import { describe, expect, it } from 'vitest';

import type { LedgerSyncRunRecord } from '../../../src/erp/ledger/ledger-domain.js';
import {
  compareTerminalRuns,
  isNonTerminalStatus,
  isTerminalStatus,
  retentionPeriodMs,
  selectSyncRunRetentionDeletes,
  SYNC_RUN_NONTERMINAL_STATUSES,
  SYNC_RUN_RETENTION_DELETE_BATCH_MAX,
  SYNC_RUN_TERMINAL_STATUSES,
  terminalTimestampMs,
  type SyncRunRetentionPolicy,
} from '../../../src/storage/sqlite/sync-run-retention-service.js';
import { SyncRunRetentionService } from '../../../src/storage/sqlite/sync-run-retention-service.js';
import type { SyncRunRepository } from '../../../src/storage/sqlite/sync-run-repository.js';

const NOW_MS = Date.parse('2026-07-25T12:00:00.000Z');
const POLICY: SyncRunRetentionPolicy = { maxCount: 3, maxAgeDays: 90 };

function run(
  partial: Partial<LedgerSyncRunRecord> & Pick<LedgerSyncRunRecord, 'syncRunId' | 'status' | 'startedAt'>,
): LedgerSyncRunRecord {
  return {
    companyId: 'co-a',
    resourceKind: 'ledgers',
    syncType: 'full',
    updatedAt: partial.updatedAt ?? partial.startedAt,
    completedAt: partial.completedAt ?? partial.startedAt,
    totalExpected: null,
    processed: 0,
    inserted: 0,
    updated: 0,
    skipped: 0,
    failed: 0,
    lastProcessedId: null,
    predecessorSyncRunId: null,
    retryCount: 0,
    cancelRequested: false,
    failureCode: null,
    failureSummary: null,
    connectorVersion: '0.3.1',
    schemaVersion: '5',
    ...partial,
  };
}

function select(
  runs: readonly LedgerSyncRunRecord[],
  policy: SyncRunRetentionPolicy = POLICY,
  predecessorRefs: ReadonlySet<string> = new Set(),
  retryEligibleId: string | null = null,
  nowMs: number = NOW_MS,
) {
  return selectSyncRunRetentionDeletes(runs, policy, predecessorRefs, retryEligibleId, nowMs);
}

describe('sync run retention status policy (B2c)', () => {
  it('documents explicit terminal allowlist', () => {
    expect([...SYNC_RUN_TERMINAL_STATUSES]).toEqual(['completed', 'failed', 'cancelled', 'interrupted']);
  });

  it('documents explicit nonterminal statuses', () => {
    expect([...SYNC_RUN_NONTERMINAL_STATUSES]).toEqual(['running', 'cancelling', 'recovering']);
  });

  it('preserves unknown statuses', () => {
    const runs = [
      run({ syncRunId: 'unknown', status: 'idle' as LedgerSyncRunRecord['status'], startedAt: '2026-01-01T00:00:00.000Z' }),
      run({ syncRunId: 'old', status: 'completed', startedAt: '2026-01-02T00:00:00.000Z' }),
    ];
    const selection = select(runs);
    expect(selection.deleteIds).not.toContain('unknown');
    expect(selection.preservedUnknownStatusCount).toBe(1);
  });
});

