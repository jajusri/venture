import type { ConnectorConfig } from '../config/defaults.js';
import { loadConfig } from '../config/index.js';
import { ServiceContainer } from '../core/container.js';
import type { ServiceLifecycle } from '../core/types.js';
import { ServiceTokens, type ServiceToken } from '../core/tokens.js';
import type { Logger } from '../infrastructure/logging/logger.js';
import { createLogger } from '../infrastructure/logging/logger.js';
import { HealthService } from '../services/health/health-service.js';
import type { ApiServerService } from '../services/interfaces/api-server.js';
import type { CompanyDiscoveryService } from '../services/interfaces/company-discovery.js';
import type { LicensingService } from '../services/interfaces/licensing.js';
import type { LocalDatabaseService } from '../services/interfaces/local-database.js';
import type { SchedulerService } from '../services/interfaces/scheduler.js';
import type { SyncEngineService } from '../services/interfaces/sync-engine.js';
import type { TallyConnectionService } from '../services/interfaces/tally-connection.js';
import type { TallyDiagnosticsService } from '../services/interfaces/tally-diagnostics.js';
import type { XmlImportService } from '../services/interfaces/xml-import.js';
import { ApiServerStub } from '../services/placeholders/api-server.stub.js';
import { LicensingStub } from '../services/placeholders/licensing.stub.js';
import { LocalDatabaseStub } from '../services/placeholders/local-database.stub.js';
import { SchedulerStub } from '../services/placeholders/scheduler.stub.js';
import { LedgerSyncServiceImpl } from '../services/ledger/ledger-sync.service.js';
import type { LedgerSyncService } from '../services/ledger/ledger-sync.service.js';
import { CompanyDiscoveryServiceImpl } from '../services/tally/company-discovery.service.js';
import { TallyConnectionServiceImpl } from '../services/tally/tally-connection.service.js';
import { TallyDiagnosticsServiceImpl } from '../services/tally/tally-diagnostics.service.js';
import { OfflineXmlIngestionService } from '../ingestion/offline-xml-ingestion.service.js';
import { CompanyResolver } from '../services/extraction/company-resolver.js';
import { MasterDataServiceImpl } from '../services/extraction/master-data.service.js';
import { ConnectorSessionServiceImpl } from '../services/session/connector-session.service.js';
import { createTallyModule } from '../tally/tally-module.js';
import { TallyXmlResponseParser } from '../tally/xml/response-parser.js';
import type { MasterDataService } from '../services/extraction/master-data.service.js';
import type { ConnectorSessionService } from '../services/interfaces/connector-session.js';

export interface ApplicationContext {
  readonly container: ServiceContainer;
  readonly config: ConnectorConfig;
  readonly logger: Logger;
}

export interface RegisterServicesOptions extends Partial<ConnectorConfig> {
  readonly fetchImpl?: typeof fetch;
}

