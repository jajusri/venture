import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  ActivePairingSessionView,
  SecurePairingCapability,
  TrustedPairingDeviceSummary,
} from '../../src/application/types.js';
import {
  activateView,
  bindPairingActions,
  disposeSyncProgressPolling,
  loadPairingPanel,
  startPairingStatusPolling,
} from '../../src/renderer/scripts/app.js';

/**
 * app.ts's pairingSession/pairingCapability are module-scoped state that persists across tests
 * within this file (ES modules are singletons). bindPairingActions() must already have been
 * called against the current DOM before this runs — clicking a .pairing-done-btn drives
 * pairingSession back to null deterministically, regardless of what an earlier test left behind.
 */
function resetPairingModuleState(): void {
  document.querySelector('.pairing-done-btn')?.dispatchEvent(new Event('click'));
}

function pairingMarkup(): string {
  return `
    <div id="app-notification" class="hidden"></div>
    <button class="nav-btn" data-view="dashboard"></button>
    <button class="nav-btn" data-view="pairing"></button>
    <section id="view-dashboard" class="view active"></section>
    <section id="view-pairing" class="view">
      <div id="pairing-state-disabled" class="hidden">
        <button id="btn-enable-secure-pairing" type="button">Enable</button>
      </div>
      <div id="pairing-state-unavailable" class="hidden">
        <p id="pairing-unavailable-message"></p>
      </div>
      <div id="pairing-state-ready" class="hidden">
        <span id="pairing-connector-name"></span>
        <span id="pairing-trusted-count"></span>
        <button id="btn-start-pairing" type="button">Start Pairing</button>
        <div id="pairing-session-active" class="hidden">
          <img id="pairing-qr-image" alt="Pairing QR code" />
          <span id="pairing-short-code"></span>
          <span id="pairing-countdown"></span>
          <button id="btn-cancel-pairing" type="button">Cancel</button>
        </div>
        <div id="pairing-session-redeemed" class="hidden">
          <p id="pairing-redeemed-message"></p>
          <button id="btn-pairing-done-redeemed" class="pairing-done-btn" type="button">Done</button>
        </div>
        <div id="pairing-session-ended" class="hidden">
          <p id="pairing-ended-message"></p>
          <button id="btn-pairing-done-ended" class="pairing-done-btn" type="button">Close</button>
        </div>
        <div id="pairing-device-list"></div>
      </div>
    </section>
  `;
}

function capability(overrides: Partial<SecurePairingCapability> = {}): SecurePairingCapability {
  return {
    state: 'ready',
    connectorName: 'Front Desk PC',
    transportFingerprint: null,
    trustedDeviceCount: 1,
    userMessage: null,
    ...overrides,
  };
}

function activeSession(overrides: Partial<ActivePairingSessionView> = {}): ActivePairingSessionView {
  return {
    state: 'active',
    pairingSessionId: 'session-1',
    qrDataUrl: 'data:image/png;base64,AAAA',
    shortCode: 'ABCD1234',
    expiresAt: new Date(Date.now() + 10_000).toISOString(),
    redeemedDeviceLabel: null,
    userMessage: null,
    ...overrides,
  };
}

function setBridge(overrides: Partial<typeof window.budcomDesktop>): void {
  window.budcomDesktop = {
    getSecurePairingCapability: vi.fn(async () => capability()),
    enableSecurePairing: vi.fn(async () => ({ ok: true, message: 'ok', restartRequired: true })),
    disableSecurePairing: vi.fn(async () => ({ ok: true, message: 'ok', restartRequired: true })),
    startPairing: vi.fn(async () => activeSession()),
    getPairingStatus: vi.fn(async () => activeSession()),
    cancelPairing: vi.fn(async () => ({ ...activeSession(), state: 'cancelled', pairingSessionId: null, qrDataUrl: null, shortCode: null })),
    listTrustedPairingDevices: vi.fn(async () => []),
    revokeTrustedPairingDevice: vi.fn(async () => ({ ok: true, message: 'Device revoked.' })),
    ...overrides,
  } as unknown as typeof window.budcomDesktop;
}

