import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { describe, expect, it, vi } from 'vitest';

import {
  applyUserDataDirOverride,
  isInstalledProbeMode,
  readUserDataDirOverride,
  sanitizeDesktopProcessEnvironment,
  validateInstalledProbeUserDataDir,
} from '../../src/application/release/startup-environment.js';

describe('startup-environment', () => {
  it('strips ELECTRON_RUN_AS_NODE from the desktop process environment', () => {
    const env = { ELECTRON_RUN_AS_NODE: '1', PATH: 'C:\\Windows' };
    sanitizeDesktopProcessEnvironment(env);
    expect(env.ELECTRON_RUN_AS_NODE).toBeUndefined();
    expect(env.PATH).toBe('C:\\Windows');
  });

  it('ignores VENTURE_USER_DATA_DIR outside installed probe mode', () => {
    const env = { VENTURE_USER_DATA_DIR: path.join(os.tmpdir(), 'venture-profile') };
    expect(readUserDataDirOverride(env)).toBeNull();
    expect(isInstalledProbeMode(env)).toBe(false);
  });

  it('honours VENTURE_USER_DATA_DIR only in installed probe mode under temp root', () => {
    const probeDir = path.join(os.tmpdir(), `venture-probe-${Date.now()}`);
    const env = {
      VENTURE_INSTALLED_PROBE_MODE: '1',
      VENTURE_USER_DATA_DIR: probeDir,
    };
    expect(readUserDataDirOverride(env)).toBe(path.resolve(probeDir));
  });

  it('prefers VENTURE_USER_DATA_DIR over argv override in probe mode', () => {
    const env = {
      VENTURE_INSTALLED_PROBE_MODE: '1',
      VENTURE_USER_DATA_DIR: path.join(os.tmpdir(), 'venture-profile'),
    };
    const argv = ['electron.exe', `--user-data-dir=${path.join(os.tmpdir(), 'ignored')}`];
    expect(readUserDataDirOverride(env, argv)).toBe(path.resolve(env.VENTURE_USER_DATA_DIR!));
  });

  it('rejects probe user-data override outside temp root', () => {
    const outsideTemp = path.join(path.parse(os.tmpdir()).root, 'venture-probe-outside-temp-test');
    expect(() => validateInstalledProbeUserDataDir(outsideTemp))
      .toThrow(/temp directory/i);
  });

  it('applies userData override through electron app.setPath in probe mode', () => {
    const setPath = vi.fn();
    const app = { setPath };
    const probeDir = path.join(os.tmpdir(), `venture-probe-${Date.now()}`);
    const override = applyUserDataDirOverride(
      {
        VENTURE_INSTALLED_PROBE_MODE: '1',
        VENTURE_USER_DATA_DIR: probeDir,
      },
      app as never,
    );
    expect(override).toBe(path.resolve(probeDir));
    expect(setPath).toHaveBeenCalledWith('userData', path.resolve(probeDir));
  });

  it('does not apply userData override without probe mode', () => {
    const setPath = vi.fn();
    const app = { setPath };
    const override = applyUserDataDirOverride(
      { VENTURE_USER_DATA_DIR: path.join(os.tmpdir(), 'venture-profile') },
      app as never,
    );
    expect(override).toBeNull();
    expect(setPath).not.toHaveBeenCalled();
  });
});
