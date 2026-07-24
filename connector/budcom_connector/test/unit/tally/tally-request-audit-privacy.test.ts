import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { TallyRequestAuditor } from '../../../src/tally/safety/tally-request-auditor.js';

const SAMPLE_REQUEST_XML = [
  '<ENVELOPE>',
  '<HEADER><TALLYREQUEST>Export Data</TALLYREQUEST></HEADER>',
  '<BODY><DESC><STATICVARIABLES>',
  '<SVCURRENTCOMPANY>JAJU SANITATIONS PRIVATE LIMITED</SVCURRENTCOMPANY>',
  '<GSTREGISTRATIONNUMBER>29AABCU9603R1ZM</GSTREGISTRATIONNUMBER>',
  '</STATICVARIABLES></DESC>',
  '<DATA><COLLECTION NAME="List of Ledgers">',
  '<LEDGER NAME="Sensitive Debtor Ledger"><GUID>a1b2c3d4-e5f6-7890-abcd-ef1234567890</GUID>',
  '<ADDRESS>42 Industrial Estate, Bangalore 560001</ADDRESS>',
  '</LEDGER></COLLECTION></DATA></BODY></ENVELOPE>',
].join('');

const FORBIDDEN = [
  'JAJU SANITATIONS',
  'Sensitive Debtor',
  '29AABCU9603R1ZM',
  'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
  'Industrial Estate',
  '<ENVELOPE>',
  '<LEDGER',
  'redactedXml',
  'errorMessage',
] as const;

function assertAuditPrivacySafe(serialized: string): void {
  const haystack = serialized.toLowerCase();
  for (const value of FORBIDDEN) {
    expect(haystack.includes(value.toLowerCase())).toBe(false);
  }
}

describe('Tally request audit privacy (1A-1D)', () => {
  it('1A persists metadata only without request XML or tags', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-privacy-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = new TallyRequestAuditor(
      auditPath,
      true,
      createLogger({ service: 'test', level: 'error' }),
    );

    await auditor.record({
      timestamp: '2026-07-24T00:00:00.000Z',
      correlationId: 'corr-1',
      operationId: 'ledger-export',
      capability: 'master-data-read',
      policyDecision: 'allow',
      collectionId: 'List of Ledgers',
      requestByteLength: Buffer.byteLength(SAMPLE_REQUEST_XML, 'utf8'),
      outcome: 'sent',
      xml: SAMPLE_REQUEST_XML,
    });

    const contents = await readFile(auditPath, 'utf8');
    const entry = JSON.parse(contents.trim()) as Record<string, unknown>;
    expect(entry.correlationId).toBe('corr-1');
    expect(entry.requestHash).toMatch(/^[a-f0-9]{64}$/);
    expect(entry).not.toHaveProperty('redactedXml');
    expect(entry).not.toHaveProperty('errorMessage');
    assertAuditPrivacySafe(contents);
  });

  it('1B normalizes failure reason codes without raw exception text', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-failure-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = new TallyRequestAuditor(
      auditPath,
      true,
      createLogger({ service: 'test', level: 'error' }),
    );

    await auditor.record({
      timestamp: '2026-07-24T00:00:01.000Z',
      correlationId: 'corr-timeout',
      requestByteLength: 128,
      outcome: 'failed',
      xml: SAMPLE_REQUEST_XML,
      error: new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Tally request timed out after 120000ms for JAJU SANITATIONS with <LEDGER/>',
        504,
      ),
    });

    const entry = JSON.parse((await readFile(auditPath, 'utf8')).trim()) as {
      errorReasonCode?: string;
    };
    expect(entry.errorReasonCode).toBe('transport_timeout');
    assertAuditPrivacySafe(JSON.stringify(entry));
  });

  it('1C remains enabled for metadata-only auditing', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-enabled-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = new TallyRequestAuditor(
      auditPath,
      true,
      createLogger({ service: 'test', level: 'error' }),
    );

    await auditor.record({
      timestamp: '2026-07-24T00:00:02.000Z',
      correlationId: 'corr-enabled',
      requestByteLength: 64,
      outcome: 'blocked',
      xml: '<ENVELOPE></ENVELOPE>',
      error: new AppError(ErrorCodes.READ_ONLY_VIOLATION, 'blocked', 403),
    });

    const contents = await readFile(auditPath, 'utf8');
    expect(contents.trim().length).toBeGreaterThan(0);
  });

  it('1D coalesces concurrent writes into complete JSONL lines', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-concurrent-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = new TallyRequestAuditor(
      auditPath,
      true,
      createLogger({ service: 'test', level: 'error' }),
    );

    await Promise.all(
      Array.from({ length: 20 }, (_, index) =>
        auditor.record({
          timestamp: `2026-07-24T00:00:${String(index).padStart(2, '0')}.000Z`,
          correlationId: `corr-${index}`,
          requestByteLength: 64 + index,
          outcome: 'sent',
          xml: `<ENVELOPE><ID>${index}</ID></ENVELOPE>`,
        }),
      ),
    );

    const lines = (await readFile(auditPath, 'utf8')).trim().split('\n');
    expect(lines).toHaveLength(20);
    for (const line of lines) {
      const parsed = JSON.parse(line) as { correlationId: string };
      expect(parsed.correlationId).toMatch(/^corr-/);
      assertAuditPrivacySafe(line);
    }
  });
});
