import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import {
  registerServices,
  startApplication,
  stopApplication,
  type ApplicationContext,
} from '../../src/bootstrap/register-services.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import type { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import type { VoucherSnapshotSyncService } from '../../src/services/voucher/voucher-application.interface.js';
import type {
  VoucherSyncCancellation,
  VoucherSyncProgressObserver,
} from '../../src/services/voucher/voucher-sync-progress.js';
import {
  APPROVED_VOUCHER_FIXTURE_XML,
  SOURCE_ERROR_XML,
} from '../fixtures/vouchers/approved-voucher-fixture.js';
import { createMockFetch } from '../helpers/mock-fetch.js';

const COMPANY = 'release-fixture';
const PERIOD_ONE = { dateFrom: '2026-07-27', dateTo: '2026-07-27' };
const PERIOD_TWO = { dateFrom: '2026-07-28', dateTo: '2026-07-28' };
const PERIOD_THREE = { dateFrom: '2026-07-29', dateTo: '2026-07-29' };
const observer: VoucherSyncProgressObserver = { onProgress: () => undefined };
const notCancelled: VoucherSyncCancellation = { requested: false };
const temporaryDirectories: string[] = [];
const contexts: ApplicationContext[] = [];

afterEach(async () => {
  for (const context of contexts.splice(0).reverse()) {
    await stopApplication(context);
  }
  for (const directory of temporaryDirectories.splice(0)) {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

describe('Voucher controlled release path', () => {
  it('validates startup through synchronization, APIs, recovery, restart, and shutdown', async () => {
    const databasePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-voucher-e2e-'));
    temporaryDirectories.push(databasePath);
    let responseXml = APPROVED_VOUCHER_FIXTURE_XML;
    const transport = createMockFetch(({ init }) => {
      const requestBody = typeof init?.body === 'string' ? init.body : '';
      return {
        body: requestBody.includes('List of Companies')
          ? RELEASE_COMPANY_LIST_XML
          : responseXml,
      };
    });

    let context = await startContext(databasePath, transport.fetchImpl);
    let baseUrl = `http://${context.config.host}:${context.config.port}`;

    const health = await getJson(baseUrl, '/health');
    expect(health.status).toBe(200);
    expect(health.body).toMatchObject({
      schemaVersion: '1.0.0',
      connectorVersion: '0.4.4',
      repositoryAvailable: true,
      databaseAccessible: true,
      readOnly: true,
    });
    const ready = await getJson(baseUrl, '/ready');
    expect(ready.status).toBe(200);
    expect(ready.body).toMatchObject({
      status: 'ready',
      voucherSynchronizationComposed: true,
      voucherApplicationComposed: true,
    });

    const first = await sync(context, PERIOD_ONE);
    expect(first).toMatchObject({
      outcome: 'completed',
      vouchersExtracted: 8,
      vouchersPersisted: 8,
      promoted: true,
    });

    const list = await getJson(
      baseUrl,
      `/api/v1/vouchers?company=${COMPANY}&from=2026-07-27&to=2026-07-27&page=1&pageSize=3&sort=voucherNumber:asc`,
    );
    expect(list.status).toBe(200);
    expect(list.body.schemaVersion).toBe('1.0.0');
    expect(list.body.data.pagination).toMatchObject({ page: 1, pageSize: 3, totalItems: 8 });
    expect(list.body.data.items.map((item: { number: string }) => item.number)).toEqual(['1', '2', '3']);
    expect(Object.keys(list.body.data.items[0])).not.toEqual(
      expect.arrayContaining(['guid', 'masterId', 'alterId', 'voucherKey', 'voucherRetainKey']),
    );

    const voucherId = list.body.data.items[0].id as string;
    const detail = await getJson(
      baseUrl,
      `/api/v1/vouchers/${encodeURIComponent(voucherId)}?company=${COMPANY}`,
    );
    expect(detail.status).toBe(200);
    expect(detail.body.data.voucher.id).toBe(voucherId);

    const search = await getJson(
      baseUrl,
      `/api/v1/vouchers/search?company=${COMPANY}&from=2026-07-27&to=2026-07-27&voucherNumber=1`,
    );
    expect(search.status).toBe(200);
    expect(search.body.data.items).toHaveLength(1);

    const isolated = await getJson(
      baseUrl,
      '/api/v1/vouchers?company=other-company&from=2026-07-27&to=2026-07-27',
    );
    expect(isolated.status).toBe(200);
    expect(isolated.body.data.items).toEqual([]);

    const invalid = await getJson(
      baseUrl,
      `/api/v1/vouchers?company=${COMPANY}&from=invalid&to=2026-07-27`,
    );
    expect(invalid.status).toBe(400);
    expect(invalid.body.code).toBe('VALIDATION_ERROR');

    const second = await sync(context, PERIOD_TWO);
    expect(second.outcome).toBe('completed');
    const storage = context.container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
    const repository = storage.getBundle().voucherRepository;
    const snapshotsAfterSecond = await repository.listSnapshots(COMPANY);
    expect(snapshotsAfterSecond.some((snapshot) => snapshot.status === 'Archived')).toBe(true);
    expect((await repository.getActiveSnapshotMetadata(COMPANY))?.snapshotId).toBe(second.snapshotId);

    responseXml = SOURCE_ERROR_XML;
    const failed = await sync(context, PERIOD_THREE);
    expect(failed).toMatchObject({
      outcome: 'failed',
      promoted: false,
      previousActiveSnapshotPreserved: true,
    });
    expect((await repository.getActiveSnapshotMetadata(COMPANY))?.snapshotId).toBe(second.snapshotId);

    const interruptedSnapshotId = 'release-interrupted-snapshot';
    await repository.createSnapshot(COMPANY, interruptedSnapshotId, PERIOD_THREE);
    await stopApplication(context);
    contexts.splice(contexts.indexOf(context), 1);

    responseXml = APPROVED_VOUCHER_FIXTURE_XML;
    context = await startContext(databasePath, transport.fetchImpl);
    baseUrl = `http://${context.config.host}:${context.config.port}`;
    const restartList = await getJson(
      baseUrl,
      `/api/v1/vouchers?company=${COMPANY}&from=2026-07-27&to=2026-07-27`,
    );
    expect(restartList.status).toBe(200);
    expect(restartList.body.data.items).toHaveLength(8);

    const recovered = await sync(context, PERIOD_THREE);
    expect(recovered.outcome).toBe('completed');
    expect(await repositoryFor(context).getSnapshot(COMPANY, interruptedSnapshotId)).toBeNull();
    expect(storageFor(context).runIntegrityCheck()).toEqual({ ok: true, message: 'ok' });

    const snapshots = await getJson(
      baseUrl,
      `/api/v1/vouchers/snapshots?company=${COMPANY}`,
    );
    expect(snapshots.status).toBe(200);
    const promotedSnapshot = snapshots.body.data.snapshots.find(
      (snapshot: { status: string }) => snapshot.status === 'Promoted',
    );
    expect(promotedSnapshot).toBeDefined();

    const snapshot = await getJson(
      baseUrl,
      `/api/v1/vouchers/snapshots/${encodeURIComponent(promotedSnapshot.id)}?company=${COMPANY}`,
    );
    expect(snapshot.status).toBe(200);
    expect(snapshot.body.data.snapshot.id).toBe(promotedSnapshot.id);

    await stopApplication(context);
    contexts.splice(contexts.indexOf(context), 1);
    expect(storageFor(context).isRunning()).toBe(false);
  });
});

const RELEASE_COMPANY_LIST_XML = `<ENVELOPE>
  <HEADER><STATUS>1</STATUS></HEADER>
  <BODY><DATA><COLLECTION>
    <COMPANY NAME="Release Fixture"><NAME>Release Fixture</NAME></COMPANY>
  </COLLECTION></DATA></BODY>
</ENVELOPE>`;

async function startContext(databasePath: string, fetchImpl: typeof fetch): Promise<ApplicationContext> {
  const context = registerServices({
    env: 'test',
    host: '127.0.0.1',
    port: await availablePort(),
    databasePath,
    tallyRequestAuditEnabled: false,
    tallyMinRequestIntervalMs: 0,
    tallySafeMode: false,
    logLevel: 'error',
    fetchImpl,
  });
  await startApplication(context);
  contexts.push(context);
  return context;
}

function sync(
  context: ApplicationContext,
  period: { dateFrom: string; dateTo: string },
) {
  return context.container.resolve<VoucherSnapshotSyncService>(
    ServiceTokens.VoucherSynchronization,
  ).synchronize({ companyId: COMPANY, ...period }, observer, notCancelled);
}

function storageFor(context: ApplicationContext): SqliteStorageService {
  return context.container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
}

function repositoryFor(context: ApplicationContext) {
  return storageFor(context).getBundle().voucherRepository;
}

async function getJson(baseUrl: string, route: string) {
  const response = await fetch(`${baseUrl}${route}`);
  return { status: response.status, body: JSON.parse(await response.text()) };
}

async function availablePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      if (!address || typeof address === 'string') {
        server.close();
        reject(new Error('Unable to allocate loopback test port.'));
        return;
      }
      server.close((error) => error ? reject(error) : resolve(address.port));
    });
  });
}
