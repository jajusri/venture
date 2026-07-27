# Budcom Architecture

Authoritative architecture notes for the Budcom monorepo.

Product vision for the Android companion: [`../PRODUCT_SOUL.md`](../PRODUCT_SOUL.md)  
Normative Android rules: [`../PROJECT_CONSTITUTION.md`](../PROJECT_CONSTITUTION.md)  
Decisions log: [`../DECISIONS.md`](../DECISIONS.md)

---

## Purpose of Architecture Decision Records (ADRs)

ADRs preserve the **reasoning** behind important architectural choices, not only the choice itself.

Use an ADR when a decision:

- constrains future implementations
- trades one set of costs for another
- is likely to be questioned later by humans or AI agents
- would be expensive to reverse

Each ADR should state context, decision, consequences, and status. Prefer appending superseding ADRs over silently rewriting history.

---

## Document index

| Document | Description |
|----------|-------------|
| [overview.md](./overview.md) | Platform layered architecture and data flow |
| [module-boundaries.md](./module-boundaries.md) | Feature modules and dependency rules |
| [milestones.md](./milestones.md) | Platform delivery milestones M0–M5 |
| [accepted-reliability-hardening.md](./accepted-reliability-hardening.md) | Accepted Connector reliability rules |

---

## Android companion ADRs

These ADRs govern `apps/budcom_android`. Numbering is **local to the Android companion series** and does not replace Connector ADRs.

| ADR | Title |
|-----|-------|
| [ADR-001](./android/ADR-001-vertical-slice-architecture.md) | Vertical Slice Architecture |
| [ADR-002](./android/ADR-002-dynamic-base-url.md) | Dynamic Base URL |
| [ADR-003](./android/ADR-003-connector-as-source-of-truth.md) | Connector as Source of Truth |
| [ADR-004](./android/ADR-004-offline-philosophy.md) | Offline Philosophy |
| [ADR-005](./android/ADR-005-product-vision.md) | Product Vision |

Related Android governance:

- [`../REPOSITORY_EVOLUTION.md`](../REPOSITORY_EVOLUTION.md)
- [`../DEVELOPMENT_WORKFLOW.md`](../DEVELOPMENT_WORKFLOW.md)
- [`../ARCHITECTURE_REVIEW.md`](../ARCHITECTURE_REVIEW.md)

---

## Connector ADRs

These ADRs govern `connector/budcom_connector` and related ERP boundary work.

| ADR | Title |
|-----|-------|
| [ADR-001](./adr/ADR-001-clean-architecture.md) | Clean Architecture for the Connector |
| [ADR-002](./adr/ADR-002-erp-read-port.md) | ERP Read Port |
| [ADR-003](./adr/ADR-003-read-only-connector.md) | Read-only Connector |
| [ADR-004](./adr/ADR-004-fail-closed-security.md) | Fail-closed Security |
| [ADR-005](./adr/ADR-005-offline-xml-ingestion.md) | Offline XML Ingestion |
| [ADR-006](./adr/ADR-006-boundary-enforcement.md) | Boundary Enforcement |

When citing an ADR, always qualify the series (**Android** vs **Connector**) because both series reuse ADR numbers.

---

## Guiding constraints (MVP 1)

1. **Read-only Tally** — connector and companion never write accounting data unless a future approved specification changes policy
2. **Offline-aware** — cached or last-known data with explicit limitations
3. **Capability-based access** — service-layer permission checks on the Connector
4. **Upgradeable** — versioned local schema when Room entities exist
5. **Connector as source of truth** — Android must not invent HTTP contracts

---

## Repository map

```text
apps/budcom_android/         Native Android companion (Kotlin, Compose)
apps/budcom_mobile/          Flutter presentation + application layers
shared/packages/
  budcom_core/               Domain entities, value objects, events
  budcom_contracts/          Connector API DTOs
connector/budcom_connector/  Local Node.js connector service
docs/                        Constitution, soul, roadmap, decisions, ADRs
docs/openapi/                Connector contract snapshots (may lag implementation)
backend/                     Reserved for optional future cloud
tests/                       Cross-cutting contract tests
```
