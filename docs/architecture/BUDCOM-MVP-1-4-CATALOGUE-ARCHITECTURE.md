# BUDCOM MVP-1.4 — Catalogue — Architecture

**Status:** Planning/recovery review complete (2026-08-19). **Not yet authorized for
implementation.** No Brainstorm 1 (User + ChatGPT) has been performed for MVP-1.4 anywhere in this
repository or the external design archive — this document inventories the actual repository state,
reconciles documentation, and separates what is genuinely locked from what is only a proposed
direction, exactly as the MVP-1.3 architecture document did for Business Profile before its own
PDL-019. It does not invent product scope.

---

## 1. Executive summary

MVP-1.4 is named **Catalogue** in the locked roadmap
(`docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` §2, §10). Its **ownership/visual shape** is
locked (`docs/design/BUDCOM-UI-DESIGN-DECISIONS.md` §7): Catalogue shares Business Profile's single
identity/asset-library boundary — "one business identity, one catalogue, one asset library" exposed
through multiple permission-controlled views; Connect and Vartalap consume this data, they do not
own it. Its **detailed field-level/screen-level scope is explicitly not locked** — the Master Plan's
own §10 labels its bullet list "Current direction," the same non-binding status its §9 used for
Business Profile before that milestone's own Brainstorm 1 (PDL-019). No Catalogue-specific
Brainstorm has happened.

**Zero code exists anywhere in this repository (Android, Desktop, Connector) for Catalogue** —
confirmed by direct repository search (§2), not assumed. MVP-1.4 starts from a true implementation
baseline of zero, but with real groundwork already in place: a locked ownership contract, a roadmap
position, and — newly confirmed this session — an MVP-1.3 foundation (Business Profile identity +
asset storage) and a pre-existing MVP-1 Stock Item local-first foundation that Catalogue can build
on without inventing either from scratch.

This document's purpose: do everything a planning/recovery review can honestly do without inventing
product scope — inventory the repository, reconcile documentation, separate LOCKED / PROPOSED / OPEN
PRODUCT DECISION, and hand the Product Owner + ChatGPT exactly the open questions Brainstorm 1 needs
to resolve.

---

## 2. Repository inventory — Catalogue implementation search

A full repository search (Android Kotlin, Desktop TypeScript/React, Connector TypeScript, and all
`docs/`) for Catalogue / product-listing / SKU / branch-draft-review-publish terminology found:

| Bucket | Finding |
|---|---|
| **IMPLEMENTED** | None. Zero. |
| **PARTIALLY IMPLEMENTED** | None. Zero. |
| **DOCUMENTED ONLY** | The real substance — see §3/§6. |
| **OBSOLETE/DEFERRED** | None — nothing was ever started, so nothing to clean up. |

Explicit checks:

- `AppDatabase.kt` `entities = [...]`: no `catalogue_items`, `catalogue_products`, `catalogue_branches`,
  or `catalogue_assets` table. Current list (21 entities, MVP-1.3-frozen): `CompanyEntity`,
  `CompanyDiscoveryMetaEntity`, `LedgerEntity`, `StockItemEntity`, `VoucherEntity`,
  `VoucherDetailEntity`, `VoucherLedgerLineEntity`, `VoucherInventoryLineEntity`,
  `VoucherCacheMetaEntity`, `PairedConnectorEntity`, `LedgerStatementEntity`,
  `LedgerStatementTransactionEntity`, `PartyEntity`, `PartySourceLinkEntity`,
  `PartyFieldProvenanceEntity`, `PartyContactPersonEntity`, `TagEntity`, `PartyTagCrossRefEntity`,
  `PartyNoteEntity`, `PartyExportEventEntity`, `PartyIssueEntity`, `BusinessProfileEntity`.
- `Routes.kt`: no catalogue/product/branch route exists.
- Connector: zero endpoint plausibly related to catalogue data, consistent with its locked
  100%-read-only-Tally-proxy boundary — a BUDCOM catalogue is BUDCOM-native data layered *on top of*
  (not owned by) Tally Stock Items, so this is expected, not a gap.
