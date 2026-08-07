import type { RawAdapterInfo, WindowsNetworkProfileCategory } from './route-querier.js';

export interface ActiveNetworkAdapter {
  readonly adapterId: string;
  readonly adapterName: string;
  readonly ipv4: string;
  readonly prefixLength: number;
  readonly gateway: string | null;
  readonly profileCategory: WindowsNetworkProfileCategory;
  readonly routeMetric: number;
}

export interface ActiveNetworkResolution {
  readonly adapter: ActiveNetworkAdapter | null;
  readonly rejectedReason: string | null;
}

export interface TrustedLanEligibility {
  readonly eligible: boolean;
  readonly reason: string | null;
}

export type ConnectorBindModeForEndpoint = 'local-only' | 'trusted-lan';

export type MobileEndpointResolution =
  | { readonly ready: true; readonly host: string }
  | { readonly ready: false; readonly reason: string };

const NO_PRIVATE_NETWORK_MESSAGE =
  'Connect this computer to a private Wi-Fi or LAN network, then retry.';
const PREPARING_MOBILE_ACCESS_MESSAGE = 'Preparing mobile access…';

/**
 * THE single authoritative answer to "is this Connector reachable by another device on the
 * LAN right now, and at what address?" — consumed by both MobileAccessStatusService (renderer
 * status display) and MobilePairingService (pairing-session gate), so there is exactly one
 * interpretation of mobile-endpoint readiness, never two that can drift apart (see TD-012,
 * docs/technical-debt/registry.md). Deliberately never returns a loopback/link-local/wildcard
 * address — Local-only mode always resolves `ready: false`, matching the requirement that a
 * mobile-pairing QR must never silently embed 127.0.0.1.
 */
export function resolveMobileEndpointHost(params: {
  readonly bindMode: ConnectorBindModeForEndpoint;
  readonly activeNetwork: ActiveNetworkAdapter | null;
  readonly trustedLanEligibility: TrustedLanEligibility;
}): MobileEndpointResolution {
  if (params.bindMode !== 'trusted-lan') {
    return { ready: false, reason: NO_PRIVATE_NETWORK_MESSAGE };
  }
  if (!params.trustedLanEligibility.eligible) {
    return { ready: false, reason: params.trustedLanEligibility.reason ?? NO_PRIVATE_NETWORK_MESSAGE };
  }
  if (!params.activeNetwork) {
    return { ready: false, reason: PREPARING_MOBILE_ACCESS_MESSAGE };
  }
  return { ready: true, host: params.activeNetwork.ipv4 };
}

/**
 * Windows' `Get-NetAdapter` MediaType string for Wi-Fi varies by driver/OS build — e.g. real
 * hardware observed as `"Native 802.11"` (with a space), while some tooling/docs use
 * `"Native802.11"` (no space). Matching a single literal string is not robust: normalize case
 * and harmless separators (spaces, hyphens, underscores, periods) before comparing against a
 * small exact allow-list, so classification survives real-world driver spacing/casing
 * variance without accepting anything merely because it *resembles* Wi-Fi (no substring or
 * fuzzy matching — normalized values must match one of these tokens exactly).
 */
function normalizeMediaTypeToken(value: string): string {
  return value.toLowerCase().trim().replace(/[\s\-_.]+/g, '');
}

/** Physical, routable media types only — excludes Bluetooth, virtual, and tunnel media types. */
const ALLOWED_MEDIA_TYPES = new Set(
  ['802.3', 'Native 802.11', 'Native802.11'].map(normalizeMediaTypeToken),
);

function isAllowedMediaType(mediaType: string): boolean {
  return ALLOWED_MEDIA_TYPES.has(normalizeMediaTypeToken(mediaType));
}

/**
 * Case-insensitive substring match against adapter name/description. Covers loopback, VPN, WSL,
 * Hyper-V, virtual Ethernet, Docker, Bluetooth, and common tunnel/miniport adapters — the exact
 * exclusion list from the discovery/reconnection spec.
 */
