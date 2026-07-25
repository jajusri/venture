# Unified Inbound XML Envelope Boundary

**Status:** Implemented (RC#4 gate — reliability order item 6)  
**Contract version:** `1`  
**Scope:** Connector offline/import workflows only; live Tally read path unchanged.

## Purpose

All approved inbound XML sources must pass through one hardened, deterministic validation boundary before domain parsing or persistence.

Live Tally HTTP responses continue through the existing gateway + operation-specific parser limits. They are **not** routed through this envelope service.

## Production callers and authorization

| Caller | Method | Authorization |
|--------|--------|---------------|
| `OfflineXmlIngestionService.ingestString` | `acceptBuffer` | Trusted internal service code only |
| `OfflineXmlIngestionService.ingestFile` | `acceptFile` | Trusted internal service code only; **no OS file picker exists yet** |
| Unit/integration tests | both | Test-only |
| Connector HTTP API | — | **No XML upload routes** |
| Desktop IPC | — | **Not wired** |
| Watched-folder product | `acceptFile` with `approvedRoot` | Framework only; path containment enforced when used |

`trusted_internal_file` must not be described as user-selected until a desktop path capability or OS picker exists.

## File stability guarantees

| Source type | Guarantee |
|-------------|-----------|
| `trusted_internal_file` | Single read into immutable buffer + post-read metadata verification. Assumes caller already selected a stable file. Does **not** prove producer completion. |
| `watched_folder_file` | Bounded stability window: default 500 ms unchanged size/mtime, 100 ms polling, 5 s max wait, then reject. Production watched-folder requires this stronger protocol. |
| `inline_buffer` | Not filesystem-backed |

Source files are never modified or deleted.

## Duplicate reservation (SQLite v6–v8)

Dual partial unique indexes (migration 8; no COALESCE sentinel):

- `idx_xml_import_attempts_reservation_no_company` on `(resource_kind, content_fingerprint)` WHERE `company_id IS NULL`
- `idx_xml_import_attempts_reservation_scoped` on `(company_id, resource_kind, content_fingerprint)` WHERE `company_id IS NOT NULL`

Both filter `reservation_status IN ('active','completed')`. NULL and non-NULL company scopes cannot collide.

`persistence_status='completed'` means import-attempt history committed only — **not** domain ledger/stock upsert.
Reservation lifecycle (`reservation_status`):

| Status | Meaning |
|--------|---------|
| `active` | Reservation held; validation in progress |
| `completed` | Successful unique import-attempt history; duplicate-protected (not domain upsert) |
| `released` | Validation failed; retry allowed |
| `abandoned` | Active reservation recovered after process restart |

Flow:

```
BEGIN IMMEDIATE → INSERT active reservation
  → on UNIQUE violation: classify duplicate | in_progress
  → on validation success: UPDATE completed
  → on validation failure after acquire: UPDATE released
  → on startup: recover active → abandoned
```

Concurrent imports: exactly one `active`/`completed` lock per `(company_id, resource_kind, fingerprint)`.

## Import history schema summary

Table `xml_import_attempts` (migration 6):

- PK: `import_attempt_id TEXT`
- Scope: `company_id`, `resource_kind`, `content_fingerprint`
- Lifecycle: `reservation_status`, `validation_status`, `persistence_status`, `duplicate_status`
- Privacy-safe metadata: `source_type`, basename `source_identifier`, `byte_size`, `error_code`, versions, timestamps
- No raw XML; no foreign keys
- Rollback: migrations are forward-only (SQLite convention)

## Explicit exclusions

- No domain upsert from offline imports in this gate
- No Tally write / IMPORT / EXECUTE execution
- No mobile UI, Bluetooth, or cloud transfer
- No automatic source-file deletion

## References

- ADR-005 Offline XML Ingestion Separation
- `xml-import-attempt-lifecycle.ts`
- Reliability Control #4 stage update §30
