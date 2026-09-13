/** Flat inventory row returned by the dedicated walked Tally collection. */
export interface VoucherInventoryExtractionEntry {
  readonly parentGuid: string;
  readonly stockItemName: string;
  /** Exact trimmed XML value, including Tally's unit suffix. */
  readonly actualQuantity?: string;
  /** Exact trimmed XML value, including Tally's unit suffix. */
  readonly billedQuantity?: string;
  /** Exact trimmed XML value, including Tally's unit suffix. */
  readonly rate?: string;
  /** Exact trimmed signed amount supplied by Tally XML. */
  readonly signedAmount: string;
}
