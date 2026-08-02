# Budcom

**Budcom** is a modular, offline-first Business OS platform. MVP 1 is an Android-first, read-only Tally companion for search, viewing, PDF generation, and sharing.

This monorepo contains the mobile apps, shared domain packages, local Tally connector, and architecture documentation.

## Project Documentation

Authoritative references for AI-assisted and human development of the BUDCO Android companion:

| Document | Purpose |
|----------|---------|
| [docs/PRODUCT_SOUL.md](docs/PRODUCT_SOUL.md) | Product manifesto and innovation pillars |
| [docs/NON_NEGOTIABLES.md](docs/NON_NEGOTIABLES.md) | Permanent, technology-independent principles |
| [docs/VISION_2030.md](docs/VISION_2030.md) | Long-term product vision through 2030 |
| [docs/PROJECT_CONSTITUTION.md](docs/PROJECT_CONSTITUTION.md) | Normative engineering rules |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Android companion milestones |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Frozen technical decisions |
| [docs/architecture/README.md](docs/architecture/README.md) | ADR index (Android + Connector) |
| [docs/DEVELOPMENT_WORKFLOW.md](docs/DEVELOPMENT_WORKFLOW.md) | Mandatory feature workflow |
| [docs/ARCHITECTURE_REVIEW.md](docs/ARCHITECTURE_REVIEW.md) | Read-only architecture review |
| [docs/REPOSITORY_EVOLUTION.md](docs/REPOSITORY_EVOLUTION.md) | Package evolution guidance (no mass moves) |
## Repository layout

| Path | Purpose |
|------|---------|
| `apps/budcom_android/` | Native Android companion (Kotlin, Compose) |
| `apps/budcom_mobile/` | Flutter Android app (presentation + application layers) |
| `shared/packages/budcom_core/` | Domain entities, value objects, events, capabilities |
| `shared/packages/budcom_contracts/` | Connector API DTOs aligned with OpenAPI |
| `connector/budcom_connector/` | Local read-only connector service (runs near Tally) |
| `backend/` | Reserved for optional future cloud services |
| `docs/` | Product constitution, soul, roadmap, decisions, architecture, OpenAPI |
| `tests/` | Cross-cutting contract and architecture tests |

## Milestone status

See [docs/ROADMAP.md](docs/ROADMAP.md) for the Android companion track. Broader platform milestones live under `docs/milestones/` and `docs/stage-updates/`.

## Prerequisites

- [Flutter SDK](https://docs.flutter.dev/get-started/install) (stable, Android toolchain) for the Flutter app
- [Dart SDK](https://dart.dev/get-started/install) (bundled with Flutter)
- JDK 17+ and Android SDK for `apps/budcom_android`
- [Node.js](https://nodejs.org/) 20+ (connector service)
- [Melos](https://melos.invertase.dev/) (`dart pub global activate melos`) for the Dart workspace

## Quick start

The MVP Android artifact is the native Compose application in
`apps/budcom_android`. Open that directory as the Android Studio project when
deploying to a device. The Flutter application in `apps/budcom_mobile` is an
early parallel track and is not the MVP artifact.

```bash
# Dart / Flutter workspace
melos bootstrap
melos run analyze
melos run test

# Native Android companion
cd apps/budcom_android
./gradlew :app:assembleDebug

# Connector service
cd connector/budcom_connector
npm ci
npm test
npm run dev
```


## Architecture Decisions

Architecture Decision Records (ADRs) preserve the reasoning behind important choices.

- Index: [docs/architecture/README.md](docs/architecture/README.md)
- Android companion ADRs: [docs/architecture/android/](docs/architecture/android/)
- Connector ADRs: [docs/architecture/adr/](docs/architecture/adr/)

Read-only Android review snapshot: [docs/ARCHITECTURE_REVIEW.md](docs/ARCHITECTURE_REVIEW.md)

## Development Workflow

Mandatory process for every Android companion feature:

[docs/DEVELOPMENT_WORKFLOW.md](docs/DEVELOPMENT_WORKFLOW.md)

Also see repository evolution guidance (no mass moves): [docs/REPOSITORY_EVOLUTION.md](docs/REPOSITORY_EVOLUTION.md)

## Architecture principles

- **Read-only Tally** - no write endpoints in MVP 1
- **Offline-first** - cached data with explicit freshness labels
- **Capability-based permissions** - not hard-coded screen access
- **Modular boundaries** - UI never calls HTTP or SQL directly
- **Upgradeable** - versioned schema migrations from day one
- **Event-driven readiness** - domain events for future automation
- **Connector as source of truth** - do not invent Android API contracts

See [docs/PROJECT_CONSTITUTION.md](docs/PROJECT_CONSTITUTION.md) for the Android companion constitution and [docs/architecture/overview.md](docs/architecture/overview.md) for the wider platform blueprint.

## License

Proprietary - confidential business information.
