# FINAL MVP-1.4 CATALOGUE SCOPE LOCK

> **Status: LOCKED — authoritative as of the final Product Owner decision.**

This document records the MVP-1.4 Catalogue Brainstorm and its decisions. The following final scope lock supersedes any earlier wording in this document that stated that MVP-1.4a, MVP-1.4b, or MVP-1.4c required separate authorization.

## Final Scope Rule

MVP-1.4 is now treated as **one complete Catalogue milestone**.

Capabilities previously described as **1.4a / 1.4b / 1.4c** may be **absorbed into MVP-1.4** where they are:

* foundational to the Catalogue;
* necessary to make the locked Catalogue workflow complete;
* required to avoid architectural rework later; or
* naturally part of the Catalogue's data, enrichment, lifecycle, branch, pricing, asset, Excel, review, publication, or sharing foundation.

They must **not** be absorbed merely because they are desirable. Capabilities representing genuinely new product domains remain outside MVP-1.4 and remain FUTURE/UNSCHEDULED.

## Locked Architectural Boundaries

MVP-1.4 must preserve all existing VENTURE architectural boundaries, including:

* Tally remains **read-only** from VENTURE.
* Existing Tally Ledger → Party/Connect identity architecture remains authoritative for customers/suppliers.
* Catalogue must **not create a competing Ledger/Party identity system**.
* Existing synced Ledger details such as **address, phone, email and other Ledger-derived information remain reusable** by the existing Connect/Party architecture.
* Catalogue-owned enrichment must not be silently destroyed by Tally synchronization.
* Tally renaming must not create duplicate Catalogue products.
* Tally disappearance must not automatically mean Catalogue deletion.
* Company isolation is mandatory for every Catalogue entity, asset, branch, draft, publication and shareable object.
* Existing TD-001, TD-035, TD-036, TD-037, TD-039 and TD-040 safety/data-integrity protections remain authoritative.
* No new Tally XML/TDL request shape may be introduced without the established Tally safety-validation process.

## Locked Catalogue Decisions

The following decisions are final and must not be reopened during implementation planning unless the Product Owner explicitly changes them:

* Override resolution order: **Item → Branch → Stock-group → Catalogue-wide**.
* Excel native/current/future-native field names are reserved and must not be silently reused for custom Catalogue fields.
* Sharing scope: **category-level + full-catalogue sharing**.
* **Item-level sharing is not part of the current locked scope** unless separately authorized.
* Catalogue lifecycle, branch model, pricing, image/asset handling, enrichment and publication boundaries must follow the decisions recorded in this document and the authoritative Catalogue architecture.
* Conflict/publication decisions must not blindly depend on a device-local clock; authoritative timestamps must be used.

## Explicit Future / Unscheduled Capability

**Prospect → Ledger remains FUTURE / UNSCHEDULED and is not part of MVP-1.4 implementation.**

When eventually authorized, it includes:

1. Prospect linked to a **Debtor Ledger**.
2. Another Prospect linked to a **Creditor Ledger**.
3. Reuse of the **existing synced Ledger data** rather than creating a parallel Ledger/Party identity system.

This future capability must not be prematurely implemented as part of Catalogue.

## Implementation Authority

For implementation planning, this document must be read together with:

* the authoritative MVP-1.4 Catalogue Architecture document;
* the Product Decision Log, including PDL-020;
* current Development Status;
* Development Ledger;
* Technical Debt Registry.

Where an older section of this brainstorm document says that 1.4a/1.4b/1.4c are awaiting separate authorization, that wording is **superseded by this Final Scope Lock**.

This header does **not** authorize implementation by itself. It establishes the final product scope from which the separately authorized implementation-planning task must proceed.

**No further Catalogue brainstorming is required unless the Product Owner explicitly reopens the scope.**


# VENTURE MVP-1.4 CATALOGUE — BRAINSTORM OUTCOME

## Purpose
This document is the handover output of the MVP-1.4 Catalogue Brainstorm session, conducted following the sequence and rules set out in `VENTURE-MVP-1_4-CATALOGUE-BRAINSTORM-CONTEXT.md`.

It records every resolved decision, every deferred item, every open gap, and the final MVP-1.4 boundary. This is a **planning artifact**, not an implementation spec. Implementation planning is a separate, future workstream.

---

## 1. Status

**MVP-1.4 = brainstorm complete, boundary defined, not yet implemented.**

The original roadmap (Section 2 of the context doc) is **unchanged**. Three new phases have been proposed during this session — **1.4a, 1.4b, 1.4c** — all marked **RECOMMENDED**, pending the product owner's formal authorization before implementation begins. They are not LOCKED.