describe('selectSyncRunRetentionDeletes (B2c)', () => {
  it('1. preserves active runs', () => {
    const runs = [
      run({ syncRunId: 'active', status: 'running', startedAt: '2026-07-25T11:00:00.000Z' }),
      run({ syncRunId: 'old-1', status: 'completed', startedAt: '2026-01-01T00:00:00.000Z' }),
      run({ syncRunId: 'old-2', status: 'completed', startedAt: '2026-01-02T00:00:00.000Z' }),
      run({ syncRunId: 'old-3', status: 'completed', startedAt: '2026-01-03T00:00:00.000Z' }),
      run({ syncRunId: 'old-4', status: 'completed', startedAt: '2026-01-04T00:00:00.000Z' }),
    ];
    const selection = select(runs);
    expect(selection.deleteIds).not.toContain('active');
    expect(selection.preservedActiveCount).toBe(1);
  });

  it('2. preserves retry-eligible interrupted predecessor', () => {
    const runs = [
      run({ syncRunId: 'retry', status: 'interrupted', startedAt: '2026-07-20T00:00:00.000Z' }),
      run({ syncRunId: 'old-1', status: 'completed', startedAt: '2026-01-01T00:00:00.000Z' }),
    ];
    const selection = select(runs, POLICY, new Set(), 'retry');
    expect(selection.deleteIds).not.toContain('retry');
    expect(selection.preservedRetryEligibleCount).toBe(1);
  });

  it('3. preserves predecessor references', () => {
    const runs = [
      run({
        syncRunId: 'retry-child',
        status: 'completed',
        startedAt: '2026-07-24T00:00:00.000Z',
        predecessorSyncRunId: 'predecessor',
      }),
      run({ syncRunId: 'predecessor', status: 'interrupted', startedAt: '2026-07-20T00:00:00.000Z' }),
    ];
    const selection = select(runs, POLICY, new Set(['predecessor']), null);
    expect(selection.deleteIds).not.toContain('predecessor');
    expect(selection.preservedPredecessorRefCount).toBe(1);
  });

  it('4. preserves latest completed and latest failure per scope', () => {
    const runs = [
      run({ syncRunId: 'latest-completed', status: 'completed', startedAt: '2026-07-10T00:00:00.000Z' }),
      run({ syncRunId: 'latest-failed', status: 'failed', startedAt: '2026-07-09T00:00:00.000Z' }),
      run({ syncRunId: 'old-failed', status: 'failed', startedAt: '2026-01-01T00:00:00.000Z' }),
      run({ syncRunId: 'old-1', status: 'completed', startedAt: '2026-01-02T00:00:00.000Z' }),
    ];
    const selection = select(runs);
    expect(selection.deleteIds).not.toContain('latest-completed');
    expect(selection.deleteIds).not.toContain('latest-failed');
    expect(selection.deleteIds).toContain('old-failed');
  });

  it('5. deletes surplus terminal runs outside the count window while within age limit', () => {
    const runs = [
      run({ syncRunId: 'r1', status: 'completed', startedAt: '2026-07-05T00:00:00.000Z' }),
      run({ syncRunId: 'r2', status: 'completed', startedAt: '2026-07-04T00:00:00.000Z' }),
      run({ syncRunId: 'r3', status: 'completed', startedAt: '2026-07-03T00:00:00.000Z' }),
      run({ syncRunId: 'r4', status: 'completed', startedAt: '2026-07-02T00:00:00.000Z' }),
      run({ syncRunId: 'r5', status: 'completed', startedAt: '2026-07-01T00:00:00.000Z' }),
      run({ syncRunId: 'r6', status: 'completed', startedAt: '2026-06-30T00:00:00.000Z' }),
    ];
    const selection = select(runs, { maxCount: 3, maxAgeDays: 90 });
    expect([...selection.deleteIds].sort()).toEqual(['r5', 'r6']);
    expect(selection.preservedRecentCount).toBe(3);
  });

  it('6. deletes age-expired terminal rows while under count limit', () => {
    const runs = [
      run({ syncRunId: 'old-1', status: 'completed', startedAt: '2026-01-01T00:00:00.000Z' }),
      run({ syncRunId: 'old-2', status: 'completed', startedAt: '2026-01-02T00:00:00.000Z' }),
    ];
    const selection = select(runs, { maxCount: 100, maxAgeDays: 90 });
    expect(selection.deleteIds).toContain('old-1');
    expect(selection.deleteIds).not.toContain('old-2');
  });

  it('7. deletes rows meeting both age and count triggers', () => {
    const runs = [
      run({ syncRunId: 'recent-1', status: 'completed', startedAt: '2026-07-20T00:00:00.000Z' }),
      run({ syncRunId: 'recent-2', status: 'completed', startedAt: '2026-07-19T00:00:00.000Z' }),
      run({ syncRunId: 'old-outside', status: 'completed', startedAt: '2026-01-01T00:00:00.000Z' }),
      run({ syncRunId: 'old-outside-2', status: 'completed', startedAt: '2026-01-02T00:00:00.000Z' }),
    ];
    const selection = select(runs, { maxCount: 1, maxAgeDays: 90 });
    expect(selection.deleteIds).toEqual(expect.arrayContaining(['old-outside', 'old-outside-2']));
  });

  it('8. keeps protected old rows despite age and count pressure', () => {
    const runs = [
      run({ syncRunId: 'retry', status: 'interrupted', startedAt: '2026-01-01T00:00:00.000Z' }),
      run({ syncRunId: 'old-1', status: 'completed', startedAt: '2026-01-02T00:00:00.000Z' }),
      run({ syncRunId: 'old-2', status: 'completed', startedAt: '2026-01-03T00:00:00.000Z' }),
    ];
    const selection = select(runs, { maxCount: 0, maxAgeDays: 7 }, new Set(), 'retry');
    expect(selection.deleteIds).not.toContain('retry');
  });

  it('9. treats exact age boundary as retained and one millisecond beyond as deletable', () => {
    const maxAgeDays = 90;
    const maxAgeMs = retentionPeriodMs(maxAgeDays);
    const boundary = new Date(NOW_MS - maxAgeMs).toISOString();
    const justExpired = new Date(NOW_MS - maxAgeMs - 1).toISOString();

    expect(
      select(
        [run({ syncRunId: 'boundary-only', status: 'completed', startedAt: boundary, completedAt: boundary })],
        { maxCount: 100, maxAgeDays },
      ).deleteIds,
    ).toEqual([]);

    const selection = select(
      [
        run({ syncRunId: 'boundary', status: 'completed', startedAt: boundary, completedAt: boundary }),
        run({ syncRunId: 'newer-failed', status: 'failed', startedAt: '2026-07-20T00:00:00.000Z' }),
        run({ syncRunId: 'expired', status: 'failed', startedAt: justExpired, completedAt: justExpired }),
      ],
      { maxCount: 100, maxAgeDays },
    );
    expect(selection.deleteIds).toContain('expired');
    expect(selection.deleteIds).not.toContain('boundary');
  });

  it('10. treats exact count boundary as retained and next index as deletable', () => {
    const runs = [
      run({ syncRunId: 'c', status: 'cancelled', startedAt: '2026-07-03T00:00:00.000Z' }),
      run({ syncRunId: 'b', status: 'cancelled', startedAt: '2026-07-02T00:00:00.000Z' }),
      run({ syncRunId: 'a', status: 'cancelled', startedAt: '2026-07-01T00:00:00.000Z' }),
      run({ syncRunId: 'z', status: 'cancelled', startedAt: '2026-06-30T00:00:00.000Z' }),
    ];
    const selection = select(runs, { maxCount: 3, maxAgeDays: 365 });
    expect(selection.deleteIds).toEqual(['z']);
    expect(selection.preservedRecentCount).toBe(3);
  });

  it('11. minimum maxAgeDays materially deletes sooner than default', () => {
    const runs = [
      run({ syncRunId: 'keep', status: 'completed', startedAt: '2026-07-20T00:00:00.000Z' }),
      run({ syncRunId: 'mid-age', status: 'cancelled', startedAt: '2026-07-10T00:00:00.000Z' }),
    ];
    expect(select(runs, { maxCount: 100, maxAgeDays: 7 }).deleteIds).toEqual(['mid-age']);
    expect(select(runs, { maxCount: 100, maxAgeDays: 90 }).deleteIds).toEqual([]);
  });

  it('12. minimum maxCount materially retains fewer rows than maximum', () => {
    const runs = [
      run({ syncRunId: 'r1', status: 'completed', startedAt: '2026-07-05T00:00:00.000Z' }),
      run({ syncRunId: 'r2', status: 'completed', startedAt: '2026-07-04T00:00:00.000Z' }),
      run({ syncRunId: 'r3', status: 'completed', startedAt: '2026-07-03T00:00:00.000Z' }),
      run({ syncRunId: 'r4', status: 'completed', startedAt: '2026-07-02T00:00:00.000Z' }),
    ];
    const minCount = select(runs, { maxCount: 2, maxAgeDays: 365 });
    const maxCount = select(runs, { maxCount: 500, maxAgeDays: 365 });
    expect(minCount.deleteIds).toEqual(['r4']);
    expect(maxCount.deleteIds).toEqual([]);
  });

  it('13. orders by terminal timestamp desc then sync_run_id desc on ties', () => {
    const tiedTime = '2026-07-01T12:00:00.000Z';
    const runs = [
      run({ syncRunId: 'aaa', status: 'cancelled', startedAt: tiedTime, completedAt: tiedTime }),
      run({ syncRunId: 'zzz', status: 'cancelled', startedAt: tiedTime, completedAt: tiedTime }),
    ];
    expect(compareTerminalRuns(runs[0]!, runs[1]!)).toBeGreaterThan(0);
    const selection = select(runs, { maxCount: 1, maxAgeDays: 365 });
    expect(selection.deleteIds).toEqual(['aaa']);
  });

  it('14. prefers completed_at over started_at for terminal ordering', () => {
    expect(
      terminalTimestampMs(
        run({
          syncRunId: 'x',
          status: 'completed',
          startedAt: '2026-01-01T00:00:00.000Z',
          completedAt: '2026-07-01T00:00:00.000Z',
        }),
      ),
    ).toBe(Date.parse('2026-07-01T00:00:00.000Z'));
  });

  it('15. allows prunable interrupted when not retry-eligible and not latest failure', () => {
    const runs = [
      run({ syncRunId: 'latest', status: 'interrupted', startedAt: '2026-07-20T00:00:00.000Z' }),
      run({ syncRunId: 'stale', status: 'interrupted', startedAt: '2026-01-01T00:00:00.000Z' }),
    ];
    const selection = select(runs, { maxCount: 100, maxAgeDays: 90 });
    expect(selection.deleteIds).toContain('stale');
    expect(selection.deleteIds).not.toContain('latest');
  });

  it('16. computes retention period from max age days', () => {
    expect(retentionPeriodMs(90)).toBe(90 * 24 * 60 * 60 * 1000);
  });

  it('17. isTerminalStatus and isNonTerminalStatus are explicit', () => {
    expect(isTerminalStatus('completed')).toBe(true);
    expect(isNonTerminalStatus('running')).toBe(true);
    expect(isTerminalStatus('running')).toBe(false);
    expect(isNonTerminalStatus('completed')).toBe(false);
  });
});

