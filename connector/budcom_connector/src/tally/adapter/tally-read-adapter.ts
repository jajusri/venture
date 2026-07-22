import type { Logger } from '../../infrastructure/logging/logger.js';
import type { ErpCompanyDiscoveryResult } from '../../erp/ports/company-discovery.js';
import { COMPANY_DISCOVERY_CONTRACT_VERSION } from '../../erp/ports/company-discovery.js';
import type {
  ExtractorDiagnostics,
  MasterDataEntityType,
  NormalizedCompanyInfo,
} from '../../extraction/core/types.js';
import {
  createCompanyInfoParser,
  createMasterDataExtractorRegistry,
  type MasterDataExtractorRegistry,
} from '../../extraction/extractors/extractor-registry.js';
import { mapCompanyInfo } from '../../extraction/parsers/entity-mappers.js';
import type { CompanyDiscoveryParser } from '../discovery/company-discovery-parser.js';
import type { TallyReadGateway } from '../gateway/tally-read-gateway.js';
import { ApprovedOperationId } from '../registry/operation-registry.js';
import type { TallyXmlResponseParser } from '../xml/response-parser.js';
import type { ErpReadPort } from '../../erp/ports/erp-read-port.js';
import { AppError } from '../../infrastructure/errors/app-error.js';
import {
  assessCompanyDiscovery,
  assessCompanyDiscoveryEnvelope,
  mapDiscoveredToErpSummary,
} from '../contracts/company-discovery-contract.js';

export interface TallyReadAdapterDeps {
  readonly gateway: TallyReadGateway;
  readonly responseParser: TallyXmlResponseParser;
  readonly companyDiscoveryParser: CompanyDiscoveryParser;
  readonly logger: Logger;
}

/**
 * Tally implementation of the ERP-neutral {@link ErpReadPort}.
 *
 * This is the ONLY place Tally XML is produced/parsed on behalf of business
 * code: the adapter calls the internal read gateway, parses responses, and maps
 * them into Budcom domain models. Raw XML and {@code ParsedXmlNode} never cross
 * this boundary.
 */
export class TallyReadAdapter implements ErpReadPort {
  private readonly gateway: TallyReadGateway;
  private readonly responseParser: TallyXmlResponseParser;
  private readonly companyDiscoveryParser: CompanyDiscoveryParser;
  private readonly extractors: MasterDataExtractorRegistry;
  private readonly companyInfoParser: ReturnType<typeof createCompanyInfoParser>;

  constructor(deps: TallyReadAdapterDeps) {
    this.gateway = deps.gateway;
    this.responseParser = deps.responseParser;
    this.companyDiscoveryParser = deps.companyDiscoveryParser;
    this.extractors = createMasterDataExtractorRegistry({
      gateway: deps.gateway,
      responseParser: deps.responseParser,
      logger: deps.logger,
    });
    this.companyInfoParser = createCompanyInfoParser(deps.responseParser);
  }

  isReady(): boolean {
    return this.gateway.isReady();
  }

  async discoverCompanies(): Promise<ErpCompanyDiscoveryResult> {
    if (!this.gateway.isReady()) {
      return {
        contractVersion: COMPANY_DISCOVERY_CONTRACT_VERSION,
        status: 'UNAVAILABLE',
        tallyReachable: false,
        items: [],
        reason: 'Tally connection manager is not running',
      };
    }

    try {
      const exchange = await this.gateway.executeApprovedRead({
        operationId: ApprovedOperationId.CompanyList,
      });

      const envelopeIssue = assessCompanyDiscoveryEnvelope(exchange.rawXml);
      if (envelopeIssue) {
        return {
          contractVersion: COMPANY_DISCOVERY_CONTRACT_VERSION,
          status: 'MALFORMED',
          tallyReachable: true,
          items: [],
          dataQuality: envelopeIssue,
          reason: envelopeIssue.reason,
        };
      }

      let document;
      try {
        document = this.responseParser.parse(exchange.rawXml);
      } catch (error) {
        return {
          contractVersion: COMPANY_DISCOVERY_CONTRACT_VERSION,
          status: 'MALFORMED',
          tallyReachable: true,
          items: [],
          reason: error instanceof Error ? error.message : 'Invalid company discovery XML',
        };
      }

      const parseResult = this.companyDiscoveryParser.parseCompanies(document);
      const assessment = assessCompanyDiscovery(parseResult);

      return {
        contractVersion: COMPANY_DISCOVERY_CONTRACT_VERSION,
        status: assessment.status,
        tallyReachable: true,
        items: parseResult.companies.map(mapDiscoveredToErpSummary),
        dataQuality: assessment.dataQuality,
        reason: assessment.reason,
      };
    } catch (error) {
      return classifyCompanyDiscoveryError(error);
    }
  }

