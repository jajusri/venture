# JSON to SQLite migration

## Detection

On `LocalDatabase.start()`, `JsonToSqliteMigrationService.migrateIfNeeded()` checks:

- `storage_meta.json_migration_completed`
- Presence of `{databasePath}/ledgers/*.json`

## Flow

1. For each company JSON file not already marked `completed` in `json_migration_runs`:
   - Insert run row as `running`
   - Parse JSON, upsert ledgers
   - Verify row count
   - Copy source to `{databasePath}/ledgers-backup/`
   - Mark run `completed`
2. On full success, set `json_migration_completed=true`
3. On any company failure, migration remains incomplete; source files are never deleted

## Restart safety

Completed companies are skipped. Failed companies can be retried after fixing source data.

## Verification

Compare JSON ledger count with `SELECT COUNT(*) FROM ledgers WHERE company_id = ?`.
