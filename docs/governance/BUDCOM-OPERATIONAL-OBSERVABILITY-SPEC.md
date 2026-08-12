# BUDCOM Operational Observability Specification

**Status:** Product-quality requirement  
**Purpose:** Ensure users and support can understand BUDCOM health without developer tools, guesswork, or hidden background behavior.

## 1. Principle

A healthy product should answer, visibly and truthfully:

- Is BUDCOM working?
- Is data fresh?
- What is connected?
- What is stale/offline?
- Is a sync/reconciliation running?
- What failed?
- Can the user safely continue?
- What should the user do next?

Observability must not expose secrets or raw sensitive business payloads.

## 2. Desktop minimum signals

Where applicable expose:

- Connector healthy/degraded/offline
- Tally reachable/unreachable
- selected company
- last refresh/sync
- current refresh/sync running state
- next automatic refresh where applicable
- fresh/stale/offline state
- storage mode
- Private Storage connected/missing/wrong-media state
- paired device count/status where useful
- actionable recovery message
- build/version information sourced from real build provenance, not hardcoded literals

## 3. Android minimum signals

Where applicable expose:

- securely connected / offline
- selected company
- local cache availability
- last successful sync
- sync running/result
- stale/coverage state
- historical reconciliation state
- partial-history/coverage message
- no misleading spinner for valid empty results
- explicit offline usability

## 4. Accounting-history observability

For Ledger/Voucher history, distinguish:

- locally complete for requested period
- partially available
- not yet synchronized
- reconciliation running
- reconciliation failed
- authoritative vs non-authoritative scope where relevant

Never claim “full history” when BOOKSFROM or another authoritative boundary is unavailable.

## 5. Error-message rules

Messages should:

- state user-visible effect;
- avoid exposing internal secrets;
- distinguish retryable vs non-retryable;
- avoid blaming network when root cause is unknown;
- avoid generic “Something went wrong” when actionable state is known;
- avoid suggesting destructive fixes such as clear data/re-pair unless genuinely required.

## 6. Diagnostics

Developer/support diagnostics may include:

- sanitized lifecycle events
- version/provenance
- health-state transitions
- bounded request metadata
- circuit state
- storage state
- sync counts/timestamps
- non-sensitive error classification

Do not include:
- raw credentials
- private keys
- raw Tally XML unless explicitly controlled for a diagnostic purpose
- full sensitive business payloads by default

## 7. Observability acceptance

For every major failure mode ask:

> Could a normal user understand whether the app is usable and what action is required?

If not, either improve the user-facing state or record the observability gap explicitly.

**NO HIDDEN HEALTH STATE WHERE THE USER MUST GUESS.**
