# Milestone 3 — Controlled Validation Report

**Date:** 2026-07-22  
**Base checkpoint:** `v0.3.1-m2.1` (Milestone 2.1 Connection Hardening)  
**Company tested:** ESTIMATION (`estimation`)  
**Status:** **INCOMPLETE — stopped at entity 5 (Units)**  
**Commit/tag/push:** Not performed (awaiting approval)

---

## Executive Summary

Controlled live validation began with all M2.1 safety controls active (`safeMode=true`, concurrency=1, retry=1, 5s inter-request delay). **Entities 1–4 passed.** **Entity 5 (Units) failed** with a 120s timeout; TallyPrime HTTP became unresponsive afterward. Live testing stopped immediately per protocol. Entities 6–11 were **not tested**.

A fail-fast fix was applied: Units extractor now uses a **30s entity-level timeout** (down from 120s global) to reduce Tally lock-up risk. **Live re-validation requires Tally restart** before continuing.

---

## Safety Configuration (Live Run)

| Setting | Value |
|---------|-------|
| `VENTURE_TALLY_SAFE_MODE` | `true` |
| `VENTURE_TALLY_MIN_REQUEST_INTERVAL_MS` | `5000` |
| `VENTURE_TALLY_POOL_MAX` | `1` |
| `VENTURE_TALLY_RETRY_MAX` | `1` |
| Connector port | `8085` |
| Inter-entity wait | 5 seconds |

---

## Entity-by-Entity Results

| # | Entity | Status | Duration | Tally Raw Bytes | Records | Correlation ID | Circuit | Tally Alive After |
|---|--------|--------|----------|-----------------|---------|----------------|---------|-------------------|
| 1 | Ledger Groups | **PASS** | 5,113 ms | 17,966 | 28 | `0f0753f4-5c43-4c2e-a204-3eca53c0fbe4` | closed | Yes |
| 2 | Ledgers | **PASS** | 802 ms | 260,389 | 921 | `18c03a9c-170e-4160-bb2b-06d1a303f625` | closed | Yes |
| 3 | Stock Groups | **PASS** | 25 ms | 16,678 | 58 | `424477c8-68d0-4a4c-8b12-5e3ca4b5504d` | closed | Yes |
| 4 | Stock Categories | **PASS** | 17 ms | 1,506 | 0 | `37239ffa-60b8-467c-bd8c-a41119880d2c` | closed | Yes |
| 5 | Units | **FAIL** | 120,017 ms | — (timeout) | — | `fec501ed-bfc2-48fc-b830-0c950892e345` | closed | **No** |
| 6 | Godowns | **NOT RUN** | — | — | — | — | — | — |
| 7 | Cost Categories | **NOT RUN** | — | — | — | — | — | — |
| 8 | Cost Centres | **NOT RUN** | — | — | — | — | — | — |
| 9 | Voucher Types | **NOT RUN** | — | — | — | — | — | — |
| 10 | GST Registrations | **NOT RUN** | — | — | — | — | — | — |
| 11 | Stock Items | **NOT RUN** | — | — | — | — | — | — |

### Parsing & Normalization (Passed Entities)

| Entity | Parsing | Normalization | Notes |
|--------|---------|---------------|-------|
| Ledger Groups | OK | OK (5 page items) | 28 groups total |
| Ledgers | OK | OK (5 page items) | 921 ledgers; largest export so far (260KB) |
| Stock Groups | OK | OK (5 page items) | 58 groups |
| Stock Categories | OK | EMPTY (0 records) | Valid empty collection |

API pagination (`pageSize=5`) applied after full Tally export — Tally still receives full collection requests.

---

## Units Failure Analysis

### Failing Collection
**`List of Units`** — Export / Collection

### Saved Artifacts
- XML payload: [`m3-units-failure-payload.xml`](m3-units-failure-payload.xml)
- Live results JSON: [`m3-live-validation-results.json`](m3-live-validation-results.json)
- Connector log: terminal session 708404
- Audit trail: `connector/venture_connector/diagnostics/tally-request-audit.jsonl`

### Evidence
1. XML pre-validated: 367 bytes, valid ENVELOPE, standard Tally convention.
2. Request hung exactly 120,000 ms (global timeout) — no retry storm (reconnectAttempts=0).
3. Same collection timed out in prior crash investigation probe (after heavier load).
4. After timeout, `License Info` probe to Tally failed — HTTP server unresponsive.

### Likely Cause
**TallyPrime internal hang** on `List of Units` export for the ESTIMATION company — not malformed Venture XML. Prolonged 120s wait may worsen Tally instability.

### Fix Applied (Units Only)
- Added `requestTimeoutMs` to `EntityExtractorConfig`
- Units extractor: **30s fail-fast timeout** (via `connectionManager.exchange` → transport)
- Test: `test/unit/extraction/units-timeout.test.ts`

**Live re-test of Units not performed** — Tally requires manual restart first.

---

## Safety Observations

| Check | Result |
|-------|--------|
| Retry storm | **None** — reconnectAttempts remained 0 throughout |
| Circuit breaker | Remained **closed** (single failure, threshold=2) |
| Concurrency | **1** — confirmed via logs (sequential `Tally request prepared`) |
| 5s delay | Observed ~5–7s between entity requests |
| Correlation IDs | Logged for every request; no XML bodies in production logs |
| M2.1 controls | Active throughout passed entities |

---

## Automated Verification

| Command | Result |
|---------|--------|
| `npm run lint` | ✅ Pass |
| `npm run test` | ✅ **102/102** pass |
| `npm run build` | ✅ Pass |

### New Tests Added
- `test/unit/extraction/controlled-validation.test.ts` — XML validation for passed entities
- `test/unit/extraction/units-timeout.test.ts` — fail-fast timeout wiring

---

## Remaining Technical Debt

1. **In-memory pagination** — API `pageSize` does not reduce Tally export size; full collections always fetched.
2. **Units collection** — Tally hang unresolved; may need alternative extraction path or Tally-side fix.
3. **Untested entities (6–11)** — Godowns, Cost Categories/Centres, Voucher Types, GST, Stock Items not validated live.
4. **Stock Items** — Prior probe returned 434KB; must use extreme caution when Tally is restarted.
5. **Entity-level timeouts** — Only Units has fail-fast; similar hangs possible for Godowns etc. (same pattern in crash probe).
6. **M3 code uncommitted** — Working tree contains full extraction layer + partial validation.

---

## Is Milestone 3 Safe to Commit?

### Recommendation: **NO — not yet**

| Criterion | Status |
|-----------|--------|
| All 11 entities live-validated | ❌ 4/11 pass, 1 fail, 6 not run |
| Zero Tally instability | ❌ Tally unresponsive after Units |
| Full test suite | ✅ Expected pass (mock-based) |
| Safety controls verified live | ✅ For passed entities |
| Known crash collection mitigated | ⚠️ Partial (30s fail-fast only) |

### Suggested Next Steps
1. **Restart TallyPrime** and confirm `License Info` responds.
2. **Re-test Units only** with 30s fail-fast; confirm Tally stays alive on timeout.
3. If Units still hangs: mark as degraded/optional or investigate alternate Tally collection.
4. Continue entities 6–11 one at a time with 5s delays.
5. **Stock Items last** — monitor raw byte length; abort if >500KB or >60s.
6. Commit only after all entities PASS or documented acceptable degradations.

---

**Stopped — awaiting approval before commit, live continuation, or M3 resume.**
