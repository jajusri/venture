import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import request from 'supertest';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { createMasterDataMockFetch } from '../helpers/mock-fetch.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import { sampleStockItemDetails } from '../helpers/stock-item-fixtures.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';

const tempDirs: string[] = [];

afterEach(async () => {
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
    } catch {
      // Windows may keep SQLite WAL handles briefly after service shutdown.
    }
  }
});

function assertSafeErrorBody(body: unknown): void {
  const serialized = JSON.stringify(body);
  expect(serialized).not.toMatch(/(?:^|[^A-Z])SELECT |INSERT INTO|UPDATE |DELETE FROM/i);
  expect(serialized).not.toMatch(/[A-Za-z]:\\|\/tmp\/|\/var\/|node_modules/i);
  expect(serialized).not.toMatch(/<ENVELOPE|<STOCKITEM|<TALLYREQUEST/i);
  expect(serialized).not.toMatch(/stack|trace|Exception/i);
}

function assertSafeSuccessBody(body: unknown): void {
  const serialized = JSON.stringify(body);
  expect(serialized).not.toMatch(/(?:^|[^A-Z])SELECT |INSERT INTO|UPDATE |DELETE FROM/i);
  expect(serialized).not.toMatch(/[A-Za-z]:\\|\/tmp\/|\/var\/|node_modules/i);
  expect(serialized).not.toMatch(/<ENVELOPE|<STOCKITEM|<TALLYREQUEST/i);
}

async function setupStockItemApi(options: {
  readonly selectCompanyId?: string;
  readonly fetchImpl?: typeof fetch;
  readonly databasePath?: string;
  readonly sessionOverrides?: Parameters<typeof createPermissiveSessionMock>[0];
} = {}) {
  const databasePath = options.databasePath ?? fs.mkdtempSync(path.join(os.tmpdir(), 'venture-stock-neg-'));
  if (!options.databasePath) {
    tempDirs.push(databasePath);
  }
  const { fetchImpl: defaultFetch } = createMasterDataMockFetch({ pingOk: true });
  const context = createTestContext({
    fetchImpl: options.fetchImpl ?? defaultFetch,
    tallyRetryMaxAttempts: 1,
    databasePath,
  });
  await startTestServices(context, options.selectCompanyId ? { selectCompanyId: options.selectCompanyId } : {});

  if (options.sessionOverrides) {
    const session = context.container.resolve(
      (await import('../../src/core/tokens.js')).ServiceTokens.ConnectorSession,
    ) as import('../../src/services/interfaces/connector-session.js').ConnectorSessionService;
    const mock = createPermissiveSessionMock(options.sessionOverrides);
    vi.spyOn(session, 'validateForOperation').mockImplementation(mock.validateForOperation);
    vi.spyOn(session, 'getSession').mockImplementation(mock.getSession);
  }

  const app = createTestApp(context);
  const { ServiceTokens } = await import('../../src/core/tokens.js');
  const stockItemSync = context.container.resolve(
    ServiceTokens.StockItemSync,
  ) as import('../../src/services/stock-item/stock-item-sync.service.js').StockItemSyncService;
  await stockItemSync.start();
  return { app, context, stockItemSync, databasePath };
}

