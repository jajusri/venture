# BUDCOM Current Development Status

**Status:** Canonical concise current-state checkpoint. The historical/audit record is
`docs/status/BUDCOM-DEVELOPMENT-LEDGER.md`. Update both when a work unit changes their subject.

## 1. Current phase

**MVP-1 CONTROLLED PILOT — GO / CLOSED**
**ANDROID `continuity.22` — FINAL HUMAN VISUAL APPROVAL COMPLETE**
**DESKTOP `0.4.17` INSTALLED-RUNTIME VALIDATION — FAILED / BLOCKED**
**DESKTOP `0.4.18` RUNTIME CORRECTION — AUTOMATED-VALIDATED / PACKAGED**
**FINAL HUMAN DESKTOP VISUAL APPROVAL BLOCKED PENDING 0.4.18 INSTALL**

## 2. Branch / HEAD

- Branch: `main`
- Product-code baseline: `e20053f`
- Versions: Desktop corrected candidate `0.4.18`; Connector `0.4.6`; Android
  `0.1.1-continuity.22` (versionCode 23).
- The documentation-only ledger/checkpoint commit follows this product-code baseline; use
  `git log -1` for the self-referential documentation tip.
- Not pushed to `origin`.

## 3. Current evidence

```
e20053f fix(desktop): restore packaged renderer interactivity
96a7a55 feat(desktop): apply approved BUDCOM visual language
9b29abf feat(android): implement approved MVP-1 visual masters
bc9cd55 chore: bump desktop 0.4.15->0.4.16, android continuity.20->continuity.21 for UI/UX polish candidate packaging
63b919e docs: record MVP-1 UI/UX polish pass status, P2/DEFER punch list, and human visual-review checklist
7db7dd9 fix(desktop): connecting-state indicator, broken Dashboard refresh, and raw status text
eb532f7 fix(android): polish loading/error/status copy consistency across Company, Diagnostics, Settings, Sync
b58d85c feat(android): A/c Data Home reconciled with approved Stitch reference + unified Voucher type-filter strip (UIP-002/UIP-003)
```

Current Android visual implementation: `9b29abf feat(android): implement approved MVP-1 visual masters`.

Full detail for the UI/UX work: `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md`.

- Controlled Pilot: **GO / CLOSED** under the constraints in
  `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` and the Quality Scorecard.
- Android: owner FINAL HUMAN VISUAL APPROVAL recorded for installed `continuity.22` on 2026-08-17.
- Desktop: installed `0.4.17` physically failed because packaged renderer ESM initialization
  aborted on a CommonJS named import, leaving all controls unbound. `0.4.18` corrects that module
  boundary and passes packaged interaction validation; UAC installation and human approval remain.

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

**Every future meaningful BUDCOM AI development work unit must end with:**

> INSPECT → IMPLEMENT → TEST → COMMIT → UPDATE RELEVANT STATUS DOC → UPDATE CANONICAL
> CURRENT-DEVELOPMENT CHECKPOINT → CLEAN-TREE AUDIT → RECORD EXACT NEXT TASK.

No completed work may exist only in Claude/Codex/chat session memory. If a work unit ends without this
file being updated to match the real `git log`/`git status`, that work unit is not finished.

At the start of a new AI development session: read this checkpoint; inspect `git status` and recent
Git history; read the relevant domain status and Development Ledger; reconstruct state from
repository evidence; continue rather than redo completed work.

## 7. Test/build status (latest recorded UI/UX regression evidence)

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

**Install Desktop `0.4.18` via the corrected per-machine installer, then repeat FINAL HUMAN DESKTOP
VISUAL APPROVAL** using `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md` §13.

**Not started:** public-release packaging and MVP-1.1. Do not begin either before Desktop visual
approval.
