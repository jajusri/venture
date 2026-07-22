# Budcom — Project Progress Tracker

> **Maintenance rule:** Update this file immediately after every completed milestone.
> Do not mark a milestone complete until verification (tests, build, read-only checks) has passed.

---

## Project name

**Budcom** — Android-first, read-only Tally companion (Business OS MVP 1)

---

## Current version

**0.1.0** (Milestone 0 foundation)

| Component | Version |
|-----------|---------|
| `budcom_core` | 0.1.0 |
| `budcom_contracts` | 0.1.0 |
| `budcom_mobile` | 0.1.0+1 |
| `@budcom/connector` | 0.1.0 |
| Connector API (`openapi`) | 1.0.0 |

---

## Current milestone

**Milestone 1 — Connector Proof of Concept** (connector core foundation complete; Tally integration not started)

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

---

## In-progress milestone

**Milestone 1 — Connector Proof of Concept** (remaining: Tally adapter, normalization, live companies endpoint, pairing, Flutter connection screen)

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
| **Milestones complete** | 1.5 / 6 (M0 + M1 connector foundation) |
| **Overall completion** | **~25%** |
| **MVP 1 acceptance checklist** | 0 / 23 items (not started) |

---

## Last verification date

**2026-07-22**

| Check | Result |
|-------|--------|
| `melos run analyze` | ✅ Pass |
| `melos run test` | ✅ 12/12 |
| Connector tests | ✅ 6/6 |
| Contract tests | ✅ 5/5 |
| `flutter build apk --debug` | ✅ Pass |
| Read-only / Tradon naming audit | ✅ Pass |
| Connector `npm run build` | ❌ Fail (tsconfig `rootDir`) |

---

## Architecture status

| Area | Status | Notes |
|------|--------|-------|
| Monorepo structure | ✅ Complete | Melos workspace, 3 Dart packages + connector |
| Layered mobile app | ✅ Scaffolded | presentation → domain → data interfaces |
| Domain model | ✅ Complete | Entities, Money, Dr/Cr, capabilities, events |
| Connector contract | ✅ Complete | OpenAPI 3.1 v1.0.0 |
| Connector implementation | 🟡 Core foundation | DI, logging, health, placeholders; no Tally yet |
| Tally adapter | ⬜ Not started | Milestone 1 |
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
| `@budcom/connector` | 18 | ✅ Pass |
| `@budcom/contract-tests` | 5 | ✅ Pass |
| **Total** | **41** | **✅ All passing** |

**Not yet covered:** Integration tests against Tally, migration tests, golden/UI tests, security tests (Milestone 1–5).

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

_None — Milestone 1 can begin once Tally test environment access is confirmed._

---

## Next recommended task

**Implement Tally adapter and live connector proof of concept** (remaining Milestone 1)

1. Confirm Tally XML/HTTP interface for target test installation
2. Implement Tally adapter behind normalization interfaces
3. Wire `GET /health` to report real Tally reachability
4. Implement `GET /companies` with one test company
5. Add minimal device pairing flow
6. Build Flutter connection setup screen
7. Add contract tests against live connector

**Acceptance:** Developer build connects to one test company.

---

## Last updated

**2026-07-22** — Milestone 1 connector core foundation complete (18 tests, build + lint pass).
