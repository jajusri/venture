# Budcom Connector

Local read-only service that runs on or near the Tally computer. It adapts Tally-specific formats and exposes the normalized Budcom connector API to the mobile app.

## Milestone 0 scope

- Express server skeleton
- `GET /health` implemented
- Read-only middleware (rejects mutating routes except pairing)
- Stub routes returning `501 Not Implemented` for all data endpoints
- Automated tests for health and read-only enforcement

## Run locally

```bash
npm ci
npm test
npm run dev
```

## Environment

| Variable | Default | Description |
|----------|---------|-------------|
| `BUDCOM_CONNECTOR_PORT` | `8080` | HTTP listen port |
| `BUDCOM_CONNECTOR_HOST` | `0.0.0.0` | Bind address |

## Contract

See [docs/openapi/connector-v1.yaml](../../docs/openapi/connector-v1.yaml).
