import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { ServiceStatus } from '../../core/types.js';
import type {
  LedgerChange,
  LedgerDetails,
  LedgerSearchParams,
  LedgerSearchResult,
  LedgerStatistics,
  LedgerSyncProgress,
  LedgerSyncResult,
  LedgerSyncRunRecord,
  StorageStatus,
} from '../../erp/ledger/ledger-domain.js';
import { mapNormalizedLedgerToDomain } from '../../erp/ledger/ledger-mapper.js';
import { validateLedgerCollection } from '../../erp/ledger/ledger-validation.js';
import type { ErpReadPort } from '../../erp/ports/erp-read-port.js';
import { computeLedgerFingerprint } from './ledger-fingerprint.js';
import type { LedgerRepositoryPort } from './ledger-repository.interface.js';
import type { CompanyResolver } from '../extraction/company-resolver.js';
import type { ConnectorSessionService } from '../interfaces/connector-session.js';
import type { SyncEngineService } from '../interfaces/sync-engine.js';
import { assertSessionValidation } from '../session/session-error-mapper.js';
import type { SqliteStorageService } from '../../storage/sqlite/storage-service.js';
import type { SyncRunRepository } from '../../storage/sqlite/sync-run-repository.js';
import { STORAGE_SCHEMA_VERSION } from '../../storage/sqlite/schema.js';

const BATCH_SIZE = 250;

export interface LedgerSyncService extends SyncEngineService {
  getLedgers(params: LedgerSearchParams): Promise<LedgerSearchResult>;
  getLedgerById(ledgerId: string): Promise<LedgerDetails | null>;
  getStatistics(): Promise<LedgerStatistics>;
  getSyncProgress(): LedgerSyncProgress;
  getStorageStatus(): StorageStatus;
  listSyncRuns(limit?: number): Promise<readonly LedgerSyncRunRecord[]>;
  getSyncRun(syncRunId: string): Promise<LedgerSyncRunRecord | null>;
  syncLedgers(options?: { incremental?: boolean; maxAttempts?: number }): Promise<LedgerSyncResult>;
  cancelSync(): Promise<LedgerSyncProgress>;
  clearCache(): Promise<void>;
  runIntegrityCheck(): Promise<{ ok: boolean; message: string }>;
  createBackup(): Promise<{ ok: boolean; backupPath: string | null; message: string }>;
}