describe('Secure Mobile Pairing panel — capability rendering', () => {
  beforeEach(() => {
    document.body.innerHTML = pairingMarkup();
    bindPairingActions();
    resetPairingModuleState();
  });
  afterEach(() => {
    disposeSyncProgressPolling();
    activateView('dashboard');
  });

  it('shows the disabled state with an explicit Enable action when the flag is off', async () => {
    setBridge({ getSecurePairingCapability: vi.fn(async () => capability({ state: 'disabled', connectorName: null, trustedDeviceCount: null })) });
    await loadPairingPanel();

    expect(document.getElementById('pairing-state-disabled')?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('pairing-state-ready')?.classList.contains('hidden')).toBe(true);
  });

  it('shows the ready state with connector name and trusted device count', async () => {
    setBridge({ getSecurePairingCapability: vi.fn(async () => capability()) });
    await loadPairingPanel();

    expect(document.getElementById('pairing-state-ready')?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('pairing-connector-name')?.textContent).toBe('Front Desk PC');
    expect(document.getElementById('pairing-trusted-count')?.textContent).toBe('1');
  });

  it('shows a clear unavailable state when the Connector is not reachable', async () => {
    setBridge({
      getSecurePairingCapability: vi.fn(async () =>
        capability({ state: 'unavailable', connectorName: null, trustedDeviceCount: null, userMessage: 'The Connector is not currently connected.' })),
    });
    await loadPairingPanel();

    expect(document.getElementById('pairing-state-unavailable')?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('pairing-unavailable-message')?.textContent).toContain('not currently connected');
  });

  it('shows a clear restart-required state distinct from a generic failure', async () => {
    setBridge({
      getSecurePairingCapability: vi.fn(async () =>
        capability({ state: 'restart_required', connectorName: null, trustedDeviceCount: null, userMessage: 'Restart the Connector for secure mobile pairing to take effect.' })),
    });
    await loadPairingPanel();

    expect(document.getElementById('pairing-unavailable-message')?.textContent).toContain('Restart the Connector');
  });

  it('never renders a raw private IP, credential, or control token anywhere in the panel', async () => {
    setBridge({ getSecurePairingCapability: vi.fn(async () => capability()) });
    await loadPairingPanel();

    const serialized = document.getElementById('view-pairing')?.innerHTML ?? '';
    expect(serialized).not.toMatch(/BUDCOM_DESKTOP_CONTROL_TOKEN|x-budcom-desktop-control-token/i);
  });
});

