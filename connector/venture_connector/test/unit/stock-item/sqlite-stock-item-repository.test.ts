import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { SqliteDatabase } from '../../../src/storage/sqlite/sqlite-database.js';
import { SqliteStockItemRepository } from '../../../src/storage/sqlite/sqlite-stock-item-repository.js';
import { sampleStockItemDetails } from '../../helpers/stock-item-fixtures.js';

const tempDirs: string[] = [];
const openDbs: SqliteDatabase[] = [];

afterEach(async () => {
  for (const db of openDbs.splice(0)) {
    db.close();
  }
  await new Promise((resolve) => setTimeout(resolve, 50));
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    } catch {
      // Windows may retain WAL handles briefly after close.
    }
  }
});

function createRepo(): { repo: SqliteStockItemRepository; db: SqliteDatabase } {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-stock-repo-'));
  tempDirs.push(basePath);
  const db = new SqliteDatabase({ databasePath: path.join(basePath, 'venture-ledger.db') });
  db.open();
  openDbs.push(db);
  return { repo: new SqliteStockItemRepository(db), db };
}

describe('SqliteStockItemRepository', () => {
  it('isolates identical source identities across companies', async () => {
    const { repo } = createRepo();
    const item = sampleStockItemDetails({ id: 'guid:abc', guid: 'abc', name: 'Widget' });
    await repo.upsertMany('company-a', [item]);
    await repo.upsertMany('company-b', [item]);
    expect(await repo.countByCompany('company-a')).toBe(1);
    expect(await repo.countByCompany('company-b')).toBe(1);
  });

  it('rejects invalid sort fields via allowlist defaulting', async () => {
    const { repo } = createRepo();
    await repo.upsertMany('co', [sampleStockItemDetails({ id: 'name:a', name: 'Alpha' })]);
    const result = await repo.search('co', {
      page: 1,
      pageSize: 10,
      sortBy: 'invalid' as 'name',
    });
    expect(result.items[0]?.name).toBe('Alpha');
  });

  it('filters deleted items from search', async () => {
    const { repo } = createRepo();
    await repo.upsertMany('co', [
      sampleStockItemDetails({ id: 'name:keep', name: 'Keep' }),
      sampleStockItemDetails({ id: 'name:gone', name: 'Gone' }),
    ]);
    await repo.softDelete('co', 'name:gone');
    const result = await repo.search('co', { page: 1, pageSize: 10 });
    expect(result.items).toHaveLength(1);
    expect(result.items[0]?.name).toBe('Keep');
  });
});
