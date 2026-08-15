# BUDCOM MVP-1 Controlled-Pilot Closure Status

**This is the one canonical MVP-1 controlled-pilot closure status document.** Supersedes
scattered milestone-era gate docs (`docs/stage-updates/production-gate-status.md`,
`docs/PROJECT_PROGRESS.md`, `docs/PRODUCTION_VALIDATION.md` — all pre-date the
pre-MVP-1 hardening lineage and are historical only). Update this document in place;
do not create a competing status file.

**Assessed:** 2026-08-15
**Branch:** `main` @ `e2d3eac` (127 commits ahead of `origin/main`, unpushed)
**Method:** Direct repository/git inspection, full automated test/build/lint execution
against current HEAD, and targeted source-code verification, cross-checked against
`docs/technical-debt/registry.md`, `docs/planning/BUDCOM-MVP-1-0-X-REFINEMENT-REGISTER.md`,
governance docs, and `D:\Projects\budcom_archives` physical-evidence files.

---

## 1. Automated verification (fresh run, this assessment, against HEAD `e2d3eac`)

| Suite | Result |
|---|---|
| Connector (`vitest run`) | **157 test files / 1358 tests passing** |
| Connector `eslint` + `tsc --noEmit` | Clean |
| Desktop (`vitest run`) | **66 test files / 674 tests passing** |
| Desktop `tsc --noEmit` (main/preload/renderer) | Clean |
| Android `testDebugUnitTest` | **1002 tests / 0 failures** |
| Android `lintDebug` / `assembleDebug` / `assembleDebugAndroidTest` | Clean / successful |

All three components are automated-clean at HEAD. This is strong evidence of
**code-level correctness**, but — as detailed in §4 — it is **not** a substitute for
the physical/device evidence the release ladder requires, and several of the
automated suites validate fixes whose physical repro scenario was never actually
re-exercised (see §4).

---

## 2. Git hygiene at assessment start (item Z)

| Item | Finding | Disposition |
|---|---|---|
| `apps/budcom_desktop/package.json` | Uncommitted, unstaged change strips the entire `scripts` block (`build`/`start`/`test`/`lint`/`dist:win`/`release:controlled-pilot`/`screenshot`) and all `devDependencies`, and reverts `version` 0.4.7→0.4.6. File mtime (2026-08-12 08:32) predates the last 5 commits — this sat broken, uncommitted, unnoticed through several commits. This is **corruption, not intentional work**: no legitimate edit both removes build tooling and decrements a version. `npm run build`/`test`/`lint`/`dist:win`/`release:controlled-pilot` would not resolve from this file as-is. | **OPEN → fixed this session** (restored from HEAD; see §7) |
| `docs/technical-debt/registry.md` | Diff (post `-w`) is one legitimate new entry, **TD-021**, correctly self-flagged "pending architectural scoping by ChatGPT" — an explicit proposal, not implemented, matching this task's "stop on unapproved architecture" mandate. | **Legitimate WIP — committed as-is this session (no implementation)** |
| `docs/technical-debt/TD-022-TD-023-voucher-sync-performance-observations.md` (untracked) | Two well-formed, explicitly non-blocking P3 post-MVP-1 performance observations, drafted standalone specifically because TD-021 was mid-flight in the registry. Self-describes as ready to merge in verbatim. | **Merged into registry.md this session** (see §7) |
| `docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md`, `docs/product/`, `docs/product-design/` (untracked) | Post-MVP-1 Connect/UX planning material (docx specs, implementation checklists). Explicitly out of MVP-1 scope per this task's mandate. | **Left untracked, not actioned** — informational only |
| Worktree `D:\Projects\Budcom_connectivity_hardening` on branch `post-mvp1/secure-local-pairing` (commit `8461901`, diverged/unmerged from `main`) | Separate, correctly isolated post-MVP-1 secure-pairing work in its own worktree. Not polluting `main`. | **Not touched** — out of scope |
| Branch `release/desktop-0.4.3-connector-0.4.0`, and ~14 detached-HEAD build worktrees under `D:/tmp/budcom-release-*` | Disposable release-build worktrees from the `controlled-pilot-release.mjs` pipeline, at various historical commits. Not part of `main`'s working-tree status. | **Not touched** — housekeeping only if requested |

