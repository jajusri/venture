import { describe, expect, it } from 'vitest';

import type { ActiveNetworkAdapter } from '../../../src/application/network/active-network-resolver.js';
import {
  evaluateTrustedLanEligibility,
  isEligibleCandidate,
  resolveActiveNetworkAdapter,
  resolveMobileEndpointHost,
} from '../../../src/application/network/active-network-resolver.js';
import type { RawAdapterInfo } from '../../../src/application/network/route-querier.js';

function adapter(overrides: Partial<RawAdapterInfo> = {}): RawAdapterInfo {
  return {
    interfaceIndex: 1,
    adapterId: '{GUID-1}',
    adapterName: 'Ethernet',
    interfaceDescription: 'Realtek PCIe GbE Family Controller',
    mediaType: '802.3',
    operationalStatus: 'Up',
    ipv4: '192.168.1.20',
    prefixLength: 24,
    gateway: '192.168.1.1',
    routeMetric: 25,
    profileCategory: 'Private',
    ...overrides,
  };
}

describe('resolveActiveNetworkAdapter', () => {
  it('selects the single active physical default-route adapter', () => {
    const resolution = resolveActiveNetworkAdapter([adapter()]);

    expect(resolution.adapter).toMatchObject({ adapterName: 'Ethernet', ipv4: '192.168.1.20' });
    expect(resolution.rejectedReason).toBeNull();
  });

  it('prefers the lowest-metric eligible adapter when several are candidates', () => {
    const wifi = adapter({
      adapterId: '{GUID-WIFI}',
      adapterName: 'Wi-Fi',
      mediaType: 'Native802.11',
      routeMetric: 50,
      ipv4: '192.168.1.30',
    });
    const ethernet = adapter({ routeMetric: 10 });

    const resolution = resolveActiveNetworkAdapter([wifi, ethernet]);

    expect(resolution.adapter?.adapterName).toBe('Ethernet');
  });

  it('prefers a Private-profile adapter over a lower-metric Public one', () => {
    const publicButFaster = adapter({
      adapterId: '{GUID-PUB}',
      adapterName: 'Public Wi-Fi',
      mediaType: 'Native802.11',
      routeMetric: 5,
      profileCategory: 'Public',
      ipv4: '10.0.0.5',
    });
    const privateAdapter = adapter({ routeMetric: 25, profileCategory: 'Private' });

    const resolution = resolveActiveNetworkAdapter([publicButFaster, privateAdapter]);

    expect(resolution.adapter?.profileCategory).toBe('Private');
  });

  it('ignores VPN, WSL, Hyper-V, virtual Ethernet, and Docker adapters', () => {
    const candidates = [
      adapter({ adapterId: '{VPN}', adapterName: 'Local Area Connection* VPN', interfaceDescription: 'WireGuard VPN Adapter' }),
      adapter({ adapterId: '{WSL}', adapterName: 'vEthernet (WSL)', interfaceDescription: 'Hyper-V Virtual Ethernet Adapter' }),
      adapter({ adapterId: '{DOCKER}', adapterName: 'vEthernet (Docker Desktop)', interfaceDescription: 'Hyper-V Virtual Ethernet Adapter #2' }),
      adapter({ adapterId: '{REAL}', adapterName: 'Ethernet', routeMetric: 999 }), // still wins — only real candidate
    ];

    const resolution = resolveActiveNetworkAdapter(candidates);

    expect(resolution.adapter?.adapterId).toBe('{REAL}');
  });

  it('ignores Bluetooth adapters (media type not in the physical allow-list)', () => {
    const bluetooth = adapter({ adapterName: 'Bluetooth Network Connection', mediaType: 'Bluetooth' });

    expect(isEligibleCandidate(bluetooth)).toBe(false);
  });

  it('ignores loopback adapters', () => {
    const loopback = adapter({ adapterName: 'Loopback Pseudo-Interface 1', ipv4: '127.0.0.1' });

    expect(isEligibleCandidate(loopback)).toBe(false);
  });

  it('ignores disconnected adapters', () => {
    const down = adapter({ operationalStatus: 'Down' });

    expect(isEligibleCandidate(down)).toBe(false);
  });

  it('ignores APIPA (169.254.x.x) addresses', () => {
    const apipa = adapter({ ipv4: '169.254.1.5' });

    expect(isEligibleCandidate(apipa)).toBe(false);
  });

  it('never selects an address merely because it appears first — order-independent result', () => {
    const worse = adapter({ adapterId: '{WORSE}', routeMetric: 999, ipv4: '192.168.9.9' });
    const better = adapter({ adapterId: '{BETTER}', routeMetric: 5, ipv4: '192.168.1.20' });

    const resolutionA = resolveActiveNetworkAdapter([worse, better]);
    const resolutionB = resolveActiveNetworkAdapter([better, worse]);

    expect(resolutionA.adapter?.adapterId).toBe('{BETTER}');
    expect(resolutionB.adapter?.adapterId).toBe('{BETTER}');
  });

  it('returns a null adapter with a reason when no eligible candidate exists', () => {
    const resolution = resolveActiveNetworkAdapter([
      adapter({ adapterName: 'vEthernet (WSL)', interfaceDescription: 'Hyper-V Virtual Ethernet Adapter' }),
    ]);

    expect(resolution.adapter).toBeNull();
    expect(resolution.rejectedReason).toBeTruthy();
  });

  it('returns a null adapter with a reason when there are no adapters at all', () => {
    const resolution = resolveActiveNetworkAdapter([]);

    expect(resolution.adapter).toBeNull();
    expect(resolution.rejectedReason).toContain('No default-route');
  });
});

