# VENTURE Product Decision Log

**Status:** Active / permanent  
**Purpose:** Record consequential product decisions so they are not repeatedly reopened later.

## How to use

Add one entry whenever a decision materially affects product behavior, scope, architecture-facing UX, accounting semantics, release policy, or operating method.

Each entry should contain:

- Decision ID
- Date
- Area
- Decision
- Why
- Alternatives rejected
- Consequences
- Revisit trigger, if any
- Status: Locked / Superseded / Under Review

---

## Locked decisions

### PDL-001 — Optional/Estimate vouchers are non-accounting
**Decision:** Optional/Estimate vouchers must not affect Ledger balances or accounting totals. They do not count as regular Sales for Ledger defaults.

**Why:** They represent unconverted estimates, not completed accounting transactions.

**Status:** Locked

### PDL-002 — Ledger default scope
**Decision:** Default Ledger scope is the last 7 regular Sales vouchers plus all accounting movements within the resulting date interval. Receipts/Payments/Journals/etc. do not consume the count of 7 Sales.

**Status:** Locked

### PDL-003 — Ledger is local-first
**Decision:** Ledger statements open from Android Room. Normal accounting/Voucher synchronization auto-populates Ledger movement data. Changing locally-covered periods should not require a per-ledger network fetch.

**Status:** Locked

### PDL-004 — Ledger historical period support
**Decision:** Support Current FY, Previous FY, Last 30 Days, This Month, Last 7 Sales, and Custom dates. Architecture must support still-earlier manually requested history without a hardcoded two-year ceiling.

**Status:** Locked

### PDL-005 — MVP-1.0.x release boundary
**Decision:** MVP-1.0.x is only for defects, essential missed MVP-1 behavior, and bounded high-value micro-polish to existing MVP-1 functionality. No new feature/module expansion.

**Status:** Locked

### PDL-006 — Post-MVP feature cadence
**Decision:** From MVP-1.1 onward, every feature receives immediate mini-hardening. After a substantial batch, perform integrated hardening and real-world validation before release.

**Status:** Locked

### PDL-007 — Claude prompt clarification
**Decision:** Claude asks consequential questions before coding when material ambiguity exists, and may also checkpoint and ask mid-execution if new ambiguity appears.

**Status:** Locked

### PDL-008 — Preserve half-completed valid work
**Decision:** Claude must checkpoint and resume rather than discard valid partial work when blocked by ambiguity, limits, crashes, or approval gates.

**Status:** Locked

### PDL-009 — Claude usage optimization
**Decision:** Product thinking should happen before scarce Claude execution time. Major prompts are reverse-engineered from desired outcome and include mini-hardening, stop conditions, and usage measurement.

**Status:** Locked

### PDL-010 — User role post-MVP-1
**Decision:** User focuses primarily on feature/product planning and product acceptance. ChatGPT plans architecture and efficient Claude execution. Claude handles approved implementation/hardening autonomously.

**Status:** Locked

### PDL-011 — Architecture completeness for major capabilities
**Decision:** For every major VENTURE capability, the following must be explicitly known and evidenced (in code, architecture notes, or this log) rather than left as an undocumented assumption: source of truth, stable identity, ownership/layer responsibility, persistence location, migration path, failure behavior, recovery behavior where relevant, and replacement/evolution path.

**Why:** Undocumented architectural assumptions silently become defects, rediscovery cost, or migration risk later.

**Status:** Locked

### PDL-012 — Complexity must earn its place
**Decision:** Architectural or product complexity must be justified by meaningful business value — time saved, mistakes prevented, improved visibility, stronger business relationships, better recovery/resilience, better decisions, or materially better user experience — never by technical interest alone.

**Status:** Locked

### PDL-013 — Sophisticated internals, simple experience
**Decision:** Users must never need to understand Room, snapshots, reconciliation internals, Connector internals, SPKI, storage architecture, or network-discovery mechanics. Where possible, the UI communicates simple truthful states: Connected, Fresh, Syncing/Pending, Offline, Needs attention, Done. Important state must never be hidden, but technical complexity must never be exposed unnecessarily.

