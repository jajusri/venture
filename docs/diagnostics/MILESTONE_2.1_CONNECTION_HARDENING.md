# Milestone 2.1 — Connection Hardening Engineering Report

**Date:** 2026-07-22  
**Scope:** Tally connection safety layer only (post–Milestone 2)  
**Base commit:** `49fbc48` (Milestone 2 — Tally Communication Layer)  
**Status:** Complete — **awaiting approval. No commit, tag, or push.**

---

## Executive Summary

Milestone 2.1 adds production-grade safety controls to the Budcom-to-Tally connection path. These changes address the TallyPrime crash (`c0000005`) root cause identified in the crash investigation: **concurrent heavy exports, retry/reconnect storms, and unbounded connection concurrency**.

All safety components are implemented, wired into `TallyConnectionManager`, covered by unit and integration tests, and verified with `npm run lint`, `npm run test`, and `npm run build`.

**Milestone 3 extraction code remains in the working tree but was not extended or validated as part of this milestone.**

---

## Verification Summary

| Check | Result |
|-------|--------|
| `npm run lint` | ✅ Pass |
| `npm run test` | ✅ **96/96** pass |
| `npm run build` | ✅ Pass |
| Live Tally testing | Not performed (mock-based verification only) |

---

## Architecture

```
src/tally/
├── safety/                          ← NEW (M2.1)
│   ├── index.ts                     Barrel exports
│   ├── xml-request-validator.ts     Pre-send XML validation + redaction
│   ├── tally-request-guard.ts       Single-flight, rate limit, orchestration
│   ├── tally-circuit-breaker.ts     Failure threshold + cooldown
│   └── tally-request-auditor.ts     Redacted audit trail (not prod logs)
├── connection/
│   └── tally-connection-manager.ts  ← UPDATED: guard integration, safe reconnect
├── transport/
│   └── tally-http-transport.ts      ← UPDATED: pool from runtime limits, correlation IDs
└── core/types.ts                    ← UPDATED: extended diagnostics snapshot
```

### Request Flow (Safe Mode)

```
API / Service
    │
    ▼
TallyConnectionManager.exchange()
    │
    ├─► TallyRequestGuard.prepare()
    │       ├─ Circuit breaker check (block if open → 503)
    │       ├─ validateTallyRequestXml() (size, structure, control chars)
    │       ├─ acquireSingleFlight() (concurrency = 1)
    │       └─ enforceRateLimit() (min interval delay)
    │
    ├─► TallyHttpTransport.send()  [pool max = 1, configurable timeout]
    │       └─ Logs: correlationId, byteLength — never XML body
    │
    ├─► TallyRequestGuard.recordSuccess() / recordFailure()
    │       ├─ Response size check
    │       ├─ Circuit breaker update
    │       └─ TallyRequestAuditor (redacted XML → diagnostics file)
    │
    └─► On failure (non-safe mode only):
            Safe reconnect probe (License Info, 10s cap)
            → suppress retries if Tally still unavailable
```

---

## Components Delivered

### 1. XML Request Validator

**File:** `src/tally/safety/xml-request-validator.ts`

| Capability | Detail |
|------------|--------|
| Empty request rejection | 400 `VALIDATION_ERROR` |
| Max byte size | Configurable (`tallyMaxRequestBytes`, default 64KB) |
| Control character scan | Rejects `\x00–\x1F` except tab/LF/CR |
| ENVELOPE root required | Single-document check |
| Tally convention validation | `TALLYREQUEST` ∈ Export/Import/Execute; `TYPE` ∈ Data/Collection/Object/Function |
| Missing ID rejection | Required `<ID>` tag |
| Redaction helper | Strips `SVCURRENTCOMPANY`, `NAME`, `GSTREGISTRATIONNUMBER` for audit output |

**Tests:** `test/unit/tally/xml-request-validator.test.ts` (6 tests)

---

### 2. Tally Request Guard

