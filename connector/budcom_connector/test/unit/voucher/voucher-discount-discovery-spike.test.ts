import { describe, expect, it } from 'vitest';

import type { ParsedXmlNode } from '../../../src/tally/xml/response-parser.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';

describe('experimental Voucher discount discovery spike', () => {
  it.each(['Discount', 'BatchDiscount'])(
    'builds a bounded inventory walk for the explicit %s method',
    (method) => {
      const xml = new TallyXmlRequestBuilder().buildEmbeddedCollection({
        collection: {
          name: `Budcom Discount Probe ${method}`,
          sourceCollection: 'Budcom Discount Source',
          walk: 'AllInventoryEntries',
          fetch: [
            'StockItemName',
            'ActualQty',
            'BilledQty',
            'Rate',
            'Amount',
            method,
          ],
          compute: { ParentGUID: '$$Owner:$GUID' },
        },
        supportingCollections: [{
          name: 'Budcom Discount Source',
          objectType: 'Voucher',
          fetch: ['GUID'],
          filters: ['BudcomVoucherDateRange'],
        }],
        staticVariables: {
          SVEXPORTFORMAT: '$$SysName:XML',
          SVCURRENTCOMPANY: 'Budcom-Test-01',
          SVFROMDATE: '20260724',
          SVTODATE: '20260724',
        },
        systemFormulae: {
          BudcomVoucherDateRange:
            '$Date >= $$Date:"24-Jul-2026" AND $Date <= $$Date:"24-Jul-2026"',
        },
      });

      expect(xml).toContain('<WALK>AllInventoryEntries</WALK>');
      expect(xml).toContain(`<NATIVEMETHOD>${method}</NATIVEMETHOD>`);
      expect(xml).not.toMatch(
        /<NATIVEMETHOD>(?:Voucher\.?Amount|AllInventoryEntries(?:\.|<)|.*\*)/i,
      );
    },
  );

  it('parses an explicit Discount value without deriving its meaning', () => {
    expect(parseCandidate(envelope(inventoryRow('<DISCOUNT>50</DISCOUNT>')), 'DISCOUNT'))
      .toEqual({
        rawValue: '50',
        parentGuid: 'voucher-guid',
        present: true,
      });
  });

  it('distinguishes explicit zero from an absent discount method', () => {
    expect(parseCandidate(envelope(inventoryRow('<DISCOUNT>0</DISCOUNT>')), 'DISCOUNT'))
      .toMatchObject({ rawValue: '0', present: true });
    expect(parseCandidate(envelope(inventoryRow('')), 'DISCOUNT'))
      .toMatchObject({ rawValue: null, present: false });
  });

  it('does not infer Discount from quantity, rate, amount, or balancing values', () => {
    const row = parseCandidate(
      envelope(inventoryRow('', {
        billedQuantity: '1 PCS',
        rate: '100/PCS',
        amount: '50',
      })),
      'DISCOUNT',
    );
    expect(row).toMatchObject({ rawValue: null, present: false });
  });
});

function parseCandidate(
  rawXml: string,
  method: 'DISCOUNT' | 'BATCHDISCOUNT',
): { readonly rawValue: string | null; readonly parentGuid: string; readonly present: boolean } {
  const parser = new TallyXmlResponseParser();
  const document = parser.parse(rawXml);
  const row = parser.findAll(document, 'INVENTORYENTRY')[0];
  if (!row) throw new Error('Discount spike fixture has no inventory row.');
  const candidate = directChild(row, method);
  const parentGuid = directChild(row, 'PARENTGUID')?.text?.trim();
  if (!parentGuid) throw new Error('Discount spike fixture has no parent GUID.');
  return {
    rawValue: candidate?.text?.trim() || null,
    parentGuid,
    present: candidate !== undefined,
  };
}

function directChild(node: ParsedXmlNode, name: string): ParsedXmlNode | undefined {
  return node.children.find((child) => child.name === name);
}

function envelope(records: string): string {
  return `<ENVELOPE><HEADER><STATUS>1</STATUS></HEADER><BODY><DATA><COLLECTION>${records}</COLLECTION></DATA></BODY></ENVELOPE>`;
}

function inventoryRow(
  candidate: string,
  values: {
    readonly billedQuantity?: string;
    readonly rate?: string;
    readonly amount?: string;
  } = {},
): string {
  return `<INVENTORYENTRY>
    <STOCKITEMNAME>Controlled Item</STOCKITEMNAME>
    <ACTUALQTY>${values.billedQuantity ?? '1 PCS'}</ACTUALQTY>
    <BILLEDQTY>${values.billedQuantity ?? '1 PCS'}</BILLEDQTY>
    <RATE>${values.rate ?? '100/PCS'}</RATE>
    <AMOUNT>${values.amount ?? '50'}</AMOUNT>
    ${candidate}
    <PARENTGUID>voucher-guid</PARENTGUID>
  </INVENTORYENTRY>`;
}
