# Budcom Tally Connector

Local read-only service that runs on or near the Tally computer.

**Milestone 3** adds a production-grade read-only master data extraction layer — company info, ledger groups, ledgers, inventory masters, units, godowns, cost centres, voucher types, and GST registrations.

## Architecture

```
src/
├── extraction/                  # M3 read-only data extraction layer
│   ├── core/                    # Types, pagination
│   ├── normalization/           # Dates, amounts, numbers, strings
│   ├── templates/               # Tally XML request templates (all entities)
│   ├── parsers/                 # Collection parsers + entity mappers
│   └── extractors/              # Per-entity extractors + diagnostics
├── tally/                       # M2 transport, connection, XML framework
├── services/
│   ├── extraction/              # MasterDataService, CompanyResolver
│   └── tally/                   # Connection, discovery, diagnostics
└── api/routes/                  # HTTP endpoints
```

## Master data endpoints (M3)

All routes are scoped under `/companies/:companyId` (company ID = slugified Tally company name).

| Method | Path | Entity |
|--------|------|--------|
| GET | `/companies` | Company list (discovery) |
| GET | `/companies/:companyId` | Company information |
| GET | `/companies/:companyId/ledger-groups` | Ledger groups |
| GET | `/companies/:companyId/ledgers` | Ledgers |
| GET | `/companies/:companyId/stock-groups` | Stock groups |
| GET | `/companies/:companyId/stock-categories` | Stock categories |
| GET | `/companies/:companyId/stock-items` | Stock items |
| GET | `/companies/:companyId/units` | Units of measure |
| GET | `/companies/:companyId/godowns` | Godowns |
| GET | `/companies/:companyId/cost-categories` | Cost categories |
| GET | `/companies/:companyId/cost-centres` | Cost centres |
| GET | `/companies/:companyId/voucher-types` | Voucher types |
| GET | `/companies/:companyId/gst-registrations` | GST registrations |
| GET | `/diagnostics/extraction` | Per-extractor diagnostics |

Paginated endpoints accept `?page=1&pageSize=50` (max 500). Extraction is in-memory paginated after full Tally collection fetch.

## System endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Connector + Tally status |
| GET | `/diagnostics/connection` | Connection diagnostics |

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `BUDCOM_CONNECTOR_HOST` | `127.0.0.1` | Connector API bind host (loopback-only default; not Tally host) |
| `BUDCOM_CONNECTOR_PORT` | `8080` | Connector API port |
| `BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED` | `false` | Required `true` for non-loopback bind in production (no auth yet) |
| `BUDCOM_TALLY_HOST` | `localhost` | Tally HTTP host |
| `BUDCOM_TALLY_PORT` | `9000` | Tally HTTP port |
| `BUDCOM_TALLY_TIMEOUT_MS` | `120000` | Request timeout (large collections) |
| `BUDCOM_TALLY_POOL_MAX` | `4` | Max concurrent Tally requests |
| `BUDCOM_TALLY_RETRY_MAX` | `3` | Retry attempts |
| `BUDCOM_TALLY_AUTO_RECONNECT` | `true` | Auto-reconnect on failure |

See [README section from M2] for full config list.

## Scripts

```bash
npm ci
npm run build
npm test
npm run lint
npm run dev
```

## Milestone 3 scope

**Implemented:** Read-only extraction of 12 master data entity types, XML templates, response parsing, normalization layer, repository/service layer, in-memory pagination, per-extractor diagnostics, unit + integration tests.

**Not implemented:** Voucher/ledger transaction sync, database persistence, offline cache, scheduler, licensing, Flutter UI, write operations.

## Testing

```bash
npm run test:unit
npm run test:integration
```

Mock fixtures in `test/helpers/master-data-fixtures.ts`. Live Tally optional for manual smoke tests.
