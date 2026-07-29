import { describe, expect, it } from 'vitest';

import {
  TallyXmlResponseParser,
  XmlParseError,
  type ParsedXmlNode,
} from '../../../src/tally/xml/response-parser.js';

const DISCOVERY_COLLECTION = `<COLLECTION NAME="Budcom Voucher Discovery" ISMODIFY="No" ISFIXED="No" ISINITIALIZE="Yes">
  <TYPE>Voucher</TYPE>
  <FILTERS>BudcomVoucherDateRange</FILTERS>
  <NATIVEMETHOD>Date</NATIVEMETHOD>
  <NATIVEMETHOD>VoucherTypeName</NATIVEMETHOD>
  <NATIVEMETHOD>VoucherNumber</NATIVEMETHOD>
  <NATIVEMETHOD>MasterID</NATIVEMETHOD>
  <NATIVEMETHOD>AlterID</NATIVEMETHOD>
  <NATIVEMETHOD>GUID</NATIVEMETHOD>
  <NATIVEMETHOD>IsCancelled</NATIVEMETHOD>
</COLLECTION>`;

const LEDGER_COLLECTIONS = `<COLLECTION NAME="Budcom Voucher Ledger Source" ISMODIFY="No" ISFIXED="No" ISINITIALIZE="Yes">
  <TYPE>Voucher</TYPE>
  <FILTERS>BudcomVoucherDateRange</FILTERS>
  <NATIVEMETHOD>GUID</NATIVEMETHOD>
</COLLECTION>
<COLLECTION NAME="Budcom Voucher Ledger Entries" ISMODIFY="No" ISFIXED="No" ISINITIALIZE="Yes">
  <SOURCECOLLECTION>Budcom Voucher Ledger Source</SOURCECOLLECTION>
  <WALK>AllLedgerEntries</WALK>
  <NATIVEMETHOD>LedgerName</NATIVEMETHOD>
  <NATIVEMETHOD>IsDeemedPositive</NATIVEMETHOD>
  <NATIVEMETHOD>Amount</NATIVEMETHOD>
  <COMPUTE>ParentGUID : $$Owner:$GUID</COMPUTE>
</COLLECTION>`;

const DISCOVERY_XML = envelope(`<VOUCHER>
  <DATE>20260724</DATE>
  <VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
  <VOUCHERNUMBER>16</VOUCHERNUMBER>
  <MASTERID>2</MASTERID>
  <GUID>voucher-sales</GUID>
</VOUCHER>
<VOUCHER>
  <DATE>20260724</DATE>
  <VOUCHERTYPENAME>Payment</VOUCHERTYPENAME>
  <VOUCHERNUMBER>4</VOUCHERNUMBER>
  <MASTERID>3</MASTERID>
  <GUID>voucher-payment</GUID>
</VOUCHER>`);

const LEDGER_XML = envelope(`<LEDGERENTRY>
  <LEDGERNAME>Fixture Customer</LEDGERNAME>
  <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
  <AMOUNT>-55.00</AMOUNT>
  <PARENTGUID>voucher-sales</PARENTGUID>
</LEDGERENTRY>
<LEDGERENTRY>
  <LEDGERNAME>Fixture Sales</LEDGERNAME>
  <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
  <AMOUNT>55.00</AMOUNT>
  <PARENTGUID>voucher-sales</PARENTGUID>
</LEDGERENTRY>
<LEDGERENTRY>
  <LEDGERNAME>Fixture Expense</LEDGERNAME>
  <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
  <AMOUNT>-125.25</AMOUNT>
  <PARENTGUID>voucher-payment</PARENTGUID>
</LEDGERENTRY>
<LEDGERENTRY>
  <LEDGERNAME>Fixture Bank</LEDGERNAME>
  <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
  <AMOUNT>125.25</AMOUNT>
  <PARENTGUID>voucher-payment</PARENTGUID>
</LEDGERENTRY>`);

