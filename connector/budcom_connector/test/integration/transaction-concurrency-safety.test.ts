import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';

import { createLogger } from '../../src/infrastructure/logging/logger.js';
import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../../src/services/stock-item/stock-item-sync.service.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { SyncRunRepository } from '../../src/storage/sqlite/sync-run-repository.js';
import { STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';
import { sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import { sampleNormalizedStockItem } from '../helpers/stock-item-fixtures.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

const tempDirs: string[] = [];

afterEach(async () => {
  await cleanupTestSqliteStorage();
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 50 });
    } catch {
      // Windows may retain WAL briefly.
    }
  }
});

function createDeferred<T = void>(): {
  promise: Promise<T>;
  resolve: (value: T | PromiseLike<T>) => void;
  reject: (reason?: unknown) => void;
  isSettled: () => boolean;
} {
  let settled = false;
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = (value) => {
      settled = true;
      res(value);
    };
    reject = (reason) => {
      settled = true;
      rej(reason);
    };
  });
  return { promise, resolve, reject, isSettled: () => settled };
}

function openTempDatabase(): { database: SqliteDatabase; databasePath: string } {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-tx-concurrency-'));
  tempDirs.push(basePath);
  const databasePath = path.join(basePath, 'budcom-ledger.db');
  const database = new SqliteDatabase({ databasePath });
  database.open();
  database.getDatabase().exec(`
    CREATE TABLE IF NOT EXISTS tx_probe (
      id TEXT PRIMARY KEY,
      label TEXT NOT NULL
    );
  `);
  return { database, databasePath };
}

function countProbe(database: SqliteDatabase, id?: string): number {
  const db = database.getDatabase();
  if (id) {
    const row = db.prepare('SELECT COUNT(*) AS count FROM tx_probe WHERE id = ?').get(id) as {
      count: number;
    };
    return Number(row.count);
  }
  const row = db.prepare('SELECT COUNT(*) AS count FROM tx_probe').get() as { count: number };
  return Number(row.count);
}

function countProbeOnPath(databasePath: string, id?: string): number {
  const reader = new SqliteDatabase({ databasePath });
  reader.open();
  try {
    return countProbe(reader, id);
  } finally {
    reader.close();
  }
}

function createReadPort(overrides: Partial<ErpReadPort> = {}): ErpReadPort {
  return {
    isReady: () => true,
    discoverCompanies: vi.fn(),
    getGroups: vi.fn(),
    getCompanyInfo: vi.fn(),
    readLedgerGroups: vi.fn(),
    readLedgers: vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 1 })),
    readStockGroups: vi.fn(),
    readStockCategories: vi.fn(),
    readStockItems: vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 1 })),
    readGodowns: vi.fn(),
    readCostCategories: vi.fn(),
    readCostCentres: vi.fn(),
    readVoucherTypes: vi.fn(),
    readGstRegistrations: vi.fn(),
    getReadDiagnostics: vi.fn(() => []),
    ...overrides,
  };
}

