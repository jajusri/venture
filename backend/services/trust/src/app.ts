import Fastify, { type FastifyInstance, type FastifyRequest, type FastifyServerOptions } from 'fastify';
import { ConsumeDeviceEnrollmentGrant, DeviceEnrollmentRejected, type DeviceEnrollmentRejectionReason, type EnrollmentGrantConsumptionStore } from './application/consume-enrollment-grant.js';
import type { BusinessDeviceCredentialIssuer } from './application/issue-credential.js';
import { ReadCurrentAuthorityEpoch } from './application/read-authority-epoch.js';
import { TrustServiceError, type ServiceErrorBody } from './errors.js';
import { mapDeviceEnrollmentRequestBody, type DeviceEnrollmentRequestBody } from './http/map-enrollment.js';
import type { VerificationKeyDirectory } from './application/verification-keys.js';
import type { TrustAuthoritySnapshotReader } from '../../relay/src/application/pilot-authority-verifier.js';

const ENROLLMENT_REJECTION_STATUS: Record<DeviceEnrollmentRejectionReason, number> = {
  grant_not_found: 404, grant_already_consumed: 409, grant_expired: 410, grant_secret_mismatch: 403,
  business_inactive: 403, membership_inactive: 403,
};

export function buildTrustService(options: {
  verificationKeys?: VerificationKeyDirectory;
  enrollment?: { store: EnrollmentGrantConsumptionStore; credentialIssuer: BusinessDeviceCredentialIssuer };
  authoritySnapshots?: TrustAuthoritySnapshotReader;
  now?: () => Date;
  /** Test seam only: lets a test capture log output (e.g. a custom pino stream) to assert on it.
   * Production callers never set this and get the same `logger: true` as before. */
  logger?: FastifyServerOptions['logger'];
} = {}): FastifyInstance {
  const app = Fastify({ logger: options.logger ?? true });
  app.get('/health', () => ({ status: 'ok', service: 'budcom-trust' }));
  if (options.verificationKeys) app.get<{ Params: { issuerId: string } }>('/v1/trust/issuers/:issuerId/verification-keys', async (request, reply) => {
    const keys = await options.verificationKeys!.list(request.params.issuerId, options.now?.() ?? new Date());
    void reply.header('cache-control', 'public, max-age=300, stale-if-error=3600');
    return { version: 1, issuerId: request.params.issuerId, keys: keys.map((key) => ({ ...key, validFrom: key.validFrom.toISOString(), validUntil: key.validUntil?.toISOString() })) };
  });
  if (options.authoritySnapshots) {
    const readEpoch = new ReadCurrentAuthorityEpoch(options.authoritySnapshots);
    // Public, unauthenticated read -- same sensitivity class as verification-keys above (a bare
    // monotonic counter, never membership/device content). Lets a device detect its OWN cached
    // credential has gone stale (authority changed since issuance) without re-deriving Relay's own
    // snapshot logic -- see `read-authority-epoch.ts`'s doc comment for why this reuses
    // `TrustAuthoritySnapshotReader` rather than a second read path.
    app.get<{ Querystring: { businessId?: string; membershipId?: string; deviceId?: string } }>('/v1/trust/authority/epoch', async (request, reply) => {
      const { businessId, membershipId, deviceId } = request.query;
      if (!businessId?.trim() || !membershipId?.trim() || !deviceId?.trim()) {
        throw new TrustServiceError('invalid_authority_epoch_request', 'businessId, membershipId, and deviceId are all required', 400);
      }
      const epoch = await readEpoch.execute({ businessId, membershipId, deviceId });
      if (epoch === null) throw new TrustServiceError('authority_not_found', 'No active authority found for the given business/membership/device', 404);
      void reply.header('cache-control', 'no-store');
      return { businessId, membershipId, deviceId, authorityEpoch: epoch };
    });
  }
  if (options.enrollment) {
    const consume = new ConsumeDeviceEnrollmentGrant(options.enrollment.store, options.enrollment.credentialIssuer, options.now);
    // Never accepts caller-supplied business/actor/membership authority (see `ConsumeDeviceEnrollmentGrant`'s
    // own doc comment) -- the enrollment grant itself, validated inside `consume.execute()`, is the
    // ONLY source of who this device enrollment is for. No other Trust route creates authority from
    // an unauthenticated request; this is the sole "bootstrap a new device" network entry point.
    app.post<{ Body: DeviceEnrollmentRequestBody }>('/v1/trust/enrollment/consume', async (request) => {
      const credential = await consume.execute(mapDeviceEnrollmentRequestBody(request.body ?? {}));
      return {
        device: {
          businessId: credential.claims.businessId, deviceId: credential.claims.deviceId,
          deviceKeyVersion: credential.claims.deviceKeyVersion, publicKeyFingerprint: credential.claims.devicePublicKeyFingerprint,
        },
        credential: {
          claims: {
            credentialVersion: credential.claims.credentialVersion, credentialId: credential.claims.credentialId, businessId: credential.claims.businessId,
            actorId: credential.claims.actorId, membershipId: credential.claims.membershipId, deviceId: credential.claims.deviceId,
            deviceKeyId: credential.claims.deviceKeyId, deviceKeyVersion: credential.claims.deviceKeyVersion, devicePublicKeyFingerprint: credential.claims.devicePublicKeyFingerprint,
            authorityScope: [...credential.claims.authorityScope.capabilities], authorityEpoch: credential.claims.authorityEpoch,
            issuedAt: credential.claims.issuedAt.toISOString(), notBefore: credential.claims.notBefore.toISOString(), expiresAt: credential.claims.expiresAt.toISOString(),
            issuerId: credential.claims.issuerId, issuerKeyId: credential.claims.issuerKeyId,
          },
          signature: Buffer.from(credential.signature.signature).toString('base64'),
        },
      };
    });
  }
  app.setErrorHandler((error, request, reply) => {
    if (error instanceof TrustServiceError) {
      const body: ServiceErrorBody = { error: { code: error.code, message: error.message, requestId: request.id } };
      void reply.status(error.statusCode).send(body);
      return;
    }
    if (error instanceof DeviceEnrollmentRejected) {
      const status = ENROLLMENT_REJECTION_STATUS[error.reason];
      const body: ServiceErrorBody = { error: { code: error.reason, message: publicEnrollmentMessage(error.reason), requestId: request.id } };
      void reply.status(status).send(body);
      return;
    }
    const mapped = error instanceof Error ? error : new Error('The Trust Service could not process the request.');
    const status = mapUnknownEnrollmentError(mapped);
    if (status === 500) logUnexpectedTrustError(request, mapped);
    const body: ServiceErrorBody = { error: { code: status === 500 ? 'internal_error' : 'enrollment_rejected', message: status === 500 ? 'The Trust Service could not process the request.' : mapped.message, requestId: request.id } };
    void reply.status(status).send(body);
  });
  return app;
}

