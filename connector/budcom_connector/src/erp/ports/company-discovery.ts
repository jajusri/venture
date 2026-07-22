import type { DataQuality } from '../../extraction/core/types.js';

/** ERP-neutral company discovery contract version. */
export const COMPANY_DISCOVERY_CONTRACT_VERSION = '1' as const;

/**
 * Explicit outcome of a company discovery attempt.
 *
 * Distinct from HTTP status — the adapter maps transport/policy failures into
 * these semantic statuses so business code never misreads an empty untrusted
 * response as success.
 */
export type CompanyDiscoveryStatus =
  | 'SUCCESS'
  | 'EMPTY'
  | 'INCOMPLETE'
  | 'MALFORMED'
  | 'UNAVAILABLE'
  | 'DENIED'
  | 'TIMEOUT';

/** Normalized company summary returned across the ERP read port boundary. */
export interface ErpCompanySummary {
  readonly id: string;
  readonly name: string;
  readonly financialYear?: string;
  readonly booksFrom?: string;
  readonly baseCurrency?: string;
}

/** ERP-neutral result of a company discovery read through the adapter. */
export interface ErpCompanyDiscoveryResult {
  readonly contractVersion: typeof COMPANY_DISCOVERY_CONTRACT_VERSION;
  readonly status: CompanyDiscoveryStatus;
  readonly tallyReachable: boolean;
  readonly items: readonly ErpCompanySummary[];
  readonly dataQuality?: DataQuality;
  readonly reason?: string;
}
