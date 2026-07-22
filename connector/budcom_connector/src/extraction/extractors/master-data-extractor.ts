import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { TallyReadGateway } from '../../tally/gateway/tally-read-gateway.js';
import type { ApprovedOperationId } from '../../tally/registry/operation-registry.js';
import type { TallyXmlResponseParser } from '../../tally/xml/response-parser.js';
import type {
  ExtractionResult,
  ExtractorDiagnostics,
  MasterDataEntityType,
} from '../core/types.js';
import { dedupeById } from '../core/pagination.js';
import { CollectionEntityParser } from '../parsers/entity-mappers.js';
import type { ParsedXmlNode } from '../../tally/xml/response-parser.js';

export interface EntityExtractorConfig<T extends { id: string }> {
  readonly entityType: MasterDataEntityType;
  /** Approved registry operation this extractor is permitted to execute. */
  readonly operationId: ApprovedOperationId;
  readonly nodeName: string;
  readonly mapNode: (parser: CollectionEntityParser, node: ParsedXmlNode) => T | undefined;
  readonly postProcess?: (items: T[]) => T[];
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

  async extract(companyName: string): Promise<ExtractionResult<T>> {
    const started = Date.now();
    this.diagnostics.totalExtractions += 1;

    try {
      const exchange = await this.gateway.executeApprovedRead({
        operationId: this.config.operationId,
        companyName,
      });

      const document = this.collectionParser.parseDocument(exchange.rawXml);
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
        companyName,
        itemCount: items.length,
        durationMs,
        byteLength: exchange.byteLength,
      });

      return {
        items,
        durationMs,
        rawByteLength: exchange.byteLength,
      };
    } catch (error) {
      this.diagnostics.failedExtractions += 1;
      this.diagnostics.lastErrorAt = new Date().toISOString();
      this.diagnostics.lastErrorMessage =
        error instanceof Error ? error.message : String(error);

      this.logger.error('Master data extraction failed', {
        entityType: this.config.entityType,
        companyName,
        error: this.diagnostics.lastErrorMessage,
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
}
