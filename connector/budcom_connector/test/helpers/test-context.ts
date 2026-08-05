import { createExpressApp } from '../../src/api/server.js';
import type { ExpressAppDeps } from '../../src/api/server.js';
import { registerServices } from '../../src/bootstrap/register-services.js';
import type { RegisterServicesOptions } from '../../src/bootstrap/register-services.js';
import type { ServiceLifecycle } from '../../src/core/types.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import type { CompanyDiscoveryService } from '../../src/services/interfaces/company-discovery.js';
import type { ConnectorSessionService } from '../../src/services/interfaces/connector-session.js';
import type { MasterDataService } from '../../src/services/extraction/master-data.service.js';
import type { LedgerSyncService } from '../../src/services/ledger/ledger-sync.service.js';
import type { StockItemSyncService } from '../../src/services/stock-item/stock-item-sync.service.js';
import type { HealthService } from '../../src/services/health/health-service.js';
import type { TallyDiagnosticsService } from '../../src/services/interfaces/tally-diagnostics.js';
import type { Logger } from '../../src/infrastructure/logging/logger.js';
import type { ApplicationContext } from '../../src/bootstrap/register-services.js';
import type { VoucherApplicationService } from '../../src/services/voucher/voucher-application.interface.js';
import type { TrustedDeviceRepository } from '../../src/services/device/trusted-device-repository.js';
import type { ConnectorIdentityRepository } from '../../src/services/identity/connector-identity-repository.js';
import type { PairingSessionRepository } from '../../src/services/pairing/pairing-session-repository.js';
import type { PairingDeviceCredentialRepository } from '../../src/services/pairing/pairing-device-credential-repository.js';
import type { ConnectorTransportIdentityService } from '../../src/services/transport/connector-transport-identity.js';

export interface StartTestServicesOptions {
  readonly selectCompanyId?: string;
}

export function createTestContext(configOverrides: RegisterServicesOptions = {}): ApplicationContext {
  return registerServices({
    env: 'test',
    logLevel: 'error',
    tallyMinRequestIntervalMs: 0,
    tallySafeMode: false,
    ...configOverrides,
  });
}

/**
 * Resolves the same dependency set `createTestApp` wires into `createExpressApp` — extracted so
 * tests that need a real `ApiServerStub` instance (not just the in-process Express app) can
 * reuse it without duplicating this resolution logic.
 */
export function resolveApiServerDeps(context: ApplicationContext): Omit<ExpressAppDeps, 'config'> {
  const healthService = context.container.resolve<HealthService>(ServiceTokens.HealthService);
  const companyDiscovery = context.container.resolve<CompanyDiscoveryService>(
    ServiceTokens.CompanyDiscovery,
  );
  const connectorSession = context.container.resolve<ConnectorSessionService>(
    ServiceTokens.ConnectorSession,
  );
  const masterData = context.container.resolve<MasterDataService>(ServiceTokens.MasterData);
  const ledgerSync = context.container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync);
  const stockItemSync = context.container.resolve<StockItemSyncService>(ServiceTokens.StockItemSync);
  const tallyDiagnostics = context.container.resolve<TallyDiagnosticsService>(
    ServiceTokens.TallyDiagnostics,
  );
  const logger = context.container.resolve<Logger>(ServiceTokens.Logger);
  const voucherApplication = context.container.resolve<VoucherApplicationService>(
    ServiceTokens.VoucherApplication,
  );
  // Only wired when LocalDatabase has already started (some tests intentionally build the app
  // before starting services, to exercise 501/503 "service not available" paths).
  let trustedDevices: TrustedDeviceRepository | undefined;
  try {
    trustedDevices = context.container.resolve<TrustedDeviceRepository>(ServiceTokens.TrustedDevices);
  } catch {
    trustedDevices = undefined;
  }
  let pairingSessions: PairingSessionRepository | undefined;
  try {
    pairingSessions = context.container.resolve<PairingSessionRepository>(ServiceTokens.PairingSessions);
  } catch {
    pairingSessions = undefined;
  }
  let pairingCredentials: PairingDeviceCredentialRepository | undefined;
  try {
    pairingCredentials = context.container.resolve<PairingDeviceCredentialRepository>(
      ServiceTokens.PairingCredentials,
    );
  } catch {
    pairingCredentials = undefined;
  }
  // Always resolvable: ConnectorIdentityRepository's constructor only stores a lazy database
  // getter closure, it never touches LocalDatabase at resolve time (unlike the repositories
  // above, which eagerly call getBundle().database and can throw before LocalDatabase starts).
  const connectorIdentity = context.container.resolve<ConnectorIdentityRepository>(
    ServiceTokens.ConnectorIdentity,
  );
  // Same reasoning as connectorIdentity above: construction only stores a directory path and
  // logger, no filesystem access until getServerCredentials()/getIdentity() is actually called.
  const transportIdentity = context.container.resolve<ConnectorTransportIdentityService>(
    ServiceTokens.TransportIdentity,
  );
  return {
    logger,
    healthService,
    companyDiscovery,
    connectorSession,
    masterData,
    ledgerSync,
    stockItemSync,
    tallyDiagnostics,
    voucherApplication,
    trustedDevices,
    connectorIdentity,
    pairingSessions,
    pairingCredentials,
    transportIdentity,
  };
}

export function createTestApp(context: ApplicationContext = createTestContext()) {
  const deps = resolveApiServerDeps(context);
  return createExpressApp({ ...deps, config: context.config });
}

export async function startTestServices(
  context: ApplicationContext,
  options: StartTestServicesOptions = {},
): Promise<void> {
  const tokens = [
    ServiceTokens.LocalDatabase,
    ServiceTokens.TallyConnection,
    ServiceTokens.XmlImport,
    ServiceTokens.CompanyDiscovery,
    ServiceTokens.ConnectorSession,
    ServiceTokens.MasterData,
    ServiceTokens.LedgerSync,
    ServiceTokens.StockItemSync,
    ServiceTokens.SyncEngine,
    ServiceTokens.Licensing,
    ServiceTokens.Scheduler,
  ] as const;

  for (const token of tokens) {
    await context.container.resolve<ServiceLifecycle>(token).start();
  }

  if (options.selectCompanyId) {
    await selectTestCompany(context, options.selectCompanyId);
  }
}

export async function selectTestCompany(
  context: ApplicationContext,
  companyId: string,
): Promise<void> {
  const session = context.container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession);
  const result = await session.selectCompany(companyId);
  if (result.status !== 'SUCCESS') {
    throw new Error(
      `Failed to select test company '${companyId}': ${result.status}${result.reason ? ` — ${result.reason}` : ''}`,
    );
  }
}
