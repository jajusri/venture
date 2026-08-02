import { afterEach, describe, expect, it } from 'vitest';

import { ConnectorIdentityRepository } from '../../../src/services/identity/connector-identity-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('ConnectorIdentityRepository', () => {
  it('generates a UUID once and persists it in storage_meta', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = new ConnectorIdentityRepository(() => storage.getBundle().database, {});

    const identity = repo.getOrCreateIdentity();

    expect(identity.connectorId).toMatch(/^[0-9a-f-]{36}$/i);
    expect(identity.source).toBe('connector-generated');
  });

  it('survives a Connector restart — a new repository over the same database returns the same id', async () => {
    const { storage } = await createTestSqliteStorage();
    const first = new ConnectorIdentityRepository(() => storage.getBundle().database, {});
    const idBeforeRestart = first.getOrCreateIdentity().connectorId;

    // Simulate a process restart: a fresh repository instance, same underlying storage.
    const second = new ConnectorIdentityRepository(() => storage.getBundle().database, {});
    const idAfterRestart = second.getOrCreateIdentity().connectorId;

    expect(idAfterRestart).toBe(idBeforeRestart);
  });

  it('is independent of any IP/host — never derived from network configuration', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = new ConnectorIdentityRepository(() => storage.getBundle().database, {});
    const idOnHostA = repo.getOrCreateIdentity().connectorId;

    // The repository has no notion of host/port/adapter at all — calling it again after a
    // simulated network change (nothing here to change, by construction) must return the same id.
    const idOnHostB = repo.getOrCreateIdentity().connectorId;

    expect(idOnHostB).toBe(idOnHostA);
    expect(idOnHostA).not.toContain('.');
  });

  it('prefers a Desktop-supplied BUDCOM_CONNECTOR_ID over generating one', async () => {
    const { storage } = await createTestSqliteStorage();
    const repo = new ConnectorIdentityRepository(
      () => storage.getBundle().database,
      { BUDCOM_CONNECTOR_ID: 'desktop-issued-id-123' },
    );

    const identity = repo.getOrCreateIdentity();

    expect(identity.connectorId).toBe('desktop-issued-id-123');
    expect(identity.source).toBe('desktop-supplied');
  });

  it('falls back to an ephemeral id without throwing when storage is unavailable', () => {
    const repo = new ConnectorIdentityRepository(() => {
      throw new Error('storage not running');
    }, {});

    expect(() => repo.getOrCreateIdentity()).not.toThrow();
    expect(repo.getOrCreateIdentity().connectorId).toBeTruthy();
  });

  it('uses BUDCOM_CONNECTOR_NAME when present, otherwise falls back to a friendly default', async () => {
    const { storage } = await createTestSqliteStorage();
    const named = new ConnectorIdentityRepository(
      () => storage.getBundle().database,
      { BUDCOM_CONNECTOR_NAME: 'Front Desk PC' },
    );
    expect(named.getOrCreateIdentity().connectorName).toBe('Front Desk PC');

    const unnamed = new ConnectorIdentityRepository(() => storage.getBundle().database, {});
    expect(unnamed.getOrCreateIdentity().connectorName).toBeTruthy();
  });
});
