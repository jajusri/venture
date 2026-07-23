# Repository Module — Stage Update (5A)

## Design

Company-scoped JSON persistence at `{databasePath}/ledgers/{companyId}.json`.

## Operations

- `upsertMany`, `insert`, `update`, `softDelete`, `delete`
- `findById`, `findByGuid`, `findByName`, `findByAlias`
- `search` with query/status/parentGroup filters, pagination, sorting
- `getStatistics`, `clearCompany`

## Rationale

Avoids new SQLite dependency in 5A while delivering full repository contract. Atomic temp-file writes protect against partial corruption.

## Future

Migrate to SQLite or embedded store in 5A.1 for transactional incremental sync checkpoints.
