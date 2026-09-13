# Ledger Extraction and Identity — Live Evidence (TallyPrime 3.0.1)

**Evidence type:** Controlled manual test (documentation only)  
**Date collected:** 2026-07-24  
**Scope:** Ledger collection export and master identity behaviour  
**Status:** Verified for one test company only — **not** universal proof

---

## Environment summary

| Attribute | Value |
|-----------|-------|
| Tally release | TallyPrime 3.0.1 |
| Host OS | Windows 11 |
| Endpoint | Loopback HTTP/XML (`127.0.0.1:9000`) |
| Test company label | `[VENTURE-TEST-01]` (synthetic test company) |
| GST | Disabled |
| Custom TDL installed | None permanently; embedded read-only FETCH used only in controlled custom-collection probe |
| Request mode | EXPORT-only |
| Safety | Separate test-company backup completed before rename/restart probes |

**Privacy:** This document uses aggregate counts and synthetic test ledger names only. No real company names, real ledger names (except the synthetic probe ledger), raw GUIDs, MasterIDs, AlterIDs, balances, raw XML, licence data, or identifiable paths are recorded.

---

## Probe 1 — Standard `List of Ledgers` export

| Metric | Result |
|--------|--------|
| XML root | `ENVELOPE` |
| Real ledger nodes | 922 |
| Empty structural `<LEDGER>` node | 1 additional |
| Line errors | 0 |

**Fields observed in standard export (essentially):**

- `NAME`
- `RESERVEDNAME`
- `LANGUAGENAME.LIST`

**Fields not exposed in standard export:**

- `PARENT`
- `GUID`
- `ALTERID`
- `MASTERID`
- `OPENINGBALANCE`
- `CLOSINGBALANCE`
- `ISBILLWISEON`

**Implication:** Standard collection export is a **shallow name list**, not sufficient for the connector’s current normalized ledger parser contract (which expects parent, balances, GUID, AlterID, contact/GST fields when present).

---

## Probe 2 — Embedded read-only custom TDL collection

Controlled collection explicitly fetched (read-only):

- Name, Parent, GUID, AlterID, MasterID, OpeningBalance, ClosingBalance, IsBillWiseOn

| Field | Populated count (of 922 ledgers) |
|-------|----------------------------------|
| PARENT | 922 |
| GUID | 922 |
| ALTERID | 922 |
| MASTERID | 922 |
| OPENINGBALANCE | 922 |
| CLOSINGBALANCE | 6 |
| ISBILLWISEON | 922 |
| Line errors | 0 |

**Uniqueness (922 ledgers):**

| Identifier | Unique values | Duplicates |
|------------|---------------|------------|
| GUID | 922 | 0 |
| AlterID | 922 | 0 |
| MasterID | 922 | 0 |

**Repeated export (two consecutive exports, same session):**

| Check | Result |
|-------|--------|
| Same GUID set | true |
| Same GUID ordered sequence | true |
| Same AlterID set | true |
| Same AlterID ordered sequence | true |
| Same MasterID set | true |
| Same MasterID ordered sequence | true |
| Line errors | 0 |

**Limitation:** Matching order across two exports in one session is **observed**, not a guaranteed Tally ordering contract. Do not use export order as a resume cursor or identity proof.

---

## Probe 3 — Controlled ledger rename

Synthetic ledger created and renamed (no other field changes):

| Step | Name |
|------|------|
| Initial | `VENTURE TEST LEDGER A` |
| Renamed | `VENTURE TEST LEDGER A RENAMED` |

| Check | Result |
|-------|--------|
| Renamed ledger found after rename | true |
| Ledger count stable | true |
| GUID unchanged across rename | true |
| MasterID unchanged across rename | true |
| AlterID changed on rename | true |
| Line errors | 0 |

**Implication:** **GUID and MasterID behave as stable master keys** for this rename. **AlterID behaves as a revision/change marker**, not a permanent identity.

---

## Probe 4 — Tally restart after rename

After closing and reopening Tally and reloading the same test company:

| Check | Result |
|-------|--------|
| Renamed ledger found | true |
| GUID stable vs pre-restart | true |
| MasterID stable vs pre-restart | true |
| AlterID stable at post-rename revision (no further change on restart) | true |
| Line errors | 0 |

---

## Architectural implications (hypothesis → evidence status)

| Hypothesis | Evidence status |
|------------|-----------------|
| GUID is preferred stable ledger identity | **Proven for this test company / TallyPrime 3.0.1 / rename + restart probes** |
| MasterID may serve as fallback when GUID absent | **Strongly indicated** (922/922 populated, stable across rename/restart) — **not universally proven** (company copy, backup/restore, re-creation untested) |
| AlterID is revision marker, not permanent identity | **Proven for rename probe** (changed on rename, stable after restart) |
| Name-slug should be last fallback only | **Architecturally indicated** — rename breaks name-slug upsert keys |
| Standard `List of Ledgers` insufficient for approved ledger contract | **Proven for this environment** |
| Minimal read-only embedded TDL FETCH may be required | **Strongly indicated** — only custom collection exposed required identity and hierarchy fields |

---

## Questions still requiring proof

1. **Other Tally releases** (2.x, 4.x, ERP variants) — same field coverage and identity behaviour?
2. **GST-enabled companies** — additional fields or different collection behaviour?
3. **MasterID stability** across company copy, backup/restore, data migration, master re-creation?
4. **GUID absence scenarios** — when does Tally omit GUID (legacy data, partial exports)?
5. **Cross-company MasterID uniqueness** — is MasterID scoped per company (expected) or global?
6. **Closing balance sparse population** (6/922) — environment-specific or contract limitation?
7. **Production pilot companies** with historical name-slug SQLite rows — migration impact of GUID-first re-key?

