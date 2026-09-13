# Venture Connector 0.4.0 Release Notes

Connector 0.4.0 introduces the production, read-only Voucher subsystem. It adds approved Tally
extraction, atomic SQLite snapshots, controlled synchronization, stable Voucher HTTP contracts,
local health/readiness checks, and production startup validation.

## Compatibility

- Requires Node.js 20 or newer.
- API schema version remains `1.0.0`.
- Existing SQLite installations are upgraded through registered forward migrations.
- The default API bind remains `127.0.0.1`; production LAN exposure requires explicit operator
  acknowledgement.
- Tally access remains export-only. No Tally mutation operation is introduced.

## Upgrade and rollback

Back up the configured data directory before upgrade and verify `GET /health` and `GET /ready`
after restart. To roll back the application, stop the connector, restore the application version
and the pre-upgrade database backup together, then restart on loopback and re-run health checks.
Do not attempt to downgrade a migrated database in place.

This file prepares release metadata only. No package, publication, tag, or deployment is created.
