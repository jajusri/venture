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
**MVP-1.3 STATUS: COMPLETE / FROZEN (2026-08-18).** Parts A, B, and C (integrated hardening +
freeze) are all complete. The five product decisions (per-company scope, initial field set, no
provenance/no Tally round-trip, app-private logo storage behind an abstraction, owner-side only)
were supplied directly in this session's governing prompt and recorded as
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` **PDL-019**. Part A shipped the `business_profile`
table (`MIGRATION_9_10`), full repository/domain/logo-storage layers, and a basic whole-entity
owner-side editor wired into the Dashboard. Part B shipped the logo picker/display UI, a
self-caught cross-company async-race fix, and the internal sharing-foundation data boundary
(`BusinessProfileShareSnapshot` — no actual share mechanism, deliberately). Part C is the
integrated A+B hardening review, the single coherent version bump, final candidate APKs,
installation, and smoke test. Full detail:
`docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Parts A–C;
architecture: `docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md` (its §3/§5 open
questions marked `RESOLVED — PDL-019`).
**PUBLIC RELEASE BLOCKED — SIGNING ONLY** (Windows code-signing + Android release keystore, neither
exists; Android `applicationId` also undecided — unrelated to and unchanged by MVP-1.1/1.2/1.3 work)
**ANDROID `0.1.1-continuity.28` (versionCode 29)** — the single coherent MVP-1.3 freeze candidate,
built and installed this session. **Installed** on device `I2407i` (`10BF44124K000E3`), verified
**after** the final `connectedDebugAndroidTest` run per this milestone's own explicit sequencing
requirement (that run's own known side effect removed the prior install; reinstalled and
re-verified, see §5).
**DESKTOP `0.4.18`** / **CONNECTOR `0.4.6`** — unchanged, not touched, and not required by MVP-1.3.

**Phase 36 — Ledger Sharing Discoverability + Offline Ledger Performance Hardening (focused
polish/performance task, not a milestone — no version bump).** Fixed the same
Load/Refresh-delegate-to-one-identical-method bug Phase 3E fixed for Vouchers, but on the Ledger
side: `LedgerRepositoryImpl.loadLedgers()` was network-first for every normal Ledger Browser open
(and every Universal Search keystroke against Ledgers), falling back to Room only on failure —
split into cache-only `listLedgers()`/network `refreshLedgers()`. Also made Detailed Ledger sharing
reachable by a normal tap (a "Ledger Summary / Detailed Ledger / More options…" menu) instead of
requiring a hidden long-press. 1,257/1,257 unit tests both variants (+8 from the 1,249/1,249
baseline), 0 lint errors, both assembles green; no device available in that session for live/
instrumented verification. Full detail: `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §24.

**Phase 37 — Ledger Local-First + Connect Real-Device Validation (device
`10BF44124K000E3`/I2407, real paired company `estimation`, 941 real Ledgers).** Closed Phase 36's
no-device limitation: live-confirmed sub-second, genuinely zero-network Ledger Browser open/
search/pagination, and a full offline test (WiFi disabled, verified via `dumpsys connectivity`)
with real cached data — search, pagination, and a full transaction-bearing statement all worked
identically offline. Found and fixed two genuine defects surfaced only by real data/real timing:
(1) `refreshLedgers()` returned the raw first network page instead of re-reading Room after
persisting a multi-page snapshot, visibly skipping several alphabetically-ordered ledgers after an
explicit refresh; (2) `SyncViewModel.runAll()` ("Run available syncs") only ever examined the
LAST of its three sequential outcomes for Party reconciliation, so a successful Ledgers sync's own
outcome (always first, never last) silently never triggered Connect auto-population outside a lone
per-target "Sync now" tap. Both fixed with regression tests, rebuilt, reinstalled, and
live-re-verified. Also found — and correctly left unfixed, out of Android scope — that Connect
still shows zero customers because the Desktop Connector's own local cache has never captured any
real ledger's Tally `parent_group`, a Connector/Tally-extraction gap, not a seeding-policy or
Android defect. 1,259/1,259 unit tests both variants, 0 lint errors, both assembles green, final
installed APK SHA-256 `a0e04343a0e48ace12f14fd6da9d0036a6894106842132e729c6fd23630ddf56`
byte-verified against the device. This also supersedes an earlier, now-confirmed-stale note in
this file (§7 prior revision) that device pairing had never been attempted — it plainly had been,
independent of this session. Full detail: `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §26.

