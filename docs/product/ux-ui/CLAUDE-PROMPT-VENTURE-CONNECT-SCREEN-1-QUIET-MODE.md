# CLAUDE IMPLEMENTATION PROMPT — VENTURE CONNECT SCREEN 1 (QUIET/NORMAL MODE)

You are implementing a single bounded milestone in the VENTURE repository.

## Authority and scope

Read and follow:
1. `docs/product/ux-ui/VENTURE-AUTHORITATIVE-UX-UI-SPECIFICATION-v1.docx`
2. the Connect implementation checklist for this milestone.

Treat those as product authority. Do not reinterpret the navigation model, module ownership, Quiet/BI boundary, or accounting ownership.

This task is ONLY:
**Connect Screen 1 — Contacts List, Quiet/Normal VENTURE.**

Do not start Screen 2, BI overlays, Vartalap commercial workflows, Insights, Referral Tree visualization, or new accounting features.

## Before changing code

1. Report current HEAD.
2. Report pre-existing git status.
3. Inspect the current Android app architecture and identify:
   - existing Connect/Contacts screens/components if any,
   - current ledger/party/domain models,
   - balance/last-sale/last-payment data already available,
   - existing Call and WhatsApp launchers,
   - current bottom navigation,
   - current Home navigation control,
   - existing search/filter/sort infrastructure,
   - current sync/freshness state.
4. Reuse existing proven components and contracts wherever possible.
5. Do not create a duplicate ledger/accounting source of truth.
6. If required data is not currently available, stop and report the narrowest missing data contract before inventing a parallel schema.

## Screen to implement

### Header/control area

- Screen title: Connect / Contacts according to the existing app visual language.
- Search remains a distinct search field.
- Immediately below Search:
  - LEFT control shows CURRENT FILTER STATE, never merely the label `Filter`.
  - RIGHT control shows CURRENT SORT STATE, never merely the label `Sort`.
- Initial:
  `All ▾                        A–Z ▾`
- Example after user changes state:
  `Active Debtors +2 ▾         Last Activity ▾`
- Search applies within the active filter by default.
- When useful, provide a low-friction way to broaden search to All Contacts.
- Filter state must always be visually obvious.

Do not build the entire advanced branch-filter editor in this milestone unless it already exists. Screen 1 must expose the state/control affordance and use whatever bounded filter set can be safely supported now. Report any deeper filter UI as a subsequent milestone rather than expanding scope.

### Smart expandable party shortcut

At the top of the contact list, below controls:

- Show ONE compact party line only.
- No heading.
- No explanation like Recent, Frequent, Smart Shortcut, Most Used, CTA, etc.
- Format conceptually:
  `ABC Traders                         +  ⌄`
- Tapping party name/body opens the same party destination as a normal contact.
- Tapping expand reveals up to 15 normal Connect party cards.
- Expanded cards must use the exact same component/behaviour as the normal contact list.

Ranking direction:
- recent CTA use has strongest weight,
- CTA frequency second,
- party opening frequency third,
- distinct parties only,
- maintain stability/hysteresis so minor score changes do not constantly reorder results.

If reliable usage telemetry/counters do not yet exist, do NOT build a large event-tracking system in this task. Implement the UI/data interface cleanly and report the narrowest follow-up needed for real ranking.

### Normal party card

Quiet/Normal VENTURE default visual hierarchy:

- Party Name — strongest text, left.
- Balance + Dr/Cr — strongest financial value, right.
- Secondary line:
  - Last Sale value + date
  - Last Payment value + date
- Direct CTAs:
  - Call
  - WhatsApp
- Phone number itself is NOT displayed by default.
- R, V and Importance are NOT required on default Quiet Screen 1 unless they already exist cleanly behind customization. Do not invent generic status badges.
- Tags/groups are NOT shown by default.
- Avoid heavy decorative cards, gradients, excessive shadows, CRM pills, or colourful labels.
- Preserve the earlier clean VENTURE white/light visual language: calm, near-neutral, information-dense but comfortable.
- Use color only where semantically justified; Dr/Cr must also remain understandable without relying solely on color.

