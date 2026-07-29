import type { Logger } from '../infrastructure/logging/logger.js';
import { assessEnvelope } from '../tally/contracts/response-contract.js';
import { assessLedgerMasterDataEnvelope } from '../tally/contracts/ledger-master-data-contract.js';
import { assessStockItemMasterDataEnvelope } from '../tally/contracts/stock-item-master-data-contract.js';
import { detectTallyLineError } from '../tally/contracts/master-data-envelope.js';
import { TallyXmlResponseParser, XmlParseError } from '../tally/xml/response-parser.js';
import { DEFAULT_XML_PARSER_MAX_BYTES } from '../tally/xml/response-parser-limits.js';
import type { XmlImportAttemptRepository } from '../storage/sqlite/xml-import-attempt-repository.js';
import {
  decodeInboundXmlBytes,
  extractReasonCode as extractEncodingReason,
  fingerprintInboundXmlBytes,
} from './inbound-xml-encoding.js';
import {
  extractReasonCode as extractFileReason,
  readStableInboundXmlFile,
} from './inbound-xml-file-source.js';
import { assertInboundXmlProhibitedConstructs } from './inbound-xml-prohibited-constructs.js';
import { InboundXmlReasonCode } from './inbound-xml-reason-codes.js';
import {
  assertCompanyIdentity,
  assertExpectedResourceKind,
  countParsedNodes,
  detectInboundXmlResourceKind,
  resolveParserMaxBytes,
} from './inbound-xml-resource-kind.js';
import {
  INBOUND_XML_ENVELOPE_CONTRACT_VERSION,
  InboundXmlDuplicateStatus,
  InboundXmlPersistenceStatus,
  InboundXmlResourceKind,
  InboundXmlSourceType,
  InboundXmlValidationStatus,
  type InboundXmlAcceptBufferOptions,
  type InboundXmlAcceptFileOptions,
  type InboundXmlAcceptResult,
  type ValidatedInboundXmlEnvelope,
} from './inbound-xml-types.js';

const PARSER_VERSION = '1';

export interface InboundXmlEnvelopeServiceOptions {
  readonly parser?: TallyXmlResponseParser;
  readonly logger?: Logger;
  readonly connectorVersion?: string;
  readonly importAttemptRepository?: XmlImportAttemptRepository;
  readonly maxPackageBytes?: number;
}

export class InboundXmlEnvelopeService {
  private readonly parser: TallyXmlResponseParser;
  private readonly logger?: Logger;
  private readonly connectorVersion: string;
  private readonly importAttemptRepository?: XmlImportAttemptRepository;
  private readonly maxPackageBytes: number;

  constructor(options: InboundXmlEnvelopeServiceOptions = {}) {
    this.parser = options.parser ?? new TallyXmlResponseParser();
    this.logger = options.logger;
    this.connectorVersion = options.connectorVersion ?? '0.4.0';
    this.importAttemptRepository = options.importAttemptRepository;
    this.maxPackageBytes = options.maxPackageBytes ?? DEFAULT_XML_PARSER_MAX_BYTES;
  }

  getParser(): TallyXmlResponseParser {
    return this.parser;
  }

  async acceptFile(options: InboundXmlAcceptFileOptions): Promise<InboundXmlAcceptResult> {
    try {
      const { rawBytes, sourceIdentifier } = await readStableInboundXmlFile({
        filePath: options.filePath,
        sourceType: options.sourceType,
        approvedRoot: options.approvedRoot,
      });
      return this.acceptBuffer(rawBytes, {
        sourceType: options.sourceType,
        sourceIdentifier,
        targetCompanyId: options.targetCompanyId,
        targetCompanyName: options.targetCompanyName,
        expectedResourceKind: options.expectedResourceKind,
        recordAttempt: options.recordAttempt,
      });
    } catch (error) {
      return this.rejectFromError(error, {
        sourceType: options.sourceType,
        sourceIdentifier: options.filePath,
        recordAttempt: options.recordAttempt,
      });
    }
  }