**Status:** Locked

### PDL-014 — Relationship Timeline is the unified relationship history
**Date:** 2026-08-18
**Area:** MVP-1.2 — Party Detail / Relationship Timeline
**Decision:** Relationship Timeline is the single, unified historical presentation for a Party. Party = the relationship; Timeline = the history of that relationship. Notes and Issues (and, where reliable evidence exists, accounting events, contact/activity events, and VENTURE actions) are structured objects/events that appear *within* the Timeline. Do not build separate, competing "Notes History" / "Issue History" / "Relationship Timeline" experiences. Only implement event types supported by approved scope and actual repository evidence — no fabricated events, no generic event engine built merely for future flexibility, no AI-generated timeline entries, no duplicating the same event across multiple histories. The Timeline must remain deterministic, explainable, and locally grounded.

**Why:** Resolves architecture doc §3.1 (open question 1). Answers the Product Owner's explicit instruction that Party Detail should read as one coherent relationship history rather than several parallel, overlapping views.

**Alternatives rejected:** Building Notes History, Issue History, and Relationship Timeline as three separate presentation surfaces over the same underlying data.

**Consequences:** MVP-1.2-B's Relationship Timeline design (architecture doc §10) is confirmed as the primary read surface replacing the flat Notes list's presentation, not an addition alongside it. Issue History (1.2-C) is a filtered view *into* the Timeline, not a separate list implementation (architecture doc §10 already anticipated this — now locked, not merely a working assumption).

**Revisit trigger:** If a future milestone identifies a relationship-history event type that cannot be deterministically and locally grounded, or the Product Owner explicitly reopens the presentation question.

**Status:** Locked

### PDL-015 — Referral Tree / RJ Concept deferred, outside MVP-1.2
**Date:** 2026-08-18
**Area:** MVP-1.2 scope boundary
**Decision:** Referral Tree / RJ Concept is explicitly outside MVP-1.2. Do not implement referral tables, referral UI, or referral attribution logic. Do not modify Party identity architecture merely to anticipate Referral Tree. It is deferred to a later, dedicated architecture and implementation milestone.

**Why:** Resolves architecture doc §3.2 (open question 2). The locked Master Product Execution Plan §8 MVP-1.2 line item never names Referral Tree even though the referral spec calls it "VVIMP" — this was a genuine sequencing ambiguity, now closed by explicit Product Owner decision rather than a Claude judgment call.

**Alternatives rejected:** Folding Referral Tree into MVP-1.2 alongside Relationship Timeline/Issue History/Dincharya, since the spec labels it high-importance.

**Consequences:** No referral schema, UI, or Party-identity change occurs in MVP-1.2-A through 1.2-E. Referral Tree remains a fully open future milestone with its own dedicated planning session.

**Revisit trigger:** A dedicated future architecture/planning session for Referral Tree, explicitly authorized by the Product Owner.

**Status:** Locked

### PDL-016 — Home Insights / OI system deferred, outside MVP-1.2
**Date:** 2026-08-18
**Area:** MVP-1.2 scope boundary
**Decision:** Home Insights and the broader Insights/OI system remain outside MVP-1.2. Do not implement a Home Insights dashboard, Attention Engine, generative AI, insight ranking, Mango attribution, insight notifications, a full OI engine, AI summaries, or other speculative intelligence features. The architectural distinction holds: Insights determines what deserves attention; Dincharya manages what the user should do. MVP-1.2 may produce deterministic action data that could eventually feed Insights, but must not build the Insights system itself.

**Why:** Resolves architecture doc §3.3 (open question 3). No Home Insights content specification exists anywhere in the repository (confirmed by the MVP-1.2 planning session) — building it now would mean inventing a workstream the Product Owner has reserved for separate, dedicated planning.

**Alternatives rejected:** Building a minimal Home Insights surface alongside Dincharya on the theory that the two are entangled.

