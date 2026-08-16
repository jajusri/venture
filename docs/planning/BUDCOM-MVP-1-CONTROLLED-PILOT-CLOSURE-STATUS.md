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

## 9. Controlled-pilot candidate production (second pass, 2026-08-15)

Following architectural approval, produced the current-HEAD candidate artifacts this
document's §4 found missing. See placeholder markers `[[FILLED BELOW]]` — populated
once both builds complete; this section is the authoritative record once done.

### 9.1 Version identity

| Component | Version at this pass | Producing commit | Why bumped |
|---|---|---|---|
| Desktop | `0.4.8` | `33f6edd` | `0.4.7` predated this session's TD-024 fix landing in `apps/budcom_desktop/src` — packaging under the stale label would misrepresent the candidate's contents |
| Connector | `0.4.0` (unchanged) | — | No connector source changed since `0.4.0` was tagged; bundled into the Desktop candidate as-is |
| Android | `versionCode 16` / `0.1.1-continuity.15` | `8808e76` | `continuity.14`'s label was assigned before two more commits (`c2be1fc`, `e2d3eac`) landed on top of it with zero physical evidence either way; bumping guarantees a strictly-higher `versionCode` than whatever is on the physical test device for a clean in-place upgrade |

### 9.2 Pre-packaging git hygiene

The three pre-existing untracked post-MVP-1 paths
(`docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md`,
`docs/product/`, `docs/product-design/`) remain **untracked, unmodified, not deleted,
not gitignored** — confirmed present again via `git status --short` immediately before
this pass. They were temporarily set aside with `git stash push -u` (approved by the
user explicitly, since the release pipeline's `assertReleaseStartClean()` treats any
untracked file as a dirty-tree rejection) for the duration of the packaging run only,
and restored immediately after with `git stash pop`. Repository classification for this
run: **"MVP-1 working set clean; pre-existing post-MVP untracked artifacts remain
outside current mandate."**

### 9.3 Desktop controlled-pilot build — RESULT: PASS

First attempt (invoked through the Bash/Git-Bash tool) failed at the Node-runtime
staging step with `tar: Cannot connect to D: resolve failed` — a Windows/Git-Bash
environment quirk, not a code defect: Git for Windows' bundled MSYS `tar` (resolved
first in that shell's `PATH`) misparses a `D:\...` destination as a remote-host
`tar` target, unlike the native `C:\WINDOWS\system32\tar.exe`. Re-invoked the identical
pipeline through PowerShell (whose `PATH` resolves the native `tar.exe` first).

Second attempt completed every build/test step successfully but was then correctly
**rejected by the pipeline's own post-build provenance check** — I had edited this
closure document (a git-tracked file) while the pipeline was still running, and its
`validatePostBuildProvenance()` step (by design) treats any non-allowlisted change
detected after the build as untrusted and refuses to certify the release. The installer
`.exe` was physically produced on disk at that point but never went through manifest/
checksum/package-boundary validation, so it was correctly not treated as the final
candidate. Committed the closure-doc edit to get a clean tree again, then re-ran a
third time touching nothing else — **full PASS** end to end:

| Step | Result |
|---|---|
| connector-lint / build / test / architecture / audit | PASS (157 files / 1358 tests, 0 vulnerabilities) |
| desktop-build / lint / test / audit | PASS (66 files / 677 tests) |
| contract-test | PASS (5 tests) |
| nsis-installer (electron-builder) | PASS |
| package-boundary / packaged-runtime-contract / packaged-connector-dependencies | PASS |
| manifest / verify-manifest / release-acceptance | PASS |

**Artifact:** `release/controlled-pilot/0.4.8/artifacts/BudcomDesktop-0.4.8-x64-setup.exe`
**Size:** 106,065,028 bytes (~101.2 MB)
**SHA-256:** `61efcd64bface65fa860933ff0aa76abd6f58fc50cbfdfa043df89f39d4af043`
**Producing commit:** `b2023df2f10926e37508ab8d60ec28d7288fd63e` (`sourceTreeCleanAtStart: true`, `dirtyTree: false`)
**Bundled Connector version:** `0.4.0` · **Storage schema version:** `12`
**Packaged Node runtime:** `22.16.0` win-x64, SHA-256 `c5ff4c736112dd483c750fd4149d30c8a116db1a49b8b3ec88be4b65e6c86c19`
**Signing:** unsigned (accepted controlled-pilot limitation — matches every prior
candidate; SmartScreen warning expected on install, per the runbook)
**Full report:** `release/controlled-pilot/0.4.8/reports/release-report.json`

### 9.4 Android debug candidate build — RESULT: PASS

Built `assembleDebug` (installable, signed with the standard Android debug keystore) —
per `D:\Projects\budcom_archives\temporary-builds\20260808_phase3u6\BudcomAndroid-d76b3d0-debug.apk`,
every physical-device candidate in this project's history has been a **debug**-signed
build, not release-signed; this candidate follows that same, already-proven-working
identity so it can upgrade-install over whatever is currently on the test phone without
requiring an uninstall.

`clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` — **all PASS**,
**1002/1002 JVM tests, 0 failures**, at post-version-bump commit `8808e76`.

**Artifact:** `release/controlled-pilot/android/0.1.1-continuity.15-8808e76/BudcomAndroid-8808e76-debug.apk`
**Size:** 13,928,060 bytes (~13.3 MB)
**SHA-256:** `7a67db16cf588c57b2b193353e063fe5b98965f65e63e8cbf277b53f1f45ac08`
**Producing commit:** `8808e76`
**versionCode / versionName:** `16` / `0.1.1-continuity.15`

