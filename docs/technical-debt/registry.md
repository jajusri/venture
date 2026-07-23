# Technical Debt Registry

Engineering-tracked compromises, defects, and deferred work.  
**Do not remove entries** — update status and append resolution notes.

---

## TD-001 — Parent encoding normalization

| Field | Value |
|-------|-------|
| **ID** | TD-001 |
| **Description** | Tally returns group parent as `"&#4; Primary"` (XML character entity + "Primary"). Hierarchy validator recognizes only normalized `"Primary"` as virtual root. Built-in root groups flag `MISSING_PARENT`; extraction status is `INCOMPLETE`. |
| **Impact** | Medium — hierarchy not fully trusted; 15 false positives observed on ESTIMATION (28 groups) |
| **Priority** | P2 |
| **Estimated fix** | 2 hours |
| **Target milestone** | 5A |
| **Status** | Open |
| **Introduced** | Milestone 3C live validation (2026-07-22) |
| **Evidence** | `docs/testing/milestone-3c-groups-manual-validation.md`; bug ID M3C-001 in `docs/stage-updates/milestone-3c-stage-update.md` |
| **Likely fix location** | `connector/budcom_connector/src/tally/groups/hierarchy-validator.ts`, normalization layer |
| **Workaround** | Consumers must treat `INCOMPLETE` + `MISSING_PARENT` for `"&#4; Primary"` as known quirk until fixed |

---

## TD-002 — Desktop company selection UI

| Field | Value |
|-------|-------|
| **ID** | TD-002 |
| **Description** | Desktop shell displays session company but cannot select/change company via UI |
| **Impact** | Medium — operators must use API or future tooling |
| **Priority** | P2 |
| **Estimated fix** | 4 hours |
| **Target milestone** | 4B |
| **Status** | **Resolved** (2026-07-23) |
| **Introduced** | Milestone 4A (2026-07-22) |
| **Resolution** | Milestone 4B — `CompanyService`, company picker UI, IPC `desktop:select-company` |

---

## TD-003 — Connector process supervision

| Field | Value |
|-------|-------|
| **ID** | TD-003 |
| **Description** | Desktop app requires connector started manually; no spawn/health supervision |
| **Impact** | Medium — poor operator experience on startup |
| **Priority** | P2 |
| **Estimated fix** | 6 hours |
| **Target milestone** | 4C |
| **Status** | **Resolved** (2026-07-23) |
| **Introduced** | Milestone 4A (2026-07-22) |
| **Resolution** | Milestone 4C — `ConnectorLifecycleService`, auto-start, health polling, crash recovery, lifecycle UI, IPC handlers |
| **Evidence** | `docs/diagnostics/m4c-live-lifecycle-validation.json` (7/7 PASS); 48/48 desktop tests |

---

## M4B-001 — Stale pre-3D connector deployment (RESOLVED)

| Field | Value |
|-------|-------|
| **ID** | M4B-001 |
| **Description** | Connector on :8080 was pre-3D build; session APIs returned 404 |
| **Status** | **Resolved** (2026-07-23T00:16+05:30) |
| **Resolution** | Stopped stale process; rebuilt and restarted latest connector; 8/8 live scenarios PASS |

---

## TD-005 — JSON ledger repository

| Field | Value |
|-------|-------|
| **ID** | TD-005 |
| **Description** | Ledger repository uses per-company JSON files instead of SQLite/embedded DB configured in connector defaults |
| **Impact** | Medium — no transactional incremental checkpoints; large datasets load full file |
| **Priority** | P2 |
| **Estimated fix** | 1 day |
| **Target milestone** | 5A-P |
| **Status** | **Resolved** (2026-07-23) |
| **Introduced** | Milestone 5A (2026-07-23) |
| **Resolution** | Milestone 5A-P — `SqliteLedgerRepository` on Node `node:sqlite`, WAL, migrations, company-scoped storage at `{databasePath}/budcom-ledger.db` |

---

## TD-006 — Durable interrupted sync resume

