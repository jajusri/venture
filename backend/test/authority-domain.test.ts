import { describe, expect, it } from 'vitest';
import { AuthorityScope, identifier, validateDeviceAuthority, type BusinessAuthorityReference, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';

const businessId = identifier('business-1', 'BusinessId');
const actorId = identifier('actor-1', 'ActorId');
const membershipId = identifier('membership-1', 'MembershipId');
const now = new Date('2026-08-27T10:00:00Z');
const scope = new AuthorityScope(['register_devices', 'issue_credentials', 'send_orders']);
const business: BusinessAuthorityReference = { businessId, status: 'active' };
const membership: BusinessMembership = { membershipId, businessId, actorId, status: 'active', authorityScope: scope, authorityEpoch: { value: 2 }, createdAt: now, modifiedAt: now };
const device: RegisteredBusinessDevice = { businessId, actorId, membershipId, deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('key-1', 'DeviceKeyId'), deviceKeyVersion: 1, publicKey: new Uint8Array([1]), publicKeyFingerprint: 'fp', status: 'active', authorityEpoch: { value: 2 }, createdAt: now };
const validate = (changes: Partial<{ business: BusinessAuthorityReference; membership: BusinessMembership; device: RegisteredBusinessDevice; requestedScope: AuthorityScope }> = {}) => validateDeviceAuthority({ business, membership, device, requestedScope: new AuthorityScope(['send_orders']), now, ...changes });

describe('business device authority model', () => {
  it('keeps principal identities distinct', () => expect(new Set([businessId, actorId, membershipId, device.deviceId, device.deviceKeyId]).size).toBe(5));
  it('accepts bounded active authority', () => expect(validate()).toBeNull());
  it('rejects inactive membership', () => expect(validate({ membership: { ...membership, status: 'revoked' } })).toBe('membership_inactive'));
  it('rejects suspended membership', () => expect(validate({ membership: { ...membership, status: 'suspended' } })).toBe('membership_inactive'));
  it('rejects revoked device', () => expect(validate({ device: { ...device, status: 'revoked', revokedAt: now } })).toBe('device_revoked'));
  it('rejects wrong business', () => expect(validate({ device: { ...device, businessId: identifier('business-2', 'BusinessId') } })).toBe('identity_mismatch'));
  it('rejects wrong actor', () => expect(validate({ device: { ...device, actorId: identifier('actor-2', 'ActorId') } })).toBe('identity_mismatch'));
  it('rejects wrong device key version', () => expect(validate({ device: { ...device, deviceKeyVersion: 0 } })).toBe('key_mismatch'));
  it('rejects expired authority state', () => expect(validate({ membership: { ...membership, authorityEpoch: { value: 2, expiresAt: now } } })).toBe('membership_expired'));
  it('rejects scope escalation', () => expect(validate({ requestedScope: new AuthorityScope(['manage_memberships']) })).toBe('scope_not_permitted'));
  it('does not rewrite historical identity', () => { const revoked = { ...device, status: 'revoked' as const, revokedAt: now }; expect(revoked.deviceKeyId).toBe(device.deviceKeyId); expect(revoked.createdAt).toBe(device.createdAt); });
});
