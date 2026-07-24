import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { nodeSqlite } from '../../src/storage/sqlite/node-sqlite.js';
import type { DatabaseSync } from '../../src/storage/sqlite/node-sqlite.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { MIGRATION_001, MIGRATION_002, MIGRATION_003, STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';

const tempDirs: string[] = [];
const openDbs: DatabaseSync[] = [];

afterEach(async () => {
  for (const db of openDbs.splice(0)) {
    db.close();
  }
  await new Promise((resolve) => setTimeout(resolve, 50));
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    } catch {
      // Windows may retain WAL handles briefly after close.
    }
  }
});

describe('schema migration v1 to v2', () => {
  it('preserves ledger rows and assigns resource_kind=ledgers to historical sync runs', () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-migrate-v2-'));
    tempDirs.push(basePath);
    const databasePath = path.join(basePath, 'budcom-ledger.db');

    const v1 = new SqliteDatabase({ databasePath });
    const db = v1.open();
    db.exec(MIGRATION_001);
    db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(
      1,
      '2026-01-01T00:00:00.000Z',
    );
    db.prepare(
      `INSERT INTO ledgers (
        company_id, ledger_id, name, normalized_name, status, balance_nature,
        content_fingerprint, is_deleted, synced_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)`,
    ).run('co-a', 'cash', 'Cash', 'cash', 'active', 'debit', 'fp', '2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.000Z');
    db.prepare(
      `INSERT INTO sync_runs (
        sync_run_id, company_id, sync_type, status, started_at, updated_at, completed_at,
        total_expected, processed, inserted, updated_count, skipped, failed, last_processed_id,
        retry_count, cancel_requested, failure_code, failure_summary, connector_version, schema_version
      ) VALUES (?, ?, 'full', 'completed', ?, ?, ?, 1, 1, 1, 0, 0, 0, NULL, 0, 0, NULL, NULL, '0.3.1', '1')`,
    ).run('run-1', 'co-a', '2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.000Z');
    v1.close();

    const v2 = new SqliteDatabase({ databasePath });
    v2.open();
    const migrated = v2.getDatabase();
    const version = migrated
      .prepare('SELECT MAX(version) AS version FROM schema_migrations')
      .get() as { version: number };
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);

    const ledgerCount = migrated.prepare('SELECT COUNT(*) AS c FROM ledgers').get() as { c: number };
    expect(ledgerCount.c).toBe(1);

    const run = migrated
      .prepare('SELECT resource_kind FROM sync_runs WHERE sync_run_id = ?')
      .get('run-1') as { resource_kind: string };
    expect(run.resource_kind).toBe('ledgers');

    const integrity = migrated.prepare('PRAGMA integrity_check').get() as { integrity_check: string };
    expect(integrity.integrity_check).toBe('ok');
    const fk = migrated.prepare('PRAGMA foreign_key_check').all();
    expect(fk).toEqual([]);
    v2.close();
  });

  it('upgrades partial stock_items tables missing identity columns', () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-migrate-v3-partial-'));
    tempDirs.push(basePath);
    const databasePath = path.join(basePath, 'budcom-ledger.db');

    const raw = new nodeSqlite.DatabaseSync(databasePath);
    openDbs.push(raw);
    raw.exec(MIGRATION_001);
    raw.exec(`
      CREATE TABLE stock_items (
        company_id TEXT NOT NULL,
        stock_item_id TEXT NOT NULL,
        name TEXT NOT NULL,
        normalized_name TEXT NOT NULL,
        parent_group TEXT,
        category TEXT,
        base_unit TEXT,
        opening_balance_json TEXT,
        closing_balance_json TEXT,
        hsn_code TEXT,
        gst_rate TEXT,
        metadata_json TEXT,
        content_fingerprint TEXT NOT NULL,
        is_deleted INTEGER NOT NULL DEFAULT 0,
        synced_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        PRIMARY KEY (company_id, stock_item_id)
      );
    `);
    raw.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(
      2,
      '2026-01-01T00:00:00.000Z',
    );
    raw.close();
    openDbs.pop();

    const upgraded = new SqliteDatabase({ databasePath });
    upgraded.open();
    const migrated = upgraded.getDatabase();
    const columns = (migrated.prepare('PRAGMA table_info(stock_items)').all() as Array<{ name: string }>).map(
      (row) => row.name,
    );
    expect(columns).toContain('guid');
    expect(columns).toContain('alter_id');
    expect(columns).toContain('data_quality');
    const version = migrated
      .prepare('SELECT MAX(version) AS version FROM schema_migrations')
      .get() as { version: number };
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);
    upgraded.close();
  });

  it('is idempotent when opened twice at current schema version', () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-migrate-idempotent-'));
    tempDirs.push(basePath);
    const databasePath = path.join(basePath, 'budcom-ledger.db');
    const first = new SqliteDatabase({ databasePath });
    first.open();
    first.close();
    const second = new SqliteDatabase({ databasePath });
    second.open();
    const version = second
      .getDatabase()
      .prepare('SELECT MAX(version) AS version FROM schema_migrations')
      .get() as { version: number };
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);
    second.close();
  });

  it('adds predecessor_sync_run_id on v3 to v4 migration with null defaults', () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-migrate-v4-'));
    tempDirs.push(basePath);
    const databasePath = path.join(basePath, 'budcom-ledger.db');

    const raw = new nodeSqlite.DatabaseSync(databasePath);
    openDbs.push(raw);
    raw.exec(MIGRATION_001);
    raw.exec(MIGRATION_002);
    raw.exec(MIGRATION_003);
    raw.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(
      3,
      '2026-01-01T00:00:00.000Z',
    );
    raw.prepare(
      `INSERT INTO sync_runs (
        sync_run_id, company_id, resource_kind, sync_type, status, started_at, updated_at, completed_at,
        total_expected, processed, inserted, updated_count, skipped, failed, last_processed_id,
        retry_count, cancel_requested, failure_code, failure_summary, connector_version, schema_version
      ) VALUES (?, ?, 'ledgers', 'full', 'interrupted', ?, ?, ?, 2, 2, 2, 0, 0, 0, 'bank', 0, 0, 'ABANDONED', 'x', '0.3.1', '3')`,
    ).run('historical-run', 'co-a', '2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.000Z');
    raw.close();
    openDbs.pop();

    const v4 = new SqliteDatabase({ databasePath });
    v4.open();
    const migrated = v4.getDatabase();
    const version = migrated
      .prepare('SELECT MAX(version) AS version FROM schema_migrations')
      .get() as { version: number };
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);

    const run = migrated
      .prepare('SELECT predecessor_sync_run_id, last_processed_id FROM sync_runs WHERE sync_run_id = ?')
      .get('historical-run') as { predecessor_sync_run_id: string | null; last_processed_id: string | null };
    expect(run.predecessor_sync_run_id).toBeNull();
    expect(run.last_processed_id).toBe('bank');
    v4.close();
  });
});
