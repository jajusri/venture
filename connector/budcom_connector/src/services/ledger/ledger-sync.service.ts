import type { ConnectorConfig } from '../../config/defaults.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import {
  normalizeSyncFailure,
  sanitizeSyncProgressError,
} from '../../infrastructure/privacy/sync-failure-normalizer.js';
import type { ServiceStatus } from '../../core/types.js';
import type {
  LedgerChange,
  LedgerContactDetailsBulkResult,
  LedgerDetails,
  LedgerSearchParams,
  LedgerSearchResult,
  LedgerStatistics,
  LedgerSyncProgress,
  LedgerSyncResult,
  LedgerSyncRunRecord,
  StorageStatus,
} from '../../erp/ledger/ledger-domain.js';
import {
  buildLedgerContact,
  buildLedgerGst,
  buildLedgerMailing,
  mapNormalizedLedgerToDomain,
} from '../../erp/ledger/ledger-mapper.js';
import { assessLedgerExtraction } from '../../erp/ledger/ledger-extraction-quality.js';
import { validateLedgerCollection } from '../../erp/ledger/ledger-validation.js';
import {
  assertLedgerMigrationQuality,
  assertLedgerMigrationValidation,
  companyNeedsLedgerIdentityMigration,
} from './ledger-cache-migration.js';
import { isLegacyLedgerId, LEDGER_IDENTITY_VERSION } from '../../extraction/core/ledger-identity.js';
import type { NormalizedLedger } from '../../extraction/core/types.js';
import {
  assessLegacyMigrationCoverage,
  toLegacyMigrationRow,
  toPrivacySafeMigrationCoverage,
} from './ledger-migration-coverage.js';
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
import type { VoucherRepositoryPort } from '../voucher/voucher-repository.interface.js';
import type { VoucherLedgerMovement } from '../../erp/voucher/voucher-ledger-movement.js';
import { businessTodayIso } from '../voucher/voucher-business-date.js';
import type { LedgerStatement, LedgerStatementTransaction } from '../../erp/ledger/ledger-statement-domain.js';
import {
  addDecimals,
  type AmountSide,
  type DecimalValue,
  sideFromSigned,
  signedFromSide,
  subtractDecimals,
  sumDecimals,
  ZERO_DECIMAL,
} from '../../erp/shared/decimal-money.js';

const BATCH_SIZE = 250;
const MAX_STATEMENT_RANGE_DAYS = 366;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export interface LedgerSyncService extends SyncEngineService {
  getLedgers(params: LedgerSearchParams): Promise<LedgerSearchResult>;
  getLedgerById(ledgerId: string): Promise<LedgerDetails | null>;
  getLedgerStatement(ledgerId: string, dateFrom: string, dateTo: string): Promise<LedgerStatement | null>;
  getStatistics(): Promise<LedgerStatistics>;
  getSyncProgress(): Promise<LedgerSyncProgress>;
  getStorageStatus(): StorageStatus;
  listSyncRuns(limit?: number): Promise<readonly LedgerSyncRunRecord[]>;
  getSyncRun(syncRunId: string): Promise<LedgerSyncRunRecord | null>;
  syncLedgers(options?: { incremental?: boolean; maxAttempts?: number }): Promise<LedgerSyncResult>;
  cancelSync(): Promise<LedgerSyncProgress>;
  clearCache(): Promise<void>;
  runIntegrityCheck(): Promise<{ ok: boolean; message: string }>;
  createBackup(): Promise<{ ok: boolean; backupPath: string | null; message: string }>;
  /**
   * Manually-triggered, occasional bulk fetch of mailing/contact/GST fields for every ledger in
   * one Tally round-trip (Connect address/email/GSTIN auto-population). Deliberately NOT part of
   * `syncLedgers`/the sync-run/progress/scheduler machinery above -- a single synchronous
   * request/response action, not a resumable batch sync. Entirely Party-agnostic; reconciling
   * against BUDCOM Parties is the Android client's job.
   */
  fetchLedgerContactDetailsBulk(): Promise<LedgerContactDetailsBulkResult>;
}

