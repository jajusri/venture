import { createHash } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { AuthorityScope, identifier, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
import { hashEnrollmentGrantSecret, validateEnrollmentGrantClaim, type EnrollmentGrantId } from '../services/trust/src/domain/enrollment.js';
import { DeviceEnrollmentRejected } from '../services/trust/src/application/consume-enrollment-grant.js';
import { PostgresEnrollmentGrantStore } from '../services/trust/src/persistence/postgres-enrollment-grant-store.js';

/**
 * SQL-shape/parameter-binding verification (never executes SQL), mirroring this codebase's own
 * `RecordingDatabase` convention (`postgres-authority-write-store.test.ts`).
 */
class RecordingDatabase implements DatabaseSession {
  readonly calls: { sql: string; parameters: readonly unknown[] }[] = [];
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    return Promise.resolve({ rows: [], rowCount: /^\s*INSERT/i.test(sql) ? 1 : 0 });
  }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> { return work(this); }
  async close(): Promise<void> {}
}

/**
 * Stateful fake modeling `trust_enrollment_grant` + `trust_business_authority` +
 * `trust_business_membership` + `trust_registered_device` -- real INSERT/SELECT/UPDATE/ON-CONFLICT
 * table semantics AND real transaction rollback-on-throw (snapshot/restore), same convention as
 * `StatefulBusinessDatabase` in `postgres-authority-write-store.test.ts`. `SELECT ... FOR UPDATE`
 * is modeled as a plain read here -- this fake, like that one, cannot reproduce Postgres's actual
 * row-lock BLOCKING behavior for truly concurrent transactions (that is provable only against live
 * Postgres, see this store's own top-of-file boundary note); what it DOES prove is the exact code
 * path a real concurrent second attempt would hit after the first commits or rolls back: the
 * guarded final UPDATE, and that a rejected attempt never marks a grant consumed.
 */
interface FakeBusinessRow { business_id: string; status: string; authority_epoch: string; created_at: Date }
interface FakeMembershipRow { membership_id: string; business_id: string; actor_id: string; status: string; authority_scope: string[]; authority_epoch: string; created_at: Date; modified_at: Date }
interface FakeDeviceRow { business_id: string; actor_id: string; membership_id: string; device_id: string; device_key_id: string; device_key_version: number; public_key: Buffer; public_key_fingerprint: string; status: string; authority_epoch: string; created_at: Date; revoked_at: Date | null }
interface FakeGrantRow { grant_id: string; business_id: string; actor_id: string; membership_id: string; granted_device_scope: string[]; grant_secret_hash: string; issued_at: Date; expires_at: Date; consumed_at: Date | null; consumed_by_device_id: string | null }