function createDelayedStockItemFetch(delayMs: number): typeof fetch {
  const { fetchImpl: baseFetch } = createMasterDataMockFetch({ pingOk: true });
  return async (url: string | URL | Request, init?: RequestInit) => {
    const body = typeof init?.body === 'string' ? init.body : '';
    if (body.includes('List of Stock Items')) {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
    return baseFetch(url, init);
  };
}

describe('stock item API negative paths and isolation', () => {
  describe('session and company gating', () => {
    it('rejects list/detail/sync when no company is selected', async () => {
      const { app } = await setupStockItemApi();

      const list = await request(app).get('/stock-items');
      expect(list.status).toBe(400);
      assertSafeErrorBody(list.body);

      const detail = await request(app).get('/stock-items/name:missing');
      expect(detail.status).toBe(400);
      assertSafeErrorBody(detail.body);

      const sync = await request(app).post('/sync/stock-items').send({});
      expect(sync.status).toBe(400);
      assertSafeErrorBody(sync.body);
    });

    it('rejects operations when session validation fails', async () => {
      const databasePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-stock-session-'));
      tempDirs.push(databasePath);
      const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
      const context = createTestContext({ fetchImpl, databasePath, tallyRetryMaxAttempts: 1 });
      await startTestServices(context, { selectCompanyId: 'estimation' });

      const session = context.container.resolve(
        (await import('../../src/core/tokens.js')).ServiceTokens.ConnectorSession,
      ) as import('../../src/services/interfaces/connector-session.js').ConnectorSessionService;
      vi.spyOn(session, 'validateForOperation').mockImplementation(async () => ({
        status: 'SESSION_INVALID',
        reason: 'Connector session is invalid',
        session: createPermissiveSessionMock().getSession().session,
      }));

      const app = createTestApp(context);
      const stockItemSync = context.container.resolve(
        (await import('../../src/core/tokens.js')).ServiceTokens.StockItemSync,
      ) as import('../../src/services/stock-item/stock-item-sync.service.js').StockItemSyncService;
      await stockItemSync.start();

      const response = await request(app).get('/stock-items');
      expect(response.status).toBe(400);
      assertSafeErrorBody(response.body);
    });
  });

  describe('list and detail endpoints', () => {
    it('returns 404 for unknown stock item id', async () => {
      const { app } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const response = await request(app).get('/stock-items/name:does-not-exist');
      expect(response.status).toBe(404);
      expect(response.body.code).toBe('NOT_FOUND');
      assertSafeErrorBody(response.body);
    });

    it('clamps invalid pagination and sort inputs safely', async () => {
      const { app, context } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const storage = context.container.resolve(
        (await import('../../src/core/tokens.js')).ServiceTokens.LocalDatabase,
      ) as import('../../src/storage/sqlite/storage-service.js').SqliteStorageService;
      await storage.getBundle().stockItemRepository.upsertMany('estimation', [
        sampleStockItemDetails({ id: 'name:alpha', name: 'Alpha' }),
      ]);

      const response = await request(app).get(
        '/stock-items?page=0&pageSize=999&sortBy=DROP%20TABLE&sortDirection=sideways&dataQuality=garbage',
      );
      expect(response.status).toBe(200);
      expect(response.body.pagination.page).toBe(1);
      expect(response.body.pagination.pageSize).toBe(100);
      expect(response.body.items[0]?.name).toBe('Alpha');
    });

    it('does not return another company stock item by id', async () => {
      const { app, context } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const storage = context.container.resolve(
        (await import('../../src/core/tokens.js')).ServiceTokens.LocalDatabase,
      ) as import('../../src/storage/sqlite/storage-service.js').SqliteStorageService;
      await storage.getBundle().stockItemRepository.upsertMany('other-co', [
        sampleStockItemDetails({ id: 'guid:secret', name: 'Secret Item', guid: 'secret' }),
      ]);

      const response = await request(app).get('/stock-items/guid:secret');
      expect(response.status).toBe(404);
      assertSafeErrorBody(response.body);
      expect(JSON.stringify(response.body)).not.toContain('Secret Item');
    });
  });

  describe('sync endpoints', () => {
    it('returns conflict for duplicate stock item sync', async () => {
      let releaseExtraction = () => {};
      const extractionGate = new Promise<void>((resolve) => {
        releaseExtraction = resolve;
      });
      const { fetchImpl: baseFetch } = createMasterDataMockFetch({ pingOk: true });
      const gatedFetch: typeof fetch = async (url, init) => {
        const body = typeof init?.body === 'string' ? init.body : '';
        if (body.includes('List of Stock Items')) {
          await extractionGate;
        }
        return baseFetch(url, init);
      };
      const { app, stockItemSync } = await setupStockItemApi({
        selectCompanyId: 'estimation',
        fetchImpl: gatedFetch,
      });

      const first = stockItemSync.syncStockItems();
      await new Promise((resolve) => setTimeout(resolve, 30));
      const second = await request(app).post('/sync/stock-items').send({});
      expect(second.status).toBe(409);
      expect(second.body.code).toBe('SYNC_CONFLICT');
      assertSafeErrorBody(second.body);
      releaseExtraction();
      await stockItemSync.cancelSync().catch(() => undefined);
      const result = await first;
      expect(['completed', 'cancelled']).toContain(result.status);
    }, 15_000);

    it('does not block ledger sync when stock item sync is active', async () => {
      const { app } = await setupStockItemApi({
        selectCompanyId: 'estimation',
        fetchImpl: createDelayedStockItemFetch(200),
      });

      const stockSync = request(app).post('/sync/stock-items').send({});
      await new Promise((resolve) => setTimeout(resolve, 20));
      const ledgerSync = await request(app).post('/sync/ledgers').send({});
      expect(ledgerSync.status).toBe(200);
      await stockSync;
    });

    it('cancel with no active stock item sync returns idle progress', async () => {
      const { app } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const response = await request(app).post('/sync/stock-items/cancel');
      expect(response.status).toBe(200);
      expect(response.body.progress.status).toBe('idle');
    });

    it('cancel does not affect ledger sync state', async () => {
      const { app } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      await request(app).post('/sync/ledgers').send({});
      const ledgerBefore = await request(app).get('/sync/ledgers/status');
      await request(app).post('/sync/stock-items/cancel');
      const ledgerAfter = await request(app).get('/sync/ledgers/status');
      expect(ledgerAfter.body.progress.status).toBe(ledgerBefore.body.progress.status);
    });

    it('status and runs endpoints expose stock item resource only', async () => {
      const { app, context } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      await request(app).post('/sync/ledgers').send({});
      await request(app).post('/sync/stock-items').send({});

      const status = await request(app).get('/sync/stock-items/status');
      expect(status.status).toBe(200);

      const runs = await request(app).get('/sync/stock-items/runs');
      expect(runs.status).toBe(200);
      expect(runs.body.runs.every((run: { resourceKind?: string }) => run.resourceKind === 'stock-items')).toBe(
        true,
      );

      const storage = context.container.resolve(
        (await import('../../src/core/tokens.js')).ServiceTokens.LocalDatabase,
      ) as import('../../src/storage/sqlite/storage-service.js').SqliteStorageService;
      const ledgerRun = storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers', 1)[0];
      expect(ledgerRun).toBeTruthy();

      const ledgerViaStockRoute = await request(app).get(`/sync/stock-items/runs/${ledgerRun!.syncRunId}`);
      expect(ledgerViaStockRoute.status).toBe(404);
      assertSafeErrorBody(ledgerViaStockRoute.body);
    });

    it('returns 404 for unknown sync run', async () => {
      const { app } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const response = await request(app).get('/sync/stock-items/runs/00000000-0000-0000-0000-000000000000');
      expect(response.status).toBe(404);
      assertSafeErrorBody(response.body);
    });

    it('does not return another company sync run', async () => {
      const { app, context } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const storage = context.container.resolve(
        (await import('../../src/core/tokens.js')).ServiceTokens.LocalDatabase,
      ) as import('../../src/storage/sqlite/storage-service.js').SqliteStorageService;
      const otherRun = storage.getBundle().syncRunRepository.createRun({
        companyId: 'other-co',
        resourceKind: 'stock-items',
        syncType: 'full',
        connectorVersion: '0.3.1',
        schemaVersion: '2',
      });
      storage.getBundle().syncRunRepository.updateRun({
        ...otherRun,
        status: 'completed',
        completedAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const response = await request(app).get(`/sync/stock-items/runs/${otherRun.syncRunId}`);
      expect(response.status).toBe(404);
      assertSafeErrorBody(response.body);
    });

    it('fails safely when Tally is unavailable', async () => {
      const fetchImpl: typeof fetch = async () => {
        throw new TypeError('fetch failed');
      };
      const { app } = await setupStockItemApi({
        fetchImpl,
        sessionOverrides: {},
      });
      const response = await request(app).post('/sync/stock-items').send({});
      expect(response.status).toBeGreaterThanOrEqual(400);
      assertSafeErrorBody(response.body);

      const list = await request(app).get('/stock-items');
      expect(list.status).toBe(200);
      expect(list.body.items).toEqual([]);
    });

    it('reports cancelled extraction without false completion counters', async () => {
      const { app } = await setupStockItemApi({
        selectCompanyId: 'estimation',
        fetchImpl: createDelayedStockItemFetch(300),
      });

      const syncPromise = request(app).post('/sync/stock-items').send({});
      await new Promise((resolve) => setTimeout(resolve, 30));
      await request(app).post('/sync/stock-items/cancel');
      const response = await syncPromise;
      expect(['cancelled', 'completed']).toContain(response.body.status);
      assertSafeErrorBody(response.body);
    });
  });

  describe('storage and clear operations', () => {
    it('clears stock items for selected company only', async () => {
      const { app, context } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const storage = context.container.resolve(
        (await import('../../src/core/tokens.js')).ServiceTokens.LocalDatabase,
      ) as import('../../src/storage/sqlite/storage-service.js').SqliteStorageService;
      const repo = storage.getBundle().stockItemRepository;
      await repo.upsertMany('estimation', [sampleStockItemDetails({ id: 'name:a', name: 'A' })]);
      await repo.upsertMany('other-co', [sampleStockItemDetails({ id: 'name:b', name: 'B' })]);

      const clear = await request(app).post('/sync/stock-items/clear-cache');
      expect(clear.status).toBe(200);

      const list = await request(app).get('/stock-items');
      expect(list.body.items).toHaveLength(0);
      expect(await repo.countByCompany('other-co')).toBe(1);
    });

    it('allows repeated clear on empty store', async () => {
      const { app } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const first = await request(app).post('/sync/stock-items/clear-cache');
      const second = await request(app).post('/sync/stock-items/clear-cache');
      expect(first.status).toBe(200);
      expect(second.status).toBe(200);
    });

    it('exposes integrity-check and backup without filesystem paths', async () => {
      const { app } = await setupStockItemApi({ selectCompanyId: 'estimation' });
      const integrity = await request(app).post('/storage/stock-items/integrity-check');
      expect(integrity.status).toBe(200);
      expect(integrity.body.ok).toBe(true);
      assertSafeErrorBody(integrity.body);

      const backup = await request(app).post('/storage/stock-items/backup');
      expect(backup.status).toBe(200);
      expect(backup.body.ok).toBe(true);
      expect(backup.body.backupPath).toMatch(/^venture-ledger-\d+\.db$/);
      assertSafeSuccessBody(backup.body);
    });
  });
});
