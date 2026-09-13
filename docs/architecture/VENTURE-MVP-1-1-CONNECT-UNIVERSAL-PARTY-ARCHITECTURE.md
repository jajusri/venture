# VENTURE MVP-1.1 — Connect + Universal Party Identity Architecture

**Status:** Frozen architecture/specification for implementation planning  
**Phase:** MVP-1.1  
**Primary objective:** Build a trusted party/contact layer around existing accounting identity without becoming a generic CRM.  
**Implementation authority:** Product Owner + ChatGPT architecture; Claude/Codex execute within this frozen scope.  
**Repository target:** `C:\Projects\Venture`

---

## 1. Purpose

This document is the implementation architecture for **MVP-1.1 — Connect with Universal Party Identity**.

It continues the locked post-MVP roadmap. It does **not** create a competing roadmap.

The post-MVP sequence remains:

1. MVP-1.1 — Connect with Universal Party Identity
2. MVP-1.2 — Relationship Timeline, Issue History, Dincharya & OI
3. MVP-1.3 — Business Profile
4. MVP-1.4 — Catalogue
5. Integrated hardening + real-world usage/evidence pause before MVP-2

MVP-1.1 must reuse the proven MVP-1 accounting, Connector, Tally, sync, local-first, security, PDF/share and deep-link capabilities. It must not rebuild them.

Development principle:

**Architectural decision → frozen milestone specification → autonomous implementation → deep testing → mini-hardening → evidence → approval → freeze → reuse**

---

## 2. Product outcome

MVP-1.1 should let the owner work with a business party as one coherent identity instead of repeatedly treating the same business as unrelated ledger/contact/voucher records.

The core outcome is:

**VWX in Connect = VWX in Ledger = VWX in Vouchers = VWX in Outstanding = future VWX in Vartalap**

The user should be able to:

- find a party quickly;
- search by name or phone number;
- see the latest Tally balance;
- call or open WhatsApp;
- open that party's Ledger;
- open Vouchers already filtered to that party;
- open Outstanding when supported by the current accounting foundation;
- enrich missing contact details in VENTURE;
- distinguish Tally-confirmed fields from VENTURE-only pending fields;
- generate reviewed Tally-compatible XML for eligible fields;
- import that XML into Tally manually;
- re-sync;
- see fields become confirmed only after VENTURE reads the same value back from Tally;
- keep VENTURE-only notes, tags and extra contact persons without forcing them into Tally.

Connect is **not** a generic CRM.

---

## 3. Non-negotiable architecture principles

### 3.1 Universal Party Identity

Create one stable VENTURE party identity.

A Party must **not** be identical to a Tally Ledger name.

The VENTURE identity must survive:

- ledger rename;
- spelling/formatting changes;
- phone formatting changes;
- future links from notes, issues, Vartalap, Catalogue shares and OI;
- multiple representations of the same business across VENTURE modules.

Never use display-name/string matching as the canonical cross-module identity.

### 3.2 Tally remains an external accounting source

VENTURE does **not** directly write to Tally.

Approved write-back boundary:

**VENTURE edit → review → generate Tally-compatible XML → user imports into Tally → re-sync → confirm**

No TDL dependency.

No silent automatic write-back.

No field turns "confirmed" merely because XML was generated or imported.

### 3.3 Local-first

Connect must open from local indexed data.

Opening Customers/Prospects, searching by phone, opening a Party, viewing tags and notes must not require a live Tally query.

Expected flow:

**Tally → Connector sync → Android local persistence → Connect UI**

Remote sync remains separate from display.

### 3.4 Reuse MVP-1 capabilities

Reuse existing:

- company identity;
- ledger GUID identity;
- voucher GUID identity;
- Ledger screens;
- Voucher screens;
- Voucher filtering;
- PDF/share infrastructure;
- company isolation;
- Room/local persistence patterns;
- Connector APIs where suitable;
- authentication/pairing;
- sync/retry patterns;
- clean-tree/governance;
- performance budgets.

Do not create a second Ledger/Voucher stack inside Connect.

### 3.5 Evidence before expansion

Build only the MVP-1.1 scope.

Do not drift into:

- CRM pipeline;
- lead scoring;
- marketing automation;
- Relationship Timeline;
- Dincharya;
- Insights;
- Vartalap implementation;
- Business Profile;
- Catalogue;
- employee order capture;
- Referral Tree implementation;
- public marketplace/social feed.

The data model should be future-compatible, but future modules stay unimplemented.

---

## 4. Canonical domain model

### 4.1 Party

Conceptual model:

```text
Party
 ├─ partyId                 stable VENTURE UUID/identifier
 ├─ companyScope            VENTURE/Tally company scope
 ├─ displayName
 ├─ partyClassification
 │   ├─ Customer
 │   ├─ Prospect
 │   ├─ Supplier
 │   └─ Other / future-safe
 ├─ primaryPhone
 ├─ primaryEmail
 ├─ address fields
 ├─ GST/business fields where Tally-compatible
 ├─ contact persons
 ├─ tags
 ├─ notes/activity
 ├─ source links
 ├─ field provenance/status
 ├─ createdAt / updatedAt
 └─ lifecycle state
```

The exact storage schema may differ, but the semantic boundary must remain.

### 4.2 PartySourceLink

Use an extensible source-link concept rather than hard-coding identity to one external system.

```text
PartySourceLink
 ├─ partyId
 ├─ sourceType
 ├─ sourceInstanceId
 ├─ externalEntityId
 ├─ externalDisplayName
 ├─ lastConfirmedAt
 └─ metadata required for safe reconciliation
```

For MVP-1.1:

```text
sourceType = TALLY_LEDGER
externalEntityId = stable Tally ledger GUID
sourceInstanceId = company/source identity
```

This is future-proofing for identity only. Do not expose generic source-link complexity in the UI.

### 4.3 Why Party is separate from Tally Ledger

A Tally Ledger is an accounting entity.

A VENTURE Party is the durable business/contact identity used across VENTURE.

A Party can link to a Tally Ledger, but the two concepts must not be collapsed.

This prevents later problems when:

- ledger names change;
- extra VENTURE-only contact persons exist;
- VENTURE-only tags/notes exist;
- future communication identity is attached;
- one business has multiple internal contacts;
- future integrations exist.

---

## 5. Party creation and identity rules

### 5.1 Initial seeding from Tally

For the initial MVP-1.1 rollout, create/derive VENTURE Parties from the existing Tally-backed ledger/contact universe according to explicit eligibility rules.

The exact ledger groups eligible for Customer/Supplier classification should be derived from the existing accounting model and product decisions, not guessed during implementation.

### 5.2 Stable identity

Party identity should be generated once and preserved.

For Tally-backed Parties, the source link must use:

- company/source identity;
- Tally Ledger GUID.

Do not use ledger name as the authoritative key.

### 5.3 Ledger rename

If Tally returns the same ledger GUID with a changed name:

- preserve the same `partyId`;
- update Tally-confirmed display/source fields;
- preserve VENTURE-only data;
- do not create a new Party.

### 5.4 Duplicate detection

Warn about possible duplicates using normalized identifiers such as:

- phone;
- email where appropriate;
- GSTIN where appropriate;
- name similarity as a weak hint only.

Do not auto-merge.

A merge, if later supported, must be explicit and audited.

### 5.5 Same phone on multiple Parties

Do not assume phone uniqueness.

Possible legitimate cases:

- group office number;
- accountant handling multiple firms;
- family businesses;
- shared landline.

Search may return multiple Parties.

---

## 6. Contact field model and provenance

Every enrichable field must carry provenance/confirmation semantics.

### 6.1 Field states

Minimum semantic states:

- **CONFIRMED_FROM_TALLY** — latest Tally sync returned this exact value.
- **VENTURE_ONLY / PENDING** — value exists in VENTURE but has not been confirmed from Tally.
- **EXPORT_READY** — eligible pending value included in reviewed XML preparation.
- **EXPORTED** — XML was generated/exported; still not Tally-confirmed.
- **CONFLICT** — Tally later returned a different non-empty value than the VENTURE pending/exported value.
- **EMPTY / UNKNOWN** — no value known.

UI visual rule remains:

- **Green = confirmed from latest Tally sync**
- **Black = VENTURE-only/pending**

Do not overload color as the only accessibility signal. Status should also be determinable semantically.

### 6.2 Confirmation rule

A field becomes confirmed only when:

1. a Tally sync returns the field;
2. the returned value matches the VENTURE value according to field-specific canonical comparison rules.

XML generation is not confirmation.

Manual user claim is not confirmation.

### 6.3 Conflict behavior

If VENTURE pending value and latest Tally value differ:

- preserve both safely;
- do not silently overwrite one with the other;
- surface a clear conflict/review state;
- allow user to decide next action where required.

Do not create conflict complexity for fields that are VENTURE-only and never intended for Tally.

---

## 7. Tally-backed fields vs VENTURE-only fields

### 7.1 Tally-compatible fields

Mirror relevant Tally ledger contact fields supported by the existing export/import architecture, including empty fields where useful for gradual enrichment.

Typical examples may include:

- ledger/business name;
- primary mobile;
- address components;
- email;
- GST/business identifiers where already supported;
- primary contact person where Tally-compatible.

Implementation must inspect the actual Tally schema currently supported by VENTURE and not invent unsupported fields.

### 7.2 VENTURE-only fields

Examples:

- hierarchical tags;
- extra contact persons;
- internal notes;
- activity notes;
- classification metadata;
- relationship metadata reserved for future milestones;
- VENTURE-specific preferences.

VENTURE-only fields must not be pushed into Tally XML.

---

## 8. Alias phone rule

Locked rule:

If Tally Alias is **exactly 10 digits**, VENTURE may seed/copy it into the VENTURE phone field non-destructively.

Requirements:

- preserve the original Tally Alias;
- do not rewrite Alias;
- do not destroy an existing confirmed phone;
- surface conflicts rather than silently replacing;
- normalize for search while preserving original display value.

---

## 9. Phone normalization and search

Every Tally-backed Party/contact must be searchable by phone number.

Internally normalize equivalent forms such as:

- `9876543210`
- `+91 98765 43210`
- spaces/hyphens/formatting variants

Preserve original display formatting.

Search requirements:

- name;
- phone;
- normalized phone;
- relevant searchable identifiers;
- bounded/indexed/paged queries.

Do not require a network call.

---

## 10. Contact persons

Support multiple people under one Party.

Examples:

- Owner
- Purchase
- Accounts
- Office

Conceptual fields:

```text
PartyContactPerson
 ├─ contactPersonId
 ├─ partyId
 ├─ name
 ├─ designation/role
 ├─ mobile
 ├─ WhatsApp number
 ├─ email
 ├─ primary flag
 ├─ source/provenance where applicable
 └─ lifecycle timestamps
```

Only Tally-compatible primary fields participate in Tally XML export.

Extra people may remain VENTURE-only.

Do not force one business = one person.

---

## 11. Party classification

MVP-1.1 user-facing primary sections:

- **Customers**
- **Prospects**

Supplier/Other classification may exist in the domain if required for stable identity/future compatibility, but must not expand the UI beyond approved MVP-1.1 scope unless the existing plan explicitly requires it.

Avoid turning classification into a sales pipeline.

No lead stages, probability scoring or marketing automation.

---

## 12. Hierarchical tags

Support VENTURE-owned hierarchical and classification tags.

Examples:

```text
AP
 └─ Rayalaseema
     └─ Chittoor
         └─ Tirupati
```

and:

- Dealer
- Retailer
- Builder
- Plumber
- Architect

Requirements:

- local-first;
- stable IDs;
- many-to-many Party association;
- indexed filtering;
- no Tally write-back;
- future-safe without overengineering.

Tags must not be embedded as comma-delimited strings in a way that prevents robust filtering/migration.

---

## 13. Connect Browser UX

Primary entry:

```text
Connect
 ├─ Customers
 └─ Prospects
```

Rows should be compact, fast and information-dense.

A row may show:

- Party/business name;
- phone where available;
- relevant tags;
- last-synced Tally balance;
- Dr/Cr;
- freshness/last-sync indicator where needed;
- compact actions.

Actions:

- Call
- WhatsApp
- VENTURE Message / Vartalap placeholder only if existing product design reserves it safely
- View Ledger
- View Vouchers
- View Outstanding where supported

Do not create dead deceptive actions.

If Vartalap is not implemented, a reserved visual affordance must not pretend to send a message.

---

## 14. Deep links

### 14.1 View Ledger

Use the existing Ledger capability.

Connect should navigate directly to the selected Party's linked Tally Ledger view.

Do not implement a second ledger viewer.

### 14.2 View Vouchers

Use the existing unified Voucher browser.

Open it already filtered to the Party's authoritative ledger/source identity.

Respect existing:

- date/FY semantics;
- pagination;
- search;
- Voucher type filters;
- snapshot/local-first behavior.

Do not filter only by display name when a stable source ID is available.

### 14.3 View Outstanding

Where current MVP-1 accounting data already supports bill-wise outstanding safely:

show unpaid/partly-paid items, ageing and total outstanding.

If the required authoritative data is not yet present, do not fabricate Outstanding in MVP-1.1. Mark the capability as gated by existing accounting support.

---

## 15. Balance presentation

Each Tally-backed Party should show:

- latest synchronized balance;
- clear Dr/Cr;
- last sync timestamp/freshness where useful.

Balance is accounting truth from the existing Tally/accounting stack.

Connect must not independently calculate a competing balance.

---

## 16. Call and WhatsApp actions

### 16.1 Call

Use Android/system call intent behavior.

No silent calls.

### 16.2 WhatsApp

Use existing approved VENTURE WhatsApp/share conventions.

Do not silently send messages.

Where a valid Party number resolves, direct behavior may be used according to the existing approved architecture.

Where no valid number resolves, fall back to chooser/select behavior rather than guessing.

Multi-WhatsApp/default-selection behavior should reuse existing Settings/architecture.

---

## 17. Notes / Activity

Every Party should have Notes / Activity capability.

MVP-1.1 notes may capture:

- payment issues;
- complaints;
- delivery issues;
- commitments;
- product interest;
- internal remarks;
- follow-up context.

Notes are VENTURE data.

They must be local-first and company/party scoped.

### 17.1 Voucher linkage

A note may link to one or more synced vouchers.

Example:

> Customer says 2 pieces were short in Sales Voucher #1842.

Tapping the linked voucher should open the existing Voucher Detail.

Use stable voucher identity/GUID, not voucher display text, as the durable link.

### 17.2 Do not build Timeline yet

MVP-1.1 notes should be structured enough for future Relationship Timeline/Issue History, but MVP-1.2 owns those features.

Do not prematurely transform Notes into Timeline/Dincharya.

---

## 18. Tally XML enrichment round trip

### 18.1 Flow

```text
Tally sync
   ↓
Party field confirmed
   ↓
User edits/adds eligible field in VENTURE
   ↓
Field becomes pending
   ↓
User reviews changes
   ↓
VENTURE generates Tally-compatible XML
   ↓
User manually imports XML into Tally
   ↓
VENTURE re-syncs
   ↓
Exact matching value read from Tally
   ↓
Field becomes confirmed
```

### 18.2 Review before XML generation

Never generate a broad opaque XML mutation without showing what is being proposed.

The user should be able to review:

- Party;
- fields changed;
- old Tally value;
- proposed value;
- conflicts;
- fields excluded because they are VENTURE-only.

### 18.3 No automatic import

VENTURE prepares the XML.

The user controls Tally import.

No automatic direct write.

### 18.4 Re-sync confirmation

After import, normal sync confirms values.

A field stays pending/exported until the matching Tally value is read back.

---

## 19. XML conflict and failure cases

Handle:

- Tally ledger renamed after VENTURE edit;
- ledger GUID no longer found;
- Tally already has a different value;
- XML generated but never imported;
- XML imported but Tally rejects it;
- Tally import succeeds but export visibility lags;
- user edits field again after XML generation;
- duplicate export attempts;
- partial confirmation across multiple fields.

Never mark the entire Party confirmed because one field matches.

Confirmation is field-specific.

---

## 20. Company isolation

Party identity is VENTURE-stable, but Tally source links and accounting context remain company-scoped.

