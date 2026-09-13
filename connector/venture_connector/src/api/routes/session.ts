import { Router } from 'express';

import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { ConnectorSessionService } from '../../services/interfaces/connector-session.js';
import {
  mapSelectionStatusToHttpStatus,
  mapValidationStatusToHttpStatus,
} from '../../services/session/session-validator.js';

interface SelectCompanyBody {
  readonly companyId?: string;
}

export function createSessionRouter(sessionService: ConnectorSessionService): Router {
  const router = Router();

  router.get(
    '/session',
    asyncHandler(async (_req, res) => {
      const snapshot = sessionService.getSession();
      res.status(200).json(snapshot);
    }),
  );

  router.post(
    '/session/company',
    asyncHandler(async (req, res) => {
      const body = req.body as SelectCompanyBody;
      const result = await sessionService.selectCompany(body.companyId ?? '');
      res.status(mapSelectionStatusToHttpStatus(result.status)).json(result);
    }),
  );

  router.delete(
    '/session/company',
    asyncHandler(async (_req, res) => {
      const snapshot = sessionService.clearSelection();
      res.status(200).json({
        status: 'SUCCESS',
        session: snapshot.session,
        contractVersion: snapshot.contractVersion,
      });
    }),
  );

  router.post(
    '/session/validate',
    asyncHandler(async (_req, res) => {
      const result = await sessionService.refreshValidation();
      res.status(mapValidationStatusToHttpStatus(result.status)).json(result);
    }),
  );

  return router;
}
