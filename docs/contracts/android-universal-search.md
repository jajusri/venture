# Confirmed contracts — Universal Search Foundation (Android)

**Date:** 2026-07-27  
**Source of truth:** Connector entity list/search routes + Android typed feature ports  
**Purpose:** Document the Android-side Universal Search foundation (not a Connector universal-search API)

---

## Scope

Universal Search Foundation provides **one search entry point** that orchestrates existing typed entity searches:

1. Ledgers
2. Stock Items
3. Vouchers

There is **no** Connector cross-entity `/search` route. Android fans out to confirmed entity routes.

---

## Supported entities and Connector routes

| Section | Connector route | Search param | Company / session |
| --- | --- | --- | --- |
| Ledgers | `GET /ledgers` | `query` (max 128) | Session-selected company (Connector in-process) |
| Stock Items | `GET /stock-items` | `query` (max 128) | Session-selected company |
| Vouchers | `GET /api/v1/vouchers` (alias `/api/v1/vouchers/search`) | `q` (max 128) | Explicit `company` + required `from` / `to` |

Response fields used for result summaries are the same public list fields already mapped by each browser.

---

## Query semantics (Android)

| Rule | Value |
| --- | --- |
| Debounce | 350 ms (shared with Master Data browsers) |
| Blank query | No network calls; idle hint |
| Minimum length | 1 non-blank character after trim (Connector has no min length) |
| Max length | 128 |
| Preview page size | 5 per section |
| Section order | Ledgers → Stock Items → Vouchers (fixed) |
| Cancellation | New query cancels obsolete debounce/search jobs |
| Partial failure | Successful sections remain visible when siblings fail |

---

## Voucher date-window limitation

Voucher search **requires** `from` and `to`. Universal Search uses the same client default as Voucher Browser:

- Last **30 UTC calendar days** inclusive (`VoucherDateRangeDefaults`)

The Search UI discloses this window (e.g. “Vouchers from {from} to {to} (last 30 days)”).  
It does **not** imply all historical vouchers were searched.

---

## Result navigation

| Result / action | Destination |
| --- | --- |
| Ledger row / See all Ledgers | Ledger Browser with query applied (`q` nav arg) |
| Stock Item row / See all Stock Items | Stock Item Browser with query applied |
| Voucher row | Voucher Details (`GET /api/v1/vouchers/:id`) |
| See all Vouchers | Voucher Browser with query applied (default 30-day range) |

Ledger and Stock Item detail screens are out of scope; browsers are the supported destinations.

---

## Partial failure and offline

- Device offline banner uses shared Master Data offline messaging.
- Per-section errors show independently with retry.
- Full retry re-runs the active normalized query across all sections.
- Search does not claim durable cache; results are live Connector responses for the current query.

---

## Architecture

```text
feature/search/
  domain/          SearchQuery, SearchHit, ExecuteUniversalSearchUseCase
  presentation/    UniversalSearch ViewModel + Compose UI

Public ports (owned by entity features):
  SearchLedgersPort
  SearchStockItemsPort
  SearchVouchersPort
```

Search depends only on those ports + `CompanySessionPort`. No Retrofit/DTO access from Search.

---

## Future extension points

Companies, Contacts, Documents, and other entities may add a typed public search port and a new `SearchSection` without rewriting the fan-out model.

---

## Explicit non-goals

- Contact / Phone Book search
- AI / semantic / vector search
- OCR / Document search
- Company search
- Cross-entity relevance ranking
- Search history / suggestions / voice
- Room FTS / background indexing
- Sync
- Inventing a Connector universal-search endpoint
