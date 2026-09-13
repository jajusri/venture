# Confirmed Connector Contracts — Master Data (Android Milestone)

**Date:** 2026-07-27 (updated for Stock Item Browser)  
**Source of truth:** Connector implementation under `connector/venture_connector`  
**Purpose:** Contract discovery for VENTURE Android Master Data browsers

---

## Ledger list (Ledger Browser)

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

- `schemaVersion`: `"1.0.0"`
- `dataFreshnessAt`: ISO timestamp
- `storage`: `StorageStatus`
- `items`: `LedgerSummary[]`
- `pagination`: `{ page, pageSize, totalItems, totalPages }`

### Source files

- `src/api/routes/ledgers.ts`
- `src/erp/ledger/ledger-domain.ts`
- `src/services/ledger/ledger-sync.service.ts`

---

## Stock Item list (Stock Item Browser)

| Item | Confirmed value |
| --- | --- |
| Route | `GET /stock-items` |
| Method | GET |
| Auth headers | None (session is in-process Connector state) |
| Session requirement | Selected company required (`requireCompanyId` / session validation) |
| Missing company | HTTP **400**, `VALIDATION_ERROR` / “No company selected.” |
| Service not running | HTTP **503** |

### Query parameters

| Param | Behavior |
| --- | --- |
| `query` | Optional string, max 128 chars — server-side search |
| `parentGroup` | Optional string, max 128 |
| `category` | Optional string, max 128 |
| `dataQuality` | Optional `complete` \| `incomplete` only |
| `page` | Default 1; values &lt; 1 clamped to 1 |
| `pageSize` | Default 50; clamped 1–100 |
| `sortBy` | `name` (default), `parentGroup`, `category`, `baseUnit`, `syncedAt`; unknown → `name` |
| `sortDirection` | `asc` (default) or `desc`; other → `asc` |

### Response (200)

- `schemaVersion`: `"1.0.0"`
- `dataFreshnessAt`: ISO timestamp
- `storage`: `StorageStatus`
- `items`: `StockItemSummary[]`
- `pagination`: `{ page, pageSize, totalItems, totalPages }`

### StockItemSummary fields (domain contract)

`id`, `name`, `normalizedName`, `parentGroup?`, `category?`, `baseUnit?`, `dataQuality` (`complete`\|`incomplete`), `openingBalance?`, `closingBalance?`, `hsnCode?`, `gstRate?`, `guid?`, `alterId?`, `alias?`, `partNumber?`, `status` (`active`\|`inactive`\|`unknown`), `sourceSystem`, `isDeleted`, `syncedAt`

`NormalizedAmount`: `{ amount, currencyCode, side }` where `side` is `Dr` | `Cr`.

### Error schema

Connector `AppError` JSON (e.g. `{ code, message }`); detail miss → 404 `NOT_FOUND`.

### Request body

None for list/detail GET.

### Detail (available; not required for list UI)

| Item | Value |
| --- | --- |
| Route | `GET /stock-items/:id` |
| 200 | `{ schemaVersion, dataFreshnessAt, stockItem: StockItemDetails }` |

### Source files

- `src/api/routes/stock-items.ts`
- `src/erp/stock-item/stock-item-domain.ts`
- `src/services/stock-item/stock-item-sync.service.ts` (`getStockItems`, `requireCompanyId`)
- Tests: `test/integration/stock-item-api-negative.test.ts`, `test/integration/stock-item-sync.test.ts`, `test/unit/stock-item/*`

### Ambiguities / out of scope

- List reflects **local Connector cache** after sync; empty list is valid when never synced.
- Sync endpoints (`POST /sync/stock-items`, …) are **out of scope** for this Android milestone.
- Alternate extraction route `GET /companies/:companyId/stock-items` is **not** used by the browser.

---

## Android browser field policy

Display only confirmed summary fields useful for browsing (name, group/category, unit, status, balances/HSN when present). Do not fabricate warehouse, pricing, tax calculations, or movement history.
