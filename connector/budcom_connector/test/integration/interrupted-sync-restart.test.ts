import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import type { LedgerSyncRunRecord, SyncResourceKind } from '../../src/erp/ledger/ledger-domain.js';
import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../../src/services/stock-item/stock-item-sync.service.js';
import { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import type { SyncRunRepository } from '../../src/storage/sqlite/sync-run-repository.js';
import { STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';
import { sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import { sampleNormalizedStockItem } from '../helpers/stock-item-fixtures.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

const COMPANY = 'estimation';
const CONNECTOR_VERSION = '0.3.1';

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
    readLedgerContactDetails: vi.fn(),
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

function createRunInput(companyId: string, resourceKind: SyncResourceKind) {
  return {
    companyId,
    resourceKind,
    syncType: 'full' as const,
    connectorVersion: CONNECTOR_VERSION,
    schemaVersion: String(STORAGE_SCHEMA_VERSION),
  };
}

/** Seeds a run left in running/cancelling state with partial committed progress, then recovers it. */
function seedAbandonedRun(
  syncRuns: SyncRunRepository,
  options: {
    companyId?: string;
    resourceKind: SyncResourceKind;
    status?: 'running' | 'cancelling';
    processed?: number;
    inserted?: number;
    lastProcessedId?: string | null;
    retryCount?: number;
  },
): LedgerSyncRunRecord {
  const companyId = options.companyId ?? COMPANY;
  const run = syncRuns.createRun(createRunInput(companyId, options.resourceKind));
  syncRuns.updateRun({
    ...run,
    status: options.status ?? 'running',
    processed: options.processed ?? 2,
    inserted: options.inserted ?? 2,
    lastProcessedId: options.lastProcessedId ?? 'bank',
    totalExpected: 3,
    cancelRequested: options.status === 'cancelling',
  });
  return syncRuns.findById(companyId, run.syncRunId)!;
}

function createLedgerService(
  storage: SqliteStorageService,
  basePath: string,
  readPort: ErpReadPort,
): LedgerSyncServiceImpl {
  return new LedgerSyncServiceImpl(
    createTestConnectorConfig(basePath),
    readPort,
    companyResolver(),
    createPermissiveSessionMock(),
    createLogger({ service: 'test', level: 'error' }),
    storage,
  );
}

function createStockService(
  storage: SqliteStorageService,
  basePath: string,
  readPort: ErpReadPort,
): StockItemSyncServiceImpl {
  return new StockItemSyncServiceImpl(
    createTestConnectorConfig(basePath),
    readPort,
    companyResolver(),
    createPermissiveSessionMock(),
    createLogger({ service: 'test', level: 'error' }),
    storage,
  );
}

describe('TD-006 interrupted sync restart (ledger)', () => {
  it('recovers running and cancelling abandoned runs to interrupted', async () => {
    const { storage } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;

    seedAbandonedRun(syncRuns, { resourceKind: 'ledgers', status: 'running' });
    const cancelling = seedAbandonedRun(syncRuns, {
      resourceKind: 'ledgers',
      status: 'cancelling',
      companyId: 'company-b',
      lastProcessedId: 'cash',
    });

    expect(syncRuns.recoverAbandonedRuns(COMPANY, 'ledgers')).toBe(1);
    expect(syncRuns.recoverAbandonedRuns('company-b', 'ledgers')).toBe(1);

    const interrupted = syncRuns.findById(COMPANY, syncRuns.listRuns(COMPANY, 'ledgers', 1)[0]!.syncRunId)!;
    expect(interrupted.status).toBe('interrupted');
    expect(interrupted.failureCode).toBe('ABANDONED');
    expect(interrupted.processed).toBe(2);
    expect(interrupted.lastProcessedId).toBe('bank');

    const cancelledRun = syncRuns.findById('company-b', cancelling.syncRunId)!;
    expect(cancelledRun.status).toBe('interrupted');
  });

  it('creates a linked retry run after interruption with correct retryCount', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    const interrupted = seedAbandonedRun(syncRuns, { resourceKind: 'ledgers' });
    syncRuns.recoverAbandonedRuns(COMPANY, 'ledgers');

    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [
          sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' }),
          sampleNormalizedLedger({ name: 'Bank', normalizedName: 'bank' }),
          sampleNormalizedLedger({ name: 'Sales', normalizedName: 'sales' }),
        ],
        durationMs: 1,
        rawByteLength: 100,
      })),
    });
    const service = createLedgerService(storage, basePath, readPort);
    await service.start();
    const result = await service.syncLedgers();
    expect(result.status).toBe('completed');

    const runs = syncRuns.listRuns(COMPANY, 'ledgers', 5);
    const retry = runs.find((r) => r.status === 'completed')!;
    expect(retry.syncRunId).not.toBe(interrupted.syncRunId);
    expect(retry.predecessorSyncRunId).toBe(interrupted.syncRunId);
    expect(retry.retryCount).toBe(1);
    expect(interrupted.syncRunId).not.toBe(retry.syncRunId);
  });

  it('fresh run after completed retry has no predecessor and retryCount zero', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    seedAbandonedRun(syncRuns, { resourceKind: 'ledgers' });
    syncRuns.recoverAbandonedRuns(COMPANY, 'ledgers');

    const items = [
      sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' }),
      sampleNormalizedLedger({ name: 'Bank', normalizedName: 'bank' }),
    ];
    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({ items, durationMs: 1, rawByteLength: 50 })),
    });
    const service = createLedgerService(storage, basePath, readPort);
    await service.start();
    await service.syncLedgers();

    const second = await service.syncLedgers({ incremental: true });
    expect(second.status).toBe('completed');

    const runs = syncRuns.listRuns(COMPANY, 'ledgers', 5);
    const latest = runs[0]!;
    expect(latest.predecessorSyncRunId).toBeNull();
    expect(latest.retryCount).toBe(0);
  });

  it('reprocesses from index zero and does not skip records after changed ordering or pre-checkpoint insert', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    const ledgerRepo = storage.getBundle().ledgerRepository;
    const interrupted = seedAbandonedRun(syncRuns, {
      resourceKind: 'ledgers',
      lastProcessedId: 'bank',
      processed: 2,
    });
    syncRuns.recoverAbandonedRuns(COMPANY, 'ledgers');

    const syncedAt = '2026-01-01T00:00:00.000Z';
    await ledgerRepo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(
        sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' }),
        syncedAt,
      ),
      mapNormalizedLedgerToDomain(
        sampleNormalizedLedger({ name: 'Bank', normalizedName: 'bank' }),
        syncedAt,
      ),
    ]);
    await ledgerRepo.markLedgerIdentityCurrent(COMPANY);

    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [
          sampleNormalizedLedger({ name: 'New Item', normalizedName: 'new item' }),
          sampleNormalizedLedger({ name: 'Bank', normalizedName: 'bank' }),
          sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' }),
        ],
        durationMs: 1,
        rawByteLength: 120,
      })),
    });
    const service = createLedgerService(storage, basePath, readPort);
    await service.start();
    await service.syncLedgers();

    expect(await ledgerRepo.countByCompany(COMPANY)).toBe(3);
    const retry = syncRuns.listRuns(COMPANY, 'ledgers', 1)[0]!;
    expect(retry.processed).toBe(3);
    expect(retry.predecessorSyncRunId).toBe(interrupted.syncRunId);
    expect(interrupted.lastProcessedId).toBe('bank');
    expect(retry.lastProcessedId).toBe(sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' }).id);
  });

  it('does not link retry to completed, failed, or unrelated interrupted runs', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    const db = storage.getBundle().database.getDatabase();
    const now = '2026-01-02T00:00:00.000Z';

    db.prepare(
      `INSERT INTO sync_runs (
        sync_run_id, company_id, resource_kind, sync_type, status, started_at, updated_at, completed_at,
        total_expected, processed, inserted, updated_count, skipped, failed, last_processed_id,
        predecessor_sync_run_id, retry_count, cancel_requested, failure_code, failure_summary,
        connector_version, schema_version
      ) VALUES (?, ?, 'ledgers', 'full', 'completed', ?, ?, ?, 1, 1, 1, 0, 0, 0, NULL, NULL, 0, 0, NULL, NULL, ?, ?)`,
    ).run('completed-run', COMPANY, now, now, now, CONNECTOR_VERSION, String(STORAGE_SCHEMA_VERSION));

    db.prepare(
      `INSERT INTO sync_runs (
        sync_run_id, company_id, resource_kind, sync_type, status, started_at, updated_at, completed_at,
        total_expected, processed, inserted, updated_count, skipped, failed, last_processed_id,
        predecessor_sync_run_id, retry_count, cancel_requested, failure_code, failure_summary,
        connector_version, schema_version
      ) VALUES (?, ?, 'ledgers', 'full', 'failed', ?, ?, ?, 1, 0, 0, 0, 0, 0, NULL, NULL, 0, 0, 'ERR', 'fail', ?, ?)`,
    ).run('failed-run', COMPANY, now, now, now, CONNECTOR_VERSION, String(STORAGE_SCHEMA_VERSION));

    seedAbandonedRun(syncRuns, { resourceKind: 'stock-items', companyId: COMPANY, lastProcessedId: 'item-a' });
    syncRuns.recoverAbandonedRuns(COMPANY, 'stock-items');

    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })],
        durationMs: 1,
        rawByteLength: 20,
      })),
    });
    const service = createLedgerService(storage, basePath, readPort);
    await service.start();
    await service.syncLedgers();

    const latest = syncRuns.listRuns(COMPANY, 'ledgers', 1)[0]!;
    expect(latest.predecessorSyncRunId).toBeNull();
    expect(latest.retryCount).toBe(0);
  });

  it('isolates retry lineage by company and resource kind', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;

    const ledgerInterrupted = seedAbandonedRun(syncRuns, { resourceKind: 'ledgers', companyId: COMPANY });
    seedAbandonedRun(syncRuns, { resourceKind: 'stock-items', companyId: COMPANY, lastProcessedId: 'widget' });
    seedAbandonedRun(syncRuns, { resourceKind: 'ledgers', companyId: 'other-co', lastProcessedId: 'x' });
    syncRuns.recoverAllAbandonedRuns();

    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })],
        durationMs: 1,
        rawByteLength: 20,
      })),
      readStockItems: vi.fn(async () => ({
        items: [sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' })],
        durationMs: 1,
        rawByteLength: 20,
      })),
    });
    const ledgerService = createLedgerService(storage, basePath, readPort);
    const stockService = createStockService(storage, basePath, readPort);
    await ledgerService.start();
    await stockService.start();
    await ledgerService.syncLedgers();
    await stockService.syncStockItems();

    const ledgerRetry = syncRuns.listRuns(COMPANY, 'ledgers', 1)[0]!;
    const stockRetry = syncRuns.listRuns(COMPANY, 'stock-items', 1)[0]!;
    expect(ledgerRetry.predecessorSyncRunId).toBe(ledgerInterrupted.syncRunId);
    expect(stockRetry.predecessorSyncRunId).not.toBe(ledgerInterrupted.syncRunId);
    expect(syncRuns.findRetryPredecessor('other-co', 'ledgers')?.syncRunId).toBeDefined();
    expect(syncRuns.findRetryPredecessor(COMPANY, 'ledgers')).toBeNull();
  });

  it('updates renamed ledger in place when GUID is stable', async () => {
    const guid = 'dddd-eeee-ffff-aaaa-000000000010';
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    const ledgerRepo = storage.getBundle().ledgerRepository;
    seedAbandonedRun(syncRuns, { resourceKind: 'ledgers', lastProcessedId: `guid:${guid}` });
    syncRuns.recoverAbandonedRuns(COMPANY, 'ledgers');

    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({
        items: [
          sampleNormalizedLedger({
            guid,
            id: `guid:${guid}`,
            name: 'Petty Cash',
            normalizedName: 'petty cash',
          }),
        ],
        durationMs: 1,
        rawByteLength: 20,
      })),
    });
    const service = createLedgerService(storage, basePath, readPort);
    await service.start();
    await service.syncLedgers();

    expect(await ledgerRepo.findById(COMPANY, `guid:${guid}`)).not.toBeNull();
    expect(await ledgerRepo.countByCompany(COMPANY)).toBe(1);
  });

  it('incremental retry prevents duplicate rows via fingerprints', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    seedAbandonedRun(syncRuns, { resourceKind: 'ledgers' });
    syncRuns.recoverAbandonedRuns(COMPANY, 'ledgers');

    const items = [
      sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' }),
      sampleNormalizedLedger({ name: 'Bank', normalizedName: 'bank' }),
    ];
    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({ items, durationMs: 1, rawByteLength: 50 })),
    });
    const service = createLedgerService(storage, basePath, readPort);
    await service.start();
    await service.syncLedgers();
    const retry = await service.syncLedgers({ incremental: true });
    expect(retry.status).toBe('completed');
    expect(await storage.getBundle().ledgerRepository.countByCompany(COMPANY)).toBe(2);
    const latest = syncRuns.listRuns(COMPANY, 'ledgers', 1)[0]!;
    expect(latest.skipped).toBe(2);
  });
});

