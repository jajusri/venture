import { afterEach, describe, expect, it } from 'vitest';
import express from 'express';
import request from 'supertest';

import { createRequireTrustedDeviceAuthMiddleware } from '../../../src/api/middleware/require-trusted-device-auth.js';
import {
  createDeviceManagementRouter,
  createDevicePairingRouter,
} from '../../../src/api/routes/device.js';
import { TrustedDeviceRepository } from '../../../src/services/device/trusted-device-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

/**
 * Proves the device-route disclosure found in review is closed: GET /device/list and
 * GET /device/trusted-companies previously sat entirely before requireTrustedDeviceAuth
 * (public, unconditionally) and returned company names/IDs and the full paired-device
 * inventory to any LAN caller, even with requireDeviceAuthForLan=true. They now live in
 * createDeviceManagementRouter, mounted after the gate — see server.ts.
 *
 * Pairing bootstrap (createDevicePairingRouter: POST /device/pair, POST
 * /device/validate-token) stays public by necessity and is covered here only to prove it
 * discloses nothing to a caller who doesn't already hold a valid token.
 */
async function buildApp(config: { networkExposure: 'loopback' | 'lan'; requireDeviceAuthForLan: boolean }) {
  const { storage } = await createTestSqliteStorage();
  const trustedDevices = new TrustedDeviceRepository(storage.getBundle().database);
  const app = express();
  app.use(express.json());
  app.use(createDevicePairingRouter(trustedDevices));
  app.use(createRequireTrustedDeviceAuthMiddleware({ config, trustedDevices }));
  app.use(createDeviceManagementRouter(trustedDevices));
  return { app, trustedDevices };
}

function pairOneDevice(trustedDevices: TrustedDeviceRepository) {
  return trustedDevices.pair({
    companyId: 'acme-001',
    companyName: 'Acme Corp',
    installationId: 'install-0000000000000000',
  });
}

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('device-management routes require trusted-device auth when enforcement is enabled', () => {
  it('GET /device/list is rejected with no credentials on LAN with enforcement enabled', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
    pairOneDevice(trustedDevices);

    const response = await request(app).get('/device/list');

    expect(response.status).toBe(401);
    expect(response.body.items).toBeUndefined();
  });

  it('GET /device/trusted-companies is rejected with no credentials on LAN with enforcement enabled', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
    pairOneDevice(trustedDevices);

    const response = await request(app)
      .get('/device/trusted-companies')
      .query({ installationId: 'install-0000000000000000' });

    expect(response.status).toBe(401);
    expect(response.body.items).toBeUndefined();
  });

  it('rejects an unknown/invalid token on the management routes', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
    pairOneDevice(trustedDevices);

    const response = await request(app)
      .get('/device/list')
      .set('Authorization', 'Bearer not-a-real-token');

    expect(response.status).toBe(401);
  });

  it('rejects a revoked device token on the management routes', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
    const { rawToken, deviceRecordId } = pairOneDevice(trustedDevices);
    trustedDevices.revoke(deviceRecordId);

    const response = await request(app)
      .get('/device/list')
      .set('Authorization', `Bearer ${rawToken}`);

    expect(response.status).toBe(401);
  });

  it('rejects a malformed Authorization header on the management routes', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
    pairOneDevice(trustedDevices);

    const response = await request(app)
      .get('/device/list')
      .set('Authorization', 'not-a-bearer-header');

    expect(response.status).toBe(401);
  });

  it(
    'a valid trusted-device token can reach the management routes — coarse-grained by design: ' +
      'any paired device, not scoped to its own company. Documented gap, not a fix in this pass.',
    async () => {
      const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
      const { rawToken } = pairOneDevice(trustedDevices);

      const response = await request(app)
        .get('/device/list')
        .set('Authorization', `Bearer ${rawToken}`);

      expect(response.status).toBe(200);
      expect(response.body.items).toHaveLength(1);
    },
  );

  it('stays a pass-through on loopback regardless of the flag — Desktop\'s own management access is unaffected', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'loopback', requireDeviceAuthForLan: true });
    pairOneDevice(trustedDevices);

    const response = await request(app).get('/device/list');

    expect(response.status).toBe(200);
    expect(response.body.items).toHaveLength(1);
  });

  it('preserves today\'s open-on-LAN behavior while the global enforcement flag stays false (default)', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: false });
    pairOneDevice(trustedDevices);

    const response = await request(app).get('/device/list');

    expect(response.status).toBe(200);
  });
});

describe('pairing-bootstrap routes disclose nothing to a caller without a valid token', () => {
  it('POST /device/validate-token with an unknown token returns no company/device data', async () => {
    const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
    pairOneDevice(trustedDevices);

    const response = await request(app)
      .post('/device/validate-token')
      .send({ token: 'not-a-real-token' });

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ valid: false, reason: expect.any(String) });
    expect(response.body.companyId).toBeUndefined();
    expect(response.body.companyName).toBeUndefined();
    expect(response.body.deviceRecordId).toBeUndefined();
  });

  it(
    'discovery alone (no token) cannot enumerate companies — validate-token and the ' +
      'management routes both refuse an anonymous caller',
    async () => {
      const { app, trustedDevices } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });
      pairOneDevice(trustedDevices);

      const validate = await request(app).post('/device/validate-token').send({ token: 'guessed' });
      const list = await request(app).get('/device/list');
      const trustedCompanies = await request(app)
        .get('/device/trusted-companies')
        .query({ installationId: 'install-0000000000000000' });

      expect(validate.body.companyId).toBeUndefined();
      expect(list.status).toBe(401);
      expect(trustedCompanies.status).toBe(401);
    },
  );

  it('POST /device/pair only ever echoes back what the caller itself supplied — never an existing company lookup', async () => {
    const { app } = await buildApp({ networkExposure: 'lan', requireDeviceAuthForLan: true });

    const response = await request(app).post('/device/pair').send({
      companyId: 'caller-supplied-id',
      companyName: 'Caller Supplied Name',
      installationId: 'install-0000000000000000',
    });

    expect(response.status).toBe(201);
    expect(response.body.companyId).toBe('caller-supplied-id');
    expect(response.body.companyName).toBe('Caller Supplied Name');
  });
});
