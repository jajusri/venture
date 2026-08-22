import { describe, expect, it } from 'vitest';

import {
  ACTIVE_WINDOW_CHECK_INTERVAL_MS,
  ACTIVE_WINDOW_DURATION_MS,
  applySyncOutcome,
  hasDetectableChange,
  initialSchedulerState,
  type SchedulerState,
} from '../../../src/services/scheduler/adaptive-scheduler-domain.js';

const T0 = Date.parse('2026-01-01T00:00:00.000Z');
const FIVE_MIN = 5 * 60_000;
const FIFTEEN_MIN = 15 * 60_000;
const THIRTY_MIN = 30 * 60_000;
const SIXTY_MIN = 60 * 60_000;

describe('initialSchedulerState', () => {
  it('starts in active_window, erring toward freshness', () => {
    const state = initialSchedulerState('estimation', 'ledgers', T0);
    expect(state.stage).toBe('active_window');
    expect(state.companyId).toBe('estimation');
    expect(state.resourceKind).toBe('ledgers');
    expect(Date.parse(state.nextCheckDueAt)).toBe(T0 + ACTIVE_WINDOW_CHECK_INTERVAL_MS);
    expect(Date.parse(state.activeWindowExpiresAt!)).toBe(T0 + ACTIVE_WINDOW_DURATION_MS);
  });
});

describe('applySyncOutcome — active window behaviour', () => {
  it('a change inside the active window resets the 15-minute window clock and keeps the 5-minute cadence', () => {
    const state = initialSchedulerState('estimation', 'ledgers', T0);
    const afterChange = applySyncOutcome(state, 'change_detected', T0 + FIVE_MIN);
    expect(afterChange.stage).toBe('active_window');
    expect(Date.parse(afterChange.activeWindowExpiresAt!)).toBe(T0 + FIVE_MIN + ACTIVE_WINDOW_DURATION_MS);
    expect(Date.parse(afterChange.nextCheckDueAt)).toBe(T0 + FIVE_MIN + ACTIVE_WINDOW_CHECK_INTERVAL_MS);
  });

  it('no change, still inside the window, keeps checking every 5 minutes without stepping down', () => {
    const state = initialSchedulerState('estimation', 'ledgers', T0);
    // Window expires at T0+15min; check at T0+5min finds no change -- still 10 minutes left.
    const afterNoChange = applySyncOutcome(state, 'no_change', T0 + FIVE_MIN);
    expect(afterNoChange.stage).toBe('active_window');
    expect(Date.parse(afterNoChange.nextCheckDueAt)).toBe(T0 + FIVE_MIN + ACTIVE_WINDOW_CHECK_INTERVAL_MS);
    // Window expiry itself is untouched by a no-change result.
    expect(afterNoChange.activeWindowExpiresAt).toBe(state.activeWindowExpiresAt);
  });

  it('no change exactly at window expiry steps down to backoff_15', () => {
    const state = initialSchedulerState('estimation', 'ledgers', T0);
    const expiresAtMs = Date.parse(state.activeWindowExpiresAt!);
    const stepped = applySyncOutcome(state, 'no_change', expiresAtMs);
    expect(stepped.stage).toBe('backoff_15');
    expect(stepped.activeWindowExpiresAt).toBeNull();
    expect(Date.parse(stepped.nextCheckDueAt)).toBe(expiresAtMs + FIFTEEN_MIN);
  });
});

