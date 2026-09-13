import { describe, expect, it } from 'vitest';
import express from 'express';
import request from 'supertest';

import { createPairingRedeemRateLimiter } from '../../../src/api/middleware/pairing-redeem-rate-limit.js';

function buildApp(options: { maxAttempts: number; windowMs: number; now: () => number }) {
  const app = express();
  app.post('/device/pairing-session/redeem', createPairingRedeemRateLimiter(options), (_req, res) =>
    res.status(200).json({ ok: true }),
  );
  return app;
}

describe('createPairingRedeemRateLimiter', () => {
  it('allows requests up to the configured limit within the window', async () => {
    const clock = 0;
    const app = buildApp({ maxAttempts: 3, windowMs: 60_000, now: () => clock });

    for (let i = 0; i < 3; i += 1) {
      const response = await request(app).post('/device/pairing-session/redeem');
      expect(response.status).toBe(200);
    }
  });

  it('returns 429 once the limit is exceeded within the same window', async () => {
    const clock = 0;
    const app = buildApp({ maxAttempts: 3, windowMs: 60_000, now: () => clock });

    for (let i = 0; i < 3; i += 1) {
      await request(app).post('/device/pairing-session/redeem');
    }
    const blocked = await request(app).post('/device/pairing-session/redeem');
    expect(blocked.status).toBe(429);
    expect(blocked.body.code).toBe('TOO_MANY_REQUESTS');
  });

  it('resets the count once the window elapses (deterministic fake clock, no real sleep)', async () => {
    let clock = 0;
    const app = buildApp({ maxAttempts: 2, windowMs: 1_000, now: () => clock });

    await request(app).post('/device/pairing-session/redeem');
    await request(app).post('/device/pairing-session/redeem');
    const blocked = await request(app).post('/device/pairing-session/redeem');
    expect(blocked.status).toBe(429);

    clock += 1_000;
    const afterWindow = await request(app).post('/device/pairing-session/redeem');
    expect(afterWindow.status).toBe(200);
  });

  it('tracks separate source addresses independently', async () => {
    const clock = 0;
    const app = express();
    app.set('trust proxy', true);
    app.post(
      '/device/pairing-session/redeem',
      createPairingRedeemRateLimiter({ maxAttempts: 1, windowMs: 60_000, now: () => clock }),
      (_req, res) => res.status(200).json({ ok: true }),
    );

    const first = await request(app)
      .post('/device/pairing-session/redeem')
      .set('X-Forwarded-For', '10.0.0.1');
    expect(first.status).toBe(200);

    const second = await request(app)
      .post('/device/pairing-session/redeem')
      .set('X-Forwarded-For', '10.0.0.2');
    expect(second.status).toBe(200);

    const thirdSameAsFirst = await request(app)
      .post('/device/pairing-session/redeem')
      .set('X-Forwarded-For', '10.0.0.1');
    expect(thirdSameAsFirst.status).toBe(429);
  });
});
