import type {
  VoucherDateRange,
  VoucherSearchCriteria,
} from '../../erp/voucher/voucher-domain.js';
import type { VoucherSnapshotMetadata } from './voucher-repository.interface.js';

export interface VoucherSummaryQueryDto {
  readonly companyId: string;
  readonly period: VoucherDateRange;
  readonly voucherType?: string;
}

export interface VoucherListQueryDto {
  readonly companyId: string;
  readonly criteria: VoucherSearchCriteria;
  readonly voucherNumber?: string;
  readonly partyName?: string;
}

export interface VoucherDetailsQueryDto {
  readonly companyId: string;
  readonly voucherId: string;
}

export interface VoucherSummaryDto {
  readonly companyId: string;
  readonly statistics: {
    readonly dateFrom: string;
    readonly dateTo: string;
    readonly totalVouchers: number;
    readonly countsByType: readonly { readonly voucherType: string; readonly count: number }[];
    readonly lastSynchronizedAt: string | null;
  };
}

export interface VoucherListDto {
  readonly companyId: string;
  readonly items: readonly VoucherPublicRecord[];
  readonly pagination: {
    readonly page: number;
    readonly pageSize: number;
    readonly totalItems: number;
    readonly totalPages: number;
  };
}

export interface VoucherDetailsDto {
  readonly companyId: string;
  readonly voucher: VoucherPublicDetails | null;
}

export interface VoucherSnapshotListDto {
  readonly companyId: string;
  readonly snapshots: readonly VoucherSnapshotDto[];
}

export interface VoucherSnapshotDetailsDto {
  readonly companyId: string;
  readonly snapshot: VoucherSnapshotDto | null;
}

export interface VoucherPublicRecord {
  readonly id: string;
  readonly date: string;
  readonly type: string;
  readonly number: string | null;
  readonly partyName: string | null;
  readonly referenceNumber: string | null;
  readonly amount: { readonly value: string; readonly side: 'debit' | 'credit' | null } | null;
  readonly status: 'active' | 'cancelled';
  readonly dataQuality: 'complete' | 'incomplete';
}

export interface VoucherPublicDetails extends VoucherPublicRecord {
  readonly effectiveDate: string | null;
  readonly narration: string | null;
  readonly ledgerEntries: readonly {
    readonly lineNumber: number;
    readonly ledgerName: string;
    readonly amount: { readonly value: string; readonly side: 'debit' | 'credit' | null };
    readonly isDeemedPositive: boolean | null;
  }[];
  readonly inventoryEntries: readonly {
    readonly lineNumber: number;
    readonly itemName: string;
    readonly quantity: string | null;
    readonly rate: string | null;
    readonly amount: { readonly value: string; readonly side: 'debit' | 'credit' | null } | null;
  }[];
}

export interface VoucherSnapshotDto {
  readonly id: string;
  readonly from: string;
  readonly to: string;
  readonly status: VoucherSnapshotMetadata['status'];
  readonly createdAt: string | null;
  readonly validatedAt: string | null;
  readonly promotedAt: string | null;
  readonly voucherCount: number;
}
