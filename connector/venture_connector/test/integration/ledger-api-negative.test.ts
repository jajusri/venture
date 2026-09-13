import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';

import { createMasterDataMockFetch } from '../helpers/mock-fetch.js';
import { sampleLedgerDetails } from '../helpers/ledger-fixtures.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';

const tempDirs: string[] = [];

afterEach(async () => {
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
    } catch {
      // ignore Windows cleanup races
    }
  }
});

function assertSafeErrorBody(body: unknown): void {
  const serialized = JSON.stringify(body);
  expect(serialized).not.toMatch(/(?:^|[^A-Z])SELECT |INSERT INTO|UPDATE |DELETE FROM/i);
  expect(serialized).not.toMatch(/[A-Za-z]:\\|\/tmp\/|node_modules/i);
  expect(serialized).not.toMatch(/<ENVELOPE|<LEDGER|<TALLYREQUEST/i);
  expect(serialized).not.toMatch(/stack|trace|Exception/i);
}

async function setupLedgerApi(options: { readonly selectCompanyId?: string } = {}) {
  const databasePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-ledger-neg-'));
  tempDirs.push(databasePath);
  const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
  const context = createTestContext({
    fetchImpl,
    tallyRetryMaxAttempts: 1,
    databasePath,
  });
  await startTestServices(context, options.selectCompanyId ? { selectCompanyId: options.selectCompanyId } : {});
  const app = createTestApp(context);
  const { ServiceTokens } = await import('../../src/core/tokens.js');
  const ledgerSync = context.container.resolve(
    ServiceTokens.LedgerSync,
  ) as import('../../src/services/ledger/ledger-sync.service.js').LedgerSyncService;
  await ledgerSync.start();
  return { app, context, ledgerSync, databasePath };
}

describe('ledger API negative paths and isolation', () => {
  it('rejects list/detail/sync when no company is selected', async () => {
    const { app } = await setupLedgerApi();
    expect((await request(app).get('/ledgers')).status).toBe(400);
    expect((await request(app).get('/ledgers/name:missing')).status).toBe(400);
    expect((await request(app).post('/sync/ledgers').send({})).status).toBe(400);
    expect((await request(app).post('/sync/ledgers/contact-details').send({})).status).toBe(400);
  });

  it('allows the bulk contact-details route through the read-only write-gate, but the still-disabled operation fails safely rather than reaching Tally', async () => {
    const { app } = await setupLedgerApi({ selectCompanyId: 'estimation' });
    const response = await request(app).post('/sync/ledgers/contact-details').send({});
    // Not 405 READ_ONLY_VIOLATION -- confirms the route is correctly registered in
    // ALLOWED_WRITE_ROUTES. Not 200 either -- LEDGERS_CONTACT_DETAILS.render() still throws
    // until its Fetch list is live-validated (operation-registry.ts, TD-040-style gate); that
    // throw surfaces as SERVICE_UNAVAILABLE (503) via the same extraction-failure error mapping
    // every other master-data read uses, proving the safety gate is enforced end-to-end through
    // the real route, not just at the unit level.
    expect(response.status).not.toBe(405);
    expect(response.status).toBe(503);
    assertSafeErrorBody(response.body);
  });

  it('does not return another company ledger by id', async () => {
    const { app, context } = await setupLedgerApi({ selectCompanyId: 'estimation' });
    const storage = context.container.resolve(
      (await import('../../src/core/tokens.js')).ServiceTokens.LocalDatabase,
    ) as import('../../src/storage/sqlite/storage-service.js').SqliteStorageService;
    await storage.getBundle().ledgerRepository.upsertMany('other-co', [
      sampleLedgerDetails({ id: 'guid:secret', name: 'Secret Ledger', guid: 'secret' }),
    ]);
    const response = await request(app).get('/ledgers/guid:secret');
    expect(response.status).toBe(404);
    assertSafeErrorBody(response.body);
    expect(JSON.stringify(response.body)).not.toContain('Secret Ledger');
  });

  it('does not return another company sync run via ledger route', async () => {
    const { app, context } = await setupLedgerApi({ selectCompanyId: 'estimation' });
    const storage = context.container.resolve(
      (await import('../../src/core/tokens.js')).ServiceTokens.LocalDatabase,
    ) as import('../../src/storage/sqlite/storage-service.js').SqliteStorageService;
    const otherRun = storage.getBundle().syncRunRepository.createRun({
      companyId: 'other-co',
      resourceKind: 'ledgers',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: '5',
    });
    storage.getBundle().syncRunRepository.updateRun({
      ...otherRun,
      status: 'completed',
      completedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });
    const response = await request(app).get(`/sync/ledgers/runs/${otherRun.syncRunId}`);
    expect(response.status).toBe(404);
    assertSafeErrorBody(response.body);
  });

  it('clamps invalid pagination and sort inputs safely', async () => {
    const { app, context } = await setupLedgerApi({ selectCompanyId: 'estimation' });
    const storage = context.container.resolve(
      (await import('../../src/core/tokens.js')).ServiceTokens.LocalDatabase,
    ) as import('../../src/storage/sqlite/storage-service.js').SqliteStorageService;
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({ id: 'name:alpha', name: 'Alpha' }),
    ]);
    const response = await request(app).get(
      '/ledgers?page=0&pageSize=999&sortBy=DROP%20TABLE&sortDirection=sideways',
    );
    expect(response.status).toBe(200);
    expect(response.body.pagination.page).toBe(1);
    expect(response.body.pagination.pageSize).toBe(100);
    expect(response.body.items[0]?.name).toBe('Alpha');
  });
});
