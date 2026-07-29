/** Flat ledger row returned by the second, walked Tally collection. */
export interface VoucherLedgerExtractionEntry {
  readonly parentGuid: string;
  readonly ledgerName: string;
  readonly isDeemedPositive: boolean;
  /** Normalized decimal retaining the sign supplied by Tally XML. */
  readonly signedAmount: string;
}

export interface VoucherLedgerReconciliation {
  readonly parentGuid: string;
  readonly entries: readonly VoucherLedgerExtractionEntry[];
  readonly signedBalance: string;
  readonly displayTotal: string | null;
}
