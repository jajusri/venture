import type { TallyXmlRequestSpec } from '../xml/request-builder.js';
import {
  buildCollectionTemplate,
  buildObjectTemplate,
  MasterDataTemplates,
  TallyMasterDataCollections,
} from '../../extraction/templates/master-data-templates.js';
import { TallyCapability } from '../security/capabilities.js';
import type { PolicyOperation } from '../../erp/policy/policy-types.js';

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
}

export interface ApprovedOperation {
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
  readonly render: (params: OperationParams) => TallyXmlRequestSpec;
}

const ADAPTER_VERSION = '0.4.0-security';
const EVIDENCE_BUILD = 'TallyPrime (ESTIMATION, 2026-07-22 live evidence)';

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
  [ApprovedOperationId.Ledgers]: masterCollection(
    ApprovedOperationId.Ledgers,
    TallyMasterDataCollections.Ledgers,
    'VERIFIED_SAFE',
    'MEDIUM',
    524_288,
    30_000,
    'live 921 rec / 260KB / 134ms',
  ),
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
      1_048_576,
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
