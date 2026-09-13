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

describe('ledger sync API', () => {
  it('syncs ledgers and exposes repository endpoints', async () => {
    const databasePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-ledger-api-'));
    tempDirs.push(databasePath);
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      databasePath,
    });
    await startTestServices(context, { selectCompanyId: 'estimation' });

    const app = createTestApp(context);

    const syncResponse = await request(app).post('/sync/ledgers').send({});
    expect(syncResponse.status).toBe(200);
    expect(syncResponse.body.status).toBe('completed');
    expect(syncResponse.body.statistics.totalLedgers).toBeGreaterThan(0);

    const listResponse = await request(app).get('/ledgers?page=1&pageSize=10');
    expect(listResponse.status).toBe(200);
    expect(listResponse.body.items.length).toBeGreaterThan(0);

    const firstId = listResponse.body.items[0].id;
    const detailResponse = await request(app).get(`/ledgers/${firstId}`);
    expect(detailResponse.status).toBe(200);
    expect(detailResponse.body.ledger.id).toBe(firstId);

    const statusResponse = await request(app).get('/sync/ledgers/status');
    expect(statusResponse.status).toBe(200);
    expect(statusResponse.body.progress.status).toBe('completed');
    expect(statusResponse.body.progress.totalExpected).toBe(syncResponse.body.statistics.totalLedgers);

    const statsResponse = await request(app).get('/sync/ledgers/statistics');
    expect(statsResponse.status).toBe(200);
    expect(statsResponse.body.statistics.totalLedgers).toBeGreaterThan(0);
  });

  it('allows POST /sync/ledgers through read-only middleware', async () => {
    const response = await request(createTestApp()).post('/sync/ledgers').send({});
    expect(response.status).not.toBe(405);
  });
});
