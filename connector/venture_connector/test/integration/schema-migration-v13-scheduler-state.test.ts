import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import type { DatabaseSync } from '../../src/storage/sqlite/node-sqlite.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { MIGRATION_001, STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../helpers/sqlite-test-storage.js';

const tempDirs: string[] = [];
const openDbs: DatabaseSync[] = [];

afterEach(async () => {
  for (const db of openDbs.splice(0)) {
    db.close();
  }
  await cleanupTestSqliteStorage();
  await new Promise((resolve) => setTimeout(resolve, 50));
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    } catch {
      // Windows may retain WAL handles briefly after close.
    }
  }
});

describe('schema migration to v13 (adaptive-sync scheduler state)', () => {
  it('migrates an existing v1 database forward to v13, creating the scheduler_state table', () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-migrate-v13-'));
    tempDirs.push(basePath);
    const databasePath = path.join(basePath, 'venture-ledger.db');

    const v1 = new SqliteDatabase({ databasePath });
    const db = v1.open();
    db.exec(MIGRATION_001);
    db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(
      1,
      '2026-01-01T00:00:00.000Z',
    );
    v1.close();

    const v13 = new SqliteDatabase({ databasePath });
    v13.open();
    const migrated = v13.getDatabase();
    openDbs.push(migrated);

    const version = migrated
      .prepare('SELECT MAX(version) AS version FROM schema_migrations')
      .get() as { version: number };
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);
    expect(STORAGE_SCHEMA_VERSION).toBe(13);

    const tables = migrated
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'scheduler_state'")
      .all() as Array<{ name: string }>;
    expect(tables.map((t) => t.name)).toEqual(['scheduler_state']);
  });

  it('creates a clean database directly at schema version 13 with the scheduler_state table', async () => {
    const { storage } = await createTestSqliteStorage();
    const db = storage.getBundle().database.getDatabase();

    const version = db.prepare('SELECT MAX(version) AS version FROM schema_migrations').get() as {
      version: number;
    };
    expect(version.version).toBe(13);

    const columns = db.prepare('PRAGMA table_info(scheduler_state)').all() as Array<{ name: string }>;
    expect(columns.map((c) => c.name).sort()).toEqual(
      [
        'company_id',
        'resource_kind',
        'stage',
        'active_window_expires_at',
        'next_check_due_at',
        'updated_at',
      ].sort(),
    );
  });

  it('is idempotent when a v13 database is reopened', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-migrate-v13-idempotent-'));
    tempDirs.push(basePath);
    const databasePath = path.join(basePath, 'venture-ledger.db');

    const first = new SqliteDatabase({ databasePath });
    first.open();
    first.close();

    const second = new SqliteDatabase({ databasePath });
    const db = second.open();
    openDbs.push(db);

    const version = db.prepare('SELECT MAX(version) AS version FROM schema_migrations').get() as {
      version: number;
    };
    expect(version.version).toBe(13);
  });

  it('enforces one row per (company_id, resource_kind)', async () => {
    const { storage } = await createTestSqliteStorage();
    const db = storage.getBundle().database.getDatabase();

    db.prepare(
      `INSERT INTO scheduler_state (company_id, resource_kind, stage, active_window_expires_at, next_check_due_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
    ).run('estimation', 'ledgers', 'active_window', '2026-01-01T00:15:00.000Z', '2026-01-01T00:05:00.000Z', '2026-01-01T00:00:00.000Z');

    expect(() =>
      db
        .prepare(
          `INSERT INTO scheduler_state (company_id, resource_kind, stage, active_window_expires_at, next_check_due_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?)`,
        )
        .run('estimation', 'ledgers', 'backoff_15', null, '2026-01-01T00:20:00.000Z', '2026-01-01T00:00:00.000Z'),
    ).toThrow();
  });
});
