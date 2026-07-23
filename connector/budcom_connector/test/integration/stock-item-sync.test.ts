import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';

import { createMasterDataMockFetch } from '../helpers/mock-fetch.js';
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

describe('stock item sync API', () => {
  it('syncs stock items and exposes repository endpoints', async () => {
    const databasePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-stock-api-'));
    tempDirs.push(databasePath);
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      databasePath,
    });
    await startTestServices(context, { selectCompanyId: 'estimation' });

    const stockItemSync = context.container.resolve(
      (await import('../../src/core/tokens.js')).ServiceTokens.StockItemSync,
    ) as import('../../src/services/stock-item/stock-item-sync.service.js').StockItemSyncService;
    await stockItemSync.start();

    const app = createTestApp(context);

    const syncResponse = await request(app).post('/sync/stock-items').send({});
    expect(syncResponse.status).toBe(200);
    expect(syncResponse.body.status).toBe('completed');
    expect(syncResponse.body.statistics.totalStockItems).toBeGreaterThan(0);

    const listResponse = await request(app).get('/stock-items?page=1&pageSize=10');
    expect(listResponse.status).toBe(200);
    expect(listResponse.body.items.length).toBeGreaterThan(0);

    const firstId = listResponse.body.items[0].id;
    const detailResponse = await request(app).get(`/stock-items/${firstId}`);
    expect(detailResponse.status).toBe(200);
    expect(detailResponse.body.stockItem.id).toBe(firstId);

    const statusResponse = await request(app).get('/sync/stock-items/status');
    expect(statusResponse.status).toBe(200);
    expect(statusResponse.body.progress.status).toBe('completed');

    const statsResponse = await request(app).get('/sync/stock-items/statistics');
    expect(statsResponse.status).toBe(200);
    expect(statsResponse.body.statistics.totalStockItems).toBeGreaterThan(0);
  });

  it('allows POST /sync/stock-items through read-only middleware', async () => {
    const response = await request(createTestApp()).post('/sync/stock-items').send({});
    expect(response.status).not.toBe(405);
  });

  it('permits ledger and stock item sync runs concurrently per resource_kind', async () => {
    const databasePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-dual-sync-'));
    tempDirs.push(databasePath);
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      databasePath,
    });
    await startTestServices(context, { selectCompanyId: 'estimation' });

    const { ServiceTokens } = await import('../../src/core/tokens.js');
    const stockItemSync = context.container.resolve(
      ServiceTokens.StockItemSync,
    ) as import('../../src/services/stock-item/stock-item-sync.service.js').StockItemSyncService;
    await stockItemSync.start();

    const app = createTestApp(context);
    const ledgerSync = await request(app).post('/sync/ledgers').send({});
    const stockSync = await request(app).post('/sync/stock-items').send({});
    expect(ledgerSync.status).toBe(200);
    expect(stockSync.status).toBe(200);
  });
});
