# Decisions

**Status:** Frozen decisions for BUDCO Android companion  
**Scope:** `apps/budcom_android`

---

## Currently frozen

| Decision | Choice |
| --- | --- |
| Working name | BUDCO |
| Platform | Android native |
| Language | Kotlin |
| UI | Jetpack Compose |
| Design system | Material 3 |
| Architecture | Clean Architecture + MVVM |
| Delivery shape | Feature-first vertical slices |
| Networking | Retrofit + OkHttp + Kotlin Serialization |
| Preferences | DataStore |
| Database | Room when justified by a real feature |
| DI | Hilt |
| Background | WorkManager when durable work is required |
| Contracts | Connector implementation is the source of truth |
| Delivery | Vertical-slice delivery |
| Offline | Offline-aware behavior |
| Sync UX | Manual and user-visible sync before advanced automation |
| Domain logic | One authoritative implementation for domain decisions |
| Cross-feature deps | Stable public ports |
| Quality | Tests are mandatory |
| Structure | No unnecessary framework or module proliferation |
| Privacy | No production payload logging; no secrets in logs |

---

## Decision log

### 2026-07-27 — Master Data typed per-entity repositories

**Status:** Accepted  
**Decision:** Master Data uses typed repositories and DTOs per entity (e.g. `LedgerRepository`), not a universal untyped master-data repository.  
**Context:** Connector exposes distinct ledger and stock-item contracts with different fields and query params. Shared foundations are limited to navigation category identity and list UI conventions.  
**Consequences:** Stock Item Browser will add its own typed slice; shared abstractions grow only when at least three features justify reuse.

### 2026-07-27 — Durable Master Data cache deferred

**Status:** Accepted  
**Decision:** Ledger Browser is network-backed with in-memory UI retention on transient failure; Room durable cache is deferred.  
**Context:** No approved stale-data / offline-cache product behavior for Master Data yet; constitution forbids pretending offline availability without a real cache.  
**Consequences:** Offline shows an honest banner; retained list may remain visible after a successful prior load; Sync foundation / Room may revisit later.

### 2026-07-27 — Stock Item search follows Connector `query`

**Status:** Accepted  
**Decision:** Stock Item search uses Connector `GET /stock-items?query=` with the shared Master Data debounce constant (350ms).  
**Context:** Connector implements the same query max-length and pagination pattern as ledgers, with stock-specific sort fields (`category`, `baseUnit`).  
**Consequences:** Stock Item Browser remains a typed vertical slice; shared reuse is limited to list UI conventions, pagination metadata, and error mapping.

### 2026-07-27 — Shared Master Data list foundation (not a universal repository)

**Status:** Accepted  
**Decision:** Extract only shared list-browser conventions (`MasterDataUiError`, list Compose primitives, pagination VO, debounce/page-size constants). Keep typed per-entity repositories and DTOs.  
**Context:** Ledger and Stock Item browsers share UX states but different Connector fields and query params.  
**Consequences:** Further shared abstractions require a third justifying consumer.

### 2026-07-27 — Voucher API uses explicit company and date range

**Status:** Accepted  
**Decision:** Android Voucher Browser always sends Connector-required `company`, `from`, and `to` query parameters. Selected company comes from `CompanySessionPort`. Default date range is a client convenience (last 30 UTC days) and is editable in UI.  
**Context:** Unlike session-scoped `GET /ledgers`, voucher list routes require explicit company and ISO date bounds.  
**Consequences:** Missing company shows a clear UI error without inventing a company id; empty lists remain valid when snapshots were never synced for the period.

### 2026-07-27 — Voucher foundation keeps typed repository + deferred details UI

**Status:** Superseded by 2026-07-27 — Voucher Details UI delivered  
**Decision:** Ship typed `VoucherRepository` with list + `getVoucherDetails` ports; Voucher Browser is list-only; detail Compose destination is the next milestone.  
**Context:** Connector exposes `GET /api/v1/vouchers/:id`; browser navigation must not pretend details exist.  
**Consequences:** Details use case is ready for the next milestone without duplicate DTO mapping.