export class LedgerSyncServiceImpl implements LedgerSyncService {
  private running = false;
  private readonly repositoryOverride?: LedgerRepositoryPort;
  private readonly syncRunsOverride?: SyncRunRepository;
  private readonly storage: SqliteStorageService;
  // Company-scoped: a process-wide singleton here would let one company's sync state (progress,
  // active run, in-flight guard) bleed into another's status poll, or let a legitimate sync for
  // Company B be rejected as "already running" purely because Company A's sync happens to be in
  // flight in the same process. See TD-036.
  private readonly progressByCompany = new Map<string, LedgerSyncProgress>();
  private readonly activeAbortByCompany = new Map<string, AbortController>();
  private readonly activeRunByCompany = new Map<string, LedgerSyncRunRecord>();
  private readonly syncInFlightByCompany = new Map<string, Promise<LedgerSyncResult>>();

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

  private get voucherRepository(): VoucherRepositoryPort {
    return this.storage.getBundle().voucherRepository;
  }

  private get syncRuns(): SyncRunRepository {
    return this.syncRunsOverride ?? this.storage.getBundle().syncRunRepository;
  }

  private progressFor(companyId: string): LedgerSyncProgress {
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
      name: 'LedgerSync',
      running: this.running,
      ready: this.running,
      message: this.syncInFlightByCompany.size > 0 ? 'running' : 'idle',
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

  async getLedgerStatement(
    ledgerId: string,
    dateFrom: string,
    dateTo: string,
  ): Promise<LedgerStatement | null> {
    if (!this.running) {
      throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Ledger sync service is not running.', 503);
    }
    validateStatementDateRange(dateFrom, dateTo);
    const companyId = await this.requireCompanyId();
    const ledger = await this.repository.findById(companyId, ledgerId);
    if (!ledger) return null;

    const syncedDate = businessTodayIso(new Date(ledger.syncedAt));
    const snapshot = await this.voucherRepository.getActiveSnapshotMetadata(companyId);

    const requiredTo = syncedDate > dateTo ? syncedDate : dateTo;
    const transactionsComplete = Boolean(
      snapshot && snapshot.period.dateFrom <= dateFrom && snapshot.period.dateTo >= dateTo,
    );
    const balanceCoverageComplete = Boolean(
      snapshot && snapshot.period.dateFrom <= dateFrom && snapshot.period.dateTo >= requiredTo,
    );
    const balanceAnchorAvailable = syncedDate >= dateFrom;
    const balanceAvailable = balanceAnchorAvailable && balanceCoverageComplete && Boolean(ledger.closingBalance);

    const visibleFrom = transactionsComplete || !snapshot
      ? dateFrom
      : maxDateIso(dateFrom, snapshot.period.dateFrom);
    const visibleTo = transactionsComplete || !snapshot
      ? dateTo
      : minDateIso(dateTo, snapshot.period.dateTo);
    const movements = snapshot && visibleFrom <= visibleTo
      ? await this.voucherRepository.findLedgerMovements(companyId, ledger.name, visibleFrom, visibleTo)
      : [];

    let openingBalance: { readonly amount: string; readonly side: AmountSide } | null = null;
    let closingBalance: { readonly amount: string; readonly side: AmountSide } | null = null;
    let runningByLine: ReadonlyMap<string, DecimalValue> | null = null;

    if (balanceAvailable && ledger.closingBalance) {
      const closingNow = signedFromSide(ledger.closingBalance.amount, ledger.closingBalance.side);
      const movementsToSynced = dateFrom <= syncedDate
        ? await this.voucherRepository.findLedgerMovements(companyId, ledger.name, dateFrom, syncedDate)
        : [];
      const netToSynced = sumDecimals(movementsToSynced.map(movementAsSigned));
      const openingDecimal = subtractDecimals(closingNow, netToSynced);
      openingBalance = sideFromSigned(openingDecimal);

      const netInPeriod = sumDecimals(movements.map(movementAsSigned));
      const closingDecimal = addDecimals(openingDecimal, netInPeriod);
      closingBalance = sideFromSigned(closingDecimal);

      const running = new Map<string, DecimalValue>();
      let cursor = openingDecimal;
      for (const movement of movements) {
        cursor = addDecimals(cursor, movementAsSigned(movement));
        running.set(runningKey(movement), cursor);
      }
      runningByLine = running;
    }

    const transactions: LedgerStatementTransaction[] = movements.map((movement) => ({
      voucherId: movement.voucherId,
      date: movement.date,
      voucherType: movement.voucherType,
      voucherNumber: movement.voucherNumber,
      referenceNumber: movement.referenceNumber,
      narration: movement.narration,
      debit: movement.amountSide === 'debit' ? movement.amount : null,
      credit: movement.amountSide === 'credit' ? movement.amount : null,
      runningBalance: runningByLine
        ? sideFromSigned(runningByLine.get(runningKey(movement)) ?? ZERO_DECIMAL)
        : null,
    }));

    const messages: string[] = [];
    if (!transactionsComplete) {
      messages.push(
        'Some transactions in this period may be missing from the statement — sync vouchers for '
        + 'this date range to complete it.',
      );
    }
    if (!balanceAvailable) {
      messages.push(
        balanceAnchorAvailable
          ? 'Opening/closing balance unavailable — sync vouchers for this date range to compute it.'
          : 'Opening/closing balance unavailable: the selected period ends before this ledger was last synced.',
      );
    }

    return {
      ledgerId: ledger.id,
      ledgerName: ledger.name,
      parentGroup: ledger.parentGroup,
      period: { from: dateFrom, to: dateTo },
      openingBalance,
      closingBalance,
      transactions,
      coverage: {
        transactionsComplete,
        balanceAvailable,
        syncedFrom: snapshot?.period.dateFrom ?? null,
        syncedTo: snapshot?.period.dateTo ?? null,
        message: messages.length > 0 ? messages.join(' ') : null,
      },
    };
  }

  async getStatistics(): Promise<LedgerStatistics> {
    const companyId = await this.requireCompanyId();
    return this.repository.getStatistics(companyId);
  }

  async getSyncProgress(): Promise<LedgerSyncProgress> {
    const companyId = this.peekCompanyId();
    if (!companyId) return createIdleProgress('');
    return { ...this.progressFor(companyId) };
  }

  listSyncRuns(limit = 20): Promise<readonly LedgerSyncRunRecord[]> {
    return this.requireCompanyId().then((companyId) => this.syncRuns.listRuns(companyId, 'ledgers', limit));
  }

  getSyncRun(syncRunId: string): Promise<LedgerSyncRunRecord | null> {
    return this.requireCompanyId().then((companyId) => this.syncRuns.findById(companyId, syncRunId));
  }

  async cancelSync(): Promise<LedgerSyncProgress> {
    const companyId = this.peekCompanyId();
    if (!companyId) return createIdleProgress('');
    const activeRun = this.activeRunByCompany.get(companyId);
    const cancellable =
      this.syncInFlightByCompany.has(companyId) &&
      activeRun !== undefined &&
      (activeRun.status === 'running' || activeRun.status === 'cancelling');
    if (cancellable && activeRun) {
      const updatedRun: LedgerSyncRunRecord = {
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
    this.logger.info('ledger_cache_cleared', { component: 'ledger-sync', companyId });
  }

  runIntegrityCheck(): Promise<{ ok: boolean; message: string }> {
    return Promise.resolve(this.storage.runIntegrityCheck());
  }

  createBackup(): Promise<{ ok: boolean; backupPath: string | null; message: string }> {
    const backupDir = `${this.config.databasePath}/backups`;
    return Promise.resolve(this.storage.createBackup(backupDir));
  }

  async fetchLedgerContactDetailsBulk(): Promise<LedgerContactDetailsBulkResult> {
    const companyId = await this.requireCompanyId();
    const companyName = await this.companyResolver.resolveName(companyId);
    const started = Date.now();

    const extraction = await this.readPort.readLedgerContactDetails(companyName);

    const patches = extraction.items.map((item) => ({
      ledgerId: item.id,
      mailing: buildLedgerMailing(item),
      contact: buildLedgerContact(item),
      gst: buildLedgerGst(item),
    }));
    const { updated, skipped } = await this.repository.updateContactDetailsMany(companyId, patches);

    return {
      requestedAt: new Date().toISOString(),
      durationMs: Date.now() - started,
      ledgerCount: extraction.items.length,
      updatedCount: updated,
      skippedCount: skipped,
      items: extraction.items.map((item) => ({
        ledgerId: item.id,
        mobile: item.mobile,
        email: item.email,
        address: item.address,
        state: item.state,
        pincode: item.pincode,
        gstin: item.gstin,
      })),
    };
  }

  async syncLedgers(options: { incremental?: boolean; maxAttempts?: number } = {}): Promise<LedgerSyncResult> {
    const companyId = await this.requireCompanyId();
    if (this.syncInFlightByCompany.has(companyId)) {
      throw new AppError(
        ErrorCodes.SYNC_CONFLICT,
        'A ledger sync is already running for the selected company.',
        409,
      );
    }

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

    const inFlight = this.executeSync(companyId, options).finally(() => {
      this.syncInFlightByCompany.delete(companyId);
    });
    this.syncInFlightByCompany.set(companyId, inFlight);
    return inFlight;
  }

  private async executeSync(
    companyId: string,
    options: { incremental?: boolean; maxAttempts?: number },
  ): Promise<LedgerSyncResult> {
    const startedAt = Date.now();
    const companyName = await this.companyResolver.resolveName(companyId);
    const maxAttempts = options.maxAttempts ?? this.config.tallyRetryMaxAttempts;
    const abort = new AbortController();
    this.activeAbortByCompany.set(companyId, abort);
    const signal = abort.signal;

    const initialRun = this.syncRuns.createRun({
      companyId,
      resourceKind: 'ledgers',
      syncType: options.incremental ? 'incremental' : 'full',
      connectorVersion: this.config.connectorVersion,
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
      predecessorSyncRunId: this.syncRuns.findRetryPredecessor(companyId, 'ledgers')?.syncRunId ?? null,
    });
    this.activeRunByCompany.set(companyId, initialRun);

    this.progressByCompany.set(companyId, toProgress(initialRun, this.storage.getStorageStatus().migrationStatus));
    const changes: LedgerChange[] = [];

    try {
      const extraction = await this.extractWithRetry(companyName, maxAttempts, signal);
      if (signal.aborted) {
        return this.finalizeRun('cancelled', companyId, startedAt, changes, 0);
      }

      const assessment = assessLedgerExtraction(extraction.items);
      if (assessment.quality === 'invalid') {
        throw new AppError(
          ErrorCodes.VALIDATION_ERROR,
          assessment.reason ?? 'Ledger extraction contract failure: shallow or unusable export.',
          502,
          { extractionQuality: assessment.quality },
        );
      }

      const syncedAt = new Date().toISOString();
      const mapped = mapExtractedLedgers(extraction.items, syncedAt, assessment.quality);
      const validation = validateLedgerCollection(mapped);

      const identityVersion = await this.repository.getLedgerIdentityVersion(companyId);
      const hasLegacyLedgerIds = await this.repository.hasLegacyLedgerIds(companyId);
      const ledgerCount = await this.repository.countByCompany(companyId);
      const needsIdentityMigration = companyNeedsLedgerIdentityMigration(
        identityVersion,
        hasLegacyLedgerIds,
        ledgerCount,
      );

      if (needsIdentityMigration) {
        return this.executeIdentityMigrationRebuild({
          companyId,
          startedAt,
          changes,
          assessment,
          validation,
          mapped,
          signal,
        });
      }

      if (!validation.ok) {
        this.logger.warn('ledger_validation_issues', {
          component: 'ledger-sync',
          issueCount: validation.issues.length,
        });
      }
      const runWithTotal: LedgerSyncRunRecord = {
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
        const currentRun = this.activeRunByCompany.get(companyId);
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
        this.activeRunByCompany.set(companyId, nextRun);
        this.progressByCompany.set(
          companyId,
          toProgress(nextRun, this.storage.getStorageStatus().migrationStatus, Date.now() - startedAt),
        );
      }

      const finalStatus = signal.aborted ? 'cancelled' : 'completed';
      const result = await this.finalizeRun(finalStatus, companyId, startedAt, changes, validation.issues.length);
      await this.markLedgerIdentityVersionIfNeeded(companyId, identityVersion);
      return result;
    } catch (error) {
      if (error instanceof AppError && error.code === ErrorCodes.SYNC_CANCELLED) {
        return this.finalizeRun('cancelled', companyId, startedAt, changes, 0);
      }
      const normalizedFailure = normalizeSyncFailure(error);
      const runOnFailure = this.activeRunByCompany.get(companyId);
      if (runOnFailure) {
        const failedRun: LedgerSyncRunRecord = {
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
      this.logger.error('ledger_sync_failed', {
        component: 'ledger-sync',
        code: normalizedFailure.failureCode,
        reasonCode: normalizedFailure.failureSummary,
      });
      this.activeRunByCompany.delete(companyId);
      throw error instanceof AppError
        ? error
        : new AppError(
            ErrorCodes.SERVICE_UNAVAILABLE,
            error instanceof Error ? error.message : 'Ledger sync failed',
            503,
            { feature: 'ledger-sync' },
          );
    } finally {
      this.activeAbortByCompany.delete(companyId);
    }
  }

  private async finalizeRun(
    status: LedgerSyncProgress['status'],
    companyId: string,
    startedAt: number,
    changes: LedgerChange[],
    validationIssueCount: number,
  ): Promise<LedgerSyncResult> {
    const finishedRun = this.activeRunByCompany.get(companyId);
    let completedRun: LedgerSyncRunRecord | undefined;
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
    this.logger.info('ledger_sync_completed', {
      component: 'ledger-sync',
      status,
      durationMs: this.progressFor(companyId).durationMs,
      ledgerCount: statistics.totalLedgers,
    });
    const result: LedgerSyncResult = {
      syncRunId: finishedRun?.syncRunId ?? '',
      status,
      statistics,
      // The result belongs to this run's own companyId, not whichever company happens to be
      // currently selected by the time this resolves — read the map directly rather than going
      // through getSyncProgress()'s requireCompanyId() re-resolution.
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

  private async executeIdentityMigrationRebuild(input: {
    readonly companyId: string;
    readonly startedAt: number;
    readonly changes: LedgerChange[];
    readonly assessment: ReturnType<typeof assessLedgerExtraction>;
    readonly validation: ReturnType<typeof validateLedgerCollection>;
    readonly mapped: LedgerDetails[];
    readonly signal: AbortSignal;
  }): Promise<LedgerSyncResult> {
    const { companyId, startedAt, changes, assessment, validation, mapped, signal } = input;
    if (signal.aborted) {
      return this.finalizeRun('cancelled', companyId, startedAt, changes, 0);
    }

    const backup = await this.createBackup();
    if (!backup.ok) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Ledger identity migration requires a successful backup before cache replacement.',
        503,
      );
    }

    try {
      assertLedgerMigrationQuality({ assessment });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new AppError(ErrorCodes.VALIDATION_ERROR, message, 422, {
        extractionQuality: assessment.quality,
      });
    }

    const legacyRows = await this.loadLegacyMigrationRows(companyId);
    const coverage = assessLegacyMigrationCoverage(legacyRows, mapped);
    if (!coverage.safeToReplace) {
      throw new AppError(
        ErrorCodes.VALIDATION_ERROR,
        coverage.message ?? 'Ledger identity migration coverage check failed.',
        422,
        {
          extractionQuality: assessment.quality,
          migrationCoverage: toPrivacySafeMigrationCoverage(coverage),
        },
      );
    }
    this.logger.info('ledger_identity_migration_coverage', {
      component: 'ledger-sync',
      companyId,
      ...toPrivacySafeMigrationCoverage(coverage),
    });

    try {
      assertLedgerMigrationValidation({ validation, ledgers: mapped });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new AppError(ErrorCodes.VALIDATION_ERROR, message, 422, {
        extractionQuality: assessment.quality,
        migrationCoverage: toPrivacySafeMigrationCoverage(coverage),
      });
    }

    for (const ledger of mapped) {
      changes.push({ ledgerId: ledger.id, changeType: 'added' });
    }

    const currentRun = this.activeRunByCompany.get(companyId);
    if (!currentRun) {
      throw new AppError(ErrorCodes.INTERNAL_ERROR, 'Ledger sync run missing during identity migration.', 500);
    }
    const nextRun: LedgerSyncRunRecord = {
      ...currentRun,
      totalExpected: mapped.length,
      processed: mapped.length,
      inserted: mapped.length,
      updated: 0,
      skipped: 0,
      lastProcessedId: mapped.at(-1)?.id ?? null,
      updatedAt: new Date().toISOString(),
    };

    await this.storage.runInTransaction(() => {
      void this.repository.replaceCompanyLedgersAtomically(companyId, mapped);
      this.syncRuns.updateRun(nextRun);
    });
    this.activeRunByCompany.set(companyId, nextRun);
    this.progressByCompany.set(
      companyId,
      toProgress(nextRun, this.storage.getStorageStatus().migrationStatus, Date.now() - startedAt),
    );

    this.logger.info('ledger_identity_migration_completed', {
      component: 'ledger-sync',
      companyId,
      ledgerCount: mapped.length,
      extractionQuality: assessment.quality,
      backupPath: backup.backupPath,
    });

    return this.finalizeRun('completed', companyId, startedAt, changes, 0);
  }

  private async loadLegacyMigrationRows(companyId: string) {
    const pageSize = 10_000;
    const result = await this.repository.search(companyId, { page: 1, pageSize });
    return result.items
      .filter((item) => isLegacyLedgerId(item.id))
      .map((item) => toLegacyMigrationRow(item))
      .filter((row): row is NonNullable<typeof row> => row !== null);
  }

  private async markLedgerIdentityVersionIfNeeded(companyId: string, currentVersion: number): Promise<void> {
    if (currentVersion >= LEDGER_IDENTITY_VERSION) {
      return;
    }
    const hasLegacyLedgerIds = await this.repository.hasLegacyLedgerIds(companyId);
    if (hasLegacyLedgerIds) {
      return;
    }
    const ledgerCount = await this.repository.countByCompany(companyId);
    if (ledgerCount === 0) {
      return;
    }
    await this.repository.markLedgerIdentityCurrent(companyId);
  }
}

function mapExtractedLedgers(
  items: readonly NormalizedLedger[],
  syncedAt: string,
  collectionQuality: 'complete' | 'partial',
): LedgerDetails[] {
  return items.map((item) => {
    const itemQuality = item.guid?.trim()
      ? collectionQuality === 'partial'
        ? 'partial'
        : 'complete'
      : 'partial';
    return mapNormalizedLedgerToDomain({ ...item, dataQuality: itemQuality }, syncedAt, itemQuality);
  });
}

function createIdleProgress(companyId: string): LedgerSyncProgress {
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
  run: LedgerSyncRunRecord,
  migrationStatus: StorageStatus['migrationStatus'],
  durationMs: number | null = null,
): LedgerSyncProgress {
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

function validateStatementDateRange(dateFrom: string, dateTo: string): void {
  if (!ISO_DATE_PATTERN.test(dateFrom) || !ISO_DATE_PATTERN.test(dateTo)) {
    throw new AppError(ErrorCodes.VALIDATION_ERROR, 'Ledger statement dates must use YYYY-MM-DD.', 400);
  }
  if (dateFrom > dateTo) {
    throw new AppError(
      ErrorCodes.VALIDATION_ERROR,
      'Ledger statement "from" date must not be after "to".',
      400,
    );
  }
  const fromMs = Date.parse(`${dateFrom}T00:00:00Z`);
  const toMs = Date.parse(`${dateTo}T00:00:00Z`);
  if (Math.floor((toMs - fromMs) / 86_400_000) + 1 > MAX_STATEMENT_RANGE_DAYS) {
    throw new AppError(
      ErrorCodes.VALIDATION_ERROR,
      `Ledger statement date range exceeds ${MAX_STATEMENT_RANGE_DAYS} days.`,
      400,
    );
  }
}

function maxDateIso(a: string, b: string): string {
  return a > b ? a : b;
}

function minDateIso(a: string, b: string): string {
  return a < b ? a : b;
}

function movementAsSigned(movement: VoucherLedgerMovement): DecimalValue {
  if (!movement.amountSide) return ZERO_DECIMAL;
  return signedFromSide(movement.amount, movement.amountSide);
}

/**
 * A movement's own composite storage key (voucherId + lineNumber) — unique within a single
 * ledger's statement, so it's safe to use as the running-balance lookup key even though the
 * same voucher can contribute more than one line to the same ledger.
 */
function runningKey(movement: VoucherLedgerMovement): string {
  return `${movement.voucherId}:${movement.lineNumber}`;
}
