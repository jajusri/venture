import { createHash, timingSafeEqual } from 'node:crypto';
import type { ActorId, AuthorityScope, BusinessId, DeviceId, MembershipId } from './authority.js';

type Brand<Value, Name extends string> = Value & { readonly __brand: Name };
export type EnrollmentGrantId = Brand<string, 'EnrollmentGrantId'>;

/**
 * A one-time, server-authoritative bootstrap primitive: proof that a Business administrator has
 * authorized ONE specific Person/Business/Membership to enroll ONE new device, without the device
 * (or the person operating it) ever asserting its own authority. The device presents `grantId` +
 * the raw `grantSecret` (never persisted -- only `grantSecretHash` is); everything else about WHO
 * this enrollment is for (business/actor/membership) comes from the grant record itself, never from
 * caller input (see `ConsumeDeviceEnrollmentGrant` in the application layer, which never accepts a
 * caller-asserted businessId/actorId/membershipId for this reason).
 *
 * `grantedDeviceScope` is deliberately a field an operator sets explicitly at grant-issuance time,
 * not automatically inferred from the target membership's own (possibly broader, administrative)
 * authorityScope -- e.g. a membership that can `manage_memberships` should not thereby cause every
 * device it enrolls to silently receive `manage_memberships` too. It is still validated as a subset
 * of the membership's CURRENT authorityScope at consumption time (via the existing, certified
 * `validateDeviceAuthority` subset check in `issue-credential.ts` -- not reopened here).
 */
export interface EnrollmentGrant {
  readonly grantId: EnrollmentGrantId;
  readonly businessId: BusinessId;
  readonly actorId: ActorId;
  readonly membershipId: MembershipId;
  readonly grantedDeviceScope: AuthorityScope;
  readonly grantSecretHash: string;
  readonly issuedAt: Date;
  readonly expiresAt: Date;
  readonly consumedAt?: Date;
  readonly consumedByDeviceId?: DeviceId;
}

export type EnrollmentGrantFailure = 'grant_not_found' | 'grant_already_consumed' | 'grant_expired' | 'grant_secret_mismatch';

const MIN_GRANT_LIFETIME_MS = 60_000;
const MAX_GRANT_LIFETIME_MS = 24 * 60 * 60 * 1000;

export function validateEnrollmentGrantLifetime(issuedAt: Date, expiresAt: Date): void {
  const lifetimeMs = expiresAt.getTime() - issuedAt.getTime();
  if (!Number.isFinite(lifetimeMs) || lifetimeMs < MIN_GRANT_LIFETIME_MS || lifetimeMs > MAX_GRANT_LIFETIME_MS) {
    throw new Error('Enrollment grant lifetime must be bounded between one minute and 24 hours');
  }
}

export function hashEnrollmentGrantSecret(secret: string): string {
  if (!secret || secret.length < 16) throw new Error('Enrollment grant secret must be a high-entropy value of at least 16 characters');
  return createHash('sha256').update(secret, 'utf8').digest('base64');
}

/** Constant-time comparison against the STORED hash -- never compares raw secrets, and never lets
 * comparison time leak how many leading bytes of a guess were correct. */
export function enrollmentGrantSecretMatches(presentedSecret: string, expectedHash: string): boolean {
  let presentedHash: Buffer;
  try { presentedHash = Buffer.from(hashEnrollmentGrantSecret(presentedSecret), 'base64'); } catch { return false; }
  const expected = Buffer.from(expectedHash, 'base64');
  return presentedHash.length === expected.length && timingSafeEqual(presentedHash, expected);
}

/** Pure decision over an ALREADY-FETCHED grant row -- the caller (the Postgres store's atomic
 * consume transaction) is responsible for fetching that row under a lock and for ensuring this
 * check runs before anything the grant authorizes (device registration, credential issuance) takes
 * effect. Order matters for the two checks that read `grant` state (consumed/expired) vs. the one
 * that depends on caller input (secret): a not-found grant is `null` and never reaches this
 * function at all (see the store), so this only ever adjudicates a grant that DOES exist. */
export function validateEnrollmentGrantClaim(grant: EnrollmentGrant, input: { presentedSecret: string; now: Date }): EnrollmentGrantFailure | null {
  if (grant.consumedAt) return 'grant_already_consumed';
  if (grant.expiresAt <= input.now) return 'grant_expired';
  if (!enrollmentGrantSecretMatches(input.presentedSecret, grant.grantSecretHash)) return 'grant_secret_mismatch';
  return null;
}
