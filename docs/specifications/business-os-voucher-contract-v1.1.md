# Business OS Voucher Contract v1.1

Status: MVP-1 Implementation Baseline

Contract version: 1.1

Scope: Read-only Voucher Summary, Voucher List, and Voucher Details

Source posture: Source-neutral; Tally mapping pending controlled evidence

Authority: This document is the single source of truth for Business OS voucher implementation planning. It supersedes v1.0 for that purpose.

## 1. Contract principles

1. Business OS defines the voucher experience. Source adapters must map source data into this contract.
2. The contract is source-neutral. It must not expose source-specific XML, field names, identifiers, payloads, or implementation details.
3. MVP-1 is read-only. Users cannot create, edit, delete, cancel, approve, post, or otherwise mutate vouchers.
4. All voucher data is isolated by the selected company.
5. A company must be selected before voucher data can be shown or synchronized.
6. Financial amounts use decimal strings. Floating-point values are not valid contract values.
7. Dates use ISO calendar format: `YYYY-MM-DD`.
8. Timestamps use ISO 8601 with an explicit time zone.
9. Missing information is shown as unavailable, never guessed.
10. Loading, empty, and error states remain local to the Voucher module. They must not replace or remount the application shell.
11. Only voucher types proven by controlled source evidence to satisfy this contract are supported.

## 2. Shared definitions

### 2.1 Supported voucher types

- MVP-1 supports only voucher types proven by controlled source evidence to satisfy the required adapter contract.
- Unsupported or unproven voucher types must not be presented as fully supported.
- The initial supported-type allowlist will be finalized after controlled Tally evidence is captured.
- Adding a voucher type requires structurally faithful fixture evidence and contract tests.
- No implementation may assume that all Tally voucher types are supported.

### 2.2 Voucher identity

`voucherId` is an opaque, stable Business OS identifier for one voucher within one company.

- Stable source identity is mandatory and must be validated.
- It must remain stable when editable voucher content changes.
- It must not be constructed or interpreted by the user interface.
- It must not contain business-sensitive values.
- Mutable business fields must never be used as fallback identity.
- Voucher number, date, voucher type, party, reference, and amount are explicitly prohibited identity fallbacks.
- A record without validated stable source identity must not enter the normal live voucher cache. It is counted as rejected or unsupported extraction data, not as an `Incomplete` voucher.
- Duplicate stable identities with different content fail the synchronization.
- The identity strategy carries an explicit `identityVersion`.
- A change to the identity strategy requires an atomic rebuild of the affected company's voucher cache.
- Identity values must never be truncated.

### 2.3 Money

```text
Money {
  amount: decimal string
  side: Debit | Credit | null
}
```

Rules:

- A voucher-level `amount` is exposed only when the supported-type adapter proves it is authoritative.
- A header amount is nullable when no authoritative voucher-level total exists. Its absent display value is `Not available`.
- The adapter must not derive a header amount by summing all ledger entries.
- The adapter must not derive it from inventory values unless that derivation is explicitly proven by the supported-type adapter contract.
- A header debit/credit `side` is exposed only when its business meaning is proven for that voucher type.
- Exact signed or absolute amount semantics must be defined by the supported-type adapter before amount sorting is activated.
- Summary monetary aggregation is excluded from MVP-1.

### 2.4 Data quality

```text
dataQuality: Complete | Incomplete
```

`Complete` means:

- Stable identity exists.
- Voucher type and date are valid.
- All mandatory fields for that supported voucher type are valid.
- Required child structures for that voucher type parsed successfully.
- No blocking contract conflict exists.

`Incomplete` means:

- Stable identity exists.
- The voucher can be safely displayed.
- One or more applicable type-dependent fields or child structures are missing or invalid.
- The record is clearly labelled `Incomplete`.

The following do not, by themselves, make a voucher incomplete:

- Missing optional reference
- Missing optional narration
- Missing optional effective date
- Missing voucher number where blank is valid
- Missing party where no single party applies
- Missing inventory entries for non-inventory vouchers
- Missing authoritative header amount where the supported type does not define one

