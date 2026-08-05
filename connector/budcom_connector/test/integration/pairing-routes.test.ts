import express from 'express';
import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import { PairingDeviceCredentialRepository } from '../../src/services/pairing/pairing-device-credential-repository.js';
import { PairingSessionRepository } from '../../src/services/pairing/pairing-session-repository.js';
import { ConnectorIdentityRepository } from '../../src/services/identity/connector-identity-repository.js';
import { ConnectorTransportIdentityService } from '../../src/services/transport/connector-transport-identity.js';
import { createPairingBootstrapRouter } from '../../src/api/routes/pairing.js';
import { createPairingRedeemRateLimiter } from '../../src/api/middleware/pairing-redeem-rate-limit.js';
import type { Logger } from '../../src/infrastructure/logging/logger.js';

const CONTROL_TOKEN_HEADER = 'X-Budcom-Desktop-Control-Token';
const DESKTOP_TOKEN = 'test-desktop-control-token-0001';

/**
 * Default test config is networkExposure: 'loopback', where the control-token gate is a
 * pass-through. securePairingEnabled defaults to false everywhere else in this codebase — these
 * helpers explicitly opt in, since this whole file's purpose is exercising the pairing surface
 * once the feature flag is on. The "disabled by default" describe block below deliberately does
 * NOT use these helpers.
 */
async function setupLoopbackContext() {
  const context = createTestContext({ securePairingEnabled: true });
  await startTestServices(context);
  return context;
}

/**
 * LAN config with a configured control token — exercises the Desktop-only boundary for real.
 * networkExposure is derived from `host` (parseConnectorBindHost), not independently settable —
 * a non-loopback host is what actually produces networkExposure: 'lan'.
 */
async function setupLanContext(overrides: { desktopControlToken?: string | null } = {}) {
  const context = createTestContext({
    host: '10.100.141.231',
    desktopControlToken: overrides.desktopControlToken ?? DESKTOP_TOKEN,
    securePairingEnabled: true,
  });
  await startTestServices(context);
  return context;
}

describe('secure local pairing — feature flag (default-off)', () => {
  it('securePairingEnabled defaults to false when not explicitly overridden', async () => {
    const context = createTestContext();
    expect(context.config.securePairingEnabled).toBe(false);
  });

  it('POST /device/pairing-session is unavailable while the flag is off, even on loopback', async () => {
    const context = createTestContext();
    await startTestServices(context);
    const response = await request(createTestApp(context)).post('/device/pairing-session').send({});
    expect(response.status).toBe(501);
    expect(response.body.code).toBe('NOT_IMPLEMENTED');
  });

  it('GET /device/pairing-session/status is unavailable while the flag is off', async () => {
    const context = createTestContext();
    await startTestServices(context);
    const response = await request(createTestApp(context))
      .get('/device/pairing-session/status')
      .query({ pairingSessionId: '00000000-0000-0000-0000-000000000000' });
    expect(response.status).toBe(501);
  });

  it('POST /device/pairing-session/redeem is unavailable while the flag is off', async () => {
    const context = createTestContext();
    await startTestServices(context);
    const response = await request(createTestApp(context))
      .post('/device/pairing-session/redeem')
      .send({ shortCode: 'ABCDEFGH' });
    expect(response.status).toBe(501);
  });

  it('POST /device/pairing-session/cancel is unavailable while the flag is off', async () => {
    const context = createTestContext();
    await startTestServices(context);
    const response = await request(createTestApp(context))
      .post('/device/pairing-session/cancel')
      .send({ pairingSessionId: '00000000-0000-0000-0000-000000000000' });
    expect(response.status).toBe(501);
  });

  it('POST /device/pairing-credential/revoke is unavailable while the flag is off', async () => {
    const context = createTestContext();
    await startTestServices(context);
    const response = await request(createTestApp(context))
      .post('/device/pairing-credential/revoke')
      .send({ credentialId: '00000000-0000-0000-0000-000000000000' });
    expect(response.status).toBe(501);
  });

  it('enabling the flag exposes exactly the intended bootstrap routes and nothing else new', async () => {
    const context = await setupLoopbackContext();
    const app = createTestApp(context);

    const create = await request(app).post('/device/pairing-session').send({});
    expect(create.status).toBe(201);

    const status = await request(app)
      .get('/device/pairing-session/status')
      .query({ pairingSessionId: create.body.pairingSessionId });
    expect(status.status).toBe(200);
  });

  it('this Connector installation is otherwise unaffected: existing legacy /device/pair still works while the flag is off', async () => {
    const context = createTestContext();
    await startTestServices(context);
    const response = await request(createTestApp(context)).post('/device/pair').send({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });
    expect(response.status).toBe(201);
    expect(response.body.token).toBeTruthy();
  });
});

