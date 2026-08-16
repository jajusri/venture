# BUDCOM Development Ledger

**Status:** Canonical historical/audit record

**Reconstructed:** 2026-08-17 from repository and Git evidence

**Product-code baseline:** `bc9cd55969b3fb2158be30700a75facafdf9471a` (`main`)

**Current-state companion:** `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`

## 1. Purpose and evidence rules

This ledger explains how BUDCOM reached its present state. It is the durable development history;
the companion checkpoint answers only “where are we now?” Planning documents remain plans, the
technical-debt registry remains the detailed TD authority, and specialist evidence remains in its
original files.

Repository filenames, contents, and all-history Git paths were searched before this file was
created. No prior canonical Development Ledger, deleted ledger, or renamed equivalent was found.
`docs/PROJECT_PROGRESS.md`, milestone/stage updates, `docs/REPOSITORY_EVOLUTION.md`, the refinement
register, and the Controlled-Pilot closure document are useful predecessors for particular eras,
but none covers the complete history and none is superseded or deleted by this ledger.

Evidence priority is Git/source/tests, then the current checkpoint and later physical-validation
records, then older status/planning material. **IMPLEMENTED** does not imply physical validation;
**AUTOMATED-VALIDATED**, **PHYSICALLY VALIDATED**, and **HUMAN VISUALLY APPROVED** are asserted only
where recorded evidence exists. Earlier documents are not silently rewritten when later evidence
changes their conclusion.

## 2. Current executive state

| Area | Current evidence classification |
|---|---|
| MVP-1 | **CONTROLLED-PILOT VALIDATED** and Controlled-Pilot **GO**; bounded MVP-1 UI/UX polish implemented. Public release is not yet authorized. |
| Android | **IMPLEMENTED / AUTOMATED-VALIDATED / PHYSICALLY VALIDATED / HUMAN VISUALLY APPROVED**. Installed owner-reviewed UI/UX candidate: `0.1.1-continuity.21`, versionCode 22. |
| Desktop | **IMPLEMENTED / AUTOMATED-VALIDATED / CONTROLLED-PILOT VALIDATED**. Candidate `0.4.16`; the latest UI/UX-polish Desktop candidate still lacks recorded human visual approval. |
| Connector | **IMPLEMENTED / AUTOMATED-VALIDATED / CONTROLLED-PILOT VALIDATED**. Version `0.4.6`; read-only Tally boundary, SQLite snapshots, trust/discovery and private-storage participation are in place. |
| Controlled Pilot | **CLOSED / GO** on 2026-08-16 under the documented stable-router, fixed-Private-storage and signing constraints. Documentation does not reopen it. |
| UI/UX polish | Android pass **HUMAN VISUALLY APPROVED** by owner on 2026-08-17 after installation. Desktop functional polish is automated-clean; Desktop visual approval remains pending. |
| Git at reconstruction start | `main` at `bc9cd55`, 284 commits from the 2026-07-22 root, 185 commits ahead of `origin/main`; three known planning/product paths were untracked and left untouched. |

## 3. Major development timeline

