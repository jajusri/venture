import { afterEach, describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';

import { createRequireBusinessRouteAuthMiddleware } from '../../../src/api/middleware/require-business-route-auth.js';
import { TrustedDeviceRepository } from '../../../src/services/device/trusted-device-repository.js';
import { PairingDeviceCredentialRepository } from '../../../src/services/pairing/pairing-device-credential-repository.js';
import { PairingSessionRepository } from '../../../src/services/pairing/pairing-session-repository.js';
import { ConnectorIdentityRepository } from '../../../src/services/identity/connector-identity-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

interface BuildAppConfig {
  networkExposure: 'loopback' | 'lan';
  requireDeviceAuthForLan: boolean;
  secureLanRouteProtectionEnabled: boolean;
}

/**
 * `trust proxy` + `X-Forwarded-Proto` is a standard, safe way to simulate an HTTPS connection in
 * a supertest-driven unit test — this is a throwaway Express instance created only inside this
 * test file, never the production app assembled in server.ts (which never sets `trust proxy`,
 * so this technique has no bearing on production spoof-resistance). Real end-to-end HTTPS
 * enforcement against the actual production app is covered separately in
 * test/integration/secure-lan-route-protection.test.ts using a genuine TLS listener.
 */
function markSecure(req: request.Test): request.Test {
  return req.set('X-Forwarded-Proto', 'https');
}

async function buildApp(config: BuildAppConfig, routePath = '/companies') {
  const { storage } = await createTestSqliteStorage();
  const db = storage.getBundle().database;
  const trustedDevices = new TrustedDeviceRepository(db);
  const pairingCredentials = new PairingDeviceCredentialRepository(db);
  const pairingSessions = new PairingSessionRepository(db);
  const connectorIdentity = new ConnectorIdentityRepository(() => db, {});
  const handler = vi.fn((req: express.Request, res: express.Response) => {
    res.json({ ok: true, principal: req.deviceAuthPrincipal ?? null });
  });

  const app = express();
  app.set('trust proxy', true);
  app.use(express.json());
  app.use(
    createRequireBusinessRouteAuthMiddleware({ config, trustedDevices, pairingCredentials, connectorIdentity }),
  );
  app.get(routePath, handler);
  app.delete(routePath, handler);

  return { app, trustedDevices, pairingCredentials, pairingSessions, connectorIdentity, handler };
}

function issuePairingCredential(
  pairingCredentials: PairingDeviceCredentialRepository,
  connectorIdentity: ConnectorIdentityRepository,
  pairingSessions: PairingSessionRepository,
) {
  const identity = connectorIdentity.getOrCreateIdentity();
  const session = pairingSessions.create({
    connectorId: identity.connectorId,
    connectorName: identity.connectorName,
    host: '127.0.0.1',
    port: 8080,
  });
  const redeemed = pairingSessions.redeemBySessionId(session.pairingSessionId, session.secret, {
    connectorId: session.connectorId,
    host: session.host,
    port: session.port,
  });
  if (redeemed.kind !== 'redeemed') throw new Error('setup: expected redemption to succeed');
  return pairingCredentials.issue({ pairingSessionId: redeemed.pairingSessionId, connectorId: identity.connectorId });
}

function pairLegacyDevice(trustedDevices: TrustedDeviceRepository) {
  return trustedDevices.pair({
    companyId: 'acme-001',
    companyName: 'Acme Corp',
    installationId: 'install-0000000000000000',
  });
}

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('createRequireBusinessRouteAuthMiddleware — flag off (default)', () => {
  it('delegates to legacy pass-through behavior on loopback regardless of requireDeviceAuthForLan', async () => {
    const { app } = await buildApp({
      networkExposure: 'loopback',
      requireDeviceAuthForLan: true,
      secureLanRouteProtectionEnabled: false,
    });

    const response = await request(app).get('/companies');
    expect(response.status).toBe(200);
  });

  it('delegates to legacy pass-through on LAN while requireDeviceAuthForLan is false', async () => {
    const { app } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: false,
    });

    const response = await request(app).get('/companies');
    expect(response.status).toBe(200);
  });

  it('delegates to legacy enforcement on LAN while requireDeviceAuthForLan is true — an old-style legacy token is accepted', async () => {
    const { app, trustedDevices } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: true,
      secureLanRouteProtectionEnabled: false,
    });
    const { rawToken } = pairLegacyDevice(trustedDevices);

    const unauthenticated = await request(app).get('/companies');
    expect(unauthenticated.status).toBe(401);

    const authenticated = await request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`);
    expect(authenticated.status).toBe(200);
  });

  it('does not require HTTPS while the flag is off, even on LAN with legacy enforcement on', async () => {
    const { app, trustedDevices } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: true,
      secureLanRouteProtectionEnabled: false,
    });
    const { rawToken } = pairLegacyDevice(trustedDevices);

    const response = await request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`);
    expect(response.status).toBe(200);
  });
});