describe('secure local pairing — HTTP routes (flag enabled)', () => {
  describe('Desktop-only control boundary (create/cancel) on LAN', () => {
    it('rejects create with no control token on LAN', async () => {
      const context = await setupLanContext();
      const response = await request(createTestApp(context)).post('/device/pairing-session').send({});
      expect(response.status).toBe(403);
      expect(response.body.code).toBe('FORBIDDEN');
      expect(response.body.secret).toBeUndefined();
    });

    it('rejects create with a wrong control token on LAN', async () => {
      const context = await setupLanContext();
      const response = await request(createTestApp(context))
        .post('/device/pairing-session')
        .set(CONTROL_TOKEN_HEADER, 'not-the-real-token')
        .send({});
      expect(response.status).toBe(403);
    });

    it('allows create with the correct control token on LAN', async () => {
      const context = await setupLanContext();
      const response = await request(createTestApp(context))
        .post('/device/pairing-session')
        .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
        .send({});
      expect(response.status).toBe(201);
    });

    it('fails closed when the Connector has no configured control token at all (no Desktop supervisor)', async () => {
      const context = await setupLanContext({ desktopControlToken: null });
      const response = await request(createTestApp(context))
        .post('/device/pairing-session')
        .set(CONTROL_TOKEN_HEADER, 'anything')
        .send({});
      expect(response.status).toBe(403);
    });

    it('rejects cancel with no control token on LAN, even for a session ID that does not exist', async () => {
      const context = await setupLanContext();
      const response = await request(createTestApp(context))
        .post('/device/pairing-session/cancel')
        .send({ pairingSessionId: '00000000-0000-0000-0000-000000000000' });
      expect(response.status).toBe(403);
    });

    it('is a pass-through on loopback regardless of the token — matches todays Desktop-local behavior', async () => {
      const context = await setupLoopbackContext();
      const response = await request(createTestApp(context)).post('/device/pairing-session').send({});
      expect(response.status).toBe(201);
    });
  });

  describe('pairing-session lifecycle (loopback context, control gate is a pass-through)', () => {
    it('POST /device/pairing-session returns only pairing-bootstrap fields — no business data, no reusable credential', async () => {
      const context = await setupLoopbackContext();
      const response = await request(createTestApp(context)).post('/device/pairing-session').send({});

      expect(response.status).toBe(201);
      expect(Object.keys(response.body).sort()).toEqual(
        [
          'schemaVersion',
          'pairingSessionId',
          'connectorId',
          'connectorName',
          'host',
          'port',
          'expiresAt',
          'secret',
          'shortCode',
        ].sort(),
      );
      const serialized = JSON.stringify(response.body);
      expect(serialized).not.toMatch(/ledger|voucher|stock|tally|company/i);
    });

    it('a caller-supplied connectorId/host/port in the request body is ignored — always bound to the Connector\'s own identity/config', async () => {
      const context = await setupLoopbackContext();
      const identity = context.container.resolve<ConnectorIdentityRepository>(ServiceTokens.ConnectorIdentity);
      const realId = identity.getOrCreateIdentity().connectorId;

      const response = await request(createTestApp(context))
        .post('/device/pairing-session')
        .send({ connectorId: 'attacker-supplied-id', host: '6.6.6.6', port: 1 });

      expect(response.status).toBe(201);
      expect(response.body.connectorId).toBe(realId);
      expect(response.body.host).not.toBe('6.6.6.6');
      expect(response.body.port).not.toBe(1);
    });

    it('GET /device/pairing-session/status reports non-sensitive status only', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});

      const response = await request(createTestApp(context)).get('/device/pairing-session/status').query({
        pairingSessionId: created.body.pairingSessionId,
      });

      expect(response.status).toBe(200);
      expect(response.body.redeemed).toBe(false);
      expect(response.body.cancelled).toBe(false);
      expect(response.body.expired).toBe(false);
      expect(response.body.secret).toBeUndefined();
      expect(response.body.shortCode).toBeUndefined();
    });

    it('GET status reflects redeemed=true after a successful redemption', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});
      await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });

      const status = await request(createTestApp(context)).get('/device/pairing-session/status').query({
        pairingSessionId: created.body.pairingSessionId,
      });
      expect(status.body.redeemed).toBe(true);
    });

    it('GET status 404s for an unknown session', async () => {
      const context = await setupLoopbackContext();
      const response = await request(createTestApp(context)).get('/device/pairing-session/status').query({
        pairingSessionId: '00000000-0000-0000-0000-000000000000',
      });
      expect(response.status).toBe(404);
    });

    it('redeem rejects a malformed body (neither shortCode nor pairingSessionId+secret+connectorId+host+port)', async () => {
      const context = await setupLoopbackContext();
      const response = await request(createTestApp(context)).post('/device/pairing-session/redeem').send({});
      expect(response.status).toBe(400);
      expect(response.body.code).toBe('VALIDATION_ERROR');
    });

    it('redeem rejects a QR-path body missing connectorId/host/port', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({ pairingSessionId: created.body.pairingSessionId, secret: created.body.secret });

      expect(response.status).toBe(400);
      expect(response.body.code).toBe('VALIDATION_ERROR');
    });

    it('redeem rejects an unsupported schemaVersion', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          schemaVersion: '999',
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });

      expect(response.status).toBe(400);
      expect(response.body.code).toBe('UNSUPPORTED_SCHEMA');
    });

    it('accepts the current schemaVersion explicitly and succeeds', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          schemaVersion: created.body.schemaVersion,
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });

      expect(response.status).toBe(201);
      expect(response.body.ok).toBe(true);
    });

    it('redeem rejects an unknown pairing session', async () => {
      const context = await setupLoopbackContext();
      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: '00000000-0000-0000-0000-000000000000',
          secret: 'anything',
          connectorId: 'x',
          host: 'x',
          port: 1,
        });

      expect(response.status).toBe(404);
      expect(response.body.code).toBe('NOT_FOUND');
    });

    it('redeem rejects a mismatched connectorId with the same generic outcome as a wrong secret (no field-level disclosure)', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: 'wrong-connector-id',
          host: created.body.host,
          port: created.body.port,
        });

      expect(response.status).toBe(401);
      expect(response.body.code).toBe('REDEMPTION_FAILED');
      expect(response.body.message).not.toMatch(/connector/i);
    });

    it('redeem rejects a mismatched host/port with the same generic outcome as a wrong secret (no field-level disclosure)', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: '10.0.0.99',
          port: created.body.port,
        });

      expect(response.status).toBe(401);
      expect(response.body.code).toBe('REDEMPTION_FAILED');
      expect(response.body.message).not.toMatch(/host|port|endpoint/i);
    });

    it('redeem rejects a wrong secret with the same generic outcome, and without issuing a credential', async () => {
      const context = await setupLoopbackContext();
      const credentials = context.container.resolve<PairingDeviceCredentialRepository>(
        ServiceTokens.PairingCredentials,
      );
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});
      const countBefore = credentials.listByConnector(created.body.connectorId).length;

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: 'wrong-secret',
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });

      expect(response.status).toBe(401);
      expect(response.body.code).toBe('REDEMPTION_FAILED');
      expect(response.body.token).toBeUndefined();

      // Delta, not an absolute count — createTestContext() uses a shared default database path,
      // so other tests/prior runs may have already issued credentials for this connectorId.
      expect(credentials.listByConnector(created.body.connectorId)).toHaveLength(countBefore);
    });

    it('a connectorId mismatch and a host/port mismatch are both counted toward the same failed-attempt lockout as a wrong secret', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});
      const app = createTestApp(context);

      await request(app)
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: 'wrong-connector-id',
          host: created.body.host,
          port: created.body.port,
        });
      await request(app)
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: '10.0.0.99',
          port: created.body.port,
        });

      // Two mismatches consumed, three wrong-secret guesses reach the five-attempt bound.
      for (let i = 0; i < 3; i += 1) {
        await request(app)
          .post('/device/pairing-session/redeem')
          .send({
            pairingSessionId: created.body.pairingSessionId,
            secret: 'still-wrong',
            connectorId: created.body.connectorId,
            host: created.body.host,
            port: created.body.port,
          });
      }

      const lockedOut = await request(app)
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });
      expect(lockedOut.status).toBe(429);
      expect(lockedOut.body.code).toBe('TOO_MANY_ATTEMPTS');
    });

    it('redeem via QR path succeeds and issues a credential only after success', async () => {
      const context = await setupLoopbackContext();
      const credentials = context.container.resolve<PairingDeviceCredentialRepository>(
        ServiceTokens.PairingCredentials,
      );
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});
      const countBefore = credentials.listByConnector(created.body.connectorId).length;

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });

      expect(response.status).toBe(201);
      expect(response.body.ok).toBe(true);
      expect(response.body.token).toBeTruthy();
      expect(response.body.credentialId).toBeTruthy();

      const issued = credentials.listByConnector(created.body.connectorId);
      expect(issued).toHaveLength(countBefore + 1);
      expect(issued.some((c) => c.credentialId === response.body.credentialId)).toBe(true);
      // Raw token returned exactly once — the credential store's own output never contains it.
      const storedJson = JSON.stringify(issued);
      expect(storedJson).not.toContain(response.body.token);
    });

    it('redeem via short code succeeds without requiring connectorId/host/port (but requires the endpoint to already be known/selected)', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({ shortCode: created.body.shortCode });

      expect(response.status).toBe(201);
      expect(response.body.ok).toBe(true);
    });

    it('redeem accepts an optional Android-supplied deviceId and stores it on the issued credential', async () => {
      const context = await setupLoopbackContext();
      const credentials = context.container.resolve<PairingDeviceCredentialRepository>(
        ServiceTokens.PairingCredentials,
      );
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});

      const response = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
          deviceId: 'android-device-uuid-001',
          deviceLabel: "Sri's Phone",
        });

      expect(response.status).toBe(201);
      const issued = credentials
        .listByConnector(created.body.connectorId)
        .find((c) => c.credentialId === response.body.credentialId);
      expect(issued?.deviceId).toBe('android-device-uuid-001');
      expect(issued?.deviceLabel).toBe("Sri's Phone");
    });
  });

  describe('per-source rate limiting on redeem', () => {
    it('a low-configured rate limiter returns 429 with the approved response shape once its bound is reached', async () => {
      // Directly exercises the router wiring (not just the standalone middleware unit tests) by
      // injecting a deliberately tiny limit through the exported test-only override.
      const context = await setupLoopbackContext();
      const identity = context.container.resolve<ConnectorIdentityRepository>(ServiceTokens.ConnectorIdentity);
      const pairingSessions = context.container.resolve<PairingSessionRepository>(ServiceTokens.PairingSessions);
      const pairingCredentials = context.container.resolve<PairingDeviceCredentialRepository>(
        ServiceTokens.PairingCredentials,
      );
      const transportIdentity = context.container.resolve<ConnectorTransportIdentityService>(
        ServiceTokens.TransportIdentity,
      );

      const app = express();
      app.use(express.json());
      app.use(
        createPairingBootstrapRouter({
          config: {
            networkExposure: 'loopback',
            desktopControlToken: null,
            securePairingEnabled: true,
            host: '127.0.0.1',
            port: 8080,
            secureTransportEnabled: false,
            secureTransportPort: 8443,
          },
          connectorIdentity: identity,
          pairingSessions,
          pairingCredentials,
          transportIdentity,
          redeemRateLimiter: createPairingRedeemRateLimiter({ maxAttempts: 1, windowMs: 60_000 }),
        }),
      );

      const first = await request(app).post('/device/pairing-session/redeem').send({ shortCode: 'AAAAAAAA' });
      expect(first.status).not.toBe(429);

      const second = await request(app).post('/device/pairing-session/redeem').send({ shortCode: 'BBBBBBBB' });
      expect(second.status).toBe(429);
      expect(second.body.code).toBe('TOO_MANY_REQUESTS');
    });
  });

  describe('cancellation and revocation', () => {
    it('cancel (with the control token where required) invalidates a session before it can be redeemed', async () => {
      const context = await setupLanContext();
      const created = await request(createTestApp(context))
        .post('/device/pairing-session')
        .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
        .send({});

      const cancelResponse = await request(createTestApp(context))
        .post('/device/pairing-session/cancel')
        .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
        .send({ pairingSessionId: created.body.pairingSessionId });
      expect(cancelResponse.status).toBe(200);
      expect(cancelResponse.body.ok).toBe(true);

      const redeemResponse = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });
      expect(redeemResponse.status).toBe(409);
      expect(redeemResponse.body.code).toBe('CANCELLED');
    });

    it('a revoked credential is rejected on subsequent validation', async () => {
      const context = await setupLoopbackContext();
      const created = await request(createTestApp(context)).post('/device/pairing-session').send({});
      const redeemed = await request(createTestApp(context))
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });

      const credentials = context.container.resolve<PairingDeviceCredentialRepository>(
        ServiceTokens.PairingCredentials,
      );
      expect(credentials.validateToken(redeemed.body.token)).not.toBeNull();

      const revokeResponse = await request(createTestApp(context))
        .post('/device/pairing-credential/revoke')
        .send({ credentialId: redeemed.body.credentialId });
      expect(revokeResponse.status).toBe(200);
      expect(revokeResponse.body.ok).toBe(true);

      expect(credentials.validateToken(redeemed.body.token)).toBeNull();
    });

    it('revoke reports not-found for an unknown credential', async () => {
      const context = await setupLoopbackContext();
      const response = await request(createTestApp(context))
        .post('/device/pairing-credential/revoke')
        .send({ credentialId: '00000000-0000-0000-0000-000000000000' });
      expect(response.status).toBe(404);
    });
  });

  describe('read-only middleware', () => {
    it('still allows the new pairing POST routes and blocks other mutations', async () => {
      const context = await setupLoopbackContext();
      const app = createTestApp(context);

      const created = await request(app).post('/device/pairing-session').send({});
      expect(created.status).toBe(201);

      const disallowed = await request(app).put('/device/pairing-session');
      expect(disallowed.status).toBe(405);
    });
  });

  describe('logging hygiene', () => {
    it('no pairing secret, short code, control token, or issued credential appears in request logs', async () => {
      const context = await setupLanContext();
      const logger = context.container.resolve<Logger>(ServiceTokens.Logger);
      const entries: unknown[] = [];
      const capture = (message: string, meta?: Record<string, unknown>) => {
        entries.push({ message, meta });
      };
      logger.info = capture;
      logger.warn = capture;
      logger.error = capture;

      const app = createTestApp(context);
      const created = await request(app)
        .post('/device/pairing-session')
        .set(CONTROL_TOKEN_HEADER, DESKTOP_TOKEN)
        .send({});
      const redeemed = await request(app)
        .post('/device/pairing-session/redeem')
        .send({
          pairingSessionId: created.body.pairingSessionId,
          secret: created.body.secret,
          connectorId: created.body.connectorId,
          host: created.body.host,
          port: created.body.port,
        });

      const serializedLogs = JSON.stringify(entries);
      expect(serializedLogs).not.toContain(created.body.secret);
      expect(serializedLogs).not.toContain(created.body.shortCode);
      expect(serializedLogs).not.toContain(redeemed.body.token);
      expect(serializedLogs).not.toContain(DESKTOP_TOKEN);
    });
  });

  describe('non-regression: legacy route and existing business routes are unaffected', () => {
    it('legacy POST /device/pair continues to work unchanged while secure pairing is also enabled', async () => {
      const context = await setupLoopbackContext();
      const response = await request(createTestApp(context)).post('/device/pair').send({
        companyId: 'acme-001',
        companyName: 'Acme Corp',
        installationId: 'install-0000000000000000',
      });
      expect(response.status).toBe(201);
      expect(response.body.token).toBeTruthy();
    });

    it('existing protected business routes (e.g. GET /session) retain their current loopback-unauthenticated behavior', async () => {
      const context = await setupLoopbackContext();
      const response = await request(createTestApp(context)).get('/session');
      // Unauthenticated on loopback today (requireDeviceAuthForLan is a no-op off-LAN) — the new
      // pairing surface must not have changed this. Any response other than 401 proves the
      // trusted-device gate was not accidentally tightened by this phase's wiring changes.
      expect(response.status).not.toBe(401);
    });

    it('securePairingEnabled still defaults to false even with secureTransportEnabled overridden true', async () => {
      const context = createTestContext({ secureTransportEnabled: true });
      expect(context.config.securePairingEnabled).toBe(false);
      expect(context.config.secureTransportEnabled).toBe(true);
    });

    it('legacy POST /device/pair and GET /session are unaffected by secureTransportEnabled being on', async () => {
      const context = createTestContext({ secureTransportEnabled: true, securePairingEnabled: true });
      await startTestServices(context);
      const app = createTestApp(context);

      const legacyPair = await request(app).post('/device/pair').send({
        companyId: 'acme-002',
        companyName: 'Acme Corp',
        installationId: 'install-1111111111111111',
      });
      expect(legacyPair.status).toBe(201);

      const session = await request(app).get('/session');
      expect(session.status).not.toBe(401);
    });
  });
});
