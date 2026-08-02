import { afterEach, describe, expect, it } from 'vitest';

import { TrustedDeviceRepository } from '../../../src/services/device/trusted-device-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('TrustedDeviceRepository', () => {
  async function setup() {
    const { storage } = await createTestSqliteStorage();
    const db = storage.getBundle().database;
    const repo = new TrustedDeviceRepository(db);
    return { repo };
  }

  it('pairs a device and returns a raw token that is not stored', async () => {
    const { repo } = await setup();

    const result = repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });

    expect(result.deviceRecordId).toBeTruthy();
    expect(result.rawToken).toBeTruthy();
    expect(result.rawToken.length).toBeGreaterThanOrEqual(32);
    expect(result.companyId).toBe('acme-001');
    expect(result.autoConnectEnabled).toBe(false);

    // The raw token must not be stored; only the hash exists in the DB row.
    const records = repo.listAll();
    expect(records).toHaveLength(1);
    const [record] = records;
    expect((record as unknown as Record<string, unknown>)['rawToken']).toBeUndefined();
    expect(record.tokenHash).not.toBe(result.rawToken);
  });

  it('validates a correct token and updates last_used_at', async () => {
    const { repo } = await setup();
    const { rawToken, deviceRecordId } = repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });

    const validated = repo.validateToken(rawToken);
    expect(validated).not.toBeNull();
    expect(validated?.deviceRecordId).toBe(deviceRecordId);
    expect(validated?.companyId).toBe('acme-001');
    expect(validated?.lastUsedAt).not.toBeNull();
  });

  it('returns null for an invalid token', async () => {
    const { repo } = await setup();
    repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });

    const result = repo.validateToken('not-a-valid-token');
    expect(result).toBeNull();
  });

  it('revokes a device record and subsequent token validation returns null', async () => {
    const { repo } = await setup();
    const { rawToken, deviceRecordId } = repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });

    const revoked = repo.revoke(deviceRecordId);
    expect(revoked).toBe(true);

    const validated = repo.validateToken(rawToken);
    expect(validated).toBeNull();
  });

  it('revoke returns false for an already-revoked or unknown record', async () => {
    const { repo } = await setup();
    const { deviceRecordId } = repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });

    repo.revoke(deviceRecordId);
    const secondRevoke = repo.revoke(deviceRecordId);
    expect(secondRevoke).toBe(false);

    const neverExisted = repo.revoke('00000000-0000-0000-0000-000000000000');
    expect(neverExisted).toBe(false);
  });

  it('listByInstallation returns only active records for that installation', async () => {
    const { repo } = await setup();
    const installId = 'install-0000000000000000';
    const { deviceRecordId } = repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: installId,
    });
    repo.pair({
      companyId: 'acme-002',
      companyName: 'Acme Corp 2',
      installationId: installId,
      autoConnectEnabled: true,
    });
    // Revoke the first record — should not appear in the list.
    repo.revoke(deviceRecordId);

    const list = repo.listByInstallation(installId);
    expect(list).toHaveLength(1);
    expect(list[0]?.companyId).toBe('acme-002');
    expect(list[0]?.autoConnectEnabled).toBe(true);
  });

  it('listByInstallation does not return records from a different installation', async () => {
    const { repo } = await setup();
    repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-aaaaaaaaaaaaaaaa',
    });

    const list = repo.listByInstallation('install-bbbbbbbbbbbbbbbb');
    expect(list).toHaveLength(0);
  });

  it('raw token is not present in any repository output (privacy guard)', async () => {
    const { repo } = await setup();
    const { rawToken } = repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
    });

    const all = repo.listAll();
    const json = JSON.stringify(all);
    expect(json).not.toContain(rawToken);
  });

  it('optional auto-connect flag is stored and returned correctly', async () => {
    const { repo } = await setup();
    const { rawToken } = repo.pair({
      companyId: 'acme-001',
      companyName: 'Acme Corp',
      installationId: 'install-0000000000000000',
      autoConnectEnabled: true,
    });

    const record = repo.validateToken(rawToken);
    expect(record?.autoConnectEnabled).toBe(true);
  });
});
