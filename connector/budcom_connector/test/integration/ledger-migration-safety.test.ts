import { afterEach, describe, expect, it, vi } from 'vitest';

import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import { LEDGER_IDENTITY_VERSION } from '../../src/extraction/core/ledger-identity.js';
import { AppError } from '../../src/infrastructure/errors/app-error.js';
import { sampleNameFallbackLedger, sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
import {
  createMigrationLedgerService,
  createMigrationReadPort,
  legacyNormalizedLedgers,
  MIGRATION_COMPANY_ID,
  seedLegacyLedgerCache,
} from '../helpers/ledger-migration-test-helpers.js';
import {
  cleanupTestSqliteStorage,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

const LEGACY_NAMES = ['Legacy Cash', 'Legacy Bank', 'Legacy Debtor', 'Legacy Creditor', 'Legacy Expense'] as const;

function incomingGuidLedger(name: string, index: number) {
  const suffix = String(index).padStart(12, '0');
  const guid = `aaaaaaaa-bbbb-cccc-dddd-${suffix}`;
  return sampleNormalizedLedger({
    name,
    guid,
    id: `guid:${guid}`,
    alterId: String(7000 + index),
  });
}

function incomingForNames(names: readonly string[]) {
  return names.map((name, index) => incomingGuidLedger(name, index + 1));
}

afterEach(async () => {
  await cleanupTestSqliteStorage();
  vi.restoreAllMocks();
});

describe('ledger identity migration replacement safety', () => {
  it('1A blocks empty extraction during migration and preserves legacy cache', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 5);
    const beforeIds = legacyNormalizedLedgers(5).map((ledger) => ledger.id);

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 1 }))),
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toSatisfy((error: unknown) => {
      expect(error).toBeInstanceOf(AppError);
      const appError = error as AppError;
      expect(appError.statusCode).toBeGreaterThanOrEqual(400);
      expect(String(appError.message).toLowerCase()).toMatch(/empty|partial|migration|cache|rebuild|extraction/);
      return true;
    });

    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(5);
    for (const id of beforeIds) {
      expect(await repo.findById(MIGRATION_COMPANY_ID, id)).not.toBeNull();
    }
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBeLessThan(LEDGER_IDENTITY_VERSION);
    expect(await repo.hasLegacyLedgerIds(MIGRATION_COMPANY_ID)).toBe(true);
  });

  it('1B blocks partial GUID coverage during migration and preserves legacy cache', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 4);
    const beforeCount = await repo.countByCompany(MIGRATION_COMPANY_ID);

    const partialItems = [
      sampleNormalizedLedger({ name: 'Guid Ledger', guid: '11111111-2222-3333-4444-555555555501', alterId: '7001' }),
      sampleNameFallbackLedger({ name: 'Fallback Ledger' }),
    ];

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(vi.fn(async () => ({ items: partialItems, durationMs: 1, rawByteLength: 100 }))),
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toSatisfy((error: unknown) => {
      expect(error).toBeInstanceOf(AppError);
      const appError = error as AppError;
      expect(String(appError.message).toLowerCase()).toMatch(/partial|migration|cache|rebuild|extraction|guid|fallback/);
      return true;
    });

    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(beforeCount);
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBeLessThan(LEDGER_IDENTITY_VERSION);
    expect(await repo.hasLegacyLedgerIds(MIGRATION_COMPANY_ID)).toBe(true);
  });

  it('1C blocks GUID-complete subset when legacy coverage is incomplete', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 5);
    const beforeIds = legacyNormalizedLedgers(5).map((ledger) => ledger.id);

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(
        vi.fn(async () => ({
          items: incomingForNames([LEGACY_NAMES[0], LEGACY_NAMES[1]]),
          durationMs: 1,
          rawByteLength: 100,
        })),
      ),
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toSatisfy((error: unknown) => {
      expect(error).toBeInstanceOf(AppError);
      const appError = error as AppError;
      expect(String(appError.message).toLowerCase()).toMatch(/deferred|blocked|legacy|coverage|unmatched|rename/);
      expect(appError.details).toMatchObject({
        migrationCoverage: expect.objectContaining({
          existingLegacyCount: 5,
          unmatchedLegacyCount: 3,
          safeToReplace: false,
        }),
      });
      return true;
    });

    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(5);
    for (const id of beforeIds) {
      expect(await repo.findById(MIGRATION_COMPANY_ID, id)).not.toBeNull();
    }
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBeLessThan(LEDGER_IDENTITY_VERSION);
  });

  it('1D performs valid complete migration with backup and GUID-first replacement', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 2);
    const migratedItems = incomingForNames([LEGACY_NAMES[0], LEGACY_NAMES[1]]);

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(vi.fn(async () => ({ items: migratedItems, durationMs: 1, rawByteLength: 100 }))),
    );
    await service.start();
    const result = await service.syncLedgers();

    expect(result.status).toBe('completed');
    expect(await repo.findById(MIGRATION_COMPANY_ID, 'legacy-cash')).toBeNull();
    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(2);
    expect(await repo.hasLegacyLedgerIds(MIGRATION_COMPANY_ID)).toBe(false);
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBe(LEDGER_IDENTITY_VERSION);
  });

  it('1E preserves legacy cache when replacement transaction fails after coverage passes', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 3);
    const beforeCount = await repo.countByCompany(MIGRATION_COMPANY_ID);

    const replaceSpy = vi.spyOn(repo, 'replaceCompanyLedgersAtomically').mockImplementation(() => {
      throw new Error('injected replace failure');
    });

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(
        vi.fn(async () => ({
          items: incomingForNames(LEGACY_NAMES.slice(0, 3)),
          durationMs: 1,
          rawByteLength: 20,
        })),
      ),
      repo,
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toThrow(/injected replace failure/i);
    expect(replaceSpy).toHaveBeenCalledTimes(1);
    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(beforeCount);
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBeLessThan(LEDGER_IDENTITY_VERSION);
    expect(await repo.hasLegacyLedgerIds(MIGRATION_COMPANY_ID)).toBe(true);
  });

  it('1F allows migration when all legacy rows are covered and incoming contains extras', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 3);

    const migratedItems = incomingForNames([
      ...LEGACY_NAMES.slice(0, 3),
      'Extra Ledger Alpha',
      'Extra Ledger Beta',
    ]);

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(vi.fn(async () => ({ items: migratedItems, durationMs: 1, rawByteLength: 100 }))),
    );
    await service.start();
    await service.syncLedgers();

    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(5);
    expect(await repo.hasLegacyLedgerIds(MIGRATION_COMPANY_ID)).toBe(false);
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBe(LEDGER_IDENTITY_VERSION);
  });

  it('1G blocks migration when multiple incoming ledgers share a legacy canonical key', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 1);

    const duplicateIncoming = [
      incomingGuidLedger('Legacy Cash', 1),
      incomingGuidLedger('Legacy Cash', 2),
    ];

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(vi.fn(async () => ({ items: duplicateIncoming, durationMs: 1, rawByteLength: 100 }))),
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toSatisfy((error: unknown) => {
      const appError = error as AppError;
      expect(String(appError.message).toLowerCase()).toMatch(/ambiguous|blocked/);
      expect(appError.details).toMatchObject({
        migrationCoverage: expect.objectContaining({
          ambiguousMatchCount: expect.any(Number),
          safeToReplace: false,
        }),
      });
      return true;
    });

    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(1);
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBeLessThan(LEDGER_IDENTITY_VERSION);
  });

  it('1H blocks migration when duplicate legacy rows share a canonical migration key', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(MIGRATION_COMPANY_ID, [
      mapNormalizedLedgerToDomain(
        sampleNormalizedLedger({ id: 'legacy-cash-a', name: 'Legacy Cash', guid: undefined, identitySource: 'name' }),
      ),
      mapNormalizedLedgerToDomain(
        sampleNormalizedLedger({ id: 'legacy-cash-b', name: 'LEGACY CASH', guid: undefined, identitySource: 'name' }),
      ),
    ]);

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(
        vi.fn(async () => ({
          items: incomingForNames(['Legacy Cash']),
          durationMs: 1,
          rawByteLength: 100,
        })),
      ),
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toSatisfy((error: unknown) => {
      const appError = error as AppError;
      expect(String(appError.message).toLowerCase()).toMatch(/ambiguous|blocked|deferred/);
      return true;
    });
    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(2);
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBeLessThan(LEDGER_IDENTITY_VERSION);
  });

  it('1I defers migration when a legacy row name no longer matches incoming GUID ledger name', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(MIGRATION_COMPANY_ID, [
      mapNormalizedLedgerToDomain(
        sampleNormalizedLedger({
          id: 'old-supplier',
          name: 'Old Supplier Name',
          guid: undefined,
          identitySource: 'name',
        }),
      ),
    ]);

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(
        vi.fn(async () => ({
          items: incomingForNames(['Renamed Supplier Name']),
          durationMs: 1,
          rawByteLength: 100,
        })),
      ),
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toSatisfy((error: unknown) => {
      const appError = error as AppError;
      expect(String(appError.message).toLowerCase()).toMatch(/deferred|unmatched|rename|legacy/);
      return true;
    });
    expect(await repo.findById(MIGRATION_COMPANY_ID, 'old-supplier')).not.toBeNull();
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBeLessThan(LEDGER_IDENTITY_VERSION);
  });

  it('1J succeeds when every legacy row has exactly one incoming GUID-first match', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 5);
    const migratedItems = incomingForNames(LEGACY_NAMES);

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(vi.fn(async () => ({ items: migratedItems, durationMs: 1, rawByteLength: 100 }))),
    );
    await service.start();
    const result = await service.syncLedgers();

    expect(result.status).toBe('completed');
    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(5);
    expect(await repo.hasLegacyLedgerIds(MIGRATION_COMPANY_ID)).toBe(false);
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBe(LEDGER_IDENTITY_VERSION);
  });

  it('1K preserves cache when replacement fails after exact correspondence succeeds', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = await seedLegacyLedgerCache(storage, 2);
    const beforeCount = await repo.countByCompany(MIGRATION_COMPANY_ID);

    vi.spyOn(repo, 'replaceCompanyLedgersAtomically').mockImplementation(() => {
      throw new Error('injected replace failure');
    });

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(
        vi.fn(async () => ({
          items: incomingForNames(LEGACY_NAMES.slice(0, 2)),
          durationMs: 1,
          rawByteLength: 20,
        })),
      ),
      repo,
    );
    await service.start();

    await expect(service.syncLedgers()).rejects.toThrow(/injected replace failure/i);
    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(beforeCount);
    expect(await repo.getLedgerIdentityVersion(MIGRATION_COMPANY_ID)).toBeLessThan(LEDGER_IDENTITY_VERSION);
  });

  it('normal upsert sync still accepts empty extraction when migration is not required', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(
      MIGRATION_COMPANY_ID,
      [mapNormalizedLedgerToDomain(sampleNormalizedLedger({ id: 'guid:existing', name: 'Existing' }))],
    );
    await repo.markLedgerIdentityCurrent(MIGRATION_COMPANY_ID);

    const service = createMigrationLedgerService(
      storage,
      basePath,
      createMigrationReadPort(vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 1 }))),
    );
    await service.start();
    const result = await service.syncLedgers();

    expect(result.status).toBe('completed');
    expect(await repo.countByCompany(MIGRATION_COMPANY_ID)).toBe(1);
    expect(await repo.findById(MIGRATION_COMPANY_ID, 'guid:existing')).not.toBeNull();
  });
});
