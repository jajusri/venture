import type { NormalizedStockItem } from '../../extraction/core/types.js';
import type { StockItemDataQuality, StockItemDetails } from './stock-item-domain.js';
import { STOCK_ITEM_SOURCE_SYSTEM } from './stock-item-identity.js';
import { STOCK_ITEM_DOMAIN_CONTRACT_VERSION } from './stock-item-domain.js';

function resolveDataQuality(item: NormalizedStockItem): StockItemDataQuality {
  return item.baseUnit?.trim() ? 'complete' : 'incomplete';
}

/** Maps ERP-neutral extraction model into the persisted stock item domain record. */
export function mapNormalizedStockItemToDomain(
  item: NormalizedStockItem,
  syncedAt = new Date().toISOString(),
): StockItemDetails {
  const dataQuality = resolveDataQuality(item);
  return {
    id: item.id,
    name: item.name,
    normalizedName: item.normalizedName,
    parentGroup: item.parentGroup,
    category: item.category,
    baseUnit: item.baseUnit?.trim() || undefined,
    dataQuality,
    openingBalance: item.openingBalance,
    closingBalance: item.closingBalance,
    hsnCode: item.hsnCode,
    gstRate: item.gstRate,
    guid: item.guid,
    alterId: item.alterId,
    alias: item.alias,
    partNumber: item.partNumber,
    status: item.status ?? 'active',
    sourceSystem: STOCK_ITEM_SOURCE_SYSTEM,
    isDeleted: false,
    syncedAt,
    metadata: {
      contractVersion: STOCK_ITEM_DOMAIN_CONTRACT_VERSION,
      sourceSystem: STOCK_ITEM_SOURCE_SYSTEM,
      ...(dataQuality === 'incomplete' ? { incompleteReason: 'BASEUNITS not present in ERP export' } : {}),
    },
  };
}
