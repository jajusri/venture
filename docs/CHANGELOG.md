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
- Stock Item Browser against Connector `GET /stock-items`
- Confirmed Voucher Connector contract notes (`docs/contracts/android-voucher-api.md`)
- Voucher foundation and Voucher Browser against `GET /api/v1/vouchers`
- Voucher Details against `GET /api/v1/vouchers/:id` (header, metadata, narration, ledger/inventory lines)
- Universal Search Foundation across Ledgers, Stock Items, and Vouchers (Android-side orchestration; contract notes in `docs/contracts/android-universal-search.md`)
- Confirmed Sync Connector contract notes (`docs/contracts/android-sync-api.md`)
- Sync Foundation: manual Ledger/Stock Item sync, cancel, status observation, Dashboard summary port
- Confirmed Diagnostics Connector contract notes (`docs/contracts/android-diagnostics.md`)
- Diagnostics Foundation: operational view aggregating health, readiness, connection diagnostics, company/session, sync summary, and honest capability notes
- Settings Foundation contract notes (`docs/contracts/android-settings.md`)
- Settings Foundation: theme preference (System/Light/Dark) via DataStore, About metadata, and navigation into Server Configuration / Company / Sync / Diagnostics

### Changed

- Dashboard architectural boundaries (stable ports, authoritative operational mode)
- Startup Connector URL hydration via core port
- Android module documentation accuracy
- Roadmap: Settings Foundation complete; current = Production Validation
