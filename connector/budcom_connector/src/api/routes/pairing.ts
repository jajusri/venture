import type { RequestHandler } from 'express';
import { Router } from 'express';

import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { ConnectorConfig } from '../../config/defaults.js';
import type { ConnectorIdentityRepository } from '../../services/identity/connector-identity-repository.js';
import {
  PAIRING_SCHEMA_VERSION,
  type PairingSessionRepository,
  type RedeemPairingSessionOutcome,
} from '../../services/pairing/pairing-session-repository.js';
import type { PairingDeviceCredentialRepository } from '../../services/pairing/pairing-device-credential-repository.js';
import type { ConnectorTransportIdentityService } from '../../services/transport/connector-transport-identity.js';
import { createRequireDesktopControlTokenMiddleware } from '../middleware/require-desktop-control-token.js';
import { createPairingRedeemRateLimiter } from '../middleware/pairing-redeem-rate-limit.js';

/** Bumped only if the pairing-payload's transport fields (protocol/port/fingerprint/algorithm) change shape. */
const TRANSPORT_IDENTITY_VERSION = 1;

const MAX_DEVICE_LABEL_LENGTH = 64;
const MAX_DEVICE_ID_LENGTH = 128;

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isValidPort(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 && value <= 65535;
}

function requireSessions(repo: PairingSessionRepository | undefined): PairingSessionRepository {
  if (!repo) {
    throw new AppError(ErrorCodes.NOT_IMPLEMENTED, 'Secure local pairing is not available in this deployment.', 501);
  }
  return repo;
}

function requireCredentials(
  repo: PairingDeviceCredentialRepository | undefined,
): PairingDeviceCredentialRepository {
  if (!repo) {
    throw new AppError(ErrorCodes.NOT_IMPLEMENTED, 'Secure local pairing is not available in this deployment.', 501);
  }
  return repo;
}

/**
 * Gates the entire pairing-bootstrap surface behind the `securePairingEnabled` feature flag
 * (defaults to false — see config/defaults.ts). Deliberately a *separate* control from
 * requireDeviceAuthForLan: this flag decides whether the new bootstrap foundation is reachable
 * at all, not whether existing business routes require a credential. Every gated route returns
 * the same consistent disabled response, so a caller cannot distinguish "flag off" from any
 * other unavailable-feature state.
 */
function requireSecurePairingEnabled(
  deps: { readonly config: Pick<ConnectorConfig, 'securePairingEnabled'> },
): RequestHandler {
  return (_req, res, next) => {
    if (!deps.config.securePairingEnabled) {
      res.status(501).json({
        code: 'NOT_IMPLEMENTED',
        message: 'Secure local pairing is not enabled on this Connector.',
      });
      return;
    }
    next();
  };
}

/**
 * Maps a redeem outcome to an HTTP status + machine-readable reason code.
 *
 * secret_mismatch, connector_id_mismatch, and endpoint_mismatch are deliberately collapsed into
 * one generic external outcome (REDEMPTION_FAILED / 401): the caller never learns which field
 * was wrong, only that redemption failed. The repository still distinguishes these internally
 * (PairingSessionRepository counts every one of them against the same failed_attempts budget)
 * so unit tests and any future auditing can see the precise reason — that detail is simply never
 * echoed back over HTTP. Never echoes the presented secret.
 */
function outcomeToHttpFailure(outcome: Exclude<RedeemPairingSessionOutcome, { kind: 'redeemed' }>): {
  status: number;
  reasonCode: string;
  message: string;
} {
  switch (outcome.kind) {
    case 'not_found':
      return { status: 404, reasonCode: 'NOT_FOUND', message: 'No pairing session matches the supplied code.' };
    case 'expired':
      return { status: 410, reasonCode: 'EXPIRED', message: 'This pairing session has expired.' };
    case 'already_redeemed':
      return { status: 409, reasonCode: 'ALREADY_REDEEMED', message: 'This pairing session has already been used.' };
    case 'cancelled':
      return { status: 409, reasonCode: 'CANCELLED', message: 'This pairing session was cancelled.' };
    case 'too_many_attempts':
      return {
        status: 429,
        reasonCode: 'TOO_MANY_ATTEMPTS',
        message: 'Too many incorrect attempts for this pairing session.',
      };
    case 'secret_mismatch':
    case 'connector_id_mismatch':
    case 'endpoint_mismatch':
      return {
        status: 401,
        reasonCode: 'REDEMPTION_FAILED',
        message: 'The pairing details submitted could not be verified.',
      };
    default: {
      const exhaustive: never = outcome;
      throw new Error(`Unhandled pairing redeem outcome: ${JSON.stringify(exhaustive)}`);
    }
  }
}

