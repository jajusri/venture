# Company Discovery Module — Stage Update

**Module:** Company Discovery  
**Milestone:** 3B  
**Last updated:** 2026-07-22  
**Owner layer:** `services/tally/company-discovery.service.ts`, `tally/discovery/`, `erp/ports/company-discovery.ts`

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-22 | Initial module stage update aligned with Milestone 3B validation |

---

## 1. Current status

**Completed**

---

## 2. Production Readiness

| Area | % |
|------|---|
| Overall | 82% |
| Architecture | 95% |
| Implementation | 90% |
| Testing | 88% |
| Integration | 85% |
| Live Validation | 90% |
| Security | 92% |
| Performance | 75% |
| Documentation | 85% |

---

## 3. Files Added

See `docs/stage-updates/milestone-3b-stage-update.md` §3.

---

## 4. Files Modified

See `docs/stage-updates/milestone-3b-stage-update.md` §4.

Also consumed by:

- `connector/budcom_connector/src/services/extraction/company-resolver.ts` (3D — discovery snapshot cache)

---

## 5. Completed Capabilities

- Production `GET /companies`
- Explicit discovery statuses and data quality
- Slug-based company IDs
- Duplicate removal and missing-identity counting

---

## 6. Test Evidence

- Unit: parser (16 tests), adapter (10 tests)
- Integration: `company-discovery.test.ts` (3 tests)
- Live: 4/4 scenarios PASS (2026-07-22)
- Regression: green in 3C and 3D suites

---

## 7. Known Bugs

None blocking.

---

## 8. Known Limitations

- Cannot detect which company is open in Tally UI
- Slug IDs may collide for normalization-equivalent names (not observed live)
- Circuit breaker recovery may require connector restart

---

## 9. Assumptions

- `List of Companies` export is stable across tested TallyPrime builds

---

## 10. Risk Level

**Medium**

---

## 11. Likely Future Bug Locations

- `company-discovery-parser.ts`
- `assessCompanyDiscovery()` in `company-discovery-contract.ts`
- Discovery + session cache interaction (3D)

---

## 12. Deferred Improvements

- Tally version capture
- Open-company detection if safely available

---

## 13. Dependencies

- Tally read gateway (3A)
- Tally connection manager (2.1)

---

## 14. Production Readiness Level

**L4 — Live Validated**

---

## 15. Exit Decision

**Approved for production use (discovery scope only)**

Milestone reference: `docs/stage-updates/milestone-3b-stage-update.md`
