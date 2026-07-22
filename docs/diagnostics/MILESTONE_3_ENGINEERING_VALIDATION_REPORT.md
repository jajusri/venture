# Milestone 3 — Engineering Validation Report

**Date:** 2026-07-22  
**Base checkpoint:** `v0.3.1-m2.1`  
**Session status:** **Live validation BLOCKED — Tally not running**  
**Commit / tag / push:** Not performed  
**Milestone 4:** Not started

---

## Executive Summary

This session attempted to continue controlled live validation per your authorization. **TallyPrime is not reachable** on `localhost:9000` (no process listening, `License Info` probe timed out at 15–20s). Live testing **stopped immediately** per protocol.

**Offline validation completed successfully:** all 11 XML templates pass structural validation; `npm run lint`, `npm run test` (103/103), and `npm run build` all pass.

A **prior controlled live run today (port 8087, post-units fix) completed all 11 entities with PASS** and zero retry storms. Results are included below as the best available live evidence until Tally is restarted for a confirmation run.

---

## Current Session — Tally Availability

| Check | Result | Time (UTC) |
|-------|--------|------------|
| `License Info` probe #1 | **TIMEOUT** (15s) | ~11:51 |
| `License Info` probe #2 | **TIMEOUT** (20s) | ~11:52 |
| Port 9000 listening | **No** | — |
| Tally process | **Not found** | — |

**Likely cause:** TallyPrime HTTP server offline or hung from earlier `List of Units` exposure during investigation. **Action required:** Start TallyPrime manually, open ESTIMATION company, confirm HTTP server on port 9000, then re-run live validation.

---

## Mandatory Safety Rules — Configuration

When live validation resumes, use:

```powershell
$env:BUDCOM_CONNECTOR_PORT="8087"
$env:BUDCOM_TALLY_SAFE_MODE="true"
$env:BUDCOM_TALLY_MIN_REQUEST_INTERVAL_MS="5000"
$env:BUDCOM_TALLY_POOL_MAX="1"
$env:BUDCOM_TALLY_RETRY_MAX="1"
$env:BUDCOM_TALLY_CIRCUIT_BREAKER="true"
```

| Rule | Enforcement |
|------|-------------|
| Read-only | Export/Collection requests only; no Import/Execute write paths |
| Concurrency = 1 | Safe mode + pool max 1 |
| 5s between requests | `tallyMinRequestIntervalMs=5000` |
| No parallel requests | Guard single-flight mutex |
| Circuit breaker | Enabled; observed CLOSED in prior run |
| No `List of Units` | **Permanently bypassed** — hangs Tally |

---

## Prior Successful Live Run (2026-07-22, Port 8087)

All entities validated sequentially against ESTIMATION company. **Safe mode active throughout.**

| # | Entity | Status | Duration | Tally Raw Bytes | Records | Parse | Normalize | Correlation ID |
|---|--------|--------|----------|-----------------|---------|-------|-----------|----------------|
| 1 | Ledger Groups | **PASS** | 5,128 ms | 17,959 | 28 | OK | OK | `b45aedae-…` |
| 2 | Ledgers | **PASS** | 781 ms | 260,382 | 921 | OK | OK | `224af545-…` |
| 3 | Stock Groups | **PASS** | 29 ms | 16,671 | 58 | OK | OK | `5d1c13a3-…` |
| 4 | Stock Categories | **PASS** | 27 ms | 1,499 | 0 | OK | EMPTY | `467ad268-…` |
| 5 | Units | **PASS** | 1,637 ms | 434,166* | 0 | OK | **EMPTY**† | `cee4bb09-…` |
| 6 | Godowns | **PASS** | 26 ms | 1,759 | 1 | OK | OK | `e8b00140-…` |
| 7 | Cost Categories | **PASS** | 16 ms | 1,899 | 1 | OK | OK | `a1bbd515-…` |
| 8 | Cost Centres | **PASS** | 19 ms | 1,498 | 0 | OK | EMPTY | `9c3c5184-…` |
| 9 | Voucher Types | **PASS** | 5,033 ms | 12,407 | 24 | OK | OK | `653e0b88-…` |
| 10 | GST Registrations | **PASS** | 15 ms | 1,966 | 0 | OK | EMPTY | `b608ac28-…` |
| 11 | Stock Items | **PASS** | 8 ms | *(cached)* | 1,502 | OK | OK | cache hit |

\*Units triggers one `List of Stock Items` export (434 KB); cached for entity 11.  
†Tally default stock-item collection XML omits `BASEUNITS` — 0 units derived from 1,502 items.

### Safety Confirmations (Prior Run)

| Check | Result |
|-------|--------|
| Retries | **0** reconnect attempts throughout |
| Circuit breaker | **CLOSED** all entities |
| Safe mode | **Active** (logged on every request) |
| Tally stability | Stable through entity 11 |
| Stock Items last | 8 ms cache hit — no second 434 KB export |

