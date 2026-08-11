/**
 * @vitest-environment jsdom
 */
import { describe, expect, it, vi } from 'vitest';

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
