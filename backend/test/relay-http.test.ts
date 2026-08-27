import { afterEach, describe, expect, it } from 'vitest';
import type { RelaySubmissionVerifier } from '../services/relay/src/application/accept-submission.js';
import { SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import { buildRelayService } from '../services/relay/src/http/app.js';
import type { RelayRepository, StoredRelayEnvelope } from '../services/relay/src/persistence/relay-repository.js';
import type { RelayAcceptance, RelaySubmission } from '../services/relay/src/domain/relay.js';

class MemoryRepository implements RelayRepository {
  value: StoredRelayEnvelope | null = null; writes = 0;
  findByIdempotency() { return Promise.resolve(this.value); }
  persist(value: RelaySubmission, acceptance: RelayAcceptance) {
    this.writes++; this.value = { submission: value, acceptance,
      delivery: { envelopeId: value.envelopeId, recipient: value.recipient, status: 'relay_accepted', mailboxSequence: 1, createdAt: acceptance.acceptedAt } };
    return Promise.resolve(this.value);
  }
}
const verifier: RelaySubmissionVerifier = { verify: (value) => Promise.resolve({ protocolVersion: 1, envelopeId: value.envelopeId, senderBusinessId: value.senderBusinessId,
  senderActorId: value.senderActorId, senderDeviceId: value.senderDeviceId, recipientBusinessId: value.recipient.businessId, mailboxId: value.recipient.mailboxId,
  envelopeIntegrityValid: true, credentialValid: true, authorityScope: new Set(['send_orders']) }) };
const issuer = () => new SignedRelayAcceptanceIssuer({ sign: () => Promise.resolve({ relayId: 'relay-1', profile: 'test-v1', evidence: new Uint8Array([9]) }) }, () => 'accept-1');
const body = {
  protocolVersion: 1, envelopeId: 'env-1', idempotencyKey: 'intent-1', objectType: 'ORDER', objectId: 'order-1', objectVersion: 3,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipientBusinessId: 'business-b', mailboxId: 'orders',
  authenticatedEnvelope: Buffer.from([4, 5]).toString('base64'), submittedAt: new Date(1).toISOString(),
};
const apps: ReturnType<typeof buildRelayService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

describe('relay HTTP submission', () => {
  it('accepts an authenticated envelope once and returns the same acceptance on retry', async () => {
    const repository = new MemoryRepository();
    const app = buildRelayService({ repository, verifier, issuer: issuer(), now: () => new Date(10) }); apps.push(app);
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
    const app = buildRelayService({ repository, verifier: { verify: async (value) => ({ ...(await verifier.verify(value)), credentialValid: false }) }, issuer: issuer() });
    apps.push(app);
    const response = await app.inject({ method: 'POST', url: '/v1/relay/envelopes', payload: body });
    expect(response.statusCode).toBe(403);
    expect(repository.writes).toBe(0);
  });
});
