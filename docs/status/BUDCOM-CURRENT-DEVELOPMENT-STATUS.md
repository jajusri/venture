# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1-C PARTY DETAIL + PROSPECT CREATION + CONTACT PERSONS + TAGS + NOTES/ACTIVITY —
AUTOMATED WORK COMPLETE, READY FOR REVIEW (2026-08-17)**
**MVP-1.1-B CONNECT BROWSER + CUSTOMERS/PROSPECTS + ACCOUNTING DEEP LINKS — READY FOR REVIEW
(2026-08-17, prior sub-session, unchanged)**
**MVP-1.1-A UNIVERSAL PARTY FOUNDATION — READY FOR REVIEW (2026-08-17, prior sub-session, unchanged)**
**MVP-1.1-D TALLY XML ENRICHMENT ROUND-TRIP — NOT STARTED** (next task, same combined-milestone
session, gated to begin only after Part C's own commit/status recording, per explicit instruction)
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1 work)
**ANDROID `continuity.24` (versionCode 25)** — unchanged this session; version bump intentionally
deferred until Part D also stabilizes (one coherent bump, not per-sub-milestone)
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched this session

## 2. Branch / HEAD

- Branch: `main`, not pushed to `origin`.
- HEAD at end of this session: see `git log -1` (this checkpoint's own commit is necessarily
  self-referential). This session's commits implement MVP-1.1-C, then record status/ledger/
  checkpoint — see the Development Ledger phase 22 entry for the full list.
- Android: owner-approved installed candidate remains `0.1.1-continuity.22` (versionCode 23,
  unchanged/untouched); the `continuity.24` (versionCode 25) candidate from the prior sub-session
  is also unchanged; Part C's own debug/release APKs are built/tested but carry the same
  unchanged version (§5) since the bump is deferred to after Part D.

## 3. This session's work (MVP-1.1-C)

First slice where a user can edit Party data in BUDCOM, not just browse it. A five-section Party
Detail screen (identity/Call/WhatsApp; accounting deep links shown only when a real
`PartySourceLink` exists, never fabricated for a Prospect; Tally-compatible contact fields with
plain-language provenance labels; tags; contact persons; notes/activity), reachable by tapping any
Connect row. A minimal offline Prospect-creation flow (name required, everything else optional, no
accounting field, no auto-merge on duplicate name/phone) reachable via a new FAB on Connect's
Prospects tab. Full contact-person CRUD with a repository-enforced at-most-one-primary invariant;
full tag create/assign/unassign with duplicate-prevention; full note CRUD with bounded
newest-first paging and optional voucher-linking. Every edit is local-Room-only — zero Connector
calls, and the only path to a Tally-confirmed field (`confirmFieldFromTally`) is unreachable from
any Part C code, so a BUDCOM edit can never mark itself confirmed. New `party_notes` table via
`MIGRATION_6_7` (schema version 6→7).

63 new tests (40 JVM + 23 instrumented, all passing on a connected physical device `I2407i`, not
simulated). Full regression: `testDebugUnitTest`/`testReleaseUnitTest` 1,128/1,128; both lints 0
errors; `assembleDebug`/`assembleRelease`/`assembleDebugAndroidTest` all green; full-app
instrumented suite 217/229 (the same 12-failure device-viewport-artifact class documented in Part
B §B20, same unrelated files, zero overlap with any Part C file — see §5 and the specialist doc
for the exact list). Explicit mini-hardening audit passed with one documented, deliberately-
deferred UI-polish item (no ellipsis on a very long Party-name header) and no other findings. Zero
MVP-1/MVP-1.1-A/MVP-1.1-B behavior change — every edit to existing code was additive.

Full detail: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part C.
Prior milestones (MVP-1.1-A, MVP-1.1-B) detail: same file, Parts A and B.
Prior sessions (pre-signing technical-debt closure, public-release prep, UI/UX polish): Development
Ledger phases 17-19, unchanged this session.

## 4. Public release blocker (unrelated to MVP-1.1, unchanged)

No Windows code-signing certificate and no Android release keystore exist anywhere in this
repository/environment. Android public Play-Store `applicationId` also undecided. Full detail:
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. Not touched by MVP-1.1 work.

## 5. Android install status

Connected device `I2407i` (serial `10BF44124K000E3`) still has **no genuine pre-existing BUDCOM
installation** — only transient `connectedDebugAndroidTest` test-harness packages
(`com.budcom.android.debug`/`.debug.test`), recreated fresh by each test run, not a real prior user
install with data/pairing/company state to preserve, and this session still has no context
establishing the device's ownership/authorization for a standing install. Per explicit governing
instruction ("if no connected device has BUDCOM installed: do not install"), **no install was
performed** (same finding as Parts A and B). The owner's previously-approved `continuity.22`
install elsewhere is untouched.

Built candidate, if the owner wants it installed somewhere specific:
`apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`
(SHA-256 `6a4a40616662e2a2240a105ec65f5c17d7542e6cc06f0a1ebc6a2a648650b470`).

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

1. **MVP-1.1-D — Tally XML enrichment round-trip** (same combined-milestone session as Part C;
   begins now that Part C is committed and status-recorded, per that session's own explicit
   C-before-D gate). Eligible-field whitelist, change-review screen, Tally IMPORTDATA XML
   generator, export lifecycle, Save/Share via a new FileProvider cache path, re-sync confirmation
   — full detail in the governing prompt; not yet started.
2. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.

**Not started:** external/public distribution; MVP-1.1-C onward; MVP-1.2+. Do not begin
distribution before signing exists and the product owner explicitly authorizes it.
