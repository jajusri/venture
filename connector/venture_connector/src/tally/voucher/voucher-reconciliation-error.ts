export type VoucherReconciliationReason =
  | 'duplicate-voucher-guid'
  | 'orphan-ledger-entry'
  | 'orphan-inventory-entry'
  | 'voucher-missing-guid'
  | 'ledger-entries-unbalanced';

/**
 * Typed classification for a two-phase discovery/ledger/inventory join failure --
 * mirrors XmlParseError's shape so callers can attach a privacy-safe `reason` (a fixed
 * enum string) to diagnostics without needing the raw message, which may embed a Tally
 * GUID. GUIDs are opaque internal identifiers, not business content, but are excluded
 * from persisted diagnostics anyway to stay strictly within the smallest necessary set.
 */
export class VoucherReconciliationError extends Error {
  constructor(
    readonly reason: VoucherReconciliationReason,
    message: string,
  ) {
    super(message);
    this.name = 'VoucherReconciliationError';
  }
}