describe('TD-006 interrupted sync restart (stock items)', () => {
  it('mirrors ledger retry lineage and full restart behavior', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    const interrupted = seedAbandonedRun(syncRuns, {
      resourceKind: 'stock-items',
      lastProcessedId: 'gasket',
    });
    syncRuns.recoverAbandonedRuns(COMPANY, 'stock-items');

    const readPort = createReadPort({
      readStockItems: vi.fn(async () => ({
        items: [
          sampleNormalizedStockItem({ id: 'widget', name: 'Widget', normalizedName: 'widget' }),
          sampleNormalizedStockItem({ id: 'gasket', name: 'Gasket', normalizedName: 'gasket' }),
          sampleNormalizedStockItem({ id: 'bolt', name: 'Bolt', normalizedName: 'bolt' }),
        ],
        durationMs: 1,
        rawByteLength: 100,
      })),
    });
    const service = createStockService(storage, basePath, readPort);
    await service.start();
    await service.syncStockItems();

    const retry = syncRuns.listRuns(COMPANY, 'stock-items', 1)[0]!;
    expect(retry.predecessorSyncRunId).toBe(interrupted.syncRunId);
    expect(retry.retryCount).toBe(1);
    expect(retry.processed).toBe(3);
    expect(await storage.getBundle().stockItemRepository.countByCompany(COMPANY)).toBe(3);
  });

  it('recovers abandoned stock-item runs on storage startup', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    seedAbandonedRun(syncRuns, { resourceKind: 'stock-items', lastProcessedId: 'widget' });
    await storage.stop();

    const config = createTestConnectorConfig(basePath);
    const restarted = new SqliteStorageService(config, {
      info: () => {},
      warn: () => {},
      error: () => {},
      debug: () => {},
      child: () => ({ info: () => {}, warn: () => {}, error: () => {}, debug: () => {}, child: () => ({} as never) }),
    } as never);
    await restarted.start();

    const run = restarted.getBundle().syncRunRepository.listRuns(COMPANY, 'stock-items', 1)[0]!;
    expect(run.status).toBe('interrupted');
    expect(run.failureCode).toBe('ABANDONED');
    await restarted.stop();
  });
});

