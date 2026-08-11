import type { ConfigValidationError, ConnectorBindMode, DesktopConfigV1, DesktopLogLevel } from './desktop-config-schema.js';
import {
  buildConnectorBaseUrl,
  parseConnectorHostPort,
  validateDesktopConfig,
} from './desktop-config-schema.js';
import { isLoopbackConnectorHost } from './connector-network-binding.js';
import type { ConnectorLifecycleConfig } from './connector-lifecycle-types.js';
import { resolveConnectorLifecycleConfig } from './connector-lifecycle-config.js';

export type ConfigSource = 'environment' | 'persisted' | 'default';

export interface ResolvedDesktopConfig {
  readonly effective: DesktopConfigV1;
  readonly persisted: DesktopConfigV1;
  readonly sources: Partial<Record<keyof DesktopConfigV1, ConfigSource>>;
  readonly connectorBaseUrl: string;
  readonly lifecycleConfig: ConnectorLifecycleConfig;
  /**
   * False when the persisted desktop-config.json failed schema validation and `effective`/
   * `persisted` above are therefore built-in defaults, not the user's actual configuration.
   * Callers that must never silently operate on substituted defaults (see runtime-integrity
   * enforcement) should treat `configValid: false` as a CONNECTOR_CONFIG_SCHEMA_MISMATCH
   * blocked state rather than proceeding as if the defaults were the real request.
   */
  readonly configValid: boolean;
  readonly configValidationErrors: readonly ConfigValidationError[];
}

function readEnvBoolean(name: string): boolean | undefined {
  const value = process.env[name];
  if (value === undefined) {
    return undefined;
  }
  return value !== 'false' && value !== '0';
}

