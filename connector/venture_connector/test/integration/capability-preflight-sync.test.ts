import request from 'supertest';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ServiceTokens } from '../../src/core/tokens.js';
import { AppError, ErrorCodes } from '../../src/infrastructure/errors/app-error.js';
import type { ConnectorSessionService } from '../../src/services/interfaces/connector-session.js';
import type { LedgerSyncService } from '../../src/services/ledger/ledger-sync.service.js';
import type { StockItemSyncService } from '../../src/services/stock-item/stock-item-sync.service.js';
import {
  assertNoPrivatePreflightDetails,
  summarizeTallyCalls,
} from '../helpers/capability-preflight-helpers.js';
import { createMasterDataMockFetch } from '../helpers/mock-fetch.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import { createTestConnectorConfig, createTestSqliteStorage, cleanupTestSqliteStorage } from '../helpers/sqlite-test-storage.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../../src/services/stock-item/stock-item-sync.service.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import { sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('capability preflight sync mutation boundary (5A-5D)', () => {
  it('5A ledger sync does not create sync run when session validation fails', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgerContactDetails: vi.fn(),
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
    } as unknown as ErpReadPort;
    const connectorSession = createPermissiveSessionMock({
      validateForOperation: async () => ({
        status: 'COMPANY_DISCOVERY_UNAVAILABLE',
        session: createPermissiveSessionMock().getSession().session,
        reason: 'Company discovery is unavailable',
      }),
    });
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      { resolveName: vi.fn() } as unknown as CompanyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toMatchObject({ statusCode: 503 });
    expect(storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers')).toHaveLength(0);
    expect(readPort.readLedgers).not.toHaveBeenCalled();
  });

  it('5B stock sync does not create sync run when session validation fails', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgerContactDetails: vi.fn(),
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
    } as unknown as ErpReadPort;
    const connectorSession = createPermissiveSessionMock({
      validateForOperation: async () => ({
        status: 'COMPANY_NOT_FOUND',
        session: createPermissiveSessionMock().getSession().session,
        reason: 'Selected company was not found',
      }),
    });
    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      { resolveName: vi.fn() } as unknown as CompanyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncStockItems()).rejects.toMatchObject({ statusCode: 404 });
    expect(storage.getBundle().syncRunRepository.listRuns('estimation', 'stock-items')).toHaveLength(0);
    expect(readPort.readStockItems).not.toHaveBeenCalled();
  });

  it('5C preflight pass with extraction failure preserves existing sync failure semantics', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const readPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgerContactDetails: vi.fn(),
      readLedgers: vi.fn(async () => {
        throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'transport failed', 503);
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
    } as unknown as ErpReadPort;
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      { resolveName: vi.fn(async () => 'ESTIMATION') } as unknown as CompanyResolver,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toMatchObject({ statusCode: 503 });
    const runs = await service.listSyncRuns();
    expect(runs.length).toBe(1);
    expect(runs[0]?.status).toBe('failed');
  });

  it('5D retry after pure preflight failure leaves no sync lineage', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    let allow = false;
    const connectorSession = createPermissiveSessionMock({
      validateForOperation: async () => {
        if (!allow) {
          return {
            status: 'COMPANY_DISCOVERY_UNAVAILABLE',
            session: createPermissiveSessionMock().getSession().session,
            reason: 'Company discovery is unavailable',
          };
        }
        return createPermissiveSessionMock().validateForOperation();
      },
    });
    const readPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgerContactDetails: vi.fn(),
      readLedgers: vi.fn(async () => ({
        items: [sampleNormalizedLedger({ name: 'Cash', normalizedName: 'cash' })],
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
    } as unknown as ErpReadPort;
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(basePath),
      readPort,
      { resolveName: vi.fn(async () => 'ESTIMATION') } as unknown as CompanyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();
    await expect(service.syncLedgers()).rejects.toMatchObject({ statusCode: 503 });
    expect(storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers')).toHaveLength(0);
    allow = true;
    const result = await service.syncLedgers();
    expect(result.status).toBe('completed');
    const runs = storage.getBundle().syncRunRepository.listRuns('estimation', 'ledgers');
    expect(runs[0]?.predecessorSyncRunId ?? null).toBeNull();
  });
});

