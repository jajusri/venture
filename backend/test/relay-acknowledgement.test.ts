import { describe, expect, it } from 'vitest';
import { RecordRelayAcknowledgement, type RelayAcknowledgementSubmission, type RelayAcknowledgementVerifier } from '../services/relay/src/application/record-acknowledgement.js';
import { relayIdentifier, type RelayAcceptance, type RelayAcknowledgement, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';

const submission = (): RelayAcknowledgementSubmission => ({
  envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'),
  recipientBusinessId: 'business-b',
  recipientActorId: 'actor-b',
  recipientDeviceId: 'device-b',
  receivedAt: new Date(20),
});

class MemoryRepository implements RelayRepository {
  acks = 0;
  stored: RelayAcknowledgement | null = null;
  value: StoredRelayEnvelope | null = null;
  findByIdempotency() { return Promise.resolve(this.value); }
  listMailboxEntries(): Promise<RelayMailboxEntry[]> { return Promise.resolve([]); }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) {
    this.value = { submission: value, acceptance,
      delivery: { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted', mailboxSequence: 1, createdAt: acceptance.acceptedAt } };
    return Promise.resolve(this.value);
  }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    if (this.stored?.envelopeId === request.envelopeId) return Promise.resolve(this.stored);
    this.acks += 1;
    this.stored = {
      envelopeId: request.envelopeId,
      recipientBusinessId: request.recipientBusinessId,
      recipientDeviceId: request.recipientDeviceId,
      receivedAt: request.receivedAt,
    };
    return Promise.resolve(this.stored);
  }
}

const verifier: RelayAcknowledgementVerifier = { verify: async (value) => ({
  recipientBusinessId: value.recipientBusinessId,
  recipientActorId: value.recipientActorId,
  recipientDeviceId: value.recipientDeviceId,
  credentialValid: true,
  authorityScope: new Set(['receive_orders']),
}) };

describe('relay delivery acknowledgement', () => {
  it('records durable acknowledgement idempotently without claiming seen', async () => {
    const repository = new MemoryRepository();
    const service = new RecordRelayAcknowledgement(repository, verifier, () => new Date(30));
    const first = await service.execute(submission());
    const retry = await service.execute(submission());
    expect(first.envelopeId).toBe('env-1');
    expect(retry.receivedAt).toEqual(first.receivedAt);
    expect(repository.acks).toBe(1);
    expect(JSON.stringify(first)).not.toContain('seen');
  });

  it('rejects forged recipient authority', async () => {
    const forged: RelayAcknowledgementVerifier = { verify: async () => ({
      recipientBusinessId: 'other', recipientActorId: 'actor-b', recipientDeviceId: 'device-b',
      credentialValid: true, authorityScope: new Set(['receive_orders']),
    }) };
    await expect(new RecordRelayAcknowledgement(new MemoryRepository(), forged).execute(submission())).rejects.toThrow('binding mismatch');
  });
});
