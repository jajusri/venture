import { describe, expect, it } from 'vitest';
import express from 'express';
import request from 'supertest';

import { createRequireDesktopControlTokenMiddleware } from '../../../src/api/middleware/require-desktop-control-token.js';

function buildApp(config: { networkExposure: 'loopback' | 'lan'; desktopControlToken: string | null }) {
  const app = express();
  app.use(createRequireDesktopControlTokenMiddleware({ config }));
  app.post('/device/pairing-session', (_req, res) => res.status(201).json({ ok: true }));
  return app;
}

describe('requireDesktopControlToken middleware', () => {
  it('is a pass-through when networkExposure is loopback, regardless of the token', async () => {
    const app = buildApp({ networkExposure: 'loopback', desktopControlToken: 'secret-token' });
    const response = await request(app).post('/device/pairing-session');
    expect(response.status).toBe(201);
  });

  it('fails closed on LAN when no control token is configured at all', async () => {
    const app = buildApp({ networkExposure: 'lan', desktopControlToken: null });
    const response = await request(app)
      .post('/device/pairing-session')
      .set('X-Budcom-Desktop-Control-Token', 'anything');
    expect(response.status).toBe(403);
  });

  it('rejects a request with no header on LAN', async () => {
    const app = buildApp({ networkExposure: 'lan', desktopControlToken: 'secret-token' });
    const response = await request(app).post('/device/pairing-session');
    expect(response.status).toBe(403);
  });

  it('rejects a request with the wrong token on LAN', async () => {
    const app = buildApp({ networkExposure: 'lan', desktopControlToken: 'secret-token' });
    const response = await request(app)
      .post('/device/pairing-session')
      .set('X-Budcom-Desktop-Control-Token', 'wrong-token');
    expect(response.status).toBe(403);
  });

  it('accepts a request with the correct token on LAN', async () => {
    const app = buildApp({ networkExposure: 'lan', desktopControlToken: 'secret-token' });
    const response = await request(app)
      .post('/device/pairing-session')
      .set('X-Budcom-Desktop-Control-Token', 'secret-token');
    expect(response.status).toBe(201);
  });

  it('rejects tokens that differ only in length without throwing', async () => {
    const app = buildApp({ networkExposure: 'lan', desktopControlToken: 'secret-token' });
    const response = await request(app)
      .post('/device/pairing-session')
      .set('X-Budcom-Desktop-Control-Token', 'secret-token-with-extra-suffix');
    expect(response.status).toBe(403);
  });
});
