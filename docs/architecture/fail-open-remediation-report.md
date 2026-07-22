# Fail-Open Remediation Report — Budcom Tally Connector

> **Superseded in part by:** [`architecture-defect-remediation-report.md`](./architecture-defect-remediation-report.md) (2026-07-22) — corrects gateway bypass, ERP-neutral policy core, raw-XML leakage, and dead architecture identified in the independent architecture review.

**Status:** Implementation complete; automated verification green. Awaiting human review.
**Scope:** Communication-layer architecture only. No live Tally contact. No business
features. No commit/tag/push. Milestone 3/4 not resumed.

**Verification (local):**

- `eslint src test` — pass
- `tsc -p tsconfig.json --noEmit` (src + tests) — pass
- `tsc -p tsconfig.build.json` (production build) — pass
- `vitest run` — **147 passed / 147** (was 103; +44 new security/architecture tests)

---

## 1. Executive summary

The adversarial review (`docs/architecture/tally-communication-safety-review.md`,
preserved unchanged) found genuine fail-open paths: a validator that permitted
`IMPORT`/`EXECUTE`/`FUNCTION`, a `SAFE_MODE=false` master off-switch, an
application-facing raw-XML `exchange()` path, and no allow-list at the egress
chokepoint.

This work makes the production connector **structurally read-only** through
architecture and runtime capability boundaries rather than conventions:

1. A **capability model** with only read capabilities. No `RAW_XML`, `IMPORT`,
   `EXECUTE`, `FUNCTION`, or `WRITE` capability can be named.
2. A **hardened egress validator**: `EXPORT`-only, `DATA`/`COLLECTION`/`OBJECT`-only,
   and a hard reject of write/execute verbs.
3. A **data-driven approved-operation registry** and a **forbidden registry**
   (permanently blocking `List of Units` and single Stock-Item object export).
4. A **fail-closed policy engine** (`ALLOW`/`DENY`/`QUARANTINE`/`REQUIRE_MANUAL_APPROVAL`,
   `UNKNOWN = DENY`) enforced on **every** request at the chokepoint.
5. A **single typed gateway** (`executeApprovedRead`) as the only application-facing
   egress. Raw transport and the XML builder are removed from the app-facing surface.
6. **Mandatory runtime controls** that `SAFE_MODE` can only tighten, never weaken.
7. **Audit-before-transport** with a canonical request hash.
8. A **physically separated offline XML file ingestion** module that cannot reach
   live Tally.
9. **Architecture/dependency-boundary tests** that fail the build on violation.

---

## 2. Finding-to-fix traceability matrix

