import { describe, expect, it } from 'vitest';
import { BusinessDeviceCredentialIssuer, type CredentialIssuanceStore, type IssuedBusinessDeviceCredential, type TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { ApproveMembership, type MembershipApprovalStore } from '../services/trust/src/application/approve-membership.js';
import { AuthorityScope, identifier, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
class IssuanceStore implements CredentialIssuanceStore { findByIntent() { return Promise.resolve(null); } record(_intent: string, value: IssuedBusinessDeviceCredential) { return Promise.resolve(value); } }
class ApprovalStore implements MembershipApprovalStore { findApproved() { return Promise.resolve(null); } saveApproved(_intent: string, value: BusinessMembership) { return Promise.resolve(value); } }
const signer: TrustCredentialSigner = { sign: (build) => { const identity = { issuerId: 'issuer', issuerKeyId: 'key', profile: 'P256-SHA256-v1' }; build(identity); return Promise.resolve({ ...identity, signature: new Uint8Array([1]) }); } };
const businessId = identifier('business-1', 'BusinessId'); const actorId = identifier('staff-1', 'ActorId'); const membershipId = identifier('member-1', 'MembershipId');
const staffScope = new AuthorityScope(['issue_credentials', 'send_orders']);
const membership: BusinessMembership = { membershipId, businessId, actorId, status: 'active', authorityScope: staffScope, authorityEpoch: { value: 7 }, createdAt: new Date(1), modifiedAt: new Date(1) };
const device: RegisteredBusinessDevice = { businessId, actorId, membershipId, deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('device-key-1', 'DeviceKeyId'), deviceKeyVersion: 3, publicKey: new Uint8Array([1]), publicKeyFingerprint: 'fp', status: 'active', authorityEpoch: { value: 7 }, createdAt: new Date(1) };
const issue = (changes: Partial<{ membership: BusinessMembership; device: RegisteredBusinessDevice }> = {}) => new BusinessDeviceCredentialIssuer(new IssuanceStore(), signer, 60_000).issue({ business: { businessId, status: 'active' }, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'red-team', ...changes });
describe('Trust authority red team', () => {
  it('rejects wrong business actor device and key version bindings', async () => {
    await expect(issue({ device: { ...device, businessId: identifier('business-2', 'BusinessId') } })).rejects.toThrow('identity_mismatch');
    await expect(issue({ device: { ...device, actorId: identifier('actor-2', 'ActorId') } })).rejects.toThrow('identity_mismatch');
    await expect(issue({ device: { ...device, membershipId: identifier('member-2', 'MembershipId') } })).rejects.toThrow('identity_mismatch');
    await expect(issue({ device: { ...device, deviceKeyVersion: 0 } })).rejects.toThrow('key_mismatch');
  });
  it('prevents staff from granting owner-like authority or hidden data privilege', async () => {
    const service = new ApproveMembership(new ApprovalStore());
    const principal = { actorId, verificationId: 'verified-staff', verifiedAt: new Date(1) };
    await expect(service.execute({ approverPrincipal: principal, approverMembership: membership, targetPrincipal: { ...principal, actorId: identifier('target', 'ActorId') }, requestedScope: new AuthorityScope(['manage_memberships']), intentId: 'elevate' })).rejects.toThrow();
    expect([...staffScope.capabilities]).not.toContain('read_private_catalogue');
  });
});
