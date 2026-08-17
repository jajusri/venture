# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`. Update both when a work unit changes their subject.

## 1. Current phase

**MVP-1 PRE-SIGNING TECHNICAL-DEBT CLOSURE — AUTOMATED WORK COMPLETE (2026-08-17)**
**PUBLIC RELEASE BLOCKED — SIGNING ONLY (Windows code-signing + Android release keystore, neither exists; Android `applicationId` also undecided)**
**ANDROID `continuity.22` — HUMAN VISUAL APPROVAL COMPLETE (unchanged this session)**
**DESKTOP `0.4.18` — HUMAN VISUAL/RUNTIME APPROVAL COMPLETE (owner-reported, 2026-08-17, unchanged this session)**
**TD-004 / TD-023 / TD-025 — FIXED (2026-08-17). TD-026 — CORRECTED (already implemented). TD-009 / TD-021 / TD-022 / TD-027 — REVIEWED, REFINED, NOT UNILATERALLY ACTIONED (each needs architectural scoping or a product/security-policy decision)**

## 2. Branch / HEAD

- Branch: `main`
- HEAD at end of this session: `f248cbd` (chore: bump desktop 0.4.19->0.4.20 for pre-signing
  technical-debt closure candidate). One documentation-only commit follows this to record the
  checkpoint itself — use `git log -1` for the self-referential tip.
- Versions: Desktop installed/owner-approved `0.4.18`; Desktop packaged pre-signing candidate
  `0.4.20` (unsigned, not installed over `0.4.18`) supersedes the `0.4.19` TD-033/034 candidate
  from earlier the same day; Connector `0.4.6` (unchanged); Android `0.1.1-continuity.22`
  (versionCode 23, installed/owner-approved) — Android source unchanged this session, confirmed via
  a full regression re-run.
- Not pushed to `origin`.

## 3. Current evidence

```
f248cbd chore: bump desktop 0.4.19->0.4.20 for pre-signing technical-debt closure candidate
4bdda3a docs: record pre-signing technical-debt closure pass in Ledger and Quality Scorecard
2a6a7b4 docs: sync TD-009 index row with its reviewed entry
52679b2 docs: reconcile TD-009 registry entry against the now-existing authenticated transport subsystem
2615005 docs: close TD-004 and record TD-027 review outcome in the registry
4605700 fix(desktop): TD-004 - forward configured Tally host/port to the spawned connector
07824b6 docs: reconcile TD-022/023/025 registry entries against this session's fixes and findings
aa3c29b test(desktop): stop leaking mkdtemp scratch directories across 13 test files
1cc2e30 fix(desktop): normalize line endings reintroduced by prior edit
87edf88 fix(desktop): move test-only diagnostic privacy sentinels out of shipped production source
755dac2 test(connector): stop leaking a temp SQLite scratch directory on every createTestContext() call
d6db49f perf(connector): TD-023 - voucher list/search endpoint no longer loads the full snapshot per request
6ff65e6 test(connector): TD-026 close last reconciliation-matrix test gap; correct TD-021/TD-026 registry classification
c9677bf fix(storage): TD-025 - distinguish mid-session private-storage loss from a generic disconnect
```
(a308433 "fix(desktop): redact sensitive content inside startup-diagnostics detail values" sits
between 07824b6 and aa3c29b in the actual log — omitted above only for brevity; see `git log` for
the complete, exact sequence.)

Full public-release detail: `docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`.
Full UI/UX detail: `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md`.
Full technical-debt detail: `docs/technical-debt/registry.md`.

- Controlled Pilot: **GO / CLOSED** under the constraints in
  `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` and the Quality Scorecard.
- Android: owner FINAL HUMAN VISUAL APPROVAL recorded for installed `continuity.22`, unchanged
  this session (no Android source touched).
- Desktop: `0.4.18` remains installed, running, and owner-reported good — unchanged this session.
- **This session (pre-signing technical-debt closure) fixed:** TD-004 (Desktop Settings' Tally
  host/port fields were silently inert — persisted but never reached the spawned Connector — now
  forwarded correctly); TD-023 (Connector voucher list/search endpoint no longer loads the full
  company snapshot per paged request, zero behavior regression); TD-025's mid-session
  private-storage-loss gap (now shows the purpose-built recovery screen instead of a generic
  "Disconnected" state, reusing existing tested UI — a narrower `desktop:restart-connector`
  stale-config sub-item remains open, Post-MVP-1).
