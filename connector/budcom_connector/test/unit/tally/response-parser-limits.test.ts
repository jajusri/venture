import { describe, expect, it } from 'vitest';

import {
  countParsedXmlNodes,
  measureParsedXmlMaxDepth,
  TallyXmlResponseParser,
  XmlParseError,
} from '../../../src/tally/xml/response-parser.js';
import {
  APPROVED_XML_PARSER_MAX_DEPTH,
  APPROVED_XML_PARSER_MAX_NODE_COUNT,
  DEFAULT_XML_PARSER_MAX_DEPTH,
  DEFAULT_XML_PARSER_MAX_BYTES,
  DEFAULT_XML_PARSER_MAX_NODE_COUNT,
  resolveXmlParserLimits,
  resolveXmlParserMaxBytesForOperation,
  resolveXmlParserOptionsForOperation,
} from '../../../src/tally/xml/response-parser-limits.js';
import {
  ApprovedOperationId,
  RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
  VOUCHER_COLLECTION_MAX_RESPONSE_BYTES,
} from '../../../src/tally/registry/operation-registry.js';
import {
  SAMPLE_COMPANY_INFO_RESPONSE,
  SAMPLE_EMPTY_COLLECTION_RESPONSE,
  SAMPLE_LEDGER_GROUPS_RESPONSE,
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_STOCK_ITEMS_RESPONSE,
} from '../../helpers/master-data-fixtures.js';
import { SAMPLE_COMPANY_LIST_RESPONSE } from '../../helpers/mock-fetch.js';

function deepNest(depth: number): string {
  if (depth <= 0) {
    return '<LEAF>value</LEAF>';
  }
  return `<N${depth}>${deepNest(depth - 1)}</N${depth}>`;
}

function wideTree(nodeCount: number): string {
  const nodes = Array.from({ length: nodeCount }, (_, index) => `<NODE${index}>v</NODE${index}>`).join('');
  return `<ENVELOPE><BODY>${nodes}</BODY></ENVELOPE>`;
}

function richLedgerExport(entityCount: number): string {
  const ledgers = Array.from({ length: entityCount }, (_, index) => `<LEDGER NAME="L${index}">
      <NAME>L${index}</NAME><PARENT>Cash</PARENT>
      <GUID TYPE="String">guid-${String(index).padStart(12, '0')}</GUID>
      <ALTERID>${1000 + index}</ALTERID><MASTERID>${2000 + index}</MASTERID>
      <OPENINGBALANCE>0</OPENINGBALANCE><CLOSINGBALANCE>0</CLOSINGBALANCE><ISBILLWISEON>No</ISBILLWISEON>
    </LEDGER>`).join('');
  return `<ENVELOPE><BODY><DATA><COLLECTION>${ledgers}</COLLECTION></DATA></BODY></ENVELOPE>`;
}

function richStockExport(entityCount: number): string {
  const items = Array.from({ length: entityCount }, (_, index) => `<STOCKITEM NAME="S${index}">
      <NAME>S${index}</NAME><GUID TYPE="String">stock-${String(index).padStart(12, '0')}</GUID>
      <ALTERID>${3000 + index}</ALTERID><PARENT>FG</PARENT><BASEUNITS>Nos</BASEUNITS><HSNCODE>1</HSNCODE>
    </STOCKITEM>`).join('');
  return `<ENVELOPE><BODY><DATA><COLLECTION>${items}</COLLECTION></DATA></BODY></ENVELOPE>`;
}

