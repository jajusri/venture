/**
 * @vitest-environment jsdom
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  loadCompanies,
  reconcileBoundedRecovery,
  startDesktopShell,
  stopBoundedRecovery,
} from '../../src/renderer/scripts/app.js';
import type { CompanyListItemDto, DashboardState } from '../../src/application/types.js';
import { lifecycleStatusFixture, settingsFixture } from '../helpers/lifecycle-fixtures.js';

const DASHBOARD_MARKUP = `
  <span id="footer-connection-indicator" class="status-dot status-unknown"></span>
  <strong id="footer-connection-status"></strong>
  <span id="footer-company"></span>
  <div id="connection-indicator"></div>
  <div id="app-title"></div>
  <div id="header-version"></div>
  <div id="header-connection-label"></div>
  <div id="header-company"></div>
  <div id="header-sync"></div>
  <div id="header-last-sync"></div>
  <div id="dashboard-connection"></div>
  <div id="dashboard-health"></div>
  <div id="dashboard-company-name"></div>
  <div id="dashboard-company-id"></div>
  <div id="dashboard-selection-time"></div>
  <div id="dashboard-session-status"></div>
  <div id="dashboard-erp-name"></div>
  <div id="dashboard-last-refresh"></div>
  <div id="dashboard-sync"></div>
  <div id="dashboard-last-sync"></div>
  <div id="dashboard-version"></div>
  <div id="dashboard-desktop-version"></div>
  <div id="connection-detail-indicator"></div>
  <div id="connection-detail-label"></div>
  <div id="connection-detail-reachable"></div>
  <div id="connection-detail-health"></div>
  <div id="connection-detail-session"></div>
  <div id="footer-license"></div>
  <div id="company-list" role="radiogroup"></div>
  <div id="company-list-status"></div>
  <button id="btn-refresh-companies"></button>
  <button id="btn-clear-company"></button>
  <div id="app-notification" class="app-notification hidden"></div>
  <section id="view-dashboard" class="view active"></section>
  <section id="view-logs" class="view"></section>
  <button class="nav-btn active" data-view="dashboard"></button>
  <button class="nav-btn" data-view="logs"></button>
  <div id="global-loading" class="loading-bar hidden"><span id="loading-message"></span></div>
`;

function dashboardState(overrides: Partial<DashboardState> = {}): DashboardState {
  return {
    windowTitle: 'Business OS Tally Connector',
    connectorVersion: '0.4.0',
    apiVersion: '1.0.0',
    desktopVersion: '0.4.3',
    erpType: 'tally',
    erpName: 'Tally',
    connectionIndicator: 'connected',
    connectionLabel: 'Connected',
    companyName: 'ESTIMATION',
    companyId: 'estimation',
    selectionTime: '2026-08-08T17:00:00.000Z',
    sessionStatus: 'ACTIVE',
    syncStatus: 'idle',
    syncLabel: 'Idle',
    lastSync: '2026-08-08T17:05:00.000Z',
    lastRefresh: '2026-08-08T17:06:00.000Z',
    licenseStatus: 'Evaluation',
    connectorReachable: true,
    healthStatus: 'ok',
    userMessage: null,
    ...overrides,
  };
}

/**
 * Models a real DashboardService.getDashboardState() "transient failure" outcome: that method
 * internally uses Promise.allSettled and never rejects, so a timeout surfaces as a resolved,
 * connectorReachable:false snapshot — never a rejected IPC call.
 */
function unhealthyState(overrides: Partial<DashboardState> = {}): DashboardState {
  return dashboardState({
    connectionIndicator: 'disconnected',
    connectionLabel: 'Not connected',
    connectorReachable: false,
    healthStatus: 'unknown',
    sessionStatus: 'DISCONNECTED',
    companyName: '—',
    companyId: '—',
    userMessage: 'The connector did not respond in time.',
    ...overrides,
  });
}

