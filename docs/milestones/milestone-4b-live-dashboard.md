# Milestone 4B — Live Company Selection & Dashboard Integration

**Status:** **Completed — Live Validated**  
**Date:** 2026-07-23  
**Live validated:** 2026-07-23T00:16:00+05:30  
**Depends on:** Milestone 4A (desktop shell), Milestone 3D (session API), Milestone 3B (company discovery)

## Objective

Integrate the desktop shell with live connector REST APIs for company discovery, session binding, dashboard refresh, connection recovery, and operator-facing error handling — without duplicating business logic or session state.

## Versions used in live validation

| Component | Version |
|-----------|---------|
| Connector | `@budcom/connector` **0.3.1** (latest source, rebuilt) |
| Desktop | `@budcom/desktop` **0.4.1** |
| Live company | **ESTIMATION** (`estimation`) |
| Additional companies | Learn (`learn`) |

## Architecture

```
Electron Renderer (UI + loading/error display only)
  ↕ contextBridge / IPC
Electron Main (window, IPC, 5s polling)
  ↓
Application Layer
  ├── ConnectorHttpClient (retry + user-friendly errors)
  ├── DashboardService (health + session + validation aggregation)
  ├── CompanyService (discovery + selection orchestration)
  ├── connection-status-mapper / session-display-mapper
  └── LogService (in-memory UI event log)
  ↓ HTTP
Connector REST API (:8080)
  ↓
Connector Core → ERP Adapter → Tally
```

## Live validation — scenario results

| # | Scenario | Result | Evidence |
|---|----------|--------|----------|
| S1 | Connector Health | **PASS** | `GET /health` ok; desktop `Connected`; no 404 banner |
| S2 | Company Discovery | **PASS** | 2 live Tally companies; ESTIMATION + Learn |
| S3 | Company Selection | **PASS** | `POST /session/company` SUCCESS; card ACTIVE; timestamps update |
| S4 | Session Validation | **PASS** | `GET /session` + `POST /session/validate` SUCCESS; no duplicate session |
| S5 | Clear Selection | **PASS** | `DELETE /session/company` SUCCESS; NO_COMPANY_SELECTED |
| S6 | Reselect Company | **PASS** | Session ACTIVE; auto refresh; no desktop restart |
| S7 | Desktop Restart | **PASS** | New desktop service instance reads persisted connector session (in-memory) |
| S8 | Connector Loss/Recovery | **PASS** | Disconnected on stop; recovers on restart; companies reload; session resets per connector design |

**Verdict: 8/8 PASS**

Full evidence: `docs/diagnostics/m4b-live-validation-results.json`

## API availability (post redeploy)

| Endpoint | Status |
|----------|--------|
| GET /health | PASS |
| GET /companies | PASS |
| GET /session | PASS |
| POST /session/validate | PASS |
| POST /session/company | PASS |
| DELETE /session/company | PASS |

## Stale deployment blocker

**RESOLVED** — Pre-3D connector on `:8080` stopped; latest source rebuilt and restarted.

## Tests

| Suite | Result |
|-------|--------|
| Desktop | **33/33** pass |
| Connector | **244/244** pass |
| Desktop build | Pass |
| Connector build | Pass |

## Validation tooling

`apps/budcom_desktop/src/scripts/live-validation.ts` — exercises desktop application layer against live connector.

## Screenshots

- `apps/budcom_desktop/screenshots/dashboard-live.png`
- `apps/budcom_desktop/screenshots/settings.png`

## Known issues

| ID | Description | Status |
|----|-------------|--------|
| M4B-001 | Stale pre-3D connector deployment | **Resolved** |
| M4B-002 | Screenshot script IPC timing (cosmetic) | Open |

## Production readiness

**L4 — Live Validated** (overall ~78%)

## Deferred (4C+)

- TD-003 — Connector process supervision
- Security review, performance benchmark, installer, code signing
