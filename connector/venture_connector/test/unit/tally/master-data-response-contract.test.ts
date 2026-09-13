import { describe, expect, it } from 'vitest';

import { assessEnvelope } from '../../../src/tally/contracts/response-contract.js';
import { CollectionEntityParser } from '../../../src/extraction/parsers/entity-mappers.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { assessLedgerExtraction } from '../../../src/erp/ledger/ledger-extraction-quality.js';
import { validateLedgerCollection } from '../../../src/erp/ledger/ledger-validation.js';
import { mapNormalizedLedgerToDomain } from '../../../src/erp/ledger/ledger-mapper.js';
import { mapNormalizedStockItemToDomain } from '../../../src/erp/stock-item/stock-item-mapper.js';
import {
  SAMPLE_EMPTY_COLLECTION_RESPONSE,
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_SHALLOW_LEDGERS_RESPONSE,
  SAMPLE_STOCK_ITEMS_RESPONSE,
} from '../../helpers/master-data-fixtures.js';
import {
  buildDuplicateGuidLedgerEnvelope,
  buildMalformedEntityLedgerEnvelope,
  buildRichLedgerEnvelope,
  buildRichStockEnvelope,
  SYNTHETIC_MISSING_ENVELOPE_RESPONSE,
  SYNTHETIC_PLACEHOLDER_ONLY_LEDGER_COLLECTION,
  SYNTHETIC_TALLY_LINEERROR_RESPONSE,
  SYNTHETIC_UNRELATED_LEDGER_IN_SUBTREE,
  SYNTHETIC_WRONG_ROOT_RESPONSE,
} from '../../helpers/inbound-xml-fixtures.js';

