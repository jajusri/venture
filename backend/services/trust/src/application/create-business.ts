import { randomUUID } from 'node:crypto';
import { AuthorityScope, identifier, type BusinessId, type BusinessMembership, type VerifiedUserPrincipal } from '../domain/authority.js';

export interface BusinessCreationAuditEvent { readonly eventId: string; readonly kind: 'business_authority_created'; readonly businessId: BusinessId; readonly actorId: string; readonly occurredAt: Date }
export interface CreatedBusinessAuthority { readonly businessId: BusinessId; readonly membership: BusinessMembership; readonly authorityEpoch: number; readonly auditEvent: BusinessCreationAuditEvent }
export interface BusinessBootstrapStore {
  findByCreationIntent(actorId: string, intentId: string): Promise<CreatedBusinessAuthority | null>;
  createAtomically(intentId: string, result: CreatedBusinessAuthority): Promise<CreatedBusinessAuthority>;
}
export interface CreateBusinessInput { readonly principal: VerifiedUserPrincipal; readonly intentId: string; readonly authorityDisplayName: string }

export class CreateBusiness {
  constructor(private readonly store: BusinessBootstrapStore, private readonly now: () => Date = () => new Date(), private readonly newId: () => string = randomUUID) {}
  async execute(input: CreateBusinessInput): Promise<CreatedBusinessAuthority> {
    if (!input.principal.verificationId.trim() || input.principal.verifiedAt > this.now()) throw new Error('A verified VENTURE user principal is required');
    if (!input.intentId.trim() || input.intentId.length > 128) throw new Error('Explicit bounded creation intent is required');
    if (!input.authorityDisplayName.trim() || input.authorityDisplayName.length > 160) throw new Error('Authority display name is required');
    const existing = await this.store.findByCreationIntent(input.principal.actorId, input.intentId);
    if (existing) return existing;
    const occurredAt = this.now();
    const businessId = identifier(this.newId(), 'BusinessId');
    const membership: BusinessMembership = {
      membershipId: identifier(this.newId(), 'MembershipId'), businessId, actorId: input.principal.actorId,
      status: 'active', authorityScope: new AuthorityScope(['manage_memberships', 'approve_memberships', 'register_devices', 'revoke_devices', 'issue_credentials', 'send_orders']),
      authorityEpoch: { value: 1 }, createdAt: occurredAt, modifiedAt: occurredAt,
    };
    return this.store.createAtomically(input.intentId, { businessId, membership, authorityEpoch: 1,
      auditEvent: { eventId: this.newId(), kind: 'business_authority_created', businessId, actorId: input.principal.actorId, occurredAt } });
  }
}