| Phase | Git/document evidence | Outcome |
|---|---|---|
| 1. Architecture foundation (2026-07-22) | Root `e8def45`; Milestone 0; ADRs and module boundaries | **IMPLEMENTED.** Local-first, clean/vertical-slice boundaries, ERP read-port, read-only Connector and fail-closed principles established. |
| 2. Connector/Tally foundation (2026-07-22) | `c71054c`, `49fbc48`, `4bf4125`, `f6f59c4` | **IMPLEMENTED / LIVE-VALIDATED.** Connector lifecycle, bounded XML requests, Tally reachability and extraction foundations. |
| 3. Discovery/Desktop shell (2026-07-22–23) | `8d2c5b0`, `e96083f`, `5a41e89`, `7849977`, `4126f5c`, `697bc79` | **IMPLEMENTED.** Company discovery/selection, groups, Desktop dashboard, lifecycle, configuration and diagnostics. |
| 4. Ledger/Stock synchronization (2026-07-23–25) | `9e24483`, `271f36e`, `f2a6b3a`; 5A/5A-P/5B reports | **IMPLEMENTED / AUTOMATED-VALIDATED / LIVE-VALIDATED.** SQLite snapshots, atomicity, retry, GUID identity, Stock and Ledger APIs. |
| 5. Android accounting experience | Android contracts/source; refinement register | **IMPLEMENTED.** A/c Data, company, Ledgers, Stock, Vouchers/details, search, Sync, Settings and Diagnostics backed by Room. |
| 6. Voucher/data-integrity expansion | Voucher contracts/runbooks; TD-001/020/026/027 | **IMPLEMENTED / PHYSICALLY VALIDATED** for pilot scope. Unified chronological browser, types, optional semantics, reconciliation; carry-forward tombstones **DEFERRED**. |
| 7. Secure pairing/trusted LAN | Secure-pairing lineage; TD-012–021 | **IMPLEMENTED / CONTROLLED-PILOT VALIDATED.** QR pairing, identities, credentials, fingerprint pinning and trusted devices; unfinished limitations remain explicit. |
| 8. Private removable storage (2026-08-11–16) | Closure §§44–46; TD-024/025/033/034 | **IMPLEMENTED / PHYSICALLY VALIDATED** for clean stop/removal/restart; fail-closed with no silent fallback. Migration and live-loss UX gaps deferred. |
| 9. PDF/preview/share | Refinement register; TD-028; closure | **IMPLEMENTED / PHYSICALLY VALIDATED / CLOSED.** Voucher/Ledger PDF, preview, Save and share. Ledger discoverability corrected after premature closure. |
| 10. MVP-1 hardening | TD-001 and TD-012–032; quality/release docs | **AUTOMATED-VALIDATED.** Accounting, reconnect, endpoint, storage, PDF and discovery defects fixed or explicitly deferred. |
| 11. Controlled-Pilot closure (2026-08-15–16) | `6fc6689` onward; closure; scorecard | Initial evidence gaps were later filled by Sessions 2–5. Final result: **CONTROLLED-PILOT VALIDATED / GO** with constraints. |
| 12. MVP-1 UI/UX polish (2026-08-16–17) | `b58d85c`, `eb532f7`, `7db7dd9`, `63b919e`, `bc9cd55` | **IMPLEMENTED / AUTOMATED-VALIDATED.** Android Home/type filters/copy and Desktop state/Refresh/status polished; candidates versioned. |
| 13. Owner Android visual review (2026-08-17) | Owner-supplied review recorded in checkpoint/UI status | Installed `continuity.21` Android candidate **HUMAN VISUALLY APPROVED**. Desktop visual approval is not implied. |

## 4. Android development

Android evolved from a companion shell into the local-first accounting-data client. Current source
contains company selection, A/c Data Home, Ledgers/statements, Stock Items, unified Vouchers and
details, universal search, Sync, PDF preview/save/share, Settings and Diagnostics. Room/local
persistence is authoritative for normal display; authenticated remote sync promotes complete
snapshots atomically. Offline/freshness and reconnect states are surfaced without exposing most
transport internals.

QR pairing stores Connector identity, device identity/credentials and the pinned transport
fingerprint. Identity-based mDNS/Android NSD rediscovery replaced fixed-IP dependence. TD-029–032
corrected Desktop rebind, invalid IPv6 advertisement, missing `securePort`, and Android multicast
reception. A OnePlus Nord 5 hotspot was proven unsuitable for the mDNS leg because it did not
forward multicast between clients; Jio Fiber/router-LAN operation and the individual mechanisms
supplied accepted pilot evidence.

The polish reconciled A/c Data Home with the approved Stitch master at
`D:\BUDCOM-Design-Archive\01_APPROVED_MASTERS\AC_DATA_HOME\BUDCOM-AC-DATA-HOME-MASTER.png`, added
the Voucher type-filter strip, normalized loading/error/status copy, improved Sync/Settings/
Diagnostics, and polished Voucher Details/PDF preview. Insights and Connect/Vartalap tabs were not
fabricated because their data/destinations are future scope. The owner physically inspected the
installed `continuity.21` build on 2026-08-17 and visually approved the Android UI/UX pass.

