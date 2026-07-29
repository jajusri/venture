import type { VoucherDateRange } from '../../erp/voucher/voucher-domain.js';

import type { VoucherValidationIssue } from '../../erp/voucher/voucher-validation.js';

export type VoucherSyncState =
  | 'validating'
  | 'extracting'
  | 'staging'
  | 'validating_snapshot'
  | 'promoting'
  | 'completed'
  | 'cancelled'
  | 'failed';

export interface VoucherSyncProgress {
  readonly snapshotId: string | null;
  readonly companyId: string;
  readonly period: VoucherDateRange;
  readonly phase: VoucherSyncState;
  readonly processed: number;
  readonly total: number | null;
}

export interface VoucherSyncProgressObserver {
  onProgress(progress: VoucherSyncProgress): void | Promise<void>;
}

export interface VoucherSyncCancellation {
  readonly requested: boolean;
  readonly signal?: AbortSignal;
}

export type VoucherSyncRollbackStatus = 'not_required' | 'completed' | 'failed';

export type VoucherSyncOutcome =
  | 'completed'
  | 'failed'
  | 'cancelled'
  | 'conflict'
  | 'already_current';

export interface VoucherSynchronizationResult {
  readonly company: string;
  readonly period: VoucherDateRange;
  readonly snapshotId: string | null;
  readonly outcome: VoucherSyncOutcome;
  readonly responseStatus: 'records' | 'empty' | 'partial' | null;
  readonly candidateVoucherCount: number;
  readonly acceptedVoucherCount: number;
  readonly rejectedVoucherCount: number;
  readonly incompleteVoucherCount: number;
  readonly ledgerEntryCount: number;
  readonly inventoryEntryCount: number;
  readonly allocationCount: number;
  readonly phaseDurationsMs: Readonly<Partial<Record<VoucherSyncState, number>>>;
  readonly validationIssues: readonly VoucherValidationIssue[];
  readonly repositoryFailureCode: string | null;
  readonly previousActiveSnapshotPreserved: boolean;
  readonly promotionOccurred: boolean;
  readonly failureReason: string | null;
  readonly notificationFailureCount: number;
  readonly startedAt: string;
  readonly finishedAt: string;
  readonly durationMs: number;
  readonly vouchersExtracted: number;
  readonly vouchersPersisted: number;
  readonly droppedVouchers: number;
  readonly validationWarningCount: number;
  readonly rollbackStatus: VoucherSyncRollbackStatus;
  readonly promoted: boolean;
}
