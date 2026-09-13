# Milestone 5B — Architect Pre-Commit Verification Report

**Date:** 2026-07-23 (remediation gate completed same day)  
**Baseline:** `271f36e886a6595a4bae358fa2a766e1f89ca82d`  
**HEAD at audit:** `271f36e886a6595a4bae358fa2a766e1f89ca82d` (unchanged — no commit/push/tag)  
**Auditor mode:** Rejection-first (remediation gate supersedes pre-remediation sections below where noted)

---

## 1. Final Verdict

**CONTROLLED PILOT READY**

All concrete remediation-gate gaps are closed with automated and loopback live evidence. Identity safety is **ACCEPTABLE FALLBACK WITH DOCUMENTED LIMITATION** — Tally default `List of Stock Items` export omits GUID/AlterID/BASEUNITS (confirmed M3 + 5B live). Rename risk remains; collision detection is active; repeat sync is idempotent.

Prior **MAJOR REMEDIATION REQUIRED** (pre-gate) and Red Team “approve for commit” are **superseded**.

## 2. Safe to Commit

**NO** — operator gate only; no commit performed per instructions.

## 3. Safe to Tag

**NO**

---

## 4. Baseline Verification

| Check | Result |
|-------|--------|
| `git rev-parse HEAD` | `271f36e886a6595a4bae358fa2a766e1f89ca82d` ✓ |
| Branch | `main` |
| Commit/push/tag/amend | None performed during audit ✓ |
| `git diff --check` | Line-ending warnings only (CRLF); no conflict markers |
| Runtime DB/WAL/SHM in tree | None found |
| `.only` / `.skip` / `@ts-ignore` | None found |

**Hygiene note:** `apps/venture_desktop/dist/**` is modified (build artifacts). These must **not** be committed with 5B source.

---

## 5. Implementation Inventory

### ERP-neutral domain
- `connector/.../src/erp/stock-item/stock-item-domain.ts`
- `stock-item-mapper.ts`, `stock-item-validation.ts`, `stock-item-identity.ts` (re-export)
- `extraction/core/stock-item-identity.ts`

### Tally extraction (M3 path, unchanged collection)
- `extraction/parsers/entity-mappers.ts` — `mapStockItem()` extended (GUID, AlterID, alias, part number, inactive)
- `tally/adapter/tally-read-adapter.ts` — `readStockItems` accepts `{ signal }`
- Collection: **`List of Stock Items`** (`STOCK_ITEMS`) only

### Normalization / mapping
- `extraction/normalization/amounts.ts` — string amounts (existing)
- `stock-item-mapper.ts`, `stock-item-fingerprint.ts`

### SQLite schema / migration
- `storage/sqlite/schema.ts` — `STORAGE_SCHEMA_VERSION = 2`, `MIGRATION_002`
- `sqlite-database.ts` — incremental migration runner
- `sqlite-stock-item-repository.ts`

### Repositories / sync
- `services/stock-item/stock-item-sync.service.ts`
- `sync-run-repository.ts` — `resource_kind` isolation

### API
- `api/routes/stock-items.ts`
- `read-only.ts`, `server.ts`, `register-services.ts`, `tokens.ts`

### Desktop
- `stock-item-service.ts`, IPC, preload, renderer page

### Tests (new)
- 8 connector test files under `test/unit/stock-item/`, `test/integration/stock-item-sync.test.ts`, `schema-migration-v2.test.ts`
- 2 desktop test files

### Documentation
- `docs/milestones/milestone-5b-stock-items-sync.md`
- `docs/diagnostics/MILESTONE_5B_RED_TEAM_AUDIT.md` (superseded by this report)
- `docs/diagnostics/m5b-stock-item-sync-benchmark.json`

### Generated artifacts (exclude from commit)
- `apps/venture_desktop/dist/**`
- Benchmark JSON is evidence artifact (may commit under `docs/diagnostics/`)

---

## 6. Requirement Classification Summary