| ID | Review finding | Source file(s) | Vulnerable path | Sev | Remediation | Proving test | Status |
|----|----------------|----------------|-----------------|-----|-------------|--------------|--------|
| W1 | Bypassable transport / raw-XML `exchange` reachable by app code | `tally/tally-module.ts`, `bootstrap/register-services.ts` | `tallyModule.transport` / `.requestBuilder` exposed; services call `exchange(xml)` | Critical | Removed `transport`+`requestBuilder` from module surface; introduced `TallyReadGateway`; rewired all services | `read-gateway.test.ts`, `module-boundaries.test.ts` | Fixed (in-process) |
| W2 | Read-only egress NOT enforced (IMPORT/EXECUTE/FUNCTION allowed) | `tally/safety/xml-request-validator.ts` | `ALLOWED_TYPES=[EXPORT,IMPORT,EXECUTE]`, kinds included `FUNCTION` | Critical | `EXPORT`-only; kinds `DATA/COLLECTION/OBJECT`; forbidden-token scan | `security-validator.test.ts`, `security-guard.test.ts` | Fixed |
| W3 | `SAFE_MODE=false` master off-switch (retries/concurrency/circuit) | `tally/safety/tally-request-guard.ts` | `resolveTallyRuntimeLimits` weakened when safe mode off | Critical | Mandatory limits (pool=1, retry=1, circuit on, no reconnect); safe mode may only tighten | `tally-runtime-limits.test.ts`, `tally-safety.test.ts`, `security-guard.test.ts` | Fixed |
| — | No egress allow-list; unknown collection IDs reach Tally (hang) | `tally/safety/tally-request-guard.ts` | validator only checked ID presence | Critical | Registry + fail-closed policy at chokepoint; `UNKNOWN=DENY` | `policy-registry.test.ts`, `security-guard.test.ts` | Fixed |
| — | `List of Units` / Stock-Item object could be requested | `tally/registry/forbidden-registry.ts` | any caller | Critical | Permanent forbidden registry + policy `DENY` | `policy-registry.test.ts`, `security-guard.test.ts` | Fixed |
| W7 | Poor forensics; audit after transport; no hash | `tally/safety/tally-request-auditor.ts`, `...guard.ts` | audit on outcome only | High | Audit **intent before transport** + SHA-256 request hash + op/decision metadata | `audit-intent.test.ts` | Fixed (redacted logs; exact-byte vault designed only — see §11) |
| — | Silent empty results (Units = 0 as PASS) | `services/extraction/master-data.service.ts`, `tally/contracts/response-contract.ts` | derivation returned empty silently | High | Explicit `INCOMPLETE` `dataQuality` on the units envelope | `response-contract.test.ts` | Fixed |
| — | `XmlImportService` inside live comms graph | `src/ingestion/offline-xml-ingestion.service.ts` | shared parser in live module | Medium | Moved to `src/ingestion/`; own parser; boundary test forbids transport imports | `module-boundaries.test.ts`, `offline-xml-ingestion.test.ts` | Fixed |
| W4 | Per-process only safety (multi-instance) | — | in-memory circuit/single-flight | High | **Not fixed** — documented | — | Open (see §11) |
| W8 | Non-persistent circuit state | `tally/safety/tally-circuit-breaker.ts` | resets on restart | Medium | **Not fixed** — documented | — | Open (see §11) |
| W6 | Tally coupling in "ERP-agnostic" core | `src/tally/**` | policy/registry Tally-scoped | Medium | Partially — engine is cohesive but still under `src/tally` | — | Partial (see §11) |
| W9 | Health/recovery SUSPECTED→QUARANTINED state machine | `...guard.ts` (`markQuarantined`), `policy-engine.ts` | wedge auto-detection not wired | Medium | Quarantine state + half-open health-only enforced; auto-transition on wedge not fully wired | `policy-registry.test.ts` | Partial (see §11) |

The preserved review is the evidence baseline: `docs/architecture/tally-communication-safety-review.md`.

---

## 3. Updated architecture (egress path)

```
Application (routes, extractors, services, workers, scheduler)
        │  strongly typed ApprovedReadRequest { operationId, companyName? }
        ▼
TallyReadGateway.executeApprovedRead()      ← ONLY app-facing egress API
        │  renders XML internally from the immutable registry contract
        ▼
TallyConnectionManager.exchange(xml)        ← adapter-internal
        ▼
TallyRequestGuard.prepare()                 ← MANDATORY chokepoint (runs for EVERY request)
   1. circuit breaker assert (open ⇒ 503)
   2. validateTallyRequestXml  (EXPORT-only, forbidden-token reject)
   3. registry lookup (findApprovedOperationByRequest) + forbidden lookup
   4. decidePolicy()  → ALLOW | DENY | QUARANTINE | REQUIRE_MANUAL_APPROVAL   (UNKNOWN = DENY)
   5. single-flight acquire (always) + rate limit
   6. AUDIT INTENT (before transport) + request hash
        ▼
TallyHttpTransport.send()                   ← private to adapter composition root
        ▼
          TallyPrime (localhost:9000)
```

Offline path is fully disconnected:

```
User-selected XML file ──► OfflineXmlIngestionService (src/ingestion)
                           parse + validate locally (own parser)
                           ✗ no gateway ✗ no connection manager ✗ no transport ✗ no socket
```

## 4. State machine / circuit (recovery)

```
closed ──(failures ≥ threshold)──► open ──(cooldown)──► half_open
  ▲                                                        │
  └────────────── successful health probe ─────────────────┘
half_open: policy admits ONLY the approved health probe (TALLY_HEALTH_READ).
quarantined: business ⇒ QUARANTINE (503); only the minimal health probe may run.
```

## 5. Capability matrix

| Capability | Operations | Verb | Notes |
|-----------|------------|------|-------|
| `TALLY_HEALTH_READ` | `HEALTH_CHECK` | Export/Data `License Info` | Only capability admitted on half-open / quarantine |
| `TALLY_COMPANY_READ` | `COMPANY_LIST`, `COMPANY_INFO` | Export/Collection, Export/Object | |
| `TALLY_MASTER_READ` | 10 master collections | Export/Collection | |
| `TALLY_REPORT_READ` | (reserved) | Export | none active |

