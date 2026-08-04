import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  LedgerPageState,
  LedgerSyncProgressDto,
  StockItemPageState,
} from '../../src/application/types.js';
import {
  activateView,
  bindLedgerActions,
  bindStockItemActions,
  disposeSyncProgressPolling,
  renderModuleSyncProgress,
  startLedgerProgressPolling,
  startStockItemProgressPolling,
} from '../../src/renderer/scripts/app.js';

function progress(overrides: Partial<LedgerSyncProgressDto> = {}): LedgerSyncProgressDto {
  return {
    status: 'running',
    startedAt: '2026-07-26T00:00:00.000Z',
    completedAt: null,
    durationMs: 2_500,
    itemsProcessed: 25,
    itemsAdded: 10,
    itemsUpdated: 8,
    itemsSkipped: 7,
    itemsFailed: 0,
    lastError: null,
    cancelRequested: false,
    ...overrides,
  };
}

function pageState(
  kind: 'ledger' | 'stock-item',
  syncProgress: LedgerSyncProgressDto,
): LedgerPageState | StockItemPageState {
  return {
    ok: true,
    list: null,
    statistics: null,
    progress: { schemaVersion: '1.0.0', progress: syncProgress },
    storage: null,
    userMessage: null,
  } as LedgerPageState | StockItemPageState;
}

function progressMarkup(module: 'ledger' | 'stock-item'): string {
  return `
    <div id="${module}-progress">
      <strong id="${module}-progress-message"></strong>
      <span id="${module}-progress-percentage"></span>
      <div role="progressbar" aria-valuenow="0"><div id="${module}-progress-bar"></div></div>
      <span id="${module}-progress-count"></span>
      <span id="${module}-progress-added"></span>
      <span id="${module}-progress-updated"></span>
      <span id="${module}-progress-skipped"></span>
      <span id="${module}-progress-failed"></span>
      <span id="${module}-progress-duration"></span>
      <span id="${module}-progress-last-sync"></span>
      <p id="${module}-progress-error" class="hidden"></p>
    </div>
  `;
}

describe('module synchronization progress rendering', () => {
  beforeEach(() => {
    document.body.innerHTML = `${progressMarkup('ledger')}${progressMarkup('stock-item')}`;
  });

  it.each([
    ['missing', undefined],
    ['null', null],
  ])('shows preparing state when totalExpected is %s', (_label, totalExpected) => {
    renderModuleSyncProgress('ledger', progress({ totalExpected }), null);

    expect(document.getElementById('ledger-progress-message')?.textContent)
      .toBe('Preparing synchronization…');
    expect(document.getElementById('ledger-progress-count')?.textContent).toBe('Processed: 25');
    expect(document.getElementById('ledger-progress-percentage')?.textContent).toBe('—');
  });

  it('renders genuine processed/total counts, percentage, counters, and duration', () => {
    renderModuleSyncProgress('ledger', progress({ totalExpected: 100 }), '2026-07-26T00:01:00.000Z');

    expect(document.getElementById('ledger-progress-count')?.textContent).toBe('25 / 100');
    expect(document.getElementById('ledger-progress-percentage')?.textContent).toBe('25%');
    expect(document.getElementById('ledger-progress-bar')?.style.width).toBe('25%');
    expect(document.getElementById('ledger-progress-added')?.textContent).toBe('10');
    expect(document.getElementById('ledger-progress-duration')?.textContent).toBe('2.5 s');
  });

  it('handles zero totals and caps defensive inconsistencies at 100%', () => {
    renderModuleSyncProgress('stock-item', progress({ totalExpected: 0, itemsProcessed: 0 }), null);
    expect(document.getElementById('stock-item-progress-percentage')?.textContent).toBe('0%');

    renderModuleSyncProgress('stock-item', progress({ totalExpected: 10, itemsProcessed: 12 }), null);
    expect(document.getElementById('stock-item-progress-count')?.textContent).toBe('12 / 10');
    expect(document.getElementById('stock-item-progress-percentage')?.textContent).toBe('100%');

    renderModuleSyncProgress(
      'stock-item',
      progress({ status: 'completed', totalExpected: 0, itemsProcessed: 0 }),
      null,
    );
    expect(document.getElementById('stock-item-progress-percentage')?.textContent).toBe('100%');
  });

  it('shows only the sanitized returned failure message', () => {
    renderModuleSyncProgress(
      'ledger',
      progress({ status: 'failed', totalExpected: 100, lastError: 'Sanitized connector failure.' }),
      null,
    );
    expect(document.getElementById('ledger-progress-message')?.textContent).toBe('Failed');
    expect(document.getElementById('ledger-progress-error')?.textContent)
      .toBe('Sanitized connector failure.');
  });
});

