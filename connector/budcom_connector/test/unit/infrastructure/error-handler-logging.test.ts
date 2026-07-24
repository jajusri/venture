import { describe, expect, it, vi } from 'vitest';

import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { createErrorMiddleware } from '../../../src/infrastructure/errors/error-handler.js';
import { createLogger, type StructuredLogEntry } from '../../../src/infrastructure/logging/logger.js';

function runMiddleware(error: unknown): StructuredLogEntry | null {
  const entries: StructuredLogEntry[] = [];
  const logger = createLogger({
    service: 'test',
    level: 'error',
    sink: (entry) => entries.push(entry),
  });
  const middleware = createErrorMiddleware(logger);
  const json = vi.fn();
  const res = { status: vi.fn(() => ({ json })) } as unknown as import('express').Response;
  middleware(error, {} as import('express').Request, res, vi.fn());
  return entries[0] ?? null;
}

describe('HTTP 5xx error logging privacy (group 3)', () => {
  it('3A sanitizes unsafe AppError details before logging', () => {
    const entry = runMiddleware(
      new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Tally unavailable', 503, {
        responseBody: '<LEDGER NAME="Secret Co"/>',
        companyName: 'JAJU SANITATIONS PRIVATE LIMITED',
        token: 'sk-live-secret',
        path: 'C:\\Users\\Secret\\budcom.db',
        nested: { xml: '<ENVELOPE/>' },
        timeoutMs: 120000,
        statusCode: 503,
      }),
    );

    expect(entry).not.toBeNull();
    const serialized = JSON.stringify(entry);
    expect(serialized.toLowerCase()).not.toContain('jaju');
    expect(serialized).not.toContain('<LEDGER');
    expect(serialized).not.toContain('sk-live-secret');
    expect(entry?.context?.code).toBe(ErrorCodes.SERVICE_UNAVAILABLE);
    expect(entry?.context?.timeoutMs).toBe(120000);
  });

  it('3B preserves AppError API response shape including details', () => {
    const details = { feature: 'ledger-sync', timeoutMs: 1000 };
    const error = new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Unavailable', 503, details);
    expect(error.toResponse()).toEqual({
      code: ErrorCodes.SERVICE_UNAVAILABLE,
      message: 'Unavailable',
      details,
    });
  });

  it('3C does not log stack traces for unhandled errors', () => {
    const entry = runMiddleware(new Error('secret failure with stack'));
    expect(entry?.context?.code).toBe(ErrorCodes.INTERNAL_ERROR);
    const serialized = JSON.stringify(entry);
    expect(serialized).not.toContain('stack');
    expect(serialized).not.toContain('secret failure');
  });
});