**Phase 38 — Git Preservation, Connector `parent_group` Investigation, MVP-1.4 Planning
(documentation-only; no production code changed).** Pushed Phase 36/37's 4 commits to
`origin/main` as a clean fast-forward (`2555c1d..b7e9377`), confirmed no secrets and `HEAD ==
origin/main`. Traced Phase 37's Connector `parent_group` gap to its exact root cause: Connector
commit `8706a80` (2026-08-03) removed `PARENT` from the Ledgers/StockItems Tally FETCH request as a
workaround for TD-001 (illegal `&#4;` control characters); TD-001 was properly fixed at the shared
XML-parsing layer on 2026-08-16, but `PARENT` was never restored — and the current Connector test
suite shows this is a **deliberate post-fix defense-in-depth decision**, not an oversight. Per this
task's own instruction not to reverse an architecture-level decision unilaterally, **no Connector
fix was implemented** — recorded as new **TD-035** (`docs/technical-debt/registry.md`) with the
exact proposed fix, pending an explicit Product Owner/ChatGPT decision. Recorded the Prospect→
existing-Ledger linking capability in `docs/planning/BUDCOM-NOT-NOW.md` per the exact specified
concept, not implemented. Produced the MVP-1.4 planning/recovery review,
`docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md`, separating LOCKED scope (ownership
boundary, roadmap position, confirmed MVP-1.3 dependency) from PROPOSED direction (Master Plan §10's
own catalogue/SKU/branch-publish bullet list) from nine OPEN PRODUCT DECISIONS requiring their own
Brainstorm 1 — no MVP-1.4 implementation started. Full detail:
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §28.

**Phase 39 — TD-035 Permanent Resolution + MVP-1.4 Product Decision Lock (2026-08-19).** TD-035
resolved for Ledgers: proved the trust boundary safe first (a parsed `parentGroup` is never fed back
into any outbound Tally XML/TDL request anywhere in the Connector, ruling out the injection vector
that motivated the original defense-in-depth exclusion), then restored `'PARENT'` to
`MasterDataTemplates.ledgers()`'s FETCH list, added 18 permanent adversarial regression tests
(`&#4;`/hex/literal-0x04, Unicode, unusual punctuation, long values, nested-tag-looking escaped text,
empty/absent PARENT), and added a centralized `LedgerGroupClassification`
(DEBTOR/CREDITOR/OTHER/UNKNOWN) plus company-isolation adversarial tests for
`ReconcilePartiesFromLedgersUseCase`. StockItems' identical `PARENT`/`BASEUNITS`/`GSTAPPLICABLE`
exclusion deliberately left untouched (out of scope; its own future safety investigation required).
Connector: 159/159 files, 1,437/1,437 tests, `tsc`/`eslint`/build all clean. Android: 1,266/1,266
tests both variants (+7), `lintDebug` 0 errors (87 pre-existing warnings), both assembles green.
Separately, locked all nine MVP-1.4 product decisions as **PDL-020** (SKU identity; Stock Item
relationship — BUDCOM-owned presentation entity referencing Tally, no write-back; lifecycle Stock
Item → Draft → Review → Published; Excel as one canonical interchange contract, not the operational
database; asset storage reusing the Business Profile abstraction pattern; visitor-facing Resources
deferred but architecturally provisioned; no Desktop surface; sharing via the existing Android
mechanism; strict company isolation), and updated
`docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md` §5.3 to mark all nine `RESOLVED —
PDL-020`. No real-device re-verification was performed (no `adb` run this session) — the fix is
proven safe at the code/test level; its live effect on Connect's customer count was not re-checked
on the physical device. **No MVP-1.4 implementation was started** — this session's authorized scope
was investigation, resolution, decision-lock, and documentation only. Full detail:
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §29; `docs/technical-debt/registry.md` TD-035;
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-020.

**Phase 40 — Desktop Startup & Connection Stabilization (focused polish/hardening task, no version
bump).** Fixed two real Desktop launch/connection defects found by direct code audit (not a
rewrite): (1) `createMainWindow()` used `show: true` alongside a redundant `ready-to-show` handler,
showing the OS's default blank/white window frame before the renderer's first paint — fixed via
`show: false` + a themed `backgroundColor` matching `main.css`'s `--bg` token; (2) the renderer's
`onStatusUpdated` push-listener was registered only after the initial dashboard/company pull
completed, so a fast lifecycle transition during that pull could be silently dropped and leave the
UI stuck on a stale state (e.g. "Starting connector…") indefinitely on relaunch with an
already-selected company — fixed by registering the listener before that pull, relying on
`refreshUi()`'s existing reentrancy guard rather than any new locking/delay. Desktop: 713/713 tests
(+2 new regression tests), `tsc`/build clean. No Tally/Connector/Android/Catalogue scope touched; no
system tray exists in this codebase (confirmed by audit, not assumed). Full detail:
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §30.

