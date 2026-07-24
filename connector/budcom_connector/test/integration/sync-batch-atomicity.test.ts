import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import type { LedgerSyncRunRecord } from '../../src/erp/ledger/ledger-domain.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../../src/services/stock-item/stock-item-sync.service.js';
import type { SyncRunRepository } from '../../src/storage/sqlite/sync-run-repository.js';
import { sampleLedgerDetails, sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import { sampleNormalizedStockItem } from '../helpers/stock-item-fixtures.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

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

function companyResolver(name = 'Demo Company'): CompanyResolver {
  return { resolveName: vi.fn(async () => name) } as unknown as CompanyResolver;
}

/**
 * Fails the first in-batch checkpoint update (processed > 0, still running).
 * Leaves run creation, totalExpected, cancel, and terminal updates alone.
 */
function installCheckpointFault(syncRuns: SyncRunRepository): { arm: () => void } {
  const original = syncRuns.updateRun.bind(syncRuns);
  let armed = false;
  vi.spyOn(syncRuns, 'updateRun').mockImplementation((record: LedgerSyncRunRecord) => {
    if (armed && record.processed > 0 && record.status === 'running') {
      armed = false;
      throw new Error('injected checkpoint failure');
    }
    return original(record);
  });
  return {
    arm: () => {
      armed = true;
    },
  };
}

const CASH_LEDGER = sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' });
const BANK_LEDGER = sampleNormalizedLedger({ name: 'Bank', normalizedName: 'bank' });

describe('sync batch atomicity (Reliability Step 1)', () => {
  it('commits ledger data and checkpoint together on success', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [CASH_LEDGER, BANK_LEDGER],
        durationMs: 1,
        rawByteLength: 100,
      })),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const result = await service.syncLedgers();
    expect(result.status).toBe('completed');
    expect(result.statistics.totalLedgers).toBe(2);

    const run = (await service.listSyncRuns())[0]!;
    expect(run.status).toBe('completed');
    expect(run.processed).toBe(2);
    expect(run.inserted).toBe(2);
    expect(run.lastProcessedId).toBeTruthy();
    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(2);
  });

  it('commits stock-item data and checkpoint together on success', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = createReadPort({
      readStockItems: vi.fn(async () => ({
        items: [
          sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' }),
          sampleNormalizedStockItem({ id: 'gasket', name: 'Gasket', normalizedName: 'gasket' }),
        ],
        durationMs: 1,
        rawByteLength: 100,
      })),
    });
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    const result = await service.syncStockItems();
    expect(result.status).toBe('completed');
    expect(result.statistics.totalStockItems).toBe(2);

    const run = (await service.listSyncRuns())[0]!;
    expect(run.status).toBe('completed');
    expect(run.processed).toBe(2);
    expect(run.lastProcessedId).toBeTruthy();
    expect(await storage.getBundle().stockItemRepository.countByCompany('estimation')).toBe(2);
  });

  it('rolls back ledger data when checkpoint update fails', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const fault = installCheckpointFault(storage.getBundle().syncRunRepository);
    fault.arm();

    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })],
        durationMs: 1,
        rawByteLength: 50,
      })),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toThrow(/injected checkpoint failure|Ledger sync|SERVICE_UNAVAILABLE|unavailable/i);

    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(0);
    const runs = storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers');
    expect(runs[0]?.status).toBe('failed');
    expect(runs[0]?.processed).toBe(0);
    expect(runs[0]?.lastProcessedId).toBeNull();
  });

  it('rolls back stock-item data when checkpoint update fails', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const fault = installCheckpointFault(storage.getBundle().syncRunRepository);
    fault.arm();

    const readPort = createReadPort({
      readStockItems: vi.fn(async () => ({
        items: [sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' })],
        durationMs: 1,
        rawByteLength: 50,
      })),
    });
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();

    await expect(service.syncStockItems()).rejects.toThrow(/injected checkpoint failure|unavailable/i);

    expect(await storage.getBundle().stockItemRepository.countByCompany('estimation')).toBe(0);
    const runs = storage.getBundle().syncRunRepository.listRuns('estimation', 'stock-items');
    expect(runs[0]?.status).toBe('failed');
    expect(runs[0]?.processed).toBe(0);
    expect(runs[0]?.lastProcessedId).toBeNull();
  });

  it('does not advance ledger checkpoint when data upsert fails', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    vi.spyOn(storage.getBundle().ledgerRepository, 'upsertMany').mockImplementation(() => {
      throw new Error('injected upsert failure');
    });

    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })],
        durationMs: 1,
        rawByteLength: 50,
      })),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toThrow(/injected upsert failure|unavailable/i);

    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(0);
    const run = storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers')[0]!;
    expect(run.status).toBe('failed');
    expect(run.processed).toBe(0);
    expect(run.lastProcessedId).toBeNull();
  });

  it('does not advance stock-item checkpoint when data upsert fails', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    vi.spyOn(storage.getBundle().stockItemRepository, 'upsertMany').mockImplementation(() => {
      throw new Error('injected upsert failure');
    });

    const readPort = createReadPort({
      readStockItems: vi.fn(async () => ({
        items: [sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' })],
        durationMs: 1,
        rawByteLength: 50,
      })),
    });
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncStockItems()).rejects.toThrow(/injected upsert failure|unavailable/i);

    expect(await storage.getBundle().stockItemRepository.countByCompany('estimation')).toBe(0);
    const run = storage.getBundle().syncRunRepository.listRuns('estimation', 'stock-items')[0]!;
    expect(run.status).toBe('failed');
    expect(run.processed).toBe(0);
    expect(run.lastProcessedId).toBeNull();
  });

  it('retries after injected checkpoint failure without creating ledger duplicates', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const fault = installCheckpointFault(storage.getBundle().syncRunRepository);
    fault.arm();

    const items = [
      sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' }),
      sampleNormalizedLedger({ name: 'Bank', normalizedName: 'bank' }),
    ];
    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({ items, durationMs: 1, rawByteLength: 100 })),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toThrow();
    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(0);

    const retry = await service.syncLedgers();
    expect(retry.status).toBe('completed');
    expect(retry.statistics.totalLedgers).toBe(2);
    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(2);
  });

  it('preserves existing ledger data after a rolled-back batch', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })],
        durationMs: 1,
        rawByteLength: 50,
      })),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await service.syncLedgers();
    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(1);

    const fault = installCheckpointFault(storage.getBundle().syncRunRepository);
    fault.arm();
    vi.mocked(readPort.readLedgers).mockImplementation(async () => ({
      items: [
        sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' }),
        sampleNormalizedLedger({ name: 'Bank', normalizedName: 'bank' }),
      ],
      durationMs: 1,
      rawByteLength: 100,
    }));

    await expect(service.syncLedgers()).rejects.toThrow();
    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(1);
    expect(await storage.getBundle().ledgerRepository.findById('estimation', CASH_LEDGER.id)).toBeTruthy();
    expect(await storage.getBundle().ledgerRepository.findById('estimation', BANK_LEDGER.id)).toBeNull();
  });

  it('keeps company isolation when a checkpoint fault rolls back one company batch', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    await storage.getBundle().ledgerRepository.upsertMany('other-co', [sampleLedgerDetails({ name: 'Other', normalizedName: 'other', balanceNature: 'unknown' })]);
    await storage.getBundle().ledgerRepository.markLedgerIdentityCurrent('other-co');

    const fault = installCheckpointFault(storage.getBundle().syncRunRepository);
    fault.arm();
    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })],
        durationMs: 1,
        rawByteLength: 50,
      })),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toThrow();

    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(0);
    expect(await storage.getBundle().ledgerRepository.countByCompany('other-co')).toBe(1);
  });

  it('keeps resource_kind isolation when ledger checkpoint fault occurs', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const stockPort = createReadPort({
      readStockItems: vi.fn(async () => ({
        items: [sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' })],
        durationMs: 1,
        rawByteLength: 50,
      })),
    });
    const stockService = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      stockPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await stockService.start();
    await stockService.syncStockItems();
    expect(await storage.getBundle().stockItemRepository.countByCompany('estimation')).toBe(1);

    const fault = installCheckpointFault(storage.getBundle().syncRunRepository);
    fault.arm();
    const ledgerService = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readLedgers: vi.fn(async () => ({
          items: [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })],
          durationMs: 1,
          rawByteLength: 50,
        })),
      }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await ledgerService.start();
    await expect(ledgerService.syncLedgers()).rejects.toThrow();

    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(0);
    expect(await storage.getBundle().stockItemRepository.countByCompany('estimation')).toBe(1);
    const stockRuns = storage.getBundle().syncRunRepository.listRuns('estimation', 'stock-items');
    expect(stockRuns[0]?.status).toBe('completed');
    const ledgerRuns = storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers');
    expect(ledgerRuns[0]?.status).toBe('failed');
  });

  it('cancellation at a supported boundary does not falsely advance the checkpoint', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    let releaseExtraction: (() => void) | undefined;
    const extractionGate = new Promise<void>((resolve) => {
      releaseExtraction = resolve;
    });

    const readPort = createReadPort({
      readLedgers: vi.fn(async () => {
        await extractionGate;
        return {
          items: Array.from({ length: 3 }, (_, index) =>
            sampleNormalizedLedger({
              id: `ledger-${index}`,
              name: `Ledger ${index}`,
              normalizedName: `ledger ${index}`,
            }),
          ),
          durationMs: 1,
          rawByteLength: 100,
        };
      }),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();

    const syncPromise = service.syncLedgers();
    await vi.waitFor(() => {
      expect(readPort.readLedgers).toHaveBeenCalled();
      expect(service.getSyncProgress().syncRunId).toBeTruthy();
    });
    // Cancel during extraction (supported cooperative boundary) before any batch commit.
    await service.cancelSync();
    expect(service.getSyncProgress().status).toBe('cancelling');
    releaseExtraction?.();
    const result = await syncPromise;

    expect(result.status).toBe('cancelled');
    expect(await storage.getBundle().ledgerRepository.countByCompany('estimation')).toBe(0);
    const run = storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers')[0]!;
    expect(run.status).toBe('cancelled');
    expect(run.processed).toBe(0);
    expect(run.lastProcessedId).toBeNull();
  });

  it('records terminal failed state after injected batch failure', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const fault = installCheckpointFault(storage.getBundle().syncRunRepository);
    fault.arm();
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      createReadPort({
        readStockItems: vi.fn(async () => ({
          items: [sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' })],
          durationMs: 1,
          rawByteLength: 50,
        })),
      }),
      companyResolver(),
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncStockItems()).rejects.toThrow();
    const progress = service.getSyncProgress();
    expect(progress.status).toBe('failed');
    expect(progress.lastError).toBe('storage_failure');
    const run = storage.getBundle().syncRunRepository.listRuns('estimation', 'stock-items')[0]!;
    expect(run.status).toBe('failed');
    expect(run.completedAt).toBeTruthy();
  });
});
