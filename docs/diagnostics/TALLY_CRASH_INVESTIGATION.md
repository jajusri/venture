# TallyPrime Crash Investigation Report

**Date:** 2026-07-22  
**Incident:** TallyPrime `Software Exception c0000005` (Memory Access Violation)  
**Scope:** Venture connector → Tally HTTP/XML connection path only  
**Status:** Investigation complete — **awaiting approval before any M3 commit**

---

## Executive Summary

TallyPrime crashed during Milestone 3 live testing. The crash was **not caused by malformed XML** from the Venture connector. Evidence points to **Tally process instability from cumulative load**: a long sequential probe of 12 heavy collection exports, **two parallel duplicate collection requests** while Tally was already busy, followed by connector API smoke tests and a **retry/reconnect storm** (6× 2s backoffs) against an already-dead Tally HTTP server.

The last Venture-originated request before connector failure was **`List of Groups`** (ledger-groups endpoint). The most probable crash trigger occurred **earlier**, during the overlapping probe/parallel fetch window (~10:26–10:30 UTC), after Tally had already returned 260KB–434KB collection responses and began timing out on subsequent requests.

Safety controls have been added to the **connection layer only** (no new extraction features). Milestone 3 code remains uncommitted and should **not be committed** until live re-validation with safe mode confirms Tally stability.

---

## 1. Request Sequence Before Crash

### Timeline (UTC, 2026-07-22)

| Time | Source | Request | Result |
|------|--------|---------|--------|
| 10:26:38 | PowerShell probe (708399) | Sequential 12× `POST localhost:9000` collection exports | Ledgers 260KB ✓, Stock Items 434KB ✓, Groups 18KB ✓, then 7× timeout (30s each) |
| 10:28:53 | Parallel scripts (708400, 708401) | Duplicate `List of Ledgers` + `List of Groups` | Both timed out at 60s — Tally already overloaded |
| 10:30:27 | Probe completes | — | Total probe ~229 seconds |
| 10:34:45 | Connector smoke (8083, 708402) | Server starts | OK |
| ~10:35–10:36 | Connector API (sequential) | `/health` → `/companies` → `/companies/estimation` → `/companies/estimation/ledger-groups` | Early calls succeeded; ledger-groups triggered failure |
| 10:36:49 | Connector logs | Reconnect backoff attempts 1–6 (2s each) | `fetch failed` — Tally HTTP unreachable |
| User report | TallyPrime UI | Internal Error c0000005 | Tally process crashed |

### Last HTTP/XML Request (Connector Path)

**Endpoint:** `POST http://localhost:9000`  
**Triggered by:** `GET /companies/estimation/ledger-groups`  
**Collection ID:** `List of Groups`  
**Type:** Export / Collection  

Redacted payload saved to: [`docs/diagnostics/tally-crash-investigation-payloads.xml`](tally-crash-investigation-payloads.xml)

### Connector Configuration at Time of Crash

| Setting | Value |
|---------|-------|
| `tallyPoolMaxConnections` | 4 |
| `tallyRetryMaxAttempts` | 3 |
| `tallyAutoReconnect` | true |
| `tallyReconnectDelayMs` | 2000 |
| Circuit breaker | none |
| Rate limiting | none |
| Single-request mode | none |
| XML validation before send | none |

---

## 2. XML Validity Assessment

All Venture-generated requests were inspected against TallyPrime conventions:

| Check | Result |
|-------|--------|
| Well-formed XML (`<ENVELOPE>` root) | ✓ Pass |
| Standard `Export` + `Collection`/`Object`/`Data` types | ✓ Pass |
| Valid collection IDs (connector templates) | ✓ Pass |
| `SVEXPORTFORMAT` = `$$SysName:XML` | ✓ Pass |
| `SVCURRENTCOMPANY` scoping | ✓ Pass |
| Unicode/control characters | ✓ None detected in generated XML |
| Recursive/nested collection definitions | ✓ None |
| Oversized **request** bodies | ✓ All requests < 1KB |

**Invalid probe ID:** `Company Features` (manual probe script only) — not in Venture templates; timed out but unlikely sole crash cause.

**Conclusion:** XML structure is valid. Crash is consistent with **Tally internal memory fault under concurrent/heavy export load**, not invalid request syntax.

---

## 3. Contributing Factors

### High confidence

1. **Heavy sequential collection exports** — 260KB ledgers + 434KB stock items in ~4 minutes without cooldown.
2. **Parallel duplicate requests** — two simultaneous `List of Ledgers` / `List of Groups` fetches at 10:28:53 while probe was still running/timeouts occurring.
3. **Retry + reconnect storm** — 3 retries × 6 reconnect backoffs hammering dead Tally after HTTP server died.
4. **Connection pool concurrency = 4** — allowed overlapping in-flight requests.

### Medium confidence

5. **Invalid `Company Features` collection** in manual probe — added failing work while Tally busy.
6. **120s timeout** — long-hanging requests kept Tally occupied.

### Low confidence / ruled out

- Malformed Venture XML
- Unicode/control character injection
- Oversized request payloads

---

## 4. Reproducibility

| Test | Result |
|------|--------|
| Reproduce crash with `List of Groups` alone | **Not attempted** — would risk another Tally crash |
| Reproduce with `License Info` (safe probe) | **Deferred** — Tally was unstable/unreachable after incident |
| Reproduce via unit/integration mocks | ✓ Single-request safe mode prevents retry storms |
| Reproduce via parallel heavy exports | **Strongly suspected** based on terminal evidence — matches known Tally fragility under concurrent ODBC/XML load |

