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

### 2026-07-27 — Ledger search follows Connector `query`

**Status:** Accepted  
**Decision:** Ledger search uses Connector `GET /ledgers?query=` with presentation debounce (350ms); not client-only filtering of a full ERP catalog.  
**Context:** Connector implements server-side search over its local ledger cache.  
**Consequences:** Empty results after sync-less Connector state are valid; Universal Search remains a later milestone.

---

## Decision log template

### YYYY-MM-DD — Title

**Status:** Accepted | Superseded by …  
**Decision:** …  
**Context:** …  
**Consequences:** …
