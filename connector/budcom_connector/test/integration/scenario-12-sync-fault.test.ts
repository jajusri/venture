import fs from 'node:fs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AppError } from '../../src/infrastructure/errors/app-error.js';
import { assessLedgerExtraction } from '../../src/erp/ledger/ledger-extraction-quality.js';
import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import { mapNormalizedStockItemToDomain } from '../../src/erp/stock-item/stock-item-mapper.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import { LedgerSyncServiceImpl } from '../../src/services/ledger/ledger-sync.service.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import type { ErpReadPort } from '../../src/erp/ports/erp-read-port.js';
import type { CompanyResolver } from '../../src/services/extraction/company-resolver.js';
import type { TallyConnectionService } from '../../src/services/interfaces/tally-connection.js';
import type { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import { SAMPLE_EMPTY_COLLECTION_RESPONSE, SAMPLE_LEDGERS_RESPONSE } from '../helpers/master-data-fixtures.js';
import {
  buildLedgersResponse,
  buildStockItemsResponse,
  FaultInjectionTallyServer,
} from '../helpers/fault-injection-server.js';
import {
  assertPrivacySafeFailure,
  createFaultTestDatabasePath,
  FAULT_TEST_COMPANY_ID,
  runLedgerSyncOnPort,
  runLedgerSyncViaFaultServer,
  runStockSyncViaFaultServer,
} from '../helpers/sync-fault-helpers.js';
import { createPermissiveSessionMock } from '../helpers/session-mock.js';
import { createTestConnectorConfig } from '../helpers/sqlite-test-storage.js';
import { registerServices } from '../../src/bootstrap/register-services.js';
import { sampleNormalizedLedger } from '../helpers/ledger-fixtures.js';
import { sampleNormalizedStockItem } from '../helpers/stock-item-fixtures.js';

const LEDGER_EXPORT_REQUEST =
  '<ENVELOPE><BODY><DATA><TALLYREQUEST>Export</TALLYREQUEST><TYPE>Collection</TYPE><ID>List of Ledgers</ID></DATA></BODY></ENVELOPE>';

const tempDirs: string[] = [];
const servers: FaultInjectionTallyServer[] = [];

afterEach(async () => {
  await Promise.all(servers.splice(0).map((server) => server.close().catch(() => undefined)));
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    } catch {
      // Windows may keep SQLite handles briefly after service shutdown.
    }
  }
});