MVP-1.1 must not accidentally expose or merge one company's ledger/contact data into another company.

Tests must cover:

- same ledger name in two companies;
- same phone in two companies;
- same external GUID assumptions;
- company switching;
- local caches.

---

## 21. Offline behavior

Connect must remain useful offline using the last synchronized local state.

Offline user can:

- browse Parties;
- search;
- open locally available Ledger/Voucher data;
- read notes/tags;
- edit VENTURE-only/pending fields;
- prepare local draft changes where safe.

Actions requiring live external apps may still launch if device capability exists.

Tally confirmation obviously requires a later sync.

Offline edits must not be lost when sync resumes.

---

## 22. Migration strategy from MVP-1

MVP-1.1 must be additive.

Existing MVP-1 accounting data remains valid.

Migration should:

1. introduce Party tables/entities safely;
2. seed/map existing eligible ledgers to Parties;
3. use stable ledger GUID source links;
4. preserve existing Android Room data;
5. avoid destructive ledger/voucher migrations;
6. be restart-safe and idempotent;
7. support rollback/fail-safe where the current migration framework permits.

Do not make MVP-1.1 installation require clearing app data.

---

## 23. Data ownership and authority

### Accounting authority
Tally/Connector existing accounting stack.

### Party/contact authority
VENTURE Party entity with field-level source/provenance.

### Tally-confirmed contact fields
Latest synchronized Tally data.

### VENTURE-only fields
VENTURE local source of truth.

### Notes/tags/contact persons
VENTURE source of truth unless explicitly mapped to a Tally-compatible primary field.

Avoid two competing canonical values without provenance.

---

## 24. Indexing and performance

Connect should feel instant on realistic business datasets.

Required indexed/bounded access patterns include:

- Party by `partyId`;
- Party by company/source link;
- ledger GUID → Party;
- normalized phone;
- name/search token strategy appropriate to current Room capabilities;
- Party classification;
- tags;
- notes by Party;
- linked voucher IDs.

Avoid full-table in-memory filtering for routine UI.

No live Tally round trip for browser/search.

---

## 25. Scale discipline

Permanent principle:

**Design irreversible decisions as though VENTURE may one day serve a billion users. Build reversible implementations only for the scale evidence justifies today.**

For MVP-1.1 this means:

Design carefully:

- stable IDs;
- source-of-truth;
- migrations;
- provenance;
- source links;
- company isolation;
- permission boundaries.

Do not prematurely build:

- cloud microservices;
- distributed identity service;
- complex event bus;
- global directory;
- public graph.

Local-first implementation remains appropriate.

---

## 26. Security and privacy

MVP-1.1 must not weaken current pairing/trust/security.

Contact data may be sensitive.

Requirements:

- no new public Connector endpoints without explicit approval;
- no raw contact data in diagnostics beyond existing privacy-safe rules;
- no secret/token leakage;
- no device phonebook import;
- no silent message sending;
- no Tally direct write;
- company isolation;
- least privilege for any future sharing.

---

## 27. Explicit device-phonebook boundary

Connect starts from **Tally/VENTURE ledger contacts**, not the Android device phonebook.

Do not request Contacts permission merely to implement MVP-1.1.

Do not import phonebook contacts.

Prospects must be VENTURE-managed records created through approved VENTURE flows, not bulk-ingested device contacts.

---

## 28. Future compatibility without feature creep

MVP-1.1 Party must be suitable for later attachment of:

- Relationship Timeline;
- Issue History;
- Dincharya;
- OI;
- Business Profile/business identity;
- Vartalap;
- Catalogue share events;
- Referral relationships;
- staff/order attribution.

But none of those modules are implemented here.

Future compatibility means stable identity and clean interfaces, not empty tables for every future concept.

---

## 29. Milestone execution plan

### 1.1-A — Universal Party Foundation

Build:

- Party domain model;
- stable `partyId`;
- Tally Ledger source link;
- field provenance/status;
- company scope;
- primary contact fields;
- contact persons;
- classification foundation;
- normalized phone;
- alias-phone rule;
- migration;
- repositories/use cases;
- indexes;
- focused tests.

No broad Connect UI yet.

