# BUDCOM Screen Inventory

**Status:** Active — reflects `apps/budcom_android` source as of the MVP-1.3 planning/recovery review
(2026-08-18). **Corrected this session:** the "Current screens" table had gone stale since before
MVP-1.1-B — it omitted five screens shipped and frozen since (Connect, Party Detail, Prospect Create,
Party XML Export, Dincharya), which this file's own "Maintenance rule" (below) explicitly warns
against ("a stale inventory is worse than none, because it will be trusted"). No visual/architectural
claim in this file was changed, only the missing rows added.
**Purpose:** Map every currently implemented Android screen to its relationship with the locked visual direction in `BUDCOM-UI-DESIGN-DECISIONS.md`, so future implementation work knows what's unchanged, what's superseded, and what's net-new before touching source.
**Companion file:** `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md`

## How to read this table

- **Unchanged** — keeps its current structure; new visual direction doesn't
  redefine it.
- **To be redesigned** — the new locked direction explicitly redefines this
  screen's structure (presentation-layer change only, per the design-decisions
  file's boundary).
- **New (not yet built)** — implied by locked decisions but has no current
  screen; scoped through the normal roadmap process, not built from this
  inventory alone.

## Current screens (`apps/budcom_android/app/src/main/java/com/budcom/android/feature/*/presentation/`)

| Screen | Source file | Relationship to new direction |
|---|---|---|
| Dashboard | `dashboard/presentation/DashboardScreen.kt` | **Partially redesigned** — the MVP-1 UI/UX polish pass (`docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md` §2) added the A/c Data Home header, permanent Universal Search entry, compact sync/Tally-connected status row, and Vouchers/Ledgers primary entries per §3, reconciled against the approved Stitch master `BUDCOM-AC-DATA-HOME-MASTER.png`, additively above the existing detail cards (none removed). The Insights surface and Connect/Vartalap bottom tabs remain **not yet built** — no backing data/destinations exist; still belongs to the dedicated Insights workstream. |
| Master Data Hub | `masterdata/presentation/MasterDataHubScreen.kt` | **To be redesigned** — Stock Items must not occupy prime Home space per §3; hub role likely narrows once Vouchers/Ledgers move to Home-level prominence. |
| Ledger Browser | `masterdata/ledger/presentation/LedgerBrowserScreen.kt` | Unchanged for now — not named in the locked decisions; local-first Ledger architecture (PDL-003) is unaffected regardless of presentation changes elsewhere. |
| Ledger Statement | `masterdata/ledger/presentation/LedgerStatementScreen.kt` | Unchanged for now. |
| Stock Item Browser | `masterdata/stockitem/presentation/StockItemBrowserScreen.kt` | Unchanged for now — see Master Data Hub note on prominence. |
| Voucher Browser | `voucher/presentation/VoucherBrowserScreen.kt` | **Implemented** — unified chronological list (no per-type screens) with LEFT/CENTER/RIGHT row layout (UIP-001) and, as of `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md` §3, the compact horizontal All/Sales/Purchase/… type-filter strip per §5 — the strip was found missing during that pass despite UIP-001 being marked closed, and has now been built. |
| Voucher Details | `voucher/presentation/VoucherDetailsScreen.kt` | Unchanged for now. |
| Universal Search | `search/presentation/UniversalSearchScreen.kt` | **To be redesigned** — moves to a permanent position directly below company context on Home per §3; also named as a possible entry point into another user's Business Profile per §7. |
| Company Selection | `company/presentation/CompanyScreen.kt` | Unchanged for now. |
| Connector Discovery | `discovery/presentation/ConnectorDiscoveryScreen.kt` | Unchanged for now. |
| Secure Pairing | `pairing/presentation/SecurePairingScreen.kt` | Unchanged — pairing/credentials are explicitly out of scope for presentation-layer UI work (see design-decisions file §8 and this consolidation task's boundaries). |
| Server Config | `serverconfig/presentation/ServerConfigScreen.kt` | Unchanged for now. |
| Sync | `sync/presentation/SyncScreen.kt` | Unchanged for now — may gain a compact Home-level summary per §3 without the full screen changing. |
| Diagnostics | `diagnostics/presentation/DiagnosticsScreen.kt` | Unchanged for now. |
| Settings | `settings/presentation/SettingsScreen.kt` | Unchanged for now. |
| Connect (Customers/Prospects) | `connect/presentation/ConnectScreen.kt` | **Implemented (MVP-1.1-B).** Not yet reflected in this table until the MVP-1.3 planning/recovery review (2026-08-18) — a stale-inventory gap, now corrected. Third Dashboard primary entry (`HomePrimaryEntryRow`), the established precedent Dincharya (below) and any future Business Profile entry (MVP-1.3) replicate. |
| Party Detail | `connect/presentation/PartyDetailScreen.kt` | **Implemented (MVP-1.1-C, extended through MVP-1.2-D/E).** Relationship Timeline, Issues section, contact/tag/note management, note completion (Mark done/Reopen, MVP-1.2-E). Reached from any Connect row or a Dincharya item tap. |
| Prospect Create | `connect/presentation/ProspectCreateScreen.kt` | **Implemented (MVP-1.1-C).** Minimal offline Prospect-creation flow, reached via a FAB on Connect's Prospects tab. |
| Party XML Export | `connect/presentation/PartyXmlExportScreen.kt` | **Implemented (MVP-1.1-D).** Change-review + Tally-compatible XML export, reached from Party Detail. |
| Dincharya | `dincharya/presentation/DincharyaScreen.kt` | **Implemented (MVP-1.2-D).** Deterministic, bounded, three-item-type worklist (Follow-ups/Callbacks, Pending Tally Confirmation, Pending Contact Completion). Fourth Dashboard primary entry, replicating Connect's own wiring exactly. Every row deep-links to Party Detail. |

## Screens implied by locked decisions but not yet built

| Screen | Implied by | Notes |
|---|---|---|
| Home Insights (All Insights / Suggested Actions / Next-Previous sequence) | §4 | Navigation shape only is locked; detailed Insights content/computation belongs to the dedicated Insights workstream, not this inventory. |
| Day at a Glance | §6 | Distinct from Daybook and Insights — do not conflate during implementation. |
| Daybook | §6 | Distinct from Day at a Glance and Insights. |
| My Business (Profile / Catalogue / Business Library) | §7 | Owner-side Business Profile Ecosystem shell — maps to roadmap MVP-1.3 (Business Profile) and MVP-1.4 (Catalogue) in `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`. Not an MVP-1.0.x item. |
| Resources (visitor-facing Business Library view) | §7 | Permission-controlled view over the same owner-side data — must not fork a second data copy. Scope (whether it ships in MVP-1.3 at all) is an open product decision — see `docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md` §5.5. |
| Vartalap | Bottom tab, §3 | No dedicated roadmap milestone yet in `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` as of this consolidation — scope pending. |

**Connect** (bottom tab, §3) is no longer "not yet built" — implemented since MVP-1.1-B, see the
"Current screens" table above. Row removed from this table accordingly.

## Maintenance rule

Update this table when a screen is added, removed, or its relationship to
the locked direction changes (e.g. "to be redesigned" → actually redesigned).
Do not let it drift silently out of sync with source — a stale inventory is
worse than none, because it will be trusted.
