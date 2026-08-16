# BUDCOM MVP-1 UI/UX Polish — Status

**Status:** AUTOMATED WORK COMPLETE — Android human visual approval complete; Desktop human
visual approval pending
**Scope:** Bounded MVP-1 UI/UX polish pass, post-Controlled-Pilot. Presentation-layer only — see
`docs/design/BUDCOM-UI-DESIGN-DECISIONS.md` for the separately-governed Home/Insights/Business-Profile
direction, which this pass does **not** implement beyond the one exception noted in §2.
**Companion files:** `docs/design/BUDCOM-SCREEN-INVENTORY.md`,
`docs/planning/BUDCOM-MVP-1-0-X-REFINEMENT-REGISTER.md`

**Owner review update (2026-08-17):** The owner physically inspected the newly installed Android
`0.1.1-continuity.21` (versionCode 22) UI/UX-polished candidate and visually approved the Android
pass. This is owner physical/visual review evidence; it does not claim that every instrumented test
was rerun and does not establish Desktop visual approval. Desktop `0.4.16` remains pending the
visual checklist in §10.

## 1. Method

Three parallel read-only audits covered every screen named in the operating brief: Android
shell/Dashboard/Company/Sync/Settings/Diagnostics/Search, Android
Ledger/Voucher/PDF-preview/Save-Share, and the Desktop renderer/tray/status surface. Findings were
graded P0 (blocks release usability or contradicts an already-approved decision) / P1 (should fix
before public release) / P2 (desirable polish) / DEFER (post-MVP or too architecturally heavy for a
bounded pass), then the highest-value, lowest-risk P0/P1 items were implemented directly, verified
by compiling, and re-tested. This document is the record of what was found, what changed, and what
was deliberately left alone.

## 2. Landing-page reference reconciliation (added mid-run)

The user identified a previously-produced Google Stitch reference image as the approved visual
authority for the Android landing page, saved outside the repository at
`D:\BUDCOM-Design-Archive\01_APPROVED_MASTERS\AC_DATA_HOME\BUDCOM-AC-DATA-HOME-MASTER.png`, per the
storage plan at `D:\BUDCOM-Design-Archive\00_README_AND_DECISIONS\BUDCOM-UI-MASTER-STORAGE-PLAN.txt`.
That plan's "LOCKED DESIGN DECISIONS TO PRESERVE" section independently confirms the same layout
already recorded in `BUDCOM-UI-DESIGN-DECISIONS.md` §3.

**Reference image contents:** header (menu icon / centered company name / sync icon), a permanent
"Search parties, vouchers, stock items…" bar directly below the header, a compact one-line
Fresh/synced-time/Tally-connected status row with a "SYNC" action, two primary rows — Vouchers
("View synced vouchers") and Ledgers ("View account balances"), each icon + title + subtitle +
chevron — then an "Insights" section (Total Receivables, Cash in Hand, a 7-day sparkline, Overdue
Amount, Monthly Sales, "Suggested Actions →"), and a bottom tab bar (A/c Data · Connect · Vartalap).

**What was implemented on `DashboardScreen.kt`** (additive, nothing existing removed):
header switched to a centered company-name title with icon actions (Settings, Sync-now); a new
`HomeSearchEntry` row directly below the header that opens the existing Universal Search screen; a
new `HomeCompactStatusRow` composing already-computed state (`operationalMode`, `syncStatusLabel`,
`connectorConnected`) into one Fresh/Offline/…-plus-Tally-connected line with a Sync button; a new
`HomePrimaryEntries` section with Vouchers and Ledgers as icon+title+subtitle+chevron rows (Stock
Items deliberately absent from prime space, matching §3). Ledgers gained a new direct navigation
path (`DashboardEvent.OpenLedgers` → `Routes.ledgers()`) — the destination screen itself is
unchanged. All four existing detail cards (Connector status, Company/session, Sync, Quick actions)
and every existing test tag were left in place below the new section, so no existing capability,
navigation, or test coverage was removed — see `DashboardScreenTest.kt` /
`DashboardViewModelTest.kt`, both green.

