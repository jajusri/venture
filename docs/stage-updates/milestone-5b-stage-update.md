# Milestone 5B — Stage Update

**Milestone:** Stock Items Synchronization  
**Last updated:** 2026-07-24  
**Packages:** `@budcom/connector` v0.3.1 · `@budcom/desktop` v0.4.3  
**Git commit (5B complete):** `f2a6b3a` on `main`  
**Working tree:** Reliability Step 1 implemented (uncommitted)  
**Production readiness:** Controlled pilot — **not** unrestricted production

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-23 | Milestone 5B implementation, remediation gate, loopback live validation (A–E), commit `f2a6b3a` |
| 2026-07-24 | Stage-update created; accepted reliability controls registered (Step 0 — documentation only) |
| 2026-07-24 | **Reliability Step 1:** atomic data + checkpoint transactions for ledger and stock-item sync batches (uncommitted) |
| 2026-07-24 | **Step 1 concurrency-safety verification:** ambient-depth hazard fixed with mutex + ALS; deterministic concurrency tests added (uncommitted) |

---

## 1. Current status

**Completed on `main` — Controlled Pilot Ready**

Milestone 5B Stock Items Sync is complete. The connector remains read-only toward Tally. No next product milestone is approved until an explicit milestone specification exists.

`docs/architecture/milestones.md` uses separate product-level numbering and is **not** connector milestone truth.

---

## 2. Implementation status

| Area | Status |
|------|--------|
| ERP-neutral stock-item domain / validation / identity | Complete |
| Tally `List of Stock Items` read path (`STOCK_ITEMS`) | Complete |
| SQLite `stock_items` + schema v3 / migration 003 | Complete |
| Sync engine (full / incremental / cancel / progress) | Complete |
| `sync_runs.resource_kind` isolation (`ledgers` \| `stock-items`) | Complete |
| Connector HTTP APIs under `/stock-items` and `/sync/stock-items/*` | Complete |
| Desktop Stock Items page + IPC | Complete |
| Atomic data + checkpoint batch commit (ledger + stock-item) | **Complete (Reliability Step 1)** |
| Deletion reconciliation | Intentionally disabled |
| First-class stock groups / units / inventory metadata | Deferred (out of 5B) |
| TD-006 full upsert resume | Still partial / deferred |
| Unrestricted production approval | Not approved |

---

## 3. Scope completed

- ERP-neutral stock-item contracts (`StockItemSummary`, details, statistics, sync progress/result, `dataQuality`)
- Extraction enrichment for GUID / AlterID / alias / part number / inactive when present in Tally export
- Content-fingerprint upsert into company-scoped SQLite store
- Independent sync concurrency per `resource_kind`
- Connector endpoints: list/detail, sync, cancel, status, statistics, runs, clear-cache, integrity-check, backup
- Desktop navigation, search, pagination, sync controls, non-blocking progress
- Validation: duplicates, required fields, incomplete unit warnings, invalid balances, whitespace normalization (no silent accounting repair)
- Identity precedence: `guid:` → `alter:` → `name:` slug, with collision detection

---

## 4. Architecture components and files

### Architecture path

```
Desktop (Stock Items page)
  └─ IPC → StockItemService → Connector HTTP
        └─ /stock-items, /sync/stock-items/*
              └─ StockItemSyncService
                    ├─ ErpReadPort.readStockItems()
                    ├─ mapNormalizedStockItemToDomain()
                    ├─ validateStockItemCollection()
                    └─ SqliteStockItemRepository + SyncRunRepository (resource_kind = stock-items)
```

### Representative components

| Layer | Paths |
|-------|-------|
| Domain | `connector/.../src/erp/stock-item/stock-item-domain.ts`, mapper, validation, fingerprint |
| Identity | `connector/.../src/extraction/core/stock-item-identity.ts` |
| Tally adapter | `tally/adapter/tally-read-adapter.ts` (`readStockItems`, AbortSignal) |
| Sync | `services/stock-item/stock-item-sync.service.ts` |
| Storage | `storage/sqlite/schema.ts` (v3), `sqlite-stock-item-repository.ts`, `sync-run-repository.ts` |
| API | `api/routes/stock-items.ts`, DI registration, read-only middleware allowlist |
| Desktop | `stock-item-service.ts`, IPC allowlist, preload, renderer Stock Items page |
| Milestone doc | `docs/milestones/milestone-5b-stock-items-sync.md` |

