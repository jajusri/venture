import { describe, expect, it } from 'vitest';
import { AcceptRelaySubmission, type RelaySubmissionVerifier, type VerifiedRelayAuthority } from '../services/relay/src/application/accept-submission.js';
import { relayIdentifier, type RelayAcceptance, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';

const submission = (): RelaySubmission => ({ envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), protocolVersion: 1, objectType: 'ORDER', objectId: 'order-1', objectVersion: 3,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipient: { businessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId') },
  authenticatedEnvelope: new Uint8Array([4, 5]), idempotencyKey: 'intent-1', submittedAt: new Date(1) });
const verified = (value = submission()): VerifiedRelayAuthority => ({ protocolVersion: 1, envelopeId: value.envelopeId, senderBusinessId: value.senderBusinessId,
  senderActorId: value.senderActorId, senderDeviceId: value.senderDeviceId, recipientBusinessId: value.recipient.businessId,
  mailboxId: value.recipient.mailboxId, envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']) });
class MemoryRepository implements RelayRepository {
  value: StoredRelayEnvelope | null = null; writes = 0;
  findByIdempotency() { return Promise.resolve(this.value); }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) { this.writes++; this.value = { submission: value, acceptance,
    delivery: { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted', mailboxSequence: 1, createdAt: acceptance.acceptedAt } }; return Promise.resolve(this.value); }
}

describe('authenticated relay submission', () => {
  it('accepts matching verified authority once and returns the same result for retry', async () => {
    const repository = new MemoryRepository(); const verifier: RelaySubmissionVerifier = { verify: () => Promise.resolve(verified()) };
    const service = new AcceptRelaySubmission(repository, verifier, () => new Date(10), () => 'accept-1');
    const first = await service.execute(submission()); const retry = await service.execute(submission());
    expect(first.acceptance.status).toBe('relay_accepted'); expect(retry).toBe(first); expect(repository.writes).toBe(1);
  });
  it.each([
    ['wrong sender', { senderBusinessId: 'other' }], ['wrong recipient', { recipientBusinessId: 'other' }],
    ['tampered envelope', { envelopeIntegrityValid: false }], ['revoked credential', { credentialValid: false }],
    ['missing authority', { authorityScope: new Set<string>() }],
  ])('rejects %s', async (_name, change) => {
    const verifier: RelaySubmissionVerifier = { verify: () => Promise.resolve({ ...verified(), ...change }) };
    await expect(new AcceptRelaySubmission(new MemoryRepository(), verifier).execute(submission())).rejects.toThrow();
  });
  it('rejects an idempotency key reused for changed canonical content', async () => {
    const repository = new MemoryRepository(); const service = new AcceptRelaySubmission(repository, { verify: () => Promise.resolve(verified()) });
    await service.execute(submission()); await expect(service.execute({ ...submission(), objectVersion: 4 })).rejects.toThrow('Conflicting');
  });
});
