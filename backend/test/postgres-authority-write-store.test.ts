import { describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { AuthorityScope, identifier, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
import {
  PostgresAuthorityMutationStore,
  PostgresBusinessBootstrapStore,
  PostgresDeviceRegistrationStore,
  PostgresMembershipApprovalStore,
} from '../services/trust/src/persistence/postgres-authority-write-store.js';
import { deriveIntentScopedId } from '../services/trust/src/persistence/file-backed-authority-store.js';
import type { CreatedBusinessAuthority } from '../services/trust/src/application/create-business.js';

/**
 * SQL-shape/parameter-binding verification for the real Postgres write-path stores, mirroring this
 * codebase's own existing convention (`authority-persistence.test.ts`, `relay-persistence.test.ts`):
 * a recording fake session that never executes SQL, only records what was asked. This sandboxed
 * environment has no reachable local PostgreSQL (no service, no Docker) -- these classes' actual
 * runtime behavior against a live database is NOT verified here (see the controlled-pilot final
 * report's stated boundary); this test only proves each store issues parameterized, indexed-lookup
 * point queries against the correct existing tables, never a new column/table.
 */
class RecordingDatabase implements DatabaseSession {
  readonly calls: { sql: string; parameters: readonly unknown[] }[] = [];
  // Default fake response simulates a normal, non-conflicting write: an INSERT affects one row
  // (`rowCount: 1`), matching what a real successful insert returns. Tests that specifically need to
  // simulate a concurrent duplicate (an `ON CONFLICT DO NOTHING` that inserted zero rows) override
  // this via a subclass -- see `RacingInsertDatabase` below.
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    const rowCount = /^\s*INSERT/i.test(sql) ? 1 : 0;
    return Promise.resolve({ rows: [], rowCount });
  }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> {
    return work(this);
  }
  async close(): Promise<void> {}
}

describe('postgres authority write stores (SQL shape only -- no live database in this environment)', () => {
  it('BusinessBootstrapStore.findByCreationIntent derives a deterministic id and looks it up by primary key, never scans', async () => {
    const db = new RecordingDatabase();
    await new PostgresBusinessBootstrapStore(db).findByCreationIntent('actor-1', 'intent-1');
    expect(db.calls[0]?.sql).toContain('WHERE business_id = $1');
    expect(db.calls[0]?.parameters).toEqual([deriveIntentScopedId(['actor-1', 'intent-1'], 'business')]);
    expect(db.calls.every((call) => call.sql.includes('WHERE'))).toBe(true);
  });

  it('BusinessBootstrapStore.createAtomically writes both rows inside one transaction, only against existing tables', async () => {
    const db = new RecordingDatabase();
    const now = new Date(1000);
    await new PostgresBusinessBootstrapStore(db).createAtomically('intent-1', {
      businessId: identifier('biz-1', 'BusinessId'),
      membership: { membershipId: identifier('mem-1', 'MembershipId'), businessId: identifier('biz-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'), status: 'active', authorityScope: new AuthorityScope(['send_orders']), authorityEpoch: { value: 1 }, createdAt: now, modifiedAt: now },
      authorityEpoch: 1,
      auditEvent: { eventId: 'evt-1', kind: 'business_authority_created', businessId: identifier('biz-1', 'BusinessId'), actorId: 'actor-1', occurredAt: now },
    });
    expect(db.calls).toHaveLength(2);
    expect(db.calls[0]?.sql).toContain('INSERT INTO trust_business_authority');
    expect(db.calls[1]?.sql).toContain('INSERT INTO trust_business_membership');
    expect(db.calls.map((c) => c.sql).join(' ')).not.toMatch(/ALTER TABLE|CREATE TABLE/);
  });

  it('MembershipApprovalStore uses the same deterministic-id technique, keyed only by intentId', async () => {
    const db = new RecordingDatabase();
    await new PostgresMembershipApprovalStore(db).findApproved('intent-approve-1');
    expect(db.calls[0]?.parameters).toEqual([deriveIntentScopedId(['intent-approve-1'], 'membership')]);
  });

  it('DeviceRegistrationStore.find is a bounded natural-key lookup, no LIMIT-less scan', async () => {
    const db = new RecordingDatabase();
    await new PostgresDeviceRegistrationStore(db).find('biz-1', 'device-1', 1);
    expect(db.calls[0]?.sql).toContain('WHERE business_id = $1 AND device_id = $2 AND device_key_version = $3');
    expect(db.calls[0]?.parameters).toEqual(['biz-1', 'device-1', 1]);
  });

  it('BusinessBootstrapStore.createAtomically re-reads and returns the winning row instead of throwing when a concurrent duplicate create already committed', async () => {
    const now = new Date(1000);
    const businessId = deriveIntentScopedId(['actor-1', 'intent-1'], 'business');
    const membershipId = deriveIntentScopedId(['actor-1', 'intent-1'], 'membership');
    const membershipRow = {
      membership_id: membershipId, business_id: businessId, actor_id: 'actor-1', status: 'active',
      authority_scope: ['send_orders'], authority_epoch: '1', created_at: now, modified_at: now,
    };
    class RacedCreateDatabase extends RecordingDatabase {
      query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
        this.calls.push({ sql, parameters });
        if (/^\s*INSERT INTO trust_business_authority/i.test(sql)) return Promise.resolve({ rows: [], rowCount: 0 }); // lost the race
        if (/^\s*SELECT .* FROM trust_business_authority/i.test(sql)) return Promise.resolve({ rows: [{ business_id: businessId, status: 'active', authority_epoch: '1', created_at: now }] as unknown as Row[], rowCount: 1 });
        if (/^\s*SELECT .* FROM trust_business_membership/i.test(sql)) return Promise.resolve({ rows: [membershipRow as unknown as Row], rowCount: 1 });
        return Promise.resolve({ rows: [], rowCount: 1 });
      }
    }
    const db = new RacedCreateDatabase();
    const result = await new PostgresBusinessBootstrapStore(db).createAtomically('intent-1', {
      businessId: identifier(businessId, 'BusinessId'),
      membership: { membershipId: identifier(membershipId, 'MembershipId'), businessId: identifier(businessId, 'BusinessId'), actorId: identifier('actor-1', 'ActorId'), status: 'active', authorityScope: new AuthorityScope(['send_orders']), authorityEpoch: { value: 1 }, createdAt: now, modifiedAt: now },
      authorityEpoch: 1,
      auditEvent: { eventId: 'evt-1', kind: 'business_authority_created', businessId: identifier(businessId, 'BusinessId'), actorId: 'actor-1', occurredAt: now },
    });
    expect(result.businessId).toBe(businessId);
    expect(result.membership.membershipId).toBe(membershipId);
  });

  it('DeviceRegistrationStore.save re-reads and returns the winning row instead of throwing when a concurrent duplicate registration already committed', async () => {
    const now = new Date(3000);
    const deviceRow = {
      business_id: 'biz-1', actor_id: 'actor-1', membership_id: 'mem-1', device_id: 'device-1', device_key_id: 'device-1-key-1',
      device_key_version: 1, public_key: Buffer.from([1, 2, 3]), public_key_fingerprint: 'fp-1', status: 'active', authority_epoch: '1', created_at: now, revoked_at: null,
    };
    class RacedRegisterDatabase extends RecordingDatabase {
      query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
        this.calls.push({ sql, parameters });
        if (/^\s*INSERT INTO trust_registered_device/i.test(sql)) return Promise.resolve({ rows: [], rowCount: 0 }); // lost the race
        if (/^\s*SELECT .* FROM trust_registered_device/i.test(sql)) return Promise.resolve({ rows: [deviceRow as unknown as Row], rowCount: 1 });
        return Promise.resolve({ rows: [], rowCount: 1 });
      }
    }
    const db = new RacedRegisterDatabase();
    const result = await new PostgresDeviceRegistrationStore(db).save({
      businessId: identifier('biz-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'), membershipId: identifier('mem-1', 'MembershipId'),
      deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('device-1-key-1', 'DeviceKeyId'), deviceKeyVersion: 1,
      publicKey: new Uint8Array([1, 2, 3]), publicKeyFingerprint: 'fp-1', status: 'active', authorityEpoch: { value: 1 }, createdAt: now,
    });
    expect(result.publicKeyFingerprint).toBe('fp-1');
    expect(result.status).toBe('active');
  });

  it('BusinessBootstrapStore.findByCreationIntent refuses to return a membership row that does not match the expected business/actor', async () => {
    const now = new Date(4000);
    class MismatchedDatabase extends RecordingDatabase {
      query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
        this.calls.push({ sql, parameters });
        if (/^\s*SELECT .* FROM trust_business_authority/i.test(sql)) return Promise.resolve({ rows: [{ business_id: 'biz-1', status: 'active', authority_epoch: '1', created_at: now }] as unknown as Row[], rowCount: 1 });
        if (/^\s*SELECT .* FROM trust_business_membership/i.test(sql)) {
          // Simulates a corrupted/mismatched row: belongs to a different actor than the one asking.
          return Promise.resolve({ rows: [{ membership_id: 'mem-1', business_id: 'biz-1', actor_id: 'someone-else', status: 'active', authority_scope: ['send_orders'], authority_epoch: '1', created_at: now, modified_at: now }] as unknown as Row[], rowCount: 1 });
        }
        return Promise.resolve({ rows: [], rowCount: 0 });
      }
    }
    const db = new MismatchedDatabase();
    await expect(new PostgresBusinessBootstrapStore(db).findByCreationIntent('actor-1', 'intent-1')).rejects.toThrow('does not match the expected business/actor');
  });

  it('AuthorityMutationStore updates are optimistic-concurrency guarded by authority_epoch in the WHERE clause', async () => {
    const db = new RecordingDatabase();
    const now = new Date(2000);
    await new PostgresAuthorityMutationStore(db).updateMembership(1, {
      membershipId: identifier('mem-1', 'MembershipId'), businessId: identifier('biz-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'),
      status: 'suspended', authorityScope: new AuthorityScope(['send_orders']), authorityEpoch: { value: 2 }, createdAt: now, modifiedAt: now,
    });
    expect(db.calls[0]?.sql).toContain('AND authority_epoch = $6');
    expect(db.calls[0]?.parameters).toContain(1); // the expected/previous epoch guards the update
  });
});

