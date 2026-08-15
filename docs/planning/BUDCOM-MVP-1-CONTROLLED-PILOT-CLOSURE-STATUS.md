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

### 13.5 Why this is NOT fixed this session — architectural decision needed

Per instruction #9: the real, correctly-scoped fix belongs in the **shared**
`TallyXmlResponseParser` (`assertXml10Characters`/`decodeXmlEntities`), since the same
Tally artifact can appear in any collection's free-text fields, not just Vouchers or
Groups — a narrow, Voucher-only or Group-only patch would repeat the exact "fix landed
at one call site instead of the shared layer" pattern this project has already flagged
twice before (TD-013, TD-017). That means this fix is not a "smallest robust
correction" confined to one file's already-approved behavior — it changes how the
connector's fail-closed XML validation treats a known-illegal-per-spec character across
**every** Tally collection, and requires deciding:

1. Should `&#4;`-class illegal numeric character references be silently tolerated
   (normalized to something, e.g. stripped or replaced) instead of causing a hard parse
   failure — for every collection, or only specific ones?
2. If tolerated, what should the normalized value be — dropped entirely, replaced with a
   visible placeholder, or something else? (TD-001's own original open question for
   Groups, now with materially higher stakes since it also breaks Vouchers.)
3. Does relaxing this validation at the shared parser layer weaken the "fail closed on
   anything unexpected from Tally" posture this project has built throughout, or is a
   narrowly-scoped exception (specifically for XML-1.0-illegal numeric character
   references only, nothing else) an acceptable, bounded carve-out?

TD-001 has sat open for this exact reason since 2026-07-22 (documented but not decided);
this investigation has now shown the stakes are higher than originally scoped (P0 core
workflow breakage, not P2 cosmetic display), which is exactly the kind of new evidence
that should go back to the product/architecture owner rather than be decided
unilaterally here. **Registry updated:** TD-001 escalated to P0, description/evidence/
likely-fix-location all corrected; see `docs/technical-debt/registry.md`.

### 13.6 What was done this session (investigation only, no production fix)

1. Traced the complete failure-classification chain from raw Tally response through to
   the Android-visible `parser_failure` string (§13.1).
2. Established, with a passing regression test against the real production parser, the
   exact mechanism that would produce this symptom, and tied it to the one Tally
   artifact already documented on this exact company (§13.2).
3. Confirmed from code (not assumption) that existing local Voucher data cannot have
   been affected (§13.3).
4. Escalated and corrected TD-001 in the registry to reflect the true scope and
   priority.
5. Did **not** modify any production code path — per instruction #6/#9, root cause is
   substantiated but not proven beyond reasonable doubt without a raw capture, and the
   fix shape requires an architectural decision outside this session's authority.
6. Ran the full connector suite after adding the regression test: **157 files / 1359
   tests passing** (1358 baseline + 1 new), no regressions.
7. **Did not produce a new Desktop or Android candidate** — none is needed yet; nothing
   about the current `0.4.8`/`continuity.15` candidates changes as a result of this
   investigation, since no fix was made.

### 13.7 Next physical retest required

**None right now.** Per instruction: "DO NOT tell me to repeatedly Retry until it
happens to pass." Retesting Voucher sync against ESTIMATION without a decision on §13.5
would either reproduce the same failure (if `&#4;` is genuinely present in the affected
date window) or pass by chance if the specific offending record falls outside the
default 30-day sync window on a later attempt — neither outcome would be informative.
**If you're willing to supply one additional piece of information, it could confirm or
rule out the leading hypothesis without any new build:** do you know of anything unusual
in vouchers entered/modified in ESTIMATION in the last ~30 days — a party ledger name,
narration, or reference field with special/unusual characters, or a ledger/party name
you know is affected by the existing TD-001 Group quirk that might also be used as a
Voucher party? If so, tell me what it is (or even just "yes, X ledger is affected") and
I can likely confirm the mechanism without needing raw Tally access at all.

Once §13.5's architectural decision is made (by ChatGPT) and a fix (if approved) is
implemented and regression-tested, a new Android candidate (and Desktop only if the fix
also touches shared Ledger/Stock-item paths) will be needed before retesting Voucher
sync specifically. Sessions 1, 3, 4, and 5 of §11 remain independently runnable now —
this defect only blocks Session 2 steps G onward (H through P depend on a successful
sync) for **Vouchers** specifically; Ledgers and Stock items already synced successfully
in the same run and are not blocked.

---

## 14. Final verdict (updated 2026-08-16)

**NOT READY.**

Unchanged from §12 in substance, with one new fact: physical testing has now begun and
found a real PILOT BLOCKER (§13) — fresh Voucher sync fails against live data for at
least one real company. This is in addition to, not instead of, §12's original
reasoning (no other physical evidence yet exists for the remaining TD-013–TD-020 gaps
or Sessions 1/3/4/5). The path forward is unchanged mechanically (continue §11's
sequence for the sessions this defect doesn't block) but now also requires a product/
architecture decision on §13.5 before Voucher sync specifically can be retested.
