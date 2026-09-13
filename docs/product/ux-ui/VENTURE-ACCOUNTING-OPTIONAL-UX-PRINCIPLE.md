# VENTURE — Accounting-Optional Product & UX Principle

Status: LOCKED PRODUCT REQUIREMENT
Date: 14 August 2026
Applies to: Entire VENTURE product, including Home, Connect, Vartalap, Dincharya, My Business, Insights and BI Mode.

## Core requirement

VENTURE must remain useful and understandable even when the user does not use, connect, or possess accounting software.

The product is intended to serve the full spectrum of small economic actors and businesses: individuals working from home, tiny/micro/mini businesses, home bakers, home stitchers/tailors, artists, designers, independent service providers, traders, retailers, wholesalers, and larger businesses.

Accounting integration is therefore an OPTIONAL ENRICHMENT LAYER, not a prerequisite for product usefulness.

## Product model

### Normal VENTURE
Normal VENTURE must work without accounting software.

Core value must come from:
- contacts and business relationships,
- Call / WhatsApp / Vartalap,
- notes and follow-ups,
- Dincharya,
- enquiries / estimates / orders in Vartalap,
- Business Profile and Catalogue under My Business,
- groups/tags/geography/product relevance,
- simple user-entered or organically generated business context,
- referral/network relationships,
- lightweight non-accounting Insights where reliable data exists.

### Accounting-connected VENTURE
When Tally or another approved accounting source is connected, VENTURE progressively enriches the same canonical parties and workflows with:
- balance,
- Dr/Cr,
- last sale,
- last payment,
- Ledger,
- voucher-derived facts,
- return ratios,
- profitability/contribution where reliable,
- credit/payment behaviour,
- accounting-backed product/customer affinity,
- advanced accounting-linked Insights.

Accounting data must extend existing objects; it must not create a second incompatible product experience.

### BI Mode
BI Mode may expose deeper accounting-connected analysis when accounting data exists.

However:
- BI Mode must not require accounting software to exist.
- Non-accounting users may still receive useful BI based on contacts, Vartalap, orders, enquiries, estimates, catalogue interactions, tags/groups, referral relationships, Dincharya outcomes and other reliable VENTURE-native data.
- Accounting-specific BI components appear only when their required source data exists.
- Missing accounting data must never produce fake estimates or misleading empty accounting widgets.

## Progressive capability rule

A screen should show only capabilities supported by the user's available data.

Example — Connect party card:

### Without accounting
ABC TRADERS
Last Contact · 12 Aug
Call    WhatsApp

### With accounting connected
ABC TRADERS                         ₹84,250 Dr
Sale ₹27,450 · 12 Aug    Payment ₹40,000 · 09 Aug
Call    WhatsApp

The same party identity and same card architecture remain; accounting information is an additive layer.

## Terminology rule

Avoid accounting-only wording in places intended for all users.

Examples:
- Prefer "Contacts" / "Parties" over "Debtors" as the default universal view.
- "Debtors", "Creditors", "Balance", "Ledger", etc. appear only when accounting data exists or the user explicitly uses those concepts.
- Filters unsupported by current data remain hidden or unavailable rather than showing meaningless zero-state categories.

## Connect rule

Connect remains a universal business Contacts system.

Without accounting:
- Name
- Mobile / WhatsApp
- Call / WhatsApp / Vartalap
- tags/groups
- location/geography
- business type
- product/service relevance
- notes / Dincharya
- referral relationships
- importance/relationship markers when enabled

With accounting:
- Balance/date
- Last Sale/date/value
- Last Payment/date/value
- Ledger deep-link
- accounting-backed filters/sorts and BI enrichment

## Filter and sort adaptability

Filters and sorts are capability-aware.

Always-available examples:
- All
- Prospects
- geography
- business type
- tags/groups
- product/service relevance
- A–Z / Z–A
- last interaction/activity where VENTURE-native activity exists

Accounting-dependent examples:
- Debtors
- Active Debtors
- Creditors
- account balance
- turnover
- profitability
- credit days
- accounting-defined last sale/payment

Unsupported options must not clutter the UI.

## Identity rule

Universal Party Identity must not depend on an accounting ledger existing.

A party can originate from:
- manual Contact/Prospect creation,
- Vartalap,
- Catalogue/Profile interaction,
- referral,
- later accounting synchronization.

If a matching accounting ledger appears later, VENTURE links/enriches the existing Party rather than replacing it.

## Onboarding rule

Do not ask a small non-accounting user to connect accounting software before VENTURE becomes useful.

Accounting connection should be offered contextually as:
"Connect accounting software for balances, ledger and deeper business insights."

It is an optional upgrade in capability, not a setup gate.

## UX rule

No screen may look broken or incomplete merely because accounting is absent.

If accounting-dependent fields are unavailable:
- remove them cleanly,
- rebalance the layout,
- allow VENTURE-native information to occupy the space,
- never leave dead placeholders such as "Balance --" / "Ledger unavailable" on every contact.

## Product principle

> VENTURE starts as a useful business companion for the smallest user and grows into a deeply integrated Business OS as the user's data and business sophistication grow.

## Acceptance test

Every major screen must be reviewed in two data conditions:
1. VENTURE-only / no accounting integration.
2. Accounting-connected.

The screen must remain coherent, useful and visually balanced in both.
