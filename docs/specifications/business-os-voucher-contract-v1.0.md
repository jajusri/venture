# Business OS Voucher Contract v1.0

Status: MVP-1 functional specification  
Contract version: 1.0  
Scope: Read-only Voucher Summary, Voucher List, and Voucher Details  
Authority: This document is the single source of truth for Business OS voucher functionality.

## 1. Contract principles

1. Business OS defines the voucher experience. Source adapters must map source data into this contract.
2. The contract is source-neutral. It must not expose source-specific XML, field names, identifiers, payloads, or implementation details.
3. MVP-1 is read-only. Users cannot create, edit, delete, cancel, approve, post, or otherwise mutate vouchers.
4. All voucher data is isolated by the selected company.
5. A company must be selected before voucher data can be shown or synchronized.
6. Financial amounts use decimal strings and an explicit debit or credit side. Floating-point values are not valid contract values.
7. Dates use ISO calendar format: `YYYY-MM-DD`.
8. Timestamps use ISO 8601 with an explicit time zone.
9. Missing optional information is shown as unavailable, never guessed.
10. Loading, empty, and error states remain local to the Voucher module. They must not replace or remount the application shell.

## 2. Shared definitions

### 2.1 Voucher identity

`voucherId` is an opaque, stable Business OS identifier for one voucher within one company.

- It must remain stable when editable voucher content changes.
- It must not be constructed or interpreted by the user interface.
- It must not contain business-sensitive values.
- Voucher number, date, party, type, reference, and amount are not valid fallback identities.

### 2.2 Money

```text
Money {
  amount: decimal string
  currencyCode: string
  side: Debit | Credit
}
```

Rules:

- `amount` is always an absolute decimal value.
- `side` carries the debit/credit meaning.
- The interface may display `Dr` and `Cr`.
- The adapter must not infer a voucher-level total when the source does not provide an unambiguous value.

### 2.3 Data quality

```text
dataQuality: Complete | Incomplete
```

- `Complete` means every MVP-1 mandatory field is valid.
- `Incomplete` means the voucher can be identified and displayed but one or more optional or type-dependent fields are unavailable.
- Records without a valid stable identity, date, or voucher type do not satisfy this contract and must not be presented as normal vouchers.

### 2.4 Voucher status

```text
status: Active | Cancelled
```

MVP-1 does not introduce approval, posting, reconciliation, or workflow states.

### 2.5 Pagination

```text
Pagination {
  page: positive integer
  pageSize: positive integer
  totalItems: non-negative integer
  totalPages: positive integer
}
```

- Default page size: 25
- Maximum page size: 100
- A deterministic secondary order by `voucherId` is required.

## 3. Voucher Summary

### 3.1 Purpose

Give the user a concise view of voucher activity for the selected company and active period without requiring the full list to load.

### 3.2 Required fields

- Active period:
  - `dateFrom`
  - `dateTo`
- `totalVouchers`
- Counts by voucher type:
  - `voucherType`
  - `count`
- `lastSynchronizedAt`

`lastSynchronizedAt` may be null when the company has never been synchronized.

### 3.3 Optional fields

- `incompleteVouchers`
- `cancelledVouchers`

These fields may be shown only when the corresponding information is authoritative.

### 3.4 Search

The Summary screen has no independent search. Search belongs to Voucher List.

### 3.5 Filters

- Date from
- Date to
- Voucher type

Changing a Summary filter must apply the same filter to Voucher List.

### 3.6 Sorting

Counts by voucher type are sorted:

1. Count descending
2. Voucher type ascending

The user does not need a Summary sorting control in MVP-1.

### 3.7 Empty state

```text
No vouchers found for this period.
```

Show:

- Total vouchers: 0
- Empty type breakdown
- The active period
- Last synchronization time when available

The empty state is not an error.

### 3.8 Error state

```text
Voucher summary could not be loaded.
```

Requirements:

- Keep the Voucher module visible.
- Preserve the current filters.
- Offer a module-local Retry action.
- Do not clear a previously valid list or details view solely because Summary failed.
- Do not expose internal errors.

### 3.9 Loading state

```text
Loading voucher summary…
```

- Display inside the Summary area.
- Keep the Summary area at a stable height.
- Do not display a global loading banner.
- Background refresh should retain existing values until new values arrive.

