import { describe, expect, it } from 'vitest';

import { AppError } from '../../../src/infrastructure/errors/app-error.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import {
  redactTallyRequestXml,
  validateTallyRequestXml,
} from '../../../src/tally/safety/xml-request-validator.js';

describe('validateTallyRequestXml', () => {
  const builder = new TallyXmlRequestBuilder();

  it('accepts a valid Tally collection export request', () => {
    const xml = builder.buildCompanyListRequest();
    const result = validateTallyRequestXml(xml, 65_536);
    expect(result.collectionId).toBe('List of Companies');
    expect(result.requestKind).toBe('COLLECTION');
  });

  it('rejects empty XML', () => {
    expect(() => validateTallyRequestXml('', 65_536)).toThrow(AppError);
  });

  it('rejects XML without ENVELOPE root', () => {
    expect(() => validateTallyRequestXml('<ROOT></ROOT>', 65_536)).toThrow(AppError);
  });

  it('rejects XML with control characters', () => {
    const xml = builder.buildCompanyListRequest().replace('Companies', 'Comp\u0001anies');
    expect(() => validateTallyRequestXml(xml, 65_536)).toThrow(AppError);
  });

  it('rejects oversized requests', () => {
    const xml = builder.buildCompanyListRequest();
    expect(() => validateTallyRequestXml(xml, 32)).toThrow(AppError);
  });
});

describe('redactTallyRequestXml', () => {
  it('redacts company names from XML', () => {
    const redacted = redactTallyRequestXml(
      '<SVCURRENTCOMPANY>ESTIMATION</SVCURRENTCOMPANY><NAME>Secret Co</NAME>',
    );
    expect(redacted).toContain('[REDACTED]');
    expect(redacted).not.toContain('ESTIMATION');
    expect(redacted).not.toContain('Secret Co');
  });
});
