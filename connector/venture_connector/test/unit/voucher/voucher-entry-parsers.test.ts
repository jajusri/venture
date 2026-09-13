import { describe, expect, it } from 'vitest';

import { VoucherEntryParseError } from '../../../src/tally/voucher/voucher-entry-parse-error.js';
import { VoucherLedgerEntryParser } from '../../../src/tally/voucher/voucher-ledger-parser.js';
import { VoucherInventoryEntryParser } from '../../../src/tally/voucher/voucher-inventory-parser.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';

// Controlled-pilot Session 2 retest FAIL follow-up (2026-08-16): the 0.4.10 physical
// failure audit showed reasonCode 'voucher-ledger-validation' with NEITHER an
// XmlParseError nor a VoucherReconciliationError attached -- proving the thrown error
// came from inside VoucherLedgerEntryParser itself, which (like
// VoucherInventoryEntryParser) previously threw only plain, untyped `Error`s. These
// tests prove every one of that parser's failure branches is now individually
// distinguishable via VoucherEntryParseError.reason, so the next physical retest's
// audit entry will show exactly which branch fired instead of collapsing to one
// opaque bucket again.

function ledgerParser(): VoucherLedgerEntryParser {
  return new VoucherLedgerEntryParser(new TallyXmlResponseParser());
}

function inventoryParser(): VoucherInventoryEntryParser {
  return new VoucherInventoryEntryParser(new TallyXmlResponseParser());
}

function envelope(records: string, options: {
  readonly omitHeader?: boolean;
  readonly omitBody?: boolean;
  readonly omitData?: boolean;
  readonly omitCollection?: boolean;
} = {}): string {
  const header = options.omitHeader ? '' : '<HEADER><STATUS>1</STATUS></HEADER>';
  const collection = options.omitCollection ? records : `<COLLECTION>${records}</COLLECTION>`;
  const data = options.omitData ? '' : `<DATA>${collection}</DATA>`;
  const body = options.omitBody ? '' : `<BODY>${data}</BODY>`;
  return `<ENVELOPE>${header}${body}</ENVELOPE>`;
}

function expectReason(fn: () => unknown, reason: string): void {
  try {
    fn();
    throw new Error('expected VoucherEntryParseError to be thrown');
  } catch (error) {
    expect(error).toBeInstanceOf(VoucherEntryParseError);
    expect((error as VoucherEntryParseError).reason).toBe(reason);
  }
}

const VALID_LEDGER_ENTRY = `<LEDGERENTRY>
  <LEDGERNAME>Customer</LEDGERNAME>
  <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
  <AMOUNT>-100.00</AMOUNT>
  <PARENTGUID>voucher-guid</PARENTGUID>
</LEDGERENTRY>`;

const VALID_INVENTORY_ENTRY = `<INVENTORYENTRY>
  <STOCKITEMNAME>Fixture Item</STOCKITEMNAME>
  <AMOUNT>-100.00</AMOUNT>
  <PARENTGUID>voucher-guid</PARENTGUID>
</INVENTORYENTRY>`;

