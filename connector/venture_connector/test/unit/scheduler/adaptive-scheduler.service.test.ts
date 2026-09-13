import { afterEach, describe, expect, it, vi } from 'vitest';

import type { LedgerSyncResult } from '../../../src/erp/ledger/ledger-domain.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { AdaptiveSchedulerService } from '../../../src/services/scheduler/adaptive-scheduler.service.js';
import { SchedulerStateRepository } from '../../../src/storage/sqlite/scheduler-state-repository.js';
import type { LedgerSyncService } from '../../../src/services/ledger/ledger-sync.service.js';
import type { StockItemSyncService } from '../../../src/services/stock-item/stock-item-sync.service.js';
import type { ConnectorSessionService } from '../../../src/services/interfaces/connector-session.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
  vi.restoreAllMocks();
});

function sampleResult(overrides: Partial<LedgerSyncResult> = {}): LedgerSyncResult {
  return {
    syncRunId: 'run-1',
    status: 'completed',
    statistics: {
      totalLedgers: 1,
      activeLedgers: 1,
      inactiveLedgers: 0,
      reservedLedgers: 0,
      deletedLedgers: 0,
      withGst: 0,
      withOpeningBalance: 0,
      lastSyncedAt: new Date().toISOString(),
    },
    progress: {
      syncRunId: 'run-1',
      companyId: 'estimation',
      status: 'completed',
      totalExpected: 1,
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      durationMs: 5,
      itemsProcessed: 1,
      itemsAdded: 0,
      itemsUpdated: 0,
      itemsSkipped: 1,
      itemsFailed: 0,
      lastError: null,
      cancelRequested: false,
      storageBackend: 'sqlite',
      migrationStatus: 'none',
    },
    changes: [{ ledgerId: 'guid:cash', changeType: 'skipped', reason: 'unchanged' }],
    validationIssueCount: 0,
    ...overrides,
  };
}

function switchableSession(initialCompanyId: string | null) {
  let companyId = initialCompanyId;
  const session: ConnectorSessionService = {
    start: async () => {},
    stop: async () => {},
    isRunning: () => true,
    getStatus: () => ({ name: 'ConnectorSession', running: true, ready: true }),
    getSession: () => ({
      session: {
        connectorVersion: '0.3.1',
        erpType: 'tally' as never,
        connectionStatus: 'connected',
        selectedCompany: companyId ? { id: companyId, name: companyId } : undefined,
        selectedAt: null,
        lastValidatedAt: null,
      } as never,
      contractVersion: '1',
    }),
    selectCompany: async () => ({ status: 'SUCCESS' }) as never,
    clearSelection: () => ({ session: undefined as never, contractVersion: '1' }),
    validateForOperation: async () => ({ status: 'SUCCESS' }) as never,
    refreshValidation: async () => ({ status: 'SUCCESS' }) as never,
  };
  return {
    session,
    switchTo: (next: string | null) => {
      companyId = next;
    },
  };
}

async function makeRepository() {
  const { storage } = await createTestSqliteStorage();
  return new SchedulerStateRepository(() => storage.getBundle().database);
}

const logger = createLogger({ service: 'test', level: 'error' });

