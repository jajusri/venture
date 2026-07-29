# Voucher Connector 0.4.0 Release Runbook

## Installation prerequisites

- Node.js 20 or newer
- Write access to the configured data and diagnostics directories
- A free loopback TCP port
- Tally reachable only for an explicitly authorized synchronization

From `connector/budcom_connector`:

```powershell
npm ci
npm run build
npm test
```

## Configuration and startup

| Variable | Default | Purpose |
| --- | --- | --- |
| `BUDCOM_CONNECTOR_HOST` | `127.0.0.1` | API bind; keep loopback unless LAN exposure is explicitly accepted |
| `BUDCOM_CONNECTOR_PORT` | `8080` | API port |
| `BUDCOM_DATABASE_PATH` | `./data/budcom-connector.db` | Connector data directory |
| `BUDCOM_TALLY_HOST` | `localhost` | Tally host |
| `BUDCOM_TALLY_PORT` | `9000` | Tally port |
| `BUDCOM_TALLY_TIMEOUT_MS` | `120000` | Tally request timeout |
| `BUDCOM_TALLY_MAX_REQUEST_BYTES` | `65536` | Maximum request size |
| `BUDCOM_TALLY_MAX_RESPONSE_BYTES` | `10485760` | Maximum response size |
| `BUDCOM_SHUTDOWN_MS` | `10000` | Graceful shutdown deadline |

```powershell
npm run build
npm start
Invoke-RestMethod http://127.0.0.1:8080/health
Invoke-RestMethod http://127.0.0.1:8080/ready
```

Startup validates configuration and directory permissions before opening the API. Health and
readiness never contact Tally.

## Controlled synchronization and live validation

There is deliberately no public synchronization endpoint. Before a controlled live run:

- Confirm Tally is running and the target company is open.
- Record the exact company name and bounded date range.
- Confirm operation `budcom.voucher.export.v1` is export/collection and read-only.
- Use an isolated database directory or take a verified backup.
- Keep the API bound to `127.0.0.1`.
- Obtain explicit authorization for this single run.

Run exactly:

```powershell
npm run validate:voucher-live -- --company "EXACT COMPANY" --from YYYY-MM-DD --to YYYY-MM-DD --database-path "D:\BudcomValidation\vouchers" --authorize-read-only-tally
```

The command refuses to run without every parameter and the authorization flag. It prints progress
and a final summary, invokes graceful shutdown, and never writes to Tally.

## API examples

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/api/v1/vouchers?company=EXACT%20COMPANY&from=2026-07-01&to=2026-07-31&page=1&pageSize=50&sort=date:asc"
Invoke-RestMethod "http://127.0.0.1:8080/api/v1/vouchers/search?company=EXACT%20COMPANY&from=2026-07-01&to=2026-07-31&partyName=Example"
Invoke-RestMethod "http://127.0.0.1:8080/api/v1/vouchers/snapshots?company=EXACT%20COMPANY"
```

Returned Voucher and snapshot identifiers can be used with their detail routes. Contracts remain
at API schema version `1.0.0`.

## Database, backup, and recovery

SQLite uses `budcom-ledger.db` under `BUDCOM_DATABASE_PATH`. Before upgrade or rollback:

1. Stop the connector.
2. Copy the complete data directory to a protected backup location.
3. Retain the application version with the backup.
4. Restart and confirm `/health` reports `databaseAccessible: true`.

The next controlled synchronization discards interrupted Pending, Writing, or Validated snapshots.
Failed synchronization preserves the previously promoted snapshot. If integrity checks fail, stop
the connector and restore the last verified application/database backup pair. Never edit snapshot
tables manually.

## Shutdown and rollback

Use `Ctrl+C`, `SIGINT`, or `SIGTERM`. The connector stops its listener and services, releases
reservations, and closes SQLite within the configured deadline.

For rollback, stop the connector, restore both the prior application and its matching pre-upgrade
database backup, restart on loopback, and verify `/health` and `/ready`. Do not run an older binary
against a database migrated by a newer release.

## Known limitations

- No authentication or authorization; protect any explicitly acknowledged LAN exposure externally.
- No public synchronization endpoint or scheduler integration.
- No Voucher UI.
- Health reflects local state and cached Tally diagnostics; it does not probe Tally.
- Voucher access is read-only and company-scoped.
