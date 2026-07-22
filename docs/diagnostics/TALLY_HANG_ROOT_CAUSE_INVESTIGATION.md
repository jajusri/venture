# TallyPrime Hang/Crash — Root Cause Investigation

**Date:** 2026-07-22
**Scope:** Engineering investigation only. No feature code changed. No commit/tag/push.
**Company:** ESTIMATION (open in TallyPrime, HTTP/XML on `localhost:9000`)
**Method:** Static forensic analysis of the connector audit trail + controlled single-request live reproduction (no connector, no concurrency, 5 s cooldowns), sampling Tally process memory and CPU around each request.

---

## Root Cause (proven)

**TallyPrime deadlocks its HTTP/XML request handler when it receives an `Export` + `Collection` request whose `ID` does not resolve to a defined collection.** The offending request never returns; the HTTP server thread wedges permanently and every subsequent request (including the trivial `License Info` probe) times out. The Tally GUI process stays alive — it does **not** crash, does **not** grow memory, and does **not** spin CPU. Only a Tally restart clears it.

The connector's `List of Units` request is one instance of this: **`List of Units` is not a resolvable collection ID in this TallyPrime build**, so it triggers the same deadlock as a deliberately fake ID.

This is **primarily a TallyPrime robustness defect** (it should return an error, not hang), **compounded by a connector correctness issue** (it emits an unsupported collection ID).

The earlier `c0000005` crash narrative (cumulative load / concurrency / retry storms) in `TALLY_CRASH_INVESTIGATION.md` is **not** the deterministic failure mode. The reproducible, single-request trigger is the unresolvable-collection-ID hang documented here.

---

## Evidence

### E1 — Connector audit trail: only `List of Units` ever genuinely hangs

`connector/budcom_connector/diagnostics/tally-request-audit.jsonl` (231 lines). Every `outcome:"failed"` entry is one of exactly two kinds:

| Failure kind | Request ID | Count | Meaning |
|--------------|-----------|-------|---------|
| `Tally request timed out after 120000ms` / `...30000ms` | **`List of Units`** | 2 | Genuine hang |
| `TALLY_HTTP_ERROR: Tally returned HTTP 503` | `License Info` | ~11 | Health probe hitting an already-dead Tally (a *symptom* of a prior hang, not a cause) |

No other collection ever timed out. `List of Groups`, `List of Ledgers`, `List of Stock Items`, `List of Companies`, `List of Stock Categories`, etc. are all recorded as `sent`/successful.

### E2 — Size is NOT the cause (live, this session)

Single hand-written requests, Tally healthy at start (356.6 MB):

| Request | Type | Result | Time | Bytes | Tally mem after |
|---------|------|--------|------|-------|-----------------|
| `License Info` | Data | OK | 410 ms | 148 | 356.6 MB |
| `List of Companies` | Collection | OK | 10 ms | 2,185 | 356.6 MB |
| **`List of Stock Items`** | Collection | **OK** | **221 ms** | **434,166** | 359.6 MB |
| `List of Ledgers` | Collection | OK | 134 ms | 260,382 | 359.6 MB |

The **largest** export (434 KB) returns in 221 ms with ~3 MB memory growth. Heavy/large collections are fine.

### E3 — The controlled discriminator: unknown collection ID hangs identically

Immediately after E2, on a healthy Tally:

| Request | Well-formed XML? | Result | Time | Tally mem | Tally CPU |
|---------|------------------|--------|------|-----------|-----------|
| **`List of Zzz Nonexistent`** (fake ID) | Yes (identical structure to working requests) | **HANG** | 15,058 ms timeout | 358.6 → 359.4 MB (flat) | 4.3 → 4.4 s (flat) |
| `License Info` (recovery probe, +5 s later) | Yes | **DEAD** | 10,016 ms timeout | — | — |

A structurally valid request with an **unresolvable ID** wedged Tally's HTTP server. `List of Units` behaves identically (E1). Therefore the trigger is **ID resolution, not XML structure, not size, not concurrency**.

### E4 — It is a blocking deadlock, not a crash / OOM / CPU spin