## 5. Desktop development

The Electron Desktop application remains a lightweight operator surface around the Connector. It
manages Connector lifecycle, company discovery/selection, refresh/sync, freshness/diagnostics,
network binding, trusted-LAN status, storage selection and the Windows installer lifecycle. Its
renderer is not an accounting system of record.

Hardening fixed transient re-poll, stale endpoint clients and departed-IP binding. UI polish made
connecting visible, repaired Dashboard Refresh so it renders fetched state, humanized raw status,
and removed dead DOM calls. These are **AUTOMATED-VALIDATED** at Desktop `0.4.16`; no evidence yet
records human visual approval. Tray/background behavior, triple-restated connection state and
product naming remain deliberate P2/deferred decisions.

## 6. Connector and Tally/XML integration

The Node/TypeScript Connector is the sole runtime boundary to Tally. Communication is read-only
and capability-restricted to exports; it builds bounded XML requests, parses company, group,
ledger, stock and voucher data, and exposes versioned local APIs. It owns lifecycle, selected
company/session state, SQLite repositories, snapshots, authentication, diagnostics and mDNS.
Android never talks directly to Tally.

Extraction evolved to GUID-first identity, quality validation, response limits, cancellation,
concurrency control and diagnostics. TD-001 corrected the invalid assumption that
`IsDeemedPositive` and signed Amount must agree. Live reconciliation later proved 98/98
GUID-matched vouchers. Tally may temporarily withhold a voucher from Export while its UI shows it
(TD-027); that is an upstream timing observation, not a BUDCOM defect.

## 7. Sync and data integrity

- **IMPLEMENTED:** company-isolated Connector SQLite snapshots and Android Room state; atomic
  promotion only after a complete authoritative window.
- **AUTOMATED-VALIDATED:** idempotency, duplicate prevention, bounded history, retry, concurrency
  safety, in-window add/edit/delete, stable GUID identity and migration protection.
- **PHYSICALLY VALIDATED:** real-Tally Sync All, repeat refresh, browsing and 98/98 GUID
  reconciliation; mutation scenarios are recorded in closure evidence.
- **LOCKED:** Optional/Estimate vouchers are non-accounting. “Last 7 Sales” counts regular Sales
  and includes all accounting movements in the resulting interval.
- **DEFERRED:** carried-forward out-of-window vouchers lack later edit/delete tombstones (TD-026).
  TD-022/023 are non-blocking snapshot/query performance observations.

## 8. Connectivity, discovery, pairing and trust

| Capability | Evidence status |
|---|---|
| Endpoint/lifecycle | **IMPLEMENTED / PHYSICALLY VALIDATED** for restart and real network rebinding. |
| mDNS/DNS-SD + Android NSD | **IMPLEMENTED.** Identity, IPv4 endpoint and `securePort` corrected by TD-030/031. |
| Android multicast | **FIXED / mechanism PHYSICALLY VALIDATED** by app-owned lock and OS group/lock evidence (TD-032). |
| QR pairing/trust | **IMPLEMENTED / CONTROLLED-PILOT VALIDATED.** Identities, credentials, fingerprint verification and trust persistence. |
| Reconnect | **PHYSICALLY VALIDATED** for restarts/router LAN; literal unattended two-router TD-017 transition remains a non-blocking residual. |
| Phone hotspot | **ACCEPTED ENVIRONMENTAL LIMITATION.** Nord 5 multicast isolation prevents reliable mDNS, not unicast TCP. |
| Legacy/renewal | TD-009 and TD-021 remain open/proposed; security is not claimed complete beyond pilot scope. |

## 9. Private USB storage

Private mode places canonical SQLite in a marker-identified removable vault. Desktop resolves the
vault; Connector independently verifies marker/vault identity and fails closed if absent/wrong.
There is no silent internal fallback.

