import type { TrustCredentialSigner } from './issue-credential.js';
import type { TrustVerificationKey } from './verification-keys.js';

export interface ManagedSignerHandle { readonly signer: TrustCredentialSigner; readonly status: 'active' | 'retired' | 'revoked' }
export class RotatingTrustCredentialSigner implements TrustCredentialSigner {
  constructor(private readonly handles: () => readonly ManagedSignerHandle[]) {}
  private active(): TrustCredentialSigner {
    const active = this.handles().filter((handle) => handle.status === 'active');
    if (active.length !== 1) throw new Error('Exactly one active Trust signing key is required');
    return active[0]!.signer;
  }
  sign(buildPayload: Parameters<TrustCredentialSigner['sign']>[0]) { return this.active().sign(buildPayload); }
}
export function validateVerificationKeyRotation(keys: readonly TrustVerificationKey[]): void {
  const identities = new Set<string>();
  for (const key of keys) {
    const identity = `${key.issuerId}:${key.issuerKeyId}`;
    if (identities.has(identity)) throw new Error('Duplicate issuer key identity');
    identities.add(identity);
    if (key.validUntil && key.validUntil <= key.validFrom) throw new Error('Invalid issuer key validity window');
  }
}
