import type { Database, DatabaseSession } from '../../../../packages/persistence/src/database.js';
import { AuthorityScope, identifier, type AuthorityCapability, type BusinessMembership, type RegisteredBusinessDevice } from '../domain/authority.js';
import type { BusinessBootstrapStore, BusinessCreationAuditEvent, CreatedBusinessAuthority } from '../application/create-business.js';
import type { MembershipApprovalStore } from '../application/approve-membership.js';
import type { DeviceRegistrationStore } from '../application/register-device.js';
import type { AuthorityMutationStore } from '../application/revoke-authority.js';
import { deriveIntentScopedId } from './file-backed-authority-store.js';

/**
 * Real Postgres-backed implementations of Trust's write-path store interfaces, against the
 * EXISTING schema only (`trust_business_authority`, `trust_business_membership`,
 * `trust_registered_device` -- migration v2, `backend/packages/persistence/src/migrations.ts`).
 * No new table or column was added for this controlled-pilot work: idempotent intent lookups reuse
 * the exact same deterministic-id technique as `FileBackedAuthorityStore` (see that file's own doc
 * comment) so `findByCreationIntent`/`findApproved` are plain indexed point lookups by primary key,
 * not a new "intent" column.
 *
 * BOUNDARY: this sandboxed dev environment has no reachable local PostgreSQL (no service, no
 * Docker) -- these classes are structurally verified against a real Postgres schema/SQL shape (see
 * `backend/test/postgres-authority-write-store.test.ts`, mirroring the existing
 * `authority-persistence.test.ts`/`relay-persistence.test.ts` convention of recording parameterized
 * calls rather than executing them), not behaviorally verified end-to-end against a live database.
 * The automated integration proof in this controlled-pilot package therefore runs against
 * `FileBackedAuthorityStore` instead (see that file and the final report's stated boundary) --
 * identical application-service/crypto logic, different persistence backend only.
 */

export interface AuthorityRow extends Record<string, unknown> { readonly business_id: string; readonly status: string; readonly authority_epoch: string; readonly created_at: Date }
export interface MembershipRow extends Record<string, unknown> {
  readonly membership_id: string; readonly business_id: string; readonly actor_id: string; readonly status: string;
  readonly authority_scope: string[]; readonly authority_epoch: string; readonly created_at: Date; readonly modified_at: Date;
}
export interface DeviceRow extends Record<string, unknown> {
  readonly business_id: string; readonly actor_id: string; readonly membership_id: string; readonly device_id: string;
  readonly device_key_id: string; readonly device_key_version: number; readonly public_key: Buffer; readonly public_key_fingerprint: string;
  readonly status: string; readonly authority_epoch: string; readonly created_at: Date; readonly revoked_at: Date | null;
}

/** Row column list shared with any reader that needs a full membership row (e.g. Relay's own
 * pilot authority-freshness check) -- keeps that query's column order in one place. */
export const MEMBERSHIP_COLUMNS = 'membership_id, business_id, actor_id, status, authority_scope, authority_epoch, created_at, modified_at';
export const DEVICE_COLUMNS = 'business_id, actor_id, membership_id, device_id, device_key_id, device_key_version, public_key, public_key_fingerprint, status, authority_epoch, created_at, revoked_at';

export function toMembership(row: MembershipRow): BusinessMembership {
  return {
    membershipId: identifier(row.membership_id, 'MembershipId'), businessId: identifier(row.business_id, 'BusinessId'),
    actorId: identifier(row.actor_id, 'ActorId'), status: row.status as BusinessMembership['status'],
    authorityScope: new AuthorityScope(row.authority_scope as AuthorityCapability[]), authorityEpoch: { value: Number(row.authority_epoch) },
    createdAt: row.created_at, modifiedAt: row.modified_at,
  };
}
export function toDevice(row: DeviceRow): RegisteredBusinessDevice {
  return {
    businessId: identifier(row.business_id, 'BusinessId'), actorId: identifier(row.actor_id, 'ActorId'), membershipId: identifier(row.membership_id, 'MembershipId'),
    deviceId: identifier(row.device_id, 'DeviceId'), deviceKeyId: identifier(row.device_key_id, 'DeviceKeyId'), deviceKeyVersion: row.device_key_version,
    publicKey: new Uint8Array(row.public_key), publicKeyFingerprint: row.public_key_fingerprint, status: row.status as RegisteredBusinessDevice['status'],
    authorityEpoch: { value: Number(row.authority_epoch) }, createdAt: row.created_at,
    ...(row.revoked_at ? { revokedAt: row.revoked_at } : {}),
  };
}