describe('Wi-Fi media-type classification (real Windows spacing/casing variance)', () => {
  it('accepts "Native 802.11" — the real value observed on this machine (with a space)', () => {
    expect(isEligibleCandidate(adapter({ mediaType: 'Native 802.11' }))).toBe(true);
  });

  it('accepts "Native802.11" — the no-space form', () => {
    expect(isEligibleCandidate(adapter({ mediaType: 'Native802.11' }))).toBe(true);
  });

  it('accepts casing variations of the Wi-Fi media type', () => {
    for (const mediaType of ['NATIVE 802.11', 'native 802.11', 'Native802.11', 'NATIVE802.11']) {
      expect(isEligibleCandidate(adapter({ mediaType }))).toBe(true);
    }
  });

  it('accepts harmless separator variations (hyphen/underscore) of the Wi-Fi media type', () => {
    for (const mediaType of ['Native-802.11', 'Native_802.11']) {
      expect(isEligibleCandidate(adapter({ mediaType }))).toBe(true);
    }
  });

  it('still accepts the supported Ethernet media type "802.3"', () => {
    expect(isEligibleCandidate(adapter({ mediaType: '802.3' }))).toBe(true);
  });

  it('rejects an unsupported/unknown media type outright', () => {
    expect(isEligibleCandidate(adapter({ mediaType: 'IEEE 802.15.4' }))).toBe(false);
    expect(isEligibleCandidate(adapter({ mediaType: '' }))).toBe(false);
  });

  it('does not accept a media type merely because it resembles Wi-Fi (no fuzzy/substring match)', () => {
    expect(isEligibleCandidate(adapter({ mediaType: 'Native 802.11 Virtual Miniport' }))).toBe(false);
    expect(isEligibleCandidate(adapter({ mediaType: 'Something Native 802.11-like' }))).toBe(false);
  });

  it('rejects a VPN/virtual adapter description even when the media type string looks like Wi-Fi', () => {
    const fakeVirtual = adapter({
      adapterName: 'vEthernet (WSL)',
      interfaceDescription: 'Hyper-V Virtual Ethernet Adapter',
      mediaType: 'Native 802.11',
    });

    expect(isEligibleCandidate(fakeVirtual)).toBe(false);
  });

  it('a real physical Wi-Fi candidate on a Public profile remains a candidate but is refused for trusted-LAN', () => {
    const publicWifi = adapter({ mediaType: 'Native 802.11', profileCategory: 'Public' });

    expect(isEligibleCandidate(publicWifi)).toBe(true);
    const resolution = resolveActiveNetworkAdapter([publicWifi]);
    expect(evaluateTrustedLanEligibility(resolution).eligible).toBe(false);
  });

  it('prefers the physical Wi-Fi candidate over a competing virtual default-route candidate', () => {
    const physicalWifi = adapter({
      adapterId: '{PHYSICAL-WIFI}',
      adapterName: 'Wi-Fi',
      mediaType: 'Native 802.11',
      routeMetric: 50,
    });
    const virtualCompeting = adapter({
      adapterId: '{VIRTUAL}',
      adapterName: 'vEthernet (Default Switch)',
      interfaceDescription: 'Hyper-V Virtual Ethernet Adapter',
      mediaType: 'Native 802.11', // even if the virtual adapter reports a Wi-Fi-like media type
      routeMetric: 1,
    });

    const resolution = resolveActiveNetworkAdapter([physicalWifi, virtualCompeting]);

    expect(resolution.adapter?.adapterId).toBe('{PHYSICAL-WIFI}');
  });

  it('regression: selects the exact real adapter description/media-type combination observed in the field', () => {
    // Reproduces the field defect exactly: interfaceDescription, mediaType, status, and profile
    // are the real observed values. The IPv4/gateway are placeholders (any valid private
    // address exercises the same code path) — deliberately not a specific machine's real
    // address, per policy against hardcoding real network values into test source.
    const realAdapter = adapter({
      adapterId: '{TEST-QCA9377-GUID}',
      adapterName: 'Wi-Fi',
      interfaceDescription: 'Qualcomm QCA9377 802.11ac Wireless Adapter',
      mediaType: 'Native 802.11',
      operationalStatus: 'Up',
      ipv4: '192.168.50.77',
      prefixLength: 24,
      gateway: '192.168.50.1',
      routeMetric: 0,
      profileCategory: 'Private',
    });

    const resolution = resolveActiveNetworkAdapter([realAdapter]);

    expect(resolution.adapter).toMatchObject({
      adapterName: 'Wi-Fi',
      ipv4: '192.168.50.77',
      profileCategory: 'Private',
    });
    expect(evaluateTrustedLanEligibility(resolution).eligible).toBe(true);
  });
});

