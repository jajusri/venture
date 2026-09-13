export const STORAGE_SCHEMA_VERSION = 13;

export const MIGRATION_001 = `
CREATE TABLE IF NOT EXISTS schema_migrations (
  version INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS storage_meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ledgers (
  company_id TEXT NOT NULL,
  ledger_id TEXT NOT NULL,
  name TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  alias TEXT,
  parent_group TEXT,
  status TEXT NOT NULL,
  balance_nature TEXT NOT NULL,
  opening_balance_json TEXT,
  closing_balance_json TEXT,
  guid TEXT,
  alter_id TEXT,
  reserved_name TEXT,
  mailing_json TEXT,
  contact_json TEXT,
  gst_json TEXT,
  metadata_json TEXT,
  content_fingerprint TEXT NOT NULL,
  is_deleted INTEGER NOT NULL DEFAULT 0,
  synced_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (company_id, ledger_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_ledgers_company_guid
  ON ledgers(company_id, guid) WHERE guid IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ledgers_company_normalized_name
  ON ledgers(company_id, normalized_name);

CREATE INDEX IF NOT EXISTS idx_ledgers_company_alias
  ON ledgers(company_id, alias);

CREATE INDEX IF NOT EXISTS idx_ledgers_company_parent
  ON ledgers(company_id, parent_group);

CREATE INDEX IF NOT EXISTS idx_ledgers_company_status
  ON ledgers(company_id, status);

CREATE INDEX IF NOT EXISTS idx_ledgers_company_alter_id
  ON ledgers(company_id, alter_id);

CREATE INDEX IF NOT EXISTS idx_ledgers_company_updated_at
  ON ledgers(company_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ledgers_company_gst
  ON ledgers(company_id, gst_json) WHERE gst_json IS NOT NULL;

CREATE TABLE IF NOT EXISTS sync_runs (
  sync_run_id TEXT PRIMARY KEY,
  company_id TEXT NOT NULL,
  sync_type TEXT NOT NULL,
  status TEXT NOT NULL,
  started_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  completed_at TEXT,
  total_expected INTEGER,
  processed INTEGER NOT NULL DEFAULT 0,
  inserted INTEGER NOT NULL DEFAULT 0,
  updated_count INTEGER NOT NULL DEFAULT 0,
  skipped INTEGER NOT NULL DEFAULT 0,
  failed INTEGER NOT NULL DEFAULT 0,
  last_processed_id TEXT,
  retry_count INTEGER NOT NULL DEFAULT 0,
  cancel_requested INTEGER NOT NULL DEFAULT 0,
  failure_code TEXT,
  failure_summary TEXT,
  connector_version TEXT NOT NULL,
  schema_version TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sync_runs_company_status
  ON sync_runs(company_id, status);

CREATE INDEX IF NOT EXISTS idx_sync_runs_company_started
  ON sync_runs(company_id, started_at DESC);

CREATE TABLE IF NOT EXISTS json_migration_runs (
  company_id TEXT PRIMARY KEY,
  status TEXT NOT NULL,
  source_path TEXT NOT NULL,
  backup_path TEXT,
  imported_count INTEGER NOT NULL DEFAULT 0,
  started_at TEXT NOT NULL,
  completed_at TEXT,
  error_message TEXT
);
`;

export const MIGRATION_002 = `
ALTER TABLE sync_runs ADD COLUMN resource_kind TEXT NOT NULL DEFAULT 'ledgers';

CREATE INDEX IF NOT EXISTS idx_sync_runs_company_resource_status
  ON sync_runs(company_id, resource_kind, status);

CREATE TABLE IF NOT EXISTS stock_items (
  company_id TEXT NOT NULL,
  stock_item_id TEXT NOT NULL,
  name TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  parent_group TEXT,
  category TEXT,
  base_unit TEXT,
  data_quality TEXT NOT NULL DEFAULT 'complete',
  opening_balance_json TEXT,
  closing_balance_json TEXT,
  hsn_code TEXT,
  gst_rate TEXT,
  guid TEXT,
  alter_id TEXT,
  alias TEXT,
  part_number TEXT,
  status TEXT NOT NULL DEFAULT 'active',
  source_system TEXT NOT NULL DEFAULT 'tally',
  metadata_json TEXT,
  content_fingerprint TEXT NOT NULL,
  is_deleted INTEGER NOT NULL DEFAULT 0,
  synced_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (company_id, stock_item_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_items_company_guid
  ON stock_items(company_id, guid) WHERE guid IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_items_company_alter_id
  ON stock_items(company_id, alter_id) WHERE alter_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_items_company_normalized_name
  ON stock_items(company_id, normalized_name);

CREATE INDEX IF NOT EXISTS idx_stock_items_company_parent
  ON stock_items(company_id, parent_group);

CREATE INDEX IF NOT EXISTS idx_stock_items_company_category
  ON stock_items(company_id, category);

CREATE INDEX IF NOT EXISTS idx_stock_items_company_base_unit
  ON stock_items(company_id, base_unit);

CREATE INDEX IF NOT EXISTS idx_stock_items_company_data_quality
  ON stock_items(company_id, data_quality);

CREATE INDEX IF NOT EXISTS idx_stock_items_company_updated_at
  ON stock_items(company_id, updated_at);
`;