Acceptance:

- existing Ledgers/Vouchers unchanged;
- same ledger GUID maps to same Party;
- rename preserves Party;
- migration is idempotent;
- phone normalization works;
- company isolation holds;
- provenance works.

Mini-harden before proceeding.

### 1.1-B — Connect Browser + Deep Links

Build:

- Connect entry;
- Customers;
- Prospects;
- local indexed search;
- phone search;
- compact rows;
- balance/DrCr;
- tags;
- Call/WhatsApp;
- View Ledger;
- View Vouchers;
- Outstanding only if existing authoritative support is ready.

Acceptance:

- browser opens without remote call;
- search feels immediate;
- deep links reuse existing screens;
- no duplicated accounting stack;
- offline browse works.

Mini-harden before proceeding.

### 1.1-C — Party Detail + Notes

Build:

- Party Detail;
- contact persons;
- tags;
- notes/activity;
- voucher-linked notes;
- clear field provenance display;
- editing of approved fields.

Acceptance:

- notes persist offline;
- voucher links use stable identity;
- field states are understandable;
- VENTURE-only data never accidentally exports to Tally.

Mini-harden before proceeding.

### 1.1-D — Tally XML Enrichment Round Trip

Build:

- change review;
- eligible-field filtering;
- XML preparation;
- export lifecycle;
- re-sync confirmation;
- conflict handling;
- audit/evidence.

Acceptance:

- no direct Tally write;
- XML contains only eligible reviewed changes;
- generated/imported does not equal confirmed;
- re-sync exact match confirms field;
- conflict remains visible;
- repeated export is safe.

Mini-harden before proceeding.

### 1.1-E — Integrated MVP-1.1 Hardening

Cover:

- duplicates;
- rename;
- same name across companies;
- same phone across Parties;
- phone conflicts;
- deleted/altered Tally ledger;
- offline;
- restart;
- migration;
- upgrade from frozen MVP-1;
- large Party list;
- XML round-trip;
- deep links;
- notes/voucher links;
- privacy/security;
- performance;
- APK size;
- Android physical validation;
- regression of all MVP-1 accounting capabilities.

Then freeze MVP-1.1.

---

## 30. UX intent

Connect should feel like a business contact/action surface, not a CRM database.

Principles:

- compact;
- fast;
- obvious actions;
- low cognitive load;
- local-first;
- meaningful balance context;
- clear Tally-confirmed vs pending fields;
- progressive disclosure;
- no giant forms on first open;
- no unnecessary cards;
- no developer jargon.

Use the approved VENTURE Android visual language established in MVP-1.

If approved Connect visual masters exist later, they become visual authority without changing this functional architecture.

---

## 31. Failure states

At minimum design for:

- no Parties yet;
- no eligible Tally contacts;
- Tally sync stale;
- offline;
- Party source link unavailable;
- ledger deleted/altered;
- phone missing/invalid;
- WhatsApp unavailable;
- XML generation failure;
- conflict after re-sync;
- duplicate warning;
- migration interrupted;
- company switched;
- linked voucher unavailable in current local window.

Failures should be recoverable and honest.

---

## 32. Acceptance evidence

Each meaningful slice should produce:

### Automated
- unit tests;
- repository/use-case tests;
- migration tests;
- Room tests;
- search/index tests;
- XML generation tests;
- regression tests;
- lint/build.

### Performance
- representative Party list/search benchmark;
- large contact set;
- bounded memory/query behavior;
- APK size impact.

### Physical
As relevant:
- upgrade existing Android build in-place;
- no data/pairing loss;
- Connect browser;
- phone search;
- Call/WhatsApp launch;
- View Ledger/Vouchers;
- offline access;
- XML export/import/re-sync on controlled sample.

### Evidence
- commits;
- checkpoint update;
- Development Ledger update;
- milestone status;
- exact known limitations.

---

## 33. Rollout / rollback

MVP-1.1 must preserve the frozen MVP-1 accounting foundation.

If Party migration fails:

- do not corrupt existing Ledger/Voucher data;
- fail safely;
- keep migration diagnosable;
- allow corrected retry according to established migration framework.

