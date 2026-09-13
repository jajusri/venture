import { describe, expect, it } from 'vitest';

import { mapLedger, mapStockItem, CollectionEntityParser } from '../../../src/extraction/parsers/entity-mappers.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';

describe('entity mapping drop behavior (ledgers and stock)', () => {
  const parser = new TallyXmlResponseParser();
  const collectionParser = new CollectionEntityParser(parser);

  function mapLedgers(rawXml: string) {
    const document = collectionParser.parseDocument(rawXml);
    return collectionParser
      .parseNodes(document, { nodeName: 'LEDGER' })
      .map((node) => mapLedger(collectionParser, node))
      .filter((item) => item !== undefined);
  }

  function mapStock(rawXml: string) {
    const document = collectionParser.parseDocument(rawXml);
    return collectionParser
      .parseNodes(document, { nodeName: 'STOCKITEM' })
      .map((node) => mapStockItem(collectionParser, node))
      .filter((item) => item !== undefined);
  }

  it('drops ledger nodes with missing name without counters', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <LEDGER NAME="Valid"><NAME>Valid</NAME><PARENT>Cash</PARENT><GUID>g1</GUID></LEDGER>
      <LEDGER><PARENT>Cash</PARENT><GUID>g2</GUID></LEDGER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const mapped = mapLedgers(xml);
    expect(mapped).toHaveLength(1);
    expect(mapped[0]?.name).toBe('Valid');
  });

  it('maps ledger nodes with missing GUID using name fallback identity', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <LEDGER NAME="Fallback"><NAME>Fallback</NAME><PARENT>Sundry Debtors</PARENT></LEDGER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const mapped = mapLedgers(xml);
    expect(mapped).toHaveLength(1);
    expect(mapped[0]?.guid).toBeUndefined();
    expect(mapped[0]?.parentGroup).toBe('Sundry Debtors');
  });

  it('maps ledger nodes with missing parent when name is present', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <LEDGER NAME="No Parent"><NAME>No Parent</NAME><GUID>g3</GUID></LEDGER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const mapped = mapLedgers(xml);
    expect(mapped).toHaveLength(1);
    expect(mapped[0]?.parentGroup).toBeUndefined();
  });

  it('records malformed numeric ledger balances as undefined without dropping the entity', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <LEDGER NAME="Bad Balance"><NAME>Bad Balance</NAME><PARENT>Cash</PARENT>
      <GUID>g4</GUID><OPENINGBALANCE>not-a-number</OPENINGBALANCE></LEDGER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const mapped = mapLedgers(xml);
    expect(mapped).toHaveLength(1);
    expect(mapped[0]?.openingBalance).toBeUndefined();
  });

  it('maps unexpected boolean values to false for bill-wise flag', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <LEDGER NAME="Bool Test"><NAME>Bool Test</NAME><PARENT>Cash</PARENT>
      <GUID>g5</GUID><ISBILLWISEON>maybe</ISBILLWISEON></LEDGER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const mapped = mapLedgers(xml);
    expect(mapped[0]?.isBillWiseOn).toBe(false);
  });

  it('drops structurally empty ledger nodes', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION><LEDGER></LEDGER></COLLECTION></DATA></BODY></ENVELOPE>`;
    expect(mapLedgers(xml)).toHaveLength(0);
  });

  it('drops stock nodes with missing name without counters', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <STOCKITEM NAME="Valid Item"><NAME>Valid Item</NAME><GUID>s1</GUID><BASEUNITS>Nos</BASEUNITS></STOCKITEM>
      <STOCKITEM><GUID>s2</GUID><BASEUNITS>Nos</BASEUNITS></STOCKITEM>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const mapped = mapStock(xml);
    expect(mapped).toHaveLength(1);
    expect(mapped[0]?.name).toBe('Valid Item');
  });

  it('maps stock nodes with missing GUID using fallback identity', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <STOCKITEM NAME="Fallback Item"><NAME>Fallback Item</NAME><BASEUNITS>Nos</BASEUNITS></STOCKITEM>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const mapped = mapStock(xml);
    expect(mapped).toHaveLength(1);
    expect(mapped[0]?.guid).toBeUndefined();
  });

  it('maps stock nodes with missing base unit without dropping them', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <STOCKITEM NAME="No Unit"><NAME>No Unit</NAME><GUID>s3</GUID></STOCKITEM>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const mapped = mapStock(xml);
    expect(mapped).toHaveLength(1);
    expect(mapped[0]?.baseUnit).toBeUndefined();
  });
});
