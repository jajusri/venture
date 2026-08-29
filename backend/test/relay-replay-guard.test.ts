import { describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { PostgresRelayReplayGuard } from '../services/relay/src/persistence/postgres-relay-replay-guard.js';

/**
 * SQL-shape AND real-repository-semantics verification for the Postgres-backed replay guard,
 * mirroring this codebase's existing convention (`postgres-authority-write-store.test.ts`). No local
 * PostgreSQL is reachable in this sandboxed environment -- a stateful fake models real INSERT/DELETE/
 * ON CONFLICT table semantics so `consume()`'s actual decision logic is exercised end to end, not
 * just the SQL strings it issues.
 */
class StatefulNonceDatabase implements Database {
  readonly calls: { sql: string; parameters: readonly unknown[] }[] = [];
  private readonly rows: { business_id: string; device_id: string; request_id: string; expires_at: number }[] = [];

  seed(row: { businessId: string; deviceId: string; requestId: string; expiresAtEpochMs: number }): void {
    this.rows.push({ business_id: row.businessId, device_id: row.deviceId, request_id: row.requestId, expires_at: row.expiresAtEpochMs });
  }
  get rowCount(): number { return this.rows.length; }

  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    if (/^\s*DELETE FROM relay_authenticated_request_nonce WHERE business_id/i.test(sql)) {
      const [businessId, deviceId, before] = parameters as [string, string, Date];
      const beforeMs = before.getTime();
      const remaining = this.rows.filter((row) => !(row.business_id === businessId && row.device_id === deviceId && row.expires_at < beforeMs));
      const removed = this.rows.length - remaining.length;
      this.rows.length = 0; this.rows.push(...remaining);
      return Promise.resolve({ rows: [], rowCount: removed });
    }
    if (/^\s*DELETE FROM relay_authenticated_request_nonce WHERE ctid IN/i.test(sql)) {
      const [before, limit] = parameters as [Date, number];
      const beforeMs = before.getTime();
      const expired = this.rows.filter((row) => row.expires_at < beforeMs).slice(0, limit);
      for (const victim of expired) {
        const index = this.rows.indexOf(victim);
        if (index >= 0) this.rows.splice(index, 1);
      }
      return Promise.resolve({ rows: [], rowCount: expired.length });
    }
    if (/^\s*INSERT INTO relay_authenticated_request_nonce/i.test(sql)) {
      const [businessId, deviceId, requestId, expiresAt] = parameters as [string, string, string, Date];
      if (this.rows.some((row) => row.business_id === businessId && row.device_id === deviceId && row.request_id === requestId)) {
        return Promise.resolve({ rows: [], rowCount: 0 });
      }
      this.rows.push({ business_id: businessId, device_id: deviceId, request_id: requestId, expires_at: expiresAt.getTime() });
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    return Promise.resolve({ rows: [], rowCount: 0 });
  }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> { return work(this); }
  async close(): Promise<void> {}
}

describe('PostgresRelayReplayGuard (SQL shape + real repository semantics -- no live database in this environment)', () => {
  it('consumes a fresh request id, rejects the exact same request id replayed', async () => {
    const db = new StatefulNonceDatabase();
    const guard = new PostgresRelayReplayGuard(db, 5 * 60_000);
    const now = new Date(1_000_000);
    const first = await guard.consume('biz-1', 'device-1', 'req-1', now, now);
    const replay = await guard.consume('biz-1', 'device-1', 'req-1', now, now);
    expect(first).toBe(true);
    expect(replay).toBe(false);
  });

  it('rejects a request outside the clock tolerance window before ever touching the database', async () => {
    const db = new StatefulNonceDatabase();
    const guard = new PostgresRelayReplayGuard(db, 5 * 60_000);
    const now = new Date(1_000_000);
    const staleTimestamp = new Date(now.getTime() - 3_600_000);
    const result = await guard.consume('biz-1', 'device-1', 'req-stale', staleTimestamp, now);
    expect(result).toBe(false);
    expect(db.calls).toHaveLength(0);
  });

  it('scopes the per-device cleanup delete to the calling business/device, and the global cleanup delete to a bounded, indexed batch', async () => {
    const db = new StatefulNonceDatabase();
    const guard = new PostgresRelayReplayGuard(db, 5 * 60_000);
    const now = new Date(1_000_000);
    await guard.consume('biz-1', 'device-1', 'req-1', now, now);
    const deviceDelete = db.calls.find((call) => /WHERE business_id = \$1 AND device_id = \$2/i.test(call.sql));
    expect(deviceDelete?.parameters).toEqual(['biz-1', 'device-1', now]);
    const globalDelete = db.calls.find((call) => /WHERE ctid IN/i.test(call.sql));
    expect(globalDelete?.sql).toMatch(/expires_at < \$1/);
    expect(globalDelete?.sql).toMatch(/LIMIT \$2/);
    expect(globalDelete?.parameters).toEqual([now, 100]);
  });

  it('global cleanup (Codex small finding) removes globally-expired rows belonging to a device that never submits again, bounded by batch size', async () => {
    const db = new StatefulNonceDatabase();
    const guard = new PostgresRelayReplayGuard(db, 5 * 60_000);
    // A device that went permanently inactive: its nonce rows expired long ago and it will never
    // submit another request, so the PER-DEVICE cleanup path (scoped to the calling device) can
    // never reach these rows -- only the bounded GLOBAL cleanup can.
    db.seed({ businessId: 'biz-inactive', deviceId: 'device-inactive', requestId: 'req-old-1', expiresAtEpochMs: 0 });
    db.seed({ businessId: 'biz-inactive', deviceId: 'device-inactive', requestId: 'req-old-2', expiresAtEpochMs: 0 });
    expect(db.rowCount).toBe(2);
    const now = new Date(1_000_000);
    await guard.consume('biz-active', 'device-active', 'req-fresh', now, now);
    // Both stale rows from the unrelated, permanently-inactive device were reclaimed by the global
    // cleanup pass triggered by this UNRELATED device's own request.
    expect(db.rowCount).toBe(1); // only this consume()'s own fresh insert remains
  });

  it('global cleanup never touches rows that are not yet expired', async () => {
    const db = new StatefulNonceDatabase();
    const guard = new PostgresRelayReplayGuard(db, 5 * 60_000);
    const now = new Date(1_000_000);
    db.seed({ businessId: 'biz-other', deviceId: 'device-other', requestId: 'req-still-valid', expiresAtEpochMs: now.getTime() + 60_000 });
    await guard.consume('biz-active', 'device-active', 'req-fresh', now, now);
    expect(db.rowCount).toBe(2); // the still-valid row plus this consume()'s own fresh insert
  });
});