**Signing-identity gap found (report, not silently resolved):** no release keystore,
`signingConfig`, or any signing material exists anywhere in this repository or
environment (`apps/budcom_android/app/build.gradle.kts`'s `release {}` block has no
`signingConfig` at all). `assembleRelease` would therefore produce an **unsigned**,
**not installable** artifact — useful only for the automated minify/shrink/lint
validation that already existed for TD-019, never for physical installation. Did not
attempt `assembleRelease` this pass since it cannot serve the physical-install goal and
would only duplicate TD-019's already-existing automated evidence. This is a genuine
environment gap (item 5's "signing state" instruction), not something to resolve
unilaterally: minting a brand-new release keystore now would not match whatever (if
anything) signed the currently-installed app on the physical device, and generating one
is a real, hard-to-reverse identity decision. **Recommendation:** continue using
debug-signed candidates for physical pilot testing (matches all prior history)
until/unless a customer-facing signed release is explicitly scoped as its own piece of
work.

### 9.5 Quality Scorecard status after packaging

Updated `docs/governance/BUDCOM-QUALITY-SCORECARD.md`:
- **Upgrade/release readiness: 1 → 2/5** — a current, fully-validated candidate now
  exists for both components with recorded hashes/manifest, but it has not been
  installed on real hardware, and Android has no signed-release path at all. Still
  capped well below "acceptable" until §11's install/upgrade steps (A, B) pass
  physically.
- **Physical validation: unchanged at 1/5** — packaging success is not physical proof
  (instruction #6). Only real device/machine testing moves this number.
- **UX clarity:** unchanged, still marked **(judgment)** — untouched by this pass.

---

## 10. USB / private-storage hot-removal test — safety analysis

Per instruction #9, before asking for any USB-removal-during-write test:

**The operation is genuinely suitable for hot-removal testing, with one important
framing point that changes the real risk level:** BUDCOM's Connector is architecturally
**read-only against Tally** (`connector/budcom_connector/src/tally/security/capabilities.ts`
enforces an `EXPORT`-only allowlist; `IMPORT`/`EXECUTE`/`CREATE`/`ALTER` are rejected —
see ADR-003 "Read-Only Production Connector"). The private-storage SQLite database is a
**synced local cache of Tally data, not the source of truth**. Tally itself — the
business's real, authoritative accounting records — is never written to by BUDCOM and
is completely unaffected by anything that happens to the private-storage vault. **The
worst realistic outcome of this test is needing to delete the corrupted local vault and
re-sync from Tally, not loss of real business data.** This materially lowers the actual
stakes versus a naive reading of "testing data corruption."

That said, the known limitation is real and worth respecting: "hot-removal mid-write
atomicity is not fully guaranteed" (`docs/governance/BUDCOM-BACKUP-RECOVERY-AND-DISASTER-RECOVERY.md`
§5; `main.ts`'s watchdog doc comment). A genuine backup/recovery mechanism exists
(`POST /storage/ledgers/backup` → timestamped copy under `{databasePath}/backups/`;
`POST /storage/ledgers/integrity-check` → `PRAGMA integrity_check`;
`docs/operations/ledger-database-backup-recovery.md`), but that backup by default lives
**on the same drive being tested** — useless if the whole drive becomes unreadable, so
it is not sufficient on its own for this specific test.

**Bounded procedure (recommended, in this order):**

1. **Preferred: use a spare/scratch USB drive with non-critical test-company data for
   this specific step**, if one is available, rather than risking the drive you intend
   to use for ongoing pilot operation. If not available, continue below.
2. **Before pulling the drive**, copy the entire `<driveLetter>\BudcomPrivate\` folder
   to a location on the Windows machine's own local disk (plain Explorer/PowerShell
   copy — no connector interaction needed, and it captures everything, not just the
   ledger DB). This is a convenience/speed measure for faster recovery, not a
   correctness requirement, given the point above.
3. Trigger the **smallest available write operation** — an individual **Ledger** sync
   (not "Sync All") — to minimize how long a write window is actually open, and pull
   the drive once the sync's in-progress indicator appears. Exact byte-level timing is
   not humanly controllable and is not the goal; "during," not "at the precise worst
   instant," is sufficient evidence.
4. Reconnect the drive, run **Diagnostics → Storage integrity check** (or
   `POST /storage/ledgers/integrity-check` directly) before doing anything else.
5. **STOP condition:** if the integrity check fails, or Ledger/Voucher data looks
   visibly wrong or missing, **do not continue further tests against that vault** —
   copy the local backup from step 2 back onto the drive (replacing the corrupted
   `BudcomPrivate` folder), re-run the integrity check to confirm recovery, and report
   back what happened before proceeding to any other step. Do not attempt to
   troubleshoot the corrupted state live against real pilot data.
6. If the drive/vault does not recover even after restoring the step-2 backup, that is
   itself a finding to report, not something to keep retrying — stop and tell me
   exactly what you observed.

This is not "yank the drive blindly" — it is bounded by a cheap backup, a
minimal-duration trigger, a real recovery mechanism, an explicit stop condition, and the
architectural fact that Tally itself is never at risk.

---

## 11. HUMAN CHECK — ordered execution sequence

Ordered to install each candidate exactly **once** and group restarts together, so nothing
needs to be reinstalled mid-sequence. For every step: exact action, expected result, what
to report, PASS/FAIL criteria, and a STOP condition where relevant. Do not skip ahead if a
step fails in a way its STOP condition flags — report back first.

### Session 1 — Desktop/Connector install (candidate 0.4.8)

**A. Desktop candidate install/upgrade identity continuity**
- Action: Install the 0.4.8 candidate over the currently-running Desktop install (no
  uninstall first).
- Expected: Install completes; existing paired devices, selected company, and local data
  remain intact — no forced re-pair.
- Report: PASS/FAIL; if FAIL, exact error text/screenshot.
- STOP if: install reports data loss or forces a re-pair — do not proceed to Session 2
  until this is understood, since it changes what "upgrade continuity" means for every
  later step.

**C. Desktop/Connector startup**
- Action: Launch Desktop normally.
- Expected: Connector auto-starts, reaches `connected`/healthy within the normal ~30–90s
  window.
- Report: PASS/FAIL, time observed.

**D. Tally/XML current source availability**
- Action: Confirm Tally (or the configured XML source) is reachable from this machine
  as it normally is for pilot use.
- Expected: Desktop's health/readiness shows Tally reachable.
- Report: PASS/FAIL.
- STOP if: Tally is not reachable at all — nothing downstream can be meaningfully
  tested; fix connectivity first.

**E. Company discovery/selection**
- Action: Confirm the company list loads and the intended pilot company is selected (or
  select it if not already).
- Expected: Company list populates; selection succeeds and persists.
- Report: PASS/FAIL, company name shown.

### Session 2 — Android APK install + core workflows (candidate continuity.15)

**B. Android APK upgrade identity/data continuity**
- Action: Install the continuity.15 debug APK **in place** (no uninstall, no data clear)
  over the currently-installed app.
- Expected: Install completes as an upgrade; existing pairing, selected company, and
  local Ledger/Voucher data all survive untouched.
- Report: PASS/FAIL; screenshot of Dashboard immediately after first launch.
- STOP if: Android forces an uninstall or reports a signature mismatch — this would mean
  the currently-installed app was NOT debug-signed, contradicting this candidate's
  premise; stop and report the exact error before doing anything else (do not
  uninstall to force it through).

**F. Android connection/pairing**
- Action: Confirm the app reaches the paired Connector without any manual action.
- Expected: Dashboard shows "Fully operational" / connected without needing a fresh QR
  scan.
- Report: PASS/FAIL.

**G. First sync**
- Action: Run "Sync all" (Ledgers + Stock items + Vouchers) once.
- Expected: Completes without error; realistic counts for the pilot company.
- Report: PASS/FAIL, counts observed per resource type.

**H. Repeated/manual refresh**
- Action: Run manual refresh 2–3 more times in a row.
- Expected: Consistent, stable results each time; no regression to a stale/error state.
- Report: PASS/FAIL.

**I. Stale-first-refresh defect check**
- Action: On the very first refresh after this session's app launch (should already be
  covered by step G, but note explicitly), confirm the data shown is NOT left over from
  before the app was updated.
- Expected: Data reflects the current sync, not stale pre-upgrade data.
- Report: PASS/FAIL.

**J. Ledgers**
- Action: Open Ledger Browser, open one ledger's statement.
- Expected: Loads correctly; Date/Particulars/Dr/Cr/Balance intact.
- Report: PASS/FAIL.

**K. Vouchers**
- Action: Open Voucher Browser/list.
- Expected: Loads; LEFT/CENTER/RIGHT row layout intact (type/number, party, date).
- Report: PASS/FAIL — this exercises the just-modified `c2be1fc`/`e2d3eac` code with
  zero prior physical confirmation, so look carefully at row alignment and party-name
  display specifically.

**L. Voucher details**
- Action: Open one voucher's detail screen.
- Expected: Header/metadata/narration/ledger+inventory lines all display correctly.
- Report: PASS/FAIL.

**M. Supported voucher types / estimate framing**
- Action: Confirm the voucher types you'd expect (Sales, Purchase, Payment, Receipt,
  Contra, Credit Note, etc.) all appear in the list as expected.
- Expected: Types match what's in Tally for the test period.
- Report: PASS/FAIL, list of types observed.
- **Also flag for product-owner decision** (not a pass/fail item): the connector
  currently syncs every voucher in the date range regardless of Tally's "Optional" flag
  or voucher type — if Tally has any Optional-marked or Estimate/Quotation-type
  vouchers in the test period, check whether they appear in the list. If your intent
  was for those to be excluded from sync entirely (not just labeled "ESTIMATE" on the
  generated PDF), that's a scope decision to make explicitly, not something to assume.

**N. Voucher PDF**
- Action: Generate a PDF from a voucher's detail screen.
- Expected: PDF generates, clearly labeled "ESTIMATE" with the "for review and
  reference only" footer — never presented as an official invoice.
- Report: PASS/FAIL.

**O. Ledger PDF**
- Action: Generate a Ledger statement PDF (both Summary and Detailed modes if time
  allows).
- Expected: Matches the already-physically-confirmed continuity.13 behavior.
- Report: PASS/FAIL.

**P. Android share/save**
- Action: Share the voucher PDF via WhatsApp (test the new ledger-alias recipient
  resolution specifically, `e2d3eac`) and Save both PDFs to device storage.
- Expected: WhatsApp opens with a resolved recipient where a ledger alias maps to a
  contact; Save completes to a normal accessible location.
- Report: PASS/FAIL for share and for save, separately. This WhatsApp-alias behavior
  has had zero device testing before this — look closely at whether the resolved
  recipient is actually correct, not just that WhatsApp opened.

### Session 3 — Connectivity resilience (no restart needed yet)

**Q. Temporary Wi-Fi interruption/reconnect**
- Action: Turn the phone's Wi-Fi off, wait ~15s, turn it back on (same network).
- Expected: Dashboard automatically shows Offline then automatically recovers, no
  manual refresh needed (already physically confirmed once in continuity.13 — this
  re-confirms it still holds on this candidate).
- Report: PASS/FAIL.

**R. REAL Connector address-change recovery (TD-017 — do not substitute Q for this)**
- Action: With the app connected and working, actually change the Connector's network
  address — e.g. move the Windows machine to a **different Wi-Fi network**, or restart
  the router so it gets a new DHCP lease. This must be a genuine address change, not a
  same-network toggle.
- Expected: The Android app reconnects to the Connector at its new address
  automatically, without re-pairing, a new QR code, or manual IP entry.
- Report: PASS/FAIL — **this is the single most important untested scenario in this
  entire sequence**; no prior physical test has ever exercised this specific path.
- STOP if: reconnection requires re-pairing — this is a real TD-017 regression/failure;
  stop and report the exact behavior observed (what error, if any, and how long you
  waited) before continuing.

### Session 4 — Restart resilience (batched together)

**S. Connector/Desktop restart**
- Action: Restart the Connector from Desktop's Settings (or fully close and relaunch
  Desktop) with a company already selected.
- Expected: Company selection and dashboard/company list recover automatically within
  ~70s without manual Refresh, even if the very first post-restart request times out.
- Report: PASS/FAIL, time observed.

**T. Android restart**
- Action: Fully restart the phone (not just the app).
- Expected: App reopens, reconnects to the Connector, and shows correct state without
  manual intervention.
- Report: PASS/FAIL.

**U. Windows restart (where required)**
- Action: Restart the Windows machine, let Desktop auto-start (or start it manually if
  it's not configured to auto-start) and confirm Connector comes back up.
- Expected: Same as C — Desktop/Connector re-reach healthy state; paired Android device
  reconnects (re-test F briefly) without re-pairing.
- Report: PASS/FAIL.

### Session 5 — Private/removable storage (do last — see §10 for the safety plan on step Y)

**V. USB attached at startup**
- Action: With the private-storage USB drive attached, restart Desktop.
- Expected: Normal startup, storage resolves automatically.
- Report: PASS/FAIL.

**W. USB unavailable at startup**
- Action: Remove the USB drive, then start Desktop.
- Expected: Desktop shows the storage-setup/unavailable screen (Retry/Locate/Exit) —
  not a silent fallback to a different location, not a crash.
- Report: PASS/FAIL.

**X. Controlled USB removal while idle**
- Action: With Desktop running normally (not syncing), pull the USB drive.
- Expected: Desktop detects loss within ~10s (the watchdog interval) and stops the
  Connector. **Known limitation (TD-025):** the dashboard will currently show a
  generic "Disconnected" state rather than a storage-specific message — this is an
  **expected FAIL of TD-025 specifically**, not a surprise; still report it as
  observed, since confirming it happens as documented (rather than something worse)
  is itself the useful evidence here.
- Report: PASS/FAIL for "Connector stopped," separately note what the dashboard
  actually displayed.

**Y. Controlled USB loss during read/write/sync**
- Follow the bounded procedure in §10 exactly — spare drive if available, local backup
  first, smallest-write trigger, integrity check on reconnect, explicit STOP condition.
- Report: PASS/FAIL, and explicitly confirm whether you used a spare drive or the
  pilot drive with a pre-test backup.

**Z. Reconnect same USB, verify canonical DB identity/data**
- Action: Reinsert the same drive, click Retry on the storage screen.
- Expected: Reconnects to the *same* vault (same data, not a fresh empty one); Ledger/
  Voucher data matches what was there before removal (plus whatever synced during Y, if
  Y succeeded without corruption).
- Report: PASS/FAIL.

**AA. Verify no competing fallback DB silently became authoritative**
- Action: While the USB was disconnected (steps W/X/Y), confirm no new database was
  created at the Standard/AppData default location.
- Expected: No `connector-data\budcom-ledger.db` appears under Desktop's AppData path if
  the install is configured for Private storage — check the folder directly if unsure
  where AppData is (`%APPDATA%\budcom-desktop\connector-data` or similar).
- Report: PASS/FAIL — this one specifically validates a guarantee the automated tests
  already prove in isolation; physically confirming it holds end-to-end matters because
  a silent second database would be a genuine data-integrity problem.

**AB. Android sync after USB recovery**
- Action: With storage reconnected and Connector healthy again, run an Android sync.
- Expected: Completes normally against the recovered vault.
- Report: PASS/FAIL.

---

## 12. Final verdict

**NOT READY.**

What changed this pass: current-HEAD, fully validated candidates now exist for both
components — closing the "nothing exists to test" gap that was the single largest
blocker in the original assessment:

- Desktop `0.4.8` — `release/controlled-pilot/0.4.8/artifacts/BudcomDesktop-0.4.8-x64-setup.exe`, SHA-256 `61efcd64bface65fa860933ff0aa76abd6f58fc50cbfdfa043df89f39d4af043`, commit `b2023df`
- Android `0.1.1-continuity.15` (versionCode 16) — `release/controlled-pilot/android/0.1.1-continuity.15-8808e76/BudcomAndroid-8808e76-debug.apk`, SHA-256 `7a67db16cf588c57b2b193353e063fe5b98965f65e63e8cbf277b53f1f45ac08`, commit `8808e76`

What has **not** changed: zero physical evidence exists for either candidate yet
(packaging success is not physical proof — instruction #6). The Quality Scorecard's
Physical validation dimension remains 1/5 (Upgrade/release readiness moved 1→2). All of
TD-013 through TD-020's physical-confirmation gaps from §3 are exactly as open as
before — nothing about producing these artifacts touches that evidence trail.

**Do not treat §9 as evidence that CONTROLLED PILOT READY is close** — it converts "no
candidate exists" to "a candidate exists, untested." The path forward is §11's ordered
sequence, run against these exact two artifacts. This document will be updated to
either a READY verdict or a narrower, exact blocker list once that physical evidence
exists — not before.

---

## 13. Session 2 physical failure — Voucher `parser_failure` investigation (2026-08-16)

**Physical evidence reported.** Android in-place upgrade to `continuity.15` (Session 2,
step B) **PASSED**: app opened normally, existing company/pairing/identity/accounting
data all preserved, no crash. The first fresh Sync/Refresh (step G) then **FAILED** for
Vouchers specifically:

- Company: ESTIMATION
- Ledgers: Completed. Stock items: Completed. **Vouchers: Failed** — Processed 0, Added
  0, Updated 0, Skipped 0, Failed 1
- Error shown: `parser_failure`
- Screen text: "Finished available syncs. 3 target(s) attempted. Some targets failed —
  results are not an all-or-nothing transaction."

Steps A, C, D, E (Session 1, Desktop install/startup/Tally/company selection) were
reported separately and have their own PASS/FAIL record once supplied — this section
covers the Session 2 defect specifically.

### 13.1 Where `parser_failure` comes from (traced in full)

`parser_failure` is a Connector-side classification, not an Android-side one — Android
only displays whatever bucket the Connector's `POST /sync/vouchers` response returns in
its `progress.lastError` field. Traced exactly:

1. `TallyVoucherExtractor.readVouchers()` (`connector/budcom_connector/src/tally/voucher/voucher-extractor.ts`)
   parses the raw Tally response via `VoucherCollectionParser.parse()`. On failure it
   throws `AppError(VALIDATION_ERROR, parsed.message, 422, { reasonCode: parsed.code })`
   — `parsed.code` is one of `malformed-xml` / `missing-envelope` / `missing-header` /
   `missing-body` / `missing-data` / `missing-collection` / `tally-source-error`.
2. The same file's two-phase ledger/inventory-join guard (active in production —
   `enforceTwoPhase = options.config.env !== 'test'` in `tally-module.ts`) can also throw
   `voucher-discovery-expansion`, `voucher-ledger-validation`, or
   `voucher-inventory-validation` for a shape mismatch between the lightweight discovery
   phase and the full ledger/inventory fetch.
3. `VoucherSynchronizationService.synchronize()`'s catch block
   (`connector/budcom_connector/src/services/voucher/voucher-snapshot-sync.service.ts`)
   calls `classifyExtractionFailure(error)`, which collapses **all seven** of the codes
   above (everything except `tally-source-error`, which maps separately to
   `tally_source_error`) into the single bucket string `'parser_failure'`.
4. **The original `AppError` — including its full diagnostic `message` (e.g. "Voucher
   response is not well-formed XML." vs "Voucher response is missing BODY/DATA/COLLECTION.")
   — is discarded at this point.** It is never logged (the completion log at the bottom
   of `synchronize()` only records `outcome`/`durationMs`/counts, not `failureReason` or
   the original error), never persisted (the voucher sync path calls `rollbackSnapshot()`,
   which takes no reason parameter, unlike the separate `discardSnapshot`/
   `failStaleSnapshots` calls elsewhere that do), and never returned over the API (the
   HTTP response only ever contains the bucket string). This is a deliberate,
   privacy-conscious design choice throughout this codebase (no raw Tally content is
   ever persisted or exposed) — but it means **no artifact anywhere in the running
   system, on either device, contains more detail than `parser_failure` for this
   specific event.** Confirmed by also checking: the Tally Request Auditor
   (`{databasePath}/diagnostics/tally-request-audit.jsonl`) records only a SHA-256 hash
   of the outbound *request* and a coarse transport-level reason, never the response
   body, and the exchange in question completed at the transport level (this is a
   post-parse failure) so it wouldn't show an error there either; the packaged
   Connector's stdout is discarded entirely (`stdio: ['ignore','ignore','pipe']`) and
   stderr is bounded to the first 8KB for the process's whole lifetime with no reset —
   almost certainly already exhausted by the time a fresh Voucher sync runs, after
   startup/health/Ledger/Stock-item activity; Desktop's Export Diagnostics bundle does
   not include raw logs or a last-error field either.

### 13.2 Leading root-cause hypothesis (code-confirmed mechanism; not yet confirmed as *the* actual trigger)

**`TD-001`'s already-documented Tally export artifact.** `docs/technical-debt/registry.md`
TD-001 has documented, since 2026-07-22, that Tally emits the literal string `"&#4; Primary"`
in a Group's parent field **on this exact same company, ESTIMATION** ("15 false
positives observed on ESTIMATION (28 groups)"). `&#4;` is a numeric XML character
reference whose decoded value (0x04, EOT) is illegal under XML 1.0. The connector's
shared `TallyXmlResponseParser.assertXml10Characters()`
(`connector/budcom_connector/src/tally/xml/response-parser.ts`) explicitly detects and
rejects this — as a literal control byte *or* as the numeric reference itself — with
`XmlParseError('xml_illegal_character', ...)`, regardless of which collection the text
appears in.

**Proven by regression test** (`test/unit/voucher/voucher-parser.test.ts`, new test
added this session, passing): feeding a Voucher record whose `PARTYLEDGERNAME` contains
`&#4; Primary` through the real, production `VoucherCollectionParser.parse()` reliably
reproduces `{status: 'failure', code: 'malformed-xml'}` — the exact mechanism that
propagates to `parser_failure` on Android. This does **not** prove Tally actually
emitted `&#4;` in this specific failure (no raw response was ever captured, per §13.1),
but it proves the mechanism is real and ties it to the one artifact already known, on
the record, to exist in this exact company's live Tally data — the strongest available
evidence without a raw capture.

**Why Ledgers/Stock items succeeded in the same run:** Vouchers carry rich free-text
fields (`NARRATION`, `PARTYLEDGERNAME`, `REFERENCE`) far more likely to carry this kind
of Tally export artifact than Ledger/Stock-item exports' shorter, more structured
fields — consistent with TD-001 having only ever been observed against Groups (which
also carries a free-text-ish parent-name field) and never against Ledgers/Stock items.

**Other candidate causes, ranked below this one:** a shape mismatch in the two-phase
discovery/ledger/inventory join (`voucher-discovery-expansion` etc.) for a specific
voucher type in ESTIMATION's data; a bare `<` or tag-like substring inside a free-text
field defeating this parser's literal-string closing-tag search (a distinct, unrelated
parser fragility this investigation also surfaced but found no supporting evidence for
yet). Both remain open until either raw evidence or user-supplied data hints narrow
them out.

### 13.3 Local data safety — CONFIRMED SAFE (from code, high confidence)

Voucher reads are always served from the **promoted, active** snapshot
(`voucher_active_snapshots` table). `VoucherSynchronizationService`'s failure path calls
`repository.rollbackSnapshot(company, snapshotId)`, which:
- only accepts a snapshot in `PENDING`/`WRITING`/`VALIDATED` state (i.e. the failed
  run's own **staging** snapshot, which never reached promotion), and
- only deletes that one snapshot's own rows.

There is no code path by which this failure could touch the previously-promoted,
already-synced Voucher data. **Cheap physical confirmation (not required, just a sanity
check):** open the Voucher list on the phone and confirm it still shows the same data
it did before this sync attempt — it should look completely unchanged.

### 13.4 Severity classification

**PILOT BLOCKER** (per instruction #7's own default) — fresh Voucher sync, a core MVP-1
workflow, currently fails completely (0 vouchers processed) against a real pilot
company's live data. Not classified as a data-integrity emergency (§13.3), not a
regression from a previously-working state on this exact scenario (this is the *first*
physical fresh-sync test of Vouchers against real, non-fixture Tally data in this
project's history — TD-020's physical confirmation was already "ambiguous, leaning not
confirmed" per §3), but it does block the pilot's core Voucher workflow outright.

### 13.5 Architectural decision — APPROVED and implemented (2026-08-16, same day)

ChatGPT approved a bounded fix: sanitize XML-1.0-illegal C0 control characters (literal
bytes and numeric character references, e.g. `&#4;`) at the shared parsing boundary,
before structural parsing — removal only, no placeholder substitution, no relaxation of
structural validation, no DTD/entity-declaration handling, class-based (not hard-coded
to `0x04`) — plus diagnostic hardening to stop discarding the granular failure reason.
Full decision text preserved in conversation history; implementation below matches it
point for point.

**Implemented in `connector/budcom_connector/src/tally/xml/response-parser.ts`:**
`sanitizeXml10IllegalCharacters()` removes illegal literal characters and illegal
numeric character references (decimal and hex) before `parseElement()` ever runs;
legal XML whitespace (`#x9`/`#xA`/`#xD`, literal or as numeric references) is always
preserved; ordinary Unicode/business text is untouched; structural parsing
(envelope/body/data/collection presence, unclosed tags, depth/node-count limits) runs
unchanged afterward and remains fully strict — a structurally broken document still
fails, sanitization or not. `ParsedXmlDocument.illegalCharactersSanitized` (a count
only, never the removed characters' content) is returned to every caller.

**Diagnostic hardening implemented:** `VoucherSynchronizationResult` gained
`failureDetail` (the granular reasonCode — `malformed-xml`, `missing-envelope`,
`missing-header`, `missing-body`, `missing-data`, `missing-collection`,
`voucher-discovery-expansion`, `voucher-ledger-validation`, `voucher-inventory-validation`,
or `tally-source-error`) alongside the existing coarse `failureReason` bucket
(`parser_failure` etc., unchanged for compatibility), and `illegalCharactersSanitized`.
Both are: retained on the result object, persisted in the connector's
`voucher.sync.completed` log line (previously missing `failureReason` entirely — a real
gap this also closes), and returned additively as `progress.lastErrorDetail` in the
`POST /sync/vouchers` HTTP response, alongside the existing `lastError` field which is
untouched. No raw error text or business content enters any of these — only fixed enum
strings and counts.

**Regression coverage added** (all passing): sanitizer contract tests in
`response-parser-limits.test.ts` (literal `0x04`, `&#4;`, `&#x4;`, a distinct C0 value
`0x1F` proving class-based coverage, a lone UTF-16 surrogate reference, legal
TAB/LF/CR preserved in both forms, ordinary Unicode/business text unchanged,
structural strictness preserved after sanitization, an already-legal fixture
unaffected); the TD-001 artifact in a Voucher's `PARTYLEDGERNAME` now parses
successfully (`voucher-parser.test.ts`); all nine granular `failureDetail` reasonCodes
preserved without leaking the raw message, plus the `tally_source_error` case and the
`illegalCharactersSanitized` success-path field (`voucher-snapshot-sync.test.ts`); two
pre-existing tests that asserted the old reject-on-illegal-character behavior
(`two-phase-voucher-extraction-design.test.ts`, `master-data-templates.test.ts`,
`voucher-inventory-extraction.test.ts`) updated to assert the new, approved
sanitize-and-succeed behavior instead of being deleted.

**Full verification:** connector `eslint`/`tsc --noEmit`/build all clean; full suite
**157 files / 1377 tests passing** (1358 baseline + 19 net new); architecture tests
(12/12) clean. Desktop (66 files / 677 tests) and contract tests (5/5) re-run and
confirmed unaffected — no Desktop or contract source was touched.

**Registry updated:** TD-001 marked "Fixed (automated validation) — physical
confirmation pending," fix location and evidence corrected; see
`docs/technical-debt/registry.md`.

### 13.6 Data safety — reconfirmed unchanged

None of this fix touched `rollbackSnapshot`, `promoteSnapshot`, or any snapshot-lifecycle
code — only XML parsing and diagnostic-detail threading changed. §13.3's confirmation
that existing local Voucher data cannot have been affected by the original failure
stands exactly as before, and nothing about producing this fix introduces new risk to
that guarantee.

### 13.7 Candidate impact

**Desktop: rebuild required.** The Desktop controlled-pilot installer bundles the
Connector (`scripts/release/prepare-connector-packaging.mjs` packages
`connector/budcom_connector` into the installer) — since Connector source changed, the
existing `0.4.8` candidate no longer reflects current code and must be superseded.

**Android: no rebuild required, none produced.** No Android source changed. The one API
change (`progress.lastErrorDetail`, additive) doesn't affect the existing app, which
doesn't read that field — no version/build reason exists. `continuity.15`
(`release/controlled-pilot/android/0.1.1-continuity.15-8808e76/BudcomAndroid-8808e76-debug.apk`)
remains the correct candidate for physical retesting.

Version bumps and the new Desktop candidate's production/hashes are recorded in §15.

---

## 14. Final verdict (superseded by §16 — see below)

Historical: at the point physical Session 2 testing found the Voucher `parser_failure`
defect (before the fix in §13 was approved and implemented), the verdict was NOT READY
with that defect as an open PILOT BLOCKER. See §16 for the current verdict.

---

## 15. TD-001 fix candidate production (2026-08-16, third pass)

### 15.1 Version identity

| Component | Version | Producing commit | Why bumped |
|---|---|---|---|
| Connector | `0.4.0` → `0.4.1` | `0467abc` | Real behavior change (XML sanitizer + diagnostic hardening), not a documentation-only bump |
| Desktop | `0.4.8` → `0.4.9` | `0467abc` | Bundles the Connector — the 0.4.8 candidate no longer reflects current code |
| Android | `0.1.1-continuity.15` (unchanged) | `8808e76` | No Android source changed; no rebuild required or produced (§13.7) |

### 15.2 A packaging-caught oversight, fixed before the candidate was accepted

First packaging attempt (of this fix) correctly **failed** its own post-build
provenance check: `apps/budcom_desktop/build/VERSION.txt` — a git-tracked file Desktop
reads for packaged-connector version/integrity checks — still held the old connector
version (`0.4.0`) because bumping `CONNECTOR_VERSION` in `defaults.ts` doesn't
automatically update it; the packaging step regenerates it to match, which the pipeline
correctly flagged as an unexpected change rather than silently shipping a
version-mismatched candidate. Fixed and committed (`53a1f98`) before re-running —
exactly the kind of process safeguard this pipeline exists for.

### 15.3 Desktop controlled-pilot build — RESULT: PASS

Full pipeline (connector lint/build/test/architecture/audit, desktop build/lint/test/
audit, contract tests, NSIS packaging, package-boundary, packaged-runtime-contract,
packaged-connector-dependencies, manifest, verify-manifest, release-acceptance) — all
PASS on the corrected candidate.

**Artifact:** `release/controlled-pilot/0.4.9/artifacts/BudcomDesktop-0.4.9-x64-setup.exe`
**Size:** 106,066,651 bytes (~101.2 MB)
**SHA-256:** `424418ab5e37cb88422363b0d8dba857c556974405f47a100931cab026122580`
**Producing commit:** `53a1f985773fd4c7639eb93f8f211684467046b4` (`sourceTreeCleanAtStart: true`, `dirtyTree: false`)
**Bundled Connector version:** `0.4.1` · **Storage schema version:** `12` (unchanged)
**Signing:** unsigned (accepted controlled-pilot limitation, as before)
**Full report:** `release/controlled-pilot/0.4.9/reports/release-report.json`

### 15.4 Pre-packaging git hygiene

Same disposition as §9.2: the three pre-existing untracked post-MVP-1 paths were
temporarily set aside with `git stash push -u` for the packaging run only and restored
immediately after with `git stash pop` — confirmed present, untracked, unmodified
again via `git status --short`.

---

## 16. Final verdict (superseded by §20 — see below)

**NOT READY** — but the one confirmed PILOT BLOCKER found by physical testing (§13) now
has an implemented, tested, packaged fix awaiting physical confirmation.

**What changed this pass:**
- TD-001 fixed (shared XML sanitizer + diagnostic hardening), automated-validated:
  157 files / 1377 tests, clean lint/build/architecture.
- A fresh Desktop candidate exists bundling the fix: `0.4.9`,
  SHA-256 `424418ab5e37cb88422363b0d8dba857c556974405f47a100931cab026122580`.
- Android candidate is unchanged (`continuity.15`, `8808e76`) — no rebuild was needed.

**What has NOT changed:**
- Zero physical evidence exists yet that the fix actually resolves the real-world
  failure — packaging and automated tests are not physical proof (instruction #6,
  restated). The **exact required physical retest** is: install `0.4.9` over the
  current Desktop, run a fresh Voucher sync against ESTIMATION from the already-paired
  `continuity.15` Android app (no reinstall needed there), and confirm Vouchers
  completes instead of failing with `parser_failure`.
- All of §3's TD-013–TD-020 physical-confirmation gaps remain exactly as open as
  before — unrelated to this fix.
- Session 1, 3, 4, 5 of §11 remain to be run; Session 2 steps H onward (which depend on
  a successful Voucher sync) remain blocked pending this one retest.

**Per the architectural decision's explicit instruction: do not proceed with the rest
of Session 2 until this physical Voucher sync retest passes.** Report back with
PASS/FAIL and what you observed (Voucher counts, any error), and this document will be
updated accordingly — either continuing Session 2, or with a new, narrower root-cause
investigation if the sanitizer does not resolve the real failure (which would mean the
leading hypothesis, though mechanically proven, was not what actually happened on the
device — a real possibility this session was explicit about never having fully
confirmed).

---

## 17. Physical retest of 0.4.9 — RESULT: FAIL (2026-08-16)

Desktop `0.4.9` installed successfully. A fresh Voucher sync against ESTIMATION was run
from the already-paired Android app. **Result: still FAILED** — same `parser_failure`,
0 vouchers processed, Ledgers/Stock items unaffected in the same run (matching the
Session 2 pattern exactly).

**Conclusion, stated plainly per instruction: the illegal-character sanitizer, while a
real, independently-proven fix for the `&#4;`-in-text-field case, is confirmed NOT to be
the (or not the only) cause of the actual production failure.** The leading hypothesis
was mechanically sound but not what actually happened on the device. No further
speculative XML fixes were attempted, per the explicit instruction not to keep guessing.

### 17.1 Why the existing 0.4.9 diagnostics could not answer "what actually failed"

Before building anything new, this was checked and confirmed via code (not assumed):

- The first diagnostic-hardening round (`failureDetail`/`illegalCharactersSanitized`)
  was implemented behind `logger.info()`/`logger.debug()` calls, which route to
  `console.log` → stdout. The packaged Connector child process is spawned with
  `stdio: ['ignore', 'ignore', 'pipe']` (`node-process-spawner.ts`) — **stdout is
  discarded entirely**, so this diagnostic detail never reached anywhere retrievable.
- `warn`/`error` (stderr) are piped but bounded to a fixed shared byte budget (8,192
  bytes default) for the **entire process lifetime**, never reset — not a reliable
  channel for a single failure's detail either, and the diagnostic hardening didn't log
  at that level in the first place.
- Android's `AuthenticatedConnectorApiClient` deliberately never attaches
  `NetworkDiagnosticsInterceptor`/`HttpLoggingInterceptor` to the authenticated
  transport (those belong only to a separate legacy `OkHttpClient`), so even a debug
  build with network logging enabled never logs the authenticated sync's raw HTTP
  traffic to Logcat.

**Answer to "was 0.4.9's failureDetail already available": no** — confirmed by code,
not by assumption. No further-guessing candidate was produced without first confirming
this.

---

## 18. Diagnostic-hardening round 2 (2026-08-16)

### 18.1 File-based failure auditor (bypasses both stdio constraints)

New `VoucherSyncFailureAuditor`
(`connector/budcom_connector/src/services/voucher/voucher-sync-failure-audit.ts`)
writes structural-only JSON-lines failure records directly to its own rotated file —
mirroring the existing, already-proven `TallyRequestAuditor` pattern exactly (same
`AuditFileRotator` infrastructure, same lazy-`fs`-injection shape). Each record
contains only fixed enum/structural facts: `failureReason` (coarse bucket),
`repositoryFailureCode`, and a `details` bag (granular `reasonCode`, which Tally
operation, response byte length, a correlation-only response hash, illegal-character-
sanitization count, and — new this round — `reconciliationReason` when applicable).
**Never** raw XML, narration, party names, amounts, phone numbers, addresses, document
bodies, or credentials — by construction, since `details` is exactly the pre-existing
`AppError.details` bag already threaded through `voucher-extractor.ts`.

Wired into the real production dependency graph in
`connector/budcom_connector/src/bootstrap/register-services.ts`'s
`ServiceTokens.VoucherSynchronization` factory (previously only referenced as an
optional constructor parameter, never actually constructed). Desktop-side plumbing
(`app-data-layout.ts`, `connector-lifecycle-config.ts`, `desktop-config-resolver.ts`,
`connector-packaged-paths.ts`, `main.ts`) gives it a real, reinstall-surviving writable
path — `{userDataRoot}/connector-diagnostics/voucher-sync-failure-audit.jsonl` — passed
to the spawned Connector child via `BUDCOM_VOUCHER_SYNC_FAILURE_AUDIT_PATH`, added to
`CONNECTOR_CHILD_ENV_ALLOWLIST`.

### 18.2 New hypothesis investigated: voucher deletion between two-phase requests

Mid-investigation, new physical context was reported: a few vouchers that existed
earlier in ESTIMATION have since been deleted in Tally. This was treated as a
potentially material clue and investigated before writing any new code:

- The Voucher extraction architecture is two-phase in production
  (`enforceTwoPhase = options.config.env !== 'test'`): a lightweight discovery request
  (GUIDs + metadata), then two further **separate, sequential** Tally requests
  (`VoucherLedgerEntries`, `VoucherInventoryEntries`), joined client-side by GUID.
- A voucher present in the discovery phase but absent from a later phase is already
  tolerated gracefully (no throw) — not the failure mechanism.
- A **ledger or inventory entry present in a later phase but referencing a GUID absent
  from the discovery phase** — which deleting a voucher from Tally between the
  discovery request and the later-phase requests would plausibly produce — was
  confirmed to throw. Previously this threw a plain, untyped `Error`, giving no
  distinguishable signal. Converted to a typed `VoucherReconciliationError`
  (`connector/budcom_connector/src/tally/voucher/voucher-reconciliation-error.ts`) with
  reasons `orphan-ledger-entry` / `orphan-inventory-entry` / `duplicate-voucher-guid` /
  `voucher-missing-guid` / `ledger-entries-unbalanced`, now threaded through as
  `reconciliationReason` in the failure detail.
- Proved the mechanism with a new regression test in
  `test/unit/voucher/voucher-inventory-extraction.test.ts` ("fails closed with a
  distinguishable reconciliationReason when an inventory entry references a Voucher
  deleted between phases") — asserts the exact
  `{ reasonCode: 'voucher-inventory-validation', reconciliationReason:
  'orphan-inventory-entry' }` shape. This is now the **leading alternative hypothesis**
  alongside the original illegal-character theory, distinguishably diagnosable from it
  via the new fields.
- Deletion-reconciliation semantics themselves required **no new architectural
  decision**: the atomic whole-window-replace-on-success /
  never-touch-existing-data-on-failure design is already correctly implemented and
  documented (`docs/specifications/business-os-voucher-contract-v1.1.md` §6.2,
  "Atomic promotion and deletion reconciliation") — a failed/partial/cancelled sync
  never clears or mutates the previously-promoted active snapshot. Confirmed from code,
  not assumed. The user's existing local Voucher data was not touched or experimented
  on at any point in this investigation.

### 18.2a Request/response shape investigation (Vouchers vs Ledgers/Stock items)

Per instruction, a third angle was checked: whether Vouchers' Tally request shape
differs structurally from Ledgers/Stock items in a way that could explain a
Voucher-specific failure independent of both hypotheses above.

**Finding:** Ledgers and Stock items (`operation-registry.ts` §§223–274,
`MasterDataTemplates.ledgers`/`.stockItems`) are each a single flat TDL `FETCH`
collection query — one live request, one response, no cross-request join. Vouchers is
structurally unique among all approved collections: it is the only one built from
**three separate live Tally queries**, and — critically — phases 2 and 3
(`buildVoucherLedgerCollectionRequestSpec` / `buildVoucherInventoryCollectionRequestSpec`,
`connector/budcom_connector/src/tally/voucher/voucher-request.ts:74-140`) do not reuse
phase 1's GUID list. Each embeds its own `supportingCollections` sub-query
(`objectType: 'Voucher', fetch: ['GUID'], filters: [dateRange]`) that **independently
re-queries live Tally at that phase's own later point in time**, then joins to it via a
TDL `WALK` (`AllLedgerEntries`/`AllInventoryEntries`) with a computed
`ParentGUID: '$$Owner:$GUID'` field. This WALK/owner-reference join has no equivalent
in the Ledgers/Stock items requests at all.

**Refinement to §18.2's hypothesis:** because phase 2/3 re-query Tally fresh rather
than reusing phase 1's result, a voucher **deleted** between phases would simply vanish
from that later phase's own re-query — producing the already-tolerated "present in
phase 1, absent from a later phase" case (fewer entries, no throw), not an orphan. The
`orphan-ledger-entry`/`orphan-inventory-entry` throw path instead requires a GUID to
appear in a **later** phase that phase 1 (queried earlier) did not see — which the
three-request choreography makes possible from **any** concurrent Tally-side edit
during the sync window that changes which vouchers fall in the query's date range
between requests (a new voucher entered, an existing voucher's date changed, or a
voucher deleted-and-recreated under a new GUID) — not pure deletion alone. This
doesn't rule out deletion as a contributing factor (the user may have been actively
editing, not only deleting, vouchers around the same time), but it means the audit's
`reconciliationReason` field, once retrieved from a real failure, will show
definitively whether this multi-request race is what happened — orphan-* reasons
confirm it; their absence rules it out in favor of the illegal-character hypothesis or
something not yet considered. No code change follows from this alone — it sharpens the
diagnostic reading, it is not a new fix.

### 18.3 Verification the fix reaches the packaged runtime (not just unit tests)

Per explicit instruction, this was checked directly rather than assumed. A clean build
(`rm -rf dist && tsc -p tsconfig.build.json`, exit 0) confirmed all of the following are
present in the compiled `connector/budcom_connector/dist/` output — the exact artifact
tree the Desktop packaging pipeline bundles into `resources/connector/dist/` (`dist/`
is gitignored; this was a local verification build only, no repository changes):

- `sanitizeXml10IllegalCharacters` and `illegalCharactersSanitized` present in
  `dist/tally/xml/response-parser.js`.
- `VoucherSyncFailureAuditor` class compiled to
  `dist/services/voucher/voucher-sync-failure-audit.js`.
- `dist/bootstrap/register-services.js` constructs and wires the auditor into the
  `VoucherSynchronization` factory using `config.voucherSyncFailureAuditPath` /
  `config.voucherSyncFailureAuditEnabled`.
- `orphan-ledger-entry` / `orphan-inventory-entry` typed throws present in
  `dist/tally/voucher/voucher-ledger-reconciler.js` and
  `dist/tally/voucher/voucher-inventory-joiner.js`.

### 18.4 Test results

New tests added: `test/unit/voucher/voucher-sync-failure-audit.test.ts` (auditor writes
structural-only records; withholds writes when disabled), a new integration test in
`test/unit/voucher/voucher-snapshot-sync.test.ts` ("records a structural-only entry via
the file-based failureAuditor when extraction fails" — proves the auditor is actually
invoked end-to-end on a real `synchronize()` failure, not just unit-constructible), and
the deletion-hypothesis regression test in
`test/unit/voucher/voucher-inventory-extraction.test.ts` (§18.2).

Full connector suite: **158 files / 1383 tests passing**, `eslint` clean, `tsc -p
tsconfig.json --noEmit` clean. Desktop suite unaffected (no Desktop source behavior
changed beyond config/env-var plumbing already covered by
`test/unit/connector-lifecycle-config.test.ts` and `test/unit/release-engineering.test.ts`,
both passing, 39/39).

### 18.5 Candidate impact

Connector version bump required (real behavior/diagnostic change). Desktop bundles the
Connector, so it also requires a version bump. **Android is unchanged** — no Android
source was touched this round, matching the minimum-necessary-candidate instruction.

---

## 19. Diagnostic-candidate production (2026-08-16, fourth pass)

### 19.1 Version identity

| Component | Version | Producing commit | Why bumped |
|---|---|---|---|
| Connector | `0.4.1` → `0.4.2` | `fe1ff8d` (tree state at pipeline start) | Real behavior change (file-based failure auditor, typed reconciliation errors, XmlParseError detail preservation) |
| Desktop | `0.4.9` → `0.4.10` | `fe1ff8d` | Bundles the Connector — the 0.4.9 candidate no longer reflects current code |
| Android | `0.1.1-continuity.15` (unchanged) | `8808e76` | No Android source changed this round; no rebuild required or produced |

### 19.2 Desktop controlled-pilot build — RESULT: PASS

Full pipeline (connector lint/build/test/architecture/audit — 158 files / 1383 tests —
desktop build/lint/test/audit — 66 files / 677 tests — contract tests — 5 tests — NSIS
packaging, package-boundary, packaged-runtime-contract, packaged-connector-dependencies,
manifest, verify-manifest, release-acceptance) — all PASS. `sourceTreeCleanAtStart:
true`, `generatedChangesAfterBuild: []` — no provenance drift.

**Artifact:** `release/controlled-pilot/0.4.10/artifacts/BudcomDesktop-0.4.10-x64-setup.exe`
**Size:** 106,068,131 bytes (~101.2 MB)
**SHA-256:** `c483e8e7f2975e94b3b4870b20b8f36fd3354a2385ab2398df16d1e0132a6a7a`
**Producing commit:** `fe1ff8d900809a9592abe2611e5cd648db1be023` (`sourceTreeCleanAtStart: true`, `dirtyTree: false`)
**Bundled Connector version:** `0.4.2` · **Storage schema version:** `12` (unchanged)
**Signing:** unsigned (accepted controlled-pilot limitation, as before)
**Full report:** `release/controlled-pilot/0.4.10/reports/release-report.json`

### 19.3 Pre-packaging git hygiene

Same disposition as §9.2/§15.4: the same pre-existing untracked post-MVP-1 paths
(`docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md`,
`docs/product-design/`, `docs/product/`) were temporarily set aside with `git stash
push -u` for the packaging run only and restored immediately after with `git stash
pop` — confirmed present, untracked, unmodified again via `git status --short`.

---

## 20. Final verdict (superseded by §24 — see below)

**NOT READY.** TD-001's root cause remains **unconfirmed** — the sanitizer fix from the
third pass did not resolve the real physical failure. This pass does not claim a fix;
it claims only that the next physical retest will, for the first time, be able to
**read the actual failure classification** instead of guessing again.

**What changed this pass:**
- 0.4.9 physical retest recorded as FAIL (§17) — the sanitizer mechanism alone is
  insufficient.
- A file-based `VoucherSyncFailureAuditor` now captures and persists the real failure
  classification to a retrievable file, bypassing the stdio constraints that made the
  first diagnostic round unretrievable (§18.1, §17.1).
- A second, independently-motivated hypothesis (voucher deletion between two-phase
  requests) was investigated and made distinguishably diagnosable via a new typed
  `reconciliationReason` (§18.2), refined by a request/response shape investigation
  (§18.2a) showing the orphan-* path requires a GUID to appear in a *later* phase that
  discovery didn't see — not pure deletion alone — with regression-test proof of the
  mechanism.
- Confirmed all of the above reaches the packaged build artifact, not just unit tests
  (§18.3).
- A fresh Desktop candidate exists bundling all of this: `0.4.10`, SHA-256
  `c483e8e7f2975e94b3b4870b20b8f36fd3354a2385ab2398df16d1e0132a6a7a` (§19). Android
  candidate is unchanged (`continuity.15`, `8808e76`) — no rebuild was needed.

**What has NOT changed / is NOT yet known:**
- The actual root cause of the real physical failure is still **unknown**. Two
  plausible, distinguishable, now-diagnosable hypotheses exist (illegal-character
  content still present somehow after sanitization; two-phase deletion race) — neither
  is confirmed nor ruled out yet.
- No new code fix has been applied beyond diagnostic capture and reconciliation-error
  typing, per instruction: fix only once the exact cause is known from real evidence,
  not from another guess.
- All of §3's TD-013–TD-020 physical-confirmation gaps remain open, unrelated to this
  investigation. Session 1, 3, 4, 5 of §11 remain to be run; Session 2 steps H onward
  remain blocked.

**Required next physical action (exactly one):** install `0.4.10` over the current
Desktop (bundling the file-based failure auditor; Android's already-paired
`continuity.15` needs no reinstall) and run exactly one fresh Voucher sync against
ESTIMATION. Regardless of pass/fail, retrieve
`{userDataRoot}/connector-diagnostics/voucher-sync-failure-audit.jsonl` afterward and
report its last entry — this is the first time the actual failure classification will
be recoverable. **Do not attempt a third speculative code fix before reading that
file.**

---

## 21. Physical retest of 0.4.10 — RESULT: FAIL, audit evidence retrieved (2026-08-16)

Desktop `0.4.10` installed and synced. **Still failed**, same shape (`parser_failure`,
0 vouchers), user additionally observed the failure occurs before any voucher
successfully processes. Per instruction, the audit file was read directly from the real
userData root
(`C:\Users\Vidhi\AppData\Roaming\@budcom\desktop\connector-diagnostics\voucher-sync-failure-audit.jsonl`)
rather than guessing again.

**All 5 entries** (5 retries across ~72 seconds) were structurally identical except
timestamp/runId:

```
failureReason: parser_failure
reasonCode: voucher-ledger-validation
operation: VoucherLedgerEntries
responseByteLength: 64941
responseHash: 7c7184b056f30ecad6db44d2f2a370b9ac6b058bf8f971c103a435a18ecaa1e6
```

No `reconciliationReason`, no `illegalCharactersSanitized`, no XML-parse-detail fields
present in any entry.

**This is decisive, not just suggestive.** `voucher-extractor.ts`'s catch block only
adds `toPrivacySafeXmlParseDetails()` when `error instanceof XmlParseError` and
`reconciliationReason` when `error instanceof VoucherReconciliationError`. Neither
fired, which rules out (not just deprioritizes) both prior hypotheses for this specific
failure:

- **Not the illegal-character hypothesis** — no `XmlParseError`; the response parsed as
  structurally-valid XML.
- **Not the two-phase-deletion/orphan hypothesis** — no `VoucherReconciliationError`;
  `joinAndReconcileVoucherLedgers` never got a chance to run.
- **Not a live-data race** — the response hash was byte-identical across all 5 retries
  spanning over a minute. A concurrent Tally-side edit during the sync window would be
  expected to produce at least some variation; it didn't.

The only remaining code in the `VoucherLedgerEntries` try block that isn't one of those
two typed errors is `VoucherLedgerEntryParser` itself
(`connector/budcom_connector/src/tally/voucher/voucher-ledger-parser.ts`) — which, at
the time of this retest, threw only plain, untyped `Error` objects for every one of its
8 failure branches, none of which were captured by the catch block's conditional
enrichment. That is why the audit collapsed to the same four fields on every attempt:
the real cause was there the whole time, just invisible to this diagnostic generation.
The user's observation ("fails on the very first voucher") is fully explained by this:
`VoucherLedgerEntries` is one bulk request across the whole date range, not per-voucher
— when it throws, the entire extraction fails atomically before any voucher is staged.

---

## 22. Diagnostic-hardening round 3 — Parts A–C (2026-08-16)

### 22.1 Part A: typed parser failures

`VoucherLedgerEntryParser` and `VoucherInventoryEntryParser` previously threw only
plain, untyped `Error` for all structural and field-level checks — the exact gap §21
identified. Added `VoucherEntryParseError`
(`connector/budcom_connector/src/tally/voucher/voucher-entry-parse-error.ts`) with one
reason per actual code branch: `invalid-root`, `missing-header`, `missing-body`,
`missing-data`, `missing-collection`, `tally-line-error`, `missing-parent-guid`,
`missing-ledger-name` (ledger only), `missing-stock-item-name` (inventory only),
`missing-or-malformed-amount`, `missing-or-invalid-is-deemed-positive` (ledger only),
`amount-sign-conflict` (ledger only), `invalid-parent-guid-node-count` (inventory
only). The five structural checks common to both parsers (ENVELOPE root, HEADER/BODY/
DATA/COLLECTION presence, Tally `LINEERROR`) were extracted into a shared
`assertVoucherEntryEnvelope()` so both parsers report the same reasons for the same
conditions. Threaded through `voucher-extractor.ts`'s existing catch blocks as a new
`parseReason` field, alongside the existing `xmlParseDetail`/`reconciliationReason` —
purely additive, no behavior change, no acceptance-semantics change.

### 22.2 Part B: regression analysis against last known good

**0.4.3's actual embedded commit and architecture.** The historical
`release/controlled-pilot/0.4.3/STALE-DO-NOT-DISTRIBUTE.md` quarantine note (already on
disk from an earlier session) records that 0.4.3 embedded Connector `0.3.1` at commit
`10a3e280439d27632d529eea6f3f0d8cf0d1ba60` (2026-07-26) and — per its own package
inspection — **does not contain** `voucher-ledger-parser`, `voucher-ledger-reconciler`,
`voucher-inventory-parser`, or `voucher-inventory-joiner` at all. It used "the legacy
compound Voucher extraction shape, including root Voucher Amount and compound ledger/
inventory methods" — i.e. ledger/inventory rows embedded directly in the discovery
response, no separate `VoucherLedgerEntries` request, no `ISDEEMEDPOSITIVE`-vs-signed-
`AMOUNT` consistency check of any kind.

**When the current architecture was introduced.** `git log --follow` shows
`voucher-ledger-parser.ts` has exactly one commit in its entire history:
`aa96637 feat(connector): finalize safe voucher extraction` (2026-07-30) — confirmed a
descendant of 0.4.3's commit (`git merge-base --is-ancestor` returns true). The
`isDeemedPositive !== signedAmount.startsWith('-')` sign-conflict assertion has never
been modified since — it is exactly as introduced. `voucher-request.ts` (the
`VoucherLedgerEntries` TDL request definition) was also rewritten in the same commit
from an earlier single-phase embedded-fetch shape (added in `5a631fe`, 2026-07-29) into
today's three-request choreography, and has not changed since.

**Timeline:**
```
2026-07-26 (10a3e280, → Desktop 0.4.3): legacy compound extraction, no separate
  ledger-entries request, no sign-consistency check. User reports this synced
  ESTIMATION successfully.
2026-07-30 (aa96637): multi-phase architecture introduced wholesale — separate
  VoucherLedgerEntries/VoucherInventoryEntries requests, strict field validation
  including the sign-conflict assertion. Never modified since.
2026-08-01 to 2026-08-15 (Desktop 0.4.4 → 0.4.9): all bundle the multi-phase
  architecture. No documented physical Voucher-sync test against ESTIMATION exists in
  this closure doc or the registry for any of these candidates — Session 2 (§13) is the
  first recorded physical exercise of this code path.
2026-08-16 (0.4.9, 0.4.10): first two physical exercises of the multi-phase path
  against real ESTIMATION data. Both fail identically at VoucherLedgerEntries parsing.
```

**Conservative conclusion, not an assumption:** the multi-phase `VoucherLedgerEntries`
path — including the sign-consistency assertion — has no known prior successful
physical run against ESTIMATION's real data. This is consistent with either (a) a
genuine logic defect in that assertion, or (b) the assertion being correct in general
but encountering a real Tally sign/deemed-positive combination for this company's data
that its current form doesn't anticipate (e.g. a voucher type where the polarity
convention differs). **Per instruction, this is not assumed to be the cause merely
because it looks likely** — Part A's typed `parseReason` will confirm or refute
`amount-sign-conflict` specifically (as opposed to any of the other 7 branches) on the
next physical retest, which is the evidence still required before any semantic fix.

### 22.3 Part C: tests

New `test/unit/voucher/voucher-entry-parsers.test.ts` (28 tests): every one of the 13
reasons above is proven individually distinguishable for both parsers via direct
`VoucherEntryParseError.reason` assertions, plus a control case proving well-formed
entries still parse identically to before, plus a privacy test proving the reason is
always a fixed enum string never containing business content. Extended
`voucher-inventory-extraction.test.ts`'s existing "fails the production extraction
closed when the inventory phase is invalid" test to also assert `parseReason`. Added a
new integration test to `voucher-extractor.test.ts` reproducing the real failure's
exact shape end-to-end through the full extractor (`reasonCode:
'voucher-ledger-validation'`, `operation: 'VoucherLedgerEntries'`) and asserting
`parseReason: 'amount-sign-conflict'` is now attached.

**Test results:** full connector suite 159 files / 1412 tests passing (up from 158/
1383), `eslint`/`tsc --noEmit` clean. Desktop suite unaffected (no Desktop source
touched this round): 66 files / 677 tests.

---

## 23. Diagnostic-candidate production (2026-08-16, fifth pass)

### 23.1 Version identity

| Component | Version | Why bumped |
|---|---|---|
| Connector | `0.4.2` → `0.4.3` | Real diagnostic behavior change (typed parser errors) |
| Desktop | `0.4.10` → `0.4.11` | Bundles the Connector |
| Android | `0.1.1-continuity.15` (unchanged) | No Android source touched this round |

### 23.2 Desktop controlled-pilot build — RESULT: PASS

Full pipeline (connector lint/build/test/architecture/audit — 159 files / 1412 tests —
desktop build/lint/test/audit — 66 files / 677 tests — contract tests — 5 tests — NSIS
packaging, package-boundary, packaged-runtime-contract, packaged-connector-dependencies,
manifest, verify-manifest, release-acceptance) — all PASS. `sourceTreeCleanAtStart:
true`, `dirtyTree: false` — no provenance drift.

**Artifact:** `release/controlled-pilot/0.4.11/artifacts/BudcomDesktop-0.4.11-x64-setup.exe`
**Size:** 106,069,591 bytes (~101.2 MB)
**SHA-256:** `855c9f6d98fc610f16effeb04072b29182c0ba0c9ee625d9214a54ff1081797d`
**Producing commit:** `ba43aeb5a2b9e25f123cfb42f855047403466ebf`
**Bundled Connector version:** `0.4.3` · **Storage schema version:** `12` (unchanged)
**Signing:** unsigned (accepted controlled-pilot limitation, as before)
**Full report:** `release/controlled-pilot/0.4.11/reports/release-report.json`

### 23.3 Pre-packaging git hygiene

Same disposition as prior passes: the same pre-existing untracked post-MVP-1 paths were
temporarily set aside with `git stash push -u` for the packaging run only and restored
immediately after with `git stash pop`.

---

## 24. Final verdict (superseded by §27 — see below)

**NOT READY.** The real root cause is narrowed to one of `VoucherLedgerEntryParser`'s
13 typed branches, most plausibly `amount-sign-conflict` given it is the only true
cross-field business-logic assertion in the list (the rest are presence/format checks)
— but per instruction this is not assumed, only flagged as the leading candidate. **No
semantic fix has been applied.** This pass adds only typed diagnostics and a
regression-history writeup.

**What changed this pass:**
- 0.4.10 physical retest recorded as FAIL (§21); its audit evidence proved the failure
  is inside `VoucherLedgerEntryParser` itself, not XML illegality and not two-phase
  reconciliation.
- `VoucherLedgerEntryParser`/`VoucherInventoryEntryParser` failures are now typed with
  13 distinguishable reasons (§22.1).
- Regression history established: the multi-phase architecture (including the
  sign-conflict assertion) postdates 0.4.3 by 4 days, has never been modified since
  introduction, and has no documented prior successful physical run against ESTIMATION
  (§22.2).
- A fresh Desktop candidate exists bundling this: `0.4.11`, SHA-256
  `855c9f6d98fc610f16effeb04072b29182c0ba0c9ee625d9214a54ff1081797d` (§23).

**What has NOT changed / is NOT yet known:**
- The exact failing branch is still unconfirmed — that is what the next retest's audit
  entry will show.
- Whether this is a genuine code defect versus a real Tally sign-convention case the
  assertion doesn't yet handle is unknown until the branch is identified and the actual
  Tally semantics behind it are understood.
- No semantic/behavioral fix has been made. Per instruction, none will be made until
  ChatGPT reviews the next retest's evidence.

**Required next physical action (exactly one):** install `0.4.11` over the current
Desktop (Android unchanged) and run exactly one fresh Voucher sync against ESTIMATION.
Read `{userDataRoot}/connector-diagnostics/voucher-sync-failure-audit.jsonl`'s last
entry and report the exact `parseReason` value. **Do not implement a semantic fix
before that evidence is reported and reviewed.**

---

## 25. Physical retest of 0.4.11 — root cause CONFIRMED (2026-08-16)

Fresh Voucher sync against ESTIMATION on `0.4.11` still failed identically in the
Android UI (generic `parser_failure`). Per instruction, no retry was attempted; the
audit file was read directly. Its last entry:

```
parseReason: amount-sign-conflict
reasonCode: voucher-ledger-validation
operation: VoucherLedgerEntries
responseByteLength: 64941
responseHash: 7c7184b056f30ecad6db44d2f2a370b9ac6b058bf8f971c103a435a18ecaa1e6
```

Byte-identical `responseByteLength`/`responseHash` to every prior attempt across
0.4.9/0.4.10/0.4.11 — confirms, again, that Tally's data for this window has not
changed at all across the entire investigation; this is a deterministic parsing
decision, not a data or timing issue.

**Correlated against the round-3 regression analysis (§22.2):** the codebase's own
established convention for interpreting a signed Tally amount —
`extraction/normalization/amounts.ts::inferSideFromSign()`, used by the legacy
voucher-mapper path — derives Dr/Cr purely from the amount's sign, with **no
reference to `ISDEEMEDPOSITIVE` at all**. `ISDEEMEDPOSITIVE` reflects a ledger's
*nature* (debit- or credit-positive by group classification); the signed `AMOUNT`
independently reflects *this specific transaction's* actual direction. They coincide
for straightforward cases but legitimately diverge for others (e.g. a debit that
reduces a normally credit-positive liability ledger). The multi-phase rewrite's
`isDeemedPositive !== signedAmount.startsWith('-')` assertion, introduced in `aa96637`
or with no prior successful physical run, conflated these two fields as an
architectural modeling error — not a data-integrity problem worth failing the entire
Voucher sync window over.

**Answers to the four required questions:**
1. Exact `parseReason`: `amount-sign-conflict`, confirmed directly.
2. Did this validation exist in 0.4.3: no — 0.4.3 predates the entire multi-phase
   architecture (§22.2).
3. Is this a regression: not in the classic sense (no prior version of this exact code
   ever worked and broke) — a new assertion, introduced in a rewrite, that had never
   had a successful physical run, and that conflates two fields the rest of the
   codebase already treats as independent.
4. Smallest safe behavior change: presented as three ranked options (A: downgrade to a
   data-quality flag; B: remove the cross-field assertion entirely; C: keep it strict
   but non-fatal, with an explicit countable diagnostic) for review before
   implementation.

---

## 26. Semantic fix implemented — Option C (2026-08-16)

Approved architectural decision: tolerate the `IsDeemedPositive`/signed-`Amount`
disagreement rather than aborting the sync, per the explicit principles in the
approval message (semantically distinct fields; mismatch not fatal; signed Amount
remains the Dr/Cr source; `IsDeemedPositive` preserved as reported; a specific
non-fatal diagnostic recorded; the voucher continues parsing/syncing; neither field
silently rewritten; structural validation for genuinely-unsafe records remains fatal;
a single tolerated mismatch must not abort the whole sync window).

**Implementation** (smallest change at the parser/model boundary):
- `VoucherLedgerExtractionEntry` (`erp/voucher/voucher-ledger-domain.ts`) gained
  `amountSignConflict: boolean`.
- `VoucherLedgerEntryParser.mapEntry()` computes this flag instead of throwing.
- `joinAndReconcileVoucherLedgers()` now returns `VoucherLedgerJoinResult { vouchers,
  amountSignConflictCount }` — counts flagged entries, no other behavior change. Its
  one production caller (`voucher-extractor.ts`) was updated accordingly; it had no
  other callers.
- `toVoucherLedgerEntry()` (unchanged) already derived Dr/Cr side purely from the
  signed Amount's sign and passed `isDeemedPositive` through independently — so
  requirements #3 and #4 of the approval were already satisfied by the existing code
  once the throw was removed; nothing there needed to change.
- `amountSignConflictCount` threaded end-to-end through `VoucherExtractionResult` →
  `VoucherSynchronizationResult` → the completion log, exactly mirroring the existing
  `illegalCharactersSanitized` telemetry pattern (a count only, never which
  voucher/ledger).
- Structural validation is byte-for-byte unchanged: malformed XML, missing envelope
  structure, missing required fields, and malformed amounts all remain fatal.
- Reviewed `VoucherInventoryEntryParser` for an equivalent cross-field assertion —
  none exists (inventory entries have no `IsDeemedPositive` field at all, never
  fetched, never checked). Nothing was changed there, per the explicit
  do-not-broaden-blindly instruction.

**Tests:** `voucher-entry-parsers.test.ts`'s two former fatal-throw tests for this
branch were rewritten to prove the conflict now parses successfully with
`amountSignConflict: true`; all other (still-fatal) branches are unchanged and still
pass. `voucher-extractor.test.ts` gained an end-to-end test proving a two-entry,
balanced voucher with one conflicting entry completes with correct ledger totals and
Dr/Cr sides, and `amountSignConflictCount: 1`; its existing fatal-`parseReason` test
was retargeted to a different, still-fatal branch (`missing-ledger-name`) since
`amount-sign-conflict` is no longer one. `voucher-snapshot-sync.test.ts` gained a test
proving the count surfaces through the full synchronize() result on a completed sync.

**Test results:** full connector suite 159 files / 1414 tests passing (up from
159/1412), desktop 66 files / 677 tests (unaffected, no desktop source changed),
contract 1 file / 5 tests, `eslint`/`tsc --noEmit` clean.

### 26.1 Candidate production

| Component | Version | Why bumped |
|---|---|---|
| Connector | `0.4.3` → `0.4.4` | Real behavior change (the semantic fix itself) |
| Desktop | `0.4.11` → `0.4.12` | Bundles the Connector |
| Android | `0.1.1-continuity.15` (unchanged) | No Android source touched |

Full pipeline PASS: connector (159 files / 1414 tests), desktop (66 files / 677
tests), contract tests (5 tests), NSIS packaging, and all provenance/manifest checks.
`sourceTreeCleanAtStart: true`, `dirtyTree: false`.

**Artifact:** `release/controlled-pilot/0.4.12/artifacts/BudcomDesktop-0.4.12-x64-setup.exe`
**Size:** 106,070,561 bytes (~101.2 MB)
**SHA-256:** `f646aa5bf28065bb7937ba09f8130e9f6d1683019665ce2af8f7deaa0d7b45ec`
**Producing commit:** `99da6abe051afc43a159dfad997e6812e25c4644`
**Bundled Connector version:** `0.4.4` · **Storage schema version:** `12` (unchanged)

Pre-packaging git hygiene: same disposition as all prior passes — pre-existing
unrelated untracked paths temporarily set aside with `git stash push -u` for the
packaging run only, restored immediately after.

---

## 27. Final verdict (superseded by §29 — see below)

**Root cause confirmed and fixed.** TD-001's Voucher `parser_failure` is understood
end-to-end: `IsDeemedPositive`/signed-`Amount` disagreement is a real, valid Tally
business case the multi-phase rewrite's new assertion incorrectly treated as fatal.
The fix tolerates and counts it instead. Pending exactly one physical confirmation.

**What changed this pass:**
- 0.4.11 physical retest confirmed `parseReason: amount-sign-conflict` directly from
  the audit file (§25).
- Correlated against the round-3 regression timeline: the assertion conflates two
  Tally fields the codebase's own established sign-inference convention already
  treats as independent (§25).
- Option C implemented: disagreement tolerated, counted via
  `amountSignConflictCount`, never silently rewritten; structural validation
  unchanged (§26).
- `VoucherInventoryEntryParser` reviewed for parity — genuinely nothing analogous
  exists there, so nothing was changed (§26).
- Full suite green: connector 159/1414, desktop 66/677, contract 1/5.
- A fresh Desktop candidate exists bundling this: `0.4.12`, SHA-256
  `f646aa5bf28065bb7937ba09f8130e9f6d1683019665ce2af8f7deaa0d7b45ec` (§26.1).

**What has NOT changed / is NOT yet known:**
- No physical confirmation yet that the fix resolves the real-world failure —
  automated tests are not physical proof (restated per standing instruction).
- All of §3's TD-013–TD-020 physical-confirmation gaps remain open, unrelated to this
  investigation. Session 1, 3, 4, 5 of §11 remain to be run; Session 2 steps H onward
  remain blocked pending this one retest.

**Required next physical action (exactly one):** install Desktop `0.4.12` (SHA-256
`f646aa5bf28065bb7937ba09f8130e9f6d1683019665ce2af8f7deaa0d7b45ec`; Android
`continuity.15` unchanged) and run exactly one fresh Voucher sync against ESTIMATION.
Report: voucher counts, the number of `amountSignConflict` diagnostics observed (via
the audit file, if anything is recorded on a successful run, or via a future surfaced
metric), whether existing voucher data/totals remain correct, and whether any new,
different failure reason appears. **Do not proceed to USB/mobile debugging unless this
candidate still fails after this exact semantic fix.**

---

## 28. Physical retest of 0.4.12 — PASS. TD-001 PHYSICALLY CONFIRMED FIXED (2026-08-16)

Desktop `0.4.12` installed successfully. From the already-paired, already-installed
Android `continuity.15` app — no manual Desktop-side sync action was performed — a
fresh Voucher sync against ESTIMATION was run. Result: **completed successfully.** No
`parser_failure`. Voucher list/details display correctly. No visible data loss,
corruption, or new error.

### 28.1 Connector-side evidence, inspected directly (not taken on the user's report alone)

**Failure audit** (`{userDataRoot}/connector-diagnostics/voucher-sync-failure-audit.jsonl`):
unchanged since the last 0.4.11 failure entry — **no new failure record for this run**,
which is exactly the expected shape for a successful sync (the auditor only writes on
failure).

**Voucher snapshot state**, queried read-only directly from the live Connector SQLite
database (`connector-data/budcom-ledger.db`, `node:sqlite` `DatabaseSync` opened with
`readOnly: true`, no writes):

```
Active/PROMOTED snapshot: 945a4735-2333-4786-8389-c98d80a26fe9
  date range:    2026-07-12 .. 2026-08-16 (today)
  status:        PROMOTED
  created_at:    2026-08-16T01:31:06.793Z
  validated_at:  2026-08-16T01:31:11.430Z
  promoted_at:   2026-08-16T01:31:11.431Z
  failed_at:     null
  failure_reason: null
  voucher_count: 94

Aggregate over the promoted snapshot's voucher_headers:
  total_ledger_entries:    189
  total_inventory_entries: 332
  total_allocations:       0
  incomplete_count:        0   (zero vouchers flagged dataQuality='incomplete')
  cancelled_count:         0
  sample of incomplete vouchers: [] (none)
```

This is real promotion evidence, not an inference: the snapshot transitioned
PENDING→WRITING→VALIDATED→PROMOTED, replacing the previously-active (now presumably
archived) snapshot atomically, with zero rejected/incomplete records.

**Noteworthy, honestly reported gap:** `amountSignConflictCount` and
`illegalCharactersSanitized` are **not persisted anywhere retrievable** for a
successful run. They exist only in the transient `VoucherSynchronizationResult`
returned to the API caller and in the `voucher.sync.completed` structured log line —
and that log line goes through `logger.info()` → stdout, which is discarded entirely
in the packaged Connector child process (`stdio: ['ignore', 'ignore', 'pipe']`, the
same constraint documented in §17.1 for failures). This is a real, symmetric
limitation of the current diagnostic design: it was built to capture *failures*
(file-based, bypasses stdio), and this gap for *successes* was not in scope of the
approved fix. **The exact `amountSignConflictCount` for this specific successful run
cannot be honestly reported — it was not fabricated or estimated.** What can be said
with confidence: the fix's logic path was exercised (the same ESTIMATION ledger data
that previously triggered `amount-sign-conflict` on every attempt was processed again,
this time without aborting), and zero vouchers were dropped or marked incomplete.

**A refinement to the round-3 regression history, visible only now:** an earlier
snapshot for ESTIMATION (`8fd497b6...`, `date_from: 2026-07-12`, `date_to:
2026-08-13`, 41 vouchers, promoted 2026-08-13T04:59:40Z) also has status `PROMOTED`
(now archived) using the current multi-phase architecture. This means a *narrower*
Voucher sync (through 2026-08-13) had succeeded before Session 2's failures began —
the conflicting ledger entry(ies) were evidently added to Tally's ESTIMATION data
between 2026-08-13 and the Session 2 test window, not present in every historical
sync. This doesn't change the fix's correctness, but refines §22.2's finding from "no
prior successful run of the multi-phase architecture" to "no prior successful run of
the *specific data* that included the conflicting entries."

### 28.2 Architecture note: why no manual Desktop sync was needed (documented, not a defect)

This is expected, correct behavior, not something to change. `POST /sync/vouchers` is
a standard authenticated route on the Connector's own local HTTP API
(`api/routes/vouchers.ts`), mounted whenever the production dependency graph wires
`VoucherSynchronizationService` + `ConnectorSessionService` — which it always does.
**Desktop is not an intermediary or relay for sync requests.** Its role is (a)
launching and supervising the Connector child process (`ConnectorLifecycleService`)
so it exists and is reachable at all, and (b) providing a UI to select the active
company, which persists in the session/database independent of which client later
triggers a sync. Once paired, Android is an independent, direct client of the same
local Connector API Desktop's own UI calls — it does not need Desktop to relay,
forward, or "kick off" anything. The only precondition is that the Connector process
is running (guaranteed by Desktop having been installed and started) and that a
company is already selected (already true, carried over from the prior session). No
code change is warranted here, per instruction — this is simply how the system was
designed to work.

### 28.3 Documentation updates made this pass

- `docs/technical-debt/registry.md` TD-001: marked **PHYSICALLY CONFIRMED FIXED**,
  citing the 0.4.12 evidence above.
- `docs/governance/BUDCOM-QUALITY-SCORECARD.md`: "Physical validation" raised from 1,
  reflecting that a current-version Desktop+Connector build has now been physically
  installed and exercised end-to-end for the Voucher sync workflow (previously the
  note there stated no post-0.4.3 build had ever been physically tested — no longer
  true). Kept conservative: only one workflow (Voucher sync) on one company has been
  confirmed; the remaining Session 2 items and all of Sessions 1/3/4/5 are still
  outstanding, so the raise is modest, not a jump to "strong."

---

## 29. Final verdict (superseded by §31 — see below)

**TD-001 CLOSED — physically confirmed fixed.** The Voucher `parser_failure` defect
that blocked Session 2 is resolved end-to-end: root cause identified from real
production audit evidence (not guessed), fix implemented and reviewed before
deployment, and now physically confirmed on real hardware against real Tally data
with zero data loss.

**What changed this pass:**
- 0.4.12 physical retest: PASS. Voucher sync completed, 94 vouchers promoted, 0
  incomplete/rejected, no `parser_failure`, no new error (§28).
- Connector-side evidence inspected directly (failure audit unchanged, snapshot
  promotion confirmed via read-only SQLite query) rather than taken on report alone
  (§28.1).
- Architecture accurately documented: Android's ability to sync without a manual
  Desktop trigger is expected behavior (direct API client), not a gap (§28.2).
- TD-001 and the Quality Scorecard updated to the extent this evidence supports
  (§28.3).

**What has NOT changed / remains open:**
- Exact `amountSignConflictCount` for this run is not retrievable (documented
  limitation, §28.1) — not fabricated.
- All of §3's TD-013–TD-020 physical-confirmation gaps remain open.
- Session 2 items B/F/I/K/L are reasonably satisfied by this round's evidence; G
  (combined "Sync all"), H (repeated refresh), J (Ledger statement), M (voucher-type
  coverage), N (Voucher PDF), O (Ledger PDF), and P (WhatsApp share/save) remain
  unconfirmed. Sessions 1, 3, 4, 5 have not started.

**Per explicit instruction: Session 3 is not started this pass.** The next step is
the remaining Session 2 HUMAN CHECK items, reported separately to the user in
efficient order.

---

## 30. Data-integrity investigation — 94 vs 62 Vouchers (2026-08-16)

**Trigger.** Immediately after the §28 physical PASS, the user reported Mobile BUDCOM
showing 94 Vouchers for ESTIMATION while Tally itself showed only 62 total (including
Optional), having intentionally deleted several Vouchers in Tally earlier. Session 2
progression was stopped pending reconciliation. This investigation was entirely
read-only: no new BUDCOM sync was triggered, no Tally data was altered, no BUDCOM data
was deleted, and no production code was changed until the findings were reported and
reviewed.

### 30.1 Exact reconciliation

The Connector's own `POST /sync/vouchers` route defaults to `dateFrom = today − 29,
dateTo = today` whenever the request body omits them (`api/routes/vouchers.ts:56-58`).
Android's `SyncApi.startVoucherSync()` is called with an **empty**
`VoucherSyncStartRequestDto()` (`SyncRemoteDataSource.kt:42`) — so the actual verified
window for this sync was `2026-07-18`..`2026-08-16`, not the wider `2026-07-12`
recorded in the snapshot's stored period (which is a historical union carried across
many prior syncs, not this sync's own request).

A standalone, read-only script sent the exact same Voucher discovery TDL request
BUDCOM's own discovery phase builds (`buildVoucherCollectionRequestSpec`) directly to
Tally's export port, entirely bypassing the Connector's sync/promotion pipeline (no
snapshot created or touched). Result, diffed GUID-for-GUID against the active BUDCOM
snapshot for the same window:

| | Count |
|---|---|
| Live Tally XML export | 94, 94 unique GUIDs |
| Active BUDCOM snapshot | 94, 94 unique GUIDs |
| A. Present in both | 94 |
| B. Present only in Tally | 0 |
| C. Present only in BUDCOM | 0 |
| D. Duplicate GUIDs (either side) | 0 |

Voucher-type breakdown identical on both sides: Sales 54, Receipt 34, Contra 1,
Purchase 1, Credit Note 1, Debit Note 1, Payment 1, Journal 1. `IsCancelled`: 94/94
"No" on both sides.

### 30.2 Carry-forward mechanism — investigated, found not to be the cause here

`SqliteVoucherRepository.carryForwardVouchersOutsideWindow()` copies Vouchers from the
previously-active snapshot into the new one when their `voucher_date` falls outside
the freshly-requested window, without re-querying Tally for those older rows. Direct
inspection of the active snapshot confirmed **0 of the 94 rows fall outside the
`2026-07-18`..`2026-08-16` window** — every single one was freshly extracted and
re-verified against Tally in this sync, none carried forward. The mechanism is real
(confirmed from code) but did not contribute to this snapshot.

### 30.3 Query-path correctness

`SqliteVoucherRepository.querySnapshot()` resolves the single active `snapshot_id` via
`voucher_active_snapshots` and filters `voucher_headers` by both `company_id` and that
exact `snapshot_id` — confirmed from code to never union multiple snapshots. The "94"
Android displays is exactly this snapshot's row count, correctly scoped.

### 30.4 Conclusion

The 94-vs-62 discrepancy is closed as **count-scope mismatch**, not a BUDCOM defect —
Tally's own live, machine-readable export for the exact window BUDCOM actually
queried matches BUDCOM's snapshot exactly, GUID for GUID. The user's manual 62-count
in Tally's UI was almost certainly scoped to a different date range/report than
BUDCOM's `2026-07-18`..`2026-08-16` window (51 of the 94 vouchers land on `2026-08-14`
alone, which a narrower or differently-anchored manual check could easily miss).

A real, separate, previously-undocumented architectural gap was found and recorded
during this investigation: out-of-window carried-forward Vouchers have no
re-verification/tombstone mechanism against Tally (TD-026, P2, explicitly deferred —
did not manifest in this dataset, 0 of 94 were carried forward).

One additional observation, recorded without further action per explicit instruction:
three near-identical Voucher snapshots were promoted within a 5-minute span
(`945a4735...` 01:31, `a681079c...` 01:34, `1e536f4d...` 01:36 UTC), each with
identical counts and windows — consistent with repeated taps or an app-side retry, not
flagged as a correctness issue absent further evidence.

---

## 31. Architectural decision recorded (superseded by §33 for final verdict — see below)

**Architectural decision (2026-08-16):** the 94-vs-62 investigation result is
accepted. Current controlled-pilot Voucher state is correct for the actual
Android-requested window. No Voucher sync behavior is being changed because of this
count discrepancy. TD-026 (out-of-window carry-forward has no re-verification
mechanism) is recorded as P2/deferred — not to be solved during this controlled-pilot
closure absent further physical evidence elevating it. Current-window deletion/update
semantics are confirmed correct and preserved as evidence. The repeated near-identical
snapshot promotions are recorded as an observation only.

**TD-001 remains CLOSED — physically confirmed fixed** (§28); this investigation
(§30) is a separate matter that does not reopen it.

**What changed this pass:**
- 94-vs-62 discrepancy fully reconciled with live-Tally evidence and closed as
  count-scope mismatch, not a defect (§30.1).
- Carry-forward mechanism investigated and confirmed real but not causally
  responsible for this dataset (§30.2); recorded as TD-026 for future architectural
  attention, explicitly not actioned now.
- Query-path snapshot-scoping confirmed correct from code (§30.3).
- No code changed, no data touched, no new sync triggered during the entire
  investigation.

**What has NOT changed / remains open:**
- All of §3's TD-013–TD-020 physical-confirmation gaps remain open.
- Session 2 items G, H, J, M, N, O, P remain unconfirmed — resuming now per explicit
  instruction, one at a time. Sessions 1, 3, 4, 5 have not started.

**Per explicit instruction: Session 3 still not started.** Resuming Session 2's
remaining HUMAN CHECK items now, one at a time, in the requested order: G, H, J, M,
N, O, P.

---

## 32. Follow-on investigation — 97-vs-94, then 98-vs-98 (2026-08-16, item G)

Immediately after Session 2 item G ("Sync All") reported PASS, the user observed
Tally showing 97 vouchers against BUDCOM's 94 — a second, related count question
surfaced mid-item-G, investigated with the same read-only discipline as §30 (no code
changes, no BUDCOM sync triggered by me, no Tally writes).

### 32.1 Isolating the gap to Sales, then to one date

The user supplied a physically-confirmed Tally "Optional Vouchers" report (10
Optional Sales: 31-Jul ×2, 6-Aug ×3, 13-Aug ×2, 15-Aug ×3) and a Tally "Statistics"
report (87 ordinary, Sales 47, other 40) for the matching period, together implying
97 total. A fresh, read-only live Tally XML query (same TDL request BUDCOM's
discovery phase builds, sent directly to Tally's export port, no snapshot touched)
showed **94 total, Sales 54, other-types 40** — the 40 "other" matching Tally exactly,
isolating the entire gap to Sales. A targeted date-histogram check found **zero
records dated `2026-08-15` anywhere in the live XML export**, despite that date
falling inside the requested window — while the physically-confirmed Optional report
showed exactly 3 Optional Sales on that date. This pinned the gap to one specific
date, not a general Sales-type exclusion (the other 3 Optional dates were fully
represented in the XML).

### 32.2 Working-date hypothesis — tested, not confirmed alone

The user reported ESTIMATION's Tally working/current date was `14-Aug-2026` and
changed it to `16-Aug-2026`, with no voucher data created/edited/deleted. A repeat
read-only query immediately after showed **no change** — byte-identical response
(135,640 bytes) to before the date change, still 0 records on `2026-08-15`. **The
working-date change alone did not make the 3 records export-visible.** This was
reported plainly rather than assumed away.

### 32.3 What actually flipped the export-visible count

The user then created exactly one new ordinary Sales voucher (intended date
16-Aug-2026). A repeat read-only query immediately after showed **98 total, Sales 58**
— a jump of +4, not +1. Full detail: **4 records now dated `2026-08-15`** (voucher
number `48`, appearing 4 times across 4 distinct GUIDs/MasterIDs — not `3 on 15-Aug +
1 on 16-Aug` as initially expected), and **zero records dated `2026-08-16`**. This is
a real, precise refinement of the initial expectation, reported exactly as observed
rather than rounded to match the prediction. GUID-for-GUID diff of the new 98-record
live XML against the newly-promoted BUDCOM snapshot (`66e7a81b...`, promoted
`2026-08-16T02:48:02Z`): **98 present in both, 0 Tally-only, 0 BUDCOM-only, 0
duplicate GUIDs**, identical type breakdown. BUDCOM's snapshot exactly mirrors
whatever Tally's Export interface currently exposes.

### 32.4 Conclusion — demonstrated fact vs. unproven mechanism

**Demonstrated, evidence-backed fact:** Optional vouchers can exist and be visible in
Tally's own UI/reports while temporarily absent from the `Voucher` Export-collection
XML that BUDCOM's Connector reads; subsequent ordinary voucher-entry activity in
Tally can cause previously-invisible records to become export-visible together with
the new entry. **Not proven, and not claimed:** the exact internal Tally mechanism.
The working-date change alone did not cause the transition (§32.2); only after an
additional ordinary voucher was created did the export-visible set advance, and it
brought forward 4 records together, not a clean 3+1 split by date. No single-variable
experiment isolated the precise trigger, and none is claimed.

This is closed as **NOT a BUDCOM data-loss or filtering defect** — BUDCOM correctly,
consistently, and losslessly reflects whatever Tally's Export interface exposes at
each point in time, confirmed twice via independent GUID-for-GUID diffs (94-vs-94 in
§30, 98-vs-98 here). It is recorded as a **Tally integration/export-behavior
observation** for future Connector resilience design — see TD-026 addendum in the
registry — explicitly not conflated with the separate, pre-existing out-of-window
carry-forward gap (also TD-026, §30.2), and no speculative production workaround is
being added during this controlled-pilot closure.

Session 2 item **G (Sync All) is recorded PASS** — the final synchronized state is
98/98, fully reconciled, no errors occurred during the sync itself; the count
question that arose immediately afterward was a separate Tally-export-timing
observation, not a sync failure.

---

## 33. Final verdict (superseded by §34 — see below)

**97-vs-94/98-vs-98 closed as NOT A BUDCOM DEFECT.** Confirmed twice, independently,
with GUID-for-GUID diffs against live Tally: BUDCOM never lost, dropped, or withheld
a voucher Tally's own Export interface exposed to it. Both count discrepancies this
session (§30's 94-vs-62, §32's 97-vs-94/98-vs-98) trace to Tally-side scope/timing
behavior, not BUDCOM extraction, snapshot, or API logic.

**What changed this pass:**
- Session 2 item G (Sync All) confirmed PASS: final synchronized state 98/98, fully
  reconciled (§32.3, §32.4).
- Tally export-visibility timing behavior observed and documented as a real,
  evidence-backed fact, explicitly without overclaiming the internal mechanism
  (§32.4) — kept separate from TD-026's out-of-window carry-forward gap.
- No code changed, no speculative workaround added, no data touched throughout.

**What has NOT changed / remains open:**
- All of §3's TD-013–TD-020 physical-confirmation gaps remain open.
- Session 2 items H, J, M, N, O, P remain unconfirmed. Sessions 1, 3, 4, 5 have not
  started.

**Per explicit instruction: Session 3 still not started.** Resuming Session 2 item H
(repeated refresh / freshness consistency) next.

---

## 34. Session 2 — COMPLETE (2026-08-16)

All Session 2 items (§11) are now confirmed. Combined report:

| Item | Result | Notes |
|---|---|---|
| B — APK upgrade identity/continuity | PASS | Satisfied by earlier continuity.15 in-place install history |
| F — Connection/pairing | PASS | Android reached the Connector without manual reconnect throughout |
| G — Sync All | **PASS** | Final synchronized state 98/98, fully GUID-reconciled against live Tally (§32) |
| H — Repeated/manual refresh | **PASS** | Confirmed stable across repeats, no reversion to stale data |
| I — Stale-first-refresh check | PASS | Data reflected each fresh sync, not stale pre-upgrade state |
| J — Ledgers | **PASS** | Ledger opens, statement/entries sensible, Dr/Cr presentation correct |
| K — Vouchers (list) | PASS | Row layout/party-name display confirmed correct |
| L — Voucher details | PASS | Header/metadata/narration/ledger+inventory lines confirmed correct |
| M — Voucher type coverage | **PASS** | Automated: all 8 required types present in the 98-voucher snapshot, all `data_quality: complete`, all `active` (§34.1). Visual: representative types opened correctly. Optional/Estimate framing confirmed unconditional and non-authoritative (§34.1) — unchanged, no semantic issue |
| N — Voucher PDF | **PASS** | Opens; party/date/voucher number/amount correct; layout usable |
| O — Ledger PDF | **PASS** | Opens; ledger identity/period/balance/entries sensible |
| P — Android share/save | **PASS**, with one recorded limitation | Share sheet/WhatsApp opens correctly, correct PDF attached, no crash. **Limitation found and recorded as TD-028** (§34.2) — not a pilot blocker |

### 34.1 Automated verification for M (before asking for visual confirmation)

Queried the active snapshot's `voucher_headers` directly (read-only), grouped by
type/status/quality:

```
Contra: 1, Credit Note: 1, Debit Note: 1, Journal: 1, Payment: 1,
Purchase: 1, Receipt: 34, Sales: 58   (total 98)
```

All rows `voucher_status: active`, all `data_quality: complete` — none incomplete,
none cancelled. All 8 required types (Sales, Receipt, Payment, Contra, Purchase,
Credit Note, Debit Note, Journal) present and non-zero.

Verified from Android source (`AndroidInvoiceShareCoordinator.kt`) that every
generated voucher PDF is unconditionally headed `"ESTIMATE"` with the review-only
footer, regardless of voucher type or Optional status — matching the
already-approved design (§11 item P, first confirmed 2026-08-15) exactly. No
semantic change was needed or made.

### 34.2 TD-028 — PDF preview-before-share gap (found and deferred)

Physically found during item P: generating a Voucher/Ledger PDF and invoking
WhatsApp/share attaches the PDF directly without an in-app preview step first. PDF
content itself was independently confirmed correct via items N/O — this is a pure UX
gap, not a data or correctness issue. **Explicit user decision (2026-08-16): defer to
post-MVP-1 controlled pilot, do not implement now.** Recorded as TD-028 (P3) in the
registry.

### 34.3 Session 2 verdict

**Session 2 is COMPLETE.** All items B through P are confirmed PASS. Two real,
evidence-backed findings surfaced during Session 2 and are fully recorded, neither a
pilot blocker: TD-026/TD-027 (Tally-side Voucher count/export-timing observations,
§30/§32, both explicitly deferred) and TD-028 (PDF preview-before-share UX gap, this
section, explicitly deferred). TD-001 (the original Session 2 blocker) remains
CLOSED — physically confirmed fixed (§28).

**Session 3 (Connectivity resilience, §11) may now begin.** Sessions 1, 4, 5 remain
outstanding and unscheduled.

---

## 35. Session 3 — items Q, R (2026-08-16)

| Item | Result | Notes |
|---|---|---|
| Q — Transient Wi-Fi interruption, same network (off/on) | **PASS** | Automatic recovery, no manual refresh needed |
| R — Real network-address change (moved to mobile hotspot) | **FAIL** | Connector became completely unreachable — see investigation below |

### 35.1 Item R investigation — root cause

Physical sequence: Desktop and Android both moved to a mobile hotspot
(SSID "JioFiber-PARme_5G", a portable hotspot device). Old IP `192.168.29.34` →
new IP `10.142.207.231`. No re-pairing, QR re-scan, or manual IP entry was
performed. Android reported "Connector is unavailable"; Desktop's own Connector
Status showed "Could not reach the Connector." Fixing the Windows network
profile to Private did not restore connectivity.

Read-only diagnosis (PowerShell `Get-NetIPConfiguration`/`Get-NetConnectionProfile`/
`Get-NetTCPConnection -OwningProcess <connector pid>`/`Get-NetFirewallRule`, `curl`
against loopback/localhost/both IPs, and `budcom-desktop.log` correlation) traced
the failure through the required layer order:

1. **Connector process** — alive, responsive (PID confirmed via `Get-NetTCPConnection`).
2. **Network binding** — **first failing layer.** The Connector's HTTP/HTTPS
   listeners were bound only to the old address, `192.168.29.34:8080`/`8443`.
   `curl` to `127.0.0.1`, `localhost`, and the new hotspot IP `10.142.207.231`
   all returned HTTP 000. Desktop's own health-check client was itself still
   dialing `192.168.29.34` — proving the staleness lived in the shared
   `activeNetworkAdapter` resolution (main.ts), not merely in the spawned
   child's own config.
3. **Windows Firewall** — ruled out as the cause: ~11 matching Budcom/Connector
   Private-profile rules were present and correctly enabled. (Loopback failing
   independently confirms the firewall was never the blocker — a firewall rule
   cannot block `127.0.0.1`.)
4. **Hotspot client isolation** — explicitly disproven, not assumed: even the
   machine's own loopback traffic failed, and a hotspot's peer-isolation
   setting cannot affect a machine's own loopback traffic.
5. **mDNS/Android discovery/reconnect** — never reached; nothing to discover,
   since no listener was reachable at any address.

**Verdict: Desktop-side defect, not TD-017, not hotspot isolation.** Root
cause: `route-querier.ts`'s PowerShell-shelled route query intermittently
failed outright during the transition (`network_resolution_failed`, repeated
in the log), and even on polls where it *succeeded*, it could return a
structurally-valid adapter entry still carrying the just-departed network's
address (Windows' route table observed to transiently lag a real network
change). `network-change-watcher.ts` routed a query exception to `onError`
only, with no retry bound and no invalidation of stale state; nothing
cross-checked a resolved adapter's address against what the OS actually had
currently assigned. Recorded as **TD-029** (P0) in the registry, distinct
from TD-017 (see registry entry for the exact relationship — TD-017's own
Android-side fix and status are unaffected).

### 35.2 Architectural decision and fix (2026-08-16)

Approved as an MVP-1 P0 blocker, fixed at the smallest robust scope rather
than a networking rewrite. Full detail, evidence, and the exact governing
requirements are in TD-029's registry entry. Summary of the fix:

- `route-querier.ts`: added `getLiveIpv4Addresses()` — a synchronous,
  no-process-spawn read of `os.networkInterfaces()`, immune to the shelled-out
  route query's staleness window.
- `active-network-resolver.ts`: added `excludeStaleAddresses()`, applied to
  the raw candidate list *before* selection, so a stale-but-structurally-valid
  entry is never a candidate.
- `network-change-watcher.ts`: applies the live-address cross-check before
  resolving; tracks consecutive query failures and fails closed (explicit
  null-adapter signal with a clear "reconnecting" reason) after a bounded
  count (default 3, ~10-15s at the 5s poll interval) rather than staying
  silently stale indefinitely.
- `main.ts`: wires the real live-address check into both the periodic watcher
  and the manual Start/Restart re-resolution path.
- No changes were needed to `route-backed-lifecycle-override.ts` or
  `trusted-lan-rebind-coordinator.ts` — the coordinator already stopped the
  current child and refused to start a replacement for a null-adapter
  resolution; the fix's job was ensuring that resolution correctly *becomes*
  null (or the genuine new address) instead of silently staying stale.

**Automated validation:** Desktop 696/696 tests passing (123 in the network
module, including 20 new/updated tests for this fix — live-address filtering,
bounded-failure fail-closed signaling, counter-reset-on-success, post-fail-closed
recovery, and an end-to-end test wiring a real `TrustedLanRebindCoordinator`
through the exact old-IP → transitioning → new-IP sequence). Connector
1414/1414. Contract 5/5. Desktop `tsc` (main/preload/renderer) and build clean.

**Physical retest required before Session 3 can continue past item R** — see
§36.

---

## 36. TD-029 fix candidate production (2026-08-16)

Committed at `5deaf60392ce8980ec0848f073d3dcb791804445` (tree clean at build
start — three pre-existing, unrelated untracked files under `docs/planning/`
and `docs/product*` were stashed for the build and restored immediately after,
untouched). Full pipeline run: connector lint/build/test (1414/1414), desktop
lint/build/test (696/696), contract tests (5/5), `npm audit --omit=dev` (0
vulnerabilities), NSIS packaging, package-boundary and packaged-runtime-contract
checks, packaged-connector-dependency inspection, manifest generation and
verification — all stages **PASS**.

| Field | Value |
|---|---|
| Desktop version | `0.4.13` (bumped from `0.4.12`) |
| Connector version | `0.4.4` (unchanged — this fix is Desktop-only) |
| Installer | `BudcomDesktop-0.4.13-x64-setup.exe` |
| SHA-256 | `a4c479cf0d4954c6c5f9e175f5533cd5c6589644a677f6bac4a46174156dcb0c` |
| Size | 106,071,734 bytes |
| Commit | `5deaf60392ce8980ec0848f073d3dcb791804445` |
| Output path | `release/controlled-pilot/0.4.13/artifacts/` |
| Android | Unchanged — `continuity.15`, versionCode 16, unaffected by this fix |

**Next required step: the physical retest specified in the architectural
decision (repeat item R exactly, both devices, against this candidate).**
Do not proceed to PDF/USB/restart testing or the remainder of Session 3 until
this passes.

---

## 37. Autonomous hardening run — TD-030 found and fixed (2026-08-16)

Continuation of item R under an explicit autonomous mandate (user away):
physically retesting the 0.4.13 candidate confirmed TD-029's fix works
(Desktop/Connector correctly moved to the new hotspot address), but Android
remained offline. Investigated further using the connected Android device
over ADB per the mandate's authorization.

### 37.1 Investigation

Live evidence gathered (all read-only, no Connector/Tally state touched):

- `Get-NetTCPConnection -OwningProcess <connector pid>` and a local HTTP
  probe confirmed the Connector was correctly listening and reachable at its
  new address — the Desktop-side rebind (TD-029) was working as designed.
- A live `bonjour-service` browse of `_budcom._tcp.local` from the Desktop
  machine (using the Connector's own production discovery library, not a
  simulation) showed the advertisement carried the correct IPv4 address
  **plus 2-3 IPv6 addresses** with nothing listening on them.
  `Get-NetTCPConnection` confirmed the HTTP/HTTPS servers are IPv4-only —
  confirmed unconditionally in the server bind code, which always passes an
  explicit IPv4 literal to `.listen()`.
- Traced to `mdns-advertiser.ts`: the published options carried no address
  restriction, so `bonjour-service` auto-enumerated every local address via
  `os.networkInterfaces()`, IPv4 and IPv6 alike, and published a record for
  each — independent of what the HTTP server actually bound to.
- Android's `NsdConnectorDiscoveryService.kt` reads exactly one address per
  resolved service (`NsdServiceInfo.host`) — a known Android platform
  inconsistency in which address family wins when both are advertised.
  `AuthenticatedConnectorEndpointResolver.kt` (TD-017's fix) then has no
  fallback if that one candidate is unreachable.
- ADB device connected (`10BF44124K000E3`); by the time device access was
  available both Desktop and Android had already returned to the shared home
  network (confirmed: Desktop back at `192.168.29.34`, Android's `wlan0` at
  `192.168.29.111`, same subnet), so the exact live failure could not be
  re-reproduced without a further physical network switch. Verified instead
  that the same over-advertisement pattern (correct IPv4 + multiple IPv6
  addresses) is present on the home network too — this is not
  hotspot-specific. Read-only inspection of the app's encrypted credential
  vault (`adb shell run-as ... secure_pairing_credential_vault.preferences_pb`,
  mtime unchanged since 2026-08-09) confirmed no `Verified` endpoint
  replacement had ever successfully persisted, consistent with rediscovery
  never reaching a working candidate.
- Direct device-side logcat proof of the exact IPv6 address Android's NSD
  resolved during the original failure could not be captured (see above).
  The fix does not depend on that proof: the over-advertisement defect is
  proven with certainty on its own and unconditionally violates "never
  advertise an address you don't listen on" regardless of which exact
  address Android picked in this one instance.

### 37.2 Fix — TD-030

Recorded in full in the registry. Summary: `mdns-advertiser.ts` now sets
`disableIPv6: true` on every publish, using `bonjour-service`'s own built-in
switch (`service.js`: unconditionally skips AAAA record construction) rather
than teaching Android to tolerate a false advertisement, per the governing
rule and the user's explicit preference for fixing at the source. A narrower,
currently-unobserved gap (bonjour-service also has no way to restrict IPv4
publication to only the actively-selected adapter, should a machine ever have
multiple eligible non-APIPA IPv4 interfaces) was identified and explicitly
not addressed — not currently triggered, and closing it would require
bypassing the library's automatic address enumeration entirely, which is a
broader change than the proven defect justifies.

Two follow-on version-drift test failures were caught by the packaging
pipeline itself (`CONNECTOR_VERSION` in `defaults.ts` not yet bumped to match
`package.json`; three tests asserting the literal prior version string in
`/health` responses) and fixed in the same pass.

**Automated validation:** `mdns-advertiser.test.ts` +2 tests. Full connector
suite 1416/1416 (was 1414). Connector `tsc`/`eslint` clean. Connector version
0.4.4 → 0.4.5.

### 37.3 Combined candidate produced

| Field | Value |
|---|---|
| Desktop version | `0.4.14` (bumped from `0.4.13`) |
| Connector version | `0.4.5` (bumped from `0.4.4` — carries TD-030) |
| Installer | `BudcomDesktop-0.4.14-x64-setup.exe` |
| SHA-256 | `7c908df4323fb5b20f4d28cf723c665d3a0fddf630f81d58c33dfc9858724ff1` |
| Size | 106,072,122 bytes |
| Commit | `c73665877900bda3a1388f716125831ee4e23195` (three commits: TD-030 fix, `CONNECTOR_VERSION` drift fix, version-string test updates) |
| Output path | `release/controlled-pilot/0.4.14/artifacts/` |
| Android | Unchanged — `continuity.15`, versionCode 16 |

### 37.4 Item R — recorded as PENDING HUMAN CONFIRMATION, not a blocker to further autonomous work

Per the explicit autonomous-run mandate: the combined TD-029 + TD-030 fix has
reached the point where only a physical retest remains. This is recorded as
**PENDING HUMAN CONFIRMATION** (Desktop + Android both move to a different
network, confirm automatic reconnect with no re-pair/QR/manual IP, confirm
sync succeeds both directions) and folded into the final consolidated human
validation checklist rather than stopping the autonomous run. Continuing to
the next hardening area.

---

## 38. Autonomous hardening run — Parts 2-13 final audit (2026-08-16)

Continuation of the same autonomous mandate: software-only audit of every
remaining MVP-1 hardening area that does not require physical human
participation. Each area below reports what was actually checked, not an
assumed pass — where existing automated coverage was reviewed and found
sufficient, that is stated as such rather than re-implemented redundantly.

### 38.1 Restart/lifecycle resilience

Reviewed `ConnectorLifecycleService`, `TrustedLanRebindCoordinator`,
`CrashRecoveryScheduler`, `MdnsAdvertiser`, and `connector-session-restart`
test coverage directly. Confirmed already automatically proven: duplicate
Connector launch prevention (`detects an already running connector and
avoids duplicate launch`, `one Desktop owns exactly one Connector child`),
bounded crash/restart backoff with max-attempt exhaustion, graceful
shutdown, persisted company selection/trusted-device/Connector identity
surviving a full stop-start cycle including expired-lease renewal, mDNS
advertiser publisher-created-once-across-republishes, runtime-integrity
bind/fingerprint verification across restart, and Desktop-level
single-instance OS lock (`quits when lock is not acquired`) preventing a
second Desktop process outright. No defects found; nothing implemented.
Genuinely irreducible: an actual Desktop restart-button click observed
end-to-end in the real UI, an actual Android OS-level reboot, and an actual
Windows OS restart with auto-start — none of these can be simulated. Moved
to the Final Human Validation Checklist (§38.6) as items S/T/U.

### 38.2 Private USB storage hardening

Reviewed `private-storage-resolver`, `private-storage-locator-store`,
`removable-volume-enumerator`, and `private-storage-guard` test coverage.
Confirmed already automatically proven: only enumerator-reported removable
drives are ever inspected (never a fixed disk), exact vaultId matching
(lookalike-drive rejection), drive-letter rediscovery, fail-safe refusal to
start **and no database file created** when the marker is absent/mismatched
(directly proves no competing canonical database can be silently created),
hot-removal detection, corrupt-JSON/unsupported-schema-version handling
(never fabricates a trusted record), backup-before-overwrite, and
transaction/WAL atomicity (`sync-batch-atomicity`,
`transaction-concurrency-safety`, `sqlite-backup-restore`,
`xml-import-reservation-concurrency`). TD-024 remains fixed; TD-025 remains
an accepted, documented P2 limitation — reviewed, not reopened, no
contradictory evidence found. No physical USB action was taken (per the
explicit constraint) and none was needed to reach these conclusions.
Genuinely irreducible: items V-AB (§11, Session 5) all require actually
attaching/removing a physical drive. Moved to §38.6.

### 38.3 Data integrity / sync final audit

Reviewed `voucher-snapshot-sync.test.ts` and the equivalent Ledger/Stock-item
suites directly. Confirmed already automatically proven, by name, for
Vouchers (and mirrored for Ledgers/Stock items): snapshot promotion and
archiving of the prior snapshot, idempotency (`creates no duplicate logical
voucher when an identical re-extraction changes nothing`), company-scoped
isolation (`rejects same-company overlap while allowing a different
company`, `never carries forward or otherwise touches another company's
vouchers`), current-window add/edit/delete semantics (`replaces the old
value of an edited voucher... with no duplicate row`, `removes a
cancelled/deleted voucher from the active view once its window is
resynced`), rollback-with-reservation-release on interrupted sync, and the
TD-001 amount-sign-conflict/illegal-character telemetry. TD-026 (out-of-
window carry-forward) and TD-027 (Tally export-visibility timing) remain
accurately documented as accepted/observational — reviewed, not reopened, no
contradictory evidence found. No defects found; nothing implemented.

### 38.4 PDF/share software preparation — TD-028 explicitly NOT implemented

Confirmed via source inspection that no preview mechanism exists anywhere in
the Android sharing code path (`feature/voucher/sharing`,
`feature/masterdata/ledger/sharing`) — matches TD-028's existing finding
exactly, no new discovery. Generation, pagination, and share-action-matrix
mechanics are well covered (30 tests across
`LedgerStatementPdfPaginationTest`, `VoucherShareActionMatrixTest`,
`InvoiceShareContentTest`).

**This autonomous mandate's Part 5 instruction ("implement the smallest
correct preview solution... if missing") was evaluated and explicitly
declined.** TD-028 was closed off by your own explicit decision during
Session 2 (2026-08-16): *"pass, one issue that pdf must be viewable before
share, which we will do post mvp-1 controlled pilot."* Implementing preview
now would invalidate that accepted decision — the autonomy rules governing
this run require escalation rather than autonomous override in exactly this
situation (rule 8: "a change would invalidate an accepted product/governance
decision"). TD-028 remains open, P3, explicitly deferred, unchanged. If you
want preview implemented now after all, say so explicitly and it can be
scoped as a normal follow-up.

### 38.5 Performance, security/trust, and installer/release audits

**Performance/cleanliness:** reviewed this session's own changes (TD-029,
TD-030) specifically against the regression categories in the mandate — poll
interval unchanged (5s), bounded-failure counter resets on success (no
runaway retry, mirrors `CrashRecoveryScheduler`'s existing bounded pattern),
zero additional process spawns, mDNS publisher created once across
republishes (existing test), and the new TD-029 end-to-end test explicitly
proves exactly one child start per network transition (no leak). No
regressions found; no speculative optimization performed.

**Security/trust:** this session's changes touch only network resolution
(`route-querier.ts`, `active-network-resolver.ts`, `network-change-watcher.ts`)
and mDNS address metadata (`mdns-advertiser.ts`) — confirmed via commit file
scope that no authentication, credential, pairing, or fingerprint code was
modified. `AuthenticatedConnectorEndpointResolver`'s design (read directly
during the TD-030 investigation) already guarantees connectorId/fingerprint/
credential are carried through unchanged across any host/port replacement —
TD-029 and TD-030 both operate strictly upstream of that trust boundary. No
new insecure fallback, no auth bypass, no weakened firewall rule, no secret
in any new log line. No regressions found.

**Installer/release/upgrade:** version consistency (package.json ↔
`CONNECTOR_VERSION` ↔ `build/VERSION.txt` ↔ hardcoded `/health` test
expectations) was verified the hard way — the release pipeline's own
drift guard caught a real mismatch mid-session (§37.2) and it was fixed
before packaging. Manifest generation/verification, package-boundary
inspection, packaged-runtime-contract check, and packaged-connector-
dependency inspection all passed fresh for the 0.4.14/0.4.5 candidate.
Android version (`continuity.15`, versionCode 16) confirmed unchanged and
matches documentation — Android was not touched this session. No new signing
credentials were minted; the debug-signed-only limitation remains unchanged
and accurately documented.

### 38.6 Final automated regression (fresh run, this session, HEAD `94eda59`)

| Component | Result |
|---|---|
| Connector — full suite | **1416/1416 passing** (159 test files), `eslint` + `tsc --noEmit` clean |
| Desktop — full suite | **696/696 passing** (67 test files), `tsc` (main/preload/renderer) + build clean |
| Contract tests | **5/5 passing** |
| Android — debug JVM unit tests | **1002/1002 passing**, 0 skipped, 0 errors |
| Android — release JVM unit tests | **1002/1002 passing**, 0 skipped, 0 errors |
| Android — lintDebug / lintRelease | **0 issues** (both) |
| Android — assembleDebug / assembleDebugAndroidTest | **BUILD SUCCESSFUL** |

No skipped failing tests, no weakened assertions, no fabricated evidence —
every count above was read directly from JUnit XML / vitest / Gradle output,
not inferred.

### 38.7 Documentation/governance consolidation

Registry, this closure-status document, and the Quality Scorecard were kept
in agreement incrementally through §35-§38 rather than as a separate final
pass — confirmed via a repo-wide search that no other canonical doc
references a stale Desktop/Connector version number. No contradictions found
requiring resolution beyond what §35-§37 already corrected. Nothing in this
run rewrites or removes prior evidence; TD-026/TD-027/TD-028/TD-025 remain
exactly as previously recorded.

### 38.8 Clean-tree / commit discipline

Working tree is clean at HEAD `94eda59371efc3de3c9e4eee3d9e3f11f594b06e`
except for three pre-existing untracked paths
(`docs/planning/BUDCOM-CONNECT-CONTACTS-UNIVERSAL-PARTY-REFERRAL-TREE-SPEC.md`,
`docs/product-design/`, `docs/product/`) that predate this session and are
outside this mandate's scope — left untouched, not added to `.gitignore`, not
hidden. All temporary diagnostic scripts and log files created during this
run were deleted after use and never committed (verified via `git status`
after each). Nothing was pushed.

### 38.9 FINAL HUMAN VALIDATION CHECKLIST

Optimized for minimum time: nothing below was already physically proven
against the current 0.4.14/0.4.5/continuity.15 candidate combination, and
nothing physically proven earlier (Session 2, §34) was invalidated by this
session's changes (TD-029/030 touch only network resolution and mDNS
metadata, never Ledger/Voucher/Sync/PDF code). Recommended order groups by
physical setup so nothing is repeated.

**A. Network transition (combined TD-029 + TD-030 retest — replaces the
original item R)**
- Action: With Desktop 0.4.14 and Android continuity.15 both connected and
  working normally, move the Desktop machine to a different network (the
  same mobile hotspot used before is fine) — a genuine address change, not a
  same-network toggle. Then move it back to the original network.
- Expected: Both directions — Android reconnects automatically within a
  short bounded time, no re-pairing/QR/manual IP entry at any point; run
  "Sync all" and a manual refresh after each transition.
- Pass criterion: Both transitions reconnect automatically; both syncs
  succeed.
- Stop condition: Any reconnection requires re-pairing, a new QR code, or
  manual IP entry — report exactly what was shown and how long you waited
  before concluding TD-029/TD-030 did not fully resolve item R.

**B. Restart (batch together — Desktop/Connector, Android, Windows)**
- S. Restart the Connector from Desktop's Settings (or fully close/relaunch
  Desktop) with a company already selected. Expected: company
  selection/dashboard recover automatically within ~70s, no manual refresh.
- T. Fully restart the phone (not just the app). Expected: app reopens,
  reconnects, shows correct state with no manual intervention.
- U. Restart the Windows machine; let Desktop auto-start (or start it
  manually if not configured to auto-start). Expected: Desktop/Connector
  reach healthy state; Android reconnects without re-pairing.
- Pass criterion: all three PASS as described.
- Stop condition: any of the three requires manual reconnection/re-pairing
  beyond what's described, or company selection/data does not survive.

**C. Private USB storage (do last of the non-PDF items, safest order per
§10)**
- V/W: restart Desktop with the drive attached, then with it removed —
  confirm normal startup vs. the storage-unavailable screen (Retry/Locate/
  Exit), never a silent fallback or crash.
- X: pull the drive while Desktop is idle — confirm the Connector stops
  within ~10s; the dashboard will show a generic "Disconnected" rather than
  a storage-specific message — this is the already-documented TD-025 limit,
  expected, not a new failure.
- Y: follow the bounded procedure in §10 exactly (spare drive if available,
  local backup first) for a controlled loss during read/write/sync.
- Z: reinsert the same drive, click Retry — confirm it reconnects to the
  *same* vault/data, not a fresh empty one.
- AA: confirm no competing database appeared at the Standard/AppData
  location while the drive was disconnected.
- AB: run an Android sync against the recovered vault — confirm it succeeds.
- Pass criterion: all of V, W, X (with the TD-025 caveat), Z, AA, AB PASS;
  Y follows the bounded procedure without data loss.
- Stop condition: any silent fallback to a different storage location, any
  crash, any corruption detected at Z, or a competing database found at AA.

**D. PDF final visual review — LAST, as you requested**
- Install the TD-028 Android candidate (`continuity.16`, versionCode 17,
  §39.4) in place first — no data clear, no re-pair needed.
- Voucher PDF: from a voucher's detail screen, tap "View invoice PDF" —
  confirm Preview opens in-app (not an external app/chooser) and shows the
  correct document (party/date/voucher number/amounts/layout, "ESTIMATE"
  heading with the review-only footer, correct multipage scrolling if
  applicable, pinch-zoom feels usable). Then tap Save from within Preview,
  then Share from within Preview via WhatsApp.
- Ledger Statement PDF: from Advanced Share Options, tap "Preview PDF" —
  confirm the same in-app preview opens and shows correct content (ledger
  identity/period/opening-closing balance/Dr-Cr/transaction rows/
  pagination). Then Save and Share (including WhatsApp where applicable)
  from within Preview.
- Pass criterion: Preview opens in-app (not an external handoff) for both
  document types, content looks visually correct, Save and Share both
  complete normally from within Preview.
- Stop condition: Preview fails to open, shows the wrong/stale document, any
  visual defect or incorrect data, or a Save/Share failure.

**Estimated total human time: 20-30 minutes**, assuming no STOP condition is
hit (A: ~5-8 min including two network transitions; B: ~8-10 min, mostly
wait time; C: ~10-12 min if a spare drive is available; D: ~5-7 min
including the Android install).

### 38.10 Explicit statement

**AUTOMATED HARDENING COMPLETE — FINAL HUMAN VALIDATION PENDING.**

Every software-verifiable MVP-1 hardening task reachable without physical
human participation has been completed, tested, and documented. What
remains is exactly §38.9 above, plus the standing TD-028 preview question
(§38.4) reserved for your explicit decision, not resolved autonomously.

---

## 39. TD-028 PDF Preview — implemented (2026-08-16, architectural decision supersedes the earlier deferral)

The earlier decision to defer TD-028 to post-MVP-1 (§34.2) was explicitly
superseded by a follow-on architectural decision: the user wants PDF review
to be the *last* stage of the final human validation session, which
requires Preview to actually exist first. Full detail, evidence, and the
exact governing constraints are in TD-028's registry entry; summary below.

### 39.1 What was built

A shared `core/pdf/` package — `PdfPageRenderer`/`AndroidPdfPageRenderer`
wraps the platform's own `android.graphics.pdf.PdfRenderer` (the read-side
counterpart to the `PdfDocument` API this app already uses to *generate*
every PDF) and `PdfPreviewScreen` is one Compose screen reused by both
Voucher and Ledger. **No new dependency was added** — evaluated and
preferred over a third-party PDF-viewer library per the explicit
reuse-before-adding-dependencies instruction.

- **Voucher:** the bottom sheet's separate "Share PDF"/"Save PDF" buttons
  became one "View invoice PDF" action that opens Preview; Save and Share
  are explicit buttons *inside* Preview, acting on the exact already-
  prepared PDF (never a second, separately regenerated document). The
  pre-existing direct `SharePdf`/`SavePdf` events are untouched.
- **Ledger:** the Advanced Share Options sheet already had a "Preview PDF"
  button, but it launched a bare `ACTION_VIEW` intent with no guarantee any
  app could handle it — effectively non-functional on many devices. It now
  opens the same in-app preview. The fast one-tap Share Ledger path and the
  other Advanced Share Options destinations (WhatsApp to Party, WhatsApp —
  choose recipient, Share via…, Save PDF) are unchanged — deliberately not
  forced through Preview, to avoid degrading an already-shipped fast path.
  **Flagged for your own call, not decided unilaterally:** if you want every
  Ledger share path to require Preview first too, that's a small follow-up,
  not something this pass assumed.
- Save/Share/WhatsApp continue to use the exact same coordinator methods,
  FileProvider URIs, cache-file lifecycle, and recipient-resolution logic
  every existing path already used — nothing about those was redesigned.

### 39.2 Known, accepted limitation

Preview's open/loading state is plain Compose state, not
`SavedStateHandle`-backed. A configuration change (rotation) safely
re-renders the same file; process death while backgrounded returns the user
to the underlying Voucher/Ledger screen rather than a re-opened preview —
fails closed (no crash, no stale content), matches how this screen already
treats other transient UI-only state, and is not remediated here since doing
so is out of scope for a narrow workflow-completion fix.

### 39.3 Automated validation

31 new/updated focused tests (Voucher ViewModel +12, Ledger ViewModel +4,
new instrumented `PdfPageRendererTest` +7 and `PdfPreviewScreenTest` +6,
plus existing screen tests updated for the new button). Full suite: Android
JVM debug and release 1,016/1,016 each (was 1,002), 0 skipped/errors;
`lintDebug`/`lintRelease` 0 issues each; `assembleDebug`/
`assembleDebugAndroidTest` both successful. Connector 1,416/1,416, Desktop
696/696, contract 5/5 all reconfirmed unaffected — zero files touched
outside `apps/budcom_android` this pass.

**APK size impact:** measured the identical commit with and without this
change via `git stash` (controlled, apples-to-apples): +29,266 bytes,
**+0.213%** — confirms the "no new dependency" claim; the delta is purely
the new code itself.

A second-pass critical self-review (17 questions covering preview/share
document identity, temp-file safety, permissions, FileProvider security,
Optional/Estimate framing, accounting-data mutation, and regression risk)
found no issues requiring a fix beyond the one limitation recorded in §39.2.

### 39.4 Candidate produced

| Field | Value |
|---|---|
| Android version | `versionCode 17` / `versionName 0.1.1-continuity.16` (bumped from `16`/`continuity.15`) |
| Artifact | `app-debug.apk` (debug-signed — no signed-release path exists in this environment, an already-documented limitation, unchanged) |
| SHA-256 | `0c2e724e07521724ee744114a0b6a74318d07f0edecc81f4026f5d5e742ea136` |
| Size | 13,960,832 bytes |
| Desktop | Unchanged — `0.4.14` (this work never touched `apps/budcom_desktop` or `connector`) |
| Connector | Unchanged — `0.4.5` |

### 39.5 Status

**TD-028: Implemented — automated validation passed. Physical/visual
confirmation pending**, reserved for the final human validation session,
last, exactly as requested. Not marked CLOSED — only your own visual
approval of representative generated PDFs can close it.

---

## 40. Session 3 item R — round 3: Android-path investigation, TD-031 found and fixed (2026-08-16)

### 40.1 Windows Public→Private retest: recovery confirmed working, Android still failed

A live retest against Desktop 0.4.14 + Connector 0.4.5 + Android continuity.16
(versionCode 17) on the same mobile hotspot found the Windows network profile
was categorized Public, which by existing, already-approved design
(`evaluateTrustedLanEligibility()`) blocks trusted-LAN exposure entirely — an
environment condition, not a BUDCOM defect. Switching the profile to Private
was the correct recommended action.

After the switch, fresh evidence (not reused from the Public-state
investigation) confirmed the TD-029+TD-030 automatic-recovery fix worked
completely: the Connector process restarted itself, reached healthy state,
correctly bound to the current hotspot IP, Tally was reachable, and company
selection was preserved — four independent successful discovery calls
observed in the log. **This definitively ruled out TD-029 and TD-030 as
causes of the continuing Android failure.** One narrow, separate ambiguity
was noted and explicitly not chased further at the time: a single late
"Company discovery failed" log entry appeared after the successful recovery
sequence, and whether the Desktop dashboard UI was showing a stale vs.
currently-accurate state as a result was left unresolved — see §40.5.

### 40.2 Android-path investigation — root cause (TD-031)

With Windows/Connector/network infrastructure proven fully healthy — and a
raw TCP/HTTP request from the Android device to the Connector's plain HTTP
port independently succeeding — the investigation moved to the Android
application path specifically, via ADB, tracing NSD discovery, the
production authenticated-endpoint path, and TD-017's rediscovery mechanism
end-to-end.

Live, decisive evidence: `curl -sk https://<hotspot-ip>:8080/health` (the
mDNS-advertised port) returned HTTP 000 with `ssl:0.000000s` — the TLS
handshake never even started, because nothing HTTPS-capable listens on that
port — while `curl -sk https://<hotspot-ip>:8443/health` (the Connector's
real, separate HTTPS/authenticated port) returned a healthy 200 in ~13ms.

Root cause, proven by source trace against that evidence: the Connector's
mDNS advertisement had **never once** published the HTTPS/authenticated port
in its TXT record — only the plain HTTP port, via the SRV record — and
Android's `AuthenticatedConnectorEndpointResolver.kt` (TD-017's rediscovery
mechanism) used that same discovered HTTP port *as* the `securePort` for its
mandatory pinned-TLS verification handshake. Every verification attempt, on
every network change, on every installation, since TD-017's fix first landed
(`cb504e4`), therefore tried to speak TLS to a plain HTTP server — which
fails unconditionally. TD-017's own rediscovery logic (bounded discovery,
fingerprint verification, candidate replacement) was never the problem; it
was simply never handed a usable secure endpoint to try. Recorded as
**TD-031** (P0) in the registry — full detail, evidence, and the exact
distinction from TD-017/TD-029/TD-030 is in TD-031's own entry.

