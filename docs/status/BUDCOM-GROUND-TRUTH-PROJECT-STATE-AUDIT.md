# BUDCOM Ground-Truth Project State Audit

**Status:** Standalone, point-in-time, read-only audit snapshot — distinct from
`BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` (which stays short and links out) and
`BUDCOM-DEVELOPMENT-LEDGER.md` (the chronological milestone record). This
document exists to give an external architectural authority / Product Owner
(ChatGPT, in the session that requested it) an independently-verified,
comprehensive ground-truth report to decide the next roadmap step from,
without relying on prior reports as truth. It was produced by directly
inspecting the repository, running safe/non-destructive test suites, and
querying the live real device — not by summarizing memory or prior documents.

Content below is preserved verbatim from the audit session
(`2026-08-23`, repository HEAD `cd20b9b19f094048ed65756fdb5586f139bca882`,
device `10BF44124K000E3`). No source code, tests, configuration, or
architecture were changed to produce it, and none were changed to save it
here.

---

# BUDCOM — Latest Ground-Truth Project State Audit
*Read-only audit. No source, tests, docs, git history, config, dependencies, device state, or Tally data were modified. Nothing was pushed. No fixes were implemented. MVP-1.4 was not started.*

## CURRENT GROUND TRUTH

**Branch:** `main`
**Local HEAD:** `cd20b9b` — `fix(connector): TD-042 -- real Ledger Alias data never reached Android at all` (2026-08-23)
**origin/main HEAD:** `9ca8c7b` (fetched fresh this session)
**Ahead/behind:** local is **24 commits ahead**, **0 behind** — a clean, fast-forwardable divergence, nothing force-pushed or rewritten.
**Working tree:** clean (`git status --short` empty).
**Unpushed:** all 24 commits are local-only. Nothing has been pushed to `origin` since whatever commit `origin/main` currently points to.

Last 24 commits (all local-only, newest first):

| Commit | Date | Subject |
|---|---|---|
| cd20b9b | 08-23 | fix(connector): TD-042 — real Ledger Alias data never reached Android at all |
| 2046bbf | 08-23 | feat(android): add Creditors tab to Connect for Supplier-classified parties |
| 74fa205 | 08-23 | fix(android): TD-041 resolved — reconciliation moved off a cancellable viewModelScope job |
| 4088f66 | 08-23 | chore(android): TD-041 SQL/transaction tracer — debug-only |
| 8cbb701 | 08-23 | fix(android): TD-041 mitigation — atomic Party reads + scoped defensive retry |
| d05ce0c | 08-23 | chore(android): TD-041 diagnostic logging |
| 5322f79 | 08-23 | docs: Phase 52 — real-data validation finds TD-041 |
| fad6152 | 08-23 | fix(android): unmerged-tree Alias assertion fix |
| 024dac1 | 08-23 | fix(android): remove invalid assertDoesNotExist import blocking androidTest compile |
| 829f90e | 08-23 | fix(android): Connect UX hardening — search-leak, freshness, Alias visibility |
| 9df3e0c | 08-23 | feat(android): Connect Alias Intelligence — 10-digit mobile + 1-5 digit shortcut |
| 49513d3 | 08-23 | fix(connector): disable COMPANY_INFO invalid export — TD-040 |
| d2b1542 | 08-23 | docs: Phase 47 — cheap Tally change-detection, investigated, not implemented |
| 6ccc582 | 08-23 | docs: Phase 46 — Adaptive Sync hardening record |
| ce07906 | 08-23 | fix(android): Ledgers Sync Now refreshes Room — TD-039 |
| 4cb622b | 08-23 | docs: Phase 45 — Adaptive Sync implementation record |
| 8e04c15 | 08-23 | feat(android): surface adaptive-sync frequency on Dashboard |
| 9473573 | 08-22 | feat(desktop): surface adaptive-sync frequency on Dashboard |
| fb0f0f2 | 08-22 | feat(connector): Adaptive Tally Synchronization scheduler engine |
| 75316bf | 08-22 | fix(connector,android): company-scope sync progress — TD-036, TD-037 |
| 6145e23 | 08-22 | fix(android): real-device TD-035 validation + Retry dead-end — Phase 44 |
| 380ce85 | 08-22 | docs: Phase 43 — Adaptive Sync architecture research |
| 4b0b8ff | 08-22 | docs: Phase 42 — Desktop UX final review, MVP-1.4 gate |
| 75aaa12 | 08-22 | fix(desktop): Dashboard staleness + Sync card wording — Phase 42 |

