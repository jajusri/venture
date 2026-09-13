import { describe, expect, it } from 'vitest';

import { NetworkChangeWatcher } from '../../../src/application/network/network-change-watcher.js';
import { FakeRouteQuerier } from '../../../src/application/network/route-querier.js';
import type { ActiveNetworkResolution } from '../../../src/application/network/active-network-resolver.js';
import type { RawAdapterInfo } from '../../../src/application/network/route-querier.js';
import { TrustedLanRebindCoordinator } from '../../../src/application/network/trusted-lan-rebind-coordinator.js';

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

/** Deterministic fake clock — captures the scheduled callback so tests can fire it manually. */
function createFakeClock() {
  let scheduled: (() => void) | null = null;
  return {
    setIntervalImpl: (handler: () => void) => {
      scheduled = handler;
      return 1 as unknown as ReturnType<typeof setInterval>;
    },
    clearIntervalImpl: () => {
      scheduled = null;
    },
    fireTick: async () => {
      scheduled?.();
      // Allow the resulting pollOnce() promise chain to settle.
      await Promise.resolve();
      await Promise.resolve();
    },
  };
}

describe('NetworkChangeWatcher', () => {
  it('resolves the current adapter immediately on start()', async () => {
    const querier = new FakeRouteQuerier([adapter()]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start();

    expect(changes).toHaveLength(1);
    expect(changes[0]?.adapter?.ipv4).toBe('192.168.1.20');
  });

  it('a DHCP address change is detected and reported on the next poll', async () => {
    const querier = new FakeRouteQuerier([adapter({ ipv4: '192.168.1.20' })]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });
    await watcher.start();
    expect(changes).toHaveLength(1);

    querier.setAdapters([adapter({ ipv4: '192.168.1.99' })]);
    await clock.fireTick();

    expect(changes).toHaveLength(2);
    expect(changes[1]?.adapter?.ipv4).toBe('192.168.1.99');
  });

  it('does not report a change when the resolved adapter is unchanged (no rebind storms)', async () => {
    const querier = new FakeRouteQuerier([adapter()]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });
    await watcher.start();
    expect(changes).toHaveLength(1);

    await clock.fireTick();
    await clock.fireTick();
    await clock.fireTick();

    expect(changes).toHaveLength(1);
  });

  it('a stable Wi-Fi->Ethernet-only change from "no adapter" to "adapter found" is reported', async () => {
    const querier = new FakeRouteQuerier([]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });
    await watcher.start();
    expect(changes[0]?.adapter).toBeNull();

    querier.setAdapters([adapter()]);
    await clock.fireTick();

    expect(changes).toHaveLength(2);
    expect(changes[1]?.adapter).not.toBeNull();
  });

  it('a Windows network-profile change alone (same adapter, same IP) is still reported', async () => {
    // Regression: profileCategory must be part of the change fingerprint. Windows flipping
    // Private<->Public keeps the same adapterId/ipv4, so if profileCategory were ignored here,
    // the trusted-LAN coordinator would never be asked to re-evaluate and a Connector already
    // bound on a now-Public network would keep running indefinitely.
    const querier = new FakeRouteQuerier([adapter({ profileCategory: 'Private' })]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });
    await watcher.start();
    expect(changes).toHaveLength(1);
    expect(changes[0]?.adapter?.profileCategory).toBe('Private');

    querier.setAdapters([adapter({ profileCategory: 'Public' })]);
    await clock.fireTick();

    expect(changes).toHaveLength(2);
    expect(changes[1]?.adapter?.ipv4).toBe('192.168.1.20');
    expect(changes[1]?.adapter?.profileCategory).toBe('Public');
  });

  it('stop() clears the interval and no further polls occur', async () => {
    const querier = new FakeRouteQuerier([adapter()]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });
    await watcher.start();
    watcher.stop();

    expect(watcher.isRunning()).toBe(false);
    await clock.fireTick();
    expect(changes).toHaveLength(1);
  });

  it('reports resolver errors via onError without throwing', async () => {
    const failingQuerier = { queryAdapters: () => Promise.reject(new Error('shell failure')) };
    const errors: unknown[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: failingQuerier,
      onChange: () => {},
      onError: (error) => errors.push(error),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await expect(watcher.start()).resolves.toBeUndefined();
    expect(errors).toHaveLength(1);
  });
});

