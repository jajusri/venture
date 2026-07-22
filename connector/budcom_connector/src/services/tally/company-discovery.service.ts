import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { ServiceStatus } from '../../core/types.js';
import type { ErpReadPort } from '../../erp/ports/erp-read-port.js';
import type {
  CompanyDiscoveryService,
  CompanyListResult,
} from '../interfaces/company-discovery.js';

export class CompanyDiscoveryServiceImpl implements CompanyDiscoveryService {
  private running = false;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly readPort: ErpReadPort,
    private readonly logger: Logger,
  ) {}

  async start(): Promise<void> {
    this.running = true;
    this.logger.info('Company discovery service started');
  }

  async stop(): Promise<void> {
    this.running = false;
    this.logger.info('Company discovery service stopped');
  }

  isRunning(): boolean {
    return this.running;
  }

  async discoverCompanies(): Promise<CompanyListResult> {
    if (!this.running) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Company discovery service is not running',
        503,
      );
    }

    const discovery = await this.readPort.discoverCompanies();

    if (discovery.status === 'DENIED') {
      throw new AppError(
        ErrorCodes.VALIDATION_ERROR,
        discovery.reason ?? 'Company discovery denied by policy',
        403,
        { status: discovery.status },
      );
    }

    if (discovery.status === 'UNAVAILABLE' || discovery.status === 'TIMEOUT') {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        discovery.reason ?? 'Tally is unavailable for company discovery',
        503,
        { status: discovery.status, tallyReachable: discovery.tallyReachable },
      );
    }

    if (discovery.status === 'MALFORMED') {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        discovery.reason ?? 'Company discovery response was malformed',
        503,
        {
          status: discovery.status,
          dataQuality: discovery.dataQuality,
          tallyReachable: discovery.tallyReachable,
        },
      );
    }

    this.logger.info('Company discovery completed', {
      status: discovery.status,
      companyCount: discovery.items.length,
      tallyReachable: discovery.tallyReachable,
    });

    return {
      items: [...discovery.items],
      schemaVersion: this.config.schemaVersion,
      dataFreshnessAt: new Date().toISOString(),
      contractVersion: '1',
      status: discovery.status,
      tallyReachable: discovery.tallyReachable,
      dataQuality: discovery.dataQuality,
      reason: discovery.reason,
    };
  }

  getStatus(): ServiceStatus {
    return {
      name: 'CompanyDiscovery',
      running: this.running,
      ready: this.running && this.readPort.isReady(),
      message: this.running ? 'Discovery ready' : 'Stopped',
    };
  }
}