The physical session stopped BUDCOM, removed USB, observed the unavailable screen, reinserted it,
and proved byte-identical database continuity by SHA-256. This validates the pilot clean-stop
workflow, not live mid-write removal. TD-025 records generic Disconnected UX after live loss; the
pilot rule is “do not remove USB while running.” TD-033 records missing Standard→Private migration/
warning (including orphaned trust); TD-034 records unreliable adoption of an externally/stale-
encoded marker. Both are deferred for a fresh, fixed-Private pilot. Historical internal-drive
remnants and orphaned vaults are non-authoritative and were not destructively cleaned.

## 10. PDF and share

Voucher/Ledger PDF generation, in-app preview, Save and Android share are implemented. TD-028 was
first closed when Voucher preview worked, then correctly reopened because Ledger preview was only
behind an undiscoverable long press. A one-tap entry was added; Preview, content review, Save and
WhatsApp Share were physically confirmed and TD-028 is **CLOSED**. Later installed Android polish
was owner-reviewed without changing accounting truth.

## 11. MVP-1 hardening and Controlled Pilot

Hardening covered accounting semantics, identity/migrations, interrupted/concurrent sync,
trusted-LAN pairing/reconnect, endpoint discovery, transport identity, private storage, PDF,
packaging and diagnostics. The initial 2026-08-15 assessment correctly found tests alone did not
prove device behavior. Later 2026-08-16 Sessions 2–5 added direct router/network, restart, USB/hash,
PDF/share and Sync All evidence. Closure and Quality Scorecard therefore supersede the provisional
verdict with **GO for Controlled Pilot**.

Constraints: stable real-router LAN; no USB removal while running; no Standard→Private switch;
expected unsigned Windows/Android-debug signing; no claim that the literal two-router transition
or live mid-write pull was witnessed.

## 12. MVP-1 UI/UX polish

| Work unit | Commit(s) | Status |
|---|---|---|
| A/c Data Home + Voucher filters (UIP-002/003) | `b58d85c` | **IMPLEMENTED / AUTOMATED-VALIDATED / HUMAN VISUALLY APPROVED** on installed continuity.21 |
| Company/Sync/Settings/Diagnostics copy/state | `eb532f7` | **IMPLEMENTED / AUTOMATED-VALIDATED / HUMAN VISUALLY APPROVED** in owner Android review |
| Voucher Details/PDF preview | `3eabf46`, `feea4ae` | **IMPLEMENTED / PHYSICALLY VALIDATED / CLOSED** |
| Desktop indicator, Refresh, raw status | `7db7dd9` | **IMPLEMENTED / AUTOMATED-VALIDATED**; visual approval pending |
| Candidate versioning | `bc9cd55` | Desktop 0.4.16; Android continuity.21/22; Connector 0.4.6 |

The Stitch path above is authoritative where the UI/UX status records it. Unsupported Insights,
Connect/Vartalap and drawer functionality remain out of scope. Detailed P2/DEFER findings remain
in the specialist UI/UX status.

## 13. Technical-debt reconciliation

Canonical detail remains `docs/technical-debt/registry.md`. This includes every registered item
through TD-034; “physical” is never inferred from tests.

