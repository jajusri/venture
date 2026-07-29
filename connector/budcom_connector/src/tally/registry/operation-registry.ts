import type {
  EmbeddedTdlCollectionRequestSpec,
  TallyXmlRequestSpec,
} from '../xml/request-builder.js';
import {
  buildCollectionTemplate,
  buildObjectTemplate,
  MasterDataTemplates,
  TallyMasterDataCollections,
} from '../../extraction/templates/master-data-templates.js';
import { TallyCapability } from '../security/capabilities.js';
import type { PolicyOperation } from '../../erp/policy/policy-types.js';
import {
  buildVoucherCollectionRequestSpec,
  buildVoucherInventoryCollectionRequestSpec,
  buildVoucherLedgerCollectionRequestSpec,
  PRODUCTION_VOUCHER_COLLECTION_NAME,
  PRODUCTION_VOUCHER_INVENTORY_COLLECTION_NAME,
  PRODUCTION_VOUCHER_LEDGER_COLLECTION_NAME,
} from '../voucher/voucher-request.js';

/**
 * Version-aware registry of approved Tally READ operations.
 *
 * The current evidence is environment-scoped (one company: ESTIMATION, one Tally
 * build). Classifications are NOT claimed to be universally safe. Only
 * VERIFIED_SAFE operations execute automatically in production; everything else
 * fails closed.
 */

export const ApprovedOperationId = {
  HealthCheck: 'HEALTH_CHECK',
  CompanyList: 'COMPANY_LIST',
  CompanyInfo: 'COMPANY_INFO',
  LedgerGroups: 'LEDGER_GROUPS',
  Ledgers: 'LEDGERS',
  StockGroups: 'STOCK_GROUPS',
  StockCategories: 'STOCK_CATEGORIES',
  StockItems: 'STOCK_ITEMS',
  Godowns: 'GODOWNS',
  CostCategories: 'COST_CATEGORIES',
  CostCentres: 'COST_CENTRES',
  VoucherTypes: 'VOUCHER_TYPES',
  Vouchers: 'VOUCHERS',
  VoucherLedgerEntries: 'VOUCHER_LEDGER_ENTRIES',
  VoucherInventoryEntries: 'VOUCHER_INVENTORY_ENTRIES',
  GstRegistrations: 'GST_REGISTRATIONS',
} as const;

export type ApprovedOperationId =
  (typeof ApprovedOperationId)[keyof typeof ApprovedOperationId];

export type OperationClassification =
  | 'VERIFIED_SAFE'
  | 'CONDITIONAL'
  | 'EXPERIMENTAL_DISABLED'
  | 'FORBIDDEN'
  | 'UNKNOWN';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface OperationParams {
  readonly companyName?: string;
  readonly dateFrom?: string;
  readonly dateTo?: string;
}

interface ApprovedOperationBase {
  readonly operationId: ApprovedOperationId;
  readonly erpType: 'tally';
  readonly adapterVersion: string;
  /** ERP build the classification evidence came from. Not a universal claim. */
  readonly tallyEvidenceBuild: string;
  readonly capability: TallyCapability;
  readonly tallyRequest: 'Export';
  readonly requestKind: 'Data' | 'Collection' | 'Object';
  /** Tally collection/report/object ID this operation resolves to. */
  readonly tallyId: string;
  readonly classification: OperationClassification;
  readonly risk: RiskLevel;
  readonly requiresCompany: boolean;
  readonly requiresDateRange?: boolean;
  readonly maximumRangeDays?: number;
  readonly maxRequestBytes: number;
  readonly maxResponseBytes: number;
  readonly timeoutMs: number;
  /**
   * True only for CONDITIONAL operations explicitly approved by CODE for
   * automatic execution (never enabled by configuration). Each such operation
   * has a documented safe fallback.
   */
  readonly autoApproveConditional: boolean;
  readonly evidenceSource: string;
  readonly rolloutStatus: 'production' | 'disabled';
  /** Immutable request contract — builds the spec from typed params only. */
}

export interface StandardApprovedOperation extends ApprovedOperationBase {
  readonly render: (params: OperationParams) => TallyXmlRequestSpec;
}

export interface EmbeddedCollectionApprovedOperation extends ApprovedOperationBase {
  readonly renderEmbeddedCollection: (
    params: OperationParams,
  ) => EmbeddedTdlCollectionRequestSpec;
}

export type ApprovedOperation =
  | StandardApprovedOperation
  | EmbeddedCollectionApprovedOperation;

type EmbeddedApprovedOperationId =
  | 'VOUCHERS'
  | 'VOUCHER_LEDGER_ENTRIES'
  | 'VOUCHER_INVENTORY_ENTRIES';
