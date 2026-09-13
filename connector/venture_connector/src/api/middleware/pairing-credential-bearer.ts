import type { Request } from 'express';

/**
 * Strict RFC 6750 §2.1 "Authorization Request Header Field" bearer-credential parser for
 * Android-facing pairing-credential routes (self-status / self-revoke — see
 * pairing-credential-authenticator.ts). Deliberately narrower than the legacy
 * require-trusted-device-auth.ts parser: this is the ONLY accepted transport for the
 * credential — never a query parameter, JSON body field, cookie, or URL path segment, and
 * this module never reads any of those.
 *
 * The HTTP header NAME is case-insensitive per RFC 7230 (Express's `req.header()` already
 * normalizes this). The "Bearer" scheme token itself is matched case-sensitively here — a
 * deliberately strict choice, not leniently accepting "bearer"/"BEARER", since this Connector
 * only ever emits "Bearer" itself and there is no legacy client to stay compatible with for
 * this brand-new route family.
 */

const BEARER_HEADER_PATTERN = /^Bearer ([A-Za-z0-9._~+/=-]+)$/;

export type BearerParseResult =
  | { readonly ok: true; readonly token: string }
  | { readonly ok: false; readonly reason: 'missing' | 'malformed' };

/**
 * Parses `Authorization: Bearer <token>`. Returns `{ok:false, reason:'missing'}` when the
 * header is absent or empty, `{ok:false, reason:'malformed'}` for anything present but not
 * exactly one well-formed Bearer credential (wrong scheme, blank token, embedded whitespace,
 * multiple comma-separated values, disallowed characters, or extra trailing content) — no
 * trimming or normalization beyond this exact grammar is ever applied to the captured token.
 */
export function parseBearerCredential(req: Request): BearerParseResult {
  const header = req.header('authorization');
  if (!header) {
    return { ok: false, reason: 'missing' };
  }

  const match = BEARER_HEADER_PATTERN.exec(header);
  if (!match) {
    return { ok: false, reason: 'malformed' };
  }

  return { ok: true, token: match[1]! };
}