This retroactively and precisely explains why TD-017's registry status
persistently read "automated validation passed; physical confirmation
pending" through the entire engagement: every physical retest attempt was
structurally doomed to fail for this reason, while the automated
`MockWebServer`-based tests could never expose it, because their fixtures
used one TLS-capable mock server's single port as both the "discovered port"
and (implicitly) the "secure port" — an artifact with no equivalent in real
production topology, where the Connector always exposes two distinct ports.

### 40.3 Fix (2026-08-16)

Additive-only, per the same "never advertise what you don't listen on"
invariant established for TD-030:

- `connector/budcom_connector/src/services/discovery/mdns-advertiser.ts`:
  publishes a new, separate `securePort` TXT field, omitted entirely (never
  a placeholder) when secure transport is not running.
- `connector/budcom_connector/src/bootstrap/register-services.ts`: wires the
  real `secureTransportPort`/`secureTransportEnabled` config into the
  advertiser.
- `apps/budcom_android/.../core/discovery/ConnectorDiscoveryPort.kt`: adds an
  additive, nullable `securePort` field to `DiscoveredConnector`.
- `apps/budcom_android/.../core/discovery/NsdConnectorDiscoveryService.kt`:
  parses the new TXT field.
- `apps/budcom_android/.../core/connectorauth/domain/AuthenticatedConnectorEndpointResolver.kt`:
  filters out any candidate with no advertised secure port, and verifies TLS
  against `candidate.securePort`, never `candidate.port`.
