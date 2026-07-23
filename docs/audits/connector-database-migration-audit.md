# Database & Migration Audit

## Schema review

Tables: `ledgers`, `sync_runs`, `json_migration_runs`, `storage_meta`, `schema_migrations` — appropriate indexes and unique constraints for company-scoped access.

## Confirmed defects (fixed)

1. **WAL backup inconsistency** — backup now runs `PRAGMA wal_checkpoint(FULL)` before copy  
2. **False healthy status** — `getStorageStatus()` now checks `integrity_check === 'ok'`  
3. **Migration completion on empty legacy dir** — no longer sets `json_migration_completed` when nothing to migrate  
4. **Path traversal in migration filenames** — `isSafeLegacyFileName()` + `resolveLegacyFile()`

## Remaining limitations

- **Sync resume** — `lastProcessedId` persisted but new runs always start fresh (full re-sync). Not data loss; operational inefficiency.  
- **Backup** — single-file copy after checkpoint; WAL/SHM not copied separately (correct after checkpoint)  
- **Benchmark 1k DB size 4096 bytes** — main file before checkpoint; data in WAL. Not a defect; measurement artifact documented.

## Migration test coverage

Unit tests: empty, valid, resume/skip, malformed JSON. Audit added: no premature completion flag.

## Integrity

`PRAGMA integrity_check` exposed via API. Foreign keys enabled. WAL mode configured.
