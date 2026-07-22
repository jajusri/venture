import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { createApp } from '../src/server.js';

describe('GET /health', () => {
  it('returns read-only health envelope', async () => {
    const response = await request(createApp()).get('/health');
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({
      status: 'ok',
      schemaVersion: '1.0.0',
      readOnly: true,
    });
  });
});

describe('read-only enforcement', () => {
  it('rejects PUT requests', async () => {
    const response = await request(createApp()).put('/companies/1/ledgers/2');
    expect(response.status).toBe(405);
    expect(response.body.code).toBe('READ_ONLY_VIOLATION');
  });

  it('rejects DELETE requests', async () => {
    const response = await request(createApp()).delete('/companies/1/vouchers/2');
    expect(response.status).toBe(405);
    expect(response.body.code).toBe('READ_ONLY_VIOLATION');
  });

  it('rejects unspecified POST routes', async () => {
    const response = await request(createApp()).post('/companies').send({});
    expect(response.status).toBe(405);
    expect(response.body.code).toBe('READ_ONLY_VIOLATION');
  });

  it('allows POST /device/pair to reach stub handler', async () => {
    const response = await request(createApp())
      .post('/device/pair')
      .send({ deviceId: 'dev-1', pairingCode: '123456' });
    expect(response.status).toBe(501);
    expect(response.body.code).toBe('NOT_IMPLEMENTED');
  });
});

describe('API stubs', () => {
  it('returns 501 for unimplemented read routes', async () => {
    const response = await request(createApp()).get('/companies');
    expect(response.status).toBe(501);
    expect(response.body.code).toBe('NOT_IMPLEMENTED');
  });
});
