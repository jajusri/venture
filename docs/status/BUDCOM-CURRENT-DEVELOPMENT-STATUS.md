# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1 STATUS: COMPLETE / FROZEN (2026-08-17)** — A through E all technically complete, and the
final `continuity.26` candidate is now installed on the owner's device (§5).
**MVP-1.2 STATUS: PRODUCT DECISIONS LOCKED (2026-08-18); MVP-1.2-A TECHNICALLY COMPLETE, READY FOR
REVIEW (2026-08-18).** The four open product questions from the 2026-08-17 architecture session
were explicitly answered by the Product Owner on 2026-08-18 and recorded permanently as
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-014–PDL-017 (Relationship Timeline is the
unified history surface; Referral Tree deferred; Home Insights/OI deferred; OS notifications
deferred, Dincharya v1 is in-app only). All four confirmed this plan's own working assumptions — no
architecture rework was needed. MVP-1.2-A (Structured Party Activity Foundation — typed notes,
due-date/completion, `party_issues`, `MIGRATION_8_9`) is then implemented and mini-hardened this
same session. See §3. Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md`
Part A; architecture: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1/1.2 work)
**ANDROID `continuity.26` (versionCode 27)** — final MVP-1.1 candidate, **installed** on device
`I2407i` this cycle (fresh install, `com.budcom.android.debug`, launches cleanly to Secure Pairing)
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched, and not required by MVP-1.2
(confirmed by this session's architecture review — MVP-1.2 is 100% Android)

## 2. Branch / HEAD

- Branch: `main`, not pushed to `origin`.
- Starting HEAD this session: `ea6869caf71ff6a07406b9d15dbb0b68c7bcb5a2` (`docs: MVP-1.2 planning,
  recovery & architecture review`), working tree clean.
- HEAD at end of this session: see `git log -1`. This session's commit(s) lock the four MVP-1.2
  product decisions (PDL-014–PDL-017), implement MVP-1.2-A (Room schema v8→v9, repository/use-case/
  ViewModel/Compose changes, 24 new tests), and update all three Durable-Development-Record
  documents — see Development Ledger phase 27.
- Android: the final MVP-1.1 candidate `0.1.1-continuity.26` (versionCode 27) remains installed on
  device `I2407i` (§5) — unchanged this session; MVP-1.2-A's version was deliberately not bumped
  (see specialist status doc Part A §A11).

## 3. This session's work (product decisions locked + MVP-1.2-A implementation)

**Part 1 — decisions locked.** The four open product questions from the prior architecture session
were answered by the Product Owner and recorded permanently as
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-014–PDL-017: Relationship Timeline is the
unified historical presentation for a Party (not a separate Notes/Issue/Timeline trio); Referral
Tree deferred to a later dedicated milestone; Home Insights/OI system deferred, Dincharya
self-contained; OS-level notifications deferred, Dincharya v1 is in-app only. All four confirmed
the architecture document's own working assumptions/recommendations — the architecture doc's §3
was updated from "open questions" to "resolved" rather than rewritten, since no substantive change
was needed.

**Part 2 — MVP-1.2-A implemented.** Exactly the architecture doc's §22 recommended prompt: Room
schema v8→v9 (`party_notes` extended with `type`/`dueAt`/`completedAt`/`issueId`; new
`party_issues` table); `PartyRepository`/`PartyUseCases` extended for typed note create/edit,
due-date/completion toggling, and issue create/resolve/reopen; Party Detail's note dialog gained a
type picker, conditional due-date field, and issue picker — and, having found during inspection
that the dialog was Add-only despite the architecture doc calling it "the existing add/edit note
dialog," this session made it genuinely serve both modes (mirroring the already-established
`ContactPersonEditor` precedent), then self-caught and fixed one resulting defect (an edit-mode
voucher-relinking false affordance) before commit. 24 new tests (14 JVM + 16 instrumented, all run
on a connected physical device); full regression clean (`testDebugUnitTest`/`testReleaseUnitTest`
1,179/1,179, both lints 0 errors, all three assembles green, instrumented suite 245/257 — the
identical 12-failure pre-existing device-viewport class, zero overlap). A genuine screen-lock-
during-long-run false alarm (53 failures on the first instrumented pass) was investigated, traced
to the device's screen timing out mid-run (not a code regression — every extra failure was in a
file this session never touched), and resolved via a device power-setting change (`adb shell svc
power stayon usb`), not accepted or worked around blindly. Full detail, evidence, and the exact
mini-hardening checklist: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md`
Part A.

Full architecture detail: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
Prior milestone (MVP-1.1-A through E): `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Parts A–E.

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
(§1/§3, PDL-014–PDL-017). MVP-1.2-A is technically complete, mini-hardened, and documented (§3;
full evidence in `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part A).**

Per this milestone's own stop condition: **MVP-1.2-A has passed its gates. STOP — do not
automatically begin MVP-1.2-B.** Product/technical review of the 1.2-A result is expected before
1.2-B is authorized, per the locked progression `1.2-A → review → 1.2-B → review → 1.2-C → review →
1.2-D → review → 1.2-E → FREEZE`.

**Next authorized task once reviewed: MVP-1.2-B — Relationship Timeline** (architecture doc §11,
§18) — the merged notes + export-events chronological read model, replacing the current flat Notes
list's presentation on Party Detail per the now-locked PDL-014. Explicitly not yet in scope: Issue
History section (1.2-C), Dincharya screen (1.2-D), or any further MVP-1.2 UI beyond Timeline itself.

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