**File:** `src/tally/safety/tally-request-guard.ts`

| Capability | Safe-mode default |
|------------|---------------------|
| Single-request mode | Enforced via mutex queue |
| Concurrency limit | 1 (also enforced at connection pool) |
| Configurable request delay | `tallyMinRequestIntervalMs` (default 2000ms) |
| Pre-send validation | Delegates to XML validator |
| Correlation IDs | Generated per request; logged at info/debug |
| Response size cap | `tallyMaxResponseBytes` (default 10MB) |
| Runtime limit resolver | `resolveTallyRuntimeLimits()` applies safe-mode overrides |

**Safe-mode overrides** (when `tallySafeMode=true`):

| Setting | Effective value |
|---------|-----------------|
| Pool max connections | 1 |
| Retry max attempts | 1 |
| Auto-reconnect | Off |
| Max reconnect attempts | 0 |
| Circuit breaker | Always on |
| Min request interval | max(configured, 2000ms) |

**Tests:** `test/unit/tally/tally-request-guard.test.ts` (2), `test/unit/tally/tally-runtime-limits.test.ts` (2)

---

### 3. Tally Circuit Breaker

**File:** `src/tally/safety/tally-circuit-breaker.ts`

| State | Behaviour |
|-------|-----------|
| `closed` | Requests allowed |
| `open` | Requests blocked → 503 until cooldown expires |
| `half_open` | Cooldown elapsed; next request allowed as probe |

| Setting | Default |
|---------|---------|
| Failure threshold | 2 consecutive failures |
| Cooldown | 60,000ms |

After threshold: **retry suppression** — connection manager breaks retry loop immediately when circuit is open.

**Tests:** `test/unit/tally/tally-circuit-breaker.test.ts` (2)

---

### 4. Tally Request Auditor

**File:** `src/tally/safety/tally-request-auditor.ts`

- Writes **redacted** request records to `./diagnostics/tally-request-audit.jsonl`
- Each entry: timestamp, correlationId, collectionId, byte length, outcome, error message, redacted XML
- Disabled via `tallyRequestAuditEnabled=false`
- Audit write failures logged as warnings — never crash the request path
- **Production logs never contain full XML bodies**

**Tests:** `test/unit/tally/tally-request-auditor.test.ts` (2)

---

### 5. Connection Manager Integration

**File:** `src/tally/connection/tally-connection-manager.ts`

| Feature | Implementation |
|---------|----------------|
| Guard on every exchange | `prepare()` → transport → `recordSuccess/Failure()` |
| Correlation ID | UUID per attempt; returned as `requestId` in exchange result |
| Safe reconnect | After reconnect backoff, sends `License Info` probe (10s timeout cap); suppresses retries if probe fails |
| Retry suppression | Safe mode: 1 attempt; circuit open: immediate break; probe fail: break |
| Graceful crash handling | Transport maps `fetch failed` / connection errors → 503 `SERVICE_UNAVAILABLE` |
| Extended diagnostics | safeMode, circuitState, lastRequest, runtimeLimits (incl. timeoutMs) |

**Tests:** `test/integration/tally-safety.test.ts` (4 tests)

---

### 6. Transport Layer

**File:** `src/tally/transport/tally-http-transport.ts`

| Feature | Detail |
|---------|--------|
| Pool size from runtime limits | Respects safe-mode pool=1 |
| Configurable timeout | `tallyTimeoutMs` (default 120s); per-request override via `timeoutMs` |
| Safe logging | Debug log: correlationId, durationMs, byteLength, statusCode — **no body** |
| Crash-aware errors | Connection failures include "Tally may be unavailable or restarting" |
| Timeout errors | 504 with timeout duration in details |

---

## Configuration Reference

### Defaults (`src/config/defaults.ts`)

