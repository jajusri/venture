# Project Constitution

**Status:** Normative — future AI assistants must read this before implementation  
**Scope:** BUDCO native Android companion (`apps/budcom_android`)  
**Working name:** BUDCO

Supporting ADRs explain individual decisions; they do not replace this document.

---

## Product identity and purpose

BUDCO is an intelligent Android business companion for the BudCom Connector. The ERP remains the system of record. The Connector implementation is the source of truth for routes, DTOs, behavior, and contracts. Never invent Connector endpoints or DTOs.

Product soul and audience: [PRODUCT_SOUL.md](PRODUCT_SOUL.md) (including Who We Serve and Design Philosophy).  
Permanent principles: [NON_NEGOTIABLES.md](NON_NEGOTIABLES.md).  
Long-term product direction: [VISION_2030.md](VISION_2030.md).  
This constitution governs **current** Android companion engineering. Vision documents may describe a broader multi-device future; they do not expand this constitution’s mandatory stack or scope until an explicit decision and roadmap milestone say so.

---

## Platform and stack (mandatory)

| Concern | Rule |
| --- | --- |
| Platform | Native Android |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| Feature shape | Feature-first vertical slices |
| DI | Hilt |
| Networking | Retrofit + OkHttp + Kotlin Serialization |
| UI state | StateFlow / immutable UI state |
| Preferences | DataStore |
| Structured DB | Room only when persistent structured data is actually required |
| Background | WorkManager only when durable background work is actually required |

---

## Layering and boundaries

1. Repository and use-case boundaries are required for business operations.
2. No business logic in Compose UI.
3. No Application → feature-data dependencies. Startup uses core ports (e.g. ConnectorBaseUrlHydrator).
4. Domain decisions have one authoritative implementation (e.g. deriveOperationalMode).
5. Cross-feature consumers (Dashboard) depend on stable public ports, not feature internals.
6. Dynamic Connector base URL: single Retrofit client; rewrite origin via interceptor; do not rebuild Retrofit on URL change.
7. Connector implementation is source of truth over stale OpenAPI.
8. DTOs owned in feature data layers; map to domain; no DTO invention; no duplicated repositories or models for the same concern.
9. No unnecessary framework or Gradle module proliferation.

---

## Errors and UI states

Use the shared AppError / AppResult model. Screens must support loading, empty, content, refreshing, error, and offline states where applicable. Partial success must not collapse into a single generic failure.

---

## Offline philosophy

Offline-aware: show cached/last-known usable data, mark limitations, bound retries, preserve safe selections. Editing ERP-backed state still requires a live Connector path defined by approved policy.

---

## Logging, security, privacy

- No production payload logging of accounting data.
- No embedded secrets in logs.
- Privacy-safe diagnostics only.

---

## Testing and Definition of Done

Tests are mandatory for feature work (unit + Compose/instrumentation as applicable).

A feature is done when: confirmed Connector contracts used; correct vertical-slice layering; ports respected; UI states covered; authoritative domain logic not duplicated; navigation intact; assemble/unit/androidTest compile (and runnable tests) pass; docs/roadmap updated when architecture or milestone status changes.

---

## AI implementation rules

1. Read NON_NEGOTIABLES, PRODUCT_SOUL, VISION_2030 (for direction), PROJECT_CONSTITUTION, DECISIONS, ROADMAP, relevant ADRs.
2. Inspect Connector implementation; confirm routes and DTOs.
3. Design one vertical slice; reuse ports; do not invent APIs.
4. Implement; test; update ROADMAP/CHANGELOG when appropriate.
5. Do not mass-move packages or start unscheduled features.

---

## Documentation updates

When architecture changes, update this constitution and/or DECISIONS.md and the relevant ADR. Keep README links accurate.
