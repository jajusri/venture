# ADR-004: Offline Philosophy

**Series:** Android companion (`apps/budcom_android`)  
**Status:** Accepted  
**Date:** 2026-07-27  
**Related:** [`PRODUCT_SOUL.md`](../../PRODUCT_SOUL.md) (Offline Intelligence), Connector reliability rules

## Context

Phones lose network. Connectors go offline. Tally may be unreachable while the Connector process is up. Users still need a useful companion: last-known operational state, saved configuration, and clear explanations. A blank “error only” app violates the product soul.

At the same time, BUDCO is not a second ERP. Live authoritative accounting state and any future write-like operations (if ever approved) remain Connector/ERP concerns.

## Decision

Adopt an **offline-aware** philosophy for Android:

| Condition | Expected behaviour |
| --- | --- |
| Device offline | Show cached / last-known usable data; explain device offline |
| Connector unavailable | Preserve configuration and prior probe results where safe; mark connection unavailable |
| Ready = not ready | Distinguish from total failure; show readiness explicitly |
| Session invalid | Keep selected company identity if locally known; require re-validation/selection paths |
| No local cache yet | Honest empty/loading states — never invent business rows |

### Cached data

- Small configuration and selection state may live in DataStore (Connector URL, selected company id).
- Broader offline caches (Room entities) appear only when a milestone defines a real schema.
- Cached business data must be labelled or implied as potentially stale; do not present it as live Connector truth without qualification.

### Manual synchronization

Until Automation milestones land, refresh is primarily user-driven (pull-to-refresh, explicit actions) or screen-entry loads. Background WorkManager jobs may be added later under bounded policies.

### Stale data indicators

UI must make limitations visible:

- offline banners
- last successful health / validation timestamps when known
- readiness not-ready distinct from connected
- partial success (URL present while health fails, and similar)

### Retry behaviour

- Use shared retry policy only for appropriate idempotent GETs.
- Bound attempts; never retry indefinitely.
- Preserve usable prior content while a refresh is in flight.

### Why editing still requires the Connector

MVP posture is **read-only toward Tally**. Even when local caches exist:

- the companion must not invent ledger/voucher mutations locally as if they were posted to the ERP
- any operation that changes ERP-backed state requires a live, authorized Connector path defined by an approved specification
- local drafts, if introduced later, must be explicitly draft-scoped and sync-reconciled — out of scope until decided

Offline intelligence means **useful viewing, diagnosis, and preserved work**, not a disconnected second books of account.

## Consequences

- Features must design partial-success states up front.
- Room is deferred until offline entities are real.
- Product soul pillars (smart notifications, automation) build on this philosophy but do not override Connector authority.