describe('createRequireBusinessRouteAuthMiddleware — flag on', () => {
  it('passes through unconditionally on loopback', async () => {
    const { app, handler } = await buildApp({
      networkExposure: 'loopback',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });

    const response = await request(app).get('/companies');
    expect(response.status).toBe(200);
    expect(handler).toHaveBeenCalledOnce();
  });

  it('rejects a plaintext LAN request before inspecting any credential', async () => {
    const { app, handler, pairingCredentials, pairingSessions, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken } = issuePairingCredential(pairingCredentials, connectorIdentity, pairingSessions);
    const findByTokenSpy = vi.spyOn(pairingCredentials, 'findByToken');

    const response = await request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`);

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('INSECURE_TRANSPORT');
    expect(handler).not.toHaveBeenCalled();
    expect(findByTokenSpy).not.toHaveBeenCalled();
  });

  it('rejects an HTTPS LAN request with no Authorization header — generic 401', async () => {
    const { app, handler } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });

    const response = await markSecure(request(app).get('/companies'));

    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
    expect(handler).not.toHaveBeenCalled();
  });

  it('rejects a malformed Bearer header — generic 401, same shape as missing header', async () => {
    const { app } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });

    const response = await markSecure(request(app).get('/companies').set('Authorization', 'not-a-bearer-header'));

    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
  });

  it('ignores a token presented only as a query parameter', async () => {
    const { app, pairingCredentials, pairingSessions, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken } = issuePairingCredential(pairingCredentials, connectorIdentity, pairingSessions);

    const response = await markSecure(request(app).get('/companies').query({ token: rawToken }));
    expect(response.status).toBe(401);
  });

  it('ignores a token presented only in the request body', async () => {
    const { app, pairingCredentials, pairingSessions, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken } = issuePairingCredential(pairingCredentials, connectorIdentity, pairingSessions);

    const response = await markSecure(request(app).delete('/companies').send({ token: rawToken }));
    expect(response.status).toBe(401);
  });

  it('ignores a token presented only as a cookie', async () => {
    const { app, pairingCredentials, pairingSessions, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken } = issuePairingCredential(pairingCredentials, connectorIdentity, pairingSessions);

    const response = await markSecure(
      request(app).get('/companies').set('Cookie', `token=${rawToken}`),
    );
    expect(response.status).toBe(401);
  });

  it('accepts a valid pairing credential and attaches a PairingCredentialPrincipal', async () => {
    const { app, pairingCredentials, pairingSessions, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken } = issuePairingCredential(pairingCredentials, connectorIdentity, pairingSessions);

    const response = await markSecure(
      request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`),
    );

    expect(response.status).toBe(200);
    expect(response.body.principal.kind).toBe('pairing-credential');
  });

  it('accepts a valid legacy trusted-device credential and attaches a LegacyTrustedDevicePrincipal', async () => {
    const { app, trustedDevices } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken } = pairLegacyDevice(trustedDevices);

    const response = await markSecure(
      request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`),
    );

    expect(response.status).toBe(200);
    expect(response.body.principal.kind).toBe('trusted-device');
  });

  it('rejects a revoked pairing credential', async () => {
    const { app, pairingCredentials, pairingSessions, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken, credentialId } = issuePairingCredential(pairingCredentials, connectorIdentity, pairingSessions);
    pairingCredentials.revoke(credentialId);

    const response = await markSecure(
      request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`),
    );
    expect(response.status).toBe(401);
  });

  it('rejects a revoked legacy trusted-device credential', async () => {
    const { app, trustedDevices } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken, deviceRecordId } = pairLegacyDevice(trustedDevices);
    trustedDevices.revoke(deviceRecordId);

    const response = await markSecure(
      request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`),
    );
    expect(response.status).toBe(401);
  });

  it('rejects a pairing credential issued for a different Connector', async () => {
    const { app, pairingCredentials, pairingSessions, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const identity = connectorIdentity.getOrCreateIdentity();
    const session = pairingSessions.create({
      connectorId: identity.connectorId,
      connectorName: identity.connectorName,
      host: '127.0.0.1',
      port: 8080,
    });
    const redeemed = pairingSessions.redeemBySessionId(session.pairingSessionId, session.secret, {
      connectorId: session.connectorId,
      host: session.host,
      port: session.port,
    });
    if (redeemed.kind !== 'redeemed') throw new Error('setup: expected redemption to succeed');
    const { rawToken } = pairingCredentials.issue({
      pairingSessionId: redeemed.pairingSessionId,
      connectorId: 'some-other-connector-id',
    });

    const response = await markSecure(
      request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`),
    );
    expect(response.status).toBe(401);
  });

  it('rejects a completely unknown token', async () => {
    const { app } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });

    const response = await markSecure(
      request(app).get('/companies').set('Authorization', 'Bearer completely-unknown-token'),
    );
    expect(response.status).toBe(401);
  });

  it('fails closed with a generic 401 when the required repositories are not wired up', async () => {
    const { storage } = await createTestSqliteStorage();
    const db = storage.getBundle().database;
    const connectorIdentity = new ConnectorIdentityRepository(() => db, {});
    const app = express();
    app.set('trust proxy', true);
    app.use(
      createRequireBusinessRouteAuthMiddleware({
        config: { networkExposure: 'lan', requireDeviceAuthForLan: false, secureLanRouteProtectionEnabled: true },
        connectorIdentity,
      }),
    );
    app.get('/companies', (_req, res) => res.json({ ok: true }));

    const response = await markSecure(request(app).get('/companies'));
    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
  });

  it('performs at most one credential validation per request', async () => {
    const { app, pairingCredentials, pairingSessions, trustedDevices, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });
    const { rawToken } = issuePairingCredential(pairingCredentials, connectorIdentity, pairingSessions);
    const findByTokenSpy = vi.spyOn(pairingCredentials, 'findByToken');
    const validateTokenSpy = vi.spyOn(trustedDevices, 'validateToken');

    const response = await markSecure(
      request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`),
    );

    expect(response.status).toBe(200);
    expect(findByTokenSpy).toHaveBeenCalledOnce();
    expect(validateTokenSpy).not.toHaveBeenCalled();
  });

  it('old legacy flag being true too does not cause double authentication — the legacy gate is never invoked', async () => {
    const { app, trustedDevices, pairingCredentials, pairingSessions, connectorIdentity } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: true,
      secureLanRouteProtectionEnabled: true,
    });
    const validateTokenSpy = vi.spyOn(trustedDevices, 'validateToken');
    const { rawToken } = issuePairingCredential(pairingCredentials, connectorIdentity, pairingSessions);

    const response = await markSecure(
      request(app).get('/companies').set('Authorization', `Bearer ${rawToken}`),
    );

    expect(response.status).toBe(200);
    // The pairing credential satisfied DeviceCredentialAuthenticator's first (pairing) branch;
    // the legacy trusted-device repository is never consulted for it.
    expect(validateTokenSpy).not.toHaveBeenCalled();
  });

  it('auth failure never invokes the downstream handler (and therefore never reaches Tally/DB access)', async () => {
    const { app, handler } = await buildApp({
      networkExposure: 'lan',
      requireDeviceAuthForLan: false,
      secureLanRouteProtectionEnabled: true,
    });

    await markSecure(request(app).get('/companies'));
    expect(handler).not.toHaveBeenCalled();
  });

  it('exempts GET /device/list from its own device-credential check — no credential and no HTTPS-first bypass, but next() is still called after the HTTPS check', async () => {
    const { app, handler, pairingCredentials, trustedDevices } = await buildApp(
      {
        networkExposure: 'lan',
        requireDeviceAuthForLan: false,
        secureLanRouteProtectionEnabled: true,
      },
      '/device/list',
    );
    const findByTokenSpy = vi.spyOn(pairingCredentials, 'findByToken');
    const validateTokenSpy = vi.spyOn(trustedDevices, 'validateToken');

    const response = await markSecure(request(app).get('/device/list'));

    expect(response.status).toBe(200);
    expect(handler).toHaveBeenCalledOnce();
    expect(findByTokenSpy).not.toHaveBeenCalled();
    expect(validateTokenSpy).not.toHaveBeenCalled();
  });

  it('still requires HTTPS for the exempted GET /device/list route', async () => {
    const { app, handler } = await buildApp(
      {
        networkExposure: 'lan',
        requireDeviceAuthForLan: false,
        secureLanRouteProtectionEnabled: true,
      },
      '/device/list',
    );

    const response = await request(app).get('/device/list');
    expect(response.status).toBe(400);
    expect(response.body.code).toBe('INSECURE_TRANSPORT');
    expect(handler).not.toHaveBeenCalled();
  });

  it('exempts DELETE /device/:deviceRecordId from its own device-credential check', async () => {
    const { app, handler } = await buildApp(
      {
        networkExposure: 'lan',
        requireDeviceAuthForLan: false,
        secureLanRouteProtectionEnabled: true,
      },
      '/device/abc123',
    );

    const response = await markSecure(request(app).delete('/device/abc123'));
    expect(response.status).toBe(200);
    expect(handler).toHaveBeenCalledOnce();
  });

  it('exempts the pairing-credential admin routes (already Desktop-control-token gated) from its own device-credential check', async () => {
    const { app, handler } = await buildApp(
      {
        networkExposure: 'lan',
        requireDeviceAuthForLan: false,
        secureLanRouteProtectionEnabled: true,
      },
      '/device/pairing-credentials',
    );

    const response = await markSecure(request(app).get('/device/pairing-credentials'));
    expect(response.status).toBe(200);
    expect(handler).toHaveBeenCalledOnce();
  });
});
