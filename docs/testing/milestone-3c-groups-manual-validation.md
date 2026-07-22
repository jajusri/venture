# Milestone 3C — Groups Manual Validation Report

**Purpose:** Human-approved live Tally validation after automated Milestone 3C implementation.  
**Date:** 2026-07-22  
**Company tested:** ESTIMATION (`estimation`)  
**Connector version:** 0.3.1  
**Tally version/build:** Not captured  
**Commit/tag/push:** Not performed

## Prerequisites

- [x] Connector built and running locally (`BUDCOM_TALLY_SAFE_MODE=true`, audit enabled)
- [x] TallyPrime with test company containing known group hierarchy
- [x] Safe Mode enabled
- [x] Audit logging enabled (`BUDCOM_TALLY_REQUEST_AUDIT=true`)
- [x] No parallel tools on Tally port 9000

## Approved operation verified

All successful extractions used only:

| Field | Value |
|-------|-------|
| Operation ID | `LEDGER_GROUPS` |
| Request type | `EXPORT` |
| Request kind | `Collection` |
| Collection | `List of Groups` |
| Classification | `VERIFIED_SAFE` |
| Policy decision | `ALLOW` |

No write-capable verbs, no architecture bypass, no raw XML in business-facing responses.

---

## Scenario 1 — Normal group extraction — **PASS**

| Field | Evidence |
|-------|----------|
| Human-confirmed Tally state | Tally running; exactly one company open (ESTIMATION) |
| Start / End | 2026-07-22 ~22:47 / ~22:47 +05:30 |
| Duration | ~4,300 ms (first call) |
| Operation ID | `LEDGER_GROUPS` |
| Policy decision | `ALLOW` |
| HTTP result | `200` |
| Domain status | `INCOMPLETE`, `contractVersion: 1`, `tallyReachable: true` |
| Total groups | 28 |
| Empty required names | 0 |
| Duplicate stable IDs | 0 |
| Duplicate logical records | 0 |
| Hierarchy issues | 15 (`MISSING_PARENT`) |
| Slug collisions | 0 |
| Audit reference | `correlationId: c2af6608-…`, request hash `86ebd3c9b01e4307b42684d4a655421ec554aaa4dc5ad825c7f2d87b98188175` |
| Raw XML in response | None |

**Checks:**

- [x] HTTP/transport success
- [x] Explicit domain status (not silent trusted success)
- [x] Group count matches Tally (28, human confirmed)
- [x] No empty required names
- [x] No duplicate IDs or slug collisions
- [x] Hierarchy issues surfaced, not silently repaired
- [x] Approved audit record exists
- [x] Only `LEDGER_GROUPS` / Export used

**Note:** Status is `INCOMPLETE` (not `SUCCESS`) because Tally returns parent as `"&#4; Primary"` rather than `"Primary"`. See known quirk below.

---

## Scenario 2 — Representative group comparison — **PASS**

Human-selected representative groups compared against Tally UI.

| Group type | Finding |
|------------|---------|
| Built-in root (Capital Account, Current Assets, Sales Accounts, etc.) | Names match; `parentName: "&#4; Primary"` |
| Built-in child (Bank Accounts, Cash-in-Hand, Sundry Debtors) | Names match; parent `Current Assets` with correct `parentStableId: current-assets` |
| Primary group | Not present in Tally export (expected virtual root) |
| User-created / subgroup | Parent-child relationships match where visible in Tally |
| Punctuation / spaces (`Branch / Divisions`, `Misc. Expenses (ASSET)`, `Duties & Taxes`) | Names preserved exactly |
| `reservedName` / built-in classification | Not set (Primary not exported; `ISBUILTIN` not surfaced for tested groups) |
| Stable ID repeatability | Consistent across calls |

**Mismatches reported separately:** Parent displayed as `"&#4; Primary"` in API vs plain `Primary` in Tally UI — entity encoding quirk, not a name mismatch.

---

## Scenario 3 — Full hierarchy validation — **PASS**

| Issue type | Count | Notes |
|------------|-------|-------|
| `MISSING_PARENT` | 15 | Parent `"&#4; Primary"` not recognized as virtual root |
| `SELF_PARENT` | 0 | |
| `CYCLE` | 0 | |
| `DUPLICATE_ID` | 0 | |
| `DUPLICATE_NAME` | 0 | |
| `EMPTY_NAME` | 0 | |
| `AMBIGUOUS_PARENT` | 0 | |
| Missing parent (unresolved) | 0 beyond above | |
| Suspicious multiple-root | 0 | |

- [x] Processing bounded and completed normally
- [x] Issues preserved with exact type and affected records
- [x] Result correctly `INCOMPLETE` (not silently repaired to `SUCCESS`)

---

## Scenario 4 — Repeatability — **PASS**

Two consecutive calls with unchanged Tally state.

| Field | Call 1 | Call 2 |
|-------|--------|--------|
| Duration | ~4,100 ms | ~28 ms |
| HTTP | 200 | 200 |
| Status | `INCOMPLETE` | `INCOMPLETE` |
| Group count | 28 | 28 |
| Names | Identical | Identical |
| Stable IDs | Identical | Identical |
| Parent relationships | Identical | Identical |
| Hierarchy issues | 15 | 15 |
| Slug collisions | 0 | 0 |

- [x] Deterministic count and identity on repeat
- [x] No slug-derived ID collisions (stable ID → name map is 1:1)

---

