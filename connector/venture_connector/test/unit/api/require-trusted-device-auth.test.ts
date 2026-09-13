import { afterEach, describe, expect, it } from 'vitest';
import express from 'express';
import request from 'supertest';

import { createRequireTrustedDeviceAuthMiddleware } from '../../../src/api/middleware/require-trusted-device-auth.js';
import { TrustedDeviceRepository } from '../../../src/services/device/trusted-device-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

async function buildApp(config: { networkExposure: 'loopback' | 'lan'; requireDeviceAuthForLan: boolean }) {
  const { storage } = await createTestSqliteStorage();
  const trustedDevices = new TrustedDeviceRepository(storage.getBundle().database);
  const app = express();
  app.use(createRequireTrustedDeviceAuthMiddleware({ config, trustedDevices }));
  app.get('/companies', (_req, res) => res.json({ items: [] }));
  return { app, trustedDevices };
}

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('requireTrustedDeviceAuth middleware', () => {
  it('is a pass-through when networkExposure is loopback, regardless of the flag (today\'s default behavior)', async () => {
    const { app } = await buildApp({ networkExposure: 'loopback', requireDeviceAuthForLan: true });

    const response = await request(app).get('/companies');

    expect(response.status).toBe(200);
  });

  it('is a pass-through on LAN when requireDeviceAuthForLan is false (the shipped default)', async () => {
    const { app } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: false });

    const response = await request(app).get('/companies');

    expect(response.status).toBe(200);
  });

  it('rejects requests with no Authorization header when enabled on LAN', async () => {
    const { app } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });

    const response = await request(app).get('/companies');

    expect(response.status).toBe(401);
  });

  it('rejects a malformed Authorization header (missing the Bearer scheme) when enabled on LAN', async () => {
    const { app } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });

    const response = await request(app)
      .get('/companies')
      .set('Authorization', 'not-a-bearer-header');

    expect(response.status).toBe(401);
  });

  it('rejects an unpaired/invalid token when enabled on LAN — accounting data stays out of reach', async () => {
    const { app } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });

    const response = await request(app)
      .get('/companies')
      .set('Authorization', 'Bearer not-a-real-token');

    expect(response.status).toBe(401);
  });

  it('accepts a valid, non-revoked paired-device token when enabled on LAN', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
    const { rawToken } = trustedDevices.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });

    const response = await request(app)
      .get('/companies')
      .set('Authorization', `Bearer ${rawToken}`);

    expect(response.status).toBe(200);
  });

  it('rejects a revoked device token when enabled on LAN', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
    const { rawToken, deviceRecordId } = trustedDevices.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });
    trustedDevices.revoke(deviceRecordId);

    const response = await request(app)
      .get('/companies')
      .set('Authorization', `Bearer ${rawToken}`);

    expect(response.status).toBe(401);
  });
});
