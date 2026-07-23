# Ledger database backup and recovery

## Backup

```http
POST /storage/ledgers/backup
```

Creates a timestamped copy at `{databasePath}/backups/budcom-ledger-{timestamp}.db`.

## Integrity check

```http
POST /storage/ledgers/integrity-check
```

Runs `PRAGMA integrity_check` and returns `{ ok, message }`.

## Recovery procedure

1. Stop the connector.
2. Copy the backup file over `budcom-ledger.db` (keep the failed file renamed for investigation).
3. Run integrity check.
4. Restart connector and verify `/sync/ledgers/statistics`.

## Corruption symptoms

- Connector logs `Unable to open local SQLite database`
- `/storage/ledgers/integrity-check` returns non-`ok`

Do not delete `ledgers-backup/` JSON archives until SQLite migration is verified complete.
