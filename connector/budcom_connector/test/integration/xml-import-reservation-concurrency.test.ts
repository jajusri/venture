import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Worker } from 'node:worker_threads';
import { fileURLToPath } from 'node:url';

import { afterEach, describe, expect, it } from 'vitest';

import { createLogger } from '../../src/infrastructure/logging/logger.js';
import {
  InboundXmlEnvelopeService,
} from '../../src/ingestion/inbound-xml-envelope.service.js';
import {
  InboundXmlDuplicateStatus,
  InboundXmlPersistenceStatus,
  InboundXmlResourceKind,
  InboundXmlSourceType,
  InboundXmlValidationStatus,
  isRejectedInboundEnvelope,
  isValidatedInboundEnvelope,
} from '../../src/ingestion/inbound-xml-types.js';
import { InboundXmlReasonCode } from '../../src/ingestion/inbound-xml-reason-codes.js';
import { InboundXmlReservationStatus } from '../../src/ingestion/xml-import-attempt-lifecycle.js';
import { slugify } from '../../src/extraction/normalization/strings.js';
import { MIGRATION_008, STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import {
  XmlImportAttemptRepository,
  type ReserveXmlImportAttemptInput,
} from '../../src/storage/sqlite/xml-import-attempt-repository.js';
import { SAMPLE_LEDGERS_RESPONSE } from '../helpers/master-data-fixtures.js';

const openDbs: SqliteDatabase[] = [];
const tempDirs: string[] = [];

afterEach(() => {
  while (openDbs.length > 0) {
    openDbs.pop()?.close();
  }
  for (const dir of tempDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function createRepo(): { db: SqliteDatabase; repo: XmlImportAttemptRepository; databasePath: string } {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-xml-reserve-'));
  tempDirs.push(basePath);
  const databasePath = path.join(basePath, 'budcom-ledger.db');
  const db = new SqliteDatabase({ databasePath });
  db.open();
  openDbs.push(db);
  return { db, repo: new XmlImportAttemptRepository(db), databasePath };
}

function reserveInput(overrides: Partial<ReserveXmlImportAttemptInput> = {}): ReserveXmlImportAttemptInput {
  return {
    companyId: 'co-a',
    resourceKind: InboundXmlResourceKind.Ledgers,
    sourceType: InboundXmlSourceType.InlineBuffer,
    sourceIdentifier: 'inline',
    contentFingerprint: 'fingerprint-abc123',
    byteSize: 100,
    parserVersion: '1',
    connectorVersion: '0.3.1',
    ...overrides,
  };
}

describe('XmlImportAttemptRepository reservation concurrency', () => {
  it('1. concurrent identical reservations yield exactly one acquired owner', async () => {
    const { repo } = createRepo();
    const input = reserveInput();
    const results = await Promise.all(
      Array.from({ length: 8 }, () => Promise.resolve(repo.reserveAttempt(input))),
    );
    const acquired = results.filter((result) => result.kind === 'acquired');
    const blocked = results.filter((result) => result.kind !== 'acquired');
    expect(acquired).toHaveLength(1);
    expect(blocked).toHaveLength(7);
  });

  it('2. concurrent blocked attempts classify as in_progress before completion', async () => {
    const { repo } = createRepo();
    const input = reserveInput();
    const first = repo.reserveAttempt(input);
    expect(first.kind).toBe('acquired');
    const second = repo.reserveAttempt(input);
    expect(second.kind).toBe('in_progress');
  });

  it('3. completed attempt remains duplicate-protected', () => {
    const { repo } = createRepo();
    const input = reserveInput();
    const acquired = repo.reserveAttempt(input);
    expect(acquired.kind).toBe('acquired');
    repo.completeAttempt({
      importAttemptId: acquired.importAttemptId,
      validationStatus: InboundXmlValidationStatus.Validated,
      persistenceStatus: InboundXmlPersistenceStatus.Completed,
      duplicateStatus: InboundXmlDuplicateStatus.Unique,
    });
    const retry = repo.reserveAttempt(input);
    expect(retry.kind).toBe('duplicate');
  });

  it('4. failed pre-validation history does not block corrected retry', () => {
    const { repo } = createRepo();
    repo.recordRejectedAttempt({
      ...reserveInput({ contentFingerprint: 'bad-content' }),
      errorCode: InboundXmlReasonCode.EnvelopeDrift,
    });
    const acquired = repo.reserveAttempt(reserveInput({ contentFingerprint: 'good-content' }));
    expect(acquired.kind).toBe('acquired');
  });

  it('5. abandoned active reservation is recovered on startup-style recovery', () => {
    const { repo } = createRepo();
    const acquired = repo.reserveAttempt(reserveInput());
    expect(acquired.kind).toBe('acquired');
    expect(repo.countActiveReservations()).toBe(1);
    const recovered = repo.recoverAllAbandonedReservations({
      now: () => new Date('2026-07-25T12:00:00.000Z'),
    });
    expect(recovered).toBe(1);
    expect(repo.countActiveReservations()).toBe(0);
  });

  it('6. retry after abandoned recovery succeeds', () => {
    const { repo } = createRepo();
    expect(repo.reserveAttempt(reserveInput()).kind).toBe('acquired');
    repo.recoverAllAbandonedReservations({
      now: () => new Date('2026-07-25T12:00:00.000Z'),
    });
    const retry = repo.reserveAttempt(reserveInput());
    expect(retry.kind).toBe('acquired');
    repo.completeAttempt({
      importAttemptId: retry.importAttemptId,
      validationStatus: InboundXmlValidationStatus.Validated,
      persistenceStatus: InboundXmlPersistenceStatus.Completed,
      duplicateStatus: InboundXmlDuplicateStatus.Unique,
    });
  });

  it('7. recovery is idempotent', () => {
    const { repo } = createRepo();
    repo.reserveAttempt(reserveInput());
    const first = repo.recoverAllAbandonedReservations({
      now: () => new Date('2026-07-25T12:00:00.000Z'),
    });
    const second = repo.recoverAllAbandonedReservations({
      now: () => new Date('2026-07-25T12:00:00.000Z'),
    });
    expect(first).toBe(1);
    expect(second).toBe(0);
  });

  it('8. different companies do not conflict', () => {
    const { repo } = createRepo();
    const first = repo.reserveAttempt(reserveInput({ companyId: 'co-a' }));
    const second = repo.reserveAttempt(reserveInput({ companyId: 'co-b' }));
    expect(first.kind).toBe('acquired');
    expect(second.kind).toBe('acquired');
  });

  it('9. different resource kinds do not conflict', () => {
    const { repo } = createRepo();
    const first = repo.reserveAttempt(reserveInput({ resourceKind: InboundXmlResourceKind.Ledgers }));
    const second = repo.reserveAttempt(reserveInput({ resourceKind: InboundXmlResourceKind.StockItems }));
    expect(first.kind).toBe('acquired');
    expect(second.kind).toBe('acquired');
  });

  it('10. completion failure releases reservation without leaving completed lock', () => {
    const { repo } = createRepo();
    const acquired = repo.reserveAttempt(reserveInput());
    repo.releaseAttempt(acquired.importAttemptId, InboundXmlReasonCode.PersistenceFailed);
    expect(repo.findCompletedDuplicate({
      companyId: 'co-a',
      resourceKind: InboundXmlResourceKind.Ledgers,
      contentFingerprint: 'fingerprint-abc123',
    })).toBeNull();
    const retry = repo.reserveAttempt(reserveInput());
    expect(retry.kind).toBe('acquired');
  });

  it('11. migration v8 creates dual reservation indexes without COALESCE sentinel', () => {
    const { db } = createRepo();
    const sqlite = db.getDatabase();
    const version = sqlite
      .prepare('SELECT MAX(version) AS version FROM schema_migrations')
      .get() as { version: number };
    expect(version.version).toBe(STORAGE_SCHEMA_VERSION);
    const noCompany = sqlite
      .prepare(
        `SELECT name, sql FROM sqlite_master
         WHERE type = 'index' AND name = 'idx_xml_import_attempts_reservation_no_company'`,
      )
      .get() as { name: string; sql: string };
    const scoped = sqlite
      .prepare(
        `SELECT name, sql FROM sqlite_master
         WHERE type = 'index' AND name = 'idx_xml_import_attempts_reservation_scoped'`,
      )
      .get() as { name: string; sql: string };
    expect(noCompany.sql).toContain('company_id IS NULL');
    expect(scoped.sql).toContain('company_id IS NOT NULL');
    expect(MIGRATION_008).not.toContain('__none__');
    const legacy = sqlite
      .prepare(
        `SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'idx_xml_import_attempts_reservation'`,
      )
      .get();
    expect(legacy).toBeUndefined();
  });

  it('12. database reopen after migration succeeds and preserves reservation semantics', () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-xml-reopen-'));
    tempDirs.push(basePath);
    const databasePath = path.join(basePath, 'budcom-ledger.db');
    const first = new SqliteDatabase({ databasePath });
    first.open();
    const repo = new XmlImportAttemptRepository(first);
    const acquired = repo.reserveAttempt(reserveInput());
    repo.completeAttempt({
      importAttemptId: acquired.importAttemptId,
      validationStatus: InboundXmlValidationStatus.Validated,
      persistenceStatus: InboundXmlPersistenceStatus.Completed,
      duplicateStatus: InboundXmlDuplicateStatus.Unique,
    });
    first.close();

    const second = new SqliteDatabase({ databasePath });
    second.open();
    openDbs.push(second);
    const reopened = new XmlImportAttemptRepository(second);
    expect(reopened.findCompletedDuplicate({
      companyId: 'co-a',
      resourceKind: InboundXmlResourceKind.Ledgers,
      contentFingerprint: 'fingerprint-abc123',
    })?.reservationStatus).toBe(InboundXmlReservationStatus.Completed);
    expect(reopened.reserveAttempt(reserveInput()).kind).toBe('duplicate');
  });
});

describe('XmlImportAttemptRepository company scope isolation', () => {
  it('proves discovered company IDs cannot slugify to __none__', () => {
    expect(slugify('__none__')).toBe('none');
    expect(slugify('__NONE__')).toBe('none');
    expect(slugify('  __ none __  ')).toBe('none');
    expect(slugify('__none__')).not.toBe('__none__');
  });

  it('isolates NULL company scope from sentinel-like company_id values', () => {
    const { repo } = createRepo();
    const fingerprint = 'scope-isolation-fingerprint';
    const base = {
      resourceKind: InboundXmlResourceKind.Ledgers,
      sourceType: InboundXmlSourceType.InlineBuffer,
      contentFingerprint: fingerprint,
      byteSize: 128,
      parserVersion: '1',
      connectorVersion: '0.3.1',
    };

    const nullScope = repo.reserveAttempt({ ...base, companyId: null });
    expect(nullScope.kind).toBe('acquired');
    repo.completeAttempt({
      importAttemptId: nullScope.importAttemptId,
      validationStatus: InboundXmlValidationStatus.Validated,
      persistenceStatus: InboundXmlPersistenceStatus.Completed,
      duplicateStatus: InboundXmlDuplicateStatus.Unique,
    });

    const sentinelScope = repo.reserveAttempt({ ...base, companyId: '__none__' });
    expect(sentinelScope.kind).toBe('acquired');
    repo.completeAttempt({
      importAttemptId: sentinelScope.importAttemptId,
      validationStatus: InboundXmlValidationStatus.Validated,
      persistenceStatus: InboundXmlPersistenceStatus.Completed,
      duplicateStatus: InboundXmlDuplicateStatus.Unique,
    });

    expect(repo.reserveAttempt({ ...base, companyId: null }).kind).toBe('duplicate');
    expect(repo.reserveAttempt({ ...base, companyId: '__none__' }).kind).toBe('duplicate');
    expect(repo.reserveAttempt({ ...base, companyId: 'co-a' }).kind).toBe('acquired');
  });
});

describe('InboundXmlEnvelopeService reservation integration', () => {
  it('returns import_in_progress when an equivalent reservation is active', () => {
    const { repo } = createRepo();
    const service = new InboundXmlEnvelopeService({
      importAttemptRepository: repo,
      logger: createLogger({ service: 'test', level: 'error' }),
    });
    const bytes = Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8');
    const preview = service.acceptBuffer(bytes, {
      sourceType: InboundXmlSourceType.InlineBuffer,
      targetCompanyName: 'Pilot Co',
      targetCompanyId: 'co-pilot',
      recordAttempt: false,
    });
    expect(isValidatedInboundEnvelope(preview)).toBe(true);
    if (!isValidatedInboundEnvelope(preview)) return;

    repo.reserveAttempt({
      companyId: 'co-pilot',
      resourceKind: preview.resourceKind,
      sourceType: InboundXmlSourceType.InlineBuffer,
      contentFingerprint: preview.contentFingerprint,
      byteSize: bytes.length,
      parserVersion: '1',
      connectorVersion: '0.3.1',
    });

    const blocked = service.acceptBuffer(bytes, {
      sourceType: InboundXmlSourceType.InlineBuffer,
      targetCompanyName: 'Pilot Co',
      targetCompanyId: 'co-pilot',
      recordAttempt: true,
    });
    expect(blocked.validationStatus).toBe(InboundXmlValidationStatus.Rejected);
    expect(isRejectedInboundEnvelope(blocked) && blocked.reasonCode).toBe(InboundXmlReasonCode.ImportInProgress);
  });

  it('releases active reservation when validation fails after acquire', () => {
    const { repo } = createRepo();
    const service = new InboundXmlEnvelopeService({
      importAttemptRepository: repo,
      logger: createLogger({ service: 'test', level: 'error' }),
    });
    const validBytes = Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8');
    const valid = service.acceptBuffer(validBytes, {
      sourceType: InboundXmlSourceType.InlineBuffer,
      targetCompanyName: 'Pilot Co',
      targetCompanyId: 'co-pilot',
      recordAttempt: true,
    });
    expect(isValidatedInboundEnvelope(valid)).toBe(true);

    const invalid = service.acceptBuffer(Buffer.from('<ENVELOPE></ENVELOPE>', 'utf8'), {
      sourceType: InboundXmlSourceType.InlineBuffer,
      targetCompanyName: 'Pilot Co',
      targetCompanyId: 'co-pilot',
      recordAttempt: true,
    });
    expect(invalid.validationStatus).toBe(InboundXmlValidationStatus.Rejected);
    expect(repo.countActiveReservations()).toBe(0);
  });
});

describe('XmlImportAttemptRepository cross-process concurrency', () => {
  it('allows only one worker to acquire the same reservation', async () => {
    const { databasePath } = createRepo();
    const workerPath = fileURLToPath(new URL('../helpers/xml-import-reserve-worker.ts', import.meta.url));
    const results = await Promise.all(
      Array.from({ length: 4 }, () => runReserveWorker(workerPath, databasePath)),
    );
    const acquired = results.filter((result) => result.kind === 'acquired');
    expect(acquired).toHaveLength(1);
    expect(results.length - acquired.length).toBeGreaterThan(0);
  });
});

function runReserveWorker(
  workerPath: string,
  databasePath: string,
): Promise<{ kind: string; importAttemptId?: string }> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(workerPath, {
      workerData: { databasePath },
      execArgv: ['--import', 'tsx'],
    });
    worker.once('message', (message) => resolve(message as { kind: string; importAttemptId?: string }));
    worker.once('error', reject);
  });
}
