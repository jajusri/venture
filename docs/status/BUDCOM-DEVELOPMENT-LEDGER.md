# BUDCOM Development Ledger

**Status:** Canonical historical/audit record

**Reconstructed:** 2026-08-17 from repository and Git evidence

**Product-code baseline:** `e20053f` (`main`)

**Current-state companion:** `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`

## 1. Purpose and evidence rules

This ledger explains how BUDCOM reached its present state. It is the durable development history;
the companion checkpoint answers only “where are we now?” Planning documents remain plans, the
technical-debt registry remains the detailed TD authority, and specialist evidence remains in its
original files.

Repository filenames, contents, and all-history Git paths were searched before this file was
created. No prior canonical Development Ledger, deleted ledger, or renamed equivalent was found.
`docs/PROJECT_PROGRESS.md`, milestone/stage updates, `docs/REPOSITORY_EVOLUTION.md`, the refinement
register, and the Controlled-Pilot closure document are useful predecessors for particular eras,
but none covers the complete history and none is superseded or deleted by this ledger.

Evidence priority is Git/source/tests, then the current checkpoint and later physical-validation
records, then older status/planning material. **IMPLEMENTED** does not imply physical validation;
**AUTOMATED-VALIDATED**, **PHYSICALLY VALIDATED**, and **HUMAN VISUALLY APPROVED** are asserted only
where recorded evidence exists. Earlier documents are not silently rewritten when later evidence
changes their conclusion.

## 2. Current executive state

