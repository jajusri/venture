import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export interface CompanyListItem {
  readonly id: string;
  readonly name: string;
  readonly financialYear: string;
  readonly baseCurrency: string;
}

export interface CompanyListResult {
  readonly items: readonly CompanyListItem[];
  readonly schemaVersion: string;
  readonly dataFreshnessAt: string;
}

export interface CompanyDiscoveryService extends ServiceLifecycle {
  discoverCompanies(): Promise<CompanyListResult>;
  getStatus(): ServiceStatus;
}
