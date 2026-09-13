import { TallyCapability } from '../security/capabilities.js';
import { PRODUCTION_VOUCHER_COLLECTION_NAME } from '../voucher/voucher-request.js';

export const VOUCHER_DISCOVERY_OPERATION_ID = 'VOUCHER_DAY_BOOK_DISCOVERY_CANDIDATE' as const;
export const VOUCHER_DISCOVERY_COLLECTION_NAME = PRODUCTION_VOUCHER_COLLECTION_NAME;

export const VOUCHER_DISCOVERY_OPERATION = Object.freeze({
  operationId: VOUCHER_DISCOVERY_OPERATION_ID,
  provisionalTallyId: VOUCHER_DISCOVERY_COLLECTION_NAME,
  capability: TallyCapability.ReportRead,
  classification: 'EXPERIMENTAL_DISABLED' as const,
  rolloutStatus: 'disabled' as const,
  requiresCompany: true,
  requiresExplicitDateRange: true,
  maximumRangeDays: 366,
  productionGatewayAllowed: false,
  evidenceStatus: 'unverified' as const,
});

export interface VoucherDiscoveryRequestShape {
  readonly operationId: typeof VOUCHER_DISCOVERY_OPERATION_ID;
  readonly companyName: string;
  readonly dateFrom: string;
  readonly dateTo: string;
}

export function validateVoucherDiscoveryRequestShape(
  value: Partial<VoucherDiscoveryRequestShape>,
): readonly string[] {
  const errors: string[] = [];
  if (value.operationId !== VOUCHER_DISCOVERY_OPERATION_ID) errors.push('Unknown discovery operation.');
  if (!value.companyName?.trim()) errors.push('Explicit fixture company is required.');
  if (!value.dateFrom) errors.push('dateFrom is required.');
  if (!value.dateTo) errors.push('dateTo is required.');
  if (!value.dateFrom || !value.dateTo) return errors;

  const isoDate = /^\d{4}-\d{2}-\d{2}$/;
  const from = isoDate.test(value.dateFrom) ? Date.parse(`${value.dateFrom}T00:00:00Z`) : Number.NaN;
  const to = isoDate.test(value.dateTo) ? Date.parse(`${value.dateTo}T00:00:00Z`) : Number.NaN;
  if (!Number.isFinite(from) || !Number.isFinite(to)) {
    errors.push('Dates must use valid YYYY-MM-DD values.');
  } else if (from > to) {
    errors.push('dateFrom must not follow dateTo.');
  } else if (Math.floor((to - from) / 86_400_000) + 1 > VOUCHER_DISCOVERY_OPERATION.maximumRangeDays) {
    errors.push('Date range exceeds one financial year.');
  }
  return errors;
}
