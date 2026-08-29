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
}, {
  version: 3,
  name: 'relay_durable_mailbox',
  sql: `CREATE TABLE relay_mailbox_checkpoint (
    recipient_business_id TEXT NOT NULL, mailbox_id TEXT NOT NULL, next_sequence BIGINT NOT NULL,
    PRIMARY KEY (recipient_business_id, mailbox_id)
  );
  CREATE TABLE relay_envelope (
    envelope_id TEXT PRIMARY KEY, idempotency_key TEXT NOT NULL, protocol_version INTEGER NOT NULL,
    object_type TEXT NOT NULL, object_id TEXT NOT NULL, object_version BIGINT NOT NULL,
    sender_business_id TEXT NOT NULL, sender_actor_id TEXT NOT NULL, sender_device_id TEXT NOT NULL,
    recipient_business_id TEXT NOT NULL, mailbox_id TEXT NOT NULL, authenticated_envelope BYTEA NOT NULL,
    acceptance_id TEXT NOT NULL UNIQUE, accepted_at TIMESTAMPTZ NOT NULL,
    UNIQUE (sender_business_id, idempotency_key)
  );
  CREATE INDEX relay_envelope_sender_created_idx ON relay_envelope(sender_business_id, accepted_at DESC);
  CREATE TABLE relay_mailbox_entry (
    recipient_business_id TEXT NOT NULL, mailbox_id TEXT NOT NULL, mailbox_sequence BIGINT NOT NULL,
    envelope_id TEXT NOT NULL UNIQUE REFERENCES relay_envelope(envelope_id), status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, acknowledged_at TIMESTAMPTZ,
    PRIMARY KEY (recipient_business_id, mailbox_id, mailbox_sequence)
  );
  CREATE INDEX relay_mailbox_status_cursor_idx ON relay_mailbox_entry(recipient_business_id, mailbox_id, status, mailbox_sequence);
  CREATE TABLE relay_delivery_attempt (
    recipient_business_id TEXT NOT NULL, mailbox_id TEXT NOT NULL, envelope_id TEXT NOT NULL REFERENCES relay_envelope(envelope_id),
    attempt_number INTEGER NOT NULL, attempted_at TIMESTAMPTZ NOT NULL, outcome TEXT NOT NULL,
    PRIMARY KEY (recipient_business_id, mailbox_id, envelope_id, attempt_number)
  );
  CREATE TABLE relay_delivery_acknowledgement (
    envelope_id TEXT PRIMARY KEY REFERENCES relay_envelope(envelope_id), recipient_business_id TEXT NOT NULL,
    recipient_device_id TEXT NOT NULL, received_at TIMESTAMPTZ NOT NULL, recorded_at TIMESTAMPTZ NOT NULL
  );`,
}, {
  version: 4,
  name: 'relay_acceptance_evidence',
  sql: `ALTER TABLE relay_envelope
    ADD COLUMN relay_id TEXT NOT NULL,
    ADD COLUMN acceptance_evidence_profile TEXT NOT NULL,
    ADD COLUMN acceptance_evidence BYTEA NOT NULL;`,
}, {
  version: 5,
  name: 'relay_authenticated_commercial_content',
  sql: `ALTER TABLE relay_envelope
    ADD COLUMN commercial_content TEXT,
    ADD COLUMN commercial_content_type TEXT,
    ADD COLUMN commercial_content_version INTEGER;`,
}, {
  version: 6,
  name: 'relay_authenticated_request_replay_guard',
  // Small, additive, non-destructive (relay-authority-repair, 2026-08-29): durable replay
  // protection for authenticated Mailbox Fetch / Acknowledgement requests (Codex STOP 1). Scoped to
  // (business_id, device_id) so one device's nonce space can never collide with another's; bounded
  // by expires_at (PostgresRelayReplayGuard deletes its own partition's expired rows on every
  // consume() call -- no separate background job needed at this scale) with a supporting index for
  // that cleanup. No existing table/column is altered or removed.
  sql: `CREATE TABLE relay_authenticated_request_nonce (
    business_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    request_id TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (business_id, device_id, request_id)
  );
  CREATE INDEX relay_request_nonce_expiry_idx ON relay_authenticated_request_nonce(expires_at);`,
}, {
  version: 7,
  name: 'trust_enrollment_grant',
  // Small, additive (operational-mobile-path, 2026-08-29): the one-time bootstrap primitive that
  // lets a legitimate Android device earn Trust authority over the network for the first time (see
  // `domain/enrollment.ts`'s own doc comment). `grant_secret_hash` stores only a SHA-256 digest of
  // the high-entropy grant secret -- the raw secret is never persisted anywhere. No existing table
  // or column is altered or removed.
  sql: `CREATE TABLE trust_enrollment_grant (
    grant_id TEXT PRIMARY KEY,
    business_id TEXT NOT NULL REFERENCES trust_business_authority(business_id),
    actor_id TEXT NOT NULL,
    membership_id TEXT NOT NULL REFERENCES trust_business_membership(membership_id),
    granted_device_scope TEXT[] NOT NULL,
    grant_secret_hash TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    consumed_by_device_id TEXT
  );
  CREATE INDEX trust_enrollment_grant_membership_idx ON trust_enrollment_grant(membership_id);
  CREATE INDEX trust_enrollment_grant_expiry_idx ON trust_enrollment_grant(expires_at) WHERE consumed_at IS NULL;`,
}, {
  version: 8,
  name: 'trust_issuer_signing_key',
  // Small, additive (operational-mobile-path round 6, 2026-08-29): server-authoritative issuer
  // signing-key LIFECYCLE metadata only -- never the private key material itself, which stays in a
  // local file per key (see `persistence/local-signer.ts`; unchanged pattern, no HSM/KMS available
  // in this environment). This table is what makes key status genuinely server-authoritative
  // (round 6 Gate 1 answer 6): Postgres is the sole source of truth for "which key is currently
  // active/retired/revoked," the local file is merely storage for that key's own immutable bytes.
  //
  // The partial unique index below is the entire enforcement mechanism for "exactly one active key
  // per issuer" (key-lifecycle invariant 1) -- a second concurrent attempt to mark a row 'active'
  // for the same issuer_id fails atomically at the database level, not via application-level
  // locking alone. No existing table or column is altered or removed.
  sql: `CREATE TABLE trust_issuer_signing_key (
    issuer_id TEXT NOT NULL,
    key_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('active', 'retired', 'revoked')),
    profile TEXT NOT NULL,
    public_key_pem TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    PRIMARY KEY (issuer_id, key_id)
  );
  CREATE UNIQUE INDEX trust_issuer_signing_key_one_active_idx ON trust_issuer_signing_key(issuer_id) WHERE status = 'active';`,
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
