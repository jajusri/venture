import { describe, expect, it } from 'vitest';

import {
  findNodeExecutableOnPath,
  resolveConnectorHostExecutable,
  shouldSpawnConnectorViaElectronNode,
  tryResolveDevelopmentConnectorNodeExecutable,
} from '../../src/application/release/connector-packaged-paths.js';

/** Deterministic fake filesystem — only the paths listed as existing are "found". */
function fakeFs(existingPaths: readonly string[]) {
  const set = new Set(existingPaths.map((p) => p.toLowerCase()));
  return { existsSync: (p: string) => set.has(p.toLowerCase()) };
}

describe('findNodeExecutableOnPath', () => {
  it('finds node.exe in the first PATH directory that has it', () => {
    const pathEnv = ['C:\\no\\node\\here', 'C:\\Program Files\\nodejs', 'C:\\also\\nothing'].join(';');
    const fs = fakeFs(['C:\\Program Files\\nodejs\\node.exe']);

    const found = findNodeExecutableOnPath(pathEnv, fs);

    expect(found).toBe('C:\\Program Files\\nodejs\\node.exe');
  });

  it('returns null when no PATH directory has node.exe', () => {
    const pathEnv = ['C:\\a', 'C:\\b'].join(';');
    const fs = fakeFs([]);

    expect(findNodeExecutableOnPath(pathEnv, fs)).toBeNull();
  });

  it('returns null when PATH is undefined', () => {
    expect(findNodeExecutableOnPath(undefined, fakeFs([]))).toBeNull();
  });

  it('ignores empty PATH segments without throwing', () => {
    const pathEnv = [';', '', 'C:\\Program Files\\nodejs', ''].join(';');
    const fs = fakeFs(['C:\\Program Files\\nodejs\\node.exe']);

    expect(findNodeExecutableOnPath(pathEnv, fs)).toBe('C:\\Program Files\\nodejs\\node.exe');
  });
});

describe('resolveConnectorHostExecutable — development mode', () => {
  it('selects a real, PATH-discovered Node executable instead of bare Electron GUI mode', () => {
    const resolved = resolveConnectorHostExecutable({
      isPackaged: false,
      currentExecPath: 'C:\\Apps\\Budcom Desktop\\Budcom Desktop.exe', // simulates Electron's main process
      env: { PATH: 'C:\\Program Files\\nodejs' },
      fsImpl: fakeFs(['C:\\Program Files\\nodejs\\node.exe']),
      sqliteChecker: () => true,
    });

    expect(resolved).toBe('C:\\Program Files\\nodejs\\node.exe');
    expect(resolved.toLowerCase()).not.toContain('electron');
  });

  it('returns process.execPath directly when already running under a real Node executable', () => {
    const resolved = resolveConnectorHostExecutable({
      isPackaged: false,
      currentExecPath: 'C:\\Program Files\\nodejs\\node.exe',
      env: { PATH: '' },
      fsImpl: fakeFs([]),
      sqliteChecker: () => true,
    });

    expect(resolved).toBe('C:\\Program Files\\nodejs\\node.exe');
  });

  it('an explicit executable override always wins, even under Electron', () => {
    const resolved = resolveConnectorHostExecutable({
      isPackaged: false,
      overrideExecutable: 'D:\\custom\\node.exe',
      currentExecPath: 'C:\\Apps\\Budcom Desktop\\Budcom Desktop.exe',
      env: { PATH: '' },
      fsImpl: fakeFs([]),
    });

    expect(resolved).toBe('D:\\custom\\node.exe');
  });

  it('rejects an incompatible Node runtime (no node:sqlite) with a clear, actionable error', () => {
    expect(() =>
      resolveConnectorHostExecutable({
        isPackaged: false,
        currentExecPath: 'C:\\Apps\\Budcom Desktop\\Budcom Desktop.exe',
        env: { PATH: 'C:\\Old\\Node' },
        fsImpl: fakeFs(['C:\\Old\\Node\\node.exe']),
        sqliteChecker: () => false,
      }),
    ).toThrow(/node:sqlite/);
  });

  it('fails clearly when no compatible development Node runtime is on PATH', () => {
    expect(() =>
      resolveConnectorHostExecutable({
        isPackaged: false,
        currentExecPath: 'C:\\Apps\\Budcom Desktop\\Budcom Desktop.exe',
        env: { PATH: 'C:\\nothing\\here' },
        fsImpl: fakeFs([]),
      }),
    ).toThrow(/PATH/);
  });

  it('never depends on a machine-specific absolute Node path or developer username — PATH only', () => {
    // No hardcoded "C:\\Users\\<name>\\..." anywhere in resolution: only PATH + discovery.
    const resolved = resolveConnectorHostExecutable({
      isPackaged: false,
      currentExecPath: 'C:\\Apps\\Budcom Desktop\\Budcom Desktop.exe',
      env: { PATH: ['C:\\first', 'C:\\Program Files\\nodejs', 'C:\\last'].join(';') },
      fsImpl: fakeFs(['C:\\Program Files\\nodejs\\node.exe']),
      sqliteChecker: () => true,
    });

    expect(resolved).toBe('C:\\Program Files\\nodejs\\node.exe');
  });
});

describe('resolveConnectorHostExecutable — packaged mode', () => {
  it('requires resourcesPath and never silently falls through to development resolution', () => {
    expect(() => resolveConnectorHostExecutable({ isPackaged: true })).toThrow(/resourcesPath/);
  });

  it('an explicit executable override still wins in packaged mode', () => {
    const resolved = resolveConnectorHostExecutable({
      isPackaged: true,
      resourcesPath: 'C:\\Apps\\Budcom Desktop\\resources',
      overrideExecutable: 'D:\\custom\\node.exe',
    });

    expect(resolved).toBe('D:\\custom\\node.exe');
  });
});

describe('tryResolveDevelopmentConnectorNodeExecutable', () => {
  it('returns the resolved executable with no error on success', () => {
    const result = tryResolveDevelopmentConnectorNodeExecutable({
      currentExecPath: 'C:\\Apps\\Budcom Desktop\\Budcom Desktop.exe',
      env: { PATH: 'C:\\Program Files\\nodejs' },
      fsImpl: fakeFs(['C:\\Program Files\\nodejs\\node.exe']),
      sqliteChecker: () => true,
    });

    expect(result).toEqual({ executable: 'C:\\Program Files\\nodejs\\node.exe', error: null });
  });

  it('never throws — returns a null executable and a clear error message on failure', () => {
    const result = tryResolveDevelopmentConnectorNodeExecutable({
      currentExecPath: 'C:\\Apps\\Budcom Desktop\\Budcom Desktop.exe',
      env: { PATH: '' },
      fsImpl: fakeFs([]),
    });

    expect(result.executable).toBeNull();
    expect(result.error).toMatch(/PATH/);
  });
});

describe('shouldSpawnConnectorViaElectronNode', () => {
  it('never recommends Electron-as-Node — Electron cannot satisfy node:sqlite', () => {
    expect(shouldSpawnConnectorViaElectronNode('C:\\Apps\\Budcom Desktop\\Budcom Desktop.exe')).toBe(false);
    expect(shouldSpawnConnectorViaElectronNode('C:\\Program Files\\nodejs\\node.exe')).toBe(false);
  });
});
