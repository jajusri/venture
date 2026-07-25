# Milestone 5B — Stage Update

**Milestone:** Stock Items Synchronization  
**Last updated:** 2026-07-24  
**Packages:** `@budcom/connector` v0.3.1 · `@budcom/desktop` v0.4.3  
**Git commit (5B complete):** `f2a6b3a` on `main`  
**Working tree:** Reliability Step 3 + ledger extraction contract / GUID-first identity remediation (uncommitted)

**Production readiness:** Controlled pilot — **not** unrestricted production

---

## Update history

| Date | Summary |
|------|---------|
| 2026-07-23 | Milestone 5B implementation, remediation gate, loopback live validation (A–E), commit `f2a6b3a` |
| 2026-07-24 | Stage-update created; accepted reliability controls registered (Step 0 — documentation only) |
| 2026-07-24 | **Reliability Step 1:** atomic data + checkpoint transactions for ledger and stock-item sync batches (uncommitted) |
| 2026-07-24 | **Reliability Step 3:** privacy-safe diagnostic allowlist, company-name removal from export surfaces, absence tests (uncommitted) |
| 2026-07-24 | **Ledger extraction contract / GUID-first identity:** embedded FETCH, identity helper, quality gate, schema v5, controlled cache rebuild (uncommitted) |

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
| TD-006 full resume | **Resolved (Step 2)** | Safe restart-from-beginning with run lineage; positional resume intentionally unsupported |

---

## 8. Technical debt

| ID | Status | Notes |
|----|--------|-------|
| TD-001 | Open | Parent encoding normalization |
| TD-004 | Open | Tally host/port not forwarded to connector spawn |
| TD-006 | **Resolved (Step 2)** | Safe interrupted-sync restart with lineage; positional resume intentionally unsupported |
| TD-009 | Open | Authenticated LAN access for connector API (does not block loopback pilot) |
| TD-005 / TD-007 / TD-008 | Resolved (prior milestones) | JSON repo replaced; cancel with limitation; loopback bind default |

---

## 9. Security and privacy observations

- Connector remains **read-only toward Tally** (EXPORT-only egress, typed gateway, capability registry).
- Desktop IPC allowlist, context isolation, and CSP from Milestone 4D remain in force.
- Diagnostic export surfaces use an explicit allowlist DTO with sentinel absence tests (**Reliability Step 3**).
- On-disk desktop log files may still contain company names from normal operational logging — only user-triggered export/clipboard/summary paths are hardened.
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

## Reliability Step 2 — TD-006 safe restart-from-beginning (2026-07-24)

**Requirement addressed:** Control #2 — detect interrupted sync and retry safely without duplicate or omitted records.

**TD-006 definition adopted:** Safe restart-from-beginning with durable interrupted-run lineage. Exact positional/source-cursor resume is intentionally unsupported.

**Why positional resume was rejected:** Tally export order is not guaranteed between requests; no ordering contract or source snapshot identity exists; ledger checkpoint ids are name-slugs; inserts/renames or order changes could cause silent omission if records were skipped via `lastProcessedId`.

### Implementation status

Complete for ledger and stock-item sync paths (controlled pilot).

### Behavior

| Area | Behavior |
|------|----------|
| Abandoned run | `running` / `cancelling` → `interrupted` with `ABANDONED`; counters and `lastProcessedId` preserved; domain data unchanged |
| Retry run | New run with `predecessor_sync_run_id` → interrupted run; `retryCount = predecessor.retryCount + 1`; full Tally re-extraction; processing from index zero |
| Fresh run | No eligible interrupted predecessor → `predecessor_sync_run_id = null`, `retryCount = 0` |
| Predecessor selection | Most recent `interrupted` run for same `(company_id, resource_kind)` that is not superseded and has no newer sync run |
| `lastProcessedId` | Audit marker for last record in last atomically committed batch — **not** a source cursor |
| Positional skip | Removed from ledger and stock-item sync services |
| Startup recovery | `SqliteStorageService.start()` calls `recoverAllAbandonedRuns()` once (ledger + stock parity) |

### Schema / migration

- **Version 4:** nullable `predecessor_sync_run_id` on `sync_runs` + partial index for superseded lookup
- Existing rows default to `NULL` predecessor

### Files changed

| Path | Change |
|------|--------|
| `schema.ts` | `MIGRATION_004`, `STORAGE_SCHEMA_VERSION = 4` |
| `sqlite-database.ts` | Apply migration 4 |
| `sync-run-repository.ts` | `findRetryPredecessor`, predecessor validation, lineage on `createRun` |
| `ledger-domain.ts` | `predecessorSyncRunId` on run record |
| `ledger-sync.service.ts` | Retry lineage; remove positional skip; startup recovery moved to storage |
| `stock-item-sync.service.ts` | Retry lineage; remove positional skip |
| `storage-service.ts` | Central `recoverAllAbandonedRuns()` on startup |
| `test/integration/interrupted-sync-restart.test.ts` | **Added** — 12 TD-006 integration tests |
| `test/integration/schema-migration-v2.test.ts` | v3→v4 migration test |

### Tests added

`interrupted-sync-restart.test.ts` — **12/12 PASS** (ledger + stock retry lineage, full restart, ordering/insert safety, isolation, rollback+retry, predecessor selection, storage startup recovery)

### Validation commands and results (2026-07-24)

| Command | Result |
|---------|--------|
| `npm run lint` | **PASS** |
| `npm run build` | **PASS** |
| `npx vitest run test/integration/interrupted-sync-restart.test.ts` | **12/12 PASS** |
| `npx vitest run test/integration/schema-migration-v2.test.ts` | **4/4 PASS** |
| `npx vitest run test/integration/sync-batch-atomicity.test.ts` | **12/12 PASS** |
| `npx vitest run test/integration/transaction-concurrency-safety.test.ts` | **6/6 PASS** |
| `npx vitest run test/integration/sync-run-concurrency.test.ts` | **5/5 PASS** |
| `npx vitest run` | **357/357 PASS** (68 files) |
| Architecture `module-boundaries.test.ts` | **12/12 PASS** |
| Desktop checks | **Not run** — no shared contracts or desktop code changed |

### Known limitations (unchanged)

- Deletion reconciliation disabled — stale rows possible on ledger rename (name-slug identity)
- Scenario 12 (mid-sync Tally unavailability detection) **unchanged** — separate defect
- Exact positional, AlterID, or GUID watermark resume: intentionally unsupported unless a future proven Tally ordering, immutable cursor, or snapshot contract makes it safe
- Unrestricted production readiness **not** approved

### Control #2 wording after Step 2

**Atomic batch commit and controlled-pilot interrupted-run restart safety are complete for existing ledger and stock-item paths. More advanced positional/source-watermark resume is intentionally not part of the approved design.**

### TD-006 status

**TD-006 resolved for controlled-pilot durable restart safety through full restart with run lineage. Exact positional/source-cursor resume is intentionally unsupported unless a future proven Tally ordering, immutable cursor, or snapshot contract makes it safe.**

### Scenario 12

**Not modified; remains unresolved** (`m5b-operational-validation.json` scenario 12 still `pass: false`).

---

## Reliability Step 3 — Privacy-safe diagnostic allowlist (2026-07-24)

**Requirement addressed:** Control #4 — diagnostic bundles and summaries must be constructed through an explicit allowlist and must not expose identifiable company, customer, accounting, tax, credential, path, or raw XML data.

**Previous exposure path:** Desktop `DiagnosticsService` built ad hoc JSON bundles and copied a `sessionSummary` string of the form `` `${sessionStatus} · ${companyName}` `` into IPC snapshots, clipboard summaries, UI (`#diag-session`), and exported bundles (`session.summary`). Recent lifecycle/error log lines could also carry company names, ledger names, GSTINs, XML snippets, tokens, and full Windows paths into exported bundles. Connector `/diagnostics/connection` and extraction diagnostics returned unsanitized `lastErrorMessage` free text.

### Implementation status

Complete for user-triggered diagnostic export surfaces on desktop and connector diagnostic API responses (controlled pilot). **Control #4 remains PARTIAL** because on-disk operational logs, normal dashboard/session APIs, and committed historical operational-validation evidence were intentionally not rewritten.

### Allowlist design

- **Build-only allowlist:** `SafeDiagnosticBundleV1` / `SafeDiagnosticLogEntry` / `SafeDiagnosticErrorEntry` / `SafeDiagnosticSession` (desktop) and `SafeConnectionDiagnostic` / `SafeExtractorDiagnostic` (connector) — fields are declared explicitly; internal session/domain objects are not passed to IPC or export code.
- **No post-hoc blacklist:** bundles are assembled from approved inputs only; log/error free text passes through bounded sanitizers before inclusion.
- **Company identity:** `sessionSummary` removed; replaced with `sessionStatus`, `selectedCompanyPresent`, and non-identifying `sessionDisplayLabel` (e.g. `ACTIVE · company selected`). Ephemeral per-bundle `correlationId` only — no stable cross-bundle customer identifier.
- **Errors:** `mapUnknownErrorToSafeDiagnostic` / `mapUnknownConnectorError` emit typed codes and generic `"An unexpected error occurred."` — no raw `error.message`, nested `cause`, headers, or response bodies.
- **Paths:** full paths replaced with safe log-file refs (`category`, optional basename, `available` boolean).
- **Size limits:** `maxBundleBytes` 256 KB, `maxStringLength` 500, `maxLogEntries` 25, `maxStackFrames` 20, `maxArrayItems` 50, `maxRecursionDepth` 4; safe truncation suffix `… [truncated]`.

### Allowed fields (desktop bundle v1)

`bundleVersion`, `generatedAt`, `correlationId`, `truncated`, `versions` (desktop/connector/electron/node), `runtime` (platform/osRelease/architecture/uptimeSeconds), `connector` (baseUrl, bindHost, networkExposure, processState, ownership, pid, health flags, tallyReachable), `session` (status, selectedCompanyPresent, displayLabel), redacted `configuration`, allowlisted `environment` (`BUDCOM_*`, `NODE_ENV`, `ELECTRON_*` only), sanitized `logs.recentLifecycleEvents` / `logs.recentErrors`, `logFile` ref, `privacyPolicy.allowlistVersion`.

### Prohibited-data handling

Pattern-based redaction for GSTIN, XML tags, Windows/Unix paths, phones, amounts, legal company names, ledger labels, vouchers, bearer tokens, session-company log lines; generic unknown-error mapping; bundle-level byte cap with log-entry count cap.

### Files changed

| Path | Change |
|------|--------|
| `apps/budcom_desktop/src/application/diagnostic-allowlist.ts` | **Added** — allowlist DTO builder, sanitizers, limits, sentinel constants |
| `apps/budcom_desktop/src/application/diagnostics-service.ts` | Route snapshot/export/summary through allowlist |
| `apps/budcom_desktop/src/application/types.ts` | Replace `sessionSummary` / `logFilePath` with safe fields |
| `apps/budcom_desktop/src/renderer/scripts/app.ts` | Render `sessionDisplayLabel` |
| `apps/budcom_desktop/test/unit/diagnostic-privacy.test.ts` | **Added** — 10 serialized-output absence tests |
| `apps/budcom_desktop/test/unit/milestone-4d.test.ts` | Expect `privacyPolicy.allowlistVersion` |
| `apps/budcom_desktop/test/helpers/lifecycle-fixtures.ts` | Updated diagnostics fixture |
| `connector/budcom_connector/src/diagnostics/diagnostic-allowlist.ts` | **Added** — connector sanitizers + safe DTOs |
| `connector/budcom_connector/src/api/routes/diagnostics.ts` | Sanitize connection diagnostics |
| `connector/budcom_connector/src/api/routes/master-data.ts` | Sanitize extraction diagnostics |
| `connector/budcom_connector/test/unit/diagnostics/diagnostic-allowlist.test.ts` | **Added** — 4 absence tests |

### Tests and sentinel evidence

Sentinels: `JAJU SANITATIONS PRIVATE LIMITED`, `Sensitive Debtor Ledger`, GSTIN `29AABCU9603R1ZM`, voucher `VCH-2026-004821`, phone, email, amount, raw XML, `sk-live-diagnostic-leak-test-token`, Windows path with user/company segments, nested error cause with response body/headers.

Desktop `diagnostic-privacy.test.ts` inspects **complete serialized** snapshot labels, exported bundle JSON, clipboard summary, error mapping, truncation, determinism, and non-mutation of source logs.

