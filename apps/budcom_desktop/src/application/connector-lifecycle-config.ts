import * as fs from 'node:fs';
import * as path from 'node:path';

import type { ConnectorLifecycleConfig } from './connector-lifecycle-types.js';
import { parseConnectorHostPort } from './desktop-config-schema.js';
import {
  buildConnectorChildEnvironment,
  resolvePackagedConnectorPaths,
} from './release/connector-packaged-paths.js';

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
  const connectorBaseUrl = overrides.connectorBaseUrl ?? process.env.BUDCOM_CONNECTOR_URL ?? 'http://127.0.0.1:8080';
  const parsedHostPort = parseConnectorHostPort(connectorBaseUrl);
  const connectorHost = overrides.connectorHost ?? parsedHostPort?.host ?? '127.0.0.1';
  const connectorPort = overrides.connectorPort ?? parsedHostPort?.port ?? DEFAULT_PORT;

  const packagedPaths = resolvePackagedConnectorPaths({
    isPackaged: context.isPackaged ?? false,
    resourcesPath: context.resourcesPath,
  });
  const defaultScript = packagedPaths.connectorEntryScript;

  const connectorExecutable = overrides.connectorExecutable ?? process.env.BUDCOM_CONNECTOR_EXECUTABLE ?? process.execPath;
  const connectorArgs = overrides.connectorArgs
    ?? (process.env.BUDCOM_CONNECTOR_ARGS
      ? process.env.BUDCOM_CONNECTOR_ARGS.split(' ')
      : [defaultScript]);
  const connectorCwd = overrides.connectorCwd
    ?? process.env.BUDCOM_CONNECTOR_CWD
    ?? path.dirname(defaultScript.includes('main.js') ? defaultScript : connectorExecutable);

  const childEnv = buildConnectorChildEnvironment(process.env, {
    ...(context.connectorDatabaseDir
      ? { BUDCOM_DATABASE_PATH: path.join(context.connectorDatabaseDir, 'budcom-ledger.db') }
      : {}),
    BUDCOM_CONNECTOR_PORT: String(connectorPort),
  });

  return {
    connectorBaseUrl,
    connectorHost,
    connectorPort,
    connectorExecutable,
    connectorArgs,
    connectorCwd,
    childEnv,
    autoStart: overrides.autoStart ?? process.env.BUDCOM_CONNECTOR_AUTO_START !== 'false',
    healthPollIntervalMs: overrides.healthPollIntervalMs ?? 5_000,
    startupTimeoutMs: overrides.startupTimeoutMs ?? 30_000,
    shutdownGraceMs: overrides.shutdownGraceMs ?? 5_000,
    maxRestartAttempts: overrides.maxRestartAttempts ?? 5,
    reconnectBaseDelayMs: overrides.reconnectBaseDelayMs ?? 1_000,
    staleHealthThresholdMs: overrides.staleHealthThresholdMs ?? 20_000,
  };
}

export function validateConnectorExecutable(config: ConnectorLifecycleConfig): string | null {
  if (config.connectorExecutable.trim().length === 0) {
    return 'Connector executable path is not configured.';
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
