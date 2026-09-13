import type { DataQuality } from '../../extraction/core/types.js';
import type { NormalizedLedger } from '../../extraction/core/types.js';
import type { MasterDataExtractionMetrics } from '../../extraction/parsers/master-data-extraction-metrics.js';
import { assessLedgerExtraction } from '../../erp/ledger/ledger-extraction-quality.js';
import {
  bodyDataRegionPresent,
  detectTallyLineError,
  findBodyDataCollections,
  hasAssociatedHeaderStatusZero,
} from './master-data-envelope.js';
import { assessEnvelope } from './response-contract.js';
import type { ParsedXmlDocument } from '../xml/response-parser.js';

export const LEDGER_MASTER_DATA_CONTRACT_VERSION = '1';

export type LedgerMasterDataStatus =
  | 'SUCCESS'
  | 'EMPTY'
  | 'INCOMPLETE'
  | 'MALFORMED'
  | 'TALLY_ERROR'
  | 'COLLECTION_MISSING'
  | 'ALL_UNMAPPABLE';

export type LedgerMasterDataReasonCode =
  | 'envelope_drift'
  | 'tally_line_error'
  | 'collection_missing'
  | 'collection_empty'
  | 'placeholder_only'
  | 'all_entities_unmappable'
  | 'partial_entities_dropped'
  | 'shallow_export';

export interface LedgerMasterDataAssessment {
  readonly status: LedgerMasterDataStatus;
  readonly reasonCode?: LedgerMasterDataReasonCode;
  readonly dataQuality?: DataQuality;
  readonly reason?: string;
  readonly blocking: boolean;
}

export function assessLedgerMasterDataEnvelope(rawXml: string): LedgerMasterDataAssessment | undefined {
  const envelopeIssue = assessEnvelope(rawXml);
  if (envelopeIssue) {
    return {
      status: 'MALFORMED',
      reasonCode: 'envelope_drift',
      dataQuality: envelopeIssue,
      reason: envelopeIssue.reason,
      blocking: true,
    };
  }

  if (detectTallyLineError(rawXml)) {
    return {
      status: 'TALLY_ERROR',
      reasonCode: 'tally_line_error',
      dataQuality: { status: 'DRIFT', reason: 'Tally returned an explicit LINEERROR response' },
      reason: 'Tally returned an explicit error response for ledger extraction',
      blocking: true,
    };
  }

  return undefined;
}

export function assessLedgerMasterDataStructure(document: ParsedXmlDocument): LedgerMasterDataAssessment {
  if (detectTallyLineError(document.rawXml, document)) {
    return {
      status: 'TALLY_ERROR',
      reasonCode: 'tally_line_error',
      dataQuality: { status: 'DRIFT', reason: 'Tally returned an explicit LINEERROR response' },
      reason: 'Tally returned an explicit error response for ledger extraction',
      blocking: true,
    };
  }

  if (!bodyDataRegionPresent(document)) {
    return {
      status: 'COLLECTION_MISSING',
      reasonCode: 'collection_missing',
      dataQuality: { status: 'DRIFT', reason: 'Response BODY/DATA region missing' },
      reason: 'Ledger response is missing the expected BODY/DATA collection region',
      blocking: true,
    };
  }

  const collections = findBodyDataCollections(document);
  if (collections.length === 0) {
    return {
      status: 'COLLECTION_MISSING',
      reasonCode: 'collection_missing',
      dataQuality: { status: 'DRIFT', reason: 'Requested COLLECTION absent under BODY/DATA' },
      reason: 'Ledger response is missing the requested DATA/COLLECTION region',
      blocking: true,
    };
  }

  return {
    status: 'SUCCESS',
    blocking: false,
  };
}

export function assessLedgerMasterDataExtraction(
  metrics: MasterDataExtractionMetrics,
  items: readonly NormalizedLedger[],
): LedgerMasterDataAssessment {
  if (metrics.placeholderOnlyCollection) {
    return {
      status: 'EMPTY',
      reasonCode: 'placeholder_only',
      dataQuality: { status: 'EMPTY', reason: 'Requested collection contained only placeholder metadata' },
      reason: 'Ledger collection contained no real entity records',
      blocking: false,
    };
  }

  if (metrics.candidateNodeCount === 0) {
    return {
      status: 'EMPTY',
      reasonCode: 'collection_empty',
      dataQuality: { status: 'EMPTY', reason: 'Requested collection present but empty' },
      reason: 'No ledger records in the requested collection',
      blocking: false,
    };
  }

  if (metrics.mappedRecordCount === 0) {
    return {
      status: 'ALL_UNMAPPABLE',
      reasonCode: 'all_entities_unmappable',
      dataQuality: {
        status: 'INCOMPLETE',
        reason: 'Ledger entity nodes were present but none could be mapped',
      },
      reason: 'All ledger entity nodes in the requested collection were unmappable',
      blocking: true,
    };
  }

  const domainQuality = assessLedgerExtraction(items);
  if (domainQuality.quality === 'invalid') {
    return {
      status: 'ALL_UNMAPPABLE',
      reasonCode: 'shallow_export',
      dataQuality: { status: 'INCOMPLETE', reason: domainQuality.reason },
      reason: domainQuality.reason ?? 'Ledger export did not satisfy minimum usable record requirements',
      blocking: true,
    };
  }

  if (
    metrics.droppedRecordCount > 0 ||
    metrics.missingIdentityCount > 0 ||
    domainQuality.quality === 'partial'
  ) {
    return {
      status: 'INCOMPLETE',
      reasonCode: 'partial_entities_dropped',
      dataQuality: {
        status: 'INCOMPLETE',
        reason:
          domainQuality.reason ??
          `${metrics.droppedRecordCount} ledger record(s) dropped during extraction`,
      },
      reason: domainQuality.reason,
      blocking: false,
    };
  }

  return {
    status: 'SUCCESS',
    dataQuality: { status: 'COMPLETE' },
    blocking: false,
  };
}

export function toPrivacySafeLedgerContractAssessment(
  assessment: LedgerMasterDataAssessment,
  options: { readonly associatedHeaderStatusZero?: boolean } = {},
): Record<string, string | boolean | undefined> {
  return {
    contractStatus: assessment.status,
    reasonCode: assessment.reasonCode,
    blocking: assessment.blocking,
    dataQualityStatus: assessment.dataQuality?.status,
    ...(options.associatedHeaderStatusZero === true
      ? { associatedHeaderStatusZero: true }
      : {}),
  };
}

/** Optional metadata for LINEERROR responses; never used as a standalone failure classifier. */
export function readLedgerLineErrorMetadata(rawXml: string): { readonly associatedHeaderStatusZero: boolean } {
  return { associatedHeaderStatusZero: hasAssociatedHeaderStatusZero(rawXml) };
}
