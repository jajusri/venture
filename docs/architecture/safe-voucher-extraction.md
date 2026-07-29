# Safe Voucher Extraction Architecture

**Status:** Implemented and live-validated behind the controlled Voucher rollout
**Evidence date:** 2026-07-29
**Documentation updated:** 2026-07-30
**Validated scope:** `Budcom-Test-01`, 24-Jul-2026, 14 vouchers, 28 ledger entries, and 100 inventory entries
**Connector boundary:** Read-only Tally XML/TDL

## Decision

Voucher extraction uses three bounded custom-collection exports:

1. A minimal, date-filtered `Voucher` collection is the authoritative discovery path.
2. A separate collection uses `SOURCECOLLECTION` and `WALK: AllLedgerEntries` to export only
   `LedgerName`, `IsDeemedPositive`, and `Amount`.
3. A separate collection uses `SOURCECOLLECTION` and `WALK: AllInventoryEntries` to export only
   `StockItemName`, `ActualQty`, `BilledQty`, `Rate`, and `Amount`.
4. Each walked entry computes its owner Voucher GUID. The Connector joins ledger and inventory
   entries to discovered vouchers by that GUID.

The built-in Day Book report is not used. In live testing it returned valid XML with zero vouchers
for a company and date where the custom collection returned 14.

Root-level `Voucher.Amount` and direct compound-collection fetches are forbidden. The Connector must
reject malformed XML; it must not repair, strip, or sanitize illegal characters from Tally responses.

## Data flow

```text
Explicit company and date range
             |
             v
Minimal Voucher collection ---------> authoritative Voucher headers
             |
             v
SOURCECOLLECTION + WALK
AllLedgerEntries --------------------> selected LedgerEntry fields
             |
             v
SOURCECOLLECTION + WALK
AllInventoryEntries ----------------> selected InventoryEntry fields
             |
             v
Compute $$Owner:$GUID --------------> join both entry sets to Voucher headers
             |
             v
Validate ownership, entry counts, signs, and per-voucher ledger balance
```

Discovery completeness is determined only by the minimal Voucher collection. A ledger walk cannot
replace discovery because vouchers with no exported ledger entries would be absent from the walked
result.

## Request 1: authoritative Voucher discovery

Every request includes an explicit company and bounded date range:

```xml
<STATICVARIABLES>
  <SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>
  <SVCURRENTCOMPANY>Budcom-Test-01</SVCURRENTCOMPANY>
  <SVFROMDATE>20260724</SVFROMDATE>
  <SVTODATE>20260724</SVTODATE>
</STATICVARIABLES>
```

The collection contains only required Voucher metadata:

```xml
<COLLECTION NAME="Budcom Voucher Discovery"
            ISMODIFY="No"
            ISFIXED="No"
            ISINITIALIZE="Yes">
  <TYPE>Voucher</TYPE>
  <FILTERS>BudcomVoucherDateRange</FILTERS>
  <NATIVEMETHOD>Date</NATIVEMETHOD>
  <NATIVEMETHOD>VoucherTypeName</NATIVEMETHOD>
  <NATIVEMETHOD>VoucherNumber</NATIVEMETHOD>
  <NATIVEMETHOD>Reference</NATIVEMETHOD>
  <NATIVEMETHOD>Narration</NATIVEMETHOD>
  <NATIVEMETHOD>PartyLedgerName</NATIVEMETHOD>
  <NATIVEMETHOD>MasterID</NATIVEMETHOD>
  <NATIVEMETHOD>AlterID</NATIVEMETHOD>
  <NATIVEMETHOD>GUID</NATIVEMETHOD>
  <NATIVEMETHOD>IsCancelled</NATIVEMETHOD>
</COLLECTION>

<SYSTEM TYPE="Formulae" NAME="BudcomVoucherDateRange">
  $Date &gt;= $$Date:"24-Jul-2026"
  AND $Date &lt;= $$Date:"24-Jul-2026"
</SYSTEM>
```

`Amount` is deliberately absent. Ledger and inventory collections are deliberately absent from
the metadata request.

## Request 2: selected ledger entries

The source collection applies the same company and date boundary as discovery. The exported
collection walks the source vouchers instead of fetching a compound collection through each
Voucher:

```xml
<COLLECTION NAME="Budcom Voucher Ledger Source"
            ISMODIFY="No"
            ISFIXED="No"
            ISINITIALIZE="Yes">
  <TYPE>Voucher</TYPE>
  <FILTERS>BudcomVoucherDateRange</FILTERS>
  <NATIVEMETHOD>GUID</NATIVEMETHOD>
</COLLECTION>

<COLLECTION NAME="Budcom Voucher Ledger Entries"
            ISMODIFY="No"
            ISFIXED="No"
            ISINITIALIZE="Yes">
  <SOURCECOLLECTION>Budcom Voucher Ledger Source</SOURCECOLLECTION>
  <WALK>AllLedgerEntries</WALK>
  <NATIVEMETHOD>LedgerName</NATIVEMETHOD>
  <NATIVEMETHOD>IsDeemedPositive</NATIVEMETHOD>
  <NATIVEMETHOD>Amount</NATIVEMETHOD>
  <COMPUTE>ParentGUID : $$Owner:$GUID</COMPUTE>
</COLLECTION>
```

