import { randomUUID } from 'node:crypto';

import type { SqliteDatabase } from './sqlite-database.js';
import type {
  InboundXmlDuplicateStatus,
  InboundXmlPersistenceStatus,
  InboundXmlResourceKind,
  InboundXmlSourceType,
  InboundXmlValidationStatus,
} from '../../ingestion/inbound-xml-types.js';
import {
  InboundXmlReservationStatus,
  type XmlImportReservationOutcome,
  type XmlImportReservationOutcomeKind,
} from '../../ingestion/xml-import-attempt-lifecycle.js';

export interface XmlImportAttemptRecord {
  readonly importAttemptId: string;
  readonly companyId: string | null;
  readonly resourceKind: InboundXmlResourceKind;
  readonly sourceType: InboundXmlSourceType;
  readonly sourceIdentifier: string | null;
  readonly contentFingerprint: string;
  readonly byteSize: number;
  readonly reservationStatus: InboundXmlReservationStatus;
  readonly validationStatus: InboundXmlValidationStatus;
  readonly persistenceStatus: InboundXmlPersistenceStatus;
  readonly duplicateStatus: InboundXmlDuplicateStatus;
  readonly errorCode: string | null;
  readonly parserVersion: string;
  readonly connectorVersion: string;
  readonly receivedAt: string;
  readonly completedAt: string | null;
}

export interface ReserveXmlImportAttemptInput {
  readonly companyId?: string | null;
  readonly resourceKind: InboundXmlResourceKind;
  readonly sourceType: InboundXmlSourceType;
  readonly sourceIdentifier?: string;
  readonly contentFingerprint: string;
  readonly byteSize: number;
  readonly parserVersion: string;
  readonly connectorVersion: string;
}

export interface CompleteXmlImportAttemptInput {
  readonly importAttemptId: string;
  readonly validationStatus: InboundXmlValidationStatus;
  readonly persistenceStatus: InboundXmlPersistenceStatus;
  readonly duplicateStatus: InboundXmlDuplicateStatus;
  readonly errorCode?: string | null;
}

export interface RecoverAbandonedImportAttemptsOptions {
  readonly now?: () => Date;
  readonly graceMs?: number;
}

export class XmlImportAttemptRepository {
  constructor(private readonly database: SqliteDatabase) {}

  findCompletedDuplicate(input: {
    readonly companyId?: string | null;
    readonly resourceKind: InboundXmlResourceKind;
    readonly contentFingerprint: string;
  }): XmlImportAttemptRecord | null {
    const db = this.database.getDatabase();
    const row = (input.companyId === undefined || input.companyId === null
      ? db.prepare(
          `SELECT * FROM xml_import_attempts
           WHERE company_id IS NULL
             AND resource_kind = ?
             AND content_fingerprint = ?
             AND reservation_status = 'completed'
             AND validation_status = 'validated'
             AND persistence_status = 'completed'
             AND duplicate_status = 'unique'
           ORDER BY received_at DESC
           LIMIT 1`,
        ).get(input.resourceKind, input.contentFingerprint)
      : db.prepare(
          `SELECT * FROM xml_import_attempts
           WHERE company_id = ?
             AND resource_kind = ?
             AND content_fingerprint = ?
             AND reservation_status = 'completed'
             AND validation_status = 'validated'
             AND persistence_status = 'completed'
             AND duplicate_status = 'unique'
           ORDER BY received_at DESC
           LIMIT 1`,
        ).get(input.companyId, input.resourceKind, input.contentFingerprint)) as
      | Record<string, unknown>
      | undefined;
    return row ? mapRow(row) : null;
  }

  /**
   * Atomically acquire a reservation or classify an existing lock as duplicate/in-progress.
   * Enforced by partial unique indexes idx_xml_import_attempts_reservation_no_company
   * and idx_xml_import_attempts_reservation_scoped.
   */
  reserveAttempt(input: ReserveXmlImportAttemptInput): XmlImportReservationOutcome {
    const db = this.database.getDatabase();
    const importAttemptId = randomUUID();
    const now = new Date().toISOString();
    const record = buildReservedRecord(importAttemptId, now, input);

    db.exec('BEGIN IMMEDIATE');
    try {
      db.prepare(
        `INSERT INTO xml_import_attempts (
          import_attempt_id, company_id, resource_kind, source_type, source_identifier,
          content_fingerprint, byte_size, reservation_status, validation_status, persistence_status,
          duplicate_status, error_code, parser_version, connector_version, received_at, completed_at
        ) VALUES (
          @importAttemptId, @companyId, @resourceKind, @sourceType, @sourceIdentifier,
          @contentFingerprint, @byteSize, @reservationStatus, @validationStatus, @persistenceStatus,
          @duplicateStatus, @errorCode, @parserVersion, @connectorVersion, @receivedAt, @completedAt
        )`,
      ).run(toParams(record));
      db.exec('COMMIT');
      return { kind: 'acquired', importAttemptId };
    } catch (error) {
      db.exec('ROLLBACK');
      if (!isUniqueConstraintError(error)) {
        throw error;
      }
      const blocking = this.findBlockingReservation(input);
      if (!blocking) {
        throw error;
      }
      const kind = classifyBlockingReservation(blocking);
      return { kind, importAttemptId: blocking.importAttemptId };
    }
  }

