import type { NormalizedAmount, AmountSide } from '../../src/extraction/normalization/amounts.js';
import type { NormalizedStockItem } from '../../src/extraction/core/types.js';
import type { StockItemDetails } from '../../src/erp/stock-item/stock-item-domain.js';

export function sampleNormalizedAmount(value: string, side: AmountSide = 'Dr'): NormalizedAmount {
  return { amount: value, currencyCode: 'INR', side };
}

export function sampleNormalizedStockItem(
  overrides: Partial<NormalizedStockItem> = {},
): NormalizedStockItem {
  return {
    id: 'item-a',
    name: 'Item A',
    normalizedName: 'item a',
    parentGroup: 'Finished Goods',
    category: 'General',
    baseUnit: 'Nos',
    ...overrides,
  };
}

export function sampleStockItemDetails(overrides: Partial<StockItemDetails> = {}): StockItemDetails {
  return {
    id: 'name:item-a',
    name: 'Item A',
    normalizedName: 'item a',
    parentGroup: 'Finished Goods',
    category: 'General',
    baseUnit: 'Nos',
    dataQuality: 'complete',
    status: 'active',
    sourceSystem: 'tally',
    isDeleted: false,
    syncedAt: '2026-01-01T00:00:00.000Z',
    ...overrides,
  };
}
