# Desktop Shell Module — Stage Update

**Module:** `@venture/desktop`  
**Milestone:** 4D  
**Last updated:** 2026-07-30  
**Path:** `apps/venture_desktop/`

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-22 | Initial Electron shell (4A) |
| 2026-07-23 | 4B company selection, 4C lifecycle manager |
| 2026-07-23 | **4D configuration, diagnostics, production readiness** |
| 2026-07-30 | Package Connector **0.4.0** (vouchers); sync VERSION.txt; rebuild connector in `dist:win` |

---

## Current status

**Completed — Live Validated (v0.4.3)** · current source/config packaging is aligned to Connector **0.4.0**; this is not a newly built, checksum-verified, retained, or approved installer (see `docs/stage-updates/milestone-5b-stage-update.md` §37–§38)

---

## Production Readiness: 86%

---

## Completed capabilities (4D additions)

- Centralized typed configuration with persistence and env overrides
- Functional Settings screen (save, restore, validation, restart notice)
- Diagnostics center (refresh, copy, export, health check, logs folder)
- Structured file logging with rotation and redaction
- IPC allowlist and security hardening
- Production readiness checker script
- Corrupt settings recovery

---

## Test evidence

65/65 desktop tests. Live 11/11. Readiness checker 10/10.

---

## Known limitations

- Tally host/port in settings stored but not yet passed to connector on spawn
- No packaged installer or code signing
- Formal security audit pending

---

## Production Readiness Level

**L4 — Live Validated**

---

## Exit decision

**Approved for commit.** Not approved for production deployment.

Milestone reference: `docs/stage-updates/milestone-4d-stage-update.md`

---

## Validation steps

1. Launch desktop — settings load from userData or defaults
2. Change connector port in Settings, save, verify restart notice
3. Open Diagnostics — refresh, export bundle, run health check
4. Run `node dist/scripts/live-4d-validation.js`
5. Run `node dist/scripts/production-readiness-check.js`

---

## Recovery behaviour

- Corrupt config → backup or defaults restored automatically
- Log directory unavailable → in-memory logs continue
- Export failure → clear error message; copy summary still available
- External connector → stop/restart blocked with user message

---

## Technical debt

TD-003 resolved (4C). TD-001 open (5A).

---

## Production gate: 9/11

