export interface CrashRecoverySchedulerDeps {
  readonly getMaxAttempts: () => number;
  readonly getBaseDelayMs: () => number;
  /** Reads TrustedLanRebindCoordinator.getGeneration() — see class doc for why. */
  readonly getGeneration: () => number;
  /** Fire-and-forget request that the owner (TrustedLanRebindCoordinator) attempt recovery. */
  readonly recover: () => void;
  /** Called once the bounded attempt budget is exhausted; no further retries will be scheduled. */
  readonly onExhausted: () => void;
  readonly setTimeoutImpl?: (handler: () => void, ms: number) => ReturnType<typeof setTimeout>;
  readonly clearTimeoutImpl?: (handle: ReturnType<typeof setTimeout>) => void;
}

/**
 * Bounded backoff/attempt-limit policy for trusted-LAN crash recovery, living at the ownership
 * layer (main.ts) rather than inside `ConnectorLifecycleService`. A coordinator-driven recovery
 * always constructs a brand-new `ConnectorLifecycleService` instance (fresh route-derived config)
 * — a per-instance attempt counter would reset to zero on every single recovery and never
 * actually bound anything, allowing an unlimited restart loop. Tracking attempts here instead
 * means the bound survives every instance recreation.
 *
 * Cancellation is generation-based rather than requiring every coordinator trigger (manual Stop,
 * manual Restart, a network change, shutdown) to remember to explicitly cancel a pending timer:
 * `TrustedLanRebindCoordinator.getGeneration()` increases on literally every enqueued transition,
 * so comparing "generation when I scheduled" against "generation when my timer fires" detects
 * being overtaken by *anything*, uniformly, with no per-trigger wiring required.
 */
export class CrashRecoveryScheduler {
  private attempts = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly deps: CrashRecoverySchedulerDeps) {}

  /** Call once per unexpected-exit notification. */
  scheduleRecovery(): void {
    const maxAttempts = this.deps.getMaxAttempts();
    if (this.attempts >= maxAttempts) {
      this.deps.onExhausted();
      return;
    }

    this.attempts += 1;
    const delay = this.deps.getBaseDelayMs() * this.attempts;
    const generationAtScheduling = this.deps.getGeneration();
    this.clearTimer();

    const setTimeoutImpl = this.deps.setTimeoutImpl ?? setTimeout;
    this.timer = setTimeoutImpl(() => {
      this.timer = null;
      if (this.deps.getGeneration() !== generationAtScheduling) {
        // Superseded while waiting out the backoff — a manual Stop/Restart, a network change, or
        // shutdown already moved the coordinator past this crash's context. Resurrecting a child
        // now would undo whatever that newer event decided, so this attempt is abandoned and the
        // stale count is dropped rather than carried into a future, unrelated crash.
        this.attempts = 0;
        return;
      }
      this.deps.recover();
    }, delay);
  }

  /** Call when a genuinely healthy connection is achieved (via any trigger). */
  notifyRecovered(): void {
    this.attempts = 0;
  }

  /** Call on a deliberate stop (manual Stop, Desktop shutdown) — ends this crash-recovery context. */
  notifyStoppedIntentionally(): void {
    this.attempts = 0;
    this.clearTimer();
  }

  getPendingAttempts(): number {
    return this.attempts;
  }

  hasPendingRecovery(): boolean {
    return this.timer !== null;
  }

  private clearTimer(): void {
    if (this.timer) {
      const clear = this.deps.clearTimeoutImpl ?? clearTimeout;
      clear(this.timer);
      this.timer = null;
    }
  }
}