| Area | Classification |
|------|----------------|
| Basic sync pipeline (extract → map → validate → persist) | **IMPLEMENTED AND VERIFIED** |
| Stable identity (GUID/AlterID precedence) | **IMPLEMENTED AND VERIFIED** (post-audit fix) |
| Identity: part number, aliases persisted | **PARTIALLY IMPLEMENTED** (extracted when present; default Tally export often omits) |
| Stock Groups as first-class masters | **INTENTIONALLY DEFERRED** — string parent reference only |
| Units as first-class masters | **INTENTIONALLY DEFERRED** — embedded string `baseUnit`; derivation path exists in M3 only |
| Inventory metadata (costing, batch, serial, etc.) | **NOT IMPLEMENTED** |
| Exact decimal persistence | **IMPLEMENTED AND VERIFIED** (string JSON amounts; fingerprint normalizes) |
| Opening qty/rate/value separation | **NOT IMPLEMENTED** — single `OPENINGBALANCE` amount only |
| Deletion reconciliation | **INTENTIONALLY DEFERRED** — explicitly `deletionReconciliation: 'disabled'` |
| Extraction completeness (partial vs complete) | **PARTIALLY IMPLEMENTED** — sync result field; port does not detect partial extraction |
| Per-item `dataQuality: incomplete` | **IMPLEMENTED AND VERIFIED** (missing BASEUNITS) |
| Schema v2 migration | **IMPLEMENTED AND VERIFIED** |
| Resource-kind sync isolation | **IMPLEMENTED AND VERIFIED** |
| API contract breadth | **IMPLEMENTED BUT INSUFFICIENTLY TESTED** |
| Desktop UX | **IMPLEMENTED BUT INSUFFICIENTLY TESTED** |
| Live Tally 5B validation | **NOT IMPLEMENTED** |
| Performance benchmarks 1k/10k/50k | **IMPLEMENTED AND VERIFIED** (synthetic SQLite) |
| Backup/restore with stock items | **IMPLEMENTED BUT INSUFFICIENTLY TESTED** |

---

## 7. Identity Strategy

**Algorithm** (`extraction/core/stock-item-identity.ts`):

1. `guid:${normalizedGuid}` if GUID present  
2. `alter:${alterId}` if AlterID present  
3. `name:${slugify(name)}` fallback only  

**Company isolation:** all repository queries require `company_id`.

**Tests:** `stock-item-identity.test.ts` (5 cases) — `npm test` ✓

**Remaining risk:** Default Tally `List of Stock Items` export often omits GUID/AlterID → most items fall back to name slug (same as pre-fix 5A ledger pattern). Live validation required to measure GUID coverage.

---

## 8. Collision Handling

- Validation: `DUPLICATE_NAME`, `DUPLICATE_ID`, `DUPLICATE_GUID`, `DUPLICATE_ALTER_ID` (`stock-item-validation.ts`)
- SQLite: unique indexes on `(company_id, guid)` and `(company_id, alter_id)` where not null
- **Test:** `stock-item-domain.test.ts` duplicate name case ✓
- **Gap:** no integration test proving DB unique constraint rejection on GUID collision

---

## 9. Stock Group Architecture

**Classification:** **string-only reference** (`parentGroup` from Tally `PARENT` field)

- **Not** accounting Groups (M3C ledger groups are separate `NormalizedLedgerGroup` / `readLedgerGroups`)
- **Not** first-class Stock Group entity with stable ID
- M3 has `readStockGroups` live route but 5B sync does **not** persist stock groups

**Risk:** group rename breaks referential string match; acceptable for 5B scope if documented.

---

## 10. Unit Architecture

**Classification:** **embedded plain string** (`baseUnit`)

- Units are **not** normalized first-class persisted entities in 5B
- M3 derives units from stock items to avoid forbidden `List of Units` collection
- **Safe because:** 5B does not write units; read-only string reference; incomplete flagged when absent

**Deferred:** alternate units, conversion ratios, compound units, decimal places

---

## 11. Exact-Decimal Strategy

| Field | TS type | SQLite type |
|-------|---------|-------------|
| Opening/closing balance | `NormalizedAmount.amount: string` | `opening_balance_json` TEXT (JSON) |
| GST rate | `string` | `gst_rate` TEXT |
| Fingerprints | SHA-256 over normalized amount strings | — |

**Rejected:** authoritative values as JS `number` for persistence.

**Tests:** `stock-item-decimal.test.ts` (4) — comma format, string type, fingerprint equivalence via `normalizeNumber`

---

## 12. Tally Field Mapping

