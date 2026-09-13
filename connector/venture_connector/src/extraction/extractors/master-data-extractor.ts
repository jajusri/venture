import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { TallyReadGateway } from '../../tally/gateway/tally-read-gateway.js';
import type { ApprovedOperationId } from '../../tally/registry/operation-registry.js';
import { resolveXmlParserOptionsForOperation } from '../../tally/xml/response-parser-limits.js';
import type { TallyXmlResponseParser } from '../../tally/xml/response-parser.js';
import {
  assessLedgerMasterDataEnvelope,
  assessLedgerMasterDataExtraction,
  assessLedgerMasterDataStructure,
  LEDGER_MASTER_DATA_CONTRACT_VERSION,
  toPrivacySafeLedgerContractAssessment,
  type LedgerMasterDataAssessment,
  readLedgerLineErrorMetadata,
} from '../../tally/contracts/ledger-master-data-contract.js';
import {
  assessStockItemMasterDataEnvelope,
  assessStockItemMasterDataExtraction,
  assessStockItemMasterDataStructure,
  STOCK_ITEM_MASTER_DATA_CONTRACT_VERSION,
  toPrivacySafeStockItemContractAssessment,
  type StockItemMasterDataAssessment,
  readStockItemLineErrorMetadata,
} from '../../tally/contracts/stock-item-master-data-contract.js';
import { findBodyDataCollections } from '../../tally/contracts/master-data-envelope.js';
import type {
  ExtractionResult,
  ExtractorDiagnostics,
  MasterDataContractSummary,
  MasterDataEntityType,
  NormalizedLedger,
  NormalizedStockItem,
} from '../core/types.js';
import { dedupeById } from '../core/pagination.js';
import { CollectionEntityParser } from '../parsers/entity-mappers.js';
import {
  computeMasterDataExtractionMetrics,
  toPrivacySafeExtractionMetrics,
} from '../parsers/master-data-extraction-metrics.js';
import type { ParsedXmlNode } from '../../tally/xml/response-parser.js';

export type MasterDataContractKind = 'ledger' | 'stock-item';

export interface EntityExtractorConfig<T extends { id: string }> {
  readonly entityType: MasterDataEntityType;
  /** Approved registry operation this extractor is permitted to execute. */
  readonly operationId: ApprovedOperationId;
  readonly nodeName: string;
  readonly mapNode: (parser: CollectionEntityParser, node: ParsedXmlNode) => T | undefined;
  readonly postProcess?: (items: T[]) => T[];
  readonly masterDataContract?: MasterDataContractKind;
}

interface MutableExtractorDiagnostics extends ExtractorDiagnostics {
  totalExtractions: number;
  failedExtractions: number;
  lastExtractedAt?: string;
  lastDurationMs?: number;
  lastItemCount?: number;
  lastErrorAt?: string;
  lastErrorMessage?: string;
}

export class MasterDataExtractor<T extends { id: string }> {
  private readonly collectionParser: CollectionEntityParser;
  private diagnostics: MutableExtractorDiagnostics;

  constructor(
    private readonly config: EntityExtractorConfig<T>,
    private readonly gateway: TallyReadGateway,
    responseParser: TallyXmlResponseParser,
    private readonly logger: Logger,
  ) {
    this.collectionParser = new CollectionEntityParser(responseParser);
    this.diagnostics = {
      entityType: config.entityType,
      totalExtractions: 0,
      failedExtractions: 0,
    };
  }

  getDiagnostics(): ExtractorDiagnostics {
    return { ...this.diagnostics };
  }

  async extract(companyName: string, options: { signal?: AbortSignal } = {}): Promise<ExtractionResult<T>> {
    if (this.config.masterDataContract) {
      return this.extractWithContract(companyName, options);
    }
    return this.extractLegacy(companyName, options);
  }

  private async extractLegacy(
    companyName: string,
    options: { signal?: AbortSignal },
  ): Promise<ExtractionResult<T>> {
    const started = Date.now();
    this.diagnostics.totalExtractions += 1;

    try {
      const exchange = await this.gateway.executeApprovedRead({
        operationId: this.config.operationId,
        companyName,
        signal: options.signal,
      });

      const parserOptions = resolveXmlParserOptionsForOperation(this.config.operationId);
      const document = this.collectionParser.parseDocument(exchange.rawXml, parserOptions);
      const nodes = this.collectionParser.parseNodes(document, {
        nodeName: this.config.nodeName,
      });

      let items = nodes
        .map((node) => this.config.mapNode(this.collectionParser, node))
        .filter((item): item is T => item !== undefined);

      items = dedupeById(items);
      if (this.config.postProcess) {
        items = this.config.postProcess(items);
      }

      return this.completeSuccess(started, companyName, items, exchange.byteLength);
    } catch (error) {
      return this.failExtraction(error, companyName);
    }
  }

