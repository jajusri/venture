import { describe, expect, it, vi } from 'vitest';



import { ConnectorRequestError } from '../../src/application/connector-error.js';

import { StockItemService } from '../../src/application/stock-item-service.js';

import {

  ALLOWED_IPC_CHANNELS,

  assertAllowedIpcChannel,

  validateStockItemQuery,

} from '../../src/application/ipc-allowlist.js';



function createLogMock() {

  return { append: vi.fn(), appendStructured: vi.fn(), clearNonessential: vi.fn() };

}



describe('StockItemService high-risk flows', () => {

  it('surfaces sync conflict as connector error', async () => {

    const fetchImpl = vi.fn(async (input: RequestInfo, init?: RequestInit) => {

      if (String(input).endsWith('/sync/stock-items') && init?.method === 'POST') {

        return new Response(

          JSON.stringify({ code: 'SYNC_CONFLICT', message: 'A stock item sync is already running for this connector.' }),

          { status: 409, headers: { 'content-type': 'application/json' } },

        );

      }

      return new Response('{}', { status: 404 });

    });



    const service = new StockItemService({

      connectorBaseUrl: 'http://127.0.0.1:8080',

      fetchImpl: fetchImpl as typeof fetch,

      logService: createLogMock(),

      maxAttempts: 1,

    });



    await expect(service.syncStockItems()).rejects.toBeInstanceOf(ConnectorRequestError);

  });



  it('returns cancellation progress from connector', async () => {

    const fetchImpl = vi.fn(async (input: RequestInfo, init?: RequestInit) => {

      if (String(input).endsWith('/sync/stock-items/cancel') && init?.method === 'POST') {

        return new Response(

          JSON.stringify({

            schemaVersion: '1.0.0',

            progress: { status: 'cancelling', itemsProcessed: 1, itemsAdded: 0, itemsUpdated: 0, itemsSkipped: 0, itemsFailed: 0, cancelRequested: true },

          }),

          { status: 200 },

        );

      }

      return new Response('{}', { status: 404 });

    });



    const service = new StockItemService({

      connectorBaseUrl: 'http://127.0.0.1:8080',

      fetchImpl: fetchImpl as typeof fetch,

      logService: createLogMock(),

    });



    const progress = await service.cancelSync();

    expect(progress.progress.status).toBe('cancelling');

  });



  it('maps API validation errors to user messages on page load', async () => {

    const fetchImpl = vi.fn(async () =>

      new Response(JSON.stringify({ code: 'VALIDATION_ERROR', message: 'No company is selected for this connector session' }), {

        status: 400,

        headers: { 'content-type': 'application/json' },

      }),

    );



    const service = new StockItemService({

      connectorBaseUrl: 'http://127.0.0.1:8080',

      fetchImpl: fetchImpl as typeof fetch,

      logService: createLogMock(),

      maxAttempts: 1,

    });



    const state = await service.getPageState();

    expect(state.ok).toBe(false);

    expect(state.userMessage).toContain('company');

  });



  it('supports search and pagination query parameters', async () => {

    const fetchImpl = vi.fn(async (input: RequestInfo) => {

      const url = String(input);

      if (url.includes('/stock-items?')) {

        expect(url).toContain('query=bolt');

        expect(url).toContain('page=2');

        expect(url).toContain('pageSize=10');

        return new Response(

          JSON.stringify({

            schemaVersion: '1.0.0',

            dataFreshnessAt: '2026-01-01T00:00:00.000Z',

            items: [],

            pagination: { page: 2, pageSize: 10, totalItems: 0, totalPages: 0 },

          }),

          { status: 200 },

        );

      }

      if (url.endsWith('/sync/stock-items/statistics')) {

        return new Response(JSON.stringify({ schemaVersion: '1.0.0', statistics: { totalStockItems: 0, withBaseUnit: 0, incompleteData: 0, withHsn: 0, withGst: 0, withOpeningBalance: 0, deletedStockItems: 0, lastSyncedAt: null } }), { status: 200 });

      }

      if (url.endsWith('/sync/stock-items/status')) {

        return new Response(JSON.stringify({ schemaVersion: '1.0.0', progress: { status: 'idle', itemsProcessed: 0, itemsAdded: 0, itemsUpdated: 0, itemsSkipped: 0, itemsFailed: 0 } }), { status: 200 });

      }

      return new Response('{}', { status: 404 });

    });



    const service = new StockItemService({

      connectorBaseUrl: 'http://127.0.0.1:8080',

      fetchImpl: fetchImpl as typeof fetch,

      logService: createLogMock(),

    });



    const result = await service.getStockItems({ query: 'bolt', page: 2, pageSize: 10 });

    expect(result.pagination.page).toBe(2);

  });



  it('handles malformed stock item list payload safely', async () => {

    const fetchImpl = vi.fn(async (input: RequestInfo) => {

      const url = String(input);

      if (url.includes('/stock-items?')) {

        return new Response('not-json', { status: 200, headers: { 'content-type': 'application/json' } });

      }

      return new Response('{}', { status: 404 });

    });



    const service = new StockItemService({

      connectorBaseUrl: 'http://127.0.0.1:8080',

      fetchImpl: fetchImpl as typeof fetch,

      logService: createLogMock(),

      maxAttempts: 1,

    });



    const state = await service.getPageState();

    expect(state.ok).toBe(false);

    expect(state.userMessage).toBeTruthy();

  });

});



describe('stock item IPC allowlist', () => {

  it('allowlists stock item IPC channels only', () => {

    for (const channel of [

      'desktop:get-stock-items',

      'desktop:sync-stock-items',

      'desktop:cancel-stock-item-sync',

      'desktop:get-stock-item-statistics',

      'desktop:clear-stock-item-cache',

    ]) {

      expect(() => assertAllowedIpcChannel(channel)).not.toThrow();

      expect(ALLOWED_IPC_CHANNELS).toContain(channel);

    }

    expect(() => assertAllowedIpcChannel('desktop:raw-sqlite-query')).toThrow();

  });



  it('bounds stock item query pagination inputs', () => {
    const bounded = validateStockItemQuery({ query: 'widget', page: 0, pageSize: 500 });
    expect(bounded.page).toBe(1);
    expect(bounded.pageSize).toBe(100);
    expect(bounded.query).toBe('widget');
  });
});

