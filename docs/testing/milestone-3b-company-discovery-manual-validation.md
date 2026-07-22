# Milestone 3B — Company Discovery Manual Validation Checklist

**Purpose:** Human-approved live Tally validation after automated Milestone 3B implementation.  
**Final session date:** 2026-07-22  
**Connector version:** 0.3.1  
**Validator:** Controlled session (Cursor agent + operator confirmation)  
**Overall result:** **VALIDATED**

## Prerequisites

- [x] Connector built and running locally (`npm run build`, start connector)
- [x] TallyPrime installed with test company data
- [x] Safe Mode enabled in connector config (`tallySafeMode: true`)
- [x] Audit logging enabled (`tallyRequestAuditEnabled: true`)
- [x] No other connector or tool sending parallel requests to Tally port 9000 (best effort)

## Validation sessions

| Session | Time (IST) | Notes |
|---------|------------|-------|
| Session 1 | ~22:06–22:17 | Scenarios 1 & 4 pass; Scenario 3 inconclusive (Tally port down); Scenario 2 skipped |
| Session 2 | ~22:20–22:22 | Scenarios 1, 3, 4 pass; Scenario 2 empty (Tally state changed mid-run) |
| Session 3 | ~22:28 | Scenario 2 pass after connector restart and multi-company Tally setup |

All scenarios executed via `GET /companies` / `GET /health` only — no raw XML, transport, or connection-manager bypass.

---

## Scenario 1 — Tally running with one company — **PASS**

**Operator confirmation:** PASS — exactly one company available/open.

| Field | Evidence |
|-------|----------|
| Start / End | 2026-07-22T22:20:04 / 22:20:06 +05:30 (Session 2 rerun) |
| Tally state | Running on `localhost:9000`; one company available |
| Operation ID | `COMPANY_LIST` |
| Policy decision | `ALLOW` |
| HTTP result | `200` |
| Domain status | `SUCCESS`, `tallyReachable: true` |
| Companies returned | 1 — `id: estimation` |
| Audit reference | `correlationId: 21dc0e08-…`, `requestHash: 3987f76d359d2b278fd3a5dfefc78436ea9fb22b9d59b89625ff0f8c9d4e1574`, outcomes `intent` + `sent` |
| Duration | ~2170 ms |

**Checks:**

- [x] HTTP 200, `status: SUCCESS`, `tallyReachable: true`
- [x] Returned company matches Tally UI (operator confirmed)
- [x] Stable `id` on repeat call
- [x] Audit: `COMPANY_LIST`, `policyDecision: ALLOW`, Export / `List of Companies` only
- [x] No duplicate company
- [x] No write-capable verbs in audit redacted XML

**Known quirk:** `dataQuality.status: INCOMPLETE` with “0 record(s) lacked…” when CMPINFO metadata nodes are skipped. Top-level `status` remains `SUCCESS`.

---

## Scenario 2 — Tally running with multiple companies — **PASS**

**Operator confirmation:** PASS — `estimation` and `learn` match companies visible in Tally.

| Field | Evidence |
|-------|----------|
| Start / End | 2026-07-22T22:28:02 / 22:28:07 +05:30 (Session 3) |
| Tally state | Running on `localhost:9000`; two companies configured |
| Operation ID | `COMPANY_LIST` |
| Policy decision | `ALLOW` |
| HTTP result | `200` |
| Domain status | `SUCCESS`, `tallyReachable: true` |
| Companies returned | 2 — `id: estimation`, `id: learn` |
| Audit reference | `correlationId: 28ecc26e-…` and `76e8ba85-…`, hash `3987f76d…`, outcomes `intent` + `sent` |
| Duration | ~2175 ms (first call); ~25 ms (repeat, stable IDs) |

**Checks:**

- [x] All expected companies returned exactly once (no duplicates)
- [x] Names match Tally UI (operator confirmed)
- [x] Stable `id` values across repeated calls
- [x] Audit evidence: Export only, `List of Companies` collection

**Note:** Connector restart required before this run to reset circuit breaker from prior Scenario 4 session.

---

## Scenario 3 — Tally running with no company open — **PASS**

