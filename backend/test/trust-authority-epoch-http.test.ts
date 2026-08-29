import { afterEach, describe, expect, it } from 'vitest';
import { buildTrustService } from '../services/trust/src/app.js';
import { AuthorityScope, identifier } from '../services/trust/src/domain/authority.js';
import type { TrustAuthoritySnapshot, TrustAuthoritySnapshotReader } from '../services/relay/src/application/pilot-authority-verifier.js';

const apps: ReturnType<typeof buildTrustService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

function activeSnapshot(epoch: number): TrustAuthoritySnapshot {
  return {
    businessStatus: 'active',
    membership: {
      membershipId: identifier('membership-1', 'MembershipId'), businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'),
      status: 'active', authorityScope: new AuthorityScope(['send_orders']), authorityEpoch: { value: epoch }, createdAt: new Date(0), modifiedAt: new Date(0),
    },
    device: {
      businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'), membershipId: identifier('membership-1', 'MembershipId'),
      deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('device-1-key-1', 'DeviceKeyId'), deviceKeyVersion: 1,
      publicKey: new Uint8Array(65).fill(1), publicKeyFingerprint: 'fingerprint-1', status: 'active', authorityEpoch: { value: epoch }, createdAt: new Date(0),
    },
  };
}

function buildApp(snapshot: TrustAuthoritySnapshot) {
  const reader: TrustAuthoritySnapshotReader = { read: () => Promise.resolve(snapshot) };
  const app = buildTrustService({ authoritySnapshots: reader });
  apps.push(app);
  return app;
}

describe('GET /v1/trust/authority/epoch', () => {
  it('returns the current authority epoch for an active business/membership/device', async () => {
    const app = buildApp(activeSnapshot(5));
    const response = await app.inject({ method: 'GET', url: '/v1/trust/authority/epoch?businessId=business-1&membershipId=membership-1&deviceId=device-1' });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({ businessId: 'business-1', membershipId: 'membership-1', deviceId: 'device-1', authorityEpoch: 5 });
  });

  it('returns 404 when no active authority is found', async () => {
    const app = buildApp({ businessStatus: null, membership: null, device: null });
    const response = await app.inject({ method: 'GET', url: '/v1/trust/authority/epoch?businessId=business-1&membershipId=membership-1&deviceId=device-1' });
    expect(response.statusCode).toBe(404);
    expect(response.json<{ error: { code: string } }>().error.code).toBe('authority_not_found');
  });

  it('returns 400 when a required query parameter is missing', async () => {
    const app = buildApp(activeSnapshot(1));
    const response = await app.inject({ method: 'GET', url: '/v1/trust/authority/epoch?businessId=business-1&deviceId=device-1' });
    expect(response.statusCode).toBe(400);
  });

  it('never caches a response (authority freshness must always be re-checked)', async () => {
    const app = buildApp(activeSnapshot(1));
    const response = await app.inject({ method: 'GET', url: '/v1/trust/authority/epoch?businessId=business-1&membershipId=membership-1&deviceId=device-1' });
    expect(response.headers['cache-control']).toBe('no-store');
  });
});
