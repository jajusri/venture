# Cross-cutting tests

Contract and architecture validation that spans multiple packages.

## Contract tests

```bash
cd tests/contract
npm ci
npm test
```

Validates `docs/openapi/connector-v1.yaml` structure and read-only invariants.

## Package tests

Dart package and connector tests run via Melos and CI. See root [README.md](../../README.md).
