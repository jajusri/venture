import type { NormalizedAmount } from '../../extraction/normalization/amounts.js';
import type { LedgerSyncProgress, LedgerSyncRunRecord, LedgerSyncStatus, StorageStatus } from '../ledger/ledger-domain.js';

export const STOCK_ITEM_DOMAIN_CONTRACT_VERSION = '1' as const;

export type StockItemDataQuality = 'complete' | 'incomplete';
export type StockItemStatus = 'active' | 'inactive' | 'unknown';
export type StockItemExtractionCompleteness = 'complete' | 'partial' | 'failed' | 'cancelled';
export type StockItemDeletionReconciliation = 'disabled';

/** ERP-neutral stock item summary for lists and search. */
export interface StockItemSummary {
  readonly id: string;
  readonly name: string;
  readonly normalizedName: string;
  readonly parentGroup?: string;
  readonly category?: string;
  readonly baseUnit?: string;
  readonly dataQuality: StockItemDataQuality;
  readonly openingBalance?: NormalizedAmount;
  readonly closingBalance?: NormalizedAmount;
  readonly hsnCode?: string;
  readonly gstRate?: string;
  readonly guid?: string;
  readonly alterId?: string;
  readonly alias?: string;
  readonly partNumber?: string;
  readonly status: StockItemStatus;
  readonly sourceSystem: string;
  readonly isDeleted: boolean;
  readonly syncedAt: string;
}

/** ERP-neutral full stock item record. */
export interface StockItemDetails extends StockItemSummary {
  readonly metadata?: Record<string, string>;
}

export interface StockItemChange {
  readonly stockItemId: string;
  readonly changeType: 'added' | 'updated' | 'skipped' | 'failed' | 'deleted';
  readonly reason?: string;
}

export interface StockItemStatistics {
  readonly totalStockItems: number;
  readonly withBaseUnit: number;
  readonly incompleteData: number;
  readonly withHsn: number;
  readonly withGst: number;
  readonly withOpeningBalance: number;
  readonly deletedStockItems: number;
  readonly lastSyncedAt: string | null;
}

export type StockItemSyncProgress = LedgerSyncProgress;
export type StockItemSyncStatus = LedgerSyncStatus;
export type StockItemSyncRunRecord = LedgerSyncRunRecord;

export interface StockItemSyncResult {
  readonly syncRunId: string;
  readonly status: StockItemSyncStatus;
  readonly extractionCompleteness: StockItemExtractionCompleteness;
  readonly deletionReconciliation: StockItemDeletionReconciliation;
  readonly statistics: StockItemStatistics;
  readonly progress: StockItemSyncProgress;
  readonly changes: readonly StockItemChange[];
  readonly validationIssueCount: number;
}

export interface StockItemSearchParams {
  readonly query?: string;
  readonly parentGroup?: string;
  readonly category?: string;
  readonly dataQuality?: StockItemDataQuality;
  readonly page: number;
  readonly pageSize: number;
  readonly sortBy?: 'name' | 'parentGroup' | 'category' | 'baseUnit' | 'syncedAt';
  readonly sortDirection?: 'asc' | 'desc';
}

export interface StockItemSearchResult {
  readonly items: readonly StockItemSummary[];
  readonly pagination: {
    readonly page: number;
    readonly pageSize: number;
    readonly totalItems: number;
    readonly totalPages: number;
  };
}

export type { StorageStatus };