- Legacy, unauthenticated consumers (`ConnectorConnectionResolver`,
  `ConnectorEnrolmentService`) were deliberately left untouched — they
  correctly continue to use the primary HTTP port.
- Fixed 5 existing Android test fixtures that had given the "discovered
  port" and "secure port" the same mock-server-port value — the exact
  artifact that structurally hid this bug — to deliberately distinct dummy
  values, so a regression to the wrong field now fails loudly. Added one new
  explicit regression test asserting a candidate with no advertised secure
  port is never dialed.

Committed at `7f40a92` (connector fix), `b19499e` (android fix), `f4eefe8`
(connector hardcoded-version test fixtures), `b0ecb11` (version bumps),
`fcb936c` (bundled `VERSION.txt` resync) — tree clean at each build start;
the three pre-existing, unrelated untracked files under `docs/planning/` and
`docs/product*` were stashed for the build and restored immediately after,
untouched.

### 40.4 Automated validation and candidates produced

Connector: `mdns-advertiser.test.ts` +3 tests. Full connector suite: 159
files / 1,419 tests passing (2 unrelated files' timeouts on the first
concurrent run were confirmed transient CPU-contention flakes — both pass
cleanly in isolation, re-run with no concurrent load, no regression).
Android: 1 new explicit regression test. Full Android regression
(`testDebugUnitTest`, `testReleaseUnitTest`, `lintDebug`, `lintRelease`,
`assembleDebug`, `assembleDebugAndroidTest`): 1,017/1,017 debug unit tests,
1,017/1,017 release unit tests, 0 lint issues, clean assemble — all passing.

