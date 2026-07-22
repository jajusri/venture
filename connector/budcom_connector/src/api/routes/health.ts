import { Router } from 'express';

import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { HealthService } from '../../services/health/health-service.js';

export function createHealthRouter(healthService: HealthService): Router {
  const router = Router();

  router.get(
    '/health',
    asyncHandler(async (_req, res) => {
      const report = await healthService.getReport();
      res.status(200).json({
        status: report.status,
        schemaVersion: report.schemaVersion,
        connectorVersion: report.connectorVersion,
        tallyReachable: report.tallyReachable,
        readOnly: report.readOnly,
        services: report.services,
      });
    }),
  );

  return router;
}