**Phase 40 follow-up (2026-08-22) — pushed + real launch validation.** User reviewed and approved
the three Phase 40 commits; pushed to `origin/main` as a clean fast-forward
(`2feb9b2..4b75ed7`), confirmed local `HEAD` byte-identical to `origin/main`
(`4b75ed7f59383aff7add0a7923da64d6711df571`). Performed real Desktop launch validation on the actual
development machine using the repository's own isolated probe-mode mechanism
(`BUDCOM_INSTALLED_PROBE_MODE`/`BUDCOM_USER_DATA_DIR`) — four independent launches of the dev build
against a real, live TallyPrime instance, fully isolated from (and never touching) an already-running
production Desktop instance found on the machine. Real timestamp evidence confirmed the window stays
hidden for the entire gap before `ready-to-show` (the fixed flash window) and that `'starting' ->
'connected'` genuinely happens in ~1.05–1.07s with company discovery completing ~100ms later — the
exact race window the second fix closes, proven real rather than hypothetical. Two targeted,
process-scoped screenshots (never full-screen, to avoid capturing unrelated content on a live machine)
confirmed correct, non-flashing, honestly-stated rendering at both the pre-connection and Connected
states. No defect was found; no further code change was made. Full detail:
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §30-H.

**Phase 41 — Desktop UX Polish (focused polish task, no version bump).** Fixed five real Desktop
daily-use UX defects: (1) the Dashboard "Sync"/"Last Sync" fields (header + card) were wired to a
dead Connector placeholder service and to session-validation time, never actual Ledger/Stock Item
sync freshness — replaced with a real freshness summary sourced from the already-existing
`getLedgerStatistics()`/`getStockItemStatistics()` endpoints (newly exposed through the preload
bridge); (2) a failed Ledger/Stock Item refresh wiped the previously-shown list and stats instead of
preserving them; (3) `loadLedgers()`/`loadStockItems()` had no reentrancy guard against rapid
repeated Refresh/pagination/search, unlike the established `loadCompanies()` pattern; (4) the header
connection-indicator dot carried a static, non-updating `aria-label` instead of being `aria-hidden`
like the footer's correctly-implemented equivalent; (5) empty Ledger/Stock Item lists showed a bare
blank area with no explanation. A self-hardening pass on the new code itself then caught and fixed
two more defects (a cross-company stale-data leak risk, and the freshness card overwriting a good
state with "Checking…" on a later transient failure) before any real validation was attempted.
**Real Desktop launch validation** (same isolated probe-mode methodology as the Phase 40 follow-up,
against the same live TallyPrime instance, zero disruption to the already-running production
instance) then caught a **third** genuine defect the reasoning-only pass had missed: the new
freshness card got stuck on an indefinite "Checking…" whenever no company was selected, because the
Connector's statistics endpoints require an active company and reject without one — a completely
normal, everyday state, not an error. Fixed by checking `hasActiveCompany()` before even attempting
the fetch; re-validated live afterward showing a clean "No company selected" instead of a stuck
spinner. Desktop: 731/731 tests (+15 new/changed), `tsc`/build clean throughout. No Android/Tally/
Connector-protocol/Catalogue scope touched. Full detail: `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`
§31.

**Phase 42 — Desktop UX Polish: Final Review, Real-Use Hardening & Next-Lineup Gate (review/hardening
task, no version bump).** Explicit continuation of Phase 41 with a "do not trust the previous
report — inspect the source fresh" mandate. Fresh-eyes re-audit of the full connection/health push
chain found a real, deterministic staleness gap: the Dashboard's connection card is refreshed only on
a coarse 5-value lifecycle transition or a handful of explicit user actions, never on every health
poll — but the Connector's own `/health` can legitimately report `'degraded'` (e.g. Tally itself
disconnects while the Connector process keeps running) without the coarse lifecycle changing, so the
Dashboard could silently keep showing a stale "Connected"/"Working normally" indefinitely. Fixed by
re-pulling dashboard state whenever the Dashboard view is (re-)activated, mirroring the same
fetch-on-activation pattern already used by every other view; deliberately not a new polling timer, to
avoid destabilizing `dashboard-recovery.test.ts`'s extensively fake-timer-tuned bounded-recovery
suite for a benefit that didn't justify the added architecture. Real-Desktop validation (same
isolated probe methodology, live TallyPrime instance) then caught a second genuine defect: the Sync
card/header duplicated the Company card's own "No company selected" wording verbatim, reading as if
sync itself had a status called "no company selected" — fixed to this app's own established "—"
placeholder convention. A third, cosmetic finding (the header's fixed 3-column badge grid overflowing
past the window's right edge at the app's own default 1200×800 size, in the same no-company-selected
state) was investigated with three rounds of CSS truncation attempts, none of which could be verified
to work via the screenshots available in this session — all three were honestly reverted rather than
shipped unproven; `main.css` carries zero diff. Desktop: 732/732 tests (+1 new, 2 updated for the
wording fix), `tsc`/`lint`/build clean throughout. No Android/Tally/Connector-protocol/Catalogue scope
touched. **MVP-1.4 readiness gate: A — READY** (the one open cosmetic defect affects only a
secondary, redundant header badge, not the primary Dashboard cards). Full detail:
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §32.

