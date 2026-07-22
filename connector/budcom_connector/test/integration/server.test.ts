import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { ServiceTokens } from '../../src/core/tokens.js';
import type { TallyConnectionService } from '../../src/services/interfaces/tally-connection.js';
import { createTallyMockFetch } from '../helpers/mock-fetch.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';

describe('GET /health', () => {
  it('returns read-only health envelope with service statuses', async () => {
    const response = await request(createTestApp()).get('/health');
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({
      status: 'unavailable',
      schemaVersion: '1.0.0',
      readOnly: true,
      connectorVersion: '0.3.1',
    });
    expect(Array.isArray(response.body.services)).toBe(true);
    expect(response.body.services.length).toBeGreaterThan(0);
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

  it('allows POST /device/pair to reach stub handler', async () => {
    const response = await request(createTestApp())
      .post('/device/pair')
      .send({ deviceId: 'dev-1', pairingCode: '123456' });
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
    expect(response.body.items).toEqual([
      {
        id: 'estimation',
        name: 'ESTIMATION',
        financialYear: '',
        baseCurrency: 'INR',
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