/** Adds stock_items columns missing from partial pre-release schemas. */
export const STOCK_ITEM_COLUMN_UPGRADES: ReadonlyArray<{ readonly name: string; readonly ddl: string }> = [
  { name: 'guid', ddl: 'ALTER TABLE stock_items ADD COLUMN guid TEXT' },
  { name: 'alter_id', ddl: 'ALTER TABLE stock_items ADD COLUMN alter_id TEXT' },
  { name: 'alias', ddl: 'ALTER TABLE stock_items ADD COLUMN alias TEXT' },
  { name: 'part_number', ddl: 'ALTER TABLE stock_items ADD COLUMN part_number TEXT' },
  { name: 'status', ddl: "ALTER TABLE stock_items ADD COLUMN status TEXT NOT NULL DEFAULT 'active'" },
  { name: 'source_system', ddl: "ALTER TABLE stock_items ADD COLUMN source_system TEXT NOT NULL DEFAULT 'tally'" },
  { name: 'data_quality', ddl: "ALTER TABLE stock_items ADD COLUMN data_quality TEXT NOT NULL DEFAULT 'complete'" },
];

export const MIGRATION_003 = `
CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_items_company_guid
  ON stock_items(company_id, guid) WHERE guid IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_items_company_alter_id
  ON stock_items(company_id, alter_id) WHERE alter_id IS NOT NULL;
`;

/** TD-006: sync run retry lineage (predecessor link for interrupted-run retries). */
export const MIGRATION_004 = `
ALTER TABLE sync_runs ADD COLUMN predecessor_sync_run_id TEXT;

CREATE INDEX IF NOT EXISTS idx_sync_runs_predecessor
  ON sync_runs(predecessor_sync_run_id) WHERE predecessor_sync_run_id IS NOT NULL;
`;

/** Ledger GUID-first identity metadata columns. */
export const MIGRATION_005 = `
CREATE INDEX IF NOT EXISTS idx_ledgers_company_data_quality
  ON ledgers(company_id, data_quality);
`;

export const LEDGER_COLUMN_UPGRADES: ReadonlyArray<{ readonly name: string; readonly ddl: string }> = [
  { name: 'master_id', ddl: 'ALTER TABLE ledgers ADD COLUMN master_id TEXT' },
  { name: 'identity_source', ddl: "ALTER TABLE ledgers ADD COLUMN identity_source TEXT NOT NULL DEFAULT 'name'" },
  { name: 'data_quality', ddl: "ALTER TABLE ledgers ADD COLUMN data_quality TEXT NOT NULL DEFAULT 'complete'" },
  { name: 'is_bill_wise_on', ddl: 'ALTER TABLE ledgers ADD COLUMN is_bill_wise_on INTEGER' },
];

/** Offline/inbound XML import attempt history (Reliability order item 6). */
export const MIGRATION_006 = `
CREATE TABLE IF NOT EXISTS xml_import_attempts (
  import_attempt_id TEXT PRIMARY KEY,
  company_id TEXT,
  resource_kind TEXT NOT NULL,
  source_type TEXT NOT NULL,
  source_identifier TEXT,
  content_fingerprint TEXT NOT NULL,
  byte_size INTEGER NOT NULL,
  reservation_status TEXT NOT NULL DEFAULT 'released',
  validation_status TEXT NOT NULL,
  persistence_status TEXT NOT NULL,
  duplicate_status TEXT NOT NULL,
  error_code TEXT,
  parser_version TEXT NOT NULL,
  connector_version TEXT NOT NULL,
  received_at TEXT NOT NULL,
  completed_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_xml_import_attempts_company_resource
  ON xml_import_attempts(company_id, resource_kind, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_xml_import_attempts_fingerprint
  ON xml_import_attempts(content_fingerprint, resource_kind, validation_status, persistence_status);
`;

