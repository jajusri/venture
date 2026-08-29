import { describe, expect, it } from 'vitest';
import { AuthorityScope, identifier } from '../services/trust/src/domain/authority.js';
import { ReadCurrentAuthorityEpoch } from '../services/trust/src/application/read-authority-epoch.js';
import type { TrustAuthoritySnapshot, TrustAuthoritySnapshotReader } from '../services/relay/src/application/pilot-authority-verifier.js';

const BUSINESS_ID = 'business-1'; const MEMBERSHIP_ID = 'membership-1'; const DEVICE_ID = 'device-1';

function snapshotOf(overrides: Partial<TrustAuthoritySnapshot> = {}): TrustAuthoritySnapshot {
  return {
    businessStatus: 'active',
    membership: {
      membershipId: identifier(MEMBERSHIP_ID, 'MembershipId'), businessId: identifier(BUSINESS_ID, 'BusinessId'), actorId: identifier('actor-1', 'ActorId'),
      status: 'active', authorityScope: new AuthorityScope(['send_orders']), authorityEpoch: { value: 3 }, createdAt: new Date(0), modifiedAt: new Date(0),
    },
    device: {
      businessId: identifier(BUSINESS_ID, 'BusinessId'), actorId: identifier('actor-1', 'ActorId'), membershipId: identifier(MEMBERSHIP_ID, 'MembershipId'),
      deviceId: identifier(DEVICE_ID, 'DeviceId'), deviceKeyId: identifier('device-1-key-1', 'DeviceKeyId'), deviceKeyVersion: 1,
      publicKey: new Uint8Array(65).fill(1), publicKeyFingerprint: 'fingerprint-1', status: 'active', authorityEpoch: { value: 3 }, createdAt: new Date(0),
    },
    ...overrides,
  };
}

function readerOf(snapshot: TrustAuthoritySnapshot): TrustAuthoritySnapshotReader {
  return { read: () => Promise.resolve(snapshot) };
}

describe('ReadCurrentAuthorityEpoch', () => {
  it('returns the live membership authority epoch when business/membership/device are all active and consistent', async () => {
    const service = new ReadCurrentAuthorityEpoch(readerOf(snapshotOf()));
    await expect(service.execute({ businessId: BUSINESS_ID, membershipId: MEMBERSHIP_ID, deviceId: DEVICE_ID })).resolves.toBe(3);
  });

  it('returns null when the business is not active', async () => {
    const service = new ReadCurrentAuthorityEpoch(readerOf(snapshotOf({ businessStatus: 'suspended' })));
    await expect(service.execute({ businessId: BUSINESS_ID, membershipId: MEMBERSHIP_ID, deviceId: DEVICE_ID })).resolves.toBeNull();
  });

  it('returns null when no membership/device is found at all (never-registered device)', async () => {
    const service = new ReadCurrentAuthorityEpoch(readerOf(snapshotOf({ membership: null, device: null })));
    await expect(service.execute({ businessId: BUSINESS_ID, membershipId: MEMBERSHIP_ID, deviceId: DEVICE_ID })).resolves.toBeNull();
  });

  it('returns null when the found membership does not match the requested membershipId (defense in depth)', async () => {
    const snapshot = snapshotOf();
    const service = new ReadCurrentAuthorityEpoch(readerOf(snapshot));
    await expect(service.execute({ businessId: BUSINESS_ID, membershipId: 'some-other-membership', deviceId: DEVICE_ID })).resolves.toBeNull();
  });

  it('returns null when the membership itself is suspended/revoked, even though the device row is active', async () => {
    const snapshot = snapshotOf();
    const service = new ReadCurrentAuthorityEpoch(readerOf({ ...snapshot, membership: { ...snapshot.membership!, status: 'revoked' } }));
    await expect(service.execute({ businessId: BUSINESS_ID, membershipId: MEMBERSHIP_ID, deviceId: DEVICE_ID })).resolves.toBeNull();
  });

  it('returns null when the device itself has been revoked, even though the membership is still active -- a revoked device must never be treated as fresh again', async () => {
    const snapshot = snapshotOf();
    const service = new ReadCurrentAuthorityEpoch(readerOf({ ...snapshot, device: { ...snapshot.device!, status: 'revoked' } }));
    await expect(service.execute({ businessId: BUSINESS_ID, membershipId: MEMBERSHIP_ID, deviceId: DEVICE_ID })).resolves.toBeNull();
  });
});
