import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import { StartupDiagnostics } from '../../src/application/release/startup-diagnostics.js';

describe('startup diagnostics', () => {
  it('writes bounded startup stages without throwing when the directory is missing initially', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-startup-diag-'));
    try {
      const diagnostics = new StartupDiagnostics({ logsDir: dir, maxFileBytes: 200 });
      diagnostics.record('process_start', { pid: 1, packaged: true });
      diagnostics.record('single_instance_lock', { acquired: true, shouldQuit: false });
      diagnostics.record('renderer_loaded', { rendererPath: 'dist/renderer/index.html' });
      expect(fs.existsSync(path.join(dir, 'startup-diagnostics.jsonl'))).toBe(true);
      expect(diagnostics.hasStartupReadySignal()).toBe(true);
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  it('redacts sensitive content found inside a detail value, not just by key name (TD hygiene: value-content redaction)', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-startup-diag-redact-'));
    try {
      const diagnostics = new StartupDiagnostics({ logsDir: dir });
      diagnostics.record('bootstrap_error', {
        message: 'Failed for company JAJU SANITATIONS — Authorization: Bearer sk-live-abc123 contact ops@example.com',
      });
      const fileContents = fs.readFileSync(path.join(dir, 'startup-diagnostics.jsonl'), 'utf8');
      expect(fileContents).not.toContain('sk-live-abc123');
      expect(fileContents).not.toContain('ops@example.com');
      expect(fileContents).toContain('JAJU SANITATIONS');
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  it('never throws when append fails', () => {
    const diagnostics = new StartupDiagnostics({
      logsDir: path.join(os.tmpdir(), 'budcom-startup-diag-missing-root', 'nested'),
      fsImpl: {
        appendFileSync: () => {
          throw new Error('disk full');
        },
        existsSync: () => false,
        mkdirSync: () => {
          throw new Error('cannot create');
        },
        readdirSync: () => [],
        readFileSync: () => '',
        renameSync: () => undefined,
        statSync: () => ({ size: 0 }),
        unlinkSync: () => undefined,
        writeFileSync: () => undefined,
      },
    });
    expect(() => diagnostics.record('process_start', { pid: 1 })).not.toThrow();
  });
});