### Memory Observations

- Connector Node process remained stable; no OOM or runaway heap during ~95s validation window.
- Largest Tally payloads: Ledgers (260 KB), Stock Items (434 KB) — within 10 MB response cap.
- **Never probe `List of Units`** — confirmed hang even at 15s; kills Tally HTTP server.

---

## Entity-Specific Notes

### Units (Entity 5)
- **Does not call** `List of Units` (Tally hang/crash).
- Derives units from stock item `baseUnit` fields via `deriveUnitsFromStockItems()`.
- Side effect: first units request performs stock items export (cached).
- **Normalization EMPTY** for ESTIMATION: Tally returns minimal STOCKITEM nodes without `BASEUNITS`.

### Stock Items (Entity 11 — Last)
- Served from session cache after units request — 8 ms, no additional Tally call.
- Full collection export unavoidable with current Tally XML API (no native chunking).
- API `pageSize=5` paginates in-memory only.
- Abort thresholds used in validation script: >90s duration or Tally unresponsive.

### Empty Collections (Valid)
- Stock Categories, Cost Centres, GST Registrations returned 0 records — valid empty envelopes.

---

## XML Pre-Validation (Offline, This Session)

All templates validated via `scripts/validate-m3-xml.ts`:

| Entity | Collection ID | Bytes | Valid |
|--------|---------------|-------|-------|
| Ledger Groups | List of Groups | 369 | ✓ |
| Ledgers | List of Ledgers | 371 | ✓ |
| Stock Groups | List of Stock Groups | 381 | ✓ |
| Stock Categories | List of Stock Categories | 389 | ✓ |
| Units template‡ | List of Units | 367 | ✓ (not used live) |
| Godowns | List of Godowns | 371 | ✓ |
| Cost Categories | List of Cost Categories | 387 | ✓ |
| Cost Centres | List of Cost Centres | 381 | ✓ |
| Voucher Types | List of Voucher Types | 383 | ✓ |
| GST Registrations | List of GST Registrations | 391 | ✓ |
| Stock Items | List of Stock Items | 379 | ✓ |

‡Live units path uses stock-items derivation, not this template.

---

## Automated Verification (This Session)

| Command | Result |
|---------|--------|
| `npm run lint` | ✅ Pass |
| `npm run test` | ✅ **103/103** pass |
| `npm run build` | ✅ Pass |

### Test Coverage Added for M3 Safety

- `test/unit/extraction/controlled-validation.test.ts` — XML validation for passed entities
- `test/unit/extraction/units-derivation.test.ts` — units derivation logic
- `test/integration/tally-safety.test.ts` — safe mode, circuit breaker, no retry storm
- `test/integration/master-data.test.ts` — all extraction routes (mocked)

---

## Code Changes (Uncommitted, M3 Working Tree)

| Area | Summary |
|------|---------|
| `src/extraction/` | Normalization, templates, parsers, extractors |
| `src/extraction/units/units-derivation.ts` | Safe units path (no List of Units) |
| `src/services/extraction/` | Master data service, company resolver, stock items cache |
| `src/api/routes/master-data.ts` | 11 read-only endpoints + diagnostics |
| Fail-fast timeouts | 30s (godowns, cost*, voucher, GST), 90s (stock items) |

---

## Remaining Technical Debt

1. **Units data completeness** — need enriched stock-item export or alternate safe collection
2. **In-memory pagination** — API page size does not reduce Tally export size
3. **Stock items chunking** — not supported by Tally collection API; full export required
4. **Confirmation live run** — blocked until Tally restart
5. **M3 uncommitted** — awaiting approval

---

## Is Milestone 3 Safe to Commit?

| Criterion | Status |
|-----------|--------|
| All 11 entities live-validated (latest session) | ❌ Blocked — Tally down |
| All 11 entities live-validated (prior run today) | ✅ PASS (stability) |
| Safety controls verified live | ✅ |
| Units normalization complete | ⚠️ EMPTY (0 records) |
| Automated tests | ✅ 103/103 |
| No Tally crash in final prior run | ✅ |

**Recommendation:** **Conditional approval** — commit after (1) Tally restart + confirmation re-run, (2) documenting units EMPTY limitation, (3) your explicit approval.

---

## Resuming Live Validation

1. Start TallyPrime and open ESTIMATION company.
2. Confirm: `Invoke-WebRequest http://localhost:9000` with `License Info` XML returns 200 within 5s.
3. Build and start connector with safety env vars above.
4. Run sequential validation (5s pause between entities); **never** call `List of Units` directly.
5. Stop immediately on any timeout, circuit OPEN, or Tally unresponsive.

Diagnostic artifacts: `docs/diagnostics/m3-live-validation-final.json`, `connector/budcom_connector/diagnostics/tally-request-audit.jsonl`

---

**Stopped — Tally unavailable for live testing. Awaiting Tally restart and your approval before any repository commits.**