async function buildTransportFields(
  transportIdentity: ConnectorTransportIdentityService,
  securePort: number,
): Promise<{
  transportProtocol: 'https';
  securePort: number;
  transportFingerprint: string;
  fingerprintAlgorithm: string;
  transportIdentityVersion: number;
}> {
  const identity = await transportIdentity.getIdentity();
  return {
    transportProtocol: 'https',
    securePort,
    transportFingerprint: identity.fingerprint,
    fingerprintAlgorithm: identity.fingerprintAlgorithm,
    transportIdentityVersion: TRANSPORT_IDENTITY_VERSION,
  };
}

export interface PairingBootstrapRouterDeps {
  readonly config: Pick<
    ConnectorConfig,
    | 'networkExposure'
    | 'desktopControlToken'
    | 'securePairingEnabled'
    | 'host'
    | 'port'
    | 'secureTransportEnabled'
    | 'secureTransportPort'
  >;
  readonly connectorIdentity: ConnectorIdentityRepository;
  readonly pairingSessions?: PairingSessionRepository;
  readonly pairingCredentials?: PairingDeviceCredentialRepository;
  readonly transportIdentity: ConnectorTransportIdentityService;
  /** Test-only override; defaults to a fresh createPairingRedeemRateLimiter() instance. */
  readonly redeemRateLimiter?: RequestHandler;
}

/**
 * Pairing-bootstrap routes.
 *
 * Availability boundary: every route here is gated by requireSecurePairingEnabled — while
 * config.securePairingEnabled is false (the default), all of them return a consistent 501
 * regardless of whether pairingSessions/pairingCredentials/connectorIdentity are wired up, so no
 * existing installation's behavior changes and there is no way for the flag itself to leak
 * partial availability.
 *
 * Authorization boundary once enabled — three tiers in this one router:
 *  - POST /device/pairing-session (create) and POST /device/pairing-session/cancel are gated by
 *    requireDesktopControlToken: only Desktop's own control boundary may start or cancel a
 *    pairing session, never an arbitrary LAN client.
 *  - GET /device/pairing-session/status stays open with no credential required — an unpaired
 *    phone has no token yet, by definition, so checking a session's public status before
 *    redeeming must be reachable without one.
 *  - POST /device/pairing-session/redeem stays open for the same reason, additionally protected
 *    by a narrow per-source rate limiter (see middleware/pairing-redeem-rate-limit.ts).
 *
 * All three routes return only pairing-session/credential-issuance data: no company lists,
 * ledger/voucher/stock data, other paired devices' identities, or another session's secrets —
 * see createPairingCredentialManagementRouter for the separately-protected revoke route.
 */