**Phase 43 — Adaptive Tally Synchronization Strategy: Research, Architecture & Product Decision
Research (read-only research task, no production code touched).** Investigated whether an adaptive
(frequent-when-active, backed-off-when-idle) Tally sync strategy is feasible, by direct source
inspection of Desktop, Connector, Android, and the project's own technical-debt history rather than
assumption. Headline finding: **no automatic sync exists anywhere in BUDCOM today** — the Connector's
own `SchedulerService` is a literal no-op placeholder, Desktop has no data-sync timer, and Android has
zero `WorkManager`/`AlarmManager` usage; every sync on every platform is manual and always a full
extraction. **No cheap pre-extraction "did Tally change" signal exists either** — Tally's `ALTERID` is
already captured per-record but only inside the same full pull, and the project's own TD-006 already
documents that a reliable resume/watermark mechanism isn't safe to assume (Tally's export order isn't
guaranteed, no snapshot identity exists). The investigation also surfaced two genuine,
previously-undocumented company-isolation gaps in in-memory (not database) sync state — one in the
Connector's ledger/stock-item sync services (process-wide singletons, not keyed by company), one in
Android's `SyncRepositoryImpl` (a `clearActiveIfCompanyChanged()` method that exists but is dead code,
never called) — documented as prerequisite fixes for the next task, not fixed here. Produced
`docs/architecture/BUDCOM-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md`: a staged time-based backoff state
machine (preserving the original 15/60-minute concept, refined into a 15→30→60-minute ladder) as the
recommended near-term default, with the originally-hoped-for cheap-change-detection strategy honestly
kept as a separately-scoped, unvalidated **OPEN** future investigation rather than assumed. No
Android/Desktop/Connector/Tally-protocol production code touched; no schema change; no version bump;
MVP-1.4 not started. Full detail: `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §33.

## 2. Branch / HEAD

- Branch: `main`.
- Starting HEAD of the MVP-1.2-D session: `d964b760e90339d3037a3447bbffa85e116e4801`.
- HEAD after MVP-1.2-D: `57a27e44f813aede9e5b71dd5a43440380ed670c` (4 commits: data/domain/
  repository, presentation/navigation/Dashboard, tests, documentation).
- Starting HEAD of the MVP-1.2-E session: `57a27e44f813aede9e5b71dd5a43440380ed670c`, working tree
  clean — confirmed by direct inspection (`git status`, `git log`, `DatabaseConstants.VERSION`,
  presence of all 8 `feature/dincharya/` files), not trusted from documentation alone.
- HEAD after MVP-1.2-E (before this push-confirmation commit): `d9af6f7623f66d8d028d3ee61d2d3fa79a91ceb9`
  — three commits: `7d6a931` (`fix(android)`: note-completion UI wiring), `ffb9d99`
  (`test(android)`: new tests + version bump), `d9af6f7` (`docs`: this checkpoint + ledger phase 32
  + specialist status Part E).
- Android: version bumped `0.1.1-continuity.26`→**`0.1.1-continuity.27`** (versionCode 27→28) — the
  single coherent MVP-1.2 freeze bump, performed only after full A–D+E regression was green (per
  explicit instruction, not before).

### 2a. Push status (MVP-1.2-D/E)

**PUSHED — confirmed byte-identical.** `git push origin main` completed as a normal fast-forward,
no force flag, no history rewrite: `d964b76..7732844 main -> main` (the final push included one
small follow-up documentation commit, `7732844`, recording this exact confirmation). Post-push
verification via `git fetch origin` + `git rev-parse`: local HEAD and `origin/main` both resolved to
`773284485e03028508e89b8f84ebeda54a1dac3b` — exact match, working tree clean. This preserved the
entire MVP-1.2-D + MVP-1.2-E work (8 commits total) on the remote.

### 2b. MVP-1.3 planning/recovery review session

- Starting HEAD: `773284485e03028508e89b8f84ebeda54a1dac3b` — confirmed to exactly match this task's
  own expected baseline; working tree clean; zero untracked files; local HEAD byte-identical to
  `origin/main` before any action was taken.
- This is an explicitly **read-only reconnaissance session** — no production code, migration, test,
  APK, or version bump was touched. The only repository changes are three documentation files: the
  new `docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`, a correction to
  `docs/design/BUDCOM-SCREEN-INVENTORY.md` (five stale-missing screens added), and this file plus
  the Development Ledger (phase 33).
- **Not pushed.** This task did not explicitly authorize a push for this planning session (unlike
  MVP-1.2-E, which did) — the documentation commit(s) remain local-only, ahead of `origin/main`,
  until explicitly authorized. See the session's own final report for the exact resulting HEAD and
  commit hash.

### 2c. MVP-1.3 implementation session (Parts A, B, C)

- Starting HEAD: `3166d3388d79e17828fb991b93a1f42336f99834` (the planning session's own final commit),
  working tree clean.
- HEAD after Part A: `3f08c56` (4 commits: data/domain/storage, presentation/navigation/Dashboard,
  tests, documentation).
- HEAD after Part B: `cb585fa` (4 commits: data/domain (logo resolution + share snapshot),
  presentation (logo UI + race fix), tests, documentation).
- HEAD after Part C (before this push-confirmation commit): `7452703` — two commits: `3bef56f`
  (`chore(android)`: version bump to `0.1.1-continuity.28`/versionCode 29), `7452703` (`docs`: Part
  C integrated hardening + freeze, MVP-1.3 COMPLETE / FROZEN).
- Android: version bumped `0.1.1-continuity.27`→**`0.1.1-continuity.28`** (versionCode 28→29) — the
  single coherent MVP-1.3 freeze bump, performed only after Parts A+B's regressions and this
  freeze part's own fresh full regression were all green.

### 2d. Push status (MVP-1.3 A/B/C)

**PUSHED** — explicitly authorized by this task's own "pushing should occur only at the final
freeze" instruction, now reached (unlike the read-only planning session in §2b, which was not
authorized to push). `git push origin main` completed as a normal fast-forward, no force flag, no
history rewrite. Post-push verification via `git fetch origin` + `git rev-parse` confirmed local
HEAD and `origin/main` resolve to the identical commit, working tree clean. See the session's own
final report for the exact resulting hash.

### 2e. Push status (Phase 39 / TD-035 + PDL-020)

**PUSHED (2026-08-19, explicit user request following the Phase 39 report).** Starting HEAD `141b92d`
(Phase 38's own final commit, already on `origin/main`); this session's own final report initially
left three new commits (`392e324`, `772e157`, `2feb9b2`) local-only pending explicit push
authorization, per the governing task's own "not pushed" default. The user then explicitly asked to
push. `git push origin main` completed as a normal fast-forward, no force flag, no history rewrite:
`b7e9377..2feb9b2 main -> main`. Post-push verification via `git fetch origin` + `git rev-parse`
confirmed local `HEAD` and `origin/main` both resolve to `2feb9b2bddc2838efafaf7de61e90e1ec33ffbc0`,
working tree clean.

### 2f. Push status (Phase 40 + real launch validation)

**PUSHED (2026-08-22, explicit user request following review/approval of the Phase 40 commits).**
Starting HEAD `4b75ed7` (Phase 40's own final commit, three commits ahead of `origin/main`'s then-tip
`2feb9b2`). Re-verified branch/HEAD/clean-tree/commit-contents directly before acting — no unrelated
changes found. `git push origin main` completed as a normal fast-forward, no force flag, no history
rewrite: `2feb9b2..4b75ed7 main -> main`. Post-push verification via `git fetch origin` + `git
rev-parse` confirmed local `HEAD` and `origin/main` both resolve to
`4b75ed7f59383aff7add0a7923da64d6711df571`, working tree clean. Real Desktop launch validation was
then performed against this exact pushed state (four isolated probe-mode launches against a live
TallyPrime instance, zero disruption to an already-running production Desktop instance found on the
machine) — see `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §30-H for full detail. No defect found, no
further commit was necessary, so `HEAD` remains `4b75ed7` (== `origin/main`) after this follow-up.

