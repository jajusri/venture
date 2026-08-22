import { Router } from 'express';

import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { StockItemSearchParams } from '../../erp/stock-item/stock-item-domain.js';
import type { StockItemSyncService } from '../../services/stock-item/stock-item-sync.service.js';

const ALLOWED_SORT = new Set(['name', 'parentGroup', 'category', 'baseUnit', 'syncedAt']);
const MAX_PAGE_SIZE = 100;

function parseStockItemSearchParams(query: Record<string, unknown>): StockItemSearchParams {
  const page = Math.max(1, Number.parseInt(String(query.page ?? '1'), 10) || 1);
  const pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, Number.parseInt(String(query.pageSize ?? '50'), 10) || 50));
  const sortByRaw = typeof query.sortBy === 'string' ? query.sortBy : 'name';
  const sortBy = ALLOWED_SORT.has(sortByRaw) ? (sortByRaw as StockItemSearchParams['sortBy']) : 'name';
  const sortDirection = query.sortDirection === 'desc' ? 'desc' : 'asc';
  return {
    query: typeof query.query === 'string' ? query.query.slice(0, 128) : undefined,
    parentGroup: typeof query.parentGroup === 'string' ? query.parentGroup.slice(0, 128) : undefined,
    category: typeof query.category === 'string' ? query.category.slice(0, 128) : undefined,
    dataQuality:
      query.dataQuality === 'complete' || query.dataQuality === 'incomplete'
        ? query.dataQuality
        : undefined,
    page,
    pageSize,
    sortBy,
    sortDirection,
  };
}

export function createStockItemsRouter(stockItemSync: StockItemSyncService): Router {
  const router = Router();

  router.get(
    '/stock-items',
    asyncHandler(async (req, res) => {
      const params = parseStockItemSearchParams(req.query as Record<string, unknown>);
      const result = await stockItemSync.getStockItems(params);
      res.status(200).json({
        schemaVersion: '1.0.0',
        dataFreshnessAt: new Date().toISOString(),
        storage: stockItemSync.getStorageStatus(),
        ...result,
      });
    }),
  );

  router.get(
    '/stock-items/:id',
    asyncHandler(async (req, res) => {
      const stockItemId = String(req.params.id).slice(0, 128);
      const item = await stockItemSync.getStockItemById(stockItemId);
      if (!item) {
        res.status(404).json({ code: 'NOT_FOUND', message: `Stock item '${stockItemId}' was not found.` });
        return;
      }
      res.status(200).json({
        schemaVersion: '1.0.0',
        dataFreshnessAt: new Date().toISOString(),
        stockItem: item,
      });
    }),
  );

  router.post(
    '/sync/stock-items',
    asyncHandler(async (req, res) => {
      const incremental = Boolean(req.body?.incremental);
      const result = await stockItemSync.syncStockItems({ incremental });
      res.status(200).json({ schemaVersion: '1.0.0', ...result });
    }),
  );

  router.post(
    '/sync/stock-items/cancel',
    asyncHandler(async (_req, res) => {
      const progress = await stockItemSync.cancelSync();
      res.status(200).json({ schemaVersion: '1.0.0', progress });
    }),
  );

  router.get(
    '/sync/stock-items/status',
    asyncHandler(async (_req, res) => {
      const progress = await stockItemSync.getSyncProgress();
      res.status(200).json({
        schemaVersion: '1.0.0',
        progress,
        storage: stockItemSync.getStorageStatus(),
      });
    }),
  );

  router.get(
    '/sync/stock-items/statistics',
    asyncHandler(async (_req, res) => {
      const statistics = await stockItemSync.getStatistics();
      res.status(200).json({ schemaVersion: '1.0.0', statistics });
    }),
  );

  router.get(
    '/sync/stock-items/runs',
    asyncHandler(async (req, res) => {
      const limit = Math.min(50, Math.max(1, Number.parseInt(String(req.query.limit ?? '20'), 10) || 20));
      const runs = await stockItemSync.listSyncRuns(limit);
      res.status(200).json({ schemaVersion: '1.0.0', runs });
    }),
  );

  router.get(
    '/sync/stock-items/runs/:id',
    asyncHandler(async (req, res) => {
      const run = await stockItemSync.getSyncRun(String(req.params.id));
      if (!run) {
        res.status(404).json({ code: 'NOT_FOUND', message: 'Sync run not found.' });
        return;
      }
      res.status(200).json({ schemaVersion: '1.0.0', run });
    }),
  );

  router.post(
    '/sync/stock-items/clear-cache',
    asyncHandler(async (_req, res) => {
      await stockItemSync.clearCache();
      res.status(200).json({ schemaVersion: '1.0.0', ok: true, message: 'Stock item cache cleared.' });
    }),
  );

  router.post(
    '/storage/stock-items/integrity-check',
    asyncHandler(async (_req, res) => {
      const result = await stockItemSync.runIntegrityCheck();
      res.status(200).json({ schemaVersion: '1.0.0', ...result });
    }),
  );

  router.post(
    '/storage/stock-items/backup',
    asyncHandler(async (_req, res) => {
      const result = await stockItemSync.createBackup();
      res.status(result.ok ? 200 : 503).json({ schemaVersion: '1.0.0', ...result });
    }),
  );

  return router;
}