describe('applySyncOutcome — staged backoff 15 -> 30 -> 60', () => {
  function backoffState(stage: SchedulerState['stage'], nowMs: number): SchedulerState {
    return {
      companyId: 'estimation',
      resourceKind: 'ledgers',
      stage,
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(nowMs).toISOString(),
      updatedAt: new Date(nowMs).toISOString(),
    };
  }

  it('backoff_15 with no change steps to backoff_30', () => {
    const state = backoffState('backoff_15', T0);
    const next = applySyncOutcome(state, 'no_change', T0);
    expect(next.stage).toBe('backoff_30');
    expect(Date.parse(next.nextCheckDueAt)).toBe(T0 + THIRTY_MIN);
  });

  it('backoff_30 with no change steps to backoff_60', () => {
    const state = backoffState('backoff_30', T0);
    const next = applySyncOutcome(state, 'no_change', T0);
    expect(next.stage).toBe('backoff_60');
    expect(Date.parse(next.nextCheckDueAt)).toBe(T0 + SIXTY_MIN);
  });

  it('backoff_60 with no change remains at the 60-minute floor indefinitely', () => {
    const state = backoffState('backoff_60', T0);
    const next = applySyncOutcome(state, 'no_change', T0);
    expect(next.stage).toBe('backoff_60');
    expect(Date.parse(next.nextCheckDueAt)).toBe(T0 + SIXTY_MIN);

    const stillFloor = applySyncOutcome(next, 'no_change', T0 + SIXTY_MIN);
    expect(stillFloor.stage).toBe('backoff_60');
    expect(Date.parse(stillFloor.nextCheckDueAt)).toBe(T0 + SIXTY_MIN + SIXTY_MIN);
  });

  it('a change detected at any backoff stage jumps straight back to active_window at the 5-minute cadence, never through the ladder', () => {
    for (const stage of ['backoff_15', 'backoff_30', 'backoff_60'] as const) {
      const state = backoffState(stage, T0);
      const next = applySyncOutcome(state, 'change_detected', T0);
      expect(next.stage).toBe('active_window');
      expect(Date.parse(next.nextCheckDueAt)).toBe(T0 + ACTIVE_WINDOW_CHECK_INTERVAL_MS);
      expect(Date.parse(next.activeWindowExpiresAt!)).toBe(T0 + ACTIVE_WINDOW_DURATION_MS);
    }
  });
});

describe('applySyncOutcome — failure semantics (LOCKED, critical)', () => {
  it('a failed check while in active_window never advances or resets the ladder, and retries at the same 5-minute interval', () => {
    const state = initialSchedulerState('estimation', 'ledgers', T0);
    const afterFailure = applySyncOutcome(state, 'failed', T0 + FIVE_MIN);
    expect(afterFailure.stage).toBe('active_window');
    expect(afterFailure.activeWindowExpiresAt).toBe(state.activeWindowExpiresAt);
    expect(Date.parse(afterFailure.nextCheckDueAt)).toBe(T0 + FIVE_MIN + ACTIVE_WINDOW_CHECK_INTERVAL_MS);
  });

  it('a failed check while in backoff_30 stays at backoff_30 and retries after another 30 minutes -- never advances to backoff_60, never resets to backoff_15', () => {
    const state: SchedulerState = {
      companyId: 'estimation',
      resourceKind: 'stock-items',
      stage: 'backoff_30',
      activeWindowExpiresAt: null,
      nextCheckDueAt: new Date(T0).toISOString(),
      updatedAt: new Date(T0).toISOString(),
    };
    const afterFailure = applySyncOutcome(state, 'failed', T0);
    expect(afterFailure.stage).toBe('backoff_30');
    expect(Date.parse(afterFailure.nextCheckDueAt)).toBe(T0 + THIRTY_MIN);
  });

  it('repeated failures never accumulate a change in stage across multiple retries', () => {
    let state = initialSchedulerState('estimation', 'ledgers', T0);
    let now = T0;
    for (let i = 0; i < 5; i += 1) {
      state = applySyncOutcome(state, 'failed', now);
      expect(state.stage).toBe('active_window');
      now = Date.parse(state.nextCheckDueAt);
    }
  });
});

describe('hasDetectableChange', () => {
  it('is true when at least one record was added or updated', () => {
    expect(hasDetectableChange([{ changeType: 'skipped' }, { changeType: 'updated' }])).toBe(true);
    expect(hasDetectableChange([{ changeType: 'added' }])).toBe(true);
    expect(hasDetectableChange([{ changeType: 'deleted' }])).toBe(true);
  });

  it('is false when every record was skipped (fingerprint unchanged) or the collection is empty', () => {
    expect(hasDetectableChange([{ changeType: 'skipped' }, { changeType: 'skipped' }])).toBe(false);
    expect(hasDetectableChange([])).toBe(false);
  });
});
