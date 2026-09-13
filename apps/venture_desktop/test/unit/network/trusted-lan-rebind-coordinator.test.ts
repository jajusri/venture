import { describe, expect, it } from 'vitest';

import type { ActiveNetworkResolution } from '../../../src/application/network/active-network-resolver.js';
import {
  TrustedLanRebindCoordinator,
  type TrustedLanBindStatus,
} from '../../../src/application/network/trusted-lan-rebind-coordinator.js';

interface Deferred<T> {
  readonly promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (error: unknown) => void;
}

function createDeferred<T = void>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/**
 * Drains the microtask queue, one tick at a time, until `predicate` is true. Deterministic —
 * everything here is single-threaded microtask scheduling, never wall-clock time — but avoids
 * hand-counting exactly how many `.then`/`.catch` hops a coordinator transition needs internally
 * before it reaches a gated `await` inside a test double.
 */
async function waitFor(predicate: () => boolean, maxTicks = 50): Promise<void> {
  for (let i = 0; i < maxTicks; i += 1) {
    if (predicate()) {
      return;
    }
    await Promise.resolve();
  }
  throw new Error('waitFor: condition not met within microtask budget');
}

function privateResolution(overrides?: { readonly ipv4?: string; readonly adapterName?: string }): ActiveNetworkResolution {
  return {
    adapter: {
      adapterId: 'adapter-private',
      adapterName: overrides?.adapterName ?? 'Wi-Fi',
      ipv4: overrides?.ipv4 ?? '192.168.50.10',
      prefixLength: 24,
      gateway: '192.168.50.1',
      profileCategory: 'Private',
      routeMetric: 25,
    },
    rejectedReason: null,
  };
}

function publicResolution(overrides?: { readonly ipv4?: string }): ActiveNetworkResolution {
  return {
    adapter: {
      adapterId: 'adapter-public',
      adapterName: 'Wi-Fi',
      ipv4: overrides?.ipv4 ?? '192.168.50.10',
      prefixLength: 24,
      gateway: '192.168.50.1',
      profileCategory: 'Public',
      routeMetric: 25,
    },
    rejectedReason: null,
  };
}

const noAdapterResolution: ActiveNetworkResolution = {
  adapter: null,
  rejectedReason: 'No default-route network adapter was found.',
};

/** Test harness: tracks call order and a live-child counter so "at most one child" is provable. */
function createHarness() {
  const calls: string[] = [];
  let activeChildren = 0;
  const statuses: TrustedLanBindStatus[] = [];
  let stopController: Deferred<void> | null = null;
  let startController: Deferred<void> | null = null;
  let startShouldReject = false;
  let resolveController: Deferred<void> | null = null;
  let manualResolution: ActiveNetworkResolution = privateResolution();
  let resolveCallCount = 0;

  const coordinator = new TrustedLanRebindCoordinator({
    stopCurrentChild: async () => {
      calls.push('stop');
      if (stopController) {
        await stopController.promise;
      }
      if (activeChildren > 0) {
        activeChildren -= 1;
      }
    },
    startChildFor: async (resolution) => {
      calls.push(`start:${resolution.adapter?.ipv4 ?? 'none'}`);
      if (startController) {
        await startController.promise;
      }
      if (startShouldReject) {
        startShouldReject = false;
        throw new Error('simulated start failure');
      }
      activeChildren += 1;
    },
    onStatusChanged: (status) => {
      statuses.push(status);
    },
    resolveCurrentNetwork: async () => {
      resolveCallCount += 1;
      calls.push(`resolve:${manualResolution.adapter?.ipv4 ?? manualResolution.adapter?.profileCategory ?? 'none'}`);
      if (resolveController) {
        await resolveController.promise;
      }
      return manualResolution;
    },
  });

  return {
    coordinator,
    calls,
    statuses,
    getActiveChildren: () => activeChildren,
    getResolveCallCount: () => resolveCallCount,
    armStopGate: () => (stopController = createDeferred<void>()),
    releaseStopGate: () => stopController?.resolve(),
    armStartGate: () => (startController = createDeferred<void>()),
    releaseStartGate: () => startController?.resolve(),
    armResolveGate: () => (resolveController = createDeferred<void>()),
    releaseResolveGate: () => resolveController?.resolve(),
    failNextStart: () => (startShouldReject = true),
    setManualResolution: (resolution: ActiveNetworkResolution) => {
      manualResolution = resolution;
    },
  };
}