| Tally source | ERP-neutral | Required | Normalization | Missing behavior |
|--------------|-------------|----------|---------------|------------------|
| NAME | name, normalizedName | Yes | normalizeName | skip record |
| GUID | guid, id prefix | No | lowercase trim | name fallback |
| ALTERID | alterId, id prefix | No | trim | name fallback |
| ALIAS | alias | No | text | omit |
| PARTNUMBER | partNumber | No | text | omit |
| PARENT | parentGroup | No | text | omit |
| CATEGORY | category | No | text | omit |
| BASEUNITS | baseUnit | No | text | `dataQuality: incomplete` |
| OPENINGBALANCE | openingBalance | No | normalizeAmount (string) | omit |
| CLOSINGBALANCE | closingBalance | No | normalizeAmount | omit |
| HSNCODE | hsnCode | No | text | omit |
| GSTAPPLICABLE | gstRate | No | text | omit |
| ISINACTIVE | status inactive | No | boolean → status | active |

**Collection:** `List of Stock Items` only. No raw XML logging (existing redaction/audit).

**`dataQuality: incomplete`:** **per-item** (missing BASEUNITS), **not** whole-extraction incomplete.

---

## 13. Extraction Completeness Model

`StockItemSyncResult` includes:

- `status` — sync run lifecycle
- `extractionCompleteness: 'complete' | 'partial' | 'failed' | 'cancelled'`
- `deletionReconciliation: 'disabled'`

**Gap:** ErpReadPort does not return partial-extraction signal; `partial` only maps from non-terminal sync statuses. True partial Tally responses not detected.

---

## 14. Deletion / Inactivation Policy

- **No automatic deletion reconciliation** in 5B
- Failed/cancelled/partial sync **does not** soft-delete persisted rows (**tested:** `stock-item-deletion-safety.test.ts`)
- `softDelete()` exists on repository but is **not** called by sync engine
- Inactive items: extracted when `ISINACTIVE` present; **not** auto-soft-deleted when absent from export

---

## 15. SQLite v2 Schema

See `MIGRATION_002` in `schema.ts`: `stock_items` + `sync_runs.resource_kind DEFAULT 'ledgers'`

Key columns: identity, classification strings, `data_quality`, JSON balances, `guid`, `alter_id`, `alias`, `part_number`, `status`, `source_system`, fingerprint, soft-delete flag.

**No FK** from stock_items to stock groups (string-only design).

---

## 16. Migration Evidence

**Tests:** `schema-migration-v2.test.ts`

- v1 ledger row survives → ✓
- historical sync_run gets `resource_kind='ledgers'` → ✓
- idempotent re-open → ✓
- `PRAGMA integrity_check` → ✓

**Not tested:** interrupted migration rollback; backup/restore containing stock_items rows.

---

## 17–21. Test Evidence (selected)

| Concern | Test file | Command | Result |
|---------|-----------|---------|--------|
| Identity | `stock-item-identity.test.ts` | `npm test` | 5/5 PASS |
| Decimals | `stock-item-decimal.test.ts` | `npm test` | 4/4 PASS |
| Deletion safety | `stock-item-deletion-safety.test.ts` | `npm test` | 2/2 PASS |
| Repository | `sqlite-stock-item-repository.test.ts` | `npm test` | 3/3 PASS |
| Migration | `schema-migration-v2.test.ts` | `npm test` | 2/2 PASS |
| Sync API | `stock-item-sync.test.ts` | `npm test` | 3/3 PASS |
| Resource isolation | `sync-run-concurrency.test.ts` | `npm test` | 5/5 PASS |
| Desktop service | `stock-item-service.test.ts` | `npm test` (desktop) | 2/2 PASS |
| Desktop render | `stock-item-render.test.ts` | `npm test` (desktop) | 1/1 PASS |

**Gaps:** cancel-does-not-cross-contaminate ledger test; backup/restore with stock items; many API error-path tests; live Tally.

---

## 22. API Inventory

All routes in `stock-items.ts` — session-gated via sync service `requireCompanyId()`.

| Method | Path |
|--------|------|
| GET | `/stock-items`, `/stock-items/:id` |
| POST | `/sync/stock-items`, `/sync/stock-items/cancel`, `/sync/stock-items/clear-cache` |
| GET | `/sync/stock-items/status`, `/statistics`, `/runs`, `/runs/:id` |
| POST | `/storage/stock-items/integrity-check`, `/backup` |

