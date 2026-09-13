import { describe, expect, it } from 'vitest';

import { ServiceTokens } from '../../src/core/tokens.js';
import type { HealthService } from '../../src/services/health/health-service.js';
import type { CompanyDiscoveryService } from '../../src/services/interfaces/company-discovery.js';
import type { TallyDiagnosticsService } from '../../src/services/interfaces/tally-diagnostics.js';
import {
  createMockFetch,
  createTallyMockFetch,
  SAMPLE_TALLY_COMPANY_LIST_RESPONSE,
} from '../helpers/mock-fetch.js';
import { createTestContext, startTestServices } from '../helpers/test-context.js';

describe('HealthService', () => {
  it('reports degraded status when API server is not running', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: false });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    const report = await healthService.getReport();

    expect(report.readOnly).toBe(true);
    expect(report.status).toBe('unavailable');
    expect(report.tallyReachable).toBe(false);
    expect(report.services.some((service) => service.name === 'TallyConnection')).toBe(true);
  });

  it('includes startup correlation id from config in health diagnostics', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: false });
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      startupCorrelationId: 'probe-correlation-123',
    });
    await startTestServices(context);
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    const report = await healthService.getReport();

    expect(report.startupCorrelationId).toBe('probe-correlation-123');
  });

  it('exposes a stable connectorId and connectorName across repeated health reports', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: false });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    const first = await healthService.getReport();
    const second = await healthService.getReport();

    expect(first.connectorId).toBeTruthy();
    expect(first.connectorId).toBe(second.connectorId);
    expect(second.connectorName).toBeTruthy();
  });

  it('reports authenticatedLanAccessEnabled false on loopback even if the flag is set', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: false });
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      requireDeviceAuthForLan: true,
    });
    await startTestServices(context);
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    const report = await healthService.getReport();

    expect(report.networkExposure).toBe('loopback');
    expect(report.authenticatedLanAccessEnabled).toBe(false);
  });

  it('reports authenticated LAN access when secure business-route protection is active', async () => {
    const context = createTestContext({
      host: '192.168.29.34',
      lanModeAcknowledged: true,
      secureLanRouteProtectionEnabled: true,
      requireDeviceAuthForLan: false,
    });
    await startTestServices(context);
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    const report = await healthService.getReport();

    expect(report.networkExposure).toBe('lan');
    expect(report.authenticatedLanAccessEnabled).toBe(true);
    expect(report.networkExposureWarning).toMatch(/require device authentication/);
  });

  // Regression coverage for the tallyReachable diagnostic fix: it must track
  // TallyConnectionManager's own connection state (updated by every live exchange()) rather
  // than the rarely-invoked ping() fallback, which previously left it stuck false forever
  // during otherwise-healthy steady-state operation.
  it('reports tallyReachable false before any live Tally exchange has occurred', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    const report = await healthService.getReport();

    expect(report.tallyReachable).toBe(false);
  });

  it('reports tallyReachable true after a successful steady-state exchange, without requiring ping()', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const companyDiscovery = context.container.resolve<CompanyDiscoveryService>(
      ServiceTokens.CompanyDiscovery,
    );
    const tallyDiagnostics = context.container.resolve<TallyDiagnosticsService>(
      ServiceTokens.TallyDiagnostics,
    );
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    await companyDiscovery.discoverCompanies();
    const report = await healthService.getReport();

    expect(report.tallyReachable).toBe(true);
    // Proves the true value came from the normal discoverCompanies() exchange path, not the
    // narrow ping() fallback, which this test never invokes.
    expect(tallyDiagnostics.getConnectionDiagnostics().lastSuccessfulPingAt).toBeUndefined();
  });

  it('reports tallyReachable false after a proven connectivity failure', async () => {
    // Unlike createTallyMockFetch({ pingOk: false }) — which only fails ping()'s own fallback
    // request and still succeeds "List of Companies" — this fails every request unconditionally,
    // so discoverCompanies() itself genuinely fails.
    const { fetchImpl } = createMockFetch(() => ({ status: 503, body: 'Service Unavailable' }));
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const companyDiscovery = context.container.resolve<CompanyDiscoveryService>(
      ServiceTokens.CompanyDiscovery,
    );
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    await expect(companyDiscovery.discoverCompanies()).rejects.toThrow();
    const report = await healthService.getReport();

    expect(report.tallyReachable).toBe(false);
  });

  it('restores tallyReachable true after a subsequent successful exchange following a failure', async () => {
    const { fetchImpl } = createMockFetch((_call, index) =>
      index === 0
        ? { status: 503, body: 'Service Unavailable' }
        : { body: SAMPLE_TALLY_COMPANY_LIST_RESPONSE },
    );
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const companyDiscovery = context.container.resolve<CompanyDiscoveryService>(
      ServiceTokens.CompanyDiscovery,
    );
    const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);

    await expect(companyDiscovery.discoverCompanies()).rejects.toThrow();
    expect((await healthService.getReport()).tallyReachable).toBe(false);

    await companyDiscovery.discoverCompanies();
    expect((await healthService.getReport()).tallyReachable).toBe(true);
  });
});

describe('registerServices', () => {
  it('registers all core services', () => {
    const context = createTestContext();
    const tokens = [
      ServiceTokens.TallyConnection,
      ServiceTokens.SyncEngine,
      ServiceTokens.XmlImport,
      ServiceTokens.CompanyDiscovery,
      ServiceTokens.MasterData,
      ServiceTokens.TallyDiagnostics,
      ServiceTokens.LocalDatabase,
      ServiceTokens.ApiServer,
      ServiceTokens.Licensing,
      ServiceTokens.Scheduler,
      ServiceTokens.HealthService,
      ServiceTokens.ConnectorIdentity,
      ServiceTokens.MdnsAdvertiser,
    ];

    for (const token of tokens) {
      expect(context.container.has(token)).toBe(true);
      expect(context.container.resolve(token)).toBeDefined();
    }
  });
});