Do not require uninstall/clear-data as normal recovery.

MVP-1.1 should be feature-additive and upgrade-safe.

---

## 34. Hard boundaries for autonomous agents

Claude/Codex may autonomously:

- inspect;
- implement this frozen scope;
- add safe migrations;
- add tests;
- run builds/lints/benchmarks;
- debug within scope;
- mini-harden;
- update documentation/status;
- commit coherent work;
- build/install Android candidate via ADB where authorized.

Must stop/escalate for:

- Party identity semantic change;
- direct Tally-write proposal;
- accounting semantic change;
- security/trust architecture change;
- destructive migration/data loss;
- product-scope expansion;
- generic CRM expansion;
- device-phonebook integration;
- major new dependency/framework;
- unclear eligibility/accounting authority for Outstanding;
- ambiguous approved visual direction.

---

## 35. Definition of MVP-1.1 complete

MVP-1.1 is complete when:

- Universal Party Identity is stable and migration-safe;
- eligible Tally ledgers map deterministically to Parties;
- Customers/Prospects are locally browsable/searchable;
- phone search works;
- balance/DrCr is correct and freshness is honest;
- Call/WhatsApp and accounting deep links work;
- Party Detail supports Tally-backed and VENTURE-only fields;
- field provenance/green-black semantics are correct;
- contact persons/tags/notes work;
- voucher-linked notes deep-link correctly;
- XML round-trip is reviewed, user-controlled and re-sync-confirmed;
- no direct Tally write exists;
- offline behavior works;
- no regression to MVP-1;
- performance remains lightweight;
- mini-hardening and integrated hardening pass;
- physical acceptance is recorded;
- checkpoint/Development Ledger are updated;
- exact deferred items are explicit.

---

## 36. Relationship to later roadmap

### MVP-1.2
Uses Party identity for:
- Relationship Timeline;
- Issue History;
- Dincharya;
- deterministic OI.

### MVP-1.3
Uses Party/business identity foundation for:
- Business Profile;
- business-to-business identity.

### MVP-1.4
Uses stable product identity and Business Profile for:
- Catalogue;
- Business Library/Resources;
- governed assets.

Do not pull later behavior into MVP-1.1.

---

## 37. Source-of-truth repository documents to read before implementation

Implementation agents must read the latest repository versions of:

- `docs/status/VENTURE-CURRENT-DEVELOPMENT-STATUS.md`
- `docs/status/VENTURE-DEVELOPMENT-LEDGER.md`
- `docs/planning/VENTURE-MASTER-PRODUCT-EXECUTION-PLAN.md`
- `docs/governance/POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`
- `docs/governance/VENTURE-CLAUDE-PROMPT-PROCESSING-RULES.md`
- `docs/governance/VENTURE-PRODUCT-DECISION-LOG.md`
- `docs/planning/VENTURE-NOT-NOW.md`
- relevant architecture ADRs
- relevant capability registry
- relevant MVP-1.1 planning documents
- this architecture specification

If repository evidence is newer than this downloaded planning copy, reconcile intentionally rather than silently overriding it.

---

## 38. Permanent implementation rule

**Reuse proven MVP-1 capabilities. Do not implement another stack.**

For every MVP-1.1 work unit:

**INSPECT → IMPLEMENT → TEST → MINI-HARDEN → COMMIT → UPDATE SPECIALIST STATUS → UPDATE DEVELOPMENT LEDGER → UPDATE CURRENT CHECKPOINT → CLEAN-TREE AUDIT → EXACT NEXT TASK**

No completed meaningful work may exist only in AI session memory.

---

## 39. Immediate next action

Do **not** jump directly into broad Connect UI implementation.

Next:

1. commit/adopt this architecture into the canonical repository planning/architecture location;
2. reconcile it with any newer repository-specific MVP-1.1 decisions;
3. freeze Milestone **1.1-A — Universal Party Foundation**;
4. give Claude the bounded autonomous 1.1-A implementation prompt;
5. mini-harden 1.1-A;
6. then proceed to 1.1-B.

This preserves the locked roadmap principle:

**Brainstorm → clarified outcome → autonomous implementation → mini-hardening → acceptance → next slice.**
