import express, { type Express } from 'express';

import type { Logger } from '../infrastructure/logging/logger.js';
import { createErrorMiddleware } from '../infrastructure/errors/error-handler.js';
import type { CompanyDiscoveryService } from '../services/interfaces/company-discovery.js';
import type { ConnectorSessionService } from '../services/interfaces/connector-session.js';
import type { HealthService } from '../services/health/health-service.js';
import type { TallyDiagnosticsService } from '../services/interfaces/tally-diagnostics.js';
import type { MasterDataService } from '../services/extraction/master-data.service.js';
import type { LedgerSyncService } from '../services/ledger/ledger-sync.service.js';
import type { StockItemSyncService } from '../services/stock-item/stock-item-sync.service.js';
import { createLedgersRouter } from './routes/ledgers.js';
import { createStockItemsRouter } from './routes/stock-items.js';
import { readOnlyMiddleware } from './middleware/read-only.js';
import { createApiStubsRouter } from './routes/api-stubs.js';
import { createCompaniesRouter } from './routes/companies.js';
import { createDeviceRouter } from './routes/device.js';
import { createDiagnosticsRouter } from './routes/diagnostics.js';
import { createHealthRouter } from './routes/health.js';
import { createMasterDataRouter } from './routes/master-data.js';
import { createSessionRouter } from './routes/session.js';

export interface ExpressAppDeps {
  readonly logger: Logger;
  readonly healthService: HealthService;
  readonly companyDiscovery: CompanyDiscoveryService;
  readonly connectorSession: ConnectorSessionService;
  readonly masterData: MasterDataService;
  readonly ledgerSync: LedgerSyncService;
  readonly stockItemSync: StockItemSyncService;
  readonly tallyDiagnostics: TallyDiagnosticsService;
}

export function createExpressApp(deps: ExpressAppDeps): Express {
  const app = express();

  app.use(express.json());
  app.use(readOnlyMiddleware);
  app.use(createHealthRouter(deps.healthService));
  app.use(createDiagnosticsRouter(deps.tallyDiagnostics));
  app.use(createDeviceRouter());
  app.use(createCompaniesRouter(deps.companyDiscovery));
  app.use(createSessionRouter(deps.connectorSession));
  app.use(createMasterDataRouter(deps.masterData));
  app.use(createLedgersRouter(deps.ledgerSync));
  app.use(createStockItemsRouter(deps.stockItemSync));
  app.use(createApiStubsRouter());
  app.use(createErrorMiddleware(deps.logger));

  return app;
}
