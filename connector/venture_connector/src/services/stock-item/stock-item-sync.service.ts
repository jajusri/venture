import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import {
  normalizeSyncFailure,
  sanitizeSyncProgressError,
} from '../../infrastructure/privacy/sync-failure-normalizer.js';
import type { ServiceStatus } from '../../core/types.js';
import type {
  StockItemChange,
  StockItemDetails,
  StockItemSearchParams,
  StockItemSearchResult,
  StockItemStatistics,
  StockItemSyncProgress,
  StockItemSyncResult,
  StockItemSyncRunRecord,
  StorageStatus,
} from '../../erp/stock-item/stock-item-domain.js';
import { mapNormalizedStockItemToDomain } from '../../erp/stock-item/stock-item-mapper.js';
import { validateStockItemCollection } from '../../erp/stock-item/stock-item-validation.js';
import type { ErpReadPort } from '../../erp/ports/erp-read-port.js';
import { computeStockItemFingerprint } from './stock-item-fingerprint.js';
import type { StockItemRepositoryPort } from './stock-item-repository.interface.js';
import type { CompanyResolver } from '../extraction/company-resolver.js';
import type { ConnectorSessionService } from '../interfaces/connector-session.js';
import { assertSessionValidation } from '../session/session-error-mapper.js';
import type { SqliteStorageService } from '../../storage/sqlite/storage-service.js';
import type { SyncRunRepository } from '../../storage/sqlite/sync-run-repository.js';
import { STORAGE_SCHEMA_VERSION } from '../../storage/sqlite/schema.js';

const BATCH_SIZE = 250;
const RESOURCE_KIND = 'stock-items' as const;

export interface StockItemSyncService {
  getStockItems(params: StockItemSearchParams): Promise<StockItemSearchResult>;
  getStockItemById(stockItemId: string): Promise<StockItemDetails | null>;
  getStatistics(): Promise<StockItemStatistics>;
  getSyncProgress(): Promise<StockItemSyncProgress>;
  getStorageStatus(): StorageStatus;
  listSyncRuns(limit?: number): Promise<readonly StockItemSyncRunRecord[]>;
  getSyncRun(syncRunId: string): Promise<StockItemSyncRunRecord | null>;
  syncStockItems(options?: { incremental?: boolean; maxAttempts?: number }): Promise<StockItemSyncResult>;
  cancelSync(): Promise<StockItemSyncProgress>;
  clearCache(): Promise<void>;
  runIntegrityCheck(): Promise<{ ok: boolean; message: string }>;
  createBackup(): Promise<{ ok: boolean; backupPath: string | null; message: string }>;
  start(): Promise<void>;
  stop(): Promise<void>;
  isRunning(): boolean;
  getStatus(): ServiceStatus;
}

export class StockItemSyncServiceImpl implements StockItemSyncService {
  private running = false;
  private readonly repositoryOverride?: StockItemRepositoryPort;
  private readonly syncRunsOverride?: SyncRunRepository;
  private readonly storage: SqliteStorageService;
  // Company-scoped: see the identical rationale on LedgerSyncServiceImpl (TD-036).
  private readonly progressByCompany = new Map<string, StockItemSyncProgress>();
  private readonly activeAbortByCompany = new Map<string, AbortController>();
  private readonly activeRunByCompany = new Map<string, StockItemSyncRunRecord>();
  private readonly syncInFlightByCompany = new Map<string, Promise<StockItemSyncResult>>();

  constructor(
    private readonly config: ConnectorConfig,
    private readonly readPort: ErpReadPort,
    private readonly companyResolver: CompanyResolver,
    private readonly connectorSession: ConnectorSessionService,
    private readonly logger: Logger,
    storage: SqliteStorageService,
    repository?: StockItemRepositoryPort,
    syncRuns?: SyncRunRepository,
  ) {
    this.storage = storage;
    this.repositoryOverride = repository;
    this.syncRunsOverride = syncRuns;
  }

  private get repository(): StockItemRepositoryPort {
    return this.repositoryOverride ?? this.storage.getBundle().stockItemRepository;
  }

