import type { LedgerSyncRunRecord, LedgerSyncStatus, SyncResourceKind } from '../../src/erp/ledger/ledger-domain.js';
import type { CreateSyncRunInput, SyncRunRepository } from '../../src/storage/sqlite/sync-run-repository.js';
import { STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';

export const RETENTION_TEST_COMPANY = 'retention-co';
export const RETENTION_CONNECTOR_VERSION = '0.3.1';

export function retentionRunInput(
  companyId: string,
  resourceKind: SyncResourceKind,
  predecessorSyncRunId?: string | null,
): CreateSyncRunInput {
  return {
    companyId,
    resourceKind,
    syncType: 'full',
    connectorVersion: RETENTION_CONNECTOR_VERSION,
    schemaVersion: String(STORAGE_SCHEMA_VERSION),
    predecessorSyncRunId,
  };
}

export function seedTerminalRun(
  repository: SyncRunRepository,
  input: CreateSyncRunInput,
  options: {
    status: Exclude<LedgerSyncStatus, 'idle' | 'recovering'>;
    startedAt: string;
    failureCode?: string | null;
    failureSummary?: string | null;
  },
): LedgerSyncRunRecord {
  const run = repository.createRun(input);
  const updated: LedgerSyncRunRecord = {
    ...run,
    status: options.status,
    startedAt: options.startedAt,
    updatedAt: options.startedAt,
    completedAt: options.startedAt,
    failureCode: options.failureCode ?? null,
    failureSummary: options.failureSummary ?? null,
  };
  repository.updateRun(updated);
  return updated;
}

export function isoDaysAgo(days: number, fromMs: number): string {
  return new Date(fromMs - days * 24 * 60 * 60 * 1000).toISOString();
}

export function countRuns(
  repository: SyncRunRepository,
  companyId: string,
  resourceKind: SyncResourceKind,
): number {
  return repository.listAllRunsForScope(companyId, resourceKind).length;
}
