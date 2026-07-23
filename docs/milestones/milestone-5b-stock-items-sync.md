# Milestone 5B — Stock Items Synchronization

## Objective

Establish ERP-neutral stock item synchronization from Tally into the local Business OS SQLite store, exposed through connector APIs and the desktop shell. Mirrors the hardened 5A-P ledger sync pattern without reopening its architecture.

## Architecture

```
Desktop (Stock Items page)
  └─ IPC → StockItemService → Connector HTTP
        └─ /stock-items, /sync/stock-items/*
              └─ StockItemSyncService
                    ├─ ErpReadPort.readStockItems()   [List of Stock Items collection only]
                    ├─ mapNormalizedStockItemToDomain()
                    ├─ validateStockItemCollection()
                    └─ SqliteStockItemRepository + SyncRunRepository (resource_kind = stock-items)
```

### ERP-neutral domain

- `StockItemSummary`, `StockItemDetails`, `StockItemStatistics`
- `StockItemSyncProgress`, `StockItemSyncResult`, `StockItemChange`
- `StockItemDataQuality`: `complete` | `incomplete` (when `BASEUNITS` absent from Tally export)

### Tally safety

- **Allowed:** `List of Stock Items` collection (`STOCK_ITEMS`) — read-only, CONDITIONAL
- **Forbidden:** single-object `Stock Item` export, `List of Units`
- Missing `BASEUNITS` in default export → `dataQuality: incomplete` + validation warning, not silent failure

### Storage (schema v3)

- `stock_items` table (company-scoped, fingerprinted upsert, search/pagination)
- `sync_runs.resource_kind` column — `'ledgers' | 'stock-items'` for independent sync concurrency per company
- **Migration 003:** upgrades partial pre-release `stock_items` tables missing identity columns (`guid`, `alter_id`, etc.)

### Sync engine

- Full sync via `POST /sync/stock-items`
- Incremental via content fingerprint comparison
- Progress, cancellation (`AbortSignal`), retry on extraction, batch upsert (250)
- Independent active-run guard per `resource_kind`

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/stock-items` | Paginated/searchable local stock item store |
| GET | `/stock-items/:id` | Stock item detail by ID |
| POST | `/sync/stock-items` | Trigger sync (`{ incremental?: boolean }`) |
| POST | `/sync/stock-items/cancel` | Cancel in-flight sync |
| GET | `/sync/stock-items/status` | Sync progress |
| GET | `/sync/stock-items/statistics` | Aggregated statistics |
| GET | `/sync/stock-items/runs` | Recent sync runs |
| GET | `/sync/stock-items/runs/:id` | Sync run detail |
| POST | `/sync/stock-items/clear-cache` | Clear company stock item cache |
| POST | `/storage/stock-items/integrity-check` | SQLite integrity check |
| POST | `/storage/stock-items/backup` | Create SQLite backup |

Existing master-data route `GET /companies/:companyId/stock-items` remains unchanged (live Tally read, no persistence).

## Desktop Features

- New **Stock Items** navigation view
- Total/with-unit/incomplete statistics, last sync, sync status, duration
- Search, pagination, manual sync, refresh, clear cache (confirmation)
- Non-blocking progress indicator during sync

## Validation Rules

Duplicate name/ID, missing required fields, incomplete unit warnings, invalid balances, whitespace normalization.

## Deletion and completeness policy

- **Deletion reconciliation:** explicitly **disabled** in 5B. Sync never soft-deletes items missing from a later extraction.
- **Extraction completeness:** `StockItemSyncResult.extractionCompleteness` distinguishes complete/failed/cancelled; port-level partial detection deferred.
- **Per-item data quality:** `dataQuality: incomplete` when BASEUNITS absent (not whole-extraction failure).

## Deferred scope (not 5B)

Inventory metadata, opening qty/rate/value separation, first-class stock groups/units, deletion reconciliation.

## Validation gates (separate concerns)

| Gate | Status |
|------|--------|
| Synthetic / integration tests | PASS (323 connector, 75 desktop) |
| Loopback live Tally 5B | PASS (Scenarios A–E); Scenario F operator-blocked |
| TD-009 authenticated LAN | Open — does not block loopback |
| 5A.1 fault scenarios 12–13 | Not performed |

## Live identity limitation (measured 2026-07-23)

Tally default `List of Stock Items` collection export returns **name-only minimal nodes** for this company (1,502 items): 0% GUID/AlterID/BASEUNITS in export. Parser retains GUID/AlterID when present. Identity precedence: `guid:` → `alter:` → `name:` slug. **Rename risk** applies to 100% of items until a safe richer export path is validated.

- First-class Stock Group / Unit masters (string references only)
- Inventory metadata: costing, batch, expiry, serial, godown allocation
- Opening quantity / rate / value decomposition (single balance field today)
- Automatic deletion reconciliation

## Recommended Next Milestone

**5C — Voucher Engine** or **5D — Inventory**