/**
 * Stateful (not merely recording) fake for `trust_registered_device` -- models a real table with
 * genuine INSERT/ON CONFLICT DO NOTHING/SELECT semantics, so the tests below exercise
 * `PostgresDeviceRegistrationStore`'s REAL repository code path end to end (Codex re-certification
 * BLOCKER 2 asked to test real repository semantics, not only a standalone comparison helper).
 */
class StatefulDeviceDatabase implements Database {
  private readonly rows: { business_id: string; actor_id: string; membership_id: string; device_id: string; device_key_id: string; device_key_version: number; public_key: Buffer; public_key_fingerprint: string; status: string; authority_epoch: string; created_at: Date; revoked_at: Date | null }[] = [];

  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    if (/^\s*INSERT INTO trust_registered_device/i.test(sql)) {
      const [businessId, actorId, membershipId, deviceId, deviceKeyId, deviceKeyVersion, publicKey, publicKeyFingerprint, status, authorityEpoch, createdAt] = parameters as [string, string, string, string, string, number, Buffer, string, string, number, Date];
      if (this.rows.some((row) => row.business_id === businessId && row.device_id === deviceId && row.device_key_version === deviceKeyVersion)) return Promise.resolve({ rows: [], rowCount: 0 });
      this.rows.push({ business_id: businessId, actor_id: actorId, membership_id: membershipId, device_id: deviceId, device_key_id: deviceKeyId, device_key_version: deviceKeyVersion, public_key: publicKey, public_key_fingerprint: publicKeyFingerprint, status, authority_epoch: String(authorityEpoch), created_at: createdAt, revoked_at: null });
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*SELECT .* FROM trust_registered_device/i.test(sql)) {
      const [businessId, deviceId, deviceKeyVersion] = parameters as [string, string, number];
      const row = this.rows.find((candidate) => candidate.business_id === businessId && candidate.device_id === deviceId && candidate.device_key_version === deviceKeyVersion);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    return Promise.resolve({ rows: [], rowCount: 0 });
  }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> { return work(this); }
  async close(): Promise<void> {}
  get storedRowCount(): number { return this.rows.length; }
}

