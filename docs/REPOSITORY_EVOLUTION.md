# Repository Evolution

**Scope:** Native Android companion package layout in `apps/budcom_android`  
**Status:** Guidance — do not execute mass moves without an explicit refactor milestone  
**Related:** [Android ADR-001](./architecture/android/ADR-001-vertical-slice-architecture.md), [`PROJECT_CONSTITUTION.md`](./PROJECT_CONSTITUTION.md)

---

## Current structure

```text
com.budcom.android
├── app/
├── core/                 # shared primitives (network, common, database install, di, util)
├── feature/              # vertical slices (serverconfig, company, dashboard, …)
├── data/                 # scaffold markers only — not the home of real repositories
├── domain/               # scaffold markers only — not the home of real domain types
├── navigation/
└── ui/
```

Real feature code lives under `feature/<name>/{domain,data,presentation}` (dashboard currently has domain + presentation only).

Top-level `data/` and `domain/` contain placeholder `*Package.kt` files from the initial scaffold. They are **not** used by current features.

---

## Future structure

```text
com.budcom.android
├── app/
├── core/                 # transport, results, DI, database, utilities
├── feature/              # vertical slices
├── common/               # optional: truly shared non-core types moved deliberately
├── navigation/
└── ui/
```

Notes:

- `common/` appears only when multiple features need the same non-core type and `core/` is the wrong home.
- Top-level `data/` and `domain/` are expected to disappear or become empty once markers are removed in a dedicated cleanup.

---

## Why migration is deferred

1. **Feature delivery first.** Moving packages mid-milestone creates merge noise without user value.
2. **No behaviour change.** Empty markers are debt, not a runtime defect.
3. **AI safety.** Mass moves tempt agents into unrelated refactors.
4. **Boundaries already work.** Vertical slices are already the effective architecture; markers are documentation lag.

---

## Migration principles

1. **No unnecessary refactoring.** Do not move code “for cleanliness” during a feature PR.
2. **Move only when adding new features** (or during an explicitly scheduled cleanup milestone).
3. **Move the smallest unit.** Prefer relocating one shared model when a second feature needs it — not entire trees.
4. **Preserve dependency direction.** presentation → domain ← data; features may depend on other features’ domain ports, not their data implementations.
5. **Update docs in the same change** (`PROJECT_CONSTITUTION`, this file, Android README package map).
6. **Keep tests green** without weakening assertions.

---

## Suggested incremental path (when scheduled)

| Step | Action | Trigger |
| --- | --- | --- |
| 1 | Delete unused `*Package.kt` markers under top-level `data/` / `domain/` / empty `settings` if still unused | Cleanup milestone or first PR that touches those paths |
| 2 | Refresh `apps/budcom_android/README.md` package map to match reality | Same cleanup |
| 3 | Introduce `common/` only for a concrete shared type | Second feature needs the same domain model |
| 4 | Optional Gradle module split (`:core`, `:feature:*`) | Build times / boundary enforcement require it |

Until those triggers fire, leave the tree as-is and put new code in the correct vertical slice.
