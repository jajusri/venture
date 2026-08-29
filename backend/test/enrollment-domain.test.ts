import { describe, expect, it } from 'vitest';
import { identifier } from '../services/trust/src/domain/authority.js';
import {
  enrollmentGrantSecretMatches, hashEnrollmentGrantSecret, validateEnrollmentGrantClaim, validateEnrollmentGrantLifetime,
  type EnrollmentGrant, type EnrollmentGrantId,
} from '../services/trust/src/domain/enrollment.js';
import { AuthorityScope } from '../services/trust/src/domain/authority.js';

const SECRET = 'a-genuinely-long-enough-secret-value';

function grant(overrides: Partial<EnrollmentGrant> = {}): EnrollmentGrant {
  return {
    grantId: identifier('grant-1', 'EnrollmentGrantId') as EnrollmentGrantId,
    businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'), membershipId: identifier('membership-1', 'MembershipId'),
    grantedDeviceScope: new AuthorityScope(['send_orders']), grantSecretHash: hashEnrollmentGrantSecret(SECRET),
    issuedAt: new Date(1000), expiresAt: new Date(1000 + 900_000),
    ...overrides,
  };
}

describe('enrollment grant secret hashing/comparison', () => {
  it('hashes deterministically and never stores the raw secret', () => {
    const hash = hashEnrollmentGrantSecret(SECRET);
    expect(hash).toBe(hashEnrollmentGrantSecret(SECRET));
    expect(hash).not.toContain(SECRET);
  });
  it('rejects a too-short secret as insufficiently high-entropy', () => {
    expect(() => hashEnrollmentGrantSecret('short')).toThrow('at least 16 characters');
  });
  it('matches the correct secret and rejects any other value, including empty/garbage input', () => {
    const hash = hashEnrollmentGrantSecret(SECRET);
    expect(enrollmentGrantSecretMatches(SECRET, hash)).toBe(true);
    expect(enrollmentGrantSecretMatches('wrong-secret-value-long-enough', hash)).toBe(false);
    expect(enrollmentGrantSecretMatches('', hash)).toBe(false);
  });
});

describe('enrollment grant lifetime bounds', () => {
  it('accepts a bounded lifetime', () => {
    expect(() => validateEnrollmentGrantLifetime(new Date(0), new Date(900_000))).not.toThrow();
  });
  it('rejects a lifetime under one minute', () => {
    expect(() => validateEnrollmentGrantLifetime(new Date(0), new Date(1000))).toThrow('bounded between one minute and 24 hours');
  });
  it('rejects a lifetime over 24 hours', () => {
    expect(() => validateEnrollmentGrantLifetime(new Date(0), new Date(25 * 60 * 60 * 1000))).toThrow('bounded between one minute and 24 hours');
  });
});

describe('validateEnrollmentGrantClaim', () => {
  it('accepts an unconsumed, unexpired grant with the correct secret', () => {
    expect(validateEnrollmentGrantClaim(grant(), { presentedSecret: SECRET, now: new Date(1500) })).toBeNull();
  });
  it('rejects an already-consumed grant, even with the correct secret', () => {
    expect(validateEnrollmentGrantClaim(grant({ consumedAt: new Date(1200) }), { presentedSecret: SECRET, now: new Date(1500) })).toBe('grant_already_consumed');
  });
  it('rejects an expired grant, even with the correct secret', () => {
    expect(validateEnrollmentGrantClaim(grant(), { presentedSecret: SECRET, now: new Date(1000 + 900_001) })).toBe('grant_expired');
  });
  it('rejects a tampered/wrong secret against an otherwise-valid grant', () => {
    expect(validateEnrollmentGrantClaim(grant(), { presentedSecret: 'a-completely-different-secret-value', now: new Date(1500) })).toBe('grant_secret_mismatch');
  });
  it('checks consumed/expired before ever comparing the secret, so a guess cannot distinguish an expired grant from a live one by timing/branch', () => {
    const expired = grant({ expiresAt: new Date(1100) });
    expect(validateEnrollmentGrantClaim(expired, { presentedSecret: 'irrelevant-wrong-secret-value', now: new Date(1500) })).toBe('grant_expired');
  });
});
