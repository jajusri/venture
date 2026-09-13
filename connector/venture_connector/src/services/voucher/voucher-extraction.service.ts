import type {
  VoucherExtractionResult,
  VoucherReadPort,
} from '../../erp/ports/vouchers.js';
import type { VoucherDateRange } from '../../erp/voucher/voucher-domain.js';

export interface VoucherExtractionRequest {
  readonly companyName: string;
  readonly period: VoucherDateRange;
  readonly signal?: AbortSignal;
}

/**
 * Production application seam for read-only extraction. It deliberately does
 * not stage, persist, reconcile, or synchronize records.
 */
export class VoucherExtractionService {
  constructor(private readonly source: VoucherReadPort) {}

  extract(
    request: VoucherExtractionRequest,
  ): Promise<VoucherExtractionResult> {
    return request.signal
      ? this.source.readVouchers(request.companyName, request.period, { signal: request.signal })
      : this.source.readVouchers(request.companyName, request.period);
  }
}