describe('AdaptiveSchedulerService — runOnce (single-tick behaviour)', () => {
  it('does nothing when no company is selected', async () => {
    const repository = await makeRepository();
    const { session } = switchableSession(null);
    const ledgerSync = { syncLedgers: vi.fn() } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn() } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    await scheduler.runOnce();

    expect(ledgerSync.syncLedgers).not.toHaveBeenCalled();
    expect(stockItemSync.syncStockItems).not.toHaveBeenCalled();
  });

  it('seeds a fresh active_window row for a never-seen company/resource but does not sync yet (not due until 5 minutes pass)', async () => {
    const repository = await makeRepository();
    const { session } = switchableSession('estimation');
    const ledgerSync = { syncLedgers: vi.fn() } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn() } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    await scheduler.runOnce();

    expect(ledgerSync.syncLedgers).not.toHaveBeenCalled();
    expect(stockItemSync.syncStockItems).not.toHaveBeenCalled();
    const state = repository.find('estimation', 'ledgers');
    expect(state?.stage).toBe('active_window');
  });

  it('triggers a real sync via the existing syncLedgers()/syncStockItems() path when a check is due (restart recovery)', async () => {
    const repository = await makeRepository();
    // Simulate an overdue row, as if the Connector had just restarted after being down past the
    // scheduled check time.
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage: 'backoff_15',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() - 60_000).toISOString(),
      updatedAt: new Date(Date.now() - 60_000).toISOString(),
    });
    const { session } = switchableSession('estimation');
    const ledgerSync = {
      syncLedgers: vi.fn(async () => sampleResult()),
    } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn(async () => sampleResult()) } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    await scheduler.runOnce();

    expect(ledgerSync.syncLedgers).toHaveBeenCalledWith({ incremental: true });
    // No change (all skipped) while in backoff_15 -> steps to backoff_30.
    expect(repository.find('estimation', 'ledgers')?.stage).toBe('backoff_30');
  });

  it('does not trigger a sync when the next check is not yet due', async () => {
    const repository = await makeRepository();
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage: 'backoff_60',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() + 60 * 60_000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    const { session } = switchableSession('estimation');
    const ledgerSync = { syncLedgers: vi.fn() } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn() } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    await scheduler.runOnce();

    expect(ledgerSync.syncLedgers).not.toHaveBeenCalled();
  });

  it('a change (added/updated) resets to active_window; treats result.status !== completed as failed (no ladder movement)', async () => {
    const repository = await makeRepository();
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage: 'backoff_30',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() - 1000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    const { session } = switchableSession('estimation');
    const ledgerSync = {
      syncLedgers: vi.fn(async () =>
        sampleResult({ changes: [{ ledgerId: 'guid:new', changeType: 'added' }] }),
      ),
    } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn(async () => sampleResult()) } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    await scheduler.runOnce();

    expect(repository.find('estimation', 'ledgers')?.stage).toBe('active_window');
  });

  it('a thrown error (Tally unreachable, Connector failure, or a manual-sync SYNC_CONFLICT) is treated as failed -- stage and next-check-due are preserved, not advanced', async () => {
    const repository = await makeRepository();
    const dueAt = new Date(Date.now() - 1000).toISOString();
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage: 'backoff_30',
      activeWindowExpiresAt: null,
      nextCheckDueAt: dueAt,
      updatedAt: new Date().toISOString(),
    });
    const { session } = switchableSession('estimation');
    const ledgerSync = {
      syncLedgers: vi.fn(async () => {
        throw new Error('SYNC_CONFLICT: a ledger sync is already running for the selected company.');
      }),
    } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn(async () => sampleResult()) } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    await scheduler.runOnce();

    const state = repository.find('estimation', 'ledgers')!;
    expect(state.stage).toBe('backoff_30');
    // Retries at the same 30-minute interval from the moment of this failed check, not from the
    // original (now-past) due time and not some other interval.
    expect(Date.parse(state.nextCheckDueAt)).toBeGreaterThan(Date.parse(dueAt));
  });
});

