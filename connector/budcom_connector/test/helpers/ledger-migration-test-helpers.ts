import { vi } from 'vitest';

import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import type { NormalizedLedger } from '../../src/extraction/core/types.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import type { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import type { LedgerRepositoryPort } from '../../src/services/ledger/ledger-repository.interface.js';
import { sampleNormalizedLedger } from './ledger-fixtures.js';
import { createPermissiveSessionMock } from './session-mock.js';
import { createTestConnectorConfig } from './sqlite-test-storage.js';

export const MIGRATION_COMPANY_ID = 'estimation';

const LEGACY_LEDGER_NAMES = ['Legacy Cash', 'Legacy Bank', 'Legacy Debtor', 'Legacy Creditor', 'Legacy Expense'] as const;

export function legacyNormalizedLedgers(count: number = LEGACY_LEDGER_NAMES.length): NormalizedLedger[] {
  return LEGACY_LEDGER_NAMES.slice(0, count).map((name) =>
    sampleNormalizedLedger({
      id: name.toLowerCase().replace(/\s+/g, '-'),
      name,
      guid: undefined,
      identitySource: 'name',
      dataQuality: 'partial',
    }),
  );
}

export async function seedLegacyLedgerCache(
  storage: SqliteStorageService,
  count: number = LEGACY_LEDGER_NAMES.length,
): Promise<LedgerRepositoryPort> {
  const repo = storage.getBundle().ledgerRepository;
  const ledgers = legacyNormalizedLedgers(count).map((ledger) => mapNormalizedLedgerToDomain(ledger));
  await repo.upsertMany(MIGRATION_COMPANY_ID, ledgers);
  return repo;
}

export function createMigrationReadPort(
  readLedgers: ErpReadPort['readLedgers'],
  overrides: Partial<ErpReadPort> = {},
): ErpReadPort {
  return {
    isReady: () => true,
    discoverCompanies: vi.fn(),
    getGroups: vi.fn(),
    getCompanyInfo: vi.fn(),
    readLedgerGroups: vi.fn(),
    readLedgerContactDetails: vi.fn(),
    readLedgers,
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

export function createMigrationLedgerService(
  storage: SqliteStorageService,
  basePath: string,
  readPort: ErpReadPort,
  repository?: LedgerRepositoryPort,
): LedgerSyncServiceImpl {
  return new LedgerSyncServiceImpl(
    createTestConnectorConfig(basePath),
    readPort,
    { resolveName: vi.fn(async () => 'Demo Company') } as never,
    createPermissiveSessionMock(),
    createLogger({ service: 'test', level: 'error' }),
    storage,
    repository,
  );
}
