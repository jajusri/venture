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
    expect((await service.getSyncProgress()).totalExpected).toBeNull();
    const result = await service.syncStockItems();
    expect(result.status).toBe('completed');
    expect(result.statistics.totalStockItems).toBe(1);
    expect(result.progress.totalExpected).toBe(1);
    expect((await service.getSyncProgress()).totalExpected).toBe(1);

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

  it('a sync in flight for one company never blocks or is visible to a different company (TD-036)', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    let releaseCompanyA: (() => void) | undefined;
    const companyAGate = new Promise<void>((resolve) => {
      releaseCompanyA = resolve;
    });
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(async (companyName: string) => {
        if (companyName === 'company-a') {
          await companyAGate;
        }
        return {
          items: [
            sampleNormalizedStockItem({
              id: `${companyName}-item`,
              name: `${companyName} Item`,
              normalizedName: `${companyName} item`,
            }),
          ],
          durationMs: 1,
          rawByteLength: 100,
        };
      }),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const companyResolver = {
      resolveName: vi.fn(async (companyId: string) => companyId),
    } as unknown as CompanyResolver;
    let currentCompanyId = 'company-a';
    const session = createPermissiveSessionMock({
      getSession: () => ({
        session: {
          connectorVersion: '0.3.1',
          erpType: 'tally' as never,
          connectionStatus: 'connected',
          selectedCompany: { id: currentCompanyId, name: currentCompanyId },
          selectedAt: new Date().toISOString(),
          lastValidatedAt: new Date().toISOString(),
        } as never,
        contractVersion: '1',
      }),
      validateForOperation: async () => ({
        status: 'SUCCESS',
        session: undefined as never,
        companyId: currentCompanyId,
        companyName: currentCompanyId,
      }),
    });
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      session,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();

    const companyASyncPromise = service.syncStockItems();
    await vi.waitFor(async () => {
      expect((await service.getSyncProgress()).status).toBe('running');
    });

    // Before TD-036's fix, this would be rejected with SYNC_CONFLICT purely because company A's
    // sync happened to still be in flight in the same process — even though nothing in the
    // database actually conflicts (A and B are different companies).
    currentCompanyId = 'company-b';
    const companyBResult = await service.syncStockItems();
    expect(companyBResult.status).toBe('completed');
    expect(companyBResult.statistics.totalStockItems).toBe(1);

    const companyBProgress = await service.getSyncProgress();
    expect(companyBProgress.companyId).toBe('company-b');
    expect(companyBProgress.status).toBe('completed');

    releaseCompanyA?.();
    const companyAResult = await companyASyncPromise;
    expect(companyAResult.status).toBe('completed');

    currentCompanyId = 'company-a';
    const companyAProgress = await service.getSyncProgress();
    expect(companyAProgress.companyId).toBe('company-a');
    expect(companyAProgress.status).toBe('completed');

    const itemsA = await service.getStockItems({ page: 1, pageSize: 10 });
    expect(itemsA.items.map((item) => item.name)).toEqual(['company-a Item']);
    currentCompanyId = 'company-b';
    const itemsB = await service.getStockItems({ page: 1, pageSize: 10 });
    expect(itemsB.items.map((item) => item.name)).toEqual(['company-b Item']);
  });
});
