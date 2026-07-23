# Ledger Sync Security Review (Milestone 5A-P)

**Date:** 2026-07-23  
**Scope:** SQLite ledger storage, sync APIs, JSON migration, desktop IPC

## Summary

No critical or high-severity defects identified in automated review. Residual risks are operational (live Tally fault injection) and platform-specific (SQLite file permissions on Windows).

## Verified controls

| Control | Status | Notes |
|---------|--------|-------|
| Parameterized SQL | PASS | All repository and sync-run queries use bound parameters |
| IPC validation | PASS | Ledger query allowlist; company ID regex validation |
| API validation | PASS | Pagination bounds, sort allowlist, 409 on concurrent sync |
| No raw XML to renderer | PASS | Desktop receives normalized DTOs only |
| No sensitive values in logs | PASS | Existing redaction patterns preserved |
| No renderer DB access | PASS | SQLite opened only in connector main process |
| No arbitrary path access | PASS | Backup target fixed under `{databasePath}/backups` |
| No command injection | PASS | No shell execution in migration or storage paths |
| DoS via unbounded pagination | PASS | `pageSize` capped at 100 |
| Duplicate sync race | PASS | In-memory guard + durable active run check |

## Accepted risks

1. **Mid-response cancellation latency** — Tally HTTP body read cannot be aborted mid-stream; cancellation is cooperative at transport boundaries.
2. **Local file permissions** — SQLite DB inherits OS user ACLs; operators must protect `%AppData%`/configured `databasePath`.
3. **Live fault scenarios** — Scenarios 12–13 in recovery matrix require manual Tally availability testing.

## Recommendations

- Run live recovery matrix scenarios 12–13 before declaring full production readiness.
- Document backup restore drill for operators (`docs/operations/ledger-database-backup-recovery.md`).
