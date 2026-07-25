export const STORAGE_SCHEMA_VERSION = 8;

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
