import { describe, expect, it } from 'vitest';

import {
  assessLedgerMasterDataEnvelope,
  assessLedgerMasterDataExtraction,
  assessLedgerMasterDataStructure,
} from '../../../src/tally/contracts/ledger-master-data-contract.js';
import { computeMasterDataExtractionMetrics } from '../../../src/extraction/parsers/master-data-extraction-metrics.js';
import { mapLedger } from '../../../src/extraction/parsers/entity-mappers.js';
import { CollectionEntityParser } from '../../../src/extraction/parsers/entity-mappers.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { findBodyDataCollections } from '../../../src/tally/contracts/master-data-envelope.js';
import {
  SAMPLE_EMPTY_COLLECTION_RESPONSE,
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_SHALLOW_LEDGERS_RESPONSE,
} from '../../helpers/master-data-fixtures.js';
import {
  buildDualCollectionLedgerEnvelope,
  buildMalformedEntityLedgerEnvelope,
  buildRichLedgerEnvelope,
  buildUnrelatedLedgerOutsideCollectionEnvelope,
  SYNTHETIC_MISSING_COLLECTION_RESPONSE,
  SYNTHETIC_MISSING_ENVELOPE_RESPONSE,
  SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE,
  SYNTHETIC_TALLY_LINEERROR_RESPONSE,
  SYNTHETIC_UNRELATED_LEDGER_IN_SUBTREE,
  SYNTHETIC_WRONG_ROOT_RESPONSE,
} from '../../helpers/inbound-xml-fixtures.js';

