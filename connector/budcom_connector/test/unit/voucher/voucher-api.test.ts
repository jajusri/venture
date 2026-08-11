import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import express from 'express';
import request from 'supertest';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import type { VoucherDetails } from '../../../src/erp/voucher/voucher-domain.js';
import { createVouchersRouter } from '../../../src/api/routes/vouchers.js';
import { createErrorMiddleware } from '../../../src/infrastructure/errors/error-handler.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { VoucherApplicationServiceImpl } from '../../../src/services/voucher/voucher-application.service.js';
import { SqliteDatabase } from '../../../src/storage/sqlite/sqlite-database.js';
import { SqliteVoucherRepository } from '../../../src/storage/sqlite/sqlite-voucher-repository.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { VoucherXmlMapper } from '../../../src/tally/voucher/voucher-mapper.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';
import { APPROVED_VOUCHER_FIXTURE_XML } from '../../fixtures/vouchers/approved-voucher-fixture.js';
import type { ConnectorSessionService } from '../../../src/services/interfaces/connector-session.js';
import type { VoucherSnapshotSyncService } from '../../../src/services/voucher/voucher-application.interface.js';

const PERIOD = { dateFrom: '2026-07-27', dateTo: '2026-07-27' };
const tempDirs: string[] = [];
let database: SqliteDatabase;
let repository: SqliteVoucherRepository;
let app: express.Express;
let vouchers: readonly VoucherDetails[];

beforeAll(() => {
  const parser = new VoucherCollectionParser(new TallyXmlResponseParser());
  const parsed = parser.parse(APPROVED_VOUCHER_FIXTURE_XML);
  if (parsed.status !== 'records') throw new Error('Voucher fixture did not parse.');
  const mapper = new VoucherXmlMapper(parser);
  vouchers = parsed.records.map((record) => {
    const mapped = mapper.map(record);
    if (!mapped.value) throw new Error('Voucher fixture did not map.');
    return mapped.value;
  });
});

beforeEach(() => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-voucher-api-'));
  tempDirs.push(dir);
  database = new SqliteDatabase({ databasePath: path.join(dir, 'api.db') });
  database.open();
  repository = new SqliteVoucherRepository(database);
  const application = new VoucherApplicationServiceImpl(() => repository);
  app = express();
  app.use(createVouchersRouter(application));
  app.use(createErrorMiddleware(createLogger({ service: 'voucher-api-test', level: 'error' })));
});

