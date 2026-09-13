import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import {
  TALLY_REQUEST_AUDIT_MAX_BYTES_DEFAULT,
  TALLY_REQUEST_AUDIT_MAX_BYTES_LIMIT,
  TALLY_REQUEST_AUDIT_MAX_BYTES_MIN,
  TALLY_REQUEST_AUDIT_MAX_FILES_DEFAULT,
  TALLY_REQUEST_AUDIT_MAX_FILES_LIMIT,
} from '../../../src/config/defaults.js';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { createTestTallyRequestAuditor } from '../../helpers/tally-audit-test-helpers.js';

describe('Tally request audit configuration (B2a)', () => {
  it('defaults to 10 MiB and 5 rotated files', () => {
    const config = loadConfig({ env: 'test' });
    expect(config.tallyRequestAuditMaxBytes).toBe(TALLY_REQUEST_AUDIT_MAX_BYTES_DEFAULT);
    expect(config.tallyRequestAuditMaxFiles).toBe(TALLY_REQUEST_AUDIT_MAX_FILES_DEFAULT);
    expect(config.tallyRequestAuditMaxBytes).toBe(10 * 1024 * 1024);
    expect(config.tallyRequestAuditMaxFiles).toBe(5);
  });

  it('accepts valid environment overrides', () => {
    const previousBytes = process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_BYTES;
    const previousFiles = process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_FILES;
    process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_BYTES = String(128 * 1024);
    process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_FILES = '2';
    try {
      const config = loadConfig({ env: 'test' });
      expect(config.tallyRequestAuditMaxBytes).toBe(128 * 1024);
      expect(config.tallyRequestAuditMaxFiles).toBe(2);
    } finally {
      if (previousBytes === undefined) {
        delete process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_BYTES;
      } else {
        process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_BYTES = previousBytes;
      }
      if (previousFiles === undefined) {
        delete process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_FILES;
      } else {
        process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_FILES = previousFiles;
      }
    }
  });

  it.each([
    ['tallyRequestAuditMaxBytes', 0],
    ['tallyRequestAuditMaxFiles', 0],
    ['tallyRequestAuditMaxBytes', -1],
    ['tallyRequestAuditMaxFiles', -1],
  ])('rejects zero and negative %s override', (field, value) => {
    expect(() => loadConfig({ env: 'test', [field]: value })).toThrow(/Invalid/);
  });

  it('rejects non-integer audit max bytes override', () => {
    expect(() => loadConfig({ env: 'test', tallyRequestAuditMaxBytes: 1.5 })).toThrow(
      /Invalid tally request audit max bytes/,
    );
  });

  it('rejects non-integer audit settings from environment variables', () => {
    const previousBytes = process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_BYTES;
    process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_BYTES = 'abc';
    try {
      expect(() => loadConfig({ env: 'test' })).toThrow(/Invalid tally request audit max bytes/);
    } finally {
      if (previousBytes === undefined) {
        delete process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_BYTES;
      } else {
        process.env.VENTURE_TALLY_REQUEST_AUDIT_MAX_BYTES = previousBytes;
      }
    }
  });

  it('rejects audit max bytes above limit', () => {
    expect(() =>
      loadConfig({ env: 'test', tallyRequestAuditMaxBytes: TALLY_REQUEST_AUDIT_MAX_BYTES_LIMIT + 1 }),
    ).toThrow(/Invalid tally request audit max bytes/);
  });

  it('rejects audit max files above limit', () => {
    expect(() =>
      loadConfig({ env: 'test', tallyRequestAuditMaxFiles: TALLY_REQUEST_AUDIT_MAX_FILES_LIMIT + 1 }),
    ).toThrow(/Invalid tally request audit max files/);
  });

  it('rejects audit max bytes below minimum through override', () => {
    expect(() =>
      loadConfig({ env: 'test', tallyRequestAuditMaxBytes: TALLY_REQUEST_AUDIT_MAX_BYTES_MIN - 1 }),
    ).toThrow(/Invalid tally request audit max bytes/);
  });

  it('does not create audit file when disabled', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'venture-audit-disabled-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { enabled: false });
    await auditor.record({
      timestamp: '2026-07-24T00:00:00.000Z',
      correlationId: 'corr-disabled',
      requestByteLength: 32,
      outcome: 'sent',
      xml: '<ENVELOPE></ENVELOPE>',
    });
    await expect(readFile(auditPath, 'utf8')).rejects.toThrow();
  });
});
