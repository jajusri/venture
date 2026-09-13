import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import type { ApplicationContext } from '../../src/bootstrap/register-services.js';
import { registerServices } from '../../src/bootstrap/register-services.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import {
  mapUnknownConnectorError,
  sanitizeConnectionDiagnostic,
  sanitizeConnectorDiagnosticText,
} from '../../src/diagnostics/diagnostic-allowlist.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { StockItemSyncServiceImpl } from '../../src/services/stock-item/stock-item-sync.service.js';
import type { TallyDiagnosticsService } from '../../src/services/interfaces/tally-diagnostics.js';
import type { TallyConnectionService } from '../../src/services/interfaces/tally-connection.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import type { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import { createPermissiveSessionMock } from './session-mock.js';
import { createTestConnectorConfig } from './sqlite-test-storage.js';
import type { FaultInjectionTallyServer } from './fault-injection-server.js';

export const FAULT_TEST_COMPANY_ID = 'estimation';

const PRIVACY_SENTINELS = [
  'JAJU SANITATIONS',
  'Sensitive Debtor',
  'sk-live-',
  '29AABCU9603R1ZM',
  '<LEDGER NAME=',
  'C:\\Users\\',
  'Documents\\',
] as const;

export interface SyncFaultRunResult {
  readonly context: ApplicationContext;
  readonly databasePath: string;
  readonly syncError: unknown;
  readonly syncStatus: string | null;
  readonly ledgerCount: number;
  readonly stockCount: number;
  readonly latestRunStatus: string | null;
  readonly latestRunProcessed: number;
  readonly predecessorSyncRunId: string | null;
}

export function createFaultTestDatabasePath(): string {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'venture-scenario12-'));
}

function companyResolver(name = 'Demo Company'): CompanyResolver {
  return { resolveName: async () => name } as unknown as CompanyResolver;
}

async function startFaultSyncStack(options: {
  readonly server?: FaultInjectionTallyServer;
  readonly tallyPort: number;
  readonly databasePath: string;
  readonly tallyTimeoutMs?: number;
  readonly tallyRetryMaxAttempts?: number;
}): Promise<{
  readonly context: ApplicationContext;
  readonly storage: SqliteStorageService;
  readonly readPort: ErpReadPort;
}> {
  const context = registerServices({
    env: 'test',
    logLevel: 'error',
    tallyHost: '127.0.0.1',
    tallyPort: options.tallyPort,
    tallyRetryMaxAttempts: options.tallyRetryMaxAttempts ?? 1,
    tallyTimeoutMs: options.tallyTimeoutMs ?? 2000,
    tallyMinRequestIntervalMs: 0,
    tallyCircuitBreakerEnabled: false,
    databasePath: options.databasePath,
  });

  const storage = context.container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
  await storage.start();
  await context.container.resolve<TallyConnectionService>(ServiceTokens.TallyConnection).start();

  return {
    context,
    storage,
    readPort: context.container.resolve<ErpReadPort>(ServiceTokens.ErpReadPort),
  };
}

export async function runLedgerSyncViaFaultServer(options: {
  readonly server: FaultInjectionTallyServer;
  readonly databasePath: string;
  readonly tallyTimeoutMs?: number;
  readonly tallyRetryMaxAttempts?: number;
}): Promise<SyncFaultRunResult> {
  const { context, storage, readPort } = await startFaultSyncStack({
    server: options.server,
    tallyPort: options.server.port,
    databasePath: options.databasePath,
    tallyTimeoutMs: options.tallyTimeoutMs,
    tallyRetryMaxAttempts: options.tallyRetryMaxAttempts,
  });

  const service = new LedgerSyncServiceImpl(
    createTestConnectorConfig(options.databasePath),
    readPort,
    companyResolver(),
    createPermissiveSessionMock(),
    createLogger({ service: 'test', level: 'error' }),
    storage,
  );
  await service.start();

  let syncError: unknown = null;
  let syncStatus: string | null = null;
  try {
    const result = await service.syncLedgers({ maxAttempts: options.tallyRetryMaxAttempts ?? 1 });
    syncStatus = result.status;
  } catch (error) {
    syncError = error;
    syncStatus = (await service.getSyncProgress()).status;
  }

  await service.stop();

  const latestRun = storage.getBundle().syncRunRepository.listRuns(FAULT_TEST_COMPANY_ID, 'ledgers', 1)[0] ?? null;
  const ledgerCount = await storage.getBundle().ledgerRepository.countByCompany(FAULT_TEST_COMPANY_ID);
  const stockCount = await storage.getBundle().stockItemRepository.countByCompany(FAULT_TEST_COMPANY_ID);

  await storage.stop();

  return {
    context,
    databasePath: options.databasePath,
    syncError,
    syncStatus,
    ledgerCount,
    stockCount,
    latestRunStatus: latestRun?.status ?? null,
    latestRunProcessed: latestRun?.processed ?? 0,
    predecessorSyncRunId: latestRun?.predecessorSyncRunId ?? null,
  };
}

