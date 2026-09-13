# Milestone 3 — Repeat Live Validation Report

**Date:** 2026-07-22  
**Run type:** Confirmation repeat (authorized)  
**Status:** **BLOCKED — full 11-entity live run not completed**  
**Commit/tag/push:** Not performed (per instructions)

---

## Executive Summary

This confirmation run **stopped before the full 11-entity sequential validation** could execute. TallyPrime became unresponsive during the **Units investigation** (after a successful fresh `List of Stock Items` collection fetch). Per protocol, no further live Tally requests were sent.

**Key findings:**

| Area | Result |
|------|--------|
| Full repeat validation (entities 1–11) | **BLOCKED** — Tally hung |
| Units investigation | **Complete** — root cause confirmed |
| Fresh stock items live request | **Done** — 1,502 items, 434 KB, 646 ms, not from cache |
| Units derivation | **WARNING / INCOMPLETE** — 0 units; export limitation, not parser bug |
| `List of Units` | **Not called** |
| Tally crashes (`c0000005`) | **0** this session |
| Tally hangs | **1** (after object-export probe) |
| `npm run lint` / `test` / `build` | **All pass** (103/103 tests) |
| Safe to commit M3 | **No** — requires Tally restart + full repeat run |

---

## Safety Controls

All controls were configured for the planned run (same as prior successful run):

| Control | Setting |
|---------|---------|
| Read-only requests | ✅ |
| Safe Mode | ✅ (`VENTURE_TALLY_SAFE_MODE=true`) |
| Circuit Breaker | ✅ |
| Max concurrency | 1 (`VENTURE_TALLY_POOL_MAX=1`) |
| Inter-request delay | 5 s |
| Parallel calls | None |
| Auto-retry on failure | Disabled (`VENTURE_TALLY_RETRY_MAX=1`) |
| `List of Units` | **Never called** |

---

## Repeat Run Timeline

| Time (UTC) | Event | Outcome |
|------------|-------|---------|
| ~12:08 | `License Info` probe | ✅ Tally OK (200, 148 bytes) |
| ~12:09 | Fresh `List of Stock Items` collection fetch | ✅ 434,170 bytes, 646 ms, 1,502 items |
| ~12:09 | Raw XML unit-field scan | ❌ Zero `BASEUNITS` / `ADDITIONALUNITS` / `UOM` in export |
| ~12:10 | `Stock Item` object export probe (1 item) | ❌ 30 s timeout |
| ~12:11+ | Recovery probes (`License Info`) | ❌ Timeout / connection closed |
| ~12:12 | **STOP** — no connector entities 1–11 executed | Per instability protocol |

**Note:** The object-export probe was attempted to determine whether unit fields appear in single-item exports. It caused Tally instability and will **not** be repeated. The collection-export evidence is sufficient for the Units conclusion.

---

## First Run vs Repeat Run — Record Counts

| # | Entity | First Run (8087) | Repeat Run | Δ | Repeat Status |
|---|--------|------------------|------------|---|---------------|
| 1 | Ledger Groups | 28 | — | — | **NOT RUN** |
| 2 | Ledgers | 921 | — | — | **NOT RUN** |
| 3 | Stock Groups | 58 | — | — | **NOT RUN** |
| 4 | Stock Categories | 0 | — | — | **NOT RUN** |
| 5 | Units | 0 | 0 (derived) | 0 | **WARNING / INCOMPLETE** |
| 6 | Godowns | 1 | — | — | **NOT RUN** |
| 7 | Cost Categories | 1 | — | — | **NOT RUN** |
| 8 | Cost Centres | 0 | — | — | **NOT RUN** |
| 9 | Voucher Types | 24 | — | — | **NOT RUN** |
| 10 | GST Registrations | 0 | — | — | **NOT RUN** |
| 11 | Stock Items | 1,502 (cache) | 1,502 (fresh live) | 0 | **PARTIAL PASS**† |

†Fresh live collection fetch succeeded outside connector cache. Full connector endpoint validation (correlation ID, circuit state, diagnostics) not executed because Tally hung before connector start.

**First-run source:** [`m3-live-validation-final.json`](m3-live-validation-final.json)

---

## Fresh vs Cached — Stock Items

| Metric | First Run (entity 11) | Repeat Run |
|--------|----------------------|------------|
| Source | Session cache (populated by Units request) | **Fresh Tally POST** |
| Cache hit | ✅ Yes (8 ms) | ❌ No |
| Duration | 8 ms | **646 ms** |
| Response size | 434,166 bytes (via Units side-effect) | **434,170 bytes** |
| Record count | 1,502 | **1,502** |
| Parsing | OK | OK |
| Unit fields in response | Absent | **Absent** |

Artifact: [`m3-stock-items-raw-sample.xml`](m3-stock-items-raw-sample.xml)

---

## Entity-by-Entity Verdict (Repeat Run)

| # | Entity | Verdict | Notes |
|---|--------|---------|-------|
| 1 | Ledger Groups | **NOT RUN** | Tally hung before validation |
| 2 | Ledgers | **NOT RUN** | — |
| 3 | Stock Groups | **NOT RUN** | — |
| 4 | Stock Categories | **NOT RUN** | — |
| 5 | Units | **WARNING / INCOMPLETE** | See Units investigation below |
| 6 | Godowns | **NOT RUN** | — |
| 7 | Cost Categories | **NOT RUN** | — |
| 8 | Cost Centres | **NOT RUN** | — |
| 9 | Voucher Types | **NOT RUN** | — |
| 10 | GST Registrations | **NOT RUN** | — |
| 11 | Stock Items | **PARTIAL PASS** | Fresh live fetch OK; connector route not re-tested |

