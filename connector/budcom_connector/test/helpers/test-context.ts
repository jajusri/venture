import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

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
import type { AdaptiveScheduler } from '../../src/services/scheduler/adaptive-scheduler.service.js';
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

/**
 * Where every `createTestContext()` call (across every forked worker process vitest's
 * `pool: 'forks'` runs test files under) records the default temp directory it minted, one JSON
 * line per directory. Deliberately a shared on-disk file, not an in-memory array: an in-process
 * `afterAll` cannot remove these directories reliably — the SQLite file inside is still open in
 * that same (still-alive) worker process, and Windows (unlike POSIX) refuses to delete an
 * open file no matter how long a same-process retry waits. `test/helpers/global-teardown.ts`
 * (wired into `globalTeardown`) runs in the main vitest process only after every worker has fully
 * exited, so every handle is guaranteed released by then — see that file for the actual removal.
 * Before this existed, every call leaked its directory outright — ~79 call sites across 20 test
 * files, the single largest source of the accumulated OS-temp scratch-directory buildup found
 * during the public-release hygiene audit.
 */
const TRACKING_FILE = path.join(os.tmpdir(), 'budcom-connector-test-tracked-dirs.jsonl');

function trackTempDir(dir: string): void {
  try {
    fs.appendFileSync(TRACKING_FILE, `${JSON.stringify({ dir })}\n`, 'utf8');
  } catch {
    // Best-effort tracking only — worst case this directory is missed by the teardown sweep,
    // exactly the pre-existing (leaky) behavior; must never fail the test that triggered it.
  }
}

export function createTestContext(configOverrides: RegisterServicesOptions = {}): ApplicationContext {
  const usesDefaultDatabasePath = configOverrides.databasePath === undefined;
  const databasePath = configOverrides.databasePath
    ?? fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-connector-test-'));
  if (usesDefaultDatabasePath) {
    trackTempDir(databasePath);
  }
  return registerServices({
    env: 'test',
    logLevel: 'error',
    tallyMinRequestIntervalMs: 0,
    tallySafeMode: false,
    // Each context gets its own on-disk database unless the caller overrides it below. Selected
    // company is now durably persisted (TD-013), so sharing the default './data' path across
    // contexts — harmless while selection was in-memory-only — would leak state between tests.
    databasePath,
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
  // Like connectorIdentity/transportIdentity below: SchedulerStateRepository only stores a lazy
  // database-getter closure at construction time, so resolving it never requires LocalDatabase to
  // have already started.
  const scheduler = context.container.resolve<AdaptiveScheduler>(ServiceTokens.Scheduler);
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
    scheduler,
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
