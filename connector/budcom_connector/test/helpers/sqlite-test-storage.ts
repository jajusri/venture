import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import type { ConnectorConfig } from '../../src/config/defaults.js';
import { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';

const tempDirs: string[] = [];
const activeStorage: SqliteStorageService[] = [];

export function createTestConnectorConfig(basePath: string): ConnectorConfig {
  return {
    env: 'test',
    host: '127.0.0.1',
    port: 8080,
    networkExposure: 'loopback',
    networkExposureWarning: null,
    lanModeAcknowledged: false,
    logLevel: 'error',
    tallyHost: '127.0.0.1',
    tallyPort: 9000,
    tallyTimeoutMs: 1000,
    tallyPoolMaxConnections: 1,
    tallyRetryMaxAttempts: 2,
    tallyRetryBaseDelayMs: 1,
    tallyRetryMaxDelayMs: 5,
    tallyRetryJitterRatio: 0,
    tallyAutoReconnect: false,
    tallyReconnectDelayMs: 1000,
    tallySafeMode: false,
    tallyMinRequestIntervalMs: 0,
    tallyMaxRequestBytes: 1_000_000,
    tallyMaxResponseBytes: 5_000_000,
    tallyCircuitBreakerEnabled: false,
    tallyCircuitBreakerFailureThreshold: 3,
    tallyCircuitBreakerCooldownMs: 1000,
    tallyRequestAuditEnabled: false,
    tallyRequestAuditPath: './audit.jsonl',
    tallyRequestAuditMaxBytes: 10 * 1024 * 1024,
    tallyRequestAuditMaxFiles: 5,
    databasePath: basePath,
    gracefulShutdownMs: 1000,
    connectorVersion: '0.3.1',
    schemaVersion: '1.0.0',
    sessionTtlMs: 86_400_000,
    syncRunHistoryMaxCount: 100,
    syncRunHistoryMaxAgeDays: 90,
    startupCorrelationId: null,
    requireDeviceAuthForLan: false,
    desktopControlToken: null,
    securePairingEnabled: false,
    secureTransportEnabled: false,
    secureTransportPort: 8443,
    transportIdentityDir: path.join(basePath, 'transport'),
    secureLanRouteProtectionEnabled: false,
  };
}

export async function createTestSqliteStorage(): Promise<{
  readonly storage: SqliteStorageService;
  readonly basePath: string;
}> {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-sqlite-test-'));
  tempDirs.push(basePath);
  const config = createTestConnectorConfig(basePath);
  const storage = new SqliteStorageService(config, {
    info: () => {},
    warn: () => {},
    error: () => {},
    debug: () => {},
    child: () => ({
      info: () => {},
      warn: () => {},
      error: () => {},
      debug: () => {},
      child: () => ({} as never),
    }),
  } as never);
  activeStorage.push(storage);
  await storage.start();
  return { storage, basePath };
}

export async function cleanupTestSqliteStorage(): Promise<void> {
  for (const storage of activeStorage.splice(0)) {
    await storage.stop();
  }
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 50 });
    } catch {
      // Windows may keep WAL files briefly after close.
    }
  }
}
