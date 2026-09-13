import type {
  StockItemDetails,
  StockItemSearchParams,
  StockItemSearchResult,
  StockItemStatistics,
} from '../../erp/stock-item/stock-item-domain.js';

export interface StockItemRepositoryPort {
  upsertMany(companyId: string, items: readonly StockItemDetails[]): Promise<void>;
  insert(companyId: string, item: StockItemDetails): Promise<void>;
  update(companyId: string, item: StockItemDetails): Promise<void>;
  softDelete(companyId: string, stockItemId: string): Promise<boolean>;
  delete(companyId: string, stockItemId: string): Promise<boolean>;
  findById(companyId: string, stockItemId: string): Promise<StockItemDetails | null>;
  findByName(companyId: string, name: string): Promise<StockItemDetails | null>;
  search(companyId: string, params: StockItemSearchParams): Promise<StockItemSearchResult>;
  getStatistics(companyId: string): Promise<StockItemStatistics>;
  clearCompany(companyId: string): Promise<void>;
  countByCompany(companyId: string): Promise<number>;
}
