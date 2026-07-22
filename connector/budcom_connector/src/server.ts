import express from 'express';

import { config } from './config.js';
import { readOnlyMiddleware } from './middleware/read-only.js';
import { apiRouter } from './routes/api-stubs.js';
import { deviceRouter } from './routes/device.js';
import { healthRouter } from './routes/health.js';

export function createApp() {
  const app = express();
  app.use(express.json());
  app.use(readOnlyMiddleware);
  app.use(healthRouter);
  app.use(deviceRouter);
  app.use(apiRouter);
  return app;
}

export function startServer() {
  const app = createApp();
  return app.listen(config.port, config.host, () => {
    console.info(`Budcom connector listening on ${config.host}:${config.port}`);
  });
}
