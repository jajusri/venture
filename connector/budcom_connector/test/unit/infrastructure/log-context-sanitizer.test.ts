import { describe, expect, it } from 'vitest';

import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { createLogger, type StructuredLogEntry } from '../../../src/infrastructure/logging/logger.js';
import { PERSISTENT_TEXT_LIMITS } from '../../../src/infrastructure/privacy/persistent-text-sanitizer.js';

const COMPANY = 'JAJU SANITATIONS PRIVATE LIMITED';
const LEDGER = 'Sensitive Debtor Ledger';
const GSTIN = '29AABCU9603R1ZM';
const GUID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
const TOKEN = 'sk-live-diagnostic-leak-test-token';
const PATH = 'C:\\Users\\ContosoUser\\Documents\\budcom.db';
const REQUEST_XML = '<ENVELOPE><SVCURRENTCOMPANY>Secret</SVCURRENTCOMPANY></ENVELOPE>';
const RESPONSE_XML = '<ENVELOPE><LEDGER NAME="Secret"/></ENVELOPE>';

function captureEntry(fn: (logger: ReturnType<typeof createLogger>) => void): StructuredLogEntry {
  let captured: StructuredLogEntry | null = null;
  const logger = createLogger({
    service: 'test',
    level: 'debug',
    sink: (entry) => {
      captured = entry;
    },
  });
  fn(logger);
  if (!captured) {
    throw new Error('No log entry captured');
  }
  return captured;
}

function assertForbiddenAbsent(serialized: string, forbidden: readonly string[]): void {
  const haystack = serialized.toLowerCase();
  for (const value of forbidden) {
    expect(haystack.includes(value.toLowerCase())).toBe(false);
  }
}

describe('connector logger sanitizer (group 2)', () => {
  it('redacts XML, identifiers, secrets, and paths from message and context', () => {
    const entry = captureEntry((logger) => {
      logger.info('sync failed', {
        component: 'ledger-sync',
        companyName: COMPANY,
        databasePath: PATH,
        nested: { ledger: LEDGER, xml: REQUEST_XML },
        token: TOKEN,
        guid: GUID,
        gstin: GSTIN,
        correlationId: 'corr-safe-1',
        durationMs: 42,
        itemCount: 100,
        byteLength: 9000,
        timeoutMs: 120000,
      });
    });

    const serialized = JSON.stringify(entry);
    assertForbiddenAbsent(serialized, [
      COMPANY,
      LEDGER,
      GSTIN,
      GUID,
      TOKEN,
      PATH,
      'ContosoUser',
      '<ENVELOPE>',
      '<LEDGER',
    ]);
    expect(entry.context?.correlationId).toBe('corr-safe-1');
    expect(entry.context?.durationMs).toBe(42);
    expect(entry.context?.itemCount).toBe(100);
    expect(entry.context?.byteLength).toBe(9000);
    expect(entry.context?.timeoutMs).toBe(120000);
  });

  it('redacts monetary-like text in messages without affecting safe numeric metadata', () => {
    const entry = captureEntry((logger) => {
      logger.info('Ledger sync failed amount 125000.50 Dr for party', {
        component: 'ledger-sync',
        itemCount: 42,
        durationMs: 1500,
        byteLength: 8192,
        correlationId: 'corr-ops-99',
      });
    });

    expect(entry.message).toContain('[REDACTED]');
    expect(entry.message).not.toContain('125000.50');
    expect(entry.context?.itemCount).toBe(42);
    expect(entry.context?.durationMs).toBe(1500);
    expect(entry.context?.byteLength).toBe(8192);
    expect(entry.context?.correlationId).toBe('corr-ops-99');
  });

  it('does not throw for Error, AppError, circular objects, or long injected text', () => {
    const circular: Record<string, unknown> = { component: 'test' };
    circular.self = circular;
    const longMessage = `${'A'.repeat(800)}\n"injected":true`;
    const entry = captureEntry((logger) => {
      logger.error(longMessage, {
        component: 'ledger-sync',
        error: new Error(`failed for ${COMPANY} ${REQUEST_XML}`),
        appError: new AppError(ErrorCodes.SERVICE_UNAVAILABLE, RESPONSE_XML, 503, {
          responseBody: RESPONSE_XML,
          companyName: COMPANY,
        }),
        circular,
      });
    });

    expect(entry.message.length).toBeLessThanOrEqual(PERSISTENT_TEXT_LIMITS.maxStringLength);
    expect(entry.message).not.toContain('\n');
    const serialized = JSON.stringify(entry);
    assertForbiddenAbsent(serialized, [COMPANY, RESPONSE_XML, 'responseBody', 'stack']);
  });

  it('preserves deterministic output for identical input', () => {
    const first = captureEntry((logger) => {
      logger.warn('hello', { component: 'sync', correlationId: 'abc', code: 'X' });
    });
    const second = captureEntry((logger) => {
      logger.warn('hello', { component: 'sync', correlationId: 'abc', code: 'X' });
    });
    expect(first.message).toBe(second.message);
    expect(first.context).toEqual(second.context);
  });
});
