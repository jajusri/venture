/**
 * @vitest-environment jsdom
 */
import fs from 'node:fs';
import path from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  bindLifecycleActions,
  renderDashboard,
  renderDiagnostics,
  renderLifecycle,
  setLoading,
} from '../../src/renderer/scripts/app.js';
import type { DashboardState } from '../../src/application/types.js';
import {
  diagnosticsFixture,
  lifecycleStatusFixture,
} from '../helpers/lifecycle-fixtures.js';

const connectedDashboard: DashboardState = {
  windowTitle: 'Business OS Tally Connector',
  connectorVersion: '0.3.1',
  apiVersion: '1.0.0',
  desktopVersion: '0.4.3',
  erpType: 'tally',
  erpName: 'Tally',
  connectionIndicator: 'connected',
  connectionLabel: 'Connected',
  companyName: 'Venture-Test-01',
  companyId: 'venture-test-01',
  selectionTime: '2026-07-26T00:00:00.000Z',
  sessionStatus: 'ACTIVE',
  syncStatus: 'idle',
  syncLabel: 'Idle',
  lastSync: '—',
  lastRefresh: '2026-07-26T00:00:00.000Z',
  licenseStatus: 'Active',
  connectorReachable: true,
  healthStatus: 'ok',
  userMessage: null,
};

const statusSurfaceIds = [
  'header-connection-label',
  'footer-connection-status',
  'dashboard-connection',
  'connection-detail-label',
  'diag-connector-status',
] as const;

function expectStatusOnEverySurface(label: string): void {
  for (const id of statusSurfaceIds) {
    expect(document.getElementById(id)?.textContent).toBe(label);
  }
}

function loadRendererMarkup(): void {
  const html = fs.readFileSync(
    path.resolve(process.cwd(), 'src/renderer/index.html'),
    'utf8',
  );
  const parsed = new DOMParser().parseFromString(html, 'text/html');
  document.body.innerHTML = parsed.body.innerHTML;
}

