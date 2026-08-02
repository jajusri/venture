import type { ActiveNetworkAdapter, ActiveNetworkResolution } from './active-network-resolver.js';
import { resolveActiveNetworkAdapter } from './active-network-resolver.js';
import type { RouteQuerier } from './route-querier.js';

export interface NetworkChangeWatcherOptions {
  readonly routeQuerier: RouteQuerier;
  readonly pollIntervalMs?: number;
  readonly onChange: (resolution: ActiveNetworkResolution) => void;
  readonly onError?: (error: unknown) => void;
  readonly setIntervalImpl?: (handler: () => void, ms: number) => ReturnType<typeof setInterval>;
  readonly clearIntervalImpl?: (handle: ReturnType<typeof setInterval>) => void;
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
      const adapters = await this.options.routeQuerier.queryAdapters();
      const resolution = resolveActiveNetworkAdapter(adapters);
      const nextFingerprint = fingerprint(resolution.adapter);
      if (nextFingerprint !== this.lastFingerprint) {
        this.lastFingerprint = nextFingerprint;
        this.options.onChange(resolution);
      }
    } catch (error) {
      this.options.onError?.(error);
    } finally {
      this.pollInFlight = false;
    }
  }
}