function readEnvNumber(name: string): number | undefined {
  const value = process.env[name];
  if (!value) {
    return undefined;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function readEnvLogLevel(name: string): DesktopLogLevel | undefined {
  const value = process.env[name]?.toLowerCase();
  if (value === 'debug' || value === 'info' || value === 'warn' || value === 'error') {
    return value;
  }
  return undefined;
}

function readEnvBindMode(name: string): ConnectorBindMode | undefined {
  const value = process.env[name]?.toLowerCase();
  if (value === 'local-only' || value === 'trusted-lan') {
    return value;
  }
  return undefined;
}

export function applyEnvironmentOverrides(
  base: DesktopConfigV1,
): { config: DesktopConfigV1; sources: Partial<Record<keyof DesktopConfigV1, ConfigSource>> } {
  const sources: Partial<Record<keyof DesktopConfigV1, ConfigSource>> = {};
  let config: DesktopConfigV1 = { ...base };

  const envUrl = process.env.BUDCOM_CONNECTOR_URL;
  let urlDerivedPort: number | undefined;
  if (envUrl) {
    const parsed = parseConnectorHostPort(envUrl);
    if (parsed) {
      config = {
        ...config,
        connectorHost: parsed.host,
        connectorPort: parsed.port,
        connectorBindMode: isLoopbackConnectorHost(parsed.host) ? 'local-only' : 'trusted-lan',
      };
      sources.connectorHost = 'environment';
      sources.connectorPort = 'environment';
      sources.connectorBindMode = 'environment';
      urlDerivedPort = parsed.port;
    }
  }

  const envHost = process.env.BUDCOM_CONNECTOR_HOST;
  if (envHost) {
    config = {
      ...config,
      connectorHost: envHost,
      connectorBindMode: isLoopbackConnectorHost(envHost) ? 'local-only' : 'trusted-lan',
    };
    sources.connectorHost = 'environment';
    sources.connectorBindMode = 'environment';
  }

  const envPort = readEnvNumber('BUDCOM_CONNECTOR_PORT');
  if (envPort !== undefined && urlDerivedPort === undefined) {
    config = { ...config, connectorPort: envPort };
    sources.connectorPort = 'environment';
  }

  const bindMode = readEnvBindMode('BUDCOM_CONNECTOR_BIND_MODE');
  if (bindMode) {
    config = {
      ...config,
      connectorBindMode: bindMode,
      connectorHost: bindMode === 'local-only' ? '127.0.0.1' : config.connectorHost,
    };
    sources.connectorBindMode = 'environment';
    if (bindMode === 'local-only') {
      sources.connectorHost = 'environment';
    }
  }

  const autoStart = readEnvBoolean('BUDCOM_CONNECTOR_AUTO_START');
  if (autoStart !== undefined) {
    config = { ...config, autoStartConnector: autoStart };
    sources.autoStartConnector = 'environment';
  }

  const pollMs = readEnvNumber('BUDCOM_HEALTH_POLL_MS');
  if (pollMs !== undefined) {
    config = { ...config, healthPollIntervalMs: pollMs };
    sources.healthPollIntervalMs = 'environment';
  }

  const startupTimeout = readEnvNumber('BUDCOM_STARTUP_TIMEOUT_MS');
  if (startupTimeout !== undefined) {
    config = { ...config, startupTimeoutMs: startupTimeout };
    sources.startupTimeoutMs = 'environment';
  }

  const shutdownGrace = readEnvNumber('BUDCOM_SHUTDOWN_GRACE_MS');
  if (shutdownGrace !== undefined) {
    config = { ...config, shutdownGraceMs: shutdownGrace };
    sources.shutdownGraceMs = 'environment';
  }

  const maxRestarts = readEnvNumber('BUDCOM_MAX_RESTART_ATTEMPTS');
  if (maxRestarts !== undefined) {
    config = { ...config, maxRestartAttempts: maxRestarts };
    sources.maxRestartAttempts = 'environment';
  }

  const reconnectDelay = readEnvNumber('BUDCOM_RECONNECT_BASE_DELAY_MS');
  if (reconnectDelay !== undefined) {
    config = { ...config, reconnectBaseDelayMs: reconnectDelay };
    sources.reconnectBaseDelayMs = 'environment';
  }

  const logLevel = readEnvLogLevel('BUDCOM_LOG_LEVEL');
  if (logLevel) {
    config = { ...config, logLevel };
    sources.logLevel = 'environment';
  }

  const tallyHost = process.env.BUDCOM_TALLY_HOST;
  if (tallyHost) {
    config = { ...config, tallyHost };
    sources.tallyHost = 'environment';
  }

  const tallyPort = readEnvNumber('BUDCOM_TALLY_PORT');
  if (tallyPort !== undefined) {
    config = { ...config, tallyPort };
    sources.tallyPort = 'environment';
  }

  return { config, sources };
}

export interface ResolveDesktopConfigOptions {
  readonly isPackaged?: boolean;
  readonly resourcesPath?: string;
  readonly connectorDatabaseDir?: string;
  readonly connectorTallyAuditPath?: string;
  readonly connectorTransportIdentityDir?: string;
  readonly connectorId?: string;
  /** Private Removable Storage mode only — see connector-lifecycle-config.ts. */
  readonly privateStorageExpectedVaultId?: string;
  readonly privateStorageMarkerPath?: string;
}

export function resolveDesktopConfig(
  persisted: DesktopConfigV1,
  defaults: DesktopConfigV1,
  options: ResolveDesktopConfigOptions = {},
): ResolvedDesktopConfig {
  const validatedPersisted = validateDesktopConfig(persisted);
  const safePersisted = validatedPersisted.ok && validatedPersisted.config
    ? validatedPersisted.config
    : defaults;

  const envApplied = applyEnvironmentOverrides(safePersisted);
  const effective = envApplied.config;
  const connectorBaseUrl = buildConnectorBaseUrl(effective.connectorHost, effective.connectorPort);

  const lifecycleConfig = resolveConnectorLifecycleConfig({
    connectorBaseUrl,
    connectorBindMode: effective.connectorBindMode,
    connectorHost: effective.connectorHost,
    connectorPort: effective.connectorPort,
    autoStart: effective.autoStartConnector,
    healthPollIntervalMs: effective.healthPollIntervalMs,
    startupTimeoutMs: effective.startupTimeoutMs,
    shutdownGraceMs: effective.shutdownGraceMs,
    maxRestartAttempts: effective.maxRestartAttempts,
    reconnectBaseDelayMs: effective.reconnectBaseDelayMs,
  }, {
    isPackaged: options.isPackaged ?? false,
    resourcesPath: options.resourcesPath,
    connectorDatabaseDir: options.connectorDatabaseDir,
    connectorTallyAuditPath: options.connectorTallyAuditPath,
    connectorTransportIdentityDir: options.connectorTransportIdentityDir,
    connectorId: options.connectorId,
    privateStorageExpectedVaultId: options.privateStorageExpectedVaultId,
    privateStorageMarkerPath: options.privateStorageMarkerPath,
  });

  return {
    effective,
    persisted: safePersisted,
    sources: envApplied.sources,
    connectorBaseUrl,
    lifecycleConfig,
    configValid: validatedPersisted.ok,
    configValidationErrors: validatedPersisted.errors,
  };
}

export function mergeSettingsPatch(
  current: DesktopConfigV1,
  patch: Partial<DesktopConfigV1>,
): DesktopConfigV1 {
  return validateDesktopConfig({
    ...current,
    ...patch,
    schemaVersion: current.schemaVersion,
  }).config ?? current;
}