**What was deliberately not built, per rule 8 ("do not invent missing product functionality merely
because it appears in the reference") and this pass's own hard boundary on Insights/Connect/Vartalap:**
- The Insights card and its financial figures (Total Receivables, Cash in Hand, sparkline, Overdue
  Amount, Monthly Sales, Suggested Actions). BUDCOM has no computation or data source for any of
  these today — `BUDCOM-UI-DESIGN-DECISIONS.md` §4 itself calls this navigation-shape-only and
  explicitly the responsibility of a separate Insights workstream, and
  `BUDCOM-ACCOUNTING-OPTIONAL-UX-PRINCIPLE.md` and §4 both say a fabricated/empty widget is worse
  than no widget. Building it would mean inventing numbers.
- The bottom tab bar (A/c Data / Connect / Vartalap). Connect and Vartalap have no destinations in
  this app; adding the bar would mean either dead tabs or building new navigation targets, both
  out of bounds.
- A hamburger/drawer menu for the header's menu icon — there is no drawer navigation model in the
  app today, and building one is new navigational infrastructure, not polish. The Settings action
  (already reachable pre-existing) took an icon-button treatment instead, visually consistent with
  the reference's icon-based header without inventing a new nav paradigm.

**Gaps that could not be reproduced, and why:** the sparkline/sales-figures widget's entire data
layer is missing (see above) — this is a product/data-source decision (what defines "Insights",
where the numbers come from), not a visual one, and belongs to the dedicated Insights workstream
per governance already on record.

**Readiness:** the implemented portion (header, search entry, compact status, Vouchers/Ledgers
primary rows) is ready for human visual comparison against the reference image. The Insights
section and bottom tab bar remain explicitly out of scope pending a product decision to promote
them off `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`'s roadmap into this app.

## 3. P0 — implemented

| Item | Where | What was wrong | Fix |
|---|---|---|---|
| Voucher type filter strip missing | `VoucherBrowserScreen.kt` | The operating brief's "preserve the approved Voucher list behavior" assumed a compact horizontal All/Sales/Purchase/… filter strip existed (per UIP-001/closed); the audit found only the LEFT/CENTER/RIGHT row layout had shipped — the filter strip itself was never built. Confirmed as approved in the external design archive's locked decisions ("Top filter treatment such as All \| Sales \| Purchase \| Receipt \| Payment … is approved"). | Added `VoucherTypeFilterStrip` (horizontally scrollable `FilterChip` row, "All" first and default) directly under the Vouchers header. Chips are derived from the types actually present in the loaded rows (`VoucherBrowserUiState.availableTypeFilters`), not a fixed taxonomy — matches the accounting-optional principle of never showing an unsupported filter. Selecting a chip only changes what `filteredVouchers` renders; the fetched/cached collection and pagination are untouched. New `VoucherBrowserEvent.TypeFilterChanged`, reset to "All" on company switch. 4 new unit tests + 1 screen-composition edit. |
| Sync screen's manual "Refresh status" gives no visual feedback | `SyncScreen.kt` | `SyncUiState.isRefreshingOverview` was set by the ViewModel but never read anywhere in the Composable — tapping Refresh looked like it did nothing. | Button now disables and shows an inline spinner while `isRefreshingOverview` is true. |
| Settings shows a raw enum literal as a radio label | `SettingsScreen.kt` | `LedgerSharingDefaultPeriod` radio group rendered `period.name` (e.g. `CurrentFinancialYear`) verbatim — the only one of four radio groups on the screen not humanized. | Added the same period-name mapping already used on `LedgerStatementScreen.kt`'s period picker ("Current FY", "Last 7 Sales", …), so the two screens speak one vocabulary instead of inventing a second. |
| Desktop header connection dot is invisible while connecting/reconnecting | `main.css` | `.indicator-connecting` was never defined (only `.indicator-waiting/-starting/-connected/-disconnected/-error/-unknown`), so the header dot rendered with no fill during exactly the states where feedback matters most. The footer's equivalent `.status-connecting` already existed — this was a one-sided gap. | Added `.indicator-connecting { background: var(--yellow); }`. |
| Desktop Dashboard "Refresh" button doesn't refresh the dashboard | `app.ts` (`loadCompanies`) | The click handler already fetched a fresh `DashboardState` (needed to resolve the selected company id for the company list) but discarded it — the visible Connection/Sync/Version summary never updated on manual Refresh, only the company list did. | `loadCompanies()` now also calls `renderDashboard(dashboard, { includeRefreshTimestamp: false, showStatusFeedback: false })` with the state it already fetched. |

## 4. P1 — implemented

- **TD-028 parity gap reopened and closed for Voucher Details.** The Ledger Statement screen's own
  code comment claimed parity with Voucher's "direct, always-visible, one-tap Preview entry point,"
  but Voucher's PDF preview required Share invoice → bottom sheet → View invoice PDF (2 gated taps,
  and no path at all when the share button was disabled). Added a `Preview` `TextButton` directly
  in Voucher Details' TopAppBar, gated on the existing `canShareInvoice` business rule (unchanged —
  `isShareableInvoice()`/`shareIneligibilityReason()` from Phase 3E are untouched), mirroring
  Ledger's placement. 2 new instrumented tests.