ERP-neutral ports remain independent from Tally-specific implementation. Tally write capability is not present.

---

## 5. Tests and operational validation evidence

### Automated (remediation gate, 2026-07-23)

| Gate | Result |
|------|--------|
| Connector lint / build | PASS |
| Connector tests | **323/323 PASS** |
| Desktop lint / build | PASS |
| Desktop tests | **75/75 PASS** |
| Backup/restore + stock items | PASS |
| API negative paths | PASS (17) |
| Schema migration v3 | PASS |
| Resource-kind run isolation | PASS |

Evidence: `docs/diagnostics/MILESTONE_5B_ARCHITECT_VERIFICATION_REPORT.md`

### Loopback live Tally

Evidence: `docs/diagnostics/m5b-live-validation.json`

| Scenario | Status |
|----------|--------|
| A Normal sync | PASS — 1,502 items |
| B Identity coverage | PASS |
| C Field coverage | PASS |
| D Repeat sync | PASS — idempotent skip |
| E Cancellation | PASS |
| F Operator Tally stop/start (architect gate) | Initially blocked; later operational evidence recorded separately |

### Operational / fault gate

Evidence: `docs/diagnostics/m5b-operational-validation.json` (baseline `f2a6b3a`)

| Observation | Result |
|-------------|--------|
| Mid-sync Tally-down fault (`5a1-scenario-12-during-sync`) | **`pass: false`** — sync observed as `completed`; Tally-down not detected as expected |
| Recovery / retry scenarios | Recorded with stable aggregate counts |
| Scenario F unavailable-only | **PASS** (attempt 4) |
| Error-body privacy checks | PASS (no issues listed) |

### Benchmarks

- `docs/diagnostics/m5b-stock-item-sync-benchmark.json` (synthetic SQLite)

---

## 6. Commit reference

| Field | Value |
|-------|-------|
| Completion commit | `f2a6b3a` |
| Full SHA | `f2a6b3ae0543f0af690d55ff64777fe879ae7725` |
| Branch | `main` |
| Subject | `feat(connector,desktop): complete milestone 5B stock item synchronization` |

---

## 7. Known defects

| ID / topic | Severity | Notes |
|------------|----------|-------|
| Mid-sync Tally-down fault scenario | Medium | Operational validation `pass: false` for scenario 12 — **unchanged by Reliability Step 1**; not claimed fixed |
| Name-fallback identity rename risk | Medium | Live export: 0% GUID/AlterID; 100% name-fallback for measured company |
| Per-item `dataQuality: incomplete` | Accepted limitation | Default export omits `BASEUNITS` (100% incomplete in measured live set) |
| Extraction completeness at port | Low / partial | Result field exists; port-level partial extraction detection deferred |
| TD-006 full resume | Medium | Interrupted sync still re-processes extracted set; resume from `lastProcessedId` incomplete |

---

## 8. Technical debt

| ID | Status | Notes |
|----|--------|-------|
| TD-001 | Open | Parent encoding normalization |
| TD-004 | Open | Tally host/port not forwarded to connector spawn |
| TD-006 | **Partial** | Durable interrupted sync resume; full upsert resume from `lastProcessedId` incomplete |
| TD-009 | Open | Authenticated LAN access for connector API (does not block loopback pilot) |
| TD-005 / TD-007 / TD-008 | Resolved (prior milestones) | JSON repo replaced; cancel with limitation; loopback bind default |

---

## 9. Security and privacy observations

- Connector remains **read-only toward Tally** (EXPORT-only egress, typed gateway, capability registry).
- Desktop IPC allowlist, context isolation, and CSP from Milestone 4D remain in force.
- Diagnostic / operational evidence uses aggregate and redacted company labels where recorded.
- **Privacy gap:** desktop diagnostic `sessionSummary` may include identifiable company names — must be removed under accepted reliability control #4 (not marked complete).
- Non-loopback LAN mode remains unauthenticated (TD-009).
- Code signing / EV certificate / authenticated updater are release decisions and must not block MVP validation.

