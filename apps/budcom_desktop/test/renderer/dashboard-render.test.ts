/**
 * @vitest-environment jsdom
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  activateView,
  bindCompanyActions,
  renderCompanyList,
  renderDashboard,
  renderLogs,
  setBanner,
  setLoading,
  type DesktopView,
} from '../../src/renderer/scripts/app.js';
import type { DashboardState } from '../../src/application/types.js';
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

    window.budcomDesktop = bridge;

    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    await Promise.all([refreshUi(), refreshUi()]);
    expect(bridge.getDashboardState).toHaveBeenCalledTimes(2);
  });

  it('keeps background refresh visually silent', async () => {
    const bridge = {
      getDashboardState: vi.fn(async () => sampleState),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    };
    window.budcomDesktop = bridge as typeof window.budcomDesktop;
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
    window.budcomDesktop = bridge as typeof window.budcomDesktop;

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
    window.budcomDesktop = bridge as typeof window.budcomDesktop;
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
    window.budcomDesktop = bridge as typeof window.budcomDesktop;

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
    window.budcomDesktop = bridge as typeof window.budcomDesktop;
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
    window.budcomDesktop = bridge as typeof window.budcomDesktop;
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
