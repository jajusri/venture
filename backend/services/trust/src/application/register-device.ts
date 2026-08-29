import { createHash } from 'node:crypto';
import type { BusinessMembership, DeviceId, DeviceKeyId, RegisteredBusinessDevice, VerifiedUserPrincipal } from '../domain/authority.js';

export interface DeviceRegistrationStore {
  find(businessId: string, deviceId: string, keyVersion: number): Promise<RegisteredBusinessDevice | null>;
  save(device: RegisteredBusinessDevice): Promise<RegisteredBusinessDevice>;
}
export interface RegisterDeviceInput {
  readonly principal: VerifiedUserPrincipal; readonly membership: BusinessMembership; readonly deviceId: DeviceId;
  readonly deviceKeyId: DeviceKeyId; readonly deviceKeyVersion: number; readonly publicKey: Uint8Array;
  readonly publicKeyFingerprint: string;
}
export class RegisterBusinessDevice {
  constructor(private readonly store: DeviceRegistrationStore, private readonly now: () => Date = () => new Date()) {}
  async execute(input: RegisterDeviceInput): Promise<RegisteredBusinessDevice> {
    if (input.principal.actorId !== input.membership.actorId || input.membership.status !== 'active') throw new Error('Active actor membership is required');
    if (!input.membership.authorityScope.permits('register_devices')) throw new Error('Membership cannot register devices');
    if (input.deviceKeyVersion < 1 || input.publicKey.length < 32) throw new Error('Valid device public identity is required');
    const fingerprint = createHash('sha256').update(input.publicKey).digest('base64');
    if (fingerprint !== input.publicKeyFingerprint) throw new Error('Device public-key fingerprint mismatch');
    const existing = await this.store.find(input.membership.businessId, input.deviceId, input.deviceKeyVersion);
    if (existing) {
      // authorityEpoch is persisted, authority-significant state (see the Postgres store's own
      // `isEquivalentDeviceRegistration` doc comment) -- an existing row from a different authority
      // epoch is not the SAME registration this call intends, even if every other field matches.
      if (existing.actorId !== input.principal.actorId || existing.membershipId !== input.membership.membershipId || existing.deviceKeyId !== input.deviceKeyId || existing.publicKeyFingerprint !== fingerprint || !Buffer.from(existing.publicKey).equals(input.publicKey) || existing.authorityEpoch.value !== input.membership.authorityEpoch.value) throw new Error('Conflicting device key registration');
      return existing;
    }
    return this.store.save({ businessId: input.membership.businessId, actorId: input.principal.actorId, membershipId: input.membership.membershipId,
      deviceId: input.deviceId, deviceKeyId: input.deviceKeyId, deviceKeyVersion: input.deviceKeyVersion, publicKey: input.publicKey,
      publicKeyFingerprint: fingerprint, status: 'active', authorityEpoch: input.membership.authorityEpoch, createdAt: this.now() });
  }
}
