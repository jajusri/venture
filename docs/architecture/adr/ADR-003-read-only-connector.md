# ADR-003: Read-Only Production Connector

**Status:** Accepted  
**Date:** 2026-07-22  
**Milestone:** 3A — Secure ERP Foundation

## Context

Live validation proved that certain Tally XML requests can **deadlock TallyPrime's HTTP handler** even when well-formed. Write-capable or executable requests (`IMPORT`, `EXECUTE`, `FUNCTION`, `ALTER`, etc.) pose unacceptable risk to customer data and Tally stability. Budcom's primary responsibility is to **protect Tally**, not to mutate ERP state through an unattended connector.

## Decision

Production communication with Tally (and any future ERP through this connector) shall be **structurally read-only**:

- Only `EXPORT` requests with types `DATA`, `COLLECTION`, or `OBJECT` are permitted.
- Every operation must be registered, classified, and approved by the fail-closed policy engine.
- No public API may accept raw XML or caller-supplied write/execute verbs.
- `TallyConnectionService` exposes lifecycle and diagnostics (`ping`, `getDiagnostics`) only — **no `exchange()`**.

## Security rationale

1. **Prevention over recovery:** A wedged Tally requires manual restart; client timeouts do not undo the damage.
2. **Fail-closed by architecture:** Unknown operations are denied at compile-time (typed gateway) and runtime (registry + policy).
3. **No configuration escape hatch:** `SAFE_MODE=false` cannot weaken mandatory controls (single-flight, no auto-retry, circuit breaker always on).
4. **Permanent forbidden registry:** Proven dangerous payloads (e.g. `List of Units` collection) remain blocked with regression tests.

## Enforcement layers

| Layer | Mechanism |
|-------|-----------|
| Validator | `EXPORT`-only; forbidden-token scan on XML |
| Registry | Approved operations with classification and evidence |
| Forbidden registry | Permanently blocked operations |
| Policy engine | `UNKNOWN = DENY`; half-open admits health probe only |
| Gateway | Typed `executeApprovedRead(operationId)` — no raw XML parameter |
| Architecture tests | Build fails on bypass imports or raw-XML in business code |

## Future write strategy (if ever required)

Writes to an ERP are **explicitly out of scope** for Milestone 3A and are not planned for the production read connector.

If Budcom ever requires ERP writes, they must be:

1. A **separate adapter module** with its own capability model, registry, and policy — never mixed into the read connector.
2. Subject to **manual approval**, dual-control, or operator-initiated workflows — never unattended automatic writes.
3. Covered by **live evidence** per operation, per Tally version, per company profile — same bar as read operations.
4. Never exposed through a generic `sendXml()` or `exchange()` API.

Until those conditions are met, the connector remains read-only by design.

## Consequences

- Budcom cannot push vouchers, masters, or imports to Tally through this connector.
- Offline workflows (user exports XML from Tally, imports into Budcom locally) remain the supported path for bulk data without live write risk.

## References

- `.cursor/rules/tally-connector.md`
- ADR-004, ADR-005
- `docs/diagnostics/TALLY_HANG_ROOT_CAUSE_INVESTIGATION.md`
