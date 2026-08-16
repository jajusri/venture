import type { VoucherLedgerExtractionEntry } from '../../erp/voucher/voucher-ledger-domain.js';
import type { ParsedXmlDocument, ParsedXmlNode, TallyXmlResponseParser } from '../xml/response-parser.js';
import type { XmlParserOptions } from '../xml/response-parser-limits.js';
import { VoucherEntryParseError } from './voucher-entry-parse-error.js';

export class VoucherLedgerEntryParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parse(rawXml: string, options?: XmlParserOptions): readonly VoucherLedgerExtractionEntry[] {
    const document = this.parser.parse(rawXml, options);
    const collection = assertVoucherEntryEnvelope(document, this.parser, 'ledger');

    return collection.children
      .filter((node) => node.name === 'LEDGERENTRY')
      .map((node) => this.mapEntry(node));
  }

  private mapEntry(node: ParsedXmlNode): VoucherLedgerExtractionEntry {
    const parentGuid = normalizeGuid(requiredText(node, 'PARENTGUID'));
    const ledgerName = requiredLedgerName(node);
    const signedAmount = normalizeSignedAmount(requiredAmountText(node));
    const isDeemedPositive = parseLogical(requiredIsDeemedPositiveText(node));
    if (isDeemedPositive !== signedAmount.startsWith('-')) {
      throw new VoucherEntryParseError(
        'amount-sign-conflict',
        `Voucher ledger sign conflicts with IsDeemedPositive for ${parentGuid}.`,
      );
    }
    return { parentGuid, ledgerName, isDeemedPositive, signedAmount };
  }
}

/**
 * Shared structural-envelope validation for the ledger/inventory ENTRY responses --
 * identical checks (ENVELOPE root, HEADER/BODY/DATA/COLLECTION presence, Tally
 * LINEERROR) apply to both VoucherLedgerEntryParser and VoucherInventoryEntryParser.
 * Each branch gets its own distinguishable reason rather than one combined message, so
 * a failure is diagnosable without exposing response content.
 */
export function assertVoucherEntryEnvelope(
  document: ParsedXmlDocument,
  parser: TallyXmlResponseParser,
  kind: 'ledger' | 'inventory',
): ParsedXmlNode {
  if (document.root.name !== 'ENVELOPE') {
    throw new VoucherEntryParseError('invalid-root', `Voucher ${kind} response root must be ENVELOPE.`);
  }
  const header = directChild(document.root, 'HEADER');
  if (!header) {
    throw new VoucherEntryParseError('missing-header', `Voucher ${kind} response is missing HEADER.`);
  }
  const body = directChild(document.root, 'BODY');
  if (!body) {
    throw new VoucherEntryParseError('missing-body', `Voucher ${kind} response is missing BODY.`);
  }
  const data = directChild(body, 'DATA');
  if (!data) {
    throw new VoucherEntryParseError('missing-data', `Voucher ${kind} response is missing BODY/DATA.`);
  }
  const collection = directChild(data, 'COLLECTION');
  if (!collection) {
    throw new VoucherEntryParseError(
      'missing-collection',
      `Voucher ${kind} response is missing BODY/DATA/COLLECTION.`,
    );
  }
  if (parser.findFirst(document, 'LINEERROR')) {
    throw new VoucherEntryParseError(
      'tally-line-error',
      `Tally returned a source error for the Voucher ${kind} export.`,
    );
  }
  return collection;
}

export function normalizeGuid(value: string): string {
  const normalized = value.trim().toLowerCase();
  if (!normalized) {
    throw new VoucherEntryParseError('missing-parent-guid', 'Voucher ledger entry has an empty ParentGUID.');
  }
  return normalized;
}

function requiredText(node: ParsedXmlNode, name: string): string {
  const value = directChild(node, name)?.text?.trim();
  if (!value) {
    throw new VoucherEntryParseError('missing-parent-guid', `Voucher ledger entry is missing ${name}.`);
  }
  return value;
}

function requiredLedgerName(node: ParsedXmlNode): string {
  const value = directChild(node, 'LEDGERNAME')?.text?.trim();
  if (!value) {
    throw new VoucherEntryParseError('missing-ledger-name', 'Voucher ledger entry is missing LEDGERNAME.');
  }
  return value;
}

function requiredAmountText(node: ParsedXmlNode): string {
  const value = directChild(node, 'AMOUNT')?.text?.trim();
  if (!value) {
    throw new VoucherEntryParseError('missing-or-malformed-amount', 'Voucher ledger entry is missing AMOUNT.');
  }
  return value;
}

function requiredIsDeemedPositiveText(node: ParsedXmlNode): string {
  const value = directChild(node, 'ISDEEMEDPOSITIVE')?.text?.trim();
  if (!value) {
    throw new VoucherEntryParseError(
      'missing-or-invalid-is-deemed-positive',
      'Voucher ledger entry is missing ISDEEMEDPOSITIVE.',
    );
  }
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
    throw new VoucherEntryParseError(
      'missing-or-malformed-amount',
      'Voucher ledger entry contains an invalid signed Amount.',
    );
  }
  return normalized;
}

function parseLogical(value: string): boolean {
  if (/^yes$/i.test(value)) return true;
  if (/^no$/i.test(value)) return false;
  throw new VoucherEntryParseError(
    'missing-or-invalid-is-deemed-positive',
    'Voucher ledger entry contains an invalid IsDeemedPositive value.',
  );
}