**Distinction the audit asked for:**
- **Committed AND locally verified working:** everything above — every one of these has accompanying test evidence, and several (TD-035/039/040/041/042, Phase 42) have real-device or real-Tally verification recorded in the ledger.
- **Pushed:** none of the 24. `origin/main` is 24 commits behind.
- **Documented but not implemented:** MVP-1.4 (Catalogue) — extensively documented (architecture doc, PDL-020) but **zero code exists** (verified below).

---

## COMPLETED AND STABLE

**MVP-1.1 (Connect)** — implemented and functioning on real data, verified this session:
- Customer population: working (873 real customers synced).
- Supplier population: working (53 real suppliers), now with its own **Creditors tab** (added this session, `2046bbf`) — three tabs: Customers/Prospects/Creditors, `ConnectTab.toClassification()` maps 1:1 to `PartyClassification`.
- Party classification: `LedgerPartyEligibilityPolicy` classifies by Tally parent group; verified live (873 customer / 53 supplier split observed directly in Room).
- Company isolation: every Party/Ledger query carries `companyId` (spot-checked DAOs and Connector sync-state maps — see Security section).
- Party Detail, View Ledger, View Vouchers, Call/WhatsApp: all implemented (`PartyDetailScreen.kt`/`ViewModel`/`UiState` exist and are wired via `ConnectEffect.OpenPartyDetail`, `ViewLedgerTapped`, `ViewVouchersTapped`, `CallTapped`, `WhatsAppTapped`).
- Freshness display: `dataFreshnessAt` sourced from max Ledger `syncedAt`, rendered on Connect.
- **Alias behavior: now real and working (TD-042, fixed this session)** — 511/949 real ledgers carry a real alias; 486/873 real customers have a validated phone auto-seeded; Connect visually confirmed showing real numbers with working Call/WhatsApp.
- Search: name/phone search implemented (`SearchPartiesUseCase`); 1-5 digit Alias search-shortcut exists in code but its live-data validation status versus the now-real Alias data was **not independently re-verified this session** — mark **NOT VERIFIED**.

