# VENTURE — Adaptive Tally Synchronization — Architecture & Product Decision Research

**Status:** Originally research/architecture only (no production code changed to produce this
document). **Implemented 2026-08-22/23** (Phase 45) — the LOCKED decisions in §17 below were built
essentially as recommended: the staged-backoff state machine (§5-§6), the company-scoped
Connector-only scheduler (§8, §14), the same-sync-engine trigger with no parallel implementation
(§16), and the `scheduler_state` migration/table shape (§14). The two RECOMMENDED prerequisite
fixes (§17 item 8: Connector singleton sync-progress state, Android's dead
`clearActiveIfCompanyChanged()`) were fixed first, as this document itself insisted. **Item 10 (the
cheap pre-extraction signal) was investigated to a conclusion 2026-08-23 (Phase 47) — see §3.1: NOT
PROVEN SAFE, not implemented, real Tally evidence recorded.** Items 11-13 (the removed voucher
`already_current` fast path, exact timing-value tuning, and UI prominence) remain genuinely open.
See `docs/status/VENTURE-DEVELOPMENT-LEDGER.md` §33 for this document's own research session record,
§35 for the implementation session record, and §37 for the Phase 47 cheap-signal investigation;
`docs/status/VENTURE-CURRENT-DEVELOPMENT-STATUS.md` for the current-state pointer.

**Investigation date:** 2026-08-22.

## 1. Executive summary

VENTURE today has **no automatic Tally synchronization anywhere** — Desktop, Android, and the
Connector itself. Every Ledger/Stock Item/Voucher sync is a manual, user-tapped, full extraction
from Tally. The Connector's own `SchedulerService` is a literal no-op placeholder
(`SchedulerStub extends PlaceholderService`) that has never been implemented. Desktop's only
`setInterval` outside UI concerns is a private-storage watchdog; Android has zero `WorkManager`/
`AlarmManager` usage (confirmed by the README's own "WorkManager / automatic sync | Out of scope,"
and by direct code search finding zero `.enqueue(` calls anywhere in the module).

There is also **no existing lightweight, pre-extraction "did Tally change" signal** anywhere in the
codebase. Tally's `ALTERID` is already fetched and stored per-record (`alter_id` columns on
`ledgers`/`stock_items`, indexed), but only as one field inside the same full-collection pull used
for a normal sync — never as a standalone cheap probe. A genuine post-extraction content fingerprint
(`computeLedgerFingerprint()`/`computeStockItemFingerprint()`, SHA-256 over the extracted record) does
exist and is already used to skip redundant database writes — but it only runs *after* paying the
full Tally/XML/network cost of extraction, so it reduces storage churn, not Tally load. The project's
own technical-debt record (TD-006) explicitly documents that a reliable resume/watermark mechanism is
**not currently safe** to build, because "Tally export order is not guaranteed" and "no snapshot
identity exists" — this is the authoritative existing position, not a new finding invented for this
task.

**Conclusion up front:** the originally-proposed "cheap change detection → expensive sync only when
necessary" architecture (Strategy D/E in §5) is **not achievable today without a dedicated,
separately-scoped Tally-XML-API validation spike** that this task explicitly may not perform (it
would require probing Tally's own object-level `ALTERID` behavior on a `Company` object export — an
external-API question, not a VENTURE-code question). What **is** achievable today, safely, using only
already-proven mechanisms, is **time-based adaptive frequency of the existing real sync operation**,
paired with the existing post-extraction fingerprint to determine after the fact whether the sync was
"worth it," plus an always-visible manual Sync Now. This document recommends that narrower, honest
architecture as the locked default, and separately flags the cheap-signal idea as an open, unvalidated
future investigation — never silently assumed.

## 2. Current architecture, recovered directly from source (§3 of the governing task)

### 2.1 Desktop (`apps/venture_desktop`)

- Connector lifecycle health poll: `ConnectorLifecycleService.startHealthMonitoring()`
  (`connector-lifecycle-service.ts:300-307`), `setInterval` every `healthPollIntervalMs` (default
  `5_000`, `desktop-config-defaults.ts:10`) — checks `/health` reachability/readiness only, never
  triggers a Ledger/Stock Item/Voucher sync.
- Ledger/Stock Item sync: manual only, via `handleLedgerSync()`/`handleStockItemSync()`
  (`app.ts`) → `POST /sync/ledgers` / `POST /sync/stock-items`, with `startLedgerProgressPolling()`/
  `startStockItemProgressPolling()` polling `GET .../status` only *while that sync is running and the
  relevant view is visible* — not a scheduler, a progress-bar poll.
- No `setInterval`/`setTimeout` anywhere in `main.ts` triggers data sync; the only other timer is the
  private-storage-vault watchdog (`privateStorageWatchdogTimer`, unrelated).
- Freshness UX (Phase 41/42, already shipped): `refreshDashboardDataFreshness()` derives "Not synced
  yet"/"Partially synced"/"Synced · [timestamp]" from the real per-module `lastSyncedAt`, replacing an
  earlier dead placeholder. This is the existing "freshness philosophy" §11 of the governing task asks
  to reuse, not replace.

### 2.2 Connector (`connector/venture_connector`)

- **No scheduler exists.** `SchedulerService` (`services/interfaces/scheduler.ts`) is implemented only
  by `SchedulerStub` (`services/placeholders/scheduler.stub.ts`), a no-op `PlaceholderService`,
  registered at `bootstrap/register-services.ts:272`. `/health`'s `scheduler` sub-status always
  reports the placeholder's static value.
- All three sync surfaces are manual, HTTP-triggered: `POST /sync/ledgers`, `POST /sync/stock-items`,
  `POST /sync/vouchers` (`api/routes/{ledgers,stock-items,vouchers}.ts`). Ledger/Stock Item expose
  `GET .../status|statistics|runs`; **Voucher does not** — its progress is only observable
  synchronously during the POST call via an in-process observer, with no pollable status endpoint at
  all.
- Every extraction is a full pull: `LedgerSyncServiceImpl.executeSync()` calls
  `readPort.readLedgers(companyName, {signal})` unconditionally
  (`services/ledger/ledger-sync.service.ts:367`) — Tally's entire `'List of Ledgers'` collection, no
  field or date restriction, every time. Vouchers are a full pull *within a date window* (default 29
  days back), with an explicit code comment ruling out any "unchanged" shortcut: *"A matching
  requested period is never treated as evidence Tally content is unchanged — every explicit sync
  performs a real live extraction for its requested window"* (`voucher-snapshot-sync.service.ts:169-172`).