describe('capability preflight minimum probe counts (6A-6D)', () => {
  it('6A ledger sync with warm cache performs one ledger export and no extra probes', async () => {
    const { fetchImpl, calls } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context, { selectCompanyId: 'estimation' });
    calls.length = 0;
    const ledgerSync = context.container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync);
    await ledgerSync.syncLedgers();
    const summary = summarizeTallyCalls(calls);
    expect(summary['ledgers']).toBe(1);
    expect(summary['health'] ?? 0).toBe(0);
    expect(summary['ledger-groups'] ?? 0).toBe(0);
    expect(summary['stock-items'] ?? 0).toBe(0);
  });

  it('6B ledger sync with warm cache performs one ledger export and no discovery on sync', async () => {
    const { fetchImpl, calls } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const session = context.container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession);
    await session.selectCompany('estimation');
    calls.length = 0;
    const ledgerSync = context.container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync);
    await ledgerSync.syncLedgers();
    const summary = summarizeTallyCalls(calls);
    expect(summary['ledgers']).toBe(1);
    expect(summary['company-discovery'] ?? 0).toBe(0);
    expect(summary['health'] ?? 0).toBe(0);
  });

  it('6C discovery failure blocks sync; ping may classify reachability only', async () => {
    const { fetchImpl, calls } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context, { selectCompanyId: 'estimation' });
    const resolver = context.container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver);
    resolver.invalidateCache();
    const discovery = context.container.resolve<import('../../src/services/interfaces/company-discovery.js').CompanyDiscoveryService>(
      ServiceTokens.CompanyDiscovery,
    );
    vi.spyOn(discovery, 'discoverCompanies').mockImplementation(async () => {
      throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Injected discovery failure', 503);
    });
    const ledgerSync = context.container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync);
    const before = calls.length;
    await expect(ledgerSync.syncLedgers()).rejects.toMatchObject({ statusCode: 503 });
    const summary = summarizeTallyCalls(calls.slice(before));
    expect(summary['ledgers'] ?? 0).toBe(0);
  });

  it('6D stock sync performs one stock export with warm cache and no extra probes', async () => {
    const { fetchImpl, calls } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context, { selectCompanyId: 'estimation' });
    calls.length = 0;
    const stockSync = context.container.resolve<StockItemSyncService>(ServiceTokens.StockItemSync);
    await stockSync.syncStockItems();
    const summary = summarizeTallyCalls(calls);
    expect(summary['stock-items']).toBe(1);
    expect(summary['health'] ?? 0).toBe(0);
    expect(summary['ledgers'] ?? 0).toBe(0);
  });
});

describe('capability preflight privacy (7)', () => {
  it('session validation errors exposed through API omit private payload fields on failure', async () => {
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context, { selectCompanyId: 'estimation' });
    const resolver = context.container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver);
    resolver.invalidateCache();
    const discovery = context.container.resolve<import('../../src/services/interfaces/company-discovery.js').CompanyDiscoveryService>(
      ServiceTokens.CompanyDiscovery,
    );
    vi.spyOn(discovery, 'discoverCompanies').mockImplementation(async () => {
      throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Injected discovery failure', 503, {
        reasonCode: 'company_discovery_unavailable',
        retryable: true,
      });
    });
    const response = await request(createTestApp(context)).post('/session/validate');
    expect(response.status).toBe(503);
    expect(response.body.status).toBe('COMPANY_DISCOVERY_UNAVAILABLE');
    assertNoPrivatePreflightDetails({
      status: response.body.status,
      reason: response.body.reason,
      sessionStatus: response.body.status,
    });
  });
});