## 3. Recent session work (MVP-1.2-B through MVP-1.3-C)

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

**MVP-1.3 planning/recovery review, complete (2026-08-18).** Read-only reconnaissance only — no
implementation. Verified the MVP-1.2 freeze baseline directly (matches this task's own expected
values exactly, §2b). Read the full authoritative document set and searched the entire repository
plus the external design archive for any Business-Profile/Catalogue groundwork — found none (zero
code anywhere), but real, locked architecture-sequencing/ownership-shape groundwork does exist
(Master Plan §9, UI Decisions §7). **Central finding:** the milestone's *detailed* scope requires
Brainstorm 1 (User + ChatGPT, per the Modus Operandi's own mandatory development cycle), which has
not happened for this milestone — so this session produced a complete planning package (gap-analysis
matrix, five explicit open product decisions ranked by risk, a candidate not-locked architecture, a
proposed sub-milestone structure) as *input* to that brainstorm, rather than guessing at scope or
beginning implementation. One documentation-staleness defect found and fixed along the way:
`BUDCOM-SCREEN-INVENTORY.md`'s screen table had been stale since before MVP-1.1-B (missing five
shipped screens), corrected directly. Full detail:
`docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`; ledger: phase 33.

**Part A — MVP-1.3-A Business Identity Foundation, implemented and complete (2026-08-18).** The
five product decisions (per-company scope, initial field set, no provenance/no Tally round-trip,
app-private logo storage behind an abstraction, owner-side only) were supplied directly in this
session's governing prompt, recorded as PDL-019. New `business_profile` table
(`MIGRATION_9_10`, one row per `companyId`), full repository/domain layer, and a genuine, fully
tested `BusinessProfileLogoStore` abstraction (file-type allowlist, 5 MB streaming size cap,
two-layer path-traversal defense) even though no picker UI exists yet — deliberate, since
`logoAssetPath` is a real schema column A introduces (full reasoning: specialist doc §A3). Basic
whole-entity owner-side editor wired into a fifth Dashboard entry. One self-caught defect fixed
before any test ran (a Cancel-reverts-to-unsaved-not-saved-data bug). One genuine instrumented-test
discrepancy investigated and resolved at the test level, not dismissed as assumed flakiness (two
new failures traced to Save/Cancel buttons sitting below the fold in an 11-field scrollable form on
this specific device — fixed with `performScrollTo()` in the test, confirmed by isolating and
comparing against an already-passing equivalent screen). 17 new JVM tests + 18 new instrumented
tests, with dedicated adversarial company-isolation coverage at the DAO, repository, and ViewModel
layers. `testDebugUnitTest`/`testReleaseUnitTest` 1,255/1,255 both; both lints 0 errors (report-XML
verified); all three assembles green; instrumented suite 329/341, the unchanged 12-failure
baseline, zero overlap. Zero network/Connector/Desktop/manifest touch (grep-verified). Version not
bumped (deferred to Part C's freeze). Continuing automatically to Part B in this same session, per
this task's own "continue A → B → C automatically when the gate passes" instruction. Full detail:
`docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Part A; product decisions: PDL-019.

**Part B — MVP-1.3-B Profile Presentation & Sharing Foundation, implemented and complete
(2026-08-18).** Logo picker (`ActivityResultContracts.PickVisualMedia`, the system Photo Picker, no
runtime permission) and display (`BitmapFactory.decodeFile` off the main thread, same pattern as
`PdfPreviewScreen`) with Add/Replace/Remove controls and plain-language failure messages.
`BusinessProfileShareSnapshot` — the internal, PDL-019-mandated "sharing foundation" data boundary:
a pure, side-effect-free business-card projection excluding `companyId`/timestamps/the raw logo
path, with zero Intent/export/network wired to it anywhere (deliberately — a real share feature is
explicitly reserved for a future Vartalap milestone). **Self-caught defect fixed:** `save()`/
`updateLogo()`/`clearLogo()` each ended by unconditionally applying their async result to
`_uiState`, so switching companies while one was still in flight could let a stale result silently
overwrite the newly-loaded company's state — fixed with an `updateIfStillOnCompany()` guard,
locked in by two dedicated race-condition regression tests using a controllable
`CompletableDeferred` gate. 15 new tests (11 JVM + 4 instrumented). `testDebugUnitTest`/
`testReleaseUnitTest` 1,266/1,266 both; both lints 0 errors; all three assembles green including
R8-minified release; instrumented `BusinessProfileScreenTest` 12/12 clean. Version not bumped
(deferred to Part C). Full detail: `docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Part B.

**Part C — MVP-1.3-C Integrated Hardening, Freeze, Final Candidate, implemented and complete
(2026-08-18).** Not a new feature — an integrated audit of Parts A+B together. Reviewed (not
fixed, deliberately accepted) two minor characteristics: Cancel doesn't revert an already-applied
logo change (logo actions apply immediately, not staged like text fields — matches common
profile-editor UX, Remove Logo gives an immediate undo), and a narrow cosmetic notice-message race
if a save and a logo update are both in flight simultaneously (data integrity unaffected, B5's
guard already covers it). Zero new defect found. Version bumped to `0.1.1-continuity.28`/
versionCode 29 — the single coherent MVP-1.3 freeze bump. Full post-bump regression:
`testDebugUnitTest`/`testReleaseUnitTest` 1,266/1,266 both; both lints 0 errors (this session's
first genuinely-cold lint analysis since MVP-1.2's freeze, which surfaced a known JIT-compilation
tooling pathology — investigated via thread dump, confirmed to be real-but-slow CPU-bound work
rather than a hang, worked around session-locally with `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`,
never written into any tracked project file); all three assembles green; instrumented suite
333/345, the unchanged 12-failure baseline (345 = 341 from A + 4 new from B, confirming no test
lost or duplicated). Built both candidate APKs (§5 for exact hashes/sizes). Installed the debug
APK on device `10BF44124K000E3` and smoke-tested it directly (§5) — launch clean, correct honest
first-run Secure Pairing state, zero crash, back-navigation correct, relaunch clean; Dashboard/
Business Profile/Connect/Dincharya explicitly **not** exercised on-device (no real Tally Connector
pairing available in this environment) — verified only through the automated instrumented suite
instead, stated honestly as weaker evidence than genuine on-device navigation. Device install state
was explicitly re-verified **after** the final `connectedDebugAndroidTest` run per this milestone's
own explicit sequencing requirement (that run's side effect had indeed removed the prior install;
reinstalled and re-verified, not silently assumed). Full detail:
`docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Part C.

**MVP-1.3 COMPLETE / FROZEN.**

## 4. Public release blocker (unrelated to MVP-1.1/1.2, unchanged)

No Windows code-signing certificate and no Android release keystore exist anywhere in this
repository/environment. Android public Play-Store `applicationId` also undecided. Full detail:
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. Not touched by MVP-1.1 or MVP-1.2 work.

## 5. Android install status

**INSTALLED — the MVP-1.3 freeze candidate (2026-08-18).** Device `I2407i` (serial
`10BF44124K000E3`) had the MVP-1.2 freeze candidate (`continuity.27`/versionCode 28) installed
entering this session. This session's own `connectedDebugAndroidTest` runs (Parts A/B/C, per this
milestone's own explicit instruction to verify install state **after**, not merely before, any
connected-test run) removed it each time via that gate's own documented post-test uninstall
behavior — exactly the same structural property already documented for MVP-1.2-D/E, re-confirmed
rather than assumed. The final Part C install below is the one that matters for current state.

**Install performed this session (Part C, final):** verified via `adb shell pm list packages
com.budcom.android` that the app was indeed absent after the final `connectedDebugAndroidTest` run
(not assumed), then `adb install -r apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`
→ `Performing Streamed Install / Success`. Confirmed via `dumpsys package
com.budcom.android.debug`: `versionCode=29`, `versionName=0.1.1-continuity.28`,
`firstInstallTime == lastUpdateTime` (a genuine fresh install).

**Smoke test performed directly on device** (full detail: specialist status doc Part C §C7):
launch succeeded (`MainActivity` became `mFocusedApp`); rendered the correct, honest first-run
**Secure Pairing** screen (screenshot-confirmed, matching the established dark theme, no visual
defect); zero `FATAL EXCEPTION`/`AndroidRuntime:` logcat entries across launch, navigation, and
relaunch; Back navigation correctly returned to the home launcher (root-activity behavior, not a
crash — app process stayed alive, `com.android.launcher3` became `mFocusedApp`); relaunch
succeeded cleanly. **Explicitly not exercised on-device:** Dashboard, Business Profile entry/
screen, Connect entry, Dincharya entry — this environment has no real paired Tally Connector to
complete Secure Pairing against, and per explicit instruction no attempt was made to fake or
bypass pairing. These surfaces are verified only through the automated instrumented Compose test
suite (real Room/real Compose on this same device, synthetic state) — a distinct and weaker form
of evidence than genuine on-device navigation, stated as such rather than conflated with it.

**Update (Phase 37, `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §26):** the "no real paired Tally
Connector" premise above no longer holds — a real, already-paired `estimation` company (941
Ledgers, 247 Vouchers) was reached and genuinely exercised on this same device in that later
session, including Dashboard, Ledger Browser/Statement, Sync, and Connect. Business Profile and
Dincharya specifically were not separately re-checked in that session. This paragraph is left
otherwise unedited as the historical record of what that earlier session's own environment
supported.

