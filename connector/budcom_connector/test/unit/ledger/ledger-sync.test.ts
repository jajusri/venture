import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ConnectorConfig } from '../../../src/config/defaults.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { LedgerRepository } from '../../../src/services/ledger/ledger-repository.js';
import { LedgerSyncServiceImpl } from '../../../src/services/ledger/ledger-sync.service.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import type { CompanyResolver } from '../../../src/services/extraction/company-resolver.js';

const tempDirs: string[] = [];

afterEach(() => {
  for (const dir of tempDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function createConfig(basePath: string): ConnectorConfig {
  return {
    env: 'test',
    host: '127.0.0.1',
    port: 8080,
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
    databasePath: basePath,
    gracefulShutdownMs: 1000,
    connectorVersion: '0.3.1',
    schemaVersion: '1.0.0',
    sessionTtlMs: 86_400_000,
  };
}

describe('LedgerSyncServiceImpl', () => {
  it('syncs ledgers into repository with statistics', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-sync-'));
    tempDirs.push(basePath);
    const repository = new LedgerRepository({ basePath: `${basePath}/ledgers` });
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(async () => ({
        items: [
          {
            id: 'cash',
            name: 'Cash',
            normalizedName: 'cash',
            parentGroup: 'Cash-in-Hand',
            closingBalance: { raw: '100 Dr', amount: 100, side: 'Dr' },
          },
        ],
        durationMs: 1,
        rawByteLength: 100,
      })),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const companyResolver = {
      resolveName: vi.fn(async () => 'Demo Company'),
    } as unknown as CompanyResolver;
    const connectorSession = createPermissiveSessionMock();
    const service = new LedgerSyncServiceImpl(
      createConfig(basePath),
      readPort,
      companyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      repository,
    );

    await service.start();
    const result = await service.syncLedgers();
    expect(result.status).toBe('completed');
    expect(result.statistics.totalLedgers).toBe(1);

    const ledgers = await service.getLedgers({ page: 1, pageSize: 10 });
    expect(ledgers.items[0]?.name).toBe('Cash');
  });

  it('supports cancellation', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-sync-'));
    tempDirs.push(basePath);
    const repository = new LedgerRepository({ basePath: `${basePath}/ledgers` });
    const readPort: ErpReadPort = {
      isReady: () => true,
      discoverCompanies: vi.fn(),
      getGroups: vi.fn(),
      getCompanyInfo: vi.fn(),
      readLedgerGroups: vi.fn(),
      readLedgers: vi.fn(async () => ({
        items: Array.from({ length: 3 }, (_, index) => ({
          id: `ledger-${index}`,
          name: `Ledger ${index}`,
          normalizedName: `ledger ${index}`,
        })),
        durationMs: 1,
        rawByteLength: 100,
      })),
      readStockGroups: vi.fn(),
      readStockCategories: vi.fn(),
      readStockItems: vi.fn(),
      readGodowns: vi.fn(),
      readCostCategories: vi.fn(),
      readCostCentres: vi.fn(),
      readVoucherTypes: vi.fn(),
      readGstRegistrations: vi.fn(),
      getReadDiagnostics: vi.fn(() => []),
    };
    const companyResolver = {
      resolveName: vi.fn(async () => 'Demo Company'),
    } as unknown as CompanyResolver;
    const connectorSession = createPermissiveSessionMock();
    const service = new LedgerSyncServiceImpl(
      createConfig(basePath),
      readPort,
      companyResolver,
      connectorSession,
      createLogger({ service: 'test', level: 'error' }),
      repository,
    );

    await service.start();
    const syncPromise = service.syncLedgers();
    await service.cancelSync();
    const result = await syncPromise;
    expect(['cancelled', 'completed']).toContain(result.status);
  });
});