class StatefulEnrollmentDatabase implements Database {
  private businesses: FakeBusinessRow[] = [];
  private memberships: FakeMembershipRow[] = [];
  private devices: FakeDeviceRow[] = [];
  private grants: FakeGrantRow[] = [];
  readonly calls: { sql: string; parameters: readonly unknown[] }[] = [];

  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    if (/^\s*INSERT INTO trust_enrollment_grant/i.test(sql)) {
      const [grantId, businessId, actorId, membershipId, scope, hash, issuedAt, expiresAt] = parameters as [string, string, string, string, string[], string, Date, Date];
      if (this.grants.some((g) => g.grant_id === grantId)) throw new Error('simulated primary key violation: duplicate grant_id');
      this.grants.push({ grant_id: grantId, business_id: businessId, actor_id: actorId, membership_id: membershipId, granted_device_scope: scope, grant_secret_hash: hash, issued_at: issuedAt, expires_at: expiresAt, consumed_at: null, consumed_by_device_id: null });
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*SELECT \* FROM trust_enrollment_grant/i.test(sql)) {
      const [grantId] = parameters as [string];
      const row = this.grants.find((g) => g.grant_id === grantId);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*UPDATE trust_enrollment_grant/i.test(sql)) {
      const [consumedAt, consumedByDeviceId, grantId] = parameters as [Date, string, string];
      const row = this.grants.find((g) => g.grant_id === grantId);
      if (!row || row.consumed_at !== null) return Promise.resolve({ rows: [], rowCount: 0 });
      row.consumed_at = consumedAt; row.consumed_by_device_id = consumedByDeviceId;
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*SELECT business_id, status, authority_epoch, created_at FROM trust_business_authority/i.test(sql)) {
      const [businessId] = parameters as [string];
      const row = this.businesses.find((b) => b.business_id === businessId);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_business_membership WHERE membership_id/i.test(sql)) {
      const [membershipId] = parameters as [string];
      const row = this.memberships.find((m) => m.membership_id === membershipId);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_registered_device/i.test(sql)) {
      const [businessId, deviceId, keyVersion] = parameters as [string, string, number];
      const row = this.devices.find((d) => d.business_id === businessId && d.device_id === deviceId && d.device_key_version === keyVersion);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*INSERT INTO trust_registered_device/i.test(sql)) {
      const [businessId, actorId, membershipId, deviceId, deviceKeyId, deviceKeyVersion, publicKey, fingerprint, status, authorityEpoch, createdAt] =
        parameters as [string, string, string, string, string, number, Buffer, string, string, number, Date];
      if (this.devices.some((d) => d.business_id === businessId && d.device_id === deviceId && d.device_key_version === deviceKeyVersion)) return Promise.resolve({ rows: [], rowCount: 0 });
      this.devices.push({ business_id: businessId, actor_id: actorId, membership_id: membershipId, device_id: deviceId, device_key_id: deviceKeyId, device_key_version: deviceKeyVersion, public_key: publicKey, public_key_fingerprint: fingerprint, status, authority_epoch: String(authorityEpoch), created_at: createdAt, revoked_at: null });
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    return Promise.resolve({ rows: [], rowCount: 0 });
  }

  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> {
    const snapshot = { businesses: [...this.businesses], memberships: [...this.memberships], devices: [...this.devices], grants: this.grants.map((g) => ({ ...g })) };
    try {
      return await work(this);
    } catch (error) {
      this.businesses = snapshot.businesses; this.memberships = snapshot.memberships; this.devices = snapshot.devices; this.grants = snapshot.grants;
      throw error;
    }
  }
  async close(): Promise<void> {}

  seedBusiness(row: FakeBusinessRow): void { this.businesses.push(row); }
  seedMembership(row: FakeMembershipRow): void { this.memberships.push(row); }
  seedDevice(row: FakeDeviceRow): void { this.devices.push(row); }
  seedGrant(row: FakeGrantRow): void { this.grants.push(row); }
  get storedDeviceCount(): number { return this.devices.length; }
  grantById(grantId: string): FakeGrantRow | undefined { return this.grants.find((g) => g.grant_id === grantId); }
}

const NOW = new Date(10_000);
const SECRET = 'a-genuinely-long-enough-secret-value';
const GRANT_ID = 'grant-1';
const BUSINESS_ID = 'business-1'; const ACTOR_ID = 'actor-1'; const MEMBERSHIP_ID = 'membership-1';

function seedGrant(db: StatefulEnrollmentDatabase, overrides: Partial<FakeGrantRow> = {}): void {
  db.seedGrant({
    grant_id: GRANT_ID, business_id: BUSINESS_ID, actor_id: ACTOR_ID, membership_id: MEMBERSHIP_ID,
    granted_device_scope: ['send_orders'], grant_secret_hash: hashEnrollmentGrantSecret(SECRET),
    issued_at: new Date(0), expires_at: new Date(NOW.getTime() + 900_000), consumed_at: null, consumed_by_device_id: null,
    ...overrides,
  });
}

function seedActiveBusinessAndMembership(db: StatefulEnrollmentDatabase): void {
  db.seedBusiness({ business_id: BUSINESS_ID, status: 'active', authority_epoch: '1', created_at: new Date(0) });
  db.seedMembership({ membership_id: MEMBERSHIP_ID, business_id: BUSINESS_ID, actor_id: ACTOR_ID, status: 'active', authority_scope: ['send_orders', 'issue_credentials', 'register_devices'], authority_epoch: '1', created_at: new Date(0), modified_at: new Date(0) });
}

const publicKey = new Uint8Array(65).fill(9);
const fingerprint = createHash('sha256').update(publicKey).digest('base64');
const deviceRegistration: Omit<RegisteredBusinessDevice, 'businessId' | 'actorId' | 'membershipId'> = {
  deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('device-1-key-1', 'DeviceKeyId'), deviceKeyVersion: 1,
  publicKey, publicKeyFingerprint: fingerprint, status: 'active', authorityEpoch: { value: 1 }, createdAt: NOW,
};

