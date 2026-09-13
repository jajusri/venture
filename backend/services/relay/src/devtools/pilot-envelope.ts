import { createPublicKey, sign as cryptoSign, verify as cryptoVerify } from 'node:crypto';

/**
 * CONTROLLED-PILOT / DEV-TEST-ONLY authenticated-envelope wire format.
 *
 * Relay's own domain (`../domain/relay.ts`) deliberately treats `authenticatedEnvelope` as opaque
 * bytes ("Relay transports the authenticated envelope bytes; it never reconstructs the canonical
 * object") -- the ACTUAL production wire format between a VENTURE Android client and Relay is not
 * defined anywhere in this backend. The only existing concrete definition lives in Android's own
 * `feature/transaction/domain/port/AuthenticatedTransportEnvelope.kt` (out of this package's write
 * scope), and it does not even agree with Relay's own `commercialContentVersion` validation today
 * (Android's constant is 3; `ORDER_SNAPSHOT_CONTENT_VERSION` in `../domain/relay.ts` is 2) -- see
 * the controlled-pilot final report's PRODUCTION PROVISIONING BLOCKER section.
 *
 * This module exists ONLY so `PilotAuthorityVerifier` (this package) and the integration test that
 * exercises it have SOMETHING concrete to sign and verify, in order to prove the REAL parts (Trust
 * credential issuance, real ECDSA verification, real Relay persistence/mailbox/ack, real
 * revocation-via-authority-epoch checking) work end to end. It is intentionally simple: a
 * JSON envelope carrying the Trust-issued credential plus a device signature over the submission's
 * own binding fields, encoded the same pipe-delimited way Trust's own `credentialSigningPayload()`
 * already does. It must NOT be read as "the" production envelope format -- reconciling it with
 * Android's real format (and the content-version mismatch) is an open architecture decision, not
 * something this operational-tooling package resolves unilaterally.
 */

export interface PilotEnvelopeBindingFields {
  readonly envelopeId: string;
  readonly objectType: string;
  readonly objectId: string;
  readonly objectVersion: number;
  readonly senderBusinessId: string;
  readonly senderActorId: string;
  readonly senderDeviceId: string;
  readonly recipientBusinessId: string;
  readonly recipientMailboxId: string;
  readonly commercialContent: string;
  readonly commercialContentType: string;
  readonly commercialContentVersion: number;
}

export interface PilotCredentialClaimsWire {
  readonly credentialVersion: number; readonly credentialId: string; readonly businessId: string; readonly actorId: string;
  readonly membershipId: string; readonly deviceId: string; readonly deviceKeyId: string; readonly deviceKeyVersion: number;
  readonly devicePublicKeyFingerprint: string; readonly authorityScope: readonly string[]; readonly authorityEpoch: number;
  readonly issuedAt: string; readonly notBefore: string; readonly expiresAt: string; readonly issuerId: string; readonly issuerKeyId: string;
}

export interface PilotEnvelopeWire {
  readonly credentialClaims: PilotCredentialClaimsWire;
  readonly credentialSignature: string;
  readonly deviceSignature: string;
}

export function pilotBindingSigningPayload(fields: PilotEnvelopeBindingFields): Uint8Array {
  const parts = [fields.envelopeId, fields.objectType, fields.objectId, fields.objectVersion, fields.senderBusinessId,
    fields.senderActorId, fields.senderDeviceId, fields.recipientBusinessId, fields.recipientMailboxId,
    fields.commercialContent, fields.commercialContentType, fields.commercialContentVersion];
  return Buffer.from(parts.map((value) => String(value).replaceAll('\\', '\\\\').replaceAll('|', '\\|')).join('|'), 'utf8');
}

/** Device-side (Android-equivalent, simulated by test tooling) envelope construction: signs the
 * binding payload with the device's own private key and packages it with the Trust credential. */