| Key | Default | Purpose |
|-----|---------|---------|
| `tallySafeMode` | `true` | Enable all safe-mode overrides |
| `tallyPoolMaxConnections` | `1` | HTTP connection pool size |
| `tallyRetryMaxAttempts` | `1` | Max retry attempts per exchange |
| `tallyTimeoutMs` | `120000` | Request timeout (ms) |
| `tallyMinRequestIntervalMs` | `2000` | Min delay between requests |
| `tallyMaxRequestBytes` | `65536` | Max outbound XML size |
| `tallyMaxResponseBytes` | `10485760` | Max inbound XML size |
| `tallyCircuitBreakerEnabled` | `true` | Enable circuit breaker |
| `tallyCircuitBreakerFailureThreshold` | `2` | Failures before open |
| `tallyCircuitBreakerCooldownMs` | `60000` | Cooldown while open |
| `tallyRequestAuditEnabled` | `true` | Write redacted audit file |
| `tallyRequestAuditPath` | `./diagnostics/tally-request-audit.jsonl` | Audit file path |
| `tallyAutoReconnect` | `true` | Overridden to `false` in safe mode |
| `tallyReconnectDelayMs` | `2000` | Backoff between reconnect attempts |

### Environment Variables

| Variable | Maps to |
|----------|---------|
| `BUDCOM_TALLY_SAFE_MODE` | `tallySafeMode` |
| `BUDCOM_TALLY_POOL_MAX` | `tallyPoolMaxConnections` |
| `BUDCOM_TALLY_RETRY_MAX` | `tallyRetryMaxAttempts` |
| `BUDCOM_TALLY_TIMEOUT_MS` | `tallyTimeoutMs` |
| `BUDCOM_TALLY_MIN_REQUEST_INTERVAL_MS` | `tallyMinRequestIntervalMs` |
| `BUDCOM_TALLY_MAX_REQUEST_BYTES` | `tallyMaxRequestBytes` |
| `BUDCOM_TALLY_MAX_RESPONSE_BYTES` | `tallyMaxResponseBytes` |
| `BUDCOM_TALLY_CIRCUIT_BREAKER` | `tallyCircuitBreakerEnabled` |
| `BUDCOM_TALLY_CIRCUIT_BREAKER_THRESHOLD` | `tallyCircuitBreakerFailureThreshold` |
| `BUDCOM_TALLY_CIRCUIT_BREAKER_COOLDOWN_MS` | `tallyCircuitBreakerCooldownMs` |
| `BUDCOM_TALLY_REQUEST_AUDIT` | `tallyRequestAuditEnabled` |
| `BUDCOM_TALLY_REQUEST_AUDIT_PATH` | `tallyRequestAuditPath` |
| `BUDCOM_TALLY_AUTO_RECONNECT` | `tallyAutoReconnect` |
| `BUDCOM_TALLY_RECONNECT_MS` | `tallyReconnectDelayMs` |

---

## Diagnostics

`GET /diagnostics/connection` now includes:

```json
{
  "safeMode": true,
  "circuitState": "closed",
  "lastRequest": {
    "correlationId": "...",
    "collectionId": "License Info",
    "sentAt": "...",
    "outcome": "success"
  },
  "runtimeLimits": {
    "poolMaxConnections": 1,
    "retryMaxAttempts": 1,
    "minRequestIntervalMs": 2000,
    "maxRequestBytes": 65536,
    "maxResponseBytes": 10485760,
    "circuitBreakerEnabled": true,
    "timeoutMs": 120000
  }
}
```

---

## Test Coverage (M2.1)

| Test file | Tests | Coverage |
|-----------|-------|----------|
| `xml-request-validator.test.ts` | 6 | Validation, redaction |
| `tally-circuit-breaker.test.ts` | 2 | Open/reset behaviour |
| `tally-request-guard.test.ts` | 2 | Serialization, circuit block |
| `tally-request-auditor.test.ts` | 2 | Redacted file write, disabled mode |
| `tally-runtime-limits.test.ts` | 2 | Safe-mode overrides |
| `tally-safety.test.ts` | 4 | End-to-end: no retry storm, 503 on crash, circuit block, safe reconnect |
| `config.test.ts` | +5 assertions | Safety defaults |