const EXCLUDED_NAME_KEYWORDS = [
  'loopback',
  'vpn',
  'wsl',
  'hyper-v',
  'hyperv',
  'vethernet',
  'virtual',
  'docker',
  'tap-windows',
  'tunnel',
  'ppp',
  'bluetooth',
  'wan miniport',
  'teredo',
  'isatap',
];

function isApipa(ipv4: string): boolean {
  return ipv4.startsWith('169.254.');
}

function matchesExcludedKeyword(text: string): boolean {
  const lower = text.toLowerCase();
  return EXCLUDED_NAME_KEYWORDS.some((keyword) => lower.includes(keyword));
}

/** True for an active, physical, non-virtual, non-APIPA adapter that is a real candidate. */
export function isEligibleCandidate(adapter: RawAdapterInfo): boolean {
  if (adapter.operationalStatus !== 'Up') {
    return false;
  }
  if (!adapter.ipv4 || isApipa(adapter.ipv4)) {
    return false;
  }
  if (!isAllowedMediaType(adapter.mediaType)) {
    return false;
  }
  if (matchesExcludedKeyword(adapter.adapterName) || matchesExcludedKeyword(adapter.interfaceDescription)) {
    return false;
  }
  return true;
}

function trustRank(category: WindowsNetworkProfileCategory): number {
  switch (category) {
    case 'Private':
      return 0;
    case 'DomainAuthenticated':
      return 1;
    case 'Unknown':
      return 2;
    case 'Public':
      return 3;
    default:
      return 2;
  }
}

/**
 * Selects the single active-route adapter from raw Windows adapter data. Never picks an address
 * merely because it appears first — filters to eligible physical adapters, then prefers a
 * Private/DomainAuthenticated profile over Public, then the lowest (best) combined route metric.
 */
export function resolveActiveNetworkAdapter(adapters: readonly RawAdapterInfo[]): ActiveNetworkResolution {
  const eligible = adapters.filter(isEligibleCandidate);
  if (eligible.length === 0) {
    return {
      adapter: null,
      rejectedReason:
        adapters.length === 0
          ? 'No default-route network adapter was found.'
          : 'No eligible physical Ethernet/Wi-Fi adapter with a default route was found — all candidates were loopback, virtual, VPN, disconnected, or APIPA.',
    };
  }

  const sorted = [...eligible].sort((a, b) => {
    const trustDelta = trustRank(a.profileCategory) - trustRank(b.profileCategory);
    if (trustDelta !== 0) {
      return trustDelta;
    }
    return (a.routeMetric ?? Number.MAX_SAFE_INTEGER) - (b.routeMetric ?? Number.MAX_SAFE_INTEGER);
  });

  const winner = sorted[0]!;
  return {
    adapter: {
      adapterId: winner.adapterId,
      adapterName: winner.adapterName,
      ipv4: winner.ipv4!,
      prefixLength: winner.prefixLength ?? 24,
      gateway: winner.gateway,
      profileCategory: winner.profileCategory,
      routeMetric: winner.routeMetric ?? 0,
    },
    rejectedReason: null,
  };
}

/**
 * Trusted-LAN mode must refuse exposure on a Windows Public network. DomainAuthenticated and
 * Private are both treated as trusted (Windows itself relaxes firewall defaults for both);
 * Public and Unknown are not.
 */
export function evaluateTrustedLanEligibility(resolution: ActiveNetworkResolution): TrustedLanEligibility {
  if (!resolution.adapter) {
    return { eligible: false, reason: resolution.rejectedReason ?? 'No active network adapter was found.' };
  }
  if (resolution.adapter.profileCategory === 'Public' || resolution.adapter.profileCategory === 'Unknown') {
    return {
      eligible: false,
      reason:
        `Trusted-LAN mode is blocked: "${resolution.adapter.adapterName}" is on a ` +
        `${resolution.adapter.profileCategory} Windows network profile. Switch the network to ` +
        'Private in Windows Settings, or use Local-only mode.',
    };
  }
  return { eligible: true, reason: null };
}
