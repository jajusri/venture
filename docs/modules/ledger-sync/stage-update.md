# Ledger sync module — stage update

**Last updated:** 2026-07-24

**Scope:** Reliability — ledger extraction contract and GUID-first identity remediation (controlled pilot)

---

## Status

**Complete in working tree (uncommitted)** — defect fix for existing ledger sync scope, not a new product feature.

---

## Requirement addressed

Live evidence (TallyPrime 3.0.1) proved standard `List of Ledgers` is shallow; embedded read-only FETCH returns GUID, hierarchy, balances, and revision metadata. Prior connector used name-slug identity, causing rename stale rows and insufficient extraction.

---

## Implementation

| Area | Change |
|------|--------|
| Extraction template | `MasterDataTemplates.ledgers()` — embedded FETCH for 8 approved fields |
| Identity | `resolveLedgerStableId()` — `guid:{normalised}` → `name:{slug}` fallback |
| Quality gate | `assessLedgerExtraction()` — `complete` / `partial` / `invalid`; sync fails on `invalid` |
| Domain | Contract v2; `identitySource`, `dataQuality`, `masterId`, `isBillWiseOn` |
| Schema | v5 / `MIGRATION_005` — metadata columns + `ledger_identity_version:{companyId}` |
| Migration | Backup → validate → `replaceCompanyLedgersAtomically()`; preserve old cache on failure |
| Rename | Same GUID updates one row; count stable after migration |

**Explicitly excluded:** AlterID/MasterID in identity chain; deletion reconciliation; vouchers/inventory; permanent TDL install.

---

## Files changed (representative)

- `src/extraction/core/ledger-identity.ts` (new)
- `src/erp/ledger/ledger-extraction-quality.ts` (new)
- `src/services/ledger/ledger-cache-migration.ts` (new)
- `src/extraction/templates/master-data-templates.ts`
- `src/extraction/parsers/entity-mappers.ts`
- `src/services/ledger/ledger-sync.service.ts`
- `src/storage/sqlite/sqlite-ledger-repository.ts`, `schema.ts`

---

## Tests

| Suite | Result |
|-------|--------|
| Connector lint / build | PASS |
| Full connector tests | **376/376 PASS** |
| Architecture boundaries | **12/12 PASS** |

New/focused: `ledger-identity.test.ts`, `ledger-extraction-quality.test.ts`, `ledger-identity-migration.test.ts`, template and mapper updates.

---

## Known limitations

- GUID-first validated on controlled TallyPrime 3.0.1 evidence only
- MasterID backup/restore stability unproven
- Name fallback rename risk when GUID absent
- Deletion reconciliation disabled
- Controlled-pilot breaking change: API ledger IDs are `guid:` / `name:` prefixed

---

## Production readiness

Controlled pilot — **not** unrestricted production approval.
