import { afterEach, describe, expect, it } from 'vitest';

import type { LedgerDetails } from '../../../src/erp/ledger/ledger-domain.js';
import { SqliteLedgerRepository } from '../../../src/storage/sqlite/sqlite-ledger-repository.js';
import { sampleLedgerDetails } from '../../helpers/ledger-fixtures.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

function sampleLedger(id: string, name: string): LedgerDetails {
  return sampleLedgerDetails({ id, name, normalizedName: name.toLowerCase(), identitySource: 'name' });
}

describe('SqliteLedgerRepository', () => {
  it('supports upsert, search, statistics, and soft delete', async () => {
    const { storage } = await createTestSqliteStorage();
    const repository = new SqliteLedgerRepository(storage.getBundle().database);

    await repository.upsertMany('company-1', [sampleLedger('cash', 'Cash'), sampleLedger('bank', 'Bank')]);
    expect(await repository.findByName('company-1', 'Cash')).toMatchObject({ id: 'cash' });

    const search = await repository.search('company-1', { page: 1, pageSize: 10, query: 'bank' });
    expect(search.items).toHaveLength(1);

    const stats = await repository.getStatistics('company-1');
    expect(stats.totalLedgers).toBe(2);

    expect(await repository.softDelete('company-1', 'cash')).toBe(true);
    const afterDelete = await repository.search('company-1', { page: 1, pageSize: 10 });
    expect(afterDelete.items).toHaveLength(1);
  });
});
