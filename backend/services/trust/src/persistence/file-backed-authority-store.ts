import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { AuthorityScope, identifier, type AuthorityCapability, type BusinessMembership, type RegisteredBusinessDevice } from '../domain/authority.js';
import type { BusinessBootstrapStore, BusinessCreationAuditEvent, CreatedBusinessAuthority } from '../application/create-business.js';
import type { MembershipApprovalStore } from '../application/approve-membership.js';
import type { DeviceRegistrationStore } from '../application/register-device.js';
import type { AuthorityMutationStore } from '../application/revoke-authority.js';

/**
 * Deterministic, dependency-free (no Postgres) persistence for Trust's write-path application
 * services -- CONTROLLED-PILOT / DEV TOOLING ONLY. Not a substitute for the real Postgres-backed
 * stores in `postgres-authority-write-store.ts`: this exists so the dev-provisioning CLI and
 * automated integration tests work in an environment with no local PostgreSQL (this sandbox has
 * neither a running Postgres nor Docker), and so a pilot operator without a running database can
 * still exercise the flow end-to-end before wiring up Postgres for real.
 *
 * Backed by a single in-memory object graph. If constructed with `persistPath`, that graph is
 * loaded from and saved back to a local JSON file after every write (used by the dev-provisioning
 * CLI, whose invocations are separate short-lived processes and need state to survive between
 * them -- e.g. `create-business` then, later, `register-device`). Constructed with no path, it is
 * purely in-memory for the lifetime of the process (used by the automated integration test, where
 * everything happens inside one process and nothing should touch disk).
 *
 * Idempotency for `findByCreationIntent`/`findApproved` is achieved by having the CALLER (see
 * `deriveIntentScopedIds` below) supply a `newId` factory to `CreateBusiness`/`ApproveMembership`
 * that deterministically derives every generated id from `(actorId, intentId)` -- so re-running the
 * same intent always looks up the same key, without needing an extra "intent" column that doesn't
 * exist in this store's on-disk shape.
 */

interface FileState {
  businesses: Record<string, { businessId: string; status: 'active' | 'suspended' | 'revoked'; createdAt: string }>;
  memberships: Record<string, StoredMembership>;
  devices: Record<string, StoredDevice>;
}

interface StoredMembership {
  membershipId: string; businessId: string; actorId: string; status: 'active' | 'suspended' | 'revoked';
  authorityScope: string[]; authorityEpochValue: number; createdAt: string; modifiedAt: string;
}

interface StoredDevice {
  businessId: string; actorId: string; membershipId: string; deviceId: string; deviceKeyId: string;
  deviceKeyVersion: number; publicKey: string; publicKeyFingerprint: string; status: 'active' | 'revoked';
  authorityEpochValue: number; createdAt: string; revokedAt?: string;
}

function emptyState(): FileState { return { businesses: {}, memberships: {}, devices: {} }; }

/** Deterministic id derivation shared between a caller (constructing `CreateBusiness`/`ApproveMembership`
 * with this as their `newId`) and this store's own idempotent lookups -- see this file's own doc
 * comment. `label` distinguishes the several ids generated within one `execute()` call. */
export function deriveIntentScopedId(parts: readonly string[], label: string): string {
  return createHash('sha256').update([...parts, label].join('|')).digest('hex').slice(0, 32);
}

/** Builds the `newId` factory `CreateBusiness`'s constructor expects: called once per id, in the
 * fixed order `CreateBusiness.execute()` calls it (businessId, membershipId, auditEvent.eventId). */
export function createBusinessDeterministicIds(actorId: string, intentId: string): () => string {
  const labels = ['business', 'membership', 'audit'];
  let index = 0;
  return () => {
    const label = labels[index] ?? `extra-${index}`;
    index += 1;
    return deriveIntentScopedId([actorId, intentId], label);
  };
}

/** Builds the `newId` factory `ApproveMembership`'s constructor expects (single membershipId). */
export function approveMembershipDeterministicId(intentId: string): () => string {
  return () => deriveIntentScopedId([intentId], 'membership');
}

export class FileBackedAuthorityStore implements BusinessBootstrapStore, MembershipApprovalStore, DeviceRegistrationStore, AuthorityMutationStore {
  private state: FileState;

  constructor(private readonly persistPath?: string) {
    this.state = persistPath && existsSync(persistPath) ? (JSON.parse(readFileSync(persistPath, 'utf8')) as FileState) : emptyState();
  }