| TD | Short title / subsystem | Root cause and disposition | Validation / state |
|---|---|---|---|
| 001 | Voucher parser / Tally | Invalid sign/`IsDeemedPositive` equality; tolerance fix | Automated + physical; **CLOSED P0** |
| 002 | Company UI | Missing Desktop selection UI; implemented | **RESOLVED P2** |
| 003 | Supervision | Missing lifecycle manager; implemented | **RESOLVED P2** |
| 004 | Tally spawn config | Host/port not forwarded | **OPEN P3**, non-blocking |
| 005 | JSON repository | Replaced by SQLite | **RESOLVED P2** |
| 006 | Interrupted sync | Durable retry lineage added | **RESOLVED for pilot P2** |
| 007 | Cancellation | Bounded extraction cancellation | **RESOLVED WITH LIMITATION P3** |
| 008 | Network binding | Insecure default narrowed | **RESOLVED P2** |
| 009 | Auth LAN | Legacy access incomplete | **OPEN P2**; paired core flows supersede it |
| 010 | Diagnostic privacy | Allowlist/sanitizers | Automated; **RESOLVED P2** |
| 011 | Ledger identity | Name slug/shallow export → GUID-first | Live evidence; **RESOLVED P1** |
| 012 | Manual-IP pairing | Discovery gap | Automated; physical once 2026-08-08; **FIXED P0**, current-candidate retest non-blocking |
| 013 | Company lost on reconnect | Session recovery | Restart physical pass; **FIXED P0** |
| 014 | Desktop recovery | No transient re-poll | Deterministic + physical; **FIXED P1** |
| 015 | Stale endpoint clients | Old route retained | Rebind physical pass; **FIXED P0** |
| 016 | Diagnostics endpoint | Legacy state shown | Physical restart evidence; **FIXED P0** |
| 017 | Pinned old endpoint | Trust coupled to address | Identity rediscovery; combined evidence; **FIXED P0**, two-router residual |
| 018 | Mutable packaged identity | Identity under install resources | Durable path + physical restart; **FIXED P0** |
| 019 | Legacy endpoint/trust replace | Unsafe release route/UI | Safe replacement + physical reconnect; **FIXED P0** |
| 020 | Voucher sync discarded | Android action did not execute | 98/98 Sync All; **CLOSED P0** |
| 021 | Session renewal scope | Renewal at one call site | **PROPOSED / NOT IMPLEMENTED P1**, non-blocking pilot |
| 022 | Android snapshot memory | Full window held before Room commit | **OPEN/DEFERRED P3** |
| 023 | Connector query memory | Full snapshot loaded per page | **OPEN/DEFERRED P3** |
| 024 | Drive validation | IPC failed to revalidate removability | Automated/live-consistent; **FIXED P1** |
| 025 | Live USB-loss UX | Generic Disconnected after watchdog | **OPEN/ACCEPTED PILOT LIMITATION P2** |
| 026 | Carry-forward tombstone | No recheck outside window | **OPEN/DEFERRED P2** |
| 027 | Tally export timing | Export may lag Tally UI | **OPEN OBSERVATION P2**, not BUDCOM defect |
| 028 | PDF discoverability | Ledger Preview hidden after premature closure | One-tap + physical Preview/Save/Share; **CLOSED P3** |
| 029 | Departed Desktop IP | Server kept old bind | Real-network pass; **FIXED P0** (`5deaf60`) |
| 030 | Invalid IPv6 advert | Advertised unbound address | IPv4-only live browse; **FIXED P0** (`fa3a4ea`) |
| 031 | Wrong TLS port | Pin verified against plain port | `securePort`; combined evidence; **FIXED P0** (`7f40a92`, `b19499e`) |
| 032 | No Android multicast | Missing scoped Wi-Fi lock | OS-level proof; **FIXED P0** (`22381e1`); hotspot external |
| 033 | Standard→Private flow | No migration/warning, trust orphaned | **OPEN/DEFERRED P2**; public data-safety UX issue |
| 034 | Stale marker adoption | Unreadable marker falls through to new vault | **OPEN/DEFERRED P3**; reproduce app-owned path |

## 14. Candidate and version evolution

| Era | Meaningful evidence |
|---|---|
| Foundation | Tags `v0.1.0-m0` through `v0.4.3-milestone-4d`. |
| Historical packaged pilot | `release/desktop-0.4.3-connector-0.4.0`; later hardening initially lacked equivalent artifacts. |
| Hardening | Desktop 0.4.7–0.4.15; Connector to 0.4.6; Android through continuity.19 for TD-029–032. |
| Approved Controlled-Pilot artifacts | Closure records Desktop 0.4.15/Connector 0.4.6 and Android continuity.19/20 with hashes. |
| Current product baseline | Desktop 0.4.16, Connector 0.4.6, Android 0.1.1-continuity.21/22 at `bc9cd55`. |
| Installed/reviewed | Android continuity.21 owner-reviewed 2026-08-17; no Desktop 0.4.16 visual approval record. |