  private async extractWithContract(
    companyName: string,
    options: { signal?: AbortSignal },
  ): Promise<ExtractionResult<T>> {
    const started = Date.now();
    this.diagnostics.totalExtractions += 1;

    try {
      const exchange = await this.gateway.executeApprovedRead({
        operationId: this.config.operationId,
        companyName,
        signal: options.signal,
      });

      const parserOptions = resolveXmlParserOptionsForOperation(this.config.operationId);

      const preAssessment = this.assessEnvelope(exchange.rawXml);
      if (preAssessment?.blocking) {
        throw this.toContractError(preAssessment, undefined, exchange.rawXml);
      }

      let document;
      try {
        document = this.collectionParser.parseDocument(exchange.rawXml, parserOptions);
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Invalid master-data XML';
        throw new AppError(ErrorCodes.VALIDATION_ERROR, message, 502, {
          contractStatus: 'MALFORMED',
          reasonCode: 'envelope_drift',
        });
      }

      const structureAssessment = this.assessStructure(document);
      if (structureAssessment.blocking) {
        throw this.toContractError(structureAssessment, undefined, exchange.rawXml);
      }

      const collections = findBodyDataCollections(document);
      const candidateNodes = this.collectionParser.collectScopedCandidateNodes(
        document,
        this.config.nodeName,
      );
      const nodes = this.collectionParser.parseNodes(document, {
        nodeName: this.config.nodeName,
        scopeToRequestedCollection: true,
      });

      const mappedBeforeDedupe = nodes
        .map((node) => this.config.mapNode(this.collectionParser, node))
        .filter((item): item is T => item !== undefined);

      let items = dedupeById(mappedBeforeDedupe);
      if (this.config.postProcess) {
        items = this.config.postProcess(items);
      }

      const metrics = computeMasterDataExtractionMetrics({
        entityNodeName: this.config.nodeName,
        candidateNodes,
        mappedBeforeDedupe,
        mappedAfterDedupe: items,
        collectionPresent: collections.length > 0,
      });

      const extractionAssessment = this.assessExtraction(metrics, items);
      if (extractionAssessment.blocking) {
        throw this.toContractError(extractionAssessment, metrics);
      }

      const contract = this.toContractSummary(extractionAssessment);
      const durationMs = Date.now() - started;
      this.diagnostics = {
        ...this.diagnostics,
        lastExtractedAt: new Date().toISOString(),
        lastDurationMs: durationMs,
        lastItemCount: items.length,
        lastErrorAt: undefined,
        lastErrorMessage: undefined,
      };

      this.logger.info('Master data extraction complete', {
        entityType: this.config.entityType,
        itemCount: items.length,
        durationMs,
        byteLength: exchange.byteLength,
        contractStatus: contract.status,
        reasonCode: contract.reasonCode,
        ...toPrivacySafeExtractionMetrics(metrics),
      });

      return {
        items,
        durationMs,
        rawByteLength: exchange.byteLength,
        contract,
        extractionMetrics: {
          candidateNodeCount: metrics.candidateNodeCount,
          mappedRecordCount: metrics.mappedRecordCount,
          droppedRecordCount: metrics.droppedRecordCount,
          missingIdentityCount: metrics.missingIdentityCount,
          duplicateIdentityCount: metrics.duplicateIdentityCount,
          conflictingIdentityCount: metrics.conflictingIdentityCount,
          collectionPresent: metrics.collectionPresent,
          placeholderOnlyCollection: metrics.placeholderOnlyCollection,
        },
      };
    } catch (error) {
      return this.failExtraction(error, companyName);
    }
  }

  private assessEnvelope(rawXml: string): LedgerMasterDataAssessment | StockItemMasterDataAssessment | undefined {
    if (this.config.masterDataContract === 'ledger') {
      return assessLedgerMasterDataEnvelope(rawXml);
    }
    return assessStockItemMasterDataEnvelope(rawXml);
  }

