/**
 * One posting line, drawn from the already-synced Voucher snapshot, for a single named
 * ledger within a date range. This is the read shape the Ledger statement feature consumes —
 * distinct from {@link VoucherLedgerEntry}, which is the per-voucher nested shape — because a
 * statement is ledger-centric (one ledger, many vouchers) rather than voucher-centric (one
 * voucher, many ledger lines).
 */
export interface VoucherLedgerMovement {
  readonly voucherId: string;
  readonly date: string;
  readonly voucherType: string;
  readonly voucherNumber: string | null;
  readonly referenceNumber: string | null;
  readonly narration: string | null;
  readonly lineNumber: number;
  readonly amount: string;
  readonly amountSide: 'debit' | 'credit' | null;
}
