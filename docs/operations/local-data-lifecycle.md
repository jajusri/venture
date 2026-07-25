# Local data lifecycle — backup, config, and retention ownership

**Scope:** RC#4 Phase B2-doc — documentation only for locally persisted Budcom artifacts.  
**Last updated:** 2026-07-25  
**Status:** Controlled pilot reference; unrestricted production not approved.

This document records discovered on-disk artifacts, who owns them, and what automatic cleanup exists today. It does **not** authorize automatic deletion of manual SQLite backups.

---

## Summary table

| Artifact | Owner | Creator | Location | Trigger | Validation | Restore responsibility | Cleanup responsibility | Uninstall treatment | Auto-delete in RC#4 |
|----------|-------|---------|----------|---------|------------|------------------------|------------------------|---------------------|---------------------|
| Manual SQLite backup | Operator / support | Connector `SqliteStorageService.createBackup()` via `POST /storage/ledgers/backup` or `POST /storage/stock-items/backup` | `{databasePath}/backups/budcom-ledger-{timestamp}.db` | Manual API call | `PRAGMA integrity_check` on copy; failed copies removed immediately | Operator — copy over `budcom-ledger.db` per [ledger-database-backup-recovery.md](./ledger-database-backup-recovery.md) | **Operator / support — manual only** | **Retained by default** (not removed by installer in current packaging) | **No — explicit RC#4 exclusion** |
| Migration-time SQLite backup | Connector (automatic, recovery-critical) | `LedgerSyncService` identity migration path before cache replacement | `{databasePath}/backups/budcom-ledger-{timestamp}.db` (same directory as manual backups) | Automatic pre-migration safety step | Same integrity gate as manual backup | Connector/support — required before identity migration rollback | **Retained until operator verifies migration** | Retained by default | **No** |
| Legacy JSON migration copy | Connector migration | `JsonToSqliteMigrationService` | `{databasePath}/ledgers-backup/{company}.json` | Per-company JSON→SQLite migration | Row-count verification after upsert | Operator — only if SQLite migration must be reversed manually | **Operator — manual only; do not delete until migration verified** ([json-to-sqlite-migration.md](./json-to-sqlite-migration.md)) | Retained by default | **No** |
| Desktop config (primary) | Desktop app | `DesktopConfigStore.save()` | `{userData}/desktop-config.json` | Settings save, recovery rewrite | `validateDesktopConfig()` | Desktop automatic recovery from backup; operator may edit only via Settings UI | Overwritten only by validated save or recovery | Retained by default | **No** |
| Desktop config backup | Desktop app | `DesktopConfigStore.save()` (copy before atomic rename) | `{userData}/desktop-config.backup.json` | Every successful save when primary already exists | Same schema validation on load | Desktop automatic recovery when primary corrupt | Overwritten on next successful save; **never auto-deleted** | Retained by default | **No** |
| Desktop config temp | Desktop app | `DesktopConfigStore.save()` (atomic write target) | `{userData}/desktop-config.json.tmp` | In-flight settings save | Schema validation before promotion | Completed by B2b-lite startup reconciliation (promote/delete orphan) | **Desktop startup reconciliation (B2b-lite)** — exact app-owned path only | Removed when promoted or identified as stale/invalid orphan | **Yes — orphan temp only (B2b-lite)** |
| Corrupt config archive | Desktop app | `DesktopConfigStore.recoverFromCorruption()` or B2b-lite promotion over invalid primary | `{userData}/desktop-config.json.corrupt-{timestamp}.json` | Primary config unreadable/invalid | None (archival copy of failed primary) | Operator/support forensic review only | **Never auto-deleted in RC#4** | Retained by default | **No** |
| Diagnostic export bundle dir | Desktop app | `DiagnosticsService.exportBundle()` | `{userData}/diagnostics-exports/budcom-diagnostics-{iso}/` | Manual export + post-export cleanup | Allowlisted bundle schema | Operator — copy bundle for support | **Desktop retention service (B2b)** — age-bound; newest always preserved | Retained by default | **Yes — expired app-owned exports (B2b)** |
| Tally request audit JSONL | Connector | `TallyRequestAuditor` | `{diagnosticsDir}/tally-request-audit.jsonl` (+ numbered archives) | Every guarded Tally request when enabled | Metadata-only schema | Support/forensics | **Connector rotation (B2a)** — size/count bounded | Retained by default | **Yes — rotated archives (B2a)** |
| `sync_runs` history rows | Connector | Sync pipeline persistence | `{databasePath}/budcom-ledger.db` table `sync_runs` | Sync execution | TD-006 recovery queries | Connector startup recovery | **Connector prune service (B2c)** — age/count bounded with TD-006 safety | Retained in database file | **Yes — terminal rows only (B2c)** |
| Desktop file logs | Desktop app | `FileLogWriter` | `{userData}/logs/budcom-desktop.log` (+ rotation suffixes) | Runtime logging | Export-grade sanitization on write | Operator | Size/count rotation only; **no age retention in RC#4** | Retained by default | **Partial — size rotation only** |
| Live SQLite database | Connector | `SqliteStorageService.start()` | `{databasePath}/budcom-ledger.db` (+ `-wal`/`-shm`) | Connector startup | `PRAGMA integrity_check` API | Operator via backup restore | **Never auto-deleted** | Retained by default | **No** |

**Path notes**

- `{databasePath}` defaults to `./data/budcom-connector.db` parent semantics: configured directory containing `budcom-ledger.db` (see `BUDCOM_DATABASE_PATH`).
- `{userData}` is Electron `app.getPath('userData')` — on Windows typically `%APPDATA%/budcom-desktop/`.

---