- Desktop renderer: zero Catalogue UI surface, same pattern as Business Profile and every prior
  milestone.

### 2.1 What MVP-1.3 actually delivered, confirmed for Catalogue to build on

The MVP-1.3 architecture document's own §10 ("MVP-1.4 Catalogue dependency — what 1.3 must establish
without building 1.4") specified two things 1.3 needed to leave behind. Both are confirmed present
in the frozen MVP-1.3 code, inspected directly this session:

- **Stable, `companyId`-scoped business identity to attach products to.** `business_profile` table
  (`MIGRATION_9_10`, `core/database/DatabaseModule.kt`): `companyId TEXT NOT NULL PRIMARY KEY`,
  matching `PartyEntity`'s own natural-key discipline. A future Catalogue table can use the same
  `companyId` as its isolation/ownership key without inventing a second identity scheme.
- **Logo/asset storage availability.** `business_profile.logoAssetPath` plus the app-private
  `BusinessProfileLogoStore` abstraction (file-type allowlist, 5 MB streaming cap, two-layer
  path-traversal defense — MVP-1.3-A). Directly reusable *pattern* for product images; §10 already
  flagged this is "not necessarily the same table" — confirmed still an open question, not resolved
  by MVP-1.3 shipping.

### 2.2 What already exists that Catalogue would naturally read from: Stock Items

Unlike Business Profile (which started from zero), Catalogue has an existing, already-synced,
already-local-first foundation to build on: `cached_stock_items` / `StockItemEntity`
(`feature/masterdata/stockitem/data/local/StockItemEntity.kt`), part of the MVP-1 Master Data
foundation (`docs/ROADMAP.md` "Completed"):

```
companyId, id, name, alias, parentGroup, category, baseUnit, partNumber, hsnCode, gstRate,
status, closingAmount, closingCurrencyCode, closingSide, dataQuality, syncedAt, dataFreshnessAt
```

