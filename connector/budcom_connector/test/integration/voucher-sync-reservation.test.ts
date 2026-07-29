import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { SqliteVoucherRepository } from '../../src/storage/sqlite/sqlite-voucher-repository.js';

const tempDirs: string[] = [];
const databases: SqliteDatabase[] = [];

afterEach(() => {
  for (const database of databases.splice(0)) database.close();
  for (const dir of tempDirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

function repositories(): [SqliteVoucherRepository, SqliteVoucherRepository] {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-voucher-reservation-'));
  tempDirs.push(dir);
  const databasePath = path.join(dir, 'voucher.db');
  const first = new SqliteDatabase({ databasePath });
  const second = new SqliteDatabase({ databasePath });
  first.open();
  second.open();
  databases.push(first, second);
  return [new SqliteVoucherRepository(first), new SqliteVoucherRepository(second)];
}

describe('Voucher synchronization reservation across SQLite connections', () => {
  it('blocks the same company and permits different companies', async () => {
    const [first, second] = repositories();
    const acquiredAt = '2026-07-27T00:00:00.000Z';
    const expiresAt = '2026-07-27T00:15:00.000Z';

    await expect(
      first.acquireSyncReservation('company-a', 'owner-a', acquiredAt, expiresAt),
    ).resolves.toBe(true);
    await expect(
      second.acquireSyncReservation('company-a', 'owner-b', acquiredAt, expiresAt),
    ).resolves.toBe(false);
    await expect(
      second.acquireSyncReservation('company-b', 'owner-b', acquiredAt, expiresAt),
    ).resolves.toBe(true);
  });

  it('recovers an abandoned expired reservation transactionally', async () => {
    const [first, second] = repositories();
    await first.acquireSyncReservation(
      'company-a',
      'abandoned-owner',
      '2026-07-27T00:00:00.000Z',
      '2026-07-27T00:01:00.000Z',
    );

    await expect(
      second.acquireSyncReservation(
        'company-a',
        'recovery-owner',
        '2026-07-27T00:02:00.000Z',
        '2026-07-27T00:17:00.000Z',
      ),
    ).resolves.toBe(true);

    await first.releaseSyncReservation('company-a', 'abandoned-owner');
    await expect(
      first.acquireSyncReservation(
        'company-a',
        'third-owner',
        '2026-07-27T00:03:00.000Z',
        '2026-07-27T00:18:00.000Z',
      ),
    ).resolves.toBe(false);
  });

  it('releases only when company and owner both match', async () => {
    const [first, second] = repositories();
    const acquiredAt = '2026-07-27T00:00:00.000Z';
    const expiresAt = '2026-07-27T00:15:00.000Z';
    await first.acquireSyncReservation('company-a', 'owner-a', acquiredAt, expiresAt);

    await second.releaseSyncReservation('company-a', 'wrong-owner');
    await expect(
      second.acquireSyncReservation('company-a', 'owner-b', acquiredAt, expiresAt),
    ).resolves.toBe(false);

    await first.releaseSyncReservation('company-a', 'owner-a');
    await expect(
      second.acquireSyncReservation('company-a', 'owner-b', acquiredAt, expiresAt),
    ).resolves.toBe(true);
  });
});
