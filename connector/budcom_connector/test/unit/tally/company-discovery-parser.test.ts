import { describe, expect, it } from 'vitest';

import {
  assessCompanyDiscovery,
  assessCompanyDiscoveryEnvelope,
} from '../../../src/tally/contracts/company-discovery-contract.js';
import { CompanyDiscoveryParser } from '../../../src/tally/discovery/company-discovery-parser.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import {
  COMPANY_DUPLICATE_RECORDS,
  COMPANY_EMPTY_LIST,
  COMPANY_MALFORMED_XML,
  COMPANY_MISSING_OPTIONAL_FIELDS,
  COMPANY_MISSING_REQUIRED_IDENTITY,
  COMPANY_MULTIPLE_VALID,
  COMPANY_NO_COMPANY_OPEN,
  COMPANY_ONE_VALID,
  COMPANY_PARTIAL_WITH_VALID,
  COMPANY_SPECIAL_CHARACTERS,
  COMPANY_UNEXPECTED_ENVELOPE,
  COMPANY_WHITESPACE_NAME,
} from '../../helpers/company-discovery-fixtures.js';

describe('CompanyDiscoveryParser', () => {
  const xmlParser = new TallyXmlResponseParser();
  const parser = new CompanyDiscoveryParser(xmlParser);

  it('parses one valid company', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_ONE_VALID));
    expect(result.companies).toEqual([
      {
        id: 'estimation',
        name: 'ESTIMATION',
        startingFrom: '20240401',
        baseCurrency: 'INR',
      },
    ]);
    expect(result.recordsMissingIdentity).toBe(0);
  });

  it('parses multiple valid companies', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_MULTIPLE_VALID));
    expect(result.companies).toHaveLength(2);
    expect(result.companies[0]?.name).toBe('Acme Traders Pvt Ltd');
    expect(result.companies[1]?.name).toBe('Demo Company');
  });

  it('removes duplicate company records', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_DUPLICATE_RECORDS));
    expect(result.companies.map((c) => c.name)).toEqual(['Acme Traders', 'Other Co']);
    expect(result.duplicateRecordsRemoved).toBe(1);
  });

  it('returns empty list for empty collection', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_EMPTY_LIST));
    expect(result.companies).toEqual([]);
    expect(result.recordsMissingIdentity).toBe(0);
  });

  it('returns empty list when no company is open', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_NO_COMPANY_OPEN));
    expect(result.companies).toEqual([]);
  });

  it('accepts missing optional fields without inventing values', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_MISSING_OPTIONAL_FIELDS));
    expect(result.companies[0]).toEqual({
      id: 'minimal-co',
      name: 'Minimal Co',
    });
    expect(result.companies[0]?.baseCurrency).toBeUndefined();
    expect(result.companies[0]?.startingFrom).toBeUndefined();
  });

  it('counts records missing required identity fields', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_MISSING_REQUIRED_IDENTITY));
    expect(result.companies).toEqual([]);
    expect(result.recordsMissingIdentity).toBeGreaterThan(0);
  });

  it('normalizes whitespace in company names', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_WHITESPACE_NAME));
    expect(result.companies[0]?.name).toBe('Acme Traders');
  });

  it('preserves special characters in company names', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_SPECIAL_CHARACTERS));
    expect(result.companies[0]?.name).toBe('Müller & Söhne GmbH');
    expect(result.companies[0]?.id).toBeTruthy();
  });

  it('recovers valid companies when invalid nodes are mixed in', () => {
    const result = parser.parseCompanies(xmlParser.parse(COMPANY_PARTIAL_WITH_VALID));
    expect(result.companies).toEqual([{ id: 'valid-co', name: 'Valid Co' }]);
    expect(result.recordsMissingIdentity).toBe(1);
  });
});

describe('company discovery response contract', () => {
  const xmlParser = new TallyXmlResponseParser();
  const parser = new CompanyDiscoveryParser(xmlParser);

  it('flags unexpected response envelope as drift', () => {
    const issue = assessCompanyDiscoveryEnvelope(COMPANY_UNEXPECTED_ENVELOPE);
    expect(issue?.status).toBe('DRIFT');
  });

  it('assesses SUCCESS for trusted company list', () => {
    const parseResult = parser.parseCompanies(xmlParser.parse(COMPANY_ONE_VALID));
    expect(assessCompanyDiscovery(parseResult).status).toBe('SUCCESS');
  });

  it('assesses EMPTY for valid empty collection', () => {
    const parseResult = parser.parseCompanies(xmlParser.parse(COMPANY_EMPTY_LIST));
    const assessment = assessCompanyDiscovery(parseResult);
    expect(assessment.status).toBe('EMPTY');
    expect(assessment.dataQuality?.status).toBe('EMPTY');
  });

  it('assesses INCOMPLETE when identity fields are missing', () => {
    const parseResult = parser.parseCompanies(xmlParser.parse(COMPANY_MISSING_REQUIRED_IDENTITY));
    const assessment = assessCompanyDiscovery(parseResult);
    expect(assessment.status).toBe('INCOMPLETE');
    expect(assessment.dataQuality?.status).toBe('INCOMPLETE');
  });

  it('assesses SUCCESS with INCOMPLETE dataQuality for partial recovery', () => {
    const parseResult = parser.parseCompanies(xmlParser.parse(COMPANY_PARTIAL_WITH_VALID));
    const assessment = assessCompanyDiscovery(parseResult);
    expect(assessment.status).toBe('SUCCESS');
    expect(assessment.dataQuality?.status).toBe('INCOMPLETE');
  });

  it('rejects malformed XML at parse time', () => {
    expect(() => xmlParser.parse(COMPANY_MALFORMED_XML)).toThrow('Invalid XML');
  });
});
