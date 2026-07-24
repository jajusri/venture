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
  ledgers: (companyName: string) =>
    buildCollectionTemplate(TallyMasterDataCollections.Ledgers, {
      companyName,
      collectionModifyFetch: [
        'NAME',
        'PARENT',
        'GUID',
        'ALTERID',
        'MASTERID',
        'OPENINGBALANCE',
        'CLOSINGBALANCE',
        'ISBILLWISEON',
      ],
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
        'PARENT',
        'BASEUNITS',
        'ALIAS',
        'PARTNUMBER',
        'HSNCODE',
        'GSTAPPLICABLE',
        'OPENINGBALANCE',
        'OPENINGRATE',
      ],
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
