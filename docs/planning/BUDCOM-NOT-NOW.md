# BUDCOM Not Now

**Purpose:** Preserve valuable ideas without allowing them to expand the current release scope.

## Rule

An idea placed here is not rejected. It is deliberately deferred.

Do not implement an item from this file unless:

1. Product Owner + ChatGPT explicitly promote it into a roadmap milestone; and
2. dependencies/scope/release timing are reconsidered.

## Deferred / later ideas

### AI knowledge layer from books/licensed content
Deferred until core platform, Tally Connector, workflows, security, adoption and revenue are stable.

### Public marketplace / social feed
Not part of early roadmap.

### Generic CRM expansion
Avoid premature expansion; Connect remains business-identity/relationship focused.

### In-app dialer / call-log
Cancelled from current Connect scope unless explicitly reconsidered later.

### New standalone A/c Data or Vartalap apps
Not planned unless explicitly changed; Contacts/Connect is the standalone exception.

### Direct write-back to Tally
Not current governance. User-initiated compatible XML import/export remains the boundary.

### Advanced generative AI dependency
Core messaging/workflows must not depend on AI. OI/deterministic helpers first.

### Prospect → existing Tally Ledger linking (Connect)
**Why valuable:** A BUDCOM Prospect (a pre-relationship record with no accounting ledger yet)
frequently *becomes* a real Debtor/Creditor once Tally accounting starts — today there is no way
to connect that Prospect's existing history/notes to the ledger that later appears. Recorded as a
**high-priority future Connect capability** (2026-08-19, during Phase 37/38 Ledger/Connect
real-device validation) once real Tally data made the gap concrete.
**Concept (exact, for the eventual design session):** A BUDCOM Prospect may later be explicitly
linked to an already-synced Tally ledger (Debtor or Creditor). Rules: (1) the ledger must already
exist in BUDCOM's synced local Ledger data — never a newly-created Tally ledger; (2) linking is
explicit and user-controlled — no automatic merge by name/phone/email/similarity; (3) no direct
Tally write is implied; (4) linking should be reversible, subject to the eventual identity model;
(5) existing Prospect history/notes must not silently disappear on link; (6) once linked, reuse the
existing `PartySourceLink`/accounting-identity machinery (MVP-1.1 Universal Party Identity) rather
than inventing a second accounting-identity system; (7) ledger search/selection must work offline
from the local Room cache, matching the already-proven Ledger Browser local-first pattern
(`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §26); (8) Debtor/Creditor classification must come from
trustworthy synced ledger information — note this currently depends on TD-035 (Connector `PARENT`/
group data) being resolved for the classification to mean anything for real ledgers, so this item
is also gated on that decision, not independently ready to schedule.
**Why not now:** No dedicated Brainstorm/architecture session has scoped it; the underlying
`PartySourceLink` linkage machinery this would reuse has only ever linked a Party to a ledger *at
creation/reconciliation time* (`ReconcilePartiesFromLedgersUseCase`), never as a later, explicit,
user-initiated re-link onto an *existing* Prospect — that's new interaction/state-transition surface
requiring its own design, not an extension of existing code inferred here.
**Dependencies:** TD-035 (Connector `PARENT`/group extraction — Debtor/Creditor classification is
currently a no-op against real data); MVP-1.1 Universal Party Identity / `PartySourceLink` (already
built, to be reused not replaced); Ledger Browser local-first search (already built, MVP-1 era).
**Potential release:** Not scheduled — a future Connect milestone, not part of MVP-1.4 (Catalogue)
unless the locked roadmap sequence explicitly changes.
**Promotion trigger:** Product Owner + ChatGPT explicitly scope it into a roadmap milestone with its
own Brainstorm, per this file's own rule.

---

## New item template

### <Idea>
**Why valuable:**  
**Why not now:**  
**Dependencies:**  
**Potential release:**  
**Promotion trigger:**  
