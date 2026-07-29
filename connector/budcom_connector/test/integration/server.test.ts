import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { ServiceTokens } from '../../src/core/tokens.js';
import type { TallyConnectionService } from '../../src/services/interfaces/tally-connection.js';
import { createTallyMockFetch } from '../helpers/mock-fetch.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';

describe('GET /health', () => {
  it('returns read-only health envelope with loopback network binding', async () => {
    const response = await request(createTestApp()).get('/health');
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({
      status: 'unavailable',
      schemaVersion: '1.0.0',
      readOnly: true,
      connectorVersion: '0.4.0',
      bindHost: '127.0.0.1',
      networkExposure: 'loopback',
      networkPolicySatisfied: true,
      authenticatedLanAccessEnabled: false,
    });
    expect(Array.isArray(response.body.services)).toBe(true);
    expect(response.body.services.length).toBeGreaterThan(0);
    expect(response.body.startupCorrelationId).toBeNull();
  });

  it('exposes startup correlation id from config in health diagnostics', async () => {
    const context = createTestContext({ startupCorrelationId: 'probe-correlation-123' });
    await startTestServices(context);

    const response = await request(createTestApp(context)).get('/health');
    expect(response.status).toBe(200);
    expect(response.body.startupCorrelationId).toBe('probe-correlation-123');
  });

  it('reports tallyReachable when mock Tally responds', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const tally = context.container.resolve<TallyConnectionService>(ServiceTokens.TallyConnection);
    await tally.ping();

    const response = await request(createTestApp(context)).get('/health');
    expect(response.status).toBe(200);
    expect(response.body.tallyReachable).toBe(true);
  });
});

describe('GET /diagnostics/connection', () => {
  it('returns connection diagnostics snapshot', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const tally = context.container.resolve<TallyConnectionService>(ServiceTokens.TallyConnection);
    await tally.ping();

    const response = await request(createTestApp(context)).get('/diagnostics/connection');
    expect(response.status).toBe(200);
    expect(response.body.schemaVersion).toBe('1.0.0');
    expect(response.body.connection).toMatchObject({
      state: 'connected',
      host: 'localhost',
      port: 9000,
      totalRequests: 1,
    });
  });
});

describe('read-only enforcement', () => {
  it('rejects PUT requests', async () => {
    const response = await request(createTestApp()).put('/companies/1/ledgers/2');
    expect(response.status).toBe(405);
    expect(response.body.code).toBe('READ_ONLY_VIOLATION');
  });

  it('rejects DELETE requests', async () => {
    const response = await request(createTestApp()).delete('/companies/1/vouchers/2');
    expect(response.status).toBe(405);
    expect(response.body.code).toBe('READ_ONLY_VIOLATION');
  });

  it('rejects unspecified POST routes', async () => {
    const response = await request(createTestApp()).post('/companies').send({});
    expect(response.status).toBe(405);
    expect(response.body.code).toBe('READ_ONLY_VIOLATION');
  });

  it('returns 501 when device pairing repository is not configured', async () => {
    // A well-formed pairing request reaches the repository guard and gets 501
    // because the test context does not inject a TrustedDeviceRepository.
    const response = await request(createTestApp())
      .post('/device/pair')
      .send({
        companyId: 'acme-001',
        companyName: 'Acme Corp',
        installationId: 'install-0000000000000000',
      });
    expect(response.status).toBe(501);
    expect(response.body.code).toBe('NOT_IMPLEMENTED');
  });
});

describe('GET /companies', () => {
  it('returns discovered companies from mock Tally', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);

    const response = await request(createTestApp(context)).get('/companies');
    expect(response.status).toBe(200);
    expect(response.body.schemaVersion).toBe('1.0.0');
    expect(response.body.status).toBe('SUCCESS');
    expect(response.body.tallyReachable).toBe(true);
    expect(response.body.contractVersion).toBe('1');
    expect(response.body.items).toEqual([
      {
        id: 'estimation',
        name: 'ESTIMATION',
      },
    ]);
    expect(response.body.dataFreshnessAt).toBeTruthy();
  });

  it('returns 503 when company discovery service is not started', async () => {
    const response = await request(createTestApp()).get('/companies');
    expect(response.status).toBe(503);
    expect(response.body.code).toBe('SERVICE_UNAVAILABLE');
  });
});

describe('API stubs', () => {
  it('returns 501 for unimplemented ledger detail routes', async () => {
    const response = await request(createTestApp()).get('/companies/demo/ledgers/ledger-id');
    expect(response.status).toBe(501);
    expect(response.body.code).toBe('NOT_IMPLEMENTED');
  });
});

describe('validation errors', () => {
  it('returns 400 for invalid pairing request', async () => {
    const response = await request(createTestApp()).post('/device/pair').send({});
    expect(response.status).toBe(400);
    expect(response.body.code).toBe('VALIDATION_ERROR');
  });
});
