import { describe, expect, it, vi } from 'vitest';

import type { VoucherDetails } from '../../../src/erp/voucher/voucher-domain.js';
import type { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import {
  ApprovedOperationId,
  getApprovedOperation,
} from '../../../src/tally/registry/operation-registry.js';
import { VoucherInventoryEntryParser } from '../../../src/tally/voucher/voucher-inventory-parser.js';
import { joinVoucherInventories } from '../../../src/tally/voucher/voucher-inventory-joiner.js';
import { TallyVoucherExtractor } from '../../../src/tally/voucher/voucher-extractor.js';
import { VoucherXmlMapper } from '../../../src/tally/voucher/voucher-mapper.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';
import {
  buildVoucherInventoryCollectionRequestSpec,
  PRODUCTION_VOUCHER_INVENTORY_FETCH_METHODS,
} from '../../../src/tally/voucher/voucher-request.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';

const PERIOD = {
  companyName: 'Venture-Test-01',
  dateFrom: '2026-07-24',
  dateTo: '2026-07-24',
};

const CLEAN_INVENTORY_XML = envelope(`<INVENTORYENTRY>
  <STOCKITEMNAME>Fixture Item</STOCKITEMNAME>
  <ACTUALQTY>2 PCS</ACTUALQTY>
  <BILLEDQTY>2 PCS</BILLEDQTY>
  <RATE>50.00/PCS</RATE>
  <AMOUNT>-100.00</AMOUNT>
  <PARENTGUID> VOUCHER-GUID </PARENTGUID>
</INVENTORYENTRY>`);

describe('safe Voucher inventory extraction', () => {
  it('builds only the bounded SOURCECOLLECTION inventory walk', () => {
    const spec = buildVoucherInventoryCollectionRequestSpec(PERIOD);
    const xml = new TallyXmlRequestBuilder().buildEmbeddedCollection(spec);

    expect(spec.collection).toMatchObject({
      sourceCollection: 'Venture Voucher Inventory Source',
      walk: 'AllInventoryEntries',
      fetch: PRODUCTION_VOUCHER_INVENTORY_FETCH_METHODS,
      compute: { ParentGUID: '$$Owner:$GUID' },
    });
    expect(xml).not.toMatch(
      /<NATIVEMETHOD>(?:Voucher\.?Amount|AllInventoryEntries(?:\.|<)|.*\*)/i,
    );
    expect(xml).toContain('<SVFROMDATE>20260724</SVFROMDATE>');
    expect(xml).toContain('<SVTODATE>20260724</SVTODATE>');
  });

  it('registers a separately approved read-only inventory operation', () => {
    expect(getApprovedOperation(ApprovedOperationId.VoucherInventoryEntries)).toMatchObject({
      operationId: 'VOUCHER_INVENTORY_ENTRIES',
      tallyRequest: 'Export',
      requestKind: 'Collection',
      classification: 'VERIFIED_SAFE',
      requiresDateRange: true,
      maximumRangeDays: 366,
    });
  });

  it('parses and preserves explicit inventory XML values', () => {
    expect(parser().parse(CLEAN_INVENTORY_XML)).toEqual([{
      parentGuid: 'voucher-guid',
      stockItemName: 'Fixture Item',
      actualQuantity: '2 PCS',
      billedQuantity: '2 PCS',
      rate: '50.00/PCS',
      signedAmount: '-100.00',
    }]);
  });

  it('joins by normalized GUID and maps the existing snapshot inventory shape', () => {
    const [joined] = joinVoucherInventories(
      [voucher('VOUCHER-GUID')],
      parser().parse(CLEAN_INVENTORY_XML),
    );
    expect(joined?.inventoryEntries).toEqual([{
      lineNumber: 1,
      itemName: 'Fixture Item',
      quantity: '2 PCS',
      actualQuantity: '2 PCS',
      billedQuantity: '2 PCS',
      rate: '50.00/PCS',
      amount: { amount: '100', side: 'credit' },
      allocations: [],
    }]);
  });

  it('rejects orphan inventory rows', () => {
    expect(() => joinVoucherInventories(
      [voucher('another-guid')],
      parser().parse(CLEAN_INVENTORY_XML),
    )).toThrow(/unknown parent GUID/i);
  });

  it('rejects duplicate Voucher parent identities', () => {
    expect(() => joinVoucherInventories(
      [voucher('voucher-guid', 'one'), voucher('VOUCHER-GUID', 'two')],
      parser().parse(CLEAN_INVENTORY_XML),
    )).toThrow(/Duplicate Voucher GUID/i);
  });

  it('rejects duplicate ParentGUID fields in one inventory row', () => {
    const xml = CLEAN_INVENTORY_XML.replace(
      '</INVENTORYENTRY>',
      '<PARENTGUID>voucher-guid</PARENTGUID></INVENTORYENTRY>',
    );
    expect(() => parser().parse(xml)).toThrow(/exactly one ParentGUID/i);
  });

  it('rejects malformed inventory amounts', () => {
    expect(() => parser().parse(
      CLEAN_INVENTORY_XML.replace('-100.00', 'not-money'),
    )).toThrow(/invalid signed Amount/i);
  });

  // TD-001 fix (2026-08-16): XML-1.0-illegal characters like &#4; are now sanitized
  // out before structural parsing (shared TallyXmlResponseParser), not rejected.
  it('sanitizes XML 1.0-invalid characters in inventory responses rather than rejecting them', () => {
    const sanitized = CLEAN_INVENTORY_XML.replace(
      '<STOCKITEMNAME>Fixture Item</STOCKITEMNAME>',
      '<STOCKITEMNAME>Fixture&#4;Item</STOCKITEMNAME>',
    );
    const entries = parser().parse(sanitized);
    expect(entries).toHaveLength(1);
    expect(entries[0]?.stockItemName).toBe('FixtureItem');
  });

  it('fails the production extraction closed when the inventory phase is invalid', async () => {
    const discovery = envelope(`<VOUCHER>
      <DATE>20260724</DATE><VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
      <VOUCHERNUMBER>16</VOUCHERNUMBER><GUID>voucher-guid</GUID>
    </VOUCHER>`);
    const ledger = envelope(`<LEDGERENTRY>
      <LEDGERNAME>Customer</LEDGERNAME><ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
      <AMOUNT>-100.00</AMOUNT><PARENTGUID>voucher-guid</PARENTGUID>
    </LEDGERENTRY><LEDGERENTRY>
      <LEDGERNAME>Sales</LEDGERNAME><ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
      <AMOUNT>100.00</AMOUNT><PARENTGUID>voucher-guid</PARENTGUID>
    </LEDGERENTRY>`);
    const invalidInventory = CLEAN_INVENTORY_XML.replace('-100.00', 'invalid');
    const executeApprovedRead = vi.fn()
      .mockResolvedValueOnce(exchange(ApprovedOperationId.Vouchers, discovery))
      .mockResolvedValueOnce(exchange(ApprovedOperationId.VoucherLedgerEntries, ledger))
      .mockResolvedValueOnce(
        exchange(ApprovedOperationId.VoucherInventoryEntries, invalidInventory),
      );
    const gateway = { executeApprovedRead } as unknown as TallyReadGateway;
    const voucherParser = new VoucherCollectionParser(new TallyXmlResponseParser());
    const extractor = new TallyVoucherExtractor(
      gateway,
      voucherParser,
      new VoucherXmlMapper(voucherParser),
      undefined,
      true,
    );

    await expect(extractor.readVouchers('Venture-Test-01', {
      dateFrom: '2026-07-24',
      dateTo: '2026-07-24',
    })).rejects.toMatchObject({
      statusCode: 422,
      details: {
        reasonCode: 'voucher-inventory-validation',
        parseReason: 'missing-or-malformed-amount',
      },
    });
    expect(executeApprovedRead).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({
        operationId: ApprovedOperationId.VoucherInventoryEntries,
      }),
    );
  });

  // Controlled-pilot Session 2 physical retest FAIL follow-up (2026-08-16): the user
  // reported vouchers recently deleted from ESTIMATION in Tally. The discovery phase and
  // the ledger/inventory-entries phases are three SEPARATE, sequential Tally requests
  // (see voucher-extractor.ts) -- if Tally's discovery report and its ledger/inventory
  // report engines disagree about a Voucher's existence at query time (most plausibly
  // because it was deleted between requests), the ledger/inventory response can contain
  // an entry for a Voucher GUID the discovery phase never listed. This proves that exact
  // "orphan entry" shape reproduces the two-phase-join-mismatch failure independent of
  // any XML-illegal-character concern, and that the diagnostic now distinguishes it via
  // `reconciliationReason` instead of collapsing to an undifferentiated message.
  it('fails closed with a distinguishable reconciliationReason when an inventory entry references a Voucher deleted between phases', async () => {
    const discovery = envelope(`<VOUCHER>
      <DATE>20260724</DATE><VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
      <VOUCHERNUMBER>16</VOUCHERNUMBER><GUID>voucher-still-present</GUID>
    </VOUCHER>`);
    const ledger = envelope(`<LEDGERENTRY>
      <LEDGERNAME>Customer</LEDGERNAME><ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
      <AMOUNT>-100.00</AMOUNT><PARENTGUID>voucher-still-present</PARENTGUID>
    </LEDGERENTRY><LEDGERENTRY>
      <LEDGERNAME>Sales</LEDGERNAME><ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
      <AMOUNT>100.00</AMOUNT><PARENTGUID>voucher-still-present</PARENTGUID>
    </LEDGERENTRY>`);
    // References a GUID absent from the discovery-phase response entirely -- simulating
    // a Voucher deleted from Tally's master list between the discovery request and this
    // (later) inventory-entries request, while its inventory posting was still returned.
    const inventoryWithOrphanEntry = CLEAN_INVENTORY_XML.replace(
      'VOUCHER-GUID',
      'voucher-deleted-between-phases',
    );
    const executeApprovedRead = vi.fn()
      .mockResolvedValueOnce(exchange(ApprovedOperationId.Vouchers, discovery))
      .mockResolvedValueOnce(exchange(ApprovedOperationId.VoucherLedgerEntries, ledger))
      .mockResolvedValueOnce(
        exchange(ApprovedOperationId.VoucherInventoryEntries, inventoryWithOrphanEntry),
      );
    const gateway = { executeApprovedRead } as unknown as TallyReadGateway;
    const voucherParser = new VoucherCollectionParser(new TallyXmlResponseParser());
    const extractor = new TallyVoucherExtractor(
      gateway,
      voucherParser,
      new VoucherXmlMapper(voucherParser),
      undefined,
      true,
    );

    await expect(extractor.readVouchers('Venture-Test-01', {
      dateFrom: '2026-07-24',
      dateTo: '2026-07-24',
    })).rejects.toMatchObject({
      statusCode: 422,
      details: {
        reasonCode: 'voucher-inventory-validation',
        reconciliationReason: 'orphan-inventory-entry',
        operation: 'VoucherInventoryEntries',
      },
    });
  });
});

function parser(): VoucherInventoryEntryParser {
  return new VoucherInventoryEntryParser(new TallyXmlResponseParser());
}

function envelope(records: string): string {
  return `<ENVELOPE><HEADER><STATUS>1</STATUS></HEADER><BODY><DATA><COLLECTION>${records}</COLLECTION></DATA></BODY></ENVELOPE>`;
}

function exchange(operationId: ApprovedOperationId, rawXml: string) {
  return {
    operationId,
    rawXml,
    byteLength: Buffer.byteLength(rawXml),
    durationMs: 1,
  };
}

function voucher(guid: string, voucherId = guid): VoucherDetails {
  return {
    voucherId,
    identityVersion: 'unproven',
    guid,
    masterId: null,
    alterId: null,
    voucherKey: null,
    voucherRetainKey: null,
    date: '2026-07-24',
    voucherType: 'Sales',
    voucherNumber: '16',
    partyName: null,
    amount: { amount: '100', side: null },
    amountComparable: true,
    status: 'active',
    dataQuality: 'complete',
    ledgerEntries: [],
    inventoryEntries: [],
    allocations: [],
  };
}
