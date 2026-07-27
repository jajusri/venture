# BUDCO Android

Native Android companion for BUDCO. Connects to the BudCom Connector REST API.

Coexists with `apps/budcom_mobile/` (Flutter). Do not remove or modify the Flutter app from this module.

Authoritative docs: [`docs/PRODUCT_SOUL.md`](../../docs/PRODUCT_SOUL.md), [`docs/PROJECT_CONSTITUTION.md`](../../docs/PROJECT_CONSTITUTION.md), [`docs/ROADMAP.md`](../../docs/ROADMAP.md).

## Milestone status

**Current:** Production Validation. **Completed here:** Settings Foundation.

**Completed in this module:** networking foundation, dynamic server configuration, health/readiness, company selection, session validation, operational dashboard (home), Master Data hub + Ledger/Stock Item browsers, Voucher foundation + Browser + Details, Universal Search across ledgers/stock items/vouchers, manual Sync Foundation (Ledgers + Stock Items), Diagnostics Foundation, Settings Foundation.

## Stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM + vertical slices |
| DI | Hilt |
| Networking | Retrofit + OkHttp + Kotlin Serialization |
| Preferences | DataStore |
| Local DB | Room (deps ready; entities when a feature requires them) |
| Background | WorkManager (workers when durable work is required) |
| Navigation | AndroidX Navigation Compose |

## Package structure

```text
com.budcom.android
├── app                 # Application, MainActivity
├── core
│   ├── common          # AppResult / AppError
│   ├── network         # OkHttp / Retrofit / dynamic base URL
│   ├── startup         # Process-start ports (e.g. URL hydrator)
│   ├── database        # Room install site
│   ├── di
│   └── util
├── feature
│   ├── serverconfig    # URL + health/readiness (+ ConnectorStatusPort)
│   ├── company         # discovery/session (+ CompanySessionPort)
│   ├── dashboard       # operational home (domain + presentation)
│   ├── masterdata      # hub, shared list conventions, pagination VO
│   │   ├── ledger      # Ledger Browser (+ SearchLedgersPort)
│   │   └── stockitem   # Stock Item Browser (+ SearchStockItemsPort)
│   ├── voucher         # Voucher foundation + Browser + Details (+ SearchVouchersPort)
│   ├── search          # Universal Search Foundation (orchestrates typed ports)
│   ├── sync            # Manual Sync Foundation (+ ObserveSyncStatusPort)
│   ├── diagnostics     # Diagnostics Foundation (+ ConnectionDiagnosticsPort)
│   └── settings        # Settings Foundation (+ ThemePreferencesRepository)
├── data/ / domain/     # scaffold markers only — prefer feature slices
├── navigation
└── ui
```

## Master Data capabilities

| Capability | Status |
| --- | --- |
| Dashboard → Master Data → Ledgers / Stock Items | Implemented |
| Shared list UI / error / debounce defaults | Implemented |
| Durable Room cache | Deferred |
| Detail screens | Deferred |

Contract notes: [`docs/contracts/android-master-data-ledger.md`](../../docs/contracts/android-master-data-ledger.md).

## Voucher capabilities

| Capability | Status |
| --- | --- |
| Dashboard → Vouchers | Implemented |
| Typed foundation (`VoucherQuery`, repository, details port) | Implemented |
| `GET /api/v1/vouchers` list (required `company`, `from`, `to`) | Implemented |
| Free-text `q` search (debounced) | Implemented |
| Page / pageSize pagination | Implemented |
| Editable date range (client default: last 30 UTC days) | Implemented |
| Loading / empty / error / offline / refresh | Implemented |
| Dashboard → Vouchers → Details | Implemented |
| `GET /api/v1/vouchers/:id` details (metadata, narration, ledger/inventory lines) | Implemented |
| Sync UI / create / edit / delete | Out of scope |

Contract notes: [`docs/contracts/android-voucher-api.md`](../../docs/contracts/android-voucher-api.md).

## Universal Search capabilities