  private assessStructure(document: import('../../tally/xml/response-parser.js').ParsedXmlDocument) {
    if (this.config.masterDataContract === 'ledger') {
      return assessLedgerMasterDataStructure(document);
    }
    return assessStockItemMasterDataStructure(document);
  }

  private assessExtraction(
    metrics: ReturnType<typeof computeMasterDataExtractionMetrics>,
    items: readonly T[],
  ): LedgerMasterDataAssessment | StockItemMasterDataAssessment {
    if (this.config.masterDataContract === 'ledger') {
      return assessLedgerMasterDataExtraction(metrics, items as unknown as readonly NormalizedLedger[]);
    }
    return assessStockItemMasterDataExtraction(metrics, items as unknown as readonly NormalizedStockItem[]);
  }

  private toContractSummary(
    assessment: LedgerMasterDataAssessment | StockItemMasterDataAssessment,
  ): MasterDataContractSummary {
    const version =
      this.config.masterDataContract === 'ledger'
        ? LEDGER_MASTER_DATA_CONTRACT_VERSION
        : STOCK_ITEM_MASTER_DATA_CONTRACT_VERSION;
    const safe =
      this.config.masterDataContract === 'ledger'
        ? toPrivacySafeLedgerContractAssessment(assessment as LedgerMasterDataAssessment)
        : toPrivacySafeStockItemContractAssessment(assessment as StockItemMasterDataAssessment);
    return {
      contractVersion: version,
      status: safe.contractStatus as string,
      reasonCode: safe.reasonCode as string | undefined,
      blocking: safe.blocking as boolean,
      dataQualityStatus: safe.dataQualityStatus as MasterDataContractSummary['dataQualityStatus'],
    };
  }

  private toContractError(
    assessment: LedgerMasterDataAssessment | StockItemMasterDataAssessment,
    metrics?: ReturnType<typeof computeMasterDataExtractionMetrics>,
    rawXml?: string,
  ): AppError {
    const associatedHeaderStatusZero =
      rawXml && assessment.reasonCode === 'tally_line_error'
        ? this.config.masterDataContract === 'ledger'
          ? readLedgerLineErrorMetadata(rawXml).associatedHeaderStatusZero
          : readStockItemLineErrorMetadata(rawXml).associatedHeaderStatusZero
        : undefined;
    const safeAssessment =
      this.config.masterDataContract === 'ledger'
        ? toPrivacySafeLedgerContractAssessment(assessment as LedgerMasterDataAssessment, {
            associatedHeaderStatusZero,
          })
        : toPrivacySafeStockItemContractAssessment(assessment as StockItemMasterDataAssessment, {
            associatedHeaderStatusZero,
          });
    return new AppError(
      ErrorCodes.VALIDATION_ERROR,
      assessment.reason ?? 'Master-data response contract failure.',
      502,
      {
        ...safeAssessment,
        ...(metrics ? { extractionMetrics: toPrivacySafeExtractionMetrics(metrics) } : {}),
      },
    );
  }

  private completeSuccess(started: number, _companyName: string, items: T[], byteLength: number) {
    const durationMs = Date.now() - started;
    this.diagnostics = {
      ...this.diagnostics,
      lastExtractedAt: new Date().toISOString(),
      lastDurationMs: durationMs,
      lastItemCount: items.length,
      lastErrorAt: undefined,
      lastErrorMessage: undefined,
    };

    this.logger.info('Master data extraction complete', {
      entityType: this.config.entityType,
      itemCount: items.length,
      durationMs,
      byteLength,
    });

    return {
      items,
      durationMs,
      rawByteLength: byteLength,
    };
  }

  private failExtraction(error: unknown, _companyName: string): never {
    this.diagnostics.failedExtractions += 1;
    this.diagnostics.lastErrorAt = new Date().toISOString();
    this.diagnostics.lastErrorMessage = error instanceof Error ? error.message : String(error);

    this.logger.error('Master data extraction failed', {
      entityType: this.config.entityType,
      reasonCode: 'extraction_failed',
      code: error instanceof AppError ? error.code : ErrorCodes.SERVICE_UNAVAILABLE,
    });

    throw error instanceof AppError
      ? error
      : new AppError(
          ErrorCodes.SERVICE_UNAVAILABLE,
          `Failed to extract ${this.config.entityType}: ${this.diagnostics.lastErrorMessage}`,
          503,
        );
  }
}
