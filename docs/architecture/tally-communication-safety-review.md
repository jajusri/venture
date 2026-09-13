# Architecture Review — ERP Communication Safety Design (Self-Critique)

**Reviewer stance:** Chief Software Architect, 20-year horizon, thousands of companies, fragile-ERP-as-banking-core.
**Subject:** `docs/architecture/tally-communication-safety.md` (the proposed design) and the current `src/tally/**` code.
**Mandate:** Challenge every assumption. No code. Harshest reasonable critique.

**Headline judgement:** The proposed design is directionally correct and materially better than what exists, but it **over-claims safety**. Several "impossible" guarantees are actually "impossible-by-convention," and at least one **read-only violation is live in the current code**. It is not yet a 20-year, thousands-of-tenants engine. Details below.

---

## Answers to the 10 Review Questions (verdicts first)

| # | Question | Verdict | Core reason |
|---|----------|---------|-------------|
| 1 | Can a future dev bypass the pipeline? | **YES** | The transport is an injectable singleton that accepts a raw body; `private exchange` is compile-time only; process can open a raw socket. |
| 2 | Can XML reach Tally skipping stages? | **YES** | Direct `ErpTransport.send()` and the health-probe path both talk to Tally outside the full pipeline. |
| 3 | Can BUSY/SAP/Zoho/Marg/ERPNext reuse the same engine? | **PARTIALLY** | Pipeline shape is reusable; but XML/ENVELOPE, redaction, "health-before-and-after," and the crash taxonomy are Tally-shaped. No auth/token/pagination/429 abstraction. |
| 4 | Is every risk config-driven vs hard-coded? | **NO / MIXED** | Registry is data (good), but a single `VENTURE_TALLY_SAFE_MODE=false` disables the protections and re-enables pool>1 + retries. |
| 5 | Survive future Tally XML changes without arch change? | **MOSTLY** | Request contracts are ours; but registry is **version-blind**, and response parsing is brittle (silent data loss on drift). |
| 6 | SOLID / loosely coupled / DIP? | **PARTIALLY** | `ErpTransport` DIP is good; `TallyRequestGuard` is a God-object (SRP), core still carries Tally names (OCP). |
| 7 | Retries/health/timeouts/quarantine/recovery/diagnostics/audit centralized? | **NO (today) / IMPROVED (proposed)** | Today they are spread across 6 modules; per-extractor timeouts are scattered; diagnostics are in-memory only. |
| 8 | Can every request be reconstructed from logs? | **NO** | Audit is redacted (destroys exact bytes), responses are **not** logged, audit is disable-able, file is unrotated/local/untamper-evident, and no end-to-end trace id. |
| 9 | Fail-closed not fail-open? | **MOSTLY, WITH FAIL-OPEN HOLES** | Master off-switch, disable-able audit, audit-after-send, in-memory circuit reset on restart, and IMPORT/EXECUTE allowed. |
| 10 | What would enterprise auditors criticize? | **A lot** | See §"What auditors would criticize." |

---

## Strengths (credit where due)

1. **Prevention-first thesis is correct.** The design correctly concludes that timeouts do not protect a fragile server and that an allow-list is the only real control. That is the right north star.
2. **Default-deny registry + typed descriptors** is the right pattern and would eliminate the specific `List of Units` class of failure — *if* the transport is truly sealed (it is not yet).
3. **`ErpTransport` already exists** — a genuine seam for dependency inversion and future ERPs.
4. **Explicit forbidden registry + permanent regression suite** is exactly how you stop regressions reintroducing dangerous requests.
5. **Failure-mode taxonomy and pre/post health gating** are appropriate for a fragile desktop ERP and materially better than today's "everything is a 503."
6. **The design already ships a candid technical-debt section** — honesty is an architectural asset.

---

## Weaknesses (concrete, code-grounded)