export function registerServices(options: RegisterServicesOptions = {}): ApplicationContext {
  const { fetchImpl, ...configOverrides } = options;
  const container = new ServiceContainer();
  const config = loadConfig(configOverrides);
  const logger = createLogger({ service: 'budcom-connector', level: config.logLevel });

  container.registerSingleton(ServiceTokens.Config, config);
  container.registerSingleton(ServiceTokens.Logger, logger);

  const tallyModule = createTallyModule({ config, logger, fetchImpl });
  container.registerSingleton(ServiceTokens.ErpReadPort, tallyModule.readPort);

  container.registerFactory(
    ServiceTokens.TallyConnection,
    () =>
      new TallyConnectionServiceImpl(
        tallyModule.connectionManager,
        logger.child({ service: 'TallyConnection' }),
      ),
  );
  // Offline Budcom XML file ingestion. Deliberately receives ONLY a pure XML
  // parser — never the read gateway, connection manager, or transport — so it
  // cannot reach live Tally. Uses its own parser instance (not the adapter's).
  container.registerFactory(
    ServiceTokens.XmlImport,
    () =>
      new OfflineXmlIngestionService(
        new TallyXmlResponseParser(),
        logger.child({ service: 'OfflineXmlIngestion' }),
      ),
  );
  container.registerFactory(
    ServiceTokens.CompanyDiscovery,
    () =>
      new CompanyDiscoveryServiceImpl(
        config,
        tallyModule.readPort,
        logger.child({ service: 'CompanyDiscovery' }),
      ),
  );
  container.registerFactory(ServiceTokens.CompanyResolver, () => {
    const discovery = container.resolve<CompanyDiscoveryService>(ServiceTokens.CompanyDiscovery);
    return new CompanyResolver(discovery);
  });
  container.registerFactory(
    ServiceTokens.ConnectorSession,
    () =>
      new ConnectorSessionServiceImpl(
        config,
        container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver),
        container.resolve<TallyConnectionService>(ServiceTokens.TallyConnection),
        tallyModule.readPort,
        logger.child({ service: 'ConnectorSession' }),
      ),
  );
  container.registerFactory(
    ServiceTokens.MasterData,
    () =>
      new MasterDataServiceImpl(
        config,
        tallyModule.readPort,
        container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver),
        container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession),
        logger.child({ service: 'MasterData' }),
      ),
  );
  container.registerFactory(
    ServiceTokens.TallyDiagnostics,
    () => new TallyDiagnosticsServiceImpl(tallyModule.connectionManager),
  );

  container.registerFactory(ServiceTokens.LedgerSync, () => {
    const service = new LedgerSyncServiceImpl(
      config,
      tallyModule.readPort,
      container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver),
      container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession),
      logger.child({ service: 'LedgerSync' }),
    );
    return service;
  });
  container.registerFactory(ServiceTokens.SyncEngine, () =>
    container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync),
  );
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
        companyDiscovery: container.resolve<CompanyDiscoveryService>(ServiceTokens.CompanyDiscovery),
        connectorSession: container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession),
        masterData: container.resolve<MasterDataService>(ServiceTokens.MasterData),
        localDatabase: container.resolve<LocalDatabaseService>(ServiceTokens.LocalDatabase),
        apiServer: container.resolve<ApiServerService>(ServiceTokens.ApiServer),
        licensing: container.resolve<LicensingService>(ServiceTokens.Licensing),
        scheduler: container.resolve<SchedulerService>(ServiceTokens.Scheduler),
      }),
  );

  container.registerFactory(
    ServiceTokens.ApiServer,
    () =>
      new ApiServerStub(config, logger, () => ({
        healthService: container.resolve<HealthService>(ServiceTokens.HealthService),
        companyDiscovery: container.resolve<CompanyDiscoveryService>(ServiceTokens.CompanyDiscovery),
        connectorSession: container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession),
        masterData: container.resolve<MasterDataService>(ServiceTokens.MasterData),
        ledgerSync: container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync),
        tallyDiagnostics: container.resolve<TallyDiagnosticsService>(ServiceTokens.TallyDiagnostics),
      })),
  );

  return { container, config, logger };
}

const STARTUP_ORDER: ServiceToken[] = [
  ServiceTokens.LocalDatabase,
  ServiceTokens.TallyConnection,
  ServiceTokens.XmlImport,
  ServiceTokens.CompanyDiscovery,
  ServiceTokens.ConnectorSession,
  ServiceTokens.MasterData,
  ServiceTokens.LedgerSync,
  ServiceTokens.SyncEngine,
  ServiceTokens.Licensing,
  ServiceTokens.Scheduler,
  ServiceTokens.ApiServer,
];

const SHUTDOWN_ORDER: ServiceToken[] = [
  ServiceTokens.ApiServer,
  ServiceTokens.Scheduler,
  ServiceTokens.SyncEngine,
  ServiceTokens.LedgerSync,
  ServiceTokens.MasterData,
  ServiceTokens.ConnectorSession,
  ServiceTokens.CompanyDiscovery,
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