---

## Repository cross-reference (audit baseline)

At time of evidence registration, the connector:

- Sends standard `List of Ledgers` with **no** embedded TDL FETCH (contrast: stock items already use TDL FETCH).
- Derives ledger `id` from **name slug only** (`slugify(name)`).
- Parses GUID/AlterID when present but does **not** use them for upsert keys.
- Does **not** parse or store MasterID.
- Has **no** ledger `dataQuality` / shallow-export detection (unlike groups and stock items).
- Leaves **stale rows on rename** when deletion reconciliation is disabled (documented test expectation).

See milestone stage-update and this audit’s implementation recommendations before changing identity or extraction.

---

## Evidence limitations (mandatory)

- Single synthetic test company on one Windows machine.
- TallyPrime 3.0.1 only; GST disabled.
- Manual probes; not automated regression fixtures.
- Does not replace formal connector live-validation JSON for milestone sign-off.
- Does not authorize voucher, write, or product-scope expansion.

---

## Implementation status (2026-07-24)

**Status:** Implemented in working tree (uncommitted) — controlled-pilot reliability fix, not universal Tally compatibility proof.

### Extraction contract

- `MasterDataTemplates.ledgers()` now uses embedded read-only `collectionModifyFetch` for: `NAME`, `PARENT`, `GUID`, `ALTERID`, `MASTERID`, `OPENINGBALANCE`, `CLOSINGBALANCE`, `ISBILLWISEON` (EXPORT-only; no permanent TDL install).
- Shallow standard export (GUID and PARENT both absent) is classified `invalid` and fails sync — not silently accepted as rich extraction.

### Identity policy (locked)

| Rule | Implementation |
|------|----------------|
| Primary identity | `guid:{normalised-guid}` when GUID present |
| Fallback | `name:{slugified-name}` when GUID absent |
| AlterID | Revision/fingerprint metadata only — **never** identity |
| MasterID | Parsed and stored as nullable source metadata — **not** identity |

Helper: `connector/venture_connector/src/extraction/core/ledger-identity.ts` (`resolveLedgerStableId`, `LEDGER_IDENTITY_VERSION = 2`).

### Data quality

`assessLedgerExtraction()` returns aggregate `complete` \| `partial` \| `invalid` without logging private field values:

- **complete** — full GUID coverage with rich fields structurally available
- **partial** — valid ledgers with some GUID absences (name fallback used)
- **invalid** — shallow export, duplicate resolved IDs, or unusable collection

### Schema and storage (v5)

New ledger columns via `MIGRATION_005`: `master_id`, `identity_source`, `data_quality`, `is_bill_wise_on`.  
Company ledger cache version stored in `storage_meta` as `ledger_identity_version:{companyId}`.

### Controlled cache migration

Legacy slug-only pilot rows trigger one-time rebuild on next sync:

1. Detect legacy IDs or identity version &lt; 2
2. Backup via existing `createBackup()` API
3. Extract and validate GUID-first dataset
4. `replaceCompanyLedgersAtomically()` only after precheck passes
5. On extraction/validation/commit failure — **old cache preserved**, migration not marked complete

### Controlled-pilot API impact

- `/ledgers/{id}` IDs are now `guid:…` or `name:…` prefixed (breaking change for slug-only consumers).
- Deletion reconciliation remains **disabled** — GUID-first fixes rename continuity only.

### Tests and validation (2026-07-24)

| Command | Result |
|---------|--------|
| `npm run lint` (connector) | PASS |
| `npm run build` (connector) | PASS |
| `npx vitest run` (connector) | **376/376 PASS** |
| `test/architecture/module-boundaries.test.ts` | **12/12 PASS** |

Focused coverage: embedded FETCH template, shallow detection, identity helper, extraction quality, rename stability, migration backup/rebuild, failed-rebuild cache preservation, TD-006 lineage, backup/restore metadata.

### Live validation (2026-07-24)

Controlled loopback validation against TallyPrime 3.0.1 / `[VENTURE-TEST-01]` completed. Evidence: `docs/diagnostics/ledger-guid-identity-live-validation.json`.

| Result | Detail |
|--------|--------|
| Extraction quality | `complete` (923/923 GUID, 0 name fallback) |
| Migration | Backup + atomic replace; identity version → 2 |
| Rename continuity | 1 row for `VENTURE TEST LEDGER A RENAMED`; no stale slug rows |
| Repeat sync | 923 skipped; count stable; no second migration backup |
| Registry gap fixed | `operation-registry.ts` now renders `MasterDataTemplates.ledgers()` (live path was shallow before fix) |
| Response cap policy | **`RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES` = 1,048,576** — shared with stock-item rich FETCH; supersedes legacy 524,288 shallow cap |
| Measured rich response | 618,411 bytes / 923 ledgers (~41% cap headroom); stock items measured ~963KB / 1502 rec on same cap |
| Response cap | `RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES` (1 MiB) — aligns with stock-item rich FETCH convention |

### Remaining evidence gaps (post-implementation)

- GUID-first proven on controlled TallyPrime 3.0.1 evidence only; other releases/configurations unvalidated.
- MasterID backup/restore and company-copy stability unproven.
- Export order is not an ordering contract.
- Name fallback can still create stale identity on rename when GUID is absent.
- Deletion reconciliation remains disabled.
- Unrestricted production readiness remains unapproved.
