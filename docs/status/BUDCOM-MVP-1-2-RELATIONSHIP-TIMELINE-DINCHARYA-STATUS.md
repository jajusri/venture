# BUDCOM MVP-1.2 — Relationship Timeline, Issue History, Dincharya & OI — Status

**Status:** Part A complete. Part B complete — acceptance gate PASSED. Part C complete. STOP per
this session's own governing prompt — MVP-1.2-D not started.
**Companion documents:** `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` (concise current-state
checkpoint), `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` (chronological record),
`docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md` (locked
architecture baseline), `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-014–PDL-017 (the four
locked product decisions this milestone builds on).

---

# Part A — MVP-1.2-A: Structured Party Activity Foundation

**Starting HEAD:** `ea6869caf71ff6a07406b9d15dbb0b68c7bcb5a2` (`docs: MVP-1.2 planning, recovery &
architecture review`), working tree clean.

## A1. Product decisions locked (session prerequisite, before implementation)

The four open product questions from the 2026-08-17 architecture session were explicitly answered
by the Product Owner this session (2026-08-18) and recorded permanently as
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-014 through PDL-017:

- **PDL-014** — Relationship Timeline is the unified historical presentation for a Party (Party =
  the relationship, Timeline = the history). Confirms the architecture doc's own working
  assumption exactly.
- **PDL-015** — Referral Tree / RJ Concept is explicitly outside MVP-1.2, deferred to a later
  dedicated milestone.
- **PDL-016** — Home Insights / OI system remains outside MVP-1.2; Dincharya is self-contained.
- **PDL-017** — OS-level notifications deferred; Dincharya v1 is in-app only.

All four confirmed the architecture document's own recommendations — no architecture rework was
needed, only converting "working assumption" language to "locked" (architecture doc §3, §1 header
updated accordingly). `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` §1/§3 updated to match.

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
changes. Verified: zero files under `apps/budcom_desktop` or the Connector changed; zero
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

Ran a real debug build (`kspDebugKotlin`) to generate `app/schemas/com.budcom.android.core.database.AppDatabase/9.json`
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
- **Privacy / sensitive logging:** note bodies and issue titles are user-authored BUDCOM-only text,
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
