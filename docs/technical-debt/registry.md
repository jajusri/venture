# Technical Debt Registry

Engineering-tracked compromises, defects, and deferred work.  
**Do not remove entries** — update status and append resolution notes.

---

## TD-001 — Parent encoding normalization

| Field | Value |
|-------|-------|
| **ID** | TD-001 |
| **Description** | Tally returns group parent as `"&#4; Primary"` (XML character entity + "Primary"). Hierarchy validator recognizes only normalized `"Primary"` as virtual root. Built-in root groups flag `MISSING_PARENT`; extraction status is `INCOMPLETE`. |
| **Impact** | Medium — hierarchy not fully trusted; 15 false positives observed on ESTIMATION (28 groups) |
| **Priority** | P2 |
| **Estimated fix** | 2 hours |
| **Target milestone** | 5A |
| **Status** | Open |
| **Introduced** | Milestone 3C live validation (2026-07-22) |
| **Evidence** | `docs/testing/milestone-3c-groups-manual-validation.md`; bug ID M3C-001 in `docs/stage-updates/milestone-3c-stage-update.md` |
| **Likely fix location** | `connector/budcom_connector/src/tally/groups/hierarchy-validator.ts`, normalization layer |
| **Workaround** | Consumers must treat `INCOMPLETE` + `MISSING_PARENT` for `"&#4; Primary"` as known quirk until fixed |

---

## TD-002 — Desktop company selection UI

| Field | Value |
|-------|-------|
| **ID** | TD-002 |
| **Description** | Desktop shell displays session company but cannot select/change company via UI |
| **Impact** | Medium — operators must use API or future tooling |
| **Priority** | P2 |
| **Estimated fix** | 4 hours |
| **Target milestone** | 4B |
| **Status** | **Resolved** (2026-07-23) |
| **Introduced** | Milestone 4A (2026-07-22) |
| **Resolution** | Milestone 4B — `CompanyService`, company picker UI, IPC `desktop:select-company` |

---

## TD-003 — Connector process supervision

| Field | Value |
|-------|-------|
| **ID** | TD-003 |
| **Description** | Desktop app requires connector started manually; no spawn/health supervision |
| **Impact** | Medium — poor operator experience on startup |
| **Priority** | P2 |
| **Estimated fix** | 6 hours |
| **Target milestone** | 4C |
| **Status** | **Resolved** (2026-07-23) |
| **Introduced** | Milestone 4A (2026-07-22) |
| **Resolution** | Milestone 4C — `ConnectorLifecycleService`, auto-start, health polling, crash recovery, lifecycle UI, IPC handlers |
| **Evidence** | `docs/diagnostics/m4c-live-lifecycle-validation.json` (7/7 PASS); 48/48 desktop tests |

---

## M4B-001 — Stale pre-3D connector deployment (RESOLVED)

| Field | Value |
|-------|-------|
| **ID** | M4B-001 |
| **Description** | Connector on :8080 was pre-3D build; session APIs returned 404 |
| **Status** | **Resolved** (2026-07-23T00:16+05:30) |
| **Resolution** | Stopped stale process; rebuilt and restarted latest connector; 8/8 live scenarios PASS |

---

## TD-005 — JSON ledger repository

| Field | Value |
|-------|-------|
| **ID** | TD-005 |
| **Description** | Ledger repository uses per-company JSON files instead of SQLite/embedded DB configured in connector defaults |
| **Impact** | Medium — no transactional incremental checkpoints; large datasets load full file |
| **Priority** | P2 |
| **Estimated fix** | 1 day |
| **Target milestone** | 5A-P |
| **Status** | **Resolved** (2026-07-23) |
| **Introduced** | Milestone 5A (2026-07-23) |
| **Resolution** | Milestone 5A-P — `SqliteLedgerRepository` on Node `node:sqlite`, WAL, migrations, company-scoped storage at `{databasePath}/budcom-ledger.db` |

---

## TD-006 — Durable interrupted sync resume

| Field | Value |
|-------|-------|
| **ID** | TD-006 |
| **Description** | Sync progress and resume state are in-memory only; connector restart loses interrupted sync checkpoint |
| **Impact** | Medium — operator must re-run full sync after crash |
| **Priority** | P2 |
| **Estimated fix** | 4 hours |
| **Target milestone** | 5A-P |
| **Status** | **Resolved for controlled-pilot restart safety** (2026-07-24) |
| **Introduced** | Milestone 5A (2026-07-23) |
| **Resolution** | Reliability Step 2 — abandoned runs marked `interrupted`; retry creates a new linked run that re-extracts from the beginning and relies on idempotent upserts plus atomic batch checkpoints (`predecessor_sync_run_id`, `retry_count` lineage) |
| **Accepted limitation** | Exact positional/source-cursor and AlterID/GUID watermark resume are intentionally unsupported unless a future proven Tally ordering, immutable cursor, or snapshot contract makes it safe. Tally export order is not guaranteed today; no snapshot identity exists. Ledger stable IDs are `guid:` / `name:` prefixed (Reliability ledger-identity fix, 2026-07-24). `lastProcessedId` is audit-only. Deletion reconciliation remains disabled. |

---

## TD-011 — Ledger name-slug identity and shallow export contract

| Field | Value |
|-------|-------|
| **ID** | TD-011 |
| **Description** | Ledger sync used standard `List of Ledgers` (shallow NAME-only export) and name-slug upsert keys. Rename left stale rows; GUID/AlterID/MasterID were not part of identity; no shallow-export detection. |
| **Impact** | High — incorrect ledger identity, rename duplication risk, insufficient extraction for balances/hierarchy |
| **Priority** | P1 |
| **Target milestone** | Reliability — ledger extraction contract remediation (controlled pilot) |
| **Status** | **Resolved (controlled pilot — 2026-07-24, uncommitted)** |
| **Evidence** | `docs/diagnostics/ledger-extraction-identity-evidence-tallyprime-3.0.1.md` |
| **Resolution** | Embedded read-only TDL FETCH (8 approved fields); `GUID → name slug` identity; extraction quality gate; schema v5 metadata; controlled backup + atomic cache rebuild for legacy slug rows |
| **Remaining limitation** | Evidence from one TallyPrime 3.0.1 company only; MasterID not in identity chain; name fallback rename risk when GUID absent; deletion reconciliation still disabled; `/ledgers/{id}` breaking change for slug consumers |

---

## TD-007 — Extraction-phase cancellation

