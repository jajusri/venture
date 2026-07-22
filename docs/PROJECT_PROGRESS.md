# Budcom — Project Progress Tracker

> **Maintenance rule:** Update this file immediately after every completed milestone.
> Do not mark a milestone complete until verification (tests, build, read-only checks) has passed.

---

## Project name

**Budcom** — Android-first, read-only Tally companion (Business OS MVP 1)

---

## Current version

**0.2.0** (Milestone 2 Tally communication layer — uncommitted)

| Component | Version |
|-----------|---------|
| `budcom_core` | 0.1.0 |
| `budcom_contracts` | 0.1.0 |
| `budcom_mobile` | 0.1.0+1 |
| `@budcom/connector` | 0.2.0 |
| Connector API (`openapi`) | 1.0.0 |

---

## Current milestone

**Milestone 2 — Tally Communication Layer** (complete — awaiting review; not committed/tagged)

---

## Completed milestones

### Milestone 0 — Architecture Foundation ✅

**Completed:** 2026-07-22

**Goal:** Repository skeleton, shared domain, connector contract, CI — no business features.

| Deliverable | Status |
|-------------|--------|
| Monorepo layout with Melos | ✅ |
| `budcom_core` domain package | ✅ |
| `budcom_contracts` DTO package | ✅ |
| OpenAPI connector contract v1 | ✅ |
| Connector service skeleton + read-only middleware | ✅ |
| Flutter app layered structure + home shell | ✅ |
| Feature module stubs | ✅ |
| Capability and feature-flag framework | ✅ |
| Domain event bus interface | ✅ |
| CI workflow | ✅ |
| Foundation tests | ✅ |

**Verification:** Passed 2026-07-22 (23/23 automated tests, analyzer clean, debug APK built)

### Milestone 1 — Connector Core Foundation ✅

**Completed:** 2026-07-22

**Goal:** Tally Connector project foundation — structure, config, logging, DI, health, graceful shutdown, placeholder services. No Tally/sync/XML/DB/licensing business logic.

| Deliverable | Status |
|-------------|--------|
| Complete connector folder structure | ✅ |
| TypeScript, ESLint, Prettier, Vitest config | ✅ |
| Configuration management | ✅ |
| Structured logging | ✅ |
| Centralized error handling | ✅ |
| Application bootstrap + graceful shutdown | ✅ |
| Dependency injection / service registration | ✅ |
| Health-check endpoint (aggregates service status) | ✅ |
| Placeholder service interfaces + stubs | ✅ |
| Unit + integration test scaffolding | ✅ |
| Connector build (`npm run build`) | ✅ |

**Verification:** Passed 2026-07-22 (18/18 connector tests, lint clean)

### Milestone 2 — Tally Communication Layer ✅

**Completed:** 2026-07-22 (awaiting user review — working tree uncommitted)

**Goal:** Production-grade Tally HTTP/XML communication — transport, connection management, company discovery, diagnostics. No voucher/ledger sync, scheduler, licensing, or database persistence.

| Deliverable | Status |
|-------------|--------|
| Tally HTTP/XML transport | ✅ |
| XML request builder | ✅ |
| XML response parser framework | ✅ |
| Connection manager with state machine | ✅ |
| Company discovery | ✅ |
| Connection diagnostics endpoint | ✅ |
| Retry policy + timeout handling | ✅ |
| Automatic reconnect | ✅ |
| Connection pooling | ✅ |
| Config enhancements (Tally tuning) | ✅ |
| Structured logging + error handling | ✅ |
| Health endpoint with real Tally ping | ✅ |
| `GET /companies` live (mock-tested) | ✅ |
| Unit + integration tests (mocked Tally) | ✅ |
| Connector README updated | ✅ |

**Verification:** Passed 2026-07-22 (45/45 connector tests, lint clean, build clean)

---

## In-progress milestone

_None — Milestone 2 complete; awaiting user review before commit/tag._

---

## Upcoming milestones

| Milestone | Name | Goal |
|-----------|------|------|
| **M1** | Connector Proof of Concept | Prove read-only Tally connectivity on a test company |
| **M2** | Company and Ledger Reading | Accurate ledger data with local cache |
| **M3** | Vouchers and Search | Voucher list/detail and universal search |
| **M4** | PDF and Sharing | Generate and share business documents |
| **M5** | Internal Launch Hardening | Daily-use readiness for Jaju Sanitations |

See [architecture/milestones.md](./architecture/milestones.md) for full acceptance criteria.

