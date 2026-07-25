import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import {
  assertNotProtectedWriteTarget,
  assertWriteTargetContained,
  isPathContainedInRoot,
} from '../../../src/infrastructure/security/path-containment.js';

describe('path containment', () => {
  it('allows contained child directories', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-contain-root-'));
    const child = path.join(root, 'backups');
    expect(assertWriteTargetContained({ candidatePath: child, rootPath: root })).toBe(path.resolve(child));
  });

  it('rejects path traversal outside the root', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-contain-root-'));
    expect(() =>
      assertWriteTargetContained({
        candidatePath: path.join(root, '..', 'escape'),
        rootPath: root,
      }),
    ).toThrow(/within the approved root/i);
  });

  it('rejects symlink segments beneath the root', () => {
    if (process.platform === 'win32') {
      return;
    }
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-contain-root-'));
    const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-contain-outside-'));
    const linkPath = path.join(root, 'linked-backups');
    fs.symlinkSync(outside, linkPath, 'dir');
    expect(() =>
      assertWriteTargetContained({
        candidatePath: path.join(linkPath, 'budcom-ledger-1.db'),
        rootPath: root,
      }),
    ).toThrow(/symbolic link or junction/i);
  });

  it('rejects non-existing targets beneath a linked parent', () => {
    if (process.platform === 'win32') {
      return;
    }
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-contain-root-'));
    const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-contain-outside-'));
    fs.symlinkSync(outside, path.join(root, 'linked'), 'dir');
    expect(() =>
      assertWriteTargetContained({
        candidatePath: path.join(root, 'linked', 'new', 'budcom-ledger-1.db'),
        rootPath: root,
      }),
    ).toThrow(/symbolic link or junction|outside the approved root/i);
  });

  it('blocks protected write targets', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-contain-root-'));
    const liveDb = path.join(root, 'budcom-ledger.db');
    fs.writeFileSync(liveDb, 'live', 'utf8');
    expect(() => assertNotProtectedWriteTarget(liveDb, [liveDb])).toThrow(/protected file/i);
  });

  it('compares containment case-insensitively on Windows', () => {
    if (process.platform !== 'win32') {
      return;
    }
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-contain-root-'));
    const child = path.join(root, 'Backups');
    expect(isPathContainedInRoot(child, root.toUpperCase())).toBe(true);
  });
});