  async getCompanyInfo(
    companyId: string,
    companyName: string,
  ): Promise<NormalizedCompanyInfo | undefined> {
    const exchange = await this.gateway.executeApprovedRead({
      operationId: ApprovedOperationId.CompanyInfo,
      companyName,
    });
    const document = this.companyInfoParser.parseDocument(exchange.rawXml);
    return mapCompanyInfo(this.companyInfoParser, document, companyId);
  }

  readLedgerGroups(companyName: string) {
    return this.extractors.ledgerGroups.extract(companyName);
  }

  readLedgers(companyName: string) {
    return this.extractors.ledgers.extract(companyName);
  }

  readStockGroups(companyName: string) {
    return this.extractors.stockGroups.extract(companyName);
  }

  readStockCategories(companyName: string) {
    return this.extractors.stockCategories.extract(companyName);
  }

  readStockItems(companyName: string) {
    return this.extractors.stockItems.extract(companyName);
  }

  readGodowns(companyName: string) {
    return this.extractors.godowns.extract(companyName);
  }

  readCostCategories(companyName: string) {
    return this.extractors.costCategories.extract(companyName);
  }

  readCostCentres(companyName: string) {
    return this.extractors.costCentres.extract(companyName);
  }

  readVoucherTypes(companyName: string) {
    return this.extractors.voucherTypes.extract(companyName);
  }

  readGstRegistrations(companyName: string) {
    return this.extractors.gstRegistrations.extract(companyName);
  }

  getReadDiagnostics(entityType?: MasterDataEntityType): readonly ExtractorDiagnostics[] {
    const all: ExtractorDiagnostics[] = [
      this.extractors.ledgerGroups.getDiagnostics(),
      this.extractors.ledgers.getDiagnostics(),
      this.extractors.stockGroups.getDiagnostics(),
      this.extractors.stockCategories.getDiagnostics(),
      this.extractors.stockItems.getDiagnostics(),
      this.extractors.godowns.getDiagnostics(),
      this.extractors.costCategories.getDiagnostics(),
      this.extractors.costCentres.getDiagnostics(),
      this.extractors.voucherTypes.getDiagnostics(),
      this.extractors.gstRegistrations.getDiagnostics(),
    ];
    return entityType ? all.filter((d) => d.entityType === entityType) : all;
  }
}

function classifyCompanyDiscoveryError(error: unknown): ErpCompanyDiscoveryResult {
  if (error instanceof AppError) {
    if (error.statusCode === 403) {
      return {
        contractVersion: COMPANY_DISCOVERY_CONTRACT_VERSION,
        status: 'DENIED',
        tallyReachable: true,
        items: [],
        reason: error.message,
      };
    }

    const message = error.message.toLowerCase();
    if (message.includes('timeout') || message.includes('timed out') || message.includes('abort')) {
      return {
        contractVersion: COMPANY_DISCOVERY_CONTRACT_VERSION,
        status: 'TIMEOUT',
        tallyReachable: false,
        items: [],
        reason: error.message,
      };
    }

    if (error.statusCode === 503) {
      return {
        contractVersion: COMPANY_DISCOVERY_CONTRACT_VERSION,
        status: 'UNAVAILABLE',
        tallyReachable: false,
        items: [],
        reason: error.message,
      };
    }
  }

  return {
    contractVersion: COMPANY_DISCOVERY_CONTRACT_VERSION,
    status: 'UNAVAILABLE',
    tallyReachable: false,
    items: [],
    reason: error instanceof Error ? error.message : String(error),
  };
}
