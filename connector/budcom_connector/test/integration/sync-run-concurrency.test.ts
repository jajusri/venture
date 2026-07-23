import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Worker } from 'node:worker_threads';
import { fileURLToPath } from 'node:url';

import { afterEach, describe, expect, it } from 'vitest';

import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { SyncRunRepository } from '../../src/storage/sqlite/sync-run-repository.js';
import { STORAGE_SCHEMA_VERSION } from '../../src/storage/sqlite/schema.js';

const workerScript = fileURLToPath(new URL('../helpers/sync-run-create-worker.ts', import.meta.url));
const tempDirs: string[] = [];

afterEach(async () => {
  await waitForWorkerRelease();
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    } catch {
      // Windows may retain file handles briefly after worker exit.
    }
  }
});

function createSharedDatabase(): { basePath: string; databasePath: string } {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-sync-concurrency-'));
  tempDirs.push(basePath);
  const databasePath = path.join(basePath, 'budcom-ledger.db');
  const bootstrap = new SqliteDatabase({ databasePath });
  bootstrap.open();
  bootstrap.close();
  return { basePath, databasePath };
}

function runCreateRunWorker(
  databasePath: string,
  companyId: string,
): Promise<{ ok: boolean; syncRunId?: string; message?: string }> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(workerScript, {
      workerData: { databasePath, companyId },
      execArgv: ['--import', 'tsx'],
    });
    let settled = false;
    worker.once('message', (message) => {
      settled = true;
      resolve(message);
    });
    worker.once('error', (error) => {
      if (!settled) reject(error);
    });
    worker.once('exit', (code) => {
      if (!settled && code !== 0) {
        reject(new Error(`Worker exited with code ${code}`));
      }
    });
  });
}

async function waitForWorkerRelease(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 200));
}

describe.sequential('sync run multi-connection concurrency', () => {
  it('returns sync-conflict on a second independent connection for the same company', () => {
    const { databasePath } = createSharedDatabase();
    const connectionA = new SqliteDatabase({ databasePath });
    const connectionB = new SqliteDatabase({ databasePath });
    connectionA.open();
    connectionB.open();
    const repoA = new SyncRunRepository(connectionA);
    const repoB = new SyncRunRepository(connectionB);

    repoA.createRun({
      companyId: 'company-a',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });

    expect(() =>
      repoB.createRun({
        companyId: 'company-a',
        syncType: 'full',
        connectorVersion: '0.3.1',
        schemaVersion: String(STORAGE_SCHEMA_VERSION),
      }),
    ).toThrow(/Active sync run already exists/);

    connectionA.close();
    connectionB.close();
  });

  it('allows exactly one active run per company across independent SQLite connections', async () => {
    const { databasePath } = createSharedDatabase();
    const [first, second] = await Promise.all([
      runCreateRunWorker(databasePath, 'company-a'),
      runCreateRunWorker(databasePath, 'company-a'),
    ]);

    await waitForWorkerRelease();

    const successes = [first, second].filter((result) => result.ok);
    expect(successes.length).toBeLessThanOrEqual(1);
    expect(successes.length).toBeGreaterThanOrEqual(1);

    const verifyDb = new SqliteDatabase({ databasePath });
    verifyDb.open();
    const repository = new SyncRunRepository(verifyDb);
    const active = repository.findActiveRun('company-a');
    expect(active).not.toBeNull();
    const allActive = verifyDb
      .getDatabase()
      .prepare(
        `SELECT COUNT(*) AS count FROM sync_runs
         WHERE company_id = ? AND status IN ('running', 'cancelling', 'recovering')`,
      )
      .get('company-a') as { count: number };
    expect(allActive.count).toBe(1);

    const integrity = verifyDb.getDatabase().prepare('PRAGMA integrity_check').get() as {
      integrity_check: string;
    };
    expect(integrity.integrity_check).toBe('ok');
    verifyDb.close();
  });

  it('allows independent active runs for different companies', async () => {
    const { databasePath } = createSharedDatabase();
    const [companyA, companyB] = await Promise.all([
      runCreateRunWorker(databasePath, 'company-a'),
      runCreateRunWorker(databasePath, 'company-b'),
    ]);
    expect(companyA.ok).toBe(true);
    expect(companyB.ok).toBe(true);

    await waitForWorkerRelease();

    const verifyDb = new SqliteDatabase({ databasePath });
    verifyDb.open();
    const repository = new SyncRunRepository(verifyDb);
    expect(repository.findActiveRun('company-a')).not.toBeNull();
    expect(repository.findActiveRun('company-b')).not.toBeNull();
    verifyDb.close();
  });

  it('recovers abandoned runs after connection loss and permits a new sync', async () => {
    const { databasePath } = createSharedDatabase();
    const connectionA = new SqliteDatabase({ databasePath });
    connectionA.open();
    const repoA = new SyncRunRepository(connectionA);
    const abandoned = repoA.createRun({
      companyId: 'company-a',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });
    expect(abandoned.status).toBe('running');
    connectionA.close();

    const connectionB = new SqliteDatabase({ databasePath });
    connectionB.open();
    const repoB = new SyncRunRepository(connectionB);
    expect(() =>
      repoB.createRun({
        companyId: 'company-a',
        syncType: 'full',
        connectorVersion: '0.3.1',
        schemaVersion: String(STORAGE_SCHEMA_VERSION),
      }),
    ).toThrow(/Active sync run already exists/);

    const recovered = repoB.recoverAbandonedRuns('company-a');
    expect(recovered).toBe(1);

    const resumed = repoB.createRun({
      companyId: 'company-a',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: String(STORAGE_SCHEMA_VERSION),
    });
    expect(resumed.status).toBe('running');
    expect(repoB.findActiveRun('company-a')?.syncRunId).toBe(resumed.syncRunId);
    connectionB.close();
  });
});
