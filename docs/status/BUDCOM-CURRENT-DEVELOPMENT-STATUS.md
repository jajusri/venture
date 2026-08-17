# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1 STATUS: COMPLETE / FROZEN (2026-08-17)** — A through E all technically complete, and the
final `continuity.26` candidate is now installed on the owner's device (§5).
**MVP-1.2 STATUS: PLANNING/ARCHITECTURE COMPLETE (2026-08-17) — implementation NOT started.** See
§3. Four open product questions must be answered before MVP-1.2-A begins — full detail:
`docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1/1.2 work)
**ANDROID `continuity.26` (versionCode 27)** — final MVP-1.1 candidate, **installed** on device
`I2407i` this cycle (fresh install, `com.budcom.android.debug`, launches cleanly to Secure Pairing)
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched, and not required by MVP-1.2
(confirmed by this session's architecture review — MVP-1.2 is 100% Android)

## 2. Branch / HEAD

- Branch: `main`, not pushed to `origin`.
- HEAD at end of this session: see `git log -1`. This session's commit adds the MVP-1.2
  architecture/planning document and updates the Development Ledger/this checkpoint — see
  Development Ledger phase 26. No application code was changed. A separate, undocumented-in-Git
  installation session (Development Ledger phase 25) installed the already-committed
  `continuity.26` candidate onto the owner's device; that session made no code/doc/Git changes by
  its own explicit instruction.
- Android: the final MVP-1.1 candidate `0.1.1-continuity.26` (versionCode 27) is now installed on
  device `I2407i` (§5) — the prior "owner-approved `continuity.22`" reference is superseded by
  this install.

## 3. This session's work (MVP-1.2 planning, recovery & architecture review)

No implementation — a planning/architecture session per explicit instruction. Read the locked
Master Product Execution Plan §8 (MVP-1.2's only locked scope title), the Universal Party Referral
Tree spec (the only document that actually defines Relationship Timeline/Issue History/Dincharya/
OI and where they sit in the UI), the UI Design Decisions doc, the Screen Inventory, `BUDCOM-NOT-
NOW.md`, and the Product Decision Log. Verified directly against source rather than assumed:
`party_notes` is flat/single-type today (no type/status/due-date/grouping); zero `Worker` classes
exist despite WorkManager being wired; Desktop has no Connect/Party surface at all, confirming
MVP-1.2 needs zero Desktop/Connector work.

Produced a full plan-vs-repository reconciliation matrix and a complete architecture document:
data model (extend `party_notes` with `type`/`dueAt`/`completedAt`/`issueId`; new `party_issues`
table; `MIGRATION_8_9`), UI/UX intent (no visual design, per instruction — information hierarchy
only), a five-sub-milestone breakdown (1.2-A structured-activity foundation → 1.2-B Relationship
Timeline → 1.2-C Issue History → 1.2-D Dincharya → 1.2-E integrated hardening, mirroring MVP-1.1's
own proven rhythm), test plan, and risk ranking (company-isolation on Dincharya's new cross-party
query is the single highest risk, since it is the first query in this feature area spanning an
entire company at once).

**Explicitly excluded from MVP-1.2, with reasoning recorded rather than assumed:** Referral Tree
(named "VVIMP" in its own locked spec but absent from the master plan's actual MVP-1.2 line item —
a genuine open product-sequencing question, §3 of the architecture doc), the Home Insights
dashboard (no content spec exists anywhere in the repository), OS-level push notifications for
Dincharya (zero supporting infrastructure exists today).

Full detail: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`.
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

**MVP-1.1 is complete/frozen and installed (§1/§5). MVP-1.2's architecture/scope plan is complete
(§3). Implementation has not begun.**

Before MVP-1.2-A can start, four open product questions need a Product Owner/ChatGPT Brainstorm-1
answer — see `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`
§3 for full framing:

1. Does Relationship Timeline replace or sit alongside the existing flat Notes/Activity display?
2. Is Referral Tree part of MVP-1.2, a later milestone, or genuinely shelved?
3. Does any part of the still-unwritten Home Insights workstream entangle with Dincharya?
4. Does Dincharya need OS-level notifications in v1, or is an in-app list sufficient (this plan's
   recommendation)?

Once answered, the recommended next prompt is the architecture document's §22 — **MVP-1.2-A only**
(structured Party Activity foundation: typed notes, due-date/completion, a new `party_issues`
table, `MIGRATION_8_9`; explicitly not Relationship Timeline/Issue History/Dincharya themselves,
which are 1.2-B/C/D).

Two independent items from prior sessions also remain open, unaffected by and not blocking the
above:

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1/1.2 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Exercise a real Tally XML import by hand** against a real paired Tally company using the now-
   installed `continuity.26` candidate, per the human acceptance checklist in
   `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E §E14 — device pairing was deliberately not
   attempted during installation (out of that task's scope).

**Not started:** MVP-1.2 implementation; external/public distribution; MVP-1.3+. Do not begin
distribution before signing exists and the product owner explicitly authorizes it. Do not begin
MVP-1.2-A without the four open questions above being answered first.