PDL-020's nine decisions remain accepted and unchanged. Where this session touched adjacent territory, it added attributes/clarifications (e.g., Source tag on Stock-Item) rather than reopening the original decisions.

---

## 2. Anchor decision — Primary job of Catalogue

**RECOMMENDED:** Sales enablement — a fast, accurate, shareable product reference staff/owner can send a customer over WhatsApp mid-conversation. Product discovery and customer self-service are secondary, natural byproducts once sharing exists.

---

## 3. Roadmap additions (new, pending formal authorization)

| Phase | Scope | Status |
|---|---|---|
| **1.4a** | Customer product selection, budget totals, self-facing Estimation output; tiered/quantity pricing; owner-facing analytics (shared with 1.4b) | RECOMMENDED, unauthorized |
| **1.4b** | Estimation shared as Purchase Order; buyer accounts; connection/approval workflow for private catalogues; version history/rollback for Published items; owner-facing analytics (shared with 1.4a) | RECOMMENDED, unauthorized |
| **1.4c** | Multi-language support — English, Hindi, Marathi, Kannada, Telugu to start | RECOMMENDED, unauthorized |

**Design principle carried into MVP-1.4:** every data model decision below is made so 1.4a/b/c can be built later **without reworking or rewriting MVP-1.4 code** — stable product identity, sum-able price fields, clean unit-of-sale, extensible text fields, extensible Excel schema.

---

## 4. MVP-1.4 — Resolved Product Model

### Core identity
- **Product ID** — stable, durable identifier referenced by all future phases.
- **Source** — `Tally` or `Manual`. Enables future matching/merging (FUTURE, unscheduled).
- **SKU** — per PDL-020 #1/#2, unchanged.

