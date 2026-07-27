# Confirmed Connector Contracts — Diagnostics Foundation (Android)

**Date:** 2026-07-27  
**Source of truth:** `connector/budcom_connector` implementation  
**Purpose:** Contract notes for BUDCO Android Diagnostics Foundation

---

## Confirmed diagnostic / operational endpoints

| Method | Route | Role |
| --- | --- | --- |
| GET | `/health` | Connector health snapshot |
| GET | `/ready` | Readiness (200 ready / 503 not_ready) |
| GET | `/session` | Session + selected company projection |
| POST | `/session/validate` | Session validation |
| GET | `/diagnostics/connection` | Sanitized Tally connection diagnostics |
| GET | `/sync/ledgers/status` | Live ledger sync progress |
| GET | `/sync/ledgers/statistics` | Ledger stats including `lastSyncedAt` |
| GET | `/sync/stock-items/status` | Live stock-item sync progress |
| GET | `/sync/stock-items/statistics` | Stock stats including `lastSyncedAt` |

There is **no** dedicated `GET /version` or `GET /build`. Connector version is `connectorVersion` on `/health` (and on session).

---

## `/health` fields (confirmed)

`status`, `schemaVersion`, `connectorVersion`, `tallyReachable`, `readOnly`, `bindHost`, `bindPort`, `networkExposure`, `networkExposureWarning`, `networkPolicySatisfied`, `authenticatedLanAccessEnabled`, `services[]` (`name`, `running`, `ready`, `message?`), `startupCorrelationId`, `repositoryAvailable`, `databaseAccessible`

## `/ready` fields (confirmed)

`status`, `repositoryAvailable`, `databaseAccessible`, `voucherSynchronizationComposed`, `voucherApplicationComposed`

## `/diagnostics/connection` response

```json
{ "schemaVersion": "1.0.0", "connection": <SafeConnectionDiagnostic> }
```

Allowlisted connection fields: `state`, `host`, `port`, `lastSuccessfulPingAt?`, `lastErrorAt?`, `lastErrorCode?`, `lastErrorMessage?`, `totalRequests`, `failedRequests`, `reconnectAttempts`, `averageLatencyMs`, `poolActiveConnections`, `poolWaitingRequests`, `safeMode`, `circuitState`, `lastRequest?`, `runtimeLimits`.

## Explicitly not available / deferred

| Item | Notes |
| --- | --- |
| `GET /version`, `GET /build` | Do not invent |
| Build id / git SHA on Connector | Not on health/diagnostics routes |
| Diagnostic bundle export | Desktop-oriented; not Android foundation |
| `/diagnostics/extraction` | Confirmed but deferred (not required for first Diagnostics screen) |
| Public voucher sync diagnostics | No `/sync/vouchers/*` |
| Clear cache / restart Connector / delete data | Not Diagnostics Foundation actions |

---

## Android Diagnostics Foundation interpretation

| Section | Source |
| --- | --- |
| Application | Android `BuildConfig` (`APP_NAME`, `VERSION_NAME`, `VERSION_CODE`, `DEBUG`) |
| Connector health/ready/URL | `ConnectorStatusPort.probeConnection()` + base URL |
| Company / session | `CompanySessionPort` |
| Synchronization | `ObserveSyncStatusPort` (+ optional overview refresh) |
| Connection pool/detail | `GET /diagnostics/connection` |
| Search | App capability statement only (no Connector search health endpoint) |
| Master Data freshness | Connector `lastSyncedAt` from sync statistics when present |
| Vouchers | Disclose public sync unavailable |

### Actions (confirmed / safe)

- Refresh diagnostics (re-probe health/ready, session, connection, sync overview)
- Open Server configuration / Company selection for remediation

### Non-goals

- Fabricated health or estimated readiness
- Duplicate independent health polling loops outside shared ports
- Export logs, clear cache, restart Connector
- WorkManager / analytics / crash reporting

### Source files

- `src/api/routes/health.ts`
- `src/api/routes/diagnostics.ts`
- `src/diagnostics/diagnostic-allowlist.ts`
- `src/api/routes/session.ts`
- `src/api/routes/ledgers.ts` / `stock-items.ts`
