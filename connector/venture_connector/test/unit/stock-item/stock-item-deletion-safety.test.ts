import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { StockItemSyncServiceImpl } from '../../../src/services/stock-item/stock-item-sync.service.js';
import type { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import { sampleNormalizedStockItem } from '../../helpers/stock-item-fixtures.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('stock item deletion safety', () => {
  it('does not remove persisted items when a later complete sync returns fewer items', async () => {
    let items = [
      sampleNormalizedStockItem({ id: 'name:item-a', name: 'Item A', normalizedName: 'item a' }),
      sampleNormalizedStockItem({ id: 'name:item-b', name: 'Item B', normalizedName: 'item b' }),
    ];
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgerContactDetails: vi.fn(),
      readLedgers: vi.fn(),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(async () => ({ items, durationMs: 1, rawByteLength: 100 })),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const { storage, basePath } = await createTestSqliteStorage();
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      { resolveName: vi.fn(async () => 'Demo') } as unknown as CompanyResolver,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const first = await service.syncStockItems();
    expect(first.deletionReconciliation).toBe('disabled');
    expect(first.statistics.totalStockItems).toBe(2);

    items = [items[0]!];
    const second = await service.syncStockItems();
    expect(second.extractionCompleteness).toBe('complete');
    expect(second.deletionReconciliation).toBe('disabled');
    expect(second.statistics.totalStockItems).toBe(2);
  });

  it('always reports deletion reconciliation disabled', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgerContactDetails: vi.fn(),
      readLedgers: vi.fn(),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(async () => ({
        items: Array.from({ length: 5 }, (_, i) =>
          sampleNormalizedStockItem({ id: `name:item-${i}`, name: `Item ${i}`, normalizedName: `item ${i}` }),
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
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      { resolveName: vi.fn(async () => 'Demo') } as unknown as CompanyResolver,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const syncPromise = service.syncStockItems();
    await service.cancelSync();
    const result = await syncPromise;
    expect(result.deletionReconciliation).toBe('disabled');
  });
});
