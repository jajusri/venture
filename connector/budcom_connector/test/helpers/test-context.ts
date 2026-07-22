import { createExpressApp } from '../../src/api/server.js';
import { registerServices } from '../../src/bootstrap/register-services.js';
import type { RegisterServicesOptions } from '../../src/bootstrap/register-services.js';
import type { ServiceLifecycle } from '../../src/core/types.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import type { CompanyDiscoveryService } from '../../src/services/interfaces/company-discovery.js';
import type { MasterDataService } from '../../src/services/extraction/master-data.service.js';
import type { HealthService } from '../../src/services/health/health-service.js';
import type { TallyDiagnosticsService } from '../../src/services/interfaces/tally-diagnostics.js';
import type { Logger } from '../../src/infrastructure/logging/logger.js';
import type { ApplicationContext } from '../../src/bootstrap/register-services.js';

export function createTestContext(configOverrides: RegisterServicesOptions = {}): ApplicationContext {
  return registerServices({
    env: 'test',
    logLevel: 'error',
    tallyMinRequestIntervalMs: 0,
    ...configOverrides,
  });
}

export function createTestApp(context: ApplicationContext = createTestContext()) {
  const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);
  const companyDiscovery = context.container.resolve<CompanyDiscoveryService>(
    ServiceTokens.CompanyDiscovery,
  );
  const masterData = context.container.resolve<MasterDataService>(ServiceTokens.MasterData);
  const tallyDiagnostics = context.container.resolve<TallyDiagnosticsService>(
    ServiceTokens.TallyDiagnostics,
  );
  const logger = context.container.resolve<Logger>(ServiceTokens.Logger);
  return createExpressApp({ logger, healthService, companyDiscovery, masterData, tallyDiagnostics });
}

export async function startTestServices(context: ApplicationContext): Promise<void> {
  const tokens = [
    ServiceTokens.LocalDatabase,
    ServiceTokens.TallyConnection,
    ServiceTokens.XmlImport,
    ServiceTokens.CompanyDiscovery,
    ServiceTokens.MasterData,
    ServiceTokens.SyncEngine,
    ServiceTokens.Licensing,
    ServiceTokens.Scheduler,
  ] as const;

  for (const token of tokens) {
    await context.container.resolve<ServiceLifecycle>(token).start();
  }
}
