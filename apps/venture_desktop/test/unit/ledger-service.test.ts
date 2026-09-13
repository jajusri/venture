import { describe, expect, it, vi } from 'vitest';

import { LedgerService } from '../../src/application/ledger-service.js';
import { LogService } from '../../src/application/log-service.js';

describe('LedgerService', () => {
  it('loads ledger page state from connector APIs', async () => {
    const fetchImpl = vi.fn(async (url: string | URL): Promise<Response> => {
      const path = String(url);
      if (path.includes('/ledgers?')) {
        return new Response(
          JSON.stringify({
            schemaVersion: '1.0.0',
            dataFreshnessAt: '2026-07-23T00:00:00.000Z',
            items: [{ id: 'cash', name: 'Cash', normalizedName: 'cash', status: 'active', balanceNature: 'debit', syncedAt: '2026-07-23T00:00:00.000Z' }],
            pagination: { page: 1, pageSize: 25, totalItems: 1, totalPages: 1 },
          }),
          { status: 200 },
        );
      }
      if (path.endsWith('/sync/ledgers/statistics')) {
        return new Response(
          JSON.stringify({
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
          }),
          { status: 200 },
        );
      }
      return new Response(
        JSON.stringify({
          schemaVersion: '1.0.0',
          progress: {
            status: 'completed',
            startedAt: '2026-07-23T00:00:00.000Z',
            completedAt: '2026-07-23T00:00:00.000Z',
            durationMs: 10,
            itemsProcessed: 1,
            itemsAdded: 1,
            itemsUpdated: 0,
            itemsSkipped: 0,
            itemsFailed: 0,
            lastError: null,
            cancelRequested: false,
          },
        }),
        { status: 200 },
      );
    });

    const service = new LedgerService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      logService: new LogService(),
      maxAttempts: 1,
    });

    const state = await service.getPageState();
    expect(state.ok).toBe(true);
    expect(state.list?.items[0]?.name).toBe('Cash');
    expect(state.statistics?.statistics.totalLedgers).toBe(1);
  });
});
