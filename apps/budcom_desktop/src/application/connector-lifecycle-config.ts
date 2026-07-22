import fs from 'node:fs';
import path from 'node:path';

import type { ConnectorLifecycleConfig } from './connector-lifecycle-types.js';

const DEFAULT_PORT = 8080;

function resolveDefaultConnectorScript(): string {
  const fromDistMain = path.resolve(__dirname, '../../../../connector/budcom_connector/dist/main.js');
  if (fs.existsSync(fromDistMain)) {
    return fromDistMain;
  }
  const fromRepoRoot = path.resolve(process.cwd(), '../../connector/budcom_connector/dist/main.js');
  return fromRepoRoot;
}

function parsePort(baseUrl: string, fallback: number): number {
  try {
    const parsed = new URL(baseUrl);
    if (parsed.port) {
      return Number.parseInt(parsed.port, 10);
    }
    return parsed.protocol === 'https:' ? 443 : fallback;
  } catch {
    return fallback;
  }
}

export function resolveConnectorLifecycleConfig(
  overrides: Partial<ConnectorLifecycleConfig> = {},
): ConnectorLifecycleConfig {
  const connectorBaseUrl = overrides.connectorBaseUrl ?? process.env.BUDCOM_CONNECTOR_URL ?? 'http://localhost:8080';
  const connectorPort = overrides.connectorPort ?? parsePort(connectorBaseUrl, DEFAULT_PORT);
  const defaultScript = resolveDefaultConnectorScript();
  const connectorExecutable = overrides.connectorExecutable ?? process.env.BUDCOM_CONNECTOR_EXECUTABLE ?? process.execPath;
  const connectorArgs = overrides.connectorArgs
    ?? (process.env.BUDCOM_CONNECTOR_ARGS
      ? process.env.BUDCOM_CONNECTOR_ARGS.split(' ')
      : [defaultScript]);
  const connectorCwd = overrides.connectorCwd
    ?? process.env.BUDCOM_CONNECTOR_CWD
    ?? path.dirname(defaultScript.includes('main.js') ? defaultScript : connectorExecutable);

  return {
    connectorBaseUrl,
    connectorPort,
    connectorExecutable,
    connectorArgs,
    connectorCwd,
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