**Debug APK (as of the MVP-1.3 freeze candidate; superseded by two later, same-version rebuilds —
see Phase 36/37 in the Development Ledger for their own SHA-256s):** `apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk` — 14,562,380 bytes —
SHA-256 `601b248fb07da27da2d805ab4ed8c06681b02e3c01eaeab22fc3a2994896902f` — `com.budcom.android.debug`,
versionCode 29, versionName `0.1.1-continuity.28`, debuggable (confirmed via `aapt dump badging` on
the actual built APK, not source config alone).

**Release (unsigned, verification only, never a public-release artifact):**
`apps/budcom_android/app/build/outputs/apk/release/app-release-unsigned.apk` — 2,536,316 bytes —
SHA-256 `dd68728630fb29f54b38ffb60aa8ca8264339810c9b2a7b9921c54b56be94a67` — `com.budcom.android`
(no debug suffix), same version, not debuggable, unsigned (no signing credentials exist anywhere in
this repository — unchanged blocker, §4).

**Structural note carried forward for future sessions:** `connectedDebugAndroidTest` will remove
whatever debug build is on this device every time it runs — a property of this project's chosen
test-gate mechanism on a real persistent-install device, not something this session "fixed" at the
tooling level. Future sessions should expect the same and plan to reinstall as a final step (exactly
as this session did), or consider moving that gate to a disposable emulator/secondary device if a
persistently-installed candidate needs to survive test runs.

