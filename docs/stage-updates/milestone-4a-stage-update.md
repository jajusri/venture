# Milestone 4A — Stage Update

**Milestone:** Desktop Connector Shell  
**Last updated:** 2026-07-22  
**Package:** `@venture/desktop` v0.4.0  
**Git commit:** Not committed (working tree)

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-22 | Initial implementation — Electron shell, 12 tests, screenshot captured |

---

## 1. Current status

**Partial**

Shell implemented and automated tests pass. Live integration with running connector + formal production gate not complete.

---

## 2. Production Readiness

| Area | % | Notes |
|------|---|-------|
| **Overall** | **55%** | UI shell only; no installer; connector must run separately |
| Architecture | 90% | Clean separation: renderer → application → HTTP connector |
| Implementation | 80% | Dashboard, nav, logs, footer complete |
| Testing | 75% | 12 unit/renderer tests; no E2E with live connector |
| Integration | 50% | HTTP client ready; live connector+desktop session not validated |
| Live Validation | 10% | Screenshot generated; no operator live session |
| Security | 70% | contextIsolation, sandbox, no nodeIntegration; CSP on renderer |
| Performance | 60% | 5s poll; not benchmarked under load |
| Documentation | 80% | Milestone + stage update + module update |

---

## 3. Files Added

See `docs/milestones/milestone-4a-desktop-shell.md` and `apps/venture_desktop/` package (application layer, main, preload, renderer, tests, screenshots).

---

## 4. Files Modified

None outside new `apps/venture_desktop/` package (isolated desktop app).

---

## 5. Completed Capabilities

- Electron window with branded header and footer
- Dashboard / Connection / Logs / Settings / About navigation
- Connection indicator (green/yellow/red/grey)
- Company card from `/session` (no duplicated session state)
- Sync card from health services
- Scrollable log panel
- 5-second status polling via IPC
- Screenshot generation script

---

## 6. Test Evidence

| Type | Result |
|------|--------|
| Desktop unit/integration | **12/12 pass** |
| Connector regression | **244/244 pass** (unchanged) |
| Desktop build | Pass (`tsc` × 3 + asset copy) |
| Electron screenshot | `apps/venture_desktop/screenshots/dashboard.png` |
| Live connector + desktop | Not executed |

---

## 7. Known Bugs

| ID | Description |
|----|-------------|
| M4A-001 | Screenshot script loads renderer before IPC registered — benign console errors during capture |

---

## 8. Known Limitations

- Connector must be started separately (`connector/venture_connector`)
- No company selection UI (displays existing session only)
- Sync/License show placeholder-derived labels
- In-memory UI logs only (not connector file logs)
- Windows shell; macOS/Linux not validated

---

## 9. Assumptions

- Connector API remains on `http://localhost:8080`
- Session authority stays on connector (`GET /session`)
- Electron 33 acceptable for Business OS desktop target

---

## 10. Risk Level

**Medium**

New desktop surface area; client workflow now requires connector running before UI shows live data.

---

## 11. Likely Future Bug Locations

- `DashboardService` — partial connector failures / timeout handling
- `connection-status-mapper` — health message string drift
- IPC polling — memory/leaks on long sessions
- Renderer CSP vs future asset needs

---

## 12. Deferred Improvements

- Embed or supervise connector process from desktop
- Company selection UI wired to `POST /session/company`
- Read connector log file into Logs view
- Windows installer (MSIX/NSIS)
- E2E tests with running connector
- TD-001 downstream impact on company/session display

---

## 13. Dependencies

- Milestone 3D — session API (`/session`, `/session/validate`)
- Connector health API
- Milestone 3B — company discovery (indirect via session)

---

## 14. Production Readiness Level

**L2 — Unit Verified**

---

## 15. Exit Decision

**Not approved for production**

**Approved for next milestone:** After live desktop+connector validation and commit.

---

## Production Gate (4A)

| Gate | Status |
|------|--------|
| Architecture reviewed | ☑ |
| Unit tests passing | ☑ (12/12) |
| Integration tests passing | ☑ (mock HTTP) |
| Live Tally validation complete | ☐ (N/A — desktop shell) |
| Live connector + desktop validation | ☐ |
| Security reviewed | ☐ (basic hardening only) |
| Performance benchmark completed | ☐ |
| Documentation complete | ☑ |
| Breaking changes documented | ☑ (new app; no API breaks) |
| Recovery scenarios tested | ☐ |
| Approved for next milestone | ☐ |
| Approved for production | ☐ |

---

## Technical Debt

| ID | Description | Target |
|----|-------------|--------|
| TD-002 | Desktop company selection UI | 4B |
| TD-003 | Connector process supervision from desktop | 4C |
| TD-001 | Parent encoding (connector) | 5A — affects company/group display downstream |

See `docs/technical-debt/registry.md`.
