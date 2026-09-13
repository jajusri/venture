import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import https from 'node:https';

import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';

import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';
import type { StartTestServicesOptions } from '../helpers/test-context.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import { PairingDeviceCredentialRepository } from '../../src/services/pairing/pairing-device-credential-repository.js';
import { TrustedDeviceRepository } from '../../src/services/device/trusted-device-repository.js';
import { ConnectorTransportIdentityService } from '../../src/services/transport/connector-transport-identity.js';
import type { Logger } from '../../src/infrastructure/logging/logger.js';
import type { ServiceLifecycle } from '../../src/core/types.js';
import type { RegisterServicesOptions } from '../../src/bootstrap/register-services.js';

const CONTROL_TOKEN_HEADER = 'X-Venture-Desktop-Control-Token';
const DESKTOP_TOKEN = 'test-desktop-control-token-0003';

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
 * Every context in this file uses its own isolated temp database (never the shared default
 * `./data/venture-connector.db` path) — this file exercises the "exactly one active pairing
 * session per Connector" invariant a great many times via real HTTP create+redeem cycles, and
 * vitest runs test files concurrently in separate workers; sharing the default on-disk path with
 * pairing-routes.test.ts (which relies on that same invariant) caused nondeterministic
 * cross-file session-state collisions when this file first used the shared default path.
 */
async function setupIsolatedContext(overrides: RegisterServicesOptions = {}, options: StartTestServicesOptions = {}) {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-pairing-self-test-'));
  tempDirs.push(basePath);
  const context = createTestContext({
    databasePath: basePath,
    transportIdentityDir: path.join(basePath, 'transport'),
    ...overrides,
  });
  await startTestServices(context, options);
  activeDatabases.push(context.container.resolve<ServiceLifecycle>(ServiceTokens.LocalDatabase));
  return context;
}

/** Loopback: HTTPS enforcement is a no-op (req.secure is irrelevant off-LAN), matching every other pairing route. */
async function setupLoopbackContext() {
  return setupIsolatedContext({ securePairingEnabled: true });
}

async function issueCredential(
  context: Awaited<ReturnType<typeof setupLoopbackContext>>,
  overrides: { deviceId?: string; deviceLabel?: string } = {},
) {
  const app = createTestApp(context);
  const created = await request(app).post('/device/pairing-session').send({});
  const redeemed = await request(app)
    .post('/device/pairing-session/redeem')
    .send({
      pairingSessionId: created.body.pairingSessionId,
      secret: created.body.secret,
      connectorId: created.body.connectorId,
      host: created.body.host,
      port: created.body.port,
      ...overrides,
    });
  return { token: redeemed.body.token as string, credentialId: redeemed.body.credentialId as string, secret: created.body.secret as string, shortCode: created.body.shortCode as string };
}

/** A LAN context with both securePairingEnabled and secureTransportEnabled on, real HTTP + HTTPS listeners. */
async function setupLanTransportContext() {
  const context = await setupIsolatedContext({
    host: '10.100.141.232',
    desktopControlToken: DESKTOP_TOKEN,
    securePairingEnabled: true,
    secureTransportEnabled: true,
  });
  const app = createTestApp(context);
  const transportIdentity = context.container.resolve<ConnectorTransportIdentityService>(
    ServiceTokens.TransportIdentity,
  );
  const credentials = await transportIdentity.getServerCredentials();

  const httpServer = http.createServer(app);
  const httpsServer = https.createServer({ key: credentials.key, cert: credentials.cert }, app);

  return { context, httpServer, httpsServer };
}

describe('secure local pairing — Android-facing self-status / self-revoke (feature flag off)', () => {
  it('[1] secure pairing disabled rejects self-status', async () => {
    const context = await setupIsolatedContext();
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', 'Bearer whatever');
    expect(response.status).toBe(501);
    expect(response.body.code).toBe('NOT_IMPLEMENTED');
  });

  it('[2] secure pairing disabled rejects self-revoke', async () => {
    const context = await setupIsolatedContext();
    const response = await request(createTestApp(context))
      .post('/device/pairing-credential/self/revoke')
      .set('Authorization', 'Bearer whatever')
      .send({});
    expect(response.status).toBe(501);
    expect(response.body.code).toBe('NOT_IMPLEMENTED');
  });
});

