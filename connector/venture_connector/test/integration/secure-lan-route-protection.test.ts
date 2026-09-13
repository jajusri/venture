import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import https from 'node:https';

import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';

import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import { ConnectorTransportIdentityService } from '../../src/services/transport/connector-transport-identity.js';
import type { ServiceLifecycle } from '../../src/core/types.js';

/**
 * Proves the dormant Phase 3Q secure LAN business-route policy (secureLanRouteProtectionEnabled)
 * against the REAL production app assembly (server.ts's createExpressApp, via createTestApp) and
 * REAL TLS transport — not a `req.secure` simulation — mirroring the existing pattern in
 * pairing-lan-transport-security.test.ts. The flag itself stays false everywhere else in the
 * suite (see test/helpers/sqlite-test-storage.ts); this file is the only place it is ever turned
 * on, and only against an isolated, throwaway in-memory-style temp database.
 */

const CONTROL_TOKEN_HEADER = 'X-Venture-Desktop-Control-Token';
const DESKTOP_TOKEN = 'test-desktop-control-token-3q02';

function trustLocalhost<T extends request.Test>(req: T): T {
  return (req as unknown as { trustLocalhost: () => T }).trustLocalhost();
}

const activeDatabases: ServiceLifecycle[] = [];
const tempDirs: string[] = [];

afterEach(async () => {
  for (const db of activeDatabases.splice(0)) {
    await db.stop();
  }
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 50 });
    } catch {
      // Windows may keep WAL/identity file handles briefly after close.
    }
  }
});

async function setupContext(secureLanRouteProtectionEnabled: boolean) {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-secure-lan-route-test-'));
  tempDirs.push(basePath);
  const context = createTestContext({
    host: '10.100.141.234',
    desktopControlToken: DESKTOP_TOKEN,
    securePairingEnabled: true,
    secureTransportEnabled: true,
    secureLanRouteProtectionEnabled,
    databasePath: basePath,
    transportIdentityDir: path.join(basePath, 'transport'),
  });
  await startTestServices(context);
  activeDatabases.push(context.container.resolve<ServiceLifecycle>(ServiceTokens.LocalDatabase));
  const app = createTestApp(context);
  const transportIdentity = context.container.resolve<ConnectorTransportIdentityService>(
    ServiceTokens.TransportIdentity,
  );
  const credentials = await transportIdentity.getServerCredentials();

  const httpServer = http.createServer(app);
  const httpsServer = https.createServer({ key: credentials.key, cert: credentials.cert }, app);

  return { context, httpServer, httpsServer };
}

async function issuePairingToken(httpServer: http.Server, httpsServer: https.Server): Promise<string> {
  const created = await request(httpServer)
    .post('/device/pairing-session')
    .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
    .send({});

  const redeemed = await trustLocalhost(
    request(httpsServer).post('/device/pairing-session/redeem'),
  ).send({
    pairingSessionId: created.body.pairingSessionId,
    secret: created.body.secret,
    connectorId: created.body.connectorId,
    host: created.body.host,
    port: created.body.port,
  });

  return redeemed.body.token as string;
}

async function pairLegacyToken(httpServer: http.Server): Promise<string> {
  const response = await request(httpServer).post('/device/pair').send({
    companyId: 'acme-001',
    companyName: 'Acme Corp',
    installationId: 'install-0000000000000000',
  });
  return response.body.token as string;
}