export class PostgresBusinessBootstrapStore implements BusinessBootstrapStore {
  constructor(private readonly database: Database) {}

  async findByCreationIntent(actorId: string, intentId: string): Promise<CreatedBusinessAuthority | null> {
    const businessId = deriveIntentScopedId([actorId, intentId], 'business');
    const membershipId = deriveIntentScopedId([actorId, intentId], 'membership');
    const business = await this.database.query<AuthorityRow>('SELECT business_id, status, authority_epoch, created_at FROM trust_business_authority WHERE business_id = $1', [businessId]);
    const membership = await this.database.query<MembershipRow>('SELECT membership_id, business_id, actor_id, status, authority_scope, authority_epoch, created_at, modified_at FROM trust_business_membership WHERE membership_id = $1', [membershipId]);
    if (business.rowCount === 0 || membership.rowCount === 0) return null;
    const businessRow = business.rows[0]!;
    const membershipRow = membership.rows[0]!;
    // Defensive confirmation: the deterministic-id derivation ties (actorId, intentId) to exactly
    // one (businessId, membershipId) pair, but this still confirms the ROW CONTENT actually matches
    // the caller's identity rather than trusting ID-based lookup alone -- a cheap guard against ever
    // silently returning a mismatched business/membership record for this intent.
    if (membershipRow.business_id !== businessId || membershipRow.actor_id !== actorId) {
      throw new Error('Creation-intent lookup returned a membership that does not match the expected business/actor -- refusing to return a possibly-inconsistent authority record');
    }
    const auditEvent: BusinessCreationAuditEvent = {
      eventId: deriveIntentScopedId([actorId, intentId], 'audit'), kind: 'business_authority_created',
      businessId: identifier(businessId, 'BusinessId'), actorId, occurredAt: businessRow.created_at,
    };
    return { businessId: identifier(businessId, 'BusinessId'), membership: toMembership(membershipRow), authorityEpoch: Number(businessRow.authority_epoch), auditEvent };
  }

  async createAtomically(intentId: string, result: CreatedBusinessAuthority): Promise<CreatedBusinessAuthority> {
    const inserted = await this.database.transaction(async (tx: DatabaseSession) => {
      const businessInsert = await tx.query(
        'INSERT INTO trust_business_authority(business_id, status, authority_epoch, created_at, modified_at) VALUES ($1,$2,$3,$4,$4) ON CONFLICT (business_id) DO NOTHING',
        [result.businessId, 'active', result.authorityEpoch, result.membership.createdAt],
      );
      await tx.query(
        'INSERT INTO trust_business_membership(membership_id, business_id, actor_id, status, authority_scope, authority_epoch, created_at, modified_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT (membership_id) DO NOTHING',
        [result.membership.membershipId, result.businessId, result.membership.actorId, result.membership.status,
          [...result.membership.authorityScope.capabilities], result.membership.authorityEpoch.value, result.membership.createdAt, result.membership.modifiedAt],
      );
      return businessInsert.rowCount > 0;
    });
    if (inserted) return result;
    // A concurrent caller with the identical (actorId, intentId) already committed this exact
    // deterministic business/membership pair between this call's own findByCreationIntent check and
    // this insert (Codex Postgres finding: a raced duplicate create must not surface a raw
    // unique-violation to the caller) -- re-read what actually landed instead of trusting the
    // in-flight `result` this call was about to insert.
    const existing = await this.findByCreationIntent(result.membership.actorId, intentId);
    if (!existing) throw new Error('Concurrent business creation left an inconsistent row after conflict');
    return existing;
  }
}

export class PostgresMembershipApprovalStore implements MembershipApprovalStore {
  constructor(private readonly database: Database) {}

