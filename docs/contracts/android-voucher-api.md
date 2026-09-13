# Confirmed Connector Contracts — Vouchers (Android)

**Date:** 2026-07-27  
**Source of truth:** `connector/venture_connector` implementation  
**Purpose:** Contract discovery for VENTURE Android Voucher Foundation / Voucher Browser

---

## List / search (primary for Voucher Browser)

| Item | Confirmed value |
| --- | --- |
| Routes | `GET /api/v1/vouchers` and `GET /api/v1/vouchers/search` (same handler) |
| Method | GET |
| Company | **Required** query `company` (explicit company id; not session-implied) |
| Date range | **Required** `from` and `to` as `YYYY-MM-DD`; `to` must not precede `from` |
| Auth headers | None |
| Validation failure | HTTP **400** `VALIDATION_ERROR` with `details.errors[]` |

### Query parameters

| Param | Behavior |
| --- | --- |
| `company` | Required company id (1–128 chars) |
| `from` | Required start date `YYYY-MM-DD` |
| `to` | Required end date `YYYY-MM-DD` |
| `page` | Default 1; positive integer |
| `pageSize` | Default 50; max 100 |
| `sort` | Optional; fields `date` (default), `voucherNumber`, `amount`; direction via leading `-` or `:asc`/`:desc` |
| `q` | Optional free-text search (max 128); matches number, reference, party, type |
| `voucherType` | Optional exact type filter (max 128) |
| `voucherNumber` | Optional exact number filter (max 128) |
| `partyName` | Optional exact party filter (max 128) |

### Response (200)

```json
{
  "schemaVersion": "1.0.0",
  "data": {
    "companyId": "…",
    "items": [ /* VoucherPublicRecord */ ],
    "pagination": { "page": 1, "pageSize": 50, "totalItems": 0, "totalPages": 1 }
  }
}
```

### VoucherPublicRecord fields

`id`, `date`, `type`, `number`, `partyName`, `referenceNumber`, `amount` (`{ value, side }` where `side` is `debit`|`credit`|null), `status` (`active`|`cancelled`), `dataQuality` (`complete`|`incomplete`)

### Source files

- `src/api/routes/vouchers.ts`
- `src/services/voucher/voucher-dtos.ts`
- `src/services/voucher/voucher-application.service.ts`
- `src/erp/voucher/voucher-domain.ts`
- Tests: `test/unit/voucher/voucher-api.test.ts`

---

## Detail (Voucher Details)

| Item | Value |
| --- | --- |
| Route | `GET /api/v1/vouchers/:id?company=…` |
| Method | GET |
| Company | Required query `company` |
| 200 | `{ schemaVersion, data: { companyId, voucher: VoucherPublicDetails } }` |
| 404 | `{ code: "NOT_FOUND", message }` |

### VoucherPublicDetails fields

All `VoucherPublicRecord` fields, plus:

- `effectiveDate` (`string | null`)
- `narration` (`string | null`)
- `ledgerEntries[]`: `lineNumber`, `ledgerName`, `amount{value,side}`, `isDeemedPositive`
- `inventoryEntries[]`: `lineNumber`, `itemName`, `quantity`, `amount{value,side}|null`

Not exposed on the public detail contract (intentionally omitted by Connector):

- `guid`, `masterId`, `alterId`, nested `allocations`, tax-specific blocks, attachments

Android displays only confirmed public fields. Missing/null values are shown as “Not provided” / omitted sections honestly.

---

## Snapshots (out of scope for Voucher Browser UI)

- `GET /api/v1/vouchers/snapshots?company=…`
- `GET /api/v1/vouchers/snapshots/:snapshotId?company=…`

---

## Android client notes

- List data comes from promoted Connector snapshots; empty list is valid when never synced for the company/period.
- Android must supply explicit `company`, `from`, and `to` on every list call.
- Default browser date range is a client convenience (last 30 days inclusive, UTC calendar dates) and is editable in UI — not a Connector default.
- Sync / write / create / delete are **out of scope** for this milestone.
