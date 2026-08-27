import { randomUUID } from 'node:crypto';
import { identifier, validateDeviceAuthority, type AuthorityScope, type BusinessAuthorityReference, type BusinessDeviceCredentialClaims, type BusinessMembership, type RegisteredBusinessDevice } from '../domain/authority.js';

export interface TrustCredentialSignature { readonly issuerId: string; readonly issuerKeyId: string; readonly profile: string; readonly signature: Uint8Array }
export interface TrustCredentialSigner { readonly issuerId: string; readonly issuerKeyId: string; readonly profile: string; sign(payload: Uint8Array): Promise<Uint8Array> }
export interface IssuedBusinessDeviceCredential { readonly claims: BusinessDeviceCredentialClaims; readonly signature: TrustCredentialSignature }
export interface CredentialIssuanceStore {
  findByIntent(businessId: string, deviceId: string, intentId: string): Promise<IssuedBusinessDeviceCredential | null>;
  record(intentId: string, credential: IssuedBusinessDeviceCredential): Promise<IssuedBusinessDeviceCredential>;
}
export interface IssueCredentialInput { readonly business: BusinessAuthorityReference; readonly membership: BusinessMembership; readonly device: RegisteredBusinessDevice; readonly requestedScope: AuthorityScope; readonly intentId: string }

export function credentialSigningPayload(claims: BusinessDeviceCredentialClaims): Uint8Array {
  const fields = [claims.credentialVersion, claims.credentialId, claims.businessId, claims.actorId, claims.membershipId,
    claims.deviceId, claims.deviceKeyId, claims.deviceKeyVersion, claims.devicePublicKeyFingerprint,
    [...claims.authorityScope.capabilities].sort().join(','), claims.authorityEpoch, claims.issuedAt.getTime(),
    claims.notBefore.getTime(), claims.expiresAt.getTime(), claims.issuerId, claims.issuerKeyId];
  return Buffer.from(fields.map((value) => String(value).replaceAll('\\', '\\\\').replaceAll('|', '\\|')).join('|'), 'utf8');
}

export class BusinessDeviceCredentialIssuer {
  constructor(private readonly store: CredentialIssuanceStore, private readonly signer: TrustCredentialSigner,
    private readonly lifetimeMs: number, private readonly now: () => Date = () => new Date(), private readonly newId: () => string = randomUUID) {
    if (!Number.isInteger(lifetimeMs) || lifetimeMs < 60_000 || lifetimeMs > 86_400_000) throw new Error('Credential lifetime must be configured between one minute and one day');
  }
  async issue(input: IssueCredentialInput): Promise<IssuedBusinessDeviceCredential> {
    if (!input.intentId.trim()) throw new Error('Issuance intent is required');
    const existing = await this.store.findByIntent(input.business.businessId, input.device.deviceId, input.intentId); if (existing) return existing;
    const timestamp = this.now();
    const invalid = validateDeviceAuthority({ ...input, now: timestamp }); if (invalid) throw new Error(`Credential authority rejected: ${invalid}`);
    if (!input.membership.authorityScope.permits('issue_credentials')) throw new Error('Membership cannot issue credentials');
    const unsigned = { credentialVersion: 1, credentialId: identifier(this.newId(), 'CredentialId'), businessId: input.business.businessId,
      actorId: input.membership.actorId, membershipId: input.membership.membershipId, deviceId: input.device.deviceId,
      deviceKeyId: input.device.deviceKeyId, deviceKeyVersion: input.device.deviceKeyVersion, devicePublicKeyFingerprint: input.device.publicKeyFingerprint,
      authorityScope: input.requestedScope, authorityEpoch: input.membership.authorityEpoch.value, issuedAt: timestamp, notBefore: timestamp,
      expiresAt: new Date(timestamp.getTime() + this.lifetimeMs) };
    const claims: BusinessDeviceCredentialClaims = { ...unsigned, issuerId: identifier(this.signer.issuerId, 'IssuerId'), issuerKeyId: identifier(this.signer.issuerKeyId, 'IssuerKeyId') };
    const signature = await this.signer.sign(credentialSigningPayload(claims));
    return this.store.record(input.intentId, { claims, signature: { issuerId: this.signer.issuerId, issuerKeyId: this.signer.issuerKeyId, profile: this.signer.profile, signature } });
  }
}