The response contains flat `LEDGERENTRY` elements. Each entry must contain a non-empty
`PARENTGUID`, and that value must resolve to exactly one Voucher from request 1.

## Request 3: selected inventory entries

The inventory source collection uses the same company and date boundary. Inventory is walked in a
dedicated response and is never fetched through a compound Voucher `NATIVEMETHOD`:

```xml
<COLLECTION NAME="Budcom Voucher Inventory Source"
            ISMODIFY="No"
            ISFIXED="No"
            ISINITIALIZE="Yes">
  <TYPE>Voucher</TYPE>
  <FILTERS>BudcomVoucherDateRange</FILTERS>
  <NATIVEMETHOD>GUID</NATIVEMETHOD>
</COLLECTION>

<COLLECTION NAME="Budcom Voucher Inventory Entries"
            ISMODIFY="No"
            ISFIXED="No"
            ISINITIALIZE="Yes">
  <SOURCECOLLECTION>Budcom Voucher Inventory Source</SOURCECOLLECTION>
  <WALK>AllInventoryEntries</WALK>
  <NATIVEMETHOD>StockItemName</NATIVEMETHOD>
  <NATIVEMETHOD>ActualQty</NATIVEMETHOD>
  <NATIVEMETHOD>BilledQty</NATIVEMETHOD>
  <NATIVEMETHOD>Rate</NATIVEMETHOD>
  <NATIVEMETHOD>Amount</NATIVEMETHOD>
  <COMPUTE>ParentGUID : $$Owner:$GUID</COMPUTE>
</COLLECTION>
```

The response contains flat inventory-entry elements. Source quantity, rate, and amount strings are
preserved. The Connector does not derive line amount from quantity and rate, and it does not infer
discounts.

## Production implementation

The production path keeps discovery, ledger parsing, inventory parsing, joining, and reconciliation
as distinct responsibilities:

- `src/tally/voucher/voucher-request.ts` defines the metadata-only discovery collection and the
  walked ledger-entry and inventory-entry collections.
- `src/tally/xml/request-builder.ts` renders supporting collections, `SOURCECOLLECTION`, `WALK`,
  and `COMPUTE` without falling back to compound Voucher fetches.
- `src/tally/registry/operation-registry.ts` allowlists the dedicated
  `VOUCHER_LEDGER_ENTRIES` and `VOUCHER_INVENTORY_ENTRIES` operations.
- `src/erp/voucher/voucher-ledger-domain.ts` defines the flat ledger-entry model and preserves each
  signed XML amount.
- `src/tally/voucher/voucher-ledger-parser.ts` parses the second response independently, validates
  required fields and amount/sign consistency, and normalizes the parent GUID.
- `src/tally/voucher/voucher-ledger-reconciler.ts` joins entries to discovered vouchers by
  normalized GUID, reconciles decimal amounts exactly, and derives a display total only after a
  zero balance is proven.
- `src/tally/voucher/voucher-inventory-parser.ts` and `voucher-inventory-joiner.ts` parse the
  dedicated inventory response and require every row to resolve to exactly one discovered Voucher.
- `src/tally/voucher/voucher-extractor.ts` performs the three reads and fails closed if discovery
  contains root amounts or compound ledger/inventory expansion.
- `src/tally/tally-module.ts` enables strict multi-phase extraction outside the test environment.
  Legacy embedded-ledger fixtures are accepted only by the isolated test-environment compatibility
  path and are not a production fallback.

The three response contracts use separate parsers and models. No parser sanitizes malformed XML, and
the production join never falls back to Voucher number, master ID, or response order.

## Why direct nested fetches are forbidden

On the validated Tally build, a dot-qualified fetch is collection exposure, not field projection.
For example:

```xml
<NATIVEMETHOD>AllLedgerEntries.LedgerName</NATIVEMETHOD>
```

expanded the complete `ALLLEDGERENTRIES.LIST` structure. Fetching only `LedgerName`, only
`IsDeemedPositive`, or only nested `Amount` produced the same 441,560-byte response and 328 illegal
XML numeric references. The first invalid value was `&#4;` under:

```text
VOUCHER/ALLLEDGERENTRIES.LIST/GSTCLASS
```

The `LedgerEntries` alias was broader still: it expanded unrelated inventory and batch allocation
fields. Its first invalid reference occurred under:

```text
VOUCHER/ALLINVENTORYENTRIES.LIST/BATCHALLOCATIONS.LIST/INDENTNO
```

The following constructs are forbidden for Voucher extraction:

- root `<NATIVEMETHOD>Amount</NATIVEMETHOD>`
- `AllLedgerEntries`, `LedgerEntries`, or their `.LIST` forms on the Voucher collection
- `AllLedgerEntries.<method>` or `LedgerEntries.<method>` on the Voucher collection
- wildcard fetches such as `AllLedgerEntries.*`
- an unqualified `Amount` on the Voucher collection
- inventory fetches in the ledger-entry request

