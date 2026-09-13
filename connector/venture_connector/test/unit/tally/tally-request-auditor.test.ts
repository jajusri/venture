import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { createTestTallyRequestAuditor } from '../../helpers/tally-audit-test-helpers.js';

describe('TallyRequestAuditor', () => {
  it('writes metadata-only audit records with hashed request bytes', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'venture-audit-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath);

    await auditor.record({
      timestamp: '2026-07-22T00:00:00.000Z',
      correlationId: 'abc-123',
      collectionId: 'List of Groups',
      requestByteLength: 512,
      outcome: 'sent',
      xml: '<SVCURRENTCOMPANY>ESTIMATION</SVCURRENTCOMPANY>',
    });

    const contents = await readFile(auditPath, 'utf8');
    const entry = JSON.parse(contents.trim()) as {
      correlationId: string;
      requestHash: string;
      redactedXml?: string;
    };
    expect(entry.correlationId).toBe('abc-123');
    expect(entry.requestHash).toMatch(/^[a-f0-9]{64}$/);
    expect(entry.redactedXml).toBeUndefined();
    expect(contents).not.toContain('ESTIMATION');
  });

  it('does not write when disabled', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'venture-audit-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath, { enabled: false });

    await auditor.record({
      timestamp: '2026-07-22T00:00:00.000Z',
      correlationId: 'abc-123',
      requestByteLength: 128,
      outcome: 'blocked',
      xml: '<ENVELOPE></ENVELOPE>',
    });

    await expect(readFile(auditPath, 'utf8')).rejects.toThrow();
  });
});
