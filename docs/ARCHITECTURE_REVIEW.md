# Android Architecture Review

**Date:** 2026-07-27  
**Scope:** Read-only review of `apps/budcom_android`  
**Code changes:** None (documentation only)

This review supports long-term governance. Findings are classified by severity. Cosmetic style issues are omitted.

---

## Summary

Inside each feature, Clean Architecture is largely followed (`presentation → domain ← data`). Compose screens primarily render state. Health networking is **not** duplicated by the dashboard; it reuses `ConnectorConfigRepository`. The main pressures are **cross-feature dashboard coupling**, **diverged operational-mode derivation**, **Application → feature data hydration**, and **stale scaffold packages/README**.

---

## Package consistency

**Actual tree**

```text
app/
core/{common,database,di,network,util}
feature/
  serverconfig/{data,domain,presentation}
  company/{data,domain,presentation}
  dashboard/{domain,presentation}    # aggregator; no data/
  settings/                          # marker only
data/                                # unused Package.kt markers
domain/                              # unused Package.kt markers
navigation/
ui/{components,theme}
```

| Finding | Severity | Notes |
| --- | --- | --- |
| Top-level `data/` / `domain/` unused while features own real code | Medium | Misleading for new work; migration deferred per `REPOSITORY_EVOLUTION.md` |
| `feature/settings` empty marker | Low | Acceptable reservation |
| Dashboard lacks `data/` by design | Low | Documented as composition/aggregation feature |
| Hilt modules live under `serverconfig/data/di` vs `company/data/repository` | Medium | Inconsistent placement |

---

## Naming consistency

| Finding | Severity | Notes |
| --- | --- | --- |
| User-facing **BudCom** vs types **Budcom*** vs package `com.budcom` vs product docs **BUDCO** | Medium | Documented dual usage; UI spelling not locked |
| No `Tradon` leftovers observed in Android sources reviewed | — | Good |

---

## Dependency direction

| Rule | Status |
| --- | --- |
| Per-feature presentation → domain ← data | Mostly yes |
| Compose without business logic | Mostly yes |
| company ↔ serverconfig isolation | Yes (no mutual imports) |
| dashboard → company.domain + serverconfig.domain | Yes (expected aggregator pressure) |
| `BudcomApplication` → `serverconfig.data.local` | High smell |
| Top-level data/domain imports | None observed |

---

## Feature isolation

| Finding | Severity | Notes |
| --- | --- | --- |
| Dashboard deep-depends on company + serverconfig domain models/repos | High | Blocks clean module extraction later |
| Dashboard ViewModel also injects repos and calls `testConnection()` directly | Medium | Splits orchestration with use cases |
| Health HTTP reused via repository (not duplicated) | — | Correct |

---

## Technical debt

| Finding | Severity | Notes |
| --- | --- | --- |
| `operationalMode()` in domain vs `derivedOperationalMode()` in ViewModel can diverge | High | Same snapshot may yield different banners |
| `Application` hydrates URL via feature data + `runBlocking` | High | Bypasses domain port; cold-start risk |
| ServerConfig has no use cases; Company use cases are thin; unused clear-session use case | Medium | Inconsistent pattern |
| UI screens import connector domain health/readiness models | Medium | Wire/domain shape leaks into Compose |
| Dual `ApiResult` / `AppResult` mapping repeated | Medium | Boundary is correct; helpers could centralize later |
| Room deps + empty `DatabaseModule`; WorkManager wired without workers | Low | Intentional scaffolding |
| Android module README still “scaffold only” | High (docs debt) | Misleads contributors; code untouched this task |

---

## Duplicated code

| Finding | Severity | Notes |
| --- | --- | --- |
| Operational mode derivation duplicated | High | See above |
| `AppError` → UI error mapping per feature | Medium | Candidate shared mapper |
| Remote data source `safeApiCall` / retry / error-body patterns similar across features | Low | Extract when a third similar client appears |
| Preview sample health fixtures duplicated in screens | Low | Test/preview only |

---

## Unused scaffolding

| Finding | Severity | Notes |
| --- | --- | --- |
| Nine `*Package.kt` markers under top-level data/domain + settings | Low | Safe to delete in cleanup milestone |
| Duplicate `data/remote/Package.kt` and `RemoteDataPackage.kt` | Low | Marker noise |
| Room / WorkManager without consumers | Low | Keep until first offline/sync feature |

---

## Future migration opportunities

1. Delete unused package markers; refresh Android README package map.
2. Single domain function for dashboard operational mode.
3. Hydrate Connector base URL through domain/repository port from `Application`.
4. Narrow dashboard dependencies to stable public ports (or a connector-status facade).
5. Standardize use-case policy and Hilt module placement.
6. Introduce `common/` only when a real shared type needs a home (see evolution doc).
7. Optional Gradle feature modules after ports are stable.

---

## Severity legend

- **Critical:** Correctness, security, or architectural cycle risk requiring immediate stop-ship attention.
- **High:** Likely to cause incorrect UX, costly coupling, or contributor mistakes soon.
- **Medium:** Maintainability / consistency debt to schedule before broad feature expansion.
- **Low:** Cleanup when touching adjacent code or during an explicit hygiene milestone.

---

## Remediation status (2026-07-27)

Phase 4 of the Dashboard stabilization checkpoint targets High findings:

| Item | Target | Status |
| --- | --- | --- |
| A. Unify operational-mode derivation | `deriveOperationalMode` only | Addressed in dashboard domain |
| B. Application → feature-data coupling | `ConnectorBaseUrlHydrator` core port | Addressed |
| C. Narrow Dashboard dependencies | `ConnectorStatusPort` / `CompanySessionPort` | Addressed |
| D. ViewModel/use-case boundaries | Probe/Refresh/Validate/Observe use cases | Addressed |
| E. Domain health models in Compose | Presentation mapping only | Addressed |

Medium/Low items (error-mapper centralization, package markers, Room scaffolding) remain deferred per `REPOSITORY_EVOLUTION.md`.
