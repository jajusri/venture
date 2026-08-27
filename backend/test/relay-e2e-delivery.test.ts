import { afterEach, describe, expect, it } from 'vitest';
import type { RelaySubmissionVerifier } from '../services/relay/src/application/accept-submission.js';
import type { RelayMailboxVerifier } from '../services/relay/src/application/fetch-mailbox.js';
import type { RelayAcknowledgementVerifier } from '../services/relay/src/application/record-acknowledgement.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import { buildRelayService } from '../services/relay/src/http/app.js';
import type { RelayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import { relayIdentifier, type RelayAcceptance, type RelayAcknowledgement, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';

class E2ERepository implements RelayRepository {
  writes = 0;
  acks = 0;
  value: StoredRelayEnvelope | null = null;
  mailbox: RelayMailboxEntry[] = [];
  findByIdempotency(senderBusinessId: string, idempotencyKey: string) {
    if (this.value && this.value.submission.senderBusinessId === senderBusinessId && this.value.submission.idempotencyKey === idempotencyKey) {
      return Promise.resolve(this.value);
    }
    return Promise.resolve(null);
  }
  listMailboxEntries(recipient, afterSequence, limit) {
    const items = this.mailbox
      .filter((entry) => entry.mailboxSequence > (afterSequence ?? 0))
      .sort((left, right) => left.mailboxSequence - right.mailboxSequence);
    return Promise.resolve(items.slice(0, limit));
  }
  async persist(value: RelaySubmission, acceptance: RelayAcceptance) {
    this.writes += 1;
    const delivery = { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted' as const, mailboxSequence: 1, createdAt: acceptance.acceptedAt };
    this.value = { submission: value, acceptance, delivery };
    this.mailbox = [{
      envelopeId: value.envelopeId, mailboxSequence: 1, objectType: value.objectType, objectId: value.objectId, objectVersion: value.objectVersion,
      senderBusinessId: value.senderBusinessId, senderActorId: value.senderActorId, senderDeviceId: value.senderDeviceId,
      status: 'relay_accepted', acceptedAt: acceptance.acceptedAt, acceptanceId: acceptance.acceptanceId, authenticatedEnvelope: value.authenticatedEnvelope,
    }];
    return this.value;
  }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    const existing = this.acksByEnvelope.get(request.envelopeId);
    if (existing) return Promise.resolve(existing);
    this.acks += 1;
    const entry = this.mailbox.find((item) => item.envelopeId === request.envelopeId);
    if (entry) entry.status = 'delivered';
    const stored = {
      envelopeId: request.envelopeId, recipientBusinessId: request.recipientBusinessId,
      recipientDeviceId: request.recipientDeviceId, receivedAt: request.receivedAt,
    };
    this.acksByEnvelope.set(request.envelopeId, stored);
    return Promise.resolve(stored);
  }

  private acksByEnvelope = new Map<string, RelayAcknowledgement>();
}

const issuer = () => new SignedRelayAcceptanceIssuer({ sign: () => Promise.resolve({ relayId: 'relay-1', profile: 'test-v1', evidence: new Uint8Array([9]) }) }, () => 'accept-1');
const verifier: RelaySubmissionVerifier = { verify: (value) => Promise.resolve({
  protocolVersion: 1, envelopeId: value.envelopeId, senderBusinessId: value.senderBusinessId, senderActorId: value.senderActorId,
  senderDeviceId: value.senderDeviceId, recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']) }) };
const mailboxVerifier: RelayMailboxVerifier = { verify: (value) => Promise.resolve({
  recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  recipientActorId: value.recipientActorId, recipientDeviceId: value.recipientDeviceId,
  credentialValid: true, authorityScope: new Set(['receive_orders']) }) };
const acknowledgementVerifier: RelayAcknowledgementVerifier = { verify: (value) => Promise.resolve({
  recipientBusinessId: value.recipientBusinessId, recipientActorId: value.recipientActorId,
  recipientDeviceId: value.recipientDeviceId, credentialValid: true, authorityScope: new Set(['receive_orders']) }) };
const apps: ReturnType<typeof buildRelayService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

describe('relay end to end structured delivery', () => {
  it('covers submit, mailbox ingest, acknowledgement, and keeps order sent while transport becomes delivered', async () => {
    const repository = new E2ERepository();
    const app = buildRelayService({ repository, verifier, mailboxVerifier, acknowledgementVerifier, issuer: issuer(), now: () => new Date(100) });
    apps.push(app);
    const submitBody = {
      protocolVersion: 1, envelopeId: 'env-e2e-1', idempotencyKey: 'intent-e2e-1', objectType: 'ORDER', objectId: 'order-e2e-1', objectVersion: 2,
      senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipientBusinessId: 'business-b', mailboxId: 'orders',
      authenticatedEnvelope: Buffer.from([7, 8]).toString('base64'), submittedAt: new Date(10).toISOString(),
    };
    const accepted = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: submitBody });
    const acceptedRetry = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: submitBody });
    expect(accepted.statusCode).toBe(200);
    expect(acceptedRetry.json()).toEqual(accepted.json());
    expect(accepted.json()).toMatchObject({ status: 'relay_accepted', envelopeId: 'env-e2e-1', objectId: 'order-e2e-1' });
    expect(repository.writes).toBe(1);

    const mailbox = await app.inject({
      method: 'POST', url: '/v1/relay/mailboxes/fetch',
      payload: { recipientBusinessId: 'business-b', mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', limit: 25 },
    });
    expect(mailbox.statusCode).toBe(200);
    expect(mailbox.json().items).toHaveLength(1);
    expect(mailbox.json().items[0]).toMatchObject({ envelopeId: 'env-e2e-1', objectId: 'order-e2e-1', status: 'relay_accepted' });

    const ack = await app.inject({
      method: 'POST', url: '/v1/relay/acknowledgements',
      payload: { envelopeId: 'env-e2e-1', recipientBusinessId: 'business-b', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', receivedAt: new Date(200).toISOString() },
    });
    const ackRetry = await app.inject({
      method: 'POST', url: '/v1/relay/acknowledgements',
      payload: { envelopeId: 'env-e2e-1', recipientBusinessId: 'business-b', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', receivedAt: new Date(200).toISOString() },
    });
    expect(ack.statusCode).toBe(200);
    expect(ackRetry.statusCode).toBe(200);
    expect(ack.json()).toMatchObject({ status: 'delivered', envelopeId: 'env-e2e-1' });

    const lifecycle = JSON.stringify({ accepted: accepted.json(), mailbox: mailbox.json(), ack: ack.json() });
    expect(lifecycle).toContain('relay_accepted');
    expect(lifecycle).toContain('delivered');
    expect(lifecycle).not.toMatch(/seen|confirmed/i);
    expect(lifecycle).not.toMatch(/price|hidden|margin/i);
    expect(repository.acks).toBe(1);
  });
});