Conceptual layout:

ABC TRADERS                         ₹84,250 Dr
Sale ₹27,450 · 12 Aug    Payment ₹40,000 · 09 Aug
                                      Call  WhatsApp

Use the project's approved iconography rather than literal text labels if that is already the standard, but accessible semantics must remain explicit.

### Navigation

Permanent bottom navigation remains exactly:
`A/c Data | Connect | Vartalap`

- fixed order,
- no extra permanent tab,
- no My Business tab,
- no Insights tab,
- no Dincharya tab.

Keep the small Home access consistent with the existing/frozen navigation architecture.

### Interaction behaviour

- Tap party card body → open party/contact destination if available; if Screen 2 does not yet exist, preserve a clean bounded placeholder or existing destination rather than implementing Screen 2 now.
- Tap Call:
  - if one valid/default number exists, act immediately;
  - no unnecessary chooser.
- Tap WhatsApp:
  - reuse existing validated recipient resolution and WhatsApp infrastructure;
  - if one valid/default number exists, act immediately;
  - never auto-send.
- External-app return must restore the same Connect list state.
- Preserve:
  - active filter,
  - sort,
  - search state when appropriate,
  - scroll position,
  - smart shortcut expanded/collapsed state.
- Do not reset the user to the top after returning from Call/WhatsApp.
- Do not reorder the visible list under the user's finger during background sync.

### Freshness/offline

- One screen-level freshness/offline state only.
- Do not repeat sync timestamps on every row.
- Cached/local contacts remain usable offline.
- Syncing should not blank the screen or block scrolling.
- Failed sync must preserve last trustworthy data.

### Accessibility

Validate:
- Android text scaling.
- long party names.
- long financial values.
- Dr/Cr readability without color alone.
- touch targets.
- Call/WhatsApp accessibility labels.
- screen-reader order.
- no CTA collision at larger font size.

### Performance

Required:
- use local cached/indexed data;
- paged/lazy contact loading where appropriate;
- no expensive per-row calculations;
- no full-history scan every time Screen 1 opens;
- preserve smooth large-list scrolling;
- idle CPU should remain near idle;
- no unnecessary background work merely because Connect is visible.

## Out of scope

Do NOT implement in this task:
- Screen 2 full Contact Detail redesign.
- Orders / Enquiries / Estimates.
- Vartalap commercial workflow.
- Insights / BI cards.
- R/V scoring algorithms.
- Referral Tree.
- Active Debtor advanced settings UI.
- full hierarchical branch editor.
- product-affinity engine.
- new accounting reports.
- database migration unless absolutely required by the narrow Screen 1 contract and explicitly justified before implementation.
- version bump, installer build, device install, push, or release unless separately requested.

## Testing

Add/update focused tests for:
- filter-state label,
- sort-state label,
- search inside active filter,
- contact row data formatting,
- Call/WhatsApp action routing,
- default recipient behavior,
- offline/cached state,
- state restoration after external action,
- smart shortcut collapsed/expanded behavior,
- no duplicate parties in shortcut list,
- long-name/large-font resilience where test infrastructure supports it.

Run:
- focused unit/widget/UI tests,
- relevant Android unit test suite,
- lint,
- any existing static analysis required by the repo.

Do not modify unrelated failing code merely to make the build green. Report unrelated pre-existing failures separately.

## Final report format

Return:

A. Pre-change HEAD
B. Pre-existing git status
C. Existing components/contracts reused
D. Missing data contracts discovered, if any
E. Exact files modified
F. Exact UI behavior implemented
G. Smart shortcut implementation status and formula/data limitations
H. Search/filter/sort behavior
I. Call/WhatsApp behavior
J. Offline/freshness behavior
K. Accessibility validation
L. Performance validation
M. Tests added/updated
N. Focused test results
O. Regression/lint results
P. Exact staged set
Q. Commit SHA (only if instructed to commit)
R. Final git status
S. Residual risks / intentionally deferred items

Do not start another task after the report.
