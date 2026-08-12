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
