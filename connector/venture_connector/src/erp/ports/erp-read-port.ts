import type {
  ExtractionResult,
  ExtractorDiagnostics,
  MasterDataEntityType,
  NormalizedCompanyInfo,
  NormalizedCostCategory,
  NormalizedCostCentre,
  NormalizedGodown,
  NormalizedGstRegistration,
  NormalizedLedger,
  NormalizedLedgerGroup,
  NormalizedStockCategory,
  NormalizedStockGroup,
  NormalizedStockItem,
  NormalizedVoucherType,
} from '../../extraction/core/types.js';
import type { ErpCompanyDiscoveryResult } from './company-discovery.js';
import type { ErpGroupsResult } from './groups.js';

/**
 * ERP-neutral read boundary between business/application code and the connected
 * ERP adapter.
 *
 * Business code depends ONLY on this port. It never sees raw XML,
 * {@code ParsedXmlNode}, transport DTOs, or any Tally-specific type. The adapter
 * owns communication and parsing; it returns strongly typed Venture domain
 * models across this boundary. A future BUSY/SAP/Zoho adapter implements the
 * same port without changing any business code.
 */
export interface ErpReadOptions {
  readonly signal?: AbortSignal;
}

export interface ErpReadPort {
  isReady(): boolean;

  discoverCompanies(): Promise<ErpCompanyDiscoveryResult>;

  /**
   * Returns normalized accounting groups for the selected company with explicit
   * extraction status and hierarchy validation. Never exposes raw XML.
   */
  getGroups(companyName: string): Promise<ErpGroupsResult>;

  /**
   * Returns normalized company info, or undefined when it cannot be resolved
   * from the ERP (the caller decides on a safe fallback). Never throws for a
   * simple "not found" — throws only on transport/policy failures.
   */
  getCompanyInfo(companyId: string, companyName: string): Promise<NormalizedCompanyInfo | undefined>;

  readLedgerGroups(companyName: string): Promise<ExtractionResult<NormalizedLedgerGroup>>;
  readLedgers(companyName: string, options?: ErpReadOptions): Promise<ExtractionResult<NormalizedLedger>>;
  /**
   * Manually-triggered, bulk, all-ledgers-in-one-call read of mailing/contact/GST fields only
   * (Connect address/email/GSTIN auto-population). Deliberately separate from {@link readLedgers}
   * -- never part of the routine sync cycle.
   */
  readLedgerContactDetails(companyName: string, options?: ErpReadOptions): Promise<ExtractionResult<NormalizedLedger>>;
  readStockGroups(companyName: string): Promise<ExtractionResult<NormalizedStockGroup>>;
  readStockCategories(companyName: string): Promise<ExtractionResult<NormalizedStockCategory>>;
  readStockItems(companyName: string, options?: ErpReadOptions): Promise<ExtractionResult<NormalizedStockItem>>;
  readGodowns(companyName: string): Promise<ExtractionResult<NormalizedGodown>>;
  readCostCategories(companyName: string): Promise<ExtractionResult<NormalizedCostCategory>>;
  readCostCentres(companyName: string): Promise<ExtractionResult<NormalizedCostCentre>>;
  readVoucherTypes(companyName: string): Promise<ExtractionResult<NormalizedVoucherType>>;
  readGstRegistrations(companyName: string): Promise<ExtractionResult<NormalizedGstRegistration>>;

  /** Adapter-owned read diagnostics for the collection extractors. */
  getReadDiagnostics(entityType?: MasterDataEntityType): readonly ExtractorDiagnostics[];
}

export type { ErpCompanyDiscoveryResult, ErpCompanySummary } from './company-discovery.js';
export type { ErpGroupsResult, ErpGroupSummary, GroupExtractionStatus } from './groups.js';
