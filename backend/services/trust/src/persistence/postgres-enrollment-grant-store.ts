import type { Database, DatabaseSession } from '../../../../packages/persistence/src/database.js';
import { AuthorityScope, identifier, type AuthorityCapability, type BusinessAuthorityReference } from '../domain/authority.js';
import type { EnrollmentGrant, EnrollmentGrantId } from '../domain/enrollment.js';
import { DeviceEnrollmentRejected, type EnrollmentAuthorityLookup, type EnrollmentGrantConsumptionStore } from '../application/consume-enrollment-grant.js';
import type { DeviceRegistrationStore } from '../application/register-device.js';
import { MEMBERSHIP_COLUMNS, PostgresDeviceRegistrationStore, toMembership, type AuthorityRow, type MembershipRow } from './postgres-authority-write-store.js';

/**
 * Real Postgres-backed enrollment-grant store, against `trust_enrollment_grant` (migration v7 --
 * `packages/persistence/src/migrations.ts`). Same testing posture as `postgres-authority-write-store.ts`:
 * structurally verified against a real Postgres schema/SQL shape and behaviorally verified against a
 * stateful fake that models real transaction/row-lock semantics (`postgres-enrollment-grant-store.test.ts`),
 * AND behaviorally verified against a real, live PostgreSQL 18 database -- including the `SELECT ...
 * FOR UPDATE`-based concurrent double-consume guarantee under genuine two-connection concurrency, not
 * merely modeled sequential interleaving -- via `backend/test/live-postgres.integration.test.ts`
 * (opt-in, gated on `BUDCOM_TRUST_DATABASE_URL`).
 */
export interface GrantRow extends Record<string, unknown> {
  readonly grant_id: string; readonly business_id: string; readonly actor_id: string; readonly membership_id: string;
  readonly granted_device_scope: string[]; readonly grant_secret_hash: string; readonly issued_at: Date; readonly expires_at: Date;
  readonly consumed_at: Date | null; readonly consumed_by_device_id: string | null;
}

export function toGrant(row: GrantRow): EnrollmentGrant {
  return {
    grantId: identifier(row.grant_id, 'EnrollmentGrantId') as EnrollmentGrantId,
    businessId: identifier(row.business_id, 'BusinessId'), actorId: identifier(row.actor_id, 'ActorId'),
    membershipId: identifier(row.membership_id, 'MembershipId'), grantedDeviceScope: new AuthorityScope(row.granted_device_scope as AuthorityCapability[]),
    grantSecretHash: row.grant_secret_hash, issuedAt: row.issued_at, expiresAt: row.expires_at,
    ...(row.consumed_at ? { consumedAt: row.consumed_at } : {}),
    ...(row.consumed_by_device_id ? { consumedByDeviceId: identifier(row.consumed_by_device_id, 'DeviceId') } : {}),
  };
}

export class PostgresEnrollmentGrantStore implements EnrollmentGrantConsumptionStore {
  constructor(private readonly database: Database) {}

  async create(grant: EnrollmentGrant): Promise<EnrollmentGrant> {
    await this.database.query(
      `INSERT INTO trust_enrollment_grant(grant_id, business_id, actor_id, membership_id, granted_device_scope, grant_secret_hash, issued_at, expires_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
      [grant.grantId, grant.businessId, grant.actorId, grant.membershipId, [...grant.grantedDeviceScope.capabilities], grant.grantSecretHash, grant.issuedAt, grant.expiresAt],
    );
    return grant;
  }

  /**
   * ONE Postgres transaction for the grant's entire lifecycle-ending action: `SELECT ... FOR UPDATE`
   * takes a row lock held for the whole transaction, so a second concurrent consumption attempt for
   * the SAME grant blocks here until this one commits or rolls back (see the lock's own inline
   * comment for exactly what that guarantees and what remains provable only against live Postgres).
   * `work` throwing -- including every `DeviceEnrollmentRejected` -- propagates out of this whole
   * method via `PostgresDatabase.transaction()`'s own catch-ROLLBACK-rethrow, undoing the device
   * registration `work` may have already performed AND leaving `consumed_at` untouched: a rejected
   * attempt (wrong secret, inactive business/membership, conflicting device) never burns a grant a
   * legitimate retry could still use before it naturally expires.
   */
  async consumeAndRegister<T>(
    grantId: string, consumingDeviceId: string,
    work: (claimed: EnrollmentGrant, deviceStore: DeviceRegistrationStore, authority: EnrollmentAuthorityLookup) => Promise<T>,
  ): Promise<T> {
    return this.database.transaction(async (tx: DatabaseSession) => {
      const row = await tx.query<GrantRow>('SELECT * FROM trust_enrollment_grant WHERE grant_id = $1 FOR UPDATE', [grantId]);
      if (row.rowCount === 0) throw new DeviceEnrollmentRejected('grant_not_found');
      const grant = toGrant(row.rows[0]!);
      const deviceStore = new PostgresDeviceRegistrationStore(tx);
      const authority: EnrollmentAuthorityLookup = {
        findBusiness: async (businessId) => {
          const result = await tx.query<AuthorityRow>('SELECT business_id, status, authority_epoch, created_at FROM trust_business_authority WHERE business_id = $1', [businessId]);
          return result.rowCount === 0 ? null : ({ businessId: identifier(result.rows[0]!.business_id, 'BusinessId'), status: result.rows[0]!.status } as BusinessAuthorityReference);
        },
        findMembership: async (membershipId) => {
          const result = await tx.query<MembershipRow>(`SELECT ${MEMBERSHIP_COLUMNS} FROM trust_business_membership WHERE membership_id = $1`, [membershipId]);
          return result.rowCount === 0 ? null : toMembership(result.rows[0]!);
        },
      };
      const result = await work(grant, deviceStore, authority);
      const claimed = await tx.query(
        'UPDATE trust_enrollment_grant SET consumed_at = $1, consumed_by_device_id = $2 WHERE grant_id = $3 AND consumed_at IS NULL',
        [new Date(), consumingDeviceId, grantId],
      );
      // Defensive, not load-bearing: the `FOR UPDATE` lock above already makes a second concurrent
      // claim impossible by the time this UPDATE runs (see this method's own doc comment) -- this
      // mirrors the same "should-not-happen, throw anyway" style already used for the analogous
      // guard in `PostgresDeviceRegistrationStore.save()`.
      if (claimed.rowCount !== 1) throw new DeviceEnrollmentRejected('grant_already_consumed');
      return result;
    });
  }
}
