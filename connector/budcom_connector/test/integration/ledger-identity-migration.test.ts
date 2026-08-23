import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import { LEDGER_IDENTITY_VERSION } from '../../src/extraction/core/ledger-identity.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { assertLedgerRebuildPrecheck } from '../../src/services/ledger/ledger-cache-migration.js';
import { assessLedgerExtraction } from '../../src/erp/ledger/ledger-extraction-quality.js';
import { validateLedgerCollection } from '../../src/erp/ledger/ledger-validation.js';
import { sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../helpers/sqlite-test-storage.js';

const COMPANY = 'estimation';

afterEach(async () => {
  await cleanupTestSqliteStorage();
  vi.restoreAllMocks();
});

function createReadPort(overrides: Partial<ErpReadPort> = {}): ErpReadPort {
  return {
    isReady: () => true,
    discoverCompanies: vi.fn(),
    getGroups: vi.fn(),
    getCompanyInfo: vi.fn(),
    readLedgerGroups: vi.fn(),
    readLedgerContactDetails: vi.fn(),
    readLedgers: vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 1 })),
    readStockGroups: vi.fn(),
    readStockCategories: vi.fn(),
    readStockItems: vi.fn(async () => ({ items: [], durationMs: 1, rawByteLength: 1 })),
    readGodowns: vi.fn(),
    readCostCategories: vi.fn(),
    readCostCentres: vi.fn(),
    readVoucherTypes: vi.fn(),
    readGstRegistrations: vi.fn(),
    getReadDiagnostics: vi.fn(() => []),
    ...overrides,
  };
}

function createLedgerService(
  storage: Awaited<ReturnType<typeof createTestSqliteStorage>>['storage'],
  basePath: string,
  readPort: ErpReadPort,
) {
  return new LedgerSyncServiceImpl(
    createTestConnectorConfig(basePath),
    readPort,
    { resolveName: vi.fn(async () => 'Demo Company') } as never,
    createPermissiveSessionMock(),
    createLogger({ service: 'test', level: 'error' }),
    storage,
  );
}

describe('ledger identity migration and rename', () => {
  it('updates one row when GUID is stable but name changes', async () => {
    const guid = 'cccc-dddd-eeee-ffff-000000000099';
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = createReadPort({
      readLedgers: vi
        .fn()
        .mockResolvedValueOnce({
          items: [
            sampleNormalizedLedger({
              guid,
              id: `guid:${guid}`,
              name: 'Probe Ledger A',
              normalizedName: 'probe ledger a',
            }),
          ],
          durationMs: 1,
          rawByteLength: 20,
        })
        .mockResolvedValueOnce({
          items: [
            sampleNormalizedLedger({
              guid,
              id: `guid:${guid}`,
              name: 'Probe Ledger A Renamed',
              normalizedName: 'probe ledger a renamed',
              alterId: '1002',
            }),
          ],
          durationMs: 1,
          rawByteLength: 20,
        }),
    });

    const service = createLedgerService(storage, basePath, readPort);
    await service.start();
    await service.syncLedgers();
    await service.syncLedgers();

    const repo = storage.getBundle().ledgerRepository;
    expect(await repo.countByCompany(COMPANY)).toBe(1);
    const row = await repo.findById(COMPANY, `guid:${guid}`);
    expect(row?.name).toBe('Probe Ledger A Renamed');
    expect(row?.alterId).toBe('1002');
  });

  it('preserves old cache when extraction contract fails during migration', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(
        sampleNormalizedLedger({ id: 'cash', name: 'Cash', guid: undefined, identitySource: 'name' }),
      ),
    ]);

    const service = createLedgerService(
      storage,
      basePath,
      createReadPort({
        readLedgers: vi.fn(async () => ({
          items: [sampleNormalizedLedger({ guid: undefined, id: 'name:cash', parentGroup: undefined })],
          durationMs: 1,
          rawByteLength: 20,
        })),
      }),
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toThrow(/shallow|contract|invalid/i);
    expect(await repo.findById(COMPANY, 'cash')).not.toBeNull();
    expect(await repo.getLedgerIdentityVersion(COMPANY)).toBeLessThan(LEDGER_IDENTITY_VERSION);
  });

  it('rebuilds legacy slug cache with GUID-first identities', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const repo = storage.getBundle().ledgerRepository;
    await repo.upsertMany(COMPANY, [
      mapNormalizedLedgerToDomain(
        sampleNormalizedLedger({ id: 'cash', name: 'Cash', guid: undefined, identitySource: 'name' }),
      ),
    ]);

    const service = createLedgerService(
      storage,
      basePath,
      createReadPort({
        readLedgers: vi.fn(async () => ({
          items: [sampleNormalizedLedger()],
          durationMs: 1,
          rawByteLength: 20,
        })),
      }),
    );
    await service.start();
    await service.syncLedgers();

    expect(await repo.findById(COMPANY, 'cash')).toBeNull();
    expect(await repo.hasLegacyLedgerIds(COMPANY)).toBe(false);
    expect(await repo.getLedgerIdentityVersion(COMPANY)).toBe(LEDGER_IDENTITY_VERSION);
  });

  it('rejects rebuild when duplicate resolved IDs are present', () => {
    const ledgers = [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger()),
      mapNormalizedLedgerToDomain(sampleNormalizedLedger()),
    ];
    const assessment = assessLedgerExtraction(
      ledgers.map((ledger) => ({
        id: ledger.id,
        name: ledger.name,
        normalizedName: ledger.normalizedName,
        guid: ledger.guid,
        parentGroup: ledger.parentGroup,
      })),
    );
    const validation = validateLedgerCollection(ledgers);
    expect(() => assertLedgerRebuildPrecheck({ assessment, validation, ledgers })).toThrow(/validation failed|duplicate/i);
  });
});
