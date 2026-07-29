import type { EmbeddedTdlCollectionRequestSpec } from '../xml/request-builder.js';

export const PRODUCTION_VOUCHER_COLLECTION_NAME = 'Budcom Voucher Discovery' as const;
export const PRODUCTION_VOUCHER_DATE_FILTER_NAME = 'BudcomVoucherDateRange' as const;

export const PRODUCTION_VOUCHER_FETCH_METHODS = [
  'Date',
  'VoucherTypeName',
  'VoucherNumber',
  'Reference',
  'Narration',
  'PartyLedgerName',
  'Amount',
  'MasterID',
  'AlterID',
  'GUID',
  'IsCancelled',
  'AllLedgerEntries.LedgerName',
  'AllLedgerEntries.Amount',
  'AllLedgerEntries.IsDeemedPositive',
  'AllInventoryEntries.StockItemName',
  'AllInventoryEntries.ActualQty',
  'AllInventoryEntries.BilledQty',
  'AllInventoryEntries.Rate',
  'AllInventoryEntries.Amount',
] as const;

export interface VoucherCollectionRequestParams {
  readonly companyName: string;
  readonly dateFrom: string;
  readonly dateTo: string;
}

export function buildVoucherCollectionRequestSpec(
  params: VoucherCollectionRequestParams,
): EmbeddedTdlCollectionRequestSpec {
  requireValue(params.companyName, 'Voucher collection company');
  validateDateRange(params.dateFrom, params.dateTo);
  return {
    collection: {
      name: PRODUCTION_VOUCHER_COLLECTION_NAME,
      objectType: 'Voucher',
      fetch: PRODUCTION_VOUCHER_FETCH_METHODS,
      attributes: {
        ISFIXED: 'No',
        ISINITIALIZE: 'Yes',
      },
      filters: [PRODUCTION_VOUCHER_DATE_FILTER_NAME],
    },
    staticVariables: {
      SVEXPORTFORMAT: '$$SysName:XML',
      SVCURRENTCOMPANY: params.companyName,
      SVFROMDATE: params.dateFrom.replaceAll('-', ''),
      SVTODATE: params.dateTo.replaceAll('-', ''),
    },
    systemFormulae: {
      [PRODUCTION_VOUCHER_DATE_FILTER_NAME]:
        `$Date >= $$Date:"${toTallyDateLiteral(params.dateFrom)}" `
        + `AND $Date <= $$Date:"${toTallyDateLiteral(params.dateTo)}"`,
    },
  };
}

const MONTHS = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
] as const;

function toTallyDateLiteral(value: string): string {
  const [year, month, day] = value.split('-');
  return `${day}-${MONTHS[Number(month) - 1]}-${year}`;
}

function validateDateRange(dateFrom: string, dateTo: string): void {
  const pattern = /^\d{4}-\d{2}-\d{2}$/;
  const from = pattern.test(dateFrom) ? Date.parse(`${dateFrom}T00:00:00Z`) : Number.NaN;
  const to = pattern.test(dateTo) ? Date.parse(`${dateTo}T00:00:00Z`) : Number.NaN;
  if (!Number.isFinite(from) || !Number.isFinite(to)) {
    throw new Error('Voucher collection dates must use valid YYYY-MM-DD values.');
  }
  if (from > to) throw new Error('Voucher collection dateFrom must not follow dateTo.');
  if (Math.floor((to - from) / 86_400_000) + 1 > 366) {
    throw new Error('Voucher collection date range exceeds 366 days.');
  }
}

function requireValue(value: string, label: string): void {
  if (!value.trim()) throw new Error(`${label} is required.`);
}