  private get syncRuns(): SyncRunRepository {
    return this.syncRunsOverride ?? this.storage.getBundle().syncRunRepository;
  }

  private progressFor(companyId: string): StockItemSyncProgress {
    return this.progressByCompany.get(companyId) ?? createIdleProgress(companyId);
  }

  async start(): Promise<void> {
    this.running = true;
  }

  async stop(): Promise<void> {
    for (const abort of this.activeAbortByCompany.values()) {
      abort.abort();
    }
    this.running = false;
  }

  isRunning(): boolean {
    return this.running;
  }

  getStatus(): ServiceStatus {
    return {
      name: 'StockItemSync',
      running: this.running,
      ready: this.running,
      message: this.syncInFlightByCompany.size > 0 ? 'running' : 'idle',
    };
  }

  getStorageStatus(): StorageStatus {
    return this.storage.getStorageStatus();
  }

  async getStockItems(params: StockItemSearchParams): Promise<StockItemSearchResult> {
    const companyId = await this.requireCompanyId();
    return this.repository.search(companyId, params);
  }

  async getStockItemById(stockItemId: string): Promise<StockItemDetails | null> {
    const companyId = await this.requireCompanyId();
    return this.repository.findById(companyId, stockItemId);
  }

  async getStatistics(): Promise<StockItemStatistics> {
    const companyId = await this.requireCompanyId();
    return this.repository.getStatistics(companyId);
  }

  async getSyncProgress(): Promise<StockItemSyncProgress> {
    const companyId = this.peekCompanyId();
    if (!companyId) return createIdleProgress('');
    return { ...this.progressFor(companyId) };
  }

  listSyncRuns(limit = 20): Promise<readonly StockItemSyncRunRecord[]> {
    return this.requireCompanyId().then((companyId) =>
      this.syncRuns.listRuns(companyId, RESOURCE_KIND, limit),
    );
  }

  async getSyncRun(syncRunId: string): Promise<StockItemSyncRunRecord | null> {
    const companyId = await this.requireCompanyId();
    const run = this.syncRuns.findById(companyId, syncRunId);
    if (!run || run.resourceKind !== RESOURCE_KIND) {
      return null;
    }
    return run;
  }

  async cancelSync(): Promise<StockItemSyncProgress> {
    const companyId = this.peekCompanyId();
    if (!companyId) return createIdleProgress('');
    const activeRun = this.activeRunByCompany.get(companyId);
    const cancellable =
      this.syncInFlightByCompany.has(companyId) &&
      activeRun !== undefined &&
      (activeRun.status === 'running' || activeRun.status === 'cancelling');
    if (cancellable && activeRun) {
      const updatedRun: StockItemSyncRunRecord = {
        ...activeRun,
        cancelRequested: true,
        status: 'cancelling',
        updatedAt: new Date().toISOString(),
      };
      this.activeRunByCompany.set(companyId, updatedRun);
      this.syncRuns.updateRun(updatedRun);
      this.activeAbortByCompany.get(companyId)?.abort();
      this.progressByCompany.set(companyId, {
        ...this.progressFor(companyId),
        cancelRequested: true,
        status: 'cancelling',
      });
    }
    return this.getSyncProgress();
  }

  async clearCache(): Promise<void> {
    const companyId = await this.requireCompanyId();
    await this.repository.clearCompany(companyId);
    this.logger.info('stock_item_cache_cleared', { component: 'stock-item-sync', companyId });
  }

  runIntegrityCheck(): Promise<{ ok: boolean; message: string }> {
    return Promise.resolve(this.storage.runIntegrityCheck());
  }

  createBackup(): Promise<{ ok: boolean; backupPath: string | null; message: string }> {
    const backupDir = `${this.config.databasePath}/backups`;
    return Promise.resolve(this.storage.createBackup(backupDir));
  }

