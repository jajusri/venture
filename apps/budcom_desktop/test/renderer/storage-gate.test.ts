/**
 * @vitest-environment jsdom
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { renderStorageGate } from '../../src/renderer/scripts/storage-gate.js';

const STORAGE_GATE_MARKUP = `
  <div id="storage-gate-overlay" class="hidden">
    <div id="storage-gate-setup" class="hidden">
      <input type="radio" name="storage-gate-mode" id="storage-gate-mode-standard" value="standard" checked />
      <input type="radio" name="storage-gate-mode" id="storage-gate-mode-private" value="private-removable" />
      <div id="storage-gate-drive-picker" class="hidden">
        <div id="storage-gate-drive-list"></div>
        <button type="button" id="storage-gate-rescan"></button>
        <p id="storage-gate-no-drives" class="hidden"></p>
      </div>
      <p id="storage-gate-setup-error" class="hidden"></p>
      <button type="button" id="storage-gate-continue"></button>
    </div>
    <div id="storage-gate-unavailable" class="hidden">
      <p id="storage-gate-unavailable-detail"></p>
      <button type="button" id="storage-gate-retry"></button>
      <button type="button" id="storage-gate-locate"></button>
      <button type="button" id="storage-gate-exit"></button>
    </div>
    <div id="storage-gate-timeout" class="hidden">
      <p class="storage-gate-unavailable-message">BUDCOM is taking longer than expected to start.</p>
      <button type="button" id="storage-gate-timeout-retry"></button>
      <button type="button" id="storage-gate-timeout-exit"></button>
    </div>
  </div>
`;

/** Deferred promise so a test can hold `retryStorageConnection()` "in flight" across two clicks. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

describe('storage-gate retry button — concurrency guard', () => {
  it('ignores a second Retry click while the first retryStorageConnection() call is still in flight', async () => {
    document.body.innerHTML = STORAGE_GATE_MARKUP;
    const first = deferred<{ kind: 'unavailable'; reason: 'missing' }>();
    const retryStorageConnection = vi.fn(() => first.promise);
    window.budcomDesktop = {
      getStorageStatus: vi.fn(async () => ({ kind: 'unavailable' as const, reason: 'missing' as const })),
      retryStorageConnection,
    } as unknown as typeof window.budcomDesktop;

    // Not awaited: this promise only resolves once a retry actually reaches 'ready', which this
    // focused test never drives it to — only the concurrency guard around the click handler
    // itself is under test here.
    void renderStorageGate();
    // Let the initial getStorageStatus() microtask resolve so the unavailable screen renders.
    await Promise.resolve();
    await Promise.resolve();

    const retryButton = document.getElementById('storage-gate-retry') as HTMLButtonElement;
    retryButton.dispatchEvent(new Event('click', { bubbles: true }));
    expect(retryButton.disabled).toBe(true);
    // A second click arrives before the first call resolves — must not start a second call.
    retryButton.dispatchEvent(new Event('click', { bubbles: true }));
    expect(retryStorageConnection).toHaveBeenCalledTimes(1);

    first.resolve({ kind: 'unavailable', reason: 'missing' });
    await Promise.resolve();
    await Promise.resolve();
    expect(retryButton.disabled).toBe(false);
    expect(retryStorageConnection).toHaveBeenCalledTimes(1);

    // A click after the button re-enables is a genuinely new, separate retry attempt.
    retryButton.dispatchEvent(new Event('click', { bubbles: true }));
    expect(retryStorageConnection).toHaveBeenCalledTimes(2);
  });
});

describe('storage-gate resolving state — bounded re-poll (P1: false "storage not connected")', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('A: resolving -> standard never shows the unavailable or timeout screen', async () => {
    document.body.innerHTML = STORAGE_GATE_MARKUP;
    const getStorageStatus = vi
      .fn()
      .mockResolvedValueOnce({ kind: 'resolving' as const })
      .mockResolvedValueOnce({ kind: 'resolving' as const })
      .mockResolvedValueOnce({ kind: 'ready' as const, mode: 'standard' as const });
    window.budcomDesktop = {
      getStorageStatus,
      retryStorageConnection: vi.fn(),
    } as unknown as typeof window.budcomDesktop;

    const resultPromise = renderStorageGate();
    await vi.advanceTimersByTimeAsync(1000);
    const result = await resultPromise;

    expect(result).toBe(true);
    expect(document.getElementById('storage-gate-unavailable')?.classList.contains('hidden')).toBe(true);
    expect(document.getElementById('storage-gate-timeout')?.classList.contains('hidden')).toBe(true);
    expect(document.getElementById('storage-gate-overlay')?.classList.contains('hidden')).toBe(true);
  });

  it('B: resolving -> private-removable ready never shows the unavailable or timeout screen', async () => {
    document.body.innerHTML = STORAGE_GATE_MARKUP;
    const getStorageStatus = vi
      .fn()
      .mockResolvedValueOnce({ kind: 'resolving' as const })
      .mockResolvedValueOnce({
        kind: 'ready' as const,
        mode: 'private-removable' as const,
        driveLetter: 'E:\\',
        volumeLabel: 'BUDCOM-USB',
      });
    window.budcomDesktop = {
      getStorageStatus,
      retryStorageConnection: vi.fn(),
    } as unknown as typeof window.budcomDesktop;

    const resultPromise = renderStorageGate();
    await vi.advanceTimersByTimeAsync(1000);
    const result = await resultPromise;

    expect(result).toBe(true);
    expect(document.getElementById('storage-gate-unavailable')?.classList.contains('hidden')).toBe(true);
    expect(document.getElementById('storage-gate-timeout')?.classList.contains('hidden')).toBe(true);
    expect(document.getElementById('storage-gate-overlay')?.classList.contains('hidden')).toBe(true);
  });

  it('C: resolving -> genuine unavailable only shows the unavailable screen once the backend actually reports it', async () => {
    document.body.innerHTML = STORAGE_GATE_MARKUP;
    const getStorageStatus = vi
      .fn()
      .mockResolvedValueOnce({ kind: 'resolving' as const })
      .mockResolvedValueOnce({ kind: 'unavailable' as const, reason: 'missing' as const });
    window.budcomDesktop = {
      getStorageStatus,
      retryStorageConnection: vi.fn(),
    } as unknown as typeof window.budcomDesktop;

    void renderStorageGate();
    await vi.advanceTimersByTimeAsync(1000);
    await Promise.resolve();
    await Promise.resolve();

    expect(document.getElementById('storage-gate-unavailable')?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('storage-gate-timeout')?.classList.contains('hidden')).toBe(true);
    expect(document.getElementById('storage-gate-unavailable-detail')?.textContent).toContain(
      'Connect the removable storage device',
    );
  });

  it('D: resolving persisting past the bounded timeout shows an honest generic startup screen, never "storage missing"', async () => {
    document.body.innerHTML = STORAGE_GATE_MARKUP;
    const getStorageStatus = vi.fn(async () => ({ kind: 'resolving' as const }));
    window.budcomDesktop = {
      getStorageStatus,
      retryStorageConnection: vi.fn(),
    } as unknown as typeof window.budcomDesktop;

    void renderStorageGate();
    // Well past the 6s bound, but not unbounded.
    await vi.advanceTimersByTimeAsync(7000);
    await Promise.resolve();
    await Promise.resolve();

    expect(document.getElementById('storage-gate-timeout')?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('storage-gate-unavailable')?.classList.contains('hidden')).toBe(true);

    const message = document.querySelector('#storage-gate-timeout .storage-gate-unavailable-message')?.textContent ?? '';
    expect(message.toLowerCase()).not.toContain('not connected');
    expect(message.toLowerCase()).not.toContain('missing');

    // Bounded: ~6000ms / 200ms poll interval, not unbounded polling.
    expect(getStorageStatus.mock.calls.length).toBeGreaterThan(0);
    expect(getStorageStatus.mock.calls.length).toBeLessThan(50);
  });
});