describe('SqliteDatabase transaction concurrency safety', () => {
  it('Test 1 — unrelated transactions do not join while A holds the connection slot', async () => {
    const { database } = openTempDatabase();
    const db = database.getDatabase();
    let beginCount = 0;
    const originalExec = db.exec.bind(db);
    db.exec = ((sql: string) => {
      if (String(sql).toUpperCase().includes('BEGIN')) {
        beginCount += 1;
      }
      return originalExec(sql);
    }) as typeof db.exec;

    const pauseA = createDeferred();
    const aReady = createDeferred();
    const bRunning = createDeferred();
    let bObservedHeldSlot = false;

    // node:sqlite cannot await between BEGIN and COMMIT; pause after mutex, before BEGIN.
    let hookArmed = true;
    database.setConcurrencyTestHook(async () => {
      if (!hookArmed) return;
      hookArmed = false;
      aReady.resolve();
      await pauseA.promise;
    });

    const txA = database.runInTransaction(() => {
      expect(database.isInTransaction()).toBe(true);
      db.prepare('INSERT INTO tx_probe (id, label) VALUES (?, ?)').run('a', 'ledger-batch');
    });

    await aReady.promise;

    const txB = database.runInTransaction(() => {
      bObservedHeldSlot = !pauseA.isSettled();
      bRunning.resolve();
      db.prepare('INSERT INTO tx_probe (id, label) VALUES (?, ?)').run('b', 'stock-batch');
    });

    await Promise.resolve();
    await new Promise((resolve) => setImmediate(resolve));

    expect(bRunning.isSettled()).toBe(false);
    expect(beginCount).toBe(0);

    pauseA.resolve();
    await Promise.all([txA, txB]);

    expect(bRunning.isSettled()).toBe(true);
    expect(bObservedHeldSlot).toBe(false);
    expect(beginCount).toBe(2);
    expect(countProbe(database, 'a')).toBe(1);
    expect(countProbe(database, 'b')).toBe(1);
    database.setConcurrencyTestHook(null);
    database.close();
  });

  it('Test 2 — rollback isolation across unrelated transactions', async () => {
    const { database, databasePath } = openTempDatabase();
    const db = database.getDatabase();
    const pauseA = createDeferred();
    const aReady = createDeferred();
    let hookArmed = true;
    database.setConcurrencyTestHook(async () => {
      if (!hookArmed) return;
      hookArmed = false;
      aReady.resolve();
      await pauseA.promise;
    });

    const txA = database.runInTransaction(() => {
      db.prepare('INSERT INTO tx_probe (id, label) VALUES (?, ?)').run('a-fail', 'ledger');
      throw new Error('injected failure in transaction A');
    });

    await aReady.promise;

    const txB = database.runInTransaction(() => {
      db.prepare('INSERT INTO tx_probe (id, label) VALUES (?, ?)').run('b-ok', 'stock');
    });

    await Promise.resolve();
    await new Promise((resolve) => setImmediate(resolve));
    expect(countProbeOnPath(databasePath, 'a-fail')).toBe(0);
    expect(countProbeOnPath(databasePath, 'b-ok')).toBe(0);

    pauseA.resolve();
    await expect(txA).rejects.toThrow(/injected failure in transaction A/);
    await txB;

    expect(countProbe(database, 'a-fail')).toBe(0);
    expect(countProbe(database, 'b-ok')).toBe(1);
    expect(countProbeOnPath(databasePath, 'b-ok')).toBe(1);
    database.setConcurrencyTestHook(null);
    database.close();
  });

  it('Test 3 — commit isolation: A cannot commit B’s data', async () => {
    const { database, databasePath } = openTempDatabase();
    const db = database.getDatabase();
    const pauseA = createDeferred();
    const aReady = createDeferred();
    const bRunning = createDeferred();
    let hookArmed = true;
    database.setConcurrencyTestHook(async () => {
      if (!hookArmed) return;
      hookArmed = false;
      aReady.resolve();
      await pauseA.promise;
    });

    const txA = database.runInTransaction(() => {
      db.prepare('INSERT INTO tx_probe (id, label) VALUES (?, ?)').run('a', 'ledger');
    });

    await aReady.promise;

    const txB = database.runInTransaction(() => {
      bRunning.resolve();
      db.prepare('INSERT INTO tx_probe (id, label) VALUES (?, ?)').run('b', 'stock');
    });

    await Promise.resolve();
    await new Promise((resolve) => setImmediate(resolve));
    expect(bRunning.isSettled()).toBe(false);
    expect(countProbeOnPath(databasePath, 'a')).toBe(0);
    expect(countProbeOnPath(databasePath, 'b')).toBe(0);

    pauseA.resolve();
    await txA;
    expect(countProbeOnPath(databasePath, 'a')).toBe(1);

    await txB;
    expect(countProbeOnPath(databasePath, 'b')).toBe(1);
    expect(countProbe(database)).toBe(2);
    database.setConcurrencyTestHook(null);
    database.close();
  });

  it('nested repository-style join within the same async context does not open a second BEGIN', async () => {
    const { database } = openTempDatabase();
    const db = database.getDatabase();
    let beginCount = 0;
    const originalExec = db.exec.bind(db);
    db.exec = ((sql: string) => {
      if (String(sql).toUpperCase().includes('BEGIN')) {
        beginCount += 1;
      }
      return originalExec(sql);
    }) as typeof db.exec;

    await database.runInTransaction(() => {
      expect(database.isInTransaction()).toBe(true);
      db.prepare('INSERT INTO tx_probe (id, label) VALUES (?, ?)').run('outer', 'owner');
      void database.runInTransaction(() => {
        expect(database.isInTransaction()).toBe(true);
        db.prepare('INSERT INTO tx_probe (id, label) VALUES (?, ?)').run('inner', 'nested');
      });
    });

    expect(beginCount).toBe(1);
    expect(countProbe(database)).toBe(2);
    database.close();
  });

  it('Test 4 — same (companyId, resource_kind) active-run exclusion remains correct', () => {
    const { database } = openTempDatabase();
    const syncRuns = new SyncRunRepository(database);
    syncRuns.createRun({
      companyId: 'company-a',
      resourceKind: 'ledgers',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });
    expect(() =>
      syncRuns.createRun({
        companyId: 'company-a',
        resourceKind: 'ledgers',
        syncType: 'full',
        connectorVersion: '0.3.1',
        schemaVersion: String(STORAGE_SCHEMA_VERSION),
      }),
    ).toThrow(/Active sync run already exists/);
    database.close();
  });

  it('Test 5 — ledger and stock-item sync remain logically independent under write serialization', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [sampleNormalizedLedger({ id: 'cash', name: 'Cash', normalizedName: 'cash' })],
        durationMs: 1,
        rawByteLength: 40,
      })),
      readStockItems: vi.fn(async () => ({
        items: [sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' })],
        durationMs: 1,
        rawByteLength: 40,
      })),
    });
    const logger = createLogger({ service: 'test', level: 'error' });
    const session = createPermissiveSessionMock();
    const resolver = { resolveName: async () => 'Demo' } as unknown as CompanyResolver;
    const ledgerService = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      resolver,
      session,
      logger,
      storage,
    );
    const stockService = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      resolver,
      session,
      logger,
      storage,
    );
    await ledgerService.start();
    await stockService.start();

    const [ledgerResult, stockResult] = await Promise.all([
      ledgerService.syncLedgers(),
      stockService.syncStockItems(),
    ]);

    expect(ledgerResult.status).toBe('completed');
    expect(stockResult.status).toBe('completed');
    expect(ledgerResult.statistics.totalLedgers).toBe(1);
    expect(stockResult.statistics.totalStockItems).toBe(1);

    const ledgerRuns = storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers');
    const stockRuns = storage.getBundle().syncRunRepository.listRuns('estimation', 'stock-items');
    expect(ledgerRuns[0]?.status).toBe('completed');
    expect(stockRuns[0]?.status).toBe('completed');
    expect(ledgerRuns[0]?.syncRunId).not.toBe(stockRuns[0]?.syncRunId);
  });
});
