import type { VoucherInventoryExtractionEntry } from '../../erp/voucher/voucher-inventory-domain.js';
import type {
  VoucherDetails,
  VoucherInventoryEntry,
  VoucherMoney,
} from '../../erp/voucher/voucher-domain.js';
import { normalizeGuid } from './voucher-ledger-parser.js';

export function joinVoucherInventories(
  vouchers: readonly VoucherDetails[],
  extractedEntries: readonly VoucherInventoryExtractionEntry[],
): readonly VoucherDetails[] {
  const vouchersByGuid = new Map<string, VoucherDetails>();
  for (const voucher of vouchers) {
    if (!voucher.guid) continue;
    const guid = normalizeGuid(voucher.guid);
    if (vouchersByGuid.has(guid)) throw new Error(`Duplicate Voucher GUID: ${guid}.`);
    vouchersByGuid.set(guid, voucher);
  }

  const grouped = new Map<string, VoucherInventoryExtractionEntry[]>();
  for (const entry of extractedEntries) {
    if (!vouchersByGuid.has(entry.parentGuid)) {
      throw new Error(`Voucher inventory entry references unknown parent GUID: ${entry.parentGuid}.`);
    }
    const entries = grouped.get(entry.parentGuid) ?? [];
    entries.push(entry);
    grouped.set(entry.parentGuid, entries);
  }

  return vouchers.map((voucher) => {
    if (!voucher.guid) {
      if (extractedEntries.length > 0) {
        throw new Error(`Voucher ${voucher.voucherId} cannot join inventory entries without GUID.`);
      }
      return voucher;
    }
    const entries = grouped.get(normalizeGuid(voucher.guid)) ?? [];
    return {
      ...voucher,
      inventoryEntries: entries.map(toVoucherInventoryEntry),
    };
  });
}

function toVoucherInventoryEntry(
  entry: VoucherInventoryExtractionEntry,
  index: number,
): VoucherInventoryEntry {
  return {
    lineNumber: index + 1,
    itemName: entry.stockItemName,
    ...(entry.billedQuantity || entry.actualQuantity
      ? { quantity: entry.billedQuantity ?? entry.actualQuantity }
      : {}),
    ...(entry.actualQuantity ? { actualQuantity: entry.actualQuantity } : {}),
    ...(entry.billedQuantity ? { billedQuantity: entry.billedQuantity } : {}),
    ...(entry.rate ? { rate: entry.rate } : {}),
    amount: toMoney(entry.signedAmount),
    allocations: [],
  };
}

function toMoney(signedAmount: string): VoucherMoney {
  const comparable = signedAmount.replaceAll(',', '');
  const negative = comparable.startsWith('-');
  const unsigned = negative ? comparable.slice(1) : comparable;
  return {
    amount: trimDecimal(unsigned),
    side: negative ? 'credit' : 'debit',
  };
}

function trimDecimal(value: string): string {
  if (!value.includes('.')) return value;
  return value.replace(/0+$/, '').replace(/\.$/, '') || '0';
}
