import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  buildDiagnosticExportDirName,
  DIAGNOSTIC_EXPORT_BUNDLE_FILENAME,
  retentionPeriodMs,
} from '../../src/application/diagnostic-export-convention.js';
import {
  DiagnosticExportRetentionService,
  type DiagnosticExportRetentionFs,
} from '../../src/application/diagnostic-export-retention-service.js';
import type { StructuredLogInput } from '../../src/application/log-service.js';

const DAY_MS = 24 * 60 * 60 * 1000;

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

function createExportBundle(exportRoot: string, generatedAtIso: string, content = '{}'): string {
  const dirName = buildDiagnosticExportDirName(generatedAtIso);
  const dirPath = path.join(exportRoot, dirName);
  fs.mkdirSync(dirPath, { recursive: true });
  fs.writeFileSync(path.join(dirPath, DIAGNOSTIC_EXPORT_BUNDLE_FILENAME), content, 'utf8');
  return dirPath;
}

function createService(
  exportRoot: string,
  overrides: {
    readonly nowMs?: number;
    readonly retentionDays?: number;
    readonly fsImpl?: DiagnosticExportRetentionFs;
    readonly logs?: StructuredLogInput[];
  } = {},
): DiagnosticExportRetentionService {
  const logs = overrides.logs ?? [];
  return new DiagnosticExportRetentionService({
    fsImpl: overrides.fsImpl,
    now: () => overrides.nowMs ?? Date.now(),
    log: (input) => logs.push(input),
  });
}

function cleanupAt(
  service: DiagnosticExportRetentionService,
  exportRoot: string,
  retentionDays: number,
  nowMs: number,
) {
  return service.cleanup({ exportDir: exportRoot, retentionDays, nowMs });
}

