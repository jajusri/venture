import type { Database } from '../../../../packages/persistence/src/database.js';
import { REPLAY_CLOCK_TOLERANCE_MS, type RelayReplayGuard } from '../application/relay-replay-guard.js';

/**
 * Real Postgres-backed `RelayReplayGuard` against `relay_authenticated_request_nonce` (migration
 * v6, `packages/persistence/src/migrations.ts`). Durable across a Relay process restart, unlike
 * `InMemoryRelayReplayGuard`.
 *
 * `consume()` first deletes this device's own already-expired rows (cheap, primary-key-prefix
 * scoped, bounds table growth without a separate background job), then attempts an atomic
 * `INSERT ... ON CONFLICT DO NOTHING`: zero rows inserted means this exact `(businessId, deviceId,
 * requestId)` was already consumed -- a replay.
 *
 * BOUNDARY: not executable against a real database in this sandboxed environment (no local
 * PostgreSQL) -- see this package's other Postgres classes for the same stated boundary; validated
 * here via SQL-shape tests only (`relay-replay-guard.test.ts`).
 */
export class PostgresRelayReplayGuard implements RelayReplayGuard {
  constructor(private readonly database: Database, private readonly toleranceMs: number = REPLAY_CLOCK_TOLERANCE_MS) {}

  async consume(businessId: string, deviceId: string, requestId: string, requestTimestamp: Date, now: Date): Promise<boolean> {
    if (Math.abs(now.getTime() - requestTimestamp.getTime()) > this.toleranceMs) return false;
    return this.database.transaction(async (tx) => {
      await tx.query('DELETE FROM relay_authenticated_request_nonce WHERE business_id = $1 AND device_id = $2 AND expires_at < $3', [businessId, deviceId, now]);
      const expiresAt = new Date(requestTimestamp.getTime() + this.toleranceMs);
      const result = await tx.query(
        'INSERT INTO relay_authenticated_request_nonce(business_id, device_id, request_id, expires_at) VALUES ($1,$2,$3,$4) ON CONFLICT (business_id, device_id, request_id) DO NOTHING',
        [businessId, deviceId, requestId, expiresAt],
      );
      return result.rowCount > 0;
    });
  }
}
