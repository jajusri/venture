import { describe, expect, it } from 'vitest';

import { renderStockItems } from '../../src/renderer/scripts/app.js';

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
});
