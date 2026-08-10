import { randomUUID } from 'node:crypto';
import type { VoucherDetails } from '../../erp/voucher/voucher-domain.js';
import type { VoucherExtractionResult } from '../../erp/ports/vouchers.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { VoucherRepositoryError } from '../../storage/sqlite/sqlite-voucher-repository.js';
import type {
  VoucherSnapshotSyncRequest,
  VoucherSnapshotSyncService,
} from './voucher-application.interface.js';
import { VoucherExtractionService } from './voucher-extraction.service.js';
import type { VoucherRepositoryPort, VoucherSnapshotMetrics } from './voucher-repository.interface.js';
import type {
  VoucherSyncCancellation,
  VoucherSyncProgress,
  VoucherSyncProgressObserver,
  VoucherSyncRollbackStatus,
  VoucherSyncState,
  VoucherSynchronizationResult,
} from './voucher-sync-progress.js';

const STAGING_BATCH_SIZE = 100;
const RESERVATION_LEASE_MS = 15 * 60 * 1_000;

class VoucherSyncFailure extends Error {
  constructor(
    readonly reason: string,
    readonly repositoryCode: string | null = null,
  ) {
    super(reason);
    this.name = 'VoucherSyncFailure';
  }
}

class VoucherSyncCancelled extends Error {
  constructor() {
    super('cancelled');
    this.name = 'VoucherSyncCancelled';
  }
}

export type ResolveVoucherCompanyName = (companyId: string) => Promise<string>;

export class VoucherSynchronizationService implements VoucherSnapshotSyncService {
  constructor(
    private readonly extraction: VoucherExtractionService,
    private readonly repository: VoucherRepositoryPort,
    private readonly stagingBatchSize = STAGING_BATCH_SIZE,
    private readonly logger?: Logger,
    private readonly reservationLeaseMs = RESERVATION_LEASE_MS,
    /**
     * Resolves Connector company IDs to Tally company names for extraction.
     * Storage and progress remain keyed by companyId.
     */
    private readonly resolveCompanyName?: ResolveVoucherCompanyName,
  ) {}

