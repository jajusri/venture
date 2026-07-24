import { describe, expect, it } from 'vitest';

import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import {
  normalizeSyncFailure,
  SYNC_FAILURE_SUMMARIES,
} from '../../../src/infrastructure/privacy/sync-failure-normalizer.js';

const FORBIDDEN = [
  'JAJU SANITATIONS',
  'Sensitive Debtor',
  '29AABCU9603R1ZM',
  '<LEDGER',
  'C:\\Users',
  'sk-live',
] as const;

describe('sync failure normalizer (group 4 unit)', () => {
  it.each([
    [
      new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Tally connection failed: fetch failed', 503),
      SYNC_FAILURE_SUMMARIES.transport_unavailable,
    ],
    [
      new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Tally request timed out after 120000ms', 504),
      SYNC_FAILURE_SUMMARIES.transport_timeout,
    ],
    [
      new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Tally response exceeds maximum size (10485760 bytes)',
        503,
      ),
      SYNC_FAILURE_SUMMARIES.response_too_large,
    ],
    [
      new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Response is not a well-formed Tally ENVELOPE',
        503,
      ),
      SYNC_FAILURE_SUMMARIES.response_parse_failed,
    ],
    [
      new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Shallow ledger export detected: PARENT and GUID absent despite rich contract.',
        503,
      ),
      SYNC_FAILURE_SUMMARIES.response_contract_rejected,
    ],
    [
      new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Tally returned an explicit error response for ledger extraction',
        503,
      ),
      SYNC_FAILURE_SUMMARIES.response_contract_rejected,
    ],
    [
      new Error('SQLITE_CONSTRAINT: database is locked at C:\\Users\\Secret\\budcom-ledger.db'),
      SYNC_FAILURE_SUMMARIES.storage_failure,
    ],
    [
      new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        '<LINEERROR>Company JAJU SANITATIONS PRIVATE LIMITED GSTIN 29AABCU9603R1ZM</LINEERROR>',
        503,
      ),
      SYNC_FAILURE_SUMMARIES.tally_reported_error,
    ],
  ])('maps errors to stable summary', (error, expectedSummary) => {
    const normalized = normalizeSyncFailure(error);
    expect(normalized.failureSummary).toBe(expectedSummary);
    const serialized = JSON.stringify(normalized);
    for (const value of FORBIDDEN) {
      expect(serialized.toLowerCase()).not.toContain(value.toLowerCase());
    }
  });
});
