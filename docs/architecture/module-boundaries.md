# Module Boundaries

Feature modules must depend on stable interfaces. Tally-specific formats stay inside the connector adapter.

## Mobile feature modules

| Module | Responsibility | Milestone |
|--------|----------------|-----------|
| `connection` | Pairing, host/port config, connectivity tests | M1 |
| `companies` | Company list and selection | M1 |
| `ledgers` | Ledger list, detail, transactions | M2 |
| `vouchers` | Voucher list and detail | M3 |
| `search` | Universal ledger/voucher search | M3 |
| `pdf` | Statement/voucher PDF and share sheet | M4 |
| `diagnostics` | Health, errors, support export | M4 |
| `dashboard` | Top-5 intelligence (feature-flagged off) | Post-MVP |

## Shared packages

### `venture_core`

Domain entities, value objects (`Money`, `DebitCredit`), capabilities, feature flags, domain events.

**Must not depend on:** Flutter, HTTP clients, SQL, Tally formats.

### `venture_contracts`

Wire-format DTOs matching `docs/openapi/connector-v1.yaml`.

**May depend on:** `venture_core` for mapping to domain types.

## Connector modules (Milestone 1+)

| Module | Responsibility |
|--------|----------------|
| `tally-adapter` | Version-specific Tally communication |
| `normalization` | Tally XML → Venture entities |
| `api` | Express routes, auth, read-only enforcement |
| `diagnostics` | Health, version reporting |

## Dependency rules

```
✅ presentation → application → domain ← data
✅ data → contracts → core
✅ connector/api → normalization → tally-adapter
❌ presentation → HTTP/SQL
❌ domain → Flutter
❌ venture_core → connector
❌ UI → Tally XML
```

## Domain events (reserved)

| Event | When emitted |
|-------|--------------|
| `LedgerViewed` | Ledger detail opened |
| `VoucherViewed` | Voucher detail opened |
| `DocumentGenerated` | PDF created |
| `DocumentShared` | Share sheet completed |
| `SyncCompleted` | Sync finished |
| `ConnectionFailed` | Unrecoverable connection error |

Subscribers will be added in later versions without rewriting existing flows.
