#!/usr/bin/env -S node --experimental-strip-types
/**
 * VENTURE Trust -- REAL PostgreSQL-backed pilot operator provisioning CLI.
 *
 * Closes the gap `dev-provision.ts` and `CONTROLLED-PILOT-RUNBOOK.md` (section E) both document:
 * `create-business`/`register-device` there write to a local, gitignored JSON file
 * (`FileBackedAuthorityStore`), never to the real Postgres-backed stores Trust's own `main.ts` uses.
 * This script writes ONLY through the certified Postgres-backed application services and stores --
 * `CreateBusiness` + `PostgresBusinessBootstrapStore` (identical write/equivalence/rollback logic
 * already proven in `backend/test/postgres-authority-write-store.test.ts` and, against a real
 * database, `backend/test/live-postgres.integration.test.ts`) and `PostgresEnrollmentGrantStore`
 * (already used this way by `dev-provision.ts create-enrollment-grant`, which this supersedes for
 * real pilot use since that command is gated to dev/test runtime and this one is not).
 *
 * LOCAL OPERATOR TOOL, not a public API -- same trust boundary as `manage-signing-keys.ts`: there is
 * no HTTP route anywhere in this backend for creating a business, a membership, or an enrollment
 * grant (see `app.ts`, which only ever registers `GET /health`, the verification-keys route, the
 * authority-epoch route, and `POST /v1/trust/enrollment/consume`). Whoever runs this already holds
 * the same `VENTURE_TRUST_DATABASE_URL` Trust's own server process uses.
 *
 * NOT gated to development/test runtime (unlike `dev-provision.ts`): onboarding a real controlled-
 * pilot business is a legitimate production/pilot operational need, not dev/test data fabrication --
 * gating it the same way would make it impossible to ever onboard a real pilot business. Every
 * operation is otherwise as narrow as `dev-provision.ts`'s own commands: only non-secret identifiers/
 * status ever printed (the ONE necessary exception is the enrollment grant secret, printed exactly
 * once at issuance time -- see `create-enrollment-grant` below, identical to `dev-provision.ts`'s own
 * documented behavior for the same reason), explicit identifiers required for every command.
 *
 * WHO CAN CONSTRUCT OR OBTAIN AUTHORITY HERE: nobody, from this CLI alone. `--actor`/
 * `--verification-id`/`--business`/`--membership` are all bare references (string identifiers,
 * exactly like `dev-provision.ts`'s existing flags of the same names) -- never a boolean assertion
 * like `--verified=true`/`--trusted=true` that would manufacture authority merely from being passed
 * on a command line. The actual authority a Business/Membership/enrollment-grant carries comes
 * entirely from what `CreateBusiness`/`PostgresBusinessBootstrapStore`/`PostgresEnrollmentGrantStore`
 * themselves persist and enforce (the fixed initial membership scope `CreateBusiness` always creates,
 * the partial-unique-index-backed one-Business-per-intent invariant, the server-generated
 * high-entropy grant secret whose hash alone is stored) -- not from anything this CLI's own flags
 * assert. This script never registers a device and never issues a credential: the device's private
 * key is generated on-device (Android Keystore) and the device earns its own credential by
 * successfully calling the real, already-Postgres-backed `POST /v1/trust/enrollment/consume` with
 * that key's public half and the grant secret -- never the other way around.
 *
 * Usage:
 *   tsx services/trust/scripts/pilot-provision.ts create-business --actor <id> --verification-id <id> --name <name> --intent <id>
 *   tsx services/trust/scripts/pilot-provision.ts create-enrollment-grant --business <id> --actor <id> --membership <id> --scope cap1,cap2,... [--lifetime-ms <ms>]
 *   tsx services/trust/scripts/pilot-provision.ts grant-scope --membership <id> --scope cap1,cap2,...
 *   tsx services/trust/scripts/pilot-provision.ts status --business <id>
 */
import { parseArgs } from 'node:util';
import { randomBytes, randomUUID } from 'node:crypto';
import { CreateBusiness } from '../src/application/create-business.js';
import { PostgresBusinessBootstrapStore, MEMBERSHIP_COLUMNS, PostgresAuthorityMutationStore, toMembership } from '../src/persistence/postgres-authority-write-store.js';
import { createBusinessDeterministicIds } from '../src/persistence/file-backed-authority-store.js';
import { PostgresEnrollmentGrantStore } from '../src/persistence/postgres-enrollment-grant-store.js';
import { hashEnrollmentGrantSecret, validateEnrollmentGrantLifetime, type EnrollmentGrantId } from '../src/domain/enrollment.js';
import { AuthorityRevocationService } from '../src/application/revoke-authority.js';
import { AuthorityScope, identifier, type AuthorityCapability } from '../src/domain/authority.js';
import type { MembershipRow } from '../src/persistence/postgres-authority-write-store.js';
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { readTrustServiceConfig } from '../src/config.js';