| Field | Value |
|-------|-------|
| **ID** | TD-006 |
| **Description** | Sync progress and resume state are in-memory only; connector restart loses interrupted sync checkpoint |
| **Impact** | Medium — operator must re-run full sync after crash |
| **Priority** | P2 |
| **Estimated fix** | 4 hours |
| **Target milestone** | 5A-P |
| **Status** | **Partially resolved** (2026-07-23) |
| **Introduced** | Milestone 5A (2026-07-23) |
| **Resolution** | Milestone 5A-P — `sync_runs` table, abandoned-run recovery on startup |
| **Residual limitation** | Full upsert resume from `lastProcessedId` not implemented; interrupted sync re-processes all extracted ledgers |

---

## TD-007 — Extraction-phase cancellation

| Field | Value |
|-------|-------|
| **ID** | TD-007 |
| **Description** | Ledger sync cancellation did not propagate through Tally extraction |
| **Impact** | Low — cancellation now works cooperatively at transport boundaries |
| **Priority** | P3 |
| **Estimated fix** | 2 hours |
| **Target milestone** | 5A-P |
| **Status** | **Resolved with accepted limitation** (2026-07-23) |
| **Introduced** | Milestone 5A (2026-07-23) |
| **Resolution** | Milestone 5A-P — `AbortSignal` chain, `POST /sync/ledgers/cancel`, HTTP abort |
| **Accepted limitation** | Tally XML response body cannot be interrupted mid-read |

---

## TD-008 — Connector network binding without authentication

| Field | Value |
|-------|-------|
| **ID** | TD-008 |
| **Description** | Connector previously defaulted to `0.0.0.0:8080` with no authentication |
| **Impact** | Medium — LAN clients could invoke sync/storage APIs if network-exposed |
| **Priority** | P2 |
| **Target milestone** | 5A-P sign-off |
| **Status** | **Resolved** (2026-07-23) — default bind is `127.0.0.1`; `0.0.0.0` rejected |
| **Introduced** | Pre-5A-P architecture |
| **Resolution** | Loopback-only default, host validation, diagnostics/readiness warnings for LAN mode |
| **Remaining limitation** | Non-loopback LAN mode has no authentication — see TD-009 |

---

## TD-009 — Authenticated LAN access for connector API

| Field | Value |
|-------|-------|
| **ID** | TD-009 |
| **Description** | Explicit LAN bind (`BUDCOM_CONNECTOR_HOST` non-loopback) is operator-acknowledged only; no authN/authZ |
| **Impact** | Medium — network-exposed connector remains trust-on-LAN |
| **Priority** | P2 |
| **Target milestone** | 5B or security hardening |
| **Status** | Open (future; disabled by default) |
| **Introduced** | Milestone 5A-P sign-off (2026-07-23) |
| **Mitigation today** | Default `127.0.0.1`, reject `0.0.0.0`, readiness check requires `BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED=true` |

---

## TD-004 — Desktop Tally settings not forwarded to connector spawn

| Field | Value |
|-------|-------|
| **ID** | TD-004 |
| **Description** | Desktop settings store Tally host/port but do not yet pass them to connector process environment on spawn |
| **Impact** | Low — Tally connection still configured in connector/Tally directly |
| **Priority** | P3 |
| **Estimated fix** | 2 hours |
| **Target milestone** | 5A |
| **Status** | Open |
| **Introduced** | Milestone 4D (2026-07-23) |

---

## Index

| ID | Summary | Priority | Status | Target |
|----|---------|----------|--------|--------|
| TD-001 | Parent encoding normalization (`&#4; Primary`) | P2 | Open | 5A |
| TD-002 | Desktop company selection UI | P2 | **Resolved** | 4B |
| TD-003 | Connector process supervision | P2 | **Resolved** | 4C |
| TD-004 | Tally host/port not forwarded to connector spawn | P3 | Open | 5A |
| TD-005 | JSON ledger repository | P2 | **Resolved** | 5A-P |
| TD-006 | Durable interrupted sync resume | P2 | **Partial** | 5A-P |
| TD-007 | Extraction-phase cancellation | P3 | **Resolved w/ limitation** | 5A-P |
| TD-008 | Insecure default network binding | P2 | **Resolved** | 5A-P |
| TD-009 | Authenticated LAN access | P2 | Open | 5B |
