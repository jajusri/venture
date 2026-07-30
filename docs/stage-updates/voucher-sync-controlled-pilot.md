# Voucher Synchronization — Controlled-Pilot Stage Update

**Updated:** 2026-07-31
**Classification:** Controlled-pilot ready; unrestricted production is not approved

## Implementation status

End-to-end voucher synchronization is implemented across the Connector and Android app.
The Connector exposes a controlled `POST /sync/vouchers` route that requires a valid,
selected company session. It invokes the existing snapshot synchronization service,
validates extracted records, stages them in the local SQLite repository, and promotes
only a validated snapshot. Android exposes both an individual **Vouchers → Sync now**
action and includes vouchers in the sequential combined synchronization action.

Voucher list and detail screens consume the promoted read-only snapshot through the
existing public voucher read API.

## Safety boundary

- Tally access remains read-only.
- The route performs local extraction, validation, snapshot persistence, and promotion.
- No Tally `IMPORT`, `EXECUTE`, voucher modification, or other write operation was added.
- Existing request middleware and Tally read-only protections remain active.
- The route requires the selected company to pass session validation before extraction.
- No customer data, local database, synchronized voucher payload, log, secret, or
  machine-specific network address is part of this change.

## Automated validation

- Connector ESLint and TypeScript checks: passed.
- Focused Connector voucher API tests: passed, including the public synchronization route.
- Android debug build: passed.
- Android unit tests: 171 passed.
- Generated desktop packaging completed with zero production dependency vulnerabilities.

## Physical-device validation

The controlled-pilot desktop build and Android debug build were installed and exercised
with a physical Android device over direct Wi-Fi, without ADB reverse.

The live synchronization result was:

- 20 candidate vouchers
- 20 vouchers extracted and persisted
- 0 rejected vouchers
- 0 validation issues
- promoted snapshot returned successfully through the voucher read API

Observed voucher types included:

- Sales
- Purchase
- Payment
- Receipt
- Contra
- Credit Note

## Artifact evidence

Artifacts are generated outputs and are not committed.

- Desktop installer SHA-256:
  `9CBC6D36135E8B6CB29C7474E140DD3995935F48965041958304253C24EFAB3D`
- Android APK SHA-256:
  `58113A8FC85A5DC53A03FB4279C71D45997E9963D6DFA72D6FA17FC39C4F43E8`

## Known limitations

- Synchronization is currently a blocking HTTP operation rather than a background job.
- The default synchronization period is the most recent 30 calendar days.
- Public voucher cancel, live-status, statistics, and run-history endpoints are not yet
  implemented; the public surface currently supports starting a sync and reading its
  completed snapshot.
- Combined synchronization is sequential and non-atomic across Ledgers, Stock items,
  and Vouchers.
- LAN operation requires explicit desktop acknowledgement and trusted-network controls.
- The installer is unsigned and uses the default Electron icon.

These limitations keep the feature in controlled-pilot classification. Broader production
release requires authenticated LAN access, background synchronization controls, expanded
failure/recovery coverage, signing, and release-governance approval.
