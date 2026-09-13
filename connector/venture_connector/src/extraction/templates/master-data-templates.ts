import { LEDGER_RICH_FETCH_FIELDS, LEDGER_CONTACT_FETCH_FIELDS } from '../core/ledger-identity.js';
import { STOCK_ITEM_RICH_FETCH_FIELDS } from '../core/stock-item-identity.js';
import type { TallyXmlRequestSpec } from '../../tally/xml/request-builder.js';

/** Tally collection/object IDs for master data extraction. */
export const TallyMasterDataCollections = {
  Companies: 'List of Companies',
  Groups: 'List of Groups',
  Ledgers: 'List of Ledgers',
  StockGroups: 'List of Stock Groups',
  StockCategories: 'List of Stock Categories',
  StockItems: 'List of Stock Items',
  Units: 'List of Units',
  Godowns: 'List of Godowns',
  CostCategories: 'List of Cost Categories',
  CostCentres: 'List of Cost Centres',
  VoucherTypes: 'List of Voucher Types',
  GstRegistrations: 'List of GST Registrations',
} as const;

export const TallyMasterDataObjects = {
  Company: 'Company',
} as const;

export interface MasterDataTemplateOptions {
  readonly companyName?: string;
  readonly extraVariables?: Readonly<Record<string, string>>;
  readonly collectionModifyFetch?: readonly string[];
}

export function buildCollectionTemplate(
  collectionId: string,
  options: MasterDataTemplateOptions = {},
): TallyXmlRequestSpec {
  const staticVariables: Record<string, string> = {
    SVEXPORTFORMAT: '$$SysName:XML',
    ...options.extraVariables,
  };
  if (options.companyName) {
    staticVariables.SVCURRENTCOMPANY = options.companyName;
  }
  return {
    tallyRequest: 'Export',
    type: 'Collection',
    id: collectionId,
    description: options.collectionModifyFetch?.length ? undefined : collectionId,
    staticVariables,
    collectionModifyFetch: options.collectionModifyFetch,
  };
}

export function buildObjectTemplate(
  objectId: string,
  options: MasterDataTemplateOptions = {},
): TallyXmlRequestSpec {
  const staticVariables: Record<string, string> = {
    SVEXPORTFORMAT: '$$SysName:XML',
    ...options.extraVariables,
  };
  if (options.companyName) {
    staticVariables.SVCURRENTCOMPANY = options.companyName;
  }
  return {
    tallyRequest: 'Export',
    type: 'Object',
    id: objectId,
    description: objectId,
    staticVariables,
  };
}

export const MasterDataTemplates = {
  companyList: () => buildCollectionTemplate(TallyMasterDataCollections.Companies),
  companyInfo: (companyName: string) =>
    buildObjectTemplate(TallyMasterDataObjects.Company, { companyName }),
  ledgerGroups: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.Groups, { companyName }),
  // Field list lives in one place, `ledger-identity.ts`'s LEDGER_RICH_FETCH_FIELDS -- see its
  // own doc comment for the field-by-field history (TD-001/TD-035/ALIAS). Previously duplicated
  // here as an independent literal, which is exactly how the missing ALIAS field went unnoticed:
  // two copies to keep in sync, only one of which was ever checked against real Tally behavior.
  ledgers: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.Ledgers, {
      companyName,
      collectionModifyFetch: [...LEDGER_RICH_FETCH_FIELDS],
    }),
  // Deliberately separate from `ledgers` above -- a manually-triggered, occasional bulk fetch of
  // mailing/contact/GST fields only, never part of the routine Ledgers sync's request shape. Same
  // Tally collection (`List of Ledgers`), different (additive) Fetch list -- see
  // `LEDGER_CONTACT_FETCH_FIELDS`'s own doc comment.
  ledgersContactDetails: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.Ledgers, {
      companyName,
      collectionModifyFetch: [...LEDGER_CONTACT_FETCH_FIELDS],
    }),
  stockGroups: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.StockGroups, { companyName }),
  stockCategories: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.StockCategories, { companyName }),
  stockItems: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.StockItems, {
      companyName,
      collectionModifyFetch: [
        'GUID',
        'ALTERID',
        'NAME',
        'ALIAS',
        'PARTNUMBER',
        'HSNCODE',
        'OPENINGBALANCE',
        'OPENINGRATE',
      ],
    }),
  // TD-043 / Catalogue Milestone 0 prerequisite -- NOT part of the routine stock-item sync above.
  // Deliberately separate additive Fetch list (same collection, `List of Stock Items`), mirroring
  // `ledgersContactDetails`'s relationship to `ledgers`: gated `EXPERIMENTAL_DISABLED`/`disabled`
  // in the operation registry until live-validated against real Tally, so no unvalidated request
  // shape reaches production. See `STOCK_ITEM_RICH_FETCH_FIELDS`'s own doc comment.
  stockItemsEnrichedFields: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.StockItems, {
      companyName,
      collectionModifyFetch: [...STOCK_ITEM_RICH_FETCH_FIELDS],
    }),
  units: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.Units, { companyName }),
  godowns: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.Godowns, { companyName }),
  costCategories: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.CostCategories, { companyName }),
  costCentres: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.CostCentres, { companyName }),
  voucherTypes: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.VoucherTypes, { companyName }),
  gstRegistrations: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.GstRegistrations, { companyName }),
} as const;