  async syncStockItems(
    options: { incremental?: boolean; maxAttempts?: number } = {},
  ): Promise<StockItemSyncResult> {
    const companyId = await this.requireCompanyId();
    if (this.syncInFlightByCompany.has(companyId)) {
      throw new AppError(
        ErrorCodes.SYNC_CONFLICT,
        'A stock item sync is already running for the selected company.',
        409,
      );
    }

    this.syncRuns.recoverAbandonedRuns(companyId, RESOURCE_KIND);
    const active = this.syncRuns.findActiveRun(companyId, RESOURCE_KIND);
    if (active) {
      throw new AppError(
        ErrorCodes.SYNC_CONFLICT,
        'A stock item sync is already active for the selected company.',
        409,
        { syncRunId: active.syncRunId },
      );
    }

    const inFlight = this.executeSync(companyId, options).finally(() => {
      this.syncInFlightByCompany.delete(companyId);
    });
    this.syncInFlightByCompany.set(companyId, inFlight);
    return inFlight;
  }

  private async executeSync(
    companyId: string,
    options: { incremental?: boolean; maxAttempts?: number },
  ): Promise<StockItemSyncResult> {
    const startedAt = Date.now();
    const companyName = await this.companyResolver.resolveName(companyId);
    const maxAttempts = options.maxAttempts ?? this.config.tallyRetryMaxAttempts;
    const abort = new AbortController();
    this.activeAbortByCompany.set(companyId, abort);
    const signal = abort.signal;

    const initialRun = this.syncRuns.createRun({
      companyId,
      resourceKind: RESOURCE_KIND,
      syncType: options.incremental ? 'incremental' : 'full',
      connectorVersion: this.config.connectorVersion,
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
      predecessorSyncRunId: this.syncRuns.findRetryPredecessor(companyId, RESOURCE_KIND)?.syncRunId ?? null,
    });
    this.activeRunByCompany.set(companyId, initialRun);

    this.progressByCompany.set(companyId, toProgress(initialRun, this.storage.getStorageStatus().migrationStatus));
    const changes: StockItemChange[] = [];

    try {
      const extraction = await this.extractWithRetry(companyName, maxAttempts, signal);
      if (signal.aborted) {
        return this.finalizeRun('cancelled', companyId, startedAt, changes, 0);
      }

      const syncedAt = new Date().toISOString();
      const mapped = extraction.items.map((item) => mapNormalizedStockItemToDomain(item, syncedAt));
      const validation = validateStockItemCollection(mapped);
      const runWithTotal: StockItemSyncRunRecord = {
        ...this.activeRunByCompany.get(companyId)!,
        totalExpected: mapped.length,
        updatedAt: new Date().toISOString(),
      };
      this.activeRunByCompany.set(companyId, runWithTotal);
      this.syncRuns.updateRun(runWithTotal);

      // Full restart from index zero on every run. lastProcessedId is audit-only (TD-006).
      for (let index = 0; index < mapped.length; index += BATCH_SIZE) {
        if (signal.aborted) {
          break;
        }
        const batch = mapped.slice(index, index + BATCH_SIZE);
        const toUpsert: StockItemDetails[] = [];
        let batchInserted = 0;
        let batchUpdated = 0;
        let batchSkipped = 0;
        let lastId: string | null = null;

        for (const item of batch) {
          const existing = await this.repository.findById(companyId, item.id);
          if (!existing) {
            toUpsert.push(item);
            batchInserted += 1;
            changes.push({ stockItemId: item.id, changeType: 'added' });
          } else if (
            options.incremental &&
            computeStockItemFingerprint(existing) === computeStockItemFingerprint(item)
          ) {
            batchSkipped += 1;
            changes.push({ stockItemId: item.id, changeType: 'skipped', reason: 'unchanged' });
          } else {
            toUpsert.push(item);
            batchUpdated += 1;
            changes.push({ stockItemId: item.id, changeType: 'updated' });
          }
          lastId = item.id;
        }

        // Atomic batch: domain upserts + checkpoint counters commit together or roll back together.
        // Run creation and terminal status updates remain outside this boundary (deliberate).
        const currentRun = this.activeRunByCompany.get(companyId);
        if (!currentRun) {
          throw new AppError(ErrorCodes.INTERNAL_ERROR, 'Stock item sync run missing during batch commit.', 500);
        }
        const nextRun: StockItemSyncRunRecord = {
          ...currentRun,
          processed: currentRun.processed + batch.length,
          inserted: currentRun.inserted + batchInserted,
          updated: currentRun.updated + batchUpdated,
          skipped: currentRun.skipped + batchSkipped,
          lastProcessedId: lastId,
          updatedAt: new Date().toISOString(),
        };
        await this.storage.runInTransaction(() => {
          if (toUpsert.length > 0) {
            // Joins ambient TX synchronously (SQLite adapter must not await inside the batch).
            void this.repository.upsertMany(companyId, toUpsert);
          }
          this.syncRuns.updateRun(nextRun);
        });
        this.activeRunByCompany.set(companyId, nextRun);
        this.progressByCompany.set(
          companyId,
          toProgress(nextRun, this.storage.getStorageStatus().migrationStatus, Date.now() - startedAt),
        );
      }

      const finalStatus = signal.aborted ? 'cancelled' : 'completed';
      return this.finalizeRun(finalStatus, companyId, startedAt, changes, validation.issues.length);
    } catch (error) {
      if (error instanceof AppError && error.code === ErrorCodes.SYNC_CANCELLED) {
        return this.finalizeRun('cancelled', companyId, startedAt, changes, 0);
      }
      const normalizedFailure = normalizeSyncFailure(error);
      const runOnFailure = this.activeRunByCompany.get(companyId);
      if (runOnFailure) {
        const failedRun: StockItemSyncRunRecord = {
          ...runOnFailure,
          status: 'failed',
          failureCode: normalizedFailure.failureCode,
          failureSummary: normalizedFailure.failureSummary,
          updatedAt: new Date().toISOString(),
          completedAt: new Date().toISOString(),
        };
        this.activeRunByCompany.set(companyId, failedRun);
        this.syncRuns.updateRun(failedRun);
      }
      this.progressByCompany.set(companyId, {
        ...this.progressFor(companyId),
        status: 'failed',
        lastError: sanitizeSyncProgressError(error),
        completedAt: new Date().toISOString(),
        durationMs: Date.now() - startedAt,
      });
      this.logger.error('stock_item_sync_failed', {
        component: 'stock-item-sync',
        code: normalizedFailure.failureCode,
        reasonCode: normalizedFailure.failureSummary,
      });
      this.activeRunByCompany.delete(companyId);
      throw error instanceof AppError
        ? error
        : new AppError(
            ErrorCodes.SERVICE_UNAVAILABLE,
            error instanceof Error ? error.message : 'Stock item sync failed',
            503,
            { feature: 'stock-item-sync' },
          );
    } finally {
      this.activeAbortByCompany.delete(companyId);
    }
  }