---

## 3. P0/P1 technical-debt physical-confirmation matrix

`docs/technical-debt/registry.md` currently claims, for every one of these, "automated
validation passed/implemented, physical confirmation **pending**." A dedicated
cross-reference against `D:\Projects\budcom_archives` (which has **zero files dated
2026-08-10 through 2026-08-15** — nothing newer than 2026-08-09 exists there) and
against the Android `continuity.13` physical-acceptance record
(`BUDCOM-MVP-1-0-X-REFINEMENT-REGISTER.md` §9, 2026-08-13) produced a materially more
precise picture than the registry text alone:

| TD | Item | True status |
|---|---|---|
| TD-012 | Manual private-IP entry for Trusted-LAN pairing | **Physically confirmed once** (iQOO, 2026-08-08, all 8 acceptance criteria met) — but never reflected back into `registry.md`, and never re-proven on the shipping continuity lineage |
| TD-013 | Post-pairing reconnection loses selected company | **Not physically confirmed.** continuity.13's in-place *app* update never restarts the *Connector process*, so it cannot exercise this defect's mechanism at all |
| TD-014 | Desktop dashboard/company-list no re-poll after transient failure | **Not physically confirmed** — Desktop-side, automated only; continuity.13's session never touched Desktop |
| TD-015 | Desktop business clients retain stale Connector endpoint | **Not physically confirmed** — its own test evidence states "no phone touched" |
| TD-016 | Android diagnostics reports legacy endpoint | **Not physically confirmed** — registry's own text: "physical iQOO retest still required" |
| TD-017 | Authenticated Android reconnect pins pairing-time endpoint | **Not physically confirmed** — a real retest attempt exists and is explicitly logged "BLOCKED / INCOMPLETE." continuity.13's Wi-Fi-toggle test does **not** exercise this (same-network toggle ≠ address change / DHCP renewal / router reboot, which is what TD-017 actually fixes) |
| TD-018 | Packaged transport identity / mutable Connector paths | **Self-contradictory in the registry itself** — Index line says "Windows physical lifecycle accepted," the entry's own body says "installed and physical acceptance pending." No archive file exists to adjudicate. Android-side confirmation is unambiguously still open |
| TD-019 | Android release exposes stale legacy endpoint / no safe active-trust replacement | **Partially confirmed** — a real physical pass found and fixed a real navigation bug (Replace/Re-pair button unreachable); the *corrected* build's physical confirmation was never subsequently done |
| TD-020 | Android Voucher sync action silently discarded | **Ambiguous, leaning not confirmed** — registry's own status field says automated validation only "in progress" (not even "passed"); continuity.13 never specifically documents a fresh Voucher snapshot reaching the Connector |
| TD-021 | `SESSION_EXPIRED` renewal scoped to one call site | Explicit unimplemented **proposal**, correctly parked pending ChatGPT architectural scoping — not an open defect to fix |

**Additional, load-bearing finding:** the current Android version label, `0.1.1-continuity.14`
(`b437e32`), has **no physical-acceptance record of any kind** — no archive file, no
dedicated recording commit (continuity.13 has one, `d081ce2`; continuity.14 does not).
The one textual claim of physical re-confirmation for continuity.14
(`62d3516`, "Physically re-confirmed visually during continuity.14 acceptance," about
UIP-001 only) is **chronologically incoherent**: it was committed 19 seconds *before*
the continuity.14 version bump even existed, and 38 minutes *before* a further commit
(`c2be1fc`) modified the exact screen it claims to have confirmed. Two commits
(`c2be1fc`, `e2d3eac` — the current HEAD) landed after the continuity.14 bump with no
further version bump, no documentation, and no physical evidence; `e2d3eac` in
particular is a real, untested-on-device behavior change (WhatsApp recipient resolution
from ledger alias).

