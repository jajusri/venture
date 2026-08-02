import type { ActiveNetworkResolution, TrustedLanEligibility } from './active-network-resolver.js';
import { evaluateTrustedLanEligibility } from './active-network-resolver.js';
import type { WindowsNetworkProfileCategory } from './route-querier.js';

export type TrustedLanBindStatus =
  | { readonly kind: 'connected' }
  | { readonly kind: 'blocked'; readonly reason: string; readonly profileCategory: WindowsNetworkProfileCategory | null }
  | { readonly kind: 'failed'; readonly message: string }
  | { readonly kind: 'stopped' };

export interface TrustedLanRebindCoordinatorDeps {
  /** Stops whatever LAN-bound child is currently running. Must be safe to call when nothing is running. */
  readonly stopCurrentChild: () => Promise<void>;
  /** Starts a new LAN-bound child for the given (already-known-eligible) resolution. May throw. */
  readonly startChildFor: (resolution: ActiveNetworkResolution) => Promise<void>;
  readonly onStatusChanged?: (status: TrustedLanBindStatus, resolution: ActiveNetworkResolution | null) => void;
  /** Injectable for tests; defaults to the real, shared policy function. */
  readonly evaluateEligibility?: (resolution: ActiveNetworkResolution) => TrustedLanEligibility;
  /**
   * Re-resolves the current network fresh, bypassing whatever the periodic watcher last cached.
   * Used only by the manual commands (`manualStart`/`manualRestart`) — a user clicking a button
   * must be judged against the network as it is right now, not a value up to one poll interval
   * stale.
   */
  readonly resolveCurrentNetwork: () => Promise<ActiveNetworkResolution>;
}

/**
 * Single-flight, generation-tracked coordinator for trusted-LAN Connector rebinds.
 *
 * This is the ONLY object allowed to start, stop, or replace the trusted-LAN Connector child.
 * That covers both triggers that exist in production:
 *  1. Automatic — `requestEvaluation`, driven by `NetworkChangeWatcher` and by startup/settings
 *     reinitialize.
 *  2. Manual — `manualStart` / `manualRestart` / `manualStop`, driven by the Desktop UI's
 *     Start/Restart/Stop buttons (`desktop:start-connector` etc. in main.ts). These used to call
 *     `ConnectorLifecycleService` directly, which bypassed policy entirely — a Public-network
 *     Connector could be manually (re)started with no eligibility check at all. Routing both
 *     triggers through the same generation counter and serialized chain means a manual command
 *     and an automatic rebind can never race for ownership of the child, and "latest wins"
 *     applies uniformly regardless of which trigger fired most recently.
 *
 * Two responsibilities:
 *  1. Policy enforcement — this is the ONE place a Public/unknown-profile network is actually
 *     prevented from reaching `startChildFor`. Status/UI reporting must read the same
 *     `evaluateTrustedLanEligibility` result (directly, or via this coordinator's published
 *     status) rather than re-deriving its own answer, so there is a single authoritative
 *     decision, not two that can drift apart.
 *  2. Concurrency safety — network observations and manual commands arrive independently of how
 *     long a previous transition takes. Every entry point coalesces with whatever is currently
 *     queued/running via a monotonic generation counter and a serialized promise chain, so at
 *     most one stop/start transition ever executes at a time, and a slower older transition can
 *     never overwrite a newer one's result (it detects its own staleness both before and after
 *     the expensive `startChildFor` call, undoing anything it started if superseded meanwhile).
 */
export class TrustedLanRebindCoordinator {
  private generation = 0;
  private latestQueuedGeneration = 0;
  private chain: Promise<void> = Promise.resolve();
  private shuttingDown = false;
  private lastStatus: TrustedLanBindStatus = {
    kind: 'blocked',
    reason: 'Not yet evaluated.',
    profileCategory: null,
  };

  constructor(private readonly deps: TrustedLanRebindCoordinatorDeps) {}

  getLastStatus(): TrustedLanBindStatus {
    return this.lastStatus;
  }

  /**
   * Monotonically increasing count of every enqueued transition (evaluated or superseded). Lets
   * a caller that schedules work *outside* the coordinator's own chain (e.g. a crash-recovery
   * backoff timer) detect whether it has been overtaken by literally any other trigger —
   * manual Stop/Restart, a network change, or shutdown — while it was waiting, without needing
   * its own separate staleness bookkeeping.
   */
  getGeneration(): number {
    return this.generation;
  }

  /**
   * Schedules evaluation of `resolution`. Never throws, never returns a promise the caller must
   * await (safe to call directly from a `NetworkChangeWatcher` onChange callback). Duplicate or
   * rapidly-superseded observations collapse to running the transition once for whichever
   * resolution is latest by the time the coordinator gets to it.
   */
  requestEvaluation(resolution: ActiveNetworkResolution): void {
    if (this.shuttingDown) {
      return;
    }
    void this.enqueue((generation) => this.runTransition(generation, resolution));
  }

  /**
   * Manual "Start Connector" button. Re-resolves the network fresh, then runs exactly the same
   * serialized stop-then-conditionally-start transition as an automatic rebind — sharing one
   * transition implementation is what guarantees a manual command can never leave a duplicate or
   * policy-violating child behind. Start and Restart are intentionally identical in mechanics:
   * the transition already always stops before starting, so there is no distinct "just start if
   * idle" shortcut that would be safe to take without re-checking eligibility.
   */
  async manualStart(): Promise<TrustedLanBindStatus> {
    return this.runManualStartOrRestart();
  }