  async synchronize(
    request: VoucherSnapshotSyncRequest,
    observer: VoucherSyncProgressObserver | readonly VoucherSyncProgressObserver[],
    cancellation: VoucherSyncCancellation,
  ): Promise<VoucherSynchronizationResult> {
    const startedMs = Date.now();
    const startedAt = new Date(startedMs).toISOString();
    const company = request.companyId.trim();
    const period = { dateFrom: request.dateFrom, dateTo: request.dateTo };
    const metrics = createMetrics();
    const phaseDurations: Partial<Record<VoucherSyncState, number>> = {};
    let phase: VoucherSyncState | null = null;
    let phaseStartedAt = Date.now();
    let snapshotId: string | null = null;
    let extractionResult: VoucherExtractionResult | null = null;
    let persistedVoucherCount = 0;
    let snapshotStarted = false;
    let promotionOccurred = false;
    let reservationAcquired = false;
    let notificationFailureCount = 0;
    const reservationOwnerId = randomUUID();
    const observers = Array.isArray(observer) ? observer : [observer];

    const emit = async (
      nextPhase: VoucherSyncState,
      processed = 0,
      total: number | null = null,
    ): Promise<void> => {
      const now = Date.now();
      if (phase) phaseDurations[phase] = (phaseDurations[phase] ?? 0) + now - phaseStartedAt;
      phase = nextPhase;
      phaseStartedAt = now;
      const progress: VoucherSyncProgress = {
        snapshotId,
        companyId: company,
        period,
        phase: nextPhase,
        processed,
        total,
      };
      const notifications = await Promise.allSettled(
        observers.map((subscriber) => Promise.resolve().then(() => subscriber.onProgress(progress))),
      );
      for (const notification of notifications) {
        if (notification.status === 'fulfilled') continue;
        notificationFailureCount += 1;
        try {
          this.logger?.warn('voucher_sync_observer_failed', {
            phase: nextPhase,
            notificationFailureCount,
            errorName:
              notification.reason instanceof Error ? notification.reason.name : 'UnknownError',
          });
        } catch {
          // Logging is observational and must never affect synchronization.
        }
      }
    };

    const finishPhase = (): void => {
      if (phase) {
        phaseDurations[phase] = (phaseDurations[phase] ?? 0) + Date.now() - phaseStartedAt;
      }
    };

    try {
      await emit('validating');
      validateRequest(company, request.dateFrom, request.dateTo);
      checkCancellation(cancellation);
      const acquiredAt = new Date();
      reservationAcquired = await this.repository.acquireSyncReservation(
        company,
        reservationOwnerId,
        acquiredAt.toISOString(),
        new Date(acquiredAt.getTime() + this.reservationLeaseMs).toISOString(),
      );
      if (!reservationAcquired) {
        finishPhase();
        return result({
          logger: this.logger,
          company,
          period,
          outcome: 'conflict',
          phaseDurations,
          failureReason: 'already_running',
          notificationFailureCount,
          startedAt,
          startedMs,
        });
      }

      const staleSnapshots = await this.repository.listSnapshots(company);
      for (const stale of staleSnapshots) {
        if (!['Pending', 'Writing', 'Validated'].includes(stale.status)) continue;
        await this.repository.rollbackSnapshot(company, stale.snapshotId);
      }
      // A matching requested period is never treated as evidence Tally content is unchanged —
      // every explicit sync performs a real live extraction for its requested window. Coverage
      // outside that window is preserved by carrying it forward from the active snapshot below,
      // so the new snapshot is always a complete authoritative replacement, not a partial one.
      const active = await this.repository.getActiveSnapshotMetadata(company);
      const storedPeriod = active
        ? {
            dateFrom: active.period.dateFrom < period.dateFrom ? active.period.dateFrom : period.dateFrom,
            dateTo: active.period.dateTo > period.dateTo ? active.period.dateTo : period.dateTo,
          }
        : period;
      snapshotId = randomUUID();
      await this.repository.createSnapshot(company, snapshotId, storedPeriod);
      snapshotStarted = true;

      await emit('extracting');
      checkCancellation(cancellation);
      try {
        const companyName = this.resolveCompanyName
          ? await this.resolveCompanyName(company)
          : company;
        extractionResult = await this.extraction.extract({
          companyName,
          period,
          signal: cancellation.signal,
        });
      } catch (error) {
        if (error instanceof AppError && error.code === ErrorCodes.SYNC_CANCELLED) {
          throw new VoucherSyncCancelled();
        }
        throw new VoucherSyncFailure(classifyExtractionFailure(error));
      }
      checkCancellation(cancellation);

      metrics.candidateVoucherCount = extractionResult.candidateRecordCount;
      metrics.acceptedVoucherCount = extractionResult.items.length;
      metrics.rejectedVoucherCount = extractionResult.droppedRecordCount;
      metrics.incompleteVoucherCount = extractionResult.items.filter(
        (voucher) => voucher.dataQuality === 'incomplete',
      ).length;
      metrics.ledgerEntryCount = sum(extractionResult.items, (voucher) => voucher.ledgerEntries.length);
      metrics.inventoryEntryCount = sum(
        extractionResult.items,
        (voucher) => voucher.inventoryEntries.length,
      );
      metrics.allocationCount = sum(extractionResult.items, countAllocations);
      assessExtraction(extractionResult);

      await emit('staging', 0, extractionResult.items.length);
      for (let offset = 0; offset < extractionResult.items.length; offset += this.stagingBatchSize) {
        checkCancellation(cancellation);
        const batch = extractionResult.items.slice(offset, offset + this.stagingBatchSize);
        await this.repository.writeVoucherBatch(company, snapshotId, batch);
        persistedVoucherCount += batch.length;
        await emit('staging', offset + batch.length, extractionResult.items.length);
      }
      checkCancellation(cancellation);

      if (active) {
        checkCancellation(cancellation);
        const carriedForward = await this.repository.carryForwardVouchersOutsideWindow(
          company,
          active.snapshotId,
          snapshotId,
          period.dateFrom,
          period.dateTo,
        );
        persistedVoucherCount += carriedForward.voucherCount;
        metrics.acceptedVoucherCount += carriedForward.voucherCount;
        metrics.ledgerEntryCount += carriedForward.ledgerEntryCount;
        metrics.inventoryEntryCount += carriedForward.inventoryEntryCount;
        metrics.allocationCount += carriedForward.allocationCount;
        await emit('staging', extractionResult.items.length, extractionResult.items.length);
      }
      checkCancellation(cancellation);

      await emit('validating_snapshot', extractionResult.items.length, extractionResult.items.length);
      checkCancellation(cancellation);
      const stored = await this.repository.getSnapshotMetrics(company, snapshotId);
      assertStoredMetrics(metrics, stored);
      await this.repository.finalizeSnapshot(company, snapshotId, new Date().toISOString());
      checkCancellation(cancellation);

      await emit('promoting', extractionResult.items.length, extractionResult.items.length);
      checkCancellation(cancellation);
      await this.repository.promoteSnapshot(company, snapshotId, new Date().toISOString());
      promotionOccurred = true;

      await emit('completed', extractionResult.items.length, extractionResult.items.length);
      finishPhase();
      return result({
        logger: this.logger,
        company,
        period,
        snapshotId,
        outcome: 'completed',
        responseStatus: extractionResult.responseStatus,
        metrics,
        phaseDurations,
        validationIssues: extractionResult.validationIssues,
        promotionOccurred: true,
        notificationFailureCount,
        rollbackStatus: 'not_required',
        startedAt,
        startedMs,
        persistedVoucherCount,
      });
    } catch (error) {
      const cancelled = error instanceof VoucherSyncCancelled;
      const repositoryCode = error instanceof VoucherRepositoryError
        ? error.code
        : error instanceof VoucherSyncFailure ? error.repositoryCode : null;
      const failureReason = cancelled ? 'cancelled' : classifyFailure(error);
      let rollbackStatus: VoucherSyncRollbackStatus = 'not_required';
      if (snapshotStarted && !promotionOccurred && snapshotId) {
        try {
          await this.repository.rollbackSnapshot(company, snapshotId);
          rollbackStatus = 'completed';
        } catch {
          rollbackStatus = 'failed';
        }
      }
      await emit(cancelled ? 'cancelled' : 'failed', metrics.acceptedVoucherCount, null);
      finishPhase();
      return result({
        logger: this.logger,
        company,
        period,
        snapshotId,
        outcome: cancelled ? 'cancelled' : 'failed',
        responseStatus: extractionResult?.responseStatus ?? null,
        metrics,
        phaseDurations,
        validationIssues: extractionResult?.validationIssues ?? [],
        repositoryFailureCode: repositoryCode,
        previousActiveSnapshotPreserved: !promotionOccurred,
        promotionOccurred,
        failureReason,
        notificationFailureCount,
        rollbackStatus,
        startedAt,
        startedMs,
        persistedVoucherCount,
      });
    } finally {
      if (reservationAcquired) {
        try {
          await this.repository.releaseSyncReservation(company, reservationOwnerId);
        } catch (error) {
          try {
            this.logger?.error('voucher_sync_reservation_release_failed', {
              errorName: error instanceof Error ? error.name : 'UnknownError',
            });
          } catch {
            // Logging is observational and must never change the completed result.
          }
        }
      }
    }
  }
}