/** Adds reservation_status and atomic duplicate index to v6 databases created before column existed. */
export const MIGRATION_007 = `
CREATE UNIQUE INDEX IF NOT EXISTS idx_xml_import_attempts_reservation
  ON xml_import_attempts(
    COALESCE(company_id, '__none__'),
    resource_kind,
    content_fingerprint
  )
  WHERE reservation_status IN ('active', 'completed');
`;

/**
 * Replaces COALESCE sentinel index with collision-proof dual partial indexes.
 * NULL company scope and non-NULL company scope are indexed separately.
 */
export const MIGRATION_008 = `
DROP INDEX IF EXISTS idx_xml_import_attempts_reservation;

CREATE UNIQUE INDEX IF NOT EXISTS idx_xml_import_attempts_reservation_no_company
  ON xml_import_attempts(resource_kind, content_fingerprint)
  WHERE company_id IS NULL AND reservation_status IN ('active', 'completed');

CREATE UNIQUE INDEX IF NOT EXISTS idx_xml_import_attempts_reservation_scoped
  ON xml_import_attempts(company_id, resource_kind, content_fingerprint)
  WHERE company_id IS NOT NULL AND reservation_status IN ('active', 'completed');
`;

/** Immutable Voucher snapshots with atomic per-company active-pointer promotion. */
export const MIGRATION_009 = `
CREATE TABLE voucher_snapshots (
  company_id TEXT NOT NULL,
  snapshot_id TEXT NOT NULL,
  date_from TEXT NOT NULL,
  date_to TEXT NOT NULL,
  status TEXT NOT NULL CHECK (
    status IN ('PENDING', 'WRITING', 'VALIDATED', 'PROMOTED', 'ARCHIVED', 'FAILED')
  ),
  created_at TEXT NOT NULL,
  validated_at TEXT,
  promoted_at TEXT,
  failed_at TEXT,
  failure_reason TEXT,
  voucher_count INTEGER NOT NULL DEFAULT 0 CHECK (voucher_count >= 0),
  PRIMARY KEY (company_id, snapshot_id),
  UNIQUE (snapshot_id)
);

CREATE TABLE voucher_active_snapshots (
  company_id TEXT PRIMARY KEY,
  snapshot_id TEXT NOT NULL,
  promoted_at TEXT NOT NULL,
  FOREIGN KEY (company_id, snapshot_id)
    REFERENCES voucher_snapshots(company_id, snapshot_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE voucher_headers (
  company_id TEXT NOT NULL,
  snapshot_id TEXT NOT NULL,
  voucher_id TEXT NOT NULL,
  identity_version TEXT NOT NULL,
  guid TEXT,
  master_id TEXT,
  alter_id TEXT,
  voucher_key TEXT,
  voucher_retain_key TEXT,
  voucher_date TEXT NOT NULL,
  effective_date TEXT,
  voucher_type TEXT NOT NULL,
  voucher_number TEXT,
  reference_number TEXT,
  narration TEXT,
  narration_preview TEXT,
  party_name TEXT,
  amount TEXT,
  amount_side TEXT CHECK (amount_side IN ('debit', 'credit') OR amount_side IS NULL),
  amount_comparable INTEGER NOT NULL CHECK (amount_comparable IN (0, 1)),
  voucher_status TEXT NOT NULL CHECK (voucher_status IN ('active', 'cancelled')),
  data_quality TEXT NOT NULL CHECK (data_quality IN ('complete', 'incomplete')),
  ledger_entry_count INTEGER NOT NULL CHECK (ledger_entry_count >= 0),
  inventory_entry_count INTEGER NOT NULL CHECK (inventory_entry_count >= 0),
  allocation_count INTEGER NOT NULL CHECK (allocation_count >= 0),
  voucher_json TEXT NOT NULL,
  PRIMARY KEY (company_id, snapshot_id, voucher_id),
  FOREIGN KEY (company_id, snapshot_id)
    REFERENCES voucher_snapshots(company_id, snapshot_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE voucher_ledger_entries (
  company_id TEXT NOT NULL,
  snapshot_id TEXT NOT NULL,
  voucher_id TEXT NOT NULL,
  line_number INTEGER NOT NULL CHECK (line_number > 0),
  ledger_name TEXT NOT NULL,
  amount TEXT NOT NULL,
  amount_side TEXT CHECK (amount_side IN ('debit', 'credit') OR amount_side IS NULL),
  is_deemed_positive INTEGER CHECK (is_deemed_positive IN (0, 1) OR is_deemed_positive IS NULL),
  reference_type TEXT,
  reference_name TEXT,
  allocation_count INTEGER NOT NULL CHECK (allocation_count >= 0),
  PRIMARY KEY (company_id, snapshot_id, voucher_id, line_number),
  FOREIGN KEY (company_id, snapshot_id, voucher_id)
    REFERENCES voucher_headers(company_id, snapshot_id, voucher_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE voucher_inventory_entries (
  company_id TEXT NOT NULL,
  snapshot_id TEXT NOT NULL,
  voucher_id TEXT NOT NULL,
  line_number INTEGER NOT NULL CHECK (line_number > 0),
  item_name TEXT NOT NULL,
  quantity TEXT,
  actual_quantity TEXT,
  billed_quantity TEXT,
  unit TEXT,
  rate TEXT,
  amount TEXT,
  amount_side TEXT CHECK (amount_side IN ('debit', 'credit') OR amount_side IS NULL),
  allocation_count INTEGER NOT NULL CHECK (allocation_count >= 0),
  PRIMARY KEY (company_id, snapshot_id, voucher_id, line_number),
  FOREIGN KEY (company_id, snapshot_id, voucher_id)
    REFERENCES voucher_headers(company_id, snapshot_id, voucher_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE voucher_allocations (
  company_id TEXT NOT NULL,
  snapshot_id TEXT NOT NULL,
  voucher_id TEXT NOT NULL,
  owner_type TEXT NOT NULL CHECK (owner_type IN ('VOUCHER', 'LEDGER', 'INVENTORY')),
  owner_line_number INTEGER NOT NULL DEFAULT 0 CHECK (owner_line_number >= 0),
  allocation_index INTEGER NOT NULL CHECK (allocation_index >= 0),
  allocation_type TEXT NOT NULL CHECK (
    allocation_type IN ('accounting', 'batch', 'bank', 'bill', 'cost-track', 'inventory')
  ),
  source_name TEXT NOT NULL,
  values_json TEXT NOT NULL,
  PRIMARY KEY (
    company_id, snapshot_id, voucher_id, owner_type, owner_line_number, allocation_index
  ),
  FOREIGN KEY (company_id, snapshot_id, voucher_id)
    REFERENCES voucher_headers(company_id, snapshot_id, voucher_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX idx_voucher_snapshots_company_status
  ON voucher_snapshots(company_id, status, created_at DESC);
CREATE INDEX idx_voucher_headers_snapshot_date
  ON voucher_headers(company_id, snapshot_id, voucher_date);
CREATE INDEX idx_voucher_headers_snapshot_type
  ON voucher_headers(company_id, snapshot_id, voucher_type);
CREATE INDEX idx_voucher_headers_snapshot_number
  ON voucher_headers(company_id, snapshot_id, voucher_number);
CREATE INDEX idx_voucher_headers_snapshot_party
  ON voucher_headers(company_id, snapshot_id, party_name);
CREATE UNIQUE INDEX idx_voucher_headers_snapshot_guid
  ON voucher_headers(company_id, snapshot_id, guid) WHERE guid IS NOT NULL;
CREATE UNIQUE INDEX idx_voucher_headers_snapshot_key
  ON voucher_headers(company_id, snapshot_id, voucher_key) WHERE voucher_key IS NOT NULL;
CREATE UNIQUE INDEX idx_voucher_headers_snapshot_master
  ON voucher_headers(company_id, snapshot_id, master_id) WHERE master_id IS NOT NULL;
CREATE INDEX idx_voucher_ledger_name
  ON voucher_ledger_entries(company_id, snapshot_id, ledger_name);
CREATE INDEX idx_voucher_inventory_item
  ON voucher_inventory_entries(company_id, snapshot_id, item_name);

CREATE TRIGGER voucher_snapshots_immutable_update
BEFORE UPDATE ON voucher_snapshots
WHEN OLD.status IN ('ARCHIVED', 'FAILED')
  OR (OLD.status = 'PROMOTED' AND NEW.status != 'ARCHIVED')
BEGIN
  SELECT RAISE(ABORT, 'immutable voucher snapshot');
END;

CREATE TRIGGER voucher_snapshots_immutable_delete
BEFORE DELETE ON voucher_snapshots
WHEN OLD.status = 'PROMOTED'
BEGIN
  SELECT RAISE(ABORT, 'immutable voucher snapshot');
END;

CREATE TRIGGER voucher_headers_require_writable_insert
BEFORE INSERT ON voucher_headers
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = NEW.company_id
    AND s.snapshot_id = NEW.snapshot_id
    AND s.status IN ('PENDING', 'WRITING')
    AND s.validated_at IS NULL
)
BEGIN
  SELECT RAISE(ABORT, 'voucher snapshot is not writable');
END;

CREATE TRIGGER voucher_headers_immutable_update
BEFORE UPDATE ON voucher_headers
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = OLD.company_id AND s.snapshot_id = OLD.snapshot_id
    AND s.status IN ('PENDING', 'WRITING') AND s.validated_at IS NULL
)
BEGIN
  SELECT RAISE(ABORT, 'immutable voucher snapshot data');
END;

CREATE TRIGGER voucher_headers_immutable_delete
BEFORE DELETE ON voucher_headers
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = OLD.company_id AND s.snapshot_id = OLD.snapshot_id
    AND (
      s.status IN ('PENDING', 'WRITING')
      OR NOT EXISTS (
        SELECT 1 FROM voucher_active_snapshots a
        WHERE a.company_id = OLD.company_id AND a.snapshot_id = OLD.snapshot_id
      )
    )
)
BEGIN
  SELECT RAISE(ABORT, 'immutable voucher snapshot data');
END;

CREATE TRIGGER voucher_ledger_entries_require_staging
BEFORE INSERT ON voucher_ledger_entries
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = NEW.company_id AND s.snapshot_id = NEW.snapshot_id
    AND s.status IN ('PENDING', 'WRITING') AND s.validated_at IS NULL
)
BEGIN
  SELECT RAISE(ABORT, 'voucher snapshot is not writable');
END;

CREATE TRIGGER voucher_inventory_entries_require_staging
BEFORE INSERT ON voucher_inventory_entries
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = NEW.company_id AND s.snapshot_id = NEW.snapshot_id
    AND s.status IN ('PENDING', 'WRITING') AND s.validated_at IS NULL
)
BEGIN
  SELECT RAISE(ABORT, 'voucher snapshot is not writable');
END;

CREATE TRIGGER voucher_allocations_require_staging
BEFORE INSERT ON voucher_allocations
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = NEW.company_id AND s.snapshot_id = NEW.snapshot_id
    AND s.status IN ('PENDING', 'WRITING') AND s.validated_at IS NULL
)
BEGIN
  SELECT RAISE(ABORT, 'voucher snapshot is not writable');
END;

CREATE TRIGGER voucher_ledger_entries_immutable_update
BEFORE UPDATE ON voucher_ledger_entries
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = OLD.company_id AND s.snapshot_id = OLD.snapshot_id
    AND s.status IN ('PENDING', 'WRITING') AND s.validated_at IS NULL
)
BEGIN SELECT RAISE(ABORT, 'immutable voucher snapshot data'); END;

CREATE TRIGGER voucher_ledger_entries_immutable_delete
BEFORE DELETE ON voucher_ledger_entries
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = OLD.company_id AND s.snapshot_id = OLD.snapshot_id
    AND (
      s.status IN ('PENDING', 'WRITING')
      OR NOT EXISTS (SELECT 1 FROM voucher_active_snapshots a
        WHERE a.company_id = OLD.company_id AND a.snapshot_id = OLD.snapshot_id)
    )
)
BEGIN SELECT RAISE(ABORT, 'immutable voucher snapshot data'); END;

CREATE TRIGGER voucher_inventory_entries_immutable_update
BEFORE UPDATE ON voucher_inventory_entries
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = OLD.company_id AND s.snapshot_id = OLD.snapshot_id
    AND s.status IN ('PENDING', 'WRITING') AND s.validated_at IS NULL
)
BEGIN SELECT RAISE(ABORT, 'immutable voucher snapshot data'); END;

CREATE TRIGGER voucher_inventory_entries_immutable_delete
BEFORE DELETE ON voucher_inventory_entries
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = OLD.company_id AND s.snapshot_id = OLD.snapshot_id
    AND (
      s.status IN ('PENDING', 'WRITING')
      OR NOT EXISTS (SELECT 1 FROM voucher_active_snapshots a
        WHERE a.company_id = OLD.company_id AND a.snapshot_id = OLD.snapshot_id)
    )
)
BEGIN SELECT RAISE(ABORT, 'immutable voucher snapshot data'); END;

CREATE TRIGGER voucher_allocations_immutable_update
BEFORE UPDATE ON voucher_allocations
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = OLD.company_id AND s.snapshot_id = OLD.snapshot_id
    AND s.status IN ('PENDING', 'WRITING') AND s.validated_at IS NULL
)
BEGIN SELECT RAISE(ABORT, 'immutable voucher snapshot data'); END;

CREATE TRIGGER voucher_allocations_immutable_delete
BEFORE DELETE ON voucher_allocations
WHEN NOT EXISTS (
  SELECT 1 FROM voucher_snapshots s
  WHERE s.company_id = OLD.company_id AND s.snapshot_id = OLD.snapshot_id
    AND (
      s.status IN ('PENDING', 'WRITING')
      OR NOT EXISTS (SELECT 1 FROM voucher_active_snapshots a
        WHERE a.company_id = OLD.company_id AND a.snapshot_id = OLD.snapshot_id)
    )
)
BEGIN SELECT RAISE(ABORT, 'immutable voucher snapshot data'); END;
`;

