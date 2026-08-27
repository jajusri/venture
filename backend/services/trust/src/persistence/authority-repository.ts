import type { DatabaseSession } from '../../../../packages/persistence/src/database.js';
import type { BusinessId, ActorId, DeviceId, MembershipId } from '../domain/authority.js';

export interface AuthorityRow extends Record<string, unknown> { readonly id: string; readonly status: string; readonly authority_epoch: string }
export class AuthorityRepository {
  constructor(private readonly database: DatabaseSession) {}
  async findBusiness(businessId: BusinessId): Promise<AuthorityRow | null> {
    const result = await this.database.query<AuthorityRow>('SELECT business_id AS id, status, authority_epoch FROM trust_business_authority WHERE business_id = $1', [businessId]);
    return result.rows[0] ?? null;
  }
  async findMembership(businessId: BusinessId, actorId: ActorId): Promise<AuthorityRow | null> {
    const result = await this.database.query<AuthorityRow>('SELECT membership_id AS id, status, authority_epoch FROM trust_business_membership WHERE business_id = $1 AND actor_id = $2', [businessId, actorId]);
    return result.rows[0] ?? null;
  }
  async findMembershipById(membershipId: MembershipId): Promise<AuthorityRow | null> {
    const result = await this.database.query<AuthorityRow>('SELECT membership_id AS id, status, authority_epoch FROM trust_business_membership WHERE membership_id = $1', [membershipId]);
    return result.rows[0] ?? null;
  }
  async findActiveDevice(businessId: BusinessId, deviceId: DeviceId): Promise<AuthorityRow | null> {
    const result = await this.database.query<AuthorityRow>("SELECT device_key_id AS id, status, authority_epoch FROM trust_registered_device WHERE business_id = $1 AND device_id = $2 AND status = 'active' ORDER BY device_key_version DESC LIMIT 1", [businessId, deviceId]);
    return result.rows[0] ?? null;
  }
}
