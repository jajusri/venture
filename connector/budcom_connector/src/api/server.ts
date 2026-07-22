import express, { type Express } from 'express';

import type { Logger } from '../infrastructure/logging/logger.js';
import { createErrorMiddleware } from '../infrastructure/errors/error-handler.js';
import type { HealthService } from '../services/health/health-service.js';
import { readOnlyMiddleware } from './middleware/read-only.js';
import { createApiStubsRouter } from './routes/api-stubs.js';
import { createDeviceRouter } from './routes/device.js';
import { createHealthRouter } from './routes/health.js';

export interface ExpressAppDeps {
  readonly logger: Logger;
  readonly healthService: HealthService;
}

export function createExpressApp(deps: ExpressAppDeps): Express {
  const app = express();

  app.use(express.json());
  app.use(readOnlyMiddleware);
  app.use(createHealthRouter(deps.healthService));
  app.use(createDeviceRouter());
  app.use(createApiStubsRouter());
  app.use(createErrorMiddleware(deps.logger));

  return app;
}
