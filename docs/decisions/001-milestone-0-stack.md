# ADR 001: Milestone 0 Technology Stack

**Date:** 2026-07-22  
**Status:** Accepted

## Context

Milestone 0 requires an architecture foundation for an Android-first, read-only Tally companion with a local connector, shared domain models, and upgradeable modular structure.

## Decision

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Mobile app | Flutter (Dart) | Locked in product spec; Android-first |
| Monorepo tool | Melos | Standard for multi-package Flutter workspaces |
| Shared domain | `venture_core` Dart package | Testable without Flutter widgets |
| API contract | OpenAPI 3.1 + `venture_contracts` | Stable boundary between app and connector |
| Connector (M0) | Node.js 20 + TypeScript + Express | Fast skeleton; Windows-deployable; tests run without Flutter SDK |
| CI | GitHub Actions | Spec workflow requirement |
| Backend cloud | Reserved, not implemented | Optional per spec; not needed for MVP 1 |

## Consequences

- Connector language may migrate to .NET for Windows Service packaging in Milestone 1 if deployment requires it; OpenAPI contract remains stable.
- Flutter platform folders generated locally via `flutter create` until committed in Milestone 1.
- Tally adapter isolated behind normalization interfaces before any UI consumes data.

## Open items for Milestone 1

- Confirm Tally XML/HTTP interface for target installations
- Finalize pairing and token lifetime
- Choose connector deployment form (Windows Service vs desktop agent)
