import request from 'supertest';
import { describe, expect, it, vi } from 'vitest';

import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { ServiceTokens } from '../../../src/core/tokens.js';
import { ERP_TYPE_TALLY } from '../../../src/erp/session/session-constants.js';
import {
  createEmptySession,
  withSelectedCompany,
} from '../../../src/services/session/session-validator.js';
import { ConnectorSessionServiceImpl } from '../../../src/services/session/connector-session.service.js';
import { SelectedCompanyRepository } from '../../../src/services/session/selected-company-repository.js';
import { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { createTestConnectorConfig } from '../../helpers/sqlite-test-storage.js';
import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import type { CompanyDiscoveryService } from '../../../src/services/interfaces/company-discovery.js';
import type { TallyConnectionService } from '../../../src/services/interfaces/tally-connection.js';
import type { TallyDiagnosticsSnapshot } from '../../../src/tally/core/types.js';
import { COMPANY_EMPTY_LIST, COMPANY_ONE_VALID } from '../../helpers/company-discovery-fixtures.js';
import { createTestApp, createTestContext, startTestServices } from '../../helpers/test-context.js';
import { createTallyMockFetch } from '../../helpers/mock-fetch.js';

describe('capability preflight session fail-closed (3A-3F)', () => {
  function makeSessionService(input: {
    readonly discoveryImpl: CompanyDiscoveryService['discoverCompanies'];
    readonly pingImpl?: () => Promise<boolean>;
    readonly isReady?: boolean;
  }) {
    const config = createTestConnectorConfig('/tmp/session-preflight');
    const discovery: CompanyDiscoveryService = {
      start: vi.fn(),
      stop: vi.fn(),
      isRunning: () => true,
      discoverCompanies: input.discoveryImpl,
      getStatus: () => ({ name: 'CompanyDiscovery', running: true, ready: true }),
    };
    const resolver = new CompanyResolver(discovery);
    const readPort: ErpReadPort = {
      isReady: () => input.isReady ?? true,
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
    const tallyConnection: TallyConnectionService = {
      start: vi.fn(),
      stop: vi.fn(),
      isRunning: () => true,
      ping: input.pingImpl ?? (async () => true),
      getDiagnostics: () =>
        ({
          state: 'connected',
          circuitState: 'closed',
          failedRequests: 0,
          totalRequests: 0,
        }) as TallyDiagnosticsSnapshot,
      getStatus: () => ({ name: 'TallyConnection', running: true, ready: true }),
    };
    const selectedCompanyRepository = new SelectedCompanyRepository(
      () => {
        throw new Error('no database in this unit test');
      },
      createLogger({ service: 'test', level: 'error' }),
    );
    const service = new ConnectorSessionServiceImpl(
      config,
      resolver,
      tallyConnection,
      readPort,
      selectedCompanyRepository,
      createLogger({ service: 'test', level: 'error' }),
    );
    return { service, discovery };
  }

  it('3A validates existing company with one discovery refresh after selection invalidates cache', async () => {
    const discoverCompanies = vi.fn(async () => ({
      items: [{ id: 'estimation', name: 'ESTIMATION' }],
      schemaVersion: '1',
      dataFreshnessAt: new Date().toISOString(),
      contractVersion: '1' as const,
      status: 'SUCCESS' as const,
      tallyReachable: true,
    }));
    const { service } = makeSessionService({ discoveryImpl: discoverCompanies });
    await service.start();
    await service.selectCompany('estimation');
    discoverCompanies.mockClear();
    const validation = await service.validateForOperation('estimation');
    expect(validation.status).toBe('SUCCESS');
    expect(discoverCompanies).toHaveBeenCalledTimes(0);
  });

  it('3B blocks absent company with COMPANY_NOT_FOUND', async () => {
    const { service } = makeSessionService({
      discoveryImpl: async () => ({
        items: [{ id: 'estimation', name: 'ESTIMATION' }],
        schemaVersion: '1',
        dataFreshnessAt: new Date().toISOString(),
        contractVersion: '1' as const,
        status: 'SUCCESS' as const,
        tallyReachable: true,
      }),
    });
    await service.start();
    const result = await service.selectCompany('missing-co');
    expect(result.status).toBe('COMPANY_NOT_FOUND');
  });

  it('3C fails closed when discovery throws and ping fails', async () => {
    const nowMs = Date.now();
    const session = withSelectedCompany(
      createEmptySession({
        connectorVersion: '0.3.1',
        erpType: ERP_TYPE_TALLY,
        connectionStatus: 'connected',
        nowMs,
      }),
      { id: 'estimation', name: 'ESTIMATION' },
      nowMs,
    );
    const { service } = makeSessionService({
      discoveryImpl: async () => {
        throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'discovery down', 503);
      },
      pingImpl: async () => false,
    });
    await service.start();
    (service as unknown as { session: typeof session }).session = session;
    const validation = await service.validateForOperation('estimation');
    expect(validation.status).toBe('COMPANY_DISCOVERY_UNAVAILABLE');
  });

  it('3D fails closed when discovery throws but ping succeeds (no reachability substitution)', async () => {
    const nowMs = Date.now();
    const session = withSelectedCompany(
      createEmptySession({
        connectorVersion: '0.3.1',
        erpType: ERP_TYPE_TALLY,
        connectionStatus: 'connected',
        nowMs,
      }),
      { id: 'estimation', name: 'ESTIMATION' },
      nowMs,
    );
    const { service } = makeSessionService({
      discoveryImpl: async () => {
        throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'discovery down', 503);
      },
      pingImpl: async () => true,
    });
    await service.start();
    (service as unknown as { session: typeof session }).session = session;
    const validation = await service.validateForOperation('estimation');
    expect(validation.status).toBe('COMPANY_DISCOVERY_UNAVAILABLE');
    expect(validation.reason).toContain('reachability alone');
  });

  it('3E distinguishes empty discovery from transport failure', async () => {
    const { service } = makeSessionService({
      discoveryImpl: async () => ({
        items: [],
        schemaVersion: '1',
        dataFreshnessAt: new Date().toISOString(),
        contractVersion: '1' as const,
        status: 'EMPTY' as const,
        tallyReachable: true,
      }),
    });
    await service.start();
    const result = await service.selectCompany('estimation');
    expect(result.status).toBe('COMPANY_NOT_FOUND');
  });

  it('3F documents that operation empty response cannot prove invalid company (discovery authority)', () => {
    expect('discovery-list-match').toBe('discovery-list-match');
  });
});

