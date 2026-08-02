import express, { type Express } from 'express';

import type { Logger } from '../infrastructure/logging/logger.js';
import { createErrorMiddleware } from '../infrastructure/errors/error-handler.js';
import type { ConnectorConfig } from '../config/defaults.js';
import { createRequireTrustedDeviceAuthMiddleware } from './middleware/require-trusted-device-auth.js';
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
import { JSON_BODY_LIMIT, rejectMalformedContentLength, requireJsonContentTypeForMutation } from './middleware/request-security.js';
import { createApiStubsRouter } from './routes/api-stubs.js';
import { createCompaniesRouter } from './routes/companies.js';
import { createDeviceManagementRouter, createDevicePairingRouter } from './routes/device.js';
import type { TrustedDeviceRepository } from '../services/device/trusted-device-repository.js';
import { createDiagnosticsRouter } from './routes/diagnostics.js';
import { createHealthRouter } from './routes/health.js';
import { createMasterDataRouter } from './routes/master-data.js';
import { createSessionRouter } from './routes/session.js';
import { createVouchersRouter } from './routes/vouchers.js';
import type {
  VoucherApplicationService,
  VoucherSnapshotSyncService,
} from '../services/voucher/voucher-application.interface.js';
import { createRequestLoggingMiddleware } from './middleware/request-logging.js';

export interface ExpressAppDeps {
  readonly logger: Logger;
  readonly config: Pick<ConnectorConfig, 'networkExposure' | 'requireDeviceAuthForLan'>;
  readonly healthService: HealthService;
  readonly companyDiscovery: CompanyDiscoveryService;
  readonly connectorSession: ConnectorSessionService;
  readonly masterData: MasterDataService;
  readonly ledgerSync: LedgerSyncService;
  readonly stockItemSync: StockItemSyncService;
  readonly tallyDiagnostics: TallyDiagnosticsService;
  readonly voucherApplication: VoucherApplicationService;
  readonly voucherSynchronization?: VoucherSnapshotSyncService;
  /** Optional: when provided, device pairing routes are functional. */
  readonly trustedDevices?: TrustedDeviceRepository;
}

export function createExpressApp(deps: ExpressAppDeps): Express {
  const app = express();

  app.use(createRequestLoggingMiddleware(deps.logger));
  app.use(rejectMalformedContentLength);
  app.use(express.json({ limit: JSON_BODY_LIMIT }));
  app.use(requireJsonContentTypeForMutation);
  app.use(readOnlyMiddleware);
  app.use(createHealthRouter(deps.healthService));
  app.use(createDiagnosticsRouter(deps.tallyDiagnostics));
  // Pairing bootstrap itself must stay reachable without a token — a device has no
  // credentials until it pairs. Neither pairing-bootstrap route discloses anything the
  // caller didn't already supply/possess (see device.ts). Everything mounted after this
  // point — including device *management* routes (list/lookup/revoke, which read back or
  // mutate other devices' state) — is gated by requireTrustedDeviceAuth.
  app.use(createDevicePairingRouter(deps.trustedDevices));
  app.use(createRequireTrustedDeviceAuthMiddleware({
    config: deps.config,
    trustedDevices: deps.trustedDevices,
  }));
  app.use(createDeviceManagementRouter(deps.trustedDevices));
  app.use(createCompaniesRouter(deps.companyDiscovery));
  app.use(createSessionRouter(deps.connectorSession));
  app.use(createMasterDataRouter(deps.masterData));
  app.use(createLedgersRouter(deps.ledgerSync));
  app.use(createStockItemsRouter(deps.stockItemSync));
  app.use(createVouchersRouter(
    deps.voucherApplication,
    deps.voucherSynchronization,
    deps.connectorSession,
  ));
  app.use(createApiStubsRouter());
  app.use(createErrorMiddleware(deps.logger));

  return app;
}