**Conclusion:** MVP-1's P0 hardening work is automated-complete and code-reviewed-sound,
but the physical-confirmation evidence trail is thinner and less current than the
project's own documents imply. This is the single largest fact bearing on controlled-pilot
readiness.

---

## 4. Release-pipeline / distributable-artifact gap

- **No controlled-pilot release artifact (installer, checksum, manifest, lifecycle-gate
  report) exists for any Desktop version newer than 0.4.3.** HEAD is `0.4.7`; four
  version bumps and substantial hardening (`c5ebc96` secure pairing, all of TD-012–021,
  the entire private-storage feature) have landed since the last real packaging run.
- The only `lifecycle-gate-report.json` on disk is under `_audit-export/staging-20260725-233800/`
  (not the canonical `release/controlled-pilot/<version>/reports/` location), dated
  2026-07-25, built against desktop 0.4.3 / connector 0.3.1 — and is internally
  inconsistent (`gitClean: false` in one phase, `dirtyTree: false` in another).
- `docs/governance/BUDCOM-QUALITY-SCORECARD.md`, which
  `BUDCOM-MVP-1-0-X-REFINEMENT-REGISTER.md` §7 makes a **mandatory** MVP-1 freeze gate,
  is a **completely blank template** — never populated for any release.
- The release pipeline (`scripts/release/controlled-pilot-release.mjs`) asserts a clean
  tree before it will run at all (`assertReleaseStartClean()`) — so until §2's git
  hygiene was fixed, a release build could not even be attempted.

---

## 5. Private removable storage (Phase 2)

A genuinely new feature, landed 2026-08-11/12 (commits `a4901cb`…`4f3f91f`), **not yet
reflected in the controlled-pilot runbook/checklist and with no `docs/technical-debt`
entry of its own** despite carrying known limitations. Both Desktop (decision, UX,
watchdog) and Connector (independent fail-closed marker-vaultId guard) participate.

