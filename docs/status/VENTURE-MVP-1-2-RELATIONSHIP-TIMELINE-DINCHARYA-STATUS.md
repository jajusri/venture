# VENTURE MVP-1.2 — Relationship Timeline, Issue History, Dincharya & OI — Status

**Status:** Part A complete. Part B complete — acceptance gate PASSED. Part C complete. Checkpoint
(B/C preservation + MVP-1.2-D readiness review) complete. Part D (Dincharya) complete.
**Part E (integrated hardening + freeze) complete — MVP-1.2 COMPLETE / FROZEN.** Final candidate
`0.1.1-continuity.27` (versionCode 28) built and installed on the owner device. STOP per this
task's own governing prompt — MVP-1.3 not started, not authorized.
**Companion documents:** `docs/status/VENTURE-CURRENT-DEVELOPMENT-STATUS.md` (concise current-state
checkpoint), `docs/status/VENTURE-DEVELOPMENT-LEDGER.md` (chronological record),
`docs/architecture/VENTURE-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md` (locked
architecture baseline), `docs/governance/VENTURE-PRODUCT-DECISION-LOG.md` PDL-014–PDL-018 (the five
locked product decisions this milestone builds on, PDL-018 being Part D's own eligibility/ordering
rules).

---

# Part A — MVP-1.2-A: Structured Party Activity Foundation

**Starting HEAD:** `ea6869caf71ff6a07406b9d15dbb0b68c7bcb5a2` (`docs: MVP-1.2 planning, recovery &
architecture review`), working tree clean.

## A1. Product decisions locked (session prerequisite, before implementation)

The four open product questions from the 2026-08-17 architecture session were explicitly answered
by the Product Owner this session (2026-08-18) and recorded permanently as
`docs/governance/VENTURE-PRODUCT-DECISION-LOG.md` PDL-014 through PDL-017:

- **PDL-014** — Relationship Timeline is the unified historical presentation for a Party (Party =
  the relationship, Timeline = the history). Confirms the architecture doc's own working
  assumption exactly.
- **PDL-015** — Referral Tree / RJ Concept is explicitly outside MVP-1.2, deferred to a later
  dedicated milestone.
- **PDL-016** — Home Insights / OI system remains outside MVP-1.2; Dincharya is self-contained.
- **PDL-017** — OS-level notifications deferred; Dincharya v1 is in-app only.

All four confirmed the architecture document's own recommendations — no architecture rework was
needed, only converting "working assumption" language to "locked" (architecture doc §3, §1 header
updated accordingly). `docs/status/VENTURE-CURRENT-DEVELOPMENT-STATUS.md` §1/§3 updated to match.

## A2. Scope actually implemented

Exactly the architecture document's §22 recommended prompt — MVP-1.2-A only:

1. `MIGRATION_8_9` (Room schema v8→v9).
2. `PartyNoteEntity`/`PartyNote`/`PartyNoteDao` extended for the new columns; new
   `PartyIssueEntity`/`PartyIssue`/`PartyIssueDao`.
3. `PartyRepository`/`PartyUseCases` extended for typed note create/edit, due-date/completion
   toggling, issue create/resolve/reopen.
4. Minimal Party Detail UI change: a type picker, a conditional due-date field, and an
   optional "part of issue" picker on the note dialog — which this session also genuinely made
   into an add/edit dialog (see A6 for why).
5. Full test coverage per architecture §17's 1.2-A row.
6. Mini-hardening: full JVM debug+release regression, both lints, all three assembles, full
   instrumented suite on a connected authorized device.
7. This Durable Development Record.