describe('two-phase Voucher and ledger extraction design', () => {
  it('keeps discovery minimal, bounded, and independent from monetary child data', () => {
    expect(DISCOVERY_COLLECTION).toContain('<FILTERS>BudcomVoucherDateRange</FILTERS>');
    expect(DISCOVERY_COLLECTION).not.toMatch(
      /<NATIVEMETHOD>(?:Amount|AllLedgerEntries|LedgerEntries|AllInventoryEntries)/,
    );

    const parser = new TallyXmlResponseParser();
    const document = parser.parse(DISCOVERY_XML);
    expect(parser.findAll(document, 'VOUCHER')).toHaveLength(2);
    expect(parser.findAll(document, 'LEDGERENTRY')).toHaveLength(0);
  });

  it('walks a source collection and fetches only explicit ledger-entry methods', () => {
    expect(LEDGER_COLLECTIONS).toContain(
      '<SOURCECOLLECTION>Budcom Voucher Ledger Source</SOURCECOLLECTION>',
    );
    expect(LEDGER_COLLECTIONS).toContain('<WALK>AllLedgerEntries</WALK>');
    expect(LEDGER_COLLECTIONS).toContain('<COMPUTE>ParentGUID : $$Owner:$GUID</COMPUTE>');
    expect(LEDGER_COLLECTIONS).not.toMatch(
      /<NATIVEMETHOD>(?:AllLedgerEntries(?:\.|<)|LedgerEntries(?:\.|<)|AllInventoryEntries)/,
    );
  });

  it('joins flat ledger entries to their authoritative parent Voucher by owner GUID', () => {
    const result = extractTwoPhase(DISCOVERY_XML, LEDGER_XML);

    expect([...result.keys()]).toEqual(['voucher-sales', 'voucher-payment']);
    expect(result.get('voucher-sales')).toMatchObject({
      voucherType: 'Sales',
      voucherNumber: '16',
      displayTotal: '55.00',
    });
    expect(result.get('voucher-payment')).toMatchObject({
      voucherType: 'Payment',
      voucherNumber: '4',
      displayTotal: '125.25',
    });
    expect(result.get('voucher-sales')?.entries).toHaveLength(2);
    expect(result.get('voucher-payment')?.entries).toHaveLength(2);
  });

  it('reconciles signed Sales and Payment entries independently', () => {
    const result = extractTwoPhase(DISCOVERY_XML, LEDGER_XML);

    for (const voucher of result.values()) {
      expect(voucher.balanceMinor).toBe(0);
      expect(voucher.entries.every((entry) =>
        entry.isDeemedPositive === (entry.amountMinor < 0))).toBe(true);
    }
  });

  it.each([
    [
      'orphan owner',
      LEDGER_XML.replaceAll('voucher-payment', 'missing-voucher'),
      /unknown parent Voucher GUID/,
    ],
    [
      'unbalanced entries',
      LEDGER_XML.replace('<AMOUNT>125.25</AMOUNT>', '<AMOUNT>125.24</AMOUNT>'),
      /does not balance/,
    ],
    [
      'missing owner GUID',
      LEDGER_XML.replace('<PARENTGUID>voucher-sales</PARENTGUID>', ''),
      /missing PARENTGUID/,
    ],
  ])('fails closed for %s', (_name, ledgerXml, expected) => {
    expect(() => extractTwoPhase(DISCOVERY_XML, ledgerXml)).toThrow(expected);
  });

  it('rejects the illegal XML emitted by a broad direct compound fetch', () => {
    const malformedBroadExpansion = envelope(`<VOUCHER>
      <DATE>20260724</DATE>
      <ALLLEDGERENTRIES.LIST>
        <LEDGERNAME>Fixture Ledger</LEDGERNAME>
        <GSTCLASS>&#4;</GSTCLASS>
      </ALLLEDGERENTRIES.LIST>
    </VOUCHER>`);

    try {
      new TallyXmlResponseParser().parse(malformedBroadExpansion);
      expect.fail('broad compound expansion XML unexpectedly parsed');
    } catch (error) {
      expect(error).toBeInstanceOf(XmlParseError);
      expect((error as XmlParseError).reason).toBe('xml_illegal_character');
    }
  });
});