export function buildPilotEnvelope(input: { claims: PilotCredentialClaimsWire; credentialSignature: Uint8Array; bindingFields: PilotEnvelopeBindingFields; devicePrivateKeyPem: string }): Uint8Array {
  const deviceSignature = cryptoSign('sha256', pilotBindingSigningPayload(input.bindingFields), input.devicePrivateKeyPem);
  const wire: PilotEnvelopeWire = {
    credentialClaims: input.claims,
    credentialSignature: Buffer.from(input.credentialSignature).toString('base64'),
    deviceSignature: deviceSignature.toString('base64'),
  };
  return Buffer.from(JSON.stringify(wire), 'utf8');
}

export function parsePilotEnvelope(bytes: Uint8Array): PilotEnvelopeWire | null {
  try {
    const parsed: unknown = JSON.parse(Buffer.from(bytes).toString('utf8'));
    if (!parsed || typeof parsed !== 'object') return null;
    const wire = parsed as Partial<PilotEnvelopeWire>;
    if (!wire.credentialClaims || typeof wire.credentialSignature !== 'string' || typeof wire.deviceSignature !== 'string') return null;
    return wire as PilotEnvelopeWire;
  } catch {
    return null;
  }
}

/** Reproduces Trust's own `credentialSigningPayload()` byte encoding (see
 * `services/trust/src/application/issue-credential.ts`) from the wire-shaped claims, so the
 * credential's signature can be verified without importing Trust's domain classes (Relay verifies
 * against plain data, not branded types). */
export function credentialClaimsSigningPayload(claims: PilotCredentialClaimsWire): Uint8Array {
  const fields = [claims.credentialVersion, claims.credentialId, claims.businessId, claims.actorId, claims.membershipId,
    claims.deviceId, claims.deviceKeyId, claims.deviceKeyVersion, claims.devicePublicKeyFingerprint,
    [...claims.authorityScope].slice().sort().join(','), claims.authorityEpoch, new Date(claims.issuedAt).getTime(),
    new Date(claims.notBefore).getTime(), new Date(claims.expiresAt).getTime(), claims.issuerId, claims.issuerKeyId];
  return Buffer.from(fields.map((value) => String(value).replaceAll('\\', '\\\\').replaceAll('|', '\\|')).join('|'), 'utf8');
}

export function verifyEcdsaSha256(payload: Uint8Array, signature: Uint8Array, publicKeyPem: string): boolean {
  try {
    return cryptoVerify('sha256', payload, publicKeyPem, signature);
  } catch {
    return false;
  }
}

/** Registered device public keys are stored/transported as raw SPKI/DER bytes (matching how
 * `RegisteredBusinessDevice.publicKey: Uint8Array` -- and, in reality, an Android Keystore-exported
 * public key -- are naturally shaped), not PEM text. `crypto.verify` accepts a PEM string directly
 * but needs an explicit `{key, format, type}` shape (or a `KeyObject`) for raw DER -- this wraps
 * that construction once so callers can treat "verify against this device's public key" uniformly
 * with the PEM-based issuer-key verification above. Returns `false` (never throws) for malformed
 * key bytes, matching `verifyEcdsaSha256`'s own fail-closed contract. */
export function verifyEcdsaSha256WithDerPublicKey(payload: Uint8Array, signature: Uint8Array, publicKeyDer: Uint8Array): boolean {
  try {
    const keyObject = createPublicKey({ key: Buffer.from(publicKeyDer), format: 'der', type: 'spki' });
    return cryptoVerify('sha256', payload, keyObject, signature);
  } catch {
    return false;
  }
}

