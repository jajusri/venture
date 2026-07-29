import type { Logger } from '../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../infrastructure/errors/app-error.js';
import type { ServiceStatus } from '../core/types.js';
import type { XmlImportService } from '../services/interfaces/xml-import.js';
import type { XmlImportAttemptRepository } from '../storage/sqlite/xml-import-attempt-repository.js';
import {
  InboundXmlEnvelopeService,
  type InboundXmlEnvelopeServiceOptions,
} from './inbound-xml-envelope.service.js';
import {
  InboundXmlSourceType,
  isValidatedInboundEnvelope,
  type InboundXmlAcceptResult,
} from './inbound-xml-types.js';

/**
 * OFFLINE Budcom XML FILE ingestion.
 *
 * ARCHITECTURAL BOUNDARY (Phase 3): this module ingests XML files supplied by
 * trusted internal callers (tests; future desktop IPC with path capability).
 * There is no OS file-picker product surface yet. Files are validated through
 * the unified inbound XML envelope boundary. This module has NO access to the
 * live Tally transport, connection manager, read gateway, fetch client, or any
 * network socket, and it never issues a Tally IMPORT request.
 */
export interface OfflineIngestionResult {
  readonly sourceLabel: string;
  readonly nodeCount: number;
  readonly byteLength: number;
  readonly resourceKind?: string;
  readonly contentFingerprint?: string;
  readonly duplicateStatus?: string;
  readonly validationStatus: string;
}

export interface OfflineIngestionOptions {
  readonly targetCompanyId?: string;
  readonly targetCompanyName?: string;
  readonly recordAttempt?: boolean;
  readonly approvedRoot?: string;
}

export class OfflineXmlIngestionService implements XmlImportService {
  private readonly envelopeService: InboundXmlEnvelopeService;
  private running = false;

  constructor(
    private readonly logger: Logger,
    envelopeOptions: InboundXmlEnvelopeServiceOptions = {},
  ) {
    this.envelopeService = new InboundXmlEnvelopeService({
      ...envelopeOptions,
      logger,
    });
  }

  static withRepository(
    logger: Logger,
    importAttemptRepository: XmlImportAttemptRepository,
    connectorVersion = '0.4.0',
  ): OfflineXmlIngestionService {
    return new OfflineXmlIngestionService(logger, {
      importAttemptRepository,
      connectorVersion,
    });
  }

  async start(): Promise<void> {
    this.running = true;
    this.logger.info('Offline XML ingestion framework started');
  }

  async stop(): Promise<void> {
    this.running = false;
    this.logger.info('Offline XML ingestion framework stopped');
  }

  isRunning(): boolean {
    return this.running;
  }

  getParser() {
    return this.envelopeService.getParser();
  }

  async ingestFile(filePath: string, options: OfflineIngestionOptions = {}): Promise<OfflineIngestionResult> {
    this.assertRunning();
    const result = await this.envelopeService.acceptFile({
      sourceType: options.approvedRoot
        ? InboundXmlSourceType.WatchedFolderFile
        : InboundXmlSourceType.TrustedInternalFile,
      filePath,
      approvedRoot: options.approvedRoot,
      targetCompanyId: options.targetCompanyId,
      targetCompanyName: options.targetCompanyName,
      recordAttempt: options.recordAttempt,
    });
    return this.toOfflineResult(result, filePath);
  }

  ingestString(
    xml: string,
    sourceLabel = 'inline',
    options: OfflineIngestionOptions = {},
  ): OfflineIngestionResult {
    this.assertRunning();
    const result = this.envelopeService.acceptBuffer(Buffer.from(xml, 'utf8'), {
      sourceType: InboundXmlSourceType.InlineBuffer,
      sourceIdentifier: sourceLabel,
      targetCompanyId: options.targetCompanyId,
      targetCompanyName: options.targetCompanyName,
      recordAttempt: options.recordAttempt,
    });
    return this.toOfflineResult(result, sourceLabel);
  }

  getStatus(): ServiceStatus {
    return {
      name: 'OfflineXmlIngestion',
      running: this.running,
      ready: this.running,
      message: 'Offline XML ingestion ready (no live Tally access)',
    };
  }

  private toOfflineResult(result: InboundXmlAcceptResult, sourceLabel: string): OfflineIngestionResult {
    if (!isValidatedInboundEnvelope(result)) {
      throw new AppError(ErrorCodes.VALIDATION_ERROR, result.message, 400, {
        reasonCode: result.reasonCode,
        importAttemptId: result.importAttemptId,
      });
    }
    return {
      sourceLabel,
      nodeCount: result.nodeCount,
      byteLength: result.byteSize,
      resourceKind: result.resourceKind,
      contentFingerprint: result.contentFingerprint,
      duplicateStatus: result.duplicateStatus,
      validationStatus: result.validationStatus,
    };
  }

  private assertRunning(): void {
    if (!this.running) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Offline XML ingestion is not running',
        503,
      );
    }
  }
}
