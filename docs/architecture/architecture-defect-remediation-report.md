# Architecture Defect Remediation Report — Budcom Tally Connector

**Status:** Implementation complete; automated verification green. Awaiting human review.  
**Date:** 2026-07-22  
**Scope:** Correct proven architectural defects from the independent architecture review.  
**No live Tally contact. No business feature changes. No commit/tag/push.**

**Verification (local):**

- `npm run lint` — pass  
- `npm run test` — **155 passed / 155**  
- `npm run build` — pass  
- Architecture tests (`test/architecture/module-boundaries.test.ts`) — **12 passed**

---

## 1. Files changed

### Added

| File | Purpose |
|------|---------|
| `src/erp/policy/policy-types.ts` | ERP-neutral policy contracts (`PolicyOperation`, `PolicyInput`, `PolicyResult`) |
| `src/erp/policy/policy-engine.ts` | ERP-neutral fail-closed policy decision point |
| `src/erp/ports/erp-read-port.ts` | ERP-neutral read port — business code depends only on this |
| `src/tally/adapter/tally-read-adapter.ts` | Tally adapter implementing `ErpReadPort`; owns XML/parsing |

### Modified

| File | Change |
|------|--------|
| `src/core/tokens.ts` | Replaced `TallyModule` token with `ErpReadPort` |
| `src/bootstrap/register-services.ts` | Registers `ErpReadPort`; business services receive port, not gateway |
| `src/tally/tally-module.ts` | Exposes `readPort` + internal `connectionManager` only |
| `src/tally/registry/operation-registry.ts` | Removed dead fields; added `toPolicyOperation()` mapper |
| `src/tally/safety/tally-request-guard.ts` | Uses ERP-neutral policy; removed dead quarantine state |
| `src/tally/connection/tally-connection-manager.ts` | Removed unguarded `probeTallyReachable()` bypass |
| `src/services/interfaces/tally-connection.ts` | Removed `exchange()` — no raw-XML API |
| `src/services/tally/tally-connection.service.ts` | Lifecycle/diagnostics only (ping, no exchange) |
| `src/services/tally/company-discovery.service.ts` | Depends on `ErpReadPort` only |
| `src/services/extraction/master-data.service.ts` | Depends on `ErpReadPort` only |
| `src/services/placeholders/tally-connection.stub.ts` | Removed `exchange()` stub |
| `test/architecture/module-boundaries.test.ts` | Strengthened: DI checks, port vs gateway, ERP-neutral policy |
| `test/unit/tally/policy-registry.test.ts` | Tests neutral policy via `toPolicyOperation()` |
| `test/unit/tally/read-gateway.test.ts` | DI surface + no exchange on connection service |

### Deleted

| File | Reason |
|------|--------|
| `src/tally/policy/policy-engine.ts` | Replaced by ERP-neutral `src/erp/policy/policy-engine.ts` |

---

## 2. Review findings resolved

| Review finding | Resolution | Evidence |
|----------------|------------|----------|
| **Gateway bypass via `TallyConnectionService.exchange(rawXml)`** | Removed `exchange()` from interface and implementation | `tally-connection.ts`, architecture test "TallyConnectionService interface has no exchange method" |
| **Gateway bypass via `ServiceTokens.TallyModule`** | Removed token; business code resolves `ErpReadPort` only | `tokens.ts`, `register-services.ts`, architecture DI tests |
| **Unguarded egress via `probeTallyReachable()`** | Deleted method; reconnect path no longer bypasses guard | `tally-connection-manager.ts` |
| **Raw XML leaked to business layers** | `CompanyDiscoveryServiceImpl` and `MasterDataServiceImpl` use `ErpReadPort` returning domain models | No `rawXml`/`ParsedXmlNode` in `services/extraction/` or `services/tally/` |
| **Business code coupled to `TallyReadGateway` + `ApprovedOperationId`** | Services depend on `ErpReadPort` interface only | `company-discovery.service.ts`, `master-data.service.ts` |
| **Policy engine Tally-coupled** | Moved to `src/erp/policy/`; Tally registry maps via `toPolicyOperation()` | Architecture test "ERP-neutral policy engine has no Tally module imports" |
| **Dead quarantine (`markQuarantined` never called)** | Removed quarantine state and methods from guard | `tally-request-guard.ts` |
| **Dead registry fields (preFlightHealth, retryPolicy, etc.)** | Removed unused fields from `ApprovedOperation` | `operation-registry.ts` |
| **Brittle architecture tests missed DI bypasses** | Added DI resolution tests, port-vs-gateway checks, scoped business-layer scans | `module-boundaries.test.ts` (12 tests) |
| **Documentation overclaimed "unbypassable"** | This report states exact guarantees and limitations (see §10) | — |

---

## 3. Remaining intentional limitations