This gives a future Catalogue a stable, GUID-first identity (`id`), name, HSN/part-number fields,
and closing stock value already synced offline via the same Room-first architecture Ledger uses
(`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §26 just re-proved this pattern live). **Whether/how a
Catalogue product record relates to a Stock Item record (one-to-one draft source? optional link?
fully independent?) is an open product decision (§5.2), not resolved by this entity existing.**

**Known related limitation, discovered the same session (TD-035,
`docs/technical-debt/registry.md`):** `StockItemEntity.parentGroup` is *also* `NULL` for real data,
for the same Connector-side reason as `LedgerEntity.parentGroup` (the shared `PARENT` FETCH-field
exclusion). If a future Catalogue design wants to group products by their Tally Stock Group, it will
hit the identical gap Connect's Party classification hit — worth Brainstorm 1 knowing about before
any such design commits to it.

### 2.3 No document conflicts found

No two authoritative documents materially disagree about MVP-1.4's identity, sequencing, or
ownership boundary. The Master Plan (§10), UI Design Decisions (§7), and the MVP-1.3 architecture
document (§4, §10, §15) are mutually consistent everywhere they overlap.

---

## 3. Cross-reference table

| Claim | Source | Verified against repository |
|---|---|---|
| MVP-1.4 = Catalogue, same ownership boundary as 1.3 | Master Plan §2/§10, UI Decisions §7 | **Confirmed** |
| "One business identity, one catalogue, one asset library" | UI Decisions §7 | **Confirmed, architecturally binding — no code contradicts it (there is no code)** |
| Connect/Vartalap consume, never own, Catalogue data | UI Decisions §7 | **Confirmed; Connect exists today with zero Catalogue dependency to retrofit — boundary trivially satisfiable from a clean slate** |
| "My Business (Profile/Catalogue/Business Library)" + "Resources" nav shape | Screen Inventory §7 row | **Confirmed as implied, zero corresponding route/screen exists** |
| "Detailed scope" is a current direction, not locked | Master Plan §10's own "Current direction" heading | **Confirmed gap — no Brainstorm 1 output found anywhere, see §4** |
| MVP-1.3 delivers a reusable identity + asset-storage foundation | MVP-1.3 architecture doc §10 | **Confirmed delivered, §2.1 above** |
| Existing Stock Item local-first foundation | `docs/ROADMAP.md` "Completed", `StockItemEntity.kt` | **Confirmed present and reusable, §2.2 above** |

---

## 4. Brainstorm gap

A full search of `docs/` and (by description; not independently re-inspected this session — the
MVP-1.3 review already confirmed it empty for both Business Profile and Catalogue) the external
design archive found no "Brainstorm 1" output for Catalogue anywhere. This is the same state MVP-1.2
and MVP-1.3 were each in before their own dedicated planning sessions produced PDL-014–017 and
PDL-019 respectively. This review reaches the equivalent conclusion for MVP-1.4: **the
ownership/ecosystem shape is locked; the product brainstorm is not done; implementation must not
begin until it is.**

---

## 5. LOCKED / PROPOSED / OPEN — the classification this task exists to produce

### 5.1 LOCKED (already explicitly decided, no fresh decision needed)

- MVP-1.4 = Catalogue, positioned immediately after MVP-1.3 in the locked post-MVP sequence
  (Master Plan §2).
- Shares Business Profile's single ownership boundary: one business identity, one catalogue, one
  asset library, multiple permission-controlled views (UI Decisions §7).
- Connect and Vartalap consume this data; neither may fork a second copy of catalogue/asset data
  (UI Decisions §7).
- Cross-roadmap architecture principles apply unchanged (Master Plan §3): Android lightweight and
  local-first; Room/local persistence authoritative for display where appropriate; sync
  incremental/idempotent where practical; queries indexed/bounded/paged; Connector identity must not
  depend on fixed laptop IP; BUDCOM does not directly write to Tally — user-initiated
  compatible-XML export/import remains the boundary unless explicitly changed; evidence before
  expansion; avoid premature CRM/marketplace/social-feed expansion.
- NOT-NOW exclusions apply (`docs/planning/BUDCOM-NOT-NOW.md`): no public marketplace/social feed;
  no generic CRM expansion; no direct Tally write-back; no advanced generative-AI dependency.
- Post-MVP feature cadence: mini-hardening per coherent slice, integrated hardening before release
  (PDL-006).
- MVP-1.3's own foundation (company-scoped identity, app-private asset-storage pattern) is available
  and confirmed delivered (§2.1).

### 5.2 PROPOSED (Master Plan §10's own "Current direction" — architecturally sensible, not yet a
Product Owner/ChatGPT-approved PDL entry)

- Master catalogue table.
- Stable product/SKU IDs.
- Excel import/export.
- Image filenames aligned with SKU IDs, DB-stored linkage.
- Branch/draft/review/publish workflow.
- Approved image organization/brand-replacement workflow.

These are recovered *intent*, not locked scope. None should appear in an implementation prompt
without an explicit PDL entry resolving the open questions in §5.3, mirroring exactly how MVP-1.3's
own "Detailed scope comes from Brainstorm 1" language was resolved by PDL-019 before MVP-1.3-A began.

### 5.3 OPEN PRODUCT DECISIONS (require an explicit Brainstorm 1 + PDL entry — not answered here)

1. **Product/SKU field list.** What fields does a Catalogue product actually carry (name, price,
   description, category, unit, tax/HSN reuse from Stock Item, multiple images, stock-on-hand
   display)? No locked list exists, unlike Business Profile's now-locked 12-field set (PDL-019 §5.2).
2. **Relationship to Tally Stock Items.** Does a Catalogue product always originate as a *draft* from
   an existing Stock Item (§2.2's `StockItemEntity`)? Can a product exist with no Stock Item link at
   all? If linked, is the link one-to-one, optional, or many-to-one (e.g. a bundled/kit product)? What
   happens to a published product if its source Stock Item is later renamed, deactivated, or deleted
   in Tally?
3. **Branch/draft/review/publish state machine.** Exact states, allowed transitions, who may act at
   each transition, and whether "branch" becomes a first-class concept above `companyId` the way the
   MVP-1.3 architecture document's own §6.1(c) speculated it might (that document explicitly flagged
   this as "should only be chosen if Catalogue's own eventual branch model is already reasonably well
   understood, which it currently is not" — still true; this document does not resolve it either).
4. **Excel import/export exact contract.** Column schema, validation rules, how a re-import
   reconciles against local edits made in the app since the last export (conflict/precedence rule),
   and whether Excel is a one-time seeding tool or a recurring two-way sync surface.
5. **Image/asset storage mechanism.** Reuse Business Profile's exact `BusinessProfileLogoStore`
   abstraction directly (small change, proven pattern), or a distinct but architecturally consistent
   store sized for potentially many more and larger product images? §2.1 confirms the *pattern* is
   reusable; whether the *same table/store* is reused is explicitly still open (MVP-1.3 architecture
   doc §10 already flagged this "not necessarily the same table").
6. **Visitor-facing "Resources"/Catalogue view timing.** Does MVP-1.4 ship the permission-controlled
   visitor-facing view at all, or owner-side-only first (mirroring PDL-019 §5.5's precedent for
   Business Profile, which explicitly deferred visitor-facing scope "to MVP-1.4 and later")? This is
   the same access-control-surface risk PDL-019 flagged for 1.3 — this codebase has still never
   shipped a genuine multi-permission-level view of anything.
7. **Desktop surface.** MVP-1.2 and MVP-1.3 were both Android-only by explicit architecture-review
   conclusion (absent a decision otherwise). Does that precedent hold for Catalogue too, given Excel
   import/export might plausibly be a Desktop-side operation?
8. **Sharing/distribution mechanism.** UI Decisions §7 names "shared product/catalogue/resource link"
   only as a future *access origin* into Business Profile generally — it is not a locked MVP-1.4
   sharing mechanism (QR? deep link? WhatsApp share, mirroring the existing Ledger/Voucher PDF-share
   pattern? a web-hosted view?).
9. **Company isolation shape.** Presumably `companyId`-scoped like every other table (§2.1's
   foundation supports this trivially), but per PDL-011's own rule this must be an explicit decision
   recorded in Brainstorm 1's output, not silently inferred here.

---

## 6. No-over-engineering discipline (PDL-012, applied)

Any eventual Catalogue architecture should anchor to patterns this codebase already proves work, not
invent new abstractions for this milestone:

- **Local-first read pattern** — reuse the exact Ledger/Voucher/Stock Item cache-only-vs-explicit-
  refresh split (`LedgerRepositoryImpl.listLedgers()`/`refreshLedgers()`, just re-proven and hardened
  in Phase 36/37/38) rather than inventing a new sync architecture for Catalogue.
- **Company-scoped natural key** — reuse `companyId` as the isolation boundary, matching every
  existing table, rather than a new tenancy model.
- **Asset storage** — reuse the app-private storage *pattern* MVP-1.3 already built and proved
  (allowlist, size cap, path-traversal defense), even if the concrete store differs (§5.3.5).
- **PartySourceLink-style linkage, not a second identity system** — if/when a product ever needs to
  reference a Party (e.g., "who created this draft"), reuse the existing linkage pattern rather than
  inventing parallel identity plumbing (the same principle the Prospect→Ledger future-capability
  item, `docs/planning/BUDCOM-NOT-NOW.md`, already commits to for Connect).

---

## 7. Company isolation strategy (candidate, not decided — §5.3.9)

Whatever Brainstorm 1 resolves, the discipline this codebase has proven repeatedly (most recently
Dincharya MVP-1.2-D, Business Profile MVP-1.3-A, and the Ledger/Sync fixes in Phase 37/38) should
apply without exception if adopted: every DAO query touching Catalogue data scoped by `companyId`
first; no cross-company leakage possible even under a company-switch race; adversarial
company-isolation tests at the DAO/repository/ViewModel layers, mirroring MVP-1.3-A's own 35-test
precedent.

---

## 8. Test strategy (candidate)

No Catalogue-specific test strategy can be locked before the data model itself is locked (§5.3.1–3).
The pattern to reuse once it is: JVM unit tests for repository/use-case/ViewModel logic (the vast
majority of this codebase's ~1,259 current tests), a small number of `androidTest` Compose UI tests
per screen, adversarial company-isolation tests wherever a new table/query is added, and a
regression test for whatever Excel-import conflict rule §5.3.4 resolves to (that rule is exactly the
kind of "silent data loss" risk this codebase's own Development Ledger has repeatedly caught in
review before it shipped).

---

## 9. Offline behavior (candidate, not decided)

If Catalogue follows the Ledger/Stock Item precedent (§6), browsing an already-synced/drafted
catalogue should work fully offline from Room, exactly as Phase 37 just live-proved for Ledgers —
this is a reasonable default expectation for Brainstorm 1 to confirm or override, not an assumption
this document treats as locked.

---

## 10. Migration requirements (candidate)

A new `catalogue_*` table (or tables) would be an additive Room migration, following the exact
`MIGRATION_9_10` precedent (`CREATE TABLE IF NOT EXISTS`, no existing table touched). No migration
requirement can be more specific than that until the schema itself (§5.3.1) is locked.

---

## 11. Performance risks (candidate)

- A "master catalogue table" (§5.2) with many products and multiple images each could be
  significantly larger than any existing local dataset (Business Profile is a single row; Stock
  Items/Ledgers are large but text-only). Image storage/thumbnailing strategy should be evidenced,
  not assumed, once Brainstorm 1 confirms image-per-product cardinality.
- Excel import of a large product set should be bounded/paged/streamed, not a single unbounded
  parse — the same discipline `RefreshVouchersUseCase`'s complete-window pagination already
  establishes as this codebase's norm for bulk operations.

---

## 12. Proposed sub-milestones

**Not decided — offered only as a structural option, mirroring the MVP-1.3-A/B/C precedent, for
Brainstorm 1 to accept, reject, or reshape:**

- **1.4-A (candidate):** Data model + local-first read path for a Catalogue product, sourced from
  §5.3.1–2's resolved schema; owner-side list/detail screens only; no Excel, no branch/publish
  workflow yet — mirrors how MVP-1.3-A shipped a basic whole-entity editor before MVP-1.3-B's
  richer sharing-foundation work.
- **1.4-B (candidate):** Image/asset attachment (§5.3.5) + Excel import/export (§5.3.4).
- **1.4-C (candidate):** Branch/draft/review/publish workflow (§5.3.3) + integrated hardening,
  version bump, freeze — mirroring MVP-1.3-C's own "integrated hardening, freeze, final candidate"
  shape.

Use the proven A/B/C rhythm only if it actually fits the scope Brainstorm 1 produces — do not
mechanically force it (this task's own governing instruction).

---

## 13. What must NOT happen in any sub-milestone

Catalogue implementation of any kind beyond what an explicit PDL entry authorizes; a visitor-facing
Resources view unless §5.3.6 explicitly authorizes it; any Desktop/Connector/Tally-write change; any
AI/cloud/marketplace feature (§5.1); pulling Prospect→Ledger linking (a separate, unrelated future
Connect capability, `docs/planning/BUDCOM-NOT-NOW.md`) into this milestone; reversing TD-035's
Connector defense-in-depth decision as a side effect of Catalogue work without its own explicit
product decision.

---

## 14. Highest risks, ranked

1. **§5.3.1/§5.3.2 (schema + Stock Item relationship)** — the two questions whose answers change the
   data model's primary shape; getting either wrong risks a costly re-migration once real drafts
   exist.
2. **§5.3.3 (branch/draft/review/publish state machine)** — the highest-complexity open question;
   the MVP-1.3 architecture document already flagged this as not yet well enough understood to
   anticipate from Business Profile, and that remains true here.
3. **§5.3.6 (visitor-facing timing)** — repeats MVP-1.3's own highest access-control risk; this
   codebase has still never shipped a genuine multi-permission-level view of anything.
4. **TD-035 spillover** — if Brainstorm 1 wants Stock-Group-based product organization, it will hit
   the same Connector `PARENT`-exclusion gap Connect's Party classification hit; worth flagging
   before, not after, a design commits to it.
5. **Marketplace/social-feed drift** — the Master Plan names this exclusion specifically and
   repeatedly across every downstream milestone; worth naming as its own risk here too.

---

## 15. Claude autonomy boundary for the eventual implementation session(s)

Once Brainstorm 1 produces a locked scope (its own PDL entry, mirroring PDL-019), the implementing
session should follow it precisely. It must not: invent scope Brainstorm 1 didn't lock; alter the
company-isolation invariant; add AI/cloud/marketplace capability under any framing; add a Desktop or
Connector surface unless Brainstorm 1 explicitly authorizes one; install on the owner's device
without explicit authorization; change signing configuration; force-push, rewrite history, or touch
unrelated work. If a genuine new architectural ambiguity appears mid-implementation that Brainstorm
1's brief didn't anticipate, the correct response is the same one this review itself followed: stop,
record the ambiguity precisely, and do not guess.

---

## 16. Recommended next task

**Not implementation.** The exact next task is **Brainstorm 1 (User + ChatGPT)** for MVP-1.4
Catalogue, using this document as the starting input — specifically resolving §5.3.1 (product/SKU
field list, highest priority alongside §5.3.2), §5.3.2 (Stock Item relationship), §5.3.3
(branch/draft/review/publish state machine), §5.3.4 (Excel contract), §5.3.5 (asset storage
mechanism), §5.3.6 (visitor-facing timing), §5.3.7 (Desktop surface), §5.3.8 (sharing mechanism),
and §5.3.9 (company isolation, likely the fastest to confirm) — after which the output should be
recorded as new Product Decision Log entries (mirroring PDL-019's own pattern) and a revised/
confirmed version of this document, at which point a fresh Claude session can be issued an
MVP-1.4-A implementation prompt directly.

Separately, and not blocking Catalogue Brainstorm 1: TD-035 (Connector `PARENT`/group extraction,
`docs/technical-debt/registry.md`) needs its own explicit Product Owner/ChatGPT decision — re-enable
the field now that TD-001's sanitizer neutralizes the original risk, or keep the defense-in-depth
exclusion and formally accept the Party/Catalogue-grouping limitation. Recommended to resolve before
Prospect→Ledger linking (`docs/planning/BUDCOM-NOT-NOW.md`) is ever scheduled, since that item's own
Debtor/Creditor classification depends on it.

---

## 17. Source documents this review reconciled

`docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`, `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md`,
`docs/design/BUDCOM-SCREEN-INVENTORY.md`, `docs/planning/BUDCOM-NOT-NOW.md`,
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` (PDL-001 through PDL-019 read in full),
`docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md` (direct structural precedent for
this document, and the source of §2.1's dependency confirmation), `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`,
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` (through Phase 37/38), `docs/technical-debt/registry.md`
(TD-035, newly added the same session), `docs/ROADMAP.md`, a full repository source search
(Android/Desktop/Connector for catalogue/SKU/branch-draft-publish terminology and for the existing
`StockItemEntity`/`BusinessProfileEntity` foundations), and direct inspection of
`AppDatabase.kt`/`Routes.kt`/`DatabaseModule.kt` current state.