describe('TrustedLanRebindCoordinator — Blocker 1: authoritative policy enforcement', () => {
  it('allows a trusted-LAN child to start on a Private profile', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(privateResolution());
    await h.coordinator.settle();

    expect(h.calls).toEqual(['stop', 'start:192.168.50.10']);
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
    expect(h.getActiveChildren()).toBe(1);
  });

  it('refuses to start a child on a Public profile using the real (non-injected) policy', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(publicResolution());
    await h.coordinator.settle();

    expect(h.calls).toEqual(['stop']);
    expect(h.calls.some((c) => c.startsWith('start:'))).toBe(false);
    const status = h.coordinator.getLastStatus();
    expect(status.kind).toBe('blocked');
    if (status.kind === 'blocked') {
      expect(status.profileCategory).toBe('Public');
      expect(status.reason).toContain('Public');
    }
  });

  it('fails closed when no adapter/profile evidence is available (Unknown/no-route case)', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(noAdapterResolution);
    await h.coordinator.settle();

    expect(h.calls.some((c) => c.startsWith('start:'))).toBe(false);
    expect(h.coordinator.getLastStatus().kind).toBe('blocked');
  });

  it('a valid-looking RFC1918 IPv4 literal cannot bypass a Public profile classification', async () => {
    const h = createHarness();
    // Same private-range address as the allowed test, but on a Public Windows profile — proves
    // eligibility is decided by profileCategory, never by inspecting the IP literal.
    h.coordinator.requestEvaluation(publicResolution({ ipv4: '192.168.50.10' }));
    await h.coordinator.settle();

    expect(h.calls.some((c) => c.startsWith('start:'))).toBe(false);
    expect(h.coordinator.getLastStatus().kind).toBe('blocked');
  });

  it('Private -> Public: stops the existing child and starts nothing new', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(privateResolution());
    await h.coordinator.settle();
    expect(h.getActiveChildren()).toBe(1);
    h.calls.length = 0;

    h.coordinator.requestEvaluation(publicResolution());
    await h.coordinator.settle();

    expect(h.calls).toEqual(['stop']);
    expect(h.getActiveChildren()).toBe(0);
    expect(h.coordinator.getLastStatus().kind).toBe('blocked');
  });

  it('Public -> Private: starts exactly one child once the network becomes trusted', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(publicResolution());
    await h.coordinator.settle();
    expect(h.getActiveChildren()).toBe(0);
    h.calls.length = 0;

    h.coordinator.requestEvaluation(privateResolution());
    await h.coordinator.settle();

    expect(h.calls).toEqual(['stop', 'start:192.168.50.10']);
    expect(h.getActiveChildren()).toBe(1);
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('DomainAuthenticated is treated as trusted, same as Private', async () => {
    const h = createHarness();
    const resolution: ActiveNetworkResolution = {
      adapter: {
        adapterId: 'adapter-domain',
        adapterName: 'Ethernet',
        ipv4: '10.0.5.20',
        prefixLength: 24,
        gateway: '10.0.5.1',
        profileCategory: 'DomainAuthenticated',
        routeMetric: 10,
      },
      rejectedReason: null,
    };
    h.coordinator.requestEvaluation(resolution);
    await h.coordinator.settle();

    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('local-only mode never reaches this coordinator at all (no requestEvaluation call = no side effects)', async () => {
    const h = createHarness();
    // Nothing requested — simulates a local-only-mode installation, where main.ts never routes
    // through the coordinator. Asserts the coordinator itself is inert until asked.
    await h.coordinator.settle();

    expect(h.calls).toEqual([]);
    expect(h.getActiveChildren()).toBe(0);
    expect(h.coordinator.getLastStatus().kind).toBe('blocked');
  });

  it('published status and getLastStatus() always agree — one decision, not two', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(publicResolution());
    await h.coordinator.settle();

    expect(h.statuses.length).toBe(1);
    expect(h.statuses[0]).toEqual(h.coordinator.getLastStatus());
  });

  it('the injected evaluateEligibility fake is honoured, proving policy is a single pluggable decision point', async () => {
    const h = createHarness();
    const coordinatorWithFake = new TrustedLanRebindCoordinator({
      stopCurrentChild: async () => {},
      startChildFor: async () => {},
      evaluateEligibility: () => ({ eligible: false, reason: 'forced-block-by-test' }),
    });

    coordinatorWithFake.requestEvaluation(privateResolution());
    await coordinatorWithFake.settle();

    const status = coordinatorWithFake.getLastStatus();
    expect(status.kind).toBe('blocked');
    if (status.kind === 'blocked') {
      expect(status.reason).toBe('forced-block-by-test');
    }
  });

  it('a failed start is reported as failed, not silently reported as connected', async () => {
    const h = createHarness();
    h.failNextStart();
    h.coordinator.requestEvaluation(privateResolution());
    await h.coordinator.settle();

    const status = h.coordinator.getLastStatus();
    expect(status.kind).toBe('failed');
    expect(h.getActiveChildren()).toBe(0);
  });
});

