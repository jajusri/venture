# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1 STATUS: COMPLETE / FROZEN (2026-08-17)** — A through E all technically complete, and the
final `continuity.26` candidate is now installed on the owner's device (§5).
**MVP-1.2 STATUS: PRODUCT DECISIONS LOCKED (2026-08-18); PARTS A, B, C ALL COMPLETE (2026-08-18);
B/C PUSHED TO ORIGIN AND MVP-1.2-D READINESS REVIEW COMPLETE (2026-08-18).**
The four open product questions from the 2026-08-17 architecture session were explicitly answered
by the Product Owner and recorded permanently as `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md`
PDL-014–PDL-017 (Relationship Timeline is the unified history surface; Referral Tree deferred;
Home Insights/OI deferred; OS notifications deferred, Dincharya v1 is in-app only). Part A
(Structured Party Activity Foundation), Part B (Relationship Timeline — merged notes/export-events
feed replacing the flat Notes list), and Part C (Issue History — Issues section + issue-lifecycle
Timeline entries + issue-filtered Timeline view) are all technically complete and mini-hardened. B's
acceptance gate passed explicitly, authorizing automatic continuation to C per this session's own
governing prompt; C's own test gate then passed, triggering this session's **final stop condition**
— MVP-1.2-D is not started, awaiting Product Owner/technical review of the full A+B+C result. See
§3. Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts A–C;
architecture: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1/1.2 work)
**ANDROID `continuity.26` (versionCode 27)** — final MVP-1.1 candidate, **installed** on device
`I2407i` this cycle (fresh install, `com.budcom.android.debug`, launches cleanly to Secure Pairing)
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched, and not required by MVP-1.2
(confirmed by this session's architecture review — MVP-1.2 is 100% Android)

## 2. Branch / HEAD

- Branch: `main`, **pushed to `origin` (2026-08-18 checkpoint session)** — local HEAD and
  `origin/main` verified byte-identical at `8f77cf63b7533f1418cb6208157969dee3022765` via
  `git fetch origin` + `git rev-parse`. Normal fast-forward push (`7cf85ba..8f77cf6`), no force flag,
  no history rewrite. 234 previously-local-only commits (spanning project inception through
  MVP-1.2-C) are now preserved on the remote.
- Starting HEAD of the B/C session: `f26def29c7e23ab2743616bc469abcdf94328242`
  (`feat(android): lock MVP-1.2 product decisions, implement MVP-1.2-A Party Activity foundation`),
  working tree clean. Prior session (product decisions + Part A): Development Ledger phase 27.
- HEAD after the B/C session: `8f77cf63b7533f1418cb6208157969dee3022765` (B = Development Ledger
  phase 28, commit `95ff98d`; C = phase 29, commit `8f77cf6`) — implemented in one autonomous run
  per explicit instruction to continue automatically once B's acceptance gate passed, which it did,
  and C's own test gate then passed too.
- This checkpoint session (Development Ledger phase 30) verified B/C directly against source,
  pushed the above HEAD to `origin/main`, and performed a **read-only** MVP-1.2-D readiness review
  (§7; full detail in `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md`
  "Checkpoint" section) — zero application code changed, so HEAD does not advance until this
  session's own documentation commit.
- Android: the final MVP-1.1 candidate `0.1.1-continuity.26` (versionCode 27) remains installed on
  device `I2407i` (§5) — unchanged this session; version bump deliberately deferred (per explicit
  instruction not to bump after B, and only at a coherent milestone boundary thereafter).

## 3. This session's work (MVP-1.2-B Relationship Timeline, then MVP-1.2-C Issue History)

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

## 4. Public release blocker (unrelated to MVP-1.1/1.2, unchanged)

No Windows code-signing certificate and no Android release keystore exist anywhere in this
repository/environment. Android public Play-Store `applicationId` also undecided. Full detail:
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. Not touched by MVP-1.1 or MVP-1.2
planning work.

## 5. Android install status

**Installed.** Device `I2407i` (serial `10BF44124K000E3`) had no BUDCOM package of any kind before
this cycle; the final MVP-1.1 candidate was installed as a genuine fresh install
(`adb install -r`, non-destructive, APK SHA-256 verified against the recorded MVP-1.1-E value
before installing). Confirmed: package `com.budcom.android.debug`, `versionName=0.1.1-
continuity.26`, `versionCode=27`, `firstInstallTime == lastUpdateTime`. Launch smoke test passed —
`MainActivity` resumed, zero crash entries in logcat, clean screenshot-confirmed render of the
Secure Pairing screen (the correct first-run state for a never-paired device; pairing itself was
not attempted, out of scope for that installation-only task). This device now has a real,
owner-visible BUDCOM installation for the first time across the entire MVP-1.1 arc.

Debug APK: `apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`
(SHA-256 `8a3413e7b4e4a22254fab7d2cbf05a698e22052ce6ef631e1e6dc6373282c4c3`).

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

**MVP-1.1 is complete/frozen and installed (§1/§5). MVP-1.2's four product decisions are locked
(§1/§3, PDL-014–PDL-017). MVP-1.2-A, MVP-1.2-B, and MVP-1.2-C are all technically complete,
mini-hardened, documented, and now pushed to `origin/main` (§2); B's acceptance gate and C's test
gate both passed explicitly (§3; full evidence in
`docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts A–C). The MVP-1.2-D
readiness review is also complete (§3 "Checkpoint session"; full detail in the same status document's
"Checkpoint" section) — **MVP-1.2-D READINESS REVIEW COMPLETE — IMPLEMENTATION NOT STARTED.**

**Next authorized task: begin MVP-1.2-D — Dincharya implementation.** The readiness review found no
blocking issue and no scope conflict — a fresh Claude session may begin implementation directly
using `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` "Checkpoint" section
(data-source analysis, file/architecture map, test matrix, hardening checklist) together with the
architecture document's own §10/§11 as the implementation brief. Key points carried forward: a new
`feature/dincharya/` top-level package (mirrors `feature/connect/`'s module boundary); the first
genuinely cross-party bounded query in this feature area (grouped by three deterministic item
types: follow-ups/callbacks, pending Tally confirmation, pending contact info) — zero new schema/
columns needed for any of the three, but each needs a new company-wide bounded DAO query; a new
Dashboard entry (mirrors the existing `OpenConnect`/`DashboardNavigation.Connect` wiring); OI
framing copy. Company-isolation on the new cross-party queries is the readiness review's own
identified single highest risk — the adversarial two-company tests for it must be named, explicit,
blocking tests at the DAO, repository, and ViewModel layers, not incidental. The required 500+-row
large-fixture performance test should be the deciding evidence for whether any new Room index (and
its accompanying additive migration) is actually justified — do not add one pre-emptively. Two
genuine open product ambiguities were flagged, not resolved, by the readiness review and should be
confirmed (or a documented default accepted) before or during implementation: (1) whether "pending
contact completion" scopes to Customer/Prospect classifications only or includes Supplier/Other;
(2) the exact aging/decay rule for long-overdue follow-ups beyond the locked top-N-plus-"N more"
bounding (architecture §20 Risk #2) — the readiness review proposes a conservative `dueAt ASC`
default absent a Product Owner decision. Explicitly not yet in scope: integrated hardening/freeze
(1.2-E).

Two independent items from prior sessions also remain open, unaffected by and not blocking the
above:

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1/1.2 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Exercise a real Tally XML import by hand** against a real paired Tally company using the
   installed `continuity.26` candidate, per the human acceptance checklist in
   `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E §E14 — device pairing was deliberately not
   attempted during installation (out of that task's scope).

**Not started:** MVP-1.2-D implementation (readiness review complete, implementation itself not yet
begun — §3/§7 above), MVP-1.2-E; external/public distribution; MVP-1.3+. Do not begin distribution
before signing exists and the product owner explicitly authorizes it.