describe('VoucherLedgerEntryParser -- distinguishable failure reasons', () => {
  it('parses a well-formed, agreeing entry without error (control case)', () => {
    expect(ledgerParser().parse(envelope(VALID_LEDGER_ENTRY))).toEqual([{
      parentGuid: 'voucher-guid',
      ledgerName: 'Customer',
      isDeemedPositive: true,
      signedAmount: '-100.00',
      amountSignConflict: false,
    }]);
  });

  it('invalid-root: document root is not ENVELOPE', () => {
    expectReason(
      () => ledgerParser().parse('<ROOT><HEADER/><BODY/></ROOT>'),
      'invalid-root',
    );
  });

  it('missing-header: ENVELOPE has no HEADER child', () => {
    expectReason(
      () => ledgerParser().parse(envelope(VALID_LEDGER_ENTRY, { omitHeader: true })),
      'missing-header',
    );
  });

  it('missing-body: ENVELOPE has no BODY child', () => {
    expectReason(
      () => ledgerParser().parse(envelope(VALID_LEDGER_ENTRY, { omitBody: true })),
      'missing-body',
    );
  });

  it('missing-data: BODY has no DATA child', () => {
    expectReason(
      () => ledgerParser().parse(envelope(VALID_LEDGER_ENTRY, { omitData: true })),
      'missing-data',
    );
  });

  it('missing-collection: DATA has no COLLECTION child', () => {
    expectReason(
      () => ledgerParser().parse(envelope(VALID_LEDGER_ENTRY, { omitCollection: true })),
      'missing-collection',
    );
  });

  it('tally-line-error: Tally returned a LINEERROR', () => {
    expectReason(
      () => ledgerParser().parse(envelope('<LINEERROR>Some Tally-side failure</LINEERROR>')),
      'tally-line-error',
    );
  });

  it('missing-parent-guid: PARENTGUID absent', () => {
    const entry = VALID_LEDGER_ENTRY.replace('<PARENTGUID>voucher-guid</PARENTGUID>', '');
    expectReason(() => ledgerParser().parse(envelope(entry)), 'missing-parent-guid');
  });

  it('missing-ledger-name: LEDGERNAME absent', () => {
    const entry = VALID_LEDGER_ENTRY.replace('<LEDGERNAME>Customer</LEDGERNAME>', '');
    expectReason(() => ledgerParser().parse(envelope(entry)), 'missing-ledger-name');
  });

  it('missing-or-malformed-amount: AMOUNT absent', () => {
    const entry = VALID_LEDGER_ENTRY.replace('<AMOUNT>-100.00</AMOUNT>', '');
    expectReason(() => ledgerParser().parse(envelope(entry)), 'missing-or-malformed-amount');
  });

  it('missing-or-malformed-amount: AMOUNT is not numeric', () => {
    const entry = VALID_LEDGER_ENTRY.replace('-100.00', 'not-money');
    expectReason(() => ledgerParser().parse(envelope(entry)), 'missing-or-malformed-amount');
  });

  it('missing-or-invalid-is-deemed-positive: ISDEEMEDPOSITIVE absent', () => {
    const entry = VALID_LEDGER_ENTRY.replace('<ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>', '');
    expectReason(() => ledgerParser().parse(envelope(entry)), 'missing-or-invalid-is-deemed-positive');
  });

  it('missing-or-invalid-is-deemed-positive: ISDEEMEDPOSITIVE is neither Yes nor No', () => {
    const entry = VALID_LEDGER_ENTRY.replace('Yes', 'Maybe');
    expectReason(() => ledgerParser().parse(envelope(entry)), 'missing-or-invalid-is-deemed-positive');
  });

  // TD-001 round 4 (2026-08-16): ISDEEMEDPOSITIVE reflects a ledger's debit/credit-
  // positive nature by group classification; the signed Amount's sign independently
  // encodes this specific transaction's actual Dr/Cr direction (the codebase's own
  // established inferSideFromSign() convention, extraction/normalization/amounts.ts).
  // These are legitimately independent Tally fields -- physical evidence (0.4.9/0.4.10/
  // 0.4.11 all failed identically with parseReason 'amount-sign-conflict' on real,
  // stable ESTIMATION data) confirmed this is a real, valid business case, not a
  // malformed record. A disagreement is now tolerated: the entry still parses, flagged
  // via amountSignConflict rather than aborting the whole Voucher sync window.
  it('tolerates IsDeemedPositive=Yes with a positive Amount -- parses with amountSignConflict', () => {
    const entry = VALID_LEDGER_ENTRY.replace('<AMOUNT>-100.00</AMOUNT>', '<AMOUNT>100.00</AMOUNT>');
    expect(ledgerParser().parse(envelope(entry))).toEqual([{
      parentGuid: 'voucher-guid',
      ledgerName: 'Customer',
      isDeemedPositive: true,
      signedAmount: '100.00',
      amountSignConflict: true,
    }]);
  });

  it('tolerates IsDeemedPositive=No with a negative Amount -- parses with amountSignConflict', () => {
    const entry = VALID_LEDGER_ENTRY.replace('Yes', 'No');
    expect(ledgerParser().parse(envelope(entry))).toEqual([{
      parentGuid: 'voucher-guid',
      ledgerName: 'Customer',
      isDeemedPositive: false,
      signedAmount: '-100.00',
      amountSignConflict: true,
    }]);
  });
});

