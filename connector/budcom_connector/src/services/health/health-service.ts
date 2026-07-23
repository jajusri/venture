import type { ConnectorConfig } from '../../config/defaults.js';
import { isLanModePolicySatisfied, parseConnectorBindHost } from '../../config/network-binding.js';
import type { HealthReport } from '../../core/types.js';
import type { TallyConnectionService } from '../interfaces/tally-connection.js';
import type { SyncEngineService } from '../interfaces/sync-engine.js';
import type { XmlImportService } from '../interfaces/xml-import.js';
import type { ConnectorSessionService } from '../interfaces/connector-session.js';
import type { MasterDataService } from '../extraction/master-data.service.js';
import type { CompanyDiscoveryService } from '../interfaces/company-discovery.js';
import type { LocalDatabaseService } from '../interfaces/local-database.js';
import type { ApiServerService } from '../interfaces/api-server.js';
import type { LicensingService } from '../interfaces/licensing.js';
import type { SchedulerService } from '../interfaces/scheduler.js';

export interface HealthServiceDeps {
  readonly config: ConnectorConfig;
  readonly tallyConnection: TallyConnectionService;
  readonly syncEngine: SyncEngineService;
  readonly xmlImport: XmlImportService;
  readonly companyDiscovery: CompanyDiscoveryService;
  readonly connectorSession: ConnectorSessionService;
  readonly masterData: MasterDataService;
  readonly localDatabase: LocalDatabaseService;
  readonly apiServer: ApiServerService;
  readonly licensing: LicensingService;
  readonly scheduler: SchedulerService;
}

export class HealthService {
  constructor(private readonly deps: HealthServiceDeps) {}

  async getReport(): Promise<HealthReport> {
    const tallyReachable = await this.deps.tallyConnection.ping();

    const services = [
      this.deps.tallyConnection.getStatus(),
      this.deps.syncEngine.getStatus(),
      this.deps.xmlImport.getStatus(),
      this.deps.companyDiscovery.getStatus(),
      this.deps.connectorSession.getStatus(),
      this.deps.masterData.getStatus(),
      this.deps.localDatabase.getStatus(),
      this.deps.apiServer.getStatus(),
      this.deps.licensing.getStatus(),
      this.deps.scheduler.getStatus(),
    ];

    const allReady = services.every((service) => service.ready);
    const bindHost = parseConnectorBindHost(this.deps.config.host, '127.0.0.1');
    const networkPolicySatisfied = isLanModePolicySatisfied(
      bindHost,
      this.deps.config.lanModeAcknowledged,
    );

    let status: HealthReport['status'] = 'ok';
    if (!allReady || !networkPolicySatisfied) {
      status = 'degraded';
    }
    if (!this.deps.apiServer.isRunning()) {
      status = 'unavailable';
    }

    return {
      status,
      schemaVersion: this.deps.config.schemaVersion,
      connectorVersion: this.deps.config.connectorVersion,
      tallyReachable,
      readOnly: true,
      bindHost: this.deps.config.host,
      bindPort: this.deps.config.port,
      networkExposure: this.deps.config.networkExposure,
      networkExposureWarning: this.deps.config.networkExposureWarning,
      networkPolicySatisfied,
      authenticatedLanAccessEnabled: false,
      services,
    };
  }
}
