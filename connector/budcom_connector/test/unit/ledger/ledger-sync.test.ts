import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../../src/services/ledger/ledger-sync.service.js';
import type { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';
import {
  sampleNormalizedAmount,
  sampleNormalizedLedger,
} from '../../helpers/ledger-fixtures.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('LedgerSyncServiceImpl', () => {
  it('syncs ledgers into repository with statistics', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(async () => ({
        items: [
          sampleNormalizedLedger({
            id: 'cash',
            name: 'Cash',
            normalizedName: 'cash',
            parentGroup: 'Cash-in-Hand',
            closingBalance: sampleNormalizedAmount('100', 'Dr'),
          }),
        ],
        durationMs: 1,
        rawByteLength: 100,
      })),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const companyResolver = {
      resolveName: vi.fn(async () => 'Demo Company'),
    } as unknown as CompanyResolver;
    const connectorSession = createPermissiveSessionMock();
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );

    await service.start();
    expect((await service.getSyncProgress()).totalExpected).toBeNull();
    const result = await service.syncLedgers();
    expect(result.status).toBe('completed');
    expect(result.statistics.totalLedgers).toBe(1);
    expect(result.progress.totalExpected).toBe(1);
    expect((await service.getSyncProgress()).totalExpected).toBe(1);

    const ledgers = await service.getLedgers({ page: 1, pageSize: 10 });
    expect(ledgers.items[0]?.name).toBe('Cash');
  });

  it('supports cancellation', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(async () => ({
        items: Array.from({ length: 3 }, (_, index) =>
          sampleNormalizedLedger({
            id: `ledger-${index}`,
            name: `Ledger ${index}`,
            normalizedName: `ledger ${index}`,
          }),
        ),
        durationMs: 1,
        rawByteLength: 100,
      })),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const companyResolver = {
      resolveName: vi.fn(async () => 'Demo Company'),
    } as unknown as CompanyResolver;
    const connectorSession = createPermissiveSessionMock();
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );

    await service.start();
    const syncPromise = service.syncLedgers();
    await service.cancelSync();
    const result = await syncPromise;
    expect(['cancelled', 'completed']).toContain(result.status);
  });

  it('returns idle progress and never throws before any company is selected', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const companyResolver = { resolveName: vi.fn() } as unknown as CompanyResolver;
    const noCompanySession = createPermissiveSessionMock({
      getSession: () => ({
        session: {
          connectorVersion: '0.3.1',
          erpType: 'tally' as never,
          connectionStatus: 'connected',
          selectedCompany: undefined,
          selectedAt: null,
          lastValidatedAt: null,
        } as never,
        contractVersion: '1',
      }),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      companyResolver,
      noCompanySession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );

    await service.start();
    const progress = await service.getSyncProgress();
    expect(progress.status).toBe('idle');
    const cancelled = await service.cancelSync();
    expect(cancelled.status).toBe('idle');
  });

  describe('company isolation (TD-036)', () => {
    function makeSwitchableSession(initialCompanyId: string) {
      let currentCompanyId = initialCompanyId;
      const session = createPermissiveSessionMock({
        getSession: () => ({
          session: {
            connectorVersion: '0.3.1',
            erpType: 'tally' as never,
            connectionStatus: 'connected',
            selectedCompany: { id: currentCompanyId, name: currentCompanyId },
            selectedAt: new Date().toISOString(),
            lastValidatedAt: new Date().toISOString(),
          } as never,
          contractVersion: '1',
        }),
        validateForOperation: async () => ({
          status: 'SUCCESS',
          session: undefined as never,
          companyId: currentCompanyId,
          companyName: currentCompanyId,
        }),
      });
      return {
        session,
        switchTo: (companyId: string) => {
          currentCompanyId = companyId;
        },
      };
    }

    it('a sync in flight for one company never blocks or is visible to a different company', async () => {
      const { storage, basePath } = await createTestSqliteStorage();
      let releaseCompanyA: (() => void) | undefined;
      const companyAGate = new Promise<void>((resolve) => {
        releaseCompanyA = resolve;
      });
      const readPort: ErpReadPort = {
        isReady: () => true,
        discoverCompanies: vi.fn(),
        getGroups: vi.fn(),
        getCompanyInfo: vi.fn(),
        readLedgerGroups: vi.fn(),
        readLedgers: vi.fn(async (companyName: string) => {
          if (companyName === 'company-a') {
            await companyAGate;
          }
          return {
            items: [
              sampleNormalizedLedger({
                id: `${companyName}-ledger`,
                name: `${companyName} Ledger`,
                normalizedName: `${companyName} ledger`,
              }),
            ],
            durationMs: 1,
            rawByteLength: 100,
          };
        }),
        readStockGroups: vi.fn(),
        readStockCategories: vi.fn(),
        readStockItems: vi.fn(),
        readGodowns: vi.fn(),
        readCostCategories: vi.fn(),
        readCostCentres: vi.fn(),
        readVoucherTypes: vi.fn(),
        readGstRegistrations: vi.fn(),
        getReadDiagnostics: vi.fn(() => []),
      };
      const companyResolver = {
        resolveName: vi.fn(async (companyId: string) => companyId),
      } as unknown as CompanyResolver;
      const { session, switchTo } = makeSwitchableSession('company-a');
      const service = new LedgerSyncServiceImpl(
        createTestConnectorConfig(basePath),
        readPort,
        companyResolver,
        session,
        createLogger({ service: 'test', level: 'error' }),
        storage,
      );
      await service.start();

      // Company A's sync starts and blocks mid-extraction (simulating a real, slow Tally pull).
      const companyASyncPromise = service.syncLedgers();
      await vi.waitFor(async () => {
        expect((await service.getSyncProgress()).status).toBe('running');
      });

      // The operator switches the Connector's selected company to B while A is still syncing.
      // Before TD-036's fix, the process-wide `syncInFlight` singleton would reject this as
      // "already running" purely because A happened to still be in flight — even though nothing
      // in the database actually conflicts (A and B are different companies).
      switchTo('company-b');
      const companyBResult = await service.syncLedgers();
      expect(companyBResult.status).toBe('completed');
      expect(companyBResult.statistics.totalLedgers).toBe(1);

      // Company B's own progress must reflect B, not leak A's still-running state.
      const companyBProgress = await service.getSyncProgress();
      expect(companyBProgress.companyId).toBe('company-b');
      expect(companyBProgress.status).toBe('completed');

      // Cancelling "the current company" (now B, already completed) must never reach into A's
      // still-in-flight run.
      const cancelResult = await service.cancelSync();
      expect(cancelResult.companyId).toBe('company-b');

      // Release A and let it finish; A's own final state must be its own, uncontaminated by B.
      releaseCompanyA?.();
      const companyAResult = await companyASyncPromise;
      expect(companyAResult.status).toBe('completed');
      expect(companyAResult.statistics.totalLedgers).toBe(1);

      switchTo('company-a');
      const companyAProgress = await service.getSyncProgress();
      expect(companyAProgress.companyId).toBe('company-a');
      expect(companyAProgress.status).toBe('completed');
      expect(companyAProgress.totalExpected).toBe(1);

      const ledgersA = await (async () => {
        switchTo('company-a');
        return service.getLedgers({ page: 1, pageSize: 10 });
      })();
      expect(ledgersA.items.map((item) => item.name)).toEqual(['company-a Ledger']);

      switchTo('company-b');
      const ledgersB = await service.getLedgers({ page: 1, pageSize: 10 });
      expect(ledgersB.items.map((item) => item.name)).toEqual(['company-b Ledger']);
    });

    it('identical ledger natural keys in two companies never cross-contaminate progress or storage', async () => {
      const { storage, basePath } = await createTestSqliteStorage();
      const readPort: ErpReadPort = {
        isReady: () => true,
        discoverCompanies: vi.fn(),
        getGroups: vi.fn(),
        getCompanyInfo: vi.fn(),
        readLedgerGroups: vi.fn(),
        readLedgers: vi.fn(async () => ({
          items: [
            sampleNormalizedLedger({
              id: 'guid:same-guid-both-companies',
              name: 'Cash',
              normalizedName: 'cash',
            }),
          ],
          durationMs: 1,
          rawByteLength: 100,
        })),
        readStockGroups: vi.fn(),
        readStockCategories: vi.fn(),
        readStockItems: vi.fn(),
        readGodowns: vi.fn(),
        readCostCategories: vi.fn(),
        readCostCentres: vi.fn(),
        readVoucherTypes: vi.fn(),
        readGstRegistrations: vi.fn(),
        getReadDiagnostics: vi.fn(() => []),
      };
      const companyResolver = {
        resolveName: vi.fn(async (companyId: string) => companyId),
      } as unknown as CompanyResolver;
      const { session, switchTo } = makeSwitchableSession('company-a');
      const service = new LedgerSyncServiceImpl(
        createTestConnectorConfig(basePath),
        readPort,
        companyResolver,
        session,
        createLogger({ service: 'test', level: 'error' }),
        storage,
      );
      await service.start();

      await service.syncLedgers();
      switchTo('company-b');
      await service.syncLedgers();

      switchTo('company-a');
      const ledgerA = await service.getLedgerById('guid:same-guid-both-companies');
      switchTo('company-b');
      const ledgerB = await service.getLedgerById('guid:same-guid-both-companies');

      expect(ledgerA).not.toBeNull();
      expect(ledgerB).not.toBeNull();
      // Same natural key/GUID in both companies — proves storage isolation is by companyId, not
      // by ledger identity alone.
      switchTo('company-a');
      const statsA = await service.getStatistics();
      switchTo('company-b');
      const statsB = await service.getStatistics();
      expect(statsA.totalLedgers).toBe(1);
      expect(statsB.totalLedgers).toBe(1);
    });
  });
});