### 3.10 No-company state

```text
Select a company to view voucher activity.
```

- Summary values are unavailable.
- Voucher synchronization and filters are disabled.
- The state is neutral, not an error.

## 4. Voucher List

### 4.1 Purpose

Allow users to find and inspect vouchers for the selected company and period.

### 4.2 Required fields per row

- `voucherId` — not displayed as a technical identifier
- `date`
- `voucherType`
- `voucherNumber`
- `partyName`
- `amount`
- `status`
- `dataQuality`

Display columns:

- Date
- Voucher Type
- Voucher Number
- Party
- Amount
- Status

`dataQuality` is displayed only when incomplete.

### 4.3 Optional fields per row

- `referenceNumber`
- `narrationPreview`

`narrationPreview` must be bounded and plain text.

### 4.4 Search

One search input searches:

- Voucher number
- Party name
- Reference number
- Voucher type

Rules:

- Case-insensitive
- Leading and trailing whitespace ignored
- Maximum query length: 128 characters
- Search must not include raw technical identifiers
- Search applies when submitted or after an established bounded debounce
- Clearing search returns to the filtered list

Full narration search is not required for MVP-1.

### 4.5 Filters

Required:

- Date from
- Date to
- Voucher type
- Status:
  - All
  - Active
  - Cancelled

Rules:

- `dateFrom` must not be after `dateTo`.
- Invalid ranges show a local validation message and do not submit.
- Filters reset pagination to page 1.
- Filters remain selected after refresh and detail viewing.

### 4.6 Sorting

Required sort fields:

- Date
- Voucher number
- Amount

Required directions:

- Ascending
- Descending

Default:

1. Date descending
2. Voucher number descending where comparable
3. Voucher ID ascending as the deterministic tie-breaker

### 4.7 Empty state

With no vouchers in the active period:

```text
No vouchers found for this period.
```

With search or filters applied:

```text
No vouchers match your search or filters.
```

Provide a Clear filters action when filters or search are active.

### 4.8 Error state

```text
Vouchers could not be loaded.
```

Requirements:

- Offer a module-local Retry action.
- Preserve search, filters, sorting, page, and previously displayed data where safe.
- Do not navigate away or show a global overlay.
- Do not expose internal errors or source payloads.

### 4.9 Loading state

Initial load:

```text
Loading vouchers…
```

Subsequent page, search, filter, or sort request:

- Use a list-local loading state.
- Keep existing list dimensions stable.
- Disable duplicate requests where necessary.
- Do not reset the selected application tab.
- Do not move focus unless the focused control is removed by the user action.

Background refresh is visually silent.

### 4.10 No-company state

```text
Select a company to view vouchers.
```

- Search, filters, pagination, details, and synchronization are disabled.
- No red or failure styling is used.

## 5. Voucher Details

### 5.1 Purpose

Show the complete Business OS representation of one selected voucher without exposing source-specific technical data.

### 5.2 Required header fields

- `voucherId` — retained internally, not shown as a technical value
- `date`
- `voucherType`
- `voucherNumber`
- `partyName`
- `amount`
- `status`
- `dataQuality`

### 5.3 Optional header fields

- `effectiveDate`
- `referenceNumber`
- `narration`

Missing optional values are displayed as:

```text
Not available
```

### 5.4 Required ledger-entry fields

Each available ledger entry contains:

- `lineNumber`
- `ledgerName`
- `amount`

Ledger entries retain their source order.

At least one ledger entry is normally expected, but the contract permits an empty ledger-entry list for voucher types whose authoritative representation is inventory-only. Such a voucher must be marked `Incomplete` unless the adapter contract explicitly recognizes that type as valid without ledger entries.

### 5.5 Optional ledger-entry fields

- `referenceType`
- `referenceName`

Bill allocations, cost-centre allocations, bank allocations, and tax allocations are not part of MVP-1.

### 5.6 Optional inventory-entry fields

Each inventory entry may contain:

- `lineNumber`
- `itemName`
- `quantity`
- `unit`
- `rate`
- `amount`

Inventory entries are optional because many accounting vouchers do not contain inventory.

### 5.7 Search

Voucher Details has no independent search.

