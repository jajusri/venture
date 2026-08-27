import type { AuthorityScope, BusinessMembership, RegisteredBusinessDevice } from '../domain/authority.js';

export interface AuthorityMutationStore {
  updateMembership(expectedEpoch: number, membership: BusinessMembership): Promise<boolean>;
  updateDevice(expectedEpoch: number, device: RegisteredBusinessDevice): Promise<boolean>;
}
export class AuthorityRevocationService {
  constructor(private readonly store: AuthorityMutationStore, private readonly now: () => Date = () => new Date()) {}
  async suspendMembership(membership: BusinessMembership): Promise<BusinessMembership> { return this.changeMembership(membership, 'suspended', membership.authorityScope); }
  async revokeMembership(membership: BusinessMembership): Promise<BusinessMembership> { return this.changeMembership(membership, 'revoked', membership.authorityScope); }
  async changeScope(membership: BusinessMembership, scope: AuthorityScope): Promise<BusinessMembership> { return this.changeMembership(membership, membership.status, scope); }
  async revokeDevice(device: RegisteredBusinessDevice): Promise<RegisteredBusinessDevice> {
    if (device.status === 'revoked') return device;
    const updated = { ...device, status: 'revoked' as const, authorityEpoch: { value: device.authorityEpoch.value + 1 }, revokedAt: this.now() };
    if (!await this.store.updateDevice(device.authorityEpoch.value, updated)) throw new Error('Concurrent device authority change');
    return updated;
  }
  private async changeMembership(membership: BusinessMembership, status: BusinessMembership['status'], scope: AuthorityScope): Promise<BusinessMembership> {
    const updated = { ...membership, status, authorityScope: scope, authorityEpoch: { value: membership.authorityEpoch.value + 1 }, modifiedAt: this.now() };
    if (!await this.store.updateMembership(membership.authorityEpoch.value, updated)) throw new Error('Concurrent membership authority change');
    return updated;
  }
}
