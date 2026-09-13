import { createHash } from 'node:crypto';

import type { StockItemDetails } from '../../erp/stock-item/stock-item-domain.js';
import { normalizeNumber } from '../../extraction/normalization/numbers.js';

function amountKey(amount: StockItemDetails['openingBalance']): string {
  if (!amount) return '';
  const normalized = normalizeNumber(amount.amount) ?? amount.amount;
  return `${normalized}|${amount.side ?? ''}`;
}

export function computeStockItemFingerprint(item: StockItemDetails): string {
  const payload = [
    item.name,
    item.normalizedName,
    item.parentGroup ?? '',
    item.category ?? '',
    item.baseUnit ?? '',
    item.dataQuality,
    amountKey(item.openingBalance),
    amountKey(item.closingBalance),
    item.guid ?? '',
    item.alterId ?? '',
    item.alias ?? '',
    item.partNumber ?? '',
    item.status,
    item.isDeleted ? '1' : '0',
  ].join('|');
  return createHash('sha256').update(payload).digest('hex');
}