| Field | Evidence |
|-------|----------|
| Start / End | 2026-07-22T22:21:39 / 22:21:41 +05:30 (Session 2 rerun) |
| Tally state | Running on port 9000; no company open |
| Operation ID | `COMPANY_LIST` |
| Policy decision | `ALLOW` |
| HTTP result | `200` |
| Domain status | `EMPTY`, `tallyReachable: true` |
| Companies returned | none |
| Audit reference | `correlationId: c76bd226-…`, outcomes `intent` + `sent` |
| Duration | ~2110 ms |

**Checks:**

- [x] No crash or hang
- [x] Explicit `EMPTY` with `dataQuality.status: EMPTY` and reason — not silent success
- [x] Not reported as malformed or trusted success with invented data
- [x] Tally HTTP listener still active (port 9000 responding)
- [x] Audit evidence exists

**Session 1 note:** Initial attempt returned `503`/`UNAVAILABLE` because Tally port 9000 became unreachable (operator likely closed Tally entirely). Superseded by Session 2 rerun with correct Tally state.

---

## Scenario 4 — Tally closed or unavailable — **PASS**

| Field | Evidence |
|-------|----------|
| Start / End | 2026-07-22T22:22:07 / 22:22:15 +05:30 (Session 2 rerun) |
| Tally state | Port 9000 unreachable |
| Operation ID | `COMPANY_LIST` (transport fail then circuit blocked) |
| Policy decision | `ALLOW` on intent; circuit breaker blocks subsequent calls |
| HTTP `GET /companies` | `503` — `SERVICE_UNAVAILABLE`, `details.status: UNAVAILABLE`, `tallyReachable: false` |
| HTTP `GET /health` | `200`, `tallyReachable: false` |
| Domain status | `UNAVAILABLE` |
| Companies returned | none |
| Audit reference | `correlationId: 0c67c183-…` outcome `failed`; `d8a1cadc-…` outcome `blocked`, `circuitStateBefore: open` |
| Duration | ~2034 ms (first transport attempt); ~3 ms (circuit-blocked repeat) |

**Checks:**

- [x] Fast, controlled failure after circuit opens
- [x] Correct `UNAVAILABLE` / `503` mapping
- [x] No uncontrolled retry loop (`tallyRetryMaxAttempts: 1`)
- [x] No process hang
- [x] Circuit breaker behaviour correct
- [x] Audit evidence exists

---

## Scenario 5 — Malformed or interrupted response (optional)

**Not executed** in this validation cycle.

---

## Scenario 6 — No write-capable requests (session review)

Reviewed audit records across all successful discovery calls:

- [x] Every request uses `<TALLYREQUEST>Export</TALLYREQUEST>`
- [x] No `IMPORT`, `EXECUTE`, `FUNCTION`, `ALTER`, `CREATE`, `DELETE`, or `UPDATE` tokens observed
- [x] Company discovery used only registered collection ID `List of Companies`

---

## Scenario 7 — Audit record verification

For each successful `COMPANY_LIST` discovery call:

- [x] `correlationId` present
- [x] `operationId: COMPANY_LIST` on intent records
- [x] `policyDecision: ALLOW`
- [x] `outcome: intent` before transport, `outcome: sent` after success
- [x] `requestHash` present (redacted XML in audit, no raw sensitive payload)

---

## Scenario 8 — Result comparison with Tally UI

- [x] Scenario 1 company name confirmed by operator
- [x] Scenario 2 companies `estimation` and `learn` confirmed by operator
- [x] No invented `baseCurrency` observed in live responses
- [ ] Financial year / books-from live field comparison — not fully exercised (names only returned in these sessions)

---

## Post-validation automated checks (2026-07-22, final)

| Check | Result |
|-------|--------|
| `npm run lint` | Pass |
| `npm run test` | 184 / 184 pass |
| `npm run build` | Pass |
| Architecture tests | 12 / 12 pass (`test/architecture/module-boundaries.test.ts`) |

---

## Sign-off

| Field | Value |
|-------|-------|
| Validator | Operator + controlled automated session |
| Date | 2026-07-22 |
| Tally version/build | Not captured |
| Connector version | 0.3.1 |
| Result | **VALIDATED** |
| Notes | All required scenarios (1–4) pass. Optional Scenario 5 not run. Minor `dataQuality: INCOMPLETE` quirk on otherwise valid SUCCESS responses when CMPINFO metadata nodes are present. |