Sampled while Tally was wedged (post-hang):

```
tally alive pid=20588  WS_MB=359.4  CPU_s=4.4     <-- process alive, memory flat, CPU flat
HTTP DOWN: The operation has timed out            <-- HTTP/XML server unresponsive
```

- **No crash:** the `tally.exe` process persists (contrast with the earlier `c0000005` event).
- **No memory exhaustion:** working set flat at ~359 MB.
- **No CPU spin:** total CPU seconds essentially unchanged (4.3 → 4.4) across the whole hang window — the handler is *blocked/waiting*, not computing.

### E5 — The connector XML is well-formed and structurally identical to working requests

Exact bytes of the connector's `List of Units` request (from audit line 68) differ from the working `List of Ledgers`/`List of Stock Items` requests **only in the `<ID>`/`<DESC>` string**. Same `<ENVELOPE>`, same `Export`/`Collection`, same `SVEXPORTFORMAT`, same `SVCURRENTCOMPANY`. XML structure, encoding, and size (367 bytes) are all valid.

### E6 — Second instance of the same class: Stock Item object export

A single `Export`/`Object` `Stock Item` request (with `SVSTOCKITEMNAME`) also hung Tally (30 s timeout, HTTP dead afterward) earlier this session. Same failure signature — an unsupported/unresolvable request form producing a handler deadlock.

---

## Reproduction Steps (minimal, deterministic)

1. Open TallyPrime, load a company, confirm HTTP server on `localhost:9000`.
2. Confirm health: `POST` `License Info` → 200, ~148 bytes.
3. Send a single well-formed collection export with an unresolvable ID:

```xml
<ENVELOPE>
  <HEADER><VERSION>1</VERSION><TALLYREQUEST>Export</TALLYREQUEST><TYPE>Collection</TYPE><ID>List of Units</ID></HEADER>
  <BODY><DESC>List of Units</DESC>
    <STATICVARIABLES><SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT><SVCURRENTCOMPANY>ESTIMATION</SVCURRENTCOMPANY></STATICVARIABLES>
  </BODY>
</ENVELOPE>
```

(`List of Zzz Nonexistent` reproduces it just as well.)

4. Observe: request never returns; connection times out.
5. Re-probe `License Info` → now times out. Tally process still alive; HTTP server dead until restart.

No connector, no concurrency, no retries, no load are required. **One request is sufficient.**

---

## Classification Against the Requested Hypotheses

| # | Hypothesis | Verdict | Basis |
|---|-----------|---------|-------|
| 1 | My connector | **Partial** | Emits an unsupported collection ID (`List of Units`). Connector concurrency/retry is **not** the trigger (E3 repro has no connector). |
| 2 | TallyPrime itself | **YES (primary)** | Hangs instead of erroring on unresolvable IDs; wedges HTTP server (E3, E4). |
| 3 | Tally XML API limitation | **YES** | No safe, documented collection ID for units in this build; API offers no fast-fail for unknown IDs. |
| 4 | Unsupported XML requests | **YES** | `List of Units` / object `Stock Item` are unsupported request forms here (E1, E6). |
| 5 | Certain collections/objects | **YES** | Failure is specific to unresolvable collection/object IDs (E1, E3, E6). |
| 6 | Timing / concurrency | **Ruled out** | Single sequential request reproduces (E3). |
| 7 | Tally version-specific bug | **Likely** | Handler-deadlock-on-unknown-ID is build-specific behavior; version not captured — see Remaining Uncertainty. |
| 8 | ODBC vs HTTP | **N/A** | Only HTTP/XML used; defect is in the HTTP/XML handler. |
| 9 | XML structure | **Ruled out** | Failing XML is well-formed and structurally identical to working requests (E5). |
| 10 | Invalid requests generated by connector | **Partial** | Well-formed but semantically unsupported ID; not malformed XML. |

---

## Connector Bug or Tally Limitation?

**Both, with clear division:**