- **Voucher Details had no back-navigation affordance at all** (no `navigationIcon`, no `onBack`
  wired anywhere in `BudcomNavHost`) — every sibling detail screen has one. Added, wired through
  `VoucherDetailsRoute`/`BudcomNavHost` to `popBackStack()`. 1 new instrumented test.
- **The app's one shared loading component was dead code**, duplicated ad hoc at
  `MasterDataListComponents.MasterDataLoadingIndicator`, `BudcomNavHost`'s root start-gate, and
  `CompanyScreen.LoadingState` — three near-identical `Box+Center+CircularProgressIndicator`
  blocks, none of which called `FullScreenLoading`. Consolidated all three onto the shared
  component (Settings/Diagnostics were **not** touched — their loading state lives inside an
  active `verticalScroll` Column, where `fillMaxSize()` is a known Compose crash hazard; fixing
  those safely needs restructuring the branch order, noted as DEFER below rather than risked here).
- **Offline/error copy was inconsistent across screens** for the identical condition: Dashboard and
  the shared `MasterDataUiError` said "Device is offline.", Company said "No network connection." —
  aligned Company to the app-wide wording. The shared `master_data_offline_banner` string said
  "Showing retained list when available.", which reads oddly on Settings/Diagnostics (no list) —
  reworded to "Showing the most recently retained data." so one string fits every screen that
  reuses it.
- **Diagnostics rendered raw `Boolean.toString()`** ("true"/"false") for Tally reachable,
  Repository, Database, and Safe mode — reads like an unformatted debug console. Added a
  `Boolean.toYesNo()` helper, applied to all four.
- **Sync screen's phase label printed the raw Kotlin enum constant** (`PartiallySuccess`, …) while
  the same screen's `SyncRunStatus.toLabel()` a few lines away already humanizes an equivalent
  enum — added `SyncPhase.toLabel()` following the same pattern.
- **Desktop showed raw booleans/enum tokens** in three places: "Connector Reachable" as
  `true`/`false` (the adjacent Lifecycle panel already used Yes/No for the same kind of fact — now
  consistent); the Company Selection panel's discovery-failure text showed the raw backend status
  code (`UNAVAILABLE`, `TIMEOUT`, …) even though `mapDiscoveryUserMessage()` already existed and
  produces a real sentence — just never called from the renderer; and Connection Details' "Session
  Status"/"Indicator" rows showed the raw `SessionDisplayStatus` (`NO_COMPANY_SELECTED`) and tone
  token (`connecting`) instead of humanized text. All three now go through small formatting
  helpers (`formatSessionStatus`, `formatConnectionTone`) or the existing `mapDiscoveryUserMessage`.
- **Desktop `dt` label casing was split** between Title Case (Connection Details/Lifecycle, ~12
  fields) and sentence case (Diagnostics/Settings, ~16 fields) for the same kind of field. Sentence
  case was the larger existing convention — normalized the Title Case outliers to match.
- **Three dead `setText()` calls** (`dashboard-company-id`, `dashboard-selection-time`,
  `dashboard-session-status`) targeted DOM ids that don't exist anywhere in `index.html` — silent
  no-ops on every render. Removed, and updated the one test that had (incorrectly) declared those
  ids in its own synthetic fixture rather than reflecting the real page; that test now asserts
  against the real `connection-detail-session` id instead.
