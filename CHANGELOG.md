# Changelog

All notable changes to the Budcom monorepo are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Milestone 3C – Production Tally Groups

- **ERP-neutral groups contract** — `ErpGroupsResult` with explicit `GroupExtractionStatus`, hierarchy issues, and `ErpGroupSummary` domain models
- **`ErpReadPort.getGroups()`** — production group extraction with envelope validation, parsing, hierarchy validation, and explicit outcomes
- **Tally adapter ownership** — `GroupsParser`, `validateGroupHierarchy`, and groups response contract in adapter layer only
- **Master data service** — `getLedgerGroups()` uses `getGroups()` with explicit status, contract version, and hierarchy warnings
- **Comprehensive automated tests** — 38 new tests (parser, hierarchy, adapter, integration); Milestone 3B regression preserved
- **Controlled live Tally validation** — Scenarios 1–7 pass (2026-07-22); 28 groups on ESTIMATION; evidence in `docs/testing/milestone-3c-groups-manual-validation.md`

### Milestone 3B – Production Company Discovery

- **ERP-neutral company discovery contract** — explicit `CompanyDiscoveryStatus` outcomes (`SUCCESS`, `EMPTY`, `INCOMPLETE`, `MALFORMED`, `UNAVAILABLE`, `DENIED`, `TIMEOUT`)
- **`ErpReadPort.discoverCompanies()`** — returns typed `ErpCompanyDiscoveryResult` with reachability, data quality, and domain models (no raw XML)
- **Tally adapter ownership** — response envelope validation, defensive parsing, normalization, deduplication, and incomplete-data detection in `TallyReadAdapter` and `CompanyDiscoveryParser`
- **Production company discovery service** — `CompanyDiscoveryServiceImpl` maps port results to versioned `CompanyListResult` with explicit status
- **Comprehensive automated tests** — parser, contract, adapter, service boundary, and integration tests with realistic XML fixtures (no live Tally)
- **Controlled live Tally validation** — Scenarios 1–4 pass (2026-07-22); evidence in `docs/testing/milestone-3b-company-discovery-manual-validation.md`

## Milestone 3A – Secure ERP Foundation

**Tag:** `v0.3.0-secure-foundation`  
**Date:** 2026-07-22

First stable architectural foundation of the Budcom Tally Connector. Establishes ERP-neutral Clean Architecture, structurally read-only communication, and enforceable security boundaries.

### Added

- **ERP-neutral architecture** — `src/erp/policy/` (fail-closed policy engine) and `src/erp/ports/` (port contracts) independent of Tally-specific types
- **`ErpReadPort`** — business services depend on a stable read interface; `TallyReadAdapter` implements it and owns XML/parsing
- **Read-only enforcement** — `EXPORT`-only validator; forbidden write/execute tokens rejected; no public raw-XML API
- **Fail-closed security** — ERP-neutral policy engine; approved-operation registry; forbidden-operation registry; mandatory guard chokepoint on every live request
- **Architecture tests** — 12 dependency-boundary tests in `test/architecture/module-boundaries.test.ts`; build fails on gateway bypass or XML leakage
- **XML isolation** — raw XML and `ParsedXmlNode` confined to Tally adapter; business layer receives domain models only
- **Offline XML ingestion** — `OfflineXmlIngestionService` separated from live Tally transport graph
- **Tally read gateway** — internal typed gateway (`executeApprovedRead`) as the adapter's sole egress orchestrator
- **Response contract safety** — explicit `INCOMPLETE` data quality for derived units
- **Architecture Decision Records** — ADR-001 through ADR-006 in `docs/architecture/adr/`
- **Remediation and review documentation** — fail-open remediation report, architecture defect remediation report, communication safety design and review

### Changed

- **Production architecture stabilization** — composition root registers `ErpReadPort` instead of `TallyModule`; business services rewired through port/adapter seam
- **`TallyConnectionService`** — lifecycle and diagnostics only (`ping`, `getDiagnostics`); `exchange()` removed
- **Mandatory runtime controls** — single-flight, no auto-retry, circuit breaker always enabled regardless of `SAFE_MODE`
- **Master data and company discovery** — consume `ErpReadPort`; no direct gateway or parser dependencies

### Removed

- **Gateway bypasses** — `TallyConnectionService.exchange()`, `ServiceTokens.TallyModule`, unguarded `probeTallyReachable()` transport bypass
- **Dead architecture** — unused quarantine state, unused registry metadata fields
- **`xml-import.service.ts`** — replaced by offline ingestion module

### Security

- Known dangerous Tally payloads permanently blocked (`List of Units`, single Stock Item object export) with regression tests
- Audit intent recorded before transport with request hash and policy decision metadata
- `SAFE_MODE=false` cannot weaken baseline security policy

### Verification

- `npm run lint` — pass
- `npm run test` — 155 tests pass (including 12 architecture tests)
- `npm run build` — pass
