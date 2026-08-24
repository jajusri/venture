# BUDCOM MVP-1.4 — Catalogue — Architecture

**Status: Implementation IN PROGRESS (2026-08-24, Phase 56) — see
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §47 for the full implementation record.** Brainstorm 1 is
complete (`docs/architecture/BUDCOM-MVP-1_4-CATALOGUE-BRAINSTORM-OUTCOME.md`), PDL-020 locked the
original nine open product decisions (2026-08-19), and a formal decision-lock pass (2026-08-24)
additionally locked the override-resolution order, the Excel field-name reservation rule, the
Prospect→Ledger FUTURE classification, sharing granularity, and the timestamp-integrity
requirement. This document was the **implementation-ready architecture and execution plan**
produced from that final locked scope; a separate, explicit go-ahead (received 2026-08-24) then
authorized implementation. **Implemented and AUTOMATED-VALIDATED this same session**: data
foundation (§6, migration 10→11), Tally identity/reconciliation (§14), override engine (§8, minus
the Stock-group level — see TD-043), lifecycle (§7), pricing governance infra (§12, pending a Tally
rate field — see the Development Ledger entry), asset store (§10), sharing (§11, plain-text instead
of PDF — disclosed simplification), Excel contract/validation (§9, minus a chosen file-format
library), and essential UI (§17, minus a branch selector and category-picker). **Also
PHYSICALLY VALIDATED this same session** (Development Ledger §47.I): the Catalogue Room migration
(10/10 instrumented tests on a real device) and a full hands-on Draft→Publish→Archive→Restore→
Share walkthrough against a real paired company — which found and fixed two real defects live
(TD-045, TD-046) that no unit test had caught. **Not yet done**: Milestone 0's live Tally-request
validation specifically (TD-043, gated safely disabled — distinct from the app-level physical
validation already done), a real Owner/staff identity to enforce §18's Owner-only rule against
(TD-044, currently a hardcoded placeholder). See the Development Ledger entry for the complete,
itemized account of what was and was not done, and why.

This revision supersedes this document's own pre-brainstorm content (§§5, 12 of the prior revision,
preserved in git history) — those sections posed the nine open questions PDL-020 then resolved and
are no longer current; everything below reflects the FINAL locked scope, not the open-questions
framing.

---

## 1. Executive summary

MVP-1.4 Catalogue's ownership shape was locked before any brainstorm (Master Plan §2/§10, UI Design
Decisions §7): one business identity, one catalogue, one asset library, multiple permission-controlled
views; Connect and Vartalap consume this data, they do not own it. The detailed product/data model
was locked by PDL-020 (2026-08-19) and the Brainstorm Outcome document (2026-08-23), then a
decision-lock pass (2026-08-24) closed the remaining ambiguities. **Zero Catalogue code exists
anywhere in this repository** (re-confirmed this session — see §2); this document turns the locked
product scope into an implementation-ready architecture: data model, identity/provenance rules,
lifecycle state machine, branch/override architecture, Excel contract, asset model, sharing model,
price governance, company isolation model, security review, migration analysis, test strategy, and a
dependency-ordered implementation sequence.

**Absorption verdict (§4): nothing substantive was absorbed from former 1.4a/1.4b/1.4c.** Every
capability in the final locked "IN MVP-1.4" scope was already part of the original Brainstorm
Outcome's core sections (2–9) or PDL-020 — none of it originated in the 1.4a/b/c buckets, which
remain genuinely future (tiered/quantity pricing, customer-facing Estimation/budget tooling,
Purchase-Order sharing, buyer accounts, external connection/approval workflows, version
history/rollback, analytics, multi-language). See §4 for the full reasoning.

**Two load-bearing findings from this session's repository research, both new information not
previously recorded anywhere:**

1. **"Branch" does not exist as a domain concept anywhere in this codebase.** Business Profile is a
   strict single row per `companyId` (`business_profile` table, PK = `companyId` alone); no
   branch/location field exists in Business Profile, Company, or anywhere else. The locked
   override-resolution order (Item → Branch → Stock-group → Catalogue-wide) requires Catalogue to
   introduce Branch as an **entirely new foundational entity**, not extend an existing one. See §8.
