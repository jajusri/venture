# Connector Session Module — Stage Update

**Module:** Company Selection & Session Management  
**Milestone:** 3D  
**Last updated:** 2026-07-22  
**Owner layer:** `services/session/`, `erp/session/`, `api/routes/session.ts`

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-22 | Initial module stage update after implementation |

---

## 1. Current status

**Needs Hardening**

---

## 2. Production Readiness

| Area | % |
|------|---|
| Overall | 62% |
| Architecture | 90% |
| Implementation | 85% |
| Testing | 85% |
| Integration | 80% |
| Live Validation | 0% |
| Security | 80% |
| Performance | 70% |
| Documentation | 75% |

---

## 3. Files Added

See `docs/stage-updates/milestone-3d-stage-update.md` §3.

---

## 4. Files Modified

See `docs/stage-updates/milestone-3d-stage-update.md` §4.

---

## 5. Completed Capabilities

- Immutable `ConnectorSession` snapshots
- Company selection with structured error statuses
- Pre-flight validation on all master-data ERP calls
- Session HTTP API (`/session`, `/session/company`, `/session/validate`)
- Configurable session TTL
- Health service reports session status

---

## 6. Test Evidence

- Unit: `session-validator.test.ts` (9)
- Integration: `connector-session.test.ts` (12)
- Live Tally: **not executed**
- Full suite: **244/244 pass** (2026-07-22)

---

## 7. Known Bugs

None in automated tests.

---

## 8. Known Limitations

- In-memory session only
- No device binding
- Master-data clients must select company first
- 60s discovery cache may lag Tally company changes

---

## 9. Assumptions

- Single selected company per connector instance
- Session routes are connector-local (not Tally writes)

---

## 10. Risk Level

**Medium**

---

## 11. Likely Future Bug Locations

- `connector-session.service.ts` — reachability vs connection status
- `company-resolver.ts` — stale cache after Tally company rename
- `session-error-mapper.ts` — HTTP code mapping for new statuses
- Client migration to select-before-extract flow

---

## 12. Deferred Improvements

- LocalDatabase persistence
- Live validation checklist
- Cache invalidation API
- Multi-instance session coordination

---

## 13. Dependencies

- Company discovery module (3B)
- CompanyResolver discovery snapshot
- Master data extraction service

---

## 14. Production Readiness Level

**L3 — Integrated**

---

## 15. Exit Decision

**Not approved for production**

Requires live Tally validation and commit before production use.

Milestone reference: `docs/stage-updates/milestone-3d-stage-update.md`
