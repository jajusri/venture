import { afterEach, describe, expect, it } from 'vitest';
import { AcceptRelaySubmission, type RelaySubmissionVerifier } from '../services/relay/src/application/accept-submission.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import { FetchRecipientMailbox } from '../services/relay/src/application/fetch-mailbox.js';
import { RecordRelayAcknowledgement, type RelayAcknowledgementVerifier } from '../services/relay/src/application/record-acknowledgement.js';
import { PartitionedRelayIngressLimiter } from '../services/relay/src/application/relay-protections.js';
import { buildRelayService } from '../services/relay/src/http/app.js';
import type { RelayMailboxVerifier } from '../services/relay/src/application/fetch-mailbox.js';
import { relayIdentifier, type RecipientRoutingKey, type RelayAcceptance, type RelayAcknowledgement, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';
import type { RelayAcknowledgementSubmission } from '../services/relay/src/application/record-acknowledgement.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';

const submission = (): RelaySubmission => ({
  envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), protocolVersion: 1, objectType: 'ORDER', objectId: 'order-1', objectVersion: 1,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a',
  recipient: { businessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId') },
  authenticatedEnvelope: new Uint8Array([1]), commercialContent: '{}', commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 3,
  idempotencyKey: 'intent-1', submittedAt: new Date(1),
});
const verified = (): Awaited<ReturnType<RelaySubmissionVerifier['verify']>> => ({
  protocolVersion: 1, envelopeId: 'env-1', senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a',
  recipientBusinessId: 'business-b', mailboxId: 'orders', envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']),
  commercialContent: '{}', commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 3,
});
const mailboxVerified = () => ({
  recipientBusinessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId'), recipientActorId: 'actor-b', recipientDeviceId: 'device-b',
  credentialValid: true, authorityScope: new Set(['receive_orders']),
});
const ackVerified = () => ({
  recipientBusinessId: 'business-b', recipientActorId: 'actor-b', recipientDeviceId: 'device-b',
  credentialValid: true, authorityScope: new Set(['receive_orders']),
});

class StatefulRepository implements RelayRepository {
  writes = 0;
  acks = 0;
  private readonly byIdempotency = new Map<string, StoredRelayEnvelope>();
  mailbox: RelayMailboxEntry[] = [];
  transientFailures = 0;
  findByIdempotency(senderBusinessId: string, idempotencyKey: string) {
    return Promise.resolve(this.byIdempotency.get(`${senderBusinessId}:${idempotencyKey}`) ?? null);
  }
  listMailboxEntries(_recipient: RecipientRoutingKey, afterSequence: number | null, limit: number) {
    const items = this.mailbox
      .filter((entry) => entry.mailboxSequence > (afterSequence ?? 0))
      .sort((left, right) => left.mailboxSequence - right.mailboxSequence);
    return Promise.resolve(items.slice(0, limit));
  }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) {
    if (this.transientFailures > 0) { this.transientFailures -= 1; throw new Error('transient database unavailable'); }
    this.writes += 1;
    const delivery = { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted' as const, mailboxSequence: this.mailbox.length + 1, createdAt: acceptance.acceptedAt };
    const stored = { submission: value, acceptance, delivery };
    this.byIdempotency.set(`${value.senderBusinessId}:${value.idempotencyKey}`, stored);
    this.mailbox.push({
      envelopeId: value.envelopeId, mailboxSequence: delivery.mailboxSequence, objectType: value.objectType, objectId: value.objectId, objectVersion: value.objectVersion,
      senderBusinessId: value.senderBusinessId, senderActorId: value.senderActorId, senderDeviceId: value.senderDeviceId,
      status: 'relay_accepted', acceptedAt: acceptance.acceptedAt, acceptanceId: acceptance.acceptanceId, authenticatedEnvelope: value.authenticatedEnvelope,
      commercialContent: value.commercialContent, commercialContentType: value.commercialContentType, commercialContentVersion: value.commercialContentVersion,
    });
    return Promise.resolve(stored);
  }
  recordAcknowledgement(request: RelayAcknowledgementSubmission, _recordedAt: Date): Promise<RelayAcknowledgement> {
    const existing = this.acksByEnvelope.get(request.envelopeId);
    if (existing) return Promise.resolve(existing);
    this.acks += 1;
    const entryIndex = this.mailbox.findIndex((item) => item.envelopeId === request.envelopeId);
    if (entryIndex >= 0) {
      const entry = this.mailbox[entryIndex]!;
      this.mailbox[entryIndex] = { ...entry, status: 'delivered' };
    }
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
  envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']),
  commercialContent: value.commercialContent, commercialContentType: value.commercialContentType, commercialContentVersion: value.commercialContentVersion,
}) };
const mailboxVerifier: RelayMailboxVerifier = { verify: (value) => Promise.resolve({
  ...mailboxVerified(), recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  recipientActorId: value.recipientActorId, recipientDeviceId: value.recipientDeviceId,
}) };
const acknowledgementVerifier: RelayAcknowledgementVerifier = { verify: (value) => Promise.resolve({ ...ackVerified(), ...value }) };
const apps: ReturnType<typeof buildRelayService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

