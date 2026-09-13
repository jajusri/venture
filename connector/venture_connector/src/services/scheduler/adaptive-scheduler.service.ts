import type { ServiceStatus } from '../../core/types.js';
import type { SyncResourceKind } from '../../erp/ledger/ledger-domain.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import type { ConnectorSessionService } from '../interfaces/connector-session.js';
import type { SchedulerService } from '../interfaces/scheduler.js';
import type { LedgerSyncService } from '../ledger/ledger-sync.service.js';
import type { StockItemSyncService } from '../stock-item/stock-item-sync.service.js';
import type { SchedulerStateRepository } from '../../storage/sqlite/scheduler-state-repository.js';
import {
  applySyncOutcome,
  hasDetectableChange,
  initialSchedulerState,
  SCHEDULER_SYNC_OPTIONS,
  type SchedulerState,
  type SchedulerSyncOutcome,
} from './adaptive-scheduler-domain.js';

const RESOURCE_KINDS: readonly SyncResourceKind[] = ['ledgers', 'stock-items'];

export interface SchedulerStateSummary {
  readonly stage: SchedulerState['stage'];
  readonly nextCheckDueAt: string;
}

/**
 * The richer surface API routes need beyond the generic [SchedulerService] lifecycle contract
 * (which only exists for `HealthService`'s uniform service-status reporting). Kept as a separate
 * interface rather than widening [SchedulerService] itself, since most callers of that interface
 * have no business observing sync outcomes or reading scheduler state.
 */
export interface AdaptiveScheduler {
  /**
   * Observes a manual "Sync Now" outcome and updates persisted scheduler state exactly as an
   * automatic check would have — no separate code path (architecture §5/§16: "state machine
   * reacts exactly as if its own timer had fired").
   */
  recordManualSyncOutcome(companyId: string, resourceKind: SyncResourceKind, succeeded: boolean): void;
  /** The currently-selected company's own scheduler state for [resourceKind], for UI display only. */
  getCurrentSchedulerState(resourceKind: SyncResourceKind): SchedulerStateSummary | null;
  /**
   * Runs one tick, awaitable (checks whichever of the currently-selected company's resources are
   * due, triggers a real sync for each). The interval timer calls this on its own schedule;
   * exposed publicly so it is deterministically testable without mocking timers, and as a natural
   * hook for a future "check now" diagnostic action.
   */
  runOnce(): Promise<void>;
}

/**
 * Adaptive Tally Synchronization scheduler
 * (`docs/architecture/VENTURE-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md`).
 *
 * A pure *trigger* — every check calls the exact same `syncLedgers()`/`syncStockItems()` path
 * manual "Sync Now" already uses, inheriting every existing safety mechanism automatically
 * (per-company single-flight, rate limiting, circuit breaker, sanitization). No second sync
 * engine exists here.
 *
 * Company boundary: the Connector has exactly one active Tally connection/session at a time
 * (architecture §8), so this service only ever acts on whichever company is *currently selected*
 * — it does not (and must not) silently switch the Connector's session to service a different
 * company in the background. Each company's own scheduler state is still fully persisted and
 * independent (§8's `Map`/table-keyed-by-company requirement), so switching back to a
 * previously-active company resumes its own history correctly; only one company's schedule is
 * ever *ticking* at a time, which is the only model consistent with a single Tally connection.
 */
export class AdaptiveSchedulerService implements SchedulerService, AdaptiveScheduler {
  private running = false;
  private timer: ReturnType<typeof setInterval> | null = null;
  private ticking = false;

  constructor(
    private readonly repository: SchedulerStateRepository,
    private readonly connectorSession: ConnectorSessionService,
    private readonly ledgerSync: LedgerSyncService,
    private readonly stockItemSync: StockItemSyncService,
    private readonly logger: Logger,
    private readonly tickIntervalMs: number = 30_000,
  ) {}

  async start(): Promise<void> {
    if (this.running) return;
    this.running = true;
    this.timer = setInterval(() => {
      void this.runOnce();
    }, this.tickIntervalMs);
    this.timer.unref?.();
    // Catch up immediately on startup rather than waiting a full tick interval — restart safety:
    // a check that was already due while the Connector was down (or during a normal restart)
    // fires as soon as possible, not after an extra unrelated delay.
    void this.runOnce();
  }

