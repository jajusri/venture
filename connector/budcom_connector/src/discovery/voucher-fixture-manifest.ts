export const VOUCHER_GOVERNANCE_MODES = [
  'single-authorized-owner',
  'multi-reviewer',
] as const;

export type VoucherGovernanceMode = (typeof VOUCHER_GOVERNANCE_MODES)[number];

export const VOUCHER_APPROVAL_ROLES = [
  'product-owner',
  'tally-data-owner',
  'accounting-reviewer',
  'security-privacy-reviewer',
  'connector-engineering-owner',
] as const;

export type VoucherApprovalRole = (typeof VOUCHER_APPROVAL_ROLES)[number];
export type VoucherExperimentType = 'create' | 'edit' | 'cancel' | 'delete';

export interface VoucherAuthorizedOwner {
  readonly name: string;
  readonly role: string;
  readonly approvedAt: string;
  readonly approvalReference: string;
  readonly fixtureOnlyAcknowledged: boolean;
  readonly syntheticDataConfirmed: boolean;
  readonly backupConfirmed: boolean;
  readonly sanitizationApproved: boolean;
  readonly dateRangeApproved: boolean;
  readonly expectedCountsApproved: boolean;
  readonly executionAcknowledged: boolean;
}

export interface VoucherFixtureApproval {
  readonly role: VoucherApprovalRole;
  readonly approverName: string;
  readonly approved: boolean;
  readonly approvedAt: string | null;
  readonly reference: string | null;
}

export interface VoucherFixtureExpectation {
  readonly fixtureKey: string;
  readonly voucherType: string;
  readonly mandatory: boolean;
  readonly expectedStatus: 'active' | 'cancelled';
}

export interface VoucherMutationStep {
  readonly sequence: number;
  readonly fixtureKey: string;
  readonly experiment: VoucherExperimentType;
}

export interface VoucherDiscoveryManifest {
  readonly evidenceVersion: string;
  readonly governanceMode: VoucherGovernanceMode;
  readonly authorizedOwner?: VoucherAuthorizedOwner;
  readonly approvals?: readonly VoucherFixtureApproval[];
  readonly fixtureCompanyAlias: string;
  readonly nonProductionConfirmed: boolean;
  readonly approvedExperiments: readonly VoucherExperimentType[];
  readonly dateRange: { readonly dateFrom: string; readonly dateTo: string };
  readonly expectedVouchers: readonly VoucherFixtureExpectation[];
  readonly expectedCountsByType: Readonly<Record<string, number>>;
  readonly expectedActiveCount: number;
  readonly expectedCancelledCount: number;
  readonly mutationSequence: readonly VoucherMutationStep[];
  readonly draftValuesApprovalStatus: 'operator-provided-pending-owner-approval' | 'owner-approved';
  readonly sanitizationStatus: 'pending' | 'sanitized' | 'rejected';
  readonly reviewerSignOffStatus: 'pending' | 'approved' | 'rejected';
}

export interface ManifestValidationOptions {
  readonly productionCompanyNames?: readonly string[];
}

export interface ManifestValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly manifest: VoucherDiscoveryManifest | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonPlaceholderString(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    value.trim().length > 0 &&
    !value.includes('[') &&
    !/pending|required|placeholder/i.test(value)
  );
}

function isIsoTimestamp(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(value) &&
    Number.isFinite(Date.parse(value))
  );
}

function validateSingleOwner(value: Record<string, unknown>, errors: string[]): void {
  if (!isRecord(value.authorizedOwner)) {
    errors.push('authorizedOwner is required for single-authorized-owner governance.');
    return;
  }
  if (Array.isArray(value.approvals) && value.approvals.length > 0) {
    errors.push('Multi-reviewer approvals cannot be selected with single-owner governance.');
  }
  const owner = value.authorizedOwner;
  for (const field of ['name', 'role', 'approvalReference'] as const) {
    if (!isNonPlaceholderString(owner[field])) {
      errors.push(`authorizedOwner.${field} must be complete and non-placeholder.`);
    }
  }
  if (!isIsoTimestamp(owner.approvedAt)) {
    errors.push('authorizedOwner.approvedAt must be a valid dated UTC approval.');
  }
  for (const field of [
    'fixtureOnlyAcknowledged',
    'syntheticDataConfirmed',
    'backupConfirmed',
    'sanitizationApproved',
    'dateRangeApproved',
    'expectedCountsApproved',
    'executionAcknowledged',
  ] as const) {
    if (owner[field] !== true) errors.push(`authorizedOwner.${field} must be explicitly true.`);
  }
}