Placing `WALK` directly on a typed Voucher collection is not an alternative. It returned the
unchanged header response with no ledger entries. `WALK` must operate on a separate
`SOURCECOLLECTION`.

## Live evidence

The controlled matrix used the same company and date for every request and changed one request
element at a time.

| Request shape | Bytes | Vouchers/owners | Ledger entries | Illegal references | Parser |
|---|---:|---:|---:|---:|---|
| Minimal Voucher metadata | 18,279 | 14 vouchers | 0 | 0 | Pass |
| Direct `AllLedgerEntries.LedgerName` | 441,560 | 14 raw parents | 28 | 328 | Fail |
| Direct `AllLedgerEntries.Amount` | 441,560 | 14 raw parents | 28 | 328 | Fail |
| Direct selected name, sign, and amount | 441,560 | 14 raw parents | 28 | 328 | Fail |
| `SOURCECOLLECTION` walk, amount only | 5,163 | Not mapped | 28 | 0 | Pass |
| `SOURCECOLLECTION` walk, name/sign/amount | 8,573 | Not mapped | 28 | 0 | Pass |
| Walk name/sign/amount plus owner GUID | 11,121 | 14 unique owners | 28 | 0 | Pass |

The smallest complete and mappable result was 11,121 bytes. It returned two ledger entries for each
of the 14 discovered Sales vouchers.

The production implementation was then exercised through an authorized read-only live extraction:

| Measure | Result |
|---|---:|
| Requested company/date | `budcom-test-01`, 24-Jul-2026 |
| Resolved Tally company | `Budcom-Test-01` |
| Tally operations | Company discovery, Voucher discovery, Voucher ledger entries, Voucher inventory entries |
| Candidate / accepted / persisted | 14 / 14 / 14 |
| Rejected / incomplete | 0 / 0 |
| Ledger entries | 28 |
| Inventory entries / allocations | 100 / 0 |
| Snapshot promotion | Completed |
| Validation issues | 0 |
| Duration | 4,213 ms |

The temporary validation database was removed after the run.

## Accounting validation

Tally supplied signed amounts consistently with `IsDeemedPositive`:

- `Yes` corresponded to a negative XML amount.
- `No` corresponded to a positive XML amount.

For every validated Voucher:

```text
sum(ledger-entry signed amounts) = 0.00
```

Example:

```text
Sales 16
  BALAJI ELECTRICALS HARDWARE, BADANGPET   -55.00  Yes
  WHOLESALE SALE                            55.00  No
  Balance                                    0.00
```

The validated day contained Sales vouchers with inventory data. Receipt, Payment, Contra, Journal,
Purchase, multi-currency, and additional rounding examples require equivalent live validation
before this design is promoted beyond the controlled rollout.

## Display-total rule

The Connector must not infer a display total from the root Voucher `Amount`.

A conservative ledger-derived display total may be produced only when:

1. every ledger amount parses in the expected currency representation;
2. every entry maps to exactly one discovered Voucher;
3. signed amounts balance within the currency's rounding precision; and
4. there is no unsupported multi-currency or incomplete-entry condition.

When those conditions hold:

```text
display total = sum(positive ledger amounts)
              = absolute value of sum(negative ledger amounts)
```

This is an accounting-side total, not a Tally root-amount field. If validation fails, the display
total is unavailable; malformed XML must fail closed.

## Implementation constraints

- Keep discovery, ledger extraction, and inventory extraction as separate contracts and parser
  paths.
- Join by Voucher GUID; never join by Voucher number alone.
- Preserve source entry order within each owner Voucher.
- Require every walked owner GUID to resolve to the discovery result.
- Permit discovered vouchers to have zero ledger entries, but classify their monetary data as
  unavailable.
- Apply independent request-size, response-size, node-count, timeout, and cancellation limits.
- Never retry automatically after malformed XML or an expansion-shaped response.
- Record structural counts and error paths without logging ledger names, narration, or amounts.
- Expose only proven source fields. Discount remains unsupported and must not be inferred.

## Verification

The implemented design passed:

- the focused safe-request, parser, GUID-join, persistence, and API mapping tests;
- the full Voucher and Connector suites;
- build, lint, and typecheck; and
- the authorized `Budcom-Test-01` live extraction described above.

The executable design contract is
`test/unit/voucher/two-phase-voucher-extraction-design.test.ts` and
`test/unit/voucher/voucher-inventory-extraction.test.ts`.

## Release gate

The implementation is approved for the existing controlled Voucher rollout. General release
remains blocked until the clean request and accounting reconciliation pass live for:

- at least one Sales or Purchase voucher;
- at least one Receipt, Payment, Contra, or Journal voucher;
- representative inventory-backed Vouchers with quantities, rates, and line amounts;
- cancellation and alteration cases; and
- representative rounding and multi-currency cases.
