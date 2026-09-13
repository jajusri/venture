#!/usr/bin/env -S node --experimental-strip-types
/**
 * VENTURE Trust -- DEV/TEST-ONLY provisioning CLI.
 *
 * Builds TEST business/device/credential identities by calling Trust's REAL application services
 * (`CreateBusiness`, `RegisterBusinessDevice`, `BusinessDeviceCredentialIssuer`,
 * `AuthorityRevocationService`) directly, in-process -- never over HTTP, and Trust exposes no HTTP
 * route for any of this (see `app.ts`: only `GET /health` and `GET .../verification-keys` exist).
 * This is a deliberate choice per the controlled-pilot mandate: "prefer direct local CLI/
 * application-layer invocation over adding a remotely exposed unauthenticated HTTP provisioning
 * API." There is no public "bootstrap anyone" endpoint anywhere in this backend.
 *
 * PRODUCTION GUARD (relay-authority-repair, 2026-08-29 -- hardened per Codex's audit of fcbc04c):
 * positive allowlist, fail closed. Runs ONLY when every runtime-environment signal that is actually
 * set (NODE_ENV, VENTURE_RUNTIME_ENV) normalizes to "development" or "test" -- not merely "is not
 * production". Three concrete weaknesses in the prior `=== 'production'` check are fixed by this:
 *   1. Case sensitivity: "Production"/"PRODUCTION" used to slip through a strict `===` check.
 *      Both signals are now trimmed and lowercased before comparison.
 *   2. VENTURE_RUNTIME_ENV could silently override a stricter NODE_ENV (e.g. a real deployment sets
 *      NODE_ENV=production, but a stray/leftover VENTURE_RUNTIME_ENV=development in the shell or an
 *      `.env` file would still let this tool run against production data). Every signal that is set
 *      must independently be in the allowlist -- none can override another into running.
 *   3. An unset environment used to default to "development" (i.e. silently allow). It now refuses:
 *      if neither variable is set at all, that is treated as unknown/ambiguous, not safe.
 * This is DEV/TEST tooling only -- production business/device/credential provisioning through this
 * exact shape of authority-establishment code is architecturally legitimate (these ARE the real
 * application services, not a bypass), but wiring a *real* production operator flow around them
 * (real user verification, real approval workflow, real key custody) is an open architecture
 * decision this package does not make. See the final report's PRODUCTION PROVISIONING BLOCKER.
 *
 * Persists to a local JSON file (`.local/trust-dev-state.json` by default, gitignored) via
 * `FileBackedAuthorityStore` -- see that file's own doc comment for why (no local PostgreSQL is
 * required to use this tool, and the real Postgres-backed stores in
 * `postgres-authority-write-store.ts` are not yet wired into this particular CLI; that is a small,
 * clearly-scoped follow-up once a real pilot Postgres is available).
 *
 * Usage:
 *   tsx services/trust/scripts/dev-provision.ts create-business --actor <id> --verification-id <id> --name <name> --intent <id>
 *   tsx services/trust/scripts/dev-provision.ts register-device --business <id> --actor <id> --membership <id> --device <id> [--device-key-path <path>]
 *   tsx services/trust/scripts/dev-provision.ts grant-scope --membership <id> --scope cap1,cap2,...
 *   tsx services/trust/scripts/dev-provision.ts issue-credential --business <id> --membership <id> --device <id> --device-key-path <path> --scope cap1,cap2,... --intent <id> [--lifetime-ms <ms>] [--out <path>]
 *   tsx services/trust/scripts/dev-provision.ts status --membership <id> [--business <id> --device <id> --device-key-version <n>]
 *   tsx services/trust/scripts/dev-provision.ts create-enrollment-grant --business <id> --actor <id> --membership <id> --scope cap1,cap2,... [--lifetime-ms <ms>]
 *
 * Every command prints only non-secret identifiers/status to stdout -- never private key material.
 *
 * `create-enrollment-grant` is the ONE exception to "never touches Postgres" for IDENTITY: it is the
 * LOCAL, dev/test-runtime-guarded operator path Gate 2B calls for -- the enrollment grant it creates
 * is consumed over the network by Trust's real `POST /v1/trust/enrollment/consume` (`app.ts`), which
 * is Postgres-backed only (`PostgresEnrollmentGrantStore`), so issuance must write to that SAME
 * store or the HTTP endpoint would never see it. Every other identity command here still uses
 * `FileBackedAuthorityStore` -- wiring real Postgres-backed business/membership provisioning into
 * this CLI too remains the pre-existing, separately-tracked PRODUCTION PROVISIONING BLOCKER this
 * file's own doc comment above already calls out (this command therefore only works against a
 * business/membership that ALREADY exists in Postgres, e.g. seeded by test setup or a future real
 * onboarding flow -- not by this CLI's own file-backed `create-business`).
 *
 * `issue-credential` is the analogous exception for the SIGNING KEY (round 6, corrected round 7):
 * it signs via `PostgresBackedTrustCredentialSigner`, which re-reads Postgres fresh at the moment of
 * signing for whichever key is currently active -- the SAME source of truth Trust's real server
 * process and `manage-signing-keys.ts` both use, and the SAME production signer class real Trust
 * uses (never a cached snapshot) -- rather than a standalone file-only signer, so a CLI-issued test
 * credential actually verifies against the real, live verification-keys endpoint instead of a
 * disconnected key nothing else knows about. Requires an already-active issuer key
 * (`manage-signing-keys.ts bootstrap` first, or Trust's own `main.ts` fresh-install auto-bootstrap).
 */
