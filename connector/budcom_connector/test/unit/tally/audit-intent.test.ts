import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { TallyRequestAuditor } from '../../../src/tally/safety/tally-request-auditor.js';
import { TallyRequestGuard } from '../../../src/tally/safety/tally-request-guard.js';

describe('audit intent is written before transport (Phase 8)', () => {
  it('records an intent entry with a request hash before the request is sent', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'budcom-audit-'));
    const auditPath = join(dir, 'audit.jsonl');
    const auditor = new TallyRequestAuditor(
      auditPath,
      true,
      createLogger({ service: 'test', level: 'error' }),
    );
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
    // Redacted XML only — no raw company payload bytes in ordinary logs.
    expect(String(intent!.redactedXml)).not.toContain('SECRET');
  });
});