  async stop(): Promise<void> {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.running = false;
  }

  isRunning(): boolean {
    return this.running;
  }

  getStatus(): ServiceStatus {
    return {
      name: 'Scheduler',
      running: this.running,
      ready: this.running,
      message: this.running ? 'active' : 'stopped',
    };
  }

  recordManualSyncOutcome(companyId: string, resourceKind: SyncResourceKind, succeeded: boolean): void {
    const current = this.repository.find(companyId, resourceKind) ?? initialSchedulerState(companyId, resourceKind, Date.now());
    // A manual trigger is itself strong, honest evidence of active use — not a fingerprint claim.
    // Manual syncs are non-incremental (existing, unchanged default), so the `changes` array
    // can't distinguish a real diff from "every record re-written" — see
    // adaptive-scheduler-domain.ts's `hasDetectableChange` doc comment for why this asymmetry
    // with the scheduler's own incremental checks is deliberate, not an oversight.
    const outcome: SchedulerSyncOutcome = succeeded ? 'change_detected' : 'failed';
    this.repository.upsert(applySyncOutcome(current, outcome, Date.now()));
  }

  getCurrentSchedulerState(resourceKind: SyncResourceKind): SchedulerStateSummary | null {
    const companyId = this.peekCompanyId();
    if (!companyId) return null;
    const state = this.repository.find(companyId, resourceKind);
    if (!state) return null;
    return { stage: state.stage, nextCheckDueAt: state.nextCheckDueAt };
  }

  private peekCompanyId(): string | null {
    try {
      return this.connectorSession.getSession().session.selectedCompany?.id ?? null;
    } catch {
      return null;
    }
  }

  async runOnce(): Promise<void> {
    if (this.ticking) return;
    this.ticking = true;
    try {
      const companyId = this.peekCompanyId();
      if (!companyId) return;
      for (const resourceKind of RESOURCE_KINDS) {
        await this.checkResourceIfDue(companyId, resourceKind);
      }
    } catch (error) {
      this.logger.warn('scheduler_tick_failed', {
        component: 'adaptive-scheduler',
        message: error instanceof Error ? error.message : String(error),
      });
    } finally {
      this.ticking = false;
    }
  }

  private async checkResourceIfDue(companyId: string, resourceKind: SyncResourceKind): Promise<void> {
    const nowMs = Date.now();
    const existing = this.repository.find(companyId, resourceKind);
    const current = existing ?? initialSchedulerState(companyId, resourceKind, nowMs);
    if (!existing) {
      // A brand-new company/resource gets a real, persisted row immediately (rather than
      // re-deriving a fresh — and quickly stale — `initialSchedulerState` on every tick until it
      // first becomes due), so restart recovery has something concrete to resume from.
      this.repository.upsert(current);
    }
    if (Date.parse(current.nextCheckDueAt) > nowMs) {
      return;
    }

    let outcome: SchedulerSyncOutcome;
    try {
      const result = resourceKind === 'ledgers'
        ? await this.ledgerSync.syncLedgers(SCHEDULER_SYNC_OPTIONS)
        : await this.stockItemSync.syncStockItems(SCHEDULER_SYNC_OPTIONS);
      // Anything other than a clean 'completed' (cancelled/interrupted/failed) is treated as a
      // failure for scheduling purposes — the scheduler does not invent a third partial-success
      // category on top of the sync engine's own status vocabulary (architecture §9).
      outcome = result.status === 'completed'
        ? (hasDetectableChange(result.changes) ? 'change_detected' : 'no_change')
        : 'failed';
    } catch (error) {
      // Covers Tally unreachable, Connector-side extraction failure, and a benign SYNC_CONFLICT
      // from a manual sync racing this exact check (the existing per-company single-flight guard
      // already prevents the two from running concurrently) — all are "failed" for scheduling
      // purposes: never advance or reset the ladder, retry at the current interval.
      this.logger.info('scheduler_check_failed', {
        component: 'adaptive-scheduler',
        resourceKind,
        message: error instanceof Error ? error.message : String(error),
      });
      outcome = 'failed';
    }

    this.repository.upsert(applySyncOutcome(current, outcome, Date.now()));
  }
}