---

## Overall completion

| Metric | Value |
|--------|-------|
| **Milestones complete** | 2 / 6 (M0 + M1 + M2 connector communication layer) |
| **Overall completion** | **~35%** |
| **MVP 1 acceptance checklist** | 0 / 23 items (not started) |

---

## Last verification date

**2026-07-22**

| Check | Result |
|-------|--------|
| Connector `npm run lint` | ✅ Pass |
| Connector `npm run build` | ✅ Pass |
| Connector `npm test` | ✅ 45/45 |
| `@budcom/contract-tests` | ✅ 5/5 (unchanged) |

---

## Architecture status

| Area | Status | Notes |
|------|--------|-------|
| Monorepo structure | ✅ Complete | Melos workspace, 3 Dart packages + connector |
| Layered mobile app | ✅ Scaffolded | presentation → domain → data interfaces |
| Domain model | ✅ Complete | Entities, Money, Dr/Cr, capabilities, events |
| Connector contract | ✅ Complete | OpenAPI 3.1 v1.0.0 |
| Connector implementation | ✅ M2 communication layer | DI, logging, health, Tally transport, discovery, diagnostics |
| Tally adapter | ✅ M2 foundation | HTTP/XML transport, connection manager, company discovery |
| Local database | ⬜ Not started | Milestone 2 |
| Feature modules | 🟡 Stubs only | connection, ledgers, vouchers, search, pdf, diagnostics |
| Cloud backend | ⬜ Reserved | Optional per spec; `backend/` placeholder only |

**Reference:** [architecture/overview.md](./architecture/overview.md)

---

## Security status

| Control | Status | Notes |
|---------|--------|-------|
| Read-only Tally enforcement | ✅ Foundation | Middleware + OpenAPI contract tests |
| Capability-based permissions | ✅ Framework | 11 read/view/export capabilities defined |
| Device pairing | 🟡 Stub only | `POST /device/pair` returns 501 |
| Encryption / keystore | ⬜ Not started | Milestone 1–2 |
| Audit logging | 🟡 Entity only | `AuditEvent` defined; no persistence |
| Secrets in logs | ✅ N/A | No logging of financial data yet |
| Information fence | 🟡 Framework | Capability evaluator ready; roles later |

---

## Testing status

| Suite | Tests | Status |
|-------|-------|--------|
| `budcom_core` | 9 | ✅ Pass |
| `budcom_contracts` | 2 | ✅ Pass |
| `budcom_mobile` | 1 | ✅ Pass |
| `@budcom/connector` | 45 | ✅ Pass |
| `@budcom/contract-tests` | 5 | ✅ Pass |
| **Total** | **68** | **✅ All passing (connector verified)** |

**Not yet covered:** Live Tally integration tests, migration tests, golden/UI tests, security tests (Milestone 3+).

---

## Documentation status

| Document | Status |
|----------|--------|
| Root `README.md` | ✅ |
| Architecture docs (`docs/architecture/`) | ✅ |
| OpenAPI spec (`docs/openapi/connector-v1.yaml`) | ✅ |
| ADR 001 — Milestone 0 stack | ✅ |
| Product spec (`.docx`) | ✅ (authoritative baseline) |
| Milestone delivery plan | ✅ |
| Connector README | ✅ |
| Mobile README | ✅ |
| Decision log (ongoing) | 🟡 Template only |

---

## Known issues

| ID | Severity | Description |
|----|----------|-------------|
| KI-001 | Low | ~~Connector `npm run build` fails~~ — **Fixed** in M1 foundation (`tsconfig.build.json`) |
| KI-002 | Low | Flutter and Melos not on default system PATH (installed at `C:\src\flutter` and Pub cache) |
| KI-003 | Info | Open M1 decisions: Tally access method, pairing flow, connector deployment form (see ADR 001) |

---

## Current blockers

_None — Milestone 2 complete; awaiting user review and commit approval._

---

## Next recommended task

**Milestone 3 — Company and Ledger Reading** (after M2 commit/tag)

1. Implement ledger list/detail normalization from Tally XML
2. Add local database persistence for cached reads
3. Wire `GET /companies/:id/ledgers` endpoints
4. Add contract tests for ledger DTOs
5. Build Flutter ledger browsing screens

**Acceptance:** Accurate read-only ledger data for one test company with local cache.

---

## Last updated

**2026-07-22** — Milestone 2 Tally communication layer complete (45 tests, build + lint pass; uncommitted).