---

## 10. Risks and limitations

| Risk / limitation | Level |
|-------------------|-------|
| Incomplete TD-006 resume after interrupt | Medium |
| Name-only identity rename / collision risk until richer export validated | Medium |
| Deletion reconciliation disabled | Accepted for 5B |
| Opening qty/rate/value not decomposed | Accepted for 5B |
| Formal installer / signing / AV program absent | Medium (release track) |
| Stale progress and production-gate documents | Process — see §14 |
| Operational scenario 12 (mid-sync Tally down) unresolved | Medium — separate from batch atomicity |
| Sync batch upsert adapters must complete writes synchronously inside the ambient transaction | Low — enforced by current SQLite repositories |

---

## 11. Production-readiness level

**Controlled pilot ready — not unrestricted production**

| Gate | Status |
|------|--------|
| Architecture reviewed (5B path) | ☑ |
| Unit / integration tests passing | ☑ (**338/338** connector after Reliability Step 1) |
| Loopback live Tally A–E | ☑ |
| Atomic data + checkpoint failure injection | ☑ (12/12 new tests) |
| Operational fault scenario 12 | ☐ (failed / unresolved — not claimed fixed by Step 1) |
| Security formal review (5B-specific) | ☐ |
| Installer / code signing | ☐ |
| Approved for unrestricted production | ☐ |
| Next product milestone specified | ☐ |

**Readiness label:** Controlled Pilot  
**Not approved for:** unrestricted production deployment, tagging as production release, or inventing next-milestone scope.

---

## 11a. Reliability Step 1 — Atomic data plus checkpoint

**Status:** Complete for existing ledger and stock-item batch paths (uncommitted)  
**Scope:** Reliability defect correction under completed 5A-P/5B sync — not a new product feature

### Transaction ownership and boundary

| Concern | Owner |
|---------|-------|
| Shared SQLite file / connection | `SqliteDatabase` via `SqliteStorageService` bundle |
| Ambient transaction API | `SqliteDatabase.runInTransaction` / `SqliteStorageService.runInTransaction` |
| Domain upserts | `SqliteLedgerRepository` / `SqliteStockItemRepository` (join ambient TX or own short TX) |
| Checkpoint counters | `SyncRunRepository.updateRun` (participates in ambient TX) |
| Sync orchestration | `LedgerSyncServiceImpl` / `StockItemSyncServiceImpl` wrap each batch |

**Atomic batch boundary (inside TX):** domain `upsertMany` (when non-empty) + `sync_runs` checkpoint counters (`processed` / `inserted` / `updated` / `skipped` / `lastProcessedId`).

**Deliberately outside the batch TX (tested / documented):**

- `createRun` (run creation)
- post-extraction `totalExpected` update
- cancel request (`cancelling`) status update
- terminal `completed` / `cancelled` / `failed` / abandoned→`interrupted` updates

Invariant: checkpoint counters never advance without durable associated domain rows for that batch; domain rows from a batch never remain committed if the corresponding checkpoint update fails.

### Files changed

| Path | Change |
|------|--------|
| `connector/.../src/storage/sqlite/sqlite-database.ts` | Ambient TX: async mutex + ALS + sync BEGIN/COMMIT body + `runInTransactionSync` |
| `connector/.../src/storage/sqlite/storage-service.ts` | `runInTransaction` passthrough (async) |
| `connector/.../src/storage/sqlite/sqlite-ledger-repository.ts` | `upsertMany` joins ALS ambient TX or uses sync TX |
| `connector/.../src/storage/sqlite/sqlite-stock-item-repository.ts` | `upsertMany` joins ALS ambient TX or uses sync TX |
| `connector/.../src/services/ledger/ledger-sync.service.ts` | Wrap batch upsert + checkpoint (`await runInTransaction`) |
| `connector/.../src/services/stock-item/stock-item-sync.service.ts` | Wrap batch upsert + checkpoint (`await runInTransaction`) |
| `connector/.../test/integration/sync-batch-atomicity.test.ts` | **Added** — 12 failure-injection tests |
| `connector/.../test/integration/transaction-concurrency-safety.test.ts` | **Added** — 6 concurrency-safety tests |
| `docs/stage-updates/milestone-5b-stage-update.md` | Step 1 + concurrency verification record |

