/**
 * Distinguishable, privacy-safe reasons for a Voucher ledger/inventory ENTRY parse
 * failure -- i.e. failures inside VoucherLedgerEntryParser/VoucherInventoryEntryParser
 * themselves, as opposed to XmlParseError (malformed XML) or VoucherReconciliationError
 * (the join/reconciliation step). Each is a fixed enum string derived directly from an
 * existing code branch; never raw error text or business content.
 */
export type VoucherEntryParseReason =
  | 'invalid-root'
  | 'missing-header'
  | 'missing-body'
  | 'missing-data'
  | 'missing-collection'
  | 'tally-line-error'
  | 'missing-parent-guid'
  | 'invalid-parent-guid-node-count'
  | 'missing-ledger-name'
  | 'missing-stock-item-name'
  | 'missing-or-malformed-amount'
  | 'missing-or-invalid-is-deemed-positive'
  | 'amount-sign-conflict';

export class VoucherEntryParseError extends Error {
  constructor(readonly reason: VoucherEntryParseReason, message: string) {
    super(message);
    this.name = 'VoucherEntryParseError';
  }
}