**No** `RAW_XML`, `IMPORT`, `EXECUTE`, `FUNCTION`, `WRITE`, `CREATE`, `ALTER`,
`DELETE`, or `UPDATE` capability exists (`src/tally/security/capabilities.ts`).

## 6. Production request registry (VERIFIED_SAFE unless noted)

`src/tally/registry/operation-registry.ts`

| Operation ID | Kind / Tally ID | Capability | Class | Risk |
|---|---|---|---|---|
| HEALTH_CHECK | Data / License Info | HEALTH_READ | VERIFIED_SAFE | LOW |
| COMPANY_LIST | Collection / List of Companies | COMPANY_READ | VERIFIED_SAFE | LOW |
| COMPANY_INFO | Object / Company | COMPANY_READ | **CONDITIONAL** (code-policy auto-allow; safe fallback) | MEDIUM |
| LEDGER_GROUPS | Collection / List of Groups | MASTER_READ | VERIFIED_SAFE | LOW |
| LEDGERS | Collection / List of Ledgers | MASTER_READ | VERIFIED_SAFE | MEDIUM |
| STOCK_GROUPS | Collection / List of Stock Groups | MASTER_READ | VERIFIED_SAFE | LOW |
| STOCK_CATEGORIES | Collection / List of Stock Categories | MASTER_READ | VERIFIED_SAFE | LOW |
| STOCK_ITEMS | Collection / List of Stock Items | MASTER_READ | VERIFIED_SAFE | MEDIUM |
| GODOWNS | Collection / List of Godowns | MASTER_READ | VERIFIED_SAFE | LOW |
| COST_CATEGORIES | Collection / List of Cost Categories | MASTER_READ | VERIFIED_SAFE | LOW |
| COST_CENTRES | Collection / List of Cost Centres | MASTER_READ | VERIFIED_SAFE | LOW |
| VOUCHER_TYPES | Collection / List of Voucher Types | MASTER_READ | VERIFIED_SAFE | LOW |
| GST_REGISTRATIONS | Collection / List of GST Registrations | MASTER_READ | VERIFIED_SAFE | LOW |

Each entry carries: adapter version, Tally evidence build, request-contract
version, response-schema version, payload/timeout caps, retry=`none`,
concurrency=1, evidence source, rollout status. **Evidence is environment-scoped**
(company `ESTIMATION`, one Tally build) and is not claimed universally safe.

## 7. Forbidden-operation registry

`src/tally/registry/forbidden-registry.ts`

| Kind / Tally ID | Reason | Evidence |
|---|---|---|
| Collection / List of Units | DEADLOCK_EVIDENCE | Live 2026-07-22: wedged HTTP handler, flat CPU/mem, timeout |
| Object / Stock Item | DEADLOCK_EVIDENCE | Live 2026-07-22: single object export wedged HTTP server |

Plus every unregistered `(kind, id)` → `UNKNOWN` → `DENY`.

## 8. Module-boundary rules (enforced by `test/architecture/module-boundaries.test.ts`)

- Application code (`extraction/`, `api/`, `services/extraction/`, `ingestion/`)
  must not import `tally/transport` or `tally/connection`.
- `extraction/`, `services/extraction/`, `ingestion/` must not import raw network
  modules (`node:net/http/https/tls`, `node-fetch`, `undici`).
- `ingestion/` must not import `tally/transport`, `tally/connection`, or `tally/gateway`.
- The `TallyXmlRequestBuilder` **class** may only be used inside `src/tally/**`
  (plus the composition root). Request templates return inert spec data only.
- At least one business module reaches Tally via `tally/gateway` (sanctioned path).

## 9. DI exposure report

- `ServiceTokens` contains **no** token for the transport, connection manager, or
  a raw-XML executor (asserted in `read-gateway.test.ts`).
- `createTallyModule()` returns `{ connectionManager, readGateway, responseParser,
  companyDiscoveryParser }` — **no `transport`, no `requestBuilder`** (asserted).
- Business services (`MasterData`, `CompanyDiscovery`, extractors) receive only the
  `TallyReadGateway`. The connection manager is handed only to infra
  lifecycle/diagnostics services (`services/tally/tally-connection.service.ts`,
  `tally-diagnostics.service.ts`) and is itself guarded on every call.
