# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1-D TALLY XML ENRICHMENT ROUND-TRIP — AUTOMATED WORK COMPLETE, READY FOR REVIEW (2026-08-17)**
**MVP-1.1-C PARTY DETAIL + PROSPECT CREATION + CONTACT PERSONS + TAGS + NOTES/ACTIVITY — READY FOR
REVIEW (2026-08-17, same session, unchanged since)**
**MVP-1.1-B / MVP-1.1-A — READY FOR REVIEW (2026-08-17, prior sub-sessions, unchanged)**
**This closes the combined MVP-1.1-C+D session — MVP-1.1-E was not started, per explicit
instruction.**
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1 work)
**ANDROID `continuity.25` (versionCode 26)** — new candidate this session, built/tested, **not
installed** (device has no genuine pre-existing BUDCOM install — §5)
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched this session

## 2. Branch / HEAD

- Branch: `main`, not pushed to `origin`.
- HEAD at end of this session: see `git log -1` (this checkpoint's own commit is necessarily
  self-referential). This session's commits implement MVP-1.1-C then MVP-1.1-D, bump the Android
  candidate once for the combined session, then record status/ledger/checkpoint — see the
  Development Ledger phases 22-23 for the full list.
- Android: owner-approved installed candidate remains `0.1.1-continuity.22` (versionCode 23,
  unchanged/untouched); this session's new `continuity.25` (versionCode 26) exists only as a
  built, tested, not-yet-installed APK (§5).

## 3. This session's work (MVP-1.1-C + MVP-1.1-D)

**Part C** — first slice where a user can edit Party data in BUDCOM: a five-section Party Detail
screen (identity/Call/WhatsApp; accounting deep links shown only when a real `PartySourceLink`
exists; Tally-compatible fields with plain-language provenance; tags; contact persons;
notes/activity), a minimal offline Prospect-creation flow, full contact-person/tag/note CRUD. Every
edit local-Room-only, zero Connector calls. New `party_notes` table (`MIGRATION_6_7`, schema v7).

**Part D** — closes the combined session. Change-review + Tally-compatible external-IMPORTDATA XML
generation (six-field whitelist — `addressCity` deliberately excluded, no distinct Tally tag
exists) + Save-via-SAF + a lightweight field-names-only export audit trail + on-demand re-sync
confirmation with field-appropriate canonical comparison. BUDCOM never writes to Tally directly.
Closed a genuine pre-existing gap along the way: Android had never called the Connector's
already-implemented, read-only `GET /ledgers/{id}` detail endpoint, so there was no local ground
truth for phone/email/address/GSTIN beyond a phone heuristic — closed with one new read-only
Retrofit method + a new `LedgerLiveDetailPort`, wired only into the explicit "Check Tally" action.
Edit-after-export staleness and per-field partial confirmation both verified to fall out correctly
from 1.1-A's existing `updateBudcomOnlyField` behavior, with zero new bookkeeping. Prospects
structurally excluded from export (button lives inside the same accounting-link-only block as View
Ledger/View Vouchers) plus a defensive ViewModel re-check. New `party_export_events` table
(`MIGRATION_7_8`, schema v8).

112 new tests this session (77 JVM + 35 instrumented: 63 Part C + 49 Part D, all passing on a
connected physical device `I2407i`, not simulated) — including one genuine Compose crash (a nested
`LazyColumn` inside a `verticalScroll` `Column`) found and fixed by the instrumented suite before
commit. Full regression: `testDebugUnitTest`/`testReleaseUnitTest` 1,165/1,165 (one incidental,
already-documented `VoucherRepositoryImplTest` flake reproduced again this session, always
unrelated, always clean on retry); both lints 0 errors; `assembleDebug`/`assembleRelease`/
`assembleDebugAndroidTest` all green; full-app instrumented suite 229/241 (the same 12-failure
pre-existing device-viewport-artifact class documented in Parts B/C, zero overlap with any
Connect/Party file). Zero MVP-1/MVP-1.1-A/MVP-1.1-B behavior change — every edit to existing code
was additive. Version bumped once for the combined session: `continuity.24`→`continuity.25`
(versionCode 25→26).

Full detail: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Parts C and D.
Prior milestones (MVP-1.1-A, MVP-1.1-B): same file, Parts A and B.
Prior sessions (pre-signing technical-debt closure, public-release prep, UI/UX polish): Development
Ledger phases 17-19, unchanged this session.

## 4. Public release blocker (unrelated to MVP-1.1, unchanged)

No Windows code-signing certificate and no Android release keystore exist anywhere in this
repository/environment. Android public Play-Store `applicationId` also undecided. Full detail:
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. Not touched by MVP-1.1 work.

## 5. Android install status

Connected device `I2407i` (serial `10BF44124K000E3`) still has **no genuine pre-existing BUDCOM
installation** at every check this session — only transient `connectedDebugAndroidTest`
test-harness packages, recreated fresh by each test run. Per explicit governing instruction ("if no
connected device has BUDCOM installed: do not install"), **no install was performed** (same
finding as Parts A/B/C). The owner's previously-approved `continuity.22` install elsewhere is
untouched. The device showed one transient mid-session ADB disconnect during instrumented testing
(recognized as a known non-defect pattern, reconnected and retried successfully) and shows
disconnected again at the time of this checkpoint — not treated as requiring any action.

Built candidate, if the owner wants it installed somewhere specific:
`apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`
(SHA-256 `8619c5652555a56616d78ef8ace58b8be87e74f89568dfba5b9ac5689da915c6`).

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

**Two independent next items, neither blocking the other — neither started:**

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Decide whether/where to install `continuity.25`** for a genuine live smoke test against a
   real paired Tally company, including exercising a real Tally XML import by hand (see
   `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part D §D16 for the human checklist). No device
   currently qualifies for a standing install (§5).

**Not started:** MVP-1.1-E; any further MVP-1.1 work; external/public distribution; MVP-1.2+. Do
not begin distribution before signing exists and the product owner explicitly authorizes it.