export function createPairingBootstrapRouter(deps: PairingBootstrapRouterDeps): Router {
  const router = Router();
  const requirePairingEnabled = requireSecurePairingEnabled({ config: deps.config });
  const requireDesktopControlToken = createRequireDesktopControlTokenMiddleware({ config: deps.config });
  const redeemRateLimiter = deps.redeemRateLimiter ?? createPairingRedeemRateLimiter();

  /**
   * POST /device/pairing-session
   *
   * Creates a short-lived pairing session for THIS Connector's own stable identity and
   * currently-configured reachable endpoint — the caller cannot supply or influence which
   * connectorId/host/port the session is bound to. Exactly one session is active per Connector
   * at a time (see PairingSessionRepository.create). Restricted to Desktop's own control
   * boundary (see requireDesktopControlToken).
   *
   * Response fields are exactly what the QR/short-code payload needs: no business or Tally
   * data, no reusable long-term credential (the returned `secret`/`shortCode` are single-use
   * and burned on redemption), and no other device's identity.
   *
   * When secureTransportEnabled is also true, the response additionally carries the pinned-HTTPS
   * fields (transportProtocol, securePort, transportFingerprint, fingerprintAlgorithm,
   * transportIdentityVersion) so the QR payload can point the client at the HTTPS endpoint and
   * let it pin the Connector's stable public-key fingerprint at first trust — see
   * connector-transport-identity.ts for the trust model. These fields are omitted entirely (not
   * present as null) while secure transport is disabled, so the response shape is unchanged from
   * before this phase for every installation that hasn't turned it on.
   */
  router.post(
    '/device/pairing-session',
    requirePairingEnabled,
    requireDesktopControlToken,
    asyncHandler(async (_req, res) => {
      const identity = deps.connectorIdentity.getOrCreateIdentity();
      const result = requireSessions(deps.pairingSessions).create({
        connectorId: identity.connectorId,
        connectorName: identity.connectorName,
        host: deps.config.host,
        port: deps.config.port,
      });

      const transportFields = deps.config.secureTransportEnabled
        ? await buildTransportFields(deps.transportIdentity, deps.config.secureTransportPort)
        : {};

      res.status(201).json({
        schemaVersion: result.schemaVersion,
        pairingSessionId: result.pairingSessionId,
        connectorId: result.connectorId,
        connectorName: result.connectorName,
        host: result.host,
        port: result.port,
        expiresAt: result.expiresAt,
        // Returned once. Never logged (request-logging middleware never logs bodies). Must be
        // rendered into the QR/short-code UI and not persisted anywhere by the caller.
        secret: result.secret,
        shortCode: result.shortCode,
        ...transportFields,
      });
    }),
  );

  /**
   * GET /device/pairing-session/status?pairingSessionId=…
   *
   * Public, non-sensitive status only — no secret, short code, or credential. Lets Desktop's
   * own QR-display UI poll "still waiting / redeemed / expired / cancelled" without consuming
   * the session. Open (no control token) since it discloses nothing sensitive and the caller
   * must already know the session's UUID.
   */
  router.get(
    '/device/pairing-session/status',
    requirePairingEnabled,
    asyncHandler(async (req, res) => {
      const pairingSessionId = req.query['pairingSessionId'];
      if (!isNonEmptyString(pairingSessionId)) {
        throw new AppError(ErrorCodes.VALIDATION_ERROR, 'pairingSessionId query parameter is required.', 400);
      }

      const status = requireSessions(deps.pairingSessions).getStatus(pairingSessionId.trim());
      if (!status) {
        res.status(404).json({ ok: false, message: 'Pairing session not found.' });
        return;
      }

      res.json({
        ok: true,
        pairingSessionId: status.pairingSessionId,
        connectorId: status.connectorId,
        connectorName: status.connectorName,
        host: status.host,
        port: status.port,
        schemaVersion: status.schemaVersion,
        createdAt: status.createdAt,
        expiresAt: status.expiresAt,
        redeemed: status.redeemedAt !== null,
        cancelled: status.cancelledAt !== null,
        expired: new Date(status.expiresAt).getTime() <= Date.now(),
      });
    }),
  );

  /**
   * POST /device/pairing-session/redeem
   *
   * Body is exactly one of:
   *   { pairingSessionId, secret, connectorId, host, port }   — the QR path. connectorId/host/
   *     port must exactly match what this session was created with — see the trust-model note
   *     in pairing-session-repository.ts for why this is checked instead of a signature. This is
   *     the primary route for a first-run device: the QR payload itself carries the Connector
   *     endpoint, so it works even where mDNS/multicast discovery is unavailable.
   *   { shortCode }                                            — the manual-entry path (no
   *     camera); a human can't transcribe connectorId/host/port alongside an 8-character code.
   *     This path only locates a session, not a Connector — it is valid only once the caller
   *     already knows or has selected the Connector's endpoint by some other means (e.g. a
   *     prior discovery pass or the QR path); it is not an independent replacement for endpoint
   *     discovery.
   *
   * On success, issues a device-bootstrap credential (pairing_device_credentials, distinct from
   * the company-bound trusted_devices credential — see pairing-device-credential-repository.ts)
   * and returns it once. The caller must still verify the returned connectorId against a live
   * /health call to the endpoint it actually connected to before trusting this response — this
   * route cannot perform that check on the caller's behalf.
   *
   * INSECURE-TRANSPORT GUARD: once an operator has explicitly turned on secureTransportEnabled
   * for a LAN-exposed Connector, a permanent device credential must never be issued over a plain
   * HTTP connection on that same LAN — see the architectural principles in Phase 3K ("pairing
   * credentials must never cross a LAN in plaintext"). This check runs before any session
   * lookup, so a rejected insecure attempt never consumes a valid one-time session or its attempt
   * budget. Loopback deployments are exempt (this connection is already local-machine-only, and
   * existing Desktop-local tooling has no HTTPS client), and so is any deployment that hasn't
   * turned secureTransportEnabled on at all — this guard adds a requirement, it does not by
   * itself force HTTPS to exist.
   */
  router.post(
    '/device/pairing-session/redeem',
    requirePairingEnabled,
    redeemRateLimiter,
    asyncHandler(async (req, res) => {
      if (deps.config.networkExposure === 'lan' && deps.config.secureTransportEnabled && !req.secure) {
        res.status(400).json({
          ok: false,
          code: 'INSECURE_TRANSPORT',
          message: 'Pairing redemption over an insecure LAN connection is not permitted while secure transport is enabled. Retry over HTTPS.',
        });
        return;
      }

      const { schemaVersion, pairingSessionId, secret, connectorId, host, port, shortCode, deviceId, deviceLabel } =
        req.body ?? {};

      if (schemaVersion !== undefined && schemaVersion !== PAIRING_SCHEMA_VERSION) {
        res.status(400).json({
          ok: false,
          code: 'UNSUPPORTED_SCHEMA',
          message: `Unsupported pairing schemaVersion '${String(schemaVersion)}'. This Connector supports '${PAIRING_SCHEMA_VERSION}'.`,
        });
        return;
      }

      if (deviceLabel !== undefined && deviceLabel !== null) {
        if (typeof deviceLabel !== 'string' || deviceLabel.length > MAX_DEVICE_LABEL_LENGTH) {
          throw new AppError(
            ErrorCodes.VALIDATION_ERROR,
            `deviceLabel must be a string of at most ${MAX_DEVICE_LABEL_LENGTH} characters.`,
            400,
          );
        }
      }

      if (deviceId !== undefined && deviceId !== null) {
        if (typeof deviceId !== 'string' || deviceId.length === 0 || deviceId.length > MAX_DEVICE_ID_LENGTH) {
          throw new AppError(
            ErrorCodes.VALIDATION_ERROR,
            `deviceId must be a non-empty string of at most ${MAX_DEVICE_ID_LENGTH} characters.`,
            400,
          );
        }
      }

      const sessions = requireSessions(deps.pairingSessions);
      let outcome: RedeemPairingSessionOutcome;

      if (isNonEmptyString(shortCode)) {
        if (
          pairingSessionId !== undefined ||
          secret !== undefined ||
          connectorId !== undefined ||
          host !== undefined ||
          port !== undefined
        ) {
          throw new AppError(
            ErrorCodes.VALIDATION_ERROR,
            'Provide either shortCode alone, or pairingSessionId + secret + connectorId + host + port — not both.',
            400,
          );
        }
        outcome = sessions.redeemByShortCode(shortCode.trim());
      } else if (isNonEmptyString(pairingSessionId) && isNonEmptyString(secret)) {
        if (!isNonEmptyString(connectorId) || !isNonEmptyString(host) || !isValidPort(port)) {
          throw new AppError(
            ErrorCodes.VALIDATION_ERROR,
            'connectorId, host, and port are required alongside pairingSessionId + secret.',
            400,
          );
        }
        outcome = sessions.redeemBySessionId(pairingSessionId.trim(), secret, {
          connectorId: connectorId.trim(),
          host: host.trim(),
          port,
        });
      } else {
        throw new AppError(
          ErrorCodes.VALIDATION_ERROR,
          'Provide either shortCode, or pairingSessionId + secret + connectorId + host + port.',
          400,
        );
      }

      if (outcome.kind !== 'redeemed') {
        const failure = outcomeToHttpFailure(outcome);
        res.status(failure.status).json({ ok: false, code: failure.reasonCode, message: failure.message });
        return;
      }

      const credential = requireCredentials(deps.pairingCredentials).issue({
        pairingSessionId: outcome.pairingSessionId,
        connectorId: outcome.connectorId,
        deviceId: typeof deviceId === 'string' ? deviceId.trim() || undefined : undefined,
        deviceLabel: typeof deviceLabel === 'string' ? deviceLabel.trim() || undefined : undefined,
      });

      res.status(201).json({
        ok: true,
        connectorId: outcome.connectorId,
        connectorName: outcome.connectorName,
        credentialId: credential.credentialId,
        // Returned once. Never logged. Caller must store via secure storage (Android Keystore).
        token: credential.rawToken,
      });
    }),
  );

  /**
   * POST /device/pairing-session/cancel
   *
   * Body: { pairingSessionId }. Restricted to Desktop's own control boundary, same as create —
   * an arbitrary LAN client must not be able to cancel a session it didn't start.
   */
  router.post(
    '/device/pairing-session/cancel',
    requirePairingEnabled,
    requireDesktopControlToken,
    asyncHandler(async (req, res) => {
      const { pairingSessionId } = req.body ?? {};
      if (!isNonEmptyString(pairingSessionId)) {
        throw new AppError(ErrorCodes.VALIDATION_ERROR, 'pairingSessionId is required.', 400);
      }

      const cancelled = requireSessions(deps.pairingSessions).cancel(pairingSessionId.trim());
      if (!cancelled) {
        res.status(404).json({ ok: false, message: 'Pairing session not found or already finalized.' });
        return;
      }
      res.json({ ok: true });
    }),
  );

  return router;
}