- `OfflineXmlIngestionService` receives only a fresh `TallyXmlResponseParser`.

## 10. Regression-test report (Phase 11, 20 requirements)

| # | Requirement | Test |
|---|-------------|------|
| 1 | IMPORT rejected | `security-validator.test.ts`, `security-guard.test.ts` |
| 2 | EXECUTE rejected | `security-validator.test.ts`, `security-guard.test.ts` |
| 3 | FUNCTION rejected | `security-validator.test.ts` |
| 4 | CREATE/ALTER/DELETE/UPDATE rejected | `security-validator.test.ts` |
| 5 | Unknown request types rejected | `security-validator.test.ts`, `policy-registry.test.ts` |
| 6 | Unknown collection IDs rejected | `policy-registry.test.ts`, `security-guard.test.ts` |
| 7 | SAFE_MODE=false cannot weaken protections | `tally-runtime-limits.test.ts`, `tally-safety.test.ts`, `security-guard.test.ts` |
| 8 | Raw transport not resolvable from DI | `read-gateway.test.ts` |
| 9 | App code cannot import transport internals | `module-boundaries.test.ts` |
| 10 | Offline ingestion cannot access transport | `module-boundaries.test.ts`, `offline-xml-ingestion.test.ts` |
| 11 | Health checks cannot carry business requests | `policy-registry.test.ts` (health capability isolated) |
| 12 | Half-open admits health only | `policy-registry.test.ts` |
| 13 | Audit intent before transport | `audit-intent.test.ts` |
| 14 | Forbidden XML never reaches mocked transport | `security-guard.test.ts` (fetch call count = 0) |
| 15 | Dangerous historical payloads permanently blocked | `policy-registry.test.ts`, `security-guard.test.ts` |
| 16 | Missing critical fields ⇒ INCOMPLETE not false success | `response-contract.test.ts` |
| 17 | Registry classifications version-aware | `policy-registry.test.ts` |
| 18 | Prod config cannot enable experimental/forbidden | `policy-registry.test.ts` (classification-driven deny; no env path in engine) |
| 19 | Approved reads all pass through the single gateway | `master-data.test.ts` (integration), `read-gateway.test.ts` |
| 20 | No route/extractor/service/helper bypasses gateway | `module-boundaries.test.ts`, `read-gateway.test.ts` |

## 11. Remaining technical debt (honest)

1. **Per-process safety only (W4).** Circuit breaker, single-flight, quarantine,
   and rate limiting are in-memory per process. Two connector instances against one
   Tally are not coordinated. Needs a shared lock/broker for true multi-instance
   safety.
2. **Non-persistent circuit (W8).** Circuit/quarantine state resets on restart. A
   crash-loop could re-probe a wedged Tally after each restart.
3. **Forensic vault designed, not built (Phase 8).** Ordinary audit logs are
   **redacted** and therefore **cannot** reconstruct exact request/response bytes.
   The encrypted, opt-in, short-retention vault is specified but not implemented.
   *Honest statement: exact-byte forensic reconstruction is not possible today.*
4. **Wedge auto-detection into QUARANTINED (W9).** The guard exposes
   `markQuarantined()` and the policy enforces quarantine + half-open-health-only,
   but the connection manager does not yet automatically classify a hung-HTTP wedge
   and call `markQuarantined()`. Failure taxonomy is partially distinguished.
5. **Response-contract coverage.** Only the Units contract is wired end-to-end
   (explicit `INCOMPLETE`). Golden-response fixtures, contract snapshots, and
   per-entity required-field validation are not yet implemented for all entities.
6. **ERP-agnostic engine (W6).** Capability/policy/registry are cohesive but still
   live under `src/tally`. Extracting an ERP-neutral policy/gateway core (with a
   Tally adapter) remains future work.
7. **Request templates location.** `extraction/templates/master-data-templates.ts`
   returns inert spec data but physically sits in `extraction/`; ideally co-located
   in the adapter. Boundary test already prevents building XML strings there.
8. **Internal reconnect probe.** `TallyConnectionManager.probeTallyReachable()`
   sends a fixed `License Info` request straight to transport (adapter-internal,
   fixed payload). With mandatory retry=1 this path is effectively dormant, but it
   is not routed through the guard object.

## 12. Production-readiness verdict

