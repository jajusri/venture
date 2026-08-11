import { describe, expect, it } from 'vitest';

import { assertPrivateStorageVaultPresent, PrivateStorageUnavailableError } from '../../../src/storage/private-storage-guard.js';
import type { PrivateStorageGuardFsPort } from '../../../src/storage/private-storage-guard.js';

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
