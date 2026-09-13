import { isAppError } from '../errors/app-error.js';

export const AUDIT_ERROR_REASON_CODES = {
  transport_timeout: 'transport_timeout',
  transport_unavailable: 'transport_unavailable',
  circuit_open: 'circuit_open',
  policy_rejected: 'policy_rejected',
  xml_validation_rejected: 'xml_validation_rejected',
  read_only_violation: 'read_only_violation',
  tally_reported_error: 'tally_reported_error',
  request_blocked: 'request_blocked',
  unexpected_failure: 'unexpected_failure',
} as const;

export type AuditErrorReasonCode =
  (typeof AUDIT_ERROR_REASON_CODES)[keyof typeof AUDIT_ERROR_REASON_CODES];

export function normalizeAuditErrorReason(
  outcome: 'intent' | 'sent' | 'failed' | 'blocked',
  error?: unknown,
): AuditErrorReasonCode | undefined {
  if (outcome === 'sent' || outcome === 'intent') {
    return undefined;
  }
  if (outcome === 'blocked') {
    return AUDIT_ERROR_REASON_CODES.request_blocked;
  }

  const message =
    error instanceof Error
      ? error.message.toLowerCase()
      : typeof error === 'string'
        ? error.toLowerCase()
        : '';

  if (isAppError(error) && error.code === 'READ_ONLY_VIOLATION') {
    return AUDIT_ERROR_REASON_CODES.read_only_violation;
  }
  if (/circuit breaker/.test(message)) {
    return AUDIT_ERROR_REASON_CODES.circuit_open;
  }
  if (/policy|read-only|blocked|not allowed|denied/.test(message)) {
    return AUDIT_ERROR_REASON_CODES.policy_rejected;
  }
  if (/xml|validation|well-formed|envelope|prohibited|request missing/.test(message)) {
    return AUDIT_ERROR_REASON_CODES.xml_validation_rejected;
  }
  if (/timed out|timeout after/.test(message)) {
    return AUDIT_ERROR_REASON_CODES.transport_timeout;
  }
  if (/connection failed|econnrefused|fetch failed|unavailable/.test(message)) {
    return AUDIT_ERROR_REASON_CODES.transport_unavailable;
  }
  if (/lineerror|explicit error|tally returned/.test(message)) {
    return AUDIT_ERROR_REASON_CODES.tally_reported_error;
  }
  return AUDIT_ERROR_REASON_CODES.unexpected_failure;
}