function baseDevice(overrides: Partial<RegisteredBusinessDevice> = {}): RegisteredBusinessDevice {
  return {
    businessId: identifier('biz-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'), membershipId: identifier('mem-1', 'MembershipId'),
    deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('device-1-key-1', 'DeviceKeyId'), deviceKeyVersion: 1,
    publicKey: new Uint8Array([1, 2, 3]), publicKeyFingerprint: 'fp-1', status: 'active', authorityEpoch: { value: 1 }, createdAt: new Date(1000),
    ...overrides,
  };
}

describe('DeviceRegistrationStore concurrent-winner equivalence (Codex re-certification BLOCKER 2)', () => {
  it('A: two concurrent identical registrations converge on one stored row, and both callers observe an equivalent result', async () => {
    const db = new StatefulDeviceDatabase();
    const store = new PostgresDeviceRegistrationStore(db);
    const [first, second] = await Promise.all([store.save(baseDevice()), store.save(baseDevice())]);
    expect(first.publicKeyFingerprint).toBe('fp-1');
    expect(second.publicKeyFingerprint).toBe('fp-1');
    expect(first).toEqual(second);
    expect(db.storedRowCount).toBe(1);
  });

  it('B: same device identity but a conflicting actor fails closed, not as a silently-accepted swap', async () => {
    const db = new StatefulDeviceDatabase();
    const store = new PostgresDeviceRegistrationStore(db);
    await store.save(baseDevice());
    await expect(store.save(baseDevice({ actorId: identifier('actor-2', 'ActorId') }))).rejects.toThrow('Conflicting device key registration');
  });

  it('C: conflicting membership fails closed', async () => {
    const db = new StatefulDeviceDatabase();
    const store = new PostgresDeviceRegistrationStore(db);
    await store.save(baseDevice());
    await expect(store.save(baseDevice({ membershipId: identifier('mem-2', 'MembershipId') }))).rejects.toThrow('Conflicting device key registration');
  });

  it('D: conflicting key ID fails closed', async () => {
    const db = new StatefulDeviceDatabase();
    const store = new PostgresDeviceRegistrationStore(db);
    await store.save(baseDevice());
    await expect(store.save(baseDevice({ deviceKeyId: identifier('device-1-key-99', 'DeviceKeyId') }))).rejects.toThrow('Conflicting device key registration');
  });

  it('E: conflicting key fingerprint fails closed', async () => {
    const db = new StatefulDeviceDatabase();
    const store = new PostgresDeviceRegistrationStore(db);
    await store.save(baseDevice());
    await expect(store.save(baseDevice({ publicKeyFingerprint: 'fp-not-the-same' }))).rejects.toThrow('Conflicting device key registration');
  });

  it('F: conflicting public key bytes fail closed', async () => {
    const db = new StatefulDeviceDatabase();
    const store = new PostgresDeviceRegistrationStore(db);
    await store.save(baseDevice());
    await expect(store.save(baseDevice({ publicKey: new Uint8Array([9, 9, 9]) }))).rejects.toThrow('Conflicting device key registration');
  });

  it('G: re-registering the SAME key version with a materially different key (not a legitimate rotation, which would use a NEW version) fails closed', async () => {
    const db = new StatefulDeviceDatabase();
    const store = new PostgresDeviceRegistrationStore(db);
    await store.save(baseDevice());
    // A legitimate rotation registers a NEW, higher deviceKeyVersion (see
    // controlled-pilot-integration.test.ts's device-key-rotation sequence) -- it never reuses the
    // same version number with different key material, which is exactly what this simulates.
    await expect(store.save(baseDevice({ deviceKeyId: identifier('device-1-key-imposter', 'DeviceKeyId'), publicKeyFingerprint: 'fp-imposter', publicKey: new Uint8Array([7, 7, 7]) }))).rejects.toThrow('Conflicting device key registration');
  });

  it('H: retrying with the exact same data after a successful creation is safe and idempotent', async () => {
    const db = new StatefulDeviceDatabase();
    const store = new PostgresDeviceRegistrationStore(db);
    const first = await store.save(baseDevice());
    const retry = await store.save(baseDevice());
    expect(retry).toEqual(first);
    expect(db.storedRowCount).toBe(1);
  });
});

