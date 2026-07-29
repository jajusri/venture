export const VOUCHER_CONTRACT_VERSION = '1.1' as const;
export const VOUCHER_IDENTITY_VERSION_UNPROVEN = 'unproven' as const;

export type VoucherDataQuality = 'complete' | 'incomplete';
export type VoucherStatus = 'active' | 'cancelled';
export type VoucherMoneySide = 'debit' | 'credit';
export type VoucherAllocationType =
  | 'accounting'
  | 'batch'
  | 'bank'
  | 'bill'
  | 'cost-track'
  | 'inventory';

/** Lossless, XML-neutral representation for allocation fields not yet interpreted. */
export interface VoucherStructuredValue {
  readonly name: string;
  readonly value?: string;
  readonly attributes: Readonly<Record<string, string>>;
  readonly children: readonly VoucherStructuredValue[];
}

export interface VoucherNestedAllocation {
  readonly type: VoucherAllocationType;
  readonly sourceName: string;
  readonly values: readonly VoucherStructuredValue[];
}

export interface VoucherMoney {
  readonly amount: string;
  readonly side: VoucherMoneySide | null;
}

export interface VoucherLedgerEntry {
  readonly lineNumber: number;
  readonly ledgerName: string;
  readonly amount: VoucherMoney;
  readonly isDeemedPositive: boolean | null;
  readonly allocations: readonly VoucherNestedAllocation[];
  readonly referenceType?: string;
  readonly referenceName?: string;
}

export interface VoucherInventoryEntry {
  readonly lineNumber: number;
  readonly itemName: string;
  readonly quantity?: string;
  readonly actualQuantity?: string;
  readonly billedQuantity?: string;
  readonly unit?: string;
  readonly rate?: string;
  readonly amount?: VoucherMoney;
  readonly allocations: readonly VoucherNestedAllocation[];
}

/** Source-neutral fields shared by list rows and details. */
export interface VoucherSummaryRecord {
  readonly voucherId: string;
  readonly identityVersion: string;
  readonly date: string;
  readonly voucherType: string;
  readonly voucherNumber: string | null;
  readonly partyName: string | null;
  readonly amount: VoucherMoney | null;
  readonly amountComparable: boolean;
  readonly status: VoucherStatus;
  readonly dataQuality: VoucherDataQuality;
  readonly referenceNumber?: string;
  readonly narrationPreview?: string;
}

export interface VoucherDetails extends VoucherSummaryRecord {
  readonly guid: string | null;
  readonly masterId: string | null;
  readonly alterId: string | null;
  readonly voucherKey: string | null;
  readonly voucherRetainKey: string | null;
  readonly effectiveDate?: string;
  readonly narration?: string;
  readonly ledgerEntries: readonly VoucherLedgerEntry[];
  readonly inventoryEntries: readonly VoucherInventoryEntry[];
  readonly allocations: readonly VoucherNestedAllocation[];
}

export interface VoucherTypeCount {
  readonly voucherType: string;
  readonly count: number;
}

export interface VoucherStatistics {
  readonly dateFrom: string;
  readonly dateTo: string;
  readonly totalVouchers: number;
  readonly countsByType: readonly VoucherTypeCount[];
  readonly lastSynchronizedAt: string | null;
  readonly incompleteVouchers?: number;
  readonly cancelledVouchers?: number;
}

export interface VoucherDateRange {
  readonly dateFrom: string;
  readonly dateTo: string;
}

export interface VoucherPage {
  readonly page: number;
  readonly pageSize: number;
  readonly totalItems: number;
  readonly totalPages: number;
}

export type VoucherSortField = 'date' | 'voucherNumber' | 'amount';
export type VoucherSortDirection = 'asc' | 'desc';

export interface VoucherSearchCriteria extends VoucherDateRange {
  readonly query?: string;
  readonly voucherType?: string;
  readonly status?: VoucherStatus;
  readonly page: number;
  readonly pageSize: number;
  readonly sortBy: VoucherSortField;
  readonly sortDirection: VoucherSortDirection;
}

export interface VoucherSearchResult {
  readonly items: readonly VoucherSummaryRecord[];
  readonly pagination: VoucherPage;
}
