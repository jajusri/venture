# Milestone 3C — Stage Update

**Milestone:** Production Tally Groups  
**Last updated:** 2026-07-22  
**Connector version:** 0.3.1  
**Git tag:** `v0.3.0-milestone-3c`  
**Commit:** `e96083f` (Complete Milestone 3C groups validation and documentation)

---

## Update history

| Date | Author | Summary |
|------|--------|---------|
| 2026-07-22 | Engineering | Initial stage update after live validation (7/7) and commit |

---

## 1. Current status

**Completed**

---

## 2. Production Readiness

| Area | % | Notes |
|------|---|-------|
| **Overall** | **78%** | Live-validated; hierarchy marked INCOMPLETE on real Tally data |
| Architecture | 95% | Groups path through `ErpReadPort.getGroups()` only |
| Implementation | 88% | Parser, hierarchy validator, contract assessment complete |
| Testing | 90% | 38 new automated tests + 7/7 live scenarios |
| Integration | 85% | Master data HTTP route + adapter wired |
| Live Validation | 85% | 7/7 PASS on ESTIMATION (28 groups); Tally version not captured |
| Security | 92% | `LEDGER_GROUPS` read-only; audit verified |
| Performance | 80% | ~4.3s first call; ~28ms cached repeat observed live |
| Documentation | 88% | Milestone + manual validation reports complete |

---

## 3. Files Added

- `connector/venture_connector/src/erp/ports/groups.ts`
- `connector/venture_connector/src/tally/contracts/groups-contract.ts`
- `connector/venture_connector/src/tally/groups/groups-parser.ts`
- `connector/venture_connector/src/tally/groups/hierarchy-validator.ts`
- `connector/venture_connector/test/helpers/groups-fixtures.ts`
- `connector/venture_connector/test/unit/tally/groups-parser.test.ts`
- `connector/venture_connector/test/unit/tally/groups-hierarchy.test.ts`
- `connector/venture_connector/test/unit/tally/groups-adapter.test.ts`
- `connector/venture_connector/test/integration/groups.test.ts`
- `docs/milestones/milestone-3c-groups.md`
- `docs/testing/milestone-3c-groups-manual-validation.md`

---

## 4. Files Modified

- `connector/venture_connector/src/erp/ports/erp-read-port.ts`
- `connector/venture_connector/src/services/extraction/master-data.service.ts`
- `connector/venture_connector/src/tally/adapter/tally-read-adapter.ts`
- `connector/venture_connector/src/tally/tally-module.ts`
- `connector/venture_connector/test/unit/tally/company-discovery-adapter.test.ts`
- `CHANGELOG.md`

---

## 5. Completed Capabilities

- `GET /companies/:companyId/ledger-groups` with explicit `status`, `contractVersion`, `hierarchyIssues`
- `ErpReadPort.getGroups(companyName)` with `ErpGroupsResult`
- `LEDGER_GROUPS` / Export / `List of Groups` approved operation
- Hierarchy validation: MISSING_PARENT, CYCLE, DUPLICATE_ID, etc.
- Slug-based stable IDs with collision detection
- No raw XML in business-facing responses

---

## 6. Test Evidence

| Type | Result |
|------|--------|
| Unit tests | Parser (18), hierarchy (5), adapter (11) — pass |
| Integration tests | `groups.test.ts` — 4/4 pass |
| Live Tally tests | 7/7 scenarios PASS (2026-07-22, ESTIMATION, 28 groups) |
| Build status | Pass at validation: lint, **222/222** tests, build, 12/12 architecture |

Evidence: `docs/testing/milestone-3c-groups-manual-validation.md`

---

## 7. Known Bugs

| ID | Severity | Description |
|----|----------|-------------|
| M3C-001 | Medium | Tally returns parent as `"&#4; Primary"`; code recognizes only `"Primary"` → 15 false `MISSING_PARENT`, status `INCOMPLETE` |

Tracked as **[TD-001](../technical-debt/registry.md#td-001--parent-encoding-normalization)** (Target: Milestone 5A, P2).

---

## 8. Known Limitations

- Parent resolution is name-based only (Tally export limitation)
- Stable IDs are slug-derived, not Tally internal GUIDs
- Primary group not included in collection export
- `reservedName` not set for tested built-in groups
- No-company / unavailable paths may fail at company resolution before `LEDGER_GROUPS`
- Tally version/build not captured
- Visually similar names may slug-collide (none observed in live test)

---

## 9. Assumptions

- Company name scoping via `SVCURRENTCOMPANY` is sufficient for groups export
- `Primary` virtual root behaviour matches Tally semantics when parent is plain `"Primary"`
- Slug collision across distinct names is rare but must remain surfaced, not merged

---

## 10. Risk Level

**Medium**

Hierarchy trust is explicitly `INCOMPLETE` on live ESTIMATION data due to entity-encoded parent names.

---

## 11. Likely Future Bug Locations

- `hierarchy-validator.ts` — virtual root recognition, entity decoding
- `groups-parser.ts` — Tally XML field drift, ISBUILTIN handling
- `groups-contract.ts` — SUCCESS vs INCOMPLETE threshold
- `slugify()` collisions for Unicode or punctuation-heavy group names

---

## 12. Deferred Improvements

- Normalize `"&#4; Primary"` (and similar entity forms) to virtual root
- Export Primary group handling when Tally omits it
- Cross-version Tally live validation matrix
- Parent GUID support if Tally exposes it safely

---

## 13. Dependencies

- Milestone 3B — company discovery and `CompanyResolver`
- Milestone 3A — secure read gateway and policy

---

## 14. Production Readiness Level

**L4 — Live Validated**

---

## 15. Exit Decision

**Approved for next milestone**

**Reason:** Seven live scenarios passed. Automated regression green including 3B. Known hierarchy quirk documented and not silently repaired. Safe to proceed to session management (3D).
