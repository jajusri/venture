import { Router } from 'express';

import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { LedgerSearchParams } from '../../erp/ledger/ledger-domain.js';
import type { LedgerSyncService } from '../../services/ledger/ledger-sync.service.js';

function parseLedgerSearchParams(query: Record<string, unknown>): LedgerSearchParams {
  const page = Math.max(1, Number.parseInt(String(query.page ?? '1'), 10) || 1);
  const pageSize = Math.min(500, Math.max(1, Number.parseInt(String(query.pageSize ?? '50'), 10) || 50));
  const sortBy = query.sortBy as LedgerSearchParams['sortBy'];
  const sortDirection = query.sortDirection === 'desc' ? 'desc' : 'asc';
  return {
    query: typeof query.query === 'string' ? query.query : undefined,
    status: query.status as LedgerSearchParams['status'],
    parentGroup: typeof query.parentGroup === 'string' ? query.parentGroup : undefined,
    page,
    pageSize,
    sortBy: sortBy ?? 'name',
    sortDirection,
  };
}

export function createLedgersRouter(ledgerSync: LedgerSyncService): Router {
  const router = Router();

  router.get(
    '/ledgers',
    asyncHandler(async (req, res) => {
      const params = parseLedgerSearchParams(req.query as Record<string, unknown>);
      const result = await ledgerSync.getLedgers(params);
      res.status(200).json({
        schemaVersion: '1.0.0',
        dataFreshnessAt: new Date().toISOString(),
        ...result,
      });
    }),
  );

  router.get(
    '/ledgers/:id',
    asyncHandler(async (req, res) => {
      const ledger = await ledgerSync.getLedgerById(req.params.id);
      if (!ledger) {
        res.status(404).json({
          code: 'NOT_FOUND',
          message: `Ledger '${req.params.id}' was not found.`,
        });
        return;
      }
      res.status(200).json({
        schemaVersion: '1.0.0',
        dataFreshnessAt: new Date().toISOString(),
        ledger,
      });
    }),
  );

  router.post(
    '/sync/ledgers',
    asyncHandler(async (req, res) => {
      const incremental = Boolean(req.body?.incremental);
      const result = await ledgerSync.syncLedgers({ incremental });
      res.status(200).json({
        schemaVersion: '1.0.0',
        ...result,
      });
    }),
  );

  router.get(
    '/sync/ledgers/status',
    asyncHandler(async (_req, res) => {
      const progress = ledgerSync.getSyncProgress();
      res.status(200).json({
        schemaVersion: '1.0.0',
        progress,
      });
    }),
  );

  router.get(
    '/sync/ledgers/statistics',
    asyncHandler(async (_req, res) => {
      const statistics = await ledgerSync.getStatistics();
      res.status(200).json({
        schemaVersion: '1.0.0',
        statistics,
      });
    }),
  );

  router.post(
    '/sync/ledgers/clear-cache',
    asyncHandler(async (_req, res) => {
      await ledgerSync.clearCache();
      res.status(200).json({
        schemaVersion: '1.0.0',
        ok: true,
        message: 'Ledger cache cleared.',
      });
    }),
  );

  return router;
}
