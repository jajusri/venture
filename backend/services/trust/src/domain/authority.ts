type Brand<Value, Name extends string> = Value & { readonly __brand: Name };
export type BusinessId = Brand<string, 'BusinessId'>;
export type ActorId = Brand<string, 'ActorId'>;
export type MembershipId = Brand<string, 'MembershipId'>;
export type DeviceId = Brand<string, 'DeviceId'>;
export type DeviceKeyId = Brand<string, 'DeviceKeyId'>;
export type CredentialId = Brand<string, 'CredentialId'>;
export type IssuerId = Brand<string, 'IssuerId'>;
export type IssuerKeyId = Brand<string, 'IssuerKeyId'>;

export function identifier<Name extends string>(value: string, name: Name): Brand<string, Name> {
  const normalized = value.trim();
  if (!normalized || normalized.length > 128) throw new Error(`${name} must contain 1-128 characters`);
  return normalized as Brand<string, Name>;
}

export interface VerifiedUserPrincipal { readonly actorId: ActorId; readonly verificationId: string; readonly verifiedAt: Date }
export interface BusinessAuthorityReference { readonly businessId: BusinessId; readonly status: 'active' | 'suspended' | 'revoked' }
export type AuthorityCapability = 'manage_memberships' | 'approve_memberships' | 'register_devices' | 'revoke_devices' | 'issue_credentials' | 'send_orders';
export class AuthorityScope {
  readonly capabilities: ReadonlySet<AuthorityCapability>;
  constructor(capabilities: readonly AuthorityCapability[]) {
    if (capabilities.length === 0 || capabilities.length > 32) throw new Error('Authority scope must be bounded and non-empty');
    this.capabilities = new Set(capabilities);
  }
  permits(capability: AuthorityCapability): boolean { return this.capabilities.has(capability); }
  isSubsetOf(other: AuthorityScope): boolean { return [...this.capabilities].every((capability) => other.permits(capability)); }
}
export interface AuthorityEpoch { readonly value: number; readonly expiresAt?: Date }
export type MembershipStatus = 'active' | 'suspended' | 'revoked';
export interface BusinessMembership {
  readonly membershipId: MembershipId; readonly businessId: BusinessId; readonly actorId: ActorId;
  readonly status: MembershipStatus; readonly authorityScope: AuthorityScope; readonly authorityEpoch: AuthorityEpoch;
  readonly createdAt: Date; readonly modifiedAt: Date;
}
export type DeviceRegistrationStatus = 'active' | 'revoked';
export interface RegisteredBusinessDevice {
  readonly businessId: BusinessId; readonly actorId: ActorId; readonly membershipId: MembershipId;
  readonly deviceId: DeviceId; readonly deviceKeyId: DeviceKeyId; readonly deviceKeyVersion: number;
  readonly publicKey: Uint8Array; readonly publicKeyFingerprint: string; readonly status: DeviceRegistrationStatus;
  readonly authorityEpoch: AuthorityEpoch; readonly createdAt: Date; readonly revokedAt?: Date;
}
export interface BusinessDeviceCredentialClaims {
  readonly credentialVersion: number; readonly credentialId: CredentialId; readonly businessId: BusinessId;
  readonly actorId: ActorId; readonly membershipId: MembershipId; readonly deviceId: DeviceId;
  readonly deviceKeyId: DeviceKeyId; readonly deviceKeyVersion: number; readonly devicePublicKeyFingerprint: string;
  readonly authorityScope: AuthorityScope; readonly authorityEpoch: number; readonly issuedAt: Date;
  readonly notBefore: Date; readonly expiresAt: Date; readonly issuerId: IssuerId; readonly issuerKeyId: IssuerKeyId;
}
export type CredentialStatus = 'valid' | 'expired' | 'revoked' | 'stale_authority';
export type AuthorityValidationFailure = 'business_inactive' | 'membership_inactive' | 'membership_expired' | 'device_revoked' | 'identity_mismatch' | 'key_mismatch' | 'scope_not_permitted' | 'stale_authority';

export function validateDeviceAuthority(input: { business: BusinessAuthorityReference; membership: BusinessMembership; device: RegisteredBusinessDevice; requestedScope: AuthorityScope; now: Date }): AuthorityValidationFailure | null {
  const { business, membership, device, requestedScope, now } = input;
  if (business.status !== 'active') return 'business_inactive';
  if (membership.status !== 'active') return 'membership_inactive';
  if (membership.authorityEpoch.expiresAt && membership.authorityEpoch.expiresAt <= now) return 'membership_expired';
  if (device.status !== 'active') return 'device_revoked';
  if (business.businessId !== membership.businessId || membership.businessId !== device.businessId || membership.actorId !== device.actorId || membership.membershipId !== device.membershipId) return 'identity_mismatch';
  if (device.deviceKeyVersion < 1 || !device.deviceKeyId || device.publicKey.length === 0 || !device.publicKeyFingerprint) return 'key_mismatch';
  if (!requestedScope.isSubsetOf(membership.authorityScope)) return 'scope_not_permitted';
  if (membership.authorityEpoch.value !== device.authorityEpoch.value) return 'stale_authority';
  return null;
}