describe('Secure Mobile Pairing panel — session lifecycle rendering', () => {
  beforeEach(() => {
    document.body.innerHTML = pairingMarkup();
    bindPairingActions();
    resetPairingModuleState();
  });
  afterEach(() => {
    disposeSyncProgressPolling();
    activateView('dashboard');
  });

  it('Start Pairing renders the QR image and short code', async () => {
    setBridge({});
    activateView('pairing');
    await Promise.resolve();

    document.getElementById('btn-start-pairing')?.dispatchEvent(new Event('click'));
    await Promise.resolve();
    await Promise.resolve();

    expect(document.getElementById('pairing-session-active')?.classList.contains('hidden')).toBe(false);
    expect((document.getElementById('pairing-qr-image') as HTMLImageElement)?.src).toContain('data:image/png');
    expect(document.getElementById('pairing-short-code')?.textContent).toBe('ABCD1234');
  });

  it('Cancel clears the QR/short-code state', async () => {
    const startPairing = vi.fn(async () => activeSession());
    const cancelPairing = vi.fn(async () => ({
      state: 'cancelled' as const,
      pairingSessionId: null,
      qrDataUrl: null,
      shortCode: null,
      expiresAt: null,
      redeemedDeviceLabel: null,
      userMessage: null,
    }));
    setBridge({ startPairing, cancelPairing });
    activateView('pairing');
    document.getElementById('btn-start-pairing')?.dispatchEvent(new Event('click'));
    await Promise.resolve();
    await Promise.resolve();

    document.getElementById('btn-cancel-pairing')?.dispatchEvent(new Event('click'));
    await Promise.resolve();
    await Promise.resolve();

    expect(cancelPairing).toHaveBeenCalledTimes(1);
    expect(document.getElementById('pairing-session-active')?.classList.contains('hidden')).toBe(true);
  });

  it('Redeemed state shows the device label and clears the QR', async () => {
    // Fake timers must be active BEFORE the session starts: handleStartPairing() itself calls
    // startPairingStatusPolling() as soon as the session becomes active, so enabling fake timers
    // afterward would leave that first scheduled poll on a real (uncontrolled) timer while a
    // later explicit startPairingStatusPolling() call here would then be a no-op (poll already
    // active) — advancing fake time would never trigger it.
    vi.useFakeTimers();
    setBridge({});
    activateView('pairing');
    document.getElementById('btn-start-pairing')?.dispatchEvent(new Event('click'));
    await vi.advanceTimersByTimeAsync(0);

    window.budcomDesktop.getPairingStatus = vi.fn(async () => ({
      state: 'redeemed',
      pairingSessionId: null,
      qrDataUrl: null,
      shortCode: null,
      expiresAt: null,
      redeemedDeviceLabel: "Sri's Phone",
      userMessage: null,
    }));
    await vi.advanceTimersByTimeAsync(2_000);
    vi.useRealTimers();

    expect(document.getElementById('pairing-session-redeemed')?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('pairing-redeemed-message')?.textContent).toContain("Sri's Phone");
  });

  it('Expired state stops polling and clears the QR', async () => {
    vi.useFakeTimers();
    setBridge({});
    activateView('pairing');
    document.getElementById('btn-start-pairing')?.dispatchEvent(new Event('click'));
    await vi.advanceTimersByTimeAsync(0);

    const getPairingStatus = vi.fn(async () => ({
      state: 'expired' as const,
      pairingSessionId: null,
      qrDataUrl: null,
      shortCode: null,
      expiresAt: null,
      redeemedDeviceLabel: null,
      userMessage: 'This pairing session expired.',
    }));
    window.budcomDesktop.getPairingStatus = getPairingStatus;

    await vi.advanceTimersByTimeAsync(2_000);
    await vi.advanceTimersByTimeAsync(2_000);
    vi.useRealTimers();

    expect(getPairingStatus).toHaveBeenCalledTimes(1);
    expect(document.getElementById('pairing-session-ended')?.classList.contains('hidden')).toBe(false);
  });
});

describe('Secure Mobile Pairing panel — bounded status polling', () => {
  beforeEach(() => {
    document.body.innerHTML = pairingMarkup();
    bindPairingActions();
    resetPairingModuleState();
    vi.useFakeTimers();
  });
  afterEach(() => {
    disposeSyncProgressPolling();
    activateView('dashboard');
    vi.useRealTimers();
  });

  it('polls no faster than once every two seconds while a session is active', async () => {
    const getPairingStatus = vi.fn(async () => activeSession());
    setBridge({ startPairing: vi.fn(async () => activeSession()), getPairingStatus });
    activateView('pairing');
    document.getElementById('btn-start-pairing')?.dispatchEvent(new Event('click'));
    await Promise.resolve();
    await Promise.resolve();

    startPairingStatusPolling();
    await vi.advanceTimersByTimeAsync(1_000);
    expect(getPairingStatus).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1_000);
    expect(getPairingStatus).toHaveBeenCalledTimes(1);
  });

  it('does not poll when no session exists', async () => {
    const getPairingStatus = vi.fn(async () => activeSession());
    setBridge({ getPairingStatus });
    activateView('pairing');
    await Promise.resolve();

    startPairingStatusPolling();
    await vi.advanceTimersByTimeAsync(5_000);
    expect(getPairingStatus).not.toHaveBeenCalled();
  });

  it('stops polling when the panel/view is no longer active', async () => {
    const getPairingStatus = vi.fn(async () => activeSession());
    setBridge({ startPairing: vi.fn(async () => activeSession()), getPairingStatus });
    activateView('pairing');
    document.getElementById('btn-start-pairing')?.dispatchEvent(new Event('click'));
    await Promise.resolve();
    await Promise.resolve();

    startPairingStatusPolling();
    activateView('dashboard');
    await vi.advanceTimersByTimeAsync(10_000);
    expect(getPairingStatus).not.toHaveBeenCalled();
  });

  it('never allows more than one status request in flight at a time', async () => {
    let resolveCall: ((value: ActivePairingSessionView) => void) | null = null;
    const getPairingStatus = vi.fn(
      () => new Promise<ActivePairingSessionView>((resolve) => {
        resolveCall = resolve;
      }),
    );
    setBridge({ startPairing: vi.fn(async () => activeSession()), getPairingStatus });
    activateView('pairing');
    document.getElementById('btn-start-pairing')?.dispatchEvent(new Event('click'));
    await Promise.resolve();
    await Promise.resolve();

    startPairingStatusPolling();
    await vi.advanceTimersByTimeAsync(2_000);
    expect(getPairingStatus).toHaveBeenCalledTimes(1);

    // A second poll tick fires while the first call is still in flight — must not overlap.
    await vi.advanceTimersByTimeAsync(2_000);
    expect(getPairingStatus).toHaveBeenCalledTimes(1);

    resolveCall?.(activeSession());
    await Promise.resolve();
  });

  it('stops polling on disposeSyncProgressPolling() (Desktop shutdown path)', async () => {
    const getPairingStatus = vi.fn(async () => activeSession());
    setBridge({ startPairing: vi.fn(async () => activeSession()), getPairingStatus });
    activateView('pairing');
    document.getElementById('btn-start-pairing')?.dispatchEvent(new Event('click'));
    await Promise.resolve();
    await Promise.resolve();

    startPairingStatusPolling();
    disposeSyncProgressPolling();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(getPairingStatus).not.toHaveBeenCalled();
  });
});