/** Cross-process, company-scoped lease for production Voucher synchronization. */
export const MIGRATION_010 = `
CREATE TABLE voucher_sync_reservations (
  company_id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL,
  acquired_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);

CREATE INDEX idx_voucher_sync_reservations_expiry
  ON voucher_sync_reservations(expires_at);
`;

/**
 * Trusted-device records for optional auto-connect.
 *
 * Design decisions:
 * - token_hash stores a SHA-256 hex digest of the raw bearer token; the raw
 *   token is never persisted on the Connector side.
 * - mobile_number is optional metadata only and must not be used as proof of
 *   identity on its own.
 * - auto_connect_enabled is stored as INTEGER (0/1) per SQLite convention.
 * - revoked_at being non-NULL means the record is revoked and must not grant
 *   access even if the token hash matches.
 * - company_id is informational; all access must still be re-validated through
 *   the live session service before any data is returned.
 */
export const MIGRATION_011 = `
CREATE TABLE trusted_devices (
  device_record_id  TEXT PRIMARY KEY,
  token_hash        TEXT NOT NULL UNIQUE,
  company_id        TEXT NOT NULL,
  company_name      TEXT NOT NULL,
  installation_id   TEXT NOT NULL,
  friendly_name     TEXT,
  auto_connect_enabled INTEGER NOT NULL DEFAULT 0,
  created_at        TEXT NOT NULL,
  last_used_at      TEXT,
  revoked_at        TEXT
);

CREATE INDEX idx_trusted_devices_company
  ON trusted_devices(company_id);

CREATE INDEX idx_trusted_devices_installation
  ON trusted_devices(installation_id);
`;