### Tests added (failure-injection evidence)

`test/integration/sync-batch-atomicity.test.ts` — **12/12 PASS**

1. Ledger success: data + checkpoint commit together  
2. Stock-item success: data + checkpoint commit together  
3. Ledger rollback when checkpoint update fails  
4. Stock-item rollback when checkpoint update fails  
5. Ledger checkpoint does not advance when upsert fails  
6. Stock-item checkpoint does not advance when upsert fails  
7. Retry after injected failure does not create duplicates  
8. Existing data remains valid after rollback  
9. Company isolation intact  
10. `resource_kind` isolation intact  
11. Cancellation at extraction boundary does not falsely advance checkpoint  
12. Terminal failed state recorded accurately  

### Validation commands and results (2026-07-24)

| Command | Result |
|---------|--------|
| `npm run lint` (eslint + `tsc --noEmit`) | **PASS** |
| `npm run build` | **PASS** |
| `npx vitest run` | **344/344 PASS** (67 files) after concurrency fix |
| Architecture `module-boundaries.test.ts` | **12/12 PASS** |
| Focused ledger/stock/storage/migration/concurrency suites | **PASS** |
| Desktop checks | **Not run** — no shared contracts or desktop code changed |

### Operational scenario 12

**Not re-tested; remains unchanged.** Atomic local batch commit does not claim to fix mid-sync Tally unavailability detection (`m5b-operational-validation.json` scenario 12 still `pass: false`).

### Control #2 wording after Step 1

**Atomic data plus checkpoint commit: complete for existing ledger and stock-item batch paths; broader crash-resume control remains partial under TD-006.**

Accepted control #2 is **not** marked fully complete.

### Step 1 Concurrency-Safety Verification

**Date:** 2026-07-24  
**TD-006 status:** unchanged (still partial)  
**Scenario 12:** unchanged / not claimed fixed

#### Identified risk

Ambient `transactionDepth` on a shared `SqliteDatabase` instance could let an unrelated async caller observe another job’s active transaction and join it (especially if a callback yielded between `BEGIN` and `COMMIT`). SQLite `BEGIN IMMEDIATE` alone does not protect JavaScript ambient-depth state.

#### Defect found?

**Yes — structural ambient-depth hazard** relative to concurrent ledger/stock-item jobs on one connection. Pre-fix Step 1 call sites were accidentally sync-safe, but the depth model was not safe under async interleaving. Additionally, `node:sqlite` `DatabaseSync` rejects awaiting between `BEGIN` and `COMMIT` (`ERR_INVALID_STATE`).

#### Implementation mechanism (correction)

| Mechanism | Role |
|-----------|------|
| Async mutex (`transactionMutex`) | Serializes complete outermost `runInTransaction` callbacks on the connection |
| `AsyncLocalStorage` | Scopes nested join / `isInTransaction()` to the owning async context only |
| Synchronous BEGIN/fn/COMMIT body | Satisfies DatabaseSync; no await inside the SQL transaction |
| `runInTransactionSync` | Standalone repository/migration upserts complete synchronously; refuses to start while async mutex is held |
| `setConcurrencyTestHook` | Test-only pause after mutex acquire / before BEGIN (DatabaseSync cannot pause mid-SQL-TX) |

Unrelated async callers never join one another’s transaction; nested repository writes within the owning callback still join via ALS.

#### Files changed (concurrency verification)

| Path | Change |
|------|--------|
| `sqlite-database.ts` | Mutex + ALS + sync TX body + sync standalone path + test hook |
| `storage-service.ts` | Async `runInTransaction` passthrough |
| `sqlite-ledger-repository.ts` / `sqlite-stock-item-repository.ts` | ALS join or `runInTransactionSync` |
| `ledger-sync.service.ts` / `stock-item-sync.service.ts` | `await runInTransaction` with sync batch body |
| `test/integration/transaction-concurrency-safety.test.ts` | **Added** — 6 deterministic concurrency tests |

#### Deterministic tests added

`transaction-concurrency-safety.test.ts` — **6/6 PASS**

