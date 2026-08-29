import { createHash } from 'node:crypto';
import { afterEach, describe, expect, it } from 'vitest';
import { buildTrustService } from '../services/trust/src/app.js';
import { AuthorityScope, identifier, type ActorId, type BusinessAuthorityReference, type BusinessMembership } from '../services/trust/src/domain/authority.js';
import { hashEnrollmentGrantSecret, type EnrollmentGrant, type EnrollmentGrantId } from '../services/trust/src/domain/enrollment.js';
import { DeviceEnrollmentRejected, type EnrollmentAuthorityLookup, type EnrollmentGrantConsumptionStore } from '../services/trust/src/application/consume-enrollment-grant.js';
import type { DeviceRegistrationStore } from '../services/trust/src/application/register-device.js';
import type { RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
import { BusinessDeviceCredentialIssuer, type TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { InMemoryCredentialIssuanceStore } from '../services/trust/src/persistence/in-memory-credential-store.js';

const apps: ReturnType<typeof buildTrustService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

const NOW = new Date(10_000);
const SECRET = 'a-genuinely-long-enough-secret-value';

class FakeDeviceStore implements DeviceRegistrationStore {
  private readonly devices = new Map<string, RegisteredBusinessDevice>();
  find(businessId: string, deviceId: string, keyVersion: number): Promise<RegisteredBusinessDevice | null> { return Promise.resolve(this.devices.get(`${businessId}::${deviceId}::${keyVersion}`) ?? null); }
  save(device: RegisteredBusinessDevice): Promise<RegisteredBusinessDevice> { this.devices.set(`${device.businessId}::${device.deviceId}::${device.deviceKeyVersion}`, device); return Promise.resolve(device); }
}

class FakeGrantStore implements EnrollmentGrantConsumptionStore {
  constructor(private readonly grants: Map<string, EnrollmentGrant>, private readonly deviceStore: DeviceRegistrationStore, private readonly authority: EnrollmentAuthorityLookup) {}
  async consumeAndRegister<T>(grantId: string, consumingDeviceId: string, work: (claimed: EnrollmentGrant, deviceStore: DeviceRegistrationStore, authority: EnrollmentAuthorityLookup) => Promise<T>): Promise<T> {
    const grant = this.grants.get(grantId);
    if (!grant) throw new DeviceEnrollmentRejected('grant_not_found');
    const result = await work(grant, this.deviceStore, this.authority);
    this.grants.set(grantId, { ...grant, consumedAt: NOW, consumedByDeviceId: identifier(consumingDeviceId, 'DeviceId') });
    return result;
  }
}

const activeMembership: BusinessMembership = {
  membershipId: identifier('membership-1', 'MembershipId'), businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'),
  status: 'active', authorityScope: new AuthorityScope(['send_orders', 'issue_credentials', 'register_devices']), authorityEpoch: { value: 1 }, createdAt: new Date(0), modifiedAt: new Date(0),
};
const activeBusiness: BusinessAuthorityReference = { businessId: identifier('business-1', 'BusinessId'), status: 'active' };

function makeGrant(overrides: Partial<EnrollmentGrant> = {}): EnrollmentGrant {
  return {
    grantId: identifier('grant-1', 'EnrollmentGrantId') as EnrollmentGrantId,
    businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId') as ActorId, membershipId: identifier('membership-1', 'MembershipId'),
    grantedDeviceScope: new AuthorityScope(['send_orders']), grantSecretHash: hashEnrollmentGrantSecret(SECRET),
    issuedAt: new Date(0), expiresAt: new Date(NOW.getTime() + 900_000),
    ...overrides,
  };
}

function buildApp(seedGrants: EnrollmentGrant[] = [makeGrant()]): ReturnType<typeof buildTrustService> {
  const grants = new Map(seedGrants.map((g) => [g.grantId, g] as const));
  const authority: EnrollmentAuthorityLookup = {
    findBusiness: () => Promise.resolve(activeBusiness),
    findMembership: () => Promise.resolve(activeMembership),
  };
  const store = new FakeGrantStore(grants, new FakeDeviceStore(), authority);
  const signer: TrustCredentialSigner = { sign: (build) => { const identity = { issuerId: 'issuer-1', issuerKeyId: 'key-1', profile: 'P256-SHA256-v1' }; build(identity); return Promise.resolve({ ...identity, signature: new Uint8Array([1, 2, 3]) }); } };
  const credentialIssuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => NOW);
  const app = buildTrustService({ enrollment: { store, credentialIssuer }, now: () => NOW });
  apps.push(app);
  return app;
}

const publicKey = new Uint8Array(65).fill(9);
const publicKeyB64 = Buffer.from(publicKey).toString('base64');
const fingerprint = createHash('sha256').update(publicKey).digest('base64');
function validBody(overrides: Record<string, unknown> = {}) {
  return { grantId: 'grant-1', grantSecret: SECRET, deviceId: 'device-1', deviceKeyId: 'device-1-key-1', deviceKeyVersion: 1, publicKey: publicKeyB64, publicKeyFingerprint: fingerprint, ...overrides };
}

describe('POST /v1/trust/enrollment/consume', () => {
  it('returns a credential and device summary for a valid grant, with no private key material anywhere in the body', async () => {
    const app = buildApp();
    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });
    expect(response.statusCode).toBe(200);
    const body = response.json<{ device: { deviceId: string }; credential: { claims: { membershipId: string }; signature: string } }>();
    expect(body.device.deviceId).toBe('device-1');
    expect(body.credential.claims.membershipId).toBe('membership-1');
    expect(response.body).not.toContain(SECRET);
    expect(response.body.toLowerCase()).not.toContain('private');
  });

  it('rejects a second consumption of the same grant with 409 grant_already_consumed', async () => {
    const app = buildApp();
    await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });
    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody({ deviceId: 'device-2' }) });
    expect(response.statusCode).toBe(409);
    expect(response.json<{ error: { code: string } }>().error.code).toBe('grant_already_consumed');
  });

  it('rejects an unknown grant with 404', async () => {
    const app = buildApp();
    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody({ grantId: 'no-such-grant' }) });
    expect(response.statusCode).toBe(404);
    expect(response.json<{ error: { code: string } }>().error.code).toBe('grant_not_found');
  });

  it('rejects an expired grant with 410', async () => {
    const app = buildApp([makeGrant({ expiresAt: new Date(NOW.getTime() - 1) })]);
    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });
    expect(response.statusCode).toBe(410);
    expect(response.json<{ error: { code: string } }>().error.code).toBe('grant_expired');
  });

  it('rejects the wrong secret with 403', async () => {
    const app = buildApp();
    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody({ grantSecret: 'a-completely-wrong-secret-value' }) });
    expect(response.statusCode).toBe(403);
    expect(response.json<{ error: { code: string } }>().error.code).toBe('grant_secret_mismatch');
  });

  it('rejects a missing field with 400 before ever touching the grant store', async () => {
    const app = buildApp();
    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: { ...validBody(), grantSecret: undefined } });
    expect(response.statusCode).toBe(400);
    expect(response.json<{ error: { code: string } }>().error.code).toBe('invalid_enrollment_request');
  });

  it('rejects a non-base64 public key with 400', async () => {
    const app = buildApp();
    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody({ publicKey: 'not-valid-base64!!' }) });
    expect(response.statusCode).toBe(400);
  });

  it('rejects deviceKeyVersion 0 or negative with 400', async () => {
    const app = buildApp();
    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody({ deviceKeyVersion: 0 }) });
    expect(response.statusCode).toBe(400);
  });
});
