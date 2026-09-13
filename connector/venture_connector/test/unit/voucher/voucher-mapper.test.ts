import { describe, expect, it } from 'vitest';

import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { VoucherXmlMapper } from '../../../src/tally/voucher/voucher-mapper.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';
import { APPROVED_VOUCHER_FIXTURE_XML } from '../../fixtures/vouchers/approved-voucher-fixture.js';

function setup() {
  const parser = new VoucherCollectionParser(new TallyXmlResponseParser());
  return { parser, mapper: new VoucherXmlMapper(parser) };
}

function recordXml(contents: string): string {
  return `<ENVELOPE><HEADER><STATUS>1</STATUS></HEADER><BODY><DATA><COLLECTION><VOUCHER>${contents}</VOUCHER></COLLECTION></DATA></BODY></ENVELOPE>`;
}

describe('VoucherXmlMapper', () => {
  it('maps all eight approved Voucher types and accounting-only records', () => {
    const { parser, mapper } = setup();
    const parsed = parser.parse(APPROVED_VOUCHER_FIXTURE_XML);
    expect(parsed.status).toBe('records');
    if (parsed.status !== 'records') return;

    const results = parsed.records.map((record) => mapper.map(record));
    expect(results.every((result) => result.accepted)).toBe(true);
    expect(results.flatMap((result) => result.value?.voucherType ?? [])).toEqual([
      'Purchase',
      'Sales',
      'Receipt',
      'Payment',
      'Debit Note',
      'Credit Note',
      'Contra',
      'Journal',
    ]);
    const journal = results[7]!.value!;
    expect(journal.ledgerEntries).toHaveLength(1);
    expect(journal.inventoryEntries).toHaveLength(0);
  });

  it('maps complete identity, header, state, and amount semantics', () => {
    const { parser, mapper } = setup();
    const parsed = parser.parse(APPROVED_VOUCHER_FIXTURE_XML);
    if (parsed.status !== 'records') throw new Error('fixture did not parse');
    const sales = mapper.map(parsed.records[1]!).value!;
    const cancelled = mapper.map(parsed.records[5]!).value!;

    expect(sales).toMatchObject({
      voucherId: 'fixture-guid-2',
      guid: 'fixture-guid-2',
      masterId: '2',
      alterId: '102',
      voucherKey: 'fixture-key-2',
      voucherRetainKey: 'fixture-retain-2',
      date: '2026-07-27',
      effectiveDate: '2026-07-27',
      voucherType: 'Sales',
      voucherNumber: '2',
      referenceNumber: 'Reference-2',
      narration: 'Fixture narration 2',
      partyName: 'Fixture Party',
      amount: { amount: '100', side: 'credit' },
      status: 'active',
    });
    expect(cancelled.status).toBe('cancelled');
    expect(sales.ledgerEntries[0]).toMatchObject({
      ledgerName: 'Fixture Ledger',
      amount: { amount: '100', side: 'credit' },
      isDeemedPositive: true,
    });
    expect(sales.inventoryEntries[0]).toMatchObject({
      itemName: 'Fixture Item',
      actualQuantity: '2 PCS',
      billedQuantity: '2 PCS',
      rate: '50/PCS',
      amount: { amount: '100', side: 'debit' },
    });
  });

  it('recognizes and losslessly preserves every supported nested allocation', () => {
    const { parser, mapper } = setup();
    const parsed = parser.parse(APPROVED_VOUCHER_FIXTURE_XML);
    if (parsed.status !== 'records') throw new Error('fixture did not parse');
    const sales = mapper.map(parsed.records[1]!).value!;

    expect(sales.allocations.map((allocation) => allocation.type).sort()).toEqual([
      'accounting',
      'bank',
      'batch',
      'bill',
      'cost-track',
      'inventory',
    ]);
    expect(sales.ledgerEntries[0]!.allocations).toHaveLength(3);
    expect(sales.inventoryEntries[0]!.allocations).toHaveLength(3);
    expect(sales.inventoryEntries[0]!.allocations[0]).toMatchObject({
      type: 'accounting',
      sourceName: 'ACCOUNTINGALLOCATIONS.LIST',
      values: [
        { name: 'LEDGERNAME', value: 'Sales', attributes: {}, children: [] },
        { name: 'AMOUNT', value: '100.00', attributes: {}, children: [] },
      ],
    });
  });

  it.each([
    [
      '<DATE>20260727</DATE><VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>',
      'MISSING_STABLE_IDENTITY',
    ],
    [
      '<GUID>g</GUID><DATE>20260230</DATE><VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>',
      'INVALID_DATE',
    ],
    [
      '<GUID>g</GUID><DATE>20260727</DATE>',
      'MISSING_TYPE_REQUIRED_FIELD',
    ],
  ])('rejects invalid Voucher records with explicit issues', (contents, code) => {
    const { parser, mapper } = setup();
    const parsed = parser.parse(recordXml(contents));
    if (parsed.status !== 'records') throw new Error('record did not parse');
    const result = mapper.map(parsed.records[0]!);

    expect(result.accepted).toBe(false);
    expect(result.value).toBeNull();
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code, classification: 'rejected' }),
    ]));
  });

  it('classifies malformed entries and inconsistent quantities without hiding the record', () => {
    const { parser, mapper } = setup();
    const parsed = parser.parse(recordXml(`
      <GUID>g</GUID><DATE>20260727</DATE><VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
      <ALLLEDGERENTRIES.LIST><AMOUNT>bad</AMOUNT></ALLLEDGERENTRIES.LIST>
      <ALLINVENTORYENTRIES.LIST>
        <STOCKITEMNAME>Item</STOCKITEMNAME>
        <ACTUALQTY>2 PCS</ACTUALQTY><BILLEDQTY>2 BOX</BILLEDQTY>
        <AMOUNT>bad</AMOUNT>
      </ALLINVENTORYENTRIES.LIST>
    `));
    if (parsed.status !== 'records') throw new Error('record did not parse');
    const result = mapper.map(parsed.records[0]!);

    expect(result.accepted).toBe(true);
    expect(result.value?.dataQuality).toBe('incomplete');
    expect(result.issues.map((issue) => issue.code)).toEqual(expect.arrayContaining([
      'MISSING_LEDGER_NAME',
      'INVALID_AMOUNT',
      'INCONSISTENT_QUANTITIES',
    ]));
  });

  it('ignores Tally empty inventory collection placeholders on accounting-only Vouchers', () => {
    const { parser, mapper } = setup();
    const parsed = parser.parse(recordXml(`
      <GUID>accounting-only</GUID>
      <DATE>20260727</DATE>
      <VOUCHERTYPENAME>Journal</VOUCHERTYPENAME>
      <ALLINVENTORYENTRIES.LIST/>
      <ALLLEDGERENTRIES.LIST>
        <LEDGERNAME>Fixture Ledger</LEDGERNAME>
        <AMOUNT>10.00</AMOUNT>
      </ALLLEDGERENTRIES.LIST>
    `));
    if (parsed.status !== 'records') throw new Error('record did not parse');
    const result = mapper.map(parsed.records[0]!);

    expect(result.accepted).toBe(true);
    expect(result.value?.inventoryEntries).toEqual([]);
    expect(result.value?.dataQuality).toBe('complete');
    expect(result.issues).toEqual([]);
  });
});