**Update (Phase 39 follow-up, TD-035 build install, 2026-08-19):** rebuilt `assembleDebug` from HEAD
`2feb9b2` (TD-035 fix + `LedgerGroupClassification`, no version bump — versionCode/versionName
unchanged at 29/`0.1.1-continuity.28`, same as the previously-installed build) and reinstalled over
device `10BF44124K000E3` via `adb install -r`. Verified via `dumpsys package
com.budcom.android.debug`: `lastUpdateTime` advanced to `2026-08-19 07:48:12` while
`firstInstallTime` (`2026-08-18 18:29:20`) stayed unchanged — a genuine update over the existing
install, not a fresh one (this is a same-version content update, so `firstInstallTime ==
lastUpdateTime` does not apply here as it did for a true fresh install). Launched via `adb shell
monkey -p com.budcom.android.debug -c android.intent.category.LAUNCHER 1` (the plain `am start
-n .../MainActivity` component name used by earlier sessions no longer resolves — the real launcher
activity is `com.budcom.android.app.MainActivity`, not `com.budcom.android.MainActivity`; recorded
here for future sessions) — confirmed `mFocusedApp` became
`com.budcom.android.debug/com.budcom.android.app.MainActivity`, no `FATAL EXCEPTION`/`AndroidRuntime`
crash in logcat (two benign vendor SELinux `avc: denied ... proc_fas` warnings present, a known
OEM/vivo-kernel artifact unrelated to app code, seen before on this same device). Deeper in-app
navigation (Dashboard/Connect/Ledger/Dincharya/Business Profile) was **not** re-exercised — this was
a launch-only smoke check, not a full walkthrough. New debug APK:
`apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk` — 15,185,874 bytes — SHA-256
`7fd36811d69bb5565d838020af4f79f4f7a29f14558a0bb2513454d6caed4592` (supersedes the `601b248f…`
build above as the byte-verified on-device state; the size/hash change vs. the MVP-1.3 freeze build
reflects the TD-035 Android code addition, not a version bump). Commits `392e324`/`772e157`/`2feb9b2`
(this session's three) also pushed to `origin/main` as a clean fast-forward
(`b7e9377..2feb9b2`), confirmed via `git fetch` + `git rev-parse` that local `HEAD` and `origin/main`
resolve identically.

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
evidence in `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts A–E).
MVP-1.3 is COMPLETE / FROZEN (§1) — Parts A, B, and C all complete, documented, tested,
version-bumped, built, installed, and smoke-tested (§3/§5; full evidence in
`docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Parts A–C). The five product decisions this
milestone needed were supplied directly in its own governing prompt and locked as PDL-019.**

