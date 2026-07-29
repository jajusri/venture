import { describe, expect, it, vi } from 'vitest';

import type { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import { ApprovedOperationId } from '../../../src/tally/registry/operation-registry.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { TallyVoucherExtractor } from '../../../src/tally/voucher/voucher-extractor.js';
import { VoucherXmlMapper } from '../../../src/tally/voucher/voucher-mapper.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';
import {
  APPROVED_VOUCHER_FIXTURE_XML,
  EMPTY_VOUCHER_PERIOD_XML,
  MISSING_COLLECTION_XML,
  SOURCE_ERROR_XML,
} from '../../fixtures/vouchers/approved-voucher-fixture.js';

function extractorFor(xml: string): TallyVoucherExtractor {
  const gateway = {
    executeApprovedRead: vi.fn().mockResolvedValue({
      operationId: ApprovedOperationId.Vouchers,
      rawXml: xml,
      byteLength: Buffer.byteLength(xml),
      durationMs: 7,
    }),
  } as unknown as TallyReadGateway;
  const parser = new VoucherCollectionParser(new TallyXmlResponseParser());
  return new TallyVoucherExtractor(gateway, parser, new VoucherXmlMapper(parser));
}

const PERIOD = { dateFrom: '2026-07-27', dateTo: '2026-07-27' };

describe('TallyVoucherExtractor', () => {
  it('extracts all eight approved fixture records with explicit metrics', async () => {
    const result = await extractorFor(APPROVED_VOUCHER_FIXTURE_XML)
      .readVouchers('ESTIMATION', PERIOD);

    expect(result).toMatchObject({
      candidateRecordCount: 8,
      droppedRecordCount: 0,
      responseStatus: 'records',
      validationIssues: [],
    });
    expect(result.items).toHaveLength(8);
  });

  it('returns a valid empty period explicitly', async () => {
    await expect(
      extractorFor(EMPTY_VOUCHER_PERIOD_XML).readVouchers('ESTIMATION', PERIOD),
    ).resolves.toMatchObject({
      items: [],
      candidateRecordCount: 0,
      droppedRecordCount: 0,
      responseStatus: 'empty',
    });
  });

  it('does not treat company-wide CMPINFO/VOUCHER as the bounded response count', async () => {
    const xml = APPROVED_VOUCHER_FIXTURE_XML.replace(
      '<VOUCHER>8</VOUCHER>',
      '<VOUCHER>9999</VOUCHER>',
    );
    await expect(extractorFor(xml).readVouchers('ESTIMATION', PERIOD)).resolves.toMatchObject({
      candidateRecordCount: 8,
      droppedRecordCount: 0,
      responseStatus: 'records',
    });
  });

  it.each([
    [MISSING_COLLECTION_XML, 'missing-collection'],
    [SOURCE_ERROR_XML, 'tally-source-error'],
  ])('rejects parser failures instead of returning empty success', async (xml, reasonCode) => {
    await expect(
      extractorFor(xml).readVouchers('ESTIMATION', PERIOD),
    ).rejects.toMatchObject({
      statusCode: 422,
      details: { reasonCode },
    });
  });

  it('reports rejected records and their validation issues', async () => {
    const xml = `<ENVELOPE><HEADER><STATUS>1</STATUS></HEADER><BODY><DATA><COLLECTION>
      <VOUCHER><DATE>20260727</DATE><VOUCHERTYPENAME>Sales</VOUCHERTYPENAME></VOUCHER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const result = await extractorFor(xml).readVouchers('ESTIMATION', PERIOD);

    expect(result).toMatchObject({
      items: [],
      candidateRecordCount: 1,
      droppedRecordCount: 1,
      responseStatus: 'partial',
    });
    expect(result.validationIssues).toEqual([
      expect.objectContaining({ code: 'MISSING_STABLE_IDENTITY' }),
    ]);
  });

  it('keeps accepted incomplete records distinct from a partial source response', async () => {
    const xml = `<ENVELOPE><HEADER><STATUS>1</STATUS></HEADER><BODY><DATA><COLLECTION>
      <VOUCHER>
        <DATE>20260727</DATE><VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
        <GUID>fixture-incomplete-guid</GUID>
        <ALLINVENTORYENTRIES.LIST><AMOUNT>10.00</AMOUNT></ALLINVENTORYENTRIES.LIST>
      </VOUCHER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const result = await extractorFor(xml).readVouchers('ESTIMATION', PERIOD);

    expect(result).toMatchObject({
      candidateRecordCount: 1,
      droppedRecordCount: 0,
      responseStatus: 'records',
    });
    expect(result.items[0]?.dataQuality).toBe('incomplete');
    expect(result.validationIssues).toEqual([
      expect.objectContaining({
        classification: 'incomplete',
        field: 'inventoryEntries[0].itemName',
      }),
    ]);
  });
});