afterEach(async () => {
  database.close();
  await new Promise((resolve) => setTimeout(resolve, 20));
  for (const dir of tempDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

async function promote(
  company: string,
  snapshotId: string,
  items: readonly VoucherDetails[] = vouchers,
): Promise<void> {
  await repository.createSnapshot(company, snapshotId, PERIOD);
  await repository.writeVoucherBatch(company, snapshotId, items);
  await repository.finalizeSnapshot(company, snapshotId, '2026-07-27T12:00:00.000Z');
  await repository.promoteSnapshot(company, snapshotId, '2026-07-27T12:01:00.000Z');
}

const listPath = '/api/v1/vouchers?company=company-a&from=2026-07-27&to=2026-07-27';

describe('production Voucher API', () => {
  it('runs the controlled public synchronization flow for the selected company', async () => {
    const synchronize = vi.fn().mockResolvedValue({
      company: 'company-a',
      period: PERIOD,
      snapshotId: 'snapshot-sync',
      outcome: 'completed',
      responseStatus: 'records',
      candidateVoucherCount: 20,
      acceptedVoucherCount: 20,
      rejectedVoucherCount: 0,
      incompleteVoucherCount: 0,
      ledgerEntryCount: 40,
      inventoryEntryCount: 10,
      allocationCount: 0,
      phaseDurationsMs: {},
      validationIssues: [],
      repositoryFailureCode: null,
      previousActiveSnapshotPreserved: false,
      promotionOccurred: true,
      failureReason: null,
      notificationFailureCount: 0,
      startedAt: '2026-07-27T12:00:00.000Z',
      finishedAt: '2026-07-27T12:00:01.000Z',
      durationMs: 1_000,
      vouchersExtracted: 20,
      vouchersPersisted: 20,
      droppedVouchers: 0,
      validationWarningCount: 0,
      rollbackStatus: 'not_required',
      promoted: true,
    });
    const synchronization = { synchronize } as unknown as VoucherSnapshotSyncService;
    const connectorSession = {
      getSession: () => ({
        session: { selectedCompany: { id: 'company-a', name: 'Company A' } },
      }),
      validateForOperation: vi.fn().mockResolvedValue({ status: 'SUCCESS' }),
    } as unknown as ConnectorSessionService;
    const syncApp = express();
    syncApp.use(express.json());
    syncApp.use(createVouchersRouter(
      new VoucherApplicationServiceImpl(() => repository),
      synchronization,
      connectorSession,
    ));
    syncApp.use(createErrorMiddleware(createLogger({
      service: 'voucher-sync-api-test',
      level: 'error',
    })));

    const response = await request(syncApp)
      .post('/sync/vouchers')
      .send({ dateFrom: PERIOD.dateFrom, dateTo: PERIOD.dateTo });

    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({
      status: 'completed',
      syncRunId: 'snapshot-sync',
      validationIssueCount: 0,
      statistics: { totalVouchers: 20 },
      progress: {
        itemsProcessed: 20,
        itemsAdded: 20,
        itemsSkipped: 0,
        itemsFailed: 0,
      },
    });
    expect(synchronize).toHaveBeenCalledWith(
      { companyId: 'company-a', ...PERIOD },
      expect.any(Object),
      { requested: false },
    );
  });

  it('defaults the sync window to the IST business day, not the UTC calendar day, when no dates are given', async () => {
    // Regression test for the V3 acceptance defect: a POST with no explicit
    // dateFrom/dateTo, issued while UTC still shows the previous calendar day
    // (00:00-05:29 IST), must still request a window ending on today's IST date.
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-10T22:39:18.671Z')); // 2026-08-11 04:09 IST
    try {
      const synchronize = vi.fn().mockResolvedValue({
        company: 'company-a',
        period: { dateFrom: '2026-07-13', dateTo: '2026-08-11' },
        snapshotId: 'snapshot-sync',
        outcome: 'completed',
        responseStatus: 'records',
        candidateVoucherCount: 0,
        acceptedVoucherCount: 0,
        rejectedVoucherCount: 0,
        incompleteVoucherCount: 0,
        ledgerEntryCount: 0,
        inventoryEntryCount: 0,
        allocationCount: 0,
        phaseDurationsMs: {},
        validationIssues: [],
        repositoryFailureCode: null,
        previousActiveSnapshotPreserved: false,
        promotionOccurred: true,
        failureReason: null,
        notificationFailureCount: 0,
        startedAt: '2026-08-10T22:39:18.000Z',
        finishedAt: '2026-08-10T22:39:19.000Z',
        durationMs: 1_000,
        vouchersExtracted: 0,
        vouchersPersisted: 0,
        droppedVouchers: 0,
        validationWarningCount: 0,
        rollbackStatus: 'not_required',
        promoted: true,
      });
      const synchronization = { synchronize } as unknown as VoucherSnapshotSyncService;
      const connectorSession = {
        getSession: () => ({
          session: { selectedCompany: { id: 'company-a', name: 'Company A' } },
        }),
        validateForOperation: vi.fn().mockResolvedValue({ status: 'SUCCESS' }),
      } as unknown as ConnectorSessionService;
      const syncApp = express();
      syncApp.use(express.json());
      syncApp.use(createVouchersRouter(
        new VoucherApplicationServiceImpl(() => repository),
        synchronization,
        connectorSession,
      ));
      syncApp.use(createErrorMiddleware(createLogger({
        service: 'voucher-sync-default-window-test',
        level: 'error',
      })));

      const response = await request(syncApp).post('/sync/vouchers').send({});

      expect(response.status).toBe(200);
      expect(synchronize).toHaveBeenCalledWith(
        { companyId: 'company-a', dateFrom: '2026-07-13', dateTo: '2026-08-11' },
        expect.any(Object),
        { requested: false },
      );
    } finally {
      vi.useRealTimers();
    }
  });

  it('derives a default dateFrom relative to an explicitly-supplied dateTo', async () => {
    const synchronize = vi.fn().mockResolvedValue({
      company: 'company-a',
      period: { dateFrom: '2026-01-02', dateTo: '2026-01-31' },
      snapshotId: 'snapshot-sync',
      outcome: 'completed',
      responseStatus: 'empty',
      candidateVoucherCount: 0,
      acceptedVoucherCount: 0,
      rejectedVoucherCount: 0,
      incompleteVoucherCount: 0,
      ledgerEntryCount: 0,
      inventoryEntryCount: 0,
      allocationCount: 0,
      phaseDurationsMs: {},
      validationIssues: [],
      repositoryFailureCode: null,
      previousActiveSnapshotPreserved: false,
      promotionOccurred: true,
      failureReason: null,
      notificationFailureCount: 0,
      startedAt: '2026-01-31T00:00:00.000Z',
      finishedAt: '2026-01-31T00:00:01.000Z',
      durationMs: 1_000,
      vouchersExtracted: 0,
      vouchersPersisted: 0,
      droppedVouchers: 0,
      validationWarningCount: 0,
      rollbackStatus: 'not_required',
      promoted: true,
    });
    const synchronization = { synchronize } as unknown as VoucherSnapshotSyncService;
    const connectorSession = {
      getSession: () => ({
        session: { selectedCompany: { id: 'company-a', name: 'Company A' } },
      }),
      validateForOperation: vi.fn().mockResolvedValue({ status: 'SUCCESS' }),
    } as unknown as ConnectorSessionService;
    const syncApp = express();
    syncApp.use(express.json());
    syncApp.use(createVouchersRouter(
      new VoucherApplicationServiceImpl(() => repository),
      synchronization,
      connectorSession,
    ));
    syncApp.use(createErrorMiddleware(createLogger({
      service: 'voucher-sync-explicit-dateto-test',
      level: 'error',
    })));

    const response = await request(syncApp).post('/sync/vouchers').send({ dateTo: '2026-01-31' });

    expect(response.status).toBe(200);
    expect(synchronize).toHaveBeenCalledWith(
      { companyId: 'company-a', dateFrom: '2026-01-02', dateTo: '2026-01-31' },
      expect.any(Object),
      { requested: false },
    );
  });

  it('lists stable public records with deterministic pagination and sorting', async () => {
    await promote('company-a', 'snapshot-a');
    const response = await request(app).get(`${listPath}&page=1&pageSize=2&sort=voucherNumber:desc`);

    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({
      schemaVersion: '1.0.0',
      data: {
        companyId: 'company-a',
        pagination: { page: 1, pageSize: 2, totalItems: vouchers.length },
      },
    });
    expect(response.body.data.items).toHaveLength(2);
    expect(response.body.data.items[0]).toEqual(expect.objectContaining({
      id: expect.any(String),
      date: '2026-07-27',
      type: expect.any(String),
    }));
    expect(response.body.data.items[0]).not.toHaveProperty('guid');
    expect(response.body.data.items[0]).not.toHaveProperty('voucherJson');
  });

  it('omits ledger/inventory/narration/effectiveDate by default (ordinary list/browse traffic)', async () => {
    await promote('company-a', 'snapshot-a');
    const response = await request(app).get(listPath);

    expect(response.status).toBe(200);
    for (const item of response.body.data.items) {
      expect(item).not.toHaveProperty('ledgerEntries');
      expect(item).not.toHaveProperty('inventoryEntries');
      expect(item).not.toHaveProperty('narration');
      expect(item).not.toHaveProperty('effectiveDate');
    }
  });

  it('includes complete ledger/inventory/narration/effectiveDate when includeDetails=true, in the same bounded response', async () => {
    await promote('company-a', 'snapshot-a');
    const response = await request(app).get(`${listPath}&includeDetails=true`);

    expect(response.status).toBe(200);
    expect(response.body.data.items).toHaveLength(vouchers.length);
    for (const item of response.body.data.items) {
      const source = vouchers.find((voucher) => voucher.voucherId === item.id)!;
      expect(item.ledgerEntries).toHaveLength(source.ledgerEntries.length);
      expect(item.inventoryEntries).toHaveLength(source.inventoryEntries.length);
      expect(item).toHaveProperty('narration');
      expect(item).toHaveProperty('effectiveDate');
    }
    // Same pagination envelope as the summary-only response — no new request shape.
    expect(response.body.data.pagination).toMatchObject({ totalItems: vouchers.length });
  });

  it('ignores any value for includeDetails other than the literal string "true"', async () => {
    await promote('company-a', 'snapshot-a');
    const response = await request(app).get(`${listPath}&includeDetails=yes`);

    expect(response.status).toBe(200);
    expect(response.body.data.items[0]).not.toHaveProperty('ledgerEntries');
  });

  it('searches by type, number, party, and free text', async () => {
    await promote('company-a', 'snapshot-a');
    const target = vouchers.find((voucher) => voucher.voucherNumber && voucher.partyName)!;
    const query = new URLSearchParams({
      company: 'company-a',
      from: PERIOD.dateFrom,
      to: PERIOD.dateTo,
      voucherType: target.voucherType,
      voucherNumber: target.voucherNumber!,
      partyName: target.partyName!,
      q: target.partyName!,
    });
    const response = await request(app).get(`/api/v1/vouchers/search?${query}`);

    expect(response.status).toBe(200);
    expect(response.body.data.items).toHaveLength(1);
    expect(response.body.data.items[0].id).toBe(target.voucherId);
  });

  it('returns Voucher details without persistence or source identity fields', async () => {
    await promote('company-a', 'snapshot-a');
    const target = vouchers[0]!;
    const response = await request(app).get(
      `/api/v1/vouchers/${encodeURIComponent(target.voucherId)}?company=company-a`,
    );

    expect(response.status).toBe(200);
    expect(response.body.data.voucher.id).toBe(target.voucherId);
    expect(response.body.data.voucher).toHaveProperty('ledgerEntries');
    const sourceInventory = target.inventoryEntries[0];
    if (sourceInventory) {
      expect(response.body.data.voucher.inventoryEntries[0]).toMatchObject({
        itemName: sourceInventory.itemName,
        quantity: sourceInventory.quantity ?? null,
        rate: sourceInventory.rate ?? null,
      });
    }
    expect(response.body.data.voucher).not.toHaveProperty('guid');
    expect(response.body.data.voucher).not.toHaveProperty('masterId');
    expect(response.body.data.voucher).not.toHaveProperty('allocations');
  });

  it('lists and looks up company-scoped snapshots', async () => {
    await promote('company-a', 'snapshot-a');
    await promote('company-b', 'snapshot-b', [vouchers[0]!]);

    const list = await request(app).get('/api/v1/vouchers/snapshots?company=company-a');
    const detail = await request(app)
      .get('/api/v1/vouchers/snapshots/snapshot-a?company=company-a');
    const isolated = await request(app)
      .get('/api/v1/vouchers/snapshots/snapshot-b?company=company-a');

    expect(list.status).toBe(200);
    expect(list.body.data.snapshots).toHaveLength(1);
    expect(detail.status).toBe(200);
    expect(detail.body.data.snapshot).toMatchObject({
      id: 'snapshot-a',
      status: 'Promoted',
      voucherCount: vouchers.length,
    });
    expect(isolated.status).toBe(404);
  });

  it.each([
    ['/api/v1/vouchers?from=2026-07-27&to=2026-07-27', 'company'],
    ['/api/v1/vouchers?company=a&from=2026-02-30&to=2026-07-27', 'from'],
    ['/api/v1/vouchers?company=a&from=2026-07-28&to=2026-07-27', 'to'],
    [`${listPath}&page=0`, 'page'],
    [`${listPath}&pageSize=101`, 'pageSize'],
    [`${listPath}&sort=DROP_TABLE`, 'sort'],
    ['/api/v1/vouchers/snapshots/bad%20id?company=a', 'snapshotId'],
  ])('returns structured validation errors for %s', async (url, field) => {
    const response = await request(app).get(url);
    expect(response.status).toBe(400);
    expect(response.body).toMatchObject({
      code: 'VALIDATION_ERROR',
      details: {
        errors: expect.arrayContaining([expect.objectContaining({ field })]),
      },
    });
  });

  it('returns empty lists and 404 lookups without leaking another company', async () => {
    await promote('company-b', 'snapshot-b', [vouchers[0]!]);
    const list = await request(app).get(listPath);
    const detail = await request(app).get(
      `/api/v1/vouchers/${encodeURIComponent(vouchers[0]!.voucherId)}?company=company-a`,
    );

    expect(list.status).toBe(200);
    expect(list.body.data.items).toEqual([]);
    expect(list.body.data.pagination.totalItems).toBe(0);
    expect(detail.status).toBe(404);
  });
});
