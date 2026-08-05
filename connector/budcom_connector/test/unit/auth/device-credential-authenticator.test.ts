import type { Request } from 'express';
import { afterEach, describe, expect, it } from 'vitest';

import { DeviceCredentialAuthenticator } from '../../../src/services/auth/device-credential-authenticator.js';
import { PairingDeviceCredentialRepository } from '../../../src/services/pairing/pairing-device-credential-repository.js';
import { PairingSessionRepository } from '../../../src/services/pairing/pairing-session-repository.js';
import { ConnectorIdentityRepository } from '../../../src/services/identity/connector-identity-repository.js';
import { TrustedDeviceRepository } from '../../../src/services/device/trusted-device-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

function bearerRequest(token: string | undefined): Request {
  return {
    header: (name: string) => (name.toLowerCase() === 'authorization' && token !== undefined ? `Bearer ${token}` : undefined),
  } as unknown as Request;
}

async function setup() {
  const { storage } = await createTestSqliteStorage();
  const db = storage.getBundle().database;
  const sessions = new PairingSessionRepository(db);
  const pairingCredentials = new PairingDeviceCredentialRepository(db);
  const trustedDevices = new TrustedDeviceRepository(db);
  const connectorIdentity = new ConnectorIdentityRepository(() => storage.getBundle().database, {
    BUDCOM_CONNECTOR_ID: 'connector-under-test',
  });
  const { connectorId } = connectorIdentity.getOrCreateIdentity();

  const session = sessions.create({ connectorId, connectorName: 'Test', host: '127.0.0.1', port: 8080 });
  const redeemed = sessions.redeemBySessionId(session.pairingSessionId, session.secret, {
    connectorId: session.connectorId,
    host: session.host,
    port: session.port,
  });
  if (redeemed.kind !== 'redeemed') throw new Error('setup: expected redemption to succeed');

  const authenticator = new DeviceCredentialAuthenticator(pairingCredentials, connectorIdentity, trustedDevices);
  return {
    pairingCredentials,
    trustedDevices,
    authenticator,
    pairingSessionId: redeemed.pairingSessionId,
    connectorId,
  };
}

describe('DeviceCredentialAuthenticator (reusable future-authentication adapter — not wired into any route)', () => {
  it('classifies a valid pairing credential as a pairing-credential principal', async () => {
    const { pairingCredentials, authenticator, pairingSessionId, connectorId } = await setup();
    const { rawToken, credentialId } = pairingCredentials.issue({ pairingSessionId, connectorId });

    const result = authenticator.authenticate(bearerRequest(rawToken));
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error('expected success');
    expect(result.principal.kind).toBe('pairing-credential');
    expect(result.principal).toMatchObject({ kind: 'pairing-credential', credentialId });
  });

  it('classifies a valid legacy trusted-device token as a trusted-device principal', async () => {
    const { trustedDevices, authenticator } = await setup();
    const { rawToken, deviceRecordId } = trustedDevices.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });

    const result = authenticator.authenticate(bearerRequest(rawToken));
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error('expected success');
    expect(result.principal.kind).toBe('trusted-device');
    expect(result.principal).toMatchObject({ kind: 'trusted-device', deviceRecordId, companyId: 'acme-001' });
  });

  it('the two credential families remain distinguishable — a pairing credential never classifies as trusted-device and vice versa', async () => {
    const { pairingCredentials, trustedDevices, authenticator, pairingSessionId, connectorId } = await setup();
    const pairing = pairingCredentials.issue({ pairingSessionId, connectorId });
    const legacy = trustedDevices.pair({
      companyId: 'acme-002',
      companyName: 'Acme Corp',
      installationId: 'install-1111111111111111',
    });

    const pairingResult = authenticator.authenticate(bearerRequest(pairing.rawToken));
    const legacyResult = authenticator.authenticate(bearerRequest(legacy.rawToken));

    expect(pairingResult.ok && pairingResult.principal.kind).toBe('pairing-credential');
    expect(legacyResult.ok && legacyResult.principal.kind).toBe('trusted-device');
  });

  it('rejects an unknown token from either family', async () => {
    const { authenticator } = await setup();
    const result = authenticator.authenticate(bearerRequest('not-a-real-token'));
    expect(result).toEqual({ ok: false });
  });

  it('rejects a revoked pairing credential', async () => {
    const { pairingCredentials, authenticator, pairingSessionId, connectorId } = await setup();
    const { rawToken, credentialId } = pairingCredentials.issue({ pairingSessionId, connectorId });
    pairingCredentials.revoke(credentialId);

    const result = authenticator.authenticate(bearerRequest(rawToken));
    expect(result).toEqual({ ok: false });
  });

  it('does not treat a pairing credential as company-bound authorization — its principal carries no companyId', async () => {
    const { pairingCredentials, authenticator, pairingSessionId, connectorId } = await setup();
    const { rawToken } = pairingCredentials.issue({ pairingSessionId, connectorId });

    const result = authenticator.authenticate(bearerRequest(rawToken));
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error('expected success');
    expect((result.principal as unknown as Record<string, unknown>)['companyId']).toBeUndefined();
  });
});
