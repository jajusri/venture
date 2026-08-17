# BUDCOM MVP-1.2 — Relationship Timeline, Issue History, Dincharya & OI Architecture

**Status:** PLANNING COMPLETE — implementation not started, per explicit instruction
**Produced:** 2026-08-17, in a dedicated planning/recovery/architecture session following MVP-1.1's freeze
**Governance:** `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`
**Primary planning authority:** `docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` §8 (locked scope title and objective)
**Supporting authority:** `docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md` §20/§23/§24 (the only repository document that defines what these four terms actually mean and where they sit in the UI)
**Frozen foundation this builds on:** MVP-1 through MVP-1.1-E, `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md`, HEAD `ad32f9a`

This document is MVP-1.2's equivalent of `BUDCOM-MVP-1-1-CONNECT-UNIVERSAL-PARTY-ARCHITECTURE.md` — a
pre-implementation architecture/scope baseline, written and reconciled against actual repository
evidence before any code is touched. Implementation should not begin until the open questions in
§3 are resolved by the Product Owner (Brainstorm 1), per this project's own governance.

---

## 1. Executive summary

MVP-1.1 gave every Party a flat, single-type Notes/Activity list. MVP-1.2's job is to turn that raw
material — plus data BUDCOM already has but doesn't yet surface (unconfirmed Tally exports, parties
missing basic contact info) — into **organized, actionable operational memory**, so a shop owner
never forgets a promise, a disputed short-shipment, or a pending confirmation, without needing CRM
training.

Per the locked Master Product Execution Plan §8, MVP-1.2 has three concrete deliverables and one
framing principle:

1. **Relationship Timeline** — "What happened." A chronological, readable history per party.
2. **Issue History** — "A grouped history of a continuing problem/issue," so ten related notes read
   as one tracked issue, not ten unrelated rows.
3. **Dincharya** — "Actions the user has undertaken and must follow through": promised-payment
   follow-ups, callbacks, pending contact completion, and pending Tally XML confirmation.
4. **OI (Operational Intelligence)**, positioned explicitly as *"We are not AI. This is OI —
   programmed to help you"* — not a fourth feature, but a discipline applied to Dincharya:
   deterministic, explainable, rule-based surfacing of data that already exists. No generative AI,
   no black-box scoring.

**This is a 100% Android feature.** Repository evidence (§4, §7) shows Connect/Party has no Desktop
or Connector surface at all — Desktop never implemented Connect, and the Connector remains a
read-only Tally boundary unrelated to relationship/notes data. MVP-1.2 requires zero Connector API
change and zero Desktop work.

---

## 2. Current baseline (repository evidence, this session)

