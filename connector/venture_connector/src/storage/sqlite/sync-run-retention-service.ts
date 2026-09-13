import type { LedgerSyncRunRecord, SyncResourceKind } from '../../erp/ledger/ledger-domain.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import type { SyncRunRepository } from './sync-run-repository.js';

/** Nonterminal statuses — never eligible for terminal pruning. */
export const SYNC_RUN_NONTERMINAL_STATUSES = ['running', 'cancelling', 'recovering'] as const;

/** Explicit terminal statuses eligible for pruning when unprotected. */
export const SYNC_RUN_TERMINAL_STATUSES = ['completed', 'failed', 'cancelled', 'interrupted'] as const;

/** Maximum DELETE rows attempted per prune invocation (global across scopes). */
export const SYNC_RUN_RETENTION_DELETE_BATCH_MAX = 100;

export interface SyncRunRetentionPolicy {
  readonly maxCount: number;
  readonly maxAgeDays: number;
  readonly deleteBatchMax?: number;
}

export interface SyncRunPruneResult {
  readonly ok: boolean;
  readonly scopesScanned: number;
  readonly runsScanned: number;
  readonly eligibleDeleteCount: number;
  readonly deletedCount: number;
  readonly preservedActiveCount: number;
  readonly preservedRetryEligibleCount: number;
  readonly preservedPredecessorRefCount: number;
  readonly preservedLatestCompletedCount: number;
  readonly preservedLatestFailureCount: number;
  readonly preservedUnknownStatusCount: number;
  readonly preservedRecentCount: number;
  readonly scopesWithActiveRunCount: number;
  readonly moreEligibleRowsRemain: boolean;
  readonly failureCount: number;
}

export interface SyncRunRetentionSelection {
  readonly protectedIds: ReadonlySet<string>;
  readonly deleteIds: readonly string[];
  readonly preservedActiveCount: number;
  readonly preservedRetryEligibleCount: number;
  readonly preservedPredecessorRefCount: number;
  readonly preservedLatestCompletedCount: number;
  readonly preservedLatestFailureCount: number;
  readonly preservedUnknownStatusCount: number;
  readonly preservedRecentCount: number;
}

function emptyResult(partial: Partial<SyncRunPruneResult> = {}): SyncRunPruneResult {
  return {
    ok: true,
    scopesScanned: 0,
    runsScanned: 0,
    eligibleDeleteCount: 0,
    deletedCount: 0,
    preservedActiveCount: 0,
    preservedRetryEligibleCount: 0,
    preservedPredecessorRefCount: 0,
    preservedLatestCompletedCount: 0,
    preservedLatestFailureCount: 0,
    preservedUnknownStatusCount: 0,
    preservedRecentCount: 0,
    scopesWithActiveRunCount: 0,
    moreEligibleRowsRemain: false,
    failureCount: 0,
    ...partial,
  };
}

export function retentionPeriodMs(maxAgeDays: number): number {
  return maxAgeDays * 24 * 60 * 60 * 1000;
}