import { parseArgs } from 'node:util';
import { createPrivateKey, createPublicKey, createHash, generateKeyPairSync, randomBytes, randomUUID } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { CreateBusiness } from '../src/application/create-business.js';
import { RegisterBusinessDevice } from '../src/application/register-device.js';
import { BusinessDeviceCredentialIssuer } from '../src/application/issue-credential.js';
import { AuthorityRevocationService } from '../src/application/revoke-authority.js';
import { AuthorityScope, identifier, type AuthorityCapability } from '../src/domain/authority.js';
import { hashEnrollmentGrantSecret, validateEnrollmentGrantLifetime, type EnrollmentGrantId } from '../src/domain/enrollment.js';
import { FileBackedAuthorityStore, createBusinessDeterministicIds } from '../src/persistence/file-backed-authority-store.js';
import { InMemoryCredentialIssuanceStore } from '../src/persistence/in-memory-credential-store.js';
import { localSignerKeyExists } from '../src/persistence/local-signer.js';
import { PostgresBackedTrustCredentialSigner } from '../src/persistence/postgres-backed-signer.js';
import { PostgresIssuerSigningKeyStore } from '../src/persistence/postgres-issuer-signing-key-store.js';
import { PostgresEnrollmentGrantStore } from '../src/persistence/postgres-enrollment-grant-store.js';
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { readTrustServiceConfig } from '../src/config.js';

const ALLOWED_DEV_PROVISION_RUNTIME_ENVS = new Set(['development', 'test']);

/** Pure decision function -- unit-tested directly in `dev-provision-guard.test.ts` without needing
 * to spawn the CLI. See the module doc comment above for exactly what this fixes and why. */
export function isDevProvisionRuntimeAllowed(nodeEnv: string | undefined, ventureRuntimeEnv: string | undefined): boolean {
  const normalize = (value: string | undefined): string | undefined => value?.trim().toLowerCase();
  const signals = [normalize(nodeEnv), normalize(ventureRuntimeEnv)].filter((value): value is string => value !== undefined);
  return signals.length > 0 && signals.every((value) => ALLOWED_DEV_PROVISION_RUNTIME_ENVS.has(value));
}

function refuseUnlessDevOrTestRuntime(): void {
  if (!isDevProvisionRuntimeAllowed(process.env.NODE_ENV, process.env.VENTURE_RUNTIME_ENV)) {
    console.error(
      `REFUSED: dev-provision.ts is DEV/TEST-ONLY tooling. It runs only when every runtime-environment signal that is set ` +
      `(NODE_ENV, VENTURE_RUNTIME_ENV) is explicitly "development" or "test" -- an unset, unrecognized, or conflicting ` +
      `environment is refused by design (fail closed), never defaulted to allowed. ` +
      `NODE_ENV=${process.env.NODE_ENV ?? '<unset>'} VENTURE_RUNTIME_ENV=${process.env.VENTURE_RUNTIME_ENV ?? '<unset>'}`,
    );
    process.exit(1);
  }
}

function statePath(): string { return process.env.VENTURE_TRUST_DEV_STATE_PATH ?? '.local/trust-dev-state.json'; }

function loadOrCreateDeviceKey(keyPath: string): { privateKeyPem: string; publicKey: Buffer; fingerprint: string } {
  let privateKeyPem: string;
  if (existsSync(keyPath)) {
    privateKeyPem = readFileSync(keyPath, 'utf8');
  } else {
    const keyPair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    privateKeyPem = keyPair.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
    mkdirSync(dirname(keyPath), { recursive: true });
    writeFileSync(keyPath, privateKeyPem, { mode: 0o600 });
  }
  const publicKey = createPublicKey(createPrivateKey(privateKeyPem)).export({ type: 'spki', format: 'der' });
  const fingerprint = createHash('sha256').update(publicKey).digest('base64');
  return { privateKeyPem, publicKey, fingerprint };
}

