import * as fs from 'node:fs';
import * as path from 'node:path';

import type { ConnectorLifecycleConfig } from './connector-lifecycle-types.js';
import { buildConnectorBaseUrl, parseConnectorHostPort } from './desktop-config-schema.js';
import {
  buildConnectorChildEnvironment,
  resolvePackagedConnectorPaths,
  shouldSpawnConnectorViaElectronNode,
} from './release/connector-packaged-paths.js';
import { resolvePackagedConnectorLoopbackHost } from './release/packaged-connector-network.js';
import { tryResolvePackagedConnectorNodeRuntimeWithIntegrity } from './release/packaged-node-runtime.js';

const DEFAULT_PORT = 8080;

export interface ConnectorLifecycleResolutionContext {
  readonly isPackaged?: boolean;
  readonly resourcesPath?: string;
  readonly connectorDatabaseDir?: string;
}

export function resolveConnectorLifecycleConfig(
  overrides: Partial<ConnectorLifecycleConfig> = {},
  context: ConnectorLifecycleResolutionContext = {},
): ConnectorLifecycleConfig {
  const isPackaged = context.isPackaged ?? false;
  const connectorBaseUrlInput = overrides.connectorBaseUrl ?? process.env.BUDCOM_CONNECTOR_URL ?? 'http://127.0.0.1:8080';
  const parsedHostPort = parseConnectorHostPort(connectorBaseUrlInput);
  const connectorHost = resolvePackagedConnectorLoopbackHost(
    overrides.connectorHost ?? parsedHostPort?.host ?? '127.0.0.1',
    isPackaged,
  );
  const connectorPort = overrides.connectorPort ?? parsedHostPort?.port ?? DEFAULT_PORT;
  const connectorBaseUrl = buildConnectorBaseUrl(connectorHost, connectorPort);

  const packagedPaths = resolvePackagedConnectorPaths({
    isPackaged,
    resourcesPath: context.resourcesPath,
  });
  const defaultScript = packagedPaths.connectorEntryScript;

  let connectorExecutable = overrides.connectorExecutable ?? process.env.BUDCOM_CONNECTOR_EXECUTABLE ?? '';
  let packagedRuntimeIntegrityCategory: string | null = null;
  if (!connectorExecutable) {
    if (isPackaged) {
      const resolvedRuntime = tryResolvePackagedConnectorNodeRuntimeWithIntegrity(context.resourcesPath);
      connectorExecutable = resolvedRuntime.executable ?? '';
      packagedRuntimeIntegrityCategory = resolvedRuntime.integrityCategory;
    } else {
      connectorExecutable = process.execPath;
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
  if (shouldSpawnConnectorViaElectronNode(connectorExecutable)) {
    childEnvOverrides.ELECTRON_RUN_AS_NODE = '1';
  }

  const childEnv = buildConnectorChildEnvironment(process.env, childEnvOverrides);

  return {
    connectorBaseUrl,
    connectorHost,
    connectorPort,
    connectorExecutable,
    connectorArgs,
    connectorCwd,
    childEnv,
    isPackaged,
    packagedRuntimeIntegrityCategory,
    autoStart: overrides.autoStart ?? process.env.BUDCOM_CONNECTOR_AUTO_START !== 'false',
    healthPollIntervalMs: overrides.healthPollIntervalMs ?? 5_000,
    startupTimeoutMs: overrides.startupTimeoutMs ?? 30_000,
    shutdownGraceMs: overrides.shutdownGraceMs ?? 5_000,
    maxRestartAttempts: overrides.maxRestartAttempts ?? 5,
    reconnectBaseDelayMs: overrides.reconnectBaseDelayMs ?? 1_000,
    staleHealthThresholdMs: overrides.staleHealthThresholdMs ?? 20_000,
    startupCorrelationId,
  };
}

export function validateConnectorExecutable(config: ConnectorLifecycleConfig): string | null {
  if (config.packagedRuntimeIntegrityCategory) {
    return 'Packaged connector runtime failed integrity verification. Reinstall the desktop application.';
  }
  if (config.connectorExecutable.trim().length === 0) {
    return 'Packaged Node runtime for the connector is not available. Reinstall the desktop application.';
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
