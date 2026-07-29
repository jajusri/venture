import { describe, expect, it, vi } from 'vitest';

import type { VoucherReadPort } from '../../../src/erp/ports/vouchers.js';
import { VoucherExtractionService } from '../../../src/services/voucher/voucher-extraction.service.js';
import type { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import {
  ApprovedOperationId,
  getApprovedOperation,
} from '../../../src/tally/registry/operation-registry.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { TallyVoucherExtractor } from '../../../src/tally/voucher/voucher-extractor.js';
import { VoucherXmlMapper } from '../../../src/tally/voucher/voucher-mapper.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';
import {
  PRODUCTION_VOUCHER_COLLECTION_NAME,
  PRODUCTION_VOUCHER_FETCH_METHODS,
} from '../../../src/tally/voucher/voucher-request.js';

const VOUCHER_XML = `<ENVELOPE>
  <HEADER><STATUS>1</STATUS></HEADER>
  <BODY>
    <DESC><CMPINFO><VOUCHER>1</VOUCHER></CMPINFO></DESC>
    <DATA>
      <COLLECTION>
        <VOUCHER>
          <DATE>20260727</DATE>
          <EFFECTIVEDATE>20260727</EFFECTIVEDATE>
          <GUID>voucher-guid-1</GUID>
          <MASTERID>10</MASTERID>
          <ALTERID>11</ALTERID>
          <VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
          <VOUCHERNUMBER>1</VOUCHERNUMBER>
          <PARTYLEDGERNAME>Customer</PARTYLEDGERNAME>
          <REFERENCE>REF-1</REFERENCE>
          <NARRATION>Test narration</NARRATION>
          <AMOUNT>-100.00</AMOUNT>
          <ISCANCELLED>NO</ISCANCELLED>
          <ALLLEDGERENTRIES.LIST>
            <LEDGERNAME>Customer</LEDGERNAME>
            <AMOUNT>-100.00</AMOUNT>
            <ISDEEMEDPOSITIVE>YES</ISDEEMEDPOSITIVE>
          </ALLLEDGERENTRIES.LIST>
          <ALLINVENTORYENTRIES.LIST>
            <STOCKITEMNAME>Item</STOCKITEMNAME>
            <ACTUALQTY>2 PCS</ACTUALQTY>
            <BILLEDQTY>2 PCS</BILLEDQTY>
            <RATE>50/PCS</RATE>
            <AMOUNT>100.00</AMOUNT>
          </ALLINVENTORYENTRIES.LIST>
        </VOUCHER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

describe('production Voucher extraction foundation', () => {
  it('registers the proven embedded collection as a bounded read-only operation', () => {
    const operation = getApprovedOperation(ApprovedOperationId.Vouchers);
    const spec = operation.renderEmbeddedCollection({
      companyName: 'ESTIMATION',
      dateFrom: '2026-07-27',
      dateTo: '2026-07-27',
    });

    expect(operation).toMatchObject({
      tallyRequest: 'Export',
      requestKind: 'Collection',
      tallyId: PRODUCTION_VOUCHER_COLLECTION_NAME,
      classification: 'VERIFIED_SAFE',
      requiresCompany: true,
    });
    expect(spec.collection).toMatchObject({
      name: PRODUCTION_VOUCHER_COLLECTION_NAME,
      objectType: 'Voucher',
      fetch: PRODUCTION_VOUCHER_FETCH_METHODS,
      attributes: { ISFIXED: 'No', ISINITIALIZE: 'Yes' },
    });
    expect(spec.staticVariables).toMatchObject({
      SVCURRENTCOMPANY: 'ESTIMATION',
      SVFROMDATE: '20260727',
      SVTODATE: '20260727',
    });
    expect(spec.collection.filters).toEqual(['BudcomVoucherDateRange']);
    expect(spec.systemFormulae).toEqual({
      BudcomVoucherDateRange:
        '$Date >= $$Date:"27-Jul-2026" AND $Date <= $$Date:"27-Jul-2026"',
    });
  });

  it.each([
    [{ companyName: '', dateFrom: '2026-07-27', dateTo: '2026-07-27' }, /company/i],
    [{ companyName: 'ESTIMATION', dateFrom: 'bad', dateTo: '2026-07-27' }, /dates/i],
    [{ companyName: 'ESTIMATION', dateFrom: '2026-07-28', dateTo: '2026-07-27' }, /follow/i],
    [{ companyName: 'ESTIMATION', dateFrom: '2025-01-01', dateTo: '2026-01-02' }, /366/i],
  ])('rejects unbounded or malformed production request parameters', (params, error) => {
    const operation = getApprovedOperation(ApprovedOperationId.Vouchers);
    expect(() => operation.renderEmbeddedCollection(params)).toThrow(error);
  });

  it('parses only direct DATA/COLLECTION Voucher records', () => {
    const parser = new VoucherCollectionParser(new TallyXmlResponseParser());
    const document = parser.parseDocument(VOUCHER_XML);
    const records = parser.collectVoucherRecords(document);

    expect(records).toHaveLength(1);
    expect(parser.childText(records[0]!, 'GUID')).toBe('voucher-guid-1');
  });

  it('maps Voucher XML into the source-neutral domain model', () => {
    const parser = new VoucherCollectionParser(new TallyXmlResponseParser());
    const mapper = new VoucherXmlMapper(parser);
    const record = parser.collectVoucherRecords(parser.parseDocument(VOUCHER_XML))[0]!;

    expect(mapper.map(record).value).toMatchObject({
      voucherId: 'voucher-guid-1',
      identityVersion: 'unproven',
      guid: 'voucher-guid-1',
      masterId: '10',
      alterId: '11',
      date: '2026-07-27',
      voucherType: 'Sales',
      voucherNumber: '1',
      partyName: 'Customer',
      amount: { amount: '100', side: 'credit' },
      amountComparable: false,
      status: 'active',
      dataQuality: 'complete',
      referenceNumber: 'REF-1',
      narrationPreview: 'Test narration',
      effectiveDate: '2026-07-27',
      narration: 'Test narration',
      ledgerEntries: [
        {
          lineNumber: 1,
          ledgerName: 'Customer',
          amount: { amount: '100', side: 'credit' },
          isDeemedPositive: true,
          allocations: [],
        },
      ],
      inventoryEntries: [
        {
          lineNumber: 1,
          itemName: 'Item',
          quantity: '2 PCS',
          actualQuantity: '2 PCS',
          billedQuantity: '2 PCS',
          rate: '50/PCS',
          amount: { amount: '100', side: 'debit' },
          allocations: [],
        },
      ],
      allocations: [],
    });
  });

  it('extracts through the approved gateway without persistence', async () => {
    const executeApprovedRead = vi.fn().mockResolvedValue({
      operationId: ApprovedOperationId.Vouchers,
      rawXml: VOUCHER_XML,
      byteLength: Buffer.byteLength(VOUCHER_XML),
      durationMs: 12,
    });
    const gateway = { executeApprovedRead } as unknown as TallyReadGateway;
    const parser = new VoucherCollectionParser(new TallyXmlResponseParser());
    const extractor = new TallyVoucherExtractor(
      gateway,
      parser,
      new VoucherXmlMapper(parser),
    );

    const result = await extractor.readVouchers('ESTIMATION', {
      dateFrom: '2026-07-27',
      dateTo: '2026-07-27',
    });

    expect(executeApprovedRead).toHaveBeenCalledWith({
      operationId: ApprovedOperationId.Vouchers,
      companyName: 'ESTIMATION',
      dateFrom: '2026-07-27',
      dateTo: '2026-07-27',
      signal: undefined,
    });
    expect(result).toMatchObject({
      candidateRecordCount: 1,
      droppedRecordCount: 0,
      responseStatus: 'records',
      durationMs: 12,
    });
    expect(result.items).toHaveLength(1);
  });

  it('exposes an application extraction service over the ERP-neutral read port', async () => {
    const readVouchers = vi.fn().mockResolvedValue({
      items: [],
      candidateRecordCount: 0,
      droppedRecordCount: 0,
      validationIssues: [],
      responseStatus: 'empty',
      durationMs: 1,
      rawByteLength: 100,
    });
    const source = { readVouchers } as VoucherReadPort;
    const service = new VoucherExtractionService(source);

    await service.extract({
      companyName: 'ESTIMATION',
      period: { dateFrom: '2026-07-27', dateTo: '2026-07-27' },
    });
    expect(readVouchers).toHaveBeenCalledWith(
      'ESTIMATION',
      { dateFrom: '2026-07-27', dateTo: '2026-07-27' },
    );
  });
});
