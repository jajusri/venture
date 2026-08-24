import type {
  EmbeddedTdlCollectionRequestSpec,
  TallyXmlRequestSpec,
} from '../xml/request-builder.js';
import {
  buildCollectionTemplate,
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
  LedgersContactDetails: 'LEDGERS_CONTACT_DETAILS',
  StockGroups: 'STOCK_GROUPS',
  StockCategories: 'STOCK_CATEGORIES',
  StockItems: 'STOCK_ITEMS',
  StockItemsEnrichedFields: 'STOCK_ITEMS_ENRICHED_FIELDS',
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
    // Disabled 2026-08-23 (TD-040): this `render()` used to build `buildObjectTemplate('Company',
    // ...)`, which emits `<TYPE>Object</TYPE><ID>Company</ID>` with no `<SUBTYPE>` and a bare
    // `<ID>` rather than `<ID TYPE="Name">`. Tally's own developer documentation requires
    // `<SUBTYPE>` plus `<ID TYPE="Name">` for any Object-type export, and Object-type export is
    // documented only for named, keyed masters (Ledger/StockItem/Voucher) -- "Company" is a
    // `SVCURRENTCOMPANY` *context*, not a keyed master Tally exposes this way. Sending this exact
    // shape directly to the real, live TallyPrime instance during an unrelated investigation
    // (2026-08-23) produced no response and put Tally's own UI into a fault state requiring a
    // restart. No caller of this operation exists anywhere in Desktop or Android today (verified:
    // neither client requests `GET /companies/:companyId`), so disabling it changes no observed
    // behavior. `getCompanyInfo()`'s pre-existing try/catch (`services/extraction/master-data.
    // service.ts`) already falls back to discovery metadata on any thrown error -- exactly what
    // this now exercises -- so this fails safely with zero Tally traffic rather than guessing a
    // second, equally-unverified shape. Do not re-enable without a live-validated request shape
    // confirmed against Tally's own documentation or a disposable non-production instance.
    render: () => {
      throw new Error(
        'COMPANY_INFO is disabled: no live-validated Tally request shape exists for a ' +
          'Company object export (see operation-registry.ts comment, TD-040). Falls back to ' +
          'discovery metadata instead of sending an unverified request to Tally.',
      );
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
  [ApprovedOperationId.LedgersContactDetails]: {
    operationId: ApprovedOperationId.LedgersContactDetails,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: EVIDENCE_BUILD,
    capability: TallyCapability.MasterRead,
    tallyRequest: 'Export',
    requestKind: 'Collection',
    tallyId: TallyMasterDataCollections.Ledgers,
    classification: 'EXPERIMENTAL_DISABLED',
    risk: 'MEDIUM',
    requiresCompany: true,
    maxRequestBytes: 65_536,
    maxResponseBytes: RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
    timeoutMs: 45_000,
    autoApproveConditional: false,
    evidenceSource: 'PENDING VALIDATION -- LEDGER_CONTACT_FETCH_FIELDS never sent to live Tally',
    rolloutStatus: 'disabled',
    // IMPORTANT: this operation shares `tallyId` ('List of Ledgers') with the already-VERIFIED_SAFE
    // `Ledgers` operation above. `TallyRequestGuard.prepare()` resolves its policy decision via
    // `findApprovedOperationByRequest(requestKind, tallyId)`, matching by (kind, tallyId) off the
    // outgoing XML -- NOT by operationId. Since `Ledgers` is declared first and shares this exact
    // (kind, tallyId) pair, the guard will always resolve THAT entry's policy for this request, not
    // this one's `classification`/`rolloutStatus`. Those fields are therefore inert as a safety gate
    // at the transport chokepoint for this specific operation -- documented here so a future reader
    // doesn't assume flipping `rolloutStatus` to 'production' is what enables this feature.
    //
    // The real gate is this `render()` throwing, exactly like `CompanyInfo`'s TD-040 fix above --
    // the difference is this is lower risk than TD-040 (same proven-safe Collection-Fetch-modify
    // mechanism as `Ledgers`, already production-verified for 923 records; only the specific
    // MAILINGNAME/ADDRESS/STATENAME/COUNTRYNAME/PINCODE/EMAIL/PHONENUMBER/MOBILENUMBER/PARTYGSTIN/
    // GSTREGISTRATIONTYPE/APPLICABLEFROM Fetch tags are unvalidated against live Tally). Per the
    // project's standing rule (BUDCOM-ADAPTIVE-TALLY-SYNC-ARCHITECTURE.md's Tally-request-safety
    // section), do not remove this throw until that field list has been validated against Tally's
    // own developer documentation or a disposable non-production instance, with real evidence
    // recorded in `evidenceSource` above.
    render: () => {
      throw new Error(
        'LEDGERS_CONTACT_DETAILS is disabled: LEDGER_CONTACT_FETCH_FIELDS has not yet been ' +
          'validated against live Tally (see operation-registry.ts comment). Do not enable the ' +
          '/sync/ledgers/contact-details route until validation evidence is recorded here.',
      );
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
  // TD-043 / Catalogue Milestone 0 prerequisite. Mirrors `LedgersContactDetails` exactly: a
  // separate, additive Fetch-field variant of an already-VERIFIED_SAFE collection (`StockItems`
  // above stays untouched and production), gated fully disabled until live-validated. `PARENT`/
  // `CATEGORY`/`BASEUNITS`/`CLOSINGBALANCE`/`GSTAPPLICABLE` are already parsed by `mapStockItem()`
  // but have never been requested from live Tally -- see `STOCK_ITEM_RICH_FETCH_FIELDS`'s doc
  // comment. Required before Catalogue's Stock-group override level (architecture §8/§21) can
  // resolve against real `parentGroup` data. Do not flip `rolloutStatus`/`classification` without
  // recording live Tally validation evidence in `evidenceSource` first.
  [ApprovedOperationId.StockItemsEnrichedFields]: {
    operationId: ApprovedOperationId.StockItemsEnrichedFields,
    erpType: 'tally',
    adapterVersion: ADAPTER_VERSION,
    tallyEvidenceBuild: EVIDENCE_BUILD,
    capability: TallyCapability.MasterRead,
    tallyRequest: 'Export',
    requestKind: 'Collection',
    tallyId: TallyMasterDataCollections.StockItems,
    classification: 'EXPERIMENTAL_DISABLED',
    risk: 'MEDIUM',
    requiresCompany: true,
    maxRequestBytes: 65_536,
    maxResponseBytes: RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
    timeoutMs: 45_000,
    autoApproveConditional: false,
    evidenceSource: 'PENDING VALIDATION -- STOCK_ITEM_RICH_FETCH_FIELDS never sent to live Tally',
    rolloutStatus: 'disabled',
    render: () => {
      throw new Error(
        'STOCK_ITEMS_ENRICHED_FIELDS is disabled: STOCK_ITEM_RICH_FETCH_FIELDS has not yet been ' +
          'validated against live Tally (see operation-registry.ts comment, TD-043). Do not ' +
          'enable this operation until validation evidence is recorded here.',
      );
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
