# SQLite Backup/Restore Automated Drill Evidence

**Date:** 2026-07-23  
**Test:** `connector/venture_connector/test/integration/sqlite-backup-restore.test.ts`

## Procedure automated

1. Create temporary SQLite database via `SqliteStorageService`.
2. Insert representative data:
   - `ledgers` (via repository upsert + direct row for WAL churn)
   - `sync_runs` (active run record)
   - `storage_meta` marker key
   - `schema_migrations` (via normal migration bootstrap)
3. Confirm WAL file exists on source before backup.
4. Run `storage.createBackup()` (includes `PRAGMA wal_checkpoint(FULL)`).
5. Close source database via `storage.stop()`.
6. Open backup file as independent read-only `SqliteDatabase`.
7. Verify:
   - `PRAGMA integrity_check` → `ok`
   - `PRAGMA foreign_key_check` → empty
   - schema version matches `STORAGE_SCHEMA_VERSION`
   - ledger row count and representative fields
   - sync run count
   - storage_meta marker value
8. Confirm backup has no `-wal`/`-shm` sidecar files.
9. Confirm API returns filename only (no absolute path).
10. Confirm stopped storage backup returns `ok: false`.

## Result

All assertions pass in CI/local Vitest run (see final regression section in sign-off report).
