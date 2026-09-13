# VENTURE Transaction Mode (Vartalap-derived) — Architecture Hardening Pass

**Status: ANALYSIS ONLY. Not authorized for implementation.** This document performs the
architecture-hardening pass requested against
`docs/architecture/VENTURE-VARTALAP-TRANSACTION-MODE-BRAINSTORM-OUTCOME.md`'s locked Q1–18 design.
**Zero Transaction Mode code exists anywhere in this repository** (confirmed this session by direct
inspection — no `feature/transaction`, `feature/chat`, `feature/vartalap` module, no
`commercial_transaction`/`txn_*` table, no chat/messenger UI surface of any kind). This document
produces a data model, integration-point analysis, migration plan, test strategy, and honest
open-questions list for a feature that is **RECOMMENDED but not LOCKED** at the product level (per
the Brainstorm Outcome §1/§9) — exactly the same authorization status MVP-1.4a/b/c carried before
their own architecture pass. No code, no migration, and no commit beyond this document was produced
in this session.

**The source document this pass depends on
(`VENTURE-VARTALAP-TRANSACTION-MODE-BRAINSTORM-OUTCOME.md`) does not exist anywhere in this
repository, its git history, or `Venture_archives`** (confirmed by direct search this session). It was
supplied directly by the user mid-session as raw content, not read from a repo path. **This document
recommends that file be checked into `docs/architecture/` at the path this document and the task both
already assume it occupies**, so future sessions (and PDL/governance cross-references) can resolve it
the same way `VENTURE-MVP-1_4-CATALOGUE-BRAINSTORM-OUTCOME.md` resolves today — this session did not do
so unilaterally, to keep this session's git footprint to exactly the one new file requested (see
Final git status, end of this document). The original Vartalap concept document
(`B2B_TRANSACTIONAL_MESSENGER_NEW_CHAT_CONTEXT.docx`) referenced by the Brainstorm Outcome's own
"Purpose" section **also does not exist anywhere in this repository** — confirmed by search. Per this
task's own instruction, the Brainstorm Outcome is authoritative and is the sole source reconciled
here; no conflict-with-the-original could be evaluated because the original is not available to this
session. This mirrors exactly how the Catalogue architecture document (§24, final paragraph) handled
its own missing companion context file — named as unavailable, not silently assumed.

---

## 1. Executive summary — three load-bearing findings

This session's own repository research surfaced three findings not previously recorded anywhere,
each significant enough to change how much of Q1–18 is actually buildable as designed. They are
stated up front because they reframe every section below.

### Finding 1 (the big one): the in-app half of Transaction Mode has no transport layer to run on

Q7, Q12, Q13, and Q16 all describe **live, bidirectional, cross-company state** — a seller-facing
inbox that receives a buyer's in-app submission, a "Mutual Agreement" moment that fires "identically
for both parties" (Q12), a per-counterparty transaction window showing "shared, mutually-visible
truth" (Q13), and a payment claim the seller cross-confirms (Q16). All of this presumes two different
VENTURE installations — the buyer's and the seller's, each its own `companyId`, each its own local Room
database — can exchange live state with each other.

**This architecture has no such mechanism today, and this document's own most authoritative
precedent explicitly rules one out.** Direct quote, `docs/architecture/VENTURE-MVP-1-4-CATALOGUE-ARCHITECTURE.md`
§15: *"This is a LAN-local architecture with no cloud backend."* Confirmed independently this session:
`backend/` exists in this repo only as a placeholder `README.md` from the very first Milestone-0
commit (`e8def45`, 2026-07-22), untouched since — `docs/PROJECT_PROGRESS.md` (itself a stale,
early-Milestone document) lists it as "Reserved... Optional per spec... placeholder only." Every
existing cross-boundary mechanism in this codebase is one of exactly two shapes, and neither fits:

1. **Phone ⟷ own paired Desktop Connector** (the secure-pairing/QR mechanism) — this connects a
   buyer or seller's *own* devices to their *own* Tally instance. It has no concept of a second
   company on the other end.
2. **Catalogue sharing** (§11 of the Catalogue architecture document) — generates a static snapshot
   file at share time, handed off through the OS share sheet (WhatsApp, etc.). Explicitly one-way,
   explicitly non-live: *"a previously-shared file does not update... exactly like an already-sent
   Ledger statement PDF."*

Neither is a live channel. **The WhatsApp-shared half of Q6/Q7 needs nothing new** — it is already
exactly shape (2), just applied to an Estimate/PO instead of a Ledger statement. **The in-app-submitted
half of Q6/Q7 — and everything downstream of it (seller inbox, Mutual Agreement, cross-confirmed
payment, "My Transactions" as a shared truth) — has no home in this architecture without a new
cross-company transport mechanism this document is explicitly not authorized to design** (that would
be inventing a cloud backend, a scope decision far larger than "Transaction Mode" and squarely a
product/infrastructure decision, not an architecture-hardening one). This is **the single most
important open question in this entire document** — see §16, Risk 1. Sections 3–12 below still
produce the requested data model (a reasonable data shape is valid and necessary regardless of which
device(s) eventually hold it), but every section touching the in-app path is written with this gap
named explicitly, not papered over.

### Finding 2: Transaction Mode's reminders and Catalogue's own reminders share an unbuilt prerequisite

Q14 requires a reminder ladder (pre-due, on-due, post-due, extended-post-due) that reaches a buyer
proactively. **This codebase has zero notification infrastructure of any kind** — confirmed this
session by an exhaustive grep of `apps/venture_android/app/src`: no `NotificationChannel`, no
`NotificationCompat`, no `NotificationManager`, no FCM, no `NotificationType` enum, and — despite
`WorkManager` being wired into `VentureApplication.kt` for Hilt DI reasons — **zero `Worker`/
`CoroutineWorker` implementations anywhere.** This is not unique to Transaction Mode: Catalogue's own
five locked notification types (Brainstorm Outcome §7, "New Tally item arrived as Draft," etc.) are
**documentation-only** — never implemented, despite Catalogue itself being code-complete
(`1b8aa90`, "MVP-1.4 COMPLETE"). See §7 below — this document recommends building generic
notification/scheduling infrastructure once, as a prerequisite shared by both features, rather than
Transaction Mode inventing its own.

### Finding 3: Q18's "selected price level" has no pricing concept to select from

Q4/Q18 both use the language "price level" / "Approved-buyer... visible once approved for pricing."
**Catalogue's actual, implemented pricing model has no tiers of any kind** — confirmed this session
directly against `CatalogueProductEntity`: `priceDisplayMode` is a two-value field, `"OPEN"` or
`"CONTACT_FOR_PRICE"`, full stop. The Brainstorm Outcome's own Catalogue precedent is explicit:
*"No pricing engine... tiered/quantity pricing... deferred to 1.4a"* — and 1.4a was never authorized.
There is no data structure anywhere for "a price level" to select. §10 below proposes the closest
faithful interpretation (the grant flips *display visibility*, not a price *tier*) and flags this
explicitly as a reinterpretation requiring product-owner confirmation, not a locked decision.

---

## 2. Grounding — current, directly-verified repository state

Re-confirmed this session by direct inspection, not carried from any prior document:

| Area | State |
|---|---|
| Transaction Mode / chat / Vartalap code | **None.** No module, table, or screen. "Vartalap" appears only as a doc-comment placeholder name in `BusinessProfileModels.kt` for a future milestone. |
| Catalogue | **Code-complete** (git log `1fe1ea7`…`10f0b9e`, closed by `1b8aa90` "MVP-1.4 COMPLETE"). This supersedes the earlier Catalogue architecture document's own "zero Catalogue code exists" framing (§2 of that document), which was true when written and is stale now. |
| Catalogue tables (actual, current) | `catalogue_product`, `catalogue_product_source_link`, `catalogue_branch`, `catalogue_settings` (the actual Public/Private toggle, `isPublic: Boolean`, defaults to Private when absent), `catalogue_override`, `catalogue_published_snapshot`, `catalogue_asset`, `catalogue_custom_field` — added `MIGRATION_10_11`/`11_12`/`12_13`. |
| Override resolution (actual) | `CatalogueOverrideResolver.resolve(rows, productId, branchId, stockGroupKey)` — pure function, Item → Branch → Stock-group → Catalogue-wide, first match wins. Wired via `CatalogueRepositoryImpl.resolveOverride()`. |
| Room schema version | `DatabaseConstants.VERSION = 13`, migrations `MIGRATION_1_2`…`MIGRATION_12_13` in `DatabaseModule.kt`. **Next available: `MIGRATION_13_14`.** |
| `PartyEntity` | `cached_parties`, PK `(companyId, partyId)`, field `classification: PartyClassification` (`Customer, Prospect, Supplier, Other`) — already mutable, no schema change needed to flip Prospect → Customer. |
| `PartySourceLinkEntity` | `party_source_links`, PK `(companyId, sourceType, externalEntityId)` — the exact "natural-key upsert, rename-survives" pattern every new source-link table in this codebase is expected to mirror (already mirrored once, faithfully, by `catalogue_product_source_link`). |
| `LedgerEntity` | `cached_ledgers`, PK `(companyId, id)` — no write path exists anywhere in this app; VENTURE only ever *exports* enrichment XML for a human to import into Tally (`PartyExportEventEntity` / `TallyLedgerXmlGenerator`). Confirmed: **no code path creates a Tally Ledger from VENTURE.** |
| `StockItemEntity` | `cached_stock_items`, PK `(companyId, id)`, 17 fields, verified exhaustive. |
| Notification/reminder infra | **None implemented.** `WorkManager` scaffolded (DI only), zero `Worker`s. Closest precedent: Dincharya's **pull-based**, read-time `dueAt` classification on `party_notes` (`MIGRATION_8_9`) — no push, no background job, surfaces only when the user opens the screen. |
| Cross-company transport | **None.** See Finding 1 above. |
| "Connect" (module name) | Not a buyer-seller link — it is this codebase's own name for the Party/Customer/Supplier management feature (`feature/connect/`), unrelated to inter-company connectivity. |

---

## 3. Relationship to the locked Catalogue architecture

