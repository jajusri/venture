import { createHash, generateKeyPairSync, randomUUID, sign, verify } from 'node:crypto';
import { rmSync } from 'node:fs';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { PostgresDatabase } from '../packages/persistence/src/postgres-database.js';
import { runMigrations } from '../packages/persistence/src/migrations.js';
import { PostgresBusinessBootstrapStore, PostgresDeviceRegistrationStore } from '../services/trust/src/persistence/postgres-authority-write-store.js';
import { deriveIntentScopedId, createBusinessDeterministicIds } from '../services/trust/src/persistence/file-backed-authority-store.js';
import { CreateBusiness, type CreatedBusinessAuthority } from '../services/trust/src/application/create-business.js';
import { PostgresEnrollmentGrantStore } from '../services/trust/src/persistence/postgres-enrollment-grant-store.js';
import { ConsumeDeviceEnrollmentGrant, DeviceEnrollmentRejected } from '../services/trust/src/application/consume-enrollment-grant.js';
import { BusinessDeviceCredentialIssuer, type TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { InMemoryCredentialIssuanceStore } from '../services/trust/src/persistence/in-memory-credential-store.js';
import { hashEnrollmentGrantSecret } from '../services/trust/src/domain/enrollment.js';
import { ManagedSigningKeyRegistry } from '../services/trust/src/persistence/managed-signing-key-registry.js';
import { PostgresIssuerSigningKeyStore } from '../services/trust/src/persistence/postgres-issuer-signing-key-store.js';
import { PostgresBackedTrustCredentialSigner } from '../services/trust/src/persistence/postgres-backed-signer.js';
import { AuthorityScope, identifier, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';

/**
 * LIVE PostgreSQL regression suite -- opt-in only, via BUDCOM_TRUST_DATABASE_URL.
 *
 * Run explicitly with:
 *   cd backend
 *   BUDCOM_TRUST_DATABASE_URL=postgres://<user>:<password>@<host>:5432/<db> npm run test:live-postgres
 * (or `set -a; . ./.env; set +a; npm run test:live-postgres` on a shell that sources a local,
 * gitignored `.env` -- see `.env.example`.)
 *
 * When the env var is absent, this entire file is skipped at collection time (see `describe.skipIf`
 * below) -- `npm test`/`npm run test:live-postgres` with no database configured never attempts a
 * connection, so the ordinary backend suite's independence from PostgreSQL is unaffected by this
 * file merely existing. This closes the "structurally verified, not behaviorally verified against a
 * live database" boundary previously documented in `postgres-authority-write-store.ts`,
 * `postgres-enrollment-grant-store.ts`, and `CONTROLLED-PILOT-RUNBOOK.md` -- first proven manually
 * against real PostgreSQL 18 in the LIVE POSTGRESQL TRUST KEY-LIFECYCLE PROOF GATE round; this file
 * makes that proof a standing, repeatable regression rather than a one-time manual exercise.
 *
 * Every row this file writes is scoped under a fresh `randomUUID()` per test run/test, deleted in a
 * `finally` block inside each test (so cleanup runs even when an assertion throws) -- never a
 * shared/well-known identifier, and never anything resembling real business/pilot data. No test here
 * ever connects as, or requires, a PostgreSQL superuser role.
 */
const DATABASE_URL = process.env.BUDCOM_TRUST_DATABASE_URL;

describe.skipIf(!DATABASE_URL)('LIVE PostgreSQL regression (BUDCOM_TRUST_DATABASE_URL required)', () => {
  let database: PostgresDatabase;
  const RUN = randomUUID().slice(0, 8);
  const KEY_DIR_BASE = `.local/live-regression-${RUN}`;

  beforeAll(async () => {
    database = new PostgresDatabase(DATABASE_URL!, 5);
    await runMigrations(database);
  });

  afterAll(async () => {
    await database.close();
    rmSync(KEY_DIR_BASE, { recursive: true, force: true });
  });

  it('1. migrations are idempotent against a real database', async () => {
    await runMigrations(database); // second run against the SAME already-migrated database
    const applied = await database.query<{ count: string }>('SELECT count(*)::text AS count FROM trust_schema_migration');
    expect(Number(applied.rows[0]!.count)).toBeGreaterThanOrEqual(8);
    const indexDef = await database.query<{ indexdef: string }>(
      `SELECT indexdef FROM pg_indexes WHERE tablename = 'trust_issuer_signing_key' AND indexname = 'trust_issuer_signing_key_one_active_idx'`,
    );
    expect(indexDef.rows[0]?.indexdef).toMatch(/UNIQUE INDEX .* ON public\.trust_issuer_signing_key USING btree \(issuer_id\) WHERE \(status = 'active'::text\)/);
  });

  it('2. Business + initial Membership are created atomically', async () => {
    const actor = `live-${RUN}-actor-create`;
    const intent = `live-${RUN}-intent-create`;
    const businessId = deriveIntentScopedId([actor, intent], 'business');
    try {
      const store = new PostgresBusinessBootstrapStore(database);
      const created = await new CreateBusiness(store, () => new Date(), createBusinessDeterministicIds(actor, intent)).execute({
        principal: { actorId: identifier(actor, 'ActorId'), verificationId: `live-${RUN}-verified`, verifiedAt: new Date(1) },
        intentId: intent, authorityDisplayName: 'Live Regression Business',
      });
      const businessRow = await database.query<{ status: string; authority_epoch: string }>('SELECT status, authority_epoch FROM trust_business_authority WHERE business_id = $1', [created.businessId]);
      const membershipRow = await database.query('SELECT 1 FROM trust_business_membership WHERE membership_id = $1', [created.membership.membershipId]);
      expect(businessRow.rowCount).toBe(1);
      expect(businessRow.rows[0]!.status).toBe('active');
      expect(Number(businessRow.rows[0]!.authority_epoch)).toBe(1);
      expect(membershipRow.rowCount).toBe(1);
    } finally {
      await database.query('DELETE FROM trust_business_membership WHERE business_id = $1', [businessId]);
      await database.query('DELETE FROM trust_business_authority WHERE business_id = $1', [businessId]);
    }
  });

  it('3. Business-wins/Membership-conflict rolls back the whole attempt -- zero new Business rows survive', async () => {
    const actor = `live-${RUN}-actor-conflict`;
    const intent = `live-${RUN}-intent-conflict`;
    const businessId = deriveIntentScopedId([actor, intent], 'business');
    const membershipId = deriveIntentScopedId([actor, intent], 'membership');
    const anchorBusinessId = `${businessId}-anchor`;
    try {
      const now = new Date();
      // A real FK (membership.business_id -> business.business_id) means the conflicting membership
      // row must reference a REAL business -- a separate anchor, never the target businessId, which
      // must stay entirely absent so the attempt's own Business INSERT below is a genuine first-time
      // insert.
      await database.query(`INSERT INTO trust_business_authority(business_id, status, authority_epoch, created_at, modified_at) VALUES ($1,'active',1,$2,$2)`, [anchorBusinessId, now]);
      await database.query(
        `INSERT INTO trust_business_membership(membership_id, business_id, actor_id, status, authority_scope, authority_epoch, created_at, modified_at) VALUES ($1,$2,$3,'active',$4,1,$5,$5)`,
        [membershipId, anchorBusinessId, 'some-other-actor', ['send_orders'], now],
      );
      const store = new PostgresBusinessBootstrapStore(database);
      const attempted: CreatedBusinessAuthority = {
        businessId: identifier(businessId, 'BusinessId'),
        membership: {
          membershipId: identifier(membershipId, 'MembershipId'), businessId: identifier(businessId, 'BusinessId'), actorId: identifier(actor, 'ActorId'),
          status: 'active', authorityScope: new AuthorityScope(['manage_memberships', 'approve_memberships', 'register_devices', 'revoke_devices', 'issue_credentials', 'send_orders']),
          authorityEpoch: { value: 1 }, createdAt: now, modifiedAt: now,
        },
        authorityEpoch: 1,
        auditEvent: { eventId: `live-${RUN}-audit-conflict`, kind: 'business_authority_created', businessId: identifier(businessId, 'BusinessId'), actorId: actor, occurredAt: now },
      };
      await expect(store.createAtomically(intent, attempted)).rejects.toThrow();
      const businessRows = await database.query('SELECT 1 FROM trust_business_authority WHERE business_id = $1', [businessId]);
      expect(businessRows.rowCount).toBe(0);
    } finally {
      await database.query('DELETE FROM trust_business_membership WHERE business_id = $1', [businessId]);
      await database.query('DELETE FROM trust_business_authority WHERE business_id = $1', [businessId]);
      await database.query('DELETE FROM trust_business_membership WHERE business_id = $1', [anchorBusinessId]);
      await database.query('DELETE FROM trust_business_authority WHERE business_id = $1', [anchorBusinessId]);
    }
  });

  it('4. exact retry is idempotent -- identical createAtomically call twice returns the same result', async () => {
    const actor = `live-${RUN}-actor-retry`;
    const intent = `live-${RUN}-intent-retry`;
    const businessId = deriveIntentScopedId([actor, intent], 'business');
    const membershipId = deriveIntentScopedId([actor, intent], 'membership');
    try {
      const now = new Date();
      const store = new PostgresBusinessBootstrapStore(database);
      const result: CreatedBusinessAuthority = {
        businessId: identifier(businessId, 'BusinessId'),
        membership: {
          membershipId: identifier(membershipId, 'MembershipId'), businessId: identifier(businessId, 'BusinessId'), actorId: identifier(actor, 'ActorId'),
          status: 'active', authorityScope: new AuthorityScope(['send_orders']), authorityEpoch: { value: 1 }, createdAt: now, modifiedAt: now,
        },
        authorityEpoch: 1,
        auditEvent: { eventId: `live-${RUN}-audit-retry`, kind: 'business_authority_created', businessId: identifier(businessId, 'BusinessId'), actorId: actor, occurredAt: now },
      };
      const first = await store.createAtomically(intent, result);
      const second = await store.createAtomically(intent, result);
      expect(second.businessId).toBe(first.businessId);
      expect(second.membership.membershipId).toBe(first.membership.membershipId);
      const count = await database.query<{ count: string }>('SELECT count(*)::text AS count FROM trust_business_authority WHERE business_id = $1', [businessId]);
      expect(count.rows[0]!.count).toBe('1');
    } finally {
      await database.query('DELETE FROM trust_business_membership WHERE business_id = $1', [businessId]);
      await database.query('DELETE FROM trust_business_authority WHERE business_id = $1', [businessId]);
    }
  });

  it('5. a conflicting device registration fails closed; the persisted row is unaffected', async () => {
    const businessId = `live-${RUN}-device-business`;
    const membershipId = `live-${RUN}-device-membership`;
    const deviceId = `live-${RUN}-device-1`;
    try {
      const now = new Date();
      await database.query(`INSERT INTO trust_business_authority(business_id, status, authority_epoch, created_at, modified_at) VALUES ($1,'active',1,$2,$2)`, [businessId, now]);
      await database.query(
        `INSERT INTO trust_business_membership(membership_id, business_id, actor_id, status, authority_scope, authority_epoch, created_at, modified_at) VALUES ($1,$2,$3,'active',$4,1,$5,$5)`,
        [membershipId, businessId, `live-${RUN}-device-actor`, ['register_devices'], now],
      );
      const store = new PostgresDeviceRegistrationStore(database);
      const original: RegisteredBusinessDevice = {
        businessId: identifier(businessId, 'BusinessId'), actorId: identifier(`live-${RUN}-device-actor`, 'ActorId'), membershipId: identifier(membershipId, 'MembershipId'),
        deviceId: identifier(deviceId, 'DeviceId'), deviceKeyId: identifier('key-1', 'DeviceKeyId'), deviceKeyVersion: 1,
        publicKey: new Uint8Array(65).fill(7), publicKeyFingerprint: 'fp-original', status: 'active', authorityEpoch: { value: 1 }, createdAt: now,
      };
      await store.save(original);
      await expect(store.save(original)).resolves.toBeTruthy(); // exact retry succeeds
      await expect(store.save({ ...original, status: 'revoked', authorityEpoch: { value: 2 }, publicKeyFingerprint: 'fp-different' })).rejects.toThrow('Conflicting device key registration');
      const row = await database.query<{ status: string }>('SELECT status FROM trust_registered_device WHERE business_id = $1 AND device_id = $2 AND device_key_version = 1', [businessId, deviceId]);
      expect(row.rows[0]!.status).toBe('active');
    } finally {
      await database.query('DELETE FROM trust_registered_device WHERE business_id = $1', [businessId]);
      await database.query('DELETE FROM trust_business_membership WHERE business_id = $1', [businessId]);
      await database.query('DELETE FROM trust_business_authority WHERE business_id = $1', [businessId]);
    }
  });

  it('6. concurrent double-consume of the same enrollment grant leaves exactly one winner', async () => {
    const businessId = `live-${RUN}-grant-business`;
    const membershipId = `live-${RUN}-grant-membership`;
    const actorId = `live-${RUN}-grant-actor`;
    const grantId = `live-${RUN}-grant-1`;
    const secret = `live-${RUN}-grant-secret-0123456789`;
    const dbA = new PostgresDatabase(DATABASE_URL!, 3);
    const dbB = new PostgresDatabase(DATABASE_URL!, 3);
    try {
      const now = new Date();
      await database.query(`INSERT INTO trust_business_authority(business_id, status, authority_epoch, created_at, modified_at) VALUES ($1,'active',1,$2,$2)`, [businessId, now]);
      await database.query(
        `INSERT INTO trust_business_membership(membership_id, business_id, actor_id, status, authority_scope, authority_epoch, created_at, modified_at) VALUES ($1,$2,$3,'active',$4,1,$5,$5)`,
        [membershipId, businessId, actorId, ['manage_memberships', 'approve_memberships', 'register_devices', 'revoke_devices', 'issue_credentials', 'send_orders'], now],
      );
      await database.query(
        `INSERT INTO trust_enrollment_grant(grant_id, business_id, actor_id, membership_id, granted_device_scope, grant_secret_hash, issued_at, expires_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
        [grantId, businessId, actorId, membershipId, ['send_orders'], hashEnrollmentGrantSecret(secret), now, new Date(now.getTime() + 900_000)],
      );

      const keyPair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
      const fakeSigner: TrustCredentialSigner = {
        sign: (build) => Promise.resolve({
          issuerId: 'live-regression-issuer', issuerKeyId: 'live-regression-key', profile: 'P256-SHA256-v1',
          signature: sign('sha256', build({ issuerId: 'live-regression-issuer', issuerKeyId: 'live-regression-key', profile: 'P256-SHA256-v1' }), keyPair.privateKey),
        }),
      };
      const attempt = (db: PostgresDatabase, deviceSuffix: 'A' | 'B') => {
        const consume = new ConsumeDeviceEnrollmentGrant(new PostgresEnrollmentGrantStore(db), new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), fakeSigner, 3_600_000));
        const publicKey = new Uint8Array(65).fill(deviceSuffix === 'A' ? 1 : 2);
        return consume.execute({
          grantId, grantSecret: secret, deviceId: identifier(`live-${RUN}-device-${deviceSuffix}`, 'DeviceId'),
          deviceKeyId: identifier(`live-${RUN}-device-${deviceSuffix}-key`, 'DeviceKeyId'), deviceKeyVersion: 1,
          publicKey, publicKeyFingerprint: createHash('sha256').update(publicKey).digest('base64'),
        });
      };

      const [a, b] = await Promise.allSettled([attempt(dbA, 'A'), attempt(dbB, 'B')]);
      const results = [a, b];
      const winners = results.filter((r) => r.status === 'fulfilled');
      const losers = results.filter((r): r is PromiseRejectedResult => r.status === 'rejected');
      expect(winners).toHaveLength(1);
      expect(losers).toHaveLength(1);
      expect(losers[0]!.reason).toBeInstanceOf(DeviceEnrollmentRejected);
      expect((losers[0]!.reason as DeviceEnrollmentRejected).reason).toBe('grant_already_consumed');
      const deviceRows = await database.query('SELECT device_id FROM trust_registered_device WHERE business_id = $1', [businessId]);
      expect(deviceRows.rowCount).toBe(1);
    } finally {
      await database.query('DELETE FROM trust_registered_device WHERE business_id = $1', [businessId]);
      await database.query('DELETE FROM trust_enrollment_grant WHERE grant_id = $1', [grantId]);
      await database.query('DELETE FROM trust_business_membership WHERE business_id = $1', [businessId]);
      await database.query('DELETE FROM trust_business_authority WHERE business_id = $1', [businessId]);
      await dbA.close();
      await dbB.close();
    }
  });

  it('7. issuer active-key uniqueness holds under a concurrent bootstrap race', async () => {
    const issuerId = `live-${RUN}-bootstrap-race-issuer`;
    const keyDir = `${KEY_DIR_BASE}/bootstrap-race-keys`;
    const dbA = new PostgresDatabase(DATABASE_URL!, 3);
    const dbB = new PostgresDatabase(DATABASE_URL!, 3);
    try {
      const registryA = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(dbA), issuerId, keyDir);
      const registryB = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(dbB), issuerId, keyDir);
      const [a, b] = await Promise.allSettled([
        registryA.bootstrap('race-key-a', 'P256-SHA256-v1', new Date()),
        registryB.bootstrap('race-key-b', 'P256-SHA256-v1', new Date()),
      ]);
      const results = [a, b];
      expect(results.filter((r) => r.status === 'fulfilled')).toHaveLength(1);
      expect(results.filter((r) => r.status === 'rejected')).toHaveLength(1);
      const activeRows = await database.query('SELECT key_id FROM trust_issuer_signing_key WHERE issuer_id = $1 AND status = $2', [issuerId, 'active']);
      expect(activeRows.rowCount).toBe(1);
    } finally {
      await database.query('DELETE FROM trust_issuer_signing_key WHERE issuer_id = $1', [issuerId]);
      await dbA.close();
      await dbB.close();
    }
  });

  it('8. rotation and revocation are immediately visible from a fresh store instance -- no in-memory cache to refresh', async () => {
    const issuerId = `live-${RUN}-lifecycle-issuer`;
    const keyDir = `${KEY_DIR_BASE}/lifecycle-keys`;
    try {
      const registry1 = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(database), issuerId, keyDir);
      await registry1.bootstrap('lifecycle-key-1', 'P256-SHA256-v1', new Date());

      // A FRESH registry/store instance -- simulating a separate process -- performs the rotation.
      const registry2 = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(database), issuerId, keyDir);
      await registry2.rotate('lifecycle-key-2', 'P256-SHA256-v1', new Date());

      // A THIRD fresh instance immediately observes the rotation -- nothing to refresh, nothing cached.
      const store3 = new PostgresIssuerSigningKeyStore(database);
      const afterRotate = await store3.listForIssuer(issuerId);
      expect(afterRotate.find((k) => k.issuerKeyId === 'lifecycle-key-1')?.status).toBe('retired');
      expect(afterRotate.find((k) => k.issuerKeyId === 'lifecycle-key-2')?.status).toBe('active');

      const registry4 = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(database), issuerId, keyDir);
      await registry4.revoke('lifecycle-key-2', new Date());
      const afterRevoke = await new PostgresIssuerSigningKeyStore(database).listForIssuer(issuerId);
      expect(afterRevoke.find((k) => k.issuerKeyId === 'lifecycle-key-2')?.status).toBe('revoked');

      // Real signing with the recovered key verifies against the live public key.
      const registry5 = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(database), issuerId, keyDir);
      await registry5.bootstrap('lifecycle-key-3', 'P256-SHA256-v1', new Date());
      const signer = new PostgresBackedTrustCredentialSigner(new PostgresIssuerSigningKeyStore(database), issuerId, keyDir);
      const signed = await signer.sign((identity) => Buffer.from(`payload:${identity.issuerKeyId}`));
      const activeKey = (await new PostgresIssuerSigningKeyStore(database).listForIssuer(issuerId)).find((k) => k.issuerKeyId === signed.issuerKeyId);
      expect(activeKey?.status).toBe('active');
      expect(verify('sha256', Buffer.from(`payload:${signed.issuerKeyId}`), activeKey!.publicKey, signed.signature)).toBe(true);
    } finally {
      await database.query('DELETE FROM trust_issuer_signing_key WHERE issuer_id = $1', [issuerId]);
    }
  });

  it('9. signing fails closed when the active key exists in Postgres but its local PEM is missing', async () => {
    const issuerId = `live-${RUN}-missing-pem-issuer`;
    const keyDir = `${KEY_DIR_BASE}/missing-pem-keys`;
    try {
      const registry = new ManagedSigningKeyRegistry(new PostgresIssuerSigningKeyStore(database), issuerId, keyDir);
      await registry.bootstrap('missing-pem-key', 'P256-SHA256-v1', new Date());
      rmSync(`${keyDir}/missing-pem-key.pem`, { force: true });
      const signer = new PostgresBackedTrustCredentialSigner(new PostgresIssuerSigningKeyStore(database), issuerId, keyDir);
      await expect(signer.sign((identity) => Buffer.from(`payload:${identity.issuerKeyId}`))).rejects.toThrow(/not found/i);
      const stillActive = (await new PostgresIssuerSigningKeyStore(database).describe(issuerId)).some((k) => k.status === 'active');
      expect(stillActive).toBe(true); // the DB row itself is untouched by a local-file failure
    } finally {
      await database.query('DELETE FROM trust_issuer_signing_key WHERE issuer_id = $1', [issuerId]);
    }
  });

  it('10. issuance fails closed when the database is unreachable', async () => {
    // A deliberately unreachable loopback port -- never touches the real configured database.
    // Deterministic and safe: always refuses the same way (ECONNREFUSED), no timing dependency.
    const unreachable = new PostgresDatabase('postgres://budcom_dev:wrong@127.0.0.1:59999/budcom_dev', 1);
    try {
      const signer = new PostgresBackedTrustCredentialSigner(new PostgresIssuerSigningKeyStore(unreachable), 'any-issuer', '.local/unused-keys');
      await expect(signer.sign((identity) => Buffer.from(`payload:${identity.issuerKeyId}`))).rejects.toThrow(/ECONNREFUSED/);
    } finally {
      await unreachable.close().catch(() => undefined);
    }
  });
});
