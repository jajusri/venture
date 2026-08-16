# BUDCOM MVP-1.0.x Refinement Register

**Status:** Active
**Governance:** `POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`, `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` (release boundary), `BUDCOM-PRODUCT-DECISION-LOG.md` (PDL-005)

## 1. Purpose

This is the one authoritative place for everything eligible for MVP-1.0.x:
essential items missed from MVP-1, physical-device findings, narrow
hardening, connectivity/state correctness, upgrade continuity, UI polish
that changes presentation but not capability, ergonomics,
layout/colors/buttons/spacing/typography, micro-refinements with
meaningful value, performance/regression findings, and final MVP-1 freeze
criteria.

It does not replace `docs/technical-debt/registry.md`, which remains the
engineering defect log with full root-cause narratives. Where a technical-debt
entry is also 1.0.x-eligible product-facing hardening, this register links to
it rather than duplicating its content.

## 2. Classification rule

> **Does this improve what MVP-1 already does, or increase what MVP-1 does?**

- **Improve existing behavior → eligible for 1.0.x** here.
- **Increase product scope → post-MVP roadmap** (`BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`), not this register.

When in doubt, the test is capability, not effort: a fix, a correctness
improvement, or a presentation change to something that already exists is
1.0.x. A new module, a new data type, or a new user-facing capability is not.

## 3. Item categories tracked here

1. Essential items missed from MVP-1
2. Physical-device findings
3. Narrow hardening
4. Connectivity/state correctness
5. Upgrade continuity
6. UI polish (presentation only, no capability change)
7. Ergonomics
8. Layout / colors / buttons / spacing / typography
9. Micro-refinements with meaningful value
10. Performance / regression findings

## 4. Connectivity/state-correctness hardening already landed (category 4)

Physical iQOO Z10 5G testing after the continuity.9 candidate showed Wi-Fi
loss frequently left the Dashboard falsely showing "Fully operational"
because the Android OS network-loss callback fired reliably only ~1 in 10
attempts. This was corrected in three narrow, mini-hardened steps, each
already committed on `main`:

- `0c769c2` — `DashboardViewModel` reacts to network-state transitions instead
  of only refreshing on manual tap.
- `db98251` — `DefaultNetworkConnectivityObserver` switched to
  `registerDefaultNetworkCallback` for a more reliable OS signal.
- `095ea6f` — foreground/lifecycle reconciliation backstop
  (`repeatOnLifecycle(RESUMED)` + 30s bounded tick) so a missed callback
  cannot leave stale state indefinitely.
- `8b62cea` — known-network-loss short-circuit: a fresh
  `NetworkConnectivityObserver.current()` check before the reconciliation
  path decides whether to spend a Connector HTTP probe, correcting to
  Offline immediately when Android already knows no usable network exists.

Candidate `continuity.10` (versionCode 11) bundled all four fixes and was
built and artifact-verified. This is the reference example for how
connectivity/state-correctness findings should be classified, hardened, and
tracked here: narrow, evidence-based, each with regression tests, none
expanding product scope.

**Status: PHYSICALLY ACCEPTED / CLOSED.** Confirmed on the iQOO Z10 5G during
`continuity.13` (versionCode 14) physical acceptance (2026-08-13): Wi-Fi OFF
produced automatic Offline state and Wi-Fi ON produced automatic recovery,
with no manual Refresh required. continuity.10's fixes carried forward
unchanged through `continuity.11`–`continuity.13`; the physical retest was
performed against `continuity.13`, the first candidate in the lineage to
reach full end-to-end acceptance — see §9 for the complete continuity.13
acceptance record.

## 5. Technical-debt entries that are also 1.0.x-eligible (cross-reference only)

The following `docs/technical-debt/registry.md` entries are release-hardening
work within the 1.0.x boundary (defects/essential-missed-behavior, no scope
increase). Tracked in full there; listed here only so this register is a
complete 1.0.x picture without duplicating content:

| TD | Summary | Status at last update |
|----|---------|------------------------|
| TD-012 | Manual private-IP entry required for Trusted-LAN pairing | Fixed (automated) — physical retest pending |
| TD-013 | Post-pairing reconnection loses selected company | Fixed (automated) — physical retest pending |
| TD-014 | Desktop dashboard/company-list no re-poll after transient failure | Implemented — physical confirmation pending |
| TD-015 | Desktop business clients retain stale Connector endpoint | Implemented — physical confirmation pending |
| TD-016 | Securely paired Android diagnostics report legacy endpoint | Fixed (automated) — physical retest pending |
| TD-017 | Authenticated Android reconnect pins pairing-time endpoint | Implemented — physical confirmation pending |
| TD-018 | Packaged transport identity under install resources | Windows accepted; Android confirmation pending |
| TD-019 | Android release exposes stale legacy endpoint / no safe trust replacement | Implemented — physical release-APK confirmation pending |
| TD-020 | Android Voucher sync action silently discarded | Implemented — physical confirmation pending |
| TD-021 | `SESSION_EXPIRED` renewal scoped to one call site (proposal) | Proposed — not implemented, pending architectural scoping |

