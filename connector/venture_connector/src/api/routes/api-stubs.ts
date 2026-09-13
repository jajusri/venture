import { Router } from 'express';

import { notImplemented } from '../../infrastructure/errors/app-error.js';
import { asyncHandler } from '../../infrastructure/errors/error-handler.js';

function stubRoute(feature: string) {
  return asyncHandler(async (_req, _res, next) => {
    next(notImplemented(feature));
  });
}

export function createApiStubsRouter(): Router {
  const router = Router();

  router.get('/companies/:companyId/ledgers/:ledgerId', stubRoute('Ledger detail'));
  router.get('/companies/:companyId/ledger-transactions', stubRoute('Ledger transactions'));
  router.get('/companies/:companyId/vouchers', stubRoute('Voucher listing'));
  router.get('/companies/:companyId/vouchers/:voucherId', stubRoute('Voucher detail'));
  router.get('/sync/checkpoint', stubRoute('Sync checkpoint'));

  return router;
}