  private persist(): void {
    if (!this.persistPath) return;
    mkdirSync(dirname(this.persistPath), { recursive: true });
    writeFileSync(this.persistPath, JSON.stringify(this.state, null, 2), { mode: 0o600 });
  }

  // ---- BusinessBootstrapStore ----
  findByCreationIntent(actorId: string, intentId: string): Promise<CreatedBusinessAuthority | null> {
    const businessId = deriveIntentScopedId([actorId, intentId], 'business');
    const business = this.state.businesses[businessId];
    const membership = this.state.memberships[deriveIntentScopedId([actorId, intentId], 'membership')];
    if (!business || !membership) return Promise.resolve(null);
    const auditEvent: BusinessCreationAuditEvent = {
      eventId: deriveIntentScopedId([actorId, intentId], 'audit'), kind: 'business_authority_created',
      businessId: identifier(businessId, 'BusinessId'), actorId, occurredAt: new Date(business.createdAt),
    };
    return Promise.resolve({ businessId: identifier(businessId, 'BusinessId'), membership: toMembership(membership), authorityEpoch: membership.authorityEpochValue, auditEvent });
  }
  createAtomically(_intentId: string, result: CreatedBusinessAuthority): Promise<CreatedBusinessAuthority> {
    this.state.businesses[result.businessId] = { businessId: result.businessId, status: 'active', createdAt: result.membership.createdAt.toISOString() };
    this.state.memberships[result.membership.membershipId] = fromMembership(result.membership);
    this.persist();
    return Promise.resolve(result);
  }

  // ---- MembershipApprovalStore ----
  findApproved(intentId: string): Promise<BusinessMembership | null> {
    const membership = this.state.memberships[deriveIntentScopedId([intentId], 'membership')];
    return Promise.resolve(membership ? toMembership(membership) : null);
  }
  saveApproved(_intentId: string, membership: BusinessMembership, _approvedByMembershipId: string): Promise<BusinessMembership> {
    this.state.memberships[membership.membershipId] = fromMembership(membership);
    this.persist();
    return Promise.resolve(membership);
  }

  // ---- DeviceRegistrationStore ----
  find(businessId: string, deviceId: string, keyVersion: number): Promise<RegisteredBusinessDevice | null> {
    const device = this.state.devices[deviceKey(businessId, deviceId, keyVersion)];
    return Promise.resolve(device ? toDevice(device) : null);
  }
  save(device: RegisteredBusinessDevice): Promise<RegisteredBusinessDevice> {
    this.state.devices[deviceKey(device.businessId, device.deviceId, device.deviceKeyVersion)] = fromDevice(device);
    this.persist();
    return Promise.resolve(device);
  }

  // ---- AuthorityMutationStore ----
  updateMembership(expectedEpoch: number, membership: BusinessMembership): Promise<boolean> {
    const current = this.state.memberships[membership.membershipId];
    if (!current || current.authorityEpochValue !== expectedEpoch) return Promise.resolve(false);
    this.state.memberships[membership.membershipId] = fromMembership(membership);
    this.persist();
    return Promise.resolve(true);
  }
  updateDevice(expectedEpoch: number, device: RegisteredBusinessDevice): Promise<boolean> {
    const key = deviceKey(device.businessId, device.deviceId, device.deviceKeyVersion);
    const current = this.state.devices[key];
    if (!current || current.authorityEpochValue !== expectedEpoch) return Promise.resolve(false);
    this.state.devices[key] = fromDevice(device);
    this.persist();
    return Promise.resolve(true);
  }

  /** Full hydrated membership by its own id -- used by the dev CLI's `grant-scope`/`revoke-*`
   * commands, which need the complete current object (in particular its epoch) to call
   * `AuthorityRevocationService`, not just the summary `describeMembership` below exposes. */
  findMembershipById(membershipId: string): Promise<BusinessMembership | null> {
    const membership = this.state.memberships[membershipId];
    return Promise.resolve(membership ? toMembership(membership) : null);
  }

  /** Non-secret status inspection for the dev CLI's `status` command -- never exposes key material. */
  describeMembership(membershipId: string): { businessId: string; actorId: string; status: string; authorityScope: readonly string[]; authorityEpoch: number } | null {
    const membership = this.state.memberships[membershipId];
    return membership ? { businessId: membership.businessId, actorId: membership.actorId, status: membership.status, authorityScope: membership.authorityScope, authorityEpoch: membership.authorityEpochValue } : null;
  }
  describeDevice(businessId: string, deviceId: string, keyVersion: number): { status: string; authorityEpoch: number; publicKeyFingerprint: string } | null {
    const device = this.state.devices[deviceKey(businessId, deviceId, keyVersion)];
    return device ? { status: device.status, authorityEpoch: device.authorityEpochValue, publicKeyFingerprint: device.publicKeyFingerprint } : null;
  }

