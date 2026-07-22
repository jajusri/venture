import type { ConnectorConfig } from '../config/defaults.js';
import type { Logger } from '../infrastructure/logging/logger.js';
import type { ErpReadPort } from '../erp/ports/erp-read-port.js';
import { TallyReadAdapter } from './adapter/tally-read-adapter.js';
import { TallyConnectionManager } from './connection/tally-connection-manager.js';
import { CompanyDiscoveryParser } from './discovery/company-discovery-parser.js';
import { GroupsParser } from './groups/groups-parser.js';
import { TallyReadGateway } from './gateway/tally-read-gateway.js';
import { TallyRequestAuditor } from './safety/tally-request-auditor.js';
import { TallyHttpTransport } from './transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from './xml/request-builder.js';
import { TallyXmlResponseParser } from './xml/response-parser.js';

/**
 * Public surface of the Tally adapter composition root.
 *
 * SECURITY: the raw HTTP transport, the XML request builder, the read gateway,
 * and the XML parsers are intentionally NOT exposed here. Application code
 * (routes, business services, schedulers) may only touch the ERP-neutral
 * {@link ErpReadPort} for reads — which returns domain models, never XML. Only
 * the infra lifecycle/diagnostics services receive the connection manager, and
 * even that guards every request through the mandatory policy pipeline.
 */
export interface TallyModule {
  readonly connectionManager: TallyConnectionManager;
  readonly readPort: ErpReadPort;
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
  const groupsParser = new GroupsParser(responseParser);
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
  const readGateway = new TallyReadGateway({
    connectionManager,
    requestBuilder,
    logger: logger.child({ component: 'read-gateway' }),
  });
  const readPort = new TallyReadAdapter({
    gateway: readGateway,
    responseParser,
    companyDiscoveryParser,
    groupsParser,
    logger: logger.child({ component: 'read-adapter' }),
  });

  return {
    connectionManager,
    readPort,
  };
}
