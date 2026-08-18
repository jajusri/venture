# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1 STATUS: COMPLETE / FROZEN (2026-08-17)** — A through E all technically complete, and the
final `continuity.26` candidate is now installed on the owner's device (§5).
**MVP-1.2 STATUS: PRODUCT DECISIONS LOCKED (2026-08-18); PART A COMPLETE; PART B COMPLETE —
ACCEPTANCE GATE PASSED (2026-08-18); PART C IN PROGRESS (same session, automatic continuation).**
The four open product questions from the 2026-08-17 architecture session were explicitly answered
by the Product Owner and recorded permanently as `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md`
PDL-014–PDL-017 (Relationship Timeline is the unified history surface; Referral Tree deferred;
Home Insights/OI deferred; OS notifications deferred, Dincharya v1 is in-app only). Part A
(Structured Party Activity Foundation — typed notes, due-date/completion, `party_issues`,
`MIGRATION_8_9`) and Part B (Relationship Timeline — merged notes/export-events chronological feed
replacing the flat Notes list) are both technically complete and mini-hardened. Part B's own
acceptance gate passed on every mandatory criterion, explicitly authorizing automatic continuation
to Part C (Issue History) within the same session, per this session's own governing prompt. See §3.
Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts A–B;
architecture: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1/1.2 work)
**ANDROID `continuity.26` (versionCode 27)** — final MVP-1.1 candidate, **installed** on device
`I2407i` this cycle (fresh install, `com.budcom.android.debug`, launches cleanly to Secure Pairing)
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched, and not required by MVP-1.2
(confirmed by this session's architecture review — MVP-1.2 is 100% Android)

## 2. Branch / HEAD

- Branch: `main`, not pushed to `origin`.
- Starting HEAD this session (the B/C session): `f26def29c7e23ab2743616bc469abcdf94328242`
  (`feat(android): lock MVP-1.2 product decisions, implement MVP-1.2-A Party Activity foundation`),
  working tree clean. Prior session (product decisions + Part A): Development Ledger phase 27.
- HEAD at end of this session: see `git log -1`. This session implements MVP-1.2-B (Relationship
  Timeline) — see Development Ledger phase 28 — and then MVP-1.2-C (Issue History) in the same
  autonomous run, per explicit instruction to continue automatically once B's acceptance gate
  passed.
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
continuation to Part C explicitly authorized and begun in this same session.** Full detail:
`docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part B.

Full architecture detail: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
Prior milestone (MVP-1.1-A through E): `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Parts A–E.
Prior MVP-1.2 work (product decisions + Part A): `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part A.

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
(§1/§3, PDL-014–PDL-017). MVP-1.2-A and MVP-1.2-B are both technically complete, mini-hardened, and
documented; B's acceptance gate passed explicitly (§3; full evidence in
`docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts A–B).**

**MVP-1.2-C — Issue History is authorized and in progress in this same autonomous session**, per
explicit instruction to continue automatically from B to C once B's gate passed (this document will
be updated again once C itself completes or if it hits its own STOP condition). Scope: the
structured issue lifecycle (create/resolve/reopen, already built in 1.2-A) surfaced as a Party
Detail Issues section, issue-filtered Timeline view (reusing 1.2-B's `PartyTimelineDao` `issueId`
parameter), and issue-lifecycle events wired into the Timeline for consistency — never a second,
competing chronological history (architecture §10, PDL-014). Explicitly not yet in scope: Dincharya
screen (1.2-D), integrated hardening/freeze (1.2-E).

Two independent items from prior sessions also remain open, unaffected by and not blocking the
above:

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1/1.2 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Exercise a real Tally XML import by hand** against a real paired Tally company using the
   installed `continuity.26` candidate, per the human acceptance checklist in
   `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E §E14 — device pairing was deliberately not
   attempted during installation (out of that task's scope).

**Not started:** MVP-1.2-B through 1.2-E; external/public distribution; MVP-1.3+. Do not begin
distribution before signing exists and the product owner explicitly authorizes it. Do not begin
MVP-1.2-B without product/technical review of the 1.2-A result first.
