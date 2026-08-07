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
