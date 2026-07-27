# ADR-001: Vertical Slice Architecture

**Series:** Android companion (`apps/budcom_android`)  
**Status:** Accepted  
**Date:** 2026-07-27  
**Related:** [`PROJECT_CONSTITUTION.md`](../../PROJECT_CONSTITUTION.md), [`DECISIONS.md`](../../DECISIONS.md)

## Context

The Android companion needs to grow feature by feature (server configuration, company selection, dashboard, master data, vouchers) while remaining understandable to humans and AI agents. A purely horizontal layout (`all` domain in one package, `all` data in another, `all` UI in another) optimizes for layer purity at the cost of feature cohesion: changing one user journey requires edits across distant packages and makes ownership unclear.

## Decision

Adopt **feature-first vertical slices** under `feature/<name>/`.

Each feature may own:

```text
feature/<name>/
  domain/          models, repository interfaces, use cases
  data/            remote/local sources, DTOs, repository implementations, DI
  presentation/    UiState, ViewModel, Screen, Route
```

Shared cross-cutting primitives remain in `core/` (network, common results, database install site, DI, utilities). Shared UI tokens remain in `ui/`. Navigation composition remains in `navigation/`.

Features that only aggregate existing repositories (for example dashboard) may omit a `data/` package and depend on other features’ **domain** contracts.

## Why features own data / domain / presentation

1. **Cohesion:** Everything required for one vertical milestone lives together.
2. **Isolation:** Company selection can evolve without rewriting server-config packages.
3. **Testability:** Feature tests can target one slice with clear fakes.
4. **AI safety:** Agents can implement “one slice” without inventing global refactors.
5. **Delivery:** Roadmap milestones map naturally onto feature folders.

## Benefits over horizontal-only layers

| Horizontal-only | Vertical slice |
| --- | --- |
| Easy to find “all repositories” | Easy to find “everything for company selection” |
| Features sprawl across trees | Feature boundaries are visible in the package tree |
| Shared packages become dumping grounds | Sharing must be intentional via `core/` or published domain ports |
| Milestone PRs touch many unrelated folders | Milestone PRs stay mostly inside one feature |

Clean Architecture and MVVM still apply **inside** each slice: presentation → domain ← data.

## Consequences

- Cross-feature UI aggregators (dashboard) will depend on other features’ domain APIs; that coupling must stay one-directional and preferably through stable repository/use-case ports.
- Top-level scaffold packages under `data/` and `domain/` are transitional; see [`REPOSITORY_EVOLUTION.md`](../../REPOSITORY_EVOLUTION.md).
- Gradle multi-module extraction is optional later; package slices come first.

## Expected future structure

Near term:

```text
core/
feature/
  serverconfig/
  company/
  dashboard/
  …
ui/
navigation/
```

Later evolution (when justified by shared types, not by aesthetics):

```text
core/
feature/
common/          # truly shared non-core types moved deliberately
```

Do not mass-move packages. Move types only when a new feature needs them and ownership is clear.