describe('advanced user interface sections', () => {
  beforeEach(loadRendererMarkup);

  it('keeps advanced diagnostics collapsed and technical values out of the default view', () => {
    const advanced = document.getElementById('advanced-technical-details') as HTMLDetailsElement;

    expect(advanced.open).toBe(false);
    expect(advanced.querySelector('#diag-runtime-versions')).toBeTruthy();
    expect(advanced.querySelector('#diag-connector-url')).toBeTruthy();
    expect(advanced.querySelector('#diag-pid')).toBeTruthy();
    expect(document.querySelector('#view-diagnostics > .panel > .detail-list #diag-pid')).toBeNull();

    advanced.open = true;
    expect(advanced.open).toBe(true);
    expect(advanced.querySelector('#diag-runtime-versions')).toBeTruthy();
  });

  it('keeps only auto-start visible before advanced connector settings', () => {
    const advanced = document.getElementById('advanced-connector-settings') as HTMLDetailsElement;
    const autoStart = document.getElementById('input-auto-start');

    expect(advanced.open).toBe(false);
    expect(autoStart?.closest('details')).toBeNull();
    expect(document.getElementById('input-log-level')?.closest('details')).toBe(advanced);
    expect(document.getElementById('input-connector-host')?.closest('details')).toBe(advanced);
    expect(advanced.textContent).toContain('Change these settings only when instructed by support.');

    advanced.open = true;
    expect(advanced.open).toBe(true);
  });

  it('uses one display mapping for header, footer, dashboard, and diagnostics', () => {
    renderDashboard(connectedDashboard);
    renderLifecycle(lifecycleStatusFixture);
    renderDiagnostics(diagnosticsFixture);

    expectStatusOnEverySurface('Connected');
    expect(document.getElementById('diag-overall')?.textContent).toBe('Working normally');
    expect(document.getElementById('diag-selected-company')?.textContent).toBe('Venture-Test-01');
  });

  it('does not let a stale connected diagnostics snapshot contradict disconnected lifecycle state', () => {
    renderDashboard(connectedDashboard);
    renderDiagnostics(diagnosticsFixture);
    renderLifecycle({
      ...lifecycleStatusFixture,
      state: 'disconnected',
      stateLabel: 'Disconnected',
    });

    expectStatusOnEverySurface('Not connected');
    expect(document.getElementById('diag-overall')?.textContent).toBe('Not connected');
  });

  it('treats a healthy connector without a company as ready, not as an error', () => {
    renderLifecycle(lifecycleStatusFixture);
    renderDashboard({
      ...connectedDashboard,
      companyName: '—',
      companyId: '—',
      sessionStatus: 'NONE',
    });
    renderDiagnostics({
      ...diagnosticsFixture,
      selectedCompanyPresent: false,
      sessionStatus: 'NONE',
      sessionDisplayLabel: 'NONE',
    });

    expectStatusOnEverySurface('Connected');
    expect(document.getElementById('diag-overall')?.textContent).toBe('Ready — select a company');
    expect(document.getElementById('dashboard-company-name')?.textContent).toBe('No company selected');
    expect(document.getElementById('footer-connection-indicator')?.className).toContain('status-connected');
    expect(document.getElementById('footer-connection-indicator')?.className).not.toContain('status-disconnected');
    for (const id of ['btn-sync-ledgers', 'btn-sync-stock-items']) {
      const button = document.getElementById(id) as HTMLButtonElement;
      expect(button.disabled).toBe(true);
      expect(button.title).toBe('Select a company before synchronizing.');
    }
  });

  it('shows needs attention for reachable but degraded health', () => {
    renderLifecycle(lifecycleStatusFixture);
    renderDashboard({ ...connectedDashboard, healthStatus: 'degraded' });
    renderDiagnostics({ ...diagnosticsFixture, healthStatus: 'degraded' });

    expectStatusOnEverySurface('Connected');
    expect(document.getElementById('diag-overall')?.textContent).toBe('Needs attention');
  });

  it.each([
    ['starting', 'Starting connector…'],
    ['reconnecting', 'Reconnecting…'],
  ] as const)('uses user-facing wording for %s lifecycle state', (state, label) => {
    renderDashboard(connectedDashboard);
    renderDiagnostics(diagnosticsFixture);
    renderLifecycle({ ...lifecycleStatusFixture, state, stateLabel: state });

    expectStatusOnEverySurface(label);
    expect(document.getElementById('diag-overall')?.textContent).toBe('Connecting…');
  });

  it('keeps manual refresh feedback in the footer only', () => {
    renderDashboard(connectedDashboard);
    renderDiagnostics(diagnosticsFixture);
    renderLifecycle(lifecycleStatusFixture);

    setLoading({ dashboard: true }, 'Refreshing…');

    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Refreshing…');
    for (const id of statusSurfaceIds.filter((id) => id !== 'footer-connection-status')) {
      expect(document.getElementById(id)?.textContent).toBe('Connected');
    }
    expect(document.getElementById('diag-overall')?.textContent).toBe('Working normally');
  });

  it.each([
    ['Evaluation', 'Licence: Evaluating…'],
    ['Active', 'Licence: Active'],
    ['Expired', 'Licence: Expired'],
    ['Invalid', 'Licence: Needs attention'],
    ['Error', 'Licence: Needs attention'],
    ['Unknown', 'Licence: Checking…'],
  ])('maps licence status %s to %s', (licenseStatus, expected) => {
    renderDashboard({ ...connectedDashboard, licenseStatus });
    expect(document.getElementById('footer-license')?.textContent).toBe(expected);
  });

  it('shows visible, sanitized feedback when a lifecycle action is rejected', async () => {
    window.ventureDesktop = {
      startConnector: vi.fn(async () => {
        throw new Error('boom: raw internal failure detail');
      }),
    } as unknown as typeof window.ventureDesktop;
    bindLifecycleActions();

    document.getElementById('btn-start-connector')?.dispatchEvent(new Event('click', { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));

    const failureMessage = 'Connector action failed. Check the Logs view for details.';
    expect(document.getElementById('lifecycle-status-message')?.textContent).toBe(failureMessage);
    expect(document.getElementById('lifecycle-status-message')?.className).toContain('form-error');
    const notification = document.getElementById('app-notification');
    expect(notification?.textContent).toContain(failureMessage);
    expect(notification?.className).toContain('app-notification-error');
    expect(notification?.textContent).not.toContain('boom');
  });

  it('preserves scroll position and focus while advanced diagnostics expands and updates', () => {
    const main = document.querySelector('main')!;
    const advanced = document.getElementById('advanced-technical-details') as HTMLDetailsElement;
    const summary = advanced.querySelector('summary') as HTMLElement;
    main.scrollTop = 320;
    summary.focus();

    advanced.open = true;
    renderDiagnostics(diagnosticsFixture);

    expect(main.scrollTop).toBe(320);
    expect(document.activeElement).toBe(summary);
    expect(advanced.isConnected).toBe(true);
  });
});

// UX polish: a critical state must never be communicated through color alone. The header
// connection-indicator dot's className changes with connection state but it carries no updating
// text signal of its own — the adjacent header-connection-label span (real text, already read by
// assistive tech) is the actual semantic source of truth, matching the footer's own
// footer-connection-indicator/footer-connection-status pair. The dot must therefore be
// aria-hidden, not carry a stale/static aria-label that never reflects the live state.
describe('connection-indicator accessibility', () => {
  beforeEach(loadRendererMarkup);

  it('hides the decorative header connection dot from assistive tech instead of exposing a static, non-updating label', () => {
    const dot = document.getElementById('connection-indicator');
    expect(dot?.getAttribute('aria-hidden')).toBe('true');
    expect(dot?.hasAttribute('aria-label')).toBe(false);
  });

  it('marks the footer connection dot the same way, consistent with the header', () => {
    const dot = document.getElementById('footer-connection-indicator');
    expect(dot?.getAttribute('aria-hidden')).toBe('true');
  });
});
