import { afterEach, describe, expect, it } from 'vitest';

import { loadConfig } from '../../src/config/index.js';

describe('loadConfig', () => {
  it('loads defaults', () => {
    const config = loadConfig({
      env: 'test',
      port: 8080,
      tallyMinRequestIntervalMs: 2_000,
      tallySafeMode: true,
      tallyPoolMaxConnections: 1,
      tallyRetryMaxAttempts: 1,
      tallyCircuitBreakerEnabled: true,
    });
    expect(config.port).toBe(8080);
    expect(config.host).toBe('127.0.0.1');
    expect(config.networkExposure).toBe('loopback');
    expect(config.schemaVersion).toBe('1.0.0');
    expect(config.connectorVersion).toBe('0.4.4');
    expect(config.tallySafeMode).toBe(true);
    expect(config.tallyPoolMaxConnections).toBe(1);
    expect(config.tallyRetryMaxAttempts).toBe(1);
    expect(config.tallyMinRequestIntervalMs).toBe(2_000);
    expect(config.tallyCircuitBreakerEnabled).toBe(true);
  });

  it('applies overrides', () => {
    const config = loadConfig({ port: 9090, env: 'test' });
    expect(config.port).toBe(9090);
  });

  it('rejects invalid port', () => {
    expect(() => loadConfig({ port: -1 })).toThrow('Invalid port value');
  });
});

describe('secureLanRouteProtectionEnabled', () => {
  const ORIGINAL_ENV = process.env.BUDCOM_SECURE_LAN_ROUTE_PROTECTION_ENABLED;

  afterEach(() => {
    if (ORIGINAL_ENV === undefined) {
      delete process.env.BUDCOM_SECURE_LAN_ROUTE_PROTECTION_ENABLED;
    } else {
      process.env.BUDCOM_SECURE_LAN_ROUTE_PROTECTION_ENABLED = ORIGINAL_ENV;
    }
  });

  it('defaults to false when unset', () => {
    delete process.env.BUDCOM_SECURE_LAN_ROUTE_PROTECTION_ENABLED;
    const config = loadConfig({ env: 'test' });
    expect(config.secureLanRouteProtectionEnabled).toBe(false);
  });

  it('parses an explicit "true" env value', () => {
    process.env.BUDCOM_SECURE_LAN_ROUTE_PROTECTION_ENABLED = 'true';
    const config = loadConfig({ env: 'test' });
    expect(config.secureLanRouteProtectionEnabled).toBe(true);
  });

  it('parses an explicit "false" env value', () => {
    process.env.BUDCOM_SECURE_LAN_ROUTE_PROTECTION_ENABLED = 'false';
    const config = loadConfig({ env: 'test' });
    expect(config.secureLanRouteProtectionEnabled).toBe(false);
  });

  it('rejects a malformed env value rather than silently defaulting', () => {
    process.env.BUDCOM_SECURE_LAN_ROUTE_PROTECTION_ENABLED = 'yes-please';
    expect(() => loadConfig({ env: 'test' })).toThrow('Invalid boolean value');
  });

  it('an explicit override still takes precedence over the env var', () => {
    process.env.BUDCOM_SECURE_LAN_ROUTE_PROTECTION_ENABLED = 'true';
    const config = loadConfig({ env: 'test', secureLanRouteProtectionEnabled: false });
    expect(config.secureLanRouteProtectionEnabled).toBe(false);
  });
});
