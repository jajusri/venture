export const STORAGE_SCHEMA_VERSION = 1;

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
