# Confirmed Connector Contracts — Sync Foundation (Android)

**Date:** 2026-07-27  
**Source of truth:** `connector/budcom_connector` implementation  
**Purpose:** Contract discovery for BUDCO Android Sync Foundation

---

## Scope selected for Android Sync Foundation

| Target | Public HTTP sync | Android action |
| --- | --- | --- |
| Ledgers | Yes | Manual sync + cancel + status + recent runs |
| Stock Items | Yes | Manual sync + cancel + status + recent runs |
| Vouchers | **No public sync routes** | Shown as unavailable (read-only voucher APIs remain) |

There is **no** Connector combined `/sync` endpoint.  
Android may offer **“Run available syncs”** as an honest sequential orchestration of Ledgers then Stock Items (not atomic).

---

## Shared prerequisites

| Concern | Confirmed behavior |
| --- | --- |
| Company | **Implicit session** via `POST /session/company`. Sync POSTs do **not** accept a company body/query field. |
| Session validation | Connector validates session before starting; failure → AppError, **no sync run created** |
| Content-Type | Mutations with a JSON body require `Content-Type: application/json` |
| Auth | None on sync routes |
| Concurrency (same entity) | One active run per company per resource → HTTP **409** `SYNC_CONFLICT` |
| Concurrency (cross-entity) | Ledger and stock-item syncs **may run concurrently** |
| Persistence | Sync **runs** persist in SQLite; live in-memory progress resets on Connector restart; abandoned runs → `interrupted` |

Session-related HTTP statuses (when validation fails): 400 / 403 / 404 / 410 / 503 depending on session code.

Service not running: **503** (e.g. “Ledger sync service is not running.”).

---

## Ledger sync

### Start (blocking)

| Item | Value |
| --- | --- |
| Route | `POST /sync/ledgers` |
| Body | `{ "incremental"?: boolean }` — truthy → incremental skip of unchanged rows; falsy/omitted → full |
| Behavior | **Synchronous.** Handler awaits completion. Response includes terminal result. |
| Progress during run | Poll `GET /sync/ledgers/status` on a **separate** connection |

### Cancel

| Item | Value |
| --- | --- |
| Route | `POST /sync/ledgers/cancel` |
| Response | `{ schemaVersion, progress }` |
| Idle cancel | Returns current progress (typically idle); **200** |

### Status

| Item | Value |
| --- | --- |
| Route | `GET /sync/ledgers/status` |
| Response | `{ schemaVersion, progress, storage }` |

### Runs / history

| Item | Value |
| --- | --- |
| List | `GET /sync/ledgers/runs?limit=` (default 20, clamp 1–50) |
| Detail | `GET /sync/ledgers/runs/:id` — 404 `NOT_FOUND` if missing / wrong company |

### Statistics (optional summary)

`GET /sync/ledgers/statistics` → `{ schemaVersion, statistics }` including `lastSyncedAt`.

### Progress fields (`LedgerSyncProgress`)

`syncRunId`, `status`, `totalExpected` (nullable), `startedAt`, `completedAt`, `durationMs`,  
`itemsProcessed`, `itemsAdded`, `itemsUpdated`, `itemsSkipped`, `itemsFailed`,  
`lastError`, `cancelRequested`, `storageBackend`, `migrationStatus`

**No percentage field.** Android may show determinate progress only when `totalExpected` is a positive number.

### Status enum

`idle` | `running` | `completed` | `failed` | `cancelled` | `interrupted` | `recovering` | `cancelling`

### Sync result (`200`)

`schemaVersion`, `syncRunId`, `status`, `statistics`, `progress`, `changes[]`, `validationIssueCount`

`changes[]`: `ledgerId`, `changeType` (`added`|`updated`|`skipped`|`failed`|`deleted`), `reason?`

**Note:** Success path typically returns `status: "completed"` even when `validationIssueCount > 0`. Android must not invent a separate “partial” ledger terminal state beyond reporting the issue count.

