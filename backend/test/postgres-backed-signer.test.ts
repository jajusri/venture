import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { AuthorityScope, identifier, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
import { BusinessDeviceCredentialIssuer, type IssuedBusinessDeviceCredential } from '../services/trust/src/application/issue-credential.js';
import type { TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { InMemoryCredentialIssuanceStore } from '../services/trust/src/persistence/in-memory-credential-store.js';
import { ManagedSigningKeyRegistry } from '../services/trust/src/persistence/managed-signing-key-registry.js';
import { PostgresBackedTrustCredentialSigner } from '../services/trust/src/persistence/postgres-backed-signer.js';
import { PostgresIssuerSigningKeyStore } from '../services/trust/src/persistence/postgres-issuer-signing-key-store.js';
import { initializeIssuerSigningKeyOnStartup } from '../services/trust/src/persistence/signing-key-startup.js';

/** Same stateful fake shape as the other round-6/7 test files -- duplicated per this codebase's own
 * per-file self-contained fake convention. `seedDirectly` bypasses the fake's own unique-active
 * enforcement, needed ONLY to construct the intentionally-corrupted ">1 active" defensive-test
 * state a real partial unique index would never allow through normal application code. */
interface FakeKeyRow { issuer_id: string; key_id: string; status: string; profile: string; public_key_pem: string; created_at: Date; activated_at: Date | null; retired_at: Date | null; revoked_at: Date | null }
class StatefulSigningKeyDatabase implements Database {
  private rows: FakeKeyRow[] = [];
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    if (/^\s*SELECT 1 FROM trust_issuer_signing_key WHERE issuer_id = \$1 AND status = \$2/i.test(sql)) {
      const [issuerId, status] = parameters as [string, string];
      const match = this.rows.some((r) => r.issuer_id === issuerId && r.status === status);
      return Promise.resolve({ rows: match ? [{} as Row] : [], rowCount: match ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_issuer_signing_key WHERE issuer_id = \$1 AND status = 'active'/i.test(sql)) {
      const [issuerId] = parameters as [string];
      const row = this.rows.find((r) => r.issuer_id === issuerId && r.status === 'active');
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_issuer_signing_key WHERE issuer_id = \$1 AND key_id = \$2/i.test(sql)) {
      const [issuerId, keyId] = parameters as [string, string];
      const row = this.rows.find((r) => r.issuer_id === issuerId && r.key_id === keyId);
      return Promise.resolve({ rows: row ? [row as unknown as Row] : [], rowCount: row ? 1 : 0 });
    }
    if (/^\s*SELECT .* FROM trust_issuer_signing_key WHERE issuer_id = \$1 ORDER BY created_at/i.test(sql)) {
      const [issuerId] = parameters as [string];
      const matches = this.rows.filter((r) => r.issuer_id === issuerId).sort((a, b) => a.created_at.getTime() - b.created_at.getTime());
      return Promise.resolve({ rows: matches as unknown as Row[], rowCount: matches.length });
    }
    if (/^\s*INSERT INTO trust_issuer_signing_key/i.test(sql)) {
      const [issuerId, keyId, profile, publicKeyPem, now] = parameters as [string, string, string, string, Date];
      if (this.rows.some((r) => r.issuer_id === issuerId && r.status === 'active')) throw new Error('simulated unique index violation');
      this.rows.push({ issuer_id: issuerId, key_id: keyId, status: 'active', profile, public_key_pem: publicKeyPem, created_at: now, activated_at: now, retired_at: null, revoked_at: null });
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*UPDATE trust_issuer_signing_key SET status = 'retired'/i.test(sql)) {
      const [now, issuerId, keyId] = parameters as [Date, string, string];
      const row = this.rows.find((r) => r.issuer_id === issuerId && r.key_id === keyId && r.status === 'active');
      if (!row) return Promise.resolve({ rows: [], rowCount: 0 });
      row.status = 'retired'; row.retired_at = now;
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    if (/^\s*UPDATE trust_issuer_signing_key SET status = 'revoked'/i.test(sql)) {
      const [now, issuerId, keyId] = parameters as [Date, string, string];
      const row = this.rows.find((r) => r.issuer_id === issuerId && r.key_id === keyId);
      if (!row) return Promise.resolve({ rows: [], rowCount: 0 });
      row.status = 'revoked'; row.revoked_at = now;
      return Promise.resolve({ rows: [], rowCount: 1 });
    }
    return Promise.resolve({ rows: [], rowCount: 0 });
  }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> {
    const snapshot = this.rows.map((r) => ({ ...r }));
    try { return await work(this); } catch (error) { this.rows = snapshot; throw error; }
  }
  async close(): Promise<void> {}
  seedDirectly(row: FakeKeyRow): void { this.rows.push(row); }
}

const ISSUER_ID = 'issuer-1';
const PROFILE = 'P256-SHA256-v1';

const membership: BusinessMembership = {
  membershipId: identifier('membership-1', 'MembershipId'), businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'),
  status: 'active', authorityScope: new AuthorityScope(['send_orders', 'issue_credentials']), authorityEpoch: { value: 1 }, createdAt: new Date(0), modifiedAt: new Date(0),
};
const business = { businessId: identifier('business-1', 'BusinessId'), status: 'active' as const };
const device: RegisteredBusinessDevice = {
  businessId: identifier('business-1', 'BusinessId'), actorId: identifier('actor-1', 'ActorId'), membershipId: identifier('membership-1', 'MembershipId'),
  deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('device-1-key-1', 'DeviceKeyId'), deviceKeyVersion: 1,
  publicKey: new Uint8Array(65).fill(9), publicKeyFingerprint: 'fingerprint-1', status: 'active', authorityEpoch: { value: 1 }, createdAt: new Date(0),
};

function issueOnce(signer: TrustCredentialSigner, nowEpochMillis: number, intentId: string): Promise<IssuedBusinessDeviceCredential> {
  return new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => new Date(nowEpochMillis)).issue({
    business, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId,
  });
}

describe('PostgresBackedTrustCredentialSigner + startup decision -- round 7 adversarial proof', () => {
  let workDir: string;
  beforeEach(() => { workDir = mkdtempSync(join(tmpdir(), 'venture-signer-adversarial-')); });
  afterEach(() => { rmSync(workDir, { recursive: true, force: true }); });

  it('SEPARATE INSTANCE ROTATION: an independent operator registry rotates A to B; the server signer, never recreated, uses B on its very next issuance and A can never issue again', async () => {
    const db = new StatefulSigningKeyDatabase();
    const serverStore = new PostgresIssuerSigningKeyStore(db);
    const serverSigner = new PostgresBackedTrustCredentialSigner(serverStore, ISSUER_ID, workDir);
    const operatorStore = new PostgresIssuerSigningKeyStore(db);
    const operatorRegistry = new ManagedSigningKeyRegistry(operatorStore, ISSUER_ID, workDir);

    await operatorRegistry.bootstrap('key-A', PROFILE, new Date(0));
    const first = await issueOnce(serverSigner, 100, 'issue-1');
    expect(first.signature.issuerKeyId).toBe('key-A');

    // Independent operator process rotates -- the "server" signer above is never recreated and has
    // no refresh method to call.
    await operatorRegistry.rotate('key-B', PROFILE, new Date(200));

    const second = await issueOnce(serverSigner, 300, 'issue-2');
    expect(second.signature.issuerKeyId).toBe('key-B');
  });

  it('SEPARATE INSTANCE REVOCATION: an independent operator registry revokes A; the server signer, never recreated, fails closed on its very next issuance', async () => {
    const db = new StatefulSigningKeyDatabase();
    const serverStore = new PostgresIssuerSigningKeyStore(db);
    const serverSigner = new PostgresBackedTrustCredentialSigner(serverStore, ISSUER_ID, workDir);
    const operatorStore = new PostgresIssuerSigningKeyStore(db);
    const operatorRegistry = new ManagedSigningKeyRegistry(operatorStore, ISSUER_ID, workDir);

    await operatorRegistry.bootstrap('key-A', PROFILE, new Date(0));
    const first = await issueOnce(serverSigner, 100, 'issue-1');
    expect(first.signature.issuerKeyId).toBe('key-A');

    await operatorRegistry.revoke('key-A', new Date(200));

    await expect(issueOnce(serverSigner, 300, 'issue-2')).rejects.toThrow('exactly one authoritative active key');
  });

  it('BOOTSTRAP-AFTER-REVOCATION: through the actual production startup decision path, a restart after revoke-to-zero-active never recreates authority', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);

    const firstOutcome = await initializeIssuerSigningKeyOnStartup(store, registry, ISSUER_ID, 'key-A', PROFILE, new Date(0));
    expect(firstOutcome).toBe('bootstrapped_fresh');

    await registry.revoke('key-A', new Date(100));

    // "restart" -- entirely fresh store/registry instances, same production decision function.
    const restartStore = new PostgresIssuerSigningKeyStore(db);
    const restartRegistry = new ManagedSigningKeyRegistry(restartStore, ISSUER_ID, workDir);
    const restartOutcome = await initializeIssuerSigningKeyOnStartup(restartStore, restartRegistry, ISSUER_ID, 'key-A', PROFILE, new Date(200));

    expect(restartOutcome).toBe('blocked_recovery_required');
    const described = await restartStore.describe(ISSUER_ID);
    expect(described).toHaveLength(1); // nothing new created -- still only the original revoked key-A
    expect(described[0]?.status).toBe('revoked');

    const signer = new PostgresBackedTrustCredentialSigner(restartStore, ISSUER_ID, workDir);
    await expect(issueOnce(signer, 300, 'issue-blocked')).rejects.toThrow('exactly one authoritative active key');

    // Explicit privileged operator recovery (never startup) restores issuance.
    await registry.bootstrap('key-B', PROFILE, new Date(400));
    const recovered = await issueOnce(signer, 500, 'issue-recovered');
    expect(recovered.signature.issuerKeyId).toBe('key-B');
  });

  it('a genuinely fresh issuer (zero lifecycle rows ever) is the ONLY case startup may auto-bootstrap', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);
    const outcome = await initializeIssuerSigningKeyOnStartup(store, registry, ISSUER_ID, 'key-A', PROFILE, new Date(0));
    expect(outcome).toBe('bootstrapped_fresh');
    expect((await store.describe(ISSUER_ID))[0]?.status).toBe('active');
  });

  it('an issuer with one already-active key is left alone by startup -- using_existing_active, no duplicate bootstrap attempted', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);
    await registry.bootstrap('key-A', PROFILE, new Date(0));
    const outcome = await initializeIssuerSigningKeyOnStartup(store, registry, ISSUER_ID, 'key-A', PROFILE, new Date(100));
    expect(outcome).toBe('using_existing_active');
    expect(await store.describe(ISSUER_ID)).toHaveLength(1);
  });

  it('MISSING PEM: PostgreSQL says a key is active but its local private-key file was never created -- issuance fails closed, no automatic replacement key generated', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    // Inserted directly against the store (bypassing the registry, which would generate the local
    // file) -- simulates an active DB row whose local key file is missing/was never created.
    await store.bootstrap(ISSUER_ID, 'key-missing', PROFILE, 'unused-pem-placeholder', new Date(0));
    const signer = new PostgresBackedTrustCredentialSigner(store, ISSUER_ID, workDir);
    await expect(issueOnce(signer, 100, 'issue-missing')).rejects.toThrow(/Issuer signing key file not found/);
  });

  it('CORRUPT PEM: the local private-key file exists but is not valid key material -- issuance fails closed, no silent fallback', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    await store.bootstrap(ISSUER_ID, 'key-corrupt', PROFILE, 'unused-pem-placeholder', new Date(0));
    mkdirSync(workDir, { recursive: true });
    writeFileSync(join(workDir, 'key-corrupt.pem'), 'not a real PEM file', { mode: 0o600 });
    const signer = new PostgresBackedTrustCredentialSigner(store, ISSUER_ID, workDir);
    await expect(issueOnce(signer, 100, 'issue-corrupt')).rejects.toThrow();
  });

  it('MULTIPLE ACTIVE (defensive, corrupted/test-only state): issuance fails closed rather than picking first/latest/arbitrary', async () => {
    const db = new StatefulSigningKeyDatabase();
    // Seeded directly, bypassing the store's own duplicate-active guard -- a real partial unique
    // index would never allow this through normal application code; this proves the RUNTIME code
    // does not merely trust that index, but independently verifies the count itself.
    db.seedDirectly({ issuer_id: ISSUER_ID, key_id: 'key-1', status: 'active', profile: PROFILE, public_key_pem: 'p1', created_at: new Date(0), activated_at: new Date(0), retired_at: null, revoked_at: null });
    db.seedDirectly({ issuer_id: ISSUER_ID, key_id: 'key-2', status: 'active', profile: PROFILE, public_key_pem: 'p2', created_at: new Date(1), activated_at: new Date(1), retired_at: null, revoked_at: null });
    const store = new PostgresIssuerSigningKeyStore(db);
    const signer = new PostgresBackedTrustCredentialSigner(store, ISSUER_ID, workDir);
    await expect(issueOnce(signer, 100, 'issue-ambiguous')).rejects.toThrow('found 2 active issuer keys');
  });

  it('an unsafe key id somehow present in PostgreSQL is rejected before any filesystem access is attempted at issuance time', async () => {
    const db = new StatefulSigningKeyDatabase();
    db.seedDirectly({ issuer_id: ISSUER_ID, key_id: '../escape', status: 'active', profile: PROFILE, public_key_pem: 'p1', created_at: new Date(0), activated_at: new Date(0), retired_at: null, revoked_at: null });
    const store = new PostgresIssuerSigningKeyStore(db);
    const signer = new PostgresBackedTrustCredentialSigner(store, ISSUER_ID, workDir);
    await expect(issueOnce(signer, 100, 'issue-unsafe-id')).rejects.toThrow(/Invalid signing key id/);
  });
});
