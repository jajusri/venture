# Budcom Tally Connector

Local read-only service that runs on or near the Tally computer. **Milestone 2** adds the production-grade Tally communication layer — HTTP/XML transport, connection management, company discovery, and diagnostics. Sync, licensing, and database logic remain out of scope.

## Architecture

```
src/
├── main.ts                      # Process entry point
├── bootstrap/                   # Startup, shutdown, DI registration
├── config/                      # Configuration management
├── core/                        # DI container, tokens, shared types
├── infrastructure/
│   ├── logging/                 # Structured JSON logging
│   └── errors/                  # AppError, centralized handler
├── tally/                       # ERP communication layer (M2)
│   ├── core/                    # Transport types, ErpTransport interface
│   ├── transport/               # HTTP transport, connection pool
│   ├── connection/              # Connection manager, retry, reconnect
│   ├── xml/                     # Request builder, response parser framework
│   ├── discovery/               # Company discovery parser
│   └── tally-module.ts          # Module factory for DI
├── services/
│   ├── interfaces/              # Service contracts
│   ├── tally/                   # Tally service implementations (M2)
│   ├── placeholders/            # Stubs for future milestones
│   └── health/                  # Health aggregation
└── api/
    ├── middleware/              # Read-only enforcement
    └── routes/                  # HTTP routes
```

## Scripts

```bash
npm ci
npm run build
npm test
npm run lint
npm run dev
```

## Configuration

| Variable                      | Default                      | Description                              |
| ----------------------------- | ---------------------------- | ---------------------------------------- |
| `BUDCOM_CONNECTOR_HOST`       | `0.0.0.0`                    | HTTP bind address                        |
| `BUDCOM_CONNECTOR_PORT`       | `8080`                       | HTTP port                                |
| `BUDCOM_LOG_LEVEL`            | `info`                       | `debug`, `info`, `warn`, `error`         |
| `BUDCOM_TALLY_HOST`           | `localhost`                  | Tally HTTP server host                   |
| `BUDCOM_TALLY_PORT`           | `9000`                       | Tally HTTP server port                   |
| `BUDCOM_TALLY_TIMEOUT_MS`     | `30000`                      | Per-request timeout (ms)                 |
| `BUDCOM_TALLY_POOL_MAX`       | `4`                          | Max concurrent in-flight Tally requests  |
| `BUDCOM_TALLY_RETRY_MAX`      | `3`                          | Max retry attempts per exchange          |
| `BUDCOM_TALLY_RETRY_BASE_MS`  | `500`                        | Retry base delay (ms)                    |
| `BUDCOM_TALLY_RETRY_MAX_MS`   | `8000`                       | Retry max delay cap (ms)                 |
| `BUDCOM_TALLY_RETRY_JITTER`   | `0.2`                        | Retry jitter ratio (0–1)                 |
| `BUDCOM_TALLY_AUTO_RECONNECT` | `true`                       | Enable automatic reconnect backoff       |
| `BUDCOM_TALLY_RECONNECT_MS`   | `2000`                       | Reconnect delay (ms)                     |
| `BUDCOM_DATABASE_PATH`        | `./data/budcom-connector.db` | Local DB path (reserved)                 |
| `BUDCOM_SHUTDOWN_MS`          | `10000`                      | Graceful shutdown timeout                |

## API endpoints (M2)

| Method | Path                      | Description                          |
| ------ | ------------------------- | ------------------------------------ |
| GET    | `/health`                 | Connector + Tally reachability       |
| GET    | `/diagnostics/connection` | Tally connection diagnostics         |
| GET    | `/companies`              | Discover companies from Tally        |
| GET    | `/companies/:id/ledgers`  | 501 — not implemented                |
| POST   | `/device/pair`            | 501 — not implemented                |

All routes are read-only except explicitly allowed POST endpoints.

## Tally communication layer

The `tally/` module is designed for reuse across future ERP connectors:

- **`ErpTransport`** — abstract transport interface (Tally HTTP today)
- **`TallyHttpTransport`** — POSTs XML to Tally with timeout via `AbortController`
- **`ConnectionPool`** — semaphore limiting concurrent requests
- **`RetryPolicy`** — exponential backoff with jitter for transient failures
- **`ReconnectManager`** — automatic reconnect with configurable delay
- **`TallyConnectionManager`** — orchestrates transport, retry, reconnect, and state
- **`TallyXmlRequestBuilder`** — builds Tally XML envelopes (connectivity, company list)
- **`TallyXmlResponseParser`** — structural XML parser with pluggable node handlers
- **`CompanyDiscoveryParser`** — extracts company metadata from discovery responses

## Milestone 2 scope

**Implemented:** Tally HTTP/XML transport, XML request builder, XML response parser framework, connection manager, company discovery, connection diagnostics, retry policy, timeout handling, automatic reconnect, connection pooling, config enhancements, structured logging, error handling, health/diagnostics endpoints, unit + integration tests (mocked Tally only).

**Not implemented:** Voucher/ledger/inventory sync, scheduler, licensing logic, database persistence, Flutter/mobile UI, business rules.

## Testing

Tests use mocked `fetch` — no live Tally instance required:

```bash
npm run test:unit
npm run test:integration
```

Mock helpers live in `test/helpers/mock-fetch.ts`.