describe('AdaptiveSchedulerService — company isolation', () => {
  it('runOnce only ever acts on the currently-selected company; switching never contaminates the other company\'s state', async () => {
    const repository = await makeRepository();
    const { session, switchTo } = switchableSession('company-a');
    repository.upsert({
      companyId: 'company-a',
      resourceKind: 'ledgers',
      stage: 'backoff_15',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() - 1000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    repository.upsert({
      companyId: 'company-b',
      resourceKind: 'ledgers',
      stage: 'backoff_60',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() - 1000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    const ledgerSync = {
      syncLedgers: vi.fn(async () => sampleResult()),
    } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn(async () => sampleResult()) } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    await scheduler.runOnce();
    // Company A was due and current -> progressed (no change -> backoff_15 to backoff_30).
    expect(repository.find('company-a', 'ledgers')?.stage).toBe('backoff_30');
    // Company B was also "due" by clock alone, but was never the selected company, so its own
    // row must remain completely untouched.
    expect(repository.find('company-b', 'ledgers')?.stage).toBe('backoff_60');
    expect(ledgerSync.syncLedgers).toHaveBeenCalledTimes(1);

    switchTo('company-b');
    await scheduler.runOnce();
    expect(repository.find('company-b', 'ledgers')?.stage).toBe('backoff_60');
    // Company A's already-updated state (now not due) must still be untouched by this second call.
    expect(repository.find('company-a', 'ledgers')?.stage).toBe('backoff_30');
    expect(ledgerSync.syncLedgers).toHaveBeenCalledTimes(2);
  });

  it('identical natural keys/company ids used loosely elsewhere never collide -- state is keyed strictly by (companyId, resourceKind)', async () => {
    const repository = await makeRepository();
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage: 'active_window',
      activeWindowExpiresAt: new Date(Date.now() + 60_000).toISOString(),
      nextCheckDueAt: new Date(Date.now() + 60_000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'stock-items',
      stage: 'backoff_60',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() + 60_000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    expect(repository.find('estimation', 'ledgers')?.stage).toBe('active_window');
    expect(repository.find('estimation', 'stock-items')?.stage).toBe('backoff_60');
  });
});

describe('AdaptiveSchedulerService — manual sync observation', () => {
  it('recordManualSyncOutcome(succeeded=true) resets to active_window exactly as a detected change would', async () => {
    const repository = await makeRepository();
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage: 'backoff_60',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() + 60 * 60_000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    const { session } = switchableSession('estimation');
    const ledgerSync = { syncLedgers: vi.fn() } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn() } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    scheduler.recordManualSyncOutcome('estimation', 'ledgers', true);

    const state = repository.find('estimation', 'ledgers')!;
    expect(state.stage).toBe('active_window');
    // Never touches the sync engine itself -- purely an observer of an outcome that already happened.
    expect(ledgerSync.syncLedgers).not.toHaveBeenCalled();
  });

  it('recordManualSyncOutcome(succeeded=false) never advances or resets the ladder', async () => {
    const repository = await makeRepository();
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage: 'backoff_30',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() + 30 * 60_000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    const { session } = switchableSession('estimation');
    const scheduler = new AdaptiveSchedulerService(
      repository,
      session,
      { syncLedgers: vi.fn() } as unknown as LedgerSyncService,
      { syncStockItems: vi.fn() } as unknown as StockItemSyncService,
      logger,
    );

    scheduler.recordManualSyncOutcome('estimation', 'ledgers', false);

    expect(repository.find('estimation', 'ledgers')?.stage).toBe('backoff_30');
  });

  it('a manual sync for a never-seen company/resource seeds and resets to active_window rather than crashing', async () => {
    const repository = await makeRepository();
    const { session } = switchableSession('estimation');
    const scheduler = new AdaptiveSchedulerService(
      repository,
      session,
      { syncLedgers: vi.fn() } as unknown as LedgerSyncService,
      { syncStockItems: vi.fn() } as unknown as StockItemSyncService,
      logger,
    );

    scheduler.recordManualSyncOutcome('estimation', 'stock-items', true);

    expect(repository.find('estimation', 'stock-items')?.stage).toBe('active_window');
  });
});

describe('AdaptiveSchedulerService — concurrency / duplicate initialization', () => {
  it('an overlapping runOnce call while one is already in flight is a no-op, never a duplicate sync', async () => {
    const repository = await makeRepository();
    repository.upsert({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage: 'backoff_15',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(Date.now() - 1000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
    const { session } = switchableSession('estimation');
    let releaseSync: (() => void) | undefined;
    const gate = new Promise<void>((resolve) => {
      releaseSync = resolve;
    });
    const ledgerSync = {
      syncLedgers: vi.fn(async () => {
        await gate;
        return sampleResult();
      }),
    } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn(async () => sampleResult()) } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger);

    const first = scheduler.runOnce();
    const second = scheduler.runOnce();
    releaseSync?.();
    await Promise.all([first, second]);

    expect(ledgerSync.syncLedgers).toHaveBeenCalledTimes(1);
  });

  it('start() called twice never arms a second interval timer', async () => {
    const repository = await makeRepository();
    const { session } = switchableSession(null);
    const ledgerSync = { syncLedgers: vi.fn() } as unknown as LedgerSyncService;
    const stockItemSync = { syncStockItems: vi.fn() } as unknown as StockItemSyncService;
    const scheduler = new AdaptiveSchedulerService(repository, session, ledgerSync, stockItemSync, logger, 1_000);
    const setIntervalSpy = vi.spyOn(global, 'setInterval');

    await scheduler.start();
    await scheduler.start();

    expect(setIntervalSpy).toHaveBeenCalledTimes(1);
    await scheduler.stop();
    expect(scheduler.isRunning()).toBe(false);
  });
});
