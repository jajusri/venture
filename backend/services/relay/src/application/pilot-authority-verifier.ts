import { AuthorityScope, identifier, validateDeviceAuthority, type AuthorityCapability, type BusinessMembership, type RegisteredBusinessDevice } from '../../../trust/src/domain/authority.js';
import type { RelaySubmission } from '../domain/relay.js';
import type { RelaySubmissionVerifier, VerifiedRelayAuthority } from './accept-submission.js';
import type { RelayMailboxFetch, RelayMailboxVerifier, VerifiedMailboxAuthority } from './fetch-mailbox.js';
import type { RelayAcknowledgementSubmission, RelayAcknowledgementVerifier, VerifiedAcknowledgementAuthority } from './record-acknowledgement.js';
import type { RelayReplayGuard } from './relay-replay-guard.js';
import {
  authenticatedRequestSigningPayload, credentialClaimsSigningPayload, parseAuthenticatedRelayRequest, parsePilotEnvelope,
  pilotBindingSigningPayload, verifyEcdsaSha256, verifyEcdsaSha256WithDerPublicKey,
  type AuthenticatedRequestBindingFields, type PilotCredentialClaimsWire,
} from '../devtools/pilot-envelope.js';

/**
 * CONTROLLED-PILOT verifier (relay-authority-repair, 2026-08-29 -- fixes Codex STOP 1 and STOP 2).
 *
 * STOP 1 (fixed): `checkBearerAuthority()` used to grant mailbox-fetch/acknowledgement authority
 * from request-supplied `businessId`/`actorId`/`deviceId` alone -- no signature, no possession
 * proof, no credential. Anyone who knew or guessed a valid identifier tuple could obtain authority.
 * It is deleted. Fetch and Acknowledgement now go through the exact same
 * `verifyCredentialAgainstCurrentAuthority()` + device-signature-over-canonical-bytes pipeline
 * Submit already used, via the new `AuthenticatedRelayRequestWire` primitive
 * (`devtools/pilot-envelope.ts`) -- possession of the registered device's private key is REQUIRED,
 * not merely knowledge of identifiers. See `relay-replay-guard.ts` for why a valid signature still
 * cannot become reusable bearer authority.
 *
 * STOP 2 (fixed): the shared `verifyCredentialAgainstCurrentAuthority()` now explicitly compares
 * every authority-sensitive signed credential claim (`actorId`, `membershipId`, `deviceKeyId`,
 * `devicePublicKeyFingerprint`, `authorityEpoch`) against the CURRENT Trust snapshot for that
 * identity, in addition to (not instead of) Trust's own `validateDeviceAuthority` internal-
 * consistency check. A credential whose claims no longer match live Trust state -- even if the
 * live business/membership/device rows are independently still "active" -- fails closed. This is
 * shared by Submit, Fetch, and Acknowledgement alike (one verification path, not three divergent
 * ones), so all three benefit uniformly.
 *
 * `RelaySubmissionVerifier`/`RelayMailboxVerifier`/`RelayAcknowledgementVerifier` all declare a
 * method literally named `verify`, with different parameter/return types -- one class cannot
 * implement all three directly. `PilotAuthorityVerifier` holds the shared logic under distinctly
 * named methods; `relaySubmissionVerifier`/`relayMailboxVerifier`/`relayAcknowledgementVerifier`
 * below wrap it into the three separate interface shapes `buildRelayService` expects.
 *
 * What this deliberately does NOT invent: the wire format of Submit's own `authenticatedEnvelope`
 * (unchanged from before this repair -- its existing device-signed binding was already structurally
 * correct per Codex; only its credential-vs-current-authority check needed strengthening, which the
 * shared function now provides) and revocation of an issuer's OWN signing key (out of scope: this
 * pilot runs one signer per Trust process; verification-key revocation would need a real key-
 * rotation store, which does not exist yet -- see the final report).
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

interface CredentialCheck {
  readonly valid: boolean;
  readonly claims: PilotCredentialClaimsWire | null;
  readonly snapshot: TrustAuthoritySnapshot | null;
}

/**
 * Core invariant (Codex STOP 2): SIGNED CREDENTIAL STATE must match CURRENT TRUST STATE. An older
 * credential must not regain authority merely because current rows later return to a superficially
 * compatible active state -- every authority-sensitive claim the credential carries is checked
 * against the live row it corresponds to, not just internal consistency between the live rows
 * themselves (which is all `validateDeviceAuthority` alone checks).
 */
function credentialMatchesCurrentAuthority(claims: PilotCredentialClaimsWire, snapshot: TrustAuthoritySnapshot): boolean {
  const membership = snapshot.membership;
  const device = snapshot.device;
  if (!membership || !device) return false;
  return (
    claims.businessId === membership.businessId &&
    claims.businessId === device.businessId &&
    claims.actorId === membership.actorId &&
    claims.membershipId === membership.membershipId &&
    claims.membershipId === device.membershipId &&
    claims.deviceId === device.deviceId &&
    claims.deviceKeyId === device.deviceKeyId &&
    claims.deviceKeyVersion === device.deviceKeyVersion &&
    claims.devicePublicKeyFingerprint === device.publicKeyFingerprint &&
    claims.authorityEpoch === membership.authorityEpoch.value &&
    claims.authorityEpoch === device.authorityEpoch.value
  );
}