  private async finalizeRun(
    status: StockItemSyncProgress['status'],
    companyId: string,
    startedAt: number,
    changes: StockItemChange[],
    validationIssueCount: number,
  ): Promise<StockItemSyncResult> {
    const finishedRun = this.activeRunByCompany.get(companyId);
    let completedRun: StockItemSyncRunRecord | undefined;
    if (finishedRun) {
      completedRun = {
        ...finishedRun,
        status,
        completedAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      this.activeRunByCompany.set(companyId, completedRun);
      this.syncRuns.updateRun(completedRun);
    }
    this.progressByCompany.set(companyId, {
      ...(completedRun
        ? toProgress(completedRun, this.storage.getStorageStatus().migrationStatus, Date.now() - startedAt)
        : this.progressFor(companyId)),
      status,
      completedAt: new Date().toISOString(),
      durationMs: Date.now() - startedAt,
    });
    const statistics = await this.repository.getStatistics(companyId);
    this.logger.info('stock_item_sync_completed', {
      component: 'stock-item-sync',
      status,
      durationMs: this.progressFor(companyId).durationMs,
      stockItemCount: statistics.totalStockItems,
      incompleteData: statistics.incompleteData,
    });
    const result: StockItemSyncResult = {
      syncRunId: finishedRun?.syncRunId ?? '',
      status,
      extractionCompleteness: toExtractionCompleteness(status),
      deletionReconciliation: 'disabled',
      statistics,
      progress: { ...this.progressFor(companyId) },
      changes,
      validationIssueCount,
    };
    this.activeRunByCompany.delete(companyId);
    return result;
  }

  private async extractWithRetry(companyName: string, maxAttempts: number, signal: AbortSignal) {
    let lastError: unknown;
    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      if (signal.aborted) {
        throw new AppError(ErrorCodes.SYNC_CANCELLED, 'Stock item extraction cancelled.', 499);
      }
      try {
        return await this.readPort.readStockItems(companyName, { signal });
      } catch (error) {
        lastError = error;
        if (error instanceof AppError && error.code === ErrorCodes.SYNC_CANCELLED) {
          throw error;
        }
        if (attempt >= maxAttempts) {
          break;
        }
        await sleepWithAbort(this.config.tallyRetryBaseDelayMs * attempt, signal);
      }
    }
    throw lastError instanceof Error
      ? lastError
      : new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Stock item extraction failed after retries.', 503);
  }

