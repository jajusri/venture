import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { TallyRequestGuard } from '../../../src/tally/safety/tally-request-guard.js';
import type { AuditFileOperations } from '../../../src/tally/safety/audit-file-rotator.js';
import { createTestTallyRequestAuditor } from '../../helpers/tally-audit-test-helpers.js';

describe('audit intent is written before transport (Phase 8)', () => {
  it('records an intent entry with a request hash before the request is sent', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'venture-audit-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = createTestTallyRequestAuditor(auditPath);
    const guard = new TallyRequestGuard({
      config: loadConfig({ env: 'test', tallySafeMode: true, tallyMinRequestIntervalMs: 0 }),
      logger: createLogger({ service: 'test', level: 'error' }),
      auditor,
    });

    const xml = new TallyXmlRequestBuilder().buildConnectivityCheck();
    // prepare() runs before any transport; it must persist an intent record.
    await guard.prepare({ correlationId: 'c1', collectionId: 'License Info', xml });

    const lines = readFileSync(auditPath, 'utf8')
      .trim()
      .split('\n')
      .map((line) => JSON.parse(line) as Record<string, unknown>);
    const intent = lines.find((entry) => entry.outcome === 'intent');
    expect(intent).toBeDefined();
    expect(intent!.requestHash).toMatch(/^[a-f0-9]{64}$/);
    expect(intent!.operationId).toBe('HEALTH_CHECK');
    expect(intent!.policyDecision).toBe('ALLOW');
    expect(intent!.redactedXml).toBeUndefined();
    expect(readFileSync(auditPath, 'utf8')).not.toContain('SECRET');
  });

  it('allows an approved request when audit rotation fails', async () => {
    const auditPathMemory = '/tmp/venture-audit-memory/guard-rotate.jsonl';
    const prefill = `${'m'.repeat(170)}\n`;
    const files = new Map<string, string>([[auditPathMemory, prefill]]);
    let renameFailures = 1;
    const fs: AuditFileOperations = {
      async stat(filePath) {
        if (!files.has(filePath)) return 'missing';
        return { size: Buffer.byteLength(files.get(filePath)!, 'utf8') };
      },
      async mkdir() {},
      async append(filePath, content) {
        files.set(filePath, `${files.get(filePath) ?? ''}${content}`);
      },
      async unlink(filePath) {
        files.delete(filePath);
      },
      async rename(from, to) {
        if (renameFailures > 0) {
          renameFailures -= 1;
          throw new Error('rename blocked');
        }
        if (!files.has(from)) {
          throw Object.assign(new Error('missing'), { code: 'ENOENT' });
        }
        files.set(to, files.get(from)!);
        files.delete(from);
      },
    };

    const auditor = createTestTallyRequestAuditor(auditPathMemory, {
      maxBytes: 200,
      maxFiles: 2,
      fs,
    });
    const guard = new TallyRequestGuard({
      config: loadConfig({ env: 'test', tallySafeMode: true, tallyMinRequestIntervalMs: 0 }),
      logger: createLogger({ service: 'test', level: 'error' }),
      auditor,
    });

    const xml = new TallyXmlRequestBuilder().buildConnectivityCheck();
    await expect(
      guard.prepare({ correlationId: 'guard-rotate-fail', collectionId: 'License Info', xml }),
    ).resolves.toBeUndefined();
    expect(files.get(auditPathMemory)).toContain('guard-rotate-fail');
  });
});
