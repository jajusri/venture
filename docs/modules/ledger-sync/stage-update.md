# Ledger Sync Module — Stage Update (5A)

## Scope

First persisted business-data pipeline: Tally ledgers → ERP-neutral domain → local repository → connector API → desktop UI.

## Components

| Component | Location |
|-----------|----------|
| Domain | `src/erp/ledger/` |
| Sync service | `src/services/ledger/ledger-sync.service.ts` |
| Repository | `src/services/ledger/ledger-repository.ts` |
| Routes | `src/api/routes/ledgers.ts` |
| Desktop service | `apps/budcom_desktop/src/application/ledger-service.ts` |

## Sync Workflow

1. Validate connector session + selected company
2. Extract ledgers via `ErpReadPort.readLedgers()`
3. Map to `LedgerDetails`, validate collection
4. Upsert into company-scoped JSON store
5. Expose progress/statistics via API

## Status

Production-ready foundation. Incremental sync compares alterId/GUID/balances. Full interrupted-sync recovery deferred to 5A.1.
