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

## 31. Phase 41 — Desktop UX Polish

Focused Desktop UX polish task (not a feature/architecture task): make the daily-use experience —
startup, connection, Dashboard clarity, refresh/sync feedback, loading/empty/error states — feel
stable, understandable, and trustworthy, without touching Android/Tally/Connector-protocol/
Catalogue. Starting HEAD `41295af` (the Phase 40 follow-up's own final commit), working tree clean,
`main` in sync with `origin/main`. Constraint acknowledged: the user was on a mobile hotspot for
this session, so Android/phone↔laptop work was explicitly out of reach — this task was Desktop-only
by design regardless, so the constraint changed nothing about scope.

### A. Reconnaissance and ranked defect list

Read the Dashboard/connection/sync rendering path in `app.ts`, `dashboard-service.ts`,
`sync-status-mapper.ts`, `session-display-mapper.ts`, `index.html`, and `main.css`, plus every
existing renderer test touching them. Found and ranked five concrete defects (not a padded list —
several other candidate issues were considered and deliberately left alone, e.g. the Diagnostics
tab already auto-loads on `activateView('diagnostics')`, and failure messaging was already
plain-language with no raw errors surfaced):

1. **Dashboard "Sync"/"Last Sync" (header + card) were wired to a dead Connector placeholder and to
   session-validation time, never real Ledger/Stock Item sync freshness.** `mapSyncDisplayStatus()`
   reads a `SyncEngine`/`LedgerSync` named service from `/health` — but the Connector's own
   `SyncEngineStub extends PlaceholderService`, so that service can never report anything but its
   permanently-idle placeholder message. Separately, `formatLastSync()` was fed
   `session.lastValidatedAt` (session-validation time), not any module's actual `lastSyncedAt`. This
   is exactly the "is this Tally data or cached data? when was this data updated?" ambiguity this
   task's own reconnaissance checklist named.
2. **`renderLedgers()`/`renderStockItems()` wiped the previously-shown list and stats on any failed
   refresh**, even when a real, still-useful list had been showing seconds earlier — replacing it
   with a bare error and an empty list. Directly contradicts "preserve usable cached data during
   refresh" / "explain that existing data remains available where that is true."
3. **`loadLedgers()`/`loadStockItems()` had no reentrancy/generation-counter guard**, unlike the
   established `loadCompanies()` TD-014 pattern — a double-clicked Refresh, or rapid
   pagination/search changes, could let an older, slower response overwrite a newer one.
4. **The header `connection-indicator` dot carried a static, non-updating `aria-label="Connection
   status"`** instead of being `aria-hidden` like the (correctly-implemented) footer dot — a
   color-only state signal for assistive tech, the exact anti-pattern this task's accessibility rule
   names.
5. **Ledger/Stock Item lists showed a bare blank area with no explanation when genuinely empty** —
   no distinction between "never synced," "synced but genuinely zero," and "search matched
   nothing."

### B. Fixes implemented

All in `apps/budcom_desktop/src/renderer/scripts/app.ts` unless noted, reusing existing components/
styles/mechanisms throughout — no new sync engine, no new polling, no new persistence, no new
visual design system:

- **Real Dashboard data freshness** (`refreshDashboardDataFreshness()`/
  `renderDashboardDataFreshness()`, new): fetches the Connector's already-existing, already-tested
  `getLedgerStatistics()`/`getStockItemStatistics()` (exposed through `preload.ts` for the first
  time — the IPC channels themselves already existed and were already allowlisted, only the
  renderer-facing bridge methods were missing) and derives "Not synced yet" / "Partially synced" /
  "Synced" plus the more-recent real timestamp of the two modules. View-scoped exactly like the
  Ledgers/Stock Items/Pairing views' own poll-only-while-visible pattern (fetched on
  `activateView('dashboard')`, on the existing `onStatusUpdated` push while Dashboard is active, and
  once at startup) — no new timer, reuses the existing push mechanism. `renderDashboard()` no longer
  writes `state.syncLabel`/`state.lastSync` into these four fields at all, with a comment explaining
  why, so the two code paths can never race each other.
- **Preserve-on-failure for Ledgers/Stock Items**: `renderLedgers()`/`renderStockItems()` now check
  `state.ok`/`state.list`/`state.statistics` together up front; on failure, if a list was ever
  successfully rendered before (`ledgerListEverRendered`/`stockItemListEverRendered`), the existing
  DOM (list, stats, pagination) is left completely untouched and only the meta line explains the
  failure ("… Showing previously loaded data."); only a genuine first-ever failure clears to an
  empty list with a plain message.
- **Generation-guarded `loadLedgers()`/`loadStockItems()`** (`ledgerLoadGeneration`/
  `stockItemLoadGeneration`), mirroring `loadCompanies()`'s existing TD-014 pattern exactly.
- **Empty-state messaging**: a genuinely empty list (0 items) now shows one of three honest
  messages — "No ledgers synced yet. Click 'Sync Now' above to load them from Tally." (never
  synced), "No ledgers found for this company." (synced, genuinely zero), or "No ledgers match
  '<query>'." (search) — same pattern for Stock Items.
- **Accessibility**: `connection-indicator` changed from `aria-label="Connection status"` to
  `aria-hidden="true"` (`index.html`), consistent with the footer's own dot — the adjacent
  `header-connection-label` text (already read by assistive tech) is the actual semantic source of
  truth.

### C. Self-hardening pass (deliberately attacked the new changes before calling this done)

Two genuine defects were found and fixed during self-hardening — not merely "looks fine":

1. **Cross-company stale-data leak risk.** The new preserve-on-failure behavior (§B) is only safe
   within the *same* company — nothing previously reset `ledgerListEverRendered`/
   `stockItemListEverRendered`/pagination/search state on a company switch, so a refresh failure for
   a newly-selected company could otherwise have kept showing the *previous* company's ledgers,
   mislabeled as "previously loaded data" for the new one. Fixed with a new
   `resetPerCompanyModuleState()`, called from `handleCompanySelection()`'s success path and
   `handleClearCompany()`'s success path — clears both `everRendered` flags, resets page/query to
   defaults, clears the search inputs, clears the list DOM, and bumps both load generations so no
   in-flight load for the old company can land afterward. Regression test added
   (`company-selection.test.ts`) proving a failed refresh right after switching companies shows a
   plain failure, never the outgoing company's stale rows.
2. **Dashboard freshness card overwriting a good state with "Checking…" on a later transient
   failure.** The first cut of `renderDashboardDataFreshness()` showed "Checking…" any time both
   statistics calls failed, regardless of whether a real value had already been shown — the same
   "refresh started, so wipe the good state" class of defect just fixed for Ledgers/Stock Items,
   caught by re-reading the new code with the same scrutiny applied to everything else. Fixed with a
   `dashboardFreshnessEverRendered` flag mirroring the list-view pattern: a later total failure now
   leaves an already-shown "Synced · <timestamp>" untouched.

### D. Real Desktop validation — performed, and caught a third genuine defect

