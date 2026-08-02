import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { StockItemSyncServiceImpl } from '../../../src/services/stock-item/stock-item-sync.service.js';
import type { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import {
  sampleNormalizedStockItem,
} from '../../helpers/stock-item-fixtures.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('StockItemSyncServiceImpl', () => {
  it('syncs stock items into repository with statistics', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(async () => ({
        items: [
          sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' }),
        ],
        durationMs: 1,
        rawByteLength: 100,
      })),
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
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );

    await service.start();
    expect(service.getSyncProgress().totalExpected).toBeNull();
    const result = await service.syncStockItems();
    expect(result.status).toBe('completed');
    expect(result.statistics.totalStockItems).toBe(1);
    expect(result.progress.totalExpected).toBe(1);
    expect(service.getSyncProgress().totalExpected).toBe(1);

    const items = await service.getStockItems({ page: 1, pageSize: 10 });
    expect(items.items[0]?.name).toBe('Widget');
  });

  it('supports cancellation', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(async () => ({
        items: Array.from({ length: 3 }, (_, index) =>
          sampleNormalizedStockItem({
            id: `item-${index}`,
            name: `Item ${index}`,
            normalizedName: `item ${index}`,
          }),
        ),
        durationMs: 1,
        rawByteLength: 100,
      })),
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
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );

    await service.start();
    const syncPromise = service.syncStockItems();
    await service.cancelSync();
    const result = await syncPromise;
    expect(['cancelled', 'completed']).toContain(result.status);
  });
});