A record without stable identity is rejected, not `Incomplete`.

### 2.5 Voucher status

```text
status: Active | Cancelled
```

MVP-1 does not introduce approval, posting, reconciliation, or workflow states.

### 2.6 Pagination and validation limits

```text
Pagination {
  page: positive integer
  pageSize: positive integer
  totalItems: non-negative integer
  totalPages: non-negative integer
}
```

- Default page size: 25
- Maximum page size: 100
- Search query: maximum 128 characters
- Voucher number: maximum 128 characters
- Voucher type: maximum 128 characters
- Party name: maximum 256 characters
- Reference: maximum 256 characters
- Narration: stored and displayed only within an approved bounded limit
- Narration preview: maximum 160 characters
- A deterministic secondary order by `voucherId` is required.
- API values over a limit must be rejected, safely truncated only where this contract explicitly permits it, or classified `Incomplete` according to field semantics.
- Identity fields must never be truncated.

The initial full-narration storage-limit recommendation is 4,096 characters. This is a discovery candidate,
not an approved limit, and requires fixture-size, truncation, usability, and privacy evidence before adoption.
The 160-character narration preview limit remains approved.

### 2.7 Privacy

Ordinary logs and diagnostics must not contain:

- Voucher number
- Party name
- Reference
- Narration
- Amount
- Inventory values
- Ledger-entry values
- User search text
- Raw voucher XML

Sanitized aggregate counts and technical state names may be logged. Support artifacts must follow the same data-minimization rule unless a separately approved support contract explicitly authorizes otherwise.

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

`lastSynchronizedAt` is the completion time of the last successful Voucher resource synchronization for the selected company. It is independent of active list filters and is null before any successful Voucher synchronization.

### 3.3 Optional fields

- `incompleteVouchers`
- `cancelledVouchers`

These fields may be shown only when the corresponding information is authoritative.

### 3.4 Search

The Summary screen has no independent search. Search belongs to Voucher List.

### 3.5 Filters and active period

- Date from
- Date to
- Voucher type

Changing a Summary filter must apply the same filter to Voucher List.

The MVP-1 period decision is:

- `defaultDateRange`: current financial year to date
- `maximumDateRange`: one financial year
- Every connector-boundary request carries explicit `dateFrom` and `dateTo`.
- The desktop may resolve the current financial-year range, but the connector must validate it.
- A request exceeding one financial year fails validation.
- Unbounded Voucher synchronization is prohibited.
- Historical-year selection is deferred until performance validation is complete.

Company discovery exposes optional financial-year and books-from metadata, but the repository does not yet
provide a guaranteed authoritative financial-year boundary resolver. Until one is approved, the caller must
supply explicit start and end dates derived from approved company-period evidence. Neither desktop nor
connector may silently assume a financial-year start month.

### 3.6 Sorting

Counts by voucher type are sorted:

1. Count descending
2. Voucher type ascending

The user does not need a Summary sorting control in MVP-1.

### 3.7 Empty state

```text
No vouchers found for this period.
```

Show total vouchers as zero, an empty type breakdown, the active period, and the last synchronization time when available. An empty state is not an error and does not itself prove that a full extraction was complete.

### 3.8 Error state

```text
Voucher summary could not be loaded.
```

- Keep the Voucher module visible.
- Preserve current filters.
- Offer a module-local Retry action.
- Do not clear a previously valid list or details view solely because Summary failed.
- Do not expose internal errors.

### 3.9 Loading state

```text
Loading voucher summary…
```

- Display inside the Summary area at a stable height.
- Do not display a global loading banner.
- Background refresh retains existing values until replacement data is valid.

### 3.10 No-company state

```text
Select a company to view voucher activity.
```

Summary values are unavailable; voucher synchronization and filters are disabled; the state is neutral, not an error.

## 4. Voucher List

### 4.1 Purpose

Allow users to find and inspect vouchers for the selected company and period.

### 4.2 Fields per row