/** Verifies a Trust-issued credential's signature, validity window, and (per the invariant above)
 * its binding to CURRENT Trust authority. Shared by Submit/Fetch/Acknowledgement. Does not verify
 * any device/request signature itself -- callers do that against `snapshot.device.publicKey` once
 * this returns a valid credential + snapshot, so a possession proof is always checked against the
 * SAME live key this function already confirmed the credential is still bound to. */
async function verifyCredentialAgainstCurrentAuthority(
  claims: PilotCredentialClaimsWire | null,
  credentialSignatureBase64: string | undefined,
  keys: TrustVerificationKeyFetcher,
  authority: TrustAuthoritySnapshotReader,
  now: Date,
): Promise<CredentialCheck> {
  if (!claims || !credentialSignatureBase64) return { valid: false, claims: null, snapshot: null };
  const issuerKey = await keys.fetch(claims.issuerId, claims.issuerKeyId);
  if (!issuerKey) return { valid: false, claims, snapshot: null };
  if (!verifyEcdsaSha256(credentialClaimsSigningPayload(claims), Buffer.from(credentialSignatureBase64, 'base64'), issuerKey)) {
    return { valid: false, claims, snapshot: null };
  }
  if (new Date(claims.notBefore) > now || new Date(claims.expiresAt) <= now) return { valid: false, claims, snapshot: null };

  const snapshot = await authority.read(claims.businessId, claims.deviceId, claims.deviceKeyVersion);
  if (!snapshot.businessStatus || !snapshot.membership || !snapshot.device) return { valid: false, claims, snapshot: null };
  if (!credentialMatchesCurrentAuthority(claims, snapshot)) return { valid: false, claims, snapshot: null };

  const failure = validateDeviceAuthority({
    business: { businessId: identifier(claims.businessId, 'BusinessId'), status: snapshot.businessStatus },
    membership: snapshot.membership,
    device: snapshot.device,
    requestedScope: new AuthorityScope(claims.authorityScope as AuthorityCapability[]),
    now,
  });
  if (failure !== null) return { valid: false, claims, snapshot: null };
  return { valid: true, claims, snapshot };
}

export class PilotAuthorityVerifier {
  constructor(
    private readonly keys: TrustVerificationKeyFetcher,
    private readonly authority: TrustAuthoritySnapshotReader,
    private readonly replay: RelayReplayGuard,
    private readonly now: () => Date = () => new Date(),
  ) {}

  async verifySubmission(submission: RelaySubmission): Promise<VerifiedRelayAuthority> {
    const wire = parsePilotEnvelope(submission.authenticatedEnvelope);
    const credCheck = await verifyCredentialAgainstCurrentAuthority(wire?.credentialClaims ?? null, wire?.credentialSignature, this.keys, this.authority, this.now());
    let valid = credCheck.valid;
    if (valid && wire) {
      const bindingPayload = pilotBindingSigningPayload({
        envelopeId: submission.envelopeId, objectType: submission.objectType, objectId: submission.objectId, objectVersion: submission.objectVersion,
        senderBusinessId: submission.senderBusinessId, senderActorId: submission.senderActorId, senderDeviceId: submission.senderDeviceId,
        recipientBusinessId: submission.recipient.businessId, recipientMailboxId: submission.recipient.mailboxId,
        commercialContent: submission.commercialContent, commercialContentType: submission.commercialContentType, commercialContentVersion: submission.commercialContentVersion,
      });
      valid = verifyEcdsaSha256WithDerPublicKey(bindingPayload, Buffer.from(wire.deviceSignature, 'base64'), credCheck.snapshot!.device!.publicKey);
    }
    return {
      protocolVersion: 1, envelopeId: submission.envelopeId,
      senderBusinessId: credCheck.claims?.businessId ?? submission.senderBusinessId, senderActorId: credCheck.claims?.actorId ?? submission.senderActorId,
      senderDeviceId: credCheck.claims?.deviceId ?? submission.senderDeviceId, recipientBusinessId: submission.recipient.businessId, mailboxId: submission.recipient.mailboxId,
      envelopeIntegrityValid: valid, credentialValid: valid,
      authorityScope: new Set(valid ? credCheck.claims!.authorityScope : []),
      commercialContent: submission.commercialContent, commercialContentType: submission.commercialContentType, commercialContentVersion: submission.commercialContentVersion,
    };
  }

