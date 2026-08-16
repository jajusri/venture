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
      xmlParseDetail: null,
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

  // Diagnostic hardening (2026-08-16, TD-001 physical retest FAIL follow-up): a generic
  // 'malformed-xml' bucket used to discard which of the six distinct XmlParseError
  // reasons actually fired (xml_malformed / xml_trailing_content / xml_max_depth_exceeded
  // / xml_max_node_count_exceeded / xml_oversized), and whether illegal-character
  // sanitization even ran before the structural break. Both now survive in
  // `xmlParseDetail`, privacy-safe (reason + line/column/byteOffset-style structural
  // facts only, never surrounding business text).
  it('preserves the specific XmlParseError reason and structural details, not just the generic malformed-xml bucket', () => {
    const outcome = parser().parse(MALFORMED_VOUCHER_XML);
    expect(outcome).toMatchObject({ status: 'failure', code: 'malformed-xml' });
    if (outcome.status !== 'failure') throw new Error('expected failure');
    expect(outcome.xmlParseDetail).toMatchObject({ reason: 'xml_malformed' });
    expect(JSON.stringify(outcome.xmlParseDetail)).not.toContain('STATUS');
  });

  it('preserves illegalCharactersSanitized in xmlParseDetail even when the document is sanitized but still structurally broken', () => {
    // The TD-001 artifact is present and gets sanitized, but the document is ALSO
    // missing its closing tags -- proving sanitization succeeding does not mask a
    // genuinely separate structural problem, and that this distinction is now visible
    // in diagnostics rather than both collapsing to the same generic bucket.
    const sanitizedButStillBroken = '<ENVELOPE><HEADER><STATUS>1</STATUS></HEADER><BODY><DATA><COLLECTION><VOUCHER><PARTYLEDGERNAME>&#4; Primary</PARTYLEDGERNAME>';
    const outcome = parser().parse(sanitizedButStillBroken);
    expect(outcome).toMatchObject({ status: 'failure', code: 'malformed-xml' });
    if (outcome.status !== 'failure') throw new Error('expected failure');
    expect(outcome.xmlParseDetail).toMatchObject({
      reason: 'xml_malformed',
      illegalCharactersSanitized: 1,
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

  // Controlled-pilot Session 2 physical failure (2026-08-16, company ESTIMATION,
  // Android sync run: "Vouchers - Failed, Processed 0, Error: parser_failure").
  // TD-001 already documented this exact Tally artifact -- "&#4; Primary" -- appearing
  // in ESTIMATION's live Group-parent export data. Originally, this caused a hard
  // 'malformed-xml' parse rejection for any Voucher carrying it in a free-text field
  // (PARTYLEDGERNAME/NARRATION/REFERENCE/VOUCHERTYPENAME), which voucher-extractor.ts
  // mapped to VALIDATION_ERROR and voucher-snapshot-sync.service.ts's
  // classifyExtractionFailure() collapsed to the 'parser_failure' reason code observed
  // on the physical device. Fixed (2026-08-16, approved architectural decision): the
  // shared TallyXmlResponseParser now sanitizes XML-1.0-illegal characters -- including
  // this exact artifact -- before structural parsing, so the same Voucher now parses
  // successfully with the artifact removed rather than being rejected outright.
  it('sanitizes the TD-001 &#4; artifact in a free-text field and parses the Voucher successfully', () => {
    const xmlWithKnownTallyArtifact = `<ENVELOPE>
  <HEADER><STATUS>1</STATUS></HEADER>
  <BODY><DATA><COLLECTION>
    <VOUCHER>
      <DATE>20260810</DATE>
      <GUID>fixture-guid-td001</GUID>
      <VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
      <VOUCHERNUMBER>1</VOUCHERNUMBER>
      <PARTYLEDGERNAME>&#4; Primary</PARTYLEDGERNAME>
      <NARRATION>Fixture narration</NARRATION>
    </VOUCHER>
  </COLLECTION></DATA></BODY>
</ENVELOPE>`;

    const outcome = parser().parse(xmlWithKnownTallyArtifact);
    expect(outcome).toMatchObject({ status: 'records' });
    if (outcome.status !== 'records') throw new Error('expected records');
    expect(outcome.records).toHaveLength(1);
    expect(outcome.document.illegalCharactersSanitized).toBe(1);
    const partyLedgerName = new TallyXmlResponseParser()
      .findFirst(outcome.document, 'PARTYLEDGERNAME')?.text;
    expect(partyLedgerName).toBe('Primary');
  });
});
