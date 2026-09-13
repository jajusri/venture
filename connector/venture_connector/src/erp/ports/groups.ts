import type { DataQuality } from '../../extraction/core/types.js';

/** ERP-neutral accounting groups contract version. */
export const GROUPS_CONTRACT_VERSION = '1' as const;

/** Explicit outcome of a groups extraction attempt. */
export type GroupExtractionStatus =
  | 'SUCCESS'
  | 'EMPTY'
  | 'INCOMPLETE'
  | 'MALFORMED'
  | 'UNAVAILABLE'
  | 'DENIED'
  | 'TIMEOUT'
  | 'COMPANY_UNAVAILABLE';

/** Hierarchy integrity issue detected during validation — never silently repaired. */
export type HierarchyIssueKind =
  | 'MISSING_PARENT'
  | 'SELF_PARENT'
  | 'CYCLE'
  | 'DUPLICATE_ID'
  | 'DUPLICATE_NAME'
  | 'EMPTY_NAME'
  | 'AMBIGUOUS_PARENT';

export interface HierarchyIssue {
  readonly kind: HierarchyIssueKind;
  readonly groupId?: string;
  readonly groupName?: string;
  readonly parentName?: string;
  readonly reason: string;
}

/**
 * ERP-neutral accounting group summary.
 * Aligns with {@link NormalizedLedgerGroup} but adds hierarchy metadata.
 */
export interface ErpGroupSummary {
  readonly id: string;
  readonly name: string;
  readonly parentName?: string;
  readonly parentStableId?: string;
  readonly isPrimary: boolean;
  readonly isRevenue?: boolean;
  readonly isDebit?: boolean;
  /** Set only when Tally provides reliable built-in/reserved evidence. */
  readonly reservedName?: string;
}

/** ERP-neutral result of a groups read through the adapter. */
export interface ErpGroupsResult {
  readonly contractVersion: typeof GROUPS_CONTRACT_VERSION;
  readonly status: GroupExtractionStatus;
  readonly tallyReachable: boolean;
  readonly items: readonly ErpGroupSummary[];
  readonly dataQuality?: DataQuality;
  readonly reason?: string;
  readonly hierarchyIssues?: readonly HierarchyIssue[];
  readonly durationMs?: number;
}