### Validation commands and results (2026-07-24)

| Command | Result |
|---------|--------|
| Desktop `npm run lint` | **PASS** |
| Desktop `npm test` | **85/85 PASS** (20 files) |
| Desktop `npm run build` | **PASS** |
| Desktop `test/unit/diagnostic-privacy.test.ts` | **10/10 PASS** |
| Connector `npm run lint` | **PASS** |
| Connector `npm run build` | **PASS** |
| Connector `npx vitest run` | **361/361 PASS** (69 files) |
| Connector `test/unit/diagnostics/diagnostic-allowlist.test.ts` | **4/4 PASS** |
| Architecture `module-boundaries.test.ts` | **12/12 PASS** (unchanged) |

### Known limitations (unchanged / remaining)

- On-disk desktop log files and normal dashboard/company-selection UI may still show company names — only diagnostic export/clipboard/summary paths are allowlisted.
- No unified connector diagnostic **bundle** exporter; only connection/extraction diagnostic API fields are sanitized.
- Committed `docs/diagnostics/m5b-operational-validation.json` not modified (no confirmed privacy defect in historical evidence).
- Scenario 12, deletion reconciliation, pre-flight, XML hardening, signing, updater, LAN auth — **not started**.
- Unrestricted production readiness **not** approved.

### Control #4 wording after Step 3

**Export and summary diagnostic surfaces on desktop and connector diagnostic APIs use an explicit allowlist with serialized absence tests. Control #4 remains PARTIAL until on-disk log redaction, unified connector bundle export, and broader operational-log privacy are addressed in a future approved step.**

### Scenario 12

**Not modified; remains unresolved** (`m5b-operational-validation.json` scenario 12 still `pass: false`).

---

## Reliability Step 3 — Privacy-safe diagnostic allowlist (2026-07-24)

**Requirement addressed:** Control #4 — diagnostic bundles and summaries must be constructed through an explicit allowlist and must not expose identifiable company, customer, accounting, tax, credential, path, or raw XML data.

**Previous exposure path:** Desktop `DiagnosticsService` built ad hoc JSON bundles and copied a `sessionSummary` string of the form `` `${sessionStatus} · ${companyName}` `` into IPC snapshots, clipboard summaries, UI (`#diag-session`), and exported bundles (`session.summary`). Recent lifecycle/error log lines could also carry company names, ledger names, GSTINs, XML snippets, tokens, and full Windows paths into exported bundles. Connector `/diagnostics/connection` and extraction diagnostics returned unsanitized `lastErrorMessage` free text.

### Implementation status

Complete for user-triggered diagnostic export surfaces on desktop and connector diagnostic API responses (controlled pilot). **Control #4 remains PARTIAL** because on-disk operational logs, normal dashboard/session APIs, and committed historical operational-validation evidence were intentionally not rewritten.

### Allowlist design

- **Build-only allowlist:** `SafeDiagnosticBundleV1` / `SafeDiagnosticLogEntry` / `SafeDiagnosticErrorEntry` / `SafeDiagnosticSession` (desktop) and `SafeConnectionDiagnostic` / `SafeExtractorDiagnostic` (connector) — fields are declared explicitly; internal session/domain objects are not passed to IPC or export code.
- **No post-hoc blacklist:** bundles are assembled from approved inputs only; log/error free text passes through bounded sanitizers before inclusion.
- **Company identity:** `sessionSummary` removed; replaced with `sessionStatus`, `selectedCompanyPresent`, and non-identifying `sessionDisplayLabel` (e.g. `ACTIVE · company selected`). Ephemeral per-bundle `correlationId` only — no stable cross-bundle customer identifier.
- **Errors:** `mapUnknownErrorToSafeDiagnostic` / `mapUnknownConnectorError` emit typed codes and generic `"An unexpected error occurred."` — no raw `error.message`, nested `cause`, headers, or response bodies.
- **Paths:** full paths replaced with safe log-file refs (`category`, optional basename, `available` boolean).
- **Size limits:** `maxBundleBytes` 256 KB, `maxStringLength` 500, `maxLogEntries` 25, `maxStackFrames` 20, `maxArrayItems` 50, `maxRecursionDepth` 4; safe truncation suffix `… [truncated]`.

### Allowed fields (desktop bundle v1)

`bundleVersion`, `generatedAt`, `correlationId`, `truncated`, `versions` (desktop/connector/electron/node), `runtime` (platform/osRelease/architecture/uptimeSeconds), `connector` (baseUrl, bindHost, networkExposure, processState, ownership, pid, health flags, tallyReachable), `session` (status, selectedCompanyPresent, displayLabel), redacted `configuration`, allowlisted `environment` (`BUDCOM_*`, `NODE_ENV`, `ELECTRON_*` only), sanitized `logs.recentLifecycleEvents` / `logs.recentErrors`, `logFile` ref, `privacyPolicy.allowlistVersion`.

### Prohibited-data handling

Pattern-based redaction for GSTIN, XML tags, Windows/Unix paths, phones, amounts, legal company names, ledger labels, vouchers, bearer tokens, session-company log lines; generic unknown-error mapping; bundle-level byte cap with log-entry count cap.

### Files changed

| Path | Change |
|------|--------|
| `apps/budcom_desktop/src/application/diagnostic-allowlist.ts` | **Added** — allowlist DTO builder, sanitizers, limits, sentinel constants |
| `apps/budcom_desktop/src/application/diagnostics-service.ts` | Route snapshot/export/summary through allowlist |
| `apps/budcom_desktop/src/application/types.ts` | Replace `sessionSummary` / `logFilePath` with safe fields |
| `apps/budcom_desktop/src/renderer/scripts/app.ts` | Render `sessionDisplayLabel` |
| `apps/budcom_desktop/test/unit/diagnostic-privacy.test.ts` | **Added** — 10 serialized-output absence tests |
| `apps/budcom_desktop/test/unit/milestone-4d.test.ts` | Expect `privacyPolicy.allowlistVersion` |
| `apps/budcom_desktop/test/helpers/lifecycle-fixtures.ts` | Updated diagnostics fixture |
| `connector/budcom_connector/src/diagnostics/diagnostic-allowlist.ts` | **Added** — connector sanitizers + safe DTOs |
| `connector/budcom_connector/src/api/routes/diagnostics.ts` | Sanitize connection diagnostics |
| `connector/budcom_connector/src/api/routes/master-data.ts` | Sanitize extraction diagnostics |
| `connector/budcom_connector/test/unit/diagnostics/diagnostic-allowlist.test.ts` | **Added** — 4 absence tests |

### Tests and sentinel evidence

Sentinels: `JAJU SANITATIONS PRIVATE LIMITED`, `Sensitive Debtor Ledger`, GSTIN `29AABCU9603R1ZM`, voucher `VCH-2026-004821`, phone, email, amount, raw XML, `sk-live-diagnostic-leak-test-token`, Windows path with user/company segments, nested error cause with response body/headers.

Desktop `diagnostic-privacy.test.ts` inspects **complete serialized** snapshot labels, exported bundle JSON, clipboard summary, error mapping, truncation, determinism, and non-mutation of source logs.

### Validation commands and results (2026-07-24)

| Command | Result |
|---------|--------|
| Desktop `npm run lint` | **PASS** |
| Desktop `npm test` | **85/85 PASS** (20 files) |
| Desktop `npm run build` | **PASS** |
| Desktop `test/unit/diagnostic-privacy.test.ts` | **10/10 PASS** |
| Connector `npm run lint` | **PASS** |
| Connector `npm run build` | **PASS** |
| Connector `npx vitest run` | **361/361 PASS** (69 files) |
| Connector `test/unit/diagnostics/diagnostic-allowlist.test.ts` | **4/4 PASS** |
| Architecture `module-boundaries.test.ts` | **12/12 PASS** (unchanged) |

### Known limitations (unchanged / remaining)

- On-disk desktop log files and normal dashboard/company-selection UI may still show company names — only diagnostic export/clipboard/summary paths are allowlisted.
- No unified connector diagnostic **bundle** exporter; only connection/extraction diagnostic API fields are sanitized.
- Committed `docs/diagnostics/m5b-operational-validation.json` not modified (no confirmed privacy defect in historical evidence).
- Scenario 12, deletion reconciliation, pre-flight, XML hardening, signing, updater, LAN auth — **not started**.
- Unrestricted production readiness **not** approved.

### Control #4 wording after Step 3

**Export and summary diagnostic surfaces on desktop and connector diagnostic APIs use an explicit allowlist with serialized absence tests. Control #4 remains PARTIAL until on-disk log redaction, unified connector bundle export, and broader operational-log privacy are addressed in a future approved step.**

---

## 12. Accepted Reliability Controls Registration

**Authority:** `.cursor/rules/budcom-tally-connector-reliability.mdc` · `docs/architecture/accepted-reliability-hardening.md`  
**Registration date:** 2026-07-24  
**Registration step:** Step 0 — documentation only (no application code changes)

These principles do **not** expand MVP-1. Implementation occurs only when required by the current approved milestone, a critical defect, security/reliability requirement, or production-readiness work.

| # | Control | Status | Notes |
|---|---------|--------|-------|
| 1 | Capability-based Tally pre-flight | **PARTIAL (Phase B — session/resolver fail-closed + cache)** | Phase B tests + narrow fixes: `COMPANY_DISCOVERY_UNAVAILABLE`, cache invalidation/coalescing; no orchestrator; no rich capability cache; see §24 |
| 2 | Atomic SQLite data plus checkpoint and crash recovery | **COMPLETE (controlled pilot)** | **Atomic batch commit and controlled-pilot interrupted-run restart safety are complete for existing ledger and stock-item paths. More advanced positional/source-watermark resume is intentionally not part of the approved design.** |
| 3 | Strict XML boundary validation | **PARTIAL (Phase B — migration gate + characterization tests)** | Egress EXPORT validation strong; inbound envelope asymmetry, parser limits, and subset migration policy remain open — see §20 |
| 4 | Privacy-safe diagnostics | **PARTIAL (export surfaces hardened — Step 3)** | Explicit allowlist DTO + serialized absence tests for desktop export/clipboard/summary and connector diagnostic APIs; on-disk operational logs and normal UI session APIs still may contain company names |
| 5 | Electron and Windows production hardening | **PARTIAL, release signing/updater/AV work deferred** | Core isolation / CSP / IPC allowlist exist (4A–4D); signing, authenticated updates, and antivirus compatibility are deferred to an explicit production-readiness milestone |

### Current reliability defects (registered)

1. ~~Ledger and stock-item data writes commit separately from `sync_runs` checkpoint/status updates.~~ **Resolved for sync batches by Reliability Step 1** (terminal/create/cancel updates remain outside batch TX by design).
2. ~~TD-006 full resume behavior remains incomplete.~~ **Resolved for controlled-pilot restart safety by Reliability Step 2** (full restart with lineage; positional resume rejected).
3. ~~Diagnostic `sessionSummary` may contain identifiable company names.~~ **Resolved for export/clipboard/summary surfaces by Reliability Step 3** (on-disk logs and normal UI remain unchanged).
4. Existing progress and production-gate documents may be stale relative to Milestones 4x through 5B.
5. ~~Operational scenario 12 (mid-sync Tally unavailability) remains unresolved and is treated as a separate defect from batch atomicity.~~ **Scenario 12 Phase B (2026-07-24): deterministic transport tests prove no production defect; original manual `pass: false` reinterpreted as harness/timing ambiguity — see §19.**

### Approved implementation order

1. Stage-update and reliability registration ← **Step 0 complete**
2. Atomic data plus checkpoint transactions for existing ledger and stock-item sync ← **Reliability Step 1 complete (this update)**
3. TD-006 resume completion ← **Reliability Step 2 complete (2026-07-24)**
4. Diagnostic allowlist and absence tests ← **Reliability Step 3 complete (2026-07-24)**
5. Unified typed capability pre-flight
6. Inbound/offline XML safety limits
7. Production release signing, authenticated updates, and antivirus compatibility

**Do not proceed to order item 5 (pre-flight) without explicit authorization.**

---

## 13. Unresolved observations