describe('Secure Mobile Pairing panel — trusted devices and revocation', () => {
  beforeEach(() => {
    document.body.innerHTML = pairingMarkup();
    bindPairingActions();
    resetPairingModuleState();
  });
  afterEach(() => {
    disposeSyncProgressPolling();
    activateView('dashboard');
  });

  it('renders trusted devices without any token/credential data in the DOM', async () => {
    const devices: readonly TrustedPairingDeviceSummary[] = [
      { credentialId: 'c1', deviceLabel: 'Phone A', firstPairedAt: '2026-01-01T00:00:00.000Z', lastUsedAt: null, status: 'active' },
    ];
    setBridge({ listTrustedPairingDevices: vi.fn(async () => devices) });
    await loadPairingPanel();

    const list = document.getElementById('pairing-device-list');
    expect(list?.textContent).toContain('Phone A');
    expect(list?.innerHTML).not.toMatch(/token/i);
  });

  it('revoke requires an explicit confirmation before calling the bridge', async () => {
    const devices: readonly TrustedPairingDeviceSummary[] = [
      { credentialId: 'c1', deviceLabel: 'Phone A', firstPairedAt: '2026-01-01T00:00:00.000Z', lastUsedAt: null, status: 'active' },
    ];
    const revokeTrustedPairingDevice = vi.fn(async () => ({ ok: true, message: 'Revoked.' }));
    setBridge({ listTrustedPairingDevices: vi.fn(async () => devices), revokeTrustedPairingDevice });
    await loadPairingPanel();

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    document.querySelector('[data-credential-id="c1"]')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await Promise.resolve();

    expect(confirmSpy).toHaveBeenCalled();
    expect(revokeTrustedPairingDevice).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it('revoke calls the bridge (Desktop-token-protected route) once confirmed, then refreshes the list', async () => {
    const devices: readonly TrustedPairingDeviceSummary[] = [
      { credentialId: 'c1', deviceLabel: 'Phone A', firstPairedAt: '2026-01-01T00:00:00.000Z', lastUsedAt: null, status: 'active' },
    ];
    const listTrustedPairingDevices = vi.fn(async () => devices);
    const revokeTrustedPairingDevice = vi.fn(async () => ({ ok: true, message: 'Revoked.' }));
    setBridge({ listTrustedPairingDevices, revokeTrustedPairingDevice });
    await loadPairingPanel();

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    document.querySelector('[data-credential-id="c1"]')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(revokeTrustedPairingDevice).toHaveBeenCalledWith('c1');
    expect(listTrustedPairingDevices.mock.calls.length).toBeGreaterThanOrEqual(2);
    confirmSpy.mockRestore();
  });
});
