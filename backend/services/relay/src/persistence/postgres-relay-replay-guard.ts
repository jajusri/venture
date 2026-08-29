import type { Database } from '../../../../packages/persistence/src/database.js';
import { REPLAY_CLOCK_TOLERANCE_MS, type RelayReplayGuard } from '../application/relay-replay-guard.js';

/** Bounded batch size for the opportunistic global cleanup below -- deliberately small and constant
 * (never derived from table size or a caller input), so the extra DELETE this adds to every
 * `consume()` call stays cheap and bounded even under a large expired-row backlog. */
const GLOBAL_CLEANUP_BATCH_SIZE = 100;

/**
 * Real Postgres-backed `RelayReplayGuard` against `relay_authenticated_request_nonce` (migration
 * v6, `packages/persistence/src/migrations.ts`). Durable across a Relay process restart, unlike
 * `InMemoryRelayReplayGuard`.
 *
 * `consume()` first deletes this device's own already-expired rows (cheap, primary-key-prefix
 * scoped), then -- per Codex's re-certification small finding below -- clears a small bounded batch
 * of GLOBALLY expired rows (any business, any device), then attempts an atomic `INSERT ... ON
 * CONFLICT DO NOTHING`: zero rows inserted means this exact `(businessId, deviceId, requestId)` was
 * already consumed -- a replay.
 *
 * REPLAY CLEANUP FINDING (fixed, relay-authority-repair round 2, 2026-08-29): the per-device delete
 * only ever reaches rows belonging to a device that is still actively submitting requests, so a
 * device that goes permanently inactive (decommissioned, uninstalled, revoked) would otherwise leave
 * its expired nonce rows in this table forever. The fix is the second DELETE below: bounded (LIMIT
 * `GLOBAL_CLEANUP_BATCH_SIZE`, never unbounded or table-wide), indexed (uses the existing
 * `relay_request_nonce_expiry_idx` from migration v6 -- no new migration needed), and runs
 * opportunistically inside the same transaction every `consume()` call rather than requiring a new
 * scheduler/worker subsystem. It cannot re-enable a replay: a row only ever becomes a cleanup
 * candidate once `expires_at < now`, and by that point `consume()`'s own clock-tolerance check above
 * already rejects any request whose timestamp is that old, regardless of whether this row still
 * physically exists -- the signed timestamp window is the actual security backstop; this DELETE is
 * pure space reclamation, not a security boundary.
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
      await tx.query(
        `DELETE FROM relay_authenticated_request_nonce WHERE ctid IN (
          SELECT ctid FROM relay_authenticated_request_nonce WHERE expires_at < $1 LIMIT $2
        )`,
        [now, GLOBAL_CLEANUP_BATCH_SIZE],
      );
      const expiresAt = new Date(requestTimestamp.getTime() + this.toleranceMs);
      const result = await tx.query(
        'INSERT INTO relay_authenticated_request_nonce(business_id, device_id, request_id, expires_at) VALUES ($1,$2,$3,$4) ON CONFLICT (business_id, device_id, request_id) DO NOTHING',
        [businessId, deviceId, requestId, expiresAt],
      );
      return result.rowCount > 0;
    });
  }
}