  /** Manual "Restart Connector" button. See `manualStart` — mechanically identical. */
  async manualRestart(): Promise<TrustedLanBindStatus> {
    return this.runManualStartOrRestart();
  }

  /**
   * Crash/health-triggered recovery request (from ConnectorLifecycleService's `onUnexpectedExit`
   * via main.ts's bounded backoff). Mechanically identical to `manualStart` — same fresh
   * re-resolve, same authoritative eligibility check, same single-flight chain — so a recovery
   * attempt can never race a manual command or an automatic rebind for ownership of the child,
   * and a stale recovery can never publish `connected` over whatever a newer trigger decided.
   */
  async recoverFromUnexpectedExit(): Promise<TrustedLanBindStatus> {
    return this.runManualStartOrRestart();
  }

  private async runManualStartOrRestart(): Promise<TrustedLanBindStatus> {
    if (this.shuttingDown) {
      return this.lastStatus;
    }
    return this.enqueue(async (generation) => {
      const resolution = await this.deps.resolveCurrentNetwork();
      return this.runTransition(generation, resolution);
    });
  }

  /**
   * Manual "Stop Connector" button. Enters the same serialized chain — which by construction
   * supersedes (and thus invalidates) any older still-in-flight startup or rebind, since it is
   * assigned a newer generation — stops the child completely, and publishes `stopped`. A later
   * automatic or manual request can still recover normally; this does not disable future rebinds.
   */
  async manualStop(): Promise<TrustedLanBindStatus> {
    if (this.shuttingDown) {
      return this.lastStatus;
    }
    return this.enqueue(async (generation) => {
      await this.deps.stopCurrentChild();
      if (this.isStale(generation)) {
        // Superseded while stopping — the newer generation already queued behind us will publish
        // whatever the correct resulting state is; we must not overwrite it with a stale "stopped".
        return this.lastStatus;
      }
      return this.publish({ kind: 'stopped' }, null);
    });
  }

  /** Resolves once all currently-queued work has drained. For initial-startup awaiting and tests. */
  async settle(): Promise<void> {
    let previous: Promise<void> | null = null;
    let current = this.chain;
    while (current !== previous) {
      previous = current;
      await current.catch(() => undefined);
      current = this.chain;
    }
  }

  /**
   * Marks the coordinator as shutting down: no further evaluations or manual commands run, and
   * any transition still in flight will not restart or retain a child once it notices. Always
   * leaves zero children running once this resolves.
   */
  async shutdown(): Promise<void> {
    this.shuttingDown = true;
    this.generation += 1;
    this.latestQueuedGeneration = this.generation;
    await this.chain.catch(() => undefined);
    await this.deps.stopCurrentChild();
  }

  /**
   * Assigns the next generation to `work` and appends it to the serialized chain. Returns a
   * promise for `work`'s own result/rejection (for callers — e.g. manual commands — that need a
   * specific answer), while `this.chain` itself always swallows errors so one failed transition
   * never blocks the ones queued after it.
   */
  private enqueue<T>(work: (generation: number) => Promise<T>): Promise<T> {
    this.generation += 1;
    const generation = this.generation;
    this.latestQueuedGeneration = generation;
    const result = this.chain.catch(() => undefined).then(() => work(generation));
    this.chain = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  }

  private async runTransition(
    generation: number,
    resolution: ActiveNetworkResolution,
  ): Promise<TrustedLanBindStatus> {
    if (this.isStale(generation)) {
      return this.lastStatus;
    }

    await this.deps.stopCurrentChild();

    if (this.isStale(generation)) {
      // Left stopped on purpose — the generation that superseded us is next in the chain and
      // will decide correctly from this clean starting point.
      return this.lastStatus;
    }

    const evaluate = this.deps.evaluateEligibility ?? evaluateTrustedLanEligibility;
    const eligibility = evaluate(resolution);
    if (!eligibility.eligible) {
      return this.publish(
        {
          kind: 'blocked',
          reason: eligibility.reason ?? 'Trusted-LAN exposure is blocked.',
          profileCategory: resolution.adapter?.profileCategory ?? null,
        },
        resolution,
      );
    }

    try {
      await this.deps.startChildFor(resolution);
    } catch (error) {
      if (this.isStale(generation)) {
        return this.lastStatus;
      }
      return this.publish(
        { kind: 'failed', message: error instanceof Error ? error.message : 'Trusted-LAN rebind failed.' },
        resolution,
      );
    }

    if (this.isStale(generation)) {
      // Superseded while starting — undo it so an obsolete child never lingers or gets reported
      // as the current connection.
      await this.deps.stopCurrentChild();
      return this.lastStatus;
    }

    return this.publish({ kind: 'connected' }, resolution);
  }

  private isStale(generation: number): boolean {
    return this.shuttingDown || generation !== this.latestQueuedGeneration;
  }

  private publish(status: TrustedLanBindStatus, resolution: ActiveNetworkResolution | null): TrustedLanBindStatus {
    this.lastStatus = status;
    this.deps.onStatusChanged?.(status, resolution);
    return status;
  }
}
