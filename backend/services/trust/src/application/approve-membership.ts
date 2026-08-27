import { randomUUID } from 'node:crypto';
import { identifier, type AuthorityScope, type BusinessMembership, type VerifiedUserPrincipal } from '../domain/authority.js';

export interface MembershipApprovalStore {
  findApproved(intentId: string): Promise<BusinessMembership | null>;
  saveApproved(intentId: string, membership: BusinessMembership, approvedByMembershipId: string): Promise<BusinessMembership>;
}
export interface ApproveMembershipInput {
  readonly approverPrincipal: VerifiedUserPrincipal; readonly approverMembership: BusinessMembership;
  readonly targetPrincipal: VerifiedUserPrincipal; readonly requestedScope: AuthorityScope; readonly intentId: string;
}
export class ApproveMembership {
  constructor(private readonly store: MembershipApprovalStore, private readonly now: () => Date = () => new Date(), private readonly newId: () => string = randomUUID) {}
  async execute(input: ApproveMembershipInput): Promise<BusinessMembership> {
    if (!input.intentId.trim()) throw new Error('Approval intent is required');
    const existing = await this.store.findApproved(input.intentId); if (existing) return existing;
    if (input.approverPrincipal.actorId !== input.approverMembership.actorId || input.approverMembership.status !== 'active') throw new Error('Approver membership is not active');
    if (!input.approverMembership.authorityScope.permits('approve_memberships')) throw new Error('Approver lacks membership approval authority');
    if (!input.requestedScope.isSubsetOf(input.approverMembership.authorityScope)) throw new Error('Requested authority exceeds approver authority');
    if (input.targetPrincipal.actorId === input.approverPrincipal.actorId && !input.approverMembership.authorityScope.permits('manage_memberships')) throw new Error('Self-elevation is prohibited');
    const timestamp = this.now();
    const membership: BusinessMembership = { membershipId: identifier(this.newId(), 'MembershipId'), businessId: input.approverMembership.businessId,
      actorId: input.targetPrincipal.actorId, status: 'active', authorityScope: input.requestedScope,
      authorityEpoch: { value: 1 }, createdAt: timestamp, modifiedAt: timestamp };
    return this.store.saveApproved(input.intentId, membership, input.approverMembership.membershipId);
  }
}