**Consequences:** Dincharya (1.2-D) is self-contained and does not wait for or depend on the Insights workstream. No Insights-specific schema, ranking logic, or AI call is introduced anywhere in MVP-1.2.

**Revisit trigger:** A dedicated future Insights/OI specification and planning session, explicitly authorized by the Product Owner.

**Status:** Locked

### PDL-017 — OS-level notifications deferred, Dincharya v1 is in-app only
**Date:** 2026-08-18
**Area:** MVP-1.2 — Dincharya
**Decision:** OS-level notifications are outside MVP-1.2 v1. Dincharya begins as an in-app action surface only: open VENTURE → Dincharya → see today's/relevant actions → act. Do not introduce Android notification permissions, notification channels, background reminder scheduling, boot rescheduling, push notifications, notification services, or recurring OS reminders.

**Why:** Resolves architecture doc §3.4 (open question 4). Zero notification infrastructure exists today (no `POST_NOTIFICATIONS` permission, no channel, zero `Worker` classes despite WorkManager being wired at the Hilt level) — adding it now would open a new permission/security surface as a first release risk rather than a natural extension.

**Alternatives rejected:** Shipping OS push/local notifications in Dincharya v1 for immediacy.

**Consequences:** MVP-1.2-D builds Dincharya as a pull-to-check, in-app list only. No new Android permission, channel, or `Worker` is added anywhere in MVP-1.2.

**Revisit trigger:** After the in-app Dincharya workflow is proven with real usage, a dedicated future session may scope OS-level notifications as its own reviewable security/permission surface.

**Status:** Locked

### PDL-018 — Dincharya v1 item-eligibility, ordering, and completion rules

**Date:** 2026-08-18
**Area:** MVP-1.2-D — Dincharya
**Decision:** Dincharya v1 is a deterministic, action-focused worklist with exactly three item
types, each locked as follows:

1. **Contact completeness** — a Party is contact-complete when it has *at least one* valid phone
   **OR** valid email (not both required). "Pending Contact Completion" shows only when a Party is
   missing *both* a valid phone and a valid email. "Valid phone" reuses
   `PhoneNumberNormalizer.normalizeForSearch` exactly (the same `primaryPhoneNormalized` column
   every other Party surface already relies on) — never a second phone-validation rule. "Valid
   email" mirrors this codebase's existing convention of applying no format check anywhere: non-
   blank after trim is the only bar.
2. **Contact-person completeness is out of scope for Dincharya v1** — no separate tasks for missing
   contact-person name/designation/phone/email are generated, even though the underlying
   architecture (MVP-1.1) already tracks contact persons. Only the Party-level minimum-
   contactability rule above is in scope.
3. **Prospects are excluded from Pending Contact Completion** — a Prospect missing both phone and
   email never appears in this Dincharya group. The rule is not broadened to reconsider this later
   without a fresh product decision.
4. **Follow-ups never automatically expire** — an overdue `commitment`/`follow_up` note remains
   active in Dincharya indefinitely until the user explicitly marks it complete or reschedules it
   (changes its due date). There is no age-based decay, demotion, or silent removal.
5. **Deterministic ordering, never an invented priority score** — every group orders by a plain,
   explainable rule (`dueAt ASC` for follow-ups — which yields overdue-first, then due-today, then
   upcoming, for free, since those are already chronologically ordered; earliest-pending-since for
   Tally confirmation; display name for contact completion), with a stable secondary tie-break
   (natural key) so paging/rendering is never ambiguous. No AI ranking, no "urgency score," no
   generative recommendation of any kind.
6. **Every item has a deterministic source, ordering rule, and disappearance condition** — a
   follow-up disappears only when completed or its due date changes past the eligibility window; a
   pending-confirmation item disappears only when every one of that Party's `exported`-state fields
   clears (confirmed or conflicted) via a genuine Tally re-sync; a contact-completion item
   disappears only when a valid phone or email is actually added. Never merely because the screen
   was opened or refreshed.