  async verifyMailboxFetch(fetch: RelayMailboxFetch): Promise<VerifiedMailboxAuthority> {
    const denied: VerifiedMailboxAuthority = {
      recipientBusinessId: fetch.recipient.businessId, mailboxId: fetch.recipient.mailboxId,
      recipientActorId: fetch.recipientActorId, recipientDeviceId: fetch.recipientDeviceId,
      credentialValid: false, authorityScope: new Set(),
    };
    const wire = parseAuthenticatedRelayRequest(fetch.authenticatedRequest);
    if (!wire) return denied;
    const now = this.now();
    const credCheck = await verifyCredentialAgainstCurrentAuthority(wire.credentialClaims, wire.credentialSignature, this.keys, this.authority, now);
    if (!credCheck.valid) return denied;
    const claims = credCheck.claims!;
    // The credential's OWN identity must match who is claimed to be making this request -- a valid
    // credential for device X can never authenticate a request claiming to be device Y.
    if (claims.businessId !== fetch.recipient.businessId || claims.actorId !== fetch.recipientActorId || claims.deviceId !== fetch.recipientDeviceId) return denied;
    const bindingFields: AuthenticatedRequestBindingFields = {
      action: 'mailbox_fetch', businessId: fetch.recipient.businessId, actorId: fetch.recipientActorId, membershipId: claims.membershipId,
      deviceId: fetch.recipientDeviceId, deviceKeyId: claims.deviceKeyId, deviceKeyVersion: claims.deviceKeyVersion,
      requestId: wire.requestId, timestamp: wire.timestamp, target: fetch.recipient.mailboxId,
      parameters: [fetch.cursor ?? '', fetch.limit],
    };
    if (!verifyEcdsaSha256WithDerPublicKey(authenticatedRequestSigningPayload(bindingFields), Buffer.from(wire.requestSignature, 'base64'), credCheck.snapshot!.device!.publicKey)) return denied;
    const requestTimestamp = new Date(wire.timestamp);
    if (Number.isNaN(requestTimestamp.getTime())) return denied;
    if (!(await this.replay.consume(fetch.recipient.businessId, fetch.recipientDeviceId, wire.requestId, requestTimestamp, now))) return denied;
    return {
      recipientBusinessId: fetch.recipient.businessId, mailboxId: fetch.recipient.mailboxId,
      recipientActorId: fetch.recipientActorId, recipientDeviceId: fetch.recipientDeviceId,
      credentialValid: true, authorityScope: new Set(claims.authorityScope),
    };
  }

  async verifyAcknowledgement(submission: RelayAcknowledgementSubmission): Promise<VerifiedAcknowledgementAuthority> {
    const denied: VerifiedAcknowledgementAuthority = {
      recipientBusinessId: submission.recipientBusinessId, recipientActorId: submission.recipientActorId,
      recipientDeviceId: submission.recipientDeviceId, envelopeId: '', credentialValid: false, authorityScope: new Set(),
    };
    const wire = parseAuthenticatedRelayRequest(submission.authenticatedRequest);
    if (!wire) return denied;
    const now = this.now();
    const credCheck = await verifyCredentialAgainstCurrentAuthority(wire.credentialClaims, wire.credentialSignature, this.keys, this.authority, now);
    if (!credCheck.valid) return denied;
    const claims = credCheck.claims!;
    if (claims.businessId !== submission.recipientBusinessId || claims.actorId !== submission.recipientActorId || claims.deviceId !== submission.recipientDeviceId) return denied;
    const bindingFields: AuthenticatedRequestBindingFields = {
      action: 'acknowledge', businessId: submission.recipientBusinessId, actorId: submission.recipientActorId, membershipId: claims.membershipId,
      deviceId: submission.recipientDeviceId, deviceKeyId: claims.deviceKeyId, deviceKeyVersion: claims.deviceKeyVersion,
      requestId: wire.requestId, timestamp: wire.timestamp, target: submission.envelopeId,
      parameters: [submission.receivedAt.toISOString()],
    };
    if (!verifyEcdsaSha256WithDerPublicKey(authenticatedRequestSigningPayload(bindingFields), Buffer.from(wire.requestSignature, 'base64'), credCheck.snapshot!.device!.publicKey)) return denied;
    const requestTimestamp = new Date(wire.timestamp);
    if (Number.isNaN(requestTimestamp.getTime())) return denied;
    if (!(await this.replay.consume(submission.recipientBusinessId, submission.recipientDeviceId, wire.requestId, requestTimestamp, now))) return denied;
    return {
      recipientBusinessId: submission.recipientBusinessId, recipientActorId: submission.recipientActorId,
      recipientDeviceId: submission.recipientDeviceId, envelopeId: submission.envelopeId,
      credentialValid: true, authorityScope: new Set(claims.authorityScope),
    };
  }
}

export function relaySubmissionVerifier(core: PilotAuthorityVerifier): RelaySubmissionVerifier { return { verify: (submission) => core.verifySubmission(submission) }; }
export function relayMailboxVerifier(core: PilotAuthorityVerifier): RelayMailboxVerifier { return { verify: (fetch) => core.verifyMailboxFetch(fetch) }; }
export function relayAcknowledgementVerifier(core: PilotAuthorityVerifier): RelayAcknowledgementVerifier { return { verify: (submission) => core.verifyAcknowledgement(submission) }; }
