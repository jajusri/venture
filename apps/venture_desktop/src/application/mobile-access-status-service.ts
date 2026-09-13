import { ConnectorHttpClient } from './connector-http-client.js';
import type { ActiveNetworkAdapter, TrustedLanEligibility } from './network/active-network-resolver.js';
import { resolveMobileEndpointHost } from './network/active-network-resolver.js';
import type { ConnectorBindMode } from './desktop-config-schema.js';

export type RebindStatus = 'idle' | 'rebinding' | 'succeeded' | 'failed' | 'blocked';

export interface RebindState {
  readonly status: RebindStatus;
  readonly at: string | null;
  readonly message: string | null;
}

export interface MobileAccessStatus {
  readonly connectorId: string | null;
  readonly connectorName: string | null;
  readonly connectorBindMode: ConnectorBindMode;
  readonly activeNetwork: {
    readonly adapterName: string;
    readonly ipv4: string;
    readonly profileCategory: string;
  } | null;
  /** The endpoint a phone would actually connect to. Never "0.0.0.0" — null when not yet advertised. */
  readonly reachableEndpoint: string | null;
  readonly trustedLanEligible: boolean;
  readonly trustedLanBlockedReason: string | null;
  readonly discoveryAdvertising: boolean;
  /** null when the Connector is unreachable or pairing is unavailable, not "0". */
  readonly pairedDeviceCount: number | null;
  readonly rebind: RebindState;
  readonly connectorReachable: boolean;
  readonly userMessage: string | null;
}

export interface MobileAccessStatusServiceOptions {
  readonly getConnectorBaseUrl: () => string;
  readonly getConnectorBindMode: () => ConnectorBindMode;
  readonly getActiveNetwork: () => ActiveNetworkAdapter | null;
  readonly getTrustedLanEligibility: () => { readonly eligible: boolean; readonly reason: string | null };
  readonly getRebindState: () => RebindState;
  readonly fetchImpl?: typeof fetch;
}

/**
 * Assembles the Mobile Access status model shown in the Desktop UI: connector identity, active
 * network, the endpoint a phone would actually reach, trusted-LAN eligibility, discovery status,
 * paired-device count, and the most recent network-driven rebind outcome. Read-only — never
 * mutates connector or network state.
 */
export class MobileAccessStatusService {
  constructor(private readonly options: MobileAccessStatusServiceOptions) {}

  async getStatus(): Promise<MobileAccessStatus> {
    const client = new ConnectorHttpClient({
      baseUrl: this.options.getConnectorBaseUrl(),
      fetchImpl: this.options.fetchImpl,
      maxAttempts: 1,
    });

    const activeNetwork = this.options.getActiveNetwork();
    const trustedLan = this.options.getTrustedLanEligibility();
    const bindMode = this.options.getConnectorBindMode();

    let connectorId: string | null = null;
    let connectorName: string | null = null;
    let discoveryAdvertising = false;
    let reachableEndpoint: string | null = null;
    let connectorReachable = false;
    let userMessage: string | null = null;

    try {
      const health = await client.getHealth();
      connectorId = health.connectorId ?? null;
      connectorName = health.connectorName ?? null;
      discoveryAdvertising = health.discoveryAdvertising ?? false;
      connectorReachable = true;
      reachableEndpoint = this.resolveReachableEndpoint(bindMode, activeNetwork, trustedLan);
    } catch (error) {
      userMessage = error instanceof Error ? error.message : 'Connector is not reachable.';
    }

    let pairedDeviceCount: number | null = null;
    if (connectorReachable) {
      try {
        const devices = await client.getDeviceList();
        pairedDeviceCount = devices.items.filter((item) => !item.revokedAt).length;
      } catch {
        pairedDeviceCount = null;
      }
    }

    return {
      connectorId,
      connectorName,
      connectorBindMode: bindMode,
      activeNetwork: activeNetwork
        ? {
            adapterName: activeNetwork.adapterName,
            ipv4: activeNetwork.ipv4,
            profileCategory: activeNetwork.profileCategory,
          }
        : null,
      reachableEndpoint,
      trustedLanEligible: trustedLan.eligible,
      trustedLanBlockedReason: trustedLan.reason,
      discoveryAdvertising,
      pairedDeviceCount,
      rebind: this.options.getRebindState(),
      connectorReachable,
      userMessage,
    };
  }

  /**
   * Never surfaces "0.0.0.0" as a phone-facing address, and never presents a Public/unknown-
   * profile network's address as reachable even if a health check happened to succeed in a
   * narrow transition window — trusted-LAN eligibility is the same authoritative decision the
   * actual lifecycle path enforces (see TrustedLanRebindCoordinator). Local-only mode has no
   * phone-reachable endpoint at all (loopback isn't reachable from another device).
   */
  private resolveReachableEndpoint(
    bindMode: ConnectorBindMode,
    activeNetwork: ActiveNetworkAdapter | null,
    trustedLanEligibility: TrustedLanEligibility,
  ): string | null {
    const resolution = resolveMobileEndpointHost({ bindMode, activeNetwork, trustedLanEligibility });
    return resolution.ready ? resolution.host : null;
  }
}
