import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';

import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import { SqliteDatabase } from '../../src/storage/sqlite/sqlite-database.js';
import { SyncRunRepository } from '../../src/storage/sqlite/sync-run-repository.js';
import { SyncRunRetentionService } from '../../src/storage/sqlite/sync-run-retention-service.js';
import { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import {
  countRuns,
  isoDaysAgo,
  RETENTION_TEST_COMPANY,
  retentionRunInput,
  seedTerminalRun,
} from '../helpers/sync-run-retention-test-helpers.js';
import { sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

const NOW_MS = Date.parse('2026-07-25T12:00:00.000Z');
const OTHER_COMPANY = 'retention-other-co';

afterEach(async () => {
  await cleanupTestSqliteStorage();
  vi.restoreAllMocks();
});

function createRetentionService(
  repository: SyncRunRepository,
  policy: { maxCount: number; maxAgeDays: number; deleteBatchMax?: number },
  nowMs: number = NOW_MS,
): SyncRunRetentionService {
  return new SyncRunRetentionService(repository, policy, undefined, () => nowMs);
}

describe('sync run retention integration (B2c)', () => {
  it('8. prunes surplus completed runs per company and resource kind', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = storage.getBundle().syncRunRepository;
    const input = retentionRunInput(RETENTION_TEST_COMPANY, 'ledgers');

    for (let day = 1; day <= 8; day += 1) {
      seedTerminalRun(repo, input, {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
    }

    const result = createRetentionService(repo, { maxCount: 3, maxAgeDays: 90 }).prune();
    expect(result.deletedCount).toBe(4);
    expect(countRuns(repo, RETENTION_TEST_COMPANY, 'ledgers')).toBe(4);
  });

  it('9. preserves retry-eligible interrupted predecessor and lineage', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = storage.getBundle().syncRunRepository;
    const input = retentionRunInput(RETENTION_TEST_COMPANY, 'ledgers');

    for (let day = 10; day <= 15; day += 1) {
      seedTerminalRun(repo, input, {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
    }

    const predecessor = seedTerminalRun(repo, input, {
      status: 'interrupted',
      startedAt: isoDaysAgo(1, NOW_MS),
      failureCode: 'ABANDONED',
      failureSummary: 'Sync was abandoned after connector restart.',
    });

    const result = createRetentionService(repo, { maxCount: 2, maxAgeDays: 90 }).prune();
    expect(result.deletedCount).toBeGreaterThan(0);
    expect(repo.findById(RETENTION_TEST_COMPANY, predecessor.syncRunId)).not.toBeNull();
    expect(repo.findRetryPredecessor(RETENTION_TEST_COMPANY, 'ledgers')?.syncRunId).toBe(
      predecessor.syncRunId,
    );
  });

  it('10. preserves predecessor reference chain when child run exists', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = storage.getBundle().syncRunRepository;
    const input = retentionRunInput(RETENTION_TEST_COMPANY, 'stock-items');

    const predecessor = seedTerminalRun(repo, input, {
      status: 'interrupted',
      startedAt: isoDaysAgo(30, NOW_MS),
      failureCode: 'ABANDONED',
      failureSummary: 'Sync was abandoned after connector restart.',
    });

    seedTerminalRun(repo, { ...input, predecessorSyncRunId: predecessor.syncRunId }, {
      status: 'completed',
      startedAt: isoDaysAgo(1, NOW_MS),
    });

    for (let day = 2; day <= 8; day += 1) {
      seedTerminalRun(repo, input, {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
    }

    createRetentionService(repo, { maxCount: 2, maxAgeDays: 90 }).prune();
    expect(repo.findById(RETENTION_TEST_COMPANY, predecessor.syncRunId)).not.toBeNull();
  });

  it('11. prunes old terminal rows while an active run coexists in the same scope', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = storage.getBundle().syncRunRepository;
    const input = retentionRunInput(RETENTION_TEST_COMPANY, 'ledgers');

    for (let day = 1; day <= 6; day += 1) {
      seedTerminalRun(repo, input, {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
    }

    const active = repo.createRun(input);
    const result = createRetentionService(repo, { maxCount: 2, maxAgeDays: 90 }).prune();
    expect(result.scopesWithActiveRunCount).toBe(1);
    expect(result.deletedCount).toBeGreaterThan(0);
    expect(countRuns(repo, RETENTION_TEST_COMPANY, 'ledgers')).toBeLessThan(7);
    expect(repo.findById(RETENTION_TEST_COMPANY, active.syncRunId)).not.toBeNull();
  });

  it('12. isolates retention by company and resource kind', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = storage.getBundle().syncRunRepository;

    for (let day = 1; day <= 5; day += 1) {
      seedTerminalRun(repo, retentionRunInput(RETENTION_TEST_COMPANY, 'ledgers'), {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
      seedTerminalRun(repo, retentionRunInput(RETENTION_TEST_COMPANY, 'stock-items'), {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
      seedTerminalRun(repo, retentionRunInput(OTHER_COMPANY, 'ledgers'), {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
    }

    createRetentionService(repo, { maxCount: 2, maxAgeDays: 90 }).prune();
    expect(countRuns(repo, RETENTION_TEST_COMPANY, 'ledgers')).toBe(3);
    expect(countRuns(repo, RETENTION_TEST_COMPANY, 'stock-items')).toBe(3);
    expect(countRuns(repo, OTHER_COMPANY, 'ledgers')).toBe(3);
  });

  it('13. does not delete ledger domain rows during sync run pruning', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().syncRunRepository;
    const ledgerRepo = storage.getBundle().ledgerRepository;
    const database = storage.getBundle().database;

    await ledgerRepo.upsertMany(RETENTION_TEST_COMPANY, [mapNormalizedLedgerToDomain(sampleNormalizedLedger())]);

    const input = retentionRunInput(RETENTION_TEST_COMPANY, 'ledgers');
    for (let day = 1; day <= 6; day += 1) {
      seedTerminalRun(repo, input, {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
    }

    createRetentionService(repo, { maxCount: 2, maxAgeDays: 90 }).prune();
    expect(countRuns(repo, RETENTION_TEST_COMPANY, 'ledgers')).toBe(3);
    expect(await ledgerRepo.countByCompany(RETENTION_TEST_COMPANY)).toBe(1);

    const row = database
      .getDatabase()
      .prepare('SELECT COUNT(*) AS count FROM ledgers WHERE company_id = ?')
      .get(RETENTION_TEST_COMPANY) as { count: number };
    expect(row.count).toBe(1);
    expect(basePath).toBeTruthy();
  });

  it('14. runs retention during storage startup after abandoned recovery', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-retention-startup-'));
    const databasePath = path.join(basePath, 'venture-ledger.db');
    const bootstrap = new SqliteDatabase({ databasePath });
    bootstrap.open();
    const repo = new SyncRunRepository(bootstrap);
    const input = retentionRunInput(RETENTION_TEST_COMPANY, 'ledgers');
    for (let day = 1; day <= 6; day += 1) {
      seedTerminalRun(repo, input, {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
    }
    bootstrap.close();

    const config = {
      ...createTestConnectorConfig(basePath),
      syncRunHistoryMaxCount: 1,
    };
    const storage = new SqliteStorageService(config, {
      info: () => {},
      warn: () => {},
      error: () => {},
      debug: () => {},
      child: () =>
        ({
          info: () => {},
          warn: () => {},
          error: () => {},
          debug: () => {},
          child: () => ({} as never),
        }) as never,
    } as never);

    try {
      await storage.start();
      expect(countRuns(storage.getBundle().syncRunRepository, RETENTION_TEST_COMPANY, 'ledgers')).toBe(2);
    } finally {
      await storage.stop();
      fs.rmSync(basePath, { recursive: true, force: true, maxRetries: 3, retryDelay: 50 });
    }
  });

  it('15. reports failure without throwing when delete fails', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = storage.getBundle().syncRunRepository;
    const input = retentionRunInput(RETENTION_TEST_COMPANY, 'ledgers');
    for (let day = 1; day <= 5; day += 1) {
      seedTerminalRun(repo, input, {
        status: 'completed',
        startedAt: isoDaysAgo(day, NOW_MS),
      });
    }

    vi.spyOn(repo, 'deleteRunsByIds').mockImplementation(() => {
      throw new Error('delete failed');
    });

    const result = createRetentionService(repo, { maxCount: 2, maxAgeDays: 90 }).prune();
    expect(result.ok).toBe(false);
    expect(result.failureCount).toBe(1);
    expect(countRuns(repo, RETENTION_TEST_COMPANY, 'ledgers')).toBe(5);
  });

  it('16. deletes age-expired rows under count limit via integration', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = storage.getBundle().syncRunRepository;
    const input = retentionRunInput(RETENTION_TEST_COMPANY, 'ledgers');

    seedTerminalRun(repo, input, {
      status: 'completed',
      startedAt: isoDaysAgo(120, NOW_MS),
    });
    seedTerminalRun(repo, input, {
      status: 'completed',
      startedAt: isoDaysAgo(10, NOW_MS),
    });

    createRetentionService(repo, { maxCount: 100, maxAgeDays: 90 }).prune();
    expect(countRuns(repo, RETENTION_TEST_COMPANY, 'ledgers')).toBe(1);
  });
});
