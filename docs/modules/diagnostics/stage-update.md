# Diagnostics Module — Stage Update

**Module:** Desktop Diagnostics (+ connector diagnostic API sanitization)
**Milestone:** 4D base · Reliability Step 3 hardening
**Last updated:** 2026-07-24
**Paths:** `apps/venture_desktop/src/application/diagnostics-service.ts`, `diagnostic-allowlist.ts` · `connector/venture_connector/src/diagnostics/diagnostic-allowlist.ts`

---

## Current status

**Export surfaces hardened — Control #4 partial (restored and validated on current main)**

Restored from stash `pre-existing-reliability-step-3-work` onto current `main` (includes GUID-first ledger remediation at `dfb4720`, repository hygiene at `daed443`, schema v5, TD-006 retry lineage, and atomic/concurrency-safe sync batches). Diagnostics Step 3 remains independent from those completed areas.

User-triggered diagnostic snapshot, clipboard summary, and JSON bundle export on desktop, plus connector connection/extraction diagnostic API fields, are built through explicit allowlist DTOs with serialized absence tests.

On-disk desktop log files and normal dashboard/session UI may still contain company names from operational logging.

---

## Restoration and compatibility review

| Check | Result |
|-------|--------|
| Stash applied cleanly onto `main` | **PASS** — 13 files, no merge conflicts |
| Overlap with ledger identity / schema v5 / sync batches | **None** — diagnostics-only paths |
| Removed unrelated `positionalResumeUnsupported` from desktop `privacyPolicy` | **Done** |
| Fixed mid-file import in `types.ts` | **Done** |
| Replaced configuration redaction with explicit configuration DTO | **Done** |
| GUID-first ledger code unchanged | **Confirmed** |

**Separate completed work (not merged into this milestone):** GUID-first ledger extraction and identity migration at commit `dfb4720`. That work addressed ledger sync identity, SQLite schema v5, and live validation evidence — not diagnostic export privacy.

---

## Capabilities

- Diagnostics snapshot aggregation (versions, OS, lifecycle, safe session display)
- Copy diagnostics summary to clipboard (allowlisted text)
- Export sanitized JSON bundle to `{userData}/diagnostics-exports/` via `SafeDiagnosticBundleV1`
- Recent lifecycle events and errors in UI (sanitized on export; metadata stripped)
- Health check action
- Open logs folder
- Clear nonessential logs (with confirmation)
- Connector `/diagnostics/connection` and `/diagnostics/extraction` return sanitized allowlisted fields

---

## Allowlist architecture (v1)

**Construction model:** Diagnostic outputs are built from explicit allowlisted field mappers (`buildSafeDiagnosticBundle`, `buildSafeDiagnosticConfiguration`, `sanitizeConnectionDiagnostic`, `sanitizeExtractorDiagnostic`). Internal operational objects are not serialized wholesale with post-hoc deletion.

**Configuration DTO (`SafeDiagnosticConfigurationV1`):** field-by-field mapper from `DesktopConfigV1` + load status + source summary. Raw hosts, paths, company identifiers, secrets, and unknown properties are dropped.

**Retained configuration fields:**

| Field | Purpose |
|-------|---------|
| `status` | Config load outcome (bounded text) |
| `schemaVersion` | Desktop config schema version |
| `connector.hostScope` | Loopback vs LAN classification (no raw host string) |
| `connector.port` | Connector listen port |
| `connector.autoStart` | Desktop-managed auto-start flag |
| `tally.hostScope` | Tally endpoint exposure classification |
| `tally.port` | Tally port |
| `lifecycle.*` | Health poll, startup/shutdown, restart, and reconnect timings |
| `logging.level` | Approved log-level enum |
| `logging.diagnosticsRetentionDays` | Diagnostics retention window |
| `sourceSummary.*` | Count/presence of environment overrides (no field names) |

**Nested objects:** Independently allowlisted (`SafeLastRequestDiagnostic`, `SafeRuntimeLimitsDiagnostic`, `SafeDiagnosticSession`, log entries, configuration sub-objects).

**Bounds:** Bundle 256 KB; string 500 chars; config status 120 chars; 25 log entries; 20 extractors; truncation suffix `… [truncated]`.

**Text sanitization:** Error messages, log lines, and path basenames pass through pattern-based redaction for XML blocks, GSTIN, amounts, GUID/AlterID/MasterID, tokens, emails, phones, addresses, entity names, and licence identifiers before export.

**Company identity in diagnostics:** `sessionDisplayLabel` uses status + presence only (e.g. `ACTIVE · company selected`). No company name in bundle or summary.

**Allowed:** connector/desktop version/build, OS/architecture/runtime, connection mode, bind-host classification, port, typed error codes, HTTP/protocol status, byte sizes, durations, retry counts, sync stage/resource kind, aggregate counts, schema/health indicators, ephemeral correlation id, capability statuses, timestamps, boolean config presence.

**Prohibited in export surfaces:** company/party/ledger/stock-item names, tax identifiers, voucher numbers, amounts/quantities, addresses/phones/emails, raw XML, Tally bodies, credentials, unrestricted env vars, HTTP headers, identifiable paths, database contents, arbitrary serialized errors, nested `cause` objects, log entry metadata, raw configuration hosts/paths, unknown configuration properties.

---

## Test evidence

| Suite | Result |
|-------|--------|
| `apps/venture_desktop/test/unit/diagnostic-privacy.test.ts` | **21/21 PASS** — serialized absence, exact configuration key tests, positive operational field tests |
| `apps/venture_desktop/test/unit/milestone-4d.test.ts` | **17/17 PASS** — includes export bundle allowlist version |
| `connector/venture_connector/test/unit/diagnostics/diagnostic-allowlist.test.ts` | **8/8 PASS** |
| `connector/venture_connector` full suite (`npx vitest run`) | **390/390 PASS** (unchanged by configuration correction) |
| `connector/venture_connector/test/architecture/module-boundaries.test.ts` | **12/12 PASS** (unchanged) |
| `apps/venture_desktop` full suite (`npm test`) | **96/96 PASS** |

Live evidence (pre-Step-3): `docs/diagnostics/m4d-live-validation.json`

---

## Validation commands (2026-07-24)

```text
# Connector (unchanged by configuration correction)
cd connector/venture_connector
npm run lint          # PASS (prior run)
npm run build         # PASS (prior run)
npx vitest run        # 390/390 PASS (prior run)

# Desktop
cd apps/venture_desktop
npm run lint          # PASS
npm run build         # PASS
npx vitest run test/unit/diagnostic-privacy.test.ts  # 21/21 PASS
npm test              # 96/96 PASS
```

---

## Remaining limitations

- On-disk log files not redacted at write time
- No unified connector diagnostic bundle exporter
- Normal company-selection, dashboard, ledger, sync, and master-data APIs unchanged (not diagnostic export)
- Committed operational-validation evidence not rewritten
- Bundle `environment` section still exports prefix-filtered `VENTURE_`/`NODE_ENV`/`ELECTRON_` variables via separate allowlist helper (not part of configuration DTO)

---

## Production readiness

**L4 — Live Validated (4D)** · **Reliability Step 3 — export privacy hardened (controlled pilot, restored on current main)**

Unrestricted production not approved from this change alone.
