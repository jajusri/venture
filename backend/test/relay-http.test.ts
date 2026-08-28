import { afterEach, describe, expect, it } from 'vitest';
import type { RelaySubmissionVerifier } from '../services/relay/src/application/accept-submission.js';
import type { RelayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import type { RelayMailboxVerifier } from '../services/relay/src/application/fetch-mailbox.js';
import type { RelayAcknowledgementVerifier } from '../services/relay/src/application/record-acknowledgement.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import { buildRelayService } from '../services/relay/src/http/app.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';
import { relayIdentifier, type RelayAcceptance, type RelayAcknowledgement, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';

class MemoryRepository implements RelayRepository {
  value: StoredRelayEnvelope | null = null; writes = 0; mailbox: RelayMailboxEntry[] = [];
  findByIdempotency() { return Promise.resolve(this.value); }
  listMailboxEntries() { return Promise.resolve(this.mailbox); }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    return Promise.resolve({
      envelopeId: request.envelopeId,
      recipientBusinessId: request.recipientBusinessId,
      recipientDeviceId: request.recipientDeviceId,
      receivedAt: request.receivedAt,
    });
  }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) {
    this.writes++; this.value = { submission: value, acceptance,
      delivery: { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted', mailboxSequence: 1, createdAt: acceptance.acceptedAt } };
    return Promise.resolve(this.value);
  }
}
const verifier: RelaySubmissionVerifier = { verify: (value) => Promise.resolve({ protocolVersion: 1, envelopeId: value.envelopeId, senderBusinessId: value.senderBusinessId,
  senderActorId: value.senderActorId, senderDeviceId: value.senderDeviceId, recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']),
  commercialContent: value.commercialContent, commercialContentType: value.commercialContentType, commercialContentVersion: value.commercialContentVersion }) };
const mailboxVerifier: RelayMailboxVerifier = { verify: (value) => Promise.resolve({
  recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  recipientActorId: value.recipientActorId, recipientDeviceId: value.recipientDeviceId,
  credentialValid: true, authorityScope: new Set(['receive_orders']) }) };
const acknowledgementVerifier: RelayAcknowledgementVerifier = { verify: (value) => Promise.resolve({
  recipientBusinessId: value.recipientBusinessId, recipientActorId: value.recipientActorId,
  recipientDeviceId: value.recipientDeviceId, credentialValid: true, authorityScope: new Set(['receive_orders']),
}) };
const issuer = () => new SignedRelayAcceptanceIssuer({ sign: () => Promise.resolve({ relayId: 'relay-1', profile: 'test-v1', evidence: new Uint8Array([9]) }) }, () => 'accept-1');
const body = {
  protocolVersion: 1, envelopeId: 'env-1', idempotencyKey: 'intent-1', objectType: 'ORDER', objectId: 'order-1', objectVersion: 3,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipientBusinessId: 'business-b', mailboxId: 'orders',
  authenticatedEnvelope: Buffer.from([4, 5]).toString('base64'), commercialContent: '{}',
  commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 2, submittedAt: new Date(1).toISOString(),
};
const apps: ReturnType<typeof buildRelayService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

describe('relay HTTP submission', () => {
  it('accepts an authenticated envelope once and returns the same acceptance on retry', async () => {
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier, mailboxVerifier, acknowledgementVerifier, issuer: issuer(), now: () => new Date(10) }); apps.push(app);
    const first = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    const retry = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    expect(first.statusCode).toBe(200);
    expect(retry.json()).toEqual(first.json());
    expect(first.json()).toMatchObject({ status: 'relay_accepted', acceptanceId: 'accept-1', envelopeId: 'env-1' });
    expect(JSON.stringify(first.json())).not.toContain('delivered');
    expect(JSON.stringify(first.json())).not.toContain('seen');
    expect(repository.writes).toBe(1);
  });
  it('rejects forged authority without creating a mailbox entry', async () => {
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier: { verify: async (value) => ({ ...(await verifier.verify(value)), credentialValid: false }) }, mailboxVerifier, acknowledgementVerifier, issuer: issuer() });
    apps.push(app);
    const response = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    expect(response.statusCode).toBe(403);
    expect(repository.writes).toBe(0);
  });
  it('rejects missing commercial content and malformed outer envelope before persistence', async () => {
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier, mailboxVerifier, acknowledgementVerifier, issuer: issuer() }); apps.push(app);
    const missing = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: { ...body, commercialContent: undefined } });
    const malformed = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: { ...body, authenticatedEnvelope: '***' } });
    expect(missing.statusCode).toBe(400);
    expect(malformed.statusCode).toBe(400);
    expect(repository.writes).toBe(0);
  });
  it('returns only the authenticated recipient mailbox page', async () => {
    const repository = new MemoryRepository();
    repository.mailbox = [{
      envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), mailboxSequence: 1, objectType: 'ORDER', objectId: 'order-1',
      objectVersion: 3, senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a',
      status: 'relay_accepted', acceptedAt: new Date(10), acceptanceId: relayIdentifier('accept-1', 'RelayAcceptanceId'),
      authenticatedEnvelope: new Uint8Array([4, 5]),
      commercialContent: '{"opaque":true}', commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 2,
    }];
    const app = buildRelayService({ repository, verifier, mailboxVerifier, acknowledgementVerifier, issuer: issuer() }); apps.push(app);
    const response = await app.inject({
      method: 'POST', url: '/v1/relay/mailboxes/fetch',
      payload: { recipientBusinessId: 'business-b', mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', limit: 25 },
    });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({ recipientBusinessId: 'business-b', mailboxId: 'orders', nextCursor: null });
    expect(response.json().items).toHaveLength(1);
    expect(response.json().items[0].commercialContent).toBe('{"opaque":true}');
    expect(JSON.stringify(response.json())).not.toContain('seen');
    expect(JSON.stringify(response.json())).not.toContain('delivered');
  });
});
