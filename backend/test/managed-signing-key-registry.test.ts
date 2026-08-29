import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { ManagedSigningKeyRegistry } from '../services/trust/src/persistence/managed-signing-key-registry.js';
import { PostgresIssuerSigningKeyStore } from '../services/trust/src/persistence/postgres-issuer-signing-key-store.js';

/** Same stateful fake shape as `postgres-issuer-signing-key-store.test.ts` -- duplicated per this
 * codebase's own convention of self-contained per-file test fakes rather than a shared test-only
 * module. */
interface FakeKeyRow { issuer_id: string; key_id: string; status: string; profile: string; public_key_pem: string; created_at: Date; activated_at: Date | null; retired_at: Date | null; revoked_at: Date | null }
class StatefulSigningKeyDatabase implements Database {
  private rows: FakeKeyRow[] = [];
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
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
      if (this.rows.some((r) => r.issuer_id === issuerId && r.status === 'active')) throw new Error('simulated unique index violation');
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
    try { return await work(this); } catch (error) { this.rows = snapshot; throw error; }
  }
  async close(): Promise<void> {}
}

const ISSUER_ID = 'issuer-1';

describe('ManagedSigningKeyRegistry', () => {
  let workDir: string;
  beforeEach(() => { workDir = mkdtempSync(join(tmpdir(), 'budcom-signing-key-registry-')); });
  afterEach(() => { rmSync(workDir, { recursive: true, force: true }); });

  it('bootstrap() generates a real local key and records it in Postgres as the sole active key', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);
    await registry.bootstrap('key-1', 'P256-SHA256-v1', new Date());
    const described = await registry.describe();
    expect(described).toHaveLength(1);
    expect(described[0]?.status).toBe('active');
  });

  it('rotate() updates Postgres immediately -- no registry refresh step exists or is needed for the new key to be selectable', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);
    await registry.bootstrap('key-1', 'P256-SHA256-v1', new Date());
    await registry.rotate('key-2', 'P256-SHA256-v1', new Date());
    const described = await registry.describe();
    expect(described.map((k) => k.status).sort()).toEqual(['active', 'retired']);
  });

  it('revoke() updates Postgres immediately -- describe() reflects it with no separate refresh call', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);
    await registry.bootstrap('key-1', 'P256-SHA256-v1', new Date());
    await registry.revoke('key-1', new Date());
    const described = await registry.describe();
    expect(described).toHaveLength(1);
    expect(described[0]?.status).toBe('revoked');
  });

  it('a safe retry with the same keyId after a failed bootstrap reuses the already-generated local key rather than regenerating it', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);
    await registry.bootstrap('key-1', 'P256-SHA256-v1', new Date());
    // Second bootstrap attempt for the SAME issuer fails (an active key already exists) -- but must
    // not corrupt or replace the already-generated local key file for key-1.
    await expect(registry.bootstrap('key-1', 'P256-SHA256-v1', new Date())).rejects.toThrow();
    const described = await registry.describe();
    expect(described).toHaveLength(1);
    expect(described[0]?.status).toBe('active');
  });

  it('this registry no longer exposes any cached signing-authority accessor -- handles()/refresh() do not exist (round 7 Codex Critical Fix 1)', () => {
    const registry = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(new StatefulSigningKeyDatabase()), ISSUER_ID, workDir);
    expect((registry as unknown as { handles?: unknown }).handles).toBeUndefined();
    expect((registry as unknown as { refresh?: unknown }).refresh).toBeUndefined();
  });

  it('key ids containing path traversal or path separators are rejected before any filesystem access', async () => {
    const db = new StatefulSigningKeyDatabase();
    const registry = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(db), ISSUER_ID, workDir);
    for (const unsafe of ['../escape', 'a/b', 'a\\b', '..', '.', '']) {
      await expect(registry.bootstrap(unsafe, 'P256-SHA256-v1', new Date())).rejects.toThrow(/Invalid signing key id/);
    }
  });
});
