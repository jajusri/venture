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
      <p class="storage-gate-unavailable-message">VENTURE is taking longer than expected to start.</p>
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
    window.ventureDesktop = {
      getStorageStatus: vi.fn(async () => ({ kind: 'unavailable' as const, reason: 'missing' as const })),
      retryStorageConnection,
    } as unknown as typeof window.ventureDesktop;

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

// TD-033 regression: choose-storage-mode must never silently switch an already-configured
// installation to a different storage mode — the setup picker reopens for an existing install via
// the "storage not connected" screen's Locate button, not only at genuine first-run.
describe('storage-gate setup flow — TD-033 switch confirmation', () => {
  it('requires a second Continue click before an existing installation is switched, and passes confirmSwitch on the retry', async () => {
    document.body.innerHTML = STORAGE_GATE_MARKUP;
    const chooseStorageMode = vi
      .fn()
      .mockResolvedValueOnce({
        ok: false,
        requiresConfirmation: true,
        message: 'This will stop using your current private removable storage.',
      })
      .mockResolvedValueOnce({ ok: true, state: { kind: 'ready', mode: 'standard' } });
    window.ventureDesktop = {
      getStorageStatus: vi.fn(async () => ({ kind: 'first-run' as const })),
      chooseStorageMode,
    } as unknown as typeof window.ventureDesktop;

    const resultPromise = renderStorageGate();
    await Promise.resolve();
    await Promise.resolve();

    const continueButton = document.getElementById('storage-gate-continue') as HTMLButtonElement;
    const errorEl = document.getElementById('storage-gate-setup-error') as HTMLElement;

    // Standard radio is checked by default in STORAGE_GATE_MARKUP.
    continueButton.dispatchEvent(new Event('click', { bubbles: true }));
    await Promise.resolve();
    await Promise.resolve();

    expect(chooseStorageMode).toHaveBeenCalledTimes(1);
    expect(chooseStorageMode).toHaveBeenNthCalledWith(1, { mode: 'standard', confirmSwitch: false });
    expect(errorEl.classList.contains('hidden')).toBe(false);
    expect(errorEl.textContent).toContain('current private removable storage');
    expect(errorEl.textContent).toContain('Click Continue again to confirm');
    expect(document.getElementById('storage-gate-overlay')?.classList.contains('hidden')).toBe(false);

    continueButton.dispatchEvent(new Event('click', { bubbles: true }));
    await Promise.resolve();
    await Promise.resolve();

    expect(chooseStorageMode).toHaveBeenCalledTimes(2);
    expect(chooseStorageMode).toHaveBeenNthCalledWith(2, { mode: 'standard', confirmSwitch: true });
    await resultPromise;
    expect(document.getElementById('storage-gate-overlay')?.classList.contains('hidden')).toBe(true);
  });

  it('changing the selection between clicks starts over unconfirmed rather than confirming the new choice', async () => {
    document.body.innerHTML = STORAGE_GATE_MARKUP;
    const chooseStorageMode = vi.fn().mockResolvedValue({
      ok: false,
      requiresConfirmation: true,
      message: 'This will switch storage modes.',
    });
    window.ventureDesktop = {
      getStorageStatus: vi.fn(async () => ({ kind: 'first-run' as const })),
      listRemovableVolumes: vi.fn(async () => [
        { driveLetter: 'E:\\', label: 'VENTURE-USB', fileSystem: 'NTFS', sizeBytes: 1000, freeBytes: 500 },
      ]),
      chooseStorageMode,
    } as unknown as typeof window.ventureDesktop;

    void renderStorageGate();
    await Promise.resolve();
    await Promise.resolve();

    const continueButton = document.getElementById('storage-gate-continue') as HTMLButtonElement;
    const standardRadio = document.getElementById('storage-gate-mode-standard') as HTMLInputElement;
    const privateRadio = document.getElementById('storage-gate-mode-private') as HTMLInputElement;

    continueButton.dispatchEvent(new Event('click', { bubbles: true }));
    await Promise.resolve();
    await Promise.resolve();
    expect(chooseStorageMode).toHaveBeenNthCalledWith(1, { mode: 'standard', confirmSwitch: false });

    // User switches the radio selection instead of confirming the original warned-about choice.
    standardRadio.checked = false;
    privateRadio.checked = true;
    privateRadio.dispatchEvent(new Event('change', { bubbles: true }));
    // Lets the async refreshDriveList() populate the (real, non-mocked) drive-letter radio list.
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    continueButton.dispatchEvent(new Event('click', { bubbles: true }));
    await Promise.resolve();
    await Promise.resolve();

    // Still unconfirmed: the newly-selected choice was never itself warned about and confirmed.
    expect(chooseStorageMode).toHaveBeenLastCalledWith(
      expect.objectContaining({ mode: 'private-removable', confirmSwitch: false }),
    );
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
    window.ventureDesktop = {
      getStorageStatus,
      retryStorageConnection: vi.fn(),
    } as unknown as typeof window.ventureDesktop;

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
        volumeLabel: 'VENTURE-USB',
      });
    window.ventureDesktop = {
      getStorageStatus,
      retryStorageConnection: vi.fn(),
    } as unknown as typeof window.ventureDesktop;

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
    window.ventureDesktop = {
      getStorageStatus,
      retryStorageConnection: vi.fn(),
    } as unknown as typeof window.ventureDesktop;

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
    window.ventureDesktop = {
      getStorageStatus,
      retryStorageConnection: vi.fn(),
    } as unknown as typeof window.ventureDesktop;

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