describe('module synchronization progress polling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    document.body.innerHTML = `
      <div id="global-loading" class="loading-bar hidden"><span id="loading-message"></span></div>
      <button class="nav-btn" data-view="dashboard"></button>
      <button class="nav-btn" data-view="ledgers"></button>
      <button class="nav-btn" data-view="stock-items"></button>
      <section id="view-dashboard" class="view active"></section>
      <section id="view-ledgers" class="view">${progressMarkup('ledger')}</section>
      <section id="view-stock-items" class="view">${progressMarkup('stock-item')}</section>
    `;
  });

  afterEach(() => {
    disposeSyncProgressPolling();
    activateView('dashboard');
    vi.useRealTimers();
  });

  it('starts once, prevents overlapping ledger requests, and stops on completion', async () => {
    let resolveLedger: ((state: LedgerPageState) => void) | null = null;
    const getLedgers = vi.fn(
      () => new Promise<LedgerPageState>((resolve) => {
        resolveLedger = resolve;
      }),
    );
    window.budcomDesktop = {
      getLedgers,
      getStockItems: vi.fn(),
    } as unknown as typeof window.budcomDesktop;
    activateView('ledgers');
    await Promise.resolve();
    getLedgers.mockClear();

    startLedgerProgressPolling();
    startLedgerProgressPolling();
    await vi.advanceTimersByTimeAsync(5_000);
    expect(getLedgers).toHaveBeenCalledTimes(1);

    resolveLedger?.(pageState('ledger', progress({
      status: 'completed',
      totalExpected: 25,
    })) as LedgerPageState);
    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(5_000);
    expect(getLedgers).toHaveBeenCalledTimes(1);
  });

  it.each(['completed', 'cancelled', 'failed'] as const)(
    'stops stock polling on %s',
    async (status) => {
      const getStockItems = vi.fn(async () => pageState(
        'stock-item',
        progress({ status, totalExpected: 25 }),
      ) as StockItemPageState);
      window.budcomDesktop = {
        getLedgers: vi.fn(),
        getStockItems,
      } as unknown as typeof window.budcomDesktop;
      activateView('stock-items');
      await Promise.resolve();
      getStockItems.mockClear();

      startStockItemProgressPolling();
      await vi.advanceTimersByTimeAsync(1_000);
      await vi.advanceTimersByTimeAsync(5_000);
      expect(getStockItems).toHaveBeenCalledTimes(1);
    },
  );

  it('keeps Ledger and Stock polling isolated and stops on navigation/disposal', async () => {
    const getLedgers = vi.fn(async () => pageState('ledger', progress()) as LedgerPageState);
    const getStockItems = vi.fn(async () => pageState('stock-item', progress()) as StockItemPageState);
    window.budcomDesktop = {
      getLedgers,
      getStockItems,
    } as unknown as typeof window.budcomDesktop;

    activateView('ledgers');
    await Promise.resolve();
    getLedgers.mockClear();
    startLedgerProgressPolling();
    await vi.advanceTimersByTimeAsync(1_000);
    expect(getLedgers).toHaveBeenCalledTimes(1);
    expect(getStockItems).not.toHaveBeenCalled();

    activateView('stock-items');
    await Promise.resolve();
    getStockItems.mockClear();
    startStockItemProgressPolling();
    await vi.advanceTimersByTimeAsync(1_000);
    expect(getStockItems).toHaveBeenCalledTimes(1);
    const ledgerCallsAfterNavigation = getLedgers.mock.calls.length;

    disposeSyncProgressPolling();
    await vi.advanceTimersByTimeAsync(5_000);
    expect(getLedgers).toHaveBeenCalledTimes(ledgerCallsAfterNavigation);
    expect(getStockItems).toHaveBeenCalledTimes(1);
    expect(document.getElementById('global-loading')?.className).toBe('loading-bar hidden');
  });
});

describe('module synchronization exception feedback', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="app-notification" class="app-notification hidden"></div>
      <button id="btn-sync-ledgers"></button>
      <button id="btn-cancel-ledger-sync" class="hidden"></button>
      <button id="btn-refresh-ledgers"></button>
      <button id="btn-clear-ledger-cache"></button>
      <button id="btn-ledger-prev"></button>
      <button id="btn-ledger-next"></button>
      <input id="ledger-search-input" />
      ${progressMarkup('ledger')}
      <span id="ledger-list-meta"></span>
      <div id="ledger-list"></div>
      <button id="btn-sync-stock-items"></button>
      <button id="btn-cancel-stock-item-sync" class="hidden"></button>
      <button id="btn-refresh-stock-items"></button>
      <button id="btn-clear-stock-item-cache"></button>
      <button id="btn-stock-item-prev"></button>
      <button id="btn-stock-item-next"></button>
      <input id="stock-item-search-input" />
      ${progressMarkup('stock-item')}
      <span id="stock-item-list-meta"></span>
      <div id="stock-item-list"></div>
    `;
  });

  afterEach(() => {
    disposeSyncProgressPolling();
  });

  it('shows visible ledger error text when a ledger sync is rejected', async () => {
    window.budcomDesktop = {
      syncLedgers: vi.fn(async () => {
        throw new Error('connector unreachable');
      }),
    } as unknown as typeof window.budcomDesktop;
    bindLedgerActions();

    document.getElementById('btn-sync-ledgers')?.dispatchEvent(new Event('click', { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));

    const error = document.getElementById('ledger-progress-error');
    expect(error?.textContent).toBe('Ledger sync failed.');
    expect(error?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('app-notification')?.textContent).toContain('Ledger sync failed.');
  });

  it('shows visible ledger error text when ledger sync cancellation is rejected', async () => {
    window.budcomDesktop = {
      cancelLedgerSync: vi.fn(async () => {
        throw new Error('connector unreachable');
      }),
    } as unknown as typeof window.budcomDesktop;
    bindLedgerActions();

    document.getElementById('btn-cancel-ledger-sync')?.dispatchEvent(new Event('click', { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));

    const error = document.getElementById('ledger-progress-error');
    expect(error?.textContent).toBe('Unable to cancel ledger sync.');
    expect(error?.classList.contains('hidden')).toBe(false);
  });

  it('shows visible stock item error text when a stock item sync is rejected', async () => {
    window.budcomDesktop = {
      syncStockItems: vi.fn(async () => {
        throw new Error('connector unreachable');
      }),
    } as unknown as typeof window.budcomDesktop;
    bindStockItemActions();

    document.getElementById('btn-sync-stock-items')?.dispatchEvent(new Event('click', { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));

    const error = document.getElementById('stock-item-progress-error');
    expect(error?.textContent).toBe('Stock item sync failed.');
    expect(error?.classList.contains('hidden')).toBe(false);
    expect(document.getElementById('app-notification')?.textContent).toContain('Stock item sync failed.');
  });

  it('shows visible stock item error text when stock item sync cancellation is rejected', async () => {
    window.budcomDesktop = {
      cancelStockItemSync: vi.fn(async () => {
        throw new Error('connector unreachable');
      }),
    } as unknown as typeof window.budcomDesktop;
    bindStockItemActions();

    document.getElementById('btn-cancel-stock-item-sync')?.dispatchEvent(new Event('click', { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));

    const error = document.getElementById('stock-item-progress-error');
    expect(error?.textContent).toBe('Unable to cancel stock item sync.');
    expect(error?.classList.contains('hidden')).toBe(false);
  });

  it('clears a stale ledger error once a subsequent sync succeeds', async () => {
    const syncLedgers = vi.fn()
      .mockRejectedValueOnce(new Error('connector unreachable'))
      .mockResolvedValueOnce({
        progress: progress({ status: 'completed', totalExpected: 10, itemsProcessed: 10 }),
        statistics: { lastSyncedAt: '2026-07-26T00:00:00.000Z' },
      });
    window.budcomDesktop = {
      syncLedgers,
      getLedgers: vi.fn(async () => pageState(
        'ledger',
        progress({ status: 'completed', totalExpected: 10, itemsProcessed: 10 }),
      ) as LedgerPageState),
    } as unknown as typeof window.budcomDesktop;
    bindLedgerActions();

    document.getElementById('btn-sync-ledgers')?.dispatchEvent(new Event('click', { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(document.getElementById('ledger-progress-error')?.classList.contains('hidden')).toBe(false);

    document.getElementById('btn-sync-ledgers')?.dispatchEvent(new Event('click', { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(document.getElementById('ledger-progress-error')?.classList.contains('hidden')).toBe(true);
  });
});