**Why:** These are the concrete implementation-level decisions the MVP-1.2-D readiness review (see
`docs/status/VENTURE-MVP-1-2-RELATIONSHIP-TIMELINE-DINCHARYA-STATUS.md` "Checkpoint" §CP6.4) flagged
as genuinely open and requiring an explicit Product Owner decision rather than a Claude judgment
call — specifically, the exact contact-completeness scope (Customer/Prospect/Supplier/Other
boundary) and the aging/decay rule for long-overdue follow-ups. Both are now closed by explicit
instruction, not inferred.

**Alternatives rejected:** Requiring both phone and email for contact-completeness (rejected —
either is sufficient to reach the Party); including contact-person-level tasks in v1 (rejected —
disproportionate scope for a v1 worklist, deferred to a future milestone if ever needed); including
Prospects in Pending Contact Completion (rejected — Prospects are pre-relationship records still
being qualified, not yet expected to carry full contact detail); an automatic expiry/decay window
for overdue follow-ups (rejected — silently hiding a genuinely unresolved commitment is a worse
outcome than an old item staying visible); an AI/heuristic priority score for ordering (rejected —
contradicts the "deterministic, explainable, bounded" architecture principle and Dincharya's
explicit non-AI framing, PDL-016/PDL-017).

**Consequences:** `PartyDao.pageMissingContactInfo`/`countMissingContactInfo` filter
`classification != 'prospect'`; `PartyNoteDao.pageFollowUpsForCompany`/`countFollowUpsForCompany`
carry no `dueAt` lower bound and order by `dueAt ASC, noteId ASC`;
`PartyFieldProvenanceDao.pagePendingConfirmationForCompany` groups per Party and clears the instant
no field remains in the `exported` state. No new Tally state machine, no new phone/email validation
implementation, no contact-person Dincharya item type exist anywhere in the MVP-1.2-D code.

**Revisit trigger:** A dedicated future product session that explicitly wants contact-person-level
Dincharya tasks, wants Prospects included in contact-completion tracking, or wants a concrete
follow-up aging/decay policy — none of which should be inferred from this milestone's code.

**Status:** Locked

---

### PDL-019 — MVP-1.3 Business Profile: scope, fields, provenance, storage, and boundary

**Date:** 2026-08-18
**Area:** MVP-1.3 — Business Profile
**Decision:** Resolves the five open questions `docs/architecture/VENTURE-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`
§5 flagged as requiring Brainstorm 1, per an explicit ChatGPT/Product Owner review:

1. **Company scoping (§5.1):** Business Profile is scoped **per selected Tally `companyId`** — the
   same natural-key discipline every other VENTURE table already uses (`companyId` as the sole
   isolation boundary, no installation-global profile). "One business identity" means one identity
   *for the currently selected company*, not one identity for the whole installation. Conceptually:
   `Selected Tally Company → Business Profile → future Catalogue → future Vartalap`.
2. **Field list (§5.2):** a deliberately small, useful set — Business/Trading Name, Legal Name,
   Address, City, State, Pincode, Phone, Email, GSTIN, Website, Logo, Short Business Description.
   No arbitrary Tally field import; no catalogue/product fields; no social-feed/marketplace fields.
3. **Provenance (§5.3):** Business Profile is **100% VENTURE-owned data** for MVP-1.3 — the existing
   `PartyFieldProvenance` model is explicitly **not** applied. There is no approved Tally round-trip
   requirement for this milestone; no Connector mutation endpoint, no automatic Tally import. A
   future Tally-derived-field requirement is a separate architectural decision, not assumed now.
4. **Asset/logo storage (§5.4):** app-private storage for MVP-1.3, not the existing Private USB
   Storage mechanism (that remains available for a future decision if proven technically required).
   Storage sits behind a small abstraction so the mechanism can change later without touching the
   Business Profile domain model.
5. **Visitor-facing scope (§5.5):** MVP-1.3 is **owner-side only** — no visitor-facing "Resources"
   view. Resources/Catalogue/public-facing content is explicitly deferred to MVP-1.4 and later
   communication work.

