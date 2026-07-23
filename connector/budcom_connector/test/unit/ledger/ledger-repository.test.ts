import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { LedgerRepository } from '../../../src/services/ledger/ledger-repository.js';
import type { LedgerDetails } from '../../../src/erp/ledger/ledger-domain.js';

const tempDirs: string[] = [];

afterEach(() => {
  for (const dir of tempDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function createRepository(): LedgerRepository {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-ledgers-'));
  tempDirs.push(basePath);
  return new LedgerRepository({ basePath });
}

function sampleLedger(id: string, name: string): LedgerDetails {
  return {
    id,
    name,
    normalizedName: name.toLowerCase(),
    status: 'active',
    balanceNature: 'debit',
    isDeleted: false,
    syncedAt: '2026-01-01T00:00:00.000Z',
  };
}

describe('LedgerRepository', () => {
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
