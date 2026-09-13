import { describe, expect, it } from 'vitest';

import { MobileAccessStatusService } from '../../src/application/mobile-access-status-service.js';
import type { HealthResponse, DeviceListResult } from '../../src/application/types.js';
import type { ActiveNetworkAdapter } from '../../src/application/network/active-network-resolver.js';

const health: HealthResponse = {
  status: 'ok',
  schemaVersion: '1.0.0',
  connectorVersion: '0.4.0',
  tallyReachable: true,
  readOnly: true,
  connectorId: 'connector-abc-123',
  connectorName: 'Front Desk PC',
  networkExposure: 'lan',
  authenticatedLanAccessEnabled: false,
  discoveryAdvertising: true,
  services: [],
};

const deviceList: DeviceListResult = {
  items: [
    { deviceRecordId: 'd1', friendlyName: 'Store Phone', lastUsedAt: null, revokedAt: null },
    { deviceRecordId: 'd2', friendlyName: 'Old Phone', lastUsedAt: null, revokedAt: '2026-01-01T00:00:00.000Z' },
  ],
};

function fakeFetch(overrides: { healthStatus?: number } = {}): typeof fetch {
  return (async (url: string | URL) => {
    const path = String(url);
    if (path.endsWith('/health')) {
      if (overrides.healthStatus && overrides.healthStatus !== 200) {
        return new Response('unavailable', { status: overrides.healthStatus });
      }
      return new Response(JSON.stringify(health), { status: 200 });
    }
    if (path.endsWith('/device/list')) {
      return new Response(JSON.stringify(deviceList), { status: 200 });
    }
    return new Response('Not found', { status: 404 });
  }) as typeof fetch;
}

function networkAdapter(): ActiveNetworkAdapter {
  return {
    adapterId: '{GUID}',
    adapterName: 'Ethernet',
    ipv4: '192.168.1.20',
    prefixLength: 24,
    gateway: '192.168.1.1',
    profileCategory: 'Private',
    routeMetric: 25,
  };
}

describe('MobileAccessStatusService', () => {
  it('assembles connector identity, network, endpoint, discovery, and paired-device count', async () => {
    const service = new MobileAccessStatusService({
      getConnectorBaseUrl: () => 'http://192.168.1.20:8080',
      getConnectorBindMode: () => 'trusted-lan',
      getActiveNetwork: () => networkAdapter(),
      getTrustedLanEligibility: () => ({ eligible: true, reason: null }),
      getRebindState: () => ({ status: 'idle', at: null, message: null }),
      fetchImpl: fakeFetch(),
    });

    const status = await service.getStatus();

    expect(status.connectorId).toBe('connector-abc-123');
    expect(status.connectorName).toBe('Front Desk PC');
    expect(status.activeNetwork).toMatchObject({ adapterName: 'Ethernet', ipv4: '192.168.1.20' });
    expect(status.reachableEndpoint).toBe('192.168.1.20');
    expect(status.discoveryAdvertising).toBe(true);
    // Only the non-revoked device counts.
    expect(status.pairedDeviceCount).toBe(1);
    expect(status.connectorReachable).toBe(true);
  });

  it('never reports 0.0.0.0 as the phone-facing endpoint in local-only mode', async () => {
    const service = new MobileAccessStatusService({
      getConnectorBaseUrl: () => 'http://127.0.0.1:8080',
      getConnectorBindMode: () => 'local-only',
      getActiveNetwork: () => networkAdapter(),
      getTrustedLanEligibility: () => ({ eligible: true, reason: null }),
      getRebindState: () => ({ status: 'idle', at: null, message: null }),
      fetchImpl: fakeFetch(),
    });

    const status = await service.getStatus();

    expect(status.reachableEndpoint).not.toBe('0.0.0.0');
    expect(status.reachableEndpoint).toBeNull();
  });

  it('reports connector unreachable with a user message instead of throwing', async () => {
    const service = new MobileAccessStatusService({
      getConnectorBaseUrl: () => 'http://192.168.1.20:8080',
      getConnectorBindMode: () => 'trusted-lan',
      getActiveNetwork: () => null,
      getTrustedLanEligibility: () => ({ eligible: false, reason: 'No active network adapter was found.' }),
      getRebindState: () => ({ status: 'idle', at: null, message: null }),
      fetchImpl: fakeFetch({ healthStatus: 503 }),
    });

    const status = await service.getStatus();

    expect(status.connectorReachable).toBe(false);
    expect(status.userMessage).toBeTruthy();
    expect(status.pairedDeviceCount).toBeNull();
    expect(status.reachableEndpoint).toBeNull();
  });

  it('surfaces trusted-LAN blocked reason (e.g. Public network) even when the connector is reachable', async () => {
    const service = new MobileAccessStatusService({
      getConnectorBaseUrl: () => 'http://192.168.1.20:8080',
      getConnectorBindMode: () => 'trusted-lan',
      getActiveNetwork: () => networkAdapter(),
      getTrustedLanEligibility: () => ({ eligible: false, reason: 'Trusted-LAN mode is blocked: Public network.' }),
      getRebindState: () => ({ status: 'idle', at: null, message: null }),
      fetchImpl: fakeFetch(),
    });

    const status = await service.getStatus();

    expect(status.trustedLanEligible).toBe(false);
    expect(status.trustedLanBlockedReason).toContain('Public');
  });

  it('reflects the current rebind state (network-change/rebind status)', async () => {
    const service = new MobileAccessStatusService({
      getConnectorBaseUrl: () => 'http://192.168.1.20:8080',
      getConnectorBindMode: () => 'trusted-lan',
      getActiveNetwork: () => networkAdapter(),
      getTrustedLanEligibility: () => ({ eligible: true, reason: null }),
      getRebindState: () => ({ status: 'rebinding', at: '2026-08-02T05:00:00.000Z', message: 'Rebinding…' }),
      fetchImpl: fakeFetch(),
    });

    const status = await service.getStatus();

    expect(status.rebind.status).toBe('rebinding');
  });
});
