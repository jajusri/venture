import { describe, expect, it } from 'vitest';
import { CreateBusiness, type BusinessBootstrapStore, type CreatedBusinessAuthority } from '../services/trust/src/application/create-business.js';
import { identifier } from '../services/trust/src/domain/authority.js';

class MemoryBootstrapStore implements BusinessBootstrapStore {
  private readonly values = new Map<string, CreatedBusinessAuthority>();
  findByCreationIntent(actorId: string, intentId: string): Promise<CreatedBusinessAuthority | null> { return Promise.resolve(this.values.get(`${actorId}:${intentId}`) ?? null); }
  createAtomically(intentId: string, result: CreatedBusinessAuthority): Promise<CreatedBusinessAuthority> { this.values.set(`${result.membership.actorId}:${intentId}`, result); return Promise.resolve(result); }
}
describe('CreateBusiness', () => {
  it('creates initial BUDCOM account owner authority for a verified principal', async () => {
    const service = new CreateBusiness(new MemoryBootstrapStore(), () => new Date(10), (() => { let id = 0; return () => `id-${++id}`; })());
    const result = await service.execute({ principal: { actorId: identifier('actor-1', 'ActorId'), verificationId: 'verified-email-1', verifiedAt: new Date(1) }, intentId: 'intent-1', authorityDisplayName: 'Acme account' });
    expect(result.authorityEpoch).toBe(1);
    expect(result.membership.authorityScope.permits('manage_memberships')).toBe(true);
    expect(result.auditEvent.kind).toBe('business_authority_created');
  });
  it('returns the same authority for an intentional retry', async () => {
    const service = new CreateBusiness(new MemoryBootstrapStore(), () => new Date(10));
    const input = { principal: { actorId: identifier('actor-1', 'ActorId'), verificationId: 'verified-1', verifiedAt: new Date(1) }, intentId: 'retry-1', authorityDisplayName: 'Acme' };
    const first = await service.execute(input); const second = await service.execute(input);
    expect(second.businessId).toBe(first.businessId); expect(second.membership.membershipId).toBe(first.membership.membershipId);
  });
  it('rejects missing explicit intent', async () => {
    const service = new CreateBusiness(new MemoryBootstrapStore());
    await expect(service.execute({ principal: { actorId: identifier('actor-1', 'ActorId'), verificationId: 'verified', verifiedAt: new Date() }, intentId: '', authorityDisplayName: 'Acme' })).rejects.toThrow('intent');
  });
});
