import { Router } from 'express';

function notImplemented(feature: string) {
  return (_req: unknown, res: { status: (code: number) => { json: (body: unknown) => void } }) => {
    res.status(501).json({
      code: 'NOT_IMPLEMENTED',
      message: `${feature} is not implemented in Milestone 0.`,
    });
  };
}

export const apiRouter = Router();

apiRouter.get('/companies', notImplemented('Company listing'));
apiRouter.get('/companies/:companyId/ledgers', notImplemented('Ledger listing'));
apiRouter.get('/companies/:companyId/ledgers/:ledgerId', notImplemented('Ledger detail'));
apiRouter.get('/companies/:companyId/ledger-transactions', notImplemented('Ledger transactions'));
apiRouter.get('/companies/:companyId/vouchers', notImplemented('Voucher listing'));
apiRouter.get('/companies/:companyId/vouchers/:voucherId', notImplemented('Voucher detail'));
apiRouter.get('/sync/checkpoint', notImplemented('Sync checkpoint'));
