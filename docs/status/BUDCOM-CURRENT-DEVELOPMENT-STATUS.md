# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1 STATUS: COMPLETE / FROZEN (2026-08-17).**
**MVP-1.2 STATUS: COMPLETE / FROZEN (2026-08-18).** Parts A, B, C, D, and E (integrated hardening +
freeze) are all complete. Product decisions locked as `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md`
PDL-014–PDL-018. Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md`
Parts A–E; architecture: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
**MVP-1.3 STATUS: IN PROGRESS — Part A (Business Identity Foundation) COMPLETE, acceptance gate
PASSED (2026-08-18).** The five product decisions (per-company scope, initial field set, no
provenance/no Tally round-trip, app-private logo storage behind an abstraction, owner-side only)
were supplied directly in this session's governing prompt and recorded as
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` **PDL-019**. Part A shipped the `business_profile`
table (`MIGRATION_9_10`), full repository/domain/logo-storage layers, and a basic whole-entity
owner-side editor wired into the Dashboard. Continuing automatically to Part B (Profile
Presentation & Sharing Foundation) per this task's own instruction. Full detail:
`docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Part A;
architecture: `docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md` (its §3/§5 open
questions marked `RESOLVED — PDL-019`).
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1/1.2/1.3 work)
**ANDROID `0.1.1-continuity.27` (versionCode 28)** — unchanged this session; the single coherent
MVP-1.3 version bump is deferred to Part C's freeze, per this task's own instruction and this
codebase's established precedent. **Installed** on device `I2407i` (`10BF44124K000E3`) since the
MVP-1.2-E session; not reinstalled this session (Part A produced no new release candidate).
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched, and not required by MVP-1.3-A.

## 2. Branch / HEAD

- Branch: `main`.
- Starting HEAD of the MVP-1.2-D session: `d964b760e90339d3037a3447bbffa85e116e4801`.
- HEAD after MVP-1.2-D: `57a27e44f813aede9e5b71dd5a43440380ed670c` (4 commits: data/domain/
  repository, presentation/navigation/Dashboard, tests, documentation).
- Starting HEAD of the MVP-1.2-E session: `57a27e44f813aede9e5b71dd5a43440380ed670c`, working tree
  clean — confirmed by direct inspection (`git status`, `git log`, `DatabaseConstants.VERSION`,
  presence of all 8 `feature/dincharya/` files), not trusted from documentation alone.
- HEAD after MVP-1.2-E (before this push-confirmation commit): `d9af6f7623f66d8d028d3ee61d2d3fa79a91ceb9`
  — three commits: `7d6a931` (`fix(android)`: note-completion UI wiring), `ffb9d99`
  (`test(android)`: new tests + version bump), `d9af6f7` (`docs`: this checkpoint + ledger phase 32
  + specialist status Part E).
- Android: version bumped `0.1.1-continuity.26`→**`0.1.1-continuity.27`** (versionCode 27→28) — the
  single coherent MVP-1.2 freeze bump, performed only after full A–D+E regression was green (per
  explicit instruction, not before).

### 2a. Push status (MVP-1.2-D/E)

**PUSHED — confirmed byte-identical.** `git push origin main` completed as a normal fast-forward,
no force flag, no history rewrite: `d964b76..7732844 main -> main` (the final push included one
small follow-up documentation commit, `7732844`, recording this exact confirmation). Post-push
verification via `git fetch origin` + `git rev-parse`: local HEAD and `origin/main` both resolved to
`773284485e03028508e89b8f84ebeda54a1dac3b` — exact match, working tree clean. This preserved the
entire MVP-1.2-D + MVP-1.2-E work (8 commits total) on the remote.

### 2b. MVP-1.3 planning/recovery review session

- Starting HEAD: `773284485e03028508e89b8f84ebeda54a1dac3b` — confirmed to exactly match this task's
  own expected baseline; working tree clean; zero untracked files; local HEAD byte-identical to
  `origin/main` before any action was taken.
- This is an explicitly **read-only reconnaissance session** — no production code, migration, test,
  APK, or version bump was touched. The only repository changes are three documentation files: the
  new `docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`, a correction to
  `docs/design/BUDCOM-SCREEN-INVENTORY.md` (five stale-missing screens added), and this file plus
  the Development Ledger (phase 33).
- **Not pushed.** This task did not explicitly authorize a push for this planning session (unlike
  MVP-1.2-E, which did) — the documentation commit(s) remain local-only, ahead of `origin/main`,
  until explicitly authorized. See the session's own final report for the exact resulting HEAD and
  commit hash.

## 3. Recent session work (MVP-1.2-B through E)

**Part B — Relationship Timeline, implemented and gate-passed.** New `PartyTimelineDao` merges
`party_notes` and `party_export_events` into one chronological, bounded/paged feed via a single SQL
`UNION ALL`. Zero schema change, zero migration. Per the locked PDL-014, this **replaces** (not
duplicates) the flat Notes list's presentation on Party Detail. 23 new tests (7 JVM + 16
instrumented). Full regression clean at the time: `testDebugUnitTest`/`testReleaseUnitTest`
1,186/1,186 both; instrumented suite 256/268, the unchanged 12-failure baseline.

**Part C — Issue History, implemented and complete.** Extended B's `PartyTimelineDao` `UNION ALL`
with `issue_opened`/`issue_resolved` arms — the direct mechanism for "Issue/Timeline Consistency."
Added a Party Detail Issues section with Resolve/Reopen actions and issue-filtered Timeline. 29 new
tests (15 JVM + 14 instrumented). `testDebugUnitTest`/`testReleaseUnitTest` 1,201/1,201 both;
instrumented suite 274/286, the unchanged 12-failure baseline.

Full detail (B–C): `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts B–C.
Prior milestone (MVP-1.1-A through E): `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Parts A–E.

**Checkpoint session — Git preservation + MVP-1.2-D readiness review (2026-08-18).** Re-verified B/C
directly against source, pushed the verified HEAD to `origin/main` via a normal fast-forward, then
performed a **read-only** MVP-1.2-D (Dincharya) architecture/readiness review — locked-scope
reconciliation, per-item-type data-source analysis, company-isolation strategy, performance/index-
impact analysis, and two genuine open product ambiguities flagged rather than silently resolved.
Zero production code, migration, version bump, or install performed. Full detail:
`docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` "Checkpoint" section.

**Part D — MVP-1.2-D Dincharya, implemented and complete (2026-08-18).** The three locked
deterministic item types (PDL-018): Follow-ups/Callbacks, Pending Tally Confirmation, Pending
Contact Completion — each a new bounded, `companyId`-scoped, company-wide DAO query, the first
genuinely cross-party queries in this feature area. New dedicated `feature/dincharya/` repository.
New top-level Dincharya screen reached from a fourth Dashboard entry. 35 new instrumented tests + 18
new JVM tests, with dedicated adversarial company-isolation coverage at the DAO, repository, and
ViewModel layers. `testDebugUnitTest`/`testReleaseUnitTest` 1,219/1,219 both; both lints 0 errors;
all three assembles green; instrumented suite 309/321, the unchanged 12-failure baseline. One
self-caught defect fixed before commit (a refresh-error-swallowing gap). Zero schema change, zero
migration, version not bumped (deferred to E). Full detail:
`docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part D; ledger: phase 31;
product decisions: PDL-018.