### 2026-07-27 — Voucher Details UI is read-only public contract

**Status:** Accepted  
**Decision:** Voucher Details renders only Connector `VoucherPublicDetails` fields (metadata, narration, ledger/inventory lines). Unknown/null values are shown honestly; no fabricated tax, attachments, or allocations.  
**Context:** Public detail API intentionally omits source identity and nested allocations.  
**Consequences:** Browser row opens Details; Sync/edit remain out of scope.

### 2026-07-27 — Universal Search is Android-side orchestration

**Status:** Accepted  
**Decision:** Universal Search Foundation fans out to typed public ports (`SearchLedgersPort`, `SearchStockItemsPort`, `SearchVouchersPort`) over existing Connector entity list/search routes. No cross-entity Connector endpoint is invented.  
**Context:** Connector exposes only entity-scoped search (`query` / `q`).  
**Consequences:** Search never depends on Retrofit/DTOs; future entities join via additional ports.

### 2026-07-27 — Universal Search bounded previews and partial success

**Status:** Accepted  
**Decision:** Results are grouped by entity with a fixed order (Ledgers → Stock Items → Vouchers), bounded preview page size (5), and “See all” into existing browsers. Partial section success is preserved; cross-entity relevance ranking is deferred.  
**Context:** First foundation milestone prioritizes trust and reuse over merged ranking.  
**Consequences:** Users see honest per-section empty/error states; browsers remain the full-list surfaces.

### 2026-07-27 — Universal Search voucher window is last 30 UTC days

**Status:** Accepted  
**Decision:** Voucher section uses the same client default date window as Voucher Browser (last 30 UTC days inclusive) and discloses it in UI.  
**Context:** Connector requires explicit `from`/`to` for voucher list/search.  
**Consequences:** Search must not imply all historical vouchers were searched; expanded ranges remain future work.

### 2026-07-27 — Sync Foundation is manual and Connector-backed

**Status:** Accepted  
**Decision:** Sync Foundation exposes manual Ledger and Stock Item sync against confirmed `POST /sync/ledgers` and `POST /sync/stock-items` contracts. Progress is Connector-reported counts only; mutating start calls are not auto-retried; WorkManager/background sync is deferred. Voucher sync is unavailable publicly and must be disclosed as such.  
**Context:** Connector sync POSTs are blocking; status/cancel/runs exist; no combined sync-all; no public voucher sync HTTP.  
**Consequences:** “Run available syncs” is sequential non-atomic orchestration; Dashboard consumes `ObserveSyncStatusPort` for in-process summary only.

### 2026-07-27 — Dedicated long-timeout Sync HTTP client

**Status:** Accepted  
**Decision:** Blocking sync POSTs use a `@SyncHttp`-qualified OkHttp/Retrofit stack with extended read timeout; default client timeouts remain unchanged for browse/API calls.  
**Context:** Default 30s/60s timeouts are insufficient for Tally-backed sync.  
**Consequences:** Only SyncApi uses the long-timeout client.

### 2026-07-27 — Diagnostics Foundation exposes only confirmed operational facts

**Status:** Accepted  
**Decision:** Diagnostics aggregates existing ports (`ConnectorStatusPort`, `CompanySessionPort`, `ObserveSyncStatusPort`) plus confirmed `GET /diagnostics/connection`. It never fabricates health, estimates readiness, or invents Connector version/build endpoints. Search/master-data/voucher sections disclose availability honestly when Connector diagnostics are absent.  
**Context:** No public `GET /version` or `GET /build`; connector version is on `/health`; extraction diagnostics and export/clear/restart actions are out of foundation scope.  
**Consequences:** Dashboard and Sync continue to own their UI; Diagnostics reuses shared ports without duplicate health polling loops.

---

## Decision log template

### YYYY-MM-DD — Title

**Status:** Accepted | Superseded by …  
**Decision:** …  
**Context:** …  
**Consequences:** …
