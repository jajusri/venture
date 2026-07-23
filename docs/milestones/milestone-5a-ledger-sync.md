# Milestone 5A — Ledger Synchronization Foundation

## Objective

Establish the first production business-data pipeline: ERP-neutral ledger synchronization from Tally into the local Business OS store, exposed through connector APIs and the desktop shell.

## Architecture

```
Desktop (Ledger page)
  └─ IPC → LedgerService → Connector HTTP
        └─ /ledgers, /sync/ledgers/*
              └─ LedgerSyncService
                    ├─ ErpReadPort.readLedgers()   [Tally adapter — no raw XML above port]
                    ├─ mapNormalizedLedgerToDomain()
                    ├─ validateLedgerCollection()
                    └─ LedgerRepository (JSON per company)
```

### ERP-neutral domain

- `LedgerSummary`, `LedgerDetails`, `LedgerCollection`, `LedgerStatistics`
- `LedgerSyncProgress`, `LedgerSyncResult`, `LedgerChange`
- `LedgerStatus`, `LedgerSyncStatus`

### Tally adapter path

- Existing M3 extraction (`mapLedger`) extended with GUID, AlterID, GST, mailing, contact, status
- `mapTallyLedgerToDomain()` for full XML node mapping inside adapter layer
- `mapNormalizedLedgerToDomain()` bridges extraction models into persisted ledger domain

### Repository

- Path: `{databasePath}/ledgers/{companyId}.json`
- CRUD, soft delete, search, pagination, sorting, statistics
- Atomic writes via temp file + rename

### Sync engine

- Full sync via `POST /sync/ledgers`
- Incremental architecture via alterId/GUID/balance comparison
- Progress, cancellation flag, retry on extraction, idempotent upsert
- Replaces `SyncEngineStub` while preserving health reporting

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ledgers` | Paginated/searchable local ledger store |
| GET | `/ledgers/:id` | Ledger detail by ID |
| POST | `/sync/ledgers` | Trigger sync (`{ incremental?: boolean }`) |
| GET | `/sync/ledgers/status` | Sync progress |
| GET | `/sync/ledgers/statistics` | Aggregated statistics |
| POST | `/sync/ledgers/clear-cache` | Clear company ledger cache |

Existing master-data route `GET /companies/:companyId/ledgers` remains unchanged (live Tally read, no persistence).

## Desktop Features

- New **Ledgers** navigation view
- Total/active/GST statistics, last sync, sync status, duration
- Search, pagination, manual sync, refresh, clear cache (confirmation)
- Non-blocking progress indicator during sync

## Validation Rules

Duplicate name/GUID/AlterID, invalid parent, circular references, reserved name conflicts, missing required fields, invalid balances, whitespace normalization, malformed XML reporting.

## Files Added

### Connector
- `src/erp/ledger/ledger-domain.ts`
- `src/erp/ledger/ledger-validation.ts`
- `src/erp/ledger/ledger-mapper.ts`
- `src/tally/ledgers/tally-ledger-mapper.ts`
- `src/services/ledger/ledger-repository.ts`
- `src/services/ledger/ledger-sync.service.ts`
- `src/api/routes/ledgers.ts`
- `scripts/live-5a-validation.ts`
- `test/unit/ledger/*.test.ts`
- `test/integration/ledger-sync.test.ts`

### Desktop
- `src/application/ledger-service.ts`
- `test/unit/ledger-service.test.ts`
- `test/renderer/ledger-render.test.ts`

## Files Modified

### Connector
- `src/extraction/core/types.ts` — extended `NormalizedLedger`
- `src/extraction/parsers/entity-mappers.ts` — richer `mapLedger`
- `src/core/tokens.ts`, `src/bootstrap/register-services.ts`
- `src/api/server.ts`, `src/api/middleware/read-only.ts`
- `src/services/placeholders/api-server.stub.ts`
- `test/helpers/test-context.ts`

### Desktop
- `src/application/types.ts`, `connector-http-client.ts`, `sync-status-mapper.ts`
- `src/application/ipc-allowlist.ts`, `src/main/main.ts`, `src/preload/preload.ts`
- `src/renderer/index.html`, `scripts/app.ts`, `styles/main.css`

## Production Readiness

| Gate | Status |
|------|--------|
| Connector build | PASS |
| Desktop build | PASS |
| Connector tests | 254/254 PASS |
| Desktop tests | 67/67 PASS |
| Live Tally validation | See completion report |
| Regression 3A–4D | PASS (existing suites green) |

## Recommended Next Milestone

**5B — Voucher synchronization** or **5A.1 — SQLite repository + interrupted sync checkpointing**
