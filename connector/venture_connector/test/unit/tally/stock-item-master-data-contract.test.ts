import { describe, expect, it } from 'vitest';

import {
  assessStockItemMasterDataEnvelope,
  assessStockItemMasterDataExtraction,
  assessStockItemMasterDataStructure,
} from '../../../src/tally/contracts/stock-item-master-data-contract.js';
import { computeMasterDataExtractionMetrics } from '../../../src/extraction/parsers/master-data-extraction-metrics.js';
import { mapStockItem, CollectionEntityParser } from '../../../src/extraction/parsers/entity-mappers.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { findBodyDataCollections } from '../../../src/tally/contracts/master-data-envelope.js';
import {
  SAMPLE_EMPTY_COLLECTION_RESPONSE,
  SAMPLE_STOCK_ITEMS_RESPONSE,
} from '../../helpers/master-data-fixtures.js';
import {
  buildRichStockEnvelope,
  SYNTHETIC_MISSING_COLLECTION_RESPONSE,
  SYNTHETIC_MISSING_ENVELOPE_RESPONSE,
  SYNTHETIC_TALLY_LINEERROR_RESPONSE,
  SYNTHETIC_UNRELATED_STOCKITEM_IN_SUBTREE,
  SYNTHETIC_WRONG_ROOT_RESPONSE,
} from '../../helpers/inbound-xml-fixtures.js';

describe('stock-item master-data response contract', () => {
  const parser = new TallyXmlResponseParser();
  const collectionParser = new CollectionEntityParser(parser);

  function extractScoped(rawXml: string) {
    const document = collectionParser.parseDocument(rawXml);
    const collections = findBodyDataCollections(document);
    const candidateNodes = collectionParser.collectScopedCandidateNodes(document, 'STOCKITEM');
    const nodes = collectionParser.parseNodes(document, {
      nodeName: 'STOCKITEM',
      scopeToRequestedCollection: true,
    });
    const mappedBeforeDedupe = nodes
      .map((node) => mapStockItem(collectionParser, node))
      .filter((item) => item !== undefined);
    const metrics = computeMasterDataExtractionMetrics({
      entityNodeName: 'STOCKITEM',
      candidateNodes,
      mappedBeforeDedupe,
      mappedAfterDedupe: mappedBeforeDedupe,
      collectionPresent: collections.length > 0,
    });
    const structure = assessStockItemMasterDataStructure(document);
    const extraction = assessStockItemMasterDataExtraction(metrics, mappedBeforeDedupe);
    return { structure, extraction, metrics, mappedBeforeDedupe };
  }

  it('accepts valid rich stock collection as SUCCESS', () => {
    expect(assessStockItemMasterDataEnvelope(SAMPLE_STOCK_ITEMS_RESPONSE)).toBeUndefined();
    const result = extractScoped(SAMPLE_STOCK_ITEMS_RESPONSE);
    expect(result.extraction.status).toBe('SUCCESS');
    expect(result.mappedBeforeDedupe).toHaveLength(1);
  });

  it('classifies synthetic LINEERROR as blocking TALLY_ERROR', () => {
    const envelope = assessStockItemMasterDataEnvelope(SYNTHETIC_TALLY_LINEERROR_RESPONSE);
    expect(envelope?.status).toBe('TALLY_ERROR');
    expect(envelope?.blocking).toBe(true);
  });

  it('classifies wrong root and missing envelope as MALFORMED', () => {
    expect(assessStockItemMasterDataEnvelope(SYNTHETIC_WRONG_ROOT_RESPONSE)?.status).toBe('MALFORMED');
    expect(assessStockItemMasterDataEnvelope(SYNTHETIC_MISSING_ENVELOPE_RESPONSE)?.status).toBe('MALFORMED');
  });

  it('classifies missing requested collection as COLLECTION_MISSING', () => {
    const document = collectionParser.parseDocument(SYNTHETIC_MISSING_COLLECTION_RESPONSE);
    const structure = assessStockItemMasterDataStructure(document);
    expect(structure.status).toBe('COLLECTION_MISSING');
    expect(structure.blocking).toBe(true);
  });

  it('classifies valid empty collection as EMPTY', () => {
    const result = extractScoped(SAMPLE_EMPTY_COLLECTION_RESPONSE);
    expect(result.extraction.status).toBe('EMPTY');
    expect(result.extraction.blocking).toBe(false);
  });

  it('ignores unrelated STOCKITEM nodes outside requested collection subtree', () => {
    const result = extractScoped(SYNTHETIC_UNRELATED_STOCKITEM_IN_SUBTREE);
    expect(result.mappedBeforeDedupe).toHaveLength(0);
    expect(result.extraction.status).toBe('EMPTY');
  });

  it('classifies all unnamed stock nodes as ALL_UNMAPPABLE', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <STOCKITEM><GUID>s1</GUID><BASEUNITS>Nos</BASEUNITS></STOCKITEM>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const result = extractScoped(xml);
    expect(result.extraction.status).toBe('ALL_UNMAPPABLE');
    expect(result.extraction.blocking).toBe(true);
  });

  it('accepts partial malformed subset as INCOMPLETE', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <STOCKITEM NAME="Valid"><NAME>Valid</NAME><GUID>s1</GUID><BASEUNITS>Nos</BASEUNITS></STOCKITEM>
      <STOCKITEM><GUID>s2</GUID><BASEUNITS>Nos</BASEUNITS></STOCKITEM>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const result = extractScoped(xml);
    expect(result.extraction.status).toBe('INCOMPLETE');
    expect(result.extraction.blocking).toBe(false);
    expect(result.metrics.droppedRecordCount).toBe(1);
  });

  it('accepts incomplete unit records as INCOMPLETE not blocking', () => {
    const result = extractScoped(
      buildRichStockEnvelope([{ name: 'No Unit Item', guid: 'stock-guid-000000000001', baseUnit: '' }]),
    );
    expect(result.extraction.status).toBe('INCOMPLETE');
    expect(result.extraction.blocking).toBe(false);
  });

  it('privacy-safe assessment contains no item names or GUIDs', () => {
    const envelope = assessStockItemMasterDataEnvelope(SYNTHETIC_TALLY_LINEERROR_RESPONSE);
    expect(JSON.stringify(envelope)).not.toMatch(/Widget|stock-guid|Could not find/i);
  });
});