  /** Full current-state snapshot for authority re-validation at credential USE time (not issuance
   * time) -- e.g. Relay's own pilot verifier, checking a presented credential's claims are still
   * backed by live, unrevoked, unchanged authority. Mirrors exactly what a real Postgres reader
   * would fetch from the same three tables Relay and Trust already share physically.
   *
   * CURRENT DEVICE KEY (relay-authority-repair round 2, 2026-08-29): takes no `deviceKeyVersion` --
   * "current" is independently the highest device_key_version among this device identity's ACTIVE
   * rows, mirroring `PostgresTrustAuthoritySnapshotReader`'s own fix and the pre-existing
   * `AuthorityRepository.findActiveDevice()` selection rule (`ORDER BY device_key_version DESC LIMIT
   * 1 WHERE status = 'active'`). See that Postgres reader's doc comment for why looking the device up
   * BY the credential's own claimed version made the version-match check downstream a tautology. */
  snapshot(businessId: string, deviceId: string): { businessStatus: 'active' | 'suspended' | 'revoked' | null; membership: BusinessMembership | null; device: RegisteredBusinessDevice | null } {
    const business = this.state.businesses[businessId];
    const device = Object.values(this.state.devices)
      .filter((candidate) => candidate.businessId === businessId && candidate.deviceId === deviceId && candidate.status === 'active')
      .reduce<StoredDevice | undefined>((current, candidate) => (!current || candidate.deviceKeyVersion > current.deviceKeyVersion ? candidate : current), undefined);
    const membership = device ? this.state.memberships[device.membershipId] : undefined;
    return {
      businessStatus: business?.status ?? null,
      membership: membership ? toMembership(membership) : null,
      device: device ? toDevice(device) : null,
    };
  }
}

function deviceKey(businessId: string, deviceId: string, keyVersion: number): string { return `${businessId}::${deviceId}::${keyVersion}`; }

function toMembership(row: StoredMembership): BusinessMembership {
  return {
    membershipId: identifier(row.membershipId, 'MembershipId'), businessId: identifier(row.businessId, 'BusinessId'),
    actorId: identifier(row.actorId, 'ActorId'), status: row.status, authorityScope: new AuthorityScope(row.authorityScope as AuthorityCapability[]),
    authorityEpoch: { value: row.authorityEpochValue }, createdAt: new Date(row.createdAt), modifiedAt: new Date(row.modifiedAt),
  };
}
function fromMembership(membership: BusinessMembership): StoredMembership {
  return {
    membershipId: membership.membershipId, businessId: membership.businessId, actorId: membership.actorId, status: membership.status,
    authorityScope: [...membership.authorityScope.capabilities], authorityEpochValue: membership.authorityEpoch.value,
    createdAt: membership.createdAt.toISOString(), modifiedAt: membership.modifiedAt.toISOString(),
  };
}
function toDevice(row: StoredDevice): RegisteredBusinessDevice {
  return {
    businessId: identifier(row.businessId, 'BusinessId'), actorId: identifier(row.actorId, 'ActorId'), membershipId: identifier(row.membershipId, 'MembershipId'),
    deviceId: identifier(row.deviceId, 'DeviceId'), deviceKeyId: identifier(row.deviceKeyId, 'DeviceKeyId'), deviceKeyVersion: row.deviceKeyVersion,
    publicKey: Buffer.from(row.publicKey, 'base64'), publicKeyFingerprint: row.publicKeyFingerprint, status: row.status,
    authorityEpoch: { value: row.authorityEpochValue }, createdAt: new Date(row.createdAt),
    ...(row.revokedAt ? { revokedAt: new Date(row.revokedAt) } : {}),
  };
}
function fromDevice(device: RegisteredBusinessDevice): StoredDevice {
  return {
    businessId: device.businessId, actorId: device.actorId, membershipId: device.membershipId, deviceId: device.deviceId,
    deviceKeyId: device.deviceKeyId, deviceKeyVersion: device.deviceKeyVersion, publicKey: Buffer.from(device.publicKey).toString('base64'),
    publicKeyFingerprint: device.publicKeyFingerprint, status: device.status, authorityEpochValue: device.authorityEpoch.value,
    createdAt: device.createdAt.toISOString(), ...(device.revokedAt ? { revokedAt: device.revokedAt.toISOString() } : {}),
  };
}
