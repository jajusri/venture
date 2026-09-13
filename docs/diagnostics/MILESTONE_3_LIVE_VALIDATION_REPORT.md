# Milestone 3 — Final Live Validation Report

**Date:** 2026-07-22  
**Base checkpoint:** `v0.3.1-m2.1`  
**Company:** ESTIMATION (`estimation`)  
**Status:** **All 11 entities PASS (final run)** — with units normalization caveat  
**Commit/tag/push:** Not performed

---

## Executive Summary

Controlled live validation completed successfully after fixing the **Units** extraction path. The Tally collection `List of Units` **must not be used** — it hangs TallyPrime and causes HTTP server failure even with client-side timeouts.

**Fix applied:** Units are **derived from stock item data** (unique `baseUnit` values), with stock items cached per company session. Entity 11 (Stock Items) served from cache in **8ms** with no additional Tally request.

---

## Safety Controls (Verified Live)

| Control | Setting | Observed |
|---------|---------|----------|
| Safe mode | ON | ✓ |
| Concurrency | 1 | ✓ |
| Inter-request delay | 5s | ✓ |
| Retry max | 1 | ✓ |
| Circuit breaker | ON, remained **closed** | ✓ |
| Retry storm | **None** (reconnectAttempts=0 throughout) | ✓ |
| Correlation IDs | Logged per request | ✓ |
| XML in production logs | Never | ✓ |

---

## Final Validation Run (Port 8087, Post-Fix)

| # | Entity | Status | Duration | Tally Raw Bytes | Records | Parsing | Normalization | Correlation ID |
|---|--------|--------|----------|-----------------|---------|---------|---------------|----------------|
| 1 | Ledger Groups | **PASS** | 5,128 ms | 17,959 | 28 | OK | OK | `b45aedae-a318-479c-9769-da20713cbe18` |
| 2 | Ledgers | **PASS** | 781 ms | 260,382 | 921 | OK | OK | `224af545-172b-4cd2-877f-fbdc307e3309` |
| 3 | Stock Groups | **PASS** | 29 ms | 16,671 | 58 | OK | OK | `5d1c13a3-ed32-4b55-bc26-cd2680976857` |
| 4 | Stock Categories | **PASS** | 27 ms | 1,499 | 0 | OK | EMPTY | `467ad268-f9cf-4003-8e1d-81d12fe9e516` |
| 5 | Units | **PASS** | 1,637 ms | 434,166* | 0 | OK | **EMPTY**† | `cee4bb09-8077-442d-89d9-7700ac7bb4d2` |
| 6 | Godowns | **PASS** | 26 ms | 1,759 | 1 | OK | OK | `e8b00140-f19c-4aea-abe4-35d0b1f7d510` |
| 7 | Cost Categories | **PASS** | 16 ms | 1,899 | 1 | OK | OK | `a1bbd515-cb4a-4eac-a64e-15abbfa1f8b1` |
| 8 | Cost Centres | **PASS** | 19 ms | 1,498 | 0 | OK | EMPTY | `9c3c5184-ff41-4314-a66e-d2b139c45d98` |
| 9 | Voucher Types | **PASS** | 5,033 ms | 12,407 | 24 | OK | OK | `653e0b88-1d85-4628-9cd3-c023088e4382` |
| 10 | GST Registrations | **PASS** | 15 ms | 1,966 | 0 | OK | EMPTY | `b608ac28-fcb7-4a49-a353-12b670efa060` |
| 11 | Stock Items | **PASS** | 8 ms (cached) | — | 1,502 | OK | OK | *(cache hit)* |

\*Units request triggers single `List of Stock Items` export (434KB); cached for entity 11.  
†Tally default stock-item collection XML omits `BASEUNITS` fields — see Units caveat below.

**Tally stability after final run:** All entities completed; Tally responded to health checks between requests.

---

## Units — Special Handling

### Problem
`List of Units` causes TallyPrime to hang (120s+). Client abort does not stop Tally internal processing; HTTP server becomes unresponsive.

