import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { JsonLedgerRepository } from '../../../src/services/ledger/json-ledger-repository.js';
import { sampleLedgerDetails } from '../../helpers/ledger-fixtures.js';

const tempDirs: string[] = [];

afterEach(() => {
  for (const dir of tempDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function createRepository(): JsonLedgerRepository {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-ledgers-'));
  tempDirs.push(basePath);
  return new JsonLedgerRepository({ basePath });
}

function sampleLedger(id: string, name: string) {
  return sampleLedgerDetails({ id, name, normalizedName: name.toLowerCase(), identitySource: 'name' });
}

describe('JsonLedgerRepository', () => {
  it('supports insert, lookup, search, and statistics', async () => {
    const repository = createRepository();
    await repository.upsertMany('company-1', [
      sampleLedger('cash', 'Cash'),
      sampleLedger('bank', 'Bank Account'),
    ]);

    expect(await repository.findByName('company-1', 'Cash')).toMatchObject({ id: 'cash' });
    expect(await repository.findById('company-1', 'bank')).toMatchObject({ name: 'Bank Account' });

    const search = await repository.search('company-1', { page: 1, pageSize: 10, query: 'bank' });
    expect(search.items).toHaveLength(1);
    expect(search.pagination.totalItems).toBe(1);

    const stats = await repository.getStatistics('company-1');
    expect(stats.totalLedgers).toBe(2);
  });

  it('supports soft delete and hard delete', async () => {
    const repository = createRepository();
    await repository.insert('company-1', sampleLedger('cash', 'Cash'));
    expect(await repository.softDelete('company-1', 'cash')).toBe(true);

    const search = await repository.search('company-1', { page: 1, pageSize: 10 });
    expect(search.items).toHaveLength(0);

    expect(await repository.delete('company-1', 'cash')).toBe(true);
    expect(await repository.findById('company-1', 'cash')).toBeNull();
  });
});
