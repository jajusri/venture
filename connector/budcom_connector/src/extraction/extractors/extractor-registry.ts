import type { TallyReadGateway } from '../../tally/gateway/tally-read-gateway.js';
import { ApprovedOperationId } from '../../tally/registry/operation-registry.js';
import type { TallyXmlResponseParser } from '../../tally/xml/response-parser.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { MasterDataEntityType } from '../core/types.js';
import {
  CollectionEntityParser,
  mapCostCategory,
  mapCostCentre,
  mapGodown,
  mapGstRegistration,
  mapLedger,
  mapLedgerGroup,
  mapStockCategory,
  mapStockGroup,
  mapStockItem,
  mapVoucherType,
} from '../parsers/entity-mappers.js';
import { MasterDataExtractor } from './master-data-extractor.js';
import type {
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
} from '../core/types.js';

export interface MasterDataExtractorRegistry {
  readonly ledgerGroups: MasterDataExtractor<NormalizedLedgerGroup>;
  readonly ledgers: MasterDataExtractor<NormalizedLedger>;
  readonly ledgerContactDetails: MasterDataExtractor<NormalizedLedger>;
  readonly stockGroups: MasterDataExtractor<NormalizedStockGroup>;
  readonly stockCategories: MasterDataExtractor<NormalizedStockCategory>;
  readonly stockItems: MasterDataExtractor<NormalizedStockItem>;
  readonly godowns: MasterDataExtractor<NormalizedGodown>;
  readonly costCategories: MasterDataExtractor<NormalizedCostCategory>;
  readonly costCentres: MasterDataExtractor<NormalizedCostCentre>;
  readonly voucherTypes: MasterDataExtractor<NormalizedVoucherType>;
  readonly gstRegistrations: MasterDataExtractor<NormalizedGstRegistration>;
}

export function createMasterDataExtractorRegistry(deps: {
  gateway: TallyReadGateway;
  responseParser: TallyXmlResponseParser;
  logger: Logger;
}): MasterDataExtractorRegistry {
  const logger = deps.logger.child({ module: 'extraction' });
  const create = <T extends { id: string }>(
    config: ConstructorParameters<typeof MasterDataExtractor<T>>[0],
  ) => new MasterDataExtractor(config, deps.gateway, deps.responseParser, logger);

  return {
    ledgerGroups: create({
      entityType: MasterDataEntityType.LedgerGroup,
      operationId: ApprovedOperationId.LedgerGroups,
      nodeName: 'GROUP',
      mapNode: mapLedgerGroup,
    }),
    ledgers: create({
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      mapNode: mapLedger,
      masterDataContract: 'ledger',
    }),
    // No masterDataContract -- the 'ledger' contract's thresholds are tuned for the full rich-ledger
    // field set/semantics; forcing a contact-only payload through it risks spurious MALFORMED
    // failures for a shape it was never designed to assess. Matches the existing precedent of
    // stockGroups/godowns/gstRegistrations below, which also omit masterDataContract.
    ledgerContactDetails: create({
      entityType: MasterDataEntityType.LedgerContactDetails,
      operationId: ApprovedOperationId.LedgersContactDetails,
      nodeName: 'LEDGER',
      mapNode: mapLedger,
    }),
    stockGroups: create({
      entityType: MasterDataEntityType.StockGroup,
      operationId: ApprovedOperationId.StockGroups,
      nodeName: 'STOCKGROUP',
      mapNode: mapStockGroup,
    }),
    stockCategories: create({
      entityType: MasterDataEntityType.StockCategory,
      operationId: ApprovedOperationId.StockCategories,
      nodeName: 'STOCKCATEGORY',
      mapNode: mapStockCategory,
    }),
    stockItems: create({
      entityType: MasterDataEntityType.StockItem,
      operationId: ApprovedOperationId.StockItems,
      nodeName: 'STOCKITEM',
      mapNode: mapStockItem,
      masterDataContract: 'stock-item',
    }),
    godowns: create({
      entityType: MasterDataEntityType.Godown,
      operationId: ApprovedOperationId.Godowns,
      nodeName: 'GODOWN',
      mapNode: mapGodown,
    }),
    costCategories: create({
      entityType: MasterDataEntityType.CostCategory,
      operationId: ApprovedOperationId.CostCategories,
      nodeName: 'COSTCATEGORY',
      mapNode: mapCostCategory,
    }),
    costCentres: create({
      entityType: MasterDataEntityType.CostCentre,
      operationId: ApprovedOperationId.CostCentres,
      nodeName: 'COSTCENTRE',
      mapNode: mapCostCentre,
    }),
    voucherTypes: create({
      entityType: MasterDataEntityType.VoucherType,
      operationId: ApprovedOperationId.VoucherTypes,
      nodeName: 'VOUCHERTYPE',
      mapNode: mapVoucherType,
    }),
    gstRegistrations: create({
      entityType: MasterDataEntityType.GstRegistration,
      operationId: ApprovedOperationId.GstRegistrations,
      nodeName: 'GSTREGISTRATION',
      mapNode: mapGstRegistration,
    }),
  };
}

export function createCompanyInfoParser(responseParser: TallyXmlResponseParser): CollectionEntityParser {
  return new CollectionEntityParser(responseParser);
}
