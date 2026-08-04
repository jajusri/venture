/**
 * @vitest-environment jsdom
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { handleClearCompany, handleCompanySelection, loadCompanies } from '../../src/renderer/scripts/app.js';
import { lifecycleStatusFixture, settingsFixture } from '../helpers/lifecycle-fixtures.js';

describe('renderer company selection', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <span id="footer-connection-indicator" class="status-dot status-unknown"></span>
      <strong id="footer-connection-status"></strong>
      <span id="footer-company"></span>
      <strong id="footer-license"></strong>
      <div id="company-list"></div>
      <div id="company-list-status"></div>
      <div id="app-notification" class="app-notification hidden"></div>
      <button id="btn-clear-company"></button>
    `;
  });

  it('loads companies into the list', async () => {
    window.budcomDesktop = {
      getCompanies: vi.fn(async () => ({
        items: [{ id: 'estimation', name: 'ESTIMATION' }],
        status: 'SUCCESS',
      })),
      getDashboardState: vi.fn(async () => ({
        companyId: 'estimation',
      })),
      getLogs: vi.fn(),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
      startConnector: vi.fn(),
      stopConnector: vi.fn(),
      restartConnector: vi.fn(),
      selectCompany: vi.fn(),
      clearCompany: vi.fn(),
      onStatusUpdated: vi.fn(),
    } as unknown as typeof window.budcomDesktop;

    await loadCompanies();
    expect(document.querySelector('[data-company-id="estimation"]')).toBeTruthy();
  });

  it('shows selection errors from connector outcome', async () => {
    window.budcomDesktop = {
      selectCompany: vi.fn(async () => ({
        ok: false,
        userMessage: 'That company was not found.',
      })),
      getDashboardState: vi.fn(async () => ({
        windowTitle: 'Business OS Tally Connector',
        connectorVersion: '0.3.1',
        apiVersion: '1.0.0',
        desktopVersion: '0.4.2',
        erpType: 'tally',
        erpName: 'Tally',
        connectionIndicator: 'connected',
        connectionLabel: 'Connected',
        companyName: '—',
        companyId: '—',
        selectionTime: 'Not selected',
        sessionStatus: 'NO_COMPANY_SELECTED',
        syncStatus: 'idle',
        syncLabel: 'Idle',
        lastSync: '—',
        lastRefresh: '—',
        licenseStatus: 'Unknown',
        connectorReachable: true,
        healthStatus: 'ok',
        userMessage: null,
      })),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
      startConnector: vi.fn(),
      stopConnector: vi.fn(),
      restartConnector: vi.fn(),
      getCompanies: vi.fn(async () => ({ items: [], status: 'EMPTY' })),
      clearCompany: vi.fn(),
      onStatusUpdated: vi.fn(),
    } as unknown as typeof window.budcomDesktop;

    await handleCompanySelection('missing');
    expect(document.getElementById('company-list-status')?.textContent)
      .toBe('That company was not found.');
  });

  it('confirms selection immediately and leaves dependent refresh to the silent status notification', async () => {
    document.getElementById('company-list')!.innerHTML = `
      <button data-company-id="estimation" role="radio" aria-checked="false">ESTIMATION</button>
    `;
    const bridge = {
      selectCompany: vi.fn(async () => ({
        ok: true,
        userMessage: 'Company selected successfully.',
      })),
      getDashboardState: vi.fn(),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
      getCompanies: vi.fn(),
    };
    window.budcomDesktop = bridge as unknown as typeof window.budcomDesktop;

    await handleCompanySelection('estimation');

    expect(document.getElementById('company-list-status')?.textContent)
      .toBe('Company selected successfully.');
    expect(document.querySelector('[data-company-id="estimation"]')?.getAttribute('aria-checked'))
      .toBe('true');
    expect(bridge.getCompanies).not.toHaveBeenCalled();
    expect(bridge.getDashboardState).not.toHaveBeenCalled();
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Connected');
    expect(document.getElementById('footer-company')?.textContent).toBe('· ESTIMATION');
  });

  it('disables duplicate selection clicks while the authoritative request is active', async () => {
    document.getElementById('company-list')!.innerHTML = `
      <button data-company-id="estimation" role="radio" aria-checked="false">ESTIMATION</button>
    `;
    let resolveSelection: ((value: { ok: boolean; userMessage: string }) => void) | null = null;
    const selectCompany = vi.fn(
      () => new Promise<{ ok: boolean; userMessage: string }>((resolve) => {
        resolveSelection = resolve;
      }),
    );
    window.budcomDesktop = {
      selectCompany,
      getDashboardState: vi.fn(async () => ({})),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
    } as unknown as typeof window.budcomDesktop;

    const first = handleCompanySelection('estimation');
    const second = handleCompanySelection('estimation');
    expect(selectCompany).toHaveBeenCalledTimes(1);
    expect((document.querySelector('[data-company-id]') as HTMLButtonElement).disabled).toBe(true);
    expect(document.getElementById('company-list-status')?.textContent).toBe('Selecting company…');
    expect(document.getElementById('footer-connection-status')?.textContent).toBe('Selecting company…');

    resolveSelection?.({ ok: false, userMessage: 'Selection rejected.' });
    await Promise.all([first, second]);
    expect((document.querySelector('[data-company-id]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows a visible confirmation after clearing the company selection succeeds', async () => {
    window.budcomDesktop = {
      clearCompany: vi.fn(async () => ({ ok: true })),
      getDashboardState: vi.fn(async () => ({})),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
      getCompanies: vi.fn(async () => ({ items: [], status: 'EMPTY' })),
    } as unknown as typeof window.budcomDesktop;

    await handleClearCompany();

    expect(document.getElementById('company-list-status')?.textContent)
      .toBe('Company selection cleared.');
    const notification = document.getElementById('app-notification');
    expect(notification?.textContent).toContain('Company selection cleared.');
    expect(notification?.className).not.toContain('hidden');
  });

  it('shows a visible error when clearing the company selection fails', async () => {
    window.budcomDesktop = {
      clearCompany: vi.fn(async () => {
        throw new Error('connector unreachable');
      }),
      getDashboardState: vi.fn(async () => ({})),
      getLogs: vi.fn(async () => []),
      getSettings: vi.fn(async () => settingsFixture),
      getLifecycleStatus: vi.fn(async () => lifecycleStatusFixture),
      getCompanies: vi.fn(async () => ({ items: [], status: 'EMPTY' })),
    } as unknown as typeof window.budcomDesktop;

    await handleClearCompany();

    expect(document.getElementById('company-list-status')?.textContent)
      .toBe('Unable to clear company selection. Please try again.');
    const notification = document.getElementById('app-notification');
    expect(notification?.textContent).toContain('Unable to clear company selection.');
    expect(notification?.className).toContain('app-notification-error');
  });
});
