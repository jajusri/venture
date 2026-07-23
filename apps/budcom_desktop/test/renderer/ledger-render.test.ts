import { describe, expect, it } from 'vitest';

import { renderLedgers } from '../../src/renderer/scripts/app.js';
import type { LedgerPageState } from '../../src/application/types.js';

describe('renderLedgers', () => {
  it('renders ledger rows and statistics', () => {
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

    const state: LedgerPageState = {
      ok: true,
      userMessage: null,
      list: {
        schemaVersion: '1.0.0',
        dataFreshnessAt: '2026-07-23T00:00:00.000Z',
        items: [{ id: 'cash', name: 'Cash', normalizedName: 'cash', status: 'active', balanceNature: 'debit', syncedAt: '2026-07-23T00:00:00.000Z' }],
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
          lastSyncedAt: '2026-07-23T00:00:00.000Z',
        },
      },
      progress: {
        schemaVersion: '1.0.0',
        progress: {
          status: 'completed',
          startedAt: '2026-07-23T00:00:00.000Z',
          completedAt: '2026-07-23T00:00:00.000Z',
          durationMs: 12,
          itemsProcessed: 1,
          itemsAdded: 1,
          itemsUpdated: 0,
          itemsSkipped: 0,
          itemsFailed: 0,
          lastError: null,
          cancelRequested: false,
        },
      },
    };

    renderLedgers(state);
    expect(document.getElementById('ledger-stat-total')?.textContent).toBe('1');
    expect(document.getElementById('ledger-list')?.textContent).toContain('Cash');
  });
});
