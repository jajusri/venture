/**
 * @vitest-environment jsdom
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { handleCompanySelection, loadCompanies } from '../../src/renderer/scripts/app.js';
import { lifecycleStatusFixture, settingsFixture } from '../helpers/lifecycle-fixtures.js';

describe('renderer company selection', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="global-banner" class="banner hidden"></div>
      <div id="global-loading" class="loading-bar hidden"><span id="loading-message"></span></div>
      <div id="company-list"></div>
      <div id="company-list-status"></div>
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
    expect(document.getElementById('global-banner')?.textContent).toBe('That company was not found.');
  });
});
