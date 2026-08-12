# BUDCOM Golden Acceptance Dataset Specification

**Status:** Required before broad post-MVP releases  
**Purpose:** Maintain one deliberately designed Tally company whose expected outcomes are known and repeatedly usable for regression/physical acceptance.

## 1. Principle

This dataset is not ordinary production data. It is a permanent acceptance fixture representing difficult but realistic BUDCOM business cases.

Every important release should be able to answer:

> Does BUDCOM still produce the expected result against the same known accounting truth?

## 2. Required voucher coverage

Include at minimum:

- Sales
- Purchase
- Receipt
- Payment
- Credit Note
- Debit Note
- Journal
- Contra
- Optional/Estimate voucher
- cancelled voucher where supported
- edited voucher
- deleted voucher
- voucher moved/corrected between ledgers where practical
- multiple vouchers on the same date

## 3. Ledger scenarios

Include ledgers with:

- Dr opening balance
- Cr opening balance
- no opening balance
- frequent Sales + many Receipts
- fewer than 7 Sales
- more than 7 Sales
- no recent activity
- old FY activity
- current FY activity
- same-day Sales + Receipt
- same-day Sales + Journal/Payment
- long ledger name
- punctuation/case edge cases
- similar-looking ledger names

## 4. Date/history scenarios

Include known transactions across:

- Current FY
- Previous FY
- 31 March boundary
- 1 April boundary
- custom older historical period
- >30-day range
- periods with complete coverage
- periods with intentionally incomplete/unsynced coverage

## 5. Voucher-detail scenarios

Include:

- single-item Sales
- multi-item Sales
- quantity correction
- rate correction
- narration
- reference
- discount/net-rate data if/when authoritative extraction supports it
- long item/party text
- multiple inventory lines

## 6. Optional/Estimate invariant

Include at least:

- one Optional/Estimate that remains unconverted;
- one estimate later converted to Sales;
- one deleted/rejected estimate where practical.

Expected invariant:

**Optional/Estimate never changes Ledger/accounting balances.**

## 7. Expected-results register

Maintain a companion table with:

- Case ID
- Tally voucher/ledger
- date
- expected amount
- expected Dr/Cr effect
- expected running balance
- expected closing balance
- expected BUDCOM visibility
- expected offline behavior
- expected deep link
- expected PDF/share result

## 8. Acceptance policy

Never change the expected result merely because BUDCOM currently disagrees.

If Tally truth changes intentionally, update:

1. the Tally fixture;
2. expected-results register;
3. reason/date for the change.

## 9. Dataset governance

- Keep a backup before every intentional fixture change.
- Do not use the golden company for casual experimentation.
- Give it a clearly recognizable name, e.g. `BUDCOM_ACCEPTANCE`.
- Preserve stable identities where possible.
- Record Tally version and relevant configuration.
- Keep a known-good exported backup.