1. Unrelated TX does not join while A holds the connection slot (barrier after mutex / before BEGIN; two separate `BEGIN`s)  
2. Rollback isolation (A fails; B’s committed data survives)  
3. Commit isolation (A cannot make B’s data durable; independent connection visibility)  
4. Nested same-context join does not open a second `BEGIN`  
5. Same `(companyId, resource_kind)` active-run exclusion still enforced  
6. Concurrent ledger + stock-item sync remain logically independent under write serialization  

#### Exact validation results (post-fix)

| Command | Result |
|---------|--------|
| `npm run lint` | **PASS** |
| `npm run build` | **PASS** |
| `npx vitest run test/integration/transaction-concurrency-safety.test.ts` | **6/6 PASS** |
| `npx vitest run test/integration/sync-batch-atomicity.test.ts` | **12/12 PASS** |
| `npx vitest run` | **344/344 PASS** (67 files) |
| Architecture boundaries | **12/12 PASS** |

#### Remaining risks

- `createRun` / terminal `updateRun` remain outside the batch mutex by design; they must not be called while another job holds an open SQL transaction with a yielding callback (production batch bodies are sync).  
- `runInTransactionSync` is safe at startup migration; it throws if invoked while the async mutex is held.  
- TD-006 full resume still incomplete.  
- Scenario 12 still unresolved.

#### Commit recommendation

**SAFE TO COMMIT** for Reliability Step 1 (atomicity + concurrency-safety fix), subject to operator commit authorization. No commit performed in this verification.

---

## 12. Accepted Reliability Controls Registration

**Authority:** `.cursor/rules/budcom-tally-connector-reliability.mdc` · `docs/architecture/accepted-reliability-hardening.md`  
**Registration date:** 2026-07-24  
**Registration step:** Step 0 — documentation only (no application code changes)

These principles do **not** expand MVP-1. Implementation occurs only when required by the current approved milestone, a critical defect, security/reliability requirement, or production-readiness work.

| # | Control | Status | Notes |
|---|---------|--------|-------|
| 1 | Capability-based Tally pre-flight | **PARTIAL** | Configurable probing and typed discovery/session outcomes exist; no unified typed pre-flight capability matrix; must not assume `tally.exe`, fixed port, one release, or one XML schema |
| 2 | Atomic SQLite data plus checkpoint and crash recovery | **PARTIAL** | **Atomic data plus checkpoint commit: complete for existing ledger and stock-item batch paths; broader crash-resume control remains partial under TD-006.** |
| 3 | Strict XML boundary validation | **PARTIAL** | Egress EXPORT validation strong; inbound/offline depth, encoding ambiguity, XXE/entity, and size/nesting limits incomplete |
| 4 | Privacy-safe diagnostics | **PARTIAL, identifiable company-name exposure must be removed** | Bundle exclusions exist; not a hard allowlist with absence proofs; `sessionSummary` may carry company names |
| 5 | Electron and Windows production hardening | **PARTIAL, release signing/updater/AV work deferred** | Core isolation / CSP / IPC allowlist exist (4A–4D); signing, authenticated updates, and antivirus compatibility are deferred to an explicit production-readiness milestone |

### Current reliability defects (registered)

1. ~~Ledger and stock-item data writes commit separately from `sync_runs` checkpoint/status updates.~~ **Resolved for sync batches by Reliability Step 1** (terminal/create/cancel updates remain outside batch TX by design).
2. TD-006 full resume behavior remains incomplete.
3. Diagnostic `sessionSummary` may contain identifiable company names.
4. Existing progress and production-gate documents may be stale relative to Milestones 4x through 5B.
5. Operational scenario 12 (mid-sync Tally unavailability) remains unresolved and is treated as a separate defect from batch atomicity.

### Approved implementation order

1. Stage-update and reliability registration ← **Step 0 complete**
2. Atomic data plus checkpoint transactions for existing ledger and stock-item sync ← **Reliability Step 1 complete (this update)**
3. TD-006 resume completion ← **next code step when authorized**
4. Diagnostic allowlist and absence tests
5. Unified typed capability pre-flight
6. Inbound/offline XML safety limits
7. Production release signing, authenticated updates, and antivirus compatibility

**Do not proceed to TD-006 full resume (order item 3) without explicit authorization.**

---

