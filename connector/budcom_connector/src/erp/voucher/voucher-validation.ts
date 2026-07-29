export const VOUCHER_VALIDATION_LIMITS = Object.freeze({
  searchQuery: 128,
  voucherNumber: 128,
  voucherType: 128,
  partyName: 256,
  reference: 256,
  narrationPreview: 160,
  defaultPageSize: 25,
  maximumPageSize: 100,
});

export type VoucherValidationIssueCode =
  | 'MISSING_STABLE_IDENTITY'
  | 'UNSUPPORTED_VOUCHER_TYPE'
  | 'INVALID_DATE'
  | 'MISSING_TYPE_REQUIRED_FIELD'
  | 'INVALID_REQUIRED_CHILD_STRUCTURE'
  | 'INVALID_AMOUNT'
  | 'INCONSISTENT_QUANTITIES'
  | 'MISSING_LEDGER_NAME'
  | 'DUPLICATE_IDENTITY_CONFLICT'
  | 'VALUE_LIMIT_EXCEEDED'
  | 'BLOCKING_CONTRACT_CONFLICT';

export interface VoucherValidationIssue {
  readonly code: VoucherValidationIssueCode;
  readonly field: string;
  readonly classification: 'rejected' | 'incomplete' | 'contract-conflict';
  readonly message: string;
}

export interface VoucherRuntimeValidationResult<T> {
  readonly accepted: boolean;
  readonly value: T | null;
  readonly issues: readonly VoucherValidationIssue[];
}

/**
 * The full narration limit is intentionally absent until its bounded value is
 * approved. Consumers must not substitute an inferred or implementation-local limit.
 */
export type ApprovedVoucherNarrationLimit = {
  readonly maximumCharacters: number;
  readonly approvalReference: string;
};
