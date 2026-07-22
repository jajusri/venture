import type { Request, Response } from 'express';
import { describe, expect, it, vi } from 'vitest';

import { ErrorCodes } from '../../src/infrastructure/errors/app-error.js';
import { AppError } from '../../src/infrastructure/errors/app-error.js';
import { createErrorMiddleware } from '../../src/infrastructure/errors/error-handler.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';

function createMockResponse() {
  const res = {
    statusCode: 200,
    body: undefined as unknown,
    status(code: number) {
      this.statusCode = code;
      return this;
    },
    json(payload: unknown) {
      this.body = payload;
      return this;
    },
  };
  return res as Response & { statusCode: number; body: unknown };
}

describe('createErrorMiddleware', () => {
  it('does not log expected NOT_IMPLEMENTED responses as errors', () => {
    const entries: unknown[] = [];
    const logger = createLogger({
      service: 'test',
      level: 'error',
      sink: (entry) => entries.push(entry),
    });
    const middleware = createErrorMiddleware(logger);
    const res = createMockResponse();

    middleware(
      new AppError(ErrorCodes.NOT_IMPLEMENTED, 'Not ready', 501),
      {} as Request,
      res,
      vi.fn(),
    );

    expect(entries).toHaveLength(0);
    expect(res.statusCode).toBe(501);
  });

  it('logs unexpected internal errors', () => {
    const entries: unknown[] = [];
    const logger = createLogger({
      service: 'test',
      level: 'error',
      sink: (entry) => entries.push(entry),
    });
    const middleware = createErrorMiddleware(logger);
    const res = createMockResponse();

    middleware(new Error('boom'), {} as Request, res, vi.fn());

    expect(entries).toHaveLength(1);
    expect(res.statusCode).toBe(500);
  });
});