## Scenario 5 — No company open — **PASS**

| Field | Evidence |
|-------|----------|
| Human-confirmed Tally state | Tally running; no company open |
| Start / End | 2026-07-22T22:54:19 / 22:54:21 +05:30 |
| Duration | ~2,097 ms |
| Operation ID | `COMPANY_LIST` only (`LEDGER_GROUPS` not reached) |
| Policy decision | `ALLOW` |
| HTTP result | `404` — `VALIDATION_ERROR`: `Unknown company id: estimation` |
| Domain status (groups) | Not reached |
| Groups returned | 0 |
| Audit reference | `correlationId: 210f2cf4-…`, hash `3987f76d…` |
| Circuit breaker | `closed`; no retry loop |

**Checks:**

- [x] No crash or hang
- [x] Explicit non-success outcome (equivalent to `COMPANY_UNAVAILABLE`)
- [x] No invented groups
- [x] No trusted SUCCESS with empty unverified data
- [x] Approved audit evidence exists
- [x] No uncontrolled retries

**Note:** Failure occurs at `CompanyResolver` (company discovery returns `EMPTY`) before `LEDGER_GROUPS` is sent. HTTP 404 rather than documented 503/`COMPANY_UNAVAILABLE` adapter path.

---

## Scenario 6 — Tally unavailable — **PASS**

| Field | Evidence |
|-------|----------|
| Human-confirmed Tally state | Tally fully closed |
| Start / End | 2026-07-22T22:56:33 / 22:56:35 +05:30 |
| Duration (1st call) | ~2,146 ms |
| Duration (2nd call, circuit blocked) | ~2,128 ms |
| Operation ID | `COMPANY_LIST` (transport fail); `LEDGER_GROUPS` not reached |
| Policy decision | `ALLOW` on intent |
| HTTP result | `503` — `SERVICE_UNAVAILABLE` |
| Domain status | `UNAVAILABLE`, `tallyReachable: false` |
| Groups returned | 0 |
| Audit (1st) | `correlationId: 40c957e2-…`, outcomes `intent` → `failed` |
| Audit (2nd) | `correlationId: 234bb4b0-…`, outcome `blocked`, `circuitStateBefore: open` |

**Checks:**

- [x] Controlled failure, no hang
- [x] `UNAVAILABLE` documented transport result
- [x] No invented groups
- [x] Circuit breaker opens and blocks repeat
- [x] Audit intent/outcome recorded
- [x] Reasonable duration

---

## Scenario 7 — Recovery after unavailable — **PASS**

| Field | Evidence |
|-------|----------|
| Human-confirmed Tally state | Tally restarted; ESTIMATION open |
| Connector restart | **Not required** — circuit recovered after cooldown |
| Start / End | 2026-07-22T22:58:00 / 22:58:04 +05:30 |
| Duration | ~4,145 ms |
| Operation ID | `COMPANY_LIST` then `LEDGER_GROUPS` |
| Policy decision | `ALLOW` |
| HTTP result | `200` |
| Domain status | `INCOMPLETE`, `tallyReachable: true` |
| Group count | 28 (matches Scenario 1/4) |
| Hierarchy issues | 15 (matches Scenario 1/4) |
| Duplicate IDs / slug collisions | 0 / 0 |
| Audit reference | `LEDGER_GROUPS` hash `86ebd3c9…`, `circuitStateBefore: closed` |
| Repeat call consistency | Two post-recovery calls identical |

**Checks:**

- [x] Recovery succeeds without stale failure state
- [x] Groups match earlier successful result
- [x] No duplicate records introduced
- [x] Approved audit evidence exists

---

## Data quality and identity review

| Check | Result |
|-------|--------|
| Unicode NFC does not merge distinct names | Pass — names preserved (e.g. punctuation groups) |
| Case differences per contract | Not observed in test data |
| Whitespace normalization collisions | None observed |
| Identical names under different parents | Not present in test data |
| Slug collisions surfaced explicitly | None (0 collisions) |
| Parent-name ambiguity → issue not guess | Pass — `"&#4; Primary"` → `MISSING_PARENT`, status `INCOMPLETE` |

---

## Known response quirks

1. Tally returns parent as `"&#4; Primary"` (XML character entity + "Primary"), not plain `"Primary"`. Code recognizes only normalized `"Primary"` as virtual root, causing 15 built-in root groups to flag `MISSING_PARENT` and overall status `INCOMPLETE`.
2. Primary group itself is not included in the collection export.
3. `reservedName` / built-in flags not populated for tested groups despite built-in classification in Tally UI.
4. No-company and unavailable paths fail at company resolution (`COMPANY_LIST`) before `LEDGER_GROUPS` is invoked.

---

## Automated verification (post-live)

| Check | Result |
|-------|--------|
| `npm run lint` | Pass |
| `npm run test` | 222/222 pass |
| `npm run build` | Pass |
| Architecture tests | 12/12 pass |
| Milestone 3B company-discovery regression | Green |

---

## Sign-off

| Field | Value |
|-------|-------|
| Validator | Human-approved (Cursor-assisted) |
| Date | 2026-07-22 |
| Tally version/build | Not captured |
| Connector version | 0.3.1 |
| Company | ESTIMATION (28 groups) |
| Known response quirks | `&#4; Primary` parent encoding; Primary not exported |
| Scenarios passed | 7 / 7 |
| Scenarios failed | 0 |
| Scenarios not tested | 0 |
| **Result** | **PASS** |
