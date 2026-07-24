import { describe, expect, it } from 'vitest';

import { TallyXmlResponseParser, XmlParseError } from '../../../src/tally/xml/response-parser.js';
import { DEFAULT_XML_PARSER_MAX_DEPTH } from '../../../src/tally/xml/response-parser-limits.js';
import { SAMPLE_LEDGERS_RESPONSE } from '../../helpers/master-data-fixtures.js';

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

describe('TallyXmlResponseParser inbound boundary characterization', () => {
  const parser = new TallyXmlResponseParser();

  it('1. rejects malformed XML with an unclosed tag', () => {
    expect(() => parser.parse('<ENVELOPE><BODY><DATA>')).toThrow(/unclosed|Invalid XML/i);
  });

  it('2. rejects trailing junk after a valid root (Phase D hardening)', () => {
    expect(() => parser.parse(`${SAMPLE_LEDGERS_RESPONSE}<JUNK>ignored</JUNK>`)).toThrow(XmlParseError);
  });

  it('3. rejects duplicate envelope-like content appended after the first root', () => {
    expect(() => parser.parse(`${SAMPLE_LEDGERS_RESPONSE}${SAMPLE_LEDGERS_RESPONSE}`)).toThrow(
      /trailing content/i,
    );
  });

  it('4. accepts BOM-prefixed XML because trim removes the UTF-8 BOM before parsing', () => {
    const document = parser.parse(`\uFEFF${SAMPLE_LEDGERS_RESPONSE}`);
    expect(parser.findAll(document, 'LEDGER').length).toBe(2);
  });

  it('5. accepts leading whitespace and XML declaration', () => {
    const document = parser.parse(`  <?xml version="1.0"?>\n${SAMPLE_LEDGERS_RESPONSE}`);
    expect(parser.findAll(document, 'LEDGER').length).toBe(2);
  });

  it('6. treats comments as malformed element input', () => {
    expect(() => parser.parse('<ENVELOPE><!-- comment --><BODY/></ENVELOPE>')).toThrow(/Invalid XML/i);
  });

  it('7. treats CDATA sections as malformed element input', () => {
    expect(() => parser.parse('<ENVELOPE><![CDATA[x]]></ENVELOPE>')).toThrow(/Invalid XML/i);
  });

  it('8. leaves numeric entity references undecoded in text nodes', () => {
    const document = parser.parse('<ENVELOPE><BODY><TEXT>&#65;</TEXT></BODY></ENVELOPE>');
    const textNode = parser.findFirst(document, 'TEXT');
    expect(parser.getText(textNode)).toBe('&#65;');
  });

  it('9. treats DOCTYPE input as malformed element input', () => {
    expect(() => parser.parse('<!DOCTYPE html><ENVELOPE><BODY/></ENVELOPE>')).toThrow(/Invalid XML/i);
  });

  it('10. rejects deeply nested XML beyond the configured depth limit', () => {
    expect(() => parser.parse(`<ENVELOPE>${deepNest(DEFAULT_XML_PARSER_MAX_DEPTH - 1)}</ENVELOPE>`)).toThrow(
      /maximum nesting depth/i,
    );
    const document = parser.parse(`<ENVELOPE>${deepNest(DEFAULT_XML_PARSER_MAX_DEPTH - 2)}</ENVELOPE>`);
    expect(document.root.name).toBe('ENVELOPE');
  });

  it('11. parses wide trees within the default node-count limit', () => {
    const document = parser.parse(wideTree(500));
    expect(parser.findAll(document, 'NODE0').length).toBe(1);
  });

  it('12. parses a balanced but semantically empty envelope successfully', () => {
    const document = parser.parse('<ENVELOPE><BODY><DATA><COLLECTION></COLLECTION></DATA></BODY></ENVELOPE>');
    expect(parser.findAll(document, 'LEDGER').length).toBe(0);
  });

  it('13. keeps parser errors privacy-safe without raw XML payload', () => {
    const secretPayload = '<ENVELOPE><SECRETNAME>Hidden Ledger</SECRETNAME></ENVELOPE>';
    try {
      parser.parse(`${secretPayload}<UNCLOSED>`);
      throw new Error('expected parse failure');
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      expect(message).not.toContain('Hidden Ledger');
      expect(message).not.toContain('<SECRETNAME>');
    }
  });
});
