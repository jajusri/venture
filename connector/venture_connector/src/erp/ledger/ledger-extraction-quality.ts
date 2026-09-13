import type { NormalizedLedger } from '../../extraction/core/types.js';
import type { NormalizedLedgerDataQuality } from '../../extraction/core/types.js';

export type LedgerDataQuality = NormalizedLedgerDataQuality;

export interface LedgerExtractionAssessment {
  readonly quality: LedgerDataQuality;
  readonly totalRecords: number;
  readonly guidPresentCount: number;
  readonly parentPresentCount: number;
  readonly masterIdPresentCount: number;
  readonly reason?: string;
}

export function assessLedgerExtraction(items: readonly NormalizedLedger[]): LedgerExtractionAssessment {
  const totalRecords = items.length;
  const guidPresentCount = items.filter((item) => Boolean(item.guid?.trim())).length;
  const parentPresentCount = items.filter((item) => Boolean(item.parentGroup?.trim())).length;
  const masterIdPresentCount = items.filter((item) => Boolean(item.masterId?.trim())).length;

  if (totalRecords === 0) {
    return {
      quality: 'partial',
      totalRecords: 0,
      guidPresentCount: 0,
      parentPresentCount: 0,
      masterIdPresentCount: 0,
      reason: 'No ledger records extracted.',
    };
  }

  // Shallow standard-export signature: rich contract requested but neither GUID nor PARENT appear.
  if (guidPresentCount === 0 && parentPresentCount === 0) {
    return {
      quality: 'invalid',
      totalRecords,
      guidPresentCount,
      parentPresentCount,
      masterIdPresentCount,
      reason: 'Shallow ledger export detected: PARENT and GUID absent despite rich contract.',
    };
  }

  if (guidPresentCount === totalRecords) {
    return {
      quality: 'complete',
      totalRecords,
      guidPresentCount,
      parentPresentCount,
      masterIdPresentCount,
    };
  }

  if (guidPresentCount > 0 || parentPresentCount > 0) {
    return {
      quality: 'partial',
      totalRecords,
      guidPresentCount,
      parentPresentCount,
      masterIdPresentCount,
      reason: `${totalRecords - guidPresentCount} ledger record(s) missing GUID; name fallback applied.`,
    };
  }

  return {
    quality: 'invalid',
    totalRecords,
    guidPresentCount,
    parentPresentCount,
    masterIdPresentCount,
    reason: 'Ledger extraction did not satisfy minimum usable record requirements.',
  };
}
