import { describe, expect, it } from 'vitest';

import {
  PrivateStorageLocatorStore,
  resolvePrivateStorageLocatorPaths,
} from '../../src/application/private-storage/private-storage-locator-store.js';
import type { PrivateStorageLocatorV1 } from '../../src/application/private-storage/private-storage-types.js';

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
    renameSync: (from: string, to: string) => {
      const value = files.get(from);
      if (value === undefined) throw new Error(`ENOENT: ${from}`);
      files.delete(from);
      files.set(to, value);
    },
    mkdirSync: () => undefined,
    copyFileSync: (from: string, to: string) => {
      const value = files.get(from);
      if (value !== undefined) files.set(to, value);
    },
  };
}

const sampleRecord: PrivateStorageLocatorV1 = {
  schemaVersion: 1,
  mode: 'private-removable',
  vaultId: 'vault-abc',
  lastKnownDriveLetter: 'E:\\',
  lastKnownVolumeLabel: 'BUDCOM-USB',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('resolvePrivateStorageLocatorPaths', () => {
  it('places the locator file directly under userDataDir', () => {
    const paths = resolvePrivateStorageLocatorPaths('C:\\Users\\test\\AppData\\Roaming\\BUDCOM');
    expect(paths.filePath).toContain('private-storage-locator.json');
    expect(paths.tempPath).toBe(`${paths.filePath}.tmp`);
  });
});

describe('PrivateStorageLocatorStore', () => {
  it('exists() is false and load() is null when no file has been written yet — the first-run signal', () => {
    const fs = fakeFs();
    const store = new PrivateStorageLocatorStore('C:\\AppData', fs);
    expect(store.exists()).toBe(false);
    expect(store.load()).toBeNull();
  });

  it('persists and reloads a standard-mode record', () => {
    const fs = fakeFs();
    const store = new PrivateStorageLocatorStore('C:\\AppData', fs);
    const standard: PrivateStorageLocatorV1 = {
      schemaVersion: 1,
      mode: 'standard',
      vaultId: null,
      lastKnownDriveLetter: null,
      lastKnownVolumeLabel: null,
      createdAt: '2026-01-01T00:00:00.000Z',
      updatedAt: '2026-01-01T00:00:00.000Z',
    };
    const result = store.save(standard);
    expect(result.ok).toBe(true);
    expect(store.exists()).toBe(true);
    expect(store.load()).toEqual(standard);
  });

  it('persists and reloads a private-removable record with vault fields intact', () => {
    const fs = fakeFs();
    const store = new PrivateStorageLocatorStore('C:\\AppData', fs);
    store.save(sampleRecord);
    expect(store.load()).toEqual(sampleRecord);
  });

  it('writes a backup copy of the previous record before overwriting', () => {
    const fs = fakeFs();
    const store = new PrivateStorageLocatorStore('C:\\AppData', fs);
    store.save(sampleRecord);
    const updated = { ...sampleRecord, lastKnownDriveLetter: 'F:\\', updatedAt: '2026-02-01T00:00:00.000Z' };
    store.save(updated);
    expect(store.load()).toEqual(updated);
    const paths = resolvePrivateStorageLocatorPaths('C:\\AppData');
    expect(fs.files.has(paths.backupPath)).toBe(true);
  });

  it('load() returns null (never a fabricated record) for corrupt JSON on disk', () => {
    const paths = resolvePrivateStorageLocatorPaths('C:\\AppData');
    const fs = fakeFs({ [paths.filePath]: 'not json at all' });
    const store = new PrivateStorageLocatorStore('C:\\AppData', fs);
    expect(store.exists()).toBe(true);
    expect(store.load()).toBeNull();
  });

  it('load() rejects a record with an unsupported schema version rather than trusting it blindly', () => {
    const paths = resolvePrivateStorageLocatorPaths('C:\\AppData');
    const fs = fakeFs({ [paths.filePath]: JSON.stringify({ ...sampleRecord, schemaVersion: 2 }) });
    const store = new PrivateStorageLocatorStore('C:\\AppData', fs);
    expect(store.load()).toBeNull();
  });
});