type StandardApprovedOperationId = Exclude<ApprovedOperationId, EmbeddedApprovedOperationId>;

const ADAPTER_VERSION = '0.4.0-security';
const EVIDENCE_BUILD = 'TallyPrime (ESTIMATION, 2026-07-22 live evidence)';

/**
 * Operation-level response cap for TDL-FETCH-enriched master collections.
 * Shared by ledgers and stock items — the repository convention for rich master export.
 *
 * Live evidence (controlled pilot): stock items ~963KB / 1502 rec; ledgers ~618KB / 923 rec.
 * Larger companies may exceed this cap and require additional validation before raising.
 */
export const RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES = 1_048_576 as const;
/** Live Budcom-Test-01 single-day voucher export measured ~1.06 MiB (2026-07-29). */
export const VOUCHER_COLLECTION_MAX_RESPONSE_BYTES = 5_242_880 as const;

/** Pre-rich-FETCH shallow ledger export cap (superseded; retained for test reference). */
export const LEGACY_SHALLOW_LEDGER_MAX_RESPONSE_BYTES = 524_288 as const;

function collection(companyName: string | undefined, id: string): TallyXmlRequestSpec {
  return buildCollectionTemplate(id, companyName ? { companyName } : {});
}

function requireCompany(params: OperationParams, id: string): TallyXmlRequestSpec {
  if (!params.companyName) {
    throw new Error(`Operation for ${id} requires a company context`);
  }
  return collection(params.companyName, id);
}

