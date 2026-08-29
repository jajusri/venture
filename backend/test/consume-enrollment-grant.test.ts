import { createHash } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { AuthorityScope, identifier, type ActorId, type BusinessAuthorityReference, type BusinessMembership, type DeviceId, type DeviceKeyId, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
import { hashEnrollmentGrantSecret, type EnrollmentGrant, type EnrollmentGrantId } from '../services/trust/src/domain/enrollment.js';
import {
  ConsumeDeviceEnrollmentGrant, DeviceEnrollmentRejected,
  type DeviceEnrollmentInput, type EnrollmentAuthorityLookup, type EnrollmentGrantConsumptionStore,
} from '../services/trust/src/application/consume-enrollment-grant.js';
import type { DeviceRegistrationStore } from '../services/trust/src/application/register-device.js';
import { BusinessDeviceCredentialIssuer, type CredentialIssuanceStore, type IssuedBusinessDeviceCredential, type TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { InMemoryCredentialIssuanceStore } from '../services/trust/src/persistence/in-memory-credential-store.js';

/** Counts real (non-idempotent-replay) credential issuances -- `.record()` only ever runs once per
 * intentId that actually reached signing, so this is a precise "was a credential produced" signal
 * for the rejection-path tests below, without monkey-patching `BusinessDeviceCredentialIssuer` itself. */
class CountingCredentialStore implements CredentialIssuanceStore {
  count = 0;
  constructor(private readonly inner: CredentialIssuanceStore) {}
  findByIntent(businessId: string, deviceId: string, intentId: string): Promise<IssuedBusinessDeviceCredential | null> { return this.inner.findByIntent(businessId, deviceId, intentId); }
  record(intentId: string, credential: IssuedBusinessDeviceCredential): Promise<IssuedBusinessDeviceCredential> { this.count += 1; return this.inner.record(intentId, credential); }
}

const NOW = new Date(10_000);
const SECRET = 'a-genuinely-long-enough-secret-value';

/** Keyed in-memory `DeviceRegistrationStore` -- unlike `register-device.test.ts`'s single-value
 * `Store` fake, this needs to model multiple businesses/devices to exercise cross-grant isolation
 * and conflicting-registration cases. */
class FakeDeviceStore implements DeviceRegistrationStore {
  private readonly devices = new Map<string, RegisteredBusinessDevice>();
  find(businessId: string, deviceId: string, keyVersion: number): Promise<RegisteredBusinessDevice | null> { return Promise.resolve(this.devices.get(this.key(businessId, deviceId, keyVersion)) ?? null); }
  save(device: RegisteredBusinessDevice): Promise<RegisteredBusinessDevice> { this.devices.set(this.key(device.businessId, device.deviceId, device.deviceKeyVersion), device); return Promise.resolve(device); }
  private key(businessId: string, deviceId: string, keyVersion: number): string { return `${businessId}::${deviceId}::${keyVersion}`; }
}

/** Mirrors the REAL Postgres store's transactional contract in plain JS: `work` throwing leaves the
 * grant untouched (no rollback machinery needed -- the `.set()` marking consumption simply never
 * runs), and success marks it consumed only AFTER `work` resolves. See
 * `postgres-enrollment-grant-store.test.ts` for the same contract proven against the real class. */
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

function makeSigner(): TrustCredentialSigner {
  return { sign: (build) => { const identity = { issuerId: 'issuer', issuerKeyId: 'key', profile: 'P256-SHA256-v1' }; build(identity); return Promise.resolve({ ...identity, signature: new Uint8Array([1]) }); } };
}

function makeGrant(overrides: Partial<EnrollmentGrant> = {}): EnrollmentGrant {
  return {
    grantId: identifier('grant-1', 'EnrollmentGrantId') as EnrollmentGrantId,
    businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId') as ActorId, membershipId: identifier('membership-1', 'MembershipId'),
    grantedDeviceScope: new AuthorityScope(['send_orders']), grantSecretHash: hashEnrollmentGrantSecret(SECRET),
    issuedAt: new Date(0), expiresAt: new Date(NOW.getTime() + 900_000),
    ...overrides,
  };
}

const publicKey = new Uint8Array(65).fill(9);
const fingerprint = createHash('sha256').update(publicKey).digest('base64');
function makeInput(overrides: Partial<DeviceEnrollmentInput> = {}): DeviceEnrollmentInput {
  return {
    grantId: 'grant-1', grantSecret: SECRET, deviceId: identifier('device-1', 'DeviceId') as DeviceId,
    deviceKeyId: identifier('device-1-key-1', 'DeviceKeyId') as DeviceKeyId, deviceKeyVersion: 1,
    publicKey, publicKeyFingerprint: fingerprint,
    ...overrides,
  };
}

interface Harness { service: ConsumeDeviceEnrollmentGrant; grants: Map<string, EnrollmentGrant>; deviceStore: FakeDeviceStore; issueCount: number }
function harness(seedGrants: EnrollmentGrant[], memberships: BusinessMembership[], businesses: BusinessAuthorityReference[]): Harness {
  const grants = new Map<string, EnrollmentGrant>(seedGrants.map((g) => [g.grantId, g]));
  const membershipMap = new Map<string, BusinessMembership>(memberships.map((m) => [m.membershipId, m]));
  const businessMap = new Map<string, BusinessAuthorityReference>(businesses.map((b) => [b.businessId, b]));
  const deviceStore = new FakeDeviceStore();
  const authority: EnrollmentAuthorityLookup = {
    findBusiness: (id) => Promise.resolve(businessMap.get(id) ?? null),
    findMembership: (id) => Promise.resolve(membershipMap.get(id) ?? null),
  };
  const store = new FakeGrantStore(grants, deviceStore, authority);
  const credentialStore = new CountingCredentialStore(new InMemoryCredentialIssuanceStore());
  const issuer = new BusinessDeviceCredentialIssuer(credentialStore, makeSigner(), 3_600_000, () => NOW);
  const service = new ConsumeDeviceEnrollmentGrant(store, issuer, () => NOW);
  return { service, grants, deviceStore, get issueCount() { return credentialStore.count; } };
}

const activeMembership: BusinessMembership = {
  membershipId: identifier('membership-1', 'MembershipId'), businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'),
  status: 'active', authorityScope: new AuthorityScope(['send_orders', 'issue_credentials', 'register_devices']), authorityEpoch: { value: 1 }, createdAt: new Date(0), modifiedAt: new Date(0),
};
const activeBusiness: BusinessAuthorityReference = { businessId: identifier('business-1', 'BusinessId'), status: 'active' };

describe('ConsumeDeviceEnrollmentGrant -- adversarial matrix (Gate 2D)', () => {
  it('a valid grant works exactly once and issues a credential bound to the grant-derived identity, not caller input', async () => {
    const h = harness([makeGrant()], [activeMembership], [activeBusiness]);
    const credential = await h.service.execute(makeInput());
    expect(credential.claims.businessId).toBe('business-1');
    expect(credential.claims.actorId).toBe('actor-1');
    expect(credential.claims.membershipId).toBe('membership-1');
    expect([...credential.claims.authorityScope.capabilities]).toEqual(['send_orders']);
    expect(h.issueCount).toBe(1);
  });

  it('second use of the same grant is rejected (grant_already_consumed)', async () => {
    const h = harness([makeGrant()], [activeMembership], [activeBusiness]);
    await h.service.execute(makeInput());
    await expect(h.service.execute(makeInput({ deviceId: identifier('device-2', 'DeviceId') as DeviceId }))).rejects.toMatchObject({ reason: 'grant_already_consumed' });
  });

  it('an expired grant is rejected even with the correct secret', async () => {
    const h = harness([makeGrant({ expiresAt: new Date(NOW.getTime() - 1) })], [activeMembership], [activeBusiness]);
    await expect(h.service.execute(makeInput())).rejects.toMatchObject({ reason: 'grant_expired' });
    expect(h.issueCount).toBe(0);
  });

  it('a tampered/wrong secret against a real, unconsumed grant is rejected, and the grant remains usable afterward', async () => {
    const h = harness([makeGrant()], [activeMembership], [activeBusiness]);
    await expect(h.service.execute(makeInput({ grantSecret: 'a-completely-wrong-secret-value' }))).rejects.toMatchObject({ reason: 'grant_secret_mismatch' });
    expect(h.grants.get('grant-1')?.consumedAt).toBeUndefined();
    await expect(h.service.execute(makeInput())).resolves.toBeDefined();
  });

  it("the correct secret for a DIFFERENT grant does not unlock this grant (no cross-grant/cross-business/cross-membership confusion)", async () => {
    const otherSecret = 'a-different-genuinely-long-secret';
    const h = harness(
      [makeGrant(), makeGrant({ grantId: identifier('grant-2', 'EnrollmentGrantId') as EnrollmentGrantId, businessId: identifier('business-2', 'BusinessId'), actorId: identifier('actor-2', 'ActorId') as ActorId, membershipId: identifier('membership-2', 'MembershipId'), grantSecretHash: hashEnrollmentGrantSecret(otherSecret) })],
      [activeMembership], [activeBusiness],
    );
    await expect(h.service.execute(makeInput({ grantId: 'grant-1', grantSecret: otherSecret }))).rejects.toMatchObject({ reason: 'grant_secret_mismatch' });
  });

  it('an unknown grantId is rejected (grant_not_found)', async () => {
    const h = harness([], [], []);
    await expect(h.service.execute(makeInput({ grantId: 'no-such-grant' }))).rejects.toMatchObject({ reason: 'grant_not_found' });
  });

  it('a suspended/revoked Business at consumption time is rejected even though the grant itself is valid', async () => {
    const h = harness([makeGrant()], [activeMembership], [{ businessId: identifier('business-1', 'BusinessId'), status: 'suspended' }]);
    await expect(h.service.execute(makeInput())).rejects.toMatchObject({ reason: 'business_inactive' });
    expect(h.issueCount).toBe(0);
  });

  it('a suspended/revoked Membership at consumption time is rejected even though the grant itself is valid', async () => {
    const h = harness([makeGrant()], [{ ...activeMembership, status: 'revoked' }], [activeBusiness]);
    await expect(h.service.execute(makeInput())).rejects.toMatchObject({ reason: 'membership_inactive' });
    expect(h.issueCount).toBe(0);
  });

  it('a malformed public key (too short) is rejected before any grant is touched', async () => {
    const h = harness([makeGrant()], [activeMembership], [activeBusiness]);
    await expect(h.service.execute(makeInput({ publicKey: new Uint8Array(4), publicKeyFingerprint: createHash('sha256').update(new Uint8Array(4)).digest('base64') }))).rejects.toThrow('Valid device public identity is required');
    expect(h.grants.get('grant-1')?.consumedAt).toBeUndefined();
  });

  it('a fingerprint that does not match the presented public key is rejected', async () => {
    const h = harness([makeGrant()], [activeMembership], [activeBusiness]);
    await expect(h.service.execute(makeInput({ publicKeyFingerprint: 'not-the-real-fingerprint' }))).rejects.toThrow('fingerprint mismatch');
  });

  it('a conflicting device registration (same business/device/keyVersion, different identity) fails closed and issues no credential', async () => {
    const h = harness([makeGrant()], [activeMembership], [activeBusiness]);
    await h.deviceStore.save({
      businessId: identifier('business-1', 'BusinessId'), actorId: identifier('someone-else', 'ActorId'), membershipId: identifier('other-membership', 'MembershipId'),
      deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('other-key', 'DeviceKeyId'), deviceKeyVersion: 1,
      publicKey: new Uint8Array(65).fill(1), publicKeyFingerprint: 'other-fingerprint', status: 'active', authorityEpoch: { value: 1 }, createdAt: new Date(0),
    });
    await expect(h.service.execute(makeInput())).rejects.toThrow('Conflicting device key registration');
    expect(h.issueCount).toBe(0);
  });

  it('a membership missing register_devices scope is rejected (grant cannot exceed the membership it is bound to)', async () => {
    const h = harness([makeGrant()], [{ ...activeMembership, authorityScope: new AuthorityScope(['send_orders']) }], [activeBusiness]);
    await expect(h.service.execute(makeInput())).rejects.toThrow('register devices');
  });

  it('a grantedDeviceScope wider than the current membership scope is rejected at issuance, not silently narrowed', async () => {
    const h = harness(
      [makeGrant({ grantedDeviceScope: new AuthorityScope(['send_orders', 'manage_memberships']) })],
      [{ ...activeMembership, authorityScope: new AuthorityScope(['send_orders', 'issue_credentials', 'register_devices']) }],
      [activeBusiness],
    );
    await expect(h.service.execute(makeInput())).rejects.toThrow('authority rejected');
  });
});
