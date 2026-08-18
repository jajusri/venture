# BUDCOM MVP-1.2 — Relationship Timeline, Issue History, Dincharya & OI — Status

**Status:** Part A technically complete, ready for review.
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

## A13. Exact NEXT TASK

**MVP-1.2-A is complete, mini-hardened, and documented. STOP per this task's own instruction — do
not begin MVP-1.2-B in this session.**

Next: **MVP-1.2-B — Relationship Timeline** (architecture doc §11, §18) — the merged notes +
export-events chronological read model, replacing the current flat Notes list's presentation on
Party Detail, per the now-locked PDL-014. Awaits Product Owner/technical review of this 1.2-A
result before being authorized.
