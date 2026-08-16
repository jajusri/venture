import type { VoucherInventoryExtractionEntry } from '../../erp/voucher/voucher-inventory-domain.js';
import type { ParsedXmlNode, TallyXmlResponseParser } from '../xml/response-parser.js';
import type { XmlParserOptions } from '../xml/response-parser-limits.js';
import { assertVoucherEntryEnvelope, normalizeGuid } from './voucher-ledger-parser.js';
import { VoucherEntryParseError } from './voucher-entry-parse-error.js';

export class VoucherInventoryEntryParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parse(rawXml: string, options?: XmlParserOptions): readonly VoucherInventoryExtractionEntry[] {
    const document = this.parser.parse(rawXml, options);
    const collection = assertVoucherEntryEnvelope(document, this.parser, 'inventory');

    return collection.children
      .filter((node) => node.name === 'INVENTORYENTRY')
      .map((node) => this.mapEntry(node));
  }

  private mapEntry(node: ParsedXmlNode): VoucherInventoryExtractionEntry {
    const parentNodes = node.children.filter((child) => child.name === 'PARENTGUID');
    if (parentNodes.length !== 1) {
      throw new VoucherEntryParseError(
        'invalid-parent-guid-node-count',
        'Voucher inventory entry must contain exactly one ParentGUID.',
      );
    }
    const parentGuid = normalizeGuid(requiredParentGuidText(node));
    const stockItemName = requiredStockItemName(node);
    const signedAmount = requiredSignedAmount(node);
    const actualQuantity = optionalText(node, 'ACTUALQTY');
    const billedQuantity = optionalText(node, 'BILLEDQTY');
    const rate = optionalText(node, 'RATE');
    return {
      parentGuid,
      stockItemName,
      ...(actualQuantity ? { actualQuantity } : {}),
      ...(billedQuantity ? { billedQuantity } : {}),
      ...(rate ? { rate } : {}),
      signedAmount,
    };
  }
}

function requiredParentGuidText(node: ParsedXmlNode): string {
  const value = optionalText(node, 'PARENTGUID');
  if (!value) {
    throw new VoucherEntryParseError(
      'missing-parent-guid',
      'Voucher inventory entry is missing PARENTGUID.',
    );
  }
  return value;
}

function requiredStockItemName(node: ParsedXmlNode): string {
  const value = optionalText(node, 'STOCKITEMNAME');
  if (!value) {
    throw new VoucherEntryParseError(
      'missing-stock-item-name',
      'Voucher inventory entry is missing STOCKITEMNAME.',
    );
  }
  return value;
}

function requiredSignedAmount(node: ParsedXmlNode): string {
  const value = optionalText(node, 'AMOUNT');
  if (!value) {
    throw new VoucherEntryParseError(
      'missing-or-malformed-amount',
      'Voucher inventory entry is missing AMOUNT.',
    );
  }
  const comparable = value.replaceAll(',', '');
  if (!/^-?\d+(?:\.\d+)?$/.test(comparable)) {
    throw new VoucherEntryParseError(
      'missing-or-malformed-amount',
      'Voucher inventory entry contains an invalid signed Amount.',
    );
  }
  return value;
}

function optionalText(node: ParsedXmlNode, name: string): string | undefined {
  return directChild(node, name)?.text?.trim() || undefined;
}

function directChild(
  node: ParsedXmlNode | undefined,
  name: string,
): ParsedXmlNode | undefined {
  return node?.children.find((child) => child.name === name);
}
