# Connector Pre-Commit Deep Audit (Milestone 5A-P)

**Audit date:** 2026-07-23  
**Auditor stance:** Independent rejection-first review  
**Baseline HEAD:** `9e24483b038e3417c2731e662ff8fe551bff6057` (5A-P uncommitted)  
**Verdict:** **CONTROLLED PILOT READY** — safe to commit; not safe to tag until live Tally fault matrix completed

## Executive summary

The 5A-P implementation delivers real production hardening (SQLite, durable sync runs, migration, cancellation chain, API/desktop UX). Prior readiness claims (261/261 tests, builds passing) were **verified**. Independent audit found **11 confirmed defects**; **9 were fixed** during this audit with regression tests. Two architectural limitations remain accepted (mid-response Tally cancellation; extraction-layer Tally coupling predating 5A-P).

**Safe to commit:** Yes, after audit fixes included in working tree.  
**Safe to tag `milestone-5a-p`:** No — live Tally scenarios 12–13 blocked; authenticated LAN access not implemented (TD-009).

## Test evidence (post-fix)

| Suite | Result |
|-------|--------|
| Connector | **264/264 PASS** (+3 audit regression tests) |
| Desktop | **67/67 PASS** |
| Connector build | PASS |
| Desktop build | PASS |
| npm audit (prod deps) | 0 vulnerabilities |

## Confirmed defects found and disposition

| ID | Severity | Finding | Status |
|----|----------|---------|--------|
| AUD-001 | High | WAL backup without checkpoint | **Fixed** |
| AUD-002 | High | Migration marked complete when no legacy JSON | **Fixed** |
| AUD-003 | Medium | `cancelSync()` corrupts completed run records | **Fixed** |
| AUD-004 | Medium | Cross-company `getSyncRun` IDOR | **Fixed** |
| AUD-005 | Medium | Progress counters updated before batch commit | **Fixed** |
| AUD-006 | Medium | Desktop sync 10s timeout + AbortError retry | **Fixed** |
| AUD-007 | Medium | `getStorageStatus()` false healthy | **Fixed** |
| AUD-008 | Low | Migration path traversal via filenames | **Fixed** |
| AUD-009 | Low | Backup API returned full filesystem path | **Fixed** |
| AUD-010 | Medium | Multi-process duplicate sync runs | **Fixed** — `BEGIN IMMEDIATE` validated with dual-connection integration test |
| AUD-011 | Low | Retry backoff ignored cancellation signal | **Fixed** |

## Accepted risks (not blocking commit)

1. **Mid-response Tally cancellation** — cooperative only; documented in TD-007  
2. **Non-loopback LAN bind without authentication** — explicit operator acknowledgement only; see TD-009  
3. **Sync resume from `lastProcessedId`** — schema present; full resume not implemented (full re-sync on restart)  
4. **Extraction ↔ Tally coupling** — pre-existing; blocks Busy adapter without refactor  
5. **Live Tally fault injection** — not available in audit environment  

## Production gate (independent score): **83/100**

Improved from 78/100 after loopback default (TD-008 resolved), multi-connection sync-run validation, and automated backup/restore drill. Score remains below tag-ready threshold due to blocked live Tally fault scenarios and open TD-009 (no authenticated LAN access).

## Cross-references

- Architecture: `connector-architecture-boundary-audit.md`
- Security: `connector-security-audit.md`
- Database/migration: `connector-database-migration-audit.md`
- Reliability/concurrency: `connector-reliability-concurrency-audit.md`
- API: `connector-api-contract-audit.md`
- Tests: `connector-test-quality-audit.md`
- Performance: `connector-performance-audit.md`
- Documentation truth: `connector-documentation-truth-audit.md`
- Machine evidence: `../diagnostics/connector-precommit-audit-evidence.json`