  acceptBuffer(rawBytes: Buffer, options: InboundXmlAcceptBufferOptions): InboundXmlAcceptResult {
    const receivedAt = new Date().toISOString();
    const sourceIdentifier = options.sourceIdentifier ?? 'inline-buffer';

    try {
      if (rawBytes.length > this.maxPackageBytes) {
        throw rejectError(
          InboundXmlReasonCode.OversizedPayload,
          'Inbound XML exceeds the approved package byte limit.',
        );
      }

      const decoded = decodeInboundXmlBytes(rawBytes);
      assertInboundXmlProhibitedConstructs(decoded.text);

      const envelopeIssue = assessEnvelope(decoded.text);
      if (envelopeIssue) {
        throw rejectError(InboundXmlReasonCode.EnvelopeDrift, 'Inbound XML failed envelope validation.');
      }

      if (detectTallyLineError(decoded.text)) {
        throw rejectError(InboundXmlReasonCode.TallyLineError, 'Inbound XML contains an explicit Tally error.');
      }

      const provisionalResourceKind = options.expectedResourceKind ?? InboundXmlResourceKind.Ledgers;
      const parserMaxBytes = resolveParserMaxBytes(provisionalResourceKind);
      const document = this.parser.parse(decoded.text, { maxBytes: parserMaxBytes });

      const resourceKind = detectInboundXmlResourceKind(document);
      assertExpectedResourceKind(resourceKind, options.expectedResourceKind);
      this.assertResourceEnvelope(decoded.text, resourceKind);

      const sourceCompanyName = assertCompanyIdentity({
        rawXml: decoded.text,
        resourceKind,
        targetCompanyId: options.targetCompanyId,
        targetCompanyName: options.targetCompanyName,
      });

      const contentFingerprint = fingerprintInboundXmlBytes(decoded.rawBytes);
      let duplicateStatus: InboundXmlDuplicateStatus = InboundXmlDuplicateStatus.NotEvaluated;
      let persistenceStatus: InboundXmlPersistenceStatus = InboundXmlPersistenceStatus.NotAttempted;
      let importAttemptId: string | undefined;

      if (options.recordAttempt && this.importAttemptRepository) {
        const reservation = this.importAttemptRepository.reserveAttempt({
          companyId: options.targetCompanyId ?? null,
          resourceKind,
          sourceType: options.sourceType,
          sourceIdentifier,
          contentFingerprint,
          byteSize: decoded.rawBytes.length,
          parserVersion: PARSER_VERSION,
          connectorVersion: this.connectorVersion,
        });

        if (reservation.kind === 'duplicate') {
          return {
            contractVersion: INBOUND_XML_ENVELOPE_CONTRACT_VERSION,
            sourceType: options.sourceType,
            sourceIdentifier,
            byteSize: decoded.rawBytes.length,
            contentFingerprint,
            detectedEncoding: decoded.detectedEncoding,
            resourceKind,
            sourceCompanyName,
            targetCompanyId: options.targetCompanyId,
            validationStatus: InboundXmlValidationStatus.Validated,
            duplicateStatus: InboundXmlDuplicateStatus.Duplicate,
            persistenceStatus: InboundXmlPersistenceStatus.Skipped,
            importAttemptId: reservation.importAttemptId,
            parserVersion: PARSER_VERSION,
            receivedAt,
            rawBytes: decoded.rawBytes,
            document,
            nodeCount: countParsedNodes(document.root),
          };
        }

        if (reservation.kind === 'in_progress') {
          return {
            validationStatus: InboundXmlValidationStatus.Rejected,
            reasonCode: InboundXmlReasonCode.ImportInProgress,
            message: 'An equivalent import is already in progress.',
            importAttemptId: reservation.importAttemptId,
          };
        }

        importAttemptId = reservation.importAttemptId;
        duplicateStatus = InboundXmlDuplicateStatus.Unique;
      }

      if (importAttemptId && this.importAttemptRepository) {
        persistenceStatus = InboundXmlPersistenceStatus.Completed;
        try {
          this.importAttemptRepository.completeAttempt({
            importAttemptId,
            validationStatus: InboundXmlValidationStatus.Validated,
            persistenceStatus,
            duplicateStatus: duplicateStatus === InboundXmlDuplicateStatus.NotEvaluated
              ? InboundXmlDuplicateStatus.Unique
              : duplicateStatus,
          });
        } catch (completeError) {
          this.importAttemptRepository.releaseAttempt(
            importAttemptId,
            InboundXmlReasonCode.PersistenceFailed,
          );
          throw completeError;
        }
      }

      return {
        contractVersion: INBOUND_XML_ENVELOPE_CONTRACT_VERSION,
        sourceType: options.sourceType,
        sourceIdentifier,
        byteSize: decoded.rawBytes.length,
        contentFingerprint,
        detectedEncoding: decoded.detectedEncoding,
        resourceKind,
        sourceCompanyName,
        targetCompanyId: options.targetCompanyId,
        validationStatus: InboundXmlValidationStatus.Validated,
        duplicateStatus: duplicateStatus === InboundXmlDuplicateStatus.NotEvaluated
          ? InboundXmlDuplicateStatus.Unique
          : duplicateStatus,
        persistenceStatus,
        importAttemptId,
        parserVersion: PARSER_VERSION,
        receivedAt,
        rawBytes: decoded.rawBytes,
        document,
        nodeCount: countParsedNodes(document.root),
      };
    } catch (error) {
      return this.rejectFromError(error, {
        sourceType: options.sourceType,
        sourceIdentifier,
        recordAttempt: options.recordAttempt,
        rawBytes,
      });
    }
  }

