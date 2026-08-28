import { describe, expect, it } from 'vitest';
import { AcceptRelaySubmission, type RelaySubmissionVerifier, type VerifiedRelayAuthority } from '../services/relay/src/application/accept-submission.js';
import type { RelayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import { relayIdentifier, type RelayAcceptance, type RelayAcknowledgement, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';

const submission = (): RelaySubmission => ({ envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), protocolVersion: 1, objectType: 'ORDER', objectId: 'order-1', objectVersion: 3,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipient: { businessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId') },
  authenticatedEnvelope: new Uint8Array([4, 5]), commercialContent: '{}', commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 2,
  idempotencyKey: 'intent-1', submittedAt: new Date(1) });
const verified = (value = submission()): VerifiedRelayAuthority => ({ protocolVersion: 1, envelopeId: value.envelopeId, senderBusinessId: value.senderBusinessId,
  senderActorId: value.senderActorId, senderDeviceId: value.senderDeviceId, recipientBusinessId: value.recipient.businessId,
  mailboxId: value.recipient.mailboxId, envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']),
  commercialContent: value.commercialContent, commercialContentType: value.commercialContentType, commercialContentVersion: value.commercialContentVersion });
class MemoryRepository implements RelayRepository {
  value: StoredRelayEnvelope | null = null; writes = 0;
  findByIdempotency() { return Promise.resolve(this.value); }
  listMailboxEntries(): Promise<RelayMailboxEntry[]> { return Promise.resolve([]); }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    return Promise.resolve({
      envelopeId: request.envelopeId, recipientBusinessId: request.recipientBusinessId,
      recipientDeviceId: request.recipientDeviceId, receivedAt: request.receivedAt,
    });
  }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) { this.writes++; this.value = { submission: value, acceptance,
    delivery: { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted', mailboxSequence: 1, createdAt: acceptance.acceptedAt } }; return Promise.resolve(this.value); }
}
const issuer = () => new SignedRelayAcceptanceIssuer({ sign: () => Promise.resolve({ relayId: 'relay-1', profile: 'test-v1', evidence: new Uint8Array([9]) }) }, () => 'accept-1');

describe('authenticated relay submission', () => {
  it('accepts matching verified authority once and returns the same result for retry', async () => {
    const repository = new MemoryRepository(); const verifier: RelaySubmissionVerifier = { verify: () => Promise.resolve(verified()) };
    const service = new AcceptRelaySubmission(repository, verifier, issuer(), undefined, () => new Date(10));
    const first = await service.execute(submission()); const retry = await service.execute(submission());
    expect(first.acceptance.status).toBe('relay_accepted'); expect(retry).toBe(first); expect(repository.writes).toBe(1);
  });
  it.each([
    ['wrong sender', { senderBusinessId: 'other' }], ['wrong recipient', { recipientBusinessId: 'other' }],
    ['tampered envelope', { envelopeIntegrityValid: false }], ['revoked credential', { credentialValid: false }],
    ['missing authority', { authorityScope: new Set<string>() }],
  ])('rejects %s', async (_name, change) => {
    const verifier: RelaySubmissionVerifier = { verify: () => Promise.resolve({ ...verified(), ...change }) };
    await expect(new AcceptRelaySubmission(new MemoryRepository(), verifier, issuer()).execute(submission())).rejects.toThrow();
  });
  it('rejects an idempotency key reused for changed canonical content', async () => {
    const repository = new MemoryRepository(); const service = new AcceptRelaySubmission(repository, { verify: () => Promise.resolve(verified()) }, issuer());
    await service.execute(submission()); await expect(service.execute({ ...submission(), objectVersion: 4 })).rejects.toThrow('Conflicting');
  });
  it.each([
    ['content', { commercialContent: '{"changed":true}' }],
    ['content type', { commercialContentType: 'application/json' }],
    ['content version', { commercialContentVersion: 3 }],
  ])('rejects an idempotency key reused with changed %s', async (_name, change) => {
    const repository = new MemoryRepository();
    const service = new AcceptRelaySubmission(repository, { verify: (value) => Promise.resolve(verified(value)) }, issuer());
    await service.execute(submission());
    await expect(service.execute({ ...submission(), ...change })).rejects.toThrow('Conflicting');
    expect(repository.writes).toBe(1);
  });
  it('rejects missing and oversized commercial content before persistence', async () => {
    const repository = new MemoryRepository();
    const service = new AcceptRelaySubmission(repository, { verify: (value) => Promise.resolve(verified(value)) }, issuer());
    await expect(service.execute({ ...submission(), commercialContent: '' })).rejects.toThrow('required');
    await expect(service.execute({ ...submission(), commercialContent: '€'.repeat(8193) })).rejects.toThrow('bounds');
    expect(repository.writes).toBe(0);
  });
  it('rejects content that differs from the cryptographically verified envelope binding', async () => {
    const repository = new MemoryRepository();
    const verifier: RelaySubmissionVerifier = { verify: () => Promise.resolve(verified()) };
    await expect(new AcceptRelaySubmission(repository, verifier, issuer()).execute({
      ...submission(), commercialContent: '{"replacement":true}',
    })).rejects.toThrow('binding mismatch');
    expect(repository.writes).toBe(0);
  });
  it('does not persist when acceptance evidence cannot be issued', async () => {
    const repository = new MemoryRepository();
    const failing = new SignedRelayAcceptanceIssuer({ sign: () => Promise.resolve({ relayId: '', profile: '', evidence: new Uint8Array() }) });
    await expect(new AcceptRelaySubmission(repository, { verify: () => Promise.resolve(verified()) }, failing).execute(submission())).rejects.toThrow('failed closed');
    expect(repository.writes).toBe(0);
  });
});
