# Android Master Data Cache

**Scope:** `apps/budcom_android`  
**Related:** `docs/DECISIONS.md` (2026-07-29), ADR-004 offline philosophy

## Roles

| Layer | Role |
| --- | --- |
| Connector HTTP APIs | **Source of Truth** |
| Room (`budcom.db`) | **Source of Availability** (companies metadata, ledgers, stock items) |
| DataStore selected company id | Selection cache only (session SoR remains Connector) |
| UI / Search | Repository consumers — no online/offline branching |

## Repository flow

```
load/refresh
  → Remote GET
      → Success: map DTO → domain → write Room → return domain
      → Failure: read Room for selected company → return domain if present
                 else map NetworkError → AppError
```

Unfiltered **page 1** success additionally warms remaining pages (bounded) and **atomically replaces** the company-scoped table only when every page succeeds. Mid-warm failure **upserts** without deleting prior rows.

## Cache lifecycle

| Event | Behavior |
| --- | --- |
| Successful browse/refresh | Update Room (replace or upsert) |
| Transport / Connector down | Preserve Room; serve cached pages |
| App process kill / phone reboot | Room survives |
| Connector restart + online refresh | Fresh GET overwrites Room |
| Explicit clear | Not implemented (future user action) |

## Out of scope

Vouchers, authentication, dashboard redesign, sync-history UI, inventing accounting rows.
