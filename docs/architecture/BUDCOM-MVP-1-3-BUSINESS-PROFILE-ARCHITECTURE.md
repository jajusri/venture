# BUDCOM MVP-1.3 — Business Profile — Architecture

**Status:** Planning/recovery review complete (2026-08-18); **Brainstorm 1 gap closed (2026-08-18)**
— all five open product decisions in §5 were explicitly reviewed and approved by ChatGPT/Product
Owner and are now recorded as `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-019. §5's
per-question text is preserved below as the reasoning record (each question now carries a
**RESOLVED — PDL-019** marker with the locked answer); §0 of the MVP-1.3-A implementation prompt is
the authoritative current statement of the locked scope. **MVP-1.3-A implementation is now
authorized and in progress/complete** — see the specialist status document
`docs/status/BUDCOM-MVP-1-3-BUSINESS-PROFILE-STATUS.md` for implementation evidence.

---

## 1. Executive summary

MVP-1.3 is named **Business Profile** in the locked roadmap
(`docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` §9). Its **ownership/visual shape** is
locked (`docs/design/BUDCOM-UI-DESIGN-DECISIONS.md` §7): one business identity, one catalogue, one
asset library, exposed through multiple permission-controlled views; Connect and Vartalap consume
this data, they do not own it. Its **detailed field-level/screen-level scope is explicitly not
locked** — the Master Plan's own §9 states "Detailed scope comes from Brainstorm 1," and Brainstorm 1
(User + ChatGPT, per Modus Operandi §2/§3) has not yet been performed for this milestone anywhere in
this repository or the external design archive (§2.3/§2.4).

**Zero code exists anywhere in this repository (Android, Desktop, Connector) for Business Profile or
Catalogue** — confirmed by direct repository search, not assumed (§2). MVP-1.3 starts from a true
implementation baseline of zero, but with real, non-trivial groundwork already locked: an ownership
contract, a navigation shape, and roadmap sequencing. This is a normal, expected state for a milestone
that has had its architecture-sequencing decided but not yet its product brainstorm — exactly the
same state MVP-1.2 was in before its own 2026-08-17 planning session (Development Ledger phase 26)
produced the architecture document that made MVP-1.2-A implementable.

**This document's purpose:** do everything a planning/recovery review can honestly do without
inventing product scope — inventory the actual repository, reconcile documentation against source,
propose a candidate architecture grounded in this codebase's own proven patterns, and hand Brainstorm
1 a concrete, reviewable starting point rather than a blank page. Every field, screen, and scope
boundary named below is a **proposal for Brainstorm 1 to confirm, adjust, or reject** — none of it is
locked, and a fresh Claude session must not treat it as such.

---

## 2. Starting state / repository recovery (verified directly, not assumed)

### 2.1 Git state

- Branch: `main`. Starting HEAD: `773284485e03028508e89b8f84ebeda54a1dac3b` — **matches this task's
  own expected baseline exactly.**
- `git status`: clean. Zero untracked files (`git status --porcelain=v1 -uall` empty).
- `origin/main`: verified via `git fetch origin` + `git rev-parse` — byte-identical to local HEAD.
  No divergence in either direction.
- Last 8 commits confirmed to be exactly the MVP-1.2-D/E sequence (`f049fb1` through `7732844`),
  matching `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` phases 31–32 without discrepancy.

### 2.2 MVP-1.2 freeze confirmation

- `apps/budcom_android/app/build.gradle.kts`: `versionCode = 28`, `versionName = "0.1.1-continuity.27"`
  — matches this task's expected frozen version exactly.
- `DatabaseConstants.VERSION = 9` — unchanged since MVP-1.2-A, confirming no undocumented schema
  drift since the freeze.
- No discrepancy found between the documented MVP-1.2 freeze state and actual repository state.

### 2.3 Repository search — Business Profile / Catalogue implementation inventory

A full repository search (Android Kotlin, Desktop TypeScript/React, Connector TypeScript, and all
`docs/`) for Business Profile / Catalogue / branding / asset-library / UPI / bank-details /
social-links / Resources / business-identity terminology found:

| Bucket | Finding |
|---|---|
| **IMPLEMENTED** | None. Zero. |
| **PARTIALLY IMPLEMENTED** | None. Zero. |
| **DOCUMENTED ONLY** | The real substance — see §2.4/§4. |
| **OBSOLETE/DEFERRED** | None — nothing was ever started, so nothing to clean up. |
| **UNRELATED (false-positive)** | `feature/company/*` (Tally ERP company/financial-year selection — a completely different "company" concept, see §5.1 for why this matters); Desktop's local `catalog` variable (a recovery-action-code lookup table); "error-shape catalog(ue)" in diagnostics docs; every `branch(es)` hit (git branches, Tally-connect filter branches — not business locations); Android's `<resources>` XML root tag. |

Explicit checks:

- `AppDatabase.kt` `entities = [...]`: no `business_profile`, `catalogue_items`, `company_profile`,
  or `assets` table. Full current list (20 entities): `CompanyEntity`, `CompanyDiscoveryMetaEntity`,
  `LedgerEntity`, `StockItemEntity`, `VoucherEntity`, `VoucherDetailEntity`, `VoucherLedgerLineEntity`,
  `VoucherInventoryLineEntity`, `VoucherCacheMetaEntity`, `PairedConnectorEntity`,
  `LedgerStatementEntity`, `LedgerStatementTransactionEntity`, `PartyEntity`, `PartySourceLinkEntity`,
  `PartyFieldProvenanceEntity`, `PartyContactPersonEntity`, `TagEntity`, `PartyTagCrossRefEntity`,
  `PartyNoteEntity`, `PartyExportEventEntity`, `PartyIssueEntity`.
- `Routes.kt`: no profile/catalogue/library route. Full current list: `HOME`, `CONNECTOR_DISCOVERY`,
  `SECURE_PAIRING`, `SERVER_CONFIG`, `COMPANY`, `MASTER_DATA`, `SEARCH`, `SYNC`, `DIAGNOSTICS`,
  `SETTINGS`, `LEDGERS`, `STOCK_ITEMS`, `VOUCHERS`, `VOUCHER_DETAILS`, `LEDGER_STATEMENT`, `CONNECT`,
  `DINCHARYA`, `PARTY_DETAIL`, `PROSPECT_CREATE`, `PARTY_XML_EXPORT`.
- `DashboardScreen.kt`/`DashboardUiState.kt`: zero reference to profile/catalogue/resources/library/
  brand/logo, disabled or otherwise — no dormant entry point exists to wire up.
- Desktop renderer: zero Business-Profile/Catalogue UI surface — confirms the MVP-1.2 architecture
  doc's own finding ("Desktop's renderer has no Connect/Party surface of any kind") extends
  identically here. **MVP-1.3, like MVP-1.2, is presumptively a 100%-Android effort** unless
  Brainstorm 1 explicitly decides Desktop needs a Business Profile surface too (not assumed here).
- Connector: zero endpoint plausibly related to business-profile/catalogue data. Consistent with the
  Connector's own locked boundary (100% read-only Tally proxy) — a Business Profile is BUDCOM-native
  data with no Tally equivalent, so this is expected, not a gap.
- A `FileProvider` is already configured in `AndroidManifest.xml` (used today by the PDF-preview/
  share and Party-XML-export cache-file flows, `core/pdf/` and
  `feature/party/sharing/PartyXmlExportCacheFileSystem.kt`) — directly reusable groundwork for a
  future logo/asset-upload flow (§8.4), not something MVP-1.3 needs to invent from scratch.

### 2.4 Documentation reconciliation — one genuine staleness finding, corrected this session

`docs/design/BUDCOM-SCREEN-INVENTORY.md`'s "Current screens" table predates MVP-1.1-B — it lists
Dashboard, Master Data Hub, Ledger/Stock/Voucher browsers, Universal Search, Company Selection,
Connector Discovery, Secure Pairing, Server Config, Sync, Diagnostics, and Settings, but **omits
Connect, Party Detail, Prospect Create, Party XML Export, and Dincharya** — five real, shipped,
frozen screens. Its own "Maintenance rule" states: "Do not let it drift silently out of sync with
source — a stale inventory is worse than none, because it will be trusted." Since a fresh session
planning MVP-1.3 would read this file and be actively misled about what already exists, **this
session corrected the table** (the only non-planning-document repository change made) rather than
merely noting the gap and leaving it stale for someone else to hit. The "Screens implied by locked
decisions but not yet built" table's own MVP-1.3/1.4 row ("My Business (Profile / Catalogue /
Business Library)... Not an MVP-1.0.x item") was accurate and is unchanged.

### 2.5 No document conflicts found

No two authoritative documents were found to materially disagree about MVP-1.3's identity, sequencing,
or ownership boundary. The only "conflict" is the §2.4 staleness (a document falling out of date, not
two documents disagreeing) and the general-vs-detailed scope gap described in §3.

---

## 3. The central finding — Brainstorm 1 has not happened, and this document is not a substitute for it

> **RESOLVED 2026-08-18 — PDL-019.** The Brainstorm-1 gap this section describes has been closed: all
> five §5 questions were explicitly reviewed and approved. This section is preserved as the
> reasoning record for *why* the gap mattered and how it was correctly identified, not as a
> currently-open blocker.

`docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` §9, verbatim: *"Detailed scope comes from
Brainstorm 1. Do not allow premature marketplace/social-network expansion."* `docs/governance/
POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §2/§3 assigns Brainstorm 1 explicitly to **User + ChatGPT**,
before implementation, covering: the problem, desired outcome, UX/workflow, data authority,
online/offline behavior, persistence, edge cases, invariants, scope, exclusions, acceptance criteria,
risks. PDL-010 (locked): *"ChatGPT plans architecture... Claude handles approved implementation."*
PDL-011 (locked) requires, for every major capability, an explicit source of truth, stable identity,
ownership/layer responsibility, persistence location, migration path, failure/recovery behavior, and
replacement/evolution path — none of which can be honestly declared "locked" for Business Profile
today, because the product brainstorm that would establish them has not occurred.

A full search of `docs/` and the external design archive (`D:\BUDCOM-Design-Archive\` — confirmed to
exist locally, inspected directly, contains zero Business-Profile/Catalogue content in any of its six
folders including `05_IMPLEMENTATION_HANDOFF`) found no "Brainstorm 1" output for this milestone
anywhere. This is the same state MVP-1.2 was in before its own dedicated 2026-08-17 planning session
(ledger phase 26) — that session explicitly did not start MVP-1.2-A implementation until the four
locked-by-then-open product questions were answered (PDL-014–017). This review reaches the equivalent
conclusion for MVP-1.3: **the architecture-sequencing and ownership shape are locked; the product
brainstorm is not done; implementation must not begin until it is.**

This is recorded as a stop condition (§20), not a blocker this session tried to route around by
guessing. Everything in §7–§16 below is offered as a **candidate input** to that brainstorm, explicit
about what is locked-and-reusable (this codebase's own proven architectural patterns) versus what is
a genuine open product question only Brainstorm 1 can answer.

---

## 4. Plan vs. repository reconciliation matrix

| Roadmap claim | Repository evidence | Status |
|---|---|---|
| MVP-1.3 = Business Profile (Master Plan §9) | Confirmed, unambiguous, no conflicting document | **Confirmed** |
| MVP-1.4 = Catalogue, same ownership boundary as 1.3 (Master Plan §10, UI Decisions §7) | Confirmed | **Confirmed** |
| "One business identity, one catalogue, one asset library" (UI Decisions §7) | Confirmed, no code contradicts it (there is no code) | **Confirmed, architecturally binding** |
| Connect/Vartalap consume, never own, Business Profile data (UI Decisions §7) | Confirmed; Connect exists today and currently has zero Business-Profile dependency to retrofit — the boundary can be respected from a clean slate | **Confirmed, currently trivially satisfiable** |
| "My Business (Profile/Catalogue/Business Library)" + "Resources" nav shape (Screen Inventory §7 row) | Confirmed as *implied*, zero corresponding route/screen exists | **Confirmed as not-yet-built, matches expectation** |
| "Detailed scope comes from Brainstorm 1" (Master Plan §9) | No Brainstorm-1 output found anywhere | **Confirmed gap — see §3** |
| Universal Search named as a possible entry point into another user's Business Profile (Screen Inventory, citing UI Decisions §7) | `UniversalSearchScreen.kt` exists (MVP-1 era), has no Business-Profile-aware result type today | **Future integration point, not a current gap — no action needed until Brainstorm 1 confirms Universal Search is in scope for 1.3 at all** |

---

## 5. Open product decisions — genuine, not resolved by guessing

> **ALL FIVE RESOLVED 2026-08-18 — PDL-019.** §5.1→per-`companyId` scoping. §5.2→the locked field
> list (Business/Trading Name, Legal Name, Address, City, State, Pincode, Phone, Email, GSTIN,
> Website, Logo, Short Business Description). §5.3→no `PartyFieldProvenance`, 100% BUDCOM-owned data.
> §5.4→app-private storage behind a small abstraction. §5.5→owner-side only, no visitor-facing
> Resources in MVP-1.3. §5.6 (Desktop) remains implicitly answered "no" by the same reasoning that
> already applied to MVP-1.2 (§2.3) — not separately re-litigated in PDL-019 since no document has
> ever suggested otherwise. The per-question text below is preserved as the reasoning record.

These are the questions this review could not answer from locked documents alone. Recording them
precisely, with the concrete options this codebase's own architecture makes visible, is this
session's most useful contribution to Brainstorm 1.

### 5.1 Is a Business Profile scoped per Tally `companyId`, or is it genuinely singular across the whole installation? — **the single highest-risk open question**

Every piece of BUDCOM data built so far (Party, notes, issues, Dincharya, vouchers, ledgers) is
scoped to exactly one `companyId` (a paired Tally company), and company isolation is this codebase's
single most heavily-tested architectural invariant (named the dominant risk in every MVP-1.1/1.2
sub-milestone). But the locked UI language is explicitly singular — **"one business identity"** — and
the nav shape is a single global `My Business` shell, not `Company X → My Business`. A real BUDCOM
user may plausibly operate several Tally companies (different GSTINs, branches, or entities) while
having exactly one business they'd recognize as "mine" — or they may want a distinct profile per
registered entity. This codebase currently offers no evidence either way, because nothing has been
built. Three concrete options for Brainstorm 1, none pre-selected here:

- **(a) One profile per `companyId`** — reuses the exact isolation pattern every other table already
  uses; simplest to build correctly; but may not match "one business identity" if a user's several
  paired companies are genuinely one business to them.
- **(b) One profile per installation (device), independent of `companyId`** — matches the literal
  "one business identity" wording; requires a **new, different** isolation/scoping story this
  codebase has never needed before (what happens on a shared device, or if the app is reinstalled and
  re-paired to a different company set?); needs its own explicit persistence/backup story.
  This is the option that gave rise to the "recovery behavior" line of PDL-011 — the answer is not
  obvious and must be a real decision, not an assumption.
  Requires answering: is a Business Profile a Tally-independent concern (`companyId` never involved) or does it optionally *link* to one or more paired companies for display purposes without being *owned* by any one of them?
- **(c) One profile per "branch," itself a new first-class concept above `companyId`** — the
  Master Plan's own Catalogue direction (§10) mentions "branch/draft/review/publish workflow,"
  suggesting a branch concept may be coming regardless; if so, Business Profile might want to
  anticipate it rather than build (a) or (b) and retrofit later. This is the highest-complexity
  option and should only be chosen if Catalogue's own eventual branch model is already reasonably
  well understood, which it currently is not (§6.1's "current direction," not locked scope).

**This review's own reasoning for the record, not a recommendation:** option (a) is dramatically
lower-risk to implement correctly (zero new isolation pattern to invent and test) and can always be
displayed to the user as "one business" in the UI even if internally scoped per-company, deferring
(b)/(c)'s harder questions until real usage evidence exists. But this is exactly the kind of
product/UX judgment call PDL-010 reserves for ChatGPT + Product Owner, not Claude — flagged, not
decided.

### 5.2 Exact field list

No field is locked. §9's Business Profile candidate field table is offered as input, not scope.
Every field needs an explicit yes/no from Brainstorm 1, plus, for each yes, whether it is
Tally-sourceable (and thus needs the existing `FieldProvenance` pattern) or BUDCOM-only.

### 5.3 Editable-vs-Tally-derived semantics, and whether the existing `PartyFieldProvenance` model applies at all

Party's field-provenance model (`ConfirmedFromTally`/`BudcomOnlyPending`/`ExportReady`/`Exported`/
`Conflict`/`EmptyUnknown`, MVP-1.1-A/D) exists because Party fields have a genuine Tally-XML
round-trip. Does any Business Profile field have an equivalent Tally source (e.g., is a business's
own GSTIN/address ever meaningfully "confirmed from Tally," or is a Business Profile 100%
BUDCOM-authored, provenance-free data)? If entirely BUDCOM-only, the whole provenance/export
machinery this review's §8 draft references may be unnecessary complexity (PDL-012 — "complexity must
earn its place") and a simpler model should be preferred. This must be answered before §8's draft
schema is treated as more than a starting sketch.

### 5.4 Asset/logo storage location and size/format policy

BUDCOM has no image-upload precedent anywhere today (only image *display*/*share* via the existing
PDF/FileProvider path). Does a logo/asset live in app-private storage (simplest, no permission
surface, lost on uninstall) or the "Private USB Storage" mechanism already built for MVP-1
(`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §9, survives reinstall, has its own fail-closed
discipline)? This is a real architecture decision with security/recovery implications (PDL-011),
not a UI detail.

### 5.5 Whether MVP-1.3 must ship "Resources" (the visitor-facing view) or only the owner-side shell

UI Decisions §7 names both the owner side (`My Business → Profile/Catalogue/Business Library`) and a
visitor-facing "Resources" view as part of the same ecosystem, but does not say both ship in MVP-1.3
specifically (Catalogue's own visitor-facing exposure plausibly waits for MVP-1.4). Building a
visitor-facing permission-controlled view is materially more complex (a new access-control dimension
this codebase has never needed — every screen today is single-user/owner-only) than an owner-only
profile editor. Recommend Brainstorm 1 explicitly scope MVP-1.3 to **owner-side Profile only**, deferring Catalogue, Business Library, and any visitor-facing "Resources" view to MVP-1.4+, unless
the Master Plan is revisited — but this is offered as a risk observation for Brainstorm 1 to weigh,
not a decision this review is making.

### 5.6 Whether Desktop needs any Business Profile surface

Not addressed by any locked document. §2.3 confirms Desktop currently has zero relevant surface, same
as MVP-1.2. Absent an explicit decision otherwise, this review assumes MVP-1.3 is Android-only,
matching the MVP-1.2 precedent — but this was an explicit, evidenced architecture-review conclusion
for MVP-1.2 (Desktop's renderer literally has no Party/Connect code), not something to silently
assume applies identically here without the same check being redone once real scope exists (§2.3's
own check already reduces this risk, but a final confirmation belongs in Brainstorm 1).

---

## 6. Product boundary — explicit classification

### MUST HAVE (if MVP-1.3 proceeds at all, per locked docs)

- A single, owner-editable Business Profile record (exact scope per §5.1).
- Respect the locked ownership boundary: Connect/Vartalap must be able to *reference* this data later
  without MVP-1.3 forking a second copy anywhere.
- Offline-capable by construction (matches every existing BUDCOM screen — no document suggests an
  exception).
- Company-isolated in whatever sense §5.1 resolves to (this invariant does not disappear even if the
  scoping boundary changes shape).

### SHOULD HAVE (aligned, not required for a minimal MVP-1.3)

- Logo/asset upload (real product value, but adds a whole new storage-permission surface — §5.4 —
  that a text-only-fields MVP-1.3-A could defer to a later sub-milestone within 1.3).
- Dashboard entry point (`My Business`), mirroring Connect's/Dincharya's own fourth/fifth
  `HomePrimaryEntryRow` precedent — likely required for the feature to be reachable at all, so
  probably MUST HAVE in practice, listed here only because no document explicitly locks the exact nav
  placement beyond "global shell."

### DEFER (valid future work, not MVP-1.3)

- Catalogue itself (MVP-1.4, explicitly sequenced after).
- Visitor-facing "Resources" view (§5.5).
- Any Universal-Search integration for discovering another user's Business Profile (mentioned once in
  Screen Inventory, not elaborated anywhere).
- Business Library / asset library beyond a single logo, if Brainstorm 1 decides MVP-1.3 should be
  narrower than the full §7 ecosystem shape.

### NOT NOW (explicitly prohibited absent a fresh, explicit product document)

Carried forward unchanged from every prior milestone's own locked exclusions — re-confirmed still
applicable, not re-litigated:

- Generative AI of any kind (`docs/planning/BUDCOM-NOT-NOW.md` "Advanced generative AI dependency";
  PDL-016).
- Cloud sync (no document authorizes it anywhere in the roadmap).
- Vartalap implementation (consumes Business Profile data later; has no roadmap milestone of its own
  yet — Screen Inventory's own words).
- Referral Tree / RJ Concept (PDL-015).
- Home Insights / OI engine (PDL-016).
- OS-level notifications (PDL-017; nothing in Business Profile's own scope suggests revisiting this).
- Public marketplace / social feed (`BUDCOM-NOT-NOW.md`, and Master Plan §9's own explicit warning
  against "premature marketplace/social-network expansion" — the single exclusion the Master Plan
  names *specifically* for this milestone).
- Catalogue implementation pulled forward into 1.3 (§6.2/§10 below).
- Any Desktop, Connector, or Tally-write change (§2.3 confirms none needed; do not introduce one).

---

## 7. No-over-engineering discipline (PDL-012, applied)

Every architectural choice below is deliberately anchored to a pattern this codebase already proves
works, not a new abstraction invented for this milestone:

- Field-level provenance (if §5.3 confirms it's needed at all) reuses `PartyFieldProvenance`'s exact
  shape — never a second provenance model.
- Company/ownership scoping (whichever §5.1 option is chosen) reuses the existing natural-key
  discipline (`companyId` as a leading column, no Room `ForeignKey`, matching every table since
  MVP-1.1-A) — never a new identity scheme invented from scratch.
- If asset storage is needed, reuse the existing `FileProvider`/cache-file pattern
  (`PartyXmlExportCacheFileSystem.kt`) or the existing Private USB Storage mechanism (§5.4) — never a
  third file-handling implementation.
- UI reuses the established Compose conventions proven since MVP-1.1: self-describing
  `TextButton`/`Card`, `MasterDataLoadingIndicator`/`MasterDataErrorBlock` shared components, honest
  empty/error states, `HomePrimaryEntryRow` for the Dashboard entry — no new visual system, per UI
  Decisions §8's own "presentation-layer only" boundary.
- No AI, no cloud, no generic CRM/marketplace expansion — §6's NOT NOW list is a hard boundary, not a
  suggestion.

---

## 8. Candidate data model (draft — not locked, depends entirely on §5.1/§5.2/§5.3/§5.4)

Offered as one concrete, internally-consistent starting sketch assuming §5.1 resolves to option (a)
(per-`companyId` scoping, this review's own lower-risk reading, not a decision) and §5.3 resolves to
"some fields have real Tally provenance, most don't." **If Brainstorm 1 picks differently, this
section should be substantially rewritten, not patched.**

### 8.1 `business_profile` (new table, one row per `companyId`)

| Column | Type | Notes |
|---|---|---|
| `companyId` | TEXT NOT NULL | Primary key (single-column, since this is a singleton per company under option (a) — no second natural-key component needed, unlike every other Party-adjacent table) |
| `businessName` | TEXT | BUDCOM-only unless a real Tally company-name field exists to reconcile against (§5.3) |
| `logoAssetId` | TEXT NULL | Points into an asset table/file store (§8.3), never an inline BLOB |
| `phone`, `email`, `addressLine1`, `addressCity`, `addressState`, `addressPincode`, `gstin` | TEXT NULL | Candidate fields only — §5.2 must confirm each; several of these already have an established validation/normalization utility (`PhoneNumberNormalizer`) that should be reused verbatim if phone is included, exactly as Party already does |
| `websiteUrl` | TEXT NULL | Candidate only, not locked (§5.2) |
| `createdAt`, `updatedAt` | INTEGER NOT NULL | Matches every existing entity's timestamp convention |

**Explicitly not drafted:** UPI, bank details, social links — zero precedent or locked requirement for
any of these anywhere in the repository; including them here would be inventing scope, exactly what
this review must not do (§3).

### 8.2 `business_profile_field_provenance` (new table — only if §5.3 confirms any field has real Tally provenance)

Would mirror `PartyFieldProvenanceEntity` exactly (`companyId`, `fieldName`, `state`, `tallyValue`,
`budcomValue`, `lastConfirmedAt`, `lastExportedAt`, `updatedAt`) — same reasoning as MVP-1.1-A's own
"normalized per-field rather than one provenance column per field" decision. **Do not build this table
if §5.3 concludes Business Profile is BUDCOM-only data with no Tally round-trip** — it would be
unjustified complexity (PDL-012).

### 8.3 Asset storage (shape depends entirely on §5.4)

If app-private storage: a simple `business_profile_assets` table (`companyId`, `assetId`, `fileName`,
`mimeType`, `createdAt`) pointing at files in app-private storage, served via the existing
`FileProvider` for any share/display need — no migration risk beyond a normal additive table. If
Private USB Storage: reuse that subsystem's existing fail-closed/no-silent-fallback discipline
directly rather than inventing a second storage-reliability story.

### 8.4 Migration

A single additive `MIGRATION_9_10` (new tables only, matching the "zero new migration needed unless a
genuine table is added" discipline every MVP-1.2 sub-milestone followed) — **only after §5.1–§5.4 are
answered**, since the exact columns depend on those answers. Building this migration before Brainstorm
1 would risk the same "ALTER an already-wrong table" rework MVP-1.2-A's own migration discipline
exists to avoid.

---

## 9. Candidate UI/UX shape (draft — not locked)

- Fifth Dashboard primary entry (`HomePrimaryEntryRow`, mirroring Connect's MVP-1.1-B and Dincharya's
  MVP-1.2-D additions exactly) — "My Business," reachable from a new `Routes.BUSINESS_PROFILE`
  no-arg route (the `Routes.SETTINGS`-style template, not `Routes.CONNECT`'s query-arg template,
  matching Dincharya's own precedent for a screen with no search parameter).
- A single-screen editor (matching Party Detail's field-edit-dialog pattern: tap a field row, edit in
  an `AlertDialog`, save) rather than a multi-step wizard — no locked document asks for a wizard, and
  this repository has no wizard-flow precedent to reuse (Prospect creation is the closest analog: one
  screen, optional fields, no accounting requirement).
- Honest empty state on first open (no business name saved yet) — matches Connect's/Dincharya's own
  empty-state discipline exactly, never a blank screen.
- If logo upload ships in MVP-1.3 (§6.2, SHOULD HAVE not MUST HAVE): a simple image picker + preview,
  no cropping/editing tool unless Brainstorm 1 asks for one (avoid inventing UI capability this
  codebase has never needed).

---

## 10. MVP-1.4 Catalogue dependency — what 1.3 must establish without building 1.4

Per the Master Plan §10 and UI Decisions §7, Catalogue shares Business Profile's ownership boundary
but is a distinct, later milestone. MVP-1.3 should establish exactly enough for Catalogue to build on
later, and no more:

- **Stable business identity to attach products to** — whatever `companyId`/profile-identity shape
  §5.1 resolves to must be usable as Catalogue's own ownership key later, without a second identity
  scheme.
- **Logo/branding availability** — if MVP-1.3 ships asset storage (§5.4), Catalogue can reuse it
  directly for product images later (Master Plan §10's own "image filenames aligned with SKU IDs, DB-
  stored linkage" language suggests a similar asset-linkage pattern, not necessarily the same table).
- **Explicitly NOT MVP-1.3's job:** any product/SKU table, Excel import/export, branch/draft/review/
  publish workflow (Master Plan §10's own "current direction" for 1.4, not 1.3), or any catalogue UI
  of any kind. None of this should appear in an MVP-1.3 implementation prompt.

---

## 11. Company isolation strategy

Whatever §5.1 resolves to, the same discipline this codebase has proven repeatedly (most recently and
rigorously in Dincharya, MVP-1.2-D) applies without exception: every DAO query touching Business
Profile data must bind its scoping key directly in SQL — never left to UI-layer filtering, never
assumed safe because "there's only one row." If option (a) (`companyId`-scoped) is chosen, this is
the same proven pattern already tested throughout MVP-1.1/1.2 — low risk. If option (b) or (c) is
chosen, this is **new territory requiring its own adversarial test design from scratch** (a
device/installation identity boundary has no existing precedent or test pattern in this codebase to
copy), and should be flagged as elevated risk in whatever implementation plan follows Brainstorm 1.

Explicit adversarial test requirement once implementation begins (regardless of which option is
chosen): two companies (or two installations, if (b)/(c)) with deliberately identical business names,
phone numbers, and addresses must never leak into one another — the same named, blocking, explicit
test discipline every MVP-1.1/1.2 sub-milestone has required, not incidental.

---

## 12. Offline-first review

Every operation in the §8/§9 candidate design is local-Room-only by construction — matches every
existing BUDCOM screen, no document suggests otherwise, and no network dependency should be
introduced without a fresh, explicit product decision (none exists). Classification:

| Operation | Classification |
|---|---|
| View/edit Business Profile fields | Fully offline |
| Save logo/asset (if in scope, §5.4) | Fully offline (local file write) |
| Any future Tally reconciliation of a provenance-tracked field (§5.3, only if applicable) | Requires the existing on-demand "Check Tally" pattern (MVP-1.1-D), never automatic |
| Any future visitor-facing "Resources" sharing (§5.5, deferred) | Undetermined — likely requires whatever sharing mechanism Vartalap/Connect eventually use; out of MVP-1.3 scope to design now |

---

## 13. Test strategy (for whichever implementation plan Brainstorm 1 authorizes)

Mirrors the exact discipline proven across MVP-1.1/1.2, not reinvented:

- **Unit (domain/use case/repository):** JVM tests with fake DAOs, following `DincharyaRepositoryImplTest`'s
  own pattern (simple in-memory fakes, no re-testing of SQL predicate correctness at this layer).
- **DAO (instrumented, real Room):** CRUD, company isolation (the named adversarial test, §11),
  empty/sparse data, and — only if a schema change is real — a `MigrationTestHelper`-driven migration
  test following `AppDatabaseMigrationTest`'s established pattern.
- **ViewModel:** loading/success/empty/error states, company-switch behavior (mirrors
  `DincharyaViewModelTest`'s `switching companies triggers a fresh load` test if option (a) is chosen).
- **Compose/UI:** field editing, save/cancel, empty state, accessibility (self-describing controls,
  no color-alone signal — the exact discipline MVP-1.2-E's own audit re-confirmed), long-text
  handling.
- **Regression:** full `testDebugUnitTest`/`testReleaseUnitTest`/both lints/all three assembles/
  `connectedDebugAndroidTest`, with the same "compare against the known 12-failure device-viewport
  baseline, investigate any extra, never silently accept" discipline this repository has now applied
  consistently across five sub-milestones.
- **Performance:** only relevant if a cross-`companyId` or cross-installation bounded query is
  introduced — a per-company singleton profile row has no large-fixture performance question the way
  Dincharya's cross-party queries did; do not manufacture one.

---

## 14. Proposed sub-milestone structure (draft — contingent on Brainstorm 1's scope decision)

Not a mechanical copy of MVP-1.2's A–E rhythm — sized to what a minimal, product-confirmed Business
Profile actually needs, which is architecturally far smaller than Dincharya was (a single-row-per-
company editable form, not a new cross-party query surface):

- **MVP-1.3-A — Data foundation + minimal editor.** Migration (§8.4), repository/use-case layer,
  Dashboard entry + route, a plain field-editor screen (text fields only, no logo). Acceptance gate:
  full regression green, company-isolation adversarial test passing, offline-verified.
- **MVP-1.3-B — Logo/asset support** (only if §5.4/§6 confirm this ships in 1.3 at all, not deferred
  to a later milestone or dropped). Asset storage decision from §5.4 implemented, image picker UI.
- **MVP-1.3-C — Integrated hardening + freeze**, mirroring MVP-1.2-E's own proven rhythm exactly
  (integrated audit, migration re-verification, accessibility pass, one coherent version bump, final
  candidate build/install/smoke-test).

A three-part A/B/C structure is proposed instead of a five-part A–E structure because the confirmed-
zero-code starting point and the (currently) much narrower likely scope (one form, one table, no
cross-party query) do not obviously justify MVP-1.2's five-way split — **but this sizing itself is a
Brainstorm 1 input, not a locked decision**, since the actual scope (especially §5.1's outcome and
whether §5.5's visitor-facing view is pulled into 1.3) could change this materially.

**What must NOT happen in any sub-milestone:** Catalogue implementation of any kind (§10); a visitor-
facing "Resources" view unless §5.5 explicitly authorizes it; any Desktop/Connector/Tally change;
any AI/cloud/marketplace feature (§6).

---

## 15. Highest risks, ranked

1. **§5.1 (company-scoping shape)** — the one question whose answer changes the data model's primary
   key, the isolation test strategy, and the migration shape. Must be resolved before any schema is
   written, or rework is guaranteed (the same class of risk MVP-1.2-A's own "ALTER a wrong table"
   near-miss illustrates).
2. **§5.5 (visitor-facing Resources)** — if pulled into MVP-1.3, this introduces a genuinely new
   access-control dimension (multi-user visibility) this codebase has never needed; materially
   higher risk than an owner-only editor.
3. **§5.4 (asset storage)** — a real security/recovery-behavior decision (PDL-011), not a UI detail;
   getting it wrong risks either a silent-data-loss-on-reinstall surprise or an unjustified new
   permission surface.
4. **Scope creep toward Catalogue** — the roadmap explicitly sequences Catalogue after Business
   Profile for a reason; pulling SKU/product concepts forward would violate PDL-012 and the Master
   Plan's own sequencing.
5. **Marketplace/social-feed drift** — the one exclusion the Master Plan names *specifically* for this
   milestone (§9's own text); worth naming as its own risk rather than folding into the generic NOT
   NOW list, given the explicit callout.

---

## 16. Claude autonomy boundary for the eventual implementation session(s)

Once Brainstorm 1 produces a locked scope and an implementation prompt, the implementing Claude
session may autonomously: inspect code, implement the approved scoped sub-milestone, write tests, run
builds/lint/instrumented tests, debug within approved scope, mini-harden, update documentation,
commit, prepare reports, and push only when the task explicitly authorizes it (matching MVP-1.2-D's
own "do not push" boundary versus MVP-1.2-E's own explicit push authorization — this is decided
per-task, not assumed).

It must not: change the locked scope Brainstorm 1 produces; alter the company-isolation invariant;
add AI/cloud/marketplace capability under any framing; pull Catalogue implementation forward; install
on the owner's device without explicit authorization; change signing configuration; force-push,
rewrite history, or touch unrelated work. If a genuine new architectural ambiguity appears mid-
implementation that Brainstorm 1's brief didn't anticipate, the correct response is the same one this
review itself followed for §5: stop, record the ambiguity precisely, and do not guess.

---

## 17. Recommended next task

**Not implementation.** The exact next task is **Brainstorm 1 (User + ChatGPT)** for MVP-1.3 Business
Profile, using this document as the starting input — specifically resolving §5.1 (company-scoping
shape, highest priority), §5.2 (field list), §5.3 (provenance applicability), §5.4 (asset storage),
and §5.5 (visitor-facing scope) — after which the output should be recorded as new Product Decision
Log entries (mirroring PDL-014–018's own pattern for MVP-1.2) and a revised/confirmed version of this
architecture document, at which point a fresh Claude session can be issued an MVP-1.3-A implementation
prompt directly, the same way `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md`
"Checkpoint" section made MVP-1.2-D directly executable without re-deriving this analysis.

---

## 18. Source documents this review reconciled

`docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`, `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md`,
`docs/design/BUDCOM-SCREEN-INVENTORY.md` (corrected, §2.4), `docs/planning/BUDCOM-NOT-NOW.md`,
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` (PDL-001 through PDL-018 read in full),
`docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`, `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`,
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`, `docs/status/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md`,
`docs/architecture/BUDCOM-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-OI-ARCHITECTURE.md` (as the direct
structural precedent for this document), a full repository source search (Android/Desktop/Connector),
and a direct inspection of the external design archive at `D:\BUDCOM-Design-Archive\` (confirmed to
exist locally; confirmed to contain zero Business-Profile/Catalogue content in any of its six
folders).
