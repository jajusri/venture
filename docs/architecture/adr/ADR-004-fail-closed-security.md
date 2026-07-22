# ADR-004: Fail-Closed Security Model

**Status:** Accepted  
**Date:** 2026-07-22  
**Milestone:** 3A — Secure ERP Foundation

## Context

A connector that guesses, assumes, or defaults to "allow" when uncertain will eventually send a request that crashes Tally or corrupts data. Prior architecture had fail-open paths: a validator that permitted `IMPORT`/`EXECUTE`, a `SAFE_MODE=false` off-switch, and unregistered collection IDs that reached transport.

## Decision

Adopt a **fail-closed** security model enforced by architecture, not conventions:

> **Unknown is denied. Unverified is rejected. Assumed is unsafe.**

## Fail-closed philosophy

| State | Decision |
|-------|----------|
| Unregistered operation | **DENY** |
| Forbidden operation | **DENY** |
| `EXPERIMENTAL_DISABLED` | **DENY** |
| `CONDITIONAL` without explicit code approval | **REQUIRE_MANUAL_APPROVAL** |
| Request exceeds payload cap | **DENY** |
| Circuit open | **QUARANTINE** (business blocked; health probe only on half-open) |
| Validator rejects XML structure | **DENY** (before transport) |

No environment variable, feature flag, debug mode, or configuration file may convert a policy **DENY** into **ALLOW** in production.

## Policy engine

The ERP-neutral policy engine (`src/erp/policy/policy-engine.ts`) is the single mandatory decision point. It accepts only neutral `PolicyOperation` descriptors — no Tally capability names or operation IDs.

Tally-specific registry entries are translated via `toPolicyOperation()` before policy evaluation. Future ERP adapters supply their own mappers without changing the policy core.

Policy outcomes: `ALLOW`, `DENY`, `QUARANTINE`, `REQUIRE_MANUAL_APPROVAL`.

## Registry

The **approved-operation registry** (`src/tally/registry/operation-registry.ts`) stores:

- Stable operation ID, capability, Tally request kind and ID
- Classification (`VERIFIED_SAFE`, `CONDITIONAL`, etc.)
- Risk, payload/response limits, timeout, evidence source
- Immutable `render()` function for XML contract

Only `VERIFIED_SAFE` operations execute automatically. `CONDITIONAL` operations require explicit code-level `autoApproveConditional` (never configuration).

## Forbidden operations

The **forbidden registry** (`src/tally/registry/forbidden-registry.ts`) permanently blocks operations with proven deadlock or write evidence:

- `List of Units` collection — live deadlock evidence (2026-07-22)
- Single `Stock Item` object export — live deadlock evidence

Forbidden operations are denied by policy before transport. Regression tests ensure they never execute again.

## Security boundaries

Every live request passes:

```
Validation → Policy → Registry lookup → Forbidden check
  → Circuit breaker → Single-flight → Rate limit
  → Audit intent (before transport) → Transport
```

Mandatory runtime limits (regardless of `SAFE_MODE`):

- `poolMaxConnections = 1`
- `retryMaxAttempts = 1`
- `circuitBreakerEnabled = true`
- `autoReconnect = false`

## Consequences

- Safer unattended operation at the cost of stricter operation onboarding (every new read requires registry entry, evidence, and tests).
- Policy core is reusable across ERP adapters; Tally-specific knowledge stays in Tally registry and guard integration.

## References

- `src/erp/policy/policy-engine.ts`
- `src/tally/registry/operation-registry.ts`
- `src/tally/registry/forbidden-registry.ts`
- `src/tally/safety/tally-request-guard.ts`
