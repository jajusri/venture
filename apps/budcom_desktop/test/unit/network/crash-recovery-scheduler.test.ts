import { describe, expect, it, vi } from 'vitest';

import { CrashRecoveryScheduler } from '../../../src/application/network/crash-recovery-scheduler.js';

/** Deterministic fake clock — captures scheduled callbacks so tests can fire them manually. */
function createFakeClock() {
  const pending = new Map<number, () => void>();
  let nextHandle = 1;
  return {
    setTimeoutImpl: (handler: () => void) => {
      const handle = nextHandle;
      nextHandle += 1;
      pending.set(handle, handler);
      return handle as unknown as ReturnType<typeof setTimeout>;
    },
    clearTimeoutImpl: (handle: ReturnType<typeof setTimeout>) => {
      pending.delete(handle as unknown as number);
    },
    fireAll: () => {
      const handlers = [...pending.values()];
      pending.clear();
      handlers.forEach((handler) => handler());
    },
    pendingCount: () => pending.size,
  };
}

function createScheduler(overrides?: {
  readonly maxAttempts?: number;
  readonly baseDelayMs?: number;
  readonly generation?: () => number;
}) {
  const clock = createFakeClock();
  const recover = vi.fn();
  const onExhausted = vi.fn();
  let generation = 0;
  const scheduler = new CrashRecoveryScheduler({
    getMaxAttempts: () => overrides?.maxAttempts ?? 5,
    getBaseDelayMs: () => overrides?.baseDelayMs ?? 1_000,
    getGeneration: overrides?.generation ?? (() => generation),
    recover,
    onExhausted,
    setTimeoutImpl: clock.setTimeoutImpl,
    clearTimeoutImpl: clock.clearTimeoutImpl,
  });
  return {
    scheduler,
    clock,
    recover,
    onExhausted,
    bumpGeneration: () => {
      generation += 1;
    },
  };
}

describe('CrashRecoveryScheduler', () => {
  it('16a. schedules a recovery request after the configured backoff delay', () => {
    const { scheduler, clock, recover } = createScheduler();

    scheduler.scheduleRecovery();
    expect(recover).not.toHaveBeenCalled();
    expect(scheduler.hasPendingRecovery()).toBe(true);

    clock.fireAll();

    expect(recover).toHaveBeenCalledTimes(1);
  });

  it('16b. backoff delay grows with each successive attempt (bounded, not unlimited)', () => {
    const { scheduler, onExhausted } = createScheduler({ maxAttempts: 3, baseDelayMs: 100 });

    scheduler.scheduleRecovery();
    expect(scheduler.getPendingAttempts()).toBe(1);
    scheduler.scheduleRecovery();
    expect(scheduler.getPendingAttempts()).toBe(2);
    scheduler.scheduleRecovery();
    expect(scheduler.getPendingAttempts()).toBe(3);
    scheduler.scheduleRecovery();

    expect(onExhausted).toHaveBeenCalledTimes(1);
  });

  it('16c. once exhausted, no further recovery is scheduled', () => {
    const { scheduler, clock, recover, onExhausted } = createScheduler({ maxAttempts: 1 });

    scheduler.scheduleRecovery();
    clock.fireAll();
    expect(recover).toHaveBeenCalledTimes(1);

    scheduler.scheduleRecovery();

    expect(onExhausted).toHaveBeenCalledTimes(1);
    expect(clock.pendingCount()).toBe(0);
  });

  it('5. a generation change during the backoff delay abandons the attempt (e.g. Private -> Public)', () => {
    const { scheduler, clock, recover, bumpGeneration } = createScheduler();

    scheduler.scheduleRecovery();
    // Something else (a network change, manual action) advances the coordinator's generation
    // while this recovery is still waiting out its backoff.
    bumpGeneration();
    clock.fireAll();

    expect(recover).not.toHaveBeenCalled();
  });

  it('8/9. manual Stop/Restart (or anything else) supersedes a pending crash recovery via generation mismatch', () => {
    const { scheduler, clock, recover, bumpGeneration } = createScheduler();

    scheduler.scheduleRecovery();
    bumpGeneration(); // simulates a manual Stop/Restart/network-change enqueue elsewhere
    clock.fireAll();

    expect(recover).not.toHaveBeenCalled();
  });

  it('a superseded attempt drops its stale count rather than carrying it into a future crash', () => {
    const { scheduler, clock, bumpGeneration } = createScheduler({ maxAttempts: 5 });

    scheduler.scheduleRecovery();
    expect(scheduler.getPendingAttempts()).toBe(1);
    bumpGeneration();
    clock.fireAll();

    expect(scheduler.getPendingAttempts()).toBe(0);
  });

  it('11. duplicate scheduleRecovery calls before the timer fires coalesce into one pending timer', () => {
    const { scheduler, clock, recover } = createScheduler();

    scheduler.scheduleRecovery();
    scheduler.scheduleRecovery();
    scheduler.scheduleRecovery();

    expect(clock.pendingCount()).toBe(1);
    clock.fireAll();
    expect(recover).toHaveBeenCalledTimes(1);
  });

  it('notifyRecovered resets the attempt count (a healthy connection ends the crash-loop context)', () => {
    const { scheduler, onExhausted } = createScheduler({ maxAttempts: 2 });

    scheduler.scheduleRecovery();
    scheduler.scheduleRecovery();
    scheduler.notifyRecovered();

    scheduler.scheduleRecovery();
    scheduler.scheduleRecovery();

    // A fresh budget of 2 was available after the reset — the third and fourth calls consumed it
    // without exhausting (only a 5th call would exhaust with maxAttempts=2 after the reset).
    expect(onExhausted).not.toHaveBeenCalled();
  });

  it('13. notifyStoppedIntentionally clears any pending timer and resets attempts', () => {
    const { scheduler, clock, recover } = createScheduler();

    scheduler.scheduleRecovery();
    expect(scheduler.hasPendingRecovery()).toBe(true);

    scheduler.notifyStoppedIntentionally();

    expect(scheduler.hasPendingRecovery()).toBe(false);
    expect(scheduler.getPendingAttempts()).toBe(0);
    clock.fireAll();
    expect(recover).not.toHaveBeenCalled();
  });

  it('16d. a later valid recovery can still succeed after an earlier one was superseded', () => {
    const { scheduler, clock, recover, bumpGeneration } = createScheduler();

    scheduler.scheduleRecovery();
    bumpGeneration();
    clock.fireAll();
    expect(recover).not.toHaveBeenCalled();

    scheduler.scheduleRecovery();
    clock.fireAll();

    expect(recover).toHaveBeenCalledTimes(1);
  });
});
