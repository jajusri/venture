import {
  toPrivacySafeXmlParseDetails,
  XmlParseError,
  type ParsedXmlDocument,
  type ParsedXmlNode,
  type TallyXmlResponseParser,
} from '../xml/response-parser.js';
import type { XmlParserOptions } from '../xml/response-parser-limits.js';

export type VoucherParserFailureCode =
  | 'malformed-xml'
  | 'missing-envelope'
  | 'missing-header'
  | 'missing-body'
  | 'missing-data'
  | 'missing-collection'
  | 'tally-source-error';

export interface VoucherParserFailure {
  readonly status: 'failure';
  readonly code: VoucherParserFailureCode;
  readonly message: string;
  /** Privacy-safe underlying XmlParseError classification (reason + structural details
   * such as line/column/byteOffset/illegalCharactersSanitized -- never surrounding
   * business text) when `code` is 'malformed-xml' and the parser threw a typed error.
   * Null for structural-envelope failures detected after a successful parse, and for
   * non-XmlParseError throws. */
  readonly xmlParseDetail: Readonly<Record<string, number | boolean | string>> | null;
}

export interface VoucherParserSuccess {
  readonly status: 'records' | 'empty' | 'partial';
  readonly document: ParsedXmlDocument;
  readonly records: readonly ParsedXmlNode[];
  readonly declaredRecordCount: number | null;
  readonly message: string;
}

export type VoucherParserOutcome = VoucherParserFailure | VoucherParserSuccess;

export class VoucherCollectionParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parse(rawXml: string, options?: XmlParserOptions): VoucherParserOutcome {
    let document: ParsedXmlDocument;
    try {
      document = this.parser.parse(rawXml, options);
    } catch (error) {
      const xmlParseDetail = error instanceof XmlParseError
        ? toPrivacySafeXmlParseDetails(error)
        : null;
      return failure('malformed-xml', 'Voucher response is not well-formed XML.', xmlParseDetail);
    }

    if (document.root.name !== 'ENVELOPE') {
      return failure('missing-envelope', 'Voucher response root must be ENVELOPE.');
    }

    const header = firstDirectChild(document.root, 'HEADER');
    if (!header) return failure('missing-header', 'Voucher response is missing HEADER.');

    if (this.containsNode(document.root, 'LINEERROR')) {
      return failure('tally-source-error', 'Tally returned a source error for the Voucher export.');
    }

    const body = firstDirectChild(document.root, 'BODY');
    if (!body) return failure('missing-body', 'Voucher response is missing BODY.');
    const data = firstDirectChild(body, 'DATA');
    if (!data) return failure('missing-data', 'Voucher response is missing BODY/DATA.');
    const collections = directChildren(data, 'COLLECTION');
    if (collections.length === 0) {
      return failure(
        'missing-collection',
        'Voucher response is missing BODY/DATA/COLLECTION.',
      );
    }

    const records = collections.flatMap((collection) =>
      directChildren(collection, 'VOUCHER'),
    );
    // CMPINFO/VOUCHER is company-wide metadata, not the number of records in
    // this bounded Collection response. It must never drive completeness.
    const declaredRecordCount = null;
    if (records.length === 0) {
      return {
        status: 'empty',
        document,
        records,
        declaredRecordCount,
        message: 'Voucher Collection is present and contains no Voucher records.',
      };
    }
    return {
      status: 'records',
      document,
      records,
      declaredRecordCount,
      message: `Voucher Collection contains ${records.length} records.`,
    };
  }

  /** Structural parsing primitive retained for diagnostics and focused tests. */
  parseDocument(rawXml: string, options?: XmlParserOptions): ParsedXmlDocument {
    return this.parser.parse(rawXml, options);
  }

  collectVoucherRecords(document: ParsedXmlDocument): readonly ParsedXmlNode[] {
    const body = directChildren(document.root, 'BODY')[0];
    const data = directChildren(body, 'DATA')[0];
    return directChildren(data, 'COLLECTION').flatMap((collection) =>
      directChildren(collection, 'VOUCHER'),
    );
  }

  childText(node: ParsedXmlNode, name: string): string | undefined {
    return directChildren(node, name)[0]?.text?.trim() || undefined;
  }

  directChildren(node: ParsedXmlNode, name: string): readonly ParsedXmlNode[] {
    return directChildren(node, name);
  }

  private containsNode(node: ParsedXmlNode, name: string): boolean {
    if (node.name === name.toUpperCase()) return true;
    return node.children.some((child) => this.containsNode(child, name));
  }
}

function failure(
  code: VoucherParserFailureCode,
  message: string,
  xmlParseDetail: Readonly<Record<string, number | boolean | string>> | null = null,
): VoucherParserFailure {
  return { status: 'failure', code, message, xmlParseDetail };
}

function firstDirectChild(
  node: ParsedXmlNode | undefined,
  name: string,
): ParsedXmlNode | undefined {
  return directChildren(node, name)[0];
}

function directChildren(
  node: ParsedXmlNode | undefined,
  name: string,
): readonly ParsedXmlNode[] {
  if (!node) return [];
  const normalized = name.toUpperCase();
  return node.children.filter((child) => child.name === normalized);
}
