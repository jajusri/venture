# Budcom Architecture

Authoritative architecture notes for the Budcom monorepo. Product intent remains in `docs/Business_OS_MVP1_Master_Product_Specification.docx`.

## Documents

| Document | Description |
|----------|-------------|
| [overview.md](./overview.md) | Layered architecture and data flow |
| [module-boundaries.md](./module-boundaries.md) | Feature modules and dependency rules |
| [milestones.md](./milestones.md) | Delivery milestones M0–M5 |

## Guiding constraints (MVP 1)

1. **Read-only Tally** — connector and app never write accounting data
2. **Offline-first** — cached data with explicit freshness labels
3. **Capability-based access** — service-layer permission checks
4. **Upgradeable** — versioned local schema from day one
5. **Event-driven readiness** — domain events for future automation

## Repository map

```
apps/budcom_mobile/          Flutter presentation + application layers
shared/packages/
  budcom_core/               Domain entities, value objects, events
  budcom_contracts/          Connector API DTOs
connector/budcom_connector/  Local Node.js connector service
docs/openapi/                Connector contract (OpenAPI 3.1)
backend/                     Reserved for optional future cloud
tests/                       Cross-cutting contract tests
```