/**
 * TD-017 regression suite. Field defect: after a genuine network change (home Wi-Fi ->
 * mobile hotspot), the Connector stayed bound to the old, now-unreachable IP. Root cause traced
 * to two gaps this suite exercises directly: (1) a route query can return a structurally-valid
 * but stale adapter entry, and (2) repeated query failures during a transition were silently
 * swallowed with no downstream effect, so stale state was never invalidated.
 */
describe('NetworkChangeWatcher — TD-017 live-address validation', () => {
  it('a resolved adapter whose IP is not currently live is rejected — getLiveIpv4Addresses wiring', async () => {
    const staleHomeNetworkAdapter = adapter({ ipv4: '192.168.29.34' });
    const querier = new FakeRouteQuerier([staleHomeNetworkAdapter]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      getLiveIpv4Addresses: () => new Set(['10.142.207.231']), // the real, current hotspot IP
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start();

    expect(changes).toHaveLength(1);
    expect(changes[0]?.adapter).toBeNull();
  });

  it('a live adapter is accepted — the live-address check does not reject a genuinely current adapter', async () => {
    const currentHotspotAdapter = adapter({ ipv4: '10.142.207.231' });
    const querier = new FakeRouteQuerier([currentHotspotAdapter]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      getLiveIpv4Addresses: () => new Set(['10.142.207.231']),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start();

    expect(changes[0]?.adapter?.ipv4).toBe('10.142.207.231');
  });

  it('a transition where the querier still lists the departed IP alongside the new one resolves to the new, live one', async () => {
    const stale = adapter({ adapterId: '{STALE}', ipv4: '192.168.29.34', routeMetric: 5 });
    const live = adapter({ adapterId: '{LIVE}', ipv4: '10.142.207.231', routeMetric: 50 });
    const querier = new FakeRouteQuerier([stale, live]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      getLiveIpv4Addresses: () => new Set(['10.142.207.231']),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start();

    expect(changes[0]?.adapter?.adapterId).toBe('{LIVE}');
  });

  it('omitting getLiveIpv4Addresses skips the cross-check entirely (backward-compatible default)', async () => {
    const anyAdapter = adapter({ ipv4: '203.0.113.5' }); // not "live" by any real definition
    const querier = new FakeRouteQuerier([anyAdapter]);
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start();

    expect(changes[0]?.adapter?.ipv4).toBe('203.0.113.5');
  });
});

describe('NetworkChangeWatcher — TD-017 bounded retry and fail-closed unresolved state', () => {
  it('a single query failure does not report a change — no flapping on one transient hiccup', async () => {
    const querier = { queryAdapters: () => Promise.reject(new Error('shell failure')) };
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      maxConsecutiveFailures: 3,
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start();
    await clock.fireTick(); // 2 consecutive failures — still below the bound

    expect(changes).toHaveLength(0);
  });

  it('reaching the bounded failure threshold fails closed: reports a null-adapter change with a clear reason', async () => {
    const querier = { queryAdapters: () => Promise.reject(new Error('shell failure')) };
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      maxConsecutiveFailures: 3,
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start(); // failure 1
    await clock.fireTick(); // failure 2
    await clock.fireTick(); // failure 3 — bound reached

    expect(changes).toHaveLength(1);
    expect(changes[0]?.adapter).toBeNull();
    expect(changes[0]?.rejectedReason).toBeTruthy();
    expect(changes[0]?.rejectedReason).toMatch(/reconnect/i);
  });

  it('a successful poll resets the failure counter — an unrelated later run of failures needs its own full bound', async () => {
    let shouldFail = true;
    const querier = {
      queryAdapters: () => (shouldFail ? Promise.reject(new Error('shell failure')) : Promise.resolve([adapter()])),
    };
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      maxConsecutiveFailures: 3,
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start(); // failure 1
    await clock.fireTick(); // failure 2 — one short of the bound
    shouldFail = false;
    await clock.fireTick(); // success — resets the counter, reports the resolved adapter
    shouldFail = true;
    await clock.fireTick(); // failure 1 of a fresh run
    await clock.fireTick(); // failure 2 of the fresh run — still short of the bound

    expect(changes.filter((c) => c.adapter === null)).toHaveLength(0);
  });

  it('after fail-closed signaling, a subsequent successful resolution is correctly detected as a change (automatic recovery)', async () => {
    let phase: 'failing' | 'recovered' = 'failing';
    const querier = {
      queryAdapters: () =>
        phase === 'failing' ? Promise.reject(new Error('shell failure')) : Promise.resolve([adapter({ ipv4: '10.142.207.231' })]),
    };
    const changes: ActiveNetworkResolution[] = [];
    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      onChange: (resolution) => changes.push(resolution),
      maxConsecutiveFailures: 3,
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start(); // failure 1
    await clock.fireTick(); // failure 2
    await clock.fireTick(); // failure 3 — fails closed, fingerprint becomes 'none'
    expect(changes).toHaveLength(1);
    expect(changes[0]?.adapter).toBeNull();

    phase = 'recovered';
    await clock.fireTick();

    expect(changes).toHaveLength(2);
    expect(changes[1]?.adapter?.ipv4).toBe('10.142.207.231');
  });

  it('end-to-end with a real TrustedLanRebindCoordinator: a stale-address transition never leaves the old child running, and recovery binds only the new address', async () => {
    let phase: 'onOldNetwork' | 'transitioning' | 'onNewNetwork' = 'onOldNetwork';
    const querier = {
      queryAdapters: (): Promise<readonly RawAdapterInfo[]> => {
        if (phase === 'onOldNetwork') {
          return Promise.resolve([adapter({ ipv4: '192.168.29.34' })]);
        }
        if (phase === 'transitioning') {
          return Promise.reject(new Error('route query failed mid-transition'));
        }
        return Promise.resolve([adapter({ ipv4: '10.142.207.231' })]);
      },
    };
    // Only the address that is "currently live" at the moment of each query matters — modelling
    // the machine's own live interface state independently of what the (possibly stale) route
    // query itself reports.
    let liveAddress = '192.168.29.34';

    const starts: string[] = [];
    let activeChildIp: string | null = null;
    const coordinator = new TrustedLanRebindCoordinator({
      stopCurrentChild: async () => {
        activeChildIp = null;
      },
      startChildFor: async (resolution) => {
        starts.push(resolution.adapter!.ipv4);
        activeChildIp = resolution.adapter!.ipv4;
      },
      resolveCurrentNetwork: async () => ({ adapter: null, rejectedReason: 'unused in this test' }),
    });

    const clock = createFakeClock();
    const watcher = new NetworkChangeWatcher({
      routeQuerier: querier,
      getLiveIpv4Addresses: () => new Set([liveAddress]),
      maxConsecutiveFailures: 2,
      onChange: (resolution) => {
        coordinator.requestEvaluation(resolution);
      },
      setIntervalImpl: clock.setIntervalImpl,
      clearIntervalImpl: clock.clearIntervalImpl,
    });

    await watcher.start();
    await coordinator.settle();
    expect(activeChildIp).toBe('192.168.29.34');

    // Physical network change: the machine moves to the hotspot. The route query hasn't caught up
    // yet and starts failing; the live-interface set flips immediately (this is what Node's own
    // os.networkInterfaces() would reflect right away, unlike the shelled-out route query).
    phase = 'transitioning';
    liveAddress = '10.142.207.231';
    await clock.fireTick(); // failure 1
    await coordinator.settle();
    await clock.fireTick(); // failure 2 — bound reached, fails closed
    await coordinator.settle();

    // The stale old-network child must not still be running once a genuine change is underway.
    expect(activeChildIp).toBeNull();

    // Route query recovers and now reports the new, live address.
    phase = 'onNewNetwork';
    await clock.fireTick();
    await coordinator.settle();

    expect(activeChildIp).toBe('10.142.207.231');
    // The old address must never have been (re)started after the transition began.
    expect(starts.filter((ip) => ip === '192.168.29.34')).toHaveLength(1);
    expect(starts.filter((ip) => ip === '10.142.207.231')).toHaveLength(1);
  });
});