export async function runStockSyncViaFaultServer(options: {
  readonly server: FaultInjectionTallyServer;
  readonly databasePath: string;
  readonly tallyTimeoutMs?: number;
}): Promise<SyncFaultRunResult> {
  const { context, storage, readPort } = await startFaultSyncStack({
    server: options.server,
    tallyPort: options.server.port,
    databasePath: options.databasePath,
    tallyTimeoutMs: options.tallyTimeoutMs,
  });

  const service = new StockItemSyncServiceImpl(
    createTestConnectorConfig(options.databasePath),
    readPort,
    companyResolver(),
    createPermissiveSessionMock(),
    createLogger({ service: 'test', level: 'error' }),
    storage,
  );
  await service.start();

  let syncError: unknown = null;
  let syncStatus: string | null = null;
  try {
    const result = await service.syncStockItems({ maxAttempts: 1 });
    syncStatus = result.status;
  } catch (error) {
    syncError = error;
    syncStatus = (await service.getSyncProgress()).status;
  }

  await service.stop();

  const latestRun = storage.getBundle().syncRunRepository.listRuns(FAULT_TEST_COMPANY_ID, 'stock-items', 1)[0] ?? null;
  const ledgerCount = await storage.getBundle().ledgerRepository.countByCompany(FAULT_TEST_COMPANY_ID);
  const stockCount = await storage.getBundle().stockItemRepository.countByCompany(FAULT_TEST_COMPANY_ID);

  await storage.stop();

  return {
    context,
    databasePath: options.databasePath,
    syncError,
    syncStatus,
    ledgerCount,
    stockCount,
    latestRunStatus: latestRun?.status ?? null,
    latestRunProcessed: latestRun?.processed ?? 0,
    predecessorSyncRunId: latestRun?.predecessorSyncRunId ?? null,
  };
}

export async function runLedgerSyncOnPort(options: {
  readonly tallyPort: number;
  readonly databasePath: string;
  readonly tallyTimeoutMs?: number;
}): Promise<SyncFaultRunResult> {
  const { context, storage, readPort } = await startFaultSyncStack({
    tallyPort: options.tallyPort,
    databasePath: options.databasePath,
    tallyTimeoutMs: options.tallyTimeoutMs,
  });

  const service = new LedgerSyncServiceImpl(
    createTestConnectorConfig(options.databasePath),
    readPort,
    companyResolver(),
    createPermissiveSessionMock(),
    createLogger({ service: 'test', level: 'error' }),
    storage,
  );
  await service.start();

  let syncError: unknown = null;
  let syncStatus: string | null = null;
  try {
    const result = await service.syncLedgers({ maxAttempts: 1 });
    syncStatus = result.status;
  } catch (error) {
    syncError = error;
    syncStatus = (await service.getSyncProgress()).status;
  }

  await service.stop();

  const latestRun = storage.getBundle().syncRunRepository.listRuns(FAULT_TEST_COMPANY_ID, 'ledgers', 1)[0] ?? null;
  const ledgerCount = await storage.getBundle().ledgerRepository.countByCompany(FAULT_TEST_COMPANY_ID);
  const stockCount = await storage.getBundle().stockItemRepository.countByCompany(FAULT_TEST_COMPANY_ID);

  await storage.stop();

  return {
    context,
    databasePath: options.databasePath,
    syncError,
    syncStatus,
    ledgerCount,
    stockCount,
    latestRunStatus: latestRun?.status ?? null,
    latestRunProcessed: latestRun?.processed ?? 0,
    predecessorSyncRunId: latestRun?.predecessorSyncRunId ?? null,
  };
}

export function collectPrivacySafeFailurePayload(error: unknown, context: ApplicationContext): string {
  const mapped = mapUnknownConnectorError(error);
  const diagnostics = context.container.resolve<TallyDiagnosticsService>(ServiceTokens.TallyDiagnostics);
  const connection = sanitizeConnectionDiagnostic(diagnostics.getConnectionDiagnostics());
  return JSON.stringify({
    mapped,
    connection,
    message: sanitizeConnectorDiagnosticText(error instanceof Error ? error.message : String(error)),
  }).toLowerCase();
}

export function assertPrivacySafeFailure(error: unknown, context: ApplicationContext): void {
  const serialized = collectPrivacySafeFailurePayload(error, context);
  for (const sentinel of PRIVACY_SENTINELS) {
    if (serialized.includes(sentinel.toLowerCase())) {
      throw new Error(`Privacy sentinel leaked in failure output: ${sentinel}`);
    }
  }
}
