# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1 STATUS: COMPLETE / FROZEN (2026-08-17).**
**MVP-1.2 STATUS: PRODUCT DECISIONS LOCKED (2026-08-18); PARTS A, B, C, AND D ALL COMPLETE
(2026-08-18); MVP-1.2-D IS THIS SESSION'S OWN WORK, JUST COMPLETED.**
The four open product questions from the 2026-08-17 architecture session were explicitly answered
by the Product Owner and recorded permanently as `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md`
PDL-014–PDL-017 (Relationship Timeline is the unified history surface; Referral Tree deferred;
Home Insights/OI deferred; OS notifications deferred, Dincharya v1 is in-app only); a fifth entry,
PDL-018, records Part D's own implementation-level eligibility/ordering decisions (contact
completeness = valid phone OR valid email, Prospects/contact-persons excluded, no follow-up
auto-expiry, deterministic `dueAt`-based ordering, never an AI ranking). Part A (Structured Party
Activity Foundation), Part B (Relationship Timeline), Part C (Issue History), and **Part D
(Dincharya — the three-item-type deterministic worklist + fourth Dashboard entry)** are all
technically complete and mini-hardened. Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-
TIMELINE-DINCHARYA-STATUS.md` Parts A–D; architecture: `docs/architecture/BUDCOM-MVP-1-2-
RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`. Per this task's own governing instruction,
**MVP-1.2-D is this session's final stop condition — MVP-1.2-E is not started, awaiting Product
Owner/technical review.** See §3.
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1/1.2 work)
**ANDROID `continuity.26` (versionCode 27)** — version deliberately not bumped for Part D (deferred
to the 1.2-E freeze boundary, per explicit instruction); **NOT CURRENTLY INSTALLED on device
`I2407i`** — see §5 for a genuine, unintended finding this session surfaced (the device's
previously-documented installation is gone, most likely removed by `connectedDebugAndroidTest`'s
own default post-test uninstall behavior, not a deliberate action).
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched, and not required by MVP-1.2
(confirmed by this session's architecture review — MVP-1.2 is 100% Android)

## 2. Branch / HEAD

- Branch: `main`. `origin/main` was last verified byte-identical to local HEAD at the B/C checkpoint
  (`8f77cf63b7533f1418cb6208157969dee3022765`, 2026-08-18). **This MVP-1.2-D session's own commits
  are NOT pushed** — per explicit instruction, Git push only happens when separately authorized.
- Starting HEAD of the MVP-1.2-D session: `d964b760e90339d3037a3447bbffa85e116e4801`
  (`docs: MVP-1.2-B/C git preservation checkpoint + MVP-1.2-D readiness review`), working tree
  clean, exactly matching the checkpoint session's own recorded HEAD and this task's stated baseline.
- HEAD after this session: see this file's own commit history / `git log -1` — this session created
  coherent commits for the data/domain/repository layer, presentation/navigation/Dashboard wiring,
  tests, and documentation (exact commit hashes recorded in the final session report rather than
  hand-copied here, to avoid this checkpoint going stale the moment a commit message is amended).
- Android: version deliberately **not** bumped (`0.1.1-continuity.26`/versionCode 27 unchanged) —
  per explicit "D → review → E → one coherent bump" instruction. The device install status changed
  as an unintended side effect of this session's own required instrumented-test gate — see §5, a
  genuine finding, not a deliberate action.

## 3. Recent session work (MVP-1.2-B through D)

**Part B — Relationship Timeline, implemented and gate-passed.** New `PartyTimelineDao` merges
`party_notes` and `party_export_events` into one chronological, bounded/paged feed via a single SQL
`UNION ALL` (`ORDER BY timestamp DESC, id ASC LIMIT/OFFSET` computed entirely in SQL — merging two
independently-paged reads in Kotlin cannot correctly paginate one feed across a source boundary).
Zero schema change, zero migration. Per the locked PDL-014, this **replaces** (not duplicates) the
flat Notes list's presentation on Party Detail — note CRUD itself is unchanged, only its read
surface. 23 new tests (7 JVM + 16 instrumented, all run on a connected physical device) including
dedicated adversarial company-isolation tests (same display name/phone across two companies), a
same-timestamp tie-break determinism test, and a 350-row large-fixture performance proof. Full
regression clean: `testDebugUnitTest`/`testReleaseUnitTest` 1,186/1,186 both; both lints 0 errors;
all three assembles green; instrumented suite 256/268, the unchanged 12-failure pre-existing
device-viewport class, zero overlap. Applied the device stay-awake power-setting fix *proactively*
this time (learned directly from Part A's own documented lesson), so the instrumented run completed
cleanly on the first attempt. **B's acceptance gate passed on every mandatory criterion — automatic
continuation to Part C explicitly authorized and begun in this same session.**

**Part C — Issue History, implemented and complete.** Extended (not duplicated) B's
`PartyTimelineDao` `UNION ALL` with two more arms reading `party_issues` live
(`issue_opened`/`issue_resolved`) — the direct mechanism for "Issue/Timeline Consistency": the
Timeline and the new Issues section read the exact same table, so they can never disagree. Added a
Party Detail Issues section (open issues compact-then-expandable, resolved issues collapsed into a
secondary disclosure) with Resolve/Reopen actions (wiring 1.2-A's already-existing but
previously-unused use cases) and a "View in Timeline" issue filter reusing B's `issueId` parameter.
New `PartyNoteDao.issueActivitySummary` bounded aggregate gives each issue card its note-count/
last-activity rollup in one query. Accepted, explicitly documented limitation: reopening an issue
does not retain a record of a *past* resolution (no append-only issue-event log; building one was
assessed and rejected as disproportionate for this milestone). 29 new tests (15 JVM + 14
instrumented) with dedicated adversarial company-isolation and direct Timeline/Issue-consistency
proofs. Full regression clean: `testDebugUnitTest`/`testReleaseUnitTest` 1,201/1,201 both; both
lints 0 errors (one transient Gradle parallel-task lint-analysis race investigated and confirmed a
build-tool scheduling artifact, clean on immediate retry); all three assembles green; instrumented
suite 274/286, the unchanged 12-failure baseline, zero overlap. **C's test gate passed — this
session's final stop condition now applies.** Full detail (both parts):
`docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts B–C.