- ~~Operational mid-sync Tally-down scenario remains failed (`m5b-operational-validation.json`).~~ **Superseded for interpretation by deterministic Scenario 12A/12B evidence (`docs/diagnostics/m5b-scenario-12-deterministic-validation.json`); historical manual record preserved unchanged.**
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
| TD-006 full resume completion | **Complete** — Reliability Step 2 (controlled pilot) |
| Diagnostic allowlist + company-name removal + absence tests | **Complete (Step 3 — export surfaces)** |
| Unified typed capability pre-flight | Reliability order item 5 |
| Inbound/offline XML safety limits | Reliability order item 6 (before untrusted voucher/import scope) |
| Signing / authenticated updates / AV compatibility | Explicit production-readiness milestone |
| Stock groups / units as first-class masters | Future inventory-related milestone (when specified) |
| Opening qty/rate/value decomposition | Future inventory-related milestone (when specified) |
| Deletion reconciliation | Future approved sync policy |
| Operational scenario 12 remediation | **Phase B complete — CORRECT VALIDATION ONLY** (deterministic harness + tests; no transport code change) |
| Next product feature milestone (vouchers, inventory, etc.) | **Only after an explicit milestone specification exists** |

---

## 16. Explicit next-action gate

**STOP — await authorization for later reliability steps (order items 5–7).**

| Allowed now | Not allowed without new authorization |
|-------------|----------------------------------------|
| Use this stage-update as connector 5B + reliability Steps 0–3 truth | Unified capability pre-flight (order item 5) |
| Narrow doc corrections listed in §14 when separately authorized | Create 5C/5D or other product milestone specifications |
| | Expand MVP-1 scope or redesign approved architecture |
| | Mark unrestricted production readiness approved |

**Next authorized code step (when approved):** order item 5 — unified typed capability pre-flight.

---

## 17. Exit decision