describe('TD-006 rollback then retry', () => {
  it('leaves no partial batch after checkpoint failure and retry completes idempotently', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    let armed = true;
    const original = syncRuns.updateRun.bind(syncRuns);
    vi.spyOn(syncRuns, 'updateRun').mockImplementation((record: LedgerSyncRunRecord) => {
      if (armed && record.processed > 0 && record.status === 'running') {
        armed = false;
        throw new Error('injected checkpoint failure');
      }
      return original(record);
    });

    const items = [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })];
    const readPort = createReadPort({
      readLedgers: vi.fn(async () => ({ items, durationMs: 1, rawByteLength: 20 })),
    });
    const service = createLedgerService(storage, basePath, readPort);
    await service.start();
    await expect(service.syncLedgers()).rejects.toThrow(/injected checkpoint failure/i);
    expect(await storage.getBundle().ledgerRepository.countByCompany(COMPANY)).toBe(0);

    const failed = syncRuns.listRuns(COMPANY, 'ledgers', 1)[0]!;
    expect(failed.status).toBe('failed');
    syncRuns.updateRun({ ...failed, status: 'interrupted', failureCode: 'ABANDONED' });

    vi.restoreAllMocks();
    await service.syncLedgers();
    expect(await storage.getBundle().ledgerRepository.countByCompany(COMPANY)).toBe(1);
    const retry = syncRuns.listRuns(COMPANY, 'ledgers', 1)[0]!;
    expect(retry.status).toBe('completed');
    expect(retry.predecessorSyncRunId).toBe(failed.syncRunId);
  });
});

