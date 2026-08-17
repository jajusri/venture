# BUDCOM — Connect Contacts, Universal Party Identity & Referral Tree Specification

**Status:** LOCKED / VVIMP  
**Purpose:** Product + architecture specification for MVP-1.1 Connect and the party/contact foundation that later supports Business Profile, Catalogue, Vartalap, Dincharya, OI and Insights.

---

## 1. Product intent

Connect is not a generic phonebook and not a conventional CRM.

It is BUDCOM's **Business Relationship Record** around one canonical party identity.

For every party, BUDCOM should help answer:

1. Who is this party?
2. Who are the people inside this business?
3. What is our current accounting relationship?
4. How do I contact or act on them?
5. Who introduced them?
6. What downstream business value has that introduction created?
7. What notes, issues, commitments and relationship context exist?
8. Is this party on BUDCOM and connected to us?
9. What external Profile/Catalogue/Vartalap information may be shared with them?
10. What private information must remain strictly internal?

The module must remain lightweight, local-first, fast, auditable and understandable without CRM training.

---

## 2. Foundational rule — one Universal Party Identity

One real business/person must resolve to one stable BUDCOM Party Identity.

Conceptually:

**VWX in Contacts = VWX in Ledger = VWX in Vouchers = VWX in Outstanding = VWX in Referral Tree = future VWX in Business Profile/Catalogue/Vartalap/Dincharya/Insights**

Do not depend on ledger-name string matching across modules.

The Party Identity is the anchor. Ledger/accounting data, mobile/contact data, referral ancestry, BUDCOM network identity and later profile/catalogue/message relationships attach to it.

### 2.1 Identity evidence may include

- accounting ledger identity;
- normalized mobile number;
- ledger alias when it is a valid mobile fallback;
- GSTIN where available;
- email where appropriate;
- BUDCOM account/business identity;
- confirmed user links;
- other stable identifiers introduced later.

No single identifier should be treated as infallible in every case.

---

## 3. Contact population

Initial Connect population:

- Tally/BUDCOM ledger contacts;
- BUDCOM-created Prospects.

The device phonebook is **not** an uncontrolled canonical import source in the initial implementation.

If device-contact matching is introduced later, it must be a deliberate enrichment/matching workflow around Universal Party Identity, not a dump of personal contacts into BUDCOM.

---

## 4. Contacts screen — final information architecture

### 4.1 Primary commercial filter

Use:

**All | Customers | Prospects**

Definitions:

- **All:** every party/contact visible to Connect under current permissions.
- **Customers:** parties with an accounting relationship/ledger.
- **Prospects:** BUDCOM-created parties not yet represented by an accounting customer ledger.

Do not make "BUDCOM" a mutually exclusive category beside Customer/Prospect because a party may simultaneously be a Customer and a BUDCOM-connected party.

### 4.2 Separate BUDCOM connection-state filter

Provide a secondary filter/state such as:

**All | On BUDCOM | Connected**

Internally support clear connection states:

- Not on BUDCOM
- BUDCOM identity found
- Connection pending
- Connected
- Disconnected
- Blocked

Connection state must not create duplicate contact records.

### 4.3 Tags/filtering

Support:

- hierarchical geographic tags;
- business classification tags;
- later user-defined operational tags.

Examples:

- AP → Rayalaseema → Chittoor → Tirupati
- Dealer
- Retailer
- Builder
- Plumber
- Architect

Tags remain BUDCOM data unless a later explicit product decision says otherwise.

---

## 5. Contact list row

Each row should answer quickly:

**Who? → What relationship? → What balance matters? → What can I do now?**

Recommended compact row content:

### Primary line
- Party / business name
- current Dr/Cr balance on the right or otherwise clearly aligned
- BUDCOM connection indicator if applicable

### Secondary line
- primary mobile / contact person context
- location / important tag context
- last-sync freshness

### Optional compact context
- last meaningful business activity where inexpensive to calculate
- Outstanding indicator if useful

Do not overload the row with full ageing/accounting details.

### Primary quick actions
- Call
- WhatsApp
- Vartalap / BUDCOM Message when available

### Contextual business actions
- View Ledger
- View Vouchers
- View Outstanding
- Referral Tree

Accounting screens must be reused through deep links. Do not rebuild duplicate ledger/voucher/outstanding tables inside Connect.

---

## 6. Accounting snapshot

