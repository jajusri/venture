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
  DEFAULT_XML_PARSER_MAX_NODE_COUNT,
  resolveXmlParserLimits,
} from '../../../src/tally/xml/response-parser-limits.js';
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
    });
  });

  it('applies safe defaults when options are omitted', () => {
    expect(resolveXmlParserLimits()).toEqual({
      maxDepth: DEFAULT_XML_PARSER_MAX_DEPTH,
      maxNodeCount: DEFAULT_XML_PARSER_MAX_NODE_COUNT,
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