export interface PairingCredentialManagementRouterDeps {
  readonly config: Pick<ConnectorConfig, 'securePairingEnabled' | 'networkExposure' | 'desktopControlToken'>;
  readonly pairingCredentials?: PairingDeviceCredentialRepository;
}

/**
 * Device-management/revocation for pairing-bootstrap credentials. Mounted in `server.ts`
 * *after* `requireTrustedDeviceAuth`, matching createDeviceManagementRouter (device.ts) — these
 * mutate trust state and must never be reachable without a valid token when LAN enforcement is
 * enabled. Also gated by requireSecurePairingEnabled, like the bootstrap router.
 *
 * INTERIM HARDENING (Phase 3K): requireTrustedDeviceAuth alone is not a sufficient gate here,
 * because requireDeviceAuthForLan remains intentionally false during this compatibility phase
 * (see require-trusted-device-auth.ts) — meaning that gate is a no-op on a default LAN
 * deployment. Revocation is an administrative action (it can lock out another device), so this
 * route additionally requires the same Desktop-control-token boundary as pairing-session
 * create/cancel, independent of requireDeviceAuthForLan. This is a narrow interim rule, not a
 * general device-administration API: a device revoking *its own* credential (self-revoke) is a
 * distinct, not-yet-implemented route reserved for a later phase, authenticated by the device's
 * own credential rather than the Desktop control token.
 */
export function createPairingCredentialManagementRouter(deps: PairingCredentialManagementRouterDeps): Router {
  const router = Router();
  const requirePairingEnabled = requireSecurePairingEnabled({ config: deps.config });
  const requireDesktopControlToken = createRequireDesktopControlTokenMiddleware({ config: deps.config });

  /**
   * POST /device/pairing-credential/revoke
   *
   * Body: { credentialId }. Revokes immediately; subsequent token validation for this
   * credential returns null (unauthenticated). Restricted to Desktop's own control boundary —
   * see the class-level doc comment above.
   */
  router.post(
    '/device/pairing-credential/revoke',
    requirePairingEnabled,
    requireDesktopControlToken,
    asyncHandler(async (req, res) => {
      const { credentialId } = req.body ?? {};
      if (!isNonEmptyString(credentialId)) {
        throw new AppError(ErrorCodes.VALIDATION_ERROR, 'credentialId is required.', 400);
      }

      const revoked = requireCredentials(deps.pairingCredentials).revoke(credentialId.trim());
      if (!revoked) {
        res.status(404).json({ ok: false, message: 'Credential not found or already revoked.' });
        return;
      }
      res.json({ ok: true, message: 'Device credential revoked.' });
    }),
  );

  return router;
}
