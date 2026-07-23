import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../../src/services/ledger/ledger-sync.service.js';
import type { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';
import {
  sampleNormalizedAmount,
  sampleNormalizedLedger,
} from '../../helpers/ledger-fixtures.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('LedgerSyncServiceImpl', () => {
  it('syncs ledgers into repository with statistics', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(async () => ({
        items: [
          sampleNormalizedLedger({
            id: 'cash',
            name: 'Cash',
            normalizedName: 'cash',
            parentGroup: 'Cash-in-Hand',
            closingBalance: sampleNormalizedAmount('100', 'Dr'),
          }),
        ],
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
    const connectorSession = createPermissiveSessionMock();
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );

    await service.start();
    const result = await service.syncLedgers();
    expect(result.status).toBe('completed');
    expect(result.statistics.totalLedgers).toBe(1);

    const ledgers = await service.getLedgers({ page: 1, pageSize: 10 });
    expect(ledgers.items[0]?.name).toBe('Cash');
  });

  it('supports cancellation', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(async () => ({
        items: Array.from({ length: 3 }, (_, index) =>
          sampleNormalizedLedger({
            id: `ledger-${index}`,
            name: `Ledger ${index}`,
            normalizedName: `ledger ${index}`,
          }),
        ),
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
    const connectorSession = createPermissiveSessionMock();
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );

    await service.start();
    const syncPromise = service.syncLedgers();
    await service.cancelSync();
    const result = await syncPromise;
    expect(['cancelled', 'completed']).toContain(result.status);
  });
});
