import type { VoucherLedgerExtractionEntry } from '../../erp/voucher/voucher-ledger-domain.js';
import type {
  VoucherDetails,
  VoucherLedgerEntry,
} from '../../erp/voucher/voucher-domain.js';
import { normalizeGuid } from './voucher-ledger-parser.js';
import { formatDecimal, parseDecimal, rescale } from '../../erp/shared/decimal-money.js';
import { VoucherReconciliationError } from './voucher-reconciliation-error.js';

export function joinAndReconcileVoucherLedgers(
  vouchers: readonly VoucherDetails[],
  extractedEntries: readonly VoucherLedgerExtractionEntry[],
): readonly VoucherDetails[] {
  const vouchersByGuid = new Map<string, VoucherDetails>();
  for (const voucher of vouchers) {
    if (!voucher.guid) continue;
    const guid = normalizeGuid(voucher.guid);
    if (vouchersByGuid.has(guid)) {
      throw new VoucherReconciliationError('duplicate-voucher-guid', `Duplicate Voucher GUID: ${guid}.`);
    }
    vouchersByGuid.set(guid, voucher);
  }

  const grouped = new Map<string, VoucherLedgerExtractionEntry[]>();
  for (const entry of extractedEntries) {
    if (!vouchersByGuid.has(entry.parentGuid)) {
      // Ledger walk returned an entry for a Voucher the discovery phase never listed --
      // e.g. deleted in Tally between the two phase-separated requests, or a cross-report
      // consistency gap on Tally's side. This is the specific "two-phase join mismatch"
      // shape distinct from a Voucher simply having zero ledger entries (tolerated below).
      throw new VoucherReconciliationError(
        'orphan-ledger-entry',
        `Voucher ledger entry references unknown parent GUID: ${entry.parentGuid}.`,
      );
    }
    const entries = grouped.get(entry.parentGuid) ?? [];
    entries.push(entry);
    grouped.set(entry.parentGuid, entries);
  }

  return vouchers.map((voucher) => {
    if (!voucher.guid) {
      if (extractedEntries.length > 0) {
        throw new VoucherReconciliationError(
          'voucher-missing-guid',
          `Voucher ${voucher.voucherId} cannot join ledger entries without GUID.`,
        );
      }
      return voucher;
    }
    const entries = grouped.get(normalizeGuid(voucher.guid)) ?? [];
    if (entries.length === 0) return voucher;
    const decimals = entries.map((entry) => parseDecimal(entry.signedAmount));
    const scale = Math.max(...decimals.map((amount) => amount.scale));
    const signedBalance = decimals.reduce(
      (sum, amount) => sum + rescale(amount, scale),
      0n,
    );
    if (signedBalance !== 0n) {
      throw new VoucherReconciliationError(
        'ledger-entries-unbalanced',
        `Voucher ${voucher.voucherId} ledger entries do not balance.`,
      );
    }
    const positiveTotal = decimals.reduce((sum, amount) => {
      const value = rescale(amount, scale);
      return sum + (value > 0n ? value : 0n);
    }, 0n);
    const displayTotal = formatDecimal(positiveTotal, scale);
    return {
      ...voucher,
      amount: { amount: displayTotal, side: null },
      amountComparable: true,
      ledgerEntries: entries.map(toVoucherLedgerEntry),
      inventoryEntries: [],
      allocations: [],
    };
  });
}

function toVoucherLedgerEntry(
  entry: VoucherLedgerExtractionEntry,
  index: number,
): VoucherLedgerEntry {
  const negative = entry.signedAmount.startsWith('-');
  return {
    lineNumber: index + 1,
    ledgerName: entry.ledgerName,
    amount: {
      amount: absoluteDecimal(entry.signedAmount),
      side: negative ? 'credit' : 'debit',
    },
    isDeemedPositive: entry.isDeemedPositive,
    allocations: [],
  };
}

function absoluteDecimal(value: string): string {
  const absolute = value.replace(/^-/, '');
  return formatDecimal(
    parseDecimal(absolute).coefficient,
    parseDecimal(absolute).scale,
  );
}