async function registerDeviceWork(grant: { businessId: string; actorId: string; membershipId: string }, deviceStore: { save(device: RegisteredBusinessDevice): Promise<RegisteredBusinessDevice> }) {
  return deviceStore.save({ ...deviceRegistration, businessId: identifier(grant.businessId, 'BusinessId'), actorId: identifier(grant.actorId, 'ActorId'), membershipId: identifier(grant.membershipId, 'MembershipId') });
}

describe('PostgresEnrollmentGrantStore SQL shape (no live database in this environment)', () => {
  it('create() inserts exactly one parameterized row into the existing trust_enrollment_grant table only', async () => {
    const db = new RecordingDatabase();
    await new PostgresEnrollmentGrantStore(db).create({
      grantId: identifier(GRANT_ID, 'EnrollmentGrantId') as EnrollmentGrantId, businessId: identifier(BUSINESS_ID, 'BusinessId'),
      actorId: identifier(ACTOR_ID, 'ActorId'), membershipId: identifier(MEMBERSHIP_ID, 'MembershipId'),
      grantedDeviceScope: new AuthorityScope(['send_orders']), grantSecretHash: hashEnrollmentGrantSecret(SECRET), issuedAt: new Date(0), expiresAt: new Date(900_000),
    });
    expect(db.calls).toHaveLength(1);
    expect(db.calls[0]?.sql).toContain('INSERT INTO trust_enrollment_grant');
    expect(db.calls[0]?.sql).not.toMatch(/ALTER TABLE|CREATE TABLE|DROP/);
  });

  it('consumeAndRegister() claims the grant with a row-locking read, never a bare unlocked SELECT', async () => {
    const db = new RecordingDatabase();
    await expect(new PostgresEnrollmentGrantStore(db).consumeAndRegister(GRANT_ID, 'device-1', async () => 'ignored')).rejects.toMatchObject({ reason: 'grant_not_found' });
    const select = db.calls.find((c) => /SELECT \* FROM trust_enrollment_grant/i.test(c.sql));
    expect(select?.sql).toMatch(/FOR UPDATE/i);
  });
});

