import { describe, expect, it } from 'vitest';

import { ServiceTokens } from '../../src/core/tokens.js';
import type { HealthService } from '../../src/services/health/health-service.js';
import { createTallyMockFetch } from '../helpers/mock-fetch.js';
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
    ];

    for (const token of tokens) {
      expect(context.container.has(token)).toBe(true);
      expect(context.container.resolve(token)).toBeDefined();
    }
  });
});