describe('TD-006 retry predecessor selection', () => {
  it('selects the most recent unsuperseded interrupted run only once', async () => {
    const { storage } = await createTestSqliteStorage();
    const syncRuns = storage.getBundle().syncRunRepository;
    const db = storage.getBundle().database.getDatabase();
    const older = '2026-01-01T00:00:00.000Z';
    const newer = '2026-01-02T00:00:00.000Z';

    db.prepare(
      `INSERT INTO sync_runs (
        sync_run_id, company_id, resource_kind, sync_type, status, started_at, updated_at, completed_at,
        total_expected, processed, inserted, updated_count, skipped, failed, last_processed_id,
        predecessor_sync_run_id, retry_count, cancel_requested, failure_code, failure_summary,
        connector_version, schema_version
      ) VALUES ('old-int', ?, 'ledgers', 'full', 'interrupted', ?, ?, ?, 1, 1, 1, 0, 0, 0, 'cash', NULL, 0, 0, 'ABANDONED', 'x', ?, ?)`,
    ).run(COMPANY, older, older, older, CONNECTOR_VERSION, String(STORAGE_SCHEMA_VERSION));

    db.prepare(
      `INSERT INTO sync_runs (
        sync_run_id, company_id, resource_kind, sync_type, status, started_at, updated_at, completed_at,
        total_expected, processed, inserted, updated_count, skipped, failed, last_processed_id,
        predecessor_sync_run_id, retry_count, cancel_requested, failure_code, failure_summary,
        connector_version, schema_version
      ) VALUES ('new-int', ?, 'ledgers', 'full', 'interrupted', ?, ?, ?, 1, 1, 1, 0, 0, 0, 'bank', NULL, 0, 0, 'ABANDONED', 'x', ?, ?)`,
    ).run(COMPANY, newer, newer, newer, CONNECTOR_VERSION, String(STORAGE_SCHEMA_VERSION));

    const predecessor = syncRuns.findRetryPredecessor(COMPANY, 'ledgers');
    expect(predecessor?.syncRunId).toBe('new-int');

    syncRuns.createRun({ ...createRunInput(COMPANY, 'ledgers'), predecessorSyncRunId: 'new-int' });
    expect(syncRuns.findRetryPredecessor(COMPANY, 'ledgers')).toBeNull();
    expect(syncRuns.findRetryPredecessor(COMPANY, 'ledgers')).not.toEqual(
      expect.objectContaining({ syncRunId: 'old-int' }),
    );
  });
});