**Part E — MVP-1.2-E Integrated Hardening, Freeze, Final Candidate, implemented and complete
(2026-08-18).** Not a new feature — an integrated audit of Parts A–D working together. Found one
genuine, consequential defect: `SetNoteCompletionUseCase` existed at the repository/use-case layer
since 1.2-A but had zero UI caller anywhere in the app through B, C, and D — meaning Dincharya's
Follow-ups/Callbacks group showed real overdue items a user had no honest way to complete. Fixed by
wiring Mark done/Reopen actions into Party Detail's note rows (mirroring the existing Issue
Resolve/Reopen pattern exactly). Self-caught and fixed a second, smaller issue during that same fix:
an initial false-urgency color signal on incomplete-but-not-yet-due notes. Re-verified (not
re-invented) company isolation, migration (schema unchanged, `AppDatabaseMigrationTest` 8/8),
accessibility, offline/failure/state behavior, and performance across Parts A–D together — no other
genuine defect found. 5 new tests (2 JVM + 3 instrumented). `testDebugUnitTest`/`testReleaseUnitTest`
1,221/1,221 both; both lints 0 errors; all three assembles green including R8-minified release;
instrumented suite 312/324, the unchanged 12-failure baseline, zero overlap, completing cleanly on
the first attempt. Version bumped to `0.1.1-continuity.27`/versionCode 28 — the single coherent
MVP-1.2 freeze bump. Built and directly inspected both a debug and an unsigned release-verification
APK (see §5 for exact hashes/sizes). Installed the debug APK on device `10BF44124K000E3` and
smoke-tested it directly (§5) — launch clean, correct honest first-run Secure Pairing state, zero
crash, back-navigation correct, relaunch clean; Dashboard/Connect/Dincharya/Timeline/Issues
explicitly **not** exercised on-device (no real Tally Connector pairing available in this
environment) — verified only through the automated instrumented suite instead, stated honestly as a
weaker form of evidence than genuine on-device navigation. Full detail:
`docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part E; ledger: phase 32.

**MVP-1.3 planning/recovery review, complete (2026-08-18).** Read-only reconnaissance only — no
implementation. Verified the MVP-1.2 freeze baseline directly (matches this task's own expected
values exactly, §2b). Read the full authoritative document set and searched the entire repository
plus the external design archive for any Business-Profile/Catalogue groundwork — found none (zero
code anywhere), but real, locked architecture-sequencing/ownership-shape groundwork does exist
(Master Plan §9, UI Decisions §7). **Central finding:** the milestone's *detailed* scope requires
Brainstorm 1 (User + ChatGPT, per the Modus Operandi's own mandatory development cycle), which has
not happened for this milestone — so this session produced a complete planning package (gap-analysis
matrix, five explicit open product decisions ranked by risk, a candidate not-locked architecture, a
proposed sub-milestone structure) as *input* to that brainstorm, rather than guessing at scope or
beginning implementation. One documentation-staleness defect found and fixed along the way:
`BUDCOM-SCREEN-INVENTORY.md`'s screen table had been stale since before MVP-1.1-B (missing five
shipped screens), corrected directly. Full detail:
`docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`; ledger: phase 33.

**Part A — MVP-1.3-A Business Identity Foundation, implemented and complete (2026-08-18).** The
five product decisions (per-company scope, initial field set, no provenance/no Tally round-trip,
app-private logo storage behind an abstraction, owner-side only) were supplied directly in this
session's governing prompt, recorded as PDL-019. New `business_profile` table
(`MIGRATION_9_10`, one row per `companyId`), full repository/domain layer, and a genuine, fully
tested `BusinessProfileLogoStore` abstraction (file-type allowlist, 5 MB streaming size cap,
two-layer path-traversal defense) even though no picker UI exists yet — deliberate, since
`logoAssetPath` is a real schema column A introduces (full reasoning: specialist doc §A3). Basic
whole-entity owner-side editor wired into a fifth Dashboard entry. One self-caught defect fixed
before any test ran (a Cancel-reverts-to-unsaved-not-saved-data bug). One genuine instrumented-test
discrepancy investigated and resolved at the test level, not dismissed as assumed flakiness (two
new failures traced to Save/Cancel buttons sitting below the fold in an 11-field scrollable form on
this specific device — fixed with `performScrollTo()` in the test, confirmed by isolating and
comparing against an already-passing equivalent screen). 17 new JVM tests + 18 new instrumented
tests, with dedicated adversarial company-isolation coverage at the DAO, repository, and ViewModel
layers. `testDebugUnitTest`/`testReleaseUnitTest` 1,255/1,255 both; both lints 0 errors (report-XML
verified); all three assembles green; instrumented suite 329/341, the unchanged 12-failure
baseline, zero overlap. Zero network/Connector/Desktop/manifest touch (grep-verified). Version not
bumped (deferred to Part C's freeze). Continuing automatically to Part B in this same session, per
this task's own "continue A → B → C automatically when the gate passes" instruction. Full detail:
`docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Part A; product decisions: PDL-019.

