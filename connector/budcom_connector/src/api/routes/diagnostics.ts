import { Router } from 'express';

import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { TallyDiagnosticsService } from '../../services/interfaces/tally-diagnostics.js';

export function createDiagnosticsRouter(tallyDiagnostics: TallyDiagnosticsService): Router {
  const router = Router();

  router.get(
    '/diagnostics/connection',
    asyncHandler(async (_req, res) => {
      const diagnostics = tallyDiagnostics.getConnectionDiagnostics();
      res.status(200).json({
        schemaVersion: '1.0.0',
        connection: diagnostics,
      });
    }),
  );

  return router;
}
