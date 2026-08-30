import { describe, expect, it, vi } from 'vitest';

import { ConnectorSessionServiceImpl } from '../../../src/services/session/connector-session.service.js';
import { SelectedCompanyRepository } from '../../../src/services/session/selected-company-repository.js';
import { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { createTestConnectorConfig } from '../../helpers/sqlite-test-storage.js';
import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import type { CompanyDiscoveryService } from '../../../src/services/interfaces/company-discovery.js';
import type { TallyConnectionService } from '../../../src/services/interfaces/tally-connection.js';
import type { TallyDiagnosticsSnapshot } from '../../../src/tally/core/types.js';

/**
 * Covers the parked "stale persisted company shown as ready" finding: getStatus() must not
 * report a company as a trustworthy selection when a fresh discovery snapshot is already cached
 * and confidently does not contain it, but must stay permissive (unchanged behavior) whenever
 * there's no cached evidence either way -- this is a status-honesty fix, not a new validation
 * mechanism (the real fail-closed behavior for actual operations already existed and is
 * unaffected: see session-validator.ts's SESSION_EXPIRED path).
 */
function makeSessionService(discoveryImpl: CompanyDiscoveryService['discoverCompanies']) {
  const config = createTestConnectorConfig('/tmp/session-status-honesty');
  const discovery: CompanyDiscoveryService = {
    start: vi.fn(),
    stop: vi.fn(),
    isRunning: () => true,
    discoverCompanies: discoveryImpl,
    getStatus: () => ({ name: 'CompanyDiscovery', running: true, ready: true }),
  };
  const resolver = new CompanyResolver(discovery);
  const readPort: ErpReadPort = {
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
  };
  const tallyConnection: TallyConnectionService = {
    start: vi.fn(),
    stop: vi.fn(),
    isRunning: () => true,
    ping: async () => true,
    getDiagnostics: () =>
      ({ state: 'connected', circuitState: 'closed', failedRequests: 0, totalRequests: 0 }) as TallyDiagnosticsSnapshot,
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
  return { service, resolver };
}

function discoveryResult(items: ReadonlyArray<{ id: string; name: string }>) {
  return async () => ({
    items,
    schemaVersion: '1',
    dataFreshnessAt: new Date().toISOString(),
    contractVersion: '1' as const,
    status: 'SUCCESS' as const,
    tallyReachable: true,
  });
}

describe('ConnectorSessionServiceImpl.getStatus() — stale-company honesty', () => {
  it('reports "No company selected" when nothing is selected', async () => {
    const { service } = makeSessionService(discoveryResult([]));
    await service.start();
    expect(service.getStatus()).toEqual({
      name: 'ConnectorSession',
      running: true,
      ready: true,
      message: 'No company selected',
    });
  });

  it('reports "Stopped" and ready:false when not running', () => {
    const { service } = makeSessionService(discoveryResult([]));
    expect(service.getStatus()).toEqual({
      name: 'ConnectorSession',
      running: false,
      ready: false,
      message: 'Stopped',
    });
  });

  it('stays permissive (ready:true) when a company is selected but no discovery snapshot is cached yet — no evidence either way', async () => {
    const discoverCompanies = vi.fn(discoveryResult([{ id: 'estimation', name: 'ESTIMATION' }]));
    const { service, resolver } = makeSessionService(discoverCompanies);
    await service.start();
    await service.selectCompany('estimation');
    // selectCompany() itself triggers a discovery call as part of validation, which populates
    // the cache -- invalidate it to reproduce the genuinely-no-evidence-yet case getStatus()
    // must handle permissively (e.g. immediately after a Desktop/Connector restart, before the
    // first /companies call or sync has run this session).
    resolver.invalidateCache();

    const status = service.getStatus();
    expect(status.ready).toBe(true);
    expect(status.message).toBe('Selected company: ESTIMATION');
  });

  it('stays ready:true when the selected company IS present in a cached discovery snapshot', async () => {
    const discoverCompanies = vi.fn(
      discoveryResult([{ id: 'estimation', name: 'ESTIMATION' }, { id: 'jaju-sanitations', name: 'Jaju Sanitations' }]),
    );
    const { service } = makeSessionService(discoverCompanies);
    await service.start();
    await service.selectCompany('estimation');

    const status = service.getStatus();
    expect(status).toEqual({
      name: 'ConnectorSession',
      running: true,
      ready: true,
      message: 'Selected company: ESTIMATION',
    });
  });

  it('reports ready:false with a clear reselect message when the selected company is confidently absent from a fresh discovery snapshot', async () => {
    const discoverCompanies = vi.fn(discoveryResult([{ id: 'estimation', name: 'ESTIMATION' }]));
    const { service, resolver } = makeSessionService(discoverCompanies);
    await service.start();
    await service.selectCompany('estimation');

    // Simulate the company having disappeared from Tally (renamed/deleted) and a fresh
    // discovery having already run and NOT found it -- exactly the parked-finding scenario.
    discoverCompanies.mockImplementation(discoveryResult([{ id: 'a-different-company', name: 'A Different Company' }]));
    resolver.invalidateCache();
    await resolver.getDiscoverySnapshot();

    const status = service.getStatus();
    expect(status.running).toBe(true);
    expect(status.ready).toBe(false);
    expect(status.message).toContain('no longer found in Tally');
    expect(status.message).toContain('ESTIMATION');
  });

  it('does not affect the real fail-closed validation path for actual operations (unchanged behavior)', async () => {
    const discoverCompanies = vi.fn(discoveryResult([{ id: 'estimation', name: 'ESTIMATION' }]));
    const { service } = makeSessionService(discoverCompanies);
    await service.start();
    await service.selectCompany('estimation');

    const result = await service.validateForOperation('estimation');
    expect(result.status).toBe('SUCCESS');
  });
});
