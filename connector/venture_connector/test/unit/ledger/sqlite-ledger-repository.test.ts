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

  describe('updateContactDetailsMany (bulk contact-details sync)', () => {
    it('writes only mailing/contact/gst, leaving name/balance/status untouched', async () => {
      const { storage } = await createTestSqliteStorage();
      const repository = new SqliteLedgerRepository(storage.getBundle().database);
      await repository.upsertMany('company-1', [sampleLedger('acme', 'Acme Corp')]);

      const { updated, skipped } = await repository.updateContactDetailsMany('company-1', [
        { ledgerId: 'acme', contact: { email: 'accounts@acme.example' }, gst: { gstin: '29AABCU9603R1ZM' } },
      ]);

      expect(updated).toBe(1);
      expect(skipped).toBe(0);
      const stored = await repository.findById('company-1', 'acme');
      expect(stored?.contact).toEqual({ email: 'accounts@acme.example' });
      expect(stored?.gst).toEqual({ gstin: '29AABCU9603R1ZM' });
      expect(stored?.name).toBe('Acme Corp');
      expect(stored?.status).toBe('active');
    });

    it('skips (never inserts) a ledger with no matching row', async () => {
      const { storage } = await createTestSqliteStorage();
      const repository = new SqliteLedgerRepository(storage.getBundle().database);

      const { updated, skipped } = await repository.updateContactDetailsMany('company-1', [
        { ledgerId: 'never-synced', contact: { email: 'x@example.com' } },
      ]);

      expect(updated).toBe(0);
      expect(skipped).toBe(1);
      expect(await repository.findById('company-1', 'never-synced')).toBeNull();
    });
  });

  describe('upsertMany COALESCE regression guard for mailing/contact/gst', () => {
    it('does not wipe contact details populated by the bulk sync when the routine sync (which never carries them) runs again', async () => {
      const { storage } = await createTestSqliteStorage();
      const repository = new SqliteLedgerRepository(storage.getBundle().database);

      // Step 1: routine Ledgers sync -- LedgerDetails shaped exactly as LEDGER_RICH_FETCH_FIELDS
      // produces it: mailing/contact/gst are always undefined (never requested from Tally).
      await repository.upsertMany('company-1', [sampleLedger('acme', 'Acme Corp')]);
      expect((await repository.findById('company-1', 'acme'))?.contact).toBeUndefined();

      // Step 2: the new bulk contact-details sync populates them.
      await repository.updateContactDetailsMany('company-1', [
        { ledgerId: 'acme', contact: { email: 'accounts@acme.example' }, gst: { gstin: '29AABCU9603R1ZM' } },
      ]);
      expect((await repository.findById('company-1', 'acme'))?.contact).toEqual({
        email: 'accounts@acme.example',
      });

      // Step 3: a later routine sync re-syncs the SAME ledger (name/balance may have changed,
      // mailing/contact/gst are still undefined in its own extraction, as always). Before the
      // COALESCE fix this unconditionally overwrote mailing_json/contact_json/gst_json with NULL.
      await repository.upsertMany('company-1', [
        sampleLedger('acme', 'Acme Corp (Renamed)'),
      ]);

      const afterRoutineSync = await repository.findById('company-1', 'acme');
      expect(afterRoutineSync?.name).toBe('Acme Corp (Renamed)');
      expect(afterRoutineSync?.contact).toEqual({ email: 'accounts@acme.example' });
      expect(afterRoutineSync?.gst).toEqual({ gstin: '29AABCU9603R1ZM' });
    });
  });
});
