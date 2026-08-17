# BUDCOM — Connect Implementation Checklist v2

Status: IMPLEMENTATION-READY BASELINE
Authority:
- BUDCOM-AUTHORITATIVE-UX-UI-SPECIFICATION-v1.docx
- BUDCOM-ACCOUNTING-OPTIONAL-UX-PRINCIPLE.md

## New overriding requirement

Connect must be fully useful WITHOUT accounting integration.

Accounting fields are additive and capability-aware. Never assume a Ledger, balance, sale/payment history, debtor/creditor classification, turnover or profitability exists.

## Screen 1 — Contacts list

### Universal base state (must work for every user)
- Header: Connect / Contacts.
- Search.
- Current filter-state control.
- Current sort-state control.
- Smart expandable shortcut line.
- Party name.
- Call and WhatsApp direct CTAs.
- Tap party → Contact Detail.
- Vartalap entry where available.
- Groups/tags/geography/business type/product/service relevance as supported.
- A–Z / Z–A sort.
- Prospects and other BUDCOM-native classifications.
- Local/offline usability.

### Accounting-enriched state (only when accounting source exists)
Add, without changing the canonical party:
- Balance + Dr/Cr + date
- Last Sale value/date
- Last Payment value/date
- Ledger deep-link
- Debtors / Active Debtors / Creditors filters
- Balance / turnover / profitability / credit-days sorts
- accounting-derived product relevance and BI measures

### Adaptive card layouts

No accounting:
ABC TRADERS
Last activity · 12 Aug
                                      Call  WhatsApp

Accounting connected:
ABC TRADERS                         ₹84,250 Dr
Sale ₹27,450 · 12 Aug    Payment ₹40,000 · 09 Aug
                                      Call  WhatsApp

Do not show empty accounting placeholders for non-accounting users.

### Identity
- Stable internal Party ID must exist independently of Tally/accounting.
- Party may originate manually, from Vartalap, Catalogue/Profile, referral, prospect creation, or accounting sync.
- Later accounting sync enriches/links the existing Party.

### Filters
Always-capable:
- All
- Prospects
- geography
- business type
- tags/groups
- product/service relevance

Accounting-capable:
- Debtors
- Active Debtors
- Creditors

Only show filters that are meaningful with available data.

### Sort
Always-capable:
- A–Z
- Z–A
- Last BUDCOM activity when available

Accounting-capable:
- High Turnover
- Most Profitable
- Account Balance
- Credit Days
- accounting-defined Last Activity

### Testing matrix
Every Connect UI milestone must be tested in:
A. No accounting source configured.
B. Accounting source configured.
C. Accounting source temporarily unavailable but previously synced.
D. Prospect/manual party later matched to accounting ledger.

## Permanent product rule

BUDCOM must feel complete for a home baker or independent artisan who has never used accounting software, while becoming richer—without becoming a different app—when accounting is connected.
