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

const CONTROL_TOKEN_HEADER = 'X-Budcom-Desktop-Control-Token';
const DESKTOP_TOKEN = 'test-desktop-control-token-0002';

/**
 * @types/supertest does not model superagent's `trustLocalhost()` (it disables strict TLS
 * verification for 127.0.0.1/localhost requests only — see superagent/lib/node/index.js). The
 * method exists and works at runtime; this narrow cast is only to satisfy the type checker.
 */
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

/**
 * A LAN-exposed context with both securePairingEnabled and secureTransportEnabled turned on,
 * wrapping the SAME Express app (see createTestApp) in a real http.Server and a real
 * https.Server bound to the Connector's own generated transport identity — proving the
 * insecure-transport guard and the pinned-HTTPS payload fields against real TLS-terminated
 * requests, not just a `req.secure` mock.
 *
 * Uses its own isolated temp database/transport-identity directory rather than
 * createTestContext()'s shared default path: this file's tests create a pairing session and
 * redeem it moments later, and "creating a session cancels any other active session for the
 * same Connector" — sharing the default database with the rest of the suite's many
 * session-creating tests makes that cancellation race against unrelated tests non-deterministic.
 */
async function setupLanTransportContext() {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-lan-transport-test-'));
  tempDirs.push(basePath);
  const context = createTestContext({
    host: '10.100.141.231',
    desktopControlToken: DESKTOP_TOKEN,
    securePairingEnabled: true,
    secureTransportEnabled: true,
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

describe('pinned-HTTPS pairing payload and LAN transport enforcement', () => {
  it('pairing-session creation includes the pinned-HTTPS fields when secure transport is enabled', async () => {
    const { httpServer } = await setupLanTransportContext();

    const created = await request(httpServer)
      .post('/device/pairing-session')
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
      .send({});

    expect(created.status).toBe(201);
    expect(created.body.transportProtocol).toBe('https');
    expect(typeof created.body.securePort).toBe('number');
    expect(created.body.transportFingerprint).toMatch(/^sha256\//);
    expect(created.body.fingerprintAlgorithm).toBe('sha256');
    expect(created.body.transportIdentityVersion).toBe(1);
    // No private key or any other secret introduced by the transport fields themselves.
    const serialized = JSON.stringify(created.body);
    expect(serialized).not.toContain('BEGIN PRIVATE KEY');
  });

  it('LAN redemption over plain HTTP cannot issue a permanent credential while secure transport is enabled', async () => {
    const { httpServer } = await setupLanTransportContext();

    const created = await request(httpServer)
      .post('/device/pairing-session')
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
      .send({});

    const insecureRedeem = await request(httpServer)
      .post('/device/pairing-session/redeem')
      .send({
        pairingSessionId: created.body.pairingSessionId,
        secret: created.body.secret,
        connectorId: created.body.connectorId,
        host: created.body.host,
        port: created.body.port,
      });

    expect(insecureRedeem.status).toBe(400);
    expect(insecureRedeem.body.code).toBe('INSECURE_TRANSPORT');
    expect(insecureRedeem.body.token).toBeUndefined();
    expect(insecureRedeem.body.credentialId).toBeUndefined();
  });

  it('a blocked insecure-HTTP redemption attempt does not consume the one-time session — a subsequent HTTPS redemption with the same secret still succeeds', async () => {
    const { httpServer, httpsServer } = await setupLanTransportContext();

    const created = await request(httpServer)
      .post('/device/pairing-session')
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
      .send({});

    const insecureRedeem = await request(httpServer)
      .post('/device/pairing-session/redeem')
      .send({
        pairingSessionId: created.body.pairingSessionId,
        secret: created.body.secret,
        connectorId: created.body.connectorId,
        host: created.body.host,
        port: created.body.port,
      });
    expect(insecureRedeem.status).toBe(400);

    const secureRedeem = await trustLocalhost(request(httpsServer)
      .post('/device/pairing-session/redeem'))
      .send({
        pairingSessionId: created.body.pairingSessionId,
        secret: created.body.secret,
        connectorId: created.body.connectorId,
        host: created.body.host,
        port: created.body.port,
      });

    expect(secureRedeem.status).toBe(201);
    expect(secureRedeem.body.ok).toBe(true);
    expect(secureRedeem.body.token).toBeTruthy();
    expect(secureRedeem.body.credentialId).toBeTruthy();
  });

  it('LAN redemption over the real pinned HTTPS listener succeeds and issues a credential', async () => {
    const { httpServer, httpsServer } = await setupLanTransportContext();

    const created = await request(httpServer)
      .post('/device/pairing-session')
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
      .send({});

    const response = await trustLocalhost(request(httpsServer)
      .post('/device/pairing-session/redeem'))
      .send({
        pairingSessionId: created.body.pairingSessionId,
        secret: created.body.secret,
        connectorId: created.body.connectorId,
        host: created.body.host,
        port: created.body.port,
      });

    expect(response.status).toBe(201);
    expect(response.body.token).toBeTruthy();
  });

  it('the short-code redemption path is also subject to the insecure-transport guard over plain HTTP on LAN', async () => {
    const { httpServer } = await setupLanTransportContext();

    const created = await request(httpServer)
      .post('/device/pairing-session')
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
      .send({});

    const insecureRedeem = await request(httpServer)
      .post('/device/pairing-session/redeem')
      .send({ shortCode: created.body.shortCode });

    expect(insecureRedeem.status).toBe(400);
    expect(insecureRedeem.body.code).toBe('INSECURE_TRANSPORT');
  });
});

describe('administrator revoke requires the Desktop-control token even though requireDeviceAuthForLan is false', () => {
  it('revoke on LAN without the control token fails, independent of requireDeviceAuthForLan', async () => {
    const { httpServer, httpsServer } = await setupLanTransportContext();

    const created = await request(httpServer)
      .post('/device/pairing-session')
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
      .send({});
    const redeemed = await trustLocalhost(request(httpsServer)
      .post('/device/pairing-session/redeem'))
      .send({
        pairingSessionId: created.body.pairingSessionId,
        secret: created.body.secret,
        connectorId: created.body.connectorId,
        host: created.body.host,
        port: created.body.port,
      });
    expect(redeemed.status).toBe(201);

    const revokeWithoutToken = await trustLocalhost(request(httpsServer)
      .post('/device/pairing-credential/revoke'))
      .send({ credentialId: redeemed.body.credentialId });

    expect(revokeWithoutToken.status).toBe(403);
    expect(revokeWithoutToken.body.code).toBe('FORBIDDEN');
  });

  it('revoke on LAN with the correct control token succeeds', async () => {
    const { httpServer, httpsServer } = await setupLanTransportContext();

    const created = await request(httpServer)
      .post('/device/pairing-session')
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
      .send({});
    const redeemed = await trustLocalhost(request(httpsServer)
      .post('/device/pairing-session/redeem'))
      .send({
        pairingSessionId: created.body.pairingSessionId,
        secret: created.body.secret,
        connectorId: created.body.connectorId,
        host: created.body.host,
        port: created.body.port,
      });

    const revokeWithToken = await trustLocalhost(request(httpsServer)
      .post('/device/pairing-credential/revoke'))
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
      .send({ credentialId: redeemed.body.credentialId });

    expect(revokeWithToken.status).toBe(200);
    expect(revokeWithToken.body.ok).toBe(true);
  });
});
