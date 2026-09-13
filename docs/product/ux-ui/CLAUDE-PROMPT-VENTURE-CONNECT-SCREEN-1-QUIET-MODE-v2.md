# CLAUDE IMPLEMENTATION PROMPT — VENTURE CONNECT SCREEN 1 (QUIET/NORMAL MODE) v2

Read and follow:
1. `docs/product/ux-ui/VENTURE-AUTHORITATIVE-UX-UI-SPECIFICATION-v1.docx`
2. `docs/product/ux-ui/VENTURE-ACCOUNTING-OPTIONAL-UX-PRINCIPLE.md`
3. `docs/product/ux-ui/VENTURE-CONNECT-IMPLEMENTATION-CHECKLIST-v2.md`

## Critical overriding product requirement

VENTURE MUST work as a useful business Contacts product when the user has NO accounting software connected.

Do not architect Connect Screen 1 around Tally being mandatory.

Accounting is an optional enrichment layer.

## Before coding

Inspect the repo and report:
- whether current Contact/Party identity requires an accounting ledger,
- which UI elements currently assume balance/ledger/voucher data always exists,
- how to represent a VENTURE-native Party before a Tally ledger exists,
- how later accounting sync can enrich/link that Party without creating a duplicate identity,
- which filters/sorts can work without accounting and which require accounting.

If the current data model structurally requires a ledger for every Party, STOP before creating a broad migration and report the narrowest compatibility-safe foundation needed.

## Screen 1 must support BOTH states

### State A — VENTURE-only / no accounting

Required:
- Contacts screen works normally.
- Party card does not show dead Balance/Ledger/Sale/Payment placeholders.
- Show Party Name and direct Call/WhatsApp actions.
- Use VENTURE-native last activity only if a reliable activity exists; otherwise keep the card clean.
- Search/filter/sort remain useful using VENTURE-native fields.
- Prospects, geography, business type, tags/groups and product/service relevance may operate where data exists.
- Debtors/Creditors/accounting-only controls must not clutter the UI when unavailable.

Conceptual card:

ABC TRADERS
Last activity · 12 Aug
                                      Call  WhatsApp

### State B — accounting connected

Enrich the SAME canonical party card with:
- Balance + Dr/Cr + date
- Last Sale value/date
- Last Payment value/date

Conceptual card:

ABC TRADERS                         ₹84,250 Dr
Sale ₹27,450 · 12 Aug    Payment ₹40,000 · 09 Aug
                                      Call  WhatsApp

Do not create separate accounting/non-accounting Contact screens.

## Capability-aware filters/sorts

Universal:
- All
- Prospects
- supported geography/business/tag/product-service branches
- A–Z / Z–A
- VENTURE-native last activity where reliable

Accounting-only:
- Debtors
- Active Debtors
- Creditors
- Balance
- Turnover
- Profitability
- Credit Days
- accounting-defined Sale/Payment metrics

Only expose meaningful options.

## Party identity

- Stable internal Party ID must not depend on a Tally ledger.
- Manual/Prospect/Vartalap/Profile/Referral-originated parties must remain valid first-class Parties.
- Later accounting synchronization should suggest/link the matching ledger to the existing Party.
- Phone/WhatsApp are strong matching evidence, not unconditional cross-business merge keys.

## Everything else from the previous Screen 1 prompt remains applicable

Preserve:
- separate Search,
- current-state Filter/Sort controls,
- smart expandable shortcut row,
- direct Call/WhatsApp,
- fixed bottom A/c Data | Connect | Vartalap,
- Home access,
- state restoration,
- offline/cached behavior,
- accessibility,
- performance,
- quiet visual language,
- no scope creep.

## Additional tests

Test at minimum:
1. no-accounting user sees a complete, balanced Contacts screen;
2. accounting-connected user receives additive Balance/Sale/Payment enrichment;
3. accounting source temporarily unavailable preserves last trustworthy synced enrichment without breaking VENTURE-native Contacts;
4. manual Prospect later linked to accounting ledger preserves Party ID/context;
5. accounting-only filters/sorts are not presented misleadingly when accounting data does not exist.

Do not proceed beyond Screen 1.