**Phase 36 (Ledger Sharing Discoverability + Offline Performance Hardening — §1 above,
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §24) and Phase 37 (Ledger Local-First + Connect
Real-Device Validation — §1 above, `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §26) are both
complete** — bounded, explicitly-authorized side-quests, not a new milestone. Neither changes the
item below.

**Phase 38 (Git Preservation, Connector Investigation, MVP-1.4 Planning — §1 above,
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §28) is complete.** Phase 36/37's commits are pushed
(`origin/main` == local `HEAD`). The Connector `parent_group` gap (Phase 37 §26.C) was root-caused as
TD-035 — see Phase 39 immediately below for its resolution.

**Phase 39 (TD-035 Permanent Resolution + MVP-1.4 Product Decision Lock — §1 above,
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §29) is complete. This supersedes both items Phase 38 left
open.** TD-035 is **RESOLVED for Ledgers** (`docs/technical-debt/registry.md`) — the trust boundary
was proven safe before any fix was written, so this was a Product Owner/ChatGPT-approved *reversal*
of the prior defense-in-depth decision, not a unilateral one; StockItems' identical exclusion remains
open and out of scope. All nine of `docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md` §5.3's
open product decisions are now locked as **PDL-020** — MVP-1.4 Brainstorm 1's output has been
supplied and recorded; that document's §5.3 no longer needs a separate brainstorming pass.

**Next task: an MVP-1.4-A implementation prompt.** PDL-020 now provides implementation-ready scope for
§5.3's nine questions; `docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md` §12's candidate
1.4-A/B/C sub-milestone split remains the structural starting point. **Do NOT begin MVP-1.4
implementation from this document alone** — Phase 39's own governing task explicitly did not
authorize implementation; a separate, explicit go-ahead is still required before writing any
Catalogue code, table, or screen.

**Independently, not blocking MVP-1.4:** StockItems' `PARENT`/`BASEUNITS`/`GSTAPPLICABLE` TDL-fetch
exclusion remains open (`docs/technical-debt/registry.md` TD-035) and would need its own safety
investigation — mirroring Phase 39 §B's methodology — before any Catalogue feature groups products by
Tally Stock Group.

Two independent items from prior sessions also remain open, unaffected by and not blocking the
above:

1. **Obtain and configure production signing credentials** — the sole remaining blocker to public
   release, unrelated to and unchanged by MVP-1.1/1.2/1.3 work (see §4 and the gate matrix for exact
   steps). Human/external action; do not perform unilaterally.
2. **Exercise a real Tally XML import by hand**, per the human acceptance checklist in
   `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E §E14 — **partially superseded by Phase 37**
   (`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §26): device pairing is confirmed genuinely done
   (device `10BF44124K000E3` against a real, already-paired `estimation` company with 941 real
   Ledgers and 247 real Vouchers, not out of scope as this line previously and incorrectly stated),
   and Dashboard/Ledger Browser/Ledger Statement/Sync/Connect were all genuinely exercised
   on-device with real data during that session. Not independently re-checked against §E14's own
   exact enumerated steps or against Business Profile/Dincharya specifically — treat this item as
   narrowed, not closed, until someone walks §E14 itself end-to-end.

**Not started:** MVP-1.4 (planning/recovery review is the next authorized step, not implementation);
external/public distribution. Do not begin distribution before signing exists and the product owner
explicitly authorizes it.
