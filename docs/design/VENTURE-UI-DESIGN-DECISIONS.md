# VENTURE UI Design Decisions

**Status:** Active — locked visual direction, screen-level decisions pending further exploration
**Scope:** Presentation-layer only unless separately approved. A decision recorded here changes how something looks or is arranged, not what VENTURE does, unless explicitly marked otherwise.
**Governance:** `POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`, `VENTURE-MASTER-PRODUCT-EXECUTION-PLAN.md` (roadmap sequencing), `VENTURE-PRODUCT-DECISION-LOG.md` (product-scope decisions)
**Companion file:** `docs/design/VENTURE-SCREEN-INVENTORY.md`

## 1. Purpose

This is the authoritative planning home for the approved visual direction —
the locked character, layout, and interaction decisions the Product Owner and
ChatGPT reached in the product/design/UI planning session. It exists so a
future Claude implementation session can find the visual truth without
searching chat history.

This file does **not** contain the formal design system
(`VENTURE-VISUAL-DESIGN-SYSTEM.md`). That file is intentionally not created
yet — it should be produced after more screen exploration, once enough
screens have been walked through to generalize tokens/components safely.

## 2. Locked visual character

**Cutting-edge minimalism + premium business software + dense professional
instrument + one-handed Android ergonomics.**

- **Light mode:** white / very-light neutral canvas.
- **Dark mode:** black / near-black canvas.
- **Color:** used for information, state, selection, and action — never
  decoration. If a color doesn't communicate something, it shouldn't be
  there.
- Dense does not mean cluttered: information density is earned through
  disciplined layout and typography, not by shrinking touch targets or
  removing whitespace that separates meaning.
- Every primary screen must remain comfortably operable one-handed on a
  typical Android phone.

## 3. A/c Data Home

- Company context at top.
- Permanent Universal Search directly below company context.
- Compact Fresh / last-sync / Tally-connected state.
- Compact Sync / Refresh action.
- Vouchers and Ledgers are the primary Home entries.
- Stock Items does **not** occupy prime Home space.
- Integrated Home Insights surface (see §4 — summary only; detailed
  Insights/OI/value-attribution design is out of scope here, see §8).
- Bottom tabs: **A/c Data / Connect / Vartalap.**

## 4. Home Insights (navigation shape only)

This section defines the navigation/layout contract for the Home Insights
surface. It does not define what an insight is, how it's computed, or its
value-attribution model — that belongs to the dedicated Insights workstream
(§8).

- Header: "Insights" left, "All Insights →" right.
- First insight view: "Suggested Actions →" left, "Next >" right.
- Middle views: "< Previous" left, "Next >" right.
- Last view: "< Previous" only.
- Any number of middle insight views may be inserted without changing this
  contract.
- The Home viewport remains fixed — Insights must not push other Home
  content around as the count of insights changes.
- Do not manufacture insights merely for engagement. An empty or
  minimal-insight state is preferable to a fabricated one.

## 5. Unified Vouchers

- All supported accounting voucher types appear in one chronological
  Vouchers window — no separate windows per voucher type.
- Default filter = **All**.
- Compact horizontal filters: All / Sales / Purchase / Receipt / Payment /
  etc.
- Filters scroll horizontally if they don't fit.
- Dense professional rows.
- One canonical underlying voucher collection — filters change what's
  displayed, not what's fetched/stored separately.

## 6. Day at a Glance / Daybook / Insights — distinct purposes

These three surfaces are easy to conflate. They are deliberately distinct:

- **Insights** — what matters, what changed, what deserves attention.
- **Day at a Glance** — a concise, today-oriented business/accounting
  picture.
- **Daybook** — the detailed chronological transaction/activity view.

Do not merge these into one surface or let one silently absorb another's
responsibility.

## 7. Business Profile Ecosystem

**Core rule:** one business identity, one catalogue, and one asset library —
multiple permission-controlled views.

**Owner side:**

```
VENTURE global shell → My Business
  - Profile
  - Catalogue
  - Business Library
```

**Other-user access can originate from:**

- Universal Search
- Connect
- Vartalap business identity
- QR / deep link
- shared product/catalogue/resource link

Visitor-facing Business Library content may be called **Resources**.

**Ownership boundary:** Vartalap and Connect may consume/reference this
business-identity/catalogue/library data. They do not own it. Do not let a
future Vartalap or Connect implementation fork a second copy of business
identity, catalogue, or asset data — there is exactly one authoritative
source, exposed through permission-controlled views.

This is directly relevant to sequencing: MVP-1.1 (Connect) and MVP-1.3/1.4
(Business Profile / Catalogue) in `VENTURE-MASTER-PRODUCT-EXECUTION-PLAN.md`
must respect this ownership boundary from the first implementation, not
retrofit it later.

## 8. Explicit boundaries

- **Presentation-layer only.** None of the above authorizes a new backend
  capability, sync path, or data model on its own. Where a decision here
  implies new capability (e.g. Business Profile, Catalogue), that capability
  is scoped and approved through the normal roadmap process in
  `VENTURE-MASTER-PRODUCT-EXECUTION-PLAN.md`, not by this file.
- **Insights detail is out of scope here.** The Product Owner has a separate,
  dedicated Insights planning workstream. This file preserves only the
  cross-product references necessary for Home/UI architecture (§4). Detailed
  Insights/OI/value-attribution design must be written there, not here.
- **External design archive stays outside the repository.** Approved visual
  references and raw Stitch explorations live at
  `D:\VENTURE-Design-Archive\` (`00_README_AND_DECISIONS`,
  `01_APPROVED_MASTERS`, `02_APPROVED_NEXT_SCREENS`,
  `03_STITCH_EXPORTS_RAW`, `04_SUPERSEDED_EXPLORATIONS`,
  `05_IMPLEMENTATION_HANDOFF`), outside Git. This file may reference that
  archive conceptually; it must never be copied into the repository. Keep
  Git high-signal.
- **Process note (Claude economics):** resolve visual direction and screen
  exploration via ChatGPT + the external design archive (Stitch
  explorations, approved masters) before Claude implements. Do not spend
  premium Claude capacity on open-ended visual/UI exploration — see
  `POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` §8.

## 9. Status of the formal design system

Not created yet, by design. `VENTURE-VISUAL-DESIGN-SYSTEM.md` (tokens,
component specs, spacing/type scale) should be authored once more screens
have been walked through in the external design archive and a stable pattern
set has emerged — premature systemization would lock decisions before enough
real screens have tested them.
