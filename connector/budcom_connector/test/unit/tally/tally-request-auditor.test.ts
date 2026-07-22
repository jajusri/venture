import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyRequestAuditor } from '../../../src/tally/safety/tally-request-auditor.js';

describe('TallyRequestAuditor', () => {
  it('writes redacted XML to audit file', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = new TallyRequestAuditor(
      auditPath,
      true,
      createLogger({ service: 'test', level: 'error' }),
    );

    await auditor.record({
      timestamp: '2026-07-22T00:00:00.000Z',
      correlationId: 'abc-123',
      collectionId: 'List of Groups',
      requestByteLength: 512,
      outcome: 'sent',
      xml: '<SVCURRENTCOMPANY>ESTIMATION</SVCURRENTCOMPANY>',
    });

    const contents = await readFile(auditPath, 'utf8');
    const entry = JSON.parse(contents.trim()) as { redactedXml: string; correlationId: string };
    expect(entry.correlationId).toBe('abc-123');
    expect(entry.redactedXml).toContain('[REDACTED]');
    expect(entry.redactedXml).not.toContain('ESTIMATION');
  });

  it('does not write when disabled', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = new TallyRequestAuditor(
      auditPath,
      false,
      createLogger({ service: 'test', level: 'error' }),
    );

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