describe('TrustedLanRebindCoordinator — Blocker 2: single-flight generation-tracked rebinds', () => {
  it('two requestEvaluation calls issued back-to-back never leave more than one active child', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '192.168.1.10' }));
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '192.168.1.20' }));
    await h.coordinator.settle();

    expect(h.getActiveChildren()).toBe(1);
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('three rapid changes: only the latest resolution wins, stale ones are skipped entirely', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.1' }));
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.2' }));
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.3' }));
    await h.coordinator.settle();

    // Only one stop/start cycle actually ran — the two superseded generations were skipped
    // before touching stopCurrentChild/startChildFor at all.
    expect(h.calls).toEqual(['stop', 'start:10.0.0.3']);
  });

  it('an older, slow-to-start transition cannot overwrite a newer one that already completed', async () => {
    const h = createHarness();
    h.armStartGate();
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.1' })); // generation 1, slow start
    const settlePromise = h.coordinator.settle();

    // Let generation 1 reach and block inside startChildFor, then supersede it.
    await waitFor(() => h.calls.includes('start:10.0.0.1'));
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.2' })); // generation 2

    h.releaseStartGate(); // generation 1's start now completes, but it is stale
    await settlePromise;

    // Generation 1 detected it was superseded after its slow start finished and undid it
    // (extra stop), then generation 2 ran its own clean stop/start.
    expect(h.calls).toEqual(['stop', 'start:10.0.0.1', 'stop', 'stop', 'start:10.0.0.2']);
    expect(h.getActiveChildren()).toBe(1);
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('duplicate/equivalent rapid observations coalesce into a single stop/start cycle', async () => {
    const h = createHarness();
    const resolution = privateResolution({ ipv4: '10.0.0.5' });
    h.coordinator.requestEvaluation(resolution);
    h.coordinator.requestEvaluation(resolution);
    h.coordinator.requestEvaluation(resolution);
    await h.coordinator.settle();

    expect(h.calls).toEqual(['stop', 'start:10.0.0.5']);
  });

  it('stop always precedes start within a single transition', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(privateResolution());
    await h.coordinator.settle();

    expect(h.calls.indexOf('stop')).toBeLessThan(h.calls.findIndex((c) => c.startsWith('start:')));
  });

  it('a failed transition releases the coordinator so the next request is not stuck', async () => {
    const h = createHarness();
    h.failNextStart();
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.1' }));
    await h.coordinator.settle();
    expect(h.coordinator.getLastStatus().kind).toBe('failed');

    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.2' }));
    await h.coordinator.settle();

    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
    expect(h.getActiveChildren()).toBe(1);
  });

  it('a later valid change succeeds even while an earlier transition is still in flight', async () => {
    const h = createHarness();
    h.armStopGate();
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.1' }));
    const settlePromise = h.coordinator.settle();

    await waitFor(() => h.calls.includes('stop'));
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.2' }));
    h.releaseStopGate();
    await settlePromise;

    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
    expect(h.calls[h.calls.length - 1]).toBe('start:10.0.0.2');
  });

  it('shutdown mid-rebind always leaves zero children running, even if a start was in flight', async () => {
    const h = createHarness();
    h.armStartGate();
    h.coordinator.requestEvaluation(privateResolution());
    await waitFor(() => h.calls.some((c) => c.startsWith('start:')));

    const shutdownPromise = h.coordinator.shutdown();
    h.releaseStartGate();
    await shutdownPromise;

    expect(h.getActiveChildren()).toBe(0);
  });

  it('after shutdown, further requestEvaluation calls are inert (no new children start)', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(privateResolution());
    await h.coordinator.settle();
    await h.coordinator.shutdown();
    h.calls.length = 0;

    h.coordinator.requestEvaluation(privateResolution());
    await h.coordinator.settle();

    expect(h.calls).toEqual([]);
    expect(h.getActiveChildren()).toBe(0);
  });

  it('Public-profile blocking runs through the same serialized single-flight path as a normal rebind', async () => {
    const h = createHarness();
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.1' }));
    h.coordinator.requestEvaluation(publicResolution());
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.2' }));
    await h.coordinator.settle();

    // Every intermediate generation was skipped except the last — the Public transition never
    // got its own out-of-band handling, it was just another generation in the same chain.
    expect(h.calls).toEqual(['stop', 'start:10.0.0.2']);
    expect(h.getActiveChildren()).toBe(1);
  });
});