/**
 * Secure local pairing: expiring bootstrap sessions (QR / one-time code) and the
 * device-bootstrap credentials they issue on successful redemption.
 *
 * Design note — trust model: this is a PHYSICALLY TRUSTED BOOTSTRAP, not an
 * offline-cryptographically-authenticated one. There is no pre-shared trust anchor a
 * first-time device could verify a signature against — an HMAC keyed by a secret carried
 * in the same payload it signs would be circular (whoever can read the payload can already
 * recompute it) and must never be presented as an authenticity guarantee. Instead: every
 * field the client submits at redemption (connector_id, host, port) is checked for an
 * EXACT match against this stored row before the secret is even compared — see
 * redeemBySessionId in pairing-session-repository.ts. That plus short expiry, single-use
 * (redeemed_at), cancellable (cancelled_at), a bounded number of guesses (failed_attempts)
 * before lockout, and constant-time secret comparison is the complete security model for
 * this phase. It does not by itself prove the QR/code was never tampered with before the
 * human scanned it — only that whatever the client is now presenting matches what was
 * stored at creation time. The live /health connectorId cross-check after connecting is
 * the future Android client's responsibility, not something this repository can perform
 * on its behalf.
 *
 * - secret_hash / short_code_hash store SHA-256 hex digests only; the raw secret and raw
 *   short code are returned once at creation time and never persisted.
 * - host/port/schema_version are captured at creation time so a redemption attempt whose
 *   submitted values don't match the session as originally created is rejected — counted
 *   against the same failed_attempts budget as a wrong-secret guess, and reported to the
 *   caller as one generic failure (see outcomeToHttpFailure in api/routes/pairing.ts) so
 *   the external response never discloses which field was wrong.
 * - Exactly one pairing session is active per Connector at a time: creating a new session
 *   cancels any still-active prior session for the same connector_id (enforced in the
 *   repository, not in SQL, so the cancellation is observable/testable). Expired-but-unused
 *   sessions are opportunistically pruned on every create() call (no background timer).
 * - connector_id is stamped from the Connector's own stable identity at creation time — a
 *   session can never be created "for" a different connector.
 * - pairing_device_credentials.device_id is the Android-supplied logical device identifier
 *   (never IMEI/serial/Android ID) when the redeeming client provides one; nullable because
 *   this phase does not yet require Android to send it.
 */
