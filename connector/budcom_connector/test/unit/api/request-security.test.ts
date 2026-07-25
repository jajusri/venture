import { describe, expect, it, vi } from 'vitest';
import type { NextFunction, Request, Response } from 'express';

import {
  rejectMalformedContentLength,
  requireJsonContentTypeForMutation,
} from '../../../src/api/middleware/request-security.js';

function createMockResponse() {
  const response = {
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
  return response as Response & { statusCode: number; body: unknown };
}

function createMockRequest(partial: Partial<Request> & { body?: unknown }): Request {
  return {
    method: 'POST',
    headers: {},
    body: undefined,
    ...partial,
  } as Request;
}

describe('request security middleware', () => {
  it('rejects malformed Content-Length before downstream parsing', () => {
    const req = createMockRequest({ headers: { 'content-length': '-1' } });
    const res = createMockResponse();
    const next = vi.fn() as NextFunction;

    rejectMalformedContentLength(req, res, next);
    expect(next).not.toHaveBeenCalled();
    expect(res.statusCode).toBe(400);
    expect(res.body).toMatchObject({ code: 'INVALID_CONTENT_LENGTH' });
  });

  it('requires JSON content type when Transfer-Encoding indicates a body', () => {
    const req = createMockRequest({
      headers: {
        'transfer-encoding': 'chunked',
        'content-type': 'text/plain',
      },
    });
    const res = createMockResponse();
    const next = vi.fn() as NextFunction;

    requireJsonContentTypeForMutation(req, res, next);
    expect(next).not.toHaveBeenCalled();
    expect(res.statusCode).toBe(415);
    expect(res.body).toMatchObject({ code: 'UNSUPPORTED_MEDIA_TYPE' });
  });

  it('allows empty-body POST routes without content type', () => {
    const req = createMockRequest({ method: 'POST', headers: {} });
    const res = createMockResponse();
    const next = vi.fn() as NextFunction;

    requireJsonContentTypeForMutation(req, res, next);
    expect(next).toHaveBeenCalledOnce();
  });

  it('requires JSON content type when express has already parsed a non-empty body', () => {
    const req = createMockRequest({
      headers: { 'content-type': 'text/plain' },
      body: { companyId: 'demo' },
    });
    const res = createMockResponse();
    const next = vi.fn() as NextFunction;

    requireJsonContentTypeForMutation(req, res, next);
    expect(next).not.toHaveBeenCalled();
    expect(res.statusCode).toBe(415);
  });
});
