/**
 * @vitest-environment jsdom
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  activateView,
  renderCompanyList,
  renderDashboard,
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
  desktopVersion: '0.4.2',
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
      <div id="global-banner" class="banner hidden"></div>
      <div id="global-loading" class="loading-bar hidden"><span id="loading-message"></span></div>
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
      <div id="footer-version"></div>
      <div id="footer-erp"></div>
      <div id="footer-license"></div>
      <div id="company-list"></div>
      <div id="company-list-status"></div>
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
    expect(document.getElementById('dashboard-session-status')?.textContent).toBe('ACTIVE');
    expect(document.getElementById('connection-indicator')?.className).toContain('indicator-connected');
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
    expect(document.querySelector('.company-item.selected')?.getAttribute('data-company-id')).toBe('estimation');
  });

  it('shows loading and banner states', () => {
    setLoading({ dashboard: true });
    expect(document.getElementById('global-loading')?.className).toBe('loading-bar');

    setBanner('Connector unavailable', 'warning');
    expect(document.getElementById('global-banner')?.textContent).toBe('Connector unavailable');
    expect(document.getElementById('global-banner')?.className).toContain('banner-warning');
  });
});

describe('renderer integration refresh', () => {
  it('deduplicates refresh calls through refreshInFlight guard', async () => {
    const bridge = {
      getDashboardState: vi.fn(async () => sampleState),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
      startConnector: vi.fn(),
      stopConnector: vi.fn(),
      restartConnector: vi.fn(),
      getCompanies: vi.fn(),
      selectCompany: vi.fn(),
      clearCompany: vi.fn(),
      onStatusUpdated: vi.fn(),
    };

    window.budcomDesktop = bridge;

    const { refreshUi } = await import('../../src/renderer/scripts/app.js');
    await Promise.all([refreshUi(), refreshUi()]);
    expect(bridge.getDashboardState).toHaveBeenCalledTimes(1);
  });
});