describe('TrustedLanRebindCoordinator — manual Start/Stop/Restart (bypass fix)', () => {
  it('1. Manual Start on Private starts exactly one child', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());

    const status = await h.coordinator.manualStart();

    expect(h.getActiveChildren()).toBe(1);
    expect(status).toEqual({ kind: 'connected' });
    expect(h.calls).toEqual(['resolve:192.168.50.10', 'stop', 'start:192.168.50.10']);
  });

  it('2. Manual Start on Public starts zero children and returns blocked', async () => {
    const h = createHarness();
    h.setManualResolution(publicResolution());

    const status = await h.coordinator.manualStart();

    expect(h.getActiveChildren()).toBe(0);
    expect(status.kind).toBe('blocked');
    expect(h.calls.some((c) => c.startsWith('start:'))).toBe(false);
  });

  it('3. Manual Start with unknown/missing profile evidence starts zero children', async () => {
    const h = createHarness();
    h.setManualResolution(noAdapterResolution);

    const status = await h.coordinator.manualStart();

    expect(h.getActiveChildren()).toBe(0);
    expect(status.kind).toBe('blocked');
  });

  it('4. Manual Restart on Private performs stop-before-start against an already-running child', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    await h.coordinator.manualStart();
    expect(h.getActiveChildren()).toBe(1);
    h.calls.length = 0;

    const status = await h.coordinator.manualRestart();

    expect(h.calls.indexOf('stop')).toBeLessThan(h.calls.findIndex((c) => c.startsWith('start:')));
    expect(h.getActiveChildren()).toBe(1);
    expect(status).toEqual({ kind: 'connected' });
  });

  it('5. Manual Restart on Public stops an existing child and does not replace it', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    await h.coordinator.manualStart();
    expect(h.getActiveChildren()).toBe(1);

    h.setManualResolution(publicResolution());
    const status = await h.coordinator.manualRestart();

    expect(h.getActiveChildren()).toBe(0);
    expect(status.kind).toBe('blocked');
  });

  it('6. Manual Stop leaves zero children', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    await h.coordinator.manualStart();
    expect(h.getActiveChildren()).toBe(1);

    const status = await h.coordinator.manualStop();

    expect(h.getActiveChildren()).toBe(0);
    expect(status).toEqual({ kind: 'stopped' });
  });

  it('7. Manual Stop updates coordinator/status state consistently', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    await h.coordinator.manualStart();

    await h.coordinator.manualStop();

    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'stopped' });
    expect(h.statuses[h.statuses.length - 1]).toEqual({ kind: 'stopped' });
  });

  it('8. Manual Stop supersedes an older in-flight manual Start', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    h.armStartGate();
    const startPromise = h.coordinator.manualStart();
    await waitFor(() => h.calls.some((c) => c.startsWith('start:')));

    const stopPromise = h.coordinator.manualStop();
    h.releaseStartGate();
    const [startResult, stopResult] = await Promise.all([startPromise, stopPromise]);

    expect(h.getActiveChildren()).toBe(0);
    expect(stopResult).toEqual({ kind: 'stopped' });
    // The superseded Start must not have published "connected" over the Stop.
    expect(startResult).not.toEqual({ kind: 'connected' });
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'stopped' });
  });

  it('9. Automatic rebind and manual Restart cannot overlap child ownership', async () => {
    const h = createHarness();
    h.armStartGate();
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.1' })); // automatic, generation 1
    await waitFor(() => h.calls.some((c) => c.startsWith('start:10.0.0.1')));

    h.setManualResolution(privateResolution({ ipv4: '10.0.0.2' }));
    const restartPromise = h.coordinator.manualRestart(); // generation 2, queued behind gen 1
    h.releaseStartGate();
    await restartPromise;

    // At no point could both generations' children coexist: gen 1 detects it was superseded
    // after its slow start and undoes itself; gen 2 then runs its own clean stop/start.
    expect(h.getActiveChildren()).toBe(1);
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('10. A Public transition arriving during manual Start leaves zero children', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    h.armStartGate();
    const startPromise = h.coordinator.manualStart();
    await waitFor(() => h.calls.some((c) => c.startsWith('start:')));

    h.coordinator.requestEvaluation(publicResolution()); // supersedes the in-flight manual Start
    h.releaseStartGate();
    await startPromise;
    await h.coordinator.settle();

    expect(h.getActiveChildren()).toBe(0);
    expect(h.coordinator.getLastStatus().kind).toBe('blocked');
  });

  it('11. A Private transition arriving after a blocked manual Start can recover', async () => {
    const h = createHarness();
    h.setManualResolution(publicResolution());
    const blocked = await h.coordinator.manualStart();
    expect(blocked.kind).toBe('blocked');

    h.coordinator.requestEvaluation(privateResolution());
    await h.coordinator.settle();

    expect(h.getActiveChildren()).toBe(1);
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('12. Duplicate manual Start requests do not create duplicate children', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());

    const [, second] = await Promise.all([h.coordinator.manualStart(), h.coordinator.manualStart()]);

    // The second call is issued synchronously right after the first, so it is assigned the
    // newer generation and supersedes the first before the first's work even begins — the first
    // call's own promise resolves to whatever the coordinator's state was at that point (it never
    // gets to observe or influence the outcome), while the second call is the one that actually
    // runs the transition and owns the result.
    expect(h.getActiveChildren()).toBe(1);
    expect(second).toEqual({ kind: 'connected' });
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
    // Exactly one generation actually reached startChildFor; the superseded one was skipped
    // before ever calling stop/start.
    expect(h.calls.filter((c) => c.startsWith('start:')).length).toBe(1);
  });

  it('13. Restart and a network change resolve to latest-state-wins, not queue-everything', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution({ ipv4: '10.0.0.9' }));
    h.armResolveGate();
    const restartPromise = h.coordinator.manualRestart();
    await waitFor(() => h.getResolveCallCount() >= 1);

    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.5' }));
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.6' }));
    h.releaseResolveGate();
    await restartPromise;
    await h.coordinator.settle();

    expect(h.getActiveChildren()).toBe(1);
    expect(h.calls[h.calls.length - 1]).toBe('start:10.0.0.6');
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('14. Desktop shutdown during a manual Start leaves zero children', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    h.armStartGate();
    const startPromise = h.coordinator.manualStart();
    await waitFor(() => h.calls.some((c) => c.startsWith('start:')));

    const shutdownPromise = h.coordinator.shutdown();
    h.releaseStartGate();
    await Promise.all([startPromise, shutdownPromise]);

    expect(h.getActiveChildren()).toBe(0);

    // Shutdown must also block any further manual command from starting a new child.
    const afterShutdown = await h.coordinator.manualStart();
    expect(h.getActiveChildren()).toBe(0);
    expect(afterShutdown).toEqual(h.coordinator.getLastStatus());
  });
});

