# ADR-002: Dynamic Base URL

**Series:** Android companion (`apps/budcom_android`)  
**Status:** Accepted  
**Date:** 2026-07-27  
**Related:** `core.network.ConnectorBaseUrlProvider`, `core.network.DynamicBaseUrlInterceptor`, `feature.serverconfig`

## Context

The Budcom Connector runs on a host and port chosen by the operator (emulator loopback, LAN IP, or localhost). Users must be able to change the Connector origin from the app. Rebuilding Retrofit/OkHttp for every URL change is brittle: it complicates DI, invalidates call state, and encourages ad-hoc client factories per feature.

## Decision

Use a **single Retrofit/OkHttp client** whose outbound origin is rewritten per request from an in-memory URL provider, persisted via DataStore.

### ConnectorBaseUrlProvider

- Holds the active normalized base URL (origin ending with `/`).
- Exposes snapshot + Flow observation for UI and repositories.
- Updated in memory when the user saves a new URL.
- Seeded from `BuildConfig.CONNECTOR_BASE_URL` at process start, then hydrated from persistence during application startup.

### DataStore persistence

- Server-config feature owns persistence of the saved URL.
- Persistence survives process death.
- In-memory provider is updated when a saved value is loaded or changed; the provider itself does not silently revert user values to the build default.

### DynamicBaseUrlInterceptor

- OkHttp interceptor rewrites each request’s scheme/host/port to the provider snapshot.
- Relative Retrofit paths and query parameters are preserved.
- Configured URLs are origin-only after validation/normalization.

### Why Retrofit is not rebuilt

Retrofit caches service proxies and converter configuration. Replacing the client on every settings save creates race conditions, duplicate clients, and DI complexity. Origin rewriting keeps one graph and one interceptor chain.

## Advantages

1. Host changes apply to the next request without DI rebuilds.
2. Features share one HTTP stack and timeout/retry policy.
3. URL validation can remain in the server-config domain while transport stays in `core.network`.
4. Tests can substitute the provider without constructing Retrofit.

## Limitations

1. Path rewriting assumes origin-only base URLs; non-root Connector bases are out of policy unless a future ADR changes validation.
2. In-flight requests started before an update keep their already-built URL.
3. Application startup must hydrate the provider from DataStore before relying on the saved host; incorrect hydration order can briefly use the build default.
4. Cleartext HTTP remains governed by network security config; dynamic URL does not grant unrestricted cleartext.