| Field | Value |
|---|---|
| Desktop version | `0.4.15` (bumped from `0.4.14`) |
| Connector version | `0.4.6` (bumped from `0.4.5`) |
| Desktop installer | `BudcomDesktop-0.4.15-x64-setup.exe` |
| Desktop SHA-256 | `321c0946ff2fc263a5558bf55cc34886f7358bbf5a7eb98da674d5a6144899b5` |
| Desktop size | 106,072,498 bytes |
| Android version | `versionCode 18` / `versionName 0.1.1-continuity.17` (bumped from `17`/`continuity.16`) |
| Android artifact | `BudcomAndroid-fcb936c-debug.apk` |
| Android SHA-256 | `28845dedd2d1bfd6023ad811326707a646c66c041e3c2007b37344f24b42b3db` |
| Android size | 13,960,832 bytes |
| Commit | `fcb936c320205fc87e83fecae1e4a313da2873d8` |
| Output path | `release/controlled-pilot/0.4.15/artifacts/`, `release/controlled-pilot/android/0.1.1-continuity.17-fcb936c/` |

### 40.5 Recorded, not chased further: Desktop dashboard-UI staleness ambiguity

During §40.1's Public→Private retest, one late "Company discovery failed"
log entry appeared after an already-successful recovery sequence, raised by
`CompanyService.discoverCompanies()`'s per-failure error log
(`company-service.ts`). TD-014's bounded dashboard-recovery mechanism
(`app.ts`: `reconcileBoundedRecovery()`, `runDashboardRecoveryCycle()`,
`companyLoadGeneration` staleness token) is designed to make exactly this
kind of stale/superseded late failure a no-op against an already-healthy UI
state, and is invoked from every `onStatusUpdated` push. Whether the
dashboard was actually left showing accurate state after this specific
sequence, or a genuinely stale one, was **not resolved** — it was explicitly
deferred when the investigation was redirected to the higher-priority
Android-side failure (§40.2), which is unrelated to and does not conflate
with this mechanism. **Recorded as an open item, not a proven defect**:
worth a dedicated, isolated observation pass in a future session, separate
from TD-031's own physical confirmation.