  async findApproved(intentId: string): Promise<BusinessMembership | null> {
    const membershipId = deriveIntentScopedId([intentId], 'membership');
    const result = await this.database.query<MembershipRow>('SELECT membership_id, business_id, actor_id, status, authority_scope, authority_epoch, created_at, modified_at FROM trust_business_membership WHERE membership_id = $1', [membershipId]);
    return result.rowCount === 0 ? null : toMembership(result.rows[0]!);
  }

  async saveApproved(_intentId: string, membership: BusinessMembership, _approvedByMembershipId: string): Promise<BusinessMembership> {
    await this.database.query(
      'INSERT INTO trust_business_membership(membership_id, business_id, actor_id, status, authority_scope, authority_epoch, created_at, modified_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)',
      [membership.membershipId, membership.businessId, membership.actorId, membership.status,
        [...membership.authorityScope.capabilities], membership.authorityEpoch.value, membership.createdAt, membership.modifiedAt],
    );
    return membership;
  }
}

export class PostgresDeviceRegistrationStore implements DeviceRegistrationStore {
  constructor(private readonly database: Database) {}

  async find(businessId: string, deviceId: string, keyVersion: number): Promise<RegisteredBusinessDevice | null> {
    const result = await this.database.query<DeviceRow>(
      'SELECT business_id, actor_id, membership_id, device_id, device_key_id, device_key_version, public_key, public_key_fingerprint, status, authority_epoch, created_at, revoked_at FROM trust_registered_device WHERE business_id = $1 AND device_id = $2 AND device_key_version = $3',
      [businessId, deviceId, keyVersion],
    );
    return result.rowCount === 0 ? null : toDevice(result.rows[0]!);
  }

  async save(device: RegisteredBusinessDevice): Promise<RegisteredBusinessDevice> {
    const inserted = await this.database.query(
      `INSERT INTO trust_registered_device(business_id, actor_id, membership_id, device_id, device_key_id, device_key_version, public_key, public_key_fingerprint, status, authority_epoch, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
       ON CONFLICT (business_id, device_id, device_key_version) DO NOTHING`,
      [device.businessId, device.actorId, device.membershipId, device.deviceId, device.deviceKeyId, device.deviceKeyVersion,
        Buffer.from(device.publicKey), device.publicKeyFingerprint, device.status, device.authorityEpoch.value, device.createdAt],
    );
    if (inserted.rowCount > 0) return device;
    // A concurrent duplicate registration for the identical (business_id, device_id,
    // device_key_version) primary key already committed between `RegisterBusinessDevice.execute()`'s
    // own find()-then-save() check and this insert (Codex Postgres finding). Re-read whichever row
    // actually won instead of throwing a raw unique-violation -- a genuinely-identical concurrent
    // retry stays idempotent at the store layer.
    const existing = await this.find(device.businessId, device.deviceId, device.deviceKeyVersion);
    if (!existing) throw new Error('Concurrent device registration left an inconsistent row after conflict');
    return existing;
  }
}

export class PostgresAuthorityMutationStore implements AuthorityMutationStore {
  constructor(private readonly database: Database) {}

  async updateMembership(expectedEpoch: number, membership: BusinessMembership): Promise<boolean> {
    const result = await this.database.query(
      'UPDATE trust_business_membership SET status = $1, authority_scope = $2, authority_epoch = $3, modified_at = $4 WHERE membership_id = $5 AND authority_epoch = $6',
      [membership.status, [...membership.authorityScope.capabilities], membership.authorityEpoch.value, membership.modifiedAt, membership.membershipId, expectedEpoch],
    );
    return result.rowCount > 0;
  }

  async updateDevice(expectedEpoch: number, device: RegisteredBusinessDevice): Promise<boolean> {
    const result = await this.database.query(
      'UPDATE trust_registered_device SET status = $1, authority_epoch = $2, revoked_at = $3 WHERE business_id = $4 AND device_id = $5 AND device_key_version = $6 AND authority_epoch = $7',
      [device.status, device.authorityEpoch.value, device.revokedAt ?? null, device.businessId, device.deviceId, device.deviceKeyVersion, expectedEpoch],
    );
    return result.rowCount > 0;
  }
}
