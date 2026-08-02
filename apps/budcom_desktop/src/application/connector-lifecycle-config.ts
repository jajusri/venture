import * as fs from 'node:fs';
import * as path from 'node:path';

import type { ConnectorLifecycleConfig } from './connector-lifecycle-types.js';
import { buildConnectorBaseUrl, parseConnectorHostPort } from './desktop-config-schema.js';
import { isLoopbackConnectorHost } from './connector-network-binding.js';
import {
  buildConnectorChildEnvironment,
  resolvePackagedConnectorPaths,
  shouldSpawnConnectorViaElectronNode,
  tryResolveDevelopmentConnectorNodeExecutable,
} from './release/connector-packaged-paths.js';
import { resolvePackagedConnectorLoopbackHost } from './release/packaged-connector-network.js';
import { tryResolvePackagedConnectorNodeRuntimeWithIntegrity } from './release/packaged-node-runtime.js';

const DEFAULT_PORT = 8080;

function isPrivateIpv4Literal(value: string): boolean {
  const parts = value.split('.');
  if (parts.length !== 4) {
    return false;
  }
  const bytes = parts.map((part) => Number.parseInt(part, 10));
  if (bytes.some((byte) => !Number.isInteger(byte) || byte < 0 || byte > 255)) {
    return false;
  }
  const [a, b] = bytes;
  if (a === undefined || b === undefined) {
    return false;
  }
  return (
    a === 10
    || (a === 172 && b >= 16 && b <= 31)
    || (a === 192 && b === 168)
  );
}

export interface ConnectorLifecycleResolutionContext {
  readonly isPackaged?: boolean;
  readonly resourcesPath?: string;
  readonly connectorDatabaseDir?: string;
  /**
   * Desktop's stable Connector identity (from ConnectorIdentityStore), passed to the spawned
   * Connector child process as BUDCOM_CONNECTOR_ID so it never has to generate its own.
   */
  readonly connectorId?: string;
}

