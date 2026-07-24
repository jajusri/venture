import { ErrorCodes, isAppError } from '../errors/app-error.js';
import { sanitizePersistentText } from './persistent-text-sanitizer.js';

export const SYNC_FAILURE_SUMMARIES = {
  transport_unavailable: 'transport_unavailable',
  transport_timeout: 'transport_timeout',
  response_too_large: 'response_too_large',
  response_parse_failed: 'response_parse_failed',
  response_contract_rejected: 'response_contract_rejected',
  tally_reported_error: 'tally_reported_error',
  storage_failure: 'storage_failure',
  sync_cancelled: 'sync_cancelled',
  sync_conflict: 'sync_conflict',
  validation_error: 'validation_error',
  read_only_violation: 'read_only_violation',
  unexpected_sync_failure: 'unexpected_sync_failure',
} as const;

export type SyncFailureSummary = (typeof SYNC_FAILURE_SUMMARIES)[keyof typeof SYNC_FAILURE_SUMMARIES];

export interface NormalizedSyncFailure {
  readonly failureCode: string;
  readonly failureSummary: SyncFailureSummary;
}

function classifyMessage(message: string): SyncFailureSummary {
  const normalized = message.toLowerCase();
  if (/timed out|timeout after/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.transport_timeout;
  }
  if (/connection failed|econnrefused|etimedout|fetch failed|unavailable or restarting/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.transport_unavailable;
  }
  if (/exceeds maximum size|response exceeds maximum/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.response_too_large;
  }
  if (/well-formed|malformed|parse|envelope|truncated xml|invalid master-data xml/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.response_parse_failed;
  }
  if (/lineerror/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.tally_reported_error;
  }
  if (/explicit error response|contract|shallow ledger|shallow stock|reasoncode/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.response_contract_rejected;
  }
  if (/injected (checkpoint|upsert) failure/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.storage_failure;
  }
  if (/sqlite|database is locked|disk i\/o|constraint failed/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.storage_failure;
  }
  if (/tally returned|tally reported/.test(normalized)) {
    return SYNC_FAILURE_SUMMARIES.tally_reported_error;
  }
  return SYNC_FAILURE_SUMMARIES.unexpected_sync_failure;
}

function mapAppErrorCode(code: string): SyncFailureSummary | null {
  switch (code) {
    case ErrorCodes.SYNC_CANCELLED:
      return SYNC_FAILURE_SUMMARIES.sync_cancelled;
    case ErrorCodes.SYNC_CONFLICT:
      return SYNC_FAILURE_SUMMARIES.sync_conflict;
    case ErrorCodes.VALIDATION_ERROR:
      return SYNC_FAILURE_SUMMARIES.validation_error;
    case ErrorCodes.READ_ONLY_VIOLATION:
      return SYNC_FAILURE_SUMMARIES.read_only_violation;
    default:
      return null;
  }
}

export function normalizeSyncFailure(error: unknown): NormalizedSyncFailure {
  if (isAppError(error)) {
    const mapped = mapAppErrorCode(error.code);
    return {
      failureCode: error.code,
      failureSummary: mapped ?? classifyMessage(error.message),
    };
  }
  if (error instanceof Error) {
    return {
      failureCode: ErrorCodes.SERVICE_UNAVAILABLE,
      failureSummary: classifyMessage(error.message),
    };
  }
  return {
    failureCode: ErrorCodes.SERVICE_UNAVAILABLE,
    failureSummary: SYNC_FAILURE_SUMMARIES.unexpected_sync_failure,
  };
}

/** Progress surfaces may show a bounded sanitized hint; never raw exception text. */
export function sanitizeSyncProgressError(error: unknown): string {
  const normalized = normalizeSyncFailure(error);
  return sanitizePersistentText(normalized.failureSummary, 120);
}
