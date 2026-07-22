import type { ConnectorConfig } from '../config/defaults.js';
import type { Logger } from '../infrastructure/logging/logger.js';
import { TallyConnectionManager } from './connection/tally-connection-manager.js';
import { CompanyDiscoveryParser } from './discovery/company-discovery-parser.js';
import { TallyRequestAuditor } from './safety/tally-request-auditor.js';
import { TallyHttpTransport } from './transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from './xml/request-builder.js';
import { TallyXmlResponseParser } from './xml/response-parser.js';

export interface TallyModule {
  readonly transport: TallyHttpTransport;
  readonly connectionManager: TallyConnectionManager;
  readonly requestBuilder: TallyXmlRequestBuilder;
  readonly responseParser: TallyXmlResponseParser;
  readonly companyDiscoveryParser: CompanyDiscoveryParser;
}

export interface TallyModuleOptions {
  readonly config: ConnectorConfig;
  readonly logger: Logger;
  readonly fetchImpl?: typeof fetch;
}

export function createTallyModule(options: TallyModuleOptions): TallyModule {
  const logger = options.logger.child({ module: 'tally' });
  const transport = new TallyHttpTransport({
    config: options.config,
    logger: logger.child({ component: 'transport' }),
    fetchImpl: options.fetchImpl,
  });
  const requestBuilder = new TallyXmlRequestBuilder();
  const responseParser = new TallyXmlResponseParser();
  const companyDiscoveryParser = new CompanyDiscoveryParser(responseParser);
  const requestAuditor = new TallyRequestAuditor(
    options.config.tallyRequestAuditPath,
    options.config.tallyRequestAuditEnabled,
    logger.child({ component: 'request-audit' }),
  );
  const connectionManager = new TallyConnectionManager({
    config: options.config,
    logger: logger.child({ component: 'connection-manager' }),
    transport,
    requestBuilder,
    requestAuditor,
  });

  return {
    transport,
    connectionManager,
    requestBuilder,
    responseParser,
    companyDiscoveryParser,
  };
}
