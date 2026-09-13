import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import {
  assertWriteTargetContained,
  isPathContainedInRoot,
} from '../../src/application/path-containment.js';
import { validateExportDirectory } from '../../src/application/ipc-allowlist.js';

const mintedDirs: string[] = [];

afterEach(() => {
  for (const dir of mintedDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function mkTempDir(prefix: string): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
  mintedDirs.push(dir);
  return dir;
}

describe('desktop path containment', () => {
  it('allows undefined export directory for automatic owned export', () => {
    const root = mkTempDir('venture-export-owned-');
    expect(validateExportDirectory(undefined, root)).toBeUndefined();
  });

  it('allows contained child export directories', () => {
    const root = mkTempDir('venture-export-owned-');
    const child = path.join(root, 'venture-diagnostics-2026-07-25T00-00-00-000Z');
    expect(validateExportDirectory(child, root)).toBe(path.resolve(child));
  });

  it('rejects arbitrary path injection outside owned export root', () => {
    const root = mkTempDir('venture-export-owned-');
    expect(() => validateExportDirectory('/tmp/evil', root)).toThrow(/within the approved root|app-owned/i);
    expect(() => validateExportDirectory(path.join(root, '..', 'escape'), root)).toThrow(
      /within the approved root|app-owned/i,
    );
  });

  it('rejects symlink escape attempts beneath the owned export root', () => {
    if (process.platform === 'win32') {
      return;
    }
    const root = mkTempDir('venture-export-owned-');
    const outside = mkTempDir('venture-export-outside-');
    fs.symlinkSync(outside, path.join(root, 'linked-export'), 'dir');
    expect(() =>
      validateExportDirectory(path.join(root, 'linked-export', 'bundle'), root),
    ).toThrow(/symbolic link or junction|outside the approved root/i);
  });

  it('detects path containment consistently', () => {
    const root = path.join(os.tmpdir(), 'venture-owned');
    expect(isPathContainedInRoot(path.join(root, 'child'), root)).toBe(true);
    expect(isPathContainedInRoot(path.join(root, '..', 'outside'), root)).toBe(false);
  });
});

describe('diagnostic export contract characterization', () => {
  it('documents renderer preload exposes export without custom path arguments', () => {
    const preloadSource = fs.readFileSync(
      path.join(process.cwd(), 'src/preload/preload.ts'),
      'utf8',
    );
    expect(preloadSource).toMatch(/exportDiagnosticsBundle:\s*\(\)\s*=>\s*ipcRenderer\.invoke\('desktop:export-diagnostics-bundle'\)/);
  });

  it('documents renderer UI invokes export without supplying a target directory', () => {
    const appSource = fs.readFileSync(path.join(process.cwd(), 'src/renderer/scripts/app.ts'), 'utf8');
    expect(appSource).toMatch(/exportDiagnosticsBundle\(\)/);
    expect(appSource).not.toMatch(/exportDiagnosticsBundle\([^)]+\)/);
  });
});
