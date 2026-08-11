import { describe, expect, it } from 'vitest';

import {
  createPrivateVault,
  isPrivateVaultStillPresent,
  privateConnectorDataDir,
  resolvePrivateVault,
} from '../../src/application/private-storage/private-storage-resolver.js';
import { FakeRemovableVolumeEnumerator } from '../../src/application/private-storage/removable-volume-enumerator.js';

function fakeFs(initial: Record<string, string> = {}) {
  const files = new Map<string, string>(Object.entries(initial));
  return {
    files,
    existsSync: (target: string) => files.has(target),
    readFileSync: (target: string) => {
      const value = files.get(target);
      if (value === undefined) throw new Error(`ENOENT: ${target}`);
      return value;
    },
    writeFileSync: (target: string, data: string) => {
      files.set(target, data);
    },
    mkdirSync: () => undefined,
  };
}

function markerPath(driveLetter: string): string {
  return `${driveLetter}BudcomPrivate\\vault.json`;
}

describe('createPrivateVault', () => {
  it('writes a marker file and creates the connector-data directory', () => {
    const fs = fakeFs();
    const result = createPrivateVault('E:\\', fs, 'vault-123');
    expect(result.vaultId).toBe('vault-123');
    expect(result.dataRoot).toBe(privateConnectorDataDir('E:\\', 'vault-123'));
    expect(fs.files.has(markerPath('E:\\'))).toBe(true);
    expect(JSON.parse(fs.files.get(markerPath('E:\\'))!).vaultId).toBe('vault-123');
  });

  it('refuses to overwrite a drive that already has a BUDCOM vault, to avoid orphaning existing data', () => {
    const fs = fakeFs({ [markerPath('E:\\')]: JSON.stringify({ schemaVersion: 1, vaultId: 'existing-vault', createdAt: 't' }) });
    expect(() => createPrivateVault('E:\\', fs, 'new-vault')).toThrow(/already exists/);
  });
});

describe('resolvePrivateVault', () => {
  it('returns standard status immediately for standard mode, without touching the enumerator', async () => {
    const enumerator = new FakeRemovableVolumeEnumerator();
    const result = await resolvePrivateVault({ mode: 'standard', vaultId: null, lastKnownDriveLetter: null }, enumerator, fakeFs());
    expect(result.status).toBe('standard');
  });

  it('resolves via the last-known drive letter without needing rediscovery when it still matches', async () => {
    const fs = fakeFs({ [markerPath('E:\\')]: JSON.stringify({ schemaVersion: 1, vaultId: 'vault-1', createdAt: 't' }) });
    const enumerator = new FakeRemovableVolumeEnumerator(); // deliberately empty — proves rediscovery wasn't needed
    const result = await resolvePrivateVault(
      { mode: 'private-removable', vaultId: 'vault-1', lastKnownDriveLetter: 'E:\\' },
      enumerator,
      fs,
    );
    expect(result).toMatchObject({ status: 'resolved', driveLetter: 'E:\\', vaultId: 'vault-1' });
  });

  it('rediscovers the vault at a NEW drive letter — the drive-letter-change case', async () => {
    // Last known was E:, but Windows now assigns the same physical drive F: this session.
    const fs = fakeFs({ [markerPath('F:\\')]: JSON.stringify({ schemaVersion: 1, vaultId: 'vault-1', createdAt: 't' }) });
    const enumerator = new FakeRemovableVolumeEnumerator([
      { driveLetter: 'F:\\', label: 'BUDCOM-USB', fileSystem: 'NTFS', sizeBytes: 1000, freeBytes: 500 },
    ]);
    const result = await resolvePrivateVault(
      { mode: 'private-removable', vaultId: 'vault-1', lastKnownDriveLetter: 'E:\\' },
      enumerator,
      fs,
    );
    expect(result).toMatchObject({ status: 'resolved', driveLetter: 'F:\\', vaultId: 'vault-1' });
  });

  it('only ever inspects drives the enumerator reports as removable — never a hard-coded/fixed disk', async () => {
    // No last-known letter, nothing removable currently attached: must report missing, not scan C:.
    const fs = fakeFs({ [markerPath('C:\\')]: JSON.stringify({ schemaVersion: 1, vaultId: 'vault-1', createdAt: 't' }) });
    const enumerator = new FakeRemovableVolumeEnumerator([]);
    const result = await resolvePrivateVault(
      { mode: 'private-removable', vaultId: 'vault-1', lastKnownDriveLetter: null },
      enumerator,
      fs,
    );
    expect(result.status).toBe('missing');
  });

  it('rejects a lookalike drive whose vaultId does not match, rather than silently attaching', async () => {
    const fs = fakeFs({ [markerPath('E:\\')]: JSON.stringify({ schemaVersion: 1, vaultId: 'WRONG-vault', createdAt: 't' }) });
    const enumerator = new FakeRemovableVolumeEnumerator([
      { driveLetter: 'E:\\', label: 'Some Other USB', fileSystem: 'exFAT', sizeBytes: 1000, freeBytes: 500 },
    ]);
    const result = await resolvePrivateVault(
      { mode: 'private-removable', vaultId: 'vault-1', lastKnownDriveLetter: 'E:\\' },
      enumerator,
      fs,
    );
    expect(result.status).not.toBe('resolved');
    expect(result).toMatchObject({ status: 'mismatched', driveLetter: 'E:\\', foundVaultId: 'WRONG-vault' });
  });

  it('reports missing when no configured vaultId exists at all', async () => {
    const enumerator = new FakeRemovableVolumeEnumerator();
    const result = await resolvePrivateVault(
      { mode: 'private-removable', vaultId: null, lastKnownDriveLetter: null },
      enumerator,
      fakeFs(),
    );
    expect(result.status).toBe('missing');
  });
});

describe('isPrivateVaultStillPresent', () => {
  it('is true only when the marker exists and the vaultId matches exactly', () => {
    const fs = fakeFs({ [markerPath('E:\\')]: JSON.stringify({ schemaVersion: 1, vaultId: 'vault-1', createdAt: 't' }) });
    expect(isPrivateVaultStillPresent('E:\\', 'vault-1', fs)).toBe(true);
    expect(isPrivateVaultStillPresent('E:\\', 'vault-OTHER', fs)).toBe(false);
  });

  it('is false when the drive/marker has disappeared — the hot-removal case', () => {
    const fs = fakeFs();
    expect(isPrivateVaultStillPresent('E:\\', 'vault-1', fs)).toBe(false);
  });
});
