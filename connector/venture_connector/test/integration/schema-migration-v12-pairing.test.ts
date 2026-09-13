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

describe('schema migration to v12 (secure local pairing)', () => {
  it('migrates an existing v1 database forward to v12, creating the pairing tables', () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-migrate-v12-'));
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

    const v12 = new SqliteDatabase({ databasePath });
    v12.open();
    const migrated = v12.getDatabase();
    openDbs.push(migrated);

    const version = migrated
      .prepare('SELECT MAX(version) AS version FROM schema_migrations')
      .get() as { version: number };
    // A v1 database migrates all the way to the current latest version (13, once the adaptive-sync
    // scheduler-state migration was added) -- this test's own concern is only that the v12 pairing
    // tables were created somewhere along that path, not that 12 is the final version.
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);

    const tables = migrated
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('pairing_sessions', 'pairing_device_credentials')")
      .all() as Array<{ name: string }>;
    expect(tables.map((t) => t.name).sort()).toEqual(['pairing_device_credentials', 'pairing_sessions']);
  });

  it('creates a clean database directly at schema version 12 with both pairing tables', async () => {
    const { storage } = await createTestSqliteStorage();
    const db = storage.getBundle().database.getDatabase();

    const version = db.prepare('SELECT MAX(version) AS version FROM schema_migrations').get() as {
      version: number;
    };
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);

    const columns = db.prepare('PRAGMA table_info(pairing_sessions)').all() as Array<{ name: string }>;
    const columnNames = columns.map((c) => c.name).sort();
    expect(columnNames).toEqual(
      [
        'cancelled_at',
        'connector_id',
        'connector_name',
        'host',
        'port',
        'schema_version',
        'created_at',
        'expires_at',
        'failed_attempts',
        'pairing_session_id',
        'redeemed_at',
        'secret_hash',
        'short_code_hash',
      ].sort(),
    );

    const credentialColumns = db.prepare('PRAGMA table_info(pairing_device_credentials)').all() as Array<{
      name: string;
    }>;
    expect(credentialColumns.map((c) => c.name).sort()).toEqual(
      [
        'credential_id',
        'pairing_session_id',
        'connector_id',
        'device_id',
        'token_hash',
        'device_label',
        'created_at',
        'last_used_at',
        'revoked_at',
      ].sort(),
    );
  });

  it('is idempotent when a v12 database is reopened', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-migrate-v12-idempotent-'));
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
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);
  });
});
