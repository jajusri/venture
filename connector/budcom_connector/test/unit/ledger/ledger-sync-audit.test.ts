import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../../src/services/ledger/ledger-sync.service.js';
import { SqliteDatabase } from '../../../src/storage/sqlite/sqlite-database.js';
import { SyncRunRepository } from '../../../src/storage/sqlite/sync-run-repository.js';
import type { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('Ledger sync audit regressions', () => {
  it('does not corrupt completed sync run when cancelSync is called afterward', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(async () => ({
        items: [{ id: 'cash', name: 'Cash', normalizedName: 'cash' }],
        durationMs: 1,
        rawByteLength: 100,
      })),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      { resolveName: vi.fn(async () => 'Demo') } as unknown as CompanyResolver,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const result = await service.syncLedgers();
    expect(result.status).toBe('completed');

    await service.cancelSync();
    const runs = await service.listSyncRuns(1);
    expect(runs[0]?.status).toBe('completed');
    expect(runs[0]?.cancelRequested).toBe(false);
  });

  it('scopes getSyncRun to selected company', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const db = new SqliteDatabase({ databasePath: path.join(basePath, 'budcom-ledger.db') });
    db.open();
    const syncRuns = new SyncRunRepository(db);
    const otherRun = syncRuns.createRun({
      companyId: 'other-company',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: '1',
    });
    syncRuns.updateRun({ ...otherRun, status: 'completed', completedAt: new Date().toISOString() });

    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 0 })),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      { resolveName: vi.fn(async () => 'Demo') } as unknown as CompanyResolver,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
      undefined,
      syncRuns,
    );
    await service.start();
    const scoped = await service.getSyncRun(otherRun.syncRunId);
    expect(scoped).toBeNull();
  });
});

describe('SqliteStorageService audit regressions', () => {
  it('does not mark JSON migration complete when legacy directory is absent', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-audit-migrate-'));
    const { storage } = await createTestSqliteStorage();
    await storage.stop();
    const config = createTestConnectorConfig(basePath);
    const { SqliteStorageService } = await import('../../../src/storage/sqlite/storage-service.js');
    const svc = new SqliteStorageService(config, createLogger({ service: 'test', level: 'error' }));
    await svc.start();
    const db = svc.getBundle().database.getDatabase();
    const completed = db
      .prepare("SELECT value FROM storage_meta WHERE key = 'json_migration_completed'")
      .get() as { value: string } | undefined;
    expect(completed).toBeUndefined();
    await svc.stop();
    fs.rmSync(basePath, { recursive: true, force: true });
  });
});