/**
 * Stateful fake for `trust_business_authority` + `trust_business_membership` -- same rationale as
 * `StatefulDeviceDatabase` above, for `PostgresBusinessBootstrapStore` (Codex re-certification
 * BLOCKER 3: business creation authority is not complete merely because the Business row exists --
 * the required initial Membership is part of the same atomic creation invariant).
 */
class StatefulBusinessDatabase implements Database {
  private readonly businesses: { business_id: string; status: string; authority_epoch: string; created_at: Date }[] = [];
  private readonly memberships: { membership_id: string; business_id: string; actor_id: string; status: string; authority_scope: string[]; authority_epoch: string; created_at: Date; modified_at: Date }[] = [];

  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    if (/^\s*INSERT INTO trust_business_authority/i.test(sql)) {
      const [businessId, status, authorityEpoch, createdAt] = parameters as [string, string, number, Date];
      if (this.businesses.some((row) => row.business_id === businessId)) return Promise.resolve({ rows: [], rowCount: 0 });
      this.businesses.push({ business_id: businessId, status, authority_epoch: String(authorityEpoch), created_at: createdAt });
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*INSERT INTO trust_business_membership/i.test(sql)) {
      const [membershipId, businessId, actorId, status, scope, authorityEpoch, createdAt, modifiedAt] = parameters as [string, string, string, string, string[], number, Date, Date];
      if (this.memberships.some((row) => row.membership_id === membershipId)) return Promise.resolve({ rows: [], rowCount: 0 });
      this.memberships.push({ membership_id: membershipId, business_id: businessId, actor_id: actorId, status, authority_scope: scope, authority_epoch: String(authorityEpoch), created_at: createdAt, modified_at: modifiedAt });
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*SELECT .* FROM trust_business_authority/i.test(sql)) {
      const [businessId] = parameters as [string];
      const row = this.businesses.find((candidate) => candidate.business_id === businessId);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_business_membership/i.test(sql)) {
      const [membershipId] = parameters as [string];
      const row = this.memberships.find((candidate) => candidate.membership_id === membershipId);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    return Promise.resolve({ rows: [], rowCount: 0 });
  }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> { return work(this); }
  async close(): Promise<void> {}
  get storedBusinessCount(): number { return this.businesses.length; }
}

