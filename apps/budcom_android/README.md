# BUDCO Android

Native Android companion for BUDCO. Connects to the BudCom Connector REST API.

Coexists with `apps/budcom_mobile/` (Flutter). Do not remove or modify the Flutter app from this module.

Authoritative docs: [`docs/PRODUCT_SOUL.md`](../../docs/PRODUCT_SOUL.md), [`docs/PROJECT_CONSTITUTION.md`](../../docs/PROJECT_CONSTITUTION.md), [`docs/ROADMAP.md`](../../docs/ROADMAP.md).

## Milestone status

**Current:** Stock Item Browser (see roadmap).

**Completed in this module:** networking foundation, dynamic server configuration, health/readiness, company selection, session validation, operational dashboard (home), Master Data hub, Ledger Browser.

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
│   ├── masterdata      # hub + category identity
│   │   └── ledger      # Ledger Browser vertical slice
│   └── settings        # reserved
├── data/ / domain/     # scaffold markers only — prefer feature slices
├── navigation
└── ui
```

## Master Data / Ledger capabilities

| Capability | Status |
| --- | --- |
| Dashboard → Master Data → Ledgers navigation | Implemented |
| `GET /ledgers` list (session company required on Connector) | Implemented |
| Server-side `query` search (debounced) | Implemented |
| Page / pageSize pagination | Implemented |
| Manual refresh (pull-to-refresh) | Implemented |
| Loading / empty / error / offline UI | Implemented |
| Stock Items destination | Hub placeholder only (disabled) |
| Durable Room cache | Deferred |
| Ledger detail screen | Deferred |

Contract notes: [`docs/contracts/android-master-data-ledger.md`](../../docs/contracts/android-master-data-ledger.md).

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