describe('secure local pairing — self-status / self-revoke authentication (loopback, flag on)', () => {
  it('[3] missing Authorization header returns generic 401', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context)).get('/device/pairing-credential/self');
    expect(response.status).toBe(401);
    expect(response.body).toEqual({ code: 'UNAUTHORIZED', message: 'The pairing credential could not be verified.' });
  });

  it('[4] malformed Authorization header returns generic 401', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', 'not-a-bearer-header');
    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
  });

  it('[5] wrong authentication scheme returns generic 401', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', 'Basic dXNlcjpwYXNz');
    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
  });

  it('[6] blank bearer token returns generic 401', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', 'Bearer ');
    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
  });

  it('[7] unknown token returns generic 401', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', 'Bearer not-a-real-token');
    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
  });

  it('[8] revoked token returns generic 401', async () => {
    const context = await setupLoopbackContext();
    const { token, credentialId } = await issueCredential(context);
    const credentials = context.container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials);
    credentials.revoke(credentialId);

    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${token}`);
    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
  });

  it('[9] credential belonging to another Connector returns generic 401', async () => {
    const context = await setupLoopbackContext();
    const credentials = context.container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials);
    const { pairingSessionId } = await (async () => {
      const app = createTestApp(context);
      const created = await request(app).post('/device/pairing-session').send({});
      return { pairingSessionId: created.body.pairingSessionId as string };
    })();
    const issued = credentials.issue({ pairingSessionId, connectorId: 'some-other-connector-id' });

    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${issued.rawToken}`);
    expect(response.status).toBe(401);
    expect(response.body.code).toBe('UNAUTHORIZED');
  });

  it('[10] every unauthorized case returns the exact same external response shape', async () => {
    const context = await setupLoopbackContext();
    const { token, credentialId } = await issueCredential(context);
    const credentials = context.container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials);
    credentials.revoke(credentialId);
    const app = createTestApp(context);

    const missing = await request(app).get('/device/pairing-credential/self');
    const malformed = await request(app).get('/device/pairing-credential/self').set('Authorization', 'garbage');
    const unknown = await request(app).get('/device/pairing-credential/self').set('Authorization', 'Bearer unknown-token');
    const revoked = await request(app).get('/device/pairing-credential/self').set('Authorization', `Bearer ${token}`);

    const shape = (r: request.Response) => ({ status: r.status, keys: Object.keys(r.body).sort() });
    expect(shape(missing)).toEqual(shape(malformed));
    expect(shape(missing)).toEqual(shape(unknown));
    expect(shape(missing)).toEqual(shape(revoked));
  });

  it('[11] a credential supplied only via query string is ignored — request is treated as unauthenticated', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .query({ token, access_token: token });
    expect(response.status).toBe(401);
  });

  it('[12] a credential supplied only via JSON body is ignored on self-revoke — request is treated as unauthenticated', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .post('/device/pairing-credential/self/revoke')
      .send({ token, credential: token });
    expect(response.status).toBe(401);
  });

  it('[13] a credential supplied only via cookie is ignored — request is treated as unauthenticated', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Cookie', `pairing_token=${token}`);
    expect(response.status).toBe(401);
  });

  it('[14] a valid active credential authenticates successfully (produces a principal)', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${token}`);
    expect(response.status).toBe(200);
    expect(response.body.credentialId).toBeTruthy();
  });

  it('[15] self-status returns exactly the sanitized field set', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context, { deviceId: 'device-self-001', deviceLabel: "Sri's Phone" });
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${token}`);

    expect(response.status).toBe(200);
    expect(Object.keys(response.body).sort()).toEqual(
      ['credentialId', 'deviceId', 'deviceLabel', 'connectorId', 'createdAt', 'lastUsedAt', 'status'].sort(),
    );
    expect(response.body.deviceId).toBe('device-self-001');
    expect(response.body.deviceLabel).toBe("Sri's Phone");
    expect(response.body.status).toBe('active');
  });

  it('[16] self-status never returns the token or token hash', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${token}`);
    const serialized = JSON.stringify(response.body);
    expect(serialized).not.toContain(token);
    expect(serialized).not.toMatch(/tokenHash|token_hash/i);
  });

  it('[17] self-status never returns the pairing secret or short code', async () => {
    const context = await setupLoopbackContext();
    const { token, secret, shortCode } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${token}`);
    const serialized = JSON.stringify(response.body);
    expect(serialized).not.toContain(secret);
    expect(serialized).not.toContain(shortCode);
  });

  it('[18] self-status never returns customer/company/accounting data', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${token}`);
    const serialized = JSON.stringify(response.body);
    expect(serialized).not.toMatch(/ledger|voucher|stock|tally|company/i);
  });

  it('[19] successful validation updates last-used time', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const app = createTestApp(context);

    const first = await request(app).get('/device/pairing-credential/self').set('Authorization', `Bearer ${token}`);
    expect(first.body.lastUsedAt).toBeTruthy();
  });

  it('[20] failed validation (wrong Connector) does not update last-used time', async () => {
    const context = await setupLoopbackContext();
    const credentials = context.container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials);
    const app = createTestApp(context);
    const created = await request(app).post('/device/pairing-session').send({});
    const issued = credentials.issue({ pairingSessionId: created.body.pairingSessionId, connectorId: 'some-other-connector-id' });

    await request(app).get('/device/pairing-credential/self').set('Authorization', `Bearer ${issued.rawToken}`);

    expect(credentials.findByToken(issued.rawToken)?.lastUsedAt).toBeNull();
  });

  it('[30] self-status does not accept the Desktop control token as device authentication', async () => {
    const context = await setupIsolatedContext({ securePairingEnabled: true, desktopControlToken: DESKTOP_TOKEN });
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN);
    expect(response.status).toBe(401);
  });
});

describe('secure local pairing — self-revoke behavior (loopback, flag on)', () => {
  it('[24] self-revoke revokes only the caller\'s own credential', async () => {
    const context = await setupLoopbackContext();
    const { token, credentialId } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .post('/device/pairing-credential/self/revoke')
      .set('Authorization', `Bearer ${token}`)
      .send({});
    expect(response.status).toBe(200);
    expect(response.body.ok).toBe(true);

    const credentials = context.container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials);
    expect(credentials.findByToken(token)?.revokedAt).not.toBeNull();
    void credentialId;
  });

  it('[25] self-revoke ignores any credentialId supplied in the request body', async () => {
    const context = await setupLoopbackContext();
    const mine = await issueCredential(context, { deviceId: 'device-mine' });
    const other = await issueCredential(context, { deviceId: 'device-other' });

    const response = await request(createTestApp(context))
      .post('/device/pairing-credential/self/revoke')
      .set('Authorization', `Bearer ${mine.token}`)
      .send({ credentialId: other.credentialId });
    expect(response.status).toBe(200);

    const credentials = context.container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials);
    expect(credentials.findByToken(mine.token)?.revokedAt).not.toBeNull();
    expect(credentials.findByToken(other.token)?.revokedAt).toBeNull();
  });

  it('[26] one device cannot revoke another device\'s credential', async () => {
    const context = await setupLoopbackContext();
    const deviceA = await issueCredential(context, { deviceId: 'device-a' });
    const deviceB = await issueCredential(context, { deviceId: 'device-b' });

    await request(createTestApp(context))
      .post('/device/pairing-credential/self/revoke')
      .set('Authorization', `Bearer ${deviceA.token}`)
      .send({ credentialId: deviceB.credentialId, deviceId: deviceB.credentialId });

    const credentials = context.container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials);
    expect(credentials.findByToken(deviceB.token)?.revokedAt).toBeNull();
  });

  it('[27] a self-revoked credential fails authentication immediately afterward', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const app = createTestApp(context);

    await request(app).post('/device/pairing-credential/self/revoke').set('Authorization', `Bearer ${token}`).send({});
    const statusAfter = await request(app).get('/device/pairing-credential/self').set('Authorization', `Bearer ${token}`);
    expect(statusAfter.status).toBe(401);
  });

  it('self-revoke also returns the generic 401 for an unauthenticated request', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context)).post('/device/pairing-credential/self/revoke').send({});
    expect(response.status).toBe(401);
  });
});

describe('secure local pairing — self-status/self-revoke LAN + HTTPS enforcement', () => {
  it('[21] LAN HTTP self-status is rejected before the credential is even checked', async () => {
    const { httpServer, httpsServer } = await setupLanTransportContext();
    const created = await request(httpServer).post('/device/pairing-session').set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN).send({});
    const redeemed = await trustLocalhost(request(httpsServer).post('/device/pairing-session/redeem')).send({
      pairingSessionId: created.body.pairingSessionId,
      secret: created.body.secret,
      connectorId: created.body.connectorId,
      host: created.body.host,
      port: created.body.port,
    });

    const overHttp = await request(httpServer)
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${redeemed.body.token}`);
    expect(overHttp.status).toBe(400);
    expect(overHttp.body.code).toBe('INSECURE_TRANSPORT');

    // Even a garbage/missing credential gets the same 400 — the transport check runs first.
    const overHttpNoAuth = await request(httpServer).get('/device/pairing-credential/self');
    expect(overHttpNoAuth.status).toBe(400);
    expect(overHttpNoAuth.body.code).toBe('INSECURE_TRANSPORT');
  });

  it('[22] LAN HTTPS self-status succeeds with a valid credential', async () => {
    const { httpServer, httpsServer } = await setupLanTransportContext();
    const created = await request(httpServer).post('/device/pairing-session').set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN).send({});
    const redeemed = await trustLocalhost(request(httpsServer).post('/device/pairing-session/redeem')).send({
      pairingSessionId: created.body.pairingSessionId,
      secret: created.body.secret,
      connectorId: created.body.connectorId,
      host: created.body.host,
      port: created.body.port,
    });

    const overHttps = await trustLocalhost(
      request(httpsServer).get('/device/pairing-credential/self'),
    ).set('Authorization', `Bearer ${redeemed.body.token}`);
    expect(overHttps.status).toBe(200);
    expect(overHttps.body.credentialId).toBe(redeemed.body.credentialId);
  });

  it('[23] a blocked insecure-HTTP self-status attempt does not mutate or revoke the credential', async () => {
    const { httpServer, httpsServer, context } = await setupLanTransportContext();
    const created = await request(httpServer).post('/device/pairing-session').set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN).send({});
    const redeemed = await trustLocalhost(request(httpsServer).post('/device/pairing-session/redeem')).send({
      pairingSessionId: created.body.pairingSessionId,
      secret: created.body.secret,
      connectorId: created.body.connectorId,
      host: created.body.host,
      port: created.body.port,
    });

    await request(httpServer).get('/device/pairing-credential/self').set('Authorization', `Bearer ${redeemed.body.token}`);
    await request(httpServer).post('/device/pairing-credential/self/revoke').set('Authorization', `Bearer ${redeemed.body.token}`).send({});

    const credentials = context.container.resolve<PairingDeviceCredentialRepository>(ServiceTokens.PairingCredentials);
    const record = credentials.findByToken(redeemed.body.token);
    expect(record?.revokedAt).toBeNull();
    expect(record?.lastUsedAt).toBeNull();
  });
});

