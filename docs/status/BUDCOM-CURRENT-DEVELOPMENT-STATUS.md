# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1-B CONNECT BROWSER + CUSTOMERS/PROSPECTS + ACCOUNTING DEEP LINKS — AUTOMATED WORK
COMPLETE, READY FOR REVIEW (2026-08-17)**
**MVP-1.1-A UNIVERSAL PARTY FOUNDATION — READY FOR REVIEW (2026-08-17, prior session, unchanged)**
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1 work)
**ANDROID `continuity.24` (versionCode 25)** — new candidate this session, built/tested,
**not installed** (no device with a genuine pre-existing BUDCOM install was available — §5)
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched this session

## 2. Branch / HEAD

- Branch: `main`, not pushed to `origin`.
- HEAD at end of this session: see `git log -1` (this checkpoint's own commit is necessarily
  self-referential). This session's commits implement MVP-1.1-B, bump the Android candidate, then
  record status/ledger/checkpoint — see the Development Ledger phase 21 entry for the full list.
- Android: owner-approved installed candidate remains `0.1.1-continuity.22` (versionCode 23,
  unchanged/untouched); this session's new `continuity.24` (versionCode 25) exists only as a
  built, tested, not-yet-installed APK (§5).

## 3. This session's work (MVP-1.1-B)

First user-visible Connect slice: a Customers/Prospects browser reachable from a new Dashboard
entry, local-first (zero network calls — offline-capable by construction), classification-scoped
name/phone search, Call (`ACTION_DIAL`)/WhatsApp (`wa.me`) actions, and deep links into the
existing unmodified Ledger Statement (stable ledger-id) and Voucher Browser (name-based prefill —
a documented, honestly-disclosed limitation of the pre-existing Voucher schema, not a regression).
Multi-company isolation explicitly tested (including that two companies' identically-named/-phoned
Parties never share a balance). Outstanding confirmed not buildable — no authoritative bill-wise
data exists anywhere in the schema — and not attempted. Also added the permanent "Durable
Development Record" rule to `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A.

32 new tests (15 JVM + 17 instrumented, all passing on a connected physical device `I2407i`, not
simulated). Full regression: `testDebugUnitTest`/`testReleaseUnitTest` 1,088/1,088; both lints 0
errors; `assembleDebug`/`assembleRelease`/`assembleDebugAndroidTest` all green; full-app
instrumented suite 194/206 (12 pre-existing failures confined entirely to files this session never
touched, classified as a device-viewport artifact on `I2407i`, not a regression — see §5 and the
specialist doc for the exact list). Zero MVP-1/MVP-1.1-A behavior change — every edit to existing
code was additive.

Full detail: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part B.
Prior milestone (MVP-1.1-A) detail: same file, Part A.
Prior sessions (pre-signing technical-debt closure, public-release prep, UI/UX polish): Development
Ledger phases 17-19, unchanged this session.

## 4. Public release blocker (unrelated to MVP-1.1, unchanged)

No Windows code-signing certificate and no Android release keystore exist anywhere in this
repository/environment. Android public Play-Store `applicationId` also undecided. Full detail:
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. Not touched by MVP-1.1 work.

## 5. Android install status

Connected device `I2407i` (serial `10BF44124K000E3`) has **no genuine pre-existing BUDCOM
installation** — only this session's own `connectedDebugAndroidTest` test-harness packages
(`com.budcom.android.debug`/`.debug.test`), not a real prior user install with data/pairing/company
state to preserve, and this session has no context establishing the device's ownership/
authorization for a standing install. Per explicit governing instruction ("if no connected device
has BUDCOM installed: do not install"), **no install was performed.** The owner's previously-
approved `continuity.22` install elsewhere is untouched.

Built candidate, if the owner wants it installed somewhere specific:
`apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`
(SHA-256 `50f80bede5d990c5327736d23f02844570e2d9696d3f0010f679caaed9b793c4`).

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

**Two independent next items, neither blocking the other:**

1. **MVP-1.1-C — Party Detail + Contact Persons + Tags + Notes/Activity + voucher-linked notes**
   (per the architecture file's own milestone sequencing) — **do not begin without explicit
   go-ahead.** Before starting, the user may want to: decide whether/where to install the
   `continuity.24` candidate for a genuine live smoke test against a real paired Tally company
   (§5), and confirm the View-Vouchers name-based deep-link disposition
   (`docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part B §B13) is acceptable or commission a
   focused Voucher-schema slice to add a real stable ledger-id filter first.
2. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.

**Not started:** external/public distribution; MVP-1.1-C onward; MVP-1.2+. Do not begin
distribution before signing exists and the product owner explicitly authorizes it.
