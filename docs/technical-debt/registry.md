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

## Index

| ID | Summary | Priority | Status | Target |
|----|---------|----------|--------|--------|
| TD-001 | Parent encoding normalization (`&#4; Primary`) | P2 | Open | 5A |
| TD-002 | Desktop company selection UI | P2 | **Resolved** | 4B |
| TD-003 | Connector process supervision | P2 | **Resolved** | 4C |
