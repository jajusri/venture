import type { ParsedXmlDocument, ParsedXmlNode, TallyXmlResponseParser } from '../../tally/xml/response-parser.js';
import { isCountMetadata, normalizeName, normalizeText, slugify } from '../normalization/strings.js';
import { normalizeAmount } from '../normalization/amounts.js';
import { normalizeDate } from '../normalization/dates.js';
import { normalizeInteger } from '../normalization/numbers.js';
import type {
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
  NormalizedUnit,
  NormalizedVoucherType,
} from '../core/types.js';
import { resolveStockItemStableId } from '../core/stock-item-identity.js';

export interface CollectionParseOptions {
  readonly nodeName: string;
  readonly skipUnderCmpInfo?: boolean;
}

export class CollectionEntityParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parseDocument(rawXml: string) {
    return this.parser.parse(rawXml);
  }

  parseNodes(document: ParsedXmlDocument, options: CollectionParseOptions): ParsedXmlNode[] {
    const nodes = this.parser.findAll(document, options.nodeName);
    return nodes.filter((node) => {
      const name = resolveNodeName(this.parser, node);
      if (!name || name.toUpperCase() === options.nodeName || isCountMetadata(name)) {
        return false;
      }
      if (options.skipUnderCmpInfo !== false && isCountMetadata(name)) {
        return false;
      }
      return true;
    });
  }

  getChildText(node: ParsedXmlNode, childName: string): string | undefined {
    return normalizeText(
      this.parser.getText(findChild(node, childName)) ??
        node.attributes[childName.toUpperCase()],
    );
  }

  getLogical(node: ParsedXmlNode, childName: string): boolean | undefined {
    const value = this.getChildText(node, childName)?.toLowerCase();
    if (!value) return undefined;
    return value === 'yes' || value === 'true' || value === '1';
  }

  resolveName(node: ParsedXmlNode): string | undefined {
    return resolveNodeName(this.parser, node);
  }
}

export function resolveNodeName(
  parser: TallyXmlResponseParser,
  node: ParsedXmlNode,
): string | undefined {
  return (
    normalizeText(parser.getText(findChild(node, 'NAME'))) ??
    normalizeText(node.attributes.NAME) ??
    normalizeText(node.text)
  );
}

export function mapCompanyInfo(
  parser: CollectionEntityParser,
  document: ParsedXmlDocument,
  companyId: string,
): NormalizedCompanyInfo | undefined {
  const companyNode =
    parser.parseNodes(document, { nodeName: 'COMPANY', skipUnderCmpInfo: false })[0] ??
    document.root;
  const name = parser.getChildText(companyNode, 'NAME') ?? companyId;
  if (!name) return undefined;

  return {
    id: companyId,
    name,
    mailingName: parser.getChildText(companyNode, 'MAILINGNAME'),
    financialYearFrom: normalizeDate(parser.getChildText(companyNode, 'STARTINGFROM')),
    booksFrom: normalizeDate(parser.getChildText(companyNode, 'BOOKSFROM')),
    baseCurrency: parser.getChildText(companyNode, 'BASECURRENCY') ?? 'INR',
    address: parser.getChildText(companyNode, 'ADDRESS'),
    state: parser.getChildText(companyNode, 'STATENAME'),
    country: parser.getChildText(companyNode, 'COUNTRYNAME'),
    pincode: parser.getChildText(companyNode, 'PINCODE'),
    email: parser.getChildText(companyNode, 'EMAIL'),
    phone: parser.getChildText(companyNode, 'PHONENUMBER'),
    gstin: parser.getChildText(companyNode, 'GSTREGISTRATIONNUMBER'),
  };
}

export function mapLedgerGroup(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
): NormalizedLedgerGroup | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  return {
    id: slugify(name),
    name,
    parentName: parser.getChildText(node, 'PARENT'),
    isRevenue: parser.getLogical(node, 'ISREVENUE'),
    isDebit: parser.getLogical(node, 'ISDEEMEDPOSITIVE'),
  };
}

function resolveLedgerStatus(parser: CollectionEntityParser, node: ParsedXmlNode): NormalizedLedger['status'] {
  const reserved = parser.getChildText(node, 'RESERVEDNAME');
  if (reserved) return 'reserved';
  if (parser.getLogical(node, 'ISDELETED') === true) return 'inactive';
  if (parser.getLogical(node, 'ISACTIVE') === false) return 'inactive';
  return 'active';
}

function resolveLedgerBalanceNature(
  openingText: string | undefined,
  closingText: string | undefined,
  closingSide?: string,
  openingSide?: string,
): NormalizedLedger['balanceNature'] {
  if (closingSide === 'Dr' || openingSide === 'Dr') return 'debit';
  if (closingSide === 'Cr' || openingSide === 'Cr') return 'credit';
  const sample = (closingText ?? openingText)?.toLowerCase() ?? '';
  if (sample.includes(' dr')) return 'debit';
  if (sample.includes(' cr')) return 'credit';
  return 'unknown';
}