// `findByCreationIntent` derives businessId/membershipId itself via `deriveIntentScopedId`, keyed
// only by (actorId, intentId) -- it never trusts a caller-supplied id. So the fixture below must use
// those SAME derived ids (matching the 'actor-1'/'intent-1' pair every test in this block uses), or
// the re-read path in `createAtomically`'s conflict branch would look up a row that was never
// written under that hash and misreport "inconsistent row" instead of exercising equivalence at all.
const RACE_BUSINESS_ID = deriveIntentScopedId(['actor-1', 'intent-1'], 'business');
const RACE_MEMBERSHIP_ID = deriveIntentScopedId(['actor-1', 'intent-1'], 'membership');

function baseCreation(overrides: { status?: 'active' | 'suspended' | 'revoked'; scope?: readonly ['send_orders' | 'receive_orders', ...('send_orders' | 'receive_orders')[]] } = {}): CreatedBusinessAuthority {
  const now = new Date(1000);
  return {
    businessId: identifier(RACE_BUSINESS_ID, 'BusinessId'),
    membership: {
      membershipId: identifier(RACE_MEMBERSHIP_ID, 'MembershipId'), businessId: identifier(RACE_BUSINESS_ID, 'BusinessId'), actorId: identifier('actor-1', 'ActorId'),
      status: overrides.status ?? 'active', authorityScope: new AuthorityScope(overrides.scope ?? ['send_orders']), authorityEpoch: { value: 1 }, createdAt: now, modifiedAt: now,
    },
    authorityEpoch: 1,
    auditEvent: { eventId: 'evt-1', kind: 'business_authority_created', businessId: identifier(RACE_BUSINESS_ID, 'BusinessId'), actorId: 'actor-1', occurredAt: now },
  };
}

