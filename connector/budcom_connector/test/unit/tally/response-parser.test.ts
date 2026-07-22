import { describe, expect, it } from 'vitest';

import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { CompanyDiscoveryParser } from '../../../src/tally/discovery/company-discovery-parser.js';
import { SAMPLE_COMPANY_LIST_RESPONSE, SAMPLE_TALLY_COMPANY_LIST_RESPONSE } from '../../helpers/mock-fetch.js';

describe('TallyXmlResponseParser', () => {
  const parser = new TallyXmlResponseParser();

  it('parses nested XML into a tree', () => {
    const document = parser.parse(SAMPLE_COMPANY_LIST_RESPONSE);
    const companies = parser.findAll(document, 'COMPANY');
    expect(companies.length).toBe(2);
    expect(parser.getText(parser.findFirst(document, 'VERSION'))).toBe('1');
  });

  it('invokes registered handlers', () => {
    const seen: string[] = [];
    parser.registerHandler('COMPANY', (node) => {
      seen.push(node.name);
    });
    parser.parse(SAMPLE_COMPANY_LIST_RESPONSE);
    expect(seen.length).toBe(2);
  });

  it('rejects invalid XML', () => {
    expect(() => parser.parse('not xml')).toThrow('Invalid XML');
  });
});

describe('CompanyDiscoveryParser', () => {
  const parser = new TallyXmlResponseParser();
  const discoveryParser = new CompanyDiscoveryParser(parser);

  it('extracts companies from collection response', () => {
    const document = parser.parse(SAMPLE_COMPANY_LIST_RESPONSE);
    const companies = discoveryParser.parseCompanies(document);
    expect(companies).toEqual([
      {
        id: 'acme-traders-pvt-ltd',
        name: 'Acme Traders Pvt Ltd',
        startingFrom: '20240401',
        booksFrom: '20240401',
      },
      {
        id: 'demo-company',
        name: 'Demo Company',
        startingFrom: '20230401',
      },
    ]);
  });

  it('ignores CMPINFO count nodes and reads NAME attributes from real Tally XML', () => {
    const document = parser.parse(SAMPLE_TALLY_COMPANY_LIST_RESPONSE);
    const companies = discoveryParser.parseCompanies(document);
    expect(companies).toEqual([
      {
        id: 'estimation',
        name: 'ESTIMATION',
      },
    ]);
  });
});