**MVP-1.2 (Relationship Timeline, Issue History, Dincharya, OI)** — per `BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`: **COMPLETE / FROZEN (2026-08-18)**, Parts A–E, PDL-014–018. `PartyTimelineDao`, `DincharyaScreen/ViewModel/UiState` confirmed present in source. Not re-exercised live this session (out of this session's scope; no code has touched this feature area since the freeze) — status as documented, **not independently re-verified**.

**MVP-1.3 (Business Profile)** — per status doc: **COMPLETE / FROZEN (2026-08-18)**, Parts A–C, PDL-019. `MIGRATION_9_10` confirmed as the most recent Room migration (i.e., nothing has touched the schema since Business Profile landed). Not re-exercised live this session — status as documented, **not independently re-verified**.

**Adaptive Tally Synchronization** — implemented and code-verified this session:
- `ACTIVE_WINDOW_DURATION_MS = 15 min`, `ACTIVE_WINDOW_CHECK_INTERVAL_MS = 5 min`, backoff ladder `backoff_15/30/60` all present exactly as documented in `adaptive-scheduler-domain.ts`.
- Fail-closed default confirmed via code comment: "A missing or corrupt row must fail closed toward MORE freshness, not less."
- Company isolation: scheduler state keyed per-company (consistent with the TD-036/037 fix pattern verified below).
- Desktop and Android both surface the checking frequency (commits `9473573`, `8e04c15`).
- **Cheap change-detection signal investigation: confirmed still closed/not implemented** — commit `d2b1542` ("investigated, not implemented") is the most recent commit touching this, no subsequent commit reopens or reverses it. Not reopened this audit, per instruction, since nothing in current code contradicts that conclusion.

**Desktop** — cold launch, connection lifecycle, health polling, company selection, sync status all functioning; directly observed live twice this session (once for TD-041 work, once for TD-042 work): clean `starting → connected` transition, real company discovery, real Ledgers sync (949/949) completing successfully both times.

---

## OPEN PRODUCT DEFECTS

| Defect | Status | Evidence |
|---|---|---|
| Desktop header badge overflow (Phase 42 §D) | **Open, cosmetic, deliberately unfixed** | 3-column header badge grid overflows the window at default 1200×800 size when no company is selected. Three CSS fix attempts made, none verifiably worked, all honestly reverted (`git diff` on `main.css` is empty). Classified in-doc as affecting "only a secondary, redundant header badge, not the primary Dashboard cards" — MVP-1.4 gate was still marked **A — READY** with this as the sole caveat. |
| Connect 1-5 digit Alias search shortcut vs. now-real data | **NOT VERIFIED** | The shortcut logic (`AliasSearchClassifier`) exists and was tested against synthetic fixtures (Phase 48). Now that real Alias data flows through (TD-042), this path has real short-numeric aliases available (e.g. `"616"`, `"42"`) but was not exercised live this session — worth a quick live check before relying on it. |
| Alias→phone coverage below the ~80% user estimate | **Expected behavior, not a defect** | 486/873 (56%) of real customers got a phone seeded. Verified the gap is explained by (a) genuine non-phone shortcut aliases and (b) malformed near-phone entries (e.g. an 11-digit value) that the existing strict `PhoneNumberNormalizer.normalizeIndianMobile` correctly declines by design. Not a bug; a real data-quality/design-tradeoff question for the Product Owner if the user wants it narrowed. |
| Android instrumented (`connectedProdDebugAndroidTest`) failure count | **NOT VERIFIED this session** | Last documented figure: 358 tests, 345–346 passed, 12–13 failed, across 9 unrelated screens (Dashboard, Diagnostics, LedgerStatement, SecurePairing, ServerConfig, Settings, Sync, VoucherDetails). **Deliberately not re-run this audit**: running it uninstalls/reinstalls the app and wipes the real device's currently-synced ESTIMATION data (949 ledgers, 511 aliases, 486 seeded phones) — exactly the kind of destructive, real-data-risking action this audit was told to avoid. Root-cause classification of those 12 (product defect vs. test defect vs. environment) was likewise not re-verified. |

---

## OPEN TECHNICAL DEBT

Verified against current code, not assumed from old docs:

| TD | Status (registry) | Verified against code? | Blocking? | Recommended priority |
|---|---|---|---|---|
| TD-001 | CLOSED (2026-08-16) | Yes — `sanitizeXml10IllegalCharacters()` confirmed unconditional for every collection, cited as the mechanism TD-035's later fix depends on | No | — |
| TD-009 | **Open** — "Authenticated LAN access... core gap confirmed still accurate" | **Yes, confirmed live**: `requireDeviceAuthForLan: false` is the compiled default (`config/defaults.ts`), and the **real running Desktop config** (`desktop-config.json`) does not override it — this actual installation, in `trusted-lan` mode, does not require device auth on business routes. `secureMobilePairingEnabled: true` is a separate, opt-in bootstrap flag, not equivalent. | Not release-blocking for a single-trusted-LAN pilot, but a real pre-public-release security gap | P2 — should be resolved before any multi-network/public release |
| TD-021 | Proposed, not implemented | Not re-verified this session | No | Unchanged |
| TD-035 | **RESOLVED (2026-08-19)** | Yes — `PARENT` present in `LEDGER_RICH_FETCH_FIELDS`, 18 adversarial tests exist | No | — |
| TD-036 | **FIXED (2026-08-22)** | **Yes, code-confirmed this session**: `progressByCompany`, `activeAbortByCompany`, `activeRunByCompany`, `syncInFlightByCompany` are all `Map<string, T>` in both `ledger-sync.service.ts` and `stock-item-sync.service.ts` | No | — |
| TD-037 | **FIXED (2026-08-22)** | Not independently re-read this session; commit exists and is part of the same TD-036 commit (`75316bf`) | No | — |
| TD-039 | **FIXED (2026-08-23)** | Yes — `StartTargetSyncUseCase.completeLedgerRoomRefresh()` present, live-verified (part of this session's own earlier TD-041 work) | No | — |
| TD-040 | **FIXED (2026-08-23)** | Not re-read this session; part of committed history | No | — |
| TD-041 | **FIXED (2026-08-23) — see dedicated section below** | Yes, exhaustively | No | — |
| TD-042 | **FIXED (2026-08-23)** — new this session's earlier work | Yes, exhaustively (511/949 ledgers, 486/873 customers phone-seeded, live-verified) | No | — |

No TD item required to be reported as still-open turned out to be secretly fixed, and no TD item marked fixed in the registry was found to be un-fixed in code — the registry's FIXED/RESOLVED claims for the items spot-checked (TD-035, TD-036, TD-001) all hold up against direct code inspection.

---

## TD-041 — DEDICATED DEEP-DIVE

1. **Is TD-041 still present?** No — fixed and live-verified in this session's own earlier work (commit `74fa205`), immediately prior to this audit.
2. **Was it fixed accidentally?** No — deliberately root-caused via live reproduction, then fixed.
3. **Can the root cause be narrowed from source inspection?** Yes, definitively — this was done, not merely theorized.
4. **Exact data flow:** `SyncViewModel` (presentation) → `StartTargetSyncUseCase` (domain, `feature/sync/domain/usecase/SyncUseCases.kt`) → `ReconcilePartiesFromLedgersUseCase` → `PartyRepositoryImpl` → Room (`cached_parties`).
5. **Which layer:** **Coroutine-scope lifecycle**, not Connector cache, not Room, not Flow/StateFlow, not company binding, not Compose state. Specifically: `SyncViewModel.maybeReconcilePartiesFromLedgers` launched Party reconciliation as a **detached `viewModelScope.launch`**, started only *after* the use-case already reported "Completed." `viewModelScope` is cancelled when the Sync screen's `NavBackStackEntry` is popped — a fast Back-navigation within the ~4s a real reconciliation of 900+ records takes silently cancelled it, with zero error surfaced. Ledger's own Room refresh never showed this because it was always `await`ed inside the same use-case call, never in a detached job.
6. **Is there a test reproducing it?** Yes — 4 new tests directly on `StartTargetSyncUseCase` in `SyncUseCasesTest.kt` (12 tests total in that file now) proving reconciliation is awaited in the same suspend chain; 6 pre-existing tests in `SyncViewModelTest.kt` (12 total) continue to pass, now exercising the fixed path end-to-end.
7. **Smallest permanent architectural fix:** Move reconciliation into `StartTargetSyncUseCase.completeLedgerRoomRefresh`, awaited in the same suspend chain as the Ledger Room refresh — this is exactly what was done. No separate job exists anymore to be cancelled.
8. **Was it safe to fix?** Yes, and it was already fixed and merged (locally) this session.
9. **What validation was done:** Full JVM suite (1,317/1,317, up from 1,313 baseline by the 4 new tests), plus **live-device validation**: rebuilt, installed, reproduced the identical prior-failing tap-then-Back sequence, and confirmed via a direct `sqlite3` query against the live `cached_parties` table that all 926 rows reconciled to completion despite the navigation.

**One honest caveat carried in the registry itself:** this fixes the *proven* detached-reconciliation mechanism. Whether it's provably identical to Phase 53's single earlier captured `totalItems=0` trace is not claimed — upsert-only semantics explain staleness directly, a hard zero on an already-populated table needs an additional condition. The Phase 53 mitigation (atomic `@Transaction` Party reads, one-shot stale-empty retry) was deliberately left in place as defense-in-depth, not removed.

---

## TEST/BUILD HEALTH

All runs fresh this session, against current HEAD (`cd20b9b`):

| Component | Tests | Lint/Typecheck | Build |
|---|---|---|---|
| **Connector** (`budcom_connector`) | **1,111/1,111 passing** (122 files) | `eslint src test && tsc --noEmit` — **clean** | `tsc -p tsconfig.build.json` — **clean** |
| **Desktop** (`budcom_desktop`) | **735/735 passing** (68 files) | `tsc --noEmit` on main/preload/renderer configs — **all clean** | Not run as a full `npm run build` this session (would rewrite `dist/` under the currently-running live Electron process for no informational gain beyond what typecheck already proves) — build health inferred from clean typecheck + the fact the app is live-running successfully from its existing build |
| **Android** (`budcom_android`) | **1,317/1,317 passing** (`testProdDebugUnitTest`) | `lintProdDebug` — **0 issues, 0 warnings, 0 errors** | Not re-run as a fresh `assembleProdDebug` this audit (already built and installed twice earlier this session for TD-041/TD-042 verification; re-running adds no new information) |
| **Android instrumented** (`connectedProdDebugAndroidTest`) | **NOT VERIFIED this session** — deliberately not run (see Open Product Defects: would wipe real device data) | — | — |

---

## REAL-DEVICE STATE

All checked fresh, read-only, this session:

- **Phone paired:** Yes — device `10BF44124K000E3` (`I2407i`) connected via `adb devices -l`.
- **App running:** `com.budcom.android.debug` process alive (PID 19003 at time of check).
- **Laptop ↔ phone communication:** Yes — Connector reachable, `192.168.29.34:8080` listening (PID 16792 — note this differs from the PID observed a session-segment earlier, indicating the Connector process cycled at some point between the earlier TD-042 work and this audit; not investigated further as it's outside audit scope and the app is functioning).
- **Tally reachable:** Yes — `tally.exe` (PID 5140) listening on port 9000.
- **Real company selected:** ESTIMATION (confirmed via Dashboard UI text earlier this session; not re-confirmed via a fresh UI screenshot this audit to avoid unnecessary device interaction — SQL data below confirms the same company context).
- **Real data available and current counts (fresh `sqlite3` query this audit):**
  - `cached_ledgers`: 949 total, **511 with a real alias**
  - `cached_parties`: 873 customers (486 with a validated phone), 53 suppliers (2 with a validated phone)
- Data is **stable and unchanged** from the values recorded immediately after TD-042's live verification earlier this session — no drift, no corruption, no unexpected reset.

No Tally data was altered. No experimental XML/TDL was sent to Tally during this audit (the direct-to-Tally requests referenced in TD-042 above were sent in the *prior* work session, not this audit, and only after being modeled exactly on the Connector's own already-shipped, already-tested request shape).

---

## SECURITY/DATA-INTEGRITY RISKS

- **Company isolation (sync state):** TD-036/037 fix verified in code this audit — all sync-progress/abort/run/in-flight state is `Map<companyId, T>`, not a shared singleton, in both `ledger-sync.service.ts` and `stock-item-sync.service.ts`.
- **Company isolation (Room queries):** every Party/Ledger DAO query inspected this session (both today's TD-041/042 work and this audit) carries an explicit `companyId` parameter; no cross-company query pattern was found.
- **XML sanitization boundary:** confirmed unconditional (`sanitizeXml10IllegalCharacters()`, TD-001/TD-035 lineage) — applies to every collection, not selectively.
- **LAN exposure / device authentication — real, open, currently-active gap:** the real running Desktop installation (`desktop-config.json`) uses `connectorBindMode: "trusted-lan"` **without** `requireDeviceAuthForLan: true` and **without** `secureLanRouteProtectionEnabled`. This is the compiled default (`false`), confirmed in `config/defaults.ts`, and is exactly TD-009's still-open, still-accurate finding. Practical effect: **any device on the same LAN as `192.168.29.34:8080` can reach BUDCOM's business routes without device-level authentication**, relying solely on network trust. `secureMobilePairingEnabled: true` is a separate, independent, opt-in bootstrap-surface flag and does not close this gap.
- **Direct Tally writes:** none found or performed; the architecture and this session's own work are read/export-only against Tally.
- **Race conditions / sync duplication:** TD-036/037's fix directly addresses the one confirmed instance found (process-wide singleton sync state); no other race condition was newly found this audit.
- **Data mutation risk from this session's own work:** none — TD-041 and TD-042 fixes are both read-path/reconciliation-timing fixes; no Tally-write code was touched.

---

## MVP-1.4 STATE

- **Code implementation: zero.** No file matching `*catalogue*`/`*Catalog*` exists anywhere in `apps/budcom_android/app/src/main` except one incidental doc-comment mention in `BusinessProfileModels.kt` ("Catalogue → future Vartalap") — not implementation.
- **Schema:** most recent Room migration is `MIGRATION_9_10` (Business Profile, MVP-1.3). Nothing beyond it exists — confirms no Catalogue schema work has begun.
- **PDL-020 exists**, titled "MVP-1.4 Catalogue: SKU identity, Stock Item relationship, lifecycle, Excel, assets, visitor scope, Desktop, sharing, company isolation" — the nine open product decisions from the architecture doc's §5.3 are recorded as locked.
- **Readiness gate, per Phase 42's own explicit assessment:** **A — READY**, with the header-overflow cosmetic defect as the sole caveat (judged non-blocking, affects a secondary badge only). This assessment predates TD-041/TD-042's discovery and fix; since both are now closed and neither touched Catalogue-relevant code paths, nothing found this audit contradicts that A rating — but it also has not been explicitly re-confirmed by anyone since Phase 42.
- **Explicit standing instruction found in the docs themselves:** "Do NOT begin MVP-1.4 implementation from this document alone — a separate, explicit go-ahead is still required."

---

## READINESS RATINGS

| Area | Rating | Why |
|---|---|---|
| Product readiness | **B** | MVP-1.1/1.2/1.3 complete and frozen; two real, user-facing defects (TD-041, TD-042) found and fixed just this session — a healthy sign of active quality work, but also evidence the product hadn't been exercised against real data as thoroughly as believed until very recently. |
| Technical stability | **B+** | All three components' automated suites are 100% green (3,163 tests total: 1,317+1,111+735), lint/typecheck clean everywhere. Held back from A by TD-041/042 having existed undetected for weeks, and by the unresolved instrumented-test question. |
| Data-integrity confidence | **B** | Company isolation verified in code for the specific mechanisms checked; TD-009's LAN-auth gap is real and open, though scoped to a single-trusted-network pilot context. |
| Real-device confidence | **A-** | Extensively, freshly live-verified this session and the one before it — real Tally, real Connector, real 949-ledger dataset, two genuine defects found and fixed with device-level proof, not just unit tests. |
| Desktop readiness | **B+** | Clean cold launch, connection lifecycle, and health handling, verified live twice this session. One open, honestly-documented cosmetic defect (header overflow). |
| Android readiness | **B+** | 1,317/1,317 JVM tests, 0 lint issues, three real defects found and fixed this session with live verification. Instrumented-test health genuinely unknown (not re-verified, by design, to avoid data loss). Version number (`0.1.1-continuity.28`) is now stale relative to actual fixed content — see Documentation Consistency. |
| Connect readiness | **B+** | Core flows all implemented and working on real data; Alias-driven phone seeding now genuinely functional end-to-end (a real, previously-invisible gap just closed); Creditors tab added this session. Alias search-shortcut against real data unverified. |
| MVP-1.4 readiness | **A (per last formal gate), not re-confirmed** | Zero implementation exists; the formal gate says ready; nothing found this audit contradicts that; explicit go-ahead still required per standing project rule. |

---

## CANDIDATE NEXT TASKS

| Priority | Task | Why it matters | Risk if postponed | Effort | Blocks roadmap? |
|---|---|---|---|---|---|
| Should fix soon | Push the 24 local commits to `origin/main` | All of today's real fixes (TD-039–042, Adaptive Sync, Connect Alias/Creditors) exist only locally | Any machine loss, disk issue, or accidental `git reset --hard` loses a full day of verified, real-defect fixes with no remote backup | Trivial | No, but high-value safety item |
| Should fix soon | Bump Android `versionCode`/`versionName` and Connector `package.json` version to reflect TD-041/TD-042/Creditors-tab content | Installed app self-reports as the pre-fix MVP-1.3 freeze build; future sessions checking "what's on this device" via version alone will be misled | Low immediate risk, but compounds confusion over time | Trivial | No |
| Should fix soon | Live-verify the 1-5 digit Alias search shortcut against the now-real Alias data | TD-042 just made real short-numeric aliases exist for the first time; this path was only ever tested against fixtures | Silent breakage would go unnoticed since nothing currently exercises it against real data | Small (a few live searches) | No |
| Should fix soon | Update `BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` §7 "Exact NEXT TASK" | Still describes MVP-1.4 planning as the frontier, with no mention of TD-039 through TD-042 or the Creditors tab — the canonical "what's next" pointer is stale | Wastes a future session's time reconstructing what actually happened | Small (documentation only) | No |
| Valuable but optional | Re-run the instrumented test suite on a disposable/secondary device (not this real-data device) | Genuinely unknown current instrumented-test health; last figure (12/13 failures) predates today's fixes | Unknown regressions could exist un-caught | Medium | No |
| Valuable but optional | Decide whether to relax phone-alias validation or support multi-value alias (phone + shortcut) | Directly affects the ~56% vs. ~80% coverage question the user raised | None urgent — current behavior is safe, just conservative | Medium (real design decision needed first) | No |
| Future | Resolve TD-009 (require device auth for LAN) before any public/multi-network release | Real, currently-active security gap on this exact installation | Not urgent for a single-trusted-LAN pilot; becomes real risk the moment the network trust boundary widens | Medium | Pre-public-release gate, already tracked as such |
| Future | MVP-1.4 Catalogue implementation | Next roadmap milestone per the execution plan | N/A — explicitly not authorized without a separate go-ahead | Large | This IS the roadmap decision point |
| Do not touch now | Desktop header-overflow CSS fix | Already attempted three times and honestly reverted as unverifiable without DevTools access; re-attempting blind is likely to repeat the same outcome | Cosmetic only | — | No |

---

## ITEMS THAT SHOULD NOT BE TOUCHED NOW

- **Desktop header-overflow CSS** — already tried three ways, honestly reverted; needs actual DevTools/computed-style access to make further attempts non-speculative.
- **Android instrumented test suite** — running it wipes this device's real, currently-valuable synced state (949 ledgers, 511 real aliases, 486 real phone numbers). Any future attempt should use a disposable/secondary device.
- **Tally itself** — no experimental TDL/XML should ever be hand-sent to this live production instance without prior validation against Tally's own documentation or a disposable instance (per the project's own standing rule, reaffirmed by this session's own TD-042 investigation methodology).
- **MVP-1.4 implementation** — explicitly gated behind a separate go-ahead per the project's own documented rule; the A-readiness rating is not itself that go-ahead.
- **Anything requiring the 24 unpushed local commits to be rewritten/rebased** — they're clean and ahead-only; no reason exists to alter them, only to push them.

---

## INFORMATION CHATGPT NEEDS FOR ROADMAP DECISION

Evidence, not a recommendation:

- The MVP-1.4 gate was formally rated **A — READY** on 2026-08-22, before TD-041/TD-042 were even discovered. Nothing found in this audit contradicts that rating, but no one has explicitly re-confirmed it *after* today's fixes landed.
- Today's session (2026-08-23) found and fixed **three real, previously-undetected defects** in already-"complete/frozen" MVP-1.1 territory (TD-039, TD-041, TD-042) purely by testing against real data for the first time in weeks — a strong signal that "complete/frozen" status for a milestone does not guarantee it's been exercised against real data recently, which is relevant to how much confidence to place in MVP-1.2/1.3's own "complete/frozen" status (neither was re-exercised this session).
- **24 real, verified commits exist only locally.** Any roadmap decision should account for getting these pushed before more work stacks on top, purely as a safety/backup matter, independent of what's decided next.
- TD-009 (no required device auth on trusted-LAN) is a real, currently-active gap on the actual running installation — relevant if any near-term plan involves wider network exposure, more devices, or moving toward public release, even if MVP-1.4 itself is chosen next.
- The Connect Alias/phone-seeding coverage gap (56% vs. the user's 80% expectation) surfaces a real, disclosed product-design question (tolerate malformed near-phone values? support both a phone and a shortcut per ledger?) that's independent of MVP-1.4 and could be resolved cheaply if prioritized.
- The Android version number no longer accurately reflects the installed build's actual content — worth knowing before using version strings as a proxy for "what's been verified" in any future session.
- Instrumented-Android-test health is a genuine unknown right now, by deliberate choice (to protect real device data) — if release confidence depends on that number, it needs a disposable-device re-run first.

---

## FINAL GIT STATE

- **Branch:** `main`
- **HEAD:** `cd20b9b19f094048ed65756fdb5586f139bca882`
- **origin/main:** `9ca8c7b4cacf17cd74f8eff314c431c35d74df5d`
- **Ahead/behind:** 24 ahead, 0 behind
- **Working tree:** clean, zero uncommitted changes
- **Nothing was pushed, fixed, implemented, or altered during this audit.**
