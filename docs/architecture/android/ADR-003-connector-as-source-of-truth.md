# ADR-003: Connector as Source of Truth

**Series:** Android companion (`apps/budcom_android`)  
**Status:** Accepted  
**Date:** 2026-07-27  
**Related:** [`PROJECT_CONSTITUTION.md`](../../PROJECT_CONSTITUTION.md), [`DECISIONS.md`](../../DECISIONS.md), Connector ADRs under [`../adr/`](../adr/)

## Context

The monorepo contains OpenAPI snapshots under `docs/openapi/` and shared contract packages. Those artefacts can lag the running Connector. Android DTOs invented from outdated specs create silent production failures: wrong fields, wrong status handling (for example readiness `503` with body), or routes that do not exist.

## Decision

For Android networking, the **Connector implementation** is the source of truth for:

- route paths and methods
- success and failure HTTP statuses
- response JSON field names and nullability
- behavioural semantics that affect UI (for example readiness not-ready payloads)

OpenAPI and shared contracts are helpful references and future sync targets. They do **not** override verified Connector behaviour.

## DTO generation rules

1. Add Retrofit interfaces and Kotlin Serialization DTOs only for confirmed routes.
2. Mirror Connector field names; do not invent convenience properties on wire DTOs.
3. Map DTOs to domain models in the data layer; domain may be narrower than the wire shape.
4. Treat unexpected fields conservatively (ignore unknown JSON where the serializer policy allows; fail closed when required business meaning is missing).
5. Never “fix” accounting values or identifiers in the client to make parsing succeed.

## Contract verification

Before implementing a feature that needs Connector data:

1. Inspect Connector route handlers / response builders.
2. Confirm status codes and sample payloads (including failure shapes).
3. Record the confirmed routes in the milestone design notes or PR description.
4. Prefer integration or serialization tests against fixtures taken from Connector behaviour.

If a required route does not exist, stop and report the gap. Do not invent an Android-only API.

## Future OpenAPI synchronization

When OpenAPI is refreshed from the Connector:

1. Diff Android DTOs against the updated contract.
2. Update Android models only where the Connector change is real.
3. Keep privacy and reliability constraints intact.
4. Until sync is automated, manual verification against Connector code remains mandatory.

## Consequences

- Android may temporarily disagree with stale OpenAPI documents; that disagreement is expected and must be resolved by updating docs/contracts, not by inventing client fields.
- Shared `budcom_contracts` packages should be treated as secondary until proven current for the routes in use.
