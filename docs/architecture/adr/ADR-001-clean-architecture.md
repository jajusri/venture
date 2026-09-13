# ADR-001: Clean Architecture for the Venture Connector

**Status:** Accepted  
**Date:** 2026-07-22  
**Milestone:** 3A — Secure ERP Foundation

## Context

The Venture Tally Connector must communicate safely with a fragile external ERP (TallyPrime) while remaining maintainable for years, supporting multiple future ERP adapters, and preventing accidental data corruption or Tally crashes. Ad-hoc layering and direct coupling to Tally-specific types had produced bypass paths, raw XML leakage into business code, and architecture that could not honestly claim a single communication gateway.

## Decision

Adopt **Clean Architecture** (ports and adapters) as the permanent structural model for the connector:

```
Business Rules / Application Services
        ↓
Ports (interfaces — ErpReadPort, policy contracts)
        ↓
Adapters (TallyReadAdapter, future BUSY/SAP adapters)
        ↓
Infrastructure (HTTP transport, XML builder, connection manager)
```

## Dependency direction

Dependencies always point **inward**:

- Business services depend on **interfaces** (ports), never on concrete ERP implementations.
- Adapters implement ports and translate external formats into Venture domain models.
- Infrastructure (transport, sockets, XML parsing) never defines business rules.
- The **composition root** (`bootstrap/register-services.ts`) is the only place that wires concrete implementations.

## Layer responsibilities

| Layer | Owner | Responsibility |
|-------|-------|----------------|
| Application services | `services/` | Orchestrate business use cases; consume ports only |
| Ports | `erp/ports/` | Define stable contracts crossing the ERP boundary |
| Adapters | `tally/adapter/` | Translate between ERP-specific protocols and domain models |
| Safety core | `erp/policy/` | ERP-neutral authorization and fail-closed decisions |
| Infrastructure | `tally/transport/`, `tally/connection/` | Network I/O, lifecycle, diagnostics |

## Benefits

- **Replaceability:** Tally can be swapped for another ERP by implementing `ErpReadPort` without changing business services.
- **Testability:** Business logic tests mock the port; adapter tests mock transport.
- **Safety:** External formats (XML, HTTP) are confined to the adapter boundary.
- **Longevity:** Architecture survives framework, language, and ERP changes because business rules do not depend on implementation details.

## Consequences

- **Positive:** Clear ownership, enforceable boundaries, honest documentation.
- **Negative:** More indirection (port → adapter → gateway → transport) than a monolithic connector.
- **Neutral:** Parsing/normalization code in `extraction/` remains connector infrastructure invoked by the adapter, not by business services directly.

## References

- `.cursor/rules/architecture-principles.md`
- `docs/architecture/architecture-defect-remediation-report.md`
