import type { AmountSide } from '../shared/decimal-money.js';

/** One dated transaction line in a Ledger statement, in accounting order. */
export interface LedgerStatementTransaction {
  /** Stable, GUID-derived Voucher identity — safe for a "View Voucher" deep link. */
  readonly voucherId: string;
  readonly date: string;
  readonly voucherType: string;
  readonly voucherNumber: string | null;
  readonly referenceNumber: string | null;
  readonly narration: string | null;
  readonly debit: string | null;
  readonly credit: string | null;
  /** Null when the statement's balance anchor is unavailable — see {@link LedgerStatementCoverage}. */
  readonly runningBalance: { readonly amount: string; readonly side: AmountSide } | null;
}

/**
 * Honest reporting of whether the underlying synced data was sufficient to compute this
 * statement authoritatively — never silently fabricated. `transactionsComplete` covers the
 * visible transaction list; `balanceAvailable` covers opening/closing/running balance, which
 * additionally requires voucher coverage through the ledger's own last-synced date.
 */
export interface LedgerStatementCoverage {
  readonly transactionsComplete: boolean;
  readonly balanceAvailable: boolean;
  readonly syncedFrom: string | null;
  readonly syncedTo: string | null;
  readonly message: string | null;
}

export interface LedgerStatement {
  readonly ledgerId: string;
  readonly ledgerName: string;
  readonly parentGroup?: string;
  readonly period: { readonly from: string; readonly to: string };
  readonly openingBalance: { readonly amount: string; readonly side: AmountSide } | null;
  readonly closingBalance: { readonly amount: string; readonly side: AmountSide } | null;
  readonly transactions: readonly LedgerStatementTransaction[];
  readonly coverage: LedgerStatementCoverage;
}
