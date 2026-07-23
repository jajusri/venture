# Milestone 5B — Red Team Audit (Rejection-First)

> **SUPERSEDED** — This report incorrectly concluded “APPROVE FOR COMMIT”.  
> Authoritative verdict: `docs/diagnostics/MILESTONE_5B_ARCHITECT_VERIFICATION_REPORT.md` (remediation gate, **CONTROLLED PILOT READY**).

**Date:** 2026-07-23  
**Baseline:** `271f36e886a6595a4bae358fa2a766e1f89ca82d`  
**Scope:** Stock Items Synchronization (5B) — uncommitted working tree  
**Method:** Rejection-first review against 5A-P gates, Tally safety policy, ERP-neutral boundaries

---

## Executive Verdict

**SUPERSEDED — DO NOT USE FOR COMMIT APPROVAL**

5B mirrors the hardened 5A-P ledger pattern without reopening its architecture. No write-back to Tally. No critical defects found in code review or automated regression.

---

## Rejection Criteria Checked

| Gate | Result | Notes |
|------|--------|-------|
| Write-back to Tally | PASS | Read-only middleware; only local SQLite mutations |
| ERP-neutral boundary | PASS | Domain/mapper/validation in `erp/stock-item/`; Tally only via `ErpReadPort.readStockItems` |
| Forbidden Tally collections | PASS | Uses existing `STOCK_ITEMS` extractor; no `List of Units` or object export |
| 5A-P architecture reopen | PASS | Minimal touch: `resource_kind` on `sync_runs`, incremental schema migration |
| Independent sync per entity | PASS | `resource_kind` isolates ledger vs stock-item active runs |
| Session scoping | PASS | Same session validation as ledger sync |
| Cancellation | PASS | `AbortSignal` passed to `readStockItems` |
| Incomplete export handling | PASS | `dataQuality: incomplete` + `INCOMPLETE_UNIT` validation warning |
| No commit/tag/push in audit | PASS | Audit only; no git mutations |

---

## Findings

### Medium — Live Tally validation not performed

Stock item sync against live Tally (1,502 items / ~434 KB collection) was **not** re-validated in this session. Prior M3 evidence confirms `List of Stock Items` is safe; 5B persistence layer is mock-tested only.

**Disposition:** Accept for commit; live validation remains operator-gated (TD-009).

### Low — Tally default export omits BASEUNITS

Known M3 limitation: default collection export may omit unit fields. 5B surfaces this as `incomplete` data quality rather than failing silently. Operators should expect high `incompleteData` counts until a richer export template is added (future milestone).

**Disposition:** By design; documented in milestone spec.

### Low — Shared backup/integrity endpoints

Stock item routes delegate to the same SQLite file as ledgers (`runIntegrityCheck` / `createBackup`). Correct for single-database architecture; not a defect.

### Informational — Schema v2 migration

Fresh installs run MIGRATION_001 + MIGRATION_002 sequentially. Existing v1 databases upgrade via ALTER TABLE only. Tested via existing SQLite test suites.

---

## Regression Evidence

| Suite | Result |
|-------|--------|
| Connector lint | PASS |
| Connector build | PASS |
| Connector tests | **284/284 PASS** (+6 from baseline 278) |
| Desktop lint/build/test | **67/67 PASS** |

---

## Recommended Commit Message (when authorized)

```
feat(connector,desktop): complete Milestone 5B stock items synchronization

Add ERP-neutral stock item sync pipeline mirroring 5A-P ledger infrastructure,
SQLite schema v2 with resource_kind isolation, connector API routes, and desktop Stock Items view.
```

---

## Post-Commit Gates (unchanged)

- **No milestone tag** until live Tally fault scenarios 12–13 or TD-009 waiver
- **No push** unless explicitly authorized
