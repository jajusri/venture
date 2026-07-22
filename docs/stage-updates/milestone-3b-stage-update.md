# Milestone 3B — Stage Update

**Milestone:** Production Company Discovery  
**Last updated:** 2026-07-22  
**Connector version:** 0.3.1  
**Git tag:** `v0.3.1-company-discovery`  
**Commit:** `8d2c5b0631493d73ac312261ebe0a331e025610f`

---

## Update history

| Date | Author | Summary |
|------|--------|---------|
| 2026-07-22 | Engineering | Initial stage update after live validation and commit |

---

## 1. Current status

**Completed**

---

## 2. Production Readiness

| Area | % | Notes |
|------|---|-------|
| **Overall** | **82%** | Live-validated for discovery path; not full connector production |
| Architecture | 95% | Clean path through `ErpReadPort`; 12/12 architecture tests |
| Implementation | 90% | Production service + adapter + parser complete |
| Testing | 88% | Automated + 4/4 live scenarios pass |
| Integration | 85% | HTTP + DI wired; circuit-breaker recovery requires restart |
| Live Validation | 90% | Scenarios 1–4 PASS on ESTIMATION / multi-company setup |
| Security | 92% | Read-only, policy/registry/audit enforced |
| Performance | 75% | ~2.1s first discovery; safe-mode interval applies |
| Documentation | 85% | Milestone doc + manual validation report complete |

---

## 3. Files Added

- `connector/budcom_connector/src/erp/ports/company-discovery.ts`
- `connector/budcom_connector/src/tally/contracts/company-discovery-contract.ts`
- `connector/budcom_connector/src/tally/discovery/company-discovery-parser.ts`
- `connector/budcom_connector/test/helpers/company-discovery-fixtures.ts`
- `connector/budcom_connector/test/unit/tally/company-discovery-parser.test.ts`
- `connector/budcom_connector/test/unit/tally/company-discovery-adapter.test.ts`
- `connector/budcom_connector/test/integration/company-discovery.test.ts`
- `docs/milestones/milestone-3b-company-discovery.md`
- `docs/testing/milestone-3b-company-discovery-manual-validation.md`

---

## 4. Files Modified

- `connector/budcom_connector/src/erp/ports/erp-read-port.ts`
- `connector/budcom_connector/src/services/interfaces/company-discovery.ts`
- `connector/budcom_connector/src/services/tally/company-discovery.service.ts`
- `connector/budcom_connector/src/tally/adapter/tally-read-adapter.ts`
- `connector/budcom_connector/src/tally/core/types.ts`
- `connector/budcom_connector/test/integration/server.test.ts`
- `connector/budcom_connector/test/unit/tally/response-parser.test.ts`
- `CHANGELOG.md`

---

## 5. Completed Capabilities

- `GET /companies` production company discovery
- `ErpReadPort.discoverCompanies()` with explicit `CompanyDiscoveryStatus`
- `COMPANY_LIST` approved read-only operation
- Envelope validation, parsing, deduplication, slug IDs
- HTTP mapping for SUCCESS / EMPTY / INCOMPLETE / UNAVAILABLE / DENIED / TIMEOUT / MALFORMED
- Audit evidence for every discovery attempt

---

## 6. Test Evidence

| Type | Result |
|------|--------|
| Unit tests | Company discovery parser + adapter tests pass |
| Integration tests | `company-discovery.test.ts` — 3/3 pass |
| Live Tally tests | 4/4 scenarios PASS (2026-07-22) |
| Build status | Pass at validation time (184/184 tests, lint, build) |

Evidence: `docs/testing/milestone-3b-company-discovery-manual-validation.md`

---

## 7. Known Bugs

None confirmed blocking discovery in validated scenarios.

---

## 8. Known Limitations

- Does not detect which company is **open** in Tally UI — only companies in data directory
- `baseCurrency` only when Tally returns it in export
- CMPINFO count nodes may produce cosmetic `dataQuality: INCOMPLETE` while status remains `SUCCESS`
- Circuit breaker open after Tally unavailable requires connector restart before recovery
- Tally version/build not captured during validation

---

## 9. Assumptions

- TallyPrime HTTP listener on port 9000
- Single connector instance per Tally port
- Company IDs derived via `slugify(name)` are stable for a given company name
- External processes may still reach Tally directly (in-process safety only)

---

## 10. Risk Level

**Medium**

Discovery is read-only and validated, but slug-based identity and circuit-breaker recovery behaviour can confuse operators.

---

## 11. Likely Future Bug Locations

- `company-discovery-parser.ts` — Tally XML shape drift
- `company-discovery-contract.ts` — SUCCESS vs INCOMPLETE assessment edge cases
- `CompanyDiscoveryServiceImpl` — HTTP status mapping for new adapter statuses
- Circuit breaker interaction after prolonged Tally outage

---

## 12. Deferred Improvements

- Capture Tally version/build in discovery or health metadata
- Persist discovery cache across connector restarts
- Explicit “company open in Tally” signal if Tally exposes it safely
- Automatic circuit recovery without full connector restart

---

## 13. Dependencies

- Milestone 3A (`v0.3.0-secure-foundation`) — ERP ports, read gateway, policy, audit
- Milestone 2.1 — connection hardening, circuit breaker, safe mode

---

## 14. Production Readiness Level

**L4 — Live Validated**

---

## 15. Exit Decision

**Approved for next milestone**

**Reason:** All four live validation scenarios passed. Automated suite green at commit time. Architecture boundaries enforced. Read-only guarantees preserved.