Every accounting-backed contact should show:

- last-synced Tally balance;
- unmistakable Dr/Cr presentation;
- last-sync timestamp/freshness;
- link to authoritative Ledger;
- link to Vouchers filtered to that party;
- link to Outstanding / unpaid-partly-paid bills / ageing.

The balance shown in Connect is a **last-synced accounting value**, not a second balance engine calculated independently by Connect.

---

## 7. Search and mobile identity resolution

Search should behave as **party-resolution search**, not plain text-only search.

Searchable evidence should include, as available:

- party/ledger name;
- normalized mobile;
- contact-person mobile;
- ledger alias where it qualifies under the locked Alias→mobile fallback;
- GSTIN;
- BUDCOM identity;
- other stable identifiers later.

### 7.1 Phone normalization

Internally normalize equivalent representations such as:

- `9876543210`
- `+91 98765 43210`
- formatted/spaced variants

Preserve the original displayed phone number.

### 7.2 Alias mobile fallback

If no valid explicit mobile exists, a ledger Alias may act as a candidate mobile only when it satisfies the locked strict Indian mobile validation rule.

Explicit mobile always wins over Alias fallback.

---

## 8. Editable contact record — VVIMP

A Tally/accounting ledger is the starting point, not the editing boundary.

BUDCOM must allow gradual contact enrichment.

Relevant Tally-compatible fields should be visible even when blank, such as:

- Name
- Mobile
- Address
- GSTIN
- Email
- Contact person
- other supported accounting-contact fields

BUDCOM-only enrichment may also exist where appropriate.

---

## 9. Field provenance and Green/Black state

The simple user-facing rule remains:

- **Green:** confirmed from the latest Tally sync.
- **Black:** added/edited in BUDCOM but not yet confirmed back from Tally.

Green must never mean merely:
- XML generated;
- XML exported;
- XML imported.

A field turns Green only when a later Tally re-sync returns the matching value.

### 9.1 Internal provenance

Internally, important values should retain source/provenance metadata such as:

- Tally-confirmed
- BUDCOM-only
- pending Tally confirmation
- later Business Profile supplied
- later BUDCOM-network verified

This provenance must support:
- conflict handling;
- duplicate resolution;
- XML review;
- auditability;
- future network synchronization.

---

## 10. Tally update boundary

BUDCOM must not directly write into Tally.

Locked workflow:

**Tally → BUDCOM sync → user enriches/edits → review → generate Tally-compatible XML → user imports XML into Tally → BUDCOM re-syncs → matching values become Green**

No TDL dependency.

BUDCOM-only fields do not automatically go back to Tally.

---

## 11. Multiple contact people under one party

A business party may contain multiple people:

- Owner
- Purchase
- Accounts
- Office
- Sales
- other role/purpose

Each may carry:

- name;
- designation/role;
- mobile;
- WhatsApp;
- email.

Only Tally-compatible primary contact fields participate in Tally XML export.

Additional people may remain BUDCOM-only.

### 11.1 Purpose-aware future routing

The data model should allow later context-aware routing without requiring it now.

Examples:

- payment follow-up → Accounts;
- purchase enquiry → Purchase;
- owner-level matter → Owner.

Do not silently auto-send. Where ambiguity exists, show a compact recipient chooser.

---

## 12. Duplicate detection and merge safety

When creating/enriching contacts, warn about possible duplicates using normalized identifiers such as:

- mobile;
- GSTIN;
- email;
- BUDCOM identity;
- other stable identifiers later.

Rules:

- never auto-merge without user approval;
- preview the proposed merge;
- show conflicting values;
- preserve provenance;
- make the merge auditable;
- maintain rollback/recovery capability where practical.

---

## 13. Prospect → Customer continuity

If a Prospect later becomes an accounting customer/ledger, BUDCOM must propose linking the accounting identity to the existing Party Identity.

Do not create a second unrelated party merely because Tally now contains a ledger.

Example:

`ABC Traders (Prospect)`  
later Tally creates `ABC TRADERS`  
→ BUDCOM suggests a possible identity match  
→ user confirms  
→ same Party Identity continues.

Existing notes, tags, referral ancestry, contact people and relationship context must survive the transition.

---

# 14. UNIVERSAL REFERRAL TREE — RJ CONCEPT — VVIMP

Referral Tree is a first-class Party capability.

Any person/party may refer another:

- customer;
- supplier;
- prospect;
- employee;
- friend;
- owner contact;
- any other legitimate referrer.

Referral ancestry continues recursively:

**A → B → C → D**

The tree/graph is anchored on Universal Party Identity, never just names.

---

## 15. Referral relationship vs employee attribution

These are separate concepts.

**Referral:** who introduced whom.

**Employee attribution:** who handled/developed/serviced the business.

They may be the same person, but must not be assumed to be the same.

An employee receives referral attribution only when the employee was genuinely the referrer.

---

## 16. Referral Tree visibility from every relevant party/ledger

Every relevant Party/Contact and accounting Ledger should expose a direct lightweight path to:

**Referral Tree**

Recommended party summary:

- Referred By
- Direct Referrals
- Active downstream parties
- Referral Tree
- later Direct Business Value
- later Indirect / Referral Value
- later Total Attributable Network Value

Ledger screens should deep-link into the same canonical Referral Tree capability rather than implement a duplicate tree.

---

## 17. Referral Tree privacy

Referral ancestry and value attribution are **private owner-side business intelligence by default**.

A customer/referral descendant must not automatically see:

- the owner's full referral network;
- siblings/other descendants;
- private accounting values;
- attribution metrics;
- rewards;
- internal notes.

External visibility, if introduced later, requires a separate explicit permission model.

---

## 18. Direct and indirect referral value

BUDCOM should ultimately distinguish:

### Direct business value
Business generated directly by the party.

### Indirect / referral value
Attributable downstream value generated by referral descendants.

Example:

A referred B.  
B referred C.  
C generates business.

A may have indirect attributable contribution through the referral chain, but attribution must not double-count the same underlying transaction.

---

## 19. Referral-value auditability

Any displayed referral-value number must be explainable.

The system should be able to identify:

- which descendant parties contributed;
- which qualifying transactions contributed;
- time period;
- attribution path;
- attribution rules;
- exclusions;
- how double counting was prevented.

The owner may recognize/reward value creators at their discretion.

BUDCOM measures and explains; it does not silently create financial obligations or entitlement to rewards.

---

## 20. Notes / Activity

Every party has Notes / Activity.

Possible note types/context:

- payment issue;
- complaint;
- delivery issue;
- commitment;
- product interest;
- internal remark;
- follow-up context.

Notes remain BUDCOM data unless explicitly included in a future shared workflow.

A note may link to one or more vouchers.

Example:

`Customer says 2 pieces were short in Sales Voucher #1842`

Tap → opens the linked voucher.

---

## 21. Internal vs shared information — critical boundary

Internal relationship data must not leak merely because two businesses connect on BUDCOM.

Internal-only examples:

- balance;
- outstanding/ageing;
- private tags;
- internal notes;
- issue history;
- referral value;
- downstream network;
- internal classifications;
- recovery concerns.

External/shared concepts later include:

- Business Profile;
- seller-approved Catalogue;
- seller-approved Resources;
- Vartalap messages;
- explicit shared documents.

**Connection does not equal permission to see internal accounting/relationship data.**

---

## 22. WhatsApp privacy truth

If BUDCOM opens WhatsApp, it may record:

`WhatsApp opened/shared from BUDCOM`

It must not claim to know what was discussed in WhatsApp unless the user records a note or the conversation occurs inside Vartalap.

---

## 23. Handoff to future BUDCOM modules

Connect is the identity/relationship foundation, but it must not own every future object.

### Business Profile
Canonical business identity and externally visible business information.

### Catalogue
Canonical structured product presentation.

### Vartalap
Canonical in-app business communication.

### Relationship Timeline
What happened.

### Issue History
A grouped history of a continuing problem/issue.

### Dincharya
Actions the user has undertaken and must follow through.

### Insights
What matters, changed or deserves attention.

### Suggested Actions
What BUDCOM recommends considering.

All these later modules should link to the same Universal Party Identity.

---

## 24. Recommended contact detail hierarchy

Recommended screen order:

1. Identity / business name
2. connection state
3. contact people
4. communication actions
5. accounting snapshot
6. View Ledger / View Vouchers / View Outstanding
7. Referral Tree
8. tags/classification
9. editable contact fields + provenance
10. Notes / Activity
11. later Relationship Timeline / Issues / Dincharya
12. later Business Profile / Catalogue / Vartalap links

Keep the screen compact and progressive. Avoid making every section visually heavy.