## Explicit no-auto-delete rule — manual SQLite backups

**Approved RC#4 decision:** Manual `POST /storage/ledgers/backup` and `POST /storage/stock-items/backup` copies are **operator-owned recovery artifacts**. The connector must **not** silently delete them in RC#4.

Rationale (Phase B2 investigation):

- Backups may be the only recovery path after corruption.
- Deletion policy requires product-owner authorization and explicit retention configuration.
- Migration-time backups share the same directory and must remain distinguishable by operator process, not automatic pruning.

---

## Desktop config temp reconciliation (B2b-lite)

Orphan `{userData}/desktop-config.json.tmp` files can remain after a crash between temp write and atomic rename.

**Startup integration:** `main.ts` runs `DesktopConfigTempReconciliationService.reconcile()` after `LogService` initialization and **before** `DesktopConfigStore` construction.

**Algorithm (exact app-owned temp path only):**

1. If temp absent → no-op.
2. `lstat` temp — on failure → log aggregate failure; preserve files.
3. If temp is symlink or directory → skip; never delete.
4. Validate primary and temp JSON via `validateDesktopConfig()`.
5. If primary valid → delete temp (stale or invalid orphan).
6. If primary invalid/missing and temp valid → archive corrupt primary best-effort, then rename temp → primary.
7. If primary invalid, temp invalid, backup valid → delete invalid temp; `DesktopConfigStore` recovery uses backup.
8. If primary, temp, and backup all invalid → preserve temp (uncertain state).

**Never deleted by B2b-lite:** primary config, backup, corrupt archives, unrelated `.tmp` files, directories, symlinks.

**Failure behaviour:** reconciliation errors do not block startup.

---

## Restore responsibility

| Scenario | Responsible party | Procedure reference |
|----------|-------------------|---------------------|
| SQLite corruption | Operator | [ledger-database-backup-recovery.md](./ledger-database-backup-recovery.md) |
| Incomplete JSON migration | Operator + connector retry | [json-to-sqlite-migration.md](./json-to-sqlite-migration.md) |
| Desktop config corruption | Desktop automatic | `DesktopConfigStore.recoverFromCorruption()` |
| Interrupted config save (orphan temp) | Desktop automatic (B2b-lite) | This document — temp reconciliation |
| TD-006 interrupted sync | Connector automatic | Startup `recoverAllAbandonedRuns()` + `sync_runs` retention (B2c) |

---

## Retention responsibility

| Class | Automatic enforcement | Configuration |
|-------|----------------------|---------------|
| Diagnostic exports | Desktop B2b | `diagnosticsRetentionDays` |
| Tally audit JSONL | Connector B2a | `tallyRequestAuditMaxBytes`, `tallyRequestAuditMaxFiles` |
| `sync_runs` terminal rows | Connector B2c | `syncRunHistoryMaxCount`, `syncRunHistoryMaxAgeDays` |
| Config orphan temp | Desktop B2b-lite | Fixed policy — no user setting |
| Manual SQLite backups | **None** | Operator manual deletion |
| Config backup / corrupt archives | **None** | Operator manual deletion |
| Legacy JSON copies | **None** | Operator after migration verification |

---

## Uninstall data ownership

Current repository state (no production installer lifecycle in RC#4):

- **Desktop `{userData}`** (config, logs, diagnostic exports) is treated as **user-owned persistent data**. Uninstall behaviour is **not automated** in RC#4; data remains unless the operator deletes it manually.
- **Connector `{databasePath}`** (SQLite, backups, legacy JSON, audit files under CWD/diagnostics) is **operator-owned business data**. Uninstall/remove of the connector process does **not** imply automatic database or backup deletion.
- **Folders that must never be deleted automatically in RC#4:** `{databasePath}/backups/`, `{databasePath}/ledgers-backup/`, `{userData}/desktop-config.backup.json`, `{userData}/desktop-config.json.corrupt-*.json`, manual operator copies outside app-owned naming conventions.

Installer/uninstall cleanup remains a **separate authorized milestone**.

---

## RC#4 local-lifecycle closure assessment

### Closed in RC#4 (Phases B1–B2b-lite)

- Metadata-only Tally audit persistence (B1)
- Bounded audit rotation (B2a)
- Diagnostic export age retention (B2b)
- Orphan desktop config temp reconciliation (B2b-lite)
- Bounded `sync_runs` terminal-row pruning with TD-006 safety (B2c)
- On-disk logging sanitization (Phase B1 logger/file-log fixes)

### Remaining blockers (require separate authorization)

- Encryption at rest (SQLite, logs, audit)
- Automatic deletion/retention for manual SQLite backups
- Migration JSON archive cleanup policy
- Generic filesystem / user-file cleanup framework
- Production installer and uninstall lifecycle
- Cloud / remote retention
- Secure deletion, crash reporter, telemetry
- Corrupt config archive pruning
- Desktop log age retention beyond size rotation

### Production-readiness status

| Mode | Status |
|------|--------|
| Controlled pilot | Local lifecycle controls sufficient for approved pilot scope with documented operator responsibilities |
| Unrestricted production | **Not approved** — encryption, installer lifecycle, backup governance, and broader retention gaps remain |

---

## Related documents

- [ledger-database-backup-recovery.md](./ledger-database-backup-recovery.md)
- [json-to-sqlite-migration.md](./json-to-sqlite-migration.md)
- [SQLITE_BACKUP_RESTORE_DRILL_EVIDENCE.md](../diagnostics/SQLITE_BACKUP_RESTORE_DRILL_EVIDENCE.md)
- [milestone-5b-stage-update.md](../stage-updates/milestone-5b-stage-update.md) §28
