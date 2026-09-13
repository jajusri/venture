import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { ServiceTokens } from '../../src/core/tokens.js';
import type { ServiceLifecycle } from '../../src/core/types.js';
import type { ConnectorIdentityRepository } from '../../src/services/identity/connector-identity-repository.js';
import type { ConnectorSessionService } from '../../src/services/interfaces/connector-session.js';
import { TrustedDeviceRepository } from '../../src/services/device/trusted-device-repository.js';
import type { SqliteStorageService } from '../../src/storage/sqlite/storage-service.js';
import { COMPANY_MULTIPLE_VALID, COMPANY_ONE_VALID } from '../helpers/company-discovery-fixtures.js';
import { createTallyMockFetch } from '../helpers/mock-fetch.js';
import { createTestApp, createTestContext } from '../helpers/test-context.js';

/**
 * TD-013 — proves the Connector-side half of the post-pairing reconnection fix: an ordinary
 * Desktop/Connector restart must not strand an already-paired customer with no company selected.
 * Every test here shares one on-disk database path across two independently constructed
 * `ApplicationContext`s to simulate a real process restart (not just a new in-memory object).
 */
describe('connector session restart (TD-013)', () => {
  function sharedDatabasePath(): string {
    return fs.mkdtempSync(path.join(os.tmpdir(), 'venture-td013-restart-'));
  }

  async function stopAndCloseDatabase(context: ReturnType<typeof createTestContext>): Promise<void> {
    const session = context.container.resolve<ServiceLifecycle>(ServiceTokens.ConnectorSession);
    await session.stop();
    const db = context.container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
    await db.stop();
  }

  async function startCore(context: ReturnType<typeof createTestContext>): Promise<void> {
    const tokens = [
      ServiceTokens.LocalDatabase,
      ServiceTokens.TallyConnection,
      ServiceTokens.XmlImport,
      ServiceTokens.CompanyDiscovery,
      ServiceTokens.ConnectorSession,
    ] as const;
    for (const token of tokens) {
      await context.container.resolve<ServiceLifecycle>(token).start();
    }
  }

  it('Scenario A: selected company, trusted device, and Connector identity all survive a full stop/start restart cycle', async () => {
    const databasePath = sharedDatabasePath();
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });

    // --- First "process": select a company, pair a trusted device, capture identity ---
    const context1 = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context1);
    const app1 = createTestApp(context1);

    const select = await request(app1).post('/session/company').send({ companyId: 'estimation' });
    expect(select.status).toBe(200);
    expect(select.body.status).toBe('SUCCESS');

    const validateBeforeRestart = await request(app1).post('/session/validate');
    expect(validateBeforeRestart.status).toBe(200);
    expect(validateBeforeRestart.body.status).toBe('SUCCESS');

    const trustedDevices1 = new TrustedDeviceRepository(
      context1.container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase).getBundle().database,
    );
    const paired = trustedDevices1.pair({
      companyId: 'estimation',
      companyName: 'ESTIMATION',
      installationId: 'install-1',
    });
    const identity1 = context1.container
      .resolve<ConnectorIdentityRepository>(ServiceTokens.ConnectorIdentity)
      .getOrCreateIdentity();

    // Normal lifecycle stop, then close the database to release the file (simulates process exit).
    await stopAndCloseDatabase(context1);

    // --- Second "process": brand-new context, same durable state on disk ---
    const context2 = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context2);
    const app2 = createTestApp(context2);

    // Identity is unchanged (trust/identity are untouched by session persistence).
    const identity2 = context2.container
      .resolve<ConnectorIdentityRepository>(ServiceTokens.ConnectorIdentity)
      .getOrCreateIdentity();
    expect(identity2.connectorId).toBe(identity1.connectorId);

    // Trusted device pairing survived untouched.
    const trustedDevices2 = new TrustedDeviceRepository(
      context2.container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase).getBundle().database,
    );
    const stillTrusted = trustedDevices2.validateToken(paired.rawToken);
    expect(stillTrusted?.deviceRecordId).toBe(paired.deviceRecordId);
    expect(stillTrusted?.revokedAt).toBeNull();

    // Selected company was restored before any session-validation traffic — no re-selection needed.
    const sessionAfterRestart = await request(app2).get('/session');
    expect(sessionAfterRestart.body.session.selectedCompany).toEqual({
      id: 'estimation',
      name: 'ESTIMATION',
    });

    const validateAfterRestart = await request(app2).post('/session/validate');
    expect(validateAfterRestart.status).toBe(200);
    expect(validateAfterRestart.body.status).toBe('SUCCESS');
    expect(validateAfterRestart.body.companyId).toBe('estimation');
  });

  it('a normal stop() (without closing the database) does not erase the durable selection an immediate restart would see', async () => {
    const databasePath = sharedDatabasePath();
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context);
    const app = createTestApp(context);

    await request(app).post('/session/company').send({ companyId: 'estimation' });

    const session = context.container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession);
    await session.stop();
    // Re-`start()` the SAME service instance — proves stop() itself never cleared the durable
    // store, independent of process/object recreation.
    await session.start();

    const snapshot = session.getSession();
    expect(snapshot.session.selectedCompany).toEqual({ id: 'estimation', name: 'ESTIMATION' });
  });

  it('renews and persists a restored same-company selection whose lease expired while Desktop was stopped', async () => {
    const databasePath = sharedDatabasePath();
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });

    const context1 = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context1);
    const app1 = createTestApp(context1);
    const selected = await request(app1).post('/session/company').send({ companyId: 'estimation' });
    expect(selected.status).toBe(200);

    const staleSelectedAt = '2026-01-01T00:00:00.000Z';
    const database1 = context1.container
      .resolve<SqliteStorageService>(ServiceTokens.LocalDatabase)
      .getBundle().database
      .getDatabase();
    database1.prepare('UPDATE storage_meta SET value = ? WHERE key = ?').run(
      JSON.stringify({
        version: 1,
        id: 'estimation',
        name: 'ESTIMATION',
        selectedAt: staleSelectedAt,
      }),
      'selected_company',
    );
    await stopAndCloseDatabase(context1);

    const context2 = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context2);
    const app2 = createTestApp(context2);

    const expired = await request(app2).post('/session/validate');
    expect(expired.status).toBe(410);
    expect(expired.body.status).toBe('SESSION_EXPIRED');

    const renewal = await request(app2).post('/session/company').send({ companyId: 'estimation' });
    expect(renewal.status).toBe(200);
    expect(renewal.body.status).toBe('DUPLICATE_SELECTION');
    expect(renewal.body.session.selectedAt).not.toBe(staleSelectedAt);

    const validation = await request(app2).post('/session/validate');
    expect(validation.status).toBe(200);
    expect(validation.body.status).toBe('SUCCESS');

    const persisted = context2.container
      .resolve<SqliteStorageService>(ServiceTokens.LocalDatabase)
      .getBundle().database
      .getDatabase()
      .prepare('SELECT value FROM storage_meta WHERE key = ?')
      .get('selected_company') as { value: string };
    expect((JSON.parse(persisted.value) as { selectedAt: string }).selectedAt).toBe(
      renewal.body.session.selectedAt,
    );
  });

  it('explicit clearSelection() clears durable state, so a subsequent restart starts with no company selected', async () => {
    const databasePath = sharedDatabasePath();
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });

    const context1 = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context1);
    const app1 = createTestApp(context1);
    await request(app1).post('/session/company').send({ companyId: 'estimation' });

    const clearResponse = await request(app1).delete('/session/company');
    expect(clearResponse.status).toBe(200);
    expect(clearResponse.body.session.selectedCompany).toBeNull();

    await stopAndCloseDatabase(context1);

    const context2 = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context2);
    const sessionAfterRestart = context2.container
      .resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession)
      .getSession();

    expect(sessionAfterRestart.session.selectedCompany).toBeNull();
  });

  it('selecting a second company persists the replacement, not the original, across a restart', async () => {
    const databasePath = sharedDatabasePath();
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_MULTIPLE_VALID });

    const context1 = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context1);
    const app1 = createTestApp(context1);

    const discovery = await request(app1).get('/companies');
    expect(discovery.status).toBe(200);
    const [companyA, companyB] = discovery.body.items as Array<{ id: string; name: string }>;
    expect(companyA.id).not.toBe(companyB.id);

    const selectA = await request(app1).post('/session/company').send({ companyId: companyA.id });
    expect(selectA.body.status).toBe('SUCCESS');
    await request(app1).delete('/session/company');
    const selectB = await request(app1).post('/session/company').send({ companyId: companyB.id });
    expect(selectB.body.status).toBe('SUCCESS');

    await stopAndCloseDatabase(context1);

    const context2 = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context2);
    const restored = context2.container
      .resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession)
      .getSession();

    expect(restored.session.selectedCompany?.id).toBe(companyB.id);
  });

  it('a Connector that legitimately starts with no persisted selection reports NO_COMPANY_SELECTED safely (defense-in-depth precondition)', async () => {
    const databasePath = sharedDatabasePath();
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1, databasePath });
    await startCore(context);
    const app = createTestApp(context);

    const response = await request(app).post('/session/validate');
    expect(response.status).toBe(400);
    expect(response.body.status).toBe('NO_COMPANY_SELECTED');
  });
});
