# Groups Module — Stage Update

**Module:** Ledger Groups Extraction  
**Milestone:** 3C  
**Last updated:** 2026-07-22  
**Owner layer:** `tally/groups/`, `erp/ports/groups.ts`, master-data service

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-22 | Initial module stage update aligned with Milestone 3C live validation |

---

## 1. Current status

**Completed** (with documented live hierarchy quirk)

---

## 2. Production Readiness

| Area | % |
|------|---|
| Overall | 78% |
| Architecture | 95% |
| Implementation | 88% |
| Testing | 90% |
| Integration | 85% |
| Live Validation | 85% |
| Security | 92% |
| Performance | 80% |
| Documentation | 88% |

---

## 3. Files Added

See `docs/stage-updates/milestone-3c-stage-update.md` §3.

---

## 4. Files Modified

See `docs/stage-updates/milestone-3c-stage-update.md` §4.

Post-3C integration:

- `master-data.service.ts` — session pre-flight gate (3D)

---

## 5. Completed Capabilities

- `GET /companies/:companyId/ledger-groups`
- Hierarchy issue reporting without silent repair
- Explicit `INCOMPLETE` when hierarchy untrusted
- Slug stable IDs and collision detection

---

## 6. Test Evidence

- Unit: parser (18), hierarchy (5), adapter (11)
- Integration: `groups.test.ts` (4)
- Live: 7/7 PASS, ESTIMATION, 28 groups (2026-07-22)
- Post-3D regression: groups tests pass with session selection (244 total suite)

---

## 7. Known Bugs

| ID | Description |
|----|-------------|
| M3C-001 | `"&#4; Primary"` parent encoding → 15× `MISSING_PARENT`, status `INCOMPLETE` |

See **[TD-001](../../technical-debt/registry.md#td-001--parent-encoding-normalization)**.

---

## 8. Known Limitations

- Name-based parent resolution only
- Slug IDs, not Tally GUIDs
- Primary not in export
- Requires company selection (3D) before extraction

---

## 9. Assumptions

- Groups export complete for open company scope
- Virtual root is `"Primary"` when Tally sends plain text

---

## 10. Risk Level

**Medium**

---

## 11. Likely Future Bug Locations

- `hierarchy-validator.ts` — entity-encoded parent names
- `groups-parser.ts` — field mapping drift
- Session + company mismatch before groups call

---

## 12. Deferred Improvements

- Entity normalization for Primary parent
- Multi-version Tally validation

---

## 13. Dependencies

- Company discovery (3B)
- Company session validation (3D)
- Secure read gateway (3A)

---

## 14. Production Readiness Level

**L4 — Live Validated**

Use with caution when hierarchy status is `INCOMPLETE`.

---

## 15. Exit Decision

**Approved for next milestone**

Milestone reference: `docs/stage-updates/milestone-3c-stage-update.md`