describe('relay failure and recovery attacks', () => {
  it('preserves canonical order on duplicate sender submission and relay restart replay', async () => {
    const repository = new StatefulRepository();
    const service = new AcceptRelaySubmission(repository, verifier, issuer());
    const first = await service.execute(submission());
    const duplicate = await service.execute(submission());
    expect(first).toBe(duplicate);
    expect(repository.writes).toBe(1);
    expect(first.acceptance.status).toBe('relay_accepted');
    expect(JSON.stringify(first)).not.toContain('seen');
  });

  it('survives transient persistence failure then idempotent retry without duplicate acceptance', async () => {
    const repository = new StatefulRepository();
    repository.transientFailures = 1;
    const service = new AcceptRelaySubmission(repository, verifier, issuer());
    await expect(service.execute(submission())).rejects.toThrow('transient');
    const recovered = await service.execute(submission());
    expect(recovered.acceptance.acceptanceId).toBe('accept-1');
    expect(repository.writes).toBe(1);
  });

  it('keeps recipient mailbox durable while offline and accepts delayed acknowledgement once', async () => {
    const repository = new StatefulRepository();
    await new AcceptRelaySubmission(repository, verifier, issuer()).execute(submission());
    const mailbox = new FetchRecipientMailbox(repository, mailboxVerifier);
    const page = await mailbox.execute({
      recipient: { businessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId') },
      recipientActorId: 'actor-b', recipientDeviceId: 'device-b', cursor: null, limit: 25,
      authenticatedRequest: new Uint8Array([1]),
    });
    expect(page.items).toHaveLength(1);
    const ack = new RecordRelayAcknowledgement(repository, acknowledgementVerifier);
    const request = {
      envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), recipientBusinessId: 'business-b',
      recipientActorId: 'actor-b', recipientDeviceId: 'device-b', receivedAt: new Date(50),
      authenticatedRequest: new Uint8Array([1]),
    };
    await ack.execute(request);
    await ack.execute(request);
    expect(repository.acks).toBe(1);
    expect(JSON.stringify(page.items[0]!)).not.toContain('seen');
    expect(JSON.stringify(page.items[0]!)).not.toContain('confirmed');
  });

  it('rejects stale or revoked credentials without false sent or delivered state', async () => {
    const repository = new StatefulRepository();
    const stale: RelaySubmissionVerifier = { verify: async () => ({ ...verified(), credentialValid: false }) };
    await expect(new AcceptRelaySubmission(repository, stale, issuer()).execute(submission())).rejects.toThrow('rejected');
    expect(repository.writes).toBe(0);
    const revokedMailbox: RelayMailboxVerifier = { verify: () => Promise.resolve({ ...mailboxVerified(), credentialValid: false }) };
    await expect(new FetchRecipientMailbox(repository, revokedMailbox).execute({
      recipient: { businessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId') },
      recipientActorId: 'actor-b', recipientDeviceId: 'device-b', cursor: null, limit: 25,
      authenticatedRequest: new Uint8Array([1]),
    })).rejects.toThrow('rejected');
  });

  it('absorbs retry storms with bounded overload while preserving idempotent acceptance', async () => {
    const repository = new StatefulRepository();
    const limiter = new PartitionedRelayIngressLimiter(2, 60_000);
    const app = buildRelayService({ repository, verifier, mailboxVerifier, acknowledgementVerifier, ingressLimiter: limiter, issuer: issuer() });
    apps.push(app);
    const body = {
      protocolVersion: 1, envelopeId: 'env-1', idempotencyKey: 'intent-1', objectType: 'ORDER', objectId: 'order-1', objectVersion: 1,
      senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipientBusinessId: 'business-b', mailboxId: 'orders',
      authenticatedEnvelope: Buffer.from([1]).toString('base64'), commercialContent: '{}',
      commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 3, submittedAt: new Date(1).toISOString(),
    };
    const storm = await Promise.all(Array.from({ length: 5 }, (_, index) => app.inject({
      method: 'POST', url: '/v1/relay/envelopes',
      payload: { ...body, envelopeId: `env-${index + 1}`, idempotencyKey: `intent-${index + 1}` },
    })));
    const accepted = storm.filter((response) => response.statusCode === 200);
    const overloaded = storm.filter((response) => response.statusCode === 503);
    expect(accepted.length).toBe(2);
    expect(overloaded.length).toBe(3);
    const replay = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    expect(replay.statusCode).toBe(200);
    expect(repository.writes).toBe(2);
    const blocked = await app.inject({
      method: 'POST', url: '/v1/relay/envelopes',
      payload: { ...body, envelopeId: 'env-6', idempotencyKey: 'intent-6' },
    });
    expect(blocked.statusCode).toBe(503);
    expect(blocked.headers['retry-after']).toBeTruthy();
  });

  it('does not infer seen or confirmed from transport delivery acknowledgement', async () => {
    const repository = new StatefulRepository();
    await new AcceptRelaySubmission(repository, verifier, issuer()).execute(submission());
    const app = buildRelayService({ repository, verifier, mailboxVerifier, acknowledgementVerifier, issuer: issuer() });
    apps.push(app);
    const ack = await app.inject({
      method: 'POST', url: '/v1/relay/acknowledgements',
      payload: { envelopeId: 'env-1', recipientBusinessId: 'business-b', recipientActorId: 'actor-b', recipientDeviceId: 'device-b', receivedAt: new Date(40).toISOString(), authenticatedRequest: Buffer.from([1]).toString('base64') },
    });
    expect(ack.statusCode).toBe(200);
    expect(ack.json().status).toBe('delivered');
    expect(JSON.stringify(ack.json())).not.toContain('seen');
    expect(JSON.stringify(ack.json())).not.toContain('confirmed');
  });
});