function validateMultiReviewer(value: Record<string, unknown>, errors: string[]): void {
  if (value.authorizedOwner !== undefined && value.authorizedOwner !== null) {
    errors.push('authorizedOwner cannot be selected with multi-reviewer governance.');
  }
  if (!Array.isArray(value.approvals)) {
    errors.push('approvals must be an array for multi-reviewer governance.');
    return;
  }
  for (const role of VOUCHER_APPROVAL_ROLES) {
    const approval = value.approvals.find(
      (candidate) => isRecord(candidate) && candidate.role === role,
    );
    if (
      !isRecord(approval) ||
      approval.approved !== true ||
      !isNonPlaceholderString(approval.approverName) ||
      !isIsoTimestamp(approval.approvedAt) ||
      !isNonPlaceholderString(approval.reference)
    ) {
      errors.push(`Complete dated approval is required for ${role}.`);
    }
  }
}

export function validateVoucherDiscoveryManifest(
  value: unknown,
  options: ManifestValidationOptions = {},
): ManifestValidationResult {
  const errors: string[] = [];
  if (!isRecord(value)) {
    return { valid: false, errors: ['Manifest must be an object.'], manifest: null };
  }
  for (const field of ['evidenceVersion', 'fixtureCompanyAlias'] as const) {
    if (!isNonPlaceholderString(value[field])) errors.push(`${field} must be complete and non-placeholder.`);
  }
  if (!VOUCHER_GOVERNANCE_MODES.includes(value.governanceMode as VoucherGovernanceMode)) {
    errors.push('Exactly one supported governanceMode must be selected.');
  } else if (value.governanceMode === 'single-authorized-owner') {
    validateSingleOwner(value, errors);
  } else {
    validateMultiReviewer(value, errors);
  }
  if (value.nonProductionConfirmed !== true) {
    errors.push('nonProductionConfirmed must be explicitly true.');
  }
  if (
    options.productionCompanyNames?.some(
      (company) =>
        typeof value.fixtureCompanyAlias === 'string' &&
        company.toLowerCase() === value.fixtureCompanyAlias.toLowerCase(),
    )
  ) {
    errors.push('Production companies are prohibited.');
  }
  if (!Array.isArray(value.approvedExperiments)) errors.push('approvedExperiments must be an array.');
  if (!isRecord(value.dateRange)) {
    errors.push('dateRange must be an object.');
  } else if (!isNonPlaceholderString(value.dateRange.dateFrom) || !isNonPlaceholderString(value.dateRange.dateTo)) {
    errors.push('dateRange must contain explicit non-placeholder dates.');
  }
  if (!Array.isArray(value.expectedVouchers)) errors.push('expectedVouchers must be an array.');
  if (!isRecord(value.expectedCountsByType)) errors.push('expectedCountsByType must be an object.');
  if (!Array.isArray(value.mutationSequence)) errors.push('mutationSequence must be an array.');
  if (!['operator-provided-pending-owner-approval', 'owner-approved'].includes(String(value.draftValuesApprovalStatus))) {
    errors.push('draftValuesApprovalStatus is invalid.');
  } else if (value.draftValuesApprovalStatus !== 'owner-approved') {
    errors.push('Operator-provided draft values require explicit owner approval.');
  }
  if (!['pending', 'sanitized', 'rejected'].includes(String(value.sanitizationStatus))) {
    errors.push('sanitizationStatus is invalid.');
  } else if (value.sanitizationStatus !== 'sanitized') {
    errors.push('Sanitization approval must be complete.');
  }
  if (!['pending', 'approved', 'rejected'].includes(String(value.reviewerSignOffStatus))) {
    errors.push('reviewerSignOffStatus is invalid.');
  } else if (value.reviewerSignOffStatus !== 'approved') {
    errors.push('Reviewer or authorized-owner sign-off must be approved.');
  }
  return {
    valid: errors.length === 0,
    errors,
    manifest: errors.length === 0 ? (value as unknown as VoucherDiscoveryManifest) : null,
  };
}

export function hasCompleteVoucherFixtureApprovals(manifest: VoucherDiscoveryManifest): boolean {
  return validateVoucherDiscoveryManifest(manifest).valid;
}
