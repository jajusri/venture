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
  /** Granular, privacy-safe diagnostic subtype (a fixed reasonCode string, e.g.
   * 'malformed-xml', 'missing-envelope') distinguishing what `failureReason`'s coarse
   * external bucket (e.g. 'parser_failure') alone collapses away. Never raw error text
   * or business content. Null when not applicable or not available. */
  readonly failureDetail: string | null;
  readonly notificationFailureCount: number;
  /** Count of XML-1.0-illegal characters sanitized out of the raw Tally response before
   * parsing (e.g. the TD-001 `&#4;` export artifact) -- telemetry only, never the
   * removed characters' surrounding content. Zero when nothing was sanitized. */
  readonly illegalCharactersSanitized: number;
  /** Count of Voucher ledger entries where IsDeemedPositive disagreed with the signed
   * Amount's sign -- tolerated, not fatal. Telemetry only, never which voucher/ledger.
   * Zero when no entry disagreed. */
  readonly amountSignConflictCount: number;
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
