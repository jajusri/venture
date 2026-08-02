import { describe, expect, it } from 'vitest';

import { NetworkChangeWatcher } from '../../../src/application/network/network-change-watcher.js';
import { FakeRouteQuerier } from '../../../src/application/network/route-querier.js';
import type { ActiveNetworkResolution } from '../../../src/application/network/active-network-resolver.js';
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
