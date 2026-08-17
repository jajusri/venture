# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`; detailed milestone evidence lives in specialist status
documents (e.g. `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`). This file stays short and links
out — see `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A for the permanent rule
governing the three-tier split. Update all three when a work unit changes their subject.

## 1. Current phase

**MVP-1.1 STATUS: COMPLETE / FROZEN (2026-08-17)** — A through E all technically complete.
MVP-1.1-E (Integrated Hardening, Acceptance & Freeze) closed this session: full integration audit
across the A–D chain, one genuine accessibility defect found and fixed, full JVM/lint/assemble +
full-device-instrumented regression re-run clean, final candidate built and version-bumped.
**NEXT MILESTONE: MVP-1.2** (planning/recovery review only — not started).
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1 work)
**ANDROID `continuity.26` (versionCode 27)** — final MVP-1.1 candidate, built/tested, **not
installed** (device has no genuine pre-existing BUDCOM install — §5)
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched this session

## 2. Branch / HEAD

- Branch: `main`, not pushed to `origin`.
- HEAD at end of this session: see `git log -1` (this checkpoint's own commit is necessarily
  self-referential). This session's commit implements MVP-1.1-E (integration audit, one
  accessibility fix, version bump, documentation) — see the Development Ledger phase 24 for detail.
- Android: owner-approved installed candidate remains `0.1.1-continuity.22` (versionCode 23,
  unchanged/untouched); the final MVP-1.1 candidate `continuity.26` (versionCode 27) exists only
  as a built, tested, not-yet-installed APK (§5).

## 3. This session's work (MVP-1.1-E — Integrated Hardening, Acceptance & Freeze)

Not new feature work: an integration audit across the already-implemented A–D scope, plus final
regression and release-candidate production. Traced the full Connect → Party Detail → field edit →
Export-to-Tally review → XML generation → Save → export audit → "Check Tally" re-sync →
confirmation/conflict chain directly against production code (navigation graph, Party-identity/
company-isolation natural keys, provenance gating, export-audit privacy, XML escaping, file/cache
path-containment, migrations, live-read company scoping, offline/performance/security) — zero
integration-level defect found; A–D's own mini-hardening evidence corroborated, not contradicted.

One genuine defect found and fixed: two Material3 `Checkbox` controls (`PartyXmlExportScreen`'s
field-selection checkbox, `PartyDetailScreen`'s contact-primary checkbox) had no accessible content
description — fixed additively via `Modifier.semantics { contentDescription = ... }`, zero
click-handling or test-tag change.

Fresh full regression this session: `testDebugUnitTest`/`testReleaseUnitTest` 1,165/1,165 (one
incidental, already-documented `VoucherRepositoryImplTest` flake, confirmed clean on isolated and
full re-run — unrelated to any file this session touched); both lints 0 errors;
`assembleDebug`/`assembleRelease`/`assembleDebugAndroidTest` all green; full-app instrumented
suite 229/241 on device `I2407i`, the identical 12-failure pre-existing device-viewport-artifact
class documented in Parts B/C/D, zero overlap with any Connect/Party/XML-export file (including
the two files this session edited — both migration tests and every Connect/Party/XML screen test
passed). Session diff is exactly three files: `app/build.gradle.kts` (version bump) and the two
accessibility-fix files — no scope creep into MVP-1.2 or public-release signing. Version bumped
`continuity.25`→`continuity.26` (versionCode 26→27), the single MVP-1.1-E candidate bump, made
only after all regression gates passed clean.

Full detail including the freeze-audit table and human-acceptance checklist:
`docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E.
Prior milestones (MVP-1.1-A through D): same file, Parts A–D.
Prior sessions (pre-signing technical-debt closure, public-release prep, UI/UX polish): Development
Ledger phases 17-19, unchanged this session.

## 4. Public release blocker (unrelated to MVP-1.1, unchanged)

No Windows code-signing certificate and no Android release keystore exist anywhere in this
repository/environment. Android public Play-Store `applicationId` also undecided. Full detail:
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. Not touched by MVP-1.1 work.

## 5. Android install status

Connected device `I2407i` (serial `10BF44124K000E3`) still has **no genuine pre-existing BUDCOM
installation** — checked both at this session's start and again immediately before the final
candidate build; only transient `connectedDebugAndroidTest` test-harness packages exist, recreated
fresh by each test run. Per explicit governing instruction ("the target must already contain
BUDCOM data or otherwise be clearly identified as the owner's existing BUDCOM phone"), **no install
was performed** (same finding as Parts A/B/C/D — this device has never qualified across the entire
MVP-1.1 arc). The owner's previously-approved `continuity.22` install elsewhere is untouched.

Final MVP-1.1 candidate, if the owner wants it installed somewhere specific:
`apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`
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

**MVP-1.1 is complete and frozen (§1). The next development milestone is MVP-1.2 — planning/
recovery review only, not started this session, per explicit instruction.**

Two independent items from prior sessions remain open and unaffected by this session, neither
blocking the other and neither blocking the MVP-1.2 planning/recovery review:

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Decide whether/where to install `continuity.26`** for a genuine live smoke test against a
   real paired Tally company, including exercising a real Tally XML import by hand (see
   `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E §E14 for the human acceptance checklist).
   No device currently qualifies for a standing install (§5).

**Not started:** MVP-1.2 substantively; external/public distribution; MVP-1.3+. Do not begin
distribution before signing exists and the product owner explicitly authorizes it. Do not begin
MVP-1.2 substantively without an explicit go-ahead — the exact next task is a planning/recovery
review of MVP-1.2 scope, not implementation.
