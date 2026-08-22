import { beforeEach, describe, expect, it, vi } from 'vitest';

import { loadStockItems, renderStockItems } from '../../src/renderer/scripts/app.js';
import type { StockItemPageState } from '../../src/application/types.js';

const STOCK_ITEM_MARKUP = `
  <span id="stock-item-stat-total"></span>
  <span id="stock-item-stat-unit"></span>
  <span id="stock-item-stat-incomplete"></span>
  <span id="stock-item-stat-last-sync"></span>
  <span id="stock-item-sync-status"></span>
  <span id="stock-item-sync-duration"></span>
  <span id="stock-item-storage-status"></span>
  <span id="stock-item-migration-status"></span>
  <button id="btn-sync-stock-items"></button>
  <button id="btn-cancel-stock-item-sync" class="hidden"></button>
  <span id="stock-item-list-meta"></span>
  <div id="stock-item-list"></div>
  <span id="stock-item-page-label"></span>
  <div id="app-notification" class="app-notification hidden"></div>
  <div id="global-loading" class="loading-bar hidden"><span id="loading-message"></span></div>
`;

function okState(overrides: Partial<StockItemPageState> = {}): StockItemPageState {
  return {
    ok: true,
    userMessage: null,
    list: {
      schemaVersion: '1.0.0',
      dataFreshnessAt: '2026-01-01T00:00:00.000Z',
      items: [{ id: 'name:widget', name: 'Widget', normalizedName: 'widget', dataQuality: 'complete', status: 'active', syncedAt: '2026-01-01T00:00:00.000Z' }],
      pagination: { page: 1, pageSize: 25, totalItems: 1, totalPages: 1 },
    },
    statistics: {
      schemaVersion: '1.0.0',
      statistics: { totalStockItems: 1, withBaseUnit: 1, incompleteData: 0, withHsn: 0, withGst: 0, withOpeningBalance: 0, deletedStockItems: 0, lastSyncedAt: '2026-01-01T00:00:00.000Z' },
    },
    progress: null,
    storage: null,
    ...overrides,
  };
}

const failState = (userMessage: string): StockItemPageState => ({
  ok: false,
  userMessage,
  list: null,
  statistics: null,
  progress: null,
  storage: null,
});

