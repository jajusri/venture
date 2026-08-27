import { generateKeyPairSync, sign, verify } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { BusinessDeviceCredentialIssuer, credentialSigningPayload, type CredentialIssuanceStore, type IssuedBusinessDeviceCredential, type TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { AuthorityScope, identifier, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
class Store implements CredentialIssuanceStore { value: IssuedBusinessDeviceCredential | null = null; findByIntent() { return Promise.resolve(this.value); } record(_intent: string, value: IssuedBusinessDeviceCredential) { this.value = value; return Promise.resolve(value); } }
const keys = generateKeyPairSync('ed25519');
const signer: TrustCredentialSigner = { sign: (build) => { const identity = { issuerId: 'issuer-1', issuerKeyId: 'issuer-key-1', profile: 'Ed25519-v1' }; return Promise.resolve({ ...identity, signature: sign(null, build(identity), keys.privateKey) }); } };
const actorId = identifier('actor-1', 'ActorId'); const businessId = identifier('business-1', 'BusinessId'); const membershipId = identifier('membership-1', 'MembershipId');
const scope = new AuthorityScope(['issue_credentials', 'send_orders']);
const membership: BusinessMembership = { membershipId, businessId, actorId, status: 'active', authorityScope: scope, authorityEpoch: { value: 2 }, createdAt: new Date(1), modifiedAt: new Date(1) };
const device: RegisteredBusinessDevice = { businessId, actorId, membershipId, deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('key-1', 'DeviceKeyId'), deviceKeyVersion: 1, publicKey: new Uint8Array([1]), publicKeyFingerprint: 'fp', status: 'active', authorityEpoch: { value: 2 }, createdAt: new Date(1) };
describe('BusinessDeviceCredentialIssuer', () => {
  it('issues a bounded signed short-lived credential idempotently', async () => {
    const service = new BusinessDeviceCredentialIssuer(new Store(), signer, 300_000, () => new Date(10));
    const input = { business: { businessId, status: 'active' as const }, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-1' };
    const issued = await service.issue(input); const retry = await service.issue(input);
    expect(retry.claims.credentialId).toBe(issued.claims.credentialId);
    expect(verify(null, credentialSigningPayload(issued.claims), keys.publicKey, issued.signature.signature)).toBe(true);
    expect(issued.claims.expiresAt.getTime() - issued.claims.issuedAt.getTime()).toBe(300_000);
  });
  it('rejects revoked device, stale epoch, and scope escalation', async () => {
    const make = () => new BusinessDeviceCredentialIssuer(new Store(), signer, 300_000);
    await expect(make().issue({ business: { businessId, status: 'active' }, membership, device: { ...device, status: 'revoked' }, requestedScope: new AuthorityScope(['send_orders']), intentId: 'a' })).rejects.toThrow('device_revoked');
    await expect(make().issue({ business: { businessId, status: 'active' }, membership, device: { ...device, authorityEpoch: { value: 1 } }, requestedScope: new AuthorityScope(['send_orders']), intentId: 'b' })).rejects.toThrow('stale_authority');
    await expect(make().issue({ business: { businessId, status: 'active' }, membership, device, requestedScope: new AuthorityScope(['manage_memberships']), intentId: 'c' })).rejects.toThrow('scope_not_permitted');
  });
});