- Sync-state persistence: SQLite (`better-sqlite3`, schema v12). `sync_runs` table
  (`storage/sqlite/schema.ts:63-90`) — one row per Ledger/Stock-Item sync attempt, `company_id NOT
  NULL`, composite indexes on `(company_id, status)`/`(company_id, started_at)`. Voucher sync uses a
  separate, immutable atomic-snapshot mechanism (`voucher_snapshots`/`voucher_active_snapshots`, with
  DB triggers enforcing immutability once `PROMOTED`) plus a company-scoped lease table
  (`voucher_sync_reservations`, 15-minute lease) preventing concurrent voucher syncs per company.
- Throttling that already exists: `TallyRequestGuard` enforces a minimum 2-second interval between
  individual Tally XML requests (`config/defaults.ts:163`, forced ≥2s under `tallySafeMode`), a
  single-connection pool (only one Tally request in flight, ever), and a circuit breaker
  (`TallyCircuitBreaker`, always enabled). None of this throttles whole sync *cycles* — nothing stops
  a client from calling `POST /sync/ledgers` repeatedly beyond the per-request throttle and the
  active-run/single-flight checks below.
- **A real, previously-undocumented company-isolation gap, found by this investigation**:
  `LedgerSyncServiceImpl`/`StockItemSyncServiceImpl` are registered as process-wide singletons
  (`core/container.ts` factory caching), and their in-memory `progress`/`activeRun`/`syncInFlight`
  fields are **not keyed by `companyId`** — `LedgerSyncProgress` has no `companyId` field at all, and
  `GET /sync/ledgers/status` takes no company parameter, returning whichever company's sync last ran
  in this process. The persisted `sync_runs` rows themselves are correctly company-scoped — this is
  purely an in-memory status-polling gap — but the single-flight guard is *also* global, meaning a
  legitimate sync for Company B can be rejected with a generic "already running" while Company A's
  sync is in flight, even though nothing in the database actually conflicts. **This must be treated as
  a prerequisite fix, not an incidental detail** — see §8 and §17.
- Failure reporting is inconsistent between sync types: Ledger/Stock Item failures set a non-200 HTTP
  status and a `status: 'failed'` field (`normalizeSyncFailure()`, unambiguous). **Voucher sync always
  returns HTTP 200**, even on failure — the route always does `res.status(200 or 409).json(...)`; a
  caller must parse the JSON body's `outcome`/`status` field to detect failure. A caller (including a
  future scheduler) that checks only the HTTP status code would misread a failed voucher sync as
  success.
- Historical signal worth carrying forward: `VoucherSyncOutcome` still declares an `'already_current'`
  member, with a code comment stating it *"can no longer be produced ... every explicit sync now
  performs a real live extraction ... kept only as a defensive no-op."* This strongly implies a
  cheap-skip fast path for vouchers **existed and was deliberately removed**, with no in-source record
  of why. This is flagged as **OPEN** in §17 — the reasoning should be recovered from the team before
  any new cheap-skip mechanism is proposed for vouchers specifically.

### 2.3 Android (`apps/venture_android`)

- Sync is manual-only, tap-driven (`SyncScreen.kt` → `SyncViewModel` → `StartTargetSyncUseCase`/
  `RunAvailableSyncsUseCase` → `POST sync/ledgers|stock-items|vouchers`). **No `WorkManager`,
  `AlarmManager`, or background scheduling exists** — `HiltWorkerFactory` is wired only to satisfy
  `Configuration.Provider`, and `AndroidManifest.xml` explicitly disables the default
  `WorkManagerInitializer`.
- Two foreground-only `delay()` loops exist and are easily mistaken for a scheduler but are not one:
  `ObserveSyncProgressUseCase` polls `GET .../status` every 1.5s **only while a user-started sync is
  actively running** (a progress-bar poll), and `DashboardViewModel.reconcileWhileActive()` re-probes
  Connector *health* (not data sync) every 30s, and only while the Dashboard is the foreground,
  resumed screen (`repeatOnLifecycle(RESUMED)`).