Mandatory for every cached voucher:

- `voucherId` — not displayed as a technical identifier
- `date`
- `voucherType`
- `status`
- `dataQuality`

Conditional:

- `voucherNumber` — required when authoritatively supplied for the supported voucher type; otherwise null
- `partyName` — required only when a single authoritative party exists for the supported voucher type; otherwise null
- `amount` — present only when an authoritative comparable voucher-level amount is proven; otherwise null

Optional:

- `referenceNumber`
- `narrationPreview`

Display columns are Date, Voucher Type, Voucher Number, Party, Amount, and Status. A missing conditional value is displayed as `Not available`. `dataQuality` is displayed only when incomplete.

The adapter must never fabricate a voucher number, derive it from another field, or infer party from an arbitrary ledger entry.

### 4.3 Search

One search input searches populated values of:

- Voucher number
- Party name
- Reference number
- Voucher type

Rules:

- Case-insensitive
- Leading and trailing whitespace ignored
- Maximum query length: 128 characters
- No raw technical identifier search
- Apply on submission or after an established bounded debounce
- Clearing search returns to the filtered list

Full narration search is not required for MVP-1.

### 4.4 Filters

Required:

- Date from
- Date to
- Voucher type
- Status: All, Active, or Cancelled

`dateFrom` must not follow `dateTo`. Invalid ranges show local validation and are not submitted. Filter changes reset pagination to page 1. Filters remain selected after refresh and detail viewing.

### 4.5 Sorting

Date:

- ISO date order
- Stable secondary order by `voucherId`

Voucher number:

- Case-insensitive natural ordering
- Missing numbers follow populated numbers in ascending order
- Stable secondary order by date, then `voucherId`

Amount:

- Enabled only where the adapter marks the amount authoritative and comparable
- Records without a comparable amount follow comparable records in ascending order
- Exact signed or absolute semantics are defined by the supported-type adapter before activation
- Disabled when no safe cross-record rule exists

Default ordering is date descending with `voucherId` as the final deterministic tie-breaker.

### 4.6 Empty state

Without active search or additional filters:

```text
No vouchers found for this period.
```

With search or filters:

```text
No vouchers match your search or filters.
```

Provide Clear filters when appropriate.

### 4.7 Error state

```text
Vouchers could not be loaded.
```

Offer module-local Retry; preserve query state and valid displayed data where safe; do not navigate away, show a global overlay, or expose source details.

### 4.8 Loading state

Initial load:

```text
Loading vouchers…
```

Subsequent list operations use a list-local loading state with stable dimensions. Duplicate requests are prevented where necessary. Focus, selected tab, and scroll state are preserved. Background refresh is visually silent.

### 4.9 No-company state

```text
Select a company to view vouchers.
```

Search, filters, pagination, details, and synchronization are disabled without red or failure styling.

## 5. Voucher Details

### 5.1 Purpose

Show the complete Business OS representation of one selected voucher without exposing source-specific technical data.

### 5.2 Header fields

Mandatory:

- `voucherId` — retained internally, not shown as a technical value
- `date`
- `voucherType`
- `status`
- `dataQuality`

Conditional:

- `voucherNumber`
- `partyName`
- `amount`

Optional:

- `effectiveDate`
- `referenceNumber`
- `narration`

Missing conditional or optional values are displayed as `Not available`. Their absence does not imply `Incomplete` unless the supported-type contract requires that field.

### 5.3 Ledger entries

Each available ledger entry contains:

- `lineNumber`
- `ledgerName`
- `amount`

Ledger entries retain authoritative source order. Whether ledger entries are required is defined per supported voucher type. An empty list is valid only when that type's proven adapter contract permits it.

Optional ledger-entry fields:

- `referenceType`
- `referenceName`

Bill allocations, cost-centre allocations, bank allocations, and tax allocations are not part of MVP-1.

### 5.4 Inventory entries

Each available inventory entry may contain:

- `lineNumber`
- `itemName`
- `quantity`
- `unit`
- `rate`
- `amount`

