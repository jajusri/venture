/** Flat ledger row returned by the second, walked Tally collection. */
export interface VoucherLedgerExtractionEntry {
  readonly parentGuid: string;
  readonly ledgerName: string;
  readonly isDeemedPositive: boolean;
  /** Normalized decimal retaining the sign supplied by Tally XML. */
  readonly signedAmount: string;
  /**
   * True when IsDeemedPositive (the ledger's debit/credit-positive nature by group
   * classification) disagrees with the signed Amount's sign (this specific
   * transaction's actual Dr/Cr direction, per the codebase's established
   * inferSideFromSign() convention -- extraction/normalization/amounts.ts). These are
   * two legitimately independent Tally fields; a mismatch is a real, valid business
   * case (e.g. a debit to a normally credit-positive liability ledger), not evidence
   * of a malformed record -- tolerated and counted, never fatal.
   */
  readonly amountSignConflict: boolean;
}

export interface VoucherLedgerReconciliation {
  readonly parentGuid: string;
  readonly entries: readonly VoucherLedgerExtractionEntry[];
  readonly signedBalance: string;
  readonly displayTotal: string | null;
}
