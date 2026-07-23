# Milestone 4D — Configuration, Diagnostics & Production Readiness

**Status:** **Completed — Live Validated**  
**Date:** 2026-07-23  
**Depends on:** Milestone 4C (connector lifecycle), 4B (company selection), 3D (session API)

## Objective

Complete the desktop foundation with production-grade configuration, diagnostics, recovery controls, security hardening, and operational readiness validation.

## Versions

| Component | Version |
|-----------|---------|
| Desktop | `@budcom/desktop` **0.4.3** |
| Connector | `@budcom/connector` **0.3.1** |

## Architecture

```
Renderer (Settings + Diagnostics UI)
  ↕ typed preload bridge (IPC allowlist)
Main Process
  ├── SettingsService → DesktopConfigStore → desktop-config.json
  ├── DesktopConfigResolver (env > persisted > defaults)
  ├── ConnectorLifecycleService (config-driven)
  ├── DiagnosticsService → sanitized bundle export
  ├── LogService → FileLogWriter (rotation + redaction)
  └── RecoveryService (corrupt config, operational failures)
```

## Configuration precedence

1. **Environment variables** (`BUDCOM_CONNECTOR_URL`, `BUDCOM_CONNECTOR_PORT`, `BUDCOM_LOG_LEVEL`, etc.)
2. **Persisted settings** (`{userData}/desktop-config.json`)
3. **Defaults** (production or development)

## Configuration storage

| Item | Location |
|------|----------|
| Config | `%APPDATA%/budcom-desktop/desktop-config.json` |
| Backup | `desktop-config.backup.json` |
| Logs | `logs/budcom-desktop.log` (5 × 1MB rotation) |
| Diagnostics exports | `diagnostics-exports/` |

## IPC contracts (new in 4D)

| Channel | Purpose |
|---------|---------|
| `desktop:save-settings` | Validate + persist settings |
| `desktop:restore-default-settings` | Restore defaults |
| `desktop:validate-settings` | Pre-save validation |
| `desktop:get-diagnostics` | Diagnostics snapshot |
| `desktop:refresh-diagnostics` | Refresh snapshot |
| `desktop:copy-diagnostics-summary` | Text summary |
| `desktop:export-diagnostics-bundle` | JSON bundle export |
| `desktop:open-logs-folder` | Open logs in OS shell |
| `desktop:clear-nonessential-logs` | Clear non-error logs |
| `desktop:run-health-check` | On-demand health check |
| `desktop:reload-renderer` | Reload UI |

## Live validation — 11/11 PASS

Evidence: `docs/diagnostics/m4d-live-validation.json`

| Scenario | Result |
|----------|--------|
| Valid persisted settings | PASS |
| Corrupt settings recovery | PASS |
| Settings save | PASS |
| Connector auto-start | PASS |
| No duplicate process | PASS |
| Diagnostics refresh | PASS |
| Copy diagnostics | PASS |
| Export bundle | PASS |
| Bundle sanitization | PASS |
| Log retention | PASS |
| External connector detection | PASS |

## Tests

| Suite | Result |
|-------|--------|
| Desktop | **65/65** pass (15 files) |
| Connector | **244/244** pass |
| Build | Pass |

## Production gate

**9/11** — installer, code signing, formal security audit remain open.

## Readiness level

**L4 — Live Validated (~86%)**

## Remaining risks

- Tally host/port settings not yet forwarded to connector spawn environment
- Formal security audit not performed
- No installer/code signing

## Technical debt

No new TD items. TD-003 remains resolved.

## Recommended next milestone

**5A** — ERP data normalization (TD-001), packaged connector binary, installer prototype.
