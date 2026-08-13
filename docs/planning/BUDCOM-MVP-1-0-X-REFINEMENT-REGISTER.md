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

Candidate `continuity.10` (versionCode 11) bundles all four fixes and is
built and artifact-verified, pending the physical acceptance sequence defined
in that build's task report. This is the reference example for how
connectivity/state-correctness findings should be classified, hardened, and
tracked here: narrow, evidence-based, each with regression tests, none
expanding product scope.

**Outstanding:** physical retest of continuity.10 per the retest sequence
(Wi-Fi OFF/ON ×3, lock/unlock, manual Refresh) — see the build task's final
report for the exact steps. Until physically confirmed, this item remains
**automated-validation-complete, physical-acceptance-pending**.

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

**Status:** Recorded only — explicitly deferred by the Product Owner during
physical acceptance ("record only, do not implement now"). Do not implement
until explicitly requested, and until the connectivity/state-correctness
physical acceptance sequence above concludes.

**Touches:** `apps/budcom_android/app/src/main/java/com/budcom/android/feature/voucher/` list row rendering (Compose UI).

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

**LOCKED STRUCTURE — CONTENT ACTIVE.**
