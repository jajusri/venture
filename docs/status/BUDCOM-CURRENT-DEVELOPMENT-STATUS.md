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
Per this task's own governing stop condition, **MVP-1.3 is not started, not authorized.**
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1/1.2 work)
**ANDROID `0.1.1-continuity.27` (versionCode 28)** — the single coherent MVP-1.2 freeze candidate,
bumped only after full A–D+E regression was confirmed green. **Installed** on device `I2407i`
(`10BF44124K000E3`) via `adb install -r` this session — a genuine fresh install (the device had zero
BUDCOM package beforehand, per the Part D finding in §5 below). Launch/smoke-verified clean; see §5.
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched, and not required by MVP-1.2.

## 2. Branch / HEAD

- Branch: `main`. `origin/main` was last verified byte-identical to local HEAD at the B/C checkpoint
  (`8f77cf63b7533f1418cb6208157969dee3022765`, 2026-08-18) — MVP-1.2-D's and MVP-1.2-E's commits
  advance local HEAD well beyond that point; push status for this range is recorded in §2a below,
  not assumed.
- Starting HEAD of the MVP-1.2-D session: `d964b760e90339d3037a3447bbffa85e116e4801`.
- HEAD after MVP-1.2-D: `57a27e44f813aede9e5b71dd5a43440380ed670c` (4 commits: data/domain/
  repository, presentation/navigation/Dashboard, tests, documentation).
- Starting HEAD of the MVP-1.2-E session: `57a27e44f813aede9e5b71dd5a43440380ed670c`, working tree
  clean — confirmed by direct inspection (`git status`, `git log`, `DatabaseConstants.VERSION`,
  presence of all 8 `feature/dincharya/` files), not trusted from documentation alone.
- HEAD after MVP-1.2-E: see `git log -1` / this session's own final report — this session created a
  `fix(android)` commit (note-completion UI wiring), a `test(android)` commit (new tests + version
  bump), and a `docs` commit (this checkpoint + ledger phase 32 + specialist status Part E). Exact
  hash intentionally not hand-copied here to avoid this record going stale the moment a later commit
  is amended — see the session's own final report for the authoritative value.
- Android: version bumped `0.1.1-continuity.26`→**`0.1.1-continuity.27`** (versionCode 27→28) — the
  single coherent MVP-1.2 freeze bump, performed only after full A–D+E regression was green (per
  explicit instruction, not before).

### 2a. Push status

Recorded factually, not assumed: this file is updated *before* the push step in this session's own
sequence, so the authoritative push result (fast-forward push succeeded and local HEAD ==
`origin/main`, or push was not possible / not yet performed) is stated in this session's own final
report and should be cross-checked against `git status`/`git log` directly rather than trusted from
this line alone if this document is read before that report.

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
evidence in `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts A–E).**
Per this task's own governing stop condition: **do not begin MVP-1.3 substantively. Do not modify
Desktop. Do not begin public signing. Do not publish anything.**

**Next authorized task: MVP-1.3 planning/recovery review** — read-only reconnaissance only (read the
Master Product Execution Plan, reconcile against actual repository state, produce a scope/
architecture plan), exactly mirroring how the MVP-1.2 planning session (ledger phase 26) began
before any MVP-1.2 code was written. Do not begin MVP-1.3 implementation until that review is
produced and explicitly authorized.

Two independent items from prior sessions also remain open, unaffected by and not blocking the
above:

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1/1.2 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Exercise a real Tally XML import by hand** against a real paired Tally company using the now-
   installed `continuity.27` candidate (§5), per the human acceptance checklist in
   `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E §E14 — device pairing has still never been
   attempted (out of every installation-only task's scope so far); this is also the only way to
   exercise Dashboard/Connect/Dincharya/Timeline/Issues genuinely on-device, per §5's own honest
   disclosure of what this session's smoke test could and could not reach.

**Not started:** MVP-1.3 (planning/recovery review authorized, implementation not); external/public
distribution. Do not begin distribution before signing exists and the product owner explicitly
authorizes it.