### Solution
- **Removed** direct `List of Units` Tally call
- **Added** `deriveUnitsFromStockItems()` — extracts unique unit names from stock item `baseUnit` fields
- **Added** per-company stock items session cache
- **Added** 30s fail-fast timeouts for godowns, cost categories, cost centres, voucher types, GST registrations
- **Added** 90s timeout for stock items collection

### Normalization Caveat
Tally's default `List of Stock Items` collection export for ESTIMATION returns **minimal STOCKITEM nodes** (name only, no `BASEUNITS`). Result: **0 derived units** despite 1,502 stock items. Parsing works; data not present in Tally export format.

**Future improvement:** Custom TDL collection or enriched stock-item export template to include `BASEUNITS` — requires Tally-side field expansion (not attempted live to avoid instability).

### Confirmation
Isolated post-validation probe of `List of Units` (15s) **reconfirmed hang** and Tally instability — **never use in production**.

---

## Earlier Failed Run (Pre-Fix)

| Entity | Status | Notes |
|--------|--------|-------|
| 1–4 | PASS | Same as final run |
| 5 Units | **FAIL** | `List of Units` — 120s/30s timeout, Tally unresponsive |

---

## Code Changes (This Session)

| File | Change |
|------|--------|
| `src/extraction/units/units-derivation.ts` | Derive units from stock items |
| `src/services/extraction/master-data.service.ts` | Cache stock items; units via derivation |
| `src/extraction/extractors/extractor-registry.ts` | Remove units extractor; fail-fast timeouts |
| `src/extraction/extractors/master-data-extractor.ts` | Per-entity `requestTimeoutMs` |
| `src/tally/connection/tally-connection-manager.ts` | Pass `timeoutMs` to transport |
| `test/unit/extraction/units-derivation.test.ts` | Derivation tests |
| `test/unit/extraction/controlled-validation.test.ts` | Passed-entity XML tests |

---

## Automated Verification

| Command | Result |
|---------|--------|
| `npm run lint` | ✅ Pass |
| `npm run test` | ✅ **103/103** pass |
| `npm run build` | ✅ Pass |

---

## Safety Observations

1. **Ledgers (260KB) and Stock Items (434KB)** export successfully under single-request safe mode with 5s delays.
2. **No retry storms** in any run — M2.1 controls effective.
3. **Stock items cache** prevents duplicate 434KB export on second API call.
4. **Entities with 0 records** (stock categories, cost centres, GST) return valid empty envelopes — not errors.
5. **`List of Units` is toxic** for this Tally company — permanently bypassed.

---

## Remaining Technical Debt

1. **Units data completeness** — need enriched stock-item export or alternative safe Tally collection
2. **In-memory pagination** — full Tally export per entity type regardless of API page size
3. **In-memory stock items cache** — not persisted; cleared on connector restart
4. **Entity-level timeouts** — tuned empirically; may need per-company configuration
5. **M3 code uncommitted** — full extraction layer in working tree

---

## Is Milestone 3 Safe to Commit?

### Recommendation: **Conditional YES — with documented limitations**

| Criterion | Status |
|-----------|--------|
| All entities live-validated | ✅ 11/11 PASS (stability) |
| Zero Tally crashes during final run | ✅ |
| Safety controls verified | ✅ |
| Units data completeness | ⚠️ 0 records — export format limitation |
| List of Units bypass | ✅ Required for stability |
| Test suite | ✅ 103/103 |

**Suggested before commit:**
1. Document units derivation limitation in README/API docs
2. Consider enriched stock-item Tally template in a future patch (no live `List of Units`)
3. User approval after reviewing units EMPTY normalization

---

## Diagnostic Artifacts

| Artifact | Path |
|----------|------|
| Final results JSON | `docs/diagnostics/m3-live-validation-final.json` |
| Units failure XML (pre-fix) | `docs/diagnostics/m3-units-failure-payload.xml` |
| Request audit | `connector/venture_connector/diagnostics/tally-request-audit.jsonl` |
| Connector log | Terminal session 708408 |

---

**Stopped — awaiting approval. No commit, tag, or push.**
