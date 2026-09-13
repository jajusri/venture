import { parentPort, workerData } from 'node:worker_threads';

import { InboundXmlResourceKind, InboundXmlSourceType } from '../../src/ingestion/inbound-xml-types.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { XmlImportAttemptRepository } from '../../src/storage/sqlite/xml-import-attempt-repository.js';

interface WorkerInput {
  readonly databasePath: string;
}

interface WorkerResult {
  readonly kind: string;
  readonly importAttemptId?: string;
  readonly message?: string;
}

const input = workerData as WorkerInput;
const database = new SqliteDatabase({ databasePath: input.databasePath });

try {
  database.open();
  const repository = new XmlImportAttemptRepository(database);
  const outcome = repository.reserveAttempt({
    companyId: 'co-a',
    resourceKind: InboundXmlResourceKind.Ledgers,
    sourceType: InboundXmlSourceType.InlineBuffer,
    contentFingerprint: 'worker-shared-fingerprint',
    byteSize: 128,
    parserVersion: '1',
    connectorVersion: '0.3.1',
  });
  const result: WorkerResult = { kind: outcome.kind, importAttemptId: outcome.importAttemptId };
  parentPort?.postMessage(result);
} catch (error) {
  const result: WorkerResult = {
    kind: 'error',
    message: error instanceof Error ? error.message : String(error),
  };
  parentPort?.postMessage(result);
} finally {
  database.close();
}