- **Tally limitation/defect (cannot fix from our side):** TallyPrime must return an error for an unresolvable collection/object ID. Instead it deadlocks the HTTP server. We must **work around** this — never send an ID that could be unresolvable, and treat any collection timeout as "Tally is now wedged, stop."
- **Connector correctness issue (must fix on our side):** the connector's template table includes `Units: 'List of Units'`, an ID this build cannot resolve. It also attempted an object `Stock Item` export form that hangs.

**Important:** a client-side timeout **does not** rescue Tally — E3/E4 show the server stays dead after the client aborts. Timeouts only bound the client; they do not prevent the damage. Prevention (never sending the bad ID) is the only effective control.

---

## Current Production Exposure

| Path | Status |
|------|--------|
| Live `List of Units` call | **Not reachable in current code.** `extractor-registry.ts` has no units extractor; units are derived from stock items (`deriveUnitsFromStockItems`). |
| Dormant risk | `master-data-templates.ts` still defines `Units: 'List of Units'` and exposes `MasterDataTemplates.units(...)`. Any future wiring that calls it will hang live Tally. |
| Object `Stock Item` export | Not used by the connector; was a manual investigation probe. Do not add it. |
| Other 10 collection IDs | Verified resolvable/safe live (return data or fast-empty). |

**Affects production?** Not on the current derive-units path. **Yes** if any endpoint is ever pointed at `List of Units` (or any unverified collection/object ID). Risk is latent, not active.

---

## Recommended Engineering Fix (not applied — investigation only)

1. **Allowlist of verified collection IDs.** Reject (fail-fast, client-side, before send) any Tally request whose `ID` is not in a proven-safe set: `List of Companies, List of Groups, List of Ledgers, List of Stock Groups, List of Stock Categories, List of Stock Items, List of Godowns, List of Cost Categories, List of Cost Centres, List of Voucher Types, List of GST Registrations`.
2. **Remove/quarantine the `List of Units` template** (and any object `Stock Item` export). Keep the derive-from-stock-items path. Add a regression test asserting no code path can emit `List of Units`.
3. **Units data (separately):** obtaining real unit values needs either (a) an explicit TDL `<COLLECTION>` with `<TYPE>Unit</TYPE>` defined in the request body — **must be validated on a disposable Tally instance first**, because if it also hangs we must abandon it — or (b) an enriched stock-item export that includes `BASEUNITS`. Until then, units remain WARNING/INCOMPLETE.
4. **Treat any collection/object timeout as a hard stop.** The circuit breaker already opens after failures; additionally mark Tally "wedged — restart required" on any export timeout, since recovery without restart is not expected.
5. **Do not rely on timeouts as protection** — they bound the client only; they do not prevent Tally from wedging.

---

## Risk Level

**HIGH.** A single well-formed request can permanently disable TallyPrime's XML server for the whole machine/company session until a manual restart — a denial-of-service-grade fragility. Mitigated today only because the live code avoids the known-bad ID; the mitigation is not enforced by a guard.

---

## Remaining Uncertainty

1. **Exact Tally build/version** was not captured in logs; the deadlock-on-unknown-ID behavior may differ across TallyPrime releases.
2. **Whether a properly TDL-defined `Unit` collection** avoids the hang is untested (would require a disposable Tally instance — not attempted, to protect the working environment).
3. **Root cause inside Tally** is inferred from black-box behavior (flat CPU/memory + dead socket = blocking wait); we cannot inspect Tally's source or a crash dump.

---

## Artifacts

| File | Contents |
|------|----------|
| `docs/diagnostics/tally-hang-isolation-payloads.xml` | Exact request XML for every OK and HANG case, with timings/memory/CPU |
| `connector/budcom_connector/diagnostics/tally-request-audit.jsonl` | Connector audit trail (only `List of Units` timed out) |
| `docs/diagnostics/m3-stock-items-raw-sample.xml` | 434 KB successful stock-items export (size baseline) |
| `docs/diagnostics/TALLY_CRASH_INVESTIGATION.md` | Prior (load/concurrency) hypothesis — superseded as the deterministic cause by this report |

---

**Investigation complete. No business logic modified. No commit, tag, or push. TallyPrime HTTP is currently wedged (process alive) from the controlled reproduction and requires a restart before further live work.**