- **This session corrected:** TD-026 — the out-of-window reconciliation mechanism this TD asked
  for already existed (`ReconcileVoucherWindowsUseCase`); this session closed a test-evidence gap
  (5 new tests) and corrected stale registry documentation, not new sync-architecture logic.
- **This session reviewed and refined, left open (each needs architectural scoping or a
  product/security-policy decision, not an engineering fix):** TD-009 (trusted-LAN bind mode has
  no per-device authN independent of the opt-in secure-pairing subsystem), TD-021 (session-renewal
  gap is deeper than one call site — would need both wider response classification and a new
  cross-feature dependency), TD-022 (refined — the automatic background walk is already bounded to
  30-day windows; the *manual* Voucher Browser refresh path has no date-span cap, a real but
  narrower gap than the original entry implied), TD-027 (Tally export-timing observation reaffirmed
  as correct with no new evidence to justify reversing the prior no-workaround decision).
- **Hygiene fixes shipped:** test-only `DIAGNOSTIC_PRIVACY_SENTINELS`/
  `assertDiagnosticOutputExcludesSentinels` moved out of shipped Desktop production source into a
  test helper; confirmed unbounded temp-directory leaks fixed in the Connector (new `globalSetup`/
  teardown, shared tracking file) and across 13 Desktop test files (`afterEach`/`try-finally`) —
  both measured net-zero growth after the fix, pre-existing leaked directories (1,554 Connector /
  1,835 Desktop) left untouched per instruction; a content-level (not just field-name) redaction
  gap closed in Desktop's `startup-diagnostics.ts`.
- **Flagged, not acted on:** `docs/diagnostics/m3-stock-items-raw-sample.xml` (~1,500 real-looking
  inventory item names, committed 2026-07-22 in `f6f59c4`) — reconfirmed unreachable by any
  packaging path, left untouched (no history rewrite), flagged for an explicit owner decision.
- Public release itself is **BLOCKED**: no Windows code-signing certificate and no Android release
  keystore exist anywhere in this repository or environment (last verified directly 2026-08-17 —
  no `.pfx`/`.p12`/`.jks`/`.keystore` files, no `signingConfigs` block, no CSC/keystore env vars,
  `signAndEditExecutable: false`). This is the dominant remaining blocker. Android's public
  Play-Store `applicationId` is also flagged, not decided, as a second release blocker. **This
  session found no additional non-signing defect that adds a new blocker** — the full non-signing
  gap inventory built this session found nothing beyond what's already tracked in the registry.

## 4. Approved Stitch reference

