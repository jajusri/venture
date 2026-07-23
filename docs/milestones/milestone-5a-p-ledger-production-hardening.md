# Milestone 5A-P — Ledger Synchronization Production Hardening

## Objective

Upgrade Milestone 5A from controlled-pilot readiness to production-grade ledger synchronization with durable SQLite storage, migration safety, sync checkpoints, and extraction-phase cancellation.

## Baseline

- Prior milestone: 5A (`9e24483b038e3417c2731e662ff8fe551bff6057`)
- Connector tests after 5A-P: **261/261 PASS**
- Desktop tests after 5A-P: see completion report

## Architecture

```
Desktop → Connector API → LedgerSyncService
                              ├─ SqliteStorageService (LocalDatabase)
                              │    ├─ SqliteDatabase (node:sqlite, WAL)
                              │    ├─ SqliteLedgerRepository
                              │    ├─ SyncRunRepository
                              │    └─ JsonToSqliteMigrationService
                              ├─ ErpReadPort.readLedgers({ signal })
                              └─ Session + concurrent-sync guard (409)
```

Database path: `{databasePath}/budcom-ledger.db`  
Legacy JSON: `{databasePath}/ledgers/*.json` → migrated with backup under `ledgers-backup/`

## Key deliverables

| Area | Implementation |
|------|----------------|
| SQLite repository | Company-scoped ledgers, indexes, soft delete, upsert batches, search/pagination/stats |
| JSON migration | One-time import, row-count verification, resume, backup, no auto-delete of source |
| Durable sync runs | `sync_runs` table, abandoned recovery, resume from `lastProcessedId` |
| Cancellation | `AbortSignal` through extraction; `POST /sync/ledgers/cancel` |
| API hardening | Pagination/sort allowlists, conflict responses, integrity/backup endpoints |
| Desktop UX | Cancel Sync, storage/migration indicators, disabled duplicate sync |
| Performance | Benchmark script; 1k/10k/50k synthetic datasets |

## Known limitation

Tally HTTP response bodies cannot be interrupted mid-read. Cancellation is honored before requests, between retries, and after response completion.

## Readiness

See `docs/diagnostics/MILESTONE_5A_P_COMPLETION_REPORT.md` for gate score and live recovery matrix.
