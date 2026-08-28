import { createPublicKey, sign as cryptoSign, verify as cryptoVerify } from 'node:crypto';

/**
 * CONTROLLED-PILOT / DEV-TEST-ONLY authenticated-envelope wire format.
 *
 * Relay's own domain (`../domain/relay.ts`) deliberately treats `authenticatedEnvelope` as opaque
 * bytes ("Relay transports the authenticated envelope bytes; it never reconstructs the canonical
 * object") -- the ACTUAL production wire format between a BUDCOM Android client and Relay is not
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
