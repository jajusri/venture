import type { Database } from '../../../../packages/persistence/src/database.js';
import type { TrustAuthoritySnapshot, TrustAuthoritySnapshotReader } from '../../../relay/src/application/pilot-authority-verifier.js';
import { DEVICE_COLUMNS, MEMBERSHIP_COLUMNS, toDevice, toMembership, type AuthorityRow, type DeviceRow, type MembershipRow } from './postgres-authority-write-store.js';

/**
 * Real Postgres-backed `TrustAuthoritySnapshotReader` -- three read-only point lookups against the
 * exact same tables `AuthorityRepository` already reads, reusing this package's own row-hydration
 * helpers (`toMembership`/`toDevice`) rather than duplicating them. Relay and Trust already share
 * one physical Postgres database by design (see `migrations.ts` -- Relay's own tables live in the
 * same migration set); this class is what lets Relay's pilot verifier re-check live authority state
 * without a new HTTP endpoint (see `pilot-authority-verifier.ts`'s own doc comment for why that
 * would otherwise be a temptation to avoid).
 *
 * BOUNDARY: not executable against a real database in this sandboxed environment (no local
 * PostgreSQL) -- see this package's other Postgres classes for the same stated boundary.
 */
export class PostgresTrustAuthoritySnapshotReader implements TrustAuthoritySnapshotReader {
  constructor(private readonly database: Database) {}

  async read(businessId: string, deviceId: string, deviceKeyVersion: number): Promise<TrustAuthoritySnapshot> {
    // Wrapped in one REPEATABLE READ, READ ONLY transaction so all three point lookups below observe
    // a single consistent snapshot as of transaction start (Codex Postgres finding: snapshot read
    // atomicity). Without this, Postgres's default READ COMMITTED lets each statement see the latest
    // COMMITTED data independently -- a concurrent authority mutation (e.g. a revocation) landing
    // between statements could produce a torn read (business/membership/device rows reflecting
    // different points in time), which the Fetch/Ack authority check
    // (`credentialMatchesCurrentAuthority`) could then misjudge. READ ONLY additionally guarantees
    // this reader can never itself write.
    return this.database.transaction(async (tx) => {
      await tx.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY');
      const business = await tx.query<AuthorityRow>('SELECT business_id, status, authority_epoch, created_at FROM trust_business_authority WHERE business_id = $1', [businessId]);
      const device = await tx.query<DeviceRow>(`SELECT ${DEVICE_COLUMNS} FROM trust_registered_device WHERE business_id = $1 AND device_id = $2 AND device_key_version = $3`, [businessId, deviceId, deviceKeyVersion]);
      if (business.rowCount === 0 || device.rowCount === 0) return { businessStatus: null, membership: null, device: null };
      const deviceRow = device.rows[0]!;
      const membership = await tx.query<MembershipRow>(`SELECT ${MEMBERSHIP_COLUMNS} FROM trust_business_membership WHERE membership_id = $1`, [deviceRow.membership_id]);
      return {
        businessStatus: business.rows[0]!.status as TrustAuthoritySnapshot['businessStatus'],
        membership: membership.rowCount === 0 ? null : toMembership(membership.rows[0]!),
        device: toDevice(deviceRow),
      };
    });
  }
}