---

## 25. Local-first / offline principle

Core Contact/Party information that has already been synchronized must remain usable from local storage.

Core offline-capable actions should include, where technically applicable:

- view contact;
- search cached parties;
- view last-synced balance;
- View Ledger / Vouchers from local accounting data;
- view/edit BUDCOM-side fields;
- notes;
- tags;
- Referral Tree already stored locally;
- prepare pending work for later synchronization where safe.

Network-specific BUDCOM connection/profile/Vartalap features may show a clear unavailable/stale state when offline.

---

## 26. Performance principle

Connect must remain lightest-of-the-light.

Requirements:

- indexed search;
- normalized phone lookup;
- paged/virtualized long lists;
- no N+1 accounting queries;
- no duplicate accounting datasets in Contacts;
- deep-link to authoritative accounting screens;
- lazy-load heavier relationship/referral detail;
- compact local-first contact rows;
- no unnecessary cloud dependency for basic contact/accounting use.

---

## 27. Trust and merge principles

1. Never silently overwrite Tally-confirmed values with lower-confidence sources.
2. Never auto-merge possible duplicate parties without approval.
3. Never expose private internal fields through a BUDCOM connection by default.
4. Never infer a WhatsApp conversation's content.
5. Never claim referral causation beyond the attribution evidence available.
6. Never double-count referral-derived economic value.
7. Never directly modify Tally; preserve XML review/import/re-sync confirmation.
8. Preserve data provenance and auditability for consequential identity changes.

---

## 28. What Connect is NOT

Do not turn initial Connect into:

- generic CRM;
- social feed;
- public open directory;
- sales pipeline with dozens of statuses;
- arbitrary lead-scoring engine;
- relationship-health percentage;
- generic task manager;
- replacement accounting system;
- device-phonebook dump;
- open price marketplace.

Add complexity only when usage evidence justifies it.

---

## 29. Acceptance criteria — MVP-1.1 Connect foundation

A satisfactory first Connect implementation should prove:

1. One stable Party Identity anchors a Tally-backed customer.
2. Customer appears once even if multiple identifiers exist.
3. All / Customers / Prospects filtering is correct.
4. BUDCOM connection state is separate from commercial classification.
5. Search by name works.
6. Search by normalized mobile works.
7. Alias-mobile fallback integrates safely where applicable.
8. Last-synced Dr/Cr balance is visible and clear.
9. Last-sync freshness is visible.
10. Call / WhatsApp / Vartalap action architecture is present.
11. View Ledger deep-links correctly.
12. View Vouchers opens party-filtered vouchers.
13. View Outstanding opens the authoritative outstanding view.
14. Contact fields may be enriched in BUDCOM.
15. Tally-confirmed vs pending BUDCOM fields remain distinguishable.
16. Green confirmation only occurs after matching Tally re-sync.
17. XML export respects the no-direct-Tally-write boundary.
18. Multiple contact people are supported.
19. Hierarchical/business tags work.
20. Notes may link to vouchers.
21. Duplicate candidates trigger warning, not auto-merge.
22. Prospect→Customer linking preserves identity/history.
23. Referral Tree is reachable from Party/Contact.
24. Referral Tree is reachable from relevant Ledger.
25. Referral ancestry uses Party IDs, not names.
26. referral vs employee attribution is distinct.
27. Internal accounting/referral information is not exposed externally by connection.
28. cached/local Connect remains usable offline.
29. long lists/search remain fast.
30. no duplicate accounting implementation is introduced.

---

## 30. Product principle to preserve

> **Connect should answer: Who is this party? Who are the people inside it? What is our real business relationship? Who introduced them? What accounting context matters now? How do I contact or act on them? — without duplicating accounting or exposing private internal information.**

---

## 31. Locked status

This document is the product/architecture baseline for the Connect contact foundation and Universal Referral Tree.

Future implementation prompts should preserve this intent unless the Product Owner explicitly changes a locked decision.

**VVIMP:**
- editable debtor/customer contact layer;
- mobile/accounting-ledger identity matching;
- Universal Party Identity;
- Dr/Cr balance + accounting deep links;
- multiple contact persons;
- Tally XML confirmation lifecycle;
- Referral Tree available from relevant parties/ledgers;
- direct vs downstream referral value without double counting;
- private internal data boundary;
- Prospect→Customer identity continuity.
