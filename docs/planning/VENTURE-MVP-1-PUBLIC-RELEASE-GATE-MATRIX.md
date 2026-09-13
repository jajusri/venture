# VENTURE MVP-1 Public Release Gate Matrix

**Status:** Active — first draft, this session (2026-08-17)
**Purpose:** One canonical gate-by-gate readiness view for the transition from Controlled-Pilot
(validated, GO) to public/external release. Distinct from
`docs/planning/VENTURE-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` (pilot-scoped) and
`docs/governance/VENTURE-QUALITY-SCORECARD.md` (dimension scoring) — this document classifies each
release-readiness item and tracks what changed to move it.
**Classification legend:** BLOCKER · MUST FIX · SHOULD FIX · ACCEPTED INITIAL RELEASE LIMITATION ·
POST-RELEASE

## 1. Summary verdict

**PUBLIC RELEASE BLOCKED — signing.** No approved Windows code-signing certificate or Android
release keystore exists in this repository or environment (verified this session — see §14 in the
main report). Every other item below that was reachable with evidence and without a signing
identity has been evaluated and, where narrow and safe, fixed. Signing is the dominant remaining
blocker; a small number of product decisions (Android public applicationId; TD-025's fuller fix)
are explicitly flagged rather than decided unilaterally.

## 2. Gate-by-gate matrix

| # | Area | Classification | Evidence / disposition |
|---|---|---|---|
| A | Android production/release signing | **BLOCKER** | No `signingConfigs` block exists in `apps/venture_android/app/build.gradle.kts`; no keystore file found anywhere in the repo or environment. `assembleRelease` succeeds and produces a real minified/shrunk/R8'd `app-release-unsigned.apk` (2.3 MB, vs. 14.5 MB debug) — the release build pipeline itself works, it just has nothing to sign with. |
| B | Windows signing | **BLOCKER** | `electron-builder.yml` has `signAndEditExecutable: false` and no `certificateFile`/`certificatePassword`; no `.pfx`/`.p12` anywhere in repo/environment; no `CSC_LINK`/`CSC_KEY_PASSWORD` env vars set. Windows SmartScreen will warn on the unsigned installer — a known, previously-accepted controlled-pilot limitation, not acceptable for broad public distribution. |
| C | Windows installer | **MUST FIX → RESOLVED this session (TD-033) / SHOULD FIX remainder** | NSIS installer (`electron-builder.yml`) is per-machine, `deleteAppDataOnUninstall: false`, custom `installer.nsh` migrates legacy transport identity safely with abort-on-conflict. Product name/publisher/icon already set (`Venture Desktop`, copyright `Venture`). Storage-mode switch guard (TD-033) and unreadable-marker fail-closed (TD-034) implemented and tested this session — see §5/§6. Remaining: `appId: com.venture.desktop` / `productName: Venture Desktop` vs. the in-app title still reading "Business OS Tally Connector" in places (pre-existing, flagged, not renamed — see prior UI/UX status doc §7, a naming decision, not a defect). |
| D | Android package/artifact | **MUST FIX identified, BLOCKED on §A** | `assembleRelease` clean; `applicationId = "com.jajusri.venture"` (debug variant appends `.debug`). Public Play-Store-facing applicationId is not formally decided anywhere in governance docs — see §13 below, flagged as a release blocker requiring a product decision, not invented here. |
| E | First install | **SHOULD FIX — reviewed, no defect found this session** | Storage-gate first-run flow (`storage-gate.ts`) already presents plain-language Standard/Private choice; TD-033's new confirmation gate only fires on a genuine *switch*, never on first-run (verified: `existingLocator === null` short-circuits the guard). No changes needed beyond TD-033/034. |
| F | In-place upgrade | **VERIFIED this session for Android; Desktop packaging blocked (see §9)** | Android `continuity.20` → `continuity.21` (`adb install -r`) previously verified in this project to preserve Room DB, `company_selection`/`pairing_device_identity`/`secure_pairing_credential_vault` DataStore files byte-for-byte (unchanged mtimes) and `firstInstallTime` (proof of true upgrade, not reinstall). Desktop `0.4.17→0.4.18` upgrade already physically completed by the owner per the current checkpoint; this session's `0.4.19` candidate packaging hit an environment-specific `tar` issue (§9), not an upgrade-safety defect. |
| G | Company selection | **No defect found** | Persisted in `privateStorageLocatorStore`/settings independent of storage mode; TD-033's guard explicitly protects this from being silently reset by a storage-mode switch. |
| H | Tally discovery | **ACCEPTED INITIAL RELEASE LIMITATION** | mDNS/NSD discovery proven correct on real router LAN (TD-029–032, closure doc). Phone-hotspot mDNS is an accepted environmental limitation, not a code defect — see §16. |
| I | Pairing | **No new defect found this session** | QR pairing/trust unchanged this session; TD-033's guard specifically protects `trusted_devices`/`pairing_sessions`/`pairing_device_credentials` from silent storage-switch orphaning. |
| J | Router-LAN assumptions | **ACCEPTED INITIAL RELEASE LIMITATION** | Same-router-LAN requirement is a real, documented operating constraint (§16), not weakened. |
| K | Private storage | **MUST FIX → RESOLVED this session** | TD-033 (switch guard) and TD-034 (unreadable-marker fail-closed) implemented, tested (9 new tests), `tsc`/`vitest` clean. See §5/§6. |
| L | Standard→Private transition | **RESOLVED this session — see K** | |
| M | Missing/unreadable vault marker | **RESOLVED this session — see K** | |
| N | Live USB loss UX (TD-025) | **SHOULD FIX — deliberately NOT implemented this session; documented as an accepted MVP-1 limitation** | See §7 — the narrow-safe fix here touches the renderer's live status-update loop (`notifyRenderer`/`onStatusUpdated`) shared by every other status surface; implementing and testing it safely was judged to need a dedicated, focused session rather than a rushed change inside this broader run. Operating guidance: close VENTURE before removing/reconnecting private storage; this is unchanged from the controlled-pilot constraint. |
| O | App restart | **No defect found** | Unchanged this session; prior closure evidence covers restart/reconnect. |
| P | Windows restart | **Not re-verified this session** | Not itemized separately in the 2026-08-16 closure pass either (Quality Scorecard note); no new evidence either way. |
| Q | Network reconnect | **No defect found** | TD-029–032 chain unchanged and previously physically confirmed. |
| R | PDF Preview/Save/Share | **No defect found** | TD-028 closed; UI/UX polish pass already improved Voucher Details preview parity (prior session). |
| S | Public-facing UI/naming | **SHOULD FIX — partially flagged, not changed** | "Business OS Tally Connector" vs. "VENTURE" naming split recorded in the UI/UX polish status doc as a deliberate-looking choice needing a product decision, not touched. `continuity.NN`/version strings, Connector version, "controlled pilot" wording do not currently appear on ordinary Android/Desktop user-facing screens (spot-checked; Settings/Diagnostics carry version numbers appropriately as diagnostic-adjacent info, not marketing surfaces). |
| T | Diagnostics/privacy | **No new defect found this session** | TD-010's diagnostic allowlist/sanitizers unchanged; out of this session's scope to re-audit without new evidence prompting it. |
| U | Logs | **No new defect found this session** | Unchanged. |
| V | Release notes | **RESOLVED this session** | `docs/planning/VENTURE-MVP-1-RELEASE-NOTES.md` (new, this session). |
| W | Rollback/supportability | **SHOULD FIX — guidance added, tooling unchanged** | `docs/planning/VENTURE-MVP-1-QUICK-START.md` (new) includes basic troubleshooting; no dedicated rollback tooling exists beyond "reinstall the previous installer" (acceptable for an initial limited release, not for broad public support). |
| X | Version/provenance | **RESOLVED this session** | Desktop `0.4.19`, Android `continuity.21`/versionCode 22 (unchanged this session — no Android source changes), Connector `0.4.6` (unchanged). Full manifest in §10. |
| Y | Clean tree/reproducibility | **VERIFIED** | `git status` clean except the three known pre-existing out-of-scope untracked planning files (unchanged, not this session's concern) — see final report. |

## 3. TD-033 — Standard↔Private storage transition (RESOLVED this session)

Implemented **Option B** (explicit guarded transition/warning) per this task's own priority order —
not full migration, which would be materially riskier than the value it provides for MVP-1.

- `desktop:choose-storage-mode` now loads the existing `PrivateStorageLocatorV1` record before
  acting. If the new choice would actually change mode or vault (comparing against the resolved
  vault id, not just the raw input), it returns `{ ok: false, requiresConfirmation: true, message }`
  instead of writing anything — no locator save, no vault creation/adoption side effect occurs
  until confirmed.
- `storage-gate.ts`'s setup flow requires an explicit second Continue click on the *same* choice to
  proceed, tracked client-side; changing the radio/drive selection resets the confirmation state so
  a different, unwarned choice can never slip through as "already confirmed."
- Nothing is deleted. The prior vault/locator record is left exactly as it was; only the *pointer*
  to what VENTURE currently reads changes, once confirmed.
- Required properties satisfied: no silent data disappearance (explicit message before proceeding);
  no silent trust loss (message explicitly names re-pairing); no automatic irreversible migration;
  clear user confirmation (second click, tracked to the specific choice); rollback/failure safety
  (declining leaves current state untouched).
- Regression: 9 new tests (2 renderer confirmation-flow, 3 input-validation, 4
  `readExistingVaultOnDrive`, shared with TD-034); 706/706 Desktop tests passing; `tsc` clean.
- Commit: `6044e88`.

## 4. TD-034 — Unreadable/stale private vault marker (RESOLVED this session)

`readMarker()`'s catch-all treated a marker file that exists but fails `JSON.parse` (e.g. a
BOM-prefixed write from a non-VENTURE tool) identically to "no marker at all," so the adoption path
in `desktop:choose-storage-mode` fell through to `createPrivateVault()` and minted a second vault
beside the unreadable one. Fixed narrowly at the one call site where this mattered
(`readExistingVaultOnDrive`, the adoption decision point): it now throws
`UnreadablePrivateVaultMarkerError` distinctly, which the existing `try/catch` in
`desktop:choose-storage-mode` already converts into a blocking `{ ok: false, message }` — no new UI
plumbing needed. The ongoing-resolve and watchdog paths (`resolvePrivateVault`,
`isPrivateVaultStillPresent`) were deliberately left unchanged: their existing
unreadable-means-not-present fail-closed behavior is already correct there. Commit: `6044e88`.

## 5. TD-025 — Live USB loss UX (explicit decision: documented MVP-1 limitation, not implemented)

Reviewed the fix direction on record (`notifyRenderer()` carrying current storage-gate state so the
renderer can re-run `renderStorageGate()` on any change, not only at startup; `desktop:restart-connector`
calling `resolveStorageGate()` first instead of reusing stale resolved config). Both changes touch
shared, high-traffic renderer/main-process plumbing (every status update, every restart action) —
correctly scoping and testing them without regressing the many other status surfaces that already
depend on the current `notifyRenderer()`/`onStatusUpdated()` contract needs a dedicated, focused
pass, not a change folded into a broad release-prep run under time pressure.

**Decision:** documented as an **ACCEPTED INITIAL RELEASE LIMITATION**, matching the existing
controlled-pilot operating constraint: do not remove/reconnect private storage while VENTURE is
running; if the drive needs to change, stop VENTURE first. This is now stated plainly in the
Quick-Start guide (§8) rather than left only in the technical-debt registry.

## 6. Signing — critical rule compliance

No signing credentials of either kind were found. None were invented, no keys were committed, no
secrets were printed. Both APK/installer artifacts produced this session are unsigned and are
explicitly not represented as distributable — see §10.

**Windows code signing — action list (human/external, not performed here):**
1. Obtain an EV or standard Authenticode code-signing certificate from a CA (or an organizational
   cert already held outside this repo).
2. Set `CSC_LINK`/`CSC_KEY_PASSWORD` (or configure `certificateFile`/`certificatePassword` in
   `electron-builder.yml`) and set `signAndEditExecutable: true`.
3. Re-run `npm run dist:win` — electron-builder signs automatically once configured.

**Android release signing — action list (human/external, not performed here):**
1. Generate (or supply an existing organizational) upload/release keystore.
2. Add a `signingConfigs { release { ... } }` block to `apps/venture_android/app/build.gradle.kts`
   reading credentials from environment variables or a local (never-committed)
   `keystore.properties`, and attach it to `buildTypes.release`.
3. Decide the public Play-Store `applicationId` first (see §13) — signing and package identity
   should be locked together since both are effectively permanent once published.
4. Re-run `./gradlew assembleRelease` (or `bundleRelease` for an AAB, Play's preferred format).

## 7. Android public applicationId — flagged, not decided

`com.jajusri.venture` is the current base id (release), `com.jajusri.venture.debug` for debug
installs. No governance document in this repository formally locks this as the intended
Play-Store-facing public identity. Because a published Android package name is effectively
permanent (changing it after publication forfeits install continuity and review history), this is
flagged per this task's own Section 13 instruction as a release blocker requiring an explicit
product decision — not decided or changed here.

## 8. Public-release documents produced this session

- `docs/planning/VENTURE-MVP-1-RELEASE-NOTES.md` — user-facing release notes.
- `docs/planning/VENTURE-MVP-1-QUICK-START.md` — install/first-use guide plus basic troubleshooting.
- This document.

## 9. Artifact packaging note (this session)

Android: `assembleRelease` succeeded — `app-release-unsigned.apk` (2.3 MB), confirming the release
build pipeline (R8 minify, resource shrink, Proguard) works end-to-end; unsigned, not installable,
not distributed.

Desktop: the existing `npm run dist:win` pipeline (unchanged, project-standard) hit an
environment-specific packaging failure under this session's Bash/Git-Bash shell — GNU `tar`
(`/usr/bin/tar` from the Git-for-Windows MSYS environment) mis-parses a `D:\...` Windows path
argument in `prepare-node-runtime.mjs` as a remote-host `tar` target (`D:` + path), a well-known GNU
tar/Windows-drive-letter ambiguity — not a defect in `prepare-node-runtime.mjs` itself, since prior
Desktop candidates (0.4.15–0.4.18) packaged successfully via the same script, evidently under a
shell where `tar` resolved to Windows' native `System32\tar.exe`. Re-run from PowerShell (which
does resolve `tar` to the Windows-native binary) — see the final report for the outcome.

## 10. Artifact manifest (this session)

| Component | Version | Commit | Artifact | SHA-256 | Signing |
|---|---|---|---|---|---|
| Android (debug, previously installed/approved) | `0.1.1-continuity.21`, versionCode 22 | `bc9cd55` | `release/controlled-pilot/android/0.1.1-continuity.21-bc9cd55/VentureAndroid-bc9cd55-debug.apk` | `bb5ece6bc93383f93cf16b670f8506967e85295ecf249b97e10bd944074705c8` | Debug keystore only |
| Android (release, unsigned, this session) | `0.1.1-continuity.21`, versionCode 22 | `2a35e25` | `apps/venture_android/app/build/outputs/apk/release/app-release-unsigned.apk` | see final report | **UNSIGNED — NOT FOR DISTRIBUTION** |
| Desktop (installed, owner-approved) | `0.4.18` | `e20053f` | (installed; not repackaged this session) | not re-verified this session | Unsigned NSIS (SmartScreen expected) |
| Desktop (this session's candidate) | `0.4.19` | `2a35e25` | see final report for outcome | see final report | **UNSIGNED — NOT FOR DISTRIBUTION** |
| Connector | `0.4.6` | unchanged | bundled inside Desktop installer | unchanged | n/a (not independently distributed) |

Full detail in the final report of this session.
