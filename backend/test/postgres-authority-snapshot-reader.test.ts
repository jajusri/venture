import { describe, expect, it } from 'vitest';
import type { DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { PostgresTrustAuthoritySnapshotReader } from '../services/trust/src/persistence/postgres-authority-snapshot-reader.js';

/**
 * SQL-shape/parameter-binding verification for Relay's real Postgres authority-freshness reader,
 * mirroring `postgres-authority-write-store.test.ts`'s own convention: a recording fake session that
 * never executes SQL, only records what was asked. No local PostgreSQL is reachable in this
 * sandboxed environment -- this proves the reader pins a consistent isolation level for its three
 * point lookups (Codex Postgres finding: snapshot read atomicity) and assembles the result
 * correctly, not that it behaves identically against a live database.
 */
class RecordingDatabase implements DatabaseSession {
  readonly calls: { sql: string; parameters: readonly unknown[] }[] = [];
  private readonly now = new Date(5000);
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    if (/^\s*SET TRANSACTION/i.test(sql)) return Promise.resolve({ rows: [], rowCount: 0 });
    if (/FROM trust_business_authority/i.test(sql)) return Promise.resolve({ rows: [{ business_id: 'biz-1', status: 'active', authority_epoch: '1', created_at: this.now }] as unknown as Row[], rowCount: 1 });
    if (/FROM trust_registered_device/i.test(sql)) {
      return Promise.resolve({ rows: [{
        business_id: 'biz-1', actor_id: 'actor-1', membership_id: 'mem-1', device_id: 'device-1', device_key_id: 'device-1-key-1',
        device_key_version: 1, public_key: Buffer.from([1, 2, 3]), public_key_fingerprint: 'fp-1', status: 'active', authority_epoch: '1', created_at: this.now, revoked_at: null,
      }] as unknown as Row[], rowCount: 1 });
    }
    if (/FROM trust_business_membership/i.test(sql)) {
      return Promise.resolve({ rows: [{
        membership_id: 'mem-1', business_id: 'biz-1', actor_id: 'actor-1', status: 'active',
        authority_scope: ['receive_orders'], authority_epoch: '1', created_at: this.now, modified_at: this.now,
      }] as unknown as Row[], rowCount: 1 });
    }
    return Promise.resolve({ rows: [], rowCount: 0 });
  }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> { return work(this); }
  async close(): Promise<void> {}
}

describe('PostgresTrustAuthoritySnapshotReader (SQL shape only -- no live database in this environment)', () => {
  it('pins REPEATABLE READ, READ ONLY as the first statement in the transaction before any of the three point lookups', async () => {
    const db = new RecordingDatabase();
    await new PostgresTrustAuthoritySnapshotReader(db).read('biz-1', 'device-1', 1);
    expect(db.calls[0]?.sql).toMatch(/SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY/i);
    expect(db.calls.slice(1).every((call) => call.sql.trim().toUpperCase().startsWith('SELECT'))).toBe(true);
  });

  it('assembles business status, membership, and device from the three lookups', async () => {
    const db = new RecordingDatabase();
    const snapshot = await new PostgresTrustAuthoritySnapshotReader(db).read('biz-1', 'device-1', 1);
    expect(snapshot.businessStatus).toBe('active');
    expect(snapshot.membership?.membershipId).toBe('mem-1');
    expect(snapshot.device?.deviceId).toBe('device-1');
  });

  it('returns an empty snapshot without looking up membership when the device is not found', async () => {
    class NoDeviceDatabase extends RecordingDatabase {
      query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
        this.calls.push({ sql, parameters });
        if (/^\s*SET TRANSACTION/i.test(sql)) return Promise.resolve({ rows: [], rowCount: 0 });
        if (/FROM trust_business_authority/i.test(sql)) return Promise.resolve({ rows: [{ business_id: 'biz-1', status: 'active', authority_epoch: '1', created_at: new Date(5000) }] as unknown as Row[], rowCount: 1 });
        return Promise.resolve({ rows: [], rowCount: 0 });
      }
    }
    const db = new NoDeviceDatabase();
    const snapshot = await new PostgresTrustAuthoritySnapshotReader(db).read('biz-1', 'device-missing', 1);
    expect(snapshot).toEqual({ businessStatus: null, membership: null, device: null });
    expect(db.calls.some((call) => /FROM trust_business_membership/i.test(call.sql))).toBe(false);
  });
});
