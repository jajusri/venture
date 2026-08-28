import { AuthorityScope, identifier, validateDeviceAuthority, type AuthorityCapability, type BusinessMembership, type RegisteredBusinessDevice } from '../../../trust/src/domain/authority.js';
import type { RelaySubmission } from '../domain/relay.js';
import type { RelaySubmissionVerifier, VerifiedRelayAuthority } from './accept-submission.js';
import type { RelayMailboxFetch, RelayMailboxVerifier, VerifiedMailboxAuthority } from './fetch-mailbox.js';
import type { RelayAcknowledgementSubmission, RelayAcknowledgementVerifier, VerifiedAcknowledgementAuthority } from './record-acknowledgement.js';
import { credentialClaimsSigningPayload, parsePilotEnvelope, pilotBindingSigningPayload, verifyEcdsaSha256, verifyEcdsaSha256WithDerPublicKey, type PilotCredentialClaimsWire } from '../devtools/pilot-envelope.js';

/**
 * CONTROLLED-PILOT verifier: real ECDSA credential + device-signature verification, real
 * authority-freshness re-validation (reusing Trust's own `validateDeviceAuthority` -- the exact
 * same function `BusinessDeviceCredentialIssuer` uses at issuance time, so "is this credential
 * still good" is answered identically at use time), against whatever `TrustAuthoritySnapshotReader`
 * is injected (a real Postgres reader sharing Trust's own database, or the in-memory
 * `FileBackedAuthorityStore` adapter used by the integration test -- see this package's own
 * persistence-boundary note).
 *
 * `RelaySubmissionVerifier`/`RelayMailboxVerifier`/`RelayAcknowledgementVerifier` all declare a
 * method literally named `verify`, with different parameter/return types -- one class cannot
 * implement all three directly. `PilotAuthorityVerifier` holds the shared logic under distinctly
 * named methods; `relaySubmissionVerifier`/`relayMailboxVerifier`/`relayAcknowledgementVerifier`
 * below wrap it into the three separate interface shapes `buildRelayService` expects (mirroring how
 * `backend/test/relay-e2e-delivery.test.ts` already constructs three separate verifier objects,
 * not one).
 *
 * What this deliberately does NOT invent: the wire format of `authenticatedEnvelope` itself (see
 * `devtools/pilot-envelope.ts`'s own doc comment -- that is an open architecture question, not
 * resolved here) and revocation of an issuer's OWN signing key (out of scope: this pilot runs one
 * signer per Trust process; verification-key revocation would need a real key-rotation store, which
 * does not exist yet -- see the final report).
 */

export interface TrustAuthoritySnapshot {
  readonly businessStatus: 'active' | 'suspended' | 'revoked' | null;
  readonly membership: BusinessMembership | null;
  readonly device: RegisteredBusinessDevice | null;
}
export interface TrustAuthoritySnapshotReader {
  read(businessId: string, deviceId: string, deviceKeyVersion: number): Promise<TrustAuthoritySnapshot> | TrustAuthoritySnapshot;
}
export interface TrustVerificationKeyFetcher {
  /** Returns the PEM public key for `(issuerId, issuerKeyId)`, or `null` if unknown/not currently
   * valid. Implementations are expected to cache -- see `HttpTrustVerificationKeyFetcher`. */
  fetch(issuerId: string, issuerKeyId: string): Promise<string | null>;
}

interface CredentialCheck { readonly valid: boolean; readonly claims: PilotCredentialClaimsWire | null }
interface BearerCheck { readonly valid: boolean; readonly membership: BusinessMembership | null }

async function checkCredentialAndBinding(
  envelopeBytes: Uint8Array,
  bindingFieldsPayload: Uint8Array,
  keys: TrustVerificationKeyFetcher,
  authority: TrustAuthoritySnapshotReader,
  now: Date,
): Promise<CredentialCheck> {
  const wire = parsePilotEnvelope(envelopeBytes);
  if (!wire) return { valid: false, claims: null };
  const claims = wire.credentialClaims;
  const issuerKey = await keys.fetch(claims.issuerId, claims.issuerKeyId);
  if (!issuerKey) return { valid: false, claims };
  if (!verifyEcdsaSha256(credentialClaimsSigningPayload(claims), Buffer.from(wire.credentialSignature, 'base64'), issuerKey)) return { valid: false, claims };
  if (new Date(claims.notBefore) > now || new Date(claims.expiresAt) <= now) return { valid: false, claims };

  const snapshot = await authority.read(claims.businessId, claims.deviceId, claims.deviceKeyVersion);
  if (!snapshot.businessStatus || !snapshot.membership || !snapshot.device) return { valid: false, claims };
  if (!verifyEcdsaSha256WithDerPublicKey(bindingFieldsPayload, Buffer.from(wire.deviceSignature, 'base64'), snapshot.device.publicKey)) return { valid: false, claims };
  const failure = validateDeviceAuthority({
    business: { businessId: identifier(claims.businessId, 'BusinessId'), status: snapshot.businessStatus },
    membership: snapshot.membership,
    device: snapshot.device,
    requestedScope: new AuthorityScope(claims.authorityScope as AuthorityCapability[]),
    now,
  });
  return { valid: failure === null, claims };
}