2. **The Stock-group level of the locked override chain has a live data-availability gap.**
   `StockItemEntity.parentGroup` (Tally's Stock Group) is real end-to-end (Connector → Android) but
   is very likely always NULL in production today, because the Connector's stock-item TDL Fetch list
   never requests the `PARENT` tag — the identical bug class as TD-035 (Ledgers, fixed 2026-08-19)
   and TD-042 (Ledger Alias, fixed 2026-08-23), just never extended to Stock Items, and this
   document's own prior revision (§13) explicitly named this as a required-separate-safety-review
   item, not a Catalogue-work side effect. Stock-group-level overrides cannot function correctly on
   real data until this is fixed and live-validated — this is a named prerequisite in the
   implementation sequence (§21, Milestone 0), not something to silently work around. See §16.

---

## 2. Repository inventory (re-confirmed this session)

**Still true, re-verified directly rather than trusted from the prior revision:** zero
`catalogue`/`Catalogue` implementation anywhere in Android, Desktop, or Connector source. No
`catalogue_*` Room entity, no Catalogue route on the Connector, no Catalogue screen/ViewModel.

**Foundations confirmed present and reusable (all verified directly, not assumed):**

- **Company-scoped business identity** — `business_profile` table (`companyId TEXT NOT NULL PRIMARY
  KEY`, `BusinessProfileEntities.kt:13-34`), single row per company, no provenance tracking, **no
  branch field**.
- **Logo/asset storage pattern** — `BusinessProfileLogoStore` interface +
  `AndroidBusinessProfileLogoStore` impl (`feature/businessprofile/storage/`): app-private
  `filesDir/business_profile_logos/<sanitized-companyId>.<ext>`, 5 MB cap, MIME allowlist (jpg/png/
  webp), path-containment check on read. **Structurally single-image-per-company** (keyed by
  `companyId` alone, one file) — not multi-image-per-entity capable as-is. This is the *pattern* to
  extend (per PDL-020 §5.3.5, which explicitly does not mandate literal table/store reuse), not a
  store Catalogue can bolt onto unmodified.
- **Stock Item local-first foundation** — `StockItemEntity`/`StockItemDao`/`StockItemRepositoryImpl`
  (`feature/masterdata/stockitem/`), already synced Room-first via the same adaptive sync engine
  Ledgers use. Fields: `companyId, id, name, alias, parentGroup, category, baseUnit, partNumber,
  hsnCode, gstRate, status, closingBalance, dataQuality, syncedAt`. Composite PK
  `(companyId, id)`, indexed `(companyId)` and `(companyId, name)` — identical isolation shape to
  `LedgerEntity`.
- **Connector-side Stock Item identity** — `resolveStockItemStableId()`
  (`src/extraction/core/stock-item-identity.ts:10-20`): GUID → AlterID → name-slug (one more fallback
  tier than Ledger's GUID → name-slug). Fetch list (`master-data-templates.ts:99-112`): `GUID,
  ALTERID, NAME, ALIAS, PARTNUMBER, HSNCODE, OPENINGBALANCE, OPENINGRATE`. **`mapStockItem()`
  (`entity-mappers.ts:294-320`) parses `PARENT`, `CATEGORY`, `BASEUNITS`, `CLOSINGBALANCE`,
  `GSTAPPLICABLE`, `ISINACTIVE` off the XML node — none of these six tags are in the Fetch list.**
  Confirmed the same missing-Fetch-field bug shape as TD-035/TD-042, not yet fixed for Stock Items.
- **No persisted Stock Group entity.** `NormalizedStockGroup`/`TallyMasterDataCollections.StockGroups`
  exist at the extraction layer only; `GET /companies/:companyId/stock-groups` is a **live,
  non-cached, non-company-scoped-storage** pass-through read (`master-data.service.ts:203-207`) —
  there is no `stock_groups` table, no repository, no stable local identity beyond
  `slugify(name)` per request. If Catalogue's Stock-group override level needs a stable local Stock
  Group ID to key overrides on, that persistence layer does not exist yet.
- **No generic source-link abstraction.** `PartySourceLinkEntity`/`PartySourceLinkDao` are hardcoded
  to Party (`party_source_links` table, `partyId` column, Party-specific queries) — there is no shared
  base entity/DAO. A `ProductSourceLink` for Catalogue↔StockItem would need the identical *shape*
  (`companyId, sourceType, externalEntityId, internalId, identitySource, lastConfirmedAt`) but fully
  duplicated code, not an extension.
- **Company isolation is convention, not structural.** Every DAO/repository/route across Party,
  Ledger, StockItem, and Business Profile scopes via composite primary key + explicit `companyId`
  parameter on every method — proven, consistent, but not enforced by any wrapper/decorator/
  interceptor. A caller that forgot to filter by `companyId` would not be structurally prevented at
  the DAO layer. See §13.

---

## 3. Final locked scope matrix

### IN MVP-1.4 (locked)

| Capability | Locked by |
|---|---|
| Catalogue lifecycle: Draft → Review → Publish → Archive | Brainstorm Outcome §4 |
| Branch-aware Catalogue foundation (new — see §1, §8) | User instruction (this session) + override-resolution lock |
| Item → Branch → Stock-group → Catalogue-wide override resolution | Decision-lock pass (2026-08-24), **LOCKED** |
| Tally Stock Item identity/reconciliation (reuse existing pipeline) | PDL-020 §2 |
| Catalogue enrichment: images/assets, descriptions/specifications | Brainstorm Outcome §4 |
| Customer-facing category (owner-assigned, independent of Tally) | Brainstorm Outcome §4 |
| Price governance (Open/"Contact for price", Auto/Manual sync, override levels) | Brainstorm Outcome §4 |
| Excel import/export foundation, reserved native/future-native field names, safe validation/import behavior | Brainstorm Outcome §6, decision-lock pass **LOCKED** |
| Category-level sharing, full-catalogue sharing, WhatsApp-oriented sharing foundation | Brainstorm Outcome §5, PDL-020 §8, decision-lock pass **LOCKED** |
| Basic review/approval foundation required for the lifecycle (internal Draft→Review→Publish gate; Owner-only Publish) | Brainstorm Outcome §4 |
| Company isolation (strict, no UI-only inference) | PDL-020 §9 |
| Clean publication boundary (published *state* exists architecturally even though the visitor-facing surface is deferred) | PDL-020 §6 |
| Field ownership/provenance (Tally-authoritative vs. Catalogue-authoritative) | PDL-020 §2, this document §6 |
| Non-destructive Tally refresh semantics (sync must not silently erase Catalogue enrichment) | PDL-020 §2, this document §6 |

### FUTURE / NOT IN MVP-1.4 (explicitly preserved, not reopened)

| Capability | Why it stays future |
|---|---|
| Prospect → Debtor Ledger, Prospect → Creditor Ledger, reuse of existing synced Ledger data | Decision-lock pass (2026-08-24), **LOCKED as FUTURE/unscheduled** — unrelated domain (Connect/Party), not reopened here |
| Marketplace / social-network behavior | Permanent exclusion, Master Plan §3, Brainstorm Outcome §9 |
| Generic CRM expansion | Permanent exclusion |
| Advanced catalogue analytics (owner-facing usage/analytics) | Former 1.4a/1.4b scope — genuinely new domain (reporting/analytics), not foundational to the lifecycle |
| Advanced staff-management capabilities beyond Owner-only Publish | Not required for the locked Draft→Review→Publish→Archive lifecycle |
| Advanced/granular notifications beyond the five locked types (Brainstorm Outcome §7) | Already scoped and locked at the level Brainstorm Outcome §7 defines; nothing further absorbed |
| Full version-history/rollback for Published items | Brainstorm Outcome §4 explicitly: "No image version history in MVP-1.4"; §9 defers version history to 1.4b — not required by the locked lifecycle (Archive is the only "old state" mechanism — see §7) |
| Purchase-Order sharing, buyer accounts, connection/approval workflow for **private** catalogues | Former 1.4b — external buyer-facing workflow, categorically different from the internal Draft→Review→Publish approval gate that IS in scope |
| Tiered/quantity pricing, customer-facing Estimation/budget tooling | Former 1.4a — new pricing-engine domain, explicitly excluded by Brainstorm Outcome §4 ("No pricing engine") |
| Multi-language (UI and/or content) | Former 1.4c — genuinely new domain (i18n infrastructure) |
| Item-level sharing | Decision-lock pass (2026-08-24), explicitly **NOT** in the locked sharing scope unless separately authorized during implementation planning |
| Broader Order Capture / Vartalap integration | Brainstorm Outcome §9, unrelated domain |
| Visitor-facing "Resources" view (customer browsing surface) | PDL-020 §6 — owner-side foundation ships first; the surface itself is a separate, later decision |
| Manual-item ↔ Tally-item matching/merging | Brainstorm Outcome §9, unscheduled |

---

## 4. What was absorbed from former 1.4a/1.4b/1.4c, and why

**Nothing substantive.** Cross-checking the final "IN MVP-1.4" list (§3) against the three deferred
buckets:

- **1.4a** (customer selection, budget totals, Estimation output, tiered/quantity pricing,
  owner-facing analytics) — none of this appears in the locked IN-scope list. Price governance IN
  MVP-1.4 is explicitly the pre-existing Open/"Contact for price" + Auto/Manual sync model
  (Brainstorm Outcome §4), not a pricing engine.
- **1.4b** (Estimation-as-PO, buyer accounts, private-catalogue connection/approval, version
  history/rollback) — none of this is in scope. The one item that could be mistaken for overlap is
  "basic review/approval foundation required for the lifecycle" — this is the **internal** staff→
  owner Draft→Review→Publish gate already locked in the original Brainstorm Outcome §4 ("staff
  prepares Drafts; only Owner-level access can Publish"), categorically different from 1.4b's
  **external** buyer-to-business connection/approval workflow, which remains future.
- **1.4c** (multi-language) — not in scope.

The only genuinely new element in the final locked scope is **branch-awareness** (§1, §8) — but this
was never part of 1.4a/1.4b/1.4c; it originates directly from the already-locked override-resolution
order (Item → Branch → Stock-group → Catalogue-wide), which has been part of the Brainstorm Outcome
since before the decision-lock pass. It is foundational architecture required to make an already-
locked decision function, not an absorbed future-phase feature.

---

## 5. Protecting existing architecture — identity boundaries

Two parallel, structurally similar, but **non-convergent** identity chains exist and must stay
separate:

```
Tally Stock Item  →  Catalogue Product        (NEW, this milestone)
Tally Ledger      →  Party / Customer / Supplier / Connect   (EXISTING, untouched)
```

**Catalogue MUST NOT, and this architecture does not require it to:**

- Create a competing Ledger/Party identity system. A Catalogue Product's optional customer/party
  reference (e.g., "who created this draft," if ever needed) resolves through the **existing**
  `Party`/`PartySourceLink` foundation — Catalogue never stores a parallel customer identity, phone
  number, or contact record. If a future need arises, it is a foreign-key-style reference to an
  existing `Party.partyId`, never a duplicate `CatalogueCustomer` table.
- Duplicate customer identity, replace existing Ledger contact-detail population, or interfere with
  phone/email/address enrichment. Catalogue's own bulk-contact-details-style seeding (if it ever
  needs enrichment reconciliation) is entirely new code operating on entirely new
  `catalogue_products`/`catalogue_asset` tables — it shares *patterns* (fill-if-empty/confirm/
  conflict) with `PartyRepositoryImpl.applyAliasPhoneSeeding`/`applyLedgerContactDetailsBulk`, never
  the same tables, DAOs, or provenance rows.
- Weaken company isolation, alter Tally write behavior, bypass sanitization/trust boundaries, or
  interfere with the Scheduler. Catalogue's own sync/reconciliation is a **new, additive** extraction
  operation (or reuses the existing Stock Item extraction — see §14), never a modification to
  `LedgerSyncServiceImpl`, `StockItemSyncServiceImpl`, `AdaptiveSchedulerService`, or any existing
  route.

**Where Catalogue needs a Stock Item reference**, it uses the exact same pattern
`PartySourceLink` already proves (§2's "no generic abstraction" finding means this is a **new,
parallel** table, not a shared one): a `ProductSourceLink` keyed
`(companyId, sourceType='tally_stock_item', externalStockItemId)` → `productId`, deterministic and
rename-stable via the same `resolveStockItemStableId()` id Android already receives on
`StockItem.id`. No new Tally-side identity mechanism is invented (§14).

---

## 6. Data model and field ownership / provenance

### Field-level ownership table

| Field | Owner | Reconciliation rule |
|---|---|---|
| Stock Item identity (`stockItemId`, GUID/AlterID/name-slug) | **Tally-authoritative** | Read-only mirror of the existing `StockItem.id`; Catalogue never mutates it |
| Item name (as synced from Tally) | **Tally-authoritative** (display default) | Catalogue product's own `displayName` may diverge — see below |
| Unit, HSN, GST rate | **Tally-authoritative** | Mirrored from `StockItem.baseUnit`/`hsnCode`/`gstRate`; Catalogue never edits — Brainstorm Outcome §4 explicitly makes HSN/GST visible-or-hidden **display** toggles, not editable Catalogue fields |
| Stock Group / Category (Tally hierarchy) | **Tally-authoritative** | Mirrored from `StockItem.parentGroup`/`category`; used only for the Stock-group override level (§8) — **not** the customer-facing category (next row) |
| Catalogue description / specifications | **Catalogue-authoritative** | Never sourced from Tally (Tally has no such field); free text at launch |
| Images / assets | **Catalogue-authoritative** | Never sourced from Tally |
| Customer-facing category | **Catalogue-authoritative**, explicitly independent of Tally | Brainstorm Outcome §4: "Category (owner-reassignable, independent of Tally)" — a distinct field from the Tally Stock Group used in override resolution |
| Price / price-display mode | **Catalogue-authoritative**, sync mode selectable | Auto mode pulls from `StockItem.closingBalance`-adjacent rate fields per the sync-mode rule (§12); Manual mode is entirely Catalogue-owned pending owner approval |
| Branch-specific overrides | **Catalogue-authoritative** | New concept entirely (§8) |
| Publication state (Draft/Review/Publish/Archive) | **Catalogue-authoritative** | Never derived from Tally |

### Reconciliation semantics (the three guaranteed properties)

1. **Tally sync MUST NOT silently destroy Catalogue-owned enrichment.** Mirrors the exact fix this
   session's own connector work made to `SqliteLedgerRepository.upsertMany` (COALESCE fix,
   `sqlite-ledger-repository.ts`): any bulk Stock Item re-sync that touches a linked Catalogue
   Product must write Tally-authoritative fields only (name/unit/HSN/GST/parentGroup) and leave
   Catalogue-authoritative columns (`description`, `specifications`, image references, customer-
   facing category, price) completely untouched — either via a narrow partial-update query (same
   shape as `updateContactDetailsMany`) or by architecturally storing Tally-mirrored fields and
   Catalogue-owned fields in **separate tables** joined by `productId`, so a Stock Item re-sync
   physically cannot reach the Catalogue-owned columns at all. The latter is the stronger guarantee
   and the recommended design (§14).
2. **Tally rename MUST NOT create a duplicate Catalogue product.** Guaranteed by keying the
   `ProductSourceLink` on the same stable `resolveStockItemStableId()` id Stock Item sync already
   uses (GUID-first) — a rename changes `StockItem.name`, never `StockItem.id`, so the link survives
   unchanged, exactly like `PartySourceLink` already survives Ledger renames today.
3. **Tally disappearance MUST NOT automatically delete the Catalogue product.** A Stock Item that
   stops appearing in a sync (soft-deleted/`isDeleted=true` per the existing Stock Item convention)
   flips the linked Catalogue Product into a **"source unavailable"** flag — visible to the owner,
   blocking new Publish actions on that product, but never an automatic Archive/delete. The owner
   explicitly decides (Archive, or re-link to a different Stock Item, or leave Draft) — mirroring
   PDL-020 §2's own explicit question ("What happens to a published product if its source Stock Item
   is later renamed, deactivated, or deleted in Tally?") resolved here as: never automatic, always
   owner-visible and owner-decided.

---

## 7. Lifecycle state machine

```
Draft → Review → Publish → Archive
```

- **Who may transition:** Draft creation — any authorized staff/owner (mirrors Brainstorm Outcome
  §4's "Team businesses: staff prepares Drafts"). Draft → Review — automatic on staff submission, or
  skipped entirely for solo businesses ("Review effectively skipped (owner does everything)," already
  locked). Review → Publish — **Owner-level access only**, no separate Reviewer role (already
  locked). Publish → Archive — **owner-initiated only**, never automatic (already locked, Brainstorm
  Outcome §4).
- **What's editable per state:** Draft — everything. Review — everything (Review is a gate, not a
  lock; the brainstorm doc does not lock Review-state field restrictions, so this document proposes
  the simplest consistent rule: Review-state editing is unrestricted, identical to Draft, since no
  stricter rule was ever specified — **flagged as a proposed default, not a locked decision**, see
  §20). Published — editing an already-Published product creates a **new pending Draft copy**, never
  an in-place mutation of the live Published record (this is the direct consequence of "editing a
  published product never auto-publishes the edit," PDL-020 §3). Archive — read-only.
- **What customers see:** only the currently-Published snapshot (or nothing, if Private — Brainstorm
  Outcome §5). A product's Draft/Review state and edit history are never customer-visible.
- **New Draft over an already-Published product:** creates a parallel pending-edit record; the
  live Published version remains visibly unchanged and fully served until the new Draft completes
  Review → Publish, at which point it **replaces** the customer-visible snapshot atomically (no
  partial-update window).
- **Does Publish create an immutable customer-visible snapshot?** Yes, functionally — not via a
  full version-history table (explicitly out of scope, §3), but via a single "current published
  state" record per product that is atomically replaced on each Publish, never partially mutated.
  This gives Publish-time atomicity without building version history.
- **How updates become visible:** immediately on successful Publish (no propagation delay beyond
  normal Room/UI refresh) — consistent with this codebase's local-first, no-queue publication model.
- **What happens when publication fails:** the product remains in Review state, unchanged and
  un-published; no partial customer-visible state is ever created. Mirrors the existing codebase's
  "fail honestly, never partially" convention (e.g., `LedgerRepositoryImpl`'s "No offline data
  available" guard rather than a silent empty success).
- **Item removed from a Draft (e.g., its Stock Item is deleted mid-edit):** the Draft is not silently
  auto-completed or auto-discarded — it surfaces the same "source unavailable" flag as §6's
  Tally-disappearance rule, and Review/Publish is blocked until the owner resolves it (re-link,
  discard, or proceed as a Catalogue-only product if that's ever separately authorized — not decided
  here).
- **Archive vs. deletion:** Archive is a **soft, owner-reversible state** — the product and all its
  Catalogue-owned enrichment (description, images, price history-of-one) are retained, simply removed
  from all customer-facing surfaces and from the default owner-facing product list (filterable back
  in). True deletion of a Catalogue product record is explicitly **not built in MVP-1.4** (mirrors
  the identical, already-locked precedent for shared-catalogue product records generally, Brainstorm
  Outcome §9 "True deletion... not built; hide/soft-delete only").

---

## 8. Branch and override architecture

**Branch does not exist anywhere in this codebase today (§1, §2) — this is new, foundational
architecture, explicitly authorized by the locked override-resolution order and the user's own
"branch-aware Catalogue foundation" scope item.**

### Branch identity (proposed, new)

A minimal `Branch` entity: `companyId, branchId, name, isActive, createdAt, updatedAt` — company-
scoped (composite PK `(companyId, branchId)`, matching every other entity's isolation pattern, §13).
No address/contact/staff-roster fields are added here — those would be genuinely new Business-Profile-
adjacent scope not requested by this task; Branch here is deliberately minimal, existing only to
support Catalogue's override-resolution and branch-assignable Publish rights (Brainstorm Outcome §4
"Publish rights are branch-assignable").

**Open placement question (flagged, not decided — §20):** should `Branch` live conceptually inside
the Business Profile module (since it's a business-identity concept, not Catalogue-specific) or
inside a new shared/company module Catalogue introduces? This document recommends Business-
Profile-adjacent ownership (a business's branches are a property of the business, reusable by any
future feature), implemented as a new table alongside (not modifying) `business_profile`, but this
is an architecture placement choice, not a product decision, and either placement satisfies the
locked scope.

### Catalogue ownership and branch availability

- **One shared catalogue per company** (already locked, Brainstorm Outcome §4: "One shared catalogue
  across branches — not separate catalogues per branch"). Branch is a **scoping/override dimension**
  on top of one catalogue, never a separate catalogue instance.
- A Branch can Add/Edit/Hide items within the shared catalogue (already locked); true deletion of the
  shared product record stays deferred regardless of branch.

### Override resolution — LOCKED

```
ITEM → BRANCH → STOCK-GROUP → CATALOGUE-WIDE
```

**Representation:** each override-capable attribute (price-sync mode is the concrete example already
named in the Brainstorm Outcome; the same table shape generalizes to any future override-capable
field) is stored as a set of override rows, each scoped to exactly one of the four levels:
`(companyId, productId?, branchId?, stockGroupId?, attributeName, value)` — at most one of
`productId`/`branchId`/`stockGroupId` is non-null per row (the catalogue-wide level has none set).
**Resolution algorithm:** for a given product+branch context, look up an ITEM-level row first; if
none, BRANCH-level; if none, STOCK-GROUP-level (keyed by the product's linked Stock Item's
`parentGroup`); if none, fall through to the single CATALOGUE-WIDE default. This is a strict,
deterministic precedence chain with no merging — the first match wins, exactly as locked.

**Conflicts:** by construction, at most one row can match at each level (enforced by the composite
key), so there is no multi-row conflict to resolve *within* a level — only the precedence *across*
levels, which is fixed and total (never ambiguous).

**Company scoping:** every override row carries `companyId` as the first key component,
non-negotiable — matches every other entity in this codebase (§13).

**Named dependency (§1, §16):** the STOCK-GROUP level of this chain resolves through
`StockItem.parentGroup`, which is very likely NULL in production today (same missing-Fetch-field gap
as TD-035/042, never fixed for Stock Items). This must be fixed and live-validated (its own Tally-TDL
safety review, per this project's standing rule) **before** Stock-group overrides can be relied on
with real data — sequenced as Milestone 0 in §21, deliberately **not** performed as part of this
planning task (this document does not modify Connector code).

---

## 9. Excel contract

**Locked constraints carried in:** Excel is a canonical **interchange** contract, never the
operational database (PDL-020 §4); native/future-native field names are **reserved** and a custom
column cannot reuse one, with a rename prompt on conflict (Brainstorm Outcome §6, **LOCKED**); stable
identifiers prevent duplicate products on round-trip (PDL-020 §4).

### Column contract

| Category | Columns | Notes |
|---|---|---|
| **Required** | Product Name, Unit | Minimal viable row — mirrors the "quick-start default path" UX principle (Brainstorm Outcome §8) |
| **Native, Tally-sourced (reserved names)** | SKU/Item Code, Stock Item Reference, HSN, GST Rate, Unit, Stock Group/Category (Tally) | Read-only on import if a Stock Item link is resolved — see validation below |
| **Native, Catalogue-owned (reserved names)** | Description, Specifications, Customer-facing Category, Price, Price-Display Mode, Publication State (export-only, not import-writable — see below) | Editable via import |
| **Reserved future-native names** | A documented, versioned reserved-word list (e.g. anything matching the native list above, plus a small explicit allowlist of near-term-likely additions) shipped with the import validator, not hardcoded per-release — this is what makes the schema non-brittle (§9's own "do not create a brittle schema" requirement) |
| **Custom columns** | Owner-defined, any name not on the reserved list | Round-tripped opaquely; BUDCOM never interprets their content |

**Extensibility discipline (avoiding a brittle schema):** the reserved-name list is enforced by
membership check against a maintained set, not a fixed column count/order — adding a future native
field later means adding one entry to that set, never a schema/file-format migration. This directly
satisfies "do not create a brittle schema that will require migration when future Catalogue
capabilities arrive."

### Validation, duplicates, malformed rows

- **Stable-identifier matching:** a row matching an existing Product's stable identifier (Stock Item
  reference, or a Catalogue-issued Product ID once assigned) updates that product; a row with no
  match and a valid Product Name creates a new Draft. This is what prevents duplicate-on-round-trip
  (PDL-020 §4).
- **Duplicate rows within one file** (same identifier twice): last-row-wins within the file, flagged
  in the import preview (not silently resolved) so the owner sees it before committing.
- **Malformed rows** (missing required column, unparseable price, reserved-name collision on a
  custom column): the whole file is not rejected — each malformed row is excluded from the Draft
  creation and listed individually in the import preview with its specific reason, mirroring this
  codebase's own "fail honestly, per-item, never a silent partial success" convention already proven
  in Ledger/StockItem sync reporting (`itemsSkipped`/`itemsFailed` counters).
- **Import preview:** mandatory step before commit — shows create/update/skip counts and every
  flagged row's reason, never a silent bulk-apply. This satisfies "safe validation/import behaviour"
  (locked scope item).
- **Conflict resolution on re-import:** already locked (Brainstorm Outcome §6) — Owner action always
  wins over Staff action regardless of timing; between two Owner actions, most-recent-timestamp wins
  (see §15 for what "timestamp" means here); an older-file re-import triggers the already-specified
  warning dialog.
- **Draft creation:** a successful import always lands as Draft state (never auto-Published),
  consistent with the general lifecycle rule (§7).
- **Export:** round-trips every native + custom column currently on each product, including current
  Publication State as a read-only informational column (not writable — Publish/Archive transitions
  happen only through the lifecycle UI, never via Excel re-import, to keep the state-machine's
  authorization rules — Owner-only Publish — intact).

---

## 10. Asset / image model

**Reuses the existing asset-storage abstraction's *pattern*, not its literal single-image-per-company
store (§2) — PDL-020 §5.3.5 explicitly permits a distinct-but-consistent store.**

- **Image identity:** `(companyId, productId, assetId)` — `assetId` a generated UUID, never derived
  from the original filename (avoids collisions/traversal by construction, matching
  `BusinessProfileLogoStore`'s own discipline).
- **Filename convention:** stored as `<companyId>/<productId>/<assetId>.<ext>`, mirroring
  `BusinessProfileLogoStore`'s `<sanitized-companyId>.<ext>` shape one level deeper for
  multi-image/multi-product support.
- **Upload/import:** app-private storage only (never a public/shared directory), same MIME allowlist
  (jpg/png/webp) and streaming size cap discipline as the existing store; auto-compression on upload
  (already locked, Brainstorm Outcome §4).
- **Duplicate images:** no content-hash dedup in MVP-1.4 (not requested, would be scope creep) — each
  upload gets its own `assetId` even if byte-identical to an existing one.
- **Missing images:** a product with zero images is valid and publishable (already locked: "Not
  required to publish").
- **Replacement:** uploading a new primary image does not overwrite the old `assetId` in place —
  it's a new asset row with the old one's `isPrimary` flag cleared, then the old file is deleted only
  after the new one is confirmed written (mirrors safe-replacement discipline, avoids a window where
  neither file is valid).
- **Deletion:** soft-remove the asset row first (image disappears from the product immediately), then
  physically delete the file — same two-phase discipline, avoids a dangling DB reference to a
  deleted file.
- **Branch-specific images:** not part of the locked scope (images are product-level, not mentioned
  as branch-overridable anywhere in the Brainstorm Outcome) — not built.
- **Publication behavior:** images referenced by the current Published snapshot are never deleted
  while that snapshot is live, even if a new Draft replaces them — avoids a broken image on an
  already-shared link (see §11).
- **Offline/cache behavior:** images sync/display through the same local-first Room+file pattern
  already proven — no new caching mechanism.
- **Company isolation:** the `companyId` segment in both the identity tuple and the file path is
  non-negotiable, matching every other entity (§13).

**No second asset-storage system is invented** — this is a second *instance* of the same proven
pattern (allowlist, size cap, path-containment, deterministic ownership, safe two-phase
replace/delete), not a new mechanism.

---

## 11. Sharing model

**LOCKED:** category-level sharing, full-catalogue sharing, WhatsApp-oriented sharing foundation
(decision-lock pass, 2026-08-24). **NOT locked / explicitly excluded:** item-level sharing, unless
separately authorized during implementation planning.

- **Mechanism:** reuses the existing, proven Android `Intent.ACTION_SEND` / WhatsApp sharing pattern
  already used for Ledger statements and Voucher PDFs (PDL-020 §8) — no new QR/deep-link/web-hosted
  mechanism.
- **What the recipient receives:** a controlled, generated representation (PDF or equivalent,
  matching the existing Ledger/Voucher share-file pattern) of the **currently Published** scope
  (category or full catalogue) — never a link into internal Room structures or filesystem paths
  (already locked).
- **Publication state's effect on sharing:** only Published products appear in a generated share;
  Draft/Review-state products are invisible to any share output, structurally (the share generator
  reads from the same "current published snapshot" record §7 defines, never from Draft-state data).
- **What happens when the catalogue changes after a link/file was already shared:** because sharing
  generates a **snapshot file at share time** (mirroring the existing ephemeral share-cache pattern —
  `PartyXmlExportCacheFileSystem`/`InvoiceShareFileSystem`, age-bounded, not company-scoped, generated
  fresh per share action), a previously-shared file does **not** update — the recipient holds a
  point-in-time snapshot, exactly like an already-sent Ledger statement PDF today. This is a direct,
  low-risk consequence of reusing the existing sharing mechanism rather than a live link — no new
  "expiring share link" or "live-updating share" mechanism is introduced, since none exists anywhere
  in this codebase today and building one would be new scope beyond what's locked.
- **Private catalogues:** already locked (Brainstorm Outcome §5) — Private means simply not shareable/
  viewable, no partial/approval access; the richer account-based approval flow is explicitly 1.4b,
  future.

This does not expand into marketplace/social behavior — there is no public link, no unauthenticated
web endpoint, no visitor account system in this model; it is exactly the existing "generate a file,
hand it to the OS share sheet" pattern, applied to a new content type.

---

## 12. Price governance

Converting the already-locked decisions (Brainstorm Outcome §4) into implementation-ready rules —
**no new pricing model introduced:**

- **Source of price:** either Tally (`StockItem`'s rate-adjacent fields, read at sync time) or
  Catalogue-entered, selected by sync mode below. No third source.
- **Sync modes, selectable at Catalogue-wide / Stock-group / Item levels (the same locked override
  chain, §8):**
  - **Auto:** price mirrors the linked Stock Item's Tally rate on every sync — Tally-authoritative
    for that product/scope while Auto is selected.
  - **Manual:** price is Catalogue-owned; a Tally-side rate change surfaces as a **pending
    price-change** shown to the owner on next login (already locked) — never silently applied.
    Approval is a simple owner accept/dismiss action, not a new workflow.
- **Branch override:** price-sync-mode selection itself is one of the override-resolution chain's
  attributes (§8) — a branch can run Auto while Catalogue-wide default is Manual, or vice versa,
  resolved by the same ITEM→BRANCH→STOCK-GROUP→CATALOGUE-WIDE precedence.
- **Conflict behavior:** none possible by construction — the override chain resolves to exactly one
  active mode per product+branch context (§8); "conflict" only ever means a pending-price-change
  notification under Manual mode, which is a queue, not a conflict.
- **Display behavior:** **Open (visible)** or **"Contact for price"** only — no pricing engine, no
  tiers, no discounts, no quantity breaks (already locked; tiered/quantity pricing is explicitly
  1.4a, future).
- **Rounding/precision:** inherits the same amount/currency representation already used for Ledger/
  StockItem balances (`MoneyAmount`-style: amount as string, explicit currency code) — no new numeric
  type introduced.
- **Customer visibility:** only the resolved, currently-Published price (or "Contact for price") is
  ever customer-visible — Draft/Review-state price edits are never exposed, matching the general
  lifecycle rule (§7).

---

## 13. Company isolation model

Treated as a hard invariant from the first schema design, following the proven pattern this codebase
already uses everywhere (§2's audit): **composite primary key with `companyId` first + `companyId`-
prefixed indices + `companyId` as an explicit, mandatory parameter on every DAO/repository/route
method** — reused unchanged, not reinvented.

Every new Catalogue entity carries `companyId` as the first key component:

| Entity | Key |
|---|---|
| `catalogue_product` | `(companyId, productId)` |
| `product_source_link` | `(companyId, sourceType, externalStockItemId)` |
| `catalogue_asset` | `(companyId, productId, assetId)` |
| `branch` | `(companyId, branchId)` |
| override row | `(companyId, [productId\|branchId\|stockGroupId], attributeName)` |
| draft/review/publish state | folded into `catalogue_product`'s own row (no separate table needed — see §7) |
| share/export record (if persisted at all — likely ephemeral only, §11) | `(companyId, ...)` if ever persisted |

**Audited against TD-036/TD-037** (both fixed 2026-08-22, confirmed via direct registry inspection):

- **TD-036's lesson (per-company in-memory state):** the Connector's `LedgerSyncServiceImpl`/
  `StockItemSyncServiceImpl` were process-wide singletons holding un-scoped progress/active-run state
  until fixed with `Map<companyId, T>`. **If Catalogue ever needs its own in-memory sync/reconciliation
  state on the Connector side** (only relevant if a future milestone adds a Connector-side Catalogue
  sync step — not required by this document's design, since Catalogue Product data is
  Android/Room-local, not Connector-persisted), it must follow this same per-company-Map pattern from
  day one, or — the cleaner alternative this session's research also surfaced — the
  `AdaptiveSchedulerService` pattern of holding **no** in-memory per-company state at all and pushing
  everything into a `(company_id, ...)`-keyed persisted table instead (`scheduler_state`'s own
  `PRIMARY KEY (company_id, resource_kind)` shape). The Scheduler pattern is recommended if Catalogue
  ever needs Connector-side state, as the structurally safer of the two proven options.
- **TD-037's lesson (stale in-memory display state on company switch):** Android's
  `SyncRepositoryImpl` leaked a previous company's cached summary object after switching companies
  until a `bindCompany()` change-detection fix was added. **Catalogue's own ViewModel(s) must apply
  the identical discipline**: any per-company enrichment/summary cache held in a ViewModel (the same
  class of cache `ConnectViewModel` already carries for Party/Ledger enrichment) must detect a real
  company switch (not just re-subscription) and clear/rebuild, exactly like `ConnectViewModel`'s own
  `hasSeenCompany` guard already does.

**Where `companyId` alone is insufficient (identified explicitly, not glossed over):**

- **Override rows** — `companyId` alone does not disambiguate a row's *level*; the composite key must
  also encode exactly which one of `productId`/`branchId`/`stockGroupId` (or none, for catalogue-wide)
  applies, with a check constraint or equivalent ensuring at most one is set per row (§8).
- **`ProductSourceLink`** — `companyId` alone does not prevent two different Stock Items in the same
  company from both claiming to link the same Catalogue Product (or vice versa); the full composite
  key `(companyId, sourceType, externalStockItemId)` uniquely identifies the source side, and a
  separate uniqueness constraint on `(companyId, productId)` (at most one active link per product)
  is required on the product side — mirrors `PartySourceLink`'s own existing dual-uniqueness shape.
- **Catalogue assets** — `companyId` alone is insufficient to prevent path-traversal or cross-product
  asset confusion; the full `(companyId, productId, assetId)` tuple must be validated on every read,
  exactly like `BusinessProfileLogoStore.resolveLogoFile`'s existing path-containment check.
- **Structural gap already present codebase-wide (not new to Catalogue):** as this session's research
  confirmed, `companyId` scoping in this codebase is **convention-enforced at every call site**, not
  structurally guaranteed by a wrapper/decorator/query-interceptor anywhere (Android or Connector).
  Catalogue inherits this same residual risk — a hypothetical future DAO method that omits its
  `WHERE companyId = :companyId` clause would not be caught by the type system. This is a pre-existing,
  codebase-wide characteristic, not something Catalogue introduces or should attempt to silently fix
  outside its own scope; it is named here so implementation-phase code review knows to check for it
  explicitly on every new Catalogue query, matching this codebase's own established review discipline.

---

## 14. Existing Tally identity — reuse, not reinvention

**No new identity mechanism is invented.** Catalogue reuses `resolveStockItemStableId()`
(`stock-item-identity.ts:10-20`, GUID → AlterID → name-slug) exactly as Stock Item sync already
produces it on `StockItem.id` — the `ProductSourceLink.externalStockItemId` is that same string,
unchanged.

| Scenario | Behavior |
|---|---|
| **Initial import** | Owner (or a future bulk "create Drafts from Stock Items" action, not itself locked/required by this document) links a Catalogue Product to a Stock Item by its existing stable id; creates the `ProductSourceLink` row |
| **Repeated sync** | Stock Item's own sync (unmodified) updates `StockItemEntity` as it already does; Catalogue's Tally-mirrored fields (name/unit/HSN/GST/parentGroup, §6) refresh from the same row, non-destructively (separate-table design, §6) |
| **Rename** | `StockItem.id` unchanged (GUID-first) → `ProductSourceLink` unaffected → no duplicate created (§6, guaranteed) |
| **Alias change** | Same — alias is not part of the identity chain for either Ledger or Stock Item; a Catalogue Product is never re-linked by an alias change |
| **Tally disappearance** | `StockItem.isDeleted` flips true (existing convention) → linked Catalogue Product surfaces "source unavailable" (§6) → never auto-deleted |
| **Reappearance** | Same stable id reappears with `isDeleted=false` on a later sync → "source unavailable" clears automatically, no owner action needed to re-establish the link (the link itself was never broken, only flagged) |
| **Duplicate-looking items** | Out of scope — Manual-item ↔ Tally-item matching/merging is explicitly future (§3); Catalogue does not attempt fuzzy matching |
| **Company switch** | `ProductSourceLink` is `companyId`-scoped like everything else (§13); switching companies simply queries a different row set, no special handling needed |
| **Branch context** | Orthogonal to identity — a Stock Item's link to a Catalogue Product is company-wide (one shared catalogue, §8), never branch-specific; branch only affects override resolution, not identity |

This achieves "permanent reconciliation without destructive enrichment loss" (the stated goal)
through exactly two mechanisms already proven elsewhere in this codebase: stable GUID-first identity
(Ledger/StockItem) and non-destructive partial-field sync (this session's own
`updateContactDetailsMany`/COALESCE pattern, generalized here to a separate-table design for even
stronger isolation between Tally-owned and Catalogue-owned columns).

---

## 15. Timestamp and conflict integrity

**Locked requirement (decision-lock pass, 2026-08-24):** Catalogue conflict/publication decisions
must not blindly rely on device-local clocks; implementation must use an authoritative/server-side
timestamp. This document does not prescribe a new technical mechanism — it identifies where an
authoritative timestamp already originates in this architecture.

**This is a LAN-local architecture with no cloud backend** — the Connector (running on the
business's own Desktop, always reachable during business hours) is the closest thing to a "server"
here, and it **already stamps authoritative timestamps** throughout its existing code (e.g.
`new Date().toISOString()` on every sync-run record, pairing-session row, and — from this session's
own recent work — the bulk contact-details result's `requestedAt`). **The conceptual answer:** for
any conflict/publication decision made while the Android client is connected to its paired Connector,
the Connector's own clock is the authoritative source, exactly the same trust boundary Android
already extends to it for sync-run timestamps.

**The genuinely unresolved edge case, named explicitly rather than invented around (§20):** this
codebase's Android side is local-first and supports offline editing elsewhere (e.g., offline voucher
creation, per `docs/architecture/BUDCOM-PHASE3E...` and similar). If a Catalogue Draft edit happens
**while Android is offline** (no Connector reachable), there is no authoritative timestamp available
at the moment of edit — only the device's own clock. Reconciling this (does an offline edit get a
provisional device timestamp later re-stamped on reconnect? What happens if two offline edits from
different devices race before either reconnects?) is not answered by any existing pattern in this
codebase and would require inventing new conflict-resolution mechanics beyond what's currently
locked. **This document does not invent that mechanism** — it is flagged as an open question for
implementation planning, not silently resolved here.

---

## 16. Architecture impact analysis

No file in any of these areas is modified by this planning task. Classification is for the *future*
implementation session's benefit.

| Area | Classification | Notes |
|---|---|---|
| **Connector — Stock Item extraction** | **Extend (prerequisite, separate from Catalogue itself)** | Must add `PARENT`/`CATEGORY`/`BASEUNITS`/`GSTAPPLICABLE` (at minimum `PARENT`) to the stock-item Fetch list, with its own Tally-TDL safety validation — same discipline as this session's own Ledger contact-details work. **Not** Catalogue code; a named prerequisite (§1, §21 Milestone 0) |
| **Connector — everything else** | **Reuse unchanged** | Per PDL-020 §7, no Desktop/Connector Catalogue surface; Catalogue Product/Draft/Publish state is Android/Room-local only in this design |
| **Android — Business Profile** | **Extend (new adjacent table)** | Branch entity, if placed here per §8's recommendation — a new table, `business_profile`/`BusinessProfileEntity` itself untouched |
| **Android — Stock Item foundation** | **Reuse unchanged** | `StockItemEntity`/DAO/repository read-only consumed by Catalogue, never modified |
| **Android — asset storage** | **New component (pattern-reuse, not code-reuse)** | New `CatalogueAssetStore` following `BusinessProfileLogoStore`'s discipline; that store itself untouched |
| **Android — Connect/Party** | **Reuse unchanged, referenced not duplicated** | Only if/when a future Catalogue-to-Party reference is needed (§5); no Party code touched by this design |
| **Android — Company/Sync infrastructure** | **Reuse unchanged** | `SelectedCompanyStore`/`CompanySessionPort`-style resolution reused for `companyId`, no changes |
| **Android — new Catalogue feature module** | **New component** | `feature/catalogue/{domain,data,presentation}` — the actual implementation surface |
| **Desktop** | **Future boundary — untouched** | PDL-020 §7, no Desktop Catalogue UI in MVP-1.4 |
| **Authentication/security** | **Reuse unchanged** | No new auth model; company isolation and existing device/session trust boundaries apply unchanged (§18) |
| **Sharing infrastructure** | **Extend (new content type on existing pattern)** | New Catalogue share-file generator alongside the existing Ledger/Voucher share-file generators; the Android `Intent.ACTION_SEND` plumbing itself untouched |

---

## 17. UX implementation boundary

Screens and their responsibilities only — no visual design, per this task's own instruction.

| Screen/state | Responsibility |
|---|---|
| **Catalogue home** | Entry point; product count/status summary; navigation to list, import, sharing |
| **Product list** | Filterable/searchable list (mirrors Connect/Ledger Browser's existing list pattern), scoped to current company + branch context, showing lifecycle state per row |
| **Product detail/edit** | Single-product view; editable fields per current lifecycle state (§7); shows linked Stock Item's Tally-authoritative fields as read-only, Catalogue-owned fields as editable |
| **Draft** | Product detail in Draft state — full edit surface |
| **Review** | Product detail in Review state — full edit surface per §7's proposed-default (flagged, §20); a submit-for-Publish action, Owner-only |
| **Publish** | A confirmation action (not a separate screen) — transitions Review → Published, generates the new customer-visible snapshot |
| **Archive** | A confirmation action; Archived products filtered out of the default list, visible via an explicit "show archived" toggle |
| **Branch context** | A company-level branch selector (mirrors the existing company selector's pattern), scoping the product list and override-editing surface |
| **Import/Excel flow** | Upload → preview (create/update/skip counts, per-row reasons, §9) → confirm → Draft creation |
| **Sharing flow** | Choose scope (category or full-catalogue), generate share file, hand off to OS share sheet |
| **Image management** | Per-product add/reorder/set-primary/delete, reusing the existing image-picker pattern already proven for Business Profile's logo upload |

---

## 18. Security and trust review

| Check | Status under this design |
|---|---|
| No direct Tally writes | Preserved — Catalogue never writes to Tally; Excel is interchange-only (§9), price sync is one-directional read (§12) |
| No unsafe Tally XML/TDL generation | Preserved — this design adds no new Tally request shape; the one prerequisite (§16, Stock Item `PARENT` fetch) reuses the exact same proven Collection-Fetch-modify mechanism already production-verified for Ledgers/StockItems, subject to the same mandatory live-validation gate this project already enforces (TD-040 precedent) |
| TD-001/TD-035 protections remain | Preserved — no change to the shared XML sanitizer or to Ledgers' already-fixed `PARENT` field; Stock Items' own `PARENT` fix (when it happens) is a separate, explicitly-flagged prerequisite, not silently bundled into Catalogue |
| Company isolation | Preserved and extended per the proven pattern (§13) |
| Authorization | Owner-only Publish/Archive enforced at the repository/use-case layer, mirroring `PartyRepositoryImpl`'s own state-transition guards; no new auth/role model introduced |
| Share-link exposure | No internal Room/filesystem paths ever exposed (§11, already locked); generated share files never contain raw asset file paths, only embedded/rendered content |
| Asset access | App-private storage only, path-containment-checked on every read (§10), never a `FileProvider`-exposed public URI beyond the existing share-generation flow's own already-proven pattern |
| Private Catalogue behavior | Private = simply not shareable, structurally — the share generator itself refuses to run for a Private catalogue, not merely a UI-layer hint (§11) |
| Unpublished content cannot leak | Structural — the share generator and any future visitor surface both read exclusively from the "current published snapshot" record (§7), which does not exist at all until first Publish; Draft/Review data has no code path that reaches a share file |
| Archived content cannot accidentally become public | Archive removes the product from the current-published-snapshot pool (§7); the share generator's read scope naturally excludes it — no separate "is archived" check needed to leak-proof this, it falls out of the data model |

---

## 19. Migration and compatibility

- **Database migrations required:** yes — additive only, following the exact `MIGRATION_9_10`
  precedent (`CREATE TABLE IF NOT EXISTS`, no existing table touched): `catalogue_product`,
  `product_source_link`, `catalogue_asset`, `branch`, `catalogue_override`. No column added to any
  existing table (`StockItemEntity`, `BusinessProfileEntity`, etc.).
- **API changes:** none required on the Connector if Catalogue Product data stays Android/Room-local
  (this design's recommendation, consistent with PDL-020 §7's "no Desktop/Connector surface"). The
  one Connector-side change is the Stock Item `PARENT`-fetch prerequisite (§16), which is not a
  Catalogue API — no new route needed for Catalogue itself.
- **Desktop changes:** none (PDL-020 §7).
- **Android changes:** new feature module only; zero changes to existing modules' public contracts.
- **Business Profile changes:** additive only if Branch is placed there (§8) — a new table, no
  existing column/behavior touched.
- **Asset-store changes:** none to the existing `BusinessProfileLogoStore` — a new, separate store.
- **Backward compatibility:** trivial — every migration is additive; an existing install with no
  Catalogue data simply has empty new tables until first used.
- **Existing-user behavior:** unaffected — Catalogue is opt-in, discoverable, not forced into any
  existing flow (mirrors the "quick-start default path" UX principle, Brainstorm Outcome §8).
- **Rollback consideration:** additive-only migrations are the easiest class to reason about for
  rollback (a rollback simply stops reading the new tables; no existing data was ever restructured).
  No special rollback tooling is required beyond this codebase's existing migration-testing
  discipline.
- **Empty/new-install behavior:** identical to any other feature — empty tables, empty product list,
  no special first-run migration path needed beyond standard Room `CREATE TABLE IF NOT EXISTS`.

---

## 20. Test strategy

| Layer | Coverage |
|---|---|
| **Unit** | Domain model mapping, override-resolution algorithm (§8) — exhaustive precedence-chain test matrix (item-only, branch-only, stock-group-only, catalogue-wide-only, and every combination, confirming first-match-wins), lifecycle state-transition guards (§7), price-sync-mode resolution (§12) |
| **Repository** | `ProductSourceLink` CRUD + rename/disappearance/reappearance behavior (§14, mirrors `PartyRepositoryImplTest`'s existing alias-seeding test shape), non-destructive Tally-field-refresh behavior (§6, mirrors this session's own `upsertMany` COALESCE regression test) |
| **Database** | Company-isolation adversarial tests at every new table (mirrors MVP-1.3-A's own 35-test precedent) — two companies with identically-named/identically-IDed products must never cross-contaminate; composite-key uniqueness enforcement tests for `ProductSourceLink` (§13) |
| **Integration** | Full Draft→Review→Publish→Archive round-trip; Excel import→preview→commit→Draft round-trip including reserved-name-collision rejection (§9) |
| **Company isolation** | Explicit two-company adversarial suite for every new DAO method, per this codebase's own standing convention |
| **Tally reconciliation** | Rename-survives-link, disappearance-flags-not-deletes, reappearance-auto-clears (§14) — each as its own explicit test, mirroring the existing Ledger identity test suite's shape |
| **Lifecycle** | Every locked transition rule (§7) as an explicit test: Owner-only Publish enforcement, Archive owner-only, new-Draft-over-Published creates a parallel record not an in-place mutation, publication-failure leaves Review state unchanged |
| **Excel** | Valid row → Draft; malformed row → per-row skip with reason; duplicate row → last-wins + flagged; reserved-name custom column → rejected with rename prompt; re-import-older-file → warning dialog fires (already-locked rule) |
| **Images/assets** | Upload/replace/delete two-phase safety (§10); path-containment adversarial tests (traversal attempts, cross-company/cross-product asset-id guessing) |
| **Sharing** | Only-Published-content-in-share adversarial test (attempt to leak a Draft/Review product into a generated share file — must fail); Private-catalogue share-generation refusal (§11) |
| **Pricing** | Auto/Manual mode resolution through the override chain; pending-price-change notification correctness under Manual mode |
| **Publication** | Atomic snapshot-replace correctness (no partial-update window observable) |
| **Failure/recovery** | Publication failure leaves no partial customer-visible state (§7); Excel import failure/partial-file handling |
| **Concurrency** | Two near-simultaneous Owner edits to the same product (last-timestamp-wins per the locked conflict rule, §9/§15) — needs the authoritative-timestamp mechanism resolved first (§15's open question) before this test can be written meaningfully for the offline case |
| **Offline behavior** | Browsing an already-synced/drafted catalogue fully from Room while offline (mirrors the Ledger/StockItem offline-read precedent); explicitly deferred until §15's offline-timestamp question is resolved for the *editing* (not just reading) case |

**Adversarial tests drawn from named lessons:**

- **TD-035/TD-042 class (missing Fetch field → silently blank data):** a test asserting the Stock
  Item `PARENT` fetch-and-parse round-trip actually returns a non-null value against a realistic
  fixture, mirroring `entity-mappers.test.ts`'s own "contact-only response" test from this session's
  recent work — written *before* any Stock-group-override feature work relies on it (§21 Milestone 0).
- **TD-036 class (un-scoped in-memory state):** if any Connector-side in-memory Catalogue state is
  ever introduced, a two-company-interleaved-request test proving no cross-company bleed, mirroring
  TD-036's own regression test.
- **TD-037 class (stale in-memory display cache):** a company-switch test on any new Catalogue
  ViewModel proving its enrichment cache clears on a real switch and survives a same-company
  re-subscription, mirroring `ConnectViewModel`'s own `hasSeenCompany`-guard test shape.
- **TD-039 class (sync completes but local cache never refreshes):** if Catalogue ever gains its own
  "sync now"-style action, a test proving the local Room cache actually refreshes post-sync, not just
  the remote/Connector side.
- **TD-040 class (unvalidated Tally request shape):** the Stock Item `PARENT`-fetch prerequisite must
  ship with recorded live-validation evidence in the Connector's operation registry, exactly like
  every existing entry — never enabled from "it looks safe."
- **TD-041 class (cancellable detached coroutine losing work):** any Catalogue reconciliation/
  publish-side-effect coroutine must be awaited in its caller's suspend chain, never a detached
  `viewModelScope.launch` a fast navigation could cancel mid-work — the exact TD-041 root cause.

**Real-device validation requirements:** company-isolation tests, Tally-reconciliation tests (rename/
disappearance/reappearance against a real synced company), and the Stock Item `PARENT`-fetch
prerequisite's live-validation must all be confirmed on a real device against a real paired
Connector/Tally instance before release — matching this project's own standing practice (every prior
milestone's JVM-green result was explicitly treated as necessary but not sufficient).

---

## 21. Implementation milestones (dependency-ordered, not artificial 1.4a/b/c boundaries)

**Milestone 0 — Stock Item Fetch-field prerequisite (Connector-only, not Catalogue code).**
*Objective:* make `StockItem.parentGroup` (and ideally `category`/`baseUnit`/`gstRate`/
`closingBalance`) reliably populated, closing the gap named in §1/§16. *Files:* Connector
`ledger-identity.ts`-equivalent stock-item Fetch-field constant, `master-data-templates.ts`.
*Dependencies:* none. *Tests:* fetch-and-parse round-trip fixture test (§20). *Acceptance:* live
Tally validation evidence recorded in the operation registry, same discipline as this session's own
`LEDGERS_CONTACT_DETAILS` work. *Stop condition:* do not proceed to Milestone 3 (Stock-group
overrides) until this is live-validated — every other milestone can proceed in parallel/before it.

**Milestone 1 — Data model + identity foundation.** *Objective:* `catalogue_product`,
`product_source_link`, and the Tally-mirrored/Catalogue-owned separate-table split (§6). *Files:* new
`feature/catalogue/domain/model/`, `data/local/CatalogueEntities.kt`, `data/local/CatalogueDao.kt`,
`ProductSourceLinkDao` (§5, §14). *Dependencies:* none (Stock Item foundation already exists).
*Tests:* repository/DAO unit + company-isolation adversarial (§20). *Acceptance:* a Catalogue Product
can be created linked to a Stock Item, survives a simulated rename, flags on simulated disappearance.
*Stop condition:* none blocking — foundation only.

**Milestone 2 — Lifecycle state machine.** *Objective:* Draft→Review→Publish→Archive transitions and
their authorization guards (§7). *Dependencies:* Milestone 1. *Tests:* lifecycle transition-rule
suite (§20). *Acceptance:* every locked transition rule has a passing test; Owner-only enforcement
verified.

**Milestone 3 — Branch + override architecture.** *Objective:* `Branch` entity, override-row table,
resolution algorithm (§8). *Dependencies:* Milestone 1; Milestone 0 for the Stock-group level
specifically (item/branch/catalogue-wide levels can ship independently of Milestone 0). *Tests:*
exhaustive override-precedence matrix (§20). *Acceptance:* resolution algorithm passes every
precedence-combination test; Stock-group level explicitly gated behind Milestone 0's completion.

**Milestone 4 — Enrichment + assets.** *Objective:* description/specifications fields, customer-facing
category, `CatalogueAssetStore` (§10). *Dependencies:* Milestone 1. *Tests:* asset two-phase
replace/delete safety, path-containment adversarial (§20). *Acceptance:* an image can be uploaded,
replaced, and deleted with no dangling file/DB-row window.

**Milestone 5 — Price governance.** *Objective:* Open/"Contact for price" display modes, Auto/Manual
sync, pending-price-change notification (§12). *Dependencies:* Milestone 3 (uses the override chain
for sync-mode selection). *Tests:* sync-mode resolution + pending-change notification correctness.

**Milestone 6 — Excel contract.** *Objective:* import/export, reserved-name enforcement, preview,
malformed/duplicate-row handling (§9). *Dependencies:* Milestone 1 (identity matching), Milestone 4
(fields to round-trip). *Tests:* full Excel validation matrix (§20). *Acceptance:* reserved-name
collision correctly rejected with rename prompt; re-import of an older file correctly triggers the
locked warning dialog.

**Milestone 7 — Publication boundary + sharing.** *Objective:* atomic "current published snapshot"
record (§7), category/full-catalogue share-file generation (§11). *Dependencies:* Milestone 2
(Publish transition), Milestone 4 (content to share). *Tests:* only-Published-content-leaks
adversarial test, Private-catalogue refusal test (§20). *Acceptance:* a Draft/Review product cannot
appear in any generated share file, verified adversarially, not just by code inspection.

**Milestone 8 — Integration + hardening.** *Objective:* full cross-cutting adversarial pass (company
isolation across every new table simultaneously, TD-035/036/037/039/040/041-class regression tests,
§20), UX screens wiring (§17), real-device validation. *Dependencies:* all prior milestones.
*Acceptance:* the full test matrix (§20) green, real-device Tally-reconciliation and company-isolation
checks passed, matching this codebase's own standing pre-release discipline.

This sequencing deliberately does not reuse the retired 1.4a/1.4b/1.4c labels (per this task's own
instruction) — milestone boundaries here are drawn strictly by dependency order and risk
concentration, not by the old, now-superseded phase groupings.

---

## 22. Risks and unresolved questions

**Named risks (carried forward, some pre-existing from the prior revision, updated):**

1. Stock-group override level's data-availability gap (§1, §16) — has its own milestone gate (§21
   Milestone 0), not a blocker for the rest of the plan.
2. Offline-editing timestamp authority (§15) — **genuinely unresolved**, flagged explicitly rather
   than invented around. Needed before concurrency/offline-editing tests (§20) can be written
   meaningfully.
3. Branch placement (Business-Profile-adjacent vs. a new shared module, §8) — an architecture
   choice with a recommended default, not a blocking decision; either choice satisfies the locked
   scope.
4. SKU/Product-ID generation and uniqueness policy — PDL-020 §1 names "SKU/Item Code" as part of
   minimal identity but does not specify whether it is owner-entered, auto-suggested from the linked
   Stock Item's `partNumber`, or system-generated, nor its uniqueness scope (per-company? per-branch?).
   **Flagged as genuinely open**, not decided here — a reasonable default (owner-entered or
   auto-suggested, unique per `companyId`, immutable once Published) is proposed but explicitly
   marked as a proposal requiring confirmation, not a locked rule.
5. Review-state edit permissions (§7) — this document proposes "unrestricted, same as Draft" as the
   simplest consistent default since no stricter rule was ever locked; flagged as a proposed default,
   not confirmed.
6. Scope-creep discipline (carried from the original Brainstorm Outcome §10) — forward-compatible
   *design* is done now (this document); forward-compatible *features* (former 1.4a/b/c) must stay
   untouched until formally authorized, unchanged from the original risk.
7. Marketplace/social-feed drift — named repeatedly across every downstream milestone in this
   codebase's planning history; worth naming here too, even though nothing in this design approaches
   it.

**This document deliberately does not invent answers for items 2, 4, and 5 above** — they are
presented as open questions for implementation planning or explicit product-owner confirmation, per
this task's own governing instruction.

---

## 23. Claude autonomy boundary (carried forward, unchanged in spirit)

Once implementation is separately authorized, the implementing session must not: invent scope this
document didn't lock; alter the company-isolation invariant (§13); add AI/cloud/marketplace capability
under any framing; add a Desktop or Connector surface beyond the one named prerequisite (§16 Milestone
0, itself requiring its own explicit go-ahead and live-validation); reopen item-level sharing without
separate authorization (§3); build full version-history/rollback; install on the owner's device
without explicit authorization; change signing configuration; force-push, rewrite history, or touch
unrelated work. If a genuine new architectural ambiguity appears mid-implementation that this
document didn't anticipate, the correct response is the same one this review itself followed: stop,
record the ambiguity precisely, and do not guess.

---

## 24. Source documents this revision reconciled

`docs/architecture/BUDCOM-MVP-1_4-CATALOGUE-BRAINSTORM-OUTCOME.md` (as decision-locked 2026-08-24),
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-020 (`:280-362`), this document's own prior
revision (2026-08-19, preserved in git history), `docs/technical-debt/registry.md` (TD-001, TD-035,
TD-036, TD-037, TD-039, TD-040, TD-041, TD-042), `docs/status/BUDCOM-GROUND-TRUTH-PROJECT-STATE-AUDIT.md`
(2026-08-23), `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` (through §46/Phase 55), and direct repository
inspection this session of: `feature/businessprofile/**`, `feature/masterdata/stockitem/**`,
`feature/party/**` (source-link pattern), `connector/budcom_connector/src/extraction/core/
stock-item-identity.ts`, `.../templates/master-data-templates.ts`, `.../parsers/entity-mappers.ts`,
`.../erp/stock-item/stock-item-domain.ts`, `.../services/stock-item/*`, `.../services/scheduler/
adaptive-scheduler.service.ts`, `.../storage/sqlite/schema.ts`, and `.../api/routes/stock-items.ts`.

The companion `BUDCOM-MVP-1_4-CATALOGUE-BRAINSTORM-CONTEXT.md` file referenced by the Brainstorm
Outcome document does not exist anywhere in this repository (confirmed this session) — it is not
among the sources reconciled here because it cannot be found, not because it was skipped.