describe('TallyXmlResponseParser bounded limits', () => {
  const parser = new TallyXmlResponseParser();

  it('accepts valid root with trailing whitespace', () => {
    const document = parser.parse(`${SAMPLE_LEDGERS_RESPONSE}   \n\t  `);
    expect(document.root.name).toBe('ENVELOPE');
  });

  it('accepts the approved Voucher operation byte limit above 1 MiB', () => {
    expect(resolveXmlParserOptionsForOperation(ApprovedOperationId.Vouchers)).toEqual({
      maxBytes: VOUCHER_COLLECTION_MAX_RESPONSE_BYTES,
    });
    expect(resolveXmlParserLimits(
      resolveXmlParserOptionsForOperation(ApprovedOperationId.Vouchers),
    ).maxBytes).toBe(VOUCHER_COLLECTION_MAX_RESPONSE_BYTES);
  });

  // TD-001 fix (2026-08-16, approved architectural decision): the shared parser used to
  // hard-reject any XML-1.0-illegal C0 control character with `xml_illegal_character`.
  // Tally is known to emit these (documented artifact: `&#4;`, TD-001) in ordinary
  // free-text fields, which broke the entire response rather than just the one field.
  // The parser now sanitizes (removes) illegal characters -- both literal bytes and
  // numeric character references -- before structural parsing, which itself remains
  // fully strict. These tests cover the sanitizer's exact contract.
  describe('XML 1.0-illegal character sanitization', () => {
    it('removes an illegal literal control character (0x04) and reports the count', () => {
      const xml = '<ENVELOPE><A>x' + String.fromCharCode(4) + 'y</A></ENVELOPE>';
      const document = parser.parse(xml);
      expect(document.illegalCharactersSanitized).toBe(1);
      expect(parser.findFirst(document, 'A')?.text).toBe('xy');
    });

    it('removes an illegal decimal numeric reference (&#4;) and reports the count', () => {
      const document = parser.parse('<ENVELOPE><A>x&#4;y</A></ENVELOPE>');
      expect(document.illegalCharactersSanitized).toBe(1);
      expect(parser.findFirst(document, 'A')?.text).toBe('xy');
    });

    it('removes an illegal hexadecimal numeric reference (&#x4;) and reports the count', () => {
      const document = parser.parse('<ENVELOPE><A>x&#x4;y</A></ENVELOPE>');
      expect(document.illegalCharactersSanitized).toBe(1);
      expect(parser.findFirst(document, 'A')?.text).toBe('xy');
    });

    it('is class-based, not hard-coded to 0x04 -- also sanitizes an unrelated illegal C0 value', () => {
      // 0x1F (Unit Separator) -- a different illegal C0 control character than the
      // TD-001 example, proving the sanitizer targets the whole illegal-character class.
      const document = parser.parse('<ENVELOPE><A>x&#31;y</A></ENVELOPE>');
      expect(document.illegalCharactersSanitized).toBe(1);
      expect(parser.findFirst(document, 'A')?.text).toBe('xy');
    });

    it('sanitizes a lone UTF-16 surrogate numeric reference (also illegal under XML 1.0)', () => {
      const document = parser.parse('<ENVELOPE><A>x&#xD800;y</A></ENVELOPE>');
      expect(document.illegalCharactersSanitized).toBe(1);
      expect(parser.findFirst(document, 'A')?.text).toBe('xy');
    });

    it('preserves legal XML whitespace -- literal and numeric-reference forms -- untouched', () => {
      const literal = parser.parse('<ENVELOPE><A>tab\tlf\nspace</A></ENVELOPE>');
      expect(literal.illegalCharactersSanitized).toBe(0);
      expect(parser.findFirst(literal, 'A')?.text).toBe('tab\tlf\nspace');

      const numericReferences = parser.parse('<ENVELOPE><A>x&#9;&#10;&#13;y</A></ENVELOPE>');
      expect(numericReferences.illegalCharactersSanitized).toBe(0);
    });

    it('leaves ordinary Unicode and business text completely unchanged', () => {
      const xml = '<ENVELOPE><A>Ramesh &amp; Sons — ₹12,345.67 — नमस्ते</A></ENVELOPE>';
      const document = parser.parse(xml);
      expect(document.illegalCharactersSanitized).toBe(0);
      expect(parser.findFirst(document, 'A')?.text).toBe(
        'Ramesh & Sons — ₹12,345.67 — नमस्ते',
      );
    });

    it('sanitizes an illegal character but still rejects the document if it is structurally malformed', () => {
      // Illegal character sanitation must not paper over a genuinely broken document --
      // this tag is never closed, independent of the illegal character inside it.
      expect(() => parser.parse('<ENVELOPE><A>x&#4;y</ENVELOPE>')).toThrow(XmlParseError);
    });

    it('does not affect an already-legal document (zero sanitized, identical structure)', () => {
      const document = parser.parse(SAMPLE_LEDGERS_RESPONSE);
      expect(document.illegalCharactersSanitized).toBe(0);
    });
  });

  it('rejects valid root with trailing text', () => {
    expect(() => parser.parse(`${SAMPLE_LEDGERS_RESPONSE}extra`)).toThrow(XmlParseError);
    try {
      parser.parse(`${SAMPLE_LEDGERS_RESPONSE}extra`);
    } catch (error) {
      const parseError = error as XmlParseError;
      expect(parseError.reason).toBe('xml_trailing_content');
      expect(parseError.message).not.toContain('extra');
      expect(JSON.stringify(parseError.details ?? {})).not.toContain('extra');
    }
  });

  it('rejects valid root followed by a second envelope', () => {
    expect(() => parser.parse(`${SAMPLE_LEDGERS_RESPONSE}${SAMPLE_LEDGERS_RESPONSE}`)).toThrow(
      /trailing content/i,
    );
  });

  it('rejects valid root followed by an XML-like fragment', () => {
    expect(() => parser.parse(`${SAMPLE_LEDGERS_RESPONSE}<JUNK/>`)).toThrow(/trailing content/i);
  });

  it('accepts known rich ledger and stock fixtures unchanged', () => {
    parser.parse(SAMPLE_LEDGERS_RESPONSE);
    parser.parse(SAMPLE_STOCK_ITEMS_RESPONSE);
    parser.parse(SAMPLE_LEDGER_GROUPS_RESPONSE);
    parser.parse(SAMPLE_COMPANY_INFO_RESPONSE);
    parser.parse(SAMPLE_EMPTY_COLLECTION_RESPONSE);
    parser.parse(SAMPLE_COMPANY_LIST_RESPONSE);
  });

  it('accepts depth exactly at configured limit', () => {
    const maxDepth = 8;
    const xml = `<ENVELOPE>${deepNest(maxDepth - 2)}</ENVELOPE>`;
    const document = parser.parse(xml, { maxDepth });
    expect(measureParsedXmlMaxDepth(document.root)).toBe(maxDepth);
  });

  it('rejects depth one beyond configured limit', () => {
    const maxDepth = 8;
    const xml = `<ENVELOPE>${deepNest(maxDepth - 1)}</ENVELOPE>`;
    expect(() => parser.parse(xml, { maxDepth })).toThrow(XmlParseError);
    try {
      parser.parse(xml, { maxDepth });
    } catch (error) {
      const parseError = error as XmlParseError;
      expect(parseError.reason).toBe('xml_max_depth_exceeded');
      expect(parseError.details).toMatchObject({
        maxDepth,
        observedDepth: maxDepth + 1,
      });
      expect(parseError.message).not.toContain('<N');
    }
  });

  it('accepts node count exactly at configured limit', () => {
    const xml = wideTree(3);
    const nodeCount = countParsedXmlNodes(parser.parse(xml, { maxNodeCount: 5 }).root);
    expect(nodeCount).toBe(5);
  });

  it('rejects node count one beyond configured limit', () => {
    const maxNodeCount = 5;
    expect(() => parser.parse(wideTree(3), { maxNodeCount })).not.toThrow();
    expect(() => parser.parse(wideTree(4), { maxNodeCount })).toThrow(XmlParseError);
    try {
      parser.parse(wideTree(4), { maxNodeCount });
    } catch (error) {
      const parseError = error as XmlParseError;
      expect(parseError.reason).toBe('xml_max_node_count_exceeded');
      expect(parseError.details).toMatchObject({
        maxNodeCount,
        observedNodeCount: maxNodeCount + 1,
      });
    }
  });

  it('accepts synthetic live-scale ledger export under default node limit', () => {
    const document = parser.parse(richLedgerExport(922));
    expect(countParsedXmlNodes(document.root)).toBeLessThanOrEqual(DEFAULT_XML_PARSER_MAX_NODE_COUNT);
    expect(measureParsedXmlMaxDepth(document.root)).toBeLessThanOrEqual(DEFAULT_XML_PARSER_MAX_DEPTH);
  });

  it('accepts synthetic live-scale stock export under default node limit', () => {
    const document = parser.parse(richStockExport(1502));
    expect(countParsedXmlNodes(document.root)).toBeLessThanOrEqual(DEFAULT_XML_PARSER_MAX_NODE_COUNT);
  });

  it('accepts live-scale stock export below rich cap with operation limit', () => {
    const xml = richStockExport(1502);
    const byteLength = Buffer.byteLength(xml, 'utf8');
    expect(byteLength).toBeGreaterThan(250_000);
    expect(byteLength).toBeLessThan(RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES);
    expect(() =>
      parser.parse(xml, resolveXmlParserOptionsForOperation(ApprovedOperationId.StockItems)),
    ).not.toThrow();
  });

  it('accepts a synthetically padded valid envelope immediately below the rich cap', () => {
    const header = '<ENVELOPE><BODY><DATA><COLLECTION><STOCKITEM NAME="S0"><NAME>S0</NAME></STOCKITEM>';
    const footer = '</COLLECTION></DATA></BODY></ENVELOPE>';
    const targetBytes = RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES - 128;
    const padLen = Math.max(0, targetBytes - Buffer.byteLength(header + footer, 'utf8'));
    const xml = `${header}<NOTES>${'N'.repeat(padLen)}</NOTES>${footer}`;
    const byteLength = Buffer.byteLength(xml, 'utf8');
    expect(byteLength).toBeGreaterThan(900_000);
    expect(byteLength).toBeLessThan(RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES);
    expect(() =>
      parser.parse(xml, resolveXmlParserOptionsForOperation(ApprovedOperationId.StockItems)),
    ).not.toThrow();
  });

  it('accepts a synthetically padded ledger envelope above legacy cap with operation limit', () => {
    const header = '<ENVELOPE><BODY><DATA><COLLECTION><LEDGER NAME="L0"><NAME>L0</NAME></LEDGER>';
    const footer = '</COLLECTION></DATA></BODY></ENVELOPE>';
    const targetBytes = RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES - 256;
    const padLen = Math.max(0, targetBytes - Buffer.byteLength(header + footer, 'utf8'));
    const xml = `${header}<NOTES>${'L'.repeat(padLen)}</NOTES>${footer}`;
    const byteLength = Buffer.byteLength(xml, 'utf8');
    expect(byteLength).toBeGreaterThan(600_000);
    expect(byteLength).toBeLessThan(RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES);
    expect(() =>
      parser.parse(xml, resolveXmlParserOptionsForOperation(ApprovedOperationId.Ledgers)),
    ).not.toThrow();
  });

  it('rejects payloads above the operation-specific byte cap even when below transport default', () => {
    const overCap = 'x'.repeat(RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES + 1);
    const xml = `<ENVELOPE><BODY>${overCap}</BODY></ENVELOPE>`;
    expect(() =>
      parser.parse(xml, resolveXmlParserOptionsForOperation(ApprovedOperationId.StockItems)),
    ).toThrow(XmlParseError);
  });

  it('mirrors operation registry caps through resolveXmlParserMaxBytesForOperation', () => {
    expect(resolveXmlParserMaxBytesForOperation(ApprovedOperationId.StockItems)).toBe(
      RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
    );
    expect(resolveXmlParserMaxBytesForOperation(ApprovedOperationId.CompanyList)).toBe(262_144);
    expect(resolveXmlParserMaxBytesForOperation(ApprovedOperationId.HealthCheck)).toBe(65_536);
    expect(DEFAULT_XML_PARSER_MAX_BYTES).toBe(RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES);
  });

  it('rejects excessive wide collection beyond configured node limit', () => {
    expect(() => parser.parse(wideTree(20), { maxNodeCount: 10 })).toThrow(XmlParseError);
  });

  it('keeps limit errors privacy-safe', () => {
    try {
      parser.parse(`${SAMPLE_LEDGERS_RESPONSE}<SECOND/>`);
    } catch (error) {
      const serialized = JSON.stringify(error);
      expect(serialized).not.toContain('Cash');
      expect(serialized).not.toContain('Acme');
      expect(serialized).not.toContain('<SECOND');
    }
  });

  it('isolates parser context across consecutive parses', () => {
    expect(() => parser.parse(wideTree(4), { maxNodeCount: 5 })).toThrow();
    const document = parser.parse(SAMPLE_LEDGERS_RESPONSE);
    expect(document.root.name).toBe('ENVELOPE');
  });

  it('documents default production limits with evidence headroom', () => {
    expect(DEFAULT_XML_PARSER_MAX_DEPTH).toBeGreaterThan(measureParsedXmlMaxDepth(parser.parse(SAMPLE_LEDGERS_RESPONSE).root));
    expect(DEFAULT_XML_PARSER_MAX_NODE_COUNT).toBeGreaterThan(countParsedXmlNodes(parser.parse(richStockExport(1502)).root));
  });
});