describe('renderStockItems', () => {
  it('renders incomplete-data statistics and list rows', () => {
    document.body.innerHTML = `
      <span id="stock-item-stat-total"></span>
      <span id="stock-item-stat-unit"></span>
      <span id="stock-item-stat-incomplete"></span>
      <span id="stock-item-stat-last-sync"></span>
      <span id="stock-item-sync-status"></span>
      <span id="stock-item-sync-duration"></span>
      <span id="stock-item-storage-status"></span>
      <span id="stock-item-migration-status"></span>
      <button id="btn-sync-stock-items"></button>
      <button id="btn-cancel-stock-item-sync" class="hidden"></button>
      <span id="stock-item-list-meta"></span>
      <div id="stock-item-list"></div>
      <span id="stock-item-page-label"></span>
    `;

    renderStockItems({
      ok: true,
      list: {
        schemaVersion: '1.0.0',
        dataFreshnessAt: '2026-01-01T00:00:00.000Z',
        items: [
          {
            id: 'name:widget',
            name: 'Widget',
            normalizedName: 'widget',
            parentGroup: 'Finished Goods',
            baseUnit: undefined,
            dataQuality: 'incomplete',
            status: 'active',
            syncedAt: '2026-01-01T00:00:00.000Z',
          },
        ],
        pagination: { page: 1, pageSize: 25, totalItems: 1, totalPages: 1 },
      },
      statistics: {
        schemaVersion: '1.0.0',
        statistics: {
          totalStockItems: 1,
          withBaseUnit: 0,
          incompleteData: 1,
          withHsn: 0,
          withGst: 0,
          withOpeningBalance: 0,
          deletedStockItems: 0,
          lastSyncedAt: '2026-01-01T00:00:00.000Z',
        },
      },
      progress: {
        schemaVersion: '1.0.0',
        progress: {
          status: 'completed',
          durationMs: 100,
          itemsProcessed: 1,
          itemsAdded: 1,
          itemsUpdated: 0,
          itemsSkipped: 0,
          itemsFailed: 0,
          startedAt: null,
          completedAt: null,
          lastError: null,
          cancelRequested: false,
        },
      },
      storage: {
        backend: 'sqlite',
        schemaVersion: 2,
        databaseHealthy: true,
        migrationStatus: 'completed',
        message: null,
      },
      userMessage: null,
    });

    expect(document.getElementById('stock-item-stat-incomplete')?.textContent).toBe('1');
    expect(document.getElementById('stock-item-list')?.textContent).toContain('Widget');
    expect(document.getElementById('stock-item-list')?.textContent).toContain('incomplete');
  });

  beforeEach(() => {
    document.body.innerHTML = STOCK_ITEM_MARKUP;
  });

  it('explains a genuinely empty, never-synced result rather than showing a bare blank list', () => {
    renderStockItems(okState({
      list: { schemaVersion: '1.0.0', dataFreshnessAt: '2026-01-01T00:00:00.000Z', items: [], pagination: { page: 1, pageSize: 25, totalItems: 0, totalPages: 0 } },
      statistics: { schemaVersion: '1.0.0', statistics: { totalStockItems: 0, withBaseUnit: 0, incompleteData: 0, withHsn: 0, withGst: 0, withOpeningBalance: 0, deletedStockItems: 0, lastSyncedAt: null } },
    }));
    const empty = document.querySelector('#stock-item-list .empty-state');
    expect(empty?.textContent).toContain('No stock items synced yet');
    expect(empty?.textContent).toContain('Sync Now');
  });

  it('preserves the previously-rendered list and stats when a later refresh fails, and explains the failure', () => {
    renderStockItems(okState());
    expect(document.getElementById('stock-item-list')?.textContent).toContain('Widget');
    expect(document.getElementById('stock-item-stat-total')?.textContent).toBe('1');

    renderStockItems(failState('The connector did not respond in time.'));

    expect(document.getElementById('stock-item-list')?.textContent).toContain('Widget');
    expect(document.getElementById('stock-item-stat-total')?.textContent).toBe('1');
    expect(document.getElementById('stock-item-list-meta')?.textContent).toContain('The connector did not respond in time.');
    expect(document.getElementById('stock-item-list-meta')?.textContent).toContain('Showing previously loaded data.');
  });
});

describe('loadStockItems', () => {
  beforeEach(() => {
    document.body.innerHTML = STOCK_ITEM_MARKUP;
  });

  it('never lets an older, slower response overwrite a newer, already-rendered one', async () => {
    let resolveOlder: ((value: StockItemPageState) => void) | null = null;
    const getStockItems = vi.fn()
      .mockImplementationOnce(() => new Promise<StockItemPageState>((resolve) => { resolveOlder = resolve; }))
      .mockResolvedValueOnce(okState());
    window.budcomDesktop = { getStockItems } as unknown as typeof window.budcomDesktop;

    const olderCall = loadStockItems();
    await loadStockItems();
    expect(document.getElementById('stock-item-list')?.textContent).toContain('Widget');

    resolveOlder?.(okState({
      list: { schemaVersion: '1.0.0', dataFreshnessAt: '2026-01-01T00:00:00.000Z', items: [{ id: 'stale', name: 'StaleItem', normalizedName: 'stale', dataQuality: 'complete', status: 'active', syncedAt: '2026-01-01T00:00:00.000Z' }], pagination: { page: 1, pageSize: 25, totalItems: 1, totalPages: 1 } },
    }));
    await olderCall;

    expect(document.getElementById('stock-item-list')?.textContent).toContain('Widget');
    expect(document.getElementById('stock-item-list')?.textContent).not.toContain('StaleItem');
  });
});
