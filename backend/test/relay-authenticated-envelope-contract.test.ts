import { generateKeyPairSync, sign as cryptoSign } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { identifier, AuthorityScope, type AuthorityCapability, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
import { relayIdentifier, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import { PilotAuthorityVerifier, type TrustAuthoritySnapshot, type TrustAuthoritySnapshotReader, type TrustVerificationKeyFetcher } from '../services/relay/src/application/pilot-authority-verifier.js';
import { relayMailboxFetch } from '../services/relay/src/application/fetch-mailbox.js';
import { relayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import {
  buildPilotEnvelope, buildAuthenticatedRelayRequest, credentialClaimsSigningPayload,
  type PilotCredentialClaimsWire, type PilotEnvelopeBindingFields, type AuthenticatedRequestBindingFields,
} from '../services/relay/src/devtools/pilot-envelope.js';

/**
 * Proves, for the first time, that the certified `PilotAuthorityVerifier` really accepts a
 * correctly-produced authenticated envelope and really rejects every tampered variant -- closing
 * the exact gap the relay-authority-repair transport wire contract fix was commissioned to close:
 * "Android unit tests mocked the HTTP boundary AND Relay tests generated their own synthetic
 * envelope" (no prior test in this repository exercised `PilotAuthorityVerifier`'s own crypto path
 * at all -- `relay-submission.test.ts` etc. all use a pure `{verify: () => ...}` fake).
 *
 * Field values are shared with `shared/fixtures/relay/authenticated-envelope-golden-v1.json`, the
 * same fixture `AuthenticatedTransportEnvelopeTest.kt` (Android) checks its own independent
 * canonical-byte construction against -- this file and that one are two halves of the one
 * language-neutral cross-stack contract proof Gate 5 requires. This file owns the crypto/verifier
 * half; the Android file owns the canonicalization-agreement half (ECDSA is not deterministic, so
 * byte-identical signatures across runtimes are neither expected nor asserted -- canonical INPUT
 * bytes are).
 */
const fixture = JSON.parse(readFileSync(new URL('../../shared/fixtures/relay/authenticated-envelope-golden-v1.json', import.meta.url), 'utf8')) as {
  credentialClaims: Omit<PilotCredentialClaimsWire, 'authorityScope'> & { authorityScope: string[] };
  submitBinding: PilotEnvelopeBindingFields;
  fetchBinding: AuthenticatedRequestBindingFields;
  ackBinding: AuthenticatedRequestBindingFields;
};

const issuerKeys = generateKeyPairSync('ec', { namedCurve: 'P-256' });
const issuerPublicKeyPem = issuerKeys.publicKey.export({ type: 'spki', format: 'pem' }).toString();
const deviceKeys = generateKeyPairSync('ec', { namedCurve: 'P-256' });
const devicePublicKeyDer = new Uint8Array(deviceKeys.publicKey.export({ type: 'spki', format: 'der' }));

const claims: PilotCredentialClaimsWire = { ...fixture.credentialClaims, authorityScope: fixture.credentialClaims.authorityScope };
const credentialSignature = cryptoSign('sha256', credentialClaimsSigningPayload(claims), issuerKeys.privateKey);

function validSnapshot(): TrustAuthoritySnapshot {
  const authorityEpoch = { value: claims.authorityEpoch };
  const membership: BusinessMembership = {
    membershipId: identifier(claims.membershipId, 'MembershipId'), businessId: identifier(claims.businessId, 'BusinessId'),
    actorId: identifier(claims.actorId, 'ActorId'), status: 'active',
    authorityScope: new AuthorityScope(claims.authorityScope as AuthorityCapability[]), authorityEpoch,
    createdAt: new Date(0), modifiedAt: new Date(0),
  };
  const device: RegisteredBusinessDevice = {
    businessId: identifier(claims.businessId, 'BusinessId'), actorId: identifier(claims.actorId, 'ActorId'),
    membershipId: identifier(claims.membershipId, 'MembershipId'), deviceId: identifier(claims.deviceId, 'DeviceId'),
    deviceKeyId: identifier(claims.deviceKeyId, 'DeviceKeyId'), deviceKeyVersion: claims.deviceKeyVersion,
    publicKey: devicePublicKeyDer, publicKeyFingerprint: claims.devicePublicKeyFingerprint, status: 'active',
    authorityEpoch, createdAt: new Date(0),
  };
  return { businessStatus: 'active', membership, device };
}

class FakeKeyFetcher implements TrustVerificationKeyFetcher {
  fetch(issuerId: string, issuerKeyId: string): Promise<string | null> {
    return Promise.resolve(issuerId === claims.issuerId && issuerKeyId === claims.issuerKeyId ? issuerPublicKeyPem : null);
  }
}
class FakeSnapshotReader implements TrustAuthoritySnapshotReader {
  constructor(private readonly snapshot: TrustAuthoritySnapshot | (() => TrustAuthoritySnapshot) = validSnapshot) {}
  read(): TrustAuthoritySnapshot { return typeof this.snapshot === 'function' ? this.snapshot() : this.snapshot; }
}
class FakeReplayGuard {
  private readonly seen = new Set<string>();
  consume(businessId: string, deviceId: string, requestId: string, requestTimestamp: Date, now: Date): Promise<boolean> {
    const key = `${businessId}:${deviceId}:${requestId}`;
    if (this.seen.has(key)) return Promise.resolve(false);
    if (Math.abs(now.getTime() - requestTimestamp.getTime()) > 5 * 60_000) return Promise.resolve(false);
    this.seen.add(key);
    return Promise.resolve(true);
  }
}

function verifier(snapshot?: TrustAuthoritySnapshot | (() => TrustAuthoritySnapshot), now = () => new Date('2026-08-29T12:00:00.500Z')) {
  return new PilotAuthorityVerifier(new FakeKeyFetcher(), new FakeSnapshotReader(snapshot), new FakeReplayGuard(), now);
}

function submission(envelopeBytes: Uint8Array, overrides: Partial<RelaySubmission> = {}): RelaySubmission {
  const b = fixture.submitBinding;
  return {
    envelopeId: relayIdentifier(b.envelopeId, 'RelayEnvelopeId'), protocolVersion: 1, objectType: b.objectType, objectId: b.objectId,
    objectVersion: b.objectVersion, senderBusinessId: b.senderBusinessId, senderActorId: b.senderActorId, senderDeviceId: b.senderDeviceId,
    recipient: { businessId: b.recipientBusinessId, mailboxId: relayIdentifier(b.recipientMailboxId, 'MailboxId') },
    authenticatedEnvelope: envelopeBytes, commercialContent: b.commercialContent, commercialContentType: b.commercialContentType,
    commercialContentVersion: b.commercialContentVersion, idempotencyKey: 'idem-1', submittedAt: new Date('2026-08-29T12:00:00Z'),
    ...overrides,
  };
}

function goldenEnvelopeBytes(): Uint8Array {
  return buildPilotEnvelope({ claims, credentialSignature, bindingFields: fixture.submitBinding, devicePrivateKeyPem: deviceKeys.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString() });
}

function tamperedWire<T extends { credentialClaims: unknown; credentialSignature: string }>(bytes: Uint8Array, mutate: (wire: T) => void): Uint8Array {
  const wire = JSON.parse(Buffer.from(bytes).toString('utf8')) as T;
  mutate(wire);
  return new Uint8Array(Buffer.from(JSON.stringify(wire), 'utf8'));
}

function flippedBase64(value: string): string {
  const bytes = Buffer.from(value, 'base64');
  bytes[0] = bytes[0]! ^ 0xff;
  return bytes.toString('base64');
}

describe('PilotAuthorityVerifier cross-stack contract (golden fixture)', () => {
  it('accepts a correctly signed submission produced with the golden binding fields', async () => {
    const authority = await verifier().verifySubmission(submission(goldenEnvelopeBytes()));
    expect(authority.credentialValid).toBe(true);
    expect(authority.envelopeIntegrityValid).toBe(true);
    expect(authority.authorityScope.has('send_orders')).toBe(true);
    expect(authority.senderBusinessId).toBe(claims.businessId);
  });

  it.each([
    ['credentialClaims.businessId tampered', (w: { credentialClaims: PilotCredentialClaimsWire }) => { w.credentialClaims = { ...w.credentialClaims, businessId: 'attacker-business' }; }],
    ['credentialSignature tampered', (w: { credentialSignature: string }) => { w.credentialSignature = flippedBase64(w.credentialSignature); }],
    ['deviceSignature tampered', (w: { deviceSignature: string }) => { w.deviceSignature = flippedBase64(w.deviceSignature); }],
  ])('rejects submission with %s', async (_name, mutate) => {
    const tampered = tamperedWire(goldenEnvelopeBytes(), mutate as (w: unknown) => void);
    const authority = await verifier().verifySubmission(submission(tampered));
    expect(authority.credentialValid && authority.envelopeIntegrityValid).toBe(false);
  });

  it.each([
    ['Business changed after signing', (s: RelaySubmission) => ({ ...s, senderBusinessId: 'attacker-business' })],
    ['device changed after signing', (s: RelaySubmission) => ({ ...s, senderDeviceId: 'attacker-device' })],
    ['target mailbox changed after signing', (s: RelaySubmission) => ({ ...s, recipient: { ...s.recipient, mailboxId: relayIdentifier('attacker-mailbox', 'MailboxId') } })],
    ['request id (envelopeId) changed after signing', (s: RelaySubmission) => ({ ...s, envelopeId: relayIdentifier('attacker-envelope', 'RelayEnvelopeId') })],
    ['commercial content changed after signing', (s: RelaySubmission) => ({ ...s, commercialContent: '{"attacker":true}' })],
  ])('rejects submission where %s (device signature no longer covers the presented fields)', async (_name, mutateSubmission) => {
    const base = submission(goldenEnvelopeBytes());
    const authority = await verifier().verifySubmission(mutateSubmission(base));
    expect(authority.envelopeIntegrityValid).toBe(false);
  });

  it('rejects when current Trust state disagrees with the credential (Business mismatch)', async () => {
    const snapshot = validSnapshot();
    const wrongBusiness: TrustAuthoritySnapshot = {
      ...snapshot,
      membership: { ...snapshot.membership!, businessId: identifier('other-business', 'BusinessId') },
    };
    const authority = await verifier(wrongBusiness).verifySubmission(submission(goldenEnvelopeBytes()));
    expect(authority.credentialValid).toBe(false);
  });

  it('rejects when current Trust state disagrees with the credential (device mismatch)', async () => {
    const snapshot = validSnapshot();
    const wrongDevice: TrustAuthoritySnapshot = { ...snapshot, device: { ...snapshot.device!, deviceId: identifier('other-device', 'DeviceId') } };
    const authority = await verifier(wrongDevice).verifySubmission(submission(goldenEnvelopeBytes()));
    expect(authority.credentialValid).toBe(false);
  });

  it('rejects an expired credential (stale authority)', async () => {
    const expiredClaims: PilotCredentialClaimsWire = { ...claims, expiresAt: '2026-08-01T00:00:00.000Z' };
    const expiredSignature = cryptoSign('sha256', credentialClaimsSigningPayload(expiredClaims), issuerKeys.privateKey);
    const bytes = buildPilotEnvelope({ claims: expiredClaims, credentialSignature: expiredSignature, bindingFields: fixture.submitBinding, devicePrivateKeyPem: deviceKeys.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString() });
    const authority = await verifier().verifySubmission(submission(bytes));
    expect(authority.credentialValid).toBe(false);
  });

  it('rejects a revoked/suspended business even with an otherwise-valid signed credential', async () => {
    const snapshot = { ...validSnapshot(), businessStatus: 'revoked' as const };
    const authority = await verifier(snapshot).verifySubmission(submission(goldenEnvelopeBytes()));
    expect(authority.credentialValid).toBe(false);
  });

  it('accepts a correctly signed, credential-backed mailbox fetch and rejects a replay of the same request', async () => {
    const devicePrivateKeyPem = deviceKeys.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
    const bytes = buildAuthenticatedRelayRequest({ claims, credentialSignature, bindingFields: fixture.fetchBinding, devicePrivateKeyPem });
    const fetch = relayMailboxFetch({
      recipientBusinessId: claims.businessId, mailboxId: fixture.fetchBinding.target, recipientActorId: claims.actorId,
      recipientDeviceId: claims.deviceId, authenticatedRequest: bytes, cursor: 'golden-cursor-1', limit: 25,
    });
    const shared = verifier();
    const first = await shared.verifyMailboxFetch(fetch);
    expect(first.credentialValid).toBe(true);
    expect(first.authorityScope.has('receive_orders')).toBe(true);
    const replay = await shared.verifyMailboxFetch(fetch);
    expect(replay.credentialValid).toBe(false);
  });

  it('rejects a mailbox fetch whose requestSignature was tampered', async () => {
    const devicePrivateKeyPem = deviceKeys.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
    const bytes = buildAuthenticatedRelayRequest({ claims, credentialSignature, bindingFields: fixture.fetchBinding, devicePrivateKeyPem });
    const tampered = tamperedWire<{ credentialClaims: unknown; credentialSignature: string; requestSignature: string }>(bytes, (w) => { w.requestSignature = flippedBase64(w.requestSignature); });
    const fetch = relayMailboxFetch({
      recipientBusinessId: claims.businessId, mailboxId: fixture.fetchBinding.target, recipientActorId: claims.actorId,
      recipientDeviceId: claims.deviceId, authenticatedRequest: tampered, cursor: 'golden-cursor-1', limit: 25,
    });
    expect((await verifier().verifyMailboxFetch(fetch)).credentialValid).toBe(false);
  });

  it('accepts a correctly signed, credential-backed acknowledgement and rejects a wrong-Business claimant', async () => {
    const devicePrivateKeyPem = deviceKeys.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
    const bytes = buildAuthenticatedRelayRequest({ claims, credentialSignature, bindingFields: fixture.ackBinding, devicePrivateKeyPem });
    const ack = relayAcknowledgementSubmission({
      envelopeId: fixture.ackBinding.target, recipientBusinessId: claims.businessId, recipientActorId: claims.actorId,
      recipientDeviceId: claims.deviceId, receivedAt: new Date('2026-08-29T12:00:02.000Z'), authenticatedRequest: bytes,
    });
    const accepted = await verifier().verifyAcknowledgement(ack);
    expect(accepted.credentialValid).toBe(true);
    expect(accepted.envelopeId).toBe(fixture.ackBinding.target);

    const wrongBusinessClaim = relayAcknowledgementSubmission({
      envelopeId: fixture.ackBinding.target, recipientBusinessId: 'attacker-business', recipientActorId: claims.actorId,
      recipientDeviceId: claims.deviceId, receivedAt: new Date('2026-08-29T12:00:02.000Z'), authenticatedRequest: bytes,
    });
    const rejected = await verifier().verifyAcknowledgement(wrongBusinessClaim);
    expect(rejected.credentialValid).toBe(false);
  });
});