  /**
   * A best-effort, never-throwing read of the currently selected company, for the two read-only/
   * no-op-safe status methods ([getSyncProgress], [cancelSync]) that must remain safe to call
   * before any company is ever selected (pre-existing contract predating company-scoped state —
   * a fresh Connector with nothing selected yet must still answer "idle", not fail the request).
   */
  private peekCompanyId(): string | null {
    if (!this.running) return null;
    return this.connectorSession.getSession().session.selectedCompany?.id ?? null;
  }

  private async requireCompanyId(): Promise<string> {
    if (!this.running) {
      throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Stock item sync service is not running.', 503);
    }
    const snapshot = this.connectorSession.getSession();
    const validation = await this.connectorSession.validateForOperation(snapshot.session.selectedCompany?.id);
    assertSessionValidation(validation);
    if (!snapshot.session.selectedCompany?.id) {
      throw new AppError(ErrorCodes.VALIDATION_ERROR, 'No company selected.', 400);
    }
    return snapshot.session.selectedCompany.id;
  }
}

function toExtractionCompleteness(
  status: StockItemSyncProgress['status'],
): StockItemSyncResult['extractionCompleteness'] {
  if (status === 'completed') return 'complete';
  if (status === 'cancelled') return 'cancelled';
  if (status === 'failed' || status === 'interrupted') return 'failed';
  return 'partial';
}

function createIdleProgress(companyId: string): StockItemSyncProgress {
  return {
    syncRunId: null,
    companyId,
    status: 'idle',
    totalExpected: null,
    startedAt: null,
    completedAt: null,
    durationMs: null,
    itemsProcessed: 0,
    itemsAdded: 0,
    itemsUpdated: 0,
    itemsSkipped: 0,
    itemsFailed: 0,
    lastError: null,
    cancelRequested: false,
    storageBackend: 'sqlite',
    migrationStatus: 'none',
  };
}

function toProgress(
  run: StockItemSyncRunRecord,
  migrationStatus: StorageStatus['migrationStatus'],
  durationMs: number | null = null,
): StockItemSyncProgress {
  return {
    syncRunId: run.syncRunId,
    companyId: run.companyId,
    status: run.status,
    totalExpected: run.totalExpected,
    startedAt: run.startedAt,
    completedAt: run.completedAt,
    durationMs,
    itemsProcessed: run.processed,
    itemsAdded: run.inserted,
    itemsUpdated: run.updated,
    itemsSkipped: run.skipped,
    itemsFailed: run.failed,
    lastError: run.failureSummary,
    cancelRequested: run.cancelRequested,
    storageBackend: 'sqlite',
    migrationStatus,
  };
}

function sleepWithAbort(ms: number, signal: AbortSignal): Promise<void> {
  if (signal.aborted) {
    throw new AppError(ErrorCodes.SYNC_CANCELLED, 'Stock item extraction cancelled.', 499);
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    const onAbort = (): void => {
      clearTimeout(timer);
      signal.removeEventListener('abort', onAbort);
      reject(new AppError(ErrorCodes.SYNC_CANCELLED, 'Stock item extraction cancelled.', 499));
    };
    signal.addEventListener('abort', onAbort, { once: true });
  });
}