**Why:** These were the exact open questions this repository's own MVP-1.3 planning/recovery review
(`docs/architecture/VENTURE-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md`, 2026-08-18) identified as
requiring a real product decision rather than a Claude judgment call (per PDL-010's own "ChatGPT
plans architecture" rule) — now answered by explicit ChatGPT/Product Owner review, closing the
Brainstorm-1 gap that document's §3 named as blocking implementation.

**Alternatives rejected:** an installation-global Business Profile (rejected — breaks the company-
isolation invariant every other VENTURE table relies on, and does not match how a user with several
paired Tally companies would expect distinct businesses to behave); forcing `PartyFieldProvenance`
onto Business Profile fields (rejected — no evidenced Tally round-trip exists for this milestone,
and doing so would be unjustified complexity, PDL-012); shipping visitor-facing Resources in 1.3
(rejected — a materially larger access-control surface than an owner-only editor, better sequenced
with Catalogue in 1.4); reusing Private USB Storage for the logo (rejected as the MVP-1.3 default —
no proven technical requirement yet, and app-private storage is simpler and lower-risk for a single
small image).

**Consequences:** MVP-1.3-A's schema uses `companyId` as (part of) its natural key, mirroring
`PartyEntity`; no `business_profile_field_provenance` table is built; no Connector/Tally change of
any kind is in scope; logo storage is app-private, behind an interface; no public/Resources screen,
route, or data model exists anywhere in MVP-1.3.

**Revisit trigger:** A dedicated future product session that explicitly wants a Tally-derived
Business Profile field, a visitor-facing Resources view, or a storage-mechanism change — none of
which should be inferred from MVP-1.3's own code.

**Status:** Locked

---

### PDL-020 — MVP-1.4 Catalogue: SKU identity, Stock Item relationship, lifecycle, Excel, assets, visitor scope, Desktop, sharing, company isolation

**Date:** 2026-08-19
**Area:** MVP-1.4 — Catalogue
**Decision:** Resolves the nine open product decisions
`docs/architecture/VENTURE-MVP-1-4-CATALOGUE-ARCHITECTURE.md` §5.3 flagged as requiring Brainstorm 1,
per an explicit ChatGPT/Product Owner review:

1. **SKU / product identity (§5.3.1):** a minimal, deterministic catalogue identity — SKU/Item
   Code, Product Name, Unit, Category/Family, and a Tally Stock Item reference, plus whatever
   stable identifiers publishing requires. Do not duplicate the complete Tally Stock Item record
   into the catalogue — the catalogue product is its own, smaller, presentation-oriented record.
2. **Stock Item relationship (§5.3.2):** a Catalogue Product references the relevant Tally Stock
   Item but remains a **VENTURE-owned presentation entity**. Tally remains the source of truth for
   accounting/product-master identity; VENTURE owns presentation, description, photographs,
   catalogue assets, customer-facing information, and publication state. No direct Tally
   write-back of any catalogue field.
3. **Lifecycle (§5.3.3):** locked as **Tally Stock Item → Catalogue Draft → Review → Published**.
   An edit must not automatically become a published customer-facing change — the
   draft/review/publish approval boundary is intentional, not incidental.
4. **Excel (§5.3.4):** one canonical Excel interchange contract. Excel is an import/export/
   interchange format only — never the operational database, never the source of truth, never a
   replacement for Room. Stable identifiers must be defined so an Excel round-trip cannot create
   accidental duplicate catalogue products.
5. **Assets (§5.3.5):** reuse the Business Profile asset-storage abstraction (MVP-1.3-A,
   `BusinessProfileLogoStore`'s pattern) where technically appropriate, rather than inventing a
   new arbitrary storage mechanism. Catalogue assets remain outside Room, in controlled
   app-private storage initially, with the same allowlist, size limits, path-traversal defense,
   company isolation, deterministic ownership, and safe deletion/replacement discipline that
   abstraction already proved.
6. **Visitor-facing Resources (§5.3.6):** **not** a full MVP-1.4 dependency. Build the owner-side
   catalogue foundation and publishing model first. A published *state* must exist
   architecturally even if the external visitor-facing surface itself is deferred — no
   marketplace/social-network behavior of any kind.
7. **Desktop (§5.3.7):** no Desktop catalogue UI in MVP-1.4. Do not modify Desktop or Connector
   merely for symmetry — only touch those layers when a concrete, approved dependency is
   demonstrated.
8. **Sharing (§5.3.8):** use the existing proven Android sharing mechanism first (the same
   `Intent.ACTION_SEND`/WhatsApp pattern Ledger/Voucher PDF sharing already established), over a
   controlled catalogue representation — never expose internal Room structures or filesystem
   paths directly. Do not introduce Vartalap as a dependency. Support the existing WhatsApp/
   WhatsApp Business sharing architecture where applicable.
9. **Company isolation (§5.3.9):** Catalogue is strictly `companyId`-scoped, matching every
   existing table's discipline — products, drafts, review state, publication state, assets,
   Excel import/export context, and sharing context must each carry a trustworthy company
   boundary. Never rely only on the currently selected company in UI state.

**Why:** These were the exact open questions this repository's own MVP-1.4 planning/recovery
review (`docs/architecture/VENTURE-MVP-1-4-CATALOGUE-ARCHITECTURE.md`, 2026-08-19) identified as
requiring a real product decision rather than a Claude judgment call (per PDL-010's own "ChatGPT
plans architecture" rule) — now answered by explicit ChatGPT/Product Owner review, mirroring
PDL-019's own precedent for MVP-1.3.

**Alternatives rejected:** duplicating the full Tally Stock Item record into the catalogue table
(rejected — unjustified complexity per PDL-012, and blurs which system owns which fields);
allowing an edit to publish immediately without a review step (rejected — removes the intentional
approval boundary between draft and customer-facing content); treating Excel as an operational
database (rejected — Room remains the sole local source of truth, per the Master Plan §3's own
cross-roadmap architecture principle); inventing a new, unrelated asset-storage mechanism instead
of extending the proven Business Profile pattern (rejected — unjustified complexity per PDL-012);
shipping visitor-facing Resources as an MVP-1.4 hard dependency (rejected — repeats the exact
access-control risk PDL-019 §5.5 already flagged and deferred for Business Profile; this codebase
has still never shipped a genuine multi-permission-level view of anything); adding a Desktop or
Connector catalogue surface for symmetry alone (rejected — no concrete dependency demonstrated);
building a new sharing mechanism instead of reusing the proven PDF/Intent-based pattern (rejected
— unjustified complexity per PDL-012); an installation-global or ambient-selected-company
catalogue scope (rejected — breaks the company-isolation invariant every other VENTURE table
relies on, same reasoning as PDL-019 §1).

**Consequences:** an MVP-1.4-A implementation may proceed against this locked scope without a
further product-brainstorm gate for these nine questions specifically. A future catalogue schema
uses `companyId` as (part of) its natural key, mirroring `PartyEntity`/`BusinessProfileEntity`; no
Tally-write endpoint is introduced anywhere; no visitor-facing route/screen/permission model is
built until a separate, explicit decision authorizes it; asset storage extends the existing
app-private abstraction rather than forking a second one; Excel import/export is additive/
interchange-only, never a second source of truth.

**Revisit trigger:** A dedicated future product session that explicitly wants a direct Tally
write-back for catalogue fields, a visitor-facing Resources view earlier than planned, a Desktop
catalogue surface, or a different sharing mechanism — none of which should be inferred from
MVP-1.4's own code.

**Status:** Locked

---

## New decision template

### PDL-XXX — <Title>
**Date:** YYYY-MM-DD  
**Area:**  
**Decision:**  
**Why:**  
**Alternatives rejected:**  
**Consequences:**  
**Revisit trigger:**  
**Status:** Locked / Superseded / Under Review