const ESTIMATION: CompanyListItemDto = { id: 'estimation', name: 'ESTIMATION' };
const SUCCESS_COMPANIES = { items: [ESTIMATION], status: 'SUCCESS' };

/**
 * A "current value" bridge stub for getDashboardState — deliberately NOT a call-position queue.
 * Every cycle (startup, a lifecycle push, a bounded retry) calls bridge.getDashboardState() TWICE
 * — once from refreshUi(), once again from inside loadCompanies() (for company-ID
 * cross-referencing) — so a call-position queue silently misaligns after the first cycle. A
 * mutable "whatever is current right now" value sidesteps that entirely: tests flip it with
 * .set(...) at the exact narrative moment they want the *next* call (whichever function makes it)
 * to observe the new state.
 */
function mutableDashboardState(initial: DashboardState) {
  let current = initial;
  const fn = vi.fn(async () => current);
  return { fn, set: (next: DashboardState) => { current = next; } };
}

/** Base bridge with every method startDesktopShell()/its bindings might touch, all inert by default. */
function baseBridge() {
  let statusListener: (() => void) | null = null;
  return {
    getLogs: vi.fn(async () => []),
    getSettings: vi.fn(async () => settingsFixture),
    getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    getMobileAccessStatus: vi.fn(),
    validateSettings: vi.fn(),
    saveSettings: vi.fn(),
    restoreDefaultSettings: vi.fn(),
    startConnector: vi.fn(),
    stopConnector: vi.fn(),
    restartConnector: vi.fn(),
    selectCompany: vi.fn(),
    clearCompany: vi.fn(),
    getDiagnostics: vi.fn(),
    refreshDiagnostics: vi.fn(),
    copyDiagnosticsSummary: vi.fn(),
    exportDiagnosticsBundle: vi.fn(),
    openLogsFolder: vi.fn(),
    clearNonessentialLogs: vi.fn(),
    runHealthCheck: vi.fn(),
    reloadRenderer: vi.fn(),
    getLedgers: vi.fn(),
    syncLedgers: vi.fn(),
    cancelLedgerSync: vi.fn(),
    clearLedgerCache: vi.fn(),
    getStockItems: vi.fn(),
    syncStockItems: vi.fn(),
    cancelStockItemSync: vi.fn(),
    clearStockItemCache: vi.fn(),
    getSecurePairingCapability: vi.fn(),
    enableSecurePairing: vi.fn(),
    disableSecurePairing: vi.fn(),
    startPairing: vi.fn(),
    getPairingStatus: vi.fn(),
    cancelPairing: vi.fn(),
    listTrustedPairingDevices: vi.fn(),
    revokeTrustedPairingDevice: vi.fn(),
    getStorageStatus: vi.fn(async () => ({ kind: 'ready' as const, mode: 'standard' as const })),
    listRemovableVolumes: vi.fn(async () => []),
    chooseStorageMode: vi.fn(),
    retryStorageConnection: vi.fn(),
    onStatusUpdated: vi.fn((listener: () => void) => {
      statusListener = listener;
      return () => {
        statusListener = null;
      };
    }),
    // exposed for tests that need to simulate a desktop:status-updated push
    __fireStatusUpdated: () => statusListener?.(),
  };
}

const ok = <T>(value: T) => () => Promise.resolve(value);
const fail = (message: string) => () => Promise.reject(new Error(message));

