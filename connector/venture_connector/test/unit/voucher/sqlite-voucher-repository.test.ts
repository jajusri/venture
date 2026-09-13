import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, beforeAll, describe, expect, it } from 'vitest';

import type {
  VoucherDetails,
  VoucherStructuredValue,
} from '../../../src/erp/voucher/voucher-domain.js';
import { SqliteDatabase } from '../../../src/storage/sqlite/sqlite-database.js';
import {
  SqliteVoucherRepository,
  VoucherRepositoryError,
} from '../../../src/storage/sqlite/sqlite-voucher-repository.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { VoucherXmlMapper } from '../../../src/tally/voucher/voucher-mapper.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';
import { APPROVED_VOUCHER_FIXTURE_XML } from '../../fixtures/vouchers/approved-voucher-fixture.js';

const tempDirs: string[] = [];
const openDbs: SqliteDatabase[] = [];
let fixtureVouchers: readonly VoucherDetails[];

beforeAll(() => {
  const parser = new VoucherCollectionParser(new TallyXmlResponseParser());
  const parsed = parser.parse(APPROVED_VOUCHER_FIXTURE_XML);
  if (parsed.status !== 'records') throw new Error('Voucher fixture did not parse.');
  const mapper = new VoucherXmlMapper(parser);
  fixtureVouchers = parsed.records.map((record) => {
    const result = mapper.map(record);
    if (!result.value) throw new Error('Voucher fixture did not map.');
    return result.value;
  });
});

afterEach(async () => {
  for (const db of openDbs.splice(0)) db.close();
  await new Promise((resolve) => setTimeout(resolve, 50));
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    } catch {
      // Windows can retain WAL handles briefly.
    }
  }
});

function createRepository(): {
  repo: SqliteVoucherRepository;
  database: SqliteDatabase;
  databasePath: string;
} {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-voucher-repo-'));
  tempDirs.push(dir);
  const databasePath = path.join(dir, 'venture-ledger.db');
  const database = new SqliteDatabase({
    databasePath,
  });
  database.open();
  openDbs.push(database);
  return { repo: new SqliteVoucherRepository(database), database, databasePath };
}

const PERIOD = { dateFrom: '2026-07-27', dateTo: '2026-07-27' };
const COMPLETED_AT = '2026-07-27T12:00:00.000Z';

async function persistAndPromote(
  repo: SqliteVoucherRepository,
  companyId: string,
  snapshotId: string,
  vouchers: readonly VoucherDetails[],
): Promise<void> {
  await repo.beginSnapshot(companyId, snapshotId, PERIOD);
  await repo.stageMany(companyId, snapshotId, vouchers);
  await repo.completeSnapshot(companyId, snapshotId, COMPLETED_AT);
  await repo.promoteSnapshot(companyId, snapshotId, COMPLETED_AT);
}

