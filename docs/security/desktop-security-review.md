# Desktop Security Review

**Component:** `@venture/desktop` v0.4.3  
**Date:** 2026-07-23  
**Milestone:** 4D

---

## Controls already present (4A–4C)

| Control | Status | Location |
|---------|--------|----------|
| `contextIsolation: true` | Enabled | `src/main/main.ts` |
| `nodeIntegration: false` | Enabled | `src/main/main.ts` |
| `sandbox: true` | Enabled | `src/main/main.ts` |
| Content Security Policy | Enabled | `src/renderer/index.html` |
| Narrow preload bridge | Enabled | `src/preload/preload.ts` |
| No business logic in renderer | Enforced | architecture |
| Session state not duplicated | Enforced | application layer |

---

## Controls added in 4D

| Control | Description |
|---------|-------------|
| IPC allowlist | `src/application/ipc-allowlist.ts` — only registered channels accepted |
| Main-process validation | Settings, company IDs, export paths validated in main |
| No renderer filesystem access | All config/log/diagnostics paths resolved in main |
| Diagnostics redaction | `src/application/log-redaction.ts` strips secrets from exports/logs |
| Blocked window.open | `setWindowOpenHandler(() => ({ action: 'deny' }))` |
| Blocked navigation | `will-navigate` prevented in main window |
| Production devtools policy | DevTools disabled outside development |
| Structured logging redaction | Bearer tokens, passwords, secret keys redacted |
| External connector guard | Stop/restart blocked for externally managed connectors |
| Atomic config writes | Temp file + rename; backup on save |

---

## IPC contract summary

All renderer access goes through `window.ventureDesktop` with typed invoke handlers. No generic `invoke(channel, payload)` escape hatch.

Settings payloads are validated against `DesktopConfigV1` schema in main before persistence.

---

## Remaining security-review items

| Item | Priority | Notes |
|------|----------|-------|
| Formal third-party security audit | P2 | Not performed |
| Code signing | P2 | Deferred |
| Auto-update trust chain | P2 | No updater yet |
| Certificate pinning for connector | P3 | Localhost-only today |
| Penetration test of IPC surface | P2 | Allowlist reviewed in unit tests |

---

## Accepted risks

| Risk | Rationale |
|------|-----------|
| Local connector trust | Desktop and connector run on same machine; localhost HTTP acceptable for MVP |
| Operator can edit config file | File is schema-validated on load; corrupt files recovered |
| Electron sandbox compatibility | Required dependencies use preload bridge only |

---

## Verification steps

1. Confirm renderer cannot access `require`, `fs`, or `process`
2. Attempt unknown IPC channel — should throw in main registration path
3. Export diagnostics — verify no secrets in bundle JSON
4. Inspect persisted config — no secret fields in schema
