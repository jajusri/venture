# Milestone 3D — Stage Update

**Milestone:** Company Selection & Session Management  
**Last updated:** 2026-07-22  
**Connector version:** 0.3.1  
**Git commit:** Not committed (working tree)  
**Depends on:** Milestone 3C (`e96083f`)

---

## Update history

| Date | Author | Summary |
|------|--------|---------|
| 2026-07-22 | Engineering | Initial stage update after implementation and automated verification |

---

## 1. Current status

**Needs Hardening**

Implementation and automated tests complete. Live Tally validation not yet performed. Changes not committed.

---

## 2. Production Readiness

| Area | % | Notes |
|------|---|-------|
| **Overall** | **62%** | Integrated and tested; no live validation; in-memory session only |
| Architecture | 90% | ERP-neutral session types; pure validator; no adapter leakage |
| Implementation | 85% | Selection, validation, HTTP routes, master-data gate complete |
| Testing | 85% | 21 new tests (9 unit + 12 integration); 244/244 total pass |
| Integration | 80% | DI wired; master-data requires session; health reports session |
| Live Validation | 0% | Not executed |
| Security | 80% | Session routes are connector-local; read-only middleware updated |
| Performance | 70% | CompanyResolver cache shared; validation reuses 60s discovery cache |
| Documentation | 75% | Milestone doc complete; stage update added; no live validation report |

---

## 3. Files Added

- `connector/budcom_connector/src/erp/session/session-constants.ts`
- `connector/budcom_connector/src/erp/session/connector-session.ts`
- `connector/budcom_connector/src/erp/session/session-results.ts`
- `connector/budcom_connector/src/services/interfaces/connector-session.ts`
- `connector/budcom_connector/src/services/session/session-validator.ts`
- `connector/budcom_connector/src/services/session/connector-session.service.ts`
- `connector/budcom_connector/src/services/session/session-error-mapper.ts`
- `connector/budcom_connector/src/api/routes/session.ts`
- `connector/budcom_connector/test/unit/session/session-validator.test.ts`
- `connector/budcom_connector/test/integration/connector-session.test.ts`
- `connector/budcom_connector/test/helpers/session-mock.ts`
- `docs/milestones/milestone-3d-company-selection.md`
- `docs/stage-updates/milestone-3d-stage-update.md`

---

## 4. Files Modified

- `connector/budcom_connector/src/core/tokens.ts`
- `connector/budcom_connector/src/config/defaults.ts`
- `connector/budcom_connector/src/config/index.ts`
- `connector/budcom_connector/src/bootstrap/register-services.ts`
- `connector/budcom_connector/src/api/server.ts`
- `connector/budcom_connector/src/api/middleware/read-only.ts`
- `connector/budcom_connector/src/services/extraction/company-resolver.ts`
- `connector/budcom_connector/src/services/extraction/master-data.service.ts`
- `connector/budcom_connector/src/services/health/health-service.ts`
- `connector/budcom_connector/src/services/placeholders/api-server.stub.ts`
- `connector/budcom_connector/test/helpers/test-context.ts`
- `connector/budcom_connector/test/integration/groups.test.ts`
- `connector/budcom_connector/test/integration/master-data.test.ts`
- `connector/budcom_connector/test/unit/tally/groups-adapter.test.ts`

---

## 5. Completed Capabilities

- `GET /session` — current immutable session snapshot
- `POST /session/company` — select company with structured status (no throws for expected failures)
- `DELETE /session/company` — clear selection
- `POST /session/validate` — explicit session validation
- Pre-flight session validation before every master-data ERP request
- Typed statuses: `NO_COMPANY_SELECTED`, `COMPANY_NOT_FOUND`, `COMPANY_NOT_ACCESSIBLE`, `SESSION_INVALID`, `SESSION_EXPIRED`, `SUCCESS`
- Selection statuses: `EMPTY_SELECTION`, `DUPLICATE_SELECTION`, `INVALID_COMPANY`, etc.
- Session TTL configurable via `BUDCOM_SESSION_TTL_MS` (default 8 hours)

---

## 6. Test Evidence

| Type | Result |
|------|--------|
| Unit tests | `session-validator.test.ts` — 9/9 pass |
| Integration tests | `connector-session.test.ts` — 12/12 pass |
| Live Tally tests | **Not executed** |
| Build status | **2026-07-22:** lint pass, **244/244** tests pass, build pass, 12/12 architecture |

---

## 7. Known Bugs

None confirmed in automated tests.

---

## 8. Known Limitations

- Session state is **in-memory only** — lost on connector restart
- Single connector instance assumed; no multi-device session binding
- Master-data routes require prior `POST /session/company` (breaking change for clients that skipped selection)
- ERP type fixed to `tally` constant
- Session validation depends on cached company discovery (60s TTL) — stale company list possible within window
- No live validation of selection/recovery flows with real Tally

---

## 9. Assumptions

- One active company per connector session is sufficient for MVP
- Connector-local session writes are not Tally writes (allowed through read-only middleware)
- Company discovery snapshot from `CompanyResolver` is authoritative enough for pre-flight validation
- Clients will adopt select-then-extract flow

---

## 10. Risk Level

**Medium**

Behaviour change for all master-data consumers. In-memory session may surprise operators after restart.

---

## 11. Likely Future Bug Locations

- `connector-session.service.ts` — connection status vs Tally reachability divergence
- `session-validator.ts` — TTL edge cases, company metadata drift detection
- `master-data.service.ts` — session gate vs `CompanyResolver` 404 mismatch
- `company-resolver.ts` — cache invalidation on company rename in Tally
- `read-only.ts` — session route allowlist maintenance

---

## 12. Deferred Improvements

- Persist session to LocalDatabase
- Device pairing → default company binding
- Live Tally validation checklist (mirror 3B/3C)
- Invalidate discovery cache on explicit company change in Tally
- Session metrics in diagnostics endpoint

---

## 13. Dependencies

- Milestone 3C — groups and master-data routes
- Milestone 3B — company discovery and `CompanyResolver`
- Milestone 3A — secure foundation

---

## 14. Production Readiness Level

**L3 — Integrated**

---

## 15. Exit Decision

**Not approved for production**

**Reason:** Live Tally validation not performed. Session persistence not implemented. Changes not committed. Approved to proceed with **3D live validation** and hardening before next feature milestone.

**Approved for next milestone:** Only after 3D live validation and commit.
