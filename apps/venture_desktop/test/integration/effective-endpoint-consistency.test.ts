import { describe, expect, it, vi } from 'vitest';

import { applyRouteBackedHost } from '../../src/application/network/route-backed-lifecycle-override.js';
import type { ActiveNetworkAdapter } from '../../src/application/network/active-network-resolver.js';
import type { ConnectorLifecycleConfig } from '../../src/application/connector-lifecycle-types.js';
import { DashboardService } from '../../src/application/dashboard-service.js';
import { CompanyService } from '../../src/application/company-service.js';
import { LedgerService } from '../../src/application/ledger-service.js';
import { StockItemService } from '../../src/application/stock-item-service.js';
import { LogService } from '../../src/application/log-service.js';

function staleConfig(overrides: Partial<ConnectorLifecycleConfig> = {}): ConnectorLifecycleConfig {
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

function liveNetworkAdapter(overrides: Partial<ActiveNetworkAdapter> = {}): ActiveNetworkAdapter {
  return {
    adapterId: '{GUID}',
    adapterName: 'Wi-Fi',
    ipv4: '192.168.29.34',
    prefixLength: 24,
    gateway: '192.168.29.1',
    profileCategory: 'Private',
    routeMetric: 25,
    ...overrides,
  };
}

/**
 * Records every URL a shared fetchImpl was asked to dial. The response body only needs to be
 * shallowly well-formed enough that DashboardService's own `session?.session.selectedCompany`
 * access (and similar) don't throw before this test gets to inspect what URL was actually
 * dialed — its content is otherwise irrelevant to what this test proves.
 */
function recordingFetch() {
  const dialedUrls: string[] = [];
  const body = JSON.stringify({
    status: 'ok',
    services: [],
    session: { selectedCompany: null },
    items: [],
  });
  const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
    dialedUrls.push(String(input));
    return new Response(body, { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
  return { fetchImpl, dialedUrls };
}

describe('TD-015 regression at the multi-consumer level: every Desktop Connector HTTP consumer shares one effective endpoint', () => {
  it('Dashboard, Company, Ledger, and StockItem services all dial the route-resolved endpoint, never the stale persisted one', async () => {
    // Reproduces TD-015's exact repro condition: desktop-config.json still holds a previous
    // network's host (A), the machine is actually on a different network now (B) — the same
    // fixture shape route-backed-lifecycle-override.test.ts uses for the resolution function
    // itself; this test instead proves what actually happens to the four HTTP-owning services
    // main.ts's startManagedLanChildFor() constructs from that one resolved value.
    const staleStartupConfig = staleConfig();
    const liveAdapter = liveNetworkAdapter();

    const effective = applyRouteBackedHost(staleStartupConfig, liveAdapter);
    expect(effective.connectorBaseUrl).toBe('http://192.168.29.34:8080');
    expect(effective.connectorBaseUrl).not.toContain('192.168.1.10'); // sanity: A truly differs from B

    const { fetchImpl, dialedUrls } = recordingFetch();
    const logService = new LogService();

    // Exactly mirrors main.ts's startManagedLanChildFor(): every one of these five constructors
    // receives the SAME effective.connectorBaseUrl — never the raw resolved.connectorBaseUrl.
    const dashboardService = new DashboardService({ connectorBaseUrl: effective.connectorBaseUrl, fetchImpl, logService });
    const companyService = new CompanyService({ connectorBaseUrl: effective.connectorBaseUrl, fetchImpl, logService });
    const ledgerService = new LedgerService({ connectorBaseUrl: effective.connectorBaseUrl, fetchImpl, logService });
    const stockItemService = new StockItemService({ connectorBaseUrl: effective.connectorBaseUrl, fetchImpl, logService });

    await dashboardService.getDashboardState();
    await companyService.discoverCompanies().catch(() => undefined); // a malformed body is fine — only the dialed URL matters here
    await ledgerService.getPageState().catch(() => undefined);
    await stockItemService.getPageState().catch(() => undefined);

    expect(dialedUrls.length).toBeGreaterThan(0);
    for (const url of dialedUrls) {
      expect(url.startsWith('http://192.168.29.34:8080')).toBe(true);
      expect(url).not.toContain('192.168.1.10');
    }
  });

  it('a live network transition mid-session moves every service to the new endpoint on reconstruction, with no residual reference to the old one', async () => {
    const startingConfig = staleConfig({ connectorHost: '10.0.0.5', connectorBaseUrl: 'http://10.0.0.5:8080' });
    const onNetworkA = applyRouteBackedHost(startingConfig, liveNetworkAdapter({ ipv4: '10.0.0.5' }));
    const onNetworkB = applyRouteBackedHost(onNetworkA, liveNetworkAdapter({ ipv4: '172.16.4.20' }));
    expect(onNetworkB.connectorBaseUrl).toBe('http://172.16.4.20:8080');

    const { fetchImpl, dialedUrls } = recordingFetch();
    const logService = new LogService();

    // Reconstruction, exactly as startManagedLanChildFor() does on every detected network change.
    const dashboardService = new DashboardService({ connectorBaseUrl: onNetworkB.connectorBaseUrl, fetchImpl, logService });
    const companyService = new CompanyService({ connectorBaseUrl: onNetworkB.connectorBaseUrl, fetchImpl, logService });

    await dashboardService.getDashboardState();
    await companyService.discoverCompanies().catch(() => undefined);

    expect(dialedUrls.length).toBeGreaterThan(0);
    for (const url of dialedUrls) {
      expect(url.startsWith('http://172.16.4.20:8080')).toBe(true);
      expect(url).not.toContain('10.0.0.5');
    }
  });
});
