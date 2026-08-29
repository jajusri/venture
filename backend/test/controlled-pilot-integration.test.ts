import { createHash, generateKeyPairSync, sign as cryptoSign } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { AddressInfo } from 'node:net';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { CreateBusiness } from '../services/trust/src/application/create-business.js';
import { RegisterBusinessDevice } from '../services/trust/src/application/register-device.js';
import { BusinessDeviceCredentialIssuer, type IssuedBusinessDeviceCredential } from '../services/trust/src/application/issue-credential.js';
import { AuthorityRevocationService } from '../services/trust/src/application/revoke-authority.js';
import { VerificationKeyDirectory } from '../services/trust/src/application/verification-keys.js';
import { AuthorityScope, identifier, type AuthorityCapability, type BusinessId, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
import { FileBackedAuthorityStore, createBusinessDeterministicIds } from '../services/trust/src/persistence/file-backed-authority-store.js';
import { InMemoryCredentialIssuanceStore } from '../services/trust/src/persistence/in-memory-credential-store.js';
import { LocalFileTrustCredentialSigner } from '../services/trust/src/persistence/local-signer.js';
import { buildTrustService } from '../services/trust/src/app.js';

import { buildRelayService } from '../services/relay/src/http/app.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import { PilotAuthorityVerifier, relayAcknowledgementVerifier, relayMailboxVerifier, relaySubmissionVerifier, type TrustAuthoritySnapshotReader } from '../services/relay/src/application/pilot-authority-verifier.js';
import { HttpTrustVerificationKeyFetcher } from '../services/relay/src/application/http-verification-key-fetcher.js';
import { InMemoryRelayReplayGuard } from '../services/relay/src/application/relay-replay-guard.js';
import { buildAuthenticatedRelayRequest, buildPilotEnvelope, type AuthenticatedRequestBindingFields, type PilotCredentialClaimsWire } from '../services/relay/src/devtools/pilot-envelope.js';
import type { RecipientRoutingKey, RelayAcceptance, RelayAcknowledgement, RelayMailboxEntry, RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';

/**
 * Real Trust + Relay backend integration proof for the controlled-pilot package.
 *
 * REAL: Trust's actual application services (CreateBusiness, RegisterBusinessDevice,
 * BusinessDeviceCredentialIssuer, AuthorityRevocationService), a real local EC signer doing real
 * ECDSA signing, a real running Trust Fastify HTTP server (bound to a real loopback port) serving
 * its real `/v1/trust/issuers/:issuerId/verification-keys` route, a real HTTP fetch+cache from
 * Relay to that route, real Relay application services (AcceptRelaySubmission,
 * FetchRecipientMailbox, RecordRelayAcknowledgement) via Relay's real Fastify route layer
 * (`app.inject()`, exercising the exact same route-handling code a live deployment would run --
 * matching this codebase's own existing `relay-http.test.ts`/`relay-e2e-delivery.test.ts`
 * convention), and real authority-freshness re-validation via `validateDeviceAuthority` (Trust's own
 * domain function, reused unmodified).
 *
 * NOT REAL (explicit, isolated, declared boundary -- see individual doc comments):
 *  - Persistence for Trust's write-path (business/membership/device) and Relay's envelope/mailbox
 *    tables: `FileBackedAuthorityStore` / `InMemoryRelayRepository` in this file, not Postgres. No
 *    local PostgreSQL is reachable in this sandboxed environment (verified: no service, no Docker).
 *    The real Postgres adapters exist (`postgres-authority-write-store.ts`,
 *    `postgres-authority-snapshot-reader.ts`, `PostgresRelayRepository` -- the last one pre-existing)
 *    and are structurally verified in `postgres-authority-write-store.test.ts`, not behaviorally
 *    verified against a live database here.
 *  - The authenticated-envelope wire format (`devtools/pilot-envelope.ts`) is a dev-test-only
 *    placeholder, not the production Android<->Relay contract (which does not exist in this backend
 *    today -- see that module's own doc comment on the v2/v3 content-version mismatch).
 */

const canonicalOrderSnapshot = readFileSync(new URL('../../shared/fixtures/relay/canonical-order-snapshot-v3.json', import.meta.url), 'utf8').replace(/\r?\n$/, '');

class InMemoryRelayRepository implements RelayRepository {
  private value: StoredRelayEnvelope | null = null;
  private mailbox: RelayMailboxEntry[] = [];
  private acksByEnvelope = new Map<string, RelayAcknowledgement>();
  writes = 0;
  acks = 0;

  findByIdempotency(senderBusinessId: string, idempotencyKey: string): Promise<StoredRelayEnvelope | null> {
    if (this.value && this.value.submission.senderBusinessId === senderBusinessId && this.value.submission.idempotencyKey === idempotencyKey) return Promise.resolve(this.value);
    return Promise.resolve(null);
  }
  listMailboxEntries(_recipient: RecipientRoutingKey, afterSequence: number | null, limit: number): Promise<RelayMailboxEntry[]> {
    const items = this.mailbox.filter((entry) => entry.mailboxSequence > (afterSequence ?? 0)).sort((a, b) => a.mailboxSequence - b.mailboxSequence);
    return Promise.resolve(items.slice(0, limit));
  }
  persist(submission: RelaySubmission, acceptance: RelayAcceptance): Promise<StoredRelayEnvelope> {
    this.writes += 1;
    const sequence = this.mailbox.length + 1;
    const delivery = { envelopeId: submission.envelopeId, recipient: submission.recipient, status: 'relay_accepted' as const, mailboxSequence: sequence, createdAt: acceptance.acceptedAt };
    this.value = { submission, acceptance, delivery };
    this.mailbox.push({
      envelopeId: submission.envelopeId, mailboxSequence: sequence, objectType: submission.objectType, objectId: submission.objectId, objectVersion: submission.objectVersion,
      senderBusinessId: submission.senderBusinessId, senderActorId: submission.senderActorId, senderDeviceId: submission.senderDeviceId,
      status: 'relay_accepted', acceptedAt: acceptance.acceptedAt, acceptanceId: acceptance.acceptanceId, authenticatedEnvelope: submission.authenticatedEnvelope,
      commercialContent: submission.commercialContent, commercialContentType: submission.commercialContentType, commercialContentVersion: submission.commercialContentVersion,
    });
    return Promise.resolve(this.value);
  }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    const existing = this.acksByEnvelope.get(request.envelopeId);
    if (existing) return Promise.resolve(existing);
    this.acks += 1;
    const index = this.mailbox.findIndex((item) => item.envelopeId === request.envelopeId);
    if (index >= 0) this.mailbox[index] = { ...this.mailbox[index]!, status: 'delivered' };
    const stored = { envelopeId: request.envelopeId, recipientBusinessId: request.recipientBusinessId, recipientDeviceId: request.recipientDeviceId, receivedAt: request.receivedAt };
    this.acksByEnvelope.set(request.envelopeId, stored);
    return Promise.resolve(stored);
  }
}

interface Provisioned {
  readonly businessId: BusinessId;
  readonly membership: BusinessMembership;
  readonly device: RegisteredBusinessDevice;
  readonly devicePrivateKeyPem: string;
}

async function provisionBusiness(store: FileBackedAuthorityStore, now: Date, opts: { actor: string; name: string; intent: string; deviceId: string; extraScope: readonly AuthorityCapability[] }): Promise<Provisioned> {
  const created = await new CreateBusiness(store, () => now, createBusinessDeterministicIds(opts.actor, opts.intent)).execute({
    principal: { actorId: identifier(opts.actor, 'ActorId'), verificationId: `verified-${opts.actor}`, verifiedAt: now },
    intentId: opts.intent, authorityDisplayName: opts.name,
  });
  const widenedScope = new AuthorityScope([...new Set([...created.membership.authorityScope.capabilities, ...opts.extraScope])]);
  const membership = await new AuthorityRevocationService(store, () => now).changeScope(created.membership, widenedScope);
  const deviceKeyPair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const devicePublicKeyDer = deviceKeyPair.publicKey.export({ type: 'spki', format: 'der' });
  const fingerprint = createHash('sha256').update(devicePublicKeyDer).digest('base64');
  const device = await new RegisterBusinessDevice(store, () => now).execute({
    principal: { actorId: identifier(opts.actor, 'ActorId'), verificationId: `verified-${opts.actor}`, verifiedAt: now },
    membership, deviceId: identifier(opts.deviceId, 'DeviceId'), deviceKeyId: identifier(`${opts.deviceId}-key-1`, 'DeviceKeyId'),
    deviceKeyVersion: 1, publicKey: new Uint8Array(devicePublicKeyDer), publicKeyFingerprint: fingerprint,
  });
  return { businessId: created.businessId, membership, device, devicePrivateKeyPem: deviceKeyPair.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString() };
}

function toWireClaims(claims: IssuedBusinessDeviceCredential['claims']): PilotCredentialClaimsWire {
  return {
    credentialVersion: claims.credentialVersion, credentialId: claims.credentialId, businessId: claims.businessId, actorId: claims.actorId,
    membershipId: claims.membershipId, deviceId: claims.deviceId, deviceKeyId: claims.deviceKeyId, deviceKeyVersion: claims.deviceKeyVersion,
    devicePublicKeyFingerprint: claims.devicePublicKeyFingerprint, authorityScope: [...claims.authorityScope.capabilities], authorityEpoch: claims.authorityEpoch,
    issuedAt: claims.issuedAt.toISOString(), notBefore: claims.notBefore.toISOString(), expiresAt: claims.expiresAt.toISOString(),
    issuerId: claims.issuerId, issuerKeyId: claims.issuerKeyId,
  };
}

function buildSubmissionEnvelope(input: { credential: IssuedBusinessDeviceCredential; devicePrivateKeyPem: string; envelopeId: string; objectId: string; objectVersion: number; recipientBusinessId: string; mailboxId: string; commercialContent: string }): Uint8Array {
  const bindingFields = {
    envelopeId: input.envelopeId, objectType: 'ORDER', objectId: input.objectId, objectVersion: input.objectVersion,
    senderBusinessId: input.credential.claims.businessId, senderActorId: input.credential.claims.actorId, senderDeviceId: input.credential.claims.deviceId,
    recipientBusinessId: input.recipientBusinessId, recipientMailboxId: input.mailboxId,
    commercialContent: input.commercialContent, commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 3,
  };
  return buildPilotEnvelope({ claims: toWireClaims(input.credential.claims), credentialSignature: input.credential.signature.signature, bindingFields, devicePrivateKeyPem: input.devicePrivateKeyPem });
}

function buildAuthenticatedFetchRequest(input: { credential: IssuedBusinessDeviceCredential; devicePrivateKeyPem: string; requestId: string; timestamp: Date; recipientBusinessId: string; recipientActorId: string; recipientDeviceId: string; mailboxId: string; cursor: string | null; limit: number }): Uint8Array {
  const bindingFields: AuthenticatedRequestBindingFields = {
    action: 'mailbox_fetch', businessId: input.recipientBusinessId, actorId: input.recipientActorId, membershipId: input.credential.claims.membershipId,
    deviceId: input.recipientDeviceId, deviceKeyId: input.credential.claims.deviceKeyId, deviceKeyVersion: input.credential.claims.deviceKeyVersion,
    requestId: input.requestId, timestamp: input.timestamp.toISOString(), target: input.mailboxId, parameters: [input.cursor ?? '', input.limit],
  };
  return buildAuthenticatedRelayRequest({ claims: toWireClaims(input.credential.claims), credentialSignature: input.credential.signature.signature, bindingFields, devicePrivateKeyPem: input.devicePrivateKeyPem });
}

function buildAuthenticatedAckRequest(input: { credential: IssuedBusinessDeviceCredential; devicePrivateKeyPem: string; requestId: string; timestamp: Date; recipientBusinessId: string; recipientActorId: string; recipientDeviceId: string; envelopeId: string; receivedAt: Date }): Uint8Array {
  const bindingFields: AuthenticatedRequestBindingFields = {
    action: 'acknowledge', businessId: input.recipientBusinessId, actorId: input.recipientActorId, membershipId: input.credential.claims.membershipId,
    deviceId: input.recipientDeviceId, deviceKeyId: input.credential.claims.deviceKeyId, deviceKeyVersion: input.credential.claims.deviceKeyVersion,
    requestId: input.requestId, timestamp: input.timestamp.toISOString(), target: input.envelopeId, parameters: [input.receivedAt.toISOString()],
  };
  return buildAuthenticatedRelayRequest({ claims: toWireClaims(input.credential.claims), credentialSignature: input.credential.signature.signature, bindingFields, devicePrivateKeyPem: input.devicePrivateKeyPem });
}

describe('controlled-pilot real Trust + Relay backend integration', () => {
  const workDir = mkdtempSync(join(tmpdir(), 'budcom-pilot-'));
  const issuerKeyPath = join(workDir, 'trust-issuer-key.pem');
  const now = new Date('2026-01-01T00:00:00.000Z');
  const store = new FileBackedAuthorityStore();
  const signer = new LocalFileTrustCredentialSigner({ keyPath: issuerKeyPath, issuerId: 'pilot-test-issuer', issuerKeyId: 'pilot-test-key-1' });
  const verificationKeys = new VerificationKeyDirectory({ listForIssuer: (issuerId) => Promise.resolve(issuerId === 'pilot-test-issuer' ? [signer.publicVerificationKey(now)] : []) });
  const trustApp = buildTrustService({ verificationKeys, now: () => now });

  let trustBaseUrl: string;
  let businessA: Provisioned;
  let businessB: Provisioned;
  let credentialA: IssuedBusinessDeviceCredential;
  let credentialB: IssuedBusinessDeviceCredential;
  let relayApp: ReturnType<typeof buildRelayService>;
  let repository: InMemoryRelayRepository;

  beforeAll(async () => {
    await trustApp.listen({ host: '127.0.0.1', port: 0 });
    trustBaseUrl = `http://127.0.0.1:${(trustApp.server.address() as AddressInfo).port}`;

    businessA = await provisionBusiness(store, now, { actor: 'actor-a', name: 'Pilot Business A', intent: 'create-a', deviceId: 'device-a-1', extraScope: [] });
    businessB = await provisionBusiness(store, now, { actor: 'actor-b', name: 'Pilot Business B', intent: 'create-b', deviceId: 'device-b-1', extraScope: ['receive_orders'] });

    const credentialStore = new InMemoryCredentialIssuanceStore();
    const issuer = new BusinessDeviceCredentialIssuer(credentialStore, signer, 3_600_000, () => now);
    credentialA = await issuer.issue({ business: { businessId: businessA.businessId, status: 'active' }, membership: businessA.membership, device: businessA.device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-a' });
    credentialB = await issuer.issue({ business: { businessId: businessB.businessId, status: 'active' }, membership: businessB.membership, device: businessB.device, requestedScope: new AuthorityScope(['receive_orders']), intentId: 'issue-b' });

    const authorityReader: TrustAuthoritySnapshotReader = { read: (businessId, deviceId, deviceKeyVersion) => store.snapshot(businessId, deviceId, deviceKeyVersion) };
    const verifierCore = new PilotAuthorityVerifier(new HttpTrustVerificationKeyFetcher(trustBaseUrl, 0), authorityReader, new InMemoryRelayReplayGuard(), () => now);
    repository = new InMemoryRelayRepository();
    const relaySigningKey = generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).privateKey;
    relayApp = buildRelayService({
      repository, now: () => now,
      verifier: relaySubmissionVerifier(verifierCore), mailboxVerifier: relayMailboxVerifier(verifierCore), acknowledgementVerifier: relayAcknowledgementVerifier(verifierCore),
      issuer: new SignedRelayAcceptanceIssuer({ sign: (payload) => Promise.resolve({ relayId: 'pilot-relay-1', profile: 'test-v1', evidence: cryptoSign('sha256', payload, relaySigningKey) }) }, () => 'acceptance-1'),
    });
  });

  afterAll(async () => {
    await trustApp.close();
    await relayApp.close();
    rmSync(workDir, { recursive: true, force: true });
  });

  function submitBody(overrides: Partial<Record<string, unknown>> = {}) {
    const envelope = buildSubmissionEnvelope({
      credential: credentialA, devicePrivateKeyPem: businessA.devicePrivateKeyPem, envelopeId: 'env-pilot-1',
      objectId: 'order-pilot-1', objectVersion: 1, recipientBusinessId: businessB.businessId, mailboxId: 'orders', commercialContent: canonicalOrderSnapshot,
    });
    return {
      protocolVersion: 1, envelopeId: 'env-pilot-1', idempotencyKey: 'intent-pilot-1', objectType: 'ORDER', objectId: 'order-pilot-1', objectVersion: 1,
      senderBusinessId: businessA.businessId, senderActorId: 'actor-a', senderDeviceId: 'device-a-1',
      recipientBusinessId: businessB.businessId, mailboxId: 'orders',
      authenticatedEnvelope: Buffer.from(envelope).toString('base64'),
      commercialContent: canonicalOrderSnapshot, commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 3,
      submittedAt: now.toISOString(),
      ...overrides,
    };
  }

  it('AUTHORIZED CASE: A -> Trust credential -> Relay verifies -> persists -> B fetches -> acknowledges, idempotently', async () => {
    // Built once and reused verbatim for the retry below: ECDSA signing is non-deterministic (a
    // fresh nonce each call), so re-*building* the envelope would produce different signature bytes
    // for logically-identical content -- correctly seen as a distinct submission, not a retry, by
    // AcceptRelaySubmission's own byte-for-byte `sameSubmission` check. A real retrying sender
    // resends the exact same bytes it already sent, which is what this test reproduces.
    const body = submitBody();
    const accepted = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    expect(accepted.statusCode, accepted.body).toBe(200);
    expect(accepted.json()).toMatchObject({ status: 'relay_accepted', envelopeId: 'env-pilot-1', senderBusinessId: businessA.businessId, recipientBusinessId: businessB.businessId });
    expect(repository.writes).toBe(1);

    const duplicateSubmit = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    expect(duplicateSubmit.statusCode, duplicateSubmit.body).toBe(200);
    expect(duplicateSubmit.json()).toEqual(accepted.json());
    expect(repository.writes, 'duplicate submit must not write a second time').toBe(1);

    const fetchRequest1 = buildAuthenticatedFetchRequest({
      credential: credentialB, devicePrivateKeyPem: businessB.devicePrivateKeyPem, requestId: 'req-fetch-1', timestamp: now,
      recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', mailboxId: 'orders', cursor: null, limit: 25,
    });
    const mailbox = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload: { recipientBusinessId: businessB.businessId, mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', limit: 25, authenticatedRequest: Buffer.from(fetchRequest1).toString('base64') } });
    expect(mailbox.statusCode, mailbox.body).toBe(200);
    expect(mailbox.json().items).toHaveLength(1);
    expect(mailbox.json().items[0]).toMatchObject({ envelopeId: 'env-pilot-1', objectId: 'order-pilot-1', status: 'relay_accepted' });
    expect(mailbox.json().items[0].commercialContent).toBe(canonicalOrderSnapshot);

    // A real retrying client mints a FRESH request id/nonce per transmission -- possession proof is
    // per-request, not reusable bearer authority (see relay-replay-guard.ts) -- while the underlying
    // acknowledgement business action stays idempotent by envelopeId, which `repository.acks` proves
    // below. These are the two DIFFERENT concepts this package's task explicitly requires preserved.
    const ackRequest1 = buildAuthenticatedAckRequest({
      credential: credentialB, devicePrivateKeyPem: businessB.devicePrivateKeyPem, requestId: 'req-ack-1', timestamp: now,
      recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', envelopeId: 'env-pilot-1', receivedAt: now,
    });
    const ack = await relayApp.inject({ method: 'POST', url: '/v1/relay/acknowledgements', payload: { envelopeId: 'env-pilot-1', recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', receivedAt: now.toISOString(), authenticatedRequest: Buffer.from(ackRequest1).toString('base64') } });
    expect(ack.statusCode, ack.body).toBe(200);
    expect(ack.json()).toMatchObject({ status: 'delivered', envelopeId: 'env-pilot-1' });

    const ackRequest2 = buildAuthenticatedAckRequest({
      credential: credentialB, devicePrivateKeyPem: businessB.devicePrivateKeyPem, requestId: 'req-ack-2', timestamp: now,
      recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', envelopeId: 'env-pilot-1', receivedAt: now,
    });
    const ackRetry = await relayApp.inject({ method: 'POST', url: '/v1/relay/acknowledgements', payload: { envelopeId: 'env-pilot-1', recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', receivedAt: now.toISOString(), authenticatedRequest: Buffer.from(ackRequest2).toString('base64') } });
    expect(ackRetry.statusCode).toBe(200);
    expect(repository.acks, 'duplicate acknowledgement must not record a second delivery').toBe(1);
  });

  describe('adversarial cases (fail-closed)', () => {
    it('tampered envelope: flipping a byte in the authenticated envelope is rejected, not silently accepted', async () => {
      const body = submitBody({ envelopeId: 'env-adv-tamper', idempotencyKey: 'intent-adv-tamper', objectId: 'order-adv-tamper' });
      const tampered = Buffer.from(body.authenticatedEnvelope, 'base64');
      tampered[tampered.length - 1] = (tampered[tampered.length - 1]! + 1) % 256;
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: { ...body, authenticatedEnvelope: tampered.toString('base64') } });
      expect(response.statusCode, response.body).not.toBe(200);
      expect(repository.writes).toBe(1); // still just the happy-path write from the prior test; nothing new persisted
    });

    it('wrong capability: a credential scoped only to receive_orders cannot be used to submit', async () => {
      const envelope = buildSubmissionEnvelope({
        credential: credentialB, devicePrivateKeyPem: businessB.devicePrivateKeyPem, envelopeId: 'env-adv-wrong-cap',
        objectId: 'order-adv-wrong-cap', objectVersion: 1, recipientBusinessId: businessA.businessId, mailboxId: 'orders', commercialContent: canonicalOrderSnapshot,
      });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: {
        protocolVersion: 1, envelopeId: 'env-adv-wrong-cap', idempotencyKey: 'intent-adv-wrong-cap', objectType: 'ORDER', objectId: 'order-adv-wrong-cap', objectVersion: 1,
        senderBusinessId: businessB.businessId, senderActorId: 'actor-b', senderDeviceId: 'device-b-1', recipientBusinessId: businessA.businessId, mailboxId: 'orders',
        authenticatedEnvelope: Buffer.from(envelope).toString('base64'), commercialContent: canonicalOrderSnapshot, commercialContentType: 'application/vnd.budcom.order-snapshot+json',
        commercialContentVersion: 3, submittedAt: now.toISOString(),
      } });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('wrong business binding: claiming a different sender business than the credential attests to is rejected', async () => {
      const body = submitBody({ envelopeId: 'env-adv-wrong-business', idempotencyKey: 'intent-adv-wrong-business', objectId: 'order-adv-wrong-business', senderBusinessId: businessB.businessId });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('wrong recipient: business A cannot fetch business B\'s mailbox even with a real, validly-signed credential + possession proof for its own device', async () => {
      const forgedFetch = buildAuthenticatedFetchRequest({
        credential: credentialA, devicePrivateKeyPem: businessA.devicePrivateKeyPem, requestId: 'req-adv-wrong-recipient', timestamp: now,
        recipientBusinessId: businessB.businessId, recipientActorId: 'actor-a', recipientDeviceId: 'device-a-1', mailboxId: 'orders', cursor: null, limit: 25,
      });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload: { recipientBusinessId: businessB.businessId, mailboxId: 'orders', recipientActorId: 'actor-a', recipientDeviceId: 'device-a-1', limit: 25, authenticatedRequest: Buffer.from(forgedFetch).toString('base64') } });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('identifier-only fetch is rejected: presenting IDs with no authenticatedRequest at all yields no authority (Codex STOP 1)', async () => {
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload: { recipientBusinessId: businessB.businessId, mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', limit: 25 } });
      expect(response.statusCode, response.body).toBe(400);
    });

    it('identifier-only acknowledgement is rejected: presenting IDs with no authenticatedRequest at all yields no authority (Codex STOP 1)', async () => {
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/acknowledgements', payload: { envelopeId: 'env-pilot-1', recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', receivedAt: now.toISOString() } });
      expect(response.statusCode, response.body).toBe(400);
    });

    it('replay: reusing the exact same signed fetch request a second time is rejected, not treated as a fresh authorized fetch', async () => {
      const replayable = buildAuthenticatedFetchRequest({
        credential: credentialB, devicePrivateKeyPem: businessB.devicePrivateKeyPem, requestId: 'req-adv-replay', timestamp: now,
        recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', mailboxId: 'orders', cursor: null, limit: 25,
      });
      const payload = { recipientBusinessId: businessB.businessId, mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', limit: 25, authenticatedRequest: Buffer.from(replayable).toString('base64') };
      const first = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload });
      expect(first.statusCode, first.body).toBe(200);
      const replay = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload });
      expect(replay.statusCode, replay.body).toBe(403);
    });

    it('stale timestamp: a fetch request signed far outside the replay clock tolerance window is rejected', async () => {
      const stale = buildAuthenticatedFetchRequest({
        credential: credentialB, devicePrivateKeyPem: businessB.devicePrivateKeyPem, requestId: 'req-adv-stale-ts', timestamp: new Date(now.getTime() - 3_600_000),
        recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', mailboxId: 'orders', cursor: null, limit: 25,
      });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload: { recipientBusinessId: businessB.businessId, mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', limit: 25, authenticatedRequest: Buffer.from(stale).toString('base64') } });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('credential freshness (Codex STOP 2): a credential issued before a later authority-epoch change is rejected even though its signature and expiry are still valid', async () => {
      const fresh = await provisionBusiness(store, now, { actor: 'actor-d', name: 'Pilot Business D (epoch case)', intent: 'create-d-epoch', deviceId: 'device-d-1', extraScope: ['receive_orders'] });
      const preRotationIssuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => now);
      const preRotationCredential = await preRotationIssuer.issue({ business: { businessId: fresh.businessId, status: 'active' }, membership: fresh.membership, device: fresh.device, requestedScope: new AuthorityScope(['receive_orders']), intentId: 'issue-adv-epoch' });

      // Authority epoch advances (membership scope widened) AFTER the credential above was issued.
      // The credential's own signature and expiry are both still valid -- only its bound authority
      // epoch is now stale, which is exactly what Codex STOP 2 required Relay to catch.
      await new AuthorityRevocationService(store, () => now).changeScope(fresh.membership, new AuthorityScope(['receive_orders', 'send_orders']));

      const staleEpochFetch = buildAuthenticatedFetchRequest({
        credential: preRotationCredential, devicePrivateKeyPem: fresh.devicePrivateKeyPem, requestId: 'req-adv-epoch', timestamp: now,
        recipientBusinessId: fresh.businessId, recipientActorId: 'actor-d', recipientDeviceId: 'device-d-1', mailboxId: 'orders', cursor: null, limit: 25,
      });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload: { recipientBusinessId: fresh.businessId, mailboxId: 'orders', recipientActorId: 'actor-d', recipientDeviceId: 'device-d-1', limit: 25, authenticatedRequest: Buffer.from(staleEpochFetch).toString('base64') } });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('tampered request signature: flipping a byte in the possession-proof signature is rejected, not silently accepted', async () => {
      const fetchRequest = buildAuthenticatedFetchRequest({
        credential: credentialB, devicePrivateKeyPem: businessB.devicePrivateKeyPem, requestId: 'req-adv-tampered-sig', timestamp: now,
        recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', mailboxId: 'orders', cursor: null, limit: 25,
      });
      const wire = JSON.parse(Buffer.from(fetchRequest).toString('utf8')) as { requestSignature: string };
      const tampered = Buffer.from(wire.requestSignature, 'base64');
      tampered[tampered.length - 1] = (tampered[tampered.length - 1]! + 1) % 256;
      const tamperedWire = { ...wire, requestSignature: tampered.toString('base64') };
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload: { recipientBusinessId: businessB.businessId, mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', limit: 25, authenticatedRequest: Buffer.from(JSON.stringify(tamperedWire)).toString('base64') } });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('wrong signing key: a request signed by a key other than the credential\'s own registered device key is rejected, even with otherwise-correct claims', async () => {
      const impostorKey = generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
      const forged = buildAuthenticatedFetchRequest({
        credential: credentialB, devicePrivateKeyPem: impostorKey, requestId: 'req-adv-wrong-key', timestamp: now,
        recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', mailboxId: 'orders', cursor: null, limit: 25,
      });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload: { recipientBusinessId: businessB.businessId, mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', limit: 25, authenticatedRequest: Buffer.from(forged).toString('base64') } });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('future timestamp: a fetch request signed far ahead of the replay clock tolerance window is rejected', async () => {
      const future = buildAuthenticatedFetchRequest({
        credential: credentialB, devicePrivateKeyPem: businessB.devicePrivateKeyPem, requestId: 'req-adv-future-ts', timestamp: new Date(now.getTime() + 3_600_000),
        recipientBusinessId: businessB.businessId, recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', mailboxId: 'orders', cursor: null, limit: 25,
      });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/mailboxes/fetch', payload: { recipientBusinessId: businessB.businessId, mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b-1', limit: 25, authenticatedRequest: Buffer.from(future).toString('base64') } });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('expired credential: a credential whose expiresAt is already in the past (relative to verification time) is rejected', async () => {
      const past = new Date(now.getTime() - 7_200_000);
      const expiredIssuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => past);
      const expiredCredential = await expiredIssuer.issue({ business: { businessId: businessA.businessId, status: 'active' }, membership: businessA.membership, device: businessA.device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-adv-expired' });
      const envelope = buildSubmissionEnvelope({ credential: expiredCredential, devicePrivateKeyPem: businessA.devicePrivateKeyPem, envelopeId: 'env-adv-expired', objectId: 'order-adv-expired', objectVersion: 1, recipientBusinessId: businessB.businessId, mailboxId: 'orders', commercialContent: canonicalOrderSnapshot });
      const body = submitBody({ envelopeId: 'env-adv-expired', idempotencyKey: 'intent-adv-expired', objectId: 'order-adv-expired', authenticatedEnvelope: Buffer.from(envelope).toString('base64') });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('revoked device: a credential issued before revocation is rejected after the device is revoked (live authority re-check, not just signature validity)', async () => {
      const revocable = await provisionBusiness(store, now, { actor: 'actor-c', name: 'Pilot Business C (revocation case)', intent: 'create-c-revoked', deviceId: 'device-c-1', extraScope: [] });
      const issuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => now);
      const credential = await issuer.issue({ business: { businessId: revocable.businessId, status: 'active' }, membership: revocable.membership, device: revocable.device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-adv-revoke' });

      await new AuthorityRevocationService(store, () => now).revokeDevice(revocable.device);

      const envelope = buildSubmissionEnvelope({ credential, devicePrivateKeyPem: revocable.devicePrivateKeyPem, envelopeId: 'env-adv-revoked', objectId: 'order-adv-revoked', objectVersion: 1, recipientBusinessId: businessB.businessId, mailboxId: 'orders', commercialContent: canonicalOrderSnapshot });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: {
        protocolVersion: 1, envelopeId: 'env-adv-revoked', idempotencyKey: 'intent-adv-revoked', objectType: 'ORDER', objectId: 'order-adv-revoked', objectVersion: 1,
        senderBusinessId: revocable.businessId, senderActorId: 'actor-c', senderDeviceId: 'device-c-1', recipientBusinessId: businessB.businessId, mailboxId: 'orders',
        authenticatedEnvelope: Buffer.from(envelope).toString('base64'), commercialContent: canonicalOrderSnapshot, commercialContentType: 'application/vnd.budcom.order-snapshot+json',
        commercialContentVersion: 3, submittedAt: now.toISOString(),
      } });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('stale v2 commercial content version is rejected end to end -- v3 is current production, v2 is not accepted', async () => {
      const body = submitBody({ envelopeId: 'env-adv-v2', idempotencyKey: 'intent-adv-v2', objectId: 'order-adv-v2', commercialContentVersion: 2 });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
      expect(response.statusCode, response.body).toBe(400);
    });

    it('unknown/future commercial content version is rejected end to end', async () => {
      const body = submitBody({ envelopeId: 'env-adv-v99', idempotencyKey: 'intent-adv-v99', objectId: 'order-adv-v99', commercialContentVersion: 99 });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
      expect(response.statusCode, response.body).toBe(400);
    });

    it('malformed envelope: bytes that are not a valid pilot envelope at all are rejected, not thrown as an unhandled error', async () => {
      const body = submitBody({ envelopeId: 'env-adv-malformed', idempotencyKey: 'intent-adv-malformed', objectId: 'order-adv-malformed', authenticatedEnvelope: Buffer.from('not-json-at-all').toString('base64') });
      const response = await relayApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
      expect(response.statusCode, response.body).toBe(403);
    });

    it('Trust unavailable at verification time: the submission is not accepted (fails closed, does not crash the process)', async () => {
      const authorityReader: TrustAuthoritySnapshotReader = { read: (businessId, deviceId, deviceKeyVersion) => store.snapshot(businessId, deviceId, deviceKeyVersion) };
      const unreachableVerifier = new PilotAuthorityVerifier(new HttpTrustVerificationKeyFetcher('http://127.0.0.1:1', 0), authorityReader, new InMemoryRelayReplayGuard(), () => now);
      const isolatedApp = buildRelayService({
        repository: new InMemoryRelayRepository(), now: () => now,
        verifier: relaySubmissionVerifier(unreachableVerifier), mailboxVerifier: relayMailboxVerifier(unreachableVerifier), acknowledgementVerifier: relayAcknowledgementVerifier(unreachableVerifier),
        issuer: new SignedRelayAcceptanceIssuer({ sign: (payload) => Promise.resolve({ relayId: 'r', profile: 'p', evidence: cryptoSign('sha256', payload, generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).privateKey) }) }, () => 'acceptance-x'),
      });
      try {
        const body = submitBody({ envelopeId: 'env-adv-trust-down', idempotencyKey: 'intent-adv-trust-down', objectId: 'order-adv-trust-down' });
        const response = await isolatedApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
        expect(response.statusCode, response.body).not.toBe(200);
      } finally {
        await isolatedApp.close();
      }
    });
  });
});
