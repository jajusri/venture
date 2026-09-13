import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { createTestVoucherSyncFailureAuditor } from '../../helpers/voucher-sync-failure-audit-test-helpers.js';

describe('VoucherSyncFailureAuditor', () => {
  it('writes structural-only failure records to its own file', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'venture-voucher-failure-audit-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = createTestVoucherSyncFailureAuditor(auditPath);

    await auditor.record({
      timestamp: '2026-08-16T00:00:00.000Z',
      runId: 'run-1',
      companyId: 'company-a',
      phase: 'extracting',
      failureReason: 'parser_failure',
      repositoryFailureCode: null,
      details: {
        reasonCode: 'voucher-ledger-validation',
        reconciliationReason: 'orphan-ledger-entry',
        operation: 'VoucherLedgerEntries',
        responseByteLength: 4096,
        responseHash: 'abc123',
        illegalCharactersSanitized: 0,
      },
    });

    const contents = await readFile(auditPath, 'utf8');
    const entry = JSON.parse(contents.trim()) as Record<string, unknown>;
    expect(entry).toMatchObject({
      runId: 'run-1',
      companyId: 'company-a',
      phase: 'extracting',
      failureReason: 'parser_failure',
      details: {
        reasonCode: 'voucher-ledger-validation',
        reconciliationReason: 'orphan-ledger-entry',
      },
    });
  });

  it('does not write when disabled', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'venture-voucher-failure-audit-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = createTestVoucherSyncFailureAuditor(auditPath, { enabled: false });

    await auditor.record({
      timestamp: '2026-08-16T00:00:00.000Z',
      runId: 'run-1',
      companyId: 'company-a',
      phase: null,
      failureReason: 'invalid_request',
      repositoryFailureCode: null,
      details: null,
    });

    await expect(readFile(auditPath, 'utf8')).rejects.toThrow();
  });
});
