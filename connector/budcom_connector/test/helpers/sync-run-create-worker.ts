import { parentPort, workerData } from 'node:worker_threads';

import { SyncRunRepository } from '../../src/storage/sqlite/sync-run-repository.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';

interface WorkerInput {
  readonly databasePath: string;
  readonly companyId: string;
}

interface WorkerResult {
  readonly ok: boolean;
  readonly syncRunId?: string;
  readonly message?: string;
}

const input = workerData as WorkerInput;
const database = new SqliteDatabase({ databasePath: input.databasePath });

try {
  database.open();
  const repository = new SyncRunRepository(database);
  const run = repository.createRun({
    companyId: input.companyId,
    syncType: 'full',
    connectorVersion: '0.3.1',
    schemaVersion: '1',
  });
  const result: WorkerResult = { ok: true, syncRunId: run.syncRunId };
  parentPort?.postMessage(result);
} catch (error) {
  const result: WorkerResult = {
    ok: false,
    message: error instanceof Error ? error.message : String(error),
  };
  parentPort?.postMessage(result);
} finally {
  database.close();
}
