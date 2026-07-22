import { Router } from 'express';

import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import { parsePaginationParams } from '../../extraction/core/pagination.js';
import { MasterDataEntityType } from '../../extraction/core/types.js';
import type { MasterDataService } from '../../services/extraction/master-data.service.js';

export function createMasterDataRouter(masterData: MasterDataService): Router {
  const router = Router({ mergeParams: true });

  router.get(
    '/companies/:companyId',
    asyncHandler(async (req, res) => {
      const info = await masterData.getCompanyInfo(req.params.companyId);
      res.status(200).json({
        company: info,
        schemaVersion: '1.0.0',
        dataFreshnessAt: new Date().toISOString(),
      });
    }),
  );

  router.get(
    '/companies/:companyId/ledger-groups',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getLedgerGroups(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/ledgers',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getLedgers(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/stock-groups',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getStockGroups(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/stock-categories',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getStockCategories(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/stock-items',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getStockItems(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/units',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getUnits(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/godowns',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getGodowns(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/cost-categories',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getCostCategories(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/cost-centres',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getCostCentres(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/voucher-types',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getVoucherTypes(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/companies/:companyId/gst-registrations',
    asyncHandler(async (req, res) => {
      const pagination = parsePaginationParams(req.query as { page?: string; pageSize?: string });
      const result = await masterData.getGstRegistrations(req.params.companyId, pagination);
      res.status(200).json(result);
    }),
  );

  router.get(
    '/diagnostics/extraction',
    asyncHandler(async (req, res) => {
      const entityType = req.query.entityType as MasterDataEntityType | undefined;
      const diagnostics = masterData.getExtractorDiagnostics(entityType);
      res.status(200).json({ schemaVersion: '1.0.0', extractors: diagnostics });
    }),
  );

  return router;
}
