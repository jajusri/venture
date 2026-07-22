import { createExpressApp } from '../../src/api/server.js';
import { registerServices } from '../../src/bootstrap/register-services.js';
import type { ServiceLifecycle } from '../../src/core/types.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import type { HealthService } from '../../src/services/health/health-service.js';
import type { Logger } from '../../src/infrastructure/logging/logger.js';
import type { ApplicationContext } from '../../src/bootstrap/register-services.js';

export function createTestContext(
  configOverrides: Parameters<typeof registerServices>[0] = {},
): ApplicationContext {
  return registerServices({ env: 'test', logLevel: 'error', ...configOverrides });
}

export function createTestApp(context: ApplicationContext = createTestContext()) {
  const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);
  const logger = context.container.resolve<Logger>(ServiceTokens.Logger);
  return createExpressApp({ logger, healthService });
}

export async function startTestServices(context: ApplicationContext): Promise<void> {
  const tokens = [
    ServiceTokens.LocalDatabase,
    ServiceTokens.TallyConnection,
    ServiceTokens.XmlImport,
    ServiceTokens.SyncEngine,
    ServiceTokens.Licensing,
    ServiceTokens.Scheduler,
  ] as const;

  for (const token of tokens) {
    await context.container.resolve<ServiceLifecycle>(token).start();
  }
}
