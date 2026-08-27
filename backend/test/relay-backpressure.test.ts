import { describe, expect, it } from 'vitest';
import { AcceptRelaySubmission, type RelaySubmissionVerifier } from '../services/relay/src/application/accept-submission.js';
import { PartitionedRelayIngressLimiter } from '../services/relay/src/application/relay-protections.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import { RelayServiceError } from '../services/relay/src/errors.js';
import { buildRelayService } from '../services/relay/src/http/app.js';
import { relayIdentifier, type RelayAcceptance, type RelayAcknowledgement, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';

const submission = (): RelaySubmission => ({
  envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), protocolVersion: 1, objectType: 'ORDER', objectId: 'order-1', objectVersion: 1,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a',
  recipient: { businessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId') },
  authenticatedEnvelope: new Uint8Array([1]), idempotencyKey: 'intent-1', submittedAt: new Date(1),
});
const verified = (): Awaited<ReturnType<RelaySubmissionVerifier['verify']>> => ({
  protocolVersion: 1, envelopeId: 'env-1', senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a',
  recipientBusinessId: 'business-b', mailboxId: 'orders', envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']),
});

class MemoryRepository implements RelayRepository {
  writes = 0;
  private readonly byIdempotency = new Map<string, StoredRelayEnvelope>();
  findByIdempotency(senderBusinessId: string, idempotencyKey: string) {
    return Promise.resolve(this.byIdempotency.get(`${senderBusinessId}:${idempotencyKey}`) ?? null);
  }
  listMailboxEntries(): Promise<RelayMailboxEntry[]> { return Promise.resolve([]); }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    return Promise.resolve({
      envelopeId: request.envelopeId, recipientBusinessId: request.recipientBusinessId,
      recipientDeviceId: request.recipientDeviceId, receivedAt: request.receivedAt,
    });
  }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) {
    this.writes += 1;
    const stored = { submission: value, acceptance,
      delivery: { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted' as const, mailboxSequence: 1, createdAt: acceptance.acceptedAt } };
    this.byIdempotency.set(`${value.senderBusinessId}:${value.idempotencyKey}`, stored);
    return Promise.resolve(stored);
  }
}

const issuer = () => new SignedRelayAcceptanceIssuer({ sign: () => Promise.resolve({ relayId: 'relay-1', profile: 'test-v1', evidence: new Uint8Array([9]) }) }, () => 'accept-1');
const verifier: RelaySubmissionVerifier = { verify: (value) => Promise.resolve({
  protocolVersion: 1, envelopeId: value.envelopeId, senderBusinessId: value.senderBusinessId, senderActorId: value.senderActorId,
  senderDeviceId: value.senderDeviceId, recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']),
}) };
const mailboxVerifier = { verify: async (value: { recipient: { businessId: string; mailboxId: string }; recipientActorId: string; recipientDeviceId: string }) => ({
  recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  recipientActorId: value.recipientActorId, recipientDeviceId: value.recipientDeviceId,
  credentialValid: true, authorityScope: new Set(['receive_orders']),
}) };
const acknowledgementVerifier = { verify: async (value: RelayAcknowledgementSubmission) => ({
  recipientBusinessId: value.recipientBusinessId, recipientActorId: value.recipientActorId,
  recipientDeviceId: value.recipientDeviceId, credentialValid: true, authorityScope: new Set(['receive_orders']),
}) };

describe('relay retry and backpressure', () => {
  it('returns Retry-After when a sender partition exceeds ingress budget', async () => {
    const limiter = new PartitionedRelayIngressLimiter(1, 60_000);
    const repository = new MemoryRepository();
    const service = new AcceptRelaySubmission(repository, verifier, issuer(), limiter);
    await service.execute(submission());
    await expect(service.execute({
      ...submission(), envelopeId: relayIdentifier('env-2', 'RelayEnvelopeId'), idempotencyKey: 'intent-2',
    })).rejects.toBeInstanceOf(RelayServiceError);
    expect(repository.writes).toBe(1);
  });

  it('exposes bounded overload through HTTP without dropping durable idempotent retries', async () => {
    const limiter = new PartitionedRelayIngressLimiter(1, 60_000);
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier, mailboxVerifier, acknowledgementVerifier, ingressLimiter: limiter, issuer: issuer() });
    const body = {
      protocolVersion: 1, envelopeId: 'env-1', idempotencyKey: 'intent-1', objectType: 'ORDER', objectId: 'order-1', objectVersion: 1,
      senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipientBusinessId: 'business-b', mailboxId: 'orders',
      authenticatedEnvelope: Buffer.from([1]).toString('base64'), submittedAt: new Date(1).toISOString(),
    };
    const first = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    const overloaded = await app.inject({
      method: 'POST', url: '/v1/relay/envelopes',
      payload: { ...body, envelopeId: 'env-2', idempotencyKey: 'intent-2' },
    });
    const retry = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    expect(first.statusCode).toBe(200);
    expect(overloaded.statusCode).toBe(503);
    expect(overloaded.headers['retry-after']).toBeTruthy();
    expect(retry.statusCode).toBe(200);
    expect(repository.writes).toBe(1);
    await app.close();
  });
});