describe('evaluateTrustedLanEligibility', () => {
  it('is eligible on a Private network', () => {
    const resolution = resolveActiveNetworkAdapter([adapter({ profileCategory: 'Private' })]);

    expect(evaluateTrustedLanEligibility(resolution)).toEqual({ eligible: true, reason: null });
  });

  it('is eligible on a DomainAuthenticated network', () => {
    const resolution = resolveActiveNetworkAdapter([adapter({ profileCategory: 'DomainAuthenticated' })]);

    expect(evaluateTrustedLanEligibility(resolution).eligible).toBe(true);
  });

  it('blocks trusted-LAN exposure on a Public Windows network', () => {
    const resolution = resolveActiveNetworkAdapter([adapter({ profileCategory: 'Public' })]);

    const eligibility = evaluateTrustedLanEligibility(resolution);

    expect(eligibility.eligible).toBe(false);
    expect(eligibility.reason).toContain('Public');
  });

  it('blocks trusted-LAN exposure when no adapter was resolved at all', () => {
    const resolution = resolveActiveNetworkAdapter([]);

    const eligibility = evaluateTrustedLanEligibility(resolution);

    expect(eligibility.eligible).toBe(false);
    expect(eligibility.reason).toBeTruthy();
  });
});

/**
 * TD-012 (docs/technical-debt/registry.md): the ONE authoritative "is this Connector reachable
 * by another device on the LAN, and at what address?" answer — consumed by both
 * MobileAccessStatusService and MobilePairingService, so there is never a silent loopback-hosted
 * mobile-pairing endpoint.
 */
describe('resolveMobileEndpointHost', () => {
  const privateWifi: ActiveNetworkAdapter = {
    adapterId: '{GUID-1}',
    adapterName: 'Wi-Fi',
    ipv4: '192.168.1.42',
    prefixLength: 24,
    gateway: '192.168.1.1',
    profileCategory: 'Private',
    routeMetric: 10,
  };
  const privateEthernet: ActiveNetworkAdapter = {
    ...privateWifi,
    adapterId: '{GUID-2}',
    adapterName: 'Ethernet',
    ipv4: '10.0.0.5',
  };

  it('resolves the active private Wi-Fi IPv4 when trusted-LAN mode is eligible', () => {
    const result = resolveMobileEndpointHost({
      bindMode: 'trusted-lan',
      activeNetwork: privateWifi,
      trustedLanEligibility: { eligible: true, reason: null },
    });

    expect(result).toEqual({ ready: true, host: '192.168.1.42' });
  });

  it('resolves the active private Ethernet IPv4 the same way', () => {
    const result = resolveMobileEndpointHost({
      bindMode: 'trusted-lan',
      activeNetwork: privateEthernet,
      trustedLanEligibility: { eligible: true, reason: null },
    });

    expect(result).toEqual({ ready: true, host: '10.0.0.5' });
  });

  it('is never ready in local-only mode — 127.0.0.1 must never become a mobile-pairing endpoint', () => {
    const result = resolveMobileEndpointHost({
      bindMode: 'local-only',
      activeNetwork: privateWifi,
      trustedLanEligibility: { eligible: true, reason: null },
    });

    expect(result.ready).toBe(false);
    if (!result.ready) {
      expect(result.reason).not.toMatch(/\d+\.\d+\.\d+\.\d+/);
    }
  });

  it('is not ready when trusted-LAN eligibility itself is false (e.g. Public network)', () => {
    const result = resolveMobileEndpointHost({
      bindMode: 'trusted-lan',
      activeNetwork: privateWifi,
      trustedLanEligibility: { eligible: false, reason: 'Trusted-LAN mode is blocked: Public network.' },
    });

    expect(result).toEqual({ ready: false, reason: 'Trusted-LAN mode is blocked: Public network.' });
  });

  it('is not ready when no eligible network has been resolved yet', () => {
    const result = resolveMobileEndpointHost({
      bindMode: 'trusted-lan',
      activeNetwork: null,
      trustedLanEligibility: { eligible: true, reason: null },
    });

    expect(result.ready).toBe(false);
  });

  it('a different resolved adapter (network/interface change) produces a refreshed endpoint', () => {
    const first = resolveMobileEndpointHost({
      bindMode: 'trusted-lan',
      activeNetwork: privateWifi,
      trustedLanEligibility: { eligible: true, reason: null },
    });
    const second = resolveMobileEndpointHost({
      bindMode: 'trusted-lan',
      activeNetwork: privateEthernet,
      trustedLanEligibility: { eligible: true, reason: null },
    });

    expect(first).toEqual({ ready: true, host: '192.168.1.42' });
    expect(second).toEqual({ ready: true, host: '10.0.0.5' });
  });
});
