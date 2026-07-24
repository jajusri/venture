import { afterEach, describe, expect, it, vi } from 'vitest';

import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import { mapNormalizedStockItemToDomain } from '../../src/erp/stock-item/stock-item-mapper.js';
import { AppError } from '../../src/infrastructure/errors/app-error.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../../src/services/stock-item/stock-item-sync.service.js';
import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import { sampleNameFallbackLedger, sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import { sampleNormalizedStockItem } from '../helpers/stock-item-fixtures.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

const COMPANY = 'estimation';

afterEach(async () => {
  await cleanupTestSqliteStorage();
  vi.restoreAllMocks();
});

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

function companyResolver(): CompanyResolver {
  return { resolveName: vi.fn(async () => 'Demo Company') } as unknown as CompanyResolver;
}

describe('normal inbound sync empty and partial behavior', () => {
  it('ledger empty response with populated GUID-first cache does not delete rows', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ id: 'guid:existing-1', name: 'Existing 1' })),
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ id: 'guid:existing-2', name: 'Existing 2' })),
    ]);
    await repo.markLedgerIdentityCurrent(COMPANY);

    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({ readLedgers: vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 1 })) }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const result = await service.syncLedgers();

    expect(result.status).toBe('completed');
    expect(await repo.countByCompany(COMPANY)).toBe(2);
  });

  it('ledger partial response with populated cache upserts without deleting existing rows', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ id: 'guid:keep-me', name: 'Keep Me' })),
    ]);
    await repo.markLedgerIdentityCurrent(COMPANY);

    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readLedgers: vi.fn(async () => ({
          items: [sampleNameFallbackLedger({ name: 'Partial New' })],
          durationMs: 1,
          rawByteLength: 20,
        })),
      }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const result = await service.syncLedgers();

    expect(result.status).toBe('completed');
    expect(await repo.findById(COMPANY, 'guid:keep-me')).not.toBeNull();
    expect(await repo.countByCompany(COMPANY)).toBeGreaterThanOrEqual(2);
  });

  it('ledger invalid shallow response does not mutate populated cache', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ id: 'guid:stable', name: 'Stable' })),
    ]);
    await repo.markLedgerIdentityCurrent(COMPANY);

    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readLedgers: vi.fn(async () => ({
          items: [
            sampleNormalizedLedger({
              guid: undefined,
              id: 'name:orphan',
              parentGroup: undefined,
              identitySource: 'name',
            }),
          ],
          durationMs: 1,
          rawByteLength: 20,
        })),
      }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toBeInstanceOf(AppError);
    expect(await repo.findById(COMPANY, 'guid:stable')).not.toBeNull();
    expect(await repo.countByCompany(COMPANY)).toBe(1);
  });

  it('stock empty response with populated cache does not delete rows', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().stockItemRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedStockItemToDomain(sampleNormalizedStockItem({ id: 'guid:stock-1', name: 'Stock 1' })),
    ]);

    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({ readStockItems: vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 1 })) }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const result = await service.syncStockItems();

    expect(result.status).toBe('completed');
    expect(await repo.countByCompany(COMPANY)).toBe(1);
  });

  it('stock incomplete records sync without deleting existing rows', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().stockItemRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedStockItemToDomain(sampleNormalizedStockItem({ id: 'guid:keep-stock', name: 'Keep Stock' })),
    ]);

    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readStockItems: vi.fn(async () => ({
          items: [sampleNormalizedStockItem({ id: 'name:new-item', name: 'New Item', baseUnit: undefined, guid: undefined })],
          durationMs: 1,
          rawByteLength: 20,
        })),
      }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const result = await service.syncStockItems();

    expect(result.status).toBe('completed');
    expect(await repo.findById(COMPANY, 'guid:keep-stock')).not.toBeNull();
    expect(await repo.countByCompany(COMPANY)).toBeGreaterThanOrEqual(2);
  });
});
