# VENTURE — Connect Implementation Checklist

Status: IMPLEMENTATION-READY BASELINE
Scope: Post-MVP-1 Connect, beginning with Quiet/Normal VENTURE
Authority: VENTURE-AUTHORITATIVE-UX-UI-SPECIFICATION-v1.docx

## Execution order

1. Connect Screen 1 — Contacts list (Quiet/Normal VENTURE)
2. Connect Screen 2 — Open Contact
3. Edit Contact / Tally-field enrichment
4. Filter / Sort / Branch tree
5. Active Debtor configuration
6. Tally-group import + VENTURE product-relevance refinement
7. R / V / Importance visual/control layer
8. Referral Tree entry/detail
9. Notes / Dincharya handoff
10. BI overlays for Connect
11. States/failures/accessibility/performance regression
12. Final Connect freeze

---

## Screen 1 — Contacts list

### Locked content
- Header: Connect / Contacts.
- Search is separate.
- Immediately below Search:
  - Left control displays CURRENT FILTER STATE, not the word “Filter”.
  - Right control displays CURRENT SORT STATE, not the word “Sort”.
  - Initial example: `All ▾        A–Z ▾`
  - Filtered example: `Active Debtors +2 ▾        Last Activity ▾`
- Search operates within the active filter by default.
- Provide an easy “Search all contacts” escape when appropriate.
- Active filter state must always remain visually obvious.

### Smart shortcut
- One compact line only; no heading such as Recent/Frequent/Smart.
- Example: `ABC Traders        +  ⌄`
- Party name itself behaves like the normal party.
- Expand control reveals up to 15 NORMAL Connect party cards.
- Selection formula is hidden from the user.
- Formula direction:
  - strongest emphasis on recent CTA use,
  - then CTA frequency,
  - then party-opening frequency,
  - distinct parties only,
  - stable ranking/hysteresis to avoid constant reordering.

### Default party card
- Party name: strongest text, left.
- Balance + Dr/Cr: strongest financial value, right.
- Secondary business line:
  - Last Sale value + date
  - Last Payment value + date
- Direct CTAs:
  - Call
  - WhatsApp
- No phone number displayed by default.
- R / V / Importance are available through customization and/or BI, but do not become generic CRM badges.
- Tags/groups are not shown by default.
- Avoid decorative card chrome; use restrained spacing/dividers.
- Quiet Mode: near-neutral, eye-friendly, information-dense but comfortable.

Example information hierarchy:

ABC TRADERS                         ₹84,250 Dr
Sale ₹27,450 · 12 Aug    Payment ₹40,000 · 09 Aug
                                      Call  WhatsApp

### Fixed navigation
- Permanent bottom navigation:
  - A/c Data
  - Connect
  - Vartalap
- Fixed order.
- Small Home control remains available according to global navigation contract.
- My Business is NOT on Home/bottom navigation; it lives under Vartalap → Profile.

### Interaction/state
- Tap party body → Open Contact.
- Tap Call/WhatsApp → act immediately when a valid default exists.
- Returning from external Call/WhatsApp restores exact Connect state.
- Preserve:
  - filter,
  - sort,
  - search context where appropriate,
  - scroll position,
  - smart-row expanded/collapsed state.
- Do not reorder visible rows under the user during sync unless required by an explicit user action or meaningful refresh.
- Cached/local data remains usable offline.
- One screen-level freshness state only; no per-row sync timestamps.

### Accessibility
- Large Android text must not cause name/balance/CTA collision.
- Tap targets remain comfortable even if icons are visually small.
- Dr/Cr meaning must not rely only on color.
- Long names/numbers truncate/wrap gracefully.
- Screen reader order: party name → balance → sale/payment → Call → WhatsApp.

### Performance
- Screen should open from local data near-instantly/sub-second where practical.
- Paged/indexed list.
- No expensive per-row calculations.
- Smart shortcut maintained incrementally; do not scan full history on every open.
- Idle CPU near zero.
- Stable memory under long scrolling.

### Explicitly out of scope for Screen 1
- Orders / Enquiries / Estimates workflows.
- Full Insights analytics.
- Profitability tables.
- Referral Tree visualization.
- Voucher/outstanding shortcuts.
- Send Ledger shortcut.
- Full BI cards.
- New accounting capabilities.
- Navigation redesign.

---

## Screen 2 — Open Contact

Locked first viewport:
- Party Name
- optional R / V / Importance when enabled
- Balance + date
- Last Sale value + date
- Last Payment value + date
- Bottom action row:
  - Left: `Ledger ›`
  - Right: Call | WhatsApp | Vartalap
- Ledger is the ONLY accounting deep-link from Connect.
- No separate Vouchers, Outstanding, or Send Ledger links.

Lower sections are progressively disclosed and should include:
- Tally-backed contact/business details
- additional numbers/roles
- groups/tags
- relationship/importance/referral entry points
- Notes / Dincharya handoff

---

## Contact identity / numbers

- Stable internal Party ID.
- Initial identity evidence: Ledger name + mobile + WhatsApp where available.
- First Mobile/WhatsApp number = Owner + Default automatically.
- `+` beside Mobile/WhatsApp adds another number.
- Additional role tags:
  - Manager
  - Accountant
  - Purchase
  - Salesman
  - Dispatch
  - Office
  - Other
- Name for non-owner role becomes available but optional.
- Role and default action target remain separate.
- Defaults can later be changed by owner.
- Do not auto-merge separate businesses solely because the same phone number appears.
- Matching uses normalized phone/WhatsApp plus accounting identity and later GSTIN/business evidence.
- Uncertain merges: Link / Keep Separate / Review Later.

---

## Filters / branches

Core:
- All
- Debtors
- Active Debtors
- Creditors
- Prospects

Hierarchical dimensions:
- Geography: State → District → City → Area
- Business type: Dealer / Retailer / Builder / Plumber / Contractor / etc.
- Product relevance:
  - import Tally groups first,
  - preserve them,
  - suggest VENTURE refinements,
  - owner approves,
  - derive party relevance automatically from transaction history.

Rules:
- Parent selection includes all descendants.
- Separate dimensions intersect.
- Example: Active Debtors + Telangana + Dealer + Supreme.

---

## Active Debtor defaults

Recommended baseline accepted:
- Activity window: 90 days
- Re-evaluation: 1st day of every month
- Alternatives: 30 / 45 / 90 / 365 days; every Monday / monthly / manual
- Minimum lifetime turnover baseline: ₹20,000
- Profitability exception where reliable: keep/suggest active if gross margin >= 20%
- Intelligent exceptions may consider importance, referral value, seasonality, reactivation, and other validated evidence.
- Party never disappears from Connect because of this classification.
- Suggestions are advisory and dismissible.

---

## Sort defaults

Available:
- A–Z
- Z–A
- High Turnover
- Most Profitable
- Last Activity
- Account Balance
- Credit Days

Default period for turnover/profitability:
- Current Financial Year
Other selectable periods:
- Last 12 months
- Lifetime
- Custom

---

## Governance

- No new feature should be added during implementation unless it solves a demonstrated usability or correctness gap.
- Claude/Cursor must not reinterpret module ownership.
- Quiet/Normal VENTURE first; BI overlay later.
- Reuse existing A/c Data Ledger, share, WhatsApp and sync infrastructure wherever applicable.
- Implementation must pass tests, lint, accessibility checks, large-data scrolling checks, offline/stale states, and state-restoration checks before Screen 1 is frozen.
