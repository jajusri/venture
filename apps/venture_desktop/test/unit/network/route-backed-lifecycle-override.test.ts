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
    childEnv: { VENTURE_CONNECTOR_HOST: '192.168.1.10' },
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
    expect(updated.childEnv?.VENTURE_CONNECTOR_HOST).toBe('192.168.1.99');
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
      childEnv: { VENTURE_CONNECTOR_HOST: '192.168.1.10', VENTURE_CONNECTOR_ID: 'stable-connector-id' },
    });

    const updated = applyRouteBackedHost(config, networkAdapter({ ipv4: '192.168.1.99' }));

    expect(updated.childEnv?.VENTURE_CONNECTOR_ID).toBe('stable-connector-id');
  });

  // TD-015 regression coverage: main.ts's computeEffectiveConnectorBaseUrl() is a one-line
  // wrapper — applyRouteBackedHost(resolved.lifecycleConfig, activeNetworkAdapter).connectorBaseUrl
  // — now used by every Desktop business-service client (Dashboard/Company/Ledger/StockItem) and
  // the manual health-check handler, exactly like ConnectorLifecycleService's own spawn/health
  // config already did. Proving this function's behavior for a stale-persisted-host-at-startup
  // scenario and a live network-transition scenario therefore proves what every one of those
  // consumers will receive, since they all call through this same function.

  it('TD-015: a startup config still holding a previous network\'s host is corrected once a live private-LAN adapter resolves — the scenario every Desktop business client shares', () => {
    // "persisted connectorHost belongs to old subnet" — simulates desktop-config.json still
    // holding yesterday's network's address.
    const staleStartupConfig = baseConfig({
      connectorHost: '192.168.1.10',
      connectorBaseUrl: 'http://192.168.1.10:8080',
      childEnv: { VENTURE_CONNECTOR_HOST: '192.168.1.10' },
    });
    // "active route resolves different eligible private-LAN host" — the machine is actually on
    // a different network now.
    const liveAdapter = networkAdapter({ ipv4: '10.93.98.231' });

    const effective = applyRouteBackedHost(staleStartupConfig, liveAdapter);

    // This is exactly the value computeEffectiveConnectorBaseUrl() would return, and therefore
    // exactly what Dashboard/Company/Ledger/StockItem/health-check now all resolve to — never
    // the stale 192.168.1.10 host from staleStartupConfig.
    expect(effective.connectorBaseUrl).toBe('http://10.93.98.231:8080');
    expect(effective.connectorHost).toBe('10.93.98.231');
    expect(effective.connectorBaseUrl).not.toContain('192.168.1.10');
  });

  it('TD-015: a live network transition from endpoint A to endpoint B is fully reflected — no residual reference to A', () => {
    const startingConfig = baseConfig({ connectorHost: '10.0.0.5', connectorBaseUrl: 'http://10.0.0.5:8080' });

    const onNetworkA = applyRouteBackedHost(startingConfig, networkAdapter({ ipv4: '10.0.0.5' }));
    expect(onNetworkA.connectorBaseUrl).toBe('http://10.0.0.5:8080');

    // Network changes (Wi-Fi switch / DHCP renewal) — a fresh resolution comes in with a
    // different address, exactly as handleNetworkChange() -> startManagedLanChildFor() would
    // feed a fresh ActiveNetworkResolution into this same function on every detected change.
    const onNetworkB = applyRouteBackedHost(onNetworkA, networkAdapter({ ipv4: '172.16.4.20' }));

    expect(onNetworkB.connectorBaseUrl).toBe('http://172.16.4.20:8080');
    expect(onNetworkB.connectorHost).toBe('172.16.4.20');
    expect(onNetworkB.connectorBaseUrl).not.toContain('10.0.0.5');
    expect(onNetworkB.childEnv?.VENTURE_CONNECTOR_HOST).toBe('172.16.4.20');
  });

  it('TD-015: no eligible active network never invents or falls back to a fabricated endpoint — stays on whatever was last known', () => {
    const lastKnownGood = baseConfig({ connectorHost: '10.93.98.231', connectorBaseUrl: 'http://10.93.98.231:8080' });

    // network resolution failed / no adapter currently eligible (e.g. Public profile refused,
    // adapter down) — activeNetworkAdapter is null, matching main.ts's own initial value and
    // what a rejected resolution leaves it at.
    const result = applyRouteBackedHost(lastKnownGood, null);

    expect(result).toEqual(lastKnownGood);
    expect(result.connectorBaseUrl).toBe('http://10.93.98.231:8080');
  });
});
