# Milestone 3B — Production Company Discovery

**Status:** Validated (automated + controlled live Tally validation complete)  
**Date:** 2026-07-22  
**Depends on:** Milestone 3A (`v0.3.0-secure-foundation`)

## Scope

Production-ready Tally company discovery through the existing secure ERP architecture. This milestone covers **company discovery only** — not ledgers, stock items, vouchers, sync, persistence, or UI.

## Architecture path

```
GET /companies
  → CompanyDiscoveryServiceImpl.discoverCompanies()
  → ErpReadPort.discoverCompanies()
  → TallyReadAdapter.discoverCompanies()
  → TallyReadGateway.executeApprovedRead({ operationId: COMPANY_LIST })
  → TallyConnectionManager.exchange()          [adapter-internal]
  → TallyRequestGuard.prepare()                [policy + registry + audit]
  → TallyHttpTransport.send()
  → CompanyDiscoveryParser + response contract
  → ErpCompanyDiscoveryResult (domain models + status)
```

No alternate communication path exists. Business code never sees XML.

## Approved operation

| Field | Value |
|-------|-------|
| Operation ID | `COMPANY_LIST` |
| Registry entry | `ApprovedOperationId.CompanyList` |
| Tally collection | `List of Companies` |
| Capability | `TALLY_COMPANY_READ` |
| Classification | `VERIFIED_SAFE` |
| Request kind | `Export` / `Collection` |

## Domain contract

**Port contract:** `ErpCompanyDiscoveryResult` (`src/erp/ports/company-discovery.ts`)

**Business contract:** `CompanyListResult` (`src/services/interfaces/company-discovery.ts`)

Both use contract version `1` and share `CompanyDiscoveryStatus`:

| Status | Meaning |
|--------|---------|
| `SUCCESS` | One or more trusted companies returned |
| `EMPTY` | Valid response; no companies available |
| `INCOMPLETE` | Response received but identity data cannot be trusted |
| `MALFORMED` | Envelope or XML cannot be parsed safely |
| `UNAVAILABLE` | Tally unreachable or connection not ready |
| `DENIED` | Policy or registry blocked the request |
| `TIMEOUT` | Transport timeout |

## Parsing and normalization rules

- Whitespace trimmed via `normalizeText` (Unicode NFC)
- Stable IDs via `slugify` (ASCII slug with hex fallback for non-ASCII names)
- CMPINFO count nodes ignored (`isCountMetadata`)
- Duplicate companies removed by ID
- Optional fields (`financialYear`, `booksFrom`, `baseCurrency`) omitted when Tally does not provide them — **never invented**
- Records without a usable `NAME` are excluded and counted as `recordsMissingIdentity`

## Outcome behaviour

| Adapter outcome | HTTP (via service) | Body |
|-----------------|-------------------|------|
| `SUCCESS`, `EMPTY`, `INCOMPLETE` | 200 | Full result with explicit `status` |
| `DENIED` | 403 | Error envelope |
| `UNAVAILABLE`, `TIMEOUT`, `MALFORMED` | 503 | Error envelope |

`INCOMPLETE` and `EMPTY` are **not** reported as untrusted success — they carry explicit `dataQuality` and `reason`.

## Tests added

| File | Coverage |
|------|----------|
| `test/helpers/company-discovery-fixtures.ts` | Realistic XML fixtures |
| `test/unit/tally/company-discovery-parser.test.ts` | Parser + contract assessment |
| `test/unit/tally/company-discovery-adapter.test.ts` | Adapter outcomes, service boundary |
| `test/integration/company-discovery.test.ts` | End-to-end via HTTP |

## Limitations

- `baseCurrency` only populated when Tally returns `BASECURRENCY` in company list export
- Company discovery does not detect which company is currently **open** in Tally UI — only which companies exist in the data directory
- In-process architectural guarantee only (external processes can still socket to Tally directly)
- Live `dataQuality: INCOMPLETE` may appear on otherwise valid SUCCESS responses when Tally returns CMPINFO count metadata nodes (`skippedRecords > 0`) — cosmetic quirk; top-level `status` remains `SUCCESS`
- Connector circuit breaker must be reset (restart) after Scenario 4 before subsequent discovery calls if Tally is brought back online

## Controlled live validation (2026-07-22)

Full evidence: `docs/testing/milestone-3b-company-discovery-manual-validation.md`

| Scenario | Result |
|----------|--------|
| 1 — One company | **PASS** — `SUCCESS`, one company (`estimation`), operator confirmed |
| 2 — Multiple companies | **PASS** — `SUCCESS`, two companies (`estimation`, `learn`), operator confirmed |
| 3 — No company open | **PASS** — `EMPTY`, explicit status, Tally reachable |
| 4 — Tally closed/unavailable | **PASS** — `503`/`UNAVAILABLE`, circuit breaker, `tallyReachable: false` |

**Verdict:** **MILESTONE 3B VALIDATED**

Final automated checks: lint pass, 184/184 tests pass, build pass, 12/12 architecture tests pass.
