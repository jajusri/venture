import { describe, expect, it } from 'vitest';
import { RotatingTrustCredentialSigner, validateVerificationKeyRotation, type ManagedSignerHandle } from '../services/trust/src/application/signer-rotation.js';
import type { TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
const signer = (key: string): TrustCredentialSigner => ({ sign: (build) => { const identity = { issuerId: 'issuer-1', issuerKeyId: key, profile: 'P256-SHA256-v1' }; build(identity); return Promise.resolve({ ...identity, signature: new Uint8Array([key === 'new' ? 2 : 1]) }); } });
describe('Trust signer rotation', () => {
  it('new credentials use the sole active key while old public material remains addressable', async () => {
    const handles: ManagedSignerHandle[] = [{ signer: signer('old'), status: 'retired' }, { signer: signer('new'), status: 'active' }];
    const rotating = new RotatingTrustCredentialSigner(() => handles);
    const signature = await rotating.sign((identity) => new TextEncoder().encode(identity.issuerKeyId));
    expect(signature.issuerKeyId).toBe('new'); expect(signature.signature).toEqual(new Uint8Array([2]));
    validateVerificationKeyRotation([
      { issuerId: 'issuer-1', issuerKeyId: 'old', profile: 'P256-SHA256-v1', publicKey: 'old-public', validFrom: new Date(1), status: 'retired' },
      { issuerId: 'issuer-1', issuerKeyId: 'new', profile: 'P256-SHA256-v1', publicKey: 'new-public', validFrom: new Date(2), status: 'active' },
    ]);
  });
  it('fails closed with zero or multiple active signers', () => {
    expect(() => new RotatingTrustCredentialSigner(() => []).sign(() => new Uint8Array())).toThrow('Exactly one');
    expect(() => new RotatingTrustCredentialSigner(() => [{ signer: signer('a'), status: 'active' }, { signer: signer('b'), status: 'active' }]).sign(() => new Uint8Array())).toThrow('Exactly one');
  });
});