describe('VoucherInventoryEntryParser -- distinguishable failure reasons', () => {
  it('parses a well-formed entry without error (control case)', () => {
    expect(inventoryParser().parse(envelope(VALID_INVENTORY_ENTRY))).toEqual([{
      parentGuid: 'voucher-guid',
      stockItemName: 'Fixture Item',
      signedAmount: '-100.00',
    }]);
  });

  it('invalid-root: document root is not ENVELOPE', () => {
    expectReason(
      () => inventoryParser().parse('<ROOT><HEADER/><BODY/></ROOT>'),
      'invalid-root',
    );
  });

  it('missing-header: ENVELOPE has no HEADER child', () => {
    expectReason(
      () => inventoryParser().parse(envelope(VALID_INVENTORY_ENTRY, { omitHeader: true })),
      'missing-header',
    );
  });

  it('missing-body: ENVELOPE has no BODY child', () => {
    expectReason(
      () => inventoryParser().parse(envelope(VALID_INVENTORY_ENTRY, { omitBody: true })),
      'missing-body',
    );
  });

  it('missing-data: BODY has no DATA child', () => {
    expectReason(
      () => inventoryParser().parse(envelope(VALID_INVENTORY_ENTRY, { omitData: true })),
      'missing-data',
    );
  });

  it('missing-collection: DATA has no COLLECTION child', () => {
    expectReason(
      () => inventoryParser().parse(envelope(VALID_INVENTORY_ENTRY, { omitCollection: true })),
      'missing-collection',
    );
  });

  it('tally-line-error: Tally returned a LINEERROR', () => {
    expectReason(
      () => inventoryParser().parse(envelope('<LINEERROR>Some Tally-side failure</LINEERROR>')),
      'tally-line-error',
    );
  });

  it('invalid-parent-guid-node-count: two PARENTGUID children', () => {
    const entry = VALID_INVENTORY_ENTRY.replace(
      '</INVENTORYENTRY>',
      '<PARENTGUID>voucher-guid</PARENTGUID></INVENTORYENTRY>',
    );
    expectReason(() => inventoryParser().parse(envelope(entry)), 'invalid-parent-guid-node-count');
  });

  it('missing-parent-guid: PARENTGUID present once but empty', () => {
    const entry = VALID_INVENTORY_ENTRY.replace(
      '<PARENTGUID>voucher-guid</PARENTGUID>',
      '<PARENTGUID></PARENTGUID>',
    );
    expectReason(() => inventoryParser().parse(envelope(entry)), 'missing-parent-guid');
  });

  it('missing-stock-item-name: STOCKITEMNAME absent', () => {
    const entry = VALID_INVENTORY_ENTRY.replace('<STOCKITEMNAME>Fixture Item</STOCKITEMNAME>', '');
    expectReason(() => inventoryParser().parse(envelope(entry)), 'missing-stock-item-name');
  });

  it('missing-or-malformed-amount: AMOUNT absent', () => {
    const entry = VALID_INVENTORY_ENTRY.replace('<AMOUNT>-100.00</AMOUNT>', '');
    expectReason(() => inventoryParser().parse(envelope(entry)), 'missing-or-malformed-amount');
  });

  it('missing-or-malformed-amount: AMOUNT is not numeric', () => {
    const entry = VALID_INVENTORY_ENTRY.replace('-100.00', 'not-money');
    expectReason(() => inventoryParser().parse(envelope(entry)), 'missing-or-malformed-amount');
  });
});

describe('privacy: parser errors never carry business content in their reason', () => {
  it('the reason is always one of the fixed enum strings, never entry text', () => {
    const entry = VALID_LEDGER_ENTRY.replace('Customer', 'Highly Confidential Party Name Ltd');
    try {
      ledgerParser().parse(envelope(entry.replace('<LEDGERNAME>Highly Confidential Party Name Ltd</LEDGERNAME>', '')));
      throw new Error('expected throw');
    } catch (error) {
      expect(error).toBeInstanceOf(VoucherEntryParseError);
      const reason = (error as VoucherEntryParseError).reason;
      expect(reason).toBe('missing-ledger-name');
      expect(reason).not.toContain('Confidential');
    }
  });
});