### Full vs incremental

Always extracts from Tally; `incremental: true` only skips unchanged fingerprints. Android Sync Foundation defaults to **full** (`incremental: false`) unless UI exposes an incremental option later.

### Date range

None.

---

## Stock-item sync

Mirrors ledgers under `/sync/stock-items/...`.

### Differences

| Item | Value |
| --- | --- |
| Start | `POST /sync/stock-items` body `{ incremental?: boolean }` |
| Cancel | `POST /sync/stock-items/cancel` |
| Status | `GET /sync/stock-items/status` |
| Runs | `GET /sync/stock-items/runs`, `GET /sync/stock-items/runs/:id` |
| Statistics | `GET /sync/stock-items/statistics` |
| Extra result fields | `extractionCompleteness`: `complete`\|`partial`\|`failed`\|`cancelled`; `deletionReconciliation`: always `"disabled"` |
| Changes | `stockItemId` instead of `ledgerId` |

Progress shape is the same as ledger progress.

---

## Voucher sync

| Item | Value |
| --- | --- |
| Public HTTP sync | **Not implemented** |
| Internal service | Exists in-process only (snapshot sync); not exposed to Android |
| Android | Do **not** call voucher sync. Disclose “Public voucher sync is not available.” |

Read-only voucher list/detail remain separate contracts (`android-voucher-api.md`).

---

## Health / readiness relevance

| Endpoint | Sync relevance |
| --- | --- |
| `GET /health` | Includes SyncEngine/LedgerSync service row; does not expose stock progress or run ids |
| `GET /ready` | DB + voucher composition flags; **does not** gate ledger/stock sync readiness |

Android should still require a selected company and treat Connector unreachable / 503 honestly. Do not invent a “ready required” rule that the Connector does not enforce for ledger/stock sync.

---

## Android interpretation rules

| Connector fact | Android UI |
| --- | --- |
| Blocking POST | Indeterminate (or count-based) progress until response; optional status polling while waiting |
| `itemsProcessed` / `totalExpected` | Determinate only when `totalExpected > 0` |
| `validationIssueCount > 0` with `completed` | Success with warnings (issue count disclosed) |
| Stock `extractionCompleteness: partial` | Partial success when reported |
| 409 `SYNC_CONFLICT` | Concurrent-run blocked / already running |
| Cancel endpoints | Show Cancel only for Ledgers / Stock Items |
| No voucher sync route | Unavailable capability row |
| Process death mid-POST | Live progress may still be `running` on Connector; recover via status poll; do not invent completion |
| In-memory status after Connector restart | Expect `idle`; history may show `interrupted` |

### Explicit Android non-goals (this milestone)

- WorkManager / automatic / scheduled sync  
- Fabricated percentages or ETAs  
- Claiming voucher sync  
- Atomic “Sync All”  
- Durable Room sync history (use Connector runs when needed)  
- Auto-retry of mutating sync start  
- Clear-cache / integrity-check / backup surfaces (deferred)

### Polling defaults (Android-derived)

| Setting | Value |
| --- | --- |
| Status poll interval | 1500 ms |
| Max consecutive poll failures before surfacing error | 5 |
| Sync HTTP read timeout | Extended dedicated client (blocking POST can exceed default 30s) |

---

## Source files

- `src/api/routes/ledgers.ts`
- `src/api/routes/stock-items.ts`
- `src/erp/ledger/ledger-domain.ts`
- `src/erp/stock-item/stock-item-domain.ts`
- `src/services/ledger/ledger-sync.service.ts`
- `src/services/stock-item/stock-item-sync.service.ts`
- Tests: `test/integration/ledger-sync.test.ts`, `stock-item-sync.test.ts`, `stock-item-api-negative.test.ts`, `sync-run-concurrency.test.ts`, `interrupted-sync-restart.test.ts`