describe('secureLanRouteProtectionEnabled — real TLS end-to-end wiring', () => {
  it('rejects a plaintext GET /companies before authenticating, over a real (non-TLS) socket', async () => {
    const { httpServer } = await setupContext(true);

    const response = await request(httpServer).get('/companies');

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('INSECURE_TRANSPORT');
  });

  it('rejects an HTTPS GET /companies with no credential', async () => {
    const { httpsServer } = await setupContext(true);

    const response = await trustLocalhost(request(httpsServer).get('/companies'));

    expect(response.status).toBe(401);
  });

  it('accepts a valid pairing credential over real HTTPS for a business route', async () => {
    const { httpServer, httpsServer } = await setupContext(true);
    const token = await issuePairingToken(httpServer, httpsServer);

    const response = await trustLocalhost(
      request(httpsServer).get('/companies').set('Authorization', `Bearer ${token}`),
    );

    expect(response.status).not.toBe(401);
    expect(response.status).not.toBe(400);
  });

  it('accepts a valid legacy trusted-device credential over real HTTPS for a business route', async () => {
    const { httpServer, httpsServer } = await setupContext(true);
    const token = await pairLegacyToken(httpServer);

    const response = await trustLocalhost(
      request(httpsServer).get('/companies').set('Authorization', `Bearer ${token}`),
    );

    expect(response.status).not.toBe(401);
    expect(response.status).not.toBe(400);
  });

  it('protects both duplicate voucher-listing paths identically', async () => {
    const { httpServer, httpsServer } = await setupContext(true);
    const token = await issuePairingToken(httpServer, httpsServer);

    const listUnauth = await trustLocalhost(request(httpsServer).get('/api/v1/vouchers'));
    const searchUnauth = await trustLocalhost(request(httpsServer).get('/api/v1/vouchers/search'));
    expect(listUnauth.status).toBe(401);
    expect(searchUnauth.status).toBe(401);

    const listAuth = await trustLocalhost(
      request(httpsServer).get('/api/v1/vouchers').set('Authorization', `Bearer ${token}`),
    );
    const searchAuth = await trustLocalhost(
      request(httpsServer).get('/api/v1/vouchers/search').set('Authorization', `Bearer ${token}`),
    );
    expect(listAuth.status).not.toBe(401);
    expect(searchAuth.status).not.toBe(401);
  });

  it('protects every reserved (501) stub route before returning NOT_IMPLEMENTED', async () => {
    const { httpServer, httpsServer } = await setupContext(true);
    const token = await issuePairingToken(httpServer, httpsServer);

    const stubRoutes = [
      '/companies/acme-001/ledgers/ledger-1',
      '/companies/acme-001/ledger-transactions',
      '/companies/acme-001/vouchers',
      '/companies/acme-001/vouchers/voucher-1',
      '/sync/checkpoint',
    ];

    for (const route of stubRoutes) {
      const unauth = await trustLocalhost(request(httpsServer).get(route));
      expect(unauth.status, `${route} should require auth`).toBe(401);

      const auth = await trustLocalhost(request(httpsServer).get(route).set('Authorization', `Bearer ${token}`));
      expect(auth.status, `${route} should be reachable once authenticated`).toBe(501);
    }
  });

  it('GET /device/list requires only the Desktop control token, never a device credential', async () => {
    const { httpServer, httpsServer } = await setupContext(true);
    const token = await issuePairingToken(httpServer, httpsServer);

    const withDeviceCredentialOnly = await trustLocalhost(
      request(httpsServer).get('/device/list').set('Authorization', `Bearer ${token}`),
    );
    expect(withDeviceCredentialOnly.status).toBe(403);

    const withDesktopTokenOnly = await trustLocalhost(
      request(httpsServer).get('/device/list').set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN),
    );
    expect(withDesktopTokenOnly.status).toBe(200);
  });

  it('GET /device/trusted-companies rejects a pairing credential but accepts a legacy trusted-device credential', async () => {
    const { httpServer, httpsServer } = await setupContext(true);
    const pairingToken = await issuePairingToken(httpServer, httpsServer);
    const legacyToken = await pairLegacyToken(httpServer);

    const withPairingCredential = await trustLocalhost(
      request(httpsServer)
        .get('/device/trusted-companies')
        .query({ installationId: 'install-0000000000000000' })
        .set('Authorization', `Bearer ${pairingToken}`),
    );
    expect(withPairingCredential.status).toBe(403);

    const withLegacyCredential = await trustLocalhost(
      request(httpsServer)
        .get('/device/trusted-companies')
        .query({ installationId: 'install-0000000000000000' })
        .set('Authorization', `Bearer ${legacyToken}`),
    );
    expect(withLegacyCredential.status).toBe(200);
  });

  it('zero default behavior change: GET /companies stays reachable with no credential over plain HTTP while the flag is off', async () => {
    const { httpServer } = await setupContext(false);

    const response = await request(httpServer).get('/companies');

    expect(response.status).not.toBe(401);
    expect(response.status).not.toBe(400);
  });
});
