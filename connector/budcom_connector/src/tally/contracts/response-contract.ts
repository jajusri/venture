import type { DataQuality } from '../../extraction/core/types.js';

/**
 * Response-contract safety (Phase 10).
 *
 * Future Tally XML changes must never silently corrupt normalized output. These
 * helpers turn "missing critical data" into an explicit INCOMPLETE/DRIFT signal
 * instead of an empty-but-successful-looking result.
 */

export const RESPONSE_CONTRACT_VERSION = '1';

/** A response that is not a well-formed Tally ENVELOPE is treated as schema drift. */
export function assessEnvelope(rawXml: string): DataQuality | undefined {
  if (!/<ENVELOPE>[\s\S]*<\/ENVELOPE>/i.test(rawXml.trim())) {
    return { status: 'DRIFT', reason: 'Response is not a well-formed Tally ENVELOPE' };
  }
  return undefined;
}

/**
 * Assess a derived-units result. Units are derived from stock-item base-unit
 * fields; when stock items exist but no unit could be derived, the source export
 * omitted the unit fields — this is an INCOMPLETE contract, never a PASS.
 */
export function assessDerivedUnits(
  unitCount: number,
  sourceStockItemCount: number,
): DataQuality {
  if (sourceStockItemCount === 0) {
    return { status: 'EMPTY', reason: 'No stock items available to derive units from' };
  }
  if (unitCount === 0) {
    return {
      status: 'INCOMPLETE',
      reason:
        'Stock items present but no unit fields (BASEUNITS/ADDITIONALUNITS) were returned by Tally; ' +
        'units cannot be derived. Direct "List of Units" is FORBIDDEN (deadlock evidence).',
    };
  }
  return { status: 'COMPLETE' };
}
