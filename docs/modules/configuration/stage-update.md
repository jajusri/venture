# Configuration Module — Stage Update

**Module:** Desktop Configuration  
**Milestone:** 4D  
**Last updated:** 2026-07-23  
**Path:** `apps/budcom_desktop/src/application/desktop-config-*.ts`

---

## Current status

**Completed — Live Validated**

---

## Capabilities

- Typed `DesktopConfigV1` schema with validation
- Environment variable overrides (`BUDCOM_*`)
- Persisted settings at `{userData}/desktop-config.json`
- Atomic writes with backup and corrupt-file recovery
- Migration-ready `schemaVersion` field
- Settings UI with save/restore/validation
- Restart-required detection for port and lifecycle fields

---

## Configuration precedence

1. Environment variables (highest)
2. Persisted user settings
3. Development/production defaults (lowest)

---

## Storage location

| Platform | Path |
|----------|------|
| Windows | `%APPDATA%/budcom-desktop/` (Electron `userData`) |
| Config file | `desktop-config.json` |
| Backup | `desktop-config.backup.json` |
| Logs | `logs/budcom-desktop.log` |
| Exports | `diagnostics-exports/` |

---

## Test evidence

17+ unit tests in `test/unit/milestone-4d.test.ts`

---

## Known limitations

- Tally host/port stored for future connector integration; not yet passed to connector spawn env in all paths
- Environment overrides cannot be overridden by UI (by design)

---

## Production readiness

**L4 — Live Validated**
