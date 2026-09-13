import type { DesktopConfigV1 } from './desktop-config-schema.js';
import { DESKTOP_CONFIG_SCHEMA_VERSION } from './desktop-config-schema.js';

export const PRODUCTION_DEFAULTS: DesktopConfigV1 = {
  schemaVersion: DESKTOP_CONFIG_SCHEMA_VERSION,
  connectorBindMode: 'local-only',
  connectorHost: '127.0.0.1',
  connectorPort: 8080,
  autoStartConnector: true,
  healthPollIntervalMs: 5_000,
  startupTimeoutMs: 30_000,
  shutdownGraceMs: 5_000,
  maxRestartAttempts: 5,
  reconnectBaseDelayMs: 1_000,
  logLevel: 'info',
  diagnosticsRetentionDays: 14,
  tallyHost: 'localhost',
  tallyPort: 9000,
  secureMobilePairingEnabled: false,
};

export const DEVELOPMENT_DEFAULTS: DesktopConfigV1 = {
  ...PRODUCTION_DEFAULTS,
  logLevel: 'debug',
  diagnosticsRetentionDays: 7,
};

export function getEnvironmentDefaults(isDevelopment: boolean): DesktopConfigV1 {
  return isDevelopment ? { ...DEVELOPMENT_DEFAULTS } : { ...PRODUCTION_DEFAULTS };
}