describe('SyncRunRetentionService batching (B2c)', () => {
  it('18. caps deletes per invocation and reports moreEligibleRowsRemain', () => {
    const deleteCalls: string[][] = [];
    const repository = {
      listDistinctScopes: () => [{ companyId: 'co', resourceKind: 'ledgers' as const }],
      listPredecessorReferencedIds: () => [],
      listAllRunsForScope: () =>
        Array.from({ length: 5 }, (_, index) =>
          run({
            syncRunId: `run-${index}`,
            status: 'completed',
            startedAt: `2026-01-0${index + 1}T00:00:00.000Z`,
          }),
        ),
      findRetryPredecessor: () => null,
      deleteRunsByIds: (ids: readonly string[]) => {
        deleteCalls.push([...ids]);
        return ids.length;
      },
    } as unknown as SyncRunRepository;

    const service = new SyncRunRetentionService(
      repository,
      { maxCount: 0, maxAgeDays: 7, deleteBatchMax: 2 },
      undefined,
      () => NOW_MS,
    );
    const result = service.prune();
    expect(result.deletedCount).toBe(2);
    expect(result.moreEligibleRowsRemain).toBe(true);
    expect(deleteCalls).toEqual([['run-3', 'run-2']]);
  });

  it('19. repeated prune is idempotent once eligible rows are removed', () => {
    let stored = [
      run({ syncRunId: 'keep', status: 'completed', startedAt: '2026-07-20T00:00:00.000Z' }),
      run({ syncRunId: 'drop', status: 'completed', startedAt: '2026-01-01T00:00:00.000Z' }),
    ];
    const repository = {
      listDistinctScopes: () => [{ companyId: 'co', resourceKind: 'ledgers' as const }],
      listPredecessorReferencedIds: () => [],
      listAllRunsForScope: () => stored,
      findRetryPredecessor: () => null,
      deleteRunsByIds: (ids: readonly string[]) => {
        stored = stored.filter((row) => !ids.includes(row.syncRunId));
        return ids.length;
      },
    } as unknown as SyncRunRepository;

    const service = new SyncRunRetentionService(
      repository,
      { maxCount: 100, maxAgeDays: 7 },
      undefined,
      () => NOW_MS,
    );
    const first = service.prune();
    const second = service.prune();
    expect(first.deletedCount).toBe(1);
    expect(second.deletedCount).toBe(0);
    expect(second.eligibleDeleteCount).toBe(0);
  });

  it('20. documents default delete batch max', () => {
    expect(SYNC_RUN_RETENTION_DELETE_BATCH_MAX).toBe(100);
  });
});
