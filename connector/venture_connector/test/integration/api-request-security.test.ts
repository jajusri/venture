import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { createTestApp } from '../helpers/test-context.js';

function assertSafeErrorBody(body: unknown): void {
  const serialized = JSON.stringify(body);
  expect(serialized).not.toMatch(/(?:^|[^A-Z])SELECT |INSERT INTO|UPDATE |DELETE FROM/i);
  expect(serialized).not.toMatch(/[A-Za-z]:\\|\/tmp\/|node_modules/i);
  expect(serialized).not.toMatch(/<ENVELOPE|<TALLYREQUEST/i);
  expect(serialized).not.toMatch(/stack|trace|Exception/i);
}

describe('connector API request security', () => {
  it('rejects unsupported HTTP methods through read-only middleware', async () => {
    const app = createTestApp();
    const response = await request(app).patch('/health');
    expect(response.status).toBe(405);
    expect(response.body.code).toBe('READ_ONLY_VIOLATION');
    assertSafeErrorBody(response.body);
  });

  it('rejects POST bodies with unsupported content type', async () => {
    const app = createTestApp();
    const response = await request(app)
      .post('/session/company')
      .set('Content-Type', 'text/plain')
      .send('company=estimation');
    expect(response.status).toBe(415);
    expect(response.body.code).toBe('UNSUPPORTED_MEDIA_TYPE');
    assertSafeErrorBody(response.body);
  });

  it('allows empty-body POST routes without content type', async () => {
    const app = createTestApp();
    const response = await request(app).post('/sync/ledgers/cancel');
    expect(response.status).toBe(200);
    assertSafeErrorBody(response.body);
    expect(response.status).not.toBe(415);
  });

  it('rejects oversized JSON request bodies', async () => {
    const app = createTestApp();
    const oversized = { payload: 'x'.repeat(80_000) };
    const response = await request(app)
      .post('/session/company')
      .set('Content-Type', 'application/json')
      .send(oversized);
    expect(response.status).toBe(413);
    expect(response.body.code).toBe('PAYLOAD_TOO_LARGE');
    assertSafeErrorBody(response.body);
  });
});