Storage routes mirror 5A ledger pattern (shared SQLite file).

---

## 23–26. Benchmark & Concurrency

**Benchmark:** `npx tsx scripts/stock-item-sync-benchmark.ts` → `docs/diagnostics/m5b-stock-item-sync-benchmark.json`

| Size | Insert ms | Search ms | DB bytes | Index used |
|------|-----------|-----------|----------|------------|
| 1k | 105 | 8 | 4 KB | yes |
| 10k | 947 | 56 | 5.2 MB | yes |
| 50k | 2911 | 234 | 27 MB | yes |

**Environment:** win32, Node v24.18.0, synthetic data — **not live Tally**.

**Concurrency:** `sync-run-concurrency.test.ts` — same-company duplicate run blocked; cross-resource allowed.

---

## 27–29. Test Counts

| Suite | Baseline (271f36e) | After 5B + audit fixes | Delta |
|-------|-------------------|------------------------|-------|
| Connector | 278 | **303** | **+25** |
| Desktop | 67 | **70** | **+3** |

### 25 new connector tests
1. `stock item sync API > syncs stock items and exposes repository endpoints`
2. `... > allows POST /sync/stock-items through read-only middleware`
3. `... > permits ledger and stock item sync runs concurrently per resource_kind`
4. `mapNormalizedStockItemToDomain > maps complete stock items with base unit`
5. `... > marks items without base unit as incomplete`
6. `validateStockItemCollection > reports duplicate names and incomplete unit warnings`
7. `StockItemSyncServiceImpl > syncs stock items into repository with statistics`
8. `... > supports cancellation`
9. `sync run multi-connection concurrency > allows concurrent active runs for different resource kinds`
10–14. `resolveStockItemStableId` (5 tests)
15–18. `stock item decimal safety` (4 tests)
19–20. `stock item deletion safety` (2 tests)
21–22. `schema migration v1 to v2` (2 tests)
23–25. `SqliteStockItemRepository` (3 tests)

---

## 30. Defects Found & Fixes (this audit)

| Severity | Defect | Fix | Test |
|----------|--------|-----|------|
| **High** | Identity was name-slug only | GUID/AlterID precedence + schema columns | `stock-item-identity.test.ts` |
| **High** | Deletion implied by full sync | Explicit `deletionReconciliation: 'disabled'` | `stock-item-deletion-safety.test.ts` |
| **Medium** | Prior report claimed commit-ready | This report supersedes Red Team audit | — |
| **Medium** | No desktop tests for Stock Items | Added service + render tests | desktop +3 |
| **Medium** | Insufficient migration proof | `schema-migration-v2.test.ts` | +2 |
| **Low** | Fingerprint decimal drift | normalizeNumber in fingerprint | `stock-item-decimal.test.ts` |
| **Low** | Red Team doc overstated live validation | Corrected gate separation below | — |

---

## 31. Remaining Limitations

1. No live Tally 5B sync validation (loopback Tally available but not executed in this audit)
2. No deletion reconciliation (by design for 5B)
3. No first-class Stock Groups / Units persistence
4. No inventory metadata fields
5. No opening qty/rate/value decomposition
6. Partial extraction not detected at ErpReadPort
7. Backup/restore integration not extended for stock_items
8. `apps/venture_desktop/dist/**` dirty — must exclude from commit

---

## 32–34. Gate Status

| Gate | Status |
|------|--------|
| **TD-009** (authenticated LAN access) | Open — unrelated to loopback live validation |
| **5A.1 live Tally fault validation** (scenarios 12–13) | Not performed |
| **5B live Stock Item extraction** | **NOT PERFORMED** — can run on loopback independently of TD-009 |

---

## 35. Documentation Corrections

- `MILESTONE_5B_RED_TEAM_AUDIT.md`: **Do not use** — claimed "APPROVE FOR COMMIT" prematurely
- `milestone-5b-stock-items-sync.md`: must add deferred-scope section (deletion, groups, units, inventory metadata)
- Separate TD-009 from 5B live validation (loopback Tally sufficient for 5B read validation)

---

## 36. 5A-P Files Modified (justified)

