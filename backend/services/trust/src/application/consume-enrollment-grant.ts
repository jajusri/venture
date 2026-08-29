import { createHash } from 'node:crypto';
import { validateEnrollmentGrantClaim, type EnrollmentGrant, type EnrollmentGrantFailure } from '../domain/enrollment.js';
import type { AuthorityScope, BusinessAuthorityReference, BusinessMembership, DeviceId, DeviceKeyId } from '../domain/authority.js';
import { RegisterBusinessDevice, type DeviceRegistrationStore } from './register-device.js';
import { BusinessDeviceCredentialIssuer, type IssuedBusinessDeviceCredential } from './issue-credential.js';

export type DeviceEnrollmentRejectionReason = EnrollmentGrantFailure | 'business_inactive' | 'membership_inactive';

/** Thrown for every enrollment-specific refusal so the HTTP layer can map each `reason` to its own
 * status code without resorting to message-substring matching (see `app.ts`). Plain input-validation
 * errors (malformed public key, fingerprint mismatch) and errors surfaced by the reused, certified
 * `RegisterBusinessDevice`/`BusinessDeviceCredentialIssuer` services remain ordinary `Error`s, exactly
 * as those services already throw them elsewhere -- this class is not a replacement error taxonomy. */
export class DeviceEnrollmentRejected extends Error {
  constructor(readonly reason: DeviceEnrollmentRejectionReason) { super(`Device enrollment rejected: ${reason}`); }
}

export interface EnrollmentAuthorityLookup {
  findBusiness(businessId: string): Promise<BusinessAuthorityReference | null>;
  findMembership(membershipId: string): Promise<BusinessMembership | null>;
}

/**
 * The one atomic unit Gate 2C requires: claiming the grant (so no other consumption attempt can
 * ever succeed for it again), re-fetching LIVE Business/Membership state (never trusting the grant's
 * own issuance-time snapshot -- authority may have changed since), and registering the device all
 * happen inside `work`, which the store implementation runs inside a single database transaction.
 * `work` throwing (including a `DeviceEnrollmentRejected`) must roll back everything `work` did,
 * INCLUDING the grant claim itself -- a rejected attempt must never burn a legitimate grant (see
 * `PostgresEnrollmentGrantStore.consumeAndRegister` for exactly how).
 */
export interface EnrollmentGrantConsumptionStore {
  consumeAndRegister<T>(
    grantId: string, consumingDeviceId: string,
    work: (claimed: EnrollmentGrant, deviceStore: DeviceRegistrationStore, authority: EnrollmentAuthorityLookup) => Promise<T>,
  ): Promise<T>;
}

export interface DeviceEnrollmentInput {
  readonly grantId: string; readonly grantSecret: string;
  readonly deviceId: DeviceId; readonly deviceKeyId: DeviceKeyId; readonly deviceKeyVersion: number;
  readonly publicKey: Uint8Array; readonly publicKeyFingerprint: string;
}

/**
 * KNOWN LIMITATION (documented, not a security defect): credential issuance runs AFTER the grant's
 * consuming transaction has already committed (device registered, grant burned). If the process
 * fails between that commit and this call returning, the device is left legitimately registered but
 * without a delivered credential, and the SAME grant cannot be replayed (`grant_already_consumed`).
 * Recovery needs either a separate re-issuance path keyed off the device's own established identity
 * (Gate 3B's "credential refresh/re-enrollment" -- not built here) or a fresh grant for the same
 * membership. This mirrors `InMemoryCredentialIssuanceStore`'s own pre-existing, already-accepted
 * limitation (losing issuance idempotency loses "the same credential", never authority correctness)
 * -- narrower window, same category of gap, not newly introduced by enrollment.
 */
export class ConsumeDeviceEnrollmentGrant {
  constructor(
    private readonly store: EnrollmentGrantConsumptionStore,
    private readonly credentialIssuer: BusinessDeviceCredentialIssuer,
    private readonly now: () => Date = () => new Date(),
  ) {}

  async execute(input: DeviceEnrollmentInput): Promise<IssuedBusinessDeviceCredential> {
    if (!input.grantId.trim() || !input.grantSecret) throw new Error('Enrollment grant proof is required');
    if (input.deviceKeyVersion < 1 || input.publicKey.length < 32) throw new Error('Valid device public identity is required');
    const fingerprint = createHash('sha256').update(input.publicKey).digest('base64');
    if (fingerprint !== input.publicKeyFingerprint) throw new Error('Device public-key fingerprint mismatch');

    const registered = await this.store.consumeAndRegister(input.grantId, input.deviceId, async (grant, deviceStore, authority) => {
      const timestamp = this.now();
      const failure = validateEnrollmentGrantClaim(grant, { presentedSecret: input.grantSecret, now: timestamp });
      if (failure) throw new DeviceEnrollmentRejected(failure);
      const business = await authority.findBusiness(grant.businessId);
      if (!business || business.status !== 'active') throw new DeviceEnrollmentRejected('business_inactive');
      const membership = await authority.findMembership(grant.membershipId);
      if (!membership || membership.status !== 'active') throw new DeviceEnrollmentRejected('membership_inactive');
      // Never caller-supplied: the grant is the ONLY source of which actor/business/membership this
      // enrollment is for. `RegisterBusinessDevice.execute()` itself independently re-checks
      // `principal.actorId === membership.actorId` and `membership.status === 'active'`, so this is
      // defense in depth, not the only gate.
      const device = await new RegisterBusinessDevice(deviceStore, () => timestamp).execute({
        principal: { actorId: grant.actorId, verificationId: `enrollment-grant:${grant.grantId}`, verifiedAt: grant.issuedAt },
        membership, deviceId: input.deviceId, deviceKeyId: input.deviceKeyId, deviceKeyVersion: input.deviceKeyVersion,
        publicKey: input.publicKey, publicKeyFingerprint: fingerprint,
      });
      return { business, membership, device, grantedDeviceScope: grant.grantedDeviceScope };
    });

    return this.credentialIssuer.issue({
      business: registered.business, membership: registered.membership, device: registered.device,
      requestedScope: registered.grantedDeviceScope as AuthorityScope, intentId: `enrollment:${input.grantId}`,
    });
  }
}