- **Icon deprecation cleanup incidental to the above:** `Icons.Filled.ArrowBack` →
  `Icons.AutoMirrored.Filled.ArrowBack` on `LedgerStatementScreen.kt` (was already inconsistent
  with `PdfPreviewScreen.kt`'s modern icon) and the new Voucher Details back button.

## 5. Design-system consolidation notes (Phase 2)

Confirmed strengths worth preserving as the template for future work: zero hardcoded
`Color(0x…)` literals and zero raw `TextStyle`/`fontSize` overrides found anywhere in the audited
Android screens — every screen correctly routes through `MaterialTheme.colorScheme`/`typography`.
`MasterDataUiError`/`toMasterDataUiError()`/`displayMessage()` is genuinely shared across
Ledger/StockItem/Voucher and should stay the template for new error-mapping code.

Consolidation opportunities identified but **not** acted on in this pass (would exceed "narrow
polish," each needs its own scoped pass):
- `ui/theme/Type.kt`'s `BudcomTypography` only overrides 7 of Material 3's type roles
  (`displaySmall/headlineMedium/titleLarge/titleMedium/bodyLarge/bodyMedium/labelLarge`).
  `bodySmall`, `labelMedium`, `labelSmall`, and `headlineSmall` — used dozens of times across every
  screen audited, including the Voucher Details document title — silently fall back to Material
  3's stock ramp instead of the brand's SansSerif scale. Fixing this touches the shared theme file
  and would visually shift roughly half the text in the app; needs a visual pass across screens
  before landing, not a blind fill-in.
- `LedgerBrowserScreen`, `StockItemBrowserScreen`, and `VoucherBrowserScreen` share ~180 lines of
  near-identical scaffold (search field, offline banner, loading/empty/error states, list). A
  `MasterDataBrowserScaffold` was clearly intended but never extracted. Ledger/StockItem also both
  compute `dataFreshnessAt` but never render it — Voucher's richer cache-status/Refresh row
  (`Live`/`Offline · Last synced {time}`) is not mirrored on the other two, so the "offline
  copy" audit finding is really "Ledger and StockItem don't show a cache-status row at all," not a
  wording mismatch. Recommended for a follow-up pass: extract the shared scaffold and give
  Ledger/StockItem the same status row Voucher already has.
- Settings' four radio-group blocks (Theme, Ledger statement mode, period, destination) are
  hand-duplicated ~20 lines each; a shared `SettingsRadioGroup` composable would remove the
  duplication safely.
- Desktop CSS has real token infrastructure (`:root` custom properties) but several literals
  bypass it: `.company-item` background/shadow (`#111827`), hover/selected overlays
  (`rgba(56,189,248,…)`), `.action-btn`'s on-accent text (`#082f49`), and three one-off
  notification tint colors. Low risk but touches many lines for a purely cosmetic gain — deferred.

## 6. P2 — recorded, not implemented (this is a punch list, not a mandate)

- App-wide spacing/padding magic numbers (6/10/13/14/18dp) scattered against a dominant
  4/8/12/16/24 scale, on both Android (Sync/Company/Diagnostics/Search) and Desktop (CSS rem
  values). No formal spacing token exists yet to normalize against — premature to force one now
  per `BUDCOM-UI-DESIGN-DECISIONS.md` §9's own stated sequencing (design system comes after more
  screens are walked through, not before).
- Sync screen and `LedgerStatementScreen.kt` hardcode English copy directly in Kotlin instead of
  `strings.xml` (`targetTitle()`, `defaultCards()`, period-picker labels, dialog titles) — a large,
  mechanical, low-risk-per-line but big-diff extraction; deferred as a dedicated localization pass.
- Voucher Details' Save/Share affordance is structurally different from Ledger's (full-width body
  button + bottom sheet vs. TopAppBar icon + long-press advanced options), and Voucher's sheet
  offers fewer destinations (`SharePdf`/`SavePdf` events exist in `VoucherDetailsUiState` but are
  never wired to a button). Bringing these into full alignment is more than the Preview-entry-point
  fix landed in §4 — recorded for a follow-up, not attempted here to avoid reshaping an already
  -tested share flow without a focused pass.
- `PdfPreviewScreen`'s load-failure state has no retry action, unlike every other error surface in
  the app (`MasterDataErrorBlock` always pairs error text with Retry).
- Company screen: unstyled single-`Text` empty state (no icon/action, thinner than its own
  `ErrorState` a few lines above), a couple of off-scale spacing values (10dp/14dp — note 14dp
  actually matches the established list-row-card convention used by Ledger/Voucher/StockItem, so
  only the 10dp is a real outlier), and raw `fontWeight = SemiBold` instead of a typography token.
- Desktop: 9 flat top-level nav buttons compete in one row on every screen ("reads like a full app
  shell" rather than the intended lightweight utility) — a navigation-shape decision, not a
  targeted fix.
- Desktop storage-gate dialog mixes button-label casing ("Rescan drives" / "Choose a different
  drive" sentence case vs. "Continue"/"Retry"/"Exit BUDCOM" Title Case) — low-visibility, rarely
  seen first-run dialog; genuinely debatable which convention should win.

## 7. DEFER — out of this pass's bounds, with reasoning

- **Desktop tray icon/menu.** The operating brief lists "tray/status behavior" among Desktop
  priorities, but no tray exists at all today (confirmed — zero `Tray`/`nativeImage` usage
  anywhere in `src/`), and closing the window currently quits the app outright
  (`window-all-closed`). Adding a tray presence would change that exit behavior to
  background-and-persist, which is a real product/UX decision (users could be confused the app is
  "still running"), not a visual tweak — and by this project's own classification rule in
  `BUDCOM-MVP-1-0-X-REFINEMENT-REGISTER.md` §2 ("does this increase what MVP-1 does?"), adding
  background-tray capability increases scope rather than polishing existing behavior. Flagged for
  the roadmap, not built here.
- **Desktop header/footer/Dashboard-cards triple restatement of connection & company state.**
  Genuinely matches the "duplicated status information" anti-pattern named in the brief, and the
  three sources (`getDisplayConnectionState()`, raw lifecycle `stateLabel`, raw `healthStatus`) are
  computed independently enough that they can transiently disagree. Trimming this safely means
  deciding which of the three views is authoritative for which purpose — a product/architecture
  call, not a mechanical fix; flagged rather than guessed at.
- **Desktop product naming: "Business OS Tally Connector"** (window title, `<h1>`, About page,
  `package.json` description, and an exported `DESKTOP_WINDOW_TITLE` constant other code imports)
  **vs. "BUDCOM"** (storage-gate dialogs). This is centralized in a named constant, not scattered
  residue — reads as a deliberate naming choice, not an oversight. Renaming the product is exactly
  the kind of "subjective visual-choice decision that materially changes direction" the operating
  brief says to stop and ask about rather than decide unilaterally.
- **`Type.kt` typography gaps and the `MasterDataBrowserScaffold` extraction** — see §5; both need
  a dedicated, visually-verified pass, not a blind fill-in during a bounded run.

## 8. Explicitly untouched (hard boundaries respected)

No changes were made to: accounting semantics, voucher/ledger/stock values, sync architecture,
snapshot/data-integrity behavior, Tally extraction semantics, security/pairing/fingerprint/
credentials, network trust architecture, private-storage architecture, database schemas, or
Connect/Vartalap/Catalogue/Insights/post-MVP scope. The three untracked docs present at the start
of this session (`docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md`,
`docs/product-design/`, `docs/product/`) are unrelated post-MVP product-planning artifacts and were
left untracked/untouched, per the same boundary.

## 9. Regression results

Android (`apps/budcom_android`, JAVA_HOME = Android Studio's bundled JBR 21, since the system
default was JDK 8): `compileDebugKotlin`/`compileDebugUnitTestKotlin`/`compileDebugAndroidTestKotlin`
— clean, zero new warnings. `testDebugUnitTest` — all passing (full suite, run twice). `testReleaseUnitTest`
— all passing. `lintDebug` — BUILD SUCCESSFUL, no new findings (the one pre-existing `ModifierParameter`
warning on `VoucherBrowserScreen.kt` predates this pass). `assembleDebug`/`assembleDebugAndroidTest`
— both build clean; debug APK ≈14.5 MB, no new dependencies added (all new icons come from the
`material-icons-core` artifact already linked in), so no measurable size impact.
`connectedAndroidTest` was **not** run: the only reachable device is the user's real paired physical
phone with live BUDCOM state, and installing a new debug build/running automated UI interactions on
it without an explicit go-ahead this session was judged too invasive for an autonomous run (see the
project's own ADB-disconnect-protocol precedent). This is a real limitation, not a skip of
convenience — flagged for the human visual-review pass below.

Desktop (`apps/budcom_desktop`): `tsc -p tsconfig.renderer.json/main.json/preload.json --noEmit` —
all clean. `vitest run` — 696/696 tests passing (one test's synthetic DOM fixture was corrected to
match production `index.html`, see §4).

## 10. Human visual-review checklist

1. Compare the implemented Android Home layout against `BUDCOM-AC-DATA-HOME-MASTER.png` on a real
   device — header, search entry, compact status row, Vouchers/Ledgers rows.
2. Confirm the new Voucher type-filter chip strip scrolls correctly and reads clearly at real data
   volumes (many voucher types vs. very few).
3. Confirm the new one-tap Preview button on Voucher Details opens the correct PDF for a genuinely
   eligible invoice-type voucher, and that ineligible vouchers still show the existing
   disabled+reasoned button with no Preview action.
4. Confirm Sync screen's Refresh spinner and Diagnostics' Yes/No fields read naturally at a glance.
5. Confirm the Desktop header connection dot now visibly changes color (not blank) while
   reconnecting, and that clicking Dashboard "Refresh" now visibly updates the Connection/Sync/
   Version cards.
6. Dark/light theme spot-check on the touched Android screens (Dashboard, Voucher Browser/Details).
7. Sign off on, or redirect, the three explicitly-deferred product/architecture calls in §7
   (Desktop tray, connection-state consolidation, product naming) before any future pass acts on
   them.
