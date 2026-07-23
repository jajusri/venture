# Reliability & Concurrency Audit

## Sync state machine

Valid states observed: idle, running, cancelling, cancelled, completed, failed, interrupted.

## Confirmed defects (fixed)

| Defect | Fix |
|--------|-----|
| `cancelSync` after completion corrupts DB record | Guard on active in-flight sync; clear `activeRun` in `finalizeRun` |
| Counter drift on batch upsert failure | Counters updated after successful `upsertMany` |
| Duplicate sync across processes | `BEGIN IMMEDIATE` + active-run check in `createRun` |
| Cancel ignored during retry backoff | `sleepWithAbort()` |

## Remaining concerns

- **Full resume** not implemented — interrupted sync re-processes all ledgers  
- **`recovering` status** — defined in queries but never set (latent)  
- **stop() during sync** — aborts but run may remain `running` until next startup recovery  

## Race tests added

`ledger-sync-audit.test.ts`: post-completion cancel, company-scoped getSyncRun