/** Mailbox fetch/ack authenticate as a plain bearer of live, active device authority -- neither
 * carries a per-call `authenticatedEnvelope` in the existing HTTP contract
 * (`map-mailbox-fetch.ts`/`map-acknowledgement.ts` take plain recipient identity fields, not
 * envelope bytes), so this pilot verifier re-checks the live Trust snapshot directly for the
 * claimed `(businessId, deviceId)` rather than verifying a signature. A stronger design (the
 * recipient signing each fetch/ack request too) is a reasonable next hardening step, not
 * implemented here to avoid changing the existing HTTP request bodies these routes already accept. */
async function checkBearerAuthority(businessId: string, actorId: string, deviceId: string, authority: TrustAuthoritySnapshotReader): Promise<BearerCheck> {
  const snapshot = await authority.read(businessId, deviceId, 1);
  const valid = snapshot.businessStatus === 'active' && snapshot.membership?.status === 'active' && snapshot.device?.status === 'active' &&
    snapshot.membership.actorId === actorId && snapshot.membership.authorityEpoch.value === snapshot.device.authorityEpoch.value;
  return { valid: Boolean(valid), membership: valid ? snapshot.membership : null };
}

export class PilotAuthorityVerifier {
  constructor(private readonly keys: TrustVerificationKeyFetcher, private readonly authority: TrustAuthoritySnapshotReader, private readonly now: () => Date = () => new Date()) {}

  async verifySubmission(submission: RelaySubmission): Promise<VerifiedRelayAuthority> {
    const bindingPayload = pilotBindingSigningPayload({
      envelopeId: submission.envelopeId, objectType: submission.objectType, objectId: submission.objectId, objectVersion: submission.objectVersion,
      senderBusinessId: submission.senderBusinessId, senderActorId: submission.senderActorId, senderDeviceId: submission.senderDeviceId,
      recipientBusinessId: submission.recipient.businessId, recipientMailboxId: submission.recipient.mailboxId,
      commercialContent: submission.commercialContent, commercialContentType: submission.commercialContentType, commercialContentVersion: submission.commercialContentVersion,
    });
    const check = await checkCredentialAndBinding(submission.authenticatedEnvelope, bindingPayload, this.keys, this.authority, this.now());
    return {
      protocolVersion: 1, envelopeId: submission.envelopeId,
      senderBusinessId: check.claims?.businessId ?? submission.senderBusinessId, senderActorId: check.claims?.actorId ?? submission.senderActorId,
      senderDeviceId: check.claims?.deviceId ?? submission.senderDeviceId, recipientBusinessId: submission.recipient.businessId, mailboxId: submission.recipient.mailboxId,
      envelopeIntegrityValid: check.valid, credentialValid: check.valid,
      authorityScope: new Set(check.valid ? check.claims!.authorityScope : []),
      commercialContent: submission.commercialContent, commercialContentType: submission.commercialContentType, commercialContentVersion: submission.commercialContentVersion,
    };
  }

  async verifyMailboxFetch(fetch: RelayMailboxFetch): Promise<VerifiedMailboxAuthority> {
    const check = await checkBearerAuthority(fetch.recipient.businessId, fetch.recipientActorId, fetch.recipientDeviceId, this.authority);
    return {
      recipientBusinessId: fetch.recipient.businessId, mailboxId: fetch.recipient.mailboxId, recipientActorId: fetch.recipientActorId, recipientDeviceId: fetch.recipientDeviceId,
      credentialValid: check.valid, authorityScope: new Set(check.valid ? [...check.membership!.authorityScope.capabilities] : []),
    };
  }

  async verifyAcknowledgement(submission: RelayAcknowledgementSubmission): Promise<VerifiedAcknowledgementAuthority> {
    const check = await checkBearerAuthority(submission.recipientBusinessId, submission.recipientActorId, submission.recipientDeviceId, this.authority);
    return {
      recipientBusinessId: submission.recipientBusinessId, recipientActorId: submission.recipientActorId, recipientDeviceId: submission.recipientDeviceId,
      credentialValid: check.valid, authorityScope: new Set(check.valid ? [...check.membership!.authorityScope.capabilities] : []),
    };
  }
}

export function relaySubmissionVerifier(core: PilotAuthorityVerifier): RelaySubmissionVerifier { return { verify: (submission) => core.verifySubmission(submission) }; }
export function relayMailboxVerifier(core: PilotAuthorityVerifier): RelayMailboxVerifier { return { verify: (fetch) => core.verifyMailboxFetch(fetch) }; }
export function relayAcknowledgementVerifier(core: PilotAuthorityVerifier): RelayAcknowledgementVerifier { return { verify: (submission) => core.verifyAcknowledgement(submission) }; }