describe('PostgresEnrollmentGrantStore.consumeAndRegister (stateful, real transaction/rollback semantics)', () => {
  it('a valid grant is claimed, live Business/Membership are re-fetched, and the device store is bound to the SAME transaction', async () => {
    const db = new StatefulEnrollmentDatabase();
    seedGrant(db); seedActiveBusinessAndMembership(db);
    const store = new PostgresEnrollmentGrantStore(db);
    const result = await store.consumeAndRegister(GRANT_ID, 'device-1', async (grant, deviceStore, authority) => {
      const business = await authority.findBusiness(grant.businessId);
      const membership = await authority.findMembership(grant.membershipId);
      expect(business?.status).toBe('active');
      expect(membership?.status).toBe('active');
      return registerDeviceWork(grant, deviceStore);
    });
    expect(result.deviceId).toBe('device-1');
    expect(db.storedDeviceCount).toBe(1);
    expect(db.grantById(GRANT_ID)?.consumed_at).not.toBeNull();
    expect(db.grantById(GRANT_ID)?.consumed_by_device_id).toBe('device-1');
  });

  it('second consumption of the same grant is rejected and does not touch the already-registered device', async () => {
    const db = new StatefulEnrollmentDatabase();
    seedGrant(db); seedActiveBusinessAndMembership(db);
    const store = new PostgresEnrollmentGrantStore(db);
    await store.consumeAndRegister(GRANT_ID, 'device-1', (grant, deviceStore) => registerDeviceWork(grant, deviceStore));
    await expect(store.consumeAndRegister(GRANT_ID, 'device-2', (grant, deviceStore) => registerDeviceWork({ ...grant, businessId: grant.businessId }, deviceStore))).rejects.toMatchObject({ reason: 'grant_already_consumed' });
    expect(db.storedDeviceCount).toBe(1);
  });

  it('an unknown grant id is rejected before any work runs', async () => {
    const db = new StatefulEnrollmentDatabase();
    const store = new PostgresEnrollmentGrantStore(db);
    let workRan = false;
    await expect(store.consumeAndRegister('no-such-grant', 'device-1', async () => { workRan = true; return null; })).rejects.toMatchObject({ reason: 'grant_not_found' });
    expect(workRan).toBe(false);
  });

  it('a rejection inside work() (e.g. wrong secret, checked by the caller before touching the store) rolls back and leaves the grant unconsumed for a legitimate retry', async () => {
    const db = new StatefulEnrollmentDatabase();
    seedGrant(db); seedActiveBusinessAndMembership(db);
    const store = new PostgresEnrollmentGrantStore(db);
    await expect(store.consumeAndRegister(GRANT_ID, 'device-1', async (grant) => {
      const failure = validateEnrollmentGrantClaim(grant, { presentedSecret: 'wrong-secret-value-long-enough', now: NOW });
      if (failure) throw new DeviceEnrollmentRejected(failure);
      return null;
    })).rejects.toMatchObject({ reason: 'grant_secret_mismatch' });
    expect(db.grantById(GRANT_ID)?.consumed_at).toBeNull();
    expect(db.storedDeviceCount).toBe(0);
    // A legitimate retry with the correct secret still works -- the rejected attempt did not burn it.
    const result = await store.consumeAndRegister(GRANT_ID, 'device-1', (grant, deviceStore) => registerDeviceWork(grant, deviceStore));
    expect(result.deviceId).toBe('device-1');
  });

  it('a write failure partway through (device INSERT throws) rolls back the grant claim too -- no half-consumed, unregistered state survives', async () => {
    class FailingDeviceInsertDatabase extends StatefulEnrollmentDatabase {
      query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
        if (/^\s*INSERT INTO trust_registered_device/i.test(sql)) throw new Error('simulated connection loss mid-transaction');
        return super.query(sql, parameters);
      }
    }
    const db = new FailingDeviceInsertDatabase();
    seedGrant(db); seedActiveBusinessAndMembership(db);
    const store = new PostgresEnrollmentGrantStore(db);
    await expect(store.consumeAndRegister(GRANT_ID, 'device-1', (grant, deviceStore) => registerDeviceWork(grant, deviceStore))).rejects.toThrow('simulated connection loss mid-transaction');
    expect(db.grantById(GRANT_ID)?.consumed_at).toBeNull();
    expect(db.storedDeviceCount).toBe(0);
  });

  it('a conflicting pre-existing device (same business/device/keyVersion, different key) fails closed via the reused, certified device-equivalence check, and leaves the grant unconsumed', async () => {
    const db = new StatefulEnrollmentDatabase();
    seedGrant(db); seedActiveBusinessAndMembership(db);
    db.seedDevice({ business_id: BUSINESS_ID, actor_id: 'someone-else', membership_id: 'other-membership', device_id: 'device-1', device_key_id: 'other-key', device_key_version: 1, public_key: Buffer.from(new Uint8Array(65).fill(1)), public_key_fingerprint: 'other-fingerprint', status: 'active', authority_epoch: '1', created_at: new Date(0), revoked_at: null });
    const store = new PostgresEnrollmentGrantStore(db);
    await expect(store.consumeAndRegister(GRANT_ID, 'device-1', (grant, deviceStore) => registerDeviceWork(grant, deviceStore))).rejects.toThrow('Conflicting device key registration');
    expect(db.grantById(GRANT_ID)?.consumed_at).toBeNull();
  });

  it('Business/Membership are re-fetched LIVE at consumption time, not trusted from the grant\'s own issuance-time snapshot -- a Business suspended after the grant was issued is caught', async () => {
    const db = new StatefulEnrollmentDatabase();
    seedGrant(db);
    db.seedBusiness({ business_id: BUSINESS_ID, status: 'suspended', authority_epoch: '1', created_at: new Date(0) });
    db.seedMembership({ membership_id: MEMBERSHIP_ID, business_id: BUSINESS_ID, actor_id: ACTOR_ID, status: 'active', authority_scope: ['send_orders', 'issue_credentials', 'register_devices'], authority_epoch: '1', created_at: new Date(0), modified_at: new Date(0) });
    const store = new PostgresEnrollmentGrantStore(db);
    await expect(store.consumeAndRegister(GRANT_ID, 'device-1', async (grant, deviceStore, authority) => {
      const business = await authority.findBusiness(grant.businessId);
      if (!business || business.status !== 'active') throw new DeviceEnrollmentRejected('business_inactive');
      return registerDeviceWork(grant, deviceStore);
    })).rejects.toMatchObject({ reason: 'business_inactive' });
    expect(db.grantById(GRANT_ID)?.consumed_at).toBeNull();
    expect(db.storedDeviceCount).toBe(0);
  });
});
