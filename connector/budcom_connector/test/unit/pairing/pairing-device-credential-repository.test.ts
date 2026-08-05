import { afterEach, describe, expect, it } from 'vitest';

import { PairingDeviceCredentialRepository } from '../../../src/services/pairing/pairing-device-credential-repository.js';
import { PairingSessionRepository } from '../../../src/services/pairing/pairing-session-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('PairingDeviceCredentialRepository', () => {
  async function setup() {
    const { storage } = await createTestSqliteStorage();
    const db = storage.getBundle().database;
    const sessions = new PairingSessionRepository(db);
    const credentials = new PairingDeviceCredentialRepository(db);
    const session = sessions.create({
      connectorId: 'connector-abc',
      connectorName: 'VIDHI',
      host: '10.100.141.231',
      port: 8080,
    });
    const redeemed = sessions.redeemBySessionId(session.pairingSessionId, session.secret, {
      connectorId: session.connectorId,
      host: session.host,
      port: session.port,
    });
    if (redeemed.kind !== 'redeemed') throw new Error('setup: expected redemption to succeed');
    return { credentials, pairingSessionId: redeemed.pairingSessionId, connectorId: redeemed.connectorId };
  }

  it('issues a credential and returns a raw token that is not stored', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();

    const result = credentials.issue({ pairingSessionId, connectorId });
    expect(result.credentialId).toBeTruthy();
    expect(result.rawToken).toBeTruthy();
    expect(result.rawToken.length).toBeGreaterThanOrEqual(32);

    const record = credentials.listByConnector(connectorId)[0];
    expect(record).toBeDefined();
    expect((record as unknown as Record<string, unknown>)['rawToken']).toBeUndefined();
    expect(record?.tokenHash).not.toBe(result.rawToken);
  });

  it('raw token is not present in any repository output (privacy guard)', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();
    const { rawToken } = credentials.issue({ pairingSessionId, connectorId });

    const all = credentials.listByConnector(connectorId);
    const json = JSON.stringify(all);
    expect(json).not.toContain(rawToken);
  });

  it('validates a correct token and updates last_used_at', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();
    const { rawToken, credentialId } = credentials.issue({ pairingSessionId, connectorId });

    const validated = credentials.validateToken(rawToken);
    expect(validated).not.toBeNull();
    expect(validated?.credentialId).toBe(credentialId);
    expect(validated?.lastUsedAt).not.toBeNull();
  });

  it('rejects an invalid token', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();
    credentials.issue({ pairingSessionId, connectorId });

    const result = credentials.validateToken('not-a-real-token');
    expect(result).toBeNull();
  });

  it('revokes a credential and subsequent validation fails', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();
    const { rawToken, credentialId } = credentials.issue({ pairingSessionId, connectorId });

    const revoked = credentials.revoke(credentialId);
    expect(revoked).toBe(true);

    const validated = credentials.validateToken(rawToken);
    expect(validated).toBeNull();
  });

  it('revoke returns false for an already-revoked or unknown credential', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();
    const { credentialId } = credentials.issue({ pairingSessionId, connectorId });

    credentials.revoke(credentialId);
    expect(credentials.revoke(credentialId)).toBe(false);
    expect(credentials.revoke('00000000-0000-0000-0000-000000000000')).toBe(false);
  });

  it('stores an optional device label', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();
    credentials.issue({ pairingSessionId, connectorId, deviceLabel: "Sri's Phone" });

    const record = credentials.listByConnector(connectorId)[0];
    expect(record?.deviceLabel).toBe("Sri's Phone");
  });

  it('stores an optional Android-supplied logical device ID, never as part of any secret', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();
    const { rawToken } = credentials.issue({ pairingSessionId, connectorId, deviceId: 'device-uuid-123' });

    const record = credentials.listByConnector(connectorId)[0];
    expect(record?.deviceId).toBe('device-uuid-123');
    expect(record?.deviceId).not.toBe(rawToken);
  });

  it('deviceId is null when the caller does not supply one', async () => {
    const { credentials, pairingSessionId, connectorId } = await setup();
    credentials.issue({ pairingSessionId, connectorId });

    const record = credentials.listByConnector(connectorId)[0];
    expect(record?.deviceId).toBeNull();
  });
});
