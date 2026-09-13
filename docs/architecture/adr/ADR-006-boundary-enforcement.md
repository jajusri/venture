# ADR-006: Architectural Boundary Enforcement

**Status:** Accepted  
**Date:** 2026-07-22  
**Milestone:** 3A — Secure ERP Foundation

## Context

TypeScript `private`, comments, and documentation are not security boundaries. Prior architecture claimed an "unbypassable gateway" while `TallyConnectionService.exchange(rawXml)` and `ServiceTokens.TallyModule` remained reachable through dependency injection. Regex-only import scans missed DI-based bypasses.

## Decision

Enforce architectural boundaries through **automated tests**, **DI design**, and **explicit module visibility** — not developer discipline alone.

## Architecture tests

`test/architecture/module-boundaries.test.ts` (12 tests) fails the build when:

| Rule | Scope |
|------|-------|
| No raw HTTP transport imports | Business read services (`services/extraction/`, `api/`) |
| No connection manager imports | Business read services |
| No Tally gateway/registry/parser imports | Business read services |
| No `rawXml` or `ParsedXmlNode` references | Business read services |
| Offline ingestion cannot import live Tally comms | `ingestion/` |
| `TallyXmlRequestBuilder` class usage only inside `tally/` | All source |
| ERP-neutral policy has no Tally module imports | `erp/policy/` |
| Business services use `ErpReadPort`, not gateway | `services/extraction/`, `services/tally/` |
| DI registers `ErpReadPort`, not `TallyModule` | Composition root |
| `TallyConnectionService` has no `exchange()` | Implementation prototype |

Architecture tests are **first-class tests** — same CI bar as unit and integration tests.

## Dependency rules

```
services/extraction/  →  erp/ports/          ✅
services/extraction/  →  tally/gateway/      ❌ (architecture test fails)
services/tally/       →  erp/ports/          ✅
services/tally/       →  tally/connection/   ✅ (lifecycle/diagnostics only)
ingestion/            →  tally/xml/parser    ✅ (offline parse only)
ingestion/            →  tally/gateway/      ❌
tally/adapter/        →  tally/gateway/      ✅ (internal adapter)
erp/policy/           →  tally/*             ❌
```

Connector parsing infrastructure (`extraction/`) may use Tally XML types when invoked only from the adapter — it is not application business logic.

## DI boundaries

The composition root (`bootstrap/register-services.ts`) is the only wiring point:

| Token | Resolves to | Consumers |
|-------|-------------|-----------|
| `ErpReadPort` | `TallyReadAdapter` | CompanyDiscovery, MasterData |
| `TallyConnection` | Lifecycle service (ping, diagnostics) | HealthService, startup order |
| *(removed)* `TallyModule` | — | Was a bypass surface |

Raw transport, connection manager, and read gateway are **not** registered as application-facing tokens.

## XML isolation

XML lifecycle is owned entirely by the Tally adapter:

1. **Build:** `TallyXmlRequestBuilder` + immutable registry `render()` contracts.
2. **Send:** Internal gateway → guarded connection manager → transport.
3. **Parse:** `TallyXmlResponseParser` + entity mappers inside `TallyReadAdapter`.
4. **Return:** Domain models across `ErpReadPort` — never raw XML strings.

## ERP neutrality

The security core (`src/erp/policy/`) contains zero Tally imports. Tally-specific operation metadata is mapped to neutral `PolicyOperation` at the adapter/guard boundary via `toPolicyOperation()`.

This allows future ERP adapters to reuse the same policy engine with their own registries and mappers.

## Consequences

- Build fails on boundary violations — violations are visible immediately, not in production.
- Tests require maintenance when intentionally adding new boundaries.
- In-process guarantee only: external processes can still open sockets to Tally; Venture's supported code paths cannot.

## References

- `test/architecture/module-boundaries.test.ts`
- `src/bootstrap/register-services.ts`
- `src/core/tokens.ts`
- ADR-001, ADR-002, ADR-004
