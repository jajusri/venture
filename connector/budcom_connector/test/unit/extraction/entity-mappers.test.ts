import { describe, expect, it } from 'vitest';

import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { CollectionEntityParser, mapLedger, mapLedgerGroup } from '../../../src/extraction/parsers/entity-mappers.js';
import {
  SAMPLE_LEDGER_GROUPS_RESPONSE,
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_UNICODE_LEDGER_RESPONSE,
} from '../../helpers/master-data-fixtures.js';

describe('entity mappers', () => {
  const responseParser = new TallyXmlResponseParser();
  const collectionParser = new CollectionEntityParser(responseParser);

  it('maps ledger groups and ignores CMPINFO counts', () => {
    const document = collectionParser.parseDocument(SAMPLE_LEDGER_GROUPS_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'GROUP' });
    const groups = nodes.map((n) => mapLedgerGroup(collectionParser, n)).filter(Boolean);
    expect(groups).toHaveLength(2);
    expect(groups[0]).toMatchObject({ name: 'Sundry Debtors', parentName: 'Current Assets' });
  });

  it('maps ledgers with normalized balances', () => {
    const document = collectionParser.parseDocument(SAMPLE_LEDGERS_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
    const ledgers = nodes.map((n) => mapLedger(collectionParser, n)).filter(Boolean);
    expect(ledgers[0]?.closingBalance).toEqual({
      amount: '2500.5',
      currencyCode: 'INR',
      side: 'Dr',
    });
    expect(ledgers[1]?.closingBalance?.side).toBe('Cr');
  });

  it('handles unicode ledger names', () => {
    const document = collectionParser.parseDocument(SAMPLE_UNICODE_LEDGER_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
    const ledger = mapLedger(collectionParser, nodes[0]!);
    expect(ledger?.name).toBe('ग्राहक खाता');
    expect(ledger?.id).toBeTruthy();
  });
});
