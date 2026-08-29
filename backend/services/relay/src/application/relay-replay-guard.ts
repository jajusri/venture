/**
 * Bounded replay defence for authenticated Relay requests (Mailbox Fetch / Acknowledgement).
 *
 * A device's signature over a request proves POSSESSION at signing time -- without this guard, a
 * captured authenticated request could be replayed indefinitely as reusable bearer authority,
 * defeating the whole point of requiring a fresh signature per request. `consume()` is the single
 * check that turns "this signature is valid" into "this signature is valid AND has not already been
 * used AND was made recently enough to trust the clock claim it carries."
 *
 * Distinct from (and layered on top of) Relay's existing BUSINESS idempotency (`AcceptRelaySubmission`'s
 * `findByIdempotency`/`sameSubmission`, `PostgresRelayRepository.recordAcknowledgement`'s own
 * duplicate-envelope check) -- that layer answers "did this same commercial action already happen,
 * and if so return the same result" (a legitimate retry must succeed idempotently); this layer
 * answers "has this exact signed request already been consumed" (a legitimate retry must sign a
 * FRESH request with a new requestId, since the whole point is that a captured signature must not be
 * indefinitely reusable). Both are preserved independently -- see
 * `controlled-pilot-integration.test.ts`'s duplicate-vs-replay tests.
 */
export interface RelayReplayGuard {
  /** Returns `true` if `(businessId, deviceId, requestId)` is newly reserved (not seen before, and
   * `requestTimestamp` is within the allowed clock window of `now`); `false` if it is a replay or
   * the timestamp is outside tolerance. Scoped to `(businessId, deviceId)` so one device's nonce
   * space can never collide with, or be exhausted by, another device's. */
  consume(businessId: string, deviceId: string, requestId: string, requestTimestamp: Date, now: Date): Promise<boolean>;
}

/** How far a request's own claimed timestamp may drift from Relay's clock in either direction
 * before it is rejected outright, and how long a consumed request id is remembered afterward (long
 * enough to reject any replay a legitimate retry-storm could plausibly produce, short enough to
 * bound memory/row growth). */
export const REPLAY_CLOCK_TOLERANCE_MS = 5 * 60 * 1000;

/** Process-lifetime in-memory implementation -- used by the automated integration test and as a
 * dependency-free fallback. Not durable across a process restart: a real deployment should use
 * `PostgresRelayReplayGuard` (backend/services/relay/src/persistence/postgres-relay-replay-guard.ts)
 * so replay protection survives a Relay restart. */
export class InMemoryRelayReplayGuard implements RelayReplayGuard {
  private readonly seen = new Map<string, number>();

  constructor(private readonly toleranceMs: number = REPLAY_CLOCK_TOLERANCE_MS) {}

  consume(businessId: string, deviceId: string, requestId: string, requestTimestamp: Date, now: Date): Promise<boolean> {
    if (Math.abs(now.getTime() - requestTimestamp.getTime()) > this.toleranceMs) return Promise.resolve(false);
    this.cleanup(now);
    const key = `${businessId}::${deviceId}::${requestId}`;
    if (this.seen.has(key)) return Promise.resolve(false);
    this.seen.set(key, requestTimestamp.getTime() + this.toleranceMs);
    return Promise.resolve(true);
  }

  private cleanup(now: Date): void {
    const nowMs = now.getTime();
    for (const [key, expiresAt] of this.seen) if (expiresAt < nowMs) this.seen.delete(key);
  }
}
