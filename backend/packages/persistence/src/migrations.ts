import type { Database } from './database.js';

export interface Migration { readonly version: number; readonly name: string; readonly sql: string }
export const migrations: readonly Migration[] = [{
  version: 1,
  name: 'migration_registry',
  sql: `CREATE TABLE IF NOT EXISTS trust_schema_migration (
    version INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
  )`,
}];

export async function runMigrations(database: Database): Promise<void> {
  await database.transaction(async (tx) => {
    await tx.query(migrations[0]!.sql);
    const applied = await tx.query<{ version: number }>('SELECT version FROM trust_schema_migration');
    const versions = new Set(applied.rows.map((row) => row.version));
    for (const migration of migrations) {
      if (versions.has(migration.version)) continue;
      await tx.query(migration.sql);
      await tx.query('INSERT INTO trust_schema_migration(version, name) VALUES ($1, $2)', [migration.version, migration.name]);
    }
  });
}
