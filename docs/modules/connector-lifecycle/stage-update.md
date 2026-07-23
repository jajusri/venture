# Connector Lifecycle Module — Stage Update

**Module:** Connector Lifecycle Supervisor  
**Milestone:** 4C (base), 4D (integration)  
**Last updated:** 2026-07-23  
**Path:** `apps/budcom_desktop/src/application/connector-lifecycle-*.ts`

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-23 | Milestone 4C — initial lifecycle supervisor |
| 2026-07-23 | Milestone 4D — config-driven lifecycle, PID in diagnostics, external connector guards |

---

## Current status

**Completed — Live Validated**

---

## 4D enhancements

- Lifecycle config resolved from centralized settings + env
- `managedProcessPid` exposed in lifecycle status
- Restart/stop guarded for externally managed connectors
- Runtime re-initialization when restart-required settings saved
- Structured lifecycle logs feed diagnostics panel

---

## Production readiness

**L4 — Live Validated (~85%)**

---

## Deferred

- PID in connector `/health` response for stronger ownership detection
- Packaged connector binary path in settings UI
