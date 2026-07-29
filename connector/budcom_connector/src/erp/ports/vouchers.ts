import type {
  VoucherDateRange,
  VoucherDetails,
} from '../voucher/voucher-domain.js';
import type { VoucherValidationIssue } from '../voucher/voucher-validation.js';
import type { ErpReadOptions } from './erp-read-port.js';

export interface VoucherExtractionResult {
  readonly items: readonly VoucherDetails[];
  readonly candidateRecordCount: number;
  readonly droppedRecordCount: number;
  readonly validationIssues: readonly VoucherValidationIssue[];
  readonly responseStatus: 'records' | 'empty' | 'partial';
  readonly durationMs: number;
  readonly rawByteLength: number;
}

/** ERP-neutral, company- and period-scoped production Voucher read boundary. */
export interface VoucherReadPort {
  readVouchers(
    companyName: string,
    period: VoucherDateRange,
    options?: ErpReadOptions,
  ): Promise<VoucherExtractionResult>;
}