---

## Units Investigation (Detailed)

### 1. Does the company have stock items with units?

**Yes — 1,502 stock items exist** in the ESTIMATION company. Items are real named masters (e.g. `079 DIVERTER BUTTON`, `1" Ball Valve Aero`). Manual Tally UI unit assignment was **not verified** this session (Tally hung before UI cross-check), but the item count confirms a populated inventory.

### 2. Raw stock-item XML — unit-related fields

From the fresh 434 KB `List of Stock Items` export:

| Tag / field | Count in export | Location |
|-------------|-----------------|----------|
| `BASEUNITS` | **0** | — |
| `ADDITIONALUNITS` | **0** | — |
| `UOM` | **0** | — |
| `BASEUNIT` | **0** | — |
| `DENOMINATOR` / `CONVERSION` / `COMPOUND` | **0** | — |
| `UNIT` | **1** | Inside `CMPINFO` metadata only (`<UNIT>0</UNIT>` — master count, not item data) |

**Actual `STOCKITEM` node structure:**

```xml
<STOCKITEM NAME="079 DIVERTER BUTTON" RESERVEDNAME="">
  <LANGUAGENAME.LIST>
    <NAME.LIST TYPE="String">
      <NAME>079 DIVERTER BUTTON</NAME>
    </NAME.LIST>
    <LANGUAGEID TYPE="Number"> 1033</LANGUAGEID>
  </LANGUAGENAME.LIST>
</STOCKITEM>
```

No `PARENT`, `CATEGORY`, `OPENINGBALANCE`, `HSNCODE`, or unit fields appear in the default collection export.

### 3. Is the stock-item request fetching required unit fields?

**No.** The current template uses the standard Tally collection:

```
List of Stock Items
```

Tally's default collection export for ESTIMATION returns **name-only** nodes. The request is valid XML; Tally simply does not include unit fields in this export format.

### 4. Is the parser dropping or misnaming unit values?

**No.** Parser verification on saved raw XML:

| Metric | Value |
|--------|-------|
| Parsed stock items | 1,502 |
| Items with `baseUnit` | **0** |
| Items with `parentGroup` | **0** |
| Parser dropping fields | **No** — fields absent from source XML |

`mapStockItem` correctly reads `BASEUNITS` when present; Tally did not supply it.

### 5. Cross-check sample stock items in Tally UI

**Not performed** — Tally HTTP hung before UI verification. Recommended on next run: open 3–5 known items in Tally Alter screen and note Base Unit / Additional Unit values.

### 6. Derive unique Units list safely from stock items

`deriveUnitsFromStockItems()` ran against parsed data → **0 unique units**.

Derivation logic is correct (see `test/unit/extraction/units-derivation.test.ts`); input data lacks `baseUnit` values.

### 7. `List of Units` — not called

Confirmed. Direct collection bypass remains in place.

### 8. Exact reason Units previously returned zero

**Tally's default `List of Stock Items` collection export omits all unit-related fields.** The prior run's Units endpoint triggered this same export, cached 1,502 items, and derivation found zero `baseUnit` values — not because items lack units in Tally, but because **the export format does not include them**.

First run incorrectly marked Units as **PASS** with **EMPTY** normalization. Correct verdict: **WARNING / INCOMPLETE**.

### 9. Can Units be derived reliably today?

**No.** Reliable derivation requires one of:

- Enriched stock-item Tally export (custom TDL / expanded field list including `BASEUNITS`)
- A safe alternative collection that returns unit masters **without** using `List of Units` (none identified)
- Per-item object exports (risky — object probe caused hang this session)

**Do not invent unit names.**

---

## Engineering Verification

| Command | Result |
|---------|--------|
| `npm run lint` | ✅ Pass |
| `npm run test` | ✅ **103/103** pass |
| `npm run build` | ✅ Pass |

---

## Tally Stability

| Metric | This Session |
|--------|--------------|
| Tally crashes (`c0000005`) | **0** |
| Tally hangs | **1** (post object-export probe) |
| Retry storms | **0** |
| Circuit breaker trips | **0** |
| `List of Units` calls | **0** |

---

## Milestone 3 Commit Safety

| Criterion | Status |
|-----------|--------|
| All 11 entities live-validated sequentially | ❌ Repeat run blocked |
| Units data completeness | ❌ WARNING / INCOMPLETE |
| Stock items fresh live (not cache-only) | ✅ Collection fetch verified |
| Zero Tally instability | ❌ Hang occurred |
| Safety controls proven on repeat | ❌ Not exercised end-to-end |
| Lint / test / build | ✅ Pass |

**Recommendation: Do not commit Milestone 3 yet.**

Required before commit:

1. **Restart TallyPrime** — open ESTIMATION, confirm HTTP on port 9000
2. **Re-run full 11-entity controlled validation** (5 s delays, no `List of Units`, fresh connector for entity 11)
3. **Mark Units as WARNING / INCOMPLETE** in API/docs until enriched export exists
4. **Your explicit approval**

---

## Artifacts

| File | Description |
|------|-------------|
| [`m3-repeat-live-validation.json`](m3-repeat-live-validation.json) | Repeat run metadata (blocked) |
| [`m3-repeat-units-investigation.json`](m3-repeat-units-investigation.json) | Units investigation data |
| [`m3-stock-items-raw-sample.xml`](m3-stock-items-raw-sample.xml) | Fresh 434 KB stock items export |
| [`m3-live-validation-final.json`](m3-live-validation-final.json) | First successful run (reference) |

---

**Stopped — awaiting your approval and Tally restart before retry.**