interface LedgerEntry {
  readonly ledgerName: string;
  readonly isDeemedPositive: boolean;
  readonly amountMinor: number;
}

interface ExtractedVoucher {
  readonly voucherType: string;
  readonly voucherNumber: string;
  readonly entries: LedgerEntry[];
  readonly balanceMinor: number;
  readonly displayTotal: string;
}

function extractTwoPhase(
  discoveryXml: string,
  ledgerXml: string,
): ReadonlyMap<string, ExtractedVoucher> {
  const parser = new TallyXmlResponseParser();
  const discovery = parser.parse(discoveryXml);
  const ledger = parser.parse(ledgerXml);
  const vouchers = new Map<string, {
    voucherType: string;
    voucherNumber: string;
    entries: LedgerEntry[];
  }>();

  for (const node of parser.findAll(discovery, 'VOUCHER')) {
    const guid = requiredText(node, 'GUID');
    if (vouchers.has(guid)) throw new Error(`duplicate Voucher GUID: ${guid}`);
    vouchers.set(guid, {
      voucherType: requiredText(node, 'VOUCHERTYPENAME'),
      voucherNumber: requiredText(node, 'VOUCHERNUMBER'),
      entries: [],
    });
  }

  for (const node of parser.findAll(ledger, 'LEDGERENTRY')) {
    const parentGuid = childText(node, 'PARENTGUID');
    if (!parentGuid) throw new Error('ledger entry is missing PARENTGUID');
    const voucher = vouchers.get(parentGuid);
    if (!voucher) throw new Error(`unknown parent Voucher GUID: ${parentGuid}`);
    const amountMinor = parseAmountMinor(requiredText(node, 'AMOUNT'));
    const isDeemedPositive = parseLogical(requiredText(node, 'ISDEEMEDPOSITIVE'));
    if (isDeemedPositive !== (amountMinor < 0)) {
      throw new Error(`ledger sign disagrees with IsDeemedPositive: ${parentGuid}`);
    }
    voucher.entries.push({
      ledgerName: requiredText(node, 'LEDGERNAME'),
      isDeemedPositive,
      amountMinor,
    });
  }

  return new Map([...vouchers].map(([guid, voucher]) => {
    const balanceMinor = voucher.entries.reduce((sum, entry) => sum + entry.amountMinor, 0);
    if (balanceMinor !== 0) throw new Error(`Voucher ${guid} does not balance`);
    const positiveMinor = voucher.entries.reduce(
      (sum, entry) => sum + Math.max(0, entry.amountMinor),
      0,
    );
    return [guid, {
      ...voucher,
      balanceMinor,
      displayTotal: (positiveMinor / 100).toFixed(2),
    }];
  }));
}

function envelope(records: string): string {
  return `<ENVELOPE>
  <HEADER><STATUS>1</STATUS></HEADER>
  <BODY><DATA><COLLECTION>${records}</COLLECTION></DATA></BODY>
</ENVELOPE>`;
}

function requiredText(node: ParsedXmlNode, name: string): string {
  const value = childText(node, name);
  if (!value) throw new Error(`${node.name} is missing ${name}`);
  return value;
}

function childText(node: ParsedXmlNode, name: string): string | undefined {
  return node.children.find((child) => child.name === name)?.text?.trim() || undefined;
}

function parseAmountMinor(value: string): number {
  if (!/^-?\d+(?:\.\d{1,2})?$/.test(value)) throw new Error(`invalid amount: ${value}`);
  const [whole, fraction = ''] = value.split('.');
  const sign = whole!.startsWith('-') ? -1 : 1;
  return (Math.abs(Number(whole)) * 100 + Number(fraction.padEnd(2, '0'))) * sign;
}

function parseLogical(value: string): boolean {
  if (/^yes$/i.test(value)) return true;
  if (/^no$/i.test(value)) return false;
  throw new Error(`invalid logical value: ${value}`);
}