## 15. Automated test and quality evidence

The 2026-08-15 closure run recorded Connector 157 files/1,358 tests, ESLint/TypeScript clean;
Desktop 66 files/674 tests and three TypeScript configs clean; Android 1,002 JVM tests plus lint,
debug assemble and AndroidTest packaging clean. Later hardening/polish records Android 1,021 debug
and 1,021 release JVM tests, both lints/assembles clean, and Desktop 696/696 with TypeScript clean.
Counts evolved as tests were added; these are dated evidence, not a fresh run for this docs-only
reconstruction.

`connectedAndroidTest` packaging succeeded. Specific device/instrumented evidence exists, but the
latest polish checkpoint does not claim a complete fresh connected run. The Quality Scorecard’s
latest result is Controlled-Pilot GO (automated evidence 5, physical evidence 5 in recorded scope).

## 16. Physical and human validation evidence

| Scenario | Result |
|---|---|
| Real Tally / Sync All | **PASS:** 98/98 GUID reconciliation; Ledger/Voucher browsing/type coverage. |
| Mutations | **PASS in closure scope:** add/edit/delete/repeat refresh; TD-026 remains outside-window limitation. |
| Restarts | **PASS:** Desktop/Connector and Android restart; company/identity/trust continuity. |
| Connectivity | **PASS/combined:** router/Jio Fiber and TD-029–032 links; literal two-router run not directly witnessed. |
| Nord 5 hotspot | **ENVIRONMENTAL LIMITATION:** mDNS multicast not forwarded; unicast TCP worked. |
| Private USB | **PASS clean-stop:** unavailable UI, reinsert, byte-identical hash; live/mid-write removal not tested. |
| PDF | **PASS:** Preview, content review, Save and WhatsApp Share. |
| Android UI/UX | **HUMAN VISUALLY APPROVED:** owner inspected installed continuity.21 on 2026-08-17. |
| Desktop UI/UX | **PENDING:** automated-clean, no owner visual approval record. |

## 17. Durable architectural decisions

- BUDCOM is local-first. Connector SQLite and Android Room hold durable, company-isolated state;
  Android displays local authoritative data where appropriate and sync remains separate.
- Connector alone owns read-only Tally communication. Future modules do not directly write
  accounting data unless explicitly approved; user-initiated compatible XML remains the boundary.
- Stable identity, atomic snapshot promotion, idempotency, bounded queries and honest recovery are
  required. Partial fetches cannot silently replace authoritative snapshots.
- Discovery is Connector-identity based, not fixed-IP based. QR pairing, device/Connector identity,
  credentials, TLS fingerprint and revocation foundations define the secure direction.
- Private removable storage is a marker-identified fail-closed vault; migration/replacement must be
  explicit before broad use.
- Vouchers form one chronological list with filters; Optional/Estimate is non-accounting. Ledger
  period/default semantics are locked in the Product Decision Log.
- PDF preview/export/share does not alter accounting truth. Desktop remains lightweight; Android
  remains simple; performance complexity must earn business value.
- Git, committed evidence, checkpoint and specialist docs are durable memory. Clean-tree discipline
  applies; agents must not edit the same uncommitted work simultaneously.
- OI/deterministic operations precede generative-AI claims. ChatGPT is recorded product/
  architecture authority; autonomous implementation is bounded by approved scope and gates.

See the ADRs, Product Decision Log, Master Product Execution Plan and Post-MVP-1 Modus Operandi.

## 18. Accepted limitations and deferred work

Pilot constraints are stable router LAN, no phone hotspot for mDNS transitions, no USB removal
while running, no Standard→Private switch, and expected unsigned Windows/Android-debug signing.
TD-017’s literal two-router transition and live mid-write USB pull remain unwitnessed. TD-021–023,
TD-025–027, TD-033/034 and P2 UI consolidation remain open/deferred. Historical internal-drive
remnants and orphaned vaults were deliberately preserved for future review.