export class LedgerSyncServiceImpl implements LedgerSyncService {
  private running = false;
  private readonly repositoryOverride?: LedgerRepositoryPort;
  private readonly syncRunsOverride?: SyncRunRepository;
  private readonly storage: SqliteStorageService;
  private progress: LedgerSyncProgress = createIdleProgress();
  private activeAbort: AbortController | null = null;
  private activeRun: LedgerSyncRunRecord | null = null;
  private syncInFlight: Promise<LedgerSyncResult> | null = null;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly readPort: ErpReadPort,
    private readonly companyResolver: CompanyResolver,
    private readonly connectorSession: ConnectorSessionService,
    private readonly logger: Logger,
    storage: SqliteStorageService,
    repository?: LedgerRepositoryPort,
    syncRuns?: SyncRunRepository,
  ) {
    this.storage = storage;
    this.repositoryOverride = repository;
    this.syncRunsOverride = syncRuns;
  }

  private get repository(): LedgerRepositoryPort {
    return this.repositoryOverride ?? this.storage.getBundle().ledgerRepository;
  }

  private get syncRuns(): SyncRunRepository {
    return this.syncRunsOverride ?? this.storage.getBundle().syncRunRepository;
  }

  async start(): Promise<void> {
    this.running = true;
    if (this.storage.isRunning()) {
      this.syncRuns.recoverAllAbandonedRuns();
    }
  }

  async stop(): Promise<void> {
    this.activeAbort?.abort();
    this.running = false;
  }

  isRunning(): boolean {
    return this.running;
  }

  getStatus(): ServiceStatus {
    return {
      name: 'LedgerSync',
      running: this.running,
      ready: this.running,
      message: this.progress.status,
    };
  }

  getStorageStatus(): StorageStatus {
    return this.storage.getStorageStatus();
  }

  async getLedgers(params: LedgerSearchParams): Promise<LedgerSearchResult> {
    const companyId = await this.requireCompanyId();
    return this.repository.search(companyId, params);
  }

  async getLedgerById(ledgerId: string): Promise<LedgerDetails | null> {
    const companyId = await this.requireCompanyId();
    return this.repository.findById(companyId, ledgerId);
  }

  async getStatistics(): Promise<LedgerStatistics> {
    const companyId = await this.requireCompanyId();
    return this.repository.getStatistics(companyId);
  }

  getSyncProgress(): LedgerSyncProgress {
    return { ...this.progress };
  }

  listSyncRuns(limit = 20): Promise<readonly LedgerSyncRunRecord[]> {
    return this.requireCompanyId().then((companyId) => this.syncRuns.listRuns(companyId, 'ledgers', limit));
  }

  getSyncRun(syncRunId: string): Promise<LedgerSyncRunRecord | null> {
    return this.requireCompanyId().then((companyId) => this.syncRuns.findById(companyId, syncRunId));
  }

  async cancelSync(): Promise<LedgerSyncProgress> {
    const cancellable =
      this.syncInFlight !== null &&
      this.activeRun !== null &&
      (this.activeRun.status === 'running' || this.activeRun.status === 'cancelling');
    if (cancellable && this.activeRun) {
      this.activeRun = {
        ...this.activeRun,
        cancelRequested: true,
        status: 'cancelling',
        updatedAt: new Date().toISOString(),
      };
      this.syncRuns.updateRun(this.activeRun);
      this.activeAbort?.abort();
      this.progress = {
        ...this.progress,
        cancelRequested: true,
        status: 'cancelling',
      };
    }
    return this.getSyncProgress();
  }

  async clearCache(): Promise<void> {
    const companyId = await this.requireCompanyId();
    await this.repository.clearCompany(companyId);
    this.logger.info('ledger_cache_cleared', { component: 'ledger-sync', companyId });
  }

  runIntegrityCheck(): Promise<{ ok: boolean; message: string }> {
    return Promise.resolve(this.storage.runIntegrityCheck());
  }

  createBackup(): Promise<{ ok: boolean; backupPath: string | null; message: string }> {
    const backupDir = `${this.config.databasePath}/backups`;
    return Promise.resolve(this.storage.createBackup(backupDir));
  }

  async syncLedgers(options: { incremental?: boolean; maxAttempts?: number } = {}): Promise<LedgerSyncResult> {
    if (this.syncInFlight) {
      throw new AppError(
        ErrorCodes.SYNC_CONFLICT,
        'A ledger sync is already running for this connector.',
        409,
      );
    }

    const companyId = await this.requireCompanyId();
    this.syncRuns.recoverAbandonedRuns(companyId, 'ledgers');
    const active = this.syncRuns.findActiveRun(companyId, 'ledgers');
    if (active) {
      throw new AppError(
        ErrorCodes.SYNC_CONFLICT,
        'A ledger sync is already active for the selected company.',
        409,
        { syncRunId: active.syncRunId },
      );
    }

    this.syncInFlight = this.executeSync(companyId, options).finally(() => {
      this.syncInFlight = null;
    });
    return this.syncInFlight;
  }

  private async executeSync(
    companyId: string,
    options: { incremental?: boolean; maxAttempts?: number },
  ): Promise<LedgerSyncResult> {
    const startedAt = Date.now();
    const companyName = await this.companyResolver.resolveName(companyId);
    const maxAttempts = options.maxAttempts ?? this.config.tallyRetryMaxAttempts;
    this.activeAbort = new AbortController();
    const signal = this.activeAbort.signal;

    this.activeRun = this.syncRuns.createRun({
      companyId,
      resourceKind: 'ledgers',
      syncType: options.incremental ? 'incremental' : 'full',
      connectorVersion: this.config.connectorVersion,
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });

    this.progress = toProgress(this.activeRun, this.storage.getStorageStatus().migrationStatus);
    const changes: LedgerChange[] = [];

    try {
      const extraction = await this.extractWithRetry(companyName, maxAttempts, signal);
      if (signal.aborted) {
        return this.finalizeRun('cancelled', companyId, startedAt, changes, 0);
      }

      const syncedAt = new Date().toISOString();
      const mapped = extraction.items.map((item) => mapNormalizedLedgerToDomain(item, syncedAt));
      const validation = validateLedgerCollection(mapped);
      this.activeRun = {
        ...this.activeRun,
        totalExpected: mapped.length,
        updatedAt: new Date().toISOString(),
      };
      this.syncRuns.updateRun(this.activeRun);

      const resumeFrom = this.activeRun.lastProcessedId;
      let startIndex = 0;
      if (resumeFrom) {
        startIndex = mapped.findIndex((ledger) => ledger.id === resumeFrom) + 1;
      }

      for (let index = startIndex; index < mapped.length; index += BATCH_SIZE) {
        if (signal.aborted) {
          break;
        }
        const batch = mapped.slice(index, index + BATCH_SIZE);
        const toUpsert: LedgerDetails[] = [];
        let batchInserted = 0;
        let batchUpdated = 0;
        let batchSkipped = 0;
        let lastId: string | null = null;

        for (const ledger of batch) {
          const existing = await this.repository.findById(companyId, ledger.id);
          if (!existing) {
            toUpsert.push(ledger);
            batchInserted += 1;
            changes.push({ ledgerId: ledger.id, changeType: 'added' });
          } else if (
            options.incremental &&
            computeLedgerFingerprint(existing) === computeLedgerFingerprint(ledger)
          ) {
            batchSkipped += 1;
            changes.push({ ledgerId: ledger.id, changeType: 'skipped', reason: 'unchanged' });
          } else {
            toUpsert.push(ledger);
            batchUpdated += 1;
            changes.push({ ledgerId: ledger.id, changeType: 'updated' });
          }
          lastId = ledger.id;
        }

        // Atomic batch: domain upserts + checkpoint counters commit together or roll back together.
        // Run creation and terminal status updates remain outside this boundary (deliberate).
        const currentRun = this.activeRun;
        if (!currentRun) {
          throw new AppError(ErrorCodes.INTERNAL_ERROR, 'Ledger sync run missing during batch commit.', 500);
        }
        const nextRun: LedgerSyncRunRecord = {
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
        this.activeRun = nextRun;
        this.progress = toProgress(nextRun, this.storage.getStorageStatus().migrationStatus, Date.now() - startedAt);
      }

      const finalStatus = signal.aborted ? 'cancelled' : 'completed';
      return this.finalizeRun(finalStatus, companyId, startedAt, changes, validation.issues.length);
    } catch (error) {
      if (error instanceof AppError && error.code === ErrorCodes.SYNC_CANCELLED) {
        return this.finalizeRun('cancelled', companyId, startedAt, changes, 0);
      }
      const message = error instanceof Error ? error.message : String(error);
      if (this.activeRun) {
        this.activeRun = {
          ...this.activeRun,
          status: 'failed',
          failureCode: error instanceof AppError ? error.code : ErrorCodes.SERVICE_UNAVAILABLE,
          failureSummary: message,
          updatedAt: new Date().toISOString(),
          completedAt: new Date().toISOString(),
        };
        this.syncRuns.updateRun(this.activeRun);
      }
      this.progress = {
        ...this.progress,
        status: 'failed',
        lastError: message,
        completedAt: new Date().toISOString(),
        durationMs: Date.now() - startedAt,
      };
      this.logger.error('ledger_sync_failed', { component: 'ledger-sync', message });
      this.activeRun = null;
      throw error instanceof AppError
        ? error
        : new AppError(ErrorCodes.SERVICE_UNAVAILABLE, message, 503, { feature: 'ledger-sync' });
    } finally {
      this.activeAbort = null;
    }
  }

  private async finalizeRun(
    status: LedgerSyncProgress['status'],
    companyId: string,
    startedAt: number,
    changes: LedgerChange[],
    validationIssueCount: number,
  ): Promise<LedgerSyncResult> {
    const finishedRun = this.activeRun;
    if (finishedRun) {
      this.activeRun = {
        ...finishedRun,
        status,
        completedAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      this.syncRuns.updateRun(this.activeRun);
    }
    this.progress = {
      ...(finishedRun
        ? toProgress(this.activeRun!, this.storage.getStorageStatus().migrationStatus, Date.now() - startedAt)
        : this.progress),
      status,
      completedAt: new Date().toISOString(),
      durationMs: Date.now() - startedAt,
    };
    const statistics = await this.repository.getStatistics(companyId);
    this.logger.info('ledger_sync_completed', {
      component: 'ledger-sync',
      status,
      durationMs: this.progress.durationMs,
      ledgerCount: statistics.totalLedgers,
    });
    const result: LedgerSyncResult = {
      syncRunId: finishedRun?.syncRunId ?? '',
      status,
      statistics,
      progress: this.getSyncProgress(),
      changes,
      validationIssueCount,
    };
    this.activeRun = null;
    return result;
  }

  private async extractWithRetry(companyName: string, maxAttempts: number, signal: AbortSignal) {
    let lastError: unknown;
    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      if (signal.aborted) {
        throw new AppError(ErrorCodes.SYNC_CANCELLED, 'Ledger extraction cancelled.', 499);
      }
      try {
        return await this.readPort.readLedgers(companyName, { signal });
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
      : new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Ledger extraction failed after retries.', 503);
  }

  private async requireCompanyId(): Promise<string> {
    if (!this.running) {
      throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Ledger sync service is not running.', 503);
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

function createIdleProgress(): LedgerSyncProgress {
  return {
    syncRunId: null,
    status: 'idle',
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
  run: LedgerSyncRunRecord,
  migrationStatus: StorageStatus['migrationStatus'],
  durationMs: number | null = null,
): LedgerSyncProgress {
  return {
    syncRunId: run.syncRunId,
    status: run.status,
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
    throw new AppError(ErrorCodes.SYNC_CANCELLED, 'Ledger extraction cancelled.', 499);
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    const onAbort = (): void => {
      clearTimeout(timer);
      signal.removeEventListener('abort', onAbort);
      reject(new AppError(ErrorCodes.SYNC_CANCELLED, 'Ledger extraction cancelled.', 499));
    };
    signal.addEventListener('abort', onAbort, { once: true });
  });
}
