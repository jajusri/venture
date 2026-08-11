import { Router } from 'express';

import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { LedgerSearchParams } from '../../erp/ledger/ledger-domain.js';
import type { LedgerSyncService } from '../../services/ledger/ledger-sync.service.js';
import {
  businessDateIsoDaysBefore,
  businessTodayIso,
} from '../../services/voucher/voucher-business-date.js';

const ALLOWED_SORT = new Set(['name', 'parentGroup', 'closingBalance', 'syncedAt']);
const MAX_PAGE_SIZE = 100;
/** Matches the Ledger statement's own default lookback when no explicit "from" is given. */
const DEFAULT_STATEMENT_LOOKBACK_DAYS = 29;

function parseStatementRange(query: Record<string, unknown>): { from: string; to: string } {
  const to = typeof query.to === 'string' && query.to.trim() ? query.to.trim() : businessTodayIso();
  const from = typeof query.from === 'string' && query.from.trim()
    ? query.from.trim()
    : businessDateIsoDaysBefore(to, DEFAULT_STATEMENT_LOOKBACK_DAYS);
  return { from, to };
}

function parseLedgerSearchParams(query: Record<string, unknown>): LedgerSearchParams {
  const page = Math.max(1, Number.parseInt(String(query.page ?? '1'), 10) || 1);
  const pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, Number.parseInt(String(query.pageSize ?? '50'), 10) || 50));
  const sortByRaw = typeof query.sortBy === 'string' ? query.sortBy : 'name';
  const sortBy = ALLOWED_SORT.has(sortByRaw) ? (sortByRaw as LedgerSearchParams['sortBy']) : 'name';
  const sortDirection = query.sortDirection === 'desc' ? 'desc' : 'asc';
  return {
    query: typeof query.query === 'string' ? query.query.slice(0, 128) : undefined,
    status: query.status as LedgerSearchParams['status'],
    parentGroup: typeof query.parentGroup === 'string' ? query.parentGroup.slice(0, 128) : undefined,
    page,
    pageSize,
    sortBy,
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
        storage: ledgerSync.getStorageStatus(),
        ...result,
      });
    }),
  );

  router.get(
    '/ledgers/:id',
    asyncHandler(async (req, res) => {
      const ledgerId = String(req.params.id).slice(0, 128);
      const ledger = await ledgerSync.getLedgerById(ledgerId);
      if (!ledger) {
        res.status(404).json({ code: 'NOT_FOUND', message: `Ledger '${ledgerId}' was not found.` });
        return;
      }
      res.status(200).json({
        schemaVersion: '1.0.0',
        dataFreshnessAt: new Date().toISOString(),
        ledger,
      });
    }),
  );

  router.get(
    '/ledgers/:id/statement',
    asyncHandler(async (req, res) => {
      const ledgerId = String(req.params.id).slice(0, 128);
      const { from, to } = parseStatementRange(req.query as Record<string, unknown>);
      const statement = await ledgerSync.getLedgerStatement(ledgerId, from, to);
      if (!statement) {
        res.status(404).json({ code: 'NOT_FOUND', message: `Ledger '${ledgerId}' was not found.` });
        return;
      }
      res.status(200).json({
        schemaVersion: '1.0.0',
        dataFreshnessAt: new Date().toISOString(),
        statement,
      });
    }),
  );

  router.post(
    '/sync/ledgers',
    asyncHandler(async (req, res) => {
      const incremental = Boolean(req.body?.incremental);
      const result = await ledgerSync.syncLedgers({ incremental });
      res.status(200).json({ schemaVersion: '1.0.0', ...result });
    }),
  );

  router.post(
    '/sync/ledgers/cancel',
    asyncHandler(async (_req, res) => {
      const progress = await ledgerSync.cancelSync();
      res.status(200).json({ schemaVersion: '1.0.0', progress });
    }),
  );

  router.get(
    '/sync/ledgers/status',
    asyncHandler(async (_req, res) => {
      const progress = ledgerSync.getSyncProgress();
      res.status(200).json({
        schemaVersion: '1.0.0',
        progress,
        storage: ledgerSync.getStorageStatus(),
      });
    }),
  );

  router.get(
    '/sync/ledgers/statistics',
    asyncHandler(async (_req, res) => {
      const statistics = await ledgerSync.getStatistics();
      res.status(200).json({ schemaVersion: '1.0.0', statistics });
    }),
  );

  router.get(
    '/sync/ledgers/runs',
    asyncHandler(async (req, res) => {
      const limit = Math.min(50, Math.max(1, Number.parseInt(String(req.query.limit ?? '20'), 10) || 20));
      const runs = await ledgerSync.listSyncRuns(limit);
      res.status(200).json({ schemaVersion: '1.0.0', runs });
    }),
  );

  router.get(
    '/sync/ledgers/runs/:id',
    asyncHandler(async (req, res) => {
      const run = await ledgerSync.getSyncRun(String(req.params.id));
      if (!run) {
        res.status(404).json({ code: 'NOT_FOUND', message: 'Sync run not found.' });
        return;
      }
      res.status(200).json({ schemaVersion: '1.0.0', run });
    }),
  );

  router.post(
    '/sync/ledgers/clear-cache',
    asyncHandler(async (_req, res) => {
      await ledgerSync.clearCache();
      res.status(200).json({ schemaVersion: '1.0.0', ok: true, message: 'Ledger cache cleared.' });
    }),
  );

  router.post(
    '/storage/ledgers/integrity-check',
    asyncHandler(async (_req, res) => {
      const result = await ledgerSync.runIntegrityCheck();
      res.status(200).json({ schemaVersion: '1.0.0', ...result });
    }),
  );

  router.post(
    '/storage/ledgers/backup',
    asyncHandler(async (_req, res) => {
      const result = await ledgerSync.createBackup();
      res.status(result.ok ? 200 : 503).json({ schemaVersion: '1.0.0', ...result });
    }),
  );

  return router;
}