  completeAttempt(input: CompleteXmlImportAttemptInput): void {
    const db = this.database.getDatabase();
    db.exec('BEGIN IMMEDIATE');
    try {
      const reservationStatus =
        input.persistenceStatus === 'completed' && input.duplicateStatus === 'unique'
          ? InboundXmlReservationStatus.Completed
          : InboundXmlReservationStatus.Released;
      db.prepare(
        `UPDATE xml_import_attempts
         SET reservation_status = @reservationStatus,
             validation_status = @validationStatus,
             persistence_status = @persistenceStatus,
             duplicate_status = @duplicateStatus,
             error_code = @errorCode,
             completed_at = @completedAt
         WHERE import_attempt_id = @importAttemptId`,
      ).run({
        importAttemptId: input.importAttemptId,
        reservationStatus,
        validationStatus: input.validationStatus,
        persistenceStatus: input.persistenceStatus,
        duplicateStatus: input.duplicateStatus,
        errorCode: input.errorCode ?? null,
        completedAt: new Date().toISOString(),
      });
      db.exec('COMMIT');
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
  }

  releaseAttempt(importAttemptId: string, errorCode: string): void {
    const db = this.database.getDatabase();
    db.exec('BEGIN IMMEDIATE');
    try {
      db.prepare(
        `UPDATE xml_import_attempts
         SET reservation_status = @reservationStatus,
             validation_status = @validationStatus,
             persistence_status = @persistenceStatus,
             error_code = @errorCode,
             completed_at = @completedAt
         WHERE import_attempt_id = @importAttemptId
           AND reservation_status = @activeStatus`,
      ).run({
        importAttemptId,
        reservationStatus: InboundXmlReservationStatus.Released,
        validationStatus: 'rejected',
        persistenceStatus: 'failed',
        errorCode,
        completedAt: new Date().toISOString(),
        activeStatus: InboundXmlReservationStatus.Active,
      });
      db.exec('COMMIT');
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
  }

  recordRejectedAttempt(input: ReserveXmlImportAttemptInput & { readonly errorCode: string }): XmlImportAttemptRecord {
    const db = this.database.getDatabase();
    db.exec('BEGIN IMMEDIATE');
    try {
      const now = new Date().toISOString();
      const record: XmlImportAttemptRecord = {
        importAttemptId: randomUUID(),
        companyId: input.companyId ?? null,
        resourceKind: input.resourceKind,
        sourceType: input.sourceType,
        sourceIdentifier: input.sourceIdentifier ?? null,
        contentFingerprint: input.contentFingerprint,
        byteSize: input.byteSize,
        reservationStatus: InboundXmlReservationStatus.Released,
        validationStatus: 'rejected',
        persistenceStatus: 'failed',
        duplicateStatus: 'not_evaluated',
        errorCode: input.errorCode,
        parserVersion: input.parserVersion,
        connectorVersion: input.connectorVersion,
        receivedAt: now,
        completedAt: now,
      };
      db.prepare(
        `INSERT INTO xml_import_attempts (
          import_attempt_id, company_id, resource_kind, source_type, source_identifier,
          content_fingerprint, byte_size, reservation_status, validation_status, persistence_status,
          duplicate_status, error_code, parser_version, connector_version, received_at, completed_at
        ) VALUES (
          @importAttemptId, @companyId, @resourceKind, @sourceType, @sourceIdentifier,
          @contentFingerprint, @byteSize, @reservationStatus, @validationStatus, @persistenceStatus,
          @duplicateStatus, @errorCode, @parserVersion, @connectorVersion, @receivedAt, @completedAt
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
   * Startup recovery: mark stale active reservations as abandoned so retries are allowed.
   * Does not run during normal import traffic.
   */
  recoverAbandonedReservations(options: RecoverAbandonedImportAttemptsOptions = {}): number {
    const db = this.database.getDatabase();
    const now = options.now?.() ?? new Date();
    const graceMs = options.graceMs ?? 0;
    const cutoff = new Date(now.getTime() - graceMs).toISOString();
    db.exec('BEGIN IMMEDIATE');
    try {
      const result = db
        .prepare(
          `UPDATE xml_import_attempts
           SET reservation_status = @abandonedStatus,
               validation_status = @rejectedStatus,
               persistence_status = @failedStatus,
               error_code = @errorCode,
               completed_at = @completedAt
           WHERE reservation_status = @activeStatus
             AND received_at <= @cutoff`,
        )
        .run({
          abandonedStatus: InboundXmlReservationStatus.Abandoned,
          rejectedStatus: 'rejected',
          failedStatus: 'failed',
          errorCode: 'abandoned',
          completedAt: now.toISOString(),
          activeStatus: InboundXmlReservationStatus.Active,
          cutoff,
        });
      db.exec('COMMIT');
      return Number(result.changes);
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
  }

  recoverAllAbandonedReservations(options: RecoverAbandonedImportAttemptsOptions = {}): number {
    return this.recoverAbandonedReservations(options);
  }

  countActiveReservations(): number {
    const db = this.database.getDatabase();
    const row = db
      .prepare(
        `SELECT COUNT(*) AS count FROM xml_import_attempts WHERE reservation_status = ?`,
      )
      .get(InboundXmlReservationStatus.Active) as { count: number };
    return Number(row.count);
  }

  private findBlockingReservation(input: ReserveXmlImportAttemptInput): XmlImportAttemptRecord | null {
    const db = this.database.getDatabase();
    const row = (input.companyId === undefined || input.companyId === null
      ? db.prepare(
          `SELECT * FROM xml_import_attempts
           WHERE company_id IS NULL
             AND resource_kind = ?
             AND content_fingerprint = ?
             AND reservation_status IN ('active', 'completed')
           ORDER BY CASE reservation_status WHEN 'completed' THEN 0 ELSE 1 END, received_at DESC
           LIMIT 1`,
        ).get(input.resourceKind, input.contentFingerprint)
      : db.prepare(
          `SELECT * FROM xml_import_attempts
           WHERE company_id = ?
             AND resource_kind = ?
             AND content_fingerprint = ?
             AND reservation_status IN ('active', 'completed')
           ORDER BY CASE reservation_status WHEN 'completed' THEN 0 ELSE 1 END, received_at DESC
           LIMIT 1`,
        ).get(input.companyId, input.resourceKind, input.contentFingerprint)) as
      | Record<string, unknown>
      | undefined;
    return row ? mapRow(row) : null;
  }
}

function classifyBlockingReservation(
  record: XmlImportAttemptRecord,
): Exclude<XmlImportReservationOutcomeKind, 'acquired'> {
  if (record.reservationStatus === InboundXmlReservationStatus.Completed) {
    return 'duplicate';
  }
  return 'in_progress';
}

function buildReservedRecord(
  importAttemptId: string,
  receivedAt: string,
  input: ReserveXmlImportAttemptInput,
): XmlImportAttemptRecord {
  return {
    importAttemptId,
    companyId: input.companyId ?? null,
    resourceKind: input.resourceKind,
    sourceType: input.sourceType,
    sourceIdentifier: input.sourceIdentifier ?? null,
    contentFingerprint: input.contentFingerprint,
    byteSize: input.byteSize,
    reservationStatus: InboundXmlReservationStatus.Active,
    validationStatus: 'pending',
    persistenceStatus: 'not_attempted',
    duplicateStatus: 'not_evaluated',
    errorCode: null,
    parserVersion: input.parserVersion,
    connectorVersion: input.connectorVersion,
    receivedAt,
    completedAt: null,
  };
}

function isUniqueConstraintError(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) {
    return false;
  }
  const code = (error as { code?: unknown }).code;
  if (code === 'SQLITE_CONSTRAINT_UNIQUE' || code === 'SQLITE_CONSTRAINT') {
    return true;
  }
  const message = error instanceof Error ? error.message : String(error);
  return message.includes('UNIQUE constraint failed');
}

type SqlBindParams = Record<string, string | number | null>;

function toParams(record: XmlImportAttemptRecord): SqlBindParams {
  return {
    importAttemptId: record.importAttemptId,
    companyId: record.companyId,
    resourceKind: record.resourceKind,
    sourceType: record.sourceType,
    sourceIdentifier: record.sourceIdentifier,
    contentFingerprint: record.contentFingerprint,
    byteSize: record.byteSize,
    reservationStatus: record.reservationStatus,
    validationStatus: record.validationStatus,
    persistenceStatus: record.persistenceStatus,
    duplicateStatus: record.duplicateStatus,
    errorCode: record.errorCode,
    parserVersion: record.parserVersion,
    connectorVersion: record.connectorVersion,
    receivedAt: record.receivedAt,
    completedAt: record.completedAt,
  };
}

function mapRow(row: Record<string, unknown>): XmlImportAttemptRecord {
  return {
    importAttemptId: String(row.import_attempt_id),
    companyId: row.company_id === null || row.company_id === undefined ? null : String(row.company_id),
    resourceKind: String(row.resource_kind) as InboundXmlResourceKind,
    sourceType: String(row.source_type) as InboundXmlSourceType,
    sourceIdentifier:
      row.source_identifier === null || row.source_identifier === undefined
        ? null
        : String(row.source_identifier),
    contentFingerprint: String(row.content_fingerprint),
    byteSize: Number(row.byte_size),
    reservationStatus: String(row.reservation_status ?? 'released') as InboundXmlReservationStatus,
    validationStatus: String(row.validation_status) as InboundXmlValidationStatus,
    persistenceStatus: String(row.persistence_status) as InboundXmlPersistenceStatus,
    duplicateStatus: String(row.duplicate_status) as InboundXmlDuplicateStatus,
    errorCode: row.error_code === null || row.error_code === undefined ? null : String(row.error_code),
    parserVersion: String(row.parser_version),
    connectorVersion: String(row.connector_version),
    receivedAt: String(row.received_at),
    completedAt: row.completed_at === null || row.completed_at === undefined ? null : String(row.completed_at),
  };
}
