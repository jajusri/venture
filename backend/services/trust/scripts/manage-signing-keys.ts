#!/usr/bin/env -S node --experimental-strip-types
/**
 * VENTURE Trust -- issuer signing-key lifecycle operator CLI (round 6, key-lifecycle Gate 2).
 *
 * LOCAL OPERATOR TOOL, not a public API -- there is no HTTP route anywhere in this backend for
 * rotating, retiring, revoking, or activating a signing key (key-lifecycle invariant 8). Whoever
 * runs this already holds the same Postgres connection string Trust's own server process uses
 * (`VENTURE_TRUST_DATABASE_URL`), the same trust boundary as any other database administration tool.
 *
 * Unlike `dev-provision.ts`, this script is NOT gated to development/test runtime: key rotation and
 * revocation are legitimate PRODUCTION/PILOT operational needs (e.g. responding to a real
 * compromise), not dev/test data fabrication -- gating them the same way `dev-provision.ts` gates
 * "create a fake business" would make it impossible to ever revoke a real compromised key in a real
 * pilot deployment. Every operation is otherwise as narrow as `dev-provision.ts`'s own commands:
 * only non-secret identifiers/status ever printed, explicit key identifiers required for every
 * command (no "revoke everything" default), operating directly against the SAME
 * `PostgresIssuerSigningKeyStore`/`ManagedSigningKeyRegistry` Trust's own server process uses --
 * never a second, parallel authority mechanism.
 *
 * Usage:
 *   tsx services/trust/scripts/manage-signing-keys.ts list
 *   tsx services/trust/scripts/manage-signing-keys.ts bootstrap --key-id <id>
 *   tsx services/trust/scripts/manage-signing-keys.ts rotate --new-key-id <id>
 *   tsx services/trust/scripts/manage-signing-keys.ts retire --key-id <id>
 *   tsx services/trust/scripts/manage-signing-keys.ts revoke --key-id <id>
 */
import { parseArgs } from 'node:util';
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { ManagedSigningKeyRegistry } from '../src/persistence/managed-signing-key-registry.js';
import { PostgresIssuerSigningKeyStore } from '../src/persistence/postgres-issuer-signing-key-store.js';
import { readTrustServiceConfig } from '../src/config.js';

const PROFILE = 'P256-SHA256-v1';

async function main(): Promise<void> {
  const config = readTrustServiceConfig();
  const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
  try {
    const store = new PostgresIssuerSigningKeyStore(database);
    const registry = new ManagedSigningKeyRegistry(store, config.issuerId, config.issuerKeyDir);
    const [command, ...rest] = process.argv.slice(2);

    switch (command) {
      case 'list':
      case 'status': {
        const keys = await store.describe(config.issuerId);
        console.log(JSON.stringify({
          issuerId: config.issuerId,
          keys: keys.map((key) => ({
            keyId: key.keyId, status: key.status, profile: key.profile,
            createdAt: key.createdAt.toISOString(), activatedAt: key.activatedAt?.toISOString() ?? null,
            retiredAt: key.retiredAt?.toISOString() ?? null, revokedAt: key.revokedAt?.toISOString() ?? null,
          })),
        }, null, 2));
        break;
      }
      case 'bootstrap': {
        const { values } = parseArgs({ args: rest, options: { 'key-id': { type: 'string' } } });
        if (!values['key-id']) throw new Error('bootstrap requires --key-id <id>');
        await registry.bootstrap(values['key-id'], PROFILE, new Date());
        console.log(JSON.stringify({ issuerId: config.issuerId, activatedKeyId: values['key-id'] }, null, 2));
        break;
      }
      case 'rotate': {
        const { values } = parseArgs({ args: rest, options: { 'new-key-id': { type: 'string' } } });
        if (!values['new-key-id']) throw new Error('rotate requires --new-key-id <id>');
        const result = await registry.rotate(values['new-key-id'], PROFILE, new Date());
        console.log(JSON.stringify({ issuerId: config.issuerId, retiredKeyId: result.retiredKeyId, activatedKeyId: values['new-key-id'] }, null, 2));
        break;
      }
      case 'retire': {
        const { values } = parseArgs({ args: rest, options: { 'key-id': { type: 'string' } } });
        if (!values['key-id']) throw new Error('retire requires --key-id <id>');
        await registry.retire(values['key-id'], new Date());
        console.log(JSON.stringify({ issuerId: config.issuerId, retiredKeyId: values['key-id'] }, null, 2));
        break;
      }
      case 'revoke': {
        const { values } = parseArgs({ args: rest, options: { 'key-id': { type: 'string' } } });
        if (!values['key-id']) throw new Error('revoke requires --key-id <id>');
        await registry.revoke(values['key-id'], new Date());
        console.log(JSON.stringify({ issuerId: config.issuerId, revokedKeyId: values['key-id'] }, null, 2));
        break;
      }
      default:
        console.error('Usage: manage-signing-keys.ts <list|status|bootstrap|rotate|retire|revoke> [options]');
        process.exitCode = 1;
    }
  } finally {
    await database.close();
  }
}

await main();