| Area | Current evidence classification |
|---|---|
| MVP-1 | **CONTROLLED-PILOT VALIDATED** and Controlled-Pilot **GO**; bounded MVP-1 UI/UX polish implemented. **MVP-1 PUBLIC RELEASE PREPARATION complete (2026-08-17)** — TD-033/TD-034 resolved (part 1); **PRE-SIGNING TECHNICAL-DEBT CLOSURE also complete (2026-08-17, part 2)** — TD-004/023/025 fixed, TD-026 corrected, TD-009/021/022/027 reviewed and refined, several hygiene fixes shipped. Public release itself remains BLOCKED on signing only (see gate matrix) — no additional non-signing defect found this session that would add a new blocker. |
| Android | **IMPLEMENTED / AUTOMATED-VALIDATED / PHYSICALLY VALIDATED / HUMAN VISUALLY APPROVED**. Owner-approved installed candidate: `0.1.1-continuity.22`, versionCode 23. Unsigned `assembleRelease` also verified this session (release pipeline works; no signing credentials exist). |
| Desktop | `0.4.17` **INSTALLED-RUNTIME FAILED**. Corrected `0.4.18` **INSTALLED / HUMAN VISUALLY & RUNTIME APPROVED** by owner (2026-08-17, this session). `0.4.19` (TD-033/034 fixes) packaged as an unsigned candidate, not installed over the approved `0.4.18`. |
| Connector | **IMPLEMENTED / AUTOMATED-VALIDATED / CONTROLLED-PILOT VALIDATED**. Version `0.4.6`; read-only Tally boundary, SQLite snapshots, trust/discovery and private-storage participation are in place. |
| Controlled Pilot | **CLOSED / GO** on 2026-08-16 under the documented stable-router, fixed-Private-storage and signing constraints. Documentation does not reopen it. |
| UI/UX polish | Android pass **HUMAN VISUALLY APPROVED** by owner on 2026-08-17. Desktop **HUMAN VISUALLY & RUNTIME APPROVED** by owner on 2026-08-17 (this session) after the `0.4.18` correction. |
| Public release | **BLOCKED — signing.** No Windows code-signing or Android release-signing credentials exist. TD-033/TD-034 resolved this session; Android public `applicationId` flagged, not decided. See `docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`. |
| Git at reconstruction start | `main` at `bc9cd55`, 284 commits from the 2026-07-22 root, 185 commits ahead of `origin/main`; three known planning/product paths were untracked and left untouched. |
| MVP-1.1-A (Universal Party Foundation) | **TECHNICALLY COMPLETE, READY FOR REVIEW (2026-08-17).** Six new Room tables (Party, PartySourceLink, PartyFieldProvenance, PartyContactPerson, Tag, PartyTagAssignment) added via `MIGRATION_5_6`, verified schema-exact against Room's own generated `6.json` before being hand-written. Full repository/use-case layer, Alias-phone rule, field provenance, eligibility policy. 43 new tests (20 JVM classes + 23 instrumented, the latter run and passing on a connected physical device). No Connect UI built — see `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` for full detail. |
| MVP-1.1-B (Connect Browser + Customers/Prospects + accounting deep links) | **TECHNICALLY COMPLETE, READY FOR REVIEW (2026-08-17).** First user-visible Connect slice: Customers/Prospects browser reachable from a new Dashboard entry, local-first (zero network calls), classification-scoped search, Call/`ACTION_DIAL`, WhatsApp/`wa.me`, and deep links to the existing Ledger Statement (stable-id-based) and Voucher Browser (name-based, documented limitation) screens. 32 new tests (15 JVM + 17 instrumented, all passing on a connected physical device); full app instrumented suite 194/206 (12 pre-existing failures confined to files this session never touched, classified as a device-viewport artifact, not a regression). See `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part B for full detail. |
| MVP-1.1-C (Party Detail + Prospect creation + Contact Persons + Tags + Notes/Activity) | **TECHNICALLY COMPLETE, READY FOR REVIEW (2026-08-17).** First slice where a user can edit Party data in BUDCOM: a five-section Party Detail screen (identity, Tally-confirmed fields with plain-language provenance, tags, contact persons, notes/activity), a minimal offline Prospect-creation flow, and full contact-person/tag/note CRUD — all local-Room-only, zero Connector calls, zero direct Tally writes. New `party_notes` table via `MIGRATION_6_7` (schema version 7). 63 new tests (40 JVM + 23 instrumented, all passing on a connected physical device); full app instrumented suite 217/229 (the same 12-failure device-viewport-artifact class documented in Part B §B20, in the same unrelated files, zero overlap with Part C). Version not bumped yet (deferred to after Part D per this session's own combined-milestone instruction). See `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part C for full detail. |
| MVP-1.1-D (Tally XML Enrichment Round-Trip) | **TECHNICALLY COMPLETE, READY FOR REVIEW (2026-08-17).** Closes the combined MVP-1.1-C+D session. BUDCOM can review pending/conflicted Tally-compatible fields, generate a Tally-compatible external IMPORTDATA XML file for manual import, record a lightweight field-names-only export audit trail, and re-check the Connector's already-synced ledger snapshot on explicit request to confirm or flag a conflict per field — never a direct Tally write. Closed a genuine pre-existing architectural gap along the way: Android had never called the Connector's already-implemented `GET /ledgers/{id}` detail endpoint, so there was no local ground truth for phone/email/address/GSTIN beyond a phone heuristic; added one new read-only `LedgerApi` method + `LedgerLiveDetailPort`, wired only into the explicit re-sync action. Six-field Tally-tag whitelist (`addressCity` deliberately excluded — no distinct Tally tag exists). New `party_export_events` table via `MIGRATION_7_8` (schema version 8). 49 new tests (37 JVM + 12 instrumented, all passing on a connected physical device); one genuine Compose crash (nested `LazyColumn` inside `verticalScroll`) found and fixed by the instrumented suite before commit. Full app instrumented suite 229/241 (same 12-failure pre-existing device-viewport class, zero overlap). Version bumped `continuity.24`→`continuity.25` (versionCode 25→26), the single coherent bump for the combined session. See `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part D for full detail. |
| MVP-1.1-E (Integrated Hardening, Acceptance & Freeze) | **COMPLETE — MVP-1.1 FROZEN (2026-08-17).** Integration audit (not new feature work) across the full A–D chain: navigation graph, Party identity/company-isolation natural keys, provenance gating, export-audit privacy, XML escaping, file/cache path-containment, migrations, live-read company scoping, offline behavior, and performance — all re-verified directly against production code, zero integration-level defect found (A–D's own mini-hardening evidence corroborated, not contradicted). One genuine accessibility defect found and fixed: two Material3 `Checkbox` controls (`PartyXmlExportScreen`'s field-selection checkbox, `PartyDetailScreen`'s contact-primary checkbox) had no accessible label — fixed additively with `Modifier.semantics { contentDescription = ... }`, zero click-handling or test-tag change. Fresh full regression this session: `testDebugUnitTest`/`testReleaseUnitTest` 1,165/1,165 (one incidental already-documented `VoucherRepositoryImplTest` flake, confirmed clean on retry); both lints 0 errors; all three assembles green; full instrumented suite 229/241 on device `I2407i`, the identical 12-failure pre-existing device-viewport class from Parts B/C/D, zero overlap with any Connect/Party/XML-export file including the two files this session edited. Device `I2407i` still has no BUDCOM package of any kind, so per the same governing rule as every prior sub-session, no install was performed. Version bumped `continuity.25`→`continuity.26` (versionCode 26→27) only after all regression gates passed. Session diff is exactly three files (version bump + two accessibility fixes) — no scope creep into MVP-1.2 or public-release signing. See `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E for full detail, including the freeze-audit table and human-acceptance checklist. |
| MVP-1.2-A (Structured Party Activity Foundation) | **TECHNICALLY COMPLETE, READY FOR REVIEW (2026-08-18).** Product decisions PDL-014–PDL-017 locked first (Relationship Timeline unification, Referral Tree deferred, Home Insights/OI deferred, OS notifications deferred), then `party_notes` extended with `type`/`dueAt`/`completedAt`/`issueId` and the new `party_issues` table added via `MIGRATION_8_9` (schema version 9) — the first migration in this codebase to `ALTER` an existing populated table's columns, verified byte-for-byte against a real generated `9.json`. Repository/use-case layer extended for typed note create/edit, due-date/completion toggling, and issue create/resolve/reopen; Party Detail's note dialog genuinely made into an add/edit dialog (mirroring `ContactPersonEditor`'s precedent) gaining a type picker, conditional due-date field, and issue picker. One self-caught defect (an edit-mode false affordance for voucher-relinking) fixed before commit. 24 new tests (14 JVM + 16 instrumented, all on a connected physical device); `testDebugUnitTest`/`testReleaseUnitTest` 1,179/1,179 both; both lints 0 errors; all three assembles green; full instrumented suite 245/257, the identical 12-failure pre-existing device-viewport class, zero overlap. A genuine screen-lock-during-long-run false alarm (53 failures on the first pass) was investigated and resolved via a device power-setting change, not accepted blindly. Version not bumped (deferred to a coherent milestone boundary). See `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part A for full detail. |
| MVP-1.2-B (Relationship Timeline) | **TECHNICALLY COMPLETE — ACCEPTANCE GATE PASSED (2026-08-18).** New `PartyTimelineDao` merges `party_notes`/`party_export_events` into one chronological, bounded/paged feed via a single SQL `UNION ALL`, replacing (per PDL-014) the flat Notes list's presentation on Party Detail — zero schema change, zero migration. 23 new tests (7 JVM + 16 instrumented) including dedicated adversarial company-isolation, same-timestamp tie-break, and a 350-row performance proof. `testDebugUnitTest`/`testReleaseUnitTest` 1,186/1,186 both; both lints 0 errors; all three assembles green; full instrumented suite 256/268, the identical unchanged 12-failure device-viewport class, zero overlap. Version not bumped. See `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part B for full detail. |
| MVP-1.2-C (Issue History) | **COMPLETE (2026-08-18).** Extended 1.2-B's `PartyTimelineDao` `UNION ALL` with two more arms reading `party_issues` live (`issue_opened`/`issue_resolved`) — never a separate record, so the Timeline and the new Issues section can never disagree. Party Detail gained an Issues section (open compact-then-expandable, resolved collapsed) with Resolve/Reopen actions and a Timeline issue-filter (reusing 1.2-B's `issueId` parameter). Zero schema change, zero migration. 29 new tests (15 JVM + 14 instrumented) with dedicated company-isolation and Timeline/Issue-consistency proofs. `testDebugUnitTest`/`testReleaseUnitTest` 1,201/1,201 both; both lints 0 errors; all three assembles green; full instrumented suite 274/286, the identical unchanged 12-failure device-viewport class, zero overlap. Version not bumped. Accepted limitation: reopening an issue does not retain a record of a past resolution (no append-only log, PDL-012). See `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part C for full detail. |
| B/C checkpoint: Git preservation + MVP-1.2-D readiness review | **PRESERVATION COMPLETE (2026-08-18).** 234 previously-local-only commits (spanning project inception through MVP-1.2-C) pushed to `origin/main` via a normal fast-forward (`7cf85ba..8f77cf6`), verified byte-identical post-push (`git fetch` + `rev-parse` both report `8f77cf63b7533f1418cb6208157969dee3022765`). B/C claims re-verified directly against source (not trusted from prior docs) before pushing — zero discrepancy found. **MVP-1.2-D READINESS REVIEW COMPLETE — IMPLEMENTATION NOT STARTED.** Read-only architecture/data-source/company-isolation/performance/test-matrix analysis recorded for the next session; zero production code, migration, or version change made. See `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` "Checkpoint" section for full detail. |

## 3. Major development timeline

| Phase | Git/document evidence | Outcome |
|---|---|---|
| 1. Architecture foundation (2026-07-22) | Root `e8def45`; Milestone 0; ADRs and module boundaries | **IMPLEMENTED.** Local-first, clean/vertical-slice boundaries, ERP read-port, read-only Connector and fail-closed principles established. |
| 2. Connector/Tally foundation (2026-07-22) | `c71054c`, `49fbc48`, `4bf4125`, `f6f59c4` | **IMPLEMENTED / LIVE-VALIDATED.** Connector lifecycle, bounded XML requests, Tally reachability and extraction foundations. |
| 3. Discovery/Desktop shell (2026-07-22–23) | `8d2c5b0`, `e96083f`, `5a41e89`, `7849977`, `4126f5c`, `697bc79` | **IMPLEMENTED.** Company discovery/selection, groups, Desktop dashboard, lifecycle, configuration and diagnostics. |
| 4. Ledger/Stock synchronization (2026-07-23–25) | `9e24483`, `271f36e`, `f2a6b3a`; 5A/5A-P/5B reports | **IMPLEMENTED / AUTOMATED-VALIDATED / LIVE-VALIDATED.** SQLite snapshots, atomicity, retry, GUID identity, Stock and Ledger APIs. |
| 5. Android accounting experience | Android contracts/source; refinement register | **IMPLEMENTED.** A/c Data, company, Ledgers, Stock, Vouchers/details, search, Sync, Settings and Diagnostics backed by Room. |
| 6. Voucher/data-integrity expansion | Voucher contracts/runbooks; TD-001/020/026/027 | **IMPLEMENTED / PHYSICALLY VALIDATED** for pilot scope. Unified chronological browser, types, optional semantics, reconciliation; carry-forward tombstones **DEFERRED**. |
| 7. Secure pairing/trusted LAN | Secure-pairing lineage; TD-012–021 | **IMPLEMENTED / CONTROLLED-PILOT VALIDATED.** QR pairing, identities, credentials, fingerprint pinning and trusted devices; unfinished limitations remain explicit. |
| 8. Private removable storage (2026-08-11–16) | Closure §§44–46; TD-024/025/033/034 | **IMPLEMENTED / PHYSICALLY VALIDATED** for clean stop/removal/restart; fail-closed with no silent fallback. Migration and live-loss UX gaps deferred. |
| 9. PDF/preview/share | Refinement register; TD-028; closure | **IMPLEMENTED / PHYSICALLY VALIDATED / CLOSED.** Voucher/Ledger PDF, preview, Save and share. Ledger discoverability corrected after premature closure. |
| 10. MVP-1 hardening | TD-001 and TD-012–032; quality/release docs | **AUTOMATED-VALIDATED.** Accounting, reconnect, endpoint, storage, PDF and discovery defects fixed or explicitly deferred. |
| 11. Controlled-Pilot closure (2026-08-15–16) | `6fc6689` onward; closure; scorecard | Initial evidence gaps were later filled by Sessions 2–5. Final result: **CONTROLLED-PILOT VALIDATED / GO** with constraints. |
| 12. MVP-1 UI/UX polish (2026-08-16–17) | `b58d85c`, `eb532f7`, `7db7dd9`, `63b919e`, `bc9cd55` | **IMPLEMENTED / AUTOMATED-VALIDATED.** Android Home/type filters/copy and Desktop state/Refresh/status polished; candidates versioned. |
| 13. Owner Android visual review (2026-08-17) | Owner-supplied review recorded in checkpoint/UI status | Installed `continuity.21` Android candidate **HUMAN VISUALLY APPROVED**. Desktop visual approval is not implied. |
| 14. Approved Android visual implementation (2026-08-17) | `9b29abf`; three approved archive masters plus approved Voucher references | **IMPLEMENTED / AUTOMATED-VALIDATED / INSTALLED.** Replaced additive generic-card presentation with master-led Home, Ledger and Voucher Detail composition while preserving MVP-1 behavior. Debug/release JVM suites (1030 each), both lint variants and both APK assemblies pass. Candidate `continuity.22` was installed in place on device `10BF44124K000E3` with company/pairing state preserved; final human visual approval pending. |
| 15. Owner Android approval + Desktop visual implementation (2026-08-17) | Owner approval; `96a7a55`; Android continuity.22 visual language | **ANDROID HUMAN VISUALLY APPROVED; DESKTOP IMPLEMENTED / AUTOMATED-VALIDATED / PACKAGED.** Desktop `0.4.17` adopts the approved BUDCOM hierarchy through a Windows sidebar, compact status band, restrained surfaces and consistent controls without changing behavior. 696 tests and all TS/build/package gates pass. Per-machine installation requires UAC; final human Desktop visual approval pending. |
| 16. Desktop installed-runtime regression (2026-08-17) | Owner physical failure; installed CDP evidence; `e20053f` | **0.4.17 FAILED / 0.4.18 CORRECTED.** Packaged Chromium rejected a renderer ESM named import from CommonJS application output, so initialization aborted before binding clicks/loading data. `0.4.18` localizes the display mapping to the renderer, adds a module-boundary regression test, passes 697 tests and packaged real-state click/navigation/Refresh/resize validation, and is packaged for UAC installation. |
| 17. Owner Desktop visual/runtime approval (2026-08-17) | Owner statement, this session; `0.4.18` confirmed installed and running on the audited machine (asar-verified) | **DESKTOP HUMAN VISUALLY/RUNTIME APPROVED.** Owner reported the corrected `0.4.18` build good after physical review. Corroborated technically: the installed `Budcom Desktop.exe`'s packaged `app.asar` reads version `0.4.18` and the process was observed running (4 processes) at session start. No specific screen-by-screen observation is claimed beyond the owner's own statement. |
| 18. MVP-1 public-release preparation, part 1 (2026-08-17) | `6044e88`, `2a35e25`; gate matrix; release notes; quick-start | **TD-033/TD-034 RESOLVED.** Full public-release gate matrix produced (`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`). TD-033 (Standard↔Private storage switch) fixed with a guarded two-click confirmation gate — no silent switch, no data deleted. TD-034 (unreadable vault marker silently treated as absent) fixed — adoption path now fails closed with a distinct error. TD-025 (live USB-loss UX) explicitly documented as an accepted MVP-1 limitation rather than rushed — the narrow-safe fix touches shared renderer status-update plumbing and needs a dedicated pass. 9 new Desktop tests (706/706 passing); `tsc` clean. Desktop version bumped to `0.4.19` and a full unsigned candidate packaged (`release/controlled-pilot/0.4.19/`, mirrored with an explicit unsigned-not-for-distribution label under `release/public-candidate/`) after diagnosing and working around a Git-Bash-vs-PowerShell `tar` PATH ambiguity in the existing `dist:win` pipeline (no script changes). Android `assembleRelease` also verified this session: builds a real minified/shrunk unsigned `app-release-unsigned.apk` (2.3 MB) — confirms the release pipeline itself works; still blocked on signing. **No Windows code-signing or Android release-signing credentials exist anywhere in this repository or environment** — verified directly (no `.pfx`/`.p12`/`.jks`/`.keystore` files, no `signingConfigs` block, no CSC/keystore environment variables, `signAndEditExecutable: false`) — this is the dominant public-release blocker. Android public `applicationId` is also flagged (not decided) as a release blocker per governance, since a published Play Store package name is effectively permanent. Release notes and a Quick-Start/troubleshooting guide authored. Public-Release view added to the Quality Scorecard (BLOCKED — signing). Full Android JVM debug+release tests, both lints, both assembles, `assembleDebugAndroidTest`, and the `tests/contract` suite (5/5) all re-verified green this session. |
| 19. Pre-signing technical-debt closure (2026-08-17) | `c9677bf` through `a308433` (13 commits); registry updates | **TD-004, TD-023, TD-025 FIXED. TD-021/022/026/027/009 investigated and corrected/refined in the registry, not implemented (each has a documented reason: architectural scoping needed, already-implemented, or a product/security-policy decision this session is not positioned to make unilaterally). Multiple hygiene fixes shipped.** Full detail in §13/§18 below and the technical-debt registry. Headline items: TD-025's mid-session private-storage-loss overlay gap fixed (reuses the existing tested storage-gate UI); TD-023's connector voucher list/search endpoint switched from full-snapshot-load-then-filter to bounded SQL `search()` (zero behavior regression, existing route-level suite passed unmodified); TD-026 found to already be correctly implemented — closed a test-evidence gap and corrected stale registry documentation rather than building new reconciliation logic; TD-022 refined with a measured (not guessed) finding that the automatic background walk is already bounded to 30-day windows but the *manual* Voucher Browser refresh has no date-span cap — a real, previously undocumented gap, kept as an accepted limitation (P3, no incident evidence, right cap is a product decision); TD-004 fixed (Desktop Settings' Tally host/port fields were silently inert — never reached the spawned Connector — now forwarded via the existing child-env-override pattern); TD-009 and TD-027 reviewed and found still accurate, registry corrected/annotated, no code change (both would require a security-policy or product decision, not an engineering fix). Hygiene: moved test-only `DIAGNOSTIC_PRIVACY_SENTINELS`/`assertDiagnosticOutputExcludesSentinels` out of shipped Desktop production source into a test helper; fixed a confirmed unbounded temp-SQLite-directory leak in every Connector test (`test-context.ts` + new `vitest globalSetup`/teardown, measured 168 dirs created/168 cleaned per run, net zero growth against 1,554 pre-existing historical leaked dirs left untouched); fixed the same class of leak across 13 Desktop test files (measured 1,835 pre-existing dirs untouched, net zero growth after a full run); closed a content-level redaction gap in `startup-diagnostics.ts` (previously redacted by field-name only, so a bearer token or email inside an unrelated key like `message` would have been written to the log verbatim — now runs the same `redactString()` the ordinary file-log pipeline already applies). `docs/diagnostics/m3-stock-items-raw-sample.xml` (~1,500 real-looking inventory item names, committed in `f6f59c4`, 2026-07-22) was found again, confirmed unreachable by any packaging path, and left untouched per explicit instruction not to rewrite history — flagged for an explicit owner decision, not auto-redacted. Full four-component regression re-run clean at session end (see §15). |
| 20. MVP-1.1-A Universal Party Foundation (2026-08-17) | Six new Room tables/DAOs/repository/use cases under `feature/party/`; `MIGRATION_5_6`; version bump to `continuity.23` | **TECHNICALLY COMPLETE, READY FOR REVIEW.** Reconciled the frozen Connect/Universal-Party architecture and locked product spec against repository evidence — no genuine conflict found; one implementation-level ambiguity (ledger-group Party-seeding eligibility) resolved via an explicit, testable, conservative default rather than a STOP. Built Party stable identity (reusing the Connector's already-existing rename-stable `guid:`/`name:`-prefixed ledger id — no new Connector/Android plumbing needed for identity itself), company-scoped source links, normalized field provenance, the exact-10-digit Alias-phone rule (extracted into a new shared `PhoneNumberNormalizer` also now used by `WhatsAppRecipientResolver`), contact-person and hierarchical-tag storage foundations, and a fire-and-forget/failure-isolated seeding hook in `SyncViewModel` triggered only after a Ledgers sync succeeds. Zero UI built (deliberately, per 1.1-A scope). 1,073/1,073 debug JVM tests passing (1,030 pre-existing + 43 new across JVM and instrumented), `testReleaseUnitTest` passing, both lints 0 errors, `assembleDebug`/`assembleRelease`/`assembleDebugAndroidTest` all green, and 23/23 new instrumented tests (Room migration + all new DAOs) run and passing on a connected physical device (`I2407i`) — not simulated. That device has no existing BUDCOM installation and its ownership/authorization for a standing install is unconfirmed in this session's context, so no `adb install` was performed; flagged for the user. Full detail, exact known limitations, and the next-task recommendation: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`. |
| 21. MVP-1.1-B Connect Browser + Customers/Prospects + accounting deep links (2026-08-17) | New `feature/connect/`; additive `PartyDao`/`PartySourceLinkDao`/`TagDao` methods; new `Routes.CONNECT`; Dashboard third primary entry; version bump to `continuity.24` | **TECHNICALLY COMPLETE, READY FOR REVIEW.** First user-visible Connect slice. Customers/Prospects tabs, local-first (zero network calls — offline-capable by construction), classification-scoped name/phone search (extended `PartyDao.search`/`searchParties` with an additive nullable classification filter), Call (`ACTION_DIAL`, never `ACTION_CALL`, no new manifest permission) and WhatsApp (`wa.me` deep link) actions, and deep links to the existing unmodified Ledger Statement (stable ledger-id, via `PartySourceLink`) and Voucher Browser (name-based prefill — a documented, honestly-disclosed limitation of the pre-existing Voucher schema's own name-only party association, not a 1.1-B regression). Balance/tag row enrichment via a once-per-load bulk join (two new bounded `PartyRepository` reads + the existing `LedgerSnapshotPort`), never per-row — proven bounded with 500-row synthetic fixtures on real Room. Multi-company isolation explicitly tested, including that two companies' identically-named/-phoned Parties never share a balance. Outstanding confirmed not buildable (no authoritative bill-wise data exists anywhere in the schema — only a capability flag) and not attempted. 32 new tests (15 JVM + 17 instrumented, all passing on a connected physical device, not simulated); `testDebugUnitTest`/`testReleaseUnitTest` 1,088/1,088; both lints 0 errors; all three assembles green; full-app instrumented suite 194/206, the 12 failures confined entirely to files this session never touched and classified as a pre-existing device-viewport artifact on `I2407i`, not a regression (see `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part B §B20 for the exact failing-test list and reasoning). Device `I2407i` has no genuine pre-existing BUDCOM installation (only this session's own test-harness debug package) and its ownership is unconfirmed, so per explicit instruction no distinct install was performed. This session also added the permanent "Durable Development Record" rule to `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §13A. Full detail: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part B. |
| 22. MVP-1.1-C Party Detail + Prospect creation + Contact Persons + Tags + Notes/Activity (2026-08-17) | New `PartyDetailScreen`/`ViewModel`, `ProspectCreateScreen`/`ViewModel` under `feature/connect/presentation/`; new `party_notes` table via `MIGRATION_6_7` (schema version 7); 12 new `PartyRepository` write/read members; version bump deferred to after Part D | **TECHNICALLY COMPLETE, READY FOR REVIEW.** First slice where a user can edit Party data in BUDCOM. A five-section Party Detail screen (identity/Call/WhatsApp; accounting deep links shown only when a real `PartySourceLink` exists, never fabricated for a Prospect; Tally-compatible contact fields with plain-language provenance labels — "Confirmed from Tally"/"Pending in BUDCOM"/"Needs review"/etc., never a raw enum; tags; contact persons; notes/activity), reachable by tapping any Connect row. A minimal offline Prospect-creation flow (name required, everything else optional, no accounting field, no auto-merge on duplicate name/phone) reachable via a new FAB on Connect's Prospects tab. Full contact-person CRUD with a repository-enforced at-most-one-primary invariant (demote-then-upsert, safe primary deletion); full tag create/assign/unassign with duplicate-prevention; full note CRUD with bounded newest-first paging and optional voucher-linking (reuses the existing local-cache-only `LoadVouchersUseCase`, an unavailable linked voucher shows an honest message rather than crashing). Every edit is local-Room-only — zero Connector calls, and `confirmFieldFromTally` (the only path to a confirmed field) is unreachable from any Part C code, so a BUDCOM edit can never mark itself Tally-confirmed. 63 new tests (40 JVM + 23 instrumented, all passing on a connected physical device, not simulated); `testDebugUnitTest`/`testReleaseUnitTest` 1,128/1,128; both lints 0 errors; all three assembles green; full-app instrumented suite 217/229, the same 12-failure device-viewport-artifact class documented in Part B §B20, same unrelated files, zero overlap with any Part C file. Device `I2407i` still has no genuine pre-existing BUDCOM installation, so per the same explicit instruction no distinct install was performed. Explicit mini-hardening audit passed with one documented, deliberately-deferred UI-polish item (no ellipsis on a very long Party-name header) and no other findings. Version intentionally not bumped this session — deferred to a single coherent bump after Part D (Tally XML enrichment) also stabilizes, per this session's own combined-milestone instruction. Full detail: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part C. |
| 23. MVP-1.1-D Tally XML Enrichment Round-Trip (2026-08-17) | New `TallyLedgerXmlGenerator`, `TallyExportFieldMapping`, `PartyXmlExportScreen`/`ViewModel`; new `LedgerApi.getLedgerDetail`/`LedgerLiveDetailPort`; new `party_export_events` table via `MIGRATION_7_8` (schema version 8); 4 new `PartyRepository` members; version bump to `continuity.25` | **TECHNICALLY COMPLETE, READY FOR REVIEW.** Closes the combined MVP-1.1-C+D session. Change-review + Tally-compatible external-IMPORTDATA XML generation (six-field whitelist — `primaryPhone→MOBILENUMBER`, `primaryEmail→EMAIL`, `addressLine1→ADDRESS`, `addressState→STATENAME`, `addressPincode→PINCODE`, `gstin→PARTYGSTIN`; `addressCity` deliberately excluded, no distinct Tally tag exists) + Save-via-SAF (mirrors the proven Ledger-statement-PDF cache/FileProvider pattern) + a lightweight field-names-only export audit trail + on-demand re-sync confirmation with field-appropriate canonical comparison (phone normalized, email/GSTIN case-insensitive, address/pincode exact-post-trim, never over-normalized) — BUDCOM never writes to Tally directly at any point. Discovered and closed a genuine pre-existing gap along the way: Android had never called the Connector's already-implemented, already-read-only `GET /ledgers/{id}` detail endpoint, so there was no local ground truth for phone/email/address/GSTIN beyond a phone heuristic; closed with one new read-only Retrofit method + a new `LedgerLiveDetailPort` (Ledger-feature-owned, mirrors `LedgerSnapshotPort`'s shape), wired only into the explicit "Check Tally" action, never into any bulk/automatic sync path. Edit-after-export staleness and per-field partial confirmation both verified to fall out correctly from reusing 1.1-A's existing `updateBudcomOnlyField` state-reset behavior, with zero new bookkeeping. Prospects structurally excluded (button lives inside the same accounting-link-only block as View Ledger/View Vouchers) plus a defensive ViewModel re-check. 49 new tests (37 JVM + 12 instrumented, all passing on a connected physical device, not simulated) — including one genuine Compose crash (a nested `LazyColumn` inside a `verticalScroll` `Column`, throwing `IllegalStateException` on real-device measurement) found and fixed by the instrumented suite before commit, exactly the class of defect a JVM-only suite cannot catch. `testDebugUnitTest`/`testReleaseUnitTest` 1,165/1,165 (one incidental, already-documented `VoucherRepositoryImplTest` flake reproduced again this session, always unrelated, always clean on retry); both lints 0 errors; all three assembles green; full-app instrumented suite 229/241, the same 12-failure pre-existing device-viewport-artifact class documented in Parts B/C, zero overlap with any Part D file (one run also hit a transient ADB disconnect, recognized as the known non-defect pattern, device reconfirmed and retried successfully). Device `I2407i` still has no genuine pre-existing BUDCOM installation, so per the same explicit instruction no distinct install was performed. Version bumped `continuity.24`→`continuity.25` (versionCode 25→26) — the single coherent bump for the combined C+D session, per explicit instruction. Neither MVP-1.1-E nor any further MVP-1.1 work was started. Full detail: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part D. |
| 24. MVP-1.1-E Integrated Hardening, Acceptance & Freeze (2026-08-17) | No new tables/screens — pure integration audit + hardening. Two-file accessibility fix (`PartyXmlExportScreen.kt`, `PartyDetailScreen.kt`) + version bump (`app/build.gradle.kts`); exactly three files changed | **COMPLETE — MVP-1.1 FROZEN.** Recovered baseline from repository evidence (HEAD `f5cfd47`, `continuity.25`/versionCode 26, schema v8, clean tree — matched expectations exactly). Read the actual production code (not only prior-session documentation) across the full Connect→Party→Detail→Review→Export→XML→Save→re-sync chain, Party identity/company-isolation natural keys, provenance gating, export-audit privacy, XML escaping, file/cache path-containment, migrations, live-read company scoping, and offline/performance/security paths — zero integration-level defect found; A–D's own mini-hardening evidence corroborated. One genuine defect found and fixed: two Material3 `Checkbox` controls (`PartyXmlExportScreen`'s field-selection checkbox, `PartyDetailScreen`'s contact-primary checkbox) had no accessible content description — fixed additively via `Modifier.semantics { contentDescription = ... }`, zero click-handling or test-tag change. Fresh full regression: `testDebugUnitTest`/`testReleaseUnitTest` 1,165/1,165 (one incidental already-documented `VoucherRepositoryImplTest` flake, confirmed clean on isolated and full re-run); both lints 0 errors; all three assembles green; full instrumented suite 229/241 on device `I2407i`, the identical 12-failure pre-existing device-viewport class from Parts B/C/D, zero overlap with any Connect/Party/XML-export file including the two files this session edited (both migration tests and every Connect/Party/XML screen test passed). Device `I2407i` still has no BUDCOM package of any kind (checked at session start and again before the final build), so per the same governing rule as every prior sub-session, no install was performed. Version bumped `continuity.25`→`continuity.26` (versionCode 26→27) only after every regression gate passed clean. No scope creep: no MVP-1.2, signing, or unrelated Desktop/Connector work touched. Full detail including the freeze-audit table and human-acceptance checklist: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E. |
| 25. Owner-device installation of the frozen MVP-1.1 candidate (2026-08-17) | Installation-only session, explicitly no code/doc/Git changes authorized or made | **INSTALLED.** Verified the final APK (`apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`) against its recorded SHA-256 (`8a3413e7b4e4a22254fab7d2cbf05a698e22052ce6ef631e1e6dc6373282c4c3`) before touching the device — exact match. Device `I2407i` (serial `10BF44124K000E3`) confirmed as the expected owner device via `adb devices`; `pm list packages` confirmed no prior BUDCOM package of any kind (first install, nothing to preserve). Installed via `adb install -r` (non-destructive). Package `com.budcom.android.debug` (debug-variant applicationId suffix, pre-existing convention), `versionName=0.1.1-continuity.26`, `versionCode=27`, `firstInstallTime == lastUpdateTime` confirming a clean fresh install. Launch smoke test: `MainActivity` resumed in foreground, zero `FATAL EXCEPTION`/crash entries in logcat, screenshot-confirmed clean render of the **Secure Pairing** screen — the correct, honest first-run state for a never-paired device (`BudcomNavHost`'s own documented routing: secure pairing when required/in-flight, Dashboard otherwise). Pairing itself was not attempted (a configuration action outside this install-only task's authorized scope). `git status` verified clean before and after; HEAD unchanged at `ad32f9a`. No code, documentation, or Git state changed by this session — recorded here now, after the fact, per the Durable Development Record rule, since the physical install itself is consequential repository-adjacent evidence that must not exist only in chat history. |
| 26. MVP-1.2 planning, recovery & architecture review (2026-08-17) | New `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md` (new file, ~500 lines); this ledger entry + Current Development Status update. **Zero application code touched.** | **PLANNING COMPLETE — implementation not started, per explicit instruction.** Read the locked Master Product Execution Plan §8 (MVP-1.2's only locked scope title/objective: "Relationship Timeline, Issue History, Dincharya & OI"), the Universal Party Referral Tree spec §14–§28 (the only repository document that actually defines what these terms mean and where they sit in the UI — the master plan itself is title-only), the UI Design Decisions doc (confirmed Home Insights has a locked *navigation shape* only, its content spec belongs to a separate, not-yet-written "dedicated Insights workstream"), the Screen Inventory (confirmed zero screens exist yet for any MVP-1.2 surface, confirmed Vartalap has no milestone), `BUDCOM-NOT-NOW.md` and the Product Decision Log for locked scope boundaries. Verified directly against source rather than assumed: `party_notes` is flat/single-type today (`companyId`/`noteId`/`partyId`/`body`/`linkedVoucherId`/`createdAt`/`updatedAt` only — no type, status, due date, or grouping of any kind); zero `Worker` classes exist anywhere despite WorkManager being wired at the Hilt level, and no `POST_NOTIFICATIONS`/notification-channel infrastructure exists at all; Desktop's renderer has no Connect/Party surface whatsoever (its `Connect`/`Party` string matches are unrelated Connector-lifecycle references) — confirming MVP-1.2 is a 100%-Android effort needing zero Desktop/Connector work. Produced a full reconciliation matrix (plan vs. actual implementation, capability-by-capability) and a complete architecture/data-model/UI-intent/test-plan/milestone-breakdown document, sequenced into five coherent sub-milestones (1.2-A structured-activity foundation/migration, 1.2-B Relationship Timeline, 1.2-C Issue History, 1.2-D Dincharya, 1.2-E integrated hardening) mirroring MVP-1.1's own proven A–E rhythm. Explicitly excluded from MVP-1.2 scope, with reasoning recorded rather than silently assumed: Referral Tree (named "VVIMP" in its own locked spec but absent from the master plan's actual MVP-1.2 line item — flagged as a genuine open product-sequencing question, not resolved unilaterally), the Home Insights dashboard (no content spec exists anywhere), OS-level push notifications for Dincharya (zero supporting infrastructure exists; would open a new permission/security surface). Identified the single highest technical risk for MVP-1.2 as company-isolation on Dincharya's new cross-party bounded query — the first query in this feature area spanning an entire company at once rather than one party/list, requiring an explicit adversarial two-company test as a named acceptance gate, not an incidental side effect. No architecture decision from MVP-1.1 was reopened or changed; no MVP-1.2 feature code was written. `git status` clean before and after except this documentation commit; HEAD advances from `ad32f9a`. Full detail: `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`. |
| 27. Product decisions locked + MVP-1.2-A Structured Party Activity Foundation (2026-08-18) | New `PartyIssueDao.kt`, `PartyNoteDaoTest.kt`, `PartyIssueDaoTest.kt`; `MIGRATION_8_9` + generated `9.json`; 14 extended/new files across domain/data/use-case/ViewModel/Compose layers; 5 unrelated test-fake files mechanically updated for the extended `PartyRepository` interface; new `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md`; 4 new PDL entries; architecture doc §3/header updated from "open questions" to "resolved." Room schema v8→v9. | **PRODUCT DECISIONS LOCKED; MVP-1.2-A TECHNICALLY COMPLETE, READY FOR REVIEW.** The four open product questions from session 26 were explicitly answered by the Product Owner and recorded as `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-014–PDL-017 — all four confirmed the architecture document's own working assumptions, requiring no architecture rework. Implemented exactly the architecture doc's §22 recommended MVP-1.2-A prompt: extended `party_notes` with `type`/`dueAt`/`completedAt`/`issueId` (the `type` column's `@ColumnInfo(defaultValue = "'general'")` was necessary, not cosmetic — this is the first migration in the codebase to `ALTER` an existing populated table rather than only add new tables, and SQLite requires a default for a `NOT NULL` column added this way); added the new `party_issues` table, reusing `party_tags`' self-referential structural precedent but via a plain `PartyNote.issueId` column rather than a join table, since a note belongs to at most one issue. Extended `PartyRepository`/`PartyUseCases` for typed note create/edit (repository interface takes no default parameter values, matching this codebase's own established convention of putting defaults only on the use-case `invoke`), due-date/completion toggling, and issue create/resolve/reopen. Found and fixed a genuine pre-existing scope-vs-implementation mismatch along the way: the architecture doc's recommended prompt referred to "the existing add/edit note dialog," but direct inspection found the dialog was Add-only (no edit affordance existed anywhere, despite `EditNoteUseCase` already existing unused at the repository layer) — made the dialog genuinely serve both modes (`PartyDetailDialog.AddNote` renamed to `NoteEditor`, mirroring the already-established `ContactPersonEditor` add/edit-in-one-dialog precedent in the same file), then self-caught and fixed one defect this introduced before commit: the extended dialog initially showed a voucher-relinking picker in edit mode that `editNote`'s contract can't actually apply (a false affordance), fixed by hiding that section in edit mode rather than expanding `editNote`'s contract beyond this milestone's authorized scope. 24 new tests (14 JVM + 16 instrumented, all run and passing on a connected physical device — not simulated); five unrelated fake `PartyRepository` implementations elsewhere in the test tree needed mechanical signature updates for the extended interface, the same precedented class of change as MVP-1.1-A's own `FakeLedgerDao` updates. `testDebugUnitTest`/`testReleaseUnitTest` 1,179/1,179 both (was 1,165); both lints 0 errors (verified via report XML, not just console summary); all three assembles (`assembleDebug`/`assembleRelease`/`assembleDebugAndroidTest`) green; full instrumented suite 245/257 on device `10BF44124K000E3`, the identical 12-failure pre-existing device-viewport-artifact class documented since MVP-1.1-B, zero overlap with any file this session touched. A genuine environmental false alarm was investigated rather than accepted blindly: the first full instrumented run produced 53 failures (not ~12), every extra failure carrying the identical "no compose hierarchy"/"component not displayed" signature across totally unrelated, untouched screens — consistent with the device's screen locking mid-run during the ~10.5-minute suite (confirmed still connected via `adb devices -l`, so not a disconnect). Enabling `adb shell svc power stayon usb` plus a screen wake (a device power setting, not an app-state change) and re-running produced exactly the documented 12-failure baseline in 2m11s. Explicit mini-hardening review performed and recorded (company isolation, null/empty data, duplicate-issue non-deduplication as a deliberate design choice, completion transitions, date/time handling including double-enforced due-date clearing on type change, migration integrity, offline behavior, performance, accessibility, privacy/sensitive-logging, destructive-operations, repository boundaries, zero-direct-Tally-write, zero Desktop/Connector/manifest/Worker/notification-permission touch — all grep- or test-verified). Version deliberately not bumped (`continuity.26`/versionCode 27 unchanged) — per this task's explicit "defer the coherent milestone version bump until the appropriate milestone boundary" instruction and MVP-1.1-C's own precedent of deferring until a combined/complete slice. `git status` clean except this session's own changes; HEAD advances from `ea6869c`. Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part A. |
| 28. MVP-1.2-B Relationship Timeline (2026-08-18) | New `PartyTimelineDao.kt`, `TimelineModels.kt`, `PartyTimelineDaoTest.kt`; 13 extended files across domain/data/use-case/ViewModel/Compose layers; 5 unrelated test-fake files mechanically updated. Zero schema change, zero migration — `DatabaseConstants.VERSION` stays 9. | **TECHNICALLY COMPLETE — ACCEPTANCE GATE PASSED.** Implemented the architecture doc's §10/§11 1.2-B line item: a new `PartyTimelineDao` merges `party_notes` and `party_export_events` into one chronological, bounded/paged feed via a single SQL `UNION ALL` with `ORDER BY timestamp DESC, id ASC LIMIT/OFFSET` computed entirely in SQL — a deliberate architecture choice, since naively merging two independently-paged reads in Kotlin cannot correctly paginate a single feed across a source boundary. `party_notes`' flat presentation on Party Detail is replaced (not duplicated) by the merged Timeline, per the locked PDL-014; note CRUD itself is unchanged, only its read surface. `issueId` filter parameter is already wired through the full stack (query/repository/use-case) though not yet exposed in the UI, built now so 1.2-C's issue-filtered Timeline view can reuse this exact query rather than a second implementation. 23 new tests (7 JVM + 16 instrumented, all run and passing on a connected physical device); dedicated adversarial company-isolation tests at both the DAO and repository layers, including a same-display-name/same-phone identity-collision case; a same-timestamp tie-break determinism test; a 350-row (300 notes + 50 exports) large-fixture performance proof matching `PartyDaoTest`'s own 500-row precedent. `testDebugUnitTest`/`testReleaseUnitTest` 1,186/1,186 both (was 1,179; one incidental already-documented `VoucherRepositoryImplTest` flake, confirmed clean on retry); both lints 0 errors; all three assembles green; full instrumented suite 256/268, the identical unchanged 12-failure pre-existing device-viewport class, zero overlap. Applied the device stay-awake power-setting fix *proactively* this session (learned directly from 1.2-A's own documented lesson), so the instrumented run completed cleanly on the first attempt in 2m10s with no repeat screen-timeout investigation needed. Explicit mini-hardening review recorded (empty/sparse timeline with an honest "No activity yet" empty state, same-timestamp determinism, long text, missing optional fields, stale references — issues can never dangle since they're never deletable, duplicate-event prevention, offline behavior, accessibility, rotation/navigation survival) — zero genuine defect found this session. Version not bumped, per explicit instruction. `git status` clean except this session's own changes; HEAD advances from `f26def2`. **B GATE PASSED — automatic continuation to 1.2-C authorized and begun in the same session.** Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part B. |
| 29. MVP-1.2-C Issue History (2026-08-18) | 5 extended files (`PartyTimelineDao.kt`, `PartyNoteDao.kt`, `TimelineModels.kt`, `PartyDetailUiState.kt`, `PartyDetailViewModel.kt`, `PartyDetailScreen.kt`, repository/use-case files); 3 extended test files gain new blocks; 5 unrelated test-fake files mechanically updated. Zero schema change, zero migration — `DatabaseConstants.VERSION` stays 9. | **COMPLETE.** Extended (not duplicated) 1.2-B's `PartyTimelineDao` `UNION ALL` with two more arms reading `party_issues` directly (`issue_opened`/`issue_resolved`, using the issue's own live `createdAt`/`resolvedAt`) — the direct mechanism for "Issue/Timeline Consistency": the Timeline and the new Issues section read the exact same table, so they can never disagree about one issue's state. Added a Party Detail Issues section (open issues compact-then-expandable, resolved issues collapsed into their own secondary disclosure, per architecture §10) with Resolve/Reopen actions (wiring 1.2-A's already-existing but previously-unused `ResolveIssueUseCase`/`ReopenIssueUseCase`) and a "View in Timeline" filter (reusing 1.2-B's `issueId` parameter — no second, duplicate list implementation) with a partial-refresh `loadTimeline()` that avoids re-fetching the whole Party on every filter tap. New `PartyNoteDao.issueActivitySummary` bounded `GROUP BY` aggregate gives each issue card its note-count/last-activity rollup in one query, never one query per card. Accepted, explicitly documented limitation: reopening an issue does not retain a permanent record of a *past* resolution (no append-only issue-event log exists; building one was assessed and rejected as disproportionate for this milestone, PDL-012). 29 new tests (15 JVM + 14 instrumented, all run and passing on a connected physical device) with dedicated adversarial company-isolation coverage at the DAO, note-aggregate, and repository layers, plus direct Timeline/Issue-consistency proofs (create→opened event; resolve→resolved event added, opened kept; reopen→resolved event removed, opened kept; issue-filtered view excludes the issue's own lifecycle rows). `testDebugUnitTest`/`testReleaseUnitTest` 1,201/1,201 both (was 1,186); both lints 0 errors (one transient Gradle parallel-task lint-analysis race investigated and confirmed to be a build-tool scheduling artifact, clean on immediate retry with zero code change); all three assembles green; full instrumented suite 274/286, the identical unchanged 12-failure pre-existing device-viewport class, zero overlap. Applied the device stay-awake fix proactively again; the run completed cleanly in 2m19s on the first attempt. Version not bumped. `git status` clean except this session's own changes; HEAD advances from `95ff98d`. **C test gate passed — this session's own final stop condition now applies: do not begin 1.2-D.** Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part C. |
| 30. B/C checkpoint: Git preservation + MVP-1.2-D readiness review (2026-08-18) | Zero application/production code changed. Documentation only: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` (new "Checkpoint" section appended), this ledger, `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`. | **GIT PRESERVATION COMPLETE. MVP-1.2-D READINESS REVIEW COMPLETE — IMPLEMENTATION NOT STARTED.** Explicitly not an implementation session — verified the completed B/C state directly against source rather than trusting prior documentation (HEAD `8f77cf63b7533f1418cb6208157969dee3022765` confirmed to contain both the B commit `95ff98d` and the C commit `8f77cf6`; targeted greps confirmed `PartyTimelineDao`'s 4-arm `UNION ALL`, the Issues-section composables, `issueActivitySummary`, and dedicated company-isolation tests at both DAO and repository layers all genuinely present in source, not merely claimed); a lightweight regression check (`testDebugUnitTest` + `lintDebug`) showed 100% Gradle `UP-TO-DATE` — independent confirmation of zero drift since the last full green run, so the expensive instrumented suite was not re-run without new signal to justify it. Performed a full Git safety audit before pushing (verified single-author 234-commit range ahead of `origin/main`, all recognizable BUDCOM milestone work; secret/credential scan across the full diff found zero matches; confirmed clean fast-forward relationship, 0 behind). Pushed 234 previously-local-only commits to `origin/main` via a normal fast-forward push — **no force flag used, no history rewritten** (`7cf85ba..8f77cf6 main -> main`); post-push verification via `git fetch origin` + `git rev-parse` confirmed local HEAD and `origin/main` are byte-identical at `8f77cf63b7533f1418cb6208157969dee3022765`, working tree clean, "up to date with 'origin/main'". Then performed a **read-only** architecture/readiness review for MVP-1.2-D (Dincharya) — re-read the architecture document's own §10/§11/§13/§16/§17/§20 in full, plus the Universal Party spec's Dincharya definition; did read-only source reconnaissance (grep/Read only, zero file modified) confirming the exact Dashboard-entry wiring pattern (`OpenConnect`→`DashboardNavigation.Connect`→`navController.navigate`) to replicate for a fourth entry, and confirming `PartyFieldProvenanceDao`'s exact current shape (only a `(companyId, partyId)` index, no company-wide index yet) as the basis for the query/index-impact analysis. Produced a complete data-source analysis for Dincharya's three locked item types (follow-ups from `party_notes`, pending-Tally-confirmation from `party_field_provenance.state`, pending-contact-completion from `cached_parties` nullable contact fields) — concluding zero new schema/columns are needed for any of the three, but each needs a new company-wide bounded query (the first genuinely cross-party query in this feature area), named this as the dominant risk (company isolation, since there is no secondary per-party narrowing to lean on unlike every prior 1.1/1.2 query), and specified named blocking adversarial two-company tests as the required proof. Documented the important architectural nuance that a *new Room index* (if the required 500-row performance proof shows one is actually needed) would itself require an additive migration, since Room's schema export tracks indices — recommending evidence-first sequencing (PDL-012) rather than a pre-emptive index+migration. Explicitly flagged, rather than silently resolved, two genuine open ambiguities the architecture document itself leaves to product judgment: the exact classification scope for "pending contact completion" (Customer/Prospect only, or all classifications), and the "endless overdue list" aging/decay rule beyond the already-locked top-N-plus-"N more" bounding (architecture §20 Risk #2) — proposed a conservative default (`dueAt ASC` ordering, fixed per-group cap) without inventing a product policy. Produced a complete file/architecture map, test matrix, and hardening checklist intended to let a fresh Claude session begin implementation directly. Zero production code, zero migration, zero version bump, zero APK build/install performed for D, per explicit instruction. `git status` clean except this session's own documentation changes. **STOP condition (this session's own): do not begin MVP-1.2-D implementation, do not begin 1.2-E, do not begin MVP-1.3, do not install.** Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` "Checkpoint" section. |
| 31. MVP-1.2-D Dincharya (2026-08-18) | New `feature/dincharya/` package (domain model/repository/use-case, data repository impl + Hilt module, presentation ViewModel/UiState/Screen — 8 new files); 3 new instrumented test files (`PartyFieldProvenanceDaoTest` new, `DincharyaScreenTest` new) plus 2 new JVM test files (`DincharyaRepositoryImplTest`, `DincharyaViewModelTest`); 6 extended production files (`PartyDao.kt`/`PartyNoteDao.kt`/`PartyFieldProvenanceDao.kt` gain bounded company-wide queries, `DashboardUiState.kt`/`DashboardViewModel.kt`/`DashboardScreen.kt` gain the fourth entry, `Routes.kt`/`BudcomNavHost.kt` gain the new route, `strings.xml` gains Dincharya copy); 4 existing test files mechanically extended (`PartyDaoTest`/`PartyNoteDaoTest` gain new blocks, `PartyRepositoryImplTest`'s three affected fake DAOs gain stub overrides, `DashboardViewModelTest`/`DashboardScreenTest` gain the new navigation case). New PDL-018. Zero schema change, zero migration — `DatabaseConstants.VERSION` stays 9 (the required 500-row performance proof showed the existing `companyId`-prefixed indices were already sufficient for all three new queries, so no new index was added, per PDL-012's evidence-first discipline). | **COMPLETE.** Implemented exactly the readiness review's own CP6 brief: the three locked deterministic item types (Follow-ups/Callbacks from `party_notes`, Pending Tally Confirmation from `party_field_provenance.state`, Pending Contact Completion from `cached_parties`), each a new bounded, `companyId`-scoped, company-wide DAO query (the first cross-party queries in this feature area) — never merged into one feed, since architecture §10 requires them visually separate. Chose a dedicated new `DincharyaRepository`/`DincharyaRepositoryImpl` composing the same DAOs directly rather than extending the already-24-method `PartyRepository` (which would have forced five unrelated existing test fakes to grow stub overrides for a company-wide concern none of them touch) — a design decision the readiness review had explicitly left open, now resolved and recorded. Pending Tally Confirmation is grouped **per Party** (`GROUP BY companyId, partyId` with `GROUP_CONCAT` for the pending field list), not per pending field, avoiding a same-Party-shown-twice near-duplicate look. Display names for the follow-up/confirmation groups are resolved via one new bounded `PartyDao.findByIds` bulk lookup per group (mirroring `ConnectViewModel`'s own bulk-enrichment-read precedent) rather than a cross-table SQL JOIN, keeping every DAO single-table-focused. Locked the two genuine open product ambiguities the readiness review flagged, not resolved unilaterally: recorded as `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-018 — contact-completeness is valid-phone-OR-valid-email (reusing `PhoneNumberNormalizer.normalizeForSearch` exactly, no second validation rule; "valid email" is non-blank only, matching this codebase's existing zero-email-format-validation convention), excludes Prospects, excludes contact-person-level detail entirely; follow-ups never auto-expire, with `dueAt ASC, noteId ASC` ordering confirmed as the locked rule (already yields overdue-first/due-today/upcoming for free — no invented urgency score). New top-level Dincharya screen (`feature/dincharya/presentation/`) reached from a fourth Dashboard primary entry, replicating Connect's own MVP-1.1-B wiring pattern exactly (`DashboardEvent.OpenDincharya`→`DashboardNavigation.Dincharya`→`navController.navigate(Routes.DINCHARYA)`); reuses the established `MasterDataLoadingIndicator`/`MasterDataErrorBlock` shared components and honest-empty-state discipline, an always-visible OI framing line using the architecture doc's own exact locked wording, capped-per-group "N more" disclosure (never infinite scroll, a deliberate different pattern from Connect/Timeline's paged lists), and plain-language reason labels only (no raw enum/DAO name ever reaches the UI). Self-caught and fixed one defect before commit: the first draft silently swallowed a refresh error once stale content already existed on screen — fixed to match `ConnectScreen`'s own inline-error-while-keeping-content discipline. 35 new instrumented tests (22 DAO + 12 Compose + 1 Dashboard-entry, all run and passing on a connected physical device — not simulated, including three dedicated 500-row large-fixture performance proofs, one per new query, all comfortably under the 2-second bar) plus 18 new JVM tests (9 repository + 9 ViewModel); dedicated adversarial company-isolation tests at the DAO, repository, and ViewModel layers, each reusing identical natural keys (same `noteId`/`partyId`/`dueAt`/display-name) across two companies to prove isolation holds under a genuine identity collision. `testDebugUnitTest`/`testReleaseUnitTest` 1,219/1,219 both (was 1,201 after C); both lints 0 errors; all three assembles green including R8-minified release; full instrumented suite 309/321, the identical unchanged 12-failure pre-existing device-viewport-artifact class documented since MVP-1.1-B, zero overlap with any file this session touched (confirmed individually against all 58 Dincharya/Party-DAO/Dashboard-entry testcases in the XML report). A genuine environmental false alarm was investigated rather than accepted blindly, the same class already documented in Part A's session: the first full run produced 27 failures (15 extras beyond the known 12, entirely in two never-before-baseline, never-touched classes — `CompanyScreenTest`/`PdfPreviewScreenTest`/`PdfPageRendererTest` — sharing the identical "No compose hierarchies found" device-timeout signature); `adb shell svc power stayon usb` alone (Part A's original fix) proved insufficient this time, so investigated further rather than retrying blindly — found the device's 30-second screen-off timeout was still active despite USB power being detected, extended it directly (`settings put system screen_off_timeout 1800000`) plus `svc power stayon true`, and the retry produced exactly the documented 12-failure baseline with zero extras. Explicit mini-hardening review performed and recorded (company isolation, false affordances, N+1, null-handling/display-name-fallback safety, duplicate-item prevention including the same-Party-in-two-groups case proven crash-free, accessibility, honest wording, zero destructive operation, zero MVP-1/1.1/1.2-A/B/C regression, refresh-error honesty, zero Desktop/Connector/manifest/Worker/notification touch — all grep- or test-verified). Version deliberately not bumped (`continuity.26`/versionCode 27 unchanged), per this task's explicit "D → review → E → one coherent bump" instruction. No production/release APK built or installed — only the standard regression-gate assembles plus the ephemeral debug test-harness install `connectedDebugAndroidTest` itself performs at the unchanged version, the same mechanism every prior 1.2-A/B/C session already used. `git status` clean except this session's own changes; HEAD advances from `d964b760`. **STOP condition (this task's own): do not begin MVP-1.2-E, do not begin MVP-1.3, do not push, do not install a release-candidate APK.** Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part D. |
| 32. MVP-1.2-E Integrated Hardening, Freeze, Final Candidate (2026-08-18) | 3 production files extended (`PartyDetailUiState.kt`/`PartyDetailViewModel.kt`/`PartyDetailScreen.kt` — Mark done/Reopen note-completion wiring); 2 test files extended (`PartyDetailViewModelTest.kt`, `PartyDetailScreenTest.kt`); `build.gradle.kts` version bump. Zero schema change, zero migration — `DatabaseConstants.VERSION` stays 9. Final candidate `versionCode` 27→28, `versionName` `0.1.1-continuity.26`→`0.1.1-continuity.27`. | **COMPLETE — MVP-1.2 FROZEN.** Integrated audit of Parts A–D together (not per-part in isolation) found one genuine, consequential defect: `SetNoteCompletionUseCase` existed at the repository/use-case layer since 1.2-A but had zero UI caller anywhere in the app through B, C, and D — meaning Dincharya's entire Follow-ups/Callbacks group showed real overdue items a user had no honest way to complete, only the indirect side effect of editing away a note's due date or type. Fixed by wiring `MarkNoteDoneTapped`/`ReopenNoteTapped` into `PartyDetailViewModel` (mirroring the existing Issue Resolve/Reopen pattern exactly, no new UI pattern), adding a due-date/completion status line plus Mark done/Reopen action to `NoteRow` for `commitment`/`follow_up` notes. Self-caught a second issue while implementing the fix: the first draft colored the status line red for every incomplete follow-up regardless of whether it was actually overdue — a false urgency signal, since this row has no access to "now" the way Dincharya's own urgency classification does; fixed to a neutral color, text alone states the fact. Re-verified (not re-invented) company isolation, migration (`AppDatabaseMigrationTest` 8/8 passing, schema unchanged), accessibility (self-describing `TextButton`/`Card` pattern maintained throughout, the one color-alone finding already fixed), offline/failure/state behavior, and performance (no new query added, Part D's three large-fixture proofs unaffected and re-confirmed passing) across Parts A–D together. Explicitly checked and confirmed not a regression: neither Dincharya nor Connect auto-reloads on returning from Party Detail after an edit — an existing, consistent, codebase-wide characteristic, not something E introduced or needed to fix. 5 new tests (2 JVM + 3 instrumented, all run and passing on device `10BF44124K000E3`). `testDebugUnitTest`/`testReleaseUnitTest` 1,221/1,221 both (was 1,219 after D); both lints 0 errors; all three assembles green including R8-minified release; full instrumented suite 312/324, the identical unchanged 12-failure pre-existing device-viewport-artifact class documented since MVP-1.1-B, zero overlap with any file this session touched (confirmed individually — all 35 `PartyDetailScreenTest` cases and all 8 `AppDatabaseMigrationTest` cases passed), completing cleanly at the known baseline on the first attempt (the device stay-awake fix from Part D's own investigation was applied proactively). Version bumped only after full A–D+E regression was confirmed green — the single coherent MVP-1.2 freeze bump. Built and directly inspected (`aapt dump badging`, not source config alone) both a debug APK (`com.budcom.android.debug`, versionCode 28, versionName `0.1.1-continuity.27`, 14,470,132 bytes, SHA-256 `92a38394e5eef4cee65eae4ba6caaef86b5e430dfccf22973aa1401362fe9d02`) and an unsigned release-verification APK (`com.budcom.android`, same version, 2,509,604 bytes, SHA-256 `ad11110475ef9e887afd642ef28c06f016a4f48fcf30b819456ab7aa1fee4c92`, never a public-release artifact — no signing credentials exist). Installed the debug APK on device `10BF44124K000E3` via `adb install -r` (confirmed via `pm list packages`/`pm path` that zero BUDCOM package existed beforehand — the Part D finding that `connectedDebugAndroidTest` had removed the prior install; no uninstall/data-clear/pairing-wipe performed since there was nothing to preserve). Confirmed installed at the correct version (`firstInstallTime == lastUpdateTime`, a genuine fresh install). Smoke-tested directly on device: launch succeeded (`MainActivity` became `mFocusedApp`), the correct honest first-run Secure Pairing screen rendered (screenshot-confirmed), zero `FATAL EXCEPTION`/`AndroidRuntime:` logcat entries, Back navigation behaved correctly (returned to launcher, app process stayed alive — normal root-activity behavior, not a crash), relaunch succeeded cleanly. Explicitly, honestly **not** exercised on-device: Dashboard/Connect/Dincharya/Party-Detail/Timeline/Issues navigation — this environment has no real paired Tally Connector to complete Secure Pairing against, and per explicit instruction no attempt was made to fake or bypass pairing; these surfaces are verified only through the automated instrumented Compose test suite (real Room/real Compose on the same device, synthetic state), stated as a distinct and weaker form of evidence than genuine on-device navigation rather than conflated with it. `git status` clean except this session's own changes; HEAD advances from `57a27e4`. **MVP-1.2 COMPLETE / FROZEN** per this task's own freeze criteria — every item verified with evidence (A–D intact, hardening complete, no unresolved defect, isolation/migration/accessibility/regression all green apart from the documented 12-baseline, final APK builds/installs/launches/smoke-tests, documentation complete, Git clean). **STOP condition (this task's own): do not begin MVP-1.3, do not modify Desktop, do not begin public signing, do not publish anything.** Full detail: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part E. |
| 33. MVP-1.3 Business Profile — planning/recovery review (2026-08-18) | New `docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`; `docs/design/BUDCOM-SCREEN-INVENTORY.md` corrected (five stale-missing screens added: Connect, Party Detail, Prospect Create, Party XML Export, Dincharya); this ledger entry; `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` updated. **Zero production/test/migration/version-bump code touched — explicitly a read-only reconnaissance session.** | **PLANNING/RECOVERY REVIEW COMPLETE — MVP-1.3 IMPLEMENTATION NOT STARTED, NOT AUTHORIZED.** Verified the MVP-1.2 freeze baseline directly (HEAD `773284485e03028508e89b8f84ebeda54a1dac3b` exactly matches this task's own expected value; `versionCode 28`/`versionName 0.1.1-continuity.27`/`DatabaseConstants.VERSION 9` all confirmed; working tree clean, zero untracked files, local HEAD byte-identical to `origin/main`) rather than trusted from documentation alone. Read the full authoritative document set (Master Product Execution Plan, UI Design Decisions, Screen Inventory, NOT-NOW, the full Product Decision Log PDL-001–018, the Modus Operandi) plus a full repository source search (Android/Desktop/Connector) and a direct inspection of the external design archive (`D:\BUDCOM-Design-Archive\`, confirmed to exist locally). **Central finding:** MVP-1.3's architecture-sequencing and ownership shape are locked (Master Plan §9: MVP-1.3 = Business Profile; UI Decisions §7: one business identity/one catalogue/one asset library, Connect/Vartalap consume not own) but its *detailed* scope explicitly is not — the Master Plan's own words, "Detailed scope comes from Brainstorm 1," and no Brainstorm-1 output (the mandatory User+ChatGPT step, Modus Operandi §2/§3, PDL-010) exists anywhere in the repository or design archive for this milestone. Zero Business-Profile/Catalogue code exists anywhere (Android, Desktop, Connector) — confirmed by direct search, not assumed; the only related content anywhere is the already-cited planning/design documents. One genuine documentation-staleness defect found and fixed: `BUDCOM-SCREEN-INVENTORY.md`'s "Current screens" table had gone stale since before MVP-1.1-B, omitting five real, shipped, frozen screens (Connect, Party Detail, Prospect Create, Party XML Export, Dincharya) — corrected directly per that file's own "a stale inventory is worse than none" maintenance rule, since a fresh MVP-1.3 session would otherwise be actively misled by it. Produced a complete architecture document (`BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`): a plan-vs-repository reconciliation matrix; five explicit, unresolved open product decisions (ranked by risk) — most significantly whether a Business Profile is scoped per Tally `companyId` (matching every other table in this codebase) or is genuinely singular per installation/device (matching the locked "one business identity" wording), a question this codebase's own architecture cannot answer without a real product decision; a full MUST/SHOULD/DEFER/NOT-NOW product-boundary classification explicitly re-confirming every existing exclusion (generative AI, cloud sync, Vartalap, Referral Tree, Home Insights, OS notifications, and the Master Plan's own specifically-named "premature marketplace/social-network expansion" warning); a candidate (explicitly not locked) data model, UI/UX shape, company-isolation strategy, offline classification, and test strategy, each grounded in this codebase's own proven patterns (`PartyFieldProvenance`, `PhoneNumberNormalizer`, the existing `FileProvider`/asset-sharing precedent, the `HomePrimaryEntryRow` Dashboard-entry precedent) rather than invented; a smaller proposed A/B/C sub-milestone structure (data foundation, optional asset support, integrated hardening/freeze) offered as a draft, explicitly contingent on Brainstorm 1's actual scope decision, not mechanically copied from MVP-1.2's five-part rhythm. No product decision was made or guessed at — every open question is recorded precisely with the concrete options this codebase's architecture makes visible, per this task's own explicit "do not resolve product decisions by guessing" instruction. `git status` clean except this session's own three documentation files; HEAD advances from `7732844`. Nothing pushed this session (documentation-only changes, push not explicitly authorized by this task). **Exact next task: Brainstorm 1 (User + ChatGPT) for MVP-1.3 Business Profile**, using the new architecture document as its starting input — not Claude implementation of any kind. Full detail: `docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`. |

## 4. Android development

Android evolved from a companion shell into the local-first accounting-data client. Current source
contains company selection, A/c Data Home, Ledgers/statements, Stock Items, unified Vouchers and
details, universal search, Sync, PDF preview/save/share, Settings and Diagnostics. Room/local
persistence is authoritative for normal display; authenticated remote sync promotes complete
snapshots atomically. Offline/freshness and reconnect states are surfaced without exposing most
transport internals.

QR pairing stores Connector identity, device identity/credentials and the pinned transport
fingerprint. Identity-based mDNS/Android NSD rediscovery replaced fixed-IP dependence. TD-029–032
corrected Desktop rebind, invalid IPv6 advertisement, missing `securePort`, and Android multicast
reception. A OnePlus Nord 5 hotspot was proven unsuitable for the mDNS leg because it did not
forward multicast between clients; Jio Fiber/router-LAN operation and the individual mechanisms
supplied accepted pilot evidence.

The polish reconciled A/c Data Home with the approved Stitch master at
`D:\BUDCOM-Design-Archive\01_APPROVED_MASTERS\AC_DATA_HOME\BUDCOM-AC-DATA-HOME-MASTER.png`, added
the Voucher type-filter strip, normalized loading/error/status copy, improved Sync/Settings/
Diagnostics, and polished Voucher Details/PDF preview. Insights and Connect/Vartalap tabs were not
fabricated because their data/destinations are future scope. The owner physically inspected the
installed `continuity.21` build on 2026-08-17 and visually approved the Android UI/UX pass.

## 5. Desktop development

The Electron Desktop application remains a lightweight operator surface around the Connector. It
manages Connector lifecycle, company discovery/selection, refresh/sync, freshness/diagnostics,
network binding, trusted-LAN status, storage selection and the Windows installer lifecycle. Its
renderer is not an accounting system of record.

Hardening fixed transient re-poll, stale endpoint clients and departed-IP binding. UI polish made
connecting visible, repaired Dashboard Refresh so it renders fetched state, humanized raw status,
and removed dead DOM calls. These are **AUTOMATED-VALIDATED** at Desktop `0.4.16`; no evidence yet
records human visual approval. Tray/background behavior, triple-restated connection state and
product naming remain deliberate P2/deferred decisions.

## 6. Connector and Tally/XML integration

The Node/TypeScript Connector is the sole runtime boundary to Tally. Communication is read-only
and capability-restricted to exports; it builds bounded XML requests, parses company, group,
ledger, stock and voucher data, and exposes versioned local APIs. It owns lifecycle, selected
company/session state, SQLite repositories, snapshots, authentication, diagnostics and mDNS.
Android never talks directly to Tally.

Extraction evolved to GUID-first identity, quality validation, response limits, cancellation,
concurrency control and diagnostics. TD-001 corrected the invalid assumption that
`IsDeemedPositive` and signed Amount must agree. Live reconciliation later proved 98/98
GUID-matched vouchers. Tally may temporarily withhold a voucher from Export while its UI shows it
(TD-027); that is an upstream timing observation, not a BUDCOM defect.

## 7. Sync and data integrity

- **IMPLEMENTED:** company-isolated Connector SQLite snapshots and Android Room state; atomic
  promotion only after a complete authoritative window.
- **AUTOMATED-VALIDATED:** idempotency, duplicate prevention, bounded history, retry, concurrency
  safety, in-window add/edit/delete, stable GUID identity and migration protection.
- **PHYSICALLY VALIDATED:** real-Tally Sync All, repeat refresh, browsing and 98/98 GUID
  reconciliation; mutation scenarios are recorded in closure evidence.
- **LOCKED:** Optional/Estimate vouchers are non-accounting. “Last 7 Sales” counts regular Sales
  and includes all accounting movements in the resulting interval.
- **DEFERRED:** carried-forward out-of-window vouchers lack later edit/delete tombstones (TD-026).
  TD-022/023 are non-blocking snapshot/query performance observations.

## 8. Connectivity, discovery, pairing and trust

| Capability | Evidence status |
|---|---|
| Endpoint/lifecycle | **IMPLEMENTED / PHYSICALLY VALIDATED** for restart and real network rebinding. |
| mDNS/DNS-SD + Android NSD | **IMPLEMENTED.** Identity, IPv4 endpoint and `securePort` corrected by TD-030/031. |
| Android multicast | **FIXED / mechanism PHYSICALLY VALIDATED** by app-owned lock and OS group/lock evidence (TD-032). |
| QR pairing/trust | **IMPLEMENTED / CONTROLLED-PILOT VALIDATED.** Identities, credentials, fingerprint verification and trust persistence. |
| Reconnect | **PHYSICALLY VALIDATED** for restarts/router LAN; literal unattended two-router TD-017 transition remains a non-blocking residual. |
| Phone hotspot | **ACCEPTED ENVIRONMENTAL LIMITATION.** Nord 5 multicast isolation prevents reliable mDNS, not unicast TCP. |
| Legacy/renewal | TD-009 and TD-021 remain open/proposed; security is not claimed complete beyond pilot scope. |

## 9. Private USB storage

Private mode places canonical SQLite in a marker-identified removable vault. Desktop resolves the
vault; Connector independently verifies marker/vault identity and fails closed if absent/wrong.
There is no silent internal fallback.

The physical session stopped BUDCOM, removed USB, observed the unavailable screen, reinserted it,
and proved byte-identical database continuity by SHA-256. This validates the pilot clean-stop
workflow, not live mid-write removal. TD-025 records generic Disconnected UX after live loss; the
pilot rule is “do not remove USB while running.” TD-033 records missing Standard→Private migration/
warning (including orphaned trust); TD-034 records unreliable adoption of an externally/stale-
encoded marker. Both are deferred for a fresh, fixed-Private pilot. Historical internal-drive
remnants and orphaned vaults are non-authoritative and were not destructively cleaned.

## 10. PDF and share

Voucher/Ledger PDF generation, in-app preview, Save and Android share are implemented. TD-028 was
first closed when Voucher preview worked, then correctly reopened because Ledger preview was only
behind an undiscoverable long press. A one-tap entry was added; Preview, content review, Save and
WhatsApp Share were physically confirmed and TD-028 is **CLOSED**. Later installed Android polish
was owner-reviewed without changing accounting truth.

## 11. MVP-1 hardening and Controlled Pilot

Hardening covered accounting semantics, identity/migrations, interrupted/concurrent sync,
trusted-LAN pairing/reconnect, endpoint discovery, transport identity, private storage, PDF,
packaging and diagnostics. The initial 2026-08-15 assessment correctly found tests alone did not
prove device behavior. Later 2026-08-16 Sessions 2–5 added direct router/network, restart, USB/hash,
PDF/share and Sync All evidence. Closure and Quality Scorecard therefore supersede the provisional
verdict with **GO for Controlled Pilot**.

Constraints: stable real-router LAN; no USB removal while running; no Standard→Private switch;
expected unsigned Windows/Android-debug signing; no claim that the literal two-router transition
or live mid-write pull was witnessed.

## 12. MVP-1 UI/UX polish

| Work unit | Commit(s) | Status |
|---|---|---|
| A/c Data Home + Voucher filters (UIP-002/003) | `b58d85c` | **IMPLEMENTED / AUTOMATED-VALIDATED / HUMAN VISUALLY APPROVED** on installed continuity.21 |
| Company/Sync/Settings/Diagnostics copy/state | `eb532f7` | **IMPLEMENTED / AUTOMATED-VALIDATED / HUMAN VISUALLY APPROVED** in owner Android review |
| Voucher Details/PDF preview | `3eabf46`, `feea4ae` | **IMPLEMENTED / PHYSICALLY VALIDATED / CLOSED** |
| Desktop indicator, Refresh, raw status | `7db7dd9` | **IMPLEMENTED / AUTOMATED-VALIDATED**; visual approval pending |
| Candidate versioning | `bc9cd55` | Desktop 0.4.16; Android continuity.21/22; Connector 0.4.6 |

The Stitch path above is authoritative where the UI/UX status records it. Unsupported Insights,
Connect/Vartalap and drawer functionality remain out of scope. Detailed P2/DEFER findings remain
in the specialist UI/UX status.

## 13. Technical-debt reconciliation

Canonical detail remains `docs/technical-debt/registry.md`. This includes every registered item
through TD-034; “physical” is never inferred from tests.

| TD | Short title / subsystem | Root cause and disposition | Validation / state |
|---|---|---|---|
| 001 | Voucher parser / Tally | Invalid sign/`IsDeemedPositive` equality; tolerance fix | Automated + physical; **CLOSED P0** |
| 002 | Company UI | Missing Desktop selection UI; implemented | **RESOLVED P2** |
| 003 | Supervision | Missing lifecycle manager; implemented | **RESOLVED P2** |
| 004 | Tally spawn config | Host/port not forwarded | **FIXED P3** (2026-08-17) — now forwarded via `resolveConnectorLifecycleConfig()` + child-env allowlist |
| 005 | JSON repository | Replaced by SQLite | **RESOLVED P2** |
| 006 | Interrupted sync | Durable retry lineage added | **RESOLVED for pilot P2** |
| 007 | Cancellation | Bounded extraction cancellation | **RESOLVED WITH LIMITATION P3** |
| 008 | Network binding | Insecure default narrowed | **RESOLVED P2** |
| 009 | Auth LAN | Trusted-LAN bind mode has no per-device authN, independent of the (opt-in) secure-pairing subsystem | **OPEN P2** (reviewed 2026-08-17, still accurate); requiring pairing for trusted-LAN is a security-policy decision, not an engineering fix |
| 010 | Diagnostic privacy | Allowlist/sanitizers | Automated; **RESOLVED P2** |
| 011 | Ledger identity | Name slug/shallow export → GUID-first | Live evidence; **RESOLVED P1** |
| 012 | Manual-IP pairing | Discovery gap | Automated; physical once 2026-08-08; **FIXED P0**, current-candidate retest non-blocking |
| 013 | Company lost on reconnect | Session recovery | Restart physical pass; **FIXED P0** |
| 014 | Desktop recovery | No transient re-poll | Deterministic + physical; **FIXED P1** |
| 015 | Stale endpoint clients | Old route retained | Rebind physical pass; **FIXED P0** |
| 016 | Diagnostics endpoint | Legacy state shown | Physical restart evidence; **FIXED P0** |
| 017 | Pinned old endpoint | Trust coupled to address | Identity rediscovery; combined evidence; **FIXED P0**, two-router residual |
| 018 | Mutable packaged identity | Identity under install resources | Durable path + physical restart; **FIXED P0** |
| 019 | Legacy endpoint/trust replace | Unsafe release route/UI | Safe replacement + physical reconnect; **FIXED P0** |
| 020 | Voucher sync discarded | Android action did not execute | 98/98 Sync All; **CLOSED P0** |
| 021 | Session renewal scope | Renewal at one call site; investigated further 2026-08-17 — the classification gap is deeper than one call site (widening + a new cross-feature transport→Company dependency) | **PROPOSED / NOT IMPLEMENTED P1**, non-blocking pilot, pending architectural scoping |
| 022 | Android snapshot memory | Full window held before Room commit | **OPEN, refined P3** (2026-08-17, measured not guessed): automatic background walk already bounded to 30-day windows (not a real risk); manual Voucher Browser refresh has no date-span cap — the real, previously undocumented open gap |
| 023 | Connector query memory | Full snapshot loaded per page | **FIXED P3** (2026-08-17) — `list()`/`search()` now bounded SQL, zero behavior regression |
| 024 | Drive validation | IPC failed to revalidate removability | Automated/live-consistent; **FIXED P1** |
| 025 | Live USB-loss UX | Generic Disconnected after watchdog | **FIXED P2** (2026-08-17) for the mid-session overlay gap; a separate, lower-impact `desktop:restart-connector` stale-config-reuse sub-item remains open, Post-MVP-1 |
| 026 | Carry-forward tombstone | No recheck outside window | **CORRECTED (2026-08-17):** the mechanism already existed (`ReconcileVoucherWindowsUseCase`) and is now proven by 5 automated tests; only a physical-device confirmation of multi-window progression remains |
| 027 | Tally export timing | Export may lag Tally UI | **OPEN OBSERVATION P2**, not BUDCOM defect; reviewed 2026-08-17, no UI change made — a deliberate prior no-workaround decision stands, no new evidence to reverse it |
| 028 | PDF discoverability | Ledger Preview hidden after premature closure | One-tap + physical Preview/Save/Share; **CLOSED P3** |
| 029 | Departed Desktop IP | Server kept old bind | Real-network pass; **FIXED P0** (`5deaf60`) |
| 030 | Invalid IPv6 advert | Advertised unbound address | IPv4-only live browse; **FIXED P0** (`fa3a4ea`) |
| 031 | Wrong TLS port | Pin verified against plain port | `securePort`; combined evidence; **FIXED P0** (`7f40a92`, `b19499e`) |
| 032 | No Android multicast | Missing scoped Wi-Fi lock | OS-level proof; **FIXED P0** (`22381e1`); hotspot external |
| 033 | Standard→Private flow | No migration/warning, trust orphaned | **OPEN/DEFERRED P2**; public data-safety UX issue |
| 034 | Stale marker adoption | Unreadable marker falls through to new vault | **OPEN/DEFERRED P3**; reproduce app-owned path |

## 14. Candidate and version evolution

| Era | Meaningful evidence |
|---|---|
| Foundation | Tags `v0.1.0-m0` through `v0.4.3-milestone-4d`. |
| Historical packaged pilot | `release/desktop-0.4.3-connector-0.4.0`; later hardening initially lacked equivalent artifacts. |
| Hardening | Desktop 0.4.7–0.4.15; Connector to 0.4.6; Android through continuity.19 for TD-029–032. |
| Approved Controlled-Pilot artifacts | Closure records Desktop 0.4.15/Connector 0.4.6 and Android continuity.19/20 with hashes. |
| Current product baseline | Desktop 0.4.16, Connector 0.4.6, Android 0.1.1-continuity.21/22 at `bc9cd55`. |
| Installed/reviewed | Android continuity.21 owner-reviewed 2026-08-17; no Desktop 0.4.16 visual approval record. |

## 15. Automated test and quality evidence

The 2026-08-15 closure run recorded Connector 157 files/1,358 tests, ESLint/TypeScript clean;
Desktop 66 files/674 tests and three TypeScript configs clean; Android 1,002 JVM tests plus lint,
debug assemble and AndroidTest packaging clean. Later hardening/polish records Android 1,021 debug
and 1,021 release JVM tests, both lints/assembles clean, and Desktop 696/696 with TypeScript clean.
Counts evolved as tests were added; these are dated evidence, not a fresh run for this docs-only
reconstruction.

**2026-08-17 pre-signing technical-debt closure — fresh full run, all four components, this
session:** Connector 159 files/1,423 tests passing, ESLint clean, `tsc`/build clean. Desktop 68
files/711 tests passing (708 baseline + 3 new this session: 2 TD-004 regression tests, 1
startup-diagnostics redaction test), `tsc --noEmit` clean across all three configs (main/preload/
renderer), full `npm run build` clean. Contract: 5/5 passing, unchanged. Android (source
unchanged this session, confirmation run): `testDebugUnitTest` 1,030/1,030, `testReleaseUnitTest`
1,030/1,030, `lintDebug`/`lintRelease` both 0 errors, `assembleDebug`/`assembleRelease`/
`assembleDebugAndroidTest` all BUILD SUCCESSFUL; `connectedAndroidTest` deliberately not run (no
physical-device go-ahead this session). Zero regressions found anywhere.

`connectedAndroidTest` packaging succeeded. Specific device/instrumented evidence exists, but the
latest polish checkpoint does not claim a complete fresh connected run. The Quality Scorecard’s
latest result is Controlled-Pilot GO (automated evidence 5, physical evidence 5 in recorded scope).

## 16. Physical and human validation evidence

| Scenario | Result |
|---|---|
| Real Tally / Sync All | **PASS:** 98/98 GUID reconciliation; Ledger/Voucher browsing/type coverage. |
| Mutations | **PASS in closure scope:** add/edit/delete/repeat refresh; TD-026 remains outside-window limitation. |
| Restarts | **PASS:** Desktop/Connector and Android restart; company/identity/trust continuity. |
| Connectivity | **PASS/combined:** router/Jio Fiber and TD-029–032 links; literal two-router run not directly witnessed. |
| Nord 5 hotspot | **ENVIRONMENTAL LIMITATION:** mDNS multicast not forwarded; unicast TCP worked. |
| Private USB | **PASS clean-stop:** unavailable UI, reinsert, byte-identical hash; live/mid-write removal not tested. |
| PDF | **PASS:** Preview, content review, Save and WhatsApp Share. |
| Android UI/UX | **HUMAN VISUALLY APPROVED:** owner inspected installed continuity.21 on 2026-08-17. |
| Desktop UI/UX | **HUMAN VISUALLY & RUNTIME APPROVED:** owner reported the corrected `0.4.18` build good on 2026-08-17, after the `0.4.17` installed-runtime failure was diagnosed and fixed. Technically corroborated: the installed executable's packaged `app.asar` reads version `0.4.18`. |

## 17. Durable architectural decisions

- BUDCOM is local-first. Connector SQLite and Android Room hold durable, company-isolated state;
  Android displays local authoritative data where appropriate and sync remains separate.
- Connector alone owns read-only Tally communication. Future modules do not directly write
  accounting data unless explicitly approved; user-initiated compatible XML remains the boundary.
- Stable identity, atomic snapshot promotion, idempotency, bounded queries and honest recovery are
  required. Partial fetches cannot silently replace authoritative snapshots.
- Discovery is Connector-identity based, not fixed-IP based. QR pairing, device/Connector identity,
  credentials, TLS fingerprint and revocation foundations define the secure direction.
- Private removable storage is a marker-identified fail-closed vault; migration/replacement must be
  explicit before broad use.
- Vouchers form one chronological list with filters; Optional/Estimate is non-accounting. Ledger
  period/default semantics are locked in the Product Decision Log.
- PDF preview/export/share does not alter accounting truth. Desktop remains lightweight; Android
  remains simple; performance complexity must earn business value.
- Git, committed evidence, checkpoint and specialist docs are durable memory. Clean-tree discipline
  applies; agents must not edit the same uncommitted work simultaneously.
- OI/deterministic operations precede generative-AI claims. ChatGPT is recorded product/
  architecture authority; autonomous implementation is bounded by approved scope and gates.

See the ADRs, Product Decision Log, Master Product Execution Plan and Post-MVP-1 Modus Operandi.

## 18. Accepted limitations and deferred work

Pilot constraints are stable router LAN, no phone hotspot for mDNS transitions, no USB removal
while running, no Standard→Private switch, and expected unsigned Windows/Android-debug signing.
TD-017’s literal two-router transition and live mid-write USB pull remain unwitnessed. TD-033/034
remain open/deferred. **Updated 2026-08-17:** TD-004, TD-023, and TD-025's mid-session-overlay gap
are now fixed, not deferred; TD-026 was found already correctly implemented; TD-009, TD-021, TD-022
(refined), and TD-027 remain genuinely open, each for a documented reason requiring architectural
scoping, or a product/security-policy decision, rather than an engineering gap this session could
close unilaterally. Historical internal-drive remnants and orphaned vaults were deliberately
preserved for future review. `docs/diagnostics/m3-stock-items-raw-sample.xml` (~1,500 real-looking
inventory item names, committed 2026-07-22) was reconfirmed unreachable by any packaging path and
left untouched pending an explicit owner decision — not auto-redacted, per the "do not rewrite
history" rule.

## 19. MVP-1 versus MVP-1.1+

### Current MVP-1

Company discovery/selection; read-only Tally integration; Connector/Desktop lifecycle; local-first
Ledgers, Stock and Vouchers; search; sync/snapshots; trusted pairing/LAN discovery; Private storage;
PDF preview/save/share; settings/diagnostics; Controlled-Pilot hardening; bounded UI/UX polish.
MVP-1.0.x permits only defects, missed essentials and bounded micro-polish.

### MVP-1.1 — 🔒 COMPLETE / FROZEN (2026-08-17)

- **MVP-1.1-A (Universal Party Foundation).** Party identity/source-link/provenance/contact-person/
  tag storage foundation, seeding from eligible Tally ledgers, no UI. See phase 20 above and
  `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part A.
- **MVP-1.1-B (Connect Browser + Customers/Prospects + accounting deep links).** First
  user-visible Connect slice. See phase 21 above and Part B.
- **MVP-1.1-C (Party Detail + Prospect creation + Contact Persons + Tags + Notes/Activity).** See
  phase 22 above and Part C.
- **MVP-1.1-D (Tally XML Enrichment Round-Trip).** See phase 23 above and Part D.
- **MVP-1.1-E (Integrated Hardening, Acceptance & Freeze).** See phase 24 above and Part E — the
  final freeze-audit table, human-acceptance checklist, and final Android candidate
  (`continuity.26`, versionCode 27) all live there.

**MVP-1.1 is frozen as a whole.** No further MVP-1.1-scoped work should begin without an explicit
new go-ahead reopening it.

- **MVP-1.2: Relationship Timeline, Issue History, Dincharya and deterministic OI — COMPLETE /
  FROZEN (2026-08-18).** Parts A through E (integrated hardening + freeze) all implemented, tested,
  and version-bumped (`0.1.1-continuity.27`, versionCode 28 — the single coherent MVP-1.2 freeze
  bump). Product decisions locked as PDL-014–PDL-018. Full evidence:
  `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Parts A–E; concise summary
  and exact regression numbers per part: `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` §3. (This
  Ledger entry closes a documentation gap: Parts A–E completed and were recorded in the specialist
  status doc and the current-status checkpoint, but a corresponding Ledger phase entry for D/E was
  not added at the time — see phase 34 below for the consolidated record.)
- **MVP-1.3: Business Profile — COMPLETE / FROZEN (2026-08-18).** Parts A, B, and C (integrated
  hardening + freeze) all implemented, tested, and version-bumped (`0.1.1-continuity.28`,
  versionCode 29 — the single coherent MVP-1.3 freeze bump). Product decisions locked as PDL-019.
  Full evidence: `docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Parts A–C; see phase 35
  below.
- MVP-1.4: Catalogue and governed assets. Not started.
- Vartalap, Insights and broader extensions remain concepts unless later committed evidence says
  otherwise.

Untracked planning/design artifacts do not by themselves make features implemented — MVP-1.1-A's
"technically complete" status above is evidenced by the code, migration, and 43 new passing tests
recorded in phase 20, not merely by the presence of the architecture/spec documents.

## 20. What remains before public release

| Classification | Evidence-grounded work |
|---|---|
| **BLOCKING PUBLIC RELEASE** | Production/release signing (Windows code-signing certificate; Android release keystore) — neither exists in this repository or environment, verified 2026-08-17. Android public Play-Store `applicationId` decision (currently `com.budcom.android`, not formally locked as the public identity — effectively permanent once published). Final signed public artifacts with manifest/checksums and artifact-level regression once signing exists. |
| **RESOLVED 2026-08-17 (public-release prep, part 1)** | Human visual/runtime approval of Desktop (`0.4.18`, owner-confirmed this session). Explicit Standard→Private warning/confirm flow (TD-033). Fail distinctly on unreadable markers (TD-034). First-install/upgrade behavior verified; release notes and Quick-Start guide authored (`docs/planning/BUDCOM-MVP-1-RELEASE-NOTES.md`, `docs/planning/BUDCOM-MVP-1-QUICK-START.md`). |
| **RESOLVED 2026-08-17 (pre-signing technical-debt closure)** | TD-025's mid-session storage-loss overlay gap (the broad-user-facing part of the original "should fix" item below). TD-023's connector query-memory hardening. TD-004's silently-inert Tally host/port settings. TD-026 test-evidence gap closed and registry corrected. Multiple hygiene fixes: test-only privacy sentinels moved out of shipped Desktop source; confirmed unbounded temp-directory leaks fixed in both Connector and Desktop test suites; a content-level log-redaction gap closed in Desktop's startup diagnostics. |
| **SHOULD FIX BEFORE PUBLIC RELEASE** | None identified as newly outstanding after this session's closure pass — TD-025's broad-user gap is now fixed; its remaining `desktop:restart-connector` stale-config sub-item is lower-impact and reasonably Post-MVP-1. |
| **ACCEPTABLE FOR INITIAL LIMITED RELEASE** | Stable-router requirement; literal two-router residual; no USB hot removal; TD-022 (refined — automatic path already bounded, manual Voucher Browser refresh path unbounded but requires a deliberate wide manual date entry); TD-009 (trusted-LAN bind mode has no per-device authN unless secure pairing is separately opted in — a security-policy decision, not an engineering gap); TD-021 (pending architectural scoping). |
| **POST-RELEASE / DEFERRED** | TD-027 observation (reviewed 2026-08-17, no change warranted); UI theme/scaffold/localization, Desktop tray/navigation; MVP-1.1+. |

**Exact NEXT TASK:** obtain and configure Windows code-signing and Android release-signing
credentials (see `docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md` §6 for the exact setup
steps for each), and get an explicit product-owner decision on the public Android `applicationId`
(§7) — both are human/external actions this session could not perform. Once signing exists, re-run
`npm run dist:win` and `./gradlew bundleRelease`/`assembleRelease` to produce signed public
artifacts. **Updated 2026-08-17 (MVP-1.1-E):** MVP-1.1 (A through E) is now complete and frozen —
see phase 24 and `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Part E. This signing/applicationId
blocker remains unrelated to and unchanged by MVP-1.1.

**Updated 2026-08-17 (MVP-1.2 planning session, phase 26):** MVP-1.2's architecture/scope plan is
now complete and repository-grounded —
`docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md`. Before
substantive MVP-1.2-A implementation begins, four open product questions in that document's §3
need a Product Owner/ChatGPT Brainstorm-1 answer: (1) whether Relationship Timeline replaces or
sits alongside the existing flat Notes/Activity presentation, (2) whether Referral Tree belongs in
MVP-1.2 at all (it is absent from the master plan's own locked MVP-1.2 line item despite being
called "VVIMP" in its own spec), (3) whether any part of the still-unwritten Home Insights
workstream entangles with Dincharya, (4) whether Dincharya needs OS-level notifications in v1 (this
plan recommends deferring them). Once answered, the recommended next prompt is that document's
§22 — MVP-1.2-A (structured Party Activity foundation: typed notes, due-date/completion, a new
`party_issues` table, `MIGRATION_8_9`) only, not the full milestone. Do not start MVP-1.2
substantively, and do not start external distribution, before an explicit go-ahead.

**Superseded 2026-08-18 (phases 34-35):** all four questions above were answered, and MVP-1.2 A-E
and MVP-1.3 A-C are both complete/frozen — see phases 34-35 (sections 22-23) for the current,
accurate state. This paragraph is left as historical record of the 2026-08-17 planning snapshot, not
rewritten.

## 21. Development and automation governance

The owner sets outcomes and accepts physical/product results. ChatGPT is recorded product and
architecture authority. Claude implements approved scopes; Codex reconstructs, validates or
implements when assigned. Git/repository documents—not chat—are durable truth.

> INSPECT → IMPLEMENT → TEST → COMMIT → UPDATE RELEVANT STATUS DOC → UPDATE CANONICAL
> CURRENT-DEVELOPMENT CHECKPOINT → CLEAN-TREE AUDIT → RECORD EXACT NEXT TASK

At each new AI session: read the checkpoint; inspect status/recent history; read the relevant
domain status and ledger; reconstruct from repository evidence; continue rather than redo. No
meaningful completed work may exist only in session memory.

## 22. Phase 34 — MVP-1.2 D/E completion (retroactive record) and MVP-1.3-A

This phase closes a documentation gap (§19 above): MVP-1.2-D (Dincharya) and MVP-1.2-E (Integrated
Hardening, Freeze, Final Candidate) were implemented, tested, and frozen on 2026-08-18, with full
evidence recorded in the specialist status doc and the current-status checkpoint at the time, but no
corresponding entry was added to this Ledger. Recorded now, evidence-first, not re-narrated:

- **MVP-1.2-D (Dincharya):** three locked deterministic item types (PDL-018) — Follow-ups/
  Callbacks, Pending Tally Confirmation, Pending Contact Completion — each a new bounded,
  `companyId`-scoped, company-wide DAO query; new `feature/dincharya/` repository; a fourth
  Dashboard entry. 53 new tests. `testDebugUnitTest`/`testReleaseUnitTest` 1,219/1,219 both;
  instrumented suite 309/321 (unchanged 12-failure baseline). Zero schema change. Full evidence:
  `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part D.
- **MVP-1.2-E (Integrated Hardening, Freeze, Final Candidate):** integrated audit of Parts A–D
  together; found and fixed one consequential defect (`SetNoteCompletionUseCase` had zero UI caller
  through B/C/D, wired into Party Detail's note rows). 5 new tests. `testDebugUnitTest`/
  `testReleaseUnitTest` 1,221/1,221 both; instrumented suite 312/324 (unchanged baseline). Version
  bumped to `0.1.1-continuity.27`/versionCode 28 — the single coherent MVP-1.2 freeze bump. Built,
  installed on device `10BF44124K000E3`, and smoke-tested. Pushed to `origin/main` (fast-forward,
  no force, byte-identical post-push verification). Full evidence:
  `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` Part E.
- **MVP-1.3 planning/recovery review (read-only):** produced a complete gap-analysis/architecture
  package (`docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`) identifying five
  genuine open product decisions rather than guessing at scope. No implementation performed.
- **MVP-1.3-A (Business Identity Foundation) — implemented and complete (2026-08-18).** The five
  open product decisions were supplied directly in the implementation session's own governing
  prompt and locked as **PDL-019**: per-company scope, the initial field set, no
  `PartyFieldProvenance`/no Tally round-trip, app-private logo storage behind a
  `BusinessProfileLogoStore` abstraction (file-type allowlist, 5 MB streaming cap, two-layer
  path-traversal defense), owner-side only. New `business_profile` table (`MIGRATION_9_10`),
  full repository/domain/logo-storage layers, and a basic whole-entity owner-side editor wired
  into a fifth Dashboard entry. One self-caught defect fixed pre-test (a Cancel-reverts-to-
  unsaved-data bug); one genuine instrumented-test discrepancy investigated and resolved at the
  test level rather than assumed to be baseline flakiness (root-caused to off-screen Save/Cancel
  buttons on this device, fixed with `performScrollTo()`, confirmed against a comparison test on
  an already-established screen). 35 new tests (17 JVM + 18 instrumented), with dedicated
  adversarial company-isolation coverage at the DAO, repository, and ViewModel layers.
  `testDebugUnitTest`/`testReleaseUnitTest` 1,255/1,255 both; both lints 0 errors; all three
  assembles green; instrumented suite 329/341 (unchanged 12-failure baseline, zero overlap).
  Version not bumped (deferred to Part C's freeze, per this codebase's established per-milestone
  precedent). Full evidence: `docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Part A.

## 23. Phase 35 — MVP-1.3-B and MVP-1.3-C — MVP-1.3 COMPLETE / FROZEN

Continued automatically within the same session as phase 34's MVP-1.3-A, per the governing
prompt's own "continue A → B → C automatically when the preceding gate passes" instruction.

- **MVP-1.3-B (Profile Presentation & Sharing Foundation) — implemented and complete
  (2026-08-18).** Logo picker (`ActivityResultContracts.PickVisualMedia`) and display
  (`BitmapFactory.decodeFile` off the main thread) with Add/Replace/Remove controls.
  `BusinessProfileShareSnapshot` — the internal PDL-019 sharing-foundation data boundary (pure
  projection, zero Intent/export/network wired to it — a real share feature is explicitly reserved
  for a future Vartalap milestone). Self-caught defect fixed: `save()`/`updateLogo()`/
  `clearLogo()` could let a stale async result overwrite a newly-loaded company's state if the
  user switched companies mid-operation — fixed with an `updateIfStillOnCompany()` guard, locked
  in by two dedicated race-condition regression tests. 15 new tests (11 JVM + 4 instrumented).
  `testDebugUnitTest`/`testReleaseUnitTest` 1,266/1,266 both; both lints 0 errors; all three
  assembles green; instrumented `BusinessProfileScreenTest` 12/12 clean. Version not bumped
  (deferred to Part C). Full evidence: `docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md`
  Part B.
- **MVP-1.3-C (Integrated Hardening, Freeze, Final Candidate) — implemented and complete
  (2026-08-18).** Integrated audit of Parts A+B; reviewed and deliberately accepted two minor
  characteristics (Cancel doesn't revert an already-applied logo change; a narrow cosmetic
  notice-message race under a rare concurrent-operation sequence) — zero new defect found. Version
  bumped to `0.1.1-continuity.28`/versionCode 29 — the single coherent MVP-1.3 freeze bump. Full
  post-bump regression: `testDebugUnitTest`/`testReleaseUnitTest` 1,266/1,266 both; both lints 0
  errors (this session's first genuinely-cold lint analysis since MVP-1.2's freeze surfaced a
  known JIT-compilation tooling pathology in the JetBrains Kotlin Analysis API's Lint/UAST bridge
  — investigated via JVM thread dump, confirmed real-but-slow rather than a hang, worked around
  session-locally via `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`, never written into any tracked
  project file); all three assembles green including R8-minified release; instrumented suite
  333/345 (unchanged 12-failure baseline, zero overlap; 345 = 341 from A + 4 new from B). Built,
  installed on device `10BF44124K000E3` (device state re-verified **after** the final
  `connectedDebugAndroidTest` run, per this milestone's own explicit sequencing requirement — its
  known uninstall side effect was confirmed, not assumed, before reinstalling), and smoke-tested.
  Pushed to `origin/main`. Full evidence:
  `docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` Part C.

**MVP-1.3 COMPLETE / FROZEN.**

**Exact NEXT TASK (at the time):** MVP-1.4 planning/recovery review. Superseded temporarily by the
focused polish task in §24 below (explicitly authorized as a side-quest, not a new milestone); MVP-1.4
planning/recovery review remains the task after §24 closes.

## 24. Phase 36 — Ledger Sharing Discoverability + Offline Ledger Performance Hardening

Focused polish/performance task on the already-frozen MVP-1.3 baseline (HEAD `2555c1d` at start) —
explicitly authorized as a bounded side-quest, not a new milestone (no milestone number invented, no
MVP-1.4 work touched). Two independent problems, investigated and fixed with evidence before any
code was changed, per the task's own "trace first, do not assume" mandate.

### A. Detailed Ledger Share discoverability

**Investigated:** `LedgerStatementScreen.kt`'s Share icon used `combinedClickable` — a plain tap
fired `ShareLedgerFast` (shares immediately using the *remembered* default statement mode from
Settings -> Ledger Sharing, Summary unless the user had separately changed that setting), while
`Detailed` was reachable only by long-pressing the icon to open the "Advanced Options" bottom
sheet, selecting the Detailed radio button, then confirming a destination. No second
PDF-generation/sharing implementation existed — `LedgerStatementViewModel.shareStatement()` was
already the single funnel for both paths.

**Fix:** the Share icon's normal tap now opens a small "Share Ledger" menu with three entries —
**Ledger Summary**, **Detailed Ledger**, and **More options…** — reusing the exact same
`shareStatement()`/PDF/share-coordinator machinery via one new event,
`LedgerStatementEvent.ShareLedgerWithMode(mode)`, which shares immediately using the currently
displayed period and the remembered default *destination* (unchanged Settings behavior) with an
explicit mode instead of the implicit remembered one. "More options…" opens the existing
long-press Advanced sheet unchanged (period/destination override, WhatsApp-to-Party, Save/Preview
PDF — all preserved exactly). Long-press on the icon still opens that same Advanced sheet directly,
kept only as a shortcut for muscle memory — nothing on the screen depends on it any more. Added
content descriptions to the icon and each menu item.
`apps/budcom_android/app/src/main/java/com/budcom/android/feature/masterdata/ledger/presentation/{LedgerStatementScreen.kt,LedgerStatementUiState.kt,LedgerStatementViewModel.kt}`.

### B. Offline Ledger performance

**Investigated:** traced both places a user can "open the Ledger" — the Ledger Browser list
(`LedgerBrowserViewModel`/`LedgerRepositoryImpl`) and the Ledger Statement detail screen
(`LedgerStatementViewModel`/`GetLocalLedgerStatementUseCase`). The Statement detail screen was
already fully local-first (from the pre-existing `a4dcf33`/`30e7ed4` "local-first Ledger
persistence" work) — its `load(refreshing=false)` path never touches the network, confirmed by
existing zero-network-call tests; no bottleneck there. The **actual bottleneck**: `LedgerBrowserViewModel`
constructs `LoadLedgersUseCase` for every normal open/search/pagination/retry and
`RefreshLedgersUseCase` only for explicit pull-to-refresh — but both use cases delegated to the
exact same `LedgerRepositoryImpl.loadLedgers()`, which called the Connector network endpoint
*first*, every time, falling back to the Room cache only on failure. This is the identical bug
shape Phase 3E fixed for Vouchers ([[project_budcom_phase3e_offline_voucher]] in memory —
`LoadVouchersUseCase`/`RefreshVouchersUseCase` were textually identical before that fix); the
Ledger side never received the equivalent fix. The same repository method was also the backing
implementation for `SearchLedgersPortImpl` (Universal Search's Ledger provider), so every
Ledger keystroke in Universal Search paid the same live-network cost. Room itself was already
correctly indexed (`cached_ledgers(companyId)`, `cached_ledgers(companyId, name)`) with no N+1 —
`LedgerMovementDao`'s narration/inventory-line reads were already batched, not per-voucher.

**Fix (smallest correct change, reusing the Voucher-established cache-only-vs-refresh split):**
split `LedgerRepository` into `listLedgers()` (Room-only; fails honestly with "No offline data
available. Connect to BUDCOM Desktop and synchronize once." when the company has never been
synced, matching Voucher's own wording) and `refreshLedgers()` (the prior network-first body,
unchanged). `LoadLedgersUseCase` now calls `listLedgers`; `RefreshLedgersUseCase` calls
`refreshLedgers`; `SearchLedgersPortImpl` now calls `listLedgers` too (fixes Universal Search's
identical exposure as a byproduct). `LedgerBrowserViewModel` needed no change — it already
selected the correct use case per its own `refreshing` flag; the bug was entirely inside the
repository. No Room schema change, no new datastore, no pagination/index change — none were
implicated by the evidence.
`apps/budcom_android/app/src/main/java/com/budcom/android/feature/masterdata/ledger/{domain/repository/LedgerRepository.kt,domain/usecase/LedgerUseCases.kt,data/repository/LedgerRepositoryImpl.kt,data/repository/SearchLedgersPortImpl.kt}`.

**Freshness UI (Part G):** `LedgerBrowserUiState.dataFreshnessAt` was already populated end-to-end
by both read paths but never rendered anywhere. Added a one-line "Data last synced: {timestamp}"
label above the search field, visible whenever content is present; the existing pull-to-refresh
gesture remains the only manual-refresh affordance (no new button added, matching "do not
overcomplicate the Ledger UI"). `LedgerBrowserScreen.kt`.

**Measurement methodology and honest limitation:** no Android device or emulator was available in
this session (`adb` not present; no `connectedDebugAndroidTest` run performed) — the specific,
genuine constraint documented per the task's own instructions rather than fabricating a wall-clock
number. Evidence instead: (1) a new JVM regression test,
`LedgerBrowserViewModelTest`."Load, search, retry, and pagination never touch the network path —
only explicit Refresh does", proves 0 `refreshLedgers` calls across init/search/retry and exactly 1
after an explicit `Refresh` event; (2) a new `LedgerRepositoryImplTest` section proves `listLedgers`
reaches a result using only `UnreachableRemote`/`UnreachableAuthenticatedRemote` fakes (calling
either throws), i.e. the cache-only path is structurally incapable of making a network call, not
merely observed not to. This is code-path proof, not a live timing measurement — before this fix,
every normal open paid for a live Connector/Tally round trip (bounded to one attempt by the
pre-existing `RetryPolicy.None` on the Ledger endpoint, per the Phase 3E memory, but still a real
network+Tally round trip); after the fix, a normal open is a single indexed Room read with no
network dependency at all. A live on-device before/after timing (mirroring the Voucher Phase 3F–3H
live validation already on record) was not performed and is recommended as a follow-up when a
device is available, but is not required to trust the fix: the change is a structural
network-call-elimination, not a tuning change whose benefit could only be inferred from timing.

### C. Architecture decision

Room retained as the sole local store — it was already the correct choice and already properly
indexed; the bottleneck was purely in the repository's *read-first* choice of transport, not in
Room itself. No new database/cache technology introduced. No speculative refactor: Desktop
Connector, Tally write paths, Business Profile, Catalogue, Vartalap, Referral Tree, Home
Insights/OI, generative AI, and cloud sync were not touched.

### D. Tests

Baseline (HEAD `2555c1d`, before this task's changes): `testDebugUnitTest`/`testReleaseUnitTest`
1,249/1,249 both, 0 failures (measured this session via `git stash` to isolate the pre-fix tree —
not assumed from an older checkpoint). After: **1,257/1,257 both, 0 failures** (+8 net new tests:
+4 `LedgerRepositoryImplTest` (`listLedgers` cache-hit/no-cache/blank-company/search-respected),
+1 `LedgerBrowserViewModelTest` (the Load-vs-Refresh network-isolation regression above),
+3 `LedgerStatementViewModelTest` (`ShareLedgerWithMode` Detailed/Summary/period-correctness);
`LedgerUseCasesTest`'s one test was rewritten in place to prove `Load`/`Refresh` now call
*different* repository methods, the exact contract the bug violated. Plus 8 new instrumented
(`androidTest`, not run this session — no device) tests: 2 in `LedgerBrowserScreenTest`
(freshness label shown/absent) and 6 in `LedgerStatementScreenTest` (tap opens the two-option
menu; Summary/Detailed/More-options each emit the right event; long-press still opens Advanced
options directly). `:app:lintDebug` 0 errors (87 pre-existing warnings, unrelated
`MonochromeLauncherIcon` on launcher icons). `:app:assembleDebug`/`:app:assembleRelease`
(R8-minified) both green. The known 12 pre-existing instrumented-suite failures could not be
re-verified this session (no device) — **unchanged, not re-confirmed**; this is stated as a
limitation, not claimed as tested.

### E. Accepted limitations

1. No Android device/emulator available this session — no live on-device timing, no
   `connectedDebugAndroidTest` run, no re-confirmation of the 12 known pre-existing instrumented
   failures. Recommended follow-up when a device is available (mirrors the Voucher Phase 3F–3H
   live-validation precedent).
2. `LedgerStatementContentUi.lastSyncedAt` is a pre-existing dead field (always constructed as
   `null` by `LedgerStatementViewModel.load()`, never rendered by `LedgerStatementContent`) —
   noted during investigation, left untouched as out-of-scope (it is not the reported bottleneck
   and populating/rendering it would be a UI change beyond this task's ask).
3. `git diff` renders `LedgerStatementScreenTest.kt`'s change as far larger than the actual edit
   (a 93-line net addition) because the file's many near-identical
   `composeRule.setContent { BudcomTheme { LedgerStatementScreen(...) } }` blocks defeat the line
   diff algorithm (confirmed with Myers/histogram/patience, and independently by full content
   review) — a cosmetic diff-rendering artifact, not a content defect.

### F. Version / Git

No version bump — a focused polish task, not a milestone freeze point, per the task's own explicit
instruction. Not installed to a device (none attached). Commit(s): see `git log` immediately
following this entry.

**Exact NEXT TASK (at the time):** MVP-1.4 planning/recovery review. Superseded temporarily by the
real-device validation in §26 below.

## 26. Phase 37 — Ledger Local-First + Connect Real-Device Validation

Real-hardware validation of Phase 36 (§24), on device `10BF44124K000E3` (OnePlus/I2407, model
`I2407`), against a genuinely live, already-paired environment: BUDCOM Desktop + Tally +
TallyGatewayServer all running on the same Windows machine as this session (connector `trusted-lan`
mode, `192.168.29.34:8080`; phone on the same LAN at `192.168.29.111`), company **estimation**,
with a real, substantial local dataset — **941 Ledgers**, 247 Vouchers per the sync run, real
customer/vendor names and transaction history (not synthetic fixtures). This corrects a stale
claim in an earlier checkpoint (§Exact NEXT TASK, pre-Phase-37) that "device pairing has still
never been attempted" — it plainly had been, well before this session; the note is superseded by
this entry, not by further edits.

### A. Ledger Browser — local-first, live-confirmed

Rebuilt current HEAD (`5b30b09` at start) as debug APK, SHA-256 `1454064a85e2…`, and installed
over the existing `continuity.28` build with `adb install -r` (`firstInstallTime` unchanged,
confirming a genuine update, not a fresh install — app's own `budcom.db`/`datastore` preserved
throughout). Live-confirmed on real data: Ledger Browser open, search, and pagination/scroll all
render fully populated real content (real business names, real closing balances) within
~750-800ms of the tap command including full ADB round-trip overhead, network throughput reading
literally `0.00 KB/s` at the moment of render — genuinely local, not merely fast-over-network.
Explicit pull-to-refresh, by contrast, showed real inflight network traffic (73–94 KB/s) while
never blanking the already-visible cached list, and updated the "Data last synced" label
correctly afterward — the exact local-vs-refresh distinction Phase 36 targeted.

**Offline test (the most important test, per the task):** WiFi disabled via `adb shell svc wifi
disable`, confirmed via `dumpsys connectivity` ("Active default network: none") and a failed ping
to the connector host. Cold-relaunched the app fully offline: Home showed an honest
"Offline · Never synced · Tally unavailable" status and a red "Device is offline. Showing last
known operational data." banner; Ledger Browser opened fully populated (same real 941-ledger list,
same sub-second render) with its own "Device is offline. Showing the most recently retained data."
banner and the "Data last synced" timestamp still shown; search worked fully offline; a Ledger
statement with two real Sales vouchers (Ah Traders Glb, ₹4,020 and ₹128,083.30) rendered its full
transaction history and balances, byte-identical to the online render. A separate, genuinely
never-locally-synced ledger (Adil Electrical Hw Zaheerabad) correctly showed "Closing balance: Not
available" / "No transactions for this ledger in the selected period" rather than fabricating
data — the honest no-cache-for-this-item path. WiFi re-enabled and connectivity independently
re-confirmed before continuing.

### B. Defect found and fixed: `refreshLedgers()` returned the raw network page, not Room

**Live-observed:** after a real network refresh (Sync -> "Run available syncs", full snapshot
warmed into Room, confirmed via direct `sqlite3` inspection of the pulled `budcom.db` — all rows
correctly re-timestamped with the new sync's `dataFreshnessAt`), the Ledger Browser's displayed
first page skipped four ledgers ("Aaijee Traders", "Aarti Sanitations Ramesh", "Abdul Hannan Khan
Mir Alam Mandi Builder", "Abdul Mateen Builder") that sort correctly between "AAI MATA BHANUR" and
"ABU BAKAR JI" — reproduced twice, including after a fully settled (non-mid-animation) screenshot,
ruling out a rendering artifact. Root-caused by direct inspection of
`LedgerRepositoryImpl.refreshLedgers()` (the renamed-but-logic-unchanged former `loadLedgers()`):
it persisted the full multi-page snapshot to Room via `persistSuccessfulPage`/`warmFullSnapshot`
correctly, but then returned `result.value` — the FIRST network response page's own `items`,
whatever order/subset the Connector happened to return for page 1 — instead of re-reading Room's
own name-COLLATE-NOCASE-sorted page, unlike `VoucherRepositoryImpl.persistAndReturn()`'s existing
re-read-after-persist precedent. Confirmed via a JVM regression test reproducing the exact shape
(2-page network fetch, assert the returned page reflects both persisted items, not just the first
page's one item) — failed before the fix, passes after. **Fixed**: `refreshLedgers()` now re-reads
`localDataSource.query(companyId, query)` after persisting and returns that (falling back to the
raw network page only if the Room re-read is unexpectedly null, matching Voucher's own fallback
pattern). Live re-verified after rebuild/reinstall: the same refresh now shows the complete,
correctly-ordered list with no ledgers skipped.
`apps/budcom_android/app/src/main/java/com/budcom/android/feature/masterdata/ledger/data/repository/LedgerRepositoryImpl.kt`
(+9/-1), new test in `LedgerRepositoryImplTest.kt` (+28).

### C. Defect found and fixed: "Run available syncs" never reconciled Parties from Ledgers

**Live-observed:** Connect's Customers tab showed "No customers found yet" even after a genuine,
fully successful "Run available syncs" (Ledgers -> Stock items -> Vouchers, all three "Completed",
247/247 vouchers processed). Root-caused in `SyncViewModel.runAll()`: it calls
`applyOutcome(agg.outcomes.lastOrNull())` — and `applyOutcome()` is the only place that invokes
`maybeReconcilePartiesFromLedgers(outcome)`, which itself only proceeds when
`outcome.target == SyncTarget.Ledgers`. Because this screen always runs Ledgers first and Vouchers
last, `last` is always the Vouchers outcome, so the Ledgers-target check inside
`maybeReconcilePartiesFromLedgers` can never pass through this path — Party reconciliation only
ever fired for a lone per-target "Sync now" tap on Ledgers specifically, never for "Run available
syncs", the more natural everyday action. Confirmed against the existing
`SyncViewModelTest`."run available syncs reaches vouchers after ledgers and stock items" test,
which asserted the target sequence but never asserted `partyRepository.reconcileCalls` for this
exact path — a genuine, pre-existing coverage gap. **Fixed**: `runAll()` now independently finds
the Ledgers-target outcome inside the aggregate (`agg.outcomes.firstOrNull { it.target ==
SyncTarget.Ledgers }`) and reconciles from it directly, regardless of its position in the sequence
— `applyOutcome(last)`'s own UI-state behavior (still showing the final/Vouchers outcome to the
user) is unchanged. New regression test added and passing.
`apps/budcom_android/app/src/main/java/com/budcom/android/feature/sync/presentation/SyncViewModel.kt`
(+7 net), new test in `SyncViewModelTest.kt` (+19).

**Live re-verified after this fix, rebuilt/reinstalled candidate:** ran "Run available syncs"
again (Ledgers completed at a fresh timestamp) — **Connect's Customers tab still showed "No
customers found yet."** Investigated further rather than assuming the fix was wrong: direct
`sqlite3` inspection of the Android app's own `budcom.db` confirmed `cached_ledgers.parentGroup`
is `NULL` for **all 941** real ledgers. Cross-checked the Desktop Connector's own independent
local cache (`connector-data/budcom-ledger.db`, `ledgers.parent_group`) on the same machine: also
`NULL` for all 941 real ledgers — only two non-real rows have it populated at all ("Cash" ->
"Cash-in-Hand", and a "Acme Corp" test/seed ledger -> "Sundry Debtors"), proving the gap already
exists in the Connector's own extracted data, before it ever reaches Android. Since
`LedgerPartyEligibilityPolicy` (MVP-1.1-A, intentionally conservative and already documented as
having exactly this class of limitation) can only classify a ledger as Customer/Supplier by
matching `parentGroup` for "debtor"/"creditor", zero of this real company's ledgers can currently
be eligible — through no defect in the Android app's own reconciliation trigger (now fixed) or
classification logic (already correct and already honest). **Not fixed, and explicitly out of
scope**: the root cause is upstream, in the Desktop Connector's Tally ledger-group extraction —
outside this task's Android-only authorization ("do not touch Desktop Connector unless the
investigation proves a direct dependency," and this investigation proves the opposite: no Android
dependency). Recorded as a genuine, real finding for a future Connector-side task, not silently
worked around and not used to justify broadening the (deliberately conservative) seeding policy.
Confirmed the Prospects tab correctly shows zero fabricated Prospects ("No prospects yet...") —
the dishonest-auto-seeding failure mode this task explicitly checked for did not occur.

### D. Detailed Ledger sharing — live-confirmed, one self-caught tooling mistake along the way

Tapping Share (no long-press) on a real Ledger statement (Ah Traders Glb, 2 real Sales vouchers)
opened exactly the designed "Ledger Summary / Detailed Ledger / More options…" menu. The first
"Detailed Ledger" attempt actually tapped "Ledger Summary" instead, due to an unscaled
screenshot-coordinate arithmetic mistake in this session's own device-automation tooling (caught
by noticing the resulting PDF — 69.23 KB, no item lines — didn't match known real inventory-line
data for those vouchers, confirmed present via direct `sqlite3` query against
`cached_voucher_inventory_lines`); corrected and re-verified. The corrected "Detailed Ledger" tap
produced a 104 KB PDF (pulled from the device via `adb exec-out ... run-as ... cat`, binary-safe;
an earlier `adb shell ... > file` pull method silently corrupted the binary and is not to be
reused for binary pulls) containing full Item Name/Qty/Rate/Amount breakdowns for both vouchers,
totals matching Room exactly (₹4,020.00 and ₹128,083.30), real Android system Share sheet
including WhatsApp as a destination (not completed — deliberately dismissed rather than actually
sending to a real contact). Long-press still opens the same Advanced Options sheet directly.

### E. Tests / build

Baseline at start of this validation session (HEAD `5b30b09`, before any of this session's fixes):
1,258/1,258 both variants (Phase 36's own +8 already included), 0 failures. After both fixes:
**1,259/1,259 both variants, 0 failures** (+1 net: `LedgerRepositoryImplTest` +1,
`SyncViewModelTest` +1, no removals). `:app:lintDebug` 0 errors (87 pre-existing warnings,
unchanged). `:app:assembleDebug`/`:app:assembleRelease` (R8-minified) both green. Final installed
debug APK SHA-256 `a0e04343a0e48ace12f14fd6da9d0036a6894106842132e729c6fd23630ddf56`, byte-verified
identical between the local build output and the APK pulled back off the device.
`connectedDebugAndroidTest` was deliberately **not run** this session — its known
uninstall-the-debug-build side effect would have discarded the validated candidate for no
offsetting benefit, since live manual validation plus the full JVM suite already provided the
evidence this task needed; the known 12 pre-existing instrumented failures were neither
re-confirmed nor disturbed.

### F. Accepted limitations / genuine findings requiring a future task

1. **Connector-side**: real Tally ledgers extracted for company `estimation` carry no `parent_group`
   in the Connector's own cache — Party auto-seeding (MVP-1.1-A) is currently a no-op for this
   real company through no Android-side defect. Needs a Connector/Tally-extraction investigation,
   out of this task's scope.
2. Items 1–2 from the prior checkpoint's "two independent open items" (production signing
   credentials; a by-hand Tally XML import acceptance walkthrough) remain open, unaffected.
3. No architectural change was made to the Party seeding policy — intentionally, per this task's
   own explicit instruction not to broaden it.

### G. Git

No version bump (validation + two small scoped fixes, not a milestone). Commit(s): see `git log`
immediately following this entry. Working tree left clean; no unrelated files touched.

**Exact NEXT TASK (at the time):** MVP-1.4 planning/recovery review. Superseded by the git
preservation, Connector investigation, and planning package produced in §28 below.

## 28. Phase 38 — Git Preservation, Connector `parent_group` Investigation, MVP-1.4 Planning

Four-phase autonomous task, starting HEAD `b7e9377` (Phase 37's own final commit), working tree
clean, `main` 4 commits ahead of `origin/main`.

### A. Git preservation (Phases 1–2)

Independently re-verified branch (`main`), HEAD (`b7e9377`), working tree (clean), and the exact
4 unpushed commits (`e6c7327`, `5b30b09`, `38cbe2c`, `b7e9377` — all recognizable Phase 36/37
Ledger/Sync/docs work, 18 files, 1,469 insertions / 676 deletions, matching what those two prior
sessions actually produced). Scanned the full diff for secrets/credentials — the only matches were
this repository's own documentation naming private RFC1918 LAN IPs (`192.168.29.x`), not a leak.
Pushed with a plain `git push origin main` — genuine fast-forward (`2555c1d..b7e9377`), no force, no
history rewrite. `git fetch` + direct SHA comparison confirmed local `HEAD` == `origin/main`
(`b7e93771c2ba714518502411889dc6588e6d8b0d`) and the working tree remained clean throughout.

### B. Connector `parent_group` investigation (Phase 3) — root cause found, fix deliberately withheld

Traced the complete path Tally → Connector extraction request → XML parsing → Connector cache →
sync payload → Android, in the main repository's own canonical Connector source
(`connector/budcom_connector/`, distinct from and never touching the separate, standing-instruction-
protected `D:\Projects\Budcom_connectivity_hardening\connector\budcom_connector\` worktree used for
unrelated in-progress secure-pairing work).

- **A/B — Tally extraction + XML parsing:** `tally-ledger-mapper.ts`'s `mapTallyLedgerToDomain()`
  correctly reads `parser.getChildText(node, 'PARENT')` into `parentGroup` — the parsing code itself
  has never been the problem.
- **F — root cause, found at the request layer:** `MasterDataTemplates.ledgers()`
  (`connector/budcom_connector/src/extraction/templates/master-data-templates.ts`)'s
  `collectionModifyFetch` array — the explicit TDL FETCH field list sent *to* Tally — does not
  include `'PARENT'`. Tally's XML response for the Ledgers collection therefore never contains a
  `<PARENT>` element for any real ledger; the parser correctly returns `undefined` every time.
  Confirmed at the data layer too: direct `sqlite3` inspection of the Connector's own
  `connector-data/budcom-ledger.db` (`ledgers.parent_group`) shows `NULL` for all 941 real ledgers —
  only a `test/helpers/master-data-fixtures.ts` fixture ("Acme Corp") has it populated, ruling out
  any Android/transport-side loss.
- **Why it's missing — not an oversight.** `git blame`/`git show 8706a80` ("fix(connector): stop
  requesting Tally fields that emit XML-illegal control characters", 2026-08-03) shows `'PARENT'`
  was deliberately removed from both Ledgers' and StockItems' FETCH lists as a workaround for TD-001
  (Tally emitting the XML-1.0-illegal `&#4;` control-character reference inside group/parent text,
  which used to hard-fail the whole response). **TD-001 was subsequently root-caused and properly
  fixed at the shared parsing layer on 2026-08-16** (`TallyXmlResponseParser.parse()`'s
  `sanitizeXml10IllegalCharacters()`, confirmed in code to run unconditionally for every collection,
  not just Vouchers/Groups) — but `PARENT` was never reconsidered afterward. Worse, it wasn't merely
  forgotten: the current `master-data-templates.test.ts` explicitly documents this as a **deliberate,
  informed, post-sanitizer decision** — "The fields below remain deliberately excluded from the TDL
  FETCH lists regardless — avoiding known-artifact-carrying fields is still sensible defense-in-depth
  even though a stray artifact no longer breaks the whole response."

**Concrete downstream cost, confirmed this session:** `LedgerPartyEligibilityPolicy` (MVP-1.1-A) can
only classify a ledger as Customer/Supplier by matching `parentGroup` against "debtor"/"creditor" —
with `parentGroup` permanently `NULL`, zero of ESTIMATION's real ledgers can ever be Party-auto-
seeded, independent of Phase 37's own (unrelated, Android-side, already-fixed) reconciliation-
trigger bug. The same gap would block any future MVP-1.4 Catalogue feature wanting to group products
by Tally Stock Group (`StockItemEntity.parentGroup` has the identical `NULL` problem, same root
cause).

### C. Decision: fix withheld, not implemented (Phase 4)

Phase 4's own gating conditions require the change to, among other things, not require an
architectural redesign and to be adequately covered by existing tests. Re-adding `'PARENT'` fails
neither of those *mechanically* — but the existing test suite's own explicit comment shows this is
not a bug fix, it is **reversing a considered, evidenced, defense-in-depth architecture decision**
made by the same governance process this task's own operating mode names as the architectural
authority ("ChatGPT remains the architectural/product authority"). Two of the existing test
assertions (`master-data-templates.test.ts`, Ledgers and StockItems) directly assert `PARENT`'s
*absence* from the FETCH list — flipping them is not "small, local, tests already cover it," it is
undoing a decision those very tests were written to lock in. **Per this task's own explicit
instruction not to broaden into general Connector work and to treat an unsafe-to-contain fix as
"document, don't implement," no Connector code was changed.** Recorded permanently as
**TD-035** in `docs/technical-debt/registry.md` (full description, evidence, and the exact proposed
fix if a Product Owner/ChatGPT decision approves re-enabling it), and referenced from the new
MVP-1.4 planning document (§D below) since it would also affect any future Catalogue
grouping-by-Stock-Group feature.

**Real-device verification:** not attempted for a fix that was not made — the fix-decision itself
(§C) is the deliverable for this phase, per the task's own "if not safely contained, document" branch.

### D. Prospect → Ledger linking (Phase 5) — recorded, not implemented

Recorded verbatim as specified, as a new entry in `docs/planning/BUDCOM-NOT-NOW.md` ("Prospect →
existing Tally Ledger linking (Connect)"), using that file's own established template (Why
valuable / Why not now / Dependencies / Potential release / Promotion trigger). Explicitly notes its
own dependency on TD-035 (§C above) — Debtor/Creditor classification is meaningless against real
data until that decision is made. No code, schema, or UI was written for this item.

### E. MVP-1.4 planning/recovery review (Phases 6–8)

Read and reconciled `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`, `BUDCOM-PRODUCT-DECISION-LOG.md`
(PDL-001 through PDL-019 in full), `BUDCOM-UI-DESIGN-DECISIONS.md`, `BUDCOM-SCREEN-INVENTORY.md`,
`BUDCOM-NOT-NOW.md`, `BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md` (the direct structural
precedent), `docs/ROADMAP.md`, this Development Ledger, and the Current Development Status
checkpoint. Confirmed directly against source (not assumed): MVP-1.3 is frozen; zero Catalogue code
exists anywhere (Android/Desktop/Connector); MVP-1.3's own `business_profile` table
(`companyId`-keyed, matching PDL-019 §5.1 exactly) and app-private logo-storage abstraction are both
genuinely present and reusable, confirmed by direct inspection of `DatabaseModule.kt`'s
`MIGRATION_9_10`; the pre-existing MVP-1 `StockItemEntity`/`cached_stock_items` foundation is present
and is the natural read-source for a future Catalogue, though the exact relationship is explicitly
undecided.

**Produced:** `docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md` — a full planning package
following this document's own structure precedent, separating LOCKED (ownership boundary, roadmap
position, cross-cutting architecture principles, NOT-NOW exclusions, the confirmed MVP-1.3
dependency) from PROPOSED (the Master Plan §10 "current direction" bullet list — master catalogue
table, SKU IDs, Excel import/export, image-filename/SKU linkage, branch/draft/review/publish,
image-organization workflow — explicitly labeled recovered intent, not locked scope) from nine OPEN
PRODUCT DECISIONS requiring their own Brainstorm 1 (product/SKU field list; Stock Item relationship;
branch/draft/review/publish state machine; Excel exact contract; asset-storage mechanism;
visitor-facing "Resources" timing; Desktop surface; sharing mechanism; company-isolation
confirmation), plus candidate (not decided) sections for company isolation, test strategy, offline
behavior, migration, performance risk, and three candidate sub-milestones (1.4-A/B/C) offered only
as a structural option. **No MVP-1.4 implementation was started or authorized.**

### F. Tests / build

No production code was changed this session (Connector fix withheld by decision, §C) — the existing
1,259/1,259 (both variants) baseline from Phase 37 is unchanged. No Android/Desktop/Connector build
was run this session since no source in any of those trees was modified; documentation-only changes.

### G. Documentation updated this session

`docs/technical-debt/registry.md` (new TD-035 + index entry), `docs/planning/BUDCOM-NOT-NOW.md` (new
Prospect→Ledger entry), `docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md` (new file),
this Development Ledger entry, and the Current Development Status checkpoint (§ below this entry's
commit).

### H. Git / device safety

No force push, no history rewrite anywhere this session. No unrelated production files touched —
this phase is documentation-only (Phase 3's investigation read connector/Android/desktop source and
the local Connector SQLite cache; it did not write to any of them). The Ledger candidate installed on
`10BF44124K000E3` from Phase 37 was not touched this session — no `adb` command of any kind was run;
its state (SHA-256 `a0e04343a0e48ace12f14fd6da9d0036a6894106842132e729c6fd23630ddf56`, data intact,
Phase 37's offline/online Ledger behavior) is presumed unchanged because nothing in this session could
have altered it, not re-verified live (no device action was part of this task's scope).

**Exact NEXT TASK:** Two independent, non-blocking items, neither of which is MVP-1.4
implementation:

1. **MVP-1.4 Brainstorm 1** (User + ChatGPT), using
   `docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md` §5.3 as the exact input — the nine
   open product decisions listed there. Only after that produces its own PDL entry (mirroring
   PDL-019) should an MVP-1.4-A implementation prompt be issued.
2. **TD-035 decision** (`docs/technical-debt/registry.md`): re-enable Connector `PARENT` fetch for
   Ledgers (and separately evaluate StockItems) now that TD-001's sanitizer neutralizes the original
   risk, or explicitly ratify the current defense-in-depth exclusion and accept the Party/Catalogue-
   grouping limitation as permanent. Recommended to resolve before Prospect→Ledger linking
   (`docs/planning/BUDCOM-NOT-NOW.md`) is ever scheduled.

**Do not begin MVP-1.4 implementation without an explicit new go-ahead following Brainstorm 1.**

## 29. Phase 39 — TD-035 Permanent Resolution + MVP-1.4 Product Decision Lock

Autonomous 13-part task (Product Owner/ChatGPT pre-approved per the task's own operating mode),
starting HEAD `141b92d` (Phase 38's own final commit), working tree clean, `main` 1 commit ahead of
`origin/main` (that same unpushed `141b92d`).

### A. Baseline re-verification (Part 1)

Did not trust Phase 38's documentation alone. Re-traced the live code directly and found one
correction to Phase 38's own account: `tally-ledger-mapper.ts`'s `mapTallyLedgerToDomain()` — the
function Phase 38 described as reading `PARENT` — is **dead code**, unreferenced anywhere outside its
own file. The real, live-wired ledger mapper is `entity-mappers.ts`'s `mapLedger()`, reached via
`extractor-registry.ts` (`mapNode: mapLedger`) → `master-data-extractor.ts`. All Part 2/3 investigation
below was re-done against the correct, live path.

### B. TD-035 safety investigation (Parts 2–3) — trust boundary proven before any fix was written

Per this task's explicit governing rule ("do NOT simply add `PARENT` back because TD-001 exists —
first prove the trust boundary"), traced every place a Connector-parsed `parentGroup` value is
consumed, end to end:

- **Inbound boundary:** `TallyXmlResponseParser.parse()`'s `sanitizeXml10IllegalCharacters()`
  (TD-001's fix, 2026-08-16) runs unconditionally for every collection, confirmed by direct read of
  the parser code — not assumed from the earlier session's documentation.
- **Outbound boundary:** `request-builder.ts`'s `escapeXml()` is comprehensive for every value it
  interpolates into a new outbound Tally XML/TDL request.
- **The critical finding:** a parsed `parentGroup` is **never** fed back into `escapeXml()`'s
  territory anywhere in the codebase — grepped every call site; `parentGroup` only ever flows into
  parameterized SQLite storage, the Connector's own JSON REST responses, and plain string comparisons
  (hierarchy validation). It is never interpolated into generated TDL/XML. This closes the exact
  injection vector Part 2 required checking for: a returned value can never become executable/
  generated TDL/XML syntax, because it never reaches the boundary that builds one.
- Confirmed the live pipeline (`CollectionEntityParser.parseDocument()` → `mapLedger()`) is the same
  shared, sanitizing parser every other collection uses — no second, parallel extraction mechanism
  exists or was created.

**Conclusion: proven safe, not assumed safe.** Existing architecture required no redesign to support
a safe fix — Part 6's "stop at the investigation boundary" branch did not apply; Part 3's implementation
branch did.

### C. TD-035 fix + adversarial regression coverage (Parts 2, 3, 10)

Restored `'PARENT'` to `MasterDataTemplates.ledgers()`'s `collectionModifyFetch`
(`connector/budcom_connector/src/extraction/templates/master-data-templates.ts`) — **Ledgers only**.
StockItems' identical `PARENT`/`BASEUNITS`/`GSTAPPLICABLE` exclusion was deliberately left untouched:
those fields were not independently verified by this investigation, and Catalogue's own future use of
them is out of this task's scope (Part 13's anti-scope-creep rule). Synced the parallel
`LEDGER_RICH_FETCH_FIELDS` constant in `ledger-identity.ts`, found via `git show 8706a80` to have been
touched by the original TD-035-causing removal commit alongside the FETCH list itself.

Added 18 new permanent adversarial regression tests in `entity-mappers.test.ts` (describe block
`mapLedger PARENT adversarial coverage (TD-035)`), run against the real live pipeline (parse →
`mapLedger`), not a mock: the exact `&#4;` decimal reference, `&#x4;` hex reference, and literal 0x04
byte (the precise TD-001/TD-035 original failure class, now a **permanent regression test** per Part
2's explicit requirement), `&amp;`/`&lt;`/`&gt;`/`&quot;`/`&apos;` round-trip, newline/tab preservation
mid-string, Devanagari Unicode, unusual punctuation, a fully-escaped nested-tag-looking value, a
500+-character value, a combined case, and both empty-PARENT/absent-PARENT edge cases (→ `undefined`,
matching `normalizeText()`'s documented contract). Updated the two `master-data-templates.test.ts`
assertions this flipped (Ledgers' FETCH-list expectation now includes `PARENT`; the shared TD-001
sanitizer-proof test's scope narrowed to StockItems, which it still correctly protects).

Full Connector regression run: **159/159 test files, 1,437/1,437 tests passing** — the complete
existing suite, none removed or weakened, zero regressions. `tsc --noEmit` and `eslint` both clean.

### D. Connect classification boundary (Part 4)

Added `LedgerGroupClassification` (`DEBTOR`/`CREDITOR`/`OTHER`/`UNKNOWN`) to
`LedgerPartyEligibilityPolicy.kt` as a centralized, deterministic classification derived from raw
`parentGroup` — `classifyGroup()` does the keyword matching; the existing `classify()` (used by
`ReconcilePartiesFromLedgersUseCase`) now delegates to it, provably preserving its exact prior
DEBTOR/CREDITOR→seed, OTHER/UNKNOWN→null behavior (a dedicated "classify and classifyGroup agree
exactly" cross-check test over 7 cases). Classification does not depend on display-name similarity;
Prospects (a BUDCOM-only concept, never Tally-ledger-backed) are not reachable by this path at all, so
they cannot be misclassified as Debtor/Creditor by construction.

### E. Company isolation (Part 5)

`PartyRepositoryImpl`'s persistence layer already had strong `companyId`-scoping test coverage; the
one real gap was the `ReconcilePartiesFromLedgersUseCase` orchestration layer's test fake not being
company-aware. Added `CompanyScopedFakeLedgerSnapshotPort` plus 2 new adversarial tests proving
identical ledger name/alias/`parentGroup` (and a ledger unique to only one company) across two
companies never leak into the other company's seed set.

### F. Tests / lint / build (Part 10)

Connector: 159/159 files, 1,437/1,437 tests; `tsc`/`eslint`/`npm run build` all clean; fix confirmed
present in the compiled `dist/` artifact. Android: 1,266/1,266 tests both variants (+7 new), `lintDebug`
0 errors (87 pre-existing warnings, unchanged), `assembleDebug`/`assembleRelease` both green (the
combined Gradle command exceeded the foreground timeout and completed in the background — no
corrective action needed). Independently re-confirmed after the CRLF cleanup (§I) with a fresh
`testDebugUnitTest`/`testReleaseUnitTest`/`lintDebug` run: same 1,266/1,266 both variants, 0
failures/errors (JUnit XML aggregated directly, not read from console text), 87 `Warning`-severity
lint issues, 0 `Error`-severity — this rerun surfaced and required fixing an environment issue, not a
code issue: the shell's default `java` on `PATH` is a JDK 8 (`1.8.0_401`), too old for AGP 8.8.2/KSP
2.1.10, causing an unrelated dependency-resolution failure until `JAVA_HOME` was pointed at Android
Studio's bundled JBR (JDK 21) for the invocation — noted here since a future session hitting the same
"Could not resolve... requires at least JVM runtime version 11" error should look here first rather
than re-diagnosing it. No real-device re-verification was performed this session — no `adb`
command was run; the fix's live effect on Connect's customer count (last observed as zero on the
physical device, Phase 37) remains to be confirmed next time that device is used. This is recorded
honestly as **deferred**, not claimed as proven.

### G. MVP-1.4 nine product decisions locked (Part 7)

Added **PDL-020** to `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md`, transcribing this task's nine
decisions verbatim (SKU identity; Stock Item relationship; lifecycle; Excel as interchange contract,
not source of truth; asset storage reusing the Business Profile abstraction pattern; visitor-facing
Resources deferred but architecturally provisioned; no Desktop surface; sharing via the existing
Android mechanism; strict company isolation), mirroring PDL-019's structure exactly. Updated
`docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md`'s header and §5.3 with the same
"ALL RESOLVED — PDL-NNN" blockquote pattern MVP-1.3's own architecture document established for
PDL-019, plus a per-question `RESOLVED — PDL-020` marker and locked-answer summary for each of the
nine questions (the original reasoning-record text is preserved below each marker, per the same
precedent). Also corrected §13 and §14's now-stale "TD-035 spillover" risk language to reflect that
TD-035 is resolved for Ledgers, with StockItems' identical exclusion explicitly named as the
still-open residual risk.

### H. Prospect → Ledger future capability (Part 9)

Unchanged this session — the Phase 38 entry in `docs/planning/BUDCOM-NOT-NOW.md` was read to confirm
it remains present, recorded-only, and not implemented; no edits were made to it.

### I. Git / device safety, and a self-caught CRLF defect (Part 12)

Before staging, `git diff --stat` showed suspiciously large changes in three Connector files
(`ledger-identity.ts`, `master-data-templates.ts`, `entity-mappers.test.ts`) compared against
`git diff --ignore-space-at-eol --stat` — a recurring Windows-tooling hazard this project has hit
before. Confirmed via direct byte inspection that the working tree had picked up 59/130/177 CRLF line
endings respectively in files whose `HEAD` blobs are pure LF, stripped the injected `\r` bytes with
`sed`, and re-ran the full Connector suite (159/159, 1,437/1,437 — unchanged) plus `tsc`/`eslint`
(clean) to confirm the cleanup introduced no regression. Post-cleanup diffs match the
`--ignore-space-at-eol` baseline exactly. No `adb`, no force push, no history rewrite; the Ledger
candidate installed on `10BF44124K000E3` from Phase 37 was not touched this session.

### J. Stop-condition compliance (Part 13)

MVP-1.4-A/B/C implementation was **not started** — no Catalogue table, screen, or code exists anywhere
in the repository as a result of this session. Work performed was limited exactly to: TD-035
investigation and permanent resolution (safe within existing architecture, so implemented in full,
not merely documented); TD-035 regression evidence; the nine PDL-020 product decisions; architecture/
status/decision documentation updates; and leaving the repository at a clean, unambiguous baseline for
a future, separately-authorized MVP-1.4-A implementation prompt.

**Exact NEXT TASK (as recorded at the time of the Phase 39 report):** Issue an MVP-1.4-A
implementation prompt using `docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md` §5.3 (now
PDL-020-resolved) as the locked input — this is the first work item PDL-020 actually unblocks.
Independently, StockItems' `PARENT`/`BASEUNITS`/`GSTAPPLICABLE` exclusion remains open and would need
its own safety investigation (mirroring §B above) before any Catalogue feature groups products by
Tally Stock Group.

### K. Follow-up: push + device install (2026-08-19, explicit user request)

The Phase 39 report above (§I/§J) correctly stated "not pushed" and "no `adb` used" as of that
report. Immediately afterward, the user explicitly asked to push TD-035 + PDL-020 and install the
latest build on the real phone — both superseding those two statements, recorded here rather than
edited into the original report text.

**Push:** `git push origin main` — clean fast-forward `b7e9377..2feb9b2`, no force, no history
rewrite. `git fetch origin` + `git rev-parse` confirmed local `HEAD` and `origin/main` both resolve to
`2feb9b2bddc2838efafaf7de61e90e1ec33ffbc0`.

**Device install:** Checked `adb devices -l` first (per this project's own ADB-disconnect protocol) —
device `10BF44124K000E3` (I2407i) connected and authorized. Rebuilt `assembleDebug` from the pushed
HEAD (`JAVA_HOME` pointed at Android Studio's bundled JBR/JDK 21 — the default `java` on `PATH` is a
JDK 8 that cannot configure this project, the same environment issue found and worked around earlier
in Phase 39 §F); Gradle reported the APK already up-to-date with current source. Compared against the
already-installed package via `dumpsys package com.budcom.android.debug` before installing (versionCode
29, `lastUpdateTime` already today from an earlier install this same calendar day whose provenance
this session did not otherwise trace — not investigated further since a same-version reinstall is
safe and was the correct action regardless). `adb install -r` → `Performing Streamed Install /
Success`; `lastUpdateTime` advanced to `2026-08-19 07:48:12` while `firstInstallTime`
(`2026-08-18 18:29:20`) stayed unchanged, confirming a genuine content update over the existing
install (no version bump — this is a code-only Phase 39 update, not a freeze candidate). Launched via
`adb shell monkey -p com.budcom.android.debug -c android.intent.category.LAUNCHER 1` (the
`am start -n com.budcom.android/.MainActivity` component name used by earlier sessions no longer
resolves; the real activity is `com.budcom.android.app.MainActivity` — recorded for future sessions).
Confirmed `mFocusedApp` became `com.budcom.android.debug/com.budcom.android.app.MainActivity`; zero
`FATAL EXCEPTION`/`AndroidRuntime` crash entries in logcat (two benign vendor SELinux `avc: denied
... proc_fas` warnings, a known OEM/vivo-kernel artifact unrelated to app code). This was a
launch-only smoke check — deeper in-app navigation was not re-exercised this follow-up. New debug
APK: 15,185,874 bytes, SHA-256 `7fd36811d69bb5565d838020af4f79f4f7a29f14558a0bb2513454d6caed4592`.

**Exact NEXT TASK (superseding the one recorded in §J above):** unchanged in substance — an
MVP-1.4-A implementation prompt is still the next authorized-scope work item, still requiring its own
separate go-ahead; the push and install just performed do not themselves authorize MVP-1.4
implementation.

## 30. Phase 40 — Desktop Startup & Connection Stabilization

Focused Desktop stabilization task (not a feature/architecture task, per this task's own explicit
scope lock): audit and harden the launch → initialization → connection → Connected/idle experience
in `apps/budcom_desktop`. Starting HEAD `0d91eac` (Phase 39's push-confirmation commit), working
tree clean, `main` 1 commit ahead of `origin/main`.

### A. Reconnaissance (read-only)

Traced the full startup path before changing anything: `main.ts` (`bootstrapApp()` →
`app.whenReady()` → `createMainWindow()` + fire-and-forget `startConnectorWithRouteResolution()`),
the connector lifecycle state machine (`connector-lifecycle-service.ts`: `starting` / `connected` /
`reconnecting` / `disconnected` / `failed`, already covered by extensive existing tests — bind-
integrity mismatch detection, bounded restart attempts, trusted-LAN eligibility via a separate
`TrustedLanRebindCoordinator`), the renderer state machine (`app.ts`'s `getDisplayConnectionState()`
combining that lifecycle state with live `DashboardState` health), and the storage-gate first-run/
timeout/unavailable screens (`storage-gate.ts`, already hardened by TD-025/TD-033). Confirmed via
grep that this app has **no system tray implementation at all** — Section 11 of the governing task
(tray/window consistency) is not applicable to this codebase. Confirmed `lifecycle-error-mapper.ts`
already produces plain-language failure messages with no raw stack traces reaching the primary UX,
and no caller ever passes the raw-`detail` override path. This is a mature, already well-tested
state machine (TD-014/TD-025/TD-033 hardening visible throughout) — the task was to find the actual
remaining defects, not to rebuild what already works.

### B. Root cause 1 — blank/white window flash at cold launch

`createMainWindow()` (`src/main/main.ts`) constructed the `BrowserWindow` with `show: true` while
*also* wiring a `ready-to-show → window.show()` handler — redundant, and the redundancy was the tell:
with `show: true`, Electron displays the OS's default blank/white frame immediately at window
creation, before `loadFile()` + CSS + the renderer's first paint complete, which is exactly the
"first screen is not stable" / "blank screens" symptom this task was scoped to fix. Fixed by setting
`show: false` (so the pre-existing `ready-to-show` handler is now the only thing that reveals the
window, once real content has painted) and adding `backgroundColor: '#0b1428'`, matching the
renderer's own `--bg` theme token (`main.css`), so even the hidden initial frame is the app's real
dark theme rather than white if ever glimpsed (e.g. via the OS task switcher) before `ready-to-show`
fires.

### C. Root cause 2 — dropped lifecycle push during initial renderer load

In `startDesktopShell()` (`src/renderer/scripts/app.ts`), the `window.budcomDesktop.onStatusUpdated`
push-listener was registered **after** the initial `refreshUi()` + `loadCompanies()` pull completed.
A fast main-process lifecycle transition (e.g. a connector reaching `'connected'` within the same
window as the very first render — realistic for local-only mode with an already-warm connector) could
push its `desktop:status-updated` event before any listener existed to receive it, silently dropping
it. Tab navigation never re-polls, and if the session already had an active company selected (the
common case on relaunch), TD-014's bounded-recovery loop is satisfied on unrelated criteria
(`isDashboardHealthy()` only checks `connectorReachable`/`sessionStatus`, not the renderer's own
`lifecycleStatus` mirror) and never retries — so nothing else would ever re-poll, and the renderer
could stay stuck showing a stale state (e.g. "Starting connector…", overriding an otherwise-correct
"Connected" dashboard read via `getDisplayConnectionState()`'s late `lifecycleStatus === 'starting'`
override) indefinitely. Fixed by moving the `onStatusUpdated` registration to before the initial
`refreshUi()`/`loadCompanies()` pull (but still after the blocking storage-gate resolution, so a push
arriving mid-storage-gate can never race a second concurrent `renderStorageGate()` invocation). A push
arriving during the initial pull now simply coalesces into it via `refreshUi()`'s own pre-existing
`trailingRefreshQueued` reentrancy guard — no new locking, no delay-based workaround, matching this
task's explicit "fix the underlying cause, not `setTimeout()`" rule.

### D. Tests added

- `test/unit/main-window.test.ts` — new case asserting `BrowserWindow` is constructed with
  `show: false` and `backgroundColor: '#0b1428'`, and that the mocked `ready-to-show` handler still
  reveals it (regression guard against the window being left permanently hidden).
- `test/renderer/dashboard-recovery.test.ts` — new case simulating a `desktop:status-updated` push
  firing as a side effect of the very first `getLifecycleStatus()` call (i.e. while the initial
  startup pull is still in flight): asserts the push is not dropped (`getLifecycleStatus` is called
  again) and the renderer converges on "Connected" rather than staying stuck on the stale
  `'starting'` snapshot — proving the exact race in §C is closed.
- One pre-existing Windows CRLF-injection hazard (recorded as a recurring pattern in Phase 39 §I too)
  hit `main-window.test.ts` specifically during editing — caught via `git diff --stat` showing the
  whole file rewritten, fixed by stripping the injected `\r` bytes before committing; re-verified the
  cleaned diff is minimal and the tests still pass.

### E. Full regression / build

Connector untouched (zero files changed there). Desktop: **68/68 test files, 713/713 tests passing**
(711 pre-existing + 2 new, 0 regressions); `tsc --noEmit` clean for the `main`/`preload`/`renderer`
TypeScript projects; full production `npm run build` (main + preload + renderer + asset copy +
build-info) clean. Android untouched (out of scope, not touched).

### F. Real Desktop launch validation — attempted, environment-limited

Attempted a live cold-launch validation (`node_modules/electron/dist/electron.exe .` against the dev
build) per this task's own request. Two genuine environment constraints, honestly recorded rather
than worked around with a fabricated result: (1) this session has no attached interactive Windows
desktop/display — a direct screen-capture attempt (`System.Drawing.Graphics.CopyFromScreen`) failed
with "the handle is invalid", so no screenshot could be taken even had the window opened; (2) this
machine already has a **separate, pre-existing, currently-running installed instance** of
`Budcom Desktop.exe` (`C:\Program Files\Budcom Desktop\`, PID 7000 + GPU/utility/renderer children),
using the same `userData` directory (`%APPDATA%\@budcom\desktop`) the dev build would use — the dev
launch attempt (PID 9916) almost certainly hit `requestDesktopSingleInstance()`'s existing "already
running, quit immediately" path and exited cleanly on its own; this is correct designed behavior, not
a bug, and that pre-existing instance was deliberately left completely undisturbed (not
inspected further, not restarted, not killed) since it is not this session's to manage. Confirmed no
stray process was left behind by the attempt (`tasklist` clean for `electron.exe` afterward). The
window-chrome/first-paint fix (§B) is therefore verified at the code + automated-test level only
(dedicated `show`/`backgroundColor`/`ready-to-show` regression test in §D) — not by direct visual
observation. This limitation is stated explicitly per this task's own instruction rather than
claiming a visual confirmation that did not happen.

### G. Scope discipline

Left completely untouched, as required: Android, Catalogue/MVP-1.4, Connector protocol/API contracts,
Tally synchronization architecture, tray behavior (none exists), the Connector LAN-binding fix, and
the existing IPC channel allowlist/contract. No version bump — per this task's own Section 17 and the
repository's own established pattern (visible throughout this ledger: several `fix(desktop)` commits
accumulate before a single later `chore: bump desktop X->Y` commit bundles them into a named release
candidate), a version bump was deliberately deferred rather than performed opportunistically here.
Nothing pushed — commits prepared locally only, per this task's explicit push rule.

### H. Follow-up: push + real launch validation (2026-08-22, explicit user request)

The Phase 40 report above (§F) correctly stated the window-flash fix was "verified at the code +
automated-test level only — not by direct visual observation." The user reviewed and approved the
three commits, then explicitly asked to push and perform real Desktop launch validation — both
superseding that statement, recorded here rather than edited into the original text.

**Push:** re-verified branch (`main`), HEAD (`4b75ed7`), clean working tree, and all three approved
commits (`2a62a6e`, `22126e7`, `4b75ed7`) by direct `git show` inspection before touching anything —
confirmed unchanged from what was reported and reviewed, no unrelated files. `git fetch origin` first
confirmed `origin/main` was still at the prior `2feb9b2` with zero divergence (`0	4` ahead/behind).
`git push origin main` — clean fast-forward `2feb9b2..4b75ed7`, no force, no history rewrite.
Post-push `git fetch origin` + `git rev-parse` confirmed local `HEAD` and `origin/main` both resolve
to `4b75ed7f59383aff7add0a7923da64d6711df571` — exact match, working tree clean.

**Real launch validation — performed successfully, with genuine care taken around a live machine.**
Before touching anything, discovered this machine had (1) a real, currently-running installed
`Budcom Desktop.exe` instance (pre-existing, pre-fix build, `C:\Program Files\Budcom Desktop\`) using
the production `userData` directory, and (2) a live, actively-being-used TallyPrime session (company
`ESTIMATION`) — a full-screen screenshot taken to orient caught an in-progress, unsaved Sales Voucher
entry that visibly changed between two screenshots seconds apart, proving real concurrent activity on
the machine. Per this task's explicit instruction ("do not terminate an existing user process
blindly"), the production instance and Tally were **never touched, closed, or interacted with** —
no window focus was stolen, no clicks/keystrokes were sent to anything on the machine.

Instead, used the repository's own pre-existing, purpose-built isolation mechanism
(`BUDCOM_INSTALLED_PROBE_MODE`/`BUDCOM_USER_DATA_DIR`, `src/application/release/startup-environment.ts`,
already exercised by `scripts/lifecycle/installed-first-launch-probe.mjs`) to launch the **dev build
with both fixes** in a fully isolated profile: its own `mkdtemp`'d `userData` directory under the OS
temp root, `BUDCOM_SKIP_SINGLE_INSTANCE=true` (no interaction with the production instance's lock),
and an ephemeral connector port — so the probe could never collide with, adopt, or in any way affect
the production instance, its Connector, or Tally. A `private-storage-locator.json` was pre-seeded
with `mode: 'standard'` to simulate a returning user's second+ launch (this task's own most-requested
scenario) without any UI interaction. Ran **four** such isolated launches in total; all four were
headless (log/timestamp observation only, no GUI interaction) except the last two, which added a
**targeted** `PrintWindow(PW_RENDERFULLCONTENT)` capture scoped to the probe's own window handle by
process id — proven immune to whichever window has OS focus (a full-screen `CopyFromScreen` attempt
was tried first and once captured this very Claude Code conversation window instead of the probe,
confirming full-screen capture is unreliable/inappropriate in this environment and was abandoned).

Real evidence obtained, all four runs consistent (no flakiness):
- **Cold-launch window timing** (proving the `show:false` fix's actual mechanism, not just its
  config): `window_created` → `ready_to_show` gaps of 390ms/311ms/etc. across runs — the window is
  provably hidden for that entire span before Electron's own `ready-to-show` reveals it, exactly the
  window during which the old `show:true` code would have exposed a blank/white frame.
- **The exact race window this task's second fix closes is real, not hypothetical**: `'starting' ->
  'connected'` transitioned in ~1.05s–1.07s across runs, and the renderer's `loadCompanies()` (logged
  as "Discovered N companies") completed only ~93–102ms after that — a tight, realistic window
  matching the root-cause analysis in §C exactly.
- **Real Tally connectivity succeeded** every run: the isolated Connector genuinely reached the live
  TallyPrime instance and repeatedly discovered real companies from it (read-only "list companies"
  calls; nothing was written to Tally).
- **Two targeted screenshots** (process-id-scoped, capturing only the probe's own 1200x800 window,
  never anything else on the machine): the early-state capture showed a fully-rendered, correctly
  dark-themed dashboard with an honest "Unknown" connection state and neutral placeholders — never a
  false "Connected" claim; the post-connection capture (taken 800ms after the log-observed
  `'starting'->'connected'` transition — precisely astride the fixed race window) showed a correctly
  rendered "Connected" state: green indicator dot, "Version 0.4.6 · Connected", "Health: ok", "No
  company selected" (accurate for a fresh isolated profile), no layout corruption, no stale/
  contradictory text anywhere.
- **Deterministic across repeats**: all four launches followed the identical stage ordering with no
  hangs, no crashes, no orphaned processes.

**No defect was found.** Both fixes behave exactly as designed under real conditions against a real
Tally instance; the pre-existing (untouched) connection-lifecycle machinery continues to work
correctly end-to-end. Per this task's own Section 10 ("only fix a defect if... reproducible... a
genuine correctness or UX-state problem"), no code change was made this follow-up — nothing to fix.

**Cleanup verified exhaustively after every run**: `tasklist` confirmed zero stray `electron.exe`
processes, the production `Budcom Desktop.exe` instance's four process IDs were byte-identical before
and after every probe run (never restarted, never touched), Tally's process ID was likewise unchanged
throughout, and every isolated probe's temp `userData` directory was removed. The two screenshot files
were reviewed then deleted from the local scratch directory after use (they contained only the
isolated probe's own window content — no unrelated application data).

**Version:** not bumped — same reasoning as §G, unchanged by this follow-up.

**Exact NEXT TASK:** unchanged in substance from §G — an MVP-1.4-A implementation prompt (or any
other net-new feature work) remains a separate, not-yet-authorized work item. This stabilization
checkpoint is complete and pushed; nothing further is queued from it.

## 31. Current source-of-truth references

- Current checkpoint: `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`
- MVP-1.1 Connect/Universal Party: `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`
- MVP-1.2 Relationship Timeline/Dincharya: `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md`
- MVP-1.3 Business Profile: `docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md`
- MVP-1.4 Catalogue (planning only, not authorized): `docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md`
- Controlled Pilot: `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md`
- Quality: `docs/governance/BUDCOM-QUALITY-SCORECARD.md`
- Technical debt: `docs/technical-debt/registry.md`
- UI/UX: `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md`
- Refinement/physical acceptance: `docs/planning/BUDCOM-MVP-1-0-X-REFINEMENT-REGISTER.md`
- Scope/roadmap: `docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`, `docs/ROADMAP.md`
- Architecture/governance: `docs/architecture/`, Product Decision Log, Post-MVP-1 Modus Operandi
- Release/runbooks: `docs/architecture/controlled-pilot-release.md`, `docs/operations/`
- Historical evidence: `docs/milestones/`, `docs/stage-updates/`, `docs/diagnostics/`,
  `docs/PROJECT_PROGRESS.md`
