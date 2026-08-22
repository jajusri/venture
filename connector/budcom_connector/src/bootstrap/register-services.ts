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
import { TrustedDeviceRepository } from '../services/device/trusted-device-repository.js';
import { ConnectorIdentityRepository } from '../services/identity/connector-identity-repository.js';
import { PairingSessionRepository } from '../services/pairing/pairing-session-repository.js';
import { PairingDeviceCredentialRepository } from '../services/pairing/pairing-device-credential-repository.js';
import { ConnectorTransportIdentityService } from '../services/transport/connector-transport-identity.js';
import { MdnsAdvertiser } from '../services/discovery/mdns-advertiser.js';
import { createBonjourMdnsPublisherFactory } from '../services/discovery/bonjour-mdns-publisher.js';
import { SqliteStorageService } from '../storage/sqlite/storage-service.js';
import { AdaptiveSchedulerService } from '../services/scheduler/adaptive-scheduler.service.js';
import { SchedulerStateRepository } from '../storage/sqlite/scheduler-state-repository.js';
import { LedgerSyncServiceImpl } from '../services/ledger/ledger-sync.service.js';
import type { LedgerSyncService } from '../services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../services/stock-item/stock-item-sync.service.js';
import type { StockItemSyncService } from '../services/stock-item/stock-item-sync.service.js';
import { CompanyDiscoveryServiceImpl } from '../services/tally/company-discovery.service.js';
import { TallyConnectionServiceImpl } from '../services/tally/tally-connection.service.js';
import { TallyDiagnosticsServiceImpl } from '../services/tally/tally-diagnostics.service.js';
import { OfflineXmlIngestionService } from '../ingestion/offline-xml-ingestion.service.js';
import { CompanyResolver } from '../services/extraction/company-resolver.js';
import { MasterDataServiceImpl } from '../services/extraction/master-data.service.js';
import { ConnectorSessionServiceImpl } from '../services/session/connector-session.service.js';
import { SelectedCompanyRepository } from '../services/session/selected-company-repository.js';
import { createTallyModule } from '../tally/tally-module.js';
import type { MasterDataService } from '../services/extraction/master-data.service.js';
import type { ConnectorSessionService } from '../services/interfaces/connector-session.js';
import { voucherFoundation } from '../services/voucher/voucher-foundation.js';
import { VoucherExtractionService } from '../services/voucher/voucher-extraction.service.js';
import type { VoucherReadPort } from '../erp/ports/vouchers.js';
import { VoucherSynchronizationService } from '../services/voucher/voucher-snapshot-sync.service.js';
import type { VoucherSnapshotSyncService } from '../services/voucher/voucher-application.interface.js';
import { VoucherApplicationServiceImpl } from '../services/voucher/voucher-application.service.js';
import type { VoucherApplicationService } from '../services/voucher/voucher-application.interface.js';
import { TallyVoucherExtractor } from '../tally/voucher/voucher-extractor.js';
import { VoucherSyncFailureAuditor } from '../services/voucher/voucher-sync-failure-audit.js';
import {
  TALLY_REQUEST_AUDIT_MAX_BYTES_DEFAULT,
  TALLY_REQUEST_AUDIT_MAX_FILES_DEFAULT,
} from '../config/defaults.js';
import { validateStartupConfiguration } from './startup-validation.js';

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
  container.registerSingleton(ServiceTokens.VoucherFoundation, voucherFoundation);

  const tallyModule = createTallyModule({ config, logger, fetchImpl });
  container.registerSingleton(ServiceTokens.ErpReadPort, tallyModule.readPort);
  container.registerSingleton(ServiceTokens.VoucherParser, tallyModule.voucherParser);
  container.registerSingleton(ServiceTokens.VoucherMapper, tallyModule.voucherMapper);
  container.registerSingleton(ServiceTokens.TallyVoucherExtractor, tallyModule.voucherExtractor);
  container.registerFactory(
    ServiceTokens.VoucherReadPort,
    () => container.resolve<TallyVoucherExtractor>(ServiceTokens.TallyVoucherExtractor),
  );
  container.registerFactory(
    ServiceTokens.VoucherExtraction,
    () => new VoucherExtractionService(
      container.resolve<VoucherReadPort>(ServiceTokens.VoucherReadPort),
    ),
  );
  container.registerFactory(
    ServiceTokens.VoucherApplication,
    () => new VoucherApplicationServiceImpl(
      () => container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase)
        .getBundle().voucherRepository,
    ),
  );

  container.registerFactory(
    ServiceTokens.TallyConnection,
    () =>
      new TallyConnectionServiceImpl(
        tallyModule.connectionManager,
        logger.child({ service: 'TallyConnection' }),
      ),
  );
  // Offline Budcom XML file ingestion. Deliberately receives ONLY the unified
  // inbound envelope boundary — never the read gateway, connection manager, or
  // transport — so it cannot reach live Tally.
  container.registerFactory(ServiceTokens.XmlImport, () => {
    const importLogger = logger.child({ service: 'OfflineXmlIngestion' });
    const storage = container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
    if (storage.isRunning()) {
      return OfflineXmlIngestionService.withRepository(
        importLogger,
        storage.getBundle().xmlImportAttemptRepository,
        config.connectorVersion ?? '0.4.0',
      );
    }
    return new OfflineXmlIngestionService(importLogger, {
      connectorVersion: config.connectorVersion ?? '0.4.0',
    });
  });
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
  container.registerFactory(ServiceTokens.VoucherSynchronization, () => {
    const storage = container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
    const companyResolver = container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver);
    const failureAuditor = new VoucherSyncFailureAuditor({
      auditPath: config.voucherSyncFailureAuditPath,
      enabled: config.voucherSyncFailureAuditEnabled,
      maxBytes: TALLY_REQUEST_AUDIT_MAX_BYTES_DEFAULT,
      maxFiles: TALLY_REQUEST_AUDIT_MAX_FILES_DEFAULT,
      logger: logger.child({ service: 'VoucherSyncFailureAudit' }),
    });
    return new VoucherSynchronizationService(
      container.resolve<VoucherExtractionService>(ServiceTokens.VoucherExtraction),
      storage.getBundle().voucherRepository,
      undefined,
      logger.child({ service: 'VoucherSync' }),
      undefined,
      (companyId) => companyResolver.resolveName(companyId),
      failureAuditor,
    );
  });
  container.registerFactory(
    ServiceTokens.VoucherSync,
    () => container.resolve<VoucherSnapshotSyncService>(ServiceTokens.VoucherSynchronization),
  );
  container.registerFactory(
    ServiceTokens.ConnectorSession,
    () =>
      new ConnectorSessionServiceImpl(
        config,
        container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver),
        container.resolve<TallyConnectionService>(ServiceTokens.TallyConnection),
        tallyModule.readPort,
        new SelectedCompanyRepository(
          () => container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase).getBundle().database,
          logger.child({ service: 'SelectedCompanyRepository' }),
        ),
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

  container.registerFactory(
    ServiceTokens.LocalDatabase,
    () => new SqliteStorageService(config, logger.child({ service: 'LocalDatabase' })),
  );
  container.registerFactory(
    ServiceTokens.ConnectorIdentity,
    () =>
      new ConnectorIdentityRepository(
        () => container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase).getBundle().database,
      ),
  );
  container.registerFactory(
    ServiceTokens.TrustedDevices,
    () =>
      new TrustedDeviceRepository(
        container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase).getBundle().database,
      ),
  );
  container.registerFactory(
    ServiceTokens.PairingSessions,
    () =>
      new PairingSessionRepository(
        container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase).getBundle().database,
      ),
  );
  container.registerFactory(
    ServiceTokens.PairingCredentials,
    () =>
      new PairingDeviceCredentialRepository(
        container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase).getBundle().database,
      ),
  );
  container.registerFactory(
    ServiceTokens.TransportIdentity,
    () => new ConnectorTransportIdentityService(config.transportIdentityDir, logger.child({ service: 'TransportIdentity' })),
  );
  container.registerFactory(
    ServiceTokens.MdnsAdvertiser,
    () =>
      new MdnsAdvertiser({
        identity: container.resolve<ConnectorIdentityRepository>(ServiceTokens.ConnectorIdentity),
        getPort: () => config.port,
        getApiVersion: () => config.schemaVersion,
        getAuthRequired: () => config.networkExposure === 'lan' && config.requireDeviceAuthForLan,
        getSecureTransportPort: () => (config.secureTransportEnabled ? config.secureTransportPort : null),
        logger: logger.child({ service: 'MdnsAdvertiser' }),
        createPublisher: createBonjourMdnsPublisherFactory(),
      }),
  );
  container.registerFactory(ServiceTokens.LedgerSync, () => {
    const storage = container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
    const service = new LedgerSyncServiceImpl(
      config,
      tallyModule.readPort,
      container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver),
      container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession),
      logger.child({ service: 'LedgerSync' }),
      storage,
    );
    return service;
  });
  container.registerFactory(ServiceTokens.StockItemSync, () => {
    const storage = container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
    const service = new StockItemSyncServiceImpl(
      config,
      tallyModule.readPort,
      container.resolve<CompanyResolver>(ServiceTokens.CompanyResolver),
      container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession),
      logger.child({ service: 'StockItemSync' }),
      storage,
    );
    return service;
  });
  container.registerFactory(ServiceTokens.SyncEngine, () =>
    container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync),
  );
  container.registerFactory(ServiceTokens.Licensing, () => new LicensingStub(logger));
  container.registerFactory(
    ServiceTokens.SchedulerState,
    () =>
      new SchedulerStateRepository(
        () => container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase).getBundle().database,
      ),
  );
  container.registerFactory(
    ServiceTokens.Scheduler,
    () =>
      new AdaptiveSchedulerService(
        container.resolve<SchedulerStateRepository>(ServiceTokens.SchedulerState),
        container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession),
        container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync),
        container.resolve<StockItemSyncService>(ServiceTokens.StockItemSync),
        logger.child({ service: 'Scheduler' }),
      ),
  );

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
        connectorIdentity: container.resolve<ConnectorIdentityRepository>(ServiceTokens.ConnectorIdentity),
        mdnsAdvertiser: container.resolve<MdnsAdvertiser>(ServiceTokens.MdnsAdvertiser),
        voucherSynchronizationComposed: () =>
          container.has(ServiceTokens.VoucherSynchronization),
        voucherApplicationComposed: () => container.has(ServiceTokens.VoucherApplication),
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
        stockItemSync: container.resolve<StockItemSyncService>(ServiceTokens.StockItemSync),
        scheduler: container.resolve<AdaptiveSchedulerService>(ServiceTokens.Scheduler),
        tallyDiagnostics: container.resolve<TallyDiagnosticsService>(ServiceTokens.TallyDiagnostics),
        voucherApplication: container.resolve<VoucherApplicationService>(
          ServiceTokens.VoucherApplication,
        ),
        voucherSynchronization: container.resolve<VoucherSnapshotSyncService>(
          ServiceTokens.VoucherSynchronization,
        ),
        trustedDevices: container.resolve<TrustedDeviceRepository>(ServiceTokens.TrustedDevices),
        connectorIdentity: container.resolve<ConnectorIdentityRepository>(ServiceTokens.ConnectorIdentity),
        pairingSessions: container.resolve<PairingSessionRepository>(ServiceTokens.PairingSessions),
        pairingCredentials: container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials),
      }), container.resolve<ConnectorTransportIdentityService>(ServiceTokens.TransportIdentity)),
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
  ServiceTokens.StockItemSync,
  ServiceTokens.SyncEngine,
  ServiceTokens.Licensing,
  ServiceTokens.Scheduler,
  ServiceTokens.ApiServer,
  ServiceTokens.MdnsAdvertiser,
];

