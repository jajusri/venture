import { afterEach, describe, expect, it, vi } from 'vitest';

import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import { mapNormalizedStockItemToDomain } from '../../src/erp/stock-item/stock-item-mapper.js';
import { AppError, ErrorCodes } from '../../src/infrastructure/errors/app-error.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../../src/services/stock-item/stock-item-sync.service.js';
import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import { sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
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

describe('master-data contract sync rejection', () => {
  it('ledger explicit contract error does not mutate populated cache', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ id: 'guid:stable', name: 'Stable' })),
    ]);
    await repo.markLedgerIdentityCurrent(COMPANY);

    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readLedgers: vi.fn(async () => {
          throw new AppError(
            ErrorCodes.VALIDATION_ERROR,
            'Tally returned an explicit error response for ledger extraction',
            502,
            { contractStatus: 'TALLY_ERROR', reasonCode: 'tally_line_error' },
          );
        }),
      }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toBeInstanceOf(AppError);
    expect(await repo.countByCompany(COMPANY)).toBe(1);
  });

  it('ledger missing collection contract error does not mutate populated cache', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ id: 'guid:stable', name: 'Stable' })),
    ]);
    await repo.markLedgerIdentityCurrent(COMPANY);

    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readLedgers: vi.fn(async () => {
          throw new AppError(
            ErrorCodes.VALIDATION_ERROR,
            'Ledger response is missing the requested DATA/COLLECTION region',
            502,
            { contractStatus: 'COLLECTION_MISSING', reasonCode: 'collection_missing' },
          );
        }),
      }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toBeInstanceOf(AppError);
    expect(await repo.countByCompany(COMPANY)).toBe(1);
  });

  it('ledger valid empty contract result completes without deleting rows', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ id: 'guid:existing', name: 'Existing' })),
    ]);
    await repo.markLedgerIdentityCurrent(COMPANY);

    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readLedgers: vi.fn(async () => ({
          items: [],
          durationMs: 1,
          rawByteLength: 1,
          contract: {
            contractVersion: '1',
            status: 'EMPTY',
            reasonCode: 'collection_empty',
            blocking: false,
            dataQualityStatus: 'EMPTY' as const,
          },
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
    expect(await repo.countByCompany(COMPANY)).toBe(1);
  });

  it('stock explicit contract error does not mutate populated cache', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().stockItemRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedStockItemToDomain(sampleNormalizedStockItem({ id: 'guid:stock-stable', name: 'Stable Stock' })),
    ]);

    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readStockItems: vi.fn(async () => {
          throw new AppError(
            ErrorCodes.VALIDATION_ERROR,
            'Tally returned an explicit error response for stock-item extraction',
            502,
            { contractStatus: 'TALLY_ERROR', reasonCode: 'tally_line_error' },
          );
        }),
      }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncStockItems()).rejects.toBeInstanceOf(AppError);
    expect(await repo.countByCompany(COMPANY)).toBe(1);
  });

  it('stock valid empty contract result completes without deleting rows', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().stockItemRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedStockItemToDomain(sampleNormalizedStockItem({ id: 'guid:stock-existing', name: 'Existing Stock' })),
    ]);

    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readStockItems: vi.fn(async () => ({
          items: [],
          durationMs: 1,
          rawByteLength: 1,
          contract: {
            contractVersion: '1',
            status: 'EMPTY',
            reasonCode: 'collection_empty',
            blocking: false,
            dataQualityStatus: 'EMPTY' as const,
          },
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
    expect(await repo.countByCompany(COMPANY)).toBe(1);
  });
});