| Capability | Status |
| --- | --- |
| Dashboard → Search | Implemented |
| Grouped results: Ledgers, Stock Items, Vouchers | Implemented |
| Debounced query; blank ignored; obsolete requests cancelled | Implemented |
| Bounded preview (5) + See all → existing browsers | Implemented |
| Voucher section: last 30 UTC days (disclosed) | Implemented |
| Partial section success / per-section retry | Implemented |
| Result → Ledger/Stock browsers or Voucher Details | Implemented |
| Contact / AI / OCR / ranking / history | Out of scope |

Contract notes: [`docs/contracts/android-universal-search.md`](../../docs/contracts/android-universal-search.md).

**Limitations:** Not ERP-wide search. Vouchers are limited to the disclosed date window. No Ledger/Stock detail destinations yet.

## Sync capabilities

| Capability | Status |
| --- | --- |
| Dashboard → Sync | Implemented |
| Manual Ledger sync (`POST /sync/ledgers`) | Implemented |
| Manual Stock Item sync (`POST /sync/stock-items`) | Implemented |
| Status poll / cancel / recent runs | Implemented |
| ObserveSyncStatusPort for Dashboard | Implemented |
| Run available syncs (sequential, non-atomic) | Implemented |
| Public voucher sync | Unavailable (Connector has no HTTP route) |
| WorkManager / automatic sync | Out of scope |

Contract notes: [`docs/contracts/android-sync-api.md`](../../docs/contracts/android-sync-api.md).

**Limitations:** Sync summary is in-process (refreshed from Connector status/runs). Progress percentages are never fabricated. Abandoned Connector runs after restart appear as interrupted history, not live progress.

## Diagnostics capabilities

| Capability | Status |
| --- | --- |
| Dashboard → Diagnostics | Implemented |
| Health / readiness via `ConnectorStatusPort` | Implemented |
| `GET /diagnostics/connection` | Implemented |
| Company / session via `CompanySessionPort` | Implemented |
| Sync summary via `ObserveSyncStatusPort` | Implemented |
| Application identity from `BuildConfig` | Implemented |
| Honest search / master-data / voucher notes | Implemented |
| Refresh diagnostics / recheck health & readiness | Implemented |
| Export logs / clear cache / restart Connector | Out of scope |

Contract notes: [`docs/contracts/android-diagnostics.md`](../../docs/contracts/android-diagnostics.md).

**Limitations:** Does not invent Connector version/build endpoints. Does not estimate readiness. Partial section failures remain visible instead of collapsing into one generic error.

## Settings capabilities

| Capability | Status |
| --- | --- |
| Dashboard → Settings | Implemented |
| Theme System / Light / Dark (DataStore `app_settings`) | Implemented |
| Immediate theme apply (no restart) | Implemented |
| Connection summary + open Server Configuration | Implemented |
| Company summary + open Company Selection | Implemented |
| Sync summary + open Sync | Implemented |
| Open Diagnostics | Implemented |
| About (BuildConfig + package; Connector version when probed) | Implemented |
| Placeholder / future-disabled options | Forbidden |
| Second Base URL editor | Forbidden (reuse Server Configuration) |

Contract notes: [`docs/contracts/android-settings.md`](../../docs/contracts/android-settings.md).

## Architecture rules

- Composables render presentation state only.
- Cross-feature consumers use stable public ports.
- Do not invent Connector endpoints.
- Prefer sealed UI state and `AppResult` at repository boundaries.
- See `docs/DEVELOPMENT_WORKFLOW.md` before implementing features.

## BuildConfig

| Field | Purpose |
| --- | --- |
| `CONNECTOR_BASE_URL` | Default `http://10.0.2.2:8080/` |
| `NETWORK_LOGGING_ENABLED` | Debug only |
| `APP_NAME` | Display label |

## Prerequisites

- JDK 17+
- Android SDK Platform 35
- `local.properties` with `sdk.dir` (do not commit)

## Commands

From `apps/budcom_android`:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
```