| Field | Value |
|-------|-------|
| **ID** | TD-007 |
| **Description** | Ledger sync cancellation did not propagate through Tally extraction |
| **Impact** | Low — cancellation now works cooperatively at transport boundaries |
| **Priority** | P3 |
| **Estimated fix** | 2 hours |
| **Target milestone** | 5A-P |
| **Status** | **Resolved with accepted limitation** (2026-07-23) |
| **Introduced** | Milestone 5A (2026-07-23) |
| **Resolution** | Milestone 5A-P — `AbortSignal` chain, `POST /sync/ledgers/cancel`, HTTP abort |
| **Accepted limitation** | Tally XML response body cannot be interrupted mid-read |

---

## TD-008 — Connector network binding without authentication

| Field | Value |
|-------|-------|
| **ID** | TD-008 |
| **Description** | Connector previously defaulted to `0.0.0.0:8080` with no authentication |
| **Impact** | Medium — LAN clients could invoke sync/storage APIs if network-exposed |
| **Priority** | P2 |
| **Target milestone** | 5A-P sign-off |
| **Status** | **Resolved** (2026-07-23) — default bind is `127.0.0.1`; `0.0.0.0` rejected |
| **Introduced** | Pre-5A-P architecture |
| **Resolution** | Loopback-only default, host validation, diagnostics/readiness warnings for LAN mode |
| **Remaining limitation** | Non-loopback LAN mode has no authentication — see TD-009 |

---

## TD-009 — Authenticated LAN access for connector API

| Field | Value |
|-------|-------|
| **ID** | TD-009 |
| **Description** | Explicit LAN bind (`BUDCOM_CONNECTOR_HOST` non-loopback) is operator-acknowledged only; no authN/authZ |
| **Impact** | Medium — network-exposed connector remains trust-on-LAN |
| **Priority** | P2 |
| **Target milestone** | 5B or security hardening |
| **Status** | Open (future; disabled by default) |
| **Introduced** | Milestone 5A-P sign-off (2026-07-23) |
| **Mitigation today** | Default `127.0.0.1`, reject `0.0.0.0`, readiness check requires `BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED=true` |

---

## TD-004 — Desktop Tally settings not forwarded to connector spawn

| Field | Value |
|-------|-------|
| **ID** | TD-004 |
| **Description** | Desktop settings store Tally host/port but do not yet pass them to connector process environment on spawn |
| **Impact** | Low — Tally connection still configured in connector/Tally directly |
| **Priority** | P3 |
| **Estimated fix** | 2 hours |
| **Target milestone** | 5A |
| **Status** | Open |
| **Introduced** | Milestone 4D (2026-07-23) |

---

## TD-010 — Diagnostic export privacy allowlist

| Field | Value |
|-------|-------|
| **ID** | TD-010 |
| **Description** | Desktop diagnostic `sessionSummary` and ad hoc bundle construction could expose company names, ledger names, paths, tokens, and raw error text in exported bundles |
| **Impact** | Medium — support bundles could contain identifiable accounting/customer data |
| **Priority** | P2 |
| **Target milestone** | Reliability Step 3 (5B reliability order item 4) |
| **Status** | **Resolved (export surfaces — controlled pilot)** (2026-07-24) |
| **Introduced** | Milestone 4D diagnostics export |
| **Resolution** | Explicit `SafeDiagnosticBundleV1` allowlist, session display without company name, serialized absence tests, connector diagnostic API sanitizers |
| **Remaining limitation** | On-disk operational logs and normal dashboard/session UI may still contain company names; no unified connector diagnostic bundle exporter |

---

## TD-012 — Manual private-IP entry required for Trusted-LAN mobile pairing

