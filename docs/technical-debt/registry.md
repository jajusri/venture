# Technical Debt Registry

Engineering-tracked compromises, defects, and deferred work.  
**Do not remove entries** — update status and append resolution notes.

---

## TD-001 — Parent encoding normalization / Tally `&#4;` illegal-character export artifact

| Field | Value |
|-------|-------|
| **ID** | TD-001 |
| **Description** | Tally returns group parent as `"&#4; Primary"` (XML character entity + "Primary"). Hierarchy validator recognizes only normalized `"Primary"` as virtual root. Built-in root groups flag `MISSING_PARENT`; extraction status is `INCOMPLETE`. **2026-08-16 escalation:** this is not a Groups-only cosmetic issue. `&#4;` is a numeric XML character reference whose decoded value (0x04, EOT) is illegal under XML 1.0. The shared `TallyXmlResponseParser`'s `assertXml10Characters()` (`connector/budcom_connector/src/tally/xml/response-parser.ts`) correctly rejects any occurrence of this artifact — as a literal control byte *or* as the numeric reference itself — with `XmlParseError('xml_illegal_character', ...)`, regardless of which collection (Groups, Vouchers, Ledgers, Stock items) it appears in. Groups happened to tolerate it (or predate this validation being added) and only showed a display/normalization defect; a controlled-pilot physical test (Session 2, 2026-08-16, company ESTIMATION — the exact same company TD-001 was originally observed on) hit a **full Voucher sync failure** (`parser_failure`, 0 vouchers processed) consistent with this same artifact now appearing in Voucher-relevant free-text fields. Reproduced mechanically: `test/unit/voucher/voucher-parser.test.ts` proves `&#4;` in a Voucher's `PARTYLEDGERNAME` reliably produces the exact `malformed-xml` → `VALIDATION_ERROR` → `parser_failure` chain observed on the physical device. Not yet proven that Tally actually sent `&#4;` in this exact failure (no raw response is ever captured or persisted, by deliberate privacy design), but this is now the leading, code-confirmed hypothesis, not speculation. |
| **Impact** | **Escalated from Medium to Critical for release.** Previously: hierarchy display trust only, 15 false positives on ESTIMATION (28 groups). Now: if confirmed, this is a single shared-layer defect capable of silently failing *any* Tally collection's sync — currently demonstrated against Vouchers, a core MVP-1 workflow, not just Groups' hierarchy display. |
| **Priority** | **P0** (escalated from P2) |
| **Target milestone** | Pre-MVP-1 release hardening (escalated from 5A) |
| **Status** | **CLOSED — PHYSICALLY CONFIRMED FIXED (2026-08-16).** Desktop `0.4.12` physical retest: Voucher sync against ESTIMATION completed successfully from the Android `continuity.15` app (no manual Desktop-side sync needed — see closure doc §28.2 for why that's expected, not a gap). Verified directly (not taken on report alone): the failure audit shows no new entry for this run, and a read-only query of the live Connector SQLite DB confirms snapshot `945a4735-2333-4786-8389-c98d80a26fe9` PROMOTED, 94 vouchers, 189 ledger entries, 332 inventory entries, **zero incomplete/rejected records**. Full evidence and honest limitations (exact `amountSignConflictCount` for this run is not persisted/retrievable — documented, not fabricated) in `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` §28. Full round-by-round history retained below for the audit trail. Round 4 summary: **ROOT CAUSE CONFIRMED AND FIXED (2026-08-16).** The 0.4.11 retest's audit entry confirmed `parseReason: amount-sign-conflict` directly (not inferred) — `VoucherLedgerEntryParser`'s `isDeemedPositive !== signedAmount.startsWith('-')` assertion. Regression finding: `ISDEEMEDPOSITIVE` reflects a ledger's debit/credit-positive *nature* by group classification; the signed `AMOUNT`'s sign independently encodes *this specific transaction's* actual Dr/Cr direction — the codebase's own established `inferSideFromSign()` convention (`extraction/normalization/amounts.ts`) already treats these as unrelated, deriving side purely from sign with no reference to `ISDEEMEDPOSITIVE`. The multi-phase rewrite's hard assertion (introduced `aa96637`, 2026-07-30, never present in any pre-0.4.4 build) conflated two legitimately independent fields as a modeling error, not a data-integrity signal. **Approved fix (Option C):** the disagreement is now tolerated, not fatal — `VoucherLedgerEntryParser` flags the entry (`amountSignConflict: boolean`) and continues; the signed Amount remains the sole source for Dr/Cr side (unchanged — `toVoucherLedgerEntry()` already derived it this way); `IsDeemedPositive` is preserved exactly as reported, never rewritten; conflicts are counted (never which voucher/ledger) via a new `amountSignConflictCount`, threaded end-to-end through `VoucherExtractionResult`/`VoucherSynchronizationResult` exactly like the existing `illegalCharactersSanitized` telemetry. Structural validation is unchanged and remains fatal (malformed XML, missing envelope/fields, malformed amounts). Reviewed `VoucherInventoryEntryParser` for an equivalent cross-field check — none exists (no `IsDeemedPositive` field on inventory entries at all), so nothing there was changed, per the explicit "do not broaden blindly" instruction. Full history below, retained for audit trail: **2026-08-16 round 3:** the 0.4.10 audit entry showed `reasonCode: voucher-ledger-validation` with no `XmlParseError` and no `VoucherReconciliationError` attached across all 5 retries — ruling OUT the illegal-character and two-phase-deletion hypotheses for this failure (see full evidence in `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` §21). The remaining code in that path, `VoucherLedgerEntryParser`, threw only plain untyped `Error`s for all 8 of its checks — now typed as `VoucherEntryParseError` with a distinguishable reason per branch (`invalid-root`/`missing-header`/`missing-body`/`missing-data`/`missing-collection`/`tally-line-error`/`missing-parent-guid`/`missing-ledger-name`/`missing-or-malformed-amount`/`missing-or-invalid-is-deemed-positive`/`amount-sign-conflict`; inventory parser typed identically for consistency, plus `missing-stock-item-name`/`invalid-parent-guid-node-count`), threaded through as a new `parseReason` field (§22.1). Regression analysis: the multi-phase Voucher architecture (including the `ISDEEMEDPOSITIVE`-vs-signed-`AMOUNT` sign-conflict assertion) was introduced wholesale in commit `aa96637` (2026-07-30), 4 days after the user's last confirmed-successful sync on Desktop 0.4.3 (commit `10a3e280`, embedded Connector `0.3.1`, which per its own quarantine note used an entirely different legacy compound extraction with no separate ledger-entries request and no sign-consistency check at all); the assertion has never been modified since introduction, and no controlled-pilot candidate between 0.4.4 and 0.4.9 has a documented successful physical Voucher sync against ESTIMATION (§22.2). **Per explicit instruction, this is not assumed to be the cause — no semantic fix has been made.** A new diagnostic-only candidate (0.4.11) was produced to confirm the exact branch on one more physical retest before any behavior change. Architectural decision approved 2026-08-16: sanitize XML-1.0-illegal C0 control characters (literal bytes and numeric character references, including `&#4;`) at the shared parsing boundary, before structural parsing, rather than rejecting the whole response. Implemented in `connector/budcom_connector/src/tally/xml/response-parser.ts` (`sanitizeXml10IllegalCharacters()`), applied uniformly to every Tally collection (Groups, Ledgers, Stock items, Vouchers). Structural XML validation remains fully strict and unchanged — only the character-legality check changed from reject to sanitize. **2026-08-16 physical retest result: FAIL.** Desktop candidate 0.4.9 (bundling this sanitizer) installed successfully, but a fresh Voucher sync against ESTIMATION still failed with `parser_failure`, 0 vouchers processed. **The illegal-character-sanitization mechanism, while a real and independently-tested fix for the `&#4;`-in-text-field case, is confirmed NOT to be the (or not the only) cause of the actual production failure.** The first diagnostic-hardening attempt (`failureDetail`/`illegalCharactersSanitized` via `logger.info()`) was also confirmed unretrievable from that failed run: the packaged Connector child process is spawned with `stdio: ['ignore', 'ignore', 'pipe']` (`node-process-spawner.ts`), so `logger.info()`/`logger.debug()` (routed to `console.log`/stdout) are discarded entirely in production, and Android's `AuthenticatedConnectorApiClient` never attaches network-logging interceptors even in debug builds — so the real failure classification from 0.4.9 could not be recovered from either side. **Round 2 (this entry, 2026-08-16 continued):** (a) a new, file-based `VoucherSyncFailureAuditor` (`connector/budcom_connector/src/services/voucher/voucher-sync-failure-audit.ts`) writes structural-only failure records (fixed enum failureReason/reasonCode, which Tally operation, response byte length, a correlation-only response hash, illegal-character-sanitization count, and — when applicable — a new `reconciliationReason` classification) directly to its own rotated file, bypassing both the stdout-discard and the stderr byte-budget constraints; wired into production via `bootstrap/register-services.ts`'s `ServiceTokens.VoucherSynchronization` factory, with Desktop-side plumbing (`app-data-layout.ts`, `connector-lifecycle-config.ts`, `desktop-config-resolver.ts`, `connector-packaged-paths.ts`) giving it a real, reinstall-surviving writable path (`{userDataRoot}/connector-diagnostics/voucher-sync-failure-audit.jsonl`) mirroring the existing `TallyRequestAuditor` pattern exactly. (b) A new, independently-motivated hypothesis was investigated per explicit physical evidence (a few vouchers that existed earlier in ESTIMATION have since been deleted in Tally): the two-phase Voucher extraction (discovery request, then separate sequential `VoucherLedgerEntries`/`VoucherInventoryEntries` requests joined client-side by GUID) can produce an `orphan-ledger-entry`/`orphan-inventory-entry` reconciliation failure if a voucher is deleted from Tally between the discovery request and the later phase requests — proven with a new regression test (`test/unit/voucher/voucher-inventory-extraction.test.ts`, "fails closed with a distinguishable reconciliationReason when an inventory entry references a Voucher deleted between phases"). This is now the leading alternative hypothesis alongside the original illegal-character theory; the ledger/inventory reconciler's previously-untyped `Error` throws were converted to a typed `VoucherReconciliationError` (`connector/budcom_connector/src/tally/voucher/voucher-reconciliation-error.ts`) so this distinction is now diagnosable rather than collapsing into the same `parser_failure`/`malformed-xml`-adjacent bucket. Deletion-reconciliation semantics themselves required no new architectural decision: the atomic whole-window-replace-on-success / never-touch-existing-data-on-failure design was already correctly implemented and documented (`docs/specifications/business-os-voucher-contract-v1.1.md` §6.2). Sanitizer + reconciliation-error typing + new auditor all confirmed present in a clean `tsc -p tsconfig.build.json` build of `connector/budcom_connector/dist/` (2026-08-16), so this reaches the exact artifact the Desktop packaging pipeline bundles, not just unit tests. (c) Request/response shape investigation: unlike Ledgers/Stock items (single flat TDL FETCH master query, one live request), Vouchers is the only collection built from three sequential live Tally queries where phases 2/3 (`voucher-request.ts:74-140`) independently re-query live Tally rather than reusing phase 1's GUID set, joined via a TDL `WALK`/`$$Owner:$GUID` construct unique to Vouchers. This refines (not replaces) the deletion hypothesis: pure deletion between phases produces the already-tolerated "missing from later phase" case, not an orphan; the orphan path instead requires a GUID appearing in a later phase that phase 1 didn't see — any concurrent Tally-side edit during the sync window, not deletion alone. The retrieved `reconciliationReason` will confirm or rule this out directly. |
| **Evidence** | `docs/testing/milestone-3c-groups-manual-validation.md`; bug ID M3C-001 in `docs/stage-updates/milestone-3c-stage-update.md`; controlled-pilot Session 2 physical failure (2026-08-16, ESTIMATION) — sanitizer fix validated by tests but **physical retests on Desktop 0.4.9, 0.4.10, and 0.4.11 (2026-08-16) all FAILED** identically until this round. **Decisive audit evidence** (all read directly from `{userDataRoot}/connector-diagnostics/voucher-sync-failure-audit.jsonl`, never guessed): 0.4.10's 5 entries ruled out illegal-character/two-phase-deletion hypotheses (identical `reasonCode: voucher-ledger-validation`, `responseByteLength: 64941`, `responseHash: 7c7184b0...`, no `reconciliationReason`/XML-parse fields — closure doc §21); 0.4.11's entry confirmed `parseReason: amount-sign-conflict` directly. Round-4 tests: `voucher-entry-parsers.test.ts` rewritten to prove the conflict now parses successfully (`amountSignConflict: true`) instead of throwing, plus the still-fatal branches (malformed amount, invalid envelope, etc.) unchanged; `voucher-extractor.test.ts` proves the full production path completes with correct ledger totals/Dr-Cr sides and a countable `amountSignConflictCount`, and that other `parseReason`s remain fatal; `voucher-snapshot-sync.test.ts` proves the count surfaces through the full sync result without aborting. Full connector suite: 159 files / 1414 tests passing; desktop 66/677; contract 1/5; `eslint`/`tsc --noEmit` clean. |
| **Fix location** | Round 1: `connector/budcom_connector/src/tally/xml/response-parser.ts` (sanitizer) + diagnostic-detail threading. Round 2: `connector/budcom_connector/src/services/voucher/voucher-sync-failure-audit.ts` (file-based auditor) + `bootstrap/register-services.ts` (production wiring) + `voucher-reconciliation-error.ts` + reconciler/joiner typed throws + Desktop app-data/lifecycle plumbing. Round 3: `voucher-entry-parse-error.ts` (typed parser error) + `voucher-ledger-parser.ts`/`voucher-inventory-parser.ts` (typed throws, shared envelope validation) + `parseReason` threading. **Round 4 (the actual semantic fix):** `connector/budcom_connector/src/erp/voucher/voucher-ledger-domain.ts` (`amountSignConflict` field) + `connector/budcom_connector/src/tally/voucher/voucher-ledger-parser.ts` (no longer throws) + `connector/budcom_connector/src/tally/voucher/voucher-ledger-reconciler.ts` (`VoucherLedgerJoinResult`, counts conflicts) + `connector/budcom_connector/src/tally/voucher/voucher-extractor.ts` + `connector/budcom_connector/src/erp/ports/vouchers.ts` + `connector/budcom_connector/src/services/voucher/voucher-snapshot-sync.service.ts` + `connector/budcom_connector/src/services/voucher/voucher-sync-progress.ts` (`amountSignConflictCount` threading). |
| **Physical confirmation** | **DONE (2026-08-16).** Desktop `0.4.12` (SHA-256 `f646aa5bf28065bb7937ba09f8130e9f6d1683019665ce2af8f7deaa0d7b45ec`, bundling Connector `0.4.4`) + Android `continuity.15` (unchanged). Voucher sync against ESTIMATION completed: 94 vouchers, 0 incomplete, snapshot PROMOTED. See closure doc §28 for the full evidence inspection. |

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
| **Physical extension (2026-08-09)** | The iQOO physical gate proved secure HTTPS reachability and stable trust, then exposed a second continuity boundary: the durable `ESTIMATION` selection was more than the default eight-hour lease old, `/session/validate` correctly returned HTTP 410 `SESSION_EXPIRED`, and selecting that same company returned `DUPLICATE_SELECTION` without renewing `selectedAt`. Android collapsed the typed 410 into a generic server error, so its existing bounded recovery never ran. The permanent extension renews the authoritative lease on an idempotent same-company selection (HTTP 200), preserves exact `SESSION_EXPIRED` through authenticated transport mapping, and performs one bounded same-company select/validate recovery without altering trust, credentials, fingerprint pinning, or unrelated 410 behavior. Authenticated diagnostics now also fetch public `/health` and `/ready` through the pinned endpoint after the credentialed reachability probe succeeds, so a secure connection is no longer reported as readiness unknown merely because those public endpoints do not require bearer authentication. Physical retest remains required for the replacement Windows and Android candidates. |

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

## TD-019 — Android release exposes stale legacy endpoint and lacks safe active-trust replacement

| Field | Value |
|-------|-------|
| **ID** | TD-019 |
| **Description** | Customer Android builds inherited the emulator-only `10.0.2.2:8080` legacy endpoint, allowed cleartext LAN traffic, and exposed manual server-address controls even though a securely paired device uses the separate certificate-pinned authenticated transport. Settings and several dashboard/diagnostic refresh paths could therefore display or probe the unrelated legacy endpoint. Separately, a device with an `ACTIVE` secure credential had no explicit customer-facing replacement action, and certificate-pin mismatch was collapsed into a generic transport failure. A legitimate historical identity loss could leave the customer unable to understand the trust failure or deliberately re-pair without destructive app-data clearing. |
| **Observed customer state** | Physical iQOO validation on 2026-08-09 showed the phone online and the UI reporting “Secure connection active,” while the Connection card still displayed `http://10.0.2.2:8080/`. The stale value was a legacy-display/configuration artifact, not evidence that the authenticated request had used that address. Earlier validation also exposed the absence of an active-state re-pair action after a legitimate historical transport-identity loss. |
| **Impact** | P0 release risk: misleading endpoint/status information invites unsafe manual-IP changes; generic trust errors can be mistaken for ordinary network loss; and the only apparent recovery may be destructive Android reset/reinstall even though a verified, explicit credential replacement is sufficient. |
| **Priority** | P0 |
| **Target milestone** | Pre-MVP-1 Android controlled-pilot hardening |
| **Status** | **Implemented — automated validation passed; physical release-APK confirmation pending** |
| **Resolution** | Customer release configuration now has a blank Connector default, blocks cleartext, and hides manual server configuration; emulator/manual controls remain debug-only. Settings, Dashboard, Diagnostics, and connection probes select the authoritative transport and never surface the legacy URL for an `AUTHENTICATED` credential. Certificate verification now returns sanitized typed identity-mismatch/certificate-invalid outcomes rather than an undifferentiated socket failure. LAN rediscovery still treats Connector ID only as a candidate filter and accepts/persists a new host/port only after verification against the existing pinned fingerprint. The active pairing screen exposes **Replace / Re-pair Connector**. Replacement verifies the new endpoint, fingerprint, credential ID, Connector ID, and device ID before one atomic encrypted `ACTIVE` publication; any parse, network, identity, certificate, encryption, or persistence failure leaves the old active record unchanged. No automatic fingerprint acceptance, legacy fallback, or trust weakening was introduced. |
| **Automated validation (2026-08-09)** | Debug JVM: 793 tests, 0 failures; release JVM: 793 tests, 0 failures. Focused coverage includes blank release endpoint configuration, local failure when no endpoint is configured, stale legacy URL suppression under authenticated trust, typed identity/certificate failures, verified endpoint rediscovery, explicit active-state replacement UI, failed replacement retaining the exact old active record, and successful verified atomic replacement. `lintDebug`, `lintRelease`, `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest` all passed. The generated APKs were not installed and no phone was accessed. |
| **Physical follow-up finding (2026-08-09)** | The continuity APK correctly preserved the historical active credential, classified the live SPKI mismatch, and refused silent trust replacement. However, Settings could not expose the advertised replacement action: entering pairing management with an existing `ACTIVE` record caused `SecurePairingRoute` to infer completion from the steady-state `Active` phase and immediately navigate Home, making its own **Replace / Re-pair Connector** button unreachable. The earlier UI tests rendered Settings and the inner pairing screen independently and therefore missed the combined route lifecycle. Android acceptance stopped before scanning or altering trust. |
| **Follow-up correction** | Completion navigation is now driven only by a one-shot `PairingCompleted` effect emitted after a user-initiated pairing or re-pair has been verified and atomically published. Initial synchronization of an existing `ACTIVE` record emits no completion event, so pairing management remains visible. Cancellation, QR rejection, and dismissal after a failed replacement re-read the encrypted vault and return to its authoritative persisted state, preserving and displaying the old active trust. Focused suite: 36 tests, 0 failures. Full debug and release JVM suites: 798 tests each, 0 failures; `lintDebug`, `lintRelease`, `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest` passed. Physical confirmation of the corrected continuity APK remains required. |
| **Required physical confirmation** | Build/sign one controlled Android candidate from a clean committed tree, record commit and APK SHA-256, then use normal customer UI only: install/update without clearing data; confirm no stale emulator URL or manual-IP entry; explicitly re-pair once if the historical pinned identity is genuinely obsolete; confirm SPKI, Connector ID, company/session, MVP smoke, and automatic reconnect across Android/Desktop/Tally/Wi-Fi restarts and an ordinary DHCP address change. |

---

## TD-020 — Android Voucher sync action was silently discarded

| Field | Value |
|-------|-------|
| **ID** | TD-020 |
| **Description** | The Android Sync screen advertised Voucher sync, but its individual action returned before invoking the repository. The shared start path also assumed every target exposed a pollable status endpoint even though Voucher sync intentionally returns its final result from a blocking start request. Physical acceptance showed ledger and stock-item extraction while no Voucher request, snapshot, or failure record reached the Connector. |
| **Priority** | P0 |
| **Target milestone** | Pre-MVP-1 Android controlled-pilot hardening |
| **Status** | **Implemented — automated validation in progress; physical confirmation pending** |
| **Resolution** | Voucher start now reaches the authenticated `POST /sync/vouchers` operation and waits for its authoritative blocking response without calling the deliberately unavailable Voucher status endpoint. Ledger and stock-item polling is unchanged. “Sync all” remains sequential and explicitly includes Ledgers, Stock items, and Vouchers. |
| **Regression boundary** | View-model tests prove an individual Voucher action makes exactly one repository start call and zero Voucher status calls, and that “Sync all” reaches all three targets in order. Existing authenticated-operation, repository, and Connector route tests continue to cover the pinned HTTPS request and extraction service. |
| **Required physical confirmation** | Install the continuity.4 APK in place without clearing data or changing trust, run individual Voucher sync, prove a fresh Connector Voucher snapshot and unchanged secure identity/session, then smoke voucher list, detail, Share Summary, Share PDF, and Save PDF. |

---

## TD-021 — Authenticated SESSION_EXPIRED renewal is scoped to one call site, not the transport layer (PROPOSAL — not implemented)

| Field | Value |
|-------|-------|
| **ID** | TD-021 |
| **Description** | The Android authenticated client added a typed `SESSION_EXPIRED` (HTTP 410) result (`AuthenticatedConnectorResult.SessionExpired`, `AUTHENTICATED_SESSION_EXPIRED_CODE`). Automatic renew-and-retry for it is implemented in exactly one place — `CompanyRepositoryImpl.validateSessionRemote()`'s `isRecoverableSessionRejection()` branch, which re-runs `selectCompany()`. Every other authenticated call path (`ConnectorOperationalStatusPortImpl.resolveAuthenticated()`'s dashboard/connection-status probe, and all of `AuthenticatedSyncRemoteDataSource` — Ledger/Stock-item/Voucher start, status, statistics, runs) has no equivalent handling: a `SessionExpired` result there falls into the generic `AuthenticatedUnavailable("The Connector returned an unexpected response.")` bucket with no renewal attempt. This is the same shape of gap as TD-013 (company selection lost after reconnect) and TD-017 (pairing-time endpoint pinned) — a fix landing at one call site instead of the shared transport layer, which is why this category of bug keeps resurfacing at new call sites rather than being closed permanently. |
| **Priority** | P1 (proposal — no severity confirmed in the field; found via source audit, not a reproduced incident) |
| **Target milestone** | Proposed — pending architectural scoping by ChatGPT (architectural authority per current project scope) |
| **Status** | **Proposed — not implemented. Out of scope for the Android Voucher-sync task that identified it; Connector/Desktop and cross-cutting Android transport changes require separate authorization.** |
| **Proposed permanent fix** | Move renew-and-retry-once-on-`SESSION_EXPIRED` out of `CompanyRepositoryImpl` and into the shared authenticated-transport layer (e.g. a decorator/interceptor around every `AuthenticatedConnectorApiPort.execute()` call), so every current and future authenticated operation gets the same self-healing behavior by construction instead of by each call site remembering to add it. |
| **Explicitly not part of this proposal** | Investigation during this audit also found a live Desktop "Not connected / no company selected" state. Log evidence (`budcom-desktop.log`, event `connector_stopped` at 2026-08-10T06:20:24Z, traced to the `desktop:stop-connector` IPC handler) shows this was an **explicit stop with no subsequent restart, not a crash or supervisor failure** — TD-003's process supervision handled a genuine transient flap earlier the same day correctly. No process-supervision change is proposed here. |
| **Verification needed before implementation** | Confirm whether `SESSION_EXPIRED` was actually the cause of the individual-Voucher-sync UI showing no visible reaction during physical continuity testing (unconfirmed — no device logcat was available; the ViewModel's `AppResult.Failure` path would normally surface a banner error, which was not observed, so this remains an open question rather than a confirmed reproduction). |

---

## TD-022 — Android accumulates one complete authoritative window in memory before atomic Room commit

| Field | Value |
|-------|-------|
| **ID** | TD-022 |
| **Description** | `VoucherRepositoryImpl.fetchCompleteWindow` (and, after f78cd01, its parallel detail accumulation) fetches every bounded page of a company+date window and holds the full `List<VoucherSummary>` plus, when requested, the full `List<VoucherDetails>` (ledger/inventory lines included) in memory before a single Room transaction persists the window. This was already true for summaries alone before f78cd01; the offline-complete-sync fix adds the (larger) detail payload to the same accumulate-then-commit shape rather than introducing it. |
| **Impact** | Low today — recent/typical windows for an SME company are realistically hundreds to low thousands of vouchers. Grows with window size; a very large historical window (e.g. a company with a `booksFrom` several years back reconciled in one background pass) could hold a materially larger in-memory set before its single commit. No physical/build evidence yet shows this is actually blocking. |
| **Priority** | P3 (performance hardening, not correctness) |
| **Target milestone** | Post-MVP-1 |
| **Status** | Open — recorded, not implemented |
| **Introduced** | Present since the original windowed-refresh design (cb5fb91); extended, not created, by f78cd01 |
| **Likely fix direction** | Stream/batch the Room write per fetched page instead of accumulating the whole window, if a future large-window scenario proves this necessary — would need to preserve the existing pagination-completeness proof (whole-window fail-closed) and the single-transaction atomicity guarantee, so isn't a small change. |
| **Do not action without** | Build or physical evidence (OOM, GC pressure, observed latency) on a real large-window company. |

---

## TD-023 — Connector `querySnapshot()` loads the full company snapshot on every paged `GET /api/v1/vouchers` request

| Field | Value |
|-------|-------|
| **ID** | TD-023 |
| **Description** | `SqliteVoucherRepository.querySnapshot(companyId)` has no SQL-level date/page filtering — it reads and `JSON.parse`s every voucher row in the company's active snapshot on every call, then `VoucherApplicationServiceImpl.list()` filters/sorts/paginates the result in JS. This is unrelated to `includeDetails` specifically: it was already true for summary-only list calls before 79690d0, which only changes whether the per-item response payload is trimmed after this same full load. |
| **Impact** | Low today for typical company sizes; scales linearly with total company voucher count per request, so a paginated Android window fetch against a very large company snapshot re-does this full load once per page (e.g. ~100 requests for a 10,000-voucher window), not once for the whole fetch. No physical/build evidence yet shows this is actually blocking. |
| **Priority** | P3 (performance hardening, not correctness) |
| **Target milestone** | Post-MVP-1 |
| **Status** | Open — recorded, not implemented |
| **Introduced** | Present since the original snapshot-query design; not touched by 79690d0/f78cd01 |
| **Likely fix direction** | Push `dateFrom`/`dateTo` (and ideally pagination) filtering into the SQL query against `voucher_headers` instead of loading the full snapshot into JS and filtering there. |
| **Do not action without** | Build or physical evidence (measured request latency, CPU) on a real large-snapshot company. |

---

## TD-024 — `desktop:choose-storage-mode` did not re-validate the drive is actually removable

| Field | Value |
|-------|-------|
| **ID** | TD-024 |
| **Description** | The private-removable-storage IPC handler (`apps/budcom_desktop/src/main/main.ts`, `desktop:choose-storage-mode`) called `readExistingVaultOnDrive(choice.driveLetter)` / `createPrivateVault(choice.driveLetter)` directly on whatever `driveLetter` string the renderer sent, without first checking it against `removableVolumeEnumerator.listRemovableVolumes()`. The enumerator was only consulted afterward, purely to look up a display label. IPC input validation (`validateChooseStorageModeInput`) only bounded the syntactic shape (`^[A-Za-z]:\\?$`), not which drive letter. The renderer's picker only happening to list removable drives is a UI convenience, not a security boundary — this codebase's own established convention elsewhere is to re-validate on the main-process side. Once a bad selection was persisted as `lastKnownDriveLetter`, every later `resolvePrivateVault()` fast-path check re-attached to it directly via the marker file, without ever re-consulting the enumerator, so the "only removable drives" invariant was not enforced end-to-end after the first (unvalidated) selection. |
| **Impact** | High for a feature whose entire premise is "never a fixed disk" — nothing in the traced code path stopped a caller from selecting `C:\` as "Private Removable Storage"; `createPrivateVault('C:\\')` would have written `C:\BudcomPrivate\vault.json` and pointed the Connector at `C:\BudcomPrivate\<vaultId>\connector-data`. Undermines the SEC-004 threat-model mitigation (`docs/governance/BUDCOM-THREAT-MODEL-AND-SECURITY-REGISTER.md`, "wrong USB / lookalike media"), which was only actually proven for the rediscovery path, not initial selection. |
| **Priority** | P1 |
| **Target milestone** | Pre-MVP-1 release hardening |
| **Status** | **Fixed (automated validation)** — found and corrected during the 2026-08-15 MVP-1 hardening/controlled-pilot closure pass; no physical USB test performed |
| **Introduced** | Private-removable-storage feature (`9d3b587` "feat(desktop): add private-storage runtime/config architecture", 2026-08-11/12) |
| **Evidence** | Found by source review during controlled-pilot closure research, 2026-08-15 — no live incident, no physical device involved |
| **Resolution** | `desktop:choose-storage-mode` now fetches `removableVolumeEnumerator.listRemovableVolumes()` and rejects the request (`{ ok: false, message: 'Selected drive is not currently detected as removable storage...' }`) before ever calling `readExistingVaultOnDrive`/`createPrivateVault`, via a new pure helper `isEnumeratedRemovableDrive(driveLetter, volumes)` in `private-storage-resolver.ts` (same file, same style as the existing pure resolver functions — mirrors the "only ever inspect drives the enumerator reports as removable" invariant `resolvePrivateVault()`'s rediscovery path already enforced). The existing rediscovery/reconnection path was not touched — it already enforced this correctly. |
| **Regression tests** | `apps/budcom_desktop/test/unit/private-storage-resolver.test.ts` — `isEnumeratedRemovableDrive` proven true only for an enumerated drive letter, false for a fixed disk when nothing is enumerated, and case-insensitive. Full desktop suite re-run clean: 677/677 (674 baseline + 3 new), `tsc -p tsconfig.main.json --noEmit` clean. |
| **Not addressed by this fix — see TD-025** | The mid-session storage-loss UX gap and the plain-Restart-button re-resolution gap found in the same review are separate, tracked there. |

---

## TD-025 — Private-storage loss mid-session degrades to a generic "Disconnected" state, not the purpose-built recovery screen

| Field | Value |
|-------|-------|
| **ID** | TD-025 |
| **Description** | `startPrivateStorageWatchdog()` (`main.ts`, 10s poll) correctly detects storage loss and stops the Connector, but its `notifyRenderer()` call only sends a bare `desktop:status-updated` signal with no payload. The renderer's `onStatusUpdated` handler re-runs the ordinary `refreshUi()`/`loadCompanies()` dashboard refresh — it never re-invokes `renderStorageGate()`/`getStorageStatus()`, which only runs once, at `startDesktopShell()` entry. A mid-session storage loss therefore degrades the dashboard to a generic "Disconnected" state (no storage-specific `userMessage`) rather than the purpose-built "Private BUDCOM storage is not connected" screen with Retry/Locate/Exit — that screen is only reachable via a full app restart/reload. This also means `docs/governance/BUDCOM-OPERATIONAL-OBSERVABILITY-SPEC.md`'s requirement that "Private Storage connected/missing/wrong-media state" remain an always-visible Desktop signal is not met post-startup. Separately, the ordinary dashboard "Restart Connector" button (`desktop:restart-connector`) does not call `resolveStorageGate()` either — it reuses the already-constructed `lifecycleService`'s last-resolved config, so if the drive reconnects on a *different* drive letter, an ordinary Restart click keeps failing the Connector-side guard with a generic lifecycle error (fails closed, but unhelpfully) until a full app relaunch. |
| **Impact** | Medium — fails closed and safely in all cases (no data corruption path, no wrong-drive attach), but a normal operator whose USB drive is bumped or sleeps mid-session sees a misleading generic "Disconnected" state instead of an actionable storage-specific one, and cannot self-recover via the dashboard's own Restart control if the drive letter also changed. |
| **Priority** | P2 |
| **Target milestone** | Post-MVP-1 (UX hardening) unless a controlled-pilot operator hits this in practice, in which case re-prioritize |
| **Status** | Open — recorded, not implemented |
| **Introduced** | Private-removable-storage feature (2026-08-11/12), present since the watchdog was added |
| **Likely fix direction** | Have `notifyRenderer()` include (or trigger a companion IPC push of) current storage-gate state so the renderer can re-run `renderStorageGate()` on any state change, not only at startup; have `desktop:restart-connector` call `resolveStorageGate()` first instead of reusing stale resolved config. |
| **Do not action without** | Product-owner confirmation this is worth prioritizing ahead of other MVP-1.0.x items — it is a real gap but not a data-safety one. |

---

## Index

| ID | Summary | Priority | Status | Target |
|----|---------|----------|--------|--------|
| TD-025 | Private-storage loss mid-session degrades to generic "Disconnected" rather than the storage recovery screen | P2 | Open — recorded, not implemented | Post-MVP-1 (or sooner if hit in pilot) |
| TD-024 | `desktop:choose-storage-mode` did not re-validate the drive is actually removable | P1 | **Fixed (automated validation)** | Pre-MVP-1 release hardening |
| TD-023 | Connector `querySnapshot()` loads the full company snapshot on every paged request | P3 | Open — recorded, not implemented | Post-MVP-1 |
| TD-022 | Android accumulates one complete authoritative window in memory before atomic Room commit | P3 | Open — recorded, not implemented | Post-MVP-1 |
| TD-021 | Authenticated SESSION_EXPIRED renewal is scoped to one call site, not the transport layer | P1 | **Proposed — not implemented** | Pending architectural scoping |
| TD-020 | Android Voucher sync action was silently discarded | P0 | **Implemented — automated validation in progress; physical confirmation pending** | Pre-MVP-1 release hardening |
| TD-018 | Packaged transport identity and mutable Connector paths lived under install resources | P0 | **Windows physical lifecycle accepted; Android confirmation pending** | Pre-MVP-1 release hardening |
| TD-019 | Android release exposes stale legacy endpoint and lacks safe active-trust replacement | P0 | **Implemented — automated validation passed; physical release-APK confirmation pending** | Pre-MVP-1 release hardening |
| TD-001 | Tally Voucher sync `parser_failure` — root cause confirmed (`amount-sign-conflict`: IsDeemedPositive and signed Amount are independent Tally fields, wrongly asserted equal since the multi-phase rewrite) and fixed: disagreement now tolerated and counted, never fatal; physically confirmed on Desktop 0.4.12 + Android continuity.15, 94 vouchers, 0 incomplete | **P0** | **CLOSED — physically confirmed fixed (2026-08-16)** | Pre-MVP-1 release hardening |
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
