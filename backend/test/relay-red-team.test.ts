import { afterEach, describe, expect, it } from 'vitest';
import type { RelaySubmissionVerifier } from '../services/relay/src/application/accept-submission.js';
import type { RelayMailboxVerifier } from '../services/relay/src/application/fetch-mailbox.js';
import type { RelayAcknowledgementVerifier } from '../services/relay/src/application/record-acknowledgement.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import { buildRelayService } from '../services/relay/src/http/app.js';
import type { RelayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import { relayIdentifier, type RelayAcceptance, type RelayAcknowledgement, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';

class MemoryRepository implements RelayRepository {
  value: StoredRelayEnvelope | null = null; writes = 0; mailbox: RelayMailboxEntry[] = [];
  findByIdempotency() { return Promise.resolve(this.value); }
  listMailboxEntries(_recipient, _after, limit) { return Promise.resolve(this.mailbox.slice(0, limit)); }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    return Promise.resolve({
      envelopeId: request.envelopeId, recipientBusinessId: request.recipientBusinessId,
      recipientDeviceId: request.recipientDeviceId, receivedAt: request.receivedAt,
    });
  }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) {
    this.writes++; this.value = { submission: value, acceptance,
      delivery: { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted', mailboxSequence: 1, createdAt: acceptance.acceptedAt } };
    this.mailbox = [{
      envelopeId: value.envelopeId, mailboxSequence: 1, objectType: value.objectType, objectId: value.objectId, objectVersion: value.objectVersion,
      senderBusinessId: value.senderBusinessId, senderActorId: value.senderActorId, senderDeviceId: value.senderDeviceId,
      status: 'relay_accepted', acceptedAt: acceptance.acceptedAt, acceptanceId: acceptance.acceptanceId, authenticatedEnvelope: value.authenticatedEnvelope,
    }];
    return Promise.resolve(this.value);
  }
}
const issuer = () => new SignedRelayAcceptanceIssuer({ sign: () => Promise.resolve({ relayId: 'relay-1', profile: 'test-v1', evidence: new Uint8Array([9]) }) }, () => 'accept-1');
const strictMailboxVerifier: RelayMailboxVerifier = { verify: () => Promise.resolve({
  recipientBusinessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId'),
  recipientActorId: 'actor-b', recipientDeviceId: 'device-b',
  credentialValid: true, authorityScope: new Set(['receive_orders']) }) };
const strictSubmissionVerifier: RelaySubmissionVerifier = { verify: () => Promise.resolve({
  protocolVersion: 1, envelopeId: 'env-1', senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a',
  recipientBusinessId: 'business-b', mailboxId: 'orders', envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']) }) };
const acknowledgementVerifier: RelayAcknowledgementVerifier = { verify: (value) => Promise.resolve({
  recipientBusinessId: value.recipientBusinessId, recipientActorId: value.recipientActorId,
  recipientDeviceId: value.recipientDeviceId, credentialValid: true, authorityScope: new Set(['receive_orders']) }) };
const body = {
  protocolVersion: 1, envelopeId: 'env-1', idempotencyKey: 'intent-1', objectType: 'ORDER', objectId: 'order-1', objectVersion: 1,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipientBusinessId: 'business-b', mailboxId: 'orders',
  authenticatedEnvelope: Buffer.from([4, 5]).toString('base64'), submittedAt: new Date(1).toISOString(),
};
const apps: ReturnType<typeof buildRelayService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

describe('relay security red team', () => {
  it('rejects forged credential, tampered envelope, wrong recipient, and replayed idempotency misuse', async () => {
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier: strictSubmissionVerifier, mailboxVerifier: strictMailboxVerifier, acknowledgementVerifier, issuer: issuer() }); apps.push(app);
    const forged = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: { ...body, recipientBusinessId: 'business-c' } });
    expect(forged.statusCode).toBe(403);
    await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    const tampered = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: { ...body, objectVersion: 99 } });
    expect(tampered.statusCode).toBe(500);
    expect(repository.writes).toBe(1);
    const forgedVerifierApp = buildRelayService({
      repository: new MemoryRepository(),
      verifier: { verify: async () => ({ protocolVersion: 1, envelopeId: 'env-1', senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a',
        recipientBusinessId: 'business-b', mailboxId: 'orders', envelopeIntegrityValid: true, credentialValid: false, authorityScope: new Set(['send_orders']) }) },
      mailboxVerifier: strictMailboxVerifier, acknowledgementVerifier, issuer: issuer(),
    }); apps.push(forgedVerifierApp);
    expect((await forgedVerifierApp.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body })).statusCode).toBe(403);
  });

  it('blocks cross-business mailbox access and forged acknowledgement authority', async () => {
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier: strictSubmissionVerifier, mailboxVerifier: strictMailboxVerifier, acknowledgementVerifier, issuer: issuer() }); apps.push(app);
    await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    const crossBusiness = await app.inject({
      method: 'POST', url: '/v1/relay/mailboxes/fetch',
      payload: { recipientBusinessId: 'business-a', mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', limit: 25 },
    });
    expect(crossBusiness.statusCode).toBe(403);
    const forgedAck = buildRelayService({
      repository, verifier: strictSubmissionVerifier, mailboxVerifier: strictMailboxVerifier,
      acknowledgementVerifier: { verify: async (value) => ({ ...(await acknowledgementVerifier.verify(value)), recipientBusinessId: 'business-a' }) },
      issuer: issuer(),
    }); apps.push(forgedAck);
    const ack = await forgedAck.inject({
      method: 'POST', url: '/v1/relay/acknowledgements',
      payload: { envelopeId: 'env-1', recipientBusinessId: 'business-b', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', receivedAt: new Date(2).toISOString() },
    });
    expect(ack.statusCode).toBe(403);
  });

  it('rejects malformed protocol version, oversized metadata, and malicious cursor', async () => {
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier: strictSubmissionVerifier, mailboxVerifier: strictMailboxVerifier, acknowledgementVerifier, issuer: issuer() }); apps.push(app);
    const badProtocol = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: { ...body, protocolVersion: 0 } });
    expect(badProtocol.statusCode).toBe(400);
    const huge = await app.inject({
      method: 'POST', url: '/v1/relay/envelopes',
      payload: { ...body, authenticatedEnvelope: Buffer.alloc(400 * 1024).toString('base64') },
    });
    expect([413, 500]).toContain(huge.statusCode);
    const badCursor = await app.inject({
      method: 'POST', url: '/v1/relay/mailboxes/fetch',
      payload: { recipientBusinessId: 'business-b', mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', cursor: 'not-a-cursor', limit: 25 },
    });
    expect(badCursor.statusCode).toBe(400);
  });

  it('does not leak hidden pricing or seen/confirmed semantics in transport payloads', async () => {
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier: strictSubmissionVerifier, mailboxVerifier: strictMailboxVerifier, acknowledgementVerifier, issuer: issuer() }); apps.push(app);
    const accepted = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    const mailbox = await app.inject({
      method: 'POST', url: '/v1/relay/mailboxes/fetch',
      payload: { recipientBusinessId: 'business-b', mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', limit: 25 },
    });
    const payload = JSON.stringify({ accepted: accepted.json(), mailbox: mailbox.json() });
    expect(payload).not.toMatch(/price|hidden|margin|seen|confirmed/i);
  });
});