| # | Checklist item | Status |
|---|---|---|
| 1 | Configured data at intended location | **PASS** — implemented + tested |
| 2 | Normal startup, storage attached | **PASS** — implemented + tested |
| 3 | Safe startup, storage absent | **PASS** — implemented + tested (real-filesystem test proves no DB file is created on rejection) |
| 4 | Storage disappears while idle | **PARTIAL** — 10s watchdog detects loss and stops the Connector, but is untested, and the UI degrades to a **generic** "Disconnected" state rather than the purpose-built storage-recovery screen (only reachable via full app restart). Violates the governance spec's "always-visible Private Storage state" requirement post-startup |
| 5 | Storage disappears mid read/write | **KNOWN ACCEPTED LIMITATION**, honestly documented in code and governance docs ("hot-removal mid-write atomicity is not fully guaranteed") — but untracked in the TD registry unlike every comparable risk |
| 6 | Reconnection | **PARTIAL** — resolver/Retry-button path tested; the ordinary dashboard "Restart Connector" button bypasses re-resolution entirely and is untested for this case |
| 7 | No accidental wrong-drive use | **REAL DEFECT FOUND** — the `desktop:choose-storage-mode` IPC handler does not validate server-side that the chosen drive is actually removable; only the *rediscovery* fallback path enforces "removable-only." A caller could select `C:\` and the code would accept it, undermining the stated security invariant end-to-end. **Fixed this session — see §7** |
| 8 | No silent competing DB creation | **PASS** — best-covered guarantee in the feature, proven at both layers with real-filesystem assertions |
| 9 | DB identity/version/integrity after reconnect | **PARTIAL** — schema/integrity checks run on every start; vault identity is filesystem-only, never bound inside the SQLite file itself |
| 10 | Connector/Desktop restart | **PARTIAL** — fresh launch always re-resolves; plain restart-after-loss bypasses re-resolution (same root cause as #6) |
| 11 | Android sync after recovery | **N/A by design** — Android has zero awareness of Desktop's storage layer; recovery is entirely mediated through the Connector's own HTTP availability |
| 12 | Junction/symlink handling | **GAP** — no `lstat`/symlink check on the vault marker or data directory, inconsistent with this codebase's own established pattern elsewhere (`DesktopConfigTempReconciliationService` does check) |
| 13 | Diagnostics without credential leakage | **PASS** — untrusted volume labels rendered via `textContent` only, no credentials in any IPC payload |

---

## 6. Gate-by-gate summary (Phase 1, items A–Z)

| # | Gate | Status |
|---|---|---|
| A | Android↔Desktop/Connector connectivity | Automated **PASS**; physical **OPEN** (§3) |
| B | Company discovery/selection | Automated **PASS**; physical **OPEN** (TD-014/015) |
| C | Secure/approved pairing state | **PARTIAL** — TD-012 confirmed once, not on shipping lineage; TD-018/019 Android side open |
| D | Reconnect after temporary network interruption | Same-network Wi-Fi drop/recover: **PASS** (physically confirmed continuity.13). Address-change/DHCP reconnect (TD-017): **OPEN**, not physically confirmed |
| E | Connector/Desktop restart | **OPEN** — automated only since 2026-08-09, no physical Desktop restart test since |
| F | Android restart | **OPEN** — continuity.13 exercised an in-place *update*, not a device restart; not separately verified |
| G | Windows restart | **OPEN** — TD-018's claim is internally self-contradictory in the registry and stale relative to current hardening |
| H | Refresh/sync correctness | Automated **PASS** (2034 tests across all 3 components); physical **PARTIAL** (Voucher-specific proof ambiguous, TD-020) |
| I | First-refresh stale-data issue | **OPEN** — TD-014 fix automated-only, not physically retested |
| J | Last refresh/sync visibility | **PASS** — implemented in both Android (43 files reference it) and Desktop UI |
| K | Offline/local-data behaviour | **PASS** — local-first Ledger/Voucher persistence implemented, tested, and physically confirmed to survive an in-place app update (continuity.13) |
| L | Tally/XML acquisition/import/sync | **PASS** — connector enforces `EXPORT`-only allowlist, blocks `IMPORT`/`EXECUTE`/`CREATE`/`ALTER` at the security-capability layer; offline XML ingestion tested |
| M | Company data integrity | **PASS** (automated) — ledger identity (TD-011) resolved, SQLite integrity checks in place |
| N | Ledger loading/details | **PASS** (automated + partially physical); full-walk reconciliation completion remains unproven per prior project memory — not a regression, a known open item |
| O | Voucher list/details, supported types | **PARTIAL** — TD-020 physical confirmation ambiguous; most recent UI commits (`c2be1fc`, `e2d3eac`) have zero physical confirmation |
| P | Optional/estimate exclusion behaviour | **PASS** — resolved on follow-up. This is implemented as deliberate PDF framing, not sync-side type filtering: every generated voucher document (`AndroidInvoiceShareCoordinator.kt`'s `InvoicePdfRenderer`) is unconditionally labeled `"ESTIMATE"` (`EstimatePdfText.HEADING`), with fields renamed accordingly (`"Est. Voucher No."`, `"Est. To"`) and an explicit footer: *"For review and reference only. Original invoice accompanies the goods. Generated from synchronized Tally data in BUDCOM."* — i.e. BUDCOM never presents a generated document as an authoritative/official invoice. Tested (`InvoiceShareContentTest.kt`, `VoucherShareActionMatrixTest.kt`). Note: the connector's voucher fetch (`voucher-request.ts`) does **not** filter by Tally's `ISOPTIONAL` flag or by voucher type — every voucher in the requested date range is synced regardless of type; exclusion happens only at PDF-presentation time, not at the data layer. Worth a product-owner confirmation that this is the intended scope (see §9) |
| Q | Invoice/voucher PDF generation | **PASS** — implemented and tested for both Ledger statements (physically confirmed) and vouchers (`InvoicePdfRenderer`, share via `AndroidInvoiceShareCoordinator`, save via `savePdf()`) |
| R | Ledger statement PDF | **PASS** — physically confirmed 2026-08-13 (Save PDF, Summary+Detailed modes) |
| S | Android share/save | **PASS** for Ledger sharing (physically confirmed); the WhatsApp-recipient-from-alias behavior in HEAD (`e2d3eac`) is brand new and **not yet physically confirmed** |
| T | Installer/APK install, upgrade/identity continuity | **PARTIAL** — Android in-place update continuity confirmed; full install/uninstall/reinstall and Windows side remain per TD-018's unresolved status |
| U | Private removable/USB storage | See §5 — mostly automated-PASS with one real defect (now fixed) and one UX gap |
| V | Performance/resource behaviour | **KNOWN ACCEPTED LIMITATION** — TD-022/TD-023, explicitly P3, non-blocking, no build/physical evidence of an actual problem |
| W | Error handling/recovery | **PASS** (automated) — TD-014 bounded recovery tested; physical confirmation shares TD-014's open status |
| X | Logging/diagnostics without credential leakage | **PASS** — TD-010 resolved, sanitizers verified, no bearer/secret logging found in reviewed paths |
| Y | Automated tests/builds/linters | **PASS** — all green at HEAD, see §1 |
| Z | Unexplained Git dirty state | **OPEN → fixed this session**, see §2/§7 |

---

## 7. Actions taken this session

1. Restored `apps/budcom_desktop/package.json` to its committed HEAD content (corruption
   fix — no functional/version change beyond undoing the accidental strip).
2. Merged TD-022 and TD-023 (voucher-sync performance observations, P3, non-blocking)
   into `docs/technical-debt/registry.md` verbatim as invited by their source file; the
   standalone file was then removed.
3. Added TD-024 (wrong-drive validation gap in `choose-storage-mode`) and TD-025
   (mid-session storage-loss UX gap) to the registry, and fixed TD-024 in code with a
   regression test — the smallest correction that restores the "removable-only" invariant
   end-to-end without touching the existing, already-correct rediscovery path.
4. Committed the above as organized, separable commits.

## 8. Gate P/Q follow-up (resolved)

- **P — Optional/estimate exclusion:** implemented as PDF-presentation framing, not
  sync-side filtering — every generated voucher document is unconditionally labeled
  `"ESTIMATE"` with an explicit "for review and reference only, original invoice
  accompanies the goods" footer (`AndroidInvoiceShareCoordinator.kt`, tested). The
  connector's voucher fetch does not filter by Tally's `ISOPTIONAL` flag or by voucher
  type at the data layer — everything in the requested date range syncs regardless of
  type. If sync-side exclusion was actually intended, that is a scope question for the
  product owner/ChatGPT, not something to guess at — see §9, item 3.
- **Q — Invoice/voucher PDF generation:** implemented and tested for both Ledger
  statements (physically confirmed 2026-08-13) and vouchers (`InvoicePdfRenderer`).

---

## 9. HUMAN CHECK list — exact tests required, and exactly what to report back

Nothing below can be closed by further code inspection. No physical evidence is invented
here; do these in order.

### 9.1 Produce a current-version controlled-pilot candidate (prerequisite for everything else)

No installer/APK newer than Desktop 0.4.3 exists anywhere (repo, archives, or
temp-build directories). Every item below needs a build made from **current HEAD**
(`e2d3eac` or later, on a clean tree) — not an old candidate. I can attempt to run
`scripts/release/controlled-pilot-release.mjs` (Desktop+Connector) and an Android
`assembleRelease` myself as a next automated step, but electron-builder packaging and
Android release signing may hit missing certificates/keystores in this environment —
if so, that becomes its own BLOCKED item requiring you to supply signing
material or run the packaging step on a machine that has it. Tell me if you want me to
attempt this next.

### 9.2 Windows Desktop/Connector physical session

Install the fresh candidate on a real Windows machine, then:
1. **Restart Connector** (Settings → Restart, or kill+relaunch) with a company already
   selected and confirm the dashboard/company selection survive without manual
   re-selection (TD-013), and the dashboard/company list recover on their own within
   ~70s without a manual Refresh if the very first request after restart times out
   (TD-014).
2. **Change the machine's active network** (switch Wi-Fi networks, or reboot the
   router so the machine gets a new DHCP address) and confirm Desktop's dashboard,
   company list, ledgers, and stock items all keep working without manually typing an
   IP anywhere (TD-015).
3. **Full install → uninstall → reinstall** and a **same-version reinstall over an
   existing install**, confirming the Connector's TLS transport identity survives
   (Android does not need to re-pair) and no data is lost (TD-018 Windows half).
4. **Report back:** for each of the 3 steps, PASS/FAIL and what you actually observed
   (screenshots of the dashboard state are ideal).

### 9.3 Android physical session (a real phone, not the emulator)

Install the fresh candidate APK **in place** (no data clear) over the current
`continuity.13`/`.14` app, then:
1. Run an individual **Voucher sync** specifically (not just Ledger/Stock) and confirm
   a fresh Voucher snapshot actually appears (open Voucher list, confirm dates/counts
   look current) — TD-020's own stated acceptance bar.
2. With the app paired and working, **change the Connector's network address** — move
   the Windows machine to a different Wi-Fi network or restart its router — and confirm
   the Android app reconnects automatically without re-pairing, a new QR, or manual IP
   entry (TD-017 — this is the one scenario continuity.13's Wi-Fi-toggle test did
   *not* actually exercise).
3. Open **Settings → Diagnostics** and confirm "Configured URL" never shows
   `10.0.2.2` or any raw IP for a securely paired device (TD-016).
4. Open **Settings → pairing management** on an already-`ACTIVE` paired device and
   confirm the **Replace / Re-pair Connector** button is reachable (not auto-navigated
   away) (TD-019's follow-up correction).
5. Test the new **WhatsApp-recipient-from-ledger-alias** feature (`e2d3eac`, current
   HEAD) — this has had zero device testing of any kind.
6. **Report back:** PASS/FAIL for each of the 5 steps with what you actually observed.

### 9.4 Private removable storage physical session

1. Normal boot with the USB drive attached — confirm Desktop starts normally.
2. Pull the USB drive while Desktop is idle (not syncing) — confirm the dashboard
   shows a storage-specific message, not a generic "Disconnected" (this will currently
   **FAIL** per TD-025 — expected, not a surprise).
3. Reinsert the drive and click the storage-gate Retry button — confirm recovery.
4. Reinsert the drive on a **different** drive letter (e.g. unplug/replug into a
   different USB port on some machines) and click the ordinary dashboard "Restart
   Connector" button (not Retry on the storage screen) — this will currently **FAIL**
   per TD-025's second gap (expected).
5. **Report back:** PASS/FAIL for each step.

### 9.5 Quality Scorecard

`docs/governance/BUDCOM-QUALITY-SCORECARD.md` must be filled in by whoever has the
product/business authority to score UX clarity, security/trust, and the other
subjective dimensions — I can prefill the objective/evidence-backed rows
(automated test evidence, functional correctness) but the mandatory-gate dimensions
need a human judgment call.

---

## 10. Final verdict

**NOT READY.**

Not because of demonstrated defects in the shipped code — automated evidence across
all three components is strong (2,712 tests passing, clean lint/typecheck/build) — but
because:

1. No current-version distributable candidate exists to physically test in the first
   place (§4, §9.1).
2. The physical-confirmation evidence trail for essentially every P0 hardening item
   (TD-013 through TD-020) is either missing or doesn't actually cover the failure
   mode it claims to close (§3).
3. The mandatory Quality Scorecard freeze gate has never been filled in (§4).
4. One real defect (TD-024, now fixed) and one real UX gap (TD-025, open) were found
   in a barely-four-day-old feature with no prior hardening pass at all.

None of this blocks continuing hardening work. It does block calling this
**CONTROLLED PILOT READY** today. The path forward is mechanical, not exploratory:
produce a current candidate (§9.1), run the five human-check sessions (§9.2–9.5), and
this document can be updated to a READY verdict once that evidence exists — or to a
narrower list of exact blockers if any physical test fails.
