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
| Active company SoR | Connector `GET /session` (`session.selectedCompany`) |
| Local selected-company id | Cache only (DataStore); never authoritative alone |
| Conflict rule | **System of Record first. Cache second.** Connector wins |

---

## Architecture guideline — System of Record first

**System of Record first. Cache second.**

Whenever Connector state and an Android local cache (DataStore, SharedPreferences, Room, in-memory) disagree about **Connector-owned** facts:

1. Treat the Connector response as authoritative.
2. Refresh or replace the local cache from Connector when reachable.
3. Never require the user to manually re-enter Connector state that already exists on the Connector (e.g. reselect a company after reinstall) when hydration is possible.
4. Device-only preferences (theme, local Connector base URL) remain device-local systems of record — they are not Connector session facts.

---

## Decision log

### 2026-07-28 — Connector Session is the authoritative source for active company state

**Status:** Accepted  
**Incident:** MVP-1 validation — Desktop had a company selected on Connector; fresh Android showed no active company / setup-like empty state because local `SelectedCompanyStore` was empty.  
**Fix (narrow):** `CompanyRepositoryImpl.restoreSelection()` hydrates from `GET /session` when local cache is blank; `RefreshDashboardUseCase` restores before reading local id.

#### Problem

- Fresh Android installations (and wiped app data) have **no** local `SelectedCompanyStore` entry.
- The Connector already maintains the active session (`ConnectorSession.selectedCompany`) after Desktop or another client selects a company.
- Android incorrectly treated its local DataStore cache as the authoritative “selected company,” so dashboard/session consumers concluded **NoCompany** despite a live Connector session.

#### Decision

- **Connector session is the source of truth** for active company (`GET /session` → `session.selectedCompany`).
- Android local storage (`SelectedCompanyStore` / DataStore) is a **cache only**.
- The cache **may be empty** (fresh install, cleared data, new device).
- The cache **may be stale** (Desktop changed company; another client cleared selection).
- The cache **must be refreshed from Connector when possible** (hydrate on restore / dashboard refresh when blank; persist ids returned from successful select/validate).

#### Consequences

- Fresh installs work without forcing manual reselection when Connector already has a company.
- Device changes / reinstalls stay consistent with Connector session.
- Multi-device support is simpler: one session SoR on Connector.
- Company selection stays consistent across Desktop and Android when hydration runs.

#### Regression prevention (mandatory)

Future implementations **must never**:

- assume local selected-company cache is authoritative without consulting Connector session when online;
- skip Connector session read/validation and invent “no company” solely from an empty cache;
- require manual company reselection after reinstall when `GET /session` already reports `selectedCompany`;
- treat DataStore / SharedPreferences / Room / memory mirrors of Connector session fields as systems of record.

Preferred pattern: expose Connector-backed reads (`getSession` / `readSelectedCompany`) for truth; use `observeSelectedCompanyId()` only as a **hydrated cache stream**, and hydrate before relying on it for operational UI.

#### Related residual watchpoints (review 2026-07-28 — do not refactor now)

These still **read the local cache stream** first. They are safe **after** dashboard/company restore hydrates the cache, but can still show empty/stale company if invoked **before** hydration or if hydration is skipped:

| Location | Mechanism | Notes |
| --- | --- | --- |
| `CompanySessionPortImpl.observeSelectedCompanyId` | DataStore via repository | Cache projection only |
| `CompanySessionPortImpl.validateSessionStatus` | Short-circuits `NoCompany` if local id blank | Does not call `GET /session` when blank |
| `ValidateDashboardSessionUseCase` | Local id gate | Same pattern |
| `ObserveDashboardContextUseCase` | Observes local id | UI can briefly show empty until hydrate |
| `ObserveSettingsSnapshotUseCase` | Local `companyId` | Facts refresh uses `readSelectedCompany()` (Connector) |
| Sync / Voucher / Search ViewModels & use cases | `observeSelectedCompanyId()` | Need company id from cache after hydrate |

**Not defects** (correct local SoR):

| Location | Mechanism | Why OK |
| --- | --- | --- |
| `ConnectorBaseUrlLocalStore` + in-memory `ConnectorBaseUrlProvider` | DataStore + memory | Device-configured Connector URL; not Connector session state |
| `ThemePreferencesLocalDataSource` | DataStore | Device UI preference |
| Master Data / browser in-memory list retention | Memory | Documented deferred Room; live lists remain network-backed |

**SharedPreferences:** none found in Android companion production code.  
**Room:** reserved; no domain Room SoR competing with Connector for active company.

---

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

### 2026-07-27 — Settings Foundation exposes only implemented configuration

**Status:** Accepted  
**Decision:** Settings is the single hub for user-configurable behavior that already exists: theme preference (new DataStore `app_settings`), Connector base URL via navigation to Server Configuration, company via Company Selection, Sync and Diagnostics entry points, and About metadata from BuildConfig/package. No placeholder or future-disabled options.  
**Context:** Base URL and company already had DataStore stores; theme had Compose-only system default with no persistence.  
**Consequences:** Theme applies immediately through `MainActivity` observation of `ThemePreferencesRepository`; Settings must not duplicate Server Configuration editing.

### 2026-07-27 — Production Validation requires live Connector and device

**Status:** Accepted  
**Decision:** Production Validation is complete only after automated gates **and** end-to-end verification against a reachable BudCom Connector on a usable Android device/emulator. Automated assemble/unit/androidTest-compile alone does not unlock Contact Intelligence.  
**Context:** 2026-07-27 run at tip `be0b960` passed automated gates; Connector probes on `:8080` failed; emulator AVD launched but remained `adb offline`.  
**Consequences:** Roadmap stays on Production Validation; Contact Intelligence remains blocked; evidence lives in `docs/PRODUCTION_VALIDATION.md`.

---

## Decision log template

### YYYY-MM-DD — Title

**Status:** Accepted | Superseded by …  
**Decision:** …  
**Context:** …  
**Consequences:** …
