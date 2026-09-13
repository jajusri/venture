# Stale release artifact — do not distribute

The Desktop 0.4.3 controlled-pilot artifacts in this directory are retained
for historical evidence only. They must not be reused, installed as the current
release candidate, or redistributed.

## Historical artifact identity

- Installer: `artifacts/VentureDesktop-0.4.3-x64-setup.exe`
- Installer SHA-256:
  `7B84106F9A49BF8E3F8762BADA8BF23EEC4B6333F95E4BDC89A2DFC9E2116F42`
- Desktop version: `0.4.3`
- Embedded Connector version: `0.3.1`
- Embedded Git commit: `10a3e280439d27632d529eea6f3f0d8cf0d1ba60`
- Historical build dirty-tree state: `false`

## Reason for quarantine

The embedded Connector predates the validated multi-phase Voucher extraction
architecture. Package inspection confirmed that it does not contain:

- `voucher-ledger-parser`
- `voucher-ledger-reconciler`
- `voucher-inventory-parser`
- `voucher-inventory-joiner`
- the dedicated safe ledger extraction phase
- the dedicated safe inventory extraction phase

The historical request artifact still contains the legacy compound Voucher
extraction shape, including root Voucher Amount and compound ledger/inventory
methods. A new release must be produced from a clean source commit and must pass
the hardened Connector artifact-manifest and packaging-freshness gates.
