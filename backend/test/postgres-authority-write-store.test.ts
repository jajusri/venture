import { describe, expect, it } from 'vitest';
import type { DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { AuthorityScope, identifier } from '../services/trust/src/domain/authority.js';
import {
  PostgresAuthorityMutationStore,
  PostgresBusinessBootstrapStore,
  PostgresDeviceRegistrationStore,
  PostgresMembershipApprovalStore,
} from '../services/trust/src/persistence/postgres-authority-write-store.js';
import { deriveIntentScopedId } from '../services/trust/src/persistence/file-backed-authority-store.js';

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
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    return Promise.resolve({ rows: [], rowCount: 0 });
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
