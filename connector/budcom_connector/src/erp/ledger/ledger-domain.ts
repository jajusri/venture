import type { NormalizedAmount } from '../../extraction/normalization/amounts.js';

export const LEDGER_DOMAIN_CONTRACT_VERSION = '1' as const;

export type LedgerStatus = 'active' | 'inactive' | 'reserved' | 'unknown';
export type LedgerSyncStatus =
  | 'idle'
  | 'running'
  | 'completed'
  | 'failed'
  | 'cancelled'
  | 'interrupted';

export type BalanceNature = 'debit' | 'credit' | 'unknown';

export interface LedgerMailingDetails {
  readonly mailingName?: string;
  readonly address?: string;
  readonly state?: string;
  readonly country?: string;
  readonly pincode?: string;
}

export interface LedgerContactDetails {
  readonly email?: string;
  readonly phone?: string;
  readonly mobile?: string;
}

export interface LedgerGstDetails {
  readonly gstin?: string;
  readonly registrationType?: string;
  readonly applicableFrom?: string;
}

/** ERP-neutral ledger summary for lists and search. */
export interface LedgerSummary {
  readonly id: string;
  readonly name: string;
  readonly normalizedName: string;
  readonly alias?: string;
  readonly parentGroup?: string;
  readonly status: LedgerStatus;
  readonly openingBalance?: NormalizedAmount;
  readonly closingBalance?: NormalizedAmount;
  readonly balanceNature: BalanceNature;
  readonly guid?: string;
  readonly alterId?: string;
  readonly isDeleted: boolean;
  readonly syncedAt: string;
}

/** ERP-neutral full ledger record. */
export interface LedgerDetails extends LedgerSummary {
  readonly reservedName?: string;
  readonly mailing?: LedgerMailingDetails;
  readonly contact?: LedgerContactDetails;
  readonly gst?: LedgerGstDetails;
  readonly metadata?: Record<string, string>;
}

export interface LedgerCollection {
  readonly items: readonly LedgerSummary[];
  readonly totalCount: number;
  readonly schemaVersion: typeof LEDGER_DOMAIN_CONTRACT_VERSION;
}

export interface LedgerChange {
  readonly ledgerId: string;
  readonly changeType: 'added' | 'updated' | 'skipped' | 'failed' | 'deleted';
  readonly reason?: string;
}

export interface LedgerStatistics {
  readonly totalLedgers: number;
  readonly activeLedgers: number;
  readonly inactiveLedgers: number;
  readonly reservedLedgers: number;
  readonly deletedLedgers: number;
  readonly withGst: number;
  readonly withOpeningBalance: number;
  readonly lastSyncedAt: string | null;
}

export interface LedgerSyncProgress {
  readonly status: LedgerSyncStatus;
  readonly startedAt: string | null;
  readonly completedAt: string | null;
  readonly durationMs: number | null;
  readonly itemsProcessed: number;
  readonly itemsAdded: number;
  readonly itemsUpdated: number;
  readonly itemsSkipped: number;
  readonly itemsFailed: number;
  readonly lastError: string | null;
  readonly cancelRequested: boolean;
}

export interface LedgerSyncResult {
  readonly status: LedgerSyncStatus;
  readonly statistics: LedgerStatistics;
  readonly progress: LedgerSyncProgress;
  readonly changes: readonly LedgerChange[];
  readonly validationIssueCount: number;
}

export interface LedgerSearchParams {
  readonly query?: string;
  readonly status?: LedgerStatus;
  readonly parentGroup?: string;
  readonly page: number;
  readonly pageSize: number;
  readonly sortBy?: 'name' | 'parentGroup' | 'closingBalance' | 'syncedAt';
  readonly sortDirection?: 'asc' | 'desc';
}

export interface LedgerSearchResult {
  readonly items: readonly LedgerSummary[];
  readonly pagination: {
    readonly page: number;
    readonly pageSize: number;
    readonly totalItems: number;
    readonly totalPages: number;
  };
}
