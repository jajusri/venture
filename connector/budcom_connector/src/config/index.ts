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
    databasePath: process.env.BUDCOM_DATABASE_PATH ?? defaultConfig.databasePath,
    gracefulShutdownMs: parsePositiveInt(
      process.env.BUDCOM_SHUTDOWN_MS,
      defaultConfig.gracefulShutdownMs,
    ),
    connectorVersion: defaultConfig.connectorVersion,
    schemaVersion: defaultConfig.schemaVersion,
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
