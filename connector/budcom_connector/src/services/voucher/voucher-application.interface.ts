import type {
  VoucherDetailsDto,
  VoucherDetailsQueryDto,
  VoucherListDto,
  VoucherListQueryDto,
  VoucherSummaryDto,
  VoucherSummaryQueryDto,
  VoucherSnapshotDetailsDto,
  VoucherSnapshotListDto,
} from './voucher-dtos.js';
import type {
  VoucherSyncCancellation,
  VoucherSyncProgressObserver,
  VoucherSynchronizationResult,
} from './voucher-sync-progress.js';

export interface VoucherApplicationService {
  getSummary(query: VoucherSummaryQueryDto): Promise<VoucherSummaryDto>;
  list(query: VoucherListQueryDto): Promise<VoucherListDto>;
  getDetails(query: VoucherDetailsQueryDto): Promise<VoucherDetailsDto>;
  listSnapshots(companyId: string): Promise<VoucherSnapshotListDto>;
  getSnapshot(companyId: string, snapshotId: string): Promise<VoucherSnapshotDetailsDto>;
}

export type VoucherQueryService = VoucherApplicationService;

export interface VoucherSnapshotSyncRequest {
  readonly companyId: string;
  readonly dateFrom: string;
  readonly dateTo: string;
}

/**
 * Internal application boundary. It is registered in production composition
 * but deliberately absent from API, IPC, and scheduler surfaces.
 */
export interface VoucherSnapshotSyncService {
  synchronize(
    request: VoucherSnapshotSyncRequest,
    observer: VoucherSyncProgressObserver | readonly VoucherSyncProgressObserver[],
    cancellation: VoucherSyncCancellation,
  ): Promise<VoucherSynchronizationResult>;
}
