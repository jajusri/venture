import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';

import { createMasterDataMockFetch } from '../helpers/mock-fetch.js';
import { sampleLedgerDetails } from '../helpers/ledger-fixtures.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';
import type { VoucherDetails } from '../../src/erp/voucher/voucher-domain.js';
import type { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';

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

function voucherFixture(overrides: {
  readonly voucherId: string;
  readonly date: string;
  readonly voucherType: string;
  readonly voucherNumber?: string;
  readonly status?: 'active' | 'cancelled';
  readonly ledgerEntries: readonly {
    readonly lineNumber: number;
    readonly ledgerName: string;
    readonly amount: string;
    readonly side: 'debit' | 'credit';
  }[];
}): VoucherDetails {
  return {
    voucherId: overrides.voucherId,
    identityVersion: 'test',
    date: overrides.date,
    voucherType: overrides.voucherType,
    voucherNumber: overrides.voucherNumber ?? null,
    partyName: 'Acme Traders',
    amount: null,
    amountComparable: false,
    status: overrides.status ?? 'active',
    dataQuality: 'complete',
    guid: `guid-${overrides.voucherId}`,
    masterId: null,
    alterId: null,
    voucherKey: null,
    voucherRetainKey: null,
    narration: `Narration for ${overrides.voucherId}`,
    ledgerEntries: overrides.ledgerEntries.map((entry) => ({
      lineNumber: entry.lineNumber,
      ledgerName: entry.ledgerName,
      amount: { amount: entry.amount, side: entry.side },
      isDeemedPositive: entry.side === 'debit',
      allocations: [],
    })),
    inventoryEntries: [],
    allocations: [],
  };
}

async function setupLedgerStatementApi() {
  const databasePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-ledger-statement-'));
  tempDirs.push(databasePath);
  const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
  const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
  await startTestServices(context, { selectCompanyId: 'estimation' });
  const app = createTestApp(context);
  const { ServiceTokens } = await import('../../src/core/tokens.js');
  const ledgerSync = context.container.resolve(
    ServiceTokens.LedgerSync,
  ) as import('../../src/services/ledger/ledger-sync.service.js').LedgerSyncService;
  await ledgerSync.start();
  const storage = context.container.resolve(ServiceTokens.LocalDatabase) as SqliteStorageService;
  return { app, context, ledgerSync, storage };
}

async function seedPromotedSnapshot(
  storage: SqliteStorageService,
  companyId: string,
  snapshotId: string,
  period: { readonly dateFrom: string; readonly dateTo: string },
  vouchers: readonly VoucherDetails[],
): Promise<void> {
  const repo = storage.getBundle().voucherRepository;
  await repo.beginSnapshot(companyId, snapshotId, period);
  await repo.stageMany(companyId, snapshotId, vouchers);
  await repo.completeSnapshot(companyId, snapshotId, `${period.dateTo}T12:00:00.000Z`);
  await repo.promoteSnapshot(companyId, snapshotId, `${period.dateTo}T12:00:00.000Z`);
}

const LEDGER_ID = 'ledger:acme-traders';

describe('GET /ledgers/:id/statement', () => {
  it('returns 404 for an unknown ledger', async () => {
    const { app } = await setupLedgerStatementApi();
    const response = await request(app).get(`/ledgers/${LEDGER_ID}/statement`);
    expect(response.status).toBe(404);
  });

  it('rejects an invalid date range', async () => {
    const { app, storage } = await setupLedgerStatementApi();
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({ id: LEDGER_ID, name: 'Acme Traders' }),
    ]);
    const inverted = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2026-08-01', to: '2026-07-01' });
    expect(inverted.status).toBe(400);

    const tooWide = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2024-01-01', to: '2026-08-01' });
    expect(tooWide.status).toBe(400);
  });

  it('computes opening, running, and closing balance from debit/credit movements, in date order', async () => {
    const { app, storage } = await setupLedgerStatementApi();
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({
        id: LEDGER_ID,
        name: 'Acme Traders',
        closingBalance: { amount: '5000', currencyCode: 'INR', side: 'Dr' },
        syncedAt: '2026-08-10T09:00:00.000Z',
      }),
    ]);
    await seedPromotedSnapshot(
      storage,
      'estimation',
      'snap-1',
      { dateFrom: '2026-07-01', dateTo: '2026-08-10' },
      [
        voucherFixture({
          voucherId: 'v-sales-1',
          date: '2026-07-05',
          voucherType: 'Sales',
          voucherNumber: 'S-001',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '2000', side: 'debit' },
            { lineNumber: 2, ledgerName: 'Sales Account', amount: '2000', side: 'credit' },
          ],
        }),
        voucherFixture({
          voucherId: 'v-receipt-1',
          date: '2026-07-15',
          voucherType: 'Receipt',
          voucherNumber: 'R-001',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '1500', side: 'credit' },
            { lineNumber: 2, ledgerName: 'Cash', amount: '1500', side: 'debit' },
          ],
        }),
        // Outside the requested (from/to) window below, but still inside sync coverage —
        // must count toward the opening-balance anchor, must NOT appear as a visible row.
        voucherFixture({
          voucherId: 'v-sales-2',
          date: '2026-08-01',
          voucherType: 'Sales',
          voucherNumber: 'S-002',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '4500', side: 'debit' },
            { lineNumber: 2, ledgerName: 'Sales Account', amount: '4500', side: 'credit' },
          ],
        }),
        // Cancelled — must be excluded from both the transaction list and the balance math.
        voucherFixture({
          voucherId: 'v-cancelled-1',
          date: '2026-07-10',
          voucherType: 'Sales',
          voucherNumber: 'S-999',
          status: 'cancelled',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '9999', side: 'debit' },
            { lineNumber: 2, ledgerName: 'Sales Account', amount: '9999', side: 'credit' },
          ],
        }),
        // A different ledger entirely — must not leak into Acme Traders' statement.
        voucherFixture({
          voucherId: 'v-other-ledger',
          date: '2026-07-12',
          voucherType: 'Payment',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Other Ledger', amount: '777', side: 'debit' },
            { lineNumber: 2, ledgerName: 'Bank', amount: '777', side: 'credit' },
          ],
        }),
      ],
    );

    const response = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2026-07-01', to: '2026-07-31' });

    expect(response.status).toBe(200);
    const { statement } = response.body;
    expect(statement.coverage.transactionsComplete).toBe(true);
    expect(statement.coverage.balanceAvailable).toBe(true);
    expect(statement.openingBalance).toEqual({ amount: '0', side: 'debit' });
    expect(statement.closingBalance).toEqual({ amount: '500', side: 'debit' });

    expect(statement.transactions).toHaveLength(2);
    expect(statement.transactions.map((t: { voucherId: string }) => t.voucherId)).toEqual([
      'v-sales-1',
      'v-receipt-1',
    ]);

    const [sales, receipt] = statement.transactions;
    expect(sales.voucherType).toBe('Sales');
    expect(sales.voucherNumber).toBe('S-001');
    expect(sales.debit).toBe('2000');
    expect(sales.credit).toBeNull();
    expect(sales.runningBalance).toEqual({ amount: '2000', side: 'debit' });

    expect(receipt.voucherType).toBe('Receipt');
    expect(receipt.debit).toBeNull();
    expect(receipt.credit).toBe('1500');
    expect(receipt.runningBalance).toEqual({ amount: '500', side: 'debit' });
  });

  // Same-day tiebreak must reflect the order vouchers were staged in (Tally's own response
  // order, preserved via SQLite rowid) — never an arbitrary sort by the GUID-derived voucherId.
  // voucherIds are deliberately chosen so a lexicographic sort would reverse the expected order.
  it('orders same-day transactions by staging order, not by voucherId', async () => {
    const { app, storage } = await setupLedgerStatementApi();
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({
        id: LEDGER_ID,
        name: 'Acme Traders',
        closingBalance: { amount: '3000', currencyCode: 'INR', side: 'Dr' },
        syncedAt: '2026-08-10T09:00:00.000Z',
      }),
    ]);
    await seedPromotedSnapshot(
      storage,
      'estimation',
      'snap-1',
      { dateFrom: '2026-07-01', dateTo: '2026-08-10' },
      [
        voucherFixture({
          voucherId: 'v-zzz-staged-first',
          date: '2026-07-05',
          voucherType: 'Sales',
          voucherNumber: 'S-001',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '1000', side: 'debit' },
            { lineNumber: 2, ledgerName: 'Sales Account', amount: '1000', side: 'credit' },
          ],
        }),
        voucherFixture({
          voucherId: 'v-aaa-staged-second',
          date: '2026-07-05',
          voucherType: 'Sales',
          voucherNumber: 'S-002',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '2000', side: 'debit' },
            { lineNumber: 2, ledgerName: 'Sales Account', amount: '2000', side: 'credit' },
          ],
        }),
      ],
    );

    const response = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2026-07-01', to: '2026-07-31' });

    expect(response.status).toBe(200);
    expect(response.body.statement.transactions.map((t: { voucherId: string }) => t.voucherId)).toEqual([
      'v-zzz-staged-first',
      'v-aaa-staged-second',
    ]);
  });

  it('never identifies a transaction`s voucher by display number — voucherId is the stable identity', async () => {
    const { app, storage } = await setupLedgerStatementApi();
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({
        id: LEDGER_ID,
        name: 'Acme Traders',
        closingBalance: { amount: '100', currencyCode: 'INR', side: 'Dr' },
        syncedAt: '2026-07-10T00:00:00.000Z',
      }),
    ]);
    await seedPromotedSnapshot(
      storage,
      'estimation',
      'snap-2',
      { dateFrom: '2026-07-01', dateTo: '2026-07-10' },
      [
        voucherFixture({
          voucherId: 'stable-guid-42',
          date: '2026-07-02',
          voucherType: 'Sales',
          voucherNumber: '1', // deliberately a colliding/common display number
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '100', side: 'debit' },
          ],
        }),
      ],
    );
    const response = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2026-07-01', to: '2026-07-10' });
    expect(response.body.statement.transactions[0].voucherId).toBe('stable-guid-42');
    expect(response.body.statement.transactions[0].voucherNumber).toBe('1');
  });

  it('reports incomplete coverage honestly instead of fabricating a balance', async () => {
    const { app, storage } = await setupLedgerStatementApi();
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({
        id: LEDGER_ID,
        name: 'Acme Traders',
        closingBalance: { amount: '5000', currencyCode: 'INR', side: 'Dr' },
        syncedAt: '2026-08-10T09:00:00.000Z',
      }),
    ]);
    // No voucher sync has happened at all.
    const response = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2026-07-01', to: '2026-07-31' });

    expect(response.status).toBe(200);
    const { statement } = response.body;
    expect(statement.openingBalance).toBeNull();
    expect(statement.closingBalance).toBeNull();
    expect(statement.transactions).toEqual([]);
    expect(statement.coverage.transactionsComplete).toBe(false);
    expect(statement.coverage.balanceAvailable).toBe(false);
    expect(statement.coverage.message).toBeTruthy();
  });

  it('shows transactions without fabricating balances when only partial history is synced', async () => {
    const { app, storage } = await setupLedgerStatementApi();
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({
        id: LEDGER_ID,
        name: 'Acme Traders',
        closingBalance: { amount: '5000', currencyCode: 'INR', side: 'Dr' },
        syncedAt: '2026-08-10T09:00:00.000Z',
      }),
    ]);
    // Snapshot covers the requested period but NOT through the ledger's own last-synced date —
    // insufficient to anchor an authoritative opening/closing balance.
    await seedPromotedSnapshot(
      storage,
      'estimation',
      'snap-3',
      { dateFrom: '2026-07-01', dateTo: '2026-07-31' },
      [
        voucherFixture({
          voucherId: 'v-sales-partial',
          date: '2026-07-05',
          voucherType: 'Sales',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '2000', side: 'debit' },
          ],
        }),
      ],
    );
    const response = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2026-07-01', to: '2026-07-31' });

    expect(response.status).toBe(200);
    const { statement } = response.body;
    expect(statement.coverage.transactionsComplete).toBe(true);
    expect(statement.coverage.balanceAvailable).toBe(false);
    expect(statement.transactions).toHaveLength(1);
    expect(statement.transactions[0].debit).toBe('2000');
    expect(statement.openingBalance).toBeNull();
    expect(statement.closingBalance).toBeNull();
    expect(statement.transactions[0].runningBalance).toBeNull();
  });

  it('returns an empty, reconciled statement for a ledger with no movement in the period', async () => {
    const { app, storage } = await setupLedgerStatementApi();
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({
        id: LEDGER_ID,
        name: 'Acme Traders',
        closingBalance: { amount: '0', currencyCode: 'INR', side: 'Dr' },
        syncedAt: '2026-07-10T00:00:00.000Z',
      }),
    ]);
    await seedPromotedSnapshot(
      storage,
      'estimation',
      'snap-4',
      { dateFrom: '2026-07-01', dateTo: '2026-07-10' },
      [],
    );
    const response = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2026-07-01', to: '2026-07-10' });

    expect(response.status).toBe(200);
    const { statement } = response.body;
    expect(statement.transactions).toEqual([]);
    expect(statement.openingBalance).toEqual({ amount: '0', side: 'debit' });
    expect(statement.closingBalance).toEqual({ amount: '0', side: 'debit' });
  });

  it('does not leak another company`s ledger movements into this company`s statement', async () => {
    const { app, storage } = await setupLedgerStatementApi();
    await storage.getBundle().ledgerRepository.upsertMany('estimation', [
      sampleLedgerDetails({
        id: LEDGER_ID,
        name: 'Acme Traders',
        closingBalance: { amount: '0', currencyCode: 'INR', side: 'Dr' },
        syncedAt: '2026-07-10T00:00:00.000Z',
      }),
    ]);
    await seedPromotedSnapshot(
      storage,
      'estimation',
      'snap-5',
      { dateFrom: '2026-07-01', dateTo: '2026-07-10' },
      [],
    );
    // Same ledger *name*, same date window, but a different company — must never surface here.
    await seedPromotedSnapshot(
      storage,
      'other-co',
      'snap-5-other',
      { dateFrom: '2026-07-01', dateTo: '2026-07-10' },
      [
        voucherFixture({
          voucherId: 'other-co-voucher',
          date: '2026-07-05',
          voucherType: 'Sales',
          ledgerEntries: [
            { lineNumber: 1, ledgerName: 'Acme Traders', amount: '99999', side: 'debit' },
          ],
        }),
      ],
    );

    const response = await request(app)
      .get(`/ledgers/${LEDGER_ID}/statement`)
      .query({ from: '2026-07-01', to: '2026-07-10' });

    expect(response.status).toBe(200);
    expect(response.body.statement.transactions).toEqual([]);
    expect(response.body.statement.closingBalance).toEqual({ amount: '0', side: 'debit' });
  });
});
