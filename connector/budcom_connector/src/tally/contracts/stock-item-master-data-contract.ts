import type { DataQuality } from '../../extraction/core/types.js';
import type { NormalizedStockItem } from '../../extraction/core/types.js';
import type { MasterDataExtractionMetrics } from '../../extraction/parsers/master-data-extraction-metrics.js';
import {
  bodyDataRegionPresent,
  detectTallyLineError,
  findBodyDataCollections,
  hasAssociatedHeaderStatusZero,
} from './master-data-envelope.js';
import { assessEnvelope } from './response-contract.js';
import type { ParsedXmlDocument } from '../xml/response-parser.js';

export const STOCK_ITEM_MASTER_DATA_CONTRACT_VERSION = '1';

export type StockItemMasterDataStatus =
  | 'SUCCESS'
  | 'EMPTY'
  | 'INCOMPLETE'
  | 'MALFORMED'
  | 'TALLY_ERROR'
  | 'COLLECTION_MISSING'
  | 'ALL_UNMAPPABLE';

export type StockItemMasterDataReasonCode =
  | 'envelope_drift'
  | 'tally_line_error'
  | 'collection_missing'
  | 'collection_empty'
  | 'placeholder_only'
  | 'all_entities_unmappable'
  | 'partial_entities_dropped';

export interface StockItemMasterDataAssessment {
  readonly status: StockItemMasterDataStatus;
  readonly reasonCode?: StockItemMasterDataReasonCode;
  readonly dataQuality?: DataQuality;
  readonly reason?: string;
  readonly blocking: boolean;
}

function assessStockCollectionUsability(items: readonly NormalizedStockItem[]): {
  readonly usableCount: number;
  readonly incompleteCount: number;
} {
  let usableCount = 0;
  let incompleteCount = 0;
  for (const item of items) {
    if (!item.name?.trim()) continue;
    usableCount += 1;
    if (!item.baseUnit?.trim()) incompleteCount += 1;
  }
  return { usableCount, incompleteCount };
}

export function assessStockItemMasterDataEnvelope(rawXml: string): StockItemMasterDataAssessment | undefined {
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
      reason: 'Tally returned an explicit error response for stock-item extraction',
      blocking: true,
    };
  }

  return undefined;
}

export function assessStockItemMasterDataStructure(document: ParsedXmlDocument): StockItemMasterDataAssessment {
  if (detectTallyLineError(document.rawXml, document)) {
    return {
      status: 'TALLY_ERROR',
      reasonCode: 'tally_line_error',
      dataQuality: { status: 'DRIFT', reason: 'Tally returned an explicit LINEERROR response' },
      reason: 'Tally returned an explicit error response for stock-item extraction',
      blocking: true,
    };
  }

  if (!bodyDataRegionPresent(document)) {
    return {
      status: 'COLLECTION_MISSING',
      reasonCode: 'collection_missing',
      dataQuality: { status: 'DRIFT', reason: 'Response BODY/DATA region missing' },
      reason: 'Stock-item response is missing the expected BODY/DATA collection region',
      blocking: true,
    };
  }

  const collections = findBodyDataCollections(document);
  if (collections.length === 0) {
    return {
      status: 'COLLECTION_MISSING',
      reasonCode: 'collection_missing',
      dataQuality: { status: 'DRIFT', reason: 'Requested COLLECTION absent under BODY/DATA' },
      reason: 'Stock-item response is missing the requested DATA/COLLECTION region',
      blocking: true,
    };
  }

  return {
    status: 'SUCCESS',
    blocking: false,
  };
}

export function assessStockItemMasterDataExtraction(
  metrics: MasterDataExtractionMetrics,
  items: readonly NormalizedStockItem[],
): StockItemMasterDataAssessment {
  if (metrics.placeholderOnlyCollection) {
    return {
      status: 'EMPTY',
      reasonCode: 'placeholder_only',
      dataQuality: { status: 'EMPTY', reason: 'Requested collection contained only placeholder metadata' },
      reason: 'Stock-item collection contained no real entity records',
      blocking: false,
    };
  }

  if (metrics.candidateNodeCount === 0) {
    return {
      status: 'EMPTY',
      reasonCode: 'collection_empty',
      dataQuality: { status: 'EMPTY', reason: 'Requested collection present but empty' },
      reason: 'No stock-item records in the requested collection',
      blocking: false,
    };
  }

  if (metrics.mappedRecordCount === 0) {
    return {
      status: 'ALL_UNMAPPABLE',
      reasonCode: 'all_entities_unmappable',
      dataQuality: {
        status: 'INCOMPLETE',
        reason: 'Stock-item entity nodes were present but none could be mapped',
      },
      reason: 'All stock-item entity nodes in the requested collection were unmappable',
      blocking: true,
    };
  }

  const usability = assessStockCollectionUsability(items);
  if (usability.usableCount === 0) {
    return {
      status: 'ALL_UNMAPPABLE',
      reasonCode: 'all_entities_unmappable',
      dataQuality: {
        status: 'INCOMPLETE',
        reason: 'Mapped stock items lacked usable identity fields',
      },
      reason: 'All stock-item entity nodes in the requested collection were unmappable',
      blocking: true,
    };
  }

  if (metrics.droppedRecordCount > 0 || metrics.missingIdentityCount > 0 || usability.incompleteCount > 0) {
    return {
      status: 'INCOMPLETE',
      reasonCode: 'partial_entities_dropped',
      dataQuality: {
        status: 'INCOMPLETE',
        reason: `${metrics.droppedRecordCount} stock-item record(s) dropped during extraction`,
      },
      blocking: false,
    };
  }

  return {
    status: 'SUCCESS',
    dataQuality: { status: 'COMPLETE' },
    blocking: false,
  };
}

export function toPrivacySafeStockItemContractAssessment(
  assessment: StockItemMasterDataAssessment,
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
export function readStockItemLineErrorMetadata(rawXml: string): { readonly associatedHeaderStatusZero: boolean } {
  return { associatedHeaderStatusZero: hasAssociatedHeaderStatusZero(rawXml) };
}