export const MIGRATION_012 = `
CREATE TABLE pairing_sessions (
  pairing_session_id TEXT PRIMARY KEY,
  connector_id        TEXT NOT NULL,
  connector_name      TEXT NOT NULL,
  host                 TEXT NOT NULL,
  port                 INTEGER NOT NULL,
  schema_version       TEXT NOT NULL,
  secret_hash          TEXT NOT NULL,
  short_code_hash       TEXT NOT NULL,
  failed_attempts      INTEGER NOT NULL DEFAULT 0,
  created_at           TEXT NOT NULL,
  expires_at           TEXT NOT NULL,
  redeemed_at          TEXT,
  cancelled_at         TEXT
);

CREATE INDEX idx_pairing_sessions_connector_active
  ON pairing_sessions(connector_id, redeemed_at, cancelled_at);

CREATE INDEX idx_pairing_sessions_expiry
  ON pairing_sessions(expires_at);

CREATE TABLE pairing_device_credentials (
  credential_id       TEXT PRIMARY KEY,
  pairing_session_id  TEXT NOT NULL REFERENCES pairing_sessions(pairing_session_id),
  connector_id        TEXT NOT NULL,
  device_id            TEXT,
  token_hash           TEXT NOT NULL UNIQUE,
  device_label         TEXT,
  created_at           TEXT NOT NULL,
  last_used_at         TEXT,
  revoked_at           TEXT
);

CREATE INDEX idx_pairing_device_credentials_session
  ON pairing_device_credentials(pairing_session_id);
`;

/**
 * Adaptive Tally Synchronization scheduler state (`docs/architecture/VENTURE-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md`).
 *
 * One row per (company_id, resource_kind), mirroring the existing `voucher_active_snapshots`
 * "current pointer" pattern rather than overloading the append-only `sync_runs` history table —
 * this is deliberately mutable current-state, not a log. `stage` is one of `active_window` /
 * `backoff_15` / `backoff_30` / `backoff_60`; `active_window_expires_at` is only meaningful while
 * `stage = 'active_window'`. A corrupt or missing row must fail closed toward MORE freshness, not
 * less — callers default to `active_window` on any read failure, never silently disable
 * scheduling (see the architecture doc §15).
 */
export const MIGRATION_013 = `
CREATE TABLE scheduler_state (
  company_id                TEXT NOT NULL,
  resource_kind              TEXT NOT NULL,
  stage                       TEXT NOT NULL,
  active_window_expires_at    TEXT,
  next_check_due_at           TEXT NOT NULL,
  updated_at                  TEXT NOT NULL,
  PRIMARY KEY (company_id, resource_kind)
);

CREATE INDEX idx_scheduler_state_next_check_due
  ON scheduler_state(next_check_due_at);
`;
