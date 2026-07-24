import { access, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import {
  auditRotatedPath,
  shouldRotateBeforeAppend,
  type AuditFileOperations,
} from '../../../src/tally/safety/audit-file-rotator.js';
import {
  createCapturingTallyRequestAuditor,
  createTestTallyRequestAuditor,
  sampleAuditRecord,
} from '../../helpers/tally-audit-test-helpers.js';

function createMemoryAuditFs(initial: Record<string, string> = {}): {
  readonly fs: AuditFileOperations;
  readonly files: Map<string, string>;
  readonly failures: {
    stat?: number;
    unlink?: number;
    rename?: number;
    append?: number;
    mkdir?: number;
  };
} {
  const files = new Map<string, string>(Object.entries(initial));
  const failures = {
    stat: 0,
    unlink: 0,
    rename: 0,
    append: 0,
    mkdir: 0,
  };

  const fsImpl: AuditFileOperations = {
    async stat(filePath) {
      if (failures.stat > 0) {
        failures.stat -= 1;
        throw new Error('stat failed');
      }
      if (!files.has(filePath)) {
        return 'missing';
      }
      return { size: Buffer.byteLength(files.get(filePath)!, 'utf8') };
    },
    async mkdir() {
      if (failures.mkdir > 0) {
        failures.mkdir -= 1;
        throw new Error('mkdir failed');
      }
    },
    async append(filePath, content) {
      if (failures.append > 0) {
        failures.append -= 1;
        throw new Error('append failed');
      }
      files.set(filePath, `${files.get(filePath) ?? ''}${content}`);
    },
    async unlink(filePath) {
      if (failures.unlink > 0) {
        failures.unlink -= 1;
        throw new Error('unlink failed');
      }
      files.delete(filePath);
    },
    async rename(from, to) {
      if (failures.rename > 0) {
        failures.rename -= 1;
        throw new Error('rename failed');
      }
      if (!files.has(from)) {
        throw Object.assign(new Error('missing'), { code: 'ENOENT' });
      }
      files.set(to, files.get(from)!);
      files.delete(from);
    },
  };

  return { fs: fsImpl, files, failures };
}

async function fileExists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

describe('shouldRotateBeforeAppend', () => {
  it('does not rotate empty files', () => {
    expect(shouldRotateBeforeAppend(0, 100, 200)).toBe(false);
  });

  it('rotates when append would exceed max bytes', () => {
    expect(shouldRotateBeforeAppend(150, 51, 200)).toBe(true);
  });

  it('accepts append that fits exactly at capacity', () => {
    expect(shouldRotateBeforeAppend(150, 50, 200)).toBe(false);
  });
});

describe('Tally request audit rotation (B2a)', () => {
  it('does not rotate while the file remains below threshold', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-threshold-'));
    const auditPath = join(dir, 'tally-request-audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 512, maxFiles: 3 });
    await auditor.record(sampleAuditRecord({ correlationId: 'first' }));
    expect(await fileExists(auditRotatedPath(auditPath, 1))).toBe(false);
  });

  it('rotates before append when the next line would exceed max bytes', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-rotate-'));
    const auditPath = join(dir, 'tally-request-audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 180, maxFiles: 2 });
    await writeFile(auditPath, `${'a'.repeat(170)}\n`, 'utf8');
    await auditor.record(sampleAuditRecord({ correlationId: 'after-rotate' }));
    expect(await fileExists(auditRotatedPath(auditPath, 1))).toBe(true);
    const current = await readFile(auditPath, 'utf8');
    expect(current.trim()).toContain('"correlationId":"after-rotate"');
    expect(JSON.parse(current.trim())).toMatchObject({ correlationId: 'after-rotate' });
  });

  it('creates numbered rotated files and deletes the oldest slot at the cap', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-cap-'));
    const auditPath = join(dir, 'tally-request-audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 80, maxFiles: 2 });
    await writeFile(auditPath, `${'b'.repeat(70)}\n`, 'utf8');
    await auditor.record(sampleAuditRecord({ correlationId: 'r1' }));
    await writeFile(auditPath, `${'c'.repeat(70)}\n`, 'utf8');
    await auditor.record(sampleAuditRecord({ correlationId: 'r2' }));
    expect(await fileExists(auditRotatedPath(auditPath, 1))).toBe(true);
    expect(await fileExists(auditRotatedPath(auditPath, 2))).toBe(true);
    expect(await fileExists(auditRotatedPath(auditPath, 3))).toBe(false);
  });

  it('leaves unrelated sibling files untouched during rotation', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-sibling-'));
    const auditPath = join(dir, 'tally-request-audit.jsonl');
    const siblingPath = join(dir, 'notes.txt');
    await writeFile(siblingPath, 'keep-me', 'utf8');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 80, maxFiles: 2 });
    await writeFile(auditPath, `${'d'.repeat(70)}\n`, 'utf8');
    await auditor.record(sampleAuditRecord({ correlationId: 'rotate-sibling' }));
    expect(await readFile(siblingPath, 'utf8')).toBe('keep-me');
  });

  it('writes an oversized single record once without rotation loops', async () => {
    const auditPath = '/tmp/budcom-audit-memory/audit.jsonl';
    const { fs, files } = createMemoryAuditFs();
    const auditor = createTestTallyRequestAuditor(auditPath, {
      maxBytes: 40,
      maxFiles: 2,
      fs,
    });
    await auditor.record(
      sampleAuditRecord({
        correlationId: 'oversized-record',
        xml: `<ENVELOPE>${'X'.repeat(200)}</ENVELOPE>`,
      }),
    );
    const contents = files.get(auditPath) ?? '';
    expect(contents.split('\n').filter(Boolean)).toHaveLength(1);
    expect(contents).toContain('oversized-record');
    expect(files.has(auditRotatedPath(auditPath, 1))).toBe(false);
  });

  it('serializes concurrent writes and preserves correlation order', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-concurrent-'));
    const auditPath = join(dir, 'tally-request-audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 10_000, maxFiles: 3 });
    await Promise.all(
      Array.from({ length: 12 }, (_, index) =>
        auditor.record(sampleAuditRecord({ correlationId: `corr-${index}` })),
      ),
    );
    const lines = (await readFile(auditPath, 'utf8')).trim().split('\n').filter(Boolean);
    expect(lines).toHaveLength(12);
    const ids = lines.map((line) => (JSON.parse(line) as { correlationId: string }).correlationId);
    expect(ids).toEqual(Array.from({ length: 12 }, (_, index) => `corr-${index}`));
  });

  it('recreates a missing current file on the next write', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-missing-'));
    const auditPath = join(dir, 'tally-request-audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 256, maxFiles: 2 });
    await auditor.record(sampleAuditRecord({ correlationId: 'before-delete' }));
    await writeFile(auditPath, '', 'utf8');
    await auditor.record(sampleAuditRecord({ correlationId: 'after-delete' }));
    const lines = (await readFile(auditPath, 'utf8')).trim().split('\n').filter(Boolean);
    expect(lines).toHaveLength(1);
    expect(JSON.parse(lines[0]!).correlationId).toBe('after-delete');
  });

  it('falls back to current-file append when rename fails', async () => {
    const auditPath = '/tmp/budcom-audit-memory/fallback.jsonl';
    const { fs, files, failures } = createMemoryAuditFs({
      [auditPath]: `${'e'.repeat(70)}\n`,
    });
    failures.rename = 1;
    const { auditor, entries } = createCapturingTallyRequestAuditor(auditPath, {
      maxBytes: 80,
      maxFiles: 2,
      fs,
    });
    await auditor.record(sampleAuditRecord({ correlationId: 'fallback-append' }));
    expect(files.get(auditPath)).toContain('fallback-append');
    expect(entries.some((entry) => entry.context?.reasonCode === 'audit_rotation_rename_failed')).toBe(
      true,
    );
    expect(JSON.stringify(entries)).not.toMatch(/\/tmp|budcom-audit-memory/);
  });

  it('continues after append failure without breaking the write chain', async () => {
    const auditPath = '/tmp/budcom-audit-memory/append-failure.jsonl';
    const { fs, files, failures } = createMemoryAuditFs();
    failures.append = 1;
    const { auditor, entries } = createCapturingTallyRequestAuditor(auditPath, {
      maxBytes: 256,
      maxFiles: 2,
      fs,
    });
    await auditor.record(sampleAuditRecord({ correlationId: 'lost' }));
    failures.append = 0;
    await auditor.record(sampleAuditRecord({ correlationId: 'recovered' }));
    expect(files.get(auditPath)).toContain('recovered');
    expect(entries.some((entry) => entry.context?.reasonCode === 'audit_append_failed')).toBe(true);
  });

  it('keeps rotated files metadata-only', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-privacy-rotate-'));
    const auditPath = join(dir, 'tally-request-audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 80, maxFiles: 2 });
    const sensitiveXml = [
      '<ENVELOPE><SVCURRENTCOMPANY>JAJU SANITATIONS PRIVATE LIMITED</SVCURRENTCOMPANY>',
      '<LEDGER NAME="Sensitive Debtor Ledger"/>',
      '</ENVELOPE>',
    ].join('');
    await writeFile(auditPath, `${'f'.repeat(70)}\n`, 'utf8');
    await auditor.record(
      sampleAuditRecord({
        correlationId: 'privacy-rotate',
        xml: sensitiveXml,
      }),
    );
    const rotated = await readFile(auditRotatedPath(auditPath, 1), 'utf8');
    expect(rotated.toLowerCase()).not.toContain('jaju');
    expect(rotated).not.toContain('<ENVELOPE>');
    expect(rotated).not.toContain('redactedXml');
  });

  it('continues after stat failure and allows a later successful rotation', async () => {
    const auditPath = '/tmp/budcom-audit-memory/stat-recover.jsonl';
    const { fs, files, failures } = createMemoryAuditFs({
      [auditPath]: `${'g'.repeat(70)}\n`,
    });
    failures.stat = 1;
    const auditor = createTestTallyRequestAuditor(auditPath, {
      maxBytes: 80,
      maxFiles: 2,
      fs,
    });
    await auditor.record(sampleAuditRecord({ correlationId: 'after-stat-failure' }));
    expect(files.get(auditPath)).toContain('after-stat-failure');

    files.set(auditPath, `${'h'.repeat(70)}\n`);
    await auditor.record(sampleAuditRecord({ correlationId: 'rotate-after-stat' }));
    expect(files.has(auditRotatedPath(auditPath, 1))).toBe(true);
    expect(files.get(auditPath)).toContain('rotate-after-stat');
  });

  it('continues after delete-oldest failure without losing newer archives', async () => {
    const auditPath = '/tmp/budcom-audit-memory/delete-failure.jsonl';
    const { fs, files, failures } = createMemoryAuditFs({
      [auditRotatedPath(auditPath, 2)]: 'oldest\n',
      [auditRotatedPath(auditPath, 1)]: 'middle\n',
      [auditPath]: `${'i'.repeat(70)}\n`,
    });
    failures.unlink = 1;
    const { auditor, entries } = createCapturingTallyRequestAuditor(auditPath, {
      maxBytes: 80,
      maxFiles: 2,
      fs,
    });
    await auditor.record(sampleAuditRecord({ correlationId: 'after-delete-failure' }));
    expect(files.get(auditPath)).toContain('after-delete-failure');
    expect(entries.some((entry) => entry.context?.reasonCode === 'audit_rotation_delete_failed')).toBe(
      true,
    );
    expect(files.get(auditRotatedPath(auditPath, 1))).toBe('middle\n');
  });

  it('recreates a missing audit directory before append', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-dir-recreate-'));
    const nestedDir = join(dir, 'nested', 'diagnostics');
    const auditPath = join(nestedDir, 'tally-request-audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 256, maxFiles: 2 });
    await auditor.record(sampleAuditRecord({ correlationId: 'dir-created' }));
    expect(await fileExists(auditPath)).toBe(true);
    expect(JSON.parse((await readFile(auditPath, 'utf8')).trim()).correlationId).toBe('dir-created');
  });

  it('preserves complete lines when concurrent writes cross the rotation threshold', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-concurrent-rotate-'));
    const auditPath = join(dir, 'tally-request-audit.jsonl');
    await writeFile(auditPath, `${'j'.repeat(170)}\n`, 'utf8');
    const auditor = createTestTallyRequestAuditor(auditPath, { maxBytes: 200, maxFiles: 10 });
    await Promise.all(
      Array.from({ length: 6 }, (_, index) =>
        auditor.record(sampleAuditRecord({ correlationId: `rotate-corr-${index}` })),
      ),
    );
    const allLines: string[] = [];
    const paths = [auditPath];
    for (let slot = 1; slot <= 10; slot += 1) {
      paths.push(auditRotatedPath(auditPath, slot));
    }
    for (const path of paths) {
      if (await fileExists(path)) {
        allLines.push(...(await readFile(path, 'utf8')).trim().split('\n').filter(Boolean));
      }
    }
    const auditLines = allLines.filter((line) => {
      try {
        return typeof (JSON.parse(line) as { correlationId?: string }).correlationId === 'string';
      } catch {
        return false;
      }
    });
    expect(auditLines).toHaveLength(6);
    for (const line of auditLines) {
      expect(() => JSON.parse(line)).not.toThrow();
    }
    const ids = auditLines.map((line) => (JSON.parse(line) as { correlationId: string }).correlationId);
    expect(new Set(ids).size).toBe(6);
  });

  it('rotates successfully on a later write after an earlier rename failure', async () => {
    const auditPath = '/tmp/budcom-audit-memory/later-rotate.jsonl';
    const { fs, files, failures } = createMemoryAuditFs({
      [auditPath]: `${'k'.repeat(70)}\n`,
    });
    failures.rename = 1;
    const auditor = createTestTallyRequestAuditor(auditPath, {
      maxBytes: 80,
      maxFiles: 2,
      fs,
    });
    await auditor.record(sampleAuditRecord({ correlationId: 'fallback-first' }));
    expect(files.has(auditRotatedPath(auditPath, 1))).toBe(false);

    files.set(auditPath, `${'l'.repeat(70)}\n`);
    await auditor.record(sampleAuditRecord({ correlationId: 'rotate-second' }));
    expect(files.has(auditRotatedPath(auditPath, 1))).toBe(true);
    expect(files.get(auditPath)).toContain('rotate-second');
  });
});