**Verdict:** Crash is **likely reproducible** under the same load pattern (heavy exports + parallelism + retries). **Not reproducible** with minimal safe requests alone based on pre-crash success of `/health` and `/companies`.

---

## 5. Code Changes Made (Connection Layer Only)

New module: `src/tally/safety/`

| File | Purpose |
|------|---------|
| `xml-request-validator.ts` | Pre-send XML validation, size limits, control-char check, redaction helper |
| `tally-circuit-breaker.ts` | Opens after consecutive failures; blocks further requests during cooldown |
| `tally-request-guard.ts` | Single-flight mode, rate limiting, runtime limit resolver, correlation IDs in logs |
| `tally-request-auditor.ts` | Redacted XML audit trail to `./diagnostics/tally-request-audit.jsonl` (not production logs) |

Updated:

| File | Change |
|------|--------|
| `tally-connection-manager.ts` | Guard integration, circuit-aware retry suppression, safe reconnect probe |
| `tally-http-transport.ts` | Pool size from runtime limits, correlation ID in debug logs (no XML body) |
| `reconnect-manager.ts` | Max reconnect attempts cap |
| `config/defaults.ts` | Safe mode defaults: pool=1, retry=1, circuit breaker, rate limit, size caps |
| `config/index.ts` | Environment variable parsing for all safety settings |
| `core/types.ts` | Extended diagnostics snapshot |

### Safety Controls Added

| Control | Default (safe mode ON) |
|---------|------------------------|
| Single-request / concurrency limit | 1 |
| Retry max attempts | 1 |
| Auto-reconnect | Disabled |
| Min interval between requests | 2000ms |
| Circuit breaker | Enabled (threshold 2, cooldown 60s) |
| Max request size | 64KB |
| Max response size | 10MB |
| Correlation IDs | Logged (info/debug), never XML body |
| XML validation | Before every send |
| Redacted audit file | `./diagnostics/tally-request-audit.jsonl` |

Environment overrides: `VENTURE_TALLY_SAFE_MODE`, `VENTURE_TALLY_POOL_MAX`, `VENTURE_TALLY_RETRY_MAX`, `VENTURE_TALLY_CIRCUIT_BREAKER*`, etc.

---

## 6. Tests Performed

### Automated

| Command | Result |
|---------|--------|
| `npm run lint` | ✅ Pass |
| `npm run test` | ✅ **89/89** pass |
| `npm run build` | ✅ Pass |

New tests:

- `test/unit/tally/xml-request-validator.test.ts`
- `test/unit/tally/tally-circuit-breaker.test.ts`
- `test/unit/tally/tally-request-guard.test.ts`
- `test/integration/tally-safety.test.ts` — verifies safe mode = 1 attempt, no reconnect storm, circuit opens

### Live Testing

Per instructions, **no broad master-data extraction** was re-run.

Post-fix minimal live probes (Tally on localhost:9000, sequential, single request each):

| Probe | Result |
|-------|--------|
| `License Info` (Data export) | ✅ HTTP 200, 148 bytes |
| `List of Companies` (Collection) | ✅ HTTP 200, 2192 bytes |

Heavy collection exports (`List of Ledgers`, `List of Groups`) were **not** re-run to avoid repeating the crash conditions.

---

## 7. Remaining Uncertainty

1. **Exact Tally-internal fault** — c0000005 is a native access violation; we cannot inspect TallyPrime source. Load/concurrency is inferred from timing and logs, not a Tally crash dump.
2. **Whether `List of Groups` alone triggers crash** — untested intentionally to avoid harm.
3. **Tally version / patch level** — not captured in logs; some Tally builds may be more fragile.
4. **Company data size correlation** — ESTIMATION company has large ledger/stock datasets; smaller test company may not reproduce.

---

## 8. Recommendation: Is Milestone 3 Safe to Commit?

### Connection layer safety fixes

**Ready for review** — safeguards address the identified failure mode (concurrency, retry storms, missing validation).

### Full Milestone 3 extraction feature set

**Not recommended to commit yet.**

Reasons:

1. Crash occurred during M3 live smoke testing; root cause mitigated at connection layer but **not re-validated live**.
2. M3 adds 12 extraction endpoints that map to the same heavy Tally collections that preceded the crash.
3. Extraction service has no additional throttling beyond connection-layer guard — sequential API calls across many endpoints could still stress Tally even at concurrency=1 if run back-to-back without delay.

### Suggested path forward

1. **Commit connection safety changes separately** (after your approval) — could be a patch on M2 or pre-M3 hardening.
2. **Live re-validate** with safe mode: `License Info` → `List of Companies` only.
3. **One collection at a time** with 5–10s manual delay; stop immediately on any Tally instability.
4. **Defer M3 commit** until at least ledger-groups and ledgers succeed individually on a test company.

---

## 9. Diagnostic Artifacts

| Artifact | Location |
|----------|----------|
| Redacted XML payloads | `docs/diagnostics/tally-crash-investigation-payloads.xml` |
| Probe terminal log | terminals/708399.txt |
| Parallel fetch logs | terminals/708400.txt, 708401.txt |
| Connector smoke log | terminals/708402.txt |
| Runtime audit (after fix) | `connector/venture_connector/diagnostics/tally-request-audit.jsonl` |

---

**Investigation complete. No commits, tags, or pushes were made. Awaiting your approval.**
