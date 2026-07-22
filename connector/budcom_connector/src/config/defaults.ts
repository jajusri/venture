export const CONNECTOR_VERSION = '0.3.1';
export const SCHEMA_VERSION = '1.0.0';

export interface ConnectorConfig {
  readonly env: 'development' | 'production' | 'test';
  readonly host: string;
  readonly port: number;
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
  readonly databasePath: string;
  readonly gracefulShutdownMs: number;
  readonly connectorVersion: string;
  readonly schemaVersion: string;
}

export const defaultConfig: ConnectorConfig = {
  env: 'development',
  host: '0.0.0.0',
  port: 8080,
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
  databasePath: './data/budcom-connector.db',
  gracefulShutdownMs: 10_000,
  connectorVersion: CONNECTOR_VERSION,
  schemaVersion: SCHEMA_VERSION,
};