## 19. MVP-1 versus MVP-1.1+

### Current MVP-1

Company discovery/selection; read-only Tally integration; Connector/Desktop lifecycle; local-first
Ledgers, Stock and Vouchers; search; sync/snapshots; trusted pairing/LAN discovery; Private storage;
PDF preview/save/share; settings/diagnostics; Controlled-Pilot hardening; bounded UI/UX polish.
MVP-1.0.x permits only defects, missed essentials and bounded micro-polish.

### MVP-1.1+ — PLANNED / NOT IMPLEMENTED

- MVP-1.1: Connect and Universal Party Identity; contacts and relationship actions; referral/
  indirect-value ideas only when approved.
- MVP-1.2: Relationship Timeline, Issue History, Dincharya and deterministic OI.
- MVP-1.3: Business Profile.
- MVP-1.4: Catalogue and governed assets.
- Vartalap, Insights and broader extensions remain concepts unless later committed evidence says
  otherwise.

Untracked planning/design artifacts do not make features implemented. MVP-1.1 has not started.

## 20. What remains before public release

| Classification | Evidence-grounded work |
|---|---|
| **BLOCKING PUBLIC RELEASE** | Human visual approval of Desktop 0.4.16; production/release signing (Windows and Android, not debug-only); final public artifacts with manifest/checksums and artifact-level regression from the intended clean baseline. |
| **SHOULD FIX BEFORE PUBLIC RELEASE** | Explicit Standard→Private warning/migration/re-pair flow (TD-033); fail distinctly on unreadable markers and reproduce TD-034 through the app path; decide TD-025 broad-user UX; verify first-install/upgrade and provide basic release/install/storage guidance. |
| **ACCEPTABLE FOR INITIAL LIMITED RELEASE** | Stable-router requirement; literal two-router residual; no USB hot removal; TD-022/023. Unsigned/debug signing only if distribution remains explicitly limited and warnings are accepted. |
| **POST-RELEASE / DEFERRED** | TD-026 if usage warrants; TD-027 observation; UI theme/scaffold/localization, Desktop tray/navigation; MVP-1.1+. |

**Exact NEXT TASK:** install Desktop `0.4.16`, perform and record final human visual approval using
`docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md` §10, without starting packaging or MVP-1.1.

## 21. Development and automation governance

The owner sets outcomes and accepts physical/product results. ChatGPT is recorded product and
architecture authority. Claude implements approved scopes; Codex reconstructs, validates or
implements when assigned. Git/repository documents—not chat—are durable truth.

> INSPECT → IMPLEMENT → TEST → COMMIT → UPDATE RELEVANT STATUS DOC → UPDATE CANONICAL
> CURRENT-DEVELOPMENT CHECKPOINT → CLEAN-TREE AUDIT → RECORD EXACT NEXT TASK

At each new AI session: read the checkpoint; inspect status/recent history; read the relevant
domain status and ledger; reconstruct from repository evidence; continue rather than redo. No
meaningful completed work may exist only in session memory.

## 22. Current source-of-truth references

- Current checkpoint: `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`
- Controlled Pilot: `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md`
- Quality: `docs/governance/BUDCOM-QUALITY-SCORECARD.md`
- Technical debt: `docs/technical-debt/registry.md`
- UI/UX: `docs/design/BUDCOM-MVP-1-UI-UX-POLISH-STATUS.md`
- Refinement/physical acceptance: `docs/planning/BUDCOM-MVP-1-0-X-REFINEMENT-REGISTER.md`
- Scope/roadmap: `docs/planning/BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`, `docs/ROADMAP.md`
- Architecture/governance: `docs/architecture/`, Product Decision Log, Post-MVP-1 Modus Operandi
- Release/runbooks: `docs/architecture/controlled-pilot-release.md`, `docs/operations/`
- Historical evidence: `docs/milestones/`, `docs/stage-updates/`, `docs/diagnostics/`,
  `docs/PROJECT_PROGRESS.md`
