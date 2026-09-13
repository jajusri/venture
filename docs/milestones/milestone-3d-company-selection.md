# Milestone 3D — Company Selection & Session Management

**Status:** Implemented  
**Date:** 2026-07-22  
**Depends on:** Milestone 3C (Groups live validation)

## Objective

Production-ready company selection and session management. The connector requires one active company to be selected before ERP master-data operations proceed.

## Architecture

```
POST /session/company
  → ConnectorSessionServiceImpl.selectCompany()
  → CompanyDiscoveryService.discoverCompanies()   [verify company exists]
  → session-validator.selectCompany()             [pure domain rules]
  → immutable ConnectorSession snapshot

GET /companies/:companyId/ledger-groups
  → MasterDataServiceImpl.getLedgerGroups()
  → ConnectorSessionServiceImpl.validateForOperation(companyId)
  → session-validator.validateSession()
  → CompanyResolver.resolveName()                 [existing path]
  → ErpReadPort.getGroups()                       [unchanged adapter path]
```

Session logic lives in the **business/service layer** (`services/session/`). ERP-neutral types live in `erp/session/`. Tally adapters are unchanged.

### Layer ownership

| Layer | Responsibility |
|-------|----------------|
| `erp/session/` | ERP-neutral session contracts, statuses, immutable session model |
| `services/session/session-validator.ts` | Pure selection and validation rules |
| `services/session/connector-session.service.ts` | Session lifecycle, discovery coordination |
| `services/session/session-error-mapper.ts` | Maps validation results to HTTP errors for ERP routes |
| `api/routes/session.ts` | Session HTTP surface (structured results, no throws for expected failures) |

## ConnectorSession

Immutable snapshot stored in connector state:

| Field | Description |
|-------|-------------|
| `sessionId` | Stable session identifier (UUID) |
| `selectedCompany` | `{ id, name }` or `null` |
| `connectionStatus` | `connected` / `disconnected` / `degraded` |
| `connectorVersion` | From connector config |
| `erpType` | `tally` (extensible for future ERPs) |
| `selectedAt` | ISO timestamp of last selection |
| `lastValidatedAt` | ISO timestamp of last successful validation |
| `createdAt` | ISO timestamp when session was created |

Session objects are replaced atomically on selection or successful validation — never mutated in place.

## Session lifecycle

1. **Start** — empty session created with connector metadata and connection status
2. **Discover** — client calls `GET /companies`
3. **Select** — client calls `POST /session/company` with `{ companyId }`
4. **Validate** — every ERP request re-validates session before adapter calls
5. **Refresh** — `POST /session/validate` explicitly re-checks company existence and reachability
6. **Clear** — `DELETE /session/company` removes selection
7. **Stop** — durable selection is preserved; only connection state becomes disconnected

Session TTL defaults to 8 hours (`VENTURE_SESSION_TTL_MS`). Expired sessions return
`SESSION_EXPIRED`. Repeating `POST /session/company` for the already-selected, still-discoverable
company is an authenticated idempotent renewal: it refreshes and persists `selectedAt`, returns
HTTP 200 with `DUPLICATE_SELECTION`, and does not alter pairing, identity, or trust state.

## Validation flow

Before every master-data ERP request, `MasterDataServiceImpl` calls:

```typescript
const validation = await connectorSession.validateForOperation(companyId);
assertSessionValidation(validation);
```

Validation checks:

1. Connector session service running
2. Connector connected (`ErpReadPort.isReady()`)
3. Session not expired
4. Company selected
5. Requested `companyId` matches selected company (when provided)
6. Tally reachable (via discovery)
7. Selected company still exists in discovery results
8. Selected company metadata still matches discovery

On success, `lastValidatedAt` is updated.

## HTTP API

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/session` | Current session snapshot |
| `POST` | `/session/company` | Select company (`{ companyId }`) |
| `DELETE` | `/session/company` | Clear selection |
| `POST` | `/session/validate` | Explicit session validation |

Selection and validation endpoints return **structured status bodies** (not exceptions) for expected failures.

## Error handling

### Selection statuses

| Status | HTTP | Meaning |
|--------|------|---------|
| `SUCCESS` | 200 | Company selected |
| `EMPTY_SELECTION` | 400 | Empty or whitespace company id |
| `INVALID_COMPANY` | 400 | Company not accessible (connector/Tally state) |
| `COMPANY_NOT_FOUND` | 404 | Company not in discovery results |
| `DUPLICATE_SELECTION` | 200 | Same company already selected; session lease renewed |

### Validation statuses

| Status | HTTP (session routes) | HTTP (ERP routes via AppError) |
|--------|----------------------|--------------------------------|
| `SUCCESS` | 200 | Proceed |
| `NO_COMPANY_SELECTED` | 400 | 400 |
| `COMPANY_NOT_FOUND` | 404 | 404 |
| `COMPANY_NOT_ACCESSIBLE` | 403 | 403 |
| `SESSION_INVALID` | 400 | 400 |
| `SESSION_EXPIRED` | 410 | 410 |

ERP routes include `details.sessionStatus` for structured client handling.

## Testing strategy

| Layer | Tests |
|-------|-------|
| `session-validator.test.ts` | Pure rules: valid/invalid/empty/duplicate/stale/missing company |
| `connector-session.test.ts` | HTTP integration: selection, validation, idempotent renewal, unavailable, stale session |
| `connector-session-restart.test.ts` | Durable restart continuity, expired persisted lease renewal, and renewed timestamp persistence |
| Master data / groups integration | Updated to select company before ERP calls; mismatch and no-selection cases |

Test helpers:

- `startTestServices(context, { selectCompanyId })` — boots services and selects company
- `createPermissiveSessionMock()` — unit-test bypass for master-data boundary tests

## Backward compatibility

- Existing `/companies/:companyId/...` routes preserved
- Company discovery (`GET /companies`) unchanged
- ERP adapter path unchanged
- **New requirement:** master-data routes require company selection first
- Clients must call `POST /session/company` before ERP extraction

## Configuration

| Setting | Env var | Default |
|---------|---------|---------|
| Session TTL | `VENTURE_SESSION_TTL_MS` | 28800000 (8 hours) |

## Remaining observations

- Session state is in-memory only; persistence deferred to LocalDatabase milestone
- Single-connector instance assumed; multi-device session binding deferred to device pairing
- ERP type is currently `tally` only; constant prepared for future adapters
- Health report includes `ConnectorSession` service status
