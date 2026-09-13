# Milestone 4C — Stage Update

**Milestone:** Production Connector Lifecycle Manager  
**Last updated:** 2026-07-23T00:50:00+05:30  
**Package:** `@venture/desktop` v0.4.2 + `@venture/connector` v0.3.1  
**Git commit:** Not committed (working tree)

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-23 | Initial implementation — 48 tests, lifecycle service + UI |
| 2026-07-23T00:50+05:30 | **Live validation — 7/7 scenarios PASS; TD-003 resolved** |

---

## 1. Current status

**Completed — Live Validated**

---

## 2. Production Readiness

| Area | % | Notes |
|------|---|-------|
| **Overall** | **82%** | Lifecycle supervision complete; packaging items remain |
| Architecture | 95% | Clean separation; lifecycle in application layer |
| Implementation | 92% | All 4C requirements delivered |
| Testing | 88% | 48 desktop + 244 connector |
| Integration | 90% | Live lifecycle validation on isolated port |
| Live Validation | 95% | 7/7 scenarios PASS |
| Security | 75% | Spawn sandbox; no formal review |
| Performance | 68% | Non-blocking startup; not benchmarked |
| Documentation | 90% | Milestone + diagnostics complete |

---

## 3. Files added

| Path | Purpose |
|------|---------|
| `apps/venture_desktop/src/application/connector-lifecycle-types.ts` | Types, interfaces |
| `apps/venture_desktop/src/application/connector-lifecycle-config.ts` | Env-based config resolution |
| `apps/venture_desktop/src/application/connector-lifecycle-service.ts` | Core orchestrator |
| `apps/venture_desktop/src/application/node-process-spawner.ts` | `child_process` adapter |
| `apps/venture_desktop/src/application/lifecycle-error-mapper.ts` | User-friendly errors |
| `apps/venture_desktop/src/scripts/live-lifecycle-validation.ts` | Live validation script |
| `apps/venture_desktop/test/unit/connector-lifecycle-service.test.ts` | Lifecycle unit tests |
| `apps/venture_desktop/test/unit/lifecycle-error-mapper.test.ts` | Error mapper tests |
| `apps/venture_desktop/test/unit/connector-lifecycle-config.test.ts` | Config tests |
| `apps/venture_desktop/test/helpers/lifecycle-fixtures.ts` | Test helpers |
| `docs/milestones/milestone-4c-connector-lifecycle.md` | Milestone doc |
| `docs/diagnostics/m4c-live-lifecycle-validation.json` | Live validation evidence |

---

## 4. Files modified

| Path | Change |
|------|--------|
| `apps/venture_desktop/src/main/main.ts` | Lifecycle service wiring, IPC, init/shutdown |
| `apps/venture_desktop/src/preload/preload.ts` | Lifecycle bridge methods |
| `apps/venture_desktop/src/application/types.ts` | Settings: executable, port, autoStart |
| `apps/venture_desktop/src/application/dashboard-service.ts` | v0.4.2; extended settings |
| `apps/venture_desktop/src/renderer/index.html` | Lifecycle panel + settings fields |
| `apps/venture_desktop/src/renderer/scripts/app.ts` | Lifecycle UI rendering |
| `apps/venture_desktop/package.json` | Version 0.4.2 |
| `apps/venture_desktop/test/unit/main-window.test.ts` | Lifecycle IPC |
| `apps/venture_desktop/test/unit/dashboard-service.test.ts` | Version bump |
| `apps/venture_desktop/test/renderer/*.test.ts` | Lifecycle bridge mocks |
| `docs/modules/desktop-shell/stage-update.md` | 4C update |
| `docs/modules/connector-session/stage-update.md` | Lifecycle consumer note |
| `docs/technical-debt/registry.md` | TD-003 resolved |

---

## 5. Live validation evidence

| Field | Value |
|-------|-------|
| Date/time | 2026-07-23T00:50:21+05:30 |
| Connector | 0.3.1 |
| Desktop | 0.4.2 |
| Validation port | 18080 |
| Scenarios | **7/7 PASS** |
| Evidence | `docs/diagnostics/m4c-live-lifecycle-validation.json` |

---

## 6. Production gate

| Gate | Status |
|------|--------|
| Architecture reviewed | ☑ |
| Unit tests passing | ☑ (48/48 desktop, 244/244 connector) |
| Integration tests passing | ☑ |
| Live lifecycle validation | ☑ (7/7) |
| Crash recovery tested | ☑ |
| Graceful shutdown tested | ☑ |
| Duplicate prevention tested | ☑ |
| Documentation complete | ☑ |
| Security reviewed | ☐ |
| Performance benchmark | ☐ |
| Installer / code signing | ☐ |
| Approved for production | ☐ |

**Gate score: 8 / 11**

---

## 7. Technical debt

| ID | Status |
|----|--------|
| TD-003 — Connector process supervision | **Resolved** |
| TD-002 | Resolved (4B) |
| TD-001 | Open → 5A |

---

## 8. Production Readiness Level

**L4 — Live Validated**

---

## 9. Exit Decision

**Approved for commit and next milestone entry**

**Not approved for production deployment** — installer, code signing, security review, packaged connector binary remain open.

---

## 10. Remaining risks

| Risk | Level |
|------|-------|
| Port 8080 conflict with manual connector in dev | Medium |
| Cannot stop externally managed connector from desktop | Low |
| Connector restart clears in-memory session | Low — documented |
| No installer/code signing | Medium |

---

## Architecture notes

- Lifecycle runs in main process application layer — renderer is display-only
- `ConnectorLifecycleService` is injectable (mock spawner/checker in tests)
- Auto-start on `app.whenReady()` — UI remains responsive (async init)
- Structured logs use `[lifecycle:*]` prefix for filtering