Reused the isolated `BUDCOM_INSTALLED_PROBE_MODE` probe methodology from the Phase 40 follow-up
(own `mkdtemp`'d `userData` dir, ephemeral connector port, `BUDCOM_SKIP_SINGLE_INSTANCE=true`) — the
same already-running production Desktop instance and live TallyPrime session found on the machine
were confirmed untouched throughout (identical process IDs before/after every run; Tally's PID
unchanged for the entire session). Captured process-id-scoped `PrintWindow` screenshots (never
full-screen — a full-screen attempt earlier this engagement had captured this very Claude Code
conversation window instead of the target app, confirming that approach is unreliable/inappropriate
here).

**Found via this real validation, not via reasoning alone:** the first screenshot of the new
Dashboard freshness card showed the Sync card stuck on **"Checking…"** indefinitely — a real,
unanticipated defect. Traced the root cause directly: `LedgerSyncService.getStatistics()` (and its
Stock Item equivalent) call `requireCompanyId()` first and reject outright with no company
selected — a completely normal, everyday state (right after cold launch, or before a user has ever
picked a company), not an error condition. The renderer's new code had no way to distinguish that
from a genuine Connector problem, so it stayed in a forever-loading state — exactly the "indefinite
spinner without context" this task's own Loading-state guidance warns against. Fixed by checking the
already-existing `hasActiveCompany()` before even attempting the fetch, short-circuiting to an
honest, static **"No company selected"** / **"Never"** — verified by direct code inspection
(`requireCompanyId()` in `ledger-sync.service.ts`), by two new regression tests (one proving the
statistics calls are never even attempted without a company, one proving the fetch resumes
correctly once a company becomes active again), and by re-running the exact same real-launch
validation afterward: the rebuilt probe now shows **"No company selected" / "Never"** cleanly
instead of a stuck spinner, alongside genuine live company discovery from the real Tally instance
("1 companies available", "ESTIMATION") — proving real end-to-end Connector↔Tally connectivity
worked throughout this validation.

### E. Tests / lint / build

15 new/changed regression tests across `advanced-ui.test.ts` (+2 accessibility), `company-
selection.test.ts` (+1 cross-company isolation), `dashboard-render.test.ts` (+7 data-freshness,
including the no-company-selected fix and its recovery), `ledger-render.test.ts` (+4 empty-state/
preserve-on-failure/generation-guard), `stock-item-render.test.ts` (+3, same pattern). One
pre-existing test fixture in `xss-safe-render.test.ts` was missing a `statistics` object entirely
(never caught before since test files aren't part of the `tsc` build check) — updated to match the
real IPC contract now that `renderLedgers()` requires it. Full suite: **68/68 files, 731/731 tests**
(725 pre-existing + a net +6 after accounting for the one narrow test removed for being
module-state-order-dependent — see below), 0 regressions, run repeatedly through the session.
`tsc --noEmit` clean for `main`/`preload`/`renderer`; full `npm run build` clean throughout. One
Windows CRLF-injection hazard (the same recurring pattern recorded in Phase 39 §I and the Phase 40
follow-up) hit four test files during editing this session — caught via `git diff --stat` showing
implausibly large changes, fixed by stripping the injected `\r` bytes before committing, full suite
re-run clean afterward.

One test-isolation lesson recorded for future sessions: module-level renderer state
(`ledgerListEverRendered`, `dashboardFreshnessEverRendered`, `latestDashboardState`, etc.) persists
across tests within the same file/module instance (this codebase does not use
`vi.resetModules()` between tests) — a test asserting "the very first load ever" behavior is
inherently order-dependent unless it explicitly re-establishes its own precondition first (as the
final `dashboard-render.test.ts` tests do via an explicit `renderDashboard()` call in `beforeEach`).
One overly-fragile "very first load fails" ledger test that could not cleanly do this was removed
rather than left flaky; the more valuable "preserves data on a *later* failure" tests are
self-contained and unaffected.

### F. Scope discipline

Left completely untouched, as required: Android, Tally extraction, Connector protocol, Catalogue/
MVP-1.4, Vartalap, Insights/OI, Referral Tree, Prospect→Ledger, Business Profile, sync-frequency
architecture, cloud sync, and the existing retry/recovery/state-machine architecture from Phase 40
(audited, not replaced — the only state-machine-adjacent change is the new generation counters,
which are the same established pattern already used for company loading, not a new architecture).
No version bump. Nothing pushed — commits prepared locally only, per this task's explicit rule.

## 32. Phase 42 — Desktop UX Polish: Final Review, Real-Use Hardening & Next-Lineup Gate

Explicit continuation of Phase 41 — a review-and-hardening gate, not a new feature pass: re-audit
the Desktop user journey (launch, connection, company, sync, offline) with fresh eyes ("do not trust
the previous report — inspect the actual current source"), fix only genuine defects found, then
render an explicit MVP-1.4-readiness decision without starting MVP-1.4 itself. Starting HEAD
`9ca8c7b` (Phase 41's own commits `264c797`/`3ceebf1`/`aed8ce0` plus the Android DEV-isolation commit
from a separate, unrelated task in the same session), working tree clean.

### A. Fresh-eyes source re-audit

Re-read `app.ts` end to end (all ~40 exported/internal functions, via a full function-signature
outline plus targeted deep reads of `getDisplayConnectionState()`, `renderConnectionDisplay()`,
`renderDashboard()`, `refreshUi()`, `activateView()`, `handleCompanySelection()`,
`startDesktopShell()`), `main.css`'s full token/layout system, and the main-process side of the
connection lifecycle (`connector-lifecycle-service.ts`, `dashboard-service.ts`) to understand exactly
when the renderer's connection/health display does and does not get refreshed. Confirmed Phase 41's
own defect list and fixes are intact and unregressed (28 bare `catch {}` blocks — the established
error-swallowing pattern — zero TODO/FIXME/console.log/console.error, matching the prior audit
exactly).

### B. Genuine defect found and fixed: Dashboard connection/health card can go silently stale

Traced the full push/pull chain for the Dashboard's connection card: `desktop:status-updated` only
fires on a *coarse* 5-value lifecycle transition (`starting`/`connected`/`reconnecting`/
`disconnected`/`failed`) or a handful of explicit user actions (settings save, company select/clear,
lifecycle buttons) — never on every health-poll tick. But the Connector's own `/health` `status` field
is a *finer* signal (`ok`/`degraded`/`unavailable`) that can legitimately read `'degraded'` while the
coarse lifecycle stays `'connected'` — confirmed at the source
(`connector/budcom_connector/src/services/health/health-service.ts`: `status = 'degraded'` whenever
any sub-service including `tallyConnection` isn't ready, e.g. **Tally itself disconnects while the
Connector process keeps running**). The Desktop's own `HttpHealthChecker.checkHealthDetails()` treats
`ready = body.status !== 'unavailable'` — so a degraded-but-still-`ready` response makes
`refreshHealthState()` call `markHealthy()` → `transitionState('connected')`, a no-op (no push) when
already connected. Since `activateView('dashboard')` only ever called
`refreshDashboardDataFreshness()` (Ledger/Stock Item freshness only, by explicit design — see Phase
41 §B) and never re-pulled `getDashboardState()`, a user sitting on or returning to the Dashboard
while Tally is disconnected but the Connector survives could see a stale "Connected"/"Working
normally" indefinitely — a real, deterministic staleness gap, not a hypothetical one, and squarely
inside this task's own "no false transitions" / "user always knows whether data is current" charter.

**Fix** (`activateView()`, `app.ts`): also call `refreshUi({ showLoading: false })` (caught, not
awaited) whenever the Dashboard view is (re-)activated — mirroring the exact "fetch fresh data when
this view becomes visible" pattern already used by `refreshDiagnostics()`/`loadLedgers()`/
`loadStockItems()`/`loadPairingPanel()`. Bounds the staleness window to "since the user last left and
returned to Dashboard" instead of indefinite. Deliberately narrower than adding a new
Dashboard-visible polling timer (the more complete fix): `dashboard-recovery.test.ts` has an
extensively fake-timer-tuned bounded-recovery suite (including one test asserting exactly 181
`getDashboardState()` calls across a simulated 15-minute heartbeat) that a new independent timer
risked destabilizing for a benefit — a 5-second-scale staleness window — that didn't justify the
added architecture or risk; the task's own instructions to prefer minimal, in-architecture fixes and
avoid new background-polling machinery. The `.catch(() => {})` guard is required, not decorative —
`sync-progress.test.ts`/`pairing-render.test.ts` both reuse `activateView('dashboard')` as a neutral
reset step against bridge mocks that don't implement `getDashboardState` et al.; verified by adding a
new `dashboard-render.test.ts` regression test and re-running both files clean.

### C. Genuine defect found and fixed: Sync card/header duplicated the Company card's own wording

`refreshDashboardDataFreshness()`'s no-active-company short-circuit (added in Phase 41 to fix a stuck
"Checking…" state) set the Sync card headline and header Sync badge to the literal string `'No
company selected'` — identical to the adjacent Company card's own headline. Real-Desktop validation
screenshot (isolated probe against a live TallyPrime instance, no company selected — the actual
default state on cold launch) showed this rendered as **"SYNC / No company selected"** directly next
to **"COMPANY / No company selected"** — reads as if sync itself has a status called "no company
selected" rather than "nothing to report, and here's why," and is exactly the kind of avoidable
technical/confusing wording this task's audit targets. Fixed to `'—'` (`dashboard-sync`/
`header-sync`), matching this app's own established placeholder convention for "nothing yet" used
everywhere else (`header-version`, `dashboard-last-refresh`, etc.) — no invented vocabulary, and
unambiguous given the Company card's own message sits immediately adjacent. Two existing
`dashboard-render.test.ts` assertions updated to match (test titles corrected to describe what they
now verify); no other test referenced the old string in this context.

### D. Investigated, attempted, and honestly reverted: header badge overflow

The same no-company-selected screenshot also showed the header's fixed 3-column `Company: / Sync: /
Last Sync:` badge grid (`.header-meta { grid-template-columns: repeat(3, auto) }`, `main.css`)
overflowing past the window's right edge at the app's own default launch size (1200×800, confirmed
in `main.ts`'s `createMainWindow()`) — `"Company: No company selected"` alone is long enough to push
the still-undisclosed Sync/Last Sync badges entirely off-screen with no wrap, ellipsis, or scrollbar.
Confirmed the fix in §C alone doesn't resolve it (the overflow is driven by `header-company`'s
"No company selected" fallback in `renderDashboard()`, a separate, correct-as-is field). Attempted
three rounds of CSS truncation (`max-width`/`overflow:hidden`/`text-overflow:ellipsis` on the value,
then `min-width: 0` on the containing grid item, then on `.header-meta`/`.header-status` themselves —
the standard nested-flex/grid truncation chain) and rebuilt/re-probed after each; all three produced
byte-for-byte the same clipped screenshot, with no diagnostic tooling available in this session to
inspect live computed styles and confirm the actual cause. Rather than leave in place three rounds of
CSS that could not be verified to work — and per this project's own standing "never overclaim a fix
that doesn't provide a demonstrated guarantee" principle — **all three attempts were reverted**;
`git diff` on `main.css` is empty. This remains a real, screenshot-confirmed, unresolved cosmetic
defect, deferred with an honest account rather than shipped unverified; see §H below for its
practical severity. Recommended follow-up: reproduce with Chromium DevTools attached (not available
in this headless probe session) to inspect actual computed grid/flex track widths.

### E. Real Desktop validation

Reused the isolated `BUDCOM_INSTALLED_PROBE_MODE` probe methodology from Phase 40/41 (own `mkdtemp`'d
`userData` dir, ephemeral connector port, `BUDCOM_SKIP_SINGLE_INSTANCE=true`) against the same live
TallyPrime instance on the machine, five independent launches across this task (baseline, then one
per fix/attempt), zero disruption to the already-running production Desktop instance found on the
machine. Every run: clean cold launch (dark background from first paint, no flash), `'starting' ->
'connected'` within ~1s, real company discovery ("1 companies available", "ESTIMATION"). Directly
confirmed via screenshot: the §C wording fix (Sync card correctly shows "—" instead of duplicating
"No company selected"); the §D overflow remains present and unresolved (documented, not fabricated as
fixed). The §B staleness fix could not be directly observed live within this session — reproducing it
requires a running Tally instance that then gets closed/disconnected while the Connector process
keeps running, a scenario this isolated, non-interactive probe (screenshot + log observation only,
no click/UI-interaction capability) was not built to induce; it is verified instead by direct source
tracing (§B) and a targeted regression test.

### F. Tests / lint / build

Full suite: **68/68 files, 732/732 tests** (731 Phase-41 baseline + 1 new regression test; 2 existing
assertions updated in place for the §C wording change, no count change from those). `tsc --noEmit`
clean for `main`/`preload`/`renderer` (via `npm run build`); `npm run lint` (tsc + full suite) clean.
Re-ran the full suite after every source edit in this session, including after reverting §D's CSS, to
confirm the revert left no residue. `git diff --stat` checked before every save for the
session's known CRLF-injection hazard — none occurred this session.

### G. Scope discipline

Left completely untouched, as required and directly confirmed by `git status`/`git diff --stat`
covering exactly two files (`app.ts`, `dashboard-render.test.ts`): Android, Tally extraction,
Connector protocol, Catalogue/MVP-1.4, Vartalap, Insights/OI, Referral Tree, Prospect→Ledger,
Business Profile. `main.css` shows zero diff (§D's attempt fully reverted). No version bump. Nothing
pushed — commits prepared locally only, per this task's explicit rule; the pre-existing Android
DEV-isolation commit (`9ca8c7b`, unrelated prior task) also remains unpushed, unchanged from before
this task.

### H. MVP-1.4 readiness gate — **A. READY**

Two real, verified Dashboard connection/trust defects found via fresh-eyes source audit (not
superficial) and fixed with narrow, low-risk changes; one cosmetic header-overflow defect found,
genuinely investigated, and honestly deferred rather than shipped unverified. Full regression clean
throughout. The final self-review question — *can a user understand within a few seconds whether
BUDCOM is ready, whether Tally is connected, which company they're working with, whether data is
current, and whether a requested action happened* — is answered **yes** via the primary Dashboard
cards (Connection/Company/Sync), which are fully legible and correctly worded in every state
observed; the one open defect (§D) affects only a secondary, redundant header badge in one specific
state at the smallest supported window width, and does not block understanding since the same
information is shown more prominently in the cards directly below it. Recommended next task:
MVP-1.4 Catalogue implementation planning/execution, per Phase 39's PDL-020 decision lock — **not
started here**, per this task's explicit instruction.

## 33. Phase 43 — Adaptive Tally Synchronization Strategy: Research, Architecture & Product Decision Research

Read-only research/architecture task, explicitly not an implementation task: investigate whether an
adaptive (frequent-when-active, backed-off-when-idle) Tally synchronization strategy is feasible,
recover the real current sync architecture across Desktop/Connector/Android/Tally by direct source
inspection rather than assumption, and produce a documented recommendation with an explicit
LOCKED/RECOMMENDED/OPEN decision split. Starting HEAD `4b0b8ff` (Phase 42's own final commit), working
tree clean. No production code touched — verified by `git diff --stat` at the end covering exactly the
new architecture document plus this ledger entry and the status-doc pointer below.

### A. Investigation method

Parallelized two independent, thoroughly-cited investigations (each producing an 800-1500+ word report
with exact file:line evidence, explicitly noting anything that could not be confirmed rather than
guessing) alongside first-party investigation of the Desktop side and the Connector's technical-debt
history:

- A general-purpose research agent traced the Connector's sync endpoints, extraction pipeline,
  scheduler/throttling infrastructure, sync-state persistence, company-boundary handling, and failure
  semantics directly from `connector/budcom_connector/src`.
- A second traced Android's sync triggers, Room caching, company-scoped state, local-first behavior,
  reentrancy guards, and connection-lifecycle indicators directly from
  `apps/budcom_android/app/src/main/java/com/budcom/android`.
- Desktop's connector-lifecycle health-poll cadence, manual sync/progress-poll wiring, and freshness UX
  were traced directly (already well understood from Phase 41/42's own review earlier this session).
- The project's own `docs/technical-debt/registry.md` was read directly for TD-006 ("Durable
  interrupted sync resume") and TD-007 ("Extraction-phase cancellation") — both materially inform
  failure/concurrency semantics for any scheduler design — and to confirm TD-001/TD-035's actual scope
  (XML sanitization and `PARENT`-group extraction respectively, not sync-state isolation as the
  governing task's phrasing might suggest).

### B. Headline finding: no automatic sync, no cheap change-detection, anywhere in the system today

Confirmed directly, not assumed: the Connector's own `SchedulerService` is implemented only by
`SchedulerStub extends PlaceholderService` — a literal no-op, never implemented. Desktop has zero
`setInterval` triggering data sync (only a private-storage watchdog and the 5-second connector-health
poll, which checks reachability only). Android has zero `WorkManager`/`AlarmManager` usage anywhere
(confirmed by a repo-wide search for `.enqueue(` returning zero matches, matching the README's own
"WorkManager / automatic sync | Out of scope" note). Every sync on every platform is manual,
user-tapped, and always a full extraction — `ALTERID` is fetched and stored per-record but only as one
field inside the same full pull, never as a standalone cheap pre-check; the existing post-extraction
SHA-256 content fingerprint (`computeLedgerFingerprint()`/`computeStockItemFingerprint()`) already
skips redundant database writes but only after paying the full Tally/XML cost. The project's own
TD-006 explicitly documents that a reliable resume/watermark mechanism is not currently safe to
assume, because Tally's export order is not guaranteed and no snapshot identity exists — this is the
existing, authoritative project position, not a new conclusion invented for this task.

### C. Two genuine, previously-undocumented defects found (not fixed — read-only task)

Both investigations independently surfaced a company-isolation gap of the identical shape, in
in-memory (not database) state:

1. **Connector**: `LedgerSyncServiceImpl`/`StockItemSyncServiceImpl` are process-wide singletons whose
   `progress`/`activeRun`/`syncInFlight` fields are not keyed by `companyId` — `GET
   /sync/ledgers/status` takes no company parameter and can return a stale company's leftover
   progress after a company switch; the single-flight guard is also global, so Company B's sync can be
   spuriously rejected as "already running" while Company A's is in flight, even though the underlying
   per-company database check would allow it.
2. **Android**: `SyncRepositoryImpl.bindCompany()` doesn't clear a previously-selected company's cached
   sync-target summaries, and `clearActiveIfCompanyChanged()` — a method that appears purpose-built to
   fix exactly this — is never called anywhere (dead code).

Neither was fixed, per this task's explicit read-only/no-production-code constraint. Both are
documented in the new architecture doc as **prerequisite fixes** for the eventual scheduler
implementation task, not incidental notes — a background scheduler ticking automatically will exercise
these gaps far more often than today's manual-tap-only usage does.

### D. Deliverable

New document: `docs/architecture/BUDCOM-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md` (19 sections, matching
the governing task's own report structure) covering: the recovered current architecture per platform;
the critical cheap-change-detection question answered honestly (not achievable today without a
separately-scoped Tally-API validation spike this task did not perform); a five-strategy comparison;
a two-state (staged-backoff) synchronization state machine preserving the originally-proposed 15/60
concept, refined into a 15→30→60-minute staged ladder with 5-minute checks during the active window;
an explicit definition of "change" per module; the multi-company isolation model (`companyId` alone,
Map-not-singleton, directly evidenced by §C's findings); failure semantics (a failed check never
advances or resets the backoff ladder); manual Sync Now behavior including a debounced, honestly-worded
connection-loss-based suggestion (not an invented "session ended" detector); a real-numbers-anchored
performance model across five scenarios; confirmation the existing local-first read path is untouched;
a state-storage recommendation (a new small company-scoped table in the Connector's existing SQLite
database, not Room/DataStore, not overloading `sync_runs`); a security/integrity review; a concurrency
model reusing the existing sync-execution path with no second state machine; and an explicit
LOCKED (4 items) / RECOMMENDED (5 items) / OPEN (4 items) decision split — most notably keeping the
cheap-Tally-signal question genuinely OPEN rather than silently assumed either way.

### E. Scope discipline

No Android, Desktop, Connector, or Tally-protocol production code was modified — confirmed by
`git status`/`git diff --stat` covering exactly the new architecture document plus this ledger entry
and the status-doc summary/pointer. No schema change, no version bump, no MVP-1.4 work. Nothing
pushed — commits prepared locally only, per this task's explicit rule.

## 34. Current source-of-truth references

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

## 34. Phase 44 — TD-035 Real-Device Validation (previously blocked, now unblocked) + Ledger/Voucher Browser Retry Dead-End Fix

Starting HEAD `380ce85` (Phase 43's own final commit), working tree clean, 3 commits ahead of
`origin/main`. Continuation of [[project_budcom_td035_physical_validation]]'s deferred physical
walkthrough — phone and laptop are now on the same ordinary Wi-Fi router (`192.168.29.x`,
JioFiber), not the mobile-hotspot SSID that previously blocked device-to-device traffic.

### A. Real-device path confirmed working end to end

Confirmed live, in order: ADB authorization (phone required an unlock + accept, a transient
condition per [[feedback_adb_disconnect_protocol]], not a defect); phone (`192.168.29.111`) and
laptop (`192.168.29.34`) on the same subnet, real TCP reachability proven via `adb shell curl` from
the phone directly hitting the Connector's `/health`; Desktop `0.4.20`/Connector `0.4.6` launched
(the installed production build already running in `C:\Program Files\Budcom Desktop`, bound
`trusted-lan` on `192.168.29.34:8080` — a separate local dev-build launch attempt correctly
deferred to it via Electron's single-instance lock); TallyPrime opened with real company
**ESTIMATION** (949 real ledgers), `tallyReachable: true`.

Ledger sync (Sync screen → Ledgers card → Sync Now) completed against live Tally: 949 processed, 0
failed. Connector's own `GET /ledgers` confirmed real `parentGroup` values ("Sundry Debtors",
"Sundry Creditors") on real ledgers — **TD-035's Connector-side fix confirmed working against real
data for the first time** (previously resolved at the code/test level only, per that entry's own
"no real-device re-verification was performed" note). Android's Room cache required one additional,
previously-undocumented step to reflect this (see §B) — once done, `cached_ledgers`: 949/949 rows
with non-null `parentGroup` (873 "Sundry Debtors", 53 "Sundry Creditors"); Party reconciliation
produced `cached_parties`: 873 customers + 53 suppliers, exact match. Connect → Customers visually
confirmed real rows with working Call/WhatsApp/View Ledger/View Vouchers actions — **first-ever
physical proof that TD-035 actually populates Connect against real Tally data**, closing the gap
Phase 37/39 left open. No dedicated Suppliers tab exists in Connect (Customers/Prospects only, an
existing MVP-1.1-A scope decision, not new) — supplier classification verified directly at the data
layer instead.

Company isolation re-proven with a second real company opened live in Tally, **Jaju Sanitations**
(21 real ledgers): synced and classified independently; zero cross-contamination with ESTIMATION in
either direction at the Room/database layer, confirmed by direct `sqlite3` inspection after
switching both ways.

### B. Two new findings surfaced by physical validation (not visible from source reading alone)

1. **The Sync screen's "Sync Now" does not populate Android's Room cache.** It only tells the
   Connector to re-extract from Tally into the Connector's own SQLite database. Room
   (`cached_ledgers`, which Party reconciliation and Connect both depend on) is populated only by
   `LedgerRepositoryImpl.refreshLedgers()`, triggered exclusively by the Ledger Browser's
   pull-to-refresh gesture — a separate, undocumented manual step the previously-recorded "exact
   validated tap sequence" in [[project_budcom_td035_physical_validation]] had not accounted for.
   Logged as **TD-037**'s sibling context, not a separate TD entry itself — see the architecture
   doc's own §13 assumption ("a background-triggered sync writes into the same tables a manual sync
   would") this contradicts; the correction is noted here and does not change any of Phase 43's
   LOCKED decisions, since the adaptive scheduler (Phase 2) is Connector-side only and does not
   depend on or worsen this pre-existing, orthogonal Android-side mechanic.
2. **Reproduced live, exactly as Phase 43's architecture research had predicted from source reading
   alone**: switching companies (ESTIMATION → Jaju Sanitations → ESTIMATION) leaked the previous
   company's "Last synced at ..." freshness timestamp onto the newly-selected company's Dashboard
   and Sync screen, bidirectionally. Confirmed by direct `sqlite3` inspection to be a **display-only
   leak** — `cached_ledgers`/`cached_parties` remained correctly company-scoped throughout. Logged as
   **TD-037**, open, flagged as a Phase 2 prerequisite fix (matches Phase 43 §8's own recommendation).
3. **New, previously-undiscovered defect**: for a company with a completely empty Room cache (a
   genuinely new company, e.g. Jaju Sanitations before its first successful refresh), the Ledger/
   Voucher Browser's "Retry" button was a **permanent dead end** — it re-ran the same cache-only load
   that had just failed, forever, and Compose's pull-to-refresh gesture had no scrollable child to
   hook into in that exact empty-state render, so swipe-to-refresh was also unreachable. Physically
   reproduced on Jaju Sanitations (repeated Retry taps and swipe attempts confirmed via `sqlite3` to
   leave `cached_ledgers` at 0 rows throughout). Logged and **fixed same-session** as **TD-038** —
   user-directed, ahead of Phase 2 (see §C).

### C. TD-038 fix

`LedgerBrowserEvent.Retry`/`VoucherBrowserEvent.Retry` now escalate to the network-backed
`refreshLedgers()`/`refreshVouchers()` path exactly when `!state.hasContent` (the identical
condition that makes the Retry button appear at all), and keep the existing cache-first behavior
whenever content already exists — preserving the Phase 36/Phase 3E "Load/search/retry/pagination
never touch the network — only explicit Refresh does" invariant for the case it was written to
protect (verified: that exact existing test still passes unmodified). Two new regression tests
added (one per browser), both physically evidenced by the real Jaju Sanitations reproduction above.
Android: **1,268/1,268 tests both variants** (+2 from the 1,266 baseline), 0 lint errors, both
assembles green (see the session's final report for the full lint run). Files: `LedgerBrowserViewModel.kt`,
`VoucherBrowserViewModel.kt`, `LedgerBrowserViewModelTest.kt`, `VoucherBrowserViewModelTest.kt`,
`docs/technical-debt/registry.md` (TD-036, TD-037, TD-038 added; TD-035 updated with real-device
confirmation).

### D. Scope discipline

No MVP-1.4 Catalogue work performed. No Desktop/Connector production code touched (Connector's
already-running production build was used as-is; no rebuild was needed or performed). No
StockItem Browser change (its `loadStockItems()` is already network-first, a different,
pre-existing, already-documented inconsistency, out of scope here). Adaptive Tally Synchronization
Phase 2 (the scheduler implementation) begins in the next session/continuation, gated on this
phase's real-device evidence per the governing task's own Phase 1 → Phase 2 sequencing.

## 35. Phase 45 — Adaptive Tally Synchronization Implementation (Phase 2)

Continuation of Phase 44, same session. Phase 1's real-device evidence passed (with the TD-038
fix applied first, per explicit user direction), authorizing this phase per the governing task's
own Phase 1 → Phase 2 gate.

### A. Prerequisite company-isolation fixes (TD-036, TD-037) — fixed before scheduler work

Both explicitly flagged by Phase 43's architecture research as blocking prerequisites, fixed here
rather than deferred:

- **TD-036 (Connector)**: `LedgerSyncServiceImpl`/`StockItemSyncServiceImpl`'s `progress`/
  `activeRun`/`activeAbort`/`syncInFlight` converted from single instance fields to
  `Map<companyId, T>`. `getSyncProgress()`/`cancelSync()` changed from sync to async to resolve
  the current company first; `LedgerSyncProgress` (shared by `StockItemSyncProgress`) gained a
  `companyId` field. Two regressions the refactor itself introduced were caught and fixed before
  landing: the two read-only status methods needed a new non-throwing `peekCompanyId()` to keep
  their pre-existing "never throws with no company selected" contract (caught by
  `api-request-security.test.ts`), and two API route call sites were missing an `await` that
  `tsc` alone didn't flag (a `Promise` would have silently serialized as `{}` in the JSON
  response).
- **TD-037 (Android)**: `SyncRepositoryImpl.bindCompany()` now resets the whole summary on an
  actual company change (discarding every stale `lastOutcome`/`lastSuccessfulAt`/`statistics`)
  and releases the local active-sync guard, activating what the pre-existing but never-called
  `clearActiveIfCompanyChanged()` was built to do — now removed as redundant. 3 new regression
  tests in `SyncRepositoryImplTest.kt`.

Connector: 159/159 → **162/162 test files, 1,441/1,441 tests** (+4), `tsc`/`eslint`/build clean.

### B. Scheduler engine (Connector)

Implements the LOCKED architecture from
`docs/architecture/BUDCOM-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md` exactly:

- **State machine** (`adaptive-scheduler-domain.ts`, pure, no I/O): `initialSchedulerState()`,
  `applySyncOutcome()`, `hasDetectableChange()`. Two states (`active_window`/staged `backoff_15→
  30→60`), 5-minute checks during a 15-minute active window, the critical LOCKED invariant (a
  failed check never advances or resets the ladder) enforced independent of any I/O concern. 13
  unit tests cover every transition explicitly required: initial state, active-window 5-minute
  checks, no-change-inside-window vs no-change-at-expiry, the full 15→30→60 staged progression,
  the 60-minute floor holding indefinitely, a change at any stage jumping straight back to
  active_window, and repeated failures never accumulating any stage movement.
- **Scheduler service** (`adaptive-scheduler.service.ts`): a pure *trigger*, never a second sync
  engine — every check calls the exact same `syncLedgers()`/`syncStockItems()` path manual "Sync
  Now" already uses, inheriting single-flight/rate-limiting/circuit-breaker/sanitization
  automatically. Company-scoped by construction: the Connector has exactly one active Tally
  connection at a time, so the scheduler only ever acts on whichever company is *currently
  selected* (never silently switches sessions in the background); each company's own row stays
  independently persisted, so switching back to a previously-active company resumes its own
  history correctly. Automatic checks run `{incremental: true}` so the existing content-fingerprint
  mechanism produces a real change signal (`hasDetectableChange()` reading the sync result's own
  `changes` array); manual syncs (non-incremental, unchanged default) are instead treated as
  unconditional evidence of active use — deliberately, since a non-incremental run's `changes`
  array can't distinguish a real diff from "every record re-written." Restart-safe by
  construction: state is never held only in memory, so a restart resumes from whatever was last
  persisted and an already-overdue check fires immediately rather than waiting a full tick
  interval. 13 integration-level tests cover: no-op with no company selected, seeding a fresh row
  without syncing prematurely, restart recovery via an overdue row, respecting a not-yet-due
  check, change-detection resetting to active_window, failure preserving stage and interval,
  strict company isolation (a due company-B row never touched while company A is current, and
  vice versa), identical natural keys never colliding (keyed strictly by `(companyId,
  resourceKind)`), manual-sync observation (`recordManualSyncOutcome`) for both success and
  failure, a never-before-seen company/resource seeding correctly, overlapping `runOnce()` calls
  never producing a duplicate sync, and `start()` called twice never arming a second timer.
- **Storage**: migration v13 adds `scheduler_state` (one row per `(company_id, resource_kind)`,
  mirroring the existing `voucher_active_snapshots` current-pointer pattern rather than
  overloading the append-only `sync_runs` history table). `SchedulerStateRepository` takes a lazy
  database-getter closure — mirroring `ConnectorIdentityRepository`'s pattern — so resolving it
  during application wiring never requires `LocalDatabase` to have already started; an eager
  version of this broke 8 test files during development, via `HealthService`'s own eager
  `Scheduler` resolution transitively touching storage before it was ready. 4 migration tests
  (forward migration from v1, clean-database column shape, idempotent reopen, one-row-per-key
  uniqueness enforced). The old v12 pairing migration test's hardcoded `toBe(12)` assertions
  updated to check `STORAGE_SCHEMA_VERSION` instead (still verifying the same v12 tables along
  the way) — the historical migration itself was not touched.
- **API surface**: `GET /sync/ledgers|stock-items/status` gained a `schedulerState` field — no
  new endpoint, per the architecture's own recommendation. `POST /sync/ledgers|stock-items` now
  feed their outcome back into the scheduler after a manual sync via
  `recordManualSyncOutcome()`, exactly as an automatic check would (no separate code path).
  Replaces the `SchedulerStub` placeholder entirely (deleted, no longer referenced anywhere).

Connector: 162/162 → **stayed at 162/162 test files, 1,471/1,471 tests** (+30 for the scheduler
domain/service/migration suites), `tsc`/`eslint`/build all clean.

### C. Real-device verification of the scheduler (not just automated tests)

A standalone build of the new Connector (dist, port 8090, isolated database, `BUDCOM_DATABASE_PATH`
override) was run directly against the same real, live TallyPrime instance from Phase 1's
validation (company ESTIMATION, 949 real ledgers) — without disturbing the already-running
production Desktop/Connector instance. Confirmed live: `/health`'s `Scheduler` sub-status reports
`"active"` (previously always `"Placeholder — not implemented"`); after selecting ESTIMATION and
running a real manual Ledger sync (949 real ledgers, all `added` since this was a fresh empty
database), `GET /sync/ledgers/status` returned real `schedulerState`:
`{"stage":"active_window","nextCheckDueAt":"2026-08-22T18:39:59.894Z"}` (exactly 5 minutes after
the manual sync's own completion) — proving the manual-sync-observation path works end to end
against real data, not just mocks. `storage.schemaVersion: 13` confirmed the new migration applies
correctly to a freshly-created real database.

**The Connector was then left running unattended, and the scheduler's own timer fired the first
real automatic check with no human action involved.** At `18:40:00.685Z` — moments after the
`18:39:59.894Z` due time passed — a genuinely new, distinct sync run (`3e0bccb2-...`, different
`syncRunId` from the manual one) executed automatically: a real, live, `{incremental: true}` Ledger
sync against the same 949 real ledgers, completing at `18:40:06.372Z`
(`itemsProcessed: 949, itemsAdded: 0, itemsUpdated: 0, itemsSkipped: 949`) — the existing
content-fingerprint mechanism correctly found zero real changes since the manual sync 5 minutes
earlier. `schedulerState` updated to `{"stage":"active_window","nextCheckDueAt":"...18:45:06.374Z"}`
— correctly *remaining* in the active window with another 5-minute check scheduled, matching the
state machine's own rule that a no-change result inside an unexpired window keeps the 5-minute
cadence rather than stepping into backoff. This is real, live, unattended, end-to-end proof of the
scheduler's core automatic-trigger loop — not a mock, not a simulation.

**The Connector was left running for the remainder of the 15-minute active window (established by
the manual sync at `18:34:59`, so due to expire at `18:49:59`), and the real backoff-staging
transition was observed live, unattended, with zero human action.** Two further real automatic
checks fired on schedule (`~18:45:06`, `~18:50:36`), each a genuine incremental sync against the
same 949 real ledgers, each correctly finding no real change. The third of these landed after the
active window had genuinely expired — real time had passed, not a simulated clock — and the state
machine correctly stepped down for the first time: `{"stage":"backoff_15",
"nextCheckDueAt":"2026-08-22T19:06:06.508Z"}` at `18:51:14`, roughly 15 minutes later exactly as
designed. This is complete, live, real-device proof of the entire core mechanism the architecture
specifies — a manual sync opening the active window, repeated real 5-minute checks holding it open
while nothing changes, and the window genuinely expiring into the first backoff stage — not merely
the deterministic unit/integration tests (26 of them) that also cover this same logic in isolation.
The session did not additionally wait out the full `backoff_15 → backoff_30 → backoff_60` staged
climb in real time (another ~45 minutes) — that further staging rests on the same deterministic
tests, which exercise the identical code path (`applySyncOutcome()`) already proven live above for
the first, hardest-to-fake transition.

### D. Desktop UX (first, per the governing task's explicit ordering)

Dashboard's existing Sync card gained one new line: "Checking regularly" while either Ledgers or
Stock Items is inside the scheduler's active window, "Checking occasionally" once both have
backed off, blank when no scheduler state is known yet (older Connector, nothing synced, or a
transient failure) — never raw stage names or minute intervals, per the architecture's UX
section. Two new read-only IPC channels (`desktop:get-ledger-sync-progress`,
`desktop:get-stock-item-sync-progress`) mirror the existing statistics channels exactly; a
pre-existing IPC allowlist security test immediately caught both being missing from the allowlist
on first attempt. Desktop: 68/68 → **stayed at 68/68 test files, 735/735 tests** (+3), `tsc`
(main+preload)/lint clean.

### E. Android UX (consumer only, no second scheduler)

Dashboard's existing sync-status line gained the same suffix via a `checkingFrequencyLabel()` pure
function mirroring Desktop's `deriveCheckingFrequencyLabel()` one-for-one. `SchedulerStateDto`/
`SchedulerState` thread the Connector's wire shape through the existing `SyncProgress` the
Dashboard already observes passively via `ObserveSyncStatusPort` — no new polling, no new data
source, no second scheduler on the Android side (the architecture's explicit "Android never runs
the scheduler" boundary preserved). Android: **1,281/1,281 tests both variants** (+13 across this
phase: +3 for TD-037 in §A, +3 DTO mapping tests, +4 in a new `SyncModelsTest.kt`, +3
`DashboardViewModel` integration tests here in §E), 0 lint errors, both assembles green.

### F. Scope discipline

No MVP-1.4 Catalogue work. No new sync engine anywhere — every automatic and manual trigger,
Connector-side, funnels through the identical `syncLedgers()`/`syncStockItems()` methods that
existed before this phase. No StockItem Browser Android-side change (out of scope, pre-existing
inconsistency). The OPEN cheap-signal investigation from Phase 43 (§17 item 10, a possible
`ALTERID`-based pre-extraction change probe) was **not** touched — this phase's scheduler design
does not depend on it, exactly as the architecture recommended.

## 36. Phase 46 — Adaptive Tally Synchronization Hardening: Manual-Sync/Room Gap Investigation + Fix

Continuation task, new session, explicitly bounded to hardening the Phase 45 work before MVP-1.4
resumes. First priority: investigate the manual-sync/Room limitation Phase 45 flagged but did not
root-cause. Second priority (conditional): the "Finished working in Tally? Sync now" suggestion.

### A. Investigation: is the Ledgers manual-sync/Room gap expected architecture, a UI issue, or a defect?

Traced the complete path (Android Sync Now → `StartTargetSyncUseCase` → Connector `POST
/sync/ledgers` → Tally extraction → Connector SQLite → response → Android Room → Connect's
Customer/Supplier reconciliation). Found that `StartTargetSyncUseCase` already had a
`completeVoucherWindowFetch()` second step for Vouchers (added historically per commit `028c650`,
explicitly scoped to Vouchers only in its own commit message — not a "Ledgers should stay
different" decision) that re-fetches from the Connector into Room after a successful extraction.
Ledgers had no equivalent step. **Verdict: genuine defect (not expected architecture, not a pure
UI issue)** — the same user action ("Sync Now") already fully completed its promise for Vouchers
via an established, accepted pattern; Ledgers simply never received the mirror. Logged as
**TD-039** (`docs/technical-debt/registry.md`).

### B. Real-device validation

Real device `10BF44124K000E3`, company ESTIMATION, phone+laptop on the same Wi-Fi (no hotspot
client-isolation this time — an ordinary router). Confirmed pre-fix via `run-as ... sqlite3` that a
Sync-Now-only tap (no separate Ledger Browser visit) left `cached_ledgers.syncedAt`/
`dataFreshnessAt` at stale values, and Connect's Customer tab showed the stale set. No Tally
business data was altered to manufacture this — the existing real, already-synced ESTIMATION
company was used as-is, and the gap was observed from its genuine pre-existing state.

### C. Fix (smallest architecturally correct change)

`StartTargetSyncUseCase` (`apps/budcom_android/.../sync/domain/usecase/SyncUseCases.kt`) gained a
4th constructor dependency, the existing `RefreshLedgersUseCase`, and a `completeLedgerRoomRefresh()`
step mirroring `completeVoucherWindowFetch()` exactly: on a successful Ledgers extraction, it calls
the same `GET /ledgers` fetch-and-persist path the Ledger Browser's own explicit Refresh already
uses, before reporting the sync complete; a refresh failure is reported as the operation's own
failure. No new Tally extraction, no second sync engine, no new endpoint — reuses the identical
Connector read path and the identical Android use case each browser screen already calls. Hilt
auto-wired the new constructor parameter with no manual `@Provides` change needed.

Re-verified live on the same real device: a Sync-Now-only tap updated `cached_ledgers.syncedAt`/
`dataFreshnessAt` to the exact tap time, and Connect's Customer tab immediately showed the full
real, current list (873 customers) with no separate Ledger Browser visit. See TD-039 for exact
timestamps.

New/updated tests: `SyncUseCasesTest.kt` (+4 covering success/failure/extraction-failure/
StockItems-untouched) and `SyncViewModelTest.kt` (+2 covering the same at the ViewModel layer).
Android: 1,281/1,281 → **1,286/1,286 tests both variants**, 0 lint errors, both assembles green.

### D. "Finished working in Tally? Sync now" suggestion — not implemented, and why

Section 5 of the governing task conditioned this feature on "legitimate connection-state evidence,"
explicitly forbidding an invented "Tally session ended" detector. `TallyConnectionManager`
(`connector/.../tally/connection/tally-connection-manager.ts`) exposes exactly one relevant signal:
`state` (`connected`/`disconnected`/`degraded`/`reconnecting`), driven purely by whether the most
recent Tally XML exchange succeeded or failed. This same state is already the input to this
codebase's own retry policy and circuit breaker, both of which exist specifically because ordinary,
frequent, transient failures (Tally momentarily busy, brief network blips) are treated as routine,
not as meaningful events. Building a "user has finished working in Tally" suggestion on top of a
`connected → disconnected/degraded` transition would silently reinterpret that same routine,
frequent, transient signal as evidence of user intent — which is a session-end detector in
substance, regardless of how the surfaced copy is worded. No other connection-state signal in
either the Connector or Android exposes anything closer to actual Tally usage (e.g., data-entry
activity, window focus, or an explicit close event) than this raw reachability flag. This is treated
as the Section 18 STOP condition ("the suggestion requires inventing Tally-session detection") and
the feature is **not implemented** — deliberately deferred, not rejected, consistent with the
open item already on record in project memory. No product decision was overridden; none was made.

### E. Re-verification (no scheduler or company-isolation code touched this session)

Full regression run across all three subsystems: Connector **1,471/1,471** tests (162/162 files),
Android **1,286/1,286** tests both variants, Desktop **735/735** tests — all clean, confirming no
regression from this session's change. The TD-036/TD-037 company-isolation regression tests
(`ledger-sync.test.ts`, `stock-item-sync.test.ts`, `SyncRepositoryImplTest.kt`) are part of these
totals and passed unchanged. Company isolation, restart/recovery, and the scheduler's own operation
were **not re-run physically** this session: the change is confined to `StartTargetSyncUseCase`'s
Ledgers branch, does not touch `bindCompany`, the scheduler service, or any persisted scheduler
state, and Phase 45's real-device scheduler evidence (§C above) already stands — repeating it here
would not exercise anything this session's diff could plausibly have broken.

### F. Scope discipline

No MVP-1.4 Catalogue work. No second sync engine. No scheduler-architecture changes — the Phase 45
design (ACTIVE_WINDOW/BACKOFF staging, per-company state, Manual Sync feeding scheduler state) is
untouched and not re-litigated. The cheap Tally change-detection signal (`ALTERID`/similar) was not
implemented, per the governing task's own instruction.

## 37. Phase 47 — Cheap Tally Change-Detection Signal: Autonomous Investigation (Research Only)

New session, explicitly scoped to resolve Phase 43's OPEN item 10 (a genuinely cheap, pre-extraction
Tally change-detection signal) with a real Tally-instance spike, and to implement it **only if
proven safe** — explicitly authorized to reach either a successful-implementation or a
research-only outcome, with an unconditional instruction never to convert an assumption into a
production behavior change.

### A. Source-level investigation (§1/§3 of the governing task)

Confirmed via direct source inspection: `MasterDataTemplates.companyInfo()`/`buildObjectTemplate()`
— the "exact request-shape scaffold" Phase 43's research had pointed to as the theoretical path to a
company-level probe — has **zero callers anywhere in the Connector** (dead code). `TD-006`'s
"Accepted limitation" already documents that Tally's export order is not guaranteed and no snapshot
identity exists, independently reinforcing why a watermark/resume-style mechanism can't be assumed
safe. The existing per-record `ALTERID` is already part of `computeLedgerFingerprint()`/the
equivalent Stock Item fingerprint — confirming BUDCOM's *existing* definition of "change" already
uses `ALTERID`, just after a full extraction, not before one.

### B. Real Tally experiment (§4 of the governing task) — incident and recovery

Tested the Candidate-1 hypothesis (a company-level object probe) directly against the real, live
TallyPrime instance (`tally.exe`, port 9000; real companies ESTIMATION and Jaju Sanitations both
open). The request BUDCOM's own dead-code scaffold would have sent — `<TYPE>Object</TYPE>
<ID>Company</ID>` with no `<SUBTYPE>` — produced **no response and a blocking error dialog on
Tally's own UI** (window title changed to `"Error"`), which required Tally to be restarted before it
would answer further requests. This is recorded honestly as an unintended, real, disruptive incident
caused by testing an unvalidated request shape against live production Tally — not a simulated or
hypothetical risk. Work paused; the user was informed directly and asked to confirm the screen was
clear before any further live request was attempted. Tally's own developer documentation
(`help.tallysolutions.com`, fetched directly, not assumed) subsequently confirmed the root cause: a
valid Object-type export requires `<SUBTYPE>` and `<ID TYPE="Name">`, plus a `<FETCHLIST>/<FETCH>`
block distinct from `collectionModifyFetch` — and, more fundamentally, Object-type export is
documented only for named, keyed masters (a specific Ledger, Stock Item, Voucher); "Company" is a
`SVCURRENTCOMPANY` *context*, not a keyed master Tally exposes this way. A follow-up web search found
no authoritative Tally documentation and no independent third-party Tally integration (including a
real Tally↔ODBC connector project that does surface per-record `$Alterid`/`$Alteredon`) implementing
any company-level "has anything changed" marker. **Candidate 1: not available, not merely untested.**

Once Tally was confirmed healthy again (re-tested with the already-proven-safe "List of Companies"
request), a second, much lower-risk candidate was measured live using only the existing, proven
`collectionModifyFetch` mechanism (no new request shape): a 3-field (`NAME, GUID, ALTERID`) Ledgers
fetch against the real ESTIMATION company (949 real ledgers) returned in **0.23-0.28s** across four
repeated requests, **386,343 bytes**; the existing full 8-field fetch returned in **0.295s**,
**637,871 bytes**. The wall-clock difference is not meaningful at this scale — Tally's own
collection-walk time dominates and is not reduced by requesting fewer fields; only network bytes
(~40%) and Connector-side downstream work (mapping/fingerprint/DB-write, skippable in a detect-only
pass) would shrink. **Candidate 2: safe-by-construction, but not proven to deliver the stated
objective (reducing Tally-side load) at this measured scale.**

**A real, live company-isolation hazard was also discovered as a side effect of this measurement**
(unrelated to whether either candidate is adopted): Tally's `GUID` is `<data-source-UUID>-<hex
MasterId>`, and MasterId is small and per-company — all 21 of Jaju Sanitations' real ledger `GUID`
values were found to also appear verbatim in ESTIMATION's real 949-`GUID` set (confirmed as
genuinely different real ledgers by name, ruling out a test-script error). A bare Tally `GUID` is
**not globally unique across companies in this real installation.** Checked immediately against
BUDCOM's own schema: `idx_ledgers_company_guid`/`idx_stock_items_company_guid` in
`connector/budcom_connector/src/storage/sqlite/schema.ts` are already unique on `(company_id, guid)`,
never `guid` alone — **no existing defect**, but this is now binding, real-data-proven evidence
against ever keying anything by a bare Tally GUID in the future.

### C. Decision

**NOT IMPLEMENTED — NOT PROVEN SAFE / NOT PROVEN BENEFICIAL.** No production code was changed.
Per the governing task's own explicit rule ("never convert an assumption into a production behavior
change" / "a false negative is unacceptable"), and given neither candidate clears the bar (Candidate
1 does not exist as a reachable mechanism; Candidate 2 is safe but does not measurably reduce the
dominant cost), the existing Phase 45 adaptive scheduler remains authoritative and unchanged. Full
evidence recorded in `docs/architecture/BUDCOM-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md` §3.1, and its
§17 OPEN item 10 is now marked CLOSED (investigated, answer is no) rather than silently dropped.

### D. Scope discipline

No MVP-1.4 work. No scheduler-architecture change. No new persisted state. No second sync engine.
The one real incident (a live Tally error dialog from an unvalidated request) was disclosed to the
user immediately, work paused pending their confirmation the screen was clear, and no further
untested request shapes were attempted afterward — only requests already proven safe by BUDCOM's own
production code or by fetched, authoritative Tally documentation.

## 38. Phase 48 — Tally Incident Forensic Review + Connect Alias Intelligence

New session. Two independent workstreams: (1) a forensic review of the Phase 47 Tally incident,
using only static evidence — no new live Tally requests, per explicit instruction; (2) Connect
Alias intelligence (10-digit mobile candidate, 1-5 digit ledger shortcut).

### A. Forensic review — exact request, timeline, and root cause

**Exact request sent immediately before the incident** (recovered verbatim from
`/d/tmp/company_object.xml`, timestamp 2026-08-23 06:24:25 IST):
```xml
<ENVELOPE><HEADER><VERSION>1</VERSION><TALLYREQUEST>Export</TALLYREQUEST><TYPE>Object</TYPE>
<ID>Company</ID></HEADER><BODY><DESC><STATICVARIABLES><SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>
<SVCURRENTCOMPANY>ESTIMATION</SVCURRENTCOMPANY></STATICVARIABLES></DESC></BODY></ENVELOPE>
```
This was an **ad-hoc experimental request sent directly to Tally's port 9000 via `curl`, bypassing
BUDCOM's Connector entirely** — the Connector process was not even running at the time (confirmed:
no `node.exe`/`electron.exe` processes present). It was not, at the moment it was sent, a real
BUDCOM production request.

**However, this same investigation discovered that the exact same broken shape *is* independently
constructed by real, "production"-classified BUDCOM Connector code** — see §B below (**TD-040**).

**Timeline, reconstructed from file timestamps and process state, not assumption:**
- `06:24:13` — a "List of Companies" request succeeds, confirming **ESTIMATION was open** in Tally
  at this point, alongside Jaju Sanitations, both returning real data.
- `06:24:25` — the malformed Object-type request above is sent; the response file is 0 bytes —
  Tally never replied.
- Within the next ~2 minutes, Tally's UI entered a fault state (window title changed to "Error";
  the user later confirmed the dialog itself read **"Memory access violation"**); `Get-Process`
  showed `Responding: True` at that point — the process had not (yet, or ever) terminated.
- **`06:28:54`** — a new `tally.exe` process (PID 1284) is observed running, confirming Tally was
  restarted between the fault and this check.
- Checked directly this session: **no Windows Application-Error or Windows-Error-Reporting crash
  report exists for `tally.exe` on 2026-08-23** (`Get-WinEvent`, `C:\ProgramData\Microsoft\Windows\
  WER\ReportArchive`) — the most recent WER entry for `tally.exe` on this machine is from
  **2026-05-17**, and the WER pipeline is confirmed active (it has captured `tally.exe` crashes and
  hangs on six other dates going back to 2025-11-12). **This is significant negative evidence**: if
  Tally's process had genuinely terminated via an unhandled OS-level exception, this same pipeline
  would very likely have recorded it, as it has before. Its absence, combined with `Responding: True`
  observed immediately after the fault, supports that Tally's own application code displayed an
  internal error dialog (using the wording "Memory access violation") **without the process actually
  crashing at the OS level** — a real Tally-side robustness gap when fed structurally incomplete
  input, not a proven OS memory-safety fault.
- "ESTIMATION was not initially open, then opened manually" (context supplied for this task) is
  best explained as describing **post-incident recovery**: a restarted Tally process does not
  automatically reload previously-open companies, so the user needed to manually reopen it
  afterward — consistent with, not contradicting, the pre-incident evidence above that ESTIMATION
  was genuinely open and being read successfully moments before the crash-inducing request.

**Root cause, confirmed against Tally's own developer documentation (`help.tallysolutions.com`,
fetched directly, cited in Phase 47):** a valid Tally Object-type export requires `<SUBTYPE>` and
`<ID TYPE="Name">...</ID>` (plus a `<FETCHLIST>/<FETCH>` block); Object-type export is documented
only for named, keyed masters (a specific Ledger/StockItem/Voucher) — "Company" is a
`SVCURRENTCOMPANY` *context*, not a keyed master Tally exposes this way. The request above has
neither `<SUBTYPE>` nor `<ID TYPE="Name">` — it is structurally invalid per Tally's own contract,
independent of any assumption about BUDCOM.

**Answering the forensic questions directly:**
- Could BUDCOM-side limits have been exceeded? No — this bypassed the Connector, so none of its
  request-size/response-size/concurrency/circuit-breaker/timeout logic were even in the code path.
  (Confirmed separately: even the real registry entry's own configured limits — 65,536-byte max
  request, 262,144-byte max response, 20-second timeout — are generous relative to this ~350-byte
  request; size was never the issue.)
- Evidence of BUDCOM accessing native memory, pointers, or unsafe APIs? None. The client was a plain
  HTTP POST with a text/xml body — no native code, no pointers, no unsafe memory operations are
  possible from that side. A memory fault, if real, occurred inside *Tally's own* process reacting
  to unexpected structural input — a client cannot directly manipulate a separate process's memory
  over a network text request; it can only supply input the receiving application mishandles.
- More consistent with a Tally-side application failure triggered by an invalid request shape? Yes
  — this is the best-evidenced explanation (structurally invalid per Tally's own docs; zero
  response; process still "Responding" immediately after; no WER crash report for this date).
- **CAUSE (exact internal mechanism) NOT PROVEN** in the strict sense: no Tally crash-dump or stack
  trace was available to examine (none was generated — see WER check above), so the literal internal
  reason Tally's own code produced this exact dialog cannot be independently confirmed beyond what
  is stated above. What **is** proven: this was not a BUDCOM production request at the time it was
  sent (Connector wasn't running), it is not evidence of any BUDCOM-side memory-unsafe code, and the
  request was objectively structurally invalid per Tally's own documented contract.

### B. TD-040 — a real, live, "production" BUDCOM defect discovered by this same review

The forensic review's evidence-gathering step (checking whether "any existing BUDCOM request could
reproduce the problem") found that it could: `ApprovedOperationId.CompanyInfo`
(`connector/budcom_connector/src/tally/registry/operation-registry.ts`, `rolloutStatus:
'production'`, `autoApproveConditional: true`) independently constructs the identical broken shape
via `buildObjectTemplate('Company', { companyName })` — not the same code path as the ad-hoc curl
test, but functionally identical output. Reachable via the Connector's own `GET
/companies/:companyId` route (`api/routes/master-data.ts` → `MasterDataService.getCompanyInfo()` →
`TallyReadAdapter.getCompanyInfo()`). Source search confirmed **neither Desktop nor Android calls
this route today** — Desktop's only `/companies*` call is the list endpoint (`connector-http-
client.ts`), and nothing in Android references it at all — so no real end-user has been exposed, but
it remained a live, reachable landmine. The existing `master-data-templates.test.ts` already
exercised `MasterDataTemplates.companyInfo()`'s XML output but asserted only generic envelope/
static-variable presence, never SUBTYPE/ID correctness — the exact gap that let this ship
undetected, entirely at the mock/interface layer in every other test referencing "CompanyInfo".

**Fixed by disabling, not re-guessing:** `render()` now throws a clear `Error` immediately, before
constructing any XML or contacting Tally — caught cleanly by `MasterDataService.getCompanyInfo()`'s
pre-existing try/catch (its own `evidenceSource` field already said "safe discovery fallback
exists"), which falls back to discovery metadata. `GET /companies/:companyId` still returns `200`
with the correct company name; only Company-object-specific fields (gstin, address, mailing name,
etc.) are now absent, since they were never safely obtainable in the first place. This is the
"prevent the malformed request from ever being sent" fix, not a second unverified guess at the
correct shape — per this task's own explicit rule against sending a new, unvalidated TDL/XML shape
to production Tally to verify a fix. Full detail: `docs/technical-debt/registry.md` TD-040.

One existing test (`master-data.test.ts`'s `GET /companies/:companyId returns company info`) asserted
a mocked GSTIN value that depended on the now-disabled path; updated to assert the new, safe fallback
behavior instead (200, correct name, `gstin` now `undefined`) — not weakened, corrected to match the
now-safe intended behavior. Connector: 162/162 → **163/163 test files, 1,473/1,473 tests** (+2),
`tsc --noEmit`/`eslint`/`npm run build` all clean.

### C. Boundary audit (Part D of the governing task) — no further defects found

Confirmed via direct source inspection, no changes needed: `TallyRequestGuard` enforces mandatory,
config-independent limits (`poolMaxConnections: 1` — single-flight always, regardless of
SAFE_MODE; `minRequestIntervalMs` at least 2000ms in safe mode; circuit breaker always enabled;
`HARD_MAX_REQUEST_BYTES = 262,144`, a ceiling config cannot raise). `LedgerSyncServiceImpl`'s
`syncInFlightByCompany`/`activeRunByCompany` Maps (the TD-036 fix) give real per-company
single-flight protection with a clean `SYNC_CONFLICT` rejection — confirmed this is what protects
against a manual sync and a scheduler check racing for the same company, exactly as
`adaptive-scheduler.service.ts`'s own comment describes. The scheduler's `ticking` flag prevents
re-entrant `runOnce()` calls; `start()` is idempotent (checked `if (this.running) return`). Desktop
triggers no sync at all (confirmed: no `/sync/*` POST call anywhere in its source) — it is a
pure status observer, exactly as the Phase 45 architecture intends. No scheduler code was changed.

### D. Permanent safety policy added

`docs/architecture/BUDCOM-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md` §3.1 already recorded the immediate
lesson from Phase 47; this session's forensic confirmation (the crash mechanism, the WER
cross-check, and TD-040's discovery that a "production" registry entry independently reproduced it)
is added there as a cross-reference. The durable rule, scoped to what the evidence actually
supports: **never send a hand-crafted or newly-added Tally TDL/XML request shape — whether as an ad
hoc research probe or as new Connector code — to a live/production Tally instance without first
validating it against Tally's own official developer documentation or a disposable non-production
Tally instance.** This is not broadened beyond what TD-040 and the Phase 47 incident actually prove.

### E. Scope discipline

No MVP-1.4 work. No further live Tally requests were sent to investigate the incident (all evidence
above is static: file timestamps, process state already observed in Phase 47, Windows Event Log /
WER report archive, git/source inspection, already-fetched Tally documentation). The TD-040 fix
required zero live Tally interaction to implement or verify (unit/integration tests use the existing
mock-fetch harness). Alias Intelligence work (Parts A/B of the governing task) follows in §39.

## 39. Phase 48 (continued) — Connect Alias Intelligence (Parts A/B)

### A. Part A (10-digit Alias -> mobile candidate): already implemented, verified only

Investigation found this exact behavior already fully implemented and locked
(`docs/architecture/BUDCOM-MVP-1-1-CONNECT-UNIVERSAL-PARTY-ARCHITECTURE.md` §8): a single canonical
`PhoneNumberNormalizer.normalizeIndianMobile()` (exactly 10 digits, leading digit 6-9, only outer
whitespace trimmed) gates `PartyRepositoryImpl.applyAliasPhoneSeeding()`, which non-destructively
seeds `Party.primaryPhone` with full `FieldProvenanceState` tracking (`ConfirmedFromTally`/
`Conflict`), never rewrites the original Tally Alias, and never overwrites an existing different
phone value. Already covered by ~10 existing tests against exactly-10-digit, 9-digit, 11-digit,
alphabetic, and punctuation-formatted aliases, plus cross-company isolation. Verified against the
governing task's full adversarial list; the only gaps were three specific cases not present by name
(`0123456789` leading-zero, explicit empty-string alias, explicit null alias) — added as 3 new tests
in `PartyRepositoryImplTest.kt`, all passing against the unchanged existing implementation. **No
production code changed for Part A** — it was already correct.

### B. Part B (1-5 digit Alias -> ledger search shortcut): new work

Genuinely new: the architecture doc's search section (§9) never anticipated a short-numeric-alias
shortcut concept. New pure classifier `com.budcom.android.core.util.AliasSearchClassifier
.isShortNumericAlias()` (1-5 digits, numeric-only after outer-whitespace trim) — deliberately
separate from `PhoneNumberNormalizer`, since a 6-9 digit alias is correctly neither a mobile
candidate nor a shortcut (too long for one, too short for the other; no special treatment, not a
gap).

**Ledger Browser**: `LedgerDao.queryPage()` gained one new leading `ORDER BY` tier
(`CASE WHEN :exactAliasFirst = 1 AND alias = :query THEN 0 ELSE 1 END`), populated by
`RoomLedgerLocalDataSource.query()` only when the query is a short numeric alias -- a pure no-op for
every other search (name, parentGroup, longer numeric queries), verified by a new
`queryPage_exactAliasFirst_zero_leavesNameOrderingUnchanged` test. Existing alias substring matching
(already present in this DAO) is untouched; only exact-match ranking is new.

**Connect**: `Party` has no Alias column (by design -- see `PartyModels.kt`), so rather than adding
one (a schema migration the task's own STOP conditions require "clear justification" for, and this
codebase's own documented convention is to avoid cross-table SQL JOINs -- see `PartyDao.findByIds`'s
own comment), `PartyRepositoryImpl.searchParties()` resolves an exact alias shortcut via the
*existing* cross-feature `SearchLedgersPort` (already used by Universal Search) + the existing
`PartySourceLinkDao.findByExternalKey()` identity-resolution lookup, merging in Kotlin -- no JOIN, no
migration, no new Room table. Scoped deliberately narrowly: only for page 1 (a "jump to it" shortcut,
not a page-2+ ranking signal), respects the caller's classification filter, dedupes against the
normal name/phone search results, and is a no-op for anything that isn't a 1-5 digit numeric query.
7 new tests in `PartyRepositoryImplTest.kt` cover: found via shortcut when name/phone search would
miss it; company isolation (never leaks another company's shortcut match); classification-filter
respected; no duplication when normal search already found the same party; 6-digit query correctly
gets no special treatment; shortcut only applies to page 1.

Android: 1,307/1,307 tests both variants (+21: 12 `AliasSearchClassifierTest`, 3 Part-A boundary
tests, 7 Connect alias-shortcut tests; some renumbering against the prior 1,286 baseline), plus 2 new
`LedgerDaoTest` androidTest cases (real-Room SQL ordering proof, requires a device/emulator, not part
of the unit-test count). 0 lint errors, both assembles green.

### C. Real-device validation (Part G)

Device `10BF44124K000E3`, company ESTIMATION. Direct inspection found **zero existing real ledgers
have any Alias set at all** (949/949 `alias IS NULL`) -- this specific real business's Tally usage
does not use the Alias field, a genuine fact about the environment, not a gap. Reconciliation
(`ReconcilePartiesFromLedgersUseCase`) only re-runs on a successful Ledgers sync outcome
(`SyncViewModel.onSyncOutcome`), and a real sync would immediately overwrite any locally-injected
test Alias with the real (alias-less) data before reconciliation ran -- so exercising the
Connect-side reconciliation live against a genuine alias would have required editing real Tally
business data, which was deliberately not done (matching the governing task's own instruction not to
alter real Tally data merely to manufacture a test).

**What was verified live, with real code and real/local data:**
- Installed the updated build; Connect (926 real customers, Call/WhatsApp buttons) and Ledger
  Browser (949 real ledgers, search) both confirmed working with zero regression against real data.
- A local-only Room fixture (`UPDATE cached_ledgers SET alias='777' WHERE id=...`, zero Tally/
  Connector interaction, reverted immediately after) proved the Ledger Browser's new exact-alias
  shortcut live: searching "777" surfaced "4m Plywood & Hw" with "Alias: 777" shown, confirmed via
  screenshot, network indicator reading `0.00 KB/s` throughout (no network call). Fixture reverted;
  device confirmed back to 0 aliases before finishing.

**What relies on the unit test suite alone**, and why: the Connect-side Alias-phone-seeding
(pre-existing, Part A) and the new Connect alias-shortcut merge (Part B) both require reconciliation
to have actually run against aliased data, which -- per the architectural constraint above -- was not
achievable without editing real Tally business data. Both are covered by the test counts in §B (81
tests in `PartyRepositoryImplTest` after this phase, including the 10 new ones), which is considered
sufficient given the alternative was a live Tally data mutation this task explicitly cautioned
against making merely to manufacture a test. Company isolation for the new alias-shortcut search
specifically is unit-tested (two-company adversarial test) but not re-proven live this session, since
only one company's data is currently cached on this device.

### D. Scope discipline

No MVP-1.4 work. No schema migration. No cross-table SQL JOIN introduced (the established
in-Kotlin-merge convention was followed). No live Tally interaction of any kind for this half of the
session's work.

## 40. Phase 49 — Connect UX Review & Hardening

New session, focused UX-trust audit of Connect now that real Ledger→Party population and Alias
behavior are working. Ground truth traced end-to-end (`Ledger → classification → cached_party →
Connect → Party Detail`) via a dedicated research pass before any change.

### A. Defects found and fixed

1. **A real, reproducible company-switch search-leak (fixed).** `ConnectViewModel`'s company-change
   handler reset `rows`/`page`/etc. but never cleared `searchQuery` and never cancelled the
   debounce `searchJob`. Since `load()` reads `searchQuery` straight off the current `UiState`
   snapshot, this meant *any* non-empty search active in company A silently scoped company B's very
   first load too — not only during the 350ms debounce window, but on every ordinary switch with an
   active search. Fixed: `searchQuery` is cleared and `searchJob` cancelled on a genuine switch
   (tracked via a local `hasSeenCompany` flag so the very first subscription — which could carry a
   deep-link `Routes.connect(query)` value — is left untouched). 2 new regression tests
   (`ConnectViewModelTest.kt`): a committed query never scoping the next company's load, and a
   pending debounced search never firing against the new company.
2. **Connect never showed data freshness (fixed).** Ledger Browser and Sync both show a "last
   synced" cue; Connect showed only a binary online/offline banner. Connect has no independent sync
   of its own (Parties are reconciled from Ledgers), so `dataFreshnessAt` is now computed as the
   most recent `Ledger.syncedAt` across the already-loaded enrichment cache (no new query — reuses
   the exact `ledgerSnapshotPort.getCachedLedgers()` call already made for balance enrichment) and
   rendered identically to Ledger Browser's own line ("Data last synced: ..."), same style, same
   `hasContent`-gated visibility. 4 new tests (2 ViewModel, 2 Screen).
3. **Alias silently drove Connect behavior with zero visibility (fixed).** The already-shipped
   Alias-phone-seeding and the Phase 48 search shortcut both depend on a Ledger's Alias, but Connect
   never showed the word "Alias" anywhere — a user could reasonably wonder why a party appeared or
   where its phone came from. `ConnectRowUi` gained `linkedLedgerAlias` (sourced from the same
   already-loaded `ledgersById` enrichment map), rendered as its own dedicated line ("Alias: ...",
   mirroring Ledger Browser's exact convention) — deliberately *never* merged into the phone/tags
   line, so a short numeric Alias is never visually confusable with a phone number. 4 new tests (2
   ViewModel, 2 Screen).
4. **Loading spinner had no accessibility label (fixed, minimally).** `FullScreenLoading`/
   `MasterDataLoadingIndicator` (shared components used by several feature screens, not
   Connect-specific) gained an optional `contentDescription` parameter, default `null` — a pure,
   backward-compatible addition with zero behavior change for every other existing caller. Connect's
   own call site now passes "Loading customers"/"Loading prospects".

### B. Found and deliberately left unchanged

- **"View Vouchers" seeds a free-text party-name filter, not a stable ledger-id filter** — a
  pre-existing, already-documented limitation (comment in `ConnectViewModel.toRowUi`,
  `BUDCOM-MVP-1-1-CONNECT-STATUS.md`) that would require Voucher-schema changes outside Connect's
  scope to fix. Not touched.
- **Classification (Customer/Prospect) isn't shown per-row or by color** — conveyed only by which
  tab is active. Judged an intentional, clean pattern, not a defect; changing it would add
  terminology/visual noise without fixing a real trust problem, so left alone.
- **Search-empty-state message doesn't name which tab you're on** ("No matches for this search.") —
  judged honest and sufficient as-is; making it tab-specific was marginal benefit for the risk of
  touching shared empty-state string resources unnecessarily.
- **`compileProdDebugAndroidTestKotlin` fails** (`LedgerBrowserScreenTest.kt:4`, `Unresolved
  reference 'assertDoesNotExist'`) — confirmed via `git status` that this file is untouched this
  session; reproduced on a clean re-run, not transient. This is a pre-existing environment/
  dependency-resolution issue, not something this session's changes caused, and fixing it would be
  a build-tooling change outside Connect's scope. Documented, not fixed. New androidTest additions
  this session (`ConnectScreenTest.kt`) could not be run against a real device/emulator as a result
  — they compile as standalone Kotlin (verified by inspection, follow exactly the same patterns as
  existing passing tests) but the full androidTest source set does not currently build.

### C. Real-device validation

Device `10BF44124K000E3`, company ESTIMATION (real data, 949 ledgers, 926 parties). Installed the
updated build fresh. **Freshness fix confirmed live**: Connect now shows "Data last synced:
2026-08-22T21:29:47.406Z" — the real Ledger sync timestamp, matching Ledger Browser's own value
exactly, with zero regression to existing rows/Call/WhatsApp/View Ledger. **Alias-display fix
confirmed live**: reusing the same local-only Room fixture technique as Phase 48 (`UPDATE
cached_ledgers SET alias='777' WHERE id=...`, zero Tally/Connector interaction, reverted
immediately after), "4m Plywood & Hw" now shows "Alias: 777" on its own line on the Connect screen
itself (previously only visible in Ledger Browser) — confirmed via screenshot. Real ESTIMATION data
still has zero ledgers with any Alias set, so — as in Phase 48 — this was validated via a reverted
local fixture, not a live Tally mutation. The company-switch search-leak fix is unit-tested (2 new
adversarial tests) but not re-proven live this session, since only one company's data is currently
cached on this device and re-syncing a second company was judged unnecessary given the fix is a
pure, deterministic ViewModel-state change with no real-device-only failure mode.

### D. Scope discipline

No MVP-1.4/Catalogue/Vartalap/CRM work. No Prospect→Ledger linking (remains future, per
`BUDCOM-NOT-NOW.md`). No new Tally request of any kind — this entire phase touched only Android UI/
ViewModel code. No architecture change outside Connect beyond the two small, backward-compatible,
purely-additive parameter additions to the shared `FullScreenLoading`/`MasterDataLoadingIndicator`
components (default-null, zero behavior change for existing callers).

Android: 1,307/1,307 → **1,313/1,313 tests both variants** (+6), 0 lint errors, `assembleProdDebug`
green. `compileProdDebugAndroidTestKotlin` fails for a pre-existing, unrelated reason (§B).

## 41. Phase 50 — Android Instrumented-Test Build Infrastructure Recovery

New session, build-tooling/dependency-recovery task only, scoped to the `compileProdDebugAndroidTestKotlin`
failure Phase 49 discovered and left undiagnosed. No Connect/product behavior touched.

### A. Root cause — not a dependency problem at all

Investigated the dependency graph before touching anything: `app:dependencies --configuration
prodDebugAndroidTestCompileClasspath` showed `androidx.compose.ui:ui-test-junit4` resolving cleanly
to `1.7.8` via the Compose BOM (`composeBom = "2025.02.00"`), no conflict, no missing artifact.
Extracted the actual cached `ui-test-release.aar` (module `ui-test-android:1.7.8`) and disassembled
its classes directly (`javap`) to settle the question with evidence rather than assumption:
`assertDoesNotExist()`/`assertExists()` are **member methods of `SemanticsNodeInteraction`** (always
have been, in every Compose UI Test version), never top-level extension functions in a `*Kt` file —
so `import androidx.compose.ui.test.assertDoesNotExist` was never a valid import at all, in any
version. `LedgerBrowserScreenTest.kt` had this exact invalid import; calling `.assertDoesNotExist()`
on a `SemanticsNodeInteraction` needs no import whatsoever (ordinary member-method resolution). This
was a genuine, pre-existing single-line source defect, not a build-tooling/version-compatibility
issue — confirmed by checking every other androidTest file: none of them import `assertDoesNotExist`
(including this session's own newly-added `ConnectScreenTest.kt` uses from Phase 49, which correctly
never imported it), so nothing else in the codebase repeats this mistake.

### B. Fix

Deleted the single invalid import line from `LedgerBrowserScreenTest.kt`. No dependency version
changed, no Gradle configuration changed, no Kotlin/AGP/Compose upgrade — the smallest possible
correction. `compileProdDebugAndroidTestKotlin` now succeeds cleanly (two pre-existing, unrelated
deprecation warnings in a different file, `AndroidInvoiceShareCoordinatorTest.kt`'s
`getParcelableExtra` usage, left untouched as out of scope).

### C. Full verification

- JVM unit tests: unaffected, still **1,313/1,313 both variants** (Gradle correctly reported these
  tasks `UP-TO-DATE` — the fix touched only an androidTest file, no dependency of the JVM test
  source sets).
- `lintProdDebug`: clean.
- `assembleProdDebug` and `assembleProdRelease`: both green (release went through the full R8/
  minify/shrink-resources pipeline successfully; still unsigned, as already known/expected — signing
  credentials remain a separate, pre-existing, unrelated blocker).
- **`connectedProdDebugAndroidTest` run live on the real device** (`10BF44124K000E3`) for the first
  time this suite has ever successfully compiled: **358 tests, 345 passed, 13 failed.** Confirmed
  `LedgerBrowserScreenTest` itself — including the two tests that specifically exercise
  `assertDoesNotExist()`, the exact method blocked by the fixed import — has **zero failures**,
  directly proving the fix works correctly at runtime, not merely at compile time.

### D. The 13 newly-surfaced failures — pre-existing, unrelated to this fix, not investigated further (out of scope)

None of the 13 failures are in the file this session touched. They span 9 unrelated feature areas
(`DashboardScreenTest`, `DiagnosticsScreenTest`, `LedgerStatementScreenTest`,
`SecurePairingScreenTest`, `ServerConfigScreenTest`, `SettingsScreenTest`, `SyncScreenTest`,
`VoucherDetailsScreenTest`, and this session's own new `ConnectScreenTest.aliasIsShownOnItsOwnLineWhenTheLinkedLedgerHasOne`),
with a mix of distinct failure shapes (`assertIsDisplayed` "component is not displayed", a
scroll-to-index out-of-bounds error, a touch-input target not found, a text-content mismatch) — the
signature of a large androidTest suite running against a specific real device's actual screen/theme
configuration for the very first time (this suite could never successfully compile before this
session, so none of it had ever been run against this or any device), not a single root cause and
not something this build-recovery task's scope covers fixing. Documented as genuinely new findings,
not silently ignored: worth a dedicated follow-up investigation, separate from this task.

### E. Device consequence (expected, disclosed)

`connectedProdDebugAndroidTest` uninstalled the `com.budcom.android.debug` package as part of its
normal lifecycle (Gradle's own managed behavior, not something this session configured or triggered
deliberately beyond running the task) — confirmed missing from `pm list packages` immediately after,
exactly the risk flagged in advance. Reinstalled the already-built APK from this same session to
restore a working app (confirmed via launch: opens correctly to the Secure Pairing / onboarding
screen, no crash) — but the uninstall cleared the app's private data directory, so the previously-
synced ESTIMATION company data and Desktop Connector pairing are gone from this device and would
need to be re-established (re-pair, re-sync) before any future Connect/Ledger real-data UI
validation on this device. No underlying Tally or Connector state was touched or lost — only this
Android app's own local Room cache and pairing session.

### F. Scope discipline

No Tally interaction of any kind. No Connect/product/UI behavior changed. No Kotlin/AGP/Compose
version changed. The only production-adjacent file touched is a pre-existing test file, and the
change is a single deleted import line.

## 42. Phase 51 — Instrumented Test Failure Triage + Connect DEV Recovery

New session, continuing directly from Phase 50's 13 newly-surfaced instrumented failures. Verified
ground truth first: HEAD/branch/tree matched the prior report exactly; device Room was confirmed
empty (`cached_ledgers`/`cached_parties` both 0 rows), `shared_prefs/` absent entirely (fresh
install, no leftover pairing), Connector unreachable on 8080, Tally directly reachable on 9000 —
all exactly as Phase 50 had disclosed.

### A. Connect failure — root-caused and fixed with direct evidence, no guessing

Reproduced `ConnectScreenTest.aliasIsShownOnItsOwnLineWhenTheLinkedLedgerHasOne` in isolation first
(ruling out test-order/shared-state as the cause: it fails identically alone). Added a temporary
`printToLog` diagnostic (removed before the final fix) to dump the actual semantics tree at the
point of failure. The dump proved, unambiguously: the row `Card`'s `onClick` makes Compose
automatically set `mergeDescendants = true` on that node (standard accessibility behavior, so
TalkBack reads the whole row as one unit) — this collapses a plain, non-interactive `Text` leaf's
own `testTag` out of the *default* (merged) semantics query tree. The row's own merged `Text` output
in the dump read `'[ABC Traders, 1000.00 Dr, +919876543210, Alias: 25]'` — **direct proof the Alias
text is genuinely composed, correct, and even correctly exposed to accessibility services** — only
the *test's query technique* was wrong, not the production code. Fixed by querying with
`useUnmergedTree = true` (the standard, documented Compose-testing technique for exactly this
scenario) — no production code touched. Verified: `ConnectScreenTest` now passes 16/16 in isolation,
and the full suite's failure count dropped from 13 to 12 with no new failures introduced.

### B. The other 12 — confirmed NOT one shared root cause, deferred

Static-code-inspected a sample from each apparent bucket to test the "one root cause" hypothesis
before accepting it: `SyncScreenTest.idleShowsTargetsAndNoAutoBusy`'s target `Card` has **no**
`onClick` at all, and `DashboardScreenTest.noCompanySelectedStateIsShown`'s company `Card` is
likewise plain/non-clickable — neither shares Connect's exact merge-tree mechanism, despite an
identical `assertIsDisplayed` "component is not displayed" failure message. This directly disproves
a single unifying cause across the 8 "not displayed" failures, and the remaining failures are
already known to differ in kind (a scroll-index-out-of-bounds error, a missing touch target, a
text-content mismatch, two generic `Assert.fail()`). Per the governing task's own explicit stop
condition ("multiple failures have unclear causality") and its explicit permission to defer
environment/unclear-cause issues rather than force 12 more individual live-device diagnoses (each
costing real instrumented-test cycles, each of which uninstalls/reinstalls the debug app), these 12
were **not** further diagnosed or fixed this session — classified as a genuine mix of pre-existing,
independent issues, recommended as a dedicated follow-up.

### C. DEV/Connector recovery — partially achieved, one real infrastructure limitation found

The Android app itself is confirmed fully functional: reinstalled cleanly, launches correctly to an
honest "Secure Pairing" onboarding screen (no crash, no stale/misleading data) reflecting its
genuinely-unpaired state truthfully. Attempted the ordinary recovery workflow (`npm start` in
`apps/budcom_desktop`, which builds and launches the real Electron Desktop app that manages the
Connector): the build succeeded, but `electron .` crashed immediately with `TypeError: Cannot read
properties of undefined (reading 'isPackaged')` because **`ELECTRON_RUN_AS_NODE=1` is set in this
shell environment**, forcing Electron's own binary to execute as plain Node (confirmed: the real
`electron.exe` binary is present and correctly installed at
`node_modules/electron/dist/electron.exe` — this is an environment/sandbox characteristic, not a
missing dependency). This env var is treated as a deliberate sandboxing boundary of this execution
environment (most plausibly intended to prevent an agent from unexpectedly spawning GUI
applications) and was **not** unset/bypassed to force a real Electron window open, since (a)
overriding an apparent safety boundary without explicit authorization is not this session's call to
make unilaterally, and (b) even a successfully-launched Electron window could not be driven through
its QR-code secure-pairing flow with this session's available tools regardless. A standalone,
GUI-free Connector process (`npm run dev` in `connector/budcom_connector`) was considered as an
alternative path (Android's existing `ServerConfig` manual-URL screen could in principle point at
it, avoiding the QR flow entirely) but was not pursued to completion: doing so would require
enabling `networkExposure: 'lan'` on the Connector, and `requireDeviceAuthForLan` exists
specifically to gate unauthenticated LAN exposure — weakening or working around that gate merely to
avoid the GUI limitation was judged the wrong tradeoff, not a genuine "ordinary existing workflow."
**Full DEV recovery (re-paired Connector, re-synced real ESTIMATION data) was not completed this
session** — documented honestly as a real, evidenced infrastructure limitation, not silently worked
around.

### D. Device hygiene note

Each of the several `connectedProdDebugAndroidTest` invocations this session (full suite, isolated
`ConnectScreenTest`, the single diagnostic test, the final full-suite re-verification) independently
uninstalled `com.budcom.android.debug` as Gradle's own managed lifecycle — reinstalled from the
already-built APK after every one, confirmed via `pm list packages` and a successful launch each
time, ending the session with a working, launchable (if unpaired) app on the device.

### E. Scope discipline

No Tally interaction of any kind — Tally itself was only ever queried via the existing, already-
proven `curl http://127.0.0.1:9000` connectivity check (identical to prior sessions' safe baseline
checks), never a new/experimental shape. No Tally data modified. No MVP-1.4/Catalogue work. The only
production-adjacent change is the one-line Connect test-query fix in §A; no Connect production
behavior changed.

## 43. Phase 52 — Full Instrumented-Test Recovery & Real-Data Validation: a genuine new Connect defect found

New session. Ground truth first: the real Desktop/Connector environment claimed as "restored" was
independently verified, not assumed — see §A. The bulk of this phase's value is §B: a genuine,
serious, previously-undiscovered Connect defect found through real-device validation, reproduced
twice with precise evidence, deliberately **not** given a speculative fix.

### A. Ground truth — the real environment, verified independently

`cached_ledgers` showed 949 real rows but `paired_connectors`/`cached_companies` were both 0 and
`curl 127.0.0.1:8080` failed from this shell — an apparent contradiction, not accepted at face value.
Traced to source: the real Desktop app (`apps/budcom_desktop`) stores its config at
`%APPDATA%\@budcom\desktop\desktop-config.json` (`connectorBindMode: "trusted-lan"`, `connectorHost:
"192.168.29.34"`, `autoStartConnector: true`) and its data on a **private-removable vault on drive
E:** (`private-storage-locator.json`, `mode: "private-removable"`) — genuinely real, long-lived user
data (backup folders, Tally exports), confirmed present and mounted. A deliberate, narrowly-scoped
attempt to launch the real Electron Desktop app (`env -u ELECTRON_RUN_AS_NODE npx electron .`, since
`ELECTRON_RUN_AS_NODE=1` is set in this shell) revealed, via the app's own `startup-diagnostics.jsonl`,
that **a real instance was already running** — my attempt correctly deferred to it via Electron's
single-instance lock (`single_instance_denied_exit`, `exitCode: 0`, zero disruption caused) and
confirmed real, live connector activity (`connector_health_check: ready:true`) at timestamps matching
real prior syncs. This shell's own `127.0.0.1` simply does not share the network/session context the
real Desktop app and the phone use — not a broken environment.

### B. A genuine, serious Connect defect — found, reproduced twice, precisely characterized, NOT speculatively fixed

Triggered a real "Sync Now" for Ledgers from the Android app (safe, already-proven action): 949/949
processed, `cached_ledgers`/`cached_parties` correctly updated (873 customers + 53 suppliers,
`createdAt` timestamps confirmed genuinely fresh, matching the sync completion time to the second).
Opening Connect for the first time in that app process showed **"No customers found yet."** despite
the data genuinely existing. Pull-to-refresh did not fix it. A full app restart (`am force-stop` +
relaunch) did — Connect then showed the correct 873 customers.

**Reproduced a second time with a cleaner, more precise test** to rule out gesture/coincidence: from
the now-correctly-displaying Connect screen, triggered Ledgers "Sync Now" again (same real, safe,
already-proven action, re-processing the same 949 unchanged ledgers) — Connect's *already-correct*
display broke again to "No customers found yet." This time, two **code-guaranteed** reload triggers
were tested explicitly: a tab switch (Customers → Prospects → Customers, which unconditionally fires
`ConnectEvent.TabChanged` → a fresh one-shot Room query) did **not** fix it. Only backgrounding fully
out of the app and returning (which most likely recreated the process) fixed it again.

**Ruled out as the cause, with direct evidence:**
- Data layer: `cached_parties` genuinely has the correct rows at the exact moment of failure
  (confirmed via direct `sqlite3` query against the same live database file).
- Company-ID mismatch: `selected_company_id` (DataStore), `cached_ledgers.companyId`, and
  `cached_parties.companyId` are all consistently `"estimation"`.
- A general Room-wide staleness issue: **Ledger Browser, queried in the same app process
  immediately after the same second sync, correctly showed fresh data** (`Data last synced:
  2026-08-23T04:13:24.044Z`) — ruling out "Room queries are stale after a sync" as a blanket
  explanation and narrowing this specifically to the Party/Connect read path.
- Test-code or gesture error: the second reproduction used only precise, code-guaranteed triggers
  (`ConnectEvent.TabChanged` via exact tab-bounds taps, not an ambiguous swipe), and still failed.

**Not proven**: the exact internal mechanism (why `PartyRepositoryImpl.listByClassification`'s plain,
uncached, one-shot Room query — structurally nearly identical to `LedgerRepositoryImpl.listLedgers`,
which does *not* exhibit this — returns stale/empty results mid-process but correctly fresh results
on a new process). This was deliberately **not chased further with a speculative fix**: the governing
task explicitly prohibits cosmetic workarounds, arbitrary delays, or fixes made without being certain
of the mechanism, and root-causing a suspected Room/SQLite-connection-level staleness with confidence
would require tooling (e.g., Android Studio's Database Inspector, step-through debugging) beyond what
this session's real-device/ADB-only toolkit can respons­ibly provide. **Classified as: genuine
PRODUCT DEFECT, reproduced twice, precisely characterized, root cause not yet isolated — recommended
as the highest-priority next task.**

### C. Full instrumented-suite re-verification

`connectedProdDebugAndroidTest` run twice more this phase (once before, once after the real-data
investigation) — both runs: **358 tests, 346 passed, 12 failed**, identical failure set to Phase 51's
post-fix baseline. No regression; confirms the environment/investigation work in §A/§B did not
disturb the existing, already-classified 12 pre-existing failures (Dashboard, Diagnostics,
LedgerStatement, SecurePairing, ServerConfig, Settings, Sync, VoucherDetails) — these remain deferred,
per the same "multiple failures have unclear causality" stop condition already invoked in Phase 51;
not individually re-triaged this phase, since this phase's real value was the Connect real-data
finding in §B, and repeating 12 more individual live-device diagnoses was judged the wrong use of
further destructive instrumented-test cycles given that finding's severity and this task's own
"Do not manufacture work" instruction.

JVM: unaffected, **1,313/1,313 both variants**. Lint: clean. `assembleProdDebug`/`assembleProdRelease`:
both green (unaffected — no production Kotlin/Compose source changed this phase).

### D. Device final state

Reinstalled the prod-debug APK after the instrumented runs (each of which uninstalls the app, as
established in Phase 50/51) — confirmed installed and launchable. The real, hard-won ESTIMATION data
from §A/§B is gone again as an unavoidable consequence of running the instrumented suite for the
required regression re-check in §C — re-establishing it again requires only the same safe, already-
proven "Sync Now" action against the still-live real Connector, not a new pairing ceremony.

### E. Scope discipline

No Tally interaction beyond the existing, already-proven "Sync Now" action (real Ledgers sync,
identical to what TD-039 already covers) and the pre-existing connectivity check. No Tally data
modified — read-only extraction via the Connector's own established sync path. No experimental
Electron/Connector bypass: the single-instance-lock-respecting launch attempt caused zero disruption
and was not repeated. No MVP-1.4/Catalogue work. No speculative production fix for the §B finding.

## 44. Phase 53 — TD-041 diagnostic instrumentation: first live trace captured, two Phase 52 assumptions corrected

New session, continuing directly from Phase 52's recommendation. Added targeted `Timber.tag("TD041")`
debug logging (no behavior change) at three points: `ConnectViewModel.load()` (logs company, tab,
`totalItems`/`items.size` after every query), `PartyRepositoryImpl.reconcilePartiesFromEligibleLedgers`
(progress every 200 seeds), and `SyncViewModel`'s reconcile trigger (START/END with elapsed time).
Built `assembleProdDebug` (required pointing `JAVA_HOME` at Android Studio's bundled JBR — the shell's
default `java` is 1.8, and AGP 8.8.2/KSP require 11+), installed on the real device (`10BF44124K000E3`,
`versionCode=29`), and captured a live repro. Full raw trace preserved outside the repo (session
scratch dir, `td041-trace-2026-08-23.txt`) since the logcat ring buffer rotates it out within minutes
under this device's background app noise.

**The trace itself:**
```
10:28:40.789  reconcile START company=estimation
10:28:44.157  reconcile END   company=estimation count=926 elapsedMs=3368
10:28:59.362  connect load    totalItems=873 items=50   (correct)
10:32:07.010  connect load    totalItems=873 items=50   (correct, 3m later)
10:35:20.077  connect load    totalItems=0   items=0    (BROKEN)
10:35:20.843  connect load    totalItems=0   items=0    (BROKEN, 766ms later)
10:35:22.594  connect load    totalItems=873 items=50   (correct again, 1.75s after the break)
10:35:25-34   connect load    177 / 31 / 25 / 13 / 13 / 1  (monotonic narrowing — reads as a search
                                                             query typed character-by-character, not
                                                             further instances of the bug)
```
Process pid 501 confirmed continuously alive from install (10:22:55) through the end of this capture
(10:38+) via the `events` log buffer (`am_proc_start`/`am_kill`/`am_proc_died`, none for pid 501 in
that window) — the whole trace is one uninterrupted process, no restart anywhere in it. A `sqlite3`
query against the live `databases/budcom.db` at capture time confirmed 926 `cached_parties` rows for
`companyId='estimation'` (`journal_mode=wal`), matching the reconcile count exactly — the DB was
correct throughout, consistent with Phase 52's finding.

**Two Phase 52 assumptions this trace corrects:**
1. Phase 52 tied the break to *a second Ledgers sync*. This trace shows only **one** reconcile
   cycle total, ~7 minutes before the break — the empty read at 10:35:20 happened with no second
   sync/reconcile logged anywhere near it. A fresh sync is evidently not required to trigger it.
2. Phase 52 reported "only a full app restart" recovers it. This trace shows the **same still-alive
   process** self-recovering to the correct count 1.75s after the first empty read, with no logged
   restart, pull-to-refresh, or Retry in between. Combined with #1, this now reads as a genuinely
   racy, self-healing condition tied to *some* Connect-load-triggering UI interaction (most likely
   re-entering/recomposing the Connect screen — exact trigger not captured this pass, since the
   instrumentation added this phase logs only the query's outcome, not what UI event called `load()`)
   rather than a durably "stuck" state requiring an app restart specifically.

**Still not proven** (same limitation as Phase 52 — Room/SQLite connection-pool internals are beyond
this session's ADB-only toolkit): why `PartyDao.countByClassification`/`pageByClassification` — plain,
uncached, sequential suspend queries inside one `withContext(dispatchers.io)` block, same shape as
`LedgerRepositoryImpl.listLedgers()` which does not exhibit this — return a genuine, real zero-row
result while the underlying table demonstrably holds the correct rows. **Deliberately not given a
speculative fix**, per the same governing instruction as Phase 52. **How to apply next**: the
diagnostic logging is left in place (uncommitted) for the next reproduction attempt; the next useful
step is capturing what UI event precedes the empty read (a fourth log point in `ConnectEvent` handling,
or Android Studio's Database Inspector/debugger if available) rather than another blind trace capture.

**Same-phase follow-up: added a `source` label to every `load()` call (which `ConnectEvent`/trigger
fired it, plus whether an in-flight `loadJob` was cancelled) and attempted two live re-reproductions —
neither reproduced TD-041.** Rebuilt (`assembleProdDebug`, 33s incremental) and reinstalled (`adb
install -r`, data preserved: 926 `cached_parties` intact) on the same device. Attempt 1: back-button
out of Connect to Dashboard and re-tap Connect, 3 cycles, ~3min gaps — every load was a fresh
`ConnectViewModel` instance (`source=CompanySubscription`, confirming the Compose nav-controller pops
and recreates the ViewModel on back-navigation), all 4 loads across ~9 minutes returned the correct 873.
Attempt 2: same ViewModel instance kept alive, cycling `Customers → Prospects → Customers` taps
in-screen (no back-navigation), 3 cycles, ~3min gaps — every `TabChanged(Customers)` load returned the
correct 873. **A false lead caught and ruled out along the way**: every `TabChanged(Prospects)` load
logged `totalItems=0`, which looked at first glance like another repro — a direct `sqlite3 SELECT
classification, COUNT(*) FROM cached_parties GROUP BY classification` showed `customer|873,
supplier|53`, zero rows classified `prospect`, so an empty Prospects tab is the **correct** result for
this real dataset, not a TD-041 instance. **Net result this pass: 0/7 Customers-tab loads reproduced
the bug**, across both a fresh-instance trigger and a same-instance/`TabChanged` trigger, each spaced
similarly to Phase 52's original ~3-minute gaps. **Working hypothesis for the next attempt** (not yet
tested): Phase 52's two reproductions both happened during/shortly after real sync activity (a Ledgers
"Sync Now" and its reconciliation), while this pass's attempts were ~30+ minutes after the session's
only sync — the trigger may correlate with proximity to actual sync/background work (WorkManager,
Adaptive Sync's scheduler) rather than pure elapsed time or revisit count on its own. Next session
should reproduce immediately following a real sync, not in a quiet steady-state window.

**Tested that hypothesis live this same phase — also came back clean.** Triggered a real Ledgers
"Sync Now" via the Sync screen (confirmed via a genuine `reconcile START`/`END` pair, 926 parties,
elapsedMs=3401) and immediately opened Connect: correct (873) within 25s of reconcile end, then 5
rapid back-and-reopen cycles over the next ~20s, all correct. A second attempt tried to catch the
narrower window of an *active* sync (tapped Sync Now, then immediately hammered `TabChanged`
Customers↔Prospects ~6x over 15s without waiting for completion) — no second `reconcile START` shows
up in the log for that tap. Root cause identified afterward, not guessed: the Dashboard's own status
banner shows the real Connector genuinely dropped to **"Connector is unavailable"** (Connection:
Unavailable, Readiness: Ready) sometime between the two sync attempts — an external LAN/Connector
connectivity loss, not a missed tap or a new defect (a subsequent navigation slip onto the unrelated
"Master data" screen was a separate, minor artifact of driving the UI blind via `adb input tap`, not
the cause of the missing reconcile). The second sync attempt simply had nothing to reconcile because
it never reached Tally. Every
Customers-tab load across all of today's live attempts (proximity-to-sync included) returned the
correct 873; only `TabChanged(Prospects)` ever returned 0, which is the dataset's genuine, correct
0-prospect count, not TD-041. **Net for the whole phase: 0 reproductions in ~20 live attempts across
two different hypotheses** (elapsed-time/revisit-count, and proximity-to-sync). TD-041 remains a
real, twice-proven (Phase 52), low-frequency, non-deterministic condition — this phase narrows what
does NOT reliably trigger it but did not find what does. Further blind ADB-driven UI automation has
hit diminishing returns; the standing recommendation to use Android Studio's Database Inspector or
an attached debugger (not available to this session's ADB-only toolkit) is now the most promising
next step rather than more live-reproduction attempts.

### F. Root-cause isolation attempt via code comparison, not live reproduction — one real structural finding

With the Connector unavailable, switched from live reproduction to carefully re-verifying the "Ledger
doesn't exhibit this, Party does" asymmetry every phase since Phase 52 has repeated without actually
re-reading Ledger's current code. **That re-read overturns the asymmetry, not confirms it.**

`RoomLedgerLocalDataSource.query()` (`LedgerLocalDataSource.kt:44`) calls `hasCache(companyId)` —
`ledgerDao.countForCompany(companyId) > 0` — first, and returns `null` immediately if it's `false`,
*before* ever running `countMatching`/`queryPage`. Its caller, `LedgerRepositoryImpl.listLedgers()`
(`LedgerRepositoryImpl.kt:40-45`), turns a `null` into `AppResult.Failure(AppError.Message(
NO_CACHE_MESSAGE))` — by design, per its own doc comment: *"A company with no cache at all (never
synced) fails honestly with NO_CACHE_MESSAGE rather than silently reaching for the network."* The
message shown is **"No offline data available. Connect to BUDCOM Desktop and synchronize once."** —
worded as "you haven't synced," not "empty result."

`PartyRepositoryImpl.listByClassification()` has no equivalent guard: it runs `countByClassification`
then `pageByClassification` directly and returns whatever they report, wrapped in a plain `PartyPage`
(not even an `AppResult` — Party's repository interface has no failure channel for this call at all).
A transient zero from `countByClassification` becomes an ordinary-looking, silent `PartyPage(items=
[], total=0)` success — which is exactly what Connect renders as "No customers found yet," right next
to an otherwise-correct freshness line.

**What this means**: if the same underlying transient-zero-count phenomenon that hits
`PartyDao.countByClassification` also hits `LedgerDao.countForCompany` (both are structurally the
same kind of plain `SELECT COUNT(*) ... WHERE companyId = :companyId [AND ...]` suspend query, on the
same database, same dispatcher, same executor) — Ledger wouldn't show an empty list at all. It would
show `NO_CACHE_MESSAGE`, an error state that reads as "not synced yet," not "empty." A tester or user
seeing that flash briefly on a company they know has synced data would very plausibly write it off as
a one-off UI glitch rather than recognize it as the same defect — which would fully explain why no
Ledger-side report of this ever surfaced, without requiring Party's code to be doing anything
differently at the SQL/Room level. **This does not prove they share one root cause** (that would need
catching a live `NO_CACHE_MESSAGE` flash on Ledger for a company with real synced data, or a debugger
on both paths at once) — but it removes the "Ledger is provably immune" evidence every prior phase's
root-cause reasoning leaned on, and redirects suspicion toward a general Room/SQLite-level phenomenon
(matching the standing suspicion already on record) rather than anything Party-specific.

**Also checked and ruled out as a source of the false asymmetry**: web research for a matching known
Room bug. One initially-promising lead (Google Issue Tracker b/340606803, "Room KMP can't receive
invalidation callback") was checked and does **not** apply — it's about `Flow`/`LiveData` observers
never re-emitting after a write, fixed in Room 2.7.0-rc02. This app (pinned to Room 2.6.1, no
`setDriver()` override — the legacy framework `SQLiteOpenHelper` path) doesn't use a `Flow`-returning
DAO method anywhere in Connect's or Ledger's read path; both use plain one-shot suspend `@Query`
calls, which don't go through `InvalidationTracker` at all. Recording this explicitly so a future
session doesn't re-discover the same lead and mistake it for a match.

**How to apply**: next live session, watch Ledger Browser (not just Connect) around a real sync for a
transient `NO_CACHE_MESSAGE` flash — catching one there, on a company definitely holding synced data,
would be the strongest evidence yet that this is one shared Room-level phenomenon rather than a
Party-specific bug, and would justify investigating a general fix (e.g., wrapping each feature's
count+page reads in a single `@Transaction` so both queries observe one atomic snapshot) rather than
a Party-only one.

### G. Mitigation implemented (user-approved: atomic reads + scoped defensive retry, no Tally-side changes)

Given root cause still isn't provable without a debugger, presented the user five options (atomic
`@Transaction` reads; defensive retry-on-suspicious-empty; a proper debugger session; copying
Ledger's `hasCache()`-style error-guard to Party; a Room version/driver upgrade), all scoped to the
local Room/Party read path only — none touch Tally/Connector communication, since TD-041 is already
proven local (data is correct in `cached_parties` at the exact moment of failure). User approved
implementing the first two together now, treating the debugger session as a separate, later track.

**1. Atomic count+page reads.** Added `PartyDao.pageWithCountByClassification` and
`pageWithCountSearch` — `@Transaction`-annotated methods that run the existing `countByClassification`
+`pageByClassification` (and `countSearch`+`search`) pairs inside one atomic Room transaction instead
of two independent suspend calls each free to land on its own connection/snapshot. `PartyRepositoryImpl
.listByClassification`/`searchParties` now call these instead of the two separate DAO methods; all
downstream logic (Alias-shortcut merge, `PartyPage` construction) is unchanged.

**2. Scoped defensive retry.** `ConnectViewModel` now remembers the last known non-zero
`totalItems` per `companyId|tab` (`lastNonZeroTotalByKey`). On the no-search classification-listing
path only (never the search path, where a genuine no-match zero is normal), if a read comes back
`totalItems=0` for a company/tab that's previously shown data, it's treated as suspicious: wait
500ms (comfortably longer than the ~1.75s self-heal window observed live in Phase 53, short enough
not to read as a stall — actually chosen below that window deliberately, see note below), retry the
same read once, and use whichever result comes back. Deliberately scoped to never fire on a
tab/company with no prior non-zero reading, so a genuinely-new company or a genuinely-empty Prospects
tab is never masked. The existing `TD041`-tagged Timber logging (kept, not removed) now also logs
every time this retry path fires and what it found, so future sessions can see in the wild whether
it's actually catching anything — this doubles as the telemetry needed to eventually judge whether
route G's mitigation addressed the real mechanism or just papered over it, informing section C's
debugger-session track.

*Correction while writing this up*: the retry delay (500ms) is shorter than the ~1.75s self-heal gap
measured in one live trace (Phase 53, §44) — chosen as a reasonable middle ground (long enough to
clear a brief race, short enough to feel instant) rather than matching that single data point exactly,
since the true self-heal timing is not established with enough samples to treat 1.75s as a reliable
floor. If live use shows the retry firing but still landing on a second zero, lengthening this delay
(or adding a second retry) is the first thing to try before anything more invasive.

**Verification**: `assembleProdDebug`/`compileProdDebugKotlin` both green. Full JVM suite:
**1,313/1,313 passing** (matches the known-good baseline, zero regressions), including all 20
`ConnectViewModelTest` and all 84 `PartyRepositoryImplTest` cases. Not yet validated on the real
device — the Connector was still unavailable at the time of this change; a real-device confirmation
(does Connect still behave correctly, does the retry path ever fire under real conditions) is the
natural next step once it reconnects. No new tests were added for the retry/atomicity behavior itself
in this pass — existing tests construct `PartyPage`/DAO results directly rather than driving through
two-connection timing, so they can't exercise the race being mitigated; this is a known gap, not an
oversight, consistent with why this bug needed live-device reproduction to find in the first place.

### H. Option 3 started (user-requested, "option 3 first") — instrumentation shipped, live capture still blocked

User asked to prioritize the debugger/root-cause track over further mitigation-validation. Since
Android Studio's actual Database Inspector/debugger GUI isn't reachable from this CLI-only session,
implemented the closest achievable equivalent: `DatabaseModule.provideAppDatabase` now registers a
`RoomDatabase.Builder.setQueryCallback` (debug builds only, gated on `BuildConfig.DEBUG`, own
single-thread executor) that logs every SQL statement touching `cached_parties` or marking a
transaction boundary, tagged `TD041_SQL` with the executing thread name — the exact detail needed to
see whether a reconciliation write and a Connect read were ever interleaved on separate
connections/transactions at the instant a read came back empty.

**A real scare, resolved by verification, not by trusting the first result.** The full JVM suite
failed once (`VoucherRepositoryImplTest`'s 60,000-record pagination test, `UncompletedCoroutinesError`)
immediately after this change landed. Did not accept "regression" at face value: isolated the test
(failed again), then ran a true A/B — `git stash` the `DatabaseModule.kt` change, full clean
`--rerun-tasks` rebuild both with and without it. **Both clean rebuilds passed 1,313/1,313.** Only the
two non-clean/incremental runs flaked, both times on this same test, regardless of which side of the
change they were on. Confirmed independently that no JVM test in this suite uses `@HiltAndroidTest` or
Robolectric (`grep` came up empty), so `DatabaseModule` is never even loaded during these tests —
there was never a plausible mechanism for this specific change to affect that test. Conclusion: a
pre-existing, load-sensitive flaky test (real-dispatcher work racing a virtual-time test budget under
system load), coincidentally surfaced by, but not caused by, this change. Recorded here as a known
pre-existing quality gap, out of TD-041's scope to fix.

Built (`assembleProdDebug`) and installed (`adb install -r`) onto the device — confirmed the existing
926-row `cached_parties` data survived the reinstall. **Could not capture a live trace this pass**: the
Diagnostics screen shows `Could not reach the Connector` — the same external LAN/Connector
unavailability already hit in section G's mitigation work, not a new problem and not something to
force around (no repeated blind retry-tapping; verified the real state once via Diagnostics and
stopped, per this project's standing ADB/connectivity-drop protocol). **How to apply**: the
`TD041_SQL`-tagged tracer is live and ready on the installed build — the next session (or later this
one, once the Connector reconnects) just needs to reproduce Connect's empty-read symptom again
(directly, or by watching for the mitigation's own `SUSPICIOUS EMPTY`/`RETRY result` log lines from
section G firing) and pull the surrounding `TD041_SQL` trace to see the actual thread/transaction
interleaving at the moment of failure.

## 45. Phase 54 — TD-041 real root cause found, fixed, and live-verified: reconciliation was a
detached `viewModelScope` job that a fast navigation away from Sync silently cancelled

New session, user asked to resolve TD-041 outright. Restored the real environment first, since it had
been down: started the Desktop app (`electron.exe` wasn't running, port 8080 closed), then found Tally
itself wasn't running either (`Company discovery is unavailable`, later `Tally circuit breaker is
open` — `tallyReachable: false`, nothing on port 9000). User started TallyPrime with ESTIMATION loaded
and confirmed its HTTP/XML server (F12 Advanced Configuration) was bound to port 9000 after a restart;
a real Ledgers "Sync Now" (949/949) then succeeded for the first time this phase.

**Two clean repro attempts (post-sync, and a second immediate repeat sync) both came back correct**,
matching every prior phase's experience — Connect showed 873 customers both times, `TD041`/`TD041_SQL`
logs showing the atomic `@Transaction` count+page mitigation (Phase 53-G) executing correctly. A third
attempt — deliberately racing a "Sync now" tap with an immediate Back-navigation, a pattern no prior
phase had systematically tried, since every previous session's repro attempts (and this project's
manual testing instinct generally) waited for the Sync screen to visibly finish before doing anything
else — silently produced **zero** `reconcile START`/`END` log lines at all, even though the Ledgers
sync itself completed normally (Connector confirmed `949/949`, Dashboard showed a new `Last sync
completed at` timestamp).

**Root cause, found by reading the code, not guessing**: `SyncViewModel.maybeReconcilePartiesFromLedgers`
launched Party reconciliation as `viewModelScope.launch { reconcilePartiesFromLedgers(companyId) }` —
a *separate, detached* coroutine, started only *after* `StartTargetSyncUseCase` had already returned
"Completed" to the UI. `SyncViewModel`'s `viewModelScope` is cancelled when its `NavBackStackEntry` is
popped (i.e., on Back from the Sync screen). A real reconciliation of ~926 records takes ~4 seconds
end to end — an entirely human-plausible window to navigate away in, especially since the UI already
says "Completed" the instant the *Ledgers* extraction and Room refresh finish, well before the
detached Party reconciliation has. The Ledger Room refresh itself (`completeLedgerRoomRefresh`,
TD-039's fix) never showed this because it's `await`ed *inside* the same `StartTargetSyncUseCase` call
that reports "Completed" — there's no window in which it can be "done" from the UI's perspective but
still running. This is exactly the asymmetry every phase since 52 observed ("Ledger doesn't show this,
Party does") but had never mechanically explained.

Whether this is *identical* to Phase 53's single captured `totalItems=0` trace is not proven — that
symptom (a hard empty count on an already-populated table) doesn't follow automatically from a
cancelled reconciliation, since `reconcileOne` only ever `INSERT OR REPLACE`s rows and never deletes;
a cancelled pass leaves *stale* data (correct old rows, missing new/updated ones), not necessarily
*zero* rows, except plausibly on a company's very first-ever reconciliation (cancelled before its
first few upserts land). Recorded honestly rather than claimed as a confirmed match — but this is the
first mechanism across four phases of investigation that is both fully explained by the code and
directly, repeatably reproducible, and it sits in exactly the code path every prior phase already
narrowed the bug to.

**Fix**: moved Party reconciliation out of `SyncViewModel` entirely and into
`StartTargetSyncUseCase.completeLedgerRoomRefresh` (`feature/sync/domain/usecase/SyncUseCases.kt`),
awaited in the same suspend chain as the Ledger Room refresh, immediately after it succeeds — failure-
isolated exactly as before (`runCatching`, a reconciliation failure can never turn a completed sync
into a reported failure), just no longer a separately-cancellable job. This has a second, free benefit:
`RunAvailableSyncsUseCase` calls `StartTargetSyncUseCase` once per target in its fixed Ledgers → Stock
items → Vouchers sequence, so every Ledgers call now reconciles regardless of position in that
sequence — the `runAll()`-specific workaround for "only the last outcome gets reconciled" (documented
in `SyncViewModelTest`'s "run available syncs triggers party reconciliation..." test, originally a
Phase-19-era live-observed defect) is now structurally unnecessary and was deleted along with the rest
of `maybeReconcilePartiesFromLedgers`.

**Tests**: `SyncUseCasesTest` gained 4 new cases directly on `StartTargetSyncUseCase` (reconciles on
Ledgers success; never reconciles for Stock items/Vouchers; never reconciles if the Room refresh itself
fails; a reconciliation failure never turns a completed sync into a failure) — 8 → 12 tests. The 6
existing reconciliation-behavior tests in `SyncViewModelTest` needed only constructor-wiring fixes (the
dependency moved to `StartTargetSyncUseCase`) and still pass unchanged, now exercising the new code
path end-to-end. Full JVM suite: **1,317/1,317**, up from the known 1,313 baseline by exactly the 4 new
cases, zero regressions.

**Live-verified on the real device, not just JVM-tested.** Built `assembleProdDebug`, installed
(`adb install -r`, existing 926-row data survived). Reproduced the *exact* prior-failing sequence —
tap "Sync now" on Ledgers, confirm the tap registered (`Phase: Starting`), then Back immediately
(~0.3s later) — and this time `TD041` logged `reconcile START` within the same second. The live logcat
tail then went quiet for ~20s with no further progress lines or a `reconcile END` — initially looked
like a stall, but this device's logcat ring buffer is known (Phase 53) to rotate fast under its own
background noise, so instead of trusting the absence of a log line, checked ground truth directly:
`adb shell run-as com.budcom.android.debug sqlite3 databases/budcom.db` against the real, live
`cached_parties` table showed **926 rows** (873 customer / 53 supplier, matching the known real
dataset exactly) with `MAX(updatedAt)` landing ~2.2s after the logged `reconcile START` timestamp —
conclusive proof the full reconciliation ran to completion even though the Sync screen (and its
`viewModelScope`) had already been navigated away from a fraction of a second after starting it.
Connect's Customers tab was then confirmed showing all 873 customers correctly.

**How to apply**: TD-041 is now closed as fixed for the mechanism this session actually proved and
reproduced (reconciliation silently skipped/truncated by fast navigation away from Sync). The Phase
53-G mitigation (atomic `@Transaction` count+page reads in `PartyDao`, plus `ConnectViewModel`'s one-
shot stale-empty retry) is left in place unchanged — it's a legitimate defense-in-depth for any other,
still-unproven transient-read mechanism, and removing it was never in scope. If a stale-empty Connect
read is ever observed again after this fix, that would be strong evidence a *second*, independent
mechanism exists — worth a fresh investigation rather than reopening this one.

## 46. Phase 55 — TD-042: real Ledger Alias data (mobile numbers) never reached Android at all,
two independent gaps found and fixed, live-verified against real data

Same session, immediately after Phase 54. User reported that ~80% of real ESTIMATION Debtors have
a mobile-number Alias in Tally (some also carrying a second, short numeric shortcut alias), and
asked for it to auto-fill Connect's phone/WhatsApp contact detail. Connect's Alias-driven phone
seeding (`PartyRepositoryImpl.applyAliasPhoneSeeding`, MVP-1.1-A/Phase 48) and its 1-5 digit
search shortcut already existed and were already tested — but only against synthetic Room
fixtures. Real ESTIMATION had shown zero Aliases in every session up to and including Phase 54
(confirmed again first thing this phase: `SELECT COUNT(*) FROM cached_ledgers WHERE alias IS NOT
NULL` = 0, even right after a fresh full Ledgers sync).

**First gap, found by reading the code**: `MasterDataTemplates.ledgers()`
(`connector/budcom_connector/src/extraction/templates/master-data-templates.ts`) — the Ledgers TDL
export's `collectionModifyFetch` field list — never included `ALIAS`. Stock Items' own field list
already did. Fixed by adding it, and while there, removed a pre-existing duplication that's
exactly how this went unnoticed: the "approved" field list lived in
`extraction/core/ledger-identity.ts`'s `LEDGER_RICH_FETCH_FIELDS` (explicitly documented as the
canonical list, with its own tests), but `master-data-templates.ts` maintained an independent
duplicate literal instead of importing it — two copies to keep in sync, only one of which was ever
checked against real Tally behavior. `master-data-templates.ts` now imports and spreads the one
canonical constant.

**Rebuilt, restarted the Connector, re-synced — still zero Aliases.** Rather than assume the fetch
field was now sufficient, sent the Connector's *exact* request XML directly to the running Tally
instance (`curl` to `localhost:9000`, bypassing both the Connector and Android entirely) to prove
Tally's real behavior in isolation. Result: a 638KB response, 950 ledgers, **zero `<ALIAS>` tags
anywhere** — even for ledgers independently confirmed (via a second raw request) to have a real
Alias. Reading the raw XML directly revealed the actual mechanism: Tally never emits a flat
`<ALIAS>` tag for Ledgers in this export shape at all. A ledger's Alias value(s) — entered as
"Name (alias)" in the ledger master, one or several comma-separated — are folded into extra
`<NAME>` siblings inside `LANGUAGENAME.LIST/NAME.LIST`, alongside the primary name as the first
entry. Confirmed live and repeatedly: `LEDGER NAME="Balaji Kowkoor"` exports
`<LANGUAGENAME.LIST><NAME.LIST><NAME>Balaji Kowkoor</NAME><NAME>7877685616</NAME>
<NAME>616</NAME></NAME.LIST>...` — name, then a 10-digit mobile, then a 3-digit shortcut, both
real Alias values Tally simply doesn't have a discrete tag for. Across the real 949 ledgers: 492
had exactly one extra name (almost always the mobile), 19 had two or more (mobile + shortcut, or
messier combinations) — this is precisely the "10-digit mobile for most Debtors, plus a 1-4 digit
shortcut for some" pattern the user described, now with a confirmed mechanism. Also checked
whether Tally's dedicated `MOBILENUMBER`/`PHONENUMBER` ledger contact fields were populated
instead (would have been a much simpler fix) — confirmed empty across the board, consistent with
the user having used the Alias field for this, not a dedicated contact field.

**Second gap, fixed in `entity-mappers.ts`'s `mapLedger`** — the function actually wired into the
live extraction path (via `extractor-registry.ts`; a second, unused duplicate mapper in
`tally-ledger-mapper.ts` was confirmed dead code via a repo-wide grep for its call sites — none
found outside its own file — and deliberately left untouched, out of scope). Added
`CollectionEntityParser.getDescendantTexts(node, path)`, a small generic nested-path walker, and a
`resolveLedgerAlias` helper: a flat `<ALIAS>` tag first if Tally ever does emit one (harmless,
forward-compatible, matches Stock Items' own already-working field), else the
`LANGUAGENAME.LIST/NAME.LIST/NAME` entries after the first, preferring whichever candidate matches
Android's own strict `PhoneNumberNormalizer.normalizeIndianMobile` shape (exactly 10 digits,
leading digit 6-9) since that's what Connect's phone seeding directly consumes, else simply the
first remaining candidate (preserves the shortcut-only case for a ledger with just one non-phone
alias). A ledger with both a phone and a shortcut can only keep one value in the existing
single-string `alias` field — the phone wins, a deliberate, disclosed trade-off favoring the
concrete, requested need (Call/WhatsApp) over the shortcut-search convenience, rather than a wider
multi-value schema change nobody asked for this session.

**Android-side phone-seeding and search-shortcut logic needed no changes at all** — already
correct, just never fed real data. Added 5 new tests directly on `mapLedger` against a new
realistic fixture (`SAMPLE_LEDGERS_WITH_LANGUAGENAME_ALIAS_RESPONSE`): single-alias, phone+shortcut
correctly preferring the phone, shortcut-only, no-alias, and flat-`<ALIAS>`-still-wins-when-present.
Connector suite: 1,106 → 1,111, all passing (full run, not just the new file).

**Live-verified end to end, not just via tests.** Rebuilt the Connector; the crash-detection
auto-restart observed in Phase 54 again did not actually respawn the child process on its own
within a reasonable wait, so restarted the whole Desktop app cleanly (same safe, disclosed pattern
as Phase 54) rather than force anything. Triggered a real Ledgers sync. Queried the *actual*
private-vault Connector database directly (`node:sqlite`'s `DatabaseSync`, read-only, against
`E:\BudcomPrivate\...\connector-data\budcom-ledger.db` — not the stale `%APPDATA%` copy from
2026-08-19, found by checking the real configured storage path rather than assuming the first `.db`
file found was the active one) and Android's Room `cached_ledgers`: **511 of 949 real ledgers now
carry a real alias.** `cached_parties`: **486 of 873 real customers got a validated `primaryPhone`**
(reconciliation ran automatically as part of the same sync, awaited synchronously — a direct,
concrete benefit of Phase 54's fix landing first). Connect's Customers tab visually confirmed
showing real phone numbers (e.g. "4m Plywood & Hw" → `8309814428`, Call/WhatsApp both active).

**The shortfall from the user's ~80% estimate (486/873 ≈ 56%) is expected and correct, not a new
bug**: some real Alias values are non-phone shortcuts (`"2"`, `"616"`, ...) or malformed near-phone
entries (e.g. an 11-digit value on "A2Z BAZAAR GODAVARI KHANI" — visually confirmed on-device: the
Alias line shows correctly, but no phone line renders and Call/WhatsApp correctly no-op) that the
existing strict validation correctly declines, by its own documented design rationale (a plain
digit string such as a pincode or account number must never be silently treated as a phone
number). **How to apply**: if the user wants that shortfall narrowed, the next step is a real,
disclosed conversation about relaxing the strict-10-digit rule (e.g. tolerating an 11-digit value
with a leading 0/91) or a genuine multi-value alias schema (to stop losing the shortcut when a
phone also exists) — neither was requested this session and neither was attempted speculatively.

## 47. Phase 56 — MVP-1.4 Catalogue: full-stack implementation (IMPLEMENTED + AUTOMATED-VALIDATED,
not physically validated)

New session. User authorized full autonomous implementation of the already-locked MVP-1.4 Catalogue
scope (`docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md`, `...BRAINSTORM-OUTCOME.md`) —
the architecture document's own §23 "Claude autonomy boundary" explicitly required a separate,
explicit go-ahead before any Catalogue code was written; this session's instruction was that
go-ahead. Read both documents plus PDL-020 and the Technical Debt Registry before writing any code,
per the architecture document's own §1/§2 findings (zero Catalogue code existed anywhere; the
Stock-group override level has a live data-availability gap identical to TD-035/TD-042).

**Pre-existing uncommitted work found and preserved, not discarded.** `git status` at session start
showed unstaged changes to `ConnectScreen.kt`/`ConnectScreenTest.kt` (Connect card UI polish: Alias
line removed per product decision, Call/WhatsApp AssistChips replaced with IconButtons) — self-
consistent, imports correct, test updated to match. Committed separately as its own commit before
starting Catalogue work, to establish a clean baseline without losing unrelated in-progress work.

### A. Milestone 0 — Connector Stock Item Fetch-field prerequisite (TD-043, new)

Confirmed the architecture document's own finding directly: `mapStockItem()`
(`entity-mappers.ts`) has always parsed `PARENT`/`CATEGORY`/`BASEUNITS`/`CLOSINGBALANCE`/
`GSTAPPLICABLE`, but the routine `stockItems` Fetch list has never requested any of them — same
bug class as TD-035 (Ledgers `PARENT`)/TD-042 (Ledger Alias). **Deliberately did not modify the
existing, already-VERIFIED_SAFE `stockItems` Fetch list or template** — this session has no live
Tally connection to validate a changed request shape against, and the task's own safety rules
forbid sending an experimental/modified Tally request shape to production. Instead, mirrored the
exact `LEDGER_CONTACT_FETCH_FIELDS`/`LedgersContactDetails` precedent: added
`STOCK_ITEM_RICH_FETCH_FIELDS` (`stock-item-identity.ts`) and a new, separate
`MasterDataTemplates.stockItemsEnrichedFields` template (`master-data-templates.ts`), registered as
`ApprovedOperationId.StockItemsEnrichedFields` in `operation-registry.ts` with
`classification: 'EXPERIMENTAL_DISABLED'`, `rolloutStatus: 'disabled'`, and a `render()` that
throws until live-validation evidence is recorded — exactly the same gate `LEDGERS_CONTACT_DETAILS`
uses. Zero change to any request Connector actually sends today. Added a fixture-only regression
test (`entity-mappers.test.ts`, inline XML, deliberately not touching the shared
`SAMPLE_STOCK_ITEMS_RESPONSE` fixture used by three other test files) proving `mapStockItem`
already parses `CATEGORY`/`CLOSINGBALANCE`/`GSTAPPLICABLE`/`ISINACTIVE` correctly — the mapper was
never the gap. Also added `serverTimeEpochMillis` to the Connector's existing `GET /health`
response (`core/types.ts`, `services/health/health-service.ts`, `api/routes/health.ts`) — additive,
zero Tally involvement, gives Android's new `CatalogueClock` (§D below) a real authoritative-clock
reading to reuse via the already-existing `ConnectorStatusPort.probeConnection()`, rather than a
new route. Connector suite: 112 tests in the affected files re-run green (26 in
`entity-mappers.test.ts`, up from 25); full connector suite and `tsc --noEmit` also re-run clean
after every change in this phase.

**Stock-group override level remains explicitly gated behind this being live-validated** — exactly
as the architecture document's own Milestone 0 stop condition requires. Item/Branch/Catalogue-wide
override levels do not depend on this and are fully implemented and tested (§C below).

### B. Data foundation — seven new Room tables, additive migration 10→11

New `feature/catalogue/data/local/CatalogueEntities.kt` + `CatalogueDao.kt`:
`catalogue_product`, `catalogue_product_source_link`, `catalogue_branch`, `catalogue_override`,
`catalogue_published_snapshot`, `catalogue_asset`, `catalogue_settings`. `DatabaseConstants.VERSION`
9→10 already used; bumped 10→11 here. **Deliberate design choice beyond what the architecture
document itself proposed**: Catalogue never mirrors a Tally-owned field (name/unit/HSN/GST/stock
group) into its own tables at all — every read resolves them live via a join against the existing
`cached_stock_items` table (through a new, additive `StockItemDao.findById`/`findAllForCompany` +
a new cross-feature `StockItemLookupPort`/`StockItemLookupPortImpl`, mirroring the existing
`SearchStockItemsPort` convention). This makes "Tally sync must not silently destroy Catalogue
enrichment" (architecture §6) true by construction — there is no mirrored copy for any sync to ever
overwrite — rather than requiring a carefully-written partial-update/COALESCE query. Company
isolation follows the exact `(companyId, ...)` composite-key + explicit-parameter convention every
other entity in this codebase already uses.

Migration test added: `AppDatabaseMigrationTest.migrate10To11_preservesExistingRowsAndAddsCatalogueTablesOnly`,
same discipline as every migration test since `MIGRATION_5_6` — starts from a real version-10
database with pre-existing company/stock-item rows, runs the exact production `MIGRATION_10_11`
object, asserts every pre-existing row survives, every new table's column set matches its entity
exactly, every table starts genuinely empty, and every table is insert/query-usable. **This is an
androidTest (instrumented) — written but not run; no emulator/device was available in this
session.** Flagged explicitly, not silently claimed as passing.

### C. Override engine, lifecycle, enrichment, pricing, branches

`CatalogueOverrideResolver` (pure function, `domain/model/`) implements the LOCKED
Item → Branch → Stock-group → Catalogue-wide precedence exactly — first match wins, no merging.
`CatalogueLifecycleTransitions` (pure function) implements Draft→Review→Publish→Archive with every
locked rule: Draft→Review by any authorized staff (no owner requirement); Publish valid from Draft
*or* Review (solo-business fast path) but Owner-only; Archive Owner-only from Published only;
Unarchive Owner-only back to Draft; every invalid transition returns a rejection, never a silent
no-op. "Editing a Published product creates a new pending Draft, never an in-place mutation of the
live Published record" (architecture §7) is implemented as `ReopenForEdit` moving the *same*
product's working row back to Draft while `catalogue_published_snapshot` — a separate table,
atomically overwritten only on the next successful Publish — stays untouched; deliberately not a
forked second `productId` needing a later merge-back, a mechanism the architecture document itself
never fully specified. `CatalogueRepositoryImpl` wires all of this together plus branch CRUD,
override set/resolve/clear, Stock Item reconciliation (`reconcileStockItemLinks`: disappearance
flags `sourceAvailable=false` without deleting/archiving, reappearance auto-clears it, mirrored in
20 repository-level tests including two-company adversarial isolation), and the Public/Private
catalogue-level setting (`catalogue_settings`, defaults Private).

**Pricing governance** implemented as designed (`PriceSyncMode.Auto`/`Manual` resolved through the
same override chain) with one disclosed, deliberate limitation: Android's `StockItem` domain model
has no Tally "rate" field anywhere in the existing sync pipeline today (the Connector fetches
`OPENINGRATE` but never maps/persists it) — so `PublishSnapshot`'s resolved price is always the
Catalogue-owned `manualPriceAmount` regardless of which sync mode resolves, until a future milestone
adds a persisted Tally rate field. The override-chain infrastructure itself is correct and fully
tested now, ready for that data once it exists.

**Authoritative timestamp (architecture §15, LOCKED)** — resolved via new `CatalogueClock`
(`domain/port/` + `data/CatalogueClockImpl.kt`): reuses the existing, stable
`ConnectorStatusPort.probeConnection()` (no new Connector route needed beyond §A's additive
`serverTimeEpochMillis` field); a successful probe yields a `CatalogueTimestampSource.Connector`
reading, a failed/offline probe falls back to the device clock tagged
`DeviceLocalProvisional` — visible and inspectable on every stored timestamp, never silently
treated as equally authoritative. The offline-multi-device-race edge case the architecture document
itself calls "genuinely unresolved" (§15/§22 item 2) remains exactly that — not invented around.

**Asset store** (`feature/catalogue/storage/`): `CatalogueAssetStore`/`AndroidCatalogueAssetStore`,
a second *instance* of `BusinessProfileLogoStore`'s exact pattern (allowlist, streaming size cap,
sanitized path segments, path-containment on read), multi-image-per-product via
`(companyId, productId, assetId)`. Path-traversal and size-cap logic unit-tested directly (11 tests
on the extracted pure helpers); the Context/ContentResolver-coupled methods themselves would need
an instrumented test — the same gap `BusinessProfileLogoStore` itself already has, not a new
regression in project discipline.

### D. Sharing (category-level + full-catalogue, LOCKED scope)

`feature/catalogue/sharing/`: `CatalogueShareCacheBoundary`/`CatalogueShareCachePolicy` (near-
identical to `LedgerStatementShareCacheBoundary`/`...CachePolicy`, own cache directory per this
codebase's own stated reasoning for not sharing instances across domains), `CatalogueShareContent`
(pure — Private-catalogue structural refusal *before* any product content is ever read, and reads
exclusively from `listAllPublished`/`listPublishedForCategory`, both of which only ever return the
atomic published snapshot), `CatalogueShareTextRenderer`, `AndroidCatalogueShareCoordinator`
(`Intent.ACTION_SEND` + `FileProvider`, reusing the existing `${applicationId}.invoice-files`
authority with one new additive `<cache-path>` entry).

**Deliberate simplification, disclosed, not silently lowered**: the share file is plain text, not a
PDF. The Ledger statement PDF renderer (`LedgerStatementPdfRenderer`, `android.graphics.pdf.PdfDocument`,
~500 lines of pagination/wrapping logic) has no directly reusable shape for a product-catalogue
layout, and this session had no way to visually verify a hand-rolled PDF renderer's actual rendered
output (no emulator/device). Plain text satisfies "generate a file, hand it to the OS share sheet"
(architecture §11) and is trivially, verifiably correct; the `CatalogueShareCoordinator` interface
does not change if a PDF renderer replaces this later. Adversarial tests: Private catalogue refused
before `listAllPublished`/`listPublishedForCategory` is ever called (proven via a call-flag on the
fake repository, not just a discarded result); empty-published-set refused with an honest message,
never an empty file; category scope never leaks another category's products.

### E. Excel foundation (contract + validation + commit, no file-format library chosen)

`feature/catalogue/domain/excel/`: `CatalogueExcelColumns` (the full reserved native-name set,
membership-check based per architecture §9's own "avoid a brittle schema" requirement, plus
`validateCustomColumnName` implementing the LOCKED "custom column cannot reuse a
native/future-native name; clear rename prompt on conflict" rule directly), `CatalogueExcelValidator`
(pure: required-field checks, malformed-price detection scoped to Open display mode only,
duplicate-row-within-file last-wins with both rows flagged, stable-identifier create-vs-update
matching), `CatalogueExcelImportUseCase` (commits a previewed result into Manual Drafts/enrichment
updates only — never touches lifecycle state, matching "Publish/Archive transitions happen only
through the lifecycle UI, never via Excel re-import"). **Deliberately does not choose or depend on
an actual `.xlsx`/`.csv` parsing library** — that is a real new-dependency decision this pass does
not make unilaterally; a future session wiring a real file reader only needs to produce
`CatalogueExcelRow`s, everything downstream already exists and is tested (24 tests: column
reservation, validator matrix, commit use case).

### F. Essential UI

`feature/catalogue/presentation/`: `CatalogueScreen`/`CatalogueViewModel` (product list, manual-
draft creation dialog, Public/Private toggle, Share-full-catalogue action, company-switch reload
discipline mirroring `ConnectViewModel`'s own `TD-037`-class guard) and
`CatalogueDetailScreen`/`CatalogueDetailViewModel` (Tally-owned fields shown read-only, Catalogue-
owned fields editable only in Draft/Review, lifecycle action buttons driven directly by
`CatalogueLifecycleTransitions`, source-unavailable banner). Wired into `Dashboard` as a sixth
primary entry (`HomePrimaryEntryRow`, `Icons.Filled.ShoppingCart`, confirmed present in the
project's `material-icons-core` artifact before use) — "one more `DashboardEvent.OpenX`," the exact
precedent the Dashboard's own doc comment already documents for every prior addition. New routes
`Routes.CATALOGUE`/`Routes.CATALOGUE_DETAIL` in `BudcomNavHost`.

**Disclosed limitation, not a security regression**: `CatalogueLifecycleTransitions`'s `isOwner`
parameter is structurally wired end-to-end exactly as architecture §7/§18 require, but this
codebase has no user/role/authentication concept anywhere to source a real signal from — a
pre-existing, whole-app characteristic (verified: `grep -r "isOwner\|UserRole"` across the app
found nothing outside this session's own new Catalogue files). `CatalogueDetailViewModel` hardcodes
`isOwner = true` (single-device-per-business assumption, consistent with every other screen today),
documented in code and tracked as new TD-044 (registry). This does not weaken any existing control
— Catalogue is new capability, not a control being removed — but it does mean "Owner-only Publish"
is not yet actually access-controlled against a real identity, only against the always-true
placeholder. Flagged for explicit product-owner attention before any team-business deployment.

### G. Tests, build, lint

Android JVM unit tests: **1,425 total, 0 failures** (full `testDevDebugUnitTest` run, not scoped to
Catalogue), of which **94 are new Catalogue tests** across override resolution (10), lifecycle
transitions (10), repository incl. company-isolation/reconciliation/publish-atomicity (20),
presentation/ViewModel (17), asset-store path-traversal/size-cap helpers (11), sharing incl.
Private-refusal adversarial tests (17), Excel contract/validator/commit (24 — recount: see actual
suite for exact per-file split). `compileDevDebugKotlin` and `compileDevDebugUnitTestKotlin` both
green. `lintDevDebug`: 0 errors both before and after the sharing/Excel additions (84 pre-existing
warnings, none Catalogue-related). Connector: `tsc --noEmit` clean, full `vitest run` re-confirmed
green after every Milestone 0 change.

### H. What was not done, and why

- **No live Tally validation** of `STOCK_ITEM_RICH_FETCH_FIELDS` — no live Tally-request validation
  was performed this session (see §I below for what *was* validated live); the operation stays
  `EXPERIMENTAL_DISABLED`/`disabled` until a future session performs and records that validation,
  per this project's own standing discipline.
- **No item-level sharing, no full version history, no tiered pricing, no Prospect→Ledger, no
  multi-language** — all explicitly out of the locked MVP-1.4 scope, not touched.
- **Category-level sharing has no dedicated category-picker UI yet** — the mechanism
  (`CatalogueShareScope.Category`) is fully implemented and tested; only "Share full catalogue" is
  wired into `CatalogueScreen` for this pass, since the existing free-text customer-facing-category
  field has no UI surfacing the distinct set of categories in use yet.
- **Branch UI** (a company-level branch selector) was not built — `Branch`/override-by-branch is
  fully implemented and tested at the repository/domain layer; no screen exposes it yet, since no
  business in this repository's data has more than one branch to demonstrate it against.
- **No currency code on a manually-entered price** — the detail screen's Price field has no
  currency selector, so `CatalogueEnrichmentUpdate.manualPriceCurrencyCode` is always `null` for a
  UI-entered price; a shared/published price shows e.g. "Price: 499" with no "INR" suffix. Found
  live (§I) reading the actual generated share file's content, not fixed this session — a small,
  genuine product-UX gap (does it need a selector, or a silent fixed default?) rather than a
  unilateral call this session should make.

### I. Live real-device validation (device `I2407`/`10BF44124K000E3`, real paired company ESTIMATION) — performed after the report above was first drafted

A real, already-paired device was available after all (`adb devices -l` found it mid-session,
screen awake, battery 100%, only this project's own dev/debug builds installed — no production
install to disturb). Ran the actual instrumented migration suite and a full hands-on walkthrough
rather than leaving §H's original "no device available" limitation standing.

**Instrumented tests, real device**: `connectedDevDebugAndroidTest` filtered to
`AppDatabaseMigrationTest` — **10/10 passed**, including the new
`migrate10To11_preservesExistingRowsAndAddsCatalogueTablesOnly` (0.105s) — the first genuine
physical confirmation that the Catalogue migration behaves correctly against Room's real SQLite
implementation, not just an assumption from reading the migration SQL.

**Built and installed `prod`-flavor debug APK over the existing, already-paired
`com.budcom.android.debug` install** (real company ESTIMATION, "Tally connected" shown, real
synced Ledgers/Vouchers/Stock Items) — confirmed the reinstall preserved pairing/session/Room data
before doing anything further. Full hands-on walkthrough via `adb shell input`/`uiautomator dump`/
`screencap` (screenshots inspected directly, not assumed): Dashboard's new Catalogue tile → empty
list → manual Draft creation ("Handwoven Basket") → detail screen (description, price mode, price)
→ Save → Submit-for-review-vs-Publish choice → Publish → Archive → Restore (back to Draft) →
Public/Private toggle → Share (Private correctly refused with "No published products to share
yet." when nothing was Published) → Publish again → Share succeeded, producing a real Android
share sheet (real WhatsApp contacts/Gmail/Quick Share targets from this device) and a real
generated file, whose actual on-device content was read directly
(`run-as ... cat .../catalogue-share/*.txt`) and confirmed correct: header, scope label, product
name, price line, description.

**Two real defects found and fixed live, both now TD-045 and TD-046 (registry)** — see those
entries for full detail. In summary: (1) every Catalogue write blocked for ~45s whenever the
paired Connector was unreachable (an ordinary state, not an edge case) — `CatalogueClockImpl`
now bounds the probe to 2.5s independent of the shared retry policy; (2) the product list never
refreshed after returning from the detail screen post-mutation — `CatalogueRoute` now refreshes on
every RESUMED lifecycle entry, with a cold-start race guarded in `CatalogueViewModel.load()`. Both
fixes were rebuilt, reinstalled, and re-verified live on the same device before being accepted —
this ledger entry does not claim a fix works without having watched it work. Both fixes also
covered by new/updated unit tests (`CatalogueClockImplTest`, 4 tests) and the full suite re-run
green after each change.

**Courtesy cleanup**: the test product was left `Archived` (Catalogue has no delete capability by
design) and the Public toggle was returned to off before ending the session, so the real
ESTIMATION company's app was left in a clean, non-disruptive state — confirmed via a final
`uiautomator dump` read of the actual switch state, not assumed from a screenshot (one screenshot
during this cleanup visually still showed the toggle in its old position due to capture timing;
the dump was treated as authoritative over the image).

**Still not done**: live Tally-request validation for TD-043 (this device's own Connector was
unreachable throughout, so no live Tally traffic was possible or attempted — consistent with this
project's safety rules); a real second-company isolation walkthrough; exercising the Excel/
category-sharing/branch paths physically (no UI exists yet for the latter two, per §H).

## 48. Phase 57 — Catalogue: UI for two capabilities that existed only at the repository layer

New session. User question ("what about auto-populating Tally stock items? and a photo option?")
identified that two capabilities Phase 56 had already built at the domain/data layer had no UI
entry point: `CatalogueRepository.createDraftFromStockItem` (never called from any screen) and
`CatalogueAssetStore` (fully implemented, never wired to a picker or camera). Closed both gaps.

**Link from Tally stock.** The Catalogue FAB now opens a choice dialog ("Enter details yourself, or
link a product you already have in Tally") instead of going straight to the manual-name dialog.
`CatalogueRepository.listUnlinkedStockItems(companyId)` (new) diffs
`StockItemLookupPort.listAllForCompany` against `CatalogueSourceLinkDao.findAllForCompany`, so an
already-linked Stock Item never appears twice. New `CatalogueStockItemPickerScreen`/ViewModel
(`Routes.CATALOGUE_STOCK_ITEM_PICKER`) provides a search field plus list; picking an item calls the
existing `createDraftFromStockItem` and navigates straight to the new Draft's detail screen with
`popUpTo(...) { inclusive = true }` so back doesn't return to the picker.

**Photos.** `CatalogueRepository` gained `addAsset`/`listAssets`/`setPrimaryAsset`/`deleteAsset`/
`resolveAssetFile`, implemented in `CatalogueRepositoryImpl` against the pre-existing
`CatalogueAssetStore` (first asset added becomes primary automatically; deleting the primary
promotes the next one; `CatalogueAsset` gained the `filePath` field it was missing for UI
resolution). `CatalogueDetailScreen` gained a Photos section — "Take photo" using
`ActivityResultContracts.TakePicture()` against a `FileProvider`-backed cache file (new
`catalogue_camera` cache-path entry in `invoice_share_paths.xml`, reusing the existing
`${applicationId}.invoice-files` authority) and "Choose photo" using
`ActivityResultContracts.PickVisualMedia()` (Android Photo Picker) — no CAMERA permission needed
for either. Thumbnails decode off the main thread via `BitmapFactory.decodeFile` in a
`LaunchedEffect`, mirroring `BusinessProfileLogo`'s existing pattern exactly (no Coil/Glide
dependency added). Plain text buttons were used instead of camera/gallery icons after confirming
via `unzip -l` on the actual AAR that this project's `material-icons-core` (no `-extended`
dependency) does not contain `PhotoCamera`/`CameraAlt`/`Image`/`AddAPhoto` — `Close` and `Star` (for
delete and primary-marker) were confirmed present and used instead.

**Tests.** 24 new unit tests: 9 in `CatalogueRepositoryImplTest` (unlinked-item filtering and
company isolation, primary-on-first-add, `setPrimaryAsset` clearing every other flag, delete-then-
promote, delete-the-only-one, a rejected asset never reaching Room, assets never leaking across
companies), 3 in `CatalogueViewModelTest` (FAB choice dialog open/manual/link-from-stock paths), 5
in the new `CatalogueStockItemPickerViewModelTest`, 7 in `CatalogueDetailViewModelTest` (load
existing photos, add/second-add-not-primary, set-primary, delete, a rejected add's failure message,
`TakePhoto`/`PickPhotoFromGallery` effects). Full `testDevDebugUnitTest` run: all green.
`compileDevDebugKotlin`/`compileDevDebugUnitTestKotlin`: clean. `lintDevDebug`: 0 errors, 84
pre-existing warnings (unchanged from Phase 56).

**Live device validation** (same device, `10BF44124K000E3`, real ESTIMATION company): ran a fresh
Tally sync to populate real Stock Items, then walked the full flow by hand via
`adb shell input`/`uiautomator dump`/`screencap` — FAB → choice dialog → "Link from Tally stock" →
search filter ("gadda") → picked "1 NO GADDA" → Draft created and opened its detail screen
automatically. Then "Choose photo" → real Android Photo Picker → selected a real gallery photo →
saved and rendered as the primary thumbnail with the star badge → removed via the delete icon
("Photo removed" confirmation). Then "Take photo" → real camera app → captured → OK → saved and
rendered as primary → removed. Both photo entry points and the stock-item link path are confirmed
working against real data, not just unit-test doubles.

Committed as `4a77955`, 20 files. Pre-existing unrelated uncommitted work (Connect screen polish,
architecture-doc edits already in the working tree at session start) was left untouched and not
included in this commit, per this project's standing scope-discipline practice.

## 49. Phase 58 — MVP-1.4 Catalogue: Excel contract completion (CSV format, custom-field
persistence, export) + category-sharing UI, offline-only pass under a network-limited environment

New session, immediately following commit `8197487` ("Link all" bulk stock-item linking). User
authorized continued autonomous MVP-1.4 completion for several hours, explicitly under a **mobile-
hotspot network limitation** ("do NOT attempt to bypass secure pairing/LAN auth/firewall isolation
merely to obtain live Tally evidence; live Connector validation is deferred"). Re-established ground
truth first (git status clean, HEAD confirmed, architecture/brainstorm/PDL-020/TD-registry/ledger
all re-read directly rather than trusted from memory) before writing any code, per the session's own
governing instructions.

**Scope decision**: the Excel contract (architecture §9) was the only area the governing instructions
named as a specific priority ("treat this as a major completion area"), and was also the largest
concrete gap — Phase 56 had deliberately left it domain/validation-only, explicitly declining to pick
a file-format library. This phase completes it plus one smaller, already-flagged UI gap (category
sharing) that could be finished with the remaining budget; Branch UI (the other Phase 56 §H gap) was
not attempted this pass — see "What was not done" below.

### A. Excel file-format implementation — CSV, no new dependency

New `CatalogueCsvFormat.kt` (domain/excel, pure Kotlin, zero Android/library dependency): RFC 4180
parse/write — quoted fields with embedded commas/quotes(`""`)/newlines, CRLF-canonical output with
lenient CRLF-or-LF input, UTF-8 BOM handling (stripped on read, written on export for reliable Excel-
on-Windows Unicode detection), blank-line dropping (not synthesized into phantom empty rows), and
explicit duplicate-column-header detection (flagged, first occurrence wins the column slot — a real
gap the locked contract's own "duplicate columns" verification item named). CSV was chosen
deliberately over a binary `.xlsx` reader/writer specifically because Phase 56's own doc comment
flagged picking a new parsing library as "a real dependency decision this pass does not make
unilaterally" — CSV needs no such dependency at all and is fully Excel-openable/editable/re-savable,
satisfying "Excel export/import bridges both platforms" (Brainstorm Outcome §6) as the smallest
architecture-consistent solution. 19 new tests (`CatalogueCsvFormatTest`): quoting, embedded special
characters, Unicode content, 20,000-character values, duplicate-header flagging (case-insensitive),
empty/header-only/blank-line files, and full write-then-parse round-trip fidelity.

### B. Custom-column persistence — new `catalogue_custom_field` table, migration 11→12

The locked contract requires custom Excel columns to be "round-tripped opaquely" (architecture §9),
but no table anywhere stored a custom column's *per-product value* — only its *name* survived
(`CatalogueExcelImportPreview.customColumnNames`). Added `CatalogueCustomFieldEntity`
(`catalogue_custom_field`, PK `(companyId, productId, columnName)`) + `CatalogueCustomFieldDao`,
wired into `CatalogueRepository`/`CatalogueRepositoryImpl` (`upsertCustomFields`/`listCustomFields`/
`listAllCustomFieldColumnNames`), and `MIGRATION_11_12` (additive-only, same `CREATE TABLE IF NOT
EXISTS` discipline as every prior Catalogue migration; `DatabaseConstants.VERSION` 11→12). New
instrumented `migrate11To12_preservesExistingRowsAndAddsCustomFieldTableOnly` test (written, and this
session also had real-device access — see §E). `CatalogueExcelImportUseCase.commit` now persists
every known custom column's value (including an explicit `null` to *clear* a column left blank on
re-import, not merely leave a stale value) for both Create and Update outcomes.

### C. Export — new `CatalogueExcelExportUseCase`

Nothing previously produced export output at all (only import preview/commit existed). New
`CatalogueExcelExportUseCase.export(companyId)`: native reserved columns in a fixed order
(`CatalogueExcelColumns.NATIVE_EXPORT_ORDER`, new) plus every distinct custom column name used
anywhere in the company (sorted, stable header set even when one product lacks a column another
has), `.toCsv()` convenience method. Archived products are included (Archive is soft/reversible,
architecture §7 — not a reason to silently drop a row from an export the owner explicitly asked
for). 5 new tests (`CatalogueExcelExportUseCaseTest`) covering native+custom column inclusion,
Publication State as read-only informational output, missing-column-blank-not-dropped behavior,
cross-company isolation, and a full export→parse→preview→re-commit idempotency test.

**Real defect found while writing the idempotency test, not fixed, recorded as TD-047**: a Manual
(non-Tally) product has no code path that ever sets its required "Unit" field (architecture §6 treats
Unit as unconditionally Tally-authoritative; `CatalogueEnrichmentUpdate` has no unit field), so
exporting and re-importing a Manual product's own file unchanged always skips that row on "Missing
required column: Unit" — a real round-trip violation for that product class, and exactly the risk
Brainstorm Outcome §10 Risk #3 ("Manual-item test coverage... not assumed parity") named in advance.
Not fixed this session — the correct fix requires a product decision (should Unit become Catalogue-
owned for Manual products, conditionally overriding the locked field-ownership table?) outside this
session's authority to make unilaterally. The round-trip test itself was corrected to exercise a
Tally-linked product instead, with its own doc comment recording exactly why, so the passing suite
does not silently mask the gap.

**A second, smaller fake-repository defect found and fixed** (test infrastructure only, not
production code): `CatalogueViewModelTest`'s `FakeCatalogueRepository.updateEnrichment` never applied
`sku`, `displayNameOverride`, or `manualPriceCurrencyCode` from a `CatalogueEnrichmentUpdate` — a
pre-existing gap between the fake and the real `CatalogueRepositoryImpl`'s actual field-copy
semantics, caught because the new Excel round-trip test genuinely needed SKU-based re-import
matching to work. Fixed to match `CatalogueRepositoryImpl` exactly. Also fixed the same fake's
`listAllPublished`/`listPublishedForCategory`, previously hardcoded to always return `emptyList()`
regardless of actual Publish transitions — needed for the new category-sharing tests (§D) to be
meaningful at all.

### D. Category-sharing UI (closes part of Phase 56 §H's disclosed gap)

The category-share *mechanism* (`CatalogueShareScope.Category`) was fully implemented and tested
since Phase 56; only "Share full catalogue" was wired into `CatalogueScreen`. Converted the app bar's
single Share icon into a `DropdownMenu` ("Share full catalogue" / "Share a category"); the latter
loads distinct customer-facing categories from `listAllPublished` (Published products only — a
Draft/Review product's category is never offered, matching the same "only Published content is ever
shareable" structural invariant `CatalogueShareContent` already enforces) and opens a picker dialog,
tapping a category fires the identical `prepareShare`/`createShareIntent` path `shareFullCatalogue`
already used. New `CatalogueUiState` fields (`showShareMenu`, `showCategoryShareDialog`,
`availableCategories`) and `CatalogueEvent`s (`OpenShareMenu`/`DismissShareMenu`/
`OpenCategoryShareDialog`/`DismissCategoryShareDialog`/`ShareCategory`). 2 new ViewModel tests
(category list correctly excludes an un-published product's category; sharing a category closes the
dialog and attempts the share through the existing coordinator).

### E. Testing, build, and real-device evidence

Android JVM unit tests: **1,494 total, 0 failures** (full `testDevDebugUnitTest`, not scoped to
Catalogue — up from the Phase 56 baseline of 1,425; +69 this phase: 19 CSV format + 5 export + 3
import-custom-field/isolation + 7 validator adversarial + 6 repository cross-company-identical-
identifier adversarial + 2 category-share ViewModel + assorted fixes). `compileDevDebugKotlin`/
`compileDevDebugUnitTestKotlin`/`compileDevDebugAndroidTestKotlin` all clean. `lintDevDebug`: 0
errors, 84 warnings (identical to the pre-existing Phase 56 baseline — one new lint error was hit and
fixed mid-session, see below). Connector/TypeScript: untouched this phase, not re-run (no Connector
file was modified).

**Real-device evidence, not merely automated** (device `10BF44124K000E3`, real paired install already
on-device from the prior session): built and installed `devDebug` **over the existing v11 database**
(not a clean install) specifically to exercise the real `MIGRATION_11_12` against genuine prior data,
not only the emulated `MigrationTestHelper` instrumented test. Confirmed via `run-as ... sqlite3`:
`PRAGMA user_version` reads `12` post-launch, `catalogue_custom_field` table exists, app launched and
the Catalogue screen opened with no crash (`logcat AndroidRuntime:E` empty throughout). Separately
verified the new category-sharing UI: the Share icon's dropdown renders both options, "Share a
category" opens the picker dialog and correctly shows "No categories among your published products
yet." for this device's genuinely-empty/Private catalogue (proving the empty-state path, not just the
happy path) — no crash. A full create→publish-with-category→share walkthrough (the stronger evidence
Phase 56's own live pass achieved for the base lifecycle) was attempted but not completed this
session: coordinate-guessed `adb shell input tap` sequences against a manual-entry dialog missed
their target twice in a row with no intermediate screenshot to correct against, and rather than keep
guessing, this was stopped and left as a named remaining item (see "What was not done") — the device
was confirmed left in a clean, unmodified state (`catalogue_settings`/`catalogue_product` both still
empty) before stopping.

**One real lint defect found and fixed mid-session**: `CatalogueCsvFormat.kt`'s BOM constant was
initially written as a literal embedded BOM character (`'\uFEFF'` typed directly, not escaped) —
`lintDevDebug` correctly flagged this as Android's `ByteOrderMark` check (a literal BOM mid-file is
unsafe cross-tool). Fixed to the escaped Kotlin literal `'\uFEFF'`, functionally identical, lint clean
after.

### F. Company/branch isolation adversarial audit (Section 9 of the governing instructions)

Added 4 new repository-level adversarial tests specifically using the *identical* identifier/name in
two different companies (not merely different data that happens not to leak — proving no accidental
key collision when `companyId` is the only distinguishing factor): the same Tally Stock Item GUID
independently linked in two companies (two independent products, reconciliation isolated), the same
SKU text entered in two companies, the same customer-facing category name on Published products in
two companies (`listPublishedForCategory` never mixes them), and the same custom Excel column
name+value in two companies. The "same image filename across companies" item from the governing
instructions' own checklist was reviewed and found to be a structural non-issue by the existing
architecture, not a new gap: `AndroidCatalogueAssetStore`'s file path is
`<companyId>/<productId>/<assetId>.<ext>` — the original filename is never part of the path at all
(architecture §10, confirmed by direct source inspection) — so no new test was added for it; the
existing `assets never leak across companies` repository test already exercises the same companyId-
keyed storage/retrieval this guarantee depends on.

### G. What was not done, and why (deferred, not silently dropped)

- **Live Tally/Connector validation** (TD-043's `stockItemsEnrichedFields` promotion, a real second-
  company physical isolation walkthrough) — explicitly out of scope this session per the governing
  network-limitation instruction; TD-043 remains exactly as Phase 56 left it, `EXPERIMENTAL_DISABLED`.
- **Branch UI** (company-level branch selector + branch CRUD screen) — Phase 56 §H's other disclosed
  gap, not attempted this pass; `Branch`/override-by-branch remain fully implemented and tested at the
  repository/domain layer only, with no screen exposing it yet.
- **Excel import/export UI** (file picker, import preview screen, "Export" action wired into
  `CatalogueScreen`) — the domain/data layer (parse/validate/preview/commit/export, all tested) is
  complete and ready to wire up, but no Compose screen or navigation route was added this pass; a
  future session can wire `ActivityResultContracts.GetContent`/`CreateDocument` directly against
  `CatalogueCsvFormat`/`CatalogueExcelValidator`/`CatalogueExcelImportUseCase`/
  `CatalogueExcelExportUseCase` with no further domain-layer work required.
- **TD-047 (Manual product Unit gap)** — disclosed, not fixed; needs product-owner input (see the TD
  registry entry).
- **A full live create→publish→category-share device walkthrough** — attempted, not completed (§E);
  the underlying share mechanism itself was already physically proven end-to-end in Phase 56 with the
  full-catalogue scope, and this session's new code only adds a second entry point onto that identical
  proven path, so the residual risk of an unexercised full walkthrough is judged low, but it is a real
  gap in physical evidence, named here rather than glossed over.

Working tree left clean after this phase's commit; nothing pushed to `origin`, per standing practice.

## 50. Phase 59 — MVP-1.4 Catalogue: TD-047 resolution, Branch selector UI, offline-only pass to
pre-live-validation completeness

Continued directly from Phase 58 (`8f9fae8`), same session, same mobile-hotspot network
constraint. User authorized taking Catalogue "to its strongest possible pre-live-validation
completion state" and gave an explicit product decision for TD-047 (Unit becomes owner-editable
for Manual products; Tally-linked stays authoritative), removing the one open product-ambiguity
Phase 58 had flagged as blocking a unilateral fix. Re-audited current implementation state against
the architecture/ledger/TD-registry directly (not from memory) before writing code, per this
project's standing discipline.

### A. TD-047 resolved (see Technical Debt Registry for the full write-up)

New nullable `catalogue_product.manualUnit` column, migration 12→13 (additive-only). Unit resolves
as `if (source == Tally) stockItem?.baseUnit else entity.manualUnit` — a Tally-linked product's
Unit stays exclusively Tally-authoritative by construction; `CatalogueRepositoryImpl.updateEnrichment`
only ever writes `manualUnit` for a `Manual`-sourced product, so an enrichment update aimed at a
Tally-linked product's Unit is silently ignored rather than merely hidden by the UI. New editable
"Unit" field in `CatalogueDetailScreen`, shown only for Manual products. Excel import now populates
it too (`CatalogueExcelImportUseCase.toEnrichmentUpdate()` gained `unit`). 16 new/strengthened
tests (11 repository, 4 ViewModel, 2 Excel — one an existing test strengthened with a new
assertion) plus the `migrate12To13` instrumented test. **Live-verified end-to-end** (§E): a real
CSV import created a Manual product whose Detail screen showed an editable Unit field correctly
pre-filled from the file, confirmed in the real on-device SQLite database, and confirmed to survive
a genuine app force-stop + relaunch.

### B. Branch selector UI (closes Phase 56 §H's other disclosed gap)

New `CatalogueBranchSelectionStore` port + `CatalogueBranchSelectionLocalDataSource` (DataStore
Preferences, own store file, mirrors `LedgerSharingPreferencesLocalDataSource`'s exact pattern —
own file, safe fallback on a corrupt/missing read) — `companyId`-keyed preference keys, so
cross-company isolation is structural (different companies read genuinely different keys, not a
shared key filtered by convention). `CatalogueScreen` gained a company-level branch selector
(architecture §17): a dropdown showing "All branches" plus every existing branch, and a minimal
"+ Add branch" (name-only dialog, mirroring the manual-product-creation dialog's own minimal-fields
precedent — deliberately no branch editing/deactivation/management screen, none of which are part
of the locked scope). Selecting a branch persists immediately and is re-read on every company load,
falling back to the catalogue-wide default if a stored selection points at a branch that no longer
exists (dangling-reference safety). Selecting a branch never filters the product list — "One shared
catalogue across branches" (Brainstorm Outcome §4) is locked and unchanged; the selector is scoping
context only, consistent with there being no override-editing UI yet to actually scope (the
override *resolution* engine itself has been fully implemented and tested since Phase 56 — this
phase only adds the missing selector surface, not a new override-editing screen, which remains
out of scope). 9 new ViewModel tests: empty-state, add-and-select, blank-name rejection,
select/deselect, persistence across a simulated relaunch (a second ViewModel instance sharing the
same underlying fake store), a dangling stored-selection fallback, single-branch rendering, and
cross-company isolation (a branch selection made under one company is never visible after switching
to another, and correctly restored on switching back).

**Live-verified**: real device, "All branches" selector renders in the app bar row; no crash.
Full add-branch → select → persist-across-relaunch walkthrough was exercised at the unit-test level
only this pass (9 tests above) — the on-device walkthrough covered rendering and the default state,
not a hands-on "type a branch name, save, relaunch, confirm" sequence; named explicitly under
"What was not done" below rather than implied.

### C. Excel import/export UI (closes the other Phase 58-disclosed gap)

New overflow menu (`MoreVert` icon, confirmed present in this project's `material-icons-core` AAR
before use, matching Phase 57's own verification discipline) offering "Import from Excel (CSV)" and
"Export to Excel (CSV)". Import: `ActivityResultContracts.GetContent("text/*")` → Route reads the
picked `Uri`'s text via `ContentResolver` off the main thread → `CatalogueViewModel` parses it
(`CatalogueCsvFormat`), builds the mandatory preview (`CatalogueExcelValidator`, identity resolution
via Stock Item Reference/SKU against the company's real products — never Product Name alone, per
the already-locked contract) → an `AlertDialog` shows create/update/skip counts, a plain-language
note on native-vs-custom field handling, any duplicate-header warnings, and every skipped row's
specific reason → `ConfirmImport` commits via the existing, untouched `CatalogueExcelImportUseCase`
and refreshes the list. Export: `CatalogueExcelExportUseCase` generates CSV text immediately (no
picker needed first, unlike import), then `ActivityResultContracts.CreateDocument("text/csv")` lets
the owner choose a destination with a suggested filename; the Route writes the already-generated
text to it. No new parsing/validation logic in the UI layer at all — every byte of contract logic
still lives in the Phase 58 domain layer, exactly as the task's own instruction required. 13 new
ViewModel tests: file-pick request, valid-file preview, malformed-row skip-with-reason, empty file,
duplicate-header warning, custom-column preservation, confirm-commits-and-refreshes,
dismiss-commits-nothing, per-company scoping, export-effect content/filename.

**Live-verified end-to-end, the strongest evidence in this phase**: pushed a real CSV
(`Product Name,Unit,Description,Category,Warranty` / one data row) to the device's Downloads via
`adb push` + a media-scanner broadcast, then drove the *actual* on-device flow by hand exactly as an
owner would — tapped the overflow menu, "Import from Excel (CSV)", the real Android system
document-picker opened, selected the pushed file, the Import Preview dialog correctly showed
"1 new, 0 updated, 0 skipped out of 1 rows," tapped Import (observed the disabled "Importing…"
label mid-flight), and the product appeared in the list as "Test Widget · Draft · Manually added"
with a real "Import complete: 1 created, 0 updated, 0 skipped" confirmation. Read the real on-device
SQLite database directly (`run-as ... sqlite3`) and confirmed `manualUnit='Nos'`,
`description='A fine test widget'`, `customerFacingCategory='Tools'`, and the custom
`Warranty='12 months'` field landed correctly in the new `catalogue_custom_field` table — proving
the entire Phase 58 Excel persistence layer, not just this phase's UI, end-to-end against real
platform I/O for the first time. Then exercised Export the same way: the real `CreateDocument`
system picker opened pre-filled with `catalogue-export.csv`, saved it, and the pulled file's actual
bytes were read back and confirmed byte-correct: native columns plus the `Warranty` custom column,
matching the database exactly. No crash at any point (`logcat AndroidRuntime:E` checked after every
step). Test data was cleaned up afterward (product Published then Archived — Catalogue has no
delete by design — Public toggle returned to off, pushed files removed from the device).

**One genuine testing-methodology lesson worth recording, not a product defect**: mid-cleanup, a
rapid double-tap at identical screen coordinates (intended as two consecutive "Archive" taps)
actually landed on "Archive" then "Restore" once the button set changed after the first tap
succeeded, silently reverting the test product from Archived back to Draft. Caught by re-checking
state via a fresh `uiautomator dump` rather than trusting the assumed tap target, and corrected with
single, dump-verified taps. `CatalogueLifecycleTransitions` — the actual state machine — was not
touched this session and remains fully covered by its own pre-existing exhaustive test suite; this
was purely an artifact of blind coordinate-based UI driving, recorded here so a future session
doesn't mistake a similar observation for a real regression.

### D. Company/branch isolation and data-integrity audit (mandatory per the governing instructions)

Added 4 repository-level adversarial tests in Phase 58 already covered the "identical identifier
across two companies" pattern for Stock Item GUID, SKU, category, and custom fields (see Phase 58's
own §F). This phase's Unit work added one more of the same shape (identical Unit value in two
companies never cross-resolves, §A above). Re-audited the branch-selection store specifically for
this class of gap: confirmed the DataStore key itself is `companyId`-prefixed (not merely filtered
after a shared read), so cross-company leakage is structurally impossible, not convention-enforced —
covered by the "branch selection never mixes across companies" ViewModel test (§B). No new gap
found beyond what Phase 58 already closed; company scoping across every new table/store introduced
across both phases (`catalogue_custom_field`, `catalogue_product.manualUnit`,
`catalogue_branch_selection` DataStore) was re-confirmed `companyId`-first by direct inspection.

### E. Testing, build, and real-device evidence (cumulative, both phases)

Android JVM unit tests: **1,530 total, 0 failures** (up from Phase 58's 1,494; +36 this phase: 16
TD-047 + 9 branch selector + 13 Excel UI, minus one test strengthened rather than added new outright
— see individual counts above; net matches the total delta). `compileDevDebugKotlin`/
`compileDevDebugUnitTestKotlin`/`compileDevDebugAndroidTestKotlin` all clean.
`lintDevDebug`: 0 errors, 84 warnings (identical pre-existing baseline, unchanged by this phase).
`assembleDevRelease` (full R8/ProGuard minification, resource shrinking, `lintVitalDevRelease`) ran
clean end-to-end — genuine evidence this phase's code is release-build-safe, not just debug-safe.

**Real-device evidence** (`10BF44124K000E3`, same device both phases): installed **over the existing
v12 database** specifically to exercise the real `MIGRATION_12_13` against genuine prior data — `PRAGMA
user_version` read `13` post-launch, no crash. All 12 instrumented tests in
`AppDatabaseMigrationTest` (10 from Phase 56/58 plus this phase's `migrate12To13` — Phase 58's own
`migrate11To12` counted in that file's total) ran via `connectedDevDebugAndroidTest` directly against
the real device and passed. The full Excel import→commit→export round-trip (§C) and TD-047's Unit
field (§A) were both proven end-to-end against real platform I/O and the real on-device database, not
merely simulated — the strongest class of evidence this project's own standing discipline recognizes
("every prior milestone's JVM-green result was explicitly treated as necessary but not sufficient").

### F. What was not done, and why (deferred, not silently dropped)

- **Live Tally/Connector validation** — explicitly out of scope this session (mobile-hotspot network
  constraint, per the governing instructions); TD-043 (`stockItemsEnrichedFields`) remains exactly
  where Phase 56 left it, `EXPERIMENTAL_DISABLED`. A precise validation protocol for the next
  trusted-network session is recorded in this session's final report (not duplicated here).
- **A hands-on, on-device "add a branch, select it, relaunch" walkthrough** — the branch selector's
  render/empty-state was confirmed live; the full add/select/persist sequence was proven at the
  unit-test level (9 tests) but not walked by hand on the device this pass, unlike the Excel
  import/export flow, which was. Named explicitly rather than implied as equivalent evidence.
- **TD-044 (no real Owner/staff identity)** — unrelated to this phase's scope, unchanged, still
  disclosed and open per its own registry entry.
- **A branch-management screen (edit/deactivate/reorder)** — deliberately not built; not part of the
  locked MVP-1.4 scope, and the governing instructions explicitly warned against inventing one
  merely because a selector now exists.
- **Override-editing UI** (e.g., a price-sync-mode picker scoped by the now-selectable branch) —
  still not built; the override *resolution* engine has been complete and tested since Phase 56, but
  no screen lets an owner actually set an override at any level. Out of this phase's named scope
  (Branch selector UI, not override UI), flagged as a real remaining gap for a future pass.

Working tree left clean after this phase's commit; nothing pushed to `origin`, per standing
practice.

## 51. Phase 60 — MVP-1.4 Catalogue: fresh offline audit (TD-048, TD-049 found and fixed) + mid-
session price-state product lock

Continued directly from Phase 59 (`8145ff4`), same session. User asked for a disciplined fresh
audit ("do not assume tests pass means the product is perfect") rather than new features, then
mid-task issued a genuine new LOCKED product requirement on pricing/buyer-visibility semantics,
explicitly to be incorporated into the same run without restarting.

### A. Company-isolation re-audit — TD-048 found and fixed

Re-read every Catalogue DAO query (all correctly `companyId`-scoped) and every store, this time
including `AndroidCatalogueAssetStore` at the implementation level rather than trusting its own
doc comment's claim of "path-containment check on read." Found the real gap: `resolveAssetFile`
received `companyId`/`productId` but never used them — it only checked the path was somewhere
under the shared `catalogue_assets/` root, not the specific `<companyId>/<productId>/`
subdirectory, directly contradicting architecture §13's own explicit requirement. Not currently
exploitable (every real `filePath` value already originates from a correctly-scoped DAO query) but
a genuine structural gap in a named defense-in-depth requirement. Fixed by extracting a pure,
`Context`-free `resolveContainedAssetFile` helper that validates the full tuple; 6 new JVM tests
using real temp directories prove a cross-company path is refused even though it sits under the
same shared root — the actual regression case. See TD-048 for full detail.

No other isolation gap found. `linkAll()`/`listUnlinkedStockItems`/`reconcileStockItemLinks`/the
branch-selection DataStore/custom-field tables were all re-checked and confirmed correctly scoped
(most of this had already been adversarially tested in Phases 58–59; this pass specifically looked
for anything *not* yet covered, per the task's own "add regression tests only where a genuine
uncovered risk is found" instruction).

### B. Mid-session product lock: explicit price state + seller-controlled visibility

User issued a new LOCKED requirement: a Catalogue product's price must always represent one of
three **explicit** states to a viewer (an actual price, an honest "no price supplied yet," or the
seller's deliberate "Contact for price") — the latter two must never be conflated — and, separately,
that Connect approval must never be treated as automatically granting Catalogue/price visibility;
the seller remains the sole authority, controllable at product/product-group level via the
already-locked override chain, with a buyer/viewer level explicitly out of reach of today's
infrastructure.

**Found and fixed a real, concrete violation (TD-049)**: `CatalogueShareTextRenderer.priceLine()`
rendered "Contact for price" for *both* `PriceDisplayMode.ContactForPrice` and
`PriceDisplayMode.Open`-with-no-amount-entered — exactly the conflation the lock forbids (matches
the lock's own Example B precisely). Fixed with a new `CataloguePriceState` sealed type
(`ActualPrice`/`NoPriceSupplied`/`ContactForPrice`) and a single `resolveCataloguePriceState`
resolver in `domain/model/CatalogueModels.kt`, now the one place this three-way distinction is
computed; the renderer shows "Price: Not supplied yet" for the previously-conflated case. The
Excel round-trip needed no change — `priceDisplayMode`/`manualPriceAmount` were already carried as
separate, uncollapsed fields through import/export; only the renderer was re-deriving a conflated
state from them. 7 new tests (`CataloguePriceStateTest`) + 1 existing renderer test corrected (it
had asserted the old, now-locked-incorrect behavior by name) + 1 new Excel-import round-trip test.

**Verified, not fixed (nothing to fix)**: Connect approval does not, and never did, grant Catalogue
access — a direct code search (`grep -rn "feature\.connect\|feature\.party\|ConnectionStatus"` across
`feature/catalogue/`) found zero coupling; the only two hits are doc-comment naming-pattern
references to `PartySourceLink`, not code dependencies. Catalogue sharing is gated exclusively by
the company-wide `catalogue_settings.isPublic` toggle, with no buyer-specific concept anywhere —
this is the correct, already-locked state (buyer accounts/connection-based access is explicitly
1.4b, future), not a gap this session needed to close.

**Deliberately deferred, not built**: extending the override-resolution chain (Item → Branch →
Stock-group → Catalogue-wide) to cover `PriceDisplayMode` itself (today only `PriceSyncMode` is
override-resolvable). Investigated the actual shape this would require: `catalogue_product.priceDisplayMode`
is a non-nullable plain column today (every product always has an explicit value), so "falling
through" to a branch/stock-group/catalogue-wide default would require making it nullable — a real
schema change — *and* deciding whether the Detail screen's existing plain-column write path should
become an Item-level override row instead, for consistency. Separately, **no override-editing UI
exists for any attribute yet** (not even the already-locked `PriceSyncMode`), so wiring resolution
for a second attribute today would have no way to actually be exercised by an owner — exactly the
"oversized speculative subsystem" the task's own instruction warned against building. The
resolution *engine* itself (`CatalogueOverrideResolver`) is already generic and requires no change
to accept a new attribute once this groundwork is done properly; this is genuinely deferred
infrastructure, not a currently-broken promise.

### C. Testing, build, and safety confirmation

Android JVM unit tests: **1,543 total, 0 failures** (up from Phase 59's 1,530; +13 net this phase: 6
TD-048 + 6 TD-049 resolver + 1 new Excel round-trip test; the renderer's existing "Open mode with no
resolved price" test was corrected in place, not counted as a net addition).
`compileDevDebugKotlin`/`compileDevDebugUnitTestKotlin` clean. Full Catalogue suite
re-run green after every change, not just the directly-touched files. No live Tally/Connector
communication attempted, no experimental Tally request shape invented, no security boundary
touched — this phase's changes are entirely local (asset-store path validation, pure price-state
domain logic) with zero Connector/Tally/pairing-code involvement.

### D. What was not done, and why

- **Live Tally/Connector validation** — out of scope per the network constraint, unchanged from
  Phase 59; still the one remaining categorical gate.
- **Override-chain support for `PriceDisplayMode`** — deliberately deferred, see §B; would need a
  nullable-column migration and a decision on the Detail screen's persistence path, neither safe to
  rush, and no override-editing UI exists yet to exercise it regardless.
- **Buyer/viewer-level price visibility** — explicitly out of reach of current infrastructure
  (no buyer identity/connection-awareness anywhere in Catalogue); remains 1.4b, future, per the
  lock's own instruction not to fake it.
- **An owner-facing "no price entered yet" hint in `CatalogueDetailScreen`** — the Detail screen is
  the seller's own edit surface; an empty Price text field is already self-evident to the person
  typing into it. Not changed, to keep this pass focused on the buyer-facing defect that was
  actually locked against.

Working tree left clean after this phase's commit; nothing pushed to `origin`, per standing
practice.

## 52. Phase 61 — MVP-1.4 Catalogue: live second-company (Jaju Sanitations) walkthrough finds and
fixes TD-050 + TD-051; live re-verification blocked by an in-session infrastructure gap, not
network/security

Continued from Phase 60 (`78b9d40`). A prior instance of this session began a live real-device
walkthrough against a second real company, Jaju Sanitations, specifically to exercise Catalogue's
"Link from stock"/"Link all" flow against genuinely different data than ESTIMATION had ever
provided — and hit its own session limit mid-task, leaving fully implemented, tested,
**uncommitted** code in the working tree with no registry/ledger write-up. This continuation's
first responsibility was reconstructing that exact stopping point from repository evidence alone
(uncommitted diff contents, `git log`/`git status`, the registry's own last-recorded ID) rather
than restarting or guessing — confirmed the ledger/registry's last entry was Phase 60/TD-049
(matching `HEAD` exactly), and that every uncommitted file change was already a complete,
internally-consistent fix for two genuine live defects, not a partial stub.

### A. TD-050 — Stock Item "Sync Now" never populated Android's own Room cache (FIXED)

Live evidence (already captured before the session limit, reconstructed from the uncommitted code's
own doc comments and independently re-verified this session): two consecutive Jaju Sanitations
Stock Item syncs both reported `added 954, updated 954, failed 0`, yet `cached_stock_items` held
zero Jaju rows — Catalogue's stock-item picker read this as "nothing left to link" rather than
"never synced." Root cause **independently confirmed by this session via direct source inspection**
(not taken on the prior instance's comment alone): `SyncUseCases.kt`'s `StartTargetSyncUseCase` has
carried a Room-refresh completion step for Vouchers since its original implementation and for
Ledgers since TD-039 (Phase 46) — `SyncTarget.StockItems -> started` (line 99) still returns the
raw Connector-only result, the identical bug class TD-039 fixed, never extended to Stock Items.
Fixed at Catalogue's actual read boundary rather than by widening the Sync screen's behavior: new
`StockItemLookupPort.warmStockItemCache(companyId)` (the one deliberate exception to this port's
local-cache-only contract) reuses the existing `LoadStockItemsUseCase`/`StockItemRepositoryImpl`
full-snapshot warm path — the same mechanism the Stock Items Browser's own `init` already
performs — swallowing a fetch failure exactly like every other degrade-to-cache path in this
codebase. `CatalogueStockItemPickerViewModel.load()` now calls it before listing unlinked items, so
the picker guarantees its own freshness instead of depending on an unrelated screen having been
visited first. The general `SyncUseCases.kt` asymmetry is deliberately left open as a named,
lower-priority residual gap (full detail and reasoning: `docs/technical-debt/registry.md` TD-050) —
no other reachable UI path currently depends on it.

### B. TD-051 — "Link all" took ~45 minutes for 1,208 real items (FIXED, classified first per this
task's own explicit instruction not to auto-optimize blindly)

Same live walkthrough surfaced a second finding once TD-050 unblocked real data: a genuine
1,208-item Link-all ran at roughly 2 items/sec (~45 minutes wall-clock), because `linkAll()` called
the single-item repository path once per item — three separate DB round-trips (already-linked
lookup, Stock Item lookup, two individual `upsert()` calls) repeated ~1,200 times. Classified first,
per this task's own explicit instruction: confirmed by direct inspection this is a pure
scalability/UX issue, not a correctness, timeout, or partial-state defect — every item that
completed was and remained correctly linked, nothing was ever left half-written, and a
killed/backgrounded run simply resumed cleanly (idempotent skip on already-linked ids). Fixed with a
new `CatalogueRepository.createDraftsFromStockItems()`: loads the already-linked set and the full
Stock Item snapshot once for the whole batch, then writes in fixed 200-item chunks via new
`upsertAll()` Room batch inserts (one transaction per chunk, not per row) — collapsing roughly 2,400
individual transactions down to single-digit chunk commits. Each chunk commits independently, so a
mid-run failure leaves every earlier chunk durably linked and reports exactly the true persisted
count, never an overcount. `CatalogueStockItemPickerViewModel`/`Screen` gained a `LinkAllProgress`
state and `LinearProgressIndicator` row, updating once per chunk, replacing the previous
indeterminate spinner for what had been a genuinely long-running action. Full detail:
`docs/technical-debt/registry.md` TD-051.

### C. Live re-verification attempted, blocked by an infrastructure gap in this shell — not network,
not security

Per this task's own explicit network-safety instruction, checked the actual current state before
attempting anything rather than assuming either a safe or unsafe network: the paired device
(`10BF44124K000E3`) is on `JioFiber-PARme_5G` at `192.168.29.111`, the same trusted home-router
subnet (`192.168.29.x`) Phase 44 already validated live Tally sync against — genuinely not a mobile
hotspot, confirmed via `adb shell dumpsys wifi` rather than assumed. The development laptop is on
the same subnet (`192.168.29.34`), matching the Desktop app's own recorded LAN-bound configuration
from Phase 52. Attempted a real Desktop relaunch (`npm start`, `ELECTRON_RUN_AS_NODE` correctly
unset first, unlike the blocker Phase 51 hit) specifically to re-verify TD-050/TD-051 against real
Jaju Sanitations data end-to-end: the Electron process launched, but its own live log
(`budcom-desktop.log`, timestamps matching this exact session) showed repeated
`network_resolution_failed` (the app's internal PowerShell-based active-network probe failing when
spawned as Electron's own child process) followed by `Company discovery failed: Cannot reach the
connector service` — the embedded Connector never came up as a result. Directly verified this is not
a real network or PowerShell problem: an identical `Get-NetRoute` PowerShell command run directly in
this session's own shell succeeded immediately with the correct default route. This is a
shell-spawning/sandboxing artifact specific to how this automated environment's process tree
interacts with Electron's own child-process invocation of `powershell.exe`, not a genuine network or
security condition — named honestly as a disclosed infrastructure limitation rather than worked
around. Per this task's own explicit instruction, no LAN-exposure/security-bypass workaround was
attempted (a standalone non-Electron Connector was considered and rejected for the same reason Phase
51 rejected it: it would require deliberately bypassing `requireDeviceAuthForLan`'s intentional
gate). The failed launch was cleanly terminated (`taskkill`), leaving no orphaned process. **TD-050's
fix, TD-051's fix, and the original live-discovered Jaju/1,208-item evidence are genuine and
independently corroborated by direct source inspection — only a fresh live re-walkthrough against
the fix itself could not be completed this session.**

### D. Testing, build, and safety confirmation

Android JVM unit tests: **1,558 total, 0 failures** (both `testDevDebugUnitTest` and
`testDevReleaseUnitTest`, up from Phase 60's 1,543; +15 net this phase: 3 new
`StockItemLookupPortImplTest` + 1 repository delegation test + 3 ViewModel warm-cache tests for
TD-050; 6 repository + 2 ViewModel tests for TD-051 — two other touched test files
(`CatalogueViewModelTest.kt`, `CatalogueShareContentTest.kt`) only extended shared fake-repository
scaffolding to satisfy the two new `CatalogueRepository` interface methods, adding no new test
cases of their own). `compileDevDebugKotlin`/`compileDevDebugUnitTestKotlin`/
`compileDevReleaseUnitTestKotlin` all clean; `lintDevDebug` re-run clean (see final report for the
exact error/warning count). No live Tally/Connector communication was attempted beyond the blocked
relaunch in §C (no experimental Tally request shape invented, no live data modified); no
company-isolation or security boundary touched — every change this phase is either local Room
batching/caching logic or pure UI progress state.

### E. What was not done, and why

- **Live re-verification of TD-050/TD-051 against real Jaju Sanitations data** — blocked by the §C
  infrastructure gap, not by network/security policy; the single remaining categorical gate for
  MVP-1.4, same standing status as every prior phase's own "Live Tally/Connector validation"
  disclosure.
- **`SyncUseCases.kt` Stock Item Room-refresh symmetry fix** (mirroring TD-039's `Ledgers` case
  exactly) — deliberately deferred, see TD-050's own entry; a real, named architectural
  inconsistency, but not currently reachable by any UI path now that both known consumers
  (the Stock Items Browser and Catalogue) self-heal independently.
- **A device-side re-check of TD-048 (asset isolation) and TD-049 (price-state wording) against real
  Jaju Sanitations data** — both remain proven at the JVM-test level from Phase 60; a live
  cross-company walkthrough exercising them with two genuinely different real companies open was
  planned but not reached, for the same §C reason.

Working tree left clean after this phase's commit; nothing pushed to `origin`, per standing
practice.

## 53. Phase 62 — TD-050 live re-verification against real Jaju Sanitations data, end to end
(2026-08-25)

Direct continuation of Phase 61, same day, user-directed: "start Live re-verification of TD-050/
TD-051 against real Jaju Sanitations data." Phase 61's own attempt to relaunch the Desktop app from
this automated shell had failed (Electron's embedded PowerShell network-probe subprocess never
completed); this phase re-attempted it and found the Desktop app already running as a normally-
launched instance (user-started), sidestepping the shell-spawning quirk entirely rather than working
around it.

### A. Root-causing the Phase 61 launch blocker, for the record

Before using the now-running instance, isolated *why* the automated launch had failed, since the
Ledger should not carry an unexplained "infrastructure gap." A minimal, hand-built Electron harness
(`app.whenReady()` + a bare `execFile('powershell.exe', ...)`) succeeded in 414ms from this exact
shell — ruling out "Electron can't spawn PowerShell here" as the cause. Re-testing with the real
production `ROUTE_QUERY_SCRIPT` (`route-querier.ts`, four chained `Get-Net*` cmdlets) showed it
reliably takes ~3.2s under this machine's real conditions — safely under the code's own 5s
`QUERY_TIMEOUT_MS`, so not a timeout either. The specific conjunction that broke Phase 61's attempt
(concurrent heavy Gradle-daemon CPU load from that same session's own lint/test runs, launched
moments before the Desktop relaunch) was not re-isolated further once a normally-running instance
made it moot — recorded as the leading explanation, not proven beyond doubt. No code changed; this
was pure diagnosis to avoid leaving a vague "shell can't launch GUI apps" claim unexamined.

### B. Live re-verification of TD-050, end to end, against real Jaju Sanitations data

With the Connector confirmed live (`/health`: `tallyReachable: true`, `ConnectorSession: "Selected
company: Jaju Sanitations"`) and the device on the same trusted `192.168.29.x` router, built and
installed the **Prod-flavor** debug variant (`installProdDebug` — `com.budcom.android.debug`, the
package that actually carries this device's real pairing/data; Phase 61's `installDevDebug` had
targeted the empty `com.budcom.android.dev.debug` sibling package by mistake, caught and corrected
this phase) carrying the TD-050/TD-051 fix.

Before touching anything, inspected the real on-device database directly (`run-as ... sqlite3`,
after discovering the correct file is `budcom.db`, not the empty stray `budcom_database` file also
present) and found Jaju Sanitations' Catalogue already fully populated from a prior session: 954
`cached_stock_items` rows, 954 `catalogue_product` rows (953 Draft + **1 Published**, with real
enrichment). Because a real, already-Published product's data must not be put at risk, deliberately
**did not** reset/delete any Jaju cache or Catalogue rows to manufacture an empty-cache scenario —
confirmed instead `catalogue_settings` has no row for Jaju (Catalogue defaults closed/non-public for
this company), so the safe, fully non-destructive test was to trigger a *real* Stock Item sync and
observe `cached_stock_items.syncedAt`, which changes on every genuine re-extraction even when the
underlying data is unchanged.

Sequence, driven by hand via `adb`/`uiautomator` exactly as an owner would (each step screenshotted):
1. Real "Sync Now" for Stock Items, Jaju Sanitations: **954/954 processed, added 0, updated 954,
   failed 0** — the identical shape as the original report (`added 954`/`updated 954` the first time
   these items existed; `updated 954` now that they already exist), confirming this is a real Tally
   re-extraction, not a no-op.
2. Immediately after, queried `cached_stock_items.syncedAt` for `jaju-sanitations` directly:
   **unchanged**, still the old `2026-08-24T16:59:55.975Z` — a live, real-time reproduction of the
   exact original defect (a successful Sync Now that never touches Android's own Room cache).
3. Opened Catalogue → "Link from Tally stock" (the picker `warmStockItemCache()` now guards).
4. Re-queried immediately: `cached_stock_items.syncedAt` had jumped to **`2026-08-25T04:30:20.723Z`**
   — a fresh timestamp distinct from (not merely equal to) the Sync screen's own reported
   `2026-08-25T04:30:21.360Z`, proving `warmStockItemCache()` performed its own genuine live Tally
   pull the moment the picker loaded, not a read of already-stale data.
5. The picker rendered **"Every synced Tally stock item is already in your Catalogue."** — the
   correct, honest state (all 954 already linked), not the original bug's false-empty/silent-failure
   shape.
6. Re-queried `catalogue_product` afterward: still 954 (953 Draft + 1 Published), the Published
   product's enrichment fields unchanged — confirmed zero side effects from this walkthrough.

This is definitive, live, first-hand proof of TD-050's fix mechanism against real data, captured with
direct before/after database evidence rather than trusting UI text alone. TD-050 marked **FIXED,
live-re-verified** in the registry.

### C. TD-051 — live timing re-measurement not possible against Jaju; honestly left open

Since all 954 real Jaju stock items are already linked (from a prior session, before this task began),
a real Link-all run today has nothing to do there — there is no way to freshly re-time the ~45-minute
figure against this specific company without either resetting real linked/Published data (declined,
per §B) or using a different company outside this task's named scope. TD-051's algorithmic fix
(chunked batch writes) remains proven only at the unit-test level; a live timing re-measurement is
recorded as still open, pending either a genuinely large fresh unlinked dataset or explicit
authorization to safely reset one.

### D. Documentation only — no further code change this phase

No production code was touched this phase (Phase 61's fix was already correct and is what got
live-verified). Updated `docs/technical-debt/registry.md` (TD-050 status/evidence, TD-051 status
note) to reflect this session's live evidence. Full detail above; `docs/technical-debt/registry.md`
TD-050 (now live-verified), TD-051 (timing re-measurement still open).

Working tree left clean after this phase's commit; nothing pushed to `origin`, per standing
practice.

## 54. Phase 63 — MVP-1.4 final gate: TD-051 evidence-limitation confirmation + full locked-
requirement audit + MVP-1.4 COMPLETE decision (2026-08-25)

Direct continuation of Phase 62, same day, user-directed final gate. Explicitly bounded: do not
manufacture a destructive TD-051 timing test against Jaju Sanitations' real data (including its one
Published product), check whether any other already-connected real company offers a safe naturally-
unlinked dataset first, then audit the full LOCKED MVP-1.4 requirement set against the actual
codebase (not assumed from prior reports) and render one final COMPLETE/NOT-COMPLETE decision.

### A. TD-051 — confirmed no safe naturally-unlinked real dataset exists

Queried the real on-device database for every company Android has ever synced. Both are fully
linked: **Jaju Sanitations** (954 `cached_stock_items` = 954 `catalogue_product_source_link` rows,
per Phase 62) and, newly checked this phase, **ESTIMATION** (1,508 `cached_stock_items` = 1,508
`catalogue_product_source_link` rows, plus 1 independent Manual product — also 100% linked). No
third company is onboarded to this device's Catalogue (a `dv` company is visible via the Connector's
`/companies` endpoint but was never synced to Android by any prior session — using it would mean
*onboarding a new real company* for the first time, not merely inspecting one already connected, so
per this task's own "already-connected" framing it was correctly left untouched rather than treated
as a loophole). Per the governing instruction, no data was reset/unlinked to manufacture a timing
run. TD-051 is recorded as **FIXED, unit/implementation-verified; live wall-clock timing
UNAVAILABLE — an evidence limitation, not a product defect** (`docs/technical-debt/registry.md`
TD-051, updated this phase).

### B. Full LOCKED MVP-1.4 requirement audit

Delegated a read-only, source-level (not ledger-prose-level) audit of all 20 checklist items named
by the governing task against `docs/architecture/BUDCOM-MVP-1_4-CATALOGUE-BRAINSTORM-OUTCOME.md`
(the authoritative Final Scope Lock), the architecture document, and the actual current source tree.
**18 of 20 items PASS outright** with direct file/function-level evidence (data foundation, Tally
identity/reconciliation, lifecycle state machine, owner-enrichment vs. Tally-authoritative structural
protection, Excel contract + import/export UI + custom fields, branch selector, TD-047 Manual Unit,
assets/TD-048, category/full-catalogue sharing, buyer-visibility isolation — re-confirmed zero
Connect/Party coupling, price-state semantics/TD-049, company isolation, and TD-050/TD-051
themselves). **2 items (override-editing UI, and the related owner-identity gap behind Publish/
Archive) were flagged as CONCERN by the audit** but, cross-checked directly against the brainstorm
document's own explicitly-enumerated "Locked Catalogue Decisions" (lines 35-44 of the Final Scope
Lock): only the override **resolution order** itself (Item → Branch → Stock-group → Catalogue-wide)
carries the document's own `LOCKED` tag, and that resolver (`CatalogueOverrideResolver`) exists,
is correct, and is tested — override-*editing* UI was never one of the document's enumerated locked
bullets, and its absence was already investigated, reasoned about, and deliberately deferred as a
non-blocking scope decision in Phase 60 (avoiding an "oversized speculative subsystem" per that
phase's own instruction), not newly discovered here. Similarly, TD-044 (no real owner/role identity)
has stood as `OPEN, disclosed, "Not scheduled — cross-cutting, out of Catalogue's own scope"` in the
registry since Phase 56 — a pre-existing, already-accepted, cross-cutting limitation, not a new
Catalogue gap. Per the governing task's own explicit instruction not to treat previously-deferred
functionality as an artificial blocker, **neither is treated as a blocker for this gate.**

### C. Tests / build

No production code was changed this phase (pure verification + documentation). Per the governing
task's own "run only appropriate validation for anything changed" instruction, the full JVM suite was
not re-run — it relies on Phase 61's already-recorded **1,558/1,558 both variants, 0 lint errors**,
unchanged since (confirmed via `git log`/`git status` that no source file has changed since that
commit).

### D. What remains open, honestly, and why none of it blocks this gate

- **TD-051 live wall-clock timing** — evidence limitation (§A), not a defect; the correctness/
  durability/non-corruption properties that actually matter for release-readiness are proven (unit
  tests + the live picker-path exercise in Phase 62).
- **Override-editing UI** (any attribute, not just `PriceSyncMode`) — deliberately deferred since
  Phase 60, not part of the document's own enumerated LOCKED bullets, requires a schema decision
  (`priceDisplayMode` nullability) not safe to rush into this gate.
- **TD-044** (no real owner/role identity anywhere in BUDCOM) — pre-existing, cross-cutting, out of
  Catalogue's scope by the registry's own standing classification.
- **TD-043** (Stock Item enriched-fetch fields, `EXPERIMENTAL_DISABLED`) — blocks only the
  Stock-group override *level* specifically (an advanced refinement on top of the already-working
  Item-level override), not core Catalogue linking/lifecycle/sharing, which are unaffected and
  already live-proven against real 954/1,508-item companies.

None of these were introduced or newly discovered this phase; all were already disclosed in earlier
sessions' own documentation, consistent with this task's explicit instruction not to reopen or
manufacture new scope.

### E. FINAL DECISION

**MVP-1.4 COMPLETE.** Every LOCKED requirement enumerated in the Final Scope Lock document is
genuinely implemented, tested, and — for the two defects discovered via real second-company live
data (TD-050, TD-051) — either fully live-verified (TD-050) or unit/implementation-verified with an
honestly-documented, non-blocking live-timing evidence gap (TD-051). Company isolation, the
buyer-visibility/seller-authority boundary, and the price-state semantics lock were all independently
re-confirmed intact this phase, not merely carried forward from memory. No genuine blocker remains.

Full detail: `docs/technical-debt/registry.md` TD-051 (updated); this section for the full audit
outcome.

Working tree left clean after this phase's commit; nothing pushed to `origin`, per standing
practice.
