#!/usr/bin/env -S node --experimental-strip-types
/**
 * Runs Trust's real Postgres migrations (`packages/persistence/src/migrations.ts`) against
 * `VENTURE_TRUST_DATABASE_URL` without booting the full Trust HTTP server. `main.ts` already does
 * this on every boot (idempotent), so this script exists purely for orchestration tooling that needs
 * the schema present before creating rows (e.g. `pilot-provision.ts`, which does not migrate itself)
 * without starting a listening service. Never touches anything the real migrations don't already
 * touch -- no new logic, just an explicit entrypoint for the existing `runMigrations()`.
 *
 * Usage: tsx services/trust/scripts/migrate.ts
 */
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { runMigrations } from '../../../packages/persistence/src/migrations.js';
import { readTrustServiceConfig } from '../src/config.js';

const config = readTrustServiceConfig();
const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
try {
  await runMigrations(database);
  const applied = await database.query<{ version: number; name: string }>('SELECT version, name FROM trust_schema_migration ORDER BY version');
  console.log(JSON.stringify({ ok: true, appliedMigrations: applied.rows }, null, 2));
} finally {
  await database.close();
}