The user returns to the unchanged Voucher List search and filter state.

### 5.8 Filters

Voucher Details has no independent filters.

### 5.9 Sorting

- Ledger entries retain authoritative source order.
- Inventory entries retain authoritative source order.
- The user does not sort detail entries in MVP-1.

### 5.10 Empty state

Before selection:

```text
Select a voucher to view details.
```

If a selected voucher has no optional ledger or inventory entries, show the available header and an appropriate local empty label for that section.

### 5.11 Error state

```text
Voucher details could not be loaded.
```

For a voucher that no longer exists:

```text
This voucher is no longer available. Refresh the voucher list.
```

Requirements:

- Preserve the Voucher List.
- Offer Retry or Refresh List as appropriate.
- Do not show source errors, stack traces, database paths, or raw payloads.

### 5.12 Loading state

```text
Loading voucher details…
```

- Display only inside the Details area.
- Keep the existing list usable where practical.
- Ignore a stale response if the user selects another voucher before the first request completes.
- Do not remount the Voucher module.

### 5.13 No-company state

```text
Select a company to view voucher details.
```

No detail request is made.

## 6. MVP-1 mandatory contract

### 6.1 Mandatory voucher fields

Every normal MVP-1 voucher must provide:

- Stable `voucherId`
- `date`
- `voucherType`
- `voucherNumber`
- `partyName`
- Unambiguous `amount`
- `status`
- `dataQuality`

Adapter policy:

- An adapter may map an authoritative blank business value to an empty display value only if the Business OS field remains semantically valid.
- An adapter must not manufacture voucher number, party, amount, or identity.
- If a source legitimately omits a mandatory Business OS field for a supported voucher type, that discrepancy requires a contract review rather than a source-specific UI exception.

### 6.2 Mandatory functional behavior

- Company isolation
- Summary count and type breakdown
- Date/type/status filtering
- Search
- Bounded pagination
- Date/number/amount sorting
- Voucher details
- Ledger-entry display
- Optional inventory-entry display
- Local loading, empty, error, and no-company states
- Stable refresh behavior
- Read-only operation
- Sanitized errors

## 7. Deferred to MVP-2

- Bill allocations
- Outstanding/reference settlement details
- Cost-centre allocations
- Bank allocation and reconciliation details
- Tax-component breakdowns
- Batch, godown, tracking-number, and order allocations
- Profitability and gross-profit analysis
- Voucher attachments
- Full narration search
- Saved filters
- Custom columns
- User-configurable page size
- Multi-company voucher views
- Cross-module global search
- Business-data export/share
- Printing or voucher-copy generation
- Audit history and alteration timeline
- Advanced period comparisons
- Aggregate monetary analytics by voucher type

## 8. Intentionally excluded

The following are outside the Business OS Voucher Contract v1.0:

- Creating vouchers
- Editing vouchers
- Deleting vouchers
- Cancelling or restoring vouchers
- Posting or approving vouchers
- Importing transactions
- Writing any data to the accounting source
- Executing source functions or actions
- Displaying or downloading raw source payloads
- Displaying source-specific technical identifiers
- Displaying internal database paths, process information, or stack traces
- Guessing missing values
- Deriving accounting, tax, compliance, or outstanding conclusions not explicitly represented by an approved Business OS contract

## 9. Source-adapter conformance

A future source adapter conforms to Business OS Voucher Contract v1.0 only when it:

1. Produces every mandatory field without fabrication.
2. Supplies a stable, company-scoped voucher identity.
3. Preserves decimal and debit/credit semantics.
4. Distinguishes complete and incomplete records.
5. Supports the required search, filters, sorting, and pagination.
6. Preserves child-entry ordering.
7. Prevents duplicate vouchers.
8. Prevents cross-company access.
9. Returns sanitized, bounded failures.
10. Remains strictly read-only toward the source system.

If a source cannot satisfy a mandatory field or behavior, the adapter is non-conforming. The Business OS contract must not be silently weakened to match source limitations.

## 10. Contract change policy

- Backward-compatible clarifications may be released as `1.x`.
- Removing or changing mandatory fields or semantics requires a new major version.
- Source-specific limitations must be documented in the adapter, not embedded into this contract.
- UI wording may vary only without changing the state semantics defined here.