describe('Scenario 12 deterministic sync fault matrix', () => {
  it('1. fails before response headers without domain or checkpoint mutation', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'reset-before-headers' });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runLedgerSyncViaFaultServer({ server, databasePath });
    expect(result.syncError).toBeInstanceOf(AppError);
    expect((result.syncError as AppError).statusCode).toBe(503);
    expect(result.latestRunStatus).toBe('failed');
    expect(result.latestRunProcessed).toBe(0);
    expect(result.ledgerCount).toBe(0);
    assertPrivacySafeFailure(result.syncError, result.context);
  });

  it('2. fails during response body without domain or checkpoint mutation', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({
      mode: 'partial-body-destroy',
      body: SAMPLE_LEDGERS_RESPONSE,
      partialBody: SAMPLE_LEDGERS_RESPONSE.slice(0, 160),
    });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runLedgerSyncViaFaultServer({ server, databasePath });
    expect(result.syncError).toBeInstanceOf(AppError);
    expect(result.latestRunStatus).toBe('failed');
    expect(result.latestRunProcessed).toBe(0);
    expect(result.ledgerCount).toBe(0);
    assertPrivacySafeFailure(result.syncError, result.context);
  });

  it('3. classifies body-read timeout at transport; sync leaves no mutation on fast transport fault', async () => {
    const stallServer = new FaultInjectionTallyServer();
    servers.push(stallServer);
    await stallServer.start({ mode: 'stall-after-headers', stallMs: 60_000 });

    const transport = new (await import('../../src/tally/transport/tally-http-transport.js')).TallyHttpTransport({
      config: registerServices({
        env: 'test',
        logLevel: 'error',
        tallyHost: '127.0.0.1',
        tallyPort: stallServer.port,
        tallyTimeoutMs: 300,
      }).config,
      logger: createLogger({ service: 'test', level: 'error' }),
    });
    await expect(
      transport.send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml', timeoutMs: 300 }),
    ).rejects.toMatchObject({ statusCode: 504 });

    const failServer = new FaultInjectionTallyServer();
    servers.push(failServer);
    await failServer.start({
      mode: 'partial-body-destroy',
      body: SAMPLE_LEDGERS_RESPONSE,
      partialBody: SAMPLE_LEDGERS_RESPONSE.slice(0, 160),
    });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);
    const result = await runLedgerSyncViaFaultServer({ server: failServer, databasePath });
    expect(result.latestRunStatus).toBe('failed');
    expect(result.ledgerCount).toBe(0);
  });

  it('4. fails on structurally truncated XML without mutation', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'truncated-xml', body: SAMPLE_LEDGERS_RESPONSE });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runLedgerSyncViaFaultServer({ server, databasePath });
    expect(result.syncError).toBeInstanceOf(AppError);
    expect(result.latestRunStatus).toBe('failed');
    expect(result.ledgerCount).toBe(0);
  });

  it('5. completes after full body even when endpoint closes before local mapping', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'complete-body', body: SAMPLE_LEDGERS_RESPONSE, closeAfterResponse: true });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runLedgerSyncViaFaultServer({ server, databasePath });
    expect(result.syncError).toBeNull();
    expect(result.syncStatus).toBe('completed');
    expect(result.ledgerCount).toBe(2);
    expect(server.stats.ledgerRequestCount).toBe(1);
  });

  it('6. completes local batching after endpoint closes with no further Tally requests', async () => {
    const body = buildLedgersResponse(300);
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'complete-body', body, closeAfterResponse: true });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runLedgerSyncViaFaultServer({ server, databasePath });
    expect(result.syncError).toBeNull();
    expect(result.syncStatus).toBe('completed');
    expect(result.ledgerCount).toBe(300);
    expect(server.stats.ledgerRequestCount).toBe(1);
  });

  it('7. rejects sync when Tally is unavailable before request', async () => {
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);
    const closedPortServer = new FaultInjectionTallyServer();
    servers.push(closedPortServer);
    await closedPortServer.start({ mode: 'complete-body' });
    const closedPort = closedPortServer.port;
    await closedPortServer.close();

    const result = await runLedgerSyncOnPort({ tallyPort: closedPort, databasePath, tallyTimeoutMs: 1000 });
    expect(result.syncError).toBeInstanceOf(AppError);
    expect(result.latestRunStatus).toBe('failed');
    expect(result.ledgerCount).toBe(0);
  });

  it('8. maps abort during body reception to cancellation without checkpoint mutation', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'stall-after-headers', stallMs: 5000 });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const context = registerServices({
      env: 'test',
      logLevel: 'error',
      tallyHost: '127.0.0.1',
      tallyPort: server.port,
      tallyRetryMaxAttempts: 1,
      tallyTimeoutMs: 5000,
      databasePath,
      tallyMinRequestIntervalMs: 0,
      tallyCircuitBreakerEnabled: false,
    });
    const storage = context.container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
    await storage.start();
    await context.container.resolve<TallyConnectionService>(ServiceTokens.TallyConnection).start();
    const readPort = context.container.resolve<ErpReadPort>(ServiceTokens.ErpReadPort);
    const service = new LedgerSyncServiceImpl(
      createTestConnectorConfig(databasePath),
      readPort,
      { resolveName: async () => 'Demo Company' } as unknown as CompanyResolver,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();

    const syncPromise = service.syncLedgers({ maxAttempts: 1 });
    await vi.waitFor(() => {
      expect(service.getSyncProgress().status).toBe('running');
    });
    await service.cancelSync();
    const result = await syncPromise;

    expect(result.status).toBe('cancelled');
    expect(await storage.getBundle().ledgerRepository.countByCompany(FAULT_TEST_COMPANY_ID)).toBe(0);
    const run = storage.getBundle().syncRunRepository.listRuns(FAULT_TEST_COMPANY_ID, 'ledgers', 1)[0];
    expect(run?.status).toBe('cancelled');
    expect(run?.processed).toBe(0);

    await service.stop();
    await storage.stop();
  });

  it('9. retries safely after transport failure without duplicates', async () => {
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const failServer = new FaultInjectionTallyServer();
    servers.push(failServer);
    await failServer.start({
      mode: 'partial-body-destroy',
      body: SAMPLE_LEDGERS_RESPONSE,
      partialBody: SAMPLE_LEDGERS_RESPONSE.slice(0, 120),
    });
    const failed = await runLedgerSyncViaFaultServer({ server: failServer, databasePath });
    expect(failed.latestRunStatus).toBe('failed');
    expect(failed.ledgerCount).toBe(0);

    const okServer = new FaultInjectionTallyServer();
    servers.push(okServer);
    await okServer.start({ mode: 'complete-body', body: SAMPLE_LEDGERS_RESPONSE });
    const retry = await runLedgerSyncViaFaultServer({ server: okServer, databasePath });
    expect(retry.syncStatus).toBe('completed');
    expect(retry.ledgerCount).toBe(2);
    expect(retry.predecessorSyncRunId).toBeNull();
    expect(failed.latestRunStatus).toBe('failed');
  });

  it('10. keeps failure diagnostics privacy-safe across transport faults', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'partial-body-destroy', body: SAMPLE_LEDGERS_RESPONSE });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runLedgerSyncViaFaultServer({ server, databasePath });
    assertPrivacySafeFailure(result.syncError, result.context);
  });
});

