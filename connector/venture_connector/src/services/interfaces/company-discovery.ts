import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';
import type { DataQuality } from '../../extraction/core/types.js';
import type {
  CompanyDiscoveryStatus,
  ErpCompanySummary,
} from '../../erp/ports/company-discovery.js';

export type CompanyListItem = ErpCompanySummary;

export interface CompanyListResult {
  readonly items: readonly CompanyListItem[];
  readonly schemaVersion: string;
  readonly dataFreshnessAt: string;
  readonly contractVersion: '1';
  readonly status: CompanyDiscoveryStatus;
  readonly tallyReachable: boolean;
  readonly dataQuality?: DataQuality;
  readonly reason?: string;
}

export interface CompanyDiscoveryService extends ServiceLifecycle {
  discoverCompanies(): Promise<CompanyListResult>;
  getStatus(): ServiceStatus;
}
