# ADR-002: ErpReadPort as the Business-to-ERP Boundary

**Status:** Accepted  
**Date:** 2026-07-22  
**Milestone:** 3A — Secure ERP Foundation

## Context

Business services previously depended on concrete Tally types (`TallyReadGateway`, `ApprovedOperationId`, `TallyXmlResponseParser`) and received raw XML for re-parsing. This violated ports-before-adapters, leaked implementation details, and made every future ERP require changes across business code.

## Decision

Introduce **`ErpReadPort`** (`src/erp/ports/erp-read-port.ts`) as the sole interface through which application/business code reads data from a connected ERP.

Business services (`CompanyDiscoveryServiceImpl`, `MasterDataServiceImpl`) depend only on `ErpReadPort`. The Tally adapter provides **`TallyReadAdapter`**, which implements the port by:

1. Calling the internal `TallyReadGateway.executeApprovedRead()` (typed operation IDs only).
2. Parsing XML internally.
3. Returning strongly typed Venture domain models (`NormalizedLedger`, `ErpCompanySummary`, etc.).

## Why business depends on ErpReadPort

- Business code must not know which ERP is connected (Tally, BUSY, SAP, etc.).
- Business code must never receive raw XML, `ParsedXmlNode`, or transport DTOs.
- The port defines **what** Venture needs from an ERP, not **how** the ERP exposes it.

## Why adapters implement ports

- All ERP-specific knowledge (XML templates, collection IDs, parsers) stays inside `src/tally/`.
- A future `BusyReadAdapter` or `SapReadAdapter` implements the same `ErpReadPort` without touching `services/`.
- Dependency injection registers `ServiceTokens.ErpReadPort`, not `TallyModule` or `TallyReadGateway`.

## Future ERP extensibility

Adding a new ERP requires:

1. A new adapter package implementing `ErpReadPort`.
2. An ERP-specific internal gateway with its own approved-operation registry.
3. A composition-root registration switch (config-driven ERP selection).

Business services, API routes, and extraction orchestration remain unchanged.

## Tradeoffs

| Benefit | Cost |
|---------|------|
| ERP independence for business layer | Extra mapping layer (port → adapter → gateway) |
| Domain models at the boundary | Adapter must own all parse/map logic |
| Testable with port mocks | Port interface must evolve deliberately (versioning) |

## Consequences

- `TallyReadGateway` is **adapter-internal**, not application-facing.
- `ApprovedOperationId` is never imported by `services/` or `api/`.
- Architecture tests fail the build if business read services import `tally/gateway` or reference `rawXml`.

## References

- `src/erp/ports/erp-read-port.ts`
- `src/tally/adapter/tally-read-adapter.ts`
- ADR-001, ADR-006