Inventory entries retain authoritative source order. They are required only for supported voucher types whose adapter contract requires them.

### 5.5 Search, filters, and sorting

Voucher Details has no independent search or filters. The user returns to unchanged Voucher List state. Detail entries retain source order and are not user-sortable in MVP-1.

### 5.6 Empty state

Before selection:

```text
Select a voucher to view details.
```

When optional child collections are empty, show the available header and a local empty label.

### 5.7 Error state

```text
Voucher details could not be loaded.
```

For a removed voucher:

```text
This voucher is no longer available. Refresh the voucher list.
```

Preserve the Voucher List, offer Retry or Refresh List, and do not expose source errors, stack traces, paths, or raw payloads.

### 5.8 Loading state

```text
Loading voucher details…
```

Display only inside Details. Keep the list usable where practical, ignore stale responses after selection changes, and do not remount the Voucher module.

### 5.9 No-company state

```text
Select a company to view voucher details.
```

No detail request is made.

## 6. MVP-1 synchronization and cache contract

### 6.1 Initial synchronization model

- Use a controlled full snapshot for an explicitly approved company and date range.
- Make no claim of incremental synchronization correctness.
- The requested date range must be explicit and must comply with the approved `defaultDateRange` and `maximumDateRange`.
- Use staging for extraction and validation.
- Keep the previous live snapshot available throughout extraction.
- A failed, cancelled, truncated, or contract-invalid run retains the previous live snapshot.
- Progress reports only real processed and total values supplied by the operation.
- Fake estimates, percentages derived from invented totals, and ETA fabrication are prohibited.

### 6.2 Atomic promotion and deletion reconciliation

Staged data may replace the affected live company/date-range snapshot, and cached vouchers may be deleted by reconciliation, only after all conditions are true:

1. Transport completed successfully.
2. The response remained within approved limits.
3. XML and source-contract validation passed.
4. Requested company and date range were confirmed.
5. Duplicate identity conflicts were absent.
6. All staging writes completed.
7. The extraction was classified as complete.
8. The synchronization was not cancelled.

Unexpected empty responses, partial results, parser success without completeness proof, timeout, truncation, cancellation, or structural drift must never clear existing cached vouchers.

## 7. MVP-1 mandatory contract

### 7.1 Mandatory voucher behavior

Every normal cached voucher provides:

- Valid stable `voucherId` and `identityVersion`
- Valid `date`
- Valid allowlisted `voucherType`
- Applicable mandatory fields and child structures for that supported type
- `status`
- Objective `dataQuality`

Voucher number, party, and header amount are conditional as defined by the supported-type adapter. Missing conditional values are never fabricated.

### 7.2 Mandatory functional behavior

- Company isolation
- Approved supported-type allowlist
- Summary count and type breakdown
- Explicit date/type/status filtering
- Search
- Bounded pagination
- Safe deterministic sorting
- Voucher details
- Type-appropriate child-entry display
- Local loading, empty, error, and no-company states
- Controlled full-snapshot synchronization with staging and fail-closed promotion
- Stable refresh behavior
- Strictly read-only operation
- Sanitized errors and privacy-safe diagnostics

## 8. Deferred to MVP-2

- Incremental synchronization, unless separately proven and contracted
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

## 9. Intentionally excluded

- Creating, editing, deleting, cancelling, restoring, posting, or approving vouchers
- Importing transactions or writing any data to the accounting source
- Executing source functions or actions
- Assuming support for every source voucher type
- Displaying or downloading raw source payloads
- Displaying source-specific technical identifiers
- Exposing sensitive voucher values in logs or diagnostics
- Displaying internal database paths, process information, or stack traces
- Guessing missing values or mutable fallback identities
- Deriving header totals, parties, accounting, tax, compliance, or outstanding conclusions without an approved adapter contract
- Summary monetary aggregation

## 10. Source-adapter conformance

Each supported voucher type must have tests proving:

1. Stable, company-scoped identity and explicit identity version
2. Mandatory-field mapping
3. Optional and conditional field behavior
4. Date normalization
5. Cancellation mapping
6. Applicable child structures and ordering
7. Amount behavior when exposed
8. Objective completeness behavior
9. Duplicate identity handling
10. Privacy-safe failures

The adapter must also prove bounded extraction, company/date-range confirmation, complete-export classification, atomic staging promotion, cross-company isolation, and strictly read-only source access.

If a source type cannot satisfy these requirements, it remains unsupported. This contract must not be silently weakened to match source limitations.

## 11. Implementation-readiness gates

| Gate | Current status | Evidence | Remaining action | Owner |
|---|---|---|---|---|
| A — Product contract ready | PASS | v1.1 defines the MVP-1 baseline and resolves the period as explicit current-financial-year-to-date with a one-financial-year maximum, without assuming a start month. | Validate caller-supplied dates against approved company-period evidence during implementation. | Product owner |
| B — Fixture company authorized | BLOCKED | No authorization is recorded by this contract task. | Designate a non-production fixture company and explicitly authorize bounded create/edit/cancel/delete experiments there only. | Product owner and Tally data owner |
| C — Transactional export captured | FAIL | Live transactional XML was intentionally not inspected and no controlled fixture evidence was captured in this task. | Run the approved discovery harness and retain sanitized, structurally faithful fixtures. | Connector engineering |
| D — Stable identity proven | FAIL | v1.1 defines the identity requirement but no controlled source evidence proves a candidate. | Test identity stability across create, edit, cancel, and delete scenarios. | Connector engineering |
| E — Supported voucher types approved | BLOCKED | The allowlist is intentionally pending evidence. | Approve only types whose fixtures and conformance tests pass. | Product owner and accounting domain reviewer |
| F — Financial semantics approved per supported type | BLOCKED | Header amount and side are conditional; no type-specific semantics are yet approved. | Approve number, party, amount, side, and child-entry semantics per candidate type. | Accounting domain reviewer |
| G — Complete-export rule proven | FAIL | Fail-closed conditions are specified, but completeness proof is not established for a source request. | Prove bounded full-export completion, company/range confirmation, truncation detection, and empty-response behavior. | Connector engineering and security reviewer |
| H — Performance limits approved | BLOCKED | Page and field limits are defined; source snapshot range and narration storage bound remain unresolved, and no measurements exist. | Approve bounds and measure extraction, staging, storage, and query performance against the fixture matrix. | Product owner and engineering |
| I — Privacy review passed | BLOCKED | Prohibited log/diagnostic data is specified, but no implementation or failure-path evidence has been reviewed. | Review fixtures, adapter failures, logs, diagnostics, and support artifacts against the privacy rules. | Security/privacy reviewer |
| J — Operational Phase 1 approved | BLOCKED | Gates B–I are not satisfied. | Approve operational Phase 1 only after all prerequisite evidence and reviews pass. | MVP-1 release authority |

## 12. Controlled discovery prerequisite

Do not begin production implementation until the relevant gates above are approved.

1. Designate or create a non-production Tally fixture company.
2. Confirm explicit permission for create/edit/cancel/delete experiments inside that fixture only.
3. Define the exact fixture voucher matrix.
4. Record expected voucher counts and known records.
5. Register a candidate voucher operation as experimental and disabled.
6. Add request-shape and policy tests.
7. Run a reviewed, bounded discovery harness.
8. Capture structurally faithful sanitized fixtures.
9. Run stable identity and completeness experiments.
10. Review evidence before approving production implementation.

## 13. Contract change policy

- v1.1 supersedes v1.0 for implementation planning.
- Backward-compatible clarifications may be released as `1.x`.
- Removing or changing mandatory fields or semantics requires a new major version.
- Source-specific limitations belong in the adapter contract, not in source-specific UI exceptions.
- Adding a supported type requires fixture evidence and conformance tests.
- Changing identity strategy requires a version change and atomic company voucher-cache rebuild.
- UI wording may vary only without changing the state semantics defined here.