### W1 — The "no bypass" guarantee is by convention, not by construction *(Q1, Q2)*
- `TallyHttpTransport` is registered in the DI container and implements `ErpTransport`. Any service can resolve the transport (or be handed it) and call `send({ body: '<ENVELOPE>...anything...' })`, skipping validation, allow-list, rate limit, circuit, and audit entirely.
- TypeScript `private exchange(xml)` and "no public string overload" are **compile-time** fictions. `as any`, JS interop, or simply editing the class defeats them.
- At the process level nothing stops `fetch('http://localhost:9000', …)` or a raw socket. The connector cannot enforce safety on code that does not go through it.
- **Consequence:** "unsafe requests can NEVER reach Tally" is false as stated. It is "cannot reach Tally *through the sanctioned path*."

### W2 — Read-only is NOT enforced at the egress safety layer *(Q9 — critical)*
- `src/tally/safety/xml-request-validator.ts:13-14`:
  `ALLOWED_TYPES = { EXPORT, IMPORT, EXECUTE }`, `ALLOWED_REQUEST_KINDS = { DATA, COLLECTION, OBJECT, FUNCTION }`.
- The safety validator **permits writes (`IMPORT`) and server-side function execution (`EXECUTE`/`FUNCTION`)** on a connector whose entire premise is read-only. An `XmlImport` service is even registered in DI (`ServiceTokens.XmlImport`).
- The read-only middleware exists only at the inbound HTTP API layer; the **Tally-egress** path has no read-only assertion.
- **This is a live fail-open**, not a hypothetical: a careless change could send a mutating request to a customer's books.

### W3 — Safety has a master off-switch *(Q4, Q9)*
- `resolveTallyRuntimeLimits(config)` (`tally-request-guard.ts:32-56`): when `tallySafeMode=false`, pool = `config` (can be >1), retry = `config` (>1), `autoReconnect=true`, `maxReconnectAttempts=MAX_SAFE_INTEGER`.
- Setting one env var reintroduces **exactly** the concurrency + retry-storm conditions of the original crash. Safety that can be disabled by configuration is not safety for a banking-core-grade system.

### W4 — Per-process safety only; no coordination against a shared Tally *(Q1, scalability)*
- Single-flight, rate limit, and circuit are **in-memory, per process**. Two connector instances (HA, blue/green, or an accidental second process) each believe they are "concurrency = 1" while collectively hammering one Tally gateway. For "thousands of companies," a shared Tally/gateway makes the guarantee void.

### W5 — `TallyRequestGuard` is a God-object *(Q6)*
- It performs circuit checks, XML validation, single-flight, rate limiting, audit, and state — five responsibilities. This violates SRP and makes the pipeline stages non-composable and hard to reuse per-ERP. The pipeline should be independent middleware with a single ordering authority.

### W6 — The "ERP-agnostic core" still smells of Tally *(Q3, Q6)*
- Schema validation assumes `<ENVELOPE>`; redaction targets `<SVCURRENTCOMPANY>`/`<NAME>`/`<GSTREGISTRATIONNUMBER>`; the crash taxonomy is about "hung desktop server"; health-before-and-after assumes a cheap `License Info`. None of this transfers to OAuth2/REST/JSON cloud ERPs (Zoho, ERPNext) or IDoc/BAPI (SAP). Reuse needs per-ERP **policy objects** (auth lifecycle, backoff/429 strategy, failure taxonomy, health strategy, redaction rules) — absent from the design.

### W7 — Forensics cannot reconstruct requests or responses *(Q8)*
- Audit stores **redacted** XML → exact bytes are unrecoverable by design.
- **Responses are not stored at all** (only `requestByteLength`) → a bad parse or unexpected XML cannot be forensically reproduced.
- Audit is **disable-able** (`tallyRequestAuditEnabled`) and the "sent" record is written **after** transport (guard `recordSuccess`), so a crash between send and audit loses the record → the request hit Tally with no trace.
- The audit file is local, single-node, **unrotated**, unbounded, not tamper-evident, not centrally shipped. No end-to-end trace id links an inbound API call to the Tally exchange (correlation IDs are minted per attempt inside `exchange`).

### W8 — Circuit is non-persistent and self-reopening *(Q9)*
- In-memory circuit resets to `closed` on connector restart → a still-wedged Tally gets hit again. `half_open` (today) admits a real business request rather than a health probe. The proposed design fixes half-open but not persistence.