async function main(): Promise<void> {
  refuseUnlessDevOrTestRuntime();
  const [command, ...rest] = process.argv.slice(2);
  const store = new FileBackedAuthorityStore(statePath());
  const config = readTrustServiceConfig({ ...process.env, VENTURE_TRUST_DATABASE_URL: process.env.VENTURE_TRUST_DATABASE_URL ?? 'unused-by-dev-provision' });

  switch (command) {
    case 'create-business': {
      const { values } = parseArgs({ args: rest, options: { actor: { type: 'string' }, 'verification-id': { type: 'string' }, name: { type: 'string' }, intent: { type: 'string' } } });
      if (!values.actor || !values['verification-id'] || !values.name || !values.intent) throw new Error('create-business requires --actor --verification-id --name --intent');
      const created = await new CreateBusiness(store, () => new Date(), createBusinessDeterministicIds(values.actor, values.intent)).execute({
        principal: { actorId: identifier(values.actor, 'ActorId'), verificationId: values['verification-id'], verifiedAt: new Date() },
        intentId: values.intent, authorityDisplayName: values.name,
      });
      console.log(JSON.stringify({ businessId: created.businessId, membershipId: created.membership.membershipId, authorityScope: [...created.membership.authorityScope.capabilities] }, null, 2));
      break;
    }
    case 'register-device': {
      const { values } = parseArgs({ args: rest, options: { business: { type: 'string' }, actor: { type: 'string' }, membership: { type: 'string' }, device: { type: 'string' }, 'device-key-path': { type: 'string' } } });
      if (!values.business || !values.actor || !values.membership || !values.device) throw new Error('register-device requires --business --actor --membership --device');
      const membership = await store.findMembershipById(values.membership);
      if (!membership) throw new Error(`No membership found for id ${values.membership} -- run create-business/grant-scope first`);
      const keyPath = values['device-key-path'] ?? `.local/device-${values.device}-key.pem`;
      const key = loadOrCreateDeviceKey(keyPath);
      const device = await new RegisterBusinessDevice(store).execute({
        principal: { actorId: identifier(values.actor, 'ActorId'), verificationId: 'dev-provisioning', verifiedAt: new Date() },
        membership, deviceId: identifier(values.device, 'DeviceId'), deviceKeyId: identifier(`${values.device}-key-1`, 'DeviceKeyId'),
        deviceKeyVersion: 1, publicKey: new Uint8Array(key.publicKey), publicKeyFingerprint: key.fingerprint,
      });
      console.log(JSON.stringify({ businessId: device.businessId, deviceId: device.deviceId, deviceKeyVersion: device.deviceKeyVersion, publicKeyFingerprint: device.publicKeyFingerprint, devicePrivateKeyPath: keyPath, status: device.status }, null, 2));
      break;
    }
    case 'grant-scope': {
      const { values } = parseArgs({ args: rest, options: { membership: { type: 'string' }, scope: { type: 'string' } } });
      if (!values.membership || !values.scope) throw new Error('grant-scope requires --membership --scope cap1,cap2,...');
      const membership = await store.findMembershipById(values.membership);
      if (!membership) throw new Error(`No membership found for id ${values.membership}`);
      const updated = await new AuthorityRevocationService(store).changeScope(membership, new AuthorityScope(values.scope.split(',').map((s) => s.trim()) as AuthorityCapability[]));
      console.log(JSON.stringify({ membershipId: updated.membershipId, authorityScope: [...updated.authorityScope.capabilities], authorityEpoch: updated.authorityEpoch.value }, null, 2));
      break;
    }
    case 'issue-credential': {
      const { values } = parseArgs({ args: rest, options: { business: { type: 'string' }, membership: { type: 'string' }, device: { type: 'string' }, 'device-key-path': { type: 'string' }, scope: { type: 'string' }, intent: { type: 'string' }, 'lifetime-ms': { type: 'string' }, out: { type: 'string' } } });
      if (!values.business || !values.membership || !values.device || !values['device-key-path'] || !values.scope || !values.intent) {
        throw new Error('issue-credential requires --business --membership --device --device-key-path --scope --intent');
      }
      const membership = await store.findMembershipById(values.membership);
      if (!membership) throw new Error(`No membership found for id ${values.membership}`);
      const device = await store.find(values.business, values.device, 1);
      if (!device) throw new Error(`No registered device found for business=${values.business} device=${values.device} keyVersion=1`);
      const signingDatabase = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
      let credential;
      try {
        // Fresh-per-issuance, same production signer real Trust uses -- no cached lifecycle
        // snapshot, no refresh step (see PostgresBackedTrustCredentialSigner's own doc comment).
        const signer = new PostgresBackedTrustCredentialSigner(new PostgresIssuerSigningKeyStore(signingDatabase), config.issuerId, config.issuerKeyDir);
        const issuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, Number(values['lifetime-ms'] ?? '3600000'));
        credential = await issuer.issue({
          business: { businessId: identifier(values.business, 'BusinessId'), status: 'active' }, membership, device,
          requestedScope: new AuthorityScope(values.scope.split(',').map((s) => s.trim()) as AuthorityCapability[]), intentId: values.intent,
        });
      } finally {
        await signingDatabase.close();
      }
      const wire = {
        credentialClaims: {
          credentialVersion: credential.claims.credentialVersion, credentialId: credential.claims.credentialId, businessId: credential.claims.businessId,
          actorId: credential.claims.actorId, membershipId: credential.claims.membershipId, deviceId: credential.claims.deviceId,
          deviceKeyId: credential.claims.deviceKeyId, deviceKeyVersion: credential.claims.deviceKeyVersion, devicePublicKeyFingerprint: credential.claims.devicePublicKeyFingerprint,
          authorityScope: [...credential.claims.authorityScope.capabilities], authorityEpoch: credential.claims.authorityEpoch,
          issuedAt: credential.claims.issuedAt.toISOString(), notBefore: credential.claims.notBefore.toISOString(), expiresAt: credential.claims.expiresAt.toISOString(),
          issuerId: credential.claims.issuerId, issuerKeyId: credential.claims.issuerKeyId,
        },
        credentialSignature: Buffer.from(credential.signature.signature).toString('base64'),
      };
      const json = JSON.stringify(wire, null, 2);
      if (values.out) { writeFileSync(values.out, json, { mode: 0o600 }); console.log(`Credential written to ${values.out}`); } else { console.log(json); }
      break;
    }
    case 'status': {
      const { values } = parseArgs({ args: rest, options: { membership: { type: 'string' }, business: { type: 'string' }, device: { type: 'string' }, 'device-key-version': { type: 'string' } } });
      const report: Record<string, unknown> = { issuerKeyPresent: localSignerKeyExists(config.issuerKeyPath), issuerId: config.issuerId };
      if (values.membership) report.membership = store.describeMembership(values.membership);
      if (values.business && values.device) report.device = store.describeDevice(values.business, values.device, Number(values['device-key-version'] ?? '1'));
      console.log(JSON.stringify(report, null, 2));
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
      const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
      try {
        const grant = await new PostgresEnrollmentGrantStore(database).create({
          grantId: identifier(randomUUID(), 'EnrollmentGrantId') as EnrollmentGrantId,
          businessId: identifier(values.business, 'BusinessId'), actorId: identifier(values.actor, 'ActorId'), membershipId: identifier(values.membership, 'MembershipId'),
          grantedDeviceScope: new AuthorityScope(values.scope.split(',').map((s) => s.trim()) as AuthorityCapability[]),
          grantSecretHash: hashEnrollmentGrantSecret(grantSecret), issuedAt, expiresAt,
        });
        // The secret is printed EXACTLY ONCE, here, at issuance time -- it is never persisted in
        // plaintext (only its hash is) and can never be recovered from Trust afterward. Transmitting
        // it to the enrolling device (QR code, manual entry, etc.) is outside this CLI's scope.
        console.log(JSON.stringify({ grantId: grant.grantId, grantSecret, expiresAt: grant.expiresAt.toISOString(), grantedDeviceScope: [...grant.grantedDeviceScope.capabilities] }, null, 2));
      } finally {
        await database.close();
      }
      break;
    }
    default:
      console.error('Usage: dev-provision.ts <create-business|register-device|grant-scope|issue-credential|status|create-enrollment-grant> [options]');
      process.exit(1);
  }
}

// Only run the CLI when this file is executed directly (`tsx dev-provision.ts ...`) -- guarded so
// `isDevProvisionRuntimeAllowed` can be unit-tested by importing this module without also invoking
// `main()` (which reads real argv and calls `process.exit`).
const isMainModule = process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMainModule) await main();