### Descriptive fields
- Name, Unit, Category (owner-reassignable, independent of Tally), HSN/GST (visible or hidden — owner's choice, both supported).
- Native field list is **not closed** — extensible over time.

### Enrichment fields
- Images (primary + optional additional), customer-facing description, Specifications.
- **Specifications:** free-form text at MVP-1.4 launch; data shape designed to support structured key-value specs later without migration.

### Pricing
- No pricing engine. **Open (visible)** or **"Contact for price"** only.
- Tiered/quantity pricing → **deferred to 1.4a**.
- Price-sync from Tally: **Auto** or **Manual**, selectable at **Catalogue-wide / Stock-group / Item** levels (layered, override order below).
- Manual mode: pending-price-change window shown to owner on next login; owner approves before customer-facing view updates.

### Lifecycle
- **Draft → Review → Publish → Archive.**
- Archive is **owner-initiated only** — no auto-archive on Tally deletion.
- Solo businesses: Review effectively skipped (owner does everything).
- Team businesses: staff prepares Drafts; **only Owner-level access can Publish** — no separate Reviewer role.

### Branches
- **One shared catalogue** across branches — not separate catalogues per branch.
- Branch can **Add**, **Edit**, and **Hide** (soft-delete) items within the shared catalogue.
- True deletion of the shared product record: **deferred**, not built in MVP-1.4.
- Publish rights are **branch-assignable** — a business can run some branches solo-style and others team-style simultaneously, with no separate "mixed mode" concept needed.

### Override resolution order (applies to price-sync mode, and generally)
**Item → Branch → Stock-group → Catalogue-wide.**
**LOCKED.**

### Images
- **Not required to publish.** One primary image per SKU + optional additional images.
- **Auto-compression on upload**; no user-facing size limit (soft technical ceiling only, to block absurd files).
- No image version history in MVP-1.4.

---

## 5. MVP-1.4 — Sharing & Customer Experience

- **Public/Private toggle**, catalogue-level, owner-controlled.
  - Public: link works with no login, scoped to that business only.
  - Private: **simply not viewable** — no partial/approval access. (Full account-based approval flow is 1.4b — see Section 3.)
- **Sharing granularity — LOCKED:** category-level link and full-catalogue link. Item-level sharing (carried from an earlier draft) is **NOT part of the currently locked MVP-1.4 sharing scope** unless separately authorized during implementation planning.
- **Customer-facing search/filter** within the shared scope — included in MVP-1.4.
- No customer login required for public viewing.

---

## 6. MVP-1.4 — Platform & Sync

- Available on **Desktop and mobile**, both with full edit capability (add, enrich, publish).
- **Excel export/import** bridges both, and enables catalogue creation for businesses **without any accounting software connected** (all items simply tagged `Manual`).
  - Round-trip fields: native fields (Name, SKU, Unit, Category, Price, Price-display mode, Description, etc. — list not closed) **plus owner-defined custom columns**.
  - **Native field names are reserved** — a custom column cannot reuse a native/future-native name; clear rename prompt shown on conflict. **LOCKED.**
  - Conflict resolution: **Owner action always wins over Staff action**, regardless of timing. Between two Owner actions, **most recent timestamp wins**. If an Owner re-imports an older file, a warning dialog fires: *"This file is older than the current version. Do you want to continue?"*
- Sync reuses the **existing adaptive sync engine** — Catalogue does not own its own scheduler (Desktop/Android display freshness/state only).
- **One-directional sync:** Tally → Catalogue only. No Tally writes. No invented/experimental Tally request shapes sent to production (permanent safety rule, Section 8 of context doc).

---

## 7. MVP-1.4 — Notifications

- Per-notification-type toggles in Settings, all actionable (link directly to the relevant item):
  1. New Tally item arrived as Draft
  2. Item aging in Draft too long
  3. Staff submitted item for review
  4. Excel import completed / warning fired
  5. Price-change pending (Manual sync mode)
- **Smart defaults (UX requirement, accepted):** #3 and #5 **on by default**; #1, #2, #4 **off by default**, discoverable later.

---

## 8. MVP-1.4 — UX Requirements (owner-seat review, accepted)

Adopted after reviewing the plan from the perspective of a solo/small-business owner persona:

1. **Quick-start default path** — minimal required fields, optional photo, one-tap publish. Stock-groups, branch settings, custom Excel columns, and granular notification settings stay hidden/discoverable, not shown upfront to businesses that don't need them.
2. **Smart notification defaults** — see Section 7.
3. **Gentle photo nudge** — a friendly prompt encouraging a photo (e.g., "add a photo to make this pop"), never a blocking requirement.

---

## 9. Explicitly Deferred / Future / Out of Scope

### Deferred to named future phases
- Tiered/quantity pricing → 1.4a
- Customer selection, budget totals, Estimation output → 1.4a
- Estimation shared as Purchase Order → 1.4b
- Buyer accounts, connection/approval workflow, private-catalogue access rules → 1.4b
- Version history / rollback for Published items → 1.4b
- Owner-facing analytics/usage visibility → 1.4a/1.4b
- Multi-language (UI and/or content) → 1.4c

### FUTURE (unscheduled, not yet named phases)
- Manual-item ↔ Tally-item matching/merging
- Order Capture (full), Vartalap integration
- **Prospect → Ledger — LOCKED as FUTURE/unscheduled:**
  - Prospect → Debtor Ledger
  - Prospect → Creditor Ledger
  - Reuses existing synced Ledger data

### OUT OF SCOPE (permanent, unchanged from original context)
- Marketplace, social network, generic CRM, e-commerce platform
- Generative AI dependency
- Invented/experimental Tally API calls against production
- True deletion of shared catalogue product records (not built; hide/soft-delete only)

---

## 10. Named Risks (carry into implementation planning)

1. **Scope-creep discipline** — forward-compatible *design* is done now; forward-compatible *features* (1.4a/b/c) must stay untouched until formally authorized.
2. **Clock-skew / timestamp integrity** — Excel conflict resolution depends on accurate timestamps; device-local clocks are unreliable. **Decision carried into implementation:** implementation must use an authoritative/server-side timestamp, not rely blindly on device-local clocks, for these comparisons. The specific technical mechanism is not decided here — that is implementation planning's job.
3. **Manual-item test coverage** — Manual-sourced Stock-Items need explicit testing, not assumed parity with Tally-sourced items in every code path.
4. **Private-toggle messaging clarity** — MVP-1.4's Private = "simply not viewable" must be communicated clearly in UI copy so it isn't mistaken for the richer approval-based access coming in 1.4b.
5. **Notification default tuning** — even with per-type toggles, poor defaults could feel noisy or cause missed alerts; defaults matter more than toggle existence (addressed in Section 7/8, but worth re-validating at implementation).
6. **Multi-language technical hook** — text fields (name, description, specs) should be designed now so 1.4c doesn't require a full data migration later.

---

## 11. Items Requiring Formal Confirmation Before Implementation

- Roadmap additions: **1.4a, 1.4b, 1.4c** (Section 3)
- UX requirements adopted from owner-seat review (Section 8)

---

## Closing note

This document supersedes nothing in the original context file — PDL-020, the architecture foundation, the locked roadmap, and all permanent exclusions remain as originally stated. This is an **additive planning record** of one brainstorming session's conclusions, ready to carry into either (a) formal roadmap authorization for 1.4a/b/c, or (b) MVP-1.4 implementation planning, as separate future workstreams.
