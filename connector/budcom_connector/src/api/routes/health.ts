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
        bindHost: report.bindHost,
        bindPort: report.bindPort,
        networkExposure: report.networkExposure,
        networkExposureWarning: report.networkExposureWarning,
        networkPolicySatisfied: report.networkPolicySatisfied,
        authenticatedLanAccessEnabled: report.authenticatedLanAccessEnabled,
        connectorId: report.connectorId,
        connectorName: report.connectorName,
        discoveryAdvertising: report.discoveryAdvertising,
        services: report.services,
        startupCorrelationId: report.startupCorrelationId ?? null,
        repositoryAvailable: report.repositoryAvailable,
        databaseAccessible: report.databaseAccessible,
      });
    }),
  );

  router.get('/ready', (_req, res) => {
    const report = healthService.getReadinessReport();
    res.status(report.status === 'ready' ? 200 : 503).json(report);
  });

  return router;
}