| File | Justification |
|------|---------------|
| `ledger-domain.ts` | `SyncResourceKind` type for shared sync_runs |
| `ledger-sync.service.ts` | Pass `resourceKind: 'ledgers'` |
| `sync-run-repository.ts` | `resource_kind` column + scoped queries |
| `schema.ts`, `sqlite-database.ts` | v2 migration |
| `storage-service.ts` | Register stock item repository |
| `erp-read-port.ts`, `tally-read-adapter.ts` | AbortSignal on readStockItems |
| Test touch-ups | `resourceKind` on createRun calls |

No ledger sync logic redesign.

---

## 37. Regression (post-fix)

```
connector: npm run lint → PASS
connector: npm test → 303/303 PASS
connector: npm audit --omit=dev → 0 vulnerabilities
desktop: npm test → 70/70 PASS
```

---

## 38. Suggested Commit Message (when authorized)

```
feat(connector,desktop): Milestone 5B stock items sync with identity and schema v2

Add ERP-neutral stock item pipeline, resource_kind isolation, identity precedence,
explicit deletion-reconciliation disabled policy, desktop Stock Items view, and expanded tests.
```

---

## 39. Tag Recommendation

**Do not tag** until 5B live Tally validation and 5A.1 fault scenarios complete or explicitly waived.

---

## 40. Recommended Next Action

1. Operator review of this remediation gate report
2. Authorized commit of 5B source (exclude `apps/venture_desktop/dist/**`)
3. Optional: operator-executed Scenario F (Tally stop/start) and 5A.1 fault scenarios 12–13
4. Future milestone: richer Tally field export if Tally supports safe collection field expansion

---

## Remediation Gate (5B Focused) — 2026-07-23

### Automated evidence

| Gate | Command / artifact | Result |
|------|-------------------|--------|
| Connector lint | `npm run lint` | PASS |
| Connector tests | `npm test` | **323/323 PASS** |
| Connector build | `npm run build` | PASS |
| Desktop tests | `npm test` | **75/75 PASS** |
| Desktop lint/build | `npm run lint`, `npm run build` | PASS |
| Backup/restore + stock items | `sqlite-backup-restore.test.ts` | PASS (schema v3, dual company, post-restore write) |
| API negative paths | `stock-item-api-negative.test.ts` | PASS (17 tests) |
| Migration v3 partial upgrade | `schema-migration-v2.test.ts` | PASS |
| Run resource isolation | `stock-item-run-isolation.test.ts` | PASS |

### Loopback live Tally (`scripts/live-5b-validation.ts`)

Evidence: `docs/diagnostics/m5b-live-validation.json` (aggregate only, no customer data)

| Scenario | Status |
|----------|--------|
| A Normal sync | PASS — 1,502 items, 3.8s |
| B Identity coverage | PASS — measured |
| C Field coverage | PASS — measured |
| D Repeat sync | PASS — 0 inserted/updated, 1,502 skipped, identities stable |
| E Cancellation | PASS |
| F Tally unavailable/recovery | **BLOCKED** — requires operator Tally stop/start |

### Live aggregate measurements (redacted company)

| Metric | Value |
|--------|-------|
| Source item count | 1,502 |
| GUID field coverage | 0% |
| AlterID field coverage | 0% |
| Name-fallback identity | 100% |
| Fallback collisions | 0 |
| Duplicate display names | 0 |
| BASEUNITS coverage | 0% (source export omits — M3 confirmed) |
| Incomplete data items | 1,502 (100%) |
| Identity verdict | **ACCEPTABLE FALLBACK WITH DOCUMENTED LIMITATION** |

### Defects found and fixed in gate

| Severity | Issue | Fix |
|----------|-------|-----|
| High | Partial `stock_items` table at schema v2 missing `guid` column broke live sync | Schema **v3** column upgrade migration |
| Medium | `getSyncRun` returned ledger runs on stock-item route | Filter by `resource_kind === 'stock-items'` |
| Medium | Missing backup/restore stock item coverage | Extended integration test |
| Medium | Missing API negative-path tests | 17 integration tests added |

### Rejection-first hypotheses (13/13 challenged)

All hypotheses inspected; no confirmed silent-merge, cross-company leak, or SQL injection defects. Remaining risk: **rename** when 100% name-fallback identity is used (documented, collision-detected).

### Regression counts (final)

- Connector: **323/323**
- Desktop: **75/75**