const REGISTRY: Readonly<Record<ApprovedOperationId, ApprovedOperation>> = Object.freeze({
  [ApprovedOperationId.HealthCheck]: {
    operationId: ApprovedOperationId.HealthCheck,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: EVIDENCE_BUILD,
    capability: TallyCapability.HealthRead,
    tallyRequest: 'Export',
    requestKind: 'Data',
    tallyId: 'License Info',
    classification: 'VERIFIED_SAFE',
    risk: 'LOW',
    requiresCompany: false,
    maxRequestBytes: 4_096,
    maxResponseBytes: 65_536,
    timeoutMs: 10_000,
    autoApproveConditional: false,
    evidenceSource: 'live 148B/410ms',
    rolloutStatus: 'production',
    render: () => ({
      tallyRequest: 'Export',
      type: 'Data',
      id: 'License Info',
    }),
  },
  [ApprovedOperationId.CompanyList]: {
    operationId: ApprovedOperationId.CompanyList,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: EVIDENCE_BUILD,
    capability: TallyCapability.CompanyRead,
    tallyRequest: 'Export',
    requestKind: 'Collection',
    tallyId: TallyMasterDataCollections.Companies,
    classification: 'VERIFIED_SAFE',
    risk: 'LOW',
    requiresCompany: false,
    maxRequestBytes: 65_536,
    maxResponseBytes: 262_144,
    timeoutMs: 15_000,
    autoApproveConditional: false,
    evidenceSource: 'live 2185B/10ms',
    rolloutStatus: 'production',
    render: () => collection(undefined, TallyMasterDataCollections.Companies),
  },
  [ApprovedOperationId.CompanyInfo]: {
    operationId: ApprovedOperationId.CompanyInfo,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: EVIDENCE_BUILD,
    capability: TallyCapability.CompanyRead,
    tallyRequest: 'Export',
    requestKind: 'Object',
    tallyId: 'Company',
    classification: 'CONDITIONAL',
    risk: 'MEDIUM',
    requiresCompany: true,
    maxRequestBytes: 65_536,
    maxResponseBytes: 262_144,
    timeoutMs: 20_000,
    autoApproveConditional: true,
    evidenceSource: 'audit sent; safe discovery fallback exists',
    rolloutStatus: 'production',
    render: (params) => {
      if (!params.companyName) throw new Error('COMPANY_INFO requires a company context');
      return buildObjectTemplate('Company', { companyName: params.companyName });
    },
  },
  [ApprovedOperationId.LedgerGroups]: masterCollection(
    ApprovedOperationId.LedgerGroups,
    TallyMasterDataCollections.Groups,
    'VERIFIED_SAFE',
    'LOW',
    128_000,
    20_000,
    'live 28 rec',
  ),
  [ApprovedOperationId.Ledgers]: {
    ...masterCollection(
      ApprovedOperationId.Ledgers,
      TallyMasterDataCollections.Ledgers,
      'VERIFIED_SAFE',
      'MEDIUM',
      RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
      45_000,
      'live 922 rec / ~618KB rich FETCH / TallyPrime 3.0.1',
    ),
    render: (params) => {
      if (!params.companyName) {
        throw new Error(`Operation for ${TallyMasterDataCollections.Ledgers} requires a company context`);
      }
      return MasterDataTemplates.ledgers(params.companyName);
    },
  },
  [ApprovedOperationId.StockGroups]: masterCollection(
    ApprovedOperationId.StockGroups,
    TallyMasterDataCollections.StockGroups,
    'VERIFIED_SAFE',
    'LOW',
    128_000,
    20_000,
    'live 58 rec',
  ),
  [ApprovedOperationId.StockCategories]: masterCollection(
    ApprovedOperationId.StockCategories,
    TallyMasterDataCollections.StockCategories,
    'VERIFIED_SAFE',
    'LOW',
    65_536,
    20_000,
    'live 0 rec (fast-empty)',
  ),
  [ApprovedOperationId.StockItems]: {
    ...masterCollection(
      ApprovedOperationId.StockItems,
      TallyMasterDataCollections.StockItems,
      'VERIFIED_SAFE',
      'MEDIUM',
      RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
      45_000,
      'live 1502 rec / 963KB / 192ms (TDL FETCH enrich)',
    ),
    render: (params) => {
      if (!params.companyName) {
        throw new Error(`Operation for ${TallyMasterDataCollections.StockItems} requires a company context`);
      }
      return MasterDataTemplates.stockItems(params.companyName);
    },
  },
  [ApprovedOperationId.Godowns]: masterCollection(
    ApprovedOperationId.Godowns,
    TallyMasterDataCollections.Godowns,
    'VERIFIED_SAFE',
    'LOW',
    65_536,
    20_000,
    'live 1 rec',
  ),
  [ApprovedOperationId.CostCategories]: masterCollection(
    ApprovedOperationId.CostCategories,
    TallyMasterDataCollections.CostCategories,
    'VERIFIED_SAFE',
    'LOW',
    65_536,
    20_000,
    'live 1 rec',
  ),
  [ApprovedOperationId.CostCentres]: masterCollection(
    ApprovedOperationId.CostCentres,
    TallyMasterDataCollections.CostCentres,
    'VERIFIED_SAFE',
    'LOW',
    65_536,
    20_000,
    'live 0 rec',
  ),
  [ApprovedOperationId.VoucherTypes]: masterCollection(
    ApprovedOperationId.VoucherTypes,
    TallyMasterDataCollections.VoucherTypes,
    'VERIFIED_SAFE',
    'LOW',
    65_536,
    20_000,
    'live 24 rec',
  ),
  [ApprovedOperationId.Vouchers]: {
    operationId: ApprovedOperationId.Vouchers,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: 'TallyPrime ESTIMATION BASELINE-04 (2026-07-27)',
    capability: TallyCapability.ReportRead,
    tallyRequest: 'Export',
    requestKind: 'Collection',
    tallyId: PRODUCTION_VOUCHER_COLLECTION_NAME,
    classification: 'VERIFIED_SAFE',
    risk: 'MEDIUM',
    requiresCompany: true,
    maxRequestBytes: 65_536,
    maxResponseBytes: VOUCHER_COLLECTION_MAX_RESPONSE_BYTES,
    timeoutMs: 30_000,
    autoApproveConditional: false,
    evidenceSource: 'live Budcom-Test-01 2026-07-24: 14 metadata records / 18279 bytes / valid XML',
    rolloutStatus: 'production',
    requiresDateRange: true,
    maximumRangeDays: 366,
    renderEmbeddedCollection: (params) => {
      if (!params.companyName || !params.dateFrom || !params.dateTo) {
        throw new Error('VOUCHERS requires companyName, dateFrom, and dateTo.');
      }
      return buildVoucherCollectionRequestSpec({
        companyName: params.companyName,
        dateFrom: params.dateFrom,
        dateTo: params.dateTo,
      });
    },
  },
  [ApprovedOperationId.VoucherLedgerEntries]: {
    operationId: ApprovedOperationId.VoucherLedgerEntries,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: 'TallyPrime Budcom-Test-01 (2026-07-29)',
    capability: TallyCapability.ReportRead,
    tallyRequest: 'Export',
    requestKind: 'Collection',
    tallyId: PRODUCTION_VOUCHER_LEDGER_COLLECTION_NAME,
    classification: 'VERIFIED_SAFE',
    risk: 'MEDIUM',
    requiresCompany: true,
    maxRequestBytes: 65_536,
    maxResponseBytes: VOUCHER_COLLECTION_MAX_RESPONSE_BYTES,
    timeoutMs: 30_000,
    autoApproveConditional: false,
    evidenceSource: 'live 2026-07-24: SOURCECOLLECTION/WALK 28 entries / 11121 bytes / 0 illegal references',
    rolloutStatus: 'production',
    requiresDateRange: true,
    maximumRangeDays: 366,
    renderEmbeddedCollection: (params) => {
      if (!params.companyName || !params.dateFrom || !params.dateTo) {
        throw new Error('VOUCHER_LEDGER_ENTRIES requires companyName, dateFrom, and dateTo.');
      }
      return buildVoucherLedgerCollectionRequestSpec({
        companyName: params.companyName,
        dateFrom: params.dateFrom,
        dateTo: params.dateTo,
      });
    },
  },
  [ApprovedOperationId.VoucherInventoryEntries]: {
    operationId: ApprovedOperationId.VoucherInventoryEntries,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: 'TallyPrime Budcom-Test-01 (2026-07-30)',
    capability: TallyCapability.ReportRead,
    tallyRequest: 'Export',
    requestKind: 'Collection',
    tallyId: PRODUCTION_VOUCHER_INVENTORY_COLLECTION_NAME,
    classification: 'VERIFIED_SAFE',
    risk: 'MEDIUM',
    requiresCompany: true,
    maxRequestBytes: 65_536,
    maxResponseBytes: VOUCHER_COLLECTION_MAX_RESPONSE_BYTES,
    timeoutMs: 30_000,
    autoApproveConditional: false,
    evidenceSource:
      'live 2026-07-24: SOURCECOLLECTION/WALK 100 entries / 46687 bytes / 0 illegal references',
    rolloutStatus: 'production',
    requiresDateRange: true,
    maximumRangeDays: 366,
    renderEmbeddedCollection: (params) => {
      if (!params.companyName || !params.dateFrom || !params.dateTo) {
        throw new Error('VOUCHER_INVENTORY_ENTRIES requires companyName, dateFrom, and dateTo.');
      }
      return buildVoucherInventoryCollectionRequestSpec({
        companyName: params.companyName,
        dateFrom: params.dateFrom,
        dateTo: params.dateTo,
      });
    },
  },
  [ApprovedOperationId.GstRegistrations]: masterCollection(
    ApprovedOperationId.GstRegistrations,
    TallyMasterDataCollections.GstRegistrations,
    'VERIFIED_SAFE',
    'LOW',
    65_536,
    20_000,
    'live 0 rec',
  ),
});

