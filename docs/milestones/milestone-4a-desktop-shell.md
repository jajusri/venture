# Milestone 4A — Desktop Connector Shell

**Status:** Implemented (automated verification complete; live desktop + connector integration pending)  
**Date:** 2026-07-22  
**Depends on:** Milestone 3D (session management), connector API on `localhost:8080`

## Objective

First production-ready **Windows desktop application shell** for the Business OS Tally Connector. Display-only UI that communicates exclusively with the existing connector HTTP services.

## Architecture

```
Electron Renderer (UI only)
  ↓ contextBridge / IPC
Electron Main (window + IPC wiring)
  ↓
Application Layer (DashboardService, ConnectorHttpClient, mappers)
  ↓ HTTP
Connector Service (@venture/connector on :8080)
  ↓
ERP Adapter / Tally
```

**Rules enforced:**

- No business logic in UI or renderer scripts
- No XML access
- No adapter access
- No duplicated session state — reads authoritative `/session` from connector
- TypeScript strict mode, no `any`

## Technology

| Component | Choice |
|-----------|--------|
| Desktop runtime | Electron 33 |
| Language | TypeScript (strict) |
| UI | Static HTML/CSS + renderer TS |
| Tests | Vitest (+ jsdom for renderer) |

## Package location

`apps/venture_desktop` (`@venture/desktop` v0.4.0)

## UI surfaces

### Window header

- Title: Business OS Tally Connector
- Version, connection label, company, sync, last sync

### Top navigation

Dashboard · Connection · Logs · Settings · About

### Dashboard cards

| Card | Data source |
|------|-------------|
| Connection | `GET /health` + connection mapper |
| Company | `GET /session` (name, id, selection time, validation status) |
| Sync | `SyncEngine` entry in health services |
| Version | Health / session connector version |

### Connection indicator

| Colour | State |
|--------|-------|
| Green | Connected |
| Yellow | Waiting |
| Red | Disconnected |
| Grey | Unknown |

### Sync display

Maps `SyncEngine` service status → Ready / Idle / Syncing / Paused / Failed

### Footer

Connector version · ERP type · License status (from `Licensing` service)

### Logs panel

In-application log buffer populated from connector poll results (information / warning / error + timestamp). Scrollable.

## Application layer

| Module | Responsibility |
|--------|----------------|
| `ConnectorHttpClient` | HTTP to `/health`, `/session`, `/session/validate` |
| `DashboardService` | Aggregates connector responses into `DashboardState` |
| `connection-status-mapper` | Health → indicator colour |
| `sync-status-mapper` | Service list → sync label |
| `session-display-mapper` | Session snapshot → display fields |
| `LogService` | UI log panel buffer |

## Configuration

| Env var | Default |
|---------|---------|
| `VENTURE_CONNECTOR_URL` | `http://localhost:8080` |

Status polling interval: 5 seconds (main process → renderer IPC event).

## Tests

| File | Coverage |
|------|----------|
| `test/unit/connection-status-mapper.test.ts` | Indicator mapping |
| `test/unit/dashboard-service.test.ts` | Service aggregation with mock fetch |
| `test/unit/session-binding.test.ts` | Session display from connector snapshot |
| `test/unit/main-window.test.ts` | Window creation + IPC registration |
| `test/renderer/dashboard-render.test.ts` | Dashboard render + navigation |

**12 desktop tests** — all pass (2026-07-22).

## Screenshots

Generated via `npm run screenshot`:

- `apps/venture_desktop/screenshots/dashboard.png`

## Limitations

- Windows-focused shell; cross-platform not validated
- No company selection UI yet (displays connector session state only)
- SyncEngine / Licensing are connector placeholders → Idle / Evaluation labels
- Requires connector service running separately
- No installer / auto-update (out of scope)

## Not in scope

- Business logic, ERP parsing, Tally communication
- Embedded connector process management
- Production code signing
