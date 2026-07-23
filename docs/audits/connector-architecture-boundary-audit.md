# Architecture Boundary Audit

## Dependency flow (verified)

```
API → services → erp/ports, erp/ledger
services/ledger → storage/sqlite, erp/ports (ErpReadPort)
tally/adapter → implements ErpReadPort, uses extraction (coupled)
storage/sqlite → erp/ledger only (CONTAINED)
```

## ERP-neutral layers (clean)

- `erp/ledger/*`, `erp/policy/*` — no Tally imports
- `services/ledger/*` — uses `ErpReadPort`; no Tally imports
- `storage/sqlite/*` — no Tally references

## Leakage (pre-existing, not introduced by 5A-P)

| Location | Issue | Severity |
|----------|-------|----------|
| `extraction/*` (4 files) | Direct Tally gateway/XML imports | Design weakness |
| `extraction ↔ tally` cycle | Bidirectional compile coupling | Design weakness |
| `tallyReachable` DTO fields | Tally-named port fields | Questionable |
| `config.tallyRetry*` in ledger sync | Tally-named retry config | Questionable |

## Busy adapter thought experiment

**Would need change:** new `src/busy/` module, bootstrap wiring, generic `erpReachable`, decouple `extraction/` Tally specifics into `tally/extraction/`.

**Would not need change:** `services/ledger/`, `storage/sqlite/`, desktop ledger contracts.

## Recommendation

Accept for 5A-P commit. Track extraction decoupling as future milestone debt (not 5A-P blocker).
