import { defaultConfig, type ConnectorConfig } from './defaults.js';

const LOG_LEVELS = new Set(['debug', 'info', 'warn', 'error']);
const ENVS = new Set(['development', 'production', 'test']);

function parsePort(value: string | undefined, fallback: number): number {
  if (value === undefined) return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
    throw new Error(`Invalid port value: ${value}`);
  }
  return parsed;
}

function parsePositiveInt(value: string | undefined, fallback: number): number {
  if (value === undefined) return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`Invalid integer value: ${value}`);
  }
  return parsed;
}

function parseBoolean(value: string | undefined, fallback: boolean): boolean {
  if (value === undefined) return fallback;
  const normalized = value.trim().toLowerCase();
  if (normalized === 'true' || normalized === '1') return true;
  if (normalized === 'false' || normalized === '0') return false;
  throw new Error(`Invalid boolean value: ${value}`);
}

function parseRatio(value: string | undefined, fallback: number): number {
  if (value === undefined) return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 1) {
    throw new Error(`Invalid ratio value: ${value}`);
  }
  return parsed;
}

function parseLogLevel(value: string | undefined, fallback: ConnectorConfig['logLevel']) {
  if (value === undefined) return fallback;
  if (!LOG_LEVELS.has(value)) {
    throw new Error(`Invalid log level: ${value}`);
  }
  return value as ConnectorConfig['logLevel'];
}

function parseEnv(value: string | undefined, fallback: ConnectorConfig['env']) {
  if (value === undefined) return fallback;
  if (!ENVS.has(value)) {
    throw new Error(`Invalid environment: ${value}`);
  }
  return value as ConnectorConfig['env'];
}

export function loadConfig(overrides: Partial<ConnectorConfig> = {}): ConnectorConfig {
  const config: ConnectorConfig = {
    env: parseEnv(process.env.NODE_ENV, defaultConfig.env),
    host: process.env.BUDCOM_CONNECTOR_HOST ?? defaultConfig.host,
    port: parsePort(process.env.BUDCOM_CONNECTOR_PORT, defaultConfig.port),
    logLevel: parseLogLevel(process.env.BUDCOM_LOG_LEVEL, defaultConfig.logLevel),
    tallyHost: process.env.BUDCOM_TALLY_HOST ?? defaultConfig.tallyHost,
    tallyPort: parsePort(process.env.BUDCOM_TALLY_PORT, defaultConfig.tallyPort),
    tallyTimeoutMs: parsePositiveInt(
      process.env.BUDCOM_TALLY_TIMEOUT_MS,
      defaultConfig.tallyTimeoutMs,
    ),
    tallyPoolMaxConnections: parsePositiveInt(
      process.env.BUDCOM_TALLY_POOL_MAX,
      defaultConfig.tallyPoolMaxConnections,
    ),
    tallyRetryMaxAttempts: parsePositiveInt(
      process.env.BUDCOM_TALLY_RETRY_MAX,
      defaultConfig.tallyRetryMaxAttempts,
    ),
    tallyRetryBaseDelayMs: parsePositiveInt(
      process.env.BUDCOM_TALLY_RETRY_BASE_MS,
      defaultConfig.tallyRetryBaseDelayMs,
    ),
    tallyRetryMaxDelayMs: parsePositiveInt(
      process.env.BUDCOM_TALLY_RETRY_MAX_MS,
      defaultConfig.tallyRetryMaxDelayMs,
    ),
    tallyRetryJitterRatio: parseRatio(
      process.env.BUDCOM_TALLY_RETRY_JITTER,
      defaultConfig.tallyRetryJitterRatio,
    ),
    tallyAutoReconnect: parseBoolean(
      process.env.BUDCOM_TALLY_AUTO_RECONNECT,
      defaultConfig.tallyAutoReconnect,
    ),
    tallyReconnectDelayMs: parsePositiveInt(
      process.env.BUDCOM_TALLY_RECONNECT_MS,
      defaultConfig.tallyReconnectDelayMs,
    ),
    tallySafeMode: parseBoolean(process.env.BUDCOM_TALLY_SAFE_MODE, defaultConfig.tallySafeMode),
    tallyMinRequestIntervalMs: parsePositiveInt(
      process.env.BUDCOM_TALLY_MIN_REQUEST_INTERVAL_MS,
      defaultConfig.tallyMinRequestIntervalMs,
    ),
    tallyMaxRequestBytes: parsePositiveInt(
      process.env.BUDCOM_TALLY_MAX_REQUEST_BYTES,
      defaultConfig.tallyMaxRequestBytes,
    ),
    tallyMaxResponseBytes: parsePositiveInt(
      process.env.BUDCOM_TALLY_MAX_RESPONSE_BYTES,
      defaultConfig.tallyMaxResponseBytes,
    ),
    tallyCircuitBreakerEnabled: parseBoolean(
      process.env.BUDCOM_TALLY_CIRCUIT_BREAKER,
      defaultConfig.tallyCircuitBreakerEnabled,
    ),
    tallyCircuitBreakerFailureThreshold: parsePositiveInt(
      process.env.BUDCOM_TALLY_CIRCUIT_BREAKER_THRESHOLD,
      defaultConfig.tallyCircuitBreakerFailureThreshold,
    ),
    tallyCircuitBreakerCooldownMs: parsePositiveInt(
      process.env.BUDCOM_TALLY_CIRCUIT_BREAKER_COOLDOWN_MS,
      defaultConfig.tallyCircuitBreakerCooldownMs,
    ),
    tallyRequestAuditEnabled: parseBoolean(
      process.env.BUDCOM_TALLY_REQUEST_AUDIT,
      defaultConfig.tallyRequestAuditEnabled,
    ),
    tallyRequestAuditPath:
      process.env.BUDCOM_TALLY_REQUEST_AUDIT_PATH ?? defaultConfig.tallyRequestAuditPath,
    databasePath: process.env.BUDCOM_DATABASE_PATH ?? defaultConfig.databasePath,
    gracefulShutdownMs: parsePositiveInt(
      process.env.BUDCOM_SHUTDOWN_MS,
      defaultConfig.gracefulShutdownMs,
    ),
    connectorVersion: defaultConfig.connectorVersion,
    schemaVersion: defaultConfig.schemaVersion,
    sessionTtlMs: parsePositiveInt(
      process.env.BUDCOM_SESSION_TTL_MS,
      defaultConfig.sessionTtlMs,
    ),
    ...overrides,
  };

  if (config.port < 1 || config.port > 65535) {
    throw new Error(`Invalid port value: ${config.port}`);
  }
  if (config.tallyPort < 1 || config.tallyPort > 65535) {
    throw new Error(`Invalid port value: ${config.tallyPort}`);
  }

  return config;
}
