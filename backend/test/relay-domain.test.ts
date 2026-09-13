import { describe, expect, it } from 'vitest';
import { relayIdentifier, validateRelaySubmission, encodeRelayCursor, decodeRelayCursor, type RelaySubmission } from '../services/relay/src/domain/relay.js';

const submission = (): RelaySubmission => ({
  envelopeId: relayIdentifier('envelope-1', 'RelayEnvelopeId'), protocolVersion: 1,
  objectType: 'ORDER', objectId: 'order-1', objectVersion: 2,
  senderBusinessId: 'business-a', senderActorId: 'actor-a', senderDeviceId: 'device-a',
  recipient: { businessId: 'business-b', mailboxId: relayIdentifier('business-b:orders', 'MailboxId') },
  authenticatedEnvelope: new Uint8Array([1, 2, 3]), commercialContent: '{}', commercialContentType: 'application/vnd.venture.order-snapshot+json', commercialContentVersion: 3,
  idempotencyKey: 'send-order-1-v2', submittedAt: new Date(1),
});

describe('relay domain contract', () => {
  it('keeps transport identity, routing, and canonical object reference distinct', () => {
    const value = submission();
    expect(() => validateRelaySubmission(value)).not.toThrow();
    expect(value.envelopeId).not.toBe(value.objectId);
    expect(value.recipient.mailboxId).not.toBe(value.recipient.businessId);
  });

  it('rejects unsupported, unbounded, and incomplete submissions', () => {
    expect(() => validateRelaySubmission({ ...submission(), protocolVersion: 2 })).toThrow('protocol');
    expect(() => validateRelaySubmission({ ...submission(), authenticatedEnvelope: new Uint8Array(256 * 1024 + 1) })).toThrow('bounds');
    expect(() => validateRelaySubmission({ ...submission(), recipient: { ...submission().recipient, businessId: '' } })).toThrow('Recipient');
  });

  it('accepts current commercial content version 3', () => {
    expect(() => validateRelaySubmission({ ...submission(), commercialContentVersion: 3 })).not.toThrow();
  });

  it('rejects the stale v2 commercial content version -- v2 is no longer current production', () => {
    expect(() => validateRelaySubmission({ ...submission(), commercialContentVersion: 2 })).toThrow('content type and version');
  });

  it('rejects an unknown/future commercial content version', () => {
    expect(() => validateRelaySubmission({ ...submission(), commercialContentVersion: 4 })).toThrow('content type and version');
  });

  it('encodes mailbox cursors as bounded sequence checkpoints', () => {
    expect(encodeRelayCursor(7)).toBe('7');
    expect(decodeRelayCursor(encodeRelayCursor(7))).toBe(7);
    expect(() => decodeRelayCursor(relayIdentifier('bad', 'RelayCursor'))).toThrow('cursor');
  });
});
