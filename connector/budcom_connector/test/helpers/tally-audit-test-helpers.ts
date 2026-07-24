import { createLogger, type StructuredLogEntry } from '../../src/infrastructure/logging/logger.js';
import {
  TallyRequestAuditor,
  type TallyRequestAuditorOptions,
  type TallyRequestOutcome,
} from '../../src/tally/safety/tally-request-auditor.js';

const DEFAULT_TEST_MAX_BYTES = 256;
const DEFAULT_TEST_MAX_FILES = 3;

export function createTestTallyRequestAuditor(
  auditPath: string,
  overrides: Partial<TallyRequestAuditorOptions> = {},
): TallyRequestAuditor {
  return new TallyRequestAuditor({
    auditPath,
    enabled: true,
    maxBytes: DEFAULT_TEST_MAX_BYTES,
    maxFiles: DEFAULT_TEST_MAX_FILES,
    logger: createLogger({ service: 'test', level: 'error' }),
    ...overrides,
  });
}

export function createCapturingTallyRequestAuditor(
  auditPath: string,
  overrides: Partial<TallyRequestAuditorOptions> = {},
): { auditor: TallyRequestAuditor; entries: StructuredLogEntry[] } {
  const entries: StructuredLogEntry[] = [];
  const auditor = createTestTallyRequestAuditor(auditPath, {
    logger: createLogger({
      service: 'test',
      level: 'debug',
      sink: (entry) => entries.push(entry),
    }),
    ...overrides,
  });
  return { auditor, entries };
}

export function sampleAuditRecord(overrides: {
  readonly timestamp?: string;
  readonly correlationId?: string;
  readonly requestByteLength?: number;
  readonly outcome?: TallyRequestOutcome;
  readonly xml?: string;
} = {}) {
  return {
    timestamp: overrides.timestamp ?? '2026-07-24T00:00:00.000Z',
    correlationId: overrides.correlationId ?? 'corr-1',
    requestByteLength: overrides.requestByteLength ?? 64,
    outcome: overrides.outcome ?? ('sent' as const),
    xml: overrides.xml ?? '<ENVELOPE></ENVELOPE>',
  };
}

export { DEFAULT_TEST_MAX_BYTES, DEFAULT_TEST_MAX_FILES };
