import { describe, expect, it } from 'vitest';
import {
  DESKTOP_CONFIG_SCHEMA_VERSION,
  validateDesktopConfig,
  type DesktopConfigV1,
} from '../../src/application/desktop-config-schema.js';

const BASE_CONFIG: DesktopConfigV1 = {
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

describe('validateDesktopConfig: TD-009 trusted-LAN requires device auth', () => {
  it('rejects trusted-lan bind mode with Secure Mobile Pairing disabled', () => {
    const result = validateDesktopConfig({
      ...BASE_CONFIG,
      connectorBindMode: 'trusted-lan',
      connectorHost: '192.168.1.50',
      secureMobilePairingEnabled: false,
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toContainEqual(
      expect.objectContaining({ field: 'secureMobilePairingEnabled' }),
    );
  });

  it('accepts trusted-lan bind mode when Secure Mobile Pairing is enabled', () => {
    const result = validateDesktopConfig({
      ...BASE_CONFIG,
      connectorBindMode: 'trusted-lan',
      connectorHost: '192.168.1.50',
      secureMobilePairingEnabled: true,
    });

    expect(result.ok).toBe(true);
    expect(result.config?.connectorBindMode).toBe('trusted-lan');
    expect(result.config?.secureMobilePairingEnabled).toBe(true);
  });

  it('still accepts local-only bind mode with Secure Mobile Pairing disabled (unaffected)', () => {
    const result = validateDesktopConfig({
      ...BASE_CONFIG,
      connectorBindMode: 'local-only',
      secureMobilePairingEnabled: false,
    });

    expect(result.ok).toBe(true);
  });
});
