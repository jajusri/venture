/**
 * @vitest-environment jsdom
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  activateView,
  bindCompanyActions,
  refreshDashboardDataFreshness,
  renderCompanyList,
  renderDashboard,
  renderLogs,
  setBanner,
  setLoading,
  type DesktopView,
} from '../../src/renderer/scripts/app.js';
import type {
  DashboardState,
  LedgerStatisticsResult,
  LedgerSyncProgressResult,
  SchedulerStateDto,
  StockItemStatisticsResult,
  StockItemSyncProgressResult,
} from '../../src/application/types.js';
import { lifecycleStatusFixture, settingsFixture } from '../helpers/lifecycle-fixtures.js';

const sampleState: DashboardState = {
  windowTitle: 'Business OS Tally Connector',
  connectorVersion: '0.3.1',
  apiVersion: '1.0.0',
  desktopVersion: '0.4.3',
  erpType: 'tally',
  erpName: 'Tally',
  connectionIndicator: 'connected',
  connectionLabel: 'Connected',
  companyName: 'ESTIMATION',
  companyId: 'estimation',
  selectionTime: '2026-07-22T17:00:00.000Z',
  sessionStatus: 'ACTIVE',
  syncStatus: 'idle',
  syncLabel: 'Idle',
  lastSync: '2026-07-22T17:05:00.000Z',
  lastRefresh: '2026-07-23T00:00:00.000Z',
  licenseStatus: 'Evaluation',
  connectorReachable: true,
  healthStatus: 'ok',
  userMessage: null,
};

describe('renderer dashboard', () => {
  beforeEach(() => {
    document.body.innerHTML = `
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
      <div id="dashboard-erp-name"></div>
      <div id="dashboard-last-refresh"></div>
      <div id="dashboard-sync"></div>
      <div id="dashboard-last-sync"></div>
      <div id="dashboard-checking-frequency"></div>
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
      <div id="app-notification" class="app-notification hidden"></div>
      <section id="view-dashboard" class="view active"></section>
      <section id="view-logs" class="view"></section>
      <button class="nav-btn active" data-view="dashboard"></button>
      <button class="nav-btn" data-view="logs"></button>
    `;
  });

  it('renders dashboard card values', () => {
    renderDashboard(sampleState);
    expect(document.getElementById('dashboard-company-name')?.textContent).toBe('ESTIMATION');
    expect(document.getElementById('dashboard-connection')?.textContent).toBe('Connected');
    // Humanized, not the raw SessionDisplayStatus enum ('ACTIVE') — see formatSessionStatus.
    expect(document.getElementById('connection-detail-session')?.textContent).toBe('Active');
    expect(document.getElementById('connection-indicator')?.className).toContain('indicator-connected');
  });

  it('does not mutate unchanged dashboard fields or disturb focus and active view', () => {
    renderDashboard(sampleState);
    const company = document.getElementById('dashboard-company-name')!;
    const logsButton = document.querySelector<HTMLButtonElement>('[data-view="logs"]')!;
    activateView('logs');
    logsButton.focus();
    const observer = new MutationObserver(() => undefined);
    observer.observe(company, { childList: true, characterData: true, subtree: true });

    renderDashboard(sampleState);

    expect(observer.takeRecords()).toHaveLength(0);
    expect(document.activeElement).toBe(logsButton);
    expect(document.getElementById('view-logs')?.classList.contains('active')).toBe(true);
    observer.disconnect();
  });

  it('keeps status transitions from changing page height, scroll, active tab, or focus', () => {
    const footer = document.createElement('footer');
    footer.className = 'app-footer';
    footer.append(
      document.getElementById('footer-connection-indicator')!,
      document.getElementById('footer-connection-status')!,
      document.getElementById('footer-company')!,
      document.getElementById('footer-license')!,
    );
    document.body.append(footer);
    Object.defineProperty(footer, 'offsetHeight', { configurable: true, value: 40 });
    const main = document.createElement('main');
    main.scrollTop = 240;
    const input = document.createElement('input');
    main.append(input);
    document.body.append(main);
    activateView('logs');
    input.focus();

    renderDashboard(sampleState);
    setLoading({ dashboard: true }, 'Refreshing…');
    setLoading({ dashboard: false });

    expect(footer.offsetHeight).toBe(40);
    expect(main.scrollTop).toBe(240);
    expect(document.activeElement).toBe(input);
    expect(document.getElementById('view-logs')?.classList.contains('active')).toBe(true);
  });

  it('does not visibly advance the refresh timestamp during a background render', () => {
    renderDashboard(sampleState);
    renderDashboard(
      { ...sampleState, lastRefresh: '2026-07-23T00:00:05.000Z' },
      { includeRefreshTimestamp: false },
    );

    expect(document.getElementById('dashboard-last-refresh')?.textContent)
      .toBe(sampleState.lastRefresh);
  });

  it('does not rebuild identical logs and preserves existing rows', () => {
    const entries = [{
      id: 'log-1',
      timestamp: '2026-07-26T00:00:00.000Z',
      level: 'information' as const,
      message: 'Connected',
      event: null,
      component: null,
      metadata: null,
    }];
    document.getElementById('company-list')!.id = 'log-list';
    renderLogs(entries);
    const originalRow = document.querySelector('[data-log-id="log-1"]');

    renderLogs(entries);

    expect(document.querySelector('[data-log-id="log-1"]')).toBe(originalRow);
  });

  it('activates navigation views', () => {
    activateView('logs' as DesktopView);
    expect(document.getElementById('view-logs')?.classList.contains('active')).toBe(true);
    expect(document.getElementById('view-dashboard')?.classList.contains('active')).toBe(false);
  });

  it('renders company list with selected company', () => {
    renderCompanyList(
      [
        { id: 'estimation', name: 'ESTIMATION' },
        { id: 'demo', name: 'DEMO' },
      ],
      'estimation',
      '2 companies available',
    );

    expect(document.getElementById('company-list-status')?.textContent).toBe('2 companies available');
    const selected = document.querySelector('.company-item.selected');
    expect(selected?.getAttribute('data-company-id')).toBe('estimation');
    expect(selected?.getAttribute('role')).toBe('radio');
    expect(selected?.getAttribute('aria-checked')).toBe('true');
    expect(document.getElementById('company-list')?.getAttribute('role')).toBe('radiogroup');
    expect(document.querySelectorAll('.company-radio')).toHaveLength(2);
  });

  it('supports roving radio focus with arrow keys and selection with the keyboard', () => {
    renderCompanyList(
      [
        { id: 'estimation', name: 'ESTIMATION' },
        { id: 'demo', name: 'DEMO' },
      ],
      'estimation',
      '2 companies available',
    );
    bindCompanyActions();
    const radios = Array.from(document.querySelectorAll<HTMLButtonElement>('[role="radio"]'));
    radios[0]?.focus();

    radios[0]?.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'ArrowDown',
      bubbles: true,
    }));
    expect(document.activeElement).toBe(radios[1]);
    expect(radios[0]?.tabIndex).toBe(-1);
    expect(radios[1]?.tabIndex).toBe(0);

    const click = vi.spyOn(radios[1]!, 'click');
    radios[1]?.dispatchEvent(new KeyboardEvent('keydown', {
      key: ' ',
      bubbles: true,
    }));
    expect(click).toHaveBeenCalledOnce();
  });

  it('shows transient loading in the fixed status bar and surfaces notifications through the app notification region', () => {
    setLoading({ dashboard: true });
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Loading…');
    expect(document.getElementById('footer-connection-indicator')?.className)
      .toBe('status-dot status-refreshing');

    setBanner('Connector unavailable', 'warning');
    const notification = document.getElementById('app-notification');
    expect(notification?.textContent).toContain('Connector unavailable');
    expect(notification?.className).toContain('app-notification-warning');
    expect(notification?.className).not.toContain('hidden');
    setLoading({ dashboard: false });
  });
});

describe('renderer integration refresh', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <span id="footer-connection-indicator" class="status-dot status-unknown"></span>
      <strong id="footer-connection-status"></strong>
      <span id="footer-company"></span>
      <div id="app-notification" class="app-notification hidden"></div>
      <main id="main-content">
        <div id="company-list" role="radiogroup" aria-label="Available companies">
          <button class="company-item selected" data-company-id="estimation"
            role="radio" aria-checked="true">ESTIMATION</button>
        </div>
      </main>
    `;
  });

  it('queues one trailing refresh while a refresh is in flight', async () => {
    const bridge = {
      getDashboardState: vi.fn(async () => sampleState),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
      validateSettings: vi.fn(),
      saveSettings: vi.fn(),
      restoreDefaultSettings: vi.fn(),
      startConnector: vi.fn(),
      stopConnector: vi.fn(),
      restartConnector: vi.fn(),
      getCompanies: vi.fn(),
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
      onStatusUpdated: vi.fn(),
    };

    window.ventureDesktop = bridge;

    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    await Promise.all([refreshUi(), refreshUi()]);
    expect(bridge.getDashboardState).toHaveBeenCalledTimes(2);
  });

  it('re-pulls dashboard/connection state when the user navigates back to Dashboard, so a health change that occurred while elsewhere is never left stale', async () => {
    document.body.innerHTML += `
      <button class="nav-btn active" data-view="dashboard"></button>
      <button class="nav-btn" data-view="logs"></button>
      <section id="view-dashboard" class="view active"></section>
      <section id="view-logs" class="view"></section>
    `;
    const bridge = {
      getDashboardState: vi.fn(async () => sampleState),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    };
    window.ventureDesktop = bridge as unknown as typeof window.ventureDesktop;
    const { activateView: activate, refreshUi } = await import('../../src/renderer/scripts/app.js');

    await refreshUi({ showLoading: false });
    expect(bridge.getDashboardState).toHaveBeenCalledTimes(1);

    // The Connector can stay lifecycle-"connected" (so no desktop:status-updated push fires) while
    // its own /health genuinely degrades in between pushes — e.g. Tally itself disconnects but the
    // Connector process keeps running. Navigating away and back to Dashboard is the one moment that
    // must always re-check, independent of any push.
    activate('logs');
    activate('dashboard');
    expect(bridge.getDashboardState).toHaveBeenCalledTimes(2);
    await Promise.resolve();
    await Promise.resolve();
  });

  it('keeps background refresh visually silent', async () => {
    const bridge = {
      getDashboardState: vi.fn(async () => sampleState),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    };
    window.ventureDesktop = bridge as typeof window.ventureDesktop;
    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    await refreshUi({ showLoading: false });

    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
  });

  it('keeps refresh silent by default for automatic health and company-state updates', async () => {
    const bridge = {
      getDashboardState: vi.fn(async () => sampleState),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    };
    window.ventureDesktop = bridge as typeof window.ventureDesktop;

    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    await refreshUi();

    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
  });

  it('preserves the page, selected company, and scroll position across repeated status refreshes', async () => {
    const bridge = {
      getDashboardState: vi.fn(async () => sampleState),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    };
    window.ventureDesktop = bridge as typeof window.ventureDesktop;
    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    const body = document.body;
    const main = document.getElementById('main-content') as HTMLElement;
    const companyList = document.getElementById('company-list');
    const selectedCompany = document.querySelector('[data-company-id="estimation"]');
    main.scrollTop = 275;

    for (let refresh = 0; refresh < 20; refresh += 1) {
      await refreshUi({ showLoading: false });
    }

    expect(document.body).toBe(body);
    expect(document.getElementById('main-content')).toBe(main);
    expect(document.getElementById('company-list')).toBe(companyList);
    expect(document.querySelector('[data-company-id="estimation"]')).toBe(selectedCompany);
    expect(selectedCompany?.getAttribute('aria-checked')).toBe('true');
    expect(selectedCompany?.classList.contains('selected')).toBe(true);
    expect(main.scrollTop).toBe(275);
  });

  it('allows an explicit manual refresh to own the bottom status message', async () => {
    let resolveDashboard: ((state: typeof sampleState) => void) | null = null;
    const bridge = {
      getDashboardState: vi.fn(
        () => new Promise<typeof sampleState>((resolve) => {
          resolveDashboard = resolve;
        }),
      ),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    };
    window.ventureDesktop = bridge as typeof window.ventureDesktop;

    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    const refresh = refreshUi({ showLoading: true });
    await Promise.resolve();
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Refreshing…');
    expect(document.getElementById('footer-connection-indicator')?.className)
      .toBe('status-dot status-refreshing');

    resolveDashboard?.(sampleState);
    await refresh;
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
  });

  it('keeps the app notification silent during a background refresh failure', async () => {
    const bridge = {
      getDashboardState: vi.fn(async () => ({
        ...sampleState,
        connectorReachable: false,
        userMessage: 'Background connector request failed.',
      })),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    };
    window.ventureDesktop = bridge as typeof window.ventureDesktop;
    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    await refreshUi();

    const notification = document.getElementById('app-notification');
    expect(notification?.className).toContain('hidden');
    expect(notification?.textContent).toBe('');
  });

  it('remains stable across 15 minutes of unchanged five-second background refreshes', async () => {
    document.body.innerHTML += `
      <button class="nav-btn active" data-view="logs">Logs</button>
      <section id="view-logs" class="view active">
        <input id="log-filter" />
        <div id="log-list"></div>
      </section>
    `;
    const entries = [{
      id: 'log-1',
      timestamp: '2026-07-26T00:00:00.000Z',
      level: 'information' as const,
      message: 'Connector Connected',
      event: null,
      component: null,
      metadata: null,
    }];
    const bridge = {
      getDashboardState: vi.fn(async () => sampleState),
      getLogs: vi.fn(async () => entries),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    };
    window.ventureDesktop = bridge as typeof window.ventureDesktop;
    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    const filter = document.getElementById('log-filter') as HTMLInputElement;

    await refreshUi({ showLoading: false });
    const originalLogRow = document.querySelector('[data-log-id="log-1"]');
    filter.focus();
    for (let heartbeat = 0; heartbeat < 180; heartbeat += 1) {
      await refreshUi({ showLoading: false });
    }

    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
    expect(document.querySelector('[data-log-id="log-1"]')).toBe(originalLogRow);
    expect(document.querySelectorAll('[data-log-id="log-1"]')).toHaveLength(1);
    expect(document.activeElement).toBe(filter);
    expect(document.getElementById('view-logs')?.classList.contains('active')).toBe(true);
    expect(bridge.getDashboardState).toHaveBeenCalledTimes(181);
  });
});

// UX polish: the Dashboard's "Sync"/"Last Sync" fields (header + card) used to be driven by
// state.syncLabel/state.lastSync — the Connector's own dead SyncEngine placeholder and session-
// validation time, never actual Ledger/Stock Item data-sync freshness (see renderDashboard()'s own
// comment). refreshDashboardDataFreshness() replaces that with the real, already-tracked
// per-module last-synced timestamps.
describe('refreshDashboardDataFreshness', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="app-title"></div>
      <div id="header-version"></div>
      <div id="header-company"></div>
      <div id="header-sync"></div>
      <div id="header-last-sync"></div>
      <div id="header-connection-label"></div>
      <div id="connection-indicator"></div>
      <div id="dashboard-health"></div>
      <div id="dashboard-company-name"></div>
      <div id="dashboard-erp-name"></div>
      <div id="dashboard-last-refresh"></div>
      <div id="dashboard-sync"></div>
      <div id="dashboard-last-sync"></div>
      <div id="dashboard-checking-frequency"></div>
      <div id="dashboard-version"></div>
      <div id="dashboard-desktop-version"></div>
      <div id="connection-detail-reachable"></div>
      <div id="connection-detail-health"></div>
      <div id="connection-detail-session"></div>
      <span id="footer-connection-indicator" class="status-dot status-unknown"></span>
      <strong id="footer-connection-status"></strong>
      <span id="footer-company"></span>
      <strong id="footer-license"></strong>
      <div id="app-notification" class="app-notification hidden"></div>
      <button id="btn-sync-ledgers"></button>
      <button id="btn-sync-stock-items"></button>
    `;
    // Every test in this block starts from a known "has an active company" baseline (not left to
    // whatever a previous test in this file happened to leave latestDashboardState at) — the
    // dedicated 'no company selected' test below then explicitly overrides it.
    renderDashboard({ ...sampleState, sessionStatus: 'ACTIVE', companyName: 'ESTIMATION' });
  });

  const ledgerStats = (lastSyncedAt: string | null): LedgerStatisticsResult => ({
    schemaVersion: '1.0.0',
    statistics: { totalLedgers: 5, activeLedgers: 5, inactiveLedgers: 0, reservedLedgers: 0, deletedLedgers: 0, withGst: 0, withOpeningBalance: 0, lastSyncedAt },
  });
  const stockStats = (lastSyncedAt: string | null): StockItemStatisticsResult => ({
    schemaVersion: '1.0.0',
    statistics: { totalStockItems: 3, withBaseUnit: 3, incompleteData: 0, withHsn: 0, withGst: 0, withOpeningBalance: 0, deletedStockItems: 0, lastSyncedAt },
  });
  const progressWithScheduler = (
    schedulerState: SchedulerStateDto | null,
  ): LedgerSyncProgressResult & StockItemSyncProgressResult => ({
    schemaVersion: '1.0.0',
    progress: {
      status: 'idle',
      totalExpected: null,
      startedAt: null,
      completedAt: null,
      durationMs: null,
      itemsProcessed: 0,
      itemsAdded: 0,
      itemsUpdated: 0,
      itemsSkipped: 0,
      itemsFailed: 0,
      lastError: null,
      cancelRequested: false,
    },
    schedulerState,
  });

  it('reports "Not synced yet" / "Never" when neither module has ever synced', async () => {
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => ledgerStats(null)),
      getStockItemStatistics: vi.fn(async () => stockStats(null)),
    } as unknown as typeof window.ventureDesktop;

    await refreshDashboardDataFreshness();

    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Not synced yet');
    expect(document.getElementById('dashboard-last-sync')?.textContent).toBe('Never');
    expect(document.getElementById('header-sync')?.textContent).toBe('Not synced yet');
  });

  it('reports "Partially synced" when only one module has ever synced, using its real timestamp', async () => {
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => ledgerStats('2026-08-22T10:00:00.000Z')),
      getStockItemStatistics: vi.fn(async () => stockStats(null)),
    } as unknown as typeof window.ventureDesktop;

    await refreshDashboardDataFreshness();

    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Partially synced');
    expect(document.getElementById('dashboard-last-sync')?.textContent).toBe('2026-08-22T10:00:00.000Z');
  });

  it('reports "Synced" with the more recent of the two real timestamps once both modules have synced', async () => {
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => ledgerStats('2026-08-22T10:00:00.000Z')),
      getStockItemStatistics: vi.fn(async () => stockStats('2026-08-22T12:30:00.000Z')),
    } as unknown as typeof window.ventureDesktop;

    await refreshDashboardDataFreshness();

    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Synced');
    expect(document.getElementById('dashboard-last-sync')?.textContent).toBe('2026-08-22T12:30:00.000Z');
    expect(document.getElementById('header-last-sync')?.textContent).toBe('2026-08-22T12:30:00.000Z');
  });

  it('shows "Checking regularly" when either module is in the scheduler\'s active window', async () => {
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => ledgerStats('2026-08-22T10:00:00.000Z')),
      getStockItemStatistics: vi.fn(async () => stockStats('2026-08-22T10:00:00.000Z')),
      getLedgerSyncProgress: vi.fn(async () =>
        progressWithScheduler({ stage: 'active_window', nextCheckDueAt: '2026-08-22T10:05:00.000Z' }),
      ),
      getStockItemSyncProgress: vi.fn(async () =>
        progressWithScheduler({ stage: 'backoff_60', nextCheckDueAt: '2026-08-22T11:00:00.000Z' }),
      ),
    } as unknown as typeof window.ventureDesktop;

    await refreshDashboardDataFreshness();

    expect(document.getElementById('dashboard-checking-frequency')?.textContent).toBe('Checking regularly');
    // Raw internal stage names/minute intervals must never leak into this user-facing text.
    expect(document.getElementById('dashboard-checking-frequency')?.textContent).not.toMatch(/active_window|backoff|minute/i);
  });

  it('shows "Checking occasionally" when every known module is in some backoff stage', async () => {
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => ledgerStats('2026-08-22T10:00:00.000Z')),
      getStockItemStatistics: vi.fn(async () => stockStats('2026-08-22T10:00:00.000Z')),
      getLedgerSyncProgress: vi.fn(async () =>
        progressWithScheduler({ stage: 'backoff_15', nextCheckDueAt: '2026-08-22T10:15:00.000Z' }),
      ),
      getStockItemSyncProgress: vi.fn(async () =>
        progressWithScheduler({ stage: 'backoff_30', nextCheckDueAt: '2026-08-22T10:30:00.000Z' }),
      ),
    } as unknown as typeof window.ventureDesktop;

    await refreshDashboardDataFreshness();

    expect(document.getElementById('dashboard-checking-frequency')?.textContent).toBe('Checking occasionally');
  });

  it('leaves the checking-frequency line blank when the bridge/Connector has no scheduler state to report', async () => {
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => ledgerStats('2026-08-22T10:00:00.000Z')),
      getStockItemStatistics: vi.fn(async () => stockStats('2026-08-22T10:00:00.000Z')),
      // Intentionally omit getLedgerSyncProgress/getStockItemSyncProgress -- an older Connector
      // or bridge shape must degrade gracefully, never show a broken or fabricated label.
    } as unknown as typeof window.ventureDesktop;

    await refreshDashboardDataFreshness();

    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Synced');
    expect(document.getElementById('dashboard-checking-frequency')?.textContent).toBe('');
  });

  it('never fails the dashboard when a statistics call fails or the bridge lacks the method entirely', async () => {
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => { throw new Error('connector unreachable'); }),
      // Intentionally omit getStockItemStatistics entirely — an older bridge shape must not throw.
    } as unknown as typeof window.ventureDesktop;

    await expect(refreshDashboardDataFreshness()).resolves.toBeUndefined();
  });

  it('preserves an already-shown freshness summary when a later refresh fails on both calls, rather than reverting to "Checking…"', async () => {
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => ledgerStats('2026-08-22T10:00:00.000Z')),
      getStockItemStatistics: vi.fn(async () => stockStats('2026-08-22T12:30:00.000Z')),
    } as unknown as typeof window.ventureDesktop;
    await refreshDashboardDataFreshness();
    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Synced');

    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => { throw new Error('connector unreachable'); }),
      getStockItemStatistics: vi.fn(async () => { throw new Error('connector unreachable'); }),
    } as unknown as typeof window.ventureDesktop;
    await refreshDashboardDataFreshness();

    // A transient failure must not blank a previously-shown, still-useful freshness summary —
    // the same "refresh started, so wipe the good state" defect fixed for Ledgers/Stock Items.
    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Synced');
    expect(document.getElementById('dashboard-last-sync')?.textContent).toBe('2026-08-22T12:30:00.000Z');
  });

  it('never lets an older, slower call overwrite a newer, already-rendered result', async () => {
    let resolveOlder: ((value: LedgerStatisticsResult) => void) | null = null;
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn()
        .mockImplementationOnce(() => new Promise<LedgerStatisticsResult>((resolve) => { resolveOlder = resolve; }))
        .mockResolvedValueOnce(ledgerStats('2026-08-22T10:00:00.000Z')),
      getStockItemStatistics: vi.fn(async () => stockStats('2026-08-22T10:00:00.000Z')),
    } as unknown as typeof window.ventureDesktop;

    const olderCall = refreshDashboardDataFreshness(); // starts first, stays pending
    await refreshDashboardDataFreshness(); // starts later, resolves immediately
    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Synced');

    resolveOlder?.(ledgerStats(null)); // the older call's stale result must not win
    await olderCall;

    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Synced');
  });

  // Real-Desktop validation against a live Tally instance caught this: the Connector's statistics
  // endpoints require an active company and reject outright without one. Before this fix, that
  // completely normal state (right after cold launch, before any company is picked) left the
  // Sync card stuck on an indefinite "Checking…" that could never resolve.
  it('shows "—" (not a duplicate of the Company card\'s own message) without even attempting the doomed-to-fail statistics calls', async () => {
    renderDashboard({ ...sampleState, sessionStatus: 'NO_COMPANY_SELECTED', companyName: '—' });
    const getLedgerStatistics = vi.fn(async () => ledgerStats('2026-08-22T10:00:00.000Z'));
    const getStockItemStatistics = vi.fn(async () => stockStats('2026-08-22T10:00:00.000Z'));
    window.ventureDesktop = { getLedgerStatistics, getStockItemStatistics } as unknown as typeof window.ventureDesktop;

    await refreshDashboardDataFreshness();

    expect(document.getElementById('dashboard-sync')?.textContent).toBe('—');
    expect(document.getElementById('dashboard-last-sync')?.textContent).toBe('Never');
    expect(document.getElementById('header-sync')?.textContent).toBe('—');
    expect(getLedgerStatistics).not.toHaveBeenCalled();
    expect(getStockItemStatistics).not.toHaveBeenCalled();
  });

  it('re-attempts the real fetch once a company becomes active again, rather than getting stuck on "—"', async () => {
    renderDashboard({ ...sampleState, sessionStatus: 'NO_COMPANY_SELECTED', companyName: '—' });
    window.ventureDesktop = {
      getLedgerStatistics: vi.fn(async () => ledgerStats('2026-08-22T10:00:00.000Z')),
      getStockItemStatistics: vi.fn(async () => stockStats('2026-08-22T10:00:00.000Z')),
    } as unknown as typeof window.ventureDesktop;
    await refreshDashboardDataFreshness();
    expect(document.getElementById('dashboard-sync')?.textContent).toBe('—');

    renderDashboard({ ...sampleState, sessionStatus: 'ACTIVE', companyName: 'ESTIMATION' });
    await refreshDashboardDataFreshness();

    expect(document.getElementById('dashboard-sync')?.textContent).toBe('Synced');
  });
});