describe('TD-014 bounded dashboard/company auto-recovery', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    document.body.innerHTML = DASHBOARD_MARKUP;
  });

  afterEach(() => {
    stopBoundedRecovery();
    vi.useRealTimers();
  });

  // 1. healthy startup requires no retry
  it('a healthy startup performs no retry at all', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    await vi.advanceTimersByTimeAsync(60_000);

    expect(getCompanies).toHaveBeenCalledTimes(1); // one cycle, never retried
  });

  // 2. initial status request times out, second attempt succeeds
  // 5. recovery succeeds without another status-updated event
  it('an initial dashboard timeout recovers automatically on the next bounded attempt, with no further lifecycle event', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(unhealthyState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await expect(startDesktopShell()).resolves.toBeUndefined();
    expect(document.getElementById('footer-connection-status')?.textContent).not.toBe('Connected');

    dashboard.set(dashboardState()); // the Connector recovers on its own before the next attempt
    await vi.advanceTimersByTimeAsync(2_000);
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
    expect(getCompanies).toHaveBeenCalledTimes(2); // initial cycle + one bounded retry cycle
    expect(bridge.onStatusUpdated).toHaveBeenCalledTimes(1); // listener registered, never fired
  });

  // 3. initial company discovery times out, later attempt succeeds
  // 6. company list appears automatically after backend recovery
  it('an initial company-discovery timeout recovers automatically and the company list appears', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn().mockImplementationOnce(fail('connector did not respond in time')).mockImplementation(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    expect(document.getElementById('company-list-status')?.textContent).toContain('failed');

    await vi.advanceTimersByTimeAsync(2_000);
    expect(getCompanies).toHaveBeenCalledTimes(2);
    expect(document.getElementById('company-list-status')?.textContent).toBe('1 companies available');
  });

  // 4. Connector lifecycle remains Connected throughout recovery — the dashboard-timeout and
  // company-timeout scenarios above (2, 3) both recover purely from the bounded timer, never
  // from bridge.__fireStatusUpdated(), which neither of them ever calls.

  // 7. stale company error clears after successful retry
  it('the stale "Unable to load companies" banner clears once a retry succeeds', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn().mockImplementationOnce(fail('timeout')).mockImplementation(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    expect(document.getElementById('app-notification')?.textContent).toContain('Unable to load companies');

    await vi.advanceTimersByTimeAsync(2_000);
    expect(document.getElementById('company-list-status')?.textContent).toBe('1 companies available');
  });

  // 8. Connection/Health/Version update after successful retry
  it('Connection/Health/Version update to the real values once a retry succeeds', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(unhealthyState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    dashboard.set(dashboardState({ connectorVersion: '0.4.0', healthStatus: 'ok' }));
    await vi.advanceTimersByTimeAsync(2_000);

    expect(document.getElementById('dashboard-connection')?.textContent).toBe('Connected');
    expect(document.getElementById('dashboard-health')?.textContent).toBe('Health: ok');
    expect(document.getElementById('dashboard-version')?.textContent).toBe('0.4.0');
  });

  // 9. bounded retry stops after success
  it('stops retrying immediately once recovery succeeds — no further calls at later bounded intervals', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(unhealthyState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    dashboard.set(dashboardState());
    await vi.advanceTimersByTimeAsync(2_000);
    expect(getCompanies).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(120_000);
    expect(getCompanies).toHaveBeenCalledTimes(2); // no growth once healthy
  });

  // 10. bounded retry stops after maximum attempts
  // 18. non-retryable/persistent error does not create an endless recovery loop
  // 19. repeated transient failure leaves an understandable final UI state
  it('gives up after the bounded number of attempts and leaves a visible failure state, never retrying forever', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(unhealthyState());
    const getCompanies = vi.fn(async () => {
      throw new Error('persistent connector failure');
    });
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    await vi.advanceTimersByTimeAsync(2_000 + 5_000 + 10_000 + 20_000 + 30_000 + 1_000);

    // initial cycle + 5 bounded retry cycles = 6 total, then it must stop.
    expect(getCompanies).toHaveBeenCalledTimes(6);
    await vi.advanceTimersByTimeAsync(300_000);
    expect(getCompanies).toHaveBeenCalledTimes(6); // no further growth — bounded, not infinite

    expect(document.getElementById('app-notification')?.textContent).toContain('Unable to load companies');
    expect(document.getElementById('company-list-status')?.textContent).toBe('Company discovery failed.');
  });

  // 11. no permanent polling while healthy
  it('performs no background network calls at all while already healthy', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    await vi.advanceTimersByTimeAsync(10 * 60_000);

    expect(getCompanies).toHaveBeenCalledTimes(1);
  });

  // 12. teardown cancels pending retry
  it('stopBoundedRecovery (window teardown) cancels a pending scheduled retry', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(unhealthyState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    stopBoundedRecovery();
    dashboard.set(dashboardState());
    await vi.advanceTimersByTimeAsync(60_000);

    expect(getCompanies).toHaveBeenCalledTimes(1); // the scheduled retry never fired
  });

  // 13. manual Refresh during recovery performs fresh request safely
  it('a manual Refresh Companies click during an in-progress recovery performs its own fresh request safely', async () => {
    const bridge = baseBridge();
    let releaseSecondCall: (() => void) | null = null;
    const getCompanies = vi.fn()
      .mockRejectedValueOnce(new Error('timeout'))
      .mockImplementationOnce(
        () => new Promise((resolve) => {
          releaseSecondCall = () => resolve(SUCCESS_COMPANIES);
        }),
      );
    const dashboard = mutableDashboardState(dashboardState());
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    // The bounded recovery cycle's next attempt is scheduled but has not fired yet (delay 2s).
    document.getElementById('btn-refresh-companies')?.dispatchEvent(new Event('click', { bubbles: true }));
    await vi.advanceTimersByTimeAsync(0);
    expect(getCompanies).toHaveBeenCalledTimes(2); // manual click issued its own request immediately

    releaseSecondCall?.();
    await vi.advanceTimersByTimeAsync(0);
    expect(document.getElementById('company-list-status')?.textContent).toBe('1 companies available');
  });

  // 14. manual Refresh success cancels obsolete recovery work
  it('a successful manual refresh cancels the scheduled automatic retry so it never fires', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn()
      .mockRejectedValueOnce(new Error('timeout'))
      .mockResolvedValue(SUCCESS_COMPANIES);
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    document.getElementById('btn-refresh-companies')?.dispatchEvent(new Event('click', { bubbles: true }));
    await vi.advanceTimersByTimeAsync(0);
    expect(getCompanies).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(60_000);
    expect(getCompanies).toHaveBeenCalledTimes(2); // the original bounded retry never additionally fired
  });

  // 15. older timeout cannot overwrite newer success
  it('an older, slower company-load call can never overwrite a newer, faster successful result', async () => {
    const bridge = baseBridge();
    let resolveOlder: ((v: { items: CompanyListItemDto[]; status: string }) => void) | null = null;
    const getCompanies = vi.fn()
      .mockImplementationOnce(
        () => new Promise((resolve) => {
          resolveOlder = resolve;
        }),
      )
      .mockResolvedValueOnce(SUCCESS_COMPANIES);
    const dashboard = mutableDashboardState(dashboardState());
    window.budcomDesktop = {
      ...bridge,
      getDashboardState: dashboard.fn,
      getCompanies,
    } as unknown as typeof window.budcomDesktop;

    const olderCall = loadCompanies(); // starts first, stays pending
    await loadCompanies(); // starts later, resolves immediately as the "newer" call
    expect(document.getElementById('company-list-status')?.textContent).toBe('1 companies available');

    resolveOlder?.({ items: [], status: 'EMPTY' }); // the older call finally resolves...
    await olderCall;
    // ...but must NOT have overwritten the newer, already-rendered success.
    expect(document.getElementById('company-list-status')?.textContent).toBe('1 companies available');
  });

  // 16. lifecycle reconnect starts/restarts bounded recovery correctly
  it('a lifecycle status-updated push refreshes both dashboard and company state and reconciles recovery', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell(); // cycle 1 — healthy
    expect(getCompanies).toHaveBeenCalledTimes(1);

    dashboard.set(unhealthyState());
    bridge.__fireStatusUpdated(); // simulates a disconnect transition — cycle 2
    await vi.advanceTimersByTimeAsync(0);
    expect(getCompanies).toHaveBeenCalledTimes(2);
    expect(document.getElementById('footer-connection-status')?.textContent).not.toBe('Connected');

    // bounded recovery, freshly started by the disconnect reconcile — cycle 3, finds health restored.
    dashboard.set(dashboardState());
    await vi.advanceTimersByTimeAsync(2_000);
    expect(getCompanies).toHaveBeenCalledTimes(3);
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
  });

  // Startup-stabilization regression: a lifecycle transition pushed while the very first
  // refreshUi()/loadCompanies() pull is still in flight must not be silently dropped. Before the
  // fix, onStatusUpdated was registered only after that initial pull completed, so a push that
  // fired during it (e.g. a connector reaching 'connected' within the same window as the first
  // render) had no listener to catch it — and if the session already had an active company
  // (satisfying TD-014's bounded-recovery health check on unrelated grounds), nothing else would
  // ever re-poll, leaving the renderer stuck showing a stale 'starting' state indefinitely.
  it('a lifecycle transition pushed while the initial startup pull is still in flight is not silently dropped', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    let lifecycleState = { ...lifecycleStatusFixture, state: 'starting' as const, stateLabel: 'Starting' as const };
    let pushed = false;
    const getLifecycleStatus = vi.fn(async () => {
      const current = lifecycleState;
      if (!pushed) {
        pushed = true;
        // Simulates the main process completing a fast 'starting' -> 'connected' transition and
        // pushing desktop:status-updated at essentially the same moment this very call resolves
        // with the still-'starting' snapshot — the exact race this fix closes.
        lifecycleState = { ...lifecycleStatusFixture, state: 'connected' as const, stateLabel: 'Connected' as const };
        bridge.__fireStatusUpdated();
      }
      return current;
    });
    window.budcomDesktop = {
      ...bridge,
      getDashboardState: dashboard.fn,
      getCompanies,
      getLifecycleStatus,
    } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    await vi.advanceTimersByTimeAsync(0);

    expect(getLifecycleStatus.mock.calls.length).toBeGreaterThan(1); // the push triggered a fresh pull
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
  });

  // 17. network endpoint transition + transient failure eventually recovers
  it('an endpoint-transition-shaped failure (unreachable, then reachable again with no exception) recovers within the bounded window', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(unhealthyState()); // old endpoint, not reachable there
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    expect(document.getElementById('footer-connection-status')?.textContent).not.toBe('Connected');

    await vi.advanceTimersByTimeAsync(2_000); // attempt 1 — still unhealthy, endpoint hasn't moved yet
    expect(document.getElementById('footer-connection-status')?.textContent).not.toBe('Connected');

    dashboard.set(dashboardState()); // effective endpoint has since moved to the reachable one (TD-015)
    await vi.advanceTimersByTimeAsync(5_000); // attempt 2 — now healthy
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
    expect(getCompanies).toHaveBeenCalledTimes(3); // initial cycle + 2 bounded retry cycles
  });

  it('recovers a company the backend re-selects shortly after becoming reachable, with no manual reselection and no lifecycle push — the Desktop-restart race', async () => {
    const bridge = baseBridge();
    // The Connector is reachable immediately after a Desktop restart, but its own session
    // recovery (re-selecting the previously active company) has not finished yet — exactly the
    // reported bug's starting snapshot. connectorReachable alone must not be read as "healthy"
    // here, or the bounded recovery loop stops before the company ever appears.
    const dashboard = mutableDashboardState(
      dashboardState({ sessionStatus: 'NO_COMPANY_SELECTED', companyName: '—', companyId: '—' }),
    );
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    expect(document.getElementById('footer-company')?.textContent).toBe('');

    // The backend finishes recovering ESTIMATION on its own. bridge.__fireStatusUpdated() is
    // deliberately never called — nothing but the bounded recovery timer may catch this.
    dashboard.set(dashboardState());
    await vi.advanceTimersByTimeAsync(2_000);

    expect(document.getElementById('footer-company')?.textContent).toBe('· ESTIMATION');
    expect(document.getElementById('dashboard-company-name')?.textContent).toBe('ESTIMATION');
    expect(bridge.onStatusUpdated).toHaveBeenCalledTimes(1); // registered; the listener itself never invoked
  });

  // 20. successful recovery does not require application restart
  // (implicit throughout — every test above recovers within the same startDesktopShell() call,
  // with no re-import/re-init of the module.)

  // ============================== Phase 11: Group 5 acceptance scenario ==============================
  // "Connector lifecycle becomes Connected -> first Desktop company/health request deliberately
  // times out -> backend becomes healthy -> lifecycle remains Connected -> no further lifecycle
  // transition -> automatic bounded recovery runs -> dashboard becomes healthy -> company
  // appears -> stale error disappears -> manual Refresh was never used."
  it.each(Array.from({ length: 10 }, (_, i) => i + 1))(
    'Group 5 acceptance scenario — consecutive run %i/10',
    async () => {
      const bridge = baseBridge();
      const dashboard = mutableDashboardState(unhealthyState());
      const getCompanies = vi.fn().mockImplementationOnce(fail('The connector did not respond in time.')).mockImplementation(ok(SUCCESS_COMPANIES));
      window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

      await startDesktopShell();
      // Initial transient failure, exactly as TD-014 describes.
      expect(document.getElementById('footer-connection-status')?.textContent).not.toBe('Connected');
      expect(document.getElementById('app-notification')?.textContent).toContain('Unable to load companies');

      // The backend becomes healthy on its own, with no further lifecycle transition ever fired
      // in this test — bridge.__fireStatusUpdated() is deliberately never called. Recovery must
      // happen from the bounded timer alone.
      dashboard.set(dashboardState());
      await vi.advanceTimersByTimeAsync(2_000);

      expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
      expect(document.getElementById('dashboard-health')?.textContent).toBe('Health: ok');
      expect(document.getElementById('company-list-status')?.textContent).toBe('1 companies available');
      expect(bridge.onStatusUpdated).toHaveBeenCalledTimes(1); // registered; the listener itself never invoked
      expect(getCompanies).toHaveBeenCalledTimes(2); // initial cycle + one bounded retry cycle
    },
  );

  it('bounded recovery uses the documented increasing-delay schedule, not a fixed interval', async () => {
    const bridge = baseBridge();
    const dashboard = mutableDashboardState(unhealthyState());
    const getCompanies = vi.fn(async () => {
      throw new Error('persistent');
    });
    window.budcomDesktop = { ...bridge, getDashboardState: dashboard.fn, getCompanies } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    expect(getCompanies).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1_999);
    expect(getCompanies).toHaveBeenCalledTimes(1); // not yet — the first bounded delay is 2s
    await vi.advanceTimersByTimeAsync(1);
    expect(getCompanies).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(4_999);
    expect(getCompanies).toHaveBeenCalledTimes(2); // not yet — second delay is 5s
    await vi.advanceTimersByTimeAsync(1);
    expect(getCompanies).toHaveBeenCalledTimes(3);
  });

  it('reconcileBoundedRecovery is a safe no-op immediately after a genuinely healthy state', () => {
    // No pending timer, nothing scheduled, no window.budcomDesktop calls made at all.
    window.budcomDesktop = baseBridge() as unknown as typeof window.budcomDesktop;
    expect(() => reconcileBoundedRecovery()).not.toThrow();
  });
});