function validateRequest(company: string, dateFrom: string, dateTo: string): void {
  if (!company) throw new VoucherSyncFailure('invalid_company');
  if (!isDate(dateFrom) || !isDate(dateTo) || dateFrom > dateTo) {
    throw new VoucherSyncFailure('invalid_period');
  }
}

function isDate(value: string): boolean {
  const compact = /^\d{8}$/.test(value)
    ? value
    : /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.replaceAll('-', '') : '';
  if (!compact) return false;
  const year = Number(compact.slice(0, 4));
  const month = Number(compact.slice(4, 6));
  const day = Number(compact.slice(6, 8));
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day;
}

function assessExtraction(extraction: VoucherExtractionResult): void {
  if (extraction.responseStatus === 'partial') throw new VoucherSyncFailure('partial_response');
  if (extraction.droppedRecordCount > 0) throw new VoucherSyncFailure('rejected_records');
  if (extraction.candidateRecordCount !== extraction.items.length + extraction.droppedRecordCount) {
    throw new VoucherSyncFailure('candidate_count_mismatch');
  }
  if (extraction.validationIssues.some((issue) =>
    issue.classification === 'rejected' || issue.classification === 'contract-conflict'
  )) {
    throw new VoucherSyncFailure('fatal_validation_issues');
  }
}

function assertStoredMetrics(
  expected: MutableMetrics,
  stored: VoucherSnapshotMetrics,
): void {
  if (
    stored.voucherCount !== expected.acceptedVoucherCount ||
    stored.ledgerEntryCount !== expected.ledgerEntryCount ||
    stored.inventoryEntryCount !== expected.inventoryEntryCount ||
    stored.allocationCount !== expected.allocationCount
  ) {
    throw new VoucherSyncFailure('stored_count_mismatch', 'SNAPSHOT_INCOMPLETE');
  }
}

function checkCancellation(cancellation: VoucherSyncCancellation): void {
  if (cancellation.requested || cancellation.signal?.aborted) throw new VoucherSyncCancelled();
}

function countAllocations(voucher: VoucherDetails): number {
  return voucher.allocations.length +
    sum(voucher.ledgerEntries, (entry) => entry.allocations.length) +
    sum(voucher.inventoryEntries, (entry) => entry.allocations.length);
}