| Field | Value |
|-------|-------|
| **ID** | TD-012 |
| **Description** | Trusted-LAN bind mode requires the operator to manually type a private IPv4 address into the raw `connectorHost` Settings field before secure mobile pairing can work at all. If left at the local-only default (`127.0.0.1`) while Trusted-LAN mode is selected, the Connector's pairing session — and therefore the QR/short-code payload — embeds a loopback host, which Android's `SecurePairingQrPayloadParser` correctly and deliberately rejects (`allowLoopbackHost = false`), surfacing only as a generic "Invalid QR code" on the phone with no actionable explanation on Desktop. Separately, the Settings panel's synchronous form validation (`connectorHost` must be a private IPv4 literal in Trusted-LAN mode) and the footer's connection status (driven by the already-running Connector's live lifecycle/health state) read from two disconnected sources of truth, so the footer can show "Connected" while Settings simultaneously flags the pending `connectorHost` value as invalid — a visibly contradictory state, with neither surface actually wrong about what it displays. |
| **Impact** | Critical for release — a normal user cannot be expected to discover their own machine's private LAN IPv4 address, type it into a raw text field, understand bind-mode/host coupling, or interpret an "Invalid QR code" phone-side error with no corresponding Desktop-side diagnosis. This blocks the core secure-pairing onboarding flow for anyone outside engineering. |
| **Priority** | P0 |
| **Target milestone** | Pre-MVP-1 release hardening (Phase 3U-1 follow-up) |
| **Status** | **Fixed (automated validation) — pending physical retest** (2026-08-07) |
| **Introduced** | Secure pairing / Trusted-LAN feature work (`feat(desktop): add secure pairing controls`, c5ebc96, 2026-08-05) |
| **Evidence** | Phase 3U-1 physical validation session, 2026-08-07 — iQOO scan of a Desktop-generated QR rejected with "Invalid QR code — This QR code is not a valid Budcom pairing code" while Desktop showed Connected/ok/Ready. Root-caused end-to-end: `desktop-config.json` had `connectorBindMode: "local-only"` → `connector-lifecycle-config.ts:70` forces `connectorHost` to `127.0.0.1` → `connector/budcom_connector/src/api/routes/pairing.ts:253` bakes that host into the pairing session → QR payload carries `host: "127.0.0.1"` → `SecurePairingQrPayloadParser.kt:205-206` rejects loopback by design. Schema version, required-field set, JSON encoding, transport-fingerprint format, and expiry handling were confirmed to match between Desktop's `buildPairingQrPayload` and Android's parser — ruled out as a contract mismatch. |
| **Likely fix location** | `apps/budcom_desktop/src/renderer/scripts/app.ts` (Settings form, `input-connector-host`); `apps/budcom_desktop/src/application/connector-lifecycle-config.ts` (host resolution for Trusted-LAN); `apps/budcom_desktop/src/application/mobile-access-status-service.ts` / `network/active-network-resolver.ts` (auto-detected LAN IPv4 already computed here as `reachableEndpoint` / `activeNetwork.ipv4` for the Mobile Access panel, but not wired as the source of truth for pairing-session host resolution) |
| **Recommended direction (not implemented — record only)** | Auto-resolve the active private LAN IPv4 via the existing `ActiveNetworkResolver`/`TrustedLanRebindCoordinator` path (already used for `MobileAccessStatusService.reachableEndpoint`) and use it to drive `connectorHost` automatically whenever Trusted-LAN mode is selected — manual entry must not be the product's primary flow. Primary UI should show only user-facing states such as "Mobile access: Ready" / "Retry" / "Not available on this network", never raw host/port. Keep `connectorHost`, bind address, and port fields under an Advanced Diagnostics section for engineering/support use only. Unify the footer connection indicator and the Settings-panel validation to read from one shared status model so they cannot disagree. |
| **Resolution (Phase 3U-2, 2026-08-07)** | New `resolveMobileEndpointHost` (`apps/budcom_desktop/src/application/network/active-network-resolver.ts`) is now the one authoritative "is this Connector reachable by another device on the LAN, and at what address?" answer, consumed by both `MobileAccessStatusService` and the new network-readiness gate in `MobilePairingService.getSecurePairingCapability()` — a pairing session is never even attempted (Connector is never called) unless an eligible, non-loopback private-LAN endpoint is currently resolved; Local-only mode and an unresolved/ineligible Trusted-LAN network both now report the existing `'unavailable'` capability state with a plain-language reason, reusing the pairing panel's existing generic rendering (which never showed raw host/IP data to begin with). Settings' `input-connector-host` field (already inside the pre-existing collapsed "Advanced Connector Settings" section) is now read-only and auto-fills from the same live detection when Trusted-LAN mode is selected, so a normal user is never required to look up or type a private IPv4 — the underlying `validateDesktopConfig`/`resolveConnectorLifecycleConfig` host-resolution and self-healing `applyRouteBackedHost` rebind machinery were deliberately left untouched (already correct once given a real value). A Retry action was added to the pairing panel's unavailable state. Android's `SecurePairingQrPayloadRejection.InvalidHost` now produces an actionable, non-sensitive message ("BUDCOM Desktop is not ready for mobile pairing. Generate a new QR code on the computer.") instead of the fully generic invalid-QR text, as a narrow defense-in-depth improvement — never exposing the rejected host/IP. A Connector-side (`pairing.ts`) defense-in-depth host guard was evaluated and deliberately NOT added: the route is already gated to Desktop-only callers via the control token, Desktop is now the fixed and only real source of network-topology truth, and adding a second cruder classifier there would both reintroduce a competing source of truth and break ~15+ existing tests that rely on the well-established loopback default test fixture for unrelated assertions. Desktop 502→567 tests (+65), Connector unchanged at 1277 (no connector-side change), Android 723→724 tests (+1); all lint/type-check/build tasks clean. Physical iQOO retest is still outstanding — this closes the automated-validation portion only. |

---

## TD-013 — Post-pairing reconnection loses selected company / authenticated transport blocks auto-recovery

| Field | Value |
|-------|-------|
| **ID** | TD-013 |
| **Description** | An ordinary Desktop/Connector process restart after a device has already completed secure pairing leaves the paired device unable to resume normal operation without manual intervention. Two independent, compounding defects: (1) Connector-side — `ConnectorSessionServiceImpl` (`connector/budcom_connector/src/services/session/connector-session.service.ts`) holds `selectedCompany` only in process memory (`session-validator.ts`'s `createEmptySession()`), and its `stop()` explicitly calls `withClearedSelection()` on top of that, so any Desktop/Connector restart always starts with no company selected — the trusted pairing credential and Connector identity are unaffected and remain valid, only the company selection is lost. (2) Android-side — after such a restart, `POST /session/validate` correctly returns HTTP 400 with a structured body `{"status":"NO_COMPANY_SELECTED", "session": {...}, "reason": "..."}` (`connector/budcom_connector/src/api/routes/session.ts` + `session-validator.ts`), but the authenticated transport's `OkHttpAuthenticatedConnectorApiClient.mapResponse()` (`apps/budcom_android/app/src/main/java/com/budcom/android/core/connectorauth/data/remote/AuthenticatedConnectorApiClient.kt:148`) maps every HTTP 400 to a generic `ValidationFailure`, and its `sanitizedErrorCode()` (line 162) only ever reads a `code` field — a field this response body does not even have (it uses `status`) — so the Connector's structured status is discarded before `CompanyRepositoryImpl.validateSession()`'s existing one-time company-reselection recovery (`apps/budcom_android/.../feature/company/data/repository/CompanyRepositoryImpl.kt:170-191`) ever sees it. That recovery already works correctly today on the legacy (pre-authenticated) transport, because `CompanyRemoteDataSource.validateSession()` decodes the same 400 body's `status` field into `AppResult.Success(SessionValidationOutcome(status="NO_COMPANY_SELECTED", ...))`, which reaches the recovery branch intact. |
| **Observed customer message** | "The Connector rejected the request." (`AuthenticatedRepositoryFailurePolicy.kt:71`) — a generic, non-actionable failure on an otherwise-healthy, already-paired device. |
| **Impact** | Critical for release — any ordinary Desktop/Connector restart (update, reboot, crash recovery) after a customer has successfully completed secure pairing silently strands that customer on the authenticated transport with no path back to normal operation except re-pairing, a new QR, manual IP entry, or ADB — none of which a normal user can or should have to do, and none of which are actually necessary since the trusted credential and Connector identity never became invalid. |
| **Priority** | P0 |
| **Target milestone** | Pre-MVP-1 release hardening (Phase 3U-5) |
| **Status** | Implemented / automated validation passed — physical reconnection retest pending (2026-08-08) |
| **Introduced** | Session/company-selection design predates secure pairing; became externally visible once the authenticated transport (Phase 3R/3S) became the primary path for paired devices without inheriting the legacy transport's structured-error recovery. |
| **Evidence** | Phase 3U-4 diagnostic session, 2026-08-08 — `D:\Projects\budcom_archives\BUDCOM_PHASE3U4_POST_PAIRING_RECONNECTION_DIAGNOSTIC_20260808_111238.txt` and `BUDCOM_PHASE3U4_RECONNECTION_HYPOTHESIS_MATRIX_20260808_111238.csv`. Explicitly disproven as root causes: credential revocation, Connector identity regeneration, pairing-session invalidation, 401/`RE_PAIR_REQUIRED`, secure-LAN business-route enforcement — the trusted credential survived and remained unrevoked, and Connector identity remained stable throughout. |
| **Distinction from TD-012** | TD-012 is about the *initial* pairing bootstrap — a device that has never paired cannot discover a usable LAN endpoint to scan a QR against. TD-013 is entirely post-pairing — the device is already trusted and connecting successfully; the defect is that routine Connector restarts lose session continuity (company selection) and the authenticated transport cannot self-heal from that loss the way the legacy transport already could. |
| **Bounded fix (planned)** | Connector: persist the minimum authoritative selected-company identity (id, name, selection timestamp) through the existing SQLite `storage_meta` durable-state table already used for connector identity (`ConnectorIdentityRepository`) and other continuity state, restored on `start()` before serving session validation, and left untouched by a normal `stop()` (explicit deselection remains a separate operation that still clears it). No pairing credential, trust-store, Connector identity, or transport-fingerprint behavior changes. Android: preserve the Connector's `status` field through the authenticated transport's HTTP 400 mapping for the exact `NO_COMPANY_SELECTED` case only (a fixed allowlist match, never a raw body pass-through), surface it as a distinguishable, well-known `AppError.Remote` code alongside the existing `SECURE_PAIRING_REQUIRED`/`AUTHENTICATED_ACCESS_DENIED` sentinels, and let `CompanyRepositoryImpl.validateSession()`'s existing bounded one-time reselect-and-retry recovery fire on it exactly as it already does for the legacy transport. 401/403 semantics, other 400 sub-statuses, and transport/offline failures are unaffected. |
| **Required automated tests** | Connector: selection persists across service restart/recreation, `stop()` does not clear durable selection, explicit deselection does clear it, corrupt/missing persisted state fails safe with no company fabricated, restored selection lets `/session/validate` succeed, trust/credential state is unaffected. Android: authenticated 400 `NO_COMPANY_SELECTED` is preserved through transport mapping and triggers exactly one bounded reselect+retry; 401/403 and unrelated/malformed 400s do not trigger it; no re-pair/QR/credential path is touched; legacy and authenticated transports reach equivalent recovery outcomes. Integrated simulation of the full paired-restart sequence and of the defense-in-depth no-selection-at-startup sequence. |
| **Required physical retest** | Outstanding — a paired iQOO device must be reconnected after a real Desktop/Connector restart and confirmed to resume normal operation without re-pairing, a new QR, manual IP entry, or ADB. Not performed in Phase 3U-5 (automated-validation only). |
| **Implementation** | Landed on `main` at commit `31fcac7` "fix(session): preserve company across authenticated reconnect" (2026-08-08), matching the bounded-fix design above exactly: `SelectedCompanyRepository` (new, `connector/budcom_connector/src/services/session/selected-company-repository.ts`) persists `{id, name, selectedAt}` under its own `storage_meta` key, restored in `ConnectorSessionServiceImpl.start()` before session-validation traffic is served, left untouched by a normal `stop()`, cleared only by explicit `clearSelection()`; Android's `AuthenticatedConnectorApiClient.mapResponse()` now sets `isNoCompanySelected` via a strict allowlist match on the 400 body's `status` field (401/403 remain structurally separate `when` branches, never conflatable), and `CompanyRepositoryImpl.validateSession()` performs exactly one reselect-and-retry on it. |
| **Automated validation (2026-08-08)** | Full suites re-run against this exact implementation, not just the tests added alongside it: Connector `tsc --noEmit`/build/lint clean (2 pre-existing lint errors in unrelated, untouched diagnostic scripts noted, not a regression), full vitest suite 153 files / 1293 tests passing including `connector-session-restart.test.ts`'s deterministic stop→recreate→restore→validate simulation with identity/trust proven unchanged. Android: full JVM suite 734 tests / 0 failures, `lintDebug`/`assembleDebug`/`assembleDebugAndroidTest` all clean, including the bounded single-retry recovery simulation and explicit 401/403/malformed-body non-triggering tests in `CompanyRepositoryImplTest.kt`. Desktop (no source changed by this fix): `tsc --noEmit` on main/preload/renderer clean, full vitest suite 55 files / 567 tests passing, build clean. Security review of the touched files found no bearer/pairing-secret/private-key logging anywhere, and confirmed company-selection persistence shares no code path with `ConnectorIdentityRepository`/`TrustedDeviceRepository`. Evidence: `D:\Projects\budcom_archives\BUDCOM_TD013_RECONNECTION_FIX_20260808_162500.txt`. |

---

## TD-014 — Desktop dashboard/company-list has no re-poll after a transient post-startup failure

| Field | Value |
|-------|-------|
| **ID** | TD-014 |
| **Description** | Desktop's renderer refresh model is entirely event-driven, not polled, and the two refresh paths are asymmetric. `startDesktopShell()` (`apps/budcom_desktop/src/renderer/scripts/app.ts:1947-1964`) calls `refreshUi()` (Connection/Health/Version summary) and `loadCompanies()` (company list, "Unable to load companies from the connector" banner) exactly once each at window load. After that, `refreshUi()` re-runs only when the main process pushes `desktop:status-updated` (`main.ts:610-614`), which itself fires only from `ConnectorLifecycleService.setStatusListener()` (`main.ts:351-353`) on an actual lifecycle **state transition** (e.g. `disconnected→starting→connected`). `loadCompanies()` has no re-trigger wired to anything at all — not to lifecycle transitions, not to any timer. A full-repo grep of the renderer found exactly one `setInterval` in the entire file, and it is the pairing-QR countdown; there is no periodic poll of the main dashboard/company summary. |
| **Observed customer message** | Desktop shows "Connection: Not connected", "Health: unknown", "Connector version: —", "Company: No company selected", plus "Unable to load companies from the connector." / "Company discovery failed / No companies available" — a state that persists indefinitely with no user action, even though the Connector itself is fully healthy and `/health`, `/session`, `/companies` all respond correctly and quickly when probed directly. |
| **Impact** | High for release perception — a transient, self-resolving slowness in the ~30-90 second window immediately after Connector startup (a separate, not-yet-fully-root-caused timing issue — see the distinction note below) can get captured as a one-time bad snapshot by whichever `refreshUi()`/`loadCompanies()` call happens to run during that window. Because the Connector's lifecycle state then settles into a stable `connected` with no further transitions, no `desktop:status-updated` push ever fires again, and `loadCompanies()` was never wired to re-fire at all — so the stale failure state is permanent until the user manually clicks Refresh Companies or restarts Desktop, even though the underlying service recovered within seconds on its own. A normal user has no way to know the failure is stale rather than real. |
| **Priority** | P1 |
| **Target milestone** | Pre-MVP-1 release hardening |
| **Status** | Implemented — automated recovery validation passed; physical confirmation pending |
| **Introduced** | Pre-dates this investigation; became visible during Phase 3U-6 physical TD-013 retest prep (2026-08-08), reproduced on a freshly rebuilt, freshly launched Desktop/Connector pair — not specific to any particular build. |
| **Evidence** | Live session, 2026-08-08 ~11:55-12:01 UTC: Desktop lifecycle log showed a clean `disconnected→starting→connected` transition at 11:55:32-11:55:39 with no further transition logged since; three `"Company discovery failed: The connector did not respond in time"` errors at 11:56:06-11:56:40; direct probes of the same running Connector at 12:00-12:01 (`/health`, `/session` in 12ms, `/companies` in 79ms returning the real company `ESTIMATION` successfully) confirmed the service was fully healthy — yet the Desktop window, left untouched, continued showing "Not connected / Health unknown / Connector version — / Unable to load companies" throughout. |
| **Distinction from the underlying transient slowness** | This entry is about the *missing recovery path* in the UI, not about *why* the ~30-90s post-startup window is occasionally slow enough to time out a client request in the first place — that remains the same not-fully-root-caused observability gap noted in the Connector→Tally diagnostic (packaged Connector `stdio:'ignore'` means no Connector-side logs exist to pinpoint the exact stall mechanism). Both are real; this entry is scoped to the Desktop-side fix (give the UI a way to notice the transient failure has cleared), which is independently valuable regardless of whether the underlying transient-slowness cause is ever further narrowed. |
| **Likely fix location** | `apps/budcom_desktop/src/renderer/scripts/app.ts` — either add a bounded periodic re-poll for the dashboard/company summary (matching the existing self-rescheduling-`setTimeout` pattern already used for sync-progress and pairing-status polling elsewhere in this same file), or wire `loadCompanies()` (and a failed `refreshUi()` result specifically) to retry on a short bounded backoff until it succeeds once, then stop — not an unconditional infinite poll. |
| **Required automated tests** | A renderer test proving: after `startDesktopShell()`'s initial `loadCompanies()`/`refreshUi()` calls fail, and with no further `desktop:status-updated` event, the UI still recovers to a correct state within a bounded time/number of attempts when the underlying bridge calls start succeeding; a test proving the retry is bounded (does not poll forever); a test proving a genuinely persistent failure still displays as an error, not a false "recovered" state. |
| **Required physical retest** | Not required to confirm the diagnosis (already confirmed live above). Outstanding — a real Desktop/Connector restart must confirm the UI now self-recovers physically within this same scenario, matching the automated result below. |
| **Implementation** | Landed on `main` at commit `60d57419828594a6f52b507bd9591823fd831565` ("fix(desktop): recover from transient connector failures", 2026-08-09), matching the likely-fix-location direction's second option: `reconcileBoundedRecovery()` runs after every dashboard/company refresh point (startup, a lifecycle status push, Start/Restart Connector, manual Refresh) and, while still unhealthy, schedules a bounded retry (2s/5s/10s/20s/30s, 5 attempts max, ~67s total) that re-fetches dashboard and company state together — never just one — so Connection and Company can never recover independently and drift apart. Cancels immediately on success, on window teardown, or when a fresher trigger (a real lifecycle transition or a manual refresh) supersedes it; never polls once healthy. `loadCompanies()` also gained a generation token so an older, slower call can never overwrite a newer call's already-rendered result (a race the Phase 3U-era physical debugging repeatedly demonstrated across other subsystems). |
| **Automated validation (2026-08-09)** | Desktop 570 → 596 tests (26 new, 0 failures), `npm run build`/`npm run lint` (which itself runs `tsc -p tsconfig.main.json`/`tsconfig.preload.json --noEmit` + the full vitest suite) clean, `tsc -p tsconfig.renderer.json --noEmit` explicitly re-run clean. The Group 5 acceptance scenario (Connector lifecycle stays Connected throughout; the very first dashboard/company request times out; the backend becomes healthy with **no further lifecycle transition at all**; recovery must happen from the bounded timer alone, never a manual Refresh) passes **10/10 consecutive runs** deterministically via Vitest fake timers (`test/renderer/dashboard-recovery.test.ts`). No Connector or Android production changes were required or made; both regression suites re-confirmed unaffected (Connector 1293/1293 tests + `tsc`/`eslint` clean; Android 783/783 JVM tests + `lintDebug`/`assembleDebug`/`assembleDebugAndroidTest` clean). Security review: no new secrets/logging, no QR/pairing/authentication/Connector-identity/endpoint-persistence changes, no manual-IP behavior; bounded retries only ever run while unhealthy and always stop (never permanent polling), and the retry timer is provably cleaned up on both success and window teardown. Evidence: `D:\Projects\budcom_archives\BUDCOM_TD014_BOUNDED_DESKTOP_AUTO_RECOVERY_20260809.txt`. |

---

## TD-015 — Desktop business clients retain stale Connector endpoint after trusted-LAN route resolution

| Field | Value |
|-------|-------|
| **ID** | TD-015 |
| **Description** | The packaged Connector child process is spawned correctly against the current, live route-resolved private-LAN address (`main.ts:250-252`'s `computeEffectiveLifecycleConfig()` applies `applyRouteBackedHost()`, `application/network/route-backed-lifecycle-override.ts:11-32`, which overrides a stale persisted host with `activeNetworkAdapter`'s live IPv4 — its own doc comment states the intent explicitly: "never a stale hand-typed IP"). But `DashboardService`, `CompanyService`, `LedgerService`, and `StockItemService` are constructed directly from `resolved.connectorBaseUrl` (`main.ts:235-238`, repeated at `397-400` and `511-514`) — the **raw, unresolved** value from `SettingsService.getResolvedConfig()` (`settings-service.ts:47-49`), itself built from `resolveDesktopConfig(loadResult.config, ...)` reading `desktop-config.json` straight off disk with no live network correction. The route-backed self-healing mechanism exists and works correctly for exactly one consumer (the Connector spawn) and was never wired to the other four. |
| **Observed customer message** | Desktop shows "Connection: Not connected", "Health: unknown", "Connector version: —", "Company: No company selected", "Unable to load companies from the connector", "Company discovery failed" — persistently, not just transiently — while the packaged Connector is demonstrably healthy and reachable: direct `/health`, `/session`, and `/companies` probes against the Connector's actual live-bound address all succeed quickly and `/companies` returns the real company. Manual "Refresh Companies" does not recover the UI, because it re-invokes the same `companyService`/`dashboardService` instances constructed with the same stale address — confirmed live during Phase 3U-6 physical retest prep, 2026-08-08. |
| **Impact** | Critical for release — any normal customer whose laptop has ever connected to a different Wi-Fi network before (a near-certainty for a laptop) will have a `desktop-config.json` with a `connectorHost` from a prior network. Every subsequent launch pays the same PENALTY: the Connector rebinds correctly (invisible to the user), but the entire Desktop UI — dashboard, company list, ledgers, stock — becomes functionally disconnected, indistinguishable from a genuine outage, with no in-product recovery path other than manually typing the current IPv4 into Advanced Settings (which is explicitly NOT an acceptable product solution — see TD-012). |
| **Priority** | P0 |
| **Target milestone** | Pre-MVP-1 release hardening |
| **Status** | Implemented — automated validation passed; physical confirmation pending. (This entry's Status field was inconsistently left at "Open" after the fix landed at `13a4cb2` — corrected here during the Phase 3U-9 Distributed Connection State Recovery Gate reconciliation, 2026-08-09; the code fix itself was never in question, only this field.) |
| **Introduced** | Pre-dates this investigation — present since `applyRouteBackedHost`'s route-backed self-healing was added for the Connector spawn path without equivalent coverage for the Desktop business-service clients. |
| **Evidence** | Phase 3U-6 physical TD-013 retest prep, 2026-08-08: `desktop-config.json`'s persisted `connectorHost` confirmed (by direct file read) to be on a different subnet than the Connector's actual live-bound address (confirmed via `connector_lifecycle_config_resolved`/`connector_spawn_attempt` log entries and direct `/health` probes); source-level trace confirmed `createDashboardService`/`createCompanyService`/`createLedgerService`/`createStockItemService` all consume the raw, uncorrected `resolved.connectorBaseUrl` while only `createLifecycleService` consumes the corrected `computeEffectiveLifecycleConfig()`. |
| **Distinction from related entries** | TD-012 is the *initial pairing bootstrap* problem (a never-paired device can't discover a usable LAN endpoint for its first QR scan) — already fixed by auto-resolving the *mobile pairing* endpoint via this same `ActiveNetworkResolver` machinery. TD-013 is Connector-side selected-company persistence across restart plus Android's authenticated-transport error recovery — unrelated to Desktop's own client endpoint selection. TD-014 is the *missing automatic re-poll/recovery-trigger* in the renderer — independent: even a fresh, explicit `loadCompanies()` call (via manual Refresh) fails here, because the request is addressed to the wrong host from the start, not because nothing re-checks a transient failure. This entry (TD-015) is specifically: the Desktop main process has two independent, divergent sources of truth for "where is the Connector," and only one of them self-heals. |
| **Not an acceptable workaround** | Manual entry of the current private IPv4 into Advanced Connector Settings is not the product solution — the whole point of the existing `applyRouteBackedHost`/`ActiveNetworkResolver` machinery (built for TD-012) is that a normal user never needs to know or type an IPv4 address. The fix must extend that same automatic mechanism to the Desktop business clients, not ask the user to work around it. |
| **Required fix direction** | One authoritative effective-runtime-endpoint source of truth in the Desktop main process, derived from the same active-network resolution already driving the Connector spawn, consumed by every Connector HTTP client (lifecycle, dashboard, company, ledger, stock item, and any other current or future Connector client) — not five independently-maintained copies of the same resolution logic. Must handle both a stale host already on disk at startup and a live network change while Desktop keeps running, without requiring a full Desktop restart if the lifecycle layer already recovers from such a change on its own. |
| **Resolution** | Landed at commit `13a4cb2` ("fix(desktop): share effective connector endpoint") — `computeEffectiveConnectorBaseUrl()`, a one-line wrapper over the existing `computeEffectiveLifecycleConfig()`/`applyRouteBackedHost()` machinery, is now the value all five Connector HTTP clients (lifecycle, Dashboard, Company, Ledger, StockItem) plus the manual health-check IPC handler are constructed from. Previously proven only at the resolution-function level (`route-backed-lifecycle-override.test.ts`); the Phase 3U-9 Distributed Connection State Recovery Gate (2026-08-09) added `test/integration/effective-endpoint-consistency.test.ts`, which constructs all four business-service classes directly with a shared intercepted `fetchImpl` and confirms every actual outbound HTTP request lands on the resolved effective endpoint, never the stale raw one — closing the "only proven piecewise" gap this entry's registry history noted. |

---

## TD-016 — Securely paired Android diagnostics report legacy emulator endpoint instead of authenticated transport state

| Field | Value |
|-------|-------|
| **ID** | TD-016 |
| **Description** | `apps/budcom_android/app/build.gradle.kts:25` bakes `BuildConfig.CONNECTOR_BASE_URL = "http://10.0.2.2:8080/"` (the documented Android-emulator host alias) as the seed for `DefaultConnectorBaseUrlProvider`'s in-memory `AtomicReference`. `DefaultConnectorBaseUrlHydrator` (`feature/serverconfig/data/startup/`) hydrates that in-memory value from `ConnectorBaseUrlLocalStore` on every process start — the **legacy/manual-URL** store. Secure QR pairing is a structurally separate architecture (`SecureCredentialVault` → trusted context → `AuthenticatedConnectorApiClient`, which builds requests directly from `resolution.context.endpoint`) and never writes into `ConnectorBaseUrlLocalStore`. `LoadDiagnosticsUseCase`/`DashboardUseCases` read "Configured URL"/health/readiness from `ConnectorStatusPort.currentBaseUrl()` (`ConnectorConfigRepositoryImpl.currentBaseUrl() = baseUrlProvider.snapshot()` — the legacy pipeline), while company/session comes from a separate, transport-aware `CompanyRepository`/`ConnectorTransportSelectionGate` path. One screen mixes state from two independent transport systems. |
| **Observed customer message** | A securely paired, already-trusted physical device can show "Configured URL: http://10.0.2.2:8080/", "Connector unavailable", "Readiness: Unknown", "The request timed out" — while the actual authenticated transport and trusted company selection may be entirely unaffected. `10.0.2.2` is merely the untouched legacy/emulator default; it does not prove the authenticated transport is targeting it, and source alone cannot prove it — this record does not claim otherwise. |
| **Impact** | Critical for release perception — an already-working, securely paired customer device can present as fully offline against a value that was never a real endpoint, inviting an unnecessary and unsafe manual-IP edit or re-pair attempt for a device that may not actually be broken. |
| **Priority** | P0 |
| **Target milestone** | Pre-MVP-1 release hardening |
| **Status** | **Fixed (automated validation) — pending physical retest** |
| **Introduced** | Pre-dates this investigation — present since secure pairing (Phase 3N/3R-era) was layered on top of the pre-existing legacy/emulator-default connection machinery without the diagnostics/status surface being made transport-aware. |
| **Evidence** | Read-only Android source trace, 2026-08-08 (physical iQOO screenshot review, no ADB, no phone-side inspection): confirmed via direct trace that `10.0.2.2` has exactly one source in the codebase (`build.gradle.kts`'s `BuildConfig` field), confirmed the legacy and authenticated pipelines are structurally disjoint (different stores, different HTTP clients), confirmed `Secure Mobile Pairing: off` (`mobile-pairing-service.ts:108-111`) gates only new-pairing-session creation and appears nowhere in credential authentication. |
| **Fix** | `ConnectorOperationalStatusPort`/`ConnectorOperationalStatusPortImpl` (`feature/serverconfig/domain/port/`, `feature/serverconfig/data/repository/`) added 2026-08-08, commit `a4c560e`: branches on the existing `ConnectorTransportSelectionGate` and, for `AUTHENTICATED` trust, probes reachability via the existing authenticated `DiagnosticsConnection` route, reporting the real trusted endpoint sourced from `AuthenticatedConnectorContextProvider` — never the legacy probe, never written into `ConnectorBaseUrlLocalStore`. `LoadDiagnosticsUseCase` and `RefreshDashboardUseCase` now consume this port instead of `ConnectorStatusPort` directly. `ProbeConnectorConnectionUseCase`/`ObserveDashboardContextUseCase` deliberately left unchanged (still LEGACY-only, out of this defect's scope). Validated: 750 Android JVM unit tests (up from a 734 baseline, 0 failures), `lintDebug`/`assembleDebug`/`assembleDebugAndroidTest` clean, Desktop (570 tests) and Connector (1293 tests) regression suites unaffected. Physical iQOO retest still required to close the "remaining open item" below. |
| **Explicitly NOT claimed / remaining open item** | "Session = Unknown" / "Last successful validation = Never" was also observed on the same physical device. That path (`CompanySessionPortImpl.validateSessionStatus()` → `CompanyRepository.validateSession()`) already routes through the authenticated transport when trust is `ACTIVE` — it is not proven to share this defect's cause. Whether it reflects a genuine, separate authenticated-transport problem (network-transition gap, TLS/certificate-identity mismatch, or something else) can only be determined by physical retest after this fix lands, not by source inspection alone. This record does not claim this defect explains that observation. |
| **Distinction from related entries** | TD-012 is first-run pairing *bootstrap* (a never-paired device discovering a usable endpoint for its first QR scan) — unrelated, this device is already trusted. TD-013 is Connector-side selected-company persistence across restart plus Android's authenticated-transport 400-mapping recovery — a different layer; this entry does not reopen or duplicate it. TD-015 is the Desktop-side mirror-image gap (a live, correct endpoint existed but one of five consumers didn't use it) — this is the Android-side case where the diagnostics *consumer* was simply never made aware a second, authoritative transport exists at all. |
| **Not an acceptable fix direction** | Do not write the authenticated/secure-pairing endpoint into `ConnectorBaseUrlLocalStore` merely to make the legacy-sourced screen display correctly — that would merge two deliberately separate transport models and risks stale-IP persistence, endpoint leakage, and future DHCP-transition bugs on the Android side. The fix must make the diagnostics/status *consumer* transport-aware (select `AUTHENTICATED` vs `LEGACY` per the existing `ConnectorTransportSelectionGate` architecture), not synchronize the stores. |

---

## TD-017 — Authenticated Android reconnect permanently pins pairing-time Connector endpoint

| Field | Value |
|-------|-------|
| **ID** | TD-017 |
| **Description** | `SecureCredentialVault.storePendingVerification()` (`apps/budcom_android/app/src/main/java/com/budcom/android/core/pairing/data/local/SecureCredentialVault.kt:143-149`) writes `host`/`securePort` exactly once, at initial QR-pairing time. Neither `markActive()` nor `markRePairRequired()` — the vault's only other mutators — ever touch those fields, and no other production code path updates them after pairing. `AuthenticatedConnectorContextProvider.resolve()` (`core/connectorauth/domain/AuthenticatedConnectorContextProvider.kt:59-60`) reads that persisted endpoint directly on every call, and `OkHttpAuthenticatedConnectorApiClient.buildRequest()` (`core/connectorauth/data/remote/AuthenticatedConnectorApiClient.kt:80-89`) constructs every authenticated request straight from `endpoint.host`/`endpoint.securePort` — no resolution or discovery step exists in that path at all. A structurally separate mechanism, `ConnectorConnectionResolver`/`NsdConnectorDiscoveryService` (`core/connection/`, `core/discovery/`), already performs bounded mDNS/NSD-based reconnection with fallback to last-known-endpoint, but it belongs to the older, non-cryptographic "legacy paired connector" system (`PairedConnectorLocalDataSource`/`ConnectorBaseUrlLocalStore`) and is never consulted for `AUTHENTICATED`-transport devices. `TrustedConnectorEndpoint`'s own doc comment (`core/pairing/domain/model/TrustedConnectorEndpoint.kt:6-9`) deliberately forbids sourcing the trust-anchoring fingerprint from mDNS/the network — a correct security instinct — but no verified-rediscovery alternative was ever built to compensate. Net effect: once the Connector's private-LAN address changes (ordinary DHCP renewal, Wi-Fi switch, router reboot), a securely paired device's trust, credential, and transport fingerprint all remain fully valid, yet the authenticated transport has no mechanism to find the Connector's new address and fails indefinitely. |
| **Observed customer message** | "Could not reach the Connector." (`AuthenticatedConnectorResult.TransportFailure` → `ConnectorOperationalStatusPortImpl.kt:99-101`), with "Configured URL" showing the non-address placeholder "Secure paired Connector", while the selected company and secure-pairing trust both remain intact. Not related to the legacy `10.0.2.2` default — confirmed absent from this path (TD-016). |
| **Impact** | Critical for release — a successfully paired customer can lose automatic Connector access after an entirely ordinary network event (DHCP address change, Wi-Fi reconnect, router restart) even though nothing about their trust relationship became invalid. Recovery today requires re-pairing, a new QR code, manual IP entry, or ADB — none of which a normal customer can or should have to do, and none of which are actually necessary. |
| **Priority** | P0 |
| **Target milestone** | Pre-MVP-1 release hardening |
| **Status** | Implemented — automated validation passed; physical confirmation pending (a Phase 3U-8 physical retest attempt was interrupted before its recovery observation completed — see `BUDCOM_TD017_PHYSICAL_RETEST_BLOCKED_INCOMPLETE_20260809_001500.txt` — genuinely unresolved, not failed). |
| **Introduced** | Pre-dates this investigation — present since secure pairing's authenticated transport (Phase 3N/3R-era) was built with a one-time endpoint snapshot and no re-resolution path was ever added. |
| **Evidence** | Phase 3U-8 preserved-iQOO physical retest, 2026-08-08: a real, previously-paired iQOO Z10 5G showed "Configured URL: Secure paired Connector" / "Connector unavailable" / "Could not reach the Connector" / "Session = Unknown" / "Last successful validation = Never" while Desktop/Connector on the same trusted LAN were simultaneously healthy (Connected, Health ok, Session ACTIVE, company ESTIMATION, ledgers/stock synced). Read-only source trace the same day confirmed 10.0.2.2 plays no role in this state (ruling out TD-016 as the cause) and confirmed the endpoint-persistence gap described above end-to-end, from `storePendingVerification()` through to `buildRequest()`. |
| **Distinction from related entries** | TD-012 is the *initial pairing bootstrap* problem (a never-paired device can't discover a usable endpoint for its first QR scan) — this device is already trusted and was previously connecting successfully. TD-013 is Connector-side selected-company persistence across restart plus Android's authenticated-transport 400-mapping recovery — a different failure mode (`NO_COMPANY_SELECTED`, not endpoint unreachability) at a different layer. TD-015 is the Desktop-side mirror-image gap (a live, correct endpoint existed but 4 of 5 HTTP consumers didn't use it) — already fixed; this entry is the Android-side case where *no* consumer has any live-endpoint-correction mechanism at all. TD-016 is Android's diagnostics/status *display* sourcing the wrong (legacy) transport for a securely paired device — already fixed and confirmed unrelated to this defect by source trace. |
| **Required fix direction** | Verified identity-based endpoint rediscovery: on authenticated transport failure, perform bounded LAN discovery filtered by the trusted `connectorId`, attempt a secure connection to any candidate found, and only after its transport fingerprint is cryptographically verified against the already-trusted fingerprint may its host/port be accepted and persisted. mDNS/NSD discovery must remain discovery-only, never a trust authority — a candidate must never be trusted merely because it advertises the expected `connectorId`, service name, host, or TXT record. |
| **Not an acceptable fix direction** | Trusting a discovered candidate on `connectorId`/TXT-record match alone, without fingerprint verification; reusing the legacy `ConnectorConnectionResolver`'s trust semantics unmodified for the authenticated path; weakening or bypassing TLS pinning to "simplify" reconnection; falling back to the unauthenticated legacy transport when authenticated rediscovery fails. |
| **Resolution** | Landed at commit `cb504e4` ("fix(android): rediscover trusted connector endpoint") exactly per the required fix direction above — see that commit and `BUDCOM_AUTHENTICATED_ENDPOINT_REDISCOVERY_FIX_20260808_232009.txt` for full detail. The Phase 3U-9 Distributed Connection State Recovery Gate (2026-08-09) closed the one remaining identified gap: `CompanyRepositoryImplRediscoverySeamTest.kt` proves this fix composes correctly with TD-013 within a single outer call — a stale endpoint that rediscovers mid-call and then genuinely needs TD-013's `NO_COMPANY_SELECTED` recovery resolves fully, with no double rediscovery, no generic "Connector rejected the request", and a companion test confirming an unrelated 400 during the same rediscovery is never mistaken for that recoverable case. |

---

## TD-018 — Packaged transport identity and mutable Connector paths lived under install resources

| Field | Value |
|-------|-------|
| **ID** | TD-018 |
| **Description** | The packaged Connector inherited CWD-relative mutable defaults. Transport key/certificate and Tally request-audit output therefore resolved beneath packaged resources. Reinstall replaced the TLS identity while preserving connectorId, producing a legitimate Android fingerprint mismatch; a system-wide Program Files install also made the audit directory unwritable and caused the child to exit during startup validation. |
| **Priority** | P0 |
| **Status** | Implementation and automated validation in progress; installed and physical acceptance pending. |
| **Repair boundary** | All mutable paths are explicit AppData paths; packaged startup rejects any mutable path beneath the install root. Legacy identity migration derives the real `resources/connector/dist/data/transport` layout, preserves byte-identical key/cert material when available, and explicitly reports that one legitimate re-pair is required when a previous reinstall has already destroyed the identity. Child fatal stderr is bounded and sanitized. |
| **Security invariants** | No fingerprint, connectorId, trusted-device, TLS-pinning, or secure-pairing validation is weakened. A missing old private key is never reconstructed or bypassed. |
| **Required acceptance** | Clean-commit installer: fresh system-wide install, Connector health, Tally companies, DB/fingerprint continuity through same-version reinstall and upgrade, exact bundled-executable firewall rules, then physical Android reconnection. |

---

## Index

| ID | Summary | Priority | Status | Target |
|----|---------|----------|--------|--------|
| TD-001 | Parent encoding normalization (`&#4; Primary`) | P2 | Open | 5A |
| TD-002 | Desktop company selection UI | P2 | **Resolved** | 4B |
| TD-003 | Connector process supervision | P2 | **Resolved** | 4C |
| TD-004 | Tally host/port not forwarded to connector spawn | P3 | Open | 5A |
| TD-005 | JSON ledger repository | P2 | **Resolved** | 5A-P |
| TD-006 | Durable interrupted sync resume | P2 | **Resolved (controlled pilot)** | 5A-P / Reliability Step 2 |
| TD-007 | Extraction-phase cancellation | P3 | **Resolved w/ limitation** | 5A-P |
| TD-008 | Insecure default network binding | P2 | **Resolved** | 5A-P |
| TD-009 | Authenticated LAN access | P2 | Open | 5B |
| TD-010 | Diagnostic export privacy allowlist | P2 | **Resolved (export surfaces)** | Reliability Step 3 |
| TD-011 | Ledger name-slug identity / shallow export | P1 | **Resolved (controlled pilot)** | Reliability ledger-identity |
| TD-012 | Manual private-IP entry required for Trusted-LAN pairing | P0 | **Fixed (automated validation) — pending physical retest** | Pre-MVP-1 release hardening |
| TD-013 | Post-pairing reconnection loses selected company / authenticated transport blocks auto-recovery | P0 | **Fixed (automated validation) — pending physical retest** | Pre-MVP-1 release hardening |
| TD-014 | Desktop dashboard/company-list has no re-poll after a transient post-startup failure | P1 | **Implemented — automated recovery validation passed; physical confirmation pending** | Pre-MVP-1 release hardening |
| TD-015 | Desktop business clients retain stale Connector endpoint after trusted-LAN route resolution | P0 | **Implemented — automated validation passed; physical confirmation pending** | Pre-MVP-1 release hardening |
| TD-016 | Securely paired Android diagnostics report legacy emulator endpoint instead of authenticated transport state | P0 | **Fixed (automated validation) — pending physical retest** | Pre-MVP-1 release hardening |
| TD-017 | Authenticated Android reconnect permanently pins pairing-time Connector endpoint | P0 | **Implemented — automated validation passed; physical confirmation pending** | Pre-MVP-1 release hardening |
