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

## Decision log template

### YYYY-MM-DD — Title

**Status:** Accepted | Superseded by …  
**Decision:** …  
**Context:** …  
**Consequences:** …