### 40.6 Next required step

Physical retest, per the acceptance standard specified for this defect: the
Desktop+Connector 0.4.15 and Android continuity.17 candidates above must be
installed and the full A→B→A network-transition cycle (e.g., home network →
hotspot → home network) repeated on both devices, confirming Android
reconnects automatically in **both** directions with **no** app restart, no
Desktop restart, no QR/re-pair, and no manual IP entry at any point. A
manual Android app restart during the retest does not constitute a PASS for
this defect — the code fix is not considered physically confirmed until that
full cycle succeeds unattended.

---

## 41. Session 3 item R — round 4: hotspot-leg physical retest FAIL, TD-032 found and fixed (2026-08-16)

### 41.1 Physical retest result

Desktop 0.4.15 / Connector 0.4.6 / Android continuity.17 (versionCode 18)
installed. Sequence: Jio Fiber → PASS (Desktop and Android both working) →
switched both devices to the Nord 5 mobile hotspot → Windows hotspot
profile set to Private → Desktop/Connector recovered and worked normally
on the hotspot → **Android did not recover.** Android showed "Device
offline" then, after roughly a minute, "Connector is unavailable." No
restart, re-pair, QR scan, manual IP entry, or ADB disconnect was
performed at any point — the live failed state was investigated exactly
as it stood, per explicit instruction.

