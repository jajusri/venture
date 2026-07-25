import { describe, expect, it, vi } from 'vitest';

import {
  formatBuildInfoForDiagnostics,
  loadBuildInfo,
  parseBuildInfoJson,
  detectVersionMismatch,
  DEV_BUILD_INFO_FALLBACK,
} from '../../src/application/release/build-info.js';
import {
  isAutoUpdatePermitted,
  isControlledPilotMode,
  isDevelopmentDiagnosticsPermitted,
  isProductionReleaseMode,
  ReleaseMode,
  resolveReleaseMode,
  UnknownReleaseModeError,
} from '../../src/application/release/release-mode.js';
import {
  assertDeletionWithinAppDataRoot,
  resolveAppDataLayout,
} from '../../src/application/release/app-data-layout.js';
import {
  buildConnectorChildEnvironment,
  resolvePackagedConnectorPaths,
} from '../../src/application/release/connector-packaged-paths.js';
import { requestDesktopSingleInstance } from '../../src/application/release/single-instance.js';

describe('release mode contract', () => {
  it('resolves controlled_pilot explicitly', () => {
    expect(resolveReleaseMode({ envValue: 'controlled_pilot' })).toBe(ReleaseMode.ControlledPilot);
  });

  it('fails closed on unknown mode', () => {
    expect(() => resolveReleaseMode({ envValue: 'staging' })).toThrow(UnknownReleaseModeError);
  });

  it('does not infer production from NODE_ENV alone', () => {
    const previous = process.env.BUDCOM_RELEASE_MODE;
    delete process.env.BUDCOM_RELEASE_MODE;
    try {
      expect(resolveReleaseMode({ isPackaged: false })).toBe(ReleaseMode.Development);
    } finally {
      if (previous === undefined) {
        delete process.env.BUDCOM_RELEASE_MODE;
      } else {
        process.env.BUDCOM_RELEASE_MODE = previous;
      }
    }
    expect(isProductionReleaseMode(ReleaseMode.Development)).toBe(false);
  });

  it('includes release mode in build metadata diagnostics', () => {
    const formatted = formatBuildInfoForDiagnostics({
      ...DEV_BUILD_INFO_FALLBACK,
      releaseMode: ReleaseMode.ControlledPilot,
    });
    expect(formatted.releaseMode).toBe('controlled_pilot');
    expect(JSON.stringify(formatted)).not.toContain('secret');
  });

  it('blocks auto-update outside production', () => {
    expect(isAutoUpdatePermitted(ReleaseMode.ControlledPilot)).toBe(false);
    expect(isAutoUpdatePermitted(ReleaseMode.Production)).toBe(true);
  });

  it('permits development diagnostics only in development/test', () => {
    expect(isDevelopmentDiagnosticsPermitted(ReleaseMode.Development)).toBe(true);
    expect(isDevelopmentDiagnosticsPermitted(ReleaseMode.ControlledPilot)).toBe(false);
    expect(isControlledPilotMode(ReleaseMode.ControlledPilot)).toBe(true);
  });
});

describe('build identity', () => {
  it('parses valid build metadata', () => {
    const parsed = parseBuildInfoJson(JSON.stringify({
      applicationVersion: '0.4.3',
      desktopVersion: '0.4.3',
      connectorVersion: '0.3.1',
      releaseMode: 'controlled_pilot',
    }));
    expect(parsed?.desktopVersion).toBe('0.4.3');
    expect(parsed?.checksumAlgorithm).toBe('sha256');
  });

  it('uses explicit local fallback', () => {
    const info = loadBuildInfo({ searchPaths: [], fallback: DEV_BUILD_INFO_FALLBACK });
    expect(info.gitCommit).toBe('unknown');
    expect(info.dirtyTree).toBe(true);
  });

  it('detects desktop/application version mismatch', () => {
    expect(detectVersionMismatch({
      ...DEV_BUILD_INFO_FALLBACK,
      applicationVersion: '9.9.9',
      desktopVersion: '0.4.3',
    })).toContain('differs');
  });
});

describe('application data layout', () => {
  it('keeps mutable data outside install directory on packaged builds', () => {
    const layout = resolveAppDataLayout({
      userDataDir: 'C:\\Users\\Tester\\AppData\\Roaming\\budcom-desktop',
      isPackaged: true,
      installRoot: 'C:\\Program Files\\Budcom Desktop',
    });
    expect(layout.installRoot).toContain('Program Files');
    expect(layout.connectorDatabaseDir).toContain('AppData');
    expect(layout.connectorDatabaseDir).not.toContain('Program Files');
  });

  it('rejects cwd fallback for mutable data', () => {
    expect(() => resolveAppDataLayout({
      userDataDir: process.cwd(),
      isPackaged: false,
    })).toThrow();
  });

  it('rejects deletion outside app-data root', () => {
    expect(() => assertDeletionWithinAppDataRoot(
      'C:\\Users\\Tester\\AppData\\outside.txt',
      'C:\\Users\\Tester\\AppData\\Roaming\\budcom-desktop',
    )).toThrow();
  });
});

describe('connector packaged paths', () => {
  it('resolves development repository layout', () => {
    const paths = resolvePackagedConnectorPaths({ isPackaged: false });
    expect(paths.connectorEntryScript.endsWith('main.js')).toBe(true);
  });

  it('resolves packaged resources layout', () => {
    const paths = resolvePackagedConnectorPaths({
      isPackaged: true,
      resourcesPath: 'C:\\app\\resources',
    });
    expect(paths.connectorEntryScript).toContain('resources');
    expect(paths.connectorEntryScript).toContain('connector');
  });

  it('minimizes child process environment', () => {
    const env = buildConnectorChildEnvironment({
      PATH: 'C:\\Windows',
      SECRET_TOKEN: 'must-not-copy',
      BUDCOM_RELEASE_MODE: 'controlled_pilot',
    }, { BUDCOM_CONNECTOR_PORT: '8080' });
    expect(env.PATH).toBe('C:\\Windows');
    expect(env.SECRET_TOKEN).toBeUndefined();
    expect(env.BUDCOM_CONNECTOR_PORT).toBe('8080');
  });
});

describe('single instance ownership', () => {
  it('quits when lock is not acquired', () => {
    const quit = vi.fn();
    const result = requestDesktopSingleInstance({
      requestSingleInstanceLock: () => false,
      quit,
    });
    expect(result.acquired).toBe(false);
    expect(result.shouldQuit).toBe(true);
    expect(quit).toHaveBeenCalled();
  });
});
