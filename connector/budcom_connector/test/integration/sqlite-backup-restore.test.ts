import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { createLogger } from '../../src/infrastructure/logging/logger.js';
import type { LedgerDetails } from '../../src/erp/ledger/ledger-domain.js';
import { STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

function sampleLedger(id: string, name: string): LedgerDetails {
  return {
    id,
    name,
    normalizedName: name.toLowerCase(),
    status: 'active',
    balanceNature: 'debit',
    isDeleted: false,
    syncedAt: '2026-01-01T00:00:00.000Z',
    metadata: { source: 'test' },
  };
}

describe('SQLite backup and restore drill', () => {
  it('creates an independent usable backup without source WAL/SHM files', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const bundle = storage.getBundle();
    const now = new Date().toISOString();

    await bundle.ledgerRepository.upsertMany('demo-co', [sampleLedger('cash', 'Cash')]);

    bundle.syncRunRepository.createRun({
      companyId: 'demo-co',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });

    const db = bundle.database.getDatabase();
    db.prepare("INSERT OR REPLACE INTO storage_meta (key, value) VALUES ('test_marker', 'restore-drill')").run();
    db.prepare('INSERT INTO ledgers (company_id, ledger_id, name, normalized_name, status, balance_nature, content_fingerprint, is_deleted, synced_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)').run(
      'demo-co',
      'bank',
      'Bank',
      'bank',
      'active',
      'debit',
      'fp-bank',
      now,
      now,
    );

    const backupDir = path.join(basePath, 'backups');
    const backup = storage.createBackup(backupDir);
    expect(backup.ok).toBe(true);
    expect(backup.backupPath).toBeTruthy();
    expect(backup.backupPath).not.toMatch(/[:\\]/);
    expect(path.isAbsolute(backup.backupPath ?? '')).toBe(false);

    const sourceDbPath = path.join(basePath, 'budcom-ledger.db');
    expect(fs.existsSync(`${sourceDbPath}-wal`)).toBe(true);

    await storage.stop();
    expect(fs.existsSync(sourceDbPath)).toBe(true);

    const backupFullPath = path.join(backupDir, backup.backupPath!);
    expect(fs.existsSync(backupFullPath)).toBe(true);
    expect(fs.existsSync(`${backupFullPath}-wal`)).toBe(false);
    expect(fs.existsSync(`${backupFullPath}-shm`)).toBe(false);

    const restored = new SqliteDatabase({ databasePath: backupFullPath, readonly: true });
    restored.open();
    const rdb = restored.getDatabase();

    const integrity = rdb.prepare('PRAGMA integrity_check').get() as { integrity_check: string };
    expect(integrity.integrity_check).toBe('ok');

    const fkCheck = rdb.prepare('PRAGMA foreign_key_check').all() as unknown[];
    expect(fkCheck).toHaveLength(0);

    const schemaVersion = rdb
      .prepare('SELECT version FROM schema_migrations ORDER BY version DESC LIMIT 1')
      .get() as { version: number };
    expect(schemaVersion.version).toBe(STORAGE_SCHEMA_VERSION);

    const ledgerCount = rdb
      .prepare('SELECT COUNT(*) AS count FROM ledgers WHERE company_id = ?')
      .get('demo-co') as { count: number };
    expect(ledgerCount.count).toBe(2);

    const cash = rdb
      .prepare('SELECT name, metadata_json FROM ledgers WHERE company_id = ? AND ledger_id = ?')
      .get('demo-co', 'cash') as { name: string; metadata_json: string };
    expect(cash.name).toBe('Cash');
    expect(cash.metadata_json).toContain('test');

    const syncRuns = rdb
      .prepare('SELECT COUNT(*) AS count FROM sync_runs WHERE company_id = ?')
      .get('demo-co') as { count: number };
    expect(syncRuns.count).toBe(1);

    const marker = rdb
      .prepare("SELECT value FROM storage_meta WHERE key = 'test_marker'")
      .get() as { value: string };
    expect(marker.value).toBe('restore-drill');

    restored.close();
  });

  it('never reports success when backup fails', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-backup-fail-'));
    const config = createTestConnectorConfig(basePath);
    const storage = new SqliteStorageService(
      config,
      createLogger({ service: 'test', level: 'error' }),
    );
    const result = storage.createBackup(path.join(basePath, 'backups'));
    expect(result.ok).toBe(false);
    expect(result.backupPath).toBeNull();
    fs.rmSync(basePath, { recursive: true, force: true });
  });
});

describe('SyncRunRepository direct backup path safety', () => {
  it('returns filename only from backup API surface', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const backup = storage.createBackup(path.join(basePath, 'backups'));
    expect(backup.ok).toBe(true);
    expect(backup.backupPath).toMatch(/^budcom-ledger-\d+\.db$/);
    await storage.stop();
  });
});
