# Changelog

## Unreleased

### Added

- BUDCO product governance documents (`PRODUCT_SOUL`, `PROJECT_CONSTITUTION`, `ROADMAP`, `DECISIONS`)
- Android architecture ADRs and development workflow
- Architecture review and repository evolution guidance
- Stabilized operational Dashboard (home destination)
- Confirmed Master Data / Ledger / Stock Item Connector contract notes (`docs/contracts/android-master-data-ledger.md`)
- Master Data foundation (hub navigation, shared list UI conventions, pagination metadata, shared UI errors)
- Ledger Browser against Connector `GET /ledgers`
- Stock Item Browser against Connector `GET /stock-items` (list, search, pagination, refresh, offline/error states)

### Changed

- Dashboard architectural boundaries (stable ports, authoritative operational mode)
- Startup Connector URL hydration via core port
- Android module documentation accuracy
- Roadmap: Master Data complete (Dashboard, Ledger Browser, Stock Item Browser); next = Voucher Browser
