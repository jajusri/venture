import { describe, expect, it, afterEach } from 'vitest';
import { FetchRecipientMailbox, normalizeMailboxPageSize, type RelayMailboxVerifier } from '../services/relay/src/application/fetch-mailbox.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import type { RelayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import type { RelayAcknowledgementVerifier } from '../services/relay/src/application/record-acknowledgement.js';
import { buildRelayService } from '../services/relay/src/http/app.js';
import { mapRelayMailboxFetchBody } from '../services/relay/src/http/map-mailbox-fetch.js';
import { encodeRelayCursor, relayIdentifier, type RelayAcceptance, type RelayAcknowledgement, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';

const recipient = { businessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId') };
const fetch = () => ({
  recipient,
  recipientActorId: 'actor-b',
  recipientDeviceId: 'device-b',
  cursor: null,
  limit: 2,
});
const verified = () => ({
  recipientBusinessId: recipient.businessId,
  mailboxId: recipient.mailboxId,
  recipientActorId: 'actor-b',
  recipientDeviceId: 'device-b',
  credentialValid: true,
  authorityScope: new Set(['receive_orders']),
});
const entry = (sequence: number): RelayMailboxEntry => ({
  envelopeId: relayIdentifier(`env-${sequence}`, 'RelayEnvelopeId'),
  mailboxSequence: sequence,
  objectType: 'ORDER',
  objectId: `order-${sequence}`,
  objectVersion: 1,
  senderBusinessId: 'business-a',
  senderActorId: 'actor-a',
  senderDeviceId: 'device-a',
  status: 'relay_accepted',
  acceptedAt: new Date(sequence),
  acceptanceId: relayIdentifier(`accept-${sequence}`, 'RelayAcceptanceId'),
  authenticatedEnvelope: new Uint8Array([sequence]),
});

class MemoryRepository implements RelayRepository {
  entries: RelayMailboxEntry[] = [];
  value: StoredRelayEnvelope | null = null;
  writes = 0;
  findByIdempotency() { return Promise.resolve(this.value); }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) {
    this.writes++;
    this.value = { submission: value, acceptance,
      delivery: { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted', mailboxSequence: 1, createdAt: acceptance.acceptedAt } };
    return Promise.resolve(this.value);
  }
  listMailboxEntries(_recipient: typeof recipient, afterSequence: number | null, limit: number) {
    const filtered = this.entries.filter((item) => afterSequence === null || item.mailboxSequence > afterSequence);
    return Promise.resolve(filtered.slice(0, limit));
  }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    return Promise.resolve({
      envelopeId: request.envelopeId, recipientBusinessId: request.recipientBusinessId,
      recipientDeviceId: request.recipientDeviceId, receivedAt: request.receivedAt,
    });
  }
}

const verifier: RelayMailboxVerifier = { verify: () => Promise.resolve(verified()) };

describe('recipient mailbox fetch', () => {
  it('returns a bounded page in stable mailbox order with a cursor', async () => {
    const repository = new MemoryRepository();
    repository.entries = [entry(1), entry(2), entry(3)];
    const service = new FetchRecipientMailbox(repository, verifier);
    const first = await service.execute(fetch());
    expect(first.items.map((item) => item.mailboxSequence)).toEqual([1, 2]);
    expect(first.nextCursor).toBe(encodeRelayCursor(2));
    const second = await service.execute({ ...fetch(), cursor: first.nextCursor });
    expect(second.items.map((item) => item.mailboxSequence)).toEqual([3]);
    expect(second.nextCursor).toBeNull();
  });

  it('caps page size and rejects forged recipient authority', async () => {
    expect(normalizeMailboxPageSize(999)).toBe(50);
    const repository = new MemoryRepository();
    repository.entries = [entry(1)];
    const forged: RelayMailboxVerifier = { verify: async () => ({ ...verified(), recipientBusinessId: 'other' }) };
    await expect(new FetchRecipientMailbox(repository, forged).execute(fetch())).rejects.toThrow('binding mismatch');
    const revoked: RelayMailboxVerifier = { verify: async () => ({ ...verified(), credentialValid: false }) };
    await expect(new FetchRecipientMailbox(repository, revoked).execute(fetch())).rejects.toThrow('authority rejected');
  });

  it('rejects malicious cursors without scanning the mailbox', async () => {
    const repository = new MemoryRepository();
    repository.entries = [entry(1)];
    await expect(new FetchRecipientMailbox(repository, verifier).execute({
      ...fetch(),
      cursor: relayIdentifier('not-a-number', 'RelayCursor'),
    })).rejects.toThrow('cursor');
  });

  it('maps HTTP mailbox fetch bodies into bounded application queries', async () => {
    const repository = new MemoryRepository();
    repository.entries = [entry(1)];
    const page = await new FetchRecipientMailbox(repository, verifier).execute(mapRelayMailboxFetchBody({
      recipientBusinessId: 'business-b', mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', limit: 25,
    }));
    expect(page.items).toHaveLength(1);
  });
});

const submissionVerifier: RelaySubmissionVerifier = { verify: async (value) => ({
  protocolVersion: 1, envelopeId: value.envelopeId, senderBusinessId: value.senderBusinessId,
  senderActorId: value.senderActorId, senderDeviceId: value.senderDeviceId,
  recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']),
}) };
const issuer = () => new SignedRelayAcceptanceIssuer({ sign: () => Promise.resolve({ relayId: 'relay-1', profile: 'test-v1', evidence: new Uint8Array([9]) }) }, () => 'accept-1');
const acknowledgementVerifier: RelayAcknowledgementVerifier = { verify: async (value) => ({
  recipientBusinessId: value.recipientBusinessId, recipientActorId: value.recipientActorId,
  recipientDeviceId: value.recipientDeviceId, credentialValid: true, authorityScope: new Set(['receive_orders']),
}) };
const apps: ReturnType<typeof buildRelayService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

describe('relay HTTP mailbox fetch', () => {
  it('returns only the authenticated recipient mailbox page', async () => {
    const repository = new MemoryRepository();
    repository.entries = [entry(1)];
    const app = buildRelayService({ repository, verifier: submissionVerifier, mailboxVerifier: verifier, acknowledgementVerifier, issuer: issuer() });
    apps.push(app);
    const response = await app.inject({
      method: 'POST', url: '/v1/relay/mailboxes/fetch',
      payload: { recipientBusinessId: 'business-b', mailboxId: 'orders', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', limit: 25 },
    });
    expect(response.statusCode).toBe(200);
    expect(response.json().items).toHaveLength(1);
  });
});