- Room caching is fully implemented (the README's "Durable Room cache | Deferred" note is stale — 10
  schema versions exist). Two distinct "last synced" concepts exist, not durably connected: a
  **durable per-row** `syncedAt`/`dataFreshnessAt` on `cached_ledgers`/`cached_stock_items` (composite
  PK includes `companyId`) and `voucher_cache_meta(companyId PK, lastSyncedAt)`; and an **in-process
  only** `SyncStatusSummary` (explicitly documented in its own code: *"Not durable across process
  death unless refreshed from Connector"*) rehydrated on restart only by re-querying the Connector.
- Local-first is **confirmed but inconsistent across features**: Ledger and Voucher browsers are
  genuinely Room-only on open (network touched only via an explicit Refresh action) — Ledger's own
  code comment: *"the fast path used on every normal screen open, search keystroke, and pagination
  fetch."* **Stock Item does not follow this pattern** — `StockItemRepositoryImpl.loadStockItems()`
  calls the network first and only falls back to Room on failure; its "Load" and "Refresh" use cases
  are literally identical. This is a pre-existing inconsistency, not something this task changes, but
  the adaptive-sync design must not assume Stock Item behaves like Ledger.
- **A second, real, previously-undocumented company-isolation gap, found by this investigation**:
  `SyncRepositoryImpl.bindCompany(companyId)` only overwrites the summary's `companyId` field — it
  does not clear the per-target `lastSuccessfulAt`/`statistics`/`liveProgress` left over from the
  previously-selected company. A method named `clearActiveIfCompanyChanged()` exists and appears
  purpose-built to fix exactly this — but it is **never called from anywhere** (dead code). If the
  first status/statistics call for the newly-selected company fails or the company has simply never
  synced, the previous company's "Last synced at ..." can remain visibly displayed. Flagged in §8/§17
  as a prerequisite fix, mirroring the Connector's own singleton-progress gap.
- Reentrancy guards exist (`SyncRepositoryImpl.localActiveTarget`, `SyncViewModel.startJob?.isActive`)
  but are simple boolean/Job checks — **no generation-counter pattern** exists on the Android side,
  unlike Desktop's own well-established TD-014 generation-counter convention.
- `SyncMode { Full, Incremental }` is already plumbed end-to-end on the wire
  (`SyncStartRequestDto.incremental`) but is **never actually exercised** — every Android "Sync Now"
  tap defaults to `Full` and no caller ever overrides it. On the Connector side, the `incremental` flag
  that *is* honored only skips a post-extraction database write when the fingerprint matches — it
  never changes what is requested from Tally. "Incremental" as a concept already exists in name but
  currently means "skip a redundant write," not "skip a redundant extraction."

### 2.4 Tally

VENTURE cannot currently determine, without a full extraction, whether Tally has changed, what
changed, or whether a sync produced identical data to before — the fingerprint mechanism answers the
last question, but only after paying for extraction. No lightweight metadata query, cursor, or
snapshot identity exists (§2.2, and TD-006's own accepted-limitation language). This is the direct,
sourced answer to the governing task's Tally-layer questions — not an assumption.

## 3. The critical architectural question (§4 of the governing task)

**Can VENTURE detect "Tally changed since last sync" without downloading/reconstructing the entire
relevant dataset? No — not with anything that exists in this codebase today**, and the project's own
technical-debt record (TD-006) treats the closely-related "resume from a watermark" question as
explicitly unsafe to assume, for a *documented* reason: Tally's XML export order is not guaranteed,
and no snapshot identity contract exists.

**Is a cheap signal *theoretically* possible?** Tally's XML API is generally known (outside this
codebase — this is external ERP-integration knowledge, not something found in VENTURE's source) to
expose an `ALTERID`/`ALTERMASTERID`/`ALTERVOUCHERID` concept at both the record and company level,
which third-party Tally integrations sometimes use for cheap change polling. VENTURE already fetches
per-record `ALTERID` (stored, indexed) and already has the exact request-shape scaffold that a
company-level probe would extend (`MasterDataTemplates.companyInfo()` uses `buildObjectTemplate` for
a single `'Company'` object export, distinct from the full-collection `buildCollectionTemplate` used
for Ledgers/Stock Items) — but **no code anywhere in the Connector currently requests or parses
`ALTERMASTERID`/`ALTERVOUCHERID`** (confirmed by a zero-result search), and this document does not
assume it will work against VENTURE's actually-supported Tally versions/editions.

**Decision**: do not fabricate a cheap signal. Recommend it as a distinctly-scoped **OPEN** future
investigation (§17), validated by a small, isolated spike against a real Tally instance before any
implementation task depends on it. The architecture recommended in §6 does **not** depend on this
signal existing.

### 3.1 Item 10 resolved (2026-08-23, Phase 47): NOT PROVEN SAFE — investigated to a conclusion

The OPEN spike this section called for was performed against the real, live TallyPrime instance
(same installation as all prior real-device sessions; companies ESTIMATION and Jaju Sanitations).
**Conclusion: no genuinely cheap, pre-extraction, company-level Tally change-detection signal is
available. Not implemented.** Full evidence:

**Candidate 1 — a company-level `ALTERID`/`ALTERMASTERID` object probe.** VENTURE's own
`MasterDataTemplates.companyInfo()` / `buildObjectTemplate()` (the "exact request-shape scaffold"
this document originally pointed to) turned out to be dead code with zero callers, and — tested live
— structurally invalid: it emits `<TYPE>Object</TYPE><ID>Company</ID>` with no `<SUBTYPE>` and a
bare `<ID>` rather than `<ID TYPE="Name">`. Issuing this request against the real Tally instance
produced a blocking error dialog on Tally's own UI (window title changed to "Error", requiring a
manual dismissal/restart before Tally would answer further requests — a genuine, disruptive,
now-documented cost of guessing at unvalidated request shapes against a live production Tally).
Tally's own developer documentation (`help.tallysolutions.com`, Case Study 1 — Object export)
confirms the correct shape requires `<TYPE>OBJECT</TYPE><SUBTYPE>Ledger</SUBTYPE><ID
TYPE="Name">...</ID>` plus a `<FETCHLIST>/<FETCH>` block — a different mechanism from
`collectionModifyFetch`'s `<COLLECTION ISMODIFY="Yes">` used everywhere else in VENTURE. More
fundamentally, Object-type export is documented and used only for **named, keyed masters** (a
specific Ledger, Stock Item, Voucher) — "Company" is a *context* selected via
`SVCURRENTCOMPANY`, not a keyed master object Tally exposes through this gateway. No authoritative
Tally documentation and no independent third-party Tally-integration project (a live web search
included a real Tally↔ODBC connector, `ramajayam-CA/Tally-Connector`, which surfaces per-record
`$Alterid`/`$Alteredon` exactly as VENTURE already does, but implements no company-level marker and
no incremental filter) shows any working mechanism for a single company-level "has anything
changed" value. **Verdict: not available, not merely untested.**

**Candidate 2 — a lightweight `NAME/GUID/ALTERID`-only collection fetch, diffed against a persisted
per-record watermark.** Technically buildable and safe-by-construction (reuses the exact
`collectionModifyFetch` mechanism VENTURE's production Ledgers/StockItems sync already sends
successfully every day — no new request shape, no new trust boundary). Measured live against the
real ESTIMATION company (949 real ledgers): a 3-field fetch (`NAME, GUID, ALTERID`) returned in
**0.23-0.28s** across four repeated real requests and **386,343 bytes**; the existing full 8-field
fetch (`NAME, GUID, ALTERID, MASTERID, PARENT, OPENINGBALANCE, CLOSINGBALANCE, ISBILLWISEON`)
returned in **0.295s** and **637,871 bytes**. **The wall-clock cost is statistically indistinguishable
between the two** — Tally's own internal collection-walk time dominates at this scale and is not
reduced by requesting fewer fields; only network bytes (~40% smaller) and Connector-side downstream
work (domain mapping, fingerprint compute, SQLite upsert — all skipped in a pure detect-only pass)
would actually shrink. This is a real but modest, different benefit than the "cheap check → do
almost nothing" ideal in §2 of the governing task — it does not reduce Tally-server-side load, which
was the primary stated objective. **Verdict: not proven beneficial enough, at this measured scale,
to justify a new persisted per-record watermark table (one row per ledger/stock item per company) on
top of the existing content-fingerprint mechanism (§7) that already does the same comparison as a
side effect of the full sync.**

**A genuine, live-proven company-isolation hazard surfaced during this same measurement, unrelated
to whether either candidate is adopted:** Tally's `GUID` field is `<data-source-installation-UUID>-
<hex MasterId>`, and **MasterId is small and per-company** — the live experiment found all 21 of
Jaju Sanitations' real `GUID` values also present verbatim in ESTIMATION's real 949-`GUID` set
(confirmed these are genuinely different real ledgers by name, not a test-script error). A bare
Tally `GUID` is **not globally unique across companies in this real installation** — it must always
be paired with `companyId`. VENTURE's existing `ledgers`/`stock_items` tables already do this
correctly (`idx_ledgers_company_guid`/`idx_stock_items_company_guid` are unique indexes on
`(company_id, guid)`, never `guid` alone — `connector/venture_connector/src/storage/sqlite/schema.ts`)
— no existing defect, but this real-data confirmation is recorded here as binding evidence for *any*
future work (this spike's candidate 2, or anything else) that might be tempted to use a bare Tally
GUID as a key: it would silently collide across companies, proven live, not hypothetically.

**Item 10 is now CLOSED as investigated: NOT PROVEN SAFE / NOT PROVEN BENEFICIAL, not implemented.**
No production code changed as a result of this spike. Full evidence, exact requests/responses, and
the incident writeup: `docs/status/VENTURE-DEVELOPMENT-LEDGER.md` §37.

**Forensic follow-up (Phase 48, `docs/status/VENTURE-DEVELOPMENT-LEDGER.md` §38):** a full review of
the incident above found it was consistent with a Tally-side application fault triggered by the
structurally invalid request (confirmed: no Windows Error Reporting crash record exists for
`tally.exe` on that date, despite the WER pipeline being active and having recorded six earlier
`tally.exe` crashes/hangs on this machine — the exact internal mechanism inside Tally's own process
is not independently provable beyond that). The same review also found that the identical broken
request shape is independently constructed by a real, "production"-classified Connector operation
(`ApprovedOperationId.CompanyInfo`) — unreachable from any real Desktop/Android client today, but a
live landmine — fixed by disabling it (**TD-040**, `docs/technical-debt/registry.md`) rather than
guessing a second unverified shape. **Permanent rule this establishes**: never send a hand-crafted or
newly-added Tally TDL/XML request shape — research probe or new Connector code alike — to a live
Tally instance without first validating it against Tally's own official developer documentation or a
disposable non-production instance.

## 4. Strategy comparison (§5 of the governing task)

| | Tally/Connector load | Freshness | Complexity | Depends on unproven signal? |
|---|---|---|---|---|
| **A — Fixed interval** | Constant, regardless of activity — wastes cycles when idle, under-serves during bursts | Flat, mediocre | Lowest | No |
| **B — Adaptive 15/60 (flat two-state)** | Much better than A when idle; a sharp 4x cliff at the 15→60 boundary | Good during activity, abrupt drop-off | Low | No |
| **C — Staged backoff (15→30→60)** | Same idle-state savings as B, without the cliff — degrades gracefully | Good, smoother | Low-moderate | No |
| **D — Change-detection driven** | Best possible *if* a cheap signal existed | Excellent | Moderate-high | **Yes — the one that doesn't exist today** |
| **E — Hybrid (cheap detection + adaptive + manual)** | Best possible, same caveat as D | Excellent | Highest | **Yes** |

Race conditions, offline behavior, and multi-company correctness are addressed identically by B/C/D/E
once §6-§9 below are applied — the strategies differ only in *when* a real sync is triggered, not in
how a sync, once triggered, must behave safely. Testability favors B/C: their triggering condition is
pure elapsed-time arithmetic, trivially unit-testable with fake timers (the same pattern already used
extensively in Desktop's own test suite, e.g. `dashboard-recovery.test.ts`'s fake-timer bounded-retry
tests). D/E's triggering condition would additionally require mocking a Tally-API behavior that has
never been validated to exist, making their tests exercise an assumption rather than a proven
contract.

**Recommendation: Strategy C (staged time-based backoff) now, with D/E's cheap-detection premise kept
as a distinctly future, separately-validated upgrade path — not blended into the same task.** This is
Strategy E's shape without its unproven prerequisite; upgrading from C to E later, if the Tally-signal
spike succeeds, is a localized change (swap what triggers "change detected" — a fingerprint diff after
a full sync today, a cheap probe result tomorrow) rather than a redesign, because the state machine in
§5 already treats "change detected" as an abstract input, not something coupled to how it was
detected.

## 5. Recommended synchronization state machine (§6, §11 of the governing task)

```text
                    ┌─────────────────────────────────────────────┐
                    │                                               │
                    ▼                                               │
   ┌─────────────┐   change detected    ┌────────────────┐          │
   │   BACKOFF   │──────────────────────▶│  ACTIVE WINDOW │          │
   │ (staged)    │                       │                │          │
   └─────────────┘                       └────────────────┘          │
        ▲  │                                    │   │                │
        │  │ next stage reached,                │   │ check finds    │
        │  │ still no change                    │   │ a change ──────┘
        │  │                                     │   │ (resets window timer)
        │  └─────────────────────────────────────┘   │
        │        window expires, no change             │
        │        found across the whole window          ▼
        └──────────────────────────────────────  (stays in ACTIVE WINDOW)

   Any state ──── manual "Sync Now" ────▶ real sync now, result feeds the same
                                          "change detected?" evaluation, state
                                          machine reacts exactly as if its own
                                          timer had fired (no separate code path)

   Any state ──── sync attempt fails ───▶ NO state transition either direction;
                                          retry at current interval; failure is
                                          never treated as "no change" (§8)
```

Two states, one of them staged — deliberately not more than this. `ACTIVE WINDOW` and `BACKOFF` are
the only states a user or the freshness UI ever needs to reason about; `BACKOFF`'s internal stages
(§6) are an implementation refinement, not new user-facing vocabulary.

## 6. Timing/backoff rules (§12 of the governing task)

The 15-minute/60-minute concept from the earlier product discussion is **preserved as the recommended
default**, refined from a flat two-state jump into a staged backoff, and made explicit about what a
"check" actually costs:

- **ACTIVE WINDOW**: a real sync (the same one manual Sync Now performs — see §5's "no separate code
  path" note) every **5 minutes**, for as long as the window keeps getting renewed. The window itself
  lasts **15 minutes past the last detected change** — i.e. up to three checks land inside a typical
  window before it's allowed to expire, giving the staged-backoff evaluation something better than a
  single data point to act on. Any check that finds a change resets the 15-minute window clock.
- **BACKOFF, staged**: once the ACTIVE WINDOW genuinely expires with no change found, the interval
  steps up: **15 min → 30 min → 60 min**, each step reached only after another full no-change check at
  the *previous* interval — not a chain of independent timers. 60 minutes is the floor; the interval
  never grows past it while idle. A change detected at any stage jumps straight back to ACTIVE WINDOW
  at the 5-minute cadence, not back through the staged ladder.
- **Every check is a real sync**, not a cheap probe (per §3) — so "5 minutes during the active window"
  is a genuine, bounded Tally/Connector cost, not a free poll. This is why the active-window interval
  is deliberately conservative (5 min, not 1 min) even though the *product* framing talks about "15
  minutes of frequent checks" — the frequency inside that window has real cost and should not be
  pushed faster than the freshness benefit justifies.
- These are **RECOMMENDED, not LOCKED** (see §17) — they are a principled starting point derived from
  the existing 15/60 product concept plus this investigation's cost evidence, not a value proven
  optimal by measurement. The next implementation task should keep them configurable (the Connector
  already has a config-resolution pattern for exactly this, e.g. `healthPollIntervalMs`) rather than
  hardcoded, so they can be tuned post-launch without a code change.

## 7. Definition of "change" (§7 of the governing task)

Per module, not blended:

- **Ledger change** = at least one ledger record's `computeLedgerFingerprint()` differs from its
  previously-stored value, *or* a ledger exists in the new extraction that didn't exist before, *or*
  a previously-extracted ledger is now absent (a real deletion signal, currently not reconciled per
  TD-026's "no deletion/edit tombstone" note — the adaptive scheduler should not assume deletions are
  detected until that gap is separately addressed).
- **Stock Item change** = the analogous `computeStockItemFingerprint()` comparison.
- **Voucher change** = a new/changed voucher inside the synced date window, per the existing
  window-carry-forward logic (`voucher-snapshot-sync.service.ts`) — vouchers outside the window are
  explicitly not re-checked by this scheduler design; that is a separate, already-accepted limitation
  (TD-022/TD-026), not something adaptive sync changes.
- **"Any Connector/Tally response change"** (e.g. a transient extraction-format quirk with no
  meaningful business content difference) is explicitly **not** what "change" means for scheduling
  purposes — only the fingerprint/existence comparison above counts. This distinction matters because
  a naive "did the raw XML differ" check would be far noisier than a content-fingerprint check (Tally
  is known to vary incidental formatting/ordering between otherwise-identical exports), inflating the
  ACTIVE WINDOW dwell time without a real freshness benefit.
- A **failed** sync is never "no change" — see §8.

## 8. Multi-company isolation model (§8 of the governing task)

**Boundary: `companyId` alone is sufficient** — the Connector is a single per-machine process with
exactly one active Tally connection at a time; there is no multi-connector-instance or
multi-session-per-company scenario in this architecture that would require a compound
`companyId + connector/session identity` key. This matches the boundary already used consistently at
the database layer (`sync_runs`, `ledgers`, `stock_items`, all keyed by `company_id` as the first
column of a composite key).

**The scheduler's own state must be implemented as an explicit `Map<companyId, SchedulerState>` (or
an equivalent `company_id`-keyed table) from the very first line of code — never a singleton field.**
This is not a generic caution; it is a direct lesson from two *already-existing, previously
undocumented* bugs this investigation found by reading the current code, both of exactly this shape:

1. **Connector**: `LedgerSyncServiceImpl`/`StockItemSyncServiceImpl`'s in-memory `progress`/
   `activeRun`/`syncInFlight` are process-wide singleton fields, not keyed by company (§2.2). Polling
   `/sync/ledgers/status` after switching companies can return the *previous* company's leftover
   progress, and the single-flight guard can spuriously reject Company B's sync while Company A's is
   in flight.
2. **Android**: `SyncRepositoryImpl.bindCompany()` doesn't clear the previous company's cached target
   summaries, and the method apparently written to fix this
   (`clearActiveIfCompanyChanged()`) is dead code, never called (§2.3).

**These two gaps should be treated as prerequisite fixes for the adaptive-sync implementation task,
not merely "worth noting."** A scheduler that fires automatically, unattended, on a timer will surface
these exact gaps far more often and more confusingly than today's manual-tap-only world does — a user
who never switches companies quickly, or always waits for a fresh manual sync to settle before
switching, may never notice either bug today; a background scheduler ticking every 5-60 minutes across
however many companies are configured removes that accidental protection.

## 9. Failure semantics (§9 of the governing task)

| Condition | Adaptive-scheduler behavior |
|---|---|
| Tally closed | Sync attempt fails (Connector's existing `TallyConnectionManager` transitions to `disconnected`/`degraded`, per its own ping-driven state machine). **No state transition.** Retry at the *current* interval, not a faster or slower one. |
| Tally open but unreachable (e.g. busy on a blocking dialog) | Same as above — the Connector already distinguishes this via `TallyConnectionManager`'s `degraded` state (HTTP 5xx from Tally) vs `disconnected` (no response); both are failures for scheduling purposes. |
| Connector unreachable (Desktop/Android's perspective) | Not a scheduler concern — the scheduler lives in the Connector process; if the Connector itself is down, nothing is scheduling anything, and Desktop/Android already show their existing "Not connected" state (§11 of Phase 41/42's work). |
| Network disappears | Same as Tally-unreachable — surfaces as an extraction failure, handled identically. |
| A sync fails outright | **Never treated as "no change."** The staged-backoff clock does not advance; the window does not expire early or extend early; the interval stays exactly where it was before the failed attempt. |
| XML parsing fails | Same as above — this already produces a distinct `failed` status with `failureCode`/`failureSummary` (Ledger/Stock Item) today; the scheduler reads that status, not a raw exception. |
| Partial extraction succeeds | Treated as a failure for scheduling purposes unless the existing sync-service's own definition of "succeeded" says otherwise — the scheduler should not invent a third partial-success category on top of the sync engine's own `SyncOutcome`/`LedgerSyncStatus` vocabulary (§7's "don't blend concepts" principle applies here too). |
| User manually presses Sync Now | Goes through the exact same code path a scheduled check would (§5) — its result feeds the same change-detection evaluation and resets/extends the window exactly as an automatic check would. No separate "manual" state. |
| App/Desktop/Connector/Android restarts | The Connector already recovers abandoned `sync_runs` rows as `interrupted` on restart (`recoverAllAbandonedRuns()`, TD-006's resolution). The scheduler's own persisted state (§14) should be read back on Connector startup and resume from wherever it left off — an interrupted check is not evidence of "no change" either, matching the failure rule above. |
| Android reconnects later | Purely a display concern — Android already re-hydrates its in-process `SyncStatusSummary` from the Connector on demand (§2.3); nothing scheduler-specific changes for Android, since Android never runs the scheduler itself (§14). |

**Critical rule restated as an invariant, because the governing task marks it critical**: the
staged-backoff ladder may only advance on a *confirmed successful* check that found no change. Every
other outcome (failure of any kind, cancellation, restart-interruption) leaves the ladder exactly
where it was and retries at the same interval.

## 10. Manual Sync Now behavior (§10 of the governing task)

- **Always visible, always available**, on both Desktop and Android, regardless of adaptive state —
  this is not new; both platforms already have it. The adaptive scheduler changes *when automatic*
  syncs happen; it never removes or gates the manual control.
- **"Finished working in Tally? Sync now" contextual suggestion**: there is no reliable way today to
  know a Tally *session* (as opposed to a single request) has ended — Tally's XML interface is
  request/response, not a persistent session VENTURE observes end-to-end. Per the governing task's own
  instruction ("if there is no reliable way ... do not invent one"), this document does **not**
  propose inventing a session-end detector. It does identify one existing, non-invented, deterministic
  proxy already present in the code: the Connector's `TallyConnectionManager` state machine transition
  from `connected` to `disconnected`/`degraded` (driven by real ping/request outcomes, not a guess).
  This transition is a reasonable, debounced (to avoid reacting to a single transient blip) trigger for
  a **soft, dismissible** suggestion — "Tally connection was lost — Sync Now to catch up?" — framed
  honestly as "the connection dropped," not "you finished your session" (VENTURE cannot know the
  latter). This is a **RECOMMENDED**, not **LOCKED**, UX detail (§17) — the exact wording and whether
  to show it at all is a product call, not an architecture one.
- **Freshness display**: reuse the existing Phase 41/42 Desktop convention exactly
  (`Synced`/`Partially synced`/`Not synced yet` plus a real timestamp) and extend it with one new,
  honest, non-technical fact: the scheduler's *next* check is not something a normal user needs to see
  ("waiting 47 minutes for the next automatic check" invites the wrong question, "why not now?") —
  what they need is **whether they're covered**: "Checking regularly" (ACTIVE WINDOW) vs "Checking
  occasionally" (BACKOFF), both alongside the same last-synced timestamp already shown today, and Sync
  Now always available regardless of which state is showing.

## 11. User trust / freshness UX (§11 of the governing task)

Nothing here is new vocabulary — it reuses the exact "trust" wording standard already established and
audited in the Desktop UX Polish passes (Phase 41/42): plain terms, no invented jargon, no hidden
background behavior, failure always visible, cached data always distinguished from a fresh check. The
adaptive scheduler adds exactly one new fact the user might reasonably want ("is VENTURE checking often
right now, or not") and this document recommends surfacing it with the two-word states above, never
raw interval numbers, never internal state-machine names.

## 12. Performance model (§12 of the governing task, §21 of the final report)

Real, session-sourced numbers used as anchors (no fabricated CPU/network figures): a real company
synced during this session's earlier physical validation had **941 ledgers, 247 vouchers**; a real
Desktop cold-connect reaches `starting → connected` in **~1.05–1.07s**; the Connector enforces a
**minimum 2-second gap** between individual Tally XML requests. Using these as order-of-magnitude
anchors, not precise benchmarks:

- **Scenario 1 — Tally actively used for 2 hours.** Fixed-interval (Strategy A) at, say, a 15-minute
  flat cadence: 8 full syncs regardless of activity. Adaptive (Strategy C): if changes keep landing
  roughly every 10-15 minutes throughout, the system stays in ACTIVE WINDOW the whole time — **worst
  case, no fewer syncs than A** (this is expected and correct: freshness during genuine activity should
  not degrade). The benefit of C over A appears specifically in scenarios 2-4.
- **Scenario 2 — Tally used for 30 minutes.** A stays flat (2 syncs in 30 min at a 15-min cadence). C:
  changes during the 30 minutes keep the window alive (≈6 checks at 5-min cadence during that half
  hour), then the window empties and backoff begins climbing (15→30→60) for the rest of the day. Net:
  more checks *during* the active half hour (better freshness exactly when it matters), far fewer for
  the remaining ~7.5 working hours.
- **Scenario 3 — Tally opened briefly and closed.** A single change, then Tally disconnects
  immediately. C fires the triggering check, detects the change, opens a 15-minute window, but every
  subsequent check inside that window finds nothing further (Tally is gone) — the window still expires
  normally into staged backoff. This is the scenario where a *cheap* pre-check (§3, not available
  today) would have saved the most; without it, C still costs up to 3 "wasted" full checks (5, 10, 15
  minutes after the single real change) before backing off — an honest, bounded cost, not zero.
- **Scenario 4 — Tally unused for an entire business day.** A: constant fixed-interval cost all day
  regardless. C: after the first backoff ladder completes (≤105 minutes: 15+30+60), the system settles
  at the 60-minute floor for the rest of the day — roughly an **8-9x reduction** in sync count over an
  8-hour idle day compared to a flat 15-minute A baseline (≈32 syncs → ≈7-8 syncs), while never going
  fully silent (the 60-minute floor still exists, satisfying "never treat idle as done forever").
- **Scenario 5 — multiple companies.** Cost multiplies per company, independently, since §8 requires
  fully independent per-company scheduler state — a busy Company A and an idle Company B must never
  share or influence each other's cadence. No cross-company savings should be assumed or engineered;
  attempting to "batch" companies together would violate the isolation requirement in §8 for a dubious
  efficiency gain.

These are **relative comparisons derived from the repository's own real numbers**, not precise
production telemetry — the governing task explicitly permits this ("use formulas or relative
comparisons where necessary" when exact figures aren't available), and this document does not claim
more precision than that.

## 13. Local-first architecture preserved (§13 of the governing task)

The adaptive scheduler is a background *trigger* for the exact same sync operation that already
exists — it does not touch, gate, or block the read path at all. Ledger and Voucher browsing on
Android already read Room first, unconditionally (§2.3); this document does not change that. A
background-triggered sync writes into the same tables a manual sync would, and the existing local-first
read path picks up the fresher data the next time it's queried — no new coupling between "is a
background check running" and "can I see my cached list right now" is introduced or should ever be
introduced. The one flagged inconsistency (Stock Item's network-first Android behavior, §2.3) is a
**pre-existing** gap, unrelated to and not created by this design — noted, not fixed, per this task's
explicit "no production code" boundary.

## 14. Room / DataStore / state-storage recommendation (§14 of the governing task)

**The scheduler and its state belong in the Connector, not Desktop or Android.** The Connector is the
single process with an actual Tally connection; Desktop and Android are both just HTTP clients of it.
Running an independent scheduler on each client would (a) triple the Tally load for no freshness
benefit, since only one client's trigger needs to fire per company, and (b) require inventing
cross-client coordination to avoid duplicate syncs — complexity with no upside. Desktop and Android
should only ever *display* the Connector's already-computed state (§11), read via a small extension to
an existing endpoint (e.g. an additional `schedulerState` field on `GET /sync/ledgers/status`, not a
new endpoint) — never compute or persist their own copy of it.

**State storage: extend the Connector's existing SQLite database — not Room, not DataStore, not a new
storage technology.** The Connector already owns exactly this kind of state (`sync_runs`) in the same
process, same file, same backup/migration story. Concretely, this needs one small, new, company-scoped
table — analogous in shape to the existing `voucher_active_snapshots` "current pointer" table sitting
alongside the historical `voucher_snapshots` log — holding, per `(company_id, resource_kind)`: current
adaptive stage (`active_window` / `backoff_15` / `backoff_30` / `backoff_60`), the active-window
expiry timestamp, and the next-check-due timestamp. This is **not** the same row shape as `sync_runs`
(which is an append-only history of individual attempts) — a separate small table matches the
project's own existing convention of keeping "current state" and "historical log" as distinct tables
rather than overloading one. **No schema change is implemented in this task** — this is a
recommendation for the next implementation task to design in detail and migrate properly, following
the Connector's existing versioned-migration pattern (`STORAGE_SCHEMA_VERSION`).

## 15. Security / data-integrity review (§15 of the governing task)

- **Company isolation**: addressed directly in §8 — the two found gaps must be closed as part of (or
  immediately before) implementing the scheduler, not left for later, because a background trigger
  will exercise them far more than today's manual-only flow does.
- **Malformed Tally responses / special characters / parser sanitization**: unaffected by this design
  — the scheduler triggers the exact same extraction/parsing pipeline (including the existing
  `sanitizeXml10IllegalCharacters()` safeguard from the TD-001 fix) that manual sync already uses. No
  new parsing path is introduced.
- **Fingerprints/hashes**: the existing SHA-256 content fingerprint is reused as-is for change
  detection (§7) — no new hashing scheme, no weakening of the existing one.
- **Stale-state reuse**: directly what §8's Map-not-singleton requirement prevents.
- **Corrupted sync-metadata**: the new scheduler-state table should follow the same defensive read
  pattern already used elsewhere in this codebase (e.g. `desktop-config.json`'s schema-validation +
  defaults-fallback) — an unreadable/corrupt scheduler-state row should fall back to a safe default
  (`ACTIVE WINDOW`, i.e. err toward more freshness, not less) rather than crash or silently disable
  scheduling.
- **Replayed/stale responses**: not a new risk — the scheduler doesn't introduce a new transport or
  caching layer between Connector and Tally.
- **Concurrent sync requests**: addressed in §16.
- **Manual sync while automatic sync is running**: addressed in §16.
- The design does not touch, weaken, or route around the TD-001 sanitization fix or the TD-035
  company-isolation fix for `PARENT`/group data — both remain fully in the extraction path the
  scheduler calls into unchanged.

## 16. Concurrency model (§16 of the governing task)

**No second synchronization state machine is created.** The scheduler is purely a *trigger* — when its
timer decides a check is due, it calls the exact same internal sync-execution path
(`LedgerSyncServiceImpl.syncLedgers()` etc.) that the `POST /sync/ledgers` HTTP handler already calls,
inheriting every existing safety mechanism automatically: the per-company `findActiveRun()` check, the
`TallyRequestGuard` rate limiter, the circuit breaker, and (once fixed per §8) a properly company-scoped
single-flight guard.

- **Automatic sync + manual Sync Now**: before firing, the scheduler should check whether a sync for
  that company already started very recently (via the existing `sync_runs`/`listRuns` query, not a new
  mechanism) — if a manual sync just landed, the scheduler should skip its own trigger and simply
  observe that sync's result to update its state (§5's "same code path" principle), rather than firing
  a redundant second sync.
- **Two automatic triggers** (e.g. a restart racing the timer): prevented by the same per-company
  active-run check every sync path already goes through today.
- **Company switch during sync**: the in-flight sync for the old company is unaffected (it already
  runs to completion or failure independent of UI state today); the scheduler's own state for that
  company is untouched (it's keyed by company, §8) — only the *newly selected* company's scheduler
  state becomes relevant to what the UI shows next.
- **Connector restart during sync**: already handled by the existing `recoverAllAbandonedRuns()`
  mechanism (TD-006) marking the interrupted run `interrupted`, not silently lost or falsely
  "succeeded." The scheduler reads this the same way any other caller would — an interrupted run is a
  failure for scheduling purposes (§9), never a "no change."
- **Android reconnect during sync**: no scheduler-specific concern — Android never runs the scheduler
  (§14); it only re-displays state on reconnect, same as today.
- **Desktop restart during sync**: same as Connector restart — Desktop doesn't own the scheduler
  either.

## 17. Product recommendation — LOCKED / RECOMMENDED / OPEN

Per the governing task's explicit instruction, nothing below is silently promoted to LOCKED without
clear justification, and OPEN items genuinely require ChatGPT/Product-Owner sign-off before an
implementation task should begin.

### LOCKED (product decisions this research recommends locking now)

1. **The scheduler lives in the Connector, as a single per-company-scoped process, never duplicated
   per client.** (§14) — architecturally load-bearing; changing this later would be a rewrite, not a
   tweak.
2. **A failed sync attempt never advances or resets the adaptive-backoff ladder in either direction.**
   (§9) — the governing task marked this "critical," and it's cheap to guarantee and expensive to get
   wrong.
3. **The scheduler triggers the *same* sync-execution code path as manual Sync Now — no parallel sync
   implementation.** (§16) — this is what makes every existing safety mechanism (rate limiting,
   circuit breaker, sanitization, TD-001/TD-035 protections) apply automatically, with zero new attack
   surface.
4. **Company boundary is `companyId` alone; scheduler state is a `Map`/table keyed by it from the
   first line of code, never a singleton.** (§8) — directly evidenced by two real bugs already found
   in the current codebase of exactly this shape.

### RECOMMENDED (strong architecture/product recommendations, still open to Product Owner adjustment)

5. **Strategy C — staged time-based backoff (15 → 30 → 60 minutes), 5-minute checks during a
   15-minute active window** — as the concrete default (§5, §6). The 15/60 concept from the earlier
   product discussion is preserved, refined rather than replaced.
6. **Definition of "change" = per-module content-fingerprint diff (existing mechanism), not raw
   response diff.** (§7)
7. **A debounced, dismissible "Tally connection lost — Sync Now?" suggestion**, sourced from the
   Connector's existing `TallyConnectionManager` state transition — framed honestly as a connection
   event, not a claimed "session ended" detection. (§10)
8. **Fix the two found company-isolation gaps (Connector singleton sync-progress state; Android's dead
   `clearActiveIfCompanyChanged()`) as part of, or immediately before, the scheduler implementation
   task** — not deferred indefinitely. (§8)
9. **New scheduler state persists in the Connector's existing SQLite database as one small new
   company-scoped table**, not Room, not DataStore, not a new table shape mirroring `sync_runs`.
   (§14)

### CLOSED (investigated to a conclusion, no product decision needed — the answer is no)

10. ~~**Whether a genuinely cheap, pre-extraction Tally change-detection signal (company-level
    `ALTERID`/similar) is real and safe against VENTURE's supported Tally versions.**~~ **Resolved
    2026-08-23 (Phase 47), see §3.1: NOT PROVEN SAFE / NOT PROVEN BENEFICIAL. No company-level
    marker exists or is reachable through any Tally mechanism found (live-tested, and searched
    against authoritative Tally documentation and independent third-party integrations); the closest
    real alternative (a lightweight per-record `ALTERID`-only fetch) is safe-by-construction but,
    measured live against 949 real ledgers, does not reduce Tally-side processing time — only bytes
    and downstream Connector work. Not implemented. Strategy C (already implemented) never depended
    on this answer, exactly as this document anticipated.**

### OPEN (requires explicit ChatGPT/Product-Owner decision before any implementation work depends on it)
11. **Why the voucher sync's `'already_current'` fast path was deliberately removed** (§2.2) — the
    reasoning isn't recorded in-source; worth recovering from the team before considering any future
    cheap-skip mechanism for vouchers specifically, in case it repeats a known-bad idea.
12. **The exact RECOMMENDED timing values in §6 (5/15/30/60 minutes) are a principled starting point,
    not a measured optimum** — whether they need tuning (and against what real-world telemetry) is a
    product call for after initial rollout, not something this research can resolve from static code
    reading alone.
13. **Whether/how prominently to surface "Checking regularly" vs "Checking occasionally" in the UI at
    all** (§11) — a minimal, defensible default is proposed, but the exact UX (or whether to show
    anything beyond the existing last-synced timestamp) is a product call.

## 18. Rejected alternatives and why

- **Strategy A (flat fixed interval)** — rejected as the primary strategy: wastes Tally/Connector
  capacity during genuinely idle periods (the entire premise of this task), with no offsetting
  benefit over C during active periods (§4, §12).
- **Strategy B (flat 15/60, no staging)** — not rejected outright, but superseded by C: the 4x cliff
  at the 15-minute boundary is avoidable at essentially no extra complexity by staging the backoff, so
  there is no reason to accept B's abruptness once C is on the table (§4).
- **Strategy D/E as the *primary* near-term strategy** — rejected for this task specifically because
  their prerequisite (a cheap pre-extraction signal) is unproven in this codebase and would require
  external Tally-API validation this task is explicitly scoped not to perform (§3). Kept as a
  documented future upgrade path, not discarded.
- **Per-client (Desktop and Android each independently) scheduling** — rejected: triples Tally load
  for zero freshness benefit and invents a cross-client coordination problem that a single
  Connector-owned scheduler avoids entirely by construction (§14).
- **Session-end detection via inference/heuristics/generative prediction** — rejected per the
  governing task's own explicit "no generative AI, no ML, no opaque prediction" constraint and its
  "if there's no reliable way to know, do not invent one" instruction (§10). The existing, real,
  non-invented `TallyConnectionManager` state transition is used instead, honestly framed.
- **Reusing `sync_runs` itself for scheduler state** (instead of a new small table) — rejected: it's
  an append-only history log with a different lifecycle than mutable "current adaptive stage" state;
  overloading it would blur a distinction the codebase already keeps clean elsewhere (`voucher_snapshots`
  vs `voucher_active_snapshots`) (§14).

## 19. Recommended next implementation task

Not started in this session (research/architecture only, per the governing task's explicit stop
condition). The recommended next task, once the OPEN items in §17 have Product-Owner sign-off (at
minimum items 10 and 12 — 11 and 13 are lower-stakes and could be resolved during implementation):

1. Fix the two company-isolation prerequisite gaps (§8, LOCKED item 4 / RECOMMENDED item 8) as
   their own small, independently-testable change.
2. Implement the new scheduler-state table and Connector-side staged-backoff trigger (§6, §14),
   reusing the existing sync-execution path exactly as §16 describes — no new sync engine.
3. Expose the minimal read-only `schedulerState` field Desktop/Android need (§14, §11) and wire the
   two-word freshness UX into Desktop first (it already has the most mature freshness philosophy to
   extend, per Phase 41/42), then Android.
4. Leave the OPEN cheap-signal investigation (§17 item 10) as a genuinely separate, later spike —
   explicitly not bundled into this implementation task, so a negative or inconclusive result there
   never blocks shipping the proven, time-based Strategy C design.