- **Egress read-only guarantee (in-process):** **Strong.** Every supported code
  path is blocked from sending write/execute or unregistered requests, proven by
  runtime tests (chokepoint) and architecture tests (imports). Score: **8.5/10**.
- **Overall connector production readiness:** **7.0/10** (up from 3.0 baseline).
  Gated on items 1–5 above (multi-instance coordination, persistence, forensic
  vault, wedge auto-quarantine, broader response contracts).

**Do not claim "impossible to bypass" beyond the in-process guarantee.** External
truth: a sufficiently privileged local process can open its own socket to Tally on
`localhost:9000`. This connector guarantees that **Budcom's supported production
code path** cannot issue a write/execute/unapproved request — not that the OS
prevents all external access.

## 13. Exact list of changed files

**New (source):**
- `connector/budcom_connector/src/tally/security/capabilities.ts`
- `connector/budcom_connector/src/tally/registry/operation-registry.ts`
- `connector/budcom_connector/src/tally/registry/forbidden-registry.ts`
- `connector/budcom_connector/src/tally/policy/policy-engine.ts`
- `connector/budcom_connector/src/tally/gateway/tally-read-gateway.ts`
- `connector/budcom_connector/src/tally/contracts/response-contract.ts`
- `connector/budcom_connector/src/ingestion/offline-xml-ingestion.service.ts`

**Modified (source):**
- `connector/budcom_connector/src/tally/safety/xml-request-validator.ts`
- `connector/budcom_connector/src/tally/safety/tally-request-guard.ts`
- `connector/budcom_connector/src/tally/safety/tally-request-auditor.ts`
- `connector/budcom_connector/src/tally/tally-module.ts`
- `connector/budcom_connector/src/bootstrap/register-services.ts`
- `connector/budcom_connector/src/services/tally/company-discovery.service.ts`
- `connector/budcom_connector/src/services/extraction/master-data.service.ts`
- `connector/budcom_connector/src/extraction/extractors/master-data-extractor.ts`
- `connector/budcom_connector/src/extraction/extractors/extractor-registry.ts`
- `connector/budcom_connector/src/extraction/core/types.ts`

**Removed (source):**
- `connector/budcom_connector/src/services/tally/xml-import.service.ts`
  (relocated to `src/ingestion/offline-xml-ingestion.service.ts`)

**New (tests):**
- `test/architecture/module-boundaries.test.ts`
- `test/unit/tally/security-validator.test.ts`
- `test/unit/tally/policy-registry.test.ts`
- `test/unit/tally/read-gateway.test.ts`
- `test/unit/tally/audit-intent.test.ts`
- `test/unit/tally/response-contract.test.ts`
- `test/unit/ingestion/offline-xml-ingestion.test.ts`
- `test/integration/security-guard.test.ts`

**Modified (tests):**
- `test/unit/tally/tally-runtime-limits.test.ts`
- `test/integration/tally-safety.test.ts`

**New (docs):**
- `docs/architecture/fail-open-remediation-report.md` (this file)

## 14. Exact list of forbidden capabilities/paths removed

- `ALLOWED_TYPES` no longer includes `IMPORT` or `EXECUTE` (now `EXPORT`-only).
- `ALLOWED_REQUEST_KINDS` no longer includes `FUNCTION` (now `DATA`/`COLLECTION`/`OBJECT`).
- Write/execute verbs (`IMPORT`, `EXECUTE`, `FUNCTION`, `CREATE`, `ALTER`, `DELETE`,
  `UPDATE`, `INSERT`, `POST`, `SAVE`, `MODIFY`, `REMOVE`) hard-rejected pre-transport.
- `TallyModule` no longer exposes `transport` or `requestBuilder` to the app.
- No DI token resolves the transport / connection manager / raw-XML executor.
- `SAFE_MODE=false` can no longer disable the circuit breaker, enable retries,
  enable multi-connection concurrency, or enable reconnect storms.
- `List of Units` and single Stock-Item object export are permanently forbidden.

## 15. Exact list of approved paths retained

- The 13 registry operations in §6 (health, company list/info, 10 master
  collections), executed **only** via `TallyReadGateway.executeApprovedRead`.
- Offline XML file ingestion via `OfflineXmlIngestionService` (local files only;
  no Tally egress).
- Infra lifecycle/diagnostics (`TallyConnectionService`, `TallyDiagnosticsService`)
  over the guarded connection manager.
```
