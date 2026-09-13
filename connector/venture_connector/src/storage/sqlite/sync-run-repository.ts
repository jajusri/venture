import { randomUUID } from 'node:crypto';

import type { SyncResourceKind, LedgerSyncRunRecord, LedgerSyncStatus } from '../../erp/ledger/ledger-domain.js';
import type { SqliteDatabase } from './sqlite-database.js';

export interface CreateSyncRunInput {
  readonly companyId: string;
  readonly resourceKind: SyncResourceKind;
  readonly syncType: 'full' | 'incremental';
  readonly connectorVersion: string;
  readonly schemaVersion: string;
  /** When set, must reference an eligible interrupted predecessor for the same company and resource kind. */
  readonly predecessorSyncRunId?: string | null;
}

export class SyncRunRepository {
  constructor(private readonly database: SqliteDatabase) {}

  createRun(input: CreateSyncRunInput): LedgerSyncRunRecord {
    const db = this.database.getDatabase();
    db.exec('BEGIN IMMEDIATE');
    try {
      const active = db
        .prepare(
          `SELECT sync_run_id FROM sync_runs
           WHERE company_id = ? AND resource_kind = ? AND status IN ('running', 'cancelling', 'recovering')
           LIMIT 1`,
        )
        .get(input.companyId, input.resourceKind) as { sync_run_id: string } | undefined;
      if (active) {
        throw new Error(
          `Active sync run already exists for company '${input.companyId}' and resource '${input.resourceKind}'.`,
        );
      }

      const predecessorSyncRunId = input.predecessorSyncRunId ?? null;
      let retryCount = 0;
      if (predecessorSyncRunId) {
        const predecessor = this.loadPredecessorForRetry(
          db,
          predecessorSyncRunId,
          input.companyId,
          input.resourceKind,
        );
        retryCount = predecessor.retryCount + 1;
      }

      const now = new Date().toISOString();
      const record: LedgerSyncRunRecord = {
        syncRunId: randomUUID(),
        companyId: input.companyId,
        resourceKind: input.resourceKind,
        syncType: input.syncType,
        status: 'running',
        startedAt: now,
        updatedAt: now,
        completedAt: null,
        totalExpected: null,
        processed: 0,
        inserted: 0,
        updated: 0,
        skipped: 0,
        failed: 0,
        lastProcessedId: null,
        predecessorSyncRunId,
        retryCount,
        cancelRequested: false,
        failureCode: null,
        failureSummary: null,
        connectorVersion: input.connectorVersion,
        schemaVersion: input.schemaVersion,
      };
      db.prepare(
        `INSERT INTO sync_runs (
          sync_run_id, company_id, resource_kind, sync_type, status, started_at, updated_at, completed_at,
          total_expected, processed, inserted, updated_count, skipped, failed, last_processed_id,
          predecessor_sync_run_id, retry_count, cancel_requested, failure_code, failure_summary,
          connector_version, schema_version
        ) VALUES (
          @syncRunId, @companyId, @resourceKind, @syncType, @status, @startedAt, @updatedAt, @completedAt,
          @totalExpected, @processed, @inserted, @updated, @skipped, @failed, @lastProcessedId,
          @predecessorSyncRunId, @retryCount, @cancelRequested, @failureCode, @failureSummary,
          @connectorVersion, @schemaVersion
        )`,
      ).run(toParams(record));
      db.exec('COMMIT');
      return record;
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
  }

  /**
   * Returns the most recent interrupted run eligible as a retry predecessor:
   * same company and resource kind, status interrupted, and not yet superseded by another run.
   */
  findRetryPredecessor(companyId: string, resourceKind: SyncResourceKind): LedgerSyncRunRecord | null {
    const db = this.database.getDatabase();
    const row = db
      .prepare(
        `SELECT sr.* FROM sync_runs sr
         WHERE sr.company_id = ?
           AND sr.resource_kind = ?
           AND sr.status = 'interrupted'
           AND NOT EXISTS (
             SELECT 1 FROM sync_runs retry
             WHERE retry.predecessor_sync_run_id = sr.sync_run_id
           )
           AND NOT EXISTS (
             SELECT 1 FROM sync_runs newer
             WHERE newer.company_id = sr.company_id
               AND newer.resource_kind = sr.resource_kind
               AND newer.started_at > sr.started_at
           )
         ORDER BY sr.started_at DESC
         LIMIT 1`,
      )
      .get(companyId, resourceKind);
    return row ? fromRow(row as unknown as SyncRunRow) : null;
  }

  updateRun(record: LedgerSyncRunRecord): void {
    const db = this.database.getDatabase();
    db.prepare(
      `UPDATE sync_runs SET
        status = @status,
        updated_at = @updatedAt,
        completed_at = @completedAt,
        total_expected = @totalExpected,
        processed = @processed,
        inserted = @inserted,
        updated_count = @updated,
        skipped = @skipped,
        failed = @failed,
        last_processed_id = @lastProcessedId,
        retry_count = @retryCount,
        cancel_requested = @cancelRequested,
        failure_code = @failureCode,
        failure_summary = @failureSummary
      WHERE sync_run_id = @syncRunId`,
    ).run(toUpdateParams(record));
  }

  findById(companyId: string, syncRunId: string): LedgerSyncRunRecord | null {
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT * FROM sync_runs WHERE sync_run_id = ? AND company_id = ?')
      .get(syncRunId, companyId);
    return row ? fromRow(row as unknown as SyncRunRow) : null;
  }

  /** @deprecated Use findById(companyId, syncRunId) for session-scoped access */
  findByIdUnsafe(syncRunId: string): LedgerSyncRunRecord | null {
    const db = this.database.getDatabase();
    const row = db.prepare('SELECT * FROM sync_runs WHERE sync_run_id = ?').get(syncRunId);
    return row ? fromRow(row as unknown as SyncRunRow) : null;
  }

  findActiveRun(companyId: string, resourceKind: SyncResourceKind): LedgerSyncRunRecord | null {
    const db = this.database.getDatabase();
    const row = db
      .prepare(
        `SELECT * FROM sync_runs
         WHERE company_id = ? AND resource_kind = ? AND status IN ('running', 'cancelling', 'recovering')
         ORDER BY started_at DESC LIMIT 1`,
      )
      .get(companyId, resourceKind);
    return row ? fromRow(row as unknown as SyncRunRow) : null;
  }

  listRuns(companyId: string, resourceKind: SyncResourceKind, limit = 20): LedgerSyncRunRecord[] {
    const db = this.database.getDatabase();
    const rows = db
      .prepare(
        'SELECT * FROM sync_runs WHERE company_id = ? AND resource_kind = ? ORDER BY started_at DESC LIMIT ?',
      )
      .all(companyId, resourceKind, limit) as unknown as SyncRunRow[];
    return rows.map(fromRow);
  }

  recoverAbandonedRuns(companyId: string, resourceKind?: SyncResourceKind): number {
    const db = this.database.getDatabase();
    const now = new Date().toISOString();
    if (resourceKind) {
      const result = db
        .prepare(
          `UPDATE sync_runs SET status = 'interrupted', updated_at = ?, completed_at = ?,
           failure_code = 'ABANDONED', failure_summary = 'Sync was abandoned after connector restart.'
           WHERE company_id = ? AND resource_kind = ? AND status IN ('running', 'cancelling')`,
        )
        .run(now, now, companyId, resourceKind);
      return Number(result.changes);
    }
    const result = db
      .prepare(
        `UPDATE sync_runs SET status = 'interrupted', updated_at = ?, completed_at = ?,
         failure_code = 'ABANDONED', failure_summary = 'Sync was abandoned after connector restart.'
         WHERE company_id = ? AND status IN ('running', 'cancelling')`,
      )
      .run(now, now, companyId);
    return Number(result.changes);
  }

  recoverAllAbandonedRuns(): number {
    const db = this.database.getDatabase();
    const now = new Date().toISOString();
    const result = db
      .prepare(
        `UPDATE sync_runs SET status = 'interrupted', updated_at = ?, completed_at = ?,
         failure_code = 'ABANDONED', failure_summary = 'Sync was abandoned after connector restart.'
         WHERE status IN ('running', 'cancelling')`,
      )
      .run(now, now);
    return Number(result.changes);
  }

  listDistinctScopes(): Array<{ companyId: string; resourceKind: SyncResourceKind }> {
    const db = this.database.getDatabase();
    const rows = db
      .prepare(
        `SELECT DISTINCT company_id, resource_kind
         FROM sync_runs
         ORDER BY company_id ASC, resource_kind ASC`,
      )
      .all() as Array<{ company_id: string; resource_kind: string }>;
    return rows.map((row) => ({
      companyId: row.company_id,
      resourceKind: row.resource_kind as SyncResourceKind,
    }));
  }

  listAllRunsForScope(companyId: string, resourceKind: SyncResourceKind): LedgerSyncRunRecord[] {
    const db = this.database.getDatabase();
    const rows = db
      .prepare(
        `SELECT * FROM sync_runs
         WHERE company_id = ? AND resource_kind = ?
         ORDER BY started_at DESC`,
      )
      .all(companyId, resourceKind) as unknown as SyncRunRow[];
    return rows.map(fromRow);
  }

  listPredecessorReferencedIds(): string[] {
    const db = this.database.getDatabase();
    const rows = db
      .prepare(
        `SELECT DISTINCT predecessor_sync_run_id AS sync_run_id
         FROM sync_runs
         WHERE predecessor_sync_run_id IS NOT NULL`,
      )
      .all() as Array<{ sync_run_id: string }>;
    return rows.map((row) => row.sync_run_id);
  }

  hasActiveRun(companyId: string, resourceKind: SyncResourceKind): boolean {
    return this.findActiveRun(companyId, resourceKind) !== null;
  }

  deleteRunsByIds(syncRunIds: readonly string[]): number {
    if (syncRunIds.length === 0) {
      return 0;
    }
    return this.database.runInTransactionSync(() => {
      const db = this.database.getDatabase();
      const statement = db.prepare('DELETE FROM sync_runs WHERE sync_run_id = ?');
      let deleted = 0;
      for (const syncRunId of syncRunIds) {
        const result = statement.run(syncRunId);
        deleted += Number(result.changes);
      }
      return deleted;
    });
  }

  private loadPredecessorForRetry(
    db: ReturnType<SqliteDatabase['getDatabase']>,
    predecessorSyncRunId: string,
    companyId: string,
    resourceKind: SyncResourceKind,
  ): LedgerSyncRunRecord {
    const row = db.prepare('SELECT * FROM sync_runs WHERE sync_run_id = ?').get(predecessorSyncRunId);
    if (!row) {
      throw new Error(`Retry predecessor sync run '${predecessorSyncRunId}' was not found.`);
    }
    const predecessor = fromRow(row as unknown as SyncRunRow);
    if (predecessor.companyId !== companyId) {
      throw new Error('Retry predecessor belongs to a different company.');
    }
    if (predecessor.resourceKind !== resourceKind) {
      throw new Error('Retry predecessor belongs to a different resource kind.');
    }
    if (predecessor.status !== 'interrupted') {
      throw new Error(`Retry predecessor must have status 'interrupted', got '${predecessor.status}'.`);
    }
    const superseded = db
      .prepare('SELECT sync_run_id FROM sync_runs WHERE predecessor_sync_run_id = ? LIMIT 1')
      .get(predecessorSyncRunId) as { sync_run_id: string } | undefined;
    if (superseded) {
      throw new Error('Retry predecessor has already been superseded by another run.');
    }
    return predecessor;
  }
}

interface SyncRunRow {
  sync_run_id: string;
  company_id: string;
  resource_kind: string;
  sync_type: string;
  status: string;
  started_at: string;
  updated_at: string;
  completed_at: string | null;
  total_expected: number | null;
  processed: number;
  inserted: number;
  updated_count: number;
  skipped: number;
  failed: number;
  last_processed_id: string | null;
  predecessor_sync_run_id: string | null;
  retry_count: number;
  cancel_requested: number;
  failure_code: string | null;
  failure_summary: string | null;
  connector_version: string;
  schema_version: string;
}

function fromRow(row: SyncRunRow): LedgerSyncRunRecord {
  return {
    syncRunId: row.sync_run_id,
    companyId: row.company_id,
    resourceKind: (row.resource_kind ?? 'ledgers') as SyncResourceKind,
    syncType: row.sync_type as LedgerSyncRunRecord['syncType'],
    status: row.status as LedgerSyncStatus,
    startedAt: row.started_at,
    updatedAt: row.updated_at,
    completedAt: row.completed_at,
    totalExpected: row.total_expected,
    processed: row.processed,
    inserted: row.inserted,
    updated: row.updated_count,
    skipped: row.skipped,
    failed: row.failed,
    lastProcessedId: row.last_processed_id,
    predecessorSyncRunId: row.predecessor_sync_run_id ?? null,
    retryCount: row.retry_count,
    cancelRequested: row.cancel_requested === 1,
    failureCode: row.failure_code,
    failureSummary: row.failure_summary,
    connectorVersion: row.connector_version,
    schemaVersion: row.schema_version,
  };
}

function toUpdateParams(record: LedgerSyncRunRecord) {
  return {
    syncRunId: record.syncRunId,
    status: record.status,
    updatedAt: record.updatedAt,
    completedAt: record.completedAt,
    totalExpected: record.totalExpected,
    processed: record.processed,
    inserted: record.inserted,
    updated: record.updated,
    skipped: record.skipped,
    failed: record.failed,
    lastProcessedId: record.lastProcessedId,
    retryCount: record.retryCount,
    cancelRequested: record.cancelRequested ? 1 : 0,
    failureCode: record.failureCode,
    failureSummary: record.failureSummary,
  };
}

function toParams(record: LedgerSyncRunRecord) {
  return {
    syncRunId: record.syncRunId,
    companyId: record.companyId,
    resourceKind: record.resourceKind,
    syncType: record.syncType,
    status: record.status,
    startedAt: record.startedAt,
    updatedAt: record.updatedAt,
    completedAt: record.completedAt,
    totalExpected: record.totalExpected,
    processed: record.processed,
    inserted: record.inserted,
    updated: record.updated,
    skipped: record.skipped,
    failed: record.failed,
    lastProcessedId: record.lastProcessedId,
    predecessorSyncRunId: record.predecessorSyncRunId,
    retryCount: record.retryCount,
    cancelRequested: record.cancelRequested ? 1 : 0,
    failureCode: record.failureCode,
    failureSummary: record.failureSummary,
    connectorVersion: record.connectorVersion,
    schemaVersion: record.schemaVersion,
  };
}