| Decision | Status |
|----------|--------|
| Milestone 5B complete on `main` | **Yes** (`f2a6b3a`) |
| Controlled pilot use | **Yes** |
| Unrestricted production | **No** |
| Reliability Step 0 (registration) | **Complete** |
| Reliability Step 1 (atomic batch commit) | **Complete** |
| Reliability Step 2 (TD-006 restart lineage) | **Complete** |
| Reliability Step 3 (diagnostic allowlist) | **Complete (export surfaces — Control #4 partial)** |
| Ledger extraction contract / GUID-first identity (TD-011) | **Complete (controlled pilot — uncommitted)** |
| Later reliability steps (5–7) | **Blocked pending explicit authorization** |
| Next product milestone | **Unspecified** |

---

## 18. Ledger extraction contract and GUID-first identity (2026-07-24)

**Status:** Complete in working tree (uncommitted)

**Type:** Reliability / extraction-correctness defect fix — **not** a new product feature

**Evidence:** `docs/diagnostics/ledger-extraction-identity-evidence-tallyprime-3.0.1.md`

**Technical debt:** TD-011 resolved (controlled pilot)

### Summary

| Item | Detail |
|------|--------|
| Extraction | Embedded read-only FETCH on `List of Ledgers` (8 fields) |
| Identity | `guid:{normalised}` → `name:{slug}`; AlterID/MasterID excluded from identity |
| Quality | `complete` / `partial` / `invalid`; shallow export fails sync |
| Schema | v5 — `master_id`, `identity_source`, `data_quality`, `is_bill_wise_on` |
| Migration | Backup → validate → atomic replace; old cache preserved on failure |
| Response cap | `RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES` (1,048,576) — shared with stock-item rich FETCH; legacy 524,288 shallow cap superseded |
| Scaling limitation | Large-company responses unvalidated; may exceed 1 MiB |

### Validation

| Command | Result |
|---------|--------|
| `npm run lint` | PASS |
| `npm run build` | PASS |
| `npx vitest run` | **376/376 PASS** |
| Architecture boundaries | **12/12 PASS** |

### Remaining limitations

- Single TallyPrime 3.0.1 controlled evidence — not universal compatibility
- MasterID metadata only; backup/restore stability unproven
- Name fallback rename risk when GUID absent
- Deletion reconciliation still disabled
- Unrestricted production not approved

**Verdict:** **SAFE TO COMMIT** subject to operator authorization. No commit performed.

---

## 19. Scenario 12 Phase B — deterministic transport validation (2026-07-24)

**Status:** Complete

**Type:** Validation-only — **not** a production transport change

**Requirement addressed:** Reliability §1 transport completeness boundary; Phase A locked conclusions; Scenario 12 harness correction

**Supersedes for interpretation:** `docs/diagnostics/m5b-operational-validation.json` → `phase 5a1-scenario-12-during-sync` (`pass: false` preserved; timing could not prove fault before body completion)

**Evidence:** `docs/diagnostics/m5b-scenario-12-deterministic-validation.json`

### Locked boundary (confirmed)

Tally is required only until the complete HTTP response body is received and `extractWithRetry()` returns. Mapping, validation, migration, and SQLite persistence are local-only. Closing Tally or the loopback endpoint after body completion must not fail an otherwise valid sync. Periodic Tally liveness polling during local persistence is **rejected**.

### Harness correction

| Phase | Induced fault | Expected | Observed |
|-------|---------------|----------|----------|
| **12A** | Partial body / socket destroy before envelope complete | Run `failed`, processed=0, no domain/checkpoint mutation, safe retry | **PASS** — `failed`, 0 rows, 1 Tally request |
| **12B** | Complete valid XML, endpoint closed after body receipt | Local processing continues, run `completed`, no further Tally requests | **PASS** — `completed`, 300 rows, 1 Tally request |

Original manual expectation (“no completed run whenever sync UI is active”) was **invalid** — conflated UI activity with Tally transport dependency.

### Deterministic test matrix

| # | Scenario | Result |
|---|----------|--------|
| 1 | Reset before response headers | `failed`, 503, no mutation |
| 2 | Socket destroy mid-body | `failed`, no extraction, no mutation |
| 3 | Body-read stall / timeout | 504 typed timeout, no mutation |
| 4 | Structurally truncated XML (200) | Parser failure → `failed`, no mutation |
| 5 | Complete body, endpoint closes before mapping | `completed`, correct count |
| 6 | Complete body, endpoint closes during SQLite batching | `completed`, 300 rows, no re-poll |
| 7 | Connection refused before request | `failed`, no mutation |
| 8 | Abort during body reception | `cancelled`, processed=0 |
| 9 | Retry after transport failure | Second run `completed`, no duplicates; predecessor null (failed ≠ interrupted) |
| 10 | Privacy-safe failure evidence | Allowlist sanitization PASS on all failure paths |
| 11 | Well-formed XML with fewer entities | Ledgers: completes; stock: completes — documents current semantics |
| 12 | Empty valid collection | Ledgers: `partial` quality, sync completes; stock: completes with 0 rows |

### Content-Length investigation

| Case | Node fetch/undici behavior |
|------|------------------------------|
| Declared Content-Length > bytes received | Does **not** reject immediately; stalls until timeout → **504** |
| Chunked transfer ending prematurely | Transport rejects → **503** |
| Correct Content-Length + full body | Normal completion |

No manual Content-Length checker added — runtime does not silently accept truncated bodies as success.

### Production defect

**None proven.** `productionDefectFound: false`, `transportCodeChanged: false`.

### Files changed

| Area | Files |
|------|-------|
| Test helpers | `test/helpers/fault-injection-server.ts`, `test/helpers/sync-fault-helpers.ts` |
| Integration tests | `test/integration/tally-transport-fault.test.ts`, `test/integration/scenario-12-sync-fault.test.ts` |
| Validation harness | `scripts/scenario-12-validation.ts` |
| Evidence | `docs/diagnostics/m5b-scenario-12-deterministic-validation.json` |
| Stage-update | This section |

**No production `src/` changes.**

### Validation

| Command | Result |
|---------|--------|
| `npm run lint` | PASS (post Phase B) |
| `npm run build` | PASS |
| `npx vitest run test/integration/tally-transport-fault.test.ts test/integration/scenario-12-sync-fault.test.ts` | **25/25 PASS** |
| `npx tsx scripts/scenario-12-validation.ts` | **PASS** (12A + 12B) |
| `npx vitest run` (full connector) | **415/415 PASS** |
| Architecture boundaries | **12/12 PASS** |
| Desktop tests | Not run (no desktop changes) |

### Remaining limitations

- Semantic completeness (fewer entities / empty collection) documented as current behavior — no source-count contract enforced
- Content-Length mismatch classified via timeout, not immediate rejection
- Manual operational record in `m5b-operational-validation.json` unchanged; interpretation superseded here only
- Live Tally mid-sync timing still not primary proof path — deterministic loopback harness is authoritative

**Verdict:** **CORRECT VALIDATION ONLY** — safe to commit test/harness/docs; **not** `SAFE TO COMMIT TRANSPORT FIX` (no transport fix required).

---

## 20. Reliability Control #3 Phase B — inbound XML and migration safety (2026-07-24)

**Status:** Complete (committed `6ff0c0e`)

**Type:** Deterministic inbound characterization + **narrow migration-gate fix**

**Requirement addressed:** Reliability Control #3 — strict inbound XML boundary and collection-quality investigation

### Phase A conclusions (locked)

- Companies/groups have envelope assessment; ledgers/stock bypass it.
- Custom parser is fail-fast but accepts trailing junk, has no nesting/node limits, and does not classify Tally error envelopes.
- Unnamed entities are silently dropped without counters.
- Normal sync is upsert-only (no stale-row deletion).
- Identity migration uses atomic full-cache replacement.
- Semantic completeness cannot be inferred from prior counts, byte size, ordering, or ID continuity.

### Migration safety (proven + corrected)

| Test | Finding | Action |
|------|---------|--------|
| **1A** empty extraction during migration | **Reproduced:** partial empty extraction could authorize destructive replace | **Fixed:** quality gate rejects empty extraction |
| **1B** partial GUID coverage | **Reproduced:** partial quality allowed replace | **Fixed:** quality gate rejects `partial` quality |
| **1C** GUID-complete subset (2 of 5 legacy rows) | **Reproduced:** subset could replace full cache | **Fixed:** legacy coverage gate blocks unmatched legacy rows |
| **1D–1E** valid migration / TX rollback | PASS | unchanged |
| **1F** full legacy coverage + incoming extras | PASS — 3 legacy → 5 GUID rows | **Allowed by design** |
| **1G–1I** ambiguous incoming, duplicate legacy keys, rename | PASS — blocked, cache preserved | **Fixed:** coverage gate |
| **1J–1K** exact correspondence / rollback after coverage | PASS | unchanged |

**Migration correspondence rule (implemented):** destructive replacement requires non-empty, `complete` extraction, validation success, GUID-first incoming rows, and **one-to-one canonical name coverage of every existing legacy row** using `normalizeName(name)`. Incoming extras are allowed. Renamed or ambiguously matched legacy rows defer automatic migration. This prevents local cache loss but **does not prove global Tally semantic completeness**.

**Rename limitation:** Tally renames before migration leave legacy `normalizeName(oldName)` unmatched — automatic migration is deferred; no AlterID/MasterID inference.

### Parser characterization (no production change)

- Trailing junk after root: accepted (compatibility limitation)
- BOM: stripped by `trim()`, parses successfully
- Comments/CDATA/DOCTYPE: rejected (malformed)
- No nesting/node-count limits (documented gap)
- Parser errors privacy-safe (no raw XML in messages)

### Response-contract characterization (no production change)

- Ledgers/stock: no pre-parse `assessEnvelope` on extract path
- Synthetic `LINEERROR` envelope parses but yields zero entities — no dedicated error classifier
- Tree-wide `findAll` can match unrelated subtrees
- Empty collection: ledger `partial`, stock no collection assessor

### Normal sync empty/partial (no production change)

- Empty/partial ledger or stock sync with populated cache: **upsert-only, no deletion**
- Invalid (`invalid`) ledger extraction: **no mutation**

### Production files changed

| File | Change |
|------|--------|
| `src/services/ledger/ledger-cache-migration.ts` | Split quality/validation gates; reject empty and partial extraction |
| `src/services/ledger/ledger-migration-coverage.ts` | Legacy-to-incoming canonical coverage assessment |
| `src/services/ledger/ledger-sync.service.ts` | Migration path: quality → coverage → validation → atomic replace |

### Tests added

| Suite | Count |
|-------|-------|
| `test/integration/ledger-migration-safety.test.ts` | 12 |
| `test/unit/services/ledger-cache-migration.test.ts` | 3 |
| `test/unit/services/ledger-migration-coverage.test.ts` | 5 |
| `test/unit/tally/response-parser-boundary.test.ts` | 13 |
| `test/unit/tally/master-data-response-contract.test.ts` | 16 |
| `test/unit/extraction/entity-mapping-counters.test.ts` | 9 |
| `test/integration/inbound-sync-empty-partial.test.ts` | 5 |
| Helpers: `inbound-xml-fixtures.ts`, `ledger-migration-test-helpers.ts` | — |

### Validation

| Command | Result |
|---------|--------|
| `npm run lint` | PASS |
| `npm run build` | PASS |
| Migration safety + coverage + identity migration | **21/21 PASS** |
| Focused Control #3 suites | **57/57 PASS** |
| TD-006 + atomicity + concurrency + diagnostics | **50/50 PASS** |
| Architecture boundaries | **12/12 PASS** |
| Full connector suite | **478/478 PASS** |

### Remaining limitations

- No ledger/stock envelope assessment on extract path
- No Tally explicit-error envelope classifier
- Parser nesting/node limits not enforced
- Silent unnamed-entity drops without counters
- Stock has no collection-level quality assessor
- Renamed ledgers require manual/controlled remediation before automatic migration
- Reliability Control #3 **not fully complete**

**Verdict:** **SAFE TO COMMIT MIGRATION SAFETY FIX** — migration gate + legacy coverage + tests/docs. Not unrestricted production approval.

---

## 21. Reliability Control #3 Phase C — operation-specific ledger/stock response contracts (2026-07-24)

**Status:** Complete (uncommitted)

**Type:** Operation-specific inbound response contracts + scoped collection extraction

**Requirement addressed:** Reliability Control #3 — ledger and stock-item extraction bypass envelope assessment; explicit Tally errors silently become empty success; tree-wide entity lookup accepts unrelated subtrees

### Valid response shapes identified (repository evidence)

**Request path (both):** `ENVELOPE/HEADER` → `ENVELOPE/BODY/DESC/STATICVARIABLES` + TDL `COLLECTION[@ISMODIFY="Yes"]/ADD Fetch : …`

**Ledger response:** `ENVELOPE/BODY/DATA/COLLECTION/LEDGER[*]` with `NAME`, `PARENT`, `GUID`, `ALTERID`, `MASTERID`, balances, `ISBILLWISEON`
Evidence: `test/helpers/master-data-fixtures.ts` (`SAMPLE_LEDGERS_RESPONSE`), live validation referenced in milestone docs

**Stock response:** `ENVELOPE/BODY/DATA/COLLECTION/STOCKITEM[*]` with `NAME`, `GUID`, `ALTERID`, `PARENT`, `BASEUNITS`, etc.
Evidence: `SAMPLE_STOCK_ITEMS_RESPONSE`

**Company/groups (existing, unchanged):** same outer envelope; entity nodes under `DATA/COLLECTION`

### Real versus synthetic error evidence

| Evidence | Source | Notes |
|----------|--------|-------|
| Valid rich ledger/stock envelopes | `master-data-fixtures.ts` | **Committed** operational-style fixtures |
| `LINEERROR` | `inbound-xml-fixtures.ts` | **Synthetic** — explicit error classifier; drives blocking `TALLY_ERROR` |
| `LINEERROR` + paired `HEADER/STATUS=0` | `SYNTHETIC_TALLY_LINEERROR_RESPONSE` | **Synthetic** — STATUS recorded as optional `associatedHeaderStatusZero` metadata only |
| Standalone `HEADER/STATUS=0` | `SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE` | **Synthetic** — **not** treated as proven Tally failure |
| Wrong root / missing envelope | `inbound-xml-fixtures.ts` | Synthetic drift fixtures |
| Live Tally error shape catalog | — | **Not committed** — standalone STATUS semantics unverified |

**Failure classifier rule:** only explicit `LINEERROR` drives blocking `TALLY_ERROR`. Standalone `HEADER/STATUS` values are never treated as universally proven failures. Raw status payloads are not exposed.

### Operation-specific contract design

| Module | Version | Scope |
|--------|---------|-------|
| `ledger-master-data-contract.ts` | `1` | Rich ledger master-data extraction |
| `stock-item-master-data-contract.ts` | `1` | Rich stock-item master-data extraction |
| `master-data-envelope.ts` | shared | Structural path helpers, LINEERROR detection |

**Status categories:** `SUCCESS`, `EMPTY`, `INCOMPLETE`, `MALFORMED`, `TALLY_ERROR`, `COLLECTION_MISSING`, `ALL_UNMAPPABLE`

**Reason codes (privacy-safe):** `envelope_drift`, `tally_line_error`, `collection_missing`, `collection_empty`, `placeholder_only`, `all_entities_unmappable`, `partial_entities_dropped`, `shallow_export` (ledger)

Optional LINEERROR metadata (not a standalone classifier): `associatedHeaderStatusZero: true`

### Collection scoping algorithm

**Exact boundary:** `ENVELOPE → BODY → DATA → direct COLLECTION children → direct LEDGER/STOCKITEM children`

1. Locate `ENVELOPE → BODY → DATA`
2. Collect **all direct child** `COLLECTION` nodes under `DATA` (named collection identity is **not** independently proven when Tally provides no trustworthy collection marker)
3. Read only **direct child** `LEDGER` / `STOCKITEM` nodes from those collections
4. Ignore entity nodes in unrelated subtrees (`OTHERDATA`, `DESC`, nested non-entity wrappers)
5. Filter placeholder/count-metadata entity nodes via `isCountMetadata` / `isPlaceholderEntityNode`

Tree-wide `findAll` is **not** used for ledger/stock contract paths. Non-contract extractors unchanged.

**Remaining scoping limitation:** operation/entity-type scoping is enforced; named collection identity is not independently proven where response metadata does not provide it.

### Aggregate extraction metrics (privacy-safe)

`candidateNodeCount`, `mappedRecordCount`, `droppedRecordCount`, `missingIdentityCount`, `duplicateIdentityCount`, `conflictingIdentityCount`, `collectionPresent`, `placeholderOnlyCollection`

Attached to `ExtractionResult` for ledger/stock contract paths. No record values in metrics or typed errors.

### Empty / missing / error policy

| Condition | Ledger normal sync | Stock normal sync | Ledger migration |
|-----------|-------------------|-------------------|------------------|
| Valid empty collection | Complete, no deletion | Complete, no deletion | Existing non-empty/complete gates unchanged (`6ff0c0e`) |
| Missing `DATA/COLLECTION` | Fail 502, no mutation | Fail 502, no mutation | Unchanged |
| Explicit `LINEERROR` | Fail 502, no mutation | Fail 502, no mutation | Unchanged |
| All entities unmappable / shallow | Fail 502, no mutation | Fail 502, no mutation | Unchanged |
| Partial malformed subset | Accept INCOMPLETE + warnings | Accept INCOMPLETE + warnings | Unchanged |

### Production files changed

| File | Change |
|------|--------|
| `src/tally/contracts/master-data-envelope.ts` | Shared envelope path + LINEERROR helpers |
| `src/tally/contracts/ledger-master-data-contract.ts` | Ledger operation contract |
| `src/tally/contracts/stock-item-master-data-contract.ts` | Stock operation contract |
| `src/extraction/parsers/master-data-extraction-metrics.ts` | Aggregate counters |
| `src/extraction/parsers/entity-mappers.ts` | Scoped collection parsing option |
| `src/extraction/core/types.ts` | Contract summary + metrics on `ExtractionResult` |
| `src/extraction/extractors/master-data-extractor.ts` | Contract-aware extraction for ledger/stock |
| `src/extraction/extractors/extractor-registry.ts` | Enable contracts on ledgers/stockItems |

### Tests added

| Suite | Count |
|-------|-------|
| `test/unit/tally/ledger-master-data-contract.test.ts` | 16 |
| `test/unit/tally/stock-item-master-data-contract.test.ts` | 10 |
| `test/unit/extraction/master-data-extractor-contract.test.ts` | 11 |
| `test/integration/master-data-contract-sync.test.ts` | 5 |
| `test/helpers/inbound-xml-fixtures.ts` | extended |

Prior Phase B characterization tests retained (`master-data-response-contract.test.ts`) — document pre-contract behavior; do not claim production fixes.

### Validation

| Command | Result |
|---------|--------|
| `npm run lint` | PASS |
| `npm run build` | PASS |
| New ledger/stock contract + extractor + sync suites | **42/42 PASS** |
| Migration safety + identity | **16/16 PASS** |
| TD-006 + atomicity + concurrency + diagnostics | **47/47 PASS** |
| Architecture boundaries | **12/12 PASS** |
| Full connector suite | **520/520 PASS** |

### Remaining Reliability Control #3 limitations

- Standalone `HEADER/STATUS` semantics not live-confirmed; only `LINEERROR` is a blocking classifier
- Named collection identity not independently proven when Tally omits trustworthy collection markers
- Broader live Tally error-shape catalogue not committed
- Global Tally semantic completeness cannot be proven
- Renamed-ledger controlled remediation still manual before automatic migration
- Stock full quality model not redesigned (minimum collection assessment only)
- Non-contract extractor paths still lack aggregate drop accounting
- Reliability Control #3 **not fully complete**

**Verdict:** **SAFE TO COMMIT OPERATION CONTRACTS** — subject to live error-shape evidence for standalone STATUS semantics. Not unrestricted production approval.

---

## 22. Reliability Control #3 Phase D — bounded XML parser hardening (2026-07-24)

**Status:** Complete (committed)

**Type:** Narrow parser boundary protections — trailing content, depth limit, node-count limit, parser-option validation

**Requirement addressed:** Reliability Control #3 — custom parser accepted trailing content and had no recursion/allocation bounds

### Evidence-based limits

| Metric | Committed/live evidence | Production default |
|--------|-------------------------|-------------------|
| Max element depth | Rich ledger/stock/company fixtures: **6**; groups fixture: **6** | **64** (root depth = 1) |
| Node count (2-ledger fixture) | **21** element nodes | — |
| Node count (922 rich ledgers, synthetic) | **8,302** element nodes | — |
| Node count (1502 rich stock, synthetic) | **10,518** element nodes | — |
| Max node count default | ~3× live stock node total headroom | **32,768** |
| Transport byte cap (unchanged) | 1 MiB rich master collections | Complementary protection |

### Parser protections implemented

1. **Trailing content:** after single root closes, whitespace allowed; non-whitespace rejected (`xml_trailing_content`)
2. **Nesting depth:** per-parse context counts element depth; reject before exceeding `maxDepth` (`xml_max_depth_exceeded`)
3. **Node count:** increment once per element node; reject before exceeding `maxNodeCount` (`xml_max_node_count_exceeded`)
4. **Context isolation:** limits stored in per-parse context — no global mutable counters
5. **Parser option validation:** optional `maxDepth` / `maxNodeCount` overrides normalized through `resolveXmlParserLimits()` — omitted values use safe defaults; valid positive integers at or below the approved maximum are accepted; zero, negative, fractional, non-finite, or excessive values reject with `xml_invalid_parser_limit` (metadata: option name, approved maximum, finite `providedValue` when applicable). Production callers do not pass overrides today; limits cannot be disabled or raised above the approved maximum.

### Approved parser maximums

| Option | Safe default | Approved maximum (hard cap) |
|--------|--------------|----------------------------|
| `maxDepth` | **64** | **64** |
| `maxNodeCount` | **32,768** | **32,768** |

Tests may use **lower** bounded values for compact boundary cases. Values above the approved maximum are rejected — tests must not require overrides above production caps.

### Compatibility preserved

Leading/trailing whitespace, XML declaration before root, case normalization, five named entities, rich ledger/stock/company/groups fixtures, operation contracts from `18f7ff0`.

Unsupported constructs remain unsupported (DOCTYPE, comments, CDATA, external entities, multiple roots).

### Relationship to other layers

- **Transport byte limits** bound response size (unchanged)
- **Parser depth/node limits** bound recursion and tree allocation
- **Operation contracts** validate business structure (unchanged from `18f7ff0`)

### Production files changed

| File | Change |
|------|--------|
| `src/tally/xml/response-parser.ts` | Trailing-content rejection, bounded parse context |
| `src/tally/xml/response-parser-limits.ts` | Centralized default limits + evidence notes |
| `src/tally/xml/response-parser-errors.ts` | Typed `XmlParseError` reason codes |

### Tests added/changed

| Suite | Count |
|-------|-------|
| `test/unit/tally/response-parser-limits.test.ts` | 39 (new + option validation) |
| `test/unit/tally/response-parser-boundary.test.ts` | 13 (updated for Phase D behavior) |

### Validation

| Command | Result |
|---------|--------|
| `npm run lint` | PASS |
| `npm run build` | PASS |
| Parser limits + boundary + response-parser | **22/22 PASS** |
| Operation contracts + extractor + sync | **42/42 PASS** |
| Migration safety + identity | **16/16 PASS** |
| Scenario 12 + transport | **25/25 PASS** |
| TD-006 + atomicity + concurrency + diagnostics | **47/47 PASS** |
| Architecture boundaries | **12/12 PASS** |
| Full connector suite | **536/536 PASS** |

### Remaining Reliability Control #3 limitations

- Standalone `HEADER/STATUS` semantics not live-confirmed
- Named collection identity not independently proven
- Broader live Tally error-shape catalogue not committed
- Global semantic completeness cannot be proven
- Renamed-ledger controlled remediation still manual
- Stock full quality model not redesigned
- Non-contract extractor paths still lack aggregate drop accounting
- Unsupported XML constructs (comments, CDATA, DOCTYPE) still fail generically — not expanded
- Reliability Control #3 **not fully complete**

**Verdict:** **SAFE TO COMMIT PARSER BOUNDARY HARDENING** — not unrestricted production approval.

---

## 23. Reliability Control #3 Phase E — live Tally error-shape evidence (2026-07-24)

**Status:** Complete — controlled live evidence captured; privacy validation PASS; harness and evidence committed (no production code changes)

**Type:** Privacy-safe live localhost error-shape capture and current-contract verification

**Requirement addressed:** Reliability Control #3 — synthetic-only LINEERROR/STATUS evidence; live structural error-shape catalogue

**Evidence file:** `docs/diagnostics/m5b-tally-error-shape-validation.json` (`validatedAt`: 2026-07-24T14:33:16.890Z)

### Harness (uncommitted)

| Component | Path |
|-----------|------|
| Structural scanner | `test/helpers/tally-error-shape-scanner.ts` |
| Run plan / preflight | `test/helpers/tally-error-shape-run-plan.ts` |
| Company context helper | `test/helpers/tally-error-shape-company-context.ts` |
| Live runner | `scripts/live-tally-error-shape-validation.ts` |
| Evidence validator | `test/helpers/tally-error-shape-evidence-validation.ts` |
| Unit tests | `test/unit/diagnostics/tally-error-shape-evidence.test.ts`, `tally-error-shape-run-plan.test.ts` |

Scanner contract assessment uses the same production functions as structure-stage evaluation: `assessLedgerMasterDataEnvelope` → `assessLedgerMasterDataStructure` (ledger) and stock equivalents. It does **not** run extraction-stage assessment (`assessLedgerMasterDataExtraction`).

### Provisional runs invalidated (do not use)

Initial localhost runs were non-authoritative due to wrong company open, E4 modal disruption, and subsequent zero-byte E1/E2. Harness corrected: E4 excluded by default; E1/E2 populated-company preflight gate.

### Controlled rerun summary

| Gate | Result |
|------|--------|
| `populatedCompanyGatePassed` | true |
| E1 sanity | 923 entities, 618430 bytes, SUCCESS |
| E2 sanity | 1503 entities, 963364 bytes, SUCCESS |
| `validCompanyPreflight` | PASS |
| `likelyTallyModalBlocking` | false |
| E4 included | false |
| Scenarios completed | 5 (E1, E2, E3, E5, E6) |
| All repeat-stable | true |
| `--validate-evidence` | PASS |

### Per-scenario analysis (structure-stage contract; 2 runs each)

#### E1 — valid rich ledger

| Field | Run 1 / Run 2 |
|-------|---------------|
| Transport | HTTP 200 |
| Response bytes | 618430 |
| ENVELOPE / HEADER / BODY / DATA / COLLECTION | all present |
| HEADER STATUS | present, classified `1` |
| LINEERROR | absent |
| Requested (LEDGER) count | 923 |
| Unrelated entity count | 0 |
| Parser | accepted |
| Contract (structure) | SUCCESS, non-blocking |
| Correct for scenario | yes |
| Repeat stability | stable (identical bytes, counts, contract) |

#### E2 — valid rich stock

| Field | Run 1 / Run 2 |
|-------|---------------|
| Transport | HTTP 200 |
| Response bytes | 963364 |
| ENVELOPE / HEADER / BODY / DATA / COLLECTION | all present |
| HEADER STATUS | present, classified `1` |
| LINEERROR | absent |
| Requested (STOCKITEM) count | 1503 |
| Unrelated entity count | 0 |
| Parser | accepted |
| Contract (structure) | SUCCESS, non-blocking |
| Correct for scenario | yes |
| Repeat stability | stable |

#### E3 — nonexistent company name in request

| Field | Run 1 / Run 2 |
|-------|---------------|
| Transport | HTTP 200 |
| Response bytes | 1497 |
| ENVELOPE / HEADER / BODY / DATA / COLLECTION | all present |
| HEADER STATUS | present, classified `1` |
| LINEERROR | absent |
| Requested (LEDGER) count | 0 |
| Unrelated entity count | 0 |
| Parser | accepted |
| Contract (structure) | SUCCESS, non-blocking |
| Harness `contractCorrectForScenario` | false (expects blocking without LINEERROR) |
| Repeat stability | stable |

**E3 decision:** Not proven misclassified by structure-stage contract alone.

Evidence distinguishes these hypotheses:

| Hypothesis | Supported? |
|------------|------------|
| Tally accepted company context and returned valid empty collection shell (~1.5 KiB, COLLECTION present, zero LEDGER) | **Yes** — shape matches prior wrong-company empty responses; no LINEERROR |
| Tally ignored nonexistent name and returned currently selected populated company | **No** — E1 returned 923 LEDGER / 618430 bytes; E3 returned 0 LEDGER / 1497 bytes |
| Tally returned error-like structure without LINEERROR | **Partial** — empty collection with STATUS=`1`, not a blocking error envelope |
| Other | No stronger marker observed |

**No reliable response marker** in captured evidence proves the nonexistent company name was rejected (no LINEERROR, no STATUS=`0` association, no company-rejection element, no named-collection identity attribute). Empty collection + STATUS=`1` is **not** treated as error without stronger evidence.

**Classification:** Unresolved **Tally company-context limitation** — connector cannot verify which company Tally applied (`doesNotVerifyTallySelectedCompany: true` in evidence). Production extraction path would yield **EMPTY** (`collection_empty`, non-blocking) for zero LEDGER nodes; that is consistent with current contract design and does not warrant automatic contract change from E3 alone.

#### E5 — ledger-groups response evaluated by ledger contract

| Field | Run 1 / Run 2 |
|-------|---------------|
| Transport | HTTP 200 |
| Response bytes | 17964 |
| ENVELOPE / HEADER / BODY / DATA / COLLECTION | all present |
| HEADER STATUS | present, classified `1` |
| LINEERROR | absent |
| Requested (LEDGER) count | 0 |
| Unrelated (GROUP) count | 28 |
| Parser | accepted |
| Contract (structure) | SUCCESS, non-blocking |
| Harness `contractCorrectForScenario` | false (expects non-SUCCESS when unrelated-only) |
| Repeat stability | stable |

**E5 decision:** Proves **structure-stage** ledger assessment returns SUCCESS when a COLLECTION exists under BODY/DATA regardless of LEDGER node presence. Scanner and `assessLedgerMasterDataStructure` use **identical logic** — confirmed.

Distinctions:

| Case | Structure contract | Full production extractor (structure + extraction) |
|------|-------------------|-----------------------------------------------------|
| Valid empty ledger collection (0 LEDGER, 0 unrelated) | SUCCESS | EMPTY (`collection_empty`, non-blocking) |
| GROUP-only collection (0 LEDGER, 28 GROUP) — E5 | SUCCESS | EMPTY (`collection_empty`, non-blocking) — conflates with legit empty |
| Populated ledger (E1) | SUCCESS | SUCCESS |

E5 does **not** prove the **final** production contract surfaces SUCCESS for GROUP-only misapplication — `MasterDataExtractor` reports extraction-stage status (EMPTY here). Approved ledger sync uses the ledger operation and returned LEDGER nodes (E1); E5 is an intentional mis-scope probe, not the production read path.

**Smallest deferred correction (not implemented):** If entity-scope enforcement at structure stage is desired, extend `assessLedgerMasterDataStructure` to return `COLLECTION_MISSING` or a scope-mismatch reason when direct COLLECTION children contain unrelated entity types but zero scoped LEDGER nodes — distinguishing E5 from E3-style legit empty. Optional extraction-stage enhancement could treat unrelated-only collections as blocking scope drift.

#### E6 — transport unavailable

| Field | Run 1 / Run 2 |
|-------|---------------|
| Transport | failure (`CONNECTION_REFUSED_OR_TIMEOUT`, status null) |
| Response bytes | 0 |
| XML structure | not present |
| HEADER STATUS / LINEERROR | n/a |
| Parser | not_applicable |
| Contract | n/a (transport before XML) |
| Correct for scenario | yes |
| Repeat stability | stable |

### Live-proven (this localhost TallyPrime scope)

- Populated ledger response: ENVELOPE → BODY → DATA → COLLECTION with 923 direct LEDGER children; ~618 KiB; parser accepted; structure SUCCESS; STATUS=`1`; no LINEERROR.
- Populated stock response: 1503 direct STOCKITEM children; ~963 KiB; same envelope shape; structure SUCCESS; STATUS=`1`; no LINEERROR.
- Collection metadata: single COLLECTION; attributes `ISMSTDEPTYPE`, `MSTDEPTYPE` only; **no** collection name/type identity attributes observed.
- STATUS=`1` on successful populated reads; no LINEERROR on any completed scenario.
- E3: ~1.5 KiB empty-collection shell; STATUS=`1`; no LINEERROR; not populated-company data.
- E5: GROUP-only collection (28 nodes); structure SUCCESS; no LINEERROR.
- E6: connection refused on probe port 9001 before XML; repeatable transport failure classification.

### Not proven

- Universal HEADER STATUS semantics (`1` vs `0`) across operations and Tally versions.
- Real live LINEERROR structure (not observed in completed safe scenarios; E4 excluded).
- Invalid-report E4 XML response shape.
- Cross-version / cross-edition consistency.
- Explicit nonexistent-company rejection marker.
- Global semantic completeness of structure-only vs extraction-stage contract boundaries.
- Named collection identity attributes for request/response correlation.

### Deferred observation

- E5 structure-stage unrelated-collection distinction may be refined later; no production correction is required for approved ledger/stock paths now.

### Evidence consistency review

| Check | Result |
|-------|--------|
| Reflects successful controlled rerun | yes (`validatedAt` 2026-07-24; populated gate passed) |
| E1/E2 counts match populated test company | yes (923 / 1503; consistent with prior identity-validation scale) |
| Raw XML / business identifiers stored | no (`privacyReview` all true; `companyLabel`: `[REDACTED]`) |
| E4 absent | yes (`includesDisruptiveE4`: false) |
| Scenario count / repeat summaries | 5 scenarios, all `repeatStable: true` |
| Validation-only rerun leaves evidence unchanged | yes (read-only validator; no live re-run in this phase) |

### Production defect determination

No production defect **proven** for approved ledger or stock read paths (E1, E2, E6). E3 is an unresolved Tally limitation, not a contract bug. E5 reveals a **latent structure-stage gap** under intentional mis-scope; full extractor mitigates to EMPTY and does not affect normal ledger operation wiring.

**Verdict:** **EVIDENCE SUPPORTS CURRENT CONTRACTS** for approved read operations — Phase E evidence committed; **Reliability Control #3 not fully complete**; **unrestricted production not approved** (structure-stage scope gap documented; LINEERROR live shape still unobserved).

---

## 24. Reliability Control #1 Phase B — capability preflight tests and narrow fixes (2026-07-24)

**Status:** Complete — deterministic tests added; narrow production fixes applied; validation PASS (uncommitted)

**Type:** Test-first capability preflight hardening (no orchestrator framework; no rich capability cache)

### Phase A inspection summary (call order)

**Ledger sync:** `POST /sync/ledgers` → `LedgerSyncServiceImpl.syncLedgers()` → `requireCompanyId()` (session `validateForOperation`) → `executeSync()` → `companyResolver.resolveName()` → `syncRuns.createRun()` → `readPort.readLedgers()` → extractor/guard/transport/contracts.

**Stock sync:** identical ordering via `StockItemSyncServiceImpl`.

**Sync-run creation:** occurs **after** session validation and company name resolution; **before** Tally extraction.

### Production changes (narrow, evidence-proven)

| Fix | Change | Proven by |
|-----|--------|-----------|
| **A — fail-closed company context** | `loadSelectionContext()` sets `discoveryAvailable: false` on discovery failure; `validateSession` / `selectCompany` return `COMPANY_DISCOVERY_UNAVAILABLE` / `INVALID_COMPANY` — reachability alone cannot authorize company-scoped ops | Tests 3C, 3D, 3D API |
| **B — cache invalidation** | `invalidateCache()` on discovery failure catch, `clearSelection()`, and **before** `selectCompany()` (fresh discovery for selection) | Tests 4C, 4D, 4E |
| **C — sync boundary** | Confirmed existing: `requireCompanyId()` before `createRun()` — no move required | Tests 5A, 5B |
| **D — request coalescing** | In-flight promise on concurrent cache miss in `CompanyResolver` | Test 4G |

**Not implemented:** `CapabilityPreflightService` orchestrator; rich ledger/stock capability cache; separate health probes before sync; registry/guard/policy changes.

### New session status

- `COMPANY_DISCOVERY_UNAVAILABLE` (HTTP 503) — discovery could not establish company context; distinct from `COMPANY_NOT_FOUND` (discovery succeeded, company absent).

### Cache contract (documented)

| Property | Value |
|----------|-------|
| TTL | `COMPANY_DISCOVERY_CACHE_TTL_MS` = 60_000 ms |
| Invalidation triggers | explicit `invalidateCache()`; discovery failure; `clearSelection()`; start of `selectCompany()` |
| Runtime host/port change | **Not supported** — config loaded at process start; no hook added |
| Concurrent miss | coalesced via in-flight refresh promise |

### Minimum probe findings (integration)

| Scenario | Tally requests on sync |
|----------|-------------------------|
| Warm cache after selection | 1× ledger (or stock) export only |
| Discovery unavailable on sync | 0× export; blocked at session validation |

Discovery for company selection performs one company-list request; sync reuses cache within TTL.

### Tests added

| File | Coverage |
|------|----------|
| `test/unit/tally/capability-preflight-policy.test.ts` | Groups 1A–1C, 2A–2C |
| `test/unit/session/capability-preflight-session.test.ts` | Groups 3A–3F, 4D API |
| `test/unit/extraction/company-resolver-cache.test.ts` | Groups 4A–4C, 4E, 4G |
| `test/integration/capability-preflight-sync.test.ts` | Groups 5A–5D, 6A–6D, 7 |
| `test/helpers/capability-preflight-helpers.ts` | Request classification + privacy assertions |

**New tests:** 40 (603 total suite, was 572).

### Capability-preflight model decision

1. **`CapabilityPreflightService` not required** for this phase.
2. **Sufficient:** tightened session validation + company resolver cache/invalidation + existing sync entry ordering.
3. **Orchestrator would add a layer** without removing guard/transport/contract responsibilities.
4. **Check placement:** session/company resolution before sync-run creation; policy/XML/circuit in request guard; response contracts after receipt.

### Remaining RC#1 limitations

- `isReady()` still means connection-manager running only (documented, not renamed).
- No typed cross-operation capability matrix or Tally version probing.
- Tally selected-company vs `SVCURRENTCOMPANY` mismatch not detectable from response shape (Phase E E3).
- Runtime config hot-reload not supported.
- Session/API success responses still include company names by design (privacy allowlist applies to diagnostics/export surfaces).

**Verdict:** **SAFE TO COMMIT NARROW PREFLIGHT FIX** — **Reliability Control #1 remains PARTIAL**; **unrestricted production not approved**.

---

## 25. Reliability Control #4 Phase B1 — on-disk privacy hardening (2026-07-24)

**Status:** Complete — tests-first hardening applied; validation PASS (uncommitted)

**Type:** Narrow persistent diagnostic sink privacy fixes (no retention, encryption, telemetry, or export redesign)

### Phase A original persistence risks

| Sink | Risk |
|------|------|
| `tally-request-audit.jsonl` | Blocklist-redacted request XML (`redactedXml`) + raw `errorMessage`; unbounded; enabled by default |
| Connector `StructuredLogger` stdout | Arbitrary context serialized without sanitization |
| HTTP 5xx error middleware | Logged raw `AppError.details` |
| `sync_runs.failure_summary` | Stored raw `error.message` / Tally text |
| Desktop `budcom-desktop.log` | Weaker message-level redaction than diagnostic export allowlist |

**Unchanged (confirmed):** diagnostic export allowlist; domain SQLite operational data; no remote telemetry/crash upload.

### Fix A — metadata-only Tally audit

**Original shape:** `timestamp`, `correlationId`, `operationId`, `capability`, `policyDecision`, `collectionId`, `reportId`, `requestByteLength`, `requestHash`, `circuitStateBefore`, `outcome`, **`redactedXml`**, **`errorMessage`**

**New shape:** same metadata fields + **`errorReasonCode`** (stable classification); **`redactedXml` and `errorMessage` removed**

**Behavior:** audit remains enabled by default; XML input used only for hash computation; concurrent writes serialized via in-process write chain; JSONL format preserved.

### Fix B — logger sink sanitization

**Boundary:** `StructuredLogger.write()` sanitizes message + context before sink emission.

**Model:** approved context key allowlist; sensitive key blocklist; safe primitives only; identifier keys (correlationId, operationId, etc.) skip pattern redaction; unknown keys dropped; errors/objects/arrays fail closed to `[REDACTED]`; message max length + control-character neutralization via shared `persistent-text-sanitizer`.

**Call-site cleanup:** removed `companyName` from master-data/session logs; removed `databasePath` from storage startup log.

### Fix C — HTTP 5xx logging

**Behavior:** `AppError.details` passed through `sanitizeLogDetails()` before logging; message sanitized; unhandled errors log `INTERNAL_ERROR` code only (no stack/cause/raw message). **`AppError.toResponse()` unchanged** — API response compatibility preserved.

### Fix D — sync failure normalization

**Mapper:** `normalizeSyncFailure()` / `sanitizeSyncProgressError()` in `infrastructure/privacy/sync-failure-normalizer.ts`

**Vocabulary:** `transport_unavailable`, `transport_timeout`, `response_too_large`, `response_parse_failed`, `response_contract_rejected`, `tally_reported_error`, `storage_failure`, `sync_cancelled`, `sync_conflict`, `validation_error`, `read_only_violation`, `unexpected_sync_failure`

**Storage:** `sync_runs.failure_code` + `failure_summary` receive stable normalized values; progress `lastError` bounded normalized summary; **re-thrown API errors preserve original message** for non-`AppError` paths (TD-006 / atomicity tests).

**Example (synthetic):**

| Before | After |
|--------|-------|
| `failure_summary`: `Connection failed: ECONNREFUSED 127.0.0.1:9000` | `failure_code`: `SERVICE_UNAVAILABLE`, `failure_summary`: `transport_unavailable` |
| `failure_summary`: `injected checkpoint failure` | `failure_code`: `SERVICE_UNAVAILABLE`, `failure_summary`: `storage_failure` |

### Fix E — desktop file-log parity

**Behavior:** export-grade `sanitizePersistentLogText()` applied on **file write only**; in-memory ring retains existing `redactString()` (diagnostic export still sanitizes at bundle boundary; source-entry mutation test preserved).

**Unchanged:** 1 MB × 5 rotation; user clear; in-memory ring; diagnostic export allowlist.

### Shared sanitizer

`persistent-text-sanitizer.ts` shared by connector diagnostics allowlist, logger, sync failure, audit error normalization. Desktop copies patterns in `persistent-log-text.ts` (no cross-package import).

### Tests added/changed

| File | Coverage |
|------|----------|
| `test/unit/tally/tally-request-audit-privacy.test.ts` | Groups 1A–1D |
| `test/unit/tally/tally-request-auditor.test.ts` | Metadata-only shape |
| `test/unit/infrastructure/log-context-sanitizer.test.ts` | Group 2 |
| `test/unit/infrastructure/error-handler-logging.test.ts` | Group 3 |
| `test/unit/infrastructure/sync-failure-normalizer.test.ts` | Group 4 unit |
| `test/integration/sync-failure-persistence.test.ts` | Group 4 integration |
| `test/integration/sync-batch-atomicity.test.ts` | Normalized `lastError` expectation |
| `test/unit/logger.test.ts` | Approved context key |
| `apps/budcom_desktop/test/unit/file-log-sanitization.test.ts` | Group 5 |

### Validation evidence

| Command | Result |
|---------|--------|
| `npm run lint` (connector) | PASS |
| `npm run build` (connector) | PASS |
| Focused privacy suites (8 files) | **46/46 PASS** |
| Full connector vitest | **623/623 PASS** |
| `test/architecture/module-boundaries.test.ts` | **12/12 PASS** |
| Desktop lint + vitest | **97/97 PASS** |

### Deferred Phase B2+

Audit retention/rotation framework; diagnostic-export cleanup; backup cleanup; SQLite/log encryption; migration-path redaction; installer/uninstall cleanup; secure deletion; crash reporter; telemetry; remote support bundles; new diagnostics UI/config; schema redesign; domain-data redaction; stdout capture changes; production packaging.

### RC#4 status

**Reliability Control #4 remains PARTIAL** — on-disk sinks hardened for highest-risk paths (Phase B1 metadata-only audit; Phase B2a bounded audit rotation). Diagnostic-export cleanup, sync-run pruning, encryption, and broader retention work remain deferred (Phase B2b+).

**Verdict (Phase B1):** **SAFE TO COMMIT NARROW ON-DISK PRIVACY FIX** — **unrestricted production not approved**.

---

## §25 — RC#4 Phase B2a: bounded Tally request audit rotation (2026-07-24)

### Requirement addressed

Reliability Control #4 Phase B2a — count- and size-bounded rotation for the metadata-only persistent Tally request audit file (`tally-request-audit.jsonl`) only.

### Prior defect

The audit append-only file grew without maximum size or rotated-file count. No production component reads historical audit files; they are support/forensic metadata only.

### Implementation status

**COMPLETE (connector scope)** — bounded rotation inside the existing serialized write chain; no diagnostic-export, sync-run, backup, encryption, or desktop changes.

### Configuration

| Field | Env | Default | Bounds |
|-------|-----|---------|--------|
| `tallyRequestAuditMaxBytes` | `BUDCOM_TALLY_REQUEST_AUDIT_MAX_BYTES` | **10 MiB** | 64 KiB – 100 MiB |
| `tallyRequestAuditMaxFiles` | `BUDCOM_TALLY_REQUEST_AUDIT_MAX_FILES` | **5** rotated archives | 1 – 20 |

Zero does not mean unlimited. Disabled audit creates no file or directory.

### Rotation semantics

Before each append (inside the write chain):

1. Serialize and sanitize the metadata-only audit record.
2. Compute complete-line byte size including newline.
3. Rotate only when `currentSize > 0 && currentSize + newLineBytes > maxBytes`.
4. Never rotate an empty file; never split a JSON line across files.
5. Deterministic descending rotation: delete `.maxFiles`, rename `.(n-1)` → `.n` … `.1` → `.2`, rename base → `.1`, append to fresh base file.
6. A single record larger than `maxBytes` is written once to the current file (no infinite rotation loop); temporary size-bound breach allowed when rotation fails.

Approximate maximum footprint: current file + `maxFiles` archives (~60 MiB at defaults).

### Failure behavior

Audit lifecycle failures never block approved Tally requests. Stable sanitized lifecycle codes only (`audit_stat_failed`, `audit_rotation_delete_failed`, `audit_rotation_rename_failed`, `audit_append_failed`, `audit_directory_create_failed`); no path, username, or raw filesystem message leakage. Failed append does not permanently block later writes.

### Privacy

Rotation applies equally to current and numbered archive files: metadata-only schema, no request/response XML, no raw errors, no company/entity names, stable `errorReasonCode`.

### Files changed

| File | Change |
|------|--------|
| `src/tally/safety/audit-file-rotator.ts` | **New** — narrow rotation helper with injectable filesystem seam |
| `src/tally/safety/tally-request-auditor.ts` | Options object; rotator integration; lifecycle logging |
| `src/config/defaults.ts` | Audit max bytes/files defaults and bounds constants |
| `src/config/index.ts` | Env parsing and post-merge validation |
| `src/tally/tally-module.ts` | Pass rotation config to auditor |
| `test/helpers/tally-audit-test-helpers.ts` | **New** — test auditor factory |
| `test/helpers/sqlite-test-storage.ts` | Test config fields |
| `test/unit/tally/tally-request-audit-config.test.ts` | **New** |
| `test/unit/tally/tally-request-audit-rotation.test.ts` | **New** |
| `test/unit/tally/tally-request-auditor.test.ts` | Updated constructor helper |
| `test/unit/tally/tally-request-audit-privacy.test.ts` | Updated constructor helper |
| `test/unit/tally/tally-request-audit-recovery.test.ts` | Updated constructor helper |
| `test/unit/tally/audit-intent.test.ts` | Guard + rotation-failure integration |

### Tests added or changed

Focused B2a suites cover configuration validation, rotation threshold and count cap, concurrency/ordering, filesystem failure recovery, privacy on rotated files, and request-guard non-blocking behavior when rotation fails.

### Validation evidence

| Command | Result |
|---------|--------|
| `npm run lint` (connector) | **PASS** |
| `npm run build` (connector) | **PASS** |
| Focused audit suites (config, rotation, privacy, recovery, intent, auditor) | **51/51 PASS** |
| `test/architecture/module-boundaries.test.ts` | **12/12 PASS** |
| `test/unit/diagnostics/diagnostic-allowlist.test.ts` | **8/8 PASS** |
| `test/unit/tally/tally-request-guard.test.ts` | **2/2 PASS** |
| Full connector vitest | **656/656 PASS** (625 prior + 31 new B2a tests) |

### Deferred (not B2a)

Diagnostic-export cleanup (B2b); sync-run pruning (B2c); backup deletion; migration cleanup; temporary-file cleanup; encryption; installer cleanup; telemetry; remote logging; packaging changes; generic retention framework.

**Reliability Control #4 remains PARTIAL** — audit file growth bounded for `tally-request-audit.jsonl`; diagnostic-export cleanup, sync-run pruning, encryption, and broader retention gaps remain.

**Verdict (Phase B2a):** **SAFE TO COMMIT BOUNDED AUDIT ROTATION**

**Unrestricted production remains unapproved.**

---

## §26 — RC#4 Phase B2b: desktop diagnostic export retention (2026-07-25)

### Requirement addressed

Reliability Control #4 Phase B2b — enforce existing `diagnosticsRetentionDays` for app-owned desktop diagnostic export directories only.

### Prior defect

Diagnostic exports accumulated under `{userData}/diagnostics-exports/budcom-diagnostics-*` without lifecycle enforcement despite persisted `diagnosticsRetentionDays` (1–90 days; default 14 production / 7 development).

### Implementation status

**COMPLETE (desktop scope)** — focused `DiagnosticExportRetentionService`; startup and post-export invocation; no connector, sync, backup, encryption, or cloud lifecycle changes.

### Ownership boundary

Only directory names matching:

`^budcom-diagnostics-\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z$`

under `{userData}/diagnostics-exports/`. Each export directory contains `diagnostics-bundle.json`. Unrelated files, backup files, temp files, symbolic links, and non-owned directories are never deleted.

### Retention semantics

- Read `diagnosticsRetentionDays` from effective desktop config (existing settings boundary).
- Export age from dirname-encoded ISO timestamp; fallback to filesystem `mtimeMs`.
- Invalid/unparseable timestamps preserved conservatively.
- Sort eligible exports by timestamp descending, then directory name descending.
- **Always preserve the newest eligible export**, even when expired.
- Delete other eligible exports only when `now - exportTime > retentionDays`.
- Injectable clock via `nowMs` / `now()` for deterministic tests.

### Invocation points

1. **Startup:** `main.ts` → `runDiagnosticExportRetentionCleanup()` inside `app.whenReady()` after `lifecycleService.initialize()`; failures do not block startup.
2. **Post-export:** `DiagnosticsService.exportBundle()` after successful bundle write; failures do not fail the export.

### Failure behavior

Missing export directory → successful no-op. Unreadable directory → bounded warning log with `failureCount`. Individual deletion failures increment `failureCount` and continue. Lifecycle logs emit aggregate counts only (`diagnostics_export_retention_cleanup`); no private paths or bundle contents.

### Files changed

| File | Change |
|------|--------|
| `src/application/diagnostic-export-convention.ts` | **New** — export dir naming, ownership match, timestamp parse |
| `src/application/diagnostic-export-retention-service.ts` | **New** — retention cleanup service |
| `src/application/diagnostics-service.ts` | Convention helper; post-export cleanup hook |
| `src/main/main.ts` | Startup cleanup wiring |
| `test/unit/diagnostic-export-retention.test.ts` | **New** — unit tests |
| `test/integration/diagnostic-export-retention.test.ts` | **New** — integration tests |

### Explicit exclusions (still open)

- ~~`sync_runs` pruning (B2c)~~ — completed in §27
- Backup deletion / config backup lifecycle changes
- Cloud lifecycle
- Encryption at rest
- Generic user-file cleanup
- Connector audit rotation (completed in B2a)
- Production packaging lifecycle

### RC#4 status (post-B2b)

**Reliability Control #4 remains PARTIAL** — connector audit rotation (B2a), desktop diagnostic export retention (B2b), and sync-run history pruning (B2c) implemented; encryption, backup cleanup, and broader retention gaps remain.

### Validation evidence (B2b)

| Command | Result |
|---------|--------|
| `npm run lint` (desktop) | **PASS** |
| `npm run build` (desktop) | **PASS** |
| Desktop vitest | **124/124 PASS** (97 prior + 27 new B2b tests) |
| `npm run lint` (connector) | **PASS** (unchanged) |
| Connector vitest | **656/656 PASS** (unchanged) |

**Unrestricted production remains unapproved.**

---

## §27 — RC#4 Phase B2c: bounded `sync_runs` retention and pruning (2026-07-25)

### Requirement addressed

Reliability Control #4 Phase B2c — age- and count-bounded, TD-006-safe pruning of locally persisted `sync_runs` history only.

### Prior defect

`sync_runs` rows accumulated indefinitely; no `DELETE` path existed despite API history and recovery lineage dependencies.

### Policy (internal defaults — no UI)

| Setting | Default | Bounds | Env override |
|---------|---------|--------|--------------|
| `syncRunHistoryMaxCount` | **100** unprotected terminal rows per `(company_id, resource_kind)` | 20–500 | `BUDCOM_SYNC_RUN_HISTORY_MAX_COUNT` |
| `syncRunHistoryMaxAgeDays` | **90** days | 7–365 | `BUDCOM_SYNC_RUN_HISTORY_MAX_AGE_DAYS` |
| Delete batch cap | **100** rows per invocation (internal constant) | — | — |

Dual-trigger deletion (either trigger deletes an unprotected terminal row):

1. Terminal timestamp older than `maxAgeDays` (`now − terminal_at > period`), or
2. Row rank ≥ `maxCount` among unprotected terminal rows ordered by terminal timestamp DESC, `sync_run_id` DESC.

Terminal timestamp field order: `completed_at` → `updated_at` → `started_at`.

### Status policy

| Class | Statuses | Treatment |
|-------|----------|-----------|
| Terminal (prunable when unprotected) | `completed`, `failed`, `cancelled`, `interrupted` | Eligible after protection rules |
| Nonterminal (never pruned) | `running`, `cancelling`, `recovering` | Always protected |
| Unknown / not persisted | e.g. `idle` | Preserved if encountered |
| `interrupted` | — | Protected when retry-eligible (`findRetryPredecessor`), predecessor-referenced, or latest `failed`/`interrupted` in scope; otherwise terminal-prunable |

### Never deleted

- Nonterminal statuses (`running`, `cancelling`, `recovering`)
- Unknown statuses
- Retry-eligible `interrupted` runs (`findRetryPredecessor` semantics)
- Runs referenced as `predecessor_sync_run_id`
- Latest `completed` per scope (by terminal timestamp)
- Latest `failed` or `interrupted` per scope (support window)

Active runs **do not skip the scope** — unrelated eligible terminal rows in the same `(company_id, resource_kind)` scope are pruned when transactionally safe.

### Implementation

| File | Change |
|------|--------|
| `src/config/defaults.ts` | Retention constants + config fields |
| `src/config/index.ts` | Bounded env parsing + validation |
| `src/storage/sqlite/sync-run-retention-service.ts` | **New** — pure selection + batched `SyncRunRetentionService.prune()` |
| `src/storage/sqlite/sync-run-repository.ts` | Scope listing, predecessor refs, transactional delete |
| `src/storage/sqlite/storage-service.ts` | Startup prune after `recoverAllAbandonedRuns()` |
| `test/helpers/sync-run-retention-test-helpers.ts` | **New** |
| `test/unit/storage/sync-run-retention-selection.test.ts` | **New** — selection + batch unit tests (23) |
| `test/unit/storage/sync-run-retention-config.test.ts` | **New** — config tests (10) |
| `test/integration/sync-run-retention.test.ts` | **New** — TD-006, isolation, startup, fail-safe (9) |

### Explicit exclusions (still open)

- Backup deletion / config backup lifecycle changes
- Cloud lifecycle
- Encryption at rest
- Generic user-file cleanup
- Production packaging lifecycle
- Post-terminal / post-sync prune hook (startup-only by design)

### RC#4 status (post-B2c)

**Reliability Control #4 remains PARTIAL** — audit rotation (B2a), diagnostic export retention (B2b), and sync-run history pruning (B2c) implemented; encryption, backup cleanup, and broader retention gaps remain.

**Verdict (Phase B2c):** **SAFE TO COMMIT SYNC-RUN RETENTION PRUNING** (pending review — uncommitted)

### Validation evidence (B2c)

| Command | Result |
|---------|--------|
| `npm run lint` (connector) | **PASS** |
| `npm run build` (connector) | **PASS** |
| B2c suites (selection, config, integration) | **42/42 PASS** |
| `test/architecture/module-boundaries.test.ts` | **12/12 PASS** |
| Full connector vitest | **698/698 PASS** (656 prior + 42 new B2c tests) |
| Desktop lint/build/tests (unchanged scope) | **124/124 PASS** |

**Unrestricted production remains unapproved.**

---

## §28 — RC#4 Phase B2b-lite + B2-doc: config temp reconciliation and local lifecycle documentation (2026-07-25)

### Requirement addressed

1. **B2b-lite** — safely reconcile orphan `{userData}/desktop-config.json.tmp` at desktop startup without blocking load.
2. **B2-doc** — document ownership and lifecycle for manual/migration SQLite backups, legacy JSON copies, desktop config artifacts, and uninstall data boundaries.

### Prior defect

Atomic config saves write to `desktop-config.json.tmp` then rename. Process crash between temp write and rename left orphan temp files indefinitely with no reconciliation. Backup/config lifecycle ownership was scattered across code comments and partial ops docs.

### Implementation status

**COMPLETE (desktop B2b-lite + documentation)** — no connector sync semantic changes, no manual SQLite backup auto-deletion, no encryption or installer lifecycle work.

### B2b-lite temp reconciliation algorithm

Exact path: `{userData}/desktop-config.json.tmp` only (`isAppOwnedDesktopConfigTempPath`).

| Condition | Action |
|-----------|--------|
| Temp absent | No-op |
| Temp symlink or directory | Skip (preserve) |
| Primary valid | Delete temp (stale/invalid orphan) |
| Primary invalid/missing, temp valid | Best-effort archive corrupt primary → rename temp to primary |
| Primary invalid, temp invalid, backup valid | Delete invalid temp; store recovery uses backup |
| Primary, temp, backup all invalid | Preserve temp (uncertain) |
| Any operation failure | Aggregate warning log; startup continues |

Safety: `lstat` (no symlink follow); never delete backup, corrupt archives, primary (except promotion rename target), unrelated `.tmp` files.

### Startup integration

`main.ts`: after `LogService` init, before `DesktopConfigStore` construction → `runDesktopConfigTempReconciliation()` wrapped in try/catch (non-blocking).

### B2-doc deliverable

`docs/operations/local-data-lifecycle.md` — artifact table, restore/retention/uninstall ownership, explicit **no auto-delete for manual SQLite backups**, RC#4 closure assessment, controlled-pilot vs unrestricted-production status.

### Files changed

| File | Change |
|------|--------|
| `src/application/desktop-config-paths.ts` | `configTempPath`, ownership helpers |
| `src/application/desktop-config-temp-reconciliation-service.ts` | **New** — reconciliation service |
| `src/application/desktop-config-store.ts` | Use `configTempPath` |
| `src/main/main.ts` | Startup wiring; reorder log before config store |
| `test/unit/desktop-config-temp-reconciliation.test.ts` | **New** — unit tests (22) |
| `test/integration/desktop-config-temp-reconciliation.test.ts` | **New** — integration tests (2) |
| `docs/operations/local-data-lifecycle.md` | **New** — B2-doc lifecycle reference |

### Explicit exclusions (still open)

- Automatic deletion/retention for manual SQLite backups
- Corrupt config archive pruning
- Encryption at rest
- Generic user-file cleanup
- Production packaging / uninstall automation
- Cloud lifecycle

### RC#4 status (post-B2b-lite + B2-doc)

**Reliability Control #4 remains PARTIAL** — audit rotation (B2a), diagnostic export retention (B2b), config temp reconciliation (B2b-lite), sync-run pruning (B2c), and local lifecycle documentation (B2-doc) complete; encryption, manual backup auto-deletion, installer lifecycle, and broader retention gaps remain.

**Verdict (Phase B2b-lite + B2-doc):** **SAFE TO COMMIT LOCAL LIFECYCLE CLOSURE** (pending review — uncommitted)

### Validation evidence (B2b-lite + B2-doc)

| Command | Result |
|---------|--------|
| `npm run lint` (desktop) | **PASS** |
| `npm run build` (desktop) | **PASS** |
| Desktop vitest | **148/148 PASS** (124 prior + 24 new B2b-lite tests) |
| `npm run lint` (connector) | **PASS** (unchanged) |
| `npm run build` (connector) | **PASS** (unchanged) |
| Full connector vitest | **698/698 PASS** (unchanged) |
| Architecture tests | **12/12 PASS** (unchanged) |
| Tracked `apps/budcom_desktop/dist/**` after build | **Restored — no modified generated artifacts** |

**Unrestricted production remains unapproved.**

---

## §29 — RC#4 Enterprise Security and Local Data Protection Closure (2026-07-25)

### Requirement addressed

Repository-provable security hardening for controlled commercial pilot expansion: IPC/preload boundaries, local API request limits, path containment, XML byte caps, backup target validation, company-isolation test parity, dependency audit baseline, and explicit security decision records.

No Tally behaviour, sync semantics, retention, or backup auto-deletion changes.

### Gate evidence

No contradictory next-gate label exists in repository docs after §28 local-lifecycle closure. RC#4 remains **PARTIAL** with encryption, LAN auth (TD-009), and installer lifecycle explicitly deferred. This gate executes the user-authorized **Enterprise Security and Local Data Protection Closure** scope aligned with Accepted Control #5 and existing security audits.

### Implementation status

**COMPLETE (non-architectural hardening + documentation)** — encryption, LAN authentication platform, signing, and updater trust remain decision-record blockers only.

### Controls verified (pre-existing)

| Surface | Control |
|---------|---------|
| Electron | `contextIsolation`, `nodeIntegration: false`, `sandbox`, CSP, navigation/window-open blocked |
| IPC | Channel allowlist; typed preload bridge; main-process validation |
| Tally egress | Fail-closed policy; IMPORT/EXECUTE denied; forbidden registry; security-guard tests |
| SQL | Parameterized queries; sort allowlist; company-scoped sync-run lookup |
| XML | Custom parser (no external entities); DOCTYPE/CDATA rejected; depth/node limits |
| Network | Loopback default; `0.0.0.0` rejected; production LAN requires acknowledgement |
| Privacy | Diagnostic allowlist; log sanitization; audit metadata-only |

### Controls added

| Area | Change |
|------|--------|
| Desktop IPC | `assertBoundedIpcPayload`; `validateSyncOptions`; export path containment under owned diagnostics root; query length cap |
| Connector API | `express.json({ limit: '64kb' })`; JSON content-type middleware; 413 handler |
| Paths | `createBackup()` target must remain within `databasePath` |
| XML | `maxBytes` limit (1 MiB default) at parser entry |
| Tooling | `npm run audit` / `audit:prod` scripts (connector + desktop) |
| Documentation | `docs/security/security-decision-matrix.md` |

### Files changed

| File | Change |
|------|--------|
| `apps/budcom_desktop/src/application/ipc-allowlist.ts` | Payload/query/sync/export hardening |
| `apps/budcom_desktop/src/main/main.ts` | Bounded IPC + export root wiring |
| `apps/budcom_desktop/test/unit/ipc-allowlist-security.test.ts` | **New** |
| `connector/.../api/middleware/request-security.ts` | **New** |
| `connector/.../api/server.ts` | Body limit + content-type middleware |
| `connector/.../infrastructure/errors/error-handler.ts` | 413 handling |
| `connector/.../storage/sqlite/storage-service.ts` | Backup path containment |
| `connector/.../tally/xml/response-parser-limits.ts` | `maxBytes` |
| `connector/.../tally/xml/response-parser.ts` | Byte cap enforcement |
| `connector/.../test/integration/api-request-security.test.ts` | **New** |
| `connector/.../test/integration/ledger-api-negative.test.ts` | **New** |
| `docs/security/security-decision-matrix.md` | **New** |

### Explicit exclusions (owner authorization required)

- SQLite / log / backup encryption at rest
- TD-009 LAN authentication
- Code signing and auto-update trust
- Installer/uninstall automation
- Telemetry / crash reporting
- Inbound offline XML envelope unification (reliability order item 6)

### RC#4 status (post-enterprise security closure)

**Reliability Control #4 remains PARTIAL** — local lifecycle (B2) and non-architectural security hardening complete; encryption, LAN auth, signing, and installer lifecycle remain open.

### Validation evidence (§29)

| Command | Result |
|---------|--------|
| `npm run lint` (connector) | **PASS** |
| `npm run build` (connector) | **PASS** |
| Full connector vitest | **711/711 PASS** (698 prior + 13 new security tests) |
| Architecture tests | **12/12 PASS** |
| `npm run audit:prod` (connector) | **0 vulnerabilities** |
| `npm run lint` (desktop) | **PASS** |
| `npm run build` (desktop) | **PASS** |
| Desktop vitest | **161/161 PASS** (148 prior + 13 new security tests) |
| `npm run audit:prod` (desktop) | **0 vulnerabilities** |
| Tracked `apps/budcom_desktop/dist/**` after build | **Restored — no modified generated artifacts** |

**Unrestricted production remains unapproved.**

### Pre-commit security-gate corrections (2026-07-25)

Addressed provisional review findings without removing size protection or breaking supported workflows.

| Finding | Resolution |
|---------|------------|
| Global 1 MiB XML cap | Confirmed against `operation-registry.ts`: rich master collections use `RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES` (1 MiB); gateway enforces per-operation caps before parse; parser default imports same constant; parse call sites pass operation-specific `maxBytes` |
| Diagnostic export containment | Verified: preload/UI export takes no custom path — automatic exports only; IPC `targetDir` is internal-only and must remain under `{userData}/diagnostics-exports`; no user-selected export dialog exists yet |
| Symlink/junction escape | Added `path-containment.ts` (desktop + connector): prefix check → `realpath` root → `lstat` walk rejecting symlinks/junctions → parent-chain verify for non-existing targets |
| Protected overwrite | Backup rejects targets matching live DB, WAL, SHM via `assertNotProtectedWriteTarget`; timestamped backup filenames prevent live DB collision |
| Content-Type/body detection | `rejectMalformedContentLength` pre-parser; mutation middleware detects chunked TE, malformed Content-Length, and parsed non-empty bodies |
| Large valid XML tests | Near-cap padded envelope fixtures (~900 KiB+) prove sub-1 MiB legitimate responses parse; oversize rejection unchanged |

### Validation evidence (pre-commit corrections)

| Command | Result |
|---------|--------|
| `npm run lint` (connector) | **PASS** |
| `npm run build` (connector) | **PASS** |
| Full connector vitest | **727/727 PASS** |
| Architecture tests (`test/architecture/module-boundaries.test.ts`) | **12/12 PASS** |
| Contract tests (`tests/contract`) | **5/5 PASS** |
| `npm run audit:prod` (connector) | **0 vulnerabilities** |
| `npm run lint` (desktop) | **PASS** |
| `npm run build` (desktop) | **PASS** |
| Desktop vitest | **168/168 PASS** |
| `npm run audit:prod` (desktop) | **0 vulnerabilities** |
| Tracked `apps/budcom_desktop/dist/**` after build | **Restored — clean** |

---

## 30. RC#4 Unified Inbound XML Envelope and Offline Import Safety Closure (2026-07-25)

**Requirement addressed:** Reliability order item 6 — unified inbound XML envelope before untrusted voucher/import scope (`milestone-5b-stage-update.md` §687; deferred from §29 §1960; `security-decision-matrix.md` row 22)

**Implementation status:** Complete for approved connector offline/import boundary. Domain upsert from offline files remains future scope.

### Ingestion surface map

| Surface | Status |
|---------|--------|
| `OfflineXmlIngestionService` | Routed through `InboundXmlEnvelopeService` |
| `InboundXmlEnvelopeService.acceptBuffer/acceptFile` | Implemented |
| Watched-folder path rules | Framework only (no product watcher) |
| Desktop import UI | Not implemented |
| Connector HTTP XML upload | None |
| Live Tally read path | Unchanged |

### Controls added

| Area | Change |
|------|--------|
| Unified boundary | `InboundXmlEnvelopeService` + supporting modules under `src/ingestion/` |
| Path safety | Null-byte rejection, symlink/junction rejection, watched-root containment |
| File stability | Bounded size/mtime stability window before read |
| Encoding | UTF-8 + BOM only; SHA-256 fingerprint of original bytes |
| Prohibited constructs | DOCTYPE, ENTITY, IMPORT/EXECUTE rejection |
| Resource allowlist | Ledgers, stock items, ledger groups, company list |
| Company isolation | Missing/ambiguous/mismatch fail closed |
| Import history | SQLite schema v6 `xml_import_attempts` + transactional duplicate detection |
| DI wiring | `OfflineXmlIngestionService` receives repository when `LocalDatabase` is running |

### Files changed

| File | Change |
|------|--------|
| `connector/.../ingestion/inbound-xml-*.ts` | **New** unified envelope modules |
| `connector/.../ingestion/offline-xml-ingestion.service.ts` | Delegate to envelope service |
| `connector/.../storage/sqlite/schema.ts` | Schema v6 + `MIGRATION_006` |
| `connector/.../storage/sqlite/xml-import-attempt-repository.ts` | **New** |
| `connector/.../storage/sqlite/storage-service.ts` | Repository in bundle |
| `connector/.../bootstrap/register-services.ts` | Updated XmlImport factory |
| `connector/.../test/unit/ingestion/inbound-xml-envelope.test.ts` | **New** |
| `connector/.../test/unit/ingestion/inbound-xml-file-source.test.ts` | **New** |
| `connector/.../test/integration/xml-import-attempt-repository.test.ts` | **New** |
| `connector/.../test/unit/ingestion/offline-xml-ingestion.test.ts` | Updated |
| `docs/architecture/inbound-xml-envelope.md` | **New** |

### Known limitations

- Offline import does not yet upsert ledgers/stock into SQLite domain tables.
- Desktop watched-folder and manual file UI not built.
- Concurrent duplicate import race relies on SQLite `BEGIN IMMEDIATE`; domain upsert concurrency untested until persistence exists.

### RC#4 status (post inbound envelope closure)

**Reliability Control #4 remains PARTIAL** — inbound envelope unified; encryption, LAN auth, signing, installer lifecycle, and offline domain persistence remain open.

### Validation evidence (§30)

| Command | Result |
|---------|--------|
| `npm run lint` (connector) | **PASS** |
| `npm run build` (connector) | **PASS** |
| Full connector vitest | **784/784 PASS** (+57 inbound envelope/reservation tests) |
| Architecture tests | **12/12 PASS** |
| Contract tests (`tests/contract`) | **5/5 PASS** |
| `npm run audit:prod` (connector) | **0 vulnerabilities** |
| `npm run lint` (desktop) | **PASS** |
| `npm run build` (desktop) | **PASS** |
| Desktop vitest | **168/168 PASS** |
| `npm run audit:prod` (desktop) | **0 vulnerabilities** |
| Tracked `apps/budcom_desktop/dist/**` after build | **Restored — clean** |

**Unrestricted production remains unapproved.**
