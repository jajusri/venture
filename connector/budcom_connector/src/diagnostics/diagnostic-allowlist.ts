import type { ExtractorDiagnostics } from '../extraction/core/types.js';
import type { TallyDiagnosticsSnapshot } from '../tally/core/types.js';
import {
  sanitizePersistentText,
  PERSISTENT_TEXT_LIMITS,
} from '../infrastructure/privacy/persistent-text-sanitizer.js';

export const CONNECTOR_DIAGNOSTIC_ALLOWLIST_VERSION = 1 as const;

export const CONNECTOR_DIAGNOSTIC_LIMITS = {
  maxStringLength: PERSISTENT_TEXT_LIMITS.maxStringLength,
  maxExtractors: 20,
} as const;

export function sanitizeConnectorDiagnosticText(
  value: string,
  maxLength: number = CONNECTOR_DIAGNOSTIC_LIMITS.maxStringLength,
): string {
  return sanitizePersistentText(value, maxLength);
}
export interface SafeConnectorErrorDiagnostic {
  readonly code: string;
  readonly category: string;
  readonly message: string;
  readonly retryable: boolean;
}

export interface SafeExtractorDiagnostic {
  readonly entityType: ExtractorDiagnostics['entityType'];
  readonly lastExtractedAt?: string;
  readonly lastDurationMs?: number;
  readonly lastItemCount?: number;
  readonly lastErrorAt?: string;
  readonly lastErrorMessage?: string;
  readonly totalExtractions: number;
  readonly failedExtractions: number;
}

export interface SafeConnectionDiagnostic {
  readonly state: TallyDiagnosticsSnapshot['state'];
  readonly host: string;
  readonly port: number;
  readonly lastSuccessfulPingAt?: string;
  readonly lastErrorAt?: string;
  readonly lastErrorCode?: string;
  readonly lastErrorMessage?: string;
  readonly totalRequests: number;
  readonly failedRequests: number;
  readonly reconnectAttempts: number;
  readonly averageLatencyMs: number;
  readonly poolActiveConnections: number;
  readonly poolWaitingRequests: number;
  readonly safeMode: boolean;
  readonly circuitState: TallyDiagnosticsSnapshot['circuitState'];
  readonly lastRequest?: SafeLastRequestDiagnostic;
  readonly runtimeLimits: SafeRuntimeLimitsDiagnostic;
}

export interface SafeLastRequestDiagnostic {
  readonly correlationId: string;
  readonly collectionId?: string;
  readonly reportId?: string;
  readonly sentAt: string;
  readonly outcome?: string;
}

export interface SafeRuntimeLimitsDiagnostic {
  readonly poolMaxConnections: number;
  readonly retryMaxAttempts: number;
  readonly minRequestIntervalMs: number;
  readonly maxRequestBytes: number;
  readonly maxResponseBytes: number;
  readonly circuitBreakerEnabled: boolean;
  readonly timeoutMs: number;
}


export function mapUnknownConnectorError(error: unknown): SafeConnectorErrorDiagnostic {  if (error instanceof Error) {
    const code = error.name && /^[A-Z0-9_]+$/.test(error.name) ? error.name : 'UNKNOWN_ERROR';
    return {
      code,
      category: 'internal',
      message: sanitizeConnectorDiagnosticText('An unexpected error occurred.'),
      retryable: false,
    };
  }
  return {
    code: 'UNKNOWN_ERROR',
    category: 'internal',
    message: 'An unexpected error occurred.',
    retryable: false,
  };
}

export function sanitizeExtractorDiagnostic(entry: ExtractorDiagnostics): SafeExtractorDiagnostic {
  return {
    entityType: entry.entityType,
    lastExtractedAt: entry.lastExtractedAt,
    lastDurationMs: entry.lastDurationMs,
    lastItemCount: entry.lastItemCount,
    lastErrorAt: entry.lastErrorAt,
    lastErrorMessage: entry.lastErrorMessage
      ? sanitizeConnectorDiagnosticText(entry.lastErrorMessage)
      : undefined,
    totalExtractions: entry.totalExtractions,
    failedExtractions: entry.failedExtractions,
  };
}

export function sanitizeExtractorDiagnostics(
  entries: readonly ExtractorDiagnostics[],
): readonly SafeExtractorDiagnostic[] {
  return entries.slice(0, CONNECTOR_DIAGNOSTIC_LIMITS.maxExtractors).map(sanitizeExtractorDiagnostic);
}

export function sanitizeConnectionDiagnostic(snapshot: TallyDiagnosticsSnapshot): SafeConnectionDiagnostic {
  return {
    state: snapshot.state,
    host: snapshot.host,
    port: snapshot.port,
    lastSuccessfulPingAt: snapshot.lastSuccessfulPingAt,
    lastErrorAt: snapshot.lastErrorAt,
    lastErrorCode: snapshot.lastErrorCode
      ? sanitizeConnectorDiagnosticText(snapshot.lastErrorCode, 120)
      : undefined,
    lastErrorMessage: snapshot.lastErrorMessage
      ? sanitizeConnectorDiagnosticText(snapshot.lastErrorMessage)
      : undefined,
    totalRequests: snapshot.totalRequests,
    failedRequests: snapshot.failedRequests,
    reconnectAttempts: snapshot.reconnectAttempts,
    averageLatencyMs: snapshot.averageLatencyMs,
    poolActiveConnections: snapshot.poolActiveConnections,
    poolWaitingRequests: snapshot.poolWaitingRequests,
    safeMode: snapshot.safeMode,
    circuitState: snapshot.circuitState,
    lastRequest: snapshot.lastRequest
      ? {
          correlationId: snapshot.lastRequest.correlationId,
          collectionId: snapshot.lastRequest.collectionId,
          reportId: snapshot.lastRequest.reportId,
          sentAt: snapshot.lastRequest.sentAt,
          outcome: snapshot.lastRequest.outcome
            ? sanitizeConnectorDiagnosticText(snapshot.lastRequest.outcome, 120)
            : undefined,
        }
      : undefined,
    runtimeLimits: {
      poolMaxConnections: snapshot.runtimeLimits.poolMaxConnections,
      retryMaxAttempts: snapshot.runtimeLimits.retryMaxAttempts,
      minRequestIntervalMs: snapshot.runtimeLimits.minRequestIntervalMs,
      maxRequestBytes: snapshot.runtimeLimits.maxRequestBytes,
      maxResponseBytes: snapshot.runtimeLimits.maxResponseBytes,
      circuitBreakerEnabled: snapshot.runtimeLimits.circuitBreakerEnabled,
      timeoutMs: snapshot.runtimeLimits.timeoutMs,
    },
  };
}
