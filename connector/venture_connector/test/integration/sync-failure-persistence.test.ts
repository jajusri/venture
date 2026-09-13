import { afterEach, describe, expect, it, vi } from 'vitest';

import { AppError, ErrorCodes } from '../../src/infrastructure/errors/app-error.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../../src/services/stock-item/stock-item-sync.service.js';
import { SYNC_FAILURE_SUMMARIES } from '../../src/infrastructure/privacy/sync-failure-normalizer.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import { createTestConnectorConfig, createTestSqliteStorage, cleanupTestSqliteStorage } from '../helpers/sqlite-test-storage.js';

const FORBIDDEN = [
  'jaju sanitations',
  '29aabCU9603R1ZM'.toLowerCase(),
  '<ledger',
  '<envelope',
  'c:\\users',
  'sensitive debtor',
] as const;

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

function buildReadPort(readLedgers: ErpReadPort['readLedgers'], readStockItems: ErpReadPort['readStockItems']) {
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
    readStockItems,
    readGodowns: vi.fn(),
    readCostCategories: vi.fn(),
    readCostCentres: vi.fn(),
    readVoucherTypes: vi.fn(),
    readGstRegistrations: vi.fn(),
    getReadDiagnostics: vi.fn(() => []),
  } as unknown as ErpReadPort;
}

describe('sync failure persistence privacy (group 4 integration)', () => {
  it.each([
    {
      label: 'ledger transport unavailable',
      resource: 'ledgers' as const,
      error: new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Tally connection failed: fetch failed for JAJU SANITATIONS PRIVATE LIMITED',
        503,
      ),
      expectedSummary: SYNC_FAILURE_SUMMARIES.transport_unavailable,
    },
    {
      label: 'stock contract rejection',
      resource: 'stock-items' as const,
      error: new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Shallow stock export detected with <STOCKITEM NAME="Sensitive Debtor"/>',
        503,
      ),
      expectedSummary: SYNC_FAILURE_SUMMARIES.response_contract_rejected,
    },
  ])('stores normalized failure for $label', async ({ resource, error, expectedSummary }) => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = buildReadPort(
      vi.fn(async () => {
        throw error;
      }),
      vi.fn(async () => {
        throw error;
      }),
    );
    const connectorSession = createPermissiveSessionMock();
    const companyResolver = { resolveName: vi.fn(async () => 'ESTIMATION') } as unknown as CompanyResolver;
    const config = createTestConnectorConfig(basePath);
    const logger = createLogger({ service: 'test', level: 'error' });

    if (resource === 'ledgers') {
      const service = new LedgerSyncServiceImpl(
        config,
        readPort,
        companyResolver,
        connectorSession,
        logger,
        storage,
      );
      await service.start();
      await expect(service.syncLedgers()).rejects.toBeDefined();
      const runs = storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers');
      expect(runs).toHaveLength(1);
      expect(runs[0]?.status).toBe('failed');
      expect(runs[0]?.failureSummary).toBe(expectedSummary);
      const serialized = JSON.stringify(runs[0]);
      for (const value of FORBIDDEN) {
        expect(serialized.toLowerCase()).not.toContain(value);
      }
      return;
    }

    const service = new StockItemSyncServiceImpl(
      config,
      readPort,
      companyResolver,
      connectorSession,
      logger,
      storage,
    );
    await service.start();
    await expect(service.syncStockItems()).rejects.toBeDefined();
    const runs = storage.getBundle().syncRunRepository.listRuns('estimation', 'stock-items');
    expect(runs).toHaveLength(1);
    expect(runs[0]?.status).toBe('failed');
    expect(runs[0]?.failureSummary).toBe(expectedSummary);
    const serialized = JSON.stringify(runs[0]);
    for (const value of FORBIDDEN) {
      expect(serialized.toLowerCase()).not.toContain(value);
    }
  });
});