### W9 — Over-aggressive fail-closed can cause chronic outages *(availability)*
- Proposed Safe-Mode circuit threshold = 1 means a single transient network blip quarantines the entire ERP for the whole cooldown. With no hysteresis and a fixed cooldown, this can **flap** or cause long false outages across thousands of tenants. Safety vs availability is untuned.

### W10 — Timeouts are cosmetic for the actual failure *(Q5, Q9)*
- `AbortController` cancels the client wait, not Tally's server-side work (proven). The design still leans on per-registry timeouts to *detect* hangs. Detection ≠ protection; the only protection is the allow-list, so any weakness in W1/W2/W3 is unmitigated by timeouts.

---

## Hidden Risks (the ones that bite in year 3, not week 1)

1. **Governance, not code, is the real backstop.** Every guarantee ultimately depends on humans not editing the registry, the forbidden list, the regression suite, or `ALLOWED_TYPES`. Without protected files (CODEOWNERS), required reviews, and CI gates on those specific files, the architecture is one careless PR from unsafe. This is the biggest hidden risk and it is organizational.
2. **Empirical allow-list rot.** SAFE/FORBIDDEN was derived from **one company, one Tally build**. Across 20 years and many Tally releases and datasets, a "SAFE" collection can start hanging (or `List of Units` could become safe). The registry is **version-blind** and has no automated re-verification pipeline. Silent drift.
3. **Response-schema drift → silent data corruption.** Regex/tag-based parsing (`entity-mappers`) will not throw on a changed Tally schema; it will quietly return partial/empty data (we already saw units silently absent). For financial data this is worse than a crash.
4. **Health-check load amplification.** Pre+post `License Info` triples request volume and adds mandatory delays. At scale (many companies, frequent syncs) this is a throughput and, for cloud ERPs, a rate-limit problem.
5. **PII/audit tension.** True forensic replay needs unredacted capture; privacy/compliance needs redaction. Storing raw financial XML for years is itself a compliance liability. The design has not resolved this — it just redacts and thereby loses forensics.
6. **Plain HTTP to Tally, no authn/authz between connector and Tally, and unclear authz on the connector's own API.** A local-only assumption that will not hold in networked deployments.
7. **Speculative multi-ERP generality (YAGNI).** Building an "ERP-agnostic engine" before a second ERP exists risks the classic wrong abstraction: it will encode Tally assumptions as "universal," then need a redesign anyway when SAP arrives. The abstraction is currently unfalsifiable.

---

## Future Scalability Concerns

- **Concurrency model does not scale horizontally.** Per-process single-flight cannot protect a shared Tally. Needs a distributed lease/lock or a single serializing egress broker per ERP endpoint.
- **No telemetry backbone.** In-memory `TallyDiagnosticsSnapshot` does not aggregate over time or across nodes. 20-year ops needs OpenTelemetry traces/metrics/logs shipped centrally, with SLOs and alerting.
- **No version matrix.** Registry, contracts, and crash rules must be keyed by ERP + ERP version; a flat registry will not survive many Tally releases.
- **No supervisory watchdog.** "Process alive but wedged" is detected but not remediated. Unattended operation needs an external supervisor to restart the ERP and to hold quarantine across connector restarts.
- **Config sprawl as a risk surface.** Each new safety knob (`SAFE_MODE`, `POOL_MAX`, `RETRY_MAX`, `AUDIT_ENABLED`, `CIRCUIT_*`) is a potential fail-open. Safety-critical settings should not be operator-tunable to unsafe values.

---

## Recommended Improvements (priority order)

**P0 — correctness/safety holes (do before any "production" claim):**
1. **Seal the transport.** Make `ErpTransport` un-resolvable by domain services; expose only `SafeErpClient`. Enforce with an architecture/lint boundary test (e.g., dependency-cruiser) that fails CI if anything but the client imports the transport.
2. **Enforce read-only at egress.** Restrict `ALLOWED_TYPES` to `EXPORT` only (and `DATA/COLLECTION/OBJECT`), forbid `IMPORT`/`EXECUTE`/`FUNCTION` in the safety layer, delete/quarantine the import path, add a regression test.
3. **Remove the master off-switch for unsafe values.** `SAFE_MODE=false` must not permit pool>1 / infinite reconnect. Make hard caps non-overridable; allow only *stricter* overrides.
4. **Audit before send, and audit responses.** Write an "intent" record pre-send, update with outcome + response metadata (status, bytes, hash) post-receive. Make auditing non-disable-able for safety-relevant requests.

