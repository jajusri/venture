# ADR-005: Offline XML Ingestion Separation

**Status:** Accepted  
**Date:** 2026-07-22  
**Milestone:** 3A — Secure ERP Foundation

## Context

Budcom requires importing XML files that users export from Tally (desktop/mobile offline workflows). This is **not** the same as issuing a Tally `IMPORT` request over HTTP. Prior architecture placed XML import inside the live Tally communication dependency graph, creating risk that offline ingestion could reach live transport or be confused with server-side Tally mutations.

## Decision

Maintain a **strict architectural boundary** between two concerns:

| Module | Purpose | May access live Tally? |
|--------|---------|------------------------|
| **Live read connector** | Approved export reads via HTTP/XML API | Yes (through gateway only) |
| **Offline XML ingestion** | Parse user-selected files from disk into Budcom | **Never** |

Offline ingestion lives in `src/ingestion/offline-xml-ingestion.service.ts` and implements `XmlImportService`.

## Why offline XML import is separated

1. **Different trust model:** User-selected files are parsed locally; no network, no Tally process interaction.
2. **No IMPORT capability:** Tally's server-side `IMPORT` is forbidden in production; offline "import" means import **into Budcom**, not into Tally.
3. **Dependency isolation:** Ingestion receives only a pure XML parser instance — never `ErpReadPort`, connection manager, gateway, or transport.
4. **Architecture test enforcement:** `ingestion/` must not import `tally/transport`, `tally/connection`, or `tally/gateway`.

## Why XML remains adapter-owned

Even offline ingestion uses `TallyXmlResponseParser` because exported files follow Tally's XML schema. Parsing is an **adapter/infrastructure concern**, not business logic:

- Business services never parse XML.
- Offline ingestion parses for validation and node counting; normalization and persistence are future milestones.
- Live and offline paths share parser **types** but not **transport** or **gateway** graphs.

## Recovery strategy

When live Tally communication is unavailable (crash, hang, network failure):

1. Users can export data from Tally manually (native Tally export).
2. Budcom ingests the exported XML file through the offline ingestion path.
3. No live connector request is required for that data to enter Budcom.

Live connector recovery (circuit breaker, health probe) is separate and does not depend on offline ingestion.

## Consequences

- Two distinct "XML" paths with clear naming in documentation and code comments.
- Offline ingestion cannot accidentally send files back to Tally.
- Future: normalized offline ingestion may use ERP-neutral parse contracts; Tally schema parsing stays in adapter layer.

## References

- `src/ingestion/offline-xml-ingestion.service.ts`
- `test/unit/ingestion/offline-xml-ingestion.test.ts`
- ADR-003, ADR-006
