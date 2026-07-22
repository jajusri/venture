# Milestone 3C — Production Tally Groups

**Status:** Implemented and live-validated (2026-07-22)  
**Date:** 2026-07-22  
**Depends on:** Milestone 3B (`v0.3.1-company-discovery`)

## Scope

Production-ready Tally **accounting group** extraction through the existing secure ERP architecture. Covers Groups only — not ledgers, stock groups, vouchers, sync, persistence, or UI.

## Architecture path

```
GET /companies/:companyId/ledger-groups
  → MasterDataServiceImpl.getLedgerGroups()
  → ErpReadPort.getGroups(companyName)
  → TallyReadAdapter.getGroups()
  → TallyReadGateway.executeApprovedRead({ operationId: LEDGER_GROUPS })
  → TallyConnectionManager.exchange()          [adapter-internal]
  → TallyRequestGuard.prepare()                [policy + registry + audit]
  → TallyHttpTransport.send()
  → GroupsParser + hierarchy validator + groups contract
  → ErpGroupsResult (domain models + status + hierarchy issues)
```

No alternate communication path. Business code never sees XML.

## Approved operation

| Field | Value |
|-------|-------|
| Operation ID | `LEDGER_GROUPS` |
| Registry entry | `ApprovedOperationId.LedgerGroups` |
| Tally collection | `List of Groups` |
| Capability | `TALLY_MASTER_READ` |
| Classification | `VERIFIED_SAFE` |
| Request kind | `Export` / `Collection` |
| Requires company | `true` |

## Domain contract

**Port contract:** `ErpGroupsResult`, `ErpGroupSummary`, `HierarchyIssue` (`src/erp/ports/groups.ts`)

**HTTP contract:** paginated envelope via `GET /companies/:companyId/ledger-groups` with explicit `status`, `contractVersion`, `hierarchyIssues`

Contract version: `1`

| Status | Meaning |
|--------|---------|
| `SUCCESS` | One or more trusted groups returned |
| `EMPTY` | Valid response; no groups available |
| `INCOMPLETE` | Groups or hierarchy cannot be fully trusted |
| `MALFORMED` | Envelope or XML cannot be parsed safely |
| `UNAVAILABLE` | Tally unreachable or connection not ready |
| `DENIED` | Policy or registry blocked the request |
| `TIMEOUT` | Transport timeout |
| `COMPANY_UNAVAILABLE` | Company name missing or invalid for scoped read |

## Hierarchy rules

- Parent references resolved by normalized name index (Tally provides parent names, not GUIDs)
- `Primary` treated as Tally virtual root — missing `Primary` node in export is not flagged as orphaned
- Validated issues: `MISSING_PARENT`, `SELF_PARENT`, `CYCLE`, `DUPLICATE_ID`, `DUPLICATE_NAME`, `EMPTY_NAME`, `AMBIGUOUS_PARENT`
- Questionable hierarchy never silently repaired — returns `INCOMPLETE` with `hierarchyIssues`
- Cycle detection uses bounded parent-chain walks (no unbounded recursion)

## Normalization rules

- Whitespace trimmed via `normalizeText` (Unicode NFC)
- Stable IDs via `slugify(name)`
- CMPINFO count metadata nodes skipped
- Duplicate IDs removed (first occurrence wins); conflicts counted
- Optional fields (`isRevenue`, `isDebit`, `reservedName`) omitted when absent — never invented
- `reservedName` set only when `ISBUILTIN` is true or name is `Primary`

## Identity and duplicate handling

- ID derived from normalized name (slug)
- Duplicate identical records deduplicated by ID
- Same normalized name with different IDs flagged as `DUPLICATE_NAME`
- Name collisions block trusted hierarchy (`hasBlockingIssues`)

## Outcome behaviour

| Adapter outcome | HTTP (via service) | Body |
|-----------------| -------------------|------|
| `SUCCESS`, `EMPTY`, `INCOMPLETE` | 200 | Full result with explicit `status` |
| `DENIED` | 403 | Error envelope |
| `UNAVAILABLE`, `TIMEOUT`, `MALFORMED`, `COMPANY_UNAVAILABLE` | 503 | Error envelope |

## Tests added

| File | Coverage |
|------|----------|
| `test/helpers/groups-fixtures.ts` | Realistic XML fixtures + large hierarchy builders |
| `test/unit/tally/groups-parser.test.ts` | Parser + contract assessment |
| `test/unit/tally/groups-hierarchy.test.ts` | Hierarchy validation edge cases |
| `test/unit/tally/groups-adapter.test.ts` | Adapter outcomes, service boundary |
| `test/integration/groups.test.ts` | HTTP integration + 3B regression |

**38 new tests** (222 total). Milestone 3B company discovery tests remain green.

## Performance considerations

- Name index built once per parse (`Map` lookup)
- Hierarchy validation uses bounded walks (`groups.length + 1` max steps)
- Large hierarchy test: 500-node chain parses in under 2s
- Live first-call extraction ~4.3s; cached repeat ~28ms

## Live validation outcome

**Result:** MILESTONE 3C VALIDATED (2026-07-22)

| Field | Value |
|-------|-------|
| Company | ESTIMATION (`estimation`) |
| Tally version/build | Not captured |
| Total groups observed | 28 |
| Domain status (normal extraction) | `INCOMPLETE` (15 hierarchy issues) |
| Scenarios passed | 7 / 7 |
| Slug collisions | 0 |
| Duplicate stable IDs | 0 |

### Representative hierarchy verification

- Built-in root groups (Capital Account, Current Assets, etc.) returned with correct names
- Child groups (Bank Accounts, Sundry Debtors, etc.) correctly linked to parents with `parentStableId`
- Primary group not in export; parent references use `"&#4; Primary"` entity encoding from Tally
- Punctuation and special-character group names preserved (`Branch / Divisions`, `Duties & Taxes`)

### Stable-ID repeatability

Two consecutive extractions returned identical 28-group sets with matching stable IDs, names, parent relationships, and hierarchy issue counts. No slug-derived ID collisions.

### Recovery behaviour

After Tally unavailable (Scenario 6), recovery succeeded without connector restart once Tally was restarted and company reopened. Circuit breaker opened on failure and closed before recovery. Post-recovery result matched pre-failure baseline exactly.

### Known response quirks (live)

1. **`&#4; Primary` parent encoding** — Tally returns parent as XML character entity `"&#4; Primary"` rather than `"Primary"`. Virtual-root recognition fails for 15 built-in root groups → `MISSING_PARENT` → status `INCOMPLETE`. Not silently repaired.
2. **Primary not exported** — virtual root absent from collection; `reservedName` not set for tested built-in groups.
3. **Company resolution prerequisite** — no-company and unavailable scenarios fail at `COMPANY_LIST` during company ID resolution (HTTP 404 or 503) before `LEDGER_GROUPS` is sent.

Full evidence: `docs/testing/milestone-3c-groups-manual-validation.md`

## Limitations

- Parent identity is name-based only (Tally export limitation)
- Stable IDs are slug-derived, not Tally internal GUIDs
- Visually similar or normalization-equivalent names may collide (none observed in live test)
- Tally version/build-specific response differences not yet proven across versions
- `&#4; Primary` entity encoding not yet normalized to virtual root (post-validation improvement candidate)
- `readLedgerGroups()` retained for backward compatibility; delegates to `getGroups()`

## Remaining before commit

Live validation complete. Implementation is ready for human review and commit when authorized. No commit, tag, or push performed during validation.