describe('DiagnosticExportRetentionService', () => {
  it('1. treats a missing export directory as a successful no-op', () => {
    const service = createService('/missing/path');
    const result = cleanupAt(service, path.join(os.tmpdir(), 'budcom-missing-exports'), 14, Date.now());
    expect(result).toMatchObject({
      ok: true,
      scannedCount: 0,
      deletedCount: 0,
    });
  });

  it('2. treats an empty directory as a successful no-op', () => {
    const exportRoot = mkTempDir('budcom-empty-exports-');
    const service = createService(exportRoot);
    const result = cleanupAt(service, exportRoot, 14, Date.now());
    expect(result).toMatchObject({ ok: true, scannedCount: 0, deletedCount: 0 });
  });

  it('3. never deletes unrelated files', () => {
    const exportRoot = mkTempDir('budcom-unrelated-exports-');
    const unrelated = path.join(exportRoot, 'notes.txt');
    fs.writeFileSync(unrelated, 'keep', 'utf8');
    const now = Date.parse('2026-07-25T12:00:00.000Z');
    createExportBundle(exportRoot, '2026-06-01T00:00:00.000Z');
    cleanupAt(createService(exportRoot), exportRoot, 7, now);
    expect(fs.existsSync(unrelated)).toBe(true);
  });

  it('4. never deletes backup files', () => {
    const exportRoot = mkTempDir('budcom-backup-exports-');
    const backup = path.join(exportRoot, 'desktop-config.backup.json');
    fs.writeFileSync(backup, '{"schemaVersion":1}', 'utf8');
    const now = Date.parse('2026-07-25T12:00:00.000Z');
    cleanupAt(createService(exportRoot), exportRoot, 7, now);
    expect(fs.existsSync(backup)).toBe(true);
  });

  it('5. never deletes temporary or non-owned files', () => {
    const exportRoot = mkTempDir('budcom-temp-exports-');
    fs.writeFileSync(path.join(exportRoot, 'partial-export.tmp'), 'temp', 'utf8');
    fs.mkdirSync(path.join(exportRoot, 'budcom-diagnostics-not-a-valid-timestamp'), { recursive: true });
    const now = Date.parse('2026-07-25T12:00:00.000Z');
    const result = cleanupAt(createService(exportRoot), exportRoot, 7, now);
    expect(fs.existsSync(path.join(exportRoot, 'partial-export.tmp'))).toBe(true);
    expect(fs.existsSync(path.join(exportRoot, 'budcom-diagnostics-not-a-valid-timestamp'))).toBe(true);
    expect(result.skippedCount).toBeGreaterThanOrEqual(2);
  });

  it('6. never deletes unrelated directories', () => {
    const exportRoot = mkTempDir('budcom-unrelated-dir-exports-');
    const otherDir = path.join(exportRoot, 'manual-support-folder');
    fs.mkdirSync(otherDir);
    fs.writeFileSync(path.join(otherDir, 'readme.txt'), 'keep', 'utf8');
    cleanupAt(createService(exportRoot), exportRoot, 7, Date.parse('2026-07-25T12:00:00.000Z'));
    expect(fs.existsSync(otherDir)).toBe(true);
  });

  it('7. does not delete symbolic links', () => {
    if (process.platform === 'win32') {
      return;
    }
    const exportRoot = mkTempDir('budcom-symlink-exports-');
    const target = createExportBundle(exportRoot, '2026-06-01T00:00:00.000Z');
    const linkPath = path.join(exportRoot, buildDiagnosticExportDirName('2026-06-02T00:00:00.000Z'));
    fs.symlinkSync(target, linkPath, 'dir');
    const result = cleanupAt(
      createService(exportRoot),
      exportRoot,
      7,
      Date.parse('2026-07-25T12:00:00.000Z'),
    );
    expect(fs.lstatSync(linkPath).isSymbolicLink()).toBe(true);
    expect(result.skippedCount).toBeGreaterThanOrEqual(1);
  });

  it('8. keeps eligible exports inside the retention window', () => {
    const exportRoot = mkTempDir('budcom-inside-retention-');
    const recent = createExportBundle(exportRoot, '2026-07-20T00:00:00.000Z');
    const now = Date.parse('2026-07-25T12:00:00.000Z');
    cleanupAt(createService(exportRoot), exportRoot, 14, now);
    expect(fs.existsSync(recent)).toBe(true);
  });

  it('9. deletes expired eligible exports', () => {
    const exportRoot = mkTempDir('budcom-expired-exports-');
    const older = createExportBundle(exportRoot, '2026-06-01T00:00:00.000Z');
    const newer = createExportBundle(exportRoot, '2026-07-20T00:00:00.000Z');
    const now = Date.parse('2026-07-25T12:00:00.000Z');
    const result = cleanupAt(createService(exportRoot), exportRoot, 7, now);
    expect(fs.existsSync(older)).toBe(false);
    expect(fs.existsSync(newer)).toBe(true);
    expect(result.deletedCount).toBe(1);
  });

  it('10. preserves the newest eligible export even when expired', () => {
    const exportRoot = mkTempDir('budcom-preserve-newest-');
    const newest = createExportBundle(exportRoot, '2026-07-01T00:00:00.000Z');
    const now = Date.parse('2026-07-25T12:00:00.000Z');
    const result = cleanupAt(createService(exportRoot), exportRoot, 7, now);
    expect(fs.existsSync(newest)).toBe(true);
    expect(result.preservedNewestCount).toBe(1);
    expect(result.deletedCount).toBe(0);
  });

  it('11. preserves a single expired eligible export', () => {
    const exportRoot = mkTempDir('budcom-single-expired-');
    const only = createExportBundle(exportRoot, '2026-01-01T00:00:00.000Z');
    cleanupAt(createService(exportRoot), exportRoot, 7, Date.parse('2026-07-25T12:00:00.000Z'));
    expect(fs.existsSync(only)).toBe(true);
  });

  it('12. preserves only the newest export and deletes other expired ones', () => {
    const exportRoot = mkTempDir('budcom-multi-expired-');
    createExportBundle(exportRoot, '2026-05-01T00:00:00.000Z');
    createExportBundle(exportRoot, '2026-06-01T00:00:00.000Z');
    const newest = createExportBundle(exportRoot, '2026-07-01T00:00:00.000Z');
    const result = cleanupAt(createService(exportRoot), exportRoot, 7, Date.parse('2026-07-25T12:00:00.000Z'));
    expect(fs.existsSync(newest)).toBe(true);
    expect(fs.readdirSync(exportRoot)).toHaveLength(1);
    expect(result.deletedCount).toBe(2);
    expect(result.preservedNewestCount).toBe(1);
  });

  it('13. uses deterministic filename ordering when export timestamps tie', () => {
    const exportRoot = mkTempDir('budcom-tie-order-');
    const tiedTime = Date.parse('2026-06-01T00:00:00.000Z');
    const dirA = path.join(exportRoot, 'budcom-diagnostics-2026-13-40T00-00-00-000Z');
    const dirB = path.join(exportRoot, 'budcom-diagnostics-2026-13-39T00-00-00-000Z');
    fs.mkdirSync(dirA, { recursive: true });
    fs.mkdirSync(dirB, { recursive: true });
    fs.writeFileSync(path.join(dirA, DIAGNOSTIC_EXPORT_BUNDLE_FILENAME), '{}', 'utf8');
    fs.writeFileSync(path.join(dirB, DIAGNOSTIC_EXPORT_BUNDLE_FILENAME), '{}', 'utf8');
    createExportBundle(exportRoot, '2026-05-01T00:00:00.000Z');
    const realFs = fs as unknown as DiagnosticExportRetentionFs;
    const fsImpl: DiagnosticExportRetentionFs = {
      existsSync: (p) => realFs.existsSync(p),
      readdirSync: (p) => realFs.readdirSync(p),
      lstatSync: (p) => {
        const stat = realFs.lstatSync(p);
        const base = path.basename(p);
        if (base.startsWith('budcom-diagnostics-2026-13-')) {
          return {
            isDirectory: () => stat.isDirectory(),
            isSymbolicLink: () => stat.isSymbolicLink(),
            mtimeMs: tiedTime,
          };
        }
        return {
          isDirectory: () => stat.isDirectory(),
          isSymbolicLink: () => stat.isSymbolicLink(),
          mtimeMs: stat.mtimeMs,
        };
      },
      rmSync: (p, options) => realFs.rmSync(p, options),
    };
    cleanupAt(
      createService(exportRoot, { fsImpl }),
      exportRoot,
      0,
      Date.parse('2026-12-01T00:00:00.000Z'),
    );
    expect(fs.existsSync(dirA)).toBe(true);
    expect(fs.existsSync(dirB)).toBe(false);
  });

  it('14. preserves exports with invalid timestamps conservatively', () => {
    const exportRoot = mkTempDir('budcom-invalid-ts-');
    const invalidDir = path.join(exportRoot, 'budcom-diagnostics-2026-13-40T00-00-00-000Z');
    fs.mkdirSync(invalidDir, { recursive: true });
    fs.writeFileSync(path.join(invalidDir, DIAGNOSTIC_EXPORT_BUNDLE_FILENAME), '{}', 'utf8');
    createExportBundle(exportRoot, '2026-07-01T00:00:00.000Z');
    createExportBundle(exportRoot, '2026-05-01T00:00:00.000Z');
    const realFs = fs as unknown as DiagnosticExportRetentionFs;
    const fsImpl: DiagnosticExportRetentionFs = {
      existsSync: (p) => realFs.existsSync(p),
      readdirSync: (p) => realFs.readdirSync(p),
      lstatSync: (p) => {
        const stat = realFs.lstatSync(p);
        const mtimeMs = p === invalidDir ? Number.NaN : stat.mtimeMs;
        return {
          isDirectory: () => stat.isDirectory(),
          isSymbolicLink: () => stat.isSymbolicLink(),
          mtimeMs,
        };
      },
      rmSync: (p, options) => realFs.rmSync(p, options),
    };
    const result = cleanupAt(
      createService(exportRoot, { fsImpl }),
      exportRoot,
      1,
      Date.parse('2026-07-25T00:00:00.000Z'),
    );
    expect(fs.existsSync(invalidDir)).toBe(true);
    expect(result.invalidTimestampCount).toBe(1);
  });

  it('15. continues deleting other exports when one deletion fails', () => {
    const exportRoot = mkTempDir('budcom-delete-failure-');
    createExportBundle(exportRoot, '2026-05-01T00:00:00.000Z');
    createExportBundle(exportRoot, '2026-04-01T00:00:00.000Z');
    createExportBundle(exportRoot, '2026-07-01T00:00:00.000Z');
    let deleteAttempts = 0;
    const realFs = fs as unknown as DiagnosticExportRetentionFs;
    const fsImpl: DiagnosticExportRetentionFs = {
      existsSync: (p) => realFs.existsSync(p),
      readdirSync: (p) => realFs.readdirSync(p),
      lstatSync: (p) => realFs.lstatSync(p),
      rmSync: (p, options) => {
        deleteAttempts += 1;
        if (deleteAttempts === 1) {
          throw new Error('permission denied');
        }
        realFs.rmSync(p, options);
      },
    };
    const result = cleanupAt(
      createService(exportRoot, { fsImpl }),
      exportRoot,
      7,
      Date.parse('2026-07-25T12:00:00.000Z'),
    );
    expect(result.failureCount).toBe(1);
    expect(result.deletedCount).toBe(1);
    expect(fs.readdirSync(exportRoot).length).toBe(2);
  });

  it('21. uses the existing diagnosticsRetentionDays setting through cleanup options', () => {
    const exportRoot = mkTempDir('budcom-retention-days-');
    createExportBundle(exportRoot, '2026-07-10T00:00:00.000Z');
    const retained = createExportBundle(exportRoot, '2026-07-22T00:00:00.000Z');
    cleanupAt(createService(exportRoot), exportRoot, 14, Date.parse('2026-07-25T12:00:00.000Z'));
    expect(fs.existsSync(retained)).toBe(true);
    expect(fs.readdirSync(exportRoot)).toHaveLength(1);
  });

  it('22. respects minimum and maximum supported retention values', () => {
    const exportRoot = mkTempDir('budcom-retention-bounds-');
    const old = createExportBundle(exportRoot, '2026-01-01T00:00:00.000Z');
    const recent = createExportBundle(exportRoot, '2026-07-20T00:00:00.000Z');
    cleanupAt(createService(exportRoot), exportRoot, 1, Date.parse('2026-07-25T12:00:00.000Z'));
    expect(fs.existsSync(recent)).toBe(true);
    expect(fs.existsSync(old)).toBe(false);
    cleanupAt(createService(exportRoot), exportRoot, 90, Date.parse('2026-07-25T12:00:00.000Z'));
    expect(fs.existsSync(recent)).toBe(true);
  });

  it('23. returns accurate cleanup result counts', () => {
    const exportRoot = mkTempDir('budcom-counts-');
    fs.writeFileSync(path.join(exportRoot, 'ignore.txt'), 'x', 'utf8');
    createExportBundle(exportRoot, '2026-05-01T00:00:00.000Z');
    createExportBundle(exportRoot, '2026-06-01T00:00:00.000Z');
    createExportBundle(exportRoot, '2026-07-01T00:00:00.000Z');
    const result = cleanupAt(createService(exportRoot), exportRoot, 7, Date.parse('2026-07-25T12:00:00.000Z'));
    expect(result.scannedCount).toBe(4);
    expect(result.eligibleCount).toBe(3);
    expect(result.deletedCount).toBe(2);
    expect(result.preservedNewestCount).toBe(1);
    expect(result.skippedCount).toBe(1);
  });

  it('24. logs only aggregate cleanup metadata without sensitive paths', () => {
    const exportRoot = mkTempDir('budcom-log-privacy-');
    createExportBundle(exportRoot, '2026-05-01T00:00:00.000Z');
    createExportBundle(exportRoot, '2026-07-01T00:00:00.000Z');
    const logs: StructuredLogInput[] = [];
    cleanupAt(createService(exportRoot, { logs }), exportRoot, 7, Date.parse('2026-07-25T12:00:00.000Z'));
    expect(logs).toHaveLength(1);
    const payload = JSON.stringify(logs[0]);
    expect(payload).not.toContain(exportRoot);
    expect(payload).not.toContain('<ENVELOPE');
    expect(logs[0]?.event).toBe('diagnostics_export_retention_cleanup');
    expect(logs[0]?.metadata?.deletedCount).toBeGreaterThanOrEqual(0);
  });
});

describe('retentionPeriodMs', () => {
  it('calculates retention windows in whole days', () => {
    expect(retentionPeriodMs(14)).toBe(14 * DAY_MS);
  });
});