1. **In-process guarantee only.** A sufficiently privileged local process can still open its own socket to Tally on port 9000. The connector guarantees that **Budcom's supported production code paths** cannot do so — not that the OS prevents all external access.

2. **`TallyConnectionManager.exchange(xml)` remains adapter-internal.** It is used by `TallyReadGateway` and `ping()` inside the adapter composition root. It is not registered in DI and is not reachable from business services.

3. **Lifecycle/diagnostics services use `connectionManager` for ping/diagnostics only** — not for business reads. `TallyDiagnosticsService` still imports connection manager for snapshot data.

4. **Offline XML ingestion uses `TallyXmlResponseParser` locally** — by design (Phase 3). It never reaches live transport.

5. **`extraction/` parsing modules still contain Tally XML types** — they are invoked only from `TallyReadAdapter` inside the adapter boundary, not from business services.

6. **Multi-instance / persistent circuit state** — unchanged from prior remediation; still in-memory per process.

7. **Automatic wedge→quarantine transition** — removed dead quarantine rather than implementing half-finished auto-transition. Circuit breaker open/half-open policy still enforced.

8. **`assessDerivedUnits` import in `master-data.service.ts`** — domain data-quality helper in `tally/contracts/`; not raw XML. Acceptable residual coupling; can move to `extraction/core/` in a future cleanup.

---

## 4. Architecture improvements

```
Business services (CompanyDiscovery, MasterData)
        │  ErpReadPort (domain models only)
        ▼
TallyReadAdapter                          ← adapter boundary; XML stays here
        │  executeApprovedRead(operationId)
        ▼
TallyReadGateway                          ← internal typed gateway
        ▼
TallyConnectionManager.exchange()         ← adapter-internal only
        ▼
TallyRequestGuard.prepare()               ← mandatory chokepoint
        │  decidePolicy(toPolicyOperation(...))   ← ERP-neutral core
        ▼
Transport
```

**Key structural changes:**

- **Ports before adapters:** Business → `ErpReadPort` → `TallyReadAdapter` → internal gateway  
- **ERP-neutral security core:** `src/erp/policy/` has zero Tally imports  
- **Single production read path for business code:** `ErpReadPort.*()` methods  
- **No raw-XML application API:** `TallyConnectionService` has ping/diagnostics only  

---

## 5. Updated scores

| Dimension | Prior review | After remediation | Rationale |
|-----------|-------------|-------------------|-----------|
| **Architecture** | 5.5/10 | **7.5/10** | Gateway bypass removed; port/adapter seam exists; dead architecture removed |
| **Security** | 7/10 | **8/10** | No application-facing exchange(); unguarded probe removed; policy neutral |
| **ERP independence** | 3/10 | **6/10** | Neutral policy core + read port; Tally knowledge confined to adapter |
| **Maintainability** | 6/10 | **7/10** | Clearer dependency direction; fewer false claims; stronger tests |
| **Production readiness** | — | **7/10** | Safe for review/commit of architecture fixes; live Tally re-validation still required separately |

---

## 6. Can the implementation honestly claim?

> **"The only supported production communication path is the approved read gateway."**

### Answer: **YES** (with documented scope)

**Evidence:**

1. **Business services** (`CompanyDiscoveryServiceImpl`, `MasterDataServiceImpl`) depend only on `ErpReadPort`. They cannot call `exchange()`, import the gateway, or receive raw XML.

2. **`TallyConnectionService`** exposes `ping()` and `getDiagnostics()` only — verified by architecture test inspecting the implementation prototype.

3. **DI** registers `ErpReadPort`, not `TallyModule`, `TallyReadGateway`, or transport tokens. `registerServices()` wires `tallyModule.readPort` into business services.

4. **`TallyReadAdapter`** is the sole bridge from `ErpReadPort` to `TallyReadGateway.executeApprovedRead()`.

5. **Architecture tests (12)** fail the build if business read services import gateway/registry/parsers or reference `rawXml`/`ParsedXmlNode`.

6. **Every request** reaching Tally still passes `TallyRequestGuard.prepare()` → ERP-neutral `decidePolicy()` → registry/forbidden lookup → audit intent.

**Scope qualifier:** This YES applies to **Budcom connector application code registered through the composition root**. It does not claim OS-level isolation from other local processes, nor does it claim `TallyConnectionManager.exchange()` is physically impossible to call from test code inside the `tally/` adapter package (integration tests intentionally construct managers for guard verification).

---

## 7. Should this be committed?

**YES** — for the architecture defect remediation scope only, pending your review.

Justification: The central false claim (single gateway) is now structurally true for production business paths; ERP-neutral policy core exists; dead architecture removed; 155/155 tests green.

---

*Prior remediation baseline: `docs/architecture/fail-open-remediation-report.md`*  
*Independent review baseline: `docs/architecture/tally-communication-safety-review.md`*