/**
 * CONTROLLED-PILOT canonical authenticated-request primitive for Mailbox Fetch and Acknowledgement
 * (Relay authority repair, 2026-08-29 -- Codex STOP 1).
 *
 * Before this, `checkBearerAuthority()` granted mailbox-fetch/acknowledgement authority from
 * request-supplied `businessId`/`actorId`/`deviceId` alone -- no signature, no possession proof.
 * Anyone who knew or guessed a valid identifier tuple could obtain authority. This primitive closes
 * that gap by requiring the SAME kind of device-signed, credential-backed proof Submit already uses
 * (`PilotEnvelopeWire`/`pilotBindingSigningPayload` above), generalized to carry an action name, a
 * request nonce, and a timestamp, since Fetch/Ack don't have the fixed set of top-level submission
 * fields Submit's binding payload is built from. This is intentionally the SAME style of pipe-
 * delimited canonicalization as `pilotBindingSigningPayload`/`credentialClaimsSigningPayload` above
 * and Trust's own `credentialSigningPayload()` -- no competing crypto scheme is introduced.
 *
 * Binds (per the approved architecture): protocol/request version, action, business/actor/
 * membership/device/device-key identity, a request id (nonce), a timestamp, the target resource
 * (mailboxId for fetch, envelopeId for acknowledge), and action-specific security-relevant
 * parameters (cursor+limit for fetch; receivedAt for acknowledge). Changing ANY of these after
 * signing invalidates the signature -- see `pilot-authority-verifier.test.ts`'s canonicalization/
 * tamper tests.
 */
export type AuthenticatedRelayRequestAction = 'mailbox_fetch' | 'acknowledge';

export interface AuthenticatedRequestBindingFields {
  readonly action: AuthenticatedRelayRequestAction;
  readonly businessId: string;
  readonly actorId: string;
  readonly membershipId: string;
  readonly deviceId: string;
  readonly deviceKeyId: string;
  readonly deviceKeyVersion: number;
  readonly requestId: string;
  readonly timestamp: string;
  /** mailboxId for `mailbox_fetch`, envelopeId for `acknowledge`. */
  readonly target: string;
  /** Ordered, action-specific security-relevant fields (already stringified/primitive). */
  readonly parameters: readonly (string | number)[];
}

export interface AuthenticatedRelayRequestWire {
  readonly credentialClaims: PilotCredentialClaimsWire;
  readonly credentialSignature: string;
  readonly requestId: string;
  readonly timestamp: string;
  readonly requestSignature: string;
}

const AUTHENTICATED_REQUEST_VERSION = 1;

export function authenticatedRequestSigningPayload(fields: AuthenticatedRequestBindingFields): Uint8Array {
  const parts: readonly (string | number)[] = [
    AUTHENTICATED_REQUEST_VERSION, fields.action, fields.businessId, fields.actorId, fields.membershipId,
    fields.deviceId, fields.deviceKeyId, fields.deviceKeyVersion, fields.requestId, fields.timestamp, fields.target,
    ...fields.parameters,
  ];
  return Buffer.from(parts.map((value) => String(value).replaceAll('\\', '\\\\').replaceAll('|', '\\|')).join('|'), 'utf8');
}

/** Device-side (Android-equivalent, simulated by test tooling) construction: signs the canonical
 * request payload with the device's own private key and packages it with the Trust credential --
 * the private key itself is never transmitted, only this signature. */
export function buildAuthenticatedRelayRequest(input: { claims: PilotCredentialClaimsWire; credentialSignature: Uint8Array; bindingFields: AuthenticatedRequestBindingFields; devicePrivateKeyPem: string }): Uint8Array {
  const requestSignature = cryptoSign('sha256', authenticatedRequestSigningPayload(input.bindingFields), input.devicePrivateKeyPem);
  const wire: AuthenticatedRelayRequestWire = {
    credentialClaims: input.claims,
    credentialSignature: Buffer.from(input.credentialSignature).toString('base64'),
    requestId: input.bindingFields.requestId,
    timestamp: input.bindingFields.timestamp,
    requestSignature: requestSignature.toString('base64'),
  };
  return Buffer.from(JSON.stringify(wire), 'utf8');
}

export function parseAuthenticatedRelayRequest(bytes: Uint8Array): AuthenticatedRelayRequestWire | null {
  try {
    const parsed: unknown = JSON.parse(Buffer.from(bytes).toString('utf8'));
    if (!parsed || typeof parsed !== 'object') return null;
    const wire = parsed as Partial<AuthenticatedRelayRequestWire>;
    if (!wire.credentialClaims || typeof wire.credentialSignature !== 'string' || typeof wire.requestId !== 'string' ||
      typeof wire.timestamp !== 'string' || typeof wire.requestSignature !== 'string') return null;
    return wire as AuthenticatedRelayRequestWire;
  } catch {
    return null;
  }
}
