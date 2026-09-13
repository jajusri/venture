import type { SyncResourceKind } from '../../erp/ledger/ledger-domain.js';

/**
 * Adaptive Tally Synchronization state machine
 * (`docs/architecture/VENTURE-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md` §5-§9).
 *
 * Two states a user or the freshness UI ever needs to reason about — `ACTIVE_WINDOW` and
 * `BACKOFF` — with `BACKOFF`'s three stages (15/30/60 min) an internal implementation detail,
 * never surfaced as separate top-level states.
 */
export type AdaptiveStage = 'active_window' | 'backoff_15' | 'backoff_30' | 'backoff_60';

export interface SchedulerState {
  readonly companyId: string;
  readonly resourceKind: SyncResourceKind;
  readonly stage: AdaptiveStage;
  /** Only meaningful while `stage === 'active_window'`; null otherwise. */
  readonly activeWindowExpiresAt: string | null;
  readonly nextCheckDueAt: string;
  readonly updatedAt: string;
}

/** A real sync every 5 minutes while inside the active window (§6). */
export const ACTIVE_WINDOW_CHECK_INTERVAL_MS = 5 * 60_000;
/** The active window lasts 15 minutes past the last detected change (§6). */
export const ACTIVE_WINDOW_DURATION_MS = 15 * 60_000;

const BACKOFF_INTERVALS_MS: Record<'backoff_15' | 'backoff_30' | 'backoff_60', number> = {
  backoff_15: 15 * 60_000,
  backoff_30: 30 * 60_000,
  backoff_60: 60 * 60_000,
};

/**
 * A missing or corrupt row must fail closed toward MORE freshness, not less (§15) — the default
 * for a company/resource never seen before, or recovered from an unreadable row, is always
 * `active_window`, never a backoff stage.
 */
export function initialSchedulerState(
  companyId: string,
  resourceKind: SyncResourceKind,
  nowMs: number,
): SchedulerState {
  const now = new Date(nowMs).toISOString();
  return {
    companyId,
    resourceKind,
    stage: 'active_window',
    activeWindowExpiresAt: new Date(nowMs + ACTIVE_WINDOW_DURATION_MS).toISOString(),
    nextCheckDueAt: new Date(nowMs + ACTIVE_WINDOW_CHECK_INTERVAL_MS).toISOString(),
    updatedAt: now,
  };
}

/**
 * The abstract input this state machine reacts to — deliberately not coupled to *how* a change
 * was detected (§4's "swap what triggers 'change detected' later without a redesign" property).
 * `'failed'` covers every non-success outcome the sync engine can report (Tally unreachable,
 * Connector-side extraction failure, cancellation, interruption) — see §9's exhaustive table.
 */
export type SchedulerSyncOutcome = 'change_detected' | 'no_change' | 'failed';

/**
 * The one rule the governing task marks critical (§9, LOCKED item 2): a failed check may never
 * advance or reset the staged-backoff ladder in either direction. It retries at exactly the
 * interval associated with the *current* stage — not faster, not slower, and never treated as
 * evidence "no change occurred."
 */
export function applySyncOutcome(
  current: SchedulerState,
  outcome: SchedulerSyncOutcome,
  nowMs: number,
): SchedulerState {
  const now = new Date(nowMs).toISOString();

  if (outcome === 'failed') {
    return {
      ...current,
      nextCheckDueAt: new Date(nowMs + intervalForStage(current.stage)).toISOString(),
      updatedAt: now,
    };
  }

  if (outcome === 'change_detected') {
    // Any change, at any stage, jumps straight back to ACTIVE_WINDOW at the 5-minute cadence —
    // never back through the staged ladder (§6).
    return {
      companyId: current.companyId,
      resourceKind: current.resourceKind,
      stage: 'active_window',
      activeWindowExpiresAt: new Date(nowMs + ACTIVE_WINDOW_DURATION_MS).toISOString(),
      nextCheckDueAt: new Date(nowMs + ACTIVE_WINDOW_CHECK_INTERVAL_MS).toISOString(),
      updatedAt: now,
    };
  }

  // no_change
  if (current.stage === 'active_window') {
    const expiresAtMs = current.activeWindowExpiresAt ? Date.parse(current.activeWindowExpiresAt) : nowMs;
    if (nowMs < expiresAtMs) {
      // Still inside the window — keep checking every 5 minutes until it either resets (a change)
      // or genuinely expires.
      return {
        ...current,
        nextCheckDueAt: new Date(nowMs + ACTIVE_WINDOW_CHECK_INTERVAL_MS).toISOString(),
        updatedAt: now,
      };
    }
    return {
      ...current,
      stage: 'backoff_15',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(nowMs + BACKOFF_INTERVALS_MS.backoff_15).toISOString(),
      updatedAt: now,
    };
  }

  if (current.stage === 'backoff_15') {
    return {
      ...current,
      stage: 'backoff_30',
      nextCheckDueAt: new Date(nowMs + BACKOFF_INTERVALS_MS.backoff_30).toISOString(),
      updatedAt: now,
    };
  }

  // backoff_30 or backoff_60 both step to (or remain at) backoff_60 — the floor (§6).
  return {
    ...current,
    stage: 'backoff_60',
    nextCheckDueAt: new Date(nowMs + BACKOFF_INTERVALS_MS.backoff_60).toISOString(),
    updatedAt: now,
  };
}

function intervalForStage(stage: AdaptiveStage): number {
  if (stage === 'active_window') return ACTIVE_WINDOW_CHECK_INTERVAL_MS;
  return BACKOFF_INTERVALS_MS[stage];
}

/**
 * Definition of "change" per module (§7): a content-fingerprint diff, or a new/absent record —
 * exactly what the existing sync engine's `changes` array already reports, reusing the existing
 * per-module fingerprint mechanism rather than a new raw-response comparison. Only meaningful
 * when the sync ran with `incremental: true` (see [SCHEDULER_SYNC_OPTIONS]) — a non-incremental
 * sync marks every existing record `'updated'` unconditionally, which is why manual "Sync Now"
 * (always non-incremental) is instead treated as an unconditional `'change_detected'`: a manual
 * trigger is itself strong, honest evidence of active use, not a fingerprint claim.
 */
export function hasDetectableChange(changes: readonly { readonly changeType: string }[]): boolean {
  return changes.some((change) => change.changeType === 'added' || change.changeType === 'updated' || change.changeType === 'deleted');
}

/** The scheduler's own automatic checks always run incremental, so `hasDetectableChange` reflects a real fingerprint diff. */
export const SCHEDULER_SYNC_OPTIONS = { incremental: true } as const;
