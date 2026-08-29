import { verify } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { AuthorityScope, identifier, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';
import { BusinessDeviceCredentialIssuer, credentialSigningPayload } from '../services/trust/src/application/issue-credential.js';
import { RotatingTrustCredentialSigner } from '../services/trust/src/application/signer-rotation.js';
import { VerificationKeyDirectory } from '../services/trust/src/application/verification-keys.js';
import { InMemoryCredentialIssuanceStore } from '../services/trust/src/persistence/in-memory-credential-store.js';
import { ManagedSigningKeyRegistry } from '../services/trust/src/persistence/managed-signing-key-registry.js';
import { PostgresIssuerSigningKeyStore } from '../services/trust/src/persistence/postgres-issuer-signing-key-store.js';

/** Same stateful fake shape as the other round-6 test files -- duplicated per this codebase's own
 * per-file self-contained fake convention. */
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

describe('Trust issuer signing-key lifecycle -- end-to-end (round 6 key-lifecycle Gate 6)', () => {
  let workDir: string;
  beforeEach(() => { workDir = mkdtempSync(join(tmpdir(), 'budcom-key-lifecycle-')); });
  afterEach(() => { rmSync(workDir, { recursive: true, force: true }); });

  it('A-F: bootstrap, issue, rotate, issue again, old credential still verifies while retired, revoke makes it immediately reported revoked, and a fresh registry (simulated restart) sees the same persisted lifecycle', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);
    const verificationKeys = new VerificationKeyDirectory(store);

    // --- A: key A active -> credential A issued -> verification succeeds ---
    await registry.bootstrap('key-A', PROFILE, new Date(0));
    const signer = new RotatingTrustCredentialSigner(() => registry.handles());
    const credentialA = await new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => new Date(100)).issue({
      business, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-A',
    });
    expect(credentialA.signature.issuerKeyId).toBe('key-A');
    const keysAfterA = await verificationKeys.list(ISSUER_ID, new Date(100));
    const keyAAfterA = keysAfterA.find((k) => k.issuerKeyId === 'key-A')!;
    expect(keyAAfterA.status).toBe('active');
    expect(verify('sha256', credentialSigningPayload(credentialA.claims), keyAAfterA.publicKey, credentialA.signature.signature)).toBe(true);

    // --- B: rotate -- A transitions to retired, B becomes current active signer ---
    const rotation = await registry.rotate('key-B', PROFILE, new Date(200));
    expect(rotation.retiredKeyId).toBe('key-A');

    // --- C: a new credential is signed by B ---
    const credentialB = await new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => new Date(300)).issue({
      business, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-B',
    });
    expect(credentialB.signature.issuerKeyId).toBe('key-B');
    const keysAfterRotate = await verificationKeys.list(ISSUER_ID, new Date(300));
    const keyBAfterRotate = keysAfterRotate.find((k) => k.issuerKeyId === 'key-B')!;
    expect(keyBAfterRotate.status).toBe('active');
    expect(verify('sha256', credentialSigningPayload(credentialB.claims), keyBAfterRotate.publicKey, credentialB.signature.signature)).toBe(true);

    // --- D: old A credential's behavior matches A's status -- RETIRED still verifies (key-lifecycle
    // invariant 2: "historical keys may remain visible for verification according to their explicit
    // lifecycle state" -- retired is not revoked) ---
    const keyAAfterRotate = keysAfterRotate.find((k) => k.issuerKeyId === 'key-A')!;
    expect(keyAAfterRotate.status).toBe('retired');
    expect(verify('sha256', credentialSigningPayload(credentialA.claims), keyAAfterRotate.publicKey, credentialA.signature.signature)).toBe(true);

    // --- E: revoke A -> verification endpoint immediately reports revoked ---
    await registry.revoke('key-A', new Date(400));
    const keysAfterRevoke = await verificationKeys.list(ISSUER_ID, new Date(400));
    const keyAAfterRevoke = keysAfterRevoke.find((k) => k.issuerKeyId === 'key-A')!;
    expect(keyAAfterRevoke.status).toBe('revoked');
    // The signature itself remains mathematically valid (revocation is a policy fact, not a crypto
    // one) -- this is exactly why a live consumer must check `status`/`revoked`, never key existence
    // alone (Android's own CachedTransportCredentialVerifier does this today, proven in
    // core/trust's LiveAuthorityRevocationTest). Proven here: the endpoint reports the true status
    // for a real verifier to act on.
    expect(verify('sha256', credentialSigningPayload(credentialA.claims), keyAAfterRevoke.publicKey, credentialA.signature.signature)).toBe(true);

    // --- F: restart -- construct entirely fresh store/registry instances (simulating a new process)
    // against the SAME persisted state; lifecycle must survive, B remains current, A remains revoked ---
    const freshStore = new PostgresIssuerSigningKeyStore(db);
    const freshRegistry = new ManagedSigningKeyRegistry(freshStore, ISSUER_ID, workDir);
    await freshRegistry.refresh();
    const freshHandles = freshRegistry.handles();
    expect(freshHandles.filter((h) => h.status === 'active')).toHaveLength(1);
    const freshDescribe = await freshStore.describe(ISSUER_ID);
    expect(freshDescribe.find((k) => k.keyId === 'key-B')?.status).toBe('active');
    expect(freshDescribe.find((k) => k.keyId === 'key-A')?.status).toBe('revoked');
    const freshSigner = new RotatingTrustCredentialSigner(() => freshRegistry.handles());
    const credentialAfterRestart = await new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), freshSigner, 3_600_000, () => new Date(500)).issue({
      business, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-after-restart',
    });
    expect(credentialAfterRestart.signature.issuerKeyId).toBe('key-B');
  });

  it('revoking the only active key stops all new issuance (fail-closed outage, not a bypass) until an operator activates a replacement', async () => {
    const db = new StatefulSigningKeyDatabase();
    const store = new PostgresIssuerSigningKeyStore(db);
    const registry = new ManagedSigningKeyRegistry(store, ISSUER_ID, workDir);
    await registry.bootstrap('key-A', PROFILE, new Date(0));
    await registry.revoke('key-A', new Date(100));
    const signer = new RotatingTrustCredentialSigner(() => registry.handles());
    await expect(new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => new Date(200)).issue({
      business, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-after-revoke',
    })).rejects.toThrow('Exactly one');
    // Recovery: activating a replacement restores issuance immediately, no restart required.
    await registry.bootstrap('key-B', PROFILE, new Date(300));
    const credential = await new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => new Date(400)).issue({
      business, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'issue-after-recovery',
    });
    expect(credential.signature.issuerKeyId).toBe('key-B');
  });
});