## 13. Unresolved observations

- Operational mid-sync Tally-down scenario remains failed (`m5b-operational-validation.json`).
- Live identity limitation: default `List of Stock Items` export measured at 0% GUID/AlterID/BASEUNITS for the pilot company.
- Whether a richer safe Tally collection export path can be validated without write capability or schema assumptions.
- Exact next product milestone remains unspecified (no approved 5C/5D specification in repository).
- Production-gate and project-progress documents have not yet been narrowly corrected for 4x–5B (see §14).

---

## 14. Stale status documents (reviewed, not rewritten)

Broad rewrites were intentionally deferred. Narrow later corrections are recommended.

| Document | Stale / contradictory statement | Recommended later correction |
|----------|----------------------------------|------------------------------|
| `docs/PROJECT_PROGRESS.md` | Claims current milestone is **Milestone 2**; versions show connector `0.2.0`; states Milestone 2 awaiting review/commit | Update current milestone to **5B complete (`f2a6b3a`)**, refresh package versions, and list completed connector milestones 3B–5B |
| `docs/stage-updates/production-gate-status.md` | Production gates stop at **3B–3D**; “Recommended next step” still says live-validate 3D then schedule TD-001 for 5A | Append gate sections for 4A–4D, 5A, 5A-P, and 5B; point next-step to authorized reliability order item 3 (TD-006) or an explicit future milestone spec |
| `docs/architecture/milestones.md` | Product MVP M0–M5 plan can be misread as connector stage truth | Add a short clarifying note that connector stage truth lives in `docs/milestones/milestone-*` + stage-updates (do not renumber product plan) |
| `docs/diagnostics/MILESTONE_5B_ARCHITECT_VERIFICATION_REPORT.md` | Pre-commit sections say “Safe to Commit: NO” and baseline `271f36e`; later remediation gate and git history show 5B committed as `f2a6b3a` | Add a dated post-commit addendum referencing `f2a6b3a` without rewriting superseded audit narrative |
| Module / older stage-updates (3x–5A-P) | Individually valid for their milestones but not rolled into a current progress/gate summary | Prefer updating the two summary docs above rather than rewriting each historical stage-update |

---

## 15. Deferred work

| Item | Deferred to |
|------|-------------|
| TD-006 full resume completion | Reliability order item 3 (after Step 1) — **await authorization** |
| Diagnostic allowlist + company-name removal + absence tests | Reliability order item 4 |
| Unified typed capability pre-flight | Reliability order item 5 |
| Inbound/offline XML safety limits | Reliability order item 6 (before untrusted voucher/import scope) |
| Signing / authenticated updates / AV compatibility | Explicit production-readiness milestone |
| Stock groups / units as first-class masters | Future inventory-related milestone (when specified) |
| Opening qty/rate/value decomposition | Future inventory-related milestone (when specified) |
| Deletion reconciliation | Future approved sync policy |
| Operational scenario 12 remediation | Separate defect track — do not conflate with batch atomicity |
| Next product feature milestone (vouchers, inventory, etc.) | **Only after an explicit milestone specification exists** |

---

## 16. Explicit next-action gate

**STOP — await authorization.**

| Allowed now | Not allowed without new authorization |
|-------------|----------------------------------------|
| Use this stage-update as connector 5B + reliability Steps 0–1 truth | Implement TD-006 full resume (order item 3) |
| Narrow doc corrections listed in §14 when separately authorized | Create 5C/5D or other product milestone specifications |
| | Mark accepted control #2 fully complete |
| | Expand MVP-1 scope or redesign approved architecture |
| | Commit unless explicitly instructed |

**Next authorized code step (when approved):** order item 3 — TD-006 resume completion (explicitly deferred; not started).

---

## 17. Exit decision

| Decision | Status |
|----------|--------|
| Milestone 5B complete on `main` | **Yes** (`f2a6b3a`) |
| Controlled pilot use | **Yes** |
| Unrestricted production | **No** |
| Reliability Step 0 (registration) | **Complete** |
| Reliability Step 1 (atomic batch commit) | **Complete (uncommitted)** |
| TD-006 full resume / later reliability steps | **Blocked pending explicit authorization** |
| Next product milestone | **Unspecified** |
