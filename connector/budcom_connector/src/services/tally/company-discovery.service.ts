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

    const items = await this.readPort.listCompanies();
    return {
      items: [...items],
      schemaVersion: this.config.schemaVersion,
      dataFreshnessAt: new Date().toISOString(),
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