### 41.2 Live investigation — Connector-side (TD-031) independently reconfirmed correct

Before assuming TD-031 was still the cause, its fix was reverified live,
not from source alone: `Get-NetTCPConnection`/`Invoke-RestMethod` confirmed
the Connector healthy at the hotspot address, `connectorVersion: "0.4.6"`.
A live `bonjour-service` browse of `_budcom._tcp.local` from the Desktop
machine, at the exact time of the Android failure, returned:

```
{ "name": "VIDHI", "port": 8080, "addresses": ["10.142.207.231"],
  "txt": { "connectorId": "...", "authRequired": "false", "securePort": "8443" } }
```

**TD-031's fix is correct and working.** The failure was proven to be
downstream of it, not a recurrence of it.

### 41.3 Live investigation — Android-side, without restarting the app

Via ADB against the live, still-running process (PID confirmed, package
`com.budcom.android.debug` continuity.17/versionCode 18):
`AuthenticatedConnectorApi` was retrying continuously — every 15-45
seconds, never stuck, never terminal — for the entire ~9 minutes the
device remained on the hotspot, and every attempt classified as
`TIMEOUT`. Two brief `CONNECTION_REFUSED` results appeared during one
transient Wi-Fi flap and were not pursued further as a separate cause.

Decisive evidence: `adb shell ip maddr show wlan0`, polled at 300ms
resolution across multiple full 5-second discovery windows while the
device was confirmed on the hotspot (SSID "Nord5", `10.142.207.147`),
**never once** showed the mDNS multicast group `224.0.0.251` joined on
the interface. No mDNS listener — app-level or system-level — was
actually receiving multicast traffic on this device during this period,
regardless of anything the Connector correctly advertised.

