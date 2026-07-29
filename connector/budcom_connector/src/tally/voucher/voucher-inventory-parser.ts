import type { VoucherInventoryExtractionEntry } from '../../erp/voucher/voucher-inventory-domain.js';
import type { ParsedXmlNode, TallyXmlResponseParser } from '../xml/response-parser.js';
import type { XmlParserOptions } from '../xml/response-parser-limits.js';
import { normalizeGuid } from './voucher-ledger-parser.js';

export class VoucherInventoryEntryParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parse(rawXml: string, options?: XmlParserOptions): readonly VoucherInventoryExtractionEntry[] {
    const document = this.parser.parse(rawXml, options);
    if (document.root.name !== 'ENVELOPE') {
      throw new Error('Voucher inventory response root must be ENVELOPE.');
    }
    const header = directChild(document.root, 'HEADER');
    const body = directChild(document.root, 'BODY');
    const data = directChild(body, 'DATA');
    const collection = directChild(data, 'COLLECTION');
    if (!header || !body || !data || !collection) {
      throw new Error('Voucher inventory response is missing required envelope structure.');
    }
    if (this.parser.findFirst(document, 'LINEERROR')) {
      throw new Error('Tally returned a source error for the Voucher inventory export.');
    }

    return collection.children
      .filter((node) => node.name === 'INVENTORYENTRY')
      .map((node) => this.mapEntry(node));
  }

  private mapEntry(node: ParsedXmlNode): VoucherInventoryExtractionEntry {
    const parentNodes = node.children.filter((child) => child.name === 'PARENTGUID');
    if (parentNodes.length !== 1) {
      throw new Error('Voucher inventory entry must contain exactly one ParentGUID.');
    }
    const parentGuid = normalizeGuid(requiredText(node, 'PARENTGUID'));
    const stockItemName = requiredText(node, 'STOCKITEMNAME');
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

function requiredSignedAmount(node: ParsedXmlNode): string {
  const value = requiredText(node, 'AMOUNT');
  const comparable = value.replaceAll(',', '');
  if (!/^-?\d+(?:\.\d+)?$/.test(comparable)) {
    throw new Error('Voucher inventory entry contains an invalid signed Amount.');
  }
  return value;
}

function requiredText(node: ParsedXmlNode, name: string): string {
  const value = optionalText(node, name);
  if (!value) throw new Error(`Voucher inventory entry is missing ${name}.`);
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