const INITIALIZATION_ORDER: ServiceToken[] = [
  ServiceTokens.VoucherExtraction,
];

const SHUTDOWN_ORDER: ServiceToken[] = [
  ServiceTokens.MdnsAdvertiser,
  ServiceTokens.ApiServer,
  ServiceTokens.Scheduler,
  ServiceTokens.SyncEngine,
  ServiceTokens.LedgerSync,
  ServiceTokens.StockItemSync,
  ServiceTokens.MasterData,
  ServiceTokens.ConnectorSession,
  ServiceTokens.CompanyDiscovery,
  ServiceTokens.XmlImport,
  ServiceTokens.TallyConnection,
  ServiceTokens.Licensing,
  ServiceTokens.LocalDatabase,
];

export async function startApplication(context: ApplicationContext): Promise<void> {
  validateStartupConfiguration(context.config);
  context.logger.info('startup.validation.succeeded', {
    event: 'startup.validation.succeeded',
    bindHost: context.config.host,
    bindPort: context.config.port,
  });

  for (const token of INITIALIZATION_ORDER) {
    context.container.resolve(token);
    context.logger.info('Service initialized', { service: token });
  }

  const started: Array<{ token: ServiceToken; service: ServiceLifecycle }> = [];
  try {
    for (const token of STARTUP_ORDER) {
      const service = context.container.resolve<ServiceLifecycle>(token);
      await service.start();
      started.push({ token, service });
      context.logger.info('Service started', { service: token });
    }
  } catch (error) {
    context.logger.error('application.startup.failed', {
      event: 'application.startup.failed',
      code: 'STARTUP_FAILED',
    });
    for (const { token, service } of started.reverse()) {
      try {
        await service.stop();
        context.logger.info('Service stopped after startup failure', { service: token });
      } catch {
        context.logger.error('application.startup.cleanup.failed', {
          event: 'application.startup.cleanup.failed',
          service: token,
        });
      }
    }
    throw error;
  }

  context.logger.info('application.startup.completed', {
    event: 'application.startup.completed',
    bindHost: context.config.host,
    bindPort: context.config.port,
    voucherSynchronizationComposed: context.container.has(ServiceTokens.VoucherSynchronization),
    voucherApplicationComposed: context.container.has(ServiceTokens.VoucherApplication),
    startupCorrelationId: context.config.startupCorrelationId,
  });
}

export async function stopApplication(context: ApplicationContext): Promise<void> {
  for (const token of SHUTDOWN_ORDER) {
    if (!context.container.has(token)) continue;
    const service = context.container.resolve<ServiceLifecycle>(token);
    await service.stop();
    context.logger.info('Service stopped', { service: token });
  }
}
