import type {
  VoucherDateRange,
  VoucherDetails,
  VoucherInventoryEntry,
  VoucherLedgerEntry,
  VoucherNestedAllocation,
  VoucherSearchCriteria,
  VoucherSearchResult,
  VoucherStatistics,
} from '../../erp/voucher/voucher-domain.js';

/** Read operations always require explicit company scope. */
export interface CompanyScopedVoucherReader {
  findById(companyId: string, voucherId: string): Promise<VoucherDetails | null>;
  search(companyId: string, criteria: VoucherSearchCriteria): Promise<VoucherSearchResult>;
  getStatistics(companyId: string, period: VoucherDateRange): Promise<VoucherStatistics>;
}

/**
 * Staging and promotion are separate so a future implementation cannot mutate
 * the live snapshot before complete-export validation succeeds.
 */
export interface CompanyScopedVoucherSnapshotWriter {
  createSnapshot(companyId: string, snapshotId: string, period: VoucherDateRange): Promise<void>;
  writeVoucherBatch(
    companyId: string,
    snapshotId: string,
    vouchers: readonly VoucherDetails[],
  ): Promise<void>;
  finalizeSnapshot(companyId: string, snapshotId: string, validatedAt: string): Promise<void>;
  rollbackSnapshot(companyId: string, snapshotId: string): Promise<void>;
  deleteSnapshot(companyId: string, snapshotId: string): Promise<void>;
  acquireSyncReservation(
    companyId: string,
    ownerId: string,
    acquiredAt: string,
    expiresAt: string,
  ): Promise<boolean>;
  releaseSyncReservation(companyId: string, ownerId: string): Promise<void>;
  beginSnapshot(companyId: string, snapshotId: string, period: VoucherDateRange): Promise<void>;
  storeVoucher(companyId: string, snapshotId: string, voucher: VoucherDetails): Promise<void>;
  storeLedgerEntries(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    entries: readonly VoucherLedgerEntry[],
  ): Promise<void>;
  storeInventoryEntries(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    entries: readonly VoucherInventoryEntry[],
  ): Promise<void>;
  storeAllocations(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    owner: VoucherAllocationOwner,
    allocations: readonly VoucherNestedAllocation[],
  ): Promise<void>;
  completeSnapshot(companyId: string, snapshotId: string, completedAt: string): Promise<void>;
  promoteSnapshot(companyId: string, snapshotId: string, promotedAt: string): Promise<void>;
  discardSnapshot(companyId: string, snapshotId: string, reason?: string): Promise<void>;
  querySnapshot(companyId: string, snapshotId?: string): Promise<readonly VoucherDetails[]>;
  getActiveSnapshotMetadata(companyId: string): Promise<VoucherSnapshotMetadata | null>;
  getSnapshotMetrics(companyId: string, snapshotId: string): Promise<VoucherSnapshotMetrics>;
  failStaleSnapshots(companyId: string, reason: string): Promise<number>;
  /**
   * Copies vouchers (and their ledger/inventory/allocation rows) from `fromSnapshotId` into the
   * still-writable `toSnapshotId`, restricted to `voucher_date` OUTSIDE [excludeDateFrom,
   * excludeDateTo] and excluding any voucherId already staged in `toSnapshotId` (so a freshly
   * re-extracted voucher whose date moved into the refreshed window always wins over a stale
   * carried-forward copy of the same identity). Enables windowed refresh: a sync of one bounded
   * window re-verifies that window live from Tally while carrying forward everything outside it
   * unchanged, so the resulting snapshot is always a complete, authoritative replacement — never
   * a partial one — without requiring the whole company history to be re-extracted every time.
   */
  carryForwardVouchersOutsideWindow(
    companyId: string,
    fromSnapshotId: string,
    toSnapshotId: string,
    excludeDateFrom: string,
    excludeDateTo: string,
  ): Promise<VoucherSnapshotMetrics>;

  /** Compatibility aliases for the pre-persistence synchronization contract. */
  beginStaging(companyId: string, syncRunId: string, period: VoucherDateRange): Promise<void>;
  stageMany(companyId: string, syncRunId: string, vouchers: readonly VoucherDetails[]): Promise<void>;
  discardStaging(companyId: string, syncRunId: string): Promise<void>;
  promoteCompleteSnapshot(companyId: string, syncRunId: string, completedAt: string): Promise<void>;
}

export interface VoucherSnapshotMetadata {
  readonly snapshotId: string;
  readonly period: VoucherDateRange;
  readonly status: VoucherSnapshotStatus;
  readonly createdAt?: string;
  readonly validatedAt?: string | null;
  readonly promotedAt?: string | null;
  readonly voucherCount?: number;
}

export type VoucherSnapshotStatus =
  | 'Pending'
  | 'Writing'
  | 'Validated'
  | 'Promoted'
  | 'Archived'
  | 'Failed';

export interface VoucherSnapshotReader {
  getSnapshot(companyId: string, snapshotId: string): Promise<VoucherSnapshotMetadata | null>;
  getVoucher(companyId: string, voucherId: string): Promise<VoucherDetails | null>;
  searchVouchers(
    companyId: string,
    criteria: VoucherSearchCriteria,
  ): Promise<VoucherSearchResult>;
  listSnapshots(companyId: string): Promise<readonly VoucherSnapshotMetadata[]>;
}

export interface VoucherSnapshotMetrics {
  readonly voucherCount: number;
  readonly ledgerEntryCount: number;
  readonly inventoryEntryCount: number;
  readonly allocationCount: number;
}

export interface VoucherAllocationOwner {
  readonly type: 'VOUCHER' | 'LEDGER' | 'INVENTORY';
  readonly lineNumber?: number;
}

export interface VoucherRepositoryPort
  extends CompanyScopedVoucherReader,
    CompanyScopedVoucherSnapshotWriter,
    VoucherSnapshotReader {}
