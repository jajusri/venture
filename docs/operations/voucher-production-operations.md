# Voucher Production Operations

## Startup requirements

The connector validates its API bind host and port, SQLite data directory, Tally connection
limits, shutdown timeout, session lifetime, and request/response limits before starting services.
The database directory and, when enabled, the Tally request-audit directory must be readable and
writable. Startup fails before opening the API listener when a requirement is not satisfied.

The default API binding is loopback-only. Production LAN binding still requires the existing
explicit acknowledgement. Startup validation does not contact Tally.

## Synchronization and snapshot lifecycle

Voucher synchronization extracts through the approved read-only Tally operation, writes bounded
batches into an isolated snapshot, validates stored counts, and promotes the snapshot atomically.
The lifecycle is `Pending -> Writing -> Validated -> Promoted -> Archived`. Failed or interrupted
staging snapshots are rolled back, and the previously promoted snapshot remains visible.

Only one reservation per company may synchronize at a time. Cancellation is propagated through
extraction and checked between repository phases. Reservations are released in a `finally` block.

## Repository model

SQLite stores snapshot metadata, Voucher headers, ledger entries, inventory entries, and
allocations with company and snapshot scope. Foreign keys protect child rows. Reads select only
promoted data, and repository transactions prevent partial batch or promotion commits.

The storage service runs SQLite integrity checks for health reporting and closes the database
connection during orderly shutdown and failed initialization.

## Read-only API

- `GET /api/v1/vouchers`
- `GET /api/v1/vouchers/:id`
- `GET /api/v1/vouchers/search`
- `GET /api/v1/vouchers/snapshots`
- `GET /api/v1/vouchers/snapshots/:snapshotId`

Requests require company scope and validate dates, identifiers, pagination, and sort fields.
Responses use schema version `1.0.0`, stable DTOs, and deterministic ordering. The API never
accesses SQLite directly and exposes no mutation endpoint.

## Health and readiness

`GET /health` confirms that the process, repository service, and SQLite database are locally
available. `GET /ready` additionally confirms that Voucher synchronization and application
services are composed. Neither endpoint contacts Tally.

API responses include an `x-correlation-id`. Completed requests, validation failures, storage
startup, and application startup are emitted as structured events without logging Voucher data.

## Operational limitations

The connector remains read-only toward Tally. Health does not imply that Tally is currently
reachable; connection diagnostics remain separate. Authentication, authorization, packaging,
deployment automation, UI, and scheduling changes are outside this subsystem.