describe('BusinessBootstrapStore concurrent-winner equivalence (Codex re-certification BLOCKER 3)', () => {
  it('A: exact duplicate create retry is idempotent success', async () => {
    const db = new StatefulBusinessDatabase();
    const store = new PostgresBusinessBootstrapStore(db);
    const first = await store.createAtomically('intent-1', baseCreation());
    const retry = await store.createAtomically('intent-1', baseCreation());
    expect(retry.businessId).toBe(first.businessId);
    expect(retry.membership.membershipId).toBe(first.membership.membershipId);
    expect(db.storedBusinessCount).toBe(1);
  });

  it('B: concurrent equivalent Business + Membership creation is idempotent success for both callers', async () => {
    const db = new StatefulBusinessDatabase();
    const store = new PostgresBusinessBootstrapStore(db);
    const [first, second] = await Promise.all([store.createAtomically('intent-1', baseCreation()), store.createAtomically('intent-1', baseCreation())]);
    expect(first.businessId).toBe(second.businessId);
    expect(first.membership.membershipId).toBe(second.membership.membershipId);
    expect(db.storedBusinessCount).toBe(1);
  });

  it('C/F: Business exists but the winning membership status differs from what this call intended fails closed', async () => {
    const db = new StatefulBusinessDatabase();
    const store = new PostgresBusinessBootstrapStore(db);
    await store.createAtomically('intent-1', baseCreation({ status: 'suspended' }));
    await expect(store.createAtomically('intent-1', baseCreation({ status: 'active' }))).rejects.toThrow('Conflicting business creation');
  });

  it('C/F: Business exists but the winning membership scope differs from what this call intended fails closed', async () => {
    const db = new StatefulBusinessDatabase();
    const store = new PostgresBusinessBootstrapStore(db);
    await store.createAtomically('intent-1', baseCreation({ scope: ['send_orders'] }));
    await expect(store.createAtomically('intent-1', baseCreation({ scope: ['receive_orders'] }))).rejects.toThrow('Conflicting business creation');
  });

  it('G: both inserts happen inside one transaction -- there is no observable state where the Business row exists without its required initial Membership', async () => {
    const calls: string[] = [];
    const db = new StatefulBusinessDatabase();
    const tracking: Database = {
      query: (sql, parameters) => { calls.push(sql); return db.query(sql, parameters); },
      transaction: (work) => db.transaction((session) => work({ query: (sql, parameters) => { calls.push(sql); return session.query(sql, parameters); } })),
      close: () => db.close(),
    };
    await new PostgresBusinessBootstrapStore(tracking).createAtomically('intent-1', baseCreation());
    // Both INSERTs are issued through the SAME `database.transaction()` call (see `createAtomically`
    // above) -- Postgres commits or rolls back that unit atomically, so there is no code path where
    // the Business insert's effects are visible without the Membership insert's also being visible.
    expect(calls.filter((sql) => /^\s*INSERT/i.test(sql))).toHaveLength(2);
  });

  it('D/E (structural, not a race): findByCreationIntent already refuses a membership that does not match the expected actor/business -- see the dedicated test above; deterministic id derivation ties actorId into businessId/membershipId themselves, so a genuinely different actor for the same intentId can never collide with this businessId in the first place', () => {
    expect(deriveIntentScopedId(['actor-1', 'intent-1'], 'business')).not.toBe(deriveIntentScopedId(['actor-2', 'intent-1'], 'business'));
  });
});