describe('master-data response contract characterization (ledgers and stock)', () => {
  const parser = new TallyXmlResponseParser();
  const collectionParser = new CollectionEntityParser(parser);

  function extractLedgers(rawXml: string) {
    const document = collectionParser.parseDocument(rawXml);
    return collectionParser
      .parseNodes(document, { nodeName: 'LEDGER' })
      .map((node) => collectionParser.resolveName(node))
      .filter((name): name is string => Boolean(name));
  }

  function extractStockItems(rawXml: string) {
    const document = collectionParser.parseDocument(rawXml);
    return collectionParser
      .parseNodes(document, { nodeName: 'STOCKITEM' })
      .map((node) => collectionParser.resolveName(node))
      .filter((name): name is string => Boolean(name));
  }

  it('1. parses valid rich ledger envelope', () => {
    expect(assessEnvelope(SAMPLE_LEDGERS_RESPONSE)).toBeUndefined();
    expect(extractLedgers(SAMPLE_LEDGERS_RESPONSE)).toEqual(['Cash', 'Acme Corp']);
  });

  it('2. parses valid rich stock envelope', () => {
    expect(assessEnvelope(SAMPLE_STOCK_ITEMS_RESPONSE)).toBeUndefined();
    expect(extractStockItems(SAMPLE_STOCK_ITEMS_RESPONSE)).toEqual(['Widget A']);
  });

  it('3. flags missing envelope as DRIFT before ledger parse', () => {
    expect(assessEnvelope(SYNTHETIC_MISSING_ENVELOPE_RESPONSE)?.status).toBe('DRIFT');
  });

  it('4. flags wrong root shape as DRIFT', () => {
    expect(assessEnvelope(SYNTHETIC_WRONG_ROOT_RESPONSE)?.status).toBe('DRIFT');
  });

  it('5. parses synthetic explicit Tally error envelope without dedicated error classifier', () => {
    expect(assessEnvelope(SYNTHETIC_TALLY_LINEERROR_RESPONSE)).toBeUndefined();
    const document = collectionParser.parseDocument(SYNTHETIC_TALLY_LINEERROR_RESPONSE);
    expect(parser.findFirst(document, 'LINEERROR')).toBeDefined();
    expect(extractLedgers(SYNTHETIC_TALLY_LINEERROR_RESPONSE)).toEqual([]);
  });

  it('6. returns zero ledger entities when requested collection is absent', () => {
    const xml = '<ENVELOPE><BODY><DATA></DATA></BODY></ENVELOPE>';
    expect(extractLedgers(xml)).toEqual([]);
    expect(assessLedgerExtraction([]).quality).toBe('partial');
  });

  it('7. treats empty requested collection as zero entities', () => {
    expect(extractLedgers(SAMPLE_EMPTY_COLLECTION_RESPONSE)).toEqual([]);
  });

  it('8. treats placeholder-only collection as zero ledger entities', () => {
    expect(extractLedgers(SYNTHETIC_PLACEHOLDER_ONLY_LEDGER_COLLECTION)).toEqual([]);
  });

  it('9. still finds ledger nodes nested under unrelated subtrees via tree-wide findAll', () => {
    expect(extractLedgers(SYNTHETIC_UNRELATED_LEDGER_IN_SUBTREE)).toEqual(['Shadow Ledger']);
  });

  it('10. extracts only valid named entities when one malformed entity is present', () => {
    const document = collectionParser.parseDocument(buildMalformedEntityLedgerEnvelope());
    const names = collectionParser
      .parseNodes(document, { nodeName: 'LEDGER' })
      .map((node) => collectionParser.resolveName(node))
      .filter((name): name is string => Boolean(name));
    expect(names).toEqual(['Valid Ledger']);
  });

  it('11. returns zero entities when all ledger nodes are malformed', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <LEDGER><NAME></NAME></LEDGER>
      <LEDGER NAME="Ledger"><PARENT>Cash</PARENT></LEDGER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    expect(extractLedgers(xml)).toEqual([]);
  });

  it('12. keeps duplicate GUID entities until validation flags them', () => {
    const names = extractLedgers(buildDuplicateGuidLedgerEnvelope());
    expect(names).toEqual(['Ledger A', 'Ledger B']);
    const mapped = names.map((name, index) =>
      mapNormalizedLedgerToDomain({
        id: index === 0 ? 'guid:duplicate-guid-000000000099' : 'guid:duplicate-guid-000000000099',
        name,
        normalizedName: name.toLowerCase(),
        guid: 'duplicate-guid-000000000099',
        parentGroup: 'Cash-in-Hand',
        identitySource: 'guid',
        dataQuality: 'complete',
      }),
    );
    const validation = validateLedgerCollection(mapped);
    expect(validation.ok).toBe(false);
    expect(validation.issues.some((issue) => issue.code === 'DUPLICATE_GUID')).toBe(true);
  });

  it('13. maps duplicate names with different GUIDs to separate records', () => {
    const xml = buildRichLedgerEnvelope([
      { name: 'Shared Name', guid: 'guid-a-000000000001' },
      { name: 'Shared Name', guid: 'guid-b-000000000002' },
    ]);
    expect(extractLedgers(xml)).toEqual(['Shared Name', 'Shared Name']);
  });

  it('14. dedupeById keep-first behavior is applied in extractor, not contract layer', () => {
    const xml = buildDuplicateGuidLedgerEnvelope();
    const document = collectionParser.parseDocument(xml);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
    expect(nodes.length).toBe(2);
  });

  it('15. shallow ledger export remains mappable but collection quality classifies separately', () => {
    const document = collectionParser.parseDocument(SAMPLE_SHALLOW_LEDGERS_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
    expect(nodes.length).toBe(1);
    expect(assessLedgerExtraction([]).quality).toBe('partial');
  });

  it('16. stock incomplete records remain mappable with incomplete dataQuality', () => {
    const xml = buildRichStockEnvelope([{ name: 'No Unit Item', guid: 'stock-guid-000000000001', baseUnit: '' }]);
    const document = collectionParser.parseDocument(xml);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'STOCKITEM' });
    expect(nodes.length).toBe(1);
    const mapped = mapNormalizedStockItemToDomain({
      id: 'guid:stock-guid-000000000001',
      name: 'No Unit Item',
      normalizedName: 'no unit item',
      baseUnit: undefined,
      status: 'active',
    });
    expect(mapped.dataQuality).toBe('incomplete');
  });
});
