# BUDCOM Current Development Status

**Status:** Canonical, single-source-of-truth checkpoint. Update at the end of every meaningful
work unit — see the permanent rule in §6. This file must never fall out of sync with `git log`.

## 1. Current phase

**MVP-1 UI/UX POLISH — AUTOMATED WORK COMPLETE**
**FINAL HUMAN VISUAL APPROVAL PENDING**

## 2. Branch / HEAD

- Branch: `main`
- HEAD: `63b919e5232f85cd69c65c0ea3c86fb86070dc8d`
- Not pushed to `origin`.

## 3. Latest UI/UX commits (newest first)

```
63b919e docs: record MVP-1 UI/UX polish pass status, P2/DEFER punch list, and human visual-review checklist
7db7dd9 fix(desktop): connecting-state indicator, broken Dashboard refresh, and raw status text
eb532f7 fix(android): polish loading/error/status copy consistency across Company, Diagnostics, Settings, Sync
b58d85c feat(android): A/c Data Home reconciled with approved Stitch reference + unified Voucher type-filter strip (UIP-002/UIP-003)
```

Full detail for all four: `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md`.

## 4. Approved Stitch reference

`D:\BUDCOM-Design-Archive\01_APPROVED_MASTERS\AC_DATA_HOME\BUDCOM-AC-DATA-HOME-MASTER.png`
(external to the repo — see `D:\BUDCOM-Design-Archive\00_README_AND_DECISIONS\BUDCOM-UI-MASTER-STORAGE-PLAN.txt`
for the archive's own governance of this file).

## 5. Major UI/UX work completed this pass

- Android Home (`DashboardScreen.kt`) reconciled against the approved Stitch master: permanent
  Universal Search entry, compact Fresh/last-sync/Tally-connected status row, Vouchers/Ledgers as
  primary rows — additive only, all prior cards/actions/tests preserved. Insights widget and
  Connect/Vartalap tabs deliberately **not** built (no backing data/destinations exist).
- Voucher Browser: compact horizontal All/Sales/Purchase/… type-filter strip (was missing despite
  UIP-001 being marked closed) — UIP-002.
- Voucher Details: TD-028 parity closed (1-tap Preview in TopAppBar, was 2-tap) + missing back
  button added.
- Cross-cutting copy/consistency fixes: Sync refresh spinner, raw-enum/raw-boolean leaks in
  Settings/Diagnostics/Sync, dead loading-component duplication, offline-copy consistency.
- Desktop: invisible "connecting" status-dot CSS fixed, Dashboard Refresh button that silently
  discarded fetched state fixed, raw status/enum text humanized, dead DOM calls removed.
- Full P2/DEFER punch list with reasoning (Desktop tray, connection-state triple-restatement,
  desktop product-naming split) recorded in the status doc, not implemented.

## 6. Permanent rule — checkpoint discipline

**Every future meaningful BUDCOM Claude work unit must end with:**

> inspect → implement → test → commit → update this canonical checkpoint → clean-tree audit → exact NEXT TASK.

No completed work may exist only in Claude chat/session memory. If a work unit ends without this
file being updated to match the real `git log`/`git status`, that work unit is not finished.

## 7. Test/build status (as of HEAD above)

- Android: JVM debug + release unit tests — passing. `lintDebug` — clean, no new findings.
  `assembleDebug` / `assembleDebugAndroidTest` — build clean. `connectedAndroidTest` — **not run**
  (only reachable device is the user's real paired physical phone; installing a new build on it
  autonomously was judged too invasive without explicit go-ahead).
- Desktop: `tsc --noEmit` clean across renderer/main/preload configs. `vitest run` — 696/696
  passing.

## 8. Known out-of-scope untracked files (leave alone)

```
docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md
docs/product-design/
docs/product/
```

Pre-existing Connect/Universal-Party/post-MVP product-planning artifacts, present before this UI/UX
pass began. Out of this pass's hard boundaries — do not track, edit, or delete without explicit
instruction.

## 9. Exact NEXT TASK

**Install the latest UI/UX-polished Android/Desktop candidates and perform final human visual
approval** — against the approved Stitch reference (§4) and the checklist in
`docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md` §10.

**Not started:** public-release packaging, MVP-1.1. Do not begin either until the visual approval
above is complete and the user explicitly authorizes moving forward.
