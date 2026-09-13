import type {
  LedgerContactDetailsPatch,
  LedgerDetails,
  LedgerSearchParams,
  LedgerSearchResult,
  LedgerStatistics,
} from '../../erp/ledger/ledger-domain.js';

export interface LedgerRepositoryPort {
  upsertMany(companyId: string, ledgers: readonly LedgerDetails[]): Promise<void>;
  /**
   * Narrow partial update touching ONLY mailing/contact/gst columns -- never
   * name/alias/balances/status. Used by the manually-triggered bulk contact-details sync so it
   * can never clobber data the routine Ledgers sync owns. See `upsertMany`'s own COALESCE
   * comment for why that method alone isn't safe to reuse for this purpose.
   */
  updateContactDetailsMany(
    companyId: string,
    patches: readonly LedgerContactDetailsPatch[],
  ): Promise<{ updated: number; skipped: number }>;
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
  hasLegacyLedgerIds(companyId: string): Promise<boolean>;
  getLedgerIdentityVersion(companyId: string): Promise<number>;
  replaceCompanyLedgersAtomically(companyId: string, ledgers: readonly LedgerDetails[]): Promise<void>;
  markLedgerIdentityCurrent(companyId: string): Promise<void>;
}