/** Server-side-only log for an error this handler could not map to a specific, known refusal --
 * i.e. genuinely unexpected: a driver/library failure, a bug, anything not already handled by
 * `TrustServiceError`/`DeviceEnrollmentRejected`/the recognized-message branches above.
 *
 * Deliberately never logs `error.message` or `error.stack`. A regex/blacklist redaction pass
 * over free-text error content (an earlier version of this function did exactly that, stripping
 * `scheme://user:pass@` connection strings) can only catch secret shapes someone thought to
 * anticipate -- an unexpected error is by definition from a code path nobody expected, so it can
 * carry a bearer token, a raw password, PEM key material, or any other secret-bearing text a
 * driver/library/future code path chooses to embed in a message or stack frame, in a shape no
 * fixed pattern list is guaranteed to catch. Only fixed, code-controlled metadata is logged:
 * the error's class name (a JS identifier the throwing code chose, never attacker/request data),
 * the matched route PATTERN (`/v1/trust/x/:id`, never the live URL -- which can carry query
 * values) and method, and the same request ID already returned to the caller in the generic 500
 * body, so a specific client-reported failure can be correlated to a specific log line without
 * the log line itself needing to reveal anything the response doesn't already say. */
function logUnexpectedTrustError(request: FastifyRequest, error: Error): void {
  request.log.error(
    {
      event: 'unexpected_trust_error',
      errorName: error.name,
      errorConstructor: error.constructor?.name ?? 'Unknown',
      route: request.routeOptions?.url ?? 'unmatched-route',
      method: request.method,
      requestId: request.id,
    },
    'unexpected_trust_error',
  );
}

/** Refusals surfaced by the reused, certified `RegisterBusinessDevice`/`BusinessDeviceCredentialIssuer`
 * application services when invoked from the enrollment path -- these already throw plain `Error`s
 * with these exact messages elsewhere in the codebase (see `register-device.ts`/`issue-credential.ts`);
 * this only maps those SAME messages to an HTTP status, exactly as Relay's own `mapUnknown` does for
 * its reused application-layer errors, rather than inventing a parallel error taxonomy for them. */
function mapUnknownEnrollmentError(error: Error): number {
  const message = error.message;
  if (message.includes('required') || message.includes('mismatch') || message.includes('must be') || message.includes('positive integer')) return 400;
  if (message.includes('Conflicting') || message.includes('cannot register devices') || message.includes('cannot issue credentials') || message.includes('authority rejected')) return 409;
  return 500;
}

function publicEnrollmentMessage(reason: DeviceEnrollmentRejectionReason): string {
  switch (reason) {
    case 'grant_not_found': return 'Enrollment grant not found';
    case 'grant_already_consumed': return 'Enrollment grant has already been used';
    case 'grant_expired': return 'Enrollment grant has expired';
    case 'grant_secret_mismatch': return 'Enrollment grant proof did not match';
    case 'business_inactive': return 'Business is not active';
    case 'membership_inactive': return 'Membership is not active';
  }
}
