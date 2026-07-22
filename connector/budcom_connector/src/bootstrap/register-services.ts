import type { ConnectorConfig } from '../config/defaults.js';
import { loadConfig } from '../config/index.js';
import { ServiceContainer } from '../core/container.js';
import type { ServiceLifecycle } from '../core/types.js';
import { ServiceTokens, type ServiceToken } from '../core/tokens.js';
import type { Logger } from '../infrastructure/logging/logger.js';
import { createLogger } from '../infrastructure/logging/logger.js';
import { HealthService } from '../services/health/health-service.js';
import type { ApiServerService } from '../services/interfaces/api-server.js';
import type { LicensingService } from '../services/interfaces/licensing.js';
import type { LocalDatabaseService } from '../services/interfaces/local-database.js';
import type { SchedulerService } from '../services/interfaces/scheduler.js';
import type { SyncEngineService } from '../services/interfaces/sync-engine.js';
import type { TallyConnectionService } from '../services/interfaces/tally-connection.js';
import type { XmlImportService } from '../services/interfaces/xml-import.js';
import { ApiServerStub } from '../services/placeholders/api-server.stub.js';
import { LicensingStub } from '../services/placeholders/licensing.stub.js';
import { LocalDatabaseStub } from '../services/placeholders/local-database.stub.js';
import { SchedulerStub } from '../services/placeholders/scheduler.stub.js';
import { SyncEngineStub } from '../services/placeholders/sync-engine.stub.js';
import { TallyConnectionStub } from '../services/placeholders/tally-connection.stub.js';
import { XmlImportStub } from '../services/placeholders/xml-import.stub.js';

export interface ApplicationContext {
  readonly container: ServiceContainer;
  readonly config: ConnectorConfig;
  readonly logger: Logger;
}

export function registerServices(configOverrides?: Partial<ConnectorConfig>): ApplicationContext {
  const container = new ServiceContainer();
  const config = loadConfig(configOverrides);
  const logger = createLogger({ service: 'budcom-connector', level: config.logLevel });

  container.registerSingleton(ServiceTokens.Config, config);
  container.registerSingleton(ServiceTokens.Logger, logger);

  container.registerFactory(ServiceTokens.TallyConnection, () => new TallyConnectionStub(logger));
  container.registerFactory(ServiceTokens.SyncEngine, () => new SyncEngineStub(logger));
  container.registerFactory(ServiceTokens.XmlImport, () => new XmlImportStub(logger));
  container.registerFactory(ServiceTokens.LocalDatabase, () => new LocalDatabaseStub(logger));
  container.registerFactory(ServiceTokens.Licensing, () => new LicensingStub(logger));
  container.registerFactory(ServiceTokens.Scheduler, () => new SchedulerStub(logger));

  container.registerFactory(
    ServiceTokens.HealthService,
    () =>
      new HealthService({
        config,
        tallyConnection: container.resolve<TallyConnectionService>(ServiceTokens.TallyConnection),
        syncEngine: container.resolve<SyncEngineService>(ServiceTokens.SyncEngine),
        xmlImport: container.resolve<XmlImportService>(ServiceTokens.XmlImport),
        localDatabase: container.resolve<LocalDatabaseService>(ServiceTokens.LocalDatabase),
        apiServer: container.resolve<ApiServerService>(ServiceTokens.ApiServer),
        licensing: container.resolve<LicensingService>(ServiceTokens.Licensing),
        scheduler: container.resolve<SchedulerService>(ServiceTokens.Scheduler),
      }),
  );

  container.registerFactory(
    ServiceTokens.ApiServer,
    () =>
      new ApiServerStub(config, logger, () =>
        container.resolve<HealthService>(ServiceTokens.HealthService),
      ),
  );

  return { container, config, logger };
}

const STARTUP_ORDER: ServiceToken[] = [
  ServiceTokens.LocalDatabase,
  ServiceTokens.TallyConnection,
  ServiceTokens.XmlImport,
  ServiceTokens.SyncEngine,
  ServiceTokens.Licensing,
  ServiceTokens.Scheduler,
  ServiceTokens.ApiServer,
];

const SHUTDOWN_ORDER: ServiceToken[] = [
  ServiceTokens.ApiServer,
  ServiceTokens.Scheduler,
  ServiceTokens.SyncEngine,
  ServiceTokens.XmlImport,
  ServiceTokens.TallyConnection,
  ServiceTokens.Licensing,
  ServiceTokens.LocalDatabase,
];

export async function startApplication(context: ApplicationContext): Promise<void> {
  for (const token of STARTUP_ORDER) {
    const service = context.container.resolve<ServiceLifecycle>(token);
    await service.start();
    context.logger.info('Service started', { service: token });
  }
}

export async function stopApplication(context: ApplicationContext): Promise<void> {
  for (const token of SHUTDOWN_ORDER) {
    if (!context.container.has(token)) continue;
    const service = context.container.resolve<ServiceLifecycle>(token);
    await service.stop();
    context.logger.info('Service stopped', { service: token });
  }
}
