import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { assertPrivateStorageVaultPresent, PrivateStorageUnavailableError } from '../../../src/storage/private-storage-guard.js';
import type { PrivateStorageGuardFsPort } from '../../../src/storage/private-storage-guard.js';
import { SqliteStorageService } from '../../../src/storage/sqlite/storage-service.js';
import { createTestConnectorConfig } from '../../helpers/sqlite-test-storage.js';

function fakeFs(files: Record<string, string>): PrivateStorageGuardFsPort {
  return {
    existsSync: (target) => Object.prototype.hasOwnProperty.call(files, target),
    readFileSync: (target) => {
      if (!Object.prototype.hasOwnProperty.call(files, target)) {
        throw new Error(`ENOENT: ${target}`);
      }
      return files[target]!;
    },
  };
}

describe('assertPrivateStorageVaultPresent', () => {
  it('is a no-op when neither config field is set (standard storage)', () => {
    expect(() => assertPrivateStorageVaultPresent(
      { privateStorageExpectedVaultId: null, privateStorageMarkerPath: null },
      fakeFs({}),
    )).not.toThrow();
  });

  it('is a no-op when only one of the two fields is set', () => {
    expect(() => assertPrivateStorageVaultPresent(
      { privateStorageExpectedVaultId: 'vault-1', privateStorageMarkerPath: null },
      fakeFs({}),
    )).not.toThrow();
    expect(() => assertPrivateStorageVaultPresent(
      { privateStorageExpectedVaultId: null, privateStorageMarkerPath: 'E:/BudcomPrivate/vault.json' },
      fakeFs({}),
    )).not.toThrow();
  });

  it('throws PrivateStorageUnavailableError when the marker file is missing', () => {
    expect(() => assertPrivateStorageVaultPresent(
      { privateStorageExpectedVaultId: 'vault-1', privateStorageMarkerPath: 'E:/BudcomPrivate/vault.json' },
      fakeFs({}),
    )).toThrow(PrivateStorageUnavailableError);
  });

  it('throws when the marker file is present but malformed JSON', () => {
    expect(() => assertPrivateStorageVaultPresent(
      { privateStorageExpectedVaultId: 'vault-1', privateStorageMarkerPath: 'E:/BudcomPrivate/vault.json' },
      fakeFs({ 'E:/BudcomPrivate/vault.json': 'not json' }),
    )).toThrow(PrivateStorageUnavailableError);
  });

  it('throws when the marker vaultId does not match — the "wrong/lookalike drive" case', () => {
    expect(() => assertPrivateStorageVaultPresent(
      { privateStorageExpectedVaultId: 'vault-1', privateStorageMarkerPath: 'E:/BudcomPrivate/vault.json' },
      fakeFs({ 'E:/BudcomPrivate/vault.json': JSON.stringify({ vaultId: 'vault-OTHER' }) }),
    )).toThrow(PrivateStorageUnavailableError);
  });

  it('passes silently when the marker vaultId matches exactly', () => {
    expect(() => assertPrivateStorageVaultPresent(
      { privateStorageExpectedVaultId: 'vault-1', privateStorageMarkerPath: 'E:/BudcomPrivate/vault.json' },
      fakeFs({ 'E:/BudcomPrivate/vault.json': JSON.stringify({ vaultId: 'vault-1', createdAt: '2026-01-01' }) }),
    )).not.toThrow();
  });
});

// The unit tests above exercise assertPrivateStorageVaultPresent() in isolation; every other
// storage-service test in this suite runs with both config fields null (a no-op), so nothing
// proves the guard is still actually wired into SqliteStorageService.start() ahead of the real
// database open. These tests use the real filesystem and the real service, not a fake fs port,
// specifically to close that gap.
describe('SqliteStorageService.start() with private storage configured', () => {
  const tempDirs: string[] = [];

  afterEach(() => {
    for (const dir of tempDirs.splice(0)) {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  const nullLogger = {
    info: () => {},
    warn: () => {},
    error: () => {},
    debug: () => {},
    child: () => nullLogger,
  } as never;

  it('refuses to start (and creates no database file) when the configured vault marker is absent', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-private-storage-guard-'));
    tempDirs.push(basePath);
    const config = {
      ...createTestConnectorConfig(basePath),
      privateStorageExpectedVaultId: 'vault-real-fs-1',
      privateStorageMarkerPath: path.join(basePath, 'does-not-exist', 'vault.json'),
    };
    const storage = new SqliteStorageService(config, nullLogger);

    await expect(storage.start()).rejects.toThrow(PrivateStorageUnavailableError);
    expect(fs.existsSync(path.join(basePath, 'budcom-ledger.db'))).toBe(false);
  });

  it('starts normally against the real filesystem when the marker genuinely matches', async () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-private-storage-guard-'));
    tempDirs.push(basePath);
    const markerPath = path.join(basePath, 'vault.json');
    fs.writeFileSync(markerPath, JSON.stringify({ schemaVersion: 1, vaultId: 'vault-real-fs-2', createdAt: '2026-01-01' }));
    const config = {
      ...createTestConnectorConfig(basePath),
      privateStorageExpectedVaultId: 'vault-real-fs-2',
      privateStorageMarkerPath: markerPath,
    };
    const storage = new SqliteStorageService(config, nullLogger);

    await expect(storage.start()).resolves.toBeUndefined();
    expect(fs.existsSync(path.join(basePath, 'budcom-ledger.db'))).toBe(true);
    await storage.stop();
  });
});
