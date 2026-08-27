import { createHash, generateKeyPairSync, sign, verify } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { CreateBusiness, type BusinessBootstrapStore, type CreatedBusinessAuthority } from '../services/trust/src/application/create-business.js';
import { BusinessDeviceCredentialIssuer, credentialSigningPayload, type CredentialIssuanceStore, type IssuedBusinessDeviceCredential, type TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { RegisterBusinessDevice, type DeviceRegistrationStore } from '../services/trust/src/application/register-device.js';
import { VerificationKeyDirectory } from '../services/trust/src/application/verification-keys.js';
import { AuthorityScope, identifier, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';

class FlowStore implements BusinessBootstrapStore, DeviceRegistrationStore, CredentialIssuanceStore {
  business: CreatedBusinessAuthority | null = null;
  device: RegisteredBusinessDevice | null = null;
  credential: IssuedBusinessDeviceCredential | null = null;
  findByCreationIntent() { return Promise.resolve(this.business); }
  createAtomically(_intentId: string, result: CreatedBusinessAuthority) { this.business = result; return Promise.resolve(result); }
  find() { return Promise.resolve(this.device); }
  save(device: RegisteredBusinessDevice) { this.device = device; return Promise.resolve(device); }
  findByIntent() { return Promise.resolve(this.credential); }
  record(_intentId: string, credential: IssuedBusinessDeviceCredential) { this.credential = credential; return Promise.resolve(credential); }
}

describe('end-to-end business device trust flow', () => {
  it('bootstraps authority, registers a device, issues a verifiable credential, and rejects stale authority', async () => {
    const now = new Date(1_000);
    const store = new FlowStore();
    const principal = { actorId: identifier('actor-1', 'ActorId'), verificationId: 'verified-user-1', verifiedAt: new Date(1) };
    const ids = ['business-1', 'membership-1', 'audit-1'];
    const created = await new CreateBusiness(store, () => now, () => ids.shift()!).execute({ principal, intentId: 'create-1', authorityDisplayName: 'Acme account' });
    const devicePublicKey = new Uint8Array(65).fill(7);
    const device = await new RegisterBusinessDevice(store, () => now).execute({
      principal, membership: created.membership, deviceId: identifier('device-1', 'DeviceId'),
      deviceKeyId: identifier('device-key-1', 'DeviceKeyId'), deviceKeyVersion: 1, publicKey: devicePublicKey,
      publicKeyFingerprint: createHash('sha256').update(devicePublicKey).digest('base64'),
    });

    const keyPair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    const signer: TrustCredentialSigner = { sign: (build) => Promise.resolve({
      issuerId: 'issuer-1', issuerKeyId: 'issuer-key-1', profile: 'P256-SHA256-v1',
      signature: sign('sha256', build({ issuerId: 'issuer-1', issuerKeyId: 'issuer-key-1', profile: 'P256-SHA256-v1' }), keyPair.privateKey),
    }) };
    const credential = await new BusinessDeviceCredentialIssuer(store, signer, 60_000, () => now, () => 'credential-1').issue({
      business: { businessId: created.businessId, status: 'active' }, membership: created.membership, device,
      requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-1',
    });
    const keys = await new VerificationKeyDirectory({ listForIssuer: () => Promise.resolve([{
      issuerId: 'issuer-1', issuerKeyId: 'issuer-key-1', profile: 'P256-SHA256-v1',
      publicKey: keyPair.publicKey.export({ type: 'spki', format: 'pem' }).toString(), validFrom: new Date(1), status: 'active' as const,
    }]) }).list('issuer-1', now);

    expect(keys).toHaveLength(1);
    expect(verify('sha256', credentialSigningPayload(credential.claims), keys[0]!.publicKey, credential.signature.signature)).toBe(true);
    expect(credential.claims.businessId).toBe(created.businessId);
    expect(credential.claims.deviceId).toBe(device.deviceId);
    await expect(new BusinessDeviceCredentialIssuer(new FlowStore(), signer, 60_000, () => now).issue({
      business: { businessId: created.businessId, status: 'active' }, membership: { ...created.membership, authorityEpoch: { value: 2 } }, device,
      requestedScope: new AuthorityScope(['send_orders']), intentId: 'stale-issue',
    })).rejects.toThrow('stale_authority');
  });
});
