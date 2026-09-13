import type { Request } from 'express';
import { afterEach, describe, expect, it } from 'vitest';

import { PairingCredentialAuthenticator } from '../../../src/services/pairing/pairing-credential-authenticator.js';
import { PairingDeviceCredentialRepository } from '../../../src/services/pairing/pairing-device-credential-repository.js';
import { PairingSessionRepository } from '../../../src/services/pairing/pairing-session-repository.js';
import { ConnectorIdentityRepository } from '../../../src/services/identity/connector-identity-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

function bearerRequest(token: string | undefined): Request {
  return {
    header: (name: string) => (name.toLowerCase() === 'authorization' && token !== undefined ? `Bearer ${token}` : undefined),
  } as unknown as Request;
}

async function setup(connectorIdOverride?: string) {
  const { storage } = await createTestSqliteStorage();
  const db = storage.getBundle().database;
  const sessions = new PairingSessionRepository(db);
  const credentials = new PairingDeviceCredentialRepository(db);
  const connectorIdentity = new ConnectorIdentityRepository(() => storage.getBundle().database, {
    VENTURE_CONNECTOR_ID: connectorIdOverride ?? 'connector-under-test',
  });
  const { connectorId } = connectorIdentity.getOrCreateIdentity();

  const session = sessions.create({ connectorId, connectorName: 'Test', host: '127.0.0.1', port: 8080 });
  const redeemed = sessions.redeemBySessionId(session.pairingSessionId, session.secret, {
    connectorId: session.connectorId,
    host: session.host,
    port: session.port,
  });
  if (redeemed.kind !== 'redeemed') throw new Error('setup: expected redemption to succeed');

  const authenticator = new PairingCredentialAuthenticator(credentials, connectorIdentity);
  return { credentials, connectorIdentity, authenticator, pairingSessionId: redeemed.pairingSessionId, connectorId };
}

describe('PairingCredentialAuthenticator', () => {
  it('rejects a missing Authorization header with reason missing_token', async () => {
    const { authenticator } = await setup();
    const result = authenticator.authenticate(bearerRequest(undefined));
    expect(result).toEqual({ ok: false, reason: 'missing_token' });
  });

  it('rejects an unknown token with reason unknown_token', async () => {
    const { authenticator } = await setup();
    const result = authenticator.authenticate(bearerRequest('not-a-real-token'));
    expect(result).toEqual({ ok: false, reason: 'unknown_token' });
  });

  it('rejects a revoked token with reason revoked_token', async () => {
    const { credentials, authenticator, pairingSessionId, connectorId } = await setup();
    const { rawToken, credentialId } = credentials.issue({ pairingSessionId, connectorId });
    credentials.revoke(credentialId);

    const result = authenticator.authenticate(bearerRequest(rawToken));
    expect(result).toEqual({ ok: false, reason: 'revoked_token' });
  });

  it('rejects a token bound to a different Connector with reason wrong_connector', async () => {
    const { credentials, authenticator, pairingSessionId } = await setup();
    const { rawToken } = credentials.issue({ pairingSessionId, connectorId: 'some-other-connector-id' });

    const result = authenticator.authenticate(bearerRequest(rawToken));
    expect(result).toEqual({ ok: false, reason: 'wrong_connector' });
  });

  it('produces a principal for a valid, active, correctly-bound credential', async () => {
    const { credentials, authenticator, pairingSessionId, connectorId } = await setup();
    const { rawToken, credentialId } = credentials.issue({
      pairingSessionId,
      connectorId,
      deviceId: 'device-001',
      deviceLabel: "Sri's Phone",
    });

    const result = authenticator.authenticate(bearerRequest(rawToken));
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error('expected success');
    expect(result.principal).toEqual({
      kind: 'pairing-credential',
      credentialId,
      connectorId,
      deviceId: 'device-001',
      deviceLabel: "Sri's Phone",
      createdAt: expect.any(String),
      lastUsedAt: expect.any(String),
    });
  });

  it('updates last_used_at on successful authentication', async () => {
    const { credentials, authenticator, pairingSessionId, connectorId } = await setup();
    const { rawToken, credentialId } = credentials.issue({ pairingSessionId, connectorId });
    expect(credentials.findByToken(rawToken)?.lastUsedAt).toBeNull();

    authenticator.authenticate(bearerRequest(rawToken));

    const record = credentials.listByConnector(connectorId).find((c) => c.credentialId === credentialId);
    expect(record?.lastUsedAt).not.toBeNull();
  });

  it('does NOT update last_used_at when the token is revoked', async () => {
    const { credentials, authenticator, pairingSessionId, connectorId } = await setup();
    const { rawToken, credentialId } = credentials.issue({ pairingSessionId, connectorId });
    credentials.revoke(credentialId);

    authenticator.authenticate(bearerRequest(rawToken));

    const record = credentials.findByToken(rawToken);
    expect(record?.lastUsedAt).toBeNull();
  });

  it('does NOT update last_used_at when the credential belongs to a different Connector', async () => {
    const { credentials, authenticator, pairingSessionId } = await setup();
    const { rawToken } = credentials.issue({ pairingSessionId, connectorId: 'some-other-connector-id' });

    authenticator.authenticate(bearerRequest(rawToken));

    const record = credentials.findByToken(rawToken);
    expect(record?.lastUsedAt).toBeNull();
  });

  it('the same credential cannot authenticate against a different Connector identity', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup('connector-A');
    const { rawToken } = credentials.issue({ pairingSessionId, connectorId });

    // A second identity repository resolved to a *different* connectorId, reusing the same
    // credential store — env-supplied IDs never touch the database (see
    // ConnectorIdentityRepository.getOrCreateIdentity), so the never-invoked db getter is safe.
    const otherIdentity = new ConnectorIdentityRepository(() => ({}) as never, {
      VENTURE_CONNECTOR_ID: 'connector-B',
    });
    const otherAuthenticator = new PairingCredentialAuthenticator(credentials, otherIdentity);

    const result = otherAuthenticator.authenticate(bearerRequest(rawToken));
    expect(result).toEqual({ ok: false, reason: 'wrong_connector' });
  });
});
