import { describe, expect, it } from 'vitest';

import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';
import {
  APPROVED_VOUCHER_FIXTURE_XML,
  EMPTY_VOUCHER_PERIOD_XML,
  MALFORMED_VOUCHER_XML,
  MISSING_COLLECTION_XML,
  SOURCE_ERROR_XML,
} from '../../fixtures/vouchers/approved-voucher-fixture.js';

function parser(): VoucherCollectionParser {
  return new VoucherCollectionParser(new TallyXmlResponseParser());
}

describe('VoucherCollectionParser', () => {
  it('returns the eight approved fixture records without counting CMPINFO metadata', () => {
    const outcome = parser().parse(APPROVED_VOUCHER_FIXTURE_XML);
    expect(outcome).toMatchObject({
      status: 'records',
      declaredRecordCount: null,
    });
    if (outcome.status === 'records') expect(outcome.records).toHaveLength(8);
  });

  it('distinguishes a valid empty period from a missing Collection', () => {
    expect(parser().parse(EMPTY_VOUCHER_PERIOD_XML)).toMatchObject({
      status: 'empty',
      declaredRecordCount: null,
      records: [],
    });
    expect(parser().parse(MISSING_COLLECTION_XML)).toEqual({
      status: 'failure',
      code: 'missing-collection',
      message: 'Voucher response is missing BODY/DATA/COLLECTION.',
    });
  });

  it('classifies malformed XML and Tally source errors explicitly', () => {
    expect(parser().parse(MALFORMED_VOUCHER_XML)).toMatchObject({
      status: 'failure',
      code: 'malformed-xml',
    });
    expect(parser().parse(SOURCE_ERROR_XML)).toMatchObject({
      status: 'failure',
      code: 'tally-source-error',
    });
  });

  it('ignores company-wide CMPINFO/VOUCHER when assessing a bounded Collection', () => {
    const xml = APPROVED_VOUCHER_FIXTURE_XML.replace(
      '<VOUCHER>8</VOUCHER>',
      '<VOUCHER>9999</VOUCHER>',
    );
    const outcome = parser().parse(xml);
    expect(outcome).toMatchObject({
      status: 'records',
      declaredRecordCount: null,
    });
    if (outcome.status === 'records') expect(outcome.records).toHaveLength(8);
  });

  it.each([
    ['<ROOT></ROOT>', 'missing-envelope'],
    ['<ENVELOPE><BODY></BODY></ENVELOPE>', 'missing-header'],
    ['<ENVELOPE><HEADER></HEADER></ENVELOPE>', 'missing-body'],
    [
      '<ENVELOPE><HEADER></HEADER><BODY></BODY></ENVELOPE>',
      'missing-data',
    ],
  ])('validates required envelope structure', (xml, code) => {
    expect(parser().parse(xml)).toMatchObject({ status: 'failure', code });
  });
});
