# Architecture Overview

Venture follows clean architecture with strict module boundaries aligned to the MVP 1 specification (Section 18).

## Layered model

```mermaid
flowchart TB
  subgraph presentation [Presentation Layer]
    UI[Flutter Screens]
    Nav[Navigation and State]
  end

  subgraph application [Application Layer]
    UC[Use Cases]
    Cap[Capability Guard]
    EB[Domain Event Bus]
  end

  subgraph domain [Domain Layer]
    Ent[Entities and Value Objects]
    RepoI[Repository Interfaces]
  end

  subgraph data [Data Layer]
    Remote[Connector API Client]
    Local[Encrypted Local DB]
    Repo[Repository Implementations]
  end

  subgraph infra [Infrastructure]
    PDF[PDF Engine]
    Share[Android Share Sheet]
    Log[Structured Logging]
    Crypto[Keystore / Encryption]
  end

  subgraph connector [Connector Service]
    API[Read-only REST API]
    Adapter[Tally Adapter]
    Norm[Normalization Layer]
  end

  UI --> UC
  UC --> Cap
  UC --> RepoI
  UC --> EB
  RepoI --> Repo
  Repo --> Remote
  Repo --> Local
  Remote --> API
  API --> Norm
  Norm --> Adapter
  Adapter --> Tally[(Tally on LAN)]
  UC --> PDF
  UC --> Share
  Repo --> Crypto
```

## Data flow (read path)

1. User action triggers a **use case** in the application layer.
2. Use case validates **capabilities** before proceeding.
3. Repository checks local cache freshness; fetches from connector if needed.
4. Connector **Tally adapter** reads Tally via export/XML/HTTP (implementation in Milestone 1).
5. **Normalization layer** maps Tally output to stable Venture entities.
6. Repository persists normalized data locally and returns domain models.
7. UI renders from domain models; **freshness timestamp** is always visible.
8. Use case publishes a **domain event** (e.g. `LedgerViewed`).

## Connection states

| State | Meaning |
|-------|---------|
| Live | Connected and current |
| Refreshing | Fetch in progress |
| Cached | Last sync available offline |
| Stale | Cache older than threshold |
| Error | Cannot connect or validate |
| Unpaired | Setup required |

## Security boundaries

- Connector exposes **read-only** REST endpoints on the local network
- Device **pairing** is the only permitted mutating HTTP route in MVP 1
- Secrets stored in platform keystore; never logged
- Export files use app-controlled cache URIs

## Schema versioning

- API responses include `schemaVersion` (currently `1.0.0`)
- Local database maintains independent `schema_version` with transactional migrations
- Connector compatibility checked on connect and upgrade