describe('TrustedLanRebindCoordinator — crash recovery (recoverFromUnexpectedExit)', () => {
  it('1/2. crash recovery on Private re-resolves fresh and starts exactly one replacement child', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());

    const status = await h.coordinator.recoverFromUnexpectedExit();

    expect(h.getActiveChildren()).toBe(1);
    expect(status).toEqual({ kind: 'connected' });
    expect(h.calls).toEqual(['resolve:192.168.50.10', 'stop', 'start:192.168.50.10']);
  });

  it('3. crash recovery on Public starts zero children', async () => {
    const h = createHarness();
    h.setManualResolution(publicResolution());

    const status = await h.coordinator.recoverFromUnexpectedExit();

    expect(h.getActiveChildren()).toBe(0);
    expect(status.kind).toBe('blocked');
    expect(h.calls.some((c) => c.startsWith('start:'))).toBe(false);
  });

  it('4. crash recovery with unknown/missing profile evidence starts zero children', async () => {
    const h = createHarness();
    h.setManualResolution(noAdapterResolution);

    const status = await h.coordinator.recoverFromUnexpectedExit();

    expect(h.getActiveChildren()).toBe(0);
    expect(status.kind).toBe('blocked');
  });

  it('6. Public -> Private allows a later bounded recovery to succeed', async () => {
    const h = createHarness();
    h.setManualResolution(publicResolution());
    const blocked = await h.coordinator.recoverFromUnexpectedExit();
    expect(blocked.kind).toBe('blocked');

    h.setManualResolution(privateResolution());
    const recovered = await h.coordinator.recoverFromUnexpectedExit();

    expect(h.getActiveChildren()).toBe(1);
    expect(recovered).toEqual({ kind: 'connected' });
  });

  it('7. a newer route arriving during crash recovery is what actually gets used', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution({ ipv4: '10.0.0.1' }));
    h.armResolveGate();
    const recoveryPromise = h.coordinator.recoverFromUnexpectedExit();
    await waitFor(() => h.getResolveCallCount() >= 1);

    // A network change lands while the crash-recovery's own resolve is still in flight.
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.9' }));
    h.releaseResolveGate();
    await recoveryPromise;
    await h.coordinator.settle();

    expect(h.getActiveChildren()).toBe(1);
    expect(h.calls[h.calls.length - 1]).toBe('start:10.0.0.9');
  });

  it('8. manual Stop supersedes a pending crash recovery', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    h.armStartGate();
    const recoveryPromise = h.coordinator.recoverFromUnexpectedExit();
    await waitFor(() => h.calls.some((c) => c.startsWith('start:')));

    const stopPromise = h.coordinator.manualStop();
    h.releaseStartGate();
    const [recoveryResult, stopResult] = await Promise.all([recoveryPromise, stopPromise]);

    expect(h.getActiveChildren()).toBe(0);
    expect(stopResult).toEqual({ kind: 'stopped' });
    expect(recoveryResult).not.toEqual({ kind: 'connected' });
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'stopped' });
  });

  it('9. manual Restart supersedes an older pending crash recovery', async () => {
    const h = createHarness();
    h.armStartGate();
    h.setManualResolution(privateResolution({ ipv4: '10.0.0.1' }));
    const recoveryPromise = h.coordinator.recoverFromUnexpectedExit();
    await waitFor(() => h.calls.some((c) => c.startsWith('start:10.0.0.1')));

    h.setManualResolution(privateResolution({ ipv4: '10.0.0.2' }));
    const restartPromise = h.coordinator.manualRestart();
    h.releaseStartGate();
    await Promise.all([recoveryPromise, restartPromise]);

    expect(h.getActiveChildren()).toBe(1);
    expect(h.calls[h.calls.length - 1]).toBe('start:10.0.0.2');
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('10. network-driven rebind and crash recovery cannot own children concurrently', async () => {
    const h = createHarness();
    h.armStartGate();
    h.coordinator.requestEvaluation(privateResolution({ ipv4: '10.0.0.1' }));
    await waitFor(() => h.calls.some((c) => c.startsWith('start:10.0.0.1')));

    h.setManualResolution(privateResolution({ ipv4: '10.0.0.2' }));
    const recoveryPromise = h.coordinator.recoverFromUnexpectedExit();
    h.releaseStartGate();
    await recoveryPromise;

    expect(h.getActiveChildren()).toBe(1);
    expect(h.coordinator.getLastStatus()).toEqual({ kind: 'connected' });
  });

  it('11. duplicate crash-recovery requests do not create duplicate children', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());

    const [, second] = await Promise.all([
      h.coordinator.recoverFromUnexpectedExit(),
      h.coordinator.recoverFromUnexpectedExit(),
    ]);

    expect(h.getActiveChildren()).toBe(1);
    expect(second).toEqual({ kind: 'connected' });
    expect(h.calls.filter((c) => c.startsWith('start:')).length).toBe(1);
  });

  it('13. Desktop shutdown cancels a pending crash recovery', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution());
    h.armStartGate();
    const recoveryPromise = h.coordinator.recoverFromUnexpectedExit();
    await waitFor(() => h.calls.some((c) => c.startsWith('start:')));

    const shutdownPromise = h.coordinator.shutdown();
    h.releaseStartGate();
    await Promise.all([recoveryPromise, shutdownPromise]);

    expect(h.getActiveChildren()).toBe(0);
  });

  it('14. a failed recovery releases the serialized chain for a later attempt', async () => {
    const h = createHarness();
    h.setManualResolution(privateResolution({ ipv4: '10.0.0.1' }));
    h.failNextStart();

    const failed = await h.coordinator.recoverFromUnexpectedExit();
    expect(failed.kind).toBe('failed');

    h.setManualResolution(privateResolution({ ipv4: '10.0.0.2' }));
    const recovered = await h.coordinator.recoverFromUnexpectedExit();

    expect(recovered).toEqual({ kind: 'connected' });
    expect(h.getActiveChildren()).toBe(1);
  });

  it('18/19. a stale recovery cannot publish Connected nor retain a child once superseded', async () => {
    const h = createHarness();
    h.armStartGate();
    h.setManualResolution(privateResolution({ ipv4: '10.0.0.1' }));
    const recoveryPromise = h.coordinator.recoverFromUnexpectedExit();
    await waitFor(() => h.calls.some((c) => c.startsWith('start:10.0.0.1')));

    h.coordinator.requestEvaluation(publicResolution()); // supersedes with a Public observation
    h.releaseStartGate();
    await recoveryPromise;
    await h.coordinator.settle();

    expect(h.getActiveChildren()).toBe(0);
    expect(h.coordinator.getLastStatus().kind).toBe('blocked');
  });
});