**Environmental note, recorded for completeness, not as product
evidence:** partway through this investigation the device's own Wi-Fi
silently roamed itself from the Nord 5 hotspot back to the original
Jio Fiber network, confirmed via `dumpsys wifi`'s own connection-event
history (`CMD_IP_CONFIGURATION_SUCCESSFUL`, freq 5785MHz, matching
JioFiber_5G) — with no restart, re-pair, or manual action by the tester
or this investigation. The dashboard afterward correctly showed "Fully
operational" against the original, still-valid Jio endpoint — genuine
success once back on that network, not evidence of hotspot recovery. This
is an OS-level Wi-Fi auto-reconnect behavior outside BUDCOM's control,
consistent with prior guidance to treat transient device/environment
instability as exactly that, not a product defect.

### 41.4 Root cause — TD-032

Source trace confirmed the mechanism: this test device (vivo I2407i,
confirmed via `/proc/vivo_rsc` references in its own logs) belongs to the
BBK Electronics family (vivo/OPPO/OnePlus/iQOO), whose Wi-Fi stacks are
documented to filter multicast frames at the driver/firmware level to
save power unless some app on the device currently holds a
`WifiManager.MulticastLock` — `NsdManager` does not acquire one on the
app's behalf on these builds. `AndroidManifest.xml` already declared
`CHANGE_WIFI_MULTICAST_STATE`, but no code anywhere ever created or held
the lock itself. Recorded as **TD-032** (P0) — full detail in the
registry entry, including its explicit distinction from TD-017/029/030/031.

This means even a Connector that correctly advertises everything TD-031
needs can never be found by Android on an affected device, because
discovery itself never receives the multicast packets carrying that
advertisement — a defect layered underneath TD-031, not a recurrence of
it.

### 41.5 Fix, validation, and candidate produced

Additive: `MulticastLockController` (new seam over
`WifiManager.MulticastLock`, with a `withLock{}` helper mirroring
`kotlinx.coroutines.sync.Mutex.withLock`'s exact signature so existing
early-return control flow in `NsdConnectorDiscoveryService.discover()`
keeps working unchanged), wired via Hilt, held for the full discovery
pass and always released. No change to trust, fingerprint, credential, or
pairing logic. Committed at `22381e1` (fix) / `1ae3b79` (version bump).

4 new focused unit tests (acquire/release ordering, exception safety,
cancellation safety, non-local-return safety). Full Android regression:
1,021/1,021 debug unit tests (was 1,017), 1,021/1,021 release unit tests,
0 lint issues, clean assemble — all passing.

| Field | Value |
|---|---|
| Android version | `versionCode 19` / `versionName 0.1.1-continuity.18` (bumped from `18`/`continuity.17`) |
| Artifact | `BudcomAndroid-1ae3b79-debug.apk` |
| SHA-256 | `4c84276cdc1d2ac4160ae1eddbfb1ee17d7550757cb84dd3335111664226cdfe` |
| Size | 13,960,832 bytes |
| Desktop / Connector | Unchanged — `0.4.15` / `0.4.6` (TD-032 is Android-only) |
| Output path | `release/controlled-pilot/android/0.1.1-continuity.18-1ae3b79/` |

### 41.6 Next required step

Physical retest, same standard as before: Desktop/Connector 0.4.15 +
Android continuity.18, full A→B→A hotspot cycle, both directions, no app
restart, no Desktop restart, no QR/re-pair, no manual IP entry. Given this
device's Wi-Fi was observed to roam networks on its own mid-test last
time, watch for or screen out auto-reconnect behavior during the retest
so a genuine app-level result isn't confounded by an unrelated OS-level
roam a second time.