**P1 — resilience/forensics:**
5. **Persist circuit/ERP state** to disk; require a passing health check on boot before leaving quarantine.
6. **End-to-end trace id** from inbound API → Tally exchange; structured, rotated, append-only, centrally shippable audit; retention + tamper-evidence policy; a secured unredacted "forensic vault" mode gated by compliance config.
7. **Distributed egress serialization** (single broker or lease) so concurrency=1 holds per Tally endpoint, not per process.
8. **Quarantine hysteresis** — exponential/backoff reopen, jitter, and a max-outage guard to avoid flapping.

**P2 — evolvability/reuse:**
9. **Version-keyed registry & contracts**; an automated safe-collection re-verification harness run against a disposable Tally per version.
10. **Decompose the God-object** into ordered, single-purpose middleware; introduce per-ERP **policy objects** (auth, backoff, failure taxonomy, health, redaction).
11. **Defer the generic multi-ERP engine** until a second adapter is concretely required; until then, keep the seam (`ErpTransport`, descriptors) but stop calling the core "ERP-agnostic."
12. **Governance controls:** CODEOWNERS + required review + CI gate on registry/forbidden-list/`ALLOWED_TYPES`/regression files; treat these as safety-critical artifacts.

---

## What Senior Enterprise Auditors Would Criticize *(Q10, consolidated)*

- A read-only product whose egress layer permits writes and function execution (**would be flagged as a control failure**).
- Safety disabled by a single environment variable (no separation between tuning and safety-critical config).
- Per-process guarantees presented as system guarantees; no proof under multi-instance/shared-endpoint.
- Forensics that cannot reproduce a request or any response; disable-able, unrotated, local, non-tamper-evident audit.
- "Impossible to bypass" claims backed by convention (`private`, code review) rather than enforced boundaries.
- Empirical, single-environment, version-blind allow-list with no re-verification pipeline.
- No threat model / FMEA, no SLOs, no quarantine runbook, no watchdog for unattended recovery.
- Premature "ERP-agnostic" abstraction with no second implementation to validate it.
- Plain HTTP, no mutual auth to the ERP; unclear authz on the connector's own data-exposing API.

---

## Final Production-Readiness Score

Scoring the **communication layer only**, for the stated bar (20 years, thousands of tenants, fragile-ERP-as-banking-core).

| Subject | Score /10 | Rationale |
|---------|-----------|-----------|
| **Current code (`src/tally/**` as-is)** | **3.0** | Good primitives (circuit, single-flight, audit, transport seam) but no allow-list, IMPORT/EXECUTE permitted, master off-switch, bypassable transport. Would crash Tally on the next unlisted ID. |
| **Proposed design, if built exactly as written** | **6.0** | Fixes the specific hang class and adds prevention-first controls, but leaves W1–W4, W7 (bypassable transport, read-only egress, off-switch, cross-process, forensics) unresolved. |
| **Proposed design + all P0 + P1 fixes** | **8.0** | Enforced boundaries, read-only egress, no unsafe off-switch, persisted quarantine, real forensics, distributed serialization. Credible for enterprise unattended operation. |
| **Full P0–P2 + governance + second ERP proven** | **9.0** | The 20-year, multi-ERP bar. 10/10 is not achievable while any external process can open a socket to the ERP — that residual is inherent, not fixable in-connector. |

**Bottom line:** the design is a strong *direction* but currently earns **6.0/10** and must not be described as "impossible to crash" or "read-only-guaranteed" until at least the four P0 items are implemented and enforced by CI boundary tests. The single most urgent fix is **W2 (read-only egress enforcement)**, followed by **W1 (seal the transport)**.

---

**Review only. No code written, nothing committed, tagged, or pushed.**
