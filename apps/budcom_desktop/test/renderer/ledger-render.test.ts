import { beforeEach, describe, expect, it, vi } from 'vitest';

import { loadLedgers, renderLedgers } from '../../src/renderer/scripts/app.js';
import type { LedgerPageState } from '../../src/application/types.js';

const LEDGER_MARKUP = `
  <span id="ledger-stat-total"></span>
  <span id="ledger-stat-active"></span>
  <span id="ledger-stat-gst"></span>
  <span id="ledger-stat-last-sync"></span>
  <span id="ledger-sync-status"></span>
  <span id="ledger-sync-duration"></span>
  <span id="ledger-storage-status"></span>
  <span id="ledger-migration-status"></span>
  <span id="ledger-list-meta"></span>
  <div id="ledger-list"></div>
  <span id="ledger-page-label"></span>
  <button id="btn-sync-ledgers"></button>
  <button id="btn-cancel-ledger-sync" class="hidden"></button>
  <div id="app-notification" class="app-notification hidden"></div>
  <div id="global-loading" class="loading-bar hidden"><span id="loading-message"></span></div>
`;

function ledgerStats(overrides: { totalLedgers?: number; lastSyncedAt?: string | null } = {}) {
  return {
    schemaVersion: '1.0.0',
    statistics: {
      totalLedgers: overrides.totalLedgers ?? 1,
      activeLedgers: overrides.totalLedgers ?? 1,
      inactiveLedgers: 0,
      reservedLedgers: 0,
      deletedLedgers: 0,
      withGst: 0,
      withOpeningBalance: 0,
      lastSyncedAt: overrides.lastSyncedAt === undefined ? '2026-07-23T00:00:00.000Z' : overrides.lastSyncedAt,
    },
  };
}

function okState(overrides: Partial<LedgerPageState> = {}): LedgerPageState {
  return {
    ok: true,
    userMessage: null,
    list: {
      schemaVersion: '1.0.0',
      dataFreshnessAt: '2026-07-23T00:00:00.000Z',
      items: [{ id: 'cash', name: 'Cash', normalizedName: 'cash', status: 'active', balanceNature: 'debit', syncedAt: '2026-07-23T00:00:00.000Z' }],
      pagination: { page: 1, pageSize: 25, totalItems: 1, totalPages: 1 },
    },
    statistics: ledgerStats(),
    progress: null,
    storage: null,
    ...overrides,
  };
}

const failState = (userMessage: string): LedgerPageState => ({
  ok: false,
  userMessage,
  list: null,
  statistics: null,
  progress: null,
  storage: null,
});

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

  beforeEach(() => {
    document.body.innerHTML = LEDGER_MARKUP;
  });

  it('explains a genuinely empty, never-synced result rather than showing a bare blank list', () => {
    renderLedgers(okState({
      list: { schemaVersion: '1.0.0', dataFreshnessAt: '2026-07-23T00:00:00.000Z', items: [], pagination: { page: 1, pageSize: 25, totalItems: 0, totalPages: 0 } },
      statistics: ledgerStats({ totalLedgers: 0, lastSyncedAt: null }),
    }));
    const empty = document.querySelector('#ledger-list .empty-state');
    expect(empty?.textContent).toContain('No ledgers synced yet');
    expect(empty?.textContent).toContain('Sync Now');
  });

  it('distinguishes an already-synced-but-genuinely-empty company from a never-synced one', () => {
    renderLedgers(okState({
      list: { schemaVersion: '1.0.0', dataFreshnessAt: '2026-07-23T00:00:00.000Z', items: [], pagination: { page: 1, pageSize: 25, totalItems: 0, totalPages: 0 } },
      // lastSyncedAt IS set — a real sync ran and genuinely found zero ledgers — so the message
      // must not claim "never synced" (that would be false) or suggest clicking Sync Now again.
      statistics: ledgerStats({ totalLedgers: 0, lastSyncedAt: '2026-07-23T00:00:00.000Z' }),
    }));
    const empty = document.querySelector('#ledger-list .empty-state');
    expect(empty?.textContent).toBe('No ledgers found for this company.');
    expect(empty?.textContent).not.toContain('never synced');
  });

  it('preserves the previously-rendered list and stats when a later refresh fails, and explains the failure', () => {
    renderLedgers(okState());
    expect(document.getElementById('ledger-list')?.textContent).toContain('Cash');
    expect(document.getElementById('ledger-stat-total')?.textContent).toBe('1');

    renderLedgers(failState('The connector did not respond in time.'));

    expect(document.getElementById('ledger-list')?.textContent).toContain('Cash');
    expect(document.getElementById('ledger-stat-total')?.textContent).toBe('1');
    expect(document.getElementById('ledger-list-meta')?.textContent).toContain('The connector did not respond in time.');
    expect(document.getElementById('ledger-list-meta')?.textContent).toContain('Showing previously loaded data.');
  });

});

describe('loadLedgers', () => {
  beforeEach(() => {
    document.body.innerHTML = LEDGER_MARKUP;
  });

  it('never lets an older, slower response overwrite a newer, already-rendered one', async () => {
    let resolveOlder: ((value: LedgerPageState) => void) | null = null;
    const getLedgers = vi.fn()
      .mockImplementationOnce(() => new Promise<LedgerPageState>((resolve) => { resolveOlder = resolve; }))
      .mockResolvedValueOnce(okState());
    window.budcomDesktop = { getLedgers } as unknown as typeof window.budcomDesktop;

    const olderCall = loadLedgers(); // starts first, stays pending
    await loadLedgers(); // starts later, resolves immediately — the "newer" call
    expect(document.getElementById('ledger-list')?.textContent).toContain('Cash');

    // The older call finally resolves with a DIFFERENT (stale) result — must not overwrite.
    resolveOlder?.(okState({
      list: { schemaVersion: '1.0.0', dataFreshnessAt: '2026-07-23T00:00:00.000Z', items: [{ id: 'stale', name: 'StaleLedger', normalizedName: 'stale', status: 'active', balanceNature: 'debit', syncedAt: '2026-07-23T00:00:00.000Z' }], pagination: { page: 1, pageSize: 25, totalItems: 1, totalPages: 1 } },
    }));
    await olderCall;

    expect(document.getElementById('ledger-list')?.textContent).toContain('Cash');
    expect(document.getElementById('ledger-list')?.textContent).not.toContain('StaleLedger');
  });
});
