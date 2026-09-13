import type { Request } from 'express';
import { describe, expect, it } from 'vitest';

import { parseBearerCredential } from '../../../src/api/middleware/pairing-credential-bearer.js';

function fakeRequest(headers: Record<string, string | string[] | undefined>): Request {
  const lowercased = new Map(Object.entries(headers).map(([key, value]) => [key.toLowerCase(), value]));
  return {
    header: (name: string) => {
      const value = lowercased.get(name.toLowerCase());
      return Array.isArray(value) ? value[0] : value;
    },
  } as unknown as Request;
}

describe('parseBearerCredential', () => {
  it('returns missing when the Authorization header is absent', () => {
    const result = parseBearerCredential(fakeRequest({}));
    expect(result).toEqual({ ok: false, reason: 'missing' });
  });

  it('returns missing when the Authorization header is blank', () => {
    const result = parseBearerCredential(fakeRequest({ authorization: '' }));
    expect(result).toEqual({ ok: false, reason: 'missing' });
  });

  it('parses a well-formed Bearer token', () => {
    const result = parseBearerCredential(fakeRequest({ authorization: 'Bearer abc123._~+/=-XYZ' }));
    expect(result).toEqual({ ok: true, token: 'abc123._~+/=-XYZ' });
  });

  it('is case-insensitive for the header NAME (Express req.header semantics)', () => {
    const result = parseBearerCredential(fakeRequest({ Authorization: 'Bearer token-value' }));
    expect(result).toEqual({ ok: true, token: 'token-value' });
  });

  it('rejects a lowercase "bearer" scheme (case-sensitive scheme match)', () => {
    const result = parseBearerCredential(fakeRequest({ authorization: 'bearer token-value' }));
    expect(result).toEqual({ ok: false, reason: 'malformed' });
  });

  it('rejects a wrong scheme entirely', () => {
    const result = parseBearerCredential(fakeRequest({ authorization: 'Basic dXNlcjpwYXNz' }));
    expect(result).toEqual({ ok: false, reason: 'malformed' });
  });

  it('rejects a blank token after the Bearer prefix', () => {
    const result = parseBearerCredential(fakeRequest({ authorization: 'Bearer ' }));
    expect(result).toEqual({ ok: false, reason: 'malformed' });
  });

  it('rejects multiple space-separated values', () => {
    const result = parseBearerCredential(fakeRequest({ authorization: 'Bearer token-one token-two' }));
    expect(result).toEqual({ ok: false, reason: 'malformed' });
  });

  it('rejects a header with no scheme prefix at all', () => {
    const result = parseBearerCredential(fakeRequest({ authorization: 'just-a-raw-token' }));
    expect(result).toEqual({ ok: false, reason: 'malformed' });
  });

  it('rejects a token containing disallowed characters', () => {
    const result = parseBearerCredential(fakeRequest({ authorization: 'Bearer token with spaces' }));
    expect(result).toEqual({ ok: false, reason: 'malformed' });
  });

  it('never reads the token from a query, body, or cookie — only the Authorization header', () => {
    const req = {
      header: () => undefined,
      query: { token: 'sneaky-query-token' },
      body: { token: 'sneaky-body-token' },
      cookies: { token: 'sneaky-cookie-token' },
    } as unknown as Request;
    const result = parseBearerCredential(req);
    expect(result).toEqual({ ok: false, reason: 'missing' });
  });
});