- Branch: `main`, HEAD `ad32f9a` ("feat(android): MVP-1.1-E integrated hardening, acceptance &
  freeze — MVP-1.1 FROZEN"), working tree clean, 230 commits ahead of `origin/main`, not pushed.
- **MVP-1 status:** Controlled-Pilot validated, GO. Public release blocked on signing only
  (unrelated to MVP-1.2).
- **MVP-1.1 status:** COMPLETE / FROZEN (A through E). Android `0.1.1-continuity.26`, versionCode
  27, installed on the owner's device (`10BF44124K000E3`) this session as a fresh install
  (Secure Pairing screen confirmed reachable, app launches cleanly, no crash).
- **Desktop:** `0.4.18`, unrelated to Connect/Party, untouched since MVP-1.1 began.
- **Connector:** `0.4.6`, 100% read-only Tally boundary; the one Party-relevant capability
  (`GET /ledgers/{id}`) already exists and is already consumed by MVP-1.1-D — no new endpoint
  needed for MVP-1.2.
- **Room schema:** version 8 (`app/schemas/.../8.json`). Party-relevant tables as of this baseline:
  `cached_parties`, `party_source_links`, `party_field_provenance`, `party_contact_persons`,
  `party_tags`, `party_tag_assignments`, `party_notes`, `party_export_events`.
- **`party_notes` current shape** (verified by direct read of `PartyEntities.kt`/`PartyModels.kt`,
  not assumed): `companyId`, `noteId`, `partyId`, `body`, `linkedVoucherId`, `createdAt`,
  `updatedAt`. **Flat and single-type** — no `type`, no `status`, no due date, no grouping key of
  any kind. This is the exact gap MVP-1.2 must close.
- **No reminder/notification infrastructure exists.** `AndroidManifest.xml` declares no
  `POST_NOTIFICATIONS` permission and defines no notification channel. WorkManager is wired at the
  Hilt/`Configuration` level (default initializer explicitly disabled in favor of a custom one) but
  **zero `Worker` classes exist anywhere in the app** — the plumbing is present, unused. Any
  OS-level reminder capability in MVP-1.2 would be the first real use of this plumbing, not a
  trivial extension.
- **Known accepted limitations carried forward unchanged (not MVP-1.2's concern):** TD-009/021/
  022/025/027 (Desktop/Connector-side), the 12 pre-existing instrumented device-viewport failures,
  missing production signing credentials, undecided Android `applicationId`.
- **No dedicated "Insights" specification exists anywhere in the repository.**
  `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md` §4 explicitly locks only the Home Insights
  *navigation shape* (header/paging contract) and explicitly states detailed Insights/OI/
  value-attribution content "belongs to the dedicated Insights workstream" — a workstream that, per
  repository-wide search, has not yet been written down anywhere. This is a real specification gap,
  not an oversight on this session's part (§3, §8).

---

## 3. Open questions — resolve before implementation (Brainstorm 1 gate)

Per `POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`'s own clarification-gate rule (PDL-007), these are
genuine ambiguities this planning session found no locked repository answer for. They should go to
the Product Owner/ChatGPT Brainstorm 1 pass before MVP-1.2-A begins — proceeding without an answer
risks building the wrong thing once, which costs more than asking once.

1. **Does the Relationship Timeline replace the existing flat "Notes/Activity" section on Party
   Detail, or does it sit alongside it as a new, separate view?** This plan's working assumption
   (§9) is that Timeline *becomes* the primary read surface for the same underlying data (notes +
   export events, better organized) rather than a duplicate — but this is a presentation choice
   the Product Owner should confirm, not one this session should lock unilaterally.
2. **Is Referral Tree part of MVP-1.2, a later milestone, or genuinely shelved?** The referral spec
   (`BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md`) calls it "a first-class Party
   capability," "VVIMP," with 6 dedicated sections (§14–§19) — but the **locked** Master Product
   Execution Plan's MVP-1.2 line item (§8) names only Relationship Timeline/Issue History/
   Dincharya/OI and never mentions Referral Tree. This plan treats Referral Tree as **explicitly
   out of MVP-1.2 scope** (§6) because the locked sequencing document is silent on it, not because
   it was found unimportant — a real product-sequencing decision is needed, not a Claude judgment
   call, per this task's own "do not silently broaden MVP-1.2" instruction.
3. **What should Home Insights (the separate, not-yet-specified workstream) actually be, and does
   any part of it belong in MVP-1.2's Dincharya, or is it entirely a later cross-cutting effort?**
   This plan assumes Dincharya is self-contained and does **not** wait for or depend on the Home
   Insights workstream (§6) — but the Product Owner may see them as more entangled than repository
   evidence currently shows.
4. **Should Dincharya support OS-level notifications in its first release, or is an in-app list
   sufficient for v1?** This plan recommends deferring OS notifications entirely (§6, §11) given
   the complete absence of existing notification infrastructure and the meaningful new
   permission/security surface (`POST_NOTIFICATIONS`, API 33+) that would open — but this is a
   product trade-off (immediacy vs. scope discipline), not a purely technical one.

---

## 4. Plan vs. repository reconciliation matrix

| Planned capability (Master Plan §8 / Referral Spec §23) | Already implemented | Partial | Not implemented | Existing foundation | Dependency | MVP-1.2 relevance |
|---|---|---|---|---|---|---|
| Party stable identity, company isolation | ✅ | | | `Party`, `PartySourceLink` (1.1-A) | — | Reused as-is, no change |
| Field provenance (Tally-confirmed vs. BUDCOM-pending) | ✅ | | | `PartyFieldProvenance` (1.1-A/D) | — | Reused as Dincharya's "pending Tally confirmation" source, zero new schema |
| Notes/Activity (flat, single-type, voucher-linkable) | ✅ | | | `party_notes` (1.1-C) | — | Extended, not replaced (§9) |
| Export audit trail (field-names-only) | ✅ | | | `party_export_events` (1.1-D) | — | Merged into Relationship Timeline read model |
| **Note typing** (payment issue / complaint / delivery / commitment / product interest / internal remark / follow-up) | | | ❌ | `party_notes.body` (free text only) | 1.2-A | Core MVP-1.2-A deliverable |
| **Relationship Timeline** ("what happened," chronological) | | ⚠️ (data exists, no unified view) | | Notes + export events both queryable today | 1.2-A, 1.2-B | Core MVP-1.2-B deliverable |
| **Issue History** (grouped continuing problem) | | | ❌ | Nothing — no grouping concept exists | 1.2-A | Core MVP-1.2-C deliverable, new `party_issues` table |
| **Dincharya** (actionable follow-ups) | | | ❌ | Nothing — no due-date/completion concept exists | 1.2-A | Core MVP-1.2-D deliverable |
| — pending Tally XML confirmation item type | | ⚠️ (data exists, not surfaced cross-party) | | `party_field_provenance.state = Exported` | 1.2-D only | Zero new schema — a bounded cross-party query |
| — pending contact completion item type | | ⚠️ (data exists, not surfaced) | | `Party.primaryPhone`/`primaryEmail` nullability | 1.2-D only | Zero new schema — a bounded cross-party query |
| — promised-payment/callback follow-ups | | | ❌ | None | 1.2-A (`dueAt`/`completedAt`) | Needs new note columns |
| OI positioning/voice | | | ❌ (no copy exists yet) | N/A | 1.2-D | Copy/framing discipline, not a technical capability |
| Home Insights dashboard | | | ❌ | Nav shape locked, content unspecified anywhere | Undefined | **Out of MVP-1.2 scope** (§3.3, §6) |
| Referral Tree | | | ❌ | Party identity anchor exists; tree/graph does not | Undefined | **Out of MVP-1.2 scope pending product decision** (§3.2, §6) |
| Vartalap integration | | | ❌ | No milestone assigned anywhere (Screen Inventory confirms) | N/A | Out of scope |
| Business Profile / Catalogue | | | ❌ | MVP-1.3/1.4, unrelated | N/A | Out of scope |
| OS-level reminder notifications | | | ❌ | WorkManager plumbing present, zero Workers exist | New permission + channel | Deferred, not in v1 (§3.4, §11) |

---

## 5. MVP-1.2 objective

**The single most important user outcome:** a shop owner who has been noting things down in BUDCOM
("customer says 2 pieces short," "promised to pay by Friday," "wants a callback about GST invoice")
should be able to open any party and immediately see *what's still open*, *what's been resolved*,
and *what they personally still owe that party's relationship* — without re-reading every note ever
written, and without BUDCOM inventing anything that isn't grounded in data the owner actually
entered or Tally actually confirmed.

---

## 6. Scope

### IN scope for MVP-1.2

- Typed, structured Party notes (extends existing `party_notes`, does not replace it).
- Relationship Timeline: one chronological, readable feed per party (notes + export events).
- Issue History: grouping related notes under one continuing, resolvable issue.
- Dincharya: a bounded, cross-party, in-app actionable list covering exactly three deterministic
  item types named in the locked plan — promised-payment/callback follow-ups, pending contact
  completion, pending Tally XML confirmation.
- OI framing/copy discipline applied to Dincharya's presentation.
- Full mini-hardening per slice + one integrated MVP-1.2 hardening pass (1.2-E), mirroring the
  proven 1.1 rhythm.

### OUT of scope for MVP-1.2 (explicit)

- **Referral Tree** — not named in the locked Master Plan §8 line item; needs an explicit product
  decision (§3.2), not a Claude judgment call.
- **Home Insights dashboard / value-attribution / "Suggested Actions" home surface** — its content
  spec does not exist anywhere in the repository (§2, §3.3); building it now would mean inventing a
  workstream the Product Owner has reserved for separate planning.
- **"Later appropriate catalogue follow-ups"** (Master Plan §8's own words) — depends on MVP-1.4
  Catalogue, which does not exist yet.
- **OS-level push/local notifications for Dincharya** — deferred to a future slice; v1 is an
  in-app, pull-to-check list only (§3.4, §11).
- **Generic CRM pipeline, lead scoring, relationship-health percentage, task manager** — explicitly
  forbidden by `docs/planning/BUDCOM-NOT-NOW.md` and the referral spec §28.
- **Any generative-AI-backed suggestion or scoring** — explicitly forbidden by `BUDCOM-NOT-NOW.md`
  ("Advanced generative AI dependency... OI/deterministic helpers first") and the OI positioning
  itself.
- **Vartalap integration** — no milestone exists yet (confirmed via `BUDCOM-SCREEN-INVENTORY.md`).
- **Business Profile / Catalogue** — MVP-1.3/1.4, unrelated.
- **Any direct Tally write** — the existing XML-review/export/re-sync boundary from MVP-1.1-D is
  unchanged and untouched.
- **Any Desktop or Connector code change** — confirmed unnecessary by this session's architecture
  review (§1, §7).
- **Reopening MVP-1.1** unless a genuine blocking defect surfaces during MVP-1.2 work.

---

## 7. Architecture review — can MVP-1.2 build cleanly on the frozen 1.1 foundation?

**Yes**, with one new table and one extended table. Reviewed directly against source:

- **Android layering** (Infra → Data → Business → UI) is intact and consistently followed across
  every existing Party file — MVP-1.2 should follow the identical shape: Room entity/DAO → domain
  model → repository method → use case → ViewModel/UiState → Compose screen. No architectural
  drift needed.
- **Reusable components:** `PartyDetailScreen`'s existing section-based composition (reuse the
  same pattern for a new Timeline/Issues presentation); the existing bounded-paging idiom
  (`PartyNotePage`, `page`/`pageSize`/`totalItems`/`canLoadMore` — reuse verbatim for Timeline);
  the existing self-referential grouping precedent in `party_tags` (`parentTagId`/`path`) as a
  structural model for how `party_issues` should relate to notes; the existing bulk-company-read
  precedent (`PartySourceLinkDao`/`TagDao`'s `getAllForCompany`-style methods, proven bounded with
  500-row fixtures in 1.1-B) as the direct template for Dincharya's cross-party query; the existing
  `MasterDataLoadingIndicator`/`MasterDataErrorBlock` shared composables; the existing
  `Routes.partyDetail(partyId)` deep-link convention for Dincharya rows to jump back into a party.
- **Missing abstractions:** nothing structural — the gap is purely two additive schema pieces
  (typed/dated notes, an issue table), not a missing layer or pattern.
- **Risky coupling:** none found. Dincharya is the first genuinely **cross-party** bounded query in
  this feature (everything in 1.1 was single-party or single-company-list-scoped); this is new
  *query shape*, not new *architecture* — same DAO/repository/use-case layering applies.
- **Migrations required:** one, `MIGRATION_8_9` (schema v8→v9), pure additive (§9) — same
  discipline as `MIGRATION_5_6`/`6_7`/`7_8` (verify byte-for-byte against Room's generated `9.json`
  before hand-writing, real `MigrationTestHelper` test with pre-migration rows).
- **API/Connector changes:** none required (§1, §2).
- **Cross-platform concerns:** none — Desktop/Connector are unaffected (§1, §2).
- **Security concerns:** company isolation on the new cross-party Dincharya query is the single
  highest-risk item in this milestone (§13, Risk #1) — everything else reuses already-proven
  company-scoped natural keys.
- **Performance concerns:** Dincharya must be bounded/paged from day one, following the exact
  precedent already proven in `PartyDaoTest`'s 500-row fixture tests — never an unbounded
  full-company scan.
- **Offline requirements:** every MVP-1.2 read/write is local-Room-only, exactly like MVP-1.1 —
  offline-capable by construction, not by a special-cased offline path (§10).

---

## 8. No-over-engineering discipline applied

Per this task's explicit instruction and PDL-012 ("complexity must earn its place"):

- Notes are **extended**, not replaced by a new "Activity" abstraction — reuses the proven
  `party_notes` table and its DAO/repository/use-case surface exactly as MVP-1.1-C left it.
- Issue grouping reuses the **exact structural precedent** already in the codebase
  (`party_tags`' self-referential-definition-plus-assignment shape), not a novel pattern.
- Dincharya's "pending Tally confirmation" and "pending contact completion" item types need **zero
  new schema** — they read data MVP-1.1 already persists. Only the "promised-payment/callback"
  item type needs new columns.
- No new dependency, no new framework, no cloud infrastructure, no generative-AI call anywhere in
  this plan.
- OS-level notifications are deliberately deferred (§3.4, §6) rather than bundled in, because zero
  supporting infrastructure exists today and adding it is a distinct, separately-reviewable
  security/permission surface, not a natural extension of an in-app list.

---

## 9. Data model

### 9.1 `party_notes` — extended (additive columns only, existing rows untouched)

| New column | Type | Nullable | Purpose |
|---|---|---|---|
| `type` | TEXT | NOT NULL, default `'general'` | One of: `payment_issue`, `complaint`, `delivery_issue`, `commitment`, `product_interest`, `internal_remark`, `follow_up`, `general` — the exact vocabulary from the referral spec §20, lower-snake-case to match this codebase's existing enum-column convention (e.g. `PartyClassification`'s `asColumn()`). Every existing row backfills to `'general'` — zero behavior change for anything written before this migration. |
| `dueAt` | INTEGER | NULL | Epoch millis. Only meaningful for `commitment`/`follow_up` types. Presence (non-null) + `completedAt IS NULL` is what makes a note a Dincharya candidate (§11). |
| `completedAt` | INTEGER | NULL | Epoch millis. Set when the user marks a follow-up/commitment done. A note is never deleted to "complete" it — completion is a state change, preserving the Timeline entry (honest history, not a disappearing act). |
| `issueId` | TEXT | NULL | Natural-key reference (no `ForeignKey`, matching this codebase's established convention — see `PartySourceLink`/`PartyTagAssignment`) into the new `party_issues` table. Most notes remain `NULL` (issue-less), exactly like today. |

### 9.2 `party_issues` — new table

| Column | Type | Notes |
|---|---|---|
| `companyId` | TEXT NOT NULL | Company scope, same convention as every other Party table |
| `issueId` | TEXT NOT NULL | UUID, primary key together with `companyId` |
| `partyId` | TEXT NOT NULL | Owning party |
| `title` | TEXT NOT NULL | Short user-entered summary, e.g. "2 pieces short — Sales Voucher #1842" |
| `status` | TEXT NOT NULL, default `'open'` | `open` / `resolved` |
| `createdAt` | INTEGER NOT NULL | |
| `resolvedAt` | INTEGER NULL | Set when status transitions to `resolved` |
| `updatedAt` | INTEGER NOT NULL | |

Index: `(companyId, partyId, status, createdAt)` — supports "open issues first" ordering per party,
matching the existing `party_notes` index shape (`(companyId, partyId, createdAt)`).

### 9.3 Migration

`MIGRATION_8_9` (schema version 8→9): one `ALTER TABLE party_notes ADD COLUMN` per new column
(four `ALTER TABLE` statements — SQLite/Room supports additive nullable/defaulted columns without
a table rebuild, consistent with this project's own "pure additive, no destructive migration ever"
convention across `5_6`/`6_7`/`7_8`), one `CREATE TABLE party_issues`, one `CREATE INDEX`. Verify
byte-for-byte against Room's own KSP-generated `9.json` before hand-writing, exactly as every prior
migration in this codebase was written. New `AppDatabaseMigrationTest.migrate8To9_...` case,
inserting pre-migration rows and asserting survival + new-table usability, following the exact
`migrate7To8_...`/`migrate6To7_...` template.

### 9.4 What is deliberately NOT modeled

- No `party_reminders`/notification-scheduling table — deferred with OS notifications (§3.4, §6).
- No cross-company Dincharya aggregation table — Dincharya is always scoped to the currently
  selected company, exactly like every other Connect/Party query.
- No generic "activity type" plugin system — the `type` enum is a fixed, small, named set matching
  the locked spec's own vocabulary, not an extensible taxonomy (PDL-012).

---

## 10. UI/UX

Per this task's own instruction: no approved visual master exists for any of these screens, so this
section defines **information hierarchy and intent only** — visual detail should be resolved via
ChatGPT + the external design archive before implementation, per
`POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §8's Claude-economics rule, not invented here.

Authority: `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md` (locked visual character — cutting-edge
minimalism, dense-but-not-cluttered, color communicates state/action never decoration, one-handed
ergonomics) and the referral spec's own recommended Party Detail hierarchy (§24): Relationship
Timeline/Issues/Dincharya belong **after** existing Notes/Activity and **before** any future
Business Profile/Catalogue/Vartalap links — i.e., they extend the current Party Detail bottom, they
do not reorder anything already shipped.

### Party Detail — Relationship Timeline (replaces the presentation of the current flat Notes list — see open question §3.1)

- One chronological feed, newest first, bounded/paged exactly like today's Notes list.
- Each entry shows: a type badge/label (using the referral spec's plain-language vocabulary, never
  a raw enum — same discipline as `FieldProvenanceState.toUiLabel()`), the note body, an optional
  linked-voucher affordance (unchanged from 1.1-C), and — new — export events interleaved
  ("Exported to Tally: Phone, Email — 17 Aug"), read-only, sourced from `party_export_events`.
- Add/Edit note dialog gains: a type picker (defaults to `general`, preserving today's one-tap
  behavior for a quick remark); a due-date field that only appears when `commitment`/`follow_up`
  is selected; an optional "part of issue" picker (existing open issues for this party, or "start a
  new issue").
- No information already visible today should require an extra tap to find — this is an
  enhancement of an existing, already-used screen, not a rebuild.

### Party Detail — Issues (new section)

- Open issues shown first, prominently but compactly (a count badge is enough at a glance —
  "2 open issues" — full detail on tap).
- Each issue card: title, note count, last-activity date, Resolve/Reopen action.
- Tapping an issue opens its notes filtered within the Timeline (reuse the Timeline's own paging
  and rendering, filtered by `issueId` — not a second, duplicate list implementation).
- Resolved issues collapse into a secondary, de-emphasized area — visible on request, not
  competing with open, actionable items for attention (locked visual principle: color/prominence
  communicates state, never decoration).

### New top-level screen — Dincharya

- Reached from a new fourth Dashboard primary entry, following the exact precedent of Connect's own
  addition in MVP-1.1-B (`HomePrimaryEntryRow`) — no new navigation paradigm invented.
- Grouped by the three deterministic item types (§6, §11): Follow-ups & Callbacks / Pending Tally
  Confirmation / Pending Contact Info — never one undifferentiated mixed list.
- Each row deep-links to that Party's Detail screen (`Routes.partyDetail(partyId)`, unchanged),
  never opens a second, parallel detail view.
- **Explicitly bounded, never an endless list** (Master Plan §8's own warning): show a capped
  count with clear "N more" disclosure rather than an infinite scroll of stale overdue items;
  completed/confirmed/resolved items drop off automatically, not manually curated.
- OI framing line near the top or in the empty state: language consistent with *"We are not AI.
  This is OI — programmed to help you"* — plain, honest, never implying autonomous judgment BUDCOM
  doesn't actually perform.
- Empty state (no open items) should read as a genuine, positive "nothing pending" state, not a
  suspiciously-empty error look — matching this codebase's existing honest-empty-state discipline
  (`connect_empty_customers`, `connect_empty_prospects` precedent from 1.1-B).

---

## 11. Android — required work (by sub-milestone, see §16)

- **1.2-A:** `party_notes` migration + entity/DAO/domain-model updates; new `party_issues`
  table/entity/DAO/domain model; repository/use-case additions for typed note create/edit,
  due-date/completion toggling, issue create/resolve/reopen; minimal Party Detail UI change (type
  picker + due-date field + issue picker on the existing add/edit note dialog only — no new screen
  yet).
- **1.2-B:** Relationship Timeline read model (merge notes + export events, chronological, paged);
  Party Detail presentation change (§10); new ViewModel/UiState following the established
  `PartyDetailViewModel` pattern.
- **1.2-C:** Issue History section on Party Detail; issue-filtered Timeline view; resolve/reopen
  wiring.
- **1.2-D:** New `feature/dincharya/` package (mirrors `feature/connect/`'s existing module
  boundary — a new top-level feature, not a Connect sub-feature, since it is cross-party rather
  than party-scoped); new bounded cross-party repository query (reusing the `getAllForCompany`
  bulk-read precedent); new Dashboard entry + route; OI copy.
- **1.2-E:** Integrated MVP-1.2 hardening — full regression, company-isolation proof for the new
  cross-party query (§13 Risk #1), migration re-verification, accessibility pass, freeze.

---

## 12. Desktop — required work

**None.** Confirmed by direct source inspection this session: Desktop's renderer has no Party/
Connect surface of any kind (`Connect`/`Party` string matches in `apps/budcom_desktop/src/renderer`
are exclusively unrelated "Connector" lifecycle references). MVP-1.2 does not touch Desktop.

---

## 13. Connector/Tally — required work

**None.** The Connector remains a 100% read-only Tally boundary. Every MVP-1.2 data point (notes,
issues, due dates, export/provenance state) is BUDCOM-local. The one Tally-adjacent Dincharya item
type ("pending Tally XML confirmation") reads `party_field_provenance` — data MVP-1.1-D's existing
`GET /ledgers/{id}` path already populates — no new Connector call, no new endpoint, no changed
Tally semantics.

---

## 14. Offline behavior

Every MVP-1.2 read and write is local-Room-only, exactly matching MVP-1.1's proven pattern —
offline-capable by construction. Creating/editing a typed note, marking a follow-up complete,
resolving an issue, and browsing Dincharya all work with zero connectivity. No new network-backed
call is introduced anywhere in this plan.

---

## 15. Security

- **Company isolation is the dominant security concern for MVP-1.2** (§13 Risk #1) — Dincharya's
  cross-party query is the first query in this feature area that spans an entire company's Parties
  at once rather than one Party or one paged list; it must use the same proven `companyId`-scoped
  natural-key discipline as every existing Party table, explicitly tested against a same-name/
  same-phone-across-two-companies fixture (the established pattern from MVP-1.1-B's own company-
  isolation tests).
- No new sensitive-data surface: issue titles and note bodies are user-authored BUDCOM-only text,
  same trust level as existing notes — no new export/audit path is introduced (Dincharya/Timeline/
  Issues are read/local-write only, never exported to XML).
- No new file I/O, no new FileProvider path, no new external-facing endpoint.
- If OS notifications are ever added (explicitly deferred, §3.4), that introduces a genuinely new
  security review surface (`POST_NOTIFICATIONS` runtime permission, notification-content privacy on
  a locked screen) that should be scoped and approved separately, not folded into this plan's
  implicit assumptions.

---

## 16. Performance

- Relationship Timeline: bounded/paged exactly like today's Notes list (`page`/`pageSize`/
  `totalItems`), merging two already-indexed sources (`party_notes`, `party_export_events`) —
  no full-table scan.
- Issue History: single indexed lookup per party (`(companyId, partyId, status, createdAt)`).
- Dincharya: the one genuinely new query shape — must be proven bounded with a synthetic
  large-fixture instrumented test (500+ notes/issues across many parties), following the exact
  precedent `PartyDaoTest`'s 500-row fixture test already established, before this milestone can be
  considered acceptance-complete. `LIMIT`/`OFFSET` throughout, never an in-memory filter over a
  full company load.

---

## 17. Test plan (per sub-milestone)

Reuses this project's established testing architecture exactly — JVM repository/use-case/
ViewModel tests, real-device Room DAO instrumented tests, real `MigrationTestHelper` migration
tests, Compose instrumented screen tests, full regression re-run at every mini-hardening point.

- **1.2-A:** JVM tests for typed-note create/edit (including default `type='general'` backwards
  compatibility), due-date/completion toggling, issue create/resolve/reopen, company isolation.
  Instrumented: `PartyNoteDaoTest`/`PartyIssueDaoTest` additions, `AppDatabaseMigrationTest.
  migrate8To9_...` (real pre-migration data, real production migration object). Regression gate:
  full JVM suite green, both lints 0 errors, all assembles green.
- **1.2-B:** JVM `PartyDetailViewModelTest` additions for the merged Timeline read model
  (ordering, type-label mapping, export-event interleaving). Instrumented: Screen test for the new
  Timeline rendering, including a Prospect (no export events ever) and a long mixed history.
- **1.2-C:** JVM tests for issue grouping/filtering, resolve/reopen state transitions, "a note can
  only ever belong to one issue" invariant if adopted. Instrumented: Issue section rendering,
  filtered-Timeline-by-issue tap-through.
- **1.2-D:** JVM `DincharyaViewModelTest` — all three item types individually and combined,
  explicit **cross-company leakage prevention test** (highest-priority test in this whole
  milestone, §13/§16), bounded-list/"N more" disclosure logic, empty-state honesty. Instrumented:
  large-fixture performance test (§16), Screen test for grouped rendering and deep-link-back to
  Party Detail. Dashboard entry wiring test (mirrors `DashboardViewModelTest`'s existing
  `OpenConnect` case).
- **1.2-E:** Full JVM debug+release suite, both lints, all three assembles, full instrumented suite
  on a connected physical device — same freeze-audit rhythm as MVP-1.1-E §26, including an explicit
  regression check that MVP-1.1's own Notes/Party Detail tests still pass unmodified in spirit
  (behavior preserved, only additively extended).

---

## 18. Milestone breakdown

| Sub-milestone | Deliverable | Architectural/acceptance boundary |
|---|---|---|
| **1.2-A** | Structured Party Activity foundation: typed notes, due-date/completion, `party_issues` table, `MIGRATION_8_9` | Schema stabilizes; existing Notes behavior is unchanged and re-proven; nothing user-visible beyond an optional type picker yet |
| **1.2-B** | Relationship Timeline | First new read surface; Party Detail presentation changes; resolves open question §3.1 |
| **1.2-C** | Issue History | Grouping/lifecycle (open→resolved) becomes real; depends on 1.2-A's `party_issues` table |
| **1.2-D** | Dincharya | First cross-party query in this feature area; new top-level screen/Dashboard entry; OI framing lands |
| **1.2-E** | Integrated MVP-1.2 hardening | Full regression, company-isolation proof, performance proof, freeze — mirrors 1.1-E exactly |

Each slice gets its own mini-hardening pass before proceeding, per PDL-006/the locked release
philosophy ("Feature → mini-hardening → next feature → ... → integrated hardening → release").

---

## 19. Acceptance criteria — MVP-1.2 complete

- Every existing MVP-1.1 Notes/Party Detail/Connect behavior is unchanged and re-proven (regression
  gate, not assumed).
- A note can be typed, and typing is optional (defaults preserve today's one-tap "just write a
  note" flow).
- A follow-up/commitment note can carry a due date and be marked complete without being deleted.
- Related notes can be grouped into a resolvable Issue; resolving an issue never deletes or hides
  its underlying notes from the Timeline.
- Relationship Timeline shows one coherent, chronological, correctly-typed history per party,
  including Tally export activity, entirely from local data.
- Dincharya surfaces exactly the three locked-plan item types, is provably bounded (never an
  endless list), is provably company-isolated (dedicated adversarial test, not just happy-path),
  and never fabricates urgency or claims AI judgment it doesn't perform.
- Zero direct Tally write exists anywhere in the new code (grep-verified, same discipline as
  MVP-1.1-D's own `confirmFieldFromTally`-unreachable proof).
- Zero Desktop or Connector file is touched.
- Full JVM/lint/assemble/instrumented regression is clean, with the same "known 12 device-viewport
  failures, zero new failures" bar MVP-1.1 established.
- Documentation/ledger/checkpoint updated per the Durable Development Record rule; MVP-1.2 status
  recorded the same way MVP-1.1's specialist status document tracked A–E.

---

## 20. Known risks, ranked by severity

1. **HIGH — cross-company data leakage in Dincharya's new cross-party query.** This is the first
   query in the Connect/Party feature area that spans an entire company at once rather than one
   party or one already-company-scoped list. Mitigation: reuse the exact proven natural-key
   discipline, and make the adversarial two-company test a named, explicit, blocking test — not an
   incidental side-effect of other tests, the way 1.1-B's own company-isolation tests were written
   deliberately, not accidentally.
2. **MEDIUM — "endless overdue list" UX failure.** Explicitly warned against in the locked Master
   Plan itself. Not fully resolvable by architecture alone — needs a concrete product decision on
   the bounding/aging rule (e.g., how many days before a stale follow-up stops being prominently
   shown) before 1.2-D's UI is finalized.
3. **MEDIUM — Timeline-vs-Notes presentation ambiguity** (§3.1). Guessing wrong means rebuilding
   Party Detail's Notes section twice.
4. **MEDIUM — Referral Tree sequencing ambiguity** (§3.2). Not a technical risk to MVP-1.2 itself,
   but a real risk of the Product Owner expecting Referral Tree inside "MVP-1.2" colloquially while
   this plan (correctly, per locked scope) excludes it.
5. **LOW — migration risk.** Mechanically low: this is the fourth additive-only migration in this
   codebase using an already-proven, three-times-validated discipline.
6. **LOW — OS notification scope creep.** Mitigated by explicit deferral (§3.4, §6) rather than
   silent inclusion.

---

## 21. Deferred items (explicit — not MVP-1.2)

- Home Insights dashboard / value-attribution / Suggested Actions home surface (§3.3, §6) — no
  spec exists yet anywhere in the repository.
- Referral Tree (§3.2, §6) — pending an explicit product-sequencing decision.
- OS-level push/local notifications for Dincharya (§3.4, §6, §15).
- "Later appropriate catalogue follow-ups" (Master Plan's own phrase) — depends on MVP-1.4.
- Vartalap integration.
- Business Profile / Catalogue (MVP-1.3/1.4).
- Any generative-AI-backed feature of any kind.
- Cross-party/company-wide Relationship Timeline (a company-wide feed rather than per-party) — the
  locked definition is per-party ("What happened" to *this* party), not a company activity stream.
- Employee-attribution / referral-value auditability (bundled with Referral Tree's deferral).

---

## 22. Recommended next autonomous implementation prompt

The following is the exact prompt this plan recommends handing to the next autonomous
implementation session, once §3's open questions are resolved by the Product Owner:

> **BUDCOM MVP-1.2-A — Structured Party Activity Foundation**
>
> Repository: `D:\Projects\Budcom`. MVP-1.1 is COMPLETE/FROZEN at HEAD `ad32f9a` — do not reopen it
> unless you discover a genuine blocking defect. Read
> `docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md` in full
> before starting — it is this milestone's locked scope/architecture baseline, reconciled against
> actual repository evidence. Also read `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` and
> `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` for current state.
>
> Implement exactly MVP-1.2-A (§9, §11, §17, §18 of the architecture doc):
> 1. `MIGRATION_8_9` (schema v8→v9): extend `party_notes` with `type` (NOT NULL, default
>    `'general'`), `dueAt` (nullable), `completedAt` (nullable), `issueId` (nullable); create
>    `party_issues` (`companyId`, `issueId`, `partyId`, `title`, `status`, `createdAt`,
>    `resolvedAt`, `updatedAt`) with its index. Verify byte-for-byte against Room's generated
>    `9.json` before hand-writing, exactly like every prior migration in this codebase.
> 2. Extend `PartyNoteEntity`/`PartyNote`/`PartyNoteDao` for the new columns; add
>    `PartyIssueEntity`/`PartyIssue`/`PartyIssueDao` (create, resolve, reopen, list-by-party).
> 3. Extend `PartyRepository`/`PartyUseCases` for typed note create/edit (default `type='general'`
>    when unspecified — must not change today's one-tap add-note behavior), due-date/completion
>    toggling, issue create/resolve/reopen.
> 4. Minimal Party Detail UI change only: a type picker, a conditional due-date field (shown only
>    for `commitment`/`follow_up`), and an optional "part of issue" picker on the *existing*
>    add/edit note dialog. Do not build the Relationship Timeline, Issue History section, or
>    Dincharya screen yet — those are 1.2-B/C/D, explicitly out of this prompt's scope.
> 5. Full test coverage per the architecture doc §17's 1.2-A row: JVM repository/use-case tests
>    including explicit backward-compatibility proof (a note created with no type still reads as
>    `general` and behaves exactly as before), instrumented DAO tests, and the real
>    `MigrationTestHelper`-based `migrate8To9_...` test.
> 6. Mini-harden: full JVM debug+release regression, both lints, all three assembles, instrumented
>    suite on a connected authorized device if one is available — confirm zero new failures beyond
>    the already-known 12 device-viewport class.
> 7. Update the Durable Development Record (specialist status doc for MVP-1.2, Development Ledger,
>    Current Development Status) per governance — do not claim completion without matching
>    `git log`/`git status` evidence.
> 8. Commit coherently. Do not push. Do not begin 1.2-B in this session — stop and report exact
>    next task once 1.2-A is mini-hardened and documented.

---

## 23. Source documents this plan reconciled

- `docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` §8 (locked MVP-1.2 title/objective — primary authority)
- `docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md` §14–§28 (only source that defines these terms and their UI placement)
- `docs/architecture/BUDCOM-MVP-1-1-CONNECT-UNIVERSAL-PARTY-ARCHITECTURE.md` §36 ("Relationship to later roadmap")
- `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md` (locked visual character, Home Insights nav-shape-only boundary)
- `docs/design/BUDCOM-SCREEN-INVENTORY.md` (confirms no screen exists yet for any MVP-1.2 surface, confirms Vartalap has no milestone)
- `docs/planning/BUDCOM-NOT-NOW.md` (explicit scope exclusions — CRM, AI dependency, dialer/call-log)
- `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` (PDL-012 complexity discipline, PDL-013 simple-experience discipline)
- `docs/status/BUDCOM-MVP-1-1-CONNECT-STATUS.md` Parts A–E, `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`, `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` (frozen baseline)
- Direct repository inspection: `PartyEntities.kt`, `PartyModels.kt`, `DatabaseModule.kt`, `AndroidManifest.xml`, `apps/budcom_desktop/src/renderer/**` (confirmed no Connect/Party surface), `PartyRepository.kt`

**No document contradicted another on MVP-1.2's core definition** — the Master Plan and the
referral spec agree on what Relationship Timeline/Issue History/Dincharya/OI mean. The genuine gaps
found were *absences* (no Insights workstream doc, no explicit Referral-Tree-in-1.2 confirmation),
not conflicts, and are recorded honestly in §3 rather than resolved by assumption.
