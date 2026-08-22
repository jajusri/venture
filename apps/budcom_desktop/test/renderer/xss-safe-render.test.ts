import { describe, expect, it } from 'vitest';

import { HOSTILE_HTML_SAMPLES, renderCompanyList, renderLedgers, renderLogs } from '../../src/renderer/scripts/app.js';
import type { LedgerPageState } from '../../src/application/types.js';
import type { LogEntry } from '../../src/application/types.js';

describe('renderer XSS-safe rendering', () => {
  it('does not interpret hostile company names as HTML', () => {
    document.body.innerHTML = '<div id="company-list"></div><span id="company-list-status"></span>';
    for (const hostile of HOSTILE_HTML_SAMPLES) {
      renderCompanyList([{ id: 'hostile-id', name: hostile, status: 'available' }], '', 'ok');
      const container = document.getElementById('company-list');
      expect(container?.querySelector('script')).toBeNull();
      expect(container?.querySelector('img')).toBeNull();
      const nameSpan = container?.querySelector('.company-name');
      expect(nameSpan?.textContent).toBe(hostile);
      expect((window as typeof window & { __xss?: boolean }).__xss).toBeUndefined();
    }
  });

  it('renders hostile ledger names as plain text', () => {
    document.body.innerHTML = `
      <span id="ledger-stat-total"></span>
      <span id="ledger-stat-active"></span>
      <span id="ledger-stat-gst"></span>
      <span id="ledger-stat-last-sync"></span>
      <span id="ledger-sync-status"></span>
      <span id="ledger-sync-duration"></span>
      <span id="ledger-list-meta"></span>
      <div id="ledger-list"></div>
      <span id="ledger-page-label"></span>
    `;
    const hostile = HOSTILE_HTML_SAMPLES[0];
    const state: LedgerPageState = {
      ok: true,
      userMessage: null,
      list: {
        schemaVersion: '1.0.0',
        dataFreshnessAt: '2026-07-25T00:00:00.000Z',
        items: [{
          id: 'x',
          name: hostile,
          normalizedName: 'x',
          status: 'active',
          balanceNature: 'debit',
          syncedAt: '2026-07-25T00:00:00.000Z',
        }],
        pagination: { page: 1, pageSize: 25, totalItems: 1, totalPages: 1 },
      },
      statistics: {
        schemaVersion: '1.0.0',
        statistics: {
          totalLedgers: 1,
          activeLedgers: 1,
          inactiveLedgers: 0,
          reservedLedgers: 0,
          deletedLedgers: 0,
          withGst: 0,
          withOpeningBalance: 0,
          lastSyncedAt: '2026-07-25T00:00:00.000Z',
        },
      },
      progress: null,
      storage: null,
    };
    renderLedgers(state);
    const list = document.getElementById('ledger-list');
    expect(list?.querySelector('img')).toBeNull();
    expect(list?.textContent).toContain('<img');
  });

  it('renders hostile log messages as plain text', () => {
    document.body.innerHTML = '<div id="log-list"></div>';
    const hostile = HOSTILE_HTML_SAMPLES[1];
    const entries: LogEntry[] = [{
      timestamp: '2026-07-25T00:00:00.000Z',
      level: 'error',
      message: hostile,
    }];
    renderLogs(entries);
    const list = document.getElementById('log-list');
    expect(list?.querySelector('script')).toBeNull();
    expect(list?.textContent).toContain('script');
  });
});
