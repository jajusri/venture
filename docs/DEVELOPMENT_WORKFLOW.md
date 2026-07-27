# Development Workflow

**Scope:** Mandatory process for every Android companion feature in `apps/budcom_android`  
**Status:** Normative for humans and AI agents  
**Related:** [`PROJECT_CONSTITUTION.md`](./PROJECT_CONSTITUTION.md), [`PRODUCT_SOUL.md`](./PRODUCT_SOUL.md), [`ROADMAP.md`](./ROADMAP.md), [architecture ADRs](./architecture/README.md)

---

## Why this process exists

Accounting-adjacent software fails expensively when agents:

- invent Connector routes
- skip partial-success and offline states
- refactor unrelated packages mid-feature
- ship UI without tests
- lose track of roadmap status

This workflow forces **reuse, contract confirmation, and vertical delivery** before code lands.

---

## Mandatory steps

Every feature must follow this order:

### 1. Read `PROJECT_CONSTITUTION.md`

Confirm layering, networking, DTO, error, offline, testing, and Definition of Done rules.

### 2. Read `PRODUCT_SOUL.md`

Confirm the feature reduces user effort and identify which innovation pillars (if any) it advances. Question features that do not reduce effort.

### 3. Read `DECISIONS.md`

Do not reopen immutable decisions (stack, Connector source of truth, vertical slices, Material 3, testing mandatory) without an explicit superseding decision.

### 4. Read `ROADMAP.md`

Confirm the work is the current milestone (or an explicitly approved exception). Do not pull unscheduled pillars into production scope.

### 5. Read relevant ADRs

At minimum for Android work:

- [ADR-001 Vertical Slice](./architecture/android/ADR-001-vertical-slice-architecture.md)
- [ADR-003 Connector as Source of Truth](./architecture/android/ADR-003-connector-as-source-of-truth.md)
- [ADR-004 Offline Philosophy](./architecture/android/ADR-004-offline-philosophy.md)

Also read Dynamic Base URL / Product Vision ADRs when the feature touches host configuration or product-facing behaviour. Read Connector ADRs when the change depends on Connector reliability or ERP boundaries.

### 6. Inspect Connector implementation

Open the Connector handlers/services that own the behaviour. Implementation beats stale OpenAPI.

### 7. Confirm routes

Record exact methods and paths (for example `GET /health`, `GET /ready`). If missing, stop and report the gap.

### 8. Confirm DTOs

Record response fields, nullability, and failure shapes (including bodies on non-2xx where applicable). Do not invent fields.

### 9. Design

Write a short design covering:

- packages touched (prefer one vertical slice)
- repositories/use cases to reuse vs create
- UI states including offline / partial success
- tests planned
- navigation impact

Prefer aggregation use cases over duplicate networking.

### 10. Implement one vertical slice

Implement only the approved milestone slice. No unrelated refactors, package moves, or “while we’re here” cleanups (see [`REPOSITORY_EVOLUTION.md`](./REPOSITORY_EVOLUTION.md)).

### 11. Add tests

Unit tests for use cases/ViewModels/repositories as applicable. Compose/instrumentation tests for critical UI states. Do not weaken tests to pass.

### 12. Update `ROADMAP.md`

Mark milestone progress accurately (in progress / completed) when the Definition of Done is met.

### 13. Update `CHANGELOG.md` if appropriate

Record user-visible or operator-visible changes in the repository changelog style already used by the monorepo.

---

## Definition of Done reminder

A feature is not done until constitution DoD is satisfied: confirmed contracts, correct layering, explicit error/partial states, tests, navigation integrity, and relevant Gradle verification for the Android module.

---

## Explicitly out of scope unless requested

- Mass package moves
- Gradle module splits
- Inventing Connector endpoints
- Implementing unscheduled product-soul pillars
- Broad refactors disguised as feature work
