import {
  defaultConfig,
  SYNC_RUN_HISTORY_MAX_AGE_DAYS_LIMIT,
  SYNC_RUN_HISTORY_MAX_AGE_DAYS_MIN,
  SYNC_RUN_HISTORY_MAX_COUNT_LIMIT,
  SYNC_RUN_HISTORY_MAX_COUNT_MIN,
  TALLY_REQUEST_AUDIT_MAX_BYTES_LIMIT,
  TALLY_REQUEST_AUDIT_MAX_BYTES_MIN,
  TALLY_REQUEST_AUDIT_MAX_FILES_LIMIT,
  TALLY_REQUEST_AUDIT_MAX_FILES_MIN,
  type ConnectorConfig,
} from './defaults.js';
import {
  getNetworkExposureWarning,
  isLanModePolicySatisfied,
  parseConnectorBindHost,
} from './network-binding.js';

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

function parseBoundedInt(
  value: string | undefined,
  fallback: number,
  min: number,
  max: number,
  label: string,
): number {
  if (value === undefined) return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < min || parsed > max) {
    throw new Error(`Invalid ${label}: ${value}`);
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

function parseConnectorHost(value: string | undefined, fallback: string): ReturnType<typeof parseConnectorBindHost> {
  return parseConnectorBindHost(value, fallback);
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
  const bindHost = parseConnectorHost(process.env.BUDCOM_CONNECTOR_HOST, defaultConfig.host);
  const lanModeAcknowledged = parseBoolean(
    process.env.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED,
    defaultConfig.lanModeAcknowledged,
  );
  const config: ConnectorConfig = {
    env: parseEnv(process.env.NODE_ENV, defaultConfig.env),
    host: bindHost.host,
    port: parsePort(process.env.BUDCOM_CONNECTOR_PORT, defaultConfig.port),
    networkExposure: bindHost.exposure,
    networkExposureWarning: getNetworkExposureWarning(bindHost),
    lanModeAcknowledged,
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
    tallyRequestAuditMaxBytes: parseBoundedInt(
      process.env.BUDCOM_TALLY_REQUEST_AUDIT_MAX_BYTES,
      defaultConfig.tallyRequestAuditMaxBytes,
      TALLY_REQUEST_AUDIT_MAX_BYTES_MIN,
      TALLY_REQUEST_AUDIT_MAX_BYTES_LIMIT,
      'tally request audit max bytes',
    ),
    tallyRequestAuditMaxFiles: parseBoundedInt(
      process.env.BUDCOM_TALLY_REQUEST_AUDIT_MAX_FILES,
      defaultConfig.tallyRequestAuditMaxFiles,
      TALLY_REQUEST_AUDIT_MAX_FILES_MIN,
      TALLY_REQUEST_AUDIT_MAX_FILES_LIMIT,
      'tally request audit max files',
    ),
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
    syncRunHistoryMaxCount: parseBoundedInt(
      process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_COUNT,
      defaultConfig.syncRunHistoryMaxCount,
      SYNC_RUN_HISTORY_MAX_COUNT_MIN,
      SYNC_RUN_HISTORY_MAX_COUNT_LIMIT,
      'sync run history max count',
    ),
    syncRunHistoryMaxAgeDays: parseBoundedInt(
      process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_AGE_DAYS,
      defaultConfig.syncRunHistoryMaxAgeDays,
      SYNC_RUN_HISTORY_MAX_AGE_DAYS_MIN,
      SYNC_RUN_HISTORY_MAX_AGE_DAYS_LIMIT,
      'sync run history max age days',
    ),
    ...overrides,
  };

  const effectiveBindHost = parseConnectorBindHost(config.host, defaultConfig.host);
  const resolved: ConnectorConfig = {
    ...config,
    host: effectiveBindHost.host,
    networkExposure: effectiveBindHost.exposure,
    networkExposureWarning: getNetworkExposureWarning(effectiveBindHost),
  };

  if (resolved.port < 1 || resolved.port > 65535) {
    throw new Error(`Invalid port value: ${resolved.port}`);
  }
  if (resolved.tallyPort < 1 || resolved.tallyPort > 65535) {
    throw new Error(`Invalid port value: ${resolved.tallyPort}`);
  }
  if (
    resolved.env === 'production' &&
    resolved.networkExposure === 'lan' &&
    !isLanModePolicySatisfied(effectiveBindHost, resolved.lanModeAcknowledged)
  ) {
    throw new Error(
      'Non-loopback connector bind requires BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED=true in production.',
    );
  }

  if (
    !Number.isInteger(resolved.tallyRequestAuditMaxBytes)
    || resolved.tallyRequestAuditMaxBytes < TALLY_REQUEST_AUDIT_MAX_BYTES_MIN
    || resolved.tallyRequestAuditMaxBytes > TALLY_REQUEST_AUDIT_MAX_BYTES_LIMIT
  ) {
    throw new Error(`Invalid tally request audit max bytes: ${resolved.tallyRequestAuditMaxBytes}`);
  }
  if (
    !Number.isInteger(resolved.tallyRequestAuditMaxFiles)
    || resolved.tallyRequestAuditMaxFiles < TALLY_REQUEST_AUDIT_MAX_FILES_MIN
    || resolved.tallyRequestAuditMaxFiles > TALLY_REQUEST_AUDIT_MAX_FILES_LIMIT
  ) {
    throw new Error(`Invalid tally request audit max files: ${resolved.tallyRequestAuditMaxFiles}`);
  }

  if (
    !Number.isInteger(resolved.syncRunHistoryMaxCount)
    || resolved.syncRunHistoryMaxCount < SYNC_RUN_HISTORY_MAX_COUNT_MIN
    || resolved.syncRunHistoryMaxCount > SYNC_RUN_HISTORY_MAX_COUNT_LIMIT
  ) {
    throw new Error(`Invalid sync run history max count: ${resolved.syncRunHistoryMaxCount}`);
  }
  if (
    !Number.isInteger(resolved.syncRunHistoryMaxAgeDays)
    || resolved.syncRunHistoryMaxAgeDays < SYNC_RUN_HISTORY_MAX_AGE_DAYS_MIN
    || resolved.syncRunHistoryMaxAgeDays > SYNC_RUN_HISTORY_MAX_AGE_DAYS_LIMIT
  ) {
    throw new Error(`Invalid sync run history max age days: ${resolved.syncRunHistoryMaxAgeDays}`);
  }

  return resolved;
}
