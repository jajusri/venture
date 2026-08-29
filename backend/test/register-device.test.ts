import { createHash } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { RegisterBusinessDevice, type DeviceRegistrationStore } from '../services/trust/src/application/register-device.js';
import { AuthorityScope, identifier, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';

class Store implements DeviceRegistrationStore {
  value: RegisteredBusinessDevice | null = null;
  find() { return Promise.resolve(this.value); }
  save(device: RegisteredBusinessDevice) { this.value = device; return Promise.resolve(device); }
}
const actorId = identifier('actor-1', 'ActorId');
const membership: BusinessMembership = { membershipId: identifier('membership-1', 'MembershipId'), businessId: identifier('business-1', 'BusinessId'), actorId, status: 'active', authorityScope: new AuthorityScope(['register_devices']), authorityEpoch: { value: 3 }, createdAt: new Date(1), modifiedAt: new Date(1) };
const publicKey = new Uint8Array(65).fill(7);
const input = { principal: { actorId, verificationId: 'verified-1', verifiedAt: new Date(1) }, membership, deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('key-1', 'DeviceKeyId'), deviceKeyVersion: 1, publicKey, publicKeyFingerprint: createHash('sha256').update(publicKey).digest('base64') };
describe('business device registration', () => {
  it('registers after verified membership authority and is idempotent', async () => {
    const service = new RegisterBusinessDevice(new Store()); const first = await service.execute(input); const retry = await service.execute(input);
    expect(retry).toBe(first); expect(first.authorityEpoch.value).toBe(3);
  });
  it('rejects conflicting key for the same device version', async () => {
    const service = new RegisterBusinessDevice(new Store()); await service.execute(input);
    const changed = new Uint8Array(65).fill(8);
    await expect(service.execute({ ...input, deviceKeyId: identifier('key-2', 'DeviceKeyId'), publicKey: changed, publicKeyFingerprint: createHash('sha256').update(changed).digest('base64') })).rejects.toThrow('Conflicting');
  });
  it('rejects self-asserted business authority and false fingerprints', async () => {
    const service = new RegisterBusinessDevice(new Store());
    await expect(service.execute({ ...input, principal: { ...input.principal, actorId: identifier('other', 'ActorId') } })).rejects.toThrow('membership');
    await expect(service.execute({ ...input, publicKeyFingerprint: 'false' })).rejects.toThrow('fingerprint');
  });
  it("rejects a non-active existing registration that is otherwise identical (Codex round 4: EXISTENCE IS NOT AUTHORITY applies to status too -- a previously revoked row must not be handed back as idempotent active authority)", async () => {
    const store = new Store();
    store.value = { businessId: membership.businessId, actorId, membershipId: membership.membershipId, deviceId: input.deviceId,
      deviceKeyId: input.deviceKeyId, deviceKeyVersion: input.deviceKeyVersion, publicKey, publicKeyFingerprint: input.publicKeyFingerprint,
      status: 'revoked', authorityEpoch: membership.authorityEpoch, createdAt: new Date(1) };
    const service = new RegisterBusinessDevice(store);
    await expect(service.execute(input)).rejects.toThrow('Conflicting device key registration');
  });
  it('rejects an existing registration whose authorityEpoch does not match the intended one, even when every other field is identical', async () => {
    const store = new Store();
    store.value = { businessId: membership.businessId, actorId, membershipId: membership.membershipId, deviceId: input.deviceId,
      deviceKeyId: input.deviceKeyId, deviceKeyVersion: input.deviceKeyVersion, publicKey, publicKeyFingerprint: input.publicKeyFingerprint,
      status: 'active', authorityEpoch: { value: 99 }, createdAt: new Date(1) };
    const service = new RegisterBusinessDevice(store);
    await expect(service.execute(input)).rejects.toThrow('Conflicting device key registration');
  });
});
