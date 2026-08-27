export type VerificationKeyStatus = 'active' | 'retired' | 'revoked';
export interface TrustVerificationKey {
  readonly issuerId: string; readonly issuerKeyId: string; readonly profile: string;
  readonly publicKey: string; readonly validFrom: Date; readonly validUntil?: Date; readonly status: VerificationKeyStatus;
}
export interface VerificationKeyStore { listForIssuer(issuerId: string): Promise<readonly TrustVerificationKey[]> }
export class VerificationKeyDirectory {
  constructor(private readonly store: VerificationKeyStore) {}
  async list(issuerId: string, now: Date): Promise<readonly TrustVerificationKey[]> {
    if (!issuerId.trim() || issuerId.length > 128) throw new Error('Invalid issuer identifier');
    return (await this.store.listForIssuer(issuerId)).filter((key) => key.validFrom <= now && (!key.validUntil || key.validUntil > now));
  }
}