  validateEnvelope(rawBytes: Buffer, options: InboundXmlAcceptBufferOptions): InboundXmlAcceptResult {
    return this.acceptBuffer(rawBytes, options);
  }

  private assertResourceEnvelope(rawXml: string, resourceKind: InboundXmlResourceKind): void {
    if (resourceKind === InboundXmlResourceKind.Ledgers) {
      const issue = assessLedgerMasterDataEnvelope(rawXml);
      if (issue?.blocking) {
        throw rejectError(issue.reasonCode ?? InboundXmlReasonCode.EnvelopeDrift, issue.reason ?? 'Ledger envelope rejected.');
      }
      return;
    }
    if (resourceKind === InboundXmlResourceKind.StockItems) {
      const issue = assessStockItemMasterDataEnvelope(rawXml);
      if (issue?.blocking) {
        throw rejectError(issue.reasonCode ?? InboundXmlReasonCode.EnvelopeDrift, issue.reason ?? 'Stock envelope rejected.');
      }
    }
  }

  private rejectFromError(
    error: unknown,
    context: {
      readonly sourceType: InboundXmlSourceType;
      readonly sourceIdentifier: string;
      readonly recordAttempt?: boolean;
      readonly rawBytes?: Buffer;
      readonly importAttemptId?: string;
    },
  ): InboundXmlAcceptResult {
    const reasonCode =
      extractEncodingReason(error)
      ?? extractFileReason(error)
      ?? (error instanceof XmlParseError ? mapParserReason(error) : undefined)
      ?? extractInlineReason(error)
      ?? InboundXmlReasonCode.XmlMalformed;

    const message = error instanceof Error ? error.message : 'Inbound XML validation failed.';
    this.logger?.warn('Inbound XML rejected', { reasonCode, sourceType: context.sourceType });

    let importAttemptId = context.importAttemptId;
    if (context.importAttemptId && this.importAttemptRepository) {
      this.importAttemptRepository.releaseAttempt(context.importAttemptId, reasonCode);
    } else if (context.recordAttempt && this.importAttemptRepository && context.rawBytes) {
      const record = this.importAttemptRepository.recordRejectedAttempt({
        companyId: null,
        resourceKind: InboundXmlResourceKind.Ledgers,
        sourceType: context.sourceType,
        sourceIdentifier: context.sourceIdentifier,
        contentFingerprint: fingerprintInboundXmlBytes(context.rawBytes),
        byteSize: context.rawBytes.length,
        parserVersion: PARSER_VERSION,
        connectorVersion: this.connectorVersion,
        errorCode: reasonCode,
      });
      importAttemptId = record.importAttemptId;
    }

    return {
      validationStatus: InboundXmlValidationStatus.Rejected,
      reasonCode,
      message,
      importAttemptId,
    };
  }
}

function rejectError(reasonCode: string, message: string): Error {
  const error = new Error(message);
  (error as Error & { reasonCode: string }).reasonCode = reasonCode;
  return error;
}

function extractInlineReason(error: unknown): string | undefined {
  if (typeof error === 'object' && error !== null && 'reasonCode' in error) {
    const value = (error as { reasonCode?: unknown }).reasonCode;
    return typeof value === 'string' ? value : undefined;
  }
  return undefined;
}

function mapParserReason(error: XmlParseError): string {
  switch (error.reason) {
    case 'xml_oversized':
      return InboundXmlReasonCode.XmlOversized;
    case 'xml_max_depth_exceeded':
      return InboundXmlReasonCode.XmlMaxDepth;
    case 'xml_max_node_count_exceeded':
      return InboundXmlReasonCode.XmlMaxNodes;
    default:
      return InboundXmlReasonCode.XmlMalformed;
  }
}

export function establishProvenance(envelope: ValidatedInboundXmlEnvelope): {
  readonly sourceType: InboundXmlSourceType;
  readonly sourceIdentifier: string;
  readonly receivedAt: string;
  readonly contentFingerprint: string;
} {
  return {
    sourceType: envelope.sourceType,
    sourceIdentifier: envelope.sourceIdentifier,
    receivedAt: envelope.receivedAt,
    contentFingerprint: envelope.contentFingerprint,
  };
}

export function calculateFingerprint(rawBytes: Buffer): string {
  return fingerprintInboundXmlBytes(rawBytes);
}