Per Q1–5 (reused unchanged) and Q10 (Transaction Mode's own scope statement), this design:

- **Does not modify** `catalogue_product`, `catalogue_product_source_link`, `catalogue_branch`,
  `catalogue_settings`, `catalogue_override`, `catalogue_published_snapshot`, `catalogue_asset`,
  `catalogue_custom_field` — all read-only inputs to Transaction Mode.
- **Does not modify** `cached_parties`, `party_source_links`, `cached_ledgers`,
  `cached_stock_items` — all read-only inputs, per this task's own migration constraint (§13).
- **Adds** one new, entry-point-agnostic domain (Estimate/PO → seller inbox → Transaction lifecycle
  → payment tracking) plus one Catalogue-adjacent extension (the chat access grant, §10) — all in new
  tables, all `companyId`-first, matching the isolation convention audited in Catalogue architecture
  §13.
- **Entry-point-agnostic, as locked (Q10):** the generic Estimate/PO shape (§4) never references
  "Catalogue" in its own schema beyond an optional `linkedProductId` on each line item — a future
  chat-originated Purchase Query (Vartalap's original vision) would populate the same table with a
  `entryPointType = "CHAT"` row and `linkedProductId = NULL` line items (free-text description
  instead), with zero schema change. This is the concrete mechanism satisfying "not Catalogue-specific."

---

## 4. Data model — the generic Estimate/PO object (Q10)

### `txn_estimate_po` — the object itself

| Column | Type | Notes |
|---|---|---|
| `companyId` | TEXT | Seller's company — this table always lives in the seller's own Room database (or, if Finding 1's transport gap is later resolved by some shared/synced store, the seller's copy of it) |
| `estimatePoId` | TEXT | Generated, opaque |
| `entryPointType` | TEXT | `"CATALOGUE"` (only producer built now) — extensible for a future `"CHAT"` value, never a closed enum at the schema level (mirrors the Catalogue Excel contract's own "reserved-name list, not a fixed column count" extensibility discipline) |
| `submissionType` | TEXT | `"ESTIMATE"` \| `"PURCHASE_ORDER"` — Q6's explicit, deliberate choice |
| `deliveryChannel` | TEXT | `"WHATSAPP_SHARED"` \| `"IN_APP_SUBMITTED"` — Q6/Q7 |
| `buyerPartyId` | TEXT, nullable | References `cached_parties(companyId, partyId)` by convention (no Room `FK`, matching every existing source-link table's own no-FK discipline) — **required** for `IN_APP_SUBMITTED`, **nullable** for `WHATSAPP_SHARED` (a WhatsApp-shared estimate may go to a phone number with no corresponding Party record yet) |
| `totalAmount` / `currencyCode` | TEXT | `MoneyAmount`-style, matching Catalogue §12's own precedent — computed sum of line items at submission time |
| `submittedAt` / `submittedAtSource` | TEXT | Authoritative-timestamp discipline, per §15's already-established rule (see §9 below) |
| `status` | TEXT | `"SHARED"` (terminal, informational, `WHATSAPP_SHARED` only) \| `"SUBMITTED"` (awaiting seller action, `IN_APP_SUBMITTED` only) — this is deliberately **not** the same state vocabulary as the seller inbox (§5) or the Transaction (§6): the Estimate/PO's own status only ever answers "was this delivered," never "has the seller acted on it" |

PK `(companyId, estimatePoId)`.

### `txn_estimate_po_line_item` — snapshot, not a live reference

| Column | Type | Notes |
|---|---|---|
| `companyId`, `estimatePoId`, `lineItemId` | TEXT | PK |
| `linkedProductId` | TEXT, nullable | References `catalogue_product(companyId, productId)` by convention; **nullable** to keep the shape entry-point-agnostic (a future chat-originated line has no Catalogue product to link) |
| `snapshotProductName`, `snapshotUnit`, `snapshotSku` | TEXT | **Denormalized at submission time.** Catalogue content can change (or the product can be Archived, re-Drafted, or its source Stock Item can disappear) after submission — this line item must remain legible regardless. This directly mirrors the Catalogue architecture's own "current published snapshot... atomically replaced, never partially mutated" discipline (§7 of that document), applied here to a line item instead of a whole catalogue |
| `quantity` | TEXT | Decimal-safe string, not a float |
| `unitPriceAmount` / `unitPriceCurrencyCode` / `lineTotalAmount` | TEXT | `MoneyAmount`-style |

PK `(companyId, estimatePoId, lineItemId)`.

**What this references vs. what's new**, exactly as requested:

| Existing entity | How it's referenced | New? |
|---|---|---|
| `Party` (`cached_parties`) | `buyerPartyId`, by convention key, no FK | Referenced, not duplicated |
| `Ledger` (`cached_ledgers`) | Not referenced directly by the Estimate/PO at all — only indirectly, later, via §6's ledger-intent record | N/A here |
| `catalogue_product` | `linkedProductId`, by convention key, nullable | Referenced, not duplicated |
| `StockItem` | Never referenced directly — Catalogue already owns that indirection; Transaction Mode never reaches past Catalogue to Stock Items | N/A |
| Everything else in this section | — | **New**: `txn_estimate_po`, `txn_estimate_po_line_item` |

---

## 5. Seller inbox entity/schema (Q7)

**Only `IN_APP_SUBMITTED` Estimates/POs create an inbox entry** — `WHATSAPP_SHARED` ones never do,
per Q7's own explicit text ("simple message, no forced in-app tracking"). This is enforced at the
write boundary (the use-case that creates a `txn_seller_inbox_entry` refuses to run for a
`WHATSAPP_SHARED` `txn_estimate_po`), not merely a UI-layer convention — the same "structural, not
just a UI hint" discipline the Catalogue architecture applied to Private-catalogue share refusal (§18
of that document).

### `txn_seller_inbox_entry`

| Column | Type | Notes |
|---|---|---|
| `companyId` | TEXT | Seller's company — first key component, per §13's isolation convention |
| `inboxEntryId` | TEXT | Generated |
| `estimatePoId` | TEXT | 1:1 back-reference to `txn_estimate_po`; unique index `(companyId, estimatePoId)` — at most one inbox entry per submission |
| `state` | TEXT | `"NEW"` → `"ACKNOWLEDGED"` \| `"CHANGES_REQUESTED"` \| `"ACCEPTED"` → (terminal) `"CONVERTED"` — see transition table below |
| `acknowledgedAt` | TEXT, nullable | Set on Acknowledge |
| `changeRequestNote` | TEXT, nullable, bounded | Set on Request-changes; a factual, seller-authored note about what needs to change — **not** a rating/judgment field (§11) |
| `respondedAt` | TEXT, nullable | Set on whichever of Accept/Request-changes fires |
| `convertedTransactionId` | TEXT, nullable | Set exactly once, when Accept fires and creates a `commercial_transaction` (§6) — this is the concrete Convert-to-confirmed-order action |

PK `(companyId, inboxEntryId)`. Index `(companyId, state)` for the seller's urgency-sorted default
view (Q13).

**State transitions:**

| From | Action | To | Side effects |
|---|---|---|---|
| `NEW` | Acknowledge | `ACKNOWLEDGED` | none beyond the timestamp — a lightweight "seen" signal |
| `NEW` or `ACKNOWLEDGED` | Request changes | `CHANGES_REQUESTED` | `changeRequestNote` recorded; **no Estimate/PO mutation** — the buyer resubmits a new `txn_estimate_po` if they choose to revise, this entry stays as a closed record of the original ask (mirrors the Catalogue architecture's own "editing a Published product never mutates in place, always a new Draft" discipline, §7 of that document) |
| `NEW` or `ACKNOWLEDGED` | Accept | `ACCEPTED` → immediately `CONVERTED` | Atomically: (1) creates `commercial_transaction` (§6), (2) creates `txn_terms_acknowledgment` with seller-proposed terms (§8), (3) triggers the Prospect→Ledger flow (§6 below) if `buyerPartyId`'s classification is `Prospect`. `ACCEPTED` is modeled as instantaneous-then-`CONVERTED` rather than a durable intermediate state, because nothing in Q7/Q9/Q10 describes a seller "Accepting" without these consequences firing together — an `ACCEPTED`-but-not-`CONVERTED` state would be an unrequested extra state with no defined meaning |
| `CHANGES_REQUESTED` | (buyer resubmits) | — | out of this entry's lifecycle; a wholly new `txn_estimate_po` + `txn_seller_inbox_entry` pair, same as any other submission |

**Company scoping:** `companyId` is the seller's company throughout — the seller's own inbox, living
in the seller's own Room database, exactly matching the isolation convention audited in Catalogue
architecture §13 (composite PK, `companyId`-first, explicit parameter on every DAO method, no
structural enforcement beyond convention — the same residual, codebase-wide, pre-existing gap named
there applies unchanged here, not newly introduced by this design).

**Finding 1 reminder:** this entire section describes the *shape* of the seller's inbox. **How a
buyer's `IN_APP_SUBMITTED` Estimate/PO physically reaches the seller's device to populate this table
is unresolved** (§1 Finding 1, §16 Risk 1) — this schema is correct regardless of the eventual
transport, but the transport itself is not designed here.

---

## 6. Prospect → Ledger creation flow (Q9)

**Exact integration point:** the existing `PartyEntity.classification` field, already a mutable enum
including `Customer` and `Prospect` — confirmed this session, no schema change to `cached_parties`
required. The flow, triggered inside the same Accept transaction as §5:

1. Look up `buyerPartyId`'s current `PartyEntity.classification`.
2. If `Prospect`: update it to `Customer` via the **existing** `PartyRepository` (the same repository
   `feature/party/` already exposes — this document does not name a new repository method beyond a
   thin `promoteProspectToCustomer(companyId, partyId)`-shaped call, since **no such promotion path
   exists today** — confirmed this session by direct grep of `PartyRepositoryImpl.kt` for
   `promote|linkToLedger|convertTo`, zero matches). If already `Customer` (or `Supplier`/`Other`): no
   classification change, flow continues.
3. Seller's Debtor/Creditor choice (Q9: "Seller chooses Debtor or Creditor") is recorded in a **new**
   table, `txn_ledger_intent` — **deliberately not a new column on `PartyEntity`**, because this
   task's own migration constraint (§13) forbids touching existing tables, and because this value is
   metadata about a *future, human-mediated* Tally ledger creation, not a property of the Party
   itself.

### `txn_ledger_intent`

| Column | Type | Notes |
|---|---|---|
| `companyId`, `transactionId` | TEXT | PK — one intent record per Transaction (the Accept action that triggers this always has exactly one associated Transaction) |
| `buyerPartyId` | TEXT | Convention reference to `cached_parties` |
| `chosenLedgerGroup` | TEXT | `"DEBTOR"` \| `"CREDITOR"` |
| `promotedProspectAt` | TEXT, nullable | Set only if step 2 above actually flipped a classification; `NULL` if the Party was already `Customer` |
| `recordedAt` | TEXT | Authoritative timestamp |

**Zero parallel identity systems — verified explicitly, as required:**

- No new customer/contact/phone/address table is created. `buyerPartyId` is the **only** identity
  reference throughout this entire document (§4, §5, §6, §7, §9, §10) — every table above resolves
  back to the exact same `cached_parties` row.
- **No Tally write occurs.** Confirmed this session: VENTURE has no code path that creates a Tally
  Ledger — only the existing `PartyExportEventEntity`/`TallyLedgerXmlGenerator` pattern, which
  generates an XML file for a *human* to import into Tally themselves. `txn_ledger_intent`'s
  `chosenLedgerGroup` is designed to feed that **exact existing export path** (as an additional
  hint/default when the seller later chooses to export this Party's enrichment XML) — not a new write
  mechanism. This document does not extend `TallyLedgerXmlGenerator`'s actual behavior (that's
  implementation, not architecture) but confirms the integration point is that existing exporter, not
  a new one.
- `party_source_links` (the mechanism that would eventually attach a *real* Tally-synced Ledger GUID
  to this Party, once the seller has imported the XML into Tally and it syncs back) is **untouched and
  reused exactly as-is** — the existing `findByExternalKey`/`upsert` reconciliation path picks up the
  new Ledger on its next sync, with no Transaction-Mode-specific code involved at all. This is the
  same reconciliation path already used for every other Tally-sourced Party today.

---

## 7. Terms Acknowledgment data shape (Q11)

### `txn_terms_acknowledgment`

| Column | Type | Notes |
|---|---|---|
| `companyId`, `transactionId` | TEXT | PK — 1:1 with `commercial_transaction` |
| `paymentTiming` | TEXT | `"ADVANCE"` \| `"ON_DELIVERY"` \| `"CREDIT_X_DAYS"` \| `"PARTIAL"` — the three-field structure's first field |
| `creditDays` | INTEGER, nullable | Populated only when `paymentTiming = "CREDIT_X_DAYS"` |
| `partialAdvancePercent` / `partialBalanceTiming` | TEXT, nullable | Populated only when `paymentTiming = "PARTIAL"` |
| `amount` / `currencyCode` | TEXT | **Denormalized copy** of `commercial_transaction.totalAmount` at the moment terms are proposed — "auto-pulled from the accepted Estimate/PO, not re-typed" (Q11), so this is a copy for point-in-time integrity, never a second entry point for the figure |
| `note` | TEXT, nullable, **≤100 chars, enforced at the write boundary** | The bounded, optional field Q11 specifies |
| `proposedAt` | TEXT | Authoritative timestamp — terms are seller-proposed, set as part of the same Accept transaction as §5/§6 |
| `buyerConfirmedAt` | TEXT, nullable | Set when the buyer taps to confirm |
| `sellerConfirmedAt` | TEXT, nullable | Set when the seller taps to confirm — note the seller *proposed* the terms but still explicitly confirms them, symmetrically with the buyer, matching Q12's "fires identically for both parties, order-independent" |

**"Not a legal contract" — enforced at the data/UI level, not just in copy:**

1. **No signature/attestation field of any kind** — no "I agree to be legally bound" checkbox, no
   e-signature capture, no name-typed-as-signature field. The schema physically has nowhere to put
   one; adding one later would be a deliberate, visible migration, not a silent addition.
2. **No document-generation path.** Unlike Catalogue's sharing model (§11 of that document, which
   deliberately generates a PDF-equivalent share file), Terms Acknowledgment has **no analogous
   "generate a document" action anywhere in this design** — there is no `TermsDocument`/`Contract`
   table, no PDF export. A printed/exportable "contract-looking" artifact is exactly the failure mode
   Q11 and the legal-review prerequisite (Brainstorm Outcome §8) are guarding against; this document
   does not introduce one.
3. **Naming discipline:** every identifier in this table and its surrounding code is
   `TermsAcknowledgment`/`termsAcknowledgment`, never `Contract`/`Agreement` — a small thing, but
   consistent, deliberate naming is cheap insurance against the concept drifting in implementation
   (the same discipline this codebase already applies to keeping "Prospect" and "Ledger" from blurring
   pre-Q9).
4. **`note` is capped and free-text only** — 100 characters is too short to constitute contractual
   language, and the field carries no structured "terms and conditions" sub-schema.
5. The regulatory question underneath this ("does structured Terms Acknowledgment edge into
   regulated financial/contractual territory") is the Brainstorm Outcome's own §8 prerequisite and
   **remains genuinely open** — this document enforces the *product's stated intent* (non-legal,
   factual-record-of-intent) at the schema level; it cannot and does not resolve the underlying legal
   question, which requires the formal legal review already named as a hard prerequisite.

---

## 8. Transaction state machine (Q12–13, Q16–17)

### `commercial_transaction`

(Named `commercial_transaction`, not `transaction` — `TRANSACTION` is a reserved word in SQLite,
used by `BEGIN TRANSACTION`; Room can quote around it, but avoiding the collision entirely is simpler
and matches this codebase's practice of clear, unambiguous table names.)

| Column | Type | Notes |
|---|---|---|
| `companyId`, `transactionId` | TEXT | PK |
| `estimatePoId` | TEXT | The accepted source document (§4), by convention reference |
| `buyerPartyId` | TEXT | Convention reference; indexed `(companyId, buyerPartyId)` for the per-counterparty "My Transactions" view (Q13) |
| `state` | TEXT | `"PENDING_CONFIRMATION"` → `"AGREED"` → `"PAYMENT_INITIATED"` → `"PAYMENT_CONFIRMED"` → `"COMPLETED"` |
| `totalAmount` / `currencyCode` | TEXT | Copied from the accepted `txn_estimate_po` at creation time |
| `acceptedAt` | TEXT | Set at creation (the seller's Accept action, §5) |
| `completedAt` | TEXT, nullable | Set automatically, never by a manual action (Q17) |

PK `(companyId, transactionId)`.

### Persisted vs. derived — explicit, as requested

| Concept | Persisted where | Derived how |
|---|---|---|
| `PENDING_CONFIRMATION` → `AGREED` | Not a separate flag — **derived** from `txn_terms_acknowledgment.buyerConfirmedAt` and `.sellerConfirmedAt` both being non-null. `commercial_transaction.state` is updated to `"AGREED"` as a side effect the moment the second of the two timestamps is written (whichever party taps second) — but the *authority* for "are we agreed" is always the two timestamp columns, never a boolean that could drift out of sync with them |
| Q12's asymmetric "waiting for [counterparty]" state | **Fully derived**, never persisted as its own field. If `buyerConfirmedAt` is set and `sellerConfirmedAt` is null (or vice versa), the UI computes "waiting for the other party" directly from which of the two columns is null. There is exactly one persisted fact (which timestamp, if either, is set) and the "waiting" framing is a read-time interpretation of it — this is what keeps Q12's "never silence" requirement honest: the state a party sees is always a direct function of real, persisted confirmation facts, never a separately-maintained UI flag that could disagree with them |
| `PAYMENT_INITIATED` | **Derived** — true once at least one `txn_payment_event` row exists with `buyerClaimStatus IN ("INITIATED", "PAID")` for this transaction. No separate flag |
| `PAYMENT_CONFIRMED` | **Derived** — true once the sum of `sellerConfirmed = true` payment events' `buyerClaimedAmount` reaches `commercial_transaction.totalAmount` |
| `COMPLETED` / `completedAt` | **Persisted**, set exactly once, automatically, the instant `PAYMENT_CONFIRMED`'s condition first becomes true (Q17: "fires when seller confirms receipt of the full/final agreed amount... not a manual button") |

**Implementation-confirmed addendum (Phase 64, `docs/status/VENTURE-DEVELOPMENT-LEDGER.md` §55):**
building this derivation revealed that `PAYMENT_CONFIRMED` and `COMPLETED` share the identical
trigger condition (full seller-confirmed amount), so under Q17's own "completion is automatic, fires
the instant..." rule, a transaction never actually rests in a persisted `PAYMENT_CONFIRMED` state —
the moment that condition becomes true, `COMPLETED` fires in the same step. `PAYMENT_CONFIRMED`
remains a real, correct member of the state vocabulary (matching Q13's locked five-state list, and
meaningful for a future manual-completion or delayed-completion variant if one is ever authorized),
but implementers should not expect to ever observe a transaction's persisted `state` column actually
equal to `PAYMENT_CONFIRMED` under the current locked automatic-completion rule. This was not
anticipated when this document was first written; it is a genuine refinement discovered by writing
the actual derivation logic and its tests, not a design defect.
| **Overdue** | **Never a state value, always a derived modifier**, exactly as the task requires. Computed at read time by comparing "now" against a due date resolved from `txn_terms_acknowledgment.paymentTiming` + `commercial_transaction.acceptedAt` (see the open question on `ON_DELIVERY`, §16 Risk 6) — the same read-time classification pattern already proven by Dincharya's `classifyUrgency` (§2). A transaction can be simultaneously `state = "AGREED"` (or `"PAYMENT_INITIATED"`) **and** Overdue; Overdue never appears in the `state` column itself |

### Q13 — "My Transactions": one shared model, two views

- **One table** (`commercial_transaction`, joined to `txn_terms_acknowledgment` and
  `txn_payment_event`), **two query shapes** — no duplicated storage:
  - **Seller default view:** `ORDER BY` a computed urgency rank (overdue first → due-soonest →
    `state = "PENDING_CONFIRMATION"` awaiting seller confirmation) — a query-time `ORDER BY`
    expression, not a stored rank column (a stored rank would need constant re-computation as "now"
    moves, which is exactly the class of staleness bug this codebase's TD-037 already named and fixed
    once for a different cache — no reason to reintroduce it here).
  - **Buyer default view:** simple `ORDER BY` next-due-date ascending.
- **Per-counterparty window** (Q13's "shared, mutually-visible truth"): `WHERE companyId = :sellerCo
  AND buyerPartyId = :buyer`, returning every `commercial_transaction` for that pair, their running
  balance (sum of confirmed payments vs. total), and full history — a query, not a separate
  materialized table.
- **Explicitly not an accounting/tax-record generator**, exactly as locked: no GST/tax field anywhere
  in this schema, no debit/credit ledger format (this is a status/commitment record, not
  `cached_ledgers`), no invoice-numbering sequence. `estimatePoId`/`transactionId` are opaque
  identifiers, never presented as an invoice number.

### Q16 — payment events

### `txn_payment_event`

| Column | Type | Notes |
|---|---|---|
| `companyId`, `transactionId`, `paymentEventId` | TEXT | PK |
| `installmentSequence` | INTEGER | 1, 2, 3… — supports Q16's "Installment N Paid" |
| `buyerClaimStatus` | TEXT | `"INITIATED"` \| `"PAID"` |
| `buyerClaimedAmount` / `currencyCode` | TEXT | |
| `buyerClaimedAt` | TEXT | |
| `sellerConfirmed` | INTEGER (bool) | Default `false` — **never silently upgraded**, per Q16's explicit rule |
| `sellerConfirmedAt` | TEXT, nullable | |
| `sellerDiscrepancyNote` | TEXT, nullable, bounded | **Factual only** — see §11; this field carries no severity/rating/judgment semantics and is never aggregated across transactions or written back onto `PartyEntity` |

**No money is ever processed by VENTURE** — there is no payment-gateway integration, no card/UPI
field, nothing beyond these status/claim/confirm columns. This is a direct, structural reflection of
Q16's own explicit statement, not merely a policy choice enforced by convention.

### Q17 — completion + reorder

Completion is the automatic side effect described above (persisted `completedAt`, never a button).
The reorder prompt itself (Q17: "gentle, dismissible... routes buyer back into Catalogue via the
existing Buy Again flow") needs **no new data model** — it reads `commercial_transaction` (now
`COMPLETED`) plus its line items (§4) and hands off to Catalogue's already-locked Buy Again mechanism
(Q3), which this document does not touch or duplicate.

---

## 9. Timestamp integrity — inherited, not reinvented

Per the already-locked rule (Catalogue architecture §15, decision-lock pass 2026-08-24): conflict/
publication-adjacent decisions must use an authoritative timestamp, not a bare device clock. Every
`...At`/`...AtSource` pair in this document's schema (§4–8) follows the exact same two-column
convention already used throughout `catalogue_*` tables. **This document inherits, not resolves,**
that document's own still-open edge case (its §15, §22 item 2: no authoritative timestamp exists for
an *offline* edit) — and Transaction Mode's live, two-party confirmation flow (Q12) makes that edge
case sharper, not simpler: an offline buyer tapping "confirm" has no more of an authoritative clock
available than an offline Catalogue edit does. Not re-solved here; carried into §16 as a shared open
question.

---

## 10. Chat-based Catalogue access grant (Q18)

### `catalogue_access_grant`

| Column | Type | Notes |
|---|---|---|
| `companyId` | TEXT | Seller's company |
| `grantId` | TEXT | Generated |
| `buyerPartyId` | TEXT | Convention reference to `cached_parties` |
| `priceVisibility` | TEXT | See interpretation note below — pragmatically single-valued today |
| `grantedAt` | TEXT | |
| `expiresAt` | TEXT, nullable | `NULL` = "Always" (Q18 explicitly allows this as an option) |
| `revokedAt` | TEXT, nullable | Manual early revoke (Q18) |

PK `(companyId, grantId)`. Index `(companyId, buyerPartyId)` — the active grant for a buyer is the
latest row (by `grantedAt`) with `revokedAt IS NULL` and (`expiresAt IS NULL OR expiresAt > now`).

**Expiry enforcement: checked-on-read, not a scheduled job — deliberately, given the codebase's actual
state.** Finding 2 (§1) already established there is no scheduler/WorkManager job infrastructure in
this app today. Building one solely to flip an `isActive` flag at the exact expiry instant would be
new infrastructure for a requirement Q18 doesn't actually need met to the second — "automatically
reverts to greyed-out, no manual seller action needed" (Q18) is fully satisfied by evaluating
`expiresAt` at the moment the chat/catalogue-access UI renders, exactly the same discipline Dincharya
already uses for `dueAt` (§2). This is the cheaper, already-proven pattern, not a shortcut: no new
mechanism needs inventing, and behavior is indistinguishable to the user (they only ever look at the
icon while the app is open).

**Integration point with Catalogue's existing Public/Private toggle and price-display override
chain:**

- `catalogue_settings.isPublic` (catalogue-wide) and `catalogue_override` (Item → Branch →
  Stock-group → Catalogue-wide, generic `attributeName`) together resolve to a price-display state
  that is **the same for every viewer** — there is no "viewer" dimension in that chain at all; it
  resolves purely from `(companyId, productId, branchId?)`.
- `catalogue_access_grant` is **not a fifth level of that chain** — it cannot be, because the chain's
  levels are all *product/branch* scoped and this grant is *buyer* scoped, an orthogonal axis. It is
  instead a **read-time override applied alongside** the existing resolution: if a product's own
  override-chain resolution yields `"CONTACT_FOR_PRICE"`, but a valid `catalogue_access_grant` exists
  for the current viewing buyer, the buyer sees the price anyway. Sellers/owners viewing their own
  catalogue are entirely unaffected — this check only ever applies on a buyer-facing read path.
- This is exactly the concrete mechanism Q18 itself claims to be: "the concrete mechanism fulfilling
  the 'Approved-buyer' pricing tier from Q4" — confirmed consistent with that framing.

**Finding 3 reminder — the "price level" interpretation gap, stated plainly:** Q4 names three price
states (Public, Approved-buyer, Private/"Contact for price"); Q18 says the seller picks "a selected
price level" when granting. **The current, actually-implemented Catalogue pricing model has exactly
two display states, not three, and zero tiers** (`priceDisplayMode: "OPEN" | "CONTACT_FOR_PRICE"`).
This document's `priceVisibility` column is therefore modeled as **effectively binary today** — a
grant either exists (buyer sees the resolved price, as if it were `"OPEN"` for them specifically) or
it doesn't (buyer sees whatever the normal chain resolves to, likely `"CONTACT_FOR_PRICE"`). The
column is typed as `TEXT` rather than `BOOLEAN` **only** so that a genuine future tiered-pricing model
(if 1.4a is ever authorized) could populate it with real tier identifiers without a schema change —
but no such tier concept exists to select from today, and this document does **not** invent one. This
is flagged as an open interpretation requiring product-owner confirmation, not a locked design (§16).

---

## 11. Explicit non-scope — the Section 4 exclusion, verified

**Verified directly against every table in §4–10 above: nothing in this design represents rating,
flagging, or any counterparty-characterizing feature.** Specifically checked, per the task's own
instruction to look for accidental hooks:

| Table | Field that could tempt a hook | Why it doesn't become one |
|---|---|---|
| `txn_seller_inbox_entry.changeRequestNote` | Free text about a specific submission | Scoped to one `estimatePoId`, never aggregated, never rolled up onto `PartyEntity` or any cross-transaction record |
| `txn_payment_event.sellerDiscrepancyNote` | Free text about a payment disagreement | Same discipline — scoped to one payment event on one transaction, no severity/score field alongside it, never written back to `PartyEntity`, never counted/summed anywhere in this schema |
| `commercial_transaction` / Overdue (derived) | Could tempt a "days overdue" counter that silently becomes a reliability signal | Explicitly **not stored** — computed fresh at read time from raw dates, per-transaction, never accumulated into a per-Party counter (no `latePaymentCount`, no `overdueTransactionTotal`, nothing of that shape exists anywhere in §4–10) |
| `PartyEntity` (existing, untouched) | Any new column here would be the most dangerous possible hook — a permanent, cross-transaction, per-counterparty field | **Confirmed untouched.** No column is added to `cached_parties` by this design (§13) — this is the single most important thing to verify, since a rating/reliability system's defining trait is exactly "a persistent property of the counterparty," and `PartyEntity` is the only table in this entire design that persists across every transaction with that counterparty |
| `catalogue_access_grant` | Could be mistaken for a trust/approval signal about the buyer | It is explicitly scoped to price *visibility*, time-boxed, seller-revocable at will, and carries no numeric/qualitative judgment field — granting or not granting is a pricing-display decision, not a stored opinion about the buyer |

**No aggregate, cross-transaction, per-counterparty field of any kind exists anywhere in this
document's schema.** Every fact this design persists is scoped to one Estimate/PO, one inbox entry,
one Transaction, or one payment event — never summed, counted, or averaged into anything that
survives past that single record. This satisfies Q15/§4/§8's deferral as a structural property of the
schema, not merely a promise kept by discipline in application code — matching the "no overclaiming"
standard this project already holds itself to elsewhere (do not claim a guarantee the design doesn't
actually provide; here, the guarantee genuinely is structural, since there is simply no column to
misuse).

---

## 12. Company isolation model

Every new table above follows the audited pattern (Catalogue architecture §13) exactly: composite
primary key with `companyId` first, `companyId`-prefixed indices, `companyId` as an explicit mandatory
parameter on every DAO method.

| Entity | Key |
|---|---|
| `txn_estimate_po` | `(companyId, estimatePoId)` |
| `txn_estimate_po_line_item` | `(companyId, estimatePoId, lineItemId)` |
| `txn_seller_inbox_entry` | `(companyId, inboxEntryId)`, unique `(companyId, estimatePoId)` |
| `commercial_transaction` | `(companyId, transactionId)` |
| `txn_terms_acknowledgment` | `(companyId, transactionId)` |
| `txn_payment_event` | `(companyId, transactionId, paymentEventId)` |
| `txn_ledger_intent` | `(companyId, transactionId)` |
| `catalogue_access_grant` | `(companyId, grantId)` |

**`companyId` alone is insufficient in two places, named explicitly rather than glossed over** (same
discipline as Catalogue architecture §13's own equivalent list):

- **`txn_seller_inbox_entry`** — `companyId` alone does not prevent two different `estimatePoId`s
  from both claiming an inbox slot; the unique index on `(companyId, estimatePoId)` is required, not
  optional.
- **`catalogue_access_grant`** — `companyId` alone does not disambiguate "the currently active grant"
  from historical (expired/revoked) ones for the same buyer; every read must apply the full
  `revokedAt IS NULL AND (expiresAt IS NULL OR expiresAt > now)` predicate, never `companyId` +
  `buyerPartyId` alone.

**Inherited, not new, residual gap:** exactly as Catalogue architecture §13 already names,
`companyId` scoping in this codebase is convention-enforced at every call site, not structurally
guaranteed by any wrapper/interceptor. Every new DAO method in this design inherits that same
residual risk — named here so implementation-phase review checks it explicitly, same standing
practice.

**Which `companyId` does "seller" vs. "buyer" actually mean here — flagged, not glossed over:** every
table above is written from the **seller's** perspective — `companyId` is always the seller's
company. A buyer using their own VENTURE installation would need their own local copy of (at minimum)
`commercial_transaction`, `txn_terms_acknowledgment`, and `txn_payment_event` rows, keyed under
**their own** `companyId`, for their own "My Transactions" view to work locally. This document does
not resolve how the buyer's copy and the seller's copy of the same logical Transaction stay
consistent — that is Finding 1 (§1) again, restated at the schema level: two independent per-company
databases, no synchronization mechanism between them.

---

## 13. Reminder scheduling mechanism (Q14)

As established in Finding 2 (§1): **no existing sync/notification infrastructure exists to plug
into.** This section states what would actually need to be built, and recommends against Transaction
Mode building its own narrow version of it.

- **What Q14 needs:** a daily-cadence background check that, for every non-`COMPLETED`
  `commercial_transaction`, compares its derived due date (§8) against "now" and — if it crosses one
  of the four ladder thresholds (T-2/3 days, on-due, T+3-7, T+14) — surfaces a **fixed, system-authored**
  notification (never seller-customizable wording, per Q14's own lock).
- **What exists to build this from:** nothing directly. `WorkManager` is present only as unused DI
  scaffolding. The nearest *pattern* (not infrastructure) is Dincharya's pull-based `dueAt` query —
  reusable as a query shape, not as a scheduling mechanism, since it never fires anything, it just
  answers "what's due" when asked.
- **Recommendation — build once, shared:** rather than Transaction Mode inventing its own
  push-notification pipeline, this document recommends the eventual implementation build one generic
  `NotificationType`-driven WorkManager `PeriodicWorkRequest` + `NotificationChannel` mechanism that
  serves **both** this feature's Q14 ladder **and** Catalogue's own five already-locked-but-unbuilt
  notification types (Brainstorm Outcome §7) — since both are otherwise about to independently invent
  the same missing plumbing. This is a recommendation for implementation planning to weigh, not a
  decision this document locks.
- **Cheaper interim option, named for completeness:** ship Transaction Mode's V1 reminders as
  **pull-based only** (surfaced on "My Transactions"/a Dincharya-adjacent worklist screen at read
  time, exactly like Dincharya's own `dueAt` classification), deferring true proactive push delivery
  until the shared notification infrastructure above is built anyway. This satisfies Q14's *content*
  ladder without requiring new background-scheduling infrastructure first — at the cost of the
  reminder only being "delivered" when the buyer happens to open the app, which is a real, material
  weakening of Q14's intent ("proactive… defuse escalation before it starts") and should be named to
  the product owner as a real trade-off, not silently accepted.
- **The buyer's proactive "I'll pay late, here's when" action (Q14)** needs no new infrastructure
  beyond a write to `txn_payment_event`-adjacent state or a small note on the relevant
  `commercial_transaction` — this document does not add a dedicated table for it, since it is
  functionally identical in shape to an early, informational `txn_payment_event` row
  (`buyerClaimStatus` could be extended with a `"DELAY_ACKNOWLEDGED"` value, or a sibling table added
  at implementation time) — flagged as an implementation-detail choice, not resolved further here.

---

## 14. Migration plan (additive-only)

**No existing table is modified.** Confirmed against `cached_parties`, `cached_ledgers`,
`cached_stock_items`, `catalogue_product`, `catalogue_product_source_link`, `catalogue_branch`,
`catalogue_settings`, `catalogue_override`, `catalogue_published_snapshot`, `catalogue_asset`,
`catalogue_custom_field`, `business_profile` — none gain a column, none change type, none change key.

- **Next migration:** `MIGRATION_13_14`, `DatabaseConstants.VERSION` 13 → 14 — following the exact
  precedent `MIGRATION_10_11` set (multiple `CREATE TABLE IF NOT EXISTS` statements bundled into one
  migration for one cohesive feature landing together).
- **Tables added, in one migration:** `txn_estimate_po`, `txn_estimate_po_line_item`,
  `txn_seller_inbox_entry`, `commercial_transaction`, `txn_terms_acknowledgment`,
  `txn_payment_event`, `txn_ledger_intent`, `catalogue_access_grant` — eight tables, mirroring
  Catalogue's own seven-table single-migration precedent in scale.
- **Runtime writes to existing tables are expected and fine, schema changes are not:** flipping
  `PartyEntity.classification` from `Prospect` to `Customer` (§6) is a normal `UPDATE` through the
  existing `PartyRepository`, using a column that already exists and is already mutable — this is
  "reusing the foundation," not a migration, and is explicitly distinct from adding a column.
- **Backward compatibility:** trivial, same reasoning as Catalogue architecture §19 — every new table
  starts empty; an install with no Transaction Mode activity is unaffected.
- **No Connector/Desktop change of any kind** — every new table is Android/Room-local, matching
  Catalogue's own "no Desktop/Connector surface" precedent (PDL-020 §7) and this design's own Finding
  1 (there is nowhere on the Connector for this to usefully live — the Connector's role is Tally
  extraction, not buyer-seller messaging).
- **No new Tally XML/TDL request shape** — this design never talks to Tally at all beyond the
  already-existing, unmodified enrichment-XML export path (§6).

---

## 15. Test strategy outline

Matching the Catalogue precedent's coverage shape (that document's §20), scoped to what this design
actually introduces:

| Layer | Coverage |
|---|---|
| **Unit** | State-derivation correctness (§8): `AGREED` fires exactly when both confirmation timestamps are set, order-independent; `PAYMENT_CONFIRMED`/`COMPLETED` fire exactly at full-amount cumulative confirmation, not before, not after partial confirmation; Overdue classification at every ladder threshold boundary (Q14), off-by-one-day edge cases |
| **Repository** | Seller-inbox state-transition guards (§5) — Accept's atomic three-part side effect (Transaction + Terms + Ledger-intent) either fully commits or fully fails, never partially (mirrors this codebase's "fail honestly, never partially" convention already proven for Catalogue publish failure) |
| **Company isolation** | Adversarial two-company suite on every new table (§12), per this codebase's standing convention — explicit test that a `commercial_transaction` for company A's `buyerPartyId` can never be read/written under company B's `companyId` |
| **Prospect → Ledger** | Classification flip is idempotent (Accept on an already-`Customer` buyer is a no-op for classification, not an error); `txn_ledger_intent` never mutates `PartyEntity` beyond that single, existing, already-mutable field |
| **Terms Acknowledgment** | `note` length enforcement at the boundary (101st character rejected, not silently truncated); `amount` always matches the source Transaction's total at proposal time, never independently editable |
| **Payment cross-confirmation** | Buyer-claimed-but-unconfirmed state is visibly distinguished (Q16) in every query that surfaces payment status — an explicit "claimed, awaiting confirmation" test, not inferable only by absence of a confirmed flag |
| **Concurrent-access edge cases** | Two near-simultaneous confirmations (buyer and seller tap "confirm" within the same second) — both writes must land, `AGREED` must derive correctly regardless of write order; two near-simultaneous payment-claim events on the same transaction (race on cumulative-total-reached check for `COMPLETED`) — needs the same authoritative-timestamp resolution named as unresolved in §9 before this can be written meaningfully for the fully-offline case, exactly as the Catalogue precedent already flagged for its own concurrency tests |
| **Access grant (Q18)** | Expiry-at-read-time correctness (a grant with `expiresAt` in the past never shows as active, with no reliance on any background job); revoke-then-immediately-check reflects instantly; "Always" (`expiresAt = NULL`) never expires |
| **Non-scope adversarial (§11)** | An explicit test asserting no code path anywhere writes to `PartyEntity` from any Transaction Mode use case — a regression guard specifically for the "no rating hook leaks in" guarantee, since that guarantee is claimed as structural and should be enforced by a real, permanent test, not just design-time inspection |
| **Excel/Sharing interaction** | None required — this design does not touch Catalogue's Excel or sharing surfaces at all |
| **Offline behavior** | Explicitly deferred pending §9's inherited open timestamp question, same status as the Catalogue precedent's own equivalent item |

**Real-device validation requirement, inherited unchanged:** any test touching Party classification
flips or the enrichment-XML export integration point (§6) must be confirmed against a real
Tally-paired company before release, matching this project's standing practice.

---

## 16. Open questions and risks — named honestly, not resolved by inventing around them

Matching the Catalogue precedent's own standard (§22 of that document named two genuinely unresolved
items rather than papering over them) — this section names its own list at the same standard.

1. **(Carried from Finding 1, restated as the top risk) No cross-company transport exists for the
   in-app half of Transaction Mode.** This is not a data-model gap this document can close — it is a
   feasibility question about whether "in-app submission," a live seller inbox, Mutual Agreement, and
   cross-confirmed payment can be built at all inside a LAN-local, no-cloud-backend architecture, or
   whether Transaction Mode's in-app path requires a genuinely new infrastructure decision (a cloud
   service, most plausibly) that is far outside this document's scope to make. **Recommendation:**
   before any implementation authorization, the product owner should be asked explicitly whether
   Transaction Mode's in-app path is expected to require new cloud infrastructure, or whether the
   WhatsApp-only path (which needs nothing new) is the actually-intended MVP scope with "in-app
   submission" deferred until a transport exists. This document takes no position on which — it only
   confirms the current architecture cannot support the in-app path as specified.
2. **(Finding 2) No notification/scheduling infrastructure exists for Q14's reminder ladder**, and
   Catalogue's own five notification types share the identical gap. Recommend resolving once, shared,
   not twice — see §13.
3. **(Finding 3) Q18's "price level" has no corresponding tier concept in Catalogue's actual,
   implemented pricing model** (Open / Contact-for-price only, no tiers). §10's `priceVisibility`
   column is a reasonable-effort binary interpretation, flagged as requiring explicit product-owner
   confirmation before implementation, not a locked design.
4. **Q12's named, unresolved risk is carried forward unchanged, and this document adds no design for
   it:** a party can indefinitely stall Mutual Agreement by simply not tapping, "used as informal
   leverage" (Brainstorm Outcome's own words). A soft-nudge mechanism was suggested there, not
   designed there, and is not designed here either — it would need Q14's own not-yet-existing
   reminder infrastructure (§13) to be meaningful, so it is naturally sequenced after that
   infrastructure exists, not before.
5. **(Inherited from Catalogue architecture §15/§22) Offline-editing timestamp authority remains
   unresolved**, and Transaction Mode's live two-party confirmation flow (Q12) is arguably a harder
   version of the same problem than any Catalogue edit is — a stalled/offline confirmation has no
   authoritative clock to stamp itself with any more than an offline Catalogue Draft edit does.
6. **`ON_DELIVERY` payment timing has no computable due date.** `paymentTiming = "ON_DELIVERY"`
   (§7/§8) has no "delivery occurred" event tracked anywhere in this design or in Q1–18 — Transaction
   Mode has no delivery-tracking concept at all. This means Overdue classification (§8) and the
   reminder ladder (§14) cannot function for `ON_DELIVERY` transactions without either (a) inventing a
   delivery-confirmation event (new scope, not requested), or (b) treating `ON_DELIVERY` transactions
   as permanently exempt from due-date-based reminders/Overdue until some other event marks them
   payable — flagged as genuinely unresolved, not decided here.
7. **Buyer-side data ownership when the buyer has no VENTURE installation at all.** Every table in this
   document assumes `buyerPartyId` resolves to a `PartyEntity` row in the *seller's* database — true
   for both delivery channels. But Q13's "per-counterparty transaction window shows shared,
   mutually-visible truth" implies the buyer can also *see* this data somewhere. If the buyer has no
   VENTURE installation (plausible for many small buyers, especially via the WhatsApp-shared path),
   there is no "buyer's own view" at all — the only "My Transactions" that can exist is the seller's.
   This is consistent with the WhatsApp-only path being fully buildable today (§1), but it means Q13's
   "buyer: simple upcoming-due tracking" view is implicitly scoped to buyers who **do** run VENTURE —
   narrower than Q13's own text suggests, and worth confirming explicitly with the product owner.
8. **Scope-creep discipline, carried forward unchanged from every prior brainstorm-outcome document in
   this project:** this document's own data model is forward-compatible *design* only: implementation
   of Section 4's deferred items (rating/flagging) remains blocked on formal legal review (Brainstorm
   Outcome §8), unchanged by anything in this pass.

---

## 17. Ordered milestone breakdown (not built now)

Dependency-ordered, mirroring the Catalogue precedent's own milestone-numbering discipline (not the
retired 1.4a/b/c-style labels). **None of this is authorized to begin** — this is a breakdown for
whenever implementation is separately authorized, per this task's own constraint.

**Milestone 0 — Resolve Finding 1 (transport) and Finding 2 (notifications) at the product/
infrastructure level.** *Objective:* get an explicit product-owner decision on whether the in-app path
requires new cloud infrastructure or is deferred, and whether shared notification infrastructure is
built now (serving both Transaction Mode and Catalogue) or deferred. *Dependencies:* none — this is a
decision gate, not code. *Stop condition:* no Transaction Mode code should be written that assumes an
answer to either question that hasn't actually been given.

**Milestone 1 — Data foundation.** *Objective:* `txn_estimate_po`, `txn_estimate_po_line_item`,
`MIGRATION_13_14`. *Dependencies:* none (Catalogue and Party/Ledger foundations already exist).
*Tests:* repository + company-isolation adversarial (§15).

**Milestone 2 — WhatsApp-shared path only.** *Objective:* Q6's WhatsApp branch end-to-end — this is
the one full slice buildable today with zero transport-layer risk, reusing Catalogue's existing
`Intent.ACTION_SEND` sharing pattern applied to an Estimate/PO instead of a catalogue snapshot.
*Dependencies:* Milestone 1. *Acceptance:* a buyer-visible Estimate/PO can be generated and shared, no
seller-inbox involvement, matching Q7's "no forced in-app tracking."

**Milestone 3 — Seller inbox + Prospect→Ledger + Accept side effects (in-app path, transport
still unresolved).** *Objective:* §5/§6 schema and transition logic. *Dependencies:* Milestone 1,
Milestone 0's transport decision (building this without that decision risks building against an
assumption that turns out wrong). *Tests:* state-transition suite, Prospect→Ledger idempotency (§15).

**Milestone 4 — Terms Acknowledgment + Transaction state machine.** *Objective:* §7/§8 schema,
derivation logic for `AGREED`/`PAYMENT_INITIATED`/`PAYMENT_CONFIRMED`/`COMPLETED`, Overdue derivation.
*Dependencies:* Milestone 3. *Tests:* full state-derivation matrix, concurrency edge cases (§15,
pending §9's timestamp resolution for the offline case).

**Milestone 5 — Payment events + cross-confirmation.** *Objective:* §8's `txn_payment_event` table
and cross-confirmation UI/logic. *Dependencies:* Milestone 4.

**Milestone 6 — Reminder infrastructure (shared with Catalogue, if Milestone 0 chose to build it) or
pull-based interim (§13).** *Dependencies:* Milestone 4 (needs a due date to compute from), Milestone
0's decision.

**Milestone 7 — Chat-based Catalogue access grant.** *Objective:* §10's `catalogue_access_grant`
table and read-time integration with Catalogue's override chain. *Dependencies:* Milestone 0
(the grant is presented *in chat*, per Q18's own text — if no chat UI surface exists yet because
Milestone 0 deferred the transport question, this milestone's UI half is blocked even though its data
model half is not).

**Milestone 8 — Integration + hardening.** *Objective:* full cross-cutting adversarial pass (company
isolation across every new table simultaneously, the §11 non-scope regression test, real-device
validation of the Prospect→Ledger/enrichment-XML integration point). *Dependencies:* all prior
milestones.

---

## 18. Claude autonomy boundary (carried forward, unchanged in spirit)

Once implementation is separately authorized, the implementing session must not: invent a cross-company
transport mechanism or cloud backend to close Finding 1 without an explicit, separate product/
infrastructure decision; build any part of Brainstorm Outcome §4 (rating/flagging/characterization)
without the named legal review completing first; add a column to `PartyEntity`, `LedgerEntity`,
`StockItemEntity`, or any existing `catalogue_*` table; introduce a new Tally write path of any kind;
treat Q18's `priceVisibility` as a real tiered-pricing feature without separate product-owner
confirmation (§16 item 3); build a document/PDF/signature-generation path for Terms Acknowledgment
under any framing; or reopen any of Q1–5 (already locked, unchanged, reused). If a genuine new
architectural ambiguity appears mid-implementation that this document didn't anticipate, the correct
response is the same one this document itself followed: stop, record the ambiguity precisely, and do
not guess.

---

## 19. Source documents this pass reconciled

`VENTURE-VARTALAP-TRANSACTION-MODE-BRAINSTORM-OUTCOME.md` (supplied directly this session — not
present in the repository; see the status note at the top of this document),
`docs/architecture/VENTURE-MVP-1_4-CATALOGUE-BRAINSTORM-OUTCOME.md`,
`docs/architecture/VENTURE-MVP-1-4-CATALOGUE-ARCHITECTURE.md` (in full, both halves),
`docs/governance/VENTURE-PRODUCT-DECISION-LOG.md` PDL-020 (`:280-362`), and direct repository
inspection this session of: `feature/party/data/local/PartyEntities.kt`,
`feature/party/data/local/PartySourceLinkDao.kt`, `feature/party/data/repository/PartyRepositoryImpl.kt`,
`feature/masterdata/ledger/data/local/LedgerEntity.kt`,
`feature/masterdata/stockitem/data/local/StockItemEntity.kt`,
`feature/catalogue/data/local/CatalogueEntities.kt`, `feature/catalogue/data/local/CatalogueDao.kt`,
`feature/catalogue/domain/model/CatalogueOverrideResolver.kt`,
`feature/catalogue/data/repository/CatalogueRepositoryImpl.kt`,
`core/database/DatabaseConstants.kt`, `core/database/DatabaseModule.kt`,
`feature/dincharya/data/repository/DincharyaRepositoryImpl.kt`,
`feature/party/data/local/PartyNoteDao.kt`, `feature/businessprofile/domain/model/BusinessProfileModels.kt`,
`app/VentureApplication.kt`, `connector/venture_connector/src/services/scheduler/adaptive-scheduler.service.ts`,
`backend/README.md`, and `docs/PROJECT_PROGRESS.md`. The original Vartalap concept document
(`B2B_TRANSACTIONAL_MESSENGER_NEW_CHAT_CONTEXT.docx`) does not exist anywhere in this repository —
confirmed by search — and is not among the sources reconciled here for that reason, not because it
was skipped.

**Full git log confirmation:** Catalogue's implementation history (`1fe1ea7` through `1b8aa90`) was
inspected directly via `git log --oneline -- apps/venture_android/.../feature/catalogue/` to establish
current implementation state, rather than trusted from any prior document's claims about it.
