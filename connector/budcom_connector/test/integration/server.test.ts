import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { createTestApp } from '../helpers/test-context.js';

describe('GET /health', () => {
  it('returns read-only health envelope with service statuses', async () => {
    const response = await request(createTestApp()).get('/health');
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({
      status: 'unavailable',
      schemaVersion: '1.0.0',
      readOnly: true,
      connectorVersion: '0.1.0',
    });
    expect(Array.isArray(response.body.services)).toBe(true);
    expect(response.body.services.length).toBeGreaterThan(0);
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

describe('API stubs', () => {
  it('returns 501 for unimplemented read routes', async () => {
    const response = await request(createTestApp()).get('/companies');
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
