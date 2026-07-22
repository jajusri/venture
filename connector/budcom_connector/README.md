# Budcom Tally Connector

Local read-only service that runs on or near the Tally computer. Milestone 1 provides the **core project foundation** — no Tally communication, sync, XML parsing, licensing, or database logic yet.

## Architecture

```
src/
├── main.ts                 # Process entry point
├── bootstrap/              # Startup, shutdown, DI registration
├── config/                 # Configuration management
├── core/                   # DI container, tokens, shared types
├── infrastructure/
│   ├── logging/            # Structured JSON logging
│   └── errors/             # AppError, centralized handler
├── services/
│   ├── interfaces/         # Service contracts
│   ├── placeholders/       # Stub implementations (M1)
│   └── health/             # Health aggregation
└── api/
    ├── middleware/         # Read-only enforcement
    └── routes/             # HTTP routes
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

| Variable                | Default                      | Description                      |
| ----------------------- | ---------------------------- | -------------------------------- |
| `BUDCOM_CONNECTOR_HOST` | `0.0.0.0`                    | HTTP bind address                |
| `BUDCOM_CONNECTOR_PORT` | `8080`                       | HTTP port                        |
| `BUDCOM_LOG_LEVEL`      | `info`                       | `debug`, `info`, `warn`, `error` |
| `BUDCOM_TALLY_HOST`     | `localhost`                  | Tally host (reserved)            |
| `BUDCOM_TALLY_PORT`     | `9000`                       | Tally port (reserved)            |
| `BUDCOM_DATABASE_PATH`  | `./data/budcom-connector.db` | Local DB path (reserved)         |
| `BUDCOM_SHUTDOWN_MS`    | `10000`                      | Graceful shutdown timeout        |

## Milestone 1 scope

Implemented: folder structure, TypeScript/ESLint/Prettier, config, logging, error handling, bootstrap, DI, health endpoint, graceful shutdown, placeholder services, unit/integration test scaffolding.

Not implemented: Tally communication, sync engine logic, XML parsing, licensing logic, database logic.
