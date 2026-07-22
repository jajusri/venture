import { Router } from 'express';

import { CONNECTOR_VERSION, SCHEMA_VERSION } from '../config.js';

export const healthRouter = Router();

healthRouter.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    schemaVersion: SCHEMA_VERSION,
    connectorVersion: CONNECTOR_VERSION,
    tallyReachable: false,
    readOnly: true,
  });
});