`D:\BUDCOM-Design-Archive\01_APPROVED_MASTERS\AC_DATA_HOME\BUDCOM-AC-DATA-HOME-MASTER.png`
(external to the repo — see `D:\BUDCOM-Design-Archive\00_README_AND_DECISIONS\BUDCOM-UI-MASTER-STORAGE-PLAN.txt`
for the archive's own governance of this file).

## 5. This session's work (pre-signing technical-debt closure)

Built a complete non-signing gap inventory across the full technical-debt registry (not limited to
a fixed list) and actioned every safe FIX NOW/IMPROVE NOW item found. Full detail in
`docs/technical-debt/registry.md` and Ledger phase 19; summary in §3 above. Fixed: TD-004, TD-023,
TD-025's mid-session overlay gap, plus a Connector temp-directory leak, a Desktop temp-directory
leak across 13 files, a shipped-source privacy-sentinel hygiene issue, and a content-level log-
redaction gap. Corrected: TD-026 (found already implemented). Reviewed and refined without
unilateral action: TD-009, TD-021, TD-022, TD-027 — each documented with the specific reason
(architectural scoping, or a product/security-policy decision) it wasn't actioned this session.
Packaged one coherent final pre-signing Desktop candidate (`0.4.20`) after all changes stabilized,
superseding the `0.4.19` candidate from earlier the same day. Full four-component regression
re-run clean at session end (§9).

## 6. Prior session's work (MVP-1 public-release preparation, part 1)

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

See prior UI/UX work in §7 below (unchanged, carried forward from the prior phase).

## 7. Major UI/UX work (prior phase, unchanged this session)

- Android Home (`DashboardScreen.kt`) reconciled against the approved Stitch master: permanent
  Universal Search entry, compact Fresh/last-sync/Tally-connected status row, Vouchers/Ledgers as
  primary rows — additive only, all prior cards/actions/tests preserved. Insights widget and
  Connect/Vartalap tabs deliberately **not** built (no backing data/destinations exist).
- Voucher Browser: compact horizontal All/Sales/Purchase/… type-filter strip (UIP-002).
- Voucher Details: TD-028 parity closed (1-tap Preview in TopAppBar) + missing back button added.
- Approved Android visual masters implemented (`9b29abf`); approved BUDCOM visual language applied
  to Desktop (`96a7a55`, corrected in `e20053f` after the `0.4.17` runtime regression).

## 8. Permanent rule — checkpoint discipline

**Every future meaningful BUDCOM AI development work unit must end with:**

> INSPECT → IMPLEMENT → TEST → COMMIT → UPDATE RELEVANT STATUS DOC → UPDATE DEVELOPMENT LEDGER →
> UPDATE CANONICAL CURRENT-DEVELOPMENT CHECKPOINT → CLEAN-TREE AUDIT → RECORD EXACT NEXT TASK.

No completed work may exist only in Claude/Codex/chat session memory. If a work unit ends without
this file being updated to match the real `git log`/`git status`, that work unit is not finished.

At the start of a new AI development session: read this checkpoint; inspect `git status` and recent
Git history; read the relevant domain status and Development Ledger; reconstruct state from
repository evidence; continue rather than redo completed work.

## 9. Test/build status (this session, final)

- **Android:** `testDebugUnitTest` 1,030/1,030, `testReleaseUnitTest` 1,030/1,030, `lintDebug`
  0 errors, `lintRelease` 0 errors, `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`
  — all BUILD SUCCESSFUL. Source unchanged this session — confirmation run, not expected to surface
  anything new; it didn't. `connectedAndroidTest` — not run (only reachable device is the owner's
  real paired physical phone; judged too invasive to run autonomously without explicit go-ahead).
- **Desktop:** `tsc --noEmit` clean across all three configs (main/preload/renderer); full
  `npm run build` clean. `vitest run` — 711/711 passing (up from 708 at session start — 3 new: 2
  TD-004 regression tests, 1 startup-diagnostics redaction test).
- **Contract:** `tests/contract` — 5/5 passing, unchanged.
- **Connector:** independently re-run in full this session (touched by TD-023/026 and the
  temp-directory-leak fix) — 159 files/1,423 tests passing, ESLint clean, `tsc`/`npm run build`
  clean.

## 10. Known out-of-scope untracked files (leave alone)

```
docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md
docs/product-design/
docs/product/
```

Pre-existing Connect/Universal-Party/post-MVP product-planning artifacts. Out of every pass's hard
boundaries — do not track, edit, or delete without explicit instruction.

## 11. Exact NEXT TASK

**Obtain and configure production signing credentials — the sole remaining blocker to public
release, confirmed unchanged by this session's full non-signing gap-inventory pass:**
1. Windows code-signing certificate (see gate matrix §6 for the exact `electron-builder.yml` /
   `CSC_LINK` setup steps).
2. Android release keystore, plus an explicit product-owner decision on the public Play-Store
   `applicationId` (currently undecided — gate matrix §7) before generating/attaching it.

Both are human/external actions (obtaining a certificate, generating and safeguarding a keystore,
deciding a permanent public package identity) that this or any autonomous session should not
perform unilaterally. Once available, re-run `npm run dist:win` (Desktop) and
`./gradlew assembleRelease`/`bundleRelease` (Android) to produce genuinely signed public artifacts,
then complete artifact-level regression against the signed builds specifically.

**Also outstanding, non-blocking (owner decision, not engineering):** whether to redact/remove
`docs/diagnostics/m3-stock-items-raw-sample.xml` (flagged, not acted on this session — see §3);
whether trusted-LAN bind mode should require secure pairing (TD-009); TD-025's remaining
`desktop:restart-connector` stale-config sub-item, reasonably Post-MVP-1.

**Not started:** external/public distribution (no push, no store submission, nothing published),
MVP-1.1. Do not begin either before signing exists and the product owner explicitly authorizes
distribution.