function sum<T>(items: readonly T[], selector: (item: T) => number): number {
  return items.reduce((total, item) => total + selector(item), 0);
}

interface MutableMetrics {
  candidateVoucherCount: number;
  acceptedVoucherCount: number;
  rejectedVoucherCount: number;
  incompleteVoucherCount: number;
  ledgerEntryCount: number;
  inventoryEntryCount: number;
  allocationCount: number;
}

function createMetrics(): MutableMetrics {
  return {
    candidateVoucherCount: 0,
    acceptedVoucherCount: 0,
    rejectedVoucherCount: 0,
    incompleteVoucherCount: 0,
    ledgerEntryCount: 0,
    inventoryEntryCount: 0,
    allocationCount: 0,
  };
}

function classifyFailure(error: unknown): string {
  if (error instanceof VoucherSyncFailure) return error.reason;
  if (error instanceof VoucherRepositoryError) return 'repository_failure';
  return 'unexpected_exception';
}

function classifyExtractionFailure(error: unknown): string {
  if (error instanceof AppError) {
    const reasonCode = typeof error.details?.reasonCode === 'string'
      ? error.details.reasonCode.toLowerCase()
      : '';
    if (reasonCode.includes('source')) return 'tally_source_error';
    if (error.code === ErrorCodes.VALIDATION_ERROR) return 'parser_failure';
    if (error.code === ErrorCodes.SERVICE_UNAVAILABLE) return 'transport_failure';
  }
  if (error instanceof Error && error.name.includes('Parser')) return 'parser_failure';
  return 'extraction_failure';
}

function result(input: {
  logger?: Logger;
  company: string;
  period: { dateFrom: string; dateTo: string };
  snapshotId?: string | null;
  outcome: VoucherSynchronizationResult['outcome'];
  responseStatus?: VoucherSynchronizationResult['responseStatus'];
  metrics?: MutableMetrics;
  phaseDurations: Partial<Record<VoucherSyncState, number>>;
  validationIssues?: VoucherSynchronizationResult['validationIssues'];
  repositoryFailureCode?: string | null;
  previousActiveSnapshotPreserved?: boolean;
  promotionOccurred?: boolean;
  failureReason?: string | null;
  notificationFailureCount?: number;
  rollbackStatus?: VoucherSyncRollbackStatus;
  startedAt: string;
  startedMs: number;
  persistedVoucherCount?: number;
}): VoucherSynchronizationResult {
  const metrics = input.metrics ?? createMetrics();
  const finishedMs = Date.now();
  const validationIssues = Object.freeze([...(input.validationIssues ?? [])]);
  const summary = Object.freeze({
    company: input.company,
    period: input.period,
    snapshotId: input.snapshotId ?? null,
    outcome: input.outcome,
    responseStatus: input.responseStatus ?? null,
    ...metrics,
    phaseDurationsMs: Object.freeze({ ...input.phaseDurations }),
    validationIssues,
    repositoryFailureCode: input.repositoryFailureCode ?? null,
    previousActiveSnapshotPreserved: input.previousActiveSnapshotPreserved ?? false,
    promotionOccurred: input.promotionOccurred ?? false,
    failureReason: input.failureReason ?? null,
    notificationFailureCount: input.notificationFailureCount ?? 0,
    startedAt: input.startedAt,
    finishedAt: new Date(finishedMs).toISOString(),
    durationMs: Math.max(0, finishedMs - input.startedMs),
    vouchersExtracted: metrics.acceptedVoucherCount,
    vouchersPersisted: input.persistedVoucherCount ?? 0,
    droppedVouchers: metrics.rejectedVoucherCount,
    validationWarningCount: validationIssues.filter(
      (issue) => issue.classification !== 'rejected' &&
        issue.classification !== 'contract-conflict',
    ).length,
    rollbackStatus: input.rollbackStatus ?? 'not_required',
    promoted: input.promotionOccurred ?? false,
  });
  try {
    input.logger?.info('voucher.sync.completed', {
      event: 'voucher.sync.completed',
      outcome: summary.outcome,
      durationMs: summary.durationMs,
      vouchersExtracted: summary.vouchersExtracted,
      vouchersPersisted: summary.vouchersPersisted,
      droppedVouchers: summary.droppedVouchers,
      rollbackStatus: summary.rollbackStatus,
      promoted: summary.promoted,
    });
  } catch {
    // Logging is observational and must never affect synchronization.
  }
  return summary;
}

export { VoucherSynchronizationService as VoucherSnapshotSyncServiceImpl };