describe('SqliteVoucherRepository', () => {
  it('registers the complete production Voucher schema migration', () => {
    const { database } = createRepository();
    const tables = database.getDatabase().prepare(`
      SELECT name FROM sqlite_master
      WHERE type = 'table' AND name LIKE 'voucher_%'
      ORDER BY name
    `).all() as Array<{ name: string }>;
    expect(tables.map((row) => row.name)).toEqual(expect.arrayContaining([
      'voucher_snapshots',
      'voucher_headers',
      'voucher_ledger_entries',
      'voucher_inventory_entries',
      'voucher_allocations',
    ]));
  });

  it('moves through Pending, Writing, Validated, Promoted, and Archived states', async () => {
    const { repo } = createRepository();
    await repo.createSnapshot('company-a', 'lifecycle-1', PERIOD);
    expect((await repo.getSnapshot('company-a', 'lifecycle-1'))?.status).toBe('Pending');

    await repo.writeVoucherBatch('company-a', 'lifecycle-1', [fixtureVouchers[0]!]);
    expect((await repo.getSnapshot('company-a', 'lifecycle-1'))?.status).toBe('Writing');

    await repo.finalizeSnapshot('company-a', 'lifecycle-1', COMPLETED_AT);
    expect((await repo.getSnapshot('company-a', 'lifecycle-1'))?.status).toBe('Validated');
    expect(await repo.getVoucher('company-a', fixtureVouchers[0]!.voucherId)).toBeNull();

    await repo.promoteSnapshot('company-a', 'lifecycle-1', COMPLETED_AT);
    expect((await repo.getSnapshot('company-a', 'lifecycle-1'))?.status).toBe('Promoted');

    await persistAndPromote(repo, 'company-a', 'lifecycle-2', [fixtureVouchers[1]!]);
    expect((await repo.getSnapshot('company-a', 'lifecycle-1'))?.status).toBe('Archived');
    expect((await repo.getSnapshot('company-a', 'lifecycle-2'))?.status).toBe('Promoted');
  });

  it('rolls back pending data atomically and deletes only non-visible snapshots', async () => {
    const { repo } = createRepository();
    await repo.createSnapshot('company-a', 'rollback-1', PERIOD);
    await repo.writeVoucherBatch('company-a', 'rollback-1', [fixtureVouchers[0]!]);
    await repo.rollbackSnapshot('company-a', 'rollback-1');
    expect(await repo.getSnapshot('company-a', 'rollback-1')).toBeNull();

    await persistAndPromote(repo, 'company-a', 'delete-1', [fixtureVouchers[0]!]);
    await expect(repo.deleteSnapshot('company-a', 'delete-1')).rejects.toMatchObject({
      code: 'INVALID_SNAPSHOT_TRANSITION',
    });
    await persistAndPromote(repo, 'company-a', 'delete-2', [fixtureVouchers[1]!]);
    await repo.deleteSnapshot('company-a', 'delete-1');
    expect(await repo.getSnapshot('company-a', 'delete-1')).toBeNull();
  });

  it('preserves promoted visibility and repository reads across restart', async () => {
    const { repo, database, databasePath } = createRepository();
    await persistAndPromote(repo, 'company-a', 'restart-1', fixtureVouchers);
    database.close();
    const index = openDbs.indexOf(database);
    if (index >= 0) openDbs.splice(index, 1);

    const reopened = new SqliteDatabase({ databasePath });
    reopened.open();
    openDbs.push(reopened);
    const restarted = new SqliteVoucherRepository(reopened);

    expect(await restarted.getVoucher('company-a', fixtureVouchers[0]!.voucherId))
      .toEqual(fixtureVouchers[0]);
    expect(await restarted.listSnapshots('company-a')).toHaveLength(1);
    const result = await restarted.searchVouchers('company-a', {
      ...PERIOD,
      page: 1,
      pageSize: 25,
      sortBy: 'date',
      sortDirection: 'asc',
    });
    expect(result.items).toHaveLength(fixtureVouchers.length);
    expect(await restarted.listSnapshots('company-b')).toEqual([]);
  });

  it('creates foreign keys, indexes, and database-level immutability guards', async () => {
    const { repo, database } = createRepository();
    await persistAndPromote(repo, 'company-a', 'schema-1', [fixtureVouchers[0]!]);
    const db = database.getDatabase();

    expect(db.prepare('PRAGMA foreign_key_check').all()).toEqual([]);
    const foreignKeys = db.prepare('PRAGMA foreign_key_list(voucher_ledger_entries)').all();
    expect(foreignKeys.length).toBeGreaterThan(0);
    const indexes = db.prepare(`
      SELECT name FROM sqlite_master
      WHERE type = 'index' AND name LIKE 'idx_voucher%'
    `).all() as Array<{ name: string }>;
    expect(indexes.map((row) => row.name)).toEqual(expect.arrayContaining([
      'idx_voucher_snapshots_company_status',
      'idx_voucher_headers_snapshot_date',
      'idx_voucher_headers_snapshot_guid',
    ]));
    expect(() => db.prepare(`
      DELETE FROM voucher_headers WHERE company_id = 'company-a' AND snapshot_id = 'schema-1'
    `).run()).toThrow(/immutable voucher snapshot data/);
  });

  it('promotes an empty immutable snapshot', async () => {
    const { repo, database } = createRepository();
    await persistAndPromote(repo, 'company-a', 'empty-1', []);

    expect(await repo.querySnapshot('company-a')).toEqual([]);
    const row = database.getDatabase().prepare(`
      SELECT status, voucher_count FROM voucher_snapshots
      WHERE company_id = 'company-a' AND snapshot_id = 'empty-1'
    `).get() as { status: string; voucher_count: number };
    expect(row).toEqual({ status: 'PROMOTED', voucher_count: 0 });
  });

  it('stores and reconstructs one Voucher with its identity and entries', async () => {
    const { repo, database } = createRepository();
    const voucher = fixtureVouchers[1]!;
    await persistAndPromote(repo, 'company-a', 'single-1', [voucher]);

    expect(await repo.findById('company-a', voucher.voucherId)).toEqual(voucher);
    expect((database.getDatabase().prepare(
      'SELECT COUNT(*) AS count FROM voucher_ledger_entries',
    ).get() as { count: number }).count).toBe(voucher.ledgerEntries.length);
    expect((database.getDatabase().prepare(
      'SELECT COUNT(*) AS count FROM voucher_inventory_entries',
    ).get() as { count: number }).count).toBe(voucher.inventoryEntries.length);
    const inventory = voucher.inventoryEntries[0];
    if (inventory) {
      expect(database.getDatabase().prepare(`
        SELECT item_name, quantity, rate, amount
        FROM voucher_inventory_entries
        WHERE company_id = ? AND snapshot_id = ? AND voucher_id = ? AND line_number = ?
      `).get('company-a', 'single-1', voucher.voucherId, inventory.lineNumber)).toEqual({
        item_name: inventory.itemName,
        quantity: inventory.quantity ?? null,
        rate: inventory.rate ?? null,
        amount: inventory.amount?.amount ?? null,
      });
    }
  });

  it('queries multiple Voucher types from only the active snapshot', async () => {
    const { repo } = createRepository();
    await persistAndPromote(repo, 'company-a', 'multi-1', fixtureVouchers);

    const result = await repo.search('company-a', {
      ...PERIOD,
      voucherType: 'Sales',
      page: 1,
      pageSize: 25,
      sortBy: 'date',
      sortDirection: 'asc',
    });
    expect(result.items).toHaveLength(1);
    expect(result.items[0]?.voucherType).toBe('Sales');
    const statistics = await repo.getStatistics('company-a', PERIOD);
    expect(statistics.totalVouchers).toBe(8);
    expect(statistics.countsByType).toHaveLength(8);
  });

  // TD-023: these two exact-match filters were added to search() specifically so the /api/v1/vouchers
  // list endpoint could move off the unbounded querySnapshot()-then-filter-in-memory path onto this
  // already-paginated SQL query without losing any filtering behavior it previously had.
  it('filters by an exact voucherNumber, distinct from the fuzzy query field', async () => {
    const { repo } = createRepository();
    await persistAndPromote(repo, 'company-a', 'exact-1', fixtureVouchers);
    const target = fixtureVouchers.find((voucher) => voucher.voucherNumber)!;

    const result = await repo.search('company-a', {
      ...PERIOD,
      voucherNumber: target.voucherNumber!,
      page: 1,
      pageSize: 25,
      sortBy: 'date',
      sortDirection: 'asc',
    });
    expect(result.items.map((voucher) => voucher.voucherId)).toEqual([target.voucherId]);
  });

  it('filters by an exact partyName, distinct from the fuzzy query field', async () => {
    const { repo } = createRepository();
    await persistAndPromote(repo, 'company-a', 'exact-2', fixtureVouchers);
    const target = fixtureVouchers.find((voucher) => voucher.partyName)!;

    const result = await repo.search('company-a', {
      ...PERIOD,
      partyName: target.partyName!,
      page: 1,
      pageSize: 25,
      sortBy: 'date',
      sortDirection: 'asc',
    });
    expect(result.items.every((voucher) => voucher.partyName === target.partyName)).toBe(true);
    expect(result.items.map((voucher) => voucher.voucherId)).toContain(target.voucherId);
  });

  it('matches the free-text query field against voucherType as well as number/reference/party', async () => {
    const { repo } = createRepository();
    await persistAndPromote(repo, 'company-a', 'exact-3', fixtureVouchers);

    const result = await repo.search('company-a', {
      ...PERIOD,
      query: 'sales',
      page: 1,
      pageSize: 25,
      sortBy: 'date',
      sortDirection: 'asc',
    });
    expect(result.items.length).toBeGreaterThan(0);
    expect(result.items.every((voucher) => voucher.voucherType.toLowerCase().includes('sales'))).toBe(true);
  });

  it('stores every nested allocation as a validated allocation record', async () => {
    const { repo, database } = createRepository();
    const sales = fixtureVouchers[1]!;
    await persistAndPromote(repo, 'company-a', 'nested-1', [sales]);

    const rows = database.getDatabase().prepare(`
      SELECT owner_type, allocation_type, values_json
      FROM voucher_allocations ORDER BY owner_type, allocation_index
    `).all() as Array<{
      owner_type: string;
      allocation_type: string;
      values_json: string;
    }>;
    expect(rows).toHaveLength(
      sales.allocations.length +
      sales.ledgerEntries.reduce((sum, entry) => sum + entry.allocations.length, 0) +
      sales.inventoryEntries.reduce((sum, entry) => sum + entry.allocations.length, 0),
    );
    expect(new Set(rows.map((row) => row.allocation_type))).toEqual(new Set([
      'accounting',
      'batch',
      'bank',
      'bill',
      'cost-track',
      'inventory',
    ]));
    expect(rows.every((row) => Array.isArray(JSON.parse(row.values_json)))).toBe(true);
  });

  it('atomically promotes a replacement while preserving the prior snapshot', async () => {
    const { repo, database } = createRepository();
    await persistAndPromote(repo, 'company-a', 'active-1', [fixtureVouchers[0]!]);
    await repo.beginSnapshot('company-a', 'active-2', PERIOD);
    await repo.stageMany('company-a', 'active-2', [fixtureVouchers[1]!]);
    await repo.completeSnapshot('company-a', 'active-2', COMPLETED_AT);
    await repo.promoteSnapshot('company-a', 'active-2', '2026-07-27T13:00:00.000Z');

    expect((await repo.querySnapshot('company-a'))[0]?.voucherType).toBe('Sales');
    expect((await repo.querySnapshot('company-a', 'active-1'))[0]?.voucherType).toBe('Purchase');
    const activeRows = database.getDatabase().prepare(`
      SELECT COUNT(*) AS count FROM voucher_snapshots
      WHERE status IN ('PROMOTED', 'ARCHIVED')
    `).get() as { count: number };
    expect(activeRows.count).toBe(2);
  });

  it('leaves the prior active snapshot intact after staging rollback', async () => {
    const { repo, database } = createRepository();
    await persistAndPromote(repo, 'company-a', 'stable-1', [fixtureVouchers[0]!]);
    await repo.beginSnapshot('company-a', 'broken-2', PERIOD);
    const duplicate = {
      ...fixtureVouchers[1]!,
      voucherId: 'different-id',
      guid: fixtureVouchers[0]!.guid,
    };

    await expect(
      repo.stageMany('company-a', 'broken-2', [fixtureVouchers[0]!, duplicate]),
    ).rejects.toMatchObject({ code: 'DUPLICATE_IDENTITY' });
    expect((database.getDatabase().prepare(`
      SELECT COUNT(*) AS count FROM voucher_headers
      WHERE company_id = 'company-a' AND snapshot_id = 'broken-2'
    `).get() as { count: number }).count).toBe(0);
    expect((await repo.querySnapshot('company-a'))[0]?.voucherType).toBe('Purchase');
  });

  it('records failed snapshots and permits recovery through a new snapshot', async () => {
    const { repo, database } = createRepository();
    await persistAndPromote(repo, 'company-a', 'stable-1', [fixtureVouchers[0]!]);
    await repo.beginSnapshot('company-a', 'failed-2', PERIOD);
    await repo.discardSnapshot('company-a', 'failed-2', 'validation failed');

    await expect(
      repo.stageMany('company-a', 'failed-2', [fixtureVouchers[1]!]),
    ).rejects.toMatchObject({ code: 'INVALID_SNAPSHOT_TRANSITION' });
    expect((await repo.querySnapshot('company-a'))[0]?.voucherType).toBe('Purchase');
    await persistAndPromote(repo, 'company-a', 'recovered-3', [fixtureVouchers[1]!]);
    expect((await repo.querySnapshot('company-a'))[0]?.voucherType).toBe('Sales');
    const failed = database.getDatabase().prepare(`
      SELECT status, failure_reason FROM voucher_snapshots WHERE snapshot_id = 'failed-2'
    `).get();
    expect(failed).toEqual({ status: 'FAILED', failure_reason: 'validation failed' });
  });

  it('enforces company isolation and rejects cross-company snapshot writes', async () => {
    const { repo } = createRepository();
    await repo.beginSnapshot('company-a', 'snapshot-a', PERIOD);
    await expect(
      repo.stageMany('company-b', 'snapshot-a', [fixtureVouchers[0]!]),
    ).rejects.toMatchObject({ code: 'CROSS_COMPANY_WRITE' });

    await persistAndPromote(repo, 'company-b', 'snapshot-b', [fixtureVouchers[1]!]);
    await repo.stageMany('company-a', 'snapshot-a', [fixtureVouchers[0]!]);
    await repo.promoteCompleteSnapshot('company-a', 'snapshot-a', COMPLETED_AT);
    expect((await repo.querySnapshot('company-a'))[0]?.voucherType).toBe('Purchase');
    expect((await repo.querySnapshot('company-b'))[0]?.voucherType).toBe('Sales');
  });

  it('rejects duplicate identities and rolls the complete transaction back', async () => {
    const { repo } = createRepository();
    await repo.beginSnapshot('company-a', 'duplicate-1', PERIOD);
    const duplicate = {
      ...fixtureVouchers[1]!,
      voucherId: 'different-id',
      guid: fixtureVouchers[0]!.guid,
    };
    await expect(
      repo.stageMany('company-a', 'duplicate-1', [fixtureVouchers[0]!, duplicate]),
    ).rejects.toBeInstanceOf(VoucherRepositoryError);
    await expect(
      repo.querySnapshot('company-a', 'duplicate-1'),
    ).rejects.toMatchObject({ code: 'INVALID_SNAPSHOT_TRANSITION' });
  });

  it('rejects orphan child rows, broken allocation trees, and invalid transitions', async () => {
    const { repo } = createRepository();
    await repo.beginSnapshot('company-a', 'validation-1', PERIOD);
    await expect(
      repo.storeLedgerEntries('company-a', 'validation-1', 'missing', []),
    ).rejects.toMatchObject({ code: 'ORPHAN_LEDGER_ENTRY' });
    await expect(
      repo.storeInventoryEntries('company-a', 'validation-1', 'missing', []),
    ).rejects.toMatchObject({ code: 'ORPHAN_INVENTORY_ENTRY' });
    await expect(
      repo.promoteSnapshot('company-a', 'validation-1', COMPLETED_AT),
    ).rejects.toMatchObject({ code: 'INVALID_SNAPSHOT_TRANSITION' });

    const cyclic = { name: 'ROOT', attributes: {}, children: [] } as unknown as {
      name: string;
      attributes: Record<string, string>;
      children: VoucherStructuredValue[];
    };
    cyclic.children.push(cyclic);
    const broken = {
      ...fixtureVouchers[0]!,
      allocations: [{
        type: 'bill' as const,
        sourceName: 'BILLALLOCATIONS.LIST',
        values: [cyclic],
      }],
    };
    await expect(
      repo.stageMany('company-a', 'validation-1', [broken]),
    ).rejects.toMatchObject({ code: 'BROKEN_ALLOCATION_TREE' });
    await expect(
      repo.querySnapshot('company-a', 'validation-1'),
    ).rejects.toMatchObject({ code: 'INVALID_SNAPSHOT_TRANSITION' });
  });

  it('rolls back a snapshot promotion transaction when completion validation fails', async () => {
    const { repo } = createRepository();
    await persistAndPromote(repo, 'company-a', 'stable-1', [fixtureVouchers[0]!]);
    await repo.beginSnapshot('company-a', 'partial-2', PERIOD);
    await repo.storeVoucher('company-a', 'partial-2', fixtureVouchers[1]!);

    await expect(
      repo.promoteCompleteSnapshot('company-a', 'partial-2', COMPLETED_AT),
    ).rejects.toMatchObject({ code: 'SNAPSHOT_INCOMPLETE' });
    expect((await repo.querySnapshot('company-a'))[0]?.voucherType).toBe('Purchase');
  });
});
