#!/usr/bin/env -S node --experimental-strip-types
/**
 * BUDCOM Trust -- DEV/TEST-ONLY provisioning CLI.
 *
 * Builds TEST business/device/credential identities by calling Trust's REAL application services
 * (`CreateBusiness`, `RegisterBusinessDevice`, `BusinessDeviceCredentialIssuer`,
 * `AuthorityRevocationService`) directly, in-process -- never over HTTP, and Trust exposes no HTTP
 * route for any of this (see `app.ts`: only `GET /health` and `GET .../verification-keys` exist).
 * This is a deliberate choice per the controlled-pilot mandate: "prefer direct local CLI/
 * application-layer invocation over adding a remotely exposed unauthenticated HTTP provisioning
 * API." There is no public "bootstrap anyone" endpoint anywhere in this backend.
 *
 * PRODUCTION GUARD: refuses to run at all if BUDCOM_RUNTIME_ENV or NODE_ENV is "production". This
 * is DEV/TEST tooling only -- production business/device/credential provisioning through this exact
 * shape of authority-establishment code is architecturally legitimate (these ARE the real
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
 *
 * Every command prints only non-secret identifiers/status to stdout -- never private key material.
 */
import { parseArgs } from 'node:util';
import { createPrivateKey, createPublicKey, createHash, generateKeyPairSync } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { CreateBusiness } from '../src/application/create-business.js';
import { RegisterBusinessDevice } from '../src/application/register-device.js';
import { BusinessDeviceCredentialIssuer } from '../src/application/issue-credential.js';
import { AuthorityRevocationService } from '../src/application/revoke-authority.js';
import { AuthorityScope, identifier, type AuthorityCapability } from '../src/domain/authority.js';
import { FileBackedAuthorityStore, createBusinessDeterministicIds } from '../src/persistence/file-backed-authority-store.js';
import { InMemoryCredentialIssuanceStore } from '../src/persistence/in-memory-credential-store.js';
import { LocalFileTrustCredentialSigner, localSignerKeyExists } from '../src/persistence/local-signer.js';
import { readTrustServiceConfig } from '../src/config.js';

function refuseInProduction(): void {
  const runtimeEnv = process.env.BUDCOM_RUNTIME_ENV ?? process.env.NODE_ENV ?? 'development';
  if (runtimeEnv === 'production') {
    console.error('REFUSED: dev-provision.ts is DEV/TEST-ONLY tooling and will not run with BUDCOM_RUNTIME_ENV=production or NODE_ENV=production.');
    process.exit(1);
  }
}

function statePath(): string { return process.env.BUDCOM_TRUST_DEV_STATE_PATH ?? '.local/trust-dev-state.json'; }

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
  refuseInProduction();
  const [command, ...rest] = process.argv.slice(2);
  const store = new FileBackedAuthorityStore(statePath());
  const config = readTrustServiceConfig({ ...process.env, BUDCOM_TRUST_DATABASE_URL: process.env.BUDCOM_TRUST_DATABASE_URL ?? 'unused-by-dev-provision' });

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
      const signer = new LocalFileTrustCredentialSigner({ keyPath: config.issuerKeyPath, issuerId: config.issuerId, issuerKeyId: config.issuerKeyId });
      const issuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, Number(values['lifetime-ms'] ?? '3600000'));
      const credential = await issuer.issue({
        business: { businessId: identifier(values.business, 'BusinessId'), status: 'active' }, membership, device,
        requestedScope: new AuthorityScope(values.scope.split(',').map((s) => s.trim()) as AuthorityCapability[]), intentId: values.intent,
      });
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
    default:
      console.error('Usage: dev-provision.ts <create-business|register-device|grant-scope|issue-credential|status> [options]');
      process.exit(1);
  }
}

await main();
