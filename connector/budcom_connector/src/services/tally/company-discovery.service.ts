import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { ServiceStatus } from '../../core/types.js';
import type { CompanyDiscoveryParser } from '../../tally/discovery/company-discovery-parser.js';
import type { TallyConnectionManager } from '../../tally/connection/tally-connection-manager.js';
import type { TallyXmlRequestBuilder } from '../../tally/xml/request-builder.js';
import type { TallyXmlResponseParser } from '../../tally/xml/response-parser.js';
import type {
  CompanyDiscoveryService,
  CompanyListResult,
} from '../interfaces/company-discovery.js';

export class CompanyDiscoveryServiceImpl implements CompanyDiscoveryService {
  private running = false;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly connectionManager: TallyConnectionManager,
    private readonly requestBuilder: TallyXmlRequestBuilder,
    private readonly responseParser: TallyXmlResponseParser,
    private readonly companyDiscoveryParser: CompanyDiscoveryParser,
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

    const xml = this.requestBuilder.buildCompanyListRequest();
    const exchange = await this.connectionManager.exchange(xml, {
      collectionId: 'List of Companies',
    });
    const document = this.responseParser.parse(exchange.rawXml);
    const discovered = this.companyDiscoveryParser.parseCompanies(document);

    return {
      items: discovered.map((company) => ({
        id: company.id,
        name: company.name,
        financialYear: company.startingFrom ?? company.booksFrom ?? '',
        baseCurrency: 'INR',
      })),
      schemaVersion: this.config.schemaVersion,
      dataFreshnessAt: new Date().toISOString(),
    };
  }

  getStatus(): ServiceStatus {
    return {
      name: 'CompanyDiscovery',
      running: this.running,
      ready: this.running && this.connectionManager.isRunning(),
      message: this.running ? 'Discovery ready' : 'Stopped',
    };
  }
}
