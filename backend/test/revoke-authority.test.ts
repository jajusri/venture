import { describe, expect, it } from 'vitest';
import { AuthorityRevocationService, type AuthorityMutationStore } from '../services/trust/src/application/revoke-authority.js';
import { AuthorityScope, identifier, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
class Store implements AuthorityMutationStore { membershipExpected?: number; deviceExpected?: number; updateMembership(epoch: number) { this.membershipExpected = epoch; return Promise.resolve(true); } updateDevice(epoch: number) { this.deviceExpected = epoch; return Promise.resolve(true); } }
const businessId = identifier('business-1', 'BusinessId'); const actorId = identifier('actor-1', 'ActorId'); const membershipId = identifier('membership-1', 'MembershipId');
const membership: BusinessMembership = { membershipId, businessId, actorId, status: 'active', authorityScope: new AuthorityScope(['issue_credentials', 'send_orders']), authorityEpoch: { value: 3 }, createdAt: new Date(1), modifiedAt: new Date(1) };
const device: RegisteredBusinessDevice = { businessId, actorId, membershipId, deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('key-1', 'DeviceKeyId'), deviceKeyVersion: 1, publicKey: new Uint8Array([1]), publicKeyFingerprint: 'fp', status: 'active', authorityEpoch: { value: 3 }, createdAt: new Date(1) };
describe('authority revocation epochs', () => {
  it('suspends membership and increments epoch without rewriting identity', async () => {
    const store = new Store(); const updated = await new AuthorityRevocationService(store, () => new Date(10)).suspendMembership(membership);
    expect(updated.status).toBe('suspended'); expect(updated.authorityEpoch.value).toBe(4); expect(updated.membershipId).toBe(membership.membershipId); expect(updated.createdAt).toBe(membership.createdAt); expect(store.membershipExpected).toBe(3);
  });
  it('scope changes and device revocation invalidate future credentials by epoch', async () => {
    const service = new AuthorityRevocationService(new Store(), () => new Date(10));
    expect((await service.changeScope(membership, new AuthorityScope(['send_orders']))).authorityEpoch.value).toBe(4);
    const revoked = await service.revokeDevice(device); expect(revoked.authorityEpoch.value).toBe(4); expect(revoked.deviceKeyId).toBe(device.deviceKeyId); expect(revoked.revokedAt).toEqual(new Date(10));
  });
});
