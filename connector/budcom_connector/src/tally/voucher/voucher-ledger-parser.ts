import type { VoucherLedgerExtractionEntry } from '../../erp/voucher/voucher-ledger-domain.js';
import type { ParsedXmlNode, TallyXmlResponseParser } from '../xml/response-parser.js';
import type { XmlParserOptions } from '../xml/response-parser-limits.js';

export class VoucherLedgerEntryParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parse(rawXml: string, options?: XmlParserOptions): readonly VoucherLedgerExtractionEntry[] {
    const document = this.parser.parse(rawXml, options);
    if (document.root.name !== 'ENVELOPE') {
      throw new Error('Voucher ledger response root must be ENVELOPE.');
    }
    const header = directChild(document.root, 'HEADER');
    const body = directChild(document.root, 'BODY');
    const data = directChild(body, 'DATA');
    const collection = directChild(data, 'COLLECTION');
    if (!header || !body || !data || !collection) {
      throw new Error('Voucher ledger response is missing required envelope structure.');
    }
    if (this.parser.findFirst(document, 'LINEERROR')) {
      throw new Error('Tally returned a source error for the Voucher ledger export.');
    }

    return collection.children
      .filter((node) => node.name === 'LEDGERENTRY')
      .map((node) => this.mapEntry(node));
  }

  private mapEntry(node: ParsedXmlNode): VoucherLedgerExtractionEntry {
    const parentGuid = normalizeGuid(requiredText(node, 'PARENTGUID'));
    const ledgerName = requiredText(node, 'LEDGERNAME');
    const signedAmount = normalizeSignedAmount(requiredText(node, 'AMOUNT'));
    const isDeemedPositive = parseLogical(requiredText(node, 'ISDEEMEDPOSITIVE'));
    if (isDeemedPositive !== signedAmount.startsWith('-')) {
      throw new Error(`Voucher ledger sign conflicts with IsDeemedPositive for ${parentGuid}.`);
    }
    return { parentGuid, ledgerName, isDeemedPositive, signedAmount };
  }
}

export function normalizeGuid(value: string): string {
  const normalized = value.trim().toLowerCase();
  if (!normalized) throw new Error('Voucher ledger entry has an empty ParentGUID.');
  return normalized;
}

function requiredText(node: ParsedXmlNode, name: string): string {
  const value = directChild(node, name)?.text?.trim();
  if (!value) throw new Error(`Voucher ledger entry is missing ${name}.`);
  return value;
}

function directChild(
  node: ParsedXmlNode | undefined,
  name: string,
): ParsedXmlNode | undefined {
  return node?.children.find((child) => child.name === name);
}

function normalizeSignedAmount(value: string): string {
  const normalized = value.replaceAll(',', '').trim();
  if (!/^-?\d+(?:\.\d+)?$/.test(normalized)) {
    throw new Error('Voucher ledger entry contains an invalid signed Amount.');
  }
  return normalized;
}

function parseLogical(value: string): boolean {
  if (/^yes$/i.test(value)) return true;
  if (/^no$/i.test(value)) return false;
  throw new Error('Voucher ledger entry contains an invalid IsDeemedPositive value.');
}
