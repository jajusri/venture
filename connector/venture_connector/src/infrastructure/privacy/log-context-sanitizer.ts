import { isAppError } from '../errors/app-error.js';
import { sanitizePersistentText, neutralizePersistentControlCharacters } from './persistent-text-sanitizer.js';

const REDACTED = '[REDACTED]';

export const APPROVED_LOG_CONTEXT_KEYS = new Set([
  'service',
  'component',
  'correlationId',
  'operationId',
  'entityType',
  'itemCount',
  'durationMs',
  'byteLength',
  'contractStatus',
  'reasonCode',
  'code',
  'statusCode',
  'feature',
  'sessionId',
  'companyId',
  'resourceKind',
  'syncRunId',
  'recoveredCount',
  'migrationStatus',
  'issueCount',
  'attempt',
  'maxResponseBytes',
  'responseBytes',
  'timeoutMs',
  'tallyReachable',
  'policyDecision',
  'safeMode',
  'circuitState',
  'requestBytes',
  'collectionId',
  'reportId',
  'signal',
  'retryable',
  'status',
  'running',
  'ready',
  'event',
  'httpMethod',
  'httpRoute',
  'httpPath',
  'remoteClientIp',
  'bindHost',
  'bindPort',
  'outcome',
  'vouchersExtracted',
  'vouchersPersisted',
  'droppedVouchers',
  'rollbackStatus',
  'promoted',
]);

const SENSITIVE_KEY_PATTERN =
  /(companyname|company_name|databasepath|database_path|filepath|file_path|password|passwd|secret|credential|authorization|licen[cs]e|stack|cause|rawxml|requestbody|responsebody|xml|details)/i;

const MAX_ARRAY_ITEMS = 20;

export type SafeLogContextValue = string | number | boolean | null;

export interface SafeLogContext {
  readonly [key: string]: SafeLogContextValue;
}

function sanitizeIdentifier(value: string): string {
  return neutralizePersistentControlCharacters(value);
}

function sanitizeContextString(key: string, value: string): string {
  if (
    key === 'correlationId' ||
    key === 'operationId' ||
    key === 'sessionId' ||
    key === 'syncRunId' ||
    key === 'collectionId' ||
    key === 'reportId' ||
    key === 'code' ||
    key === 'reasonCode' ||
    key === 'feature' ||
    key === 'component' ||
    key === 'signal' ||
    key === 'status' ||
    key === 'event' ||
    key === 'httpMethod' ||
    key === 'httpRoute' ||
    key === 'httpPath' ||
    key === 'remoteClientIp' ||
    key === 'bindHost' ||
    key === 'outcome' ||
    key === 'rollbackStatus' ||
    key === 'resourceKind' ||
    key === 'migrationStatus' ||
    key === 'contractStatus' ||
    key === 'name'
  ) {
    return sanitizeIdentifier(value);
  }
  return sanitizePersistentText(value);
}
function sanitizePrimitive(value: unknown, key?: string): SafeLogContextValue {
  if (value === null) {
    return null;
  }
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) {
      return REDACTED;
    }
    return value;
  }
  if (typeof value === 'string') {
    return key ? sanitizeContextString(key, value) : sanitizePersistentText(value);
  }
  if (typeof value === 'bigint') {
    return Number(value);
  }
  return REDACTED;
}

function sanitizeArray(values: readonly unknown[]): readonly SafeLogContextValue[] {
  return values.slice(0, MAX_ARRAY_ITEMS).map((item) => {
    if (item === null || typeof item === 'boolean' || typeof item === 'number' || typeof item === 'string') {
      return sanitizePrimitive(item);
    }
    return REDACTED;
  });
}

export function sanitizeLogContext(
  context: Record<string, unknown> | undefined,
  seen: WeakSet<object> = new WeakSet(),
): SafeLogContext | undefined {
  if (!context) {
    return undefined;
  }
  if (seen.has(context)) {
    return { recursive: REDACTED };
  }
  seen.add(context);

  const output: Record<string, SafeLogContextValue> = {};
  for (const [key, value] of Object.entries(context)) {
    if (SENSITIVE_KEY_PATTERN.test(key)) {
      output[key] = REDACTED;
      continue;
    }
    if (!APPROVED_LOG_CONTEXT_KEYS.has(key)) {
      continue;
    }
    if (value instanceof Error || isAppError(value)) {
      output[key] = REDACTED;
      continue;
    }
    if (Array.isArray(value)) {
      const sanitized = sanitizeArray(value);
      output[key] = sanitizePersistentText(JSON.stringify(sanitized));
      continue;
    }
    if (value && typeof value === 'object') {
      output[key] = REDACTED;
      continue;
    }
    output[key] = sanitizePrimitive(value, key);
  }
  return output;
}

export function sanitizeLogMessage(message: string): string {
  return sanitizePersistentText(message);
}

export function sanitizeLogDetails(
  details: Record<string, unknown> | undefined,
): SafeLogContext | undefined {
  if (!details) {
    return undefined;
  }
  const output: Record<string, SafeLogContextValue> = {};
  for (const [key, value] of Object.entries(details)) {
    if (SENSITIVE_KEY_PATTERN.test(key)) {
      output[key] = REDACTED;
      continue;
    }
    if (value instanceof Error) {
      output[key] = REDACTED;
      continue;
    }
    if (Array.isArray(value)) {
      output[key] = sanitizePersistentText(JSON.stringify(sanitizeArray(value)));
      continue;
    }
    if (value && typeof value === 'object') {
      output[key] = REDACTED;
      continue;
    }
    output[key] = sanitizePrimitive(value, key);
  }
  return output;
}