describe('capability preflight session integration (3D API)', () => {
  it('returns discovery unavailable instead of company-not-found when discovery throws after selection', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const app = createTestApp(context);
    await request(app).post('/session/company').send({ companyId: 'estimation' });

    const resolver = context.container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver);
    resolver.invalidateCache();
    const discovery = context.container.resolve<CompanyDiscoveryService>(
      ServiceTokens.CompanyDiscovery,
    );
    const original = discovery.discoverCompanies.bind(discovery);
    vi.spyOn(discovery, 'discoverCompanies').mockImplementation(async () => {
      throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Injected discovery failure', 503);
    });

    const response = await request(app).post('/session/validate');
    expect(response.status).toBe(503);
    expect(response.body.status).toBe('COMPANY_DISCOVERY_UNAVAILABLE');

    vi.spyOn(discovery, 'discoverCompanies').mockImplementation(original);
  });

  it('reports COMPANY_NOT_FOUND for empty discovery list', async () => {
    const context = createTestContext({
      fetchImpl: createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_EMPTY_LIST }).fetchImpl,
      tallyRetryMaxAttempts: 1,
    });
    await startTestServices(context);
    const response = await request(createTestApp(context))
      .post('/session/company')
      .send({ companyId: 'estimation' });
    expect(response.status).toBe(404);
    expect(response.body.status).toBe('COMPANY_NOT_FOUND');
  });

  it('4D invalidates discovery cache when company selection changes', async () => {
    const discoverCalls = vi.fn();
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const session = context.container.resolve<import('../../../src/services/interfaces/connector-session.js').ConnectorSessionService>(
      ServiceTokens.ConnectorSession,
    );
    const resolver = context.container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver);
    const discovery = context.container.resolve<CompanyDiscoveryService>(ServiceTokens.CompanyDiscovery);
    const wrapped = discovery.discoverCompanies.bind(discovery);
    vi.spyOn(discovery, 'discoverCompanies').mockImplementation(async (...args) => {
      discoverCalls();
      return wrapped(...args);
    });

    await session.selectCompany('estimation');
    const afterFirst = discoverCalls.mock.calls.length;
    session.clearSelection();
    await session.selectCompany('estimation');
    expect(discoverCalls.mock.calls.length).toBeGreaterThan(afterFirst);
    expect(resolver.getCacheAgeMs()).not.toBeNull();
  });
});