export function resolveConnectorLifecycleConfig(
  overrides: Partial<ConnectorLifecycleConfig> = {},
  context: ConnectorLifecycleResolutionContext = {},
): ConnectorLifecycleConfig {
  const isPackaged = context.isPackaged ?? false;
  const connectorBaseUrlInput = overrides.connectorBaseUrl ?? process.env.BUDCOM_CONNECTOR_URL ?? 'http://127.0.0.1:8080';
  const parsedHostPort = parseConnectorHostPort(connectorBaseUrlInput);
  const resolvedBindMode = overrides.connectorBindMode
    ?? (process.env.BUDCOM_CONNECTOR_BIND_MODE?.trim().toLowerCase() === 'trusted-lan'
      ? 'trusted-lan'
      : process.env.BUDCOM_CONNECTOR_BIND_MODE?.trim().toLowerCase() === 'local-only'
        ? 'local-only'
        : undefined)
    ?? (isLoopbackConnectorHost(overrides.connectorHost ?? parsedHostPort?.host ?? '127.0.0.1')
      ? 'local-only'
      : 'trusted-lan');
  const requestedHost = overrides.connectorHost ?? parsedHostPort?.host ?? '127.0.0.1';
  const lanModeAcknowledged =
    resolvedBindMode === 'trusted-lan'
    || process.env.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED?.trim().toLowerCase() === 'true';
  const connectorHost = resolvePackagedConnectorLoopbackHost(
    resolvedBindMode === 'local-only' ? '127.0.0.1' : requestedHost,
    isPackaged,
    lanModeAcknowledged,
  );
  if (resolvedBindMode === 'trusted-lan' && !isPrivateIpv4Literal(connectorHost)) {
    throw new Error('Trusted-LAN mode requires a private IPv4 connector host.');
  }
  const connectorPort = overrides.connectorPort ?? parsedHostPort?.port ?? DEFAULT_PORT;
  const connectorBaseUrl = buildConnectorBaseUrl(connectorHost, connectorPort);

  const packagedPaths = resolvePackagedConnectorPaths({
    isPackaged,
    resourcesPath: context.resourcesPath,
  });
  const defaultScript = packagedPaths.connectorEntryScript;

  let connectorExecutable = overrides.connectorExecutable ?? process.env.BUDCOM_CONNECTOR_EXECUTABLE ?? '';
  let packagedRuntimeIntegrityCategory: string | null = null;
  let developmentRuntimeError: string | null = null;
  if (!connectorExecutable) {
    if (isPackaged) {
      const resolvedRuntime = tryResolvePackagedConnectorNodeRuntimeWithIntegrity(context.resourcesPath);
      connectorExecutable = resolvedRuntime.executable ?? '';
      packagedRuntimeIntegrityCategory = resolvedRuntime.integrityCategory;
    } else {
      // Never process.execPath here: inside Electron's main process that is electron.exe,
      // which cannot host the Connector (see resolveConnectorHostExecutable). Discover a
      // real, PATH-resolved Node executable instead, failing clearly if none is compatible.
      const resolvedDevRuntime = tryResolveDevelopmentConnectorNodeExecutable();
      connectorExecutable = resolvedDevRuntime.executable ?? '';
      developmentRuntimeError = resolvedDevRuntime.error;
    }
  }

  const connectorArgs = overrides.connectorArgs
    ?? (process.env.BUDCOM_CONNECTOR_ARGS
      ? process.env.BUDCOM_CONNECTOR_ARGS.split(' ')
      : [defaultScript]);
  const connectorCwd = overrides.connectorCwd
    ?? process.env.BUDCOM_CONNECTOR_CWD
    ?? (isPackaged
      ? packagedPaths.connectorResourceRoot
      : path.dirname(defaultScript.includes('main.js') ? defaultScript : connectorExecutable));

  const childEnvOverrides: Record<string, string> = {
    BUDCOM_CONNECTOR_PORT: String(connectorPort),
    BUDCOM_CONNECTOR_HOST: connectorHost,
    BUDCOM_CONNECTOR_BIND_MODE: resolvedBindMode,
    BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED: String(lanModeAcknowledged),
  };
  const startupCorrelationId = overrides.startupCorrelationId
    ?? process.env.BUDCOM_STARTUP_CORRELATION_ID?.trim()
    ?? null;
  if (startupCorrelationId) {
    childEnvOverrides.BUDCOM_STARTUP_CORRELATION_ID = startupCorrelationId;
  }
  if (context.connectorDatabaseDir) {
    childEnvOverrides.BUDCOM_DATABASE_PATH = context.connectorDatabaseDir;
  }
  if (context.connectorId) {
    childEnvOverrides.BUDCOM_CONNECTOR_ID = context.connectorId;
  }
  if (shouldSpawnConnectorViaElectronNode(connectorExecutable)) {
    childEnvOverrides.ELECTRON_RUN_AS_NODE = '1';
  }

  const childEnv = buildConnectorChildEnvironment(process.env, childEnvOverrides);

  return {
    connectorBaseUrl,
    connectorBindMode: resolvedBindMode,
    connectorHost,
    connectorPort,
    connectorExecutable,
    connectorArgs,
    connectorCwd,
    childEnv,
    isPackaged,
    packagedRuntimeIntegrityCategory,
    developmentRuntimeError,
    autoStart: overrides.autoStart ?? process.env.BUDCOM_CONNECTOR_AUTO_START !== 'false',
    healthPollIntervalMs: overrides.healthPollIntervalMs ?? 5_000,
    startupTimeoutMs: overrides.startupTimeoutMs ?? 30_000,
    shutdownGraceMs: overrides.shutdownGraceMs ?? 5_000,
    maxRestartAttempts: overrides.maxRestartAttempts ?? 5,
    reconnectBaseDelayMs: overrides.reconnectBaseDelayMs ?? 1_000,
    staleHealthThresholdMs: overrides.staleHealthThresholdMs ?? 20_000,
    startupCorrelationId,
    bundledConnectorVersion: overrides.bundledConnectorVersion
      ?? readBundledConnectorVersion(packagedPaths.connectorVersionFile),
  };
}

export function validateConnectorExecutable(config: ConnectorLifecycleConfig): string | null {
  if (config.packagedRuntimeIntegrityCategory) {
    return 'Packaged connector runtime failed integrity verification. Reinstall the desktop application.';
  }
  if (config.developmentRuntimeError) {
    return config.developmentRuntimeError;
  }
  if (config.connectorExecutable.trim().length === 0) {
    return config.isPackaged
      ? 'Packaged Node runtime for the connector is not available. Reinstall the desktop application.'
      : 'No compatible development Node runtime is available for the connector.';
  }
  const scriptPath = config.connectorArgs[0];
  if (scriptPath && scriptPath.endsWith('.js') && !fs.existsSync(scriptPath)) {
    return `Connector executable not found at ${scriptPath}. Build the connector first.`;
  }
  if (!scriptPath?.endsWith('.js') && !fs.existsSync(config.connectorExecutable)) {
    return `Connector executable not found at ${config.connectorExecutable}.`;
  }
  return null;
}

export function readBundledConnectorVersion(versionFile: string): string | null {
  if (!fs.existsSync(versionFile)) {
    return null;
  }
  const version = fs.readFileSync(versionFile, 'utf8').trim();
  return version.length > 0 ? version : null;
}

