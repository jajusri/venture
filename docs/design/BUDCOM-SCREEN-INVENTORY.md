# BUDCOM Screen Inventory

**Status:** Active — reflects `apps/budcom_android` source as of this consolidation
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
| Dashboard | `dashboard/presentation/DashboardScreen.kt` | **To be redesigned** — becomes the A/c Data Home per §3 of the design-decisions file (company context, permanent Universal Search, compact sync state, Vouchers/Ledgers as primary entries, integrated Insights surface). |
| Master Data Hub | `masterdata/presentation/MasterDataHubScreen.kt` | **To be redesigned** — Stock Items must not occupy prime Home space per §3; hub role likely narrows once Vouchers/Ledgers move to Home-level prominence. |
| Ledger Browser | `masterdata/ledger/presentation/LedgerBrowserScreen.kt` | Unchanged for now — not named in the locked decisions; local-first Ledger architecture (PDL-003) is unaffected regardless of presentation changes elsewhere. |
| Ledger Statement | `masterdata/ledger/presentation/LedgerStatementScreen.kt` | Unchanged for now. |
| Stock Item Browser | `masterdata/stockitem/presentation/StockItemBrowserScreen.kt` | Unchanged for now — see Master Data Hub note on prominence. |
| Voucher Browser | `voucher/presentation/VoucherBrowserScreen.kt` | **To be redesigned** — becomes the Unified Vouchers window per §5 (all voucher types, default filter All, compact horizontal type filters, one canonical collection). |
| Voucher Details | `voucher/presentation/VoucherDetailsScreen.kt` | Unchanged for now. |
| Universal Search | `search/presentation/UniversalSearchScreen.kt` | **To be redesigned** — moves to a permanent position directly below company context on Home per §3; also named as a possible entry point into another user's Business Profile per §7. |
| Company Selection | `company/presentation/CompanyScreen.kt` | Unchanged for now. |
| Connector Discovery | `discovery/presentation/ConnectorDiscoveryScreen.kt` | Unchanged for now. |
| Secure Pairing | `pairing/presentation/SecurePairingScreen.kt` | Unchanged — pairing/credentials are explicitly out of scope for presentation-layer UI work (see design-decisions file §8 and this consolidation task's boundaries). |
| Server Config | `serverconfig/presentation/ServerConfigScreen.kt` | Unchanged for now. |
| Sync | `sync/presentation/SyncScreen.kt` | Unchanged for now — may gain a compact Home-level summary per §3 without the full screen changing. |
| Diagnostics | `diagnostics/presentation/DiagnosticsScreen.kt` | Unchanged for now. |
| Settings | `settings/presentation/SettingsScreen.kt` | Unchanged for now. |

## Screens implied by locked decisions but not yet built

| Screen | Implied by | Notes |
|---|---|---|
| Home Insights (All Insights / Suggested Actions / Next-Previous sequence) | §4 | Navigation shape only is locked; detailed Insights content/computation belongs to the dedicated Insights workstream, not this inventory. |
| Day at a Glance | §6 | Distinct from Daybook and Insights — do not conflate during implementation. |
| Daybook | §6 | Distinct from Day at a Glance and Insights. |
| My Business (Profile / Catalogue / Business Library) | §7 | Owner-side Business Profile Ecosystem shell — maps to roadmap MVP-1.3 (Business Profile) and MVP-1.4 (Catalogue) in `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`. Not an MVP-1.0.x item. |
| Resources (visitor-facing Business Library view) | §7 | Permission-controlled view over the same owner-side data — must not fork a second data copy. |
| Connect | Bottom tab, §3 | Maps to roadmap MVP-1.1 (Connect with Universal Party Identity). Not an MVP-1.0.x item. |
| Vartalap | Bottom tab, §3 | No dedicated roadmap milestone yet in `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` as of this consolidation — scope pending. |

## Maintenance rule

Update this table when a screen is added, removed, or its relationship to
the locked direction changes (e.g. "to be redesigned" → actually redesigned).
Do not let it drift silently out of sync with source — a stale inventory is
worse than none, because it will be trusted.
