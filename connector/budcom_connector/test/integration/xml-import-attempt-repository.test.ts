import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { createLogger } from '../../src/infrastructure/logging/logger.js';
import {
  InboundXmlEnvelopeService,
} from '../../src/ingestion/inbound-xml-envelope.service.js';
import {
  InboundXmlDuplicateStatus,
  InboundXmlPersistenceStatus,
  InboundXmlSourceType,
  InboundXmlValidationStatus,
  isValidatedInboundEnvelope,
} from '../../src/ingestion/inbound-xml-types.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { XmlImportAttemptRepository } from '../../src/storage/sqlite/xml-import-attempt-repository.js';
import { SAMPLE_LEDGERS_RESPONSE } from '../helpers/master-data-fixtures.js';

const openDbs: SqliteDatabase[] = [];

afterEach(() => {
  while (openDbs.length > 0) {
    openDbs.pop()?.close();
  }
});

function createRepo(): { db: SqliteDatabase; repo: XmlImportAttemptRepository } {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-xml-import-'));
  const db = new SqliteDatabase({ databasePath: path.join(basePath, 'budcom-ledger.db') });
  db.open();
  openDbs.push(db);
  return { db, repo: new XmlImportAttemptRepository(db) };
}

describe('XmlImportAttemptRepository integration', () => {
  it('records validated attempts and detects duplicates transactionally', () => {
    const { repo } = createRepo();
    const service = new InboundXmlEnvelopeService({
      importAttemptRepository: repo,
      logger: createLogger({ service: 'test', level: 'error' }),
    });
    const bytes = Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8');
    const options = {
      sourceType: InboundXmlSourceType.InlineBuffer,
      targetCompanyName: 'Pilot Co',
      targetCompanyId: 'co-pilot',
      recordAttempt: true,
    };

    const first = service.acceptBuffer(bytes, options);
    expect(isValidatedInboundEnvelope(first)).toBe(true);
    if (!isValidatedInboundEnvelope(first)) return;
    expect(first.duplicateStatus).toBe(InboundXmlDuplicateStatus.Unique);
    expect(first.persistenceStatus).toBe(InboundXmlPersistenceStatus.Completed);

    const second = service.acceptBuffer(bytes, options);
    expect(isValidatedInboundEnvelope(second)).toBe(true);
    if (!isValidatedInboundEnvelope(second)) return;
    expect(second.duplicateStatus).toBe(InboundXmlDuplicateStatus.Duplicate);
    expect(second.persistenceStatus).toBe(InboundXmlPersistenceStatus.Skipped);

    const duplicate = repo.findCompletedDuplicate({
      companyId: 'co-pilot',
      resourceKind: first.resourceKind,
      contentFingerprint: first.contentFingerprint,
    });
    expect(duplicate?.validationStatus).toBe(InboundXmlValidationStatus.Validated);
  });

  it('does not treat validation failures as completed duplicates', () => {
    const { repo } = createRepo();
    const service = new InboundXmlEnvelopeService({
      importAttemptRepository: repo,
      logger: createLogger({ service: 'test', level: 'error' }),
    });
    const invalid = service.acceptBuffer(Buffer.from('<ENVELOPE></ENVELOPE>', 'utf8'), {
      sourceType: InboundXmlSourceType.InlineBuffer,
      recordAttempt: true,
    });
    expect(invalid.validationStatus).toBe(InboundXmlValidationStatus.Rejected);

    const retry = service.acceptBuffer(Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8'), {
      sourceType: InboundXmlSourceType.InlineBuffer,
      targetCompanyName: 'Pilot Co',
      recordAttempt: true,
    });
    expect(isValidatedInboundEnvelope(retry)).toBe(true);
    if (!isValidatedInboundEnvelope(retry)) return;
    expect(retry.duplicateStatus).toBe(InboundXmlDuplicateStatus.Unique);
  });
});