**Explicitly not built** (per PART 5's boundary, confirmed untouched): Relationship Timeline UI,
Issue History section, Dincharya screen, Referral Tree, Home Insights, OS notifications, Vartalap,
Business Profile, Catalogue, generative AI, cloud sync, direct Tally writes, Desktop/Connector
changes. Verified: zero files under `apps/venture_desktop` or the Connector changed; zero
`AndroidManifest.xml` change; zero new `Worker` class; zero `POST_NOTIFICATIONS`/notification-
channel code anywhere (`grep` verified, see A9).

## A3. Data model

### `party_notes` — extended (additive columns only, existing rows untouched)

| New column | Type | Nullable | Purpose |
|---|---|---|---|
| `type` | TEXT | NOT NULL, `@ColumnInfo(defaultValue = "'general'")` | One of `general`/`payment_issue`/`complaint`/`delivery_issue`/`commitment`/`product_interest`/`internal_remark`/`follow_up` — the exact vocabulary from architecture §9.1. |
| `dueAt` | INTEGER | NULL | Epoch millis. Only meaningful for `commitment`/`follow_up`. |
| `completedAt` | INTEGER | NULL | Epoch millis. A note is never deleted to "complete" it. |
| `issueId` | TEXT | NULL | Natural-key reference (no `ForeignKey`, matching this codebase's convention) into `party_issues`. |

`type`'s `@ColumnInfo(defaultValue = "'general'")` is not cosmetic: SQLite requires a default value
when `ALTER TABLE ... ADD COLUMN` adds a `NOT NULL` column to an already-populated table, and
Room's own generated schema must declare the identical default or migration validation reports a
mismatch. This is the first migration in this codebase to `ALTER` an existing table's columns
(every prior migration only ever added whole new tables) — verified this actually matters by
generating `9.json` via a real build and diffing it byte-for-byte against the hand-written
migration (see A5).

### `party_issues` — new table

| Column | Type | Notes |
|---|---|---|
| `companyId` | TEXT NOT NULL | Company scope |
| `issueId` | TEXT NOT NULL | UUID, PK together with `companyId` |
| `partyId` | TEXT NOT NULL | Owning party |
| `title` | TEXT NOT NULL | User-entered summary |
| `status` | TEXT NOT NULL | `open` / `resolved` — no SQL-level default (matches every other enum-column in this codebase; the app always sets it explicitly, exactly like `party_field_provenance.state`) |
| `createdAt` | INTEGER NOT NULL | |
| `resolvedAt` | INTEGER NULL | Set when status transitions to `resolved` |
| `updatedAt` | INTEGER NOT NULL | |

Index: `(companyId, partyId, status, createdAt)`. `PartyIssueDao.findAllForParty` orders
`status ASC, createdAt DESC` — `'open'` sorts before `'resolved'` alphabetically, so the desired
"open issues first" ordering falls out of a plain `ORDER BY` with no `CASE` expression needed.

Structural precedent: a note joins an issue via `PartyNote.issueId` (a plain nullable natural-key
column), not a join table — deliberately different from `party_tags`' many-to-many
`PartyTagCrossRefEntity`, since a note belongs to at most one issue, not several.

## A4. Migration

`DatabaseModule.MIGRATION_8_9` — four `ALTER TABLE party_notes ADD COLUMN` statements, one
`CREATE TABLE party_issues`, one `CREATE INDEX`. Verified byte-for-byte against Room's own
KSP-generated `app/schemas/.../9.json` after a real build (A5) — every column name, SQL type,
`notNull` flag, and default value matches exactly, including the one subtlety unique to this
migration (the `type` column's SQL-level default, above).

`AppDatabaseMigrationTest.migrate8To9_preservesExistingRowsAndAddsTypedNotesAndPartyIssuesTableOnly`
— real `MigrationTestHelper` run against the actual production `MIGRATION_8_9` object, starting
from a populated version-8 database. Proves: the pre-existing company/party rows survive; a
`party_notes` row inserted **before** the migration (with none of the four new columns) reads back
afterward with `type` backfilled to `'general'` and `dueAt`/`completedAt`/`issueId` all `NULL` —
the exact backward-compatibility guarantee this milestone's acceptance criteria requires, proven
against real SQLite, not asserted in prose; a **post**-migration insert with no `type` specified
also backfills to `'general'` via the column default (not just pre-existing rows); both new
tables' full column/type maps match the entity definitions exactly; both tables are genuinely
insert/query-usable. **Run and passing on real hardware** (device `10BF44124K000E3`), not just
JVM-simulated.

## A5. Schema verification evidence

Ran a real debug build (`kspDebugKotlin`) to generate `app/schemas/com.jajusri.venture.core.database.AppDatabase/9.json`
from the actual `@Entity`/`@ColumnInfo` annotations, then read it directly and compared against the
hand-written `MIGRATION_8_9` SQL line-by-line:

- `party_notes.type`: generated `"affinity": "TEXT", "notNull": true, "defaultValue": "'general'"` —
  matches `ALTER TABLE party_notes ADD COLUMN type TEXT NOT NULL DEFAULT 'general'` exactly.
- `party_notes.dueAt`/`completedAt`/`issueId`: generated with `"notNull": false`, no `defaultValue`
  key — matches the plain nullable `ADD COLUMN` statements (SQLite implicitly NULLs existing rows).
- `party_issues`: generated `createSql` matches the hand-written `CREATE TABLE` exactly, column for
  column; generated index name/columns match the hand-written `CREATE INDEX` exactly.

This is the same "verify byte-for-byte against Room's generated schema before hand-writing"
discipline this codebase has applied to every migration since `MIGRATION_5_6` — applied here via a
build-then-diff order rather than diff-then-write, since this migration also required reasoning
through a Room subtlety (`@ColumnInfo(defaultValue=...)`) that had no prior precedent in this
codebase to copy from.

## A6. Repository / use-case API

`PartyRepository`: `addNote`/`editNote` extended with `type`/`dueAt`/`issueId` parameters (no
default values on the interface itself — matching this codebase's own established convention,
e.g. `listByClassification`, where defaults live only on the use-case's `invoke`, never on the
repository contract). New: `setNoteCompletion`, `createIssue`, `resolveIssue`, `reopenIssue`,
`getIssuesForParty`.

`AddNoteUseCase`'s `invoke` defaults `type = NoteType.General, dueAt = null, issueId = null` —
preserving every pre-1.2-A caller's one-tap "just write a note" behavior unchanged, proven by a
dedicated JVM test (`a note with no type specified defaults to General, preserving pre-1_2-A
behavior`) and the migration test's backward-compatibility assertion (A4).

`EditNoteUseCase`/`editNote` intentionally take **no defaults** for `type`/`dueAt`/`issueId` —
callers must pass the full intended state of all three, exactly like `upsertContactPerson` already
does for contact-person edits. This was a deliberate design decision, not an oversight: a default
of `NoteType.General` on edit would silently reset an existing note's type to General on any
body-only edit — the opposite of "editing preserves what wasn't touched." The Party Detail note
dialog always pre-fills the full current state before allowing Save, so this can never happen from
the UI.

`setNoteCompletion` is implemented and tested at the repository/use-case layer
(`SetNoteCompletionUseCase`) but has no UI trigger yet in 1.2-A — the architecture doc's own
minimal-UI-change boundary (§22 item 4) only names a type picker, due-date field, and issue picker,
not a "mark complete" action. The capability exists for 1.2-B/C/D to wire, not fabricated as a UI
affordance ahead of when it's actually needed.

## A7. UI change — genuinely add/edit, not add-only

The architecture doc's recommended prompt (§22 item 4) describes "the existing add/edit note
dialog" — but direct inspection of the pre-1.2-A code found the dialog only ever supported **Add**
(hardcoded title `"Add note"`, no edit affordance anywhere on a note row, `EditNoteUseCase` already
existed at the repository layer but was never wired to any UI call site). Rather than build the
type/due-date/issue picker onto an Add-only dialog while leaving the architecture doc's own
"add/edit" description inaccurate, this session made the dialog genuinely serve both modes —
renamed `PartyDetailDialog.AddNote` → `NoteEditor` (an optional `noteId` field distinguishes
add/edit mode), exactly mirroring the established `ContactPersonEditor` precedent already in the
same file for the identical add/edit-in-one-dialog shape. `NoteRow` gained a new `Edit` button.

**Self-caught defect, fixed before commit:** extending the same dialog to edit mode initially left
the voucher-linking picker visible during edit — but `editNote`'s contract deliberately does not
carry `linkedVoucherId` (see A6), so a user picking a different voucher while editing would have
had their choice silently discarded, a false affordance. Fixed by hiding the voucher-linking
section entirely in edit mode (`dialog.noteId == null` gate) rather than expanding `editNote`'s
contract to add voucher-relinking — voucher-linking was never editable pre-1.2-A either, and
expanding it wasn't part of this milestone's authorized scope. The ViewModel also skips the
voucher-list network/local fetch entirely in edit mode now (previously wasted work).

Party Detail's existing add/edit note dialog (now `NoteEditorDialog`) gained: a type picker
(`NoteType` list, reusing the exact "row of `TextButton`s with a `✓` prefix for the selected item"
pattern already used by this file's tag/voucher pickers — no new UI pattern introduced); a
conditional due-date field (plain `YYYY-MM-DD` text input, shown only for `commitment`/`follow_up`
— deliberately not a full date-picker dialog, since no date-picker pattern exists anywhere else in
this codebase yet and inventing one is out of proportion to a minimal foundation milestone;
invalid/blank text is treated as "no due date" with an honest notice on invalid non-blank input,
never a crash); an "existing open issue, or start a new issue" picker.

## A8. Tests

- **New JVM tests (14):** `PartyRepositoryImplTest` — 5 notes tests (default-type
  backward-compatibility, due-date carrying, extended edit, mark-complete, reopen) + 5 issues tests
  (create-starts-open, resolve preserves notes, reopen clears `resolvedAt`, open-before-resolved
  ordering, company isolation) = 9; `PartyDetailViewModelTest` — 5 tests (default type on add,
  Follow-up type + due date save together, invalid-due-date notice without silent data loss,
  edit-preserves-identity, new-issue-from-note-editor).
- **New instrumented tests (16), run and passing on device `10BF44124K000E3`:**
  `AppDatabaseMigrationTest.migrate8To9_...` (1, real production migration object, see A4);
  `PartyNoteDaoTest` (5, new file — entity-default round-trip, typed/due-date/issue round-trip,
  complete/reopen round-trip, company isolation, paging carries the new columns);
  `PartyIssueDaoTest` (5, new file — find-by-id, resolve, open-before-resolved ordering, company
  isolation, per-party isolation); `PartyDetailScreenTest` (5 — type-picker selection, due-date
  field shown/hidden by type, edit-tap wiring, edit-mode title/prefill).
- **Existing-test mechanical updates (no behavior change):** five unrelated fake `PartyRepository`
  implementations elsewhere in the test tree (`ConnectViewModelTest`, `PartyXmlExportViewModelTest`,
  `ProspectCreateViewModelTest`, `ReconcilePartiesFromLedgersUseCaseTest`, `SyncViewModelTest`) each
  needed their `addNote`/`editNote` overrides extended to the new interface signature plus five new
  `error("unused")` stub overrides for the new interface members — mechanical, same class of change
  as MVP-1.1-A's own precedent ("two pre-existing hand-written `FakeLedgerDao` test doubles needed
  one added override each after the additive DAO method").

## A9. Mini-hardening review

Performed explicitly before declaring the milestone complete, per PART 8:

- **Company isolation:** every new DAO query is `companyId`-scoped; dedicated adversarial tests
  exist at both the DAO layer (`PartyNoteDaoTest.typedNotesAreCompanyIsolated`,
  `PartyIssueDaoTest.issuesAreCompanyIsolated`) and the repository layer (`notes are company
  isolated`, `issues are company isolated` in `PartyRepositoryImplTest`) — same-name data inserted
  under two different `companyId`s never bleeds across.
- **Null/empty data:** `dueAt`/`completedAt`/`issueId` are all-nullable by design; an empty note
  body is rejected with a notice before any repository call; an empty/invalid due-date string is
  treated as "no due date" (or rejected with a notice if non-blank and unparseable) — never a crash.
- **Duplicate issues:** `createIssue` does not deduplicate by title — deliberate, not an oversight:
  unlike the global `party_tags` vocabulary (`createOrGetTag` dedupes because tags are a shared
  vocabulary), each issue is a distinct instance of a real problem a user is tracking; two issues
  sharing a title (e.g., two unrelated "short shipment" problems) are legitimately different issues.
- **Duplicate notes:** unchanged from pre-1.2-A — every note gets its own UUID via `upsert`
  (REPLACE-by-primary-key), no duplication path introduced.
- **Completion transitions:** `setNoteCompletion` tested both directions (mark complete, reopen)
  at the repository and DAO layers; never deletes the note (Timeline history preserved).
- **Date/time handling:** due dates stored as epoch millis (`ZoneId.systemDefault()` at parse/format
  time, consistent with this app's existing date handling); switching a note's type away from
  `commitment`/`follow_up` clears the in-flight due-date text in the dialog *and* the value actually
  saved (double-enforced — event handler clears the text field, `saveNote()` independently computes
  `dueAt = null` whenever the current type doesn't show a due date), so a stale due date can never
  survive a type change.
- **Migration integrity:** A4/A5 above — byte-for-byte schema verification plus a real
  `MigrationTestHelper` proof, including the explicit backward-compatibility case.
- **Offline behavior:** every new read/write is local-Room-only; zero network call, zero Connector
  interaction anywhere in the new code (grep-verified — no import of any Connector/API type in
  the touched files).
- **Performance:** `PartyNoteDao`'s existing bounded paging (`pageForParty`/`countForParty`,
  `LIMIT`/`OFFSET`) is untouched; `PartyIssueDao.findAllForParty` is unbounded but per-party
  (matching the existing unpaged per-party precedent for tags/contact-persons — a Party's own issue
  count is expected to stay small; the genuinely new, must-be-bounded cross-party query is
  Dincharya's, explicitly 1.2-D scope, not touched here).
- **Accessibility:** every new interactive element is a `TextButton`/`OutlinedTextField` with a
  visible `Text`/`label` child — the same self-describing pattern already used (and previously
  audited in MVP-1.1-E) for this file's tag/voucher pickers; no new icon-only or unlabeled control
  introduced.
- **Privacy / sensitive logging:** note bodies and issue titles are user-authored VENTURE-only text,
  the same trust level as pre-existing notes; grep-verified zero `Timber`/`Log` call anywhere in the
  touched `feature/party` files referencing note/issue content.
- **Destructive operations:** no `DELETE` statement was added anywhere; `party_issues` has no
  delete method at all — resolving/reopening only ever changes `status`, never removes a row,
  matching the locked "resolving an issue never deletes or hides its notes" architecture rule.
- **Repository boundaries:** Room → Domain → Repository → Use Case → ViewModel → Compose layering
  preserved exactly, no shortcut/bypass introduced.
- **Zero direct Tally write:** grep-verified — the new note/issue code never references
  `feature/party/sharing` (the XML export module) at all; `TallyExportFieldMapping.ELIGIBLE_FIELDS`
  is untouched (still exactly the six pre-existing fields).
- **Zero Desktop/Connector touch, zero manifest change, zero new `Worker`, zero notification
  permission/channel code:** all grep-verified this session (see A2).

**Defects found and fixed:** one — the edit-mode voucher-picker false affordance (A7), caught during
this session's own review before the milestone was declared complete, not by an external report.

**No other genuine defect found.**

## A10. Full regression results

- `testDebugUnitTest`: **1,179/1,179 passing** (was 1,165 after MVP-1.1-E; +14 new this session).
- `testReleaseUnitTest`: **1,179/1,179 passing** (full re-run, same suite, same count).
- `lintDebug` / `lintRelease`: **0 errors** both (confirmed via report XML `severity="Error"` count,
  not just console summary).
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`: all `BUILD SUCCESSFUL`.
- `connectedDebugAndroidTest` on device `10BF44124K000E3` (`I2407i`/`I2407`): **245/257 passing**
  (was 229/241 after MVP-1.1-E; +16 new this session). The 12 failures are the exact same
  pre-existing device-viewport-artifact class documented since MVP-1.1-B (`DashboardScreenTest`,
  `DiagnosticsScreenTest`, `LedgerStatementScreenTest`, `SecurePairingScreenTest`,
  `ServerConfigScreenTest`, `SettingsScreenTest`, `SyncScreenTest`, `VoucherDetailsScreenTest`) —
  **zero overlap with any Connect/Party/note/issue file this session touched.**

**A genuine environmental false-alarm, investigated and resolved rather than accepted blindly:** the
first full instrumented run this session produced 53 failures, not the expected ~12 — every extra
failure carried the identical `IllegalStateException: No compose hierarchies found in the app` or
an equivalent "component is not displayed" signature, scattered across totally unrelated screens
(`CompanyScreenTest`, `ConnectScreenTest`, `PdfPreviewScreenTest`, etc., none touched this session),
consistent with the device's screen locking mid-run during the ~10.5-minute suite. `adb devices -l`
confirmed the device stayed connected throughout (not a disconnect). Enabled `adb shell svc power
stayon usb` (keeps the screen on while USB-connected — a device power setting, not an app-state
change) plus a screen wake/keyguard-dismiss, then re-ran the full suite: it completed in 2m11s
(down from 10m27s) with exactly 12 failures, matching the documented baseline precisely and
confirming the extra 41 were a device-timeout artifact, not a code regression. Recorded here rather
than silently discarded, per the Durable Development Record rule — this is exactly the "transient
device-testing artifact" class of non-defect, not swept under the rug.

## A11. Version / artifacts

**Version deliberately not bumped this session.** Per this task's explicit instruction ("Do not
perform repeated unnecessary version bumps during intermediate work... defer the coherent milestone
version bump until the appropriate milestone boundary") and this codebase's own precedent
(MVP-1.1-C deferred its bump to the combined C+D session rather than bumping after every slice):
1.2-A is a data-foundation milestone within the larger 1.2-A→B→C→D→E arc, not a standalone
user-facing release candidate. `versionName`/`versionCode` remain
`0.1.1-continuity.26`/`27`, unchanged from the MVP-1.1 freeze. No APK artifact was produced for
distribution this session (only the standard `assembleDebug`/`assembleRelease` regression-gate
builds, not preserved as named release candidates).

## A12. Accepted limitations (explicit)

- Editing a note's linked voucher is still not possible (unchanged from pre-1.2-A) — the voucher
  picker only appears when creating a new note (A7).
- A note's issue association can be changed via the picker only while that issue is still open; if
  a note's issue gets resolved, the note-editor's issue picker won't show it as a selectable/
  deselectable option (it isn't in the open-issues list), though the underlying association is
  preserved correctly in the data. Full issue management is 1.2-C's Issue History section.
- The due-date field is a plain `YYYY-MM-DD` text input, not a native date-picker dialog — an
  interim, deliberately minimal choice (A7); no date-picker pattern exists anywhere else in this
  codebase to reuse, and building one is out of proportion to this foundation milestone.
- `setNoteCompletion` has no UI trigger yet (A6) — implemented and tested at the repository/use-case
  layer only, per the architecture doc's own minimal-UI-change boundary.
- The previously accepted 12 device-viewport instrumented failures remain accepted, re-confirmed
  with zero overlap and zero new failures this session (A10).

## A13. Exact NEXT TASK (superseded — see Part B)

~~MVP-1.2-A is complete, mini-hardened, and documented. STOP... Awaits Product Owner/technical
review of this 1.2-A result before being authorized.~~ — **explicitly authorized and completed this
session**, see Part B below.

---

# Part B — MVP-1.2-B: Relationship Timeline

**Starting HEAD:** `f26def29c7e23ab2743616bc469abcdf94328242` (`feat(android): lock MVP-1.2 product
decisions, implement MVP-1.2-A Party Activity foundation`), working tree clean. Recovered state by
direct inspection rather than trusting the prior session's own report: re-read `git log -1`,
`DatabaseConstants.VERSION` (confirmed `9`, unchanged since 1.2-A), `adb devices -l` (device
`10BF44124K000E3` connected/authorized), and the actual current `PartyDetailScreen.kt`/
`PartyDetailUiState.kt`/`PartyDetailViewModel.kt`/`PartyRepository.kt` source before writing any
code.

## B1. Scope actually implemented

Exactly architecture doc §10/§11's 1.2-B line item: the Relationship Timeline read model, merging
`party_notes` and `party_export_events` into one chronological, bounded/paged feed, replacing the
flat Notes list's presentation on Party Detail (locked by PDL-014). **Deliberately not included in
B, reserved for C's own explicit "Issue/Timeline Consistency" requirement:** issue-lifecycle events
(opened/resolved) in the Timeline — architecture doc's own 1.2-B description names only "notes +
export events interleaved," and building the Issues section first (C) before wiring its lifecycle
into the same merge query keeps each milestone's diff honestly scoped to what it actually delivers.

## B2. Architecture — the merge query

New `PartyTimelineDao` (`feature/party/data/local/PartyTimelineDao.kt`) — a dedicated read-only
`@Dao` belonging to neither `party_notes` nor `party_export_events` alone, since the merge belongs
to neither table individually. A single SQL `UNION ALL` of both tables (aliased into one shared
column shape via a `TimelineRowEntity` projection, non-`@Entity`, no migration needed — both source
tables already existed), `ORDER BY timestamp DESC, id ASC LIMIT/OFFSET` computed **entirely in
SQL**, not merged in Kotlin after two separate paged reads — this was a deliberate architecture
decision: naively fetching page N from each source independently and merging in memory cannot
correctly paginate a single chronological feed across a source boundary (page 2 might need 3 rows
from notes and 1 from exports, a split that shifts unpredictably as more data accumulates). The
`id ASC` tie-break (not left to chance) guarantees deterministic paging even when two events share
an identical timestamp (B9's explicit same-timestamp test).

**Zero schema change, zero migration.** `PartyTimelineDao` is a pure `@Query` projection over two
already-existing tables — Room validated the query at KSP compile time against the real schema
(confirmed: `compileDebugKotlin` succeeded on first attempt with the query in place, meaning Room's
own SQL validator accepted it against `9.json`'s actual column set). `DatabaseConstants.VERSION`
remains `9`, unchanged from 1.2-A.

`issueId` filter parameter (`NULL` = full unfiltered Timeline) is already wired into the query and
repository/use-case layers this session, but not yet exposed anywhere in the UI — built now so
1.2-C's "tap an issue to see its filtered notes" (architecture §10) reuses this exact query rather
than a second, duplicate list implementation, without needing a B→C signature change later.

## B3. Data model

`TimelineEntry` (new `feature/party/domain/model/TimelineModels.kt`) — a sealed interface with
exactly two 1.2-B cases, `NoteEvent`/`ExportEvent`, each wrapping the real, unmodified
`PartyNote`/`PartyExportEvent` domain object (never a flattened/lossy projection) plus a computed
`timestamp` for sort/merge purposes. `TimelineEntryPage` mirrors the existing `PartyNotePage`
paging shape exactly (`items`/`page`/`pageSize`/`totalItems`/`canLoadMore`).

## B4. Repository / use-case API

`PartyRepository.getTimelineForParty(companyId, partyId, page, pageSize, issueId)` — no default
parameter values on the interface (matching the convention established in 1.2-A: defaults live only
on `GetTimelineForPartyUseCase.invoke`, e.g. `page = 1, pageSize = 20, issueId = null`).

## B5. UI change — Party Detail's flat Notes list becomes the Relationship Timeline

Per PDL-014, this is a genuine **replacement of presentation**, not an addition: `PartyDetailUiState`'s
`notes`/`notesPage`/`notesCanLoadMore`/`isLoadingMoreNotes` fields are gone, replaced by
`timeline`/`timelinePage`/`timelineCanLoadMore`/`isLoadingMoreTimeline`. A new computed
`notesInTimeline: List<PartyNote>` property (`timeline.filterIsInstance<NoteEvent>().map { it.note }`)
lets note-only operations (edit lookup, linked-voucher lookup) keep working against the merged
Timeline without re-fetching — the Timeline is the single source, not a second parallel list.

The section header changed from "Notes" to "Relationship Timeline"; a new `TimelineRow` composable
dispatches each entry to the existing `NoteRow` (note-kind, unchanged internals) or a new
`ExportEventRow` (export-kind, read-only, "Exported to Tally: Phone, Email" using the same
`TallyExportFieldMapping.labelFor()` plain-language labels the XML-export screen already uses — no
raw field name ever shown). Both row kinds now show a formatted date (`dd MMM yyyy`, matching
`VoucherDateFormatting.kt`'s existing display convention) — a deliberate, explicitly-justified small
enhancement: a chronological Timeline with no visible dates would fail this milestone's own
"maintain chronological clarity" requirement, and pre-1.2-B `NoteRow` showed no date at all.

Add/edit/delete-note actions, the note editor dialog, and the "Add note" button are all unchanged —
notes are still created/edited exactly as in 1.2-A, only their **read presentation** changed.

## B6. Company isolation — adversarial evidence

Dedicated tests at both layers, not incidental:
- DAO (`PartyTimelineDaoTest.timelineNeverLeaksAnotherCompanysNotesOrExportEvents`): the exact same
  `partyId` natural key reused under two different `companyId`s, with different note content under
  each — company A's Timeline returns exactly its own 2 events; company B's returns exactly its own
  1 event; a lookup combining company B's `companyId` with company A's `partyId` (the cross-company
  natural-key confusion case) returns empty, not company A's data.
- Repository (`PartyRepositoryImplTest`'s `timeline never leaks another company's notes or export
  events, even with identical content`): two Prospects with the **identical** display name and
  **identical** phone number, created independently under `co-A`/`co-B`, each given a note — proves
  the isolation holds even under a genuine same-identity collision, not just different-looking data.

## B7. Performance evidence

`PartyTimelineDaoTest.timelineStaysBoundedAndFastWithALargeFixture` — 300 notes + 50 export events
(350 total) for one party against real in-memory Room/SQLite on the connected device; a bounded
20-row paged query returns in well under the 2-second soft ceiling (same bar as `PartyDaoTest`'s own
500-row precedent), `countTimelineForParty` matches exactly (350), and the newest overall entry
(a note, since its timestamp exceeds every export event's) is correctly first — proving the merge's
`ORDER BY` genuinely spans both sources, not just one. All Timeline queries use `LIMIT`/`OFFSET`; no
full-table or full-party in-memory sort.

## B8. Offline behavior

Unchanged — `PartyTimelineDao` reads only already-local Room tables; zero network call, zero
Connector interaction anywhere in the new code (same class of evidence as 1.2-A: no import of any
Connector/API type in the touched files).

## B9. Mini-hardening review

- **Empty Party / empty timeline:** `PartyRepositoryImplTest`'s `an empty party has an empty
  timeline that cannot load more` and `PartyTimelineDaoTest.anEmptyPartyHasAnEmptyTimeline` both
  assert `items.isEmpty()`, `totalItems == 0`, `canLoadMore == false` — no crash, no error state. The
  Compose layer shows an explicit "No activity yet" message (`party_detail_timeline_empty` testTag,
  instrumented-test-verified) rather than blank space — the existing honest-empty-state discipline
  extended to this new surface.
- **One event / many events:** covered by the merge-ordering and large-fixture tests (B6/B7).
- **Same-day (same-timestamp) events:** `PartyTimelineDaoTest.sameTimestampEntriesGetADeterministic
  TieBreakSoPagingNeverDuplicatesOrDrops` — three entries sharing one exact timestamp; proves both
  that repeated reads return a stable order and that paging across the tie produces no
  duplicate/dropped row.
- **Long text / long Party name:** no `maxLines`/truncation applied to note bodies or export
  summaries (Compose `Text` wraps naturally) — matches the pre-1.2-B `NoteRow`'s own behavior,
  verified not to regress.
- **Missing optional fields:** `dueAt`/`completedAt`/`issueId` remain fully nullable through the
  merge; `PartyExportEvent.fieldNames` is defensively handled (`.orEmpty()`) though `recordExport`'s
  own pre-existing `require(eligible.isNotEmpty())` check means an empty list should never actually
  reach this path.
- **Malformed/stale references, deleted/changed underlying entities:** issues are never deletable
  (no delete method exists on `PartyIssueDao`, unchanged from 1.2-A), so a note's `issueId` can never
  dangle; the existing "linked voucher not available" honest-fallback (1.1-C) is unchanged.
- **Duplicate events:** the `UNION ALL` reads each source table exactly once per row; no
  denormalization, no write-side duplication introduced anywhere.
- **Chronological ordering / pagination:** B6/B7 above, plus `PartyDetailViewModelTest`'s
  `loading more timeline entries appends rather than replaces the page` (25-note fixture, exact
  boundary at pageSize 20).
- **Migration integrity:** N/A — no migration this milestone (B2).
- **Accessibility:** every new element is plain `Text`/`Card`, the same self-describing pattern
  already used and previously audited (MVP-1.1-E) elsewhere on this screen; `ExportEventRow` has no
  interactive control at all (read-only), so nothing new needed a content description.
- **Rotation/recomposition/navigation:** unchanged — Timeline state lives in the same
  ViewModel-scoped `StateFlow` as everything else on this screen, surviving rotation exactly like
  the pre-1.2-B Notes state did; `Load` re-fires on each navigation entry (unchanged `init` block).

**Defects found:** none this session — B extended an already-hardened surface (1.2-A) along an
already-proven architectural seam (Room → Domain → Repository → Use Case → ViewModel → Compose), and
no genuine defect surfaced during implementation or review.

## B10. Tests

- **New JVM tests (7):** `PartyRepositoryImplTest` — 5 Timeline tests (merge-ordering,
  pagination-boundary, empty-party, issueId-filter-excludes-exports, company-isolation-with-identical-
  content); `PartyDetailViewModelTest` — 2 Timeline tests (empty-state, load-more-appends).
- **New instrumented tests (16), run and passing on device `10BF44124K000E3`:** `PartyTimelineDaoTest`
  (new file, 8 — merge-ordering, count, empty, pagination-boundary, tie-break-determinism,
  issueId-filter, company-isolation, large-fixture performance); `PartyDetailScreenTest` (5 —
  empty-state, export-row rendering, notes+exports rendering together, load-more-timeline button).
- **Existing-test mechanical updates (no behavior change):** the same five unrelated fake
  `PartyRepository` implementations from 1.2-A (`ConnectViewModelTest`, `PartyXmlExportViewModelTest`,
  `ProspectCreateViewModelTest`, `ReconcilePartiesFromLedgersUseCaseTest`, `SyncViewModelTest`) each
  needed one additional `getTimelineForParty` stub override for the extended interface — mechanical,
  same precedented class of change as 1.2-A's own.

## B11. Full regression results

- `testDebugUnitTest` / `testReleaseUnitTest`: **1,186/1,186 passing** both (was 1,179 after 1.2-A;
  +7 new this session — 5 repository + 2 ViewModel; one incidental, already-documented
  `VoucherRepositoryImplTest` timing flake reproduced once on the first release run, confirmed clean
  on an isolated retry and on the full-suite re-run — the exact same pre-existing flake class
  recorded in 1.1-D/E and 1.2-A, not a new regression).
- `lintDebug` / `lintRelease`: **0 errors** both (report-XML-verified).
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`: all `BUILD SUCCESSFUL`.
- `connectedDebugAndroidTest` on device `10BF44124K000E3`: **256/268 passing** (was 245/257 after
  1.2-A; +11 net new instrumented tests run this session — 16 new minus the 5 `PartyDetailScreenTest`
  cases replaced in place). The 12 failures are byte-for-byte the same pre-existing
  device-viewport-artifact class (`DashboardScreenTest`, `DiagnosticsScreenTest`,
  `LedgerStatementScreenTest`, `SecurePairingScreenTest`, `ServerConfigScreenTest`,
  `SettingsScreenTest`, `SyncScreenTest`, `VoucherDetailsScreenTest`) — **zero overlap** with any
  file this session touched. Applied the stay-awake device-power-setting fix (`adb shell svc power
  stayon usb` + wake) *before* this session's instrumented run, based directly on 1.2-A's own
  documented lesson — the run completed cleanly in 2m10s with exactly the known 12 failures on the
  first attempt, no repeat investigation needed.

## B12. Version / artifacts

**Version not bumped.** Per this task's explicit "Do NOT bump the Android version after B" instruction
— `versionName`/`versionCode` remain `0.1.1-continuity.26`/`27`, unchanged.

## B13. Accepted limitations (explicit)

- Issue-lifecycle events (opened/resolved) do not yet appear in the Timeline — reserved for 1.2-C's
  explicit "Issue/Timeline Consistency" requirement (B1).
- The previously accepted 12 device-viewport instrumented failures remain accepted, re-confirmed
  with zero overlap and zero new failures this session (B11).
- All 1.2-A accepted limitations (specialist doc §A12) remain unchanged and still apply.

## B14. ACCEPTANCE GATE RESULT

**MVP-1.2-B ACCEPTANCE GATE PASSED — AUTOMATIC CONTINUATION TO MVP-1.2-C AUTHORIZED.**

Every mandatory gate item verified with evidence above: compiles; JVM/instrumented tests pass;
lint/assembles green; company-isolation, ordering, empty/sparse, and pagination tests all pass;
accessibility unaffected; zero new unexplained instrumented failures (exact known-12 baseline);
zero P0/P1 defect; zero security/data-integrity issue; zero architecture contradiction; zero
accidental scope expansion (issue-lifecycle Timeline events deliberately deferred to C, not
silently included). Proceeding to Part C in this same session.

---

# Part C — MVP-1.2-C: Issue History

**Starting HEAD:** `95ff98ded476c8051e892790b240a90ec5d2ea0b` (`feat(android): MVP-1.2-B
Relationship Timeline — acceptance gate passed`), working tree clean.

## C1. Scope actually implemented

Architecture doc §10/§11's 1.2-C line item: a Party Detail Issues section (open issues prominent,
resolved issues collapsed), issue-filtered Timeline view (reusing 1.2-B's `PartyTimelineDao`
`issueId` parameter — no second, duplicate list implementation), and issue-lifecycle events wired
into the Relationship Timeline for consistency (architecture §16). Issue History is explicitly
**not** a second competing chronological history — it is the structured lifecycle view of data the
Timeline already displays, per PDL-014.

## C2. Architecture — extending the Timeline query rather than building a second one

`PartyTimelineDao`'s `UNION ALL` (built in B with exactly this extension in mind) gained two more
arms reading `party_issues` directly: `issue_opened` (the issue's own `createdAt`) and
`issue_resolved` (`resolvedAt`, only emitted when currently non-null). Both are read **live** from
the same `party_issues` table the Issues section itself queries — never a separately-stored,
independently-writable record — so the Timeline and the Issues section can never disagree about the
same issue's state. This is the direct mechanism satisfying "Issue/Timeline Consistency": creating
an issue immediately produces an "Issue opened" Timeline row with zero additional write; resolving
produces "Issue resolved" the same way; reopening makes that row disappear because `resolvedAt`
genuinely became `NULL` again — the Timeline is not out of sync, it is reporting the truth.

**Accepted, explicitly documented limitation:** because `party_issues` has no append-only history
table, reopening an issue does not retain a permanent record that it was *previously* resolved at
some earlier time — only the *current* state is ever representable. Building a full issue-event
log was considered and rejected as disproportionate schema growth for this milestone (PDL-012);
this is a genuine, named trade-off, not an oversight.

`id ASC, kind ASC` (extended from B's `id ASC`) tie-breaks deterministically even when an issue's
`opened`/`resolved` rows share both `id` (the same `issueId`) and, in the rare case, an identical
timestamp — proven by a dedicated same-millisecond test.

Zero schema change, zero migration — `DatabaseConstants.VERSION` stays 9, exactly as in B.

## C3. Data model

`TimelineEntry` gained two 1.2-C cases: `IssueOpenedEvent(issueId, title, openedAt)`,
`IssueResolvedEvent(issueId, title, resolvedAt)` — thin, derived types, not a copy of `PartyIssue`.
New `IssueActivitySummary(noteCount, latestNoteAt)` domain type for the Issues section's per-card
rollup.

## C4. Repository / use-case / DAO additions

`PartyNoteDao.issueActivitySummary(companyId, partyId)` — one bounded, `GROUP BY issueId` aggregate
query over `party_notes` (already indexed by `companyId, partyId` prefix), returning note count +
latest note timestamp per issue in a single round-trip — never one query per issue card.
`PartyRepository.getIssueActivitySummary` / `GetIssueActivitySummaryUseCase` expose it.
`ResolveIssueUseCase`/`ReopenIssueUseCase` (already existing since 1.2-A, previously unused by any
UI) are now wired into `PartyDetailViewModel`.

## C5. UI — Issues section and Timeline filtering

`PartyDetailUiState` gained `issues: List<IssueCardUi>` (issue + note count + computed
`lastActivityAt = max(createdAt, updatedAt, resolvedAt, latest linked note)`), `issuesExpanded`/
`resolvedIssuesExpanded` (progressive disclosure, matching architecture §10's "count badge, full
detail on tap" and "resolved issues collapse into a secondary, de-emphasized area"), and
`selectedIssueFilterId`. The Issues section is **omitted entirely** when a Party has never had an
issue — no perpetual empty-section clutter, following the same "only show a picker section when
there's something to pick" precedent already established for the note editor's issue/voucher
pickers in 1.2-A.

Tapping an issue's "View in Timeline" action does **not** trigger the full `load()` — a new
`loadTimeline(issueId)` refreshes only the Timeline portion of state, avoiding an unnecessary
re-fetch of party/fields/contacts/tags on every filter tap. Tapping the same issue again clears the
filter (toggle), matching this screen's existing note/tag/voucher picker selection convention.
Resolve/Reopen actions refresh both the issue cards and the (possibly filtered) Timeline in the same
operation, so a resolved issue's card and its new Timeline entry appear together, never one without
the other.

## C6. Company isolation — adversarial evidence

Extended the same two-layer discipline from B:
- DAO (`PartyTimelineDaoTest.issueLifecycleRowsNeverLeakAcrossCompanies`): two companies each with
  their own issue under the same `partyId` natural key — each company's Timeline shows exactly its
  own issue's lifecycle rows, never the other's.
- DAO (`PartyNoteDaoTest.issueActivitySummaryIsCompanyIsolated`): identical `issueId` reused under
  two companies with different note counts — each company's summary reflects only its own notes.
- Repository (`PartyRepositoryImplTest`'s `issue lifecycle timeline entries and activity summaries
  never leak across companies`): a full end-to-end proof through the public repository API, not
  just the raw DAO layer.

## C7. Timeline/Issue consistency — direct evidence

- `creating an issue produces an Issue opened timeline entry using the issue's own createdAt`
- `resolving an issue adds an Issue resolved entry without removing the opened entry`
- `reopening a resolved issue removes its Issue resolved entry from the timeline, keeps opened`
- `issue-filtered timeline shows only that issue's notes, never its own lifecycle rows`
- ViewModel-level: `resolving an issue moves it out of open issues and into the timeline as
  resolved`, `reopening an issue moves it back to open and clears the resolved timeline entry` —
  proving the Issues section and the Timeline update together from the same user action, never one
  without the other.

## C8. Mini-hardening review

- **Create/empty issue:** blank-title creation is silently skipped (unchanged 1.2-A behavior via
  `resolveIssueId`'s own `title.trim().isEmpty()` guard) — no new validation needed for C.
- **Long issue text:** no `maxLines`/truncation on issue titles in cards or lifecycle rows —
  Compose wraps naturally, matching every other text row on this screen.
- **Duplicate-looking issues:** reaffirmed from 1.2-A — `createIssue` deliberately does not
  deduplicate by title (each issue is a distinct real problem instance).
- **Resolve/reopen:** both directions tested at repository, DAO, and ViewModel layers (C6/C7); an
  idempotent double-resolve (calling resolve on an already-resolved issue) does not error, just
  refreshes `resolvedAt` — a defensible, low-risk non-error path, not separately asserted.
- **Due date:** not applicable to issues themselves (a note-level concept, unchanged) — issue cards
  correctly show no due-date field, matching the locked scope (architecture §10's card fields are
  title/note-count/last-activity/action only).
- **Navigation away/back, process recreation, rotation:** `issuesExpanded`/`resolvedIssuesExpanded`/
  `selectedIssueFilterId` live in the same ViewModel-scoped `StateFlow` as every other transient UI
  toggle on this screen (e.g. dialog state) — survives rotation via the standard ViewModel-retention
  mechanism already proven throughout MVP-1.1/1.2-A/B, resets to defaults on process death exactly
  like every other non-`SavedStateHandle`-backed toggle already on this screen (not a regression,
  not a new risk class).
- **Offline behavior:** grep-verified zero network/Connector import in any new C file.
- **Timeline consistency:** C7 above — the dominant hardening focus for this milestone, per the
  task's own explicit "major acceptance criterion" framing.
- **Company isolation:** C6 above.
- **Accessibility:** every new element is `Text`/`TextButton`/`Card`, the established
  self-describing pattern; no icon-only or unlabeled control introduced.
- **Sparse data:** an issue with zero notes correctly has no entry in the activity summary map
  (`noteCount` defaults to 0 in the UI join, tested both at the DAO and ViewModel layers).
- **Many issues:** not separately large-fixture-tested beyond 1.2-A's own `PartyIssueDaoTest`
  precedent — per-party issue counts are architecturally assumed small (matching tags/contact
  persons), and the genuinely-must-be-bounded cross-party query is Dincharya's (1.2-D), not this
  milestone's.
- **Stale references:** cannot occur — issues are never deletable (unchanged since 1.2-A).

**Defects found:** none this session.

## C9. Tests

- **New JVM tests (15):** `PartyRepositoryImplTest` — 7 (issue-opened entry, resolved entry
  preserves opened, reopen removes resolved, issue-filtered timeline excludes lifecycle rows,
  activity summary count/latest, no-notes issue has no summary entry, cross-company isolation);
  `PartyDetailViewModelTest` — 8 (no-issues state, note-count/open-first ordering, resolve moves
  card + adds timeline entry, reopen reverses both, filter narrows timeline, re-tap clears filter,
  explicit clear-filter action, expand/collapse toggle).
- **New instrumented tests (14), run and passing on device `10BF44124K000E3`:** `PartyTimelineDaoTest`
  — 6 new (open-issue-produces-one-row, resolved-issue-produces-two-rows, count includes lifecycle
  rows, issue-filter excludes lifecycle rows, cross-company isolation, same-timestamp tie-break);
  `PartyNoteDaoTest` — 3 new (`issueActivitySummary` count/latest, excludes issue-less notes,
  company isolation); `PartyDetailScreenTest` — 8 new (no-issues-section, toggle-expand, resolve
  action, reopen action, view-in-timeline filter tap, filter banner + clear action, resolved-issues
  stay-collapsed, issue-opened Timeline row renders).
- **Existing-test mechanical updates (no behavior change):** the same five unrelated fake
  `PartyRepository` implementations (from 1.2-A/B) each needed one additional
  `getIssueActivitySummary` stub override.

## C10. Full regression results

- `testDebugUnitTest` / `testReleaseUnitTest`: **1,201/1,201 passing** both (was 1,186 after B; +15
  new this session).
- `lintDebug` / `lintRelease`: **0 errors** both (report-XML-verified). One transient Gradle
  parallel-task race (`lintAnalyzeDebugUnitTest`/`lintAnalyzeDebugAndroidTest` hit a Hilt-generated
  file mid-write by a concurrently-running `kspReleaseKotlin` task — "Unexpected failure during
  lint analysis... FileNotFoundException") — investigated, confirmed to be a build-tool
  scheduling artifact unrelated to any source change (the same commands succeeded cleanly on
  immediate retry with no code change), not silently retried without understanding why.
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`: all `BUILD SUCCESSFUL`.
- `connectedDebugAndroidTest` on device `10BF44124K000E3`: **274/286 passing** (was 256/268 after
  B; +18 net new instrumented tests this session). The 12 failures are byte-for-byte the same
  pre-existing device-viewport-artifact class (`DashboardScreenTest`, `DiagnosticsScreenTest`,
  `LedgerStatementScreenTest`, `SecurePairingScreenTest`, `ServerConfigScreenTest`,
  `SettingsScreenTest`, `SyncScreenTest`, `VoucherDetailsScreenTest`) — **zero overlap** with any
  file this session touched. The device stay-awake fix (learned in 1.2-A, reused in B) was applied
  proactively before this run too; it completed cleanly in 2m19s on the first attempt.

## C11. Version / artifacts

**Version not bumped.** Unchanged `0.1.1-continuity.26`/versionCode 27, per this task's explicit
instruction to defer to a coherent milestone boundary.

## C12. Accepted limitations (explicit)

- Reopening an issue does not retain a permanent record of a *past* resolution — only current state
  is representable without a dedicated append-only issue-event log, which is out of this
  milestone's scope (C2).
- The Issues section's per-party reads are not large-fixture performance-tested beyond 1.2-A's own
  `PartyIssueDaoTest` precedent — architecturally assumed small per party, unlike Dincharya's
  future cross-party query (1.2-D).
- All Part A and Part B accepted limitations remain unchanged and still apply.

## C13. FINAL RESULT

**MVP-1.2-C COMPLETE.** Every C test-gate item verified: full JVM regression clean both variants;
both lints 0 errors; all three assembles green; instrumented suite at the exact known-12 baseline
with zero overlap; company-isolation and Timeline/Issue-consistency tests both pass with dedicated
adversarial evidence; zero P0/P1 defect; zero security/data-integrity issue.

Per this session's own final stop condition: **STOP. Do not begin MVP-1.2-D. Do not begin 1.2-E. Do
not begin MVP-1.3. Do not push. Do not install.**

Next authorized task: **MVP-1.2-D — Dincharya**, pending Product Owner/technical review of this B+C
result.

---

# Checkpoint — B/C Preservation, Git Push, and MVP-1.2-D Readiness Review

**Session type:** preservation + read-only architecture review. **No production code was written or
modified in this session.**

## CP1. Repository recovery

Verified directly, not assumed: `git status` clean; branch `main`; HEAD
`8f77cf63b7533f1418cb6208157969dee3022765` (the exact commit the prior B/C session's own final
report claimed); B commit `95ff98d` and C commit `8f77cf6` both present in `git log`; single author
(`Rajgopal Jaju <jajusri1@gmail.com>`) across all 234 commits ahead of the pre-push `origin/main`;
zero unexpected/untracked files.

## CP2. B/C source verification

Every claim in Parts B/C above was re-verified against actual source this session (not trusted
blindly): `PartyTimelineDao.kt` exists with 7 `UNION ALL` occurrences and both `issue_opened`/
`issue_resolved` arms present; `DatabaseConstants.VERSION` is still `9` with zero
`MIGRATION_9_10`; the Issues section composables (`IssuesSection`/`IssueCard`, 5 references) and
`resolveIssueTapped`/`reopenIssueTapped` (4 references) exist in the ViewModel; `issueActivitySummary`
exists on `PartyNoteDao`; company-isolation tests exist in both the instrumented Timeline test (2
matches) and the JVM repository test (5 matches, case-insensitive scan). **No discrepancy found
between the B/C reports and the actual repository.**

## CP3. Test/build baseline

Since the source tree is byte-for-byte unchanged since the last full green verification at the end
of the C session, a full re-run of the expensive instrumented suite would be ceremony, not signal.
Ran `testDebugUnitTest` + `lintDebug`: **every task reported `UP-TO-DATE`** — Gradle's own
incremental-build fingerprinting independently confirms zero drift since the last recorded
1,201/1,201 JVM / 0-lint-error / 274-of-286-instrumented (12 known baseline, zero overlap) result.
No new failure discovered; nothing needed fixing.

## CP4. Git safety check (before push)

- `git status --short`: empty (nothing uncommitted).
- Commits ahead of pre-push `origin/main`: 234, single author, all recognizable VENTURE milestone
  work (spans early architecture docs through MVP-1.2-C) — nothing unrelated or unexplained.
- Secret/credential scan across the full diff range (`git diff origin/main..HEAD`): zero matches for
  private-key headers, API-key/secret/password literal patterns, AWS-style keys, or certificate
  blocks; zero `.jks`/`.keystore`/`.p12`/`.pfx`/`.pem`/`.key` files in the diff. (Consistent with the
  already-documented fact that no signing credentials exist anywhere in this repository.)
- `.gitignore` already excludes `.env`/`.env.*`/`*.jks`/`node_modules/`.
- Branch confirmed `main`; remote confirmed `origin` → `https://github.com/jajusri/venture.git`
  (the expected repository).
- Relationship to `origin/main` before push: 234 ahead, 0 behind — a clean fast-forward, zero
  divergence, zero risk of overwriting remote-only work.

## CP5. Push result

```
git push origin main
To https://github.com/jajusri/venture.git
   7cf85ba..8f77cf6  main -> main
```

Normal fast-forward push, **no force flag used**. Post-push verification:

- Local HEAD: `8f77cf63b7533f1418cb6208157969dee3022765`
- `origin/main` HEAD (after `git fetch origin`): `8f77cf63b7533f1418cb6208157969dee3022765`
- **Exact match** — local and remote are byte-identical.
- Working tree remains clean; `git status` reports "up to date with 'origin/main'", zero divergence.
- 234 commits (previously local-only since project inception) now preserved on `origin/main`,
  spanning from `7cf85ba`'s successor through `8f77cf6`.

This push is a **recovery checkpoint only** — it does not publish VENTURE to end users, does not
submit to any app store, and does not change the public-release-blocked status (§4 above,
unchanged: no signing credentials exist).

## CP6. MVP-1.2-D — DINCHARYA READINESS REVIEW (read-only — implementation NOT started)

### CP6.1 Locked scope (reconciled against architecture doc §1/§6/§11 and PDL-014–PDL-017)

Exactly three deterministic item types, per the locked Master Plan §8 and architecture doc §6/§11:

1. **Promised-payment/callback follow-ups** — notes with `type IN ('commitment','follow_up')`,
   `dueAt IS NOT NULL`, `completedAt IS NULL`.
2. **Pending Tally XML confirmation** — `party_field_provenance` rows with
   `state = 'exported'` (awaiting a live Tally re-sync to confirm or conflict).
3. **Pending contact completion** — Parties with `primaryPhone IS NULL` and/or
   `primaryEmail IS NULL`.

A new top-level `feature/dincharya/` package, reached from a **new fourth Dashboard primary entry**
(mirrors Connect's own MVP-1.1-B addition exactly). Each row deep-links to
`Routes.partyDetail(partyId)` — never a second, parallel detail view. OI framing copy
("We are not AI. This is OI — programmed to help you") in the empty state / near the top. Explicitly
bounded, capped-count-plus-"N more" disclosure — never an infinite scroll.

### CP6.2 Data-source analysis (per item type)

| Item type | Authoritative source | Table/entity | Existing DAO/repo surface | New schema? | New query? |
|---|---|---|---|---|---|
| Follow-ups/callbacks | `PartyNote.type`/`dueAt`/`completedAt` (1.2-A) | `party_notes` | `PartyNoteDao` (no company-wide method yet) | **No** — columns already exist | **Yes** — new company-wide bounded query, first of its kind on this DAO |
| Pending Tally confirmation | `PartyFieldProvenance.state` (1.1-A/D) | `party_field_provenance` | `PartyFieldProvenanceDao` (party-scoped only today) | **No** | **Yes** — new company-wide bounded query |
| Pending contact completion | `Party.primaryPhone`/`primaryEmail` (1.1-A) | `cached_parties` | `PartyDao` (already has company-scoped list/search) | **No** | **Yes**, but closely mirrors `listByClassification`'s existing shape |

All three are local-Room-only, offline-capable by construction — no Connector/network call for any
of them (the Tally-confirmation item type reads a field VENTURE already persisted from a prior
`GET /ledgers/{id}` call in 1.1-D; it does not perform a new live Tally read).

**Confirms the architecture doc's own claim:** two of the three item types (Tally confirmation,
contact completion) need zero new columns; only the follow-up type needed new columns, and those
were already added in 1.2-A. Company-isolation for all three is `companyId`-scoped at the query
boundary (CP6.3), never left to UI-layer filtering.

### CP6.3 Company isolation — the dominant risk (architecture §13/§20 Risk #1, HIGH)

Every one of the three new DAO queries **must** take `companyId` as its first bound parameter and
filter on it directly in SQL — the same natural-key discipline every existing Party table already
uses, with zero exception. This is the first query in the whole Connect/Party feature area that
spans an entire company's Parties in one read, so unlike every 1.1/1.2-A/B/C query (single-party or
already-narrowed-by-partyId), there is no secondary narrowing to lean on — `companyId` scoping is
the *only* isolation boundary, and it must be enforced in the DAO's SQL, not filtered afterward in
Kotlin or in the ViewModel.

**Named, explicit, blocking adversarial tests required before 1.2-D can be considered
acceptance-complete** (not incidental side effects of other tests, mirroring 1.1-B's own
company-isolation test discipline and this session's own B/C precedent):

- Two companies, each with a Party of the identical display name and identical phone number, each
  with its own due follow-up — company A's Dincharya list must never show company B's item.
  Follow the exact evidence pattern established in
  `PartyRepositoryImplTest`'s `timeline never leaks another company's notes or export events, even
  with identical content` (B) and `issue lifecycle timeline entries and activity summaries never
  leak across companies` (C).
  - Identical ledger names across two companies whose provenance both happen to be `state = 'exported'`.
  - Identical/overlapping `dueAt` timestamps across two companies (proves no accidental cross-company
    merge/sort happens before the `companyId` filter is applied).
  - A Party in company A missing contact info, and a same-named Party in company B that is *not*
    missing contact info — company B's list must correctly show nothing for that Party.
  - Company-switching mid-session (the active `companyId` changes) must immediately produce a fresh,
    correctly-scoped Dincharya read, never a stale cached cross-company list.

### CP6.4 Ordering / bounding

Architecture §10 already locks the bounding UX: **grouped by the three item types** (never one
undifferentiated mixed list), each group **capped with an explicit "N more" disclosure**, never an
infinite scroll; completed/confirmed/resolved items **drop off automatically** (a query-level filter,
e.g. `completedAt IS NULL`, not a manually-curated list).

**Genuine open ambiguity, explicitly flagged rather than silently resolved (architecture §20 Risk
#2, MEDIUM, its own words: "not fully resolvable by architecture alone — needs a concrete product
decision"):** the exact staleness/aging rule for a follow-up that has been overdue for a long time —
does it stay pinned at the top indefinitely, or does its prominence decay after some threshold? The
architecture doc does not lock a specific number of days, and this session did not invent one. A
sensible, conservative, easily-changed **default** for 1.2-D's first implementation: order each
group by `dueAt ASC` (soonest/most-overdue first, a natural and honest ordering that needs no
invented "urgency score"), with a fixed cap (e.g. 20 per group, matching the `pageSize` convention
already used everywhere else in this codebase) and a "N more" link — deferring any special
decay/demotion rule until the Product Owner confirms one is actually wanted. This preserves
"deterministic, explainable, bounded" without inventing a product policy.

**Tie-breaker for deterministic ordering:** `dueAt ASC, noteId ASC` (or the equivalent natural key
per item type) — the exact same "add a secondary sort key so paging never duplicates/drops a row"
discipline this session already proved necessary and tested for the Timeline merge (B9/C's
same-timestamp tie-break tests).

### CP6.5 Performance design

- **Follow-ups query:** `party_notes` has no company-wide (only `companyId, partyId`) index today.
  A `WHERE companyId = ? AND type IN (...) AND dueAt IS NOT NULL AND completedAt IS NULL ORDER BY
  dueAt ASC LIMIT ? OFFSET ?` query can still use the existing index's `companyId` prefix to narrow
  to the company's own notes before filtering/sorting the remainder in SQLite — bounded by one
  company's total note count, never the whole database. Acceptable without a new index for realistic
  per-company scale (matching this session's own equivalent reasoning for `issueActivitySummary` in
  C, which also relies on an existing prefix index rather than adding a new one) — **but this must be
  empirically proven, not assumed:** a 500+-row large-fixture instrumented performance test
  (following `PartyDaoTest.pageByClassification_staysFastAndBoundedWithALargeFixture`'s exact
  pattern and the same sub-2-second bar) is architecture §16's own explicit acceptance requirement
  for this query specifically, and should be the deciding evidence for whether a new index (and a
  small additive `MIGRATION_9_10`) becomes justified. Do not add the index pre-emptively without
  that evidence (PDL-012).
- **Tally-confirmation query:** `party_field_provenance` has only a `(companyId, partyId)` index; a
  company-wide `state = 'exported'` filter narrows via the same prefix-match reasoning. Same
  large-fixture proof requirement.
- **Contact-completion query:** `cached_parties` already has a plain `(companyId)` index (used by
  existing `listByClassification`/`searchParties`) — a `WHERE companyId = ? AND (primaryPhone IS
  NULL OR primaryEmail IS NULL)` filter is the cheapest of the three, structurally identical to
  already-proven queries.
- **General:** `LIMIT`/`OFFSET` throughout, never an in-memory filter over a full company load
  (architecture §16's explicit instruction); no N+1 — each item type is one bounded query, not one
  query per Party.

### CP6.6 UX / accessibility

Dincharya is a **new top-level screen**, not an extension of Party Detail — it does not reuse
`PartyDetailScreen`'s composables directly, though it should reuse the same established shared
composables (`MasterDataLoadingIndicator`/`MasterDataErrorBlock`) and the same honest-empty-state
discipline (`connect_empty_customers`/`connect_empty_prospects` precedent). Each grouped section
needs its own header (mirroring `SectionHeader` from Party Detail) and each row needs a deep-link
action (mirroring the existing `Routes.partyDetail(partyId)` navigation, unchanged). No new
navigation paradigm — a fourth `HomePrimaryEntryRow` on the Dashboard, exactly like Connect's own
addition. Long Party names/note text: rely on Compose's natural text wrapping, the same discipline
already proven safe throughout B/C — no new truncation logic needed. Every interactive element
should be a labeled `TextButton`/`Card`, the same self-describing pattern used throughout
Connect/Party Detail — no icon-only controls. **Dincharya must not become**: a second Notes list, a
second Timeline, a Home Insights preview, or anything resembling an AI assistant — it is an
action-oriented, deterministic, bounded list only.

### CP6.7 Proposed file/architecture map (Room → Domain → Repository → Use Case → ViewModel → Compose, unchanged layering)

| File | New/existing | Purpose |
|---|---|---|
| `feature/party/data/local/PartyNoteDao.kt` | existing, extended | new bounded company-wide follow-up query |
| `feature/party/data/local/PartyFieldProvenanceDao.kt` | existing, extended | new bounded company-wide exported-field query |
| `feature/party/data/local/PartyDao.kt` | existing, extended | new bounded company-wide missing-contact query |
| `feature/party/domain/model/DincharyaModels.kt` | new | `DincharyaItem` sealed type (3 cases) + bounded page/summary types |
| `feature/party/domain/repository/PartyRepository.kt` / `Impl.kt` | existing, extended | `getDincharyaItems(companyId, ...)` — or a small dedicated repository if the architecture doc's "new feature/dincharya/ package" boundary is read strictly (worth confirming during implementation, not this readiness pass) |
| `feature/party/domain/usecase/PartyUseCases.kt` (or a new `DincharyaUseCases.kt`) | new/extended | `GetDincharyaItemsUseCase` |
| `feature/dincharya/presentation/DincharyaScreen.kt` | new | grouped-by-type screen, capped lists, OI copy |
| `feature/dincharya/presentation/DincharyaUiState.kt` | new | UiState/Event/Effect |
| `feature/dincharya/presentation/DincharyaViewModel.kt` | new | loads/paginates the three groups |
| `navigation/Routes.kt` | existing, extended | new `DINCHARYA` route constant |
| `navigation/VentureNavHost.kt` | existing, extended | new `composable(route = Routes.DINCHARYA)` entry |
| `feature/dashboard/presentation/DashboardUiState.kt` | existing, extended | new `OpenDincharya` event + `DashboardNavigation.Dincharya` |
| `feature/dashboard/presentation/DashboardViewModel.kt` | existing, extended | wire the new event (mirrors `OpenConnect`, `DashboardViewModel.kt:166`) |
| `feature/dashboard/presentation/DashboardScreen.kt` | existing, extended | fourth `HomePrimaryEntryRow` (mirrors `DashboardScreen.kt:369-388`) |

**No schema/migration file is expected** unless the 500-row performance proof (CP6.5) demonstrates a
genuine need for a new index — in which case the smallest possible additive `MIGRATION_9_10` (index
only, no column change) would be the correct, evidence-justified response, not a pre-emptive one.

### CP6.8 Test matrix

- **DAO:** company isolation (CP6.3, blocking); correct eligibility filter per item type
  (`completedAt IS NULL`, `state = 'exported'`, missing-contact predicate); deterministic ordering
  and tie-break; empty-company/empty-result; bounded `LIMIT`/`OFFSET` correctness across a page
  boundary (no gaps/duplicates, matching B/C's own proven pattern).
- **Repository:** correct `companyId` pass-through end-to-end; offline behavior (zero network/
  Connector import, grep-verifiable like every prior milestone).
- **ViewModel:** loading/success/empty/error states; refresh; all three item types individually and
  combined (explicitly named in architecture §17); "N more" disclosure logic; the cross-company
  leakage test repeated at this layer too, not just the DAO (matching this session's own two-layer
  isolation discipline).
- **UI:** grouped rendering per item type; deep-link-back to Party Detail; empty state honesty;
  accessibility (labeled controls); long content; dark/light theme (existing `VentureTheme`, no new
  theming work).
- **Instrumented:** the required 500+-row large-fixture performance test (CP6.5); real-device
  company-isolation proof; Dashboard entry wiring test (mirrors `DashboardViewModelTest`'s existing
  `OpenConnect` case at line 280).
- **Regression:** full MVP-1/1.1/1.2-A/B/C suite re-run clean, zero new instrumented failure beyond
  the known 12.

### CP6.9 Hardening checklist (pre-implementation risk list)

| Risk | Guard |
|---|---|
| Cross-company leakage | CP6.3 — named blocking adversarial tests at DAO, repository, and ViewModel layers |
| Incorrect due-date interpretation | Reuse 1.2-A's exact `dueAt`/`completedAt` semantics unchanged; no new date logic invented |
| Stale data | All local-Room-only reads are always current as of the last sync — no separate staleness state to track |
| Duplicate Dincharya items | Each item type is one row per source row (note/provenance/party) — no join that could fan out |
| Missing Party identity | Every item type already carries `partyId`/`companyId` from its source row — no orphan risk |
| Resolved issues resurfacing | N/A to Dincharya directly (issues aren't one of the three item types) — but a follow-up note tied to a resolved issue should still surface if still due/incomplete, since completion and issue-resolution are independent state (verify this is the intended behavior during implementation, not assumed here) |
| Completed items resurfacing | Query-level `completedAt IS NULL` filter, never a UI-only hide |
| Timestamp collisions | Deterministic secondary tie-break (CP6.4), matching B/C's proven pattern |
| Empty company | Honest empty state per item group, matching Connect's precedent |
| Company switching | Fresh query on every company change — no stale cross-company cache |
| Rotation/process recreation | Same ViewModel-scoped `StateFlow` pattern proven throughout B/C |
| Long names/notes | Compose natural wrapping, no new truncation logic |
| Offline state | Pure local Room — works identically online/offline |
| Migration compatibility | Only relevant if the performance proof justifies a new index (CP6.5) |
| N+1 regression | Three single bounded queries, not one per Party |
| Accessibility omissions | Labeled `TextButton`/`Card` throughout, same as B/C |
| False action affordances | Every action must actually be backed by working navigation/data — no placeholder buttons |

### CP6.10 Explicit scope lock (confirmed, not re-litigated)

MVP-1.2-D will **not** include, and nothing in the current codebase suggests otherwise: Referral
Tree / RJ Concept (PDL-015), Home Insights / OI dashboard beyond the framing-copy discipline
(PDL-016), generative AI of any kind, OS-level notifications (PDL-017), Vartalap, Business Profile,
Catalogue, cloud sync, direct Tally writes or automatic Tally imports, any Desktop change, any
Connector change. All confirmed unchanged/untouched this session (grep-verified in CP2, and no
source under `apps/venture_desktop` or the Connector was read or modified).

### CP6.11 Version/artifact policy (this checkpoint)

Version **not bumped**. `0.1.1-continuity.26` / versionCode 27 remains the installed candidate on
device `10BF44124K000E3`. No APK built or installed this session. The next coherent version bump
should land at an appropriate MVP-1.2 milestone boundary (most likely after 1.2-D, or at the 1.2-E
integrated-hardening freeze), not during this checkpoint.

## CP7. MVP-1.2-D READINESS REVIEW COMPLETE — IMPLEMENTATION NOT STARTED.

This record, together with the architecture document's own §10/§11/§13/§16/§17/§20, is intended to
let a completely fresh Claude session begin MVP-1.2-D directly without repeating this analysis.

---

# Part D — MVP-1.2-D: Dincharya

**Starting HEAD:** `d964b760e90339d3037a3447bbffa85e116e4801` (`docs: MVP-1.2-B/C git preservation
checkpoint + MVP-1.2-D readiness review`), working tree clean, matching this checkpoint's own
recorded HEAD exactly. `DatabaseConstants.VERSION` confirmed `9`, unchanged since 1.2-A.

## D1. Scope actually implemented

Exactly the three locked item types from CP6.1/PDL-018 — Follow-ups & Callbacks, Pending Tally
Confirmation, Pending Contact Info — surfaced on a new top-level Dincharya screen reached from a
fourth Dashboard primary entry, mirroring Connect's own MVP-1.1-B addition exactly. Every design
decision the checkpoint flagged as "worth confirming during implementation" was resolved and is
recorded here, not silently assumed:

- **Dedicated `feature/dincharya/` repository, not an extension of `PartyRepository`.** The
  checkpoint's file map left this open. `PartyRepository` already carries 24 party-scoped methods
  and five independent hand-written test fakes elsewhere in the tree
  (`ConnectViewModelTest`/`PartyXmlExportViewModelTest`/`ProspectCreateViewModelTest`/
  `ReconcilePartiesFromLedgersUseCaseTest`/`SyncViewModelTest`); Dincharya's three queries are
  genuinely company-wide, not party-scoped, so a new `DincharyaRepository`
  (`feature/dincharya/domain/repository/DincharyaRepository.kt` +
  `data/repository/DincharyaRepositoryImpl.kt`, its own `DincharyaBindModule`) composes the same
  underlying DAOs directly rather than growing `PartyRepository` and forcing five unrelated fakes to
  grow stub overrides for a concern none of them touch.
- **Pending Tally Confirmation is grouped per Party, not per pending field.** `party_field_provenance`
  can have several fields in `exported` state for one Party at once; a naive one-row-per-field query
  would show the same Party multiple times in one Dincharya group, which reads as a duplicate to a
  user even though it is technically two different fields. `PartyFieldProvenanceDao.
  pagePendingConfirmationForCompany` uses `GROUP BY companyId, partyId` with
  `GROUP_CONCAT(fieldName, ',')`, so a Party with several pending fields is exactly one Dincharya
  item, its card listing every pending field's plain-language label
  (`TallyExportFieldMapping.labelFor`).
- **No SQL JOIN across DAOs — a bounded bulk-by-id lookup instead.** The follow-up and
  pending-confirmation queries only carry `partyId`, not a display name; rather than joining
  `party_notes`/`party_field_provenance` against `cached_parties` in SQL (which would make each DAO
  no longer single-table-focused), `DincharyaRepositoryImpl` resolves display names via one new
  `PartyDao.findByIds(companyId, partyIds)` bulk lookup per group — the same "bulk read once, map in
  Kotlin" precedent `ConnectViewModel` already uses for tag/source-link enrichment, just moved into
  the repository layer. Never one query per row.
- **The two genuine open product ambiguities the checkpoint flagged are now closed by explicit
  instruction, recorded as `docs/governance/VENTURE-PRODUCT-DECISION-LOG.md` PDL-018:** contact
  completeness excludes Prospects and excludes contact-person-level detail entirely; follow-ups never
  auto-expire, with the checkpoint's own proposed conservative `dueAt ASC` default confirmed exactly
  as the locked ordering rule (it already yields overdue-first/due-today/upcoming for free, since
  those are already chronologically ordered — no separate three-way `CASE` needed).

**Explicitly not built**, confirmed untouched (grep-verified, D9): Referral Tree, Home Insights/OI
engine beyond the locked framing copy, generative AI, OS notifications/`Worker`/`POST_NOTIFICATIONS`,
Vartalap, Business Profile, Catalogue, cloud sync, any Desktop/Connector file, any new Tally
mutation/write path, any Room migration (no schema/column change was needed for any of the three
item types, confirmed by the readiness review and unchanged by implementation).

## D2. Architecture — three independent bounded queries, not a merged feed

Unlike Relationship Timeline (1.2-B/C), which genuinely needed one interleaved chronological
`UNION ALL` because notes/exports/issue-lifecycle events share one timeline, Dincharya's three item
types are architecturally required to stay **visually separate, capped groups** (architecture §10 —
"grouped by the three deterministic item types... never one undifferentiated mixed list"), so each
is its own single bounded query, never merged:

- `PartyNoteDao.pageFollowUpsForCompany`/`countFollowUpsForCompany` — `type IN
  ('commitment','follow_up') AND dueAt IS NOT NULL AND completedAt IS NULL`, `ORDER BY dueAt ASC,
  noteId ASC`, deliberately no `dueAt` lower bound (PDL-018 — no automatic expiry).
- `PartyFieldProvenanceDao.pagePendingConfirmationForCompany`/`countPendingConfirmationForCompany` —
  `state = 'exported'`, grouped per Party (D1).
- `PartyDao.pageMissingContactInfo`/`countMissingContactInfo` — `classification != 'prospect' AND
  primaryPhoneNormalized IS NULL AND (primaryEmail IS NULL OR TRIM(primaryEmail) = '')`.

Each group is fetched with a fixed cap (`DINCHARYA_DEFAULT_GROUP_LIMIT = 20`, matching this
codebase's existing `pageSize` convention) plus a separate `count*` query for the true total, so the
UI can show an honest "N more" (architecture §10's explicit bounded-disclosure requirement) — this is
a single capped fetch per group, not the infinite-scroll pagination Connect/Timeline use, since
Dincharya's own product requirement is a bounded worklist, not a browsable list.

## D3. Data model

New `feature/dincharya/domain/model/DincharyaModels.kt`: `DincharyaItem` sealed interface (three
cases — `FollowUp`/`PendingTallyConfirmation`/`PendingContactCompletion`, each carrying `partyId`/
`partyDisplayName` for the deep-link), `FollowUpUrgency` enum (`Overdue`/`DueToday`/`Upcoming` —
derived purely from comparing `dueAt` to "now" in the device's local zone, never an invented priority
score), `DincharyaGroup<T>` (bounded `items` + true `totalItems`, `moreCount` computed), and
`DincharyaSnapshot` (all three groups together, `isEmpty` convenience). Zero new Room entity, zero
new table — every field already existed on `PartyNoteEntity`/`PartyFieldProvenanceEntity`/
`PartyEntity` since 1.2-A/1.1-A/1.1-D.

New `PartyFieldProvenanceDao.kt`'s `PendingConfirmationRow` projection (`companyId`/`partyId`/
`fieldNamesCsv`/`earliestAt`) — a plain non-`@Entity` Room projection, same pattern as
`PartyTimelineDao`'s `TimelineRowEntity`/`PartyNoteDao`'s `IssueActivityRow`.

## D4. Repository / use-case API

`DincharyaRepository` (three methods, `getFollowUps`/`getPendingTallyConfirmations`/
`getPendingContactCompletions`, each `(companyId, limit)`) implemented by `DincharyaRepositoryImpl`,
injecting `PartyDao`/`PartyNoteDao`/`PartyFieldProvenanceDao`/`TimeProvider`/`DispatcherProvider`
directly — the same constructor-injection and `withContext(dispatchers.io)` IO-wrapping convention
`PartyRepositoryImpl` already uses, `TimeProvider.nowEpochMillis()` for urgency classification (never
a direct `System.currentTimeMillis()` call). `GetDincharyaSnapshotUseCase` combines all three into
one `DincharyaSnapshot` — the single entry point `DincharyaViewModel` calls, so "load Dincharya" is
always all three groups together, never a partially-loaded screen.

## D5. UI — new top-level screen + fourth Dashboard entry

`feature/dincharya/presentation/`: `DincharyaUiState`/`DincharyaEvent`/`DincharyaEffect`
(`DincharyaScreen.kt`), `DincharyaViewModel` (mirrors `ConnectViewModel`'s exact
`companySession.observeSelectedCompanyId().distinctUntilChanged()` reload-on-company-change pattern),
`DincharyaScreen`/`DincharyaRoute` (Compose). Reuses the established shared components
(`MasterDataLoadingIndicator`/`MasterDataErrorBlock`/`MasterDataUiError`) and the honest-empty-state
discipline already proven on Connect/Relationship Timeline — no new visual system introduced. One
`LazyColumn` with an always-visible OI framing line (`"We are not AI. This is OI — programmed to help
you."`, architecture §10's exact locked wording) at the top, then one section per non-empty item
type (header, capped rows, "N more" when `moreCount > 0`), each row a labeled `Card` with a combined
semantics `contentDescription` (name, plain-language reason, and body/fields), deep-linking to
`Routes.partyDetail(partyId)` on tap — never a second, parallel detail view. Plain-language reason
labels only, exactly the task's own examples ("Follow-up overdue"/"Follow-up due today"/"Follow-up
upcoming"/"Tally confirmation pending"/"Contact details incomplete") — no raw `NoteType`, DAO name, or
internal enum ever reaches the UI.

Dashboard: `DashboardEvent.OpenDincharya` → `DashboardViewModel.onEvent` → `DashboardNavigation.
Dincharya` → `DashboardScreen`'s `LaunchedEffect` collector → `onOpenDincharya()` → `VentureNavHost`'s
`navController.navigate(Routes.DINCHARYA)` — the identical five-hop wiring Connect's own
`OpenConnect`/`DashboardNavigation.Connect` already uses, replicated exactly, not reinvented. Fourth
`HomePrimaryEntryRow` (`Icons.Filled.CheckCircle`, confirmed present in the actual
`material-icons-core` runtime jar before use, not assumed) added after Connect's row in
`HomePrimaryEntries`. New simple no-arg `Routes.DINCHARYA = "dincharya"` (the `Routes.SETTINGS`-style
template, not `Routes.CONNECT`'s query-arg template, since Dincharya takes no search parameter).

**A self-caught gap, fixed before commit:** the first draft of `DincharyaScreen` only showed an error
block when there was zero existing content, silently dropping a refresh failure once the list already
had rows — inconsistent with `ConnectScreen`'s own `connect_inline_error` discipline of always stating
an error honestly even when stale content remains visible. Fixed by adding the identical inline-error
row inside the content branch.

## D6. Company isolation — the dominant risk, addressed at every layer

Per architecture §13/§20 Risk #1 and CP6.3's own naming of this as the milestone's single highest
risk, every one of the three new DAO queries binds `companyId` directly in SQL as the sole isolation
boundary (no secondary `partyId` narrowing exists for a company-wide query to lean on). Named,
explicit, blocking adversarial tests exist at every layer, not as incidental side effects of other
tests:

- **DAO (instrumented, real Room, run on device `10BF44124K000E3`):**
  `PartyNoteDaoTest.followUpsForCompanyAreCompanyIsolatedEvenWithIdenticalNoteIdsAndDueDates` (same
  `noteId`, same `dueAt`, two companies, different body text — each company's follow-up page returns
  only its own row); `PartyFieldProvenanceDaoTest.
  pendingConfirmationForCompanyIsCompanyIsolatedEvenWithIdenticalPartyIds` (identical `partyId`
  reused under two companies with different pending fields — each company's group shows only its
  own field); `PartyDaoTest.pageMissingContactInfoIsCompanyIsolatedEvenWithIdenticalNamesAndPhones`
  (identical display name across two companies, one missing contact info and one not — company B's
  list is empty for that same-named Party, not company A's data).
- **Repository (JVM, `DincharyaRepositoryImplTest`):** `follow-ups are company isolated end to end
  through the repository`, `pending confirmations are company isolated end to end through the
  repository` — proving `DincharyaRepositoryImpl`'s own display-name-resolution step (`PartyDao.
  findByIds`) is itself `companyId`-scoped, so even the enrichment join can never leak a name across
  companies.
- **ViewModel (JVM, `DincharyaViewModelTest`):** `switching companies triggers a fresh load scoped to
  the new company, never mixing data` — company A's follow-up body text is on screen, the company
  session flips to company B mid-session, and only company B's content appears, with `companyId` in
  `DincharyaUiState` updated to match.

All company-isolation tests reuse the exact adversarial pattern this session's own precedent
(1.2-B/C's `PartyTimelineDaoTest`/`PartyRepositoryImplTest`) established: identical natural keys
(name/phone/`noteId`/`partyId`/`dueAt`) deliberately reused across two companies, proving isolation
holds under a genuine identity collision, not just different-looking data.

## D7. Performance evidence

Each of the three new company-wide queries has a dedicated 500-row large-fixture instrumented
performance test, following `PartyDaoTest.pageByClassification_staysFastAndBoundedWithALargeFixture`'s
exact precedent and sub-2-second bar, all **run and passing on real hardware** (device
`10BF44124K000E3`), not JVM-simulated:

- `PartyNoteDaoTest.pageFollowUpsForCompanyStaysBoundedAndFastWithALargeFixture` — 500 eligible
  follow-up notes across 50 Parties.
- `PartyFieldProvenanceDaoTest.pagePendingConfirmationForCompanyStaysBoundedAndFastWithALargeFixture`
  — 500 Parties each with one `exported`-state field.
- `PartyDaoTest.pageMissingContactInfoStaysBoundedAndFastWithALargeFixture` — 500 Parties, half
  eligible after the Prospect-exclusion filter.

Per the readiness review's own explicit instruction (CP6.5, PDL-012), this evidence was the deciding
factor for whether a new Room index was justified — **all three stayed comfortably under the 2-second
bar using only the existing `(companyId, ...)`-prefixed indices** (`party_notes`'s
`(companyId, partyId, createdAt)`, `party_field_provenance`'s `(companyId, partyId)`,
`cached_parties`'s `(companyId)`/`(companyId, classification)`), so **no new index and no
`MIGRATION_9_10` were added** — an evidence-grounded "no" rather than a pre-emptive index.
`DatabaseConstants.VERSION` remains `9`, unchanged since 1.2-A. Every query uses `LIMIT`/`OFFSET`
throughout; the one bulk-by-id enrichment lookup (`PartyDao.findByIds`) is bounded to the current
page's distinct `partyId` set (≤20 per group), never a second full-company read; no N+1 anywhere —
each item type is exactly one bounded query plus at most one bounded enrichment query, never one
query per Party.

## D8. Offline behavior

Every Dincharya read is local-Room-only. Grep-verified zero import of any Connector/network/Retrofit/
OkHttp type anywhere under `feature/dincharya/` (same class of evidence as every prior 1.1/1.2
milestone). The "Pending Tally Confirmation" item type reads `party_field_provenance.state` — data
MVP-1.1-D's existing `GET /ledgers/{id}` re-sync path already persisted locally — Dincharya performs
no live Tally read of its own, exactly as architecture §13/§14 require.

## D9. Mini-hardening review

Performed by re-reading the actual changed production code fresh, not merely trusting the tests that
already passed:

- **Company isolation:** D6 above — the dominant focus, addressed at DAO/repository/ViewModel layers.
- **False affordances:** every row's tap action genuinely navigates to that Party's real Detail
  screen (`Routes.partyDetail`, unchanged) — no placeholder button, no action that looks live but
  does nothing.
- **Hidden network dependency:** none — D8.
- **N+1 queries:** none — D7; the one enrichment lookup is bounded, bulk, and per-group, not per-row.
- **Unsafe null handling:** a follow-up whose display-name lookup misses (structurally shouldn't
  happen — Parties are never deletable in this codebase) falls back to the raw `partyId` rather than
  dropping the item or crashing — a genuine follow-up must never silently vanish from Dincharya just
  because a display-name join missed (`DincharyaRepositoryImplTest`'s own dedicated test for this).
- **Duplicate items:** a Party with several pending Tally-confirmation fields surfaces as exactly one
  Dincharya item (D1/D2), not several; a Party can legitimately appear once in each of the three
  different groups at once (e.g., missing contact info *and* a pending confirmation) — this is not a
  duplicate, it is two independently true facts about the same Party, and the Compose `LazyColumn`
  item keys are prefixed per group (`"followup_..."`/`"confirmation_..."`/`"contact_..."`) so this
  can never produce a duplicate-key crash (`DincharyaScreenTest.
  sameCompanyPartyAppearingInTwoDifferentGroupsProducesNoDuplicateNodeKeyCrash`).
  Within one group, each source row (note/provenance-group/Party) produces exactly one item — no join
  that could fan out.
- **Accessibility omissions:** every interactive row is a labeled `Card` with a combined
  `contentDescription` (name + plain-language reason + body/fields) — the same self-describing
  pattern already audited throughout Connect/Party Detail; no icon-only or unlabeled control
  introduced; overdue urgency is stated in text first (`dincharya_reason_followup_overdue` etc.),
  with color used only as a secondary signal, never the sole channel for a state.
- **Misleading wording:** the reason strings are the task's own locked plain-language examples; the
  OI framing line is the architecture doc's own locked exact wording — nothing invented.
- **Destructive behaviour:** zero new `DELETE`/mutation statement anywhere in the three new DAO
  queries or the new repository — Dincharya is 100% read-only against already-existing tables.
- **Accidental MVP-1/1.1/1.2-A/B/C regression:** `PartyDao`/`PartyNoteDao`/`PartyFieldProvenanceDao`'s
  existing methods are untouched (only new methods appended); `PartyRepository`'s 24-method interface
  is untouched (Dincharya deliberately does not extend it, D1); the full JVM/lint/assemble/instrumented
  regression suites confirm this directly (D11).
- **Refresh-error honesty:** D5's self-caught inline-error fix — a refresh failure with existing
  content on screen is now always stated, never silently swallowed.
- **Zero Desktop/Connector/manifest/Worker/notification touch:** grep-verified this session (D1).

**Defects found and fixed:** one — the refresh-error-swallowing gap (D5/D9), caught during this
session's own review before the milestone was declared complete, not by an external report.

**No other genuine defect found.**

## D10. Tests

- **New/extended instrumented DAO tests (real Room, device `10BF44124K000E3`), 22 total:**
  `PartyDaoTest` — 6 new (missing-contact eligibility, Prospect exclusion, deterministic ordering,
  company isolation, 500-row performance, bulk `findByIds`); `PartyNoteDaoTest` — 7 new (eligible-type
  filter, `dueAt ASC` ordering including the overdue-before-upcoming proof, same-`dueAt` tie-break,
  a very-old-overdue note staying eligible forever, empty-result, company isolation, 500-row
  performance); new file `PartyFieldProvenanceDaoTest` — 9 tests (basic CRUD round-trip coverage this
  DAO never had before, per-Party grouping/`GROUP_CONCAT`, never-more-than-one-row-per-Party,
  earliest-first ordering, clears once every field leaves `exported`, company isolation, 500-row
  performance).
- **New instrumented Compose tests, 12 total:** new file `DincharyaScreenTest` — loading, error+retry,
  empty state, each of the three item types rendering + tap-to-navigate, all three groups rendered
  simultaneously, the same Party appearing in two groups at once (no crash), "N more" shown/hidden
  correctly, each `FollowUpUrgency` rendering its own reason, long Party name + long note body
  rendering without crashing.
- **New instrumented Dashboard test, 1:** `DashboardScreenTest.
  dincharyaPrimaryEntryIsShownAndNavigable` — the fourth entry row is visible and emits
  `DashboardEvent.OpenDincharya` on tap, mirroring the existing `OpenConnect` case precedent.
- **New JVM tests, 18 total:** new file `DincharyaRepositoryImplTest` — 9 tests (display-name
  resolution, urgency classification across all three states, the display-name-fallback defensive
  path, `moreCount` computation, company isolation for follow-ups and for pending confirmations,
  field-name-to-label mapping, contact-completion pass-through, all three types fetched independently
  and combined without interference); new file `DincharyaViewModelTest` — 9 tests (no-company-selected
  message state, successful combined load, genuine empty state vs. error, `moreCount` surfacing,
  repository-failure error state, retry-clears-error, refresh-without-stuck-busy-flags, item-tap
  effect, company-switch isolation at the ViewModel layer).
- **Existing-test mechanical updates (no behavior change):** `PartyRepositoryImplTest`'s own
  `FakePartyDao`/`FakePartyNoteDao`/`FakePartyFieldProvenanceDao` test doubles each needed stub
  overrides added for the three DAOs' new interface methods (`= error("unused")`, since
  `PartyRepositoryImpl` never calls them) — the same precedented mechanical-update class documented in
  every prior 1.2-A/B/C session whenever a shared DAO interface gains a method; `DashboardViewModelTest`'s
  existing `navigation events emitted` test extended with the new `OpenDincharya`/`Dincharya` case.

## D11. Full regression results

- `testDebugUnitTest`: **1,219/1,219 passing** (was 1,201 after C; +18 new this session — 9 repository
  + 9 ViewModel).
- `testReleaseUnitTest`: **1,219/1,219 passing** (full independent re-run, same suite, same count).
- `lintDebug` / `lintRelease`: **0 errors** both (confirmed via report XML `severity="Error"` count,
  not just console summary).
- `assembleDebug`, `assembleRelease` (including R8 minification/shrinking), `assembleDebugAndroidTest`:
  all `BUILD SUCCESSFUL`.
- `connectedDebugAndroidTest` on device `10BF44124K000E3` (`I2407i`/`I2407`): **309/321 passing** (was
  274/286 after C; +35 net new instrumented tests this session — 22 DAO + 12 Compose + 1 Dashboard).
  The 12 failures are byte-for-byte the same pre-existing device-viewport-artifact class documented
  since MVP-1.1-B (`DashboardScreenTest`, `DiagnosticsScreenTest`, `LedgerStatementScreenTest`,
  `SecurePairingScreenTest`, `ServerConfigScreenTest`, `SettingsScreenTest`, `SyncScreenTest`,
  `VoucherDetailsScreenTest`) — **zero overlap with any file this session touched**, confirmed by name
  against every one of the 58 Dincharya/Party-DAO/Dashboard-entry testcases in the XML report
  individually.

**A genuine environmental false alarm, investigated rather than accepted blindly, exactly the same
class already documented in Part A's own session:** the first full instrumented run this session
produced 27 failures, not the expected 12 — the 15 extras were entirely in `CompanyScreenTest` (9,
`IllegalStateException: No compose hierarchies found in the app`) and `PdfPreviewScreenTest`/
`PdfPageRendererTest` (6), two classes never in the documented baseline and never touched this
session, every extra failure carrying the identical device-artifact signature. `adb shell svc power
stayon usb` alone (Part A's original fix) was applied proactively before this run but proved
insufficient this time; investigated further rather than retried blindly — `dumpsys battery` showed
`USB powered: true` but the device's own 30-second default screen-off timeout was still in effect.
Extended it directly (`adb shell settings put system screen_off_timeout 1800000`) plus `svc power
stayon true` (not just `usb`) and a fresh wake/keyguard-dismiss, then re-ran the full suite: it
completed with exactly the documented 12-failure baseline, zero extras, confirming the additional 15
were a device screen-timeout artifact, not a code regression — recorded here per the Durable
Development Record rule rather than silently discarded.

## D12. Version / artifacts

**Version not bumped.** Per this task's explicit instruction ("D → review → E integrated hardening →
one coherent version bump/build/install") and this codebase's own consistent precedent across every
1.2-A/B/C session: `versionName`/`versionCode` remain `0.1.1-continuity.26`/`27`, unchanged from the
MVP-1.1 freeze. No APK artifact was produced or installed for distribution this session — only the
standard `assembleDebug`/`assembleRelease`/`assembleDebugAndroidTest` regression-gate builds (not
preserved as named release candidates), plus the ephemeral debug test-harness install/uninstall that
`connectedDebugAndroidTest` itself performs as part of running the instrumented suite on device
`10BF44124K000E3` — the same mechanism, at the same `versionName`/`versionCode`, every prior 1.2-A/B/C
session already used for this exact purpose. This is distinct from, and not, a deliberate
release-candidate installation for the device owner's use.

## D13. Accepted limitations (explicit)

- Pending Tally Confirmation clears only when a field genuinely leaves the `exported` state via a real
  Tally re-sync (`confirmFieldFromTally`/`reconcileExportedFieldFromTally`, both unchanged 1.1-D
  paths) — Dincharya itself performs no live Tally read and cannot force a field to clear; this is by
  design (architecture §13 — zero new Connector call), not a gap.
- The follow-up urgency boundary (`Overdue`/`DueToday`/`Upcoming`) is computed once per load against
  `TimeProvider.nowEpochMillis()` at the moment Dincharya is opened/refreshed — a follow-up due
  exactly at midnight will not silently re-label itself to "overdue" while the screen sits open
  unrefreshed; this matches every other "now"-relative computation already in this codebase (all
  computed once per load, never a live ticking clock) and is not a new risk class.
- Each Dincharya group is capped at 20 items with an honest "N more" count, never paginated further —
  a deliberate, locked product decision (architecture §10's explicit anti-infinite-scroll requirement),
  not an oversight; there is no "load more" affordance for Dincharya groups, unlike Connect/Timeline's
  paged lists.
- All Part A/B/C accepted limitations (specialist doc §A12/§B13/§C12) remain unchanged and still
  apply.

## D14. FINAL RESULT

**MVP-1.2-D COMPLETE.** Every mandatory gate item verified with evidence above: compiles clean
(production + both test source sets); full JVM regression clean both variants (1,219/1,219); both
lints 0 errors; all three assembles green including R8-minified release; company-isolation tests pass
with dedicated adversarial evidence at DAO, repository, and ViewModel layers, including identical-
natural-key collision cases; 500-row large-fixture performance proof passes comfortably under the
2-second bar for all three new queries, on real hardware, with no new index added (evidence said none
was needed); instrumented suite at the exact known-12 device-viewport baseline with zero overlap,
confirmed by an investigated-and-resolved retry rather than blind acceptance; zero P0/P1 defect (one
self-caught P3 refresh-error-honesty gap, fixed before commit); zero security/data-integrity issue;
zero accidental scope expansion (no Referral Tree, Home Insights engine, AI ranking, OS notification,
contact-person task, or Desktop/Connector touch anywhere in this session's diff); zero architecture
contradiction; PDL-018 records every implementation-level product decision this milestone needed.

Per this task's own explicit governing instruction: **STOP. Do not begin MVP-1.2-E. Do not begin
MVP-1.3. Do not push. Do not install a release-candidate APK.**

Next authorized task: **MVP-1.2-E — integrated MVP-1.2 hardening, full regression, migration
re-verification, accessibility pass, freeze** (architecture §11's own 1.2-E line item), pending
Product Owner/technical review of this Part D result.

---

# Part E — MVP-1.2-E: Integrated Hardening, Freeze, Final Candidate

**Starting HEAD:** `57a27e44f813aede9e5b71dd5a43440380ed670c` (`docs: record MVP-1.2-D Dincharya
completion (Part D, PDL-018, ledger phase 31)`), working tree clean — Part D's own final HEAD,
confirmed by direct inspection (`git status`, `git log`, `DatabaseConstants.VERSION`, presence of
all 8 `feature/dincharya/` files) rather than trusted from documentation alone.

## E1. Scope actually performed

Exactly this task's own definition: **not a new feature** — an integrated audit of Parts A–D working
together, defect-fixing, migration/accessibility/company-isolation/performance re-verification, one
coherent version bump, a final debug candidate built and installed on the authorized owner device, a
real-device smoke test, and the MVP-1.2 freeze. No Referral Tree, Home Insights, generative AI, cloud
sync, OS notifications, Desktop, Connector, Tally-mutation, signing, or Play Store work — grep- and
diff-verified untouched (E9).

## E2. Integrated functional audit — one genuine defect found and fixed

Read Parts A–D's actual current source together (not per-part in isolation) looking specifically for
interaction gaps between them. Found one real, consequential gap:

**`SetNoteCompletionUseCase` — implemented and tested at the repository/use-case layer since
MVP-1.2-A (architecture §22 item 4's own note: "the capability exists for 1.2-B/C/D to wire, not
fabricated as a UI affordance ahead of when it's actually needed") — had zero UI caller anywhere in
the app through Parts B, C, and D.** Grep-verified: zero reference to `setNoteCompletion`/
`SetNoteCompletionUseCase`/`completedAt` anywhere in `feature/connect/presentation/` before this
session. Consequence: Dincharya's entire "Follow-ups & Callbacks" group (1.2-D's own Type A) showed
real, live-queried overdue/due-today/upcoming items that a user had **no honest way to complete** —
only the indirect, undiscoverable side effect of editing a note's due date to blank or changing its
type away from `commitment`/`follow_up`. This directly undermines Dincharya's own stated purpose
("action-focused operational worklist") and is exactly the class of false-affordance/incomplete-loop
defect this milestone's own audit brief (§8) asks for.

**Fixed:** added `PartyDetailEvent.MarkNoteDoneTapped`/`ReopenNoteTapped`, wired to the
already-existing `SetNoteCompletionUseCase` via a new `setNoteCompletionTapped` ViewModel handler
(partial `loadTimeline()` refresh only, mirroring `resolveIssueTapped`/`reopenIssueTapped`'s own
efficiency principle — completion never changes fields/contacts/tags/issues, only how this one note
reads). UI: `NoteRow` gained a due-date/completion status line and a Mark done/Reopen `TextButton`
for `commitment`/`follow_up` notes with a due date — **mirrors the existing Issue Resolve/Reopen
`TextButton` pattern exactly, no new UI pattern introduced.** A `TimeProvider` (already Hilt-bound,
already used identically by `PartyRepositoryImpl`) was added to `PartyDetailViewModel`'s constructor
to supply `completedAt`'s timestamp — never a direct `System.currentTimeMillis()` call, consistent
with this codebase's one time-source convention throughout.

**A second, smaller issue self-caught while implementing the fix, corrected before commit:** the
first draft colored the due-date/status line `MaterialTheme.colorScheme.error` for every incomplete
follow-up, regardless of whether it was actually overdue. Since this row has no access to "now"
(computed once at the ViewModel/repository layer everywhere else in this codebase, never re-derived
at Compose render time — the same discipline Dincharya's own `FollowUpUrgency` classification
follows), coloring every *incomplete* row red would be a false urgency signal for a note not yet
due. Fixed to a neutral color; the text ("Due <date>" / "Done · due <date>") states the fact plainly,
color is not used to imply urgency this row cannot honestly compute.

**No other genuine defect found** in the integrated read of Parts A–D's data model, repository
boundaries, Timeline/Issues/Dincharya live-read construction (structurally cannot drift — all three
read the same tables directly, never a cached/duplicated copy, confirmed unchanged since B/C), or
navigation wiring.

## E3. Company isolation — re-verified, not re-invented

Every adversarial company-isolation test from Parts A–D (DAO, repository, and ViewModel layers, for
notes/issues/Timeline/Dincharya's three item types) was re-run as part of the full regression below
and passed unchanged. No new cross-party query was added in E (the note-completion fix only adds a
single-note, already-`companyId`-scoped write, the same natural-key discipline every note write in
this codebase already uses), so no new isolation surface exists to test. Re-verified rather than
assumed: `AppDatabaseMigrationTest` (8/8) and every test whose name signals company isolation across
the instrumented suite passed in the same clean run as everything else (E7).

## E4. Migration verification

`DatabaseConstants.VERSION` remains **9** — E added no column, no table, no index. `AppDatabaseMigrationTest`'s
full suite (8 tests, `MIGRATION_1_2` through `MIGRATION_8_9`) re-run and passing on real hardware,
including `migrate8To9_preservesExistingRowsAndAddsTypedNotesAndPartyIssuesTableOnly` — the exact
same real `MigrationTestHelper`-driven proof from 1.2-A, unchanged and still green. No migration
work was needed or performed.

## E5. Accessibility audit

Reviewed `PartyDetailScreen.kt` (Timeline/Issues/Notes), `DincharyaScreen.kt`, and `DashboardScreen.kt`
fresh, together, for the specific items this task names: every interactive element in the touched and
adjacent surfaces remains a labeled `TextButton`/`Card`/`Checkbox` with a visible `Text` child or an
explicit `semantics { contentDescription = ... }` — the same self-describing pattern already audited
in MVP-1.1-E and every 1.2 sub-milestone since; zero icon-only or unlabeled control anywhere in the
touched files. The one genuine finding was the color-alone false-urgency issue (E2), self-caught and
fixed before commit — not left for an external report. No other accessibility defect found; no UI
redesign performed (per this task's own explicit "do not redesign the UI unnecessarily" boundary).

## E6. UX / false-affordance audit

The dominant finding is E2 (a *missing* affordance, the inverse of a false one — Dincharya implied an
action loop that had no real "done" button). Checked this task's own named false-affordance
categories directly: edit controls that cannot persist (none found — every dialog's Save path writes
through the same repository methods its Load path reads); actions available for Prospects that
require accounting identity (none — `hasAccountingLink`/source-link gating unchanged and still
correctly excludes Prospects from View Ledger/View Vouchers/Export to Tally); stale actions after a
state transition (Mark done ↔ Reopen correctly flips immediately after `loadTimeline()`'s partial
refresh, proven by the new instrumented tests — E8); silently-failing actions (none — every new/
existing note/issue action is a direct, unguarded repository call, the same no-try/catch convention
already used throughout `PartyDetailViewModel`, since local Room writes are not expected to fail
under normal operation); controls enabled when data is unavailable (Mark done/Reopen only render
when `note.type.showsDueDate() && note.dueAt != null`, so the action is never shown without the data
it needs).

## E7. Offline / failure / state audit

Re-confirmed rather than re-tested from scratch, since no source change in E touches any of these
paths beyond the single new note-completion write: Party/Timeline/Issues/Dincharya remain
local-Room-only (E2's fix is itself a local write, zero network/Connector import — grep-verified);
loading/empty/error states unchanged; a refresh failure with existing content still shows Dincharya's
own inline error (D5's fix, unaffected by E); rotation/navigation-away-and-back state survival
unchanged (`PartyDetailViewModel`'s new `timeProvider` field is a plain constructor-injected
singleton, not additional mutable state to survive); company switching unaffected (E introduces no
new company-scoped read). **One consistency point checked and confirmed not a regression:** neither
Dincharya nor Connect auto-reloads on returning from Party Detail after an edit (both rely on the
user's own pull-to-refresh) — grep-verified zero `repeatOnLifecycle`/`LifecycleResumeEffect`/
`DisposableEffect` in `ConnectScreen.kt` either, confirming this is this codebase's existing,
consistent, already-accepted pattern, not something Dincharya introduced or regressed.

## E8. Tests

- **New JVM tests (2):** `PartyDetailViewModelTest` — `marking a follow-up note done sets
  completedAt to now, preserving the note`, `reopening a completed follow-up note clears
  completedAt`.
- **New instrumented tests (3), run and passing on device `10BF44124K000E3`:** `PartyDetailScreenTest`
  — `anIncompleteFollowUpNoteShowsMarkDoneAndEmitsMarkNoteDoneTapped`,
  `aCompletedFollowUpNoteShowsReopenAndEmitsReopenNoteTapped`,
  `aGeneralNoteWithNoDueDateShowsNeitherDoneNorReopenAction` (proving the action is genuinely absent,
  not merely disabled, when there is no due date to act on).
- **Existing-test mechanical update:** `PartyDetailViewModelTest`'s own `createViewModel` helper
  gained `setNoteCompletion`/`timeProvider` constructor arguments (the fake `InMemoryPartyRepository`
  already implemented `setNoteCompletion` since 1.2-A's own test coverage, so no fake needed
  extending — only the constructor call site).

## E9. Scope verification

Grep-verified zero touch anywhere in this session's diff to: `apps/venture_desktop`, the Connector,
`AndroidManifest.xml`, any `Worker` class, notification permission/channel code, Referral Tree, Home
Insights, generative AI, Vartalap, Business Profile, Catalogue, cloud sync, Tally mutation/write
paths, signing configuration, Play Store metadata. E's entire diff is 6 files: three production
(`PartyDetailUiState.kt`/`PartyDetailViewModel.kt`/`PartyDetailScreen.kt`), two test
(`PartyDetailViewModelTest.kt`/`PartyDetailScreenTest.kt`), and `build.gradle.kts`'s version bump.

## E10. Full regression results

- `testDebugUnitTest` / `testReleaseUnitTest`: **1,221/1,221 passing** both (was 1,219 after D; +2
  new this session).
- `lintDebug` / `lintRelease`: **0 errors** both (report-XML-verified, not console summary).
- `assembleDebug`, `assembleRelease` (R8-minified/shrunk), `assembleDebugAndroidTest`: all
  `BUILD SUCCESSFUL`.
- `connectedDebugAndroidTest` on device `10BF44124K000E3` (`I2407i`/`I2407`): **312/324 passing**
  (was 309/321 after D; +3 net new instrumented tests). The 12 failures are byte-for-byte the same
  pre-existing device-viewport-artifact class documented since MVP-1.1-B (`DashboardScreenTest`,
  `DiagnosticsScreenTest`, `LedgerStatementScreenTest`, `SecurePairingScreenTest`,
  `ServerConfigScreenTest`, `SettingsScreenTest`, `SyncScreenTest`, `VoucherDetailsScreenTest`) —
  **zero overlap** with any file this session touched, confirmed individually: all 35
  `PartyDetailScreenTest` cases passed (including the 3 new ones), all 8 `AppDatabaseMigrationTest`
  cases passed. The device stay-awake fix (extended screen timeout + `svc power stayon true`,
  learned during Part D's own investigation) was applied proactively before this run — it completed
  cleanly at exactly the known 12-failure baseline on the **first attempt**, no repeat investigation
  needed this time.

## E11. The known 12-failure baseline — explained, not merely cited

Every one of the 12 failing instrumented tests belongs to the same documented class first identified
in MVP-1.1-B and re-confirmed identically in every 1.2 sub-milestone since: `DashboardScreenTest` (2),
`DiagnosticsScreenTest` (1), `LedgerStatementScreenTest` (2), `SecurePairingScreenTest` (1),
`ServerConfigScreenTest` (2), `SettingsScreenTest` (2), `SyncScreenTest` (1), `VoucherDetailsScreenTest`
(1) — a device-viewport/timing artifact specific to this physical device's screen geometry and
instrumentation timing under `createAndroidComposeRule`, not a code defect: the same 12 tests fail
identically whether or not the files near them are touched, and the failure signatures (e.g.
`AssertionError: The component is not displayed!`, `Can't scroll to index 5, it is out of bounds`)
are layout/timing assertions on screens never touched by any 1.2-A through E commit. Re-confirmed
this session with zero overlap against the 6 files E actually changed.

## E12. Performance

No new query was added in E (E2's fix is a single-row `UPDATE`-equivalent write via the existing
`upsert`-by-natural-key `PartyNoteDao`/`PartyRepositoryImpl` path, not a new read). The three
Dincharya large-fixture performance proofs from Part D (D7) are unaffected by E and were re-confirmed
passing in the same instrumented run (E10) — not re-benchmarked from scratch, since nothing that
could change their cost was touched.

## E13. Version

**Bumped, the single coherent MVP-1.2 freeze bump, only after full A–D+E regression was confirmed
green (E10):** `versionCode` 27→**28**, `versionName` `0.1.1-continuity.26`→**`0.1.1-continuity.27`**.
Confirmed via direct inspection of the actual built APKs (`aapt dump badging`), not merely the source
config:
- Debug: `package: name='com.jajusri.venture.debug' versionCode='28' versionName='0.1.1-continuity.27'`, `application-debuggable`.
- Release: `package: name='com.jajusri.venture' versionCode='28' versionName='0.1.1-continuity.27'` (no debug suffix, not debuggable, unsigned as expected — no signing credentials exist anywhere in this repository, unchanged blocker).

## E14. Final APK artifacts

- **Debug:** `apps/venture_android/app/build/outputs/apk/debug/app-debug.apk` — 14,470,132 bytes —
  SHA-256 `92a38394e5eef4cee65eae4ba6caaef86b5e430dfccf22973aa1401362fe9d02`.
- **Release (unsigned, verification only — never a public-release artifact):**
  `apps/venture_android/app/build/outputs/apk/release/app-release-unsigned.apk` — 2,509,604 bytes —
  SHA-256 `ad11110475ef9e887afd642ef28c06f016a4f48fcf30b819456ab7aa1fee4c92`.

## E15. Device installation

**Device:** `10BF44124K000E3` (`I2407i`/`I2407`) — the same authorized owner device used throughout
MVP-1.1/1.2, confirmed connected and authorized via `adb devices -l` before any action. **Pre-install
state:** confirmed via `pm list packages`/`pm path` that **zero VENTURE package of any kind was
installed** (the Part D finding — the previously-documented `continuity.26` install had been removed,
most likely by `connectedDebugAndroidTest`'s own default post-test uninstall behavior; see
`docs/status/VENTURE-CURRENT-DEVELOPMENT-STATUS.md` §5 for full detail). **Install method:**
`adb install -r <debug apk>` — the safest available method (non-destructive flag even though there
was nothing to preserve this time); no prior uninstall, no data clear, no pairing/company-state
wipe performed (there was none to wipe). **Result:** `Performing Streamed Install / Success`.
**Confirmed installed:** `dumpsys package com.jajusri.venture.debug` reports `versionCode=28`,
`versionName=0.1.1-continuity.27`, `firstInstallTime == lastUpdateTime` (a genuine fresh install,
consistent with the pre-install state).

## E16. Smoke test — exactly what was and was not exercised on real hardware

Performed directly on device `10BF44124K000E3` after install:

1. **Launch:** `am start`/monkey-launcher intent → `MainActivity` became `mFocusedApp` within
   seconds. **Verified on device.**
2. **Expected first/current state:** rendered the **Secure Pairing** screen — the correct, honest
   state for a device with no prior pairing/company data (confirmed by the pre-install package
   check, E15) — screenshot-confirmed clean render matching the established dark theme, no visual
   defect. **Verified on device.**
3. **No fatal crash:** `logcat -d` scanned for `FATAL EXCEPTION`/`AndroidRuntime:` entries
   attributable to the app across the entire launch — **zero matches**. **Verified on device.**
4. **Back navigation:** pressing Back from Secure Pairing (the app's root/start destination, no
   back stack beneath it) correctly returned to the home launcher — standard Android root-activity
   behavior, not a crash; the app process remained alive in the background (`pidof` confirmed),
   confirming this was normal navigation, not a process death. **Verified on device.**
5. **Relaunch:** `am start` a second time brought `MainActivity` back to `mFocusedApp` cleanly.
   **Verified on device.**
6. **Existing company/pairing state preserved:** not applicable — there was none to preserve
   (fresh install, E15).
7. **Dashboard, Connect entry, Dincharya entry, Party Detail, Timeline, Issues, offline-capable
   surfaces with real data:** **NOT exercised on this device.** This environment has no real paired
   Tally Connector to complete Secure Pairing against, and per this task's own explicit instruction
   ("do not fabricate successful Tally behavior if a real paired Tally connection is unavailable"),
   no attempt was made to fake or bypass pairing to reach these screens. These surfaces are
   **verified through automated instrumented Compose tests only** (E8/E10 — real Room/real Compose
   on this same device, synthetic state, not the live app flow) — a distinct and weaker form of
   evidence than genuine on-device navigation, stated as such rather than conflated with it.

## E17. Git

Commits this session: `7d6a931` (`fix(android)`, E2's note-completion UI wiring), `ffb9d99`
(`test(android)`, E8's new tests plus the E13 version bump), `d9af6f7` (`docs`, this Part E record
plus the Development Ledger phase 32/Current-Development-Status updates). `git status` clean before
and after each commit; a dedicated secret/credential scan across the full `origin/main..HEAD` diff
range (private-key headers, API-key/secret/password literal patterns, AWS-style keys, certificate
blocks, `.jks`/`.keystore`/`.p12`/`.pfx`/`.pem`/`.key`/`.env` files) found zero matches; the full
36-file diff-range file list was inspected directly and contains only expected MVP-1.2-D/E files —
no unrelated file, no generated artifact (build outputs remain correctly `.gitignore`d, verified via
`git check-ignore`).

**Pushed.** `git push origin main` completed as a normal fast-forward, no force flag, no history
rewrite: `d964b76..d9af6f7 main -> main`. Post-push verification via `git fetch origin` +
`git rev-parse`: local HEAD and `origin/main` both resolved to
`d9af6f7623f66d8d028d3ee61d2d3fa79a91ceb9` — exact byte-identical match; `git status` reported "up
to date with 'origin/main'". Full confirmation recorded in
`docs/status/VENTURE-CURRENT-DEVELOPMENT-STATUS.md` §2a.

## E18. Accepted limitations (explicit)

- The device install gap discovered in Part D (now resolved by this session's own install, E15) is
  a reminder that `connectedDebugAndroidTest` will remove whatever debug build is on this device
  every time it runs — a structural property of this project's chosen test-gate mechanism on a real
  persistent-install device, not something E "fixed" at the tooling level. Future sessions should
  expect the same and plan accordingly (reinstall as the final step, exactly as this session did).
- Dincharya/Connect's "no auto-reload on return from Party Detail" (E7) remains an accepted,
  consistent, pre-existing UX characteristic across this codebase, not unique to any one screen —
  not addressed here since fixing it everywhere at once would be a genuine new feature (a lifecycle-
  reload pattern that exists nowhere in this codebase today), out of this hardening milestone's
  scope.
- All Part A/B/C/D accepted limitations (specialist doc §A12/§B13/§C12/§D13) remain unchanged and
  still apply — none were revisited or reopened by E.

## E19. MVP-1.2 FREEZE RESULT

**MVP-1.2 COMPLETE / FROZEN.** Every freeze criterion from this task's own §20 verified with
evidence above: A–D functionality intact (E2's fix is additive, nothing removed or altered);
integrated hardening complete (E2–E9); no unresolved new correctness/security/data-integrity defect
(the one defect found was fixed, E2); company isolation proven (E3); migration verified (E4);
accessibility pass complete (E5); regression gates green apart from the documented historical
12-device-viewport baseline (E10/E11); final APK builds (E14); final APK installs (E15); launch
succeeds (E16); smoke test succeeds to the extent a real device without Tally pairing allows, with
the exact boundary of what was and was not verified on-device stated honestly (E16); documentation
complete (this record, plus the Development Ledger and Current Development Status); Git clean with
coherent commits (E17); push result recorded separately, not assumed.

Per this task's own explicit final stop condition: **STOP. Do not begin MVP-1.3. Do not modify
Desktop. Do not begin public signing. Do not publish anything.**

Next authorized task: **MVP-1.3 planning/recovery review** — read-only reconnaissance only, per this
task's own instruction not to begin MVP-1.3 substantively.
