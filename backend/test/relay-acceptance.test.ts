import { createSign, createVerify, generateKeyPairSync } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { acceptanceSigningPayload, SignedRelayAcceptanceIssuer } from '../services/relay/src/application/acceptance-evidence.js';
import { relayIdentifier, type RelaySubmission } from '../services/relay/src/domain/relay.js';

const submission: RelaySubmission = { envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), protocolVersion: 1, objectType: 'ORDER', objectId: 'order-1', objectVersion: 4,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a', recipient: { businessId: 'business-b', mailboxId: relayIdentifier('orders', 'MailboxId') },
  authenticatedEnvelope: new Uint8Array([1]), commercialContent: '{}', commercialContentType: 'application/vnd.budcom.order-snapshot+json', commercialContentVersion: 3,
  idempotencyKey: 'intent', submittedAt: new Date(1) };

describe('durable relay acceptance evidence', () => {
  it('binds canonical reference, sender, recipient and acceptance time without claiming delivery', async () => {
    const pair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    const issuer = new SignedRelayAcceptanceIssuer({ sign: (payload) => Promise.resolve({ relayId: 'relay-1', profile: 'P256-SHA256-v1',
      evidence: createSign('SHA256').update(payload).sign(pair.privateKey) }) }, () => 'accept-1');
    const acceptance = await issuer.issue(submission, new Date(10));
    const unsigned = { acceptanceId: acceptance.acceptanceId, envelopeId: acceptance.envelopeId, objectType: acceptance.objectType,
      objectId: acceptance.objectId, objectVersion: acceptance.objectVersion, senderBusinessId: acceptance.senderBusinessId,
      recipientBusinessId: acceptance.recipientBusinessId, acceptedAt: acceptance.acceptedAt, status: acceptance.status };
    expect(createVerify('SHA256').update(acceptanceSigningPayload(unsigned)).verify(pair.publicKey, acceptance.evidence)).toBe(true);
    expect(acceptance.status).toBe('relay_accepted');
    expect(JSON.stringify(acceptance)).not.toContain('delivered');
    expect(JSON.stringify(acceptance)).not.toContain('seen');
  });

  it('fails closed when the relay signer returns empty evidence', async () => {
    const issuer = new SignedRelayAcceptanceIssuer({
      sign: () => Promise.resolve({ relayId: 'relay-1', profile: 'P256-SHA256-v1', evidence: new Uint8Array() }),
    });
    await expect(issuer.issue(submission, new Date(10))).rejects.toThrow('failed closed');
  });
});