describe('ledger master-data response contract', () => {
  const parser = new TallyXmlResponseParser();
  const collectionParser = new CollectionEntityParser(parser);

  function extractScoped(rawXml: string) {
    const document = collectionParser.parseDocument(rawXml);
    const collections = findBodyDataCollections(document);
    const candidateNodes = collectionParser.collectScopedCandidateNodes(document, 'LEDGER');
    const nodes = collectionParser.parseNodes(document, {
      nodeName: 'LEDGER',
      scopeToRequestedCollection: true,
    });
    const mappedBeforeDedupe = nodes
      .map((node) => mapLedger(collectionParser, node))
      .filter((item) => item !== undefined);
    const metrics = computeMasterDataExtractionMetrics({
      entityNodeName: 'LEDGER',
      candidateNodes,
      mappedBeforeDedupe,
      mappedAfterDedupe: mappedBeforeDedupe,
      collectionPresent: collections.length > 0,
    });
    const structure = assessLedgerMasterDataStructure(document);
    const extraction = assessLedgerMasterDataExtraction(metrics, mappedBeforeDedupe);
    return { structure, extraction, metrics, mappedBeforeDedupe };
  }

  it('accepts valid rich ledger collection as SUCCESS', () => {
    expect(assessLedgerMasterDataEnvelope(SAMPLE_LEDGERS_RESPONSE)).toBeUndefined();
    const result = extractScoped(SAMPLE_LEDGERS_RESPONSE);
    expect(result.structure.blocking).toBe(false);
    expect(result.extraction.status).toBe('SUCCESS');
    expect(result.mappedBeforeDedupe).toHaveLength(2);
  });

  it('classifies synthetic LINEERROR as blocking TALLY_ERROR', () => {
    const envelope = assessLedgerMasterDataEnvelope(SYNTHETIC_TALLY_LINEERROR_RESPONSE);
    expect(envelope?.status).toBe('TALLY_ERROR');
    expect(envelope?.blocking).toBe(true);
    expect(envelope?.reasonCode).toBe('tally_line_error');
  });

  it('does not classify standalone HEADER/STATUS=0 without LINEERROR as proven Tally error', () => {
    expect(assessLedgerMasterDataEnvelope(SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE)).toBeUndefined();
    const result = extractScoped(SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE);
    expect(result.extraction.status).toBe('SUCCESS');
    expect(result.mappedBeforeDedupe).toHaveLength(1);
  });

  it('classifies wrong root as blocking MALFORMED', () => {
    const envelope = assessLedgerMasterDataEnvelope(SYNTHETIC_WRONG_ROOT_RESPONSE);
    expect(envelope?.status).toBe('MALFORMED');
    expect(envelope?.reasonCode).toBe('envelope_drift');
  });

  it('classifies missing ENVELOPE as blocking MALFORMED', () => {
    const envelope = assessLedgerMasterDataEnvelope(SYNTHETIC_MISSING_ENVELOPE_RESPONSE);
    expect(envelope?.status).toBe('MALFORMED');
  });

  it('classifies missing requested collection as COLLECTION_MISSING', () => {
    const document = collectionParser.parseDocument(SYNTHETIC_MISSING_COLLECTION_RESPONSE);
    const structure = assessLedgerMasterDataStructure(document);
    expect(structure.status).toBe('COLLECTION_MISSING');
    expect(structure.blocking).toBe(true);
  });

  it('classifies valid empty collection as EMPTY', () => {
    const result = extractScoped(SAMPLE_EMPTY_COLLECTION_RESPONSE);
    expect(result.extraction.status).toBe('EMPTY');
    expect(result.extraction.blocking).toBe(false);
    expect(result.extraction.reasonCode).toBe('collection_empty');
  });

  it('classifies placeholder-only collection as EMPTY', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION><LEDGER>0</LEDGER></COLLECTION></DATA></BODY></ENVELOPE>`;
    const result = extractScoped(xml);
    expect(result.extraction.status).toBe('EMPTY');
    expect(result.extraction.reasonCode).toBe('placeholder_only');
    expect(result.extraction.blocking).toBe(false);
  });

  it('ignores unrelated LEDGER nodes outside requested collection subtree', () => {
    const result = extractScoped(SYNTHETIC_UNRELATED_LEDGER_IN_SUBTREE);
    expect(result.mappedBeforeDedupe).toHaveLength(0);
    expect(result.extraction.status).toBe('EMPTY');
  });

  it('ignores ledgers outside DATA/COLLECTION even when another collection exists', () => {
    const result = extractScoped(buildDualCollectionLedgerEnvelope());
    expect(result.mappedBeforeDedupe.map((item) => item.name)).toEqual(['In First Collection']);
  });

  it('does not read ledgers from DESC-only regions', () => {
    const result = extractScoped(buildUnrelatedLedgerOutsideCollectionEnvelope());
    expect(result.mappedBeforeDedupe).toHaveLength(0);
    expect(result.extraction.status).toBe('EMPTY');
  });

  it('classifies all malformed nodes as ALL_UNMAPPABLE', () => {
    const xml = `<ENVELOPE><BODY><DATA><COLLECTION>
      <LEDGER><NAME></NAME></LEDGER>
      <LEDGER NAME="Ledger"><PARENT>Cash</PARENT></LEDGER>
    </COLLECTION></DATA></BODY></ENVELOPE>`;
    const result = extractScoped(xml);
    expect(result.extraction.status).toBe('ALL_UNMAPPABLE');
    expect(result.extraction.blocking).toBe(true);
  });

  it('accepts partial malformed subset as INCOMPLETE', () => {
    const result = extractScoped(buildMalformedEntityLedgerEnvelope());
    expect(result.extraction.status).toBe('INCOMPLETE');
    expect(result.extraction.blocking).toBe(false);
    expect(result.metrics.droppedRecordCount).toBeGreaterThan(0);
    expect(result.mappedBeforeDedupe).toHaveLength(1);
  });

  it('blocks shallow export without GUID or PARENT as ALL_UNMAPPABLE', () => {
    const result = extractScoped(SAMPLE_SHALLOW_LEDGERS_RESPONSE);
    expect(result.extraction.status).toBe('ALL_UNMAPPABLE');
    expect(result.extraction.reasonCode).toBe('shallow_export');
    expect(result.extraction.blocking).toBe(true);
  });

  it('reports aggregate extraction metrics without record values', () => {
    const result = extractScoped(buildRichLedgerEnvelope([{ name: 'A', guid: 'g1' }, { name: 'B', guid: 'g2' }]));
    expect(result.metrics.candidateNodeCount).toBe(2);
    expect(result.metrics.mappedRecordCount).toBe(2);
    expect(JSON.stringify(result.metrics)).not.toMatch(/g1|Ledger A/i);
  });

  it('privacy-safe assessment contains no ledger names or GUIDs', () => {
    const envelope = assessLedgerMasterDataEnvelope(SYNTHETIC_TALLY_LINEERROR_RESPONSE);
    expect(JSON.stringify(envelope)).not.toMatch(/Could not find|Cash|GUID/i);
  });
});
