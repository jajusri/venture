/**
 * @vitest-environment jsdom
 */
import { beforeEach, describe, expect, it } from 'vitest';

import {
  activateView,
  renderDashboard,
  type DesktopView,
} from '../../src/renderer/scripts/app.js';
import type { DashboardState } from '../../src/application/types.js';

const sampleState: DashboardState = {
  windowTitle: 'Business OS Tally Connector',
  connectorVersion: '0.3.1',
  erpType: 'tally',
  connectionIndicator: 'connected',
  connectionLabel: 'Connected',
  companyName: 'ESTIMATION',
  companyId: 'estimation',
  selectionTime: '2026-07-22T17:00:00.000Z',
  sessionStatus: 'SUCCESS',
  syncStatus: 'idle',
  syncLabel: 'Idle',
  lastSync: '2026-07-22T17:05:00.000Z',
  licenseStatus: 'Evaluation',
  connectorReachable: true,
  healthStatus: 'ok',
};

describe('renderer dashboard', () => {
  beforeEach(() => {
    document.body.innerHTML = `
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
      <div id="dashboard-sync"></div>
      <div id="dashboard-last-sync"></div>
      <div id="dashboard-version"></div>
      <div id="dashboard-erp"></div>
      <div id="connection-detail-indicator"></div>
      <div id="connection-detail-label"></div>
      <div id="connection-detail-reachable"></div>
      <div id="connection-detail-health"></div>
      <div id="footer-version"></div>
      <div id="footer-erp"></div>
      <div id="footer-license"></div>
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
    expect(document.getElementById('connection-indicator')?.className).toContain('indicator-connected');
  });

  it('activates navigation views', () => {
    activateView('logs' as DesktopView);
    expect(document.getElementById('view-logs')?.classList.contains('active')).toBe(true);
    expect(document.getElementById('view-dashboard')?.classList.contains('active')).toBe(false);
  });
});