describe('secure local pairing — administrator controls remain unchanged by the new self routes', () => {
  it('[28] GET /device/pairing-credentials (admin listing) still requires the Desktop control token on LAN', async () => {
    const context = await setupIsolatedContext({ host: '10.100.141.233', desktopControlToken: DESKTOP_TOKEN, securePairingEnabled: true });
    const response = await request(createTestApp(context)).get('/device/pairing-credentials');
    expect(response.status).toBe(403);
    expect(response.body.code).toBe('FORBIDDEN');
  });

  it('[29] POST /device/pairing-credential/revoke (admin revoke) still requires the Desktop control token on LAN', async () => {
    const context = await setupIsolatedContext({ host: '10.100.141.234', desktopControlToken: DESKTOP_TOKEN, securePairingEnabled: true });
    const response = await request(createTestApp(context))
      .post('/device/pairing-credential/revoke')
      .send({ credentialId: '00000000-0000-0000-0000-000000000000' });
    expect(response.status).toBe(403);
    expect(response.body.code).toBe('FORBIDDEN');
  });

  it('self-status cannot be used to list other devices — its response is a single sanitized object, never a list', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const response = await request(createTestApp(context))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${token}`);
    expect(Array.isArray(response.body)).toBe(false);
    expect(response.body.items).toBeUndefined();
  });
});

describe('secure local pairing — compatibility non-regression', () => {
  it('[31] legacy trusted-device authentication remains unchanged', async () => {
    const context = await setupIsolatedContext({ securePairingEnabled: true, requireDeviceAuthForLan: false });
    const trustedDevices = context.container.resolve<TrustedDeviceRepository>(ServiceTokens.TrustedDevices);
    const { rawToken } = trustedDevices.pair({
      companyId: 'acme-legacy',
      companyName: 'Acme Corp',
      installationId: 'install-legacy-0000000000',
    });
    expect(trustedDevices.validateToken(rawToken)?.companyId).toBe('acme-legacy');
  });

  it('[32] legacy POST /device/pair behaviour remains unchanged', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context)).post('/device/pair').send({
      companyId: 'acme-003',
      companyName: 'Acme Corp',
      installationId: 'install-2222222222222222',
    });
    expect(response.status).toBe(201);
    expect(response.body.token).toBeTruthy();
  });

  it('[33] existing business routes remain unchanged', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context)).get('/session');
    expect(response.status).not.toBe(401);
  });

  it('[34] feature defaults remain unchanged', async () => {
    const context = createTestContext();
    expect(context.config.securePairingEnabled).toBe(false);
    expect(context.config.secureTransportEnabled).toBe(false);
    expect(context.config.requireDeviceAuthForLan).toBe(false);
  });

  it('[36] credential validation survives a Connector restart through persisted hash data', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-pairing-self-restart-'));
    tempDirs.push(basePath);
    const first = createTestContext({ securePairingEnabled: true, databasePath: basePath, transportIdentityDir: path.join(basePath, 'transport') });
    await startTestServices(first);
    activeDatabases.push(first.container.resolve<ServiceLifecycle>(ServiceTokens.LocalDatabase));
    const { token } = await issueCredential(first);
    await first.container.resolve<ServiceLifecycle>(ServiceTokens.LocalDatabase).stop();
    activeDatabases.pop();

    const second = createTestContext({ securePairingEnabled: true, databasePath: basePath, transportIdentityDir: path.join(basePath, 'transport') });
    await startTestServices(second);
    activeDatabases.push(second.container.resolve<ServiceLifecycle>(ServiceTokens.LocalDatabase));

    const response = await request(createTestApp(second))
      .get('/device/pairing-credential/self')
      .set('Authorization', `Bearer ${token}`);
    expect(response.status).toBe(200);
  });

  it('[39] no Android or Desktop paths change — the self routes are additive only, existing pairing-bootstrap fields are untouched', async () => {
    const context = await setupLoopbackContext();
    const response = await request(createTestApp(context)).post('/device/pairing-session').send({});
    expect(Object.keys(response.body).sort()).toEqual(
      ['schemaVersion', 'pairingSessionId', 'connectorId', 'connectorName', 'host', 'port', 'expiresAt', 'secret', 'shortCode'].sort(),
    );
  });

  it('[35] no raw token or token hash appears in logs for self-status or self-revoke', async () => {
    const context = await setupLoopbackContext();
    const logger = context.container.resolve<Logger>(ServiceTokens.Logger);
    const entries: unknown[] = [];
    const capture = (message: string, meta?: Record<string, unknown>) => {
      entries.push({ message, meta });
    };
    logger.info = capture;
    logger.warn = capture;
    logger.error = capture;

    const { token } = await issueCredential(context);
    const app = createTestApp(context);
    await request(app).get('/device/pairing-credential/self').set('Authorization', `Bearer ${token}`);
    await request(app).post('/device/pairing-credential/self/revoke').set('Authorization', `Bearer ${token}`).send({});

    const serializedLogs = JSON.stringify(entries);
    expect(serializedLogs).not.toContain(token);
  });

  it('read-only middleware still allows the new self/revoke POST route and blocks other mutations', async () => {
    const context = await setupLoopbackContext();
    const { token } = await issueCredential(context);
    const app = createTestApp(context);

    const revoke = await request(app).post('/device/pairing-credential/self/revoke').set('Authorization', `Bearer ${token}`).send({});
    expect(revoke.status).toBe(200);

    const disallowed = await request(app).put('/device/pairing-credential/self');
    expect(disallowed.status).toBe(405);
  });
});
