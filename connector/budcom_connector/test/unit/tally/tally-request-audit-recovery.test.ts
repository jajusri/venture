import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { beforeEach, describe, expect, it, vi } from 'vitest';

let failNextAppend = false;

vi.mock('node:fs/promises', async (importOriginal) => {
  const actual = await importOriginal<typeof import('node:fs/promises')>();
  return {
    ...actual,
    appendFile: async (...args: Parameters<typeof actual.appendFile>) => {
      if (failNextAppend) {
        failNextAppend = false;
        throw new Error('disk full');
      }
      return actual.appendFile(...args);
    },
  };
});

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyRequestAuditor } from '../../../src/tally/safety/tally-request-auditor.js';

describe('Tally request audit write recovery (1E)', () => {
  beforeEach(() => {
    failNextAppend = false;
  });

  it('recovers after a failed append without blocking future records', async () => {
    failNextAppend = true;
    const dir = await mkdtemp(join(tmpdir(), 'budcom-audit-recover-'));
    const auditPath = join(dir, 'audit-recover.jsonl');
    const auditor = new TallyRequestAuditor(
      auditPath,
      true,
      createLogger({ service: 'test', level: 'error' }),
    );

    await auditor.record({
      timestamp: '2026-07-24T00:00:03.000Z',
      correlationId: 'corr-failed',
      requestByteLength: 32,
      outcome: 'sent',
      xml: '<ENVELOPE></ENVELOPE>',
    });
    await auditor.record({
      timestamp: '2026-07-24T00:00:04.000Z',
      correlationId: 'corr-recovered',
      requestByteLength: 48,
      outcome: 'sent',
      xml: '<ENVELOPE><RECOVERED/></ENVELOPE>',
    });

    const lines = (await readFile(auditPath, 'utf8')).trim().split('\n').filter(Boolean);
    expect(lines).toHaveLength(1);
    expect(JSON.parse(lines[0]!).correlationId).toBe('corr-recovered');
  });
});
