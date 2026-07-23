import type {
  LedgerDetails,
  LedgerSearchParams,
  LedgerSearchResult,
  LedgerStatistics,
} from '../../erp/ledger/ledger-domain.js';

export interface LedgerRepositoryPort {
  upsertMany(companyId: string, ledgers: readonly LedgerDetails[]): Promise<void>;
  insert(companyId: string, ledger: LedgerDetails): Promise<void>;
  update(companyId: string, ledger: LedgerDetails): Promise<void>;
  softDelete(companyId: string, ledgerId: string): Promise<boolean>;
  delete(companyId: string, ledgerId: string): Promise<boolean>;
  findById(companyId: string, ledgerId: string): Promise<LedgerDetails | null>;
  findByGuid(companyId: string, guid: string): Promise<LedgerDetails | null>;
  findByName(companyId: string, name: string): Promise<LedgerDetails | null>;
  findByAlias(companyId: string, alias: string): Promise<LedgerDetails | null>;
  search(companyId: string, params: LedgerSearchParams): Promise<LedgerSearchResult>;
  getStatistics(companyId: string): Promise<LedgerStatistics>;
  clearCompany(companyId: string): Promise<void>;
  countByCompany(companyId: string): Promise<number>;
}
