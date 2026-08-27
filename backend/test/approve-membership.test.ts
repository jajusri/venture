import { describe, expect, it } from 'vitest';
import { ApproveMembership, type MembershipApprovalStore } from '../services/trust/src/application/approve-membership.js';
import { AuthorityScope, identifier, type BusinessMembership, type VerifiedUserPrincipal } from '../services/trust/src/domain/authority.js';

class Store implements MembershipApprovalStore {
  readonly values = new Map<string, BusinessMembership>();
  findApproved(intentId: string) { return Promise.resolve(this.values.get(intentId) ?? null); }
  saveApproved(intentId: string, membership: BusinessMembership) { this.values.set(intentId, membership); return Promise.resolve(membership); }
}
const principal = (id: string): VerifiedUserPrincipal => ({ actorId: identifier(id, 'ActorId'), verificationId: `verified-${id}`, verifiedAt: new Date(1) });
const approver = (scope: AuthorityScope): BusinessMembership => ({ membershipId: identifier('membership-owner', 'MembershipId'), businessId: identifier('business-1', 'BusinessId'), actorId: identifier('owner', 'ActorId'), status: 'active', authorityScope: scope, authorityEpoch: { value: 1 }, createdAt: new Date(1), modifiedAt: new Date(1) });
describe('membership approval authority', () => {
  it('approves only a bounded subset and is idempotent', async () => {
    const authority = new AuthorityScope(['approve_memberships', 'register_devices', 'send_orders']); const service = new ApproveMembership(new Store());
    const input = { approverPrincipal: principal('owner'), approverMembership: approver(authority), targetPrincipal: principal('staff'), requestedScope: new AuthorityScope(['send_orders']), intentId: 'approve-1' };
    const first = await service.execute(input); const retry = await service.execute(input);
    expect(first.membershipId).toBe(retry.membershipId); expect(first.authorityScope.permits('approve_memberships')).toBe(false);
  });
  it('rejects privilege escalation', async () => {
    const service = new ApproveMembership(new Store());
    await expect(service.execute({ approverPrincipal: principal('owner'), approverMembership: approver(new AuthorityScope(['approve_memberships'])), targetPrincipal: principal('staff'), requestedScope: new AuthorityScope(['manage_memberships']), intentId: 'bad' })).rejects.toThrow('exceeds');
  });
  it('rejects an inactive or unrelated approver', async () => {
    const membership = { ...approver(new AuthorityScope(['approve_memberships'])), status: 'suspended' as const };
    await expect(new ApproveMembership(new Store()).execute({ approverPrincipal: principal('owner'), approverMembership: membership, targetPrincipal: principal('staff'), requestedScope: new AuthorityScope(['approve_memberships']), intentId: 'bad-2' })).rejects.toThrow('not active');
  });
});
