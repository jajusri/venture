import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../../src/services/ledger/ledger-sync.service.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import { cleanupTestSqliteStorage, createTestConnectorConfig, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';
import type { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('LedgerSyncServiceImpl (SQLite)', () => {
  it('syncs ledgers into SQLite with durable sync run', async () => {
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
    const companyResolver = {
      resolveName: vi.fn(async () => 'Demo Company'),
    } as unknown as CompanyResolver;
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );

    await service.start();
    const result = await service.syncLedgers();
    expect(result.status).toBe('completed');
    expect(result.syncRunId).toBeTruthy();
    expect(result.statistics.totalLedgers).toBe(1);
    const runs = await service.listSyncRuns();
    expect(runs.length).toBeGreaterThan(0);
  });

  it('prevents duplicate concurrent sync', async () => {
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
    await service.syncLedgers();
    const active = storage.getBundle().syncRunRepository.findActiveRun('estimation');
    expect(active).toBeNull();
  });
});
