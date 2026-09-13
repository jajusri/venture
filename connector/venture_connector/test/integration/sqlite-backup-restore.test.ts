import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { createLogger } from '../../src/infrastructure/logging/logger.js';
import type { LedgerDetails } from '../../src/erp/ledger/ledger-domain.js';
import { computeStockItemFingerprint } from '../../src/services/stock-item/stock-item-fingerprint.js';
import { STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { SqliteStockItemRepository } from '../../src/storage/sqlite/sqlite-stock-item-repository.js';
import { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import { sampleLedgerDetails } from '../helpers/ledger-fixtures.js';
import { sampleNormalizedAmount, sampleStockItemDetails } from '../helpers/stock-item-fixtures.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

function sampleLedger(id: string, name: string): LedgerDetails {
  return sampleLedgerDetails({ id, name, normalizedName: name.toLowerCase(), identitySource: 'name', metadata: { source: 'test' } });
}

describe('SQLite backup and restore drill', () => {
  it('creates an independent usable backup without source WAL/SHM files', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const bundle = storage.getBundle();
    const now = new Date().toISOString();

    await bundle.ledgerRepository.upsertMany('demo-co', [sampleLedger('cash', 'Cash')]);

    bundle.syncRunRepository.createRun({
      companyId: 'demo-co',
      resourceKind: 'ledgers',
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

    const sourceDbPath = path.join(basePath, 'venture-ledger.db');
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

  it('rejects backup target directories outside database path', async () => {
    const { storage } = await createTestSqliteStorage();
    const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-outside-backup-'));
    const result = storage.createBackup(outside);
    expect(result.ok).toBe(false);
    expect(result.message).toMatch(/within the approved root/i);
  });

  it('rejects backup targets that traverse a symlink beneath database path', async () => {
    if (process.platform === 'win32') {
      return;
    }
    const { storage, basePath } = await createTestSqliteStorage();
    const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-outside-backup-'));
    const linkPath = path.join(basePath, 'linked-backups');
    fs.symlinkSync(outside, linkPath, 'dir');
    const result = storage.createBackup(linkPath);
    expect(result.ok).toBe(false);
    expect(result.message).toMatch(/symbolic link or junction|approved root/i);
    await storage.stop();
  });

  it('never reports success when backup fails', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-backup-fail-'));
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
    expect(backup.backupPath).toMatch(/^venture-ledger-\d+\.db$/);
    await storage.stop();
  });
});

describe('SQLite backup and restore with stock items', () => {
  it('preserves schema-v2 ledgers, stock items, sync runs, and supports post-restore writes', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const bundle = storage.getBundle();
    const now = '2026-01-15T10:00:00.000Z';
    const stockRepo = bundle.stockItemRepository;

    await bundle.ledgerRepository.upsertMany('company-a', [
      sampleLedger('cash', 'Cash'),
      sampleLedger('bank', 'Bank'),
    ]);
    await bundle.ledgerRepository.upsertMany('company-b', [sampleLedger('petty', 'Petty Cash')]);

    const guidItem = sampleStockItemDetails({
      id: 'guid:aaa-bbb',
      guid: 'aaa-bbb',
      name: 'Guid Widget',
      normalizedName: 'guid widget',
      baseUnit: 'Nos',
      openingBalance: sampleNormalizedAmount('1234.5678'),
      alias: 'GW',
      partNumber: 'PN-1',
    });
    const alterItem = sampleStockItemDetails({
      id: 'alter:42',
      alterId: '42',
      name: 'Alter Part',
      normalizedName: 'alter part',
      baseUnit: 'Kg',
    });
    const incompleteItem = sampleStockItemDetails({
      id: 'name:incomplete-item',
      name: 'Incomplete Item',
      normalizedName: 'incomplete item',
      baseUnit: undefined,
      dataQuality: 'incomplete',
    });
    const unchangedItem = sampleStockItemDetails({
      id: 'name:stable',
      name: 'Stable Item',
      normalizedName: 'stable item',
      baseUnit: 'Nos',
    });
    const changedItem = sampleStockItemDetails({
      id: 'name:changed',
      name: 'Changed Item',
      normalizedName: 'changed item',
      baseUnit: 'Nos',
      category: 'Updated',
    });

    await stockRepo.upsertMany('company-a', [guidItem, alterItem, incompleteItem, unchangedItem, changedItem]);
    await stockRepo.upsertMany('company-b', [
      sampleStockItemDetails({ id: 'name:other', name: 'Other Co Item', normalizedName: 'other co item' }),
    ]);

    const guidFingerprint = computeStockItemFingerprint(guidItem);
    const ledgerRun = bundle.syncRunRepository.createRun({
      companyId: 'company-a',
      resourceKind: 'ledgers',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });
    bundle.syncRunRepository.updateRun({
      ...ledgerRun,
      status: 'completed',
      completedAt: now,
      updatedAt: now,
      processed: 2,
      inserted: 2,
    });

    const stockCompleted = bundle.syncRunRepository.createRun({
      companyId: 'company-a',
      resourceKind: 'stock-items',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });
    bundle.syncRunRepository.updateRun({
      ...stockCompleted,
      status: 'completed',
      completedAt: now,
      updatedAt: now,
      processed: 5,
      inserted: 5,
    });

    const stockFailed = bundle.syncRunRepository.createRun({
      companyId: 'company-a',
      resourceKind: 'stock-items',
      syncType: 'incremental',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });
    bundle.syncRunRepository.updateRun({
      ...stockFailed,
      status: 'failed',
      completedAt: now,
      updatedAt: now,
      failureCode: 'SERVICE_UNAVAILABLE',
      failureSummary: 'Simulated extraction failure',
    });

    const stockCancelled = bundle.syncRunRepository.createRun({
      companyId: 'company-a',
      resourceKind: 'stock-items',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });
    bundle.syncRunRepository.updateRun({
      ...stockCancelled,
      status: 'cancelled',
      completedAt: now,
      updatedAt: now,
      cancelRequested: true,
    });

    const db = bundle.database.getDatabase();
    db.prepare("INSERT OR REPLACE INTO storage_meta (key, value) VALUES ('migration_marker', 'v2-complete')").run();

    const backupDir = path.join(basePath, 'backups');
    const backup = storage.createBackup(backupDir);
    expect(backup.ok).toBe(true);

    await storage.stop();

    const restoreDir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-restore-'));
    const restoredDbPath = path.join(restoreDir, 'venture-ledger.db');
    fs.copyFileSync(path.join(backupDir, backup.backupPath!), restoredDbPath);

    const restored = new SqliteDatabase({ databasePath: restoredDbPath });
    restored.open();
    const rdb = restored.getDatabase();
    const restoredRepo = new SqliteStockItemRepository(restored);

    expect((rdb.prepare('PRAGMA integrity_check').get() as { integrity_check: string }).integrity_check).toBe('ok');
    expect((rdb.prepare('PRAGMA foreign_key_check').all() as unknown[])).toHaveLength(0);

    const schemaVersion = rdb
      .prepare('SELECT version FROM schema_migrations ORDER BY version DESC LIMIT 1')
      .get() as { version: number };
    expect(schemaVersion.version).toBe(STORAGE_SCHEMA_VERSION);

    expect(
      (rdb.prepare('SELECT COUNT(*) AS count FROM ledgers WHERE company_id = ?').get('company-a') as { count: number })
        .count,
    ).toBe(2);
    expect(
      (rdb.prepare('SELECT COUNT(*) AS count FROM stock_items WHERE company_id = ?').get('company-a') as { count: number })
        .count,
    ).toBe(5);
    expect(
      (rdb.prepare('SELECT COUNT(*) AS count FROM stock_items WHERE company_id = ?').get('company-b') as { count: number })
        .count,
    ).toBe(1);

    const restoredGuid = rdb
      .prepare('SELECT guid, alter_id, content_fingerprint, opening_balance_json, data_quality FROM stock_items WHERE stock_item_id = ? AND company_id = ?')
      .get('guid:aaa-bbb', 'company-a') as {
      guid: string;
      alter_id: string | null;
      content_fingerprint: string;
      opening_balance_json: string;
      data_quality: string;
    };
    expect(restoredGuid.guid).toBe('aaa-bbb');
    expect(restoredGuid.content_fingerprint).toBe(guidFingerprint);
    expect(restoredGuid.opening_balance_json).toContain('1234.5678');
    expect(restoredGuid.data_quality).toBe('complete');

    const incompleteRow = rdb
      .prepare('SELECT data_quality, base_unit FROM stock_items WHERE stock_item_id = ? AND company_id = ?')
      .get('name:incomplete-item', 'company-a') as { data_quality: string; base_unit: string | null };
    expect(incompleteRow.data_quality).toBe('incomplete');
    expect(incompleteRow.base_unit).toBeNull();

    const ledgerRuns = rdb
      .prepare("SELECT COUNT(*) AS count FROM sync_runs WHERE company_id = ? AND resource_kind = 'ledgers'")
      .get('company-a') as { count: number };
    const stockRuns = rdb
      .prepare("SELECT COUNT(*) AS count FROM sync_runs WHERE company_id = ? AND resource_kind = 'stock-items'")
      .get('company-a') as { count: number };
    expect(ledgerRuns.count).toBe(1);
    expect(stockRuns.count).toBe(3);

    const marker = rdb
      .prepare("SELECT value FROM storage_meta WHERE key = 'migration_marker'")
      .get() as { value: string };
    expect(marker.value).toBe('v2-complete');

    await restoredRepo.insert('company-a', sampleStockItemDetails({
      id: 'name:post-restore',
      name: 'Post Restore Item',
      normalizedName: 'post restore item',
      baseUnit: 'Nos',
      syncedAt: now,
    }));
    expect(await restoredRepo.countByCompany('company-a')).toBe(6);

    restored.close();
    fs.rmSync(restoreDir, { recursive: true, force: true });
  });
});