## 6. UI polish / micro-refinement items (category 6-9)

### UIP-001 — Voucher-list row layout (LEFT/CENTER/RIGHT)

**Description:** Voucher-list row should become LEFT = voucher number/type,
CENTER = party name, RIGHT = date, all on the same primary line. Long party
names should ellipsize rather than displace the date.

**Why 1.0.x:** Presentation-only change to an existing list; no new
capability.

**Identified:** 2026-08-11, during V3R-1/V3.5 physical acceptance testing on
the unified-voucher candidate (commit `3343cdd`).

**Status: IMPLEMENTED / CLOSED.** Landed at `b2e59cb21bbcd9f079e57d005ec314eded115e77`
("feat(android): polish voucher list row layout and surface reconciliation
status", 2026-08-11) — the same day this item was identified, predating the
`continuity.10`–`continuity.13` lineage. `VoucherRowCard` now renders LEFT =
type/number, CENTER = flexible ellipsized party name, RIGHT = date (fixed
width, never weighted, never wrapped or squeezed). Covered by
`VoucherBrowserUiStateTest.kt` (10 tests). This register entry was not
updated at the time the fix landed; corrected here during `continuity.14`
final-polish scoping (2026-08-13) after re-discovering the fix already
existed. Physically re-confirmed visually during `continuity.14` acceptance.

**Touches:** `apps/budcom_android/app/src/main/java/com/budcom/android/feature/voucher/` list row rendering (Compose UI).

### UIP-002 — Voucher-list horizontal type-filter strip

**Description:** The compact horizontal All/Sales/Purchase/Receipt/Payment/… type filter strip
called for in `BUDCOM-UI-DESIGN-DECISIONS.md` §5 was found missing during the MVP-1 UI/UX polish
pass, despite UIP-001 (row layout) being marked closed — only the LEFT/CENTER/RIGHT row layout had
actually shipped, not the filter strip itself.

**Why 1.0.x:** Presentation-only addition to an existing, already-approved screen; no new
capability — filters narrow what the existing unified collection displays, never what is fetched.

**Identified:** 2026-08-16, during the automated MVP-1 UI/UX polish pass.

**Status: IMPLEMENTED / CLOSED.** `VoucherTypeFilterStrip` added directly under the Vouchers
header — "All" first and default-selected, remaining chips derived from the voucher types
actually present in the loaded rows (never a fixed taxonomy, so a company never sees a filter for
a type it has no vouchers of). Covered by 4 new `VoucherBrowserUiStateTest` cases and 2 new
`VoucherBrowserViewModelTest` cases. See `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md` §3 for
full detail and the wider polish pass this landed alongside.

**Touches:** `apps/budcom_android/app/src/main/java/com/budcom/android/feature/voucher/presentation/`.

### UIP-003 — MVP-1 UI/UX polish pass (bounded, post-Controlled-Pilot)

**Description:** A bounded pass across Android and Desktop covering: Sync refresh visual feedback,
raw-enum/raw-boolean copy leaks (Settings, Diagnostics, Sync, Desktop Connection Details), offline/
error-copy consistency, TD-028 Preview-entry-point parity for Voucher Details (was Ledger-only),
Voucher Details back navigation, shared `FullScreenLoading` component consolidation, a Desktop CSS
fix for an invisible "connecting" status dot, and a Desktop fix for the Dashboard Refresh button
silently discarding the state it fetched. Full item-by-item detail, plus P2/DEFER items recorded
but not implemented, lives in the dedicated status document rather than duplicated here.

**Why 1.0.x:** Every item is a correctness/consistency fix or presentation polish to something that
already exists; no product-scope increase.

**Identified / Status:** 2026-08-16, IMPLEMENTED / CLOSED for the items listed in
`docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md` §3–4; that document's §6–7 record the P2/DEFER
items explicitly left for a future pass or product decision.

**Touches:** see the status document's full file list — spans `apps/budcom_android` presentation
layer and `apps/budcom_desktop` renderer.

### New item template

```
### UIP-XXX — <Title>
**Description:**
**Why 1.0.x (improve, not increase):**
**Identified:**
**Status:**
**Touches:**
```

## 7. Final MVP-1 freeze criteria

MVP-1 is frozen for a given area when, for that area:

1. all P0/P1 technical-debt entries touching it are physically confirmed,
   not just automated-validation-complete;
2. no known falsely-stale or falsely-offline state can persist beyond a
   short bounded interval;
3. upgrade continuity (in-place update, no uninstall, no data clear, no
   re-pair) is physically proven across the accepted candidate lineage;
4. the Quality Scorecard (`BUDCOM-QUALITY-SCORECARD.md`) shows no blocking
   0/1 in accounting/data integrity, security/trust, migration/persistence,
   or core functional correctness;
5. recorded UI-polish items are either implemented or explicitly re-deferred
   by the Product Owner with a stated reason.

Freeze is declared per-area as evidence completes, not as a single
all-at-once event — see `BUDCOM-RELEASE-LADDER-AND-ROLLBACK.md` for the
general release ladder this register operates within.

## 8. Relationship to other documents

- `docs/technical-debt/registry.md` — full defect root-cause detail; this
  register cross-references, never duplicates.
- `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md` — locked visual direction;
  large presentation redesigns (e.g. Home restructuring) belong there and in
  the post-MVP roadmap, not here — this register is for narrow polish to
  existing MVP-1 screens only, not the new Home/Insights/Business-Profile
  direction.
- `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` — anything that increases scope
  moves there instead of staying here.
- `BUDCOM-PRODUCT-DECISION-LOG.md` PDL-005 — the locked 1.0.x boundary
  decision this register implements.

## 9. continuity.11–continuity.13 physical acceptance record (categories 1–3)

### Connector response NetworkOnMainThreadException fix (category 3 — narrow hardening)

`AuthenticatedConnectorApiClient` crashed intermittently with
`NetworkOnMainThreadException` because response-body consumption
(`mapResponse`/`readBoundedBody`) ran outside the `runInterruptible(Dispatchers.IO)`
boundary. Fixed at `e1ff03092daf6d209a40c077a086a3b0d2bd739f` ("fix(android):
consume connector responses off main thread"), first shipped in the
`continuity.11` candidate lineage.

**Status: PHYSICALLY ACCEPTED / CLOSED.** Confirmed on the iQOO Z10 5G during
`continuity.13` (versionCode 14) acceptance (2026-08-13): one normal Sync,
two heavier Syncs, and an incidental mid-sync cancel all completed with the
app process alive throughout and zero new `NetworkOnMainThreadException`,
app crash, or ANR (live ADB-monitored).

### Detailed Ledger Sharing (category 1 — essential item)

Adds an item-level Detailed statement mode alongside the existing Summary
statement, plus a persisted default and a fast-share path — landed at
`bd3d2f4ec5da4dc178bb42c5279f7589885bb003` ("feat(android): detailed ledger
sharing and fast share flow"). A physical-acceptance defect (item Rate blank
on every Detailed row, because Tally's stored rate is a compound display
string like `26.00/Nos` rather than a plain decimal) was found during
`continuity.12` acceptance and fixed at
`6b2a090e5cda66b225ef43ad46331742dfe84480` ("fix(android): preserve detailed
ledger rate text"), shipped in `continuity.13`.

**Status: PHYSICALLY ACCEPTED / CLOSED.** Confirmed on the iQOO Z10 5G during
`continuity.13` acceptance (2026-08-13):
- existing Date / Particulars / Dr / Cr / Balance hierarchy preserved
- item name / Qty+Unit / Rate / Amount displayed within Particulars,
  including compound Tally rate text (e.g. `26.00/Nos`) preserved verbatim
- item Total correct; narration supported; voucher-level Dr/Cr/Balance
  preserved (never repeated per item); all tested items included
- Save PDF — PASS
- WhatsApp Select — PASS
- persisted Summary/Detailed default (Settings → Ledger Sharing) — PASS
- fast-share saved-default path (single tap, no options screen) — PASS

### continuity.13 (versionCode 14) — overall physical acceptance

Physically accepted end-to-end on the iQOO Z10 5G, 2026-08-13, at HEAD
`583d13c996513677aa91a9b1a70b2e562e46c3cb`. In-place ADB update across the
`continuity.10` → `continuity.13` lineage: pairing survived, ESTIMATION
remained the selected company, local Ledger/Voucher data survived, and no
uninstall, data clear, or re-pair was required at any step — satisfying
freeze criterion 3 (§7) for the areas covered above. See §4 for the Wi-Fi
OFF/ON connectivity-regression confirmation, also performed under this
acceptance pass.

**Note:** during physical-acceptance log monitoring, an `adb logcat -c`
inadvertently cleared the logcat crash *buffer*. The historical crash text
had already been captured in the build task's report before this happened,
and `ApplicationExitInfo` (an independent, device-persisted record) retained
the full historical crash timeline unaffected. No continuity.13 acceptance
evidence was lost.

**LOCKED STRUCTURE — CONTENT ACTIVE.**