Full architecture detail: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
Prior milestone (MVP-1.1-A through E): `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Parts A–E.
Prior MVP-1.2 work (product decisions + Part A): `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part A.

**Checkpoint session — Git preservation + MVP-1.2-D readiness review (2026-08-18).** A separate,
explicitly non-implementation session. Re-verified B/C directly against source (not trusted from
docs alone) — zero discrepancy found; a lightweight regression check showed 100% Gradle
`UP-TO-DATE`, confirming zero drift since the last full green run. Pushed the verified B/C HEAD to
`origin/main` (§2) via a normal fast-forward, no force, no rewrite; confirmed byte-identical
post-push. Then performed a **read-only** MVP-1.2-D (Dincharya) architecture/readiness review:
locked-scope reconciliation, per-item-type data-source analysis (zero new schema/columns needed for
any of the three item types; each needs one new company-wide bounded query — the first genuinely
cross-party query in this feature area), company-isolation strategy (named as the dominant risk,
with explicit blocking adversarial-test requirements), performance/index-impact analysis (including
the nuance that a new Room index, if the required large-fixture proof justifies one, would itself
need an additive migration), UX/file-architecture/test-matrix/hardening plan, and two genuine open
product ambiguities flagged rather than silently resolved. **MVP-1.2-D READINESS REVIEW COMPLETE —
IMPLEMENTATION NOT STARTED.** Zero production code, migration, version bump, or install performed.
Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` "Checkpoint"
section; ledger: `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` phase 30.

**This session — MVP-1.2-D Dincharya, implemented and complete (2026-08-18).** The three locked
deterministic item types (PDL-018): Follow-ups/Callbacks (`party_notes`, `type IN
('commitment','follow_up')`, active/incomplete, no automatic expiry), Pending Tally Confirmation
(`party_field_provenance.state = 'exported'`, grouped per Party), Pending Contact Completion
(non-Prospect Parties missing both a valid phone and a valid email). Each is a new bounded,
`companyId`-scoped, company-wide DAO query — the first genuinely cross-party queries in this feature
area — added to the existing `PartyNoteDao`/`PartyFieldProvenanceDao`/`PartyDao`. A new dedicated
`feature/dincharya/` repository (not an extension of the already-24-method `PartyRepository`) composes
these DAOs directly. New top-level Dincharya screen reached from a fourth Dashboard entry, replicating
Connect's own MVP-1.1-B wiring exactly; deep-links every row to the existing `Routes.partyDetail`,
never a second detail view; reuses the established shared loading/error/empty-state components; OI
framing copy uses the architecture doc's own locked exact wording. 35 new instrumented tests (22 DAO
+ 12 Compose + 1 Dashboard-entry, all run and passing on a connected physical device, including three
dedicated 500-row large-fixture performance proofs — one per new query, all comfortably under 2s,
proving no new Room index was needed) + 18 new JVM tests, with dedicated adversarial company-isolation
coverage at the DAO, repository, and ViewModel layers. `testDebugUnitTest`/`testReleaseUnitTest`
1,219/1,219 both; both lints 0 errors; all three assembles green; instrumented suite 309/321, the
unchanged 12-failure pre-existing device-viewport baseline, zero overlap (confirmed individually
against every Dincharya/Party-DAO/Dashboard-entry testcase). One self-caught defect fixed before
commit: a refresh failure with existing content on screen was initially swallowed silently, not shown
— fixed to match Connect's own inline-error discipline. Zero schema change, zero migration, version
not bumped. **A genuine unintended side effect was found and is fully disclosed, not hidden: running
the required `connectedDebugAndroidTest` gate appears to have uninstalled the device's
previously-documented BUDCOM installation — see §5.** Full detail:
`docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part D; ledger:
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` phase 31; product decisions:
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-018.

## 4. Public release blocker (unrelated to MVP-1.1/1.2, unchanged)

No Windows code-signing certificate and no Android release keystore exist anywhere in this
repository/environment. Android public Play-Store `applicationId` also undecided. Full detail:
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. Not touched by MVP-1.1 or MVP-1.2
planning work.

## 5. Android install status

**NOT INSTALLED as of the MVP-1.2-D session (2026-08-18) — a genuine, unintended finding, recorded
here rather than left stale.** `adb shell pm list packages` / `pm path com.budcom.android.debug` /
`pm path com.budcom.android` on device `10BF44124K000E3` (`I2407i`) all confirm **zero BUDCOM
package of any kind is currently installed**, despite this file's own prior claim (below, preserved
for the record) that the frozen MVP-1.1 candidate was installed and unchanged through MVP-1.2-A/B/C.
The MVP-1.2-D session ran the required `connectedDebugAndroidTest` instrumented-test gate twice on
this device (architecture/testing convention this codebase has followed since MVP-1.1-B, and this
task's own explicit instrumented-testing requirement) — Gradle/AGP's `connectedDebugAndroidTest`
task installs the app-under-test and test APK before running, then **uninstalls both afterward by
default**, for test hermeticity. This is almost certainly what removed the previously-installed
candidate; it was not a direct `adb install`/`adb uninstall` action, and no reinstall was attempted
(the original `continuity.26` binary's exact bytes no longer exist locally — this session's own
`assembleDebug`/`connectedDebugAndroidTest` runs rebuilt `app-debug.apk` from today's source, which
now includes MVP-1.2-D — and installing anything is explicitly outside this session's authorized
scope). Every prior 1.2-A/B/C session ran the identical gate on this same device *after* the
original install (§ history below) without re-checking `pm list packages` afterward, so this may
well have already happened during Part A's own instrumented run and simply gone unnoticed/
unreported since — that cannot be confirmed retroactively, but it means this is very likely a
pre-existing gap in this project's own testing practice, not a new one introduced this session.
**Flagged for explicit Product Owner decision:** whether/when to reinstall (e.g., a fresh
`continuity.26`-equivalent build, or wait for the MVP-1.2-E freeze candidate), and whether future
sessions should avoid `connectedDebugAndroidTest` on any device carrying a real, meaningful
persistent installation, running it only on a disposable emulator/secondary device instead.

**Prior claim (now stale, preserved for the record):** device `I2407i` had no BUDCOM package before
the phase-25 install cycle; the final MVP-1.1 candidate was installed as a genuine fresh install
(`adb install -r`, non-destructive, APK SHA-256 verified against the recorded MVP-1.1-E value before
installing). Confirmed at the time: package `com.budcom.android.debug`, `versionName=0.1.1-
continuity.26`, `versionCode=27`, `firstInstallTime == lastUpdateTime`. Launch smoke test passed —
`MainActivity` resumed, zero crash entries in logcat, clean screenshot-confirmed render of the
Secure Pairing screen.

Debug APK (now reflects today's MVP-1.2-D source, not continuity.26):
`apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk` — the recorded MVP-1.1-E SHA-256
(`8a3413e7b4e4a22254fab7d2cbf05a698e22052ce6ef631e1e6dc6373282c4c3`) no longer matches this file, by
design (this session's regression-gate builds rebuilt it from current source).

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

**MVP-1.1 is complete/frozen (§1). MVP-1.2's product decisions are locked (§1/§3, PDL-014–PDL-018).
MVP-1.2-A, MVP-1.2-B, MVP-1.2-C, and MVP-1.2-D are all technically complete, mini-hardened, and
documented (§3; full evidence in `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-
STATUS.md` Parts A–D). This MVP-1.2-D session's own commits are local-only, not yet pushed to
`origin/main` (§2) — per this task's explicit instruction, Git push happens only when separately
authorized.** Per this task's own governing stop condition: **MVP-1.2-D COMPLETE — AWAITING HUMAN
REVIEW BEFORE MVP-1.2-E.**

**Next authorized task, once reviewed and explicitly approved: MVP-1.2-E — integrated MVP-1.2
hardening, full regression re-verification, migration re-verification, an accessibility pass across
Parts A–D together, and the freeze** (architecture doc §11's own 1.2-E line item). The single
coherent version bump deferred across A/B/C/D (§2) should land at that freeze boundary, per this
task's own explicit instruction, not before. Do not begin 1.2-E, 1.3, or any external/public
distribution before that explicit review and approval.

**A genuine open item requiring an explicit Product Owner decision before any further device work:**
device `I2407i`/`10BF44124K000E3` currently has no BUDCOM package installed at all (§5) — the
previously-documented `continuity.26` installation is gone, most likely removed by this session's own
required `connectedDebugAndroidTest` instrumented-test gate (AGP's default post-test uninstall
behavior), not a deliberate action, and not something this session attempted to fix by installing
anything (explicitly out of scope). Decide: (a) reinstall a `continuity.26`-equivalent build now, (b)
wait for the MVP-1.2-E freeze candidate, or (c) accept the gap until public release. Also decide
whether future sessions should keep running `connectedDebugAndroidTest` against this same physical
device at all, given this side effect, or move that gate to a disposable emulator/secondary device.

Two independent items from prior sessions also remain open, unaffected by and not blocking the
above:

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1/1.2 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Exercise a real Tally XML import by hand** against a real paired Tally company, once a candidate
   is reinstalled (§5), per the human acceptance checklist in `docs/status/BUDCOM-MVP-1-1-CONNECT-
   STATUS.md` Part E §E14 — device pairing was deliberately not attempted during the original
   installation (out of that task's scope), and there is currently no installation to pair at all.

**Not started:** MVP-1.2-E (awaiting explicit review/approval per this task's own stop condition);
external/public distribution; MVP-1.3+. Do not begin distribution before signing exists and the
product owner explicitly authorizes it.
