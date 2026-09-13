import type { ConnectorConfig } from '../config/defaults.js';
import type { Logger } from '../infrastructure/logging/logger.js';
import type { ErpReadPort } from '../erp/ports/erp-read-port.js';
import type { VoucherReadPort } from '../erp/ports/vouchers.js';
import { TallyReadAdapter } from './adapter/tally-read-adapter.js';
import { TallyConnectionManager } from './connection/tally-connection-manager.js';
import { CompanyDiscoveryParser } from './discovery/company-discovery-parser.js';
import { GroupsParser } from './groups/groups-parser.js';
import { TallyReadGateway } from './gateway/tally-read-gateway.js';
import { TallyRequestAuditor } from './safety/tally-request-auditor.js';
import { TallyHttpTransport } from './transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from './xml/request-builder.js';
import { TallyXmlResponseParser } from './xml/response-parser.js';
import { TallyVoucherExtractor } from './voucher/voucher-extractor.js';
import { VoucherXmlMapper } from './voucher/voucher-mapper.js';
import { VoucherCollectionParser } from './voucher/voucher-parser.js';
import { VoucherLedgerEntryParser } from './voucher/voucher-ledger-parser.js';
import { VoucherInventoryEntryParser } from './voucher/voucher-inventory-parser.js';

/**
 * Public surface of the Tally adapter composition root.
 *
 * SECURITY: the raw HTTP transport, XML request builder, read gateway, and
 * shared response parser are intentionally NOT exposed here. Voucher parser
 * and mapper instances are exposed only to the application composition root.
 * Runtime consumers may only touch the ERP-neutral
 * {@link ErpReadPort} for reads — which returns domain models, never XML. Only
 * the infra lifecycle/diagnostics services receive the connection manager, and
 * even that guards every request through the mandatory policy pipeline.
 */
export interface TallyModule {
  readonly connectionManager: TallyConnectionManager;
  readonly readPort: ErpReadPort;
  readonly voucherParser: VoucherCollectionParser;
  readonly voucherMapper: VoucherXmlMapper;
  readonly voucherExtractor: TallyVoucherExtractor;
  readonly voucherReadPort: VoucherReadPort;
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
  const requestAuditor = new TallyRequestAuditor({
    auditPath: options.config.tallyRequestAuditPath,
    enabled: options.config.tallyRequestAuditEnabled,
    maxBytes: options.config.tallyRequestAuditMaxBytes,
    maxFiles: options.config.tallyRequestAuditMaxFiles,
    logger: logger.child({ component: 'request-audit' }),
  });
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
  const voucherParser = new VoucherCollectionParser(responseParser);
  const voucherMapper = new VoucherXmlMapper(voucherParser);
  const voucherLedgerParser = new VoucherLedgerEntryParser(responseParser);
  const voucherInventoryParser = new VoucherInventoryEntryParser(responseParser);
  const voucherExtractor = new TallyVoucherExtractor(
    readGateway,
    voucherParser,
    voucherMapper,
    voucherLedgerParser,
    options.config.env !== 'test',
    voucherInventoryParser,
  );

  return {
    connectionManager,
    readPort,
    voucherParser,
    voucherMapper,
    voucherExtractor,
    voucherReadPort: voucherExtractor,
  };
}
