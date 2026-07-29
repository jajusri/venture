import type { ConnectorNetworkExposure } from './network-binding.js';

export const CONNECTOR_VERSION = '0.4.0';
export const SCHEMA_VERSION = '1.0.0';

export interface ConnectorConfig {
  readonly env: 'development' | 'production' | 'test';
  /** Connector API bind host (not Tally host). Defaults to loopback-only. */
  readonly host: string;
  readonly port: number;
  readonly networkExposure: ConnectorNetworkExposure;
  readonly networkExposureWarning: string | null;
  /** Explicit operator acknowledgement for non-loopback bind without authentication. */
  readonly lanModeAcknowledged: boolean;
  readonly logLevel: 'debug' | 'info' | 'warn' | 'error';
  readonly tallyHost: string;
  readonly tallyPort: number;
  readonly tallyTimeoutMs: number;
  readonly tallyPoolMaxConnections: number;
  readonly tallyRetryMaxAttempts: number;
  readonly tallyRetryBaseDelayMs: number;
  readonly tallyRetryMaxDelayMs: number;
  readonly tallyRetryJitterRatio: number;
  readonly tallyAutoReconnect: boolean;
  readonly tallyReconnectDelayMs: number;
  readonly tallySafeMode: boolean;
  readonly tallyMinRequestIntervalMs: number;
  readonly tallyMaxRequestBytes: number;
  readonly tallyMaxResponseBytes: number;
  readonly tallyCircuitBreakerEnabled: boolean;
  readonly tallyCircuitBreakerFailureThreshold: number;
  readonly tallyCircuitBreakerCooldownMs: number;
  readonly tallyRequestAuditEnabled: boolean;
  readonly tallyRequestAuditPath: string;
  readonly tallyRequestAuditMaxBytes: number;
  readonly tallyRequestAuditMaxFiles: number;
  readonly databasePath: string;
  readonly gracefulShutdownMs: number;
  readonly connectorVersion: string;
  readonly schemaVersion: string;
  /** Maximum age of a connector session selection before SESSION_EXPIRED. */
  readonly sessionTtlMs: number;
  /** Maximum terminal sync-run history rows retained per company and resource kind. */
  readonly syncRunHistoryMaxCount: number;
  /** Maximum age in days for non-protected terminal sync-run rows outside the count window. */
  readonly syncRunHistoryMaxAgeDays: number;
  /** Bounded per-launch identifier exposed in /health diagnostics when set by desktop supervisor. */
  readonly startupCorrelationId: string | null;
}

export const TALLY_REQUEST_AUDIT_MAX_BYTES_DEFAULT = 10 * 1024 * 1024;
export const TALLY_REQUEST_AUDIT_MAX_FILES_DEFAULT = 5;
export const TALLY_REQUEST_AUDIT_MAX_BYTES_MIN = 64 * 1024;
export const TALLY_REQUEST_AUDIT_MAX_BYTES_LIMIT = 100 * 1024 * 1024;
export const TALLY_REQUEST_AUDIT_MAX_FILES_MIN = 1;
export const TALLY_REQUEST_AUDIT_MAX_FILES_LIMIT = 20;

export const SYNC_RUN_HISTORY_MAX_COUNT_DEFAULT = 100;
export const SYNC_RUN_HISTORY_MAX_COUNT_MIN = 20;
export const SYNC_RUN_HISTORY_MAX_COUNT_LIMIT = 500;
export const SYNC_RUN_HISTORY_MAX_AGE_DAYS_DEFAULT = 90;
export const SYNC_RUN_HISTORY_MAX_AGE_DAYS_MIN = 7;
export const SYNC_RUN_HISTORY_MAX_AGE_DAYS_LIMIT = 365;

export const defaultConfig: ConnectorConfig = {
  env: 'development',
  host: '127.0.0.1',
  port: 8080,
  networkExposure: 'loopback',
  networkExposureWarning: null,
  lanModeAcknowledged: false,
  logLevel: 'info',
  tallyHost: 'localhost',
  tallyPort: 9000,
  tallyTimeoutMs: 120_000,
  tallyPoolMaxConnections: 1,
  tallyRetryMaxAttempts: 1,
  tallyRetryBaseDelayMs: 500,
  tallyRetryMaxDelayMs: 8_000,
  tallyRetryJitterRatio: 0.2,
  tallyAutoReconnect: true,
  tallyReconnectDelayMs: 2_000,
  tallySafeMode: true,
  tallyMinRequestIntervalMs: 2_000,
  tallyMaxRequestBytes: 65_536,
  tallyMaxResponseBytes: 10_485_760,
  tallyCircuitBreakerEnabled: true,
  tallyCircuitBreakerFailureThreshold: 2,
  tallyCircuitBreakerCooldownMs: 60_000,
  tallyRequestAuditEnabled: true,
  tallyRequestAuditPath: './diagnostics/tally-request-audit.jsonl',
  tallyRequestAuditMaxBytes: TALLY_REQUEST_AUDIT_MAX_BYTES_DEFAULT,
  tallyRequestAuditMaxFiles: TALLY_REQUEST_AUDIT_MAX_FILES_DEFAULT,
  databasePath: './data/budcom-connector.db',
  gracefulShutdownMs: 10_000,
  connectorVersion: CONNECTOR_VERSION,
  schemaVersion: SCHEMA_VERSION,
  sessionTtlMs: 8 * 60 * 60 * 1000,
  syncRunHistoryMaxCount: SYNC_RUN_HISTORY_MAX_COUNT_DEFAULT,
  syncRunHistoryMaxAgeDays: SYNC_RUN_HISTORY_MAX_AGE_DAYS_DEFAULT,
  startupCorrelationId: null,
};
