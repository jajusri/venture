# Desktop Shell Module — Stage Update

**Module:** `@budcom/desktop`  
**Milestone:** 4C  
**Last updated:** 2026-07-23T00:50:00+05:30  
**Path:** `apps/budcom_desktop/`

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-22 | Initial Electron shell (4A) |
| 2026-07-23 | Milestone 4B — live company selection, dashboard integration |
| 2026-07-23T00:16+05:30 | Live validated — 8/8 scenarios PASS (L4) |
| 2026-07-23T00:50+05:30 | Milestone 4C — connector lifecycle manager; 7/7 lifecycle validation PASS |

---

## 1. Current status

**Completed — Live Validated**

---

## 2. Production Readiness

| Area | % |
|------|---|
| Overall | 82% |
| Architecture | 95% |
| Implementation | 92% |
| Testing | 88% |
| Integration | 90% |
| Live Validation | 95% |
| Security | 75% |
| Performance | 68% |
| Documentation | 90% |

---

## 3–4. Files

See `docs/stage-updates/milestone-4c-stage-update.md` §3–4 for 4C additions.

---

## 5. Completed Capabilities

Electron shell with dashboard, connection indicator, session-bound company card, sync card, logs panel, footer metadata, company picker, loading states, retry/recovery, settings view, **connector lifecycle panel** (start/stop/restart, state labels, last health check, restart attempts), **auto-start connector on launch**.

---

## 6. Test Evidence

48/48 desktop tests pass. Live lifecycle validation: `docs/diagnostics/m4c-live-lifecycle-validation.json`.

---

## 7. Known Bugs

M4A-001 — screenshot capture IPC timing (cosmetic).

---

## 8. Known Limitations

- Cannot stop/restart connector started outside desktop (manual `npm start`)
- Dev default port 8080 may conflict — configure via env or settings
- Packaged connector binary not yet bundled with desktop installer
- Requires connector built at `connector/budcom_connector/dist/main.js` for dev auto-start

---

## 9. Assumptions

Connector HTTP API stable at `/health`, `/session`, `/session/validate`. Connector accepts `BUDCOM_CONNECTOR_PORT` env when spawned by desktop.

---

## 10. Risk Level

**Medium**

---

## 11. Likely Future Bug Locations

`ConnectorLifecycleService`, `DashboardService`, IPC layer, port conflict handling.

---

## 12. Deferred Improvements

Installer, connector log file ingestion, packaged binary, code signing, security review.

---

## 13. Dependencies

Connector service (3B–3D APIs). Node.js for dev connector spawn.

---

## 14. Production Readiness Level

**L4 — Live Validated**

---

## 15. Exit Decision

**Approved for commit**

Not approved for production deployment (installer, signing, security review pending).

Milestone reference: `docs/stage-updates/milestone-4c-stage-update.md`

---

## Architecture notes

- Renderer has zero business logic — DOM updates only
- Application layer owns HTTP, lifecycle orchestration, and mapping
- Main process owns window lifecycle, IPC, and connector process spawn
- Session state is never stored in desktop app memory beyond display refresh
- Lifecycle service uses injectable spawner/checker for testability

---

## Production gate

8/11 checks complete — see milestone 4C stage update.

---

## Technical debt

TD-002 **resolved** in 4B. TD-003 **resolved** in 4C.

---

## Validation steps

1. Build connector and desktop
2. Launch desktop — verify connector auto-starts (lifecycle **Connected**)
3. Stop/start/restart via lifecycle panel
4. Kill connector process — verify reconnect
5. Run `node dist/scripts/live-lifecycle-validation.js` for automated 7/7 check

---

## Recovery behaviour

- Connector crash → exponential backoff restart (max 5 attempts)
- Health loss while connected → reconnecting state + retry
- App quit → graceful SIGTERM for managed connector only
- External connector → attach without duplicate spawn; clear message on stop/restart