export function parseTimestampMs(value: string): number | null {
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export function isNonTerminalStatus(status: LedgerSyncRunRecord['status']): boolean {
  return (SYNC_RUN_NONTERMINAL_STATUSES as readonly string[]).includes(status);
}

export function isTerminalStatus(status: LedgerSyncRunRecord['status']): boolean {
  return (SYNC_RUN_TERMINAL_STATUSES as readonly string[]).includes(status);
}

/** Terminal ordering timestamp: completed_at, then updated_at, then started_at. */
export function terminalTimestampMs(run: LedgerSyncRunRecord): number | null {
  return parseTimestampMs(run.completedAt ?? run.updatedAt ?? run.startedAt);
}

export function compareTerminalRuns(a: LedgerSyncRunRecord, b: LedgerSyncRunRecord): number {
  const aMs = terminalTimestampMs(a) ?? Number.NEGATIVE_INFINITY;
  const bMs = terminalTimestampMs(b) ?? Number.NEGATIVE_INFINITY;
  if (aMs !== bMs) {
    return bMs - aMs;
  }
  return b.syncRunId.localeCompare(a.syncRunId);
}

function findLatestByStatuses(
  runs: readonly LedgerSyncRunRecord[],
  statuses: readonly LedgerSyncRunRecord['status'][],
): LedgerSyncRunRecord | undefined {
  const allowed = new Set<string>(statuses);
  let latest: LedgerSyncRunRecord | undefined;
  for (const run of runs) {
    if (!allowed.has(run.status)) {
      continue;
    }
    if (!latest || compareTerminalRuns(latest, run) > 0) {
      latest = run;
    }
  }
  return latest;
}

/**
 * Pure retention selection for one `(company_id, resource_kind)` scope.
 */
export function selectSyncRunRetentionDeletes(
  runs: readonly LedgerSyncRunRecord[],
  policy: SyncRunRetentionPolicy,
  predecessorReferencedIds: ReadonlySet<string>,
  retryEligibleSyncRunId: string | null,
  nowMs: number,
): SyncRunRetentionSelection {
  const protectedIds = new Set<string>();
  let preservedActiveCount = 0;
  let preservedRetryEligibleCount = 0;
  let preservedPredecessorRefCount = 0;
  let preservedLatestCompletedCount = 0;
  let preservedLatestFailureCount = 0;
  let preservedUnknownStatusCount = 0;

  for (const run of runs) {
    if (isNonTerminalStatus(run.status)) {
      protectedIds.add(run.syncRunId);
      preservedActiveCount += 1;
      continue;
    }
    if (!isTerminalStatus(run.status)) {
      protectedIds.add(run.syncRunId);
      preservedUnknownStatusCount += 1;
    }
  }

  for (const run of runs) {
    if (predecessorReferencedIds.has(run.syncRunId)) {
      if (!protectedIds.has(run.syncRunId)) {
        preservedPredecessorRefCount += 1;
      }
      protectedIds.add(run.syncRunId);
    }
  }

  if (retryEligibleSyncRunId) {
    if (!protectedIds.has(retryEligibleSyncRunId)) {
      preservedRetryEligibleCount = 1;
    }
    protectedIds.add(retryEligibleSyncRunId);
  }

  const latestCompleted = findLatestByStatuses(runs, ['completed']);
  if (latestCompleted) {
    if (!protectedIds.has(latestCompleted.syncRunId)) {
      preservedLatestCompletedCount = 1;
    }
    protectedIds.add(latestCompleted.syncRunId);
  }

  const latestFailure = findLatestByStatuses(runs, ['failed', 'interrupted']);
  if (latestFailure) {
    if (!protectedIds.has(latestFailure.syncRunId)) {
      preservedLatestFailureCount = 1;
    }
    protectedIds.add(latestFailure.syncRunId);
  }

  const terminalCandidates = runs.filter(
    (run) => !protectedIds.has(run.syncRunId) && isTerminalStatus(run.status),
  );
  terminalCandidates.sort(compareTerminalRuns);

  const maxAgeMs = retentionPeriodMs(policy.maxAgeDays);
  let preservedRecentCount = 0;
  const deleteIds: string[] = [];

  for (let index = 0; index < terminalCandidates.length; index += 1) {
    const run = terminalCandidates[index]!;
    const terminalMs = terminalTimestampMs(run);
    const tooOld = terminalMs === null || nowMs - terminalMs > maxAgeMs;
    const outsideCountWindow = index >= policy.maxCount;

    if (!tooOld && !outsideCountWindow) {
      preservedRecentCount += 1;
      continue;
    }

    deleteIds.push(run.syncRunId);
  }

  return {
    protectedIds,
    deleteIds,
    preservedActiveCount,
    preservedRetryEligibleCount,
    preservedPredecessorRefCount,
    preservedLatestCompletedCount,
    preservedLatestFailureCount,
    preservedUnknownStatusCount,
    preservedRecentCount,
  };
}

export class SyncRunRetentionService {
  constructor(
    private readonly repository: SyncRunRepository,
    private readonly policy: SyncRunRetentionPolicy,
    private readonly logger?: Logger,
    private readonly now: () => number = () => Date.now(),
  ) {}

  prune(): SyncRunPruneResult {
    const nowMs = this.now();
    const deleteBatchMax = this.policy.deleteBatchMax ?? SYNC_RUN_RETENTION_DELETE_BATCH_MAX;
    const scopes = this.repository.listDistinctScopes();
    const predecessorReferencedIds = new Set(this.repository.listPredecessorReferencedIds());
    let runsScanned = 0;
    let eligibleDeleteCount = 0;
    let deletedCount = 0;
    let preservedActiveCount = 0;
    let preservedRetryEligibleCount = 0;
    let preservedPredecessorRefCount = 0;
    let preservedLatestCompletedCount = 0;
    let preservedLatestFailureCount = 0;
    let preservedUnknownStatusCount = 0;
    let preservedRecentCount = 0;
    let scopesWithActiveRunCount = 0;
    let failureCount = 0;
    let remainingBatch = deleteBatchMax;
    let moreEligibleRowsRemain = false;

    for (const scope of scopes) {
      const runs = this.repository.listAllRunsForScope(scope.companyId, scope.resourceKind);
      runsScanned += runs.length;

      if (runs.some((run) => isNonTerminalStatus(run.status))) {
        scopesWithActiveRunCount += 1;
      }

      const retryEligible = this.repository.findRetryPredecessor(scope.companyId, scope.resourceKind);
      const selection = selectSyncRunRetentionDeletes(
        runs,
        this.policy,
        predecessorReferencedIds,
        retryEligible?.syncRunId ?? null,
        nowMs,
      );

      preservedActiveCount += selection.preservedActiveCount;
      preservedRetryEligibleCount += selection.preservedRetryEligibleCount;
      preservedPredecessorRefCount += selection.preservedPredecessorRefCount;
      preservedLatestCompletedCount += selection.preservedLatestCompletedCount;
      preservedLatestFailureCount += selection.preservedLatestFailureCount;
      preservedUnknownStatusCount += selection.preservedUnknownStatusCount;
      preservedRecentCount += selection.preservedRecentCount;
      eligibleDeleteCount += selection.deleteIds.length;

      if (selection.deleteIds.length === 0 || remainingBatch <= 0) {
        if (selection.deleteIds.length > 0 && remainingBatch <= 0) {
          moreEligibleRowsRemain = true;
        }
        continue;
      }

      const batch = selection.deleteIds.slice(0, remainingBatch);
      if (selection.deleteIds.length > batch.length) {
        moreEligibleRowsRemain = true;
      }

      try {
        deletedCount += this.repository.deleteRunsByIds(batch);
        remainingBatch -= batch.length;
      } catch {
        failureCount += 1;
      }
    }

    const result = emptyResult({
      ok: failureCount === 0,
      scopesScanned: scopes.length,
      runsScanned,
      eligibleDeleteCount,
      deletedCount,
      preservedActiveCount,
      preservedRetryEligibleCount,
      preservedPredecessorRefCount,
      preservedLatestCompletedCount,
      preservedLatestFailureCount,
      preservedUnknownStatusCount,
      preservedRecentCount,
      scopesWithActiveRunCount,
      moreEligibleRowsRemain,
      failureCount,
    });

    this.logger?.info('sync_run_retention_prune_completed', {
      component: 'sqlite-storage',
      ok: result.ok,
      scopesScanned: result.scopesScanned,
      runsScanned: result.runsScanned,
      eligibleDeleteCount: result.eligibleDeleteCount,
      deletedCount: result.deletedCount,
      preservedActiveCount: result.preservedActiveCount,
      preservedRetryEligibleCount: result.preservedRetryEligibleCount,
      preservedPredecessorRefCount: result.preservedPredecessorRefCount,
      preservedLatestCompletedCount: result.preservedLatestCompletedCount,
      preservedLatestFailureCount: result.preservedLatestFailureCount,
      preservedUnknownStatusCount: result.preservedUnknownStatusCount,
      preservedRecentCount: result.preservedRecentCount,
      scopesWithActiveRunCount: result.scopesWithActiveRunCount,
      moreEligibleRowsRemain: result.moreEligibleRowsRemain,
      failureCount: result.failureCount,
    });

    return result;
  }
}

export interface SyncRunScope {
  readonly companyId: string;
  readonly resourceKind: SyncResourceKind;
}