**Total suite:** 96 tests across 25 files.

---

## Requirement Checklist

| Requirement | Status | Evidence |
|-------------|--------|----------|
| XML Request Validator | ✅ | `xml-request-validator.ts` + 6 unit tests |
| Tally Request Guard | ✅ | `tally-request-guard.ts` + unit/integration tests |
| Tally Circuit Breaker | ✅ | `tally-circuit-breaker.ts` + unit/integration tests |
| Tally Request Auditor | ✅ | `tally-request-auditor.ts` + 2 unit tests |
| Single-request mode | ✅ | Guard mutex + pool=1 in safe mode |
| Configurable request delay | ✅ | `tallyMinRequestIntervalMs` / env var |
| Configurable timeout | ✅ | `tallyTimeoutMs` / env var + per-request override |
| Concurrency limit = 1 | ✅ | Default pool=1, guard single-flight |
| Safe reconnect | ✅ | License Info probe after backoff; suppress if unavailable |
| Retry suppression after repeated failures | ✅ | Circuit breaker + safe-mode retry=1 |
| Graceful handling of Tally crashes | ✅ | `fetch failed` → 503, not 500 |
| Request correlation IDs | ✅ | UUID per exchange; in logs and diagnostics |
| Safe logging (never full XML bodies) | ✅ | Transport/guard log metadata only; audit file redacted |

---

## Files Changed (M2.1 Scope)

### New
- `src/tally/safety/index.ts`
- `src/tally/safety/xml-request-validator.ts`
- `src/tally/safety/tally-request-guard.ts`
- `src/tally/safety/tally-circuit-breaker.ts`
- `src/tally/safety/tally-request-auditor.ts`
- `test/unit/tally/xml-request-validator.test.ts`
- `test/unit/tally/tally-circuit-breaker.test.ts`
- `test/unit/tally/tally-request-guard.test.ts`
- `test/unit/tally/tally-request-auditor.test.ts`
- `test/unit/tally/tally-runtime-limits.test.ts`
- `test/integration/tally-safety.test.ts`

### Modified (connection layer)
- `src/config/defaults.ts`
- `src/config/index.ts`
- `src/tally/connection/tally-connection-manager.ts`
- `src/tally/connection/reconnect-manager.ts`
- `src/tally/transport/tally-http-transport.ts`
- `src/tally/tally-module.ts`
- `src/tally/core/types.ts`
- `src/services/placeholders/tally-connection.stub.ts`
- `test/unit/config.test.ts`

---

## Relationship to Milestone 3

The working tree contains uncommitted Milestone 3 extraction code (`src/extraction/`, master-data routes, etc.). **M2.1 does not depend on M3** — the safety layer wraps `TallyConnectionManager.exchange()` which M2 already established. M3 services automatically inherit safety controls when they call the connection manager.

**Recommendation:** Commit M2.1 connection hardening as a standalone changeset (either on `main` after M2 or as a patch branch) before committing M3 extraction.

---

## Known Limitations

1. **Safe reconnect probe bypasses the request guard** — intentional to avoid deadlock; probe uses minimal `License Info` with 10s timeout cap. In safe mode, auto-reconnect is disabled so this path is inactive.
2. **Response size checked after full body read** — very large responses could consume memory before rejection. Mitigated by 10MB cap and single-request mode.
3. **Circuit breaker is process-local** — not shared across multiple connector instances.
4. **No live Tally re-validation in this milestone** — all verification is mock-based per crash investigation guidance.

---

## Git State

```
Branch: main
Latest commit: 49fbc48 (M2)
Working tree: DIRTY (M2.1 safety + uncommitted M3 extraction)
Committed: No
Tagged: No
Pushed: No
```

---

**Milestone 2.1 connection hardening is complete. Stopped — awaiting your approval before commit, tag, or push.**