## 4. Public release blocker (unrelated to MVP-1.1/1.2, unchanged)

No Windows code-signing certificate and no Android release keystore exist anywhere in this
repository/environment. Android public Play-Store `applicationId` also undecided. Full detail:
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. Not touched by MVP-1.1 or MVP-1.2 work.

## 5. Android install status

**INSTALLED — the MVP-1.2 freeze candidate (2026-08-18).** Device `I2407i` (serial
`10BF44124K000E3`) had **zero BUDCOM package of any kind** before this session's own install step —
a genuine finding first surfaced during MVP-1.2-D: the previously-documented `continuity.26`
installation had disappeared, most likely removed by `connectedDebugAndroidTest`'s own default
post-test uninstall behavior (installs the app-under-test + test APK before running, uninstalls both
afterward, for test hermeticity) rather than any direct `adb install`/`adb uninstall` action. This
was not remediated during the D session (installing anything was outside that session's authorized
scope); it *is* remediated now, as an explicit, authorized part of MVP-1.2-E's own final-candidate
install step.

**Install performed this session:** `adb install -r apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`
→ `Performing Streamed Install / Success`. No prior uninstall, no data clear, no pairing/company-state
wipe (there was none to wipe, per the pre-install check above). Confirmed via `dumpsys package
com.budcom.android.debug`: `versionCode=28`, `versionName=0.1.1-continuity.27`,
`firstInstallTime == lastUpdateTime` (a genuine fresh install, consistent with the empty pre-install
state).

