import { describe, expect, it } from 'vitest';

import { applyRouteBackedHost } from '../../../src/application/network/route-backed-lifecycle-override.js';
import type { ActiveNetworkAdapter } from '../../../src/application/network/active-network-resolver.js';
import type { ConnectorLifecycleConfig } from '../../../src/application/connector-lifecycle-types.js';

function baseConfig(overrides: Partial<ConnectorLifecycleConfig> = {}): ConnectorLifecycleConfig {
  return {
    connectorBaseUrl: 'http://192.168.1.10:8080',
    connectorBindMode: 'trusted-lan',
    connectorHost: '192.168.1.10',
    connectorPort: 8080,
    connectorExecutable: 'node',
    connectorArgs: ['main.js'],
    connectorCwd: '.',
    childEnv: { BUDCOM_CONNECTOR_HOST: '192.168.1.10' },
    autoStart: true,
    healthPollIntervalMs: 5_000,
    startupTimeoutMs: 30_000,
    shutdownGraceMs: 5_000,
    maxRestartAttempts: 5,
    reconnectBaseDelayMs: 1_000,
    staleHealthThresholdMs: 20_000,
    ...overrides,
  };
}

function networkAdapter(overrides: Partial<ActiveNetworkAdapter> = {}): ActiveNetworkAdapter {
  return {
    adapterId: '{GUID}',
    adapterName: 'Ethernet',
    ipv4: '192.168.1.55',
    prefixLength: 24,
    gateway: '192.168.1.1',
    profileCategory: 'Private',
    routeMetric: 25,
    ...overrides,
  };
}

describe('applyRouteBackedHost', () => {
  it('a DHCP address change updates the effective connectorHost and connectorBaseUrl in trusted-LAN mode', () => {
    const config = baseConfig({ connectorHost: '192.168.1.10', connectorBaseUrl: 'http://192.168.1.10:8080' });

    const updated = applyRouteBackedHost(config, networkAdapter({ ipv4: '192.168.1.99' }));

    expect(updated.connectorHost).toBe('192.168.1.99');
    expect(updated.connectorBaseUrl).toBe('http://192.168.1.99:8080');
    expect(updated.childEnv?.BUDCOM_CONNECTOR_HOST).toBe('192.168.1.99');
  });

  it('leaves local-only configuration untouched regardless of the resolved adapter', () => {
    const config = baseConfig({ connectorBindMode: 'local-only', connectorHost: '127.0.0.1', connectorBaseUrl: 'http://127.0.0.1:8080' });

    const updated = applyRouteBackedHost(config, networkAdapter({ ipv4: '192.168.1.99' }));

    expect(updated.connectorHost).toBe('127.0.0.1');
    expect(updated).toEqual(config);
  });

  it('leaves configuration untouched when no adapter has been resolved yet', () => {
    const config = baseConfig();

    const updated = applyRouteBackedHost(config, null);

    expect(updated).toEqual(config);
  });

  it('is a no-op when the resolved adapter already matches the configured host', () => {
    const config = baseConfig({ connectorHost: '192.168.1.55' });

    const updated = applyRouteBackedHost(config, networkAdapter({ ipv4: '192.168.1.55' }));

    expect(updated).toEqual(config);
  });

  it('preserves the stable connector id passthrough in childEnv across a rebind', () => {
    const config = baseConfig({
      childEnv: { BUDCOM_CONNECTOR_HOST: '192.168.1.10', BUDCOM_CONNECTOR_ID: 'stable-connector-id' },
    });

    const updated = applyRouteBackedHost(config, networkAdapter({ ipv4: '192.168.1.99' }));

    expect(updated.childEnv?.BUDCOM_CONNECTOR_ID).toBe('stable-connector-id');
  });
});
