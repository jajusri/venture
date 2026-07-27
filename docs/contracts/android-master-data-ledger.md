# Confirmed Connector Contracts — Master Data (Android Milestone)

**Date:** 2026-07-27  
**Source of truth:** Connector implementation under `connector/budcom_connector`  
**Purpose:** Contract discovery for BUDCO Android Master Data / Ledger Browser

---

## Ledger list (primary for Ledger Browser)

| Item | Confirmed value |
| --- | --- |
| Route | `GET /ledgers` |
| Method | GET |
| Auth headers | None (session is in-process Connector state) |
| Session requirement | Selected company required; service validates session via `ConnectorSessionService` |
| Missing company | HTTP **400**, `VALIDATION_ERROR` / “No company selected.” |
| Service not running | HTTP **503** |

### Query parameters

| Param | Behavior |
| --- | --- |
| `query` | Optional string, max 128 chars — server-side search |
| `status` | Optional ledger status filter |
| `parentGroup` | Optional string, max 128 |
| `page` | Default 1; values &lt; 1 clamped to 1 |
| `pageSize` | Default 50; clamped 1–100 |
| `sortBy` | `name` (default), `parentGroup`, `closingBalance`, `syncedAt`; unknown → `name` |
| `sortDirection` | `asc` (default) or `desc`; other → `asc` |

### Response (200)

Envelope fields plus search result:

- `schemaVersion`: `"1.0.0"`
- `dataFreshnessAt`: ISO timestamp
- `storage`: `StorageStatus` (`backend`, `schemaVersion`, `databaseHealthy`, `migrationStatus`, `message`)
- `items`: `LedgerSummary[]`
- `pagination`: `{ page, pageSize, totalItems, totalPages }`

### LedgerSummary fields (domain contract v2)

`id`, `name`, `normalizedName`, `alias?`, `parentGroup?`, `status`, `openingBalance?`, `closingBalance?`, `balanceNature`, `guid?`, `alterId?`, `masterId?`, `identitySource`, `dataQuality`, `isBillWiseOn?`, `isDeleted`, `syncedAt`

`NormalizedAmount`: `{ amount, currencyCode, side }` where `side` is `Dr` | `Cr`.

### Error schema

Connector `AppError` JSON (e.g. `{ code, message }`); 404 on detail miss.

### Request body

None for list/detail GET.

### Pagination / search / sort

Confirmed as above (page + pageSize + totalPages; `query` search; limited sort fields).

### Source files

- `src/api/routes/ledgers.ts`
- `src/erp/ledger/ledger-domain.ts`
- `src/services/ledger/ledger-sync.service.ts` (`getLedgers`, `requireCompanyId`)
- Tests: `test/integration/ledger-sync.test.ts`, `test/integration/ledger-api-negative.test.ts`, `test/unit/ledger/ledger-sync.test.ts`

### Ambiguities

- List reflects **local Connector cache** after sync; empty list is valid when never synced (not a protocol gap).
- Sync endpoints (`POST /sync/ledgers`, …) exist but are **out of scope** for this Android milestone (Sync foundation is later).

---

## Ledger detail (available; not required for list milestone UI)

| Item | Value |
| --- | --- |
| Route | `GET /ledgers/:id` |
| 200 | `{ schemaVersion, dataFreshnessAt, ledger: LedgerDetails }` |
| 404 | `{ code: 'NOT_FOUND', message }` |

---

## Alternate live-extraction route (not used for Browser cache list)

| Item | Value |
| --- | --- |
| Route | `GET /companies/:companyId/ledgers` |
| Behavior | Extraction/master-data service with pagination (`page`/`pageSize`) |
| Source | `src/api/routes/master-data.ts` |

Android Ledger Browser uses **`GET /ledgers`** (session-scoped repository), not the company-path extraction route.

---

## Stock Item (documented; implementation deferred)

| Item | Confirmed value |
| --- | --- |
| Route | `GET /stock-items` |
| Detail | `GET /stock-items/:id` |
| Session | Same company/session requirement pattern as ledgers |
| Query | `query`, `parentGroup`, `category`, `dataQuality`, `page`, `pageSize`, `sortBy`, `sortDirection` |
| Response | `schemaVersion`, `dataFreshnessAt`, `storage`, `items`, `pagination` |
| Source | `src/api/routes/stock-items.ts`, `src/erp/stock-item/stock-item-domain.ts` |

Full Stock Item Browser is deferred to the next roadmap milestone.
