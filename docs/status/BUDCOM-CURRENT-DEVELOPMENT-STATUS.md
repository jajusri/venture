# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`. Update both when a work unit changes their subject.

## 1. Current phase

**MVP-1 PUBLIC RELEASE PREPARATION — AUTOMATED WORK COMPLETE**
**PUBLIC RELEASE BLOCKED — SIGNING (Windows code-signing + Android release keystore, neither exists)**
**ANDROID `continuity.22` — HUMAN VISUAL APPROVAL COMPLETE**
**DESKTOP `0.4.18` — HUMAN VISUAL/RUNTIME APPROVAL COMPLETE (owner-reported, 2026-08-17)**
**TD-033 / TD-034 — RESOLVED (2026-08-17)**

## 2. Branch / HEAD

- Branch: `main`
- HEAD at end of this session: `2a35e25` (chore: bump desktop 0.4.18->0.4.19 for TD-033/TD-034
  release-prep candidate). One documentation-only commit follows this to record the checkpoint
  itself — use `git log -1` for the self-referential tip.
- Versions: Desktop installed/owner-approved `0.4.18`; Desktop packaged release-prep candidate
  `0.4.19` (unsigned, not installed over `0.4.18`); Connector `0.4.6` (unchanged); Android
  `0.1.1-continuity.22` (versionCode 23, installed/owner-approved) — Android source unchanged this
  session; an unsigned `0.1.1-continuity.21`/versionCode 22 `assembleRelease` artifact was also
  produced this session purely to verify the release pipeline works.
- Not pushed to `origin`.

## 3. Current evidence

```
2a35e25 chore: bump desktop 0.4.18->0.4.19 for TD-033/TD-034 release-prep candidate
6044e88 fix(desktop): TD-033 guarded Standard<->Private storage switch, TD-034 fail-closed on unreadable vault marker
986ad93 docs: record Desktop 0.4.17 runtime failure and correction
e20053f fix(desktop): restore packaged renderer interactivity
96a7a55 feat(desktop): apply approved BUDCOM visual language
9b29abf feat(android): implement approved MVP-1 visual masters
bc9cd55 chore: bump desktop 0.4.15->0.4.16, android continuity.20->continuity.21 for UI/UX polish candidate packaging
```

Full public-release detail: `docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`.
Full UI/UX detail: `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md`.

- Controlled Pilot: **GO / CLOSED** under the constraints in
  `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` and the Quality Scorecard.
- Android: owner FINAL HUMAN VISUAL APPROVAL recorded for installed `continuity.22` on 2026-08-17.
- Desktop: `0.4.17` failed installed-runtime interaction (packaged ESM/CommonJS module-boundary
  bug); `0.4.18` corrected it and is installed, running, and **owner-reported good** on 2026-08-17
  (this session) — Desktop visual/runtime approval is now recorded, not pending.
- TD-033 (silent Standard↔Private storage-mode switch) and TD-034 (unreadable vault marker
  silently treated as absent) — both **RESOLVED** this session, narrow guarded fixes, 9 new tests,
  706/706 Desktop tests passing. See gate matrix §3–4 for full detail.
- Public release itself is **BLOCKED**: no Windows code-signing certificate and no Android release
  keystore exist anywhere in this repository or environment (verified directly this session — no
  `.pfx`/`.p12`/`.jks`/`.keystore` files, no `signingConfigs` block, no CSC/keystore env vars,
  `signAndEditExecutable: false`). This is the dominant remaining blocker. Android's public
  Play-Store `applicationId` is also flagged, not decided, as a second release blocker.

## 4. Approved Stitch reference

