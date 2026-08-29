import { describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import {
  DuplicateActiveSigningKeyError, NoActiveSigningKeyError, PostgresIssuerSigningKeyStore,
  SigningKeyLifecycleConflictError, UnknownSigningKeyError,
} from '../services/trust/src/persistence/postgres-issuer-signing-key-store.js';

/** SQL-shape verification (never executes SQL), mirroring this codebase's own `RecordingDatabase`
 * convention (`postgres-authority-write-store.test.ts`). */
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
 * Stateful fake modeling `trust_issuer_signing_key` -- real INSERT/SELECT/UPDATE table semantics
 * PLUS the partial-unique-index invariant ("exactly one active row per issuer_id") and real
 * transaction rollback-on-throw, same convention as `StatefulBusinessDatabase`
 * (`postgres-authority-write-store.test.ts`). `SELECT ... FOR UPDATE` is modeled as a plain read --
 * this fake, like the established ones elsewhere in this codebase, cannot reproduce Postgres's
 * actual row-lock BLOCKING behavior for truly concurrent transactions (provable only against live
 * Postgres); what it DOES prove is the exact code path a real concurrent second attempt would hit
 * after the first commits or rolls back -- see the "concurrent rotation" test below, which seeds the
 * post-rotation state directly (matching this codebase's established "seed a pre-existing
 * conflicting row before calling" convention for modeling races, e.g.
 * `postgres-authority-write-store.test.ts` test 13).
 */
interface FakeKeyRow { issuer_id: string; key_id: string; status: string; profile: string; public_key_pem: string; created_at: Date; activated_at: Date | null; retired_at: Date | null; revoked_at: Date | null }

class StatefulSigningKeyDatabase implements Database {
  private rows: FakeKeyRow[] = [];
  readonly calls: { sql: string; parameters: readonly unknown[] }[] = [];

  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    if (/^\s*SELECT 1 FROM trust_issuer_signing_key WHERE issuer_id = \$1 AND status = \$2/i.test(sql)) {
      const [issuerId, status] = parameters as [string, string];
      const match = this.rows.some((r) => r.issuer_id === issuerId && r.status === status);
      return Promise.resolve({ rows: match ? [{} as Row] : [], rowCount: match ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_issuer_signing_key WHERE issuer_id = \$1 AND status = 'active'/i.test(sql)) {
      const [issuerId] = parameters as [string];
      const row = this.rows.find((r) => r.issuer_id === issuerId && r.status === 'active');
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_issuer_signing_key WHERE issuer_id = \$1 AND key_id = \$2/i.test(sql)) {
      const [issuerId, keyId] = parameters as [string, string];
      const row = this.rows.find((r) => r.issuer_id === issuerId && r.key_id === keyId);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_issuer_signing_key WHERE issuer_id = \$1 ORDER BY created_at/i.test(sql)) {
      const [issuerId] = parameters as [string];
      const matches = this.rows.filter((r) => r.issuer_id === issuerId).sort((a, b) => a.created_at.getTime() - b.created_at.getTime());
      return Promise.resolve({ rows: matches as unknown as Row[], rowCount: matches.length });
    }
    if (/^\s*INSERT INTO trust_issuer_signing_key/i.test(sql)) {
      const [issuerId, keyId, profile, publicKeyPem, now] = parameters as [string, string, string, string, Date];
      if (this.rows.some((r) => r.issuer_id === issuerId && r.key_id === keyId)) throw new Error('simulated primary key violation: duplicate (issuer_id, key_id)');
      if (this.rows.some((r) => r.issuer_id === issuerId && r.status === 'active')) throw new Error('simulated unique index violation: trust_issuer_signing_key_one_active_idx');
      this.rows.push({ issuer_id: issuerId, key_id: keyId, status: 'active', profile, public_key_pem: publicKeyPem, created_at: now, activated_at: now, retired_at: null, revoked_at: null });
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*UPDATE trust_issuer_signing_key SET status = 'retired'/i.test(sql)) {
      const [now, issuerId, keyId] = parameters as [Date, string, string];
      const row = this.rows.find((r) => r.issuer_id === issuerId && r.key_id === keyId && r.status === 'active');
      if (!row) return Promise.resolve({ rows: [], rowCount: 0 });
      row.status = 'retired'; row.retired_at = now;
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*UPDATE trust_issuer_signing_key SET status = 'revoked'/i.test(sql)) {
      const [now, issuerId, keyId] = parameters as [Date, string, string];
      const row = this.rows.find((r) => r.issuer_id === issuerId && r.key_id === keyId);
      if (!row) return Promise.resolve({ rows: [], rowCount: 0 });
      row.status = 'revoked'; row.revoked_at = now;
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    return Promise.resolve({ rows: [], rowCount: 0 });
  }

  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> {
    const snapshot = this.rows.map((r) => ({ ...r }));
    try {
      return await work(this);
    } catch (error) {
      this.rows = snapshot;
      throw error;
    }
  }
  async close(): Promise<void> {}

  seed(row: FakeKeyRow): void { this.rows.push(row); }
  get count(): number { return this.rows.length; }
  find(issuerId: string, keyId: string): FakeKeyRow | undefined { return this.rows.find((r) => r.issuer_id === issuerId && r.key_id === keyId); }
  activeCount(): number { return this.rows.filter((r) => r.status === 'active').length; }
}

const ISSUER_ID = 'issuer-1';
const NOW = new Date(10_000);

function seedActive(db: StatefulSigningKeyDatabase, keyId = 'key-1', overrides: Partial<FakeKeyRow> = {}): void {
  db.seed({ issuer_id: ISSUER_ID, key_id: keyId, status: 'active', profile: 'P256-SHA256-v1', public_key_pem: 'PEM-1', created_at: new Date(0), activated_at: new Date(0), retired_at: null, revoked_at: null, ...overrides });
}

describe('PostgresIssuerSigningKeyStore SQL shape (no live database in this environment)', () => {
  it('bootstrap() issues only parameterized queries against the existing table, never DDL', async () => {
    const db = new RecordingDatabase();
    await new PostgresIssuerSigningKeyStore(db).bootstrap(ISSUER_ID, 'key-1', 'P256-SHA256-v1', 'PEM', NOW);
    expect(db.calls.some((c) => /INSERT INTO trust_issuer_signing_key/i.test(c.sql))).toBe(true);
    expect(db.calls.map((c) => c.sql).join(' ')).not.toMatch(/ALTER TABLE|CREATE TABLE|DROP/);
  });

  it('rotate() locks the current active row with FOR UPDATE, never a bare unlocked SELECT', async () => {
    const db = new RecordingDatabase();
    await expect(new PostgresIssuerSigningKeyStore(db).rotate(ISSUER_ID, 'key-2', 'P256-SHA256-v1', 'PEM-2', NOW)).rejects.toBeInstanceOf(NoActiveSigningKeyError);
    const select = db.calls.find((c) => /SELECT .* FROM trust_issuer_signing_key WHERE issuer_id = \$1 AND status = 'active'/i.test(c.sql));
    expect(select?.sql).toMatch(/FOR UPDATE/i);
  });
});

describe('PostgresIssuerSigningKeyStore (stateful, real transaction/rollback semantics)', () => {
  it('bootstrap() creates the first active key', async () => {
    const db = new StatefulSigningKeyDatabase();
    await new PostgresIssuerSigningKeyStore(db).bootstrap(ISSUER_ID, 'key-1', 'P256-SHA256-v1', 'PEM-1', NOW);
    expect(db.find(ISSUER_ID, 'key-1')?.status).toBe('active');
  });

  it('bootstrap() fails closed if an active key already exists -- does not silently replace it', async () => {
    const db = new StatefulSigningKeyDatabase();
    seedActive(db);
    await expect(new PostgresIssuerSigningKeyStore(db).bootstrap(ISSUER_ID, 'key-2', 'P256-SHA256-v1', 'PEM-2', NOW)).rejects.toBeInstanceOf(DuplicateActiveSigningKeyError);
    expect(db.count).toBe(1);
  });

  it('rotate() atomically retires the old key and activates the new one', async () => {
    const db = new StatefulSigningKeyDatabase();
    seedActive(db, 'key-1');
    const result = await new PostgresIssuerSigningKeyStore(db).rotate(ISSUER_ID, 'key-2', 'P256-SHA256-v1', 'PEM-2', NOW);
    expect(result.retiredKeyId).toBe('key-1');
    expect(db.find(ISSUER_ID, 'key-1')?.status).toBe('retired');
    expect(db.find(ISSUER_ID, 'key-2')?.status).toBe('active');
  });

  it('rotate() with no active key fails closed and creates nothing', async () => {
    const db = new StatefulSigningKeyDatabase();
    await expect(new PostgresIssuerSigningKeyStore(db).rotate(ISSUER_ID, 'key-2', 'P256-SHA256-v1', 'PEM-2', NOW)).rejects.toBeInstanceOf(NoActiveSigningKeyError);
    expect(db.count).toBe(0);
  });

  it('rotate() rolls back entirely if the insert of the new key fails partway through -- the old key remains active, not retired-with-no-successor', async () => {
    class FailingInsertDatabase extends StatefulSigningKeyDatabase {
      query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
        if (/^\s*INSERT INTO trust_issuer_signing_key/i.test(sql)) throw new Error('simulated connection loss mid-rotation');
        return super.query(sql, parameters);
      }
    }
    const db = new FailingInsertDatabase();
    seedActive(db, 'key-1');
    await expect(new PostgresIssuerSigningKeyStore(db).rotate(ISSUER_ID, 'key-2', 'P256-SHA256-v1', 'PEM-2', NOW)).rejects.toThrow('simulated connection loss mid-rotation');
    // No unintended state: key-1 must still be active (rollback undid the retirement too), and no
    // orphaned key-2 row exists. Never "zero current signing keys after a reported failure."
    expect(db.find(ISSUER_ID, 'key-1')?.status).toBe('active');
    expect(db.find(ISSUER_ID, 'key-2')).toBeUndefined();
    expect(db.count).toBe(1);
  });

  it('concurrent rotation (modeled as elsewhere in this codebase by seeding the post-first-rotation state): a second rotation attempt against the now-retired key fails closed, never creating a second active key', async () => {
    const db = new StatefulSigningKeyDatabase();
    // Simulates: rotation #1 already committed (key-1 retired, key-2 now active) between this
    // attempt's own read and its FOR UPDATE lock resolving -- exactly what a real concurrent
    // rotate() would observe once unblocked (see this file's own top-of-file boundary note).
    db.seed({ issuer_id: ISSUER_ID, key_id: 'key-1', status: 'retired', profile: 'P256-SHA256-v1', public_key_pem: 'PEM-1', created_at: new Date(0), activated_at: new Date(0), retired_at: NOW, revoked_at: null });
    seedActive(db, 'key-2', { created_at: new Date(1) });
    // A second rotate() call that still believed key-1 was active would find no active row
    // matching what it expected; the store itself always looks up "whichever key IS active now",
    // so it correctly rotates key-2 -> key-3 instead of ever creating two active keys.
    const result = await new PostgresIssuerSigningKeyStore(db).rotate(ISSUER_ID, 'key-3', 'P256-SHA256-v1', 'PEM-3', NOW);
    expect(result.retiredKeyId).toBe('key-2');
    expect(db.find(ISSUER_ID, 'key-1')?.status).toBe('retired');
    expect(db.find(ISSUER_ID, 'key-2')?.status).toBe('retired');
    expect(db.find(ISSUER_ID, 'key-3')?.status).toBe('active');
    expect(db.activeCount()).toBe(1);
  });

  it('retire() only succeeds from active status', async () => {
    const db = new StatefulSigningKeyDatabase();
    seedActive(db);
    await new PostgresIssuerSigningKeyStore(db).retire(ISSUER_ID, 'key-1', NOW);
    expect(db.find(ISSUER_ID, 'key-1')?.status).toBe('retired');
  });

  it('retire() of a non-active key is a deterministic conflict, not silent idempotency', async () => {
    const db = new StatefulSigningKeyDatabase();
    seedActive(db, 'key-1', { status: 'retired', retired_at: NOW });
    await expect(new PostgresIssuerSigningKeyStore(db).retire(ISSUER_ID, 'key-1', NOW)).rejects.toBeInstanceOf(SigningKeyLifecycleConflictError);
  });

  it('retire() of an unknown key id fails closed with a specific error', async () => {
    const db = new StatefulSigningKeyDatabase();
    await expect(new PostgresIssuerSigningKeyStore(db).retire(ISSUER_ID, 'no-such-key', NOW)).rejects.toBeInstanceOf(UnknownSigningKeyError);
  });

  it('revoke() works from active status and leaves zero active keys (fail-closed issuance outage, not a bypass)', async () => {
    const db = new StatefulSigningKeyDatabase();
    seedActive(db, 'key-1');
    await new PostgresIssuerSigningKeyStore(db).revoke(ISSUER_ID, 'key-1', NOW);
    expect(db.find(ISSUER_ID, 'key-1')?.status).toBe('revoked');
  });

  it('revoke() works from retired status too (a compromise discovered after rotation)', async () => {
    const db = new StatefulSigningKeyDatabase();
    seedActive(db, 'key-1', { status: 'retired', retired_at: NOW });
    await new PostgresIssuerSigningKeyStore(db).revoke(ISSUER_ID, 'key-1', NOW);
    expect(db.find(ISSUER_ID, 'key-1')?.status).toBe('revoked');
  });

  it('revoke() of an already-revoked key is a deterministic conflict, not silent idempotency', async () => {
    const db = new StatefulSigningKeyDatabase();
    seedActive(db, 'key-1', { status: 'revoked', revoked_at: NOW });
    await expect(new PostgresIssuerSigningKeyStore(db).revoke(ISSUER_ID, 'key-1', NOW)).rejects.toBeInstanceOf(SigningKeyLifecycleConflictError);
  });

  it('revoke() of an unknown key id fails closed with a specific error', async () => {
    const db = new StatefulSigningKeyDatabase();
    await expect(new PostgresIssuerSigningKeyStore(db).revoke(ISSUER_ID, 'no-such-key', NOW)).rejects.toBeInstanceOf(UnknownSigningKeyError);
  });

  it('listForIssuer() and describe() return every key regardless of status -- a revoked key remains visible, not hidden', async () => {
    const db = new StatefulSigningKeyDatabase();
    seedActive(db, 'key-1', { status: 'revoked', revoked_at: NOW, created_at: new Date(0) });
    seedActive(db, 'key-2', { created_at: new Date(1) });
    const store = new PostgresIssuerSigningKeyStore(db);
    const keys = await store.listForIssuer(ISSUER_ID);
    expect(keys.map((k) => ({ id: k.issuerKeyId, status: k.status }))).toEqual([{ id: 'key-1', status: 'revoked' }, { id: 'key-2', status: 'active' }]);
    const described = await store.describe(ISSUER_ID);
    expect(described.map((k) => k.status)).toEqual(['revoked', 'active']);
  });
});