export function mapLedger(parser: CollectionEntityParser, node: ParsedXmlNode): NormalizedLedger | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  const openingText = parser.getChildText(node, 'OPENINGBALANCE');
  const closingText = parser.getChildText(node, 'CLOSINGBALANCE');
  const openingBalance = normalizeAmount(openingText);
  const closingBalance = normalizeAmount(closingText);
  return {
    id: slugify(name),
    name,
    normalizedName: normalizeName(name),
    alias: parser.getChildText(node, 'ALIAS'),
    parentGroup: normalizeText(parser.getChildText(node, 'PARENT')),
    openingBalance,
    closingBalance,
    balanceNature: resolveLedgerBalanceNature(
      openingText,
      closingText,
      closingBalance?.side,
      openingBalance?.side,
    ),
    status: resolveLedgerStatus(parser, node),
    guid: parser.getChildText(node, 'GUID'),
    alterId: parser.getChildText(node, 'ALTERID'),
    reservedName: parser.getChildText(node, 'RESERVEDNAME'),
    mailingName: parser.getChildText(node, 'MAILINGNAME'),
    address: parser.getChildText(node, 'ADDRESS'),
    state: parser.getChildText(node, 'STATENAME'),
    country: parser.getChildText(node, 'COUNTRYNAME'),
    pincode: parser.getChildText(node, 'PINCODE'),
    email: parser.getChildText(node, 'EMAIL'),
    phone: parser.getChildText(node, 'PHONENUMBER'),
    mobile: parser.getChildText(node, 'MOBILENUMBER'),
    gstin: parser.getChildText(node, 'PARTYGSTIN') ?? parser.getChildText(node, 'GSTIN'),
    gstRegistrationType: parser.getChildText(node, 'GSTREGISTRATIONTYPE'),
    gstApplicableFrom: parser.getChildText(node, 'APPLICABLEFROM'),
  };
}

export function mapStockGroup(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
): NormalizedStockGroup | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  return {
    id: slugify(name),
    name,
    parentName: parser.getChildText(node, 'PARENT'),
  };
}

export function mapStockCategory(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
): NormalizedStockCategory | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  return { id: slugify(name), name };
}

export function mapStockItem(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
): NormalizedStockItem | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  const guid = parser.getChildText(node, 'GUID');
  const alterId = parser.getChildText(node, 'ALTERID');
  const inactive = parser.getLogical(node, 'ISINACTIVE');
  return {
    id: resolveStockItemStableId({ guid, alterId, name }),
    name,
    normalizedName: normalizeName(name),
    parentGroup: parser.getChildText(node, 'PARENT'),
    category: parser.getChildText(node, 'CATEGORY'),
    baseUnit: parser.getChildText(node, 'BASEUNITS'),
    openingBalance: normalizeAmount(parser.getChildText(node, 'OPENINGBALANCE')),
    closingBalance: normalizeAmount(parser.getChildText(node, 'CLOSINGBALANCE')),
    hsnCode: parser.getChildText(node, 'HSNCODE'),
    gstRate: parser.getChildText(node, 'GSTAPPLICABLE'),
    guid,
    alterId,
    alias: parser.getChildText(node, 'ALIAS'),
    partNumber: parser.getChildText(node, 'PARTNUMBER'),
    status: inactive ? 'inactive' : 'active',
  };
}

export function mapUnit(parser: CollectionEntityParser, node: ParsedXmlNode): NormalizedUnit | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  return {
    id: slugify(name),
    name,
    symbol: parser.getChildText(node, 'SYMBOL') ?? parser.getChildText(node, 'FORMALNAME'),
    decimalPlaces: normalizeInteger(parser.getChildText(node, 'DECIMALPLACES')),
  };
}

export function mapGodown(parser: CollectionEntityParser, node: ParsedXmlNode): NormalizedGodown | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  return {
    id: slugify(name),
    name,
    parentName: parser.getChildText(node, 'PARENT'),
    address: parser.getChildText(node, 'ADDRESS'),
  };
}

export function mapCostCategory(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
): NormalizedCostCategory | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  return {
    id: slugify(name),
    name,
    allocateRevenue: parser.getLogical(node, 'ALLOCATEREVENUE'),
    allocateNonRevenue: parser.getLogical(node, 'ALLOCATENONREVENUE'),
  };
}

export function mapCostCentre(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
): NormalizedCostCentre | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  return {
    id: slugify(name),
    name,
    parentName: parser.getChildText(node, 'PARENT'),
    category: parser.getChildText(node, 'CATEGORY'),
  };
}

export function mapVoucherType(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
): NormalizedVoucherType | undefined {
  const name = parser.resolveName(node);
  if (!name) return undefined;
  return {
    id: slugify(name),
    name,
    parentName: parser.getChildText(node, 'PARENT'),
    numberingMethod: parser.getChildText(node, 'NUMBERINGMETHOD'),
  };
}

export function mapGstRegistration(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
): NormalizedGstRegistration | undefined {
  const name =
    parser.resolveName(node) ??
    parser.getChildText(node, 'GSTREGISTRATIONNUMBER') ??
    parser.getChildText(node, 'GSTIN');
  if (!name) return undefined;
  return {
    id: slugify(name),
    name,
    gstin: parser.getChildText(node, 'GSTREGISTRATIONNUMBER') ?? parser.getChildText(node, 'GSTIN'),
    state: parser.getChildText(node, 'STATE'),
    registrationType: parser.getChildText(node, 'GSTREGISTRATIONTYPE'),
    applicableFrom: normalizeDate(parser.getChildText(node, 'APPLICABLEFROM')),
  };
}

function findChild(node: ParsedXmlNode, name: string): ParsedXmlNode | undefined {
  const target = name.toUpperCase();
  return node.children.find((child) => child.name.toUpperCase() === target);
}