// TD-025: mid-session private-storage loss must show the purpose-built storage-unavailable screen
// (via the same renderStorageGate() flow already used at startup), not degrade to the ordinary
// dashboard's generic "Disconnected" status — and must never let the ordinary refresh run while
// storage is unavailable, so no misleading Connector/network message can appear underneath it.
describe('TD-025 mid-session private-storage loss', () => {
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
        <p class="storage-gate-unavailable-message"></p>
        <button type="button" id="storage-gate-timeout-retry"></button>
        <button type="button" id="storage-gate-timeout-exit"></button>
      </div>
    </div>
  `;

  function mutableStorageStatus(initial: { kind: string; [k: string]: unknown }) {
    let current = initial;
    return {
      fn: vi.fn(async () => current),
      set: (next: typeof initial) => {
        current = next;
      },
    };
  }

  beforeEach(() => {
    vi.useFakeTimers();
    document.body.innerHTML = DASHBOARD_MARKUP + STORAGE_GATE_MARKUP;
  });

  afterEach(() => {
    stopBoundedRecovery();
    vi.useRealTimers();
  });

  it('shows the dedicated storage-unavailable screen instead of an ordinary refresh, and blocks refresh until storage returns', async () => {
    const bridge = baseBridge();
    const storageStatus = mutableStorageStatus({ kind: 'ready', mode: 'standard' });
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = {
      ...bridge,
      getDashboardState: dashboard.fn,
      getCompanies,
      getStorageStatus: storageStatus.fn,
      // Mirrors the real desktop:retry-storage-connection handler: re-resolves and returns
      // whatever the (now updated) storage state is.
      retryStorageConnection: vi.fn(() => storageStatus.fn()),
    } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    const refreshCallsBeforeLoss = dashboard.fn.mock.calls.length;
    expect(document.getElementById('storage-gate-overlay')?.classList.contains('hidden')).toBe(true);

    // The watchdog in main.ts stops the Connector and marks storage unavailable BEFORE it fires
    // notifyRenderer() — by the time this renderer event arrives, getStorageStatus() already
    // reflects the loss.
    storageStatus.set({ kind: 'unavailable', reason: 'missing' });
    void bridge.__fireStatusUpdated();
    await Promise.resolve();
    await Promise.resolve();

    // G: dedicated storage-loss screen shown, not a generic dashboard refresh.
    expect(document.getElementById('storage-gate-overlay')?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('storage-gate-unavailable')?.classList.contains('hidden')).toBe(false);
    // M: the detail text names storage specifically, never implying a network/Connector problem.
    expect(document.getElementById('storage-gate-unavailable-detail')?.textContent).toContain('storage');
    // E/F/L: refresh is blocked while unavailable — no additional dashboard poll, no tight loop.
    expect(dashboard.fn.mock.calls.length).toBe(refreshCallsBeforeLoss);

    // H: the same vault reconnects — Retry resolves it, and normal refresh resumes.
    storageStatus.set({ kind: 'ready', mode: 'private-removable', driveLetter: 'E:\\', volumeLabel: 'BUDCOM-USB' });
    document.getElementById('storage-gate-retry')!.dispatchEvent(new Event('click', { bubbles: true }));
    for (let tick = 0; tick < 10; tick += 1) {
      await Promise.resolve();
    }

    expect(document.getElementById('storage-gate-overlay')?.classList.contains('hidden')).toBe(true);
    expect(dashboard.fn.mock.calls.length).toBeGreaterThan(refreshCallsBeforeLoss);
  });

  it('a status update that is unrelated to storage (storage already ready) never shows the overlay', async () => {
    const bridge = baseBridge();
    const storageStatus = mutableStorageStatus({ kind: 'ready', mode: 'standard' });
    const dashboard = mutableDashboardState(dashboardState());
    const getCompanies = vi.fn(ok(SUCCESS_COMPANIES));
    window.budcomDesktop = {
      ...bridge,
      getDashboardState: dashboard.fn,
      getCompanies,
      getStorageStatus: storageStatus.fn,
    } as unknown as typeof window.budcomDesktop;

    await startDesktopShell();
    const callsBefore = dashboard.fn.mock.calls.length;

    // An ordinary lifecycle transition (e.g. a company change) — storage was never affected.
    void bridge.__fireStatusUpdated();
    await Promise.resolve();
    await Promise.resolve();

    expect(document.getElementById('storage-gate-overlay')?.classList.contains('hidden')).toBe(true);
    expect(dashboard.fn.mock.calls.length).toBeGreaterThan(callsBefore);
  });
});
