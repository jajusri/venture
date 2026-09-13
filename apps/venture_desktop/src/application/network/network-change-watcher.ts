import type { ActiveNetworkAdapter, ActiveNetworkResolution } from './active-network-resolver.js';
import { excludeStaleAddresses, resolveActiveNetworkAdapter } from './active-network-resolver.js';
import type { RouteQuerier } from './route-querier.js';

/** Default bound on consecutive query failures tolerated before failing closed (TD-017). At the
 * default 5s poll interval this is ~10-15s of uncertainty before the user is told, not silence. */
const DEFAULT_MAX_CONSECUTIVE_FAILURES = 3;

const UNRESOLVED_AFTER_REPEATED_FAILURE_REASON =
  'Network changed and could not be confirmed after several attempts. Reconnecting automatically — ' +
  'this should resolve once the new network settles.';

export interface NetworkChangeWatcherOptions {
  readonly routeQuerier: RouteQuerier;
  readonly pollIntervalMs?: number;
  readonly onChange: (resolution: ActiveNetworkResolution) => void;
  readonly onError?: (error: unknown) => void;
  readonly setIntervalImpl?: (handler: () => void, ms: number) => ReturnType<typeof setInterval>;
  readonly clearIntervalImpl?: (handle: ReturnType<typeof setInterval>) => void;
  /**
   * TD-017: returns the IPv4 addresses currently assigned to a live local interface, used to
   * reject a resolved adapter whose address the route query is reporting stale. Omit to skip this
   * cross-check (existing tests that construct fixture adapters with addresses unrelated to the
   * real host's interfaces rely on this being opt-in); production wiring always supplies the real
   * `getLiveIpv4Addresses` from route-querier.ts.
   */
  readonly getLiveIpv4Addresses?: () => ReadonlySet<string>;
  /** Consecutive `queryAdapters()` failures tolerated before failing closed to an explicit
   * unresolved-network signal. Defaults to 3. */
  readonly maxConsecutiveFailures?: number;
}

function fingerprint(adapter: ActiveNetworkAdapter | null): string {
  if (!adapter) {
    return 'none';
  }
  // profileCategory must be part of the fingerprint even though it never changes the adapter's
  // identity or address: Windows flipping a Wi-Fi network from Private to Public (or back)
  // keeps the same adapterId/ipv4, but is exactly the transition trusted-LAN policy exists to
  // react to. Without this, a pure profile change would never be seen as a "change" and the
  // Connector would keep exposing a now-Public network indefinitely between IP/adapter swaps.
  return `${adapter.adapterId}|${adapter.ipv4}|${adapter.profileCategory}`;
}

/**
 * Detects Windows network changes (DHCP renewal, Wi-Fi switch, cable unplug/replug) while the
 * Desktop is running, by periodically re-resolving the active-route adapter and diffing its
 * fingerprint (adapter id + IPv4). Only calls onChange when the resolved adapter actually
 * changes — a rebind/mDNS republish is comparatively expensive and must not fire every poll.
 *
 * The clock is injectable so tests can drive polling deterministically without real timers.
 */
export class NetworkChangeWatcher {
  private timer: ReturnType<typeof setInterval> | null = null;
  private lastFingerprint: string | null = null;
  private pollInFlight = false;
  private consecutiveFailures = 0;

  constructor(private readonly options: NetworkChangeWatcherOptions) {}

  async start(): Promise<void> {
    await this.pollOnce();
    const intervalMs = this.options.pollIntervalMs ?? 5_000;
    const setIntervalImpl = this.options.setIntervalImpl ?? setInterval;
    this.timer = setIntervalImpl(() => {
      void this.pollOnce();
    }, intervalMs);
  }

  stop(): void {
    if (this.timer !== null) {
      const clearIntervalImpl = this.options.clearIntervalImpl ?? clearInterval;
      clearIntervalImpl(this.timer);
      this.timer = null;
    }
  }

  isRunning(): boolean {
    return this.timer !== null;
  }

  /** Runs one resolve+diff cycle immediately. Exposed for tests and manual "recheck now" actions. */
  async pollOnce(): Promise<void> {
    if (this.pollInFlight) {
      return;
    }
    this.pollInFlight = true;
    try {
      const rawAdapters = await this.options.routeQuerier.queryAdapters();
      this.consecutiveFailures = 0;
      const liveAdapters = this.options.getLiveIpv4Addresses
        ? excludeStaleAddresses(rawAdapters, this.options.getLiveIpv4Addresses())
        : rawAdapters;
      const resolution = resolveActiveNetworkAdapter(liveAdapters);
      this.reportIfChanged(resolution);
    } catch (error) {
      this.options.onError?.(error);
      this.consecutiveFailures += 1;
      const maxFailures = this.options.maxConsecutiveFailures ?? DEFAULT_MAX_CONSECUTIVE_FAILURES;
      if (this.consecutiveFailures >= maxFailures) {
        // TD-017: bounded retry budget exhausted — fail closed rather than silently leaving a
        // previously-resolved (now unconfirmable) adapter authoritative. Resetting the counter
        // here means a still-failing querier reports this once per new fingerprint transition,
        // not on every subsequent poll.
        this.consecutiveFailures = 0;
        this.reportIfChanged({ adapter: null, rejectedReason: UNRESOLVED_AFTER_REPEATED_FAILURE_REASON });
      }
    } finally {
      this.pollInFlight = false;
    }
  }

  private reportIfChanged(resolution: ActiveNetworkResolution): void {
    const nextFingerprint = fingerprint(resolution.adapter);
    if (nextFingerprint !== this.lastFingerprint) {
      this.lastFingerprint = nextFingerprint;
      this.options.onChange(resolution);
    }
  }
}