async function main(): Promise<void> {
  const config = readTrustServiceConfig();
  const [command, ...rest] = process.argv.slice(2);
  const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
  try {
    switch (command) {
      case 'create-business': {
        const { values } = parseArgs({ args: rest, options: { actor: { type: 'string' }, 'verification-id': { type: 'string' }, name: { type: 'string' }, intent: { type: 'string' } } });
        if (!values.actor || !values['verification-id'] || !values.name || !values.intent) throw new Error('create-business requires --actor --verification-id --name --intent');
        const store = new PostgresBusinessBootstrapStore(database);
        const created = await new CreateBusiness(store, () => new Date(), createBusinessDeterministicIds(values.actor, values.intent)).execute({
          principal: { actorId: identifier(values.actor, 'ActorId'), verificationId: values['verification-id'], verifiedAt: new Date() },
          intentId: values.intent, authorityDisplayName: values.name,
        });
        console.log(JSON.stringify({
          businessId: created.businessId, membershipId: created.membership.membershipId,
          authorityScope: [...created.membership.authorityScope.capabilities], authorityEpoch: created.authorityEpoch,
        }, null, 2));
        break;
      }
      case 'create-enrollment-grant': {
        const { values } = parseArgs({ args: rest, options: { business: { type: 'string' }, actor: { type: 'string' }, membership: { type: 'string' }, scope: { type: 'string' }, 'lifetime-ms': { type: 'string' } } });
        if (!values.business || !values.actor || !values.membership || !values.scope) throw new Error('create-enrollment-grant requires --business --actor --membership --scope cap1,cap2,...');
        const lifetimeMs = Number(values['lifetime-ms'] ?? '900000');
        const issuedAt = new Date();
        const expiresAt = new Date(issuedAt.getTime() + lifetimeMs);
        validateEnrollmentGrantLifetime(issuedAt, expiresAt);
        const grantSecret = randomBytes(32).toString('base64url');
        const grant = await new PostgresEnrollmentGrantStore(database).create({
          grantId: identifier(randomUUID(), 'EnrollmentGrantId') as EnrollmentGrantId,
          businessId: identifier(values.business, 'BusinessId'), actorId: identifier(values.actor, 'ActorId'), membershipId: identifier(values.membership, 'MembershipId'),
          grantedDeviceScope: new AuthorityScope(values.scope.split(',').map((s) => s.trim()) as AuthorityCapability[]),
          grantSecretHash: hashEnrollmentGrantSecret(grantSecret), issuedAt, expiresAt,
        });
        // Printed EXACTLY ONCE, here, at issuance time -- never persisted in plaintext (only its hash
        // is), never recoverable from Trust afterward. Relaying it to the enrolling phone (QR code,
        // manual entry) is outside this CLI's scope -- identical contract to `dev-provision.ts`'s own
        // `create-enrollment-grant`.
        console.log(JSON.stringify({ grantId: grant.grantId, grantSecret, expiresAt: grant.expiresAt.toISOString(), grantedDeviceScope: [...grant.grantedDeviceScope.capabilities] }, null, 2));
        break;
      }
      case 'grant-scope': {
        const { values } = parseArgs({ args: rest, options: { membership: { type: 'string' }, scope: { type: 'string' } } });
        if (!values.membership || !values.scope) throw new Error('grant-scope requires --membership --scope cap1,cap2,...');
        const row = await database.query<MembershipRow>(`SELECT ${MEMBERSHIP_COLUMNS} FROM trust_business_membership WHERE membership_id = $1`, [values.membership]);
        if (row.rowCount === 0) throw new Error(`No membership found for id ${values.membership}`);
        const membership = toMembership(row.rows[0]!);
        const updated = await new AuthorityRevocationService(new PostgresAuthorityMutationStore(database)).changeScope(
          membership, new AuthorityScope(values.scope.split(',').map((s) => s.trim()) as AuthorityCapability[]),
        );
        console.log(JSON.stringify({ membershipId: updated.membershipId, authorityScope: [...updated.authorityScope.capabilities], authorityEpoch: updated.authorityEpoch.value }, null, 2));
        break;
      }
      case 'status': {
        const { values } = parseArgs({ args: rest, options: { business: { type: 'string' } } });
        if (!values.business) throw new Error('status requires --business <id>');
        const business = await database.query<{ status: string; authority_epoch: string; created_at: Date }>(
          'SELECT status, authority_epoch, created_at FROM trust_business_authority WHERE business_id = $1', [values.business],
        );
        const memberships = await database.query<{ membership_id: string; actor_id: string; status: string; authority_scope: string[]; authority_epoch: string }>(
          'SELECT membership_id, actor_id, status, authority_scope, authority_epoch FROM trust_business_membership WHERE business_id = $1', [values.business],
        );
        const grants = await database.query<{ grant_id: string; membership_id: string; expires_at: Date; consumed_at: Date | null; consumed_by_device_id: string | null }>(
          'SELECT grant_id, membership_id, expires_at, consumed_at, consumed_by_device_id FROM trust_enrollment_grant WHERE business_id = $1', [values.business],
        );
        console.log(JSON.stringify({
          businessId: values.business,
          business: business.rowCount === 0 ? null : { status: business.rows[0]!.status, authorityEpoch: Number(business.rows[0]!.authority_epoch), createdAt: business.rows[0]!.created_at.toISOString() },
          memberships: memberships.rows.map((m) => ({ membershipId: m.membership_id, actorId: m.actor_id, status: m.status, authorityScope: m.authority_scope, authorityEpoch: Number(m.authority_epoch) })),
          enrollmentGrants: grants.rows.map((g) => ({
            grantId: g.grant_id, membershipId: g.membership_id, expiresAt: g.expires_at.toISOString(),
            consumed: g.consumed_at !== null, consumedByDeviceId: g.consumed_by_device_id,
          })),
        }, null, 2));
        break;
      }
      default:
        console.error('Usage: pilot-provision.ts <create-business|create-enrollment-grant|grant-scope|status> [options]');
        process.exitCode = 1;
    }
  } finally {
    await database.close();
  }
}

await main();
