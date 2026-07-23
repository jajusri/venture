import { randomUUID } from 'node:crypto';

import type { LedgerSyncRunRecord, LedgerSyncStatus } from '../../erp/ledger/ledger-domain.js';
import type { SqliteDatabase } from './sqlite-database.js';

export interface CreateSyncRunInput {
  readonly companyId: string;
  readonly syncType: 'full' | 'incremental';
  readonly connectorVersion: string;
  readonly schemaVersion: string;
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
           WHERE company_id = ? AND status IN ('running', 'cancelling', 'recovering')
           LIMIT 1`,
        )
        .get(input.companyId) as { sync_run_id: string } | undefined;
      if (active) {
        throw new Error(`Active sync run already exists for company '${input.companyId}'.`);
      }

    const now = new Date().toISOString();
    const record: LedgerSyncRunRecord = {
      syncRunId: randomUUID(),
      companyId: input.companyId,
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
      retryCount: 0,
      cancelRequested: false,
      failureCode: null,
      failureSummary: null,
      connectorVersion: input.connectorVersion,
      schemaVersion: input.schemaVersion,
    };
    db.prepare(
      `INSERT INTO sync_runs (
        sync_run_id, company_id, sync_type, status, started_at, updated_at, completed_at,
        total_expected, processed, inserted, updated_count, skipped, failed, last_processed_id,
        retry_count, cancel_requested, failure_code, failure_summary, connector_version, schema_version
      ) VALUES (
        @syncRunId, @companyId, @syncType, @status, @startedAt, @updatedAt, @completedAt,
        @totalExpected, @processed, @inserted, @updated, @skipped, @failed, @lastProcessedId,
        @retryCount, @cancelRequested, @failureCode, @failureSummary, @connectorVersion, @schemaVersion
      )`,
    ).run(toParams(record));
    db.exec('COMMIT');
    return record;
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
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

  findActiveRun(companyId: string): LedgerSyncRunRecord | null {
    const db = this.database.getDatabase();
    const row = db
      .prepare(
        `SELECT * FROM sync_runs
         WHERE company_id = ? AND status IN ('running', 'cancelling', 'recovering')
         ORDER BY started_at DESC LIMIT 1`,
      )
      .get(companyId);
    return row ? fromRow(row as unknown as SyncRunRow) : null;
  }

  listRuns(companyId: string, limit = 20): LedgerSyncRunRecord[] {
    const db = this.database.getDatabase();
    const rows = db
      .prepare('SELECT * FROM sync_runs WHERE company_id = ? ORDER BY started_at DESC LIMIT ?')
      .all(companyId, limit) as unknown as SyncRunRow[];
    return rows.map(fromRow);
  }

  recoverAbandonedRuns(companyId: string): number {
    const db = this.database.getDatabase();
    const now = new Date().toISOString();
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
}

interface SyncRunRow {
  sync_run_id: string;
  company_id: string;
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
    retryCount: record.retryCount,
    cancelRequested: record.cancelRequested ? 1 : 0,
    failureCode: record.failureCode,
    failureSummary: record.failureSummary,
    connectorVersion: record.connectorVersion,
    schemaVersion: record.schemaVersion,
  };
}
