# API Contract Audit

## Ledger endpoints verified

All 11 ledger/sync/storage routes present with pagination bounds (max pageSize 100), sort allowlist, structured errors, 409 on concurrent sync.

## Issues found and fixed

- `GET /sync/ledgers/runs/:id` — now company-scoped (404 for other company)  
- Backup response — filename only, not absolute path  
- Desktop `syncLedgers` — extended timeout (600s), single request (no retry storm)

## Remaining gaps (non-blocking)

- `status` query filter cast without allowlist (ignored silently for invalid values)  
- No request body size override beyond Express default ~100KB  
- Admin storage endpoints not exposed via desktop IPC (intentional)

## Error consistency

`AppError.toResponse()` used consistently; 500 generic for unexpected errors.