describe('TallyXmlResponseParser limit option validation', () => {
  const parser = new TallyXmlResponseParser();
  const validXml = SAMPLE_LEDGERS_RESPONSE;

  const invalidDepthValues = [
    { label: 'zero', value: 0 },
    { label: 'negative', value: -1 },
    { label: 'fractional', value: 3.5 },
    { label: 'NaN', value: Number.NaN },
    { label: 'positive Infinity', value: Number.POSITIVE_INFINITY },
    { label: 'negative Infinity', value: Number.NEGATIVE_INFINITY },
    {
      label: 'above approved maximum',
      value: APPROVED_XML_PARSER_MAX_DEPTH + 1,
    },
  ] as const;

  const invalidNodeCountValues = [
    { label: 'zero', value: 0 },
    { label: 'negative', value: -1 },
    { label: 'fractional', value: 10.1 },
    { label: 'NaN', value: Number.NaN },
    { label: 'positive Infinity', value: Number.POSITIVE_INFINITY },
    { label: 'negative Infinity', value: Number.NEGATIVE_INFINITY },
    {
      label: 'above approved maximum',
      value: APPROVED_XML_PARSER_MAX_NODE_COUNT + 1,
    },
  ] as const;

  it.each(invalidDepthValues)('rejects invalid maxDepth: $label', ({ value }) => {
    expect(() => parser.parse(validXml, { maxDepth: value })).toThrow(XmlParseError);
    try {
      parser.parse(validXml, { maxDepth: value });
    } catch (error) {
      const parseError = error as XmlParseError;
      expect(parseError.reason).toBe('xml_invalid_parser_limit');
      expect(parseError.details).toMatchObject({
        optionName: 'maxDepth',
        approvedMaximum: APPROVED_XML_PARSER_MAX_DEPTH,
      });
      expect(parseError.message).not.toContain('<');
      expect(JSON.stringify(parseError.details ?? {})).not.toContain('Cash');
    }
  });

  it.each(invalidNodeCountValues)('rejects invalid maxNodeCount: $label', ({ value }) => {
    expect(() => parser.parse(validXml, { maxNodeCount: value })).toThrow(XmlParseError);
    try {
      parser.parse(validXml, { maxNodeCount: value });
    } catch (error) {
      const parseError = error as XmlParseError;
      expect(parseError.reason).toBe('xml_invalid_parser_limit');
      expect(parseError.details).toMatchObject({
        optionName: 'maxNodeCount',
        approvedMaximum: APPROVED_XML_PARSER_MAX_NODE_COUNT,
      });
    }
  });

  it('accepts maxDepth at approved production maximum', () => {
    expect(() =>
      parser.parse(validXml, { maxDepth: APPROVED_XML_PARSER_MAX_DEPTH }),
    ).not.toThrow();
  });

  it('accepts maxNodeCount at approved production maximum', () => {
    expect(() =>
      parser.parse(validXml, { maxNodeCount: APPROVED_XML_PARSER_MAX_NODE_COUNT }),
    ).not.toThrow();
  });

  it('accepts lower bounded test overrides below production defaults', () => {
    expect(resolveXmlParserLimits({ maxDepth: 8, maxNodeCount: 5 })).toEqual({
      maxDepth: 8,
      maxNodeCount: 5,
      maxBytes: DEFAULT_XML_PARSER_MAX_BYTES,
    });
  });

  it('applies safe defaults when options are omitted', () => {
    expect(resolveXmlParserLimits()).toEqual({
      maxDepth: DEFAULT_XML_PARSER_MAX_DEPTH,
      maxNodeCount: DEFAULT_XML_PARSER_MAX_NODE_COUNT,
      maxBytes: DEFAULT_XML_PARSER_MAX_BYTES,
    });
    expect(() => parser.parse(validXml)).not.toThrow();
  });

  it('does not expose source XML in invalid limit errors', () => {
    try {
      parser.parse(validXml, { maxDepth: 0 });
    } catch (error) {
      const serialized = JSON.stringify(error);
      expect(serialized).not.toContain('<ENVELOPE');
      expect(serialized).not.toContain('Acme');
    }
  });
});

describe('TallyXmlResponseParser fixture depth and node evidence', () => {
  const parser = new TallyXmlResponseParser();

  it('records committed fixture depth and node counts for documentation evidence', () => {
    const ledger = parser.parse(SAMPLE_LEDGERS_RESPONSE);
    const stock = parser.parse(SAMPLE_STOCK_ITEMS_RESPONSE);
    const groups = parser.parse(SAMPLE_LEDGER_GROUPS_RESPONSE);
    const company = parser.parse(SAMPLE_COMPANY_LIST_RESPONSE);

    expect(measureParsedXmlMaxDepth(ledger.root)).toBe(6);
    expect(measureParsedXmlMaxDepth(stock.root)).toBe(6);
    expect(measureParsedXmlMaxDepth(groups.root)).toBe(6);
    expect(measureParsedXmlMaxDepth(company.root)).toBe(6);

    expect(countParsedXmlNodes(ledger.root)).toBe(21);
    expect(countParsedXmlNodes(parser.parse(richLedgerExport(922)).root)).toBe(8302);
    expect(countParsedXmlNodes(parser.parse(richStockExport(1502)).root)).toBe(10518);
  });
});