function masterCollection(
  operationId: ApprovedOperationId,
  tallyId: string,
  classification: OperationClassification,
  risk: RiskLevel,
  maxResponseBytes: number,
  timeoutMs: number,
  evidenceSource: string,
): ApprovedOperation {
  return {
    operationId,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: EVIDENCE_BUILD,
    capability: TallyCapability.MasterRead,
    tallyRequest: 'Export',
    requestKind: 'Collection',
    tallyId,
    classification,
    risk,
    requiresCompany: true,
    maxRequestBytes: 65_536,
    maxResponseBytes,
    timeoutMs,
    autoApproveConditional: false,
    evidenceSource,
    rolloutStatus: 'production',
    render: (params) => requireCompany(params, tallyId),
  };
}

export function getApprovedOperation(
  operationId: StandardApprovedOperationId,
): StandardApprovedOperation;
export function getApprovedOperation(
  operationId: EmbeddedApprovedOperationId,
): EmbeddedCollectionApprovedOperation;
export function getApprovedOperation(operationId: ApprovedOperationId): ApprovedOperation;
export function getApprovedOperation(operationId: ApprovedOperationId): ApprovedOperation {
  return REGISTRY[operationId];
}

/**
 * Translate a Tally-specific approved operation into the ERP-neutral
 * {@link PolicyOperation} the security core understands. This is the only place
 * Tally capability names map onto neutral policy concepts.
 */
export function toPolicyOperation(operation: ApprovedOperation): PolicyOperation {
  return {
    operationId: operation.operationId,
    classification: operation.classification,
    rolloutStatus: operation.rolloutStatus,
    maxRequestBytes: operation.maxRequestBytes,
    isHealthProbe: operation.capability === TallyCapability.HealthRead,
    autoApproveConditional: operation.autoApproveConditional,
  };
}

export function listApprovedOperations(): ApprovedOperation[] {
  return Object.values(REGISTRY);
}

/**
 * Resolve an approved operation from a raw (requestKind, tallyId) pair extracted
 * from an XML request. Returns undefined for anything not registered — callers
 * MUST treat undefined as UNKNOWN → DENY (fail closed).
 */
export function findApprovedOperationByRequest(
  requestKind: string | undefined,
  tallyId: string | undefined,
): ApprovedOperation | undefined {
  if (!requestKind || !tallyId) return undefined;
  const kind = requestKind.toUpperCase();
  const id = tallyId.trim().toLowerCase();
  return listApprovedOperations().find(
    (op) => op.requestKind.toUpperCase() === kind && op.tallyId.toLowerCase() === id,
  );
}
