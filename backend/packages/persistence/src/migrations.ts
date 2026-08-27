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
}, {
  version: 2,
  name: 'trust_authority_state',
  sql: `CREATE TABLE trust_business_authority (
    business_id TEXT PRIMARY KEY, status TEXT NOT NULL, authority_epoch BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, modified_at TIMESTAMPTZ NOT NULL
  );
  CREATE TABLE trust_business_membership (
    membership_id TEXT PRIMARY KEY, business_id TEXT NOT NULL REFERENCES trust_business_authority(business_id),
    actor_id TEXT NOT NULL, status TEXT NOT NULL, authority_scope TEXT[] NOT NULL, authority_epoch BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    UNIQUE (business_id, actor_id)
  );
  CREATE INDEX trust_membership_business_status_idx ON trust_business_membership(business_id, status, actor_id);
  CREATE INDEX trust_membership_epoch_idx ON trust_business_membership(business_id, authority_epoch);
  CREATE TABLE trust_registered_device (
    business_id TEXT NOT NULL REFERENCES trust_business_authority(business_id), actor_id TEXT NOT NULL,
    membership_id TEXT NOT NULL REFERENCES trust_business_membership(membership_id), device_id TEXT NOT NULL,
    device_key_id TEXT NOT NULL, device_key_version INTEGER NOT NULL CHECK (device_key_version > 0),
    public_key BYTEA NOT NULL, public_key_fingerprint TEXT NOT NULL, status TEXT NOT NULL,
    authority_epoch BIGINT NOT NULL, created_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ,
    PRIMARY KEY (business_id, device_id, device_key_version), UNIQUE (business_id, device_key_id)
  );
  CREATE INDEX trust_device_business_status_idx ON trust_registered_device(business_id, status, device_id);
  CREATE INDEX trust_device_membership_idx ON trust_registered_device(membership_id, status);
  CREATE INDEX trust_device_epoch_idx ON trust_registered_device(business_id, authority_epoch);`,
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