describe('Scenario 12 semantic completeness (document current behavior)', () => {
  it('11a. ledgers: fewer entities in complete envelope sync completes', async () => {
    const singleLedger = buildLedgersResponse(1);
    const assessment = assessLedgerExtraction([
      sampleNormalizedLedger({ id: 'guid:aaaaaaaa-bbbb-cccc-dddd-000000000001', name: 'Ledger 0', guid: 'aaaaaaaa-bbbb-cccc-dddd-000000000001' }),
    ]);
    expect(assessment.quality).toBe('complete');
    expect(assessment.totalRecords).toBe(1);

    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'complete-body', body: singleLedger });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runLedgerSyncViaFaultServer({ server, databasePath });
    expect(result.syncStatus).toBe('completed');
    expect(result.ledgerCount).toBe(1);
    expect(singleLedger.length).toBeLessThan(SAMPLE_LEDGERS_RESPONSE.length);
  });

  it('11b. stock items: fewer entities in complete envelope sync completes', async () => {
    const singleItem = buildStockItemsResponse(1);
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'complete-body', body: singleItem });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runStockSyncViaFaultServer({ server, databasePath });
    expect(result.syncStatus).toBe('completed');
    expect(result.stockCount).toBe(1);
  });

  it('12a. empty ledger collection is partial and sync completes', async () => {
    const assessment = assessLedgerExtraction([]);
    expect(assessment.quality).toBe('partial');
    expect(assessment.reason).toContain('No ledger records');

    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'complete-body', body: SAMPLE_EMPTY_COLLECTION_RESPONSE });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runLedgerSyncViaFaultServer({ server, databasePath });
    expect(result.syncStatus).toBe('completed');
    expect(result.ledgerCount).toBe(0);
  });

  it('12b. empty stock collection sync completes with zero rows', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'complete-body', body: SAMPLE_EMPTY_COLLECTION_RESPONSE });
    const databasePath = createFaultTestDatabasePath();
    tempDirs.push(databasePath);

    const result = await runStockSyncViaFaultServer({ server, databasePath });
    expect(result.syncStatus).toBe('completed');
    expect(result.stockCount).toBe(0);
  });

  it('documents domain mapping accepts reduced ledger set without transport error', () => {
    const mapped = mapNormalizedLedgerToDomain(
      sampleNormalizedLedger({ id: 'guid:test', name: 'Ledger 0' }),
      new Date().toISOString(),
      'partial',
    );
    expect(mapped.id).toBe('guid:test');
  });

  it('documents domain mapping accepts reduced stock set without transport error', () => {
    const mapped = mapNormalizedStockItemToDomain(
      sampleNormalizedStockItem({ id: 'guid:test', name: 'Item 0' }),
      new Date().toISOString(),
    );
    expect(mapped.id).toBe('guid:test');
  });
});
