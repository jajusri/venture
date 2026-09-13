import { describe, expect, it } from 'vitest';

import { resolveDesktopConfig } from '../../src/application/desktop-config-resolver.js';
import type { DesktopConfigV1 } from '../../src/application/desktop-config-schema.js';
import { DESKTOP_CONFIG_SCHEMA_VERSION } from '../../src/application/desktop-config-schema.js';

const VALID_CONFIG: DesktopConfigV1 = {
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
  tallyHost: '127.0.0.1',
  tallyPort: 9000,
  secureMobilePairingEnabled: false,
};

const DEFAULTS: DesktopConfigV1 = VALID_CONFIG;

describe('resolveDesktopConfig: configuration schema mismatch (scenario 8)', () => {
  it('marks configValid true with no errors for a schema-valid persisted config', () => {
    const resolved = resolveDesktopConfig(VALID_CONFIG, DEFAULTS);

    expect(resolved.configValid).toBe(true);
    expect(resolved.configValidationErrors).toEqual([]);
    expect(resolved.persisted.connectorHost).toBe('127.0.0.1');
  });

  it('marks configValid false with errors for a schema-invalid persisted config, without hiding the substitution', () => {
    const invalidPersisted = {
      ...VALID_CONFIG,
      schemaVersion: 999 as unknown as typeof DESKTOP_CONFIG_SCHEMA_VERSION,
    };

    const resolved = resolveDesktopConfig(invalidPersisted, DEFAULTS);

    expect(resolved.configValid).toBe(false);
    expect(resolved.configValidationErrors.length).toBeGreaterThan(0);
    expect(resolved.configValidationErrors.some((error) => error.field === 'schemaVersion')).toBe(true);
    // Existing fallback behavior is preserved: effective/persisted still fall back to defaults,
    // this only surfaces that a fallback happened rather than changing what happens.
    expect(resolved.persisted).toEqual(DEFAULTS);
  });

  it('surfaces a bind-mode/host schema conflict as a validation error rather than silently coercing it', () => {
    const invalidPersisted: DesktopConfigV1 = {
      ...VALID_CONFIG,
      connectorBindMode: 'trusted-lan',
      connectorHost: '127.0.0.1',
    };

    const resolved = resolveDesktopConfig(invalidPersisted, DEFAULTS);

    expect(resolved.configValid).toBe(false);
    expect(resolved.configValidationErrors.some((error) => error.field === 'connectorHost')).toBe(true);
  });
});
