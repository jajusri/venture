import type { NormalizedAmount } from '../normalization/amounts.js';
import type { LedgerIdentitySource } from './ledger-identity.js';

export type NormalizedLedgerDataQuality = 'complete' | 'partial' | 'invalid';

export const MasterDataEntityType = {
  CompanyInfo: 'company-info',
  LedgerGroup: 'ledger-groups',
  Ledger: 'ledgers',
  StockGroup: 'stock-groups',
  StockCategory: 'stock-categories',
  StockItem: 'stock-items',
  Unit: 'units',
  Godown: 'godowns',
  CostCategory: 'cost-categories',
  CostCentre: 'cost-centres',
  VoucherType: 'voucher-types',
  GstRegistration: 'gst-registrations',
} as const;

export type MasterDataEntityType =
  (typeof MasterDataEntityType)[keyof typeof MasterDataEntityType];

export interface PaginationParams {
  readonly page: number;
  readonly pageSize: number;
}

export interface PaginationMeta {
  readonly page: number;
  readonly pageSize: number;
  readonly totalItems: number;
  readonly hasMore: boolean;
}

export type DataQualityStatus = 'COMPLETE' | 'INCOMPLETE' | 'EMPTY' | 'DRIFT';

export interface DataQuality {
  readonly status: DataQualityStatus;
  readonly reason?: string;
}

export interface PaginatedEnvelope<T> {
  readonly items: readonly T[];
  readonly pagination: PaginationMeta;
  readonly schemaVersion: string;
  readonly dataFreshnessAt: string;
  /**
   * Optional response-contract assessment. Present when a critical field or
   * whole entity could not be reconstructed from Tally's response so the client
   * never mistakes an incomplete result for a verified-complete one.
   */
  readonly dataQuality?: DataQuality;
}

export interface NormalizedCompanyInfo {
  readonly id: string;
  readonly name: string;
  readonly mailingName?: string;
  readonly financialYearFrom?: string;
  readonly booksFrom?: string;
  readonly baseCurrency: string;
  readonly address?: string;
  readonly state?: string;
  readonly country?: string;
  readonly pincode?: string;
  readonly email?: string;
  readonly phone?: string;
  readonly gstin?: string;
}

export interface NormalizedLedgerGroup {
  readonly id: string;
  readonly name: string;
  readonly parentName?: string;
  readonly isRevenue?: boolean;
  readonly isDebit?: boolean;
}

export interface NormalizedLedger {
  readonly id: string;
  readonly name: string;
  readonly normalizedName: string;
  readonly alias?: string;
  readonly parentGroup?: string;
  readonly openingBalance?: NormalizedAmount;
  readonly closingBalance?: NormalizedAmount;
  readonly balanceNature?: 'debit' | 'credit' | 'unknown';
  readonly status?: 'active' | 'inactive' | 'reserved' | 'unknown';
  readonly guid?: string;
  readonly alterId?: string;
  readonly masterId?: string;
  readonly identitySource?: LedgerIdentitySource;
  readonly dataQuality?: NormalizedLedgerDataQuality;
  readonly isBillWiseOn?: boolean;
  readonly reservedName?: string;
  readonly mailingName?: string;
  readonly address?: string;
  readonly state?: string;
  readonly country?: string;
  readonly pincode?: string;
  readonly email?: string;
  readonly phone?: string;
  readonly mobile?: string;
  readonly gstin?: string;
  readonly gstRegistrationType?: string;
  readonly gstApplicableFrom?: string;
}

export interface NormalizedStockGroup {
  readonly id: string;
  readonly name: string;
  readonly parentName?: string;
}

export interface NormalizedStockCategory {
  readonly id: string;
  readonly name: string;
}

export interface NormalizedStockItem {
  readonly id: string;
  readonly name: string;
  readonly normalizedName: string;
  readonly parentGroup?: string;
  readonly category?: string;
  readonly baseUnit?: string;
  readonly openingBalance?: NormalizedAmount;
  readonly closingBalance?: NormalizedAmount;
  readonly hsnCode?: string;
  readonly gstRate?: string;
  readonly guid?: string;
  readonly alterId?: string;
  readonly alias?: string;
  readonly partNumber?: string;
  readonly status?: 'active' | 'inactive' | 'unknown';
}

export interface NormalizedUnit {
  readonly id: string;
  readonly name: string;
  readonly symbol?: string;
  readonly decimalPlaces?: number;
}

export interface NormalizedGodown {
  readonly id: string;
  readonly name: string;
  readonly parentName?: string;
  readonly address?: string;
}

export interface NormalizedCostCategory {
  readonly id: string;
  readonly name: string;
  readonly allocateRevenue?: boolean;
  readonly allocateNonRevenue?: boolean;
}

export interface NormalizedCostCentre {
  readonly id: string;
  readonly name: string;
  readonly parentName?: string;
  readonly category?: string;
}

export interface NormalizedVoucherType {
  readonly id: string;
  readonly name: string;
  readonly parentName?: string;
  readonly numberingMethod?: string;
}

export interface NormalizedGstRegistration {
  readonly id: string;
  readonly name: string;
  readonly gstin?: string;
  readonly state?: string;
  readonly registrationType?: string;
  readonly applicableFrom?: string;
}

export interface ExtractorDiagnostics {
  readonly entityType: MasterDataEntityType;
  readonly lastExtractedAt?: string;
  readonly lastDurationMs?: number;
  readonly lastItemCount?: number;
  readonly lastErrorAt?: string;
  readonly lastErrorMessage?: string;
  readonly totalExtractions: number;
  readonly failedExtractions: number;
}

export interface MasterDataContractSummary {
  readonly contractVersion: string;
  readonly status: string;
  readonly reasonCode?: string;
  readonly blocking: boolean;
  readonly dataQualityStatus?: DataQualityStatus;
}

export interface ExtractionResult<T> {
  readonly items: readonly T[];
  readonly durationMs: number;
  readonly rawByteLength: number;
  readonly contract?: MasterDataContractSummary;
  readonly extractionMetrics?: {
    readonly candidateNodeCount: number;
    readonly mappedRecordCount: number;
    readonly droppedRecordCount: number;
    readonly missingIdentityCount: number;
    readonly duplicateIdentityCount: number;
    readonly conflictingIdentityCount: number;
    readonly collectionPresent: boolean;
    readonly placeholderOnlyCollection: boolean;
  };
}
