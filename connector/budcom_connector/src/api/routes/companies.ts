import { Router } from 'express';

import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { CompanyDiscoveryService } from '../../services/interfaces/company-discovery.js';

export function createCompaniesRouter(companyDiscovery: CompanyDiscoveryService): Router {
  const router = Router();

  router.get(
    '/companies',
    asyncHandler(async (_req, res) => {
      const result = await companyDiscovery.discoverCompanies();
      res.status(200).json(result);
    }),
  );

  return router;
}
