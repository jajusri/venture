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

  it('ignores BUDCOM_USER_DATA_DIR outside installed probe mode', () => {
    const env = { BUDCOM_USER_DATA_DIR: path.join(os.tmpdir(), 'budcom-profile') };
    expect(readUserDataDirOverride(env)).toBeNull();
    expect(isInstalledProbeMode(env)).toBe(false);
  });

  it('honours BUDCOM_USER_DATA_DIR only in installed probe mode under temp root', () => {
    const probeDir = path.join(os.tmpdir(), `budcom-probe-${Date.now()}`);
    const env = {
      BUDCOM_INSTALLED_PROBE_MODE: '1',
      BUDCOM_USER_DATA_DIR: probeDir,
    };
    expect(readUserDataDirOverride(env)).toBe(path.resolve(probeDir));
  });

  it('prefers BUDCOM_USER_DATA_DIR over argv override in probe mode', () => {
    const env = {
      BUDCOM_INSTALLED_PROBE_MODE: '1',
      BUDCOM_USER_DATA_DIR: path.join(os.tmpdir(), 'budcom-profile'),
    };
    const argv = ['electron.exe', `--user-data-dir=${path.join(os.tmpdir(), 'ignored')}`];
    expect(readUserDataDirOverride(env, argv)).toBe(path.resolve(env.BUDCOM_USER_DATA_DIR!));
  });

  it('rejects probe user-data override outside temp root', () => {
    const outsideTemp = path.join(path.parse(os.tmpdir()).root, 'budcom-probe-outside-temp-test');
    expect(() => validateInstalledProbeUserDataDir(outsideTemp))
      .toThrow(/temp directory/i);
  });

  it('applies userData override through electron app.setPath in probe mode', () => {
    const setPath = vi.fn();
    const app = { setPath };
    const probeDir = path.join(os.tmpdir(), `budcom-probe-${Date.now()}`);
    const override = applyUserDataDirOverride(
      {
        BUDCOM_INSTALLED_PROBE_MODE: '1',
        BUDCOM_USER_DATA_DIR: probeDir,
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
      { BUDCOM_USER_DATA_DIR: path.join(os.tmpdir(), 'budcom-profile') },
      app as never,
    );
    expect(override).toBeNull();
    expect(setPath).not.toHaveBeenCalled();
  });
});