**Smoke test performed directly on device** (full detail: specialist status doc Part E §E16):
launch succeeded (`MainActivity` became `mFocusedApp`); rendered the correct, honest first-run
**Secure Pairing** screen (screenshot-confirmed, matching the established dark theme, no visual
defect); zero `FATAL EXCEPTION`/`AndroidRuntime:` logcat entries; Back navigation correctly returned
to the home launcher (root-activity behavior, not a crash — app process stayed alive); relaunch
succeeded cleanly. **Explicitly not exercised on-device:** Dashboard, Connect entry, Dincharya entry,
Party Detail, Timeline, Issues — this environment has no real paired Tally Connector to complete
Secure Pairing against, and per explicit instruction no attempt was made to fake or bypass pairing.
These surfaces are verified only through the automated instrumented Compose test suite (real
Room/real Compose on this same device, synthetic state) — a distinct and weaker form of evidence
than genuine on-device navigation, stated as such rather than conflated with it.

**Debug APK:** `apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk` — 14,470,132 bytes —
SHA-256 `92a38394e5eef4cee65eae4ba6caaef86b5e430dfccf22973aa1401362fe9d02` — `com.budcom.android.debug`,
versionCode 28, versionName `0.1.1-continuity.27`, debuggable (confirmed via `aapt dump badging` on
the actual built APK, not source config alone).

**Release (unsigned, verification only, never a public-release artifact):**
`apps/budcom_android/app/build/outputs/apk/release/app-release-unsigned.apk` — 2,509,604 bytes —
SHA-256 `ad11110475ef9e887afd642ef28c06f016a4f48fcf30b819456ab7aa1fee4c92` — `com.budcom.android`
(no debug suffix), same version, not debuggable, unsigned (no signing credentials exist anywhere in
this repository — unchanged blocker, §4).

**Structural note carried forward for future sessions:** `connectedDebugAndroidTest` will remove
whatever debug build is on this device every time it runs — a property of this project's chosen
test-gate mechanism on a real persistent-install device, not something this session "fixed" at the
tooling level. Future sessions should expect the same and plan to reinstall as a final step (exactly
as this session did), or consider moving that gate to a disposable emulator/secondary device if a
persistently-installed candidate needs to survive test runs.

## 6. Permanent rules

- **Checkpoint discipline:** INSPECT → IMPLEMENT → TEST → COMMIT → UPDATE RELEVANT STATUS DOC →
  UPDATE DEVELOPMENT LEDGER → UPDATE THIS CHECKPOINT → CLEAN-TREE AUDIT → RECORD EXACT NEXT TASK.
- **Durable Development Record** (full text: `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`
  §13A): no consequential completed work may exist only in chat/AI memory. Specialist doc = detailed
  evidence; Development Ledger = chronological record; this file = concise current-state/handoff.

At the start of a new AI development session: read this checkpoint, the Development Ledger, and
the relevant specialist status doc; inspect `git status`/recent history; reconstruct state from
repository evidence, not session memory.

## 7. Exact NEXT TASK

**MVP-1.1 is complete/frozen (§1). MVP-1.2 is COMPLETE / FROZEN (§1) — Parts A through E all
complete, documented, tested, version-bumped, built, installed, and smoke-tested (§3/§5; full
evidence in `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts A–E).
MVP-1.3 is IN PROGRESS — Part A (Business Identity Foundation) complete, acceptance gate PASSED
(§3; full evidence in `docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Part A). The five
product decisions this milestone needed were supplied directly in this session's governing prompt
and locked as PDL-019 — Brainstorm 1 is no longer outstanding for this milestone.**

**Next task: continue in the same session to MVP-1.3-B (Profile Presentation & Sharing
Foundation)** — logo picker/display UI, full presentation-hierarchy/accessibility polish, and the
internal (non-public) sharing-foundation data boundary, per this task's own explicit "continue
A → B → C automatically when the preceding gate passes" instruction. If this document is being read
at the start of a fresh session because that continuation did not happen in one sitting, resume from
here rather than re-deriving MVP-1.3-A's own analysis — its full evidence is already in the
specialist status doc.

Two independent items from prior sessions also remain open, unaffected by and not blocking the
above:

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1/1.2 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Exercise a real Tally XML import by hand** against a real paired Tally company using the
   installed `continuity.27` candidate (§5), per the human acceptance checklist in
   `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E §E14 — device pairing has still never been
   attempted (out of every installation-only task's scope so far); this is also the only way to
   exercise Dashboard/Connect/Dincharya/Timeline/Issues genuinely on-device, per §5's own honest
   disclosure of what MVP-1.2-E's own smoke test could and could not reach.

**Not started:** MVP-1.3-B/C (Part A complete, continuing automatically this session per above);
external/public distribution. Do not begin distribution before signing exists and the product owner
explicitly authorizes it.
