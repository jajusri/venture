import { describe, expect, it, vi } from 'vitest';
import type fs from 'node:fs';

import {
  formatBuildInfoForDiagnostics,
  loadBuildInfo,
  parseBuildInfoJson,
  detectVersionMismatch,
  DEV_BUILD_INFO_FALLBACK,
  defaultBuildInfoSearchPaths,
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
  assertPackagedMutablePathsOutsideInstallRoot,
  ensureAppDataDirectories,
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

  it('fails closed when a packaged build silently falls back to development', () => {
    expect(() => resolveReleaseMode({ envValue: 'development', buildInfoMode: 'development', isPackaged: true }))
      .toThrow(/packaged builds/i);
  });

  it('does not infer production from NODE_ENV alone', () => {
    const previous = process.env.VENTURE_RELEASE_MODE;
    delete process.env.VENTURE_RELEASE_MODE;
    try {
      expect(resolveReleaseMode({ isPackaged: false })).toBe(ReleaseMode.Development);
    } finally {
      if (previous === undefined) {
        delete process.env.VENTURE_RELEASE_MODE;
      } else {
        process.env.VENTURE_RELEASE_MODE = previous;
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
  it('searches generated dist/main build-info from packaged application code', () => {
    const candidates = defaultBuildInfoSearchPaths('C:\\app\\resources\\app.asar\\dist\\application\\release');
    expect(candidates.map((candidate) => candidate.replace(/\\/g, '/')))
      .toContain('C:/app/resources/app.asar/dist/main/build-info.json');
  });
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
      userDataDir: 'C:\\Users\\Tester\\AppData\\Roaming\\venture-desktop',
      isPackaged: true,
      installRoot: 'C:\\Program Files\\Venture Desktop',
    });
    expect(layout.installRoot).toContain('Program Files');
    expect(layout.connectorDatabaseDir).toContain('AppData');
    expect(layout.connectorDatabaseDir).not.toContain('Program Files');
    expect(layout.connectorTallyAuditPath).toContain('AppData');
    expect(layout.connectorTallyAuditPath).not.toContain('Program Files');
    expect(() => assertPackagedMutablePathsOutsideInstallRoot(layout)).not.toThrow();
  });

  it('places the connector transport identity outside the install directory too (TD-018)', () => {
    const layout = resolveAppDataLayout({
      userDataDir: 'C:\\Users\\Tester\\AppData\\Roaming\\venture-desktop',
      isPackaged: true,
      installRoot: 'C:\\Program Files\\Venture Desktop',
    });
    expect(layout.connectorTransportIdentityDir).toContain('AppData');
    expect(layout.connectorTransportIdentityDir).not.toContain('Program Files');
    // Distinct from the database dir — a sibling under the same persistent userData root.
    expect(layout.connectorTransportIdentityDir).not.toBe(layout.connectorDatabaseDir);
  });

  it('ensureAppDataDirectories creates the connector transport identity directory', () => {
    const layout = resolveAppDataLayout({
      userDataDir: 'C:\\Users\\Tester\\AppData\\Roaming\\venture-desktop',
      isPackaged: true,
      installRoot: 'C:\\Program Files\\Venture Desktop',
    });
    const created: string[] = [];
    ensureAppDataDirectories(layout, {
      mkdirSync: (dir: fs.PathLike) => {
        created.push(String(dir));
        return undefined;
      },
    });
    expect(created).toContain(layout.connectorTransportIdentityDir);
  });

  it('rejects cwd fallback for mutable data', () => {
    expect(() => resolveAppDataLayout({
      userDataDir: process.cwd(),
      isPackaged: false,
    })).toThrow();
  });

  it('fails closed when any packaged mutable path resolves beneath the install root', () => {
    const layout = resolveAppDataLayout({
      userDataDir: 'C:\\Program Files\\Venture Desktop\\mutable',
      isPackaged: true,
      installRoot: 'C:\\Program Files\\Venture Desktop',
    });
    expect(() => assertPackagedMutablePathsOutsideInstallRoot(layout)).toThrow(/outside the install root/i);
  });

  it('rejects deletion outside app-data root', () => {
    expect(() => assertDeletionWithinAppDataRoot(
      'C:\\Users\\Tester\\AppData\\outside.txt',
      'C:\\Users\\Tester\\AppData\\Roaming\\venture-desktop',
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
      VENTURE_RELEASE_MODE: 'controlled_pilot',
    }, { VENTURE_CONNECTOR_PORT: '8080' });
    expect(env.PATH).toBe('C:\\Windows');
    expect(env.SECRET_TOKEN).toBeUndefined();
    expect(env.VENTURE_CONNECTOR_PORT).toBe('8080');
  });

  it('passes Desktop-supplied stable Connector identity through to the child (VENTURE_CONNECTOR_ID/NAME)', () => {
    const env = buildConnectorChildEnvironment({ PATH: 'C:\\Windows' }, {
      VENTURE_CONNECTOR_ID: '9c98ff3c-3b1c-4429-a1a9-4055ef4c95e4',
      VENTURE_CONNECTOR_NAME: 'Front Desk',
    });
    expect(env.VENTURE_CONNECTOR_ID).toBe('9c98ff3c-3b1c-4429-a1a9-4055ef4c95e4');
    expect(env.VENTURE_CONNECTOR_NAME).toBe('Front Desk');
  });

  it('passes the secure-pairing child-env overrides through to the child when explicitly supplied (Phase 3L)', () => {
    const env = buildConnectorChildEnvironment({ PATH: 'C:\\Windows' }, {
      VENTURE_SECURE_PAIRING_ENABLED: 'true',
      VENTURE_SECURE_TRANSPORT_ENABLED: 'true',
      VENTURE_SECURE_TRANSPORT_PORT: '8443',
      VENTURE_DESKTOP_CONTROL_TOKEN: 'a-fresh-per-launch-token',
    });
    expect(env.VENTURE_SECURE_PAIRING_ENABLED).toBe('true');
    expect(env.VENTURE_SECURE_TRANSPORT_ENABLED).toBe('true');
    expect(env.VENTURE_SECURE_TRANSPORT_PORT).toBe('8443');
    expect(env.VENTURE_DESKTOP_CONTROL_TOKEN).toBe('a-fresh-per-launch-token');
  });

  it('does not pass the secure-pairing keys through at all when they are absent from overrides (default-off compatibility)', () => {
    const env = buildConnectorChildEnvironment({ PATH: 'C:\\Windows' }, { VENTURE_CONNECTOR_PORT: '8080' });
    expect(env.VENTURE_SECURE_PAIRING_ENABLED).toBeUndefined();
    expect(env.VENTURE_SECURE_TRANSPORT_ENABLED).toBeUndefined();
    expect(env.VENTURE_SECURE_TRANSPORT_PORT).toBeUndefined();
    expect(env.VENTURE_DESKTOP_CONTROL_TOKEN).toBeUndefined();
  });

  it('never passes an arbitrary, non-allowlisted secret-looking key through, even under a plausible name', () => {
    const env = buildConnectorChildEnvironment({ PATH: 'C:\\Windows' }, {
      VENTURE_DESKTOP_CONTROL_TOKEN: 'real-token',
      VENTURE_SOME_OTHER_SECRET: 'must-not-copy',
    } as Record<string, string>);
    expect(env.VENTURE_DESKTOP_CONTROL_TOKEN).toBe('real-token');
    expect((env as Record<string, string | undefined>).VENTURE_SOME_OTHER_SECRET).toBeUndefined();
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
