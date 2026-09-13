import type { NextFunction, Request, RequestHandler, Response } from 'express';

const DEFAULT_MAX_ATTEMPTS = 20;
const DEFAULT_WINDOW_MS = 60_000;

export interface PairingRedeemRateLimitOptions {
  readonly maxAttempts?: number;
  readonly windowMs?: number;
  /** Injectable clock for deterministic tests. Defaults to Date.now. */
  readonly now?: () => number;
}

interface WindowState {
  count: number;
  windowStart: number;
}

/**
 * Narrowly-scoped, in-memory, per-source-address fixed-window limiter for pairing-session
 * redemption only — never applied to ordinary accounting-data routes. This is defense-in-depth
 * on top of PairingSessionRepository's own per-session failed_attempts lockout: that lockout is
 * scoped to one session, so a caller could otherwise spread attempts across many short-lived
 * sessions from the same source to route around it.
 *
 * Deliberately single-process, in-memory state (no external store): pairing sessions are
 * short-lived (30s-300s TTL) and this whole feature is default-off, so a limiter that resets on
 * process restart or doesn't coordinate across multiple Connector instances is an acceptable
 * MVP-1 trade-off. Revisit if the Connector is ever horizontally scaled.
 */
export function createPairingRedeemRateLimiter(options: PairingRedeemRateLimitOptions = {}): RequestHandler {
  const maxAttempts = options.maxAttempts ?? DEFAULT_MAX_ATTEMPTS;
  const windowMs = options.windowMs ?? DEFAULT_WINDOW_MS;
  const now = options.now ?? Date.now;
  const state = new Map<string, WindowState>();

  return (req: Request, res: Response, next: NextFunction) => {
    const key = req.ip ?? 'unknown';
    const current = now();
    const existing = state.get(key);

    if (!existing || current - existing.windowStart >= windowMs) {
      state.set(key, { count: 1, windowStart: current });
      next();
      return;
    }

    if (existing.count >= maxAttempts) {
      res.status(429).json({
        code: 'TOO_MANY_REQUESTS',
        message: 'Too many pairing redemption attempts from this source. Try again shortly.',
      });
      return;
    }

    existing.count += 1;
    next();
  };
}
