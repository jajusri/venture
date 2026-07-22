# Budcom

**Budcom** is a modular, offline-first Business OS platform. MVP 1 is an Android-first, read-only Tally companion for search, viewing, PDF generation, and sharing.

This monorepo contains the mobile app, shared domain packages, local Tally connector, and architecture documentation.

## Repository layout

| Path | Purpose |
|------|---------|
| `apps/budcom_mobile/` | Flutter Android app (presentation + application layers) |
| `shared/packages/budcom_core/` | Domain entities, value objects, events, capabilities |
| `shared/packages/budcom_contracts/` | Connector API DTOs aligned with OpenAPI |
| `connector/budcom_connector/` | Local read-only connector service (runs near Tally) |
| `backend/` | Reserved for optional future cloud services |
| `docs/` | Product spec, architecture, OpenAPI, decision log |
| `tests/` | Cross-cutting contract and architecture tests |

## Milestone 0 (current)

Architecture foundation only — no business features. See [docs/architecture/milestones.md](docs/architecture/milestones.md).

## Prerequisites

- [Flutter SDK](https://docs.flutter.dev/get-started/install) (stable, Android toolchain)
- [Dart SDK](https://dart.dev/get-started/install) (bundled with Flutter)
- [Node.js](https://nodejs.org/) 20+ (connector service)
- [Melos](https://melos.invertase.dev/) (`dart pub global activate melos`)

## Quick start

```bash
# Dart / Flutter workspace
melos bootstrap
melos run analyze
melos run test

# Connector service
cd connector/budcom_connector
npm ci
npm test
npm run dev
```

## Architecture principles

- **Read-only Tally** — no write endpoints in MVP 1
- **Offline-first** — cached data with explicit freshness labels
- **Capability-based permissions** — not hard-coded screen access
- **Modular boundaries** — UI never calls HTTP or SQL directly
- **Upgradeable** — versioned schema migrations from day one
- **Event-driven readiness** — domain events for future automation

See [docs/architecture/overview.md](docs/architecture/overview.md) for the full blueprint.

## License

Proprietary — confidential business information.
