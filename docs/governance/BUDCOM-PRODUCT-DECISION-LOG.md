# BUDCOM Product Decision Log

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
**Decision:** For every major BUDCOM capability, the following must be explicitly known and evidenced (in code, architecture notes, or this log) rather than left as an undocumented assumption: source of truth, stable identity, ownership/layer responsibility, persistence location, migration path, failure behavior, recovery behavior where relevant, and replacement/evolution path.

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
**Decision:** Relationship Timeline is the single, unified historical presentation for a Party. Party = the relationship; Timeline = the history of that relationship. Notes and Issues (and, where reliable evidence exists, accounting events, contact/activity events, and BUDCOM actions) are structured objects/events that appear *within* the Timeline. Do not build separate, competing "Notes History" / "Issue History" / "Relationship Timeline" experiences. Only implement event types supported by approved scope and actual repository evidence — no fabricated events, no generic event engine built merely for future flexibility, no AI-generated timeline entries, no duplicating the same event across multiple histories. The Timeline must remain deterministic, explainable, and locally grounded.

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
**Decision:** OS-level notifications are outside MVP-1.2 v1. Dincharya begins as an in-app action surface only: open BUDCOM → Dincharya → see today's/relevant actions → act. Do not introduce Android notification permissions, notification channels, background reminder scheduling, boot rescheduling, push notifications, notification services, or recurring OS reminders.

**Why:** Resolves architecture doc §3.4 (open question 4). Zero notification infrastructure exists today (no `POST_NOTIFICATIONS` permission, no channel, zero `Worker` classes despite WorkManager being wired at the Hilt level) — adding it now would open a new permission/security surface as a first release risk rather than a natural extension.

**Alternatives rejected:** Shipping OS push/local notifications in Dincharya v1 for immediacy.

**Consequences:** MVP-1.2-D builds Dincharya as a pull-to-check, in-app list only. No new Android permission, channel, or `Worker` is added anywhere in MVP-1.2.

**Revisit trigger:** After the in-app Dincharya workflow is proven with real usage, a dedicated future session may scope OS-level notifications as its own reviewable security/permission surface.

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