`D:\BUDCOM-Design-Archive\01_APPROVED_MASTERS\AC_DATA_HOME\BUDCOM-AC-DATA-HOME-MASTER.png`
(external to the repo — see `D:\BUDCOM-Design-Archive\00_README_AND_DECISIONS\BUDCOM-UI-MASTER-STORAGE-PLAN.txt`
for the archive's own governance of this file).

## 5. This session's work (MVP-1 public-release preparation, part 1)

- Produced `docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md` — the canonical gate-by-gate
  public-release readiness view (26 gates evaluated).
- **TD-033 resolved:** `desktop:choose-storage-mode` now requires an explicit second confirmation
  before switching an already-configured installation's storage mode — no silent data/trust loss.
  Nothing is deleted; this is a warn-and-confirm gate, not a migration.
- **TD-034 resolved:** the storage-mode adoption path now fails closed and distinctly when a vault
  marker file exists but can't be parsed, instead of silently minting a second vault beside it.
- **TD-025 explicitly deferred** as an accepted MVP-1 limitation (documented in the Quick-Start
  guide) rather than rushed — the narrow-safe fix touches shared renderer status-update plumbing
  used by every other status surface and needs its own focused pass.
- Authored `docs/planning/BUDCOM-MVP-1-RELEASE-NOTES.md` and
  `docs/planning/BUDCOM-MVP-1-QUICK-START.md` (user-facing, plain-language).
- Added a "Public-release readiness view" section to `docs/governance/BUDCOM-QUALITY-SCORECARD.md`
  (verdict: BLOCKED — signing).
- Verified, directly, that no signing credentials of either kind exist in this environment.
- Packaged unsigned release-prep candidates for both platforms, clearly labeled
  `release/public-candidate/UNSIGNED-NOT-FOR-DISTRIBUTION.md`: Desktop `0.4.19`
  (`release/controlled-pilot/0.4.19/artifacts/BudcomDesktop-0.4.19-x64-setup.exe`, SHA-256
  `0128643918726b880dbfe2e051aaa5cd98a9490fa632dd978085cb68aa84ebc6`) and Android unsigned release
  (`apps/budcom_android/app/build/outputs/apk/release/app-release-unsigned.apk`, SHA-256
  `95db60376d562b73d95cc83cf76bd5f20b47a19d0db54e20c9d25cac4aa0b440`). Neither was installed over
  the currently-approved running builds.
- Diagnosed (not modified) an environment-specific packaging blocker: Git-Bash's `/usr/bin/tar`
  (GNU tar) mis-parses `D:\...` Windows paths as a remote-host target inside
  `prepare-node-runtime.mjs`; re-running the same unchanged script via PowerShell (where `tar`
  resolves to the Windows-native binary) succeeded. No script changes were made.
- Re-verified full regression: Android debug+release JVM unit tests, both lints, both assembles,
  `assembleDebugAndroidTest`, and `tests/contract` (5/5) all green. Desktop `tsc` (3 configs) and
  `vitest` (706/706) green.

See prior UI/UX work in §6 below (unchanged, carried forward from the prior phase).

## 6. Major UI/UX work (prior phase, unchanged this session)

- Android Home (`DashboardScreen.kt`) reconciled against the approved Stitch master: permanent
  Universal Search entry, compact Fresh/last-sync/Tally-connected status row, Vouchers/Ledgers as
  primary rows — additive only, all prior cards/actions/tests preserved. Insights widget and
  Connect/Vartalap tabs deliberately **not** built (no backing data/destinations exist).
- Voucher Browser: compact horizontal All/Sales/Purchase/… type-filter strip (UIP-002).
- Voucher Details: TD-028 parity closed (1-tap Preview in TopAppBar) + missing back button added.
- Approved Android visual masters implemented (`9b29abf`); approved BUDCOM visual language applied
  to Desktop (`96a7a55`, corrected in `e20053f` after the `0.4.17` runtime regression).

## 7. Permanent rule — checkpoint discipline

**Every future meaningful BUDCOM AI development work unit must end with:**

> INSPECT → IMPLEMENT → TEST → COMMIT → UPDATE RELEVANT STATUS DOC → UPDATE DEVELOPMENT LEDGER →
> UPDATE CANONICAL CURRENT-DEVELOPMENT CHECKPOINT → CLEAN-TREE AUDIT → RECORD EXACT NEXT TASK.

No completed work may exist only in Claude/Codex/chat session memory. If a work unit ends without
this file being updated to match the real `git log`/`git status`, that work unit is not finished.

At the start of a new AI development session: read this checkpoint; inspect `git status` and recent
Git history; read the relevant domain status and Development Ledger; reconstruct state from
repository evidence; continue rather than redo completed work.

## 8. Test/build status (this session, final)

- **Android:** `testDebugUnitTest`, `testReleaseUnitTest`, `lintDebug`, `lintRelease`,
  `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest` — all green.
  `connectedAndroidTest` — not run (only reachable device is the owner's real paired physical
  phone; judged too invasive to run autonomously without explicit go-ahead).
- **Desktop:** `tsc --noEmit` clean across renderer/main/preload. `vitest run` — 706/706 passing
  (up from 697 — 9 new TD-033/034 regression tests).
- **Contract:** `tests/contract` — 5/5 passing.
- **Connector:** not independently re-run in full (unchanged this session); its `tsc` build
  succeeded as part of the Desktop packaging pipeline, which is sufficient compatibility evidence
  for an unchanged component.

## 9. Known out-of-scope untracked files (leave alone)

```
docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md
docs/product-design/
docs/product/
```

Pre-existing Connect/Universal-Party/post-MVP product-planning artifacts. Out of every pass's hard
boundaries — do not track, edit, or delete without explicit instruction.

## 10. Exact NEXT TASK

**Obtain and configure production signing credentials — the sole remaining blocker to public
release:**
1. Windows code-signing certificate (see gate matrix §6 for the exact `electron-builder.yml` /
   `CSC_LINK` setup steps).
2. Android release keystore, plus an explicit product-owner decision on the public Play-Store
   `applicationId` (currently undecided — gate matrix §7) before generating/attaching it.

Both are human/external actions (obtaining a certificate, generating and safeguarding a keystore,
deciding a permanent public package identity) that this or any autonomous session should not
perform unilaterally. Once available, re-run `npm run dist:win` (Desktop) and
`./gradlew assembleRelease`/`bundleRelease` (Android) to produce genuinely signed public artifacts,
then complete artifact-level regression against the signed builds specifically.

**Not started:** external/public distribution (no push, no store submission, nothing published),
MVP-1.1. Do not begin either before signing exists and the product owner explicitly authorizes
distribution.
