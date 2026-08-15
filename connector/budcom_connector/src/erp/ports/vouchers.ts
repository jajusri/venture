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
  /** Count of XML-1.0-illegal characters sanitized out of the raw response(s) before
   * parsing (e.g. Tally's `&#4;` export artifact, TD-001) -- telemetry only, never the
   * removed characters' surrounding content. Zero when nothing was sanitized. */
  readonly illegalCharactersSanitized: number;
}

/** ERP-neutral, company- and period-scoped production Voucher read boundary. */
export interface VoucherReadPort {
  readVouchers(
    companyName: string,
    period: VoucherDateRange,
    options?: ErpReadOptions,
  ): Promise<VoucherExtractionResult>;
}
